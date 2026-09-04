# Round 1 Reviewer A — Data/Semantics

## Overall
**PASS WITH ISSUES.** Inspected Kotlin data/domain/util, Room schema v1, source/merged manifests, backup XML, and JVM tests on disk. Long cents, epochDay, `date → createdAt → id` order, dynamic running balances, snapshot freeze, option soft-delete + new insert ID, default categories with empty persons, Room v1 (no destructive fallback), and no dangerous permissions hold in source. Two Major defects: custom period `range()` fail-opens by swapping inverted dates; Auto Backup / cloud backup include the ledger DB. No Critical balance-corruption bug in the inspected write/read path.

## Severity counts
| Severity | Count |
|---|---|
| Critical | 0 |
| Major | 2 |
| Minor | 2 |

## Requirements traceability
| Topic | Verdict | Anchor |
|---|---|---|
| Long cents / strict parser / overflow | PASS | `Money.kt` `parseCents`; `LedgerCalculator.withRunningBalances` `addExact`/`subtractExact`; `LedgerRepository.saveTransaction` preflight |
| epochDay | PASS | `TransactionEntity.transactionDate`; `saveTransaction` `toEpochDay()`; `DateRange.contains`; `GeoDates` UTC picker |
| Stable date-createdAt-id order | PASS | `GeoDao` `ORDER BY transaction_date, created_at_millis, id`; `LedgerCalculator.stableAscendingComparator` |
| Dynamic balances (backfill/edit/delete/date move) | PASS | Balances not stored; Flow + save/delete candidate run `withRunningBalances` |
| Period totals + all-history ending | PASS WITH A-1 | `LedgerCalculator.summary`; `HomeDashboard` last running balance |
| Snapshot capture/preservation | PASS (untested in JVM) | `resolvePersonSnapshot` / `resolveCategorySnapshot` keep snapshot on same ID |
| Soft delete / re-add new ID | PASS WITH A-3 | `isActive=false`; `addPerson`/`addCategory` always `@Insert` |
| Default categories / empty persons | PASS | `DefaultCategoryCallback.onCreate` five names; no person seed |
| Room atomicity / migrations / indices / persistence | PASS WITH A-2, A-3 | `withTransaction`; v1 `exportSchema`; no `fallbackToDestructiveMigration`; file `geo-ledger.db` |
| No permissions | PASS | Source manifest has no `uses-permission`; merged only signature `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` |

## Actionable issues

### A-1 — Major
- **Where:** `app/src/main/java/com/geo/ledger/domain/BillsQuery.kt` `range` (`BillsPeriodMode.CUSTOM` branch, lines 32–33)
- **Evidence:** `customRange` returns null when start is after end. `range()` then builds `DateRange(min, max)` and accepts the swapped span. `DateRange` init rejects inverted bounds (`LedgerCalculatorTest.testK`). `BillsQueryTest` asserts `customRange` null for inverted input and does not assert `range(CUSTOM, …)` rejection. Production period totals call `BillsQuery.range` (`BillsViewModel` combine). Custom start/end are independent SavedState epoch keys.
- **Required fix:** On inverted custom bounds, fail closed (null/error). Do not min/max-swap. Align `range()` with `customRange` / `DateRange`.

### A-2 — Major
- **Where:** `app/src/main/AndroidManifest.xml` line 6 `android:allowBackup="true"`; `app/src/main/res/xml/backup_rules.xml` line 3; `app/src/main/res/xml/data_extraction_rules.xml` lines 3–8
- **Evidence:** Full-backup and cloud-backup/device-transfer rules `<include domain="database" path="." />`. Room file `geo-ledger.db` (`GeoDatabase` `DATABASE_NAME`) is eligible to leave the device without an app network permission.
- **Required fix:** Set `allowBackup="false"` and exclude the database domain from backup/extraction rules (or include only non-ledger files).

### A-3 — Minor
- **Where:** `app/src/main/java/com/geo/ledger/data/local/Entities.kt` `PersonOptionEntity` line 50; `ExpenseCategoryEntity` line 68
- **Evidence:** Indices are `(is_active, sort_order)` only. Schema `1.json` has no UNIQUE on `name`. Uniqueness is only `countActiveByName` inside `withTransaction`.
- **Required fix:** Enforce active-name uniqueness in schema (partial unique index or equivalent) so re-add of a deactivated name stays a new row without two active duplicates.

### A-4 — Minor
- **Where:** `app/src/main/java/com/geo/ledger/domain/LedgerCalculator.kt` `summary` lines 36–39
- **Evidence:** `endingBalanceCents` is `entries.lastOrNull { transactionDate <= range.end }`. `withRunningBalances` sorts; `summary` does not. Unsorted input yields the wrong all-history ending. In-range income/expense ignore order and stay correct.
- **Required fix:** Sort with `stableAscendingComparator` inside `summary` (or document and assert a sorted precondition).

## Test / build evidence observed
- `.\gradlew.bat testDebugUnitTest` **did not complete** in this session (cancelled). No new pass/fail counts from this run.
- Prior artifacts present under `app/build/test-results/testDebugUnitTest/` for: `LedgerCalculatorTest`, `BillsQueryTest`, `GeoDatesTest`, `MoneyParserTest`, `OptionNameValidatorTest`, `AddTransactionValidatorTest`, `BillsViewModelTest`, `TransactionDetailUiMapperTest`. Those XML bodies were not parsed here.
- JVM suite covers cents parse/overflow, leap/inclusive epoch ranges, stable same-day order, backfill/edit/delete/date-move balances, period totals with pre-range ending, inverted `DateRange` reject, `customRange` null.
- JVM suite does **not** cover `LedgerRepository` snapshot, soft-delete re-add, default seed, or `withTransaction` rollback.
- `LedgerRepositoryInstrumentedTest` exists (in-memory snapshot rename/deactivate; file reopen). It is not part of `testDebugUnitTest`. In-memory builder omits `DefaultCategoryCallback`.

## Unverified
- This-session unit-test exit code and assertion results
- Instrumented Room run (seed, re-add new ID, atomic rollback, file reopen)
- Production `getInstance()` on-create seed on a real device DB
- Runtime merged-manifest permission set beyond the inspected debug merged manifest
- Overflow exception mapping on a real `saveTransaction` Room transaction
- Whether SavedState ever persists inverted custom start/end in the wild (A-1 is still fail-open in domain)
