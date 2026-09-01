import { useCallback, useEffect, useState } from 'react'
import {
  Alert,
  Badge,
  Button,
  Card,
  CardHeader,
  EmptyState,
  Loading,
  PageHeader,
  Stat,
  TextInput,
} from '../components/ui'
import { api, errorMessage, unwrap } from '../lib/api'
import { formatDate, relativeTime } from '../lib/format'

const STATUS_TONE = {
  ACTIVE: 'emerald',
  PENDING: 'amber',
  SUSPENDED: 'red',
  DELETED: 'slate',
}

export default function Admin() {
  const [stats, setStats] = useState(null)
  const [users, setUsers] = useState(null)
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [actionError, setActionError] = useState(null)
  const [busyId, setBusyId] = useState(null)

  const load = useCallback(async () => {
    setError(null)
    try {
      const [statsResult, usersResult] = await Promise.all([
        api.get('/admin/stats'),
        api.get('/admin/users', { params: { query: query || undefined, page, size: 20 } }),
      ])
      setStats(unwrap(statsResult))
      setUsers(unwrap(usersResult))
    } catch (err) {
      setError(errorMessage(err, 'Could not load the admin console.'))
    } finally {
      setLoading(false)
    }
  }, [query, page])

  useEffect(() => {
    load()
  }, [load])

  async function setStatus(userId, action) {
    setBusyId(userId)
    setActionError(null)
    try {
      await api.patch(`/admin/users/${userId}/status`, { action })
      await load()
    } catch (err) {
      setActionError(errorMessage(err, 'Could not change that account.'))
    } finally {
      setBusyId(null)
    }
  }

  if (loading) return <Loading label="Loading the admin console…" />

  if (error) {
    return (
      <Alert tone="red" title="Could not load" action={<Button onClick={load}>Try again</Button>}>
        {error}
      </Alert>
    )
  }

  return (
    <>
      <PageHeader
        title="Admin"
        description="Platform totals and account management. Counts only — nothing here quotes a user's documents."
      />

      {actionError && (
        <Alert tone="red" title="That did not work" className="mb-6">
          {actionError}
        </Alert>
      )}

      <div className="mb-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Stat label="Users" value={stats.totalUsers} hint={`${stats.newUsersLast7Days} in the last 7 days`} />
        <Stat label="Active" value={stats.activeUsers} hint={`${stats.pendingUsers} pending, ${stats.suspendedUsers} suspended`} />
        <Stat label="Resumes" value={stats.totalResumes} hint={`${stats.totalAnalyses} analyses run`} />
        <Stat label="Matches" value={stats.totalMatches} hint={`${stats.totalInterviews} interviews`} />
      </div>

      <Card className="mb-6">
        <CardHeader title="Job queue" description="Background work across the platform." />
        <div className="grid grid-cols-3 divide-x divide-ink-100 text-center">
          <QueueCell label="Queued" value={stats.queuedJobs} tone="slate" />
          <QueueCell label="Running" value={stats.runningJobs} tone="brand" />
          <QueueCell label="Failed" value={stats.failedJobs} tone={stats.failedJobs > 0 ? 'red' : 'slate'} />
        </div>
      </Card>

      <Card>
        <CardHeader
          title="Users"
          description={`${users?.totalElements ?? 0} accounts`}
          action={
            <div className="w-full sm:w-64">
              <TextInput
                type="search"
                placeholder="Search email or name"
                value={query}
                onChange={(event) => {
                  setPage(0)
                  setQuery(event.target.value)
                }}
                aria-label="Search users"
              />
            </div>
          }
        />

        {!users || users.content.length === 0 ? (
          <EmptyState
            title="No accounts found"
            description={query ? 'Nothing matches that search.' : 'No users have registered yet.'}
          />
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[46rem] text-sm">
                <thead>
                  <tr className="border-b border-ink-100 text-left text-xs uppercase tracking-wide text-ink-400">
                    <th scope="col" className="px-5 py-3 font-medium">User</th>
                    <th scope="col" className="px-5 py-3 font-medium">Status</th>
                    <th scope="col" className="px-5 py-3 font-medium">Roles</th>
                    <th scope="col" className="px-5 py-3 font-medium">Last seen</th>
                    <th scope="col" className="px-5 py-3 font-medium">Joined</th>
                    <th scope="col" className="px-5 py-3 font-medium sr-only">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-ink-100">
                  {users.content.map((user) => (
                    <tr key={user.id}>
                      <td className="px-5 py-3">
                        <p className="font-medium text-ink-900">{user.fullName}</p>
                        <p className="text-xs text-ink-500">{user.email}</p>
                      </td>
                      <td className="px-5 py-3">
                        <div className="flex flex-wrap gap-1.5">
                          <Badge tone={STATUS_TONE[user.status] ?? 'slate'}>{user.status}</Badge>
                          {user.locked && <Badge tone="red">Locked</Badge>}
                          {!user.emailVerified && <Badge tone="amber">Unverified</Badge>}
                        </div>
                      </td>
                      <td className="px-5 py-3 text-ink-600">
                        {user.roles.map((role) => role.replace('ROLE_', '')).join(', ')}
                      </td>
                      <td className="px-5 py-3 text-ink-600">
                        {user.lastLoginAt ? relativeTime(user.lastLoginAt) : 'Never'}
                      </td>
                      <td className="px-5 py-3 text-ink-600">{formatDate(user.createdAt)}</td>
                      <td className="px-5 py-3 text-right">
                        {user.status === 'SUSPENDED' ? (
                          <Button
                            size="sm"
                            variant="secondary"
                            loading={busyId === user.id}
                            onClick={() => setStatus(user.id, 'REACTIVATE')}
                          >
                            Reinstate
                          </Button>
                        ) : (
                          <Button
                            size="sm"
                            variant="ghost"
                            className="text-red-600 hover:bg-red-50"
                            loading={busyId === user.id}
                            onClick={() => setStatus(user.id, 'SUSPEND')}
                          >
                            Suspend
                          </Button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {users.totalPages > 1 && (
              <div className="flex items-center justify-between border-t border-ink-100 px-5 py-3">
                <p className="text-sm text-ink-500">
                  Page {users.page + 1} of {users.totalPages}
                </p>
                <div className="flex gap-2">
                  <Button
                    size="sm"
                    variant="secondary"
                    disabled={users.page === 0}
                    onClick={() => setPage((current) => Math.max(0, current - 1))}
                  >
                    Previous
                  </Button>
                  <Button size="sm" variant="secondary" disabled={users.last} onClick={() => setPage((current) => current + 1)}>
                    Next
                  </Button>
                </div>
              </div>
            )}
          </>
        )}
      </Card>
    </>
  )
}

function QueueCell({ label, value, tone }) {
  return (
    <div className="px-5 py-5">
      <p className="text-xs font-medium uppercase tracking-wide text-ink-400">{label}</p>
      <p
        className={
          tone === 'red'
            ? 'mt-1 text-2xl font-semibold tabular-nums text-red-600'
            : tone === 'brand'
              ? 'mt-1 text-2xl font-semibold tabular-nums text-brand-700'
              : 'mt-1 text-2xl font-semibold tabular-nums text-ink-900'
        }
      >
        {value}
      </p>
    </div>
  )
}
