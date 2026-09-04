I'll recheck the update sources, the fixes doc, tests, and artifacts against UA-UPD-02/03 and look for regressions.# GitHub update closure recheck (Reviewer A)

**Overall: PASS**  
**Critical: 0 · Major: 0 · Minor: 0**

Read-only recheck of current update sources, `docs/agent_c_github_update_fixes.md`, tests, and Gradle artifacts. No edits.

## Closure table

| ID | Status | Proof |
|---|---|---|
| **UA-UPD-01** | **Closed (adjudicated)** | Not a client defect. `AppUpdatePolicy.parseAndValidate` still requires non-empty notes and `MAX_NOTES` (2000). `docs/github_update.md`: language is publisher policy; client does not require CJK. |
| **UA-UPD-02** | **Closed** | `AppUpdateLaunch.open` (`AppUpdateLaunch.kt`) re-checks `isAllowedApkUrl`, then `catch (_: RuntimeException)` (covers ANFE/`SecurityException`), does not catch `Error`. UI uses `url.toUri()` + `ACTION_VIEW`. Test `launchSwallowsRuntimeFailuresAfterUrlCheck`: evil URL skipped; `SecurityException`/`RuntimeException` swallowed; success path runs once. |
| **UA-UPD-03** | **Closed** | `README.md` table **1.1.0 / 2**; Privacy: only source permission **INTERNET**. `docs/delivery_manifest.md` APK **1.1.0 (2)**, INTERNET-only. `docs/build_status.md` versionCode **2** / **1.1.0**, INTERNET-only. Gradle `versionCode = 2`, `versionName = "1.1.0"`; debug `output-metadata.json` matches. |
| Fetch High / Medium | **Closed** | Exact allowlist: `MANIFEST_URL` and `MANIFEST_URL_REFS` only. Canonicalizer rejects encoding/`.`/`..`/`//`/query/port/www/userInfo/fragment. `resolveFetchRedirect` rejects HTTP, `//`, `\`, sibling paths. `MAX_REDIRECTS = 2`. Tests: `fetchUrlIsExactAllowlist`, `redirectResolutionIsPinnedAndSafe`. |
| APK Low | **Closed** | Host `github.com` only; latest or tagged `GEO.apk`. Tests: `apkUrlMustBeExactGithubReleaseAsset`. |
| UI Low | **Closed** | Notes: `heightIn(max = 280.dp)` + `verticalScroll`; buttons remain `TextButton`s outside the scroll. Test: `longNotesWithinCapParseAndStayOnState`. |

Core contract still holds: `BuildConfig.VERSION_CODE` at `AppUpdateGate`; IO fetch of the pinned raw URL; `shouldPrompt` is integer `>`; one M3 dialog (稍后更新 / 立即更新); silent network/parse/launch failures; `checkStarted` CAS; overlay-only in `GeoApp`; Manifest INTERNET + HTTPS VIEW `queries`; `allowBackup="false"`.

## Remaining code-proven issues

**None.** No fix regressions found in policy, HTTP loop, launch wrapper, or dialog.

## Exact test / lint / assemble evidence (on disk)

- **`testDebugUnitTest`:** `app/build/reports/tests/testDebugUnitTest/index.html` — **81 tests, 0 failures, 0 ignored** (duration 0.610s).  
  `TEST-com.geo.ledger.update.AppUpdateTest.xml` (2026-09-04T11:23:48Z): **12/12 pass**, including new allowlist, redirect, long-notes, and launch tests.
- **`lintDebug`:** `app/build/reports/lint-results-debug.txt` — **0 errors, 10 warnings** (GradleDependency / ConfigurationScreenWidthHeight / ObsoleteSdkInt / KaptUsageInsteadOfKsp). **No UseKtx** on the update UI (KTX `toUri` applied).
- **`assembleDebug`:** `app/build/outputs/apk/debug/output-metadata.json` — `com.geo.ledger` **versionCode 2, versionName 1.1.0**. Fixes doc: `testDebugUnitTest lintDebug assembleDebug` **BUILD SUCCESSFUL**. This recheck did not re-run Gradle.

`README.md` / `docs/build_status.md` still say “77+ tests” as a historical phrase; identity/INTERNET (UA-UPD-03) are correct.

## Live GitHub / device boundary

**Not verified:** live GET of `main/version.json`, real 302 to `refs/heads/main/version.json`, TLS, device `ACTION_VIEW` / installer, no-browser devices, dialog vs IME/nav. `HttpUrlConnectionFetcher` still has no live-network test. Same boundary as `docs/agent_c_github_update_fixes.md`.
