import { describe, expect, it } from 'vitest'

import {
  DAY_LABELS,
  alarmMinutesForDate,
  dayOfWeek,
  daysUntil,
  dateKeyLocal,
  durationLabel,
  formatDue,
  minutesToLabel,
  minutesToTimeInput,
  occursOnDate,
  startOfWeek,
  termState,
  timeInputToMinutes,
  weekDates,
} from './time'

const friday = new Date(2026, 10, 27, 9, 0, 0) // Fri 27 Nov 2026

describe('clock formatting', () => {
  it('renders minutes as zero-padded 24h', () => {
    expect(minutesToLabel(540)).toBe('09:00')
    expect(minutesToLabel(0)).toBe('00:00')
    expect(minutesToLabel(1439)).toBe('23:59')
  })

  it('round-trips time inputs', () => {
    expect(timeInputToMinutes('10:15')).toBe(615)
    expect(minutesToTimeInput(615)).toBe('10:15')
  })

  it('formats durations without a trailing zero component', () => {
    expect(durationLabel(45)).toBe('45m')
    expect(durationLabel(60)).toBe('1h')
    expect(durationLabel(95)).toBe('1h 35m')
  })
})

describe('weeks', () => {
  it('treats Monday as day 1', () => {
    expect(dayOfWeek(friday)).toBe(5)
    expect(DAY_LABELS[4]).toBe('Fri')
  })

  it('starts a week on Monday and returns seven days', () => {
    const week = weekDates(friday, 1)
    expect(week).toHaveLength(7)
    expect(week[0]?.getDate()).toBe(23)
    expect(week[6]?.getDate()).toBe(29)
    expect(week[0]?.getDay()).toBe(1)
    expect(startOfWeek(friday).getDate()).toBe(23)
  })
})

describe('due labels', () => {
  function timestamp(date: Date) {
    return { toDate: () => date } as never
  }

  it('handles no deadline', () => {
    expect(formatDue(null)).toBeNull()
  })

  it('labels today, tomorrow, and overdue', () => {
    const today = new Date()
    today.setHours(17, 30, 0, 0)
    expect(formatDue(timestamp(today))?.label).toBe('Today · 17:30')

    const tomorrow = new Date()
    tomorrow.setDate(tomorrow.getDate() + 1)
    tomorrow.setHours(9, 0, 0, 0)
    expect(formatDue(timestamp(tomorrow))?.label).toBe('Tomorrow · 09:00')
    expect(daysUntil(timestamp(tomorrow))).toBe(1)

    const yesterday = new Date()
    yesterday.setDate(yesterday.getDate() - 1)
    expect(formatDue(timestamp(yesterday))?.tone).toBe('danger')
  })
})

describe('alarm occurrences', () => {
  it('fires weekly alarms only on the matching weekday', () => {
    const weekly = {
      enabled: true,
      recurrence: 'weekly' as const,
      dayOfWeek: dayOfWeek(friday),
      timeMinutes: 540,
    }
    expect(alarmMinutesForDate(weekly as never, friday)).toEqual([540])
    expect(alarmMinutesForDate(weekly as never, new Date(2026, 10, 26))).toEqual([])
    expect(alarmMinutesForDate({ ...weekly, enabled: false } as never, friday)).toEqual([])
  })

  it('fires daily alarms every day', () => {
    const daily = { enabled: true, recurrence: 'daily' as const, timeMinutes: 420 }
    expect(alarmMinutesForDate(daily as never, friday)).toEqual([420])
  })

  it('fires one-off alarms only on their date', () => {
    const once = {
      enabled: true,
      recurrence: 'once' as const,
      onceAt: { toDate: () => new Date(2026, 10, 27, 6, 45) },
    }
    expect(alarmMinutesForDate(once as never, friday)).toEqual([405])
    expect(alarmMinutesForDate(once as never, new Date(2026, 10, 28))).toEqual([])
  })
})

describe('term windows', () => {
  it('detects before, during and after a term', () => {
    const term = { termStart: '2026-09-01', termEnd: '2026-12-15' }
    expect(termState(term, new Date(2026, 7, 31))).toBe('before')
    expect(termState(term, new Date(2026, 10, 27))).toBe('active')
    expect(termState(term, new Date(2026, 11, 16))).toBe('after')
  })

  it('treats an unset term as always active', () => {
    expect(termState({}, friday)).toBe('unset')
  })
})

describe('one-off events', () => {
  it('matches weekly classes by weekday', () => {
    expect(occursOnDate({ dayOfWeek: 5 }, friday)).toBe(true)
    expect(occursOnDate({ dayOfWeek: 4 }, friday)).toBe(false)
  })

  it('matches a dated event only on that date', () => {
    expect(dateKeyLocal(friday)).toBe('2026-11-27')
    expect(occursOnDate({ dayOfWeek: 5, date: '2026-11-27' }, friday)).toBe(true)
    expect(occursOnDate({ dayOfWeek: 5, date: '2026-11-28' }, friday)).toBe(false)
  })
})
