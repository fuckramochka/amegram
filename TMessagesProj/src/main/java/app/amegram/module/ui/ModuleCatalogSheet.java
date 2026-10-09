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
    private final List<Entry> allEntries = new ArrayList<>();
    private String query = "";
    private int textColor;
    private int subColor;
    private int accentColor;
    private TextView statusView;

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
                LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));
        this.textColor = text;
        this.subColor = sub;
        this.accentColor = accent;

        android.widget.EditText search = new android.widget.EditText(context);
        search.setHint("Пошук…");
        search.setSingleLine(true);
        search.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        try {
            search.setTextColor(text);
            search.setHintTextColor(sub);
        } catch (Throwable ignore) {
        }
        search.setBackground(app.amegram.theme.YumiTheme.cardBackground(12));
        search.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(10),
                AndroidUtilities.dp(14), AndroidUtilities.dp(10));
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(android.text.Editable s) {
                query = s != null ? s.toString().trim().toLowerCase() : "";
                renderEntries(filterEntries(), textColor, subColor, accentColor);
            }
        });
        root.addView(search, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

        statusView = new TextView(context);
        statusView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        statusView.setTextColor(sub);
        root.addView(statusView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

        ScrollView scroll = new ScrollView(context);
        itemsContainer = new LinearLayout(context);
        itemsContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(itemsContainer);
        root.addView(scroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        setCustomView(root);
        loadCatalog(text, sub, accent);
    }

    private void loadCatalog(final int text, final int sub, final int accent) {
        if (statusView != null) {
            statusView.setText("Завантаження каталогу…");
        }
        new Thread(() -> {
            final List<Entry> entries = fetchEntries();
            AndroidUtilities.runOnUIThread(() -> {
                allEntries.clear();
                allEntries.addAll(entries);
                renderEntries(filterEntries(), text, sub, accent);
            });
        }, "amod-catalog").start();
    }

    private List<Entry> filterEntries() {
        if (query == null || query.isEmpty()) return new ArrayList<>(allEntries);
        List<Entry> out = new ArrayList<>();
        for (Entry e : allEntries) {
            String t = (e.title + " " + e.id + " " + e.description).toLowerCase();
            if (t.contains(query)) out.add(e);
        }
        return out;
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
        if (statusView != null) {
            if (allEntries.isEmpty()) {
                statusView.setText("Каталог недоступний. Перевірте мережу.");
            } else if (entries.size() != allEntries.size()) {
                statusView.setText("Показано " + entries.size() + " з " + allEntries.size());
            } else {
                statusView.setText("Модулів: " + allEntries.size());
            }
        }
        if (entries.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText(allEntries.isEmpty()
                    ? "\u041a\u0430\u0442\u0430\u043b\u043e\u0433 \u043d\u0435\u0434\u043e\u0441\u0442\u0443\u043f\u043d\u0438\u0439. \u041f\u0435\u0440\u0435\u0432\u0456\u0440\u0442\u0435 \u043c\u0435\u0440\u0435\u0436\u0443."
                    : "Нічого не знайдено за запитом.");
            empty.setTextColor(sub);
            empty.setPadding(0, AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16));
            empty.setGravity(Gravity.CENTER);
            itemsContainer.addView(empty);
            // Швидкий вихід в єдиний Hot-магазин, щоб не було тупика.
            TextView goHot = app.amegram.theme.YumiComponents.pillButton(context, "У Hot-магазин →");
            goHot.setOnClickListener(v -> {
                try {
                    dismiss();
                } catch (Throwable ignore) {
                }
                try {
                    org.telegram.ui.ActionBar.BaseFragment last =
                            org.telegram.ui.LaunchActivity.getLastFragment();
                    if (last != null) {
                        last.presentFragment(new app.amegram.hot.ui.HotStoreActivity());
                    }
                } catch (Throwable ignore) {
                }
            });
            itemsContainer.addView(goHot);
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
        card.setBackground(app.amegram.theme.YumiTheme.cardBackground(16));
        card.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12),
                AndroidUtilities.dp(16), AndroidUtilities.dp(12));

        LinearLayout topRow = new LinearLayout(context);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView nameView = new TextView(context);
        nameView.setText(e.title + (e.version.isEmpty() ? "" : "  • v" + e.version));
        nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        nameView.setTypeface(AndroidUtilities.bold());
        nameView.setTextColor(text);
        nameView.setMaxLines(1);
        nameView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        topRow.addView(nameView, LayoutHelper.createLinear(
                0, LayoutHelper.WRAP_CONTENT, 1.0f));

        final TextView actionBtn = app.amegram.theme.YumiComponents.pillButton(context, "");
        if (e.downloadUrl.isEmpty()) {
            // 0 вбудованих: мертвої кнопки "Вбудовано" більше немає.
            // Ведемо в єдиний Hot-магазин, де модуль реально ставиться як .hmod.
            actionBtn.setText("У Hot-магазин →");
            actionBtn.setOnClickListener(v -> {
                try {
                    dismiss();
                } catch (Throwable ignore) {
                }
                // Єдиний магазин — повноекранний HotStoreActivity.
                try {
                    org.telegram.ui.ActionBar.BaseFragment last =
                            org.telegram.ui.LaunchActivity.getLastFragment();
                    if (last != null) {
                        last.presentFragment(new app.amegram.hot.ui.HotStoreActivity());
                    }
                } catch (Throwable ignore) {
                }
            });
        } else {
            actionBtn.setText("\u0412\u0441\u0442\u0430\u043d\u043e\u0432\u0438\u0442\u0438");
            actionBtn.setOnClickListener(v -> {
                actionBtn.setText("…");
                actionBtn.setEnabled(false);
                actionBtn.setAlpha(0.6f);
                ModuleManager.downloadAndInstall(e.downloadUrl, (ok, msg) -> {
                    actionBtn.setEnabled(true);
                    actionBtn.setAlpha(1f);
                    actionBtn.setText(ok ? "\u0412\u0441\u0442\u0430\u043d\u043e\u0432\u043b\u0435\u043d\u043e \u2713" : "\u041f\u043e\u043c\u0438\u043b\u043a\u0430");
                    if (!ok && msg != null && !msg.isEmpty()) {
                        try {
                            android.widget.Toast.makeText(context, msg,
                                    android.widget.Toast.LENGTH_SHORT).show();
                        } catch (Throwable ignore) {
                        }
                    }
                    if (ok && onChanged != null) {
                        onChanged.onChanged();
                    }
                });
            });
        }
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
