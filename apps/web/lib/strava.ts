/**
 * Parses a Strava "activities.csv" export into training sessions.
 *
 * Kept as a no-server fallback. The Garmin importer is the primary path now;
 * both normalise to the same ImportedSession shape.
 */
import type { ImportedSession } from './importers'

function parseCsv(text: string): string[][] {
  const rows: string[][] = []
  let row: string[] = []
  let field = ''
  let quoted = false

  const source = text.replace(/^\uFEFF/, '')

  for (let index = 0; index < source.length; index += 1) {
    const char = source[index]

    if (quoted) {
      if (char === '"') {
        if (source[index + 1] === '"') {
          field += '"'
          index += 1
        } else {
          quoted = false
        }
      } else {
        field += char
      }
      continue
    }

    if (char === '"') {
      quoted = true
    } else if (char === ',') {
      row.push(field)
      field = ''
    } else if (char === '\n' || char === '\r') {
      if (char === '\r' && source[index + 1] === '\n') index += 1
      row.push(field)
      field = ''
      if (row.some((value) => value.trim() !== '')) rows.push(row)
      row = []
    } else {
      field += char
    }
  }

  row.push(field)
  if (row.some((value) => value.trim() !== '')) rows.push(row)
  return rows
}

function toMinutes(value: string): number | null {
  const raw = value.trim()
  if (!raw) return null

  if (raw.includes(':')) {
    const parts = raw.split(':').map((part) => Number(part))
    if (parts.some((part) => !Number.isFinite(part))) return null
    const [hours = 0, minutes = 0, seconds = 0] = parts
    return Math.round(hours * 60 + minutes + seconds / 60)
  }

  const seconds = Number(raw)
  return Number.isFinite(seconds) ? Math.round(seconds / 60) : null
}

function toKm(value: string, unit: 'km' | 'mi'): number | null {
  const number = Number(value.trim().replace(',', ''))
  if (!Number.isFinite(number)) return null
  return unit === 'mi' ? Math.round(number * 1.60934 * 100) / 100 : Math.round(number * 100) / 100
}

export function parseStravaCsv(
  text: string,
  unit: 'km' | 'mi' = 'km',
): { sessions: ImportedSession[]; skipped: number } {
  const rows = parseCsv(text)
  if (rows.length < 2) return { sessions: [], skipped: 0 }

  const header = rows[0]!.map((cell) => cell.trim().toLowerCase())
  const column = (name: string): number => header.indexOf(name.toLowerCase())

  const idIndex = column('Activity ID')
  const dateIndex = column('Activity Date')
  const nameIndex = column('Activity Name')
  const typeIndex = column('Activity Type')
  const timeIndex = column('Elapsed Time')
  const distanceIndex = column('Distance')

  if (idIndex < 0 || dateIndex < 0 || typeIndex < 0 || timeIndex < 0) {
    return { sessions: [], skipped: rows.length - 1 }
  }

  const sessions: ImportedSession[] = []
  let skipped = 0

  for (const row of rows.slice(1)) {
    const type = (row[typeIndex] ?? '').toLowerCase()
    const id = (row[idIndex] ?? '').trim()
    const startedAt = new Date((row[dateIndex] ?? '').trim())
    const minutes = toMinutes(row[timeIndex] ?? '')

    if (!type.includes('run') || !id || Number.isNaN(startedAt.getTime()) || minutes === null) {
      skipped += 1
      continue
    }

    sessions.push({
      externalId: id,
      title: (row[nameIndex] ?? '').trim() || 'Run',
      startedAt,
      durationMinutes: Math.min(minutes, 1440),
      distanceKm: distanceIndex >= 0 ? toKm(row[distanceIndex] ?? '', unit) : null,
      type: 'run',
    })
  }

  return { sessions, skipped }
}
