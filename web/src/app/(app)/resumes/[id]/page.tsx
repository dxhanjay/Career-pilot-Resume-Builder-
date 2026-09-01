'use client'

import Link from 'next/link'
import { useParams } from 'next/navigation'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  Alert, Badge, Button, Card, CardHeader, EmptyState, Loading, PageHeader, ScoreBar, ScoreRing, cx,
} from '@/components/ui'
import { ApiError, api, errorMessage } from '@/lib/client'
import {
  RESUME_STATUS, SEVERITY_LABEL, SEVERITY_TONE, SKILL_CATEGORY_LABEL,
  formatBytes, formatDate, relativeTime, scoreTone,
} from '@/lib/format'

/**
 * Hex values for the history bars.
 *
 * Inline styles rather than classes: the bar's height is already dynamic, and
 * Tailwind cannot generate a class name that only exists at runtime. Keyed off
 * the same scoreTone the rest of the page uses so the bar and the ring can never
 * disagree.
 */
const BAR_COLOURS: Record<string, string> = {
  emerald: '#10b981', brand: '#3182f6', amber: '#f59e0b', red: '#ef4444', slate: '#94a3b8',
}

const TABS = [
  { id: 'score', label: 'ATS score' },
  { id: 'parse', label: 'What the machine saw' },
  { id: 'entities', label: 'Extracted details' },
  { id: 'history', label: 'History' },
] as const

type TabId = (typeof TABS)[number]['id']

export default function ResumeDetailPage() {
  const { id } = useParams<{ id: string }>()

  const [resume, setResume] = useState<any>(null)
  const [structured, setStructured] = useState<any>(null)
  const [analysis, setAnalysis] = useState<any>(null)
  const [history, setHistory] = useState<any>(null)

  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [working, setWorking] = useState(false)
  const [tab, setTab] = useState<TabId>('score')
  const [highlight, setHighlight] = useState<number | null>(null)

  const lineRefs = useRef<Record<number, HTMLLIElement | null>>({})

  const load = useCallback(async () => {
    setError(null)
    try {
      setResume(await api.get(`/resumes/${id}`))

      // Each of these is 404 or 409 until it exists. Failing the whole page
      // because a resume has not been scored yet would hide the very screen
      // that explains why.
      const [parse, ats, hist] = await Promise.allSettled([
        api.get(`/resumes/${id}/parse`),
        api.get(`/resumes/${id}/ats`),
        api.get(`/resumes/${id}/ats/history`),
      ])

      setStructured(parse.status === 'fulfilled' ? parse.value : null)
      setAnalysis(ats.status === 'fulfilled' ? ats.value : null)
      setHistory(hist.status === 'fulfilled' ? hist.value : null)
    } catch (err) {
      setError(errorMessage(err, 'Could not load this resume.'))
    } finally {
      setLoading(false)
    }
  }, [id])

  useEffect(() => {
    load()
  }, [load])

  async function runAnalysis() {
    setWorking(true)
    setActionError(null)
    try {
      setAnalysis(await api.post(`/resumes/${id}/ats`))
      setHistory(await api.get(`/resumes/${id}/ats/history`))
      setTab('score')
    } catch (err) {
      setActionError(errorMessage(err, 'Could not run the analysis.'))
    } finally {
      setWorking(false)
    }
  }

  async function reparse() {
    setWorking(true)
    setActionError(null)
    try {
      await api.post(`/resumes/${id}/parse`)
      await load()
    } catch (err) {
      setActionError(errorMessage(err, 'Could not read this resume again.'))
    } finally {
      setWorking(false)
    }
  }

  /** Scrolls the raw-text panel to a quoted line and flashes it. */
  const showEvidence = useCallback((lineStart: number | null) => {
    if (lineStart === null || lineStart === undefined) return
    setTab('parse')
    setHighlight(lineStart)
    // The panel has to be on screen before it can be scrolled into view.
    window.setTimeout(() => {
      lineRefs.current[lineStart]?.scrollIntoView({ block: 'center', behavior: 'smooth' })
    }, 60)
  }, [])

  if (loading) return <Loading label="Loading this resume…" />

  if (error || !resume) {
    return (
      <Alert tone="red" title="Could not load this resume" action={<Button onClick={load}>Try again</Button>}>
        {error}
      </Alert>
    )
  }

  const status = RESUME_STATUS[resume.status] ?? { label: resume.status, tone: 'slate' }

  return (
    <>
      <PageHeader
        breadcrumb={
          <Link href="/resumes" className="mb-2 inline-flex items-center gap-1 rounded text-sm text-ink-500 hover:text-ink-800">
            <svg viewBox="0 0 20 20" className="h-4 w-4" fill="currentColor" aria-hidden="true">
              <path d="M12.72 4.22a.75.75 0 0 1 0 1.06L8.06 10l4.66 4.72a.75.75 0 1 1-1.06 1.06l-5.25-5.25a.75.75 0 0 1 0-1.06l5.25-5.25a.75.75 0 0 1 1.06 0Z" />
            </svg>
            All resumes
          </Link>
        }
        title={resume.originalFilename}
        description={`${formatBytes(resume.sizeBytes)} · uploaded ${formatDate(resume.createdAt)}`}
        action={
          <>
            <Badge tone={status.tone}>{status.label}</Badge>
            {resume.isPrimary && <Badge tone="brand">Primary</Badge>}
            {resume.status === 'PARSED' && (
              <Button size="sm" loading={working} onClick={runAnalysis}>
                {analysis ? 'Re-run analysis' : 'Run analysis'}
              </Button>
            )}
            {resume.status === 'PARSE_FAILED' && (
              <Button size="sm" loading={working} onClick={reparse}>
                Try reading again
              </Button>
            )}
          </>
        }
      />

      {actionError && (
        <Alert tone="red" title="That did not work" className="mb-6">{actionError}</Alert>
      )}

      {resume.status === 'PARSE_FAILED' && (
        <Alert tone="red" title="We could not read this file" className="mb-6">
          No usable text could be extracted. This almost always means the document is a scan or an
          image rather than real text — export a PDF directly from your word processor instead.
        </Alert>
      )}

      {structured?.warnings?.length > 0 && (
        <div className="mb-6 space-y-3">
          {structured.warnings.map((warning: any) => (
            <Alert key={warning.code} tone="amber" title={titleForWarning(warning.code)}>
              {warning.message}
            </Alert>
          ))}
        </div>
      )}

      {resume.status === 'PARSED' && (
        <>
          <div className="mb-6 overflow-x-auto border-b border-ink-200">
            <div className="flex min-w-max gap-1" role="tablist">
              {TABS.map((item) => (
                <button
                  key={item.id}
                  type="button"
                  role="tab"
                  aria-selected={tab === item.id}
                  onClick={() => setTab(item.id)}
                  className={cx(
                    '-mb-px border-b-2 px-4 py-2.5 text-sm font-medium transition',
                    tab === item.id
                      ? 'border-brand-600 text-brand-700'
                      : 'border-transparent text-ink-500 hover:border-ink-300 hover:text-ink-800',
                  )}
                >
                  {item.label}
                </button>
              ))}
            </div>
          </div>

          {tab === 'score' && (
            <ScoreTab analysis={analysis} working={working} onRun={runAnalysis} onEvidence={showEvidence} />
          )}
          {tab === 'parse' && (
            <ParseTab
              structured={structured}
              highlight={highlight}
              lineRefs={lineRefs}
              onClear={() => setHighlight(null)}
            />
          )}
          {tab === 'entities' && <EntitiesTab structured={structured} onEvidence={showEvidence} />}
          {tab === 'history' && <HistoryTab history={history} />}
        </>
      )}
    </>
  )
}

function titleForWarning(code: string) {
  return (
    {
      MULTI_COLUMN_LAYOUT: 'Multi-column layout detected',
      NO_TEXT_LAYER: 'No selectable text',
      SPARSE_TEXT: 'Very little text found',
      UNUSUALLY_LONG: 'Unusually long',
      ENCRYPTED_DOCUMENT: 'The document is protected',
    }[code] ?? 'Parse warning'
  )
}

function ScoreTab({ analysis, working, onRun, onEvidence }: any) {
  const [filter, setFilter] = useState<'problems' | 'passes' | 'all'>('problems')

  const findings = useMemo(() => {
    if (!analysis) return []
    if (filter === 'problems') return analysis.findings.filter((f: any) => f.severity !== 'PASS')
    if (filter === 'passes') return analysis.findings.filter((f: any) => f.severity === 'PASS')
    return analysis.findings
  }, [analysis, filter])

  if (!analysis) {
    return (
      <Card>
        <EmptyState
          title="Not scored yet"
          description="Run the analysis to score this resume against the rules screening software uses. Every point lost will name the line that caused it."
          action={<Button size="lg" loading={working} onClick={onRun}>Run the analysis</Button>}
        />
      </Card>
    )
  }

  return (
    <div className="grid gap-6 lg:grid-cols-3">
      <div className="space-y-6 lg:col-span-1">
        <Card>
          <div className="flex flex-col items-center px-5 py-7">
            <ScoreRing score={analysis.overallScore} tone={scoreTone(analysis.overallScore)} size={150} sublabel="/ 100" />
            <p className="mt-4 text-lg font-semibold text-ink-900">{analysis.bandLabel}</p>
            <p className="mt-1 text-center text-sm leading-6 text-ink-600">{analysis.bandSummary}</p>
            <p className="mt-4 text-xs text-ink-400">
              Scored {relativeTime(analysis.createdAt)} · rubric v{analysis.rubricVersion}
            </p>
          </div>
        </Card>

        <Card>
          <CardHeader title="Where the points went" description="Each category is weighted." />
          <div className="space-y-5 px-5 py-5">
            {analysis.categories.map((category: any) => (
              <ScoreBar
                key={category.category}
                label={`${category.displayName} · ${category.weight}%`}
                score={category.score}
                tone={scoreTone(category.score)}
                hint={category.description}
              />
            ))}
          </div>
        </Card>
      </div>

      <div className="lg:col-span-2">
        <Card>
          <CardHeader
            title="Findings"
            description={`${analysis.problemCount} to fix · ${analysis.passCount} already working`}
            action={
              <div className="flex rounded-lg border border-ink-200 p-0.5">
                {([
                  { id: 'problems', label: 'To fix' },
                  { id: 'passes', label: 'Working' },
                  { id: 'all', label: 'All' },
                ] as const).map((option) => (
                  <button
                    key={option.id}
                    type="button"
                    onClick={() => setFilter(option.id)}
                    className={cx(
                      'rounded-md px-3 py-1 text-xs font-medium transition',
                      filter === option.id ? 'bg-ink-900 text-white' : 'text-ink-600 hover:bg-ink-100',
                    )}
                  >
                    {option.label}
                  </button>
                ))}
              </div>
            }
          />

          {findings.length === 0 ? (
            <EmptyState
              title={filter === 'problems' ? 'Nothing to fix' : 'Nothing here'}
              description={
                filter === 'problems'
                  ? 'This resume passed every rule in the rubric. That is rare.'
                  : 'Switch the filter to see the other findings.'
              }
            />
          ) : (
            <ul className="divide-y divide-ink-100">
              {findings.map((finding: any) => (
                <FindingRow key={finding.id} finding={finding} onEvidence={onEvidence} />
              ))}
            </ul>
          )}
        </Card>
      </div>
    </div>
  )
}

function FindingRow({ finding, onEvidence }: any) {
  const [open, setOpen] = useState(finding.severity === 'CRITICAL')
  const tone = SEVERITY_TONE[finding.severity] ?? 'slate'

  return (
    <li className="px-5 py-4">
      <button
        type="button"
        onClick={() => setOpen((value) => !value)}
        className="flex w-full items-start gap-3 text-left"
        aria-expanded={open}
      >
        <Badge tone={tone} className="mt-0.5 shrink-0">
          {SEVERITY_LABEL[finding.severity] ?? finding.severity}
        </Badge>
        <span className="min-w-0 flex-1">
          <span className="block text-sm font-medium text-ink-900">{finding.title}</span>
          <span className="mt-0.5 block text-xs text-ink-500">
            {finding.categoryLabel}
            {finding.pointsLost > 0 && ` · −${finding.pointsLost} in this category`}
          </span>
        </span>
        <svg
          viewBox="0 0 20 20"
          className={cx('mt-1 h-4 w-4 shrink-0 text-ink-400 transition', open && 'rotate-180')}
          fill="currentColor" aria-hidden="true"
        >
          <path d="M5.22 7.22a.75.75 0 0 1 1.06 0L10 10.94l3.72-3.72a.75.75 0 1 1 1.06 1.06l-4.25 4.25a.75.75 0 0 1-1.06 0L5.22 8.28a.75.75 0 0 1 0-1.06Z" />
        </svg>
      </button>

      {open && (
        <div className="mt-3 space-y-3 pl-0 sm:pl-[4.5rem]">
          <p className="text-sm leading-6 text-ink-700">{finding.detail}</p>

          {finding.evidence && (
            <figure className="rounded-lg border border-ink-200 bg-ink-50 p-3">
              <figcaption className="mb-1.5 flex items-center justify-between gap-2 text-xs font-medium text-ink-500">
                <span>
                  From your resume
                  {finding.lineStart != null && ` · line ${finding.lineStart + 1}`}
                </span>
                {finding.lineStart != null && (
                  <button
                    type="button"
                    onClick={() => onEvidence(finding.lineStart)}
                    className="rounded font-medium text-brand-700 hover:text-brand-800"
                  >
                    Show in context
                  </button>
                )}
              </figcaption>
              <blockquote className="parse-line text-ink-800">{finding.evidence}</blockquote>
            </figure>
          )}

          {finding.recommendation && (
            <div className="rounded-lg border border-brand-200 bg-brand-50 p-3">
              <p className="text-xs font-semibold uppercase tracking-wide text-brand-700">Fix</p>
              <p className="mt-1 text-sm leading-6 text-ink-800">{finding.recommendation}</p>
            </div>
          )}
        </div>
      )}
    </li>
  )
}

function ParseTab({ structured, highlight, lineRefs, onClear }: any) {
  const lines: string[] = structured?.lines ?? []
  const sections = useMemo(() => structured?.sections ?? [], [structured])

  // Map each line to its section once, rather than searching the section list
  // for every one of several hundred lines during render.
  const sectionByLine = useMemo(() => {
    const map = new Map<number, any>()
    sections.forEach((section: any) => {
      for (let i = section.startLine; i <= section.endLine; i += 1) map.set(i, section)
      if (section.headingLine >= 0) map.set(section.headingLine, section)
    })
    return map
  }, [sections])

  if (!structured) {
    return (
      <Card>
        <EmptyState title="No parse available" description="This resume has not been read successfully." />
      </Card>
    )
  }

  return (
    <div className="grid gap-6 lg:grid-cols-3">
      <div className="space-y-6 lg:col-span-1">
        <Card>
          <CardHeader title="Extraction" description={`Read with ${structured.parser}`} />
          <dl className="divide-y divide-ink-100 text-sm">
            <Row label="Pages" value={structured.pageCount ?? '—'} />
            <Row label="Words" value={structured.wordCount?.toLocaleString() ?? '—'} />
            <Row label="Characters" value={structured.charCount?.toLocaleString() ?? '—'} />
          </dl>
        </Card>

        <Card>
          <CardHeader title="Sections found" description="What a parser recognised, and how confident it is." />
          {sections.length === 0 ? (
            <EmptyState
              title="No sections recognised"
              description="Without recognisable headings, a screener cannot tell your experience from your education."
            />
          ) : (
            <ul className="divide-y divide-ink-100">
              {sections.map((section: any) => (
                <li key={`${section.type}-${section.startLine}`} className="px-5 py-3">
                  <div className="flex items-center justify-between gap-3">
                    <span className="text-sm font-medium text-ink-900">{section.displayName}</span>
                    <Badge tone={section.confidence >= 70 ? 'emerald' : 'amber'}>
                      {section.confidence}% sure
                    </Badge>
                  </div>
                  <p className="mt-0.5 text-xs text-ink-500">
                    {section.headingText ? `“${section.headingText}”` : 'Inferred from position'} · lines{' '}
                    {section.startLine + 1}–{section.endLine + 1}
                  </p>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>

      <div className="lg:col-span-2">
        <Card>
          <CardHeader
            title="The text a screener reads"
            description="Line by line, exactly as extracted. If something is missing here, it is invisible to the machine."
            action={
              highlight != null && (
                <Button variant="ghost" size="sm" onClick={onClear}>Clear highlight</Button>
              )
            }
          />
          <div className="max-h-[70vh] overflow-y-auto">
            {lines.length === 0 ? (
              <EmptyState title="No text extracted" description="Nothing readable came out of this file." />
            ) : (
              <ol className="divide-y divide-ink-50">
                {lines.map((line, index) => {
                  const section = sectionByLine.get(index)
                  const isHeading = section?.headingLine === index
                  return (
                    <li
                      key={index}
                      ref={(element) => {
                        lineRefs.current[index] = element
                      }}
                      className={cx(
                        'flex gap-3 px-4 py-1 transition-colors',
                        highlight === index && 'bg-amber-100',
                        isHeading && 'bg-brand-50/60',
                      )}
                    >
                      <span className="w-10 shrink-0 select-none pt-0.5 text-right font-mono text-[11px] text-ink-300">
                        {index + 1}
                      </span>
                      <span
                        className={cx(
                          'parse-line min-w-0 flex-1',
                          isHeading ? 'font-semibold text-brand-800' : 'text-ink-800',
                          !line.trim() && 'text-ink-300',
                        )}
                      >
                        {line || '·'}
                      </span>
                    </li>
                  )
                })}
              </ol>
            )}
          </div>
        </Card>
      </div>
    </div>
  )
}

function EntitiesTab({ structured, onEvidence }: any) {
  if (!structured) {
    return (
      <Card>
        <EmptyState title="Nothing extracted" description="This resume has not been read successfully." />
      </Card>
    )
  }

  const { contact, skills = [], education = [], experience = [] } = structured

  return (
    <div className="grid gap-6 lg:grid-cols-2">
      <Card>
        <CardHeader title="Contact" description="What a system would populate its candidate record with." />
        {!contact ? (
          <EmptyState
            title="No contact details found"
            description="If your name, email, or phone are in a header, a footer, or an image, most parsers will not see them either."
          />
        ) : (
          <dl className="divide-y divide-ink-100 text-sm">
            <Row label="Name" value={contact.fullName ?? <Missing />} />
            <Row label="Email" value={contact.email ?? <Missing />} />
            <Row label="Phone" value={contact.phone ?? <Missing />} />
            <Row label="Location" value={contact.location ?? <Missing />} />
            <Row label="LinkedIn" value={contact.linkedinUrl ?? <Missing />} />
            <Row label="GitHub" value={contact.githubUrl ?? <Missing />} />
          </dl>
        )}
      </Card>

      <Card>
        <CardHeader title="Skills" description={`${skills.length} recognised`} />
        {skills.length === 0 ? (
          <EmptyState
            title="No skills recognised"
            description="Keyword matching is the first filter most systems apply. A resume with no matchable vocabulary fails it whatever the candidate can do."
          />
        ) : (
          <div className="flex flex-wrap gap-2 p-5">
            {skills.map((skill: any) => (
              <button
                key={skill.id}
                type="button"
                onClick={() => onEvidence(skill.lineStart)}
                title={`${SKILL_CATEGORY_LABEL[skill.category] ?? skill.category} · ${skill.confidence}% confident`}
                className="rounded-full border border-ink-200 bg-white px-3 py-1 text-sm text-ink-800 transition hover:border-brand-300 hover:bg-brand-50"
              >
                {skill.name}
              </button>
            ))}
          </div>
        )}
      </Card>

      <Card>
        <CardHeader title="Experience" description={`${experience.length} entries`} />
        {experience.length === 0 ? (
          <EmptyState
            title="No experience entries parsed"
            description="Systems that index candidates by employer and title would store nothing for you here."
          />
        ) : (
          <ul className="divide-y divide-ink-100">
            {experience.map((entry: any) => (
              <li key={entry.id} className="px-5 py-4">
                <p className="text-sm font-medium text-ink-900">
                  {entry.jobTitle ?? <Missing label="No title read" />}
                </p>
                <p className="mt-0.5 text-sm text-ink-600">
                  {entry.company ?? <Missing label="No company read" />}
                </p>
                <p className="mt-1 text-xs text-ink-500">
                  {entry.startDate || entry.endDate || entry.isCurrent
                    ? `${formatDate(entry.startDate, { month: 'short', year: 'numeric' })} — ${
                        entry.isCurrent ? 'present' : formatDate(entry.endDate, { month: 'short', year: 'numeric' })
                      }`
                    : 'No dates read'}
                </p>
                {entry.lineStart != null && (
                  <button
                    type="button"
                    onClick={() => onEvidence(entry.lineStart)}
                    className="mt-2 rounded text-xs font-medium text-brand-700 hover:text-brand-800"
                  >
                    Show in the text
                  </button>
                )}
              </li>
            ))}
          </ul>
        )}
      </Card>

      <Card>
        <CardHeader title="Education" description={`${education.length} entries`} />
        {education.length === 0 ? (
          <EmptyState
            title="No education entries parsed"
            description="For early-career applications this is often the field a filter is set on."
          />
        ) : (
          <ul className="divide-y divide-ink-100">
            {education.map((entry: any) => (
              <li key={entry.id} className="px-5 py-4">
                <p className="text-sm font-medium text-ink-900">
                  {entry.institution ?? <Missing label="No institution read" />}
                </p>
                <p className="mt-0.5 text-sm text-ink-600">
                  {[entry.degree, entry.fieldOfStudy].filter(Boolean).join(' · ') || (
                    <Missing label="No degree read" />
                  )}
                </p>
                <p className="mt-1 text-xs text-ink-500">
                  {entry.endDate ? formatDate(entry.endDate, { month: 'short', year: 'numeric' }) : 'No dates read'}
                  {entry.grade && ` · ${entry.grade}`}
                </p>
                {entry.lineStart != null && (
                  <button
                    type="button"
                    onClick={() => onEvidence(entry.lineStart)}
                    className="mt-2 rounded text-xs font-medium text-brand-700 hover:text-brand-800"
                  >
                    Show in the text
                  </button>
                )}
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  )
}

function HistoryTab({ history }: any) {
  if (!history || history.points.length === 0) {
    return (
      <Card>
        <EmptyState
          title="No history yet"
          description="Run the analysis more than once — after editing your resume — and the movement shows up here."
        />
      </Card>
    )
  }

  return (
    <Card>
      <CardHeader
        title="Score over time"
        description={
          history.delta === 0
            ? 'No change between your first and latest run.'
            : `${history.delta > 0 ? '+' : ''}${history.delta} points since your first analysis.`
        }
      />
      <div className="px-5 py-6">
        <div className="flex items-end gap-3 overflow-x-auto pb-2">
          {history.points.map((point: any) => (
            <div key={point.analysisId} className="flex min-w-[3.5rem] flex-col items-center gap-2">
              <span className="text-xs font-semibold tabular-nums text-ink-700">{point.overallScore}</span>
              <div
                className="w-8 rounded-t transition-all"
                style={{
                  height: `${(point.overallScore / 100) * 160 + 4}px`,
                  backgroundColor: BAR_COLOURS[scoreTone(point.overallScore)],
                }}
              />
              <span className="text-[10px] text-ink-400">
                {formatDate(point.createdAt, { day: 'numeric', month: 'short' })}
              </span>
            </div>
          ))}
        </div>
      </div>
    </Card>
  )
}

function Row({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex items-start justify-between gap-4 px-5 py-3">
      <dt className="shrink-0 text-ink-500">{label}</dt>
      <dd className="min-w-0 break-words text-right font-medium text-ink-900">{value}</dd>
    </div>
  )
}

function Missing({ label = 'Not found' }: { label?: string }) {
  return <span className="font-normal italic text-red-600">{label}</span>
}
