import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import AuthShell from '../components/AuthShell'
import { Alert, Button, Field, TextInput } from '../components/ui'
import { api, errorMessage } from '../lib/api'

const MIN_PASSWORD = 8

export default function ResetPassword() {
  const [params] = useSearchParams()
  const token = params.get('token')

  const [form, setForm] = useState({ password: '', confirm: '' })
  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState(null)
  const [submitting, setSubmitting] = useState(false)
  const [done, setDone] = useState(false)

  function validate() {
    const next = {}
    if (!form.password) next.password = 'Choose a new password'
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
      await api.post('/auth/reset-password', { token, newPassword: form.password })
      setDone(true)
    } catch (error) {
      setFormError(errorMessage(error, 'That reset link is no longer valid.'))
    } finally {
      setSubmitting(false)
    }
  }

  if (!token) {
    return (
      <AuthShell title="Missing reset link" description="This page needs the token from your email.">
        <Alert tone="red" title="No token supplied">
          Open the link from the reset email directly rather than typing this address.
        </Alert>
        <Button as={Link} to="/forgot-password" size="lg" className="mt-6 w-full">
          Request a new link
        </Button>
      </AuthShell>
    )
  }

  if (done) {
    return (
      <AuthShell title="Password updated" description="Every other session has been signed out.">
        <Alert tone="emerald" title="Done">
          Your password has been changed. Signing in again is the last step.
        </Alert>
        <Button as={Link} to="/login" size="lg" className="mt-6 w-full">
          Sign in
        </Button>
      </AuthShell>
    )
  }

  return (
    <AuthShell title="Choose a new password" description="Make it one you have not used elsewhere.">
      <form onSubmit={handleSubmit} noValidate className="space-y-4">
        {formError && (
          <Alert
            tone="red"
            title="Could not reset your password"
            action={
              <Button as={Link} to="/forgot-password" size="sm" variant="secondary">
                Request a new link
              </Button>
            }
          >
            {formError}
          </Alert>
        )}

        <Field
          label="New password"
          htmlFor="password"
          error={errors.password}
          hint={`At least ${MIN_PASSWORD} characters.`}
        >
          <TextInput
            id="password"
            type="password"
            autoComplete="new-password"
            autoFocus
            value={form.password}
            error={errors.password}
            onChange={(event) => {
              setForm((current) => ({ ...current, password: event.target.value }))
              setErrors((current) => ({ ...current, password: undefined }))
            }}
          />
        </Field>

        <Field label="Confirm new password" htmlFor="confirm" error={errors.confirm}>
          <TextInput
            id="confirm"
            type="password"
            autoComplete="new-password"
            value={form.confirm}
            error={errors.confirm}
            onChange={(event) => {
              setForm((current) => ({ ...current, confirm: event.target.value }))
              setErrors((current) => ({ ...current, confirm: undefined }))
            }}
          />
        </Field>

        <Button type="submit" size="lg" className="w-full" loading={submitting}>
          {submitting ? 'Updating…' : 'Update password'}
        </Button>
      </form>
    </AuthShell>
  )
}
