package app.amegram.module.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import app.amegram.core.modules.ModuleManager;

/**
 * «+» хаба: каталог модулів. Спочатку GitHub, при недоступності —
 * вбудований asset. Стиль карток — як у каталозі плагінів.
 */
public class ModuleCatalogSheet extends BottomSheet {

    public static final String REMOTE_CATALOG_URL =
            "https://raw.githubusercontent.com/fuckramochka/amegram/main/modules/catalog.json";

    public interface OnChanged {
        void onChanged();
    }

    private final LinearLayout itemsContainer;
    private final OnChanged onChanged;

    private static class Entry {
        String id = "";
        String title = "";
        String version = "";
        String description = "";
        String downloadUrl = "";
    }

    public ModuleCatalogSheet(Context context, OnChanged onChanged) {
        super(context, false);
        this.onChanged = onChanged;

        int bg = getThemedColor(Theme.key_dialogBackground);
        if (bg == 0) {
            bg = 0xFF14151F;
        }
        int text = getThemedColor(Theme.key_dialogTextBlack);
        if (text == 0) {
            text = 0xFFFFFFFF;
        }
        int sub = getThemedColor(Theme.key_dialogTextGray2);
        if (sub == 0) {
            sub = 0xAAFFFFFF;
        }
        int accent = getThemedColor(Theme.key_featuredStickers_addButton);
        if (accent == 0) {
            accent = 0xFF6C63FF;
        }

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        root.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12),
                AndroidUtilities.dp(16), AndroidUtilities.dp(16));

        TextView title = new TextView(context);
        title.setText("\u041a\u0430\u0442\u0430\u043b\u043e\u0433 \u043c\u043e\u0434\u0443\u043b\u0456\u0432");
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(text);
        root.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        ScrollView scroll = new ScrollView(context);
        itemsContainer = new LinearLayout(context);
        itemsContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(itemsContainer);
        root.addView(scroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        setCustomView(root);
        loadCatalog(text, sub, accent);
    }

    private void loadCatalog(final int text, final int sub, final int accent) {
        new Thread(() -> {
            final List<Entry> entries = fetchEntries();
            AndroidUtilities.runOnUIThread(() -> renderEntries(entries, text, sub, accent));
        }, "amod-catalog").start();
    }

    private List<Entry> fetchEntries() {
        List<Entry> out = new ArrayList<>();
        String json = fetchRemote();
        if (json == null) {
            json = readAsset();
        }
        if (json == null) {
            return out;
        }
        try {
            JSONObject root = new JSONObject(json);
            JSONArray arr = root.optJSONArray("modules");
            if (arr == null) {
                return out;
            }
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                Entry e = new Entry();
                e.id = o.optString("id", "");
                e.title = o.optString("title", e.id);
                e.version = o.optString("version", "");
                e.description = o.optString("description", "");
                e.downloadUrl = o.optString("downloadUrl", "");
                if (!e.id.isEmpty()) {
                    out.add(e);
                }
            }
        } catch (Throwable ignore) {
        }
        return out;
    }

    private String fetchRemote() {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(REMOTE_CATALOG_URL).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            if (conn.getResponseCode() != 200) {
                return null;
            }
            BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line);
            }
            r.close();
            return sb.toString();
        } catch (Throwable t) {
            return null;
        } finally {
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Throwable ignore) {
                }
            }
        }
    }

    private String readAsset() {
        try {
            Context ctx = getContext();
            InputStream in = ctx.getAssets().open("amegram_catalog.json");
            BufferedReader r = new BufferedReader(new InputStreamReader(in));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line);
            }
            r.close();
            return sb.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    private void renderEntries(List<Entry> entries, int text, int sub, int accent) {
        Context context = getContext();
        if (context == null) {
            return;
        }
        itemsContainer.removeAllViews();
        if (entries.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText("\u041a\u0430\u0442\u0430\u043b\u043e\u0433 \u043d\u0435\u0434\u043e\u0441\u0442\u0443\u043f\u043d\u0438\u0439. \u041f\u0435\u0440\u0435\u0432\u0456\u0440\u0442\u0435 \u043c\u0435\u0440\u0435\u0436\u0443.");
            empty.setTextColor(sub);
            itemsContainer.addView(empty);
            return;
        }
        for (Entry e : entries) {
            itemsContainer.addView(makeCard(e, text, sub, accent),
                    LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                            LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));
        }
    }

    private LinearLayout makeCard(final Entry e, int text, int sub, int accent) {
        final Context context = getContext();
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(getThemedColor(Theme.key_dialogBackground));
        cardBg.setCornerRadius(AndroidUtilities.dp(16));
        cardBg.setStroke(AndroidUtilities.dp(1),
                android.graphics.Color.argb(25, 128, 128, 128));
        card.setBackground(cardBg);
        card.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12),
                AndroidUtilities.dp(16), AndroidUtilities.dp(12));

        LinearLayout topRow = new LinearLayout(context);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView nameView = new TextView(context);
        nameView.setText(e.title + (e.version.isEmpty() ? "" : "  v" + e.version));
        nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        nameView.setTypeface(AndroidUtilities.bold());
        nameView.setTextColor(text);
        topRow.addView(nameView, LayoutHelper.createLinear(
                0, LayoutHelper.WRAP_CONTENT, 1.0f));

        final TextView actionBtn = new TextView(context);
        actionBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
        actionBtn.setTypeface(AndroidUtilities.bold());
        actionBtn.setGravity(Gravity.CENTER);
        actionBtn.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(6),
                AndroidUtilities.dp(14), AndroidUtilities.dp(6));
        GradientDrawable actBg = new GradientDrawable();
        actBg.setCornerRadius(AndroidUtilities.dp(14));
        if (e.downloadUrl.isEmpty()) {
            actionBtn.setText("\u0412\u0431\u0443\u0434\u043e\u0432\u0430\u043d\u043e \u2713");
            actBg.setColor(android.graphics.Color.argb(30, 46, 204, 113));
            actionBtn.setTextColor(0xFF2ECC71);
            actionBtn.setOnClickListener(null);
        } else {
            actionBtn.setText("\u0412\u0441\u0442\u0430\u043d\u043e\u0432\u0438\u0442\u0438");
            actBg.setColor(accent);
            actionBtn.setTextColor(0xFFFFFFFF);
            actionBtn.setOnClickListener(v -> {
                actionBtn.setText("...");
                ModuleManager.downloadAndInstall(e.downloadUrl, (ok, msg) -> {
                    actionBtn.setText(ok ? "\u0412\u0441\u0442\u0430\u043d\u043e\u0432\u043b\u0435\u043d\u043e \u2713" : "\u041f\u043e\u043c\u0438\u043b\u043a\u0430");
                    if (ok && onChanged != null) {
                        onChanged.onChanged();
                    }
                });
            });
        }
        actionBtn.setBackground(actBg);
        topRow.addView(actionBtn, LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 8, 0, 0, 0));
        card.addView(topRow, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        if (!e.description.isEmpty()) {
            TextView desc = new TextView(context);
            desc.setText(e.description);
            desc.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            desc.setTextColor(text);
            desc.setPadding(0, AndroidUtilities.dp(6), 0, 0);
            card.addView(desc, LayoutHelper.createLinear(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        } else {
            TextView desc = new TextView(context);
            desc.setText("id: " + e.id);
            desc.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            desc.setTextColor(sub);
            desc.setPadding(0, AndroidUtilities.dp(6), 0, 0);
            card.addView(desc, LayoutHelper.createLinear(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
        return card;
    }
}
