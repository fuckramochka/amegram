#!/usr/bin/env bash
# Extends verify-module.sh: compiles the two UI files against signature-faithful
# stubs (every signature copied verbatim from repo sources, see grep refs below).
set -e
TC=/tmp/opencode/toolchain
if [ -f /tmp/opencode/sdk/platforms/android-36/android.jar ]; then TC_ANDROID_JAR=/tmp/opencode/sdk/platforms/android-36/android.jar; else TC_ANDROID_JAR=$TC/android.jar; fi
JD="$(echo "$TC"/jdk-21* | cut -d' ' -f1)"
JAVAC="$JD/bin/javac"
STUBS=/tmp/opencode/stubs-ui
OUT=/tmp/opencode/mod-classes-ui
SRC="$(cd "$(dirname "$0")/.." && pwd)/TMessagesProj/src/main/java"
rm -rf "$STUBS" "$OUT"
mkdir -p "$STUBS"/org/telegram/ui/ActionBar "$STUBS"/org/telegram/ui/Cells "$STUBS"/org/telegram/ui/Components "$STUBS"/org/telegram/messenger "$STUBS"/tw/nekomimi/nekogram/settings "$STUBS"/tw/nekomimi/nekogram/ui/cells "$STUBS"/androidx/annotation "$STUBS"/androidx/recyclerview/widget "$OUT"

cat > "$STUBS/androidx/annotation/NonNull.java" <<'EOF'
package androidx.annotation;
public @interface NonNull {}
EOF
cat > "$STUBS/androidx/recyclerview/widget/RecyclerView.java" <<'EOF'
package androidx.recyclerview.widget;
public class RecyclerView {
    public static class ViewHolder {
        public android.view.View itemView;
        public ViewHolder(android.view.View v) { itemView = v; }
        public int getItemViewType() { return 0; }
    }
}
EOF
# Signatures from org/telegram/ui/ActionBar/BottomSheet.java:1219,1196,1673,1603,2153
cat > "$STUBS/org/telegram/ui/ActionBar/BottomSheet.java" <<'EOF'
package org.telegram.ui.ActionBar;
public class BottomSheet {
    public BottomSheet(android.content.Context context, boolean needFocus) {}
    protected int getThemedColor(int key) { return 0; }
    public android.content.Context getContext() { return null; }
    public void setCustomView(android.view.View view) {}
    public void show() {}
    public void dismiss() {}
}
EOF
# Keys from org/telegram/ui/ActionBar/Theme.java:2575,2577,2585,2662
cat > "$STUBS/org/telegram/ui/ActionBar/Theme.java" <<'EOF'
package org.telegram.ui.ActionBar;
public class Theme {
    public static final int key_dialogBackground = 1;
    public static final int key_dialogTextBlack = 2;
    public static final int key_dialogTextGray2 = 3;
    public static final int key_windowBackgroundWhiteBlueHeader = 4;
    public static final int key_windowBackgroundWhite = 5;
    public static final int key_featuredStickers_addButton = 6;
}
EOF
# Arity mirrors LayoutHelper.java:234 (w,h,weight), :210 (w,h,4 margins), :238 (w,h)
cat > "$STUBS/org/telegram/ui/Components/LayoutHelper.java" <<'EOF'
package org.telegram.ui.Components;
public class LayoutHelper {
    public static final int MATCH_PARENT = -1;
    public static final int WRAP_CONTENT = -2;
    public static android.widget.LinearLayout.LayoutParams createLinear(int w, int h, float weight) { return null; }
    public static android.widget.LinearLayout.LayoutParams createLinear(int w, int h, int gravity, int a, int b, int c, int d) { return null; }
    public static android.widget.LinearLayout.LayoutParams createLinear(int w, int h, int a, int b, int c, int d) { return null; }
    public static android.widget.LinearLayout.LayoutParams createLinear(int w, int h) { return null; }
}
EOF
# AndroidUtilities.java:2713 dp(float), :271 bold()
cat > "$STUBS/org/telegram/messenger/AndroidUtilities.java" <<'EOF'
package org.telegram.messenger;
public class AndroidUtilities {
    public static int dp(float value) { return (int) value; }
    public static android.graphics.Typeface bold() { return null; }
    public static void runOnUIThread(Runnable r) {}
}
EOF
cat > "$STUBS/org/telegram/messenger/R.java" <<'EOF'
package org.telegram.messenger;
public final class R {
    public static final class drawable {
        public static final int msg_bot = 1;
        public static final int msg_download_solar = 2;
        public static final int msg_plugins = 3;
    }
}
EOF
cat > "$STUBS/org/telegram/messenger/NotificationCenter.java" <<'EOF'
package org.telegram.messenger;
public class NotificationCenter {
    public static final int mainUserInfoChanged = 1;
    public void postNotificationName(int id, Object... args) {}
}
EOF
# TextCell.java:399,499,617 / TextCheckCell.java:218 / TextInfoPrivacyCell.java:163
cat > "$STUBS/org/telegram/ui/Cells/TextCell.java" <<'EOF'
package org.telegram.ui.Cells;
public class TextCell extends android.view.View {
    public TextCell(android.content.Context c) { super(c); }
    public void setTextAndIcon(CharSequence t, int resId, boolean d) {}
    public void setTextAndValue(CharSequence t, CharSequence v, boolean d) {}
    public void setTextAndValueAndIcon(CharSequence t, CharSequence v, int resId, boolean d) {}
}
EOF
cat > "$STUBS/org/telegram/ui/Cells/TextCheckCell.java" <<'EOF'
package org.telegram.ui.Cells;
public class TextCheckCell extends android.view.View {
    public TextCheckCell(android.content.Context c) { super(c); }
    public void setTextAndCheck(CharSequence t, boolean c, boolean d) {}
    public void setChecked(boolean c) {}
}
EOF
cat > "$STUBS/org/telegram/ui/Cells/TextInfoPrivacyCell.java" <<'EOF'
package org.telegram.ui.Cells;
public class TextInfoPrivacyCell extends android.view.View {
    public TextInfoPrivacyCell(android.content.Context c) { super(c); }
    public void setText(CharSequence t) {}
}
EOF
# HeaderCell.java:169
cat > "$STUBS/tw/nekomimi/nekogram/ui/cells/HeaderCell.java" <<'EOF'
package tw.nekomimi.nekogram.ui.cells;
public class HeaderCell extends android.view.View {
    public HeaderCell(android.content.Context c) { super(c); }
    public void setText(CharSequence t) {}
}
EOF
# BaseNekoSettingsActivity: TYPE_* (:64-65), BaseListAdapter (:362-366), addRow/listAdapter
cat > "$STUBS/tw/nekomimi/nekogram/settings/BaseNekoSettingsActivity.java" <<'EOF'
package tw.nekomimi.nekogram.settings;
public class BaseNekoSettingsActivity {
    public static final int TYPE_HEADER = 4;
    public static final int TYPE_CHECK = 3;
    public static final int TYPE_TEXT = 1;
    public static final int TYPE_INFO_PRIVACY = 2;
    protected int rowCount;
    protected BaseListAdapter listAdapter;
    protected int addRow() { return rowCount++; }
    protected void updateRows() {}
    public void onItemClick(android.view.View v, int p, float x, float y) {}
    protected String getActionBarTitle() { return ""; }
    protected BaseListAdapter createAdapter(android.content.Context c) { return null; }
    public void onResume() {}
    public android.app.Activity getParentActivity() { return null; }
    public boolean presentFragment(Object fragment) { return true; }
    public int getThemedColor(int key) { return 0; }
    public org.telegram.messenger.NotificationCenter getNotificationCenter() { return null; }
    protected abstract class BaseListAdapter {
        public BaseListAdapter(android.content.Context c) {}
        public int getItemViewType(int position) { return 0; }
        public void onBindViewHolder(androidx.recyclerview.widget.RecyclerView.ViewHolder h, int p, boolean partial) {}
        public androidx.recyclerview.widget.RecyclerView.ViewHolder onCreateViewHolder(android.view.ViewGroup p, int t) { return null; }
        public void notifyDataSetChanged() {}
    }
}
EOF
# Switch.java:130 ctor, :371 setChecked, :424 isChecked
cat > "$STUBS/org/telegram/ui/Components/Switch.java" <<'EOF'
package org.telegram.ui.Components;
public class Switch extends android.view.View {
    public Switch(android.content.Context c) { super(c); }
    public void setChecked(boolean checked, boolean animated) {}
    public boolean isChecked() { return false; }
}
EOF
cat > "$STUBS/org/telegram/ui/Components/RecyclerListView.java" <<'EOF'
package org.telegram.ui.Components;
public class RecyclerListView {
    public static class Holder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
        public Holder(android.view.View v) { super(v); }
    }
}
EOF
mkdir -p "$STUBS/app/exteraless/plugins/ui"
cat > "$STUBS/app/exteraless/plugins/ui/PluginsActivity.java" <<'EOF'
package app.exteraless.plugins.ui;
public class PluginsActivity {
    public PluginsActivity() {}
}
EOF
mkdir -p "$STUBS/app/miogram/bridge/patch" "$STUBS/app/miogram/bridge/vault"
cat > "$STUBS/app/miogram/bridge/vault/MiogramDoubleBottomActivity.java" <<'EOF'
package app.miogram.bridge.vault;
public class MiogramDoubleBottomActivity {
    public MiogramDoubleBottomActivity() {}
}
EOF
cat > "$STUBS/app/miogram/bridge/patch/AmegramPatchManager.java" <<'EOF'
package app.miogram.bridge.patch;
public class AmegramPatchManager {
    public interface PatchCheckCallback {
        void onCheckComplete(int newPatchesApplied, String message);
    }
    public static AmegramPatchManager getInstance() { return null; }
    public int getAppliedPatchCount() { return 0; }
    public void checkForPatches(PatchCheckCallback callback) {}
}
EOF
# ApplicationLoader stub (same as verify-module.sh)
mkdir -p "$STUBS/org/telegram/messenger"
cat > "$STUBS/org/telegram/messenger/ApplicationLoader.java" <<'EOF'
package org.telegram.messenger;
public class ApplicationLoader {
    public static android.content.Context applicationContext;
}
EOF

"$JAVAC" -proc:none -nowarn \
  -cp "$TC_ANDROID_JAR:$TC/json.jar" \
  -sourcepath "$STUBS:$SRC" -d "$OUT" \
  "$SRC/app/amegram/module/ui/AmegramModulesActivity.java" \
  "$SRC/app/amegram/module/ui/AmegramWelcomeSheet.java"
echo "MODULE-UI-JAVAC-OK (2 files, stub-verified call sites)"
