#!/bin/bash
# Збірка md3player.hmod (тільки entry-клас; класи плеєра беруться з APK).
# Потрібні: javac, d8 (Android build-tools), android.jar, zip.
#   ./build.sh /path/to/android.jar ./out/
# api-класи: ../../TMessagesProj/src/main/java (compileOnly).
set -e
ANDROID_JAR="$1"; OUTDIR="${2:-./out}"
HERE="$(cd "$(dirname "$0")" && pwd)"
APP="$HERE/../../TMessagesProj/src/main/java"
if [ -z "$ANDROID_JAR" ] || [ ! -f "$ANDROID_JAR" ]; then
  echo "usage: $0 /path/to/android.jar [outdir]"
  exit 2
fi
TMP="$(mktemp -d)"; trap "rm -rf $TMP" EXIT
mkdir -p "$OUTDIR" "$TMP/classes"
javac -source 8 -target 8 -cp "$ANDROID_JAR:$APP" -d "$TMP/classes" \
  "$HERE/src/com/amegram/mods/md3player/Md3PlayerModule.java"
echo "[ok] compiled."
if command -v d8 >/dev/null 2>&1; then D8=d8;
elif [ -n "$ANDROID_HOME" ]; then D8="$(ls -d $ANDROID_HOME/build-tools/*/d8 | sort -V | tail -1)";
else echo "no d8"; exit 3; fi
"$D8" --min-api 24 --lib "$ANDROID_JAR" --output "$TMP" $(find "$TMP/classes" -name "*.class")
cp "$HERE/manifest.json" "$TMP/manifest.json"
(cd "$TMP" && zip -q -r "$OUTDIR/md3player-1.0.0.hmod" classes.dex manifest.json)
echo "[ok] $OUTDIR/md3player-1.0.0.hmod"
echo "Далі: meta.json в yuimodules + make_modules_json.py + sync_from_yuimodules.sh"
