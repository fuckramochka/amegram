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
# сусідній чекаут клієнта -> TMessagesProj всередині цього репо.
API_CP="${HOTMOD_API:-}"
if [ -z "$API_CP" ]; then
  if [ -d "$ROOT/api" ]; then API_CP="$ROOT/api"
  elif [ -d "$ROOT/../amegram/TMessagesProj/src/main/java" ]; then API_CP="$ROOT/../amegram/TMessagesProj/src/main/java"
  elif [ -d "$ROOT/TMessagesProj/src/main/java" ]; then API_CP="$ROOT/TMessagesProj/src/main/java"
  else
    echo "нема api-класів: скопіюй app/amegram/hot/api/*.java з клієнта в ./api/ або вкажи HOTMOD_API"
    exit 2
  fi
fi
TMP="$(mktemp -d)"
trap "rm -rf $TMP" EXIT
mkdir -p "$OUTDIR" "$TMP/classes"
echo "[1/4] lint manifest..."
python3 "$(dirname "$0")/lint_manifest.py" --help >/dev/null
javac -source 8 -target 8 -cp "$API_CP" -d "$TMP/classes" "$SRC"
echo "[2/4] compiled."
if command -v d8 >/dev/null 2>&1; then
  d8 --min-api 24 --lib "$API_CP" --output "$TMP" $(find "$TMP/classes" -name "*.class")
elif [ -n "$ANDROID_HOME" ] && ls "$ANDROID_HOME/build-tools" >/dev/null 2>&1; then
  D8="$(ls -d $ANDROID_HOME/build-tools/*/d8 | sort -V | tail -1)"
  "$D8" --min-api 24 --output "$TMP" $(find "$TMP/classes" -name "*.class")
else
  echo "pine: d8 не знайдено (встанови Android build-tools). dex пропущено."
  exit 3
fi
echo "[3/4] dex ready."
ID=$(python3 -c "import json;print(json.load(open('$MANIFEST'))['id'])")
VER=$(python3 -c "import json;print(json.load(open('$MANIFEST'))['version'])")
cp "$MANIFEST" "$TMP/manifest.json"
(cd "$TMP" && zip -q -r "$OUTDIR/${ID}-${VER}.hmod" manifest.json classes*.dex 2>/dev/null || zip -q -r "$OUTDIR/${ID}-${VER}.hmod" manifest.json *.dex)
echo "[4/4] OK: $OUTDIR/${ID}-${VER}.hmod"
