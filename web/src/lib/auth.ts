import { and, eq, gt, isNull, sql } from 'drizzle-orm'
import { cookies } from 'next/headers'
import bcrypt from 'bcryptjs'
import { db } from '@/db'
import { sessions, users, type User } from '@/db/schema'

/**
 * Sessions.
 *
 * An opaque random token in an httpOnly cookie, hashed in the database. Not a
 * JWT, and not localStorage.
 *
 * A JWT cannot be revoked without a server-side blocklist, at which point the
 * statelessness that justified it is gone; signing out here is one UPDATE and
 * takes effect on the very next request. And a token in localStorage is
 * readable by any script that gets onto the page, which is exactly what an XSS
 * bug gives an attacker — an httpOnly cookie is not.
 */

export const SESSION_COOKIE = 'cp_session'

/** Long enough not to nag, short enough that a forgotten shared device expires. */
const SESSION_TTL_DAYS = 14

/**
 * BCrypt cost factor.
 *
 * Each increment doubles the work. At 11 a hash takes roughly 100ms, which is
 * imperceptible on a login and brutal against a stolen hash dump — the
 * arithmetic turns billions of guesses per second into thousands. Higher would
 * be better still, but a serverless function billed by the millisecond has a
 * budget, and 11 is the point where the trade stops favouring more.
 */
const BCRYPT_ROUNDS = 11

const MAX_FAILED_LOGINS = 5
const LOCKOUT_MINUTES = 15

export interface SessionUser {
  readonly id: string
  readonly email: string
  readonly fullName: string
  readonly role: string
  readonly status: string
  readonly createdAt: Date
}

/* ------------------------------------------------------------------ */
/* Passwords                                                          */
/* ------------------------------------------------------------------ */

export const hashPassword = (plain: string) => bcrypt.hash(plain, BCRYPT_ROUNDS)

export const verifyPassword = (plain: string, hash: string) => bcrypt.compare(plain, hash)

/* ------------------------------------------------------------------ */
/* Tokens                                                             */
/* ------------------------------------------------------------------ */

/**
 * 32 bytes of cryptographic randomness, hex-encoded.
 *
 * crypto.getRandomValues, never Math.random: the latter is seeded predictably
 * and produces a sequence an attacker who has seen a few outputs can continue,
 * which for a session token means forging other people's sessions.
 */
function generateToken(): string {
  const bytes = new Uint8Array(32)
  crypto.getRandomValues(bytes)
  return Array.from(bytes)
    .map((byte) => byte.toString(16).padStart(2, '0'))
    .join('')
}

/**
 * SHA-256, deliberately not BCrypt.
 *
 * BCrypt is correct for passwords because it is slow, which defeats offline
 * brute force against low-entropy human-chosen secrets. A session token is 256
 * bits of randomness, so brute force is not on the table. More decisively,
 * BCrypt's per-token salt would make lookup impossible — finding a row requires
 * hashing the presented value deterministically.
 */
export async function hashToken(token: string): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(token))
  return Array.from(new Uint8Array(digest))
    .map((byte) => byte.toString(16).padStart(2, '0'))
    .join('')
}

/* ------------------------------------------------------------------ */
/* Session lifecycle                                                  */
/* ------------------------------------------------------------------ */

export async function createSession(userId: string, userAgent?: string | null): Promise<string> {
  const token = generateToken()
  const tokenHash = await hashToken(token)
  const expiresAt = new Date(Date.now() + SESSION_TTL_DAYS * 24 * 60 * 60 * 1000)

  await db.insert(sessions).values({
    userId,
    tokenHash,
    expiresAt,
    userAgent: userAgent?.slice(0, 300) ?? null,
  })

  const store = await cookies()
  store.set(SESSION_COOKIE, token, {
    httpOnly: true,
    // Off in development, where the dev server is plain HTTP and a Secure
    // cookie would simply never be stored — presenting as "login does nothing".
    secure: process.env.NODE_ENV === 'production',
    // Lax rather than Strict: Strict drops the cookie on a cross-site
    // navigation, so following an emailed link into the app lands you logged
    // out. Lax still blocks the cross-site POSTs that CSRF depends on.
    sameSite: 'lax',
    path: '/',
    maxAge: SESSION_TTL_DAYS * 24 * 60 * 60,
  })

  return token
}

/**
 * The signed-in user, or null.
 *
 * Reads the cookie, hashes it, and checks the row is neither expired nor
 * revoked. One query per call; Next dedupes within a render pass.
 */
export async function getSessionUser(): Promise<SessionUser | null> {
  const store = await cookies()
  const token = store.get(SESSION_COOKIE)?.value
  if (!token) return null

  const tokenHash = await hashToken(token)

  const rows = await db
    .select({
      id: users.id,
      email: users.email,
      fullName: users.fullName,
      role: users.role,
      status: users.status,
      createdAt: users.createdAt,
    })
    .from(sessions)
    .innerJoin(users, eq(sessions.userId, users.id))
    .where(
      and(
        eq(sessions.tokenHash, tokenHash),
        isNull(sessions.revokedAt),
        gt(sessions.expiresAt, new Date()),
      ),
    )
    .limit(1)

  const user = rows[0]
  if (!user) return null

  // A suspended account keeps its cookie but loses its session. Checking here
  // rather than only at login means a suspension takes effect immediately.
  if (user.status !== 'ACTIVE') return null

  return user
}

export async function destroySession(): Promise<void> {
  const store = await cookies()
  const token = store.get(SESSION_COOKIE)?.value

  if (token) {
    const tokenHash = await hashToken(token)
    await db
      .update(sessions)
      .set({ revokedAt: new Date() })
      .where(eq(sessions.tokenHash, tokenHash))
  }

  store.delete(SESSION_COOKIE)
}

export async function destroyAllSessions(userId: string): Promise<void> {
  await db
    .update(sessions)
    .set({ revokedAt: new Date() })
    .where(and(eq(sessions.userId, userId), isNull(sessions.revokedAt)))
}

/* ------------------------------------------------------------------ */
/* Login attempts                                                     */
/* ------------------------------------------------------------------ */

export function isLocked(user: Pick<User, 'lockedUntil'>): boolean {
  return user.lockedUntil !== null && user.lockedUntil > new Date()
}

/**
 * Records a failed sign-in and locks the account after too many.
 *
 * Rate limiting at the account level rather than the IP level, because the
 * attack this defends against — guessing one person's password — comes from
 * many IPs and targets one account.
 */
export async function recordFailedLogin(user: User): Promise<void> {
  const attempts = user.failedLoginAttempts + 1
  const lockedUntil =
    attempts >= MAX_FAILED_LOGINS
      ? new Date(Date.now() + LOCKOUT_MINUTES * 60 * 1000)
      : user.lockedUntil

  await db
    .update(users)
    .set({ failedLoginAttempts: attempts, lockedUntil, updatedAt: new Date() })
    .where(eq(users.id, user.id))
}

export async function recordSuccessfulLogin(userId: string): Promise<void> {
  await db
    .update(users)
    .set({
      failedLoginAttempts: 0,
      lockedUntil: null,
      lastLoginAt: new Date(),
      updatedAt: new Date(),
    })
    .where(eq(users.id, userId))
}

export async function findUserByEmail(email: string): Promise<User | undefined> {
  const rows = await db
    .select()
    .from(users)
    .where(sql`lower(${users.email}) = lower(${email})`)
    .limit(1)
  return rows[0]
}
