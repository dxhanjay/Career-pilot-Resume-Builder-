import { NextResponse } from 'next/server'
import { and, eq, sql } from 'drizzle-orm'
import { db } from '@/db'
import { resumes } from '@/db/schema'
import { handler, notFound, ok, requireUser } from '@/lib/api'

export const runtime = 'nodejs'

type Params = { params: Promise<{ id: string }> }

export const GET = handler(async (_request: Request, { params }: Params): Promise<NextResponse> => {
  const user = await requireUser()
  const { id } = await params

  const [resume] = await db
    .select({
      id: resumes.id,
      originalFilename: resumes.originalFilename,
      mimeType: resumes.mimeType,
      sizeBytes: resumes.sizeBytes,
      status: resumes.status,
      isPrimary: resumes.isPrimary,
      createdAt: resumes.createdAt,
    })
    .from(resumes)
    // 404 rather than 403 when it belongs to someone else: telling an intruder
    // that a resource exists but is not theirs is itself a disclosure.
    .where(and(eq(resumes.id, id), eq(resumes.userId, user.id)))
    .limit(1)

  if (!resume) throw notFound('Resume')
  return ok(resume)
})

export const PATCH = handler(async (_request: Request, { params }: Params): Promise<NextResponse> => {
  const user = await requireUser()
  const { id } = await params

  const [resume] = await db
    .select({ id: resumes.id })
    .from(resumes)
    .where(and(eq(resumes.id, id), eq(resumes.userId, user.id)))
    .limit(1)

  if (!resume) throw notFound('Resume')

  // Cleared first, then set. A partial unique index allows one primary per
  // user, so setting before clearing would collide with the existing one.
  await db
    .update(resumes)
    .set({ isPrimary: false, updatedAt: new Date() })
    .where(and(eq(resumes.userId, user.id), eq(resumes.isPrimary, true)))

  await db
    .update(resumes)
    .set({ isPrimary: true, updatedAt: new Date() })
    .where(eq(resumes.id, id))

  return ok({ id, isPrimary: true })
})

export const DELETE = handler(async (_request: Request, { params }: Params): Promise<NextResponse> => {
  const user = await requireUser()
  const { id } = await params

  const deleted = await db
    .delete(resumes)
    .where(and(eq(resumes.id, id), eq(resumes.userId, user.id)))
    .returning({ id: resumes.id })

  if (!deleted.length) throw notFound('Resume')
  return ok({ deleted: id })
})
