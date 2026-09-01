import { NextResponse } from 'next/server'
import { handler, ok, requireUser } from '@/lib/api'
import { completeSession } from '@/lib/interview-service'

export const runtime = 'nodejs'

type Params = { params: Promise<{ id: string }> }

export const POST = handler(async (_request: Request, { params }: Params): Promise<NextResponse> => {
  const user = await requireUser()
  const { id } = await params
  return ok(await completeSession(id, user.id))
})
