import { NextResponse } from 'next/server'
import { avg, count, desc, eq } from 'drizzle-orm'
import { db } from '@/db'
import { interviewSessions, jdMatches, jobDescriptions, resumes } from '@/db/schema'
import { handler, ok, requireUser } from '@/lib/api'
import { getLatestAtsAnalysis } from '@/lib/analysis-service'

export const runtime = 'nodejs'

const RECENT_RESUMES = 5

export const GET = handler(async (): Promise<NextResponse> => {
  const user = await requireUser()

  const [recent, postingCount, matchCount, interviewStats] = await Promise.all([
    db
      .select({
        id: resumes.id,
        originalFilename: resumes.originalFilename,
        status: resumes.status,
        isPrimary: resumes.isPrimary,
        createdAt: resumes.createdAt,
      })
      .from(resumes)
      .where(eq(resumes.userId, user.id))
      .orderBy(desc(resumes.createdAt))
      .limit(RECENT_RESUMES),
    db.select({ value: count() }).from(jobDescriptions).where(eq(jobDescriptions.userId, user.id)),
    db.select({ value: count() }).from(jdMatches).where(eq(jdMatches.userId, user.id)),
    db
      .select({ total: count(), average: avg(interviewSessions.overallScore) })
      .from(interviewSessions)
      .where(eq(interviewSessions.userId, user.id)),
  ])

  // The primary resume is what the headline number is about. If none is
  // flagged, the most recent upload is the honest stand-in.
  const focus = recent.find((resume) => resume.isPrimary) ?? recent[0] ?? null

  // Not analysed yet is the normal state for a new account, not an error. The
  // dashboard's job is to say so and offer the button.
  const latestScore = focus ? await getLatestAtsAnalysis(focus.id, user.id) : null

  return ok({
    recentResumes: recent,
    focusResume: focus,
    latestScore: latestScore
      ? {
          analysisId: latestScore.id,
          overallScore: latestScore.overallScore,
          band: latestScore.band,
          bandLabel: latestScore.bandLabel,
          bandSummary: latestScore.bandSummary,
          problemCount: latestScore.problemCount,
          createdAt: latestScore.createdAt,
        }
      : null,
    counts: {
      resumes: recent.length,
      jobDescriptions: postingCount[0]?.value ?? 0,
      matches: matchCount[0]?.value ?? 0,
      interviews: interviewStats[0]?.total ?? 0,
    },
    averageInterviewScore: interviewStats[0]?.average
      ? Number(interviewStats[0].average)
      : null,
  })
})
