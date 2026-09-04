# Agent C — GitHub update security/UI/docs fixes

## Closed

| ID | Action |
|---|---|
| UA-UPD-01 | **Not a defect.** Notes stay nonblank + length cap. Language is publisher policy (`docs/github_update.md`). |
| Fetch High | Exact canonical allowlist; reject `.`/`..`/`//`/`%` encoding, userInfo, query, fragment, non-443 ports, `GEOevil`/`GEO.git` prefixes. |
| Fetch Medium | Fetch/redirect only `main/version.json` and `refs/heads/main/version.json`. One URI canonicalizer + `URL` agreement check. Relative Location via `URI.resolve`. Reject HTTP, `//` protocol-relative, malformed/`\`. Cap 2 redirects. |
| APK Low | Host `github.com` only; no www/query/port/userInfo/fragment; exact `/kimo423/GEO/releases/latest/download/GEO.apk` or tagged `download/<tag>/GEO.apk`. |
| UI Low | Notes in `heightIn(280.dp)` + `verticalScroll`; buttons stay outside the scroll. |
| UA-UPD-02 | `AppUpdateLaunch.open` catches `RuntimeException` (ANFE/SecurityException included); does not catch `Error`. |
| UA-UPD-03 | README / build_status / delivery_manifest → 1.1.0(2), INTERNET-only update. |

## Build

`.\gradlew.bat --no-daemon testDebugUnitTest lintDebug assembleDebug` → **BUILD SUCCESSFUL**, JVM **81 / 0 failed**, lint **0 errors / 10 warnings**.

## Tests added

Exact fetch URLs, sibling prefixes, `../` and `%2e%2e`, `//`, query/port/www/userInfo/fragment, downgrade/protocol-relative/relative redirect, redirect hops, APK query/port/www, long notes, launch swallow.

## Unverified

Live GitHub GET, real 302, TLS, device `ACTION_VIEW`, no-browser devices.
