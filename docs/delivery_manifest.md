# Delivery manifest

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
