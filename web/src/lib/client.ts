'use client'

/**
 * The browser's side of the API.
 *
 * Same-origin throughout — the client and the API are the same Next.js
 * deployment — so there is no base URL to configure, no CORS preflight on any
 * request, and no environment variable that can be wrong in exactly one
 * environment. Authentication travels in an httpOnly cookie the browser
 * attaches automatically, which is why there is no token handling here at all.
 */

export interface ApiFailure {
  readonly code: string
  readonly message: string
  readonly details?: Array<{ field: string; message: string }>
}

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly details?: ApiFailure['details'],
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch(`/api${path}`, {
      ...init,
      headers:
        init?.body instanceof FormData
          ? init?.headers
          : { 'Content-Type': 'application/json', ...(init?.headers ?? {}) },
    })
  } catch {
    throw new ApiError(0, 'NETWORK', 'Could not reach the server. Check your connection.')
  }

  let payload: any = null
  try {
    payload = await response.json()
  } catch {
    // A non-JSON body from an API route means something upstream returned an
    // error page. Reporting the status is more useful than a parse error.
    if (!response.ok) {
      throw new ApiError(response.status, 'UNEXPECTED', `Request failed (${response.status}).`)
    }
  }

  if (!response.ok || payload?.success === false) {
    const error: ApiFailure = payload?.error ?? {
      code: 'UNEXPECTED',
      message: `Request failed (${response.status}).`,
    }
    // Per-field validation messages are far more useful than the envelope's
    // generic one, so they win when present.
    const message = error.details?.length
      ? error.details.map((detail) => detail.message).join(' ')
      : error.message
    throw new ApiError(response.status, error.code, message, error.details)
  }

  return payload?.data as T
}

export const api = {
  get: <T,>(path: string) => request<T>(path),
  post: <T,>(path: string, body?: unknown) =>
    request<T>(path, {
      method: 'POST',
      body: body instanceof FormData ? body : body ? JSON.stringify(body) : undefined,
    }),
  patch: <T,>(path: string, body?: unknown) =>
    request<T>(path, { method: 'PATCH', body: body ? JSON.stringify(body) : undefined }),
  delete: <T,>(path: string) => request<T>(path, { method: 'DELETE' }),
}

/** Turns any failure into a sentence a person can act on. */
export function errorMessage(error: unknown, fallback = 'Something went wrong. Please try again.') {
  if (error instanceof ApiError) return error.message
  if (error instanceof Error && error.message) return error.message
  return fallback
}
