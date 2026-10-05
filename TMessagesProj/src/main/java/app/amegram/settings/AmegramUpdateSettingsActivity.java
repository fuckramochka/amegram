package app.amegram.settings;

import android.content.Context;
import android.view.View;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.io.File;
import java.util.ArrayList;

import app.miogram.bridge.MiogramLocale;
import app.miogram.bridge.updater.MiogramDownloadManager;
import app.miogram.bridge.updater.MiogramUpdater;

/**
 * Экран автообновлений. Чистый порт логики MiogramUpdateSettingsActivity
 * без Neko-базы: BaseFragment + UniversalRecyclerView.
 */
public class AmegramUpdateSettingsActivity extends BaseFragment {

    private static final int ROW_VERSION = 1;
    private static final int ROW_CHECK = 2;
    private static final int ROW_BETA = 3;
    private static final int ROW_STABLE = 4;
    private static final int ROW_AUTOCHECK = 5;
    private static final int ROW_WIFIONLY = 6;
    private static final int ROW_INSTALL_CACHED = 7;
    private static final int ROW_DELETE_CACHED = 8;

    private UniversalRecyclerView listView;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(MiogramLocale.get("Автооновлення", "Автообновления", "Auto-updates"));
        actionBar.setActionBarMenuOnItemClick(new org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });
        fragmentView = listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, null);
        listView.setSections();
        return fragmentView;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(MiogramLocale.get("Статус", "Статус", "Status")));
        String ver;
        try {
            ver = BuildConfig.BUILD_VERSION_STRING + " (" + BuildConfig.BUILD_COMMIT_ID + ")";
        } catch (Throwable t) {
            ver = "?";
        }
        items.add(UItem.asSettingsCell(ROW_VERSION,
                MiogramLocale.get("Поточна версія", "Текущая версия", "Current version"), ver));
        items.add(UItem.asSettingsCell(ROW_CHECK,
                MiogramLocale.get("Перевірити зараз", "Проверить сейчас", "Check now"),
                MiogramUpdater.getLastCheckTimeFormatted()));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(MiogramLocale.get("Канал", "Канал", "Channel")));
        boolean beta = !MiogramUpdater.CHANNEL_STABLE.equals(MiogramUpdater.getUpdateChannel());
        items.add(UItem.asCheck(ROW_BETA, MiogramLocale.get("Бета", "Бета", "Beta")).setChecked(beta));
        items.add(UItem.asCheck(ROW_STABLE, "Stable").setChecked(!beta));
        items.add(UItem.asShadow(MiogramLocale.get(
                "Бета — нові фічі раніше, Stable — рідше і стабільніше.",
                "Бета — новые фичи раньше, Stable — реже и стабильнее.",
                "Beta gets features earlier, Stable is calmer.")));

        items.add(UItem.asHeader(MiogramLocale.get("Налаштування", "Настройки", "Preferences")));
        items.add(UItem.asCheck(ROW_AUTOCHECK,
                MiogramLocale.get("Автоперевірка", "Автопроверка", "Auto-check")).setChecked(MiogramUpdater.isAutoCheckEnabled()));
        items.add(UItem.asCheck(ROW_WIFIONLY,
                MiogramLocale.get("Тільки Wi-Fi", "Только Wi-Fi", "Wi-Fi only")).setChecked(MiogramUpdater.isWifiOnlyEnabled()));
        items.add(UItem.asShadow(null));

        Context ctx = getContext();
        File cached = ctx != null ? MiogramDownloadManager.getCachedApk(ctx, null) : null;
        if (cached != null && cached.exists() && cached.length() > 0) {
            items.add(UItem.asHeader(MiogramLocale.get("Кеш", "Кэш", "Cache")));
            items.add(UItem.asSettingsCell(ROW_INSTALL_CACHED,
                    MiogramLocale.get("Встановити з кешу", "Установить из кэша", "Install cached"),
                    AndroidUtilities.formatFileSize(cached.length())));
            items.add(UItem.asSettingsCell(ROW_DELETE_CACHED,
                    MiogramLocale.get("Видалити кеш", "Удалить кэш", "Delete cache"), ""));
            items.add(UItem.asShadow(null));
        }
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ROW_CHECK) {
            MiogramUpdater.checkAndShowUpdate(this, true);
            AndroidUtilities.runOnUIThread(() -> {
                if (listView != null && listView.adapter != null) listView.adapter.update(true);
            }, 1200);
        } else if (item.id == ROW_BETA) {
            MiogramUpdater.setUpdateChannel(MiogramUpdater.CHANNEL_BETA);
            listView.adapter.update(true);
        } else if (item.id == ROW_STABLE) {
            MiogramUpdater.setUpdateChannel(MiogramUpdater.CHANNEL_STABLE);
            listView.adapter.update(true);
        } else if (item.id == ROW_AUTOCHECK) {
            MiogramUpdater.setAutoCheckEnabled(!MiogramUpdater.isAutoCheckEnabled());
            listView.adapter.update(true);
        } else if (item.id == ROW_WIFIONLY) {
            MiogramUpdater.setWifiOnlyEnabled(!MiogramUpdater.isWifiOnlyEnabled());
            listView.adapter.update(true);
        } else if (item.id == ROW_INSTALL_CACHED) {
            Context ctx = getContext();
            File cached = ctx != null ? MiogramDownloadManager.getCachedApk(ctx, null) : null;
            if (cached != null) MiogramDownloadManager.promptInstall(ctx, cached);
        } else if (item.id == ROW_DELETE_CACHED) {
            Context ctx = getContext();
            File cached = ctx != null ? MiogramDownloadManager.getCachedApk(ctx, null) : null;
            if (cached != null && cached.exists()) cached.delete();
            if (ctx != null) Toast.makeText(ctx, MiogramLocale.get("Кеш видалено", "Кэш удалён", "Cache deleted"), Toast.LENGTH_SHORT).show();
            listView.adapter.update(true);
        }
    }

    @Override
    public boolean isLightStatusBar() {
        return !Theme.isCurrentThemeDark();
    }
}
