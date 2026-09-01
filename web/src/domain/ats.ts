import {
  type ResumeSnapshot,
  type SnapshotExperience,
  EMPTY_CONTACT,
  hasSection,
  snapshotLine,
  snapshotSection,
  snapshotText,
} from './snapshot'
import { isBullet, stripBullet, wordCount } from './text'

/**
 * The rubric. Every point this product ever takes off a resume is taken off
 * here, by a named rule, with a quote attached.
 *
 * Deterministic and pure: same snapshot in, same assessment out. No clock, no
 * network, no model. A score a student can reproduce and argue with is worth
 * more than a slightly better score they cannot.
 *
 * Deductions are expressed on each category's own 0-100 scale. The category
 * weights are applied afterwards, so a rule author never has to reason about
 * global weighting to know what their rule costs.
 */

/** Bump on any change to rules or weights. Stored with every analysis. */
export const RUBRIC_VERSION = '1.0.0'

export type AtsCategory = 'PARSEABILITY' | 'STRUCTURE' | 'CONTENT' | 'SKILLS' | 'CONTACT'
export type AtsSeverity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' | 'PASS'
export type ScoreBand = 'NEEDS_WORK' | 'FAIR' | 'GOOD' | 'STRONG'

interface CategoryMeta {
  readonly label: string
  /** Share of the 100-point overall score. The five sum to 100. */
  readonly weight: number
  readonly description: string
}

/**
 * Parseability carries the most weight because it is the only category that can
 * zero the others. Content a screener never sees scores nothing, however well
 * it is written.
 */
export const ATS_CATEGORIES: Record<AtsCategory, CategoryMeta> = {
  PARSEABILITY: {
    label: 'Parseability',
    weight: 30,
    description: 'Whether screening software can read the file, and in the intended order.',
  },
  STRUCTURE: {
    label: 'Structure',
    weight: 20,
    description: 'Whether the expected sections exist and are labelled recognisably.',
  },
  CONTENT: {
    label: 'Content',
    weight: 25,
    description: 'Whether bullets describe measurable results rather than assigned duties.',
  },
  SKILLS: {
    label: 'Skills',
    weight: 15,
    description: 'Whether concrete, matchable technical vocabulary is present and varied.',
  },
  CONTACT: {
    label: 'Contact',
    weight: 10,
    description: 'Whether a recruiter who decides to reply can find a way to.',
  },
}

/** Bands rather than decimals: the rubric is not precise enough to justify one. */
export const SCORE_BANDS: Record<ScoreBand, { label: string; summary: string }> = {
  NEEDS_WORK: {
    label: 'Needs work',
    summary: 'Likely to be filtered out before a human reads it.',
  },
  FAIR: { label: 'Fair', summary: 'Readable, but leaving opportunities on the table.' },
  GOOD: { label: 'Good', summary: 'Solid. A few specific fixes away from strong.' },
  STRONG: { label: 'Strong', summary: 'Parses cleanly and reads as evidence. Apply.' },
}

const SEVERITY_RANK: Record<AtsSeverity, number> = {
  CRITICAL: 4, HIGH: 3, MEDIUM: 2, LOW: 1, PASS: 0,
}

export interface RuleFinding {
  readonly code: string
  readonly category: AtsCategory
  readonly severity: AtsSeverity
  readonly title: string
  readonly detail: string
  readonly recommendation: string | null
  readonly evidence: string | null
  readonly lineStart: number | null
  readonly lineEnd: number | null
  readonly pointsLost: number
}

export interface AtsAssessment {
  readonly overallScore: number
  readonly band: ScoreBand
  readonly categoryScores: Record<AtsCategory, number>
  readonly findings: readonly RuleFinding[]
  readonly rubricVersion: string
}

export function bandFor(score: number): ScoreBand {
  if (score >= 85) return 'STRONG'
  if (score >= 70) return 'GOOD'
  if (score >= 50) return 'FAIR'
  return 'NEEDS_WORK'
}

/* ------------------------------------------------------------------ */
/* Language                                                           */
/* ------------------------------------------------------------------ */

/**
 * Verbs describing a thing done, not a thing assigned. Stored as stems without
 * the -ed/-s ending so "Built", "Build" and "Builds" match on one entry.
 */
const ACTION_VERB_STEMS = new Set([
  'achiev', 'acquir', 'adapt', 'address', 'administer', 'advis', 'analys',
  'analyz', 'architect', 'assembl', 'assess', 'audit', 'authent', 'author',
  'automat', 'benchmark', 'boost', 'build', 'built', 'captur', 'central',
  'chair', 'coach', 'cod', 'collaborat', 'collect', 'compil', 'complet',
  'compos', 'comput', 'conduct', 'configur', 'consolidat', 'construct',
  'consult', 'contribut', 'convert', 'coordinat', 'creat', 'cut', 'debug',
  'decreas', 'defin', 'deliver', 'demonstrat', 'deploy', 'design', 'detect',
  'develop', 'devis', 'diagnos', 'direct', 'document', 'doubl', 'draft',
  'drove', 'driv', 'earn', 'edit', 'eliminat', 'enabl', 'engineer', 'enhanc',
  'ensur', 'establish', 'evaluat', 'execut', 'expand', 'experiment', 'explor',
  'extend', 'facilitat', 'fix', 'forecast', 'found', 'generat', 'grew', 'grow',
  'guid', 'halv', 'handl', 'headed', 'identifi', 'implement', 'improv',
  'increas', 'influenc', 'initiat', 'innovat', 'instal', 'instrument',
  'integrat', 'introduc', 'invent', 'investigat', 'launch', 'led', 'lead',
  'leverag', 'maintain', 'manag', 'map', 'measur', 'mentor', 'migrat',
  'minimis', 'minimiz', 'model', 'modernis', 'moderniz', 'monitor', 'negotiat',
  'obtain', 'optimis', 'optimiz', 'orchestrat', 'organis', 'organiz',
  'overhaul', 'own', 'partner', 'perform', 'pilot', 'pioneer', 'plan',
  'predict', 'prepar', 'present', 'prioritis', 'prioritiz', 'process',
  'produc', 'program', 'promot', 'propos', 'prototyp', 'publish', 'queri',
  'rais', 'rank', 'rebuilt', 'rebuild', 'recommend', 'reconcil', 'record',
  'recruit', 'redesign', 'reduc', 'refactor', 'releas', 'remov', 'render',
  'repair', 'replac', 'report', 'research', 'resolv', 'restructur', 'retriev',
  'revamp', 'review', 'revis', 'rewrote', 'rewrit', 'sav', 'scal', 'schedul',
  'secur', 'select', 'shipp', 'ship', 'simplifi', 'simulat', 'solv', 'sourc',
  'spearhead', 'specifi', 'stabilis', 'stabiliz', 'standardis', 'standardiz',
  'streamlin', 'strengthen', 'structur', 'submit', 'supervis', 'support',
  'sustain', 'synthesis', 'synthesiz', 'target', 'taught', 'teach', 'test',
  'track', 'train', 'transform', 'translat', 'trim', 'troubleshoot', 'tun',
  'unifi', 'upgrad', 'validat', 'verifi', 'won', 'wrote', 'writ',
])

/**
 * Openers describing a job description rather than a person's work.
 * "Responsible for X" says the task existed. It does not say it was done, done
 * well, or done by the applicant.
 */
const WEAK_OPENERS = [
  'responsible for', 'responsibilities included', 'duties included', 'duties:',
  'tasked with', 'in charge of', 'helped with', 'helped to', 'assisted with',
  'worked on', 'worked with', 'involved in', 'participated in',
  'part of a team', 'was responsible', 'familiar with', 'exposure to',
  'knowledge of',
]

const FIRST_PERSON = ['i', "i'm", "i've", 'my', 'me', 'mine', 'myself']

/** Anything a reader can check: a number, a percentage, money, a magnitude. */
const QUANTIFIED = /(\d+\s*%|[$£€₹]\s*\d|\d+\s*(k|m|bn|b)\b|\b\d[\d,.]*\b)/i

const startsWithActionVerb = (line: string): boolean => {
  const body = stripBullet(line).toLowerCase()
  if (!body) return false
  const first = body.split(/[^\p{L}]+/u)[0]
  if (!first || first.length < 3) return false
  return [...ACTION_VERB_STEMS].some((stem) => first.startsWith(stem))
}

const isQuantified = (line: string) => QUANTIFIED.test(line)

const weakOpener = (line: string): string | null => {
  const body = stripBullet(line).toLowerCase()
  return WEAK_OPENERS.find((opener) => body.startsWith(opener)) ?? null
}

const firstPersonMarker = (line: string): string | null => {
  const words = stripBullet(line).toLowerCase().split(/[^\p{L}']+/u)
  return FIRST_PERSON.find((marker) => words.includes(marker)) ?? null
}

/* ------------------------------------------------------------------ */
/* Thresholds                                                         */
/* ------------------------------------------------------------------ */

const IDEAL_MIN_WORDS = 250
const IDEAL_MAX_WORDS = 900
const MAX_BULLET_WORDS = 34
const MIN_SKILLS = 6
const HEALTHY_SKILLS = 12
const KEYWORD_STUFFING_SKILLS = 60

/* ------------------------------------------------------------------ */
/* The rubric                                                         */
/* ------------------------------------------------------------------ */

export function evaluateAts(snapshot: ResumeSnapshot): AtsAssessment {
  const deductions: Record<AtsCategory, number> = {
    PARSEABILITY: 0, STRUCTURE: 0, CONTENT: 0, SKILLS: 0, CONTACT: 0,
  }
  const findings: RuleFinding[] = []

  const deduct = (category: AtsCategory, points: number) => {
    deductions[category] += points
  }

  evaluateParseability(snapshot, deduct, findings)
  evaluateStructure(snapshot, deduct, findings)
  evaluateContent(snapshot, deduct, findings)
  evaluateSkills(snapshot, deduct, findings)
  evaluateContact(snapshot, deduct, findings)

  const categoryScores = {} as Record<AtsCategory, number>
  let weighted = 0

  for (const key of Object.keys(ATS_CATEGORIES) as AtsCategory[]) {
    const score = Math.max(0, Math.min(100, 100 - deductions[key]))
    categoryScores[key] = score
    weighted += score * (ATS_CATEGORIES[key].weight / 100)
  }

  const overallScore = Math.round(weighted)

  // Most urgent first, and within a severity the most expensive first: a user
  // reading top-down should fix the thing that costs the most first.
  const ordered = [...findings].sort(
    (a, b) =>
      SEVERITY_RANK[b.severity] - SEVERITY_RANK[a.severity] ||
      b.pointsLost - a.pointsLost ||
      a.code.localeCompare(b.code),
  )

  return {
    overallScore,
    band: bandFor(overallScore),
    categoryScores,
    findings: ordered,
    rubricVersion: RUBRIC_VERSION,
  }
}

type Deduct = (category: AtsCategory, points: number) => void

const problem = (
  code: string,
  category: AtsCategory,
  severity: AtsSeverity,
  title: string,
  detail: string,
  recommendation: string,
  pointsLost: number,
  evidence: string | null = null,
  lineStart: number | null = null,
  lineEnd: number | null = null,
): RuleFinding => ({
  code, category, severity, title, detail, recommendation,
  evidence: evidence && evidence.length > 600 ? `${evidence.slice(0, 599)}…` : evidence,
  lineStart, lineEnd, pointsLost,
})

/** Something already right. Costs nothing and is still reported. */
const pass = (
  code: string,
  category: AtsCategory,
  title: string,
  detail: string,
): RuleFinding => ({
  code, category, severity: 'PASS', title, detail,
  recommendation: null, evidence: null, lineStart: null, lineEnd: null, pointsLost: 0,
})

/* --- Parseability ------------------------------------------------- */

function evaluateParseability(
  snapshot: ResumeSnapshot,
  deduct: Deduct,
  findings: RuleFinding[],
): void {
  const warnings = snapshot.warningCodes

  if (warnings.has('NO_TEXT_LAYER')) {
    deduct('PARSEABILITY', 100)
    findings.push(problem(
      'NO_TEXT_LAYER', 'PARSEABILITY', 'CRITICAL',
      'No selectable text in this file',
      'Nothing in this document is machine-readable. It is almost certainly a scan or an image exported as a PDF. Screening software sees an empty document, so none of your experience exists as far as it is concerned.',
      'Export a PDF directly from Word, Google Docs, or LaTeX rather than scanning or screenshotting. Test it by trying to select the text in a PDF reader.',
      100,
    ))
    return
  }

  if (warnings.has('MULTI_COLUMN_LAYOUT')) {
    deduct('PARSEABILITY', 40)
    const end = Math.min(6, Math.max(0, snapshot.lines.length - 1))
    findings.push(problem(
      'MULTI_COLUMN_LAYOUT', 'PARSEABILITY', 'CRITICAL',
      'Multi-column layout detected',
      'This resume appears to use side-by-side columns. Most parsers read a page as one stream from top to bottom, so a sidebar gets interleaved into your work history — a job title attached to the wrong employer, or a skills list spliced through a bullet.',
      'Move to a single-column layout. It is the single highest-impact change available on most student resumes.',
      40, snapshotText(snapshot, 0, end), 0, end,
    ))
  } else {
    findings.push(pass(
      'SINGLE_COLUMN', 'PARSEABILITY', 'Reads as a single column',
      'Text came out in a sensible top-to-bottom order, which is what a screener will see.',
    ))
  }

  if (warnings.has('ENCRYPTED_DOCUMENT')) {
    deduct('PARSEABILITY', 30)
    findings.push(problem(
      'ENCRYPTED_DOCUMENT', 'PARSEABILITY', 'HIGH',
      'The document is protected',
      'This file carries password or permission restrictions. Many applicant tracking systems reject protected files outright rather than attempting to open them.',
      'Re-export without a password or usage restrictions.',
      30,
    ))
  }

  if (warnings.has('SPARSE_TEXT')) {
    deduct('PARSEABILITY', 35)
    findings.push(problem(
      'SPARSE_TEXT', 'PARSEABILITY', 'HIGH',
      'Very little readable text',
      `We found only ${snapshot.wordCount ?? 0} words. Content held in text boxes, images, or graphics does not survive parsing, so a visually full page can arrive nearly empty.`,
      'Put every claim in ordinary body text rather than in a graphic or a text box.',
      35,
    ))
  }

  const pages = snapshot.pageCount
  if (pages !== null && pages > 2) {
    const lost = Math.min(20, (pages - 2) * 8)
    deduct('PARSEABILITY', lost)
    findings.push(problem(
      'TOO_MANY_PAGES', 'PARSEABILITY', 'MEDIUM',
      `${pages} pages is long for this stage`,
      'Recruiters screening early-career applicants spend seconds per resume. Past two pages, the material at the end is rarely reached.',
      'Cut to one page if you have under three years of experience, two at most.',
      lost,
    ))
  } else if (pages !== null) {
    findings.push(pass(
      'LENGTH_APPROPRIATE', 'PARSEABILITY',
      `${pages} ${pages === 1 ? 'page' : 'pages'}`,
      'A length a recruiter will actually finish.',
    ))
  }

  const words = snapshot.wordCount ?? 0
  if (words > 0 && words < IDEAL_MIN_WORDS && !warnings.has('SPARSE_TEXT')) {
    deduct('PARSEABILITY', 15)
    findings.push(problem(
      'THIN_CONTENT', 'PARSEABILITY', 'MEDIUM',
      'Thin on detail',
      `${words} words is short. There is likely room to describe what you actually did on the projects already listed.`,
      'Aim for roughly 300-700 words. Add outcomes to the entries you already have rather than adding new entries.',
      15,
    ))
  } else if (words > IDEAL_MAX_WORDS) {
    deduct('PARSEABILITY', 10)
    findings.push(problem(
      'VERBOSE', 'PARSEABILITY', 'LOW',
      'Denser than a screener will read',
      `${words} words is a lot to scan. Density buries your strongest evidence among your weakest.`,
      'Cut the oldest and least relevant entries rather than shortening every bullet uniformly.',
      10,
    ))
  }
}

/* --- Structure ---------------------------------------------------- */

function evaluateStructure(
  snapshot: ResumeSnapshot,
  deduct: Deduct,
  findings: RuleFinding[],
): void {
  checkSection(snapshot, deduct, findings, 'EXPERIENCE', 'experience', 30, 'CRITICAL',
    'No section that a parser recognises as work experience was found. Many systems index candidates by employer and title; with no experience block, those fields come back empty.',
    'Add a heading spelled exactly "Experience", "Work Experience", or "Professional Experience". Internships and freelance work still belong under that heading.')

  checkSection(snapshot, deduct, findings, 'EDUCATION', 'education', 20, 'HIGH',
    'No education section was found. For early-career applicants this is often the field a filter is set on, so an unreadable one can fail a screen outright.',
    'Add a heading spelled "Education" with your institution, qualification, and dates.')

  checkSection(snapshot, deduct, findings, 'SKILLS', 'skills', 20, 'HIGH',
    'No skills section was found. Keyword matching leans heavily on this block, and skills mentioned only inside prose match far less reliably.',
    'Add a "Skills" heading listing the technologies you can actually be questioned on.')

  if (hasSection(snapshot, 'SUMMARY')) {
    findings.push(pass('HAS_SUMMARY', 'STRUCTURE', 'Opens with a summary',
      'A short opening statement gives a human reader somewhere to start.'))
  }

  const weak = snapshot.sections.find(
    (section) =>
      section.confidence < 70 &&
      section.headingText !== null &&
      (section.type === 'EXPERIENCE' || section.type === 'EDUCATION' || section.type === 'SKILLS'),
  )
  if (weak) {
    deduct('STRUCTURE', 10)
    findings.push(problem(
      'AMBIGUOUS_HEADING', 'STRUCTURE', 'MEDIUM',
      'A section heading is hard to recognise',
      `We identified "${weak.headingText}" as a section heading, but only barely. Creative headings such as "Where I've Been" read well to a person and match nothing in a parser's vocabulary.`,
      'Use the conventional word for the section. Save the personality for the bullets.',
      10, weak.headingText, weak.headingLine, weak.headingLine,
    ))
  }

  if (snapshot.experience.length > 0) {
    const undated = snapshot.experience.filter((e) => !e.startDate && !e.endDate)
    if (undated.length > 0) {
      const lost = Math.min(20, undated.length * 8)
      deduct('STRUCTURE', lost)
      const entry = undated[0]
      findings.push(problem(
        'UNDATED_EXPERIENCE', 'STRUCTURE', 'HIGH',
        `${undated.length} experience ${undated.length === 1 ? 'entry has' : 'entries have'} no readable dates`,
        `We could not read a date range for ${describeRole(entry)}. Systems that compute years of experience treat an undated role as zero.`,
        'Write dates in a plain format on the same line as the role, for example "Jun 2024 - Aug 2024". Avoid dates that live only in a sidebar or a graphic.',
        lost,
        evidenceFor(snapshot, entry.lineStart, entry.lineEnd), entry.lineStart, entry.lineEnd,
      ))
    } else {
      findings.push(pass('DATED_EXPERIENCE', 'STRUCTURE', 'Every role carries dates',
        'Date ranges parsed cleanly, so your years of experience compute correctly.'))
    }

    const gap = findTimelineGap(snapshot)
    if (gap) findings.push(gap)
  }
}

function checkSection(
  snapshot: ResumeSnapshot,
  deduct: Deduct,
  findings: RuleFinding[],
  type: 'EXPERIENCE' | 'EDUCATION' | 'SKILLS',
  label: string,
  cost: number,
  severity: AtsSeverity,
  detail: string,
  fix: string,
): void {
  const section = snapshotSection(snapshot, type)
  if (!section) {
    deduct('STRUCTURE', cost)
    findings.push(problem(`MISSING_${type}`, 'STRUCTURE', severity,
      `No ${label} section found`, detail, fix, cost))
  } else {
    findings.push(pass(`HAS_${type}`, 'STRUCTURE',
      `${label[0].toUpperCase()}${label.slice(1)} section found`,
      section.headingText
        ? `Recognised from the heading "${section.headingText}".`
        : 'Identified from position and content.'))
  }
}

/**
 * More than a year between roles. Reported, never judged — gaps have reasons,
 * and the point is that a reader will notice, so the candidate should decide
 * what to say first.
 */
function findTimelineGap(snapshot: ResumeSnapshot): RuleFinding | null {
  const dated = snapshot.experience
    .filter((entry) => entry.startDate)
    .sort((a, b) => (a.startDate! < b.startDate! ? -1 : 1))

  for (let i = 1; i < dated.length; i++) {
    const previous = dated[i - 1]
    const next = dated[i]
    if (!previous.endDate || previous.isCurrent) continue

    const previousEnd = new Date(previous.endDate)
    const nextStart = new Date(next.startDate!)
    const months =
      (nextStart.getFullYear() - previousEnd.getFullYear()) * 12 +
      (nextStart.getMonth() - previousEnd.getMonth())

    if (months > 13) {
      return problem(
        'TIMELINE_GAP', 'STRUCTURE', 'LOW',
        'A gap in the timeline',
        `There is more than a year between ${previous.endDate} and ${next.startDate}. A reader will notice it, so it is better to have decided what it says than to be asked cold.`,
        'If the time was spent studying, caring, travelling, or job-hunting, one dated line saying so removes the question entirely.',
        0, evidenceFor(snapshot, next.lineStart, next.lineEnd), next.lineStart, next.lineEnd,
      )
    }
  }
  return null
}

/* --- Content ------------------------------------------------------ */

function evaluateContent(
  snapshot: ResumeSnapshot,
  deduct: Deduct,
  findings: RuleFinding[],
): void {
  const bullets = bulletLines(snapshot)

  if (bullets.length === 0) {
    deduct('CONTENT', 45)
    findings.push(problem(
      'NO_BULLETS', 'CONTENT', 'HIGH',
      'No bullet points found',
      'Experience is written as prose or as bare lines. Recruiters scan; paragraphs are the first thing skipped, and a parser has no way to tell where one achievement ends and the next begins.',
      'Rewrite each role as three to five bullets, one achievement each.',
      45,
    ))
    return
  }

  const withVerb = bullets.filter((b) => startsWithActionVerb(b.text))
  const verbRatio = withVerb.length / bullets.length
  if (verbRatio < 0.6) {
    const lost = Math.round((0.6 - verbRatio) * 60)
    deduct('CONTENT', lost)
    const example = bullets.find((b) => !startsWithActionVerb(b.text)) ?? bullets[0]
    findings.push(problem(
      'WEAK_BULLET_OPENERS', 'CONTENT', 'HIGH',
      `Only ${percent(verbRatio)} of bullets open with an action verb`,
      'A bullet that opens with a verb states what you did. One that opens any other way usually states what existed around you.',
      'Start each bullet with the strongest true verb for it — built, migrated, reduced, automated, led — and put the object second.',
      lost, stripBullet(example.text), example.index, example.index,
    ))
  } else {
    findings.push(pass('STRONG_BULLET_OPENERS', 'CONTENT',
      `${percent(verbRatio)} of bullets open with an action verb`,
      'The writing reads as things you did rather than things you were near.'))
  }

  const quantified = bullets.filter((b) => isQuantified(b.text))
  const quantRatio = quantified.length / bullets.length
  if (quantRatio < 0.3) {
    const lost = Math.round((0.3 - quantRatio) * 90)
    deduct('CONTENT', lost)
    const example = bullets.find((b) => !isQuantified(b.text)) ?? bullets[0]
    findings.push(problem(
      'UNQUANTIFIED', 'CONTENT', 'HIGH',
      `Only ${percent(quantRatio)} of bullets contain a number`,
      "Unmeasured claims are indistinguishable from every other applicant's unmeasured claims. A number is the cheapest credibility available.",
      'Add scale or outcome to your strongest bullets: how many users, how much faster, how many records, how long it took, how many people.',
      lost, stripBullet(example.text), example.index, example.index,
    ))
  } else {
    findings.push(pass('QUANTIFIED', 'CONTENT',
      `${percent(quantRatio)} of bullets are quantified`,
      'Measured claims are what separate a resume from a wish list.'))
  }

  const weak = bullets.filter((b) => weakOpener(b.text) !== null)
  if (weak.length > 0) {
    const lost = Math.min(20, weak.length * 4)
    deduct('CONTENT', lost)
    const example = weak[0]
    findings.push(problem(
      'PASSIVE_PHRASING', 'CONTENT', 'MEDIUM',
      `${weak.length} bullet${weak.length === 1 ? '' : 's'} describe duties rather than results`,
      `Phrases like "${weakOpener(example.text)}" describe the job that existed, not what you did with it. They are copied from the job description you were given.`,
      'Replace the opener with the verb for what you actually produced, and add the outcome.',
      lost, stripBullet(example.text), example.index, example.index,
    ))
  }

  const firstPerson = bullets.filter((b) => firstPersonMarker(b.text) !== null)
  if (firstPerson.length > 0) {
    const lost = Math.min(10, firstPerson.length * 3)
    deduct('CONTENT', lost)
    const example = firstPerson[0]
    findings.push(problem(
      'FIRST_PERSON', 'CONTENT', 'LOW',
      'First-person pronouns in the bullets',
      'Resumes are conventionally written in an implied first person. "I built" reads as an essay; "Built" reads as a resume.',
      'Drop the pronoun and start with the verb.',
      lost, stripBullet(example.text), example.index, example.index,
    ))
  }

  const overlong = bullets.filter((b) => wordCount(b.text) > MAX_BULLET_WORDS)
  if (overlong.length > 0) {
    const lost = Math.min(12, overlong.length * 3)
    deduct('CONTENT', lost)
    const example = overlong[0]
    findings.push(problem(
      'OVERLONG_BULLETS', 'CONTENT', 'LOW',
      `${overlong.length} bullet${overlong.length === 1 ? ' is' : 's are'} longer than a scan survives`,
      `A bullet over ${MAX_BULLET_WORDS} words is a paragraph wearing a dot. The achievement inside it does not get read.`,
      'One achievement per bullet, under about 25 words. Split rather than trim.',
      lost, stripBullet(example.text), example.index, example.index,
    ))
  }
}

/**
 * Bullets from the experience, projects and achievements blocks only.
 *
 * Scanning the whole document would count a skills list written with dashes as
 * unquantified bullets, which is both wrong and confusing to read in a report.
 */
function bulletLines(snapshot: ResumeSnapshot): Array<{ index: number; text: string }> {
  const relevant = snapshot.sections.filter(
    (section) =>
      section.type === 'EXPERIENCE' ||
      section.type === 'PROJECTS' ||
      section.type === 'ACHIEVEMENTS',
  )
  const scope = relevant.length
    ? relevant
    : snapshot.sections.filter((s) => s.type !== 'SKILLS' && s.type !== 'CONTACT')

  const bullets: Array<{ index: number; text: string }> = []
  for (const section of scope) {
    const end = Math.min(section.endLine, snapshot.lines.length - 1)
    for (let i = Math.max(0, section.startLine); i <= end; i++) {
      const text = snapshotLine(snapshot, i)
      if (isBullet(text) && wordCount(text) >= 3) bullets.push({ index: i, text })
    }
  }
  return bullets
}

/* --- Skills ------------------------------------------------------- */

function evaluateSkills(
  snapshot: ResumeSnapshot,
  deduct: Deduct,
  findings: RuleFinding[],
): void {
  const count = snapshot.skills.length

  if (count === 0) {
    deduct('SKILLS', 100)
    findings.push(problem(
      'NO_SKILLS_DETECTED', 'SKILLS', 'CRITICAL',
      'No recognisable skills found',
      'We could not match a single technology, language, or tool. Keyword matching is the first filter most systems apply, and a resume with no matchable vocabulary fails it regardless of what the candidate can do.',
      'List the concrete technologies you have used by their usual names — the language, the framework, the database, the cloud, the tooling.',
      100,
    ))
    return
  }

  if (count < MIN_SKILLS) {
    const lost = (MIN_SKILLS - count) * 10
    deduct('SKILLS', lost)
    findings.push(problem(
      'FEW_SKILLS', 'SKILLS', 'MEDIUM',
      `Only ${count} skill${count === 1 ? '' : 's'} detected`,
      'A short skills list narrows the set of postings you can match at all. Named tools you have genuinely used are worth listing even when they feel minor.',
      `Aim for around ${HEALTHY_SKILLS} concrete, named technologies you could answer a question about.`,
      lost,
    ))
  } else if (count > KEYWORD_STUFFING_SKILLS) {
    deduct('SKILLS', 20)
    findings.push(problem(
      'KEYWORD_STUFFING', 'SKILLS', 'MEDIUM',
      `${count} skills is more than a list — it is a wall`,
      'Very long skill lists dilute the signal and invite an interviewer to pick the one you know least. A recruiter reads the first line and stops.',
      'Cut to the technologies you would be comfortable being questioned on, grouped by kind.',
      20,
    ))
  } else {
    findings.push(pass('SKILLS_PRESENT', 'SKILLS', `${count} skills detected`,
      'Enough matchable vocabulary for keyword screening to find you.'))
  }

  const categories = new Set(snapshot.skills.map((skill) => skill.category))
  const hasTechnicalDepth = categories.has('LANGUAGE') || categories.has('FRAMEWORK')

  if (!hasTechnicalDepth && count >= MIN_SKILLS) {
    deduct('SKILLS', 25)
    findings.push(problem(
      'NO_TECHNICAL_SKILLS', 'SKILLS', 'MEDIUM',
      'No programming languages or frameworks recognised',
      'The skills we found are tools and general concepts. For technical roles the language and framework names are what a filter is actually set on.',
      'Name the languages and frameworks explicitly, even the obvious ones.',
      25,
    ))
  } else if (categories.size >= 3) {
    findings.push(pass('VARIED_SKILLS', 'SKILLS',
      `Skills span ${categories.size} categories`,
      'Breadth across languages, frameworks, and tooling matches a wider set of postings.'))
  }
}

/* --- Contact ------------------------------------------------------ */

function evaluateContact(
  snapshot: ResumeSnapshot,
  deduct: Deduct,
  findings: RuleFinding[],
): void {
  const contact = snapshot.contact ?? EMPTY_CONTACT

  if (!contact.email) {
    deduct('CONTACT', 50)
    findings.push(problem(
      'NO_EMAIL', 'CONTACT', 'CRITICAL',
      'No email address found',
      'We could not extract an email address. If it is in a header, a footer, or a graphic, many parsers will not see it either — and a candidate with no contact field is unreachable no matter how good the rest is.',
      'Put your email in the body of the first few lines, as plain text.',
      50,
    ))
  } else {
    findings.push(pass('HAS_EMAIL', 'CONTACT', 'Email address found',
      `${contact.email} parsed cleanly from the contact block.`))
  }

  if (!contact.phone) {
    deduct('CONTACT', 20)
    findings.push(problem(
      'NO_PHONE', 'CONTACT', 'MEDIUM', 'No phone number found',
      'Some recruiters call before they email, and some systems require the field.',
      'Add a phone number in plain text with its country code.', 20,
    ))
  }

  if (!contact.fullName) {
    deduct('CONTACT', 20)
    findings.push(problem(
      'NO_NAME', 'CONTACT', 'HIGH', 'Could not identify your name',
      'The name is usually the first strong line of a resume. If it is set as an image, a logo, or a page header, the parsed record has an empty name field.',
      'Put your name as ordinary text on the first line.', 20,
    ))
  }

  if (!contact.linkedinUrl && !contact.githubUrl && !contact.portfolioUrl) {
    deduct('CONTACT', 15)
    findings.push(problem(
      'NO_PROFILE_LINKS', 'CONTACT', 'LOW',
      'No LinkedIn, GitHub, or portfolio link',
      'A link is the one place a reader can verify a claim themselves. For technical roles a GitHub profile does more work than another bullet.',
      'Add the profile links that show your work, written out as full URLs.', 15,
    ))
  } else {
    findings.push(pass('HAS_PROFILE_LINKS', 'CONTACT', 'Profile link found',
      'A reader can check your work rather than take it on trust.'))
  }

  if (!contact.location) {
    deduct('CONTACT', 10)
    findings.push(problem(
      'NO_LOCATION', 'CONTACT', 'LOW', 'No location found',
      'Location filters are common, and a blank location is often treated as a non-match rather than as unknown.',
      'Add your city and country. A full street address is not needed and is better left off.', 10,
    ))
  }
}

/* --- Helpers ------------------------------------------------------ */

const percent = (ratio: number) => `${Math.round(ratio * 100)}%`

function evidenceFor(
  snapshot: ResumeSnapshot,
  start: number | null,
  end: number | null,
): string | null {
  if (start === null) return null
  return snapshotText(snapshot, start, end ?? start)
}

function describeRole(entry: SnapshotExperience): string {
  if (entry.jobTitle && entry.company) return `"${entry.jobTitle}" at ${entry.company}`
  if (entry.jobTitle) return `"${entry.jobTitle}"`
  if (entry.company) return entry.company
  return 'one of your roles'
}
