import {
  type LineModel,
  endsLikeProse,
  hasContactDetails,
  isAllCaps,
  isBlank,
  isBullet,
  isTitleCase,
  nextNonBlank,
  wordCount,
} from './text'

/**
 * Finding the sections of a resume.
 *
 * This is the step everything downstream depends on: an experience block that
 * is not recognised means no dated roles, no bullets to score, and no evidence
 * to quote. It is also the step most resumes get wrong, by giving sections
 * creative names that read beautifully and match nothing.
 */

export type SectionType =
  | 'CONTACT'
  | 'SUMMARY'
  | 'EDUCATION'
  | 'EXPERIENCE'
  | 'SKILLS'
  | 'PROJECTS'
  | 'CERTIFICATIONS'
  | 'ACHIEVEMENTS'
  | 'LANGUAGES'
  | 'PUBLICATIONS'
  | 'INTERESTS'
  | 'REFERENCES'
  | 'UNKNOWN'

export const SECTION_LABELS: Record<SectionType, string> = {
  CONTACT: 'Contact',
  SUMMARY: 'Summary',
  EDUCATION: 'Education',
  EXPERIENCE: 'Experience',
  SKILLS: 'Skills',
  PROJECTS: 'Projects',
  CERTIFICATIONS: 'Certifications',
  ACHIEVEMENTS: 'Achievements',
  LANGUAGES: 'Languages',
  PUBLICATIONS: 'Publications',
  INTERESTS: 'Interests',
  REFERENCES: 'References',
  UNKNOWN: 'Unrecognised',
}

/** The sections whose absence a screener would actually notice. */
export const CORE_SECTIONS: ReadonlySet<SectionType> = new Set<SectionType>([
  'CONTACT',
  'EDUCATION',
  'EXPERIENCE',
  'SKILLS',
])

export interface ResumeSection {
  readonly type: SectionType
  readonly headingText: string | null
  readonly headingLine: number
  readonly startLine: number
  readonly endLine: number
  readonly confidence: number
}

/**
 * Heading vocabulary.
 *
 * `exact` is the whole heading verbatim; `keyword` only has to appear in it. A
 * line reading "Professional Experience" hits the exact list, "Relevant Work
 * Experience & Internships" hits the keyword list and scores lower — which is
 * the honest answer, because a parser that has to guess should say so.
 */
interface HeadingRule {
  readonly type: SectionType
  readonly exact: readonly string[]
  readonly keywords: readonly string[]
}

const HEADING_RULES: readonly HeadingRule[] = [
  {
    type: 'EXPERIENCE',
    exact: [
      'experience', 'work experience', 'professional experience', 'employment',
      'employment history', 'work history', 'career history', 'internships',
      'internship experience', 'relevant experience', 'professional background',
    ],
    keywords: ['experience', 'employment', 'internship', 'work history'],
  },
  {
    type: 'EDUCATION',
    exact: [
      'education', 'academic background', 'academics', 'qualifications',
      'academic qualifications', 'educational background', 'education & training',
    ],
    keywords: ['education', 'academic', 'qualification', 'schooling'],
  },
  {
    type: 'SKILLS',
    exact: [
      'skills', 'technical skills', 'core skills', 'key skills', 'competencies',
      'core competencies', 'technologies', 'tech stack', 'technical proficiencies',
      'skills & tools', 'areas of expertise',
    ],
    keywords: ['skill', 'competenc', 'technolog', 'proficienc', 'expertise', 'tech stack'],
  },
  {
    type: 'PROJECTS',
    exact: [
      'projects', 'personal projects', 'academic projects', 'key projects',
      'selected projects', 'side projects', 'portfolio',
    ],
    keywords: ['project', 'portfolio'],
  },
  {
    type: 'SUMMARY',
    exact: [
      'summary', 'professional summary', 'profile', 'about me', 'objective',
      'career objective', 'personal statement', 'overview', 'career summary',
    ],
    keywords: ['summary', 'objective', 'profile', 'about'],
  },
  {
    type: 'CERTIFICATIONS',
    exact: [
      'certifications', 'certificates', 'licenses', 'licences',
      'certifications & licenses', 'courses', 'training',
    ],
    keywords: ['certification', 'certificate', 'licen', 'course', 'training'],
  },
  {
    type: 'ACHIEVEMENTS',
    exact: [
      'achievements', 'awards', 'honors', 'honours', 'accomplishments',
      'awards & achievements', 'honors & awards', 'extracurricular',
      'extracurricular activities', 'activities',
    ],
    keywords: ['achievement', 'award', 'honor', 'honour', 'accomplishment', 'extracurricular'],
  },
  {
    type: 'PUBLICATIONS',
    exact: ['publications', 'papers', 'research', 'research experience'],
    keywords: ['publication', 'research', 'paper'],
  },
  {
    type: 'LANGUAGES',
    exact: ['languages', 'language proficiency', 'languages known'],
    keywords: ['language'],
  },
  {
    type: 'INTERESTS',
    exact: ['interests', 'hobbies', 'hobbies & interests', 'personal interests'],
    keywords: ['interest', 'hobb'],
  },
  {
    type: 'REFERENCES',
    exact: ['references', 'referees'],
    keywords: ['reference', 'referee'],
  },
]

interface HeadingMatch {
  readonly type: SectionType
  readonly exact: boolean
}

/**
 * Resolves a line against the heading vocabulary.
 *
 * Exact matches are checked for every rule before any keyword match is
 * considered. Otherwise "Technical Skills & Projects" resolves to whichever rule
 * happens to be listed first, which is an ordering accident rather than a
 * decision.
 */
export function resolveHeading(text: string): HeadingMatch | null {
  const normalised = text
    .trim()
    .toLowerCase()
    .replace(/[:•|]+$/, '')
    .replace(/\s+/g, ' ')
    .trim()

  if (!normalised) return null

  for (const rule of HEADING_RULES) {
    if (rule.exact.includes(normalised)) return { type: rule.type, exact: true }
  }
  for (const rule of HEADING_RULES) {
    if (rule.keywords.some((keyword) => normalised.includes(keyword))) {
      return { type: rule.type, exact: false }
    }
  }
  return null
}

/* ------------------------------------------------------------------ */
/* Scoring                                                            */
/* ------------------------------------------------------------------ */

const HEADING_THRESHOLD = 60

const SCORE = {
  exactMatch: 50,
  keywordMatch: 28,
  allCaps: 18,
  titleCase: 9,
  shortLine: 12,
  shortChars: 8,
  blankBefore: 10,
  contentFollows: 6,
  trailingColon: 5,
} as const

const MAX_HEADING_WORDS = 6
const MAX_HEADING_CHARS = 60
const SHORT_LINE_WORDS = 3
const SHORT_LINE_CHARS = 30

/** Lines that cannot be headings whatever they say. */
function isDisqualified(text: string): boolean {
  return (
    isBlank(text) ||
    isBullet(text) ||
    // "Experience: aditi@example.com" is a contact line, not a heading.
    hasContactDetails(text) ||
    endsLikeProse(text) ||
    wordCount(text) > MAX_HEADING_WORDS ||
    text.trim().length > MAX_HEADING_CHARS
  )
}

function scoreHeading(model: LineModel, index: number, match: HeadingMatch): number {
  const text = model.lines[index].text
  const stripped = text.trim()

  let score = match.exact ? SCORE.exactMatch : SCORE.keywordMatch

  // Typography is evidence. A heading is usually visually distinct from the
  // body around it, and all-caps is the commonest way resumes do that.
  if (isAllCaps(stripped)) score += SCORE.allCaps
  else if (isTitleCase(stripped)) score += SCORE.titleCase

  if (wordCount(stripped) <= SHORT_LINE_WORDS) score += SCORE.shortLine
  if (stripped.length <= SHORT_LINE_CHARS) score += SCORE.shortChars
  if (stripped.endsWith(':')) score += SCORE.trailingColon

  // Whitespace above and content below is what a heading looks like
  // structurally, independent of what it says.
  if (index === 0 || isBlank(model.lines[index - 1].text)) score += SCORE.blankBefore
  if (nextNonBlank(model, index + 1) >= 0) score += SCORE.contentFollows

  return Math.min(100, score)
}

/**
 * Splits a document into sections.
 *
 * Text above the first heading becomes a headless CONTACT block, which is where
 * a resume's name and details almost always live.
 */
export function segmentSections(model: LineModel): ResumeSection[] {
  if (!model.lines.length) return []

  const headings: Array<{ line: number; type: SectionType; score: number }> = []

  for (const line of model.lines) {
    if (isDisqualified(line.text)) continue
    const match = resolveHeading(line.text)
    if (!match) continue

    const score = scoreHeading(model, line.index, match)
    if (score >= HEADING_THRESHOLD) {
      headings.push({ line: line.index, type: match.type, score })
    }
  }

  if (!headings.length) {
    return [
      {
        type: 'UNKNOWN',
        headingText: null,
        headingLine: -1,
        startLine: 0,
        endLine: model.lines.length - 1,
        confidence: 0,
      },
    ]
  }

  const sections: ResumeSection[] = []

  const firstHeadingLine = headings[0].line
  if (firstHeadingLine > 0) {
    const hasDetails = model.lines
      .slice(0, firstHeadingLine)
      .some((line) => hasContactDetails(line.text))

    sections.push({
      type: 'CONTACT',
      headingText: null,
      headingLine: -1,
      startLine: 0,
      endLine: firstHeadingLine - 1,
      // A block with an email in it is almost certainly the contact block. One
      // without is a guess, and says so.
      confidence: hasDetails ? 90 : 45,
    })
  }

  for (let i = 0; i < headings.length; i++) {
    const heading = headings[i]
    const contentEnd =
      i + 1 < headings.length ? headings[i + 1].line - 1 : model.lines.length - 1

    sections.push({
      type: heading.type,
      headingText: model.lines[heading.line].text.trim(),
      headingLine: heading.line,
      startLine: heading.line + 1,
      endLine: contentEnd,
      confidence: heading.score,
    })
  }

  return sections
}

export function findSection(
  sections: readonly ResumeSection[],
  type: SectionType,
): ResumeSection | undefined {
  return sections.find((section) => section.type === type)
}

/** Which section each line belongs to, for O(1) lookup during extraction. */
export function sectionByLine(
  sections: readonly ResumeSection[],
  lineCount: number,
): SectionType[] {
  const map: SectionType[] = new Array(lineCount).fill('UNKNOWN')
  for (const section of sections) {
    for (let i = Math.max(0, section.startLine); i <= Math.min(section.endLine, lineCount - 1); i++) {
      map[i] = section.type
    }
    if (section.headingLine >= 0 && section.headingLine < lineCount) {
      map[section.headingLine] = section.type
    }
  }
  return map
}
