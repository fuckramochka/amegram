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

        // Показываем кнопки настроек ТОЛЬКО для реально активных и загруженных модулей
        boolean hasGhost = HotModulesManager.isModuleEnabled("ghost") && HotModulesManager.isModuleActive("ghost");
        boolean hasVault = HotModulesManager.isModuleEnabled("vault") && HotModulesManager.isModuleActive("vault");
        boolean hasPlayer = HotModulesManager.isModuleEnabled("player") && HotModulesManager.isModuleActive("player");
        boolean hasAme = HotModulesManager.isModuleEnabled("ame") && HotModulesManager.isModuleActive("ame");
        boolean hasTikTok = HotModulesManager.isModuleEnabled("tiktok") && HotModulesManager.isModuleActive("tiktok");
        boolean hasAi = HotModulesManager.isModuleEnabled("ai") && HotModulesManager.isModuleActive("ai");
        boolean hasExp = HotModulesManager.isModuleEnabled("experimental") && HotModulesManager.isModuleActive("experimental");

        // Пошук увімкнених сторонніх модулів із налаштуваннями
        List<HotModulesManager.Handle> allHandles = HotModulesManager.settingsHandles();
        for (HotModulesManager.Handle h : allHandles) {
            if (h != null && h.info != null && h.info.manifest != null) {
                if (!BUILTIN_IDS.contains(h.info.manifest.id)) {
                    customActiveModules.add(h);
                }
            }
        }

        boolean anyFeatureActive = hasGhost || hasVault || hasPlayer || hasAme
                || hasTikTok || hasAi || hasExp || !customActiveModules.isEmpty();

        // 1. Розділ активних модулів (з'являється тільки якщо користувач реально щось увімкнув)
        if (anyFeatureActive) {
            items.add(UItem.asHeader(MiogramLocale.get("Увімкнені функції та модулі", "Включённые функции и модули", "Active features & modules")));

            if (hasGhost) {
                items.add(YumiSettingCell.Factory.of(ROW_GHOST, R.drawable.msg_secret, 0xFF8E24AA,
                        MiogramLocale.get("Режим привида", "Режим призрака", "Ghost mode"),
                        MiogramLocale.get("Приховування онлайну, прочитання та тайпінгу",
                                "Скрытие онлайна, прочтения и тайпинга",
                                "Hide online, read receipts & typing")));
            }

            if (hasVault) {
                items.add(YumiSettingCell.Factory.of(ROW_VAULT, R.drawable.msg_saved, 0xFFFF8F00,
                        MiogramLocale.get("Хмарне сховище", "Облачное хранилище", "Cloud vault"),
                        MiogramLocale.get("AES-256 диск, шифрування, чанки та синхронізація",
                                "AES-256 диск, шифрование, чанки и синхронизация",
                                "AES-256 virtual disk, chunking, auto-sync")));
            }

            if (hasPlayer) {
                items.add(YumiSettingCell.Factory.of(ROW_PLAYER, R.drawable.baseline_music_note_24, 0xFF00ACC1,
                        MiogramLocale.get("Музичний плеєр", "Музыкальный плеер", "Music player"),
                        MiogramLocale.get("Пошук із 6 сервісів, кастомний плеєр, тексти LRC",
                                "Поиск из 6 сервисов, кастомный плеер, тексты LRC",
                                "Search 6 music sources, custom player, LRC lyrics")));
            }

            if (hasAme) {
                items.add(YumiSettingCell.Factory.of(ROW_AME, R.drawable.msg_customize, 0xFFE91E63,
                        MiogramLocale.get("Кастомізація UI", "Кастомизация UI", "Customization"),
                        MiogramLocale.get("XML-картки профілю, діалоги, blur",
                                "XML-карточки профиля, диалоги, blur",
                                "Profile XML cards, dialogs, blur")));
            }

            if (hasTikTok) {
                items.add(YumiSettingCell.Factory.of(ROW_TIKTOK, R.drawable.msg_video, 0xFFEE1D52,
                        "TikTok MI",
                        MiogramLocale.get("Вбудований плеєр без ватермарок, чисті посилання",
                                "Встроенный плеер без ватермарок, чистые ссылки",
                                "Watermark-free player, clean links")));
            }

            if (hasAi) {
                items.add(YumiSettingCell.Factory.of(ROW_AI, R.drawable.baseline_stars_24, 0xFF7E57C2,
                        MiogramLocale.get("Штучний інтелект (ШІ)", "Искусственный интеллект (ИИ)", "Artificial Intelligence (AI)"),
                        MiogramLocale.get("Супутник Ame / KAngel, Gemini розпізнавання",
                                "Спутник Ame / KAngel, Gemini распознавание",
                                "Companion Ame / KAngel, Gemini STT")));
            }

            if (hasExp) {
                items.add(YumiSettingCell.Factory.of(ROW_EXPERIMENTAL, R.drawable.msg_fave, 0xFF43A047,
                        MiogramLocale.get("Експериментальні функції", "Экспериментальные функции", "Experimental features"),
                        MiogramLocale.get("Збереження видалених, безліміт закріпів, boost",
                                "Сохранение удаленных, безлимит закрепов, boost",
                                "Save deleted messages, unlimited pins, boost")));
            }

            for (int i = 0; i < customActiveModules.size(); i++) {
                HotModulesManager.Handle h = customActiveModules.get(i);
                String title = h.instance != null ? h.instance.settingsTitle() : null;
                if (title == null || title.isEmpty()) title = h.info.manifest.name;
                items.add(YumiSettingCell.Factory.of(100 + i, R.drawable.msg_plugins, 0xFF2A87FF, title, h.info.manifest.name));
            }

            items.add(UItem.asShadow(null));
        }

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

        items.add(YumiSettingCell.Factory.of(ROW_PLUGINS, R.drawable.msg_bot, 0xFF2A87FF,
                MiogramLocale.get("Плагіни", "Плагины", "Plugins"),
                MiogramLocale.get("Python + Lua + Java скрипти", "Python + Lua + Java скрипты", "Python + Lua + Java scripts")));

        items.add(YumiSettingCell.Factory.of(ROW_GUIDE, R.drawable.msg_help, 0xFF00897B,
                MiogramLocale.get("Гід по функціях", "Гид по функциям", "Feature guide"),
                MiogramLocale.get("Інструкції та підказки", "Инструкции и подсказки", "Instructions & tips")));

        items.add(UItem.asShadow(null));

        // 3. Нативні налаштування та спільнота (вбудовані в ядро, не модуль)
        items.add(UItem.asHeader(MiogramLocale.get("Нативні функції та спільнота", "Нативные функции и сообщество", "Native features & community")));

        items.add(YumiSettingCell.Factory.of(ROW_BADGES, R.drawable.msg_premium_normal, 0xFFE5486B,
                MiogramLocale.get("Бейджики", "Бейджики", "Badges"),
                MiogramLocale.get("Спільнота Yumigram • Каталог стилів",
                        "Сообщество Yumigram • Каталог стилей",
                        "Yumigram community • Style catalog")));

        items.add(YumiSettingCell.Factory.of(ROW_UPDATES, R.drawable.msg_download_solar, 0xFF546E7A,
                MiogramLocale.get("Автооновлення", "Автообновления", "Auto-updates"),
                app.miogram.bridge.updater.MiogramUpdater.getUpdateChannelName()));

        items.add(YumiSettingCell.Factory.of(ROW_ABOUT, R.drawable.msg_info, 0xFFE5486B,
                MiogramLocale.get("Про Yumigram", "О Yumigram", "About Yumigram"),
                MiogramLocale.get("Версія " + BuildConfig.BUILD_VERSION_STRING,
                        "Версия " + BuildConfig.BUILD_VERSION_STRING,
                        "Version " + BuildConfig.BUILD_VERSION_STRING)));

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
