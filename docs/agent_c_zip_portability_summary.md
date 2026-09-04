I'll change the packager to write ZIP entries with `/` separators, then rebuild the zip and recheck hashes.**ZIP portability fixed.** `package_delivery.ps1` now writes entries with `ZipArchive`/`CreateEntry` and `/` names.

`C:\Users\SUN\Desktop\GEO\dist\GEO-delivery.zip` — **21,189,051** bytes  
SHA-256 `AE465A165FB26B1BAF82A0F4E5B23231A05B1742E0A58F63FB86E3F709655223`

**Checks:** **167** entries, **0** backslashes, `version.json` present, `dist/GEO.apk` present, **0** cache/`app/build`/`.idea`/`docs/logs`/staging/zip-self hits. Staging removed.

**Unchanged:** `GEO.apk` / `GEO-debug.apk` SHA-256 `3004B42684B2E568BE534BA035F1288840054CF381088BF3FB0625CA97571D72`; icon `BB8EA2FECC57D0DD37A06285567F58928D015DD23FAF583D28D1E5CA3213EC3C`. `SHA256SUMS.txt` regenerated. No app/docs/git/remote changes.
