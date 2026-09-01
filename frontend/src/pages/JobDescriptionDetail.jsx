import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  Alert,
  Badge,
  Button,
  Card,
  CardHeader,
  EmptyState,
  Field,
  Loading,
  PageHeader,
  ScoreBar,
  ScoreRing,
  Select,
  cx,
} from '../components/ui'
import { api, errorMessage, unwrap } from '../lib/api'
import { SKILL_CATEGORY_LABEL, matchTone, relativeTime } from '../lib/format'

const SUGGESTION_LABEL = {
  SURFACE_SKILL: 'Placement',
  REPHRASE: 'Focus',
  QUANTIFY: 'Evidence',
  MIRROR_TITLE: 'Wording',
  LEARN: 'Gap',
}

export default function JobDescriptionDetail() {
  const { id } = useParams()
  const navigate = useNavigate()

  const [posting, setPosting] = useState(null)
  const [match, setMatch] = useState(null)
  const [resumes, setResumes] = useState([])
  const [selectedResume, setSelectedResume] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [actionError, setActionError] = useState(null)
  const [working, setWorking] = useState(false)
  const [showText, setShowText] = useState(false)

  const load = useCallback(async () => {
    setError(null)
    try {
      const postingData = unwrap(await api.get(`/job-descriptions/${id}`))
      setPosting(postingData)

      const [matchResult, resumeResult] = await Promise.allSettled([
        api.get(`/job-descriptions/${id}/match`),
        api.get('/resumes', { params: { page: 0, size: 50 } }),
      ])

      const latestMatch = matchResult.status === 'fulfilled' ? unwrap(matchResult.value) : null
      setMatch(latestMatch)

      const available =
        resumeResult.status === 'fulfilled'
          ? (unwrap(resumeResult.value)?.content ?? []).filter((resume) => resume.analysable)
          : []
      setResumes(available)

      // Default to the resume the last match used, then the primary, then the
      // newest — in that order, because it is the order of what the user most
      // likely means.
      setSelectedResume((current) => {
        if (current) return current
        if (latestMatch?.resumeId && available.some((r) => r.id === latestMatch.resumeId)) {
          return latestMatch.resumeId
        }
        return available.find((resume) => resume.primary)?.id ?? available[0]?.id ?? ''
      })
    } catch (err) {
      setError(errorMessage(err, 'Could not load this job description.'))
    } finally {
      setLoading(false)
    }
  }, [id])

  useEffect(() => {
    load()
  }, [load])

  async function runMatch() {
    if (!selectedResume) return
    setWorking(true)
    setActionError(null)
    try {
      setMatch(unwrap(await api.post(`/job-descriptions/${id}/match`, { resumeId: selectedResume })))
    } catch (err) {
      setActionError(errorMessage(err, 'Could not run the match.'))
    } finally {
      setWorking(false)
    }
  }

  async function remove() {
    if (!window.confirm('Delete this job description and its match history?')) return
    try {
      await api.delete(`/job-descriptions/${id}`)
      navigate('/jobs', { replace: true })
    } catch (err) {
      setActionError(errorMessage(err, 'Could not delete it.'))
    }
  }

  if (loading) return <Loading label="Loading this job description…" />

  if (error) {
    return (
      <Alert tone="red" title="Could not load this posting" action={<Button onClick={load}>Try again</Button>}>
        {error}
      </Alert>
    )
  }

  return (
    <>
      <PageHeader
        breadcrumb={
          <Link to="/jobs" className="mb-2 inline-flex items-center gap-1 rounded text-sm text-ink-500 hover:text-ink-800">
            <svg viewBox="0 0 20 20" className="h-4 w-4" fill="currentColor" aria-hidden="true">
              <path d="M12.72 4.22a.75.75 0 0 1 0 1.06L8.06 10l4.66 4.72a.75.75 0 1 1-1.06 1.06l-5.25-5.25a.75.75 0 0 1 0-1.06l5.25-5.25a.75.75 0 0 1 1.06 0Z" />
            </svg>
            All job matches
          </Link>
        }
        title={posting.title}
        description={[posting.company, posting.location].filter(Boolean).join(' · ') || 'No company given'}
        action={
          <>
            {posting.sourceUrl && (
              <Button as="a" href={posting.sourceUrl} target="_blank" rel="noreferrer noopener" variant="secondary" size="sm">
                Open posting
              </Button>
            )}
            <Button variant="ghost" size="sm" className="text-red-600 hover:bg-red-50" onClick={remove}>
              Delete
            </Button>
          </>
        }
      />

      {actionError && (
        <Alert tone="red" title="That did not work" className="mb-6">
          {actionError}
        </Alert>
      )}

      <Card className="mb-6">
        <CardHeader
          title="Match a resume against this posting"
          description="Each run is kept, so you can edit your resume and see whether the number moves."
        />
        <div className="flex flex-col gap-3 p-5 sm:flex-row sm:items-end">
          <Field label="Resume" htmlFor="resume" className="flex-1">
            <Select
              id="resume"
              value={selectedResume}
              onChange={(event) => setSelectedResume(event.target.value)}
              disabled={resumes.length === 0}
            >
              {resumes.length === 0 ? (
                <option value="">No parsed resume available</option>
              ) : (
                resumes.map((resume) => (
                  <option key={resume.id} value={resume.id}>
                    {resume.originalFilename}
                    {resume.primary ? ' (primary)' : ''}
                  </option>
                ))
              )}
            </Select>
          </Field>
          <Button size="lg" loading={working} disabled={!selectedResume} onClick={runMatch}>
            {match ? 'Run again' : 'Run the match'}
          </Button>
        </div>
        {resumes.length === 0 && (
          <div className="px-5 pb-5">
            <Alert tone="amber" title="No parsed resume yet">
              Upload a resume and let it finish parsing before matching it against a posting.{' '}
              <Link to="/resumes" className="font-medium text-brand-700 hover:text-brand-800">
                Go to resumes
              </Link>
            </Alert>
          </div>
        )}
      </Card>

      {!match ? (
        <Card>
          <EmptyState
            title="No match run yet"
            description="Choose a resume above and run the match. You will get a percentage, the requirements you already meet, and the gaps ranked by how much they cost you against this specific posting."
          />
        </Card>
      ) : (
        <MatchReport match={match} />
      )}

      <Card className="mt-6">
        <CardHeader
          title="The posting"
          description={`${posting.characterCount.toLocaleString()} characters, as you pasted it`}
          action={
            <Button variant="ghost" size="sm" onClick={() => setShowText((open) => !open)}>
              {showText ? 'Hide' : 'Show'}
            </Button>
          }
        />
        {showText && (
          <pre className="max-h-96 overflow-auto whitespace-pre-wrap break-words px-5 py-4 font-mono text-[13px] leading-6 text-ink-700">
            {posting.rawText}
          </pre>
        )}
      </Card>
    </>
  )
}

function MatchReport({ match }) {
  return (
    <div className="space-y-6">
      <div className="grid gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-1">
          <div className="flex flex-col items-center px-5 py-7">
            <ScoreRing score={match.overallScore} tone={matchTone(match.overallScore)} size={150} sublabel="match" />
            <p className="mt-4 text-lg font-semibold text-ink-900">{match.bandLabel}</p>
            <p className="mt-1 text-center text-sm leading-6 text-ink-600">{match.advice}</p>
            <p className="mt-4 text-xs text-ink-400">Matched {relativeTime(match.createdAt)}</p>
          </div>
        </Card>

        <Card className="lg:col-span-2">
          <CardHeader title="How the percentage is built" description="Published deliberately — a score you cannot check is not a score." />
          <div className="grid gap-5 px-5 py-5 sm:grid-cols-2">
            <ScoreBar
              label="Must-have skills · 55%"
              score={match.requiredSkillScore}
              tone={matchTone(match.requiredSkillScore)}
              hint="Skills the posting states as requirements."
            />
            <ScoreBar
              label="Nice-to-haves · 15%"
              score={match.optionalSkillScore}
              tone={matchTone(match.optionalSkillScore)}
              hint="Preferred skills. Worth less, and never worth lying about."
            />
            <ScoreBar
              label="Title overlap · 15%"
              score={match.titleScore}
              tone={matchTone(match.titleScore)}
              hint="Words shared between your roles and this one."
            />
            <ScoreBar
              label="Experience · 15%"
              score={match.experienceScore}
              tone={matchTone(match.experienceScore)}
              hint="Your dated experience against any stated minimum. Overlapping roles are not double-counted."
            />
          </div>
        </Card>
      </div>

      {match.suggestions?.length > 0 && (
        <Card>
          <CardHeader
            title="What to change"
            description="Every suggestion rewrites something you already wrote. Nothing here invents experience."
          />
          <ul className="divide-y divide-ink-100">
            {match.suggestions.map((suggestion, index) => (
              <li key={`${suggestion.kind}-${index}`} className="px-5 py-4">
                <div className="flex flex-wrap items-center gap-2">
                  <Badge tone={suggestion.kind === 'LEARN' ? 'amber' : 'brand'}>
                    {SUGGESTION_LABEL[suggestion.kind] ?? suggestion.kind}
                  </Badge>
                  <p className="text-sm font-medium text-ink-900">{suggestion.title}</p>
                </div>
                <p className="mt-2 text-sm leading-6 text-ink-600">{suggestion.rationale}</p>

                {suggestion.before && (
                  <div className="mt-3 grid gap-3 sm:grid-cols-2">
                    <div className="rounded-lg border border-ink-200 bg-ink-50 p-3">
                      <p className="text-xs font-semibold uppercase tracking-wide text-ink-500">Now</p>
                      <p className="mt-1 text-sm leading-6 text-ink-700">{suggestion.before}</p>
                    </div>
                    <div className="rounded-lg border border-emerald-200 bg-emerald-50 p-3">
                      <p className="text-xs font-semibold uppercase tracking-wide text-emerald-700">Suggested</p>
                      <p className="mt-1 text-sm leading-6 text-ink-800">{suggestion.after}</p>
                    </div>
                  </div>
                )}
                {!suggestion.before && suggestion.after && (
                  <div className="mt-3 rounded-lg border border-brand-200 bg-brand-50 p-3">
                    <p className="text-sm leading-6 text-ink-800">{suggestion.after}</p>
                  </div>
                )}
              </li>
            ))}
          </ul>
        </Card>
      )}

      <div className="grid gap-6 lg:grid-cols-2">
        <SkillPanel
          title="Gaps"
          description="Ranked by how much each costs you against this posting."
          skills={match.missing}
          tone="red"
          emptyTitle="No gaps"
          emptyDescription="Your resume covers every skill this posting names."
          showJd
        />
        <SkillPanel
          title="Requirements you meet"
          description="Found on your resume and named in the posting."
          skills={match.matched}
          tone="emerald"
          emptyTitle="No overlap found"
          emptyDescription="Nothing this posting asks for appears on your resume yet."
          showResume
        />
      </div>

      {match.extra?.length > 0 && (
        <Card>
          <CardHeader
            title="On your resume, not in this posting"
            description={`${match.extra.length} skills. Not a problem — but if the list is long, this resume is aimed wider than this role.`}
          />
          <div className="flex flex-wrap gap-2 p-5">
            {match.extra.map((skill) => (
              <span
                key={skill.normalizedName}
                className="rounded-full border border-ink-200 bg-white px-3 py-1 text-sm text-ink-600"
              >
                {skill.name}
              </span>
            ))}
          </div>
        </Card>
      )}
    </div>
  )
}

function SkillPanel({ title, description, skills = [], tone, emptyTitle, emptyDescription, showJd, showResume }) {
  return (
    <Card>
      <CardHeader title={`${title} · ${skills.length}`} description={description} />
      {skills.length === 0 ? (
        <EmptyState title={emptyTitle} description={emptyDescription} />
      ) : (
        <ul className="divide-y divide-ink-100">
          {skills.map((skill) => (
            <li key={skill.normalizedName} className="px-5 py-3.5">
              <div className="flex flex-wrap items-center gap-2">
                <span className={cx('h-2 w-2 shrink-0 rounded-full', tone === 'red' ? 'bg-red-500' : 'bg-emerald-500')} />
                <span className="text-sm font-medium text-ink-900">{skill.name}</span>
                {skill.required && <Badge tone="amber">Required</Badge>}
                <span className="text-xs text-ink-400">
                  {SKILL_CATEGORY_LABEL[skill.category] ?? skill.category}
                </span>
              </div>
              {showJd && skill.jdEvidence && (
                <p className="mt-1.5 text-xs leading-5 text-ink-500">
                  The posting says: “{skill.jdEvidence}”
                </p>
              )}
              {showResume && skill.resumeEvidence && (
                <p className="mt-1.5 text-xs leading-5 text-ink-500">
                  Your resume says: “{skill.resumeEvidence}”
                </p>
              )}
            </li>
          ))}
        </ul>
      )}
    </Card>
  )
}
