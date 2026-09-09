# GEO 1.2 local upgrade manifest — 2026-09-09

This is the pre-publication DEBUG review package snapshot. User subsequently
authorized publishing a Release APK without device acceptance; see
github_publish_report_v1.2.0.md for the separately signed GEO.apk and remote results.

Current artifact: `dist/GEO-debug.apk`, app1.2.0/code6, 20,178,362 bytes.
SHA256 `712C7192258F1C6D1DCF110E4FE68C7829D6B649847CF1FDBF5E7A2C811F0CD9`.
The 109-file source/test/schema fingerprint and validation metadata are in
`docs/upgrade_validation_manifest.json`. Full 127-test run and lint/assemble exit0.
Final review and phone-gate status: `docs/upgrade_delivery_report.md`.

Use `tools/package_upgrade.ps1` for a versioned review ZIP; it checks source/APK
hashes, includes current reports, host screenshots, JUnit/lint evidence, and refuses
to overwrite existing ZIPs. No staging deletion or remote write. Excludes SDK,
caches, app build binaries except the verified APK, credentials/keystores and .git.
Captured public GitHub JSON is included for the offline test; version.json remains
the published1.1.3 manifest, NOT a claim that local1.2.0 has been uploaded.

Old `dist/GEO.apk`, `GEO-delivery.zip` and `SHA256SUMS.txt` are historical. The old
package_delivery.ps1 retains the old packaging workflow; do not use it for this review.

## Archived 1.1 manifest (not current identity/status)

Package root: `C:\Users\SUN\Desktop\GEO`

Identity: **GEO 1.1.0 (versionCode 2)**, `com.geo.ledger`, minSdk 26.

| Artifact | Path |
|---|---|
| Release-named APK | `dist\GEO.apk` |
| Debug alias | `dist\GEO-debug.apk` (same bytes as `GEO.apk`) |
| Delivery ZIP | `dist\GEO-delivery.zip` |
| Checksums | `dist\SHA256SUMS.txt` |
| Manifest for GitHub | `version.json` |
| Original logo | `GEO-icon-original.png` |
| Update reviews | `docs\github_update_review.md`, `docs\github_update_bug_hunt.md` |

ZIP includes: app source + tests + Room schemas, Gradle wrapper/config, `tools`, `version.json`, `README.md`, concise docs (status, tests, issues, checklist, github_update + reviews, Grok R3 reports), original PNG, `dist\GEO.apk`.

ZIP excludes: `.gradle-user-home`, `.gradle`, `.kotlin`, `app/build`, `.idea`, `docs/logs`, `docs/runtime`, `docs/screenshots`, staging, `local.properties`, the ZIP itself.

APK is **debug-signed**. Keep the same cert for upgrades. No keystore was generated.

Remote publication: **not done** (no push, no Release). Update reviewers: **PASS, zero findings**.

Original icon SHA-256:

`BB8EA2FECC57D0DD37A06285567F58928D015DD23FAF583D28D1E5CA3213EC3C`

Git: **not a repository**. No commit/push.
