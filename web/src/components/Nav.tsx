'use client'

import Link from 'next/link'
import { usePathname, useRouter } from 'next/navigation'
import { useEffect, useState } from 'react'
import { api } from '@/lib/client'
import { initials } from '@/lib/format'
import { Button, cx } from './ui'

export function Logo({ className }: { className?: string }) {
  return (
    <span className={cx('inline-flex items-center gap-2', className)}>
      <svg viewBox="0 0 32 32" className="h-7 w-7" aria-hidden="true">
        <rect width="32" height="32" rx="8" className="fill-brand-600" />
        <path
          d="M9 21V11h5.2a3.4 3.4 0 0 1 0 6.8H12V21H9Zm3-5.6h2a1.4 1.4 0 0 0 0-2.8h-2v2.8Z"
          fill="white"
        />
        <circle cx="22" cy="19.5" r="2.5" fill="white" />
      </svg>
      <span className="text-[15px] font-semibold tracking-tight text-ink-900">CareerPilot</span>
    </span>
  )
}

const LINKS = [
  { href: '/dashboard', label: 'Dashboard' },
  { href: '/resumes', label: 'Resumes' },
  { href: '/jobs', label: 'Job matches' },
  { href: '/interviews', label: 'Interviews' },
]

interface NavUser {
  fullName: string
  email: string
  role: string
}

/**
 * The signed-in header.
 *
 * The mobile menu closes on navigation. Leaving it open is a small bug with a
 * large effect: on a phone the panel covers the page the user just asked for,
 * and it reads as the tap having failed.
 */
export default function Nav({ user }: { user: NavUser }) {
  const [menuOpen, setMenuOpen] = useState(false)
  const [accountOpen, setAccountOpen] = useState(false)
  const pathname = usePathname()
  const router = useRouter()

  useEffect(() => {
    setMenuOpen(false)
    setAccountOpen(false)
  }, [pathname])

  const links = user.role === 'ADMIN' ? [...LINKS, { href: '/admin', label: 'Admin' }] : LINKS

  async function signOut() {
    try {
      await api.post('/auth/logout')
    } finally {
      // Refresh as well as push: the layout reads the session on the server, so
      // without this the shell would still render for a user who has just left.
      router.push('/login')
      router.refresh()
    }
  }

  const isActive = (href: string) => pathname === href || pathname.startsWith(`${href}/`)

  return (
    <header className="sticky top-0 z-40 border-b border-ink-100 bg-white/90 backdrop-blur">
      <div className="mx-auto flex h-16 max-w-7xl items-center justify-between gap-4 px-4 sm:px-6 lg:px-8">
        <div className="flex items-center gap-8">
          <Link href="/dashboard" className="rounded-md">
            <Logo />
          </Link>
          <nav className="hidden items-center gap-1 md:flex" aria-label="Main">
            {links.map((link) => (
              <Link
                key={link.href}
                href={link.href}
                aria-current={isActive(link.href) ? 'page' : undefined}
                className={cx(
                  'rounded-lg px-3 py-2 text-sm font-medium transition',
                  isActive(link.href)
                    ? 'bg-brand-50 text-brand-700'
                    : 'text-ink-600 hover:bg-ink-100 hover:text-ink-900',
                )}
              >
                {link.label}
              </Link>
            ))}
          </nav>
        </div>

        <div className="flex items-center gap-2">
          <div className="relative hidden md:block">
            <button
              type="button"
              onClick={() => setAccountOpen((open) => !open)}
              className="flex items-center gap-2 rounded-lg px-2 py-1.5 text-sm hover:bg-ink-100"
              aria-expanded={accountOpen}
              aria-haspopup="menu"
            >
              <span className="flex h-8 w-8 items-center justify-center rounded-full bg-brand-100 text-xs font-semibold text-brand-700">
                {initials(user.fullName)}
              </span>
              <span className="max-w-[10rem] truncate font-medium text-ink-700">
                {user.fullName}
              </span>
            </button>
            {accountOpen && (
              <div
                className="absolute right-0 mt-2 w-56 overflow-hidden rounded-xl border border-ink-100 bg-white py-1 shadow-lift"
                role="menu"
              >
                <div className="border-b border-ink-100 px-4 py-3">
                  <p className="truncate text-sm font-medium text-ink-900">{user.fullName}</p>
                  <p className="truncate text-xs text-ink-500">{user.email}</p>
                </div>
                <Link
                  href="/settings"
                  className="block px-4 py-2 text-sm text-ink-700 hover:bg-ink-50"
                  role="menuitem"
                >
                  Settings
                </Link>
                <button
                  type="button"
                  onClick={signOut}
                  className="block w-full px-4 py-2 text-left text-sm text-ink-700 hover:bg-ink-50"
                  role="menuitem"
                >
                  Sign out
                </button>
              </div>
            )}
          </div>

          <button
            type="button"
            className="rounded-lg p-2 text-ink-600 hover:bg-ink-100 md:hidden"
            onClick={() => setMenuOpen((open) => !open)}
            aria-expanded={menuOpen}
            aria-label={menuOpen ? 'Close menu' : 'Open menu'}
          >
            <svg viewBox="0 0 24 24" className="h-6 w-6" fill="none" stroke="currentColor" strokeWidth="2">
              {menuOpen ? (
                <path strokeLinecap="round" d="M6 6l12 12M18 6L6 18" />
              ) : (
                <path strokeLinecap="round" d="M4 7h16M4 12h16M4 17h16" />
              )}
            </svg>
          </button>
        </div>
      </div>

      {menuOpen && (
        <nav className="border-t border-ink-100 bg-white px-4 py-3 md:hidden" aria-label="Main">
          <div className="mb-3 flex items-center gap-3 rounded-lg bg-ink-50 px-3 py-2.5">
            <span className="flex h-9 w-9 items-center justify-center rounded-full bg-brand-100 text-xs font-semibold text-brand-700">
              {initials(user.fullName)}
            </span>
            <div className="min-w-0">
              <p className="truncate text-sm font-medium text-ink-900">{user.fullName}</p>
              <p className="truncate text-xs text-ink-500">{user.email}</p>
            </div>
          </div>
          {[...links, { href: '/settings', label: 'Settings' }].map((link) => (
            <Link
              key={link.href}
              href={link.href}
              className={cx(
                'block rounded-lg px-3 py-2.5 text-sm font-medium',
                isActive(link.href) ? 'bg-brand-50 text-brand-700' : 'text-ink-700 hover:bg-ink-100',
              )}
            >
              {link.label}
            </Link>
          ))}
          <Button variant="secondary" className="mt-3 w-full" onClick={signOut}>
            Sign out
          </Button>
        </nav>
      )}
    </header>
  )
}
