'use client'

import Link from 'next/link'
import { useCallback, useEffect, useState } from 'react'
import {
  Alert, Badge, Button, Card, CardHeader, EmptyState, Field, Loading, PageHeader, TextArea, TextInput,
} from '@/components/ui'
import { api, errorMessage } from '@/lib/client'
import { matchTone, relativeTime } from '@/lib/format'

const MIN_TEXT = 40

interface PostingRow {
  id: string
  title: string
  company: string | null
  location: string | null
  characterCount: number
  latestScore: number | null
  latestBand: string | null
  createdAt: string
}

export default function JobsPage() {
  const [postings, setPostings] = useState<PostingRow[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState({ title: '', company: '', location: '', sourceUrl: '', rawText: '' })
  const [errors, setErrors] = useState<Record<string, string | undefined>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  const load = useCallback(async () => {
    setError(null)
    try {
      setPostings(await api.get<PostingRow[]>('/job-descriptions'))
    } catch (err) {
      setError(errorMessage(err, 'Could not load your job descriptions.'))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  function update(field: keyof typeof form, value: string) {
    setForm((current) => ({ ...current, [field]: value }))
    setErrors((current) => ({ ...current, [field]: undefined }))
  }

  function validate() {
    const next: Record<string, string> = {}
    if (!form.title.trim()) next.title = 'Give this posting a title'
    if (form.rawText.trim().length < MIN_TEXT) {
      next.rawText = `Paste the full posting — at least ${MIN_TEXT} characters. A short snippet produces a confident, meaningless score.`
    }
    setErrors(next)
    return Object.keys(next).length === 0
  }

  async function save(event: React.FormEvent) {
    event.preventDefault()
    setFormError(null)
    if (!validate()) return

    setSaving(true)
    try {
      await api.post('/job-descriptions', {
        title: form.title.trim(),
        company: form.company.trim() || null,
        location: form.location.trim() || null,
        sourceUrl: form.sourceUrl.trim() || null,
        rawText: form.rawText,
      })
      setForm({ title: '', company: '', location: '', sourceUrl: '', rawText: '' })
      setShowForm(false)
      await load()
    } catch (err) {
      setFormError(errorMessage(err, 'Could not save that job description.'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <>
      <PageHeader
        title="Job matches"
        description="Paste a posting to see how your resume scores against it, and which gaps matter most."
        action={
          <Button onClick={() => setShowForm((open) => !open)}>
            {showForm ? 'Cancel' : 'Add a job description'}
          </Button>
        }
      />

      {showForm && (
        <Card className="mb-6">
          <CardHeader
            title="Add a job description"
            description="Paste the whole posting, including the requirements list. More text means a better match."
          />
          <form onSubmit={save} noValidate className="space-y-4 p-5">
            {formError && <Alert tone="red" title="Could not save">{formError}</Alert>}

            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="Job title" htmlFor="title" error={errors.title}>
                <TextInput
                  id="title"
                  placeholder="Software Engineering Intern"
                  value={form.title}
                  error={errors.title}
                  onChange={(e: React.ChangeEvent<HTMLInputElement>) => update('title', e.target.value)}
                />
              </Field>
              <Field label="Company" htmlFor="company" hint="Optional">
                <TextInput
                  id="company"
                  placeholder="Acme"
                  value={form.company}
                  onChange={(e: React.ChangeEvent<HTMLInputElement>) => update('company', e.target.value)}
                />
              </Field>
              <Field label="Location" htmlFor="location" hint="Optional">
                <TextInput
                  id="location"
                  placeholder="Bengaluru, India"
                  value={form.location}
                  onChange={(e: React.ChangeEvent<HTMLInputElement>) => update('location', e.target.value)}
                />
              </Field>
              <Field label="Link to the posting" htmlFor="sourceUrl" hint="Optional">
                <TextInput
                  id="sourceUrl"
                  type="url"
                  placeholder="https://…"
                  value={form.sourceUrl}
                  onChange={(e: React.ChangeEvent<HTMLInputElement>) => update('sourceUrl', e.target.value)}
                />
              </Field>
            </div>

            <Field
              label="The posting"
              htmlFor="rawText"
              error={errors.rawText}
              hint={`${form.rawText.length} characters. Include the requirements and nice-to-haves — that split is what ranks your gaps.`}
            >
              <TextArea
                id="rawText"
                rows={10}
                placeholder="Paste the full job description here…"
                value={form.rawText}
                error={errors.rawText}
                onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => update('rawText', e.target.value)}
              />
            </Field>

            <div className="flex gap-2">
              <Button type="submit" loading={saving}>
                {saving ? 'Saving…' : 'Save job description'}
              </Button>
              <Button type="button" variant="secondary" onClick={() => setShowForm(false)}>
                Cancel
              </Button>
            </div>
          </form>
        </Card>
      )}

      {error && (
        <Alert tone="red" title="Something went wrong" className="mb-6" action={<Button onClick={load}>Retry</Button>}>
          {error}
        </Alert>
      )}

      <Card>
        <CardHeader title="Saved postings" description={`${postings.length} saved`} />
        {loading ? (
          <Loading label="Loading job descriptions…" />
        ) : postings.length === 0 ? (
          <EmptyState
            icon={
              <svg viewBox="0 0 24 24" className="h-6 w-6" fill="none" stroke="currentColor" strokeWidth="1.8">
                <path strokeLinecap="round" strokeLinejoin="round" d="M9 7V5a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v2m-9 0h14a1 1 0 0 1 1 1v11a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V8a1 1 0 0 1 1-1Z" />
              </svg>
            }
            title="No job descriptions yet"
            description="Paste a posting you are about to apply for. You will get a match percentage, the requirements you already meet, and the gaps ranked by how much they cost you."
            action={<Button onClick={() => setShowForm(true)}>Add your first posting</Button>}
          />
        ) : (
          <ul className="divide-y divide-ink-100">
            {postings.map((posting) => (
              <li key={posting.id}>
                <Link
                  href={`/jobs/${posting.id}`}
                  className="flex flex-col gap-3 px-5 py-4 transition hover:bg-ink-50 sm:flex-row sm:items-center sm:justify-between"
                >
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium text-ink-900">{posting.title}</p>
                    <p className="mt-0.5 truncate text-xs text-ink-500">
                      {[posting.company, posting.location].filter(Boolean).join(' · ') || 'No company given'}
                      {' · added '}
                      {relativeTime(posting.createdAt)}
                    </p>
                  </div>
                  <div className="flex shrink-0 items-center gap-3">
                    {posting.latestScore != null ? (
                      <Badge tone={matchTone(posting.latestScore)}>{posting.latestScore}% match</Badge>
                    ) : (
                      <Badge tone="slate">Not matched yet</Badge>
                    )}
                  </div>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </Card>
    </>
  )
}
