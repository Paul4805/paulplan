'use client'

import { useEffect, useState } from 'react'

import { Button, Dialog, Field, Input, Select } from '@/components/ui'
import { useAuth } from '@/lib/auth'
import { createDoc, patchDoc, removeDoc, userPath } from '@/lib/db'
import { DAY_LONG, minutesToTimeInput, timeInputToMinutes } from '@/lib/time'
import { useToast } from '@/lib/toast'
import type { ClassSlot, Subject } from '@/lib/types'

interface FormState {
  subjectId: string
  dayOfWeek: string
  start: string
  end: string
  room: string
  teacher: string
}

function emptyForm(subjects: Subject[]): FormState {
  return {
    subjectId: subjects[0]?.id ?? '',
    dayOfWeek: '1',
    start: '09:00',
    end: '10:00',
    room: '',
    teacher: '',
  }
}

export function ClassDialog({
  open,
  onClose,
  subjects,
  initial,
  defaultDay,
}: {
  open: boolean
  onClose: () => void
  subjects: Subject[]
  initial?: ClassSlot | null
  defaultDay?: number
}) {
  const { user } = useAuth()
  const toast = useToast()
  const [form, setForm] = useState<FormState>(() => emptyForm(subjects))
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (!open) return
    setForm(
      initial
        ? {
            subjectId: initial.subjectId,
            dayOfWeek: String(initial.dayOfWeek),
            start: minutesToTimeInput(initial.startMinutes),
            end: minutesToTimeInput(initial.endMinutes),
            room: initial.room ?? '',
            teacher: initial.teacher ?? '',
          }
        : { ...emptyForm(subjects), dayOfWeek: String(defaultDay ?? 1) },
    )
  }, [open, initial, subjects, defaultDay])

  if (!user) return null

  const path = userPath(user.uid, 'classes')

  async function save() {
    if (!form.subjectId) {
      toast({ title: 'Pick a subject first', tone: 'danger' })
      return
    }

    const startMinutes = timeInputToMinutes(form.start)
    const endMinutes = timeInputToMinutes(form.end)

    if (endMinutes <= startMinutes) {
      toast({ title: 'The end time must be after the start', tone: 'danger' })
      return
    }

    setBusy(true)
    try {
      const payload = {
        subjectId: form.subjectId,
        dayOfWeek: Number(form.dayOfWeek),
        startMinutes,
        endMinutes,
        room: form.room.trim(),
        teacher: form.teacher.trim(),
      }

      if (initial) await patchDoc(path, initial.id, payload)
      else await createDoc(path, payload)

      toast({ title: initial ? 'Class updated' : 'Class added', tone: 'success' })
      onClose()
    } catch (cause) {
      console.error(cause)
      toast({ title: 'Could not save the class', tone: 'danger' })
    } finally {
      setBusy(false)
    }
  }

  async function destroy() {
    if (!initial) return
    if (!window.confirm('Remove this class from the timetable?')) return
    setBusy(true)
    try {
      await removeDoc(path, initial.id)
      toast({ title: 'Class removed', tone: 'success' })
      onClose()
    } catch (cause) {
      console.error(cause)
      toast({ title: 'Could not remove the class', tone: 'danger' })
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog
      open={open}
      onClose={onClose}
      title={initial ? 'Edit class' : 'Add class'}
      description="Repeats weekly. Times are wall-clock, in your timetable's timezone."
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
          <Button onClick={save} disabled={busy || subjects.length === 0}>
            {busy ? 'Saving…' : 'Save'}
          </Button>
        </>
      }
    >
      {subjects.length === 0 ? (
        <p className="rounded-chip border border-border bg-canvas p-3 text-sm text-ink-muted">
          Create a subject first — classes belong to a subject.
        </p>
      ) : (
        <div className="space-y-3">
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

          <div className="grid grid-cols-2 gap-3">
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

            <Field label="Room" htmlFor="class-room">
              <Input
                id="class-room"
                value={form.room}
                onChange={(event) => setForm({ ...form, room: event.target.value })}
                placeholder="LT-4"
              />
            </Field>
          </div>

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

          <Field label="Teacher" htmlFor="class-teacher">
            <Input
              id="class-teacher"
              value={form.teacher}
              onChange={(event) => setForm({ ...form, teacher: event.target.value })}
              placeholder="Dr. Rao"
            />
          </Field>
        </div>
      )}
    </Dialog>
  )
}
