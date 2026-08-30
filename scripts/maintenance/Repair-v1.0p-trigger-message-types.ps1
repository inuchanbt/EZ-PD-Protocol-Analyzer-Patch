param([string]$AppDir='C:\Infineon\Tools\EZ-PD Protocol Analyzer Utility')

$ErrorActionPreference='Stop'
$AppDir=(Resolve-Path -LiteralPath $AppDir).Path
$ui=Join-Path $AppDir 'plugins\com.cypress.ezpdanalyzer.ui_1.0.0.202502261407.jar'
$root=Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path))
$jar=(Get-Command jar -ErrorAction Stop).Source
$javac=(Get-Command javac -ErrorAction Stop).Source
$javap=(Get-Command javap -ErrorAction Stop).Source

function Require([bool]$condition,[string]$message){
  if(!$condition){ throw $message }
}

if(Get-Process -Name 'EZ_PD_Protocol_Analyzer_Utility','EZ_PD_Protocol_Analyzer_Utilityc' -ErrorAction SilentlyContinue){
  throw 'Close EZ-PD Protocol Analyzer Utility first.'
}
Require (Test-Path -LiteralPath $ui) "UI plug-in JAR not found: $ui"

$work=Join-Path $env:TEMP ('ezpd-trigger-message-type-repair-'+[guid]::NewGuid().ToString('N'))
$classes=Join-Path $work 'classes'
$stage=Join-Path $work 'stage'
$viewStage=Join-Path $stage 'com\cypress\ezpdanalyzer\ui\views'
New-Item -ItemType Directory -Force $classes,$viewStage | Out-Null

try {
  $currentText=(& $javap -classpath $ui -p 'com.cypress.ezpdanalyzer.ui.views.TriggerViewFixed' 2>&1 | Out-String)
  Require ($LASTEXITCODE -eq 0) 'Triggers are not enabled in this installation. Re-run Patch-Analyzer.cmd -EnableTriggers first.'

  & $javac --release 8 -encoding UTF-8 -cp "$(Join-Path $AppDir 'plugins\*')" -d $classes `
    (Join-Path $root 'src\com\cypress\ezpdanalyzer\ui\views\AdvancedFeatureUsbSync.java') `
    (Join-Path $root 'src\com\cypress\ezpdanalyzer\ui\views\TriggerViewFixed.java')
  Require ($LASTEXITCODE -eq 0) "Trigger message-type compilation failed: $LASTEXITCODE"

  $backup="$ui.pre-trigger-message-type-repair-v1.0p.bak"
  if(!(Test-Path -LiteralPath $backup)){
    Copy-Item -LiteralPath $ui -Destination $backup
  }

  Get-ChildItem -LiteralPath (Join-Path $classes 'com\cypress\ezpdanalyzer\ui\views') `
    -Filter 'TriggerViewFixed*.class' | Copy-Item -Destination $viewStage
  Get-ChildItem -LiteralPath (Join-Path $classes 'com\cypress\ezpdanalyzer\ui\views') `
    -Filter 'AdvancedFeatureUsbSync*.class' | Copy-Item -Destination $viewStage
  & $jar uf $ui -C $stage .
  Require ($LASTEXITCODE -eq 0) "UI JAR update failed: $LASTEXITCODE"

  $verifyText=(& $javap -classpath $ui -p -c 'com.cypress.ezpdanalyzer.ui.views.TriggerViewFixed' | Out-String)
  foreach($needle in @('isReservedMessageType','C_RSVD0','D_RSVD13','E_RSVD0','E_RSVD(1[9]','rawTypeIndex','sendMappedTrigger')){
    Require ($verifyText -match [regex]::Escape($needle)) "Trigger message-type verification missing: $needle"
  }
  Write-Host 'REPAIR SUCCESS: reserved message types are hidden from the Triggers Message Type list while their original device values are preserved.'
}
finally {
  if(Test-Path -LiteralPath $work){
    Remove-Item -LiteralPath $work -Recurse -Force -ErrorAction SilentlyContinue
  }
}
