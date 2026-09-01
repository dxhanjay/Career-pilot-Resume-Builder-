import { NextResponse } from 'next/server'
import { and, desc, eq } from 'drizzle-orm'
import { db } from '@/db'
import { resumes } from '@/db/schema'
import { conflict, handler, ok, requireUser, tooLarge, unsupportedType } from '@/lib/api'
import { runAtsQuietly } from '@/lib/analysis-service'
import { parseResume } from '@/lib/parsing-service'
import { DOCX_MIME, PDF_MIME, detectFileType, sha256Hex } from '@/domain/parse'

export const runtime = 'nodejs'
// Parsing a large PDF is CPU-bound and can take a few seconds. The default
// serverless timeout would cut it off part-way and leave a resume stuck in
// PARSING with no explanation.
export const maxDuration = 60

const MAX_BYTES = 5 * 1024 * 1024
const MAX_RESUMES_PER_USER = 10

export const GET = handler(async (): Promise<NextResponse> => {
  const user = await requireUser()

  // The file bytes are deliberately excluded: a list of ten resumes carrying
  // 50 MB of PDF is a slow list for no benefit.
  const rows = await db
    .select({
      id: resumes.id,
      originalFilename: resumes.originalFilename,
      mimeType: resumes.mimeType,
      sizeBytes: resumes.sizeBytes,
      status: resumes.status,
      isPrimary: resumes.isPrimary,
      createdAt: resumes.createdAt,
    })
    .from(resumes)
    .where(eq(resumes.userId, user.id))
    .orderBy(desc(resumes.createdAt))

  return ok(rows)
})

export const POST = handler(async (request: Request): Promise<NextResponse> => {
  const user = await requireUser()

  const form = await request.formData()
  const file = form.get('file')

  if (!(file instanceof File) || file.size === 0) {
    throw conflict('Choose a file to upload.')
  }
  if (file.size > MAX_BYTES) {
    throw tooLarge(
      `That file is ${(file.size / 1024 / 1024).toFixed(1)} MB. The limit is 5 MB.`,
    )
  }

  const existing = await db
    .select({ id: resumes.id })
    .from(resumes)
    .where(eq(resumes.userId, user.id))

  if (existing.length >= MAX_RESUMES_PER_USER) {
    throw conflict(
      `You have reached the limit of ${MAX_RESUMES_PER_USER} resumes. Delete one to upload another.`,
    )
  }

  const bytes = new Uint8Array(await file.arrayBuffer())

  // The type is decided by the file's own bytes, never its extension or the
  // Content-Type header — both are supplied by the client and neither is
  // evidence. Renaming payload.html to resume.pdf is a two-second attack.
  const mimeType = detectFileType(bytes)
  if (mimeType !== PDF_MIME && mimeType !== DOCX_MIME) {
    throw unsupportedType(
      'Upload a PDF or a Word (.docx) file. Those are the formats screening software reads.',
    )
  }

  const checksum = await sha256Hex(bytes)
  const duplicate = await db
    .select({ id: resumes.id })
    .from(resumes)
    .where(and(eq(resumes.userId, user.id), eq(resumes.checksumSha256, checksum)))
    .limit(1)

  if (duplicate.length) {
    throw conflict('You have already uploaded this exact file.')
  }

  const [resume] = await db
    .insert(resumes)
    .values({
      userId: user.id,
      originalFilename: (file.name || 'resume').slice(0, 255),
      mimeType,
      sizeBytes: bytes.length,
      checksumSha256: checksum,
      content: Buffer.from(bytes),
      // The first upload becomes primary automatically; later ones do not
      // silently displace it.
      isPrimary: existing.length === 0,
    })
    .returning({
      id: resumes.id,
      originalFilename: resumes.originalFilename,
      status: resumes.status,
      isPrimary: resumes.isPrimary,
    })

  // Parsed inline rather than queued. It takes well under a second, and the
  // alternative on serverless is a job table plus somewhere to run a worker.
  const parse = await parseResume(resume.id, user.id)

  // Scored immediately too, so a report is waiting the moment the user opens
  // the resume. A failure here is logged and swallowed: the extracted text is
  // valuable on its own.
  if (parse.status === 'SUCCEEDED') {
    await runAtsQuietly(resume.id, user.id)
  }

  return ok({ ...resume, parseStatus: parse.status, warnings: parse.warnings }, 201)
})
