import type { Timestamp } from 'firebase/firestore'

import type { Alarm } from './types'

export const DAY_LABELS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'] as const
export const DAY_LONG = [
  'Monday',
  'Tuesday',
  'Wednesday',
  'Thursday',
  'Friday',
  'Saturday',
  'Sunday',
] as const

export function pad2(value: number): string {
  return value.toString().padStart(2, '0')
}

export function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max)
}

export function timeInputToMinutes(value: string): number {
  const [hours, minutes] = value.split(':').map(Number)
  return (hours ?? 0) * 60 + (minutes ?? 0)
}

export function minutesToTimeInput(minutes: number): string {
  return `${pad2(Math.floor(minutes / 60))}:${pad2(minutes % 60)}`
}

export function minutesToLabel(minutes: number): string {
  return minutesToTimeInput(minutes)
}

export function minutesNow(date: Date = new Date()): number {
  return date.getHours() * 60 + date.getMinutes()
}

export function toDate(value: Timestamp | null | undefined): Date | null {
  return value ? value.toDate() : null
}

export function startOfDay(date: Date): Date {
  const copy = new Date(date)
  copy.setHours(0, 0, 0, 0)
  return copy
}

export function addDays(date: Date, days: number): Date {
  const copy = new Date(date)
  copy.setDate(copy.getDate() + days)
  return copy
}

/** ISO-ish weekday, 1 = Monday … 7 = Sunday. */
export function dayOfWeek(date: Date): number {
  return ((date.getDay() + 6) % 7) + 1
}

export function startOfWeek(date: Date, weekStartsOn = 1): Date {
  const day = startOfDay(date)
  const offset = (dayOfWeek(day) - weekStartsOn + 7) % 7
  return addDays(day, -offset)
}

export function weekDates(anchor: Date, weekStartsOn = 1): Date[] {
  const start = startOfWeek(anchor, weekStartsOn)
  return Array.from({ length: 7 }, (_, index) => addDays(start, index))
}

export function isSameDay(a: Date, b: Date): boolean {
  return (
    a.getFullYear() === b.getFullYear() &&
    a.getMonth() === b.getMonth() &&
    a.getDate() === b.getDate()
  )
}

export function formatClock(date: Date): string {
  return `${pad2(date.getHours())}:${pad2(date.getMinutes())}`
}

export function formatDateShort(date: Date): string {
  return date.toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short' })
}

export function formatDateLong(date: Date): string {
  return date.toLocaleDateString(undefined, { weekday: 'long', day: 'numeric', month: 'long' })
}

export type DueTone = 'default' | 'warning' | 'danger' | 'success'

export function formatDue(value: Timestamp | null | undefined): {
  label: string
  tone: DueTone
} | null {
  const date = toDate(value)
  if (!date) return null

  const today = startOfDay(new Date())
  const target = startOfDay(date)
  const days = Math.round((target.getTime() - today.getTime()) / 86_400_000)
  const clock = formatClock(date)

  if (days < 0) return { label: `Overdue · ${formatDateShort(date)}`, tone: 'danger' }
  if (days === 0) return { label: `Today · ${clock}`, tone: 'warning' }
  if (days === 1) return { label: `Tomorrow · ${clock}`, tone: 'default' }
  if (days <= 7) return { label: `${formatDateShort(date)} · ${clock}`, tone: 'default' }
  return { label: formatDateShort(date), tone: 'default' }
}

export function dateAtMinutes(date: Date, minutes: number): Date {
  const result = startOfDay(date)
  result.setMinutes(minutes)
  return result
}

export function durationLabel(minutes: number): string {
  if (minutes < 60) return `${Math.round(minutes)}m`
  const hours = Math.floor(minutes / 60)
  const rest = Math.round(minutes % 60)
  return rest === 0 ? `${hours}h` : `${hours}h ${pad2(rest)}m`
}

export function daysUntil(value: Timestamp | null | undefined): number | null {
  const date = toDate(value)
  if (!date) return null
  const today = startOfDay(new Date())
  return Math.round((startOfDay(date).getTime() - today.getTime()) / 86_400_000)
}

export type TermState = 'active' | 'before' | 'after' | 'unset'

/** Local calendar dates from `YYYY-MM-DD`, inclusive. */
export function termState(
  term: { termStart?: string; termEnd?: string } | null | undefined,
  date = new Date(),
): TermState {
  if (!term?.termStart && !term?.termEnd) return 'unset'
  const today = startOfDay(date)

  if (term.termStart) {
    const start = new Date(`${term.termStart}T00:00:00`)
    if (!Number.isNaN(start.getTime()) && today < startOfDay(start)) return 'before'
  }
  if (term.termEnd) {
    const end = new Date(`${term.termEnd}T00:00:00`)
    if (!Number.isNaN(end.getTime()) && today > startOfDay(end)) return 'after'
  }
  return 'active'
}

/** Wall-clock minutes at which an alarm fires on a given date, if it does. */
export function alarmMinutesForDate(alarm: Alarm, date: Date): number[] {
  if (!alarm.enabled) return []

  if (alarm.recurrence === 'weekly') {
    return alarm.dayOfWeek === dayOfWeek(date) && alarm.timeMinutes != null
      ? [alarm.timeMinutes]
      : []
  }

  if (alarm.recurrence === 'daily') {
    return alarm.timeMinutes != null ? [alarm.timeMinutes] : []
  }

  const once = toDate(alarm.onceAt)
  return once && isSameDay(once, date) ? [once.getHours() * 60 + once.getMinutes()] : []
}
