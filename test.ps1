# Runs the desktop smoke test for the non-UI code: ID3 parsing, library
# grouping, sorting, string joining.
#
# test/ contains real java.io-backed versions of Connector and FileConnection.
# Putting them ahead of stubs/ on the classpath lets Id3 read actual files on a
# desktop JVM, so the tag parser can be exercised without a phone.
#
#   powershell -ExecutionPolicy Bypass -File .\test.ps1

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\javac.exe"))) { $jdk = $env:JAVA_HOME }
else {
    $jc = Get-Command javac.exe -ErrorAction SilentlyContinue
    if ($jc) { $jdk = Split-Path (Split-Path $jc.Source -Parent) -Parent }
    else { Write-Host "No JDK found - set JAVA_HOME." -ForegroundColor Red; exit 1 }
}

if (-not (Test-Path build\classes\ipod\Id3.class)) {
    Write-Host "Build first: .\build.ps1" -ForegroundColor Yellow
    exit 1
}

New-Item -ItemType Directory -Force -Path build\test | Out-Null
$src = Get-ChildItem -Recurse test -Filter *.java | ForEach-Object { $_.FullName }
& "$jdk\bin\javac.exe" -nowarn -classpath "build\classes;build\stubs" -d build\test $src
if ($LASTEXITCODE -ne 0) { throw "test compile failed" }

& "$jdk\bin\java.exe" -cp "build\test;build\classes;build\stubs" ipod.Harness
exit $LASTEXITCODE
