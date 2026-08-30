param([string]$AppDir='C:\Infineon\Tools\EZ-PD Protocol Analyzer Utility')

$ErrorActionPreference='Stop'
$AppDir=(Resolve-Path -LiteralPath $AppDir).Path
$ui=Join-Path $AppDir 'plugins\com.cypress.ezpdanalyzer.ui_1.0.0.202502261407.jar'
$root=Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path))
$jar=(Get-Command jar -ErrorAction Stop).Source
$javac=(Get-Command javac -ErrorAction Stop).Source
$java=(Get-Command java -ErrorAction Stop).Source

if(Get-Process -Name 'EZ_PD_Protocol_Analyzer_Utility','EZ_PD_Protocol_Analyzer_Utilityc' -ErrorAction SilentlyContinue){
  throw 'Close Analyzer first.'
}
if(!(Test-Path -LiteralPath $ui)){ throw "UI plug-in JAR not found: $ui" }

$w=Join-Path $env:TEMP ('ezpd-table-width-repair-'+[guid]::NewGuid().ToString('N'))
$x=Join-Path $w 'extract'
$c=Join-Path $w 'classes'
$t=Join-Path $w 'tools'
$s=Join-Path $w 'stage'
New-Item -ItemType Directory -Force $x,$c,$t,(Join-Path $s 'com\cypress\ezpdanalyzer\ui\nattable') | Out-Null

try {
  Push-Location $x
  try {
    & $jar xf $ui 'com/cypress/ezpdanalyzer/ui/nattable/CreateUSBPackageNatTable.class'
    if($LASTEXITCODE){ throw "UI extraction failed: $LASTEXITCODE" }
  } finally { Pop-Location }

  & $javac --release 8 -encoding UTF-8 -cp "$(Join-Path $AppDir 'plugins\*')" -d $c `
    (Join-Path $root 'src\com\cypress\ezpdanalyzer\ui\nattable\UsbPacketTableSupport.java')
  if($LASTEXITCODE){ throw "USB PD Messages support compilation failed: $LASTEXITCODE" }

  & $javac --add-exports 'java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED' -source 17 -target 17 -encoding UTF-8 -d $t `
    (Join-Path $root 'tools\ColumnWidthPersistencePatcher.java')
  if($LASTEXITCODE){ throw "Column-width patcher compilation failed: $LASTEXITCODE" }

  $table=Join-Path $x 'com\cypress\ezpdanalyzer\ui\nattable\CreateUSBPackageNatTable.class'
  & $java --add-exports 'java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED' -cp $t `
    ColumnWidthPersistencePatcher $table "$table.patched"
  if($LASTEXITCODE){ throw "Column-width patch failed: $LASTEXITCODE" }

  $backup="$ui.pre-column-width-persistence-v1.0p.bak"
  if(!(Test-Path -LiteralPath $backup)){ Copy-Item -LiteralPath $ui -Destination $backup }
  $tableStage=Join-Path $s 'com\cypress\ezpdanalyzer\ui\nattable'
  Copy-Item -LiteralPath "$table.patched" -Destination (Join-Path $tableStage 'CreateUSBPackageNatTable.class')
  Get-ChildItem -LiteralPath (Join-Path $c 'com\cypress\ezpdanalyzer\ui\nattable') -Filter 'UsbPacketTableSupport*.class' | Copy-Item -Destination $tableStage
  & $jar uf $ui -C $s 'com/cypress/ezpdanalyzer/ui/nattable'
  if($LASTEXITCODE){ throw "UI JAR update failed: $LASTEXITCODE" }

  $verify=(& javap -classpath $ui -p -c 'com.cypress.ezpdanalyzer.ui.nattable.CreateUSBPackageNatTable' | Out-String)
  if($verify -notmatch [regex]::Escape('UsbPacketTableSupport.installColumnWidthPersistence')){
    throw 'Column-width persistence verification failed.'
  }
  Write-Host 'REPAIR SUCCESS: USB PD Messages column widths are now remembered.'
}
finally {
  if(Test-Path -LiteralPath $w){ Remove-Item -LiteralPath $w -Recurse -Force -ErrorAction SilentlyContinue }
}
