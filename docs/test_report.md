# Test report

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
