# GEO 1.2 test report — 2026-09-09

Final full run **127 tests, 0 failures, 0 errors, 0 skipped**, 29 suites.
`docs/upgrade_final_validation.log`: BUILD SUCCESSFUL in 34s, process exit 0.
Current XML: `app/build/test-results/testDebugUnitTest/TEST-*.xml`.

- UpgradeRepositoryTest (4): actual Room schema1/2/3→4 migration, audit/no-op/
  delete/tombstone/private bytes, active creation replay A,B→A,C, normalized options.
- ArchiveRoundTripTest (3) / ArchiveSecurityTest (3): full/same-UUID restore,
  config modes, deleted/history/10 same-name attachments, immutable conflict rollback,
  malicious ZIP matrix (including attach/ directory), own 10MiB zero-file export.
- PrivateBlobStoreTest (3), TransferPrimitivesTest (6), RoomHostSmokeTest (1),
  UpgradeUiContractsTest (2): private copy/restart, limits/strict JSON+UTF8/hash,
  real Room opening, no startup update and private provider source contracts.
- SettingsToolsViewModelTest (1): repeated preview frees previous staging, malformed
  UTF8 preserves existing preview, switching to valid config closes data preview.
  Successful data apply also clears staging; post-commit cleanup is NonCancellable.
  This test is NOT a forced process-death/cancellation-timing test.
- ReleaseVersionTest (3): numerical SemVer, bad responses/fallback, parse public
  GitHub response captured 2026-09-09. The test itself never networks.
- UpgradeComposeSmokeTest (1): host settings/history/navigation and populated
  Home/Bills/audit rendering. Native Robolectric screenshots in docs/ui-review.
- Existing calculator, money, date, option, ViewModel, navigation and old updater
  regressions all pass. Full XML enumeration is authoritative; attacks within a
  parameterized loop are not inflated into extra JUnit test counts.

lintDebug: **0 errors / 12 warnings**: GradleDependency7, ConfigurationScreenWidthHeight1,
KaptUsageInsteadOfKsp1, ObsoleteSdkInt1, UsableSpace1, UseKtx1. No warning suppressed
merely to obtain a green build. assembleDebug + AndroidTest Kotlin compilation PASS.

Read-only live GitHub GET: v1.1.3, draft=false, prerelease=false, asset GEO.apk.
This proves host access and captured response parsing, not phone network or installer.

Connected tests: **not run**; adb device list empty. Host Room/Compose is not actual
phone migration, SAF, third-party viewer, process-death or frame-performance proof.
An earlier host screenshot PixelCopy/forceRedraw attempt timed out; current smoke
uses decorView.draw and passed twice with full suites. No app smoothness claim.

## Archived 1.1 report (NOT evidence for this upgrade)

Date: 2026-09-04.

## JVM (`testDebugUnitTest`) — **81 / 0 failed**

XML under `app\build\test-results\testDebugUnitTest\` (timestamp 2026-09-04T11:23:47–48Z). All suites failures=0, errors=0, skipped=0.

| Suite | Tests |
|---|---:|
| LedgerCalculatorTest | 15 |
| AppUpdateTest | 12 |
| OptionNameValidatorTest | 7 |
| AddTransactionValidatorTest | 6 |
| Round2PoliciesTest | 6 |
| BillsViewModelTest | 5 |
| TransactionDetailUiMapperTest | 5 |
| MoneyParserTest | 4 |
| GeoDatesTest | 4 |
| ActiveOptionNameKeyTest | 4 |
| BillsQueryTest | 3 |
| AddTransactionViewModelTest | 3 |
| HomeViewModelTest | 3 |
| BackupAndPermissionManifestTest | 3 |
| HomeDashboardTest | 1 |
| **Total** | **81** |

Coverage includes cents overflow, inverted custom range, Home/Bills loading, corrupt epochDay, source/note caps, client-op helper, historical chips, GitHub update allowlist/redirects/launch swallow.

## Lint

**0 errors, 10 warnings:** GradleDependency (7), ConfigurationScreenWidthHeight (1), ObsoleteSdkInt (1), KaptUsageInsteadOfKsp (1).

## Instrumented (`connectedDebugAndroidTest`)

**0 tests executed.** Failed: `com.android.builder.testing.api.DeviceException: No connected devices!`  
**Not passed.** AVD `Study_Android_36` never reached `sys.boot_completed` (no AEHD).

## GitHub update (source review)

Reviewer A/B **PASS, zero findings** after allowlist hardening. Live HTTP to GitHub, Release asset `GEO.apk`, and device `ACTION_VIEW` **not executed**. Remote repo is empty/unpublished.
