# Make it yours

PaulPlan is designed to be forked and re-pointed at your own Firebase project. This is the complete checklist. Most of it is configuration; renaming the Android package is optional and has a cost.

## 1. Fork and clone

```powershell
gh repo fork Paul4805/paulplan --clone
cd paulplan
pnpm install
```

## 2. Create your Firebase project

1. Firebase console → **Add project** → stay on the **Spark** plan (no card).
2. **Authentication → Sign-in method**: enable **Google** and **Email/Password**.
3. **Firestore Database** → create in production mode. Choose a region close to you; it cannot be changed later.
4. **Project settings → Your apps → Web**: register an app, copy the `firebaseConfig` block.
5. **Project settings → Your apps → Android**: register your package (see §6), using the SHA-1 from `.\gradlew.bat signingReport`.

## 3. Point the repo at it

Update `.firebaserc`:

```json
{ "projects": { "default": "your-project-id" } }
```

Web config — copy the example and fill it in:

```powershell
Copy-Item apps\web\.env.example apps\web\.env.local
```

Android config — copy the example and fill it in:

```powershell
Copy-Item apps\android\local.properties.example apps\android\local.properties
```

`local.properties` needs `sdk.dir`, the same `firebase.*` values as the web config, and `google.webClientId` (the **Web** OAuth client ID from Authentication → Google). Both files are gitignored.

## 4. Deploy rules and hosting

```powershell
firebase login
firebase use --add
pnpm build
firebase deploy --only firestore:rules,hosting
```

Rules are per-user, so an empty project is safe to open up. Deploy them **before** using the app; production mode denies everything by default.

## 5. Optional: Garmin sync

1. Garmin Connect → Settings → **Personal API** → create a key.
2. Build and install the Android app.
3. In the app: **Settings → Garmin** → paste the key.

The key stays on the phone and is never written to Firestore.

## 6. Rename the app

### Minimum (recommended): display name only

Change these strings, keep the package:

| File | What |
| --- | --- |
| `apps/web/app/layout.tsx` | `title`, `applicationName`, `appleWebApp.title` |
| `apps/web/app/login/page.tsx` | logo letter + wordmark |
| `apps/web/components/app-shell.tsx` | logo + sidebar wordmark |
| `apps/web/app/providers.tsx` | boot logo |
| `apps/web/public/manifest.webmanifest` | `name`, `short_name` |
| `apps/web/public/offline.html` | title, mark, copy |
| `apps/android/.../res/values/strings.xml` | `app_name` |
| `apps/android/.../MainActivity.kt` | sign-in logo/wordmark |
| `apps/android/.../ui/SettingsScreen.kt` | version footer |
| `README.md`, `docs/*` | branding |

The Android package stays `com.slot.app`. That is fine: the launcher shows `app_name`, and the package is just an identifier. Re-registering a new package also means a new Firebase Android app, a new SHA-1 and a fresh install.

### Full package rename (optional)

Only if `com.slot.app` bothers you:

1. Rename the Kotlin package and directory `app/src/main/java/com/slot/app` → `com/you/app`.
2. Update `namespace` and `applicationId` in `apps/android/app/build.gradle.kts`.
3. Update every `import com.slot.app.*` (IDE refactor).
4. Register the new package in Firebase and add the SHA-1 again.
5. Uninstall the old app from the phone; it is a separate application.

Firestore collections and data are untouched by either rename.

### Internal names you can leave alone

- `@slot/*` workspace package names
- `ClassSlot`, `SlotTheme`, `SlotViewModel` class names
- `slot-*` local storage keys (`slot-theme`, `slot-pomodoro`, …) — renaming them resets local preferences
- `com.slot.app`

## 7. Make it multi-user (if you ever need to)

Nothing prevents it today: every document is already under `users/{uid}`. But the Garmin sync assumes one personal API key per device, and the design targets a single person. Supporting many users would need the Garmin business API (OAuth per user, server) and Cloud Functions on Blaze.

## 8. Checklist before you publish your fork

- [ ] `.env.local` and `local.properties` are untracked (`git status` clean of them)
- [ ] `firestore.rules` deployed
- [ ] Google sign-in works on the web (authorized domain is automatic on `*.web.app`)
- [ ] Android APK signed with your debug key; SHA-1 registered in Firebase
- [ ] No personal data in the repo (screenshots, exports, API keys)
- [ ] Your own `LICENSE` copyright line
