#!/bin/bash
# Сборка .hmod для одного модуля.
# Использование: ./build.sh demo 1.0.0 stable
# Нужны: JDK 17+, ANDROID_HOME (platforms/android-36/android.jar, build-tools с d8).
# Для модулей с экранами/движками на классах телеграма:
#   export APP_API_JAR=/шлях/до/amegram-app.jar   (зібрати в amenew: ./gradlew :TMessagesProj:exportModuleApi)
# Додаткові compileOnly-банки (gson/okhttp/...) — поклади в ext-libs/ (в рантаймі їх дає застосунок).
set -e
MOD=${1:-demo}
VER=${2:-1.0.0}
BRANCH=${3:-stable}
HERE=$(cd "$(dirname "$0")" && pwd)
OUT="$HERE/out"
rm -rf "$OUT" && mkdir -p "$OUT/classes"

ANDROID_JAR="$ANDROID_HOME/platforms/android-36/android.jar"
D8=$(ls -d "$ANDROID_HOME/build-tools/"*/d8 2>/dev/null | sort | tail -1)

CP="$HERE/hot-api:$ANDROID_JAR"
LIBS="--lib $ANDROID_JAR"
if [ -n "$APP_API_JAR" ] && [ -f "$APP_API_JAR" ]; then
  CP="$CP:$APP_API_JAR"
  LIBS="$LIBS --lib $APP_API_JAR"
fi
for j in "$HERE/ext-libs/"*.jar; do
  [ -f "$j" ] || continue
  CP="$CP:$j"
  LIBS="$LIBS --lib $j"
done

javac -source 8 -target 8 -nowarn \
  -cp "$CP" \
  -d "$OUT/classes" \
  "$HERE/modules/$MOD/"*.java

# shellcheck disable=SC2086
"$D8" --min-api 27 $LIBS --output "$OUT/dex" "$OUT/classes"

python3 - "$HERE/modules/$MOD/manifest.json" "$VER" "$BRANCH" <<'PY'
import json, sys
src, ver, branch = sys.argv[1], sys.argv[2], sys.argv[3]
m = json.load(open(src))
m["version"] = ver
m["branch"] = branch
json.dump(m, open("/tmp/hmod_manifest.json", "w"), ensure_ascii=False)
PY

mkdir -p "$OUT/pkg" && cp "$OUT/dex/classes.dex" "$OUT/pkg/" && cp /tmp/hmod_manifest.json "$OUT/pkg/manifest.json"
cd "$OUT/pkg" && zip -q -X "$OUT/$MOD-$VER.hmod" classes.dex manifest.json
sha256sum "$OUT/$MOD-$VER.hmod"
echo "Готово: $OUT/$MOD-$VER.hmod — залей в релиз и обнови modules.json (url + sha256 + version)."
