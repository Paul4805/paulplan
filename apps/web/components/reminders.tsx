'use client'

import { useEffect, useState } from 'react'

import { useAuth } from '@/lib/auth'
import { useCollection, useNow, userPath } from '@/lib/db'
import {
  alarmMinutesForDate,
  dayOfWeek,
  minutesNow,
  minutesToLabel,
} from '@/lib/time'
import type { Alarm, ClassSlot, Subject } from '@/lib/types'

const SHOWN_KEY = 'slot-reminded'
const LEAD_MINUTES = 10

interface Due {
  key: string
  title: string
  detail: string
}

function readShown(): Set<string> {
  try {
    const raw = sessionStorage.getItem(SHOWN_KEY)
    return new Set<string>(raw ? (JSON.parse(raw) as string[]) : [])
  } catch {
    return new Set<string>()
  }
}

function markShown(key: string): void {
  try {
    const next = Array.from(readShown()).slice(-40)
    next.push(key)
    sessionStorage.setItem(SHOWN_KEY, JSON.stringify(next))
  } catch {
    // Storage blocked: a reminder may repeat in this session. Harmless.
  }
}

/**
 * The web build has no background delivery — no service-worker push, no Cloud
 * Functions. While a tab is open, this watches the wall clock and surfaces a
 * banner at the same lead time the phone uses. The UI says so explicitly.
 */
export function InPageReminders() {
  const { user } = useAuth()
  const alarms = useCollection<Alarm>(user ? userPath(user.uid, 'alarms') : null)
  const classes = useCollection<ClassSlot>(user ? userPath(user.uid, 'classes') : null)
  const subjects = useCollection<Subject>(user ? userPath(user.uid, 'subjects') : null)
  const now = useNow(20_000)
  const [due, setDue] = useState<Due | null>(null)

  useEffect(() => {
    if (!user) return

    const minutes = minutesNow(now)
    const dateKey = `${now.getFullYear()}-${now.getMonth() + 1}-${now.getDate()}`
    const shown = readShown()
    const matches: Due[] = []

    for (const slot of classes.items) {
      if (slot.dayOfWeek !== dayOfWeek(now)) continue
      const subject = subjects.items.find((item) => item.id === slot.subjectId)
      const name = subject?.name ?? 'Class'
      const room = slot.room ? ` · ${slot.room}` : ''

      if (slot.startMinutes - LEAD_MINUTES === minutes) {
        matches.push({
          key: `class:${slot.id}:${dateKey}:lead`,
          title: `${name} in ${LEAD_MINUTES} minutes`,
          detail: `${minutesToLabel(slot.startMinutes)}${room}`,
        })
      }
      if (slot.startMinutes === minutes) {
        matches.push({
          key: `class:${slot.id}:${dateKey}:start`,
          title: `${name} starts now`,
          detail: `${minutesToLabel(slot.startMinutes)}${room}`,
        })
      }
    }

    for (const alarm of alarms.items) {
      for (const at of alarmMinutesForDate(alarm, now)) {
        if (at - (alarm.leadMinutes ?? 0) === minutes) {
          matches.push({
            key: `alarm:${alarm.id}:${dateKey}:${at}`,
            title: alarm.title,
            detail: minutesToLabel(at),
          })
        }
      }
    }

    const fresh = matches.find((match) => !shown.has(match.key))
    if (fresh) {
      markShown(fresh.key)
      setDue(fresh)
    }
  }, [now, alarms.items, classes.items, subjects.items, user])

  if (!due) return null

  return (
    <div className="sticky top-14 z-20 px-4 pt-3 lg:top-0 lg:pt-4">
      <div
        role="status"
        className="mx-auto flex max-w-2xl items-start gap-3 rounded-card border border-accent bg-accent-subtle px-4 py-3"
      >
        <span className="mt-1 size-2 shrink-0 rounded-pill bg-accent" aria-hidden />
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-medium text-accent-text">{due.title}</p>
          <p className="text-xs text-accent-text">{due.detail}</p>
        </div>
        <button
          type="button"
          onClick={() => setDue(null)}
          className="rounded-chip px-1.5 text-xs font-medium text-accent-text hover:bg-surface"
        >
          Dismiss
        </button>
      </div>
    </div>
  )
}
