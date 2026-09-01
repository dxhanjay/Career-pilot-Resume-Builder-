import { and, desc, eq, sql } from 'drizzle-orm'
import { db } from '@/db'
import { interviewAnswers, interviewQuestions, interviewSessions } from '@/db/schema'
import {
  BLUEPRINT_VERSION,
  FOCUS_META,
  PERFORMANCE_BANDS,
  QUESTION_KIND_LABELS,
  evaluateAnswer,
  generateQuestions,
  hashSeed,
  performanceBandFor,
  type InterviewFocus,
  type PerformanceBand,
  type QuestionKind,
} from '@/domain/interview'
import { buildSnapshot } from './parsing-service'
import { runMatch } from './analysis-service'
import { conflict, notFound } from './api'

/**
 * Running mock interviews.
 *
 * Questions are generated once, at session creation, and stored. Regenerating
 * them per request would mean a candidate who reloads mid-answer loses the
 * question they were part-way through, and a report that claims to be about
 * questions that were never asked.
 */

/** Concurrent unfinished sessions per user. Practice, not a backlog. */
const MAX_OPEN_SESSIONS = 3
const MAX_GAPS_CONSIDERED = 6

export async function startSession(
  userId: string,
  input: {
    focus: InterviewFocus
    resumeId: string | null
    jobDescriptionId: string | null
    questionCount: number
  },
) {
  const open = await db
    .select({ id: interviewSessions.id })
    .from(interviewSessions)
    .where(
      and(eq(interviewSessions.userId, userId), eq(interviewSessions.status, 'IN_PROGRESS')),
    )

  if (open.length >= MAX_OPEN_SESSIONS) {
    throw conflict(
      `You have ${open.length} interviews still in progress. Finish or abandon one before starting another.`,
    )
  }

  if (input.focus === 'JOB_SPECIFIC' && !input.jobDescriptionId) {
    throw conflict('A job-specific interview needs a job description to target.')
  }

  const snapshot = await loadSnapshot(input.resumeId, userId)
  const gaps = await loadGaps(input, userId)

  // Seeded from the inputs rather than the clock, so the same request produces
  // the same interview and a complaint about a bad question is reproducible.
  const seed = hashSeed(
    userId, input.resumeId, input.jobDescriptionId, input.focus, input.questionCount,
  )

  const generated = generateQuestions(snapshot, gaps, input.focus, input.questionCount, seed)

  const [session] = await db
    .insert(interviewSessions)
    .values({
      userId,
      resumeId: input.resumeId,
      jobDescriptionId: input.focus === 'JOB_SPECIFIC' ? input.jobDescriptionId : null,
      focus: input.focus,
      questionCount: generated.length,
      blueprintVersion: BLUEPRINT_VERSION,
    })
    .returning()

  await db.insert(interviewQuestions).values(
    generated.map((question, position) => ({
      sessionId: session.id,
      userId,
      position,
      kind: question.kind,
      prompt: question.prompt,
      focusSkill: question.focusSkill?.slice(0, 100) ?? null,
      rationale: question.rationale,
      expectedPoints: question.expectedPoints.join('\n'),
    })),
  )

  return getSession(session.id, userId)
}

export async function getSession(sessionId: string, userId: string) {
  const [session] = await db
    .select()
    .from(interviewSessions)
    .where(and(eq(interviewSessions.id, sessionId), eq(interviewSessions.userId, userId)))
    .limit(1)

  if (!session) throw notFound('Interview session')

  const [questions, answers] = await Promise.all([
    db
      .select()
      .from(interviewQuestions)
      .where(eq(interviewQuestions.sessionId, sessionId))
      .orderBy(interviewQuestions.position),
    db.select().from(interviewAnswers).where(eq(interviewAnswers.sessionId, sessionId)),
  ])

  const answerByQuestion = new Map(answers.map((answer) => [answer.questionId, answer]))
  const isOpen = session.status === 'IN_PROGRESS'

  return {
    id: session.id,
    resumeId: session.resumeId,
    jobDescriptionId: session.jobDescriptionId,
    focus: session.focus,
    focusLabel: FOCUS_META[session.focus as InterviewFocus]?.label ?? session.focus,
    focusDescription: FOCUS_META[session.focus as InterviewFocus]?.description ?? '',
    status: session.status,
    questionCount: session.questionCount,
    answeredCount: session.answeredCount,
    overallScore: session.overallScore,
    band: session.band,
    bandLabel: session.band
      ? PERFORMANCE_BANDS[session.band as PerformanceBand]?.label
      : null,
    bandSummary: session.band
      ? PERFORMANCE_BANDS[session.band as PerformanceBand]?.summary
      : null,
    questions: questions.map((question) => {
      const answer = answerByQuestion.get(question.id)
      // The cues a good answer covers stay hidden until the question is
      // answered, or the session is closed. Showing them first turns the
      // exercise into transcription.
      const reveal = Boolean(answer) || !isOpen
      return {
        id: question.id,
        position: question.position,
        kind: question.kind,
        kindLabel: QUESTION_KIND_LABELS[question.kind as QuestionKind] ?? question.kind,
        prompt: question.prompt,
        focusSkill: question.focusSkill,
        rationale: question.rationale,
        expectedPoints: reveal ? splitLines(question.expectedPoints) : null,
        answer: answer ? shapeAnswer(answer) : null,
      }
    }),
    report: isOpen ? null : buildReport(session, [...answerByQuestion.values()]),
    createdAt: session.createdAt,
    completedAt: session.completedAt,
  }
}

export async function submitAnswer(
  sessionId: string,
  questionId: string,
  userId: string,
  answerText: string,
) {
  const [session] = await db
    .select()
    .from(interviewSessions)
    .where(and(eq(interviewSessions.id, sessionId), eq(interviewSessions.userId, userId)))
    .limit(1)

  if (!session) throw notFound('Interview session')
  if (session.status !== 'IN_PROGRESS') {
    throw conflict('This interview is finished. Start a new one to practise again.')
  }

  const [question] = await db
    .select()
    .from(interviewQuestions)
    .where(
      and(
        eq(interviewQuestions.id, questionId),
        eq(interviewQuestions.sessionId, sessionId),
        eq(interviewQuestions.userId, userId),
      ),
    )
    .limit(1)

  if (!question) throw notFound('Question')

  const assessment = evaluateAnswer(
    answerText,
    question.kind as QuestionKind,
    splitLines(question.expectedPoints),
    question.focusSkill,
  )

  const [existing] = await db
    .select({ id: interviewAnswers.id })
    .from(interviewAnswers)
    .where(eq(interviewAnswers.questionId, questionId))
    .limit(1)

  const values = {
    answerText: answerText.trim(),
    wordCount: assessment.wordCount,
    score: assessment.overallScore,
    structureScore: assessment.structureScore,
    specificityScore: assessment.specificityScore,
    relevanceScore: assessment.relevanceScore,
    clarityScore: assessment.clarityScore,
    strengths: assessment.strengths.join('\n'),
    improvements: assessment.improvements.join('\n'),
    rubricVersion: assessment.rubricVersion,
    updatedAt: new Date(),
  }

  let saved
  if (existing) {
    // Re-answering replaces rather than adds, so a candidate can rewrite and
    // watch the score move. The unique index on question_id enforces it too.
    ;[saved] = await db
      .update(interviewAnswers)
      .set(values)
      .where(eq(interviewAnswers.id, existing.id))
      .returning()
  } else {
    ;[saved] = await db
      .insert(interviewAnswers)
      .values({ ...values, questionId, sessionId, userId })
      .returning()

    await db
      .update(interviewSessions)
      .set({ answeredCount: sql`${interviewSessions.answeredCount} + 1` })
      .where(eq(interviewSessions.id, sessionId))
  }

  return shapeAnswer(saved)
}

export async function completeSession(sessionId: string, userId: string) {
  const [session] = await db
    .select()
    .from(interviewSessions)
    .where(and(eq(interviewSessions.id, sessionId), eq(interviewSessions.userId, userId)))
    .limit(1)

  if (!session) throw notFound('Interview session')
  if (session.status !== 'IN_PROGRESS') return getSession(sessionId, userId)

  const answers = await db
    .select()
    .from(interviewAnswers)
    .where(eq(interviewAnswers.sessionId, sessionId))

  if (!answers.length) {
    throw conflict('Answer at least one question before finishing the interview.')
  }

  // Unanswered questions score zero. Averaging only what was answered would let
  // a candidate answer one question well and be told the interview went
  // strongly, which is the opposite of useful.
  const total = answers.reduce((sum, answer) => sum + answer.score, 0)
  const score = Math.max(0, Math.min(100, Math.round(total / session.questionCount)))

  await db
    .update(interviewSessions)
    .set({
      status: 'COMPLETED',
      overallScore: score,
      band: performanceBandFor(score),
      completedAt: new Date(),
    })
    .where(eq(interviewSessions.id, sessionId))

  return getSession(sessionId, userId)
}

export async function abandonSession(sessionId: string, userId: string) {
  await db
    .update(interviewSessions)
    .set({ status: 'ABANDONED', completedAt: new Date() })
    .where(
      and(
        eq(interviewSessions.id, sessionId),
        eq(interviewSessions.userId, userId),
        eq(interviewSessions.status, 'IN_PROGRESS'),
      ),
    )
}

export async function listSessions(userId: string) {
  const rows = await db
    .select()
    .from(interviewSessions)
    .where(eq(interviewSessions.userId, userId))
    .orderBy(desc(interviewSessions.createdAt))
    .limit(50)

  return rows.map((session) => ({
    id: session.id,
    focus: session.focus,
    focusLabel: FOCUS_META[session.focus as InterviewFocus]?.label ?? session.focus,
    status: session.status,
    questionCount: session.questionCount,
    answeredCount: session.answeredCount,
    overallScore: session.overallScore,
    band: session.band,
    createdAt: session.createdAt,
    completedAt: session.completedAt,
  }))
}

/* ------------------------------------------------------------------ */
/* Helpers                                                            */
/* ------------------------------------------------------------------ */

function shapeAnswer(answer: typeof interviewAnswers.$inferSelect) {
  return {
    id: answer.id,
    questionId: answer.questionId,
    answerText: answer.answerText,
    wordCount: answer.wordCount,
    score: answer.score,
    structureScore: answer.structureScore,
    specificityScore: answer.specificityScore,
    relevanceScore: answer.relevanceScore,
    clarityScore: answer.clarityScore,
    strengths: splitLines(answer.strengths),
    improvements: splitLines(answer.improvements),
    createdAt: answer.createdAt,
  }
}

function buildReport(
  session: typeof interviewSessions.$inferSelect,
  answers: Array<typeof interviewAnswers.$inferSelect>,
) {
  if (!answers.length) return null

  const average = (pick: (answer: (typeof answers)[number]) => number) =>
    Math.round(answers.reduce((sum, answer) => sum + pick(answer), 0) / answers.length)

  const band = (session.band ?? 'NEEDS_WORK') as PerformanceBand

  return {
    overallScore: session.overallScore ?? 0,
    band,
    bandLabel: PERFORMANCE_BANDS[band].label,
    bandSummary: PERFORMANCE_BANDS[band].summary,
    axisScores: [
      {
        axis: 'STRUCTURE', displayName: 'Structure',
        score: average((a) => a.structureScore),
        description: 'Whether your answers move from situation to action to result.',
      },
      {
        axis: 'SPECIFICITY', displayName: 'Specificity',
        score: average((a) => a.specificityScore),
        description: 'Whether anything you said could be checked — numbers, names, artefacts.',
      },
      {
        axis: 'RELEVANCE', displayName: 'Relevance',
        score: average((a) => a.relevanceScore),
        description: 'Whether you covered what each question was actually asking for.',
      },
      {
        axis: 'CLARITY', displayName: 'Clarity',
        score: average((a) => a.clarityScore),
        description: 'Length, hedging, and how easy the answer is to follow aloud.',
      },
    ],
    // Frequency is the signal: advice that appeared once is about one answer,
    // advice that appeared four times is about how the candidate interviews.
    topStrengths: mostCommon(answers.flatMap((a) => splitLines(a.strengths)), 3),
    topImprovements: mostCommon(answers.flatMap((a) => splitLines(a.improvements)), 4),
  }
}

function mostCommon(lines: string[], limit: number): string[] {
  const counts = new Map<string, number>()
  for (const line of lines) counts.set(line, (counts.get(line) ?? 0) + 1)
  return [...counts.entries()]
    .sort((a, b) => b[1] - a[1])
    .slice(0, limit)
    .map(([line]) => line)
}

const splitLines = (value: string | null): string[] =>
  value ? value.split('\n').map((line) => line.trim()).filter(Boolean) : []

async function loadSnapshot(resumeId: string | null, userId: string) {
  if (!resumeId) return null
  try {
    return (await buildSnapshot(resumeId, userId)).snapshot
  } catch {
    // An unparsed resume is not a reason to refuse an interview — it just means
    // the questions will be generic rather than personal.
    return null
  }
}

async function loadGaps(
  input: { resumeId: string | null; jobDescriptionId: string | null },
  userId: string,
): Promise<string[]> {
  if (!input.jobDescriptionId || !input.resumeId) return []
  try {
    const match = await runMatch(input.jobDescriptionId, input.resumeId, userId)
    return match.missing
      .filter((skill) => skill.required)
      .slice(0, MAX_GAPS_CONSIDERED)
      .map((skill) => skill.name)
  } catch {
    return []
  }
}
