param([Parameter(Mandatory=$true)][string]$Jar)
# By FastedCorsi. Synthetic managed profiles only, never the real launcher.
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSHOME 'Modules/Microsoft.PowerShell.Utility') -Force
Import-Module (Join-Path $PSHOME 'Modules/Microsoft.PowerShell.Management') -Force
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$fixture = Join-Path ([IO.Path]::GetTempPath()) ('tropimon-managed-test-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $fixture | Out-Null
$source = Join-Path $fixture 'TropimonWiki-test-LOCAL.jar'
Copy-Item -LiteralPath $Jar -Destination $source
$hash = (Get-FileHash -LiteralPath $source).Hash
[IO.File]::WriteAllText(($source + '.sha256'), $hash)
# The player installer is now Java; UpdaterSafetyTest executes its actual exported helper.
# Keep these separate regression checks for the external local-delivery PowerShell tool.

function Assert([bool]$Condition, [string]$Message) {
    if (!$Condition) { throw $Message }
    Write-Output ('MANAGED_CHECK: ' + $Message)
}
function Fixture-Jar([string]$Path, [string]$Id, [string]$Version) {
    $zip = [IO.Compression.ZipFile]::Open($Path, [IO.Compression.ZipArchiveMode]::Create)
    try {
        $writer = [IO.StreamWriter]::new($zip.CreateEntry('fabric.mod.json').Open())
        try { $writer.Write((@{id=$Id;version=$Version;authors=@('By FastedCorsi')} | ConvertTo-Json)) }
        finally { $writer.Dispose() }
    } finally { $zip.Dispose() }
}
function Run-Installer([string]$Expected, [string[]]$Extra = @()) {
    $output = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $installer -SourceJar $source -ExpectedModId tropimon_wiki -LauncherRoot $instance @Extra
    $result = ($output | Select-Object -Last 1) | ConvertFrom-Json
    Assert ($result.state -eq $Expected) ('Installer state: ' + $Expected + '; actual: ' + $result.state + '; error type: ' + $result.errorType)
}
foreach ($installer in @((Join-Path $PSScriptRoot 'InstallManagedLocalMod.ps1'))) {
    $profile = Join-Path $fixture ([guid]::NewGuid().ToString('N'))
    $instance = Join-Path $profile 'instance'
    $mods = Join-Path $instance 'mods'
    $managed = Join-Path $instance 'mods-user'
    New-Item -ItemType Directory -Path $mods,$managed | Out-Null
    $tracker = Join-Path $profile 'user-mods-tracked.json'
    [IO.File]::WriteAllText($tracker, '["old-wiki.jar","unrelated.jar","disabled.jar"]')
    foreach ($dir in @($mods,$managed)) { Fixture-Jar (Join-Path $dir 'old-wiki.jar') 'tropimon_wiki' '0.0.1' }
    Fixture-Jar (Join-Path $mods 'unrelated.jar') 'fixture_other' '1.0.0'
    Fixture-Jar (Join-Path $managed 'disabled.jar') 'fixture_disabled' '1.0.0'
    $otherHash = (Get-FileHash -LiteralPath (Join-Path $mods 'unrelated.jar')).Hash
    $disabledHash = (Get-FileHash -LiteralPath (Join-Path $managed 'disabled.jar')).Hash
    Run-Installer 'installed'
    $target = Join-Path $mods (Split-Path -Leaf $source)
    $imported = Join-Path $managed (Split-Path -Leaf $source)
    Assert ((Get-FileHash -LiteralPath $target).Hash -eq $hash -and (Get-FileHash -LiteralPath $imported).Hash -eq $hash) 'Both copies verified'
    $parsed = Get-Content -LiteralPath $tracker -Raw | ConvertFrom-Json
    $tracked = @($parsed)
    Assert ($tracked.Count -eq 3 -and 'old-wiki.jar' -notin $tracked -and (Split-Path -Leaf $source) -in $tracked) 'Only delivered mod tracking replaced'
    Assert ((Get-FileHash -LiteralPath (Join-Path $mods 'unrelated.jar')).Hash -eq $otherHash) 'Unrelated loaded mod preserved'
    Assert ((Get-FileHash -LiteralPath (Join-Path $managed 'disabled.jar')).Hash -eq $disabledHash -and !(Test-Path (Join-Path $mods 'disabled.jar'))) 'Disabled mod stays disabled'
    Assert (@(Get-ChildItem (Join-Path $instance 'mod-archive') -Recurse -Filter old-wiki.jar).Count -eq 2) 'Both previous copies backed up'
    Run-Installer 'installed'
    Assert (@(Get-ChildItem (Join-Path $instance 'mod-archive') -Directory).Count -eq 1) 'Identical install is idempotent'
    $locked = [IO.File]::Open($target, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::None)
    try { Run-Installer 'blocked' } finally { $locked.Dispose() }
    Assert ((Get-FileHash -LiteralPath $target).Hash -eq $hash) 'Locked target is preserved'
    [IO.File]::WriteAllText(($source + '.sha256'), ('0' * 64))
    Run-Installer 'blocked'
    [IO.File]::WriteAllText(($source + '.sha256'), $hash)
    [IO.File]::WriteAllText($tracker, '{"unknown":[]}')
    Run-Installer 'blocked'
    [IO.File]::WriteAllText($tracker, (ConvertTo-Json -InputObject $tracked))
    Copy-Item -LiteralPath $source -Destination (Join-Path $mods 'duplicate.jar')
    Run-Installer 'blocked'
    # Move only the exact synthetic fixture file, never a computed recursive target.
    Move-Item -LiteralPath (Join-Path $mods 'duplicate.jar') -Destination (Join-Path $profile 'duplicate.jar')
    $saved = Join-Path $profile 'before-newer.jar'
    Move-Item -LiteralPath $target -Destination $saved
    Fixture-Jar $target 'tropimon_wiki' '99.0.0'
    Run-Installer 'blocked'
    Assert ((Get-FileHash -LiteralPath $imported).Hash -eq $hash) 'Blocked installs preserve imported copy'
}
Write-Output 'External managed delivery transactions passed.'
