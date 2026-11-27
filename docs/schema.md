# Data contract — v1

This document is the language-neutral contract for the Firestore schema. `firestore.rules` enforces the required fields listed here, `packages/rules-tests` proves the rules behave, and the Android data layer mirrors it by hand. TypeScript types live in `apps/web/lib/types.ts`.

**Constraints baked into this document:** Spark plan, no Cloud Functions, no FCM, no server-side rendering. Every write in the system comes from a client, under the user's own auth.

---

## 1. Conventions

- **Ownership.** Every document lives under `users/{uid}/`. There are no shared documents and no cross-user reads.
- **IDs.** Client-generated, 20-character URL-safe random strings (`Firestore` auto-ids are fine). Exceptions: `alarmState` uses a deterministic derived id (see §7).
- **Timestamps.** Absolute moments are Firestore `Timestamp` (UTC). Recurring wall-clock values are integers (see §6). Clients never write a literal date for `updatedAt`; they write `serverTimestamp()`, and rules reject anything else.
- **`createdAt` / `updatedAt`.** On every content document. `createdAt` is set once; `updatedAt` on every write. Rules require `updatedAt == request.time`.
- **Optional fields** are omitted, not set to `null`, except `tasks.dueAt`, `tasks.subjectId`, the unused half of `classes.subjectId` / `classes.title`, and `ptSessions.distanceKm`, where `null` is meaningful.
- **Unknown fields are permitted** by the rules for forward compatibility. Clients must not rely on them.
- **`schemaVersion`** lives on `users/{uid}/meta/profile`. Bump it when a migration is needed; migrations run client-side on first launch after an update.
- **Colours** are token names (`indigo`, `sky`, `violet`, `amber`, `rose`, `emerald`), never hex values. The palette lives in `packages/design-tokens/tokens.json`.

---

## 2. Tree

```
users/{uid}                              identity doc
users/{uid}/meta/profile                 settings + schemaVersion
users/{uid}/subjects/{subjectId}
users/{uid}/classes/{classId}            weekly timetable slots
users/{uid}/terms/{termId}               optional campus timezone + term bounds
users/{uid}/tasks/{taskId}
users/{uid}/studySessions/{sessionId}
users/{uid}/ptSessions/{sessionId}
users/{uid}/alarms/{alarmId}             alarm definitions
users/{uid}/alarmState/{eventId}         sparse snooze/dismiss actions
```

Deliberately absent: `devices`, FCM tokens, `alarmEvents`, anything written by a server.

---

## 3. Documents

### `users/{uid}`

| Field | Type | Notes |
| --- | --- | --- |
| `displayName` | string | from the auth provider, editable |
| `email` | string | from the auth provider, not editable |
| `photoURL` | string \| null | |
| `createdAt` | timestamp | |
| `updatedAt` | timestamp | |

```json
{ "displayName": "J Paul", "email": "jp@example.com", "photoURL": null,
  "createdAt": "<ts>", "updatedAt": "<ts>" }
```

### `meta/profile`

One document. `timezone` is the device's IANA zone at last save, used as the default for everything.

| Field | Type | Required by rules |
| --- | --- | --- |
| `timezone` | string (IANA) | yes |
| `weekStartsOn` | int (1 = Monday) | no |
| `theme` | `'system' \| 'light' \| 'dark'` | no |
| `pomodoro` | `{ focus: int, short: int, long: int }` minutes | no |
| `notifications` | `{ enabled: bool, leadMinutes: int, sound: bool, vibrate: bool }` | no |
| `schemaVersion` | int | yes |
| `updatedAt` | timestamp | yes |

```json
{ "timezone": "Asia/Kolkata", "weekStartsOn": 1, "theme": "system",
  "pomodoro": { "focus": 25, "short": 5, "long": 15 },
  "notifications": { "enabled": true, "leadMinutes": 10, "sound": true, "vibrate": true },
  "schemaVersion": 1, "updatedAt": "<ts>" }
```

### `meta/garmin`

Sync status only. The Garmin Personal API key lives on the Android device and is never written here.

| Field | Type | Notes |
| --- | --- | --- |
| `enabled` | bool | set by the phone after a successful sync |
| `lastSyncAt` | timestamp | server time |
| `lastImported` | int | sessions added by the last sync |
| `requestedAt` | timestamp | written by the web's "Sync now via phone" button; the phone watches it |
| `updatedAt` | timestamp | required by rules |

```json
{ "enabled": true, "lastSyncAt": "<ts>", "lastImported": 3,
  "requestedAt": "<ts>", "updatedAt": "<ts>" }
```

The phone must not rewrite `requestedAt` when it syncs, or its own listener would retrigger. It records the handled timestamp locally instead.

### `subjects`

| Field | Type | Required |
| --- | --- | --- |
| `name` | string, 1–80 chars | yes |
| `code` | string | no |
| `color` | enum above | yes |
| `teacher` | string | no |
| `room` | string | no |
| `targetHoursPerWeek` | number ≥ 0 | no |
| `credits` | number | no |
| `archived` | bool | yes |
| `sortKey` | string | no |

```json
{ "name": "Algorithms", "code": "CS301", "color": "indigo", "teacher": "Dr. Rao",
  "room": "LT-4", "targetHoursPerWeek": 4, "archived": false,
  "createdAt": "<ts>", "updatedAt": "<ts>" }
```

### `classes` (timetable entries)

One collection for both classes and free-standing events, because both are weekly wall-clock entries. A class links to a subject; an event carries its own title and colour.

| Field | Type | Required |
| --- | --- | --- |
| `kind` | `'class' \| 'event'` | no (defaults to class for older docs) |
| `subjectId` | string \| null | when `kind = class` |
| `title` | string, 1–80 chars \| null | when `kind = event` |
| `dayOfWeek` | int 1–7 (Mon = 1) | yes |
| `startMinutes` | int 0–1439, minutes from local midnight | yes |
| `endMinutes` | int > `startMinutes`, ≤ 1440 | yes |
| `room` | string | no |
| `teacher` | string | no |
| `colorOverride` | enum | no; events use it, classes inherit the subject colour |
| `notes` | string | no |
| `termId` | string | no |

```json
{ "kind": "event", "title": "Study group", "colorOverride": "violet",
  "dayOfWeek": 4, "startMinutes": 1080, "endMinutes": 1140,
  "room": "Library", "createdAt": "<ts>", "updatedAt": "<ts>" }
```

Rules require **either** a non-empty `subjectId` **or** a non-empty `title`, so a malformed entry cannot exist. `startMinutes: 540` renders as 09:00 in the entry's timezone. Entries carry no date: they repeat weekly.

### `terms` (optional)

| Field | Type | Notes |
| --- | --- | --- |
| `name` | string | "Autumn 2026" |
| `startDate` / `endDate` | string `YYYY-MM-DD` | wall-clock dates, no zone |
| `timezone` | string (IANA) | campus zone; overrides the profile zone for this term |
| `weekParity` | `'every' \| 'odd' \| 'even'` | optional |

Resolution order for a class's timezone: `terms/{termId}.timezone` → `meta/profile.timezone`. This matters when travelling: a 09:00 class stays 09:00 at the campus, not 09:00 wherever you are.

### `tasks`

| Field | Type | Required |
| --- | --- | --- |
| `title` | string, 1–200 chars | yes |
| `notes` | string | no |
| `subjectId` | string \| null | no |
| `dueAt` | timestamp \| null (UTC) | no |
| `priority` | `'low' \| 'medium' \| 'high'` | yes |
| `status` | `'todo' \| 'doing' \| 'done'` | yes |
| `completedAt` | timestamp \| null | no |
| `order` | number, midpoint-inserted | no |```json
{ "title": "Problem set 3", "subjectId": "cs301",
  "dueAt": "<ts>", "priority": "high", "status": "todo",
  "completedAt": null, "order": 1000, "createdAt": "<ts>", "updatedAt": "<ts>" }
```

### `studySessions`

| Field | Type | Required |
| --- | --- | --- |
| `subjectId` | string \| null | no |
| `taskId` | string \| null | no |
| `startedAt` / `endedAt` | timestamp (UTC) | yes |
| `durationMinutes` | int 0–1440 | yes |
| `source` | `'pomodoro' \| 'manual'` | yes |
| `note` | string | no |

```json
{ "subjectId": "cs301", "startedAt": "<ts>", "endedAt": "<ts>",
  "durationMinutes": 25, "source": "pomodoro", "createdAt": "<ts>", "updatedAt": "<ts>" }
```

Sessions are append-only and immutable in practice; editing a past session rewrites `durationMinutes`. Progress and weekly hours are derived by summing these documents, never stored.

### `ptSessions` (physical training)

| Field | Type | Required |
| --- | --- | --- |
| `type` | `'run' \| 'football' \| 'gym' \| 'other'` | yes |
| `title` | string | no (defaults to the type) |
| `startedAt` | timestamp (UTC) | yes |
| `durationMinutes` | int 0–1440 | yes |
| `distanceKm` | number ≥ 0 \| null | no (runs only) |
| `source` | `'manual' \| 'strava-csv' \| 'garmin-json' \| 'garmin-api'` | yes |
| `externalId` | string | no; dedupe key for imports |
| `notes` | string | no |

```json
{ "type": "run", "title": "Morning run", "startedAt": "<ts>",
  "durationMinutes": 42, "distanceKm": 7.4, "source": "garmin-json",
  "externalId": "12345678901", "createdAt": "<ts>", "updatedAt": "<ts>" }
```

`externalId` dedupes imports: the importer skips ids already present.

Three ingestion paths:

- **`garmin-api`** — the primary one. The Android app stores a Garmin Personal API key on-device (never in Firestore), exchanges it for a 24h bearer token, and pulls activities every 12h, on app open, or when the web asks (`meta/garmin.requestedAt`). Garmin sends no CORS headers, so the browser cannot call it; the phone is the sync agent.
- **`garmin-json`** — a Garmin data-export `summarizedActivities.json`, imported from the web (no key needed).
- **`strava-csv`** — a Strava `activities.csv`, kept as a fallback.

`meta/garmin` holds only sync status (`enabled`, `lastSyncAt`, `lastImported`, `requestedAt`), never the key. The profile doc keeps its stricter schema; other meta docs only need a server `updatedAt`.

### `alarms` (definitions)

| Field | Type | Required |
| --- | --- | --- |
| `title` | string | yes |
| `kind` | `'class' \| 'task' \| 'custom'` | yes |
| `recurrence` | `'weekly' \| 'daily' \| 'once'` | yes |
| `dayOfWeek` | int 1–7 | when `weekly` |
| `timeMinutes` | int 0–1439 | when `weekly` or `daily` |
| `onceAt` | timestamp (UTC) | when `once` |
| `leadMinutes` | int 0–1440 | yes (0 = at the moment) |
| `exact` | bool | no; default true on Android |
| `enabled` | bool | yes |
| `timezone` | string (IANA) | yes |
| `targetId` | string | no; `classId` or `taskId` |
| `subjectId` / `subjectName` | string | no; denormalised for display |

```json
{ "title": "Algorithms", "kind": "class", "recurrence": "weekly",
  "dayOfWeek": 2, "timeMinutes": 540, "leadMinutes": 10, "exact": true,
  "enabled": true, "timezone": "Asia/Kolkata", "targetId": "c1",
  "subjectId": "cs301", "subjectName": "Algorithms",
  "createdAt": "<ts>", "updatedAt": "<ts>" }
```

`leadMinutes` is a heads-up reminder. `exact: true` means the alarm itself fires at `timeMinutes` on Android. On the web there is no background delivery: an open tab shows a banner at the reminder time and again at the alarm time.

### `alarmState`

Created only by a user action on a fired occurrence. Never pre-materialised.

| Field | Type | Required |
| --- | --- | --- |
| `alarmId` | string | yes |
| `state` | `'snoozed' \| 'dismissed'` | yes |
| `snoozeUntil` | timestamp (UTC) | when snoozed |
| `actedAt` | timestamp, server time | yes |
| `actedBy` | `'web' \| 'android'` | no |

```json
{ "alarmId": "a1", "state": "snoozed",
  "snoozeUntil": "<ts>", "actedAt": "<ts>", "actedBy": "android" }
```

---

## 4. Listeners per screen

Data volume per user is small (hundreds of documents), so screens subscribe to whole subcollections and filter in memory. Query-based subscriptions would serve a partial cache when offline, which is exactly the wrong behaviour.

| Screen | Collections listened |
| --- | --- |
| Today | `classes`, `tasks`, `alarms`, `alarmState`, `studySessions` (filtered to today/this week in memory) |
| Timetable | `subjects`, `classes`, `tasks` (deadlines in the month grid) |
| Tasks | `tasks`, `subjects` |
| Subject detail | `subjects`, `tasks`, `studySessions` |
| Focus | `subjects`, `studySessions` |
| Training | `ptSessions`, `meta/garmin` (status doc) |
| Stats | `studySessions`, `tasks`, `classes`, `subjects`, `ptSessions` |
| Settings | `meta/profile`, `alarms` |

One repository object owns each listener for the process lifetime; screens subscribe to derived state. No screen opens a duplicate listener.

---

## 5. Offline and conflicts

- Firestore's persistent cache is the offline store and the write queue. Web enables `persistentLocalCache` at init; Android uses the default persistent cache. Writes made offline complete locally, appear immediately, and flush in order on reconnect.
- There is no separate outbox, no retry loop, and no client-side conflict resolver.
- Resolution is Firestore's native per-field last-write-wins on server receipt. Every edit is a single atomic `update()`/`set(merge: true)`, so two devices editing different fields both survive.
- Reordering is an `order` update (midpoint between neighbours), not an index cascade, so concurrent drags cannot corrupt a list.
- Aggregates are derived, so they cannot drift.

---

## 6. Time model

| Kind | Storage | Example |
| --- | --- | --- |
| Absolute instant | `Timestamp` (UTC) | task `dueAt`, `alarm.onceAt`, study session bounds |
| Recurring wall-clock | ints + IANA zone | `dayOfWeek` + `startMinutes` + `timezone` |
| Calendar date | `YYYY-MM-DD` string | term start/end |

Converting a wall-clock slot to an instant happens exactly once per occurrence, on the device that schedules an alarm, using the slot's resolved timezone. `leadMinutes` is subtracted from that instant for the reminder.

---

## 7. Alarm scheduling contract (Android)

With no Cloud Functions there is no server materialisation, so this algorithm is the contract between the definition and the alarm:

```
occurrences(alarm, now, horizon = 7 days, zone = alarm.timezone):
  weekly -> for each date d in [now, now+horizon] where d.dayOfWeek == dayOfWeek:
              at(local(d, timeMinutes), zone)
  daily  -> for each date d in [now, now+horizon]:
              at(local(d, timeMinutes), zone)
  once   -> [onceAt] if onceAt > now - 1h

eventId(alarm, fireAt) = `${alarm.id}_${floor(fireAt / 60s)}`
```

- The phone arms `AlarmManager.setAlarmClock` for each occurrence within the horizon, including `leadMinutes` reminders.
- A `DataStore`-backed JSON snapshot of the computed occurrences is rewritten on every `alarms` emission. Boot, timezone-change, and package-replaced receivers re-arm from that snapshot without waiting for Firebase to initialise.
- Snooze: write `alarmState/{eventId}` with `state: 'snoozed'` and `snoozeUntil`; the ringing device re-arms one local alarm at `snoozeUntil`. Other devices remove the notification if they see it.
- Dismiss: write `alarmState/{eventId}` with `state: 'dismissed'`; every device that is running cancels any matching pending alarm or banner. A force-stopped phone cannot receive this, so a web dismiss may not silence it — accepted and documented in the UI.
- Dedupe: the device that rang records `lastFiredEventId` and ignores duplicates for the same `eventId`.
- Cleanup: clients delete their own `alarmState` documents older than 30 days on launch. No scheduled cleanup exists.

---

## 8. Access control

`firestore.rules` grants `read`/`delete` to the owner for every collection, and `create`/`update` only when `updatedAt == request.time` plus the required-field checks above. `alarmState` uses `actedAt` instead of `updatedAt`. Anything not matched is denied. Rules are tested in the emulator; see `packages/rules-tests/tests/firestore.test.ts`.

---

## 9. Indexes

None. Every listener reads a full subcollection ordered client-side, so no composite index is required. If a future screen queries `tasks where status == 'todo' orderBy dueAt`, add a composite index then, together with a test.

---

## 10. Deferred decision: study-session read cost

Recorded here, built in Phase 8 if needed:

- **(a) Full collection listener** — simplest, trivially correct offline. At ~5 sessions/day that is ~1,800 docs/year; a cold start costs one read per document, so roughly 1.8k reads per app open. Fine on Spark's 50k/day for one user, but 20+ opens/day gets close.
- **(b) Windowed listener + stats doc** — listen to `startedAt >= startOfWeek − 8 weeks` for the chart, and maintain `studyStats/{weekId}` client-side for all-time totals. More code, one more write path, and a derived document that can lag while offline.

Default: (a). Switch to (b) when the collection passes ~3k documents or cold-start reads approach half the daily quota.

---

## 11. Not in v1

`devices`, FCM tokens, `alarmEvents`, Cloud Functions, web push, presence, sharing, and any server-written field. The schema leaves room for all of them; none of them exist today.
