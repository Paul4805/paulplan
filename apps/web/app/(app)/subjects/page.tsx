'use client'

import Link from 'next/link'
import { useSearchParams } from 'next/navigation'
import { Suspense, useMemo, useState } from 'react'
import { Timestamp } from 'firebase/firestore'

import { ClassDialog } from '@/components/class-dialog'
import { SubjectDialog } from '@/components/subject-dialog'
import { TaskDialog } from '@/components/task-dialog'
import {
  Badge,
  Button,
  Card,
  EmptyState,
  ErrorNote,
  Field,
  Input,
  PageHeader,
  Progress,
  Skeleton,
  cn,
} from '@/components/ui'
import { useAuth } from '@/lib/auth'
import { createDoc, useCollection, userPath } from '@/lib/db'
import { tagBar, tagDot } from '@/lib/tag'
import {
  DAY_LABELS,
  addDays,
  dayOfWeek,
  durationLabel,
  formatDue,
  isSameDay,
  minutesToLabel,
  startOfDay,
  toDate,
} from '@/lib/time'
import { useToast } from '@/lib/toast'
import type { ClassSlot, StudySession, Subject, Task } from '@/lib/types'

function weekMinutes(sessions: StudySession[], subjectId: string): number {
  const weekAgo = new Date()
  weekAgo.setDate(weekAgo.getDate() - 7)
  return sessions
    .filter((session) => {
      if (session.subjectId !== subjectId) return false
      const started = toDate(session.startedAt)
      return started !== null && started >= weekAgo
    })
    .reduce((total, session) => total + (session.durationMinutes ?? 0), 0)
}

function SubjectDetail({
  subject,
  subjects,
  classes,
  tasks,
  sessions,
}: {
  subject: Subject
  subjects: Subject[]
  classes: ClassSlot[]
  tasks: Task[]
  sessions: StudySession[]
}) {
  const { user } = useAuth()
  const toast = useToast()
  const [editOpen, setEditOpen] = useState(false)
  const [classOpen, setClassOpen] = useState(false)
  const [taskOpen, setTaskOpen] = useState(false)
  const [taskTarget, setTaskTarget] = useState<Task | null>(null)
  const [minutes, setMinutes] = useState('30')
  const [busy, setBusy] = useState(false)

  const slots = useMemo(
    () => classes.filter((slot) => slot.subjectId === subject.id).sort((a, b) => a.dayOfWeek - b.dayOfWeek || a.startMinutes - b.startMinutes),
    [classes, subject.id],
  )

  const subjectTasks = useMemo(
    () => tasks.filter((task) => task.subjectId === subject.id),
    [tasks, subject.id],
  )

  const doneTasks = subjectTasks.filter((task) => task.status === 'done').length
  const thisWeek = weekMinutes(sessions, subject.id)
  const targetMinutes = (subject.targetHoursPerWeek ?? 0) * 60
  const totalMinutes = sessions
    .filter((session) => session.subjectId === subject.id)
    .reduce((total, session) => total + (session.durationMinutes ?? 0), 0)

  const chart = useMemo(() => {
    const today = startOfDay(new Date())
    const days = Array.from({ length: 7 }, (_, index) => addDays(today, index - 6))
    const totals = days.map((day) =>
      sessions
        .filter((session) => {
          if (session.subjectId !== subject.id) return false
          const started = toDate(session.startedAt)
          return started !== null && isSameDay(started, day)
        })
        .reduce((total, session) => total + (session.durationMinutes ?? 0), 0),
    )
    return { days, totals, max: Math.max(60, ...totals) }
  }, [sessions, subject.id])

  async function logSession() {
    if (!user) return
    const value = Number(minutes)
    if (!Number.isFinite(value) || value <= 0) {
      toast({ title: 'Enter a positive number of minutes', tone: 'danger' })
      return
    }

    setBusy(true)
    try {
      const ended = new Date()
      const started = new Date(ended.getTime() - value * 60_000)
      await createDoc(userPath(user.uid, 'studySessions'), {
        subjectId: subject.id,
        taskId: null,
        startedAt: Timestamp.fromDate(started),
        endedAt: Timestamp.fromDate(ended),
        durationMinutes: Math.min(Math.round(value), 1440),
        source: 'manual',
      })
      toast({ title: `Logged ${value} minutes`, tone: 'success' })
    } catch (cause) {
      console.error(cause)
      toast({ title: 'Could not log the session', tone: 'danger' })
    } finally {
      setBusy(false)
    }
  }

  const orderedSubjects = [subject, ...subjects.filter((item) => item.id !== subject.id)]

  return (
    <div>
      <Link href="/subjects" className="mb-4 inline-flex items-center gap-1 text-sm text-ink-muted hover:text-ink">
        <svg viewBox="0 0 24 24" className="size-4" fill="none" stroke="currentColor" strokeWidth="1.8">
          <path d="M15 6l-6 6 6 6" strokeLinecap="round" />
        </svg>
        All subjects
      </Link>

      <PageHeader
        title={subject.name}
        description={[subject.code, subject.teacher, subject.room].filter(Boolean).join(' · ') || undefined}
        actions={
          <>
            <Button variant="secondary" size="sm" onClick={() => setEditOpen(true)}>
              Edit
            </Button>
            <Button size="sm" onClick={() => setClassOpen(true)}>
              Add class
            </Button>
          </>
        }
      />

      <section className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <Card className="p-4">
          <p className="text-xs uppercase tracking-wide text-ink-muted">This week</p>
          <p className="mt-2 text-xl font-semibold tabular-nums text-ink">
            {durationLabel(thisWeek)}
          </p>
        </Card>
        <Card className="p-4">
          <p className="text-xs uppercase tracking-wide text-ink-muted">Target</p>
          <p className="mt-2 text-xl font-semibold tabular-nums text-ink">
            {subject.targetHoursPerWeek ? `${subject.targetHoursPerWeek}h` : '—'}
          </p>
        </Card>
        <Card className="p-4">
          <p className="text-xs uppercase tracking-wide text-ink-muted">Tasks done</p>
          <p className="mt-2 text-xl font-semibold tabular-nums text-ink">
            {doneTasks}/{subjectTasks.length}
          </p>
        </Card>
        <Card className="p-4">
          <p className="text-xs uppercase tracking-wide text-ink-muted">All time</p>
          <p className="mt-2 text-xl font-semibold tabular-nums text-ink">
            {durationLabel(totalMinutes)}
          </p>
        </Card>
      </section>

      <div className="mt-4 space-y-2">
        <div className="flex items-center justify-between text-sm">
          <span className="text-ink-secondary">Weekly study target</span>
          <span className="tabular-nums text-ink-muted">
            {durationLabel(thisWeek)}
            {targetMinutes > 0 ? ` / ${durationLabel(targetMinutes)}` : ''}
          </span>
        </div>
        <Progress
          value={targetMinutes > 0 ? thisWeek / targetMinutes : 0}
          label={`${subject.name} weekly study target`}
        />
        <div className="flex items-center justify-between pt-2 text-sm">
          <span className="text-ink-secondary">Tasks completed</span>
          <span className="tabular-nums text-ink-muted">
            {subjectTasks.length > 0 ? `${doneTasks} of ${subjectTasks.length}` : 'No tasks'}
          </span>
        </div>
        <Progress
          value={subjectTasks.length > 0 ? doneTasks / subjectTasks.length : 0}
          label={`${subject.name} task completion`}
        />
      </div>

      <div className="mt-8 grid gap-8 lg:grid-cols-2">
        <section aria-labelledby="study-chart">
          <h2 id="study-chart" className="mb-3 text-base font-medium text-ink">
            Study, last 7 days
          </h2>
          <Card className="p-5">
            <div className="flex h-32 items-end gap-2">
              {chart.days.map((day, index) => {
                const value = chart.totals[index] ?? 0
                const height = Math.round((value / chart.max) * 100)
                return (
                  <div key={day.toISOString()} className="flex flex-1 flex-col items-center gap-2">
                    <div className="flex w-full flex-1 items-end">
                      <div
                        className={cn(
                          'w-full rounded-t-[4px]',
                          value > 0 ? 'bg-accent' : 'bg-border',
                        )}
                        style={{ height: `${Math.max(height, 3)}%` }}
                        title={`${durationLabel(value)}`}
                      />
                    </div>
                    <span className="text-[10px] uppercase text-ink-muted">
                      {DAY_LABELS[(dayOfWeek(day) - 1 + 7) % 7]?.slice(0, 1)}
                    </span>
                  </div>
                )
              })}
            </div>
          </Card>

          <div className="mt-4">
            <h3 className="mb-2 text-sm font-medium text-ink">Log a session</h3>
            <div className="flex items-end gap-2">
              <Field label="Minutes" htmlFor="log-minutes" className="flex-1">
                <Input
                  id="log-minutes"
                  type="number"
                  min="1"
                  max="1440"
                  inputMode="numeric"
                  value={minutes}
                  onChange={(event) => setMinutes(event.target.value)}
                />
              </Field>
              <Button onClick={logSession} disabled={busy}>
                Log
              </Button>
            </div>
          </div>
        </section>

        <section aria-labelledby="subject-slots">
          <h2 id="subject-slots" className="mb-3 text-base font-medium text-ink">
            Weekly slots
          </h2>
          {slots.length === 0 ? (
            <EmptyState
              title="No classes yet"
              description="Add the weekly slots for this subject."
              action={
                <Button size="sm" variant="secondary" onClick={() => setClassOpen(true)}>
                  Add class
                </Button>
              }
            />
          ) : (
            <ul className="space-y-2">
              {slots.map((slot) => (
                <li
                  key={slot.id}
                  className="flex items-center gap-3 rounded-card border border-border bg-surface p-3"
                >
                  <span aria-hidden className="h-8 w-1 rounded-pill" style={tagBar(subject.color)} />
                  <div className="min-w-0 flex-1">
                    <p className="text-sm font-medium text-ink">{DAY_LABELS[slot.dayOfWeek - 1]}</p>
                    <p className="text-xs text-ink-muted">
                      {minutesToLabel(slot.startMinutes)}–{minutesToLabel(slot.endMinutes)}
                      {slot.room ? ` · ${slot.room}` : ''}
                    </p>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>

      <section className="mt-8" aria-labelledby="subject-tasks">
        <div className="mb-3 flex items-center justify-between">
          <h2 id="subject-tasks" className="text-base font-medium text-ink">
            Tasks
          </h2>
          <Button
            size="sm"
            variant="secondary"
            onClick={() => {
              setTaskTarget(null)
              setTaskOpen(true)
            }}
          >
            New task
          </Button>
        </div>
        {subjectTasks.length === 0 ? (
          <EmptyState
            title="No tasks for this subject"
            description="Anything you add here shows up on the tasks board, already linked."
            action={
              <Button
                size="sm"
                variant="secondary"
                onClick={() => {
                  setTaskTarget(null)
                  setTaskOpen(true)
                }}
              >
                New task
              </Button>
            }
          />
        ) : (
          <ul className="space-y-2">
            {subjectTasks.map((task) => {
              const due = formatDue(task.dueAt)
              return (
                <li key={task.id}>
                  <button
                    type="button"
                    onClick={() => {
                      setTaskTarget(task)
                      setTaskOpen(true)
                    }}
                    className="flex w-full items-center gap-3 rounded-card border border-border bg-surface p-3 text-left"
                  >
                    <span aria-hidden className="size-2 rounded-pill" style={tagDot(subject.color)} />
                    <span className="min-w-0 flex-1">
                      <span
                        className={cn(
                          'block truncate text-sm font-medium',
                          task.status === 'done' ? 'text-ink-muted line-through' : 'text-ink',
                        )}
                      >
                        {task.title}
                      </span>
                      <span className="block text-xs text-ink-muted">
                        {task.priority} priority
                      </span>
                    </span>
                    {due ? (
                      <Badge
                        tone={
                          due.tone === 'danger' ? 'danger' : due.tone === 'warning' ? 'warning' : 'neutral'
                        }
                      >
                        {due.label}
                      </Badge>
                    ) : null}
                  </button>
                </li>
              )
            })}
          </ul>
        )}
      </section>

      <SubjectDialog open={editOpen} onClose={() => setEditOpen(false)} initial={subject} />
      <ClassDialog
        open={classOpen}
        onClose={() => setClassOpen(false)}
        subjects={orderedSubjects}
        defaultDay={dayOfWeek(new Date())}
      />
      <TaskDialog
        open={taskOpen}
        onClose={() => setTaskOpen(false)}
        subjects={orderedSubjects}
        initial={taskTarget}
        defaultSubjectId={subject.id}
      />
    </div>
  )
}

function SubjectsInner() {
  const { user } = useAuth()
  const params = useSearchParams()
  const selectedId = params.get('id')

  const subjects = useCollection<Subject>(user ? userPath(user.uid, 'subjects') : null)
  const classes = useCollection<ClassSlot>(user ? userPath(user.uid, 'classes') : null)
  const tasks = useCollection<Task>(user ? userPath(user.uid, 'tasks') : null)
  const sessions = useCollection<StudySession>(user ? userPath(user.uid, 'studySessions') : null)

  const [addOpen, setAddOpen] = useState(false)

  const selected = selectedId ? subjects.items.find((item) => item.id === selectedId) : undefined

  if (!user) return null

  if (subjects.loading) {
    return (
      <div className="space-y-3">
        <Skeleton className="h-10 w-48" />
        <Skeleton className="h-40 w-full" />
      </div>
    )
  }

  if (selected) {
    return (
      <SubjectDetail
        subject={selected}
        subjects={subjects.items}
        classes={classes.items}
        tasks={tasks.items}
        sessions={sessions.items}
      />
    )
  }

  return (
    <div>
      <PageHeader
        title="Subjects"
        description="Every subject gets a colour, a weekly target, and its own study history."
        actions={
          <Button size="sm" onClick={() => setAddOpen(true)}>
            New subject
          </Button>
        }
      />

      {subjects.error ? (
        <div className="mb-4">
          <ErrorNote error={subjects.error} what="your subjects" />
        </div>
      ) : null}

      {subjects.items.length === 0 ? (
        <EmptyState
          title="No subjects yet"
          description="Start with one subject from this term. You can add the rest as you go."
          action={
            <Button size="sm" onClick={() => setAddOpen(true)}>
              Add a subject
            </Button>
          }
        />
      ) : (
        <ul className="grid gap-3 sm:grid-cols-2">
          {subjects.items.map((subject) => {
            const weekly = weekMinutes(sessions.items, subject.id)
            const target = (subject.targetHoursPerWeek ?? 0) * 60
            const subjectTasks = tasks.items.filter((task) => task.subjectId === subject.id)
            const done = subjectTasks.filter((task) => task.status === 'done').length

            return (
              <li key={subject.id}>
                <Link
                  href={`/subjects/?id=${subject.id}`}
                  className="block rounded-card border border-border bg-surface p-4 transition-shadow duration-fast hover:shadow-popover"
                >
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <p className="flex items-center gap-2 truncate text-base font-medium text-ink">
                        <span aria-hidden className="size-2 rounded-pill" style={tagDot(subject.color)} />
                        {subject.name}
                      </p>
                      <p className="mt-0.5 truncate text-xs text-ink-muted">
                        {[subject.code, subject.teacher].filter(Boolean).join(' · ') || 'No code'}
                      </p>
                    </div>
                    <span className="shrink-0 text-xs tabular-nums text-ink-muted">
                      {durationLabel(weekly)}
                      {target > 0 ? ` / ${subject.targetHoursPerWeek}h` : ''}
                    </span>
                  </div>

                  <Progress
                    className="mt-4"
                    value={target > 0 ? weekly / target : 0}
                    label={`${subject.name} weekly progress`}
                  />

                  <p className="mt-3 text-xs text-ink-muted">
                    {subjectTasks.length > 0 ? `${done}/${subjectTasks.length} tasks done` : 'No tasks'}
                  </p>
                </Link>
              </li>
            )
          })}
        </ul>
      )}

      <SubjectDialog open={addOpen} onClose={() => setAddOpen(false)} />
    </div>
  )
}

export default function SubjectsPage() {
  return (
    <Suspense fallback={<Skeleton className="h-40 w-full" />}>
      <SubjectsInner />
    </Suspense>
  )
}
