package app.amegram.settings;

import android.content.Context;
import android.view.View;

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
import app.exteraless.plugins.ui.PluginsActivity;
import app.miogram.bridge.MiogramLocale;

/**
 * Головний розділ налаштувань Amegram.
 * На першому старті — чистий екран без зайвого сміття.
 * Кнопки налаштувань функціоналу модулів (Привид, Сховище, Плеєр, Експерименти...)
 * з'являються та зникають ТІЛЬКИ ПІСЛЯ ВВІМКНЕННЯ відповідного модуля в реальному часі.
 */
public class AmegramSettingsActivity extends BaseFragment implements HotModulesManager.ModulesChangeListener {

    private static final int ROW_GHOST = 10;
    private static final int ROW_VAULT = 11;
    private static final int ROW_PLAYER = 12;
    private static final int ROW_AME = 13;
    private static final int ROW_TIKTOK = 14;
    private static final int ROW_AI = 15;
    private static final int ROW_EXPERIMENTAL = 16;

    private static final int ROW_HOTMODULES = 20;
    private static final int ROW_PLUGINS = 21;
    private static final int ROW_GUIDE = 22;

    private static final int ROW_BADGES = 30;
    private static final int ROW_UPDATES = 31;

    private static final Set<String> BUILTIN_IDS = new HashSet<>(Arrays.asList(
            "ghost", "vault", "player", "ame", "tiktok", "stt", "ai", "experimental", "automation", "demo"
    ));

    private UniversalRecyclerView listView;
    private final List<HotModulesManager.Handle> customActiveModules = new ArrayList<>();

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(MiogramLocale.get("Amegram", "Amegram", "Amegram"));
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

        boolean hasGhost = HotModulesManager.isModuleEnabled("ghost");
        boolean hasVault = HotModulesManager.isModuleEnabled("vault");
        boolean hasPlayer = HotModulesManager.isModuleEnabled("player");
        boolean hasAme = HotModulesManager.isModuleEnabled("ame");
        boolean hasTikTok = HotModulesManager.isModuleEnabled("tiktok");
        boolean hasAi = HotModulesManager.isModuleEnabled("ai");
        boolean hasExp = HotModulesManager.isModuleEnabled("experimental");

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

            // Режим привида (лише коли модуль увімкнено)
            if (hasGhost) {
                items.add(UItem.asSettingsCell(ROW_GHOST, R.drawable.msg_secret,
                        MiogramLocale.get("Режим привида", "Режим призрака", "Ghost mode"),
                        MiogramLocale.get("Приховування онлайну, прочитання та тайпінгу",
                                "Скрытие онлайна, прочтения и тайпинга",
                                "Hide online, read receipts & typing")));
            }

            // Хмарне сховище (лише коли увімкнено)
            if (hasVault) {
                items.add(UItem.asSettingsCell(ROW_VAULT, R.drawable.msg_saved,
                        MiogramLocale.get("Хмарне сховище та автоматизація",
                                "Облачное хранилище и автоматизация",
                                "Cloud storage & automation"),
                        MiogramLocale.get("AES-256 віртуальний диск, чанки, автосинхронізація",
                                "AES-256 виртуальный диск, чанки, автосинхронизация",
                                "AES-256 virtual disk, chunking, auto-sync")));
            }

            // Плеєр (лише коли увімкнено)
            if (hasPlayer) {
                items.add(UItem.asSettingsCell(ROW_PLAYER, R.drawable.baseline_music_note_24,
                        MiogramLocale.get("Музика та аудіоплеєр", "Музыка и аудиоплеер", "Music & audio player"),
                        MiogramLocale.get("Пошук із 6 сервісів, кастомний плеєр, тексти LRC",
                                "Поиск из 6 сервисов, кастомный плеер, тексты LRC",
                                "Search 6 music sources, custom player, LRC lyrics")));
            }

            // Кастомізація UI (лише коли увімкнено)
            if (hasAme) {
                items.add(UItem.asSettingsCell(ROW_AME, R.drawable.msg_customize,
                        MiogramLocale.get("Кастомізація та Ame Studio", "Кастомизация и Ame Studio", "Customization & Ame Studio"),
                        MiogramLocale.get("XML-картки профіля, градієнти, діалоги, blur",
                                "XML-карточки профиля, градиенты, диалоги, blur",
                                "Profile XML cards, gradients, dialogs, blur")));
            }

            // TikTok MI (лише коли увімкнено)
            if (hasTikTok) {
                items.add(UItem.asSettingsCell(ROW_TIKTOK, R.drawable.msg_video,
                        MiogramLocale.get("TikTok MI", "TikTok MI", "TikTok MI"),
                        MiogramLocale.get("Вбудований плеєр без ватермарок, чисті посилання",
                                "Встроенный плеер без ватермарок, чистые ссылки",
                                "Watermark-free player, clean links")));
            }

            // ШІ компаньйон (лише коли увімкнено)
            if (hasAi) {
                items.add(UItem.asSettingsCell(ROW_AI, R.drawable.baseline_stars_24,
                        MiogramLocale.get("Штучний інтелект (ШІ)", "Искусственный интеллект (ИИ)", "Artificial Intelligence (AI)"),
                        MiogramLocale.get("Супутник Ame / KAngel, Gemini / Whisper розпізнавання",
                                "Спутник Ame / KAngel, Gemini / Whisper распознавание",
                                "Companion Ame / KAngel, Gemini / Whisper STT")));
            }

            // Експерименти (перегляд видалених повідомлень, безліміти)
            if (hasExp) {
                items.add(UItem.asSettingsCell(ROW_EXPERIMENTAL, R.drawable.msg_fave,
                        MiogramLocale.get("Експериментальні налаштування", "Экспериментальные настройки", "Experimental settings"),
                        MiogramLocale.get("Збереження видалених, безліміт закріпів, upload boost",
                                "Сохранение удаленных, безлимит закрепов, upload boost",
                                "Save deleted messages, unlimited pins, upload boost")));
            }

            // Сторонні увімкнені модулі
            for (int i = 0; i < customActiveModules.size(); i++) {
                HotModulesManager.Handle h = customActiveModules.get(i);
                String title = h.instance != null ? h.instance.settingsTitle() : null;
                if (title == null || title.isEmpty()) title = h.info.manifest.name;
                items.add(UItem.asSettingsCell(100 + i, R.drawable.msg_plugins, title, h.info.manifest.name));
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

        items.add(UItem.asSettingsCell(ROW_HOTMODULES, R.drawable.msg_plugins,
                MiogramLocale.get("Хот-модулі (.hmod)", "Хот-модули (.hmod)", "Hot modules (.hmod)"),
                hotSubtitle));

        items.add(UItem.asSettingsCell(ROW_PLUGINS, R.drawable.msg_bot,
                MiogramLocale.get("Плагіни", "Плагины", "Plugins"),
                MiogramLocale.get("Python + Lua + Java скрипти", "Python + Lua + Java скрипты", "Python + Lua + Java scripts")));

        items.add(UItem.asSettingsCell(ROW_GUIDE, R.drawable.msg_help,
                MiogramLocale.get("Гід по функціях", "Гид по функциям", "Feature guide"),
                MiogramLocale.get("Інструкції та підказки", "Инструкции и подсказки", "Instructions & tips")));

        items.add(UItem.asShadow(null));

        // 3. Нативні налаштування та спільнота (вбудовані в ядро, не модуль)
        items.add(UItem.asHeader(MiogramLocale.get("Нативні функції та спільнота", "Нативные функции и сообщество", "Native features & community")));

        // Бейджики вбудовані нативно!
        items.add(UItem.asSettingsCell(ROW_BADGES, R.drawable.msg_premium_normal,
                MiogramLocale.get("Бейджики", "Бейджики", "Badges"),
                MiogramLocale.get("Спільнота Amegram • Нативна підтримка",
                        "Сообщество Amegram • Нативная поддержка",
                        "Amegram community • Native support")));

        items.add(UItem.asSettingsCell(ROW_UPDATES, R.drawable.msg_download_solar,
                MiogramLocale.get("Автооновлення", "Автообновления", "Auto-updates"),
                app.miogram.bridge.updater.MiogramUpdater.getUpdateChannelName()));

        items.add(UItem.asShadow(MiogramLocale.get(
                "Amegram: модульна клієнтська платформа на базі офіційного Telegram для Android.",
                "Amegram: модульная клиентская платформа на базе официального Telegram для Android.",
                "Amegram: modular client platform based on official Telegram for Android.")));
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
