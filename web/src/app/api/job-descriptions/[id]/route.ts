import { NextResponse } from 'next/server'
import { and, eq } from 'drizzle-orm'
import { db } from '@/db'
import { jobDescriptions } from '@/db/schema'
import { handler, notFound, ok, requireUser } from '@/lib/api'

export const runtime = 'nodejs'

type Params = { params: Promise<{ id: string }> }

export const GET = handler(async (_request: Request, { params }: Params): Promise<NextResponse> => {
  const user = await requireUser()
  const { id } = await params

  const [posting] = await db
    .select()
    .from(jobDescriptions)
    .where(and(eq(jobDescriptions.id, id), eq(jobDescriptions.userId, user.id)))
    .limit(1)

  if (!posting) throw notFound('Job description')
  return ok({ ...posting, characterCount: posting.rawText.length })
})

export const DELETE = handler(async (_request: Request, { params }: Params): Promise<NextResponse> => {
  const user = await requireUser()
  const { id } = await params

  const deleted = await db
    .delete(jobDescriptions)
    .where(and(eq(jobDescriptions.id, id), eq(jobDescriptions.userId, user.id)))
    .returning({ id: jobDescriptions.id })

  if (!deleted.length) throw notFound('Job description')
  return ok({ deleted: id })
})
