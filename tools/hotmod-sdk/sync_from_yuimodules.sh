#!/bin/bash
# Синхронізація офлайн-seed з РЕПОЗИТОРІЮ МОДУЛІВ (yuimodules) у клієнт.
# Розкол: модулі живуть і збираються в yuimodules, клієнт (цей репо) лише
# споживає modules.json + .hmod. Слепок в assets/hotmodules — тільки fallback
# на випадок відсутності інтернету, НЕ джерело правди.
#
# Використання: ./sync_from_yuimodules.sh [branch]
set -e
BRANCH="${1:-main}"
BASE="https://raw.githubusercontent.com/fuckramochka/yuimodules/$BRANCH"
ASSETS="TMessagesProj/src/main/assets/hotmodules"
TMP="$(mktemp -d)"
trap "rm -rf $TMP" EXIT

echo "[1/3] тягну modules.json з yuimodules@$BRANCH..."
curl -fsSL "$BASE/modules.json" -o "$TMP/modules.json"
python3 tools/hotmod-sdk/lint_manifest.py --catalog "$TMP/modules.json" || {
  echo "стоп: каталог yuimodules не проходить лінт"
  exit 1
}

echo "[2/3] тягну .hmod..."
mkdir -p "$TMP/hmods"
python3 - "$TMP/modules.json" "$TMP/hmods" "$BASE" <<'EOF'
import json, sys, urllib.request, os
cat = json.load(open(sys.argv[1], encoding="utf-8"))
out = sys.argv[2]
seen = set()
def grab(b, mid):
    url = b["url"]
    fn = f"{mid}-{b['version']}.hmod"
    if fn in seen:
        return
    seen.add(fn)
    print("  ", fn)
    urllib.request.urlretrieve(url, os.path.join(out, fn))
for m in cat["modules"]:
    for b in (m.get("branches") or {}).values():
        grab(b, m["id"])
    for b in m.get("history", []) or []:
        grab(b, m["id"])
EOF

echo "[3/3] лінт + розкладка в $ASSETS..."
for f in "$TMP"/hmods/*.hmod; do
  python3 tools/hotmod-sdk/lint_manifest.py "$f" >/dev/null || {
    echo "стоп: битий $f"
    exit 1
  }
done
mkdir -p "$ASSETS"
cp "$TMP/modules.json" "$ASSETS/modules.json"
cp "$TMP"/hmods/*.hmod "$ASSETS/"
echo "OK: $(ls $ASSETS/*.hmod | wc -l) .hmod + modules.json"
