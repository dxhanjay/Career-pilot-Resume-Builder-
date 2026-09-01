import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import AuthShell from '../components/AuthShell'
import { Alert, Button, Loading } from '../components/ui'
import { api, errorMessage } from '../lib/api'

/**
 * Confirms an email address from the token in the link.
 *
 * The ref guard matters: React 18 StrictMode runs effects twice in development,
 * and this token is single-use. Without it the second call always fails and the
 * page shows an error for a verification that actually succeeded.
 */
export default function VerifyEmail() {
  const [params] = useSearchParams()
  const token = params.get('token')
  const [state, setState] = useState(token ? 'verifying' : 'missing')
  const [message, setMessage] = useState(null)
  const attempted = useRef(false)

  useEffect(() => {
    if (!token || attempted.current) return
    attempted.current = true

    api
      .post('/auth/verify-email', { token })
      .then(() => setState('done'))
      .catch((error) => {
        setMessage(errorMessage(error, 'That link is no longer valid.'))
        setState('failed')
      })
  }, [token])

  if (state === 'verifying') {
    return (
      <AuthShell title="Confirming your email" description="One moment.">
        <Loading label="Checking the link…" />
      </AuthShell>
    )
  }

  if (state === 'done') {
    return (
      <AuthShell title="Email confirmed" description="Your account is ready.">
        <Alert tone="emerald" title="All set">
          Your email address is confirmed. Sign in and upload your first resume.
        </Alert>
        <Button as={Link} to="/login" size="lg" className="mt-6 w-full">
          Sign in
        </Button>
      </AuthShell>
    )
  }

  return (
    <AuthShell
      title="That link did not work"
      description="Verification links expire, and each one can only be used once."
    >
      <Alert tone="red" title="Could not confirm your email">
        {message ?? 'No token was supplied. Open the link from the email directly.'}
      </Alert>
      <div className="mt-6 space-y-3">
        <Button as={Link} to="/login" size="lg" className="w-full">
          Go to sign in
        </Button>
        <p className="text-center text-sm text-ink-500">
          Signing in will offer to send a fresh verification email.
        </p>
      </div>
    </AuthShell>
  )
}
