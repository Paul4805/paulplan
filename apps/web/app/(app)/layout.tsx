'use client'

import { useRouter } from 'next/navigation'
import { useEffect } from 'react'

import { AppShell } from '@/components/app-shell'
import { useAuth } from '@/lib/auth'

export default function AppLayout({ children }: { children: React.ReactNode }) {
  const { user, loading } = useAuth()
  const router = useRouter()

  useEffect(() => {
    if (!loading && !user) router.replace('/login')
  }, [loading, user, router])

  if (loading) {
    return (
      <div className="flex min-h-dvh items-center justify-center bg-canvas">
        <div className="h-2 w-32 animate-pulse rounded-pill bg-border" />
        <span className="sr-only">Checking your session</span>
      </div>
    )
  }

  if (!user) return null

  return <AppShell>{children}</AppShell>
}
