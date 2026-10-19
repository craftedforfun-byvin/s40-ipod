#!/bin/sh
# Same build as build.ps1, for a POSIX shell.
# Needs a JDK (8 - 21) on PATH and tools/proguard.jar.
set -e
cd "$(dirname "$0")"

command -v javac >/dev/null 2>&1 || { echo "No javac on PATH. Install a JDK (8-21)."; exit 1; }
[ -f tools/proguard.jar ] || {
  echo "tools/proguard.jar is missing."
  echo "Take lib/proguard.jar from a ProGuard 7.x release and drop it in tools/."
  exit 1
}

JDK="$(dirname "$(dirname "$(command -v javac)")")"
echo "JDK: $JDK"

rm -rf build/classes build/stubs build/pre dist
mkdir -p build/classes build/stubs build/pre dist

echo "[1/5] compiling API stubs"
find stubs -name '*.java' > build/stubs.list
javac -nowarn -source 8 -target 8 -d build/stubs @build/stubs.list

echo "[2/5] compiling the MIDlet"
find src -name '*.java' > build/src.list
javac -nowarn -source 8 -target 8 -classpath build/stubs -d build/classes @build/src.list

echo "[3/5] preverifying for CLDC 1.1"
if [ -f "$JDK/jmods/java.base.jmod" ]; then
  LIB="$JDK/jmods/java.base.jmod(!**.jar;!module-info.class)"
else
  LIB="$JDK/jre/lib/rt.jar"
fi
java -jar tools/proguard.jar \
     -injars build/classes -outjars build/pre \
     -libraryjars build/stubs -libraryjars "$LIB" \
     @build/proguard.pro

echo "[4/5] packaging ipod.jar"
cp res/icon.png build/pre/icon.png
jar cfm dist/ipod.jar build/MANIFEST.MF -C build/pre .

echo "[5/5] writing ipod.jad"
SIZE=$(wc -c < dist/ipod.jar | tr -d ' ')
cat > dist/ipod.jad <<EOF
MIDlet-1: iPod, /icon.png, ipod.IPodMIDlet
MIDlet-Name: iPod
MIDlet-Vendor: Homebrew
MIDlet-Version: 1.0.0
MIDlet-Description: An iPod-style MP3 player for Series 40
MIDlet-Icon: /icon.png
MIDlet-Jar-URL: ipod.jar
MIDlet-Jar-Size: $SIZE
MicroEdition-Configuration: CLDC-1.1
MicroEdition-Profile: MIDP-2.0
MIDlet-Permissions: javax.microedition.io.Connector.file.read
Nokia-MIDlet-Category: Application
EOF

echo
echo "Built dist/ipod.jar ($SIZE bytes) and dist/ipod.jad"
