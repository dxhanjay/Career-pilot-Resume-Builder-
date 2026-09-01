import { z } from 'zod'

/**
 * Request schemas.
 *
 * Every message is written to be shown to a user unaltered. "Invalid input" is
 * a message that has never helped anybody; "At least 8 characters" is.
 */

/** Matches the bcrypt hash column width and keeps the work factor bounded. */
export const MIN_PASSWORD = 8
const MAX_PASSWORD = 200

export const registerSchema = z.object({
  fullName: z
    .string()
    .trim()
    .min(2, 'Enter your name')
    .max(120, 'That name is too long'),
  email: z
    .string()
    .trim()
    .min(1, 'Enter your email address')
    .email('That does not look like an email address')
    .max(255, 'That email address is too long'),
  password: z
    .string()
    .min(MIN_PASSWORD, `At least ${MIN_PASSWORD} characters`)
    .max(MAX_PASSWORD, 'That password is too long'),
})

export const loginSchema = z.object({
  email: z.string().trim().min(1, 'Enter your email address').email('That does not look like an email address'),
  password: z.string().min(1, 'Enter your password'),
})

export const changePasswordSchema = z.object({
  currentPassword: z.string().min(1, 'Enter your current password'),
  newPassword: z
    .string()
    .min(MIN_PASSWORD, `At least ${MIN_PASSWORD} characters`)
    .max(MAX_PASSWORD, 'That password is too long'),
})

/**
 * The lower bound is not arbitrary. Forty characters is not a job description,
 * and matching a resume against a fragment produces a confident, meaningless
 * score — worse than refusing, because the user believes it.
 */
export const jobDescriptionSchema = z.object({
  title: z.string().trim().min(1, 'Give this posting a title').max(200, 'That title is too long'),
  company: z.string().trim().max(200).nullish(),
  location: z.string().trim().max(150).nullish(),
  sourceUrl: z.string().trim().max(1000).nullish(),
  rawText: z
    .string()
    .trim()
    .min(40, 'Paste the full posting — at least 40 characters. A short snippet produces a confident, meaningless score.')
    .max(40000, 'That posting is longer than we can process'),
})

export const runMatchSchema = z.object({
  resumeId: z.string().uuid('Choose a resume to match against'),
})

export const startInterviewSchema = z.object({
  focus: z.enum(['GENERAL', 'RESUME_DEEP_DIVE', 'JOB_SPECIFIC', 'BEHAVIOURAL'], {
    errorMap: () => ({ message: 'Choose what to focus on' }),
  }),
  resumeId: z.string().uuid().nullish(),
  jobDescriptionId: z.string().uuid().nullish(),
  questionCount: z
    .number()
    .int()
    .min(3, 'An interview needs at least 3 questions')
    .max(12, '12 questions is the maximum')
    .default(6),
})

export const submitAnswerSchema = z.object({
  answer: z
    .string()
    .trim()
    .min(1, 'Write an answer before submitting')
    .max(8000, 'Answers are limited to 8000 characters'),
})

export const updateUserStatusSchema = z.object({
  action: z.enum(['SUSPEND', 'REACTIVATE'], {
    errorMap: () => ({ message: 'An action is required' }),
  }),
})
