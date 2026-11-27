import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
  type RulesTestEnvironment,
} from '@firebase/rules-unit-testing'
import { deleteDoc, doc, getDoc, serverTimestamp, setDoc } from 'firebase/firestore'
import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest'

let env: RulesTestEnvironment

beforeAll(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-slot',
    firestore: {
      rules: readFileSync(resolve(process.cwd(), '../../firestore.rules'), 'utf8'),
    },
  })
})

afterAll(async () => {
  await env.cleanup()
})

beforeEach(async () => {
  await env.clearFirestore()
})

const asAlice = () => env.authenticatedContext('alice').firestore()
const asBob = () => env.authenticatedContext('bob').firestore()
const asAnonymous = () => env.unauthenticatedContext().firestore()

const path = (suffix: string, uid = 'alice') => `users/${uid}/${suffix}`

function subjectData(overrides: Record<string, unknown> = {}) {
  return {
    name: 'Algorithms',
    color: 'indigo',
    archived: false,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
    ...overrides,
  }
}

function classData(overrides: Record<string, unknown> = {}) {
  return {
    subjectId: 'algorithms',
    dayOfWeek: 2,
    startMinutes: 540,
    endMinutes: 630,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
    ...overrides,
  }
}

function taskData(overrides: Record<string, unknown> = {}) {
  return {
    title: 'Problem set 3',
    status: 'todo',
    priority: 'medium',
    sortKey: 'a0',
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
    ...overrides,
  }
}

function alarmStateData(overrides: Record<string, unknown> = {}) {
  return {
    alarmId: 'alarm-1',
    state: 'dismissed',
    actedAt: serverTimestamp(),
    ...overrides,
  }
}

describe('ownership', () => {
  it('denies anonymous reads', async () => {
    await assertFails(getDoc(doc(asAnonymous(), path('subjects/s1'))))
  })

  it('denies reading another user’s data', async () => {
    await assertSucceeds(setDoc(doc(asAlice(), path('subjects/s1')), subjectData()))

    await assertFails(getDoc(doc(asBob(), path('subjects/s1'))))
    await assertFails(setDoc(doc(asBob(), path('subjects/s1')), subjectData()))
    await assertFails(deleteDoc(doc(asBob(), path('subjects/s1'))))
  })

  it('allows the owner to read and write', async () => {
    await assertSucceeds(setDoc(doc(asAlice(), path('subjects/s1')), subjectData()))
    await assertSucceeds(getDoc(doc(asAlice(), path('subjects/s1'))))
    await assertSucceeds(deleteDoc(doc(asAlice(), path('subjects/s1'))))
  })

  it('denies collections that have no rule, even for the owner', async () => {
    await assertFails(
      setDoc(doc(asAlice(), path('secrets/s1')), { updatedAt: serverTimestamp() }),
    )
  })
})

describe('write validation', () => {
  it('requires a server timestamp for updatedAt', async () => {
    await assertFails(
      setDoc(doc(asAlice(), path('subjects/s1')), subjectData({ updatedAt: new Date() })),
    )
    // A plain number is a legal Firestore value but is not a timestamp, so the
    // client-side write reaches the rules and is rejected there.
    await assertFails(
      setDoc(doc(asAlice(), path('subjects/s1')), subjectData({ updatedAt: 0 })),
    )
  })

  it('rejects subjects outside the colour palette', async () => {
    await assertFails(
      setDoc(doc(asAlice(), path('subjects/s1')), subjectData({ color: 'magenta' })),
    )
  })

  it('rejects an empty subject name', async () => {
    await assertFails(setDoc(doc(asAlice(), path('subjects/s1')), subjectData({ name: '' })))
  })

  it('rejects class slots that end before they start', async () => {
    await assertFails(
      setDoc(
        doc(asAlice(), path('classes/c1')),
        classData({ startMinutes: 600, endMinutes: 600 }),
      ),
    )
    await assertSucceeds(setDoc(doc(asAlice(), path('classes/c1')), classData()))
  })

  it('accepts free-standing events with a title and colour', async () => {
    const event = {
      kind: 'event',
      title: 'Study group',
      colorOverride: 'violet',
      dayOfWeek: 4,
      startMinutes: 1080,
      endMinutes: 1140,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    }

    await assertSucceeds(setDoc(doc(asAlice(), path('classes/e1')), event))
    await assertFails(
      setDoc(doc(asAlice(), path('classes/e1')), { ...event, colorOverride: 'magenta' }),
    )
    await assertFails(
      setDoc(doc(asAlice(), path('classes/e1')), { ...event, title: '' }),
    )
  })

  it('rejects invalid days of week', async () => {
    await assertFails(
      setDoc(doc(asAlice(), path('classes/c1')), classData({ dayOfWeek: 8 })),
    )
  })

  it('rejects task statuses outside the enum', async () => {
    await assertFails(
      setDoc(doc(asAlice(), path('tasks/t1')), taskData({ status: 'blocked' })),
    )
    await assertSucceeds(setDoc(doc(asAlice(), path('tasks/t1')), taskData()))
  })

  it('rejects study sessions without a valid duration', async () => {
    await assertFails(
      setDoc(doc(asAlice(), path('studySessions/s1')), {
        durationMinutes: 2000,
        startedAt: serverTimestamp(),
        endedAt: serverTimestamp(),
        source: 'pomodoro',
        updatedAt: serverTimestamp(),
      }),
    )
  })

  it('rejects alarms without a timezone or with a bad lead time', async () => {    const alarm = {
      title: 'Wake up',
      kind: 'custom',
      recurrence: 'daily',
      enabled: true,
      leadMinutes: 0,
      timezone: 'Asia/Kolkata',
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    }

    await assertSucceeds(setDoc(doc(asAlice(), path('alarms/a1')), alarm))
    await assertFails(
      setDoc(doc(asAlice(), path('alarms/a1')), { ...alarm, timezone: 42 }),
    )
    await assertFails(
      setDoc(doc(asAlice(), path('alarms/a1')), { ...alarm, leadMinutes: 5000 }),
    )
  })
})

describe('alarm state', () => {
  it('accepts a dismiss stamped with the server time', async () => {
    await assertSucceeds(
      setDoc(doc(asAlice(), path('alarmState/a1_1700000000')), alarmStateData()),
    )
    await assertSucceeds(getDoc(doc(asAlice(), path('alarmState/a1_1700000000'))))
  })

  it('rejects a forged actedAt', async () => {
    await assertFails(
      setDoc(
        doc(asAlice(), path('alarmState/a1_1700000000')),
        alarmStateData({ actedAt: new Date('2020-01-01T00:00:00Z') }),
      ),
    )
  })

  it('rejects unknown states and missing alarm ids', async () => {
    await assertFails(
      setDoc(
        doc(asAlice(), path('alarmState/a1_1700000000')),
        alarmStateData({ state: 'maybe' }),
      ),
    )
    await assertFails(
      setDoc(
        doc(asAlice(), path('alarmState/a1_1700000000')),
        alarmStateData({ alarmId: '' }),
      ),
    )
  })

  it('is not writable by another user', async () => {
    await assertFails(
      setDoc(doc(asBob(), path('alarmState/a1_1700000000')), alarmStateData()),
    )
  })
})

describe('physical training', () => {
  const sessionData = (overrides: Record<string, unknown> = {}) => ({
    type: 'run',
    source: 'manual',
    startedAt: serverTimestamp(),
    durationMinutes: 42,
    distanceKm: 7.5,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
    ...overrides,
  })

  it('accepts runs and gym sessions', async () => {
    await assertSucceeds(setDoc(doc(asAlice(), path('ptSessions/p1')), sessionData()))
    await assertSucceeds(
      setDoc(
        doc(asAlice(), path('ptSessions/p2')),
        sessionData({ type: 'gym', distanceKm: null }),
      ),
    )
  })

  it('rejects unknown types and impossible durations', async () => {
    await assertFails(
      setDoc(doc(asAlice(), path('ptSessions/p1')), sessionData({ type: 'swim' })),
    )
    await assertFails(
      setDoc(doc(asAlice(), path('ptSessions/p1')), sessionData({ durationMinutes: 5000 })),
    )
  })

  it('is private to its owner', async () => {
    await assertFails(setDoc(doc(asBob(), path('ptSessions/p1')), sessionData()))
  })
})

describe('profile', () => {
  it('lets the owner write settings but requires a timezone', async () => {
    const profile = {
      timezone: 'Asia/Kolkata',
      schemaVersion: 1,
      updatedAt: serverTimestamp(),
    }

    await assertSucceeds(setDoc(doc(asAlice(), path('meta/profile')), profile))
    await assertFails(
      setDoc(doc(asAlice(), path('meta/profile')), { ...profile, timezone: 42 }),
    )
  })

  it('keeps the root user document owner-only', async () => {
    await assertSucceeds(
      setDoc(doc(asAlice(), 'users/alice'), { displayName: 'Alice', updatedAt: serverTimestamp() }),
    )
    await assertFails(getDoc(doc(asBob(), 'users/alice')))
  })
})

describe('rules file', () => {
  it('is not permissive by accident', () => {
    const rules = readFileSync(resolve(process.cwd(), '../../firestore.rules'), 'utf8')
    expect(rules).not.toMatch(/allow\s+read,\s*write\s*:\s*if\s+true/)
    expect(rules).toMatch(/rules_version\s*=\s*'2'/)
  })
})
