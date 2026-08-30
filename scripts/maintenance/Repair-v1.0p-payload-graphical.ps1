param([string]$AppDir='C:\Infineon\Tools\EZ-PD Protocol Analyzer Utility')

$ErrorActionPreference='Stop'
$micro=[char]0x00B5
$AppDir=(Resolve-Path -LiteralPath $AppDir).Path
$ui=Join-Path $AppDir 'plugins\com.cypress.ezpdanalyzer.ui_1.0.0.202502261407.jar'
$root=Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path))
$jar=(Get-Command jar -ErrorAction Stop).Source
$javac=(Get-Command javac -ErrorAction Stop).Source
$java=(Get-Command java -ErrorAction Stop).Source
$javap=(Get-Command javap -ErrorAction Stop).Source

function Test-Utf8TextInFile([string]$path, [string]$text) {
  $bytes=[System.IO.File]::ReadAllBytes($path)
  $needle=[System.Text.Encoding]::UTF8.GetBytes($text)
  if($needle.Length -eq 0 -or $bytes.Length -lt $needle.Length){ return $false }
  for($i=0;$i -le ($bytes.Length-$needle.Length);$i++){
    $matches=$true
    for($j=0;$j -lt $needle.Length;$j++){
      if($bytes[$i+$j] -ne $needle[$j]){ $matches=$false; break }
    }
    if($matches){ return $true }
  }
  return $false
}

if(Get-Process -Name 'EZ_PD_Protocol_Analyzer_Utility','EZ_PD_Protocol_Analyzer_Utilityc' -ErrorAction SilentlyContinue){
  throw 'Close Analyzer first.'
}
if(!(Test-Path -LiteralPath $ui)){ throw "UI plug-in JAR not found: $ui" }

$w=Join-Path $env:TEMP ('ezpd-payload-graphical-repair-'+[guid]::NewGuid().ToString('N'))
$x=Join-Path $w 'extract'
$c=Join-Path $w 'classes'
$t=Join-Path $w 'tools'
$s=Join-Path $w 'stage'
$viewPath='com/cypress/ezpdanalyzer/ui/views'
New-Item -ItemType Directory -Force $x,$c,$t,(Join-Path $s $viewPath) | Out-Null

try {
  Push-Location $x
  try {
    & $jar tf $ui "$viewPath/PayloadView.class" | Out-Null
    if($LASTEXITCODE){ throw "UI inspection failed: $LASTEXITCODE" }
  } finally { Pop-Location }

  & $javac --release 8 -encoding UTF-8 -cp "$(Join-Path $AppDir 'plugins\*')" -d $c `
    (Join-Path $root 'src\com\cypress\ezpdanalyzer\ui\views\PayloadTableSupport.java') `
    (Join-Path $root 'src\com\cypress\ezpdanalyzer\ui\views\GraphicalHeaderSupport.java')
  if($LASTEXITCODE){ throw "Payload/Graphical support compilation failed: $LASTEXITCODE" }

  $backup="$ui.pre-payload-graphical-v1.0p.bak"
  if(!(Test-Path -LiteralPath $backup)){ Copy-Item -LiteralPath $ui -Destination $backup }
  $viewStage=Join-Path $s $viewPath
  Get-ChildItem -LiteralPath (Join-Path $c $viewPath) -Filter 'PayloadTableSupport*.class' | Copy-Item -Destination $viewStage
  Get-ChildItem -LiteralPath (Join-Path $c $viewPath) -Filter 'GraphicalHeaderSupport*.class' | Copy-Item -Destination $viewStage
  & $jar uf $ui -C $s $viewPath
  if($LASTEXITCODE){ throw "UI JAR update failed: $LASTEXITCODE" }

  $payloadSupportText=(& $javap -classpath $c -p -c 'com.cypress.ezpdanalyzer.ui.views.PayloadTableSupport' | Out-String)
  if($payloadSupportText -notmatch 'addListener'){ throw 'Payload owner-draw alignment support verification failed.' }
  $headerSupport=Join-Path $c "$viewPath\GraphicalHeaderSupport.class"
  if(!(Test-Utf8TextInFile $headerSupport $micro)){ throw 'Initial Delta Y unit verification failed.' }
  Write-Host 'REPAIR SUCCESS: Payload Byte Index data cells and zero-value Delta units are updated.'
}
finally {
  if(Test-Path -LiteralPath $w){ Remove-Item -LiteralPath $w -Recurse -Force -ErrorAction SilentlyContinue }
}
