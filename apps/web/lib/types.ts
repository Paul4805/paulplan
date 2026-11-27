import type { Timestamp } from 'firebase/firestore'

export type TagColor = 'indigo' | 'sky' | 'violet' | 'amber' | 'rose' | 'emerald'
export type TaskStatus = 'todo' | 'doing' | 'done'
export type Priority = 'low' | 'medium' | 'high'
export type Recurrence = 'weekly' | 'daily' | 'once'
export type AlarmKind = 'class' | 'task' | 'custom'
export type ThemeMode = 'system' | 'light' | 'dark'

export const TAG_COLORS: TagColor[] = ['indigo', 'sky', 'violet', 'amber', 'rose', 'emerald']
export const PRIORITIES: Priority[] = ['low', 'medium', 'high']
export const STATUSES: TaskStatus[] = ['todo', 'doing', 'done']

export interface WithId {
  id: string
}

export interface Subject extends WithId {
  name: string
  code?: string
  color: TagColor
  teacher?: string
  room?: string
  targetHoursPerWeek?: number
  credits?: number
  archived: boolean
  createdAt: Timestamp | null
  updatedAt: Timestamp | null
}

export interface ClassSlot extends WithId {
  kind?: 'class' | 'event'
  subjectId?: string | null
  title?: string
  dayOfWeek: number
  startMinutes: number
  endMinutes: number
  room?: string
  teacher?: string
  colorOverride?: TagColor
  notes?: string
  termId?: string
}

export interface Task extends WithId {
  title: string
  notes?: string
  subjectId?: string | null
  dueAt?: Timestamp | null
  priority: Priority
  status: TaskStatus
  completedAt?: Timestamp | null
  order: number
  remind?: boolean
  alarmId?: string | null
  archived?: boolean
}

export interface StudySession extends WithId {
  subjectId?: string | null
  taskId?: string | null
  startedAt: Timestamp | null
  endedAt: Timestamp | null
  durationMinutes: number
  source: 'pomodoro' | 'manual'
  note?: string
}

export type PtType = 'run' | 'football' | 'gym' | 'other'

export const PT_TYPES: PtType[] = ['run', 'football', 'gym', 'other']

export interface PtSession extends WithId {
  type: PtType
  title?: string
  startedAt: Timestamp | null
  durationMinutes: number
  distanceKm?: number | null
  source: 'manual' | 'strava-csv' | 'garmin-json' | 'garmin-api'
  externalId?: string | null
  avgHr?: number | null
  maxHr?: number | null
  elevationGain?: number | null
  calories?: number | null
  notes?: string
  createdAt?: Timestamp | null
  updatedAt?: Timestamp | null
}

export interface GarminStatus {
  enabled?: boolean
  lastSyncAt?: Timestamp | null
  lastImported?: number
  requestedAt?: Timestamp | null
  healthLastSyncAt?: Timestamp | null
  healthDays?: number
  healthError?: string | null
}

export interface Alarm extends WithId {
  title: string
  kind: AlarmKind
  recurrence: Recurrence
  dayOfWeek?: number
  timeMinutes?: number
  onceAt?: Timestamp | null
  leadMinutes: number
  exact?: boolean
  enabled: boolean
  timezone: string
  targetId?: string
  subjectId?: string
  subjectName?: string
}

export interface AlarmState extends WithId {
  alarmId: string
  state: 'snoozed' | 'dismissed'
  snoozeUntil?: Timestamp | null
  actedAt: Timestamp | null
  actedBy?: 'web' | 'android'
}

export interface Profile {
  timezone?: string
  weekStartsOn?: number
  theme?: ThemeMode
  termName?: string
  termStart?: string
  termEnd?: string
  pomodoro?: { focus: number; short: number; long: number }
  notifications?: { enabled: boolean; leadMinutes: number; sound: boolean; vibrate: boolean; browser?: boolean }
  schemaVersion?: number
}

export interface DailyHealth extends WithId {
  date: string
  sleepMinutes?: number | null
  sleepScore?: number | null
  steps?: number | null
  hrv?: number | null
}

export const DEFAULT_PROFILE: Profile = {
  timezone: 'UTC',
  weekStartsOn: 1,
  theme: 'system',
  pomodoro: { focus: 25, short: 5, long: 15 },
  notifications: { enabled: true, leadMinutes: 10, sound: true, vibrate: true, browser: false },
  schemaVersion: 1,
}
