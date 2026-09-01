import { relations, sql } from 'drizzle-orm'
import {
  boolean,
  customType,
  date,
  index,
  integer,
  pgTable,
  smallint,
  text,
  timestamp,
  uniqueIndex,
  uuid,
  varchar,
} from 'drizzle-orm/pg-core'

/**
 * The schema.
 *
 * Every table that holds user content carries `userId`, denormalised even where
 * it could be reached through a join. Tenant isolation is then a WHERE clause on
 * the table being queried rather than a property of the join path, and a query
 * written without it is obviously wrong at the point it is written.
 */

/**
 * Raw file bytes.
 *
 * Deliberately stored in Postgres rather than an object store. It keeps the
 * whole application to one piece of infrastructure — a database — which is the
 * difference between deploying this and not. Resumes are capped at 5 MB and 10
 * per user, so the ceiling is 50 MB per user, which a small Postgres carries
 * without complaint.
 *
 * The trade is real: bytea rows bloat the table and make backups larger, and an
 * object store would be correct at scale. It is a considered compromise, not an
 * oversight.
 */
const bytea = customType<{ data: Buffer; default: false }>({
  dataType() {
    return 'bytea'
  },
})

/* ------------------------------------------------------------------ */
/* Accounts                                                           */
/* ------------------------------------------------------------------ */

export const users = pgTable(
  'users',
  {
    id: uuid('id').primaryKey().defaultRandom(),
    email: varchar('email', { length: 255 }).notNull(),
    passwordHash: varchar('password_hash', { length: 100 }).notNull(),
    fullName: varchar('full_name', { length: 120 }).notNull(),

    // 'USER' or 'ADMIN'. A column rather than a join table: there are two roles
    // and no prospect of a third, and a join table for that is ceremony.
    role: varchar('role', { length: 20 }).notNull().default('USER'),
    status: varchar('status', { length: 20 }).notNull().default('ACTIVE'),

    failedLoginAttempts: smallint('failed_login_attempts').notNull().default(0),
    lockedUntil: timestamp('locked_until', { withTimezone: true }),
    lastLoginAt: timestamp('last_login_at', { withTimezone: true }),

    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [
    // Lowercased, so Ada@x.com and ada@x.com cannot both register. Case-sensitive
    // email uniqueness is a duplicate-account bug waiting to happen.
    uniqueIndex('ux_users_email_lower').on(sql`lower(${table.email})`),
    index('ix_users_created').on(table.createdAt),
  ],
)

/**
 * Sessions.
 *
 * Opaque random tokens in a table, not self-validating JWTs. A JWT cannot be
 * revoked without a server-side blocklist, at which point the statelessness that
 * justified it is gone. Signing out here is one UPDATE and takes effect on the
 * next request.
 */
export const sessions = pgTable(
  'sessions',
  {
    id: uuid('id').primaryKey().defaultRandom(),
    userId: uuid('user_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),

    // SHA-256 of the cookie value. The token itself is never stored, so a
    // database leak does not hand over live sessions.
    tokenHash: varchar('token_hash', { length: 64 }).notNull(),

    expiresAt: timestamp('expires_at', { withTimezone: true }).notNull(),
    revokedAt: timestamp('revoked_at', { withTimezone: true }),
    userAgent: varchar('user_agent', { length: 300 }),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [
    uniqueIndex('ux_sessions_token_hash').on(table.tokenHash),
    index('ix_sessions_user').on(table.userId),
    index('ix_sessions_expires').on(table.expiresAt),
  ],
)

/* ------------------------------------------------------------------ */
/* Resumes and what was read from them                                */
/* ------------------------------------------------------------------ */

export const resumes = pgTable(
  'resumes',
  {
    id: uuid('id').primaryKey().defaultRandom(),
    userId: uuid('user_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),

    originalFilename: varchar('original_filename', { length: 255 }).notNull(),
    mimeType: varchar('mime_type', { length: 100 }).notNull(),
    sizeBytes: integer('size_bytes').notNull(),
    checksumSha256: varchar('checksum_sha256', { length: 64 }).notNull(),
    content: bytea('content').notNull(),

    // UPLOADED | PARSING | PARSED | PARSE_FAILED
    status: varchar('status', { length: 20 }).notNull().default('UPLOADED'),
    isPrimary: boolean('is_primary').notNull().default(false),

    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [
    index('ix_resumes_user_created').on(table.userId, table.createdAt),
    // Re-uploading identical bytes is almost always an accident, and analysing
    // the same document twice costs the user a slot for nothing.
    uniqueIndex('ux_resumes_user_checksum').on(table.userId, table.checksumSha256),
  ],
)

/**
 * One row per parse attempt, failures included.
 *
 * A failed parse is the most valuable row in this table: it is the evidence for
 * telling a user their resume is a scanned image, and the data for improving the
 * parser.
 */
export const resumeParses = pgTable(
  'resume_parses',
  {
    id: uuid('id').primaryKey().defaultRandom(),
    resumeId: uuid('resume_id')
      .notNull()
      .references(() => resumes.id, { onDelete: 'cascade' }),
    userId: uuid('user_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),

    status: varchar('status', { length: 20 }).notNull(),
    parser: varchar('parser', { length: 30 }).notNull(),
    rawText: text('raw_text'),
    pageCount: smallint('page_count'),
    wordCount: integer('word_count'),
    charCount: integer('char_count'),

    // [{ code, message }] — structural problems worth telling the user about.
    warnings: text('warnings').notNull().default('[]'),
    errorMessage: text('error_message'),
    durationMs: integer('duration_ms'),

    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [index('ix_parses_resume_created').on(table.resumeId, table.createdAt)],
)

export const parsedContacts = pgTable('parsed_contacts', {
  id: uuid('id').primaryKey().defaultRandom(),
  parseId: uuid('parse_id')
    .notNull()
    .references(() => resumeParses.id, { onDelete: 'cascade' }),
  userId: uuid('user_id')
    .notNull()
    .references(() => users.id, { onDelete: 'cascade' }),

  fullName: varchar('full_name', { length: 150 }),
  email: varchar('email', { length: 320 }),
  phone: varchar('phone', { length: 40 }),
  location: varchar('location', { length: 150 }),
  linkedinUrl: varchar('linkedin_url', { length: 500 }),
  githubUrl: varchar('github_url', { length: 500 }),
  portfolioUrl: varchar('portfolio_url', { length: 500 }),

  confidence: smallint('confidence').notNull(),
  lineStart: integer('line_start'),
  lineEnd: integer('line_end'),
})

export const parsedSkills = pgTable(
  'parsed_skills',
  {
    id: uuid('id').primaryKey().defaultRandom(),
    parseId: uuid('parse_id')
      .notNull()
      .references(() => resumeParses.id, { onDelete: 'cascade' }),
    userId: uuid('user_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),

    // What the candidate wrote, shown back to them unaltered.
    name: varchar('name', { length: 100 }).notNull(),
    // The canonical form matching joins on: "react" for "ReactJS".
    normalizedName: varchar('normalized_name', { length: 100 }).notNull(),
    category: varchar('category', { length: 30 }).notNull(),

    confidence: smallint('confidence').notNull(),
    lineStart: integer('line_start'),
  },
  (table) => [
    // The extractor can legitimately see "React" in both the skills block and a
    // project bullet; without this the second insert aborts the transaction.
    uniqueIndex('ux_parsed_skills_parse_name').on(table.parseId, table.normalizedName),
    index('ix_parsed_skills_normalized').on(table.normalizedName),
  ],
)

export const parsedEducation = pgTable('parsed_education', {
  id: uuid('id').primaryKey().defaultRandom(),
  parseId: uuid('parse_id')
    .notNull()
    .references(() => resumeParses.id, { onDelete: 'cascade' }),
  userId: uuid('user_id')
    .notNull()
    .references(() => users.id, { onDelete: 'cascade' }),

  institution: varchar('institution', { length: 200 }),
  degree: varchar('degree', { length: 150 }),
  fieldOfStudy: varchar('field_of_study', { length: 150 }),
  startDate: date('start_date'),
  endDate: date('end_date'),
  grade: varchar('grade', { length: 20 }),

  confidence: smallint('confidence').notNull(),
  lineStart: integer('line_start'),
  lineEnd: integer('line_end'),
})

export const parsedExperience = pgTable('parsed_experience', {
  id: uuid('id').primaryKey().defaultRandom(),
  parseId: uuid('parse_id')
    .notNull()
    .references(() => resumeParses.id, { onDelete: 'cascade' }),
  userId: uuid('user_id')
    .notNull()
    .references(() => users.id, { onDelete: 'cascade' }),

  company: varchar('company', { length: 200 }),
  jobTitle: varchar('job_title', { length: 200 }),
  startDate: date('start_date'),
  endDate: date('end_date'),
  isCurrent: boolean('is_current').notNull().default(false),
  description: text('description'),

  confidence: smallint('confidence').notNull(),
  lineStart: integer('line_start'),
  lineEnd: integer('line_end'),
})

/* ------------------------------------------------------------------ */
/* ATS analysis                                                       */
/* ------------------------------------------------------------------ */

/**
 * Append-only. The closing promise is "fix it and watch the score move", and a
 * table that overwrites the previous score cannot show movement.
 */
export const atsAnalyses = pgTable(
  'ats_analyses',
  {
    id: uuid('id').primaryKey().defaultRandom(),
    resumeId: uuid('resume_id')
      .notNull()
      .references(() => resumes.id, { onDelete: 'cascade' }),
    parseId: uuid('parse_id')
      .notNull()
      .references(() => resumeParses.id, { onDelete: 'cascade' }),
    userId: uuid('user_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),

    overallScore: smallint('overall_score').notNull(),
    band: varchar('band', { length: 20 }).notNull(),

    parseabilityScore: smallint('parseability_score').notNull(),
    structureScore: smallint('structure_score').notNull(),
    contentScore: smallint('content_score').notNull(),
    skillsScore: smallint('skills_score').notNull(),
    contactScore: smallint('contact_score').notNull(),

    // Which rule set produced this number. Without it, a score from before a
    // rubric change and one from after look like a change in the resume.
    rubricVersion: varchar('rubric_version', { length: 20 }).notNull(),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [index('ix_ats_resume_created').on(table.resumeId, table.createdAt)],
)

export const atsFindings = pgTable(
  'ats_findings',
  {
    id: uuid('id').primaryKey().defaultRandom(),
    analysisId: uuid('analysis_id')
      .notNull()
      .references(() => atsAnalyses.id, { onDelete: 'cascade' }),

    code: varchar('code', { length: 60 }).notNull(),
    category: varchar('category', { length: 30 }).notNull(),
    severity: varchar('severity', { length: 20 }).notNull(),

    title: varchar('title', { length: 200 }).notNull(),
    detail: text('detail').notNull(),
    recommendation: text('recommendation'),

    // A score without a quote from the resume it came from is an assertion,
    // not analysis. These are first-class columns for that reason.
    evidence: text('evidence'),
    lineStart: integer('line_start'),
    lineEnd: integer('line_end'),

    pointsLost: smallint('points_lost').notNull().default(0),
    displayOrder: smallint('display_order').notNull().default(0),
  },
  (table) => [index('ix_findings_analysis').on(table.analysisId, table.displayOrder)],
)

/* ------------------------------------------------------------------ */
/* Job descriptions and matching                                      */
/* ------------------------------------------------------------------ */

export const jobDescriptions = pgTable(
  'job_descriptions',
  {
    id: uuid('id').primaryKey().defaultRandom(),
    userId: uuid('user_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),

    title: varchar('title', { length: 200 }).notNull(),
    company: varchar('company', { length: 200 }),
    location: varchar('location', { length: 150 }),
    sourceUrl: varchar('source_url', { length: 1000 }),
    // The posting as pasted. Never rewritten — it is the evidence a match is
    // explained against.
    rawText: text('raw_text').notNull(),

    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [index('ix_jd_user_created').on(table.userId, table.createdAt)],
)

export const jdMatches = pgTable(
  'jd_matches',
  {
    id: uuid('id').primaryKey().defaultRandom(),
    jobDescriptionId: uuid('job_description_id')
      .notNull()
      .references(() => jobDescriptions.id, { onDelete: 'cascade' }),
    resumeId: uuid('resume_id')
      .notNull()
      .references(() => resumes.id, { onDelete: 'cascade' }),
    userId: uuid('user_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),

    overallScore: smallint('overall_score').notNull(),
    band: varchar('band', { length: 20 }).notNull(),
    requiredSkillScore: smallint('required_skill_score').notNull(),
    optionalSkillScore: smallint('optional_skill_score').notNull(),
    titleScore: smallint('title_score').notNull(),
    experienceScore: smallint('experience_score').notNull(),

    matchedCount: smallint('matched_count').notNull().default(0),
    missingCount: smallint('missing_count').notNull().default(0),

    rubricVersion: varchar('rubric_version', { length: 20 }).notNull(),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [index('ix_matches_jd_created').on(table.jobDescriptionId, table.createdAt)],
)

export const jdMatchSkills = pgTable(
  'jd_match_skills',
  {
    id: uuid('id').primaryKey().defaultRandom(),
    matchId: uuid('match_id')
      .notNull()
      .references(() => jdMatches.id, { onDelete: 'cascade' }),

    normalizedName: varchar('normalized_name', { length: 100 }).notNull(),
    displayName: varchar('display_name', { length: 100 }).notNull(),
    category: varchar('category', { length: 30 }).notNull(),
    // MATCHED | MISSING | EXTRA
    status: varchar('status', { length: 20 }).notNull(),
    required: boolean('required').notNull().default(false),
    priority: smallint('priority').notNull().default(0),

    // Both sides are quoted. "Missing: Kubernetes" is unactionable; the line of
    // the posting that asked for it tells the candidate how central it is.
    resumeEvidence: text('resume_evidence'),
    resumeLine: integer('resume_line'),
    jdEvidence: text('jd_evidence'),
    jdLine: integer('jd_line'),
  },
  (table) => [index('ix_match_skills_match').on(table.matchId, table.priority)],
)

/* ------------------------------------------------------------------ */
/* Mock interview                                                     */
/* ------------------------------------------------------------------ */

export const interviewSessions = pgTable(
  'interview_sessions',
  {
    id: uuid('id').primaryKey().defaultRandom(),
    userId: uuid('user_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),
    // SET NULL, not CASCADE: deleting a resume must not delete the record of an
    // interview the candidate already sat and learned from.
    resumeId: uuid('resume_id').references(() => resumes.id, { onDelete: 'set null' }),
    jobDescriptionId: uuid('job_description_id').references(() => jobDescriptions.id, {
      onDelete: 'set null',
    }),

    focus: varchar('focus', { length: 30 }).notNull(),
    status: varchar('status', { length: 20 }).notNull().default('IN_PROGRESS'),

    questionCount: smallint('question_count').notNull(),
    answeredCount: smallint('answered_count').notNull().default(0),

    overallScore: smallint('overall_score'),
    band: varchar('band', { length: 20 }),

    blueprintVersion: varchar('blueprint_version', { length: 20 }).notNull(),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    completedAt: timestamp('completed_at', { withTimezone: true }),
  },
  (table) => [index('ix_sessions_user_created').on(table.userId, table.createdAt)],
)

export const interviewQuestions = pgTable(
  'interview_questions',
  {
    id: uuid('id').primaryKey().defaultRandom(),
    sessionId: uuid('session_id')
      .notNull()
      .references(() => interviewSessions.id, { onDelete: 'cascade' }),
    userId: uuid('user_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),

    position: smallint('position').notNull(),
    kind: varchar('kind', { length: 30 }).notNull(),
    prompt: text('prompt').notNull(),
    focusSkill: varchar('focus_skill', { length: 100 }),
    // Why the candidate is being asked this. A generated question that cannot
    // explain itself is indistinguishable from a generic one.
    rationale: text('rationale'),
    // Newline-separated cues a good answer covers, revealed after answering.
    expectedPoints: text('expected_points'),
  },
  (table) => [uniqueIndex('ux_questions_session_position').on(table.sessionId, table.position)],
)

export const interviewAnswers = pgTable(
  'interview_answers',
  {
    id: uuid('id').primaryKey().defaultRandom(),
    questionId: uuid('question_id')
      .notNull()
      .references(() => interviewQuestions.id, { onDelete: 'cascade' }),
    sessionId: uuid('session_id')
      .notNull()
      .references(() => interviewSessions.id, { onDelete: 'cascade' }),
    userId: uuid('user_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),

    answerText: text('answer_text').notNull(),
    wordCount: integer('word_count').notNull(),

    score: smallint('score').notNull(),
    structureScore: smallint('structure_score').notNull(),
    specificityScore: smallint('specificity_score').notNull(),
    relevanceScore: smallint('relevance_score').notNull(),
    clarityScore: smallint('clarity_score').notNull(),

    strengths: text('strengths'),
    improvements: text('improvements'),
    rubricVersion: varchar('rubric_version', { length: 20 }).notNull(),

    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [
    // One answer per question, enforced here rather than in application code.
    // Two rows for one question would make the session score depend on which
    // one a query happened to return first.
    uniqueIndex('ux_answers_question').on(table.questionId),
    index('ix_answers_session').on(table.sessionId),
  ],
)

/* ------------------------------------------------------------------ */
/* Relations                                                          */
/* ------------------------------------------------------------------ */

export const usersRelations = relations(users, ({ many }) => ({
  resumes: many(resumes),
  sessions: many(sessions),
}))

export const resumesRelations = relations(resumes, ({ one, many }) => ({
  user: one(users, { fields: [resumes.userId], references: [users.id] }),
  parses: many(resumeParses),
}))

export const resumeParsesRelations = relations(resumeParses, ({ one, many }) => ({
  resume: one(resumes, { fields: [resumeParses.resumeId], references: [resumes.id] }),
  contact: one(parsedContacts),
  skills: many(parsedSkills),
  education: many(parsedEducation),
  experience: many(parsedExperience),
}))

export type User = typeof users.$inferSelect
export type Resume = typeof resumes.$inferSelect
export type ResumeParse = typeof resumeParses.$inferSelect
export type ParsedSkill = typeof parsedSkills.$inferSelect
export type ParsedEducation = typeof parsedEducation.$inferSelect
export type ParsedExperience = typeof parsedExperience.$inferSelect
export type ParsedContact = typeof parsedContacts.$inferSelect
export type AtsAnalysis = typeof atsAnalyses.$inferSelect
export type AtsFinding = typeof atsFindings.$inferSelect
export type JobDescription = typeof jobDescriptions.$inferSelect
export type JdMatch = typeof jdMatches.$inferSelect
export type JdMatchSkill = typeof jdMatchSkills.$inferSelect
export type InterviewSession = typeof interviewSessions.$inferSelect
export type InterviewQuestion = typeof interviewQuestions.$inferSelect
export type InterviewAnswer = typeof interviewAnswers.$inferSelect
