param(
  [Parameter(ValueFromRemainingArguments = $true)]
  [string[]]$Arguments
)
$ErrorActionPreference = "Stop"

$DefaultAppDir = "C:\Infineon\Tools\EZ-PD Protocol Analyzer Utility"
$AppDir = $DefaultAppDir
$JavaHome = ""
$NoLaunch = $false
$EnableTriggers = $false
$EnableTerminations = $false
$InvalidArgument = $null
$ShowHelp = $false

function Show-Usage {
  Write-Host "EZ-PD Protocol Analyzer Utility 4.2.0 - Community Patch v1.0p"
  Write-Host ""
  Write-Host "Usage:"
  Write-Host "  Patch-Analyzer.cmd [options]"
  Write-Host ""
  Write-Host "Options:"
  Write-Host "  -Help, -h, /?                  Show this help. No files are changed."
  Write-Host "  -EnableTriggers                Enable the hidden Triggers view."
  Write-Host "  -EnableTerminations            Enable the hidden Terminations view."
  Write-Host "  -AppDir <directory>            Analyzer install directory."
  Write-Host "                                 Default: $DefaultAppDir"
  Write-Host "  -JavaHome <directory>          JDK directory; default is the JDK on PATH."
  Write-Host "  -NoLaunch                      Patch but do not start the Analyzer."
  Write-Host ""
  Write-Host "Examples:"
  Write-Host "  Patch-Analyzer.cmd"
  Write-Host "  Patch-Analyzer.cmd -EnableTriggers"
  Write-Host "  Patch-Analyzer.cmd -EnableTriggers -EnableTerminations"
  Write-Host "  Patch-Analyzer.cmd -AppDir `"D:\My Tools\EZ-PD Protocol Analyzer Utility`""
  Write-Host ""
  Write-Host "Unknown, misspelled, or incomplete options stop before any files are changed."
}

for ($argumentIndex = 0; $argumentIndex -lt $Arguments.Count; $argumentIndex++) {
  $argument = [string]$Arguments[$argumentIndex]
  switch ($argument.ToLowerInvariant()) {
    '-help' { $ShowHelp = $true; continue }
    '-h' { $ShowHelp = $true; continue }
    '-?' { $ShowHelp = $true; continue }
    '/?' { $ShowHelp = $true; continue }
    '-enabletriggers' { $EnableTriggers = $true; continue }
    '-enableterminations' { $EnableTerminations = $true; continue }
    '-nolaunch' { $NoLaunch = $true; continue }
    '-appdir' {
      if ($argumentIndex + 1 -ge $Arguments.Count) {
        $InvalidArgument = '-AppDir requires a directory path.'
        break
      }
      $argumentIndex++
      $AppDir = [string]$Arguments[$argumentIndex]
      if ([string]::IsNullOrWhiteSpace($AppDir) -or $AppDir.StartsWith('-')) {
        $InvalidArgument = '-AppDir requires a directory path.'
        break
      }
      continue
    }
    '-javahome' {
      if ($argumentIndex + 1 -ge $Arguments.Count) {
        $InvalidArgument = '-JavaHome requires a JDK directory path.'
        break
      }
      $argumentIndex++
      $JavaHome = [string]$Arguments[$argumentIndex]
      if ([string]::IsNullOrWhiteSpace($JavaHome) -or $JavaHome.StartsWith('-')) {
        $InvalidArgument = '-JavaHome requires a JDK directory path.'
        break
      }
      continue
    }
    default {
      $InvalidArgument = "Unknown option or unexpected value: $argument"
      break
    }
  }
  if ($InvalidArgument) { break }
}

if ($InvalidArgument) {
  Write-Host "ERROR: $InvalidArgument" -ForegroundColor Red
  Write-Host ""
  Show-Usage
  exit 2
}
if ($ShowHelp) {
  Show-Usage
  exit 0
}

# Resolve once so a user-supplied relative path remains valid while this script
# temporarily changes its working directory during JAR extraction and updates.
$AppDir = (Resolve-Path -LiteralPath $AppDir).Path

$JarName = "com.cypress.ezpdanalyzer.ui_1.0.0.202502261407.jar"
$JarPath = Join-Path $AppDir "plugins\$JarName"
$ExePath = Join-Path $AppDir "EZ_PD_Protocol_Analyzer_Utility.exe"
$BackupPath = "$JarPath.pre-community-patch-v1.0p-final.bak"
$CsvEntry = 'com/cypress/ezpdanalyzer/ui/handler/ExcelExportHandler$1.class'
$GraphEntry = 'com/cypress/ezpdanalyzer/ui/jfreechart/CyXYLineChart.class'
$GraphCC1Entry = 'com/cypress/ezpdanalyzer/ui/views/GraphSelectorComposite$1.class'
$GraphCC2Entry = 'com/cypress/ezpdanalyzer/ui/views/GraphSelectorComposite$2.class'
$GraphVBUSCheckEntry = 'com/cypress/ezpdanalyzer/ui/views/GraphSelectorComposite$3.class'
$GraphAMPCheckEntry = 'com/cypress/ezpdanalyzer/ui/views/GraphSelectorComposite$4.class'
$StockCsvSha = "fb25f1ff39e0658ca87cc338457cf7432fb5c9d6c71c562c52eb265f1367106f"
$StockGraphSha = "41074c261a64ae370e25ce6c2809e9fd0fd8fbd725e2ea97c39e2a1342779ba9"
$StockCC2ListenerSha = "f98de86433624a34c1e5b2f27a0d239c6c7e38d2bc9208473ec0be4d75df7edc"
$StockVBUSListenerSha = "0cb2842dc6f3ada95a6f2d042e1a67cfffb4b2ac0a3e8d95928f088fcbe159d0"
$StockAMPListenerSha = "e3dfa7543496f32c901e642ca1baf1c11c0512f4081375c08198e5e9df1275b1"
$GraphAfterVoltSha = "dc7d147126e922eb655405bdefdfc8fe98749d23977be4a667b23fb9141a2abd"
$GraphNavigationSupportEntry = "com/cypress/ezpdanalyzer/ui/jfreechart/GraphNavigationSupport.class"
$GraphStopSupportEntry = "com/cypress/ezpdanalyzer/ui/jfreechart/GraphStopSupport.class"
$GraphUiCosmeticSupportEntry = "com/cypress/ezpdanalyzer/ui/jfreechart/GraphUiCosmeticSupport.class"
$GraphUiCosmeticFormatEntry = 'com/cypress/ezpdanalyzer/ui/jfreechart/GraphUiCosmeticSupport$FigureSpaceNumberFormat.class'
$StopEntry = "com/cypress/ezpdanalyzer/ui/handler/StopHandler.class"

function Sha256([string]$p) {
  $sha=[System.Security.Cryptography.SHA256]::Create()
  try {
    $s=[System.IO.File]::OpenRead($p)
    try {
      return ([BitConverter]::ToString($sha.ComputeHash($s))).Replace("-","").ToLowerInvariant()
    } finally {
      $s.Dispose()
    }
  } finally {
    $sha.Dispose()
  }
}

function Replace-ExactlyOneRegex(
  [string]$Text,
  [string]$Pattern,
  [string]$Replacement,
  [string]$Label
) {
  $rx = New-Object System.Text.RegularExpressions.Regex(
    $Pattern,
    [System.Text.RegularExpressions.RegexOptions]::Singleline
  )
  $count = $rx.Matches($Text).Count
  if ($count -ne 1) {
    throw "${Label}: expected exactly one stock block, found $count"
  }
  return $rx.Replace($Text, $Replacement, 1)
}

function Assert-XmlNodeCount(
  [xml]$Doc,
  [string]$XPath,
  [int]$Expected,
  [string]$Label
) {
  $nodes = $Doc.SelectNodes($XPath)
  $count = if ($null -eq $nodes) { 0 } else { $nodes.Count }
  if ($count -ne $Expected) {
    throw "${Label}: expected $Expected XML node(s), found $count"
  }
}

Write-Host "EZ-PD Protocol Analyzer Utility 4.2.0 - integrated FINAL v1.0p"
Write-Host "Target JAR: $JarPath"
Write-Host "Triggers: $EnableTriggers | Terminations: $EnableTerminations"
Write-Host ""

if (!(Test-Path -LiteralPath $JarPath)) {
  throw "Target JAR not found: $JarPath"
}
if (!(Test-Path -LiteralPath $ExePath)) {
  throw "Analyzer EXE not found: $ExePath"
}

$running = Get-Process -Name `
  'EZ_PD_Protocol_Analyzer_Utility', `
  'EZ_PD_Protocol_Analyzer_Utilityc' `
  -ErrorAction SilentlyContinue
if ($running) {
  $running | Format-Table Id,ProcessName,MainWindowTitle -AutoSize
  throw "Analyzer Utility is running. Close it before patching."
}

if ($JavaHome) {
  $java=Join-Path $JavaHome "bin\java.exe"
  $javac=Join-Path $JavaHome "bin\javac.exe"
  $jar=Join-Path $JavaHome "bin\jar.exe"
  $javap=Join-Path $JavaHome "bin\javap.exe"
} else {
  $java=(Get-Command java.exe -ErrorAction Stop).Source
  $javac=(Get-Command javac.exe -ErrorAction Stop).Source
  $jar=(Get-Command jar.exe -ErrorAction Stop).Source
  $javap=(Get-Command javap.exe -ErrorAction Stop).Source
}

Write-Host "java : $java"
Write-Host "javac: $javac"
Write-Host "jar  : $jar"
Write-Host "javap: $javap"
Write-Host ""

$Here=Split-Path -Parent $MyInvocation.MyCommand.Path
$Work=Join-Path $env:TEMP ("ezpd-patch-v08-"+[guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory $Work | Out-Null

try {
  Write-Host "[1/21] Create immutable backup"
  if (!(Test-Path -LiteralPath $BackupPath)) {
    Copy-Item -LiteralPath $JarPath -Destination $BackupPath
    Write-Host "        $BackupPath"
  } else {
    Write-Host "        Existing v0.8 backup kept: $BackupPath"
  }

  Write-Host "[2/21] Extract stock CSV/Graph/STOP classes + plugin.xml"
  Push-Location $Work
  try {
    & $jar xf $JarPath $CsvEntry $GraphEntry $StopEntry $GraphCC1Entry $GraphCC2Entry $GraphVBUSCheckEntry $GraphAMPCheckEntry "plugin.xml"
    if ($LASTEXITCODE -ne 0) { throw "jar extract failed: $LASTEXITCODE" }
  } finally {
    Pop-Location
  }

  $CsvPath=Join-Path $Work ($CsvEntry -replace '/','\')
  $Plugin=Join-Path $Work "plugin.xml"
  $GraphPath=Join-Path $Work ($GraphEntry -replace '/','\')
  $GraphCC1Path=Join-Path $Work ($GraphCC1Entry -replace '/','\')
  $GraphCC2Path=Join-Path $Work ($GraphCC2Entry -replace '/','\')
  $GraphVBUSCheckPath=Join-Path $Work ($GraphVBUSCheckEntry -replace '/','\')
  $GraphAMPCheckPath=Join-Path $Work ($GraphAMPCheckEntry -replace '/','\')
  $StopPath=Join-Path $Work ($StopEntry -replace '/','\')

  if (!(Test-Path -LiteralPath $CsvPath)) { throw "CSV class extraction failed." }
  if (!(Test-Path -LiteralPath $Plugin)) { throw "plugin.xml extraction failed." }
  if (!(Test-Path -LiteralPath $GraphPath)) { throw "CyXYLineChart.class extraction failed." }
  if (!(Test-Path -LiteralPath $StopPath)) { throw "StopHandler.class extraction failed." }
  foreach ($listenerPath in @(
    $GraphCC1Path,
    $GraphCC2Path,
    $GraphVBUSCheckPath,
    $GraphAMPCheckPath
  )) {
    if (!(Test-Path -LiteralPath $listenerPath)) {
      throw "Graph checkbox listener extraction failed: $listenerPath"
    }
  }

  $sha=Sha256 $CsvPath
  Write-Host "        ExcelExportHandler`$1 SHA256: $sha"
  if ($sha -ne $StockCsvSha) {
    throw (
      "CSV exporter is not the confirmed STOCK 4.2.0 class.`n" +
      "Expected: $StockCsvSha`nActual:   $sha`n" +
      "Restore stock 4.2.0 before applying v0.8."
    )
  }

  $graphSha=Sha256 $GraphPath
  Write-Host "        CyXYLineChart SHA256: $graphSha"
  if ($graphSha -ne $StockGraphSha) {
    throw (
      "CyXYLineChart is not confirmed STOCK 4.2.0.`n" +
      "Expected: $StockGraphSha`nActual:   $graphSha"
    )
  }

  $graphStockJavap = & $javap -classpath $JarPath -c -p `
    "com.cypress.ezpdanalyzer.ui.jfreechart.CyXYLineChart" 2>&1
  $graphStockText = $graphStockJavap -join "`n"

  $voltCallsStock = @(
    $graphStockJavap |
      Select-String 'IData\.getVolt:\(\)S'
  ).Count

  $ampCallsStock = @(
    $graphStockJavap |
      Select-String 'IData\.getAmp:\(\)S'
  ).Count

  $voltThenI2d = [regex]::Matches(
    $graphStockText,
    'IData\.getVolt:\(\)S[^\r\n]*\r?\n\s*\d+:\s+i2d'
  ).Count

  $stockIand = @(
    $graphStockJavap |
      Select-String '^\s*\d+:\s+iand\s*$'
  ).Count

  if ($voltCallsStock -ne 2) {
    throw "Graph stock guard failed: expected 2 IData.getVolt():S calls, found $voltCallsStock"
  }
  if ($ampCallsStock -ne 2) {
    throw "Graph stock guard failed: expected 2 IData.getAmp():S calls, found $ampCallsStock"
  }
  if ($voltThenI2d -ne 2) {
    throw "Graph stock guard failed: both getVolt():S calls must be immediately followed by i2d; matched $voltThenI2d"
  }
  if ($stockIand -ne 0) {
    throw "Graph stock guard failed: expected 0 stock IAND instructions, found $stockIand"
  }

  # CC1 is the primary/base dataset. Unlike CC2/VBUS/AMP, the stock CC1
  # checkbox already does NOT call a create*Axix() method. Verify that before
  # touching the other three listeners.
  $cc1Stock = & $javap -classpath $JarPath -c -p `
    'com.cypress.ezpdanalyzer.ui.views.GraphSelectorComposite$1' 2>&1

  $cc1SetStock = @(
    $cc1Stock | Select-String 'CyXYLineChart\.setCc1Enable:\(Z\)V'
  ).Count
  $cc1RefreshStock = @(
    $cc1Stock | Select-String 'CyXYLineChart\.refreshGraph:\(\)V'
  ).Count
  $cc1AxisStock = @(
    $cc1Stock | Select-String 'CyXYLineChart\.create[A-Za-z0-9]+Axix:\(Z\)V'
  ).Count

  if ($cc1SetStock -ne 2) {
    throw "CC1 checkbox stock guard failed: setCc1Enable=$cc1SetStock"
  }
  if ($cc1RefreshStock -ne 2) {
    throw "CC1 checkbox stock guard failed: refreshGraph=$cc1RefreshStock"
  }
  if ($cc1AxisStock -ne 0) {
    throw "CC1 checkbox stock guard failed: unexpected create*Axix calls=$cc1AxisStock"
  }

  Write-Host "        CC1 checkbox: already enable+refresh only (no axis recreation)"

  $cc2Sha=Sha256 $GraphCC2Path
  $vbusCheckSha=Sha256 $GraphVBUSCheckPath
  $ampCheckSha=Sha256 $GraphAMPCheckPath

  Write-Host "        CC2 listener SHA256 : $cc2Sha"
  Write-Host "        VBUS listener SHA256: $vbusCheckSha"
  Write-Host "        AMP listener SHA256 : $ampCheckSha"

  if ($cc2Sha -ne $StockCC2ListenerSha) {
    throw "CC2 listener is not confirmed STOCK 4.2.0."
  }
  if ($vbusCheckSha -ne $StockVBUSListenerSha) {
    throw "VBUS listener is not confirmed STOCK 4.2.0."
  }
  if ($ampCheckSha -ne $StockAMPListenerSha) {
    throw "AMP listener is not confirmed STOCK 4.2.0."
  }

  # CC2 is also already non-destructive in stock 4.2.0.
  $cc2Stock = & $javap -classpath $JarPath -c -p `
    'com.cypress.ezpdanalyzer.ui.views.GraphSelectorComposite$2' 2>&1

  $cc2SetStock = @(
    $cc2Stock | Select-String 'CyXYLineChart\.setCc2Enable:\(Z\)V'
  ).Count
  $cc2RefreshStock = @(
    $cc2Stock | Select-String 'CyXYLineChart\.refreshGraph:\(\)V'
  ).Count
  $cc2AxisStock = @(
    $cc2Stock | Select-String 'CyXYLineChart\.create[A-Za-z0-9]+Axix:\(Z\)V'
  ).Count

  if ($cc2SetStock -ne 2) {
    throw "CC2 checkbox stock guard failed: setCc2Enable=$cc2SetStock"
  }
  if ($cc2RefreshStock -ne 2) {
    throw "CC2 checkbox stock guard failed: refreshGraph=$cc2RefreshStock"
  }
  if ($cc2AxisStock -ne 0) {
    throw "CC2 checkbox stock guard failed: unexpected create*Axix calls=$cc2AxisStock"
  }

  Write-Host "        CC2 checkbox: already enable+refresh only (no axis recreation)"

  $stopStock = & $javap -classpath $JarPath -c -p `
    "com.cypress.ezpdanalyzer.ui.handler.StopHandler" 2>&1
  $stopFlushStock = @(
    $stopStock | Select-String 'CachedDataListManager\.processAndSavePrimaryBuffer:\(\)V'
  ).Count
  $stopSupportStock = @(
    $stopStock | Select-String 'GraphStopSupport\.'
  ).Count
  if ($stopFlushStock -ne 1 -or $stopSupportStock -ne 0) {
    throw "STOP stock guard failed: vendorFlush=$stopFlushStock supportCalls=$stopSupportStock"
  }
  Write-Host "        StopHandler: confirmed single vendor final-buffer flush"

  Write-Host "[3/21] Verify hidden stock UI blocks"
  $xml=[IO.File]::ReadAllText($Plugin)

  # Stock 4.2.0 deliberately comments out both view registrations.
  $triggerViewComment = '(?s)<!--\s*(<view\s+class="com\.cypress\.ezpdanalyzer\.ui\.views\.TriggerView"\s+id="com\.cypress\.ezpdanalyzer\.ui\.views\.TriggerView"\s+name="Triggers"\s+restorable="true">\s*</view>)\s*-->'
  $termViewComment = '(?s)<!--\s*(<view\s+class="com\.cypress\.ezpdanalyzer\.ui\.views\.Terminations"\s+id="com\.cypress\.ezpdanalyzer\.ui\.views\.Terminations"\s+name="Terminations"\s+restorable="true">\s*</view>)\s*-->'

  # Stock 4.2.0 also comments out the corresponding Window > Show View commands.
  $triggerMenuComment = '(?s)<!--\s*(<command\s+commandId="com\.cypress\.ezpdanalyzer\.ui\.triggerviewcommand"\s+style="push">\s*</command>)\s*-->'
  $termMenuComment = '(?s)<!--\s*(<command\s+commandId="com\.cypress\.ezpdanalyzer\.ui\.terminationscmd"\s+style="push">\s*</command>)\s*-->'

  # Stock 4.2.0 also comments out both initial Perspective placements.
  # Distinguish these from org.eclipse.ui.views registrations by requiring
  # the perspective-only closeable= attribute and the original stable view ID.
  $triggerPerspectiveComment = '(?s)<!--\s*(<view\s+closeable="true"\s+id="com\.cypress\.ezpdanalyzer\.ui\.views\.TriggerView"\s+minimized="false"\s+moveable="true"\s+relationship="stack"\s+relative="com\.cypress\.ezpdanalyzer\.ui\.views\.DetailsView"\s+showTitle="true"\s+standalone="true"\s+visible="true">\s*</view>)\s*-->'
  $termPerspectiveComment = '(?s)<!--\s*(<view\s+closeable="true"\s+id="com\.cypress\.ezpdanalyzer\.ui\.views\.Terminations"\s+minimized="false"\s+moveable="true"\s+relationship="stack"\s+relative="com\.cypress\.ezpdanalyzer\.ui\.views\.DetailsView"\s+showTitle="true"\s+standalone="true"\s+visible="true">\s*</view>)\s*-->'

  # Each advanced view is fully independent.  A disabled view stays exactly
  # as stock: its registration, Show View command, and perspective placement
  # remain inside the original XML comments.
  $advancedChecks = @()
  if ($EnableTriggers) {
    $advancedChecks += @(
      @($triggerViewComment, "Trigger view comment"),
      @($triggerMenuComment, "Trigger Show View comment"),
      @($triggerPerspectiveComment, "Trigger perspective comment")
    )
  }
  if ($EnableTerminations) {
    $advancedChecks += @(
      @($termViewComment, "Terminations view comment"),
      @($termMenuComment, "Terminations Show View comment"),
      @($termPerspectiveComment, "Terminations perspective comment")
    )
  }
  foreach ($check in $advancedChecks) {
    $rx = New-Object System.Text.RegularExpressions.Regex(
      $check[0],
      [System.Text.RegularExpressions.RegexOptions]::Singleline
    )
    $n = $rx.Matches($xml).Count
    if ($n -ne 1) {
      throw "$($check[1]): expected exactly one stock commented block, found $n"
    }
  }

  Write-Host "[4/21] Compile optional restored view classes"
  $Classes=Join-Path $Work "classes"
  New-Item -ItemType Directory $Classes | Out-Null
  $src=Join-Path $Here "src\com\cypress\ezpdanalyzer\ui\views"
  $sources=@()
  if ($EnableTriggers -or $EnableTerminations) {
    $sources += (Join-Path $src "AdvancedFeatureUsbSync.java")
  }
  if ($EnableTriggers) { $sources += (Join-Path $src "TriggerViewFixed.java") }
  if ($EnableTerminations) { $sources += (Join-Path $src "TerminationsFixed.java") }
  if ($sources.Count -gt 0) {
    & $javac --release 8 -encoding UTF-8 -classpath (Join-Path $AppDir "plugins\*") -d $Classes @sources
    if ($LASTEXITCODE -ne 0) { throw "optional view javac failed: $LASTEXITCODE" }
  }

  $graphSupportSources = @(
    (Join-Path $Here "src\com\cypress\ezpdanalyzer\ui\jfreechart\GraphNavigationSupport.java"),
    (Join-Path $Here "src\com\cypress\ezpdanalyzer\ui\jfreechart\GraphStopSupport.java"),
    (Join-Path $Here "src\com\cypress\ezpdanalyzer\ui\jfreechart\GraphUiCosmeticSupport.java")
  )

  & $javac --release 8 -encoding UTF-8 -d $Classes @graphSupportSources
  if ($LASTEXITCODE -ne 0) {
    throw "Graph support javac failed: $LASTEXITCODE"
  }

  if ($EnableTriggers -or $EnableTerminations) {
    # Verify the compiled helper keeps the proven public javax.usb event path.
    $usbSyncJavap = & $javap -classpath "$Classes;$($AppDir)\plugins\*" -c -p -verbose `
      "com.cypress.ezpdanalyzer.ui.views.AdvancedFeatureUsbSync" 2>&1
    foreach ($apiName in @(
      "javax.usb.event.UsbServicesEvent",
      "javax.usb.UsbDevice",
      "javax.usb.UsbDeviceDescriptor"
    )) {
      if (@($usbSyncJavap | Select-String ([regex]::Escape($apiName))).Count -lt 1) {
        throw "AdvancedFeatureUsbSync compile verification failed: missing $apiName"
      }
    }
  }

  Write-Host "[5/21] Compile CSV + Graph bytecode transformers"
  $ToolClasses=Join-Path $Work "tool"
  New-Item -ItemType Directory $ToolClasses | Out-Null
  & $javac `
    --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED `
    -source 17 -target 17 -encoding UTF-8 `
    -d $ToolClasses `
    (Join-Path $Here "tools\ExcelCsvPatcher.java") `
    (Join-Path $Here "tools\GraphVoltPatcher.java") `
    (Join-Path $Here "tools\GraphCheckboxPatcher.java") `
    (Join-Path $Here "tools\GraphNavigationPatcher.java") `
    (Join-Path $Here "tools\GraphScrollRightPatcher.java") `
    (Join-Path $Here "tools\GraphStopHandlerPatcher.java") `
    (Join-Path $Here "tools\GraphUiPatcher.java")
  if ($LASTEXITCODE -ne 0) { throw "bytecode transformer javac failed: $LASTEXITCODE" }

  Write-Host "[6/21] Apply CSV End Time + Sno fixes"
  $Patched="$CsvPath.patched"
  & $java `
    --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED `
    -cp $ToolClasses `
    ExcelCsvPatcher `
    $CsvPath `
    $Patched
  if ($LASTEXITCODE -ne 0) { throw "CSV transform failed: $LASTEXITCODE" }
  Move-Item -Force -LiteralPath $Patched -Destination $CsvPath

  Write-Host "[7/21] Apply Graph >32.767V unsigned-VBUS fix"
  $PatchedGraph="$GraphPath.patched"
  & $java `
    --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED `
    -cp $ToolClasses `
    GraphVoltPatcher `
    $GraphPath `
    $PatchedGraph
  if ($LASTEXITCODE -ne 0) { throw "Graph transform failed: $LASTEXITCODE" }
  Move-Item -Force -LiteralPath $PatchedGraph -Destination $GraphPath

  Write-Host "[8/21] Verify transformed Graph bytecode"
  $GraphVerifyDir=Join-Path $Work "graphverify"
  New-Item -ItemType Directory $GraphVerifyDir | Out-Null
  $GraphVerifyClass=Join-Path $GraphVerifyDir ($GraphEntry -replace '/','\')
  New-Item -ItemType Directory -Force (Split-Path -Parent $GraphVerifyClass) | Out-Null
  Copy-Item -LiteralPath $GraphPath -Destination $GraphVerifyClass

  $graphPatchedJavap = & $javap -classpath $GraphVerifyDir -c -p `
    "com.cypress.ezpdanalyzer.ui.jfreechart.CyXYLineChart" 2>&1

  $voltCallsPatched = @(
    $graphPatchedJavap |
      Select-String 'IData\.getVolt:\(\)S'
  ).Count
  $ampCallsPatched = @(
    $graphPatchedJavap |
      Select-String 'IData\.getAmp:\(\)S'
  ).Count
  $iandPatched = @(
    $graphPatchedJavap |
      Select-String '^\s*\d+:\s+iand\s*$'
  ).Count
  $maskPatched = @(
    $graphPatchedJavap |
      Select-String 'int 65535'
  ).Count

  if ($voltCallsPatched -ne 2) { throw "Graph verify failed: getVolt=$voltCallsPatched" }
  if ($ampCallsPatched -ne 2) { throw "Graph verify failed: getAmp=$ampCallsPatched" }
  if ($iandPatched -ne 2) { throw "Graph verify failed: iand=$iandPatched" }
  if ($maskPatched -ne 2) { throw "Graph verify failed: 65535 masks=$maskPatched" }

  $graphAfterVoltSha=Sha256 $GraphPath
  Write-Host "        Graph-after-VBUS-fix SHA256: $graphAfterVoltSha"
  if ($graphAfterVoltSha -ne $GraphAfterVoltSha) {
    throw (
      "Unexpected CyXYLineChart after unsigned-VBUS patch.`n" +
      "Expected: $GraphAfterVoltSha`nActual:   $graphAfterVoltSha"
    )
  }

  Write-Host "[9/21] Apply Graph navigation/selection repair"
  $NavPatchedGraph="$GraphPath.navpatched"

  & $java `
    --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED `
    -cp $ToolClasses `
    GraphNavigationPatcher `
    $GraphPath `
    $NavPatchedGraph

  if ($LASTEXITCODE -ne 0) {
    throw "Graph navigation transform failed: $LASTEXITCODE"
  }

  Move-Item -Force -LiteralPath $NavPatchedGraph -Destination $GraphPath

  $RightPatchedGraph="$GraphPath.rightpatched"
  & $java `
    --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED `
    -cp $ToolClasses `
    GraphScrollRightPatcher `
    $GraphPath `
    $RightPatchedGraph
  if ($LASTEXITCODE -ne 0) {
    throw "Graph scrollRight transform failed: $LASTEXITCODE"
  }
  Move-Item -Force -LiteralPath $RightPatchedGraph -Destination $GraphPath

  Write-Host "[10/21] Verify Graph navigation/selection bytecode"
  Copy-Item -Force -LiteralPath $GraphPath -Destination $GraphVerifyClass

  $graphNavJavap = & $javap -classpath "$GraphVerifyDir;$Classes" -c -p `
    "com.cypress.ezpdanalyzer.ui.jfreechart.CyXYLineChart" 2>&1

  $graphNavText=$graphNavJavap -join "`n"

  $clearMarkersCalls=@(
    $graphNavJavap |
      Select-String 'Method clearMarkers:\(\)V'
  ).Count

  $snapCalls=@(
    $graphNavJavap |
      Select-String 'GraphNavigationSupport\.snapSelectionTime:\(Ljava/util/List;J\)J'
  ).Count

  $normalizeCalls=@(
    $graphNavJavap |
      Select-String 'GraphNavigationSupport\.normalizeAfterSelection:\(Ljava/lang/Object;Ljava/lang/Object;\)V'
  ).Count

  $stableStopDataCalls=@(
    $graphNavJavap |
      Select-String 'GraphStopSupport\.chooseGraphData:\(Ljava/lang/Object;Ljava/util/List;\)Ljava/util/List;'
  ).Count

  $staleAmpCalls=@(
    $graphNavJavap |
      Select-String 'Method setAmpSeriesRange:\(\)V'
  ).Count

  if ($clearMarkersCalls -ne 1) {
    throw "Graph navigation verify failed: clearMarkers calls=$clearMarkersCalls"
  }
  if ($snapCalls -ne 1) {
    throw "Graph navigation verify failed: snapSelectionTime calls=$snapCalls"
  }
  if ($normalizeCalls -ne 1) {
    throw "Graph navigation verify failed: normalizeAfterSelection calls=$normalizeCalls"
  }
  if ($stableStopDataCalls -ne 2) {
    throw "Graph navigation verify failed: chooseGraphData calls=$stableStopDataCalls"
  }
  if ($staleAmpCalls -ne 0) {
    throw "Graph navigation verify failed: stale setAmpSeriesRange calls=$staleAmpCalls"
  }

  $scrollLeftBlock=[regex]::Match(
    $graphNavText,
    '(?s)public void scrollLeft\(\);.*?(?=public void scrollRight\(\);)'
  ).Value

  if (!$scrollLeftBlock) {
    throw "Graph navigation verify failed: scrollLeft javap block not found"
  }

  $scrollLeftMax=@(
    $scrollLeftBlock -split "`r?`n" |
      Select-String 'Field maxPoints:I'
  ).Count
  $scrollLeftShift=@(
    $scrollLeftBlock -split "`r?`n" |
      Select-String 'Field shiftPoints:I'
  ).Count

  if ($scrollLeftMax -ne 0) {
    throw "Graph navigation verify failed: scrollLeft still reads maxPoints"
  }
  if ($scrollLeftShift -ne 2) {
    throw "Graph navigation verify failed: scrollLeft shiftPoints reads=$scrollLeftShift"
  }

  $scrollRightBlock=[regex]::Match(
    $graphNavText,
    '(?s)public void scrollRight\(\);.*?(?=public void clearMarkers\(\);)'
  ).Value
  if (!$scrollRightBlock) {
    throw "Graph navigation verify failed: scrollRight javap block not found"
  }

  $scrollRightStableSize=@(
    $scrollRightBlock -split "`r?`n" |
      Select-String 'GraphStopSupport\.graphDataSizeForScrollRight:\(Ljava/lang/Object;Ljava/util/List;\)I'
  ).Count
  $scrollRightArrayListSize=@(
    $scrollRightBlock -split "`r?`n" |
      Select-String 'java/util/ArrayList\.size:\(\)I'
  ).Count

  if ($scrollRightStableSize -ne 1) {
    throw "Graph navigation verify failed: scrollRight stable-size calls=$scrollRightStableSize"
  }
  if ($scrollRightArrayListSize -ne 1) {
    throw "Graph navigation verify failed: scrollRight ArrayList.size calls=$scrollRightArrayListSize"
  }

  Write-Host "        selection markers : replace old pair"
  Write-Host "        page lookup        : timestamp snapped to nearest graph sample"
  Write-Host "        stopped LIVE data  : independent final GraphData snapshot"
  Write-Host "        Y-axis normalize   : post-selection auto-range"
  Write-Host "        scrollLeft         : 500 -> 0 now reachable"
  Write-Host "        scrollRight STOP   : boundary uses stable stopped snapshot"

  Write-Host "[11/21] Preserve stable post-STOP GraphData + apply right-axis cosmetics"
  $UiPatchedGraph="$GraphPath.uipatched"
  & $java `
    --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED `
    -cp $ToolClasses `
    GraphUiPatcher `
    $GraphPath `
    $UiPatchedGraph
  if ($LASTEXITCODE -ne 0) { throw "Graph UI transform failed: $LASTEXITCODE" }
  Move-Item -Force -LiteralPath $UiPatchedGraph -Destination $GraphPath

  $PatchedStop="$StopPath.patched"
  & $java `
    --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED `
    -cp $ToolClasses `
    GraphStopHandlerPatcher `
    $StopPath `
    $PatchedStop
  if ($LASTEXITCODE -ne 0) { throw "STOP transform failed: $LASTEXITCODE" }
  Move-Item -Force -LiteralPath $PatchedStop -Destination $StopPath

  Write-Host "[12/21] Verify STOP freeze + graph UI hooks"
  Copy-Item -Force -LiteralPath $GraphPath -Destination $GraphVerifyClass
  $graphUiJavap = & $javap -classpath "$GraphVerifyDir;$Classes" -c -p `
    "com.cypress.ezpdanalyzer.ui.jfreechart.CyXYLineChart" 2>&1
  $registerCalls = @(
    $graphUiJavap | Select-String 'GraphStopSupport\.registerChart:\(Ljava/lang/Object;\)V'
  ).Count
  $cosmeticCalls = @(
    $graphUiJavap | Select-String 'GraphUiCosmeticSupport\.configureChart:\(Ljava/lang/Object;\)V'
  ).Count
  if ($registerCalls -ne 1 -or $cosmeticCalls -ne 1) {
    throw "Graph UI hook verify failed: register=$registerCalls cosmetic=$cosmeticCalls"
  }

  $stopPatchedJavap = & $javap -classpath "$Work;$Classes" -c -p `
    "com.cypress.ezpdanalyzer.ui.handler.StopHandler" 2>&1
  $stopFlushPatched = @(
    $stopPatchedJavap | Select-String 'CachedDataListManager\.processAndSavePrimaryBuffer:\(\)V'
  ).Count
  $stopCaptureHook = @(
    $stopPatchedJavap | Select-String 'GraphStopSupport\.captureBeforeStopFlush:\(Ljava/lang/Object;\)V'
  ).Count
  $stopRestoreHook = @(
    $stopPatchedJavap | Select-String 'GraphStopSupport\.restoreAfterStopFlush:\(Ljava/lang/Object;\)V'
  ).Count
  if ($stopFlushPatched -ne 1 -or $stopCaptureHook -ne 1 -or $stopRestoreHook -ne 1) {
    throw "STOP hook verify failed: flush=$stopFlushPatched capture=$stopCaptureHook restore=$stopRestoreHook"
  }
  Write-Host "        STOP vendor flush : preserved"
  Write-Host "        live graph buffer : snapshot -> restore -> refresh"
  Write-Host "        VBUS/AMP labels   : figure-space right alignment"

  Write-Host "[13/21] Patch VBUS/AMP checkbox listeners (preserve axis/dataset)"
  $checkboxJobs = @(
    @{
      Path=$GraphVBUSCheckPath
      Class='com/cypress/ezpdanalyzer/ui/views/GraphSelectorComposite$3'
      JavaClass='com.cypress.ezpdanalyzer.ui.views.GraphSelectorComposite$3'
      SetMethod='setVbusEnable'
      AxisMethod='createVBUSAxix'
      Label='VBUS'
    },
    @{
      Path=$GraphAMPCheckPath
      Class='com/cypress/ezpdanalyzer/ui/views/GraphSelectorComposite$4'
      JavaClass='com.cypress.ezpdanalyzer.ui.views.GraphSelectorComposite$4'
      SetMethod='setAmpEnable'
      AxisMethod='createAMPAxix'
      Label='AMP'
    }
  )

  foreach ($job in $checkboxJobs) {
    $inPath=$job['Path']
    $outPath="$inPath.patched"

    & $java `
      --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED `
      -cp $ToolClasses `
      GraphCheckboxPatcher `
      $inPath `
      $outPath `
      $job['Class'] `
      $job['SetMethod'] `
      $job['AxisMethod']

    if ($LASTEXITCODE -ne 0) {
      throw "$($job['Label']) checkbox transform failed: $LASTEXITCODE"
    }

    Move-Item -Force -LiteralPath $outPath -Destination $inPath
  }

  Write-Host "[14/21] Verify non-destructive checkbox bytecode"

  # CC1 should remain its already-correct enable+refresh implementation.
  $cc1Verify = & $javap -classpath $Work -c -p `
    'com.cypress.ezpdanalyzer.ui.views.GraphSelectorComposite$1' 2>&1

  if (@($cc1Verify | Select-String 'CyXYLineChart\.setCc1Enable:\(Z\)V').Count -ne 2) {
    throw "CC1 checkbox verify failed: setCc1Enable"
  }
  if (@($cc1Verify | Select-String 'CyXYLineChart\.refreshGraph:\(\)V').Count -ne 2) {
    throw "CC1 checkbox verify failed: refreshGraph"
  }
  if (@($cc1Verify | Select-String 'CyXYLineChart\.create[A-Za-z0-9]+Axix:\(Z\)V').Count -ne 0) {
    throw "CC1 checkbox verify failed: unexpected create*Axix"
  }

  $cc2Verify = & $javap -classpath $Work -c -p `
    'com.cypress.ezpdanalyzer.ui.views.GraphSelectorComposite$2' 2>&1
  if (@($cc2Verify | Select-String 'CyXYLineChart\.setCc2Enable:\(Z\)V').Count -ne 2) {
    throw "CC2 checkbox verify failed: setCc2Enable"
  }
  if (@($cc2Verify | Select-String 'CyXYLineChart\.refreshGraph:\(\)V').Count -ne 2) {
    throw "CC2 checkbox verify failed: refreshGraph"
  }
  if (@($cc2Verify | Select-String 'CyXYLineChart\.create[A-Za-z0-9]+Axix:\(Z\)V').Count -ne 0) {
    throw "CC2 checkbox verify failed: unexpected create*Axix"
  }

  foreach ($job in $checkboxJobs) {
    $listenerJavap = & $javap -classpath $Work -c -p $job['JavaClass'] 2>&1

    $setCount = @(
      $listenerJavap |
        Select-String ("CyXYLineChart\." + [regex]::Escape($job['SetMethod']) + ":\(Z\)V")
    ).Count

    $axisCount = @(
      $listenerJavap |
        Select-String ("CyXYLineChart\." + [regex]::Escape($job['AxisMethod']) + ":\(Z\)V")
    ).Count

    $refreshCount = @(
      $listenerJavap |
        Select-String 'CyXYLineChart\.refreshGraph:\(\)V'
    ).Count

    $pop2Count = @(
      $listenerJavap |
        Select-String '^\s*\d+:\s+pop2\s*$'
    ).Count

    if ($setCount -ne 2) {
      throw "$($job['Label']) checkbox verify failed: set-enable=$setCount"
    }
    if ($axisCount -ne 0) {
      throw "$($job['Label']) checkbox verify failed: destructive axis calls remain=$axisCount"
    }
    if ($refreshCount -ne 2) {
      throw "$($job['Label']) checkbox verify failed: refreshGraph=$refreshCount"
    }
    if ($pop2Count -ne 2) {
      throw "$($job['Label']) checkbox verify failed: POP2 replacements=$pop2Count"
    }

    Write-Host (
      "        " + $job['Label'] +
      ": set=2, createAxis=0, refresh=2, POP2=2"
    )
  }

  Write-Host "[15/21] Apply requested advanced-view extensions"
  # Replace only the three complete comments belonging to each requested view.
  if ($EnableTriggers) {
    $xml = Replace-ExactlyOneRegex $xml $triggerViewComment '$1' "Trigger view uncomment"
    $xml = Replace-ExactlyOneRegex $xml $triggerMenuComment '$1' "Trigger Show View uncomment"
    $xml = Replace-ExactlyOneRegex $xml $triggerPerspectiveComment '$1' "Trigger perspective uncomment"
  }
  if ($EnableTerminations) {
    $xml = Replace-ExactlyOneRegex $xml $termViewComment '$1' "Terminations view uncomment"
    $xml = Replace-ExactlyOneRegex $xml $termMenuComment '$1' "Terminations Show View uncomment"
    $xml = Replace-ExactlyOneRegex $xml $termPerspectiveComment '$1' "Terminations perspective uncomment"
  }

  # Now redirect only the two newly live view registrations to our classes.
  $oldTriggerClass='class="com.cypress.ezpdanalyzer.ui.views.TriggerView"'
  $newTriggerClass='class="com.cypress.ezpdanalyzer.ui.views.TriggerViewFixed"'
  $oldTermClass='class="com.cypress.ezpdanalyzer.ui.views.Terminations"'
  $newTermClass='class="com.cypress.ezpdanalyzer.ui.views.TerminationsFixed"'

  if ($EnableTriggers) {
    if (([regex]::Matches($xml,[regex]::Escape($oldTriggerClass))).Count -ne 1) {
      throw "Live TriggerView class anchor count is not 1 after uncomment."
    }
    $xml=$xml.Replace($oldTriggerClass,$newTriggerClass)
  }
  if ($EnableTerminations) {
    if (([regex]::Matches($xml,[regex]::Escape($oldTermClass))).Count -ne 1) {
      throw "Live Terminations class anchor count is not 1 after uncomment."
    }
    $xml=$xml.Replace($oldTermClass,$newTermClass)
  }

  Write-Host "[16/21] Parse and validate patched plugin.xml"
  try {
    [xml]$doc = $xml
  } catch {
    throw "Patched plugin.xml is not valid XML: $($_.Exception.Message)"
  }

  $triggerCount = if ($EnableTriggers) { 1 } else { 0 }
  $terminationCount = if ($EnableTerminations) { 1 } else { 0 }
  Assert-XmlNodeCount $doc `
    "/plugin/extension[@point='org.eclipse.ui.views']/view[@id='com.cypress.ezpdanalyzer.ui.views.TriggerView' and @class='com.cypress.ezpdanalyzer.ui.views.TriggerViewFixed']" `
    $triggerCount "Trigger view registration"

  Assert-XmlNodeCount $doc `
    "/plugin/extension[@point='org.eclipse.ui.views']/view[@id='com.cypress.ezpdanalyzer.ui.views.Terminations' and @class='com.cypress.ezpdanalyzer.ui.views.TerminationsFixed']" `
    $terminationCount "Terminations view registration"

  Assert-XmlNodeCount $doc `
    "/plugin/extension[@point='org.eclipse.ui.menus']//menu[@label='Show View']/command[@commandId='com.cypress.ezpdanalyzer.ui.triggerviewcommand']" `
    $triggerCount "Trigger Show View contribution"

  Assert-XmlNodeCount $doc `
    "/plugin/extension[@point='org.eclipse.ui.menus']//menu[@label='Show View']/command[@commandId='com.cypress.ezpdanalyzer.ui.terminationscmd']" `
    $terminationCount "Terminations Show View contribution"

  Assert-XmlNodeCount $doc `
    "/plugin/extension[@point='org.eclipse.ui.perspectiveExtensions']/perspectiveExtension/view[@id='com.cypress.ezpdanalyzer.ui.views.TriggerView']" `
    $triggerCount "Trigger perspective view"

  Assert-XmlNodeCount $doc `
    "/plugin/extension[@point='org.eclipse.ui.perspectiveExtensions']/perspectiveExtension/view[@id='com.cypress.ezpdanalyzer.ui.views.Terminations']" `
    $terminationCount "Terminations perspective view"

  if ($EnableTriggers -and ($xml -match 'triggerviewcommand"\s+style="push">\s*</command>\s*-->' -or
      $xml -match 'class="com\.cypress\.ezpdanalyzer\.ui\.views\.TriggerViewFixed"[^>]*>.*?</view>\s*-->' -or
      $xml -match 'closeable="true"\s+id="com\.cypress\.ezpdanalyzer\.ui\.views\.TriggerView"[^>]*>.*?</view>\s*-->')) {
    throw "At least one Trigger node is still commented out."
  }
  if ($EnableTerminations -and ($xml -match 'terminationscmd"\s+style="push">\s*</command>\s*-->' -or
      $xml -match 'class="com\.cypress\.ezpdanalyzer\.ui\.views\.TerminationsFixed"[^>]*>.*?</view>\s*-->' -or
      $xml -match 'closeable="true"\s+id="com\.cypress\.ezpdanalyzer\.ui\.views\.Terminations"[^>]*>.*?</view>\s*-->')) {
    throw "At least one Terminations node is still commented out."
  }

  [IO.File]::WriteAllText(
    $Plugin,
    $xml,
    (New-Object Text.UTF8Encoding($false))
  )

  Write-Host "[17/21] Stage local JAR update"
  $Stage=Join-Path $Work "stage"
  New-Item -ItemType Directory $Stage | Out-Null
  Copy-Item -LiteralPath $Plugin -Destination (Join-Path $Stage "plugin.xml")

  $csvStage=Join-Path $Stage ($CsvEntry -replace '/','\')
  New-Item -ItemType Directory -Force (Split-Path -Parent $csvStage) | Out-Null
  Copy-Item -LiteralPath $CsvPath -Destination $csvStage

  $graphStage=Join-Path $Stage ($GraphEntry -replace '/','\')
  New-Item -ItemType Directory -Force (Split-Path -Parent $graphStage) | Out-Null
  Copy-Item -LiteralPath $GraphPath -Destination $graphStage

  $stopStage=Join-Path $Stage ($StopEntry -replace '/','\')
  New-Item -ItemType Directory -Force (Split-Path -Parent $stopStage) | Out-Null
  Copy-Item -LiteralPath $StopPath -Destination $stopStage

  $graphSupportSourceDir=Join-Path $Classes "com\cypress\ezpdanalyzer\ui\jfreechart"
  $graphSupportStageDir=Join-Path $Stage "com\cypress\ezpdanalyzer\ui\jfreechart"
  New-Item -ItemType Directory -Force $graphSupportStageDir | Out-Null
  foreach ($pattern in @(
    "GraphNavigationSupport*.class",
    "GraphStopSupport*.class",
    "GraphUiCosmeticSupport*.class"
  )) {
    $supportFiles = @(Get-ChildItem $graphSupportSourceDir -Filter $pattern)
    if ($supportFiles.Count -lt 1) {
      throw "Compiled graph support missing: $pattern"
    }
    $supportFiles | ForEach-Object {
      Copy-Item -LiteralPath $_.FullName -Destination $graphSupportStageDir
    }
  }

  foreach ($listener in @(
    @($GraphVBUSCheckEntry,$GraphVBUSCheckPath),
    @($GraphAMPCheckEntry,$GraphAMPCheckPath)
  )) {
    $listenerStage=Join-Path $Stage ($listener[0] -replace '/','\')
    New-Item -ItemType Directory -Force (Split-Path -Parent $listenerStage) | Out-Null
    Copy-Item -LiteralPath $listener[1] -Destination $listenerStage
  }

  if ($EnableTriggers -or $EnableTerminations) {
    $viewStage=Join-Path $Stage "com\cypress\ezpdanalyzer\ui\views"
    New-Item -ItemType Directory -Force $viewStage | Out-Null
    Copy-Item (Join-Path $Classes "com\cypress\ezpdanalyzer\ui\views\*.class") $viewStage
  }

  Write-Host "[18/21] Update installed JAR"
  Push-Location $Stage
  try {
    & $jar uf $JarPath "plugin.xml" $CsvEntry $GraphEntry $StopEntry $GraphVBUSCheckEntry $GraphAMPCheckEntry
    if ($LASTEXITCODE -ne 0) { throw "jar update failed: $LASTEXITCODE" }

    foreach ($pattern in @(
      "GraphNavigationSupport*.class",
      "GraphStopSupport*.class",
      "GraphUiCosmeticSupport*.class"
    )) {
      Get-ChildItem "com\cypress\ezpdanalyzer\ui\jfreechart" -Filter $pattern |
        ForEach-Object {
          $entry = "com/cypress/ezpdanalyzer/ui/jfreechart/" + $_.Name
          & $jar uf $JarPath $entry
          if ($LASTEXITCODE -ne 0) {
            throw "jar update failed for $($_.Name)"
          }
        }
    }

    $viewPatterns = @()
    if ($EnableTriggers -or $EnableTerminations) {
      $viewPatterns += "AdvancedFeatureUsbSync*.class"
    }
    if ($EnableTriggers) { $viewPatterns += "TriggerViewFixed*.class" }
    if ($EnableTerminations) { $viewPatterns += "TerminationsFixed*.class" }
    foreach ($pattern in $viewPatterns) {
      Get-ChildItem "com\cypress\ezpdanalyzer\ui\views" -Filter $pattern |
        ForEach-Object {
          $entry = "com/cypress/ezpdanalyzer/ui/views/" + $_.Name
          & $jar uf $JarPath $entry
          if ($LASTEXITCODE -ne 0) {
            throw "jar update failed for $($_.Name)"
          }
        }
    }
  } finally {
    Pop-Location
  }

  Write-Host "[19/21] Verify final JAR bytecode + live XML"
  $tf=& $jar tf $JarPath
  $requiredEntries = @(
    $CsvEntry,
    $GraphEntry,
    $StopEntry,
    $GraphNavigationSupportEntry,
    $GraphStopSupportEntry,
    $GraphUiCosmeticSupportEntry,
    $GraphUiCosmeticFormatEntry,
    $GraphVBUSCheckEntry,
    $GraphAMPCheckEntry,
    "plugin.xml"
  )
  if ($EnableTriggers -or $EnableTerminations) {
    $requiredEntries += "com/cypress/ezpdanalyzer/ui/views/AdvancedFeatureUsbSync.class"
  }
  if ($EnableTriggers) { $requiredEntries += "com/cypress/ezpdanalyzer/ui/views/TriggerViewFixed.class" }
  if ($EnableTerminations) { $requiredEntries += "com/cypress/ezpdanalyzer/ui/views/TerminationsFixed.class" }
  foreach ($e in $requiredEntries) {
    if (!($tf -contains $e)) { throw "Missing from final JAR: $e" }
  }

  if ($EnableTriggers -or $EnableTerminations) {
    $usbFinal = & $javap -classpath $JarPath -c -p -verbose `
      "com.cypress.ezpdanalyzer.ui.views.AdvancedFeatureUsbSync" 2>&1
    foreach ($apiName in @(
      "javax.usb.event.UsbServicesEvent",
      "javax.usb.UsbDevice",
      "javax.usb.UsbDeviceDescriptor"
    )) {
      if (@($usbFinal | Select-String ([regex]::Escape($apiName))).Count -lt 1) {
        throw "FINAL AdvancedFeatureUsbSync verification failed: missing $apiName"
      }
    }
  }

  $jv=& $javap -classpath $JarPath -c -p 'com.cypress.ezpdanalyzer.ui.handler.ExcelExportHandler$1' 2>&1
  if (@($jv|Select-String 'USBPacketData\.getsTime').Count -ne 1) { throw "getsTime final verify failed" }
  if (@($jv|Select-String 'USBPacketData\.geteTime').Count -ne 1) { throw "geteTime final verify failed" }
  if (@($jv|Select-String 'USBPacketData\.getSno').Count -ne 0) { throw "getSno final verify failed" }
  if (@($jv|Select-String '__ezpdCsvRowCounter').Count -lt 3) { throw "row counter final verify failed" }

  $graphFinal = & $javap -classpath $JarPath -c -p `
    "com.cypress.ezpdanalyzer.ui.jfreechart.CyXYLineChart" 2>&1

  $graphFinalVolt = @(
    $graphFinal | Select-String 'IData\.getVolt:\(\)S'
  ).Count
  $graphFinalAmp = @(
    $graphFinal | Select-String 'IData\.getAmp:\(\)S'
  ).Count
  $graphFinalIand = @(
    $graphFinal | Select-String '^\s*\d+:\s+iand\s*$'
  ).Count
  $graphFinalMask = @(
    $graphFinal | Select-String 'int 65535'
  ).Count

  if ($graphFinalVolt -ne 2) { throw "FINAL Graph verify failed: getVolt=$graphFinalVolt" }
  if ($graphFinalAmp -ne 2) { throw "FINAL Graph verify failed: getAmp=$graphFinalAmp" }
  if ($graphFinalIand -ne 2) { throw "FINAL Graph verify failed: iand=$graphFinalIand" }
  if ($graphFinalMask -ne 2) { throw "FINAL Graph verify failed: 65535 masks=$graphFinalMask" }

  $graphFinalText=$graphFinal -join "`n"

  $finalClearMarkers=@(
    $graphFinal |
      Select-String 'Method clearMarkers:\(\)V'
  ).Count
  $finalSnap=@(
    $graphFinal |
      Select-String 'GraphNavigationSupport\.snapSelectionTime:\(Ljava/util/List;J\)J'
  ).Count
  $finalNormalize=@(
    $graphFinal |
      Select-String 'GraphNavigationSupport\.normalizeAfterSelection:\(Ljava/lang/Object;Ljava/lang/Object;\)V'
  ).Count
  $finalStableStopData=@(
    $graphFinal |
      Select-String 'GraphStopSupport\.chooseGraphData:\(Ljava/lang/Object;Ljava/util/List;\)Ljava/util/List;'
  ).Count
  $finalStaleAmp=@(
    $graphFinal |
      Select-String 'Method setAmpSeriesRange:\(\)V'
  ).Count

  if ($finalClearMarkers -ne 1) {
    throw "FINAL Graph navigation verify failed: clearMarkers=$finalClearMarkers"
  }
  if ($finalSnap -ne 1) {
    throw "FINAL Graph navigation verify failed: snapSelectionTime=$finalSnap"
  }
  if ($finalNormalize -ne 1) {
    throw "FINAL Graph navigation verify failed: normalizeAfterSelection=$finalNormalize"
  }
  if ($finalStableStopData -ne 2) {
    throw "FINAL Graph navigation verify failed: chooseGraphData=$finalStableStopData"
  }
  $finalRegister=@(
    $graphFinal | Select-String 'GraphStopSupport\.registerChart:\(Ljava/lang/Object;\)V'
  ).Count
  $finalCosmetic=@(
    $graphFinal | Select-String 'GraphUiCosmeticSupport\.configureChart:\(Ljava/lang/Object;\)V'
  ).Count
  if ($finalRegister -ne 1 -or $finalCosmetic -ne 1) {
    throw "FINAL Graph UI hooks failed: register=$finalRegister cosmetic=$finalCosmetic"
  }
  if ($finalStaleAmp -ne 0) {
    throw "FINAL Graph navigation verify failed: stale AMP-range call=$finalStaleAmp"
  }

  $finalScrollLeft=[regex]::Match(
    $graphFinalText,
    '(?s)public void scrollLeft\(\);.*?(?=public void scrollRight\(\);)'
  ).Value

  if (!$finalScrollLeft) {
    throw "FINAL Graph navigation verify failed: scrollLeft block missing"
  }

  $finalScrollMax=@(
    $finalScrollLeft -split "`r?`n" |
      Select-String 'Field maxPoints:I'
  ).Count
  $finalScrollShift=@(
    $finalScrollLeft -split "`r?`n" |
      Select-String 'Field shiftPoints:I'
  ).Count

  if ($finalScrollMax -ne 0 -or $finalScrollShift -ne 2) {
    throw (
      "FINAL scrollLeft verify failed: " +
      "maxPoints=$finalScrollMax shiftPoints=$finalScrollShift"
    )
  }

  $finalScrollRight=[regex]::Match(
    $graphFinalText,
    '(?s)public void scrollRight\(\);.*?(?=public void clearMarkers\(\);)'
  ).Value
  if (!$finalScrollRight) {
    throw "FINAL Graph navigation verify failed: scrollRight block missing"
  }

  $finalRightStableSize=@(
    $finalScrollRight -split "`r?`n" |
      Select-String 'GraphStopSupport\.graphDataSizeForScrollRight:\(Ljava/lang/Object;Ljava/util/List;\)I'
  ).Count
  $finalRightArrayListSize=@(
    $finalScrollRight -split "`r?`n" |
      Select-String 'java/util/ArrayList\.size:\(\)I'
  ).Count

  if ($finalRightStableSize -ne 1) {
    throw "FINAL scrollRight stable-size calls=$finalRightStableSize"
  }
  if ($finalRightArrayListSize -ne 1) {
    throw "FINAL scrollRight ArrayList.size calls=$finalRightArrayListSize"
  }

  $supportFinal=& $javap -classpath $JarPath -c -p `
    "com.cypress.ezpdanalyzer.ui.jfreechart.GraphNavigationSupport" 2>&1

  if (@($supportFinal | Select-String 'snapSelectionTime').Count -lt 1) {
    throw "FINAL GraphNavigationSupport missing snapSelectionTime"
  }
  if (@($supportFinal | Select-String 'normalizeAfterSelection').Count -lt 1) {
    throw "FINAL GraphNavigationSupport missing normalizeAfterSelection"
  }

  $stopSupportFinal=& $javap -classpath $JarPath -c -p `
    "com.cypress.ezpdanalyzer.ui.jfreechart.GraphStopSupport" 2>&1
  if (@($stopSupportFinal | Select-String 'captureBeforeStopFlush').Count -lt 1 -or
      @($stopSupportFinal | Select-String 'restoreAfterStopFlush').Count -lt 1 -or
      @($stopSupportFinal | Select-String 'chooseGraphData').Count -lt 1 -or
      @($stopSupportFinal | Select-String 'graphDataSizeForScrollRight').Count -lt 1 -or
      @($stopSupportFinal | Select-String 'getStoppedLiveSnapshotSize').Count -lt 1 -or
      @($stopSupportFinal | Select-String 'registerChart').Count -lt 1) {
    throw "FINAL GraphStopSupport methods missing"
  }

  $stopSupportClear=@(
    $stopSupportFinal |
      Select-String 'java/util/List\.clear:\(\)V'
  ).Count
  $stopSupportAddAll=@(
    $stopSupportFinal |
      Select-String 'java/util/List\.addAll:\(Ljava/util/Collection;\)Z'
  ).Count
  if ($stopSupportClear -ne 0 -or $stopSupportAddAll -ne 0) {
    throw "FINAL GraphStopSupport must not mutate vendor lists"
  }

  $uiSupportFinal=& $javap -classpath $JarPath -c -p `
    "com.cypress.ezpdanalyzer.ui.jfreechart.GraphUiCosmeticSupport" 2>&1
  if (@($uiSupportFinal | Select-String 'configureChart').Count -lt 1 -or
      @($uiSupportFinal | Select-String 'setNumberFormatOverride').Count -lt 1) {
    throw "FINAL GraphUiCosmeticSupport verification failed"
  }

  $stopFinal=& $javap -classpath $JarPath -c -p `
    "com.cypress.ezpdanalyzer.ui.handler.StopHandler" 2>&1
  $stopFinalFlush=@($stopFinal | Select-String 'CachedDataListManager\.processAndSavePrimaryBuffer:\(\)V').Count
  $stopFinalCapture=@($stopFinal | Select-String 'GraphStopSupport\.captureBeforeStopFlush:\(Ljava/lang/Object;\)V').Count
  $stopFinalRestore=@($stopFinal | Select-String 'GraphStopSupport\.restoreAfterStopFlush:\(Ljava/lang/Object;\)V').Count
  if ($stopFinalFlush -ne 1 -or $stopFinalCapture -ne 1 -or $stopFinalRestore -ne 1) {
    throw "FINAL STOP verify failed: flush=$stopFinalFlush capture=$stopFinalCapture restore=$stopFinalRestore"
  }

  # Final installed-JAR verification for all four checkboxes.
  $cc1Final = & $javap -classpath $JarPath -c -p `
    'com.cypress.ezpdanalyzer.ui.views.GraphSelectorComposite$1' 2>&1
  if (@($cc1Final | Select-String 'CyXYLineChart\.setCc1Enable:\(Z\)V').Count -ne 2) {
    throw "FINAL CC1 checkbox verify failed"
  }
  if (@($cc1Final | Select-String 'CyXYLineChart\.create[A-Za-z0-9]+Axix:\(Z\)V').Count -ne 0) {
    throw "FINAL CC1 checkbox has destructive axis recreation"
  }

  $cc2Final = & $javap -classpath $JarPath -c -p `
    'com.cypress.ezpdanalyzer.ui.views.GraphSelectorComposite$2' 2>&1
  if (@($cc2Final | Select-String 'CyXYLineChart\.setCc2Enable:\(Z\)V').Count -ne 2) {
    throw "FINAL CC2 checkbox verify failed"
  }
  if (@($cc2Final | Select-String 'CyXYLineChart\.refreshGraph:\(\)V').Count -ne 2) {
    throw "FINAL CC2 checkbox refresh verify failed"
  }
  if (@($cc2Final | Select-String 'CyXYLineChart\.create[A-Za-z0-9]+Axix:\(Z\)V').Count -ne 0) {
    throw "FINAL CC2 checkbox has destructive axis recreation"
  }

  foreach ($job in $checkboxJobs) {
    $listenerFinal = & $javap -classpath $JarPath -c -p $job['JavaClass'] 2>&1

    $setFinal = @(
      $listenerFinal |
        Select-String ("CyXYLineChart\." + [regex]::Escape($job['SetMethod']) + ":\(Z\)V")
    ).Count
    $axisFinal = @(
      $listenerFinal |
        Select-String ("CyXYLineChart\." + [regex]::Escape($job['AxisMethod']) + ":\(Z\)V")
    ).Count
    $refreshFinal = @(
      $listenerFinal |
        Select-String 'CyXYLineChart\.refreshGraph:\(\)V'
    ).Count
    $pop2Final = @(
      $listenerFinal |
        Select-String '^\s*\d+:\s+pop2\s*$'
    ).Count

    if ($setFinal -ne 2 -or
        $axisFinal -ne 0 -or
        $refreshFinal -ne 2 -or
        $pop2Final -ne 2) {
      throw (
        "FINAL " + $job['Label'] +
        " checkbox verify failed: set=$setFinal axis=$axisFinal " +
        "refresh=$refreshFinal pop2=$pop2Final"
      )
    }
  }

  $VerifyDir=Join-Path $Work "verify"
  New-Item -ItemType Directory $VerifyDir | Out-Null
  Push-Location $VerifyDir
  try {
    & $jar xf $JarPath "plugin.xml"
    if ($LASTEXITCODE -ne 0) { throw "final plugin.xml extract failed" }
  } finally {
    Pop-Location
  }

  $finalXml=[IO.File]::ReadAllText((Join-Path $VerifyDir "plugin.xml"))
  try {
    [xml]$finalDoc=$finalXml
  } catch {
    throw "Final JAR plugin.xml is not valid XML."
  }

  Assert-XmlNodeCount $finalDoc `
    "/plugin/extension[@point='org.eclipse.ui.views']/view[@id='com.cypress.ezpdanalyzer.ui.views.TriggerView' and @class='com.cypress.ezpdanalyzer.ui.views.TriggerViewFixed']" `
    $triggerCount "FINAL Trigger view registration"

  Assert-XmlNodeCount $finalDoc `
    "/plugin/extension[@point='org.eclipse.ui.views']/view[@id='com.cypress.ezpdanalyzer.ui.views.Terminations' and @class='com.cypress.ezpdanalyzer.ui.views.TerminationsFixed']" `
    $terminationCount "FINAL Terminations view registration"

  Assert-XmlNodeCount $finalDoc `
    "/plugin/extension[@point='org.eclipse.ui.menus']//menu[@label='Show View']/command[@commandId='com.cypress.ezpdanalyzer.ui.triggerviewcommand']" `
    $triggerCount "FINAL Trigger Show View contribution"

  Assert-XmlNodeCount $finalDoc `
    "/plugin/extension[@point='org.eclipse.ui.menus']//menu[@label='Show View']/command[@commandId='com.cypress.ezpdanalyzer.ui.terminationscmd']" `
    $terminationCount "FINAL Terminations Show View contribution"

  Assert-XmlNodeCount $finalDoc `
    "/plugin/extension[@point='org.eclipse.ui.perspectiveExtensions']/perspectiveExtension/view[@id='com.cypress.ezpdanalyzer.ui.views.TriggerView']" `
    $triggerCount "FINAL Trigger perspective view"

  Assert-XmlNodeCount $finalDoc `
    "/plugin/extension[@point='org.eclipse.ui.perspectiveExtensions']/perspectiveExtension/view[@id='com.cypress.ezpdanalyzer.ui.views.Terminations']" `
    $terminationCount "FINAL Terminations perspective view"

  Write-Host "[20/21] Apply final graph, table, status, workflow, About, and window-state changes"
  & (Join-Path $Here 'scripts\Apply-Final-Display-v1.0p.ps1') -AppDir $AppDir
  if ($LASTEXITCODE -ne 0) { throw "Final display integration failed: $LASTEXITCODE" }

  Write-Host "[21/21] Done"
  Write-Host ""
  Write-Host "PATCH SUCCESS"
  Write-Host "  CSV End Time          FIXED"
  Write-Host "  CSV Sno               1-based display-row numbering"
  Write-Host "  Graph VBUS >32.767V   unsigned 16-bit mV fix"
  Write-Host "  Graph checkboxes      VBUS/AMP no axis/dataset recreation"
  Write-Host "  Graph row selection   page-gap + stale-axis repair"
  Write-Host "  Graph markers         old selection markers cleared"
  Write-Host "  Graph scroll-left     first 500 samples reachable"
  Write-Host "  Graph after STOP      stable snapshot for row/checkbox/left/right"
  Write-Host "  Axis tick labels      final major-label alignment on all graph axes"
  Write-Host "  VBUS/AMP tick labels  aligned, dynamically precise numeric columns"
  Write-Host "  Graph/Table UI        labels, microsecond units, X/Y/dX/dY order, spacing, table layout"
  Write-Host "  Message filters       checkbox multi-select with include/exclude mode"
  Write-Host "  Column widths         USB PD Messages, Details, and Payload remembered"
  Write-Host "  Window size           last non-maximized size remembered"
  Write-Host "  Device status         VBUS: <mV>mV, <mA>mA"
  Write-Host "  File workflow         redundant success/exit dialogs removed"
  Write-Host "  About                 Mod by @USB_PD_EPR_240W v1.0p"
  if ($EnableTriggers) {
    Write-Host "  Trigger view XML      ENABLED + TriggerViewFixed"
    Write-Host "  Trigger Clear         real hardware clear"
  } else { Write-Host "  Trigger view XML      stock-disabled (use -EnableTriggers)" }
  if ($EnableTerminations) {
    Write-Host "  Terminations view XML ENABLED + TerminationsFixed"
  } else { Write-Host "  Terminations view XML stock-disabled (use -EnableTerminations)" }
  if ($EnableTriggers -or $EnableTerminations) {
    Write-Host "  USB state sync        proven javax.usb event path; no polling"
  }
  Write-Host "  JAR SHA256            $(Sha256 $JarPath)"
  Write-Host "  Restore backup        $BackupPath"
  Write-Host ""

  if (!$NoLaunch) {
    Write-Host "Launching once with Equinox -clean -clearPersistedState..."
    Start-Process -FilePath $ExePath -ArgumentList "-clean","-clearPersistedState"
  } else {
    Write-Host "First launch MUST use:"
    Write-Host "  `"$ExePath`" -clean -clearPersistedState"
  }
}
catch {
  Write-Host ""
  Write-Host "PATCH FAILED: $($_.Exception.Message)" -ForegroundColor Red
  Write-Host "Backup (if already created):"
  Write-Host "  $BackupPath"
  throw
}
finally {
  if (Test-Path -LiteralPath $Work) {
    Remove-Item -Recurse -Force -LiteralPath $Work -ErrorAction SilentlyContinue
  }
}
