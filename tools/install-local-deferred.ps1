param(
    [string]$LauncherRoot = $(if ($env:TROPIMON_HOME) { $env:TROPIMON_HOME } else { Join-Path $env:APPDATA '.tropimon' }),
    [int]$PollSeconds = 5
)
# By FastedCorsi. Local entry point; the managed installer validates the profile before any change.
$ErrorActionPreference = 'Stop'
$jars = @(Get-ChildItem -LiteralPath $PSScriptRoot -Filter 'TropimonWiki-*-LOCAL.jar' -File)
if ($jars.Count -ne 1) { throw 'One LOCAL JAR is required.' }
& (Join-Path $PSScriptRoot 'InstallManagedLocalMod.ps1') -SourceJar $jars[0].FullName `
    -ExpectedModId 'tropimon_wiki' -LauncherRoot $LauncherRoot -PollSeconds $PollSeconds
exit $LASTEXITCODE
