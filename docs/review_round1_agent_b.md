# GEO Round 1 — Reviewer B adversarial data hunt

## Overall verdict

Money parse/format, running-balance order (date → `createdAtMillis` → `id`), backfill, edit/delete/date-move, same-day ties, leap/cross-month/year inclusivity, empty/only-income/only-expense/mixed totals, prior-history ending balance, option trim/blank/duplicate-active/40 UTF-16/emoji, snapshot freeze, and type-field isolation are consistent with the inspected code and on-disk JVM reports. One High defect remains: period income/expense totals can `addExact`-crash after the repository has already accepted the rows.

**Severity counts:** Critical 0 · High 1 · Medium 0 · Low 0

## BUG-B1 | High | period totals overflow after successful save

**Where:** `app/src/main/java/com/geo/ledger/domain/LedgerCalculator.kt` `summary` lines 26–35 (`Math.addExact` on `income` / `expense`); `app/src/main/java/com/geo/ledger/data/LedgerRepository.kt` `saveTransaction` lines 69–75 (pre-check is only `withRunningBalances`); consumers `HomeDashboard.from` line 18 and `BillsViewModel` line 78; enabler `MoneyParser.parseCents` (`app/src/main/java/com/geo/ledger/util/Money.kt` 13–28) and `AmountField` (`AddTransactionScreen.kt` 244–263, no max length).

**Trigger:** Parse/save amounts near `Long.MAX_VALUE` cents (`92233720368547758.06` = `MAX-1`). Example:

1. Income `MAX-1` on 2026-09-01  
2. Expense `MAX-1` on 2026-09-02  
3. Income `MAX-1` on 2026-09-03  

Running balances: `MAX-1`, `0`, `MAX-1` — `addExact`/`subtractExact` in `withRunningBalances` (lines 17–19) succeed, so `saveTransaction` commits. Equivalent: two large incomes in one period with an intervening expense that keeps the running balance in range; or date-move an older large income into that period.

**Impact:** `LedgerCalculator.summary` does `addExact(MAX-1, MAX-1)` for period income and throws `ArithmeticException`. `HomeViewModel.uiState` maps `HomeDashboard.from` with no catch; `BillsViewModel` calls `summary` inside `combine`. After legal writes, Home/Bills observation fails (stuck `stateIn` initial value or uncaught collector failure). Save-time overflow UI (`AddTransactionViewModel` catches `ArithmeticException`) never runs.

**Evidence (static, code-proven):** `summary` accumulates in-range amounts independently of running balance. `saveTransaction` builds `candidate` and calls only `withRunningBalances`, then insert/update. Parser: regex `^(?:[0-9]+(?:\.[0-9]{0,2})?|\.[0-9]{1,2})$`, then `multiplyExact(whole, 100)` + fraction; `92233720368547758.08` is rejected in `MoneyParserTest`, so `MAX` and `MAX-1` cents are in-range. `isAllowedDraftInput` allows long digit strings; amount field has no `maxLength`. JVM test `rejectsAggregateLongOverflowInsteadOfWrappingBalance` covers running-balance overflow only, not period income/expense sums.

**Required regression:** JVM test that `withRunningBalances` succeeds on `[INCOME MAX-1, EXPENSE MAX-1, INCOME MAX-1]` and `summary(..., September 2026)` throws `ArithmeticException` (or, after a cap/BigInteger fix, returns exact totals without throw). Also: `MoneyParser.parseCents` rejects or caps above a documented max (tests already use `999999999.99`); repository save rejects the same triple; Home/Bills mapping does not propagate raw `ArithmeticException`.

**Fix direction:** Cap parseable cents to a sane max **and** overflow-safe period totals (or treat overflow as a domain error at save). Catching only in the UI leaves the Flow unsafe.

## Tests / build evidence observed

This session’s `.\gradlew.bat testDebugUnitTest` **did not complete** (cancelled). On-disk `app/build/test-results/testDebugUnitTest/` XMLs (timestamp `2026-09-03T12:20:05Z`) report **0 failures / 0 errors**:

| Class | Tests |
|---|---|
| `LedgerCalculatorTest` | 12 |
| `MoneyParserTest` | 3 |
| `GeoDatesTest` | 3 |
| `BillsQueryTest` | 3 |
| `OptionNameValidatorTest` | 7 |
| `AddTransactionValidatorTest` | 6 |
| `BillsViewModelTest` | 2 |
| `TransactionDetailUiMapperTest` | 5 |

Covered in those artifacts: `0`/`0.00`/`-1`/`NaN`/`Infinity`/`1e3`/`1.001`/`128.`/`.5`/`999999999.99`; running-balance `Long.MAX_VALUE+1` income; backfill 9/1+9/3 then 9/2; edit/delete/date reorder; same-day `createdAt` then `id`; Jan 31 / leap Feb 29 / Dec 31 / cross-year inclusive; empty/only-income/only-expense/mixed; ending balance with prior history and later txs omitted; option blank/whitespace/duplicate-active/40 chars/emoji; `setCustomRange` start>end → false.

`LedgerRepositoryInstrumentedTest` (snapshots after rename/soft-delete, income/expense field isolation, file reopen) is **androidTest** and was **not executed** here. `assembleDebug` was **not run** here.

## Unverified attack surfaces

- **Concurrent option inserts:** `countActiveByName` + insert is inside `withTransaction`; schema `1.json` has **no unique** index on `(is_active, name)`. Single-process Room writer serialization is assumed, not raced.
- **Concurrent/double transaction save:** ViewModel `Mutex`+`AtomicBoolean`; repository transaction. No concurrent instrumented test.
- **DB tamper:** no `CHECK(amount_cents >= 1)`; `withRunningBalances` `require(amountCents >= 1)` would crash `observeAllOrdered` if a non-repository writer inserted 0/negative.
- **`BillsQuery.range(CUSTOM)`** swaps inverted endpoints (`BillsQuery.kt` 32–33) if SavedState keys are inverted; `setCustomRange` rejects that path.
- **Process-level two `GeoDatabase` connections** on one file (production uses `getInstance` singleton).
- Live device/emulator, Robolectric repository, and a fresh Gradle run: **not completed this session**.
