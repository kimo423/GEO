I'll check devices and acceleration again, start Study_Android_36 if needed, then run connected tests and capture Home evidence.**Blocked: no booted device.** `Study_Android_36` is the only AVD. Hardware accel is missing (`emulator -accel-check` = 6, AEHD not installed). Default start exits with “x86_64 emulation currently requires hardware acceleration.” `-accel off` either ACCESS_VIOLATIONs or leaves `emulator-5554` **offline** until qemu dies. `sys.boot_completed` never became `1`. AVD was not wiped.

**connectedDebugAndroidTest:** `GRADLE_USER_HOME=C:\Users\SUN\Desktop\GEO\.gradle-user-home` → **BUILD FAILED in 16s**, exit **1**, `DeviceException: No connected devices!` **0 tests run** (12 instrumented methods on disk). No app/test code change (not a reproducible test failure).

**Not done (no adb device):** `adb install -r` APK, launch `com.geo.ledger/.MainActivity`, logcat/dumpsys, screenshot, uiautomator dump.

**Unverified UI:** GEO title, Chinese date, Home empty/real, bottom nav, Add, Settings.

Reports: `docs/emulator_test_report.md`, `docs/manual_test_checklist.md`. Unblock by installing AEHD/Windows hypervisor, then boot this AVD with acceleration.
