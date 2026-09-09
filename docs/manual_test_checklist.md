# GEO manual / emulator checklist

## GEO 1.2 device gate (2026-09-09 — NOT RUN)

No connected device. Do not uninstall/clear the user's ledger or run destructive
fixtures against production data. Verify package/signing, back up, use isolated data.

- [ ] In-place 1.1.3→1.2.0 migration and unchanged 24-hour seconds.
- [ ] 0/1/10/11 attachments; exact 10MiB/30MiB and +1 byte; repeated taps.
- [ ] Delete original, restart, preview/open/save private and historical attachments.
- [ ] A,B→A,C, consecutive edits, unchanged save, delete twice, before/after one event.
- [ ] Both import/export formats and modes; bad archives leave local state unchanged.
- [ ] Process death during copy/draft/before and after commit; no live blob loss.
- [ ] Small screen/font scale/keyboard/TalkBack, save/delete feedback.
- [ ] Frame timings during rapid Home/Bills/Settings changes; screenshot is not proof.
- [ ] Manual update offline/errors/latest/new version/browser handoff.

## Historical checklist (not 1.2 acceptance evidence)

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
