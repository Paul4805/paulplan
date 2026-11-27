import { describe, expect, it } from 'vitest'

import { parseStravaCsv } from './strava'

const header =
  'Activity ID,Activity Date,Activity Name,Activity Type,Elapsed Time,Distance,Max Heart Rate'

describe('parseStravaCsv', () => {
  it('imports runs and skips other activity types', () => {
    const csv = [
      header,
      '111,"Nov 27, 2026, 7:10:00 AM","Morning run",Run,2520,7.4,150',
      '112,"Nov 26, 2026, 6:00:00 PM","Easy spin",Ride,3600,20.0,120',
      '113,"Nov 25, 2026, 7:00:00 AM","Tempo",Run,00:32:00,6.2,165',
    ].join('\n')

    const result = parseStravaCsv(csv, 'km')

    expect(result.sessions).toHaveLength(2)
    expect(result.skipped).toBe(1)
    expect(result.sessions[0]).toMatchObject({
      externalId: '111',
      title: 'Morning run',
      durationMinutes: 42,
      distanceKm: 7.4,
      type: 'run',
    })
    expect(result.sessions[1]?.durationMinutes).toBe(32)
  })

  it('converts miles to kilometres', () => {
    const csv = [header, '200,2026-11-20 07:00:00,"Long run",Run,3600,10,140'].join('\n')
    const result = parseStravaCsv(csv, 'mi')
    expect(result.sessions[0]?.distanceKm).toBeCloseTo(16.09, 1)
  })

  it('handles quoted commas and escaped quotes', () => {
    const csv = [
      header,
      '300,2026-11-19 07:00:00,"Run, easy ""shakeout""",Run,1800,5,120',
    ].join('\n')
    const result = parseStravaCsv(csv, 'km')
    expect(result.sessions[0]?.title).toBe('Run, easy "shakeout"')
  })

  it('returns nothing for a file without the expected columns', () => {
    expect(parseStravaCsv('foo,bar\n1,2', 'km').sessions).toEqual([])
  })
})
