# Build status

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
