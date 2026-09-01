import { Link } from 'react-router-dom'
import { Logo } from '../components/Layout'
import { Button } from '../components/ui'
import { useAuth } from '../lib/auth'

export default function NotFound() {
  const { isAuthenticated } = useAuth()

  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-ink-50 px-4 text-center">
      <Link to="/" className="rounded-md">
        <Logo />
      </Link>
      <p className="mt-10 text-sm font-semibold uppercase tracking-wide text-brand-600">404</p>
      <h1 className="mt-2 text-3xl font-semibold tracking-tight text-ink-900">
        There is nothing at this address
      </h1>
      <p className="mt-3 max-w-md text-ink-500">
        The link may be out of date, or the page may have moved.
      </p>
      <Button as={Link} to={isAuthenticated ? '/dashboard' : '/'} size="lg" className="mt-8">
        {isAuthenticated ? 'Back to your dashboard' : 'Back to the home page'}
      </Button>
    </div>
  )
}
