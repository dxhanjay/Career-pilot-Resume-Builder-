import axios from 'axios'

/**
 * The HTTP layer.
 *
 * Relative by default. When the client and the API are served from one origin —
 * the single-image Docker build, and the Vite dev server, which proxies these
 * same paths — there is no base URL to configure and therefore none to get
 * wrong, and no CORS preflight on any request.
 *
 * `VITE_API_BASE_URL` exists for the split deployment, where the client is on a
 * static host (Vercel) and the API is elsewhere (Render). Set it to the API's
 * origin with no trailing slash, e.g. `https://careerpilot-api.onrender.com`.
 * The backend must then list the client's origin in APP_CORS_ALLOWED_ORIGINS.
 *
 * Read at build time, not runtime: Vite inlines it into the bundle, so changing
 * it requires a rebuild rather than a restart.
 */
const API_ORIGIN = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/+$/, '')

const ACCESS_TOKEN_KEY = 'cp.accessToken'
const REFRESH_TOKEN_KEY = 'cp.refreshToken'

export const tokenStore = {
  get access() {
    return safeRead(ACCESS_TOKEN_KEY)
  },
  get refresh() {
    return safeRead(REFRESH_TOKEN_KEY)
  },
  save(accessToken, refreshToken) {
    safeWrite(ACCESS_TOKEN_KEY, accessToken)
    if (refreshToken) safeWrite(REFRESH_TOKEN_KEY, refreshToken)
  },
  clear() {
    safeRemove(ACCESS_TOKEN_KEY)
    safeRemove(REFRESH_TOKEN_KEY)
  },
}

// Storage throws in a private window with site data blocked, and a thrown
// exception here would take down the whole app on first paint.
function safeRead(key) {
  try {
    return window.localStorage.getItem(key)
  } catch {
    return null
  }
}

function safeWrite(key, value) {
  try {
    window.localStorage.setItem(key, value)
  } catch {
    /* ignore — the session simply will not survive a reload */
  }
}

function safeRemove(key) {
  try {
    window.localStorage.removeItem(key)
  } catch {
    /* ignore */
  }
}

export const api = axios.create({
  baseURL: `${API_ORIGIN}/api/v1`,
  timeout: 45000,
  headers: { 'Content-Type': 'application/json' },
})

api.interceptors.request.use((config) => {
  const token = tokenStore.access
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

/*
 * Refresh-on-401, with a single in-flight refresh.
 *
 * Without the shared promise, a dashboard that fires five requests at once on a
 * newly expired token sends five refreshes. The backend rotates refresh tokens
 * and treats reuse as a breach, so four of those would invalidate the whole
 * token family and sign the user out — a bug that only appears on pages that
 * happen to load several things at once.
 */
let refreshInFlight = null
let onUnauthorised = () => {}

export function setUnauthorisedHandler(handler) {
  onUnauthorised = handler
}

/** Endpoints where a 401 is the answer, not a signal to refresh. */
function isAuthEndpoint(url = '') {
  return (
    url.includes('/auth/login') ||
    url.includes('/auth/refresh') ||
    url.includes('/auth/register')
  )
}

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const original = error.config
    const status = error.response?.status

    if (status !== 401 || !original || original._retried || isAuthEndpoint(original.url)) {
      return Promise.reject(error)
    }

    const refreshToken = tokenStore.refresh
    if (!refreshToken) {
      onUnauthorised()
      return Promise.reject(error)
    }

    original._retried = true

    try {
      if (!refreshInFlight) {
        // A bare axios instance, not `api`: routing the refresh through the
        // same instance would re-enter this interceptor on failure and loop.
        refreshInFlight = axios
          .post(`${API_ORIGIN}/api/v1/auth/refresh`, { refreshToken })
          .then((response) => response.data?.data)
          .finally(() => {
            refreshInFlight = null
          })
      }
      const tokens = await refreshInFlight
      if (!tokens?.accessToken) throw new Error('Refresh returned no token')
      tokenStore.save(tokens.accessToken, tokens.refreshToken)
      original.headers.Authorization = `Bearer ${tokens.accessToken}`
      return api(original)
    } catch (refreshError) {
      tokenStore.clear()
      onUnauthorised()
      return Promise.reject(refreshError)
    }
  },
)

/**
 * Turns any failure into a sentence a person can act on.
 *
 * The backend returns { success, data: { code, message, details } }. Validation
 * failures carry per-field messages, and those are far more useful than the
 * generic envelope message, so they win.
 */
export function errorMessage(error, fallback = 'Something went wrong. Please try again.') {
  if (!error) return fallback

  if (error.code === 'ECONNABORTED') {
    return 'That took too long. Check your connection and try again.'
  }
  if (!error.response) {
    return 'Could not reach the server. Check your connection and try again.'
  }

  const body = error.response.data?.data
  const details = body?.details
  if (Array.isArray(details) && details.length > 0) {
    return details.map((detail) => detail.message).join(' ')
  }
  return body?.message || error.response.data?.message || fallback
}

/** The stable error code, for callers that need to branch rather than display. */
export function errorCode(error) {
  return error?.response?.data?.data?.code || null
}

/** Unwraps the ApiResponse envelope. */
export function unwrap(response) {
  return response?.data?.data
}
