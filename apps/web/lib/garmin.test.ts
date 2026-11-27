import { describe, expect, it } from 'vitest'

import { parseGarminJson } from './garmin'

const sample = JSON.stringify([
  {
    activityId: 12345678901,
    activityName: 'Morning Run',
    activityType: { typeId: 1, typeKey: 'running' },
    startTimeGMT: '2026-11-27 01:30:00',
    duration: 2520.0,
    distance: 7400.0,
  },
  {
    activityId: 12345678902,
    activityName: 'Strength',
    activityType: { typeId: 5, typeKey: 'strength_training' },
    startTimeGMT: '2026-11-26 12:00:00',
    duration: 3600.0,
    distance: 0.0,
  },
  {
    activityId: '',
    activityName: 'Broken row',
    activityType: { typeKey: 'running' },
    duration: 600,
  },
])

describe('parseGarminJson', () => {
  it('maps runs and gym sessions, converting metres and seconds', () => {
    const result = parseGarminJson(sample)

    expect(result.sessions).toHaveLength(2)
    expect(result.skipped).toBe(1)

    const run = result.sessions[0]!
    expect(run).toMatchObject({
      externalId: '12345678901',
      title: 'Morning Run',
      type: 'run',
      durationMinutes: 42,
      distanceKm: 7.4,
    })
    expect(run.startedAt.toISOString()).toBe('2026-11-27T01:30:00.000Z')

    expect(result.sessions[1]).toMatchObject({ type: 'gym', distanceKm: null })
  })

  it('prefers beginTimestamp when present', () => {
    const text = JSON.stringify([
      {
        activityId: 1,
        activityType: { typeKey: 'running' },
        duration: 600,
        distance: 1000,
        beginTimestamp: Date.UTC(2026, 10, 20, 6, 0, 0),
        startTimeGMT: '2026-11-20 00:00:00',
      },
    ])
    const result = parseGarminJson(text)
    expect(result.sessions[0]?.startedAt.toISOString()).toBe('2026-11-20T06:00:00.000Z')
  })

  it('accepts an object wrapper around the activity array', () => {
    const text = JSON.stringify({ summarizedActivitiesExport: JSON.parse(sample).slice(0, 1) })
    expect(parseGarminJson(text).sessions).toHaveLength(1)
  })

  it('returns nothing for invalid JSON', () => {
    expect(parseGarminJson('not json').sessions).toEqual([])
  })
})
