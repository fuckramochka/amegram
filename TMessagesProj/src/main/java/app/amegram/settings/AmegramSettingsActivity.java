package app.amegram.settings;

import android.content.Context;
import android.view.View;

import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import app.amegram.hot.HotModulesManager;
import app.amegram.hot.HotSettingsUiGate;
import app.amegram.hot.ui.HotModuleMeta;
import app.amegram.hot.ui.HotModuleSettingsActivity;
import app.amegram.hot.ui.HotModulesActivity;
import app.amegram.settings.ui.YumiSettingsModel;
import app.amegram.settings.ui.YumiSettingsView;
import app.amegram.theme.YumiMd3;
import app.exteraless.plugins.ui.PluginsActivity;
import app.miogram.bridge.MiogramLocale;
import app.miogram.bridge.settings.MiogramAboutActivity;
import app.miogram.bridge.ui.MiogramAiSettingsActivity;
import app.miogram.bridge.ui.MiogramChatsSettingsActivity;
import app.miogram.bridge.ui.MiogramPrivacySettingsActivity;
import app.miogram.bridge.ui.MiogramVisualsActivity;
import app.miogram.bridge.ai.companion.MiogramCompanionActivity;
import tw.nekomimi.nekogram.settings.NekoChatSettingsActivity;
import tw.nekomimi.nekogram.settings.NekoExperimentalSettingsActivity;
import tw.nekomimi.nekogram.settings.NekoGeneralSettingsActivity;
import tw.nekomimi.nekogram.settings.NekoTranslatorSettingsActivity;

/**
 * Главный экран настроек Yumigram («Юмиграм»).
 *
 * <p>Содержимое строит {@link YumiSettingsView} — выразительная MD3-композиция
 * с фирменным фоном, hero-картой, быстрыми переключателями и тональными
 * группами. Активность отвечает только за данные и навигацию, поэтому все
 * прежние маршруты сохранены один в один.</p>
 */
public class AmegramSettingsActivity extends BaseFragment implements HotModulesManager.ModulesChangeListener {

    private static final int ROW_GHOST = 1;
    private static final int ROW_VAULT = 2;
    private static final int ROW_PLAYER = 3;
    private static final int ROW_AME = 4;
    private static final int ROW_TIKTOK = 5;
    private static final int ROW_AI = 6;
    private static final int ROW_EXPERIMENTAL = 7;
    private static final int ROW_STT = 8;
    private static final int ROW_AUTOMATION = 9;
    private static final int ROW_DEMO = 10;
    private static final int ROW_UI = 11;
    private static final int ROW_FILEORG = 12;

    private static final int ROW_NEKO_GENERAL = 40;
    private static final int ROW_NEKO_CHAT = 41;
    private static final int ROW_NEKO_TRANSLATOR = 42;
    private static final int ROW_NEKO_PASSCODE = 43;
    private static final int ROW_NEKO_EXPERIMENTAL = 44;
    private static final int ROW_MIO_VISUALS = 50;
    private static final int ROW_MIO_CHATS = 51;
    private static final int ROW_MIO_PRIVACY = 52;
    private static final int ROW_MIO_COMPANION = 53;
    private static final int ROW_MIO_AI = 54;
    private static final int ROW_MIO_VAULT = 55;
    private static final int ROW_MIO_PLAYER = 56;
    private static final int ROW_MIO_BADGES = 57;
    private static final int ROW_MIO_MUSIC = 58;
    private static final int ROW_MIO_TIKTOK = 59;
    private static final int ROW_MIO_PUSH = 60;
    private static final int ROW_MIO_HISTORY = 61;
    private static final int ROW_MIO_AUTOMATION = 62;
    private static final int ROW_EXTERA_GENERAL = 70;
    private static final int ROW_EXTERA_APPEARANCE = 71;
    private static final int ROW_EXTERA_CHATS = 72;
    private static final int ROW_EXTERA_OTHER = 73;
    private static final int ROW_ANTIBLOCK = 75;

    private static final int ROW_HOTMODULES = 20;
    private static final int ROW_PLUGINS = 21;
    private static final int ROW_GUIDE = 22;
    private static final int ROW_BETA_MODULES = 23;

    private static final int ROW_BADGES = 30;
    private static final int ROW_UPDATES = 31;
    private static final int ROW_ABOUT = 32;
    private static final int ROW_CLIENT_SETTINGS = 33;

    /** Сторонні модули получают стабильный диапазон идентификаторов. */
    private static final int ROW_CUSTOM_BASE = 1000;

    private static final Set<String> BUILTIN_IDS = new HashSet<>(Arrays.asList(
            "ghost", "vault", "player", "ame", "tiktok", "ai", "experimental", "stt", "demo", "automation",
            "ui", "fileorganization"
    ));

    private YumiSettingsView settingsView;
    private final List<HotModulesManager.InstalledInfo> customInstalledModules = new ArrayList<>();
    private final boolean clientSettingsPage;

    public AmegramSettingsActivity() {
        this(false);
    }

    public AmegramSettingsActivity(boolean clientSettingsPage) {
        this.clientSettingsPage = clientSettingsPage;
    }

    @Override
    public View createView(Context context) {
        setHasOwnBackground(true);
        if (actionBar != null) {
            actionBar.setAddToContainer(false);
            actionBar.setVisibility(View.GONE);
        }

        HotModulesManager.init(context.getApplicationContext());
        HotModulesManager.addListener(this);

        View customView = HotSettingsUiGate.createCustomSettingsView(this, context);
        if (customView != null) {
            fragmentView = customView;
            return fragmentView;
        }

        settingsView = new YumiSettingsView(context, this::buildModel, new YumiSettingsView.Listener() {
            @Override
            public void onBack() {
                finishFragment();
            }

            @Override
            public void onRow(YumiSettingsModel.Row row) {
                route(row.id, row.moduleId);
            }

            @Override
            public void onToggle(YumiSettingsModel.Row row, boolean checked) {
                toggleModule(row.moduleId, checked);
            }

            @Override
            public void onQuick(YumiSettingsModel.Quick quick) {
                if (quick.toggle) {
                    toggleModule(quick.moduleId, !quick.checked);
                } else {
                    route(quick.id, quick.moduleId);
                }
            }

            @Override
            public void onQuickToggle(YumiSettingsModel.Quick quick, boolean checked) {
                toggleModule(quick.moduleId, checked);
            }

            @Override
            public void onFooterAction(YumiSettingsModel.Action action) {
                route(action.id, action.moduleId);
            }
        });
        fragmentView = settingsView;
        settingsView.refresh();
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
        if (settingsView != null) {
            settingsView.refresh();
        }
    }

    // ------------------------------------------------------------------
    // Model
    // ------------------------------------------------------------------

    private YumiSettingsModel buildModel() {
        customInstalledModules.clear();
        YumiSettingsModel model = new YumiSettingsModel();
        if (clientSettingsPage) {
            buildClientModel(model);
        } else {
            buildMainModel(model);
            indexClientRows(model);
        }
        HotSettingsUiGate.customizeModel(model);
        return model;
    }

    /**
     * Поиск на главном экране должен находить и те пункты, которые живут
     * на странице клиента. Строим их один раз и складываем в скрытый индекс.
     */
    private void indexClientRows(YumiSettingsModel model) {
        Set<String> visible = new HashSet<>();
        for (YumiSettingsModel.Section section : model.sections) {
            for (YumiSettingsModel.Row row : section.rows) {
                visible.add(rowKey(row));
            }
        }
        YumiSettingsModel client = new YumiSettingsModel();
        buildClientModel(client);
        for (YumiSettingsModel.Section section : client.sections) {
            for (YumiSettingsModel.Row row : section.rows) {
                if (visible.add(rowKey(row))) {
                    model.searchIndex.add(row);
                }
            }
        }
    }

    private static String rowKey(YumiSettingsModel.Row row) {
        return row.id + ":" + (row.moduleId == null ? "" : row.moduleId);
    }

    private void buildMainModel(YumiSettingsModel model) {
        model.pageTitle = MiogramLocale.get("Налаштування Yumigram", "Настройки Yumigram", "Yumigram settings");
        model.hero.title = model.pageTitle;
        model.hero.subtitle = MiogramLocale.get(
                "Модулі, клієнт і стиль — усе в одному місці.",
                "Модули, клиент и стиль — всё в одном месте.",
                "Modules, client and style — all in one place.");
        model.hero.logoRes = R.drawable.yumi_logo;

        List<HotModulesManager.InstalledInfo> active = activeInstalled();
        int enabledCount = 0;
        for (HotModulesManager.InstalledInfo info : active) {
            if (info.enabled) {
                enabledCount++;
            }
        }
        model.hero.statModules = enabledCount + " " + MiogramLocale.plural(enabledCount,
                "активний", "активні", "активних",
                "активный", "активных", "активных",
                "active", "active");
        model.hero.statVersion = "v" + BuildConfig.VERSION_NAME;
        model.hero.statSecurity = MiogramLocale.get("Приватність", "Приватность", "Privacy");

        // --- Швидкі перемикачі -----------------------------------------
        boolean ghostInstalled = HotModulesManager.isModuleInstalled("ghost");
        boolean ghostOn = ghostInstalled && HotModulesManager.isModuleEnabled("ghost");
        YumiSettingsModel.Quick ghost = quick(ROW_GHOST, "ghost", R.drawable.yumi_ic_ghost,
                YumiMd3.primary(),
                MiogramLocale.get("Режим привида", "Режим призрака", "Ghost mode"),
                !ghostInstalled
                        ? MiogramLocale.get("Встановити модуль", "Установить модуль", "Install module")
                        : ghostOn
                        ? MiogramLocale.get("Онлайн і прочитання приховано", "Онлайн и прочтения скрыты", "Online & reads hidden")
                        : MiogramLocale.get("Увімкнути невидимість", "Включить невидимость", "Turn on invisibility"));
        ghost.toggle = ghostInstalled;
        ghost.checked = ghostOn;
        model.quick.add(ghost);

        boolean uiInstalled = HotModulesManager.isModuleInstalled("ui");
        boolean uiOn = uiInstalled && HotModulesManager.isModuleEnabled("ui");
        YumiSettingsModel.Quick ui = quick(uiInstalled ? ROW_UI : ROW_CLIENT_SETTINGS,
                uiInstalled ? "ui" : null, R.drawable.yumi_ic_palette, YumiMd3.secondary(),
                MiogramLocale.get("Інтерфейс Yumi", "Интерфейс Yumi", "Yumi interface"),
                !uiInstalled
                        ? MiogramLocale.get("Оформлення та теми", "Оформление и темы", "Appearance & themes")
                        : uiOn
                        ? MiogramLocale.get("MD3 увімкнено", "MD3 включён", "MD3 is on")
                        : MiogramLocale.get("Увімкнути MD3", "Включить MD3", "Turn on MD3"));
        ui.toggle = uiInstalled;
        ui.checked = uiOn;
        model.quick.add(ui);

        boolean aiInstalled = HotModulesManager.isModuleInstalled("ai");
        model.quick.add(quick(ROW_AI, "ai", R.drawable.yumi_ic_auto_awesome, YumiMd3.primary(),
                MiogramLocale.get("ШІ-асистент", "ИИ-ассистент", "AI assistant"),
                aiInstalled
                        ? MiogramLocale.get("Ame / KAngel поруч", "Ame / KAngel рядом", "Ame / KAngel is here")
                        : MiogramLocale.get("Встановити Ame / KAngel", "Установить Ame / KAngel", "Install Ame / KAngel")));

        boolean playerInstalled = HotModulesManager.isModuleInstalled("player");
        model.quick.add(quick(ROW_PLAYER, "player", R.drawable.yumi_ic_music, YumiMd3.tertiary(),
                MiogramLocale.get("Музика та тексти", "Музыка и тексты", "Music & lyrics"),
                playerInstalled
                        ? MiogramLocale.get("Плеєр, пошук і LRC", "Плеер, поиск и LRC", "Player, search & LRC")
                        : MiogramLocale.get("Встановити плеєр", "Установить плеер", "Install the player")));

        // --- Управление клиентом ---------------------------------------
        YumiSettingsModel.Section client = section(
                MiogramLocale.get("Керування та клієнт", "Управление и клиент", "Management & Client"),
                null);
        client.rows.add(row(ROW_CLIENT_SETTINGS, R.drawable.yumi_ic_settings, YumiMd3.primary(),
                MiogramLocale.get("Налаштування клієнта", "Настройки клиента", "Client settings"),
                MiogramLocale.get("Чати, приватність, перекладач, оформлення", "Чаты, приватность, переводчик, оформление", "Chats, privacy, translator, appearance")));
        client.rows.add(row(ROW_HOTMODULES, R.drawable.yumi_ic_extension, YumiMd3.primary(),
                MiogramLocale.get("Модулі", "Модули", "Modules"),
                enabledCount > 0
                        ? MiogramLocale.get("Увімкнено: " + enabledCount + " • Каталог та версії",
                        "Включено: " + enabledCount + " • Каталог и версии",
                        "Active: " + enabledCount + " • Catalog & versions")
                        : MiogramLocale.get("Каталог модулів • 0 активних",
                        "Каталог модулей • 0 активных",
                        "Module catalog • 0 active")));
        client.rows.add(row(ROW_PLUGINS, R.drawable.yumi_ic_apps, YumiMd3.primary(),
                MiogramLocale.get("Плагіни", "Плагины", "Plugins"),
                MiogramLocale.get("Python + Lua + Java скрипти", "Python + Lua + Java скрипты", "Python + Lua + Java scripts")));
        client.rows.add(row(ROW_GUIDE, R.drawable.yumi_ic_help, YumiMd3.primary(),
                MiogramLocale.get("Гід по функціях", "Гид по функциям", "Feature guide"),
                MiogramLocale.get("Інструкції та підказки", "Инструкции и подсказки", "Instructions & tips")));
        model.sections.add(client);

        // --- Модулі ----------------------------------------------------
        YumiSettingsModel.Section modules = section(
                MiogramLocale.get("Модулі", "Модули", "Modules"),
                null);

        addModuleRow(modules, "ghost", ROW_GHOST, R.drawable.yumi_ic_ghost, YumiMd3.primary(),
                MiogramLocale.get("Режим привида", "Режим призрака", "Ghost mode"),
                MiogramLocale.get("Скритність онлайну та прочитання", "Скрытность онлайна и прочтения", "Hide online, read receipts & typing"));
        addModuleRow(modules, "ui", ROW_UI, R.drawable.yumi_ic_palette, YumiMd3.primary(),
                MiogramLocale.get("Інтерфейс Yumi", "Интерфейс Yumi", "Yumi interface"),
                MiogramLocale.get("Material Design 3 та навігація", "Material Design 3 и навигация", "Material Design 3 & navigation"));
        addModuleRow(modules, "ai", ROW_AI, R.drawable.yumi_ic_auto_awesome, YumiMd3.primary(),
                MiogramLocale.get("Штучний інтелект (ШІ)", "Искусственный интеллект (ИИ)", "Artificial intelligence (AI)"),
                MiogramLocale.get("Асистент Ame / KAngel, чат і генерація", "Ассистент Ame / KAngel, чат и генерация", "Ame / KAngel companion, chat & generation"));
        addModuleRow(modules, "player", ROW_PLAYER, R.drawable.yumi_ic_music, YumiMd3.primary(),
                MiogramLocale.get("Пошук музики та тексти", "Поиск музыки и тексты", "Music search & lyrics"),
                MiogramLocale.get("Пошук у сервісах, відтворення, LRC", "Поиск в сервисах, воспроизведение, LRC", "Music service search, playback & LRC"));
        addModuleRow(modules, "vault", ROW_VAULT, R.drawable.yumi_ic_storage, YumiMd3.primary(),
                MiogramLocale.get("Хмарне сховище", "Облачное хранилище", "Cloud vault"),
                MiogramLocale.get("AES-256 диск, шифрування", "AES-256 диск, шифрование", "AES-256 virtual disk, encryption"));
        addModuleRow(modules, "experimental", ROW_EXPERIMENTAL, R.drawable.yumi_ic_science, YumiMd3.primary(),
                MiogramLocale.get("Експерименти та видалені", "Эксперименты и удалённые", "Experiments & deleted"),
                MiogramLocale.get("Збереження повідомлень, безліміти", "Сохранение сообщений, безлимиты", "Save deleted messages, unlimited pins"));
        addModuleRow(modules, "tiktok", ROW_TIKTOK, R.drawable.yumi_ic_movie, YumiMd3.primary(),
                "TikTok MI",
                MiogramLocale.get("Відео без водяних знаків, прямі посилання", "Видео без водяных знаков, прямые ссылки", "Watermark-free videos, direct links"));
        addModuleRow(modules, "ame", ROW_AME, R.drawable.yumi_ic_customize, YumiMd3.primary(),
                MiogramLocale.get("Кастомізація UI", "Кастомизация UI", "Customization"),
                MiogramLocale.get("Картки профілю, діалоги, ефекти", "Карточки профиля, диалоги, эффекты", "Profile cards, dialogs, visual effects"));
        addModuleRow(modules, "stt", ROW_STT, R.drawable.yumi_ic_voice, YumiMd3.primary(),
                MiogramLocale.get("Розпізнавання мовлення", "Распознавание речи", "Speech recognition"),
                MiogramLocale.get("Розшифровка голосових і відео", "Расшифровка голосовых и видео", "Transcribe voice and video messages"));
        addModuleRow(modules, "automation", ROW_AUTOMATION, R.drawable.yumi_ic_bolt, YumiMd3.primary(),
                MiogramLocale.get("Автоматизація", "Автоматизация", "Automation"),
                MiogramLocale.get("Синхронізація, резервні копії, очищення", "Синхронизация, резервные копии, очистка", "Sync, backups and cleanup"));
        addModuleRow(modules, "fileorganization", ROW_FILEORG, R.drawable.yumi_ic_folder, YumiMd3.primary(),
                MiogramLocale.get("Організація файлів", "Организация файлов", "File organization"),
                MiogramLocale.get("Збереження завантажень у папки чатів", "Сохранение загрузок в папки чатов", "Save downloads into chat folders"));

        for (HotModulesManager.InstalledInfo info : active) {
            if (info == null || info.manifest == null || BUILTIN_IDS.contains(info.manifest.id)) {
                continue;
            }
            YumiSettingsModel.Row row = moduleRow(info.manifest.id, ROW_CUSTOM_BASE + customInstalledModules.size(),
                    R.drawable.yumi_ic_extension, YumiMd3.primary(),
                    info.manifest.name,
                    MiogramLocale.get("Стороннє розширення", "Стороннее расширение", "Third-party extension"));
            customInstalledModules.add(info);
            modules.rows.add(row);
        }
        if (!modules.rows.isEmpty()) {
            model.sections.add(modules);
        }

        // --- Сообщество и информация -----------------------------------
        YumiSettingsModel.Section about = section(
                MiogramLocale.get("Про застосунок", "О приложении", "About"),
                null);
        about.rows.add(row(ROW_BADGES, R.drawable.yumi_ic_badges, YumiMd3.primary(),
                MiogramLocale.get("Бейджики", "Бейджики", "Badges"),
                MiogramLocale.get("Спільнота Yumigram • Каталог стилів", "Сообщество Yumigram • Каталог стилей", "Yumigram community • Style catalog")));
        about.rows.add(row(ROW_UPDATES, R.drawable.yumi_ic_update, YumiMd3.primary(),
                MiogramLocale.get("Автооновлення", "Автообновления", "Auto-updates"),
                "v" + BuildConfig.VERSION_NAME + " • " + app.miogram.bridge.updater.MiogramUpdater.getUpdateChannelName()));
        about.rows.add(row(ROW_ABOUT, R.drawable.yumi_ic_info, YumiMd3.primary(),
                MiogramLocale.get("Про Yumigram", "О Yumigram", "About Yumigram"),
                MiogramLocale.get("Інформація про клієнт та ліцензії", "Информация о клиенте и лицензии", "Client information and licenses")));
        model.sections.add(about);

        model.footerPrimary = null;
        model.footerSecondary = null;
        model.footerNote = MiogramLocale.get(
                "Yumigram " + BuildConfig.VERSION_NAME + " · Material Design 3",
                "Yumigram " + BuildConfig.VERSION_NAME + " · Material Design 3",
                "Yumigram " + BuildConfig.VERSION_NAME + " · Material Design 3");
    }

    private void buildClientModel(YumiSettingsModel model) {
        model.pageTitle = MiogramLocale.get("Налаштування клієнта", "Настройки клиента", "Client settings");
        model.hero.title = model.pageTitle;
        model.hero.subtitle = MiogramLocale.get(
                "Чати, приватність і зовнішній вигляд — без компромісів.",
                "Чаты, приватность и внешний вид — без компромиссов.",
                "Chats, privacy and appearance — without compromise.");
        model.hero.logoRes = R.drawable.yumi_logo;
        model.hero.statVersion = "v" + BuildConfig.VERSION_NAME;
        model.hero.statSecurity = MiogramLocale.get("Приватність", "Приватность", "Privacy");

        YumiSettingsModel.Section general = section(
                MiogramLocale.get("Основні налаштування", "Основные настройки", "General settings"),
                null);
        general.rows.add(row(ROW_NEKO_GENERAL, R.drawable.yumi_ic_settings, YumiMd3.primary(),
                MiogramLocale.get("Загальні", "Общие", "General"),
                MiogramLocale.get("Мова, поведінка та параметри застосунку", "Язык, поведение и параметры приложения", "Language, behavior and app options")));
        general.rows.add(row(ROW_NEKO_CHAT, R.drawable.yumi_ic_chat, YumiMd3.primary(),
                MiogramLocale.get("Чати", "Чаты", "Chats"),
                MiogramLocale.get("Налаштування чатів та повідомлень", "Настройки чатов и сообщений", "Chat and message options")));
        general.rows.add(row(ROW_NEKO_TRANSLATOR, R.drawable.yumi_ic_translate, YumiMd3.primary(),
                MiogramLocale.get("Перекладач", "Переводчик", "Translator"),
                MiogramLocale.get("Мови, сервіси та поведінка перекладу", "Языки, сервисы и поведение перевода", "Languages, providers and translation behavior")));
        if (!app.miogram.bridge.vault.MiogramDoubleBottomManager.isDuressActive()) {
            general.rows.add(row(ROW_NEKO_PASSCODE, R.drawable.yumi_ic_lock, YumiMd3.primary(),
                    MiogramLocale.get("Код-пароль", "Код-пароль", "Passcode"),
                    MiogramLocale.get("Блокування, PIN і подвійне дно", "Блокировка, PIN и двойное дно", "Lock, PIN & double bottom")));
        }
        general.rows.add(row(ROW_ANTIBLOCK, R.drawable.yumi_ic_vpn, YumiMd3.primary(),
                MiogramLocale.get("Обхід блокувань (Антиблок)", "Обход блокировок (Антиблок)", "Anti-block bypass"),
                MiogramLocale.get("Автоматичний вибір проксі та захист з'єднання", "Автоматический выбор прокси и защита соединения", "Automatic proxy selection and connection protection")));
        general.rows.add(row(ROW_NEKO_EXPERIMENTAL, R.drawable.yumi_ic_science, YumiMd3.primary(),
                MiogramLocale.get("Експериментальні функції", "Экспериментальные функции", "Experimental features"),
                MiogramLocale.get("Додаткові можливості клієнта", "Дополнительные возможности клиента", "Additional client features")));
        model.sections.add(general);

        YumiSettingsModel.Section tools = section(
                MiogramLocale.get("Інтерфейс та сповіщення", "Интерфейс и уведомления", "Interface & Notifications"),
                null);
        tools.rows.add(row(ROW_MIO_VISUALS, R.drawable.yumi_ic_palette, YumiMd3.primary(),
                MiogramLocale.get("Оформлення та інтерфейс", "Оформление и интерфейс", "Appearance & interface"),
                MiogramLocale.get("Вигляд клієнта та елементи інтерфейсу", "Вид клиента и элементы интерфейса", "Client look and interface elements")));
        tools.rows.add(row(ROW_MIO_CHATS, R.drawable.yumi_ic_chat, YumiMd3.primary(),
                MiogramLocale.get("Додаткові налаштування чатів", "Дополнительные настройки чатов", "More chat options"),
                MiogramLocale.get("Жести, панелі та вигляд списку чатів", "Жесты, панели и вид списка чатов", "Gestures, panels and chat list layout")));
        tools.rows.add(row(ROW_MIO_PUSH, R.drawable.yumi_ic_notifications, YumiMd3.primary(),
                MiogramLocale.get("Сповіщення та робота у фоні", "Уведомления и работа в фоне", "Notifications & background"),
                app.miogram.bridge.push.MiogramPushSheet.getShortStatus()));
        tools.rows.add(row(ROW_MIO_HISTORY, R.drawable.yumi_ic_history, YumiMd3.primary(),
                MiogramLocale.get("Історія повідомлень", "История сообщений", "Message history"),
                MiogramLocale.get("Перегляд збережених змін повідомлень", "Просмотр сохранённых изменений сообщений", "Review saved message changes")));
        model.sections.add(tools);

        YumiSettingsModel.Section other = section(
                MiogramLocale.get("Інші параметри клієнта", "Другие параметры клиента", "Additional client options"),
                null);
        other.rows.add(row(ROW_EXTERA_GENERAL, R.drawable.yumi_ic_settings, YumiMd3.primary(),
                MiogramLocale.get("Загальні функції", "Общие функции", "General options"), null));
        other.rows.add(row(ROW_EXTERA_APPEARANCE, R.drawable.yumi_ic_palette, YumiMd3.primary(),
                MiogramLocale.get("Зовнішній вигляд", "Внешний вид", "Appearance"), null));
        other.rows.add(row(ROW_EXTERA_CHATS, R.drawable.yumi_ic_chat, YumiMd3.primary(),
                MiogramLocale.get("Налаштування чатів", "Настройки чатов", "Chat options"), null));
        other.rows.add(row(ROW_EXTERA_OTHER, R.drawable.yumi_ic_tune, YumiMd3.primary(),
                MiogramLocale.get("Інші налаштування", "Другие настройки", "Other options"), null));
        model.sections.add(other);

        YumiSettingsModel.Section about = section(
                MiogramLocale.get("Про застосунок", "О приложении", "About"),
                null);
        about.rows.add(row(ROW_ABOUT, R.drawable.yumi_ic_info, YumiMd3.primary(),
                MiogramLocale.get("Про Yumigram", "О Yumigram", "About Yumigram"),
                MiogramLocale.get("Версія " + BuildConfig.VERSION_NAME, "Версия " + BuildConfig.VERSION_NAME, "Version " + BuildConfig.VERSION_NAME)));
        model.sections.add(about);

        model.footerPrimary = null;
        model.footerSecondary = null;
        model.footerNote = MiogramLocale.get(
                "Yumigram Client " + BuildConfig.VERSION_NAME + " · Material Design 3",
                "Yumigram Client " + BuildConfig.VERSION_NAME + " · Material Design 3",
                "Yumigram Client " + BuildConfig.VERSION_NAME + " · Material Design 3");
    }

    // ------------------------------------------------------------------
    // Model helpers
    // ------------------------------------------------------------------

    private List<HotModulesManager.InstalledInfo> activeInstalled() {
        List<HotModulesManager.InstalledInfo> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (HotModulesManager.InstalledInfo info : HotModulesManager.listInstalled()) {
            if (info == null || info.manifest == null || !info.active) {
                continue;
            }
            if (seen.add(info.manifest.id)) {
                result.add(info);
            }
        }
        return result;
    }

    private static YumiSettingsModel.Section section(CharSequence title, CharSequence subtitle) {
        YumiSettingsModel.Section section = new YumiSettingsModel.Section();
        section.title = title;
        section.subtitle = subtitle;
        return section;
    }

    private static YumiSettingsModel.Row row(int id, int icon, int accent, CharSequence title, CharSequence subtitle) {
        YumiSettingsModel.Row row = new YumiSettingsModel.Row();
        row.id = id;
        row.icon = icon;
        row.accent = accent;
        row.title = title;
        row.subtitle = subtitle;
        return row;
    }

    private static YumiSettingsModel.Quick quick(int id, String moduleId, int icon, int accent,
                                                 CharSequence title, CharSequence subtitle) {
        YumiSettingsModel.Quick quick = new YumiSettingsModel.Quick();
        quick.id = id;
        quick.moduleId = moduleId;
        quick.icon = icon;
        quick.accent = accent;
        quick.title = title;
        quick.subtitle = subtitle;
        return quick;
    }

    private static YumiSettingsModel.Action action(int id, int icon, CharSequence label, boolean primary) {
        YumiSettingsModel.Action action = new YumiSettingsModel.Action();
        action.id = id;
        action.icon = icon;
        action.label = label;
        action.primary = primary;
        return action;
    }

    private void addModuleRow(YumiSettingsModel.Section section, String moduleId, int id,
                              int icon, int accent, CharSequence title, CharSequence description) {
        if (HotModulesManager.isModuleInstalled(moduleId)) {
            section.rows.add(moduleRow(moduleId, id, icon, accent, title, description));
        }
    }

    private YumiSettingsModel.Row moduleRow(String moduleId, int id, int icon, int accent,
                                            CharSequence title, CharSequence description) {
        boolean enabled = HotModulesManager.isModuleEnabled(moduleId);
        YumiSettingsModel.Row row = row(id, icon, accent, title, moduleSubtitle(enabled, description));
        row.moduleId = moduleId;
        row.kind = YumiSettingsModel.KIND_SWITCH;
        row.checked = enabled;
        return row;
    }

    private String moduleSubtitle(boolean enabled, CharSequence description) {
        String state = enabled
                ? MiogramLocale.get("Увімкнено", "Включено", "Enabled")
                : MiogramLocale.get("Вимкнено", "Выключено", "Disabled");
        return state + " · " + description;
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    private void toggleModule(String moduleId, boolean enabled) {
        if (moduleId == null || moduleId.isEmpty()) {
            return;
        }
        if (!HotModulesManager.isModuleInstalled(moduleId)) {
            openModule(moduleId);
            return;
        }
        HotModulesManager.setEnabled(moduleId, enabled, (ok, message, data) -> {
            refresh();
            if (!ok && message != null && !message.isEmpty()) {
                showToast(message);
            }
        });
    }

    private void showToast(String message) {
        try {
            BulletinFactory.of(this).createSimpleBulletin(R.raw.info, message).show();
        } catch (Throwable ignore) {
        }
    }

    private void openModule(String moduleId) {
        if (HotModulesManager.isModuleInstalled(moduleId)) {
            presentFragment(new HotModuleSettingsActivity(moduleId));
        } else {
            presentFragment(new HotModulesActivity());
        }
    }

    private void route(int id, String moduleId) {
        if (id >= ROW_CUSTOM_BASE && moduleId != null) {
            presentFragment(new HotModuleSettingsActivity(moduleId));
            return;
        }
        if (id == ROW_CLIENT_SETTINGS) {
            presentFragment(new AmegramSettingsActivity(true));
        } else if (id == ROW_GHOST) {
            openModule("ghost");
        } else if (id == ROW_VAULT) {
            openModule("vault");
        } else if (id == ROW_PLAYER) {
            openModule("player");
        } else if (id == ROW_AME) {
            openModule("ame");
        } else if (id == ROW_TIKTOK) {
            openModule("tiktok");
        } else if (id == ROW_AI) {
            openModule("ai");
        } else if (id == ROW_EXPERIMENTAL) {
            openModule("experimental");
        } else if (id == ROW_STT) {
            openModule("stt");
        } else if (id == ROW_AUTOMATION) {
            openModule("automation");
        } else if (id == ROW_UI) {
            openModule("ui");
        } else if (id == ROW_FILEORG) {
            openModule("fileorganization");
        } else if (id == ROW_DEMO) {
            openModule("demo");
        } else if (id == ROW_NEKO_GENERAL) {
            presentFragment(new NekoGeneralSettingsActivity());
        } else if (id == ROW_NEKO_CHAT) {
            presentFragment(new NekoChatSettingsActivity());
        } else if (id == ROW_NEKO_TRANSLATOR) {
            presentFragment(new NekoTranslatorSettingsActivity());
        } else if (id == ROW_NEKO_PASSCODE) {
            presentFragment(new app.miogram.bridge.vault.MiogramDoubleBottomActivity());
        } else if (id == ROW_NEKO_EXPERIMENTAL) {
            presentFragment(new NekoExperimentalSettingsActivity());
        } else if (id == ROW_ANTIBLOCK) {
            presentFragment(new app.miogram.bridge.bypass.MiogramAntiBlockActivity());
        } else if (id == ROW_MIO_VISUALS) {
            presentFragment(new MiogramVisualsActivity());
        } else if (id == ROW_MIO_CHATS) {
            presentFragment(new MiogramChatsSettingsActivity());
        } else if (id == ROW_MIO_PRIVACY) {
            presentFragment(new MiogramPrivacySettingsActivity());
        } else if (id == ROW_MIO_COMPANION) {
            presentFragment(new MiogramCompanionActivity());
        } else if (id == ROW_MIO_AI) {
            presentFragment(new MiogramAiSettingsActivity());
        } else if (id == ROW_MIO_VAULT) {
            presentFragment(new app.miogram.bridge.cloudvault.MiogramCloudVaultActivity());
        } else if (id == ROW_MIO_PLAYER) {
            try {
                org.telegram.messenger.MessageObject now = org.telegram.messenger.MediaController.getInstance().getPlayingMessageObject();
                if (now != null && now.isMusic()) {
                    showDialog(app.amegram.hot.Md3Router.create(getParentActivity(), getResourceProvider()));
                } else {
                    org.telegram.ui.Components.BulletinFactory.of(this).createSimpleBulletin(
                            R.raw.info,
                            MiogramLocale.get("Увімкніть музичний трек, щоб налаштувати MD3-плеєр",
                                    "Включите музыкальный трек, чтобы настроить MD3-плеер",
                                    "Play a track to customize the MD3 player")
                    ).show();
                }
            } catch (Throwable ignored) {
            }
        } else if (id == ROW_MIO_BADGES) {
            app.miogram.bridge.badge.MiogramBadgeBottomSheet.show(getParentActivity(),
                    org.telegram.messenger.UserConfig.getInstance(currentAccount).getClientUserId());
        } else if (id == ROW_MIO_MUSIC) {
            new app.miogram.bridge.presence.MiogramConnectedAppsSheet(getParentActivity(), null).show();
        } else if (id == ROW_MIO_TIKTOK) {
            presentFragment(new app.miogram.bridge.ecosystem.AmegramTikTokSettingsActivity());
        } else if (id == ROW_MIO_PUSH) {
            new app.miogram.bridge.push.MiogramPushSheet(getParentActivity(), getResourceProvider()).show();
        } else if (id == ROW_MIO_HISTORY) {
            presentFragment(new NekoExperimentalSettingsActivity());
        } else if (id == ROW_MIO_AUTOMATION) {
            presentFragment(new app.miogram.bridge.userbot.MiogramHerokuActivity());
        } else if (id == ROW_EXTERA_GENERAL) {
            presentFragment(new app.exteraless.settings.OpenExteraGeneralActivity());
        } else if (id == ROW_EXTERA_APPEARANCE) {
            presentFragment(new app.exteraless.settings.OpenExteraAppearanceActivity());
        } else if (id == ROW_EXTERA_CHATS) {
            presentFragment(new app.exteraless.settings.OpenExteraChatsActivity());
        } else if (id == ROW_EXTERA_OTHER) {
            presentFragment(new app.exteraless.settings.OpenExteraOtherActivity());
        } else if (id == ROW_HOTMODULES) {
            presentFragment(new HotModulesActivity());
        } else if (id == ROW_PLUGINS) {
            presentFragment(new PluginsActivity());
        } else if (id == ROW_GUIDE) {
            presentFragment(new AmegramGuideActivity());

        } else if (id == ROW_BADGES) {
            presentFragment(new AmegramBadgesActivity());
        } else if (id == ROW_UPDATES) {
            presentFragment(new AmegramUpdateSettingsActivity());
        } else if (id == ROW_ABOUT) {
            presentFragment(new MiogramAboutActivity());
        }
    }

    @Override
    public boolean isLightStatusBar() {
        return !Theme.isCurrentThemeDark();
    }
}
