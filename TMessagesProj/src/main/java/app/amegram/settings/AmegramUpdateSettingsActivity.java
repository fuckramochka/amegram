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
    private static final int ROW_GITHUB_CHECK = 9;
    private static final int ROW_CHANNEL = 3;
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

        items.add(YumiSettingCell.Factory.of(ROW_GITHUB_CHECK, R.drawable.msg_download_solar, 0xFF7E57C2,
                MiogramLocale.get("Перевірити через GitHub-шит", "Проверить через GitHub-шит", "Check via GitHub sheet"),
                MiogramLocale.get("Той самий репозиторій, але з шитом версій і прогресом", "Тот же репозиторий, но с шитом версий и прогрессом", "Same repo, version sheet with progress")));

        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(MiogramLocale.get("Канал оновлень", "Канал обновлений", "Update channel")));
        boolean beta = !MiogramUpdater.CHANNEL_STABLE.equals(MiogramUpdater.getUpdateChannel());
        String curName = beta
                ? MiogramLocale.get("Бета (Beta)", "Бета (Beta)", "Beta")
                : MiogramLocale.get("Стабільний (Stable)", "Стабильный (Stable)", "Stable");
        // Одна кнопка замість двох радіо (текст налізав один на одного).
        items.add(YumiSettingCell.Factory.of(ROW_CHANNEL, R.drawable.msg_download_solar, 0xFF2196F3,
                MiogramLocale.get("Канал: " + curName, "Канал: " + curName, "Channel: " + curName),
                MiogramLocale.get("Натисни для вибору • Beta: раніше, можливі баги • Stable: рідше, вивірено",
                        "Нажми для выбора • Beta: раньше, возможны баги • Stable: реже, проверено",
                        "Tap to choose • Beta: earlier, may have bugs • Stable: calmer, tested")));

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
        } else if (item.id == ROW_GITHUB_CHECK) {
            try {
                app.exteraless.updater.GitHubUpdater.check(true);
            } catch (Throwable ignore) {
            }
            AndroidUtilities.runOnUIThread(() -> {
                if (listView != null && listView.adapter != null) listView.adapter.update(true);
            }, 1200);
        } else if (item.id == ROW_CHANNEL) {
            showChannelChooser();
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

    private void showChannelChooser() {
        try {
            android.content.Context ctx = getContext();
            if (ctx == null) return;
            boolean beta = !MiogramUpdater.CHANNEL_STABLE.equals(MiogramUpdater.getUpdateChannel());
            String[] items = new String[]{
                    MiogramLocale.get("Бета (Beta) — + раніше фічі, − можливі баги",
                            "Бета (Beta) — + раньше фичи, − возможны баги",
                            "Beta — + earlier features, − may have bugs"),
                    MiogramLocale.get("Стабільний (Stable) — + вивірено, − рідше оновлення",
                            "Стабильный (Stable) — + проверено, − реже обновления",
                            "Stable — + battle-tested, − calmer releases")
            };
            org.telegram.ui.ActionBar.AlertDialog.Builder b =
                    new org.telegram.ui.ActionBar.AlertDialog.Builder(ctx);
            b.setTitle(MiogramLocale.get("Канал оновлень", "Канал обновлений", "Update channel"));
            b.setItems(items, (d, which) -> {
                MiogramUpdater.setUpdateChannel(which == 0
                        ? MiogramUpdater.CHANNEL_BETA : MiogramUpdater.CHANNEL_STABLE);
                if (listView != null && listView.adapter != null) listView.adapter.update(true);
            });
            b.setNegativeButton(MiogramLocale.get("Скасувати", "Отмена", "Cancel"), null);
            b.show();
        } catch (Throwable ignore) {
            boolean cur = !MiogramUpdater.CHANNEL_STABLE.equals(MiogramUpdater.getUpdateChannel());
            MiogramUpdater.setUpdateChannel(cur ? MiogramUpdater.CHANNEL_STABLE : MiogramUpdater.CHANNEL_BETA);
            if (listView != null && listView.adapter != null) listView.adapter.update(true);
        }
    }

    @Override
    public boolean isLightStatusBar() {
        return !Theme.isCurrentThemeDark();
    }
}
