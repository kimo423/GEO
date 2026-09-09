# GEO 1.2 known issues — 2026-09-09

- No Android device detected. Real SAF selection/save, third-party PDF viewer,
  process-death recovery, browser/update flow and frame smoothness need device
  acceptance. Host rendering does not prove smooth animation.
- Existing legacy case-equivalent active options are preserved. New UI/repository
  writes reject NFC+trim+ASCII-case equivalent names. Ambiguous legacy names cause
  safe import refusal; rename/deactivate one in Settings, never silent merging.
- Abandoned immutable blobs are retained conservatively. Staging older than 24h
  is cleaned; no blob garbage collector. Uninstall/clear storage loses private data.
- Archive limits: 1 GiB input, 2 GiB expanded, 32 MiB JSON, 20,000 transactions,
  50,000 audit events and 100,000 entries; excess is rejected.
- Audit is application-level history, not tamper-proof forensic evidence.
- Legacy version.json adapter is retained for regression compatibility, not mounted
  in navigation, and never requests updates automatically on construction.
- Debug signing certificate retained for upgrade compatibility; publishing uses a
  non-debuggable Release build. User explicitly waived device acceptance and
  authorized publication; unrun device tests remain unverified.

Round3's single low-risk post-commit staging cleanup window was fixed using
NonCancellable IO; full suite rerun passed. Actual process death can still leave
temporary/orphan files, handled conservatively as described above. No claimed
phone/process-death gate. Final reviewer counts are in the current review addenda.

## Archived 1.1 issues (obsolete snapshot, NOT current gate status)

The old empty-repository statement below is obsolete; September 6 read-only check
returned v1.1.3. Historical approvals below do not approve the 1.2 upgrade.

## Publication / device

- **Remote GitHub repo empty.** `https://github.com/kimo423/GEO` has no `main/version.json` and no Release asset `GEO.apk`. In-app update check cannot succeed until those exist.
- **Live/device update unverified:** TLS fetch, redirects, dialog, `ACTION_VIEW` install, no-browser devices.
- **No accelerated emulator.** `connectedDebugAndroidTest` ran **0 tests** and **failed** (`No connected devices!`). Not an APK functional defect.
- APK in this delivery is **debug-signed**. Future Play/sideload upgrades must keep the **same certificate**.

## Remaining lint (warnings only)

- GradleDependency ×7
- ConfigurationScreenWidthHeight ×1
- ObsoleteSdkInt ×1 (`mipmap-anydpi-v26`)
- KaptUsageInsteadOfKsp ×1

## Closed (do not regress)

Round-3 ledger/UI findings; GitHub update allowlist/redirect/launch/docs findings. Reviewer A/B for update: **PASS, zero findings**.
