param([Parameter(Mandatory=$true)][string]$AppDir)

$ErrorActionPreference = 'Stop'
$micro = [char]0x00B5
$timeUnit = 'Time(' + $micro + 's)'
$AppDir = (Resolve-Path -LiteralPath $AppDir).Path
$plugins = Join-Path $AppDir 'plugins'
$uiJar = Join-Path $plugins 'com.cypress.ezpdanalyzer.ui_1.0.0.202502261407.jar'
$applnJar = Join-Path $plugins 'com.cypress.ezpdanalyzer.appln_1.0.0.202502261407.jar'
$applnBackup = "$applnJar.pre-community-patch-v1.0p-final.bak"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$jfree156 = Join-Path $root 'third_party\jfreechart-1.5.6.jar'

function Require([bool]$condition, [string]$message) {
  if (!$condition) { throw $message }
}
function Test-Utf8TextInFile([string]$path, [string]$text) {
  $bytes = [System.IO.File]::ReadAllBytes($path)
  $needle = [System.Text.Encoding]::UTF8.GetBytes($text)
  if ($needle.Length -eq 0 -or $bytes.Length -lt $needle.Length) { return $false }
  for ($i = 0; $i -le ($bytes.Length - $needle.Length); $i++) {
    $matches = $true
    for ($j = 0; $j -lt $needle.Length; $j++) {
      if ($bytes[$i + $j] -ne $needle[$j]) { $matches = $false; break }
    }
    if ($matches) { return $true }
  }
  return $false
}
function Get-Sha256Hex([string]$path) {
  # Get-FileHash is not present in some PowerShell hosts shipped alongside
  # older Eclipse installations.  Use only .NET APIs available there.
  $sha = [System.Security.Cryptography.SHA256]::Create()
  $stream = [System.IO.File]::OpenRead($path)
  try {
    return ([System.BitConverter]::ToString($sha.ComputeHash($stream))).Replace('-', '')
  }
  finally {
    $stream.Dispose()
    $sha.Dispose()
  }
}
function Invoke-Transform([string]$className, [string[]]$arguments) {
  & $java --add-exports 'java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED' -cp $tools $className @arguments
  if ($LASTEXITCODE -ne 0) { throw "$className failed: $LASTEXITCODE" }
}

Require (Test-Path -LiteralPath $uiJar) "UI plug-in JAR not found: $uiJar"
Require (Test-Path -LiteralPath $applnJar) "Application plug-in JAR not found: $applnJar"
Require (Test-Path -LiteralPath $jfree156) "Bundled JFreeChart 1.5.6 core is missing: $jfree156"
$running = Get-Process -Name 'EZ_PD_Protocol_Analyzer_Utility','EZ_PD_Protocol_Analyzer_Utilityc' -ErrorAction SilentlyContinue
Require ($null -eq $running) 'EZ-PD Protocol Analyzer Utility is running. Close it and rerun.'

$java=(Get-Command java -ErrorAction Stop).Source
$javac=(Get-Command javac -ErrorAction Stop).Source
$jar=(Get-Command jar -ErrorAction Stop).Source
$javap=(Get-Command javap -ErrorAction Stop).Source
$work=Join-Path $env:TEMP ('ezpd-final-display-' + [guid]::NewGuid().ToString('N'))
$extract=Join-Path $work 'extract'
$classes=Join-Path $work 'classes'
$tools=Join-Path $work 'tools'
$stage=Join-Path $work 'stage'
$aboutStage=Join-Path $work 'about-stage'
New-Item -ItemType Directory -Force $extract,$classes,$tools,$stage,$aboutStage | Out-Null

try {
  Write-Host '[display 1/9] Extract final-display targets'
  $entries=@(
    'com/cypress/ezpdanalyzer/ui/jfreechart/CyXYLineChart.class',
    'com/cypress/ezpdanalyzer/ui/views/GraphSelectorComposite.class',
    'com/cypress/ezpdanalyzer/ui/nattable/CreateUSBPackageNatTable.class',
    'com/cypress/ezpdanalyzer/ui/nattable/CreateUSBPackageNatTable$1.class',
    'com/cypress/ezpdanalyzer/ui/nattable/CustomBodyLayerStack.class',
    'com/cypress/ezpdanalyzer/ui/util/DataManager.class',
    'com/cypress/ezpdanalyzer/ui/views/DeviceStatusView.class',
    'com/cypress/ezpdanalyzer/ui/views/DeviceStatusView$1.class',
    'com/cypress/ezpdanalyzer/ui/views/DeviceStatusView$2.class',
    'com/cypress/ezpdanalyzer/ui/views/DetailsView.class',
    'com/cypress/ezpdanalyzer/ui/views/PayloadView.class',
    'com/cypress/ezpdanalyzer/ui/views/GraphicalView$2$1.class',
    'com/cypress/ezpdanalyzer/ui/views/GraphicalView$2$2.class',
    'com/cypress/ezpdanalyzer/ui/handler/ExcelExportHandler$2$1.class',
    'com/cypress/ezpdanalyzer/ui/reader/XLSXReader$2$1.class',
    'com/cypress/ezpdanalyzer/ui/handler/SaveHandler$2$1.class',
    'com/cypress/ezpdanalyzer/ui/util/ExitUtil.class',
    'lib/jcommon-1.0.23.jar', 'lib/jfreechart-1.5.3.jar'
  )
  Push-Location $extract
  try { & $jar xf $uiJar @entries; Require ($LASTEXITCODE -eq 0) "UI JAR extraction failed: $LASTEXITCODE" }
  finally { Pop-Location }
  Push-Location $aboutStage
  try {
    & $jar xf $applnJar 'plugin.xml' 'com/cypress/ezpdanalyzer/appln/ApplicationWorkbenchWindowAdvisor.class'
    Require ($LASTEXITCODE -eq 0) "Application JAR extraction failed: $LASTEXITCODE"
  }
  finally { Pop-Location }

  $chart=Join-Path $extract 'com\cypress\ezpdanalyzer\ui\jfreechart\CyXYLineChart.class'
  $selector=Join-Path $extract 'com\cypress\ezpdanalyzer\ui\views\GraphSelectorComposite.class'
  $table=Join-Path $extract 'com\cypress\ezpdanalyzer\ui\nattable\CreateUSBPackageNatTable.class'
  $tableLabels=Join-Path $extract 'com\cypress\ezpdanalyzer\ui\nattable\CreateUSBPackageNatTable$1.class'
  $tableBody=Join-Path $extract 'com\cypress\ezpdanalyzer\ui\nattable\CustomBodyLayerStack.class'
  $dataManager=Join-Path $extract 'com\cypress\ezpdanalyzer\ui\util\DataManager.class'
  $windowAdvisor=Join-Path $aboutStage 'com\cypress\ezpdanalyzer\appln\ApplicationWorkbenchWindowAdvisor.class'
  $device=@(
    (Join-Path $extract 'com\cypress\ezpdanalyzer\ui\views\DeviceStatusView.class'),
    (Join-Path $extract 'com\cypress\ezpdanalyzer\ui\views\DeviceStatusView$1.class'),
    (Join-Path $extract 'com\cypress\ezpdanalyzer\ui\views\DeviceStatusView$2.class')
  )
  $detailsView=Join-Path $extract 'com\cypress\ezpdanalyzer\ui\views\DetailsView.class'
  $payloadView=Join-Path $extract 'com\cypress\ezpdanalyzer\ui\views\PayloadView.class'
  $graphicalMicroUnits=@(
    (Join-Path $extract 'com\cypress\ezpdanalyzer\ui\views\GraphicalView$2$1.class'),
    (Join-Path $extract 'com\cypress\ezpdanalyzer\ui\views\GraphicalView$2$2.class')
  )
  $workflow=@(
    (Join-Path $extract 'com\cypress\ezpdanalyzer\ui\handler\ExcelExportHandler$2$1.class'),
    (Join-Path $extract 'com\cypress\ezpdanalyzer\ui\reader\XLSXReader$2$1.class'),
    (Join-Path $extract 'com\cypress\ezpdanalyzer\ui\handler\SaveHandler$2$1.class'),
    (Join-Path $extract 'com\cypress\ezpdanalyzer\ui\util\ExitUtil.class')
  )
  foreach ($path in @($chart,$selector,$table,$tableLabels,$tableBody,$dataManager,$windowAdvisor,$detailsView,$payloadView) + $graphicalMicroUnits + $device + $workflow) { Require (Test-Path -LiteralPath $path) "Missing expected class: $path" }

  $jfree=Join-Path $extract 'lib\jfreechart-1.5.3.jar'
  $jcommon=Join-Path $extract 'lib\jcommon-1.0.23.jar'
  Write-Host '[display 2/9] Upgrade embedded JFreeChart core to 1.5.6'
  Copy-Item -LiteralPath $jfree156 -Destination $jfree -Force
  Require ((Get-Sha256Hex $jfree) -eq (Get-Sha256Hex $jfree156)) 'JFreeChart 1.5.6 staging hash verification failed.'

  Write-Host '[display 3/9] Compile verified support classes and transformers'
  $jfreeSrc=Join-Path $root 'src\com\cypress\ezpdanalyzer\ui\jfreechart'
  & $javac --release 8 -encoding UTF-8 -cp "$jfree;$jcommon" -d $classes `
    (Join-Path $jfreeSrc 'RightAlignedNumberAxis.java') `
    (Join-Path $jfreeSrc 'FirstMajorTickNumberAxis.java') `
    (Join-Path $jfreeSrc 'DomainAlignedNumberAxis.java') `
    (Join-Path $jfreeSrc 'DomainAxisSupport.java') `
    (Join-Path $jfreeSrc 'LegendTextBaselineSupport.java') `
    (Join-Path $jfreeSrc 'MarkerLabelBackgroundSupport.java')
  Require ($LASTEXITCODE -eq 0) "JFreeChart support compilation failed: $LASTEXITCODE"
  & $javac --release 8 -encoding UTF-8 -cp $jfree -d $classes `
    (Join-Path $root 'src\org\jfree\chart\ChartUtilities.java') `
    (Join-Path $root 'src\org\jfree\chart\util\ParamChecks.java')
  Require ($LASTEXITCODE -eq 0) "JFreeChart 1.5.6 SWT compatibility compilation failed: $LASTEXITCODE"
  & $javac --release 8 -encoding UTF-8 -cp "$plugins\*" -d $classes `
    (Join-Path $root 'src\com\cypress\ezpdanalyzer\ui\nattable\UsbPacketTableSupport.java') `
    (Join-Path $root 'src\com\cypress\ezpdanalyzer\ui\views\ViewColumnWidthPersistence.java') `
    (Join-Path $root 'src\com\cypress\ezpdanalyzer\ui\views\PayloadTableSupport.java') `
    (Join-Path $root 'src\com\cypress\ezpdanalyzer\ui\views\GraphicalHeaderSupport.java')
  Require ($LASTEXITCODE -eq 0) "Table persistence support compilation failed: $LASTEXITCODE"
  $toolSources=@(
    'GraphAxisAlignmentPatcher.java','V10bPatcher.java','GraphLabelPatcher.java','LegendPatcher.java','MarkerLabelBackgroundPatcher.java',
    'UsbPacketTablePatcher.java','UsbPacketTableLayoutFixPatcher.java','StatusWidthPatcher.java',
    'FinalTableConfigurationPatcher.java','VbusHeaderPatcher.java','ColumnWidthPersistencePatcher.java',
    'DeviceStatusLabelPatcher.java','DeviceStatusSpacingPatcher.java',
    'FileWorkflowDialogPatcher.java','AboutTextPatcher.java','WindowStatePatcher.java',
    'ViewColumnWidthPersistencePatcher.java','PayloadTablePatcher.java',
    'GraphSelectorHeaderPatcher.java','GraphicalMicroUnitPatcher.java',
    'DataManagerFilterReapplyPatcher.java'
  ) | ForEach-Object { Join-Path $root "tools\$_" }
  foreach ($source in $toolSources) { Require (Test-Path -LiteralPath $source) "Missing final-patch source: $source" }
  & $javac --add-exports 'java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED' -source 17 -target 17 -encoding UTF-8 -d $tools @toolSources
  Require ($LASTEXITCODE -eq 0) "Final transformer compilation failed: $LASTEXITCODE"

  Write-Host '[display 4/9] Apply graph axis, label, marker, and header fixes'
  Invoke-Transform 'GraphAxisAlignmentPatcher' @($chart,"$chart.right")
  Invoke-Transform 'V10bPatcher' @("$chart.right","$chart.axis")
  Invoke-Transform 'GraphLabelPatcher' @("$chart.axis","$chart.labels")
  Invoke-Transform 'LegendPatcher' @("$chart.labels","$chart.legend")
  Invoke-Transform 'MarkerLabelBackgroundPatcher' @("$chart.legend",$chart)
  Invoke-Transform 'GraphLabelPatcher' @($selector,$selector)
  Invoke-Transform 'GraphSelectorHeaderPatcher' @($selector,"$selector.header")
  Move-Item -Force -LiteralPath "$selector.header" -Destination $selector
  foreach ($unitClass in $graphicalMicroUnits) {
    Invoke-Transform 'GraphicalMicroUnitPatcher' @($unitClass,"$unitClass.micro")
    Move-Item -Force -LiteralPath "$unitClass.micro" -Destination $unitClass
  }

  Write-Host '[display 5/9] Apply USB PD Messages and Payload table layout'
  Invoke-Transform 'UsbPacketTablePatcher' @($table,"$table.g",$tableLabels,"$tableLabels.g",$tableBody,"$tableBody.g")
  Invoke-Transform 'UsbPacketTableLayoutFixPatcher' @("$table.g","$table.h","$tableBody.g","$tableBody.h")
  Invoke-Transform 'StatusWidthPatcher' @("$tableBody.h","$tableBody.j")
  Invoke-Transform 'FinalTableConfigurationPatcher' @("$table.h","$table.k")
  Invoke-Transform 'VbusHeaderPatcher' @("$table.k","$table.l")
  Invoke-Transform 'ColumnWidthPersistencePatcher' @("$table.l",$table)
  Invoke-Transform 'DataManagerFilterReapplyPatcher' @($dataManager,"$dataManager.filters")
  Move-Item -Force -LiteralPath "$dataManager.filters" -Destination $dataManager
  Move-Item -Force -LiteralPath "$tableLabels.g" -Destination $tableLabels
  Move-Item -Force -LiteralPath "$tableBody.j" -Destination $tableBody

  Write-Host '[display 6/9] Apply device-status and workflow-dialog fixes'
  Invoke-Transform 'DeviceStatusLabelPatcher' @($device[0],"$($device[0]).m")
  Move-Item -Force -LiteralPath "$($device[0]).m" -Destination $device[0]
  foreach ($file in $device) { Invoke-Transform 'DeviceStatusSpacingPatcher' @($file,"$file.n") }
  foreach ($file in $device) { Move-Item -Force -LiteralPath "$file.n" -Destination $file }
  Invoke-Transform 'ViewColumnWidthPersistencePatcher' @($detailsView,"$detailsView.width",$payloadView,"$payloadView.width")
  Move-Item -Force -LiteralPath "$detailsView.width" -Destination $detailsView
  Move-Item -Force -LiteralPath "$payloadView.width" -Destination $payloadView
  Invoke-Transform 'PayloadTablePatcher' @($payloadView,"$payloadView.align")
  Move-Item -Force -LiteralPath "$payloadView.align" -Destination $payloadView
  foreach ($file in $workflow) { Invoke-Transform 'FileWorkflowDialogPatcher' @($file,$file) }

  Write-Host '[display 7/9] Apply About signature and remembered window size'
  $aboutXml=Join-Path $aboutStage 'plugin.xml'
  Invoke-Transform 'AboutTextPatcher' @($aboutXml,"$aboutXml.patched")
  Move-Item -Force -LiteralPath "$aboutXml.patched" -Destination $aboutXml
  Invoke-Transform 'WindowStatePatcher' @($windowAdvisor,"$windowAdvisor.patched")
  Move-Item -Force -LiteralPath "$windowAdvisor.patched" -Destination $windowAdvisor

  Write-Host '[display 8/9] Update both plug-in JARs'
  $jfreeStage=Join-Path $stage 'com\cypress\ezpdanalyzer\ui\jfreechart'
  $viewStage=Join-Path $stage 'com\cypress\ezpdanalyzer\ui\views'
  $tableStage=Join-Path $stage 'com\cypress\ezpdanalyzer\ui\nattable'
  $handlerStage=Join-Path $stage 'com\cypress\ezpdanalyzer\ui\handler'
  $readerStage=Join-Path $stage 'com\cypress\ezpdanalyzer\ui\reader'
  $utilStage=Join-Path $stage 'com\cypress\ezpdanalyzer\ui\util'
  $jfreeCoreStage=Join-Path $stage 'lib'
  $jfreeCompatStage=Join-Path $stage 'org\jfree\chart'
  $jfreeCompatUtilStage=Join-Path $jfreeCompatStage 'util'
  New-Item -ItemType Directory -Force $jfreeStage,$viewStage,$tableStage,$handlerStage,$readerStage,$utilStage,$jfreeCoreStage,$jfreeCompatStage,$jfreeCompatUtilStage | Out-Null
  Copy-Item -LiteralPath $chart -Destination (Join-Path $jfreeStage 'CyXYLineChart.class')
  Get-ChildItem -LiteralPath (Join-Path $classes 'com\cypress\ezpdanalyzer\ui\jfreechart') -Filter '*.class' | Copy-Item -Destination $jfreeStage
  Copy-Item -LiteralPath $selector -Destination (Join-Path $viewStage 'GraphSelectorComposite.class')
  foreach ($unitClass in $graphicalMicroUnits) { Copy-Item -LiteralPath $unitClass -Destination $viewStage }
  foreach ($file in $device) { Copy-Item -LiteralPath $file -Destination $viewStage }
  Copy-Item -LiteralPath $detailsView -Destination $viewStage
  Copy-Item -LiteralPath $payloadView -Destination $viewStage
  Get-ChildItem -LiteralPath (Join-Path $classes 'com\cypress\ezpdanalyzer\ui\views') -Filter 'ViewColumnWidthPersistence*.class' | Copy-Item -Destination $viewStage
  Get-ChildItem -LiteralPath (Join-Path $classes 'com\cypress\ezpdanalyzer\ui\views') -Filter 'PayloadTableSupport*.class' | Copy-Item -Destination $viewStage
  Get-ChildItem -LiteralPath (Join-Path $classes 'com\cypress\ezpdanalyzer\ui\views') -Filter 'GraphicalHeaderSupport*.class' | Copy-Item -Destination $viewStage
  Copy-Item -LiteralPath $table -Destination (Join-Path $tableStage 'CreateUSBPackageNatTable.class')
  Copy-Item -LiteralPath $tableLabels -Destination (Join-Path $tableStage 'CreateUSBPackageNatTable$1.class')
  Copy-Item -LiteralPath $tableBody -Destination (Join-Path $tableStage 'CustomBodyLayerStack.class')
  Copy-Item -LiteralPath $dataManager -Destination (Join-Path $utilStage 'DataManager.class')
  Get-ChildItem -LiteralPath (Join-Path $classes 'com\cypress\ezpdanalyzer\ui\nattable') -Filter 'UsbPacketTableSupport*.class' | Copy-Item -Destination $tableStage
  Copy-Item -LiteralPath $workflow[0] -Destination $handlerStage
  Copy-Item -LiteralPath $workflow[1] -Destination $readerStage
  Copy-Item -LiteralPath $workflow[2] -Destination $handlerStage
  Copy-Item -LiteralPath $workflow[3] -Destination $utilStage
  # Keep Infineon's proven SWT bridge, but replace only its embedded JFreeChart
  # core and provide the two small API shims that bridge uses on 1.5.6.
  Copy-Item -LiteralPath $jfree -Destination (Join-Path $jfreeCoreStage 'jfreechart-1.5.3.jar')
  Copy-Item -LiteralPath (Join-Path $classes 'org\jfree\chart\ChartUtilities.class') -Destination $jfreeCompatStage
  Copy-Item -LiteralPath (Join-Path $classes 'org\jfree\chart\util\ParamChecks.class') -Destination $jfreeCompatUtilStage
  & $jar uf $uiJar -C $stage .
  Require ($LASTEXITCODE -eq 0) "UI JAR update failed: $LASTEXITCODE"
  if (!(Test-Path -LiteralPath $applnBackup)) { Copy-Item -LiteralPath $applnJar -Destination $applnBackup }
  # jar applies -C only to the immediately following entry, so repeat it for
  # the nested class rather than looking for that class in the caller's cwd.
  & $jar uf $applnJar `
    -C $aboutStage 'plugin.xml' `
    -C $aboutStage 'com/cypress/ezpdanalyzer/appln/ApplicationWorkbenchWindowAdvisor.class'
  Require ($LASTEXITCODE -eq 0) "Application JAR update failed: $LASTEXITCODE"

  Write-Host '[display 9/9] Verify final display changes'
  $graphText=(& $javap -classpath $uiJar -p -c 'com.cypress.ezpdanalyzer.ui.jfreechart.CyXYLineChart' | Out-String)
  foreach ($needle in @('RightAlignedNumberAxis."<init>"','DomainAxisSupport.install','MarkerLabelBackgroundSupport.clear','LegendTextBaselineSupport.adjust','CC1/CC2 (mV)','VBUS (mA)','CC1(mV)','CC2(mV)','VBUS(mV)','VBUS(mA)')) { Require ($graphText -match [regex]::Escape($needle)) "Graph verification missing: $needle" }
  Require (Test-Utf8TextInFile $chart $timeUnit) 'Graph verification missing: Time(microseconds).'
  $axisSupportText=(& $javap -classpath $uiJar -p -c 'com.cypress.ezpdanalyzer.ui.jfreechart.DomainAxisSupport' | Out-String)
  Require ($axisSupportText -match 'FirstMajorTickNumberAxis') 'Left-axis first-tick correction is missing.'
  $rightAxisText=(& $javap -classpath $uiJar -p 'com.cypress.ezpdanalyzer.ui.jfreechart.RightAlignedNumberAxis' | Out-String)
  Require ($rightAxisText -match 'extends com\.cypress\.ezpdanalyzer\.ui\.jfreechart\.FirstMajorTickNumberAxis') 'Right-axis first-tick correction is missing.'
  $selectorText=(& $javap -classpath $uiJar -p -c 'com.cypress.ezpdanalyzer.ui.views.GraphSelectorComposite' | Out-String)
  foreach ($needle in @('CC1(mV)','CC2(mV)','VBUS(mV)','VBUS(mA)','GraphicalHeaderSupport.configure')) { Require ($selectorText -match [regex]::Escape($needle)) "Graphical header verification missing: $needle" }
  $detailsText=(& $javap -classpath $uiJar -p -c 'com.cypress.ezpdanalyzer.ui.views.DetailsView' | Out-String)
  $payloadText=(& $javap -classpath $uiJar -p -c 'com.cypress.ezpdanalyzer.ui.views.PayloadView' | Out-String)
  foreach ($viewText in @($detailsText,$payloadText)) { Require ($viewText -match 'ViewColumnWidthPersistence') 'Details/Payload width-persistence hook is missing.' }
  Require ($payloadText -match 'PayloadTableSupport\.configure') 'Payload numeric-alignment hook is missing.'
  foreach ($unitPath in $graphicalMicroUnits) {
    Require (Test-Utf8TextInFile $unitPath $micro) "Graphical microsecond verification missing: $unitPath"
  }
  $tableText=(& $javap -classpath $uiJar -p -c 'com.cypress.ezpdanalyzer.ui.nattable.CreateUSBPackageNatTable' | Out-String)
  foreach ($needle in @('UsbPacketTableSupport.configure','UsbPacketTableSupport.installColumnWidthPersistence')) {
    Require ($tableText -match [regex]::Escape($needle)) "USB PD Messages helper hook is missing: $needle"
  }
  $filterComboText=(& $javap -classpath $uiJar -p -c 'com.cypress.ezpdanalyzer.ui.nattable.UsbPacketTableSupport$EnglishFilterNatCombo' | Out-String)
  $tableSupportText=(
    (& $javap -classpath $uiJar -p -c 'com.cypress.ezpdanalyzer.ui.nattable.UsbPacketTableSupport' | Out-String) +
    (& $javap -classpath $uiJar -p -c 'com.cypress.ezpdanalyzer.ui.nattable.UsbPacketTableSupport$UsbPacketMultiFilterDataProvider' | Out-String) +
    (& $javap -classpath $uiJar -p -c 'com.cypress.ezpdanalyzer.ui.nattable.UsbPacketTableSupport$EnglishFilterRowComboBoxCellEditor' | Out-String) +
    $filterComboText
  )
  foreach ($needle in @('configureMultiSelectFilters','EnglishFilterRowComboBoxCellEditor','getCanonicalValue','REGULAR_EXPRESSION','Hide selected values','getUsbPacketDatas','Select all','ICheckStateProvider','CheckboxTableViewer','filterModeSeparator','computeSize')) {
    Require ($tableSupportText -match [regex]::Escape($needle)) "USB PD Messages multi-filter verification missing: $needle"
  }
  Require ($filterComboText -match 'bipush\s+16') 'Hide-selected row NO_SCROLL verification missing.'
  $filterProviderText=(& $javap -classpath $uiJar -p -c 'com.cypress.ezpdanalyzer.ui.nattable.UsbPacketTableSupport$UsbPacketMultiFilterDataProvider' | Out-String)
  Require ($filterProviderText -notmatch [regex]::Escape('Hide selected values')) 'Exclusion mode is still mixed into the ordinary Select All values.'
  $dataManagerText=(& $javap -classpath $uiJar -p -c 'com.cypress.ezpdanalyzer.ui.util.DataManager' | Out-String)
  Require ($dataManagerText -match 'UsbPacketTableSupport\.configureMultiSelectFilters') 'USB PD Messages filter-reset hook is missing.'
  $workflowText=(& $javap -classpath $uiJar -p -c 'com.cypress.ezpdanalyzer.ui.handler.ExcelExportHandler$2$1' | Out-String)
  Require ($workflowText -notmatch 'MessageDialog.openInformation') 'Export success dialog was not removed.'
  $aboutVerify=Join-Path $work 'about-verify'
  New-Item -ItemType Directory -Force $aboutVerify | Out-Null
  Push-Location $aboutVerify
  try { & $jar xf $applnJar 'plugin.xml'; Require ($LASTEXITCODE -eq 0) 'About verification extraction failed.' }
  finally { Pop-Location }
  Require ((Get-Content -LiteralPath (Join-Path $aboutVerify 'plugin.xml') -Raw) -match [regex]::Escape('Build     :  155 (Mod by @USB_PD_EPR_240W v1.0p)')) 'About signature verification failed.'
  $windowText=(& $javap -classpath $applnJar -p -c 'com.cypress.ezpdanalyzer.appln.ApplicationWorkbenchWindowAdvisor' | Out-String)
  Require ($windowText -notmatch 'Shell\.setMaximized') 'Window-size persistence verification failed.'
  $jfreeVerify=Join-Path $work 'jfree156-verify'
  New-Item -ItemType Directory -Force $jfreeVerify | Out-Null
  Push-Location $jfreeVerify
  try { & $jar xf $uiJar 'lib/jfreechart-1.5.3.jar'; Require ($LASTEXITCODE -eq 0) 'JFreeChart core verification extraction failed.' }
  finally { Pop-Location }
  Require ((Get-Sha256Hex (Join-Path $jfreeVerify 'lib\jfreechart-1.5.3.jar')) -eq (Get-Sha256Hex $jfree156)) 'Embedded JFreeChart 1.5.6 hash verification failed.'
  $uiEntries=& $jar tf $uiJar
  foreach ($entry in @('org/jfree/chart/ChartUtilities.class','org/jfree/chart/util/ParamChecks.class')) { Require ($uiEntries -contains $entry) "JFreeChart 1.5.6 SWT compatibility class is missing: $entry" }
  Write-Host '        final graph, table, status, workflow, About, window-size, and JFreeChart 1.5.6 core changes: VERIFIED'
}
finally {
  if (Test-Path -LiteralPath $work) { Remove-Item -LiteralPath $work -Recurse -Force -ErrorAction SilentlyContinue }
}
