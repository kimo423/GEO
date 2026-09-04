I'll inspect the current launcher assets and lint report, then add adaptive icons from the original logo without changing `GEO-icon-original.png`.**R2A-4 done.** Adaptive icons use the supplied GEO logo (no new mark). Manifest still points at `@mipmap/ic_launcher` and `@mipmap/ic_launcher_round`.

**Assets:** `mipmap-anydpi` adaptive XML (regular + round) with background `#12151A`, 432px inset foreground, and a white monochrome silhouette from original luminance. Legacy `mipmap-*dpi` PNGs were not rewritten. `mipmap-anydpi-v26` removed (minSdk 26). Unused `optional_hint` removed.

**SHA-256** of `GEO-icon-original.png` still  
`BB8EA2FECC57D0DD37A06285567F58928D015DD23FAF583D28D1E5CA3213EC3C`.

**Build:** `GRADLE_USER_HOME=C:\Users\SUN\Desktop\GEO\.gradle-user-home`  
`.\gradlew.bat --no-daemon lintDebug testDebugUnitTest assembleDebug`  
**BUILD SUCCESSFUL in 44s**, exit **0**. JVM tests **66 / 0 failed**. Lint **0 errors**.

**Remaining warnings (26):** GradleDependency 7, ConfigurationScreenWidthHeight 1, KaptUsageInsteadOfKsp 1, IconXmlAndPng 2, IconLauncherShape 10, IconDuplicates 5 — kept because density PNGs and deps were not churned.

Details: `docs/agent_c_icon_lint_fixes.md`.
