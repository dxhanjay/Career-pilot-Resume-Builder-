import { NextResponse } from 'next/server'
import { handler, ok, readJson, requireUser } from '@/lib/api'
import { submitAnswer } from '@/lib/interview-service'
import { submitAnswerSchema } from '@/lib/validation'

export const runtime = 'nodejs'

type Params = { params: Promise<{ id: string; questionId: string }> }

/**
 * Scored immediately. Answering again replaces the previous answer rather than
 * adding one, so a candidate can rewrite and watch the score move — which is
 * the only way this is practice rather than a test.
 */
export const POST = handler(async (request: Request, { params }: Params): Promise<NextResponse> => {
  const user = await requireUser()
  const { id, questionId } = await params
  const input = await readJson(request, submitAnswerSchema)

  return ok(await submitAnswer(id, questionId, user.id, input.answer))
})
