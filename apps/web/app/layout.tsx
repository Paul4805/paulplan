import type { Metadata, Viewport } from 'next'
import { Inter } from 'next/font/google'

import { Providers } from './providers'
import './tokens.generated.css'
import './globals.css'

const inter = Inter({
  subsets: ['latin'],
  variable: '--font-inter',
  display: 'swap',
})

export const metadata: Metadata = {
  title: {
    default: 'Slot',
    template: '%s · Slot',
  },
  description: 'A minimal college schedule manager: timetable, tasks, and study progress.',
  applicationName: 'Slot',
  manifest: '/manifest.webmanifest',
  appleWebApp: { capable: true, title: 'Slot', statusBarStyle: 'default' },
  icons: { icon: '/icon.svg', apple: '/icon.svg' },
}

export const viewport: Viewport = {
  themeColor: [
    { media: '(prefers-color-scheme: light)', color: '#FAFAFA' },
    { media: '(prefers-color-scheme: dark)', color: '#0B0B0F' },
  ],
  width: 'device-width',
  initialScale: 1,
  viewportFit: 'cover',
}

const THEME_BOOTSTRAP = `try{var t=localStorage.getItem('slot-theme');var r=document.documentElement;if(t==='light')r.classList.add('light');else if(t==='dark')r.classList.add('dark')}catch(e){}`

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className={inter.variable} suppressHydrationWarning>
      <body className="min-h-dvh bg-canvas font-sans text-ink antialiased">
        <script dangerouslySetInnerHTML={{ __html: THEME_BOOTSTRAP }} />
        <Providers>{children}</Providers>
      </body>
    </html>
  )
}
