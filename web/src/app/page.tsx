import Link from 'next/link'
import { Logo } from '@/components/Nav'
import { Button } from '@/components/ui'

const STEPS = [
  {
    title: 'Upload your resume',
    body: 'PDF or Word, up to 5 MB. We read it the way an applicant tracking system does — not the way a person does.',
  },
  {
    title: 'See exactly what the machine saw',
    body: 'The extracted text, the sections it recognised, and the details it could not find. Most tools give you a score. This is the part that tells you why.',
  },
  {
    title: 'Get a score where every point is explained',
    body: 'Five categories, and every deduction quotes the line of your resume that caused it. A score you can argue with is a score you can act on.',
  },
  {
    title: 'Paste the job you are applying for',
    body: 'A match percentage, the requirements you meet, and the gaps ranked by how much they matter for that specific posting.',
  },
  {
    title: 'Rehearse that interview',
    body: 'Questions built from your own resume and the gaps against that job, scored on structure, specifics, relevance, and clarity.',
  },
]

const PRINCIPLES = [
  {
    title: 'Candidate-side only',
    body: 'We never screen, rank, or reject real applicants for an employer. The person whose resume is analysed owns it.',
  },
  {
    title: 'Never fabricates experience',
    body: 'Suggestions rewrite what you already wrote. Where a number is needed, you are asked for it rather than given one.',
  },
  {
    title: 'No face or emotion analysis',
    body: 'Interview feedback comes from the content of your answers. There is no camera, no microphone, and no affect scoring.',
  },
  {
    title: 'Every finding shows its evidence',
    body: 'A score without a quote from the resume it came from is an assertion, not analysis.',
  },
]

export default function LandingPage() {
  return (
    <div className="min-h-screen bg-white">
      <header className="border-b border-ink-100">
        <div className="mx-auto flex h-16 max-w-6xl items-center justify-between px-4 sm:px-6">
          <Logo />
          <div className="flex items-center gap-2">
            <Button as={Link} href="/login" variant="ghost" size="sm">
              Sign in
            </Button>
            <Button as={Link} href="/register" size="sm">
              Get started
            </Button>
          </div>
        </div>
      </header>

      <main>
        <section className="mx-auto max-w-6xl px-4 py-16 sm:px-6 sm:py-24">
          <div className="max-w-3xl animate-fade-up">
            <p className="text-sm font-semibold uppercase tracking-wide text-brand-600">
              Resume analysis with evidence
            </p>
            <h1 className="mt-3 text-4xl font-semibold tracking-tight text-ink-950 sm:text-5xl sm:leading-[1.1]">
              Find out why your resume gets rejected — before you send it again.
            </h1>
            <p className="mt-6 text-lg leading-relaxed text-ink-600">
              You apply to 200 internships. You hear nothing from 190, get rejected by 9, and are
              told why by exactly zero. CareerPilot shows you what screening software actually reads
              from your resume, scores it against the rules those systems use, and quotes your own
              words back as the reason for every point lost.
            </p>
            <div className="mt-8 flex flex-col gap-3 sm:flex-row">
              <Button as={Link} href="/register" size="lg">
                Analyse my resume
              </Button>
              <Button as={Link} href="/login" variant="secondary" size="lg">
                I already have an account
              </Button>
            </div>
          </div>
        </section>

        <section className="border-y border-ink-100 bg-ink-50">
          <div className="mx-auto max-w-6xl px-4 py-16 sm:px-6 sm:py-20">
            <h2 className="text-2xl font-semibold tracking-tight text-ink-900 sm:text-3xl">
              One loop, start to finish
            </h2>
            <p className="mt-2 max-w-2xl text-ink-600">
              Not a grid of features. A single path from an upload to an interview you are ready for.
            </p>

            <ol className="mt-10 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
              {STEPS.map((step, index) => (
                <li key={step.title} className="card p-6">
                  <span className="inline-flex h-8 w-8 items-center justify-center rounded-lg bg-brand-600 text-sm font-semibold text-white">
                    {index + 1}
                  </span>
                  <h3 className="mt-4 text-base font-semibold text-ink-900">{step.title}</h3>
                  <p className="mt-2 text-sm leading-6 text-ink-600">{step.body}</p>
                </li>
              ))}
            </ol>
          </div>
        </section>

        <section className="mx-auto max-w-6xl px-4 py-16 sm:px-6 sm:py-20">
          <h2 className="text-2xl font-semibold tracking-tight text-ink-900 sm:text-3xl">
            What this will not do
          </h2>
          <p className="mt-2 max-w-2xl text-ink-600">
            Four commitments that constrain every feature. They are limits, and they are deliberate.
          </p>

          <div className="mt-10 grid gap-5 sm:grid-cols-2">
            {PRINCIPLES.map((principle) => (
              <div key={principle.title} className="rounded-xl border border-ink-100 p-6">
                <h3 className="text-base font-semibold text-ink-900">{principle.title}</h3>
                <p className="mt-2 text-sm leading-6 text-ink-600">{principle.body}</p>
              </div>
            ))}
          </div>
        </section>

        <section className="border-t border-ink-100 bg-ink-950">
          <div className="mx-auto max-w-6xl px-4 py-16 text-center sm:px-6">
            <h2 className="text-2xl font-semibold tracking-tight text-white sm:text-3xl">
              See what the machine reads from your resume
            </h2>
            <p className="mx-auto mt-3 max-w-xl text-ink-300">
              Free to start. One upload is enough to find out whether your layout is costing you
              interviews.
            </p>
            <Button as={Link} href="/register" size="lg" className="mt-8">
              Create a free account
            </Button>
          </div>
        </section>
      </main>

      <footer className="border-t border-ink-100 bg-white">
        <div className="mx-auto flex max-w-6xl flex-col gap-3 px-4 py-8 text-sm text-ink-400 sm:flex-row sm:items-center sm:justify-between sm:px-6">
          <Logo />
          <p>CareerPilot AI — your resume is yours.</p>
        </div>
      </footer>
    </div>
  )
}
