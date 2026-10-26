package com.amegram.mods.kpm;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class KpmStoreDialog {

    public static class KpmPlugin {
        public String id;
        public String name;
        public String version;
        public String author;
        public String description;
        public String url;
        public String category;
        public String hash;

        public String getFileName() {
            if (url != null && url.contains("/")) {
                String last = url.substring(url.lastIndexOf("/") + 1);
                int q = last.indexOf("?");
                if (q >= 0) last = last.substring(0, q);
                if (!TextUtils.isEmpty(last)) return last;
            }
            return (id != null ? id : "plugin") + ".plugin";
        }
    }

    public static void show(Context context, KpmModule module) {
        if (context == null) return;
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                Dialog dialog = new Dialog(context);
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

                Window w = dialog.getWindow();
                if (w != null) {
                    w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                    w.setGravity(Gravity.BOTTOM);
                    w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
                }

                View view = createDialogView(context, dialog, module);
                dialog.setContentView(view);
                dialog.show();
            } catch (Throwable e) {
                if (module != null && module.getHost() != null) {
                    module.getHost().log("KpmStoreDialog show error: " + e.getMessage());
                }
            }
        });
    }

    private static View createDialogView(Context context, Dialog dialog, KpmModule module) {
        int maxHeight = (int) (context.getResources().getDisplayMetrics().heightPixels * 0.85f);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);

        GradientDrawable rootBg = new GradientDrawable();
        rootBg.setColor(0xFF14141E);
        rootBg.setCornerRadii(new float[]{dp(context, 20), dp(context, 20), dp(context, 20), dp(context, 20), 0, 0, 0, 0});
        root.setBackground(rootBg);

        // Drag handle
        View handle = new View(context);
        GradientDrawable handleBg = new GradientDrawable();
        handleBg.setColor(0x44888899);
        handleBg.setCornerRadius(dp(context, 3));
        handle.setBackground(handleBg);
        LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(dp(context, 36), dp(context, 4));
        handleLp.gravity = Gravity.CENTER_HORIZONTAL;
        handleLp.topMargin = dp(context, 10);
        handleLp.bottomMargin = dp(context, 6);
        root.addView(handle, handleLp);

        // Header
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(context, 16), dp(context, 4), dp(context, 16), dp(context, 8));

        LinearLayout titleBox = new LinearLayout(context);
        titleBox.setOrientation(LinearLayout.VERTICAL);

        TextView titleText = new TextView(context);
        titleText.setText("Kangel Plugins Store ໒꒱");
        titleText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 19);
        titleText.setTextColor(0xFFFFFFFF);
        titleText.getPaint().setFakeBoldText(true);
        titleBox.addView(titleText);

        TextView subtitleText = new TextView(context);
        subtitleText.setText("Офіційне сховище плагінів KPM");
        subtitleText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        subtitleText.setTextColor(0xFF8888AA);
        titleBox.addView(subtitleText);

        header.addView(titleBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        TextView closeBtn = new TextView(context);
        closeBtn.setText("✕");
        closeBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        closeBtn.setTextColor(0xFF8888AA);
        closeBtn.setPadding(dp(context, 8), dp(context, 4), dp(context, 8), dp(context, 4));
        closeBtn.setOnClickListener(v -> dialog.dismiss());
        header.addView(closeBtn);

        root.addView(header);

        // Search
        EditText search = new EditText(context);
        search.setHint("Пошук серед плагінів KPM…");
        search.setHintTextColor(0xFF777788);
        search.setTextColor(0xFFFFFFFF);
        search.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        search.setSingleLine(true);
        search.setPadding(dp(context, 14), dp(context, 9), dp(context, 14), dp(context, 9));

        GradientDrawable searchBg = new GradientDrawable();
        searchBg.setColor(0x18888888);
        searchBg.setCornerRadius(dp(context, 12));
        search.setBackground(searchBg);

        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        searchLp.setMargins(dp(context, 16), dp(context, 4), dp(context, 16), dp(context, 6));
        root.addView(search, searchLp);

        // Categories
        HorizontalScrollView catScroll = new HorizontalScrollView(context);
        catScroll.setHorizontalScrollBarEnabled(false);
        catScroll.setPadding(dp(context, 16), dp(context, 2), dp(context, 16), dp(context, 6));

        LinearLayout catLayout = new LinearLayout(context);
        catLayout.setOrientation(LinearLayout.HORIZONTAL);
        catScroll.addView(catLayout);
        root.addView(catScroll);

        // Content Area
        FrameLayout contentArea = new FrameLayout(context);
        LinearLayout.LayoutParams caLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxHeight);
        root.addView(contentArea, caLp);

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        contentArea.addView(scrollView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout itemsContainer = new LinearLayout(context);
        itemsContainer.setOrientation(LinearLayout.VERTICAL);
        itemsContainer.setPadding(dp(context, 16), dp(context, 4), dp(context, 16), dp(context, 24));
        scrollView.addView(itemsContainer);

        ProgressBar loading = new ProgressBar(context);
        FrameLayout.LayoutParams pbLp = new FrameLayout.LayoutParams(dp(context, 36), dp(context, 36), Gravity.CENTER);
        contentArea.addView(loading, pbLp);

        TextView emptyView = new TextView(context);
        emptyView.setText("Плагінів не знайдено");
        emptyView.setTextColor(0xFF8888AA);
        emptyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        emptyView.setVisibility(View.GONE);
        FrameLayout.LayoutParams emptyLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        contentArea.addView(emptyView, emptyLp);

        List<KpmPlugin> allList = new ArrayList<>();
        List<KpmPlugin> filteredList = new ArrayList<>();
        final String[] activeCat = new String[]{"ALL"};
        final String[] currentQuery = new String[]{""};

        Runnable updateFilter = () -> {
            filteredList.clear();
            String q = currentQuery[0].toLowerCase(Locale.ROOT);
            for (KpmPlugin p : allList) {
                boolean matchesCat = "ALL".equalsIgnoreCase(activeCat[0])
                        || (p.category != null && p.category.equalsIgnoreCase(activeCat[0]));
                boolean matchesSearch = q.isEmpty()
                        || (p.name != null && p.name.toLowerCase(Locale.ROOT).contains(q))
                        || (p.id != null && p.id.toLowerCase(Locale.ROOT).contains(q))
                        || (p.author != null && p.author.toLowerCase(Locale.ROOT).contains(q))
                        || (p.description != null && p.description.toLowerCase(Locale.ROOT).contains(q));
                if (matchesCat && matchesSearch) {
                    filteredList.add(p);
                }
            }

            itemsContainer.removeAllViews();
            if (filteredList.isEmpty()) {
                emptyView.setVisibility(View.VISIBLE);
            } else {
                emptyView.setVisibility(View.GONE);
                File pluginsDir = getPluginsDirectory(context);
                for (KpmPlugin p : filteredList) {
                    boolean installed = isPluginInstalled(pluginsDir, p);
                    itemsContainer.addView(createCard(context, p, installed, module));
                }
            }
        };

        // Populate Categories
        String[] cats = {"ALL", "utilities", "customization", "media", "tools", "text", "other"};
        String[] catTitles = {"Всі", "Утиліти", "Інтерфейс", "Медіа", "Інструменти", "Текст", "Інше"};

        Runnable rebuildChips = new Runnable() {
            @Override
            public void run() {
                catLayout.removeAllViews();
                for (int i = 0; i < cats.length; i++) {
                    final String catKey = cats[i];
                    TextView chip = new TextView(context);
                    chip.setText(catTitles[i]);
                    chip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
                    chip.setPadding(dp(context, 10), dp(context, 5), dp(context, 10), dp(context, 5));
                    chip.setGravity(Gravity.CENTER);

                    boolean active = activeCat[0].equalsIgnoreCase(catKey);
                    GradientDrawable chipBg = new GradientDrawable();
                    chipBg.setCornerRadius(dp(context, 10));
                    if (active) {
                        chipBg.setColor(0xFF7000FF);
                        chip.setTextColor(0xFFFFFFFF);
                    } else {
                        chipBg.setColor(0x18888888);
                        chip.setTextColor(0xFFDDDDDD);
                    }
                    chip.setBackground(chipBg);

                    chip.setOnClickListener(v -> {
                        activeCat[0] = catKey;
                        this.run();
                        updateFilter.run();
                    });

                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    lp.rightMargin = dp(context, 6);
                    catLayout.addView(chip, lp);
                }
            }
        };
        rebuildChips.run();

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentQuery[0] = s != null ? s.toString().trim() : "";
                updateFilter.run();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Load catalog
        new Thread(() -> {
            String mirror = module != null ? module.getActiveMirror() : KpmModule.MIRRORS[0];
            List<KpmPlugin> list = fetchCatalogFromUrl(mirror);
            if (list == null || list.isEmpty()) {
                for (String m : KpmModule.MIRRORS) {
                    if (!m.equals(mirror)) {
                        list = fetchCatalogFromUrl(m);
                        if (list != null && !list.isEmpty()) break;
                    }
                }
            }
            final List<KpmPlugin> finalRes = list != null ? list : Collections.emptyList();
            new Handler(Looper.getMainLooper()).post(() -> {
                loading.setVisibility(View.GONE);
                allList.clear();
                allList.addAll(finalRes);
                updateFilter.run();
            });
        }).start();

        return root;
    }

    private static View createCard(Context context, KpmPlugin plugin, boolean installed, KpmModule module) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF1E1E2C);
        bg.setCornerRadius(dp(context, 14));
        bg.setStroke(dp(context, 1), 0x22888888);
        card.setBackground(bg);

        LinearLayout topRow = new LinearLayout(context);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView name = new TextView(context);
        name.setText(plugin.name != null ? plugin.name : plugin.id);
        name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        name.setTextColor(0xFFFFFFFF);
        name.getPaint().setFakeBoldText(true);
        topRow.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        if (!TextUtils.isEmpty(plugin.version)) {
            TextView ver = new TextView(context);
            ver.setText("v" + plugin.version);
            ver.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10);
            ver.setTextColor(0xFF00D2FF);
            ver.setPadding(dp(context, 6), dp(context, 2), dp(context, 6), dp(context, 2));

            GradientDrawable verBg = new GradientDrawable();
            verBg.setColor(0x2200D2FF);
            verBg.setCornerRadius(dp(context, 6));
            ver.setBackground(verBg);
            topRow.addView(ver);
        }
        card.addView(topRow);

        if (!TextUtils.isEmpty(plugin.author)) {
            TextView author = new TextView(context);
            author.setText("від " + plugin.author);
            author.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
            author.setTextColor(0xFF8888AA);
            author.setPadding(0, dp(context, 2), 0, dp(context, 4));
            card.addView(author);
        }

        if (!TextUtils.isEmpty(plugin.description)) {
            TextView desc = new TextView(context);
            desc.setText(plugin.description);
            desc.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            desc.setTextColor(0xFFB0B0C0);
            desc.setMaxLines(3);
            desc.setEllipsize(TextUtils.TruncateAt.END);
            desc.setPadding(0, dp(context, 2), 0, dp(context, 8));
            card.addView(desc);
        }

        TextView btn = new TextView(context);
        btn.setText(installed ? "Встановлено ✓" : "Встановити ⤓");
        btn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(context, 14), dp(context, 6), dp(context, 14), dp(context, 6));

        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setCornerRadius(dp(context, 8));
        if (installed) {
            btnBg.setColor(0x2200E676);
            btn.setTextColor(0xFF00E676);
        } else {
            btnBg.setColor(0xFF7000FF);
            btn.setTextColor(0xFFFFFFFF);
        }
        btn.setBackground(btnBg);

        btn.setOnClickListener(v -> {
            btn.setEnabled(false);
            btn.setText("Завантаження…");
            new Thread(() -> {
                boolean ok = downloadAndInstallPlugin(context, plugin, module != null && module.isSecurityAuditEnabled());
                new Handler(Looper.getMainLooper()).post(() -> {
                    btn.setEnabled(true);
                    if (ok) {
                        btn.setText("Встановлено ✓");
                        GradientDrawable sBg = new GradientDrawable();
                        sBg.setCornerRadius(dp(context, 8));
                        sBg.setColor(0x2200E676);
                        btn.setBackground(sBg);
                        btn.setTextColor(0xFF00E676);
                        if (module != null && module.getHost() != null) {
                            module.getHost().toast("Плагін " + plugin.name + " встановлено! ✧");
                        }
                    } else {
                        btn.setText("Помилка ✕");
                    }
                });
            }).start();
        });

        card.addView(btn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = dp(context, 10);
        card.setLayoutParams(cardLp);
        return card;
    }

    public static List<KpmPlugin> fetchCatalogFromUrl(String catalogUrl) {
        HttpURLConnection conn = null;
        try {
            URL u = new URL(catalogUrl);
            conn = (HttpURLConnection) u.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(10000);
            conn.setRequestMethod("GET");
            conn.connect();

            if (conn.getResponseCode() != 200) return null;

            InputStream is = conn.getInputStream();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int r;
            while ((r = is.read(buf)) != -1) {
                baos.write(buf, 0, r);
            }
            is.close();

            String json = baos.toString("UTF-8");
            JSONObject root = new JSONObject(json);
            List<KpmPlugin> list = new ArrayList<>();

            Iterator<String> keys = root.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONObject obj = root.optJSONObject(key);
                if (obj == null) continue;

                KpmPlugin p = new KpmPlugin();
                p.id = key;
                p.name = obj.optString("name", key);
                p.version = obj.optString("version", "1.0.0");
                p.author = obj.optString("author", "");
                p.description = obj.optString("description", "");
                p.url = obj.optString("url", "");
                p.category = obj.optString("status", "utilities");
                p.hash = obj.optString("hash", "");

                if (!TextUtils.isEmpty(p.url)) {
                    list.add(p);
                }
            }
            return list;
        } catch (Throwable ignore) {
            return null;
        } finally {
            if (conn != null) {
                try { conn.disconnect(); } catch (Throwable ignored) {}
            }
        }
    }

    public static boolean downloadAndInstallPlugin(Context context, KpmPlugin plugin, boolean audit) {
        if (plugin == null || TextUtils.isEmpty(plugin.url)) return false;
        HttpURLConnection conn = null;
        try {
            File pluginsDir = getPluginsDirectory(context);
            if (!pluginsDir.exists()) pluginsDir.mkdirs();

            URL u = new URL(plugin.url);
            conn = (HttpURLConnection) u.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(20000);
            conn.setRequestMethod("GET");
            conn.connect();

            if (conn.getResponseCode() != 200) return false;

            File tempFile = new File(context.getCacheDir(), plugin.getFileName());
            InputStream is = conn.getInputStream();
            FileOutputStream fos = new FileOutputStream(tempFile);
            byte[] buf = new byte[8192];
            int len;
            while ((len = is.read(buf)) != -1) {
                fos.write(buf, 0, len);
            }
            fos.flush();
            fos.close();
            is.close();

            if (audit && !TextUtils.isEmpty(plugin.hash)) {
                String calcHash = sha256(tempFile);
                if (!plugin.hash.equalsIgnoreCase(calcHash)) {
                    tempFile.delete();
                    return false;
                }
            }

            File targetFile = new File(pluginsDir, plugin.getFileName());
            copyFile(tempFile, targetFile);
            tempFile.delete();

            rescanPlugins();
            return true;
        } catch (Throwable ignore) {
            return false;
        } finally {
            if (conn != null) {
                try { conn.disconnect(); } catch (Throwable ignored) {}
            }
        }
    }

    private static void copyFile(File src, File dst) throws Exception {
        try (InputStream in = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
            out.flush();
        }
    }

    private static String sha256(File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream is = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            byte[] hash = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public static File getPluginsDirectory(Context context) {
        try {
            Class<?> clazz = Class.forName("app.exteraless.plugins.PluginsController");
            Object instance = clazz.getMethod("getInstance").invoke(null);
            if (instance != null) {
                Object dir = clazz.getMethod("getPluginsDir").invoke(instance);
                if (dir instanceof File) return (File) dir;
            }
        } catch (Throwable ignore) {}
        return new File(context.getFilesDir(), "plugins");
    }

    public static void rescanPlugins() {
        try {
            Class<?> clazz = Class.forName("app.exteraless.plugins.PluginsController");
            Object instance = clazz.getMethod("getInstance").invoke(null);
            if (instance != null) {
                clazz.getMethod("rescanPlugins").invoke(instance);
            }
        } catch (Throwable ignore) {}
    }

    private static boolean isPluginInstalled(File pluginsDir, KpmPlugin plugin) {
        if (pluginsDir == null || !pluginsDir.exists() || plugin == null) return false;
        File target = new File(pluginsDir, plugin.getFileName());
        if (target.exists()) return true;
        if (new File(pluginsDir, plugin.id + ".plugin").exists()) return true;
        if (new File(pluginsDir, plugin.id + ".eaf").exists()) return true;
        if (new File(pluginsDir, plugin.id + ".elyx").exists()) return true;
        if (new File(pluginsDir, plugin.id + ".py").exists()) return true;
        return false;
    }

    private static int dp(Context context, float value) {
        try {
            Class<?> au = Class.forName("org.telegram.messenger.AndroidUtilities");
            return (int) au.getMethod("dp", float.class).invoke(null, value);
        } catch (Throwable e) {
            return (int) Math.ceil(value * context.getResources().getDisplayMetrics().density);
        }
    }
}
