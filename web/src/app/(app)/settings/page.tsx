import { redirect } from 'next/navigation'
import { Badge, Card, CardHeader, PageHeader } from '@/components/ui'
import { getSessionUser } from '@/lib/auth'
import { formatDate } from '@/lib/format'

export const metadata = { title: 'Settings' }

/**
 * A server component: the account details all come from the session, so there is
 * nothing to fetch on the client and no loading state to design.
 */
export default async function SettingsPage() {
  const user = await getSessionUser()
  if (!user) redirect('/login')

  return (
    <>
      <PageHeader title="Settings" description="Your account." />

      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader title="Account" />
          <dl className="divide-y divide-ink-100 text-sm">
            <Row label="Name" value={user.fullName} />
            <Row label="Email" value={user.email} />
            <Row label="Status" value={<Badge tone="emerald">{user.status}</Badge>} />
            <Row label="Role" value={user.role === 'ADMIN' ? 'Administrator' : 'User'} />
            <Row label="Member since" value={formatDate(user.createdAt)} />
          </dl>
        </Card>

        <Card>
          <CardHeader
            title="Your data"
            description="What this product will and will not do with it."
          />
          <div className="space-y-3 p-5 text-sm leading-6 text-ink-600">
            <p>
              <strong className="font-medium text-ink-900">Candidate-side only.</strong> Your resume
              is analysed for you. It is never shown to an employer, ranked against another
              candidate, or used to screen anybody.
            </p>
            <p>
              <strong className="font-medium text-ink-900">Nothing is invented.</strong> Every
              rewrite suggestion quotes something you already wrote. Where a number is needed, you
              are asked for it rather than given one.
            </p>
            <p>
              <strong className="font-medium text-ink-900">No face or voice analysis.</strong>{' '}
              Interview feedback comes from the content of your answers. There is no camera and no
              microphone anywhere in this product.
            </p>
            <p>
              Deleting a resume deletes its parses, scores, and matches with it.
            </p>
          </div>
        </Card>
      </div>
    </>
  )
}

function Row({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-4 px-5 py-3">
      <dt className="text-ink-500">{label}</dt>
      <dd className="min-w-0 break-words text-right font-medium text-ink-900">{value ?? '—'}</dd>
    </div>
  )
}
