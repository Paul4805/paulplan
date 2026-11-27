import type { ImportedSession } from './importers'
import type { PtType } from './types'

function mapType(key: string): PtType {
  const value = key.toLowerCase()
  if (value.includes('run')) return 'run'
  if (value.includes('soccer') || value.includes('football')) return 'football'
  if (value.includes('strength') || value.includes('gym') || value.includes('weight')) return 'gym'
  return 'other'
}

function findArray(value: unknown, depth = 0): unknown[] {
  if (Array.isArray(value)) return value
  if (depth > 3 || typeof value !== 'object' || value === null) return []

  for (const entry of Object.values(value as Record<string, unknown>)) {
    if (Array.isArray(entry)) return entry
  }
  for (const entry of Object.values(value as Record<string, unknown>)) {
    const found = findArray(entry, depth + 1)
    if (found.length > 0) return found
  }
  return []
}

function parseStart(record: Record<string, unknown>): Date | null {
  const begin = Number(record.beginTimestamp)
  if (Number.isFinite(begin) && begin > 1_000_000_000_000) return new Date(begin)

  const raw = String(record.startTimeGMT ?? '').trim()
  if (!raw) return null

  // "2026-11-27 01:30:00" is GMT; add the Z that Garmin omits.
  const iso = raw.includes('T') ? raw : `${raw.replace(' ', 'T')}Z`
  const date = new Date(iso)
  return Number.isNaN(date.getTime()) ? null : date
}

/**
 * Parses `*_summarizedActivities.json` from a Garmin Connect data export
 * (Account → Data Management → Export Your Data). Garmin stores distance in
 * metres and duration in seconds, in GMT.
 */
export function parseGarminJson(text: string): {
  sessions: ImportedSession[]
  skipped: number
} {
  let parsed: unknown
  try {
    parsed = JSON.parse(text.replace(/^\uFEFF/, ''))
  } catch {
    return { sessions: [], skipped: 0 }
  }

  const rows = findArray(parsed)
  const sessions: ImportedSession[] = []
  let skipped = 0

  for (const row of rows) {
    if (typeof row !== 'object' || row === null) {
      skipped += 1
      continue
    }

    const record = row as Record<string, unknown>
    const externalId = String(record.activityId ?? '').trim()
    const startedAt = parseStart(record)
    const seconds = Number(record.duration ?? record.elapsedDuration ?? 0)
    const minutes = Number.isFinite(seconds) ? Math.round(seconds / 60) : 0

    if (!externalId || !startedAt || minutes <= 0) {
      skipped += 1
      continue
    }

    const meters = Number(record.distance ?? 0)
    const typeInfo = record.activityType
    const typeKey =
      typeof typeInfo === 'object' && typeInfo !== null
        ? String((typeInfo as Record<string, unknown>).typeKey ?? '')
        : String(typeInfo ?? '')

    sessions.push({
      externalId,
      title: String(record.activityName ?? '').trim() || typeKey || 'Garmin activity',
      startedAt,
      durationMinutes: Math.min(minutes, 1440),
      distanceKm:
        Number.isFinite(meters) && meters > 0 ? Math.round(meters / 10) / 100 : null,
      type: mapType(typeKey),
    })
  }

  return { sessions, skipped }
}
