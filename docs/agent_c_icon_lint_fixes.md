# Agent C — R2A-4 adaptive icon + lint slice

## Original logo SHA-256

`GEO-icon-original.png` **unchanged**:

`BB8EA2FECC57D0DD37A06285567F58928D015DD23FAF583D28D1E5CA3213EC3C`

Matches the recorded original. Generator never writes this file.

## Adaptive launcher (R2A-4)

Manifest still uses `@mipmap/ic_launcher` and `@mipmap/ic_launcher_round`.

| Asset | Role |
|---|---|
| `mipmap-anydpi/ic_launcher.xml` | Adaptive (minSdk 26; not `-v26`) |
| `mipmap-anydpi/ic_launcher_round.xml` | Same layers for roundIcon |
| `drawable-xxxhdpi/ic_launcher_foreground.png` | 432px RGBA, inner 288px safe zone from original |
| `drawable-xxxhdpi/ic_launcher_monochrome.png` | White silhouette from original luminance |
| `@color/ic_launcher_background` `#12151A` | Matches source dark field |
| `mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher*.png` | **Unchanged** legacy fallbacks |

`tools/generate_launcher_icons.py` now only writes adaptive layers.

Removed unused `R.string.optional_hint`. Removed obsolete `mipmap-anydpi-v26` (ObsoleteSdkInt / MonochromeLauncherIcon).

## Build

```
GRADLE_USER_HOME=C:\Users\SUN\Desktop\GEO\.gradle-user-home
.\gradlew.bat --no-daemon lintDebug testDebugUnitTest assembleDebug
```

**BUILD SUCCESSFUL in 44s**, exit **0**. JVM tests **66 / 0 failed**. Lint **0 errors**.

## Remaining lint warnings (26, 0 errors)

Left on purpose (legacy density PNGs, deps, Compose):

| ID | Count | Why kept |
|---|---:|---|
| GradleDependency | 7 | No version churn |
| ConfigurationScreenWidthHeight | 1 | Bills picker `screenHeightDp` |
| KaptUsageInsteadOfKsp | 1 | Room kapt stays |
| IconXmlAndPng | 2 | Adaptive XML + kept density bitmaps |
| IconLauncherShape | 10 | Legacy full-bleed square; round copies not circular |
| IconDuplicates | 5 | Round PNGs still identical to square |

Cleared vs prior 21: UnusedResources (`optional_hint`), ObsoleteSdkInt (`mipmap-anydpi-v26`), MonochromeLauncherIcon.
