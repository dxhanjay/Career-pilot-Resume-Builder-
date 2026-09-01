import { NextResponse } from 'next/server'
import { handler, notFound, ok, requireUser } from '@/lib/api'
import { getLatestAtsAnalysis, runAtsAnalysis } from '@/lib/analysis-service'

export const runtime = 'nodejs'
export const maxDuration = 60

type Params = { params: Promise<{ id: string }> }

export const GET = handler(async (_request: Request, { params }: Params): Promise<NextResponse> => {
  const user = await requireUser()
  const { id } = await params

  const analysis = await getLatestAtsAnalysis(id, user.id)
  if (!analysis) throw notFound('ATS analysis')
  return ok(analysis)
})

/**
 * Stores a new run rather than replacing the previous one, so re-analysing
 * after an edit builds the score history the product's closing promise depends
 * on.
 */
export const POST = handler(async (_request: Request, { params }: Params): Promise<NextResponse> => {
  const user = await requireUser()
  const { id } = await params
  return ok(await runAtsAnalysis(id, user.id))
})
