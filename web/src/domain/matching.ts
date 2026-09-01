import { type ResumeSnapshot, snapshotLine } from './snapshot'
import { type SkillCategory, findSkillsIn, impliedBy, skillLabel } from './skills'
import { indexOfToken, isBullet, stripBullet, toLineModel, wordCount } from './text'

/**
 * Comparing a resume against one job posting.
 *
 * Requirement-level matching: the unit of comparison is one stated requirement,
 * not the document as a whole. A similarity score between two bags of words
 * produces a number nobody can act on; "this posting asks for Docker three times
 * and your resume never says it" produces an evening's work.
 */

export const MATCH_VERSION = '1.0.0'

export type MatchBand = 'WEAK' | 'PARTIAL' | 'PROMISING' | 'STRONG'
export type SkillVerdict = 'MATCHED' | 'MISSING' | 'EXTRA'

/** Phrased as advice about whether to apply, the only decision this feeds. */
export const MATCH_BANDS: Record<MatchBand, { label: string; advice: string }> = {
  WEAK: {
    label: 'Weak match',
    advice:
      'This posting asks for a different profile. Applying costs little, but your effort is better spent on roles closer to what you have.',
  },
  PARTIAL: {
    label: 'Partial match',
    advice:
      'Some overlap, several hard gaps. Worth applying only if you can close a gap or two first.',
  },
  PROMISING: {
    label: 'Promising match',
    advice:
      'You clear most of what this asks for. Close the top gaps and this becomes a strong application.',
  },
  STRONG: {
    label: 'Strong match',
    advice:
      "You cover what this posting asks for. Make sure the resume says so in the posting's own words.",
  },
}

export function matchBandFor(score: number): MatchBand {
  if (score >= 80) return 'STRONG'
  if (score >= 60) return 'PROMISING'
  if (score >= 35) return 'PARTIAL'
  return 'WEAK'
}

/* ------------------------------------------------------------------ */
/* Reading a posting                                                  */
/* ------------------------------------------------------------------ */

/** Headings under which everything is treated as a hard requirement. */
const REQUIRED_HEADING =
  /^\s*(requirements?|required|must[- ]?haves?|qualifications?|minimum qualifications|what (you|we)('| a)?ll need|basic qualifications|essential|you have|who you are)\b.{0,40}$/i

/** Headings marking the optional half of a posting. */
const OPTIONAL_HEADING =
  /^\s*(nice[- ]to[- ]haves?|preferred|preferred qualifications|bonus|desirable|pluses?|good to have|advantageous|it would be great)\b.{0,40}$/i

/** In-line softeners that demote a skill even under a hard heading. */
const OPTIONAL_PHRASE =
  /(nice to have|a plus|bonus|preferred|desirable|would be great|familiarity with|exposure to|any of|advantage)/i

/** In-line intensifiers that promote a skill even under a soft heading. */
const REQUIRED_PHRASE =
  /(must have|required|strong (experience|knowledge|proficiency)|proven (experience|track)|solid (experience|understanding)|expertise in|proficient in|deep knowledge)/i

const YEARS = /(\d{1,2})\s*\+?\s*(?:-\s*\d{1,2}\s*)?(?:years?|yrs?)\b/gi

const SENIORITY: ReadonlyArray<[string, string]> = [
  ['internship', 'Internship'], ['intern', 'Internship'], ['graduate', 'Graduate'],
  ['entry level', 'Entry level'], ['entry-level', 'Entry level'], ['junior', 'Junior'],
  ['associate', 'Associate'], ['mid-level', 'Mid-level'], ['senior', 'Senior'],
  ['staff', 'Staff'], ['principal', 'Principal'], ['lead', 'Lead'],
  ['head of', 'Leadership'], ['director', 'Leadership'],
]

export interface RequiredSkill {
  readonly normalizedName: string
  readonly displayName: string
  readonly category: SkillCategory
  readonly required: boolean
  /** How many lines mention it. Repetition raises gap priority. */
  readonly mentions: number
  readonly line: number
  readonly evidence: string
}

export interface JobPosting {
  readonly lines: readonly string[]
  readonly skills: readonly RequiredSkill[]
  readonly detectedTitle: string | null
  readonly minimumYears: number | null
  readonly seniority: string | null
}

/**
 * How badly a gap hurts, 0-100.
 *
 * Required beats optional; repeated beats mentioned once. A concrete language or
 * framework outranks a soft skill, because a filter can be set on the first and
 * rarely is on the second.
 */
export function gapPriority(skill: RequiredSkill): number {
  let score = skill.required ? 60 : 25
  score += Math.min(20, (skill.mentions - 1) * 7)
  switch (skill.category) {
    case 'LANGUAGE':
    case 'FRAMEWORK':
      score += 15
      break
    case 'DATABASE':
    case 'CLOUD_DEVOPS':
      score += 10
      break
    case 'TOOL':
      score += 5
      break
    default:
      break
  }
  return Math.min(100, score)
}

export function parseJobPosting(rawText: string | null | undefined): JobPosting {
  if (!rawText || !rawText.trim()) {
    return { lines: [], skills: [], detectedTitle: null, minimumYears: null, seniority: null }
  }

  const model = toLineModel(rawText)
  const lines = model.lines.map((line) => line.text)
  const found = new Map<string, RequiredSkill>()
  let inOptionalBlock = false

  for (let i = 0; i < lines.length; i++) {
    const stripped = lines[i].trim()

    if (REQUIRED_HEADING.test(stripped)) {
      inOptionalBlock = false
      continue
    }
    if (OPTIONAL_HEADING.test(stripped)) {
      inOptionalBlock = true
      continue
    }

    let required = !inOptionalBlock
    if (OPTIONAL_PHRASE.test(stripped)) required = false
    if (REQUIRED_PHRASE.test(stripped)) required = true

    // Ambiguous lexicon entries are included: a posting is dense with
    // technology names and the surrounding words disambiguate far better than
    // they do in a resume's prose.
    for (const hit of findSkillsIn(lines[i], true)) {
      const canonical = hit.entry.canonical
      const existing = found.get(canonical)

      if (!existing) {
        found.set(canonical, {
          normalizedName: canonical,
          displayName: canonical,
          category: hit.entry.category,
          required,
          mentions: 1,
          line: i,
          evidence: stripped,
        })
      } else {
        // Repetition is the posting telling you what it cares about.
        found.set(canonical, {
          ...existing,
          required: existing.required || required,
          mentions: existing.mentions + 1,
        })
      }
    }
  }

  return {
    lines,
    skills: [...found.values()],
    detectedTitle: detectTitle(lines),
    minimumYears: detectYears(rawText),
    seniority: detectSeniority(rawText),
  }
}

/** The first substantial line. Postings almost always open with the role name. */
function detectTitle(lines: readonly string[]): string | null {
  return (
    lines
      .map((line) => line.trim())
      .find((line) => line.length >= 3 && line.length <= 90 && /\p{L}/u.test(line)) ?? null
  )
}

function detectYears(rawText: string): number | null {
  let lowest: number | null = null
  for (const match of rawText.matchAll(YEARS)) {
    const years = Number(match[1])
    // 20+ years is a date range or a company age, not a requirement.
    if (years >= 1 && years <= 20 && (lowest === null || years < lowest)) lowest = years
  }
  return lowest
}

function detectSeniority(rawText: string): string | null {
  // Only the opening, where the title lives. "senior" in the tenth bullet
  // describes a colleague, not the role.
  const head = rawText.toLowerCase().slice(0, 400)
  return SENIORITY.find(([needle]) => head.includes(needle))?.[1] ?? null
}

/* ------------------------------------------------------------------ */
/* Comparing                                                          */
/* ------------------------------------------------------------------ */

const WEIGHT_REQUIRED_SKILLS = 55
const WEIGHT_OPTIONAL_SKILLS = 15
const WEIGHT_TITLE = 15
const WEIGHT_EXPERIENCE = 15

/** Above this many unasked-for skills, the resume is aimed elsewhere. */
const UNFOCUSED_EXTRA_SKILLS = 15
const MAX_SUGGESTIONS = 8

export interface SkillComparison {
  readonly normalizedName: string
  readonly displayName: string
  readonly category: SkillCategory
  readonly verdict: SkillVerdict
  readonly required: boolean
  readonly priority: number
  readonly resumeEvidence: string | null
  readonly resumeLine: number | null
  readonly jdEvidence: string | null
  readonly jdLine: number | null
}

export type SuggestionKind =
  | 'SURFACE_SKILL'
  | 'REPHRASE'
  | 'QUANTIFY'
  | 'MIRROR_TITLE'
  | 'LEARN'

/**
 * A grounded improvement suggestion.
 *
 * The system improves how the truth is expressed and never invents experience.
 * Every suggestion quotes text that already exists in the resume (`before`) and
 * proposes a rewrite of that text (`after`), with any figure the candidate must
 * supply left as an explicit placeholder rather than guessed at.
 */
export interface Suggestion {
  readonly kind: SuggestionKind
  readonly title: string
  readonly rationale: string
  readonly before: string | null
  readonly after: string | null
  readonly line: number | null
}

export interface MatchOutcome {
  readonly overallScore: number
  readonly band: MatchBand
  readonly requiredSkillScore: number
  readonly optionalSkillScore: number
  readonly titleScore: number
  readonly experienceScore: number
  readonly skills: readonly SkillComparison[]
  readonly suggestions: readonly Suggestion[]
  readonly rubricVersion: string
}

export function matchResume(snapshot: ResumeSnapshot, posting: JobPosting): MatchOutcome {
  const resumeSkills = new Map<string, (typeof snapshot.skills)[number]>()
  for (const skill of snapshot.skills) {
    if (!resumeSkills.has(skill.normalizedName)) resumeSkills.set(skill.normalizedName, skill)
  }

  const comparisons: SkillComparison[] = []
  const askedFor = new Set<string>()

  let requiredTotal = 0
  let requiredMet = 0
  let optionalTotal = 0
  let optionalMet = 0

  for (const wanted of posting.skills) {
    askedFor.add(wanted.normalizedName)

    const have = resumeSkills.get(wanted.normalizedName)

    // A skill can be present in the prose without reaching the skills block.
    // That is a real finding — the candidate has it and is not being credited —
    // so it counts as a match and produces a "surface this" suggestion.
    const prose = have ? null : findInText(snapshot, wanted.displayName)

    // And a skill can be demonstrated by another one. A resume listing
    // PostgreSQL is not missing SQL, and saying so costs the user's trust in
    // every other gap on the list.
    const implier = have || prose !== null ? null : impliedBy(wanted.normalizedName, resumeSkills.keys())
    const impliedSkill = implier ? resumeSkills.get(implier) : undefined

    const matched = Boolean(have) || prose !== null || Boolean(impliedSkill)

    if (wanted.required) {
      requiredTotal++
      if (matched) requiredMet++
    } else {
      optionalTotal++
      if (matched) optionalMet++
    }

    const resumeLine = have ? have.lineStart : (prose ?? impliedSkill?.lineStart ?? null)

    comparisons.push({
      normalizedName: wanted.normalizedName,
      displayName: have ? have.name : skillLabel(wanted.displayName),
      category: wanted.category,
      verdict: matched ? 'MATCHED' : 'MISSING',
      required: wanted.required,
      priority: gapPriority(wanted),
      resumeEvidence: resumeLine !== null ? snapshotLine(snapshot, resumeLine).trim() : null,
      resumeLine,
      jdEvidence: wanted.evidence,
      jdLine: wanted.line,
    })
  }

  for (const skill of resumeSkills.values()) {
    if (askedFor.has(skill.normalizedName)) continue
    comparisons.push({
      normalizedName: skill.normalizedName,
      displayName: skill.name,
      category: skill.category,
      verdict: 'EXTRA',
      required: false,
      priority: 0,
      resumeEvidence:
        skill.lineStart !== null ? snapshotLine(snapshot, skill.lineStart).trim() : null,
      resumeLine: skill.lineStart,
      jdEvidence: null,
      jdLine: null,
    })
  }

  const requiredSkillScore = ratioScore(requiredMet, requiredTotal)
  const optionalSkillScore = ratioScore(optionalMet, optionalTotal)
  const titleScore = scoreTitle(snapshot, posting)
  const experienceScore = scoreExperience(snapshot, posting)

  const overallScore = Math.round(
    requiredSkillScore * (WEIGHT_REQUIRED_SKILLS / 100) +
      optionalSkillScore * (WEIGHT_OPTIONAL_SKILLS / 100) +
      titleScore * (WEIGHT_TITLE / 100) +
      experienceScore * (WEIGHT_EXPERIENCE / 100),
  )

  const verdictOrder: Record<SkillVerdict, number> = { MATCHED: 0, MISSING: 1, EXTRA: 2 }
  comparisons.sort(
    (a, b) =>
      verdictOrder[a.verdict] - verdictOrder[b.verdict] ||
      b.priority - a.priority ||
      a.displayName.localeCompare(b.displayName),
  )

  return {
    overallScore,
    band: matchBandFor(overallScore),
    requiredSkillScore,
    optionalSkillScore,
    titleScore,
    experienceScore,
    skills: comparisons,
    suggestions: suggest(snapshot, posting, comparisons, titleScore),
    rubricVersion: MATCH_VERSION,
  }
}

/**
 * A posting that states no requirements of a given kind scores 100 for it, not
 * 0. The candidate has failed nothing; the posting simply did not ask.
 */
const ratioScore = (met: number, total: number) =>
  total === 0 ? 100 : Math.round((100 * met) / total)

/**
 * Token overlap between the posting's title and the candidate's role titles.
 *
 * Deliberately generous. An intern whose title was "Software Engineering Intern"
 * applying to "Software Engineer" should not be told the titles do not match;
 * the words that matter are the same.
 */
function scoreTitle(snapshot: ResumeSnapshot, posting: JobPosting): number {
  const target = posting.detectedTitle
  if (!target || snapshot.experience.length === 0) return 50

  const wanted = significantTokens(target)
  if (wanted.size === 0) return 50

  let best = 0
  for (const experience of snapshot.experience) {
    if (!experience.jobTitle) continue
    const have = significantTokens(experience.jobTitle)
    const shared = [...wanted].filter((token) => have.has(token)).length
    best = Math.max(best, Math.round((100 * shared) / wanted.size))
  }
  return best
}

/**
 * Years of experience against the posting's stated minimum.
 *
 * Overlapping roles are not double-counted: two concurrent part-time jobs are
 * not four years, and a candidate told they have four will be caught out.
 */
function scoreExperience(snapshot: ResumeSnapshot, posting: JobPosting): number {
  const required = posting.minimumYears
  if (required === null || required === 0) return 100

  const actual = totalYears(snapshot)
  if (actual >= required) return 100
  return Math.max(0, Math.round((100 * actual) / required))
}

function totalYears(snapshot: ResumeSnapshot): number {
  const dated = snapshot.experience
    .filter((entry) => entry.startDate)
    .sort((a, b) => (a.startDate! < b.startDate! ? -1 : 1))

  let months = 0
  let coveredTo: Date | null = null

  for (const entry of dated) {
    let start = new Date(entry.startDate!)
    const end = entry.endDate
      ? new Date(entry.endDate)
      : entry.isCurrent
        ? new Date()
        : new Date(entry.startDate!)

    if (end < start) continue
    if (coveredTo && start < coveredTo) {
      start = coveredTo
      if (end < start) continue
    }

    months +=
      (end.getFullYear() - start.getFullYear()) * 12 + (end.getMonth() - start.getMonth())
    if (!coveredTo || end > coveredTo) coveredTo = end
  }

  return months / 12
}

/* ------------------------------------------------------------------ */
/* Suggestions                                                        */
/* ------------------------------------------------------------------ */

function suggest(
  snapshot: ResumeSnapshot,
  posting: JobPosting,
  comparisons: readonly SkillComparison[],
  titleScore: number,
): Suggestion[] {
  const suggestions: Suggestion[] = []
  const hasSkillsSection = snapshot.sections.some((section) => section.type === 'SKILLS')

  // 1. Skills the candidate demonstrably has that never reach the skills block.
  //    The cheapest possible win: no new claim, better placement.
  const listedSkills = new Set(snapshot.skills.map((skill) => skill.normalizedName))
  comparisons
    .filter((c) => c.verdict === 'MATCHED' && c.resumeLine !== null)
    .filter((c) => !inSkillsSection(snapshot, c.resumeLine!))
    // Never suggest moving something that is already in the skills list. The
    // skill may have been *detected* on a bullet, but if the lexicon also found
    // it in the skills block there is nothing to move.
    .filter((c) => !(listedSkills.has(c.normalizedName) && inSkillsSectionAnywhere(snapshot, c.normalizedName)))
    .slice(0, 3)
    .forEach((c) => {
      suggestions.push({
        kind: 'SURFACE_SKILL',
        title: `Move "${c.displayName}" into your skills section`,
        rationale:
          `This posting asks for ${c.displayName} and you have used it — but it only appears inside a bullet. ` +
          'Keyword screening leans on the skills block, so a skill mentioned only in prose matches less reliably than one listed.' +
          (hasSkillsSection ? '' : ' You have no skills section at all yet.'),
        before: c.resumeEvidence,
        after: `Add "${c.displayName}" to your skills list, keeping the bullet as the evidence for it.`,
        line: c.resumeLine,
      })
    })

  // 2. Top gaps. Never phrased as "add this" — that would be an invitation to
  //    fabricate. Phrased as what closing it would take.
  comparisons
    .filter((c) => c.verdict === 'MISSING' && c.required)
    .sort((a, b) => b.priority - a.priority)
    .slice(0, 3)
    .forEach((c) => {
      suggestions.push({
        kind: 'LEARN',
        title: `Gap: ${c.displayName}`,
        rationale:
          'The posting asks for this and nothing in your resume shows it. If you have used it and simply did not write it down, add it with the project that proves it. If you have not, a small public project is a faster route to a truthful line than a course certificate.',
        before: null,
        after: null,
        line: c.jdLine,
      })
    })

  // 3. Bullets already describing relevant work but stating no outcome. The
  //    rewrite leaves the metric as a placeholder: the candidate is the only
  //    party who knows the number, and guessing one is fabrication.
  const vocabulary = new Set(posting.skills.map((skill) => skill.normalizedName))
  for (let i = 0; i < snapshot.lines.length && suggestions.length < MAX_SUGGESTIONS; i++) {
    const line = snapshotLine(snapshot, i)
    if (!isBullet(line) || wordCount(line) < 5) continue
    if (/\d/.test(line)) continue

    const lower = line.toLowerCase()
    const relevant = [...vocabulary].some((skill) => indexOfToken(lower, skill) >= 0)
    if (!relevant) continue

    const stripped = stripBullet(line)
    suggestions.push({
      kind: 'QUANTIFY',
      title: 'Quantify a bullet this posting cares about',
      rationale:
        'This bullet covers something the posting explicitly asks for, which makes it one of your strongest — and it states no outcome. A number here is worth more than a number anywhere else on the page.',
      before: stripped,
      after: `${stripped} — [add the scale or result: how many users, how much faster, how many records, over what period]`,
      line: i,
    })
    break
  }

  // 4. Title mirroring: the posting's own words applied to the candidate's own
  //    summary line, not a new claim.
  if (titleScore < 50 && posting.detectedTitle) {
    suggestions.push({
      kind: 'MIRROR_TITLE',
      title: "Mirror the posting's job title",
      rationale:
        "Your titles and this posting's title share few words. Recruiters and filters both scan for the role name. Where it is truthful, using the posting's vocabulary for work you have actually done costs nothing and matches better.",
      before: null,
      after: `Consider a one-line summary naming the target role, for example: "Aspiring ${posting.detectedTitle.trim()}" — only if it describes what you are genuinely aiming at.`,
      line: null,
    })
  }

  // 5. A resume aimed at a different role entirely.
  const extras = comparisons.filter((c) => c.verdict === 'EXTRA').length
  if (extras > UNFOCUSED_EXTRA_SKILLS) {
    suggestions.push({
      kind: 'REPHRASE',
      title: 'This resume is aimed wider than this posting',
      rationale: `${extras} of your listed skills are never mentioned in this posting. A general-purpose resume reads as unfocused against a specific role.`,
      before: null,
      after: 'Lead with the skills this posting names and move the rest down, rather than removing them.',
      line: null,
    })
  }

  return suggestions.slice(0, MAX_SUGGESTIONS)
}

/** Finds a skill name in the resume text when it never reached the skills block. */
function findInText(snapshot: ResumeSnapshot, skillName: string): number | null {
  const needle = skillName.toLowerCase()
  for (let i = 0; i < snapshot.lines.length; i++) {
    if (indexOfToken(snapshot.lines[i].toLowerCase(), needle) >= 0) return i
  }
  return null
}

/** Whether the skills block itself mentions this skill, wherever it was recorded. */
function inSkillsSectionAnywhere(snapshot: ResumeSnapshot, normalizedName: string): boolean {
  const section = snapshot.sections.find((s) => s.type === 'SKILLS')
  if (!section) return false
  for (let i = Math.max(0, section.startLine); i <= Math.min(section.endLine, snapshot.lines.length - 1); i++) {
    if (indexOfToken(snapshot.lines[i].toLowerCase(), normalizedName) >= 0) return true
  }
  return false
}

function inSkillsSection(snapshot: ResumeSnapshot, line: number): boolean {
  return snapshot.sections.some(
    (section) =>
      section.type === 'SKILLS' && line >= section.startLine && line <= section.endLine,
  )
}

/** Words worth comparing in a job title — stop words removed. */
function significantTokens(title: string): Set<string> {
  const stop = new Set([
    'a', 'an', 'the', 'and', 'or', 'of', 'for', 'to', 'in', 'at', 'with', 'on',
    'i', 'ii', 'iii', '1', '2', '3',
  ])
  const tokens = new Set<string>()
  for (const token of title.toLowerCase().split(/[^\p{L}\p{N}+#.]+/u)) {
    if (token.length > 1 && !stop.has(token)) tokens.add(token)
  }
  return tokens
}
