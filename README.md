# GEO

Offline personal ledger for Android. Label **GEO**, package `com.geo.ledger`. Records income and expense in **integer cents** (`Long`), never floating-point money.

[Download GEO 1.2.0 APK](https://github.com/kimo423/GEO/releases/download/v1.2.0/GEO.apk)
· [Release notes](https://github.com/kimo423/GEO/releases/tag/v1.2.0).
Install over the existing app; do not uninstall or clear storage. Published after
automated validation with user-authorized deferral of physical-device testing.

## Features and screens

- **Home:** GEO title, current Chinese date, current balance, this-month income/expense, last 5 bills, empty/error/loading states, 记一笔.
- **Bills:** day / month / year / custom range, grouped running balances. Inverted custom range is rejected (no min/max swap). Loading until first ledger observation.
- **记一笔 / Edit:** amount, person/category chips (historical + clear + explicit reselect), source, date picker (UTC epochDay), note, save lock.
- **Detail:** view, edit, delete with confirm.
- **Settings:** accounting settings, audit history, configuration/data export and import, version and manual update check.
- **Update:** ONLY a user tap on “检查更新” requests the existing GitHub repository's latest stable Release. Numeric SemVer comparison; APK asset preferred, Release page fallback. No startup/background check or automatic installation.

## Architecture

Kotlin, Jetpack Compose, Material 3, Room, Flow, ViewModel.

| Piece | Version |
|---|---|
| Android Gradle Plugin | 8.13.2 |
| Gradle wrapper | 8.13 |
| Kotlin / Compose compiler plugin | 2.2.21 |
| compileSdk / targetSdk | 36 |
| minSdk | 26 |
| Java / JVM | 17 |
| Compose BOM | 2025.12.00 |
| Room | 2.8.4 |
| Lifecycle | 2.9.4 |
| Navigation Compose | 2.9.5 |
| Coroutines | 1.10.2 |
| versionName / versionCode | 1.2.0 / 6 |

Database: `geo-ledger.db`, Room **v4**, schema export under `app/schemas/`.

## 1.2 data-safety upgrade

- Each income/expense supports 0–10 attachments. Each <=10 MiB (10,485,760 bytes),
  combined <=30 MiB (31,457,280 bytes); counted from streamed bytes, not provider metadata.
- Files are copied into GEO private `filesDir/blobs/` using random internal names.
  Deleting the original external source does not delete GEO's copy. SAF file selection
  requires no storage permission. Images have bounded internal previews; PDF/other
  formats use FileProvider and an installed viewer. Every attachment supports SAF
  “保存到设备”, including historical attachments.
- Stable transaction UUIDs; transactions logically delete. Normal balances exclude
  tombstones. Every real edit stores an immutable before/after event; delete stores
  before. Removed attachments remain accessible from history. Unchanged saves add
  no event. History is application audit, not cryptographic tamper-proof evidence.
- Explicit non-destructive Room migration 1->2->3->4 assigns each old row a unique
  UUID while preserving IDs, amounts, dates, snapshots and balance ordering. Never
  uninstall/clear app data as a migration strategy.
- `.geocfg`: persons/categories. Full replace or same-name-only replacement; the
  latter ignores unmatched imported options. Historic transaction names stay intact.
- `.geodata`: ZIP with current/deleted transactions, all audit, actual attachment
  bytes and SHA-256 manifest. Full dataset restore or same-UUID-only replacement;
  the latter ignores new imported transactions. Configuration is separate.
- Validate entire import in staging before confirmation/commit. Reject corrupt
  hashes, missing files, duplicate/conflicting IDs and dangerous ZIP paths. Single
  Room commit follows new-file promotion; rollback never overwrites existing blobs.
- INTERNET is used only for the user's explicit “检查更新” action. No accounts,
  analytics, ledger upload, broad storage permission or install permission.

See [data format and restore semantics](docs/data_format.md) for exact schema,
resource limits and compatibility rules. Keep regular exported backups: uninstall
or system “clear storage” still removes private data. Old unreferenced blobs are
retained conservatively; automatic garbage collection is not included in 1.2.

On 2026-09-09 the user explicitly authorized publishing 1.2.0 without physical-device
testing. This waives the device acceptance step; it does not turn unrun device tests
into passes. See [1.2.0 release notes](docs/release_notes_v1.2.0.md). The legacy
`version.json` is updated for older installed versions after the Release asset is ready.

## Open, build, install

1. Android Studio Ladybug+ (or SDK 36 + JDK 17).
2. Open `C:\Users\SUN\Desktop\GEO` (or the unzipped tree). Create `local.properties` with `sdk.dir=...` (not shipped).
3. Optional: `GRADLE_USER_HOME` inside the project (this machine used `C:\Users\SUN\Desktop\GEO\.gradle-user-home`).
4. JVM tests + debug APK:

```
.\gradlew.bat --no-daemon testDebugUnitTest assembleDebug
```

5. Install `app\build\outputs\apk\debug\app-debug.apk` or packaged `dist\GEO-debug.apk`:

```
adb install -r dist\GEO-debug.apk
adb shell am start -n com.geo.ledger/.MainActivity
```

Connected tests need a **hardware-accelerated** emulator or device. `Study_Android_36` did not boot here (AEHD missing).

## Data model

- `transactions`: type, `amount_cents` ≥ 1, `transaction_date` (epochDay), snapshots, optional person/category IDs, income source, note, unique `client_op_key`.
- `person_options` / `expense_categories`: name, `is_active`, unique nullable `active_name_key`.

**Balance:** sort by date, createdAt, id; running `Long` with `addExact` / `subtractExact`. Invalid amounts or epochDays yield `LedgerObservation.Invalid` (no Compose crash).

**Snapshots:** bills keep the name at last explicit person/category selection. Rename without reselect keeps the old snapshot. Soft-delete shows a historical chip that can be cleared.

**epochDay:** calendar day in UTC picker millis ↔ `LocalDate` epochDay. Out-of-range values are rejected.

## Migrations

- **1→2:** unique `active_name_key` on option tables; duplicate actives deactivated.
- **2→3:** `transactions.client_op_key` + unique index (NEW-save idempotency).
- **3→4:** permanent transaction UUIDs, tombstones, attachment/blob and immutable audit tables.
- No `fallbackToDestructiveMigration`.

## Validation for 1.2.0

Current final-run evidence is recorded in [build status](docs/build_status.md) and
[test report](docs/test_report.md). Host tests include real Room under Robolectric,
migrations from each prior schema, attachment/audit and archive round trips, malicious
imports, SemVer and Compose smoke checks. Robolectric is **not** a physical-device test.
No Android device is currently connected; SAF/viewer compatibility, process death,
on-device updates and frame smoothness remain unverified. Historical 1.1 review
reports do not approve 1.2; see the explicitly labelled upgrade review reports.

## Privacy

`allowBackup="false"`. Backup/extraction rules exclude the database domain. The only source `<uses-permission>` is `INTERNET` (GitHub update check). No location/contacts/storage permissions.

## Known boundary

Emulator `Study_Android_36` cannot boot without AEHD/Windows hypervisor. Do not treat instrumented migration as green.

Packaged APK: `dist\GEO-debug.apk`. See `docs\` for status, tests, issues, and Grok Round-3 reports.
