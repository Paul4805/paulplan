'use client'

import { Timestamp } from 'firebase/firestore'
import { useEffect, useState } from 'react'

import { Button, Dialog, Field, Input, Select, Textarea } from '@/components/ui'
import { useAuth } from '@/lib/auth'
import { createDoc, patchDoc, removeDoc, useDoc, userPath } from '@/lib/db'
import { pad2 } from '@/lib/time'
import { useToast } from '@/lib/toast'
import type { Priority, Profile, Subject, Task, TaskStatus } from '@/lib/types'

interface FormState {
  title: string
  notes: string
  subjectId: string
  date: string
  time: string
  priority: Priority
  status: TaskStatus
  remind: boolean
}

function toDateInput(date: Date): string {
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`
}

function toTimeInput(date: Date): string {
  return `${pad2(date.getHours())}:${pad2(date.getMinutes())}`
}

function localDate(date: string, time: string): Date | null {
  if (!date) return null
  const parsed = new Date(`${date}T${time || '23:59'}`)
  return Number.isNaN(parsed.getTime()) ? null : parsed
}

function emptyForm(status: TaskStatus, subjectId = ''): FormState {
  return {
    title: '',
    notes: '',
    subjectId,
    date: '',
    time: '17:00',
    priority: 'medium',
    status,
    remind: false,
  }
}

export function TaskDialog({
  open,
  onClose,
  subjects,
  initial,
  defaultStatus = 'todo',
  defaultSubjectId = '',
}: {
  open: boolean
  onClose: () => void
  subjects: Subject[]
  initial?: Task | null
  defaultStatus?: TaskStatus
  defaultSubjectId?: string
}) {
  const { user } = useAuth()
  const toast = useToast()
  const profile = useDoc<Profile & { id: string }>(
    user ? userPath(user.uid, 'meta/profile') : null,
  )
  const [form, setForm] = useState<FormState>(() => emptyForm(defaultStatus, defaultSubjectId))
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (!open) return
    if (!initial) {
      setForm(emptyForm(defaultStatus, defaultSubjectId))
      return
    }

    const due = initial.dueAt ? initial.dueAt.toDate() : null
    setForm({
      title: initial.title,
      notes: initial.notes ?? '',
      subjectId: initial.subjectId ?? '',
      date: due ? toDateInput(due) : '',
      time: due ? toTimeInput(due) : '17:00',
      priority: initial.priority,
      status: initial.status,
      remind: Boolean(initial.remind || initial.alarmId),
    })
  }, [open, initial, defaultStatus, defaultSubjectId])

  if (!user) return null

  const uid = user.uid
  const path = userPath(uid, 'tasks')

  async function save() {
    if (!form.title.trim()) {
      toast({ title: 'Give the task a title', tone: 'danger' })
      return
    }

    setBusy(true)
    try {
      const due = localDate(form.date, form.time)
      const completedAt =
        form.status === 'done'
          ? initial?.status === 'done'
            ? (initial.completedAt ?? Timestamp.now())
            : Timestamp.now()
          : null

      const payload: Record<string, unknown> = {
        title: form.title.trim(),
        notes: form.notes.trim(),
        subjectId: form.subjectId || null,
        dueAt: due ? Timestamp.fromDate(due) : null,
        priority: form.priority,
        status: form.status,
        completedAt,
        remind: form.remind && due !== null,
      }

      let alarmId = initial?.alarmId ?? null

      if (form.remind && due) {
        const meta = { ...profile.item }
        const alarmPayload = {
          title: form.title.trim(),
          kind: 'task' as const,
          recurrence: 'once' as const,
          onceAt: Timestamp.fromDate(due),
          leadMinutes: meta.notifications?.leadMinutes ?? 10,
          exact: true,
          enabled: true,
          timezone: meta.timezone ?? Intl.DateTimeFormat().resolvedOptions().timeZone ?? 'UTC',
          targetId: initial?.id ?? null,
        }

        if (alarmId) {
          await patchDoc(userPath(uid, 'alarms'), alarmId, alarmPayload)
        } else {
          const created = await createDoc(userPath(uid, 'alarms'), alarmPayload)
          alarmId = created.id
        }
      } else if (alarmId) {
        await removeDoc(userPath(uid, 'alarms'), alarmId).catch(() => undefined)
        alarmId = null
      }

      payload.alarmId = alarmId

      if (initial) {
        await patchDoc(path, initial.id, payload)
      } else {
        const created = await createDoc(path, { ...payload, order: Date.now() })
        if (alarmId) {
          await patchDoc(userPath(uid, 'alarms'), alarmId, { targetId: created.id }).catch(
            () => undefined,
          )
        }
      }

      toast({ title: initial ? 'Task updated' : 'Task added', tone: 'success' })
      onClose()
    } catch (cause) {
      console.error(cause)
      toast({ title: 'Could not save the task', tone: 'danger' })
    } finally {
      setBusy(false)
    }
  }

  async function destroy() {
    if (!initial) return
    if (!window.confirm('Delete this task?')) return
    setBusy(true)
    try {
      if (initial.alarmId) {
        await removeDoc(userPath(uid, 'alarms'), initial.alarmId).catch(() => undefined)
      }
      await removeDoc(path, initial.id)
      toast({ title: 'Task deleted', tone: 'success' })
      onClose()
    } catch (cause) {
      console.error(cause)
      toast({ title: 'Could not delete the task', tone: 'danger' })
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog
      open={open}
      onClose={onClose}
      title={initial ? 'Edit task' : 'New task'}
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
        <Field label="Title" htmlFor="task-title">
          <Input
            id="task-title"
            value={form.title}
            onChange={(event) => setForm({ ...form, title: event.target.value })}
            placeholder="Problem set 3"
            maxLength={200}
          />
        </Field>

        <Field label="Notes" htmlFor="task-notes">
          <Textarea
            id="task-notes"
            value={form.notes}
            onChange={(event) => setForm({ ...form, notes: event.target.value })}
            placeholder="Optional detail"
          />
        </Field>

        <div className="grid grid-cols-2 gap-3">
          <Field label="Subject" htmlFor="task-subject">
            <Select
              id="task-subject"
              value={form.subjectId}
              onChange={(event) => setForm({ ...form, subjectId: event.target.value })}
            >
              <option value="">None</option>
              {subjects.map((subject) => (
                <option key={subject.id} value={subject.id}>
                  {subject.name}
                </option>
              ))}
            </Select>
          </Field>

          <Field label="Priority" htmlFor="task-priority">
            <Select
              id="task-priority"
              value={form.priority}
              onChange={(event) =>
                setForm({ ...form, priority: event.target.value as Priority })
              }
            >
              <option value="low">Low</option>
              <option value="medium">Medium</option>
              <option value="high">High</option>
            </Select>
          </Field>
        </div>

        <div className="grid grid-cols-2 gap-3">
          <Field label="Due date" htmlFor="task-date">
            <Input
              id="task-date"
              type="date"
              value={form.date}
              onChange={(event) =>
                setForm({
                  ...form,
                  date: event.target.value,
                  remind: event.target.value ? form.remind : false,
                })
              }
            />
          </Field>
          <Field label="Due time" htmlFor="task-time">
            <Input
              id="task-time"
              type="time"
              value={form.time}
              onChange={(event) => setForm({ ...form, time: event.target.value })}
              disabled={!form.date}
            />
          </Field>
        </div>

        <label
          className={
            form.date
              ? 'flex cursor-pointer items-start gap-3 rounded-chip border border-border bg-canvas p-3'
              : 'flex items-start gap-3 rounded-chip border border-border bg-canvas p-3 opacity-60'
          }
        >
          <input
            type="checkbox"
            checked={form.remind}
            disabled={!form.date}
            onChange={(event) => setForm({ ...form, remind: event.target.checked })}
            className="mt-0.5 size-4 accent-[var(--color-accent)]"
          />
          <span>
            <span className="block text-sm font-medium text-ink">Remind me</span>
            <span className="block text-xs leading-relaxed text-ink-muted">
              {form.date
                ? 'Fires on your Android phone, even offline. The web shows a banner while open.'
                : 'Set a due date first.'}
            </span>
          </span>
        </label>

        <Field label="Status" htmlFor="task-status">
          <Select
            id="task-status"
            value={form.status}
            onChange={(event) => setForm({ ...form, status: event.target.value as TaskStatus })}
          >
            <option value="todo">To do</option>
            <option value="doing">Doing</option>
            <option value="done">Done</option>
          </Select>
        </Field>
      </div>
    </Dialog>
  )
}
