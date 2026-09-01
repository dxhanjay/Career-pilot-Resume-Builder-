'use client'

import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useState } from 'react'
import AuthShell from '@/components/AuthShell'
import { Alert, Button, Field, TextInput } from '@/components/ui'
import { api, errorMessage } from '@/lib/client'

export default function LoginPage() {
  const router = useRouter()
  const [form, setForm] = useState({ email: '', password: '' })
  const [errors, setErrors] = useState<Record<string, string | undefined>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  function update(field: 'email' | 'password', value: string) {
    setForm((current) => ({ ...current, [field]: value }))
    setErrors((current) => ({ ...current, [field]: undefined }))
  }

  function validate() {
    const next: Record<string, string> = {}
    if (!form.email.trim()) next.email = 'Enter your email address'
    else if (!/^\S+@\S+\.\S+$/.test(form.email.trim()))
      next.email = 'That does not look like an email address'
    if (!form.password) next.password = 'Enter your password'
    setErrors(next)
    return Object.keys(next).length === 0
  }

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    setFormError(null)
    if (!validate()) return

    setSubmitting(true)
    try {
      await api.post('/auth/login', { email: form.email.trim(), password: form.password })
      router.push('/dashboard')
      // The layout reads the session on the server, so the cached tree has to
      // be invalidated or the shell renders as signed-out.
      router.refresh()
    } catch (error) {
      setFormError(errorMessage(error, 'Could not sign you in. Check your details and try again.'))
      setSubmitting(false)
    }
  }

  return (
    <AuthShell
      title="Sign in"
      description="Pick up where you left off."
      footer={
        <>
          New here?{' '}
          <Link href="/register" className="font-medium text-brand-700 hover:text-brand-800">
            Create an account
          </Link>
        </>
      }
    >
      <form onSubmit={handleSubmit} noValidate className="space-y-4">
        {formError && (
          <Alert tone="red" title="Sign in failed">
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
            onChange={(event: React.ChangeEvent<HTMLInputElement>) =>
              update('email', event.target.value)
            }
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
            onChange={(event: React.ChangeEvent<HTMLInputElement>) =>
              update('password', event.target.value)
            }
          />
        </Field>

        <Button type="submit" size="lg" className="w-full" loading={submitting}>
          {submitting ? 'Signing in…' : 'Sign in'}
        </Button>
      </form>
    </AuthShell>
  )
}
