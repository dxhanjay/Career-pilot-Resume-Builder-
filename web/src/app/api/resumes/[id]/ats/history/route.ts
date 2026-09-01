import { NextResponse } from 'next/server'
import { handler, ok, requireUser } from '@/lib/api'
import { getAtsHistory } from '@/lib/analysis-service'

export const runtime = 'nodejs'

type Params = { params: Promise<{ id: string }> }

export const GET = handler(async (_request: Request, { params }: Params): Promise<NextResponse> => {
  const user = await requireUser()
  const { id } = await params
  return ok(await getAtsHistory(id, user.id))
})
