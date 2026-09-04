# GEO

Offline personal ledger for Android. Label **GEO**, package `com.geo.ledger`. Records income and expense in **integer cents** (`Long`), never floating-point money.

## Features and screens

- **Home:** GEO title, current Chinese date, current balance, this-month income/expense, last 5 bills, empty/error/loading states, 记一笔.
- **Bills:** day / month / year / custom range, grouped running balances. Inverted custom range is rejected (no min/max swap). Loading until first ledger observation.
- **记一笔 / Edit:** amount, person/category chips (historical + clear + explicit reselect), source, date picker (UTC epochDay), note, save lock.
- **Detail:** view, edit, delete with confirm.
- **Settings:** add / rename / soft-delete persons and expense categories.
- **Update:** on cold start, HTTPS fetch of GitHub `version.json`. Newer `versionCode` shows one dialog (稍后更新 / 立即更新).

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
| versionName / versionCode | 1.1.0 / 2 |

Database: `geo-ledger.db`, Room **v3**, schema export under `app/schemas/`.

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
- No `fallbackToDestructiveMigration`.

## Tests (this delivery)

- JVM `testDebugUnitTest`: **81 tests, 0 failures**.
- `lintDebug`: **0 errors, 10 warnings**.
- `assembleDebug`: success (`com.geo.ledger` 1.1.0 / versionCode 2).
- `connectedDebugAndroidTest`: **0 tests run**, failed with `No connected devices!` — **not passed**.
- GitHub update reviewer A/B: **PASS, zero findings** (`docs/github_update_review.md`, `docs/github_update_bug_hunt.md`). Live fetch/install **unverified**; remote `kimo423/GEO` is empty/unpublished.

## Privacy

`allowBackup="false"`. Backup/extraction rules exclude the database domain. The only source `<uses-permission>` is `INTERNET` (GitHub update check). No location/contacts/storage permissions.

## Known boundary

Emulator `Study_Android_36` cannot boot without AEHD/Windows hypervisor. Do not treat instrumented migration as green.

Packaged APK: `dist\GEO-debug.apk`. See `docs\` for status, tests, issues, and Grok Round-3 reports.
