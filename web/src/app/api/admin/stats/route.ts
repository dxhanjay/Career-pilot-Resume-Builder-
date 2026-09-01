import { NextResponse } from 'next/server'
import { count, eq, gte, sql } from 'drizzle-orm'
import { db } from '@/db'
import { atsAnalyses, interviewSessions, jdMatches, resumes, users } from '@/db/schema'
import { handler, ok, requireAdmin } from '@/lib/api'

export const runtime = 'nodejs'

/** Counts only. Operational visibility should not require reading anyone's documents. */
export const GET = handler(async (): Promise<NextResponse> => {
  await requireAdmin()

  const weekAgo = new Date(Date.now() - 7 * 24 * 60 * 60 * 1000)

  const [totalUsers, activeUsers, suspended, recent, resumeCount, analyses, matches, interviews] =
    await Promise.all([
      db.select({ value: count() }).from(users),
      db.select({ value: count() }).from(users).where(eq(users.status, 'ACTIVE')),
      db.select({ value: count() }).from(users).where(eq(users.status, 'SUSPENDED')),
      db.select({ value: count() }).from(users).where(gte(users.createdAt, weekAgo)),
      db.select({ value: count() }).from(resumes),
      db.select({ value: count() }).from(atsAnalyses),
      db.select({ value: count() }).from(jdMatches),
      db.select({ value: count() }).from(interviewSessions),
    ])

  return ok({
    totalUsers: totalUsers[0]?.value ?? 0,
    activeUsers: activeUsers[0]?.value ?? 0,
    suspendedUsers: suspended[0]?.value ?? 0,
    newUsersLast7Days: recent[0]?.value ?? 0,
    totalResumes: resumeCount[0]?.value ?? 0,
    totalAnalyses: analyses[0]?.value ?? 0,
    totalMatches: matches[0]?.value ?? 0,
    totalInterviews: interviews[0]?.value ?? 0,
  })
})
