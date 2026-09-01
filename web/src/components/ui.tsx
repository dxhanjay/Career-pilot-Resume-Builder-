'use client'

import Link from 'next/link'
import type { ReactNode } from 'react'
import { TONE_CLASSES } from '@/lib/format'

/**
 * The design system.
 *
 * One file, because every one of these is under thirty lines and the value of
 * having them together is that a new page can see the whole vocabulary at once.
 *
 * Props are typed loosely on purpose. These are internal building blocks rather
 * than a published component API, and exhaustively typing every pass-through
 * attribute would add noise without catching a bug anyone would otherwise ship.
 */

type Props = Record<string, any>

export function cx(...values: Array<string | false | null | undefined>) {
  return values.filter(Boolean).join(' ')
}

/* ------------------------------------------------------------------ */
/* Buttons                                                            */
/* ------------------------------------------------------------------ */

const BUTTON_VARIANTS: Record<string, string> = {
  primary:
    'bg-brand-600 text-white hover:bg-brand-700 active:bg-brand-800 shadow-sm disabled:bg-brand-300',
  secondary:
    'bg-white text-ink-800 border border-ink-200 hover:bg-ink-50 active:bg-ink-100 shadow-sm disabled:text-ink-400',
  ghost: 'text-ink-600 hover:bg-ink-100 hover:text-ink-900 disabled:text-ink-300',
  danger:
    'bg-red-600 text-white hover:bg-red-700 active:bg-red-800 shadow-sm disabled:bg-red-300',
}

const BUTTON_SIZES: Record<string, string> = {
  sm: 'text-sm px-3 py-1.5 gap-1.5',
  md: 'text-sm px-4 py-2.5 gap-2',
  lg: 'text-base px-5 py-3 gap-2',
}

export function Button({
  as: Component = 'button',
  variant = 'primary',
  size = 'md',
  loading = false,
  className,
  children,
  disabled,
  ...props
}: Props) {
  return (
    <Component
      className={cx(
        'inline-flex items-center justify-center rounded-lg font-medium transition',
        'disabled:cursor-not-allowed',
        BUTTON_VARIANTS[variant],
        BUTTON_SIZES[size],
        className,
      )}
      disabled={disabled || loading}
      // Announced to screen readers, not only shown as a spinner.
      aria-busy={loading || undefined}
      {...props}
    >
      {loading && <Spinner className="h-4 w-4" />}
      {children}
    </Component>
  )
}

export function LinkButton({ href, ...props }: Props) {
  return <Button as={Link} href={href} {...props} />
}

/* ------------------------------------------------------------------ */
/* Feedback                                                           */
/* ------------------------------------------------------------------ */

export function Spinner({ className = 'h-5 w-5' }: { className?: string }) {
  return (
    <svg className={cx('animate-spin', className)} viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <circle className="opacity-20" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
      <path
        className="opacity-90"
        fill="currentColor"
        d="M4 12a8 8 0 0 1 8-8v4a4 4 0 0 0-4 4H4z"
      />
    </svg>
  )
}

export function Loading({ label = 'Loading…', className }: Props) {
  return (
    <div
      className={cx('flex items-center justify-center gap-3 py-16 text-ink-500', className)}
      role="status"
    >
      <Spinner />
      <span className="text-sm">{label}</span>
    </div>
  )
}

export function Alert({ tone = 'red', title, children, action, className }: Props) {
  const palette = TONE_CLASSES[tone] ?? TONE_CLASSES.red
  return (
    <div
      role={tone === 'red' ? 'alert' : 'status'}
      className={cx('rounded-lg border p-4', palette.bg, palette.border, className)}
    >
      {title && <p className={cx('text-sm font-semibold', palette.text)}>{title}</p>}
      {children && <div className="mt-1 text-sm text-ink-700">{children}</div>}
      {action && <div className="mt-3">{action}</div>}
    </div>
  )
}

export function EmptyState({ icon, title, description, action, className }: Props) {
  return (
    <div className={cx('flex flex-col items-center px-6 py-14 text-center', className)}>
      {icon && (
        <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-brand-50 text-brand-600">
          {icon}
        </div>
      )}
      <h3 className="text-base font-semibold text-ink-900">{title}</h3>
      {description && <p className="mt-1.5 max-w-md text-sm text-ink-500">{description}</p>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  )
}

/* ------------------------------------------------------------------ */
/* Surfaces                                                           */
/* ------------------------------------------------------------------ */

export function Card({ className, children, ...props }: Props) {
  return (
    <div className={cx('card', className)} {...props}>
      {children}
    </div>
  )
}

export function CardHeader({ title, description, action, className }: Props) {
  return (
    <div
      className={cx(
        'flex flex-col gap-3 border-b border-ink-100 px-5 py-4 sm:flex-row sm:items-center sm:justify-between',
        className,
      )}
    >
      <div className="min-w-0">
        <h2 className="text-sm font-semibold text-ink-900">{title}</h2>
        {description && <p className="mt-0.5 text-sm text-ink-500">{description}</p>}
      </div>
      {action && <div className="flex shrink-0 items-center gap-2">{action}</div>}
    </div>
  )
}

export function Badge({ tone = 'slate', children, className }: Props) {
  const palette = TONE_CLASSES[tone] ?? TONE_CLASSES.slate
  return (
    <span
      className={cx(
        'inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-xs font-medium',
        palette.bg,
        palette.border,
        palette.text,
        className,
      )}
    >
      {children}
    </span>
  )
}

/* ------------------------------------------------------------------ */
/* Score display                                                      */
/* ------------------------------------------------------------------ */

/**
 * A 0-100 score as a ring.
 *
 * The number is repeated in an aria-label because a stroke-dasharray arc is
 * invisible to a screen reader, and the score is the single most important
 * value on the page.
 */
export function ScoreRing({ score = 0, tone = 'brand', size = 120, label, sublabel }: Props) {
  const palette = TONE_CLASSES[tone] ?? TONE_CLASSES.brand
  const stroke = size >= 100 ? 10 : 8
  const radius = (size - stroke) / 2
  const circumference = 2 * Math.PI * radius
  const filled = (Math.max(0, Math.min(100, score)) / 100) * circumference

  return (
    <div className="inline-flex flex-col items-center" role="img" aria-label={`${label ?? 'Score'}: ${score} out of 100`}>
      <div className="relative" style={{ width: size, height: size }}>
        <svg width={size} height={size} className="-rotate-90" aria-hidden="true">
          <circle
            cx={size / 2}
            cy={size / 2}
            r={radius}
            className="stroke-ink-100"
            strokeWidth={stroke}
            fill="none"
          />
          <circle
            cx={size / 2}
            cy={size / 2}
            r={radius}
            className={cx(palette.ring, 'transition-[stroke-dasharray] duration-700 ease-out')}
            strokeWidth={stroke}
            strokeLinecap="round"
            fill="none"
            strokeDasharray={`${filled} ${circumference}`}
          />
        </svg>
        <div className="absolute inset-0 flex flex-col items-center justify-center">
          <span className={cx('font-semibold tabular-nums', size >= 100 ? 'text-3xl' : 'text-xl', palette.text)}>
            {score}
          </span>
          {sublabel && <span className="text-[11px] font-medium text-ink-400">{sublabel}</span>}
        </div>
      </div>
      {label && <span className="mt-2 text-sm font-medium text-ink-700">{label}</span>}
    </div>
  )
}

export function ScoreBar({ score = 0, tone = 'brand', label, hint }: Props) {
  const palette = TONE_CLASSES[tone] ?? TONE_CLASSES.brand
  return (
    <div>
      <div className="flex items-baseline justify-between gap-3">
        <span className="text-sm font-medium text-ink-800">{label}</span>
        <span className="text-sm font-semibold tabular-nums text-ink-900">{score}</span>
      </div>
      <div
        className="mt-1.5 h-2 w-full overflow-hidden rounded-full bg-ink-100"
        role="progressbar"
        aria-valuenow={score}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-label={label}
      >
        <div
          className={cx('h-full rounded-full transition-[width] duration-700 ease-out', palette.bar)}
          style={{ width: `${Math.max(0, Math.min(100, score))}%` }}
        />
      </div>
      {hint && <p className="mt-1.5 text-xs leading-5 text-ink-500">{hint}</p>}
    </div>
  )
}

/* ------------------------------------------------------------------ */
/* Forms                                                              */
/* ------------------------------------------------------------------ */

export function Field({ label, htmlFor, error, hint, children, className }: Props) {
  return (
    <div className={className}>
      {label && (
        <label className="label" htmlFor={htmlFor}>
          {label}
        </label>
      )}
      {children}
      {hint && !error && <p className="mt-1.5 text-xs text-ink-500">{hint}</p>}
      {error && (
        <p className="mt-1.5 text-xs font-medium text-red-600" role="alert">
          {error}
        </p>
      )}
    </div>
  )
}

export function TextInput({ error, className, ...props }: Props) {
  return <input className={cx('input', error && 'input-error', className)} aria-invalid={Boolean(error)} {...props} />
}

export function TextArea({ error, className, ...props }: Props) {
  return (
    <textarea
      className={cx('input resize-y', error && 'input-error', className)}
      aria-invalid={Boolean(error)}
      {...props}
    />
  )
}

export function Select({ error, className, children, ...props }: Props) {
  return (
    <select className={cx('input', error && 'input-error', className)} {...props}>
      {children}
    </select>
  )
}

/* ------------------------------------------------------------------ */
/* Layout                                                             */
/* ------------------------------------------------------------------ */

export function PageHeader({ title, description, action, breadcrumb }: Props) {
  return (
    <div className="mb-6">
      {breadcrumb}
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div className="min-w-0">
          <h1 className="text-2xl font-semibold tracking-tight text-ink-900 sm:text-3xl">{title}</h1>
          {description && <p className="mt-1.5 max-w-2xl text-sm text-ink-500">{description}</p>}
        </div>
        {action && <div className="flex shrink-0 flex-wrap items-center gap-2">{action}</div>}
      </div>
    </div>
  )
}

export function Stat({ label, value, hint }: Props) {
  return (
    <div className="card px-5 py-4">
      <p className="text-xs font-medium uppercase tracking-wide text-ink-400">{label}</p>
      <p className="mt-1.5 text-2xl font-semibold tabular-nums text-ink-900">{value}</p>
      {hint && <p className="mt-0.5 text-xs text-ink-500">{hint}</p>}
    </div>
  )
}
