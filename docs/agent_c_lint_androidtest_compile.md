# Agent C — lintDebug + compileDebugAndroidTestKotlin

## Command

```
GRADLE_USER_HOME=C:\Users\SUN\Desktop\GEO\.gradle-user-home
.\gradlew.bat --no-daemon lintDebug compileDebugAndroidTestKotlin
```

Working directory: `C:\Users\SUN\Desktop\GEO`

## Result (this pass)

Log: `call-138067f9-058c-416e-a0a1-2bc73c356b2f-157.log`

```
> Task :app:compileDebugAndroidTestKotlin UP-TO-DATE
> Task :app:lintDebug

BUILD SUCCESSFUL in 1m 29s
44 actionable tasks: 12 executed, 32 up-to-date
```

**Exit code: 0**

## Fixes

- `DefaultCategoryCallback` lived inside `companion object`; androidTest could not resolve `GeoDatabase.DefaultCategoryCallback`.
- Moved the callback to a nested `GeoDatabase.DefaultCategoryCallback` and added `GeoDatabase.openFileDatabase(context, name)` (same callback + `MIGRATION_1_2`/`MIGRATION_2_3` as `getInstance`).
- Seed/reopen instrumented test now calls `openFileDatabase` only (no duplicated seed SQL).
- Lint **errors** (4) also blocked `lintDebug`:
  - `RestrictedApi` `NavController.currentBackStack` → `getBackStackEntry(editorRoute)` + catch.
  - `LocalContextGetResourceValueCall` snackbars → `LocalResources.current.getString`.

## androidTest coverage (no emulator)

Already present; no extra tests added this pass:

| Topic | Test |
|---|---|
| v1→v2 unique key | `migratesV1ToUniqueActiveNameKeyWithoutDestroyingRows` |
| v1→v2→v3 + `client_op_key` | `migratesV1ToV2ToV3WithoutDestroyingTransactions` |
| default seed + reopen | `fileDatabaseSeedsFiveDefaultCategoriesAndZeroPersons` |
| snapshot / soft-delete | `personAndCategorySnapshotsSurviveRenameAndSoftDelete` |
| re-add new ids | `deactivateThenReAddUsesNewIdAndAllowsRepeatedCycles` |
| persist/reopen | `fileDatabasePersistsAcrossCloseAndReopen` |
| client_op_key idempotency | `clientOpKeyReplayIsIdempotent` |

## Remaining lint warnings (21, 0 errors)

Report: `app/build/reports/lint-results-debug.html`

- ConfigurationScreenWidthHeight — `BillsScreen.kt:384`
- ObsoleteSdkInt — `mipmap-anydpi-v26`
- KaptUsageInsteadOfKsp — Room compiler
- UnusedResources — `optional_hint`
- IconLauncherShape ×10 (square + round mipmaps)
- MonochromeLauncherIcon ×2
- IconDuplicates ×5 (`ic_launcher` == `ic_launcher_round`)
