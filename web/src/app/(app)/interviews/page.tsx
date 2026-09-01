'use client'

import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useCallback, useEffect, useState } from 'react'
import {
  Alert, Badge, Button, Card, CardHeader, EmptyState, Field, Loading, PageHeader, Select,
} from '@/components/ui'
import { api, errorMessage } from '@/lib/client'
import { relativeTime, scoreTone } from '@/lib/format'

const FOCUS_OPTIONS = [
  {
    value: 'RESUME_DEEP_DIVE',
    label: 'Resume deep dive',
    description:
      'Questions drawn from what is actually on your resume — the ones an interviewer who read it would ask.',
    needsResume: true,
  },
  {
    value: 'JOB_SPECIFIC',
    label: 'Targeted at a posting',
    description:
      'Built around the gaps between your resume and one job description, including the ones you would rather not be asked about.',
    needsResume: true,
    needsPosting: true,
  },
  {
    value: 'BEHAVIOURAL',
    label: 'Behavioural',
    description: 'Conflict, failure, ownership, and working with other people. Needs no resume.',
  },
  {
    value: 'GENERAL',
    label: 'General practice',
    description: 'A spread across your background, your projects, and how you work.',
  },
] as const

const STATUS_TONE: Record<string, string> = {
  IN_PROGRESS: 'amber', COMPLETED: 'emerald', ABANDONED: 'slate',
}
const STATUS_LABEL: Record<string, string> = {
  IN_PROGRESS: 'In progress', COMPLETED: 'Complete', ABANDONED: 'Abandoned',
}

export default function InterviewsPage() {
  const router = useRouter()

  const [sessions, setSessions] = useState<any[]>([])
  const [resumes, setResumes] = useState<any[]>([])
  const [postings, setPostings] = useState<any[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [starting, setStarting] = useState(false)
  const [startError, setStartError] = useState<string | null>(null)

  const [focus, setFocus] = useState<string>('RESUME_DEEP_DIVE')
  const [resumeId, setResumeId] = useState('')
  const [jobDescriptionId, setJobDescriptionId] = useState('')
  const [questionCount, setQuestionCount] = useState(6)

  const load = useCallback(async () => {
    setError(null)
    try {
      const [sessionResult, resumeResult, postingResult] = await Promise.allSettled([
        api.get<any[]>('/interviews'),
        api.get<any[]>('/resumes'),
        api.get<any[]>('/job-descriptions'),
      ])

      setSessions(sessionResult.status === 'fulfilled' ? sessionResult.value : [])

      const parsed =
        resumeResult.status === 'fulfilled'
          ? resumeResult.value.filter((resume) => resume.status === 'PARSED')
          : []
      setResumes(parsed)
      setResumeId((current) => current || parsed.find((r) => r.isPrimary)?.id || parsed[0]?.id || '')

      const saved = postingResult.status === 'fulfilled' ? postingResult.value : []
      setPostings(saved)
      setJobDescriptionId((current) => current || saved[0]?.id || '')
    } catch (err) {
      setError(errorMessage(err, 'Could not load your interviews.'))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const selected = FOCUS_OPTIONS.find((option) => option.value === focus)
  const missingResume = Boolean(selected && 'needsResume' in selected && selected.needsResume && !resumeId)
  const missingPosting = Boolean(
    selected && 'needsPosting' in selected && selected.needsPosting && !jobDescriptionId,
  )

  async function start() {
    setStarting(true)
    setStartError(null)
    try {
      const session = await api.post<{ id: string }>('/interviews', {
        focus,
        resumeId: resumeId || null,
        jobDescriptionId:
          selected && 'needsPosting' in selected && selected.needsPosting ? jobDescriptionId : null,
        questionCount: Number(questionCount),
      })
      router.push(`/interviews/${session.id}`)
    } catch (err) {
      setStartError(errorMessage(err, 'Could not start the interview.'))
      setStarting(false)
    }
  }

  return (
    <>
      <PageHeader
        title="Mock interviews"
        description="Questions built from your own resume and the gaps against a specific posting. Answers are scored on structure, specifics, relevance, and clarity."
      />

      <Alert tone="slate" className="mb-6" title="Content only">
        Feedback comes from what your answers say. There is no camera, no microphone, and no scoring
        of how you look or sound.
      </Alert>

      <Card className="mb-6">
        <CardHeader title="Start an interview" description="Three to twelve questions. You can leave and come back." />
        <div className="space-y-4 p-5">
          {startError && <Alert tone="red" title="Could not start">{startError}</Alert>}

          <Field label="What do you want to practise?" htmlFor="focus" hint={selected?.description}>
            <Select id="focus" value={focus} onChange={(e: React.ChangeEvent<HTMLSelectElement>) => setFocus(e.target.value)}>
              {FOCUS_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>{option.label}</option>
              ))}
            </Select>
          </Field>

          <div className="grid gap-4 sm:grid-cols-3">
            <Field
              label="Resume"
              htmlFor="resume"
              hint={selected && 'needsResume' in selected && selected.needsResume ? 'Required for this focus.' : 'Optional — makes the questions personal.'}
            >
              <Select id="resume" value={resumeId} onChange={(e: React.ChangeEvent<HTMLSelectElement>) => setResumeId(e.target.value)}>
                <option value="">None</option>
                {resumes.map((resume) => (
                  <option key={resume.id} value={resume.id}>{resume.originalFilename}</option>
                ))}
              </Select>
            </Field>

            <Field
              label="Job description"
              htmlFor="posting"
              hint={selected && 'needsPosting' in selected && selected.needsPosting ? 'Required for this focus.' : 'Only used for a targeted interview.'}
            >
              <Select
                id="posting"
                value={jobDescriptionId}
                onChange={(e: React.ChangeEvent<HTMLSelectElement>) => setJobDescriptionId(e.target.value)}
                disabled={!(selected && 'needsPosting' in selected && selected.needsPosting)}
              >
                <option value="">None</option>
                {postings.map((posting) => (
                  <option key={posting.id} value={posting.id}>{posting.title}</option>
                ))}
              </Select>
            </Field>

            <Field label="Questions" htmlFor="count">
              <Select
                id="count"
                value={questionCount}
                onChange={(e: React.ChangeEvent<HTMLSelectElement>) => setQuestionCount(Number(e.target.value))}
              >
                {[3, 5, 6, 8, 10, 12].map((value) => (
                  <option key={value} value={value}>{value}</option>
                ))}
              </Select>
            </Field>
          </div>

          {missingResume && (
            <Alert tone="amber" title="A parsed resume is needed">
              This focus draws its questions from your resume.{' '}
              <Link href="/resumes" className="font-medium text-brand-700 hover:text-brand-800">
                Upload one first
              </Link>
              , or switch to behavioural practice.
            </Alert>
          )}
          {missingPosting && (
            <Alert tone="amber" title="A job description is needed">
              A targeted interview probes the gaps against one posting.{' '}
              <Link href="/jobs" className="font-medium text-brand-700 hover:text-brand-800">
                Add one first
              </Link>
              .
            </Alert>
          )}

          <Button size="lg" loading={starting} disabled={missingResume || missingPosting} onClick={start}>
            {starting ? 'Preparing your questions…' : 'Start interview'}
          </Button>
        </div>
      </Card>

      {error && (
        <Alert tone="red" title="Something went wrong" className="mb-6" action={<Button onClick={load}>Retry</Button>}>
          {error}
        </Alert>
      )}

      <Card>
        <CardHeader title="Past interviews" description={`${sessions.length} sessions`} />
        {loading ? (
          <Loading label="Loading your interviews…" />
        ) : sessions.length === 0 ? (
          <EmptyState
            title="No interviews yet"
            description="Start one above. Answers are scored the moment you submit them, so you can rewrite and watch the score move."
          />
        ) : (
          <ul className="divide-y divide-ink-100">
            {sessions.map((session) => (
              <li key={session.id}>
                <Link
                  href={`/interviews/${session.id}`}
                  className="flex flex-col gap-3 px-5 py-4 transition hover:bg-ink-50 sm:flex-row sm:items-center sm:justify-between"
                >
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="text-sm font-medium text-ink-900">{session.focusLabel}</span>
                      <Badge tone={STATUS_TONE[session.status] ?? 'slate'}>
                        {STATUS_LABEL[session.status] ?? session.status}
                      </Badge>
                    </div>
                    <p className="mt-0.5 text-xs text-ink-500">
                      {session.answeredCount} of {session.questionCount} answered · started{' '}
                      {relativeTime(session.createdAt)}
                    </p>
                  </div>
                  {session.overallScore != null && (
                    <Badge tone={scoreTone(session.overallScore)}>{session.overallScore} / 100</Badge>
                  )}
                </Link>
              </li>
            ))}
          </ul>
        )}
      </Card>
    </>
  )
}
