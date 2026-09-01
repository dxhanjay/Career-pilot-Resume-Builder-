import type { Metadata, Viewport } from 'next'
import './globals.css'

export const metadata: Metadata = {
  title: {
    default: 'CareerPilot AI',
    template: '%s · CareerPilot AI',
  },
  description:
    'CareerPilot AI shows you exactly what an applicant tracking system reads from your resume, scores it with evidence, and rehearses you for the interview a specific job will give you.',
  robots: {
    // The marketing page is indexable; every signed-in route is excluded per
    // route. Nothing behind authentication should appear in a search result.
    index: true,
    follow: true,
  },
  icons: {
    icon: [
      {
        url:
          "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 32 32'%3E%3Crect width='32' height='32' rx='8' fill='%231c63eb'/%3E%3Cpath d='M9 21V11h5.2a3.4 3.4 0 0 1 0 6.8H12V21H9Zm3-5.6h2a1.4 1.4 0 0 0 0-2.8h-2v2.8Z' fill='white'/%3E%3Ccircle cx='22' cy='19.5' r='2.5' fill='white'/%3E%3C/svg%3E",
      },
    ],
  },
}

export const viewport: Viewport = {
  themeColor: '#1c63eb',
  width: 'device-width',
  initialScale: 1,
  viewportFit: 'cover',
}

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  )
}
