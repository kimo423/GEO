# Package the verified local upgrade without publishing or touching old deliveries.
param([string]$Name = 'GEO-v1.2.0-review.zip')
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$distRoot = (Resolve-Path -LiteralPath (Join-Path $projectRoot 'dist')).Path
if ($Name -notmatch '^GEO-[A-Za-z0-9._-]+\.zip$') { throw 'Invalid output filename' }
$zipPath = [IO.Path]::GetFullPath((Join-Path $distRoot $Name))
if ([IO.Path]::GetDirectoryName($zipPath) -ne $distRoot) { throw 'Output escaped dist' }
if (Test-Path -LiteralPath $zipPath) { throw 'Output exists; choose another -Name to preserve it' }
$manifest = Get-Content -LiteralPath (Join-Path $projectRoot 'docs/upgrade_validation_manifest.json') -Raw | ConvertFrom-Json
$apk = Join-Path $distRoot 'GEO-debug.apk'
if ((Get-FileHash -LiteralPath $apk).Hash -ne $manifest.apkSha256) { throw 'APK differs from verified build' }
foreach ($entry in $manifest.entries) {
    if ((Get-FileHash -LiteralPath (Join-Path $projectRoot $entry.path)).Hash -ne $entry.sha256) {
        throw "Source changed after verification: $($entry.path)"
    }
}
$paths = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
foreach ($entry in $manifest.entries) { [void]$paths.Add($entry.path) }
foreach ($dir in @('gradle','tools')) {
    Get-ChildItem -LiteralPath (Join-Path $projectRoot $dir) -File -Recurse | ForEach-Object {
        [void]$paths.Add([IO.Path]::GetRelativePath($projectRoot,$_.FullName).Replace('\','/'))
    }
}
foreach ($name in @('README.md','.gitignore','gradlew','gradlew.bat','gradle.properties',
    'build.gradle.kts','settings.gradle.kts','app/proguard-rules.pro','GEO-icon-original.png',
    'version.json','dist/GEO-debug.apk','docs/build_status.md','docs/test_report.md',
    'docs/known_issues.md','docs/manual_test_checklist.md','docs/delivery_manifest.md',
    'docs/data_format.md','docs/grok_implementation_review.md','docs/grok_bug_hunt.md')) {
    [void]$paths.Add($name)
}
Get-ChildItem -LiteralPath (Join-Path $projectRoot 'docs') -File -Filter 'upgrade*' | ForEach-Object { [void]$paths.Add('docs/'+$_.Name) }
Get-ChildItem -LiteralPath (Join-Path $projectRoot 'docs/prompts') -File -Filter 'upgrade*' | ForEach-Object { [void]$paths.Add('docs/prompts/'+$_.Name) }
Get-ChildItem -LiteralPath (Join-Path $projectRoot 'docs/ui-review') -File -Filter 'host-*.png' | ForEach-Object { [void]$paths.Add('docs/ui-review/'+$_.Name) }
Get-ChildItem -LiteralPath (Join-Path $projectRoot 'docs/upgrade-b-tests') -File | ForEach-Object { [void]$paths.Add('docs/upgrade-b-tests/'+$_.Name) }
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$stream = [IO.File]::Open($zipPath,[IO.FileMode]::CreateNew)
try {
    $archive = [IO.Compression.ZipArchive]::new($stream,[IO.Compression.ZipArchiveMode]::Create,$false)
    try {
        foreach ($relative in ($paths | Sort-Object)) {
            $full = [IO.Path]::GetFullPath((Join-Path $projectRoot $relative))
            if (-not $full.StartsWith($projectRoot+[IO.Path]::DirectorySeparatorChar)) { throw 'Input escaped project' }
            [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive,$full,$relative,[IO.Compression.CompressionLevel]::Optimal)
        }
        Get-ChildItem -LiteralPath (Join-Path $projectRoot 'app/build/test-results/testDebugUnitTest') -File -Filter 'TEST-*.xml' | ForEach-Object {
            [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive,$_.FullName,'docs/verification/junit/'+$_.Name,[IO.Compression.CompressionLevel]::Optimal)
        }
        [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive,(Join-Path $projectRoot 'app/build/reports/lint-results-debug.xml'),'docs/verification/lint-results-debug.xml',[IO.Compression.CompressionLevel]::Optimal)
    } finally { $archive.Dispose() }
} finally { $stream.Dispose() }
Get-FileHash -LiteralPath $zipPath
