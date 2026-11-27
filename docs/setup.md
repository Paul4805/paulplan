# Setup (Windows, PowerShell)

Everything below is manual and one-time. Nothing here has been run yet on this machine: `node`, `pnpm`, `java`, `gradle`, and `adb` are not on PATH.

---

## 1. Toolchain

```powershell
# Developer Mode lets pnpm create symlinks without EPERM.
start ms-settings:developers

winget install --id Git.Git -e
winget install --id OpenJS.NodeJS.LTS -e
winget install --id EclipseAdoptium.Temurin.17.JDK -e
winget install --id Gradle.Gradle -e
winget install --id Google.AndroidStudio -e

# New terminal after this point, then check:
node -v          # v22.x or v24.x
git --version
java -version    # 17.x
gradle -v        # 8.x, used once to create the wrapper
```

```powershell
npm install -g pnpm@9 firebase-tools
Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
pnpm -v
firebase --version
```

`package.json` pins `pnpm@9.15.0`. If Corepack complains about that exact version, edit the `packageManager` field to the 9.x you have installed.

### Environment variables

```powershell
setx ANDROID_HOME "$env:LOCALAPPDATA\Android\Sdk"
setx JAVA_HOME ((Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Directory |
    Where-Object Name -like 'jdk-17*').FullName)

# New terminal again, then:
$env:Path += ";$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\cmdline-tools\latest\bin"
```

If Android Studio installed the SDK somewhere else, point `ANDROID_HOME` there instead.

### Android SDK packages

```powershell
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0" `
           "system-images;android-35;google_apis;x86_64" "emulator"

avdmanager create avd -n slot35 -k "system-images;android-35;google_apis;x86_64"
adb devices
```

A physical phone works too: enable Developer options, enable USB debugging, accept the RSA prompt, and confirm it appears in `adb devices`.

### Git hygiene

```powershell
git config --global core.longpaths true
```

---

## 2. Firebase

1. The project already exists as **planner-9b0a2** (`.firebaserc` points at it). No billing account.
2. **Authentication → Sign-in method**: enable **Google** and **Email/Password**.
3. **Project settings → Your apps → Web**: register an app, copy the config block into `apps/web/.env.local` (see `apps/web/.env.example`). The same values go into `apps/android/local.properties` (see step 3.5).
4. **Project settings → Your apps → Android**: register `com.slot.app`. Use the debug SHA-1 from `.\gradlew.bat signingReport` (also required for Google sign-in on Android).
5. **Hosting**: create the default site. `firebase.json` already points at `apps/web/out`.
6. **Firestore**: create the database in production mode once you are ready to deploy rules. The emulator does not need it.

```powershell
firebase login
firebase use --add      # select planner-9b0a2, alias "default"
```

---

## 3. First run

```powershell
# 3.1 Install JS dependencies. `prepare` runs `pnpm tokens`, which writes the
# generated CSS/TS/Kotlin token files into the two apps.
pnpm install

# 3.2 Web
Copy-Item apps\web\.env.example apps\web\.env.local
# paste the Firebase web config values into .env.local
pnpm dev                # http://localhost:3000
```

```powershell
# 3.3 Rules tests. Needs Java (the Firestore emulator is a JVM process).
pnpm test:rules

# 3.4 Full check
pnpm verify
```

```powershell
# 3.5 Android. The wrapper is committed; no `gradle wrapper` step needed.
cd apps\android
```

Before the first Android build, create `apps/android/local.properties` with the Firebase web app values
(the same ones as `.env.local`) plus the OAuth web client id:

```properties
sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
firebase.apiKey=...
firebase.applicationId=1:...:android:...
firebase.projectId=planner-9b0a2
firebase.storageBucket=planner-9b0a2.firebasestorage.app
firebase.senderId=...
google.webClientId=...apps.googleusercontent.com
```

`firebase.applicationId` is the Android app's `mobilesdk_app_id` from the Firebase console. `google.webClientId`
is the **Web** OAuth client id (Google Cloud → Credentials), used by Credential Manager. Leave it blank to
use email sign-in only. There is deliberately no `google-services.json` and no Google Services plugin:
`SlotApp` builds `FirebaseOptions` from these values at runtime.

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat signingReport   # debug SHA-1, needed for Google sign-in / Firebase console
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

```powershell
# 3.6 Deploy (optional, after pnpm build)
pnpm build
firebase deploy --only hosting,firestore:rules
```

---

## 4. Common failures

| Symptom | Cause | Fix |
| --- | --- | --- |
| `pnpm.ps1 cannot be loaded` | Execution policy | `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned` |
| `EPERM: operation not permitted, symlink` | No Developer Mode | Enable it, or set `node-linker=hoisted` in `.npmrc` |
| Gradle cannot find Java | `JAVA_HOME` unset or stale terminal | Set it, open a new terminal |
| `Design tokens are missing` during Gradle sync | `Tokens.kt` not generated | `pnpm tokens` at the repo root |
| Rules tests hang | Emulator could not start (no Java, or port 8080 busy) | `java -version`; `Get-NetTCPConnection -LocalPort 8080` |
| Firestore `permission-denied` in the browser | Rules deployed? Signed in? | Deploy rules; check the user id in the path |
| Next build fails on `next/font` | No network access | Connect once; fonts are cached afterwards |

---

## 5. What is intentionally absent

- No `google-services.json` and no Google Services Gradle plugin: the Android app builds `FirebaseOptions` from `local.properties` at runtime.
- The Gradle wrapper is committed, so `.\gradlew.bat` works after a clone; only the SDK packages still need installing.
- No CI pipeline: `pnpm verify` is the check. A GitHub Actions workflow is a small addition later if wanted.
- No Cloud Functions, no `firebase-functions` dependency, no Blaze plan. Nothing in the codebase calls a server.
- No PNG app icons. The web manifest and Android launcher use SVG/vector assets; add PNGs if a launcher rejects them.
