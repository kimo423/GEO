# GEO Round 2 — Reviewer B adversarial hunt

## Overall verdict

**FAIL WITH ISSUES.** Domain money/balance/order, v1→v2 unique `active_name_key`, backup off, and save Mutex look consistent with source. Four High defects remain: edit cannot clear inactive options; edit-after-rename freezes the old snapshot while chips show the new name; Home flashes ¥0.00 by bypassing `HomeViewModel`; process death after a NEW insert can duplicate the row.

**Severity counts:** Critical **0** · High **4** · Medium **2** · Low **2**

---

## R2B-1 | High | inactive person/category cannot be seen or cleared on edit

**Where:** `LedgerRepository.resolvePersonSnapshot` / `resolveCategorySnapshot` (lines 191–196, 204–209); `AddTransactionViewModel.hydrateExistingIfNeeded` (242–243); `ChipSection` (`AddTransactionScreen.kt` 118–131, 268–299); `togglePerson`/`toggleCategory` (158–166).

**Trigger:** Save expense with 张三 → Settings soft-delete 张三 → open that bill → Edit. Hydrate writes the inactive id; chips list only `activePersons`/`activeCategories`.

**Impact:** No selected chip; there is no “none” control; toggle only works on visible chips. Save keeps the deleted option via the same-id snapshot branch. Optional person/category cannot be removed. NEW save with a stale id throws `Person is inactive` → generic `error_save_failed`.

**Evidence:** `require(option.isActive)` is skipped when `existing.expensePersonId == selectedId`. UI never reconciles `personId` against `uiState.persons`.

**Fix:** If id is active, snapshot = current `name` and show the chip; if inactive, show a disabled “historical” chip that can be cleared to null; drop ids missing from both.

**Regression:** JVM/UI test: deactivate person, edit, assert chip visible or clearable; save with null person; NEW + inactive id does not generic-fail with no way to unselect.

---

## R2B-2 | High | edit after rename keeps old snapshot

**Where:** `resolvePersonSnapshot` 191–193; `resolveCategorySnapshot` 204–206; chips bind `it.name` (AddTransactionScreen 122–123).

**Trigger:** Expense category 耗材 → rename to 实验耗材 → Edit (chip shows 实验耗材, still selected) → save amount/date only.

**Impact:** Same-id branch returns `existing.expenseCategorySnapshot` (“耗材”). User confirmed the live name; the bill still shows the old snapshot. Conflicts with edit of Person/Category (requirements §35) vs freeze-on-option-rename-without-edit (§8).

**Evidence:** Instrumented `personAndCategorySnapshotsSurviveRenameAndSoftDelete` covers rename **without** edit only. No edit-after-rename test.

**Fix:** Active id → current `option.name`; inactive id → existing snapshot (R2B-1).

**Regression:** Rename category, edit+save same id, assert snapshot is the new name; detail/list show it.

---

## R2B-3 | High | Home flashes ¥0.00 / empty bills after save and tab return

**Where:** `GeoApp.kt` `HomeScreen` 301–304: `repository.ledgerEntries.collectAsStateWithLifecycle(initialValue = emptyList())`; `remember(entries) { HomeDashboard.from(...) }`. `HomeViewModel` (`dateLabel` + `stateIn`) is only constructed in `GeoViewModelFactory` 22–23, never used.

**Trigger:** Home → 记一笔 → save → pop; or Home → Bills/Settings → Home. NavHost disposes Home; collection restarts at `emptyList()`.

**Impact:** First composition shows 当前余额 **¥0.00**, empty copy, then real data. Breaks “第一眼知道还剩多少钱” on the main path. `HomeViewModel`’s `StateFlow` would keep last value on the back-stack entry.

**Evidence:** Cold Flow + `produceState(initialValue)`; Bills uses `stateIn` and does not reset on return.

**Fix:** Collect `HomeViewModel.uiState`; do not seed the UI with an empty ledger when a cached snapshot exists.

**Regression:** Compose/VM test: non-empty StateFlow, new collector sees last balance, not 0.

---

## R2B-4 | High | process death after NEW insert duplicates the row

**Where:** `AddTransactionViewModel.save` 180–224: `editId` from `ARG_TRANSACTION_ID` only if `> 0` (203–205); insert does not write the new id; `saveInFlight`/`success` are memory-only; `NEW_TRANSACTION_ID = -1` (271).

**Trigger:** NEW save commits → process kill before `Saved` → `popBackStack`. Restored editor has the same SavedState form and `transactionId=-1`.

**Impact:** Second Save inserts a duplicate. Edit path is safe (update same id). In-memory Mutex does not survive death.

**Evidence:** No SavedState “alreadySaved”/id after insert; events Channel is not process-persistent.

**Fix:** After insert, `savedStateHandle[ARG_TRANSACTION_ID] = newId` (or pop before return); treat restored id as update.

**Regression:** Fake repository + restored handle with filled form and id=-1 after one insert → second save must update, not insert.

---

## R2B-5 | Medium | Home missing GEO title and current date

**Where:** `GeoApp.kt` 143–161 topBar only when `!showBottomBar`; `HomeScreen` 314+ starts at 当前余额. `HomeViewModel.dateLabel` (27–28) unused. Requirements §15.

**Trigger:** Open Home.

**Impact:** No app name, no 2026年9月3日.

**Fix:** Render GEO + `dateLabel` from `HomeViewModel`.

**Regression:** Compose assertion on those two strings.

---

## R2B-6 | Medium | rapid 记一笔 / Edit stacks editors

**Where:** `GeoApp.kt` 232, 272 `navController.navigate(editor)` with no `launchSingleTop`/debounce. Edit button only gated by `isDeleting` (TransactionDetailScreen 211–214).

**Trigger:** Double-tap ＋ 记一笔 or Edit.

**Impact:** Two back-stack editors / two ViewModels. Save pops onto a second empty editor; two NEW saves → two rows.

**Fix:** `launchSingleTop` + ignore clicks while already on editor.

**Regression:** Nav test: two navigates to `editor?transactionId=-1` → one destination.

---

## R2B-7 | Low | `catch (Exception)` swallows cancellation

**Where:** `AddTransactionViewModel` 217–218; `TransactionDetailViewModel` 80–85; `OptionMutationController` 85–86.

**Trigger:** System Back during save/delete (`onBack` not disabled while `isSaving`).

**Impact:** `CancellationException` treated as failure; Room work may already have committed. Back is enabled during save (AddTransactionScreen 150, GeoApp 150).

**Fix:** Re-throw `CancellationException`; disable back while in flight.

**Regression:** Cancel `viewModelScope` mid-save; no Failed event; no stuck `saveInFlight` on a live VM.

---

## R2B-8 | Low | long option names overflow chips/rows

**Where:** `ChipSection` 294–297 `Text(name)`; `OptionRow` 267–271. Max length 40 UTF-16 (`OptionNameValidatorTest` 67–77).

**Trigger:** 40× “测” as person/category.

**Impact:** Chip/row wider than the screen; no `maxLines`/`overflow`/`widthIn`. Unlikely crash; layout overflow.

**Fix:** Ellipsize; wrap FlowRow against `fillMaxWidth`.

**Regression:** 40-CJK name still lays out inside screen width.

---

## Test / build evidence observed

This session’s `.\gradlew.bat testDebugUnitTest --offline` **did not complete** (cancelled). No new JVM/APK run.

On-disk `app/build/test-results/testDebugUnitTest/TEST-com.geo.ledger.domain.LedgerCalculatorTest.xml` timestamp **2026-09-03T12:50:16Z**: **15 tests, 0 failures, 0 errors**. Other XMLs present (not re-parsed here): `MoneyParserTest`, `GeoDatesTest`, `BillsQueryTest`, `OptionNameValidatorTest`, `AddTransactionValidatorTest`, `BillsViewModelTest`, `TransactionDetailUiMapperTest`, `BackupAndPermissionManifestTest`, `ActiveOptionNameKeyTest`, `HomeDashboardTest`. Agent C log claimed **53 / 0 / 0** for `testDebugUnitTest assembleDebug`; not reproduced here.

Read-only: merged debug manifest `allowBackup="false"`, no `uses-permission` except signature `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`; schemas v1 hash `055e3729edffa723e7e668af75d2e503` matches migration test; v2 unique indexes on `active_name_key`. **androidTest not executed** (no emulator).

Covered in existing JVM sources: cents parse/reject, running-balance backfill/edit/delete/date-move, same-day createdAt/id, leap/cross-year, empty/only-income/only-expense, inverted CUSTOM fail-closed, overflow aggregates, option 40/emoji/blank/duplicate.

**Not covered:** R2B-1…6, snapshot-on-edit, Home initial empty, SavedState duplicate insert, stacked nav.

---

## Unverified attack surfaces

- `ledgerEntries` `map(withRunningBalances)` uncaught if `amountCents < 1` or type `valueOf` fails (tamper); no Room CHECK.
- `BillsViewModel` `YearMonth.parse` / `LocalDate.ofEpochDay` on corrupt SavedState; unbounded year `shift`.
- v1→v2 + default seed `onCreate` on a real file DB (instrumented exists, not run).
- Option AlertDialog IME vs confirm; amount field unbounded draft length.
- `Don't keep activities` + editor vs Settings deactivate.
- Home month totals stale across midnight until the next Flow emission.
- Live Auto Backup / device-transfer; release merged manifest.
