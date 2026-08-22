# Releasing & the in-app updater

How Halo is signed, built, installed, and how the in-app updater ships new versions. Sibling
Kotlin apps (workout-tracker) use the same pattern; the .NET apps use the equivalent in `dotnetlib`
(`DotNetLib.Core/Updating`).

## How the in-app updater works

Settings (**You** tab) → **Updates** → **Check for updates** calls
[`AppUpdater`](../app/src/main/java/com/krejci/halo/update/AppUpdater.kt), which:

1. GETs `https://api.github.com/repos/lukr-99/ring-set/releases/latest` (public API, no auth).
2. Parses the release `tag_name` and the first asset whose name ends in `.apk`.
3. Compares the tag (minus a leading `v`) against the installed `versionName` (read from
   `PackageManager`) with a dotted-numeric comparison.
4. If newer: downloads the APK into `cacheDir/updates/`, then launches the system package installer
   via a `FileProvider` content URI (`${applicationId}.fileprovider`, path `updates/`).

No third-party dependencies — `HttpURLConnection` + `org.json` + coroutines. Requires the
`INTERNET` and `REQUEST_INSTALL_PACKAGES` permissions (both declared in the manifest).

**The critical constraint — signatures must match.** Android only lets an APK upgrade an installed
app *in place* when both are signed with the **same key**. So:

- The first release build must be **installed manually**, replacing whatever was there (an
  **uninstall** — data is wiped, so export/pull first via `pull-data.ps1`).
- Every subsequent GitHub release APK must be signed with that **same release keystore**, or the
  updater's download fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.

## Signing: ring-set has NO release keystore yet ⚠️

Right now `app/build.gradle.kts` has **no signing config** — `build-and-install.ps1` installs a
**debug-signed** APK. That's fine for development, but the updater cannot self-update a debug-signed
install from release APKs. Before distributing, add a release keystore exactly like
workout-tracker's (see its `docs/RELEASING.md`):

1. Create the key (run once, then **back it up** — losing it is unrecoverable):

   ```
   keytool -genkeypair -v -keystore keystore.jks -alias halo \
     -keyalg RSA -keysize 2048 -validity 10000 \
     -storepass <pw> -keypass <pw> -dname "CN=lukr-99, O=lukr-99, C=CZ"
   ```

2. Add a gitignored `keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`)
   and a `signingConfigs { release { … } }` + `buildTypes.release.signingConfig` block that reads it
   (mirror workout-tracker's `app/build.gradle.kts`). Add `keystore.properties`, `*.jks`, `*.keystore`
   to `.gitignore`.
3. Keep **two** backups of `keystore.jks` + `keystore.properties`: a flash drive
   (`G:\android-keystores\ring-set\`) **and** one more (cloud / password manager). The `.properties`
   holds the password in plaintext, so ideally store it apart from the `.jks`.

## Build & install

**Dev / debug** (current default):

```
./build-and-install.ps1     # assembleDebug + adb install -r
```

**Release** (once signing is set up):

```
./gradlew.bat :app:assembleRelease
# -> app/build/outputs/apk/release/app-release.apk
```

First release install (or any signature change) needs a clean install:

```
./pull-data.ps1                     # back up on-device data first
adb uninstall com.krejci.halo
adb install app/build/outputs/apk/release/app-release.apk
```

Verify: `adb shell dumpsys package com.krejci.halo | findstr version`.

SDK note: this repo has no `sdk.dir` in `local.properties`, so it builds against `ANDROID_HOME`
(this station: `F:/DevTools/Android/Sdk`, which has build-tools 35).

## Publishing a release the updater will find

1. Bump `versionCode` + `versionName` in `app/build.gradle.kts`.
2. Build the signed release APK.
3. Create a **GitHub Release** (not just a tag) tagged `v<versionName>` and **attach the APK**.
4. Ensure the repo's Releases are **public**.

The next time an installed copy taps **Check for updates**, it finds the release and updates in place.

## Current state (2026-08-22)

- Installed on the phone: **v2.1** (debug-signed). Updater UI verified live (renders, check runs,
  reports "You're on the latest version.").
- **No release keystore yet** and no GitHub Release published — both needed before the updater can
  actually pull a new build.
