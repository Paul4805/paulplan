import type { PtType } from './types'

/** Normalised shape both importers produce. */
export interface ImportedSession {
  externalId: string
  title: string
  startedAt: Date
  durationMinutes: number
  distanceKm: number | null
  type: PtType
}
