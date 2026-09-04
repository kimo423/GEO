# Copies the current debug APK to dist\GEO.apk and dist\GEO-debug.apk,
# then builds dist\GEO-delivery.zip and dist\SHA256SUMS.txt.
# Staging lives under the GEO tree and is deleted after a successful zip.
$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$Dist = Join-Path $Root "dist"
$Stage = Join-Path $Root ".delivery-stage"
$ApkSrc = Join-Path $Root "app\build\outputs\apk\debug\app-debug.apk"
$Icon = Join-Path $Root "GEO-icon-original.png"
$VersionJson = Join-Path $Root "version.json"
$ExpectedIcon = "BB8EA2FECC57D0DD37A06285567F58928D015DD23FAF583D28D1E5CA3213EC3C"

if (-not (Test-Path $ApkSrc)) {
    throw "Missing debug APK: $ApkSrc — run assembleDebug first."
}

New-Item -ItemType Directory -Force -Path $Dist | Out-Null
Copy-Item -Force $ApkSrc (Join-Path $Dist "GEO.apk")
Copy-Item -Force $ApkSrc (Join-Path $Dist "GEO-debug.apk")

$iconHash = (Get-FileHash -Algorithm SHA256 $Icon).Hash.ToUpperInvariant()
if ($iconHash -ne $ExpectedIcon) {
    throw "GEO-icon-original.png hash $iconHash does not match $ExpectedIcon"
}

if (Test-Path $Stage) { Remove-Item -Recurse -Force $Stage }
New-Item -ItemType Directory -Force -Path $Stage | Out-Null

function Copy-Tree($from, $to) {
    New-Item -ItemType Directory -Force -Path $to | Out-Null
    Copy-Item -Path (Join-Path $from "*") -Destination $to -Recurse -Force
}

Copy-Tree (Join-Path $Root "app\src") (Join-Path $Stage "app\src")
Copy-Tree (Join-Path $Root "app\schemas") (Join-Path $Stage "app\schemas")
Copy-Item -Force (Join-Path $Root "app\build.gradle.kts") (Join-Path $Stage "app\build.gradle.kts")
Copy-Item -Force (Join-Path $Root "app\proguard-rules.pro") (Join-Path $Stage "app\proguard-rules.pro")
Copy-Tree (Join-Path $Root "gradle") (Join-Path $Stage "gradle")
Copy-Tree (Join-Path $Root "tools") (Join-Path $Stage "tools")
foreach ($f in @(
    "build.gradle.kts", "settings.gradle.kts", "gradle.properties",
    "gradlew", "gradlew.bat", ".gitignore", "README.md",
    "GEO-icon-original.png", "version.json"
)) {
    Copy-Item -Force (Join-Path $Root $f) (Join-Path $Stage $f)
}

$DocsOut = Join-Path $Stage "docs"
New-Item -ItemType Directory -Force -Path $DocsOut | Out-Null
$DocNames = @(
    "grok_implementation_review.md",
    "grok_bug_hunt.md",
    "github_update.md",
    "github_update_review.md",
    "github_update_bug_hunt.md",
    "agent_c_github_update.md",
    "agent_c_github_update_fixes.md",
    "agent_c_round3_fixes.md",
    "emulator_test_report.md",
    "build_status.md",
    "test_report.md",
    "known_issues.md",
    "manual_test_checklist.md",
    "delivery_manifest.md",
    "requirements.txt"
)
foreach ($name in $DocNames) {
    $p = Join-Path $Root "docs\$name"
    if (Test-Path $p) { Copy-Item -Force $p (Join-Path $DocsOut $name) }
}

New-Item -ItemType Directory -Force -Path (Join-Path $Stage "dist") | Out-Null
Copy-Item -Force (Join-Path $Dist "GEO.apk") (Join-Path $Stage "dist\GEO.apk")

$Zip = Join-Path $Dist "GEO-delivery.zip"
if (Test-Path $Zip) { Remove-Item -Force $Zip }
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zipStream = [System.IO.File]::Open($Zip, [System.IO.FileMode]::CreateNew)
try {
    $archive = New-Object System.IO.Compression.ZipArchive(
        $zipStream,
        [System.IO.Compression.ZipArchiveMode]::Create,
        $false
    )
    try {
        $stagePrefix = $Stage.TrimEnd('\', '/')
        Get-ChildItem -LiteralPath $Stage -Recurse -Force | ForEach-Object {
            $relative = $_.FullName.Substring($stagePrefix.Length).TrimStart('\', '/')
            $entryName = ($relative -replace '\\', '/')
            if ($_.PSIsContainer) {
                if (-not $entryName.EndsWith('/')) { $entryName += '/' }
                [void]$archive.CreateEntry($entryName)
            } else {
                [void][System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
                    $archive,
                    $_.FullName,
                    $entryName,
                    [System.IO.Compression.CompressionLevel]::Optimal
                )
            }
        }
    } finally {
        $archive.Dispose()
    }
} finally {
    $zipStream.Dispose()
}

Remove-Item -Recurse -Force $Stage

function Hash-File([string]$path) {
    (Get-FileHash -Algorithm SHA256 $path).Hash.ToUpperInvariant()
}

$apkHash = Hash-File (Join-Path $Dist "GEO.apk")
$debugHash = Hash-File (Join-Path $Dist "GEO-debug.apk")
$zipHash = Hash-File $Zip
$verHash = Hash-File $VersionJson
@(
    "$apkHash  GEO.apk"
    "$debugHash  GEO-debug.apk"
    "$zipHash  GEO-delivery.zip"
    "$verHash  version.json"
    "$iconHash  GEO-icon-original.png"
) | Set-Content -Encoding ascii (Join-Path $Dist "SHA256SUMS.txt")

Write-Output "PACKAGED"
Write-Output "GEO.apk=$apkHash"
Write-Output "ZIP=$Zip"
Write-Output "ICON=$iconHash"
