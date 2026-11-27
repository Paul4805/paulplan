import { describe, expect, it } from 'vitest'

import { activityStreak, rangeDays, rangeLabel } from './stats'
import { addDays, startOfDay } from './time'

function days(values: number[]) {
  const start = startOfDay(addDays(new Date(), -(values.length - 1)))
  return values.map((minutes, index) => ({ date: addDays(start, index), minutes }))
}

describe('activityStreak', () => {
  it('counts a run ending today', () => {
    expect(activityStreak(days([10, 0, 5, 20, 15]))).toEqual({ current: 3, longest: 3 })
  })

  it('breaks the current streak when today is empty', () => {
    expect(activityStreak(days([10, 10, 10, 0]))).toEqual({ current: 0, longest: 3 })
  })

  it('keeps the longest run from earlier in the window', () => {
    expect(activityStreak(days([5, 5, 5, 5, 0, 5]))).toEqual({ current: 1, longest: 4 })
  })

  it('returns zero for an empty list', () => {
    expect(activityStreak([])).toEqual({ current: 0, longest: 0 })
  })
})

describe('rangeDays', () => {
  it('caps a long term window while ending today', () => {
    const now = new Date('2026-11-27T12:00:00')
    const list = rangeDays('term', { termStart: '2026-01-05' }, now, 31)
    expect(list).toHaveLength(31)
    expect(list[list.length - 1]!.getDate()).toBe(27)
  })

  it('labels the ranges', () => {
    expect(rangeLabel('week')).toBe('This week')
    expect(rangeLabel('month')).toBe('This month')
    expect(rangeLabel('term')).toBe('This term')
  })
})
