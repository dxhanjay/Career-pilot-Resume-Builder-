import { NextResponse } from 'next/server'
import { handler, ok, readJson, requireUser } from '@/lib/api'
import { listSessions, startSession } from '@/lib/interview-service'
import { startInterviewSchema } from '@/lib/validation'

export const runtime = 'nodejs'
export const maxDuration = 60

export const GET = handler(async (): Promise<NextResponse> => {
  const user = await requireUser()
  return ok(await listSessions(user.id))
})

export const POST = handler(async (request: Request): Promise<NextResponse> => {
  const user = await requireUser()
  const input = await readJson(request, startInterviewSchema)

  return ok(
    await startSession(user.id, {
      focus: input.focus,
      resumeId: input.resumeId ?? null,
      jobDescriptionId: input.jobDescriptionId ?? null,
      questionCount: input.questionCount,
    }),
    201,
  )
})
