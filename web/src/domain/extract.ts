import {
  type LineModel,
  hasContactDetails,
  indexOfToken,
  isBullet,
  lineAt,
  stripBullet,
  textOf,
  wordCount,
} from './text'
import {
  type ResumeSection,
  type SectionType,
  findSection,
  sectionByLine,
} from './sections'
import { type SkillCategory, findSkillsIn } from './skills'

/**
 * Turning a resume's text into structured fields.
 *
 * Every extractor returns a confidence and the line range it read from. Both are
 * shown to the user: the point of the product is that they can see what the
 * machine understood and correct it, which requires admitting how sure it is.
 */

/* ------------------------------------------------------------------ */
/* Dates                                                              */
/* ------------------------------------------------------------------ */

const MONTHS: Record<string, number> = {
  jan: 1, january: 1, feb: 2, february: 2, mar: 3, march: 3, apr: 4, april: 4,
  may: 5, jun: 6, june: 6, jul: 7, july: 7, aug: 8, august: 8,
  sep: 9, sept: 9, september: 9, oct: 10, october: 10, nov: 11, november: 11,
  dec: 12, december: 12,
}

const MONTH_NAMES = Object.keys(MONTHS).join('|')
const PRESENT = '(?:present|current|now|ongoing|to date|till date)'

const RANGE_PATTERN = new RegExp(
  String.raw`(?:(${MONTH_NAMES})\.?\s*)?(\d{4})\s*(?:-|–|—|to|until|through)\s*(?:(?:(${MONTH_NAMES})\.?\s*)?(\d{4})|${PRESENT})`,
  'i',
)

const SINGLE_PATTERN = new RegExp(
  String.raw`(?:(${MONTH_NAMES})\.?\s+)?(\d{4})`,
  'i',
)

const CURRENT_PATTERN = new RegExp(PRESENT, 'i')

/** Years outside this range are page numbers, phone fragments, or typos. */
const MIN_YEAR = 1950
const MAX_YEAR = new Date().getFullYear() + 10

export interface DateRange {
  readonly start: string | null
  readonly end: string | null
  readonly current: boolean
}

const iso = (year: number, month: number) =>
  `${year}-${String(month).padStart(2, '0')}-01`

const plausibleYear = (year: number) => year >= MIN_YEAR && year <= MAX_YEAR

/**
 * Reads a date range from a line.
 *
 * Refuses rather than guesses. A wrong date silently corrupts the years-of-
 * experience calculation, and a candidate told they have four years when they
 * have two will be caught out in an interview — so an unparseable line yields
 * nothing at all.
 */
export function parseDateRange(text: string): DateRange | null {
  if (!text) return null

  const range = RANGE_PATTERN.exec(text)
  if (range) {
    const startYear = Number(range[2])
    if (!plausibleYear(startYear)) return null

    const startMonth = range[1] ? MONTHS[range[1].toLowerCase()] : 1
    const start = iso(startYear, startMonth)

    // The end group is absent when the range ended with "Present".
    if (!range[4]) {
      return { start, end: null, current: true }
    }

    const endYear = Number(range[4])
    if (!plausibleYear(endYear) || endYear < startYear) return null
    const endMonth = range[3] ? MONTHS[range[3].toLowerCase()] : 12

    return { start, end: iso(endYear, endMonth), current: false }
  }

  const single = SINGLE_PATTERN.exec(text)
  if (single) {
    const year = Number(single[2])
    if (!plausibleYear(year)) return null
    const month = single[1] ? MONTHS[single[1].toLowerCase()] : 1
    const date = iso(year, month)
    return { start: date, end: date, current: CURRENT_PATTERN.test(text) }
  }

  return null
}

/** The line with any date range removed, leaving the title or institution. */
export function stripDates(text: string): string {
  return text
    .replace(RANGE_PATTERN, ' ')
    .replace(CURRENT_PATTERN, ' ')
    .replace(/[|•,–—-]\s*$/, '')
    .replace(/^\s*[|•,–—-]/, '')
    .replace(/\s{2,}/g, ' ')
    .trim()
}

/* ------------------------------------------------------------------ */
/* Contact                                                            */
/* ------------------------------------------------------------------ */

const EMAIL = /[\w.+-]+@[\w-]+\.[\w.]{2,}/
const PHONE = /(?:\+\d{1,3}[\s-]?)?(?:\(\d{2,4}\)[\s-]?)?\d[\d\s().-]{7,}\d/
const LINKEDIN = /(?:https?:\/\/)?(?:www\.)?linkedin\.com\/[^\s|,)]+/i
const GITHUB = /(?:https?:\/\/)?(?:www\.)?github\.com\/[^\s|,)]+/i
const GENERIC_URL = /(?:https?:\/\/)[^\s|,)]+|(?:www\.)[^\s|,)]+/i

/** A city/region fragment: "Bengaluru, India", "London, UK". */
const LOCATION = /\b([A-Z][a-zA-Z.'-]+(?:\s+[A-Z][a-zA-Z.'-]+)*),\s*([A-Z][a-zA-Z.'-]+(?:\s+[A-Z][a-zA-Z.'-]+)*)\b/

/** Words that appear in a heading, never in a person's name. */
const NON_NAME_WORDS = new Set([
  'resume', 'curriculum', 'vitae', 'cv', 'profile', 'portfolio', 'contact',
  'summary', 'objective', 'experience', 'education', 'skills', 'projects',
  'phone', 'email', 'address', 'linkedin', 'github', 'engineer', 'developer',
  'student', 'intern',
])

export interface ContactDetails {
  fullName: string | null
  nameConfidence: number | null
  email: string | null
  phone: string | null
  location: string | null
  linkedinUrl: string | null
  githubUrl: string | null
  portfolioUrl: string | null
  confidence: number
  lineStart: number | null
  lineEnd: number | null
}

const emptyContact = (): ContactDetails => ({
  fullName: null, nameConfidence: null, email: null, phone: null, location: null,
  linkedinUrl: null, githubUrl: null, portfolioUrl: null,
  confidence: 0, lineStart: null, lineEnd: null,
})

/**
 * Whether a line looks like a person's name.
 *
 * Deliberately conservative. Claiming the wrong name is worse than claiming
 * none: a blank name field tells the user the parser could not find it, which is
 * itself the finding they need.
 */
function scoreName(text: string): number {
  const stripped = text.trim()
  if (!stripped || stripped.length > 60) return 0
  if (hasContactDetails(stripped)) return 0

  const words = stripped.split(/\s+/)
  if (words.length < 2 || words.length > 4) return 0

  const lower = stripped.toLowerCase()
  if ([...NON_NAME_WORDS].some((word) => indexOfToken(lower, word) >= 0)) return 0
  // Digits and most punctuation do not appear in names.
  if (/[\d@|/\\]/.test(stripped)) return 0

  const allCapitalised = words.every((word) => {
    const letters = word.replace(/[^\p{L}]/gu, '')
    return letters.length > 0 && letters[0] === letters[0].toUpperCase()
  })
  if (!allCapitalised) return 0

  // ALL CAPS is a common styling for the name line, but also for headings —
  // hence lower confidence rather than rejection.
  const shouty = stripped === stripped.toUpperCase()
  return shouty ? 70 : 85
}

export function extractContact(
  model: LineModel,
  sections: readonly ResumeSection[],
): ContactDetails {
  const contact = emptyContact()
  if (!model.lines.length) return contact

  // Search the contact block if one was identified, otherwise the opening lines
  // — which is where the details are on essentially every resume.
  const block = findSection(sections, 'CONTACT')
  const start = block ? block.startLine : 0
  const end = block ? block.endLine : Math.min(9, model.lines.length - 1)

  contact.lineStart = start
  contact.lineEnd = end

  let found = 0

  for (let i = start; i <= Math.min(end, model.lines.length - 1); i++) {
    const text = lineAt(model, i)
    if (!text.trim()) continue

    if (!contact.email) {
      const match = EMAIL.exec(text)
      if (match) {
        contact.email = match[0]
        found++
      }
    }

    if (!contact.linkedinUrl) {
      const match = LINKEDIN.exec(text)
      if (match) {
        contact.linkedinUrl = match[0]
        found++
      }
    }

    if (!contact.githubUrl) {
      const match = GITHUB.exec(text)
      if (match) {
        contact.githubUrl = match[0]
        found++
      }
    }

    if (!contact.phone) {
      // Strip anything already claimed, so a LinkedIn URL containing digits
      // cannot be read as a phone number.
      const withoutKnown = text
        .replace(EMAIL, ' ')
        .replace(LINKEDIN, ' ')
        .replace(GITHUB, ' ')
      const match = PHONE.exec(withoutKnown)
      if (match) {
        const digits = match[0].replace(/\D/g, '')
        if (digits.length >= 8 && digits.length <= 15) {
          contact.phone = match[0].trim()
          found++
        }
      }
    }

    if (!contact.portfolioUrl && !LINKEDIN.test(text) && !GITHUB.test(text)) {
      const match = GENERIC_URL.exec(text)
      if (match) {
        contact.portfolioUrl = match[0]
        found++
      }
    }

    if (!contact.location) {
      const withoutEmail = text.replace(EMAIL, ' ')
      const match = LOCATION.exec(withoutEmail)
      if (match) {
        contact.location = match[0].trim()
        found++
      }
    }
  }

  // The name is looked for only in the first few lines, and only after the
  // other fields, so a line already claimed as contact detail is skipped.
  for (let i = start; i <= Math.min(start + 4, model.lines.length - 1); i++) {
    const score = scoreName(lineAt(model, i))
    if (score > 0) {
      contact.fullName = lineAt(model, i).trim()
      contact.nameConfidence = score
      found++
      break
    }
  }

  contact.confidence = Math.min(100, found * 18)
  return contact
}

export const contactIsEmpty = (contact: ContactDetails) =>
  !contact.fullName && !contact.email && !contact.phone && !contact.location &&
  !contact.linkedinUrl && !contact.githubUrl && !contact.portfolioUrl

/* ------------------------------------------------------------------ */
/* Skills                                                             */
/* ------------------------------------------------------------------ */

export interface DetectedSkill {
  readonly name: string
  readonly normalizedName: string
  readonly category: SkillCategory
  readonly confidence: number
  readonly lineStart: number
}

const CONFIDENCE_IN_SKILLS_SECTION = 95
const CONFIDENCE_AMBIGUOUS_IN_SKILLS_SECTION = 80
const CONFIDENCE_ELSEWHERE = 75
const CONFIDENCE_UNSTRUCTURED = 60

/**
 * Finds skills, weighting a mention by where it appeared.
 *
 * A skill listed in the skills block is a claim. The same word inside a
 * sentence may be incidental — "worked alongside the Java team" is not a claim
 * to know Java — so it scores lower and ambiguous names are not matched there
 * at all.
 */
export function extractSkills(
  model: LineModel,
  sections: readonly ResumeSection[],
): DetectedSkill[] {
  if (!model.lines.length) return []

  const lineSections = sectionByLine(sections, model.lines.length)
  const hasSkillsSection = sections.some((section) => section.type === 'SKILLS')
  const found = new Map<string, DetectedSkill>()

  for (const line of model.lines) {
    if (!line.text.trim()) continue

    const section: SectionType = lineSections[line.index] ?? 'UNKNOWN'
    const inSkillsSection = section === 'SKILLS'

    for (const hit of findSkillsIn(line.text, inSkillsSection)) {
      const canonical = hit.entry.canonical
      // First mention wins: it is the most likely to be the deliberate listing,
      // and re-reading the same skill from a later bullet would only downgrade
      // its confidence.
      if (found.has(canonical)) continue

      let confidence: number
      if (inSkillsSection) {
        confidence = hit.entry.ambiguous
          ? CONFIDENCE_AMBIGUOUS_IN_SKILLS_SECTION
          : CONFIDENCE_IN_SKILLS_SECTION
      } else if (hasSkillsSection) {
        confidence = CONFIDENCE_ELSEWHERE
      } else {
        // No skills section at all: everything is a guess from prose.
        confidence = CONFIDENCE_UNSTRUCTURED
      }

      found.set(canonical, {
        name: originalCasing(line.text, hit.start, canonical) ?? canonical,
        normalizedName: canonical,
        category: hit.entry.category,
        confidence,
        lineStart: line.index,
      })
    }
  }

  return [...found.values()]
}

/**
 * Recovers how the candidate actually wrote a skill.
 *
 * Their spelling is shown back to them unaltered — telling someone their resume
 * says "postgresql" when it says "PostgreSQL" undermines the claim that this is
 * what the machine read.
 */
function originalCasing(line: string, start: number, canonical: string): string | null {
  const slice = line.slice(start, start + canonical.length)
  return slice.toLowerCase() === canonical.toLowerCase() ? slice : null
}

/* ------------------------------------------------------------------ */
/* Entry splitting                                                    */
/* ------------------------------------------------------------------ */

export interface SectionEntry {
  readonly startLine: number
  readonly endLine: number
}

/**
 * Splits a section's body into one entry per role or qualification.
 *
 * A new entry starts at a non-bullet line that carries a date, or after a blank
 * line followed by a non-bullet line. Bullets always belong to the entry above
 * them, which is what stops a role's achievements being read as separate jobs.
 */
export function splitEntries(
  model: LineModel,
  section: ResumeSection,
): SectionEntry[] {
  const entries: SectionEntry[] = []
  const start = Math.max(0, section.startLine)
  const end = Math.min(section.endLine, model.lines.length - 1)
  if (start > end) return entries

  let currentStart = -1
  let sawBlank = false

  for (let i = start; i <= end; i++) {
    const text = lineAt(model, i)

    if (!text.trim()) {
      sawBlank = true
      continue
    }

    const bullet = isBullet(text)
    const dated = parseDateRange(text) !== null
    const startsNewEntry = !bullet && (dated || sawBlank || currentStart < 0)

    if (startsNewEntry && currentStart >= 0) {
      entries.push({ startLine: currentStart, endLine: i - 1 })
      currentStart = i
    } else if (currentStart < 0) {
      currentStart = i
    }

    sawBlank = false
  }

  if (currentStart >= 0) entries.push({ startLine: currentStart, endLine: end })

  return entries.filter((entry) => entry.endLine >= entry.startLine)
}

/* ------------------------------------------------------------------ */
/* Education                                                          */
/* ------------------------------------------------------------------ */

const DEGREE_PATTERNS: ReadonlyArray<[RegExp, string]> = [
  [/\bb\.?\s?tech\b|\bbachelor of technology\b/i, 'B.Tech'],
  [/\bm\.?\s?tech\b|\bmaster of technology\b/i, 'M.Tech'],
  [/\bb\.?\s?e\.?\b|\bbachelor of engineering\b/i, 'B.E.'],
  [/\bb\.?\s?sc\b|\bbachelor of science\b/i, 'B.Sc'],
  [/\bm\.?\s?sc\b|\bmaster of science\b/i, 'M.Sc'],
  [/\bb\.?\s?a\.?\b|\bbachelor of arts\b/i, 'B.A.'],
  [/\bm\.?\s?a\.?\b|\bmaster of arts\b/i, 'M.A.'],
  [/\bb\.?\s?com\b/i, 'B.Com'],
  [/\bm\.?\s?com\b/i, 'M.Com'],
  [/\bmba\b|\bmaster of business\b/i, 'MBA'],
  [/\bbca\b/i, 'BCA'],
  [/\bmca\b/i, 'MCA'],
  [/\bph\.?\s?d\b|\bdoctorate\b/i, 'PhD'],
  [/\bdiploma\b/i, 'Diploma'],
  [/\bhigh school\b|\bsecondary\b|\b12th\b|\bhsc\b/i, 'High School'],
]

const INSTITUTION_WORDS = [
  'university', 'college', 'institute', 'school', 'academy', 'polytechnic',
  'iit', 'nit', 'iiit',
]

const FIELD_PATTERN = /\b(?:in|of)\s+([A-Z][\w&.\- ]{2,60})/
const GRADE_PATTERN = /\b(?:cgpa|gpa|percentage|score|marks)\s*[:\-]?\s*([\d.]+\s*%?(?:\s*\/\s*[\d.]+)?)|\b([\d.]{1,5})\s*(?:cgpa|gpa)\b|\b(\d{2}(?:\.\d+)?)\s*%/i

export interface EducationEntry {
  institution: string | null
  degree: string | null
  fieldOfStudy: string | null
  startDate: string | null
  endDate: string | null
  grade: string | null
  confidence: number
  lineStart: number
  lineEnd: number
}

export function extractEducation(
  model: LineModel,
  sections: readonly ResumeSection[],
): EducationEntry[] {
  const section = findSection(sections, 'EDUCATION')
  if (!section) return []

  return splitEntries(model, section)
    .map((entry) => readEducationEntry(model, entry))
    .filter((entry): entry is EducationEntry => entry !== null)
}

function readEducationEntry(model: LineModel, entry: SectionEntry): EducationEntry | null {
  const block = textOf(model, entry.startLine, entry.endLine)
  if (!block.trim()) return null

  let institution: string | null = null
  let degree: string | null = null
  let fieldOfStudy: string | null = null
  let grade: string | null = null
  let range: DateRange | null = null
  let found = 0

  for (let i = entry.startLine; i <= entry.endLine; i++) {
    const text = lineAt(model, i)
    const lower = text.toLowerCase()

    if (!range) range = parseDateRange(text)

    if (!institution && INSTITUTION_WORDS.some((word) => indexOfToken(lower, word) >= 0)) {
      institution = trimTo(stripDates(stripBullet(text)), 200)
      if (institution) found++
    }

    if (!degree) {
      for (const [pattern, label] of DEGREE_PATTERNS) {
        if (pattern.test(text)) {
          degree = label
          found++
          break
        }
      }
    }

    if (!fieldOfStudy && degree) {
      const match = FIELD_PATTERN.exec(stripDates(text))
      if (match) {
        fieldOfStudy = trimTo(match[1].trim(), 150)
        if (fieldOfStudy) found++
      }
    }

    if (!grade) {
      const match = GRADE_PATTERN.exec(text)
      if (match) {
        grade = trimTo((match[1] ?? match[2] ?? match[3] ?? '').trim(), 20)
        if (grade) found++
      }
    }
  }

  // An institution named on no line but present as the first line of the entry
  // is the common "NIT Trichy" case, with no giveaway word.
  if (!institution && !degree) {
    const first = trimTo(stripDates(stripBullet(lineAt(model, entry.startLine))), 200)
    if (first && first.length > 3) {
      institution = first
      found++
    }
  }

  if (!institution && !degree && !range) return null

  if (range) found++

  return {
    institution,
    degree,
    fieldOfStudy,
    startDate: range?.start ?? null,
    endDate: range?.end ?? null,
    grade,
    confidence: Math.min(100, 35 + found * 15),
    lineStart: entry.startLine,
    lineEnd: entry.endLine,
  }
}

/* ------------------------------------------------------------------ */
/* Experience                                                         */
/* ------------------------------------------------------------------ */

const ROLE_WORDS = [
  'engineer', 'developer', 'intern', 'analyst', 'manager', 'designer',
  'consultant', 'architect', 'scientist', 'administrator', 'specialist',
  'lead', 'associate', 'assistant', 'coordinator', 'executive', 'officer',
  'trainee', 'freelance', 'contractor', 'researcher',
]

const COMPANY_SUFFIXES = [
  'inc', 'llc', 'ltd', 'limited', 'corp', 'corporation', 'gmbh', 'plc',
  'technologies', 'technology', 'solutions', 'systems', 'labs', 'studio',
  'consulting', 'services', 'group', 'holdings', 'pvt', 'private',
]

export interface ExperienceEntry {
  company: string | null
  jobTitle: string | null
  startDate: string | null
  endDate: string | null
  isCurrent: boolean
  description: string | null
  confidence: number
  lineStart: number
  lineEnd: number
}

export function extractExperience(
  model: LineModel,
  sections: readonly ResumeSection[],
): ExperienceEntry[] {
  const section = findSection(sections, 'EXPERIENCE')
  if (!section) return []

  return splitEntries(model, section)
    .map((entry) => readExperienceEntry(model, entry))
    .filter((entry): entry is ExperienceEntry => entry !== null)
}

function readExperienceEntry(model: LineModel, entry: SectionEntry): ExperienceEntry | null {
  let jobTitle: string | null = null
  let company: string | null = null
  let range: DateRange | null = null
  const bullets: string[] = []
  let found = 0

  for (let i = entry.startLine; i <= entry.endLine; i++) {
    const text = lineAt(model, i)
    if (!text.trim()) continue

    if (isBullet(text)) {
      bullets.push(stripBullet(text))
      continue
    }

    if (!range) {
      range = parseDateRange(text)
      if (range) found++
    }

    const cleaned = stripDates(text)
    if (!cleaned) continue

    // "Software Engineer, Acme Technologies" and "Software Engineer at Acme"
    // are both extremely common; splitting on the separator gets both halves
    // from one line.
    const parts = cleaned.split(/\s+(?:at|@|[|,–—])\s+|,\s+/).map((part) => part.trim()).filter(Boolean)

    for (const part of parts) {
      const lower = part.toLowerCase()
      const looksLikeRole = ROLE_WORDS.some((word) => indexOfToken(lower, word) >= 0)
      const looksLikeCompany = COMPANY_SUFFIXES.some((word) => indexOfToken(lower, word) >= 0)

      if (!jobTitle && looksLikeRole) {
        jobTitle = trimTo(part, 200)
        found++
      } else if (!company && (looksLikeCompany || (jobTitle && part !== jobTitle))) {
        company = trimTo(part, 200)
        found++
      }
    }
  }

  if (!jobTitle && !company && !range && !bullets.length) return null

  return {
    company,
    jobTitle,
    startDate: range?.start ?? null,
    endDate: range?.end ?? null,
    isCurrent: range?.current ?? false,
    description: bullets.length ? bullets.join('\n') : null,
    confidence: Math.min(100, 30 + found * 15 + Math.min(20, bullets.length * 5)),
    lineStart: entry.startLine,
    lineEnd: entry.endLine,
  }
}

export const experienceIsEmpty = (entry: ExperienceEntry) =>
  !entry.company && !entry.jobTitle && !entry.startDate && !entry.description

export const educationIsEmpty = (entry: EducationEntry) =>
  !entry.institution && !entry.degree && !entry.fieldOfStudy && !entry.endDate

function trimTo(value: string | null, max: number): string | null {
  if (!value) return null
  const trimmed = value.trim()
  if (!trimmed) return null
  return trimmed.length <= max ? trimmed : trimmed.slice(0, max)
}

/** Bullet lines inside a section, used by the content rules. */
export function bulletsIn(
  model: LineModel,
  section: ResumeSection,
): Array<{ index: number; text: string }> {
  const bullets: Array<{ index: number; text: string }> = []
  for (let i = Math.max(0, section.startLine); i <= Math.min(section.endLine, model.lines.length - 1); i++) {
    const text = lineAt(model, i)
    if (isBullet(text) && wordCount(text) >= 3) bullets.push({ index: i, text })
  }
  return bullets
}
