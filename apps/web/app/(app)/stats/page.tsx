'use client'

import { useMemo } from 'react'

import { Card, EmptyState, ErrorNote, PageHeader, Progress, Skeleton, cn } from '@/components/ui'
import { useAuth } from '@/lib/auth'
import { useCollection, userPath } from '@/lib/db'
import { addDays, durationLabel, startOfDay, startOfWeek, toDate } from '@/lib/time'
import type {
  ClassSlot,
  PtSession,
  PtType,
  StudySession,
  Subject,
  Task,
} from '@/lib/types'

const PT_LABEL: Record<PtType, string> = {
  run: 'Running',
  football: 'Football',
  gym: 'Gym',
  other: 'Other',
}

const PT_COLOR: Record<PtType, string> = {
  run: 'indigo',
  football: 'emerald',
  gym: 'amber',
  other: 'violet',
}

function dayKey(date: Date): string {
  return `${date.getFullYear()}-${date.getMonth()}-${date.getDate()}`
}

function barHeight(value: number, max: number): number {
  return Math.max(value > 0 ? 4 : 2, Math.round((value / Math.max(max, 1)) * 100))
}

function totalMinutes(sessions: Array<{ startedAt: unknown; durationMinutes: number }>, from?: Date): number {
  return sessions.reduce((total, session) => {
    const started = toDate(session.startedAt as never)
    if (!started) return total
    if (from && started < from) return total
    return total + session.durationMinutes
  }, 0)
}

export default function StatsPage() {
  const { user } = useAuth()

  const study = useCollection<StudySession>(user ? userPath(user.uid, 'studySessions') : null)
  const tasks = useCollection<Task>(user ? userPath(user.uid, 'tasks') : null)
  const entries = useCollection<ClassSlot>(user ? userPath(user.uid, 'classes') : null)
  const subjects = useCollection<Subject>(user ? userPath(user.uid, 'subjects') : null)
  const pt = useCollection<PtSession>(user ? userPath(user.uid, 'ptSessions') : null)

  const weekStart = useMemo(() => startOfWeek(new Date()), [])
  const days = useMemo(() => {
    const start = addDays(startOfDay(new Date()), -13)
    return Array.from({ length: 14 }, (_, index) => addDays(start, index))
  }, [])

  const studyByDay = useMemo(() => {
    const map = new Map<string, number>()
    for (const session of study.items) {
      const started = toDate(session.startedAt)
      if (!started) continue
      map.set(dayKey(started), (map.get(dayKey(started)) ?? 0) + session.durationMinutes)
    }
    return map
  }, [study.items])

  const ptByDay = useMemo(() => {
    const map = new Map<string, number>()
    for (const session of pt.items) {
      const started = toDate(session.startedAt)
      if (!started) continue
      map.set(dayKey(started), (map.get(dayKey(started)) ?? 0) + session.durationMinutes)
    }
    return map
  }, [pt.items])

  const studyWeek = totalMinutes(study.items, weekStart)
  const studyAll = totalMinutes(study.items)
  const ptWeek = totalMinutes(pt.items, weekStart)
  const ptAll = totalMinutes(pt.items)
  const kmWeek = pt.items.reduce((total, session) => {
    const started = toDate(session.startedAt)
    return started && started >= weekStart ? total + (session.distanceKm ?? 0) : total
  }, 0)

  const doneTasks = tasks.items.filter((task) => task.status === 'done')
  const doneThisWeek = doneTasks.filter((task) => {
    const completed = toDate(task.completedAt)
    return completed !== null && completed >= weekStart
  })
  const openTasks = tasks.items.length - doneTasks.length

  const bySubject = useMemo(() => {
    return subjects.items
      .map((subject) => ({
        subject,
        minutes: study.items.reduce((total, session) => {
          const started = toDate(session.startedAt)
          return session.subjectId === subject.id && started && started >= weekStart
            ? total + session.durationMinutes
            : total
        }, 0),
      }))
      .sort((a, b) => b.minutes - a.minutes)
  }, [subjects.items, study.items, weekStart])

  const byType = useMemo(() => {
    const types: PtType[] = ['run', 'football', 'gym', 'other']
    return types
      .map((type) => ({
        type,
        minutes: pt.items.reduce(
          (total, session) => (session.type === type ? total + session.durationMinutes : total),
          0,
        ),
      }))
      .filter((row) => row.minutes > 0)
      .sort((a, b) => b.minutes - a.minutes)
  }, [pt.items])

  const studyMax = Math.max(60, ...days.map((day) => studyByDay.get(dayKey(day)) ?? 0))
  const ptMax = Math.max(60, ...days.map((day) => ptByDay.get(dayKey(day)) ?? 0))
  const subjectMax = Math.max(1, ...bySubject.map((row) => row.minutes))
  const typeMax = Math.max(1, ...byType.map((row) => row.minutes))

  const loading = study.loading || tasks.loading || subjects.loading || pt.loading

  if (!user) return null

  return (
    <div className="space-y-10">
      <PageHeader
        title="Stats"
        description="What the week actually looked like — study on one side, training on the other."
      />

      {study.error ?? pt.error ? (
        <ErrorNote error={study.error ?? pt.error} what="your stats" />
      ) : null}

      <section aria-labelledby="academic-stats">
        <h2 id="academic-stats" className="mb-3 text-base font-medium text-ink">
          Academics
        </h2>

        <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
          <Card className="p-4">
            <p className="text-xs uppercase tracking-wide text-ink-muted">Studied this week</p>
            <p className="mt-2 text-xxl font-semibold tabular-nums text-ink">
              {durationLabel(studyWeek)}
            </p>
            <p className="mt-1 text-sm text-ink-muted">all time {durationLabel(studyAll)}</p>
          </Card>
          <Card className="p-4">
            <p className="text-xs uppercase tracking-wide text-ink-muted">Tasks done</p>
            <p className="mt-2 text-xxl font-semibold tabular-nums text-ink">{doneThisWeek.length}</p>
            <p className="mt-1 text-sm text-ink-muted">
              {doneTasks.length} all time · {openTasks} open
            </p>
          </Card>
          <Card className="p-4">
            <p className="text-xs uppercase tracking-wide text-ink-muted">Schedule</p>
            <p className="mt-2 text-xxl font-semibold tabular-nums text-ink">{entries.items.length}</p>
            <p className="mt-1 text-sm text-ink-muted">weekly classes and events</p>
          </Card>
          <Card className="p-4">
            <p className="text-xs uppercase tracking-wide text-ink-muted">Subjects</p>
            <p className="mt-2 text-xxl font-semibold tabular-nums text-ink">
              {subjects.items.length}
            </p>
            <p className="mt-1 text-sm text-ink-muted">active this term</p>
          </Card>
        </div>

        <div className="mt-4 grid gap-4 lg:grid-cols-2">
          <Card className="p-5">
            <p className="text-sm font-medium text-ink">Study, last 14 days</p>
            <div className="mt-4 flex h-28 items-end gap-1.5">
              {days.map((day) => {
                const value = studyByDay.get(dayKey(day)) ?? 0
                return (
                  <div key={day.toISOString()} className="flex flex-1 flex-col items-center gap-1">
                    <div className="flex h-full w-full items-end">
                      <div
                        className={cn('w-full rounded-t-[3px]', value > 0 ? 'bg-accent' : 'bg-border')}
                        style={{ height: `${barHeight(value, studyMax)}%` }}
                        title={`${durationLabel(value)}`}
                      />
                    </div>
                    <span className="text-[9px] text-ink-muted">{day.getDate()}</span>
                  </div>
                )
              })}
            </div>
          </Card>

          <Card className="p-5">
            <p className="text-sm font-medium text-ink">Study by subject, this week</p>
            {loading ? (
              <Skeleton className="mt-4 h-24 w-full" />
            ) : bySubject.length === 0 ? (
              <p className="mt-4 text-sm text-ink-muted">No subjects yet.</p>
            ) : (
              <ul className="mt-4 space-y-3">
                {bySubject.slice(0, 5).map(({ subject, minutes }) => (
                  <li key={subject.id}>
                    <div className="flex items-baseline justify-between text-sm">
                      <span className="truncate text-ink">{subject.name}</span>
                      <span className="tabular-nums text-ink-muted">{durationLabel(minutes)}</span>
                    </div>
                    <Progress
                      className="mt-1.5"
                      value={subjectMax > 0 ? minutes / subjectMax : 0}
                      label={`${subject.name} share of study this week`}
                    />
                  </li>
                ))}
              </ul>
            )}
          </Card>
        </div>
      </section>

      <section aria-labelledby="training-stats">
        <h2 id="training-stats" className="mb-3 text-base font-medium text-ink">
          Physical training
        </h2>

        <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
          <Card className="p-4">
            <p className="text-xs uppercase tracking-wide text-ink-muted">Trained this week</p>
            <p className="mt-2 text-xxl font-semibold tabular-nums text-ink">
              {durationLabel(ptWeek)}
            </p>
            <p className="mt-1 text-sm text-ink-muted">all time {durationLabel(ptAll)}</p>
          </Card>
          <Card className="p-4">
            <p className="text-xs uppercase tracking-wide text-ink-muted">Sessions</p>
            <p className="mt-2 text-xxl font-semibold tabular-nums text-ink">
              {pt.items.filter((session) => {
                const started = toDate(session.startedAt)
                return started !== null && started >= weekStart
              }).length}
            </p>
            <p className="mt-1 text-sm text-ink-muted">{pt.items.length} all time</p>
          </Card>
          <Card className="p-4">
            <p className="text-xs uppercase tracking-wide text-ink-muted">Distance</p>
            <p className="mt-2 text-xxl font-semibold tabular-nums text-ink">
              {Math.round(kmWeek * 10) / 10}
              <span className="ml-1 text-base font-medium text-ink-muted">km</span>
            </p>
            <p className="mt-1 text-sm text-ink-muted">this week</p>
          </Card>
          <Card className="p-4">
            <p className="text-xs uppercase tracking-wide text-ink-muted">Balance</p>
            <p className="mt-2 text-xxl font-semibold tabular-nums text-ink">
              {studyWeek + ptWeek > 0
                ? `${Math.round((studyWeek / (studyWeek + ptWeek)) * 100)}%`
                : '—'}
            </p>
            <p className="mt-1 text-sm text-ink-muted">study vs training</p>
          </Card>
        </div>

        <div className="mt-4 grid gap-4 lg:grid-cols-2">
          <Card className="p-5">
            <p className="text-sm font-medium text-ink">Training, last 14 days</p>
            <div className="mt-4 flex h-28 items-end gap-1.5">
              {days.map((day) => {
                const value = ptByDay.get(dayKey(day)) ?? 0
                return (
                  <div key={day.toISOString()} className="flex flex-1 flex-col items-center gap-1">
                    <div className="flex h-full w-full items-end">
                      <div
                        className={cn(
                          'w-full rounded-t-[3px]',
                          value > 0 ? 'bg-success' : 'bg-border',
                        )}
                        style={{ height: `${barHeight(value, ptMax)}%` }}
                        title={`${durationLabel(value)}`}
                      />
                    </div>
                    <span className="text-[9px] text-ink-muted">{day.getDate()}</span>
                  </div>
                )
              })}
            </div>
          </Card>

          <Card className="p-5">
            <p className="text-sm font-medium text-ink">Training by type, all time</p>
            {byType.length === 0 ? (
              <EmptyState
                title="No training logged"
                description="Log a run or a gym session in the Training tab."
              />
            ) : (
              <ul className="mt-4 space-y-3">
                {byType.map((row) => (
                  <li key={row.type}>
                    <div className="flex items-baseline justify-between text-sm">
                      <span className="flex items-center gap-2 text-ink">
                        <span
                          aria-hidden
                          className="size-2 rounded-pill"
                          style={{ backgroundColor: `var(--tag-${PT_COLOR[row.type]})` }}
                        />
                        {PT_LABEL[row.type]}
                      </span>
                      <span className="tabular-nums text-ink-muted">
                        {durationLabel(row.minutes)}
                      </span>
                    </div>
                    <Progress
                      className="mt-1.5"
                      value={typeMax > 0 ? row.minutes / typeMax : 0}
                      label={`${PT_LABEL[row.type]} share of training`}
                    />
                  </li>
                ))}
              </ul>
            )}
          </Card>
        </div>
      </section>
    </div>
  )
}
