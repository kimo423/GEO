I'll implement date-safe helpers, Bills/Home loading and invalid-custom states, input caps, then drop conflicting density launcher PNGs.**BUILD SUCCESSFUL** (`exit 0`, 1m 1s) for `lintDebug testDebugUnitTest assembleDebug`. JVM **69 tests, 0 failures**. Lint **0 errors**.

**R3A-1:** Restored inverted CUSTOM stays CUSTOM with `invalidCustomRange`, `range = null`, `error_invalid_range` — no month substitution.

**R3B-1:** Bills `isReady`; cold `stateIn` is not a successful empty ¥0 period.

**R3B-2:** `LedgerObserver` rejects bad `epochDay`. Safe `GeoDates` helpers used on Home/detail/editor pickers. `Long.MAX_VALUE` → Invalid, no throw.

**R3B-3:** Home shows an accessible loading indicator while `!isReady && !ledgerError`.

**R3B-4:** Source/note truncated to `MAX_SOURCE_LENGTH` / `MAX_NOTE_LENGTH` on input and hydrate.

**R3A-2 / R3B-5:** Density launcher PNGs removed. Adaptive XML + foreground/monochrome kept. AAPT required `mipmap-anydpi-v26` (plain `mipmap-anydpi` XML did not link). Original SHA-256 still `BB8EA2FECC57D0DD37A06285567F58928D015DD23FAF583D28D1E5CA3213EC3C`.

**Remaining lint (10):** GradleDependency 7, ConfigurationScreenWidthHeight 1, ObsoleteSdkInt 1, KaptUsageInsteadOfKsp 1. No IconLauncherShape / IconXmlAndPng.

Emulator/connected tests still blocked (no AEHD). Details: `docs/agent_c_round3_fixes.md`.
