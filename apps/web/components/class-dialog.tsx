'use client'

import { useEffect, useState } from 'react'

import { Button, Dialog, Field, Input, Segmented, Select, Textarea } from '@/components/ui'
import { useAuth } from '@/lib/auth'
import { createDoc, patchDoc, removeDoc, userPath } from '@/lib/db'
import {
  DAY_LONG,
  dayOfWeek,
  minutesToTimeInput,
  pad2,
  timeInputToMinutes,
} from '@/lib/time'
import { useToast } from '@/lib/toast'
import { TAG_COLORS, type ClassSlot, type Subject, type TagColor } from '@/lib/types'

type Kind = 'class' | 'event'

interface FormState {
  kind: Kind
  subjectId: string
  title: string
  color: TagColor
  dayOfWeek: string
  date: string
  start: string
  end: string
  room: string
  teacher: string
  notes: string
}

function todayInput(): string {
  const today = new Date()
  return `${today.getFullYear()}-${pad2(today.getMonth() + 1)}-${pad2(today.getDate())}`
}

function emptyForm(subjects: Subject[], kind: Kind, day: number): FormState {
  return {
    kind,
    subjectId: subjects[0]?.id ?? '',
    title: '',
    color: 'indigo',
    dayOfWeek: String(day),
    date: kind === 'event' ? todayInput() : '',
    start: '09:00',
    end: '10:00',
    room: '',
    teacher: '',
    notes: '',
  }
}

export function ClassDialog({
  open,
  onClose,
  subjects,
  initial,
  defaultDay,
  defaultKind = 'class',
}: {
  open: boolean
  onClose: () => void
  subjects: Subject[]
  initial?: ClassSlot | null
  defaultDay?: number
  defaultKind?: Kind
}) {
  const { user } = useAuth()
  const toast = useToast()
  const [form, setForm] = useState<FormState>(() => emptyForm(subjects, defaultKind, 1))
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (!open) return

    if (initial) {
      const kind: Kind = initial.kind ?? (initial.subjectId ? 'class' : 'event')
      const subject = subjects.find((item) => item.id === initial.subjectId)
      setForm({
        kind,
        subjectId: initial.subjectId ?? subjects[0]?.id ?? '',
        title: initial.title ?? '',
        color: initial.colorOverride ?? subject?.color ?? 'indigo',
        dayOfWeek: String(initial.dayOfWeek),
        date: initial.date ?? '',
        start: minutesToTimeInput(initial.startMinutes),
        end: minutesToTimeInput(initial.endMinutes),
        room: initial.room ?? '',
        teacher: initial.teacher ?? '',
        notes: initial.notes ?? '',
      })
      return
    }

    setForm({ ...emptyForm(subjects, defaultKind, defaultDay ?? 1) })
  }, [open, initial, subjects, defaultDay, defaultKind])

  if (!user) return null

  const path = userPath(user.uid, 'classes')
  const isClass = form.kind === 'class'

  async function save() {
    if (isClass && !form.subjectId) {
      toast({ title: 'Pick a subject, or switch to Event', tone: 'danger' })
      return
    }
    if (!isClass && !form.title.trim()) {
      toast({ title: 'Give the event a title', tone: 'danger' })
      return
    }

    const startMinutes = timeInputToMinutes(form.start)
    const endMinutes = timeInputToMinutes(form.end)
    if (endMinutes <= startMinutes) {
      toast({ title: 'The end time must be after the start', tone: 'danger' })
      return
    }

    const eventDate = !isClass && form.date ? new Date(`${form.date}T00:00:00`) : null
    if (eventDate && Number.isNaN(eventDate.getTime())) {
      toast({ title: 'Use a valid date for the event', tone: 'danger' })
      return
    }

    setBusy(true)
    try {
      const payload: Record<string, unknown> = {
        kind: form.kind,
        dayOfWeek: eventDate ? dayOfWeek(eventDate) : Number(form.dayOfWeek),
        date: !isClass ? form.date || null : null,
        startMinutes,
        endMinutes,
        room: form.room.trim(),
        notes: form.notes.trim(),
        subjectId: isClass ? form.subjectId : null,
        title: isClass ? null : form.title.trim(),
        teacher: isClass ? form.teacher.trim() : '',
      }

      // Only events carry their own colour; classes inherit the subject's.
      if (!isClass) payload.colorOverride = form.color

      if (initial) await patchDoc(path, initial.id, payload)
      else await createDoc(path, payload)

      toast({ title: initial ? 'Entry updated' : isClass ? 'Class added' : 'Event added', tone: 'success' })
      onClose()
    } catch (cause) {
      console.error(cause)
      toast({ title: 'Could not save that entry', tone: 'danger' })
    } finally {
      setBusy(false)
    }
  }

  async function destroy() {
    if (!initial) return
    if (!window.confirm('Remove this from your timetable?')) return
    setBusy(true)
    try {
      await removeDoc(path, initial.id)
      toast({ title: 'Removed', tone: 'success' })
      onClose()
    } catch (cause) {
      console.error(cause)
      toast({ title: 'Could not remove that entry', tone: 'danger' })
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog
      open={open}
      onClose={onClose}
      title={initial ? 'Edit entry' : isClass ? 'Add class' : 'Add event'}
      description="Classes link to a subject. Events are everything else: talks, meetings, church, a hangout."
      footer={
        <>
          {initial ? (
            <Button variant="ghost" className="mr-auto text-danger" onClick={destroy} disabled={busy}>
              Delete
            </Button>
          ) : null}
          <Button variant="secondary" onClick={onClose} disabled={busy}>
            Cancel
          </Button>
          <Button onClick={save} disabled={busy}>
            {busy ? 'Saving…' : 'Save'}
          </Button>
        </>
      }
    >
      <div className="space-y-3">
        <Segmented<Kind>
          label="Entry type"
          value={form.kind}
          onChange={(kind) =>
            setForm({
              ...form,
              kind,
              date: kind === 'event' && !form.date ? todayInput() : form.date,
            })
          }
          options={[
            { value: 'class', label: 'Class' },
            { value: 'event', label: 'Event' },
          ]}
        />

        {isClass ? (
          <>
            {subjects.length === 0 ? (
              <p className="rounded-chip border border-border bg-canvas p-3 text-sm text-ink-muted">
                Create a subject first, or switch this entry to Event.
              </p>
            ) : (
              <Field label="Subject" htmlFor="class-subject">
                <Select
                  id="class-subject"
                  value={form.subjectId}
                  onChange={(event) => setForm({ ...form, subjectId: event.target.value })}
                >
                  {subjects.map((subject) => (
                    <option key={subject.id} value={subject.id}>
                      {subject.name}
                    </option>
                  ))}
                </Select>
              </Field>
            )}
          </>
        ) : (
          <div className="grid gap-3 sm:grid-cols-[1fr_120px]">
            <Field label="Title" htmlFor="event-title">
              <Input
                id="event-title"
                value={form.title}
                onChange={(event) => setForm({ ...form, title: event.target.value })}
                placeholder="Talk with the design team"
                maxLength={80}
              />
            </Field>
            <Field label="Colour" htmlFor="event-color">
              <Select
                id="event-color"
                value={form.color}
                onChange={(event) => setForm({ ...form, color: event.target.value as TagColor })}
              >
                {TAG_COLORS.map((color) => (
                  <option key={color} value={color}>
                    {color[0]?.toUpperCase()}
                    {color.slice(1)}
                  </option>
                ))}
              </Select>
            </Field>
          </div>
        )}

        <div className="grid grid-cols-2 gap-3">
          {isClass ? (
            <Field label="Day" htmlFor="class-day">
              <Select
                id="class-day"
                value={form.dayOfWeek}
                onChange={(event) => setForm({ ...form, dayOfWeek: event.target.value })}
              >
                {DAY_LONG.map((label, index) => (
                  <option key={label} value={index + 1}>
                    {label}
                  </option>
                ))}
              </Select>
            </Field>
          ) : (
            <Field
              label="Date"
              htmlFor="event-date"
              hint="Defaults to today. Clear it to repeat weekly instead."
            >
              <Input
                id="event-date"
                type="date"
                value={form.date}
                onChange={(event) => setForm({ ...form, date: event.target.value })}
              />
            </Field>
          )}

          <Field label="Location" htmlFor="class-room">
            <Input
              id="class-room"
              value={form.room}
              onChange={(event) => setForm({ ...form, room: event.target.value })}
              placeholder={isClass ? 'LT-4' : 'Library, Café, Online'}
            />
          </Field>
        </div>

        {!isClass && !form.date ? (
          <Field label="Repeats on" htmlFor="event-day">
            <Select
              id="event-day"
              value={form.dayOfWeek}
              onChange={(event) => setForm({ ...form, dayOfWeek: event.target.value })}
            >
              {DAY_LONG.map((label, index) => (
                <option key={label} value={index + 1}>
                  {label}
                </option>
              ))}
            </Select>
          </Field>
        ) : null}

        <div className="grid grid-cols-2 gap-3">
          <Field label="Start" htmlFor="class-start">
            <Input
              id="class-start"
              type="time"
              required
              value={form.start}
              onChange={(event) => setForm({ ...form, start: event.target.value })}
            />
          </Field>
          <Field label="End" htmlFor="class-end">
            <Input
              id="class-end"
              type="time"
              required
              value={form.end}
              onChange={(event) => setForm({ ...form, end: event.target.value })}
            />
          </Field>
        </div>

        {isClass ? (
          <Field label="Teacher" htmlFor="class-teacher">
            <Input
              id="class-teacher"
              value={form.teacher}
              onChange={(event) => setForm({ ...form, teacher: event.target.value })}
              placeholder="Dr. Rao"
            />
          </Field>
        ) : null}

        <Field label="Notes" htmlFor="class-notes">
          <Textarea
            id="class-notes"
            value={form.notes}
            onChange={(event) => setForm({ ...form, notes: event.target.value })}
            placeholder={isClass ? 'Optional' : 'Who you are meeting, what to bring…'}
            rows={2}
          />
        </Field>
      </div>
    </Dialog>
  )
}
