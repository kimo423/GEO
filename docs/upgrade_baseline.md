# GEO upgrade baseline (2026-09-05)

- Project: C:\Users\SUN\Desktop\GEO (existing, not recreated).
- Starting Git: main...origin/main, clean. Origin https://github.com/kimo423/GEO.git.
- App: com.geo.ledger, 1.1.3 / versionCode 5, minSdk 26, target/compileSdk 36.
- Room: 3; exported 1.json, 2.json, 3.json; explicit 1->2->3 migrations.
- Architecture: Kotlin / Compose Material3 / Room / ViewModel; cents are Long.
- Current transactions physically delete; options deactivate; audit/attachments not present.
- Current updater automatically requests version.json; upgrade changes to manual Release check.
- Original PNG SHA256: BB8EA2FECC57D0DD37A06285567F58928D015DD23FAF583D28D1E5CA3213EC3C.
- Existing tests: JVM domain/viewmodels and Android Room/migration sources. Existing docs/dist present.
- Baseline testDebugUnitTest: Gradle successful (UP-TO-DATE cache, not fresh execution), log upgrade_baseline_test.log.
- ADB: no connected devices. Emulator acceleration check exit 6: hypervisor driver not installed.
- Existing dist/GEO-debug.apk (19,757,468 bytes) and GEO.apk (13,000,422 bytes) are PRE-UPGRADE artifacts.
- New architecture review reports will be separate from old grok reports until final round.
- No remote write authorized for this upgrade. No push or release will be performed.

Technical references consulted for planned host-side Room testing and file sharing:
[Robolectric setup](https://robolectric.org/getting-started/),
[Android secure file sharing](https://developer.android.com/training/secure-file-sharing).

Pending: architecture review clearance, implementation, fresh tests, final APK and reviews.
