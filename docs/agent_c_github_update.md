# Agent C — GitHub update check

## Design

- Cold start: `AppUpdateViewModel` (Activity-scoped) fetches once on `Dispatchers.IO` via `HttpURLConnection` (`no-cache`, 8s timeouts, max 8KB, no unsafe redirects).
- Local `BuildConfig.VERSION_CODE` vs remote integer `versionCode`. Name is display-only.
- Newer remote → one M3 `AlertDialog` over `GeoApp`: notes + 「稍后更新」/「立即更新」. Later dismisses for the process. Now opens validated HTTPS GitHub Release APK URL (`ACTION_VIEW`); missing handler is swallowed.
- Failures (except `CancellationException`) are silent. Duplicate `checkOnce` is a no-op (`AtomicBoolean`).
- Manifest: only `INTERNET` (plus `queries` for HTTPS VIEW). Backup still off; DB still excluded.

## Changed files

- `app/build.gradle.kts` versionCode **2**, versionName **1.1.0**
- `version.json` (repo root)
- `app/src/main/AndroidManifest.xml`
- `app/src/main/res/values/strings.xml`
- `update/AppUpdate.kt`, `AppUpdateHttp.kt`, `AppUpdateViewModel.kt`
- `ui/update/AppUpdateDialog.kt`, `ui/navigation/GeoApp.kt`
- `BackupAndPermissionManifestTest.kt`, `update/AppUpdateTest.kt`
- `docs/github_update.md`, this file

## Build

`GRADLE_USER_HOME=C:\Users\SUN\Desktop\GEO\.gradle-user-home`
`.\gradlew.bat --no-daemon testDebugUnitTest lintDebug assembleDebug`

**BUILD SUCCESSFUL in 1m 17s**, exit **0**. JVM **77 tests, 0 failures** (includes `AppUpdateTest` 8). Lint **0 errors**, 11 warnings (prior 10 + UseKtx on `Uri.parse`).

## Unverified on device

Real GitHub fetch, browser handoff, no-browser device, dialog over IME/nav, process death vs session dismiss.
