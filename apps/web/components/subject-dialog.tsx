'use client'

import { useEffect, useState } from 'react'

import { Button, Dialog, Field, Input, Select } from '@/components/ui'
import { useAuth } from '@/lib/auth'
import { createDoc, patchDoc, removeDoc, userPath } from '@/lib/db'
import { useToast } from '@/lib/toast'
import { TAG_COLORS, type Subject, type TagColor } from '@/lib/types'

interface FormState {
  name: string
  code: string
  color: TagColor
  teacher: string
  room: string
  target: string
}

function emptyForm(): FormState {
  return { name: '', code: '', color: 'indigo', teacher: '', room: '', target: '' }
}

export function SubjectDialog({
  open,
  onClose,
  initial,
}: {
  open: boolean
  onClose: () => void
  initial?: Subject | null
}) {
  const { user } = useAuth()
  const toast = useToast()
  const [form, setForm] = useState<FormState>(emptyForm)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (!open) return
    setForm(
      initial
        ? {
            name: initial.name,
            code: initial.code ?? '',
            color: initial.color,
            teacher: initial.teacher ?? '',
            room: initial.room ?? '',
            target: initial.targetHoursPerWeek ? String(initial.targetHoursPerWeek) : '',
          }
        : emptyForm(),
    )
  }, [open, initial])

  if (!user) return null

  const path = userPath(user.uid, 'subjects')

  async function save() {
    if (!form.name.trim()) {
      toast({ title: 'Give the subject a name', tone: 'danger' })
      return
    }

    const target = Number(form.target)
    setBusy(true)
    try {
      const payload = {
        name: form.name.trim(),
        code: form.code.trim(),
        color: form.color,
        teacher: form.teacher.trim(),
        room: form.room.trim(),
        targetHoursPerWeek: Number.isFinite(target) && target > 0 ? target : null,
        archived: initial?.archived ?? false,
      }

      if (initial) await patchDoc(path, initial.id, payload)
      else await createDoc(path, payload)

      toast({ title: initial ? 'Subject updated' : 'Subject added', tone: 'success' })
      onClose()
    } catch (cause) {
      console.error(cause)
      toast({ title: 'Could not save the subject', tone: 'danger' })
    } finally {
      setBusy(false)
    }
  }

  async function destroy() {
    if (!initial) return
    if (!window.confirm('Delete this subject? Its classes will be left pointing at a missing subject.')) {
      return
    }
    setBusy(true)
    try {
      await removeDoc(path, initial.id)
      toast({ title: 'Subject deleted', tone: 'success' })
      onClose()
    } catch (cause) {
      console.error(cause)
      toast({ title: 'Could not delete the subject', tone: 'danger' })
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog
      open={open}
      onClose={onClose}
      title={initial ? 'Edit subject' : 'New subject'}
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
        <Field label="Name" htmlFor="subject-name">
          <Input
            id="subject-name"
            value={form.name}
            onChange={(event) => setForm({ ...form, name: event.target.value })}
            placeholder="Algorithms"
            maxLength={80}
          />
        </Field>

        <div className="grid grid-cols-2 gap-3">
          <Field label="Code" htmlFor="subject-code">
            <Input
              id="subject-code"
              value={form.code}
              onChange={(event) => setForm({ ...form, code: event.target.value })}
              placeholder="CS301"
            />
          </Field>
          <Field label="Colour" htmlFor="subject-color">
            <Select
              id="subject-color"
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

        <div className="grid grid-cols-2 gap-3">
          <Field label="Teacher" htmlFor="subject-teacher">
            <Input
              id="subject-teacher"
              value={form.teacher}
              onChange={(event) => setForm({ ...form, teacher: event.target.value })}
              placeholder="Dr. Rao"
            />
          </Field>
          <Field label="Room" htmlFor="subject-room">
            <Input
              id="subject-room"
              value={form.room}
              onChange={(event) => setForm({ ...form, room: event.target.value })}
              placeholder="LT-4"
            />
          </Field>
        </div>

        <Field
          label="Weekly study target"
          htmlFor="subject-target"
          hint="Hours per week. Used for the progress bar; optional."
        >
          <Input
            id="subject-target"
            type="number"
            min="0"
            step="0.5"
            inputMode="decimal"
            value={form.target}
            onChange={(event) => setForm({ ...form, target: event.target.value })}
            placeholder="4"
          />
        </Field>
      </div>
    </Dialog>
  )
}
