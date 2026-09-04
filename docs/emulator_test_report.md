# GEO emulator verification

Date: 2026-09-04

## Device / API

- SDK: `C:\Users\SUN\AppData\Local\Android\Sdk` (`local.properties`)
- AVD: `Study_Android_36` (x86_64, Pixel 6 image `android-36/google_apis/x86_64`)
- `adb devices`: empty after boot attempts
- API / model: **not obtained** (`sys.boot_completed` never became `1`)

## Commands

```
adb devices -l
emulator -list-avds
emulator -accel-check
Start-Process emulator.exe -ArgumentList -avd Study_Android_36 ... -WindowStyle Hidden
# retry: -accel off -gpu swiftshader_indirect -no-window
adb shell getprop sys.boot_completed   # polled
GRADLE_USER_HOME=C:\Users\SUN\Desktop\GEO\.gradle-user-home
.\gradlew.bat --no-daemon connectedDebugAndroidTest
```

No wipe/reset. No APK install (no device).

## Emulator start

1. Default start: process exits. Log: `x86_64 emulation currently requires hardware acceleration!` / `Android Emulator hypervisor driver is not installed`.
2. `-accel off -gpu swiftshader_indirect`: qemu ACCESS_VIOLATION (`exit -1073741819`).
3. Same plus `-no-window`: `emulator-5554 offline` ~6 min; `qemu-system-x86_64-headless` CPU ~0.67s then **EMU_DEAD**. Never `device` / never `boot_completed=1`.

`emulator -accel-check` → code 6, AEHD not installed. `aehd`/`gvm` services missing.

## connectedDebugAndroidTest

- Exit: **1**
- `BUILD FAILED in 16s`
- `:app:connectedDebugAndroidTest` → `DeviceException: No connected devices!`
- **Tests run: 0** (12 `@Test` methods exist in androidTest; none executed)

## UI evidence

Not captured. Missing:

- `docs/screenshots/` Home PNG
- `docs/runtime/` uiautomator XML / focused logcat / dumpsys

GEO title, Chinese date, Home empty/real, bottom nav, Add, Settings: **unverified**.

## Unverified (blocked)

Install `-r` debug APK; launch `com.geo.ledger/.MainActivity`; logcat crash scan; all UI flows; live Room migrations on device.

To unblock: install Android Emulator hypervisor driver (AEHD) or enable Windows hypervisor, then boot `Study_Android_36` with hardware accel (no wipe).
