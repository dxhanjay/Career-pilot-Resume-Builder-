import { NextResponse } from 'next/server'
import { ZodError } from 'zod'
import { getSessionUser, type SessionUser } from './auth'

/**
 * The shape of every API response, and one place that turns a thrown error into
 * one.
 *
 * A route handler that has to remember to catch, map, and shape its own errors
 * is a route handler that eventually forgets, and the symptom is a stack trace
 * in a browser.
 */

export interface ApiError {
  readonly code: string
  readonly message: string
  readonly details?: Array<{ field: string; message: string }>
}

export const ok = <T>(data: T, status = 200) =>
  NextResponse.json({ success: true, data }, { status })

export const fail = (code: string, message: string, status: number, details?: ApiError['details']) =>
  NextResponse.json({ success: false, error: { code, message, details } }, { status })

/**
 * An error a route deliberately raises, with the status it should produce.
 *
 * Distinguishes "the user asked for something impossible" from "we have a bug".
 * The first is reported verbatim; the second never is.
 */
export class HttpError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
  ) {
    super(message)
    this.name = 'HttpError'
  }
}

export const badRequest = (message: string) => new HttpError(400, 'BAD_REQUEST', message)
export const unauthorized = (message = 'Sign in to continue') =>
  new HttpError(401, 'UNAUTHORIZED', message)
export const forbidden = (message = 'You do not have permission to do that') =>
  new HttpError(403, 'FORBIDDEN', message)
export const notFound = (what = 'Resource') => new HttpError(404, 'NOT_FOUND', `${what} not found`)
export const conflict = (message: string) => new HttpError(409, 'CONFLICT', message)
export const tooLarge = (message: string) => new HttpError(413, 'PAYLOAD_TOO_LARGE', message)
export const unsupportedType = (message: string) =>
  new HttpError(415, 'UNSUPPORTED_MEDIA_TYPE', message)

/**
 * Wraps a handler so every failure becomes a shaped response.
 *
 * An unrecognised error is logged in full and reported as a bare 500. The
 * message of an unexpected exception routinely contains a connection string, a
 * query, or a file path, and none of that belongs in a browser.
 */
export function handler<T extends unknown[]>(
  fn: (...args: T) => Promise<NextResponse>,
): (...args: T) => Promise<NextResponse> {
  return async (...args: T) => {
    try {
      return await fn(...args)
    } catch (error) {
      if (error instanceof HttpError) {
        return fail(error.code, error.message, error.status)
      }

      if (error instanceof ZodError) {
        return fail(
          'VALIDATION_ERROR',
          'Some of what you entered is not valid.',
          422,
          error.errors.map((issue) => ({
            field: issue.path.join('.') || 'body',
            message: issue.message,
          })),
        )
      }

      // A deployment with no database is a configuration state, not a secret,
      // and it is the single most likely reason a fresh deploy fails. Saying so
      // turns "something went wrong" into an instruction; anything else here
      // stays generic, because the message of an unexpected exception routinely
      // contains a connection string, a query, or a file path.
      if (error instanceof Error && error.message.includes('DATABASE_URL')) {
        console.error('Database is not configured', error)
        return fail('DATABASE_NOT_CONFIGURED', error.message, 503)
      }

      console.error('Unhandled API error', error)
      return fail('INTERNAL_ERROR', 'Something went wrong on our side.', 500)
    }
  }
}

/**
 * The signed-in user, or a 401.
 *
 * Every protected route starts with this. Returning the user rather than a
 * boolean means a route cannot check authentication and then forget to scope its
 * query to the user it just checked.
 */
export async function requireUser(): Promise<SessionUser> {
  const user = await getSessionUser()
  if (!user) throw unauthorized()
  return user
}

export async function requireAdmin(): Promise<SessionUser> {
  const user = await requireUser()
  if (user.role !== 'ADMIN') throw forbidden('This area is for administrators.')
  return user
}

/** Reads and parses a JSON body, refusing anything malformed with a 400. */
export async function readJson<T>(
  request: Request,
  schema: { parse: (value: unknown) => T },
): Promise<T> {
  let body: unknown
  try {
    body = await request.json()
  } catch {
    throw badRequest('The request body was not valid JSON.')
  }
  return schema.parse(body)
}
