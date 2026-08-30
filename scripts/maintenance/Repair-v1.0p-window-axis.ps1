param([string]$AppDir='C:\Infineon\Tools\EZ-PD Protocol Analyzer Utility')
$ErrorActionPreference='Stop'; $AppDir=(Resolve-Path -LiteralPath $AppDir).Path
$ui=Join-Path $AppDir 'plugins\com.cypress.ezpdanalyzer.ui_1.0.0.202502261407.jar'
$app=Join-Path $AppDir 'plugins\com.cypress.ezpdanalyzer.appln_1.0.0.202502261407.jar'
$root=Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path))
$jar=(Get-Command jar -ErrorAction Stop).Source; $javac=(Get-Command javac -ErrorAction Stop).Source; $java=(Get-Command java -ErrorAction Stop).Source
if(Get-Process -Name 'EZ_PD_Protocol_Analyzer_Utility','EZ_PD_Protocol_Analyzer_Utilityc' -ErrorAction SilentlyContinue){throw 'Close Analyzer first.'}
$w=Join-Path $env:TEMP ('ezpd-repair-'+[guid]::NewGuid().ToString('N')); $x=Join-Path $w x; $c=Join-Path $w c; $t=Join-Path $w t; New-Item -ItemType Directory -Force $x,$c,$t|Out-Null
try{
 Push-Location $x; try{&$jar xf $ui 'lib/jcommon-1.0.23.jar' 'lib/jfreechart-1.5.3.jar' 'com/cypress/ezpdanalyzer/ui/jfreechart/RightAlignedNumberAxis.class'; if($LASTEXITCODE){throw 'UI extract failed'}; &$jar xf $app 'com/cypress/ezpdanalyzer/appln/ApplicationWorkbenchWindowAdvisor.class'; if($LASTEXITCODE){throw 'App extract failed'}}finally{Pop-Location}
 &$javac --release 8 -encoding UTF-8 -cp "$(Join-Path $x 'lib\jfreechart-1.5.3.jar');$(Join-Path $x 'lib\jcommon-1.0.23.jar')" -d $c (Join-Path $root 'src\com\cypress\ezpdanalyzer\ui\jfreechart\RightAlignedNumberAxis.java'); if($LASTEXITCODE){throw 'Axis compile failed'}
 &$javac --add-exports 'java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED' -source 17 -target 17 -d $t (Join-Path $root 'tools\WindowStatePatcher.java'); if($LASTEXITCODE){throw 'Window patcher compile failed'}
 $advisor=Join-Path $x 'com\cypress\ezpdanalyzer\appln\ApplicationWorkbenchWindowAdvisor.class'; &$java --add-exports 'java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED' -cp $t WindowStatePatcher $advisor "$advisor.fixed"; if($LASTEXITCODE){throw 'Window patch failed'}
 $s=Join-Path $w s; New-Item -ItemType Directory -Force (Join-Path $s 'com\cypress\ezpdanalyzer\ui\jfreechart'),(Join-Path $s 'com\cypress\ezpdanalyzer\appln')|Out-Null; Copy-Item (Join-Path $c 'com\cypress\ezpdanalyzer\ui\jfreechart\RightAlignedNumberAxis.class') (Join-Path $s 'com\cypress\ezpdanalyzer\ui\jfreechart\'); Copy-Item "$advisor.fixed" (Join-Path $s 'com\cypress\ezpdanalyzer\appln\ApplicationWorkbenchWindowAdvisor.class'); &$jar uf $ui -C $s 'com/cypress/ezpdanalyzer/ui/jfreechart/RightAlignedNumberAxis.class'; &$jar uf $app -C $s 'com/cypress/ezpdanalyzer/appln/ApplicationWorkbenchWindowAdvisor.class'; Write-Host 'REPAIR SUCCESS: right-axis decimals and remembered window size enabled.'
}finally{if(Test-Path $w){Remove-Item $w -Recurse -Force -ErrorAction SilentlyContinue}}
