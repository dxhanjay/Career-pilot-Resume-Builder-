import { NextResponse } from 'next/server'
import { handler, ok, requireUser } from '@/lib/api'
import { abandonSession, getSession } from '@/lib/interview-service'

export const runtime = 'nodejs'

type Params = { params: Promise<{ id: string }> }

export const GET = handler(async (_request: Request, { params }: Params): Promise<NextResponse> => {
  const user = await requireUser()
  const { id } = await params
  return ok(await getSession(id, user.id))
})

/** Marks it abandoned and keeps the record. A walked-away interview is a data point. */
export const DELETE = handler(async (_request: Request, { params }: Params): Promise<NextResponse> => {
  const user = await requireUser()
  const { id } = await params
  await abandonSession(id, user.id)
  return ok({ abandoned: id })
})
