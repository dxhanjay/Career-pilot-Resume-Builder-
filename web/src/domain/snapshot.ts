import type { SectionType } from './sections'
import type { SkillCategory } from './skills'

/**
 * Everything the rule engines are allowed to look at.
 *
 * One read model, built once per request and shared by ATS scoring, job
 * matching, and interview generation. Two mappings would be two chances for the
 * same resume to have a skill one report counts and another does not.
 */
export interface ResumeSnapshot {
  readonly lines: readonly string[]
  readonly sections: readonly SnapshotSection[]
  readonly contact: SnapshotContact
  readonly skills: readonly SnapshotSkill[]
  readonly education: readonly SnapshotEducation[]
  readonly experience: readonly SnapshotExperience[]
  readonly warningCodes: ReadonlySet<string>
  readonly pageCount: number | null
  readonly wordCount: number | null
  readonly charCount: number | null
}

export interface SnapshotSection {
  readonly type: SectionType
  readonly headingText: string | null
  readonly headingLine: number
  readonly startLine: number
  readonly endLine: number
  readonly confidence: number
}

export interface SnapshotContact {
  readonly fullName: string | null
  readonly email: string | null
  readonly phone: string | null
  readonly location: string | null
  readonly linkedinUrl: string | null
  readonly githubUrl: string | null
  readonly portfolioUrl: string | null
}

export interface SnapshotSkill {
  readonly name: string
  readonly normalizedName: string
  readonly category: SkillCategory
  readonly confidence: number
  readonly lineStart: number | null
}

export interface SnapshotEducation {
  readonly institution: string | null
  readonly degree: string | null
  readonly fieldOfStudy: string | null
  readonly startDate: string | null
  readonly endDate: string | null
  readonly lineStart: number | null
  readonly lineEnd: number | null
}

export interface SnapshotExperience {
  readonly company: string | null
  readonly jobTitle: string | null
  readonly startDate: string | null
  readonly endDate: string | null
  readonly isCurrent: boolean
  readonly description: string | null
  readonly lineStart: number | null
  readonly lineEnd: number | null
}

export const EMPTY_CONTACT: SnapshotContact = {
  fullName: null,
  email: null,
  phone: null,
  location: null,
  linkedinUrl: null,
  githubUrl: null,
  portfolioUrl: null,
}

/** The text of a line, or empty when the index is out of range. */
export function snapshotLine(snapshot: ResumeSnapshot, index: number | null): string {
  if (index === null || index < 0 || index >= snapshot.lines.length) return ''
  return snapshot.lines[index]
}

export function snapshotText(
  snapshot: ResumeSnapshot,
  start: number,
  end: number,
): string {
  const from = Math.max(0, start)
  const to = Math.min(snapshot.lines.length - 1, end)
  if (from > to) return ''
  return snapshot.lines.slice(from, to + 1).join('\n').trim()
}

export function hasSection(snapshot: ResumeSnapshot, type: SectionType): boolean {
  return snapshot.sections.some((section) => section.type === type)
}

export function snapshotSection(
  snapshot: ResumeSnapshot,
  type: SectionType,
): SnapshotSection | undefined {
  return snapshot.sections.find((section) => section.type === type)
}
