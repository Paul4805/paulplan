'use client'

import { Timestamp } from 'firebase/firestore'
import { useCallback, useEffect, useRef, useState } from 'react'

import {
  Badge,
  Button,
  Card,
  EmptyState,
  ErrorNote,
  Field,
  PageHeader,
  Select,
  Skeleton,
} from '@/components/ui'
import { useAuth } from '@/lib/auth'
import { createDoc, useCollection, useDoc, userPath } from '@/lib/db'
import { tagDot } from '@/lib/tag'
import { DAY_LABELS, dayOfWeek, durationLabel, isSameDay, toDate } from '@/lib/time'
import { useToast } from '@/lib/toast'
import type { Profile, StudySession, Subject } from '@/lib/types'

type Phase = 'focus' | 'short' | 'long'

interface TimerState {
  phase: Phase
  running: boolean
  endsAt: number | null
  remainingSec: number
  subjectId: string
}

const STORE_KEY = 'slot-pomodoro'

interface LoadedTimer {
  state: TimerState
  expired: boolean
}

function loadTimer(): LoadedTimer | null {
  try {
    const raw = localStorage.getItem(STORE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as TimerState
    if (!parsed || typeof parsed !== 'object') return null
    if (parsed.running && parsed.endsAt) {
      const remaining = Math.max(0, Math.round((parsed.endsAt - Date.now()) / 1000))
      return {
        state: { ...parsed, remainingSec: remaining, running: remaining > 0 },
        expired: remaining === 0,
      }
    }
    return { state: parsed, expired: false }
  } catch {
    return null
  }
}

function formatClock(totalSeconds: number): string {
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`
}

export default function FocusPage() {
  const { user } = useAuth()
  const toast = useToast()

  const subjects = useCollection<Subject>(user ? userPath(user.uid, 'subjects') : null)
  const sessions = useCollection<StudySession>(user ? userPath(user.uid, 'studySessions') : null)
  const profile = useDoc<Profile & { id: string }>(
    user ? userPath(user.uid, 'meta/profile') : null,
  )

  const focusMinutes = profile.item?.pomodoro?.focus ?? 25
  const shortMinutes = profile.item?.pomodoro?.short ?? 5
  const longMinutes = profile.item?.pomodoro?.long ?? 15

  const [timer, setTimer] = useState<TimerState>({
    phase: 'focus',
    running: false,
    endsAt: null,
    remainingSec: focusMinutes * 60,
    subjectId: '',
  })
  const [hydrated, setHydrated] = useState(false)
  const [recovered, setRecovered] = useState<TimerState | null>(null)
  const finishedFor = useRef<number | null>(null)

  useEffect(() => {
    const loaded = loadTimer()
    if (loaded) {
      setTimer(loaded.state)
      if (loaded.expired) setRecovered(loaded.state)
    }
    setHydrated(true)
  }, [])

  useEffect(() => {
    if (!hydrated) return
    try {
      localStorage.setItem(STORE_KEY, JSON.stringify(timer))
    } catch {
      // Storage blocked: the timer still works for this session.
    }
  }, [timer, hydrated])

  useEffect(() => {
    if (!timer.running) return
    const interval = setInterval(() => {
      setTimer((current) => {
        if (!current.running || current.endsAt === null) return current
        const remainingSec = Math.max(0, Math.round((current.endsAt - Date.now()) / 1000))
        return { ...current, remainingSec }
      })
    }, 500)
    return () => clearInterval(interval)
  }, [timer.running])

  const finish = useCallback(
    async (completed: TimerState) => {
      if (!user) return
      if (completed.endsAt !== null && finishedFor.current === completed.endsAt) return
      finishedFor.current = completed.endsAt

      const wasFocus = completed.phase === 'focus'
      const minutes = wasFocus
        ? focusMinutes
        : completed.phase === 'short'
          ? shortMinutes
          : longMinutes

      const nextPhase: Phase = wasFocus ? 'short' : 'focus'
      const nextMinutes = nextPhase === 'focus' ? focusMinutes : shortMinutes
      const next: TimerState = {
        ...completed,
        phase: nextPhase,
        running: false,
        endsAt: null,
        remainingSec: nextMinutes * 60,
      }

      // Commit the advanced state before the network write. A remount (or a
      // second tab) then sees a finished timer instead of logging twice.
      setTimer(next)
      try {
        localStorage.setItem(STORE_KEY, JSON.stringify(next))
      } catch {
        // Storage blocked; the in-memory state is still correct.
      }

      if (!wasFocus) return

      try {
        const ended = new Date()
        const started = new Date(ended.getTime() - minutes * 60_000)
        await createDoc(userPath(user.uid, 'studySessions'), {
          subjectId: completed.subjectId || null,
          taskId: null,
          startedAt: Timestamp.fromDate(started),
          endedAt: Timestamp.fromDate(ended),
          durationMinutes: minutes,
          source: 'pomodoro',
        })
        toast({ title: `${minutes} minutes logged`, tone: 'success' })
      } catch (cause) {
        console.error(cause)
        toast({ title: 'Focus finished, but the session did not save', tone: 'danger' })
      }
    },
    [user, focusMinutes, shortMinutes, longMinutes, toast],
  )

  useEffect(() => {
    if (timer.running && timer.remainingSec <= 0) void finish(timer)
  }, [timer, finish])

  useEffect(() => {
    if (!recovered) return
    setRecovered(null)
    void finish(recovered)
  }, [recovered, finish])

  if (!user) return null

  const duration = timer.phase === 'focus' ? focusMinutes : timer.phase === 'short' ? shortMinutes : longMinutes
  const total = duration * 60
  const elapsedRatio = total > 0 ? 1 - timer.remainingSec / total : 0
  const ring = Math.max(0, Math.min(1, elapsedRatio))
  const circumference = 2 * Math.PI * 52

  const today = new Date()
  const todaySessions = sessions.items
    .filter((session) => {
      const started = toDate(session.startedAt)
      return started !== null && isSameDay(started, today)
    })
    .sort((a, b) => (b.startedAt?.toMillis() ?? 0) - (a.startedAt?.toMillis() ?? 0))

  const minutesToday = todaySessions.reduce(
    (totalMinutes, session) => totalMinutes + (session.durationMinutes ?? 0),
    0,
  )

  function setPhase(phase: Phase) {
    const minutes = phase === 'focus' ? focusMinutes : phase === 'short' ? shortMinutes : longMinutes
    setTimer((current) => ({
      ...current,
      phase,
      running: false,
      endsAt: null,
      remainingSec: minutes * 60,
    }))
  }

  function toggle() {
    setTimer((current) => {
      if (current.running) {
        return {
          ...current,
          running: false,
          endsAt: null,
          remainingSec: Math.max(1, current.remainingSec),
        }
      }
      return {
        ...current,
        running: true,
        endsAt: Date.now() + current.remainingSec * 1000,
      }
    })
  }

  function reset() {
    setPhase(timer.phase)
  }

  function skip() {
    setTimer((current) => {
      const nextPhase: Phase = current.phase === 'focus' ? 'short' : 'focus'
      const minutes = nextPhase === 'focus' ? focusMinutes : shortMinutes
      return {
        ...current,
        phase: nextPhase,
        running: false,
        endsAt: null,
        remainingSec: minutes * 60,
      }
    })
  }

  return (
    <div>
      <PageHeader
        title="Focus"
        description="A pomodoro that logs itself. The timer keeps running if you close this tab."
      />

      {sessions.error ? (
        <div className="mb-4">
          <ErrorNote error={sessions.error} what="your study sessions" />
        </div>
      ) : null}

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_320px]">
        <Card className="flex flex-col items-center gap-6 p-6 sm:p-10">
          <div className="flex items-center gap-1 rounded-pill border border-border p-1">
            {(
              [
                ['focus', 'Focus'],
                ['short', 'Short break'],
                ['long', 'Long break'],
              ] as Array<[Phase, string]>
            ).map(([phase, label]) => (
              <button
                key={phase}
                type="button"
                onClick={() => setPhase(phase)}
                aria-pressed={timer.phase === phase}
                className={
                  timer.phase === phase
                    ? 'rounded-pill bg-accent-subtle px-3 py-1.5 text-sm font-medium text-accent-text'
                    : 'rounded-pill px-3 py-1.5 text-sm font-medium text-ink-muted hover:text-ink'
                }
              >
                {label}
              </button>
            ))}
          </div>

          <div className="relative flex size-64 items-center justify-center">
            <svg viewBox="0 0 120 120" className="absolute inset-0 size-full -rotate-90">
              <circle cx="60" cy="60" r="52" fill="none" stroke="var(--color-border)" strokeWidth="3" />
              <circle
                cx="60"
                cy="60"
                r="52"
                fill="none"
                stroke="var(--color-accent)"
                strokeWidth="3"
                strokeLinecap="round"
                strokeDasharray={circumference}
                strokeDashoffset={circumference * (1 - ring)}
                className="transition-[stroke-dashoffset] duration-base"
              />
            </svg>
            <div className="text-center">
              <p className="text-[56px] font-semibold leading-none tabular-nums tracking-tight text-ink">
                {hydrated ? formatClock(timer.remainingSec) : formatClock(duration * 60)}
              </p>
              <p className="mt-2 text-xs uppercase tracking-widest text-ink-muted">{timer.phase}</p>
            </div>
          </div>

          <div className="flex w-full max-w-sm flex-col gap-3">
            <Field label="Subject" htmlFor="focus-subject">
              <Select
                id="focus-subject"
                value={timer.subjectId}
                onChange={(event) =>
                  setTimer((current) => ({ ...current, subjectId: event.target.value }))
                }
              >
                <option value="">No subject</option>
                {subjects.items.map((subject) => (
                  <option key={subject.id} value={subject.id}>
                    {subject.name}
                  </option>
                ))}
              </Select>
            </Field>

            <div className="flex items-center justify-center gap-2 pt-1">
              <Button onClick={toggle} className="min-w-28">
                {timer.running ? 'Pause' : 'Start'}
              </Button>
              <Button variant="secondary" onClick={reset}>
                Reset
              </Button>
              <Button variant="ghost" onClick={skip}>
                Skip
              </Button>
            </div>
          </div>

          <p className="text-center text-xs leading-relaxed text-ink-muted">
            Focus sessions are written to Firestore when the timer reaches zero, so they sync to
            your phone and appear on the subject page.
          </p>
        </Card>

        <div className="space-y-6">
          <Card className="p-4">
            <div className="flex items-baseline justify-between">
              <p className="text-sm font-medium text-ink">Today</p>
              <p className="text-sm tabular-nums text-ink-muted">{durationLabel(minutesToday)}</p>
            </div>
            <p className="mt-1 text-xs text-ink-muted">
              {todaySessions.length} {todaySessions.length === 1 ? 'session' : 'sessions'} logged
            </p>
          </Card>

          <section aria-labelledby="recent-sessions">
            <h2 id="recent-sessions" className="mb-3 text-sm font-medium text-ink">
              Today&apos;s sessions
            </h2>
            {sessions.loading ? (
              <Skeleton className="h-16 w-full" />
            ) : todaySessions.length === 0 ? (
              <EmptyState
                title="No sessions yet"
                description="Start the timer. Or log one manually from a subject page."
              />
            ) : (
              <ul className="space-y-2">
                {todaySessions.map((session) => {
                  const subject = subjects.items.find((item) => item.id === session.subjectId)
                  const started = toDate(session.startedAt)
                  return (
                    <li
                      key={session.id}
                      className="flex items-center gap-3 rounded-card border border-border bg-surface p-3"
                    >
                      <span
                        aria-hidden
                        className="size-2 shrink-0 rounded-pill"
                        style={tagDot(subject?.color ?? 'indigo')}
                      />
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-sm text-ink">
                          {subject?.name ?? 'Unassigned'}
                        </p>
                        <p className="text-xs text-ink-muted">
                          {started
                            ? started.toLocaleTimeString(undefined, {
                                hour: '2-digit',
                                minute: '2-digit',
                              })
                            : '—'}
                          {' · '}
                          {DAY_LABELS[(dayOfWeek(started ?? today) - 1 + 7) % 7]}
                        </p>
                      </div>
                      <Badge tone="neutral">{durationLabel(session.durationMinutes)}</Badge>
                    </li>
                  )
                })}
              </ul>
            )}
          </section>
        </div>
      </div>
    </div>
  )
}
