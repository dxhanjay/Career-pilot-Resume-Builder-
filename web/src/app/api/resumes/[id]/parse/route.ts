import { NextResponse } from 'next/server'
import { handler, ok, requireUser } from '@/lib/api'
import { getStructuredParse, parseResume } from '@/lib/parsing-service'
import { runAtsQuietly } from '@/lib/analysis-service'

export const runtime = 'nodejs'
export const maxDuration = 60

type Params = { params: Promise<{ id: string }> }

/** "Here is exactly what the machine saw" — the screen the product exists for. */
export const GET = handler(async (_request: Request, { params }: Params): Promise<NextResponse> => {
  const user = await requireUser()
  const { id } = await params
  return ok(await getStructuredParse(id, user.id))
})

/** Re-reads the stored file. Used after a parse failed, or to pick up parser fixes. */
export const POST = handler(async (_request: Request, { params }: Params): Promise<NextResponse> => {
  const user = await requireUser()
  const { id } = await params

  const result = await parseResume(id, user.id)
  if (result.status === 'SUCCEEDED') await runAtsQuietly(id, user.id)

  return ok(result)
})
