package com.amegram.mods.kpm;

import android.app.AlertDialog;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import org.telegram.ui.ActionBar.BaseFragment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import app.amegram.hot.api.HotHost;
import app.amegram.hot.api.HotKpm;
import app.amegram.hot.api.HotModule;
import app.amegram.hot.api.HotRow;
import app.amegram.hot.api.HotServices;

/**
 * Офіційний нативний хот-модуль Kangel Plugins Manager (KPM) для Yumigram.
 * Надає доступ до каталогу KPM Store з 4 дзеркал (Kangel, GitHub, Codeberg, GitVerse),
 * безпечне 1-тап встановлення плагінів та обробку deep-link'ів (tg://kpm?install=...).
 */
public class KpmModule implements HotModule, HotKpm {

    public static final String[] MIRRORS = {
            "https://git.kangel.xyz/KangelPlugins/Plugins-Store/raw/branch/main/store.json",
            "https://raw.githubusercontent.com/Kangel-Plugins/Plugins-Store/main/store.json",
            "https://codeberg.org/Kangel/Plugins-Store/raw/branch/main/store.json",
            "https://gitverse.ru/api/repos/bigfishtheory/Plugins-Store/raw/branch/main/store.json"
    };

    public static final String[] MIRROR_NAMES = {
            "Kangel (git.kangel.xyz)",
            "GitHub Mirror",
            "Codeberg Mirror",
            "GitVerse Mirror"
    };

    private HotHost host;

    public KpmModule() {
    }

    @Override
    public String moduleId() {
        return "kpm";
    }

    @Override
    public void onAttach(Context appContext, HotHost host) {
        this.host = host;
        try {
            host.registerService(HotServices.KPM, this);
            host.log("kpm hotmodule attached with Kangel Plugins Store");
        } catch (Throwable ignore) {
        }
    }

    @Override
    public void onDetach() {
        try {
            if (host != null) {
                host.unregisterService(HotServices.KPM);
                host.log("kpm hotmodule detached");
            }
        } catch (Throwable ignore) {
        }
        this.host = null;
    }

    @Override
    public boolean hasSettings() {
        return true;
    }

    @Override
    public String settingsTitle() {
        return "Kangel Plugins Manager";
    }

    @Override
    public void fillSettings(List<HotRow> rows) {
        if (rows == null) return;
        rows.add(HotRow.header("KANGEL PLUGINS STORE"));
        rows.add(HotRow.button("open_store", "Відкрити KPM Store ✧",
                "Офіційний каталог плагінів, пошук та встановлення в 1 тап"));

        int mirrorIdx = host != null ? host.getInt("kpm_mirror", 0) : 0;
        if (mirrorIdx < 0 || mirrorIdx >= MIRROR_NAMES.length) mirrorIdx = 0;
        rows.add(HotRow.choice("kpm_mirror", "Дзеркало KPM Store",
                "Активне сховище для завантаження каталогу та оновлень", MIRROR_NAMES, mirrorIdx));

        boolean autoUpdate = host == null || host.getBool("kpm_auto_update", true);
        rows.add(HotRow.switchRow("kpm_auto_update", "Автоперевірка оновлень",
                "Автоматично перевіряти свіжі релізи встановлених плагінів", autoUpdate));

        boolean audit = host == null || host.getBool("kpm_audit", true);
        rows.add(HotRow.switchRow("kpm_audit", "Безпековий аудит",
                "Перевірка цілісності SHA-256 та аналіз дозволів перед встановленням", audit));

        rows.add(HotRow.info("Yumigram Native Engine: модуль KPM інтегровано у нативну систему хот-модулів."));
    }

    @Override
    public void onSettingsAction(String rowId) {
        if ("open_store".equals(rowId)) {
            openStore(null, host != null ? host.context() : null);
        }
    }

    @Override
    public void onSettingsChoice(String key, int index) {
        if ("kpm_mirror".equals(key) && host != null) {
            host.setInt(key, index);
            host.toast("Дзеркало KPM: " + MIRROR_NAMES[Math.max(0, Math.min(index, MIRROR_NAMES.length - 1))]);
        }
    }

    @Override
    public void onSettingsToggle(String key, boolean value) {
        if (host != null) {
            host.putBool(key, value);
        }
    }

    @Override
    public Object createScreen(String screenId) {
        return null;
    }

    @Override
    public void openStore(BaseFragment fragment, Context context) {
        try {
            if (app.amegram.hot.HotKpmGate.openStoreDirect(fragment, context)) {
                return;
            }
        } catch (Throwable ignore) {
        }
        Context ctx = context;
        if (ctx == null && fragment != null) {
            try {
                ctx = (Context) fragment.getClass().getMethod("getContext").invoke(fragment);
            } catch (Throwable ignore) {
            }
            if (ctx == null) {
                try {
                    ctx = (Context) fragment.getClass().getMethod("getParentActivity").invoke(fragment);
                } catch (Throwable ignore) {
                }
            }
        }
        if (ctx == null && host != null) {
            ctx = host.context();
        }
        if (ctx != null) {
            KpmStoreDialog.show(ctx, this);
        }
    }

    @Override
    public boolean handleDeepLink(BaseFragment fragment, String url) {
        if (url == null || url.isEmpty()) return false;
        try {
            if (app.amegram.hot.HotKpmGate.handleDeepLink(fragment, url)) {
                return true;
            }
        } catch (Throwable ignore) {
        }
        try {
            Uri uri = Uri.parse(url);
            String pluginId = null;
            if ("tg".equalsIgnoreCase(uri.getScheme()) && "kpm".equalsIgnoreCase(uri.getHost())) {
                pluginId = uri.getQueryParameter("install");
            } else if ("kpm".equalsIgnoreCase(uri.getScheme())) {
                pluginId = uri.getQueryParameter("install");
                if (pluginId == null && uri.getPath() != null) {
                    pluginId = uri.getPath().replace("/", "").trim();
                }
            } else if (url.contains("kpm?install=")) {
                int idx = url.indexOf("kpm?install=");
                pluginId = url.substring(idx + "kpm?install=".length()).split("&")[0];
            }

            if (pluginId != null && !pluginId.isEmpty()) {
                final String finalId = pluginId;
                new Thread(() -> resolveAndPromptInstall(fragment, finalId)).start();
                return true;
            }
        } catch (Throwable ignore) {
        }
        return false;
    }

    private void resolveAndPromptInstall(BaseFragment fragment, String pluginId) {
        try {
            KpmStoreDialog.KpmPlugin target = null;
            for (String mirrorUrl : MIRRORS) {
                List<KpmStoreDialog.KpmPlugin> list = KpmStoreDialog.fetchCatalogFromUrl(mirrorUrl);
                if (list != null && !list.isEmpty()) {
                    for (KpmStoreDialog.KpmPlugin p : list) {
                        if (pluginId.equalsIgnoreCase(p.id)) {
                            target = p;
                            break;
                        }
                    }
                    if (target != null) break;
                }
            }

            final KpmStoreDialog.KpmPlugin found = target;
            new Handler(Looper.getMainLooper()).post(() -> {
                Context foundCtx = null;
                if (fragment != null) {
                    try {
                        foundCtx = (Context) fragment.getClass().getMethod("getContext").invoke(fragment);
                    } catch (Throwable ignore) {
                    }
                    if (foundCtx == null) {
                        try {
                            foundCtx = (Context) fragment.getClass().getMethod("getParentActivity").invoke(fragment);
                        } catch (Throwable ignore) {
                        }
                    }
                }
                if (foundCtx == null && host != null) foundCtx = host.context();
                if (foundCtx == null) return;
                final Context ctx = foundCtx;

                if (found == null) {
                    if (host != null) host.toast("Плагін '" + pluginId + "' не знайдено в KPM Store");
                    return;
                }
                new AlertDialog.Builder(ctx)
                        .setTitle("Встановлення плагіна KPM")
                        .setMessage("Встановити плагін \"" + found.name + "\" v" + found.version + " від " + found.author + "?\n\n" + found.description)
                        .setPositiveButton("Встановити", (dialog, which) -> {
                            new Thread(() -> {
                                boolean ok = KpmStoreDialog.downloadAndInstallPlugin(ctx, found, isSecurityAuditEnabled());
                                new Handler(Looper.getMainLooper()).post(() -> {
                                    if (host != null) {
                                        host.toast(ok ? "Плагін " + found.name + " встановлено! ✧" : "Помилка завантаження плагіна");
                                    }
                                });
                            }).start();
                        })
                        .setNegativeButton("Скасувати", null)
                        .show();
            });
        } catch (Throwable e) {
            if (host != null) host.log("resolveAndPromptInstall failed: " + e.getMessage());
        }
    }

    @Override
    public boolean isSecurityAuditEnabled() {
        return host == null || host.getBool("kpm_audit", true);
    }

    @Override
    public String getActiveMirror() {
        int idx = host != null ? host.getInt("kpm_mirror", 0) : 0;
        if (idx < 0 || idx >= MIRRORS.length) idx = 0;
        return MIRRORS[idx];
    }

    public HotHost getHost() {
        return host;
    }
}
