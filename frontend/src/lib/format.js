/** Shared formatting and presentation helpers. */

export function formatDate(value, options) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleDateString(undefined, options ?? { day: 'numeric', month: 'short', year: 'numeric' })
}

export function formatDateTime(value) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleString(undefined, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

/** "3 days ago" reads better than a date for recent activity. */
export function relativeTime(value) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'

  const seconds = Math.round((Date.now() - date.getTime()) / 1000)
  if (seconds < 60) return 'just now'

  const units = [
    ['minute', 60],
    ['hour', 3600],
    ['day', 86400],
    ['week', 604800],
    ['month', 2592000],
    ['year', 31536000],
  ]
  let chosen = units[0]
  for (const unit of units) {
    if (seconds >= unit[1]) chosen = unit
  }
  const amount = Math.floor(seconds / chosen[1])
  return `${amount} ${chosen[0]}${amount === 1 ? '' : 's'} ago`
}

export function formatBytes(bytes) {
  if (!bytes && bytes !== 0) return '—'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

/**
 * Colour for a 0-100 score.
 *
 * The thresholds match the backend bands exactly. Two sets of thresholds is how
 * a page ends up showing an amber ring above the word "Strong".
 */
export function scoreTone(score) {
  if (score >= 85) return 'emerald'
  if (score >= 70) return 'brand'
  if (score >= 50) return 'amber'
  return 'red'
}

export function matchTone(score) {
  if (score >= 80) return 'emerald'
  if (score >= 60) return 'brand'
  if (score >= 35) return 'amber'
  return 'red'
}

/** Static class names, because Tailwind cannot see a template-built one. */
export const TONE_CLASSES = {
  emerald: {
    text: 'text-emerald-700',
    bg: 'bg-emerald-50',
    border: 'border-emerald-200',
    ring: 'stroke-emerald-500',
    dot: 'bg-emerald-500',
    bar: 'bg-emerald-500',
  },
  brand: {
    text: 'text-brand-700',
    bg: 'bg-brand-50',
    border: 'border-brand-200',
    ring: 'stroke-brand-500',
    dot: 'bg-brand-500',
    bar: 'bg-brand-500',
  },
  amber: {
    text: 'text-amber-700',
    bg: 'bg-amber-50',
    border: 'border-amber-200',
    ring: 'stroke-amber-500',
    dot: 'bg-amber-500',
    bar: 'bg-amber-500',
  },
  red: {
    text: 'text-red-700',
    bg: 'bg-red-50',
    border: 'border-red-200',
    ring: 'stroke-red-500',
    dot: 'bg-red-500',
    bar: 'bg-red-500',
  },
  slate: {
    text: 'text-ink-600',
    bg: 'bg-ink-50',
    border: 'border-ink-200',
    ring: 'stroke-ink-400',
    dot: 'bg-ink-400',
    bar: 'bg-ink-400',
  },
}

export const SEVERITY_TONE = {
  CRITICAL: 'red',
  HIGH: 'red',
  MEDIUM: 'amber',
  LOW: 'slate',
  PASS: 'emerald',
}

export const SEVERITY_LABEL = {
  CRITICAL: 'Critical',
  HIGH: 'High',
  MEDIUM: 'Medium',
  LOW: 'Low',
  PASS: 'Working',
}

export const RESUME_STATUS = {
  UPLOADED: { label: 'Uploaded', tone: 'slate' },
  PARSING: { label: 'Reading…', tone: 'amber' },
  PARSED: { label: 'Ready', tone: 'emerald' },
  PARSE_FAILED: { label: 'Could not read', tone: 'red' },
}

export const SKILL_CATEGORY_LABEL = {
  LANGUAGE: 'Language',
  FRAMEWORK: 'Framework',
  DATABASE: 'Database',
  CLOUD_DEVOPS: 'Cloud / DevOps',
  TOOL: 'Tool',
  CONCEPT: 'Concept',
  SOFT_SKILL: 'Soft skill',
}

export function pluralise(count, singular, plural) {
  return `${count} ${count === 1 ? singular : plural ?? `${singular}s`}`
}

export function initials(name = '') {
  const parts = name.trim().split(/\s+/).filter(Boolean)
  if (parts.length === 0) return '?'
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase()
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase()
}
