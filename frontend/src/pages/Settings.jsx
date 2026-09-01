import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Alert,
  Badge,
  Button,
  Card,
  CardHeader,
  Field,
  PageHeader,
  TextInput,
} from '../components/ui'
import { api, errorMessage } from '../lib/api'
import { useAuth } from '../lib/auth'
import { formatDate } from '../lib/format'

const MIN_PASSWORD = 8

export default function Settings() {
  const { user, signOut } = useAuth()
  const navigate = useNavigate()

  const [form, setForm] = useState({ currentPassword: '', newPassword: '', confirm: '' })
  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState(null)
  const [submitting, setSubmitting] = useState(false)
  const [changed, setChanged] = useState(false)
  const [signingOutAll, setSigningOutAll] = useState(false)

  function update(field, value) {
    setForm((current) => ({ ...current, [field]: value }))
    setErrors((current) => ({ ...current, [field]: undefined }))
  }

  function validate() {
    const next = {}
    if (!form.currentPassword) next.currentPassword = 'Enter your current password'
    if (!form.newPassword) next.newPassword = 'Choose a new password'
    else if (form.newPassword.length < MIN_PASSWORD) next.newPassword = `At least ${MIN_PASSWORD} characters`
    else if (form.newPassword === form.currentPassword) next.newPassword = 'That is your current password'
    if (form.confirm !== form.newPassword) next.confirm = 'The two passwords do not match'
    setErrors(next)
    return Object.keys(next).length === 0
  }

  async function changePassword(event) {
    event.preventDefault()
    setFormError(null)
    if (!validate()) return

    setSubmitting(true)
    try {
      await api.post('/auth/change-password', {
        currentPassword: form.currentPassword,
        newPassword: form.newPassword,
      })
      setChanged(true)
      // The backend revokes every session on a password change, so staying on
      // this page would leave the app holding tokens the server has discarded.
      window.setTimeout(async () => {
        await signOut()
        navigate('/login', { replace: true })
      }, 2200)
    } catch (err) {
      setFormError(errorMessage(err, 'Could not change your password.'))
    } finally {
      setSubmitting(false)
    }
  }

  async function signOutEverywhere() {
    setSigningOutAll(true)
    try {
      await api.post('/auth/logout-all')
      await signOut()
      navigate('/login', { replace: true })
    } catch (err) {
      setFormError(errorMessage(err, 'Could not sign out your other sessions.'))
      setSigningOutAll(false)
    }
  }

  return (
    <>
      <PageHeader title="Settings" description="Your account and security." />

      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader title="Account" />
          <dl className="divide-y divide-ink-100 text-sm">
            <Row label="Name" value={user?.fullName} />
            <Row label="Email" value={user?.email} />
            <Row
              label="Email verified"
              value={
                user?.emailVerified ? (
                  <Badge tone="emerald">Verified</Badge>
                ) : (
                  <Badge tone="amber">Not verified</Badge>
                )
              }
            />
            <Row label="Status" value={user?.status} />
            <Row
              label="Roles"
              value={(user?.roles ?? []).map((role) => role.replace('ROLE_', '')).join(', ') || '—'}
            />
            <Row label="Member since" value={formatDate(user?.createdAt)} />
          </dl>
        </Card>

        <Card>
          <CardHeader
            title="Change password"
            description="Changing it signs out every device, including this one."
          />
          <form onSubmit={changePassword} noValidate className="space-y-4 p-5">
            {changed && (
              <Alert tone="emerald" title="Password updated">
                Signing you out now. Sign in again with your new password.
              </Alert>
            )}
            {formError && (
              <Alert tone="red" title="Could not change your password">
                {formError}
              </Alert>
            )}

            <Field label="Current password" htmlFor="currentPassword" error={errors.currentPassword}>
              <TextInput
                id="currentPassword"
                type="password"
                autoComplete="current-password"
                value={form.currentPassword}
                error={errors.currentPassword}
                onChange={(event) => update('currentPassword', event.target.value)}
              />
            </Field>

            <Field
              label="New password"
              htmlFor="newPassword"
              error={errors.newPassword}
              hint={`At least ${MIN_PASSWORD} characters.`}
            >
              <TextInput
                id="newPassword"
                type="password"
                autoComplete="new-password"
                value={form.newPassword}
                error={errors.newPassword}
                onChange={(event) => update('newPassword', event.target.value)}
              />
            </Field>

            <Field label="Confirm new password" htmlFor="confirm" error={errors.confirm}>
              <TextInput
                id="confirm"
                type="password"
                autoComplete="new-password"
                value={form.confirm}
                error={errors.confirm}
                onChange={(event) => update('confirm', event.target.value)}
              />
            </Field>

            <Button type="submit" loading={submitting} disabled={changed}>
              Change password
            </Button>
          </form>
        </Card>

        <Card className="lg:col-span-2">
          <CardHeader
            title="Sessions"
            description="If you signed in on a shared or lost device, end every session."
          />
          <div className="p-5">
            <Button variant="secondary" loading={signingOutAll} onClick={signOutEverywhere}>
              Sign out everywhere
            </Button>
          </div>
        </Card>
      </div>
    </>
  )
}

function Row({ label, value }) {
  return (
    <div className="flex items-center justify-between gap-4 px-5 py-3">
      <dt className="text-ink-500">{label}</dt>
      <dd className="min-w-0 break-words text-right font-medium text-ink-900">{value ?? '—'}</dd>
    </div>
  )
}
