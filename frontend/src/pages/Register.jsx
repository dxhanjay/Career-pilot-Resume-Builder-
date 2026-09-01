import { useState } from 'react'
import { Link } from 'react-router-dom'
import AuthShell from '../components/AuthShell'
import { Alert, Button, Field, TextInput } from '../components/ui'
import { api, errorMessage } from '../lib/api'

/** Mirrors the backend's minimum. Anything stricter here is a lie about the API. */
const MIN_PASSWORD = 8

export default function Register() {
  const [form, setForm] = useState({ fullName: '', email: '', password: '', confirm: '' })
  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState(null)
  const [submitting, setSubmitting] = useState(false)
  const [registered, setRegistered] = useState(false)

  function update(field, value) {
    setForm((current) => ({ ...current, [field]: value }))
    setErrors((current) => ({ ...current, [field]: undefined }))
  }

  function validate() {
    const next = {}
    if (!form.fullName.trim()) next.fullName = 'Enter your name'
    else if (form.fullName.trim().length < 2) next.fullName = 'That looks too short'

    if (!form.email.trim()) next.email = 'Enter your email address'
    else if (!/^\S+@\S+\.\S+$/.test(form.email.trim())) next.email = 'That does not look like an email address'

    if (!form.password) next.password = 'Choose a password'
    else if (form.password.length < MIN_PASSWORD) next.password = `At least ${MIN_PASSWORD} characters`

    if (form.confirm !== form.password) next.confirm = 'The two passwords do not match'

    setErrors(next)
    return Object.keys(next).length === 0
  }

  async function handleSubmit(event) {
    event.preventDefault()
    setFormError(null)
    if (!validate()) return

    setSubmitting(true)
    try {
      await api.post('/auth/register', {
        email: form.email.trim(),
        password: form.password,
        fullName: form.fullName.trim(),
      })
      setRegistered(true)
    } catch (error) {
      setFormError(errorMessage(error, 'Could not create your account.'))
    } finally {
      setSubmitting(false)
    }
  }

  if (registered) {
    return (
      <AuthShell
        title="Check your email"
        description={`We sent a confirmation link to ${form.email.trim()}.`}
      >
        <Alert tone="emerald" title="Account created">
          Open the link in that email to confirm your address, then sign in. If nothing arrives
          within a few minutes, check your spam folder.
        </Alert>
        <Button as={Link} to="/login" size="lg" className="mt-6 w-full">
          Go to sign in
        </Button>
      </AuthShell>
    )
  }

  return (
    <AuthShell
      title="Create your account"
      description="Free. Upload one resume and see what a screener sees."
      footer={
        <>
          Already have an account?{' '}
          <Link to="/login" className="font-medium text-brand-700 hover:text-brand-800">
            Sign in
          </Link>
        </>
      }
    >
      <form onSubmit={handleSubmit} noValidate className="space-y-4">
        {formError && (
          <Alert tone="red" title="Registration failed">
            {formError}
          </Alert>
        )}

        <Field label="Full name" htmlFor="fullName" error={errors.fullName}>
          <TextInput
            id="fullName"
            name="fullName"
            autoComplete="name"
            autoFocus
            placeholder="Aditi Sharma"
            value={form.fullName}
            error={errors.fullName}
            onChange={(event) => update('fullName', event.target.value)}
          />
        </Field>

        <Field label="Email" htmlFor="email" error={errors.email}>
          <TextInput
            id="email"
            name="email"
            type="email"
            autoComplete="email"
            placeholder="you@university.edu"
            value={form.email}
            error={errors.email}
            onChange={(event) => update('email', event.target.value)}
          />
        </Field>

        <Field
          label="Password"
          htmlFor="password"
          error={errors.password}
          hint={`At least ${MIN_PASSWORD} characters.`}
        >
          <TextInput
            id="password"
            name="password"
            type="password"
            autoComplete="new-password"
            value={form.password}
            error={errors.password}
            onChange={(event) => update('password', event.target.value)}
          />
        </Field>

        <Field label="Confirm password" htmlFor="confirm" error={errors.confirm}>
          <TextInput
            id="confirm"
            name="confirm"
            type="password"
            autoComplete="new-password"
            value={form.confirm}
            error={errors.confirm}
            onChange={(event) => update('confirm', event.target.value)}
          />
        </Field>

        <Button type="submit" size="lg" className="w-full" loading={submitting}>
          {submitting ? 'Creating your account…' : 'Create account'}
        </Button>

        <p className="text-center text-xs leading-5 text-ink-400">
          Your resume is analysed for you and never shown to an employer on your behalf.
        </p>
      </form>
    </AuthShell>
  )
}
