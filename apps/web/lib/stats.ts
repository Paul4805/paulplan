import type { Profile } from './types'
import { addDays, startOfDay, startOfWeek } from './time'

export type StatsRange = 'week' | 'month' | 'term'

export interface DayValue {
  date: Date
  minutes: number
}

export function dateKey(date: Date): string {
  return `${date.getFullYear()}-${date.getMonth()}-${date.getDate()}`
}

/** Consecutive days with activity, ending today (GitHub-style). */
export function activityStreak(days: DayValue[], minimum = 1): {
  current: number
  longest: number
} {
  let current = 0
  for (let index = days.length - 1; index >= 0; index -= 1) {
    if ((days[index]?.minutes ?? 0) < minimum) break
    current += 1
  }

  let longest = 0
  let run = 0
  for (const day of days) {
    if (day.minutes >= minimum) {
      run += 1
      longest = Math.max(longest, run)
    } else {
      run = 0
    }
  }

  return { current, longest: Math.max(longest, current) }
}

/** The first instant included in a stats range. */
export function rangeStart(range: StatsRange, profile: Profile | null, now = new Date()): Date {
  if (range === 'term') {
    if (profile?.termStart) {
      const parsed = new Date(`${profile.termStart}T00:00:00`)
      if (!Number.isNaN(parsed.getTime())) return parsed
    }
    return addDays(startOfDay(now), -29)
  }
  if (range === 'month') return new Date(now.getFullYear(), now.getMonth(), 1)
  return startOfWeek(now, profile?.weekStartsOn ?? 1)
}

/** Inclusive day list for a range, capped so charts stay readable. */
export function rangeDays(
  range: StatsRange,
  profile: Profile | null,
  now = new Date(),
  maxDays = 31,
): Date[] {
  const start = startOfDay(rangeStart(range, profile, now))
  const today = startOfDay(now)
  const total = Math.max(1, Math.round((today.getTime() - start.getTime()) / 86_400_000) + 1)
  const capped = Math.min(total, maxDays)
  const from = addDays(today, -(capped - 1))
  return Array.from({ length: capped }, (_, index) => addDays(from, index))
}

export function rangeLabel(range: StatsRange): string {
  if (range === 'week') return 'This week'
  if (range === 'month') return 'This month'
  return 'This term'
}
