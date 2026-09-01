import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  Alert,
  Badge,
  Button,
  Card,
  CardHeader,
  Loading,
  PageHeader,
  ScoreBar,
  ScoreRing,
  TextArea,
  cx,
} from '../components/ui'
import { api, errorMessage, unwrap } from '../lib/api'
import { scoreTone } from '../lib/format'

/** Under this, the rubric refuses to judge — so warn before the submit, not after. */
const MIN_WORDS = 25

export default function InterviewRoom() {
  const { id } = useParams()

  const [session, setSession] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [actionError, setActionError] = useState(null)
  const [current, setCurrent] = useState(0)
  const [draft, setDraft] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [finishing, setFinishing] = useState(false)

  const load = useCallback(async () => {
    setError(null)
    try {
      const data = unwrap(await api.get(`/interviews/${id}`))
      setSession(data)
      // Open on the first unanswered question, which is where the user left off.
      const firstUnanswered = data.questions.findIndex((question) => !question.answer)
      setCurrent(firstUnanswered === -1 ? 0 : firstUnanswered)
    } catch (err) {
      setError(errorMessage(err, 'Could not load this interview.'))
    } finally {
      setLoading(false)
    }
  }, [id])

  useEffect(() => {
    load()
  }, [load])

  const question = session?.questions?.[current]

  // Load the stored answer into the box when moving between questions, so a
  // previous answer can be edited rather than retyped.
  useEffect(() => {
    setDraft(question?.answer?.answerText ?? '')
  }, [question?.id, question?.answer?.answerText])

  const wordCount = useMemo(() => (draft.trim() ? draft.trim().split(/\s+/).length : 0), [draft])

  async function submitAnswer() {
    if (!question) return
    setSubmitting(true)
    setActionError(null)
    try {
      await api.post(`/interviews/${id}/questions/${question.id}/answer`, { answer: draft })
      await load()
      setCurrent(current)
    } catch (err) {
      setActionError(errorMessage(err, 'Could not save that answer.'))
    } finally {
      setSubmitting(false)
    }
  }

  async function finish() {
    setFinishing(true)
    setActionError(null)
    try {
      setSession(unwrap(await api.post(`/interviews/${id}/complete`)))
    } catch (err) {
      setActionError(errorMessage(err, 'Could not finish the interview.'))
    } finally {
      setFinishing(false)
    }
  }

  if (loading) return <Loading label="Loading your interview…" />

  if (error) {
    return (
      <Alert tone="red" title="Could not load this interview" action={<Button onClick={load}>Try again</Button>}>
        {error}
      </Alert>
    )
  }

  const isOpen = session.status === 'IN_PROGRESS'
  const answered = session.answeredCount
  const progress = Math.round((answered / session.questionCount) * 100)

  return (
    <>
      <PageHeader
        breadcrumb={
          <Link to="/interviews" className="mb-2 inline-flex items-center gap-1 rounded text-sm text-ink-500 hover:text-ink-800">
            <svg viewBox="0 0 20 20" className="h-4 w-4" fill="currentColor" aria-hidden="true">
              <path d="M12.72 4.22a.75.75 0 0 1 0 1.06L8.06 10l4.66 4.72a.75.75 0 1 1-1.06 1.06l-5.25-5.25a.75.75 0 0 1 0-1.06l5.25-5.25a.75.75 0 0 1 1.06 0Z" />
            </svg>
            All interviews
          </Link>
        }
        title={session.focusLabel}
        description={session.focusDescription}
        action={
          isOpen && (
            <Button loading={finishing} disabled={answered === 0} onClick={finish}>
              Finish and see the report
            </Button>
          )
        }
      />

      {actionError && (
        <Alert tone="red" title="That did not work" className="mb-6">
          {actionError}
        </Alert>
      )}

      {!isOpen && session.report && <Report report={session.report} />}

      <div className="grid gap-6 lg:grid-cols-4">
        <Card className="lg:col-span-1">
          <CardHeader title="Questions" description={`${answered} of ${session.questionCount} answered`} />
          <div className="px-5 pt-1">
            <div
              className="h-1.5 w-full overflow-hidden rounded-full bg-ink-100"
              role="progressbar"
              aria-valuenow={progress}
              aria-valuemin={0}
              aria-valuemax={100}
              aria-label="Interview progress"
            >
              <div className="h-full rounded-full bg-brand-500 transition-[width]" style={{ width: `${progress}%` }} />
            </div>
          </div>
          <ul className="mt-2 divide-y divide-ink-50">
            {session.questions.map((item, index) => (
              <li key={item.id}>
                <button
                  type="button"
                  onClick={() => setCurrent(index)}
                  className={cx(
                    'flex w-full items-center gap-3 px-5 py-3 text-left transition',
                    index === current ? 'bg-brand-50' : 'hover:bg-ink-50',
                  )}
                  aria-current={index === current}
                >
                  <span
                    className={cx(
                      'flex h-6 w-6 shrink-0 items-center justify-center rounded-full text-xs font-semibold',
                      item.answer ? 'bg-emerald-100 text-emerald-700' : 'bg-ink-100 text-ink-500',
                    )}
                  >
                    {item.answer ? '✓' : index + 1}
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm text-ink-800">{item.kindLabel}</span>
                    {item.answer && (
                      <span className="text-xs text-ink-500">Scored {item.answer.score}</span>
                    )}
                  </span>
                </button>
              </li>
            ))}
          </ul>
        </Card>

        <div className="lg:col-span-3">
          {question && (
            <Card>
              <CardHeader
                title={`Question ${current + 1} of ${session.questionCount}`}
                description={question.kindLabel}
                action={question.focusSkill && <Badge tone="brand">{question.focusSkill}</Badge>}
              />

              <div className="space-y-5 p-5">
                <p className="text-lg font-medium leading-relaxed text-ink-900">{question.prompt}</p>

                {question.rationale && (
                  <div className="rounded-lg border border-ink-200 bg-ink-50 p-4">
                    <p className="text-xs font-semibold uppercase tracking-wide text-ink-500">
                      Why you are being asked this
                    </p>
                    <p className="mt-1 text-sm leading-6 text-ink-700">{question.rationale}</p>
                  </div>
                )}

                <div>
                  <label className="label" htmlFor="answer">
                    Your answer
                  </label>
                  <TextArea
                    id="answer"
                    rows={9}
                    placeholder="Say what the situation was, what you personally did, and what happened as a result."
                    value={draft}
                    disabled={!isOpen}
                    onChange={(event) => setDraft(event.target.value)}
                  />
                  <p className="mt-1.5 text-xs text-ink-500">
                    {wordCount} words
                    {wordCount > 0 && wordCount < MIN_WORDS && ' — too short to judge. Aim for 120 to 200.'}
                    {wordCount >= MIN_WORDS && wordCount < 70 && ' — under 45 seconds of speech.'}
                    {wordCount > 320 && ' — over three minutes. Interviewers stop listening.'}
                  </p>
                </div>

                {isOpen && (
                  <div className="flex flex-wrap gap-2">
                    <Button loading={submitting} disabled={wordCount === 0} onClick={submitAnswer}>
                      {question.answer ? 'Save and re-score' : 'Submit answer'}
                    </Button>
                    {current > 0 && (
                      <Button variant="secondary" onClick={() => setCurrent(current - 1)}>
                        Previous
                      </Button>
                    )}
                    {current < session.questions.length - 1 && (
                      <Button variant="secondary" onClick={() => setCurrent(current + 1)}>
                        Next question
                      </Button>
                    )}
                  </div>
                )}

                {question.answer && <AnswerFeedback answer={question.answer} expected={question.expectedPoints} />}
              </div>
            </Card>
          )}
        </div>
      </div>
    </>
  )
}

function AnswerFeedback({ answer, expected }) {
  return (
    <div className="space-y-4 rounded-xl border border-ink-200 bg-ink-50/70 p-5">
      <div className="flex flex-wrap items-center gap-4">
        <ScoreRing score={answer.score} tone={scoreTone(answer.score)} size={78} sublabel="/100" />
        <div className="min-w-0 flex-1 space-y-3">
          <ScoreBar label="Structure" score={answer.structureScore} tone={scoreTone(answer.structureScore)} />
          <ScoreBar label="Specificity" score={answer.specificityScore} tone={scoreTone(answer.specificityScore)} />
          <ScoreBar label="Relevance" score={answer.relevanceScore} tone={scoreTone(answer.relevanceScore)} />
          <ScoreBar label="Clarity" score={answer.clarityScore} tone={scoreTone(answer.clarityScore)} />
        </div>
      </div>

      {answer.strengths?.length > 0 && (
        <div>
          <p className="text-xs font-semibold uppercase tracking-wide text-emerald-700">What worked</p>
          <ul className="mt-1.5 space-y-1.5">
            {answer.strengths.map((item) => (
              <li key={item} className="text-sm leading-6 text-ink-700">
                • {item}
              </li>
            ))}
          </ul>
        </div>
      )}

      {answer.improvements?.length > 0 && (
        <div>
          <p className="text-xs font-semibold uppercase tracking-wide text-amber-700">What to change</p>
          <ul className="mt-1.5 space-y-1.5">
            {answer.improvements.map((item) => (
              <li key={item} className="text-sm leading-6 text-ink-700">
                • {item}
              </li>
            ))}
          </ul>
        </div>
      )}

      {expected?.length > 0 && (
        <div>
          <p className="text-xs font-semibold uppercase tracking-wide text-ink-500">
            What a strong answer covers
          </p>
          <ul className="mt-1.5 space-y-1.5">
            {expected.map((item) => (
              <li key={item} className="text-sm leading-6 text-ink-600">
                • {item}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}

function Report({ report }) {
  return (
    <Card className="mb-6">
      <CardHeader title="Interview report" description="Unanswered questions count as zero, so this reflects the interview you actually sat." />
      <div className="grid gap-6 p-5 lg:grid-cols-3">
        <div className="flex flex-col items-center justify-center">
          <ScoreRing score={report.overallScore} tone={scoreTone(report.overallScore)} size={140} sublabel="/ 100" />
          <p className="mt-3 text-lg font-semibold text-ink-900">{report.bandLabel}</p>
          <p className="mt-1 text-center text-sm leading-6 text-ink-600">{report.bandSummary}</p>
        </div>

        <div className="space-y-5 lg:col-span-2">
          <div className="grid gap-5 sm:grid-cols-2">
            {report.axisScores.map((axis) => (
              <ScoreBar
                key={axis.axis}
                label={axis.displayName}
                score={axis.score}
                tone={scoreTone(axis.score)}
                hint={axis.description}
              />
            ))}
          </div>

          {report.topImprovements?.length > 0 && (
            <div>
              <p className="text-xs font-semibold uppercase tracking-wide text-amber-700">
                What kept coming up
              </p>
              <ul className="mt-1.5 space-y-1.5">
                {report.topImprovements.map((item) => (
                  <li key={item} className="text-sm leading-6 text-ink-700">
                    • {item}
                  </li>
                ))}
              </ul>
            </div>
          )}

          {report.topStrengths?.length > 0 && (
            <div>
              <p className="text-xs font-semibold uppercase tracking-wide text-emerald-700">
                What you did consistently well
              </p>
              <ul className="mt-1.5 space-y-1.5">
                {report.topStrengths.map((item) => (
                  <li key={item} className="text-sm leading-6 text-ink-700">
                    • {item}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      </div>
    </Card>
  )
}
