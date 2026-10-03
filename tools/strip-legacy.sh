#!/usr/bin/env bash
# Copies the self-contained Amegram Module onto a vanilla DrKLO/Telegram checkout
# and reports remaining legacy references that still need porting.
# Usage: ./tools/strip-legacy.sh /path/to/Telegram-vanilla
set -e
VANILLA="${1:?usage: strip-legacy.sh /path/to/Telegram-vanilla}"
SRC="$(cd "$(dirname "$0")/.." && pwd)"
MOD="TMessagesProj/src/main/java/app/amegram/module"
TEST="TMessagesProj/src/test/kotlin/app/amegram"

mkdir -p "$VANILLA/$MOD" "$VANILLA/$TEST"
cp -r "$SRC/$MOD/"* "$VANILLA/$MOD/"
cp -r "$SRC/$TEST/"* "$VANILLA/$TEST/" 2>/dev/null || true

echo "== module copied =="
find "$VANILLA/$MOD" -name "*.java" | sort
echo
echo "== 5 one-line hooks to add in vanilla (see $MOD/AmegramHooks.java) =="
echo " ApplicationLoader.onCreate        -> AmegramModule.init(this);"
echo " LaunchActivity.onCreate           -> AmegramHooks.onLaunchCreated();"
echo " DialogsActivity.createView        -> AmegramHooks.onDialogsCreate(fragment);"
echo " ChatMessageCell.onDraw            -> AmegramHooks.onChatCellDraw(cell, canvas);"
echo " AudioPlayerAlert.<init>           -> AmegramHooks.onPlayerOpen(alert);"
echo " SendMessagesHelper (read path)    -> if (AmegramHooks.shouldBlockReadReceipts()) return;"
echo
echo "== legacy refs still inside THIS repo (must hit 0 before deleting app.miogram/exteraless) =="
grep -rl "app\.miogram\|app\.exteraless" "$SRC/TMessagesProj/src/main/java/org/telegram" | wc -l
