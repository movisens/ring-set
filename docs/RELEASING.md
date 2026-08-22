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

## Signing: the release keystore

Release signing is wired in [`app/build.gradle.kts`](../app/build.gradle.kts) via a gitignored
`keystore.properties` at the repo root:

```
storeFile=keystore.jks
storePassword=<password>
keyAlias=halo
keyPassword=<password>
```

`keystore.jks` (also gitignored) is the actual signing key — RSA 2048, ~27-year validity, cert
SHA-256 `644de329…`. **Without these two files a release build comes out UNSIGNED.** `.gitignore`
covers `keystore.properties`, `*.jks`, `*.keystore`.

### ⚠️ Back it up — losing it is unrecoverable

The keystore is **not in git** and cannot be regenerated to match. Lose it and you can never publish
an update that upgrades an installed copy in place. Keep **at least two** independent copies:

- Flash drive: `G:\android-keystores\ring-set\` (both files) — for carrying between stations.
- Plus one more (cloud / password manager). The `.properties` holds the password in **plaintext**,
  so ideally store it apart from the `.jks`.

## Backing up your data (no data loss on reinstall)

Because the first release build is signed with a new key, installing it requires an uninstall, which
wipes the on-device Room database. Halo now has a **full backup/restore** for exactly this:

- **Data tab → Backup & restore → Back up** writes a single JSON file (heart rate, steps, sleep,
  workouts, known rings, and your profile) via the system file picker — save it to Downloads/Drive.
- After reinstalling, **Data tab → Backup & restore → Restore** loads it back (replaces current
  data). This is the only lossless path across an uninstall.

(`pull-data.ps1` only pulls shareable CSV copies and cannot be re-imported — use Backup for restores.)

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

## First release install (one-time, from a debug-signed build)

```
# 1. In the app: Data -> Backup & restore -> Back up, save the JSON OUTSIDE the app.
adb uninstall com.krejci.halo
adb install app/build/outputs/apk/release/app-release.apk
# 2. In the app: Data -> Backup & restore -> Restore, pick the JSON.
```

Verify: `adb shell dumpsys package com.krejci.halo | findstr version`.

## Current state (2026-08-23)

- Release keystore created + backed up to the flash drive (needs a second backup copy).
- **v2.2** release APK built & signed (`app-release.apk`, cert `644de329…`) with the new
  backup/restore feature. Not yet installed on the phone (was disconnected) and no GitHub Release
  published yet — both pending.
