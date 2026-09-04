I'll hoist the seed callback out of the companion so androidTest can reuse the production Room builder, then recompile lint and androidTest.**BUILD SUCCESSFUL** (`exit 0`) for `GRADLE_USER_HOME=C:\Users\SUN\Desktop\GEO\.gradle-user-home` and `.\gradlew.bat --no-daemon lintDebug compileDebugAndroidTestKotlin` (1m 29s).

**androidTest:** `GeoDatabase.DefaultCategoryCallback` was nested in the companion object, so the seed test could not resolve it. The callback is now a class nested type. Production `getInstance` and tests share `GeoDatabase.openFileDatabase()` (same callback + `MIGRATION_1_2`/`MIGRATION_2_3`). Seed/reopen uses that helper only.

**Lint errors also fixed** so `lintDebug` could pass: editor stack check uses public `getBackStackEntry` instead of restricted `currentBackStack`; snackbars use `LocalResources.current.getString`.

**Existing instrumented tests already cover** v1→v2, v1→v2→v3, default seed/reopen, snapshot/soft-delete, re-add, persist/reopen, and `client_op_key` replay. No extra tests added. Emulator not started.

**Remaining lint:** 0 errors, **21 warnings** (picker `screenHeightDp`, `mipmap-anydpi-v26`, kapt vs KSP, unused `optional_hint`, launcher shape/duplicates/monochrome). Details in `docs/agent_c_lint_androidtest_compile.md`.
