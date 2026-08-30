param(
  [string]$AppDir = "C:\Infineon\Tools\EZ-PD Protocol Analyzer Utility",
  [switch]$NoLaunch
)
$ErrorActionPreference="Stop"

$AppDir=(Resolve-Path -LiteralPath $AppDir).Path

$UiJarPath=Join-Path $AppDir "plugins\com.cypress.ezpdanalyzer.ui_1.0.0.202502261407.jar"
$ApplnJarPath=Join-Path $AppDir "plugins\com.cypress.ezpdanalyzer.appln_1.0.0.202502261407.jar"
$UiBackup="$UiJarPath.pre-community-patch-v1.0p-final.bak"
$ApplnBackup="$ApplnJarPath.pre-community-patch-v1.0p-final.bak"
$Exe=Join-Path $AppDir "EZ_PD_Protocol_Analyzer_Utility.exe"

$running=Get-Process -Name 'EZ_PD_Protocol_Analyzer_Utility','EZ_PD_Protocol_Analyzer_Utilityc' -ErrorAction SilentlyContinue
if ($running) {
  $running | Format-Table Id,ProcessName,MainWindowTitle -AutoSize
  throw "Analyzer Utility is running. Kill every instance first."
}
if (!(Test-Path -LiteralPath $UiBackup)) {
  throw "Final UI backup not found: $UiBackup"
}

Copy-Item -Force -LiteralPath $UiBackup -Destination $UiJarPath
if (Test-Path -LiteralPath $ApplnBackup) {
  Copy-Item -Force -LiteralPath $ApplnBackup -Destination $ApplnJarPath
}
Write-Host "RESTORE SUCCESS"
Write-Host "  UI JAR       : $UiJarPath"
Write-Host "  About JAR    : $ApplnJarPath"
Write-Host "  UI backup    : $UiBackup"
Write-Host "  About backup : $ApplnBackup"

if (!$NoLaunch) {
  Write-Host "Launching restored Utility once with -clean -clearPersistedState..."
  Start-Process -FilePath $Exe -ArgumentList "-clean","-clearPersistedState"
}
