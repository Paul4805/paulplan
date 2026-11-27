# PaulPlan

A minimal college schedule manager. Weekly timetable, tasks, study progress, and reminders — on the web and on Android, sharing one account and one dataset in real time.

**Status:** v1 is implemented and verified end to end against the live Firebase project **planner-9b0a2**: Google sign-in, Firestore reads and writes under the deployed rules, and an Android APK built with the same config. The only untested surface is Android runtime alarm behaviour on a physical phone.

## Verification

| Check | Command | Result |
| --- | --- | --- |
| Design tokens generate | `pnpm tokens` | 3 artefacts, web + Kotlin |
| Web unit tests (time, alarms, Strava CSV, Garmin JSON) | `pnpm test` | 18 passed |
| Web typecheck + lint | `pnpm typecheck`, `pnpm lint` | clean |
| Web static export | `pnpm build` | 11 routes exported |
| Firestore rules | `pnpm test:rules` | 23 passed against the emulator |
| Android compile | `.\gradlew.bat assembleDebug` | `app-debug.apk`, 14.7 MB |
| Android lint | `.\gradlew.bat :app:lintDebug` | 0 errors, 44 warnings (version/deprecation noise) |

`pnpm verify` runs tokens, web tests, typecheck, lint, and the emulator rules tests in one command.

---

## Scope (v1)

| Area | Included |
| --- | --- |
| Timetable | Weekly recurring classes (subject, room, teacher, day, start/end, color), day + week views |
| Tasks | Deadline, linked subject, priority, status (todo/doing/done), list + kanban |
| Study | Per-subject progress, session logging, weekly hours chart, Pomodoro timer |
| Training | Runs, football, gym and other sessions; automatic Garmin sync via Personal API key on the phone; export import fallback |
| Stats | Academic and training totals, 14-day trends, per-subject and per-type breakdowns |
| Reminders | In-page reminders on web; exact local alarms on Android |
| Auth | Google + email/password, one account across both clients |
| Sync | Firestore realtime + offline persistence on both clients |

Phase 2 (after core works): attendance tracker, exam countdown, GPA calculator, Android home-screen widget, CSV export, focus mode.

## Hard constraints

These are deliberate, not placeholders. Changing one changes the architecture.

- **Firebase Spark only.** No Cloud Functions, no Blaze, no billing card.
- **No FCM in v1.** Without a server there is no sender. Web push is out; Android alarms are local.
- **Android is sideload-only.** No Play Console, no store listing, no release-signing ceremony. Debug builds via `adb install`.
- **Web is a static export.** Firebase Hosting on Spark cannot run Next SSR. Every page is client-rendered; data comes from the Firestore client SDK.
- **Firestore is the only source of truth.** Local state is view state only. There is no custom offline queue — the Firestore SDK's cache *is* the queue.
- **Security rules are mandatory.** The web build exposes the Firebase client config publicly; rules are the only access control. Rules tests run in the emulator.
- **One accent color:** `#6366F1`. Neutral canvas, 1px hairline borders, 12–16px radii, no gradients.
- **Timestamps:** absolute moments in UTC; recurring weekly slots in local wall-clock (see below). Rendered in the device timezone.

## Architecture

```
   Next.js static PWA ──┐                        ┌── Android (Kotlin + Compose)
   (Firebase Hosting)   │                        │   (sideloaded debug APK)
                        ├──▶ Firestore ◀─────────┤
                        │    realtime + offline  │
                        │    persistence         │
                        └──▶ Firebase Auth       │
                                                 └── AlarmManager (local exact alarms)
```

No Cloud Functions. No server-side rendering. No push messaging. Both clients talk to Firestore directly under per-user security rules.

## Time model (the two-clock rule)

Two different kinds of time, stored differently on purpose:

- **Absolute moments** — task deadlines, alarm fire times, study session start/end. Stored as Firestore `Timestamp` (UTC epoch), rendered in local time.
- **Recurring wall-clock** — weekly class slots. Stored as `dayOfWeek` (1 = Monday … 7 = Sunday), `startMinutes` / `endMinutes` (minutes from local midnight), and an IANA `timezone` on the user. A 09:00 Monday class stays 09:00 across DST changes and travel.

Android converts slots to absolute instants locally when arming alarms; that conversion is the only place the two clocks meet.

## Data model

All data lives under the signed-in user. Rules grant access only when `request.auth.uid == uid`.

```
users/{uid}/meta/profile          settings, timezone, schemaVersion
users/{uid}/subjects/{id}         name, code, color, teacher, room, target hours
users/{uid}/classes/{id}          timetable slots (dayOfWeek + minutes)
users/{uid}/terms/{id}            optional: term start/end, week parity
users/{uid}/tasks/{id}            dueAt (UTC), priority, status, sortKey
users/{uid}/studySessions/{id}    startedAt, endedAt, durationMinutes, source
users/{uid}/alarms/{id}           alarm definitions (recurrence, lead time, exact flag)
users/{uid}/alarmState/{eventId}  sparse user actions: snoozed | dismissed
```

Notes:

- Per-subject progress and weekly study hours are **derived** from tasks and sessions, never stored. Aggregates stored client-side go stale while offline.
- `alarmState` documents are created only when a user acts on a fired alarm: `eventId = alarmId_fireAtEpochMin`. They are client-writable and cleaned up on launch after 30 days.
- Kanban order uses a numeric `order` with midpoint inserts, so reordering never needs a transaction.
- Conflict resolution is Firestore's native last-write-wins per field, with `updatedAt: serverTimestamp()` on every document for auditability. Multi-field edits are a single atomic `update()`. No read-before-write, so offline writes keep working.

## Phase plan

| # | Milestone | Done when |
| --- | --- | --- |
| 0 | Foundations | `pnpm dev` serves a themed placeholder; `assembleDebug` builds; a rule test proves cross-user reads are denied |
| 1 | Web shell + auth | Google + email sign-in works in two browsers, session persists |
| 2 | Timetable + subjects (web) | Edit in one window, other updates live; offline edit flushes on reconnect |
| 3 | Tasks + kanban (web) | Offline create and reorder survive reconnect with no duplicates |
| 4 | Study + progress + Pomodoro (web) | Timer survives reload; chart matches summed sessions exactly |
| 5 | Web v1 complete + offline gate | Airplane-mode walkthrough of every screen; PWA installs; rules tests green |
| 6 | Android: data + UI | Same account, identical live data, edits round-trip both ways |
| 7 | Android: alarms | Fires with app killed, after reboot, in Doze, after timezone change; snooze/dismiss reaches web |
| 8 | Polish + phase 2 extras | Widget, attendance, exam countdown, GPA, CSV export |

Phase 5 is a hard gate: sync and offline semantics are cheap to prove on the web and expensive to debug across two platforms.

Phases 0–7 are implemented in this repository. Phase 8 (widget, attendance, exam countdown, GPA, CSV export) is deliberately not started.

Known v1 deviations from the original outline, all for static-export or scope reasons:

- Subject detail is `/subjects/?id=…`, not `/subjects/[id]`, because a static export cannot pre-render unknown ids. Deep links still work.
- The web kanban board is drag-and-drop; Android v1 uses a list with tap-to-cycle status and long-press to delete. Both write the same `status` and `order` fields.
- Web reminders appear in an open tab only. Android alarms are the reliable path.
- Android reads its Firebase config from `local.properties` via `BuildConfig` instead of `google-services.json`, so the repo builds without a generated credential file.

## Repository layout

```
apps/web/            Next.js 14 (App Router, static export)
apps/android/        Kotlin + Jetpack Compose, Gradle
packages/design-tokens/   tokens.json -> CSS variables, Tailwind scale, Tokens.kt
packages/shared/     zod schemas, enums, date helpers, Firestore types (Phase 1)
packages/rules-tests/ Firestore rules tests against the emulator
docs/schema.md       language-neutral data contract (mirrored by hand in Kotlin)
docs/setup.md        Windows toolchain, Firebase console, first run
firestore.rules      access control (tested)
firebase.json        Hosting + emulator config
```

Tokens are generated, not hand-copied, so web and Android cannot drift. The data contract is a document plus zod schemas because Kotlin cannot share TypeScript; an emulator test catches contract drift.

## Local prerequisites

Node 20+ (LTS), pnpm 9, JDK 17, Android Studio with SDK 35 (min SDK 26), Firebase CLI, Git. Exact install commands are in [`docs/setup.md`](docs/setup.md).

```powershell
npm install -g pnpm@9 firebase-tools
firebase login
firebase use --add        # select the planner-9b0a2 Firebase project
```

Configuration lives in `apps/web/.env.local` (never committed):

```
NEXT_PUBLIC_FIREBASE_API_KEY=
NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN=
NEXT_PUBLIC_FIREBASE_PROJECT_ID=
NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET=
NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID=
NEXT_PUBLIC_FIREBASE_APP_ID=
```

These values are public by design. Security rules are what protect the data.

## Commands

| Command | Purpose |
| --- | --- |
| `pnpm install` | Install deps; regenerates design tokens via `prepare` |
| `pnpm dev` | Run the web app |
| `pnpm build` | Static export to `apps/web/out` |
| `pnpm tokens` | Regenerate tokens for web + Android |
| `pnpm test:rules` | Firestore rules tests in the emulator |
| `pnpm verify` | Tokens + typecheck + lint + rules tests |
| `cd apps/android && ./gradlew.bat assembleDebug` | Build the APK |
| `adb install -r apps/android/app/build/outputs/apk/debug/app-debug.apk` | Sideload to a phone |

Generated files (`tokens.generated.css`, `tokens.generated.ts`, `Tokens.kt`) are gitignored; `pnpm tokens` recreates them from `packages/design-tokens/tokens.json`.

## Principles

1. Boring, proven choices. No clever sync engines.
2. One write path per collection. Optimistic UI comes free from the Firestore cache.
3. Every screen has an empty state, a loading skeleton, an error toast, and optimistic mutations.
4. Honest UI: web says "Reminders fire on your phone; web shows them when open." It does not pretend to be an alarm clock.
5. Generous whitespace, one accent color, 150–200ms ease-out motion. No gradients, no emoji UI.
