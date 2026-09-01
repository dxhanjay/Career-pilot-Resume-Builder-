import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import Layout from './components/Layout'
import { Loading } from './components/ui'
import { useAuth } from './lib/auth'

import Landing from './pages/Landing'
import Login from './pages/Login'
import Register from './pages/Register'
import VerifyEmail from './pages/VerifyEmail'
import ForgotPassword from './pages/ForgotPassword'
import ResetPassword from './pages/ResetPassword'
import Dashboard from './pages/Dashboard'
import Resumes from './pages/Resumes'
import ResumeDetail from './pages/ResumeDetail'
import JobDescriptions from './pages/JobDescriptions'
import JobDescriptionDetail from './pages/JobDescriptionDetail'
import Interviews from './pages/Interviews'
import InterviewRoom from './pages/InterviewRoom'
import Settings from './pages/Settings'
import Admin from './pages/Admin'
import NotFound from './pages/NotFound'

/**
 * Waits for the session check before deciding.
 *
 * Redirecting while `loading` is true bounces a signed-in user to /login on
 * every hard refresh — the token is in storage but has not been verified yet.
 */
function RequireAuth({ children }) {
  const { isAuthenticated, loading } = useAuth()
  const location = useLocation()

  if (loading) return <FullPageLoading />
  if (!isAuthenticated) {
    // Remember where they were headed so the login can return them there.
    return <Navigate to="/login" replace state={{ from: location }} />
  }
  return children
}

function RequireAdmin({ children }) {
  const { isAdmin, loading } = useAuth()
  if (loading) return <FullPageLoading />
  if (!isAdmin) return <Navigate to="/dashboard" replace />
  return children
}

/** Keeps a signed-in user off the login and register screens. */
function RedirectIfAuthenticated({ children }) {
  const { isAuthenticated, loading } = useAuth()
  if (loading) return <FullPageLoading />
  if (isAuthenticated) return <Navigate to="/dashboard" replace />
  return children
}

function FullPageLoading() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-ink-50">
      <Loading label="Checking your session…" />
    </div>
  )
}

export default function App() {
  const { isAuthenticated, loading } = useAuth()

  return (
    <Routes>
      <Route
        path="/"
        element={
          loading ? (
            <FullPageLoading />
          ) : isAuthenticated ? (
            <Navigate to="/dashboard" replace />
          ) : (
            <Landing />
          )
        }
      />

      <Route
        path="/login"
        element={
          <RedirectIfAuthenticated>
            <Login />
          </RedirectIfAuthenticated>
        }
      />
      <Route
        path="/register"
        element={
          <RedirectIfAuthenticated>
            <Register />
          </RedirectIfAuthenticated>
        }
      />
      <Route path="/verify-email" element={<VerifyEmail />} />
      <Route path="/forgot-password" element={<ForgotPassword />} />
      <Route path="/reset-password" element={<ResetPassword />} />

      <Route
        element={
          <RequireAuth>
            <Layout />
          </RequireAuth>
        }
      >
        <Route path="/dashboard" element={<Dashboard />} />
        <Route path="/resumes" element={<Resumes />} />
        <Route path="/resumes/:id" element={<ResumeDetail />} />
        <Route path="/jobs" element={<JobDescriptions />} />
        <Route path="/jobs/:id" element={<JobDescriptionDetail />} />
        <Route path="/interviews" element={<Interviews />} />
        <Route path="/interviews/:id" element={<InterviewRoom />} />
        <Route path="/settings" element={<Settings />} />
        <Route
          path="/admin"
          element={
            <RequireAdmin>
              <Admin />
            </RequireAdmin>
          }
        />
      </Route>

      <Route path="*" element={<NotFound />} />
    </Routes>
  )
}
