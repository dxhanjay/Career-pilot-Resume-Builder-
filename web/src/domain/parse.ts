/**
 * Getting text out of a resume file.
 *
 * The single most consequential step in the product. Everything downstream —
 * sections, skills, the ATS score, the match — is computed from whatever comes
 * out of here, and the most common real-world failure is a document that looks
 * perfect to a person and yields nothing to a parser.
 */

export interface ParseWarning {
  readonly code: string
  readonly message: string
}

export interface ExtractedText {
  readonly rawText: string
  readonly pageCount: number
  readonly parser: string
  readonly warnings: ParseWarning[]
}

/** Below this, a document with pages almost certainly has no text layer. */
const NO_TEXT_LAYER_THRESHOLD = 20
/** Below this, a resume is suspiciously thin — usually image-based. */
const SPARSE_TEXT_THRESHOLD = 120
/** Above this, unusually long for an early-career candidate. */
const LONG_DOCUMENT_PAGES = 4

export const PDF_MIME = 'application/pdf'
export const DOCX_MIME =
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document'

/**
 * Identifies a file from its own bytes, never its extension or Content-Type.
 *
 * Both of those are supplied by the client and neither is evidence. Renaming
 * `payload.html` to `resume.pdf` is a two-second attack, and a parser handed
 * something it did not expect is the least pleasant place to discover that.
 */
export function detectFileType(bytes: Uint8Array): string | null {
  if (bytes.length < 4) return null

  // %PDF
  if (bytes[0] === 0x25 && bytes[1] === 0x50 && bytes[2] === 0x44 && bytes[3] === 0x46) {
    return PDF_MIME
  }

  // PK\x03\x04 — a ZIP container. .docx is a ZIP; so are .xlsx, .pptx and .jar,
  // so this is necessary but not sufficient and the caller checks further.
  if (bytes[0] === 0x50 && bytes[1] === 0x4b && bytes[2] === 0x03 && bytes[3] === 0x04) {
    return DOCX_MIME
  }

  return null
}

export function sha256Hex(bytes: Uint8Array): Promise<string> {
  return crypto.subtle
    .digest('SHA-256', bytes as unknown as ArrayBuffer)
    .then((digest) =>
      Array.from(new Uint8Array(digest))
        .map((byte) => byte.toString(16).padStart(2, '0'))
        .join(''),
    )
}

/**
 * Extracts text from a PDF or DOCX.
 *
 * Never throws for a document it simply could not read: an unreadable resume is
 * a finding to report, not an error to swallow. It throws only when the file is
 * not a type we accept at all.
 */
export async function extractText(
  bytes: Uint8Array,
  mimeType: string,
): Promise<ExtractedText> {
  if (mimeType === PDF_MIME) return extractPdf(bytes)
  if (mimeType === DOCX_MIME) return extractDocx(bytes)
  throw new Error(`No extractor is configured for ${mimeType}`)
}

async function extractPdf(bytes: Uint8Array): Promise<ExtractedText> {
  // Imported lazily and only on the server. unpdf pulls in a pdf.js build that
  // has no business in a client bundle.
  const { extractText: unpdfExtract, getDocumentProxy } = await import('unpdf')

  const warnings: ParseWarning[] = []

  try {
    const document = await getDocumentProxy(new Uint8Array(bytes))
    const result = await unpdfExtract(document, { mergePages: false })

    const pages: string[] = Array.isArray(result.text) ? result.text : [String(result.text)]
    const pageCount = result.totalPages ?? pages.length

    // Page-joined with blank lines between, so the section segmenter sees a
    // page break the way it sees any other paragraph break.
    const rawText = pages.join('\n\n')

    return {
      rawText,
      pageCount,
      parser: 'UNPDF',
      warnings: qualityWarnings(rawText, pageCount, warnings),
    }
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error)

    if (/password|encrypt/i.test(message)) {
      return {
        rawText: '',
        pageCount: 0,
        parser: 'UNPDF',
        warnings: [
          {
            code: 'ENCRYPTED_DOCUMENT',
            message:
              'This document is password-protected or restricted. Some content may be unreadable, and many screening systems reject protected files outright.',
          },
        ],
      }
    }

    throw new Error(`The PDF could not be read: ${message}`)
  }
}

async function extractDocx(bytes: Uint8Array): Promise<ExtractedText> {
  const mammoth = await import('mammoth')

  const result = await mammoth.extractRawText({
    buffer: Buffer.from(bytes),
  })

  const rawText = result.value ?? ''

  // Word documents carry no page count until they are laid out, which requires
  // a rendering engine. Estimated from length instead, and only used for the
  // "too long" heuristic where an estimate is good enough.
  const pageCount = Math.max(1, Math.ceil(countWords(rawText) / 500))

  return {
    rawText,
    pageCount,
    parser: 'MAMMOTH',
    warnings: qualityWarnings(rawText, pageCount, []),
  }
}

/**
 * Structural problems worth telling the user about.
 *
 * These are the difference between "your resume scored 42" and "your resume
 * scored 42 because it is a two-column layout and the parser read your sidebar
 * through the middle of your work history".
 */
function qualityWarnings(
  rawText: string,
  pageCount: number,
  existing: ParseWarning[],
): ParseWarning[] {
  const warnings = [...existing]
  const words = countWords(rawText)

  if (pageCount > 0 && words < NO_TEXT_LAYER_THRESHOLD) {
    warnings.push({
      code: 'NO_TEXT_LAYER',
      message:
        'We could not find any selectable text. This is usually a scanned image or a photo saved as a PDF. Most screening software cannot read these at all — export a text-based PDF from your word processor instead.',
    })
    return warnings
  }

  if (words < SPARSE_TEXT_THRESHOLD) {
    warnings.push({
      code: 'SPARSE_TEXT',
      message: `We only found ${words} words. If your resume relies on images, graphics, or text boxes, screening software may not see that content.`,
    })
  }

  if (pageCount >= LONG_DOCUMENT_PAGES) {
    warnings.push({
      code: 'UNUSUALLY_LONG',
      message: `This resume is ${pageCount} pages. For most early-career roles, one or two pages is expected.`,
    })
  }

  if (looksMultiColumn(rawText)) {
    warnings.push({
      code: 'MULTI_COLUMN_LAYOUT',
      message:
        'This looks like a multi-column layout. Applicant tracking systems often read columns as one interleaved stream, which can scramble your experience. A single-column layout is safer.',
    })
  }

  return warnings
}

/**
 * Detects a two-column layout from the shape of the extracted text.
 *
 * When a parser reads a two-column page it produces many short lines: each
 * physical row yields a fragment of the left column and a fragment of the
 * right, rather than one full-width sentence. A high proportion of very short
 * non-empty lines is the signature.
 *
 * Heuristic, and it will occasionally be wrong on a heavily bulleted
 * single-column resume — which is why the finding it produces says "looks like"
 * and shows the user the extracted text so they can judge for themselves.
 */
function looksMultiColumn(rawText: string): boolean {
  const lines = rawText
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)

  if (lines.length < 25) return false

  const short = lines.filter((line) => line.length > 0 && line.length < 25).length
  return short / lines.length > 0.55
}

export function countWords(text: string): number {
  const trimmed = (text ?? '').trim()
  return trimmed ? trimmed.split(/\s+/).length : 0
}

export const isUsable = (extracted: ExtractedText): boolean =>
  countWords(extracted.rawText) >= NO_TEXT_LAYER_THRESHOLD
