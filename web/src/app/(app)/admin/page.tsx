'use client'

import { useCallback, useEffect, useState } from 'react'
import {
  Alert, Badge, Button, Card, CardHeader, EmptyState, Loading, PageHeader, Stat, TextInput,
} from '@/components/ui'
import { api, errorMessage } from '@/lib/client'
import { formatDate, relativeTime } from '@/lib/format'

const STATUS_TONE: Record<string, string> = {
  ACTIVE: 'emerald', SUSPENDED: 'red', DELETED: 'slate',
}

export default function AdminPage() {
  const [stats, setStats] = useState<any>(null)
  const [users, setUsers] = useState<any[]>([])
  const [query, setQuery] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setError(null)
    try {
      const [statsResult, usersResult] = await Promise.all([
        api.get<any>('/admin/stats'),
        api.get<any[]>(`/admin/users${query ? `?query=${encodeURIComponent(query)}` : ''}`),
      ])
      setStats(statsResult)
      setUsers(usersResult)
    } catch (err) {
      setError(errorMessage(err, 'Could not load the admin console.'))
    } finally {
      setLoading(false)
    }
  }, [query])

  useEffect(() => {
    load()
  }, [load])

  if (loading) return <Loading label="Loading the admin console…" />

  if (error || !stats) {
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
        description="Platform totals and accounts. Counts only — nothing here quotes a user's documents."
      />

      <div className="mb-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Stat label="Users" value={stats.totalUsers} hint={`${stats.newUsersLast7Days} in the last 7 days`} />
        <Stat label="Active" value={stats.activeUsers} hint={`${stats.suspendedUsers} suspended`} />
        <Stat label="Resumes" value={stats.totalResumes} hint={`${stats.totalAnalyses} analyses run`} />
        <Stat label="Matches" value={stats.totalMatches} hint={`${stats.totalInterviews} interviews`} />
      </div>

      <Card>
        <CardHeader
          title="Users"
          description={`${users.length} shown`}
          action={
            <div className="w-full sm:w-64">
              <TextInput
                type="search"
                placeholder="Search email or name"
                value={query}
                onChange={(e: React.ChangeEvent<HTMLInputElement>) => setQuery(e.target.value)}
                aria-label="Search users"
              />
            </div>
          }
        />

        {users.length === 0 ? (
          <EmptyState
            title="No accounts found"
            description={query ? 'Nothing matches that search.' : 'No users have registered yet.'}
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[40rem] text-sm">
              <thead>
                <tr className="border-b border-ink-100 text-left text-xs uppercase tracking-wide text-ink-400">
                  <th scope="col" className="px-5 py-3 font-medium">User</th>
                  <th scope="col" className="px-5 py-3 font-medium">Status</th>
                  <th scope="col" className="px-5 py-3 font-medium">Role</th>
                  <th scope="col" className="px-5 py-3 font-medium">Last seen</th>
                  <th scope="col" className="px-5 py-3 font-medium">Joined</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-ink-100">
                {users.map((user) => (
                  <tr key={user.id}>
                    <td className="px-5 py-3">
                      <p className="font-medium text-ink-900">{user.fullName}</p>
                      <p className="text-xs text-ink-500">{user.email}</p>
                    </td>
                    <td className="px-5 py-3">
                      <Badge tone={STATUS_TONE[user.status] ?? 'slate'}>{user.status}</Badge>
                    </td>
                    <td className="px-5 py-3 text-ink-600">{user.role}</td>
                    <td className="px-5 py-3 text-ink-600">
                      {user.lastLoginAt ? relativeTime(user.lastLoginAt) : 'Never'}
                    </td>
                    <td className="px-5 py-3 text-ink-600">{formatDate(user.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </>
  )
}
