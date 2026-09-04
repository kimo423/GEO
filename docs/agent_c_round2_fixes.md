# Agent C Round 2 — fixes

## Compiler
- `BillsScreen.kt` custom-range error `Text` used `Modifier.padding(horizontal, bottom)`. Compose has no that overload. Split to `padding(horizontal = 24.dp).padding(bottom = 8.dp)`.

## Room
- Schema remains **v3**. `MIGRATION_1_2` (unique `active_name_key`) and `MIGRATION_2_3` (`transactions.client_op_key` unique index) stay explicit. Production builder still has **no** `fallbackToDestructiveMigration`.

## Home
- Home is `HomeViewModel`-backed (`HomeScreen` collects `uiState`). GEO title + `dateLabel`. Invalid ledger observation sets `ledgerError` instead of crashing collection. Calendar ticker is skippable in JVM tests (`tickCalendar = false`) so `delay(until midnight)` cannot infinite-skip virtual time.

## Historical options / reselect (R2A-2, R2B-1, R2B-2)
- Inactive/renamed selection shows a historical chip.
- Historical chip click **clears** the id.
- Live chip click with the same id **explicitly reselects**: marks edited and writes the live name into the snapshot handle so the historical chip disappears.
- Save still freezes the old snapshot when the user never reselects.

## Idempotent NEW save (R2B-4)
- Editor allocates `KEY_CLIENT_OP` in SavedState. After insert, `ARG_TRANSACTION_ID` is updated. Repository `SaveIdempotency` maps a restored NEW editor (`id <= 0`) to the row for the same client op key.

## Custom range (R2A-3)
- Confirm is disabled when start > end. UI shows `error_invalid_range`. Domain `CustomRangeSelection` / `BillsQuery.customRange` fail closed (no min/max swap). Saved inverted CUSTOM state falls back to the current month range, not a swapped span.

## Navigation (R2B follow-on)
- `EditorNavigationGuard` blocks same-editor current destination, existing back-stack editor for that id, and same-target taps inside 600ms.

## Amount cap (R2A-5)
- `MoneyParser.MAX_DRAFT_LENGTH = 20`. Draft issue `TooLong`/`Invalid` shown on the amount field. ViewModel truncates to the cap.

## Observation errors (R2A-6)
- `LedgerObserver.observe` catches non-cancellation failures. Home/Bills map `Invalid` to `ledgerError`. `CancellationException` is rethrown on Home, Bills, editor hydrate/save, options, detail.

## Tests added/repaired
- `BillsViewModelTest` now uses `Flow<LedgerObservation>` (constructor match). Overflow month totals assert `ledgerError`.
- `ActiveOptionNameKeyTest` covers schema v3 + no destructive fallback.
- `Round2PoliciesTest`, `AddTransactionViewModelTest`, `HomeViewModelTest`, amount-cap assertion in `MoneyParserTest`.

## Deferred
- R2A-4 adaptive/round launcher icon (not in this pass).
- Instrumented / emulator paths (DatePicker TZ, live v2→v3, IME vs save, Home flash on device).
