import { Link } from 'react-router-dom'
import { Logo } from './Layout'

/**
 * The frame around every signed-out form.
 *
 * The right-hand panel is hidden below `lg`. On a phone, a marketing column
 * above a login form means scrolling past the pitch every time you sign in.
 */
export default function AuthShell({ title, description, children, footer }) {
  return (
    <div className="min-h-screen lg:grid lg:grid-cols-2">
      <div className="flex min-h-screen flex-col px-4 py-10 sm:px-8 lg:min-h-0 lg:px-16 lg:py-14">
        <Link to="/" className="inline-flex w-fit rounded-md">
          <Logo />
        </Link>

        <div className="flex flex-1 flex-col justify-center py-10">
          <div className="mx-auto w-full max-w-sm animate-fade-up">
            <h1 className="text-2xl font-semibold tracking-tight text-ink-900">{title}</h1>
            {description && <p className="mt-2 text-sm text-ink-500">{description}</p>}
            <div className="mt-8">{children}</div>
            {footer && <div className="mt-6 text-sm text-ink-500">{footer}</div>}
          </div>
        </div>
      </div>

      <aside className="hidden bg-ink-950 lg:flex lg:flex-col lg:justify-center lg:px-16">
        <blockquote className="max-w-md">
          <p className="text-2xl font-medium leading-relaxed text-white">
            A student applies to 200 internships, hears nothing from 190, gets rejected by 9, and is
            told why by exactly&nbsp;zero.
          </p>
          <footer className="mt-6 text-sm leading-6 text-ink-300">
            CareerPilot shows you what the machine actually read from your resume, scores it against
            the rules screeners use, and quotes your own words back as the evidence for every
            finding.
          </footer>
        </blockquote>

        <ul className="mt-12 max-w-md space-y-4 text-sm text-ink-300">
          {[
            'See the parse — the text, the sections, and what was missed',
            'An ATS score where every point lost names a line',
            'Paste a job description for a match percentage and ranked gaps',
            'Rehearse the interview that specific job will give you',
          ].map((item) => (
            <li key={item} className="flex gap-3">
              <svg viewBox="0 0 20 20" className="mt-0.5 h-5 w-5 shrink-0 fill-brand-400" aria-hidden="true">
                <path
                  fillRule="evenodd"
                  d="M10 18a8 8 0 1 0 0-16 8 8 0 0 0 0 16Zm3.857-9.809a.75.75 0 0 0-1.214-.882l-3.483 4.79-1.88-1.88a.75.75 0 1 0-1.06 1.061l2.5 2.5a.75.75 0 0 0 1.137-.089l4-5.5Z"
                  clipRule="evenodd"
                />
              </svg>
              {item}
            </li>
          ))}
        </ul>
      </aside>
    </div>
  )
}
