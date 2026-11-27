'use client'

import { Fragment, useEffect, useMemo, useState } from 'react'

import { ClassDialog } from '@/components/class-dialog'
import {
  Badge,
  Button,
  Card,
  ErrorNote,
  PageHeader,
  Segmented,
  Skeleton,
  cn,
} from '@/components/ui'
import { useAuth } from '@/lib/auth'
import { patchDoc, useCollection, useDoc, userPath } from '@/lib/db'
import { tagBar, tagTint } from '@/lib/tag'
import {
  DAY_LABELS,
  DAY_LONG,
  addDays,
  clamp,
  dateKeyLocal,
  dayOfWeek,
  isSameDay,
  minutesToLabel,
  occursOnDate,
  startOfWeek,
  termState,
  toDate,
  weekDates,
} from '@/lib/time'
import { useToast } from '@/lib/toast'
import type { ClassSlot, Profile, Subject, Task } from '@/lib/types'

const ROW_MINUTES = 30

export default function TimetablePage() {
  const { user } = useAuth()
  const toast = useToast()
  const classes = useCollection<ClassSlot>(user ? userPath(user.uid, 'classes') : null)
  const subjects = useCollection<Subject>(user ? userPath(user.uid, 'subjects') : null)
  const tasks = useCollection<Task>(user ? userPath(user.uid, 'tasks') : null)
  const profile = useDoc<Profile & { id: string }>(
    user ? userPath(user.uid, 'meta/profile') : null,
  )

  const [view, setView] = useState<'month' | 'week' | 'day'>('month')
  const [anchor, setAnchor] = useState(() => new Date())
  const [selectedDay, setSelectedDay] = useState(() => dayOfWeek(new Date()) - 1)
  const [dragId, setDragId] = useState<string | null>(null)
  const [dialog, setDialog] = useState<{ open: boolean; slot: ClassSlot | null; kind: 'class' | 'event' }>({
    open: false,
    slot: null,
    kind: 'class',
  })

  useEffect(() => {
    const small = window.matchMedia('(max-width: 639px)').matches
    let stored: 'month' | 'week' | 'day' | null = null
    try {
      const raw = localStorage.getItem('slot-timetable-view')
      if (raw === 'month' || raw === 'week' || raw === 'day') stored = raw
    } catch {
      // storage blocked; fall through to the default
    }
    setView(stored ?? (small ? 'day' : 'week'))
  }, [])

  const week = useMemo(
    () => weekDates(anchor, profile.item?.weekStartsOn ?? 1),
    [anchor, profile.item?.weekStartsOn],
  )

  const term = termState(profile.item)
  const termPaused = term === 'before' || term === 'after'
  const items = useMemo(() => (termPaused ? [] : classes.items), [termPaused, classes.items])

  const subjectById = useMemo(
    () => new Map(subjects.items.map((subject) => [subject.id, subject])),
    [subjects.items],
  )

  const { start, rows } = useMemo(() => {
    let min = 8 * 60
    let max = 18 * 60
    for (const slot of items) {
      if (slot.startMinutes < min) min = slot.startMinutes
      if (slot.endMinutes > max) max = slot.endMinutes
    }
    const from = Math.floor(clamp(min, 6 * 60, 12 * 60) / 60) * 60
    const to = Math.ceil(clamp(max, 14 * 60, 23 * 60) / 60) * 60
    return { start: from, end: to, rows: Math.max(1, Math.ceil((to - from) / ROW_MINUTES)) }
  }, [items])

  const slotsForDay = useMemo(() => {
    const map = new Map<number, ClassSlot[]>()
    for (const slot of items) {
      const list = map.get(slot.dayOfWeek) ?? []
      list.push(slot)
      map.set(slot.dayOfWeek, list)
    }
    for (const list of map.values()) list.sort((a, b) => a.startMinutes - b.startMinutes)
    return map
  }, [items])

  const monthStart = useMemo(
    () => new Date(anchor.getFullYear(), anchor.getMonth(), 1),
    [anchor],
  )

  const monthDays = useMemo(() => {
    const gridStart = startOfWeek(monthStart, 1)
    return Array.from({ length: 42 }, (_, index) => addDays(gridStart, index))
  }, [monthStart])

  const tasksByDay = useMemo(() => {
    const map = new Map<string, Task[]>()
    for (const task of tasks.items) {
      const due = toDate(task.dueAt)
      if (!due) continue
      const key = `${due.getFullYear()}-${due.getMonth()}-${due.getDate()}`
      const list = map.get(key) ?? []
      list.push(task)
      map.set(key, list)
    }
    return map
  }, [tasks.items])

  const dayKey = (date: Date) => `${date.getFullYear()}-${date.getMonth()}-${date.getDate()}`

  function shift(direction: number) {
    if (view === 'month') {
      setAnchor(new Date(anchor.getFullYear(), anchor.getMonth() + direction, 1))
    } else {
      setAnchor(addDays(anchor, 7 * direction))
    }
  }

  async function moveSlot(slotId: string, day: number, row: number) {
    if (!user) return
    const slot = items.find((entry) => entry.id === slotId)
    if (!slot) return

    const duration = slot.endMinutes - slot.startMinutes
    const nextStart = clamp(start + row * ROW_MINUTES, 0, 1440 - duration)
    const targetDate = week.find((date) => dayOfWeek(date) === day)
    const nextDate = slot.date && targetDate ? dateKeyLocal(targetDate) : null
    if (slot.dayOfWeek === day && slot.startMinutes === nextStart && (slot.date ?? null) === nextDate) {
      return
    }

    try {
      await patchDoc(userPath(user.uid, 'classes'), slot.id, {
        dayOfWeek: day,
        startMinutes: nextStart,
        endMinutes: nextStart + duration,
        ...(nextDate ? { date: nextDate } : {}),
      })
      toast({
        title: 'Class moved',
        description: `${DAY_LONG[day - 1]} · ${minutesToLabel(nextStart)}`,
        tone: 'success',
      })
    } catch (cause) {
      console.error(cause)
      toast({ title: 'Could not move that class', tone: 'danger' })
    }
  }

  if (!user) return null

  const weekLabel = `${week[0]?.toLocaleDateString(undefined, { day: 'numeric', month: 'short' })} – ${week[6]?.toLocaleDateString(undefined, { day: 'numeric', month: 'short' })}`
  const periodLabel =
    view === 'month'
      ? anchor.toLocaleDateString(undefined, { month: 'long', year: 'numeric' })
      : weekLabel
  const selectedDate = week[selectedDay] ?? week[0] ?? new Date()
  const selectedSlots = (slotsForDay.get(dayOfWeek(selectedDate)) ?? []).filter((slot) =>
    occursOnDate(slot, selectedDate),
  )

  return (
    <div>
      <PageHeader
        title="Timetable"
        description="Your week repeats on its own. Times are wall-clock in your timetable timezone."
        actions={
          <>
            <Button
              variant="secondary"
              size="sm"
              onClick={() => setDialog({ open: true, slot: null, kind: 'event' })}
            >
              Add event
            </Button>
            <Button size="sm" onClick={() => setDialog({ open: true, slot: null, kind: 'class' })}>
              Add class
            </Button>
          </>
        }
      />

      {classes.error ? (
        <div className="mb-4">
          <ErrorNote error={classes.error} what="your timetable" />
        </div>
      ) : null}

      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <Button
            variant="secondary"
            size="sm"
            aria-label="Previous period"
            onClick={() => shift(-1)}
          >
            <svg viewBox="0 0 24 24" className="size-4" fill="none" stroke="currentColor" strokeWidth="1.8">
              <path d="M15 6l-6 6 6 6" strokeLinecap="round" />
            </svg>
          </Button>
          <Button variant="secondary" size="sm" onClick={() => setAnchor(new Date())}>
            {view === 'month' ? 'This month' : 'This week'}
          </Button>
          <Button
            variant="secondary"
            size="sm"
            aria-label="Next period"
            onClick={() => shift(1)}
          >
            <svg viewBox="0 0 24 24" className="size-4" fill="none" stroke="currentColor" strokeWidth="1.8">
              <path d="M9 6l6 6-6 6" strokeLinecap="round" />
            </svg>
          </Button>
          <span className="text-sm text-ink-muted">{periodLabel}</span>
        </div>

        <Segmented
          label="Timetable view"
          value={view}
          onChange={(next) => {
            setView(next)
            try {
              localStorage.setItem('slot-timetable-view', next)
            } catch {
              // non-fatal
            }
          }}
          options={[
            { value: 'week', label: 'Week' },
            { value: 'day', label: 'Day' },
            { value: 'month', label: 'Month' },
          ]}
        />
      </div>

      {termPaused ? (
        <p className="mb-3 rounded-card border border-warning bg-surface px-4 py-3 text-sm text-ink-muted">
          {term === 'before'
            ? 'This term has not started yet, so weekly classes are hidden.'
            : 'This term has ended, so weekly classes are hidden.'}{' '}
          Adjust the dates or archive the term in{' '}
          <a href="/settings" className="font-medium text-accent-text hover:underline">
            Settings
          </a>
          .
        </p>
      ) : !classes.loading && items.length === 0 ? (
        <p className="mb-3 rounded-card border border-dashed border-border px-4 py-3 text-sm text-ink-muted">
          Nothing scheduled yet. The grid stays here — add a class or an event and it fills in.
        </p>
      ) : null}

      {classes.loading ? (
        <Skeleton className="h-96 w-full" />
      ) : view === 'month' ? (
        <div className="overflow-x-auto rounded-card border border-border bg-surface">
          <div className="min-w-[760px]">
            <div className="grid grid-cols-7 border-b border-border">
              {DAY_LABELS.map((label) => (
                <div
                  key={label}
                  className="px-2 py-2 text-center text-[11px] font-medium uppercase tracking-wide text-ink-muted"
                >
                  {label}
                </div>
              ))}
            </div>
            <div className="grid grid-cols-7">
              {monthDays.map((day) => {
                const inMonth = day.getMonth() === anchor.getMonth()
                const entries = (slotsForDay.get(dayOfWeek(day)) ?? []).filter((slot) =>
                  occursOnDate(slot, day),
                )
                const due = tasksByDay.get(dayKey(day)) ?? []
                const today = isSameDay(day, new Date())

                return (
                  <button
                    key={day.toISOString()}
                    type="button"
                    onClick={() => {
                      setAnchor(day)
                      setSelectedDay(dayOfWeek(day) - 1)
                      setView('day')
                    }}
                    className={cn(
                      'min-h-24 border-b border-r border-border p-1.5 text-left align-top transition-colors hover:bg-surface-elevated',
                      !inMonth && 'bg-canvas',
                    )}
                  >
                    <div className="flex items-center justify-between">
                      <span
                        className={cn(
                          'inline-flex size-5 items-center justify-center rounded-pill text-xs tabular-nums',
                          today
                            ? 'bg-accent-solid text-accent-fg'
                            : inMonth
                              ? 'text-ink'
                              : 'text-ink-muted',
                        )}
                      >
                        {day.getDate()}
                      </span>
                      {due.length > 0 ? (
                        <span aria-hidden className="size-1.5 rounded-pill bg-warning" />
                      ) : null}
                    </div>

                    <div className="mt-1 space-y-0.5">
                      {entries.slice(0, 2).map((slot) => {
                        const subject = slot.subjectId
                          ? subjectById.get(slot.subjectId)
                          : undefined
                        const color = slot.colorOverride ?? subject?.color ?? 'indigo'
                        return (
                          <span
                            key={slot.id}
                            className="block truncate rounded-[4px] border px-1 py-0.5 text-[10px] leading-tight"
                            style={tagTint(color)}
                          >
                            <span className="tabular-nums text-ink-muted">
                              {minutesToLabel(slot.startMinutes)}
                            </span>{' '}
                            <span className="text-ink">
                              {subject?.name ?? slot.title ?? 'Entry'}
                            </span>
                          </span>
                        )
                      })}
                      {entries.length > 2 ? (
                        <span className="block px-1 text-[10px] text-ink-muted">
                          +{entries.length - 2} more
                        </span>
                      ) : null}
                      {due.slice(0, 2).map((task) => (
                        <span
                          key={task.id}
                          className="block truncate px-1 text-[10px] text-warning"
                        >
                          • {task.title}
                        </span>
                      ))}
                    </div>
                  </button>
                )
              })}
            </div>
          </div>
        </div>
      ) : view === 'week' ? (
        <div className="overflow-x-auto rounded-card border border-border bg-surface">
          <div className="min-w-[760px] p-2">
            <div
              className="grid"
              style={{
                gridTemplateColumns: '52px repeat(7, minmax(0, 1fr))',
                gridTemplateRows: `auto repeat(${rows}, 26px)`,
              }}
            >
              <div />
              {week.map((date) => (
                <div key={date.toISOString()} className="border-l border-border px-1 pb-2 text-center">
                  <p className="text-[11px] font-medium uppercase tracking-wide text-ink-muted">
                    {DAY_LABELS[dayOfWeek(date) - 1]}
                  </p>
                  <p
                    className={cn(
                      'text-sm tabular-nums',
                      isSameDay(date, new Date())
                        ? 'font-semibold text-accent-text'
                        : 'text-ink',
                    )}
                  >
                    {date.getDate()}
                  </p>
                </div>
              ))}

              {Array.from({ length: rows }).map((_, row) => (
                <Fragment key={row}>
                  <div
                    className="pr-2 text-right text-[10px] tabular-nums text-ink-muted"
                    style={{ gridColumn: 1, gridRow: row + 2 }}
                  >
                    {row % 2 === 0 ? minutesToLabel(start + row * ROW_MINUTES) : ''}
                  </div>
                  {week.map((date, dayIndex) => (
                    <div
                      key={`${row}-${dayIndex}`}
                      onDragOver={(event) => {
                        if (!dragId) return
                        event.preventDefault()
                        event.dataTransfer.dropEffect = 'move'
                      }}
                      onDrop={(event) => {
                        event.preventDefault()
                        const id = dragId ?? event.dataTransfer.getData('text/plain')
                        setDragId(null)
                        if (id) void moveSlot(id, dayOfWeek(date), row)
                      }}
                      className={cn(
                        'border-l border-t border-border transition-colors duration-fast',
                        dragId && 'bg-accent-subtle/60 hover:bg-accent-subtle',
                      )}
                      style={{ gridColumn: dayIndex + 2, gridRow: row + 2 }}
                    />
                  ))}
                </Fragment>
              ))}

              {items
                .filter(
                  (slot) =>
                    !slot.date || week.some((date) => dateKeyLocal(date) === slot.date),
                )
                .map((slot) => {
                const subject = slot.subjectId ? subjectById.get(slot.subjectId) : undefined
                const color = slot.colorOverride ?? subject?.color ?? 'indigo'
                const label = subject?.name ?? slot.title ?? 'Entry'
                const fromRow = clamp(
                  Math.floor((slot.startMinutes - start) / ROW_MINUTES),
                  0,
                  rows - 1,
                )
                const toRow = clamp(
                  Math.ceil((slot.endMinutes - start) / ROW_MINUTES),
                  fromRow + 1,
                  rows,
                )

                return (
                  <button
                    key={slot.id}
                    type="button"
                    draggable
                    onDragStart={(event) => {
                      setDragId(slot.id)
                      event.dataTransfer.setData('text/plain', slot.id)
                      event.dataTransfer.effectAllowed = 'move'
                    }}
                    onDragEnd={() => setDragId(null)}
                    onDragOver={(event) => {
                      if (!dragId) return
                      event.preventDefault()
                    }}
                    onDrop={(event) => {
                      event.preventDefault()
                      const id = dragId ?? event.dataTransfer.getData('text/plain')
                      setDragId(null)
                      if (id) void moveSlot(id, slot.dayOfWeek, fromRow)
                    }}
                    onClick={() => setDialog({ open: true, slot, kind: slot.kind ?? 'class' })}
                    className={cn(
                      'z-10 m-0.5 cursor-grab overflow-hidden rounded-chip border p-1.5 text-left transition-transform duration-fast hover:-translate-y-px active:cursor-grabbing',
                      dragId === slot.id && 'opacity-50',
                    )}
                    style={{
                      gridColumn: slot.dayOfWeek + 1,
                      gridRow: `${fromRow + 2} / ${toRow + 2}`,
                      ...tagTint(color),
                    }}
                  >
                    <span
                      aria-hidden
                      className="mb-1 block h-0.5 w-6 rounded-pill"
                      style={tagBar(color)}
                    />
                    <span className="block truncate text-[11px] font-medium leading-tight text-ink">
                      {label}
                    </span>
                    <span className="block truncate text-[10px] leading-tight text-ink-muted">
                      {minutesToLabel(slot.startMinutes)}
                      {slot.room ? ` · ${slot.room}` : ''}
                    </span>
                  </button>
                )
              })}
            </div>
            <p className="border-t border-border px-3 py-2 text-xs text-ink-muted">
              Drag a class block to another day or time. Drops snap to 30 minutes.
            </p>
          </div>
        </div>
      ) : (
        <div>
          <div className="mb-3 flex gap-1 overflow-x-auto pb-1">
            {week.map((date, index) => (
              <button
                key={date.toISOString()}
                type="button"
                onClick={() => setSelectedDay(index)}
                aria-pressed={selectedDay === index}
                className={cn(
                  'flex min-w-14 flex-col items-center rounded-chip border px-3 py-2 transition-colors duration-fast',
                  selectedDay === index
                    ? 'border-accent bg-accent-subtle text-accent-text'
                    : 'border-border bg-surface text-ink-muted hover:text-ink',
                )}
              >
                <span className="text-[11px] font-medium uppercase">{DAY_LABELS[index]}</span>
                <span className="text-base tabular-nums">{date.getDate()}</span>
              </button>
            ))}
          </div>

          <Card className="p-4">
            <div className="mb-3 flex items-center justify-between">
              <p className="text-sm font-medium text-ink">{DAY_LONG[selectedDay]}</p>
              <Badge tone="neutral">{selectedSlots.length} classes</Badge>
            </div>

            {selectedSlots.length === 0 ? (
              <p className="py-6 text-center text-sm text-ink-muted">Nothing scheduled.</p>
            ) : (
              <ul className="space-y-2">
                {selectedSlots.map((slot) => {
                  const subject = slot.subjectId ? subjectById.get(slot.subjectId) : undefined
                  const color = slot.colorOverride ?? subject?.color ?? 'indigo'
                  const label = subject?.name ?? slot.title ?? 'Entry'
                  return (
                    <li key={slot.id}>
                      <button
                        type="button"
                        onClick={() => setDialog({ open: true, slot, kind: slot.kind ?? 'class' })}
                        className="flex w-full items-center gap-3 rounded-chip border p-3 text-left"
                        style={tagTint(color)}
                      >
                        <span
                          aria-hidden
                          className="h-8 w-1 shrink-0 rounded-pill"
                          style={tagBar(color)}
                        />
                        <span className="min-w-0 flex-1">
                          <span className="block truncate text-sm font-medium text-ink">
                            {label}
                          </span>
                          <span className="block text-xs text-ink-muted">
                            {minutesToLabel(slot.startMinutes)}–{minutesToLabel(slot.endMinutes)}
                            {slot.room ? ` · ${slot.room}` : ''}
                            {slot.teacher ? ` · ${slot.teacher}` : ''}
                          </span>
                        </span>
                      </button>
                    </li>
                  )
                })}
              </ul>
            )}
          </Card>

          <p className="mt-3 text-xs text-ink-muted">
            Showing {selectedDate.toLocaleDateString(undefined, { weekday: 'long', day: 'numeric', month: 'long' })}
          </p>
        </div>
      )}

      <ClassDialog
        open={dialog.open}
        onClose={() => setDialog({ open: false, slot: null, kind: 'class' })}
        subjects={subjects.items}
        initial={dialog.slot}
        defaultDay={selectedDay + 1}
        defaultKind={dialog.kind}
      />
    </div>
  )
}
