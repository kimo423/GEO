I'll inspect the app startup path, manifest tests, and UI shell, then add a dependency-free GitHub update check with JVM coverage.**GitHub update check is in.** Local app is **versionCode 2 / versionName 1.1.0**. Root `version.json` matches, with APK URL `https://github.com/kimo423/GEO/releases/latest/download/GEO.apk`.

**Behavior:** Activity-scoped `AppUpdateViewModel` fetches `https://raw.githubusercontent.com/kimo423/GEO/main/version.json` once on cold start (`HttpURLConnection`, IO dispatcher, 8s timeouts, no cache, 8KB cap, HTTPS GitHub-only redirects). JSON is strictly validated. If remote `versionCode` is greater, one Material 3 dialog shows the version and notes with **稍后更新** (session dismiss) and **立即更新** (`ACTION_VIEW` to the validated Release APK; no handler = silent). Network/parse/launch failures never block GEO; `CancellationException` is not swallowed. Ledger, Room, backup-off, and the original icon are unchanged. Manifest adds only **INTERNET** (plus HTTPS VIEW `queries`).

**Build:** `GRADLE_USER_HOME=C:\Users\SUN\Desktop\GEO\.gradle-user-home`  
`.\gradlew.bat --no-daemon testDebugUnitTest lintDebug assembleDebug`  
**BUILD SUCCESSFUL**, exit **0**. JVM **77 / 0 failed**. Lint **0 errors**, 11 warnings.

**Docs:** `docs/github_update.md`, `docs/agent_c_github_update.md`. No git init/commit/push and no remote Release.

**Unverified:** live GitHub fetch, browser install, no-browser devices, dialog vs IME/nav. Remote still needs `main/version.json` plus a Release asset named **GEO.apk**.
