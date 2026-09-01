import { NextResponse } from 'next/server'
import { db } from '@/db'
import { users } from '@/db/schema'
import { conflict, handler, ok, readJson } from '@/lib/api'
import { createSession, findUserByEmail, hashPassword } from '@/lib/auth'
import { registerSchema } from '@/lib/validation'

export const runtime = 'nodejs'

export const POST = handler(async (request: Request): Promise<NextResponse> => {
  const input = await readJson(request, registerSchema)

  const existing = await findUserByEmail(input.email)
  if (existing) {
    // Deliberately explicit. Enumeration resistance matters on password reset,
    // where the attacker learns something the user cannot see; on registration
    // the person is already looking at a form that must tell them why it
    // failed, and "something went wrong" would just make them try again.
    throw conflict('An account with that email address already exists. Try signing in.')
  }

  const passwordHash = await hashPassword(input.password)

  const [user] = await db
    .insert(users)
    .values({ email: input.email.toLowerCase(), passwordHash, fullName: input.fullName })
    .returning({ id: users.id, email: users.email, fullName: users.fullName, role: users.role })

  // Signed in immediately. Email verification would be the safer default, but
  // there is no SMTP provider configured, so enforcing it would leave every new
  // account stranded waiting for a message that never arrives.
  await createSession(user.id, request.headers.get('user-agent'))

  return ok(user, 201)
})
