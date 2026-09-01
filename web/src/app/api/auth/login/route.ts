import { NextResponse } from 'next/server'
import { HttpError, handler, ok, readJson, unauthorized } from '@/lib/api'
import {
  createSession, findUserByEmail, isLocked,
  recordFailedLogin, recordSuccessfulLogin, verifyPassword,
} from '@/lib/auth'
import { loginSchema } from '@/lib/validation'

export const runtime = 'nodejs'

export const POST = handler(async (request: Request): Promise<NextResponse> => {
  const input = await readJson(request, loginSchema)
  const user = await findUserByEmail(input.email)

  // One message for "no such account" and "wrong password", so the form cannot
  // be used to discover which addresses have accounts here.
  const rejected = unauthorized('That email address and password do not match.')

  if (!user) {
    // Hash anyway. Returning immediately makes an unknown address measurably
    // faster to reject than a known one, and that timing difference is itself
    // an enumeration oracle.
    await verifyPassword(input.password, '$2a$11$invalidinvalidinvalidinvalidinvalidinvalidinvalidinvalidin')
    throw rejected
  }

  if (isLocked(user)) {
    throw new HttpError(
      423, 'ACCOUNT_LOCKED',
      'Too many failed sign-in attempts. Try again in a few minutes.',
    )
  }

  if (user.status !== 'ACTIVE') {
    throw new HttpError(403, 'ACCOUNT_SUSPENDED', 'This account has been suspended.')
  }

  const valid = await verifyPassword(input.password, user.passwordHash)
  if (!valid) {
    await recordFailedLogin(user)
    throw rejected
  }

  await recordSuccessfulLogin(user.id)
  await createSession(user.id, request.headers.get('user-agent'))

  return ok({ id: user.id, email: user.email, fullName: user.fullName, role: user.role })
})
