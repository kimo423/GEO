I'll re-read the current update policy, HTTP layer, dialog/launch tests, and the fixes doc to verify the prior findings.# GitHub update — security closure recheck

## Overall
Prior High, two Medium, and two Low items are **closed in code**. Fetch URLs are an exact two-entry set; redirects are re-canonicalized into that set; APK URLs are `github.com` path-exact; notes scroll; launch validates then swallows `RuntimeException`. **No remaining code-proven allowlist/dialog/launch regressions.** Counts: **Critical 0, High 0, Medium 0, Low 0**.

## Closure table

| Prior | Status | Evidence |
|---|---|---|
| **High** fetch prefix + `..` | **Closed** | `isAllowedFetchUrl` = `canonicalizeHttps` then `canonical in {MANIFEST_URL, MANIFEST_URL_REFS}`. `isSafeAbsolutePath` rejects `.` / `..` / `//` / `%`. Tests: `GEOevil`, `GEO.git`, `../`, `%2e%2e`, `//`, `./`. |
| **Medium** unpinned fetch path | **Closed** | Only `.../main/version.json` and `.../refs/heads/main/version.json`. Issues/blob paths `assertFalse`. `MAX_REDIRECTS = 2`. |
| **Medium** `URL` vs `URI` / HTTP Location | **Closed** | `resolveFetchRedirect` rejects `http://`, `HTTP://`, `//`, `\`. Relative via `URI.resolve`; result must canonicalize + `urlAgreesWithUri`. `getHttps` never opens a URL that failed that path. |
| **Low** APK query / `www` / port | **Closed** | Host must be `github.com`; no query/userInfo/fragment; port only default/443; path latest asset or tagged `[A-Za-z0-9][A-Za-z0-9._-]{0,63}`. Tests: `?token`, `:8443`, `www`, `user@`, `#frag`. |
| **Low** long notes UI | **Closed** | `heightIn(max = 280.dp)` + `verticalScroll`; buttons outside. `longNotesWithinCapParseAndStayOnState`. |
| Launch (UA-UPD-02) | **Closed** | `AppUpdateLaunch.open` → `isAllowedApkUrl` then `startActivity`; catch `RuntimeException` only. Evil URL does not run lambda. |

## Remaining issues
None in the scoped allowlists, redirect resolver, dialog notes, or launch wrapper.

`getHttps` still has no socket-level mock (timeouts/TLS/real 302). That is coverage, not a policy bypass: every hop uses `canonicalizeHttps` + `resolveFetchRedirect`.

## Build evidence
- `docs/agent_c_github_update_fixes.md`: `testDebugUnitTest lintDebug assembleDebug` **BUILD SUCCESSFUL**.
- JVM: `app/build/reports/tests/testDebugUnitTest/index.html` **81 tests, 0 failures**. `TEST-com.geo.ledger.update.AppUpdateTest.xml`: **12 / 0 fail** (2026-09-04T11:23:48Z), including allowlist, redirect, long notes, launch.
- Lint: `lint-results-debug.txt` **10× Warning**, **0 Error** (GradleDependency ×7, ConfigurationScreenWidthHeight, ObsoleteSdkInt, KaptUsageInsteadOfKsp).
- APK: `app/build/outputs/apk/debug/output-metadata.json` **versionCode 2 / 1.1.0**.

## Unpublished / live-device
No GitHub Release/`GEO.apk` in this review. Unverified: live GET/302/TLS, device `ACTION_VIEW`, no-browser, dialog vs editor back/IME. JVM allowlist closure does not prove those surfaces.
