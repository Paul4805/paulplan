'use client'

import { useMemo, useState } from 'react'

import {
  Card,
  EmptyState,
  ErrorNote,
  PageHeader,
  Progress,
  Segmented,
  Skeleton,
  cn,
} from '@/components/ui'
import { useAuth } from '@/lib/auth'
import { useCollection, useDoc, userPath } from '@/lib/db'
import { activityStreak, dateKey, rangeDays, rangeLabel, rangeStart, type StatsRange } from '@/lib/stats'
import { addDays, durationLabel, startOfDay, startOfWeek, toDate } from '@/lib/time'
import type {
  DailyHealth,
  GarminStatus,
  Profile,
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

function barHeight(value: number, max: number): number {
  return Math.max(value > 0 ? 5 : 2, Math.round((value / Math.max(max, 1)) * 100))
}

function totalMinutes(
  sessions: Array<{ startedAt: unknown; durationMinutes: number }>,
  from?: Date,
): number {
  return sessions.reduce((total, session) => {
    const started = toDate(session.startedAt as never)
    if (!started) return total
    if (from && started < from) return total
    return total + session.durationMinutes
  }, 0)
}

function average(values: Array<number | null | undefined>): number | null {
  const present = values.filter((value): value is number => typeof value === 'number')
  if (present.length === 0) return null
  return present.reduce((total, value) => total + value, 0) / present.length
}

function sleepLabel(minutes: number | null): string {
  if (minutes === null) return '—'
  const hours = Math.floor(minutes / 60)
  return `${hours}h ${Math.round(minutes % 60)}m`
}

function Heatmap({
  days,
  values,
  tone,
  label,
}: {
  days: Date[]
  values: Map<string, number>
  tone: 'accent' | 'success'
  label: string
}) {
  const max = Math.max(1, ...days.map((day) => values.get(dateKey(day)) ?? 0))
  const today = startOfDay(new Date())

  return (
    <div role="img" aria-label={label} className="flex gap-1 overflow-x-auto pb-1">
      {Array.from({ length: Math.ceil(days.length / 7) }, (_, weekIndex) => (
        <div key={weekIndex} className="flex flex-col gap-1">
          {days.slice(weekIndex * 7, weekIndex * 7 + 7).map((day) => {
            const value = values.get(dateKey(day)) ?? 0
            const future = day > today
            const level = value <= 0 ? 0 : Math.max(1, Math.ceil((value / max) * 4))
            return (
              <span
                key={day.toISOString()}
                title={`${day.toLocaleDateString(undefined, {
                  weekday: 'short',
                  day: 'numeric',
                  month: 'short',
                })} · ${durationLabel(value)}`}
                className={cn(
                  'size-3 rounded-[3px]',
                  future
                    ? 'bg-canvas'
                    : level === 0
                      ? 'bg-border'
                      : tone === 'accent'
                        ? 'bg-accent'
                        : 'bg-success',
                )}
                style={!future && level > 0 ? { opacity: 0.25 + level * 0.19 } : undefined}
              />
            )
          })}
        </div>
      ))}
    </div>
  )
}

function DayBars({
  days,
  values,
  tone,
}: {
  days: Date[]
  values: Map<string, number>
  tone: 'accent' | 'success'
}) {
  const max = Math.max(60, ...days.map((day) => values.get(dateKey(day)) ?? 0))
  const every = days.length > 14 ? Math.ceil(days.length / 8) : 1

  return (
    <div>
      <div className="flex h-28 items-stretch gap-1">
        {days.map((day) => {
          const value = values.get(dateKey(day)) ?? 0
          return (
            <div key={day.toISOString()} className="flex h-full flex-1 flex-col items-center justify-end">
              <div
                className={cn(
                  'w-full rounded-t-[3px]',
                  value > 0 ? (tone === 'accent' ? 'bg-accent' : 'bg-success') : 'bg-border',
                )}
                style={{ height: `${barHeight(value, max)}%` }}
                title={`${day.toLocaleDateString()} · ${durationLabel(value)}`}
              />
            </div>
          )
        })}
      </div>
      <div className="mt-1 flex gap-1">
        {days.map((day, index) => (
          <span
            key={day.toISOString()}
            className="flex-1 text-center text-[9px] tabular-nums text-ink-muted"
          >
            {index % every === 0 || index === days.length - 1 ? day.getDate() : ''}
          </span>
        ))}
      </div>
    </div>
  )
}

function HeatmapSummary({
  title,
  days,
  values,
  tone,
  streak,
}: {
  title: string
  days: Date[]
  values: Map<string, number>
  tone: 'accent' | 'success'
  streak: { current: number; longest: number; total: number }
}) {
  return (
    <Card className="p-5">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="min-w-0 flex-1">
          <div className="mb-3 flex items-baseline justify-between gap-3">
            <p className="text-sm font-medium text-ink">{title}</p>
            <span className="text-xs text-ink-muted">last 12 weeks</span>
          </div>
          <Heatmap
            days={days}
            values={values}
            tone={tone}
            label={`${title} minutes per day for the last 12 weeks`}
          />
        </div>
        <div className="sm:w-44 sm:text-right">
          <p className="text-lg font-semibold tabular-nums text-ink">
            {streak.current} day streak
          </p>
          <p className="mt-1 text-xs text-ink-muted">
            best {streak.longest} · {streak.total} active days
          </p>
        </div>
      </div>
    </Card>
  )
}

export default function StatsPage() {
  const { user } = useAuth()
  const [range, setRange] = useState<StatsRange>('week')
  const [section, setSection] = useState<'academics' | 'training'>('academics')

  const study = useCollection<StudySession>(user ? userPath(user.uid, 'studySessions') : null)
  const tasks = useCollection<Task>(user ? userPath(user.uid, 'tasks') : null)
  const subjects = useCollection<Subject>(user ? userPath(user.uid, 'subjects') : null)
  const pt = useCollection<PtSession>(user ? userPath(user.uid, 'ptSessions') : null)
  const health = useCollection<DailyHealth>(user ? userPath(user.uid, 'dailyHealth') : null)
  const garmin = useDoc<GarminStatus & { id: string }>(
    user ? userPath(user.uid, 'meta/garmin') : null,
  )
  const profile = useDoc<Profile & { id: string }>(
    user ? userPath(user.uid, 'meta/profile') : null,
  )

  const meta = profile.item

  const start = useMemo(() => rangeStart(range, meta), [range, meta])
  const days = useMemo(() => rangeDays(range, meta), [range, meta])

  const heatDays = useMemo(() => {
    const first = addDays(startOfWeek(new Date()), -77)
    return Array.from({ length: 84 }, (_, index) => addDays(first, index))
  }, [])

  const studyByDay = useMemo(() => {
    const map = new Map<string, number>()
    for (const session of study.items) {
      const started = toDate(session.startedAt)
      if (!started) continue
      map.set(dateKey(started), (map.get(dateKey(started)) ?? 0) + session.durationMinutes)
    }
    return map
  }, [study.items])

  const ptByDay = useMemo(() => {
    const map = new Map<string, number>()
    for (const session of pt.items) {
      const started = toDate(session.startedAt)
      if (!started) continue
      map.set(dateKey(started), (map.get(dateKey(started)) ?? 0) + session.durationMinutes)
    }
    return map
  }, [pt.items])

  const studyStreak = useMemo(() => {
    const today = startOfDay(new Date())
    const list = Array.from({ length: 84 }, (_, index) => {
      const day = addDays(today, index - 83)
      return { date: day, minutes: studyByDay.get(dateKey(day)) ?? 0 }
    })
    return {
      ...activityStreak(list),
      total: list.filter((day) => day.minutes > 0).length,
    }
  }, [studyByDay])

  const ptStreak = useMemo(() => {
    const today = startOfDay(new Date())
    const list = Array.from({ length: 84 }, (_, index) => {
      const day = addDays(today, index - 83)
      return { date: day, minutes: ptByDay.get(dateKey(day)) ?? 0 }
    })
    return {
      ...activityStreak(list),
      total: list.filter((day) => day.minutes > 0).length,
    }
  }, [ptByDay])

  const studyRange = totalMinutes(study.items, start)
  const studyAll = totalMinutes(study.items)
  const ptRange = totalMinutes(pt.items, start)
  const ptAll = totalMinutes(pt.items)
  const ptRangeCount = pt.items.filter((session) => {
    const started = toDate(session.startedAt)
    return started !== null && started >= start
  }).length
  const kmRange = pt.items.reduce((total, session) => {
    const started = toDate(session.startedAt)
    return started && started >= start ? total + (session.distanceKm ?? 0) : total
  }, 0)

  const doneTasks = tasks.items.filter((task) => task.status === 'done' && !task.archived)
  const doneInRange = doneTasks.filter((task) => {
    const completed = toDate(task.completedAt)
    return completed !== null && completed >= start
  })
  const openTasks = tasks.items.filter((task) => task.status !== 'done' && !task.archived).length

  const bySubject = useMemo(() => {
    return subjects.items
      .filter((subject) => !subject.archived)
      .map((subject) => ({
        subject,
        minutes: study.items.reduce((total, session) => {
          const started = toDate(session.startedAt)
          return session.subjectId === subject.id && started && started >= start
            ? total + session.durationMinutes
            : total
        }, 0),
      }))
      .filter((row) => row.minutes > 0)
      .sort((a, b) => b.minutes - a.minutes)
  }, [subjects.items, study.items, start])

  const byType = useMemo(() => {
    const types: PtType[] = ['run', 'football', 'gym', 'other']
    return types
      .map((type) => ({
        type,
        minutes: pt.items.reduce((total, session) => {
          const started = toDate(session.startedAt)
          return session.type === type && started && started >= start
            ? total + session.durationMinutes
            : total
        }, 0),
      }))
      .filter((row) => row.minutes > 0)
      .sort((a, b) => b.minutes - a.minutes)
  }, [pt.items, start])

  const recentHealth = useMemo(
    () =>
      health.items
        .filter((item) => {
          const parsed = new Date(`${item.date}T00:00:00`)
          return !Number.isNaN(parsed.getTime()) && parsed >= addDays(startOfDay(new Date()), -6)
        })
        .sort((a, b) => a.date.localeCompare(b.date)),
    [health.items],
  )

  const avgSleep = average(recentHealth.map((item) => item.sleepMinutes))
  const avgSteps = average(recentHealth.map((item) => item.steps))
  const avgHrv = average(recentHealth.map((item) => item.hrv))
  const latestHealth = recentHealth[recentHealth.length - 1] ?? null

  const subjectMax = Math.max(1, ...bySubject.map((row) => row.minutes))
  const typeMax = Math.max(1, ...byType.map((row) => row.minutes))

  const loading = study.loading || tasks.loading || subjects.loading || pt.loading

  if (!user) return null

  return (
    <div className="space-y-10">
      <PageHeader
        title="Stats"
        description="What the term actually looked like — study on one side, training and recovery on the other."
        actions={
          <Segmented<StatsRange>
            label="Stats range"
            value={range}
            onChange={setRange}
            options={[
              { value: 'week', label: 'Week' },
              { value: 'month', label: 'Month' },
              { value: 'term', label: 'Term' },
            ]}
          />
        }
      />

      {study.error ?? pt.error ? (
        <ErrorNote error={study.error ?? pt.error} what="your stats" />
      ) : null}

      <p className="text-xs uppercase tracking-wide text-ink-muted">
        {rangeLabel(range)}
        {meta?.termName && range === 'term' ? ` · ${meta.termName}` : ''}
      </p>

      <Segmented<'academics' | 'training'>
        label="Stats section"
        value={section}
        onChange={setSection}
        options={[
          { value: 'academics', label: 'Academics' },
          { value: 'training', label: 'Training' },
        ]}
      />

      {section === 'academics' ? (
        <section aria-labelledby="academic-stats">
          <div className="grid grid-cols-2 gap-3">
            <Card className="p-4">
              <p className="text-xs uppercase tracking-wide text-ink-muted">Studied</p>
              <p className="mt-2 text-xxl font-semibold tabular-nums text-ink">
                {durationLabel(studyRange)}
              </p>
              <p className="mt-1 text-sm text-ink-muted">all time {durationLabel(studyAll)}</p>
            </Card>
            <Card className="p-4">
              <p className="text-xs uppercase tracking-wide text-ink-muted">Tasks done</p>
              <p className="mt-2 text-xxl font-semibold tabular-nums text-ink">{doneInRange.length}</p>
              <p className="mt-1 text-sm text-ink-muted">
                {doneTasks.length} all time · {openTasks} open
              </p>
            </Card>
          </div>

        <div className="mt-4 grid gap-4 lg:grid-cols-2">
          <Card className="p-5">
            <div className="flex items-baseline justify-between">
              <p className="text-sm font-medium text-ink">Study, {rangeLabel(range).toLowerCase()}</p>
              <span className="text-sm tabular-nums text-ink-muted">{durationLabel(studyRange)}</span>
            </div>
            {loading ? (
              <Skeleton className="mt-4 h-28 w-full" />
            ) : (
              <div className="mt-4">
                <DayBars days={days} values={studyByDay} tone="accent" />
              </div>
            )}
          </Card>

          <Card className="p-5">
            <p className="text-sm font-medium text-ink">Study by subject</p>
            {loading ? (
              <Skeleton className="mt-4 h-24 w-full" />
            ) : bySubject.length === 0 ? (
              <p className="mt-4 text-sm text-ink-muted">No study in this range.</p>
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
                      label={`${subject.name} share of study`}
                    />
                  </li>
                ))}
              </ul>
            )}
          </Card>
        </div>

          <div className="mt-4">
            <HeatmapSummary
              title="Study heatmap"
              days={heatDays}
              values={studyByDay}
              tone="accent"
              streak={studyStreak}
            />
          </div>
        </section>
      ) : null}

      {section === 'training' ? (
        <section aria-labelledby="training-stats">
          <div className="mb-3 flex flex-wrap items-baseline justify-between gap-2">
            <p className="text-sm text-ink-muted">
              {studyRange + ptRange > 0
                ? `Study ${Math.round((studyRange / (studyRange + ptRange)) * 100)}% · training ${Math.round(
                    (ptRange / (studyRange + ptRange)) * 100,
                  )}%`
                : 'No study or training in this range'}
            </p>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <Card className="p-4">
              <p className="text-xs uppercase tracking-wide text-ink-muted">Trained</p>
              <p className="mt-2 text-xxl font-semibold tabular-nums text-ink">
                {durationLabel(ptRange)}
              </p>
              <p className="mt-1 text-sm text-ink-muted">all time {durationLabel(ptAll)}</p>
            </Card>
            <Card className="p-4">
              <p className="text-xs uppercase tracking-wide text-ink-muted">Sessions</p>
              <p className="mt-2 text-xxl font-semibold tabular-nums text-ink">{ptRangeCount}</p>
              <p className="mt-1 text-sm text-ink-muted">
                {pt.items.length} all time · {Math.round(kmRange * 10) / 10} km
              </p>
            </Card>
          </div>

        <div className="mt-4 grid gap-4 lg:grid-cols-2">
          <Card className="p-5">
            <div className="flex items-baseline justify-between">
              <p className="text-sm font-medium text-ink">Training, {rangeLabel(range).toLowerCase()}</p>
              <span className="text-sm tabular-nums text-ink-muted">{durationLabel(ptRange)}</span>
            </div>
            <div className="mt-4">
              <DayBars days={days} values={ptByDay} tone="success" />
            </div>
          </Card>

          <Card className="p-5">
            <p className="text-sm font-medium text-ink">Training by type</p>
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
                      <span className="tabular-nums text-ink-muted">{durationLabel(row.minutes)}</span>
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

          <div className="mt-4">
            <HeatmapSummary
              title="Training heatmap"
              days={heatDays}
              values={ptByDay}
              tone="success"
              streak={ptStreak}
            />
          </div>

        <div className="mt-8" aria-labelledby="wellbeing">
          <div className="mb-3 flex items-baseline justify-between">
            <h2 id="wellbeing" className="text-base font-medium text-ink">
              Recovery
            </h2>
            <span className="text-xs text-ink-muted">Garmin · last 7 days</span>
          </div>
          {health.loading ? (
            <Skeleton className="h-20 w-full" />
          ) : health.items.length === 0 ? (
            <Card className="p-4">
              <p className="text-sm text-ink-muted">
                {garmin.item?.healthError
                  ? `Garmin health sync is failing: ${garmin.item.healthError}`
                  : 'Connect Garmin in the Android app to see sleep, steps and HRV here.'}
              </p>
              {garmin.item?.healthLastSyncAt ? (
                <p className="mt-1 text-xs text-ink-muted">
                  Last health sync{' '}
                  {garmin.item.healthLastSyncAt.toDate().toLocaleString(undefined, {
                    day: 'numeric',
                    month: 'short',
                    hour: '2-digit',
                    minute: '2-digit',
                  })}
                </p>
              ) : null}
            </Card>
          ) : (
            <div className="grid grid-cols-3 gap-2">
              <Card className="p-3">
                <p className="text-[11px] uppercase tracking-wide text-ink-muted">Sleep</p>
                <p className="mt-1.5 text-lg font-semibold tabular-nums text-ink">
                  {latestHealth?.sleepMinutes != null ? sleepLabel(latestHealth.sleepMinutes) : '—'}
                </p>
                <p className="mt-1 text-[11px] text-ink-muted">
                  7d avg {sleepLabel(avgSleep)}
                  {latestHealth?.sleepScore ? ` · score ${latestHealth.sleepScore}` : ''}
                </p>
              </Card>
              <Card className="p-3">
                <p className="text-[11px] uppercase tracking-wide text-ink-muted">Steps</p>
                <p className="mt-1.5 text-lg font-semibold tabular-nums text-ink">
                  {latestHealth?.steps == null ? '—' : latestHealth.steps.toLocaleString()}
                </p>
                <p className="mt-1 text-[11px] text-ink-muted">
                  {avgSteps === null ? 'no data' : `7d avg ${Math.round(avgSteps).toLocaleString()}`}
                </p>
              </Card>
              <Card className="p-3">
                <p className="text-[11px] uppercase tracking-wide text-ink-muted">HRV</p>
                <p className="mt-1.5 text-lg font-semibold tabular-nums text-ink">
                  {latestHealth?.hrv == null ? '—' : Math.round(latestHealth.hrv)}
                  <span className="ml-1 text-xs font-medium text-ink-muted">ms</span>
                </p>
                <p className="mt-1 text-[11px] text-ink-muted">
                  {avgHrv === null ? 'no data' : `7d avg ${Math.round(avgHrv)} ms`}
                </p>
              </Card>
            </div>
          )}
        </div>
        </section>
      ) : null}
    </div>
  )
}
