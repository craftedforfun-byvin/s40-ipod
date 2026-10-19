# Builds ipod.jar / ipod.jad for a Series 40 phone (Nokia Asha 210).
#
# Needs: a JDK (8 - 21) in JAVA_HOME or on PATH, and tools\proguard.jar.
# ProGuard is used only to downgrade the bytecode and preverify it for CLDC.
#
#   powershell -ExecutionPolicy Bypass -File .\build.ps1
#
# Note: keep the JDK somewhere with a short path. The Java launcher still uses
# MAX_PATH internally and reports "could not find java.dll" from a deep folder.

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

function Find-Jdk {
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\javac.exe"))) { return $env:JAVA_HOME }
    $jc = Get-Command javac.exe -ErrorAction SilentlyContinue
    if ($jc) { return (Split-Path (Split-Path $jc.Source -Parent) -Parent) }
    foreach ($base in @("$env:LOCALAPPDATA", "$env:ProgramFiles\Eclipse Adoptium", "$env:ProgramFiles\Java",
                        "${env:ProgramFiles(x86)}\Java")) {
        if (Test-Path $base) {
            $hit = Get-ChildItem $base -Directory -ErrorAction SilentlyContinue |
                   Where-Object { Test-Path (Join-Path $_.FullName "bin\javac.exe") } |
                   Sort-Object Name -Descending | Select-Object -First 1
            if ($hit) { return $hit.FullName }
        }
    }
    return $null
}

$jdk = Find-Jdk
if (-not $jdk) {
    Write-Host "No JDK found. Set JAVA_HOME, or install one (version 8-21)." -ForegroundColor Red
    Write-Host "  winget install EclipseAdoptium.Temurin.17.JDK"
    Write-Host "  ...or unzip a Temurin archive somewhere with a short path and set JAVA_HOME."
    exit 1
}
$javac = Join-Path $jdk "bin\javac.exe"
$jarx  = Join-Path $jdk "bin\jar.exe"
$javax = Join-Path $jdk "bin\java.exe"
Write-Host "JDK: $jdk"

$proguard = Join-Path $PSScriptRoot "tools\proguard.jar"
if (-not (Test-Path $proguard)) {
    Write-Host "tools\proguard.jar is missing." -ForegroundColor Red
    Write-Host "Take lib\proguard.jar out of a ProGuard 7.x release and drop it in tools\:"
    Write-Host "  https://github.com/Guardsquare/proguard/releases"
    exit 1
}

Remove-Item -Recurse -Force build\classes, build\stubs, build\pre, dist -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path build\classes, build\stubs, build\pre, dist | Out-Null

# 1. the fake javax.microedition API - compile-time only, never shipped
Write-Host "[1/5] compiling API stubs"
$stubSrc = Get-ChildItem -Recurse stubs -Filter *.java | ForEach-Object { $_.FullName }
& $javac -nowarn -source 8 -target 8 -d build\stubs $stubSrc
if ($LASTEXITCODE -ne 0) { throw "stub compile failed" }

# 2. the MIDlet itself
Write-Host "[2/5] compiling the MIDlet"
$appSrc = Get-ChildItem -Recurse src -Filter *.java | ForEach-Object { $_.FullName }
& $javac -nowarn -source 8 -target 8 -classpath build\stubs -d build\classes $appSrc
if ($LASTEXITCODE -ne 0) { throw "compile failed" }

# 3. downgrade to class file 46 and write the CLDC StackMap
Write-Host "[3/5] preverifying for CLDC 1.1"
$jmod = Join-Path $jdk "jmods\java.base.jmod"
$rt   = Join-Path $jdk "jre\lib\rt.jar"
if (Test-Path $jmod)    { $lib = "$jmod(!**.jar;!module-info.class)" }
elseif (Test-Path $rt)  { $lib = $rt }
else { throw "cannot find the JDK's own class library" }

& $javax -jar $proguard `
    -injars build\classes -outjars build\pre `
    -libraryjars build\stubs -libraryjars $lib `
    -include build\proguard.pro
if ($LASTEXITCODE -ne 0) { throw "preverify failed" }

# 4. package
Write-Host "[4/5] packaging ipod.jar"
Copy-Item res\icon.png build\pre\icon.png -Force
& $jarx cfm dist\ipod.jar build\MANIFEST.MF -C build\pre .
if ($LASTEXITCODE -ne 0) { throw "jar failed" }

# 5. the descriptor the phone reads first
Write-Host "[5/5] writing ipod.jad"
$size = (Get-Item dist\ipod.jar).Length
@(
    "MIDlet-1: iPod, /icon.png, ipod.IPodMIDlet",
    "MIDlet-Name: iPod",
    "MIDlet-Vendor: Homebrew",
    "MIDlet-Version: 1.0.0",
    "MIDlet-Description: An iPod-style MP3 player for Series 40",
    "MIDlet-Icon: /icon.png",
    "MIDlet-Jar-URL: ipod.jar",
    "MIDlet-Jar-Size: $size",
    "MicroEdition-Configuration: CLDC-1.1",
    "MicroEdition-Profile: MIDP-2.0",
    "MIDlet-Permissions: javax.microedition.io.Connector.file.read",
    "Nokia-MIDlet-Category: Application"
) | Set-Content dist\ipod.jad -Encoding ascii

Write-Host ""
Write-Host "Built dist\ipod.jar ($size bytes) and dist\ipod.jad" -ForegroundColor Green
Write-Host "Copy both to the phone's memory card and open the .jad on the phone."
