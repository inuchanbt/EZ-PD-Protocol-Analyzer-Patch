param([string]$AppDir='C:\Infineon\Tools\EZ-PD Protocol Analyzer Utility')

$ErrorActionPreference='Stop'
$AppDir=(Resolve-Path -LiteralPath $AppDir).Path
$ui=Join-Path $AppDir 'plugins\com.cypress.ezpdanalyzer.ui_1.0.0.202502261407.jar'
$root=Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path))
$jar=(Get-Command jar -ErrorAction Stop).Source
$javac=(Get-Command javac -ErrorAction Stop).Source
$java=(Get-Command java -ErrorAction Stop).Source
$javap=(Get-Command javap -ErrorAction Stop).Source

function Require([bool]$condition,[string]$message){
  if(!$condition){ throw $message }
}

if(Get-Process -Name 'EZ_PD_Protocol_Analyzer_Utility','EZ_PD_Protocol_Analyzer_Utilityc' -ErrorAction SilentlyContinue){
  throw 'Close EZ-PD Protocol Analyzer Utility first.'
}
Require (Test-Path -LiteralPath $ui) "UI plug-in JAR not found: $ui"

$work=Join-Path $env:TEMP ('ezpd-message-filter-repair-'+[guid]::NewGuid().ToString('N'))
$extract=Join-Path $work 'extract'
$classes=Join-Path $work 'classes'
$tools=Join-Path $work 'tools'
$stage=Join-Path $work 'stage'
$tableStage=Join-Path $stage 'com\cypress\ezpdanalyzer\ui\nattable'
$utilStage=Join-Path $stage 'com\cypress\ezpdanalyzer\ui\util'
New-Item -ItemType Directory -Force $extract,$classes,$tools,$tableStage,$utilStage | Out-Null

try {
  Push-Location $extract
  try {
    & $jar xf $ui 'com/cypress/ezpdanalyzer/ui/util/DataManager.class'
    Require ($LASTEXITCODE -eq 0) "DataManager extraction failed: $LASTEXITCODE"
  }
  finally { Pop-Location }

  & $javac --release 8 -encoding UTF-8 -cp "$(Join-Path $AppDir 'plugins\*')" -d $classes `
    (Join-Path $root 'src\com\cypress\ezpdanalyzer\ui\nattable\UsbPacketTableSupport.java')
  Require ($LASTEXITCODE -eq 0) "Message-filter support compilation failed: $LASTEXITCODE"

  & $javac --add-exports 'java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED' `
    -source 17 -target 17 -encoding UTF-8 -d $tools `
    (Join-Path $root 'tools\DataManagerFilterReapplyPatcher.java')
  Require ($LASTEXITCODE -eq 0) "Filter-reset patcher compilation failed: $LASTEXITCODE"

  $dataManager=Join-Path $extract 'com\cypress\ezpdanalyzer\ui\util\DataManager.class'
  $currentText=(& $javap -classpath $ui -p -c 'com.cypress.ezpdanalyzer.ui.util.DataManager' | Out-String)
  if($currentText -notmatch 'UsbPacketTableSupport\.configureMultiSelectFilters'){
    & $java --add-exports 'java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED' -cp $tools `
      DataManagerFilterReapplyPatcher $dataManager "$dataManager.patched"
    Require ($LASTEXITCODE -eq 0) "Filter-reset hook failed: $LASTEXITCODE"
    Move-Item -Force -LiteralPath "$dataManager.patched" -Destination $dataManager
  }

  $backup="$ui.pre-message-filter-reapply-v1.0p.bak"
  if(!(Test-Path -LiteralPath $backup)){
    Copy-Item -LiteralPath $ui -Destination $backup
  }

  Get-ChildItem -LiteralPath (Join-Path $classes 'com\cypress\ezpdanalyzer\ui\nattable') `
    -Filter 'UsbPacketTableSupport*.class' | Copy-Item -Destination $tableStage
  Copy-Item -LiteralPath $dataManager -Destination (Join-Path $utilStage 'DataManager.class')
  & $jar uf $ui -C $stage .
  Require ($LASTEXITCODE -eq 0) "UI JAR update failed: $LASTEXITCODE"

  $providerText=(& $javap -classpath $ui -p -c 'com.cypress.ezpdanalyzer.ui.nattable.UsbPacketTableSupport$UsbPacketMultiFilterDataProvider' | Out-String)
  $editorText=(& $javap -classpath $ui -p -c 'com.cypress.ezpdanalyzer.ui.nattable.UsbPacketTableSupport$EnglishFilterRowComboBoxCellEditor' | Out-String)
  $comboText=(& $javap -classpath $ui -p -c 'com.cypress.ezpdanalyzer.ui.nattable.UsbPacketTableSupport$EnglishFilterNatCombo' | Out-String)
  $supportText=(
    (& $javap -classpath $ui -p -c 'com.cypress.ezpdanalyzer.ui.nattable.UsbPacketTableSupport' | Out-String) +
    $providerText + $editorText + $comboText
  )
  $dataManagerText=(& $javap -classpath $ui -p -c 'com.cypress.ezpdanalyzer.ui.util.DataManager' | Out-String)
  foreach($needle in @('configureMultiSelectFilters','EnglishFilterRowComboBoxCellEditor','Hide selected values')){
    Require ($supportText -match [regex]::Escape($needle)) "Message-filter verification missing: $needle"
  }
  Require ($providerText -notmatch [regex]::Escape('Hide selected values')) `
    'Exclusion mode is still mixed into the ordinary Select All values.'
  foreach($needle in @('createHideSelectedViewer','CheckboxTableViewer','ICheckStateProvider','filterModeSeparator','computeSize')){
    Require ($comboText -match [regex]::Escape($needle)) "Independent exclusion-mode verification missing: $needle"
  }
  Require ($comboText -match 'bipush\s+16') 'Hide-selected row NO_SCROLL verification missing.'
  foreach($needle in @('getCanonicalValue','Hide selected values')){
    Require ($editorText -match [regex]::Escape($needle)) "Exclusion-mode commit verification missing: $needle"
  }
  Require ($dataManagerText -match 'UsbPacketTableSupport\.configureMultiSelectFilters') `
    'Filter-reset reapply verification failed.'
  Write-Host 'REPAIR SUCCESS: English multi-select filters now survive capture, Open, Import, and Clear resets.'
}
finally {
  if(Test-Path -LiteralPath $work){
    Remove-Item -LiteralPath $work -Recurse -Force -ErrorAction SilentlyContinue
  }
}
