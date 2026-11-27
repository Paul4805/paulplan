'use client'

import { Dialog } from '@/components/ui'
import { durationLabel, toDate } from '@/lib/time'
import type { PtSession, PtType } from '@/lib/types'

const PT_LABEL: Record<PtType, string> = {
  run: 'Running',
  football: 'Football',
  gym: 'Gym',
  other: 'Other',
}

function paceLabel(session: PtSession): string | null {
  if (!session.distanceKm || session.distanceKm <= 0) return null
  const seconds = (session.durationMinutes * 60) / session.distanceKm
  const minutes = Math.floor(seconds / 60)
  const rest = Math.round(seconds % 60)
  return `${minutes}:${rest.toString().padStart(2, '0')} /km`
}

export function SessionDetail({
  session,
  onClose,
}: {
  session: PtSession | null
  onClose: () => void
}) {
  if (!session) return null
  const started = toDate(session.startedAt)
  const pace = paceLabel(session)

  const rows: Array<[string, string]> = [
    ['Duration', durationLabel(session.durationMinutes)],
    ['Distance', session.distanceKm ? `${Math.round(session.distanceKm * 100) / 100} km` : '—'],
    ['Pace', pace ?? '—'],
    ['Average HR', session.avgHr ? `${Math.round(session.avgHr)} bpm` : '—'],
    ['Max HR', session.maxHr ? `${Math.round(session.maxHr)} bpm` : '—'],
    ['Elevation', session.elevationGain ? `${Math.round(session.elevationGain)} m` : '—'],
    ['Calories', session.calories ? `${Math.round(session.calories)} kcal` : '—'],
    [
      'Source',
      session.source === 'garmin-api' || session.source === 'garmin-json'
        ? 'Garmin'
        : session.source === 'strava-csv'
          ? 'Strava'
          : 'Manual',
    ],
  ]

  return (
    <Dialog
      open
      onClose={onClose}
      title={session.title ?? PT_LABEL[session.type]}
      description={
        started
          ? started.toLocaleString(undefined, {
              weekday: 'long',
              day: 'numeric',
              month: 'long',
              hour: '2-digit',
              minute: '2-digit',
            })
          : 'Session detail'
      }
    >
      <dl className="grid grid-cols-2 gap-3">
        {rows.map(([label, value]) => (
          <div key={label} className="rounded-chip border border-border bg-canvas p-3">
            <dt className="text-xs uppercase tracking-wide text-ink-muted">{label}</dt>
            <dd className="mt-1 text-sm font-medium tabular-nums text-ink">{value}</dd>
          </div>
        ))}
      </dl>
      {session.notes ? (
        <p className="mt-3 text-sm leading-relaxed text-ink-muted">{session.notes}</p>
      ) : null}
      {session.source === 'garmin-api' ? (
        <p className="mt-3 text-xs leading-relaxed text-ink-muted">
          Heart rate, elevation and calories come from Garmin. Splits are available on the watch.
        </p>
      ) : null}
    </Dialog>
  )
}
