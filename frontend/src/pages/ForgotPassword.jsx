import { useState } from 'react'
import { Link } from 'react-router-dom'
import AuthShell from '../components/AuthShell'
import { Alert, Button, Field, TextInput } from '../components/ui'
import { api } from '../lib/api'

export default function ForgotPassword() {
  const [email, setEmail] = useState('')
  const [error, setError] = useState(null)
  const [submitting, setSubmitting] = useState(false)
  const [sent, setSent] = useState(false)

  async function handleSubmit(event) {
    event.preventDefault()
    if (!/^\S+@\S+\.\S+$/.test(email.trim())) {
      setError('Enter a valid email address')
      return
    }
    setError(null)
    setSubmitting(true)
    try {
      await api.post('/auth/forgot-password', { email: email.trim() })
    } catch {
      // Deliberately ignored. The endpoint answers identically for a known and
      // an unknown address, and so does this screen — anything else turns the
      // form into a way to test whether somebody has an account here.
    } finally {
      setSubmitting(false)
      setSent(true)
    }
  }

  if (sent) {
    return (
      <AuthShell title="Check your email" description="If that address has an account, a reset link is on its way.">
        <Alert tone="emerald" title="Link sent">
          The link expires in an hour. If nothing arrives, check your spam folder, or try again with
          a different address.
        </Alert>
        <Button as={Link} to="/login" size="lg" className="mt-6 w-full">
          Back to sign in
        </Button>
      </AuthShell>
    )
  }

  return (
    <AuthShell
      title="Reset your password"
      description="Enter the address you signed up with and we will send you a link."
      footer={
        <Link to="/login" className="font-medium text-brand-700 hover:text-brand-800">
          Back to sign in
        </Link>
      }
    >
      <form onSubmit={handleSubmit} noValidate className="space-y-4">
        <Field label="Email" htmlFor="email" error={error}>
          <TextInput
            id="email"
            type="email"
            autoComplete="email"
            autoFocus
            placeholder="you@university.edu"
            value={email}
            error={error}
            onChange={(event) => {
              setEmail(event.target.value)
              setError(null)
            }}
          />
        </Field>
        <Button type="submit" size="lg" className="w-full" loading={submitting}>
          {submitting ? 'Sending…' : 'Send reset link'}
        </Button>
      </form>
    </AuthShell>
  )
}
