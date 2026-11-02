#requires -Version 7.0
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$AdbPath,
    [Parameter(Mandatory)][ValidatePattern('^[A-Za-z0-9._:-]+$')][string]$Serial,
    [ValidateRange(1,65535)][int]$ServerPort = 5038,
    [Parameter(Mandatory)][string]$OutputDirectory
)
$ErrorActionPreference = 'Stop'
$adb = (Resolve-Path -LiteralPath $AdbPath).Path
$destination = [IO.Path]::GetFullPath($OutputDirectory, (Get-Location).ProviderPath)
if (Test-Path -LiteralPath $destination) { throw 'Output directory already exists; choose a new run directory.' }
$selector = @('-P', "$ServerPort", '-s', $Serial)
function Read-Adb([string[]]$CommandArguments) {
    $lines = & $adb @selector @CommandArguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "Selected-device command failed: $($CommandArguments -join ' '): $lines" }
    ($lines -join "`n").Trim()
}
if ((Read-Adb @('get-state')) -ne 'device') { throw 'Selected target is not online and authorized.' }
$device = [ordered]@{
    collectedAtUtc = [DateTime]::UtcNow.ToString('o')
    serial = $Serial
    adbServerPort = $ServerPort
    manufacturer = Read-Adb @('shell','getprop','ro.product.manufacturer')
    model = Read-Adb @('shell','getprop','ro.product.model')
    hardware = Read-Adb @('shell','getprop','ro.hardware')
    sdk = Read-Adb @('shell','getprop','ro.build.version.sdk')
    fingerprint = Read-Adb @('shell','getprop','ro.build.fingerprint')
    securityPatch = Read-Adb @('shell','getprop','ro.build.version.security_patch')
    limitations = 'Identity/state snapshot only; not camera-pair, capture, thermal or physical acceptance.'
}
# All device operations are read-only. Never install, grant/revoke, start an app or change settings.
$package = Read-Adb @('shell','dumpsys','package','com.umbracam.app')
$battery = Read-Adb @('shell','dumpsys','battery')
$thermal = Read-Adb @('shell','dumpsys','thermalservice')
New-Item -ItemType Directory -Path $destination | Out-Null
$device | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $destination 'device.json') -Encoding utf8
$package | Set-Content -LiteralPath (Join-Path $destination 'app-package.txt') -Encoding utf8
$battery | Set-Content -LiteralPath (Join-Path $destination 'battery.txt') -Encoding utf8
$thermal | Set-Content -LiteralPath (Join-Path $destination 'thermal.txt') -Encoding utf8
Get-ChildItem -LiteralPath $destination -File | Sort-Object Name | ForEach-Object {
    [ordered]@{file=$_.Name; sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()}
} | ConvertTo-Json -AsArray | Set-Content -LiteralPath (Join-Path $destination 'manifest.json') -Encoding utf8
Write-Output "Collected read-only identity/state evidence for explicitly selected $Serial in $destination"
