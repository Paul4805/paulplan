'use client'

import { useEffect, useState, type ReactNode } from 'react'

import { AuthProvider } from '@/lib/auth'
import { ThemeProvider } from '@/lib/theme'
import { ToastProvider } from '@/lib/toast'

function BootScreen() {
  return (
    <div className="flex min-h-dvh flex-col items-center justify-center gap-4 bg-canvas">
      <span className="flex size-9 items-center justify-center rounded-card bg-accent-solid text-sm font-semibold text-accent-fg">
        P
      </span>
      <div className="space-y-2">
        <div className="h-2 w-32 animate-pulse rounded-pill bg-border" />
        <div className="h-2 w-24 animate-pulse rounded-pill bg-border" />
      </div>
      <span className="sr-only">Loading PaulPlan</span>
    </div>
  )
}

/**
 * Children are not rendered until after hydration. Every page is a client
 * component that reads Firestore, so this is what keeps static export's
 * build-time prerender away from browser-only Firebase initialisation.
 */
export function Providers({ children }: { children: ReactNode }) {
  const [mounted, setMounted] = useState(false)

  useEffect(() => {
    setMounted(true)
  }, [])

  return (
    <ThemeProvider>
      <ToastProvider>
        <AuthProvider>{mounted ? children : <BootScreen />}</AuthProvider>
      </ToastProvider>
    </ThemeProvider>
  )
}
