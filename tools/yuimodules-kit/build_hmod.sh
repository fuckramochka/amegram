#!/bin/bash
# Збірка .hmod з одного .java модуля.
# Використання: ./build_hmod.sh com.example.hotmod.DemoModule DemoModule.java manifest.json out/
# Потрібні: javac, d8 (Android build-tools) або dx, zip.
# api-класи беруться з TMessagesProj (compileOnly — НЕ пакуються в dex!).
set -e
FQCN="$1"; SRC="$2"; MANIFEST="$3"; OUTDIR="$4"
if [ -z "$FQCN" ] || [ -z "$SRC" ] || [ -z "$MANIFEST" ] || [ -z "$OUTDIR" ]; then
  echo "usage: $0 <FQCN> <Src.java> <manifest.json> <outdir>"
  exit 2
fi
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
# api-класи HotModule/HotHost/HotRow живуть у КЛІЄНТІ (app.amegram.hot.api).
# Шукаємо їх так: $HOTMOD_API -> ./api (дзеркало в цьому репо) ->
if [ -z "$ANDROID_HOME" ] && [ -d "/home/user/android-sdk" ]; then
  export ANDROID_HOME="/home/user/android-sdk"
fi
if ! command -v javac >/dev/null 2>&1; then
  JDK_BIN="$(ls -d /home/user/.gradle/jdks/*/bin 2>/dev/null | head -1)"
  if [ -n "$JDK_BIN" ]; then export PATH="$JDK_BIN:$PATH"; fi
fi

API_CP="${HOTMOD_API:-}"
if [ -z "$API_CP" ]; then
  if [ -d "$ROOT/TMessagesProj/src/main/java" ]; then API_CP="$ROOT/TMessagesProj/src/main/java"
  elif [ -d "$ROOT/api" ]; then API_CP="$ROOT/api"
  elif [ -d "$ROOT/../amegram/TMessagesProj/src/main/java" ]; then API_CP="$ROOT/../amegram/TMessagesProj/src/main/java"
  else
    echo "нема api-класів: скопіюй app/amegram/hot/api/*.java з клієнта в ./api/ або вкажи HOTMOD_API"
    exit 2
  fi
fi

ANDROID_JAR=""
if [ -n "$ANDROID_HOME" ]; then
  ANDROID_JAR="$(ls -d $ANDROID_HOME/platforms/android-*/android.jar 2>/dev/null | sort -V | tail -1)"
fi
CP="$API_CP"
if [ -n "$ANDROID_JAR" ]; then
  CP="$API_CP:$ANDROID_JAR"
fi

TMP="$(mktemp -d)"
trap "rm -rf $TMP" EXIT
mkdir -p "$OUTDIR" "$TMP/classes"
echo "[1/4] lint manifest..."
python3 "$(dirname "$0")/lint_manifest.py" --help >/dev/null 2>&1 || true
if [ -d "$SRC" ]; then
  SRC_FILES="$(find "$SRC" -name '*.java' -print)"
else
  SRC_FILES="$SRC"
fi
javac -source 8 -target 8 -cp "$CP" -d "$TMP/classes" $SRC_FILES
echo "[2/4] compiled."
D8_LIB_ARGS="--lib $API_CP"
if [ -n "$ANDROID_JAR" ]; then
  D8_LIB_ARGS="--lib $API_CP --lib $ANDROID_JAR"
fi
if command -v d8 >/dev/null 2>&1; then
  d8 --min-api 24 $D8_LIB_ARGS --output "$TMP" $(find "$TMP/classes" -name "*.class")
elif [ -n "$ANDROID_HOME" ] && ls "$ANDROID_HOME/build-tools" >/dev/null 2>&1; then
  D8="$(ls -d $ANDROID_HOME/build-tools/*/d8 | sort -V | tail -1)"
  "$D8" --min-api 24 $D8_LIB_ARGS --output "$TMP" $(find "$TMP/classes" -name "*.class")
else
  echo "pine: d8 не знайдено (встанови Android build-tools). dex пропущено."
  exit 3
fi
echo "[3/4] dex ready."
ID=$(python3 -c "import json;print(json.load(open('$MANIFEST'))['id'])")
VER=$(python3 -c "import json;print(json.load(open('$MANIFEST'))['version'])")
cp "$MANIFEST" "$TMP/manifest.json"
(cd "$TMP" && python3 -c "import zipfile, glob, os, sys; out = sys.argv[1]; z = zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED); z.write('manifest.json'); [z.write(f) for f in glob.glob('*.dex')]; z.close()" "$OUTDIR/${ID}-${VER}.hmod")
echo "[4/4] OK: $OUTDIR/${ID}-${VER}.hmod"
