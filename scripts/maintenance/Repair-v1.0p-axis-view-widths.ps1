param([string]$AppDir='C:\Infineon\Tools\EZ-PD Protocol Analyzer Utility')

$ErrorActionPreference='Stop'
$AppDir=(Resolve-Path -LiteralPath $AppDir).Path
$ui=Join-Path $AppDir 'plugins\com.cypress.ezpdanalyzer.ui_1.0.0.202502261407.jar'
$root=Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path))
$jar=(Get-Command jar -ErrorAction Stop).Source
$javac=(Get-Command javac -ErrorAction Stop).Source
$java=(Get-Command java -ErrorAction Stop).Source
$javap=(Get-Command javap -ErrorAction Stop).Source

if(Get-Process -Name 'EZ_PD_Protocol_Analyzer_Utility','EZ_PD_Protocol_Analyzer_Utilityc' -ErrorAction SilentlyContinue){
  throw 'Close Analyzer first.'
}
if(!(Test-Path -LiteralPath $ui)){ throw "UI plug-in JAR not found: $ui" }

$w=Join-Path $env:TEMP ('ezpd-axis-view-width-repair-'+[guid]::NewGuid().ToString('N'))
$x=Join-Path $w 'extract'; $c=Join-Path $w 'classes'; $t=Join-Path $w 'tools'; $s=Join-Path $w 'stage'
New-Item -ItemType Directory -Force $x,$c,$t,$s | Out-Null

try {
  Push-Location $x
  try {
    & $jar xf $ui `
      'lib/jcommon-1.0.23.jar' `
      'lib/jfreechart-1.5.3.jar' `
      'com/cypress/ezpdanalyzer/ui/jfreechart/RightAlignedNumberAxis.class' `
      'com/cypress/ezpdanalyzer/ui/jfreechart/DomainAlignedNumberAxis.class' `
      'com/cypress/ezpdanalyzer/ui/jfreechart/DomainAxisSupport.class' `
      'com/cypress/ezpdanalyzer/ui/jfreechart/CyXYLineChart.class' `
      'com/cypress/ezpdanalyzer/ui/views/DetailsView.class' `
      'com/cypress/ezpdanalyzer/ui/views/PayloadView.class'
    if($LASTEXITCODE){ throw "UI extraction failed: $LASTEXITCODE" }
  } finally { Pop-Location }

  $jfree=Join-Path $x 'lib\jfreechart-1.5.3.jar'
  $jcommon=Join-Path $x 'lib\jcommon-1.0.23.jar'
  $jfreeSrc=Join-Path $root 'src\com\cypress\ezpdanalyzer\ui\jfreechart'
  & $javac --release 8 -encoding UTF-8 -cp "$jfree;$jcommon" -d $c `
    (Join-Path $jfreeSrc 'FirstMajorTickNumberAxis.java') `
    (Join-Path $jfreeSrc 'RightAlignedNumberAxis.java') `
    (Join-Path $jfreeSrc 'DomainAlignedNumberAxis.java') `
    (Join-Path $jfreeSrc 'DomainAxisSupport.java') `
    (Join-Path $jfreeSrc 'LegendTextBaselineSupport.java')
  if($LASTEXITCODE){ throw "Axis support compilation failed: $LASTEXITCODE" }
  & $javac --release 8 -encoding UTF-8 -cp "$(Join-Path $AppDir 'plugins\*')" -d $c `
    (Join-Path $root 'src\com\cypress\ezpdanalyzer\ui\views\ViewColumnWidthPersistence.java')
  if($LASTEXITCODE){ throw "View-width support compilation failed: $LASTEXITCODE" }
  & $javac --add-exports 'java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED' -source 17 -target 17 -encoding UTF-8 -d $t `
    (Join-Path $root 'tools\ViewColumnWidthPersistencePatcher.java') `
    (Join-Path $root 'tools\LegendPatcher.java')
  if($LASTEXITCODE){ throw "Repair patcher compilation failed: $LASTEXITCODE" }

  $details=Join-Path $x 'com\cypress\ezpdanalyzer\ui\views\DetailsView.class'
  $payload=Join-Path $x 'com\cypress\ezpdanalyzer\ui\views\PayloadView.class'
  & $java --add-exports 'java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED' -cp $t `
    ViewColumnWidthPersistencePatcher $details "$details.fixed" $payload "$payload.fixed"
  if($LASTEXITCODE){ throw "View-width patch failed: $LASTEXITCODE" }
  $chart=Join-Path $x 'com\cypress\ezpdanalyzer\ui\jfreechart\CyXYLineChart.class'
  & $java --add-exports 'java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED' -cp $t `
    LegendPatcher $chart "$chart.legend"
  if($LASTEXITCODE){ throw "Legend patch failed: $LASTEXITCODE" }

  $backup="$ui.pre-axis-view-widths-v1.0p.bak"
  if(!(Test-Path -LiteralPath $backup)){ Copy-Item -LiteralPath $ui -Destination $backup }
  $jfreeStage=Join-Path $s 'com\cypress\ezpdanalyzer\ui\jfreechart'
  $viewsStage=Join-Path $s 'com\cypress\ezpdanalyzer\ui\views'
  New-Item -ItemType Directory -Force $jfreeStage,$viewsStage | Out-Null
  Get-ChildItem -LiteralPath (Join-Path $c 'com\cypress\ezpdanalyzer\ui\jfreechart') -Filter '*.class' | Copy-Item -Destination $jfreeStage
  Copy-Item -LiteralPath "$chart.legend" -Destination (Join-Path $jfreeStage 'CyXYLineChart.class')
  Copy-Item -LiteralPath "$details.fixed" -Destination (Join-Path $viewsStage 'DetailsView.class')
  Copy-Item -LiteralPath "$payload.fixed" -Destination (Join-Path $viewsStage 'PayloadView.class')
  Get-ChildItem -LiteralPath (Join-Path $c 'com\cypress\ezpdanalyzer\ui\views') -Filter 'ViewColumnWidthPersistence*.class' | Copy-Item -Destination $viewsStage
  & $jar uf $ui -C $s 'com/cypress/ezpdanalyzer/ui/jfreechart' -C $s 'com/cypress/ezpdanalyzer/ui/views'
  if($LASTEXITCODE){ throw "UI JAR update failed: $LASTEXITCODE" }

  $axisVerify=(& $javap -classpath $ui -p -c 'com.cypress.ezpdanalyzer.ui.jfreechart.DomainAxisSupport' | Out-String)
  $chartVerify=(& $javap -classpath $ui -p -c 'com.cypress.ezpdanalyzer.ui.jfreechart.CyXYLineChart' | Out-String)
  $detailsVerify=(& $javap -classpath $ui -p -c 'com.cypress.ezpdanalyzer.ui.views.DetailsView' | Out-String)
  $payloadVerify=(& $javap -classpath $ui -p -c 'com.cypress.ezpdanalyzer.ui.views.PayloadView' | Out-String)
  if($axisVerify -notmatch 'FirstMajorTickNumberAxis'){ throw 'Left-axis correction verification failed.' }
  foreach($needle in @('LegendTextBaselineSupport.adjust','CC1(mV)','CC2(mV)','VBUS(mV)','VBUS(mA)')){
    if($chartVerify -notmatch [regex]::Escape($needle)){ throw "Legend verification failed: $needle" }
  }
  if($detailsVerify -notmatch 'ViewColumnWidthPersistence' -or $payloadVerify -notmatch 'ViewColumnWidthPersistence'){
    throw 'Details/Payload width-persistence verification failed.'
  }
Write-Host 'REPAIR SUCCESS: all major tick labels moved up 4px; legend units and text baseline updated; Details/Payload widths are remembered.'
}
finally {
  if(Test-Path -LiteralPath $w){ Remove-Item -LiteralPath $w -Recurse -Force -ErrorAction SilentlyContinue }
}
