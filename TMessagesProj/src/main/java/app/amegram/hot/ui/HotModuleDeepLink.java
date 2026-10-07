package app.amegram.hot.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import org.telegram.messenger.AndroidUtilities;

import app.amegram.hot.HotCatalog;
import app.amegram.hot.HotModulesManager;
import app.miogram.bridge.MiogramLocale;

/**
 * Діплінки модулів: amegram://module/&lt;id&gt; — відкрити деталку,
 * amegram://modules — відкрити магазин. Схема amegram:// вже в маніфесті.
 */
public final class HotModuleDeepLink {

    private HotModuleDeepLink() {
    }

    public static String linkForModule(String moduleId) {
        return "amegram://module/" + moduleId;
    }

    public static String linkForStore() {
        return "amegram://modules";
    }

    /** Витягнути id модуля з amegram://module/&lt;id&gt; (інакше ""). */
    public static String parseModuleId(Uri uri) {
        if (uri == null) return "";
        if (!"amegram".equals(uri.getScheme())) return "";
        if (!"module".equals(uri.getHost())) {
            // Підтримка форм amegram://modules/... не потрібна, amegram://module?id=x — так.
            if ("modules".equals(uri.getHost())) return "";
            String ssp = uri.getSchemeSpecificPart();
            if (ssp != null && ssp.startsWith("module/")) {
                return ssp.substring("module/".length());
            }
            return uri.getQueryParameter("id") != null ? uri.getQueryParameter("id") : "";
        }
        String id = uri.getLastPathSegment();
        if (id == null || id.isEmpty()) {
            id = uri.getQueryParameter("id");
        }
        return id != null ? id : "";
    }

    public static boolean isStoreLink(Uri uri) {
        return uri != null && "amegram".equals(uri.getScheme()) && "modules".equals(uri.getHost());
    }

    /** Відкрити лінк: магазин або деталку модуля (з докачкою каталога). */
    public static void open(Context context, Uri uri, HotCatalogSheet.OnChanged onChanged) {
        if (context == null || uri == null) return;
        if (isStoreLink(uri)) {
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    new HotCatalogSheet(context, onChanged).show();
                } catch (Throwable ignore) {
                }
            });
            return;
        }
        String id = parseModuleId(uri);
        if (id.isEmpty()) return;
        final String moduleId = id;
        HotModulesManager.fetchCatalog(false, (ok, msg, catalog) -> {
            HotCatalog.Entry found = (ok && catalog != null) ? catalog.find(moduleId) : null;
            HotCatalog.Build sel = found != null ? found.defaultBuild() : null;
            try {
                new HotModuleDetailSheet(context, moduleId, found, sel, onChanged).show();
            } catch (Throwable ignore) {
            }
        });
    }

    /** Поділитись модулем: системний share з діплінком. */
    public static void share(Context context, String moduleId) {
        if (context == null || moduleId == null) return;
        try {
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT, linkForModule(moduleId));
            context.startActivity(Intent.createChooser(send, MiogramLocale.get(
                    "Поділитись модулем", "Поделиться модулем", "Share module")));
        } catch (Throwable ignore) {
        }
    }

    /** Скопіювати діплінк у буфер обміну. */
    public static void copyLink(Context context, String moduleId) {
        if (context == null || moduleId == null) return;
        try {
            ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("module", linkForModule(moduleId)));
                android.widget.Toast.makeText(context, linkForModule(moduleId),
                        android.widget.Toast.LENGTH_SHORT).show();
            }
        } catch (Throwable ignore) {
        }
    }
}
