'use client'

import Link from 'next/link'
import { useMemo, useState } from 'react'

import { ClassDialog } from '@/components/class-dialog'
import { ActionMenu } from '@/components/action-menu'
import { TaskDialog } from '@/components/task-dialog'
import {
  Badge,
  Button,
  Card,
  EmptyState,
  ErrorNote,
  PageHeader,
  Skeleton,
} from '@/components/ui'
import { useAuth } from '@/lib/auth'
import { useCollection, useDoc, useNow, userPath } from '@/lib/db'
import { tagDot } from '@/lib/tag'
import {
  dayOfWeek,
  formatDue,
  minutesNow,
  minutesToLabel,
  occursOnDate,
  termState,
  toDate,
} from '@/lib/time'
import type { ClassSlot, Profile, StudySession, Subject, Task } from '@/lib/types'

function greeting(hour: number): string {
  if (hour < 12) return 'Good morning'
  if (hour < 17) return 'Good afternoon'
  return 'Good evening'
}

export default function TodayPage() {
  const { user } = useAuth()
  const now = useNow(30_000)

  const classes = useCollection<ClassSlot>(user ? userPath(user.uid, 'classes') : null)
  const subjects = useCollection<Subject>(user ? userPath(user.uid, 'subjects') : null)
  const tasks = useCollection<Task>(user ? userPath(user.uid, 'tasks') : null)
  const sessions = useCollection<StudySession>(user ? userPath(user.uid, 'studySessions') : null)
  const profile = useDoc<Profile & { id: string }>(
    user ? userPath(user.uid, 'meta/profile') : null,
  )

  const [classOpen, setClassOpen] = useState(false)
  const [entryKind, setEntryKind] = useState<'class' | 'event'>('class')
  const [taskOpen, setTaskOpen] = useState(false)

  const today = now
  const todayMinutes = minutesNow(now)
  const weekday = dayOfWeek(today)
  const term = termState(profile.item, today)
  const termPaused = term === 'before' || term === 'after'

  const subjectById = useMemo(
    () => new Map(subjects.items.map((subject) => [subject.id, subject])),
    [subjects.items],
  )

  const entryLabel = (slot: ClassSlot): string => {
    const subject = slot.subjectId ? subjectById.get(slot.subjectId) : undefined
    return subject?.name ?? slot.title ?? 'Entry'
  }

  const entryColor = (slot: ClassSlot): string => {
    const subject = slot.subjectId ? subjectById.get(slot.subjectId) : undefined
    return slot.colorOverride ?? subject?.color ?? 'indigo'
  }

  const todaysClasses = useMemo(
    () =>
      termPaused
        ? []
        : classes.items
            .filter((slot) => occursOnDate(slot, today))
            .sort((a, b) => a.startMinutes - b.startMinutes),
    [classes.items, today, termPaused],
  )

  const currentClass = todaysClasses.find(
    (slot) => todayMinutes >= slot.startMinutes && todayMinutes < slot.endMinutes,
  )
  const nextClass = todaysClasses.find((slot) => slot.startMinutes > todayMinutes)

  const openTasks = useMemo(
    () => tasks.items.filter((task) => task.status !== 'done' && !task.archived),
    [tasks.items],
  )

  const dueSoon = useMemo(() => {
    const end = new Date(today)
    end.setDate(end.getDate() + 3)
    end.setHours(23, 59, 59, 999)

    return openTasks
      .filter((task) => {
        const due = toDate(task.dueAt)
        return due !== null && due <= end
      })
      .sort((a, b) => (a.dueAt?.toMillis() ?? 0) - (b.dueAt?.toMillis() ?? 0))
      .slice(0, 5)
  }, [openTasks, today])

  // Anything open, soonest deadline first, undated tasks last. A task with no
  // deadline must still be visible on Today, not only in the Tasks page.
  const upNext = useMemo(
    () =>
      [...openTasks]
        .sort(
          (a, b) =>
            (a.dueAt?.toMillis() ?? Number.MAX_SAFE_INTEGER) -
            (b.dueAt?.toMillis() ?? Number.MAX_SAFE_INTEGER),
        )
        .slice(0, 5),
    [openTasks],
  )

  const studyMinutesToday = useMemo(
    () =>
      sessions.items.reduce((total, session) => {
        const started = toDate(session.startedAt)
        if (!started) return total
        const sameDay =
          started.getFullYear() === today.getFullYear() &&
          started.getMonth() === today.getMonth() &&
          started.getDate() === today.getDate()
        return sameDay ? total + (session.durationMinutes ?? 0) : total
      }, 0),
    [sessions.items, today],
  )

  const loading = classes.loading || subjects.loading || tasks.loading || sessions.loading

  if (!user) return null

  return (
    <div>
      <PageHeader
        title={`${greeting(now.getHours())}.`}
        description={today.toLocaleDateString(undefined, {
          weekday: 'long',
          day: 'numeric',
          month: 'long',
        })}
        actions={
          <ActionMenu
            label="+ New"
            items={[
              {
                label: 'Task',
                description: 'Deadline or to-do',
                onSelect: () => setTaskOpen(true),
              },
              {
                label: 'Class',
                description: 'Repeats every week',
                onSelect: () => {
                  setEntryKind('class')
                  setClassOpen(true)
                },
              },
              {
                label: 'Event',
                description: 'One-off on a date',
                onSelect: () => {
                  setEntryKind('event')
                  setClassOpen(true)
                },
              },
            ]}
          />
        }
      />

      {classes.error ?? tasks.error ? (
        <div className="mb-6">
          <ErrorNote error={classes.error ?? tasks.error} what="your schedule" />
        </div>
      ) : null}

      <section aria-label="At a glance" className="grid grid-cols-2 gap-3">
        <Card className="p-4">
          <p className="text-xs font-medium uppercase tracking-wide text-ink-muted">
            {currentClass ? 'Now' : nextClass ? 'Up next' : 'Now'}
          </p>
          {currentClass ? (
            <>
              <p className="mt-2 truncate text-base font-medium text-ink">
                {entryLabel(currentClass)}
              </p>
              <p className="text-sm text-ink-muted">
                until {minutesToLabel(currentClass.endMinutes)}
              </p>
            </>
          ) : nextClass ? (
            <>
              <p className="mt-2 truncate text-base font-medium text-ink">
                {entryLabel(nextClass)}
              </p>
              <p className="text-sm text-ink-muted">
                in {nextClass.startMinutes - todayMinutes} min ·{' '}
                {minutesToLabel(nextClass.startMinutes)}
              </p>
            </>
          ) : (
            <>
              <p className="mt-2 text-base font-medium text-ink">Nothing scheduled</p>
              <p className="text-sm text-ink-muted">The rest of the day is yours</p>
            </>
          )}
        </Card>

        <Card className="p-4">
          <p className="text-xs font-medium uppercase tracking-wide text-ink-muted">Open tasks</p>
          <p className="mt-2 text-xxl font-semibold leading-none tracking-tight text-ink">
            {openTasks.length}
          </p>
          <p className="mt-1 text-sm text-ink-muted">
            {dueSoon.length} due soon · {studyMinutesToday}m studied today
          </p>
        </Card>
      </section>

      <div className="mt-8 grid gap-8 lg:grid-cols-2">
        <section aria-labelledby="today-classes">
          <div className="mb-3 flex items-center justify-between">
            <h2 id="today-classes" className="text-base font-medium text-ink">
              Today&apos;s schedule
            </h2>
            <Link href="/timetable" className="text-sm text-ink-muted hover:text-ink">
              Week view
            </Link>
          </div>

          {loading ? (
            <div className="space-y-2">
              <Skeleton className="h-14 w-full" />
              <Skeleton className="h-14 w-full" />
            </div>
          ) : termPaused ? (
            <div className="rounded-card border border-warning bg-surface px-4 py-6 text-center">
              <p className="text-sm font-medium text-ink">
                {term === 'before' ? 'The term has not started' : 'The term has ended'}
              </p>
              <p className="mt-1 text-sm text-ink-muted">
                Weekly classes are hidden. Adjust the dates in Settings.
              </p>
            </div>
          ) : todaysClasses.length === 0 ? (
            <EmptyState
              title="Nothing scheduled today"
              description="Add your weekly classes once, or drop in one-off events like talks and meetings."
              action={
                <div className="flex items-center gap-2">
                  <Button
                    size="sm"
                    variant="secondary"
                    onClick={() => {
                      setEntryKind('event')
                      setClassOpen(true)
                    }}
                  >
                    Add event
                  </Button>
                  <Button
                    size="sm"
                    onClick={() => {
                      setEntryKind('class')
                      setClassOpen(true)
                    }}
                  >
                    Add class
                  </Button>
                </div>
              }
            />
          ) : (
            <ul className="space-y-2">
              {todaysClasses.map((slot) => {
                const active =
                  todayMinutes >= slot.startMinutes && todayMinutes < slot.endMinutes
                return (
                  <li
                    key={slot.id}
                    className="flex items-center gap-3 rounded-card border border-border bg-surface p-3"
                  >
                    <span
                      aria-hidden
                      className="h-8 w-1 rounded-pill"
                      style={{ backgroundColor: `var(--tag-${entryColor(slot)})` }}
                    />
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium text-ink">{entryLabel(slot)}</p>
                      <p className="text-xs text-ink-muted">
                        {minutesToLabel(slot.startMinutes)}–{minutesToLabel(slot.endMinutes)}
                        {slot.room ? ` · ${slot.room}` : ''}
                      </p>
                    </div>
                    {active ? <Badge tone="accent">Now</Badge> : null}
                  </li>
                )
              })}
            </ul>
          )}
        </section>

        <section aria-labelledby="open-tasks">
          <div className="mb-3 flex items-center justify-between">
            <h2 id="open-tasks" className="text-base font-medium text-ink">
              Open tasks
            </h2>
            <Link href="/tasks" className="text-sm text-ink-muted hover:text-ink">
              All tasks
            </Link>
          </div>

          {loading ? (
            <div className="space-y-2">
              <Skeleton className="h-14 w-full" />
              <Skeleton className="h-14 w-full" />
            </div>
          ) : openTasks.length === 0 ? (
            <EmptyState
              title="No open tasks"
              description="Everything is done. Add a task when something comes up."
              action={
                <Button size="sm" variant="secondary" onClick={() => setTaskOpen(true)}>
                  New task
                </Button>
              }
            />
          ) : (
            <ul className="space-y-2">
              {upNext.map((task) => {
                const due = formatDue(task.dueAt)
                const subject = task.subjectId ? subjectById.get(task.subjectId) : undefined
                return (
                  <li
                    key={task.id}
                    className="flex items-start gap-3 rounded-card border border-border bg-surface p-3"
                  >
                    <span
                      aria-hidden
                      className="mt-1.5 size-2 shrink-0 rounded-pill"
                      style={tagDot(subject?.color ?? 'indigo')}
                    />
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium text-ink">{task.title}</p>
                      <p className="truncate text-xs text-ink-muted">{subject?.name ?? 'No subject'}</p>
                    </div>
                    {due ? (
                      <Badge tone={due.tone === 'danger' ? 'danger' : due.tone === 'warning' ? 'warning' : 'neutral'}>
                        {due.label}
                      </Badge>
                    ) : (
                      <Badge tone="neutral">No deadline</Badge>
                    )}
                  </li>
                )
              })}
              {openTasks.length > upNext.length ? (
                <li className="pt-1 text-center text-xs text-ink-muted">
                  +{openTasks.length - upNext.length} more open
                </li>
              ) : null}
            </ul>
          )}
        </section>
      </div>

      <div className="mt-6 flex gap-4 text-sm text-ink-muted lg:hidden">
        <Link href="/focus" className="hover:text-ink">
          Start focus
        </Link>
        <Link href="/subjects" className="hover:text-ink">
          Subjects
        </Link>
      </div>

      <ClassDialog
        open={classOpen}
        onClose={() => setClassOpen(false)}
        subjects={subjects.items}
        defaultDay={weekday}
        defaultKind={entryKind}
      />
      <TaskDialog
        open={taskOpen}
        onClose={() => setTaskOpen(false)}
        subjects={subjects.items}
      />
    </div>
  )
}
