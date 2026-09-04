# Agent C — Round 1 implementation log

Workspace: `C:\Users\SUN\Desktop\GEO`  
Gradle user home: `C:\Users\SUN\Desktop\GEO\.gradle-user-home`  
Command: `.\gradlew.bat testDebugUnitTest assembleDebug`  
Result: **BUILD SUCCESSFUL** (second run after one JVM assertion fix).  
JVM tests: **53** tests, **0** failures, **0** errors, **0** skipped.  
Connected/instrumented tests: written, **not executed** (no emulator in this slice).

No commit, no push, no files deleted outside this work, original launcher icon unchanged.

---

## A-1 Major — `BillsQuery.range(CUSTOM)` fail closed

**Bug:** `range()` called `customRange()` then, on null, built `DateRange(min, max)`, silently swapping inverted custom endpoints. Production `BillsViewModel` uses `range()`; `setCustomRange` already rejected inverted input, but independent SavedState keys could still restore start>end.

**Fix:**
- `BillsQuery.range(...)` now returns `DateRange?`. CUSTOM delegates only to `customRange` (null when start>end). No min/max swap.
- `BillsViewModel.resolveRange` uses that result, or falls back to `GeoDates.month(month)` so the UI never queries a swapped custom span and never crashes.
- `setMode` copies a resolved (non-swapped) range into custom keys.

**Tests:**
- `BillsQueryTest.customRangeRejectsStartAfterEndAndAcceptsSingleDay` now asserts `range(CUSTOM, start>end)` is null and a valid ordered custom range still matches `customRange`.
- `BillsViewModelTest.invertedCustomSavedStateDoesNotSwapRange` restores CUSTOM with Sep 3 → Sep 1 and asserts the observed range is not the swapped Sep 1–Sep 3 span.

**Files:** `BillsQuery.kt`, `BillsViewModel.kt`, `BillsQueryTest.kt`, `BillsViewModelTest.kt`.

---

## A-2 Major — do not cloud-backup or device-transfer the ledger DB

**Bug:** `android:allowBackup="true"` and both backup XML files `<include domain="database" path="." />`, so `geo-ledger.db` (and WAL/SHM) could leave the device without an INTERNET permission.

**Fix:**
- Manifest `android:allowBackup="false"`.
- `backup_rules.xml`: `<exclude domain="database" path="." />`.
- `data_extraction_rules.xml`: exclude database in both `<cloud-backup>` and `<device-transfer>` (Android 12+ device transfer is independent of `allowBackup`).
- No `uses-permission` added. Merged debug manifest still only has the signature `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`.

**Tests:** `BackupAndPermissionManifestTest` reads source XML and `GeoDatabase.kt`:
- `allowBackup="false"`, no `<uses-permission>`, no listed dangerous permissions.
- backup/extraction rules exclude database and do not include it.
- production builder has `MIGRATION_1_2`, version 2, and no `fallbackToDestructiveMigration`.

Merged debug manifest verified after assemble: `allowBackup="false"`.

**Files:** `AndroidManifest.xml`, `backup_rules.xml`, `data_extraction_rules.xml`, `BackupAndPermissionManifestTest.kt`.

---

## B-1 High — reject histories whose aggregate income or expense cannot be exact Long cents

**Bug:** `saveTransaction` preflight was only `withRunningBalances`. Alternating `INCOME MAX-1`, `EXPENSE MAX-1`, `INCOME MAX-1` keeps running balance in range (`MAX-1`, `0`, `MAX-1`) and commits. `LedgerCalculator.summary` then `addExact`s period income and throws. Home/Bills Flows had no catch.

**Fix (strict exact arithmetic, no BigInteger/wrap):**
- `LedgerCalculator.requireExactAggregates` `addExact`s all-history income and all-history expense.
- `validateCandidateHistory` = aggregates then `withRunningBalances`.
- `LedgerRepository.saveTransaction` / `deleteTransaction` call `validateCandidateHistory` on the candidate. Overflow throws `ArithmeticException`; `AddTransactionViewModel` already maps that to `R.string.error_overflow` (“金额过大，无法保存”).
- Because any period is a subset of all-history, repository-accepted data cannot overflow period totals.
- Defense in depth for pre-fix/corrupt rows: `HomeDashboard.from` and `BillsViewModel.summaryOrSafe` catch `ArithmeticException` and do not propagate it. Period income/expense become 0; current/ending running balance is still taken from entries.

**Parser:** `MoneyParser.parseCents` still accepts `92233720368547758.06` = `MAX-1` (existing Long-range behavior). The repository gate is what stops the triple. Documented by `MoneyParserTest`.

**Tests:**
- `alternatingMaxMinusOneKeepsRunningBalanceButOverflowsPeriodAndHistoryTotals`: running balances succeed; `summary` / `requireExactAggregates` / `validateCandidateHistory` throw.
- `validateCandidateHistoryRejectsUpdateAndDateMoveThatOverflowAggregates`: accepted MAX-1 income + MAX-1 expense; date-move of that income into the expense month still validates (aggregates unchanged); updating the expense into a second MAX-1 income throws; appending a third MAX-1 income throws.
- `HomeDashboardTest.overflowPeriodTotalsDoNotPropagateArithmeticException`.
- `BillsViewModelTest.periodTotalsOverflowDoesNotCrashCustomOrMonthMapping`.
- Instrumented (not run): `saveRejectsHistoryWhoseIncomeTotalsOverflowLongEvenWhenRunningBalanceFits` — first two saves commit, third throws, date-move of the accepted income still saves.

**Files:** `LedgerCalculator.kt`, `LedgerRepository.kt`, `HomeDashboard.kt`, `BillsViewModel.kt`, `LedgerCalculatorTest.kt`, `HomeDashboardTest.kt`, `BillsViewModelTest.kt`, `MoneyParserTest.kt`, `LedgerRepositoryInstrumentedTest.kt`.

---

## A-4 Minor — `summary` ending balance on unsorted input

**Bug:** `endingBalanceCents` used `entries.lastOrNull { date <= range.end }` without sorting. `withRunningBalances` sorts; `summary` did not. Unsorted input picked the wrong all-history ending. In-range income/expense were order-independent and already correct.

**Fix:** `summary` sorts with `stableAscendingComparator` (date → createdAt → id) before accumulating and taking the last in-range-or-earlier entry.

**Test:** `summaryEndingBalanceUsesStableOrderForUnsortedInput` — entries listed Sep 3, Aug 31, Sep 2 with precomputed balances; September ending is 130_000 (last in stable order), not 150_000 (last in input order).

**Files:** `LedgerCalculator.kt`, `LedgerCalculatorTest.kt`.  
`HomeDashboard` now also takes current balance / recent 5 from stable order so unsorted callers stay consistent.

---

## A-3 Minor — unique active option names at schema level

**Bug:** Person/category uniqueness was only `countActiveByName` inside `withTransaction`. Schema v1 had no UNIQUE on name. Soft-delete then re-add with a new ID was already the repository path; concurrent/racy inserts could still create two active rows with the same name.

**Fix:**
- Nullable `active_name_key` on `person_options` and `expense_categories`.
- Unique index on that column (SQLite unique allows multiple NULLs).
- `activeOptionNameKey(isActive, normalizedName)`: active → normalized name, inactive → null.
- Repository add/rename set the key; deactivate nulls it. `countActiveByName` remains as a friendly pre-check. Unique violations map `SQLiteConstraintException` → `IllegalArgumentException("Active … name already exists")` so Settings UI still shows `error_duplicate_name`.
- Default category seed INSERT now includes `active_name_key`.
- Room version **1 → 2**. Manual `MIGRATION_1_2` (no destructive fallback):
  1. `ALTER TABLE … ADD COLUMN active_name_key TEXT`
  2. If duplicate active names exist, keep `MIN(id)` active and soft-deactivate extras (rows are not deleted).
  3. Set key = name for active, NULL for inactive.
  4. `CREATE UNIQUE INDEX index_*_active_name_key`
- Schema v1 JSON kept. kapt exported `app/schemas/.../2.json` with unique indexes. `exportSchema = true`.

**Tests:**
- JVM: `ActiveOptionNameKeyTest` (helper + schema v2 unique indexes + v1 preserved without the column).
- Instrumented (not run): repeated deactivate/re-add new IDs for person and category; DAO-level unique constraint vs multiple inactive NULLs; v1→v2 migration keeps three 张三 rows (one active with key, two inactive null) and survives reopen.

**Files:** `Entities.kt`, `GeoDatabase.kt`, `LedgerRepository.kt`, `app/build.gradle.kts` (androidTest schema assets), `ActiveOptionNameKeyTest.kt`, `LedgerRepositoryInstrumentedTest.kt`, `GeoDatabaseMigrationInstrumentedTest.kt`, generated `2.json`.

---

## Build / test evidence

```
.\gradlew.bat testDebugUnitTest assembleDebug
BUILD SUCCESSFUL
```

| Class | Tests |
|---|---|
| LedgerCalculatorTest | 15 |
| BillsViewModelTest | 4 |
| BillsQueryTest | 3 |
| BackupAndPermissionManifestTest | 3 |
| ActiveOptionNameKeyTest | 3 |
| HomeDashboardTest | 1 |
| MoneyParserTest | 3 |
| GeoDatesTest | 3 |
| OptionNameValidatorTest | 7 |
| AddTransactionValidatorTest | 6 |
| TransactionDetailUiMapperTest | 5 |
| **Total** | **53 / 0 fail / 0 error** |

First run: 53 tests, 1 failure (`validateCandidateHistoryRejectsUpdateAndDateMoveThatOverflowAggregates` expected September summary to throw after date-moving MAX-1 income next to MAX-1 expense). That history’s all-history/period totals still fit in Long; the assertion was wrong. Corrected to assert date-move of accepted data still validates, and that an update/add creating two MAX-1 incomes is rejected.

---

## Remaining risks / deferred

1. **Instrumented tests not run** (A-3 unique index live SQLite, v1→v2 migration + reopen, repository save of the MAX-1 triple). Need a device/emulator.
2. **Pre-fix databases** that already contain two MAX-1 incomes: observe Flow still computes running balances; Home/Bills zero period totals instead of crashing; new saves that would worsen aggregates are rejected. No automatic rewrite of those rows.
3. **Migration duplicate-active collapse** soft-deactivates extra active duplicates (`MIN(id)` kept). Unlikely if the app-level check held; rows are preserved.
4. **Money parser still allows MAX-1** per transaction. Product-sane cap was not applied in order to preserve existing parse behavior (`999999999.99` and Long-range cents). Repository aggregate check is the gate.
5. Amount field still has no `maxLength`; draft regex still allows long digit strings. Overflow on parse returns null; overflow on save shows the existing friendly error.
6. Concurrent option inserts are now blocked by the unique index even if `countActiveByName` races.
7. No live Auto Backup / device-transfer exercise; verification is source XML + merged debug manifest.
