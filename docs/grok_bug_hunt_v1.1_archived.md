I'll re-read the Round 3 fix notes and the current Bills, date, Home, editor, and icon code against R3B-1–5.# GEO Round 3 closure — Reviewer B

## Overall

**PASS.** R3B-1 through R3B-5 are fixed in current source. No new Critical/High/Medium defects were proven from those changes. Remaining lint items are dependency/tooling/`ObsoleteSdkInt` (adaptive XML kept under `mipmap-anydpi-v26` so AAPT links `@mipmap/ic_launcher`).

**Severity counts:** Critical **0** · High **0** · Medium **0** · Low **0**

---

## Closure

| ID | Status | Proof |
|---|---|---|
| **R3B-1** | **Fixed** | `BillsUiState.isReady`; `initialState()` sets `isReady = false`. `BillsScreen` shows `content_loading` spinner; summary zeros and `empty_period_bills` only when `isReady && !ledgerError && !invalidCustomRange`. Test `coldStartIsNotReadyUntilObservationEmits`. |
| **R3B-2** | **Fixed** | `LedgerObserver` requires `GeoDates.fromEpochDayOrNull`. Home/detail/editor use `formatEpochDay` / `toPickerMillisOrNull` / `fromPickerMillisOrNull`. Confirm paths never call throwing `fromPickerMillis`. Tests: `GeoDatesTest` corrupt epoch; `Round2PoliciesTest` `Long.MAX_VALUE` → `Invalid`. |
| **R3B-3** | **Fixed** | `HomeScreen`: `!isReady && !ledgerError` → `CircularProgressIndicator` + `content_loading`. `HomeViewModelTest.unreadObservationIsNotReadyAndNotError`. |
| **R3B-4** | **Fixed** | `onSourceChange` / `onNoteChange` and hydrate `take(MAX_SOURCE_LENGTH / MAX_NOTE_LENGTH)`. `amountCapTruncatesOverlongDraft` asserts both caps. |
| **R3B-5** | **Fixed** | Density `mipmap-*dpi/ic_launcher*.png` gone. Adaptive XML in `mipmap-anydpi-v26`. Lint has **no** `IconLauncherShape` / `IconXmlAndPng`. |

R3A-1 (inverted CUSTOM fail-closed, `range = null`, `error_invalid_range`) is consistent with `BillsViewModel` / `invertedCustomSavedStateDoesNotSwapRange`.

Fix-induced notes (not filed): one bad `transactionDate` fail-closes the **whole** ledger (`Invalid`), which is the intended crash-avoidance. Invalid CUSTOM picker seeds `DateRange(LocalDate.now(), LocalDate.now())` only as a dialog fallback (`BillsScreen.kt` 198–199); confirm still goes through `setCustomRange` / `customRange`. `BillsQuery.groups` still uses `ofEpochDay`; callers are `Ready` rows already date-checked, plus VM `try/catch`.

No remaining **code-proven** product bugs in this recheck.

---

## Test / lint / assemble evidence

On-disk `app/build/test-results/testDebugUnitTest/*.xml` timestamp **2026-09-04T04:40:49Z**:

**69 tests, 0 failures, 0 errors** (14 suites: MoneyParser 4, GeoDates 4, Detail mapper 5, Home VM 3, Bills VM 5, AddTx VM 3, AddTx validator 6, Round2Policies 6, OptionName 7, LedgerCalculator 15, HomeDashboard 1, BillsQuery 3, ActiveOptionNameKey 4, BackupAndPermission 3). This session did **not** re-invoke Gradle.

`lint-results-debug.txt`: **0 errors, 10 warnings** (GradleDependency 7, ConfigurationScreenWidthHeight 1, ObsoleteSdkInt 1 on `mipmap-anydpi-v26`, KaptUsageInsteadOfKsp 1).

`docs/agent_c_round3_fixes.md`: `lintDebug testDebugUnitTest assembleDebug` **BUILD SUCCESSFUL**, exit **0**, ~1m 1s. `app/build/outputs/apk/debug/app-debug.apk` present (`output-metadata.json` versionName `1.0.0`).

**Emulator not run** (AEHD / no device). androidTest not executed.

---

## Unverified device boundary

Launcher silhouette of XML-only adaptive icons; IME vs option dialogs; process-death after insert; v1→v3 file migration; `TransactionType.valueOf` on tampered `type`; empty `KEY_CLIENT_OP`; midnight/DST Home tick; `getBackStackEntry` vs editor query routes.
