# Known issues

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
