import { NextResponse } from 'next/server'
import { desc, eq } from 'drizzle-orm'
import { db } from '@/db'
import { jdMatches, jobDescriptions } from '@/db/schema'
import { conflict, handler, ok, readJson, requireUser } from '@/lib/api'
import { jobDescriptionSchema } from '@/lib/validation'

export const runtime = 'nodejs'

/** Every posting is a candidate for repeated matching; unbounded is unbounded cost. */
const MAX_POSTINGS = 50

export const GET = handler(async (): Promise<NextResponse> => {
  const user = await requireUser()

  // rawText excluded from the list for the same reason resume bytes are: a page
  // of twenty postings carrying their full text is slow for no benefit.
  const rows = await db
    .select({
      id: jobDescriptions.id,
      title: jobDescriptions.title,
      company: jobDescriptions.company,
      location: jobDescriptions.location,
      sourceUrl: jobDescriptions.sourceUrl,
      characterCount: jobDescriptions.rawText,
      createdAt: jobDescriptions.createdAt,
    })
    .from(jobDescriptions)
    .where(eq(jobDescriptions.userId, user.id))
    .orderBy(desc(jobDescriptions.createdAt))

  const latest = await db
    .select({
      jobDescriptionId: jdMatches.jobDescriptionId,
      overallScore: jdMatches.overallScore,
      band: jdMatches.band,
      createdAt: jdMatches.createdAt,
    })
    .from(jdMatches)
    .where(eq(jdMatches.userId, user.id))
    .orderBy(desc(jdMatches.createdAt))

  const scoreByPosting = new Map<string, (typeof latest)[number]>()
  for (const match of latest) {
    if (!scoreByPosting.has(match.jobDescriptionId)) {
      scoreByPosting.set(match.jobDescriptionId, match)
    }
  }

  return ok(
    rows.map((row) => {
      const match = scoreByPosting.get(row.id)
      return {
        ...row,
        characterCount: row.characterCount.length,
        latestScore: match?.overallScore ?? null,
        latestBand: match?.band ?? null,
        latestMatchedAt: match?.createdAt ?? null,
      }
    }),
  )
})

export const POST = handler(async (request: Request): Promise<NextResponse> => {
  const user = await requireUser()
  const input = await readJson(request, jobDescriptionSchema)

  const existing = await db
    .select({ id: jobDescriptions.id })
    .from(jobDescriptions)
    .where(eq(jobDescriptions.userId, user.id))

  if (existing.length >= MAX_POSTINGS) {
    throw conflict(
      `You have reached the limit of ${MAX_POSTINGS} saved job descriptions. Delete one to add another.`,
    )
  }

  const [posting] = await db
    .insert(jobDescriptions)
    .values({
      userId: user.id,
      title: input.title,
      company: input.company ?? null,
      location: input.location ?? null,
      sourceUrl: input.sourceUrl ?? null,
      rawText: input.rawText,
    })
    .returning({ id: jobDescriptions.id, title: jobDescriptions.title })

  return ok(posting, 201)
})
