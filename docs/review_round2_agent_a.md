# GEO Round 2 — Reviewer A implementation audit

## Overall
**PASS WITH ISSUES.** Kotlin + Compose + Material 3 + Room v2 + Flow/ViewModel ledger is present in source. Long cents, epochDay, dynamic running balances, snapshots, soft-delete, bills day/month/year/custom (domain fail-closed), backup exclusion, and no dangerous permissions hold in the inspected tree. Two Major product gaps: Home never renders the specified GEO header/date and never uses `HomeViewModel`; expense edit does not show a soft-deleted person/category while still saving that ID. No Critical balance-corruption path found in the write/observe code that was read.

## Severity counts
| Severity | Count |
|---|---|
| Critical | 0 |
| Major | 2 |
| Minor | 4 |

## Requirements traceability
| Topic | Verdict | Anchor |
|---|---|---|
| Kotlin / Compose / M3 / Room / Flow / VM | PASS WITH R2A-1 | `app/build.gradle.kts`; VMs except Home |
| Label `GEO` / `com.geo.ledger` / launcher PNG | PASS WITH R2A-4 | `app_name`; `applicationId`; mipmap PNGs |
| Home balance / month totals / recent 5 / empty | PASS WITH R2A-1 | `HomeDashboard`; `GeoApp.HomeScreen` |
| Add/edit form, validation, IME, double-submit | PASS WITH R2A-2, R2A-5 | `AddTransactionViewModel`/`Screen`/`Validator` |
| Bills day/month/year/custom + grouped balances | PASS WITH R2A-3 | `BillsQuery`; `BillsViewModel`; `BillsScreen` |
| Settings person/category add/rename/soft-delete | PASS | `LedgerRepository`; `ManageOptionsContent` |
| Detail / edit / delete + confirm | PASS | `TransactionDetailScreen`/`ViewModel` |
| Snapshots; cents; epochDay picker | PASS | `resolvePersonSnapshot`; `MoneyParser`; `GeoDates` UTC |
| Room v2 migration; no destructive fallback | PASS | `GeoDatabase` v2 + `MIGRATION_1_2`; `2.json` unique indexes |
| Backup / privacy / permissions | PASS | `allowBackup="false"`; DB excluded; no `uses-permission` |
| Real data / no demo amounts | PASS | Home/Bills bind `LedgerEntry` |
| Nav / back / loading / empty / error | PASS (static) | `GeoApp` top-level bar; editor/detail loading |
| Tests A–K (JVM) | PARTIAL | Calculator/query/parser/option tests; C/D/L instrumented only |

## Actionable issues

### R2A-1 — Major — Home ignores `HomeViewModel`; no GEO title or date
- **Where:** `app/src/main/java/com/geo/ledger/ui/navigation/GeoApp.kt` `HomeScreen` (296–387); unused `ui/home/HomeViewModel.kt` `uiState` (24–29); factory wires VM (22–23) but nothing composes it.
- **Evidence:** Home collects `repository.ledgerEntries` in the composable and `remember(entries) { HomeDashboard.from(entries, LocalDate.now()) }`. It starts at `R.string.current_balance`. No `R.string.app_name`, no `GeoDates.formatDate`. `HomeViewModel` already builds `dateLabel`. Top-level Scaffold has no topBar (`showBottomBar` true).
- **Impact:** First screen misses required identity/date; month totals freeze until ledger emissions; architecture skips ViewModel on Home.
- **Fix/test:** Compose `HomeViewModel`; show GEO + `dateLabel`; Compose/VM test that those strings are in the tree.

### R2A-2 — Major — Soft-deleted person/category not shown on edit; ID still saved
- **Where:** `AddTransactionViewModel.hydrateExistingIfNeeded` (240–243); `AddTransactionScreen` expense `ChipSection` (118–132); `ChipSection` (293–298); `LedgerRepository.resolvePersonSnapshot` / `resolveCategorySnapshot` (190–209).
- **Evidence:** Hydrate writes inactive IDs into SavedState. Chips iterate `activePersons`/`activeCategories` only, so no chip is selected. Save still sends that ID; same-ID snapshot is kept. If no other active option exists, the ID cannot be toggled off.
- **Impact:** Edit does not show filled Person/Category; user can believe the optional field is empty; clearing can be impossible.
- **Fix/test:** Show inactive selection (read-only chip or snapshot label) with explicit clear; JVM/UI test: deactivate then edit → visible + clearable.

### R2A-3 — Minor — Custom picker min/max-swaps inverted dates
- **Where:** `BillsScreen.kt` `CustomRangePickerDialog` (386–400); unused `R.string.error_invalid_range`.
- **Evidence:** Confirm uses `minOf`/`maxOf` and a swapped label. `BillsQuery.range(CUSTOM)` is null when start>end (32–33); `setCustomRange` returns false (116–117); `resolveRange` falls back to `GeoDates.month` (169–170). User confirm never hits fail-closed.
- **Impact:** Inverted taps still query the swapped span (TEST K bypass at UI).
- **Fix/test:** Disable confirm or show `error_invalid_range`; assert no min/max swap.

### R2A-4 — Minor — No adaptive icon; generator flattens RGB and copies round=square
- **Where:** `tools/generate_launcher_icons.py` (20–27); `AndroidManifest.xml` 9–11; `res/` has density PNGs only (no `mipmap-anydpi-v26`); unused `ic_launcher_background`.
- **Evidence:** `convert("RGB")` then `resize` to 48–192; same bytes for `ic_launcher_round`. Original PNG left in place.
- **Impact:** OEM masks may crop; alpha/round treatment is not adaptive.
- **Fix/test:** Adaptive XML + RGBA foreground; round mask; keep original PNG.

### R2A-5 — Minor — Amount field has no `maxLength`
- **Where:** `AddTransactionScreen.kt` `AmountField` (244–263); `MoneyParser.isAllowedDraftInput` (31–36).
- **Evidence:** Draft regex allows long digit strings; overflow parse returns null; save disable has no overflow hint.
- **Impact:** Huge drafts, easy to hit overflow-disable with no explanation.
- **Fix/test:** Cap length; assert over-cap rejected in UI/parser tests.

### R2A-6 — Minor — Observe path does not catch `withRunningBalances` failures
- **Where:** `LedgerRepository.ledgerEntries` (26–28).
- **Evidence:** `.map(LedgerCalculator::withRunningBalances)` has no catch. `require(amountCents >= 1)` and `addExact`/`subtractExact` can throw. `HomeDashboard.from` / `summaryOrSafe` catch only `summary` `ArithmeticException`. No DB `CHECK` on `amount_cents`.
- **Impact:** Tampered/pre-gate rows crash Home/Bills/Detail collection.
- **Fix/test:** Catch in the Flow; UI error state; test amount 0 / running overflow does not kill collection.

## Test / build evidence observed
- This session did **not** complete `gradlew` (command cancelled). No new exit code.
- On-disk `app/build/test-results/testDebugUnitTest/TEST-com.geo.ledger.domain.LedgerCalculatorTest.xml` timestamp `2026-09-03T12:50:16.550Z`: **15 tests, 0 failures, 0 errors, 0 skipped**.
- Other `TEST-*.xml` files exist for Backup, ActiveOptionNameKey, BillsQuery, HomeDashboard, OptionNameValidator, AddTransactionValidator, BillsViewModel, TransactionDetailUiMapper, GeoDates, MoneyParser; **bodies not parsed here**.
- `app/build/outputs/apk/debug/` listed an APK; assemble not re-run here.
- Instrumented tests exist; **not executed**.

## Unverified (no emulator / no this-session Gradle)
- Device Home GEO/date, IME vs save, system Back, empty/error flash
- DatePicker / DateRangePicker timezone on device
- Adaptive/round icon on a launcher
- Auto Backup / device-transfer of `geo-ledger.db`
- Live v1→v2 migration, unique index, snapshot/reopen, MAX-1 triple save
- Merged release manifest; TalkBack; landscape range picker
- Midnight-stale Home month totals without ledger emissions
