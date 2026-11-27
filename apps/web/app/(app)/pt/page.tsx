'use client'

import { Timestamp } from 'firebase/firestore'
import { useEffect, useMemo, useRef, useState } from 'react'

import { SessionDetail } from '@/components/session-detail'
import { ActionMenu } from '@/components/action-menu'
import {
  Button,
  Card,
  Dialog,
  EmptyState,
  ErrorNote,
  Field,
  Input,
  PageHeader,
  Select,
  Skeleton,
  Textarea,
  cn,
} from '@/components/ui'
import { useAuth } from '@/lib/auth'
import { createDoc, removeDoc, useCollection, userPath } from '@/lib/db'
import { parseGarminJson } from '@/lib/garmin'
import type { ImportedSession } from '@/lib/importers'
import { parseStravaCsv } from '@/lib/strava'
import { pad2, startOfWeek, toDate } from '@/lib/time'
import { useToast } from '@/lib/toast'
import { PT_TYPES, type PtSession, type PtType } from '@/lib/types'

const TYPE_LABEL: Record<PtType, string> = {
  run: 'Run',
  football: 'Football',
  gym: 'Gym',
  other: 'Other',
}

const TYPE_COLOR: Record<PtType, string> = {
  run: 'indigo',
  football: 'emerald',
  gym: 'amber',
  other: 'violet',
}

function dateInput(date: Date): string {
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`
}

function timeInput(date: Date): string {
  return `${pad2(date.getHours())}:${pad2(date.getMinutes())}`
}

function durationLabel(minutes: number): string {
  const hours = Math.floor(minutes / 60)
  const rest = Math.round(minutes % 60)
  return hours > 0 ? `${hours}h ${pad2(rest)}m` : `${rest}m`
}

function chunk<T>(items: T[], size: number): T[][] {
  const groups: T[][] = []
  for (let index = 0; index < items.length; index += size) groups.push(items.slice(index, index + size))
  return groups
}

export default function TrainingPage() {
  const { user } = useAuth()
  const toast = useToast()
  const sessions = useCollection<PtSession>(user ? userPath(user.uid, 'ptSessions') : null)

  const [logOpen, setLogOpen] = useState(false)
  const [importOpen, setImportOpen] = useState(false)
  const [detail, setDetail] = useState<PtSession | null>(null)
  const [visible, setVisible] = useState(12)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const [form, setForm] = useState({
    type: 'run' as PtType,
    title: '',
    date: '',
    time: '',
    minutes: '45',
    km: '',
    notes: '',
  })

  const [source, setSource] = useState<'garmin-json' | 'strava-csv'>('garmin-json')
  const [unit, setUnit] = useState<'km' | 'mi'>('km')
  const [fileName, setFileName] = useState<string | null>(null)
  const [imported, setImported] = useState<ImportedSession[]>([])
  const [importSkipped, setImportSkipped] = useState(0)
  const fileRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (!logOpen) return
    const now = new Date()
    setForm({
      type: 'run',
      title: '',
      date: dateInput(now),
      time: timeInput(now),
      minutes: '45',
      km: '',
      notes: '',
    })
    setError(null)
  }, [logOpen])

  const sorted = useMemo(
    () =>
      [...sessions.items].sort(
        (a, b) => (b.startedAt?.toMillis() ?? 0) - (a.startedAt?.toMillis() ?? 0),
      ),
    [sessions.items],
  )

  const weekStart = useMemo(() => startOfWeek(new Date()), [])
  const thisWeek = sorted.filter((session) => {
    const started = toDate(session.startedAt)
    return started !== null && started >= weekStart
  })

  const weekMinutes = thisWeek.reduce((total, session) => total + session.durationMinutes, 0)
  const weekKm = thisWeek.reduce((total, session) => total + (session.distanceKm ?? 0), 0)

  if (!user) return null

  const path = userPath(user.uid, 'ptSessions')

  async function save() {
    const started = new Date(`${form.date}T${form.time || '18:00'}`)
    const minutes = Number(form.minutes)

    if (Number.isNaN(started.getTime())) {
      setError('Pick a valid date and time.')
      return
    }
    if (!Number.isFinite(minutes) || minutes <= 0) {
      setError('How long was the session?')
      return
    }

    const km = form.type === 'run' ? Number(form.km) : NaN
    setBusy(true)
    setError(null)

    try {
      await createDoc(path, {
        type: form.type,
        title: form.title.trim() || TYPE_LABEL[form.type],
        startedAt: Timestamp.fromDate(started),
        durationMinutes: Math.min(Math.round(minutes), 1440),
        distanceKm: Number.isFinite(km) && km > 0 ? Math.round(km * 100) / 100 : null,
        source: 'manual',
        notes: form.notes.trim(),
      })
      toast({ title: 'Session logged', tone: 'success' })
      setLogOpen(false)
    } catch (cause) {
      console.error(cause)
      toast({ title: 'Could not log the session', tone: 'danger' })
    } finally {
      setBusy(false)
    }
  }

  async function remove(session: PtSession) {
    if (!window.confirm(`Delete "${session.title ?? TYPE_LABEL[session.type]}"?`)) return
    try {
      await removeDoc(path, session.id)
      toast({ title: 'Session deleted', tone: 'success' })
    } catch (cause) {
      console.error(cause)
      toast({ title: 'Could not delete the session', tone: 'danger' })
    }
  }

  async function onFile(event: React.ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    if (!file) return

    const text = await file.text()
    const result =
      source === 'garmin-json' ? parseGarminJson(text) : parseStravaCsv(text, unit)
    setFileName(file.name)
    setImported(result.sessions)
    setImportSkipped(result.skipped)
  }

  async function runImport() {
    if (imported.length === 0) return

    const existing = new Set(
      sessions.items
        .map((session) => session.externalId)
        .filter((id): id is string => Boolean(id)),
    )
    const fresh = imported.filter((run) => !existing.has(run.externalId))
    const duplicates = imported.length - fresh.length

    if (fresh.length === 0) {
      toast({ title: 'Nothing new to import', description: `${duplicates} already in PaulPlan.` })
      return
    }

    setBusy(true)
    try {
      for (const group of chunk(fresh, 10)) {
        await Promise.all(
          group.map((run) =>
            createDoc(path, {
              type: run.type,
              title: run.title,
              startedAt: Timestamp.fromDate(run.startedAt),
              durationMinutes: run.durationMinutes,
              distanceKm: run.distanceKm,
              source,
              externalId: run.externalId,
              notes: '',
            }),
          ),
        )
      }

      toast({
        title: `Imported ${fresh.length} sessions`,
        description: duplicates > 0 ? `${duplicates} duplicates skipped.` : undefined,
        tone: 'success',
      })
      setImportOpen(false)
      setImported([])
      setFileName(null)
    } catch (cause) {
      console.error(cause)
      toast({ title: 'Import failed part-way', description: 'Re-importing is safe; duplicates are skipped.', tone: 'danger' })
    } finally {
      setBusy(false)
    }
  }

  return (
    <div>
      <PageHeader
        title="Training"
        description="Runs, football, gym — the physical side, tracked separately from study."
        actions={
          <>
            <ActionMenu
              label="More"
              variant="secondary"
              size="sm"
              ariaLabel="More training actions"
              items={[
                {
                  label: 'Import',
                  description: 'Garmin JSON or Strava CSV',
                  onSelect: () => setImportOpen(true),
                },
              ]}
            />
            <Button size="sm" onClick={() => setLogOpen(true)}>
              Log session
            </Button>
          </>
        }
      />

      {sessions.error ? (
        <div className="mb-4">
          <ErrorNote error={sessions.error} what="your training" />
        </div>
      ) : null}

      <section className="grid grid-cols-2 gap-3">
        <Card className="p-4">
          <p className="text-xs font-medium uppercase tracking-wide text-ink-muted">This week</p>
          <p className="mt-2 text-xxl font-semibold leading-none tabular-nums tracking-tight text-ink">
            {Math.round((weekMinutes / 60) * 10) / 10}
            <span className="ml-1 text-base font-medium text-ink-muted">h</span>
          </p>
          <p className="mt-1 text-sm text-ink-muted">
            {thisWeek.length} sessions · {Math.round(weekKm * 10) / 10} km
          </p>
        </Card>

        <Card className="p-4">
          <p className="text-xs font-medium uppercase tracking-wide text-ink-muted">All time</p>
          <p className="mt-2 text-xxl font-semibold leading-none tabular-nums tracking-tight text-ink">
            {Math.round(
              (sessions.items.reduce((total, session) => total + session.durationMinutes, 0) / 60) *
                10,
            ) / 10}
            <span className="ml-1 text-base font-medium text-ink-muted">h</span>
          </p>
          <p className="mt-1 text-sm text-ink-muted">{sessions.items.length} sessions</p>
        </Card>
      </section>

      <section className="mt-8" aria-labelledby="sessions">
        <div className="mb-3 flex items-center justify-between">
          <h2 id="sessions" className="text-base font-medium text-ink">
            Sessions
          </h2>
          <span className="text-sm text-ink-muted">{sorted.length} total</span>
        </div>

        {sessions.loading ? (
          <div className="space-y-2">
            <Skeleton className="h-14 w-full" />
            <Skeleton className="h-14 w-full" />
          </div>
        ) : sorted.length === 0 ? (
          <EmptyState
            title="No training yet"
            description="Log a run, a football match or a gym session. Or import your Garmin export."
            action={
              <div className="flex items-center gap-2">
                <Button size="sm" variant="secondary" onClick={() => setImportOpen(true)}>
                  Import
                </Button>
                <Button size="sm" onClick={() => setLogOpen(true)}>
                  Log session
                </Button>
              </div>
            }
          />
        ) : (
          <ul className="space-y-2">
            {sorted.slice(0, visible).map((session) => {
              const started = toDate(session.startedAt)
              return (
                <li
                  key={session.id}
                  className="flex items-center gap-2 rounded-card border border-border bg-surface p-2"
                >
                  <button
                    type="button"
                    onClick={() => setDetail(session)}
                    className="flex min-w-0 flex-1 items-center gap-3 rounded-chip p-1 text-left transition-colors hover:bg-surface-elevated"
                  >
                    <span
                      aria-hidden
                      className="h-8 w-1 shrink-0 rounded-pill"
                      style={{ backgroundColor: `var(--tag-${TYPE_COLOR[session.type]})` }}
                    />
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium text-ink">
                        {session.title ?? TYPE_LABEL[session.type]}
                      </p>
                      <p className="text-xs text-ink-muted">
                        {started ? started.toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short' }) : '—'}
                        {started ? ` · ${timeInput(started)}` : ''}
                        {session.source === 'strava-csv'
                          ? ' · Strava'
                          : session.source === 'garmin-json' || session.source === 'garmin-api'
                            ? ' · Garmin'
                            : ''}
                      </p>
                    </div>
                    <div className="text-right">
                      <p className="text-sm tabular-nums text-ink">{durationLabel(session.durationMinutes)}</p>
                      {session.distanceKm ? (
                        <p className="text-xs tabular-nums text-ink-muted">
                          {Math.round(session.distanceKm * 10) / 10} km
                        </p>
                      ) : null}
                    </div>
                  </button>
                </li>
              )
            })}
          </ul>
        )}

        {!sessions.loading && sorted.length > visible ? (
          <div className="mt-4 flex justify-center">
            <Button variant="secondary" size="sm" onClick={() => setVisible((count) => count + 20)}>
              Show {Math.min(20, sorted.length - visible)} more
            </Button>
          </div>
        ) : null}
      </section>

      <Dialog
        open={logOpen}
        onClose={() => setLogOpen(false)}
        title="Log a session"
        description="Football and gym live here too — no watch required."
        footer={
          <>
            <Button variant="secondary" onClick={() => setLogOpen(false)} disabled={busy}>
              Cancel
            </Button>
            <Button onClick={save} disabled={busy}>
              {busy ? 'Saving…' : 'Save'}
            </Button>
          </>
        }
      >
        <div className="space-y-3">
          <div role="group" aria-label="Session type" className="flex flex-wrap gap-2">
            {PT_TYPES.map((type) => (
              <button
                key={type}
                type="button"
                onClick={() => setForm({ ...form, type })}
                aria-pressed={form.type === type}
                className={cn(
                  'rounded-pill border px-3 py-1.5 text-sm font-medium transition-colors',
                  form.type === type
                    ? 'border-accent bg-accent-subtle text-accent-text'
                    : 'border-border bg-surface text-ink-muted hover:text-ink',
                )}
              >
                {TYPE_LABEL[type]}
              </button>
            ))}
          </div>

          <Field label="Title" htmlFor="pt-title" hint="Optional. Defaults to the session type.">
            <Input
              id="pt-title"
              value={form.title}
              onChange={(event) => setForm({ ...form, title: event.target.value })}
              placeholder={form.type === 'run' ? 'Easy 5k' : 'Five-a-side'}
            />
          </Field>

          <div className="grid grid-cols-2 gap-3">
            <Field label="Date" htmlFor="pt-date">
              <Input
                id="pt-date"
                type="date"
                value={form.date}
                onChange={(event) => setForm({ ...form, date: event.target.value })}
              />
            </Field>
            <Field label="Start" htmlFor="pt-time">
              <Input
                id="pt-time"
                type="time"
                value={form.time}
                onChange={(event) => setForm({ ...form, time: event.target.value })}
              />
            </Field>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <Field label="Duration (minutes)" htmlFor="pt-minutes">
              <Input
                id="pt-minutes"
                type="number"
                min="1"
                max="1440"
                inputMode="numeric"
                value={form.minutes}
                onChange={(event) => setForm({ ...form, minutes: event.target.value })}
              />
            </Field>
            {form.type === 'run' ? (
              <Field label="Distance (km)" htmlFor="pt-km">
                <Input
                  id="pt-km"
                  type="number"
                  min="0"
                  step="0.1"
                  inputMode="decimal"
                  value={form.km}
                  onChange={(event) => setForm({ ...form, km: event.target.value })}
                  placeholder="5.0"
                />
              </Field>
            ) : null}
          </div>

          <Field label="Notes" htmlFor="pt-notes">
            <Textarea
              id="pt-notes"
              rows={2}
              value={form.notes}
              onChange={(event) => setForm({ ...form, notes: event.target.value })}
              placeholder="How it felt, who with…"
            />
          </Field>

          {error ? (
            <p role="alert" className="text-sm text-danger">
              {error}
            </p>
          ) : null}
        </div>
      </Dialog>

      <Dialog
        open={importOpen}
        onClose={() => setImportOpen(false)}
        title="Import training"
        description="Garmin: Account → Data Management → Export Your Data, then pick summarizedActivities.json from the archive."
        footer={
          <>
            <Button variant="secondary" onClick={() => setImportOpen(false)} disabled={busy}>
              Cancel
            </Button>
            <Button onClick={runImport} disabled={busy || imported.length === 0}>
              {busy ? 'Importing…' : `Import ${imported.length || ''} sessions`}
            </Button>
          </>
        }
      >
        <div className="space-y-3">
          <Field label="Source" htmlFor="import-source">
            <Select
              id="import-source"
              value={source}
              onChange={(event) => {
                setSource(event.target.value as 'garmin-json' | 'strava-csv')
                if (fileRef.current) fileRef.current.value = ''
                setImported([])
                setFileName(null)
              }}
            >
              <option value="garmin-json">Garmin export (summarizedActivities.json)</option>
              <option value="strava-csv">Strava export (activities.csv)</option>
            </Select>
          </Field>

          {source === 'strava-csv' ? (
            <Field label="Distance unit in your export" htmlFor="import-unit">
              <Select
                id="import-unit"
                value={unit}
                onChange={(event) => {
                  setUnit(event.target.value as 'km' | 'mi')
                  if (fileRef.current) fileRef.current.value = ''
                  setImported([])
                  setFileName(null)
                }}
              >
                <option value="km">Kilometres</option>
                <option value="mi">Miles</option>
              </Select>
            </Field>
          ) : null}

          <Field
            label={source === 'garmin-json' ? 'summarizedActivities.json' : 'activities.csv'}
            htmlFor="import-file"
          >
            <input
              id="import-file"
              ref={fileRef}
              type="file"
              accept={source === 'garmin-json' ? '.json,application/json' : '.csv,text/csv'}
              onChange={onFile}
              className="w-full rounded-chip border border-border bg-surface px-3 py-2 text-sm text-ink file:mr-3 file:rounded-chip file:border-0 file:bg-accent-subtle file:px-3 file:py-1.5 file:text-sm file:font-medium file:text-accent-text"
            />
          </Field>

          {fileName ? (
            <div className="rounded-chip border border-border bg-canvas p-3 text-sm">
              <p className="font-medium text-ink">{fileName}</p>
              <p className="mt-1 text-ink-muted">
                {imported.length} sessions ready, {importSkipped} rows skipped.
              </p>
              <p className="mt-1 text-xs text-ink-muted">
                Duplicates already in PaulPlan are skipped on import.
              </p>
            </div>
          ) : null}

          <p className="text-xs leading-relaxed text-ink-muted">
            Garmin&apos;s official API needs partner approval, and Strava&apos;s API is now
            subscription-gated. The data export is the practical path: no server, no secret, and
            re-running it is safe.
          </p>
        </div>
      </Dialog>

      <SessionDetail
        session={detail}
        onClose={() => setDetail(null)}
        onDelete={
          detail
            ? () => {
                const target = detail
                setDetail(null)
                void remove(target)
              }
            : undefined
        }
      />
    </div>
  )
}
