package app.amegram.settings;

import android.content.Context;
import android.view.View;

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

import app.amegram.theme.YumiSettingCell;
import app.amegram.theme.YumiTheme;
import app.miogram.bridge.MiogramLocale;
import app.miogram.bridge.updater.MiogramDownloadManager;
import app.miogram.bridge.updater.MiogramUpdater;

/**
 * Экран автообновлений Yumigram в стиле Material Design 3.
 * Взаимоисключающий выбор каналов (Radio), карточки статуса и настроек.
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
        items.add(UItem.asHeader(MiogramLocale.get("Статус клієнта", "Статус клиента", "Client status")));
        String ver;
        try {
            ver = BuildConfig.BUILD_VERSION_STRING + " (" + BuildConfig.BUILD_COMMIT_ID + ")";
        } catch (Throwable t) {
            ver = "1.0.0";
        }

        items.add(YumiSettingCell.Factory.of(ROW_VERSION, R.drawable.msg_download_solar, YumiTheme.getPrimary(),
                "Yumigram v" + ver,
                MiogramLocale.get("Остання перевірка: ", "Последняя проверка: ", "Last check: ") + MiogramUpdater.getLastCheckTimeFormatted()));

        items.add(YumiSettingCell.Factory.of(ROW_CHECK, R.drawable.msg_retry, 0xFF2A87FF,
                MiogramLocale.get("Перевірити наявність оновлень", "Проверить наличие обновлений", "Check for updates"),
                MiogramLocale.get("Запит свіжих релізів з GitHub", "Запрос свежих релизов с GitHub", "Query latest releases from GitHub")));

        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(MiogramLocale.get("Канал оновлень", "Канал обновлений", "Update channel")));
        boolean beta = !MiogramUpdater.CHANNEL_STABLE.equals(MiogramUpdater.getUpdateChannel());

        // Взаимоисключающий выбор канала через asRadio2 / asRadio
        items.add(UItem.asRadio(ROW_BETA,
                MiogramLocale.get("Бета-канал (Beta)", "Бета-канал (Beta)", "Beta channel"),
                MiogramLocale.get("Нові функції та покращення раніше", "Новые функции и улучшения раньше", "Get fresh features earlier")).setChecked(beta));

        items.add(UItem.asRadio(ROW_STABLE,
                MiogramLocale.get("Стабільний канал (Stable)", "Стабильный канал (Stable)", "Stable channel"),
                MiogramLocale.get("Рідші та максимально вивірені збірки", "Более редкие и стабильные сборки", "Calmer and battle-tested releases")).setChecked(!beta));

        items.add(UItem.asShadow(MiogramLocale.get(
                "Канал визначає, які версії завантажуватимуться автоматично.",
                "Канал определяет, какие версии будут загружаться автоматически.",
                "Channel selects which versions will be fetched automatically.")));

        items.add(UItem.asHeader(MiogramLocale.get("Параметри", "Параметры", "Preferences")));
        items.add(UItem.asCheck(ROW_AUTOCHECK,
                MiogramLocale.get("Фонова автоперевірка", "Фоновая автопроверка", "Background auto-check")).setChecked(MiogramUpdater.isAutoCheckEnabled()));
        items.add(UItem.asCheck(ROW_WIFIONLY,
                MiogramLocale.get("Завантажувати лише через Wi-Fi", "Загружать только по Wi-Fi", "Download on Wi-Fi only")).setChecked(MiogramUpdater.isWifiOnlyEnabled()));
        items.add(UItem.asShadow(null));

        Context ctx = getContext();
        File cached = ctx != null ? MiogramDownloadManager.getCachedApk(ctx, null) : null;
        if (cached != null && cached.exists() && cached.length() > 0) {
            items.add(UItem.asHeader(MiogramLocale.get("Завантажений файл", "Загруженный файл", "Cached APK")));
            items.add(YumiSettingCell.Factory.of(ROW_INSTALL_CACHED, R.drawable.msg_download_solar, 0xFF4CAF50,
                    MiogramLocale.get("Встановити оновлення", "Установить обновление", "Install update"),
                    AndroidUtilities.formatFileSize(cached.length())));
            items.add(YumiSettingCell.Factory.of(ROW_DELETE_CACHED, R.drawable.msg_delete, YumiTheme.getError(),
                    MiogramLocale.get("Видалити інсталятор", "Удалить установщик", "Delete installer"),
                    MiogramLocale.get("Звільнити місце на пристрої", "Освободить место на устройстве", "Free up disk space")));
            items.add(UItem.asShadow(null));
        }
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ROW_CHECK || item.id == ROW_VERSION) {
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
            if (cached != null) cached.delete();
            listView.adapter.update(true);
        }
    }

    @Override
    public boolean isLightStatusBar() {
        return !Theme.isCurrentThemeDark();
    }
}
