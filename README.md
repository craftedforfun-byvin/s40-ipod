# iPod for Series 40

An iPod-style Cover Flow music player for Nokia Series 40 feature phones.

Built for the Nokia Asha 210 because its stock music player is unusable and there is no app store left to replace it from.

<img width="654" height="368" alt="20260916_133015_1(1)-2" src="https://github.com/user-attachments/assets/ea873f39-d606-41fe-8488-2be9a73bb7d8" />

---

## Supported devices

Built and tested on the **Nokia Asha 210** (Series 40, MIDP 2.1, 320 x 240 landscape).

It should run on other Series 40 devices with a 320 x 240 screen, but I have not tested any. If you try it on another handset, open an issue and say whether it worked.

## Install

1. Download `ipod.jar` and `ipod.jad` from [Releases](../../releases).
2. Connect the phone over USB and copy **both files into the same folder** on the memory card. The `.jad` refers to the `.jar` sitting next to it, so they cannot be separated.
3. On the phone, open **File manager**, find `ipod.jad` and open it. Confirm the install.
4. Go to **Application manager → iPod → Settings → Read user data** and set it to **Ask first time**.

Step 4 matters. This is an unsigned MIDlet, so Series 40 gives it no blanket file access. If you leave the default, the phone will prompt you once per file during a library scan, which is unusable on a real music library.

## What it does

- Cover Flow with genuine perspective. Covers angle away from you, and the selected album rotates and flips forward to reveal its track list.
- Reads album art embedded in your MP3 files.
- Scans both the memory card and phone memory, including `predefgallery/`, which is where Series 40 stores internal music and where most scanners miss it.
- Caches the library so it does not rescan on every launch. Manual rescan is in Settings → Update Library.

## What it does not do

- **No FLAC.** The device decodes MP3, AAC and AAC+, WMA, AMR, MIDI and WAV in firmware. An app cannot add a format to that list in any practical way. You can confirm what your own handset supports with `Manager.getSupportedContentTypes(null)`.
- **No system volume control.** There is no MIDP API for it. In-app playback volume is a separate thing and is handled, see Known issues.
- **No streaming.** Local files only. There is no app store on this platform and there is no Spotify.

## Known issues

- In-app volume is not confirmed working on hardware yet. The Diagnostics screen reports the state of the audio control. If it reports `no VolumeControl` on your device, please open an issue with your model name.
- Albums with no embedded artwork fall back to a placeholder rather than a generated cover.

---

## How it works

The interesting part of this project is not the music player. It is that the platform has been dead for a decade.

**There is no SDK.** No Nokia SDK, no Java ME SDK, no emulator. So the app compiles against hand-written stub declarations of every `javax.microedition.*` class it touches, compiled to a separate directory, placed on the classpath and excluded from the JAR. The code builds against a skeleton that does nothing. The phone supplies the real implementations at install time.

**Modern Java does not produce code this VM can load.** Three things had to be worked around:

- `StringBuilder` does not exist on CLDC 1.1, but `javac` from Java 5 onwards silently compiles string concatenation into it. The MIDlet installs fine and then dies with `NoClassDefFoundError` on the first screen draw. Every string join goes through a `StringBuffer` helper instead.
- Java 11 and later compile private access across a class nest as nestmates rather than synthetic accessors, which an old VM cannot verify. Hence `-source 8 -target 8` and a few package-private members.
- Preverification is ProGuard 7.10 rather than the old `preverify.exe`. `-microedition` writes the CLDC StackMap attribute the VM requires, and `-target 1.2` rewrites class files to version 46, since CLDC will not load version 50 and above. It runs `-dontshrink -dontoptimize -dontobfuscate`, acting purely as a preverifier and downgrader.

**The perspective Cover Flow is a software texture map.** The Asha 210 has no GPU and `lcdui.Graphics` has no perspective transform, so rotating album art per frame is impossible. Instead, `Gfx.perspective()` does a per-column projective map: each source column of the image lands at its own screen x and its own height, so a square becomes a trapezoid. Four angle keyframes (0, 26, 46, 62 degrees) are pre-rendered on a background thread and cached, and a cover steps through them while its position slides over 280 ms, interpolated from a wall clock rather than a frame counter so timing survives frame drops. The album flip uses the same approach over 420 ms, with both halves near edge-on at the midpoint to hide the swap from cover to panel.

**The memory limit is not 32 MB.** That is device RAM. A MIDlet on this class of phone gets roughly 2 MB of Java heap. `Image.createImage()` inflates artwork to source resolution, so a single 600 x 600 cover is around 1.4 MB, most of the heap for one album. Artwork is therefore recorded as a byte offset into the music file during the scan and only read at the moment it is drawn.

Full write-up:  [Can your old Nokia phone act as your iPod in 2026?](https://vinayakrao.framer.website/blog/nokia_ipod)

## Building from source

Requires Temurin JDK 17 and ProGuard 7.10. No IDE, no SDK, no emulator.

```
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

```

Output is `ipod.jar` and `ipod.jad`. Current build: 74,220 bytes, 4,451 lines across 25 classes.

## License

MIT. Do what you like with it.
