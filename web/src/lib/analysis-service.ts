import { and, desc, eq } from 'drizzle-orm'
import { db } from '@/db'
import { atsAnalyses, atsFindings, jdMatches, jdMatchSkills, jobDescriptions } from '@/db/schema'
import {
  ATS_CATEGORIES,
  SCORE_BANDS,
  evaluateAts,
  type AtsCategory,
  type ScoreBand,
} from '@/domain/ats'
import { MATCH_BANDS, matchResume, parseJobPosting, type MatchBand } from '@/domain/matching'
import { buildSnapshot } from './parsing-service'
import { notFound } from './api'

/**
 * Scoring a resume, and matching it against a posting.
 *
 * Both run synchronously. They are pure computation over text already in the
 * database — single-digit milliseconds — and making a client poll for a result
 * that was ready before the response would have been written buys only latency
 * and a spinner.
 *
 * Both are append-only. Re-running after an edit is the entire point of the
 * product's closing promise, and an overwrite would erase the evidence that
 * anything improved.
 */

/* ------------------------------------------------------------------ */
/* ATS                                                                */
/* ------------------------------------------------------------------ */

export async function runAtsAnalysis(resumeId: string, userId: string) {
  const { parseId, snapshot } = await buildSnapshot(resumeId, userId)
  const assessment = evaluateAts(snapshot)

  const [analysis] = await db
    .insert(atsAnalyses)
    .values({
      resumeId, parseId, userId,
      overallScore: assessment.overallScore,
      band: assessment.band,
      parseabilityScore: assessment.categoryScores.PARSEABILITY,
      structureScore: assessment.categoryScores.STRUCTURE,
      contentScore: assessment.categoryScores.CONTENT,
      skillsScore: assessment.categoryScores.SKILLS,
      contactScore: assessment.categoryScores.CONTACT,
      rubricVersion: assessment.rubricVersion,
    })
    .returning()

  if (assessment.findings.length) {
    await db.insert(atsFindings).values(
      assessment.findings.map((finding, order) => ({
        analysisId: analysis.id,
        code: finding.code.slice(0, 60),
        category: finding.category,
        severity: finding.severity,
        title: finding.title.slice(0, 200),
        detail: finding.detail,
        recommendation: finding.recommendation,
        evidence: finding.evidence,
        lineStart: finding.lineStart,
        lineEnd: finding.lineEnd,
        pointsLost: Math.max(0, finding.pointsLost),
        displayOrder: order,
      })),
    )
  }

  return getAtsAnalysis(analysis.id, userId)
}

/** Runs the analysis but never lets a failure break the caller. */
export async function runAtsQuietly(resumeId: string, userId: string): Promise<void> {
  try {
    await runAtsAnalysis(resumeId, userId)
  } catch (error) {
    // The extracted text is valuable on its own, and the user can retry the
    // analysis from the UI. Failing the upload over this would be worse.
    console.warn('Automatic ATS analysis failed', { resumeId, error })
  }
}

export async function getAtsAnalysis(analysisId: string, userId: string) {
  const [analysis] = await db
    .select()
    .from(atsAnalyses)
    .where(and(eq(atsAnalyses.id, analysisId), eq(atsAnalyses.userId, userId)))
    .limit(1)

  if (!analysis) throw notFound('Analysis')

  const findings = await db
    .select()
    .from(atsFindings)
    .where(eq(atsFindings.analysisId, analysis.id))
    .orderBy(atsFindings.displayOrder)

  return shapeAnalysis(analysis, findings)
}

export async function getLatestAtsAnalysis(resumeId: string, userId: string) {
  const [analysis] = await db
    .select()
    .from(atsAnalyses)
    .where(and(eq(atsAnalyses.resumeId, resumeId), eq(atsAnalyses.userId, userId)))
    .orderBy(desc(atsAnalyses.createdAt))
    .limit(1)

  if (!analysis) return null

  const findings = await db
    .select()
    .from(atsFindings)
    .where(eq(atsFindings.analysisId, analysis.id))
    .orderBy(atsFindings.displayOrder)

  return shapeAnalysis(analysis, findings)
}

export async function getAtsHistory(resumeId: string, userId: string) {
  const rows = await db
    .select()
    .from(atsAnalyses)
    .where(and(eq(atsAnalyses.resumeId, resumeId), eq(atsAnalyses.userId, userId)))
    .orderBy(atsAnalyses.createdAt)

  const points = rows.map((row) => ({
    analysisId: row.id,
    overallScore: row.overallScore,
    band: row.band,
    createdAt: row.createdAt,
  }))

  const first = points[0]?.overallScore ?? 0
  const latest = points[points.length - 1]?.overallScore ?? 0

  return { resumeId, points, firstScore: first, latestScore: latest, delta: latest - first }
}

function shapeAnalysis(
  analysis: typeof atsAnalyses.$inferSelect,
  findings: Array<typeof atsFindings.$inferSelect>,
) {
  const band = analysis.band as ScoreBand
  const scores: Record<AtsCategory, number> = {
    PARSEABILITY: analysis.parseabilityScore,
    STRUCTURE: analysis.structureScore,
    CONTENT: analysis.contentScore,
    SKILLS: analysis.skillsScore,
    CONTACT: analysis.contactScore,
  }

  const passCount = findings.filter((finding) => finding.severity === 'PASS').length

  return {
    id: analysis.id,
    resumeId: analysis.resumeId,
    overallScore: analysis.overallScore,
    band,
    bandLabel: SCORE_BANDS[band]?.label ?? band,
    bandSummary: SCORE_BANDS[band]?.summary ?? '',
    // The rubric's own description travels with the score, so the client never
    // hardcodes an explanation that can drift from what the rule actually does.
    categories: (Object.keys(ATS_CATEGORIES) as AtsCategory[]).map((key) => ({
      category: key,
      displayName: ATS_CATEGORIES[key].label,
      description: ATS_CATEGORIES[key].description,
      weight: ATS_CATEGORIES[key].weight,
      score: scores[key],
    })),
    findings: findings.map((finding) => ({
      id: finding.id,
      code: finding.code,
      category: finding.category,
      categoryLabel: ATS_CATEGORIES[finding.category as AtsCategory]?.label ?? finding.category,
      severity: finding.severity,
      title: finding.title,
      detail: finding.detail,
      recommendation: finding.recommendation,
      evidence: finding.evidence,
      lineStart: finding.lineStart,
      lineEnd: finding.lineEnd,
      pointsLost: finding.pointsLost,
    })),
    problemCount: findings.length - passCount,
    passCount,
    rubricVersion: analysis.rubricVersion,
    createdAt: analysis.createdAt,
  }
}

/* ------------------------------------------------------------------ */
/* Matching                                                           */
/* ------------------------------------------------------------------ */

export async function runMatch(postingId: string, resumeId: string, userId: string) {
  const [posting] = await db
    .select()
    .from(jobDescriptions)
    .where(and(eq(jobDescriptions.id, postingId), eq(jobDescriptions.userId, userId)))
    .limit(1)

  if (!posting) throw notFound('Job description')

  const { snapshot } = await buildSnapshot(resumeId, userId)
  const parsed = parseJobPosting(posting.rawText)
  const outcome = matchResume(snapshot, parsed)

  const [match] = await db
    .insert(jdMatches)
    .values({
      jobDescriptionId: postingId, resumeId, userId,
      overallScore: outcome.overallScore,
      band: outcome.band,
      requiredSkillScore: outcome.requiredSkillScore,
      optionalSkillScore: outcome.optionalSkillScore,
      titleScore: outcome.titleScore,
      experienceScore: outcome.experienceScore,
      matchedCount: outcome.skills.filter((s) => s.verdict === 'MATCHED').length,
      missingCount: outcome.skills.filter((s) => s.verdict === 'MISSING').length,
      rubricVersion: outcome.rubricVersion,
    })
    .returning()

  if (outcome.skills.length) {
    await db.insert(jdMatchSkills).values(
      outcome.skills.map((skill) => ({
        matchId: match.id,
        normalizedName: skill.normalizedName.slice(0, 100),
        displayName: skill.displayName.slice(0, 100),
        category: skill.category,
        status: skill.verdict,
        required: skill.required,
        priority: skill.priority,
        resumeEvidence: skill.resumeEvidence,
        resumeLine: skill.resumeLine,
        jdEvidence: skill.jdEvidence,
        jdLine: skill.jdLine,
      })),
    )
  }

  return shapeMatch(match, await matchSkills(match.id), outcome.suggestions, posting)
}

export async function getLatestMatch(postingId: string, userId: string) {
  const [posting] = await db
    .select()
    .from(jobDescriptions)
    .where(and(eq(jobDescriptions.id, postingId), eq(jobDescriptions.userId, userId)))
    .limit(1)

  if (!posting) throw notFound('Job description')

  const [match] = await db
    .select()
    .from(jdMatches)
    .where(and(eq(jdMatches.jobDescriptionId, postingId), eq(jdMatches.userId, userId)))
    .orderBy(desc(jdMatches.createdAt))
    .limit(1)

  if (!match) return null

  // Suggestions are recomputed rather than stored: they are a pure function of
  // two documents that are both already persisted, so a table for them would
  // only add a way for the two to disagree. Re-reading an old match after
  // editing the resume then shows advice about the resume as it is now.
  let suggestions: Awaited<ReturnType<typeof matchResume>>['suggestions'] = []
  try {
    const { snapshot } = await buildSnapshot(match.resumeId, userId)
    suggestions = matchResume(snapshot, parseJobPosting(posting.rawText)).suggestions
  } catch {
    // The resume may since have been deleted or re-uploaded. The stored
    // verdicts are still valid history; only the advice is unavailable.
  }

  return shapeMatch(match, await matchSkills(match.id), suggestions, posting)
}

const matchSkills = (matchId: string) =>
  db
    .select()
    .from(jdMatchSkills)
    .where(eq(jdMatchSkills.matchId, matchId))
    .orderBy(desc(jdMatchSkills.priority))

function shapeMatch(
  match: typeof jdMatches.$inferSelect,
  skills: Array<typeof jdMatchSkills.$inferSelect>,
  suggestions: Awaited<ReturnType<typeof matchResume>>['suggestions'],
  posting: typeof jobDescriptions.$inferSelect,
) {
  const band = match.band as MatchBand
  const byStatus = (status: string) =>
    skills
      .filter((skill) => skill.status === status)
      .map((skill) => ({
        name: skill.displayName,
        normalizedName: skill.normalizedName,
        category: skill.category,
        required: skill.required,
        priority: skill.priority,
        resumeEvidence: skill.resumeEvidence,
        resumeLine: skill.resumeLine,
        jdEvidence: skill.jdEvidence,
        jdLine: skill.jdLine,
      }))

  return {
    id: match.id,
    jobDescriptionId: match.jobDescriptionId,
    resumeId: match.resumeId,
    jobTitle: posting.title,
    company: posting.company,
    overallScore: match.overallScore,
    band,
    bandLabel: MATCH_BANDS[band]?.label ?? band,
    advice: MATCH_BANDS[band]?.advice ?? '',
    requiredSkillScore: match.requiredSkillScore,
    optionalSkillScore: match.optionalSkillScore,
    titleScore: match.titleScore,
    experienceScore: match.experienceScore,
    matchedCount: match.matchedCount,
    missingCount: match.missingCount,
    matched: byStatus('MATCHED'),
    missing: byStatus('MISSING'),
    extra: byStatus('EXTRA'),
    suggestions,
    createdAt: match.createdAt,
  }
}
