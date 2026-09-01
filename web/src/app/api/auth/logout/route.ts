import { NextResponse } from 'next/server'
import { handler, ok } from '@/lib/api'
import { destroySession } from '@/lib/auth'

export const runtime = 'nodejs'

// Unauthenticated on purpose. Requiring a valid session to end a session means a
// user whose session has already expired cannot clear it, and revoking a
// credential you already hold is not an attack.
export const POST = handler(async (): Promise<NextResponse> => {
  await destroySession()
  return ok({ signedOut: true })
})
