import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { api, setUnauthorisedHandler, tokenStore, unwrap } from './api'

const AuthContext = createContext(null)

/**
 * Session state for the whole app.
 *
 * `loading` is the state that matters most. Without it, a page that redirects
 * unauthenticated visitors to /login will do so on every hard refresh, in the
 * moment before the stored token has been verified — the user is signed in and
 * gets bounced to the login screen anyway.
 */
export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [loading, setLoading] = useState(true)

  const signOutLocally = useCallback(() => {
    tokenStore.clear()
    setUser(null)
  }, [])

  useEffect(() => {
    setUnauthorisedHandler(signOutLocally)
  }, [signOutLocally])

  useEffect(() => {
    let cancelled = false

    async function restore() {
      if (!tokenStore.access && !tokenStore.refresh) {
        setLoading(false)
        return
      }
      try {
        const response = await api.get('/auth/me')
        if (!cancelled) setUser(unwrap(response))
      } catch {
        // The interceptor has already tried to refresh. Reaching here means the
        // session is genuinely over.
        if (!cancelled) signOutLocally()
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    restore()
    return () => {
      cancelled = true
    }
  }, [signOutLocally])

  const signIn = useCallback(async (email, password) => {
    const response = await api.post('/auth/login', { email, password })
    const tokens = unwrap(response)
    tokenStore.save(tokens.accessToken, tokens.refreshToken)
    setUser(tokens.user)
    return tokens.user
  }, [])

  const signOut = useCallback(async () => {
    const refreshToken = tokenStore.refresh
    try {
      if (refreshToken) await api.post('/auth/logout', { refreshToken })
    } catch {
      // A failed logout call must not trap someone in a session they have
      // asked to leave. The local tokens go regardless.
    } finally {
      signOutLocally()
    }
  }, [signOutLocally])

  const refreshUser = useCallback(async () => {
    const response = await api.get('/auth/me')
    const fresh = unwrap(response)
    setUser(fresh)
    return fresh
  }, [])

  const value = useMemo(
    () => ({
      user,
      loading,
      signIn,
      signOut,
      refreshUser,
      isAuthenticated: Boolean(user),
      isAdmin: Boolean(user?.roles?.includes('ROLE_ADMIN')),
    }),
    [user, loading, signIn, signOut, refreshUser],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used inside an AuthProvider')
  return context
}
