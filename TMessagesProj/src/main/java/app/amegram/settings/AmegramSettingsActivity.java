package app.amegram.settings;

import android.content.Context;
import android.view.View;

import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import app.amegram.hot.HotModulesManager;
import app.amegram.hot.ui.HotModuleSettingsActivity;
import app.amegram.hot.ui.HotModulesActivity;
import app.amegram.theme.YumiSettingCell;
import app.amegram.theme.YumiTheme;
import app.exteraless.plugins.ui.PluginsActivity;
import app.miogram.bridge.MiogramLocale;
import app.miogram.bridge.settings.MiogramAboutActivity;

/**
 * Главный экран настроек Yumigram («Юмиграм»).
 * Построен полностью на MD3-компонентах YumiTheme и YumiSettingCell:
 * единые скругления, тональные поверхности, без наложения текста.
 */
public class AmegramSettingsActivity extends BaseFragment implements HotModulesManager.ModulesChangeListener {

    private static final int ROW_GHOST = 1;
    private static final int ROW_VAULT = 2;
    private static final int ROW_PLAYER = 3;
    private static final int ROW_AME = 4;
    private static final int ROW_TIKTOK = 5;
    private static final int ROW_AI = 6;
    private static final int ROW_EXPERIMENTAL = 7;

    private static final int ROW_HOTMODULES = 20;
    private static final int ROW_PLUGINS = 21;
    private static final int ROW_GUIDE = 22;

    private static final int ROW_BADGES = 30;
    private static final int ROW_UPDATES = 31;
    private static final int ROW_ABOUT = 32;

    private static final Set<String> BUILTIN_IDS = new HashSet<>(Arrays.asList(
            "ghost", "vault", "player", "ame", "tiktok", "ai", "experimental", "stt", "demo", "automation"
    ));

    private UniversalRecyclerView listView;
    private final List<HotModulesManager.Handle> customActiveModules = new ArrayList<>();

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(MiogramLocale.get("Юміграм", "Юмиграм", "Yumigram"));
        actionBar.setActionBarMenuOnItemClick(new org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        HotModulesManager.init(context.getApplicationContext());
        HotModulesManager.addListener(this);

        fragmentView = listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, null);
        listView.setSections();
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        HotModulesManager.removeListener(this);
    }

    @Override
    public void onModulesChanged() {
        refresh();
    }

    private void refresh() {
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        customActiveModules.clear();

        // 1. Головні функції клієнта — тільки увімкнені модулі. Вимкнений = зник з налаштувань.
        items.add(UItem.asHeader(MiogramLocale.get("Можливості Yumigram", "Возможности Yumigram", "Yumigram features")));

        boolean ghostOn = HotModulesManager.isModuleEnabled("ghost");
        if (ghostOn) {
        items.add(YumiSettingCell.Factory.of(ROW_GHOST, R.drawable.msg_secret, 0xFF9C27B0,
                MiogramLocale.get("Режим привида", "Режим призрака", "Ghost mode"),
                ghostOn ? MiogramLocale.get("Увімкнено • Скритність онлайну та прочитання",
                                "Включено • Скрытность онлайна и прочтения",
                                "Active • Hide online, read receipts & typing")
                        : MiogramLocale.get("Вимкнено • Натисніть для налаштування",
                                "Выключено • Нажмите для настройки",
                                "Disabled • Tap to configure")));
        }

        boolean expOn = HotModulesManager.isModuleEnabled("experimental");
        if (expOn) {
        items.add(YumiSettingCell.Factory.of(ROW_EXPERIMENTAL, R.drawable.msg_fave, 0xFF43A047,
                MiogramLocale.get("Експерименти та видалені", "Эксперименты и удалённые", "Experiments & Deleted"),
                expOn ? MiogramLocale.get("Увімкнено • Збереження повідомлень, безліміти",
                                "Включено • Сохранение сообщений, безлимиты",
                                "Active • Save deleted messages, unlimited pins")
                      : MiogramLocale.get("Вимкнено • Натисніть для налаштування",
                                "Выключено • Нажмите для настройки",
                                "Disabled • Tap to configure")));
        }

        boolean vaultOn = HotModulesManager.isModuleEnabled("vault");
        if (vaultOn) {
        items.add(YumiSettingCell.Factory.of(ROW_VAULT, R.drawable.msg_saved, 0xFFE5486B,
                MiogramLocale.get("Хмарне сховище", "Облачное хранилище", "Cloud vault"),
                vaultOn ? MiogramLocale.get("Увімкнено • AES-256 диск, шифрування",
                                "Включено • AES-256 диск, шифрование",
                                "Active • AES-256 virtual disk, encryption")
                        : MiogramLocale.get("Вимкнено • Натисніть для налаштування",
                                "Выключено • Нажмите для настройки",
                                "Disabled • Tap to configure")));
        }

        boolean playerOn = HotModulesManager.isModuleEnabled("player");
        if (playerOn) {
        items.add(YumiSettingCell.Factory.of(ROW_PLAYER, R.drawable.baseline_music_note_24, 0xFF00ACC1,
                MiogramLocale.get("Музичний плеєр", "Музыкальный плеер", "Music player"),
                playerOn ? MiogramLocale.get("Увімкнено • Тексти LRC, візуалізатор, пошук",
                                 "Включено • Тексты LRC, визуализатор, поиск",
                                 "Active • LRC lyrics, visualizer, search")
                         : MiogramLocale.get("Вимкнено • Натисніть для налаштування",
                                 "Выключено • Нажмите для настройки",
                                 "Disabled • Tap to configure")));
        }

        boolean aiOn = HotModulesManager.isModuleEnabled("ai");
        if (aiOn) {
        items.add(YumiSettingCell.Factory.of(ROW_AI, R.drawable.baseline_stars_24, 0xFF8B5CF6,
                MiogramLocale.get("Штучний інтелект (ШІ)", "Искусственный интеллект (ИИ)", "Artificial Intelligence (AI)"),
                aiOn ? MiogramLocale.get("Увімкнено • Асистент Ame / KAngel, STT",
                               "Включено • Ассистент Ame / KAngel, STT",
                               "Active • Companion Ame / KAngel, STT")
                     : MiogramLocale.get("Вимкнено • Натисніть для налаштування",
                               "Выключено • Нажмите для настройки",
                               "Disabled • Tap to configure")));
        }

        boolean tikOn = HotModulesManager.isModuleEnabled("tiktok");
        if (tikOn) {
        items.add(YumiSettingCell.Factory.of(ROW_TIKTOK, R.drawable.msg_video, 0xFFEE1D52,
                "TikTok MI",
                tikOn ? MiogramLocale.get("Увімкнено • Відео без ватермарок, прямі лінки",
                              "Включено • Видео без водяных знаков, прямые ссылки",
                              "Active • Watermark-free videos, clean links")
                      : MiogramLocale.get("Вимкнено • Натисніть для налаштування",
                              "Выключено • Нажмите для настройки",
                              "Disabled • Tap to configure")));
        }

        boolean ameOn = HotModulesManager.isModuleEnabled("ame");
        if (ameOn) {
        items.add(YumiSettingCell.Factory.of(ROW_AME, R.drawable.msg_customize, 0xFFE91E63,
                MiogramLocale.get("Кастомізація UI", "Кастомизация UI", "Customization"),
                ameOn ? MiogramLocale.get("Увімкнено • XML-картки профілю, діалоги, blur",
                              "Включено • XML-карточки профиля, диалоги, blur",
                              "Active • Profile XML cards, dialogs, blur")
                      : MiogramLocale.get("Вимкнено • Натисніть для налаштування",
                              "Выключено • Нажмите для настройки",
                              "Disabled • Tap to configure")));
        }

        // Сторонні завантажені модулі
        List<HotModulesManager.Handle> allHandles = HotModulesManager.settingsHandles();
        for (HotModulesManager.Handle h : allHandles) {
            if (h != null && h.info != null && h.info.manifest != null) {
                if (!BUILTIN_IDS.contains(h.info.manifest.id)) {
                    customActiveModules.add(h);
                }
            }
        }
        for (int i = 0; i < customActiveModules.size(); i++) {
            HotModulesManager.Handle h = customActiveModules.get(i);
            String title = h.instance != null ? h.instance.settingsTitle() : null;
            if (title == null || title.isEmpty()) title = h.info.manifest.name;
            items.add(YumiSettingCell.Factory.of(100 + i, R.drawable.msg_plugins, 0xFF2A87FF, title, h.info.manifest.name));
        }

        items.add(UItem.asShadow(null));

        // 2. Керування модулями та розширеннями (головна точка входу)
        items.add(UItem.asHeader(MiogramLocale.get("Модулі та розширення", "Модули и расширения", "Modules & Extensions")));

        int enabledCount = HotModulesManager.countEnabledModules();
        String hotSubtitle = enabledCount > 0
                ? MiogramLocale.get("Увімкнено: " + enabledCount + " • Каталог та версії",
                        "Включено: " + enabledCount + " • Каталог и версии",
                        "Active: " + enabledCount + " • Catalog & versions")
                : MiogramLocale.get("Каталог розширень • 0 активних (чистий старт)",
                        "Каталог расширений • 0 активных (чистый старт)",
                        "Extension catalog • 0 active (clean start)");

        items.add(YumiSettingCell.Factory.of(ROW_HOTMODULES, R.drawable.msg_plugins, 0xFF8B5CF6,
                MiogramLocale.get("Хот-модулі (.hmod)", "Хот-модули (.hmod)", "Hot modules (.hmod)"),
                hotSubtitle));

        items.add(YumiSettingCell.Factory.of(ROW_PLUGINS, R.drawable.msg_bot, 0xFF3F51B5,
                MiogramLocale.get("Плагіни", "Плагины", "Plugins"),
                MiogramLocale.get("Python + Lua + Java скрипти", "Python + Lua + Java скрипты", "Python + Lua + Java scripts")));

        items.add(YumiSettingCell.Factory.of(ROW_GUIDE, R.drawable.msg_help, 0xFF00897B,
                MiogramLocale.get("Гід по функціях", "Гид по функциям", "Feature guide"),
                MiogramLocale.get("Інструкції та підказки", "Инструкции и подсказки", "Instructions & tips")));

        items.add(UItem.asShadow(null));

        // 3. Нативні налаштування та спільнота
        items.add(UItem.asHeader(MiogramLocale.get("Нативні функції та спільнота", "Нативные функции и сообщество", "Native features & community")));

        items.add(YumiSettingCell.Factory.of(ROW_BADGES, R.drawable.msg_premium_normal, 0xFFE5486B,
                MiogramLocale.get("Бейджики", "Бейджики", "Badges"),
                MiogramLocale.get("Спільнота Yumigram • Каталог стилів",
                        "Сообщество Yumigram • Каталог стилей",
                        "Yumigram community • Style catalog")));

        items.add(YumiSettingCell.Factory.of(ROW_UPDATES, R.drawable.msg_download_solar, 0xFF2196F3,
                MiogramLocale.get("Автооновлення", "Автообновления", "Auto-updates"),
                app.miogram.bridge.updater.MiogramUpdater.getUpdateChannelName()));

        items.add(YumiSettingCell.Factory.of(ROW_ABOUT, R.drawable.msg_info, 0xFF757575,
                MiogramLocale.get("Про Yumigram", "О Yumigram", "About Yumigram"),
                "Версія " + BuildConfig.VERSION_NAME));

        items.add(UItem.asShadow(MiogramLocale.get(
                "Yumigram: модульна клієнтська платформа на базі офіційного Telegram для Android.",
                "Yumigram: модульная клиентская платформа на базе официального Telegram для Android.",
                "Yumigram: modular client platform based on official Telegram for Android.")));
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ROW_GHOST) {
            presentFragment(new HotModuleSettingsActivity("ghost"));
        } else if (item.id == ROW_VAULT) {
            presentFragment(new HotModuleSettingsActivity("vault"));
        } else if (item.id == ROW_PLAYER) {
            presentFragment(new HotModuleSettingsActivity("player"));
        } else if (item.id == ROW_AME) {
            presentFragment(new HotModuleSettingsActivity("ame"));
        } else if (item.id == ROW_TIKTOK) {
            presentFragment(new HotModuleSettingsActivity("tiktok"));
        } else if (item.id == ROW_AI) {
            presentFragment(new HotModuleSettingsActivity("ai"));
        } else if (item.id == ROW_EXPERIMENTAL) {
            presentFragment(new HotModuleSettingsActivity("experimental"));
        } else if (item.id == ROW_HOTMODULES) {
            presentFragment(new HotModulesActivity());
        } else if (item.id == ROW_PLUGINS) {
            presentFragment(new PluginsActivity());
        } else if (item.id == ROW_GUIDE) {
            presentFragment(new AmegramGuideActivity());
        } else if (item.id == ROW_BADGES) {
            presentFragment(new AmegramBadgesActivity());
        } else if (item.id == ROW_UPDATES) {
            presentFragment(new AmegramUpdateSettingsActivity());
        } else if (item.id == ROW_ABOUT) {
            presentFragment(new MiogramAboutActivity());
        } else if (item.id >= 100 && item.id < 1000) {
            int idx = item.id - 100;
            if (idx >= 0 && idx < customActiveModules.size()) {
                presentFragment(new HotModuleSettingsActivity(
                        customActiveModules.get(idx).info.manifest.id));
            }
        }
    }

    @Override
    public boolean isLightStatusBar() {
        return !Theme.isCurrentThemeDark();
    }
}
