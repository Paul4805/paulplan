'use client'

import { useRouter } from 'next/navigation'
import { useEffect, useState } from 'react'

import { Button, Field, Input } from '@/components/ui'
import {
  describeAuthError,
  registerWithEmail,
  sendReset,
  signInWithEmail,
  signInWithGoogle,
  useAuth,
} from '@/lib/auth'
import { useToast } from '@/lib/toast'

export default function LoginPage() {
  const { user, loading } = useAuth()
  const router = useRouter()
  const toast = useToast()

  const [mode, setMode] = useState<'signin' | 'signup'>('signin')
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!loading && user) router.replace('/')
  }, [loading, user, router])

  async function withBusy(action: () => Promise<void>) {
    setBusy(true)
    setError(null)
    try {
      await action()
    } catch (cause) {
      const message = describeAuthError(cause)
      setError(message)
      toast({ title: 'Could not sign in', description: message, tone: 'danger' })
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="flex min-h-dvh items-center justify-center px-4 py-12">
      <div className="w-full max-w-sm">
        <div className="mb-8 flex items-center gap-2">
          <span className="flex size-8 items-center justify-center rounded-chip bg-accent-solid text-xs font-semibold text-accent-fg">
            P
          </span>
          <span className="text-lg font-semibold tracking-tight text-ink">PaulPlan</span>
        </div>

        <div className="rounded-card border border-border bg-surface p-6">
          <h1 className="text-xl font-semibold tracking-tight text-ink">
            {mode === 'signin' ? 'Sign in' : 'Create your account'}
          </h1>
          <p className="mt-1 text-sm text-ink-muted">
            {mode === 'signin'
              ? 'Your timetable and tasks, on every device.'
              : 'One account for the web and your phone.'}
          </p>

          <Button
            variant="secondary"
            className="mt-6 w-full"
            disabled={busy}
            onClick={() => withBusy(signInWithGoogle)}
          >
            <svg viewBox="0 0 24 24" className="size-4" aria-hidden>
              <path
                fill="currentColor"
                d="M21.6 12.23c0-.68-.06-1.33-.18-1.96H12v3.71h5.38a4.6 4.6 0 0 1-2 3.02v2.51h3.24c1.9-1.75 2.98-4.33 2.98-7.28Z"
              />
              <path
                fill="currentColor"
                opacity=".75"
                d="M12 22c2.7 0 4.96-.9 6.62-2.43l-3.24-2.51c-.9.6-2.05.96-3.38.96-2.6 0-4.8-1.76-5.59-4.12H3.06v2.59A10 10 0 0 0 12 22Z"
              />
              <path
                fill="currentColor"
                opacity=".5"
                d="M6.41 13.9a6 6 0 0 1 0-3.8V7.51H3.06a10 10 0 0 0 0 8.98l3.35-2.59Z"
              />
              <path
                fill="currentColor"
                opacity=".9"
                d="M12 5.98c1.47 0 2.79.5 3.83 1.5l2.87-2.87A9.6 9.6 0 0 0 12 2a10 10 0 0 0-8.94 5.51l3.35 2.59C7.2 7.74 9.4 5.98 12 5.98Z"
              />
            </svg>
            Continue with Google
          </Button>

          <div className="my-5 flex items-center gap-3">
            <span className="h-px flex-1 bg-border" />
            <span className="text-xs uppercase tracking-widest text-ink-muted">or</span>
            <span className="h-px flex-1 bg-border" />
          </div>

          <form
            className="space-y-3"
            onSubmit={(event) => {
              event.preventDefault()
              void withBusy(async () => {
                if (mode === 'signin') await signInWithEmail(email.trim(), password)
                else await registerWithEmail(name, email.trim(), password)
              })
            }}
          >
            {mode === 'signup' ? (
              <Field label="Name" htmlFor="name">
                <Input
                  id="name"
                  autoComplete="name"
                  value={name}
                  onChange={(event) => setName(event.target.value)}
                  placeholder="Your name"
                />
              </Field>
            ) : null}

            <Field label="Email" htmlFor="email">
              <Input
                id="email"
                type="email"
                inputMode="email"
                autoComplete="email"
                required
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                placeholder="you@college.edu"
              />
            </Field>

            <Field label="Password" htmlFor="password">
              <Input
                id="password"
                type="password"
                autoComplete={mode === 'signin' ? 'current-password' : 'new-password'}
                required
                minLength={6}
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                placeholder="At least six characters"
              />
            </Field>

            {error ? (
              <p role="alert" className="text-sm text-danger">
                {error}
              </p>
            ) : null}

            <Button type="submit" className="w-full" disabled={busy}>
              {busy ? 'Working…' : mode === 'signin' ? 'Sign in' : 'Create account'}
            </Button>
          </form>

          <div className="mt-4 flex flex-col gap-2 text-sm">
            <button
              type="button"
              className="text-left text-ink-muted transition-colors hover:text-ink"
              onClick={() => {
                setMode(mode === 'signin' ? 'signup' : 'signin')
                setError(null)
              }}
            >
              {mode === 'signin' ? 'No account yet? Create one' : 'Already have an account? Sign in'}
            </button>
            {mode === 'signin' ? (
              <button
                type="button"
                className="text-left text-ink-muted transition-colors hover:text-ink"
                onClick={() =>
                  void withBusy(async () => {
                    if (!email.trim()) throw new Error('Enter your email first, then tap reset.')
                    await sendReset(email.trim())
                    toast({ title: 'Reset link sent', description: `Check ${email.trim()}.` })
                  })
                }
              >
                Forgot password?
              </button>
            ) : null}
          </div>
        </div>

        <p className="mt-6 text-center text-xs leading-relaxed text-ink-muted">
          Reminders fire on your phone. The web dashboard shows them while it is open.
        </p>
      </div>
    </main>
  )
}
