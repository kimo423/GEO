I'll recheck R3A-1/R3A-2 in current source and artifacts, then write the closure report.# GEO Round 3 closure — Reviewer A

## Overall
**PASS.** Round-3 R3A-1 and R3A-2 are closed in current source. No new Critical/Major/Minor product defect was proven in the recheck. Ledger remains local Kotlin/Compose/M3/Room v3: Long cents, epochDay, dynamic balances, snapshots/historical chips, `client_op_key`, v1→v2→v3 without destructive fallback, Home via `HomeViewModel`, backup off, no dangerous permissions.

## Severity counts
| Severity | Count |
|---|---|
| Critical | 0 |
| Major | 0 |
| Minor | 0 |

## R3A closure

### R3A-1 — CLOSED
**Was:** CUSTOM + inverted SavedState used `resolveRange(...) ?: GeoDates.month(month)` so month totals ran under CUSTOM chrome.

**Now:** `BillsViewModel` combine sets `invalidCustom = (mode == CUSTOM && BillsQuery.range(...) == null)`, `range = null`, `invalidCustomRange = true`, mode stays `CUSTOM`. Ready path does **not** call `LedgerCalculator.summary` / `BillsQuery.groups` on a month substitute. `BillsScreen` shows `R.string.error_invalid_range` when `invalidCustomRange`. Picker still uses `CustomRangeSelection.confirmRange` (confirm disabled if inverted). `setCustomRange` still returns false on start>end.

**Test:** `BillsViewModelTest.invertedCustomSavedStateDoesNotSwapRange` — CUSTOM, `range == null`, `invalidCustomRange`, not `ledgerError`. XML: pass, 0 fail.

`setMode` still uses `?: GeoDates.month` only to **seed** start/end when entering CUSTOM from a valid day/month/year. That is not the inverted-query path.

### R3A-2 — CLOSED
**Was:** Density `mipmap-*dpi` square/round PNGs (`IconLauncherShape` / `IconXmlAndPng`).

**Now:** Those bitmaps are gone (`mipmap-mdpi`…`xxxhdpi` empty). Adaptive XML: `mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml` → `@drawable/ic_launcher_foreground` / monochrome. Manifest still `@mipmap/ic_launcher` / `_round`. Lint: **no** IconLauncherShape / IconXmlAndPng. Remaining `ObsoleteSdkInt` on `-v26` is the documented AAPT constraint (plain `mipmap-anydpi` XML did not link). `GEO-icon-original.png` hash recorded in `docs/agent_c_round3_fixes.md`: `BB8EA2FECC57D0DD37A06285567F58928D015DD23FAF583D28D1E5CA3213EC3C`.

## Remaining findings
None that meet Critical/Major/Minor for this gate. Leftover lint (10) is GradleDependency (7), ConfigurationScreenWidthHeight (1), ObsoleteSdkInt (1), KaptUsageInsteadOfKsp (1) — not balance/privacy/icon-shape defects.

Related Round-3 work (not regressions): Bills/Home `isReady` + loading; `LedgerObserver` rejects bad epochDay; source/note length caps. `coldStartIsNotReadyUntilObservationEmits` is in the JVM suite.

## Traceability (compact)
| Topic | Status |
|---|---|
| Home GEO/date/`HomeViewModel`/error | PASS |
| Historical option chips + snapshot freeze until reselect | PASS |
| Custom range fail-closed | PASS (R3A-1 closed) |
| `client_op_key` + v1→v2→v3 | PASS |
| Cents / `addExact` / order | PASS |
| Adaptive icon / original PNG | PASS (R3A-2 closed) |
| Backup/permissions | PASS |
| Real data binding | PASS |

## Test / lint / assemble
`docs/agent_c_round3_fixes.md`: `GRADLE_USER_HOME=…/.gradle-user-home` `.\gradlew.bat --no-daemon lintDebug testDebugUnitTest assembleDebug` — **BUILD SUCCESSFUL in 1m 1s**, exit **0**.

JVM XML `2026-09-04T04:40:49Z`, **69 tests, 0 failures, 0 errors** (sum of suites: LedgerCalculator 15, OptionNameValidator 7, Round2Policies 6, AddTransactionValidator 6, BillsViewModel 5, TransactionDetailUiMapper 5, ActiveOptionNameKey 4, MoneyParser 4, GeoDates 4, Backup 3, BillsQuery 3, HomeViewModel 3, AddTransactionViewModel 3, HomeDashboard 1).

Lint `app/build/reports/lint-results-debug.txt`: **0 errors, 10 warnings**.

Debug APK metadata still `app-debug.apk` / `com.geo.ledger` / `1.0.0`.

## External connected-test boundary
**Not passed.** No emulator this invocation. Prior `docs/emulator_test_report.md`: no AEHD; `connectedDebugAndroidTest` **exit 1**, no devices, **0** instrumented tests run. Room file migration / UI on device remain unverified until a hypervisor-capable AVD boots.
