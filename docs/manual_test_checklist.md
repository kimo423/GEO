# GEO manual / emulator checklist

Status: **JVM + lint + debug APK packaged.** Device UI **blocked** (no accelerated `Study_Android_36`).

| Item | Status | Evidence |
|---|---|---|
| JVM unit tests | pass | 69 tests, 0 failures |
| lintDebug | 0 errors | 10 warnings listed in `known_issues.md` |
| assembleDebug | pass | `dist\GEO-debug.apk` |
| `connectedDebugAndroidTest` | **fail / 0 tests** | `No connected devices!` — **not passed** |
| AVD boot `sys.boot_completed=1` | blocked | AEHD missing |
| Install APK + launch MainActivity | unverified on device | |
| Home GEO title + Chinese date | unverified on device | |
| Home loading then real/empty ledger | unverified on device | |
| Bottom nav 首页 / 账单 / 设置 | unverified on device | |
| Add / edit / historical chips | unverified on device | |
| Custom inverted range UI | unverified on device | JVM covers SavedState |
| Settings add/rename/soft-delete | unverified on device | |
| Adaptive icon on launcher | unverified on device | |

Round-3 Grok implementation review + bug hunt: **PASS, zero findings**.
