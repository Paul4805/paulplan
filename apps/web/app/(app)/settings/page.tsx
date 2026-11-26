'use client'

import { useRouter } from 'next/navigation'
import { useEffect, useMemo, useState } from 'react'
import { updateProfile } from 'firebase/auth'

import {
  Badge,
  Button,
  Card,
  Field,
  Input,
  PageHeader,
  Segmented,
  Select,
  Skeleton,
} from '@/components/ui'
import { useAuth } from '@/lib/auth'
import { createDoc, fetchAll, patchDoc, removeDoc, useCollection, useDoc, userPath, writeDoc } from '@/lib/db'
import { auth } from '@/lib/firebase'
import { useTheme } from '@/lib/theme'
import { DAY_LONG, minutesToTimeInput, timeInputToMinutes } from '@/lib/time'
import { useToast } from '@/lib/toast'
import {
  DEFAULT_PROFILE,
  type Alarm,
  type Profile,
  type ThemeMode,
} from '@/lib/types'

export default function SettingsPage() {
  const { user, signOutNow } = useAuth()
  const router = useRouter()
  const toast = useToast()
  const { mode, setMode } = useTheme()

  const profilePath = user ? userPath(user.uid, 'meta/profile') : null
  const profile = useDoc<Profile & { id: string }>(profilePath)
  const alarms = useCollection<Alarm>(user ? userPath(user.uid, 'alarms') : null)

  const [name, setName] = useState('')
  const [busy, setBusy] = useState<string | null>(null)
  const [alarmTitle, setAlarmTitle] = useState('')
  const [alarmRecurrence, setAlarmRecurrence] = useState<'daily' | 'weekly'>('daily')
  const [alarmDay, setAlarmDay] = useState('1')
  const [alarmTime, setAlarmTime] = useState('07:00')
  const [alarmLead, setAlarmLead] = useState('0')

  const detectedZone = useMemo(
    () => Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC',
    [],
  )

  useEffect(() => {
    setName(user?.displayName ?? '')
  }, [user])

  if (!user) return null

  const meta = { ...DEFAULT_PROFILE, ...(profile.item ?? {}) }
  const leadMinutes = meta.notifications?.leadMinutes ?? 10

  async function saveName() {
    if (!name.trim()) return
    setBusy('name')
    try {
      await patchDoc('users', user!.uid, { displayName: name.trim() })
      if (auth().currentUser) await updateProfile(auth().currentUser!, { displayName: name.trim() })
      toast({ title: 'Name updated', tone: 'success' })
    } catch (cause) {
      console.error(cause)
      toast({ title: 'Could not update your name', tone: 'danger' })
    } finally {
      setBusy(null)
    }
  }

  async function updateProfileDoc(values: Record<string, unknown>, message: string) {
    if (!profilePath) return
    setBusy(message)
    try {
      await writeDoc(profilePath, values)
      toast({ title: message, tone: 'success' })
    } catch (cause) {
      console.error(cause)
      toast({ title: 'Could not save that setting', tone: 'danger' })
    } finally {
      setBusy(null)
    }
  }

  async function addAlarm() {
    if (!user) return
    if (!alarmTitle.trim()) {
      toast({ title: 'Give the alarm a title', tone: 'danger' })
      return
    }

    setBusy('alarm')
    try {
      const payload: Record<string, unknown> = {
        title: alarmTitle.trim(),
        kind: 'custom',
        recurrence: alarmRecurrence,
        timeMinutes: timeInputToMinutes(alarmTime),
        leadMinutes: Number(alarmLead),
        exact: true,
        enabled: true,
        timezone: meta.timezone ?? detectedZone,
      }
      if (alarmRecurrence === 'weekly') payload.dayOfWeek = Number(alarmDay)

      await createDoc(userPath(user.uid, 'alarms'), payload)
      setAlarmTitle('')
      toast({ title: 'Alarm added', description: 'Your phone will schedule it next time it syncs.', tone: 'success' })
    } catch (cause) {
      console.error(cause)
      toast({ title: 'Could not add the alarm', tone: 'danger' })
    } finally {
      setBusy(null)
    }
  }

  async function toggleAlarm(alarm: Alarm) {
    if (!user) return
    try {
      await patchDoc(userPath(user.uid, 'alarms'), alarm.id, { enabled: !alarm.enabled })
    } catch (cause) {
      console.error(cause)
      toast({ title: 'Could not update the alarm', tone: 'danger' })
    }
  }

  async function deleteAlarm(alarm: Alarm) {
    if (!user) return
    if (!window.confirm(`Delete "${alarm.title}"?`)) return
    try {
      await removeDoc(userPath(user.uid, 'alarms'), alarm.id)
      toast({ title: 'Alarm deleted', tone: 'success' })
    } catch (cause) {
      console.error(cause)
      toast({ title: 'Could not delete the alarm', tone: 'danger' })
    }
  }

  async function exportData() {
    if (!user) return
    setBusy('export')
    try {
      const base = userPath(user.uid)
      const [subjects, classes, tasks, studySessions, alarmDocs, metaDocs] = await Promise.all([
        fetchAll<Record<string, unknown>>(`${base}/subjects`),
        fetchAll<Record<string, unknown>>(`${base}/classes`),
        fetchAll<Record<string, unknown>>(`${base}/tasks`),
        fetchAll<Record<string, unknown>>(`${base}/studySessions`),
        fetchAll<Record<string, unknown>>(`${base}/alarms`),
        fetchAll<Record<string, unknown>>(`${base}/meta`),
      ])

      const payload = {
        exportedAt: new Date().toISOString(),
        user: { id: user.uid, email: user.email, displayName: user.displayName },
        subjects,
        classes,
        tasks,
        studySessions,
        alarms: alarmDocs,
        meta: metaDocs,
      }

      const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' })
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `slot-export-${new Date().toISOString().slice(0, 10)}.json`
      link.click()
      URL.revokeObjectURL(url)
      toast({ title: 'Export downloaded', tone: 'success' })
    } catch (cause) {
      console.error(cause)
      toast({ title: 'Could not export your data', tone: 'danger' })
    } finally {
      setBusy(null)
    }
  }

  const loading = profile.loading || alarms.loading

  return (
    <div className="space-y-10">
      <PageHeader title="Settings" description="Account, appearance, reminders, and your data." />

      <section aria-labelledby="account">
        <h2 id="account" className="mb-3 text-base font-medium text-ink">
          Account
        </h2>
        <Card className="divide-y divide-border">
          <div className="flex flex-col gap-3 p-4 sm:flex-row sm:items-end">
            <Field label="Display name" htmlFor="display-name" className="flex-1">
              <Input
                id="display-name"
                value={name}
                onChange={(event) => setName(event.target.value)}
                placeholder="Your name"
              />
            </Field>
            <Button onClick={saveName} disabled={busy === 'name' || name.trim() === (user.displayName ?? '')}>
              Save
            </Button>
          </div>
          <div className="flex flex-wrap items-center justify-between gap-2 p-4">
            <div>
              <p className="text-sm text-ink">Signed in as</p>
              <p className="text-sm text-ink-muted">{user.email}</p>
            </div>
            <Button
              variant="secondary"
              onClick={async () => {
                await signOutNow()
                router.replace('/login')
              }}
            >
              Sign out
            </Button>
          </div>
        </Card>
      </section>

      <section aria-labelledby="appearance">
        <h2 id="appearance" className="mb-3 text-base font-medium text-ink">
          Appearance
        </h2>
        <Card className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <p className="text-sm text-ink">Theme</p>
            <p className="text-sm text-ink-muted">
              Follows your device unless you choose otherwise.
            </p>
          </div>
          <Segmented<ThemeMode>
            label="Theme"
            value={mode}
            onChange={(next) => {
              setMode(next)
              void updateProfileDoc({ theme: next }, 'Theme saved')
            }}
            options={[
              { value: 'system', label: 'System' },
              { value: 'light', label: 'Light' },
              { value: 'dark', label: 'Dark' },
            ]}
          />
        </Card>
      </section>

      <section aria-labelledby="alarms">
        <h2 id="alarms" className="mb-3 text-base font-medium text-ink">
          Alarms
        </h2>
        <Card className="p-4">
          <p className="rounded-chip border border-border bg-canvas p-3 text-sm leading-relaxed text-ink-secondary">
            Alarms run on your Android phone, even offline or after a reboot. This browser has no
            background delivery — it only shows a banner while a tab is open.
          </p>

          <div className="mt-4 grid gap-3 sm:grid-cols-2">
            <Field label="Title" htmlFor="alarm-title">
              <Input
                id="alarm-title"
                value={alarmTitle}
                onChange={(event) => setAlarmTitle(event.target.value)}
                placeholder="Wake up"
              />
            </Field>

            <Field label="Repeat" htmlFor="alarm-repeat">
              <Select
                id="alarm-repeat"
                value={alarmRecurrence}
                onChange={(event) =>
                  setAlarmRecurrence(event.target.value as 'daily' | 'weekly')
                }
              >
                <option value="daily">Every day</option>
                <option value="weekly">Every week</option>
              </Select>
            </Field>

            {alarmRecurrence === 'weekly' ? (
              <Field label="Day" htmlFor="alarm-day">
                <Select
                  id="alarm-day"
                  value={alarmDay}
                  onChange={(event) => setAlarmDay(event.target.value)}
                >
                  {DAY_LONG.map((label, index) => (
                    <option key={label} value={index + 1}>
                      {label}
                    </option>
                  ))}
                </Select>
              </Field>
            ) : null}

            <Field label="Time" htmlFor="alarm-time">
              <Input
                id="alarm-time"
                type="time"
                value={alarmTime}
                onChange={(event) => setAlarmTime(event.target.value)}
              />
            </Field>

            <Field label="Heads-up" htmlFor="alarm-lead">
              <Select
                id="alarm-lead"
                value={alarmLead}
                onChange={(event) => setAlarmLead(event.target.value)}
              >
                <option value="0">At the time</option>
                <option value="5">5 minutes before</option>
                <option value="10">10 minutes before</option>
                <option value="15">15 minutes before</option>
                <option value="30">30 minutes before</option>
              </Select>
            </Field>
          </div>

          <div className="mt-3">
            <Button onClick={addAlarm} disabled={busy === 'alarm'}>
              Add alarm
            </Button>
          </div>

          <div className="mt-6 border-t border-border pt-4">
            {loading ? (
              <Skeleton className="h-12 w-full" />
            ) : alarms.items.length === 0 ? (
              <p className="text-sm text-ink-muted">No alarms yet.</p>
            ) : (
              <ul className="divide-y divide-border">
                {alarms.items.map((alarm) => (
                  <li key={alarm.id} className="flex items-center gap-3 py-3">
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium text-ink">{alarm.title}</p>
                      <p className="text-xs text-ink-muted">
                        {alarm.recurrence === 'weekly' ? `Weekly · ${DAY_LONG[(alarm.dayOfWeek ?? 1) - 1]}` : 'Daily'}
                        {alarm.timeMinutes != null ? ` · ${minutesToTimeInput(alarm.timeMinutes)}` : ''}
                        {alarm.leadMinutes > 0 ? ` · ${alarm.leadMinutes} min early` : ''}
                      </p>
                    </div>
                    {alarm.enabled ? (
                      <Badge tone="accent">On</Badge>
                    ) : (
                      <Badge tone="neutral">Off</Badge>
                    )}
                    <Button variant="ghost" size="sm" onClick={() => toggleAlarm(alarm)}>
                      {alarm.enabled ? 'Disable' : 'Enable'}
                    </Button>
                    <Button variant="ghost" size="sm" className="text-danger" onClick={() => deleteAlarm(alarm)}>
                      Delete
                    </Button>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </Card>
      </section>

      <section aria-labelledby="reminders">
        <h2 id="reminders" className="mb-3 text-base font-medium text-ink">
          Reminder defaults
        </h2>
        <Card className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <p className="text-sm text-ink">Class heads-up</p>
            <p className="text-sm text-ink-muted">
              How early a class reminder appears. Used on the web and for new phone alarms.
            </p>
          </div>
          <Select
            aria-label="Class reminder lead time"
            className="w-48"
            value={String(leadMinutes)}
            onChange={(event) =>
              void updateProfileDoc(
                {
                  notifications: {
                    enabled: true,
                    leadMinutes: Number(event.target.value),
                    sound: true,
                    vibrate: true,
                  },
                },
                'Reminder default saved',
              )
            }
          >
            <option value="0">At the time</option>
            <option value="5">5 minutes before</option>
            <option value="10">10 minutes before</option>
            <option value="15">15 minutes before</option>
            <option value="30">30 minutes before</option>
          </Select>
        </Card>
      </section>

      <section aria-labelledby="timezone">
        <h2 id="timezone" className="mb-3 text-base font-medium text-ink">
          Timezone
        </h2>
        <Card className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <p className="text-sm text-ink">{meta.timezone ?? 'UTC'}</p>
            <p className="text-sm text-ink-muted">
              This browser thinks you are in {detectedZone}.
            </p>
          </div>
          <Button
            variant="secondary"
            onClick={() => updateProfileDoc({ timezone: detectedZone }, 'Timezone updated')}
            disabled={busy !== null}
          >
            Use {detectedZone}
          </Button>
        </Card>
      </section>

      <section aria-labelledby="data">
        <h2 id="data" className="mb-3 text-base font-medium text-ink">
          Your data
        </h2>
        <Card className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <p className="text-sm text-ink">Export everything</p>
            <p className="text-sm text-ink-muted">
              Subjects, classes, tasks, sessions, and alarms as JSON. Timestamps are epoch objects.
            </p>
          </div>
          <Button variant="secondary" onClick={exportData} disabled={busy === 'export'}>
            {busy === 'export' ? 'Preparing…' : 'Download JSON'}
          </Button>
        </Card>
      </section>

      <p className="text-xs leading-relaxed text-ink-muted">
        Slot v0.1 · Firestore Spark plan · no server, no analytics. Deleting your account is done
        from the Firebase console for now.
      </p>
    </div>
  )
}
