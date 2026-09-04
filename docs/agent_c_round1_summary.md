I'll start by reading the Round-1 review docs and requirements, then implement the five validated issues in the GEO project.I've got the five issues; next I'll inspect the current domain, Room, and test code so the fixes match existing behavior.I have a clear picture of the five defects. Next I’ll apply the production fixes, then add the regression tests.Running unit tests and the debug assemble with the project-local Gradle home.One unit test failed; I'll inspect that assertion and correct it.Round-1 fixes are implemented in `C:\Users\SUN\Desktop\GEO`. Internal log: `docs\agent_c_round1_fixes.md`.

**Closed:** A-1, A-2, B-1, A-3, A-4.

**Build:** `.\gradlew.bat testDebugUnitTest assembleDebug` with `GRADLE_USER_HOME=C:\Users\SUN\Desktop\GEO\.gradle-user-home` → **BUILD SUCCESSFUL**, exit **0**. JVM: **53 tests, 0 failures, 0 errors**. Instrumented tests written, not run.

### Fixes
- **A-1:** `BillsQuery.range(CUSTOM)` returns null when start>end; no min/max swap. ViewModel falls back to the selected month.
- **A-2:** `allowBackup=false`; backup and data-extraction XML exclude the database domain. No dangerous permissions. Merged debug manifest: `allowBackup="false"`.
- **B-1:** `validateCandidateHistory` `addExact`s all-history income and expense before save/delete. The MAX-1 / expense / MAX-1 triple still has a valid running balance but is rejected at save (`error_overflow`). Home/Bills catch leftover overflow instead of crashing.
- **A-4:** `summary` sorts by date → createdAt → id before taking ending balance.
- **A-3:** Nullable unique `active_name_key` on persons/categories; inactive rows are null so deactivate/re-add can repeat with a new ID. Room **v1→v2** manual migration, no destructive fallback. Seeds/add/rename/deactivate set the key.

### Files changed
`BillsQuery.kt`, `BillsViewModel.kt`, `LedgerCalculator.kt`, `LedgerRepository.kt`, `HomeDashboard.kt`, `Entities.kt`, `GeoDatabase.kt`, `AndroidManifest.xml`, `backup_rules.xml`, `data_extraction_rules.xml`, `app/build.gradle.kts`, schema `2.json`, plus JVM/instrumented tests and `docs\agent_c_round1_fixes.md`.

### Remaining risks
- Instrumented unique-index, migration/reopen, and repository overflow saves are unrun until a device/emulator is available.
- `MoneyParser` still accepts `MAX-1` cents; the repository aggregate check is the gate.
- Pre-fix DBs with two `MAX-1` incomes are not rewritten; Home/Bills zero period totals instead of crashing.
- Migration soft-deactivates extra duplicate *active* names (`MIN(id)` kept); rows are not deleted.
