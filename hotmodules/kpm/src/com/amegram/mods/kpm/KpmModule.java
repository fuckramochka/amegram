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

    private static String t(String uk, String ru, String en) {
        try {
            Class<?> locale = Class.forName("app.miogram.bridge.MiogramLocale");
            java.lang.reflect.Method get = locale.getMethod("get", String.class, String.class, String.class);
            Object value = get.invoke(null, uk, ru, en);
            if (value instanceof String) return (String) value;
        } catch (Throwable ignore) {
        }
        try {
            Class<?> lc = Class.forName("org.telegram.messenger.LocaleController");
            Object inst = lc.getMethod("getInstance").invoke(null);
            java.util.Locale current = (java.util.Locale) lc.getMethod("getCurrentLocale").invoke(inst);
            if (current != null) {
                String lang = current.getLanguage().toLowerCase();
                if (lang.startsWith("uk")) return uk;
                if (lang.startsWith("ru") || lang.startsWith("be") || lang.startsWith("kk")) return ru;
            }
        } catch (Throwable ignore) {
        }
        return uk != null ? uk : (en != null ? en : "");
    }

    @Override
    public boolean hasSettings() {
        return true;
    }

    @Override
    public String settingsTitle() {
        return t("Менеджер плагінів KPM", "Менеджер плагинов KPM", "KPM Plugins Manager");
    }

    @Override
    public void fillSettings(List<HotRow> rows) {
        if (rows == null) return;
        rows.add(HotRow.header(t("КАТАЛОГ ПЛАГІНІВ KPM", "КАТАЛОГ ПЛАГИНОВ KPM", "KPM PLUGINS CATALOG")));
        rows.add(HotRow.button("open_store",
                t("Відкрити каталог плагінів ✧", "Открыть каталог плагинов ✧", "Open Plugins Catalog ✧"),
                t("Офіційний каталог плагінів, пошук та встановлення в один дотик",
                  "Официальный каталог плагинов, поиск и установка в одно касание",
                  "Official plugins catalog, search and one-tap install")));

        int mirrorIdx = host != null ? host.getInt("kpm_mirror", 0) : 0;
        if (mirrorIdx < 0 || mirrorIdx >= MIRROR_NAMES.length) mirrorIdx = 0;
        rows.add(HotRow.choice("kpm_mirror",
                t("Дзеркало каталогу плагінів", "Зеркало каталога плагинов", "Plugin Catalog Mirror"),
                t("Активне сховище для завантаження каталогу та оновлень",
                  "Активное хранилище для загрузки каталога и обновлений",
                  "Active repository for catalog download and updates"),
                MIRROR_NAMES, mirrorIdx));

        boolean autoUpdate = host == null || host.getBool("kpm_auto_update", true);
        rows.add(HotRow.switchRow("kpm_auto_update",
                t("Автоматична перевірка оновлень", "Автоматическая проверка обновлений", "Automatic Update Check"),
                t("Автоматично перевіряти свіжі релізи встановлених плагінів",
                  "Автоматически проверять свежие релизы установленных плагинов",
                  "Automatically check for new releases of installed plugins"),
                autoUpdate));

        boolean audit = host == null || host.getBool("kpm_audit", true);
        rows.add(HotRow.switchRow("kpm_audit",
                t("Безпековий аудит", "Аудит безопасности", "Security Audit"),
                t("Перевірка цілісності SHA-256 та аналіз дозволів перед встановленням",
                  "Проверка целостности SHA-256 и анализ разрешений перед установкой",
                  "SHA-256 integrity check and permission analysis prior to installation"),
                audit));

        rows.add(HotRow.info(t("Модуль KPM інтегровано у нативну систему хот-модулів Yumigram.",
                "Модуль KPM интегрирован в нативную систему хот-модулей Yumigram.",
                "KPM module integrated into Yumigram hot-modules system.")));
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
            host.toast(t("Дзеркало каталогу: ", "Зеркало каталога: ", "Catalog mirror: ") + MIRROR_NAMES[Math.max(0, Math.min(index, MIRROR_NAMES.length - 1))]);
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
            Class<?> gate = Class.forName("app.amegram.hot.HotKpmGate");
            Object res = gate.getMethod("openStoreDirect", Object.class, Context.class).invoke(null, fragment, context);
            if (Boolean.TRUE.equals(res)) {
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
            Class<?> gate = Class.forName("app.amegram.hot.HotKpmGate");
            Object res = gate.getMethod("handleDeepLink", Object.class, String.class).invoke(null, fragment, url);
            if (Boolean.TRUE.equals(res)) {
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
                    if (host != null) host.toast(t("Плагін '" + pluginId + "' не знайдено в KPM Store", "Плагин '" + pluginId + "' не найден в KPM Store", "Plugin '" + pluginId + "' not found in KPM Store"));
                    return;
                }
                new AlertDialog.Builder(ctx)
                        .setTitle(t("Встановлення плагіна KPM", "Установка плагина KPM", "Install KPM Plugin"))
                        .setMessage(t("Встановити плагін \"", "Установить плагин \"", "Install plugin \"") + found.name + "\" v" + found.version + t(" від ", " от ", " by ") + found.author + "?\n\n" + found.description)
                        .setPositiveButton(t("Встановити", "Установить", "Install"), (dialog, which) -> {
                            new Thread(() -> {
                                boolean ok = KpmStoreDialog.downloadAndInstallPlugin(ctx, found, isSecurityAuditEnabled());
                                new Handler(Looper.getMainLooper()).post(() -> {
                                    if (host != null) {
                                        host.toast(ok ? (t("Плагін ", "Плагин ", "Plugin ") + found.name + t(" встановлено! ✧", " установлен! ✧", " installed! ✧")) : t("Помилка завантаження плагіна", "Ошибка загрузки плагина", "Plugin download error"));
                                    }
                                });
                            }).start();
                        })
                        .setNegativeButton(t("Скасувати", "Отмена", "Cancel"), null)
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
