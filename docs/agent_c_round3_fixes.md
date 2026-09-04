# Agent C Round 3 fixes

## R3A-1
CUSTOM + inverted/invalid SavedState: `invalidCustomRange`, `range = null`, mode stays CUSTOM, no month substitution. UI shows `error_invalid_range`. Test `invertedCustomSavedStateDoesNotSwapRange`.

## R3B-1
`BillsUiState.isReady`. `stateIn` initial is `isReady = false`. No summary zeros / `empty_period_bills` until first `LedgerObservation`. Test `coldStartIsNotReadyUntilObservationEmits`.

## R3B-2
`LedgerObserver` rejects out-of-range `transactionDate`. `GeoDates.fromEpochDayOrNull` / `formatEpochDay` / `toPickerMillisOrNull` / `fromPickerMillisOrNull`. Home row, detail, editor DateField/DatePicker use them. Tests: GeoDates corrupt epoch; observer `Long.MAX_VALUE` → Invalid.

## R3B-3
Home: `!isReady && !ledgerError` → `CircularProgressIndicator` with `content_loading`.

## R3B-4
`onSourceChange` / `onNoteChange` and hydrate `take(MAX_SOURCE_LENGTH / MAX_NOTE_LENGTH)`. Covered in `amountCapTruncatesOverlongDraft`.

## R3A-2 / R3B-5
Removed density `mipmap-*dpi/ic_launcher*.png` only. Adaptive XML in `mipmap-anydpi-v26` (AAPT did not link `mipmap-anydpi` XML-only). Foreground + monochrome remain. Manifest unchanged.

`GEO-icon-original.png` SHA-256: `BB8EA2FECC57D0DD37A06285567F58928D015DD23FAF583D28D1E5CA3213EC3C`.

## Build
```
GRADLE_USER_HOME=C:\Users\SUN\Desktop\GEO\.gradle-user-home
.\gradlew.bat --no-daemon lintDebug testDebugUnitTest assembleDebug
```
**BUILD SUCCESSFUL in 1m 1s**, exit **0**.

JVM: **69 tests, 0 failures, 0 errors**. Lint: **0 errors**.

Remaining warnings (10): GradleDependency 7, ConfigurationScreenWidthHeight 1, ObsoleteSdkInt 1 (`mipmap-anydpi-v26` required for AAPT), KaptUsageInsteadOfKsp 1.

No IconLauncherShape / IconXmlAndPng. Emulator still blocked (AEHD).
