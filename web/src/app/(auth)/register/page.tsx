'use client'

import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useState } from 'react'
import AuthShell from '@/components/AuthShell'
import { Alert, Button, Field, TextInput } from '@/components/ui'
import { api, errorMessage } from '@/lib/client'

/** Mirrors the server's minimum. Anything stricter here is a lie about the API. */
const MIN_PASSWORD = 8

export default function RegisterPage() {
  const router = useRouter()
  const [form, setForm] = useState({ fullName: '', email: '', password: '', confirm: '' })
  const [errors, setErrors] = useState<Record<string, string | undefined>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  function update(field: keyof typeof form, value: string) {
    setForm((current) => ({ ...current, [field]: value }))
    setErrors((current) => ({ ...current, [field]: undefined }))
  }

  function validate() {
    const next: Record<string, string> = {}
    if (!form.fullName.trim()) next.fullName = 'Enter your name'
    else if (form.fullName.trim().length < 2) next.fullName = 'That looks too short'

    if (!form.email.trim()) next.email = 'Enter your email address'
    else if (!/^\S+@\S+\.\S+$/.test(form.email.trim()))
      next.email = 'That does not look like an email address'

    if (!form.password) next.password = 'Choose a password'
    else if (form.password.length < MIN_PASSWORD)
      next.password = `At least ${MIN_PASSWORD} characters`

    if (form.confirm !== form.password) next.confirm = 'The two passwords do not match'

    setErrors(next)
    return Object.keys(next).length === 0
  }

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    setFormError(null)
    if (!validate()) return

    setSubmitting(true)
    try {
      await api.post('/auth/register', {
        fullName: form.fullName.trim(),
        email: form.email.trim(),
        password: form.password,
      })
      // Registration signs you in, so there is no second step to ask for.
      router.push('/dashboard')
      router.refresh()
    } catch (error) {
      setFormError(errorMessage(error, 'Could not create your account.'))
      setSubmitting(false)
    }
  }

  return (
    <AuthShell
      title="Create your account"
      description="Free. Upload one resume and see what a screener sees."
      footer={
        <>
          Already have an account?{' '}
          <Link href="/login" className="font-medium text-brand-700 hover:text-brand-800">
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
            autoComplete="name"
            autoFocus
            placeholder="Aditi Sharma"
            value={form.fullName}
            error={errors.fullName}
            onChange={(event: React.ChangeEvent<HTMLInputElement>) =>
              update('fullName', event.target.value)
            }
          />
        </Field>

        <Field label="Email" htmlFor="email" error={errors.email}>
          <TextInput
            id="email"
            type="email"
            autoComplete="email"
            placeholder="you@university.edu"
            value={form.email}
            error={errors.email}
            onChange={(event: React.ChangeEvent<HTMLInputElement>) =>
              update('email', event.target.value)
            }
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
            type="password"
            autoComplete="new-password"
            value={form.password}
            error={errors.password}
            onChange={(event: React.ChangeEvent<HTMLInputElement>) =>
              update('password', event.target.value)
            }
          />
        </Field>

        <Field label="Confirm password" htmlFor="confirm" error={errors.confirm}>
          <TextInput
            id="confirm"
            type="password"
            autoComplete="new-password"
            value={form.confirm}
            error={errors.confirm}
            onChange={(event: React.ChangeEvent<HTMLInputElement>) =>
              update('confirm', event.target.value)
            }
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
