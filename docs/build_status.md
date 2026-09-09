# GEO 1.2 build status — 2026-09-09

Publication update: user explicitly authorized skipping device acceptance and
publishing. The following is debug-build evidence; Release validation/signing and
final upload evidence are recorded separately in github_publish_report_v1.2.0.md.

Current source: com.geo.ledger 1.2.0 / code 6, Room 4.
Final full run: **BUILD SUCCESSFUL in 34s, exit 0**, 127 tests, 0 failed/errors/skipped,
lint 0 errors / 12 warnings; assembleDebug and compileDebugAndroidTestKotlin passed.
Log: `docs/upgrade_final_validation.log`. All 29 JUnit suites were read after completion.

APK: `dist/GEO-debug.apk` copied from the last successful debug build.
SHA256: `712C7192258F1C6D1DCF110E4FE68C7829D6B649847CF1FDBF5E7A2C811F0CD9`.
Signer SHA256: `cdd46c1132f21ffa1149ec920424fcfb9bb2d633674b44843e36311d292ec732`,
same as prior dist/GEO.apk. Name GEO, min26/target36; not installed or published.
`dist/GEO.apk` and old ZIPs remain historical; use **GEO-debug.apk** for this upgrade.

```powershell
.\gradlew.bat --gradle-user-home .gradle-user-home --console=plain testDebugUnitTest lintDebug assembleDebug compileDebugAndroidTestKotlin
```

`adb devices -l` today shows no device. No installation, data clear, uninstall or
connected test was performed. Host Room/Compose tests are Robolectric, not phone
migration/UI/smoothness acceptance. No commit or push this upgrade.

## Archived 1.1 build evidence (NOT evidence for 1.2)

Everything below is a historical September 4 snapshot, including remote status.
The repository is no longer empty (September 6 read-only check returned v1.1.3).

Last recorded local build (2026-09-04, update-hardening assemble):

```
GRADLE_USER_HOME=C:\Users\SUN\Desktop\GEO\.gradle-user-home
.\gradlew.bat --no-daemon testDebugUnitTest lintDebug assembleDebug
```

**BUILD SUCCESSFUL in 1m 4s**, exit **0**.

| Task | Result |
|---|---|
| `testDebugUnitTest` | **81 tests, 0 failures, 0 errors** |
| `lintDebug` | **0 errors, 10 warnings** |
| `assembleDebug` | `app\build\outputs\apk\debug\app-debug.apk` (debug-signed) |
| Packaged copies | `dist\GEO.apk` (Release asset name) and `dist\GEO-debug.apk` |
| `connectedDebugAndroidTest` | **Failed**: `DeviceException: No connected devices!` — **0 tests run**. Not a pass. |

APK identity (`aapt dump badging`):

- package `com.geo.ledger`
- versionCode **2**, versionName **1.1.0**
- minSdk 26, target/compileSdk 36
- application-label `GEO`
- source permission: `INTERNET` only (GitHub update check)
- **debug-signed**; later upgrades must use the **same signing certificate**. No release keystore was created.

GitHub update reviewer A/B: **PASS, zero findings**. Remote `https://github.com/kimo423/GEO` is currently **empty/unpublished**; live/device update flow **unverified**. Emulator `Study_Android_36` remains blocked (no AEHD).
