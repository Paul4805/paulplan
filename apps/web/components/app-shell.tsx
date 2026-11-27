'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { useEffect, type ReactNode } from 'react'

import { InPageReminders } from '@/components/reminders'
import { cn } from '@/components/ui'
import { useAuth } from '@/lib/auth'

const NAV = [
  { href: '/', label: 'Today' },
  { href: '/timetable', label: 'Timetable' },
  { href: '/tasks', label: 'Tasks' },
  { href: '/subjects', label: 'Subjects' },
  { href: '/focus', label: 'Focus' },
  { href: '/pt', label: 'Training' },
  { href: '/stats', label: 'Stats' },
] as const

const MOBILE_NAV = [
  { href: '/', label: 'Today' },
  { href: '/timetable', label: 'Timetable' },
  { href: '/tasks', label: 'Tasks' },
  { href: '/pt', label: 'Training' },
  { href: '/stats', label: 'Stats' },
] as const

function currentPath(pathname: string): string {
  const trimmed = pathname.replace(/\/+$/, '')
  return trimmed === '' ? '/' : trimmed
}

function Mark() {
  return (
    <span className="flex size-7 items-center justify-center rounded-chip bg-accent-solid text-xs font-semibold tracking-tight text-accent-fg">
      P
    </span>
  )
}

function SettingsIcon() {
  return (
    <svg viewBox="0 0 24 24" className="size-5" fill="none" stroke="currentColor" strokeWidth="1.6">
      <circle cx="12" cy="12" r="3" />
      <path d="M19.4 15a1.7 1.7 0 0 0 .34 1.87l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.7 1.7 0 0 0-1.87-.34 1.7 1.7 0 0 0-1 1.55V21a2 2 0 1 1-4 0v-.09a1.7 1.7 0 0 0-1.11-1.55 1.7 1.7 0 0 0-1.87.34l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.7 1.7 0 0 0 .34-1.87 1.7 1.7 0 0 0-1.55-1H3a2 2 0 1 1 0-4h.09a1.7 1.7 0 0 0 1.55-1.11 1.7 1.7 0 0 0-.34-1.87l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.7 1.7 0 0 0 1.87.34H9a1.7 1.7 0 0 0 1-1.55V3a2 2 0 1 1 4 0v.09a1.7 1.7 0 0 0 1 1.55 1.7 1.7 0 0 0 1.87-.34l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.7 1.7 0 0 0-.34 1.87V9a1.7 1.7 0 0 0 1.55 1H21a2 2 0 1 1 0 4h-.09a1.7 1.7 0 0 0-1.51 1z" />
    </svg>
  )
}

function ServiceWorker() {
  useEffect(() => {
    if (!('serviceWorker' in navigator)) return
    // A cache-first SW serves stale chunks against the dev server. Only take
    // over in production, where the exported build is immutable per deploy.
    if (process.env.NODE_ENV !== 'production') return
    navigator.serviceWorker.register('/sw.js').catch((error) => {
      console.error('Service worker registration failed', error)
    })
  }, [])
  return null
}

export function AppShell({ children }: { children: ReactNode }) {
  const pathname = usePathname()
  const { user } = useAuth()
  const path = currentPath(pathname)
  const title = path === '/subjects' ? 'Subjects' : (NAV.find((item) => item.href === path)?.label ?? 'PaulPlan')

  return (
    <div className="min-h-dvh bg-canvas">
      <ServiceWorker />

      {/* desktop sidebar */}
      <aside className="fixed inset-y-0 left-0 z-30 hidden w-60 flex-col border-r border-border bg-canvas px-3 py-5 lg:flex">
        <Link href="/" className="mb-8 flex items-center gap-2 px-2">
          <Mark />
          <span className="text-base font-semibold tracking-tight text-ink">PaulPlan</span>
        </Link>

        <nav aria-label="Primary" className="flex flex-col gap-0.5">
          {NAV.map((item) => {
            const active = path === item.href
            return (
              <Link
                key={item.href}
                href={item.href}
                aria-current={active ? 'page' : undefined}
                className={cn(
                  'rounded-chip px-3 py-2 text-sm font-medium transition-colors duration-fast',
                  active
                    ? 'bg-accent-subtle text-accent-text'
                    : 'text-ink-secondary hover:bg-surface-elevated hover:text-ink',
                )}
              >
                {item.label}
              </Link>
            )
          })}
        </nav>

        <div className="mt-auto flex flex-col gap-1 border-t border-border pt-3">
          <Link
            href="/settings"
            aria-current={path === '/settings' ? 'page' : undefined}
            className={cn(
              'flex items-center gap-2 rounded-chip px-3 py-2 text-sm font-medium transition-colors duration-fast',
              path === '/settings'
                ? 'bg-accent-subtle text-accent-text'
                : 'text-ink-secondary hover:bg-surface-elevated hover:text-ink',
            )}
          >
            <SettingsIcon />
            Settings
          </Link>
          <p className="truncate px-3 pt-1 text-xs text-ink-muted">{user?.email}</p>
        </div>
      </aside>

      {/* mobile header */}
      <header className="sticky top-0 z-30 flex h-14 items-center justify-between border-b border-border bg-canvas px-4 lg:hidden">
        <Link href="/" className="flex items-center gap-2">
          <Mark />
          <span className="text-base font-semibold tracking-tight text-ink">{title}</span>
        </Link>
        <Link
          href="/settings"
          aria-label="Settings"
          className="rounded-chip p-2 text-ink-muted transition-colors duration-fast hover:bg-surface-elevated hover:text-ink"
        >
          <SettingsIcon />
        </Link>
      </header>

      <div className="lg:pl-60">
        <InPageReminders />
        <main className="mx-auto w-full max-w-5xl px-4 pb-28 pt-6 sm:px-6 lg:pb-16 lg:pt-10">
          {children}
        </main>
      </div>

      {/* mobile bottom tabs */}
      <nav
        aria-label="Primary"
        className="fixed inset-x-0 bottom-0 z-30 border-t border-border bg-canvas pb-[env(safe-area-inset-bottom)] lg:hidden"
      >
        <ul className="mx-auto flex max-w-md items-stretch">
          {MOBILE_NAV.map((item) => {
            const active = path === item.href
            return (
              <li key={item.href} className="flex-1">
                <Link
                  href={item.href}
                  aria-current={active ? 'page' : undefined}
                  className={cn(
                    'flex h-14 flex-col items-center justify-center text-xs font-medium transition-colors duration-fast',
                    active ? 'text-accent-text' : 'text-ink-muted hover:text-ink',
                  )}
                >
                  {item.label}
                </Link>
              </li>
            )
          })}
        </ul>
      </nav>
    </div>
  )
}
