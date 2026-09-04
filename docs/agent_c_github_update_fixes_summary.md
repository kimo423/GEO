I'll read the update review findings and current fetch/URL/dialog code, then tighten allowlisting, redirects, UI, and tests.I'll replace prefix URL checks with a single canonical allowlist, then tighten redirects, dialog scrolling, and tests.**BUILD SUCCESSFUL** (`testDebugUnitTest lintDebug assembleDebug`). JVM **81 tests, 0 failures**. Lint **0 errors, 10 warnings**.

**Closed**
- **UA-UPD-01:** not a defect. Notes stay non-blank + 2000-char cap; language is publisher choice (`docs/github_update.md`).
- **Fetch High:** exact canonical HTTPS allowlist. Rejects `.`/`..`/`//`/`%` encoding, userInfo, query, fragment, non-443 ports, `GEOevil`/`GEO.git` prefixes.
- **Fetch Medium:** GET/redirects only `…/main/version.json` and `…/refs/heads/main/version.json`. One URI normalizer + `URL` agreement. Relative `Location` via `URI.resolve`. Rejects HTTP, `//`, backslash. Max **2** redirects.
- **APK Low:** host `github.com` only; latest or tagged `/kimo423/GEO/releases/…/GEO.apk`; no www/query/port/userInfo/fragment.
- **UI Low:** notes in a 280.dp vertical scroll; actions stay outside.
- **UA-UPD-02:** `AppUpdateLaunch` swallows `RuntimeException` (ANFE/SecurityException); does not catch `Error`.
- **UA-UPD-03:** README, `build_status.md`, `delivery_manifest.md` now **1.1.0 (2)** and INTERNET-only update.

**Deferred:** none from those reviews.

**Live/device:** no GitHub Release/`GEO.apk` published; real fetch, TLS, 302, `ACTION_VIEW`, no-browser devices still unverified.

Details: `docs/agent_c_github_update_fixes.md`. No git push/release.
