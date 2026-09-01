/**
 * Text primitives shared by every extractor and rule engine.
 *
 * All pure, all deterministic. The whole analysis pipeline is built on these,
 * which is what lets a score be reproduced and argued with rather than taken on
 * trust.
 */

/** Bumped whenever normalisation changes; stored with each parse. */
export const NORMALISATION_VERSION = 1

const LINE_SEPARATORS = /\r\n|\r|\u2028|\u2029/g
const EXOTIC_SPACES = /[\u00A0\u2000-\u200A\u202F\u205F\u3000]/g
const ZERO_WIDTH = /[\u200B-\u200D\uFEFF]/g

const BULLET_START = /^\s*[-\u2013\u2014\*\u2022\u25AA\u25CF\u2023\u00B7]\s+/
const EMAIL_LIKE = /[\w.+-]+@[\w-]+\.[\w.]+/
const URL_LIKE = /(https?:\/\/|www\.|linkedin\.com|github\.com)/i
const PHONE_LIKE = /\+?\d[\d\s().-]{7,}\d/

const MINOR_WORDS = new Set([
  'a', 'an', 'and', 'as', 'at', 'but', 'by', 'for', 'in', 'of', 'on', 'or',
  'the', 'to', 'with', '&',
])

/** One line of a document, with the cheap questions it gets asked repeatedly. */
export interface DocumentLine {
  readonly index: number
  readonly text: string
}

export interface LineModel {
  readonly text: string
  readonly lines: DocumentLine[]
}

/**
 * Normalises raw extracted text into numbered lines.
 *
 * Every line pointer stored anywhere in the database refers to this numbering,
 * which is why NORMALISATION_VERSION is recorded alongside them.
 */
export function toLineModel(rawText: string | null | undefined): LineModel {
  if (!rawText || !rawText.trim()) return { text: '', lines: [] }

  let normalised = rawText.normalize('NFC')
  normalised = normalised.replace(ZERO_WIDTH, '')
  normalised = normalised.replace(EXOTIC_SPACES, ' ')
  normalised = normalised.replace(LINE_SEPARATORS, '\n')

  const split = normalised.split('\n')

  // Trim leading and trailing blank lines, but never interior ones: a blank
  // line between blocks is a signal the section segmenter reads.
  let first = 0
  let last = split.length - 1
  while (first <= last && !split[first].trim()) first++
  while (last >= first && !split[last].trim()) last--
  if (first > last) return { text: '', lines: [] }

  const lines: DocumentLine[] = []
  for (let i = first; i <= last; i++) {
    lines.push({ index: lines.length, text: split[i].replace(/\s+$/, '') })
  }

  return { text: lines.map((line) => line.text).join('\n'), lines }
}

export function lineAt(model: LineModel, index: number): string {
  return index >= 0 && index < model.lines.length ? model.lines[index].text : ''
}

/** Joined text of an inclusive line range, clamped to the document. */
export function textOf(model: LineModel, start: number, end: number): string {
  const from = Math.max(0, start)
  const to = Math.min(model.lines.length - 1, end)
  if (from > to) return ''
  return model.lines
    .slice(from, to + 1)
    .map((line) => line.text)
    .join('\n')
    .trim()
}

export function nextNonBlank(model: LineModel, fromIndex: number): number {
  for (let i = Math.max(0, fromIndex); i < model.lines.length; i++) {
    if (model.lines[i].text.trim()) return i
  }
  return -1
}

/* ------------------------------------------------------------------ */
/* Line predicates                                                    */
/* ------------------------------------------------------------------ */

export const isBlank = (text: string) => !text || !text.trim()

export const wordCount = (text: string) => {
  const stripped = stripBullet(text)
  return stripped ? stripped.split(/\s+/).length : 0
}

export const isBullet = (text: string) => Boolean(text) && BULLET_START.test(text)

export const stripBullet = (text: string) =>
  text ? text.replace(BULLET_START, '').trim() : ''

export const hasEmail = (text: string) => Boolean(text) && EMAIL_LIKE.test(text)
export const hasUrl = (text: string) => Boolean(text) && URL_LIKE.test(text)
export const hasPhone = (text: string) => Boolean(text) && PHONE_LIKE.test(text)
export const hasContactDetails = (text: string) =>
  hasEmail(text) || hasUrl(text) || hasPhone(text)

export function isAllCaps(text: string): boolean {
  const stripped = text.trim()
  let letters = 0
  for (const char of stripped) {
    if (/\p{L}/u.test(char)) {
      letters++
      if (char === char.toLowerCase() && char !== char.toUpperCase()) return false
    }
  }
  return letters >= 3
}

export function isTitleCase(text: string): boolean {
  const stripped = text.trim()
  if (!stripped) return false

  let sawSignificantWord = false
  for (const word of stripped.split(/\s+/)) {
    const letters = word.replace(/[^\p{L}]/gu, '')
    if (!letters) continue
    if (MINOR_WORDS.has(letters.toLowerCase())) continue
    sawSignificantWord = true
    if (letters[0] !== letters[0].toUpperCase()) return false
  }
  return sawSignificantWord
}

/** A line ending in punctuation that prose uses and headings do not. */
export function endsLikeProse(text: string): boolean {
  const stripped = text.trim()
  if (!stripped) return false
  const last = stripped[stripped.length - 1]
  return last === '.' || last === ',' || last === ';'
}

/* ------------------------------------------------------------------ */
/* Whole-token search                                                 */
/* ------------------------------------------------------------------ */

const isWordChar = (char: string) => /[\p{L}\p{N}]/u.test(char)

/**
 * Finds a term bounded by non-alphanumeric characters.
 *
 * Every lexicon lookup in this codebase asks the same question — "does this text
 * contain this term *as a term*?" — and every one of them gets it wrong in the
 * same way with plain `includes`: "java" matches inside "javascript", "go"
 * inside "google", "r" inside "react".
 *
 * A token boundary is anything that is not a letter or digit, which is what lets
 * terms containing punctuation work without special cases: "c++", "node.js",
 * ".net" and "ci/cd" are all searched the same way.
 *
 * @param haystack lowercased text to search
 * @param term lowercase term to find
 * @returns the index of the match, or -1
 */
export function indexOfToken(haystack: string, term: string): number {
  if (!haystack || !term) return -1

  let from = 0
  for (;;) {
    const at = haystack.indexOf(term, from)
    if (at < 0) return -1

    const before = at === 0 || !isWordChar(haystack[at - 1])
    const afterIndex = at + term.length
    const after = afterIndex >= haystack.length || !isWordChar(haystack[afterIndex])

    if (before && after) return at
    from = at + 1
  }
}

export const containsToken = (text: string, term: string) =>
  indexOfToken(text.toLowerCase(), term) >= 0
