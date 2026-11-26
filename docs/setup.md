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

1. Create a project named **slot-app** on the **Spark** plan. No billing account.
2. **Authentication → Sign-in method**: enable **Google** and **Email/Password**.
3. **Project settings → Your apps → Web**: register an app, copy the config block into `apps/web/.env.local` (see `apps/web/.env.example`).
4. **Project settings → Your apps → Android**: register `com.slot.app`. The debug SHA-1 comes from `./gradlew signingReport` after step 3.5 below. (Not needed until the Android Firebase SDK lands in Phase 6, but registering now avoids a detour.)
5. **Hosting**: create the default site. `firebase.json` already points at `apps/web/out`.
6. **Firestore**: create the database in production mode once you are ready to deploy rules. The emulator does not need it.

```powershell
firebase login
firebase use --add      # select slot-app, alias "default"
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
# 3.5 Android. The wrapper JAR is a binary and is not committed, so create it once:
cd apps\android
gradle wrapper --gradle-version 8.9
.\gradlew.bat assembleDebug
.\gradlew.bat signingReport   # copy the debug SHA-1 into the Firebase Android app

# Install on a connected phone or running emulator:
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

- No `google-services.json` yet: the Android app does not include the Firebase SDK until Phase 6.
- No Gradle wrapper JAR: generated once (step 3.5) rather than committed as a binary.
- No CI pipeline: `pnpm verify` is the check. A GitHub Actions workflow is a small addition later if wanted.
- No Cloud Functions, no `firebase-functions` dependency, no Blaze plan. Nothing in the codebase calls a server.
