import { redirect } from 'next/navigation'
import { getSessionUser } from '@/lib/auth'

export const metadata = { robots: { index: false, follow: false } }

/** Keeps a signed-in user off the login and register screens. */
export default async function AuthLayout({ children }: { children: React.ReactNode }) {
  const user = await getSessionUser()
  if (user) redirect('/dashboard')
  return <>{children}</>
}
