import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  Alert,
  Badge,
  Button,
  Card,
  CardHeader,
  EmptyState,
  Loading,
  PageHeader,
  ScoreRing,
  Stat,
} from '../components/ui'
import { api, errorMessage, unwrap } from '../lib/api'
import { useAuth } from '../lib/auth'
import { RESUME_STATUS, relativeTime, scoreTone } from '../lib/format'

export default function Dashboard() {
  const { user } = useAuth()
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setData(unwrap(await api.get('/dashboard')))
    } catch (err) {
      setError(errorMessage(err, 'Could not load your dashboard.'))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  if (loading) return <Loading label="Loading your dashboard…" />

  if (error) {
    return (
      <Alert tone="red" title="Something went wrong" action={<Button onClick={load}>Try again</Button>}>
        {error}
      </Alert>
    )
  }

  const firstName = user?.fullName?.split(' ')[0] ?? 'there'
  const hasResumes = (data?.recentResumes?.length ?? 0) > 0
  const score = data?.latestScore

  return (
    <>
      <PageHeader
        title={`Welcome back, ${firstName}`}
        description={
          hasResumes
            ? 'Here is where your resume stands, and what to fix next.'
            : 'Upload a resume to see exactly what a screening system reads from it.'
        }
        action={
          hasResumes && (
            <Button as={Link} to="/resumes">
              Upload another resume
            </Button>
          )
        }
      />

      {!hasResumes ? (
        <Card>
          <EmptyState
            icon={
              <svg viewBox="0 0 24 24" className="h-6 w-6" fill="none" stroke="currentColor" strokeWidth="1.8">
                <path strokeLinecap="round" strokeLinejoin="round" d="M12 16V4m0 0L8 8m4-4 4 4M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2" />
              </svg>
            }
            title="No resume yet"
            description="Upload a PDF or Word document and we will show you the text a screener extracts, the sections it recognises, and everything it misses."
            action={
              <Button as={Link} to="/resumes" size="lg">
                Upload your first resume
              </Button>
            }
          />
        </Card>
      ) : (
        <div className="space-y-6">
          <div className="grid gap-6 lg:grid-cols-3">
            <Card className="lg:col-span-2">
              <CardHeader
                title="Your primary resume"
                description={data.focusResume?.originalFilename}
                action={
                  data.focusResume && (
                    <Button as={Link} to={`/resumes/${data.focusResume.id}`} variant="secondary" size="sm">
                      Open
                    </Button>
                  )
                }
              />
              <div className="flex flex-col items-center gap-8 px-5 py-6 sm:flex-row sm:items-start">
                {score ? (
                  <>
                    <ScoreRing
                      score={score.overallScore}
                      tone={scoreTone(score.overallScore)}
                      sublabel="/ 100"
                      label={score.bandLabel}
                    />
                    <div className="min-w-0 flex-1 text-center sm:text-left">
                      <p className="text-sm leading-6 text-ink-700">{score.bandSummary}</p>
                      <p className="mt-3 text-sm text-ink-500">
                        {score.problemCount === 0
                          ? 'No problems found — a rare and good place to be.'
                          : `${score.problemCount} thing${score.problemCount === 1 ? '' : 's'} to fix, each with the line that caused it.`}
                      </p>
                      <p className="mt-1 text-xs text-ink-400">
                        Scored {relativeTime(score.createdAt)}
                      </p>
                      <Button
                        as={Link}
                        to={`/resumes/${data.focusResume.id}`}
                        size="sm"
                        className="mt-4"
                      >
                        See the findings
                      </Button>
                    </div>
                  </>
                ) : (
                  <div className="w-full text-center sm:text-left">
                    <Badge tone={RESUME_STATUS[data.focusResume?.status]?.tone ?? 'slate'}>
                      {RESUME_STATUS[data.focusResume?.status]?.label ?? data.focusResume?.status}
                    </Badge>
                    <p className="mt-3 text-sm text-ink-600">
                      {data.focusResume?.status === 'PARSING'
                        ? 'We are reading this resume now. It usually takes a few seconds.'
                        : 'This resume has not been scored yet. Open it to run the analysis.'}
                    </p>
                    {data.focusResume && (
                      <Button as={Link} to={`/resumes/${data.focusResume.id}`} size="sm" className="mt-4">
                        Open resume
                      </Button>
                    )}
                  </div>
                )}
              </div>
            </Card>

            <div className="grid grid-cols-2 gap-4 lg:grid-cols-1">
              <Stat label="Resumes" value={data.counts?.resumes ?? 0} />
              <Stat label="Job descriptions" value={data.counts?.jobDescriptions ?? 0} />
              <Stat label="Matches run" value={data.counts?.matches ?? 0} />
              <Stat
                label="Interviews"
                value={data.counts?.interviews ?? 0}
                hint={
                  data.averageInterviewScore != null
                    ? `Average score ${Math.round(data.averageInterviewScore)}`
                    : undefined
                }
              />
            </div>
          </div>

          <div className="grid gap-6 lg:grid-cols-2">
            <Card>
              <CardHeader
                title="Recent resumes"
                action={
                  <Button as={Link} to="/resumes" variant="ghost" size="sm">
                    See all
                  </Button>
                }
              />
              <ul className="divide-y divide-ink-100">
                {data.recentResumes.map((resume) => {
                  const status = RESUME_STATUS[resume.status] ?? { label: resume.status, tone: 'slate' }
                  return (
                    <li key={resume.id}>
                      <Link
                        to={`/resumes/${resume.id}`}
                        className="flex items-center justify-between gap-3 px-5 py-3.5 transition hover:bg-ink-50"
                      >
                        <div className="min-w-0">
                          <p className="truncate text-sm font-medium text-ink-900">
                            {resume.originalFilename}
                          </p>
                          <p className="mt-0.5 text-xs text-ink-500">
                            Uploaded {relativeTime(resume.createdAt)}
                            {resume.primary && ' · Primary'}
                          </p>
                        </div>
                        <Badge tone={status.tone}>{status.label}</Badge>
                      </Link>
                    </li>
                  )
                })}
              </ul>
            </Card>

            <Card>
              <CardHeader title="What to do next" />
              <ul className="divide-y divide-ink-100">
                <NextStep
                  to="/resumes"
                  title="Check the parse"
                  body="See the exact text a screener extracts from your resume, and what it misses."
                />
                <NextStep
                  to="/jobs"
                  title="Match against a real posting"
                  body="Paste a job description for a match percentage and a ranked list of gaps."
                />
                <NextStep
                  to="/interviews"
                  title="Rehearse the interview"
                  body="Questions built from your own resume, scored on structure and specifics."
                />
              </ul>
            </Card>
          </div>
        </div>
      )}
    </>
  )
}

function NextStep({ to, title, body }) {
  return (
    <li>
      <Link to={to} className="flex items-start gap-3 px-5 py-4 transition hover:bg-ink-50">
        <span className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-brand-50 text-brand-600">
          <svg viewBox="0 0 20 20" className="h-4 w-4" fill="currentColor" aria-hidden="true">
            <path d="M7.28 4.22a.75.75 0 0 0-1.06 1.06L10.94 10l-4.72 4.72a.75.75 0 1 0 1.06 1.06l5.25-5.25a.75.75 0 0 0 0-1.06L7.28 4.22Z" />
          </svg>
        </span>
        <div className="min-w-0">
          <p className="text-sm font-medium text-ink-900">{title}</p>
          <p className="mt-0.5 text-sm text-ink-500">{body}</p>
        </div>
      </Link>
    </li>
  )
}
