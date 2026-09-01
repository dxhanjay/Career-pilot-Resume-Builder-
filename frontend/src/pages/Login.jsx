import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import AuthShell from '../components/AuthShell'
import { Alert, Button, Field, TextInput } from '../components/ui'
import { api, errorCode, errorMessage } from '../lib/api'
import { useAuth } from '../lib/auth'

export default function Login() {
  const { signIn } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  const [form, setForm] = useState({ email: '', password: '' })
  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState(null)
  const [unverified, setUnverified] = useState(false)
  const [resent, setResent] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  function update(field, value) {
    setForm((current) => ({ ...current, [field]: value }))
    setErrors((current) => ({ ...current, [field]: undefined }))
  }

  function validate() {
    const next = {}
    if (!form.email.trim()) next.email = 'Enter your email address'
    else if (!/^\S+@\S+\.\S+$/.test(form.email.trim())) next.email = 'That does not look like an email address'
    if (!form.password) next.password = 'Enter your password'
    setErrors(next)
    return Object.keys(next).length === 0
  }

  async function handleSubmit(event) {
    event.preventDefault()
    setFormError(null)
    setUnverified(false)
    if (!validate()) return

    setSubmitting(true)
    try {
      await signIn(form.email.trim(), form.password)
      // Return them to whatever they were trying to reach before being asked
      // to sign in, rather than always dumping them on the dashboard.
      const destination = location.state?.from?.pathname ?? '/dashboard'
      navigate(destination, { replace: true })
    } catch (error) {
      if (errorCode(error) === 'EMAIL_NOT_VERIFIED') setUnverified(true)
      setFormError(errorMessage(error, 'Could not sign you in. Check your details and try again.'))
    } finally {
      setSubmitting(false)
    }
  }

  async function resendVerification() {
    try {
      await api.post('/auth/resend-verification', { email: form.email.trim() })
      setResent(true)
    } catch {
      setResent(true) // The endpoint is deliberately silent about unknown addresses.
    }
  }

  return (
    <AuthShell
      title="Sign in"
      description="Pick up where you left off."
      footer={
        <>
          New here?{' '}
          <Link to="/register" className="font-medium text-brand-700 hover:text-brand-800">
            Create an account
          </Link>
        </>
      }
    >
      <form onSubmit={handleSubmit} noValidate className="space-y-4">
        {formError && (
          <Alert
            tone="red"
            title="Sign in failed"
            action={
              unverified &&
              (resent ? (
                <p className="text-sm text-ink-600">
                  Sent. Check your inbox, and your spam folder.
                </p>
              ) : (
                <Button type="button" size="sm" variant="secondary" onClick={resendVerification}>
                  Resend the verification email
                </Button>
              ))
            }
          >
            {formError}
          </Alert>
        )}

        <Field label="Email" htmlFor="email" error={errors.email}>
          <TextInput
            id="email"
            name="email"
            type="email"
            autoComplete="email"
            autoFocus
            placeholder="you@university.edu"
            value={form.email}
            error={errors.email}
            onChange={(event) => update('email', event.target.value)}
          />
        </Field>

        <Field label="Password" htmlFor="password" error={errors.password}>
          <TextInput
            id="password"
            name="password"
            type="password"
            autoComplete="current-password"
            placeholder="••••••••"
            value={form.password}
            error={errors.password}
            onChange={(event) => update('password', event.target.value)}
          />
        </Field>

        <div className="flex justify-end">
          <Link to="/forgot-password" className="text-sm font-medium text-brand-700 hover:text-brand-800">
            Forgot your password?
          </Link>
        </div>

        <Button type="submit" size="lg" className="w-full" loading={submitting}>
          {submitting ? 'Signing in…' : 'Sign in'}
        </Button>
      </form>
    </AuthShell>
  )
}
