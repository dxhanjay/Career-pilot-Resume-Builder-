import type { ResumeSnapshot, SnapshotExperience } from './snapshot'
import { hasSection } from './snapshot'

/**
 * Mock interviews built from the candidate's own material.
 *
 * A blueprint decides the shape of the interview — how many of each kind, in
 * what order — and slots are then filled from the resume and the posting. That
 * separation is what stops a session becoming five variations of the same
 * question when a resume happens to list five databases.
 *
 * Feedback comes from the content of an answer and nothing else. There is no
 * audio, no video, and no affect model anywhere in this file, so nothing here
 * can score a candidate on their accent, their face, or how nervous they sound.
 */

export const BLUEPRINT_VERSION = '1.0.0'
export const ANSWER_RUBRIC_VERSION = '1.0.0'

export type InterviewFocus = 'GENERAL' | 'RESUME_DEEP_DIVE' | 'JOB_SPECIFIC' | 'BEHAVIOURAL'
export type QuestionKind =
  | 'TECHNICAL'
  | 'BEHAVIOURAL'
  | 'GAP_PROBE'
  | 'PROJECT_DEEP_DIVE'
  | 'EXPERIENCE_PROBE'
  | 'MOTIVATION'
export type PerformanceBand = 'NEEDS_WORK' | 'DEVELOPING' | 'SOLID' | 'STRONG'

export const FOCUS_META: Record<InterviewFocus, { label: string; description: string }> = {
  GENERAL: {
    label: 'General practice',
    description: 'A spread across your background, your projects, and how you work.',
  },
  RESUME_DEEP_DIVE: {
    label: 'Resume deep dive',
    description:
      'Questions drawn from what is actually on your resume — the ones an interviewer who read it would ask.',
  },
  JOB_SPECIFIC: {
    label: 'Targeted at a posting',
    description:
      'Built around the gaps between your resume and one job description, including the ones you would rather not be asked about.',
  },
  BEHAVIOURAL: {
    label: 'Behavioural',
    description:
      'Situation questions about conflict, failure, ownership, and working with other people.',
  },
}

export const QUESTION_KIND_LABELS: Record<QuestionKind, string> = {
  TECHNICAL: 'Technical',
  BEHAVIOURAL: 'Behavioural',
  GAP_PROBE: 'Gap',
  PROJECT_DEEP_DIVE: 'Project',
  EXPERIENCE_PROBE: 'Experience',
  MOTIVATION: 'Motivation',
}

export const PERFORMANCE_BANDS: Record<PerformanceBand, { label: string; summary: string }> = {
  NEEDS_WORK: {
    label: 'Needs work',
    summary: 'Answers are too thin for an interviewer to judge you on. Add the specifics.',
  },
  DEVELOPING: {
    label: 'Developing',
    summary:
      'The material is there; the structure is not. Say what you did before you say what you learned.',
  },
  SOLID: {
    label: 'Solid',
    summary: 'Clear, structured answers. Sharpen the results and this is interview-ready.',
  },
  STRONG: {
    label: 'Strong',
    summary: 'Structured, specific, and measurable. This is what a good interview sounds like.',
  },
}

export function performanceBandFor(score: number): PerformanceBand {
  if (score >= 80) return 'STRONG'
  if (score >= 62) return 'SOLID'
  if (score >= 40) return 'DEVELOPING'
  return 'NEEDS_WORK'
}

export interface GeneratedQuestion {
  readonly kind: QuestionKind
  readonly prompt: string
  readonly focusSkill: string | null
  /** Why the candidate is being asked this. */
  readonly rationale: string
  /** Cues a good answer covers, revealed only after answering. */
  readonly expectedPoints: readonly string[]
}

/* ------------------------------------------------------------------ */
/* Question generation                                                */
/* ------------------------------------------------------------------ */

/**
 * Deterministic pseudo-random, seeded from the session inputs.
 *
 * The same request produces the same interview, which makes a complaint about a
 * bad question reproducible. Math.random() would make it a ghost.
 */
function seededShuffle<T>(items: T[], seed: number): T[] {
  const result = [...items]
  let state = seed || 1
  for (let i = result.length - 1; i > 0; i--) {
    state = (state * 1664525 + 1013904223) % 4294967296
    const j = state % (i + 1)
    ;[result[i], result[j]] = [result[j], result[i]]
  }
  return result
}

export function hashSeed(...parts: Array<string | number | null | undefined>): number {
  let hash = 2166136261
  for (const part of parts) {
    const text = String(part ?? '')
    for (let i = 0; i < text.length; i++) {
      hash ^= text.charCodeAt(i)
      hash = Math.imul(hash, 16777619)
    }
  }
  return Math.abs(hash)
}

export function generateQuestions(
  snapshot: ResumeSnapshot | null,
  gaps: readonly string[],
  focus: InterviewFocus,
  count: number,
  seed: number,
): GeneratedQuestion[] {
  const pool: GeneratedQuestion[] = [opener(snapshot)]

  switch (focus) {
    case 'BEHAVIOURAL':
      pool.push(...behavioural(seed, count))
      break
    case 'JOB_SPECIFIC':
      pool.push(...gapProbes(gaps, 3))
      pool.push(...technical(snapshot, seed, 2))
      pool.push(...experienceProbes(snapshot, 2))
      pool.push(...behavioural(seed, 2))
      break
    case 'RESUME_DEEP_DIVE':
      pool.push(...experienceProbes(snapshot, 3))
      pool.push(...technical(snapshot, seed, 3))
      pool.push(...projectProbes(snapshot))
      pool.push(...behavioural(seed, 1))
      break
    default:
      pool.push(...technical(snapshot, seed, 2))
      pool.push(...experienceProbes(snapshot, 2))
      pool.push(...gapProbes(gaps, 1))
      pool.push(...behavioural(seed, 3))
      break
  }

  // Behavioural questions are the safety net: they need no resume, so a
  // candidate whose parse found little still gets a full session.
  if (pool.length < count) pool.push(...behavioural(seed + 1, count - pool.length + 3))

  const chosen: GeneratedQuestion[] = []
  const seen = new Set<string>()
  for (const question of pool) {
    if (chosen.length === count) break
    if (seen.has(question.prompt)) continue
    seen.add(question.prompt)
    chosen.push(question)
  }
  return chosen
}

function opener(snapshot: ResumeSnapshot | null): GeneratedQuestion {
  const role = mostRecentTitle(snapshot)
  if (role) {
    return {
      kind: 'MOTIVATION',
      prompt: `Walk me through your background, ending with your time as ${role}. Keep it to about two minutes.`,
      focusSkill: null,
      rationale:
        'Almost every interview opens with this, and almost every candidate rambles. It is the one answer worth rehearsing word for word.',
      expectedPoints: [
        'A one-line summary of who you are now',
        'Two or three steps that got you here, not every step',
        'Why the most recent role mattered',
        'A closing sentence pointing at the role you are applying for',
      ],
    }
  }
  return {
    kind: 'MOTIVATION',
    prompt: 'Tell me about yourself. Keep it to about two minutes.',
    focusSkill: null,
    rationale:
      'The opening question in almost every interview, and the one candidates most often waste.',
    expectedPoints: [
      'Who you are now, in one line',
      'Two or three steps that got you here',
      'Why you are in this conversation',
    ],
  }
}

function technical(
  snapshot: ResumeSnapshot | null,
  seed: number,
  max: number,
): GeneratedQuestion[] {
  if (!snapshot) return []

  const technical = snapshot.skills.filter(
    (skill) =>
      skill.category === 'LANGUAGE' ||
      skill.category === 'FRAMEWORK' ||
      skill.category === 'DATABASE' ||
      skill.category === 'CLOUD_DEVOPS',
  )
  const pool = seededShuffle(technical.length ? [...technical] : [...snapshot.skills], seed)

  const templates = [
    (skill: string) =>
      `You list ${skill} on your resume. Describe a specific problem you solved with it where the obvious approach did not work. What did you try first, and why was it wrong?`,
    (skill: string) =>
      `Someone on your team argues that ${skill} is the wrong choice for a new project. Make the case for it, then make the case against it.`,
    (skill: string) =>
      `What is something about ${skill} that you got wrong when you were learning it, and what made it click?`,
  ]

  const questions: GeneratedQuestion[] = []
  for (const skill of pool) {
    if (questions.length >= max) break
    questions.push({
      kind: 'TECHNICAL',
      prompt: templates[questions.length % templates.length](skill.name),
      focusSkill: skill.name,
      rationale: `You claimed ${skill.name} on your resume, so an interviewer is entitled to test it. A skill you cannot discuss concretely is worse than one you never listed.`,
      expectedPoints: [
        `A specific situation, not a general description of ${skill.name}`,
        'The decision you made and the alternative you rejected',
        'What actually happened as a result',
        'Honesty about the limits of what you know',
      ],
    })
  }
  return questions
}

function experienceProbes(
  snapshot: ResumeSnapshot | null,
  max: number,
): GeneratedQuestion[] {
  if (!snapshot) return []

  const templates = [
    (where: string) =>
      `Tell me about your work ${where}. What was the hardest technical decision you had to make, and what did you decide?`,
    (where: string) =>
      `${where} — what would you do differently if you started that work again tomorrow?`,
    (where: string) =>
      `Describe something you shipped ${where} that you were genuinely proud of. What made it good?`,
  ]

  const questions: GeneratedQuestion[] = []
  for (const entry of snapshot.experience) {
    if (questions.length >= max) break
    const where = describeRole(entry)
    if (!where) continue

    questions.push({
      kind: 'EXPERIENCE_PROBE',
      prompt: templates[questions.length % templates.length](where),
      focusSkill: null,
      rationale:
        'This is on your resume, so it will be asked about. An interviewer reads the entry and asks the first question it invites.',
      expectedPoints: [
        'Context: what the situation actually was',
        "Your specific contribution, not the team's",
        'The outcome, with a number if one exists',
        'What you would change now',
      ],
    })
  }
  return questions
}

function projectProbes(snapshot: ResumeSnapshot | null): GeneratedQuestion[] {
  if (!snapshot || !hasSection(snapshot, 'PROJECTS')) return []
  return [
    {
      kind: 'PROJECT_DEEP_DIVE',
      prompt:
        'Pick the project on your resume you are least confident defending, and walk me through how it works end to end.',
      focusSkill: null,
      rationale:
        'Interviewers pick the project you least want to discuss, not the one you rehearsed. Practising the uncomfortable one is the point.',
      expectedPoints: [
        'What it does, in one sentence, before any implementation detail',
        'The main design decision and why',
        'What is genuinely unfinished or weak about it',
        'What you learned that transfers to other work',
      ],
    },
  ]
}

function gapProbes(gaps: readonly string[], max: number): GeneratedQuestion[] {
  return gaps.slice(0, max).map((gap) => ({
    kind: 'GAP_PROBE' as const,
    prompt: `This role asks for ${gap}, and it does not appear on your resume. How would you get up to speed, and what experience of yours is closest to it?`,
    focusSkill: gap,
    rationale:
      'This is the question you are most likely to be asked and least likely to have prepared. A confident, honest answer to a gap does more good than pretending the gap is not there.',
    expectedPoints: [
      'Acknowledge it plainly — do not bluff',
      'Name the closest thing you have actually done',
      'A concrete plan, with a timeframe',
      'Evidence you have picked something up quickly before',
    ],
  }))
}

/**
 * The behavioural bank.
 *
 * Fixed rather than generated: these questions are near-universal, and inventing
 * variations of "tell me about a conflict" produces worse practice than the
 * standard wording a candidate will actually hear.
 */
const BEHAVIOURAL_BANK: readonly GeneratedQuestion[] = [
  {
    kind: 'BEHAVIOURAL',
    prompt: 'Tell me about a time you disagreed with a decision on your team. What did you do?',
    focusSkill: null,
    rationale:
      'Disagreement is the fastest way to learn how someone behaves when they are not in charge.',
    expectedPoints: [
      'The disagreement, stated fairly from both sides',
      'What you actually did, not what you thought',
      'The outcome, including if you were wrong',
    ],
  },
  {
    kind: 'BEHAVIOURAL',
    prompt:
      'Describe something you worked on that failed. What went wrong, and what was your part in it?',
    focusSkill: null,
    rationale:
      'Candidates who cannot name a failure read as either inexperienced or evasive. Both cost the offer.',
    expectedPoints: [
      'A real failure, not a disguised strength',
      'Your own contribution to it, owned plainly',
      'What specifically changed in how you work',
    ],
  },
  {
    kind: 'BEHAVIOURAL',
    prompt: 'Tell me about a time you had to learn something difficult quickly.',
    focusSkill: null,
    rationale:
      'Most early-career hiring is a bet on learning speed rather than on current knowledge.',
    expectedPoints: [
      'What you needed to learn and by when',
      'The method — how you actually went about it',
      'Proof it worked',
    ],
  },
  {
    kind: 'BEHAVIOURAL',
    prompt: 'Give me an example of feedback that was hard to hear. What did you do with it?',
    focusSkill: null,
    rationale:
      'How someone handles criticism predicts how they will be to work with far better than a list of skills.',
    expectedPoints: [
      'The feedback, quoted honestly',
      'Your first reaction, including if it was defensive',
      'The specific change you made',
    ],
  },
  {
    kind: 'BEHAVIOURAL',
    prompt:
      'Tell me about a time you had to deliver under a deadline you thought was unrealistic.',
    focusSkill: null,
    rationale:
      'This tests judgement under pressure and whether you communicate early or go quiet.',
    expectedPoints: [
      'Why the deadline was unrealistic',
      'What you cut, and who you told',
      'What shipped in the end',
    ],
  },
  {
    kind: 'BEHAVIOURAL',
    prompt: 'Describe a time you took ownership of something nobody had asked you to do.',
    focusSkill: null,
    rationale:
      'Initiative is claimed on every resume and demonstrable in almost none of them.',
    expectedPoints: [
      'How you noticed the gap',
      'What you did without being asked',
      'Whether it actually mattered',
    ],
  },
]

function behavioural(seed: number, max: number): GeneratedQuestion[] {
  return seededShuffle([...BEHAVIOURAL_BANK], seed).slice(0, Math.min(max, BEHAVIOURAL_BANK.length))
}

function mostRecentTitle(snapshot: ResumeSnapshot | null): string | null {
  if (!snapshot) return null
  const entry = snapshot.experience.find((item) => item.jobTitle)
  if (!entry) return null
  return entry.company ? `${entry.jobTitle} at ${entry.company}` : entry.jobTitle
}

function describeRole(entry: SnapshotExperience): string | null {
  if (entry.jobTitle && entry.company) return `as ${entry.jobTitle} at ${entry.company}`
  if (entry.company) return `at ${entry.company}`
  if (entry.jobTitle) return `as ${entry.jobTitle}`
  return null
}

/* ------------------------------------------------------------------ */
/* Answer scoring                                                     */
/* ------------------------------------------------------------------ */

/** Below this, there is not enough answer to judge. */
const TOO_SHORT_WORDS = 25
/** A good spoken answer runs roughly 90 seconds — about this many words. */
const IDEAL_MIN_WORDS = 70
const IDEAL_MAX_WORDS = 320

const SITUATION =
  /\b(when|while|during|at the time|the situation|we were|i was working|last (year|summer|semester|term)|in my (role|internship|project)|the (project|team|company|client))\b/i

const ACTION =
  /\b(i (built|wrote|designed|implemented|refactored|migrated|debugged|led|proposed|decided|chose|tested|automated|fixed|shipped|reviewed|investigated|rewrote|set up|added|removed|measured|profiled)|so i |what i did|my approach|i started by|i decided)\b/i

const RESULT =
  /\b(as a result|which (meant|led|reduced|improved|allowed)|the outcome|in the end|we ended up|this (reduced|improved|saved|increased|cut)|afterwards|the result was|it went live|we shipped|finally)\b/i

const REFLECTION =
  /\b(i learned|i would|next time|looking back|in hindsight|if i did it again|what i took from|i now)\b/i

const NUMBER = /(\d+\s*%|[$£€₹]\s*\d|\b\d[\d,.]*\b|\b(half|double|triple|twice)\b)/i

const HEDGE =
  /\b(kind of|sort of|i guess|i think maybe|probably|somewhat|a bit|i'm not really sure|or something|whatever|stuff like that|you know|basically|literally|honestly)\b/gi

export interface AnswerAssessment {
  readonly overallScore: number
  readonly structureScore: number
  readonly specificityScore: number
  readonly relevanceScore: number
  readonly clarityScore: number
  readonly wordCount: number
  readonly rubricVersion: string
  readonly strengths: readonly string[]
  readonly improvements: readonly string[]
}

export function evaluateAnswer(
  answer: string | null | undefined,
  kind: QuestionKind,
  expectedPoints: readonly string[],
  focusSkill: string | null,
): AnswerAssessment {
  const text = (answer ?? '').trim()
  const words = countWords(text)

  if (words < TOO_SHORT_WORDS) {
    return {
      overallScore: words === 0 ? 0 : 15,
      structureScore: 10, specificityScore: 10, relevanceScore: 10, clarityScore: 20,
      wordCount: words,
      rubricVersion: ANSWER_RUBRIC_VERSION,
      strengths: [],
      improvements: [
        'This is too short for an interviewer to judge. Aim for 90 seconds of speech — roughly 120 to 200 words.',
        'Start with the situation, then what you personally did, then what happened as a result.',
      ],
    }
  }

  const lower = text.toLowerCase()
  const strengths: string[] = []
  const improvements: string[] = []

  const structureScore = scoreStructure(lower, kind, strengths, improvements)
  const specificityScore = scoreSpecificity(text, strengths, improvements)
  const relevanceScore = scoreRelevance(lower, expectedPoints, focusSkill, strengths, improvements)
  const clarityScore = scoreClarity(text, lower, words, strengths, improvements)

  // Structure and specificity carry the most weight because they separate a
  // rehearsed answer from a real one. Clarity matters least: an interviewer will
  // forgive a rambling answer that contains evidence, and will not forgive a
  // polished one that contains none.
  const overallScore = Math.round(
    structureScore * 0.3 + specificityScore * 0.3 + relevanceScore * 0.25 + clarityScore * 0.15,
  )

  if (strengths.length === 0) {
    strengths.push('You answered in full sentences and stayed on the question.')
  }

  return {
    overallScore,
    structureScore, specificityScore, relevanceScore, clarityScore,
    wordCount: words,
    rubricVersion: ANSWER_RUBRIC_VERSION,
    strengths, improvements,
  }
}

function scoreStructure(
  lower: string,
  kind: QuestionKind,
  strengths: string[],
  improvements: string[],
): number {
  const hasSituation = SITUATION.test(lower)
  const hasAction = ACTION.test(lower)
  const hasResult = RESULT.test(lower)
  const hasReflection = REFLECTION.test(lower)

  let score = 25
  if (hasSituation) score += 20
  if (hasAction) score += 25
  if (hasResult) score += 20
  if (hasReflection) score += 10
  score = Math.min(100, score)

  const narrative =
    kind === 'BEHAVIOURAL' || kind === 'EXPERIENCE_PROBE' || kind === 'PROJECT_DEEP_DIVE'

  if (hasSituation && hasAction && hasResult) {
    strengths.push(
      'The answer moves through situation, action and outcome — which is what makes a story easy for an interviewer to follow and score.',
    )
  } else if (narrative) {
    if (!hasSituation) {
      improvements.push(
        'Open with one sentence of context: where you were, when, and what the problem was. Interviewers cannot judge a decision without it.',
      )
    }
    if (!hasAction) {
      improvements.push(
        'Say what you personally did, in the first person. "I decided", "I built", "I argued for" — not what the team did around you.',
      )
    }
    if (!hasResult) {
      improvements.push(
        'Finish with what happened. An answer with no outcome sounds like an activity rather than an achievement.',
      )
    }
  }

  const weCount = (lower.match(/\bwe\b/g) ?? []).length
  const iCount = (lower.match(/\bi\b/g) ?? []).length
  if (weCount > iCount * 2 && weCount >= 3) {
    score = Math.max(0, score - 15)
    improvements.push(
      'You said "we" far more than "I". The interviewer is hiring you, not your old team — be specific about your own contribution.',
    )
  }

  return score
}

function scoreSpecificity(text: string, strengths: string[], improvements: string[]): number {
  const numbers = (text.match(new RegExp(NUMBER.source, 'gi')) ?? []).length
  const properNouns = countProperNouns(text)

  let score = 20
  score += Math.min(45, numbers * 15)
  score += Math.min(35, properNouns * 7)
  score = Math.min(100, score)

  if (numbers >= 2) {
    strengths.push(
      'You used concrete numbers. That is the single biggest difference between an answer that is believed and one that is politely noted.',
    )
  } else if (numbers === 0) {
    improvements.push(
      'Nothing in this answer can be checked. Add scale or outcome — how many users, how much faster, how long it took, how many people.',
    )
  }

  if (properNouns === 0) {
    improvements.push(
      'Name the actual tools, systems, or products involved. Generic answers are indistinguishable from answers about nothing.',
    )
  }

  return score
}

function scoreRelevance(
  lower: string,
  expectedPoints: readonly string[],
  focusSkill: string | null,
  strengths: string[],
  improvements: string[],
): number {
  if (!expectedPoints.length) return 70

  const missed: string[] = []
  let covered = 0
  for (const point of expectedPoints) {
    if (coversPoint(lower, point)) covered++
    else missed.push(point)
  }

  let score = Math.round((100 * covered) / expectedPoints.length)

  if (focusSkill && !lower.includes(focusSkill.toLowerCase())) {
    score = Math.max(0, score - 25)
    improvements.push(
      `You were asked about ${focusSkill} and never named it. Use the interviewer's own vocabulary — it is what they are listening for.`,
    )
  }

  if (covered === expectedPoints.length) {
    strengths.push('You covered everything the question was actually fishing for.')
  } else if (missed.length) {
    improvements.push(`The answer did not clearly cover: ${missed[0].toLowerCase()}.`)
  }

  return score
}

/**
 * Whether an answer covers what an expected point was asking for.
 *
 * Not literal keyword overlap. An expected point is a *description* of what a
 * good answer contains — "Context: what the situation actually was" — and a
 * strong answer describing a specific incident will contain neither the word
 * "context" nor "situation". Matching those literally marks every good answer
 * as having missed the point, which is worse than not scoring relevance at all.
 *
 * So each cue is mapped to the *kind* of content it asks for and checked against
 * the same structural signals the structure axis uses. Only a cue matching no
 * known theme falls back to token overlap.
 */
function coversPoint(lowerAnswer: string, point: string): boolean {
  const cue = point.toLowerCase()
  let recognisedTheme = false

  const mentions = (...needles: string[]) => needles.some((needle) => cue.includes(needle))

  if (mentions('context', 'situation', 'where you were', 'the problem was', 'one sentence', 'summary')) {
    recognisedTheme = true
    if (!SITUATION.test(lowerAnswer)) return false
  }
  if (mentions('did', 'contribution', 'action', 'approach', 'decision', 'decided', 'chose', 'method', 'plan', 'own', 'acknowledge', 'honest', 'plainly')) {
    recognisedTheme = true
    if (!ACTION.test(lowerAnswer)) return false
  }
  if (mentions('outcome', 'result', 'happened', 'impact', 'proof', 'evidence', 'worked', 'shipped')) {
    recognisedTheme = true
    if (!RESULT.test(lowerAnswer) && !NUMBER.test(lowerAnswer)) return false
  }
  if (mentions('number', 'measur', 'scale', 'how many', 'how much')) {
    recognisedTheme = true
    if (!NUMBER.test(lowerAnswer)) return false
  }
  if (mentions('learn', 'differently', 'change', 'hindsight', 'next time', 'would you')) {
    recognisedTheme = true
    if (!REFLECTION.test(lowerAnswer)) return false
  }

  if (recognisedTheme) return true

  // An unrecognised cue is question-specific vocabulary. Loose overlap is the
  // honest fallback: a false positive is much cheaper than telling somebody
  // they missed a point they made.
  const stop = new Set([
    'what', 'with', 'that', 'this', 'your', 'from', 'they', 'them', 'have',
    'been', 'were', 'will', 'would', 'about', 'which', 'their', 'there',
    'than', 'then', 'into', 'just', 'only', 'some', 'such', 'more', 'most',
    'does', 'actually',
  ])
  let significant = 0
  let hits = 0
  for (const token of cue.split(/[^\p{L}]+/u)) {
    if (token.length < 4 || stop.has(token)) continue
    significant++
    if (lowerAnswer.includes(token)) hits++
  }
  return significant === 0 || hits * 2 >= significant
}

function scoreClarity(
  text: string,
  lower: string,
  words: number,
  strengths: string[],
  improvements: string[],
): number {
  let score = 100

  if (words < IDEAL_MIN_WORDS) {
    score -= 30
    improvements.push(
      `At ${words} words this is under 45 seconds of speech. Most interview answers want 90 seconds — there is room for a second example.`,
    )
  } else if (words > IDEAL_MAX_WORDS) {
    score -= 25
    improvements.push(
      `At ${words} words this runs past three minutes. Interviewers stop listening; cut the setup, keep the decision and the result.`,
    )
  } else {
    strengths.push(
      'Length is right for a spoken answer — long enough to be substantive, short enough to be heard.',
    )
  }

  const hedges = (lower.match(HEDGE) ?? []).length
  if (hedges >= 3) {
    score -= Math.min(30, hedges * 6)
    improvements.push(
      `You hedged ${hedges} times ("kind of", "I guess", "you know"). Hedging makes a true claim sound uncertain. State it, then qualify once if you must.`,
    )
  }

  const averageSentence = averageSentenceLength(text)
  if (averageSentence > 38) {
    score -= 12
    improvements.push(
      `Your sentences average ${Math.round(averageSentence)} words. Long sentences are hard to follow out loud — break them.`,
    )
  }

  return Math.max(0, Math.min(100, score))
}

/**
 * A capitalised word that is not the first of a sentence is usually a product,
 * company, or technology name — the thing that makes an answer checkable.
 */
function countProperNouns(text: string): number {
  const words = text.split(/\s+/)
  let count = 0
  for (let i = 1; i < words.length; i++) {
    const word = words[i].replace(/[^\p{L}\p{N}+#.]/gu, '')
    if (word.length < 2) continue
    const previous = words[i - 1]
    const previousEndedSentence = /[.!?]$/.test(previous)
    if (!previousEndedSentence && word[0] === word[0].toUpperCase() && /\p{L}/u.test(word[0])) {
      count++
    }
  }
  return count
}

function averageSentenceLength(text: string): number {
  const sentences = text.split(/[.!?]+/)
  let counted = 0
  let words = 0
  for (const sentence of sentences) {
    const length = countWords(sentence)
    if (length > 0) {
      counted++
      words += length
    }
  }
  return counted === 0 ? 0 : words / counted
}

function countWords(text: string): number {
  const stripped = (text ?? '').trim()
  return stripped ? stripped.split(/\s+/).length : 0
}
