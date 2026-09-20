param([Parameter(Mandatory=$true)][string]$Jar)
# Import the engine's built-in modules explicitly, including when launched by a build daemon.
Import-Module (Join-Path $PSHOME 'Modules/Microsoft.PowerShell.Utility/Microsoft.PowerShell.Utility.psd1') -ErrorAction Stop
Import-Module (Join-Path $PSHOME 'Modules/CimCmdlets/CimCmdlets.psd1') -ErrorAction Stop
$ErrorActionPreference='Stop'
$fixture=Join-Path ([IO.Path]::GetTempPath()) ('tropimon-deployment-test-'+[guid]::NewGuid().ToString('N'))
$instance=Join-Path $fixture 'instance'
$delivery=Join-Path $fixture 'delivery'
New-Item -ItemType Directory -Path (Join-Path $instance 'mods'),$delivery | Out-Null
$script=Join-Path $delivery 'install-local-deferred.ps1'
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'install-unmanaged-deferred.ps1') -Destination $script
$source=Join-Path $delivery 'TropimonWiki-0.1.0+1.21.1-LOCAL.jar'
Copy-Item -LiteralPath $Jar -Destination $source
$hash=(Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash
[IO.File]::WriteAllText($source+'.sha256',$hash)
# The isolated fixture has no running game. No real process or launcher is touched.
function Get-CimInstance { @() }
& $script -LauncherRoot $instance
$state=Get-Content -LiteralPath (Join-Path $delivery 'install-status.json') -Raw | ConvertFrom-Json
if($state.state -ne 'installed'){throw 'Clean installation failed'}
$installed=Join-Path $instance ('mods/'+(Split-Path $source -Leaf))
if((Get-FileHash -LiteralPath $installed -Algorithm SHA256).Hash -ne $hash){throw 'Installed hash mismatch'}
& $script -LauncherRoot $instance
$state=Get-Content -LiteralPath (Join-Path $delivery 'install-status.json') -Raw | ConvertFrom-Json
if($state.state -ne 'installed'){throw 'Replacement failed'}
if(@(Get-ChildItem -LiteralPath (Join-Path $instance 'mod-archive') -Recurse -Filter '*.jar' -File).Count -lt 1){throw 'Backup missing'}
# A corrupt staged file must not replace the verified installation.
[IO.File]::WriteAllText($source+'.sha256',('0'*64))
& $script -LauncherRoot $instance
$state=Get-Content -LiteralPath (Join-Path $delivery 'install-status.json') -Raw | ConvertFrom-Json
if($state.state -ne 'blocked'){throw 'Corruption accepted'}
if((Get-FileHash -LiteralPath $installed -Algorithm SHA256).Hash -ne $hash){throw 'Existing JAR changed'}
# A target changed after the preparation snapshot must also be preserved.
[IO.File]::WriteAllText($source+'.sha256',$hash)
$script:mutationDone=$false
function Get-CimInstance {
 if(!$script:mutationDone){
  $script:mutationDone=$true
  $stream=[IO.File]::Open($installed,[IO.FileMode]::Append,[IO.FileAccess]::Write)
  try{$stream.WriteByte(0)}finally{$stream.Dispose()}
 }
 @()
}
& $script -LauncherRoot $instance
$state=Get-Content -LiteralPath (Join-Path $delivery 'install-status.json') -Raw | ConvertFrom-Json
if($state.state -ne 'blocked'){throw 'Concurrent target modification was not blocked'}
if((Get-FileHash -LiteralPath $installed -Algorithm SHA256).Hash -eq $hash){throw 'Changed target was overwritten'}
Write-Output 'Deployment tests passed: install, backup, corruption and changed-target rejection.'
function Get-CimInstance { @() }
$profileRoot = Join-Path $fixture 'profile-launcher'
$profileInstance = Join-Path $profileRoot 'profiles/stable/instance'
New-Item -ItemType Directory -Path (Join-Path $profileRoot 'mods'),(Join-Path $profileInstance 'mods') -Force | Out-Null
$mirror = Join-Path $profileRoot 'mods/launcher-mirror.jar'
Copy-Item -LiteralPath $Jar -Destination $mirror
& $script -LauncherRoot $profileRoot
$state = Get-Content -LiteralPath (Join-Path $delivery 'install-status.json') -Raw | ConvertFrom-Json
if ($state.state -ne 'installed') { throw 'Active profile installation failed' }
$profileJar = Join-Path $profileInstance ('mods/' + (Split-Path $source -Leaf))
if ((Get-FileHash -LiteralPath $profileJar).Hash -ne $hash) { throw 'Active profile hash mismatch' }
if ((Get-FileHash -LiteralPath $mirror).Hash -ne $hash) { throw 'Launcher mirror changed' }
New-Item -ItemType Directory -Path (Join-Path $profileRoot 'profiles/preview/instance/mods') -Force | Out-Null
& $script -LauncherRoot $profileRoot
$state = Get-Content -LiteralPath (Join-Path $delivery 'install-status.json') -Raw | ConvertFrom-Json
if ($state.state -ne 'blocked') { throw 'Ambiguous profile was accepted' }
Write-Output 'Profile tests passed: active instance, mirror preservation, ambiguous profile rejection.'
# Keep synthetic fixtures in the OS temporary directory; never publish them.
