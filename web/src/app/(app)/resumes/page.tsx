'use client'

import Link from 'next/link'
import { useCallback, useEffect, useRef, useState } from 'react'
import {
  Alert, Badge, Button, Card, CardHeader, EmptyState, Loading, PageHeader, cx,
} from '@/components/ui'
import { api, errorMessage } from '@/lib/client'
import { RESUME_STATUS, formatBytes, relativeTime } from '@/lib/format'

const MAX_BYTES = 5 * 1024 * 1024

interface ResumeRow {
  id: string
  originalFilename: string
  mimeType: string
  sizeBytes: number
  status: string
  isPrimary: boolean
  createdAt: string
}

export default function ResumesPage() {
  const [resumes, setResumes] = useState<ResumeRow[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [uploading, setUploading] = useState(false)
  const [uploadError, setUploadError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [dragging, setDragging] = useState(false)
  const [busyId, setBusyId] = useState<string | null>(null)
  const fileInput = useRef<HTMLInputElement>(null)

  const load = useCallback(async () => {
    setError(null)
    try {
      setResumes(await api.get<ResumeRow[]>('/resumes'))
    } catch (err) {
      setError(errorMessage(err, 'Could not load your resumes.'))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  function validate(file: File | undefined | null): string | null {
    if (!file) return 'Choose a file to upload.'
    if (file.size === 0) return 'That file is empty.'
    if (file.size > MAX_BYTES) return `That file is ${formatBytes(file.size)}. The limit is 5 MB.`
    if (!/\.(pdf|docx)$/i.test(file.name)) {
      return 'Upload a PDF or a Word (.docx) file. Those are the formats screening software reads.'
    }
    return null
  }

  async function upload(file: File | undefined | null) {
    const problem = validate(file)
    if (problem) {
      setUploadError(problem)
      return
    }

    setUploadError(null)
    setNotice(null)
    setUploading(true)

    const body = new FormData()
    body.append('file', file!)

    try {
      // Parsing and scoring both happen inside this request, so there is
      // nothing to poll for afterwards — the resume is ready when it returns.
      const created = await api.post<{ originalFilename: string; parseStatus: string }>(
        '/resumes',
        body,
      )
      setNotice(
        created.parseStatus === 'SUCCEEDED'
          ? `Read ${created.originalFilename} and scored it. Open it to see what the machine saw.`
          : `Uploaded ${created.originalFilename}, but we could not read any text from it. Open it to find out why.`,
      )
      await load()
    } catch (err) {
      setUploadError(errorMessage(err, 'Could not upload that file.'))
    } finally {
      setUploading(false)
      if (fileInput.current) fileInput.current.value = ''
    }
  }

  async function makePrimary(id: string) {
    setBusyId(id)
    try {
      await api.patch(`/resumes/${id}`)
      await load()
    } catch (err) {
      setError(errorMessage(err, 'Could not set that resume as primary.'))
    } finally {
      setBusyId(null)
    }
  }

  async function remove(id: string, name: string) {
    if (!window.confirm(`Delete ${name}? Any scores and matches for it go too.`)) return
    setBusyId(id)
    try {
      await api.delete(`/resumes/${id}`)
      setNotice(`Deleted ${name}.`)
      await load()
    } catch (err) {
      setError(errorMessage(err, 'Could not delete that resume.'))
    } finally {
      setBusyId(null)
    }
  }

  return (
    <>
      <PageHeader
        title="Resumes"
        description="Upload a PDF or Word document. We read it exactly as screening software would."
      />

      <Card className="mb-6">
        <CardHeader
          title="Upload a resume"
          description="PDF or .docx, up to 5 MB. Export from your word processor rather than scanning."
        />
        <div className="p-5">
          <div
            onDragOver={(event) => {
              event.preventDefault()
              setDragging(true)
            }}
            onDragLeave={() => setDragging(false)}
            onDrop={(event) => {
              event.preventDefault()
              setDragging(false)
              upload(event.dataTransfer.files?.[0])
            }}
            className={cx(
              'rounded-xl border-2 border-dashed px-6 py-10 text-center transition',
              dragging ? 'border-brand-400 bg-brand-50' : 'border-ink-200 bg-ink-50/50',
            )}
          >
            <svg
              viewBox="0 0 24 24"
              className="mx-auto h-10 w-10 text-ink-300"
              fill="none" stroke="currentColor" strokeWidth="1.5" aria-hidden="true"
            >
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 16V4m0 0L8 8m4-4 4 4M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2" />
            </svg>
            <p className="mt-3 text-sm font-medium text-ink-800">Drag a file here, or choose one</p>
            <p className="mt-1 text-xs text-ink-500">PDF or .docx · 5 MB maximum</p>

            <input
              ref={fileInput}
              type="file"
              accept=".pdf,.docx"
              className="sr-only"
              id="resume-file"
              onChange={(event) => upload(event.target.files?.[0])}
            />
            <Button
              type="button"
              className="mt-5"
              loading={uploading}
              onClick={() => fileInput.current?.click()}
            >
              {uploading ? 'Reading your resume…' : 'Choose a file'}
            </Button>
          </div>

          {uploadError && (
            <Alert tone="red" title="Upload failed" className="mt-4">
              {uploadError}
            </Alert>
          )}
          {notice && (
            <Alert tone="emerald" title="Done" className="mt-4">
              {notice}
            </Alert>
          )}
        </div>
      </Card>

      {error && (
        <Alert tone="red" title="Something went wrong" className="mb-6" action={<Button onClick={load}>Retry</Button>}>
          {error}
        </Alert>
      )}

      <Card>
        <CardHeader title="Your resumes" description={`${resumes.length} uploaded`} />
        {loading ? (
          <Loading label="Loading your resumes…" />
        ) : resumes.length === 0 ? (
          <EmptyState
            title="Nothing uploaded yet"
            description="Once you upload a resume it appears here, already parsed and scored."
          />
        ) : (
          <ul className="divide-y divide-ink-100">
            {resumes.map((resume) => {
              const status = RESUME_STATUS[resume.status] ?? { label: resume.status, tone: 'slate' }
              return (
                <li
                  key={resume.id}
                  className="flex flex-col gap-3 px-5 py-4 sm:flex-row sm:items-center sm:justify-between"
                >
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <Link
                        href={`/resumes/${resume.id}`}
                        className="truncate rounded text-sm font-medium text-ink-900 hover:text-brand-700"
                      >
                        {resume.originalFilename}
                      </Link>
                      {resume.isPrimary && <Badge tone="brand">Primary</Badge>}
                      <Badge tone={status.tone}>{status.label}</Badge>
                    </div>
                    <p className="mt-1 text-xs text-ink-500">
                      {formatBytes(resume.sizeBytes)} · uploaded {relativeTime(resume.createdAt)}
                    </p>
                  </div>

                  <div className="flex shrink-0 flex-wrap items-center gap-2">
                    <Button as={Link} href={`/resumes/${resume.id}`} variant="secondary" size="sm">
                      Open
                    </Button>
                    {!resume.isPrimary && (
                      <Button
                        variant="ghost"
                        size="sm"
                        loading={busyId === resume.id}
                        onClick={() => makePrimary(resume.id)}
                      >
                        Make primary
                      </Button>
                    )}
                    <Button
                      variant="ghost"
                      size="sm"
                      className="text-red-600 hover:bg-red-50 hover:text-red-700"
                      loading={busyId === resume.id}
                      onClick={() => remove(resume.id, resume.originalFilename)}
                    >
                      Delete
                    </Button>
                  </div>
                </li>
              )
            })}
          </ul>
        )}
      </Card>
    </>
  )
}
