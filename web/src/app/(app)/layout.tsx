import { redirect } from 'next/navigation'
import Nav from '@/components/Nav'
import { getSessionUser } from '@/lib/auth'

export const metadata = { robots: { index: false, follow: false } }

/**
 * The signed-in shell.
 *
 * The session is checked on the server, before anything renders. A client-side
 * guard would paint the page first and redirect after, which shows a flash of
 * the app to someone who is not signed in and a flash of the login screen to
 * someone who is.
 */
export default async function AppLayout({ children }: { children: React.ReactNode }) {
  const user = await getSessionUser()
  if (!user) redirect('/login')

  return (
    <div className="flex min-h-screen flex-col bg-ink-50">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50 focus:rounded-lg focus:bg-white focus:px-4 focus:py-2 focus:shadow-lift"
      >
        Skip to content
      </a>

      <Nav user={{ fullName: user.fullName, email: user.email, role: user.role }} />

      <main id="main" className="mx-auto w-full max-w-7xl flex-1 px-4 py-8 sm:px-6 lg:px-8">
        {children}
      </main>

      <footer className="border-t border-ink-100 bg-white">
        <div className="mx-auto flex max-w-7xl flex-col gap-2 px-4 py-6 text-xs text-ink-400 sm:flex-row sm:items-center sm:justify-between sm:px-6 lg:px-8">
          <p>CareerPilot AI — candidate-side only. Your resume is yours.</p>
          <p>Scores are explained, never asserted. Every finding quotes your own text.</p>
        </div>
      </footer>
    </div>
  )
}
