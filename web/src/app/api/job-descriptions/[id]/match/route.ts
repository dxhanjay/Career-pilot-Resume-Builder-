import { NextResponse } from 'next/server'
import { handler, notFound, ok, readJson, requireUser } from '@/lib/api'
import { getLatestMatch, runMatch } from '@/lib/analysis-service'
import { runMatchSchema } from '@/lib/validation'

export const runtime = 'nodejs'
export const maxDuration = 60

type Params = { params: Promise<{ id: string }> }

export const GET = handler(async (_request: Request, { params }: Params): Promise<NextResponse> => {
  const user = await requireUser()
  const { id } = await params

  const match = await getLatestMatch(id, user.id)
  if (!match) throw notFound('Match')
  return ok(match)
})

export const POST = handler(async (request: Request, { params }: Params): Promise<NextResponse> => {
  const user = await requireUser()
  const { id } = await params
  const input = await readJson(request, runMatchSchema)

  return ok(await runMatch(id, input.resumeId, user.id))
})
