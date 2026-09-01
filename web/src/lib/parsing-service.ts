import { and, desc, eq } from 'drizzle-orm'
import { db } from '@/db'
import {
  parsedContacts,
  parsedEducation,
  parsedExperience,
  parsedSkills,
  resumeParses,
  resumes,
} from '@/db/schema'
import {
  contactIsEmpty,
  educationIsEmpty,
  experienceIsEmpty,
  extractContact,
  extractEducation,
  extractExperience,
  extractSkills,
} from '@/domain/extract'
import { extractText, isUsable, type ParseWarning } from '@/domain/parse'
import { segmentSections, type SectionType } from '@/domain/sections'
import type { ResumeSnapshot } from '@/domain/snapshot'
import { EMPTY_CONTACT } from '@/domain/snapshot'
import type { SkillCategory } from '@/domain/skills'
import { countWords } from '@/domain/parse'
import { toLineModel } from '@/domain/text'
import { conflict, notFound } from './api'

/**
 * Parsing, and storing what was read.
 *
 * Runs synchronously inside the upload request rather than through a queue.
 * Extraction of a five-page PDF takes well under a second, and a background
 * worker would mean a job table, a poller, a process to run it, and a client
 * that has to poll for a result that was ready before the response would have
 * been written. Serverless has nowhere to put the worker anyway.
 */

/** A skills list longer than this is keyword stuffing, not skill. */
const MAX_SKILLS = 120

export interface ParseResult {
  readonly parseId: string
  readonly status: 'SUCCEEDED' | 'FAILED'
  readonly warnings: ParseWarning[]
  readonly error: string | null
}

/**
 * Extracts text from a stored resume and writes everything read from it.
 *
 * Idempotent per call: a re-parse inserts a new parse row with its own
 * children. Old parses are kept because a failed parse is the evidence for
 * telling a user their file is a scan.
 */
export async function parseResume(resumeId: string, userId: string): Promise<ParseResult> {
  const [resume] = await db
    .select()
    .from(resumes)
    .where(and(eq(resumes.id, resumeId), eq(resumes.userId, userId)))
    .limit(1)

  if (!resume) throw notFound('Resume')

  const startedAt = Date.now()
  await db
    .update(resumes)
    .set({ status: 'PARSING', updatedAt: new Date() })
    .where(eq(resumes.id, resumeId))

  let extracted
  try {
    extracted = await extractText(new Uint8Array(resume.content), resume.mimeType)
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Could not read the file'
    const [failed] = await db
      .insert(resumeParses)
      .values({
        resumeId, userId, status: 'FAILED', parser: 'UNKNOWN',
        errorMessage: message, warnings: '[]',
        durationMs: Date.now() - startedAt,
      })
      .returning({ id: resumeParses.id })

    await db
      .update(resumes)
      .set({ status: 'PARSE_FAILED', updatedAt: new Date() })
      .where(eq(resumes.id, resumeId))

    return { parseId: failed.id, status: 'FAILED', warnings: [], error: message }
  }

  if (!isUsable(extracted)) {
    const message =
      'No usable text could be extracted. This is almost always a scanned image or a photo saved as a PDF.'

    const [failed] = await db
      .insert(resumeParses)
      .values({
        resumeId, userId, status: 'FAILED', parser: extracted.parser,
        rawText: extracted.rawText, pageCount: extracted.pageCount,
        wordCount: countWords(extracted.rawText),
        charCount: extracted.rawText.length,
        warnings: JSON.stringify(extracted.warnings),
        errorMessage: message,
        durationMs: Date.now() - startedAt,
      })
      .returning({ id: resumeParses.id })

    await db
      .update(resumes)
      .set({ status: 'PARSE_FAILED', updatedAt: new Date() })
      .where(eq(resumes.id, resumeId))

    return {
      parseId: failed.id,
      status: 'FAILED',
      warnings: extracted.warnings,
      error: message,
    }
  }

  const [parse] = await db
    .insert(resumeParses)
    .values({
      resumeId, userId, status: 'SUCCEEDED', parser: extracted.parser,
      rawText: extracted.rawText, pageCount: extracted.pageCount,
      wordCount: countWords(extracted.rawText),
      charCount: extracted.rawText.length,
      warnings: JSON.stringify(extracted.warnings),
      durationMs: Date.now() - startedAt,
    })
    .returning({ id: resumeParses.id })

  await writeStructure(parse.id, userId, extracted.rawText)

  await db
    .update(resumes)
    .set({ status: 'PARSED', updatedAt: new Date() })
    .where(eq(resumes.id, resumeId))

  return { parseId: parse.id, status: 'SUCCEEDED', warnings: extracted.warnings, error: null }
}

/**
 * Runs the extraction cascade and stores what it found.
 *
 * Extraction failure is never fatal. A resume whose text came out but whose
 * sections could not be identified is still worth showing: the raw text is the
 * evidence, and a partial structure beats a failed parse.
 */
async function writeStructure(parseId: string, userId: string, rawText: string): Promise<void> {
  const model = toLineModel(rawText)
  if (!model.lines.length) return

  const sections = segmentSections(model)

  const contact = extractContact(model, sections)
  if (!contactIsEmpty(contact)) {
    await db.insert(parsedContacts).values({
      parseId, userId,
      fullName: contact.fullName, email: contact.email, phone: contact.phone,
      location: contact.location, linkedinUrl: contact.linkedinUrl,
      githubUrl: contact.githubUrl, portfolioUrl: contact.portfolioUrl,
      confidence: contact.confidence,
      lineStart: contact.lineStart, lineEnd: contact.lineEnd,
    })
  }

  const skills = extractSkills(model, sections).slice(0, MAX_SKILLS)
  if (skills.length) {
    await db.insert(parsedSkills).values(
      skills.map((skill) => ({
        parseId, userId,
        name: skill.name.slice(0, 100),
        normalizedName: skill.normalizedName.slice(0, 100),
        category: skill.category,
        confidence: skill.confidence,
        lineStart: skill.lineStart,
      })),
    )
  }

  const education = extractEducation(model, sections).filter((entry) => !educationIsEmpty(entry))
  if (education.length) {
    await db.insert(parsedEducation).values(
      education.map((entry) => ({
        parseId, userId,
        institution: entry.institution, degree: entry.degree,
        fieldOfStudy: entry.fieldOfStudy,
        startDate: entry.startDate, endDate: entry.endDate, grade: entry.grade,
        confidence: entry.confidence,
        lineStart: entry.lineStart, lineEnd: entry.lineEnd,
      })),
    )
  }

  const experience = extractExperience(model, sections).filter(
    (entry) => !experienceIsEmpty(entry),
  )
  if (experience.length) {
    await db.insert(parsedExperience).values(
      experience.map((entry) => ({
        parseId, userId,
        company: entry.company, jobTitle: entry.jobTitle,
        startDate: entry.startDate, endDate: entry.endDate,
        isCurrent: entry.isCurrent, description: entry.description,
        confidence: entry.confidence,
        lineStart: entry.lineStart, lineEnd: entry.lineEnd,
      })),
    )
  }
}

/* ------------------------------------------------------------------ */
/* Reading it back                                                    */
/* ------------------------------------------------------------------ */

export async function latestParse(resumeId: string, userId: string, successfulOnly = true) {
  const conditions = successfulOnly
    ? and(
        eq(resumeParses.resumeId, resumeId),
        eq(resumeParses.userId, userId),
        eq(resumeParses.status, 'SUCCEEDED'),
      )
    : and(eq(resumeParses.resumeId, resumeId), eq(resumeParses.userId, userId))

  const [parse] = await db
    .select()
    .from(resumeParses)
    .where(conditions)
    .orderBy(desc(resumeParses.createdAt))
    .limit(1)

  return parse
}

export interface StructuredParse {
  readonly parseId: string
  readonly resumeId: string
  readonly lines: string[]
  readonly sections: Array<{
    type: SectionType
    displayName: string
    headingText: string | null
    headingLine: number
    startLine: number
    endLine: number
    confidence: number
    core: boolean
  }>
  readonly contact: typeof parsedContacts.$inferSelect | null
  readonly skills: Array<typeof parsedSkills.$inferSelect>
  readonly education: Array<typeof parsedEducation.$inferSelect>
  readonly experience: Array<typeof parsedExperience.$inferSelect>
  readonly warnings: ParseWarning[]
  readonly pageCount: number | null
  readonly wordCount: number | null
  readonly charCount: number | null
  readonly parser: string
}

/**
 * The structured view, with sections recomputed rather than stored.
 *
 * Segmentation is a pure function of the normalised text, so recomputing is
 * cheaper than a table and can never drift from the line pointers already held
 * on the entity rows.
 */
export async function getStructuredParse(
  resumeId: string,
  userId: string,
): Promise<StructuredParse> {
  const parse = await latestParse(resumeId, userId)
  if (!parse) throw conflict('This resume has not been parsed successfully yet.')

  const model = toLineModel(parse.rawText)
  const sections = segmentSections(model)

  const [contact, skills, education, experience] = await Promise.all([
    db.select().from(parsedContacts).where(eq(parsedContacts.parseId, parse.id)).limit(1),
    db.select().from(parsedSkills).where(eq(parsedSkills.parseId, parse.id)),
    db.select().from(parsedEducation).where(eq(parsedEducation.parseId, parse.id)),
    db.select().from(parsedExperience).where(eq(parsedExperience.parseId, parse.id)),
  ])

  const { SECTION_LABELS, CORE_SECTIONS } = await import('@/domain/sections')

  return {
    parseId: parse.id,
    resumeId,
    lines: model.lines.map((line) => line.text),
    sections: sections.map((section) => ({
      type: section.type,
      displayName: SECTION_LABELS[section.type],
      headingText: section.headingText,
      headingLine: section.headingLine,
      startLine: section.startLine,
      endLine: section.endLine,
      confidence: section.confidence,
      core: CORE_SECTIONS.has(section.type),
    })),
    contact: contact[0] ?? null,
    skills: skills.sort((a, b) => b.confidence - a.confidence),
    education,
    experience,
    warnings: safeWarnings(parse.warnings),
    pageCount: parse.pageCount,
    wordCount: parse.wordCount,
    charCount: parse.charCount,
    parser: parse.parser,
  }
}

/**
 * The read model the rule engines work from.
 *
 * Built once and shared by ATS scoring, matching, and interview generation. Two
 * mappings would be two chances for the same resume to have a skill one report
 * counts and another does not.
 */
export async function buildSnapshot(
  resumeId: string,
  userId: string,
): Promise<{ parseId: string; snapshot: ResumeSnapshot }> {
  const structured = await getStructuredParse(resumeId, userId)

  const snapshot: ResumeSnapshot = {
    lines: structured.lines,
    sections: structured.sections.map((section) => ({
      type: section.type,
      headingText: section.headingText,
      headingLine: section.headingLine,
      startLine: section.startLine,
      endLine: section.endLine,
      confidence: section.confidence,
    })),
    contact: structured.contact
      ? {
          fullName: structured.contact.fullName,
          email: structured.contact.email,
          phone: structured.contact.phone,
          location: structured.contact.location,
          linkedinUrl: structured.contact.linkedinUrl,
          githubUrl: structured.contact.githubUrl,
          portfolioUrl: structured.contact.portfolioUrl,
        }
      : EMPTY_CONTACT,
    skills: structured.skills.map((skill) => ({
      name: skill.name,
      normalizedName: skill.normalizedName,
      category: skill.category as SkillCategory,
      confidence: skill.confidence,
      lineStart: skill.lineStart,
    })),
    education: structured.education.map((entry) => ({
      institution: entry.institution,
      degree: entry.degree,
      fieldOfStudy: entry.fieldOfStudy,
      startDate: entry.startDate,
      endDate: entry.endDate,
      lineStart: entry.lineStart,
      lineEnd: entry.lineEnd,
    })),
    experience: structured.experience.map((entry) => ({
      company: entry.company,
      jobTitle: entry.jobTitle,
      startDate: entry.startDate,
      endDate: entry.endDate,
      isCurrent: entry.isCurrent,
      description: entry.description,
      lineStart: entry.lineStart,
      lineEnd: entry.lineEnd,
    })),
    warningCodes: new Set(structured.warnings.map((warning) => warning.code)),
    pageCount: structured.pageCount,
    wordCount: structured.wordCount,
    charCount: structured.charCount,
  }

  return { parseId: structured.parseId, snapshot }
}

/** Warnings are stored as JSON text; a malformed value must not break the page. */
function safeWarnings(value: string | null): ParseWarning[] {
  if (!value) return []
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}
