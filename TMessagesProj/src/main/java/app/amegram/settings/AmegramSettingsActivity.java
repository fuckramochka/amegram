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
import app.miogram.bridge.ui.MiogramPrivacySettingsActivity;
import app.miogram.bridge.ui.MiogramVisualsActivity;
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

    // Канонічні розділи клієнта: один екран на одну область.
    private static final int ROW_PERSONALIZATION = 50;
    private static final int ROW_CHATS_MEDIA = 51;
    private static final int ROW_NOTIFICATIONS = 52;
    private static final int ROW_PRIVACY_SECURITY = 53;
    private static final int ROW_DATA_STORAGE = 54;
    private static final int ROW_CHANNEL = 55;
    private static final int ROW_ADVANCED = 56;

    // Класичні екрани Neko / Extera. Більше не мають верхнього рівня:
    // доступні з екрана «Додатково» та з контекстних посилань.
    private static final int ROW_NEKO_GENERAL = 40;
    private static final int ROW_NEKO_CHAT = 41;
    private static final int ROW_NEKO_TRANSLATOR = 42;
    private static final int ROW_NEKO_PASSCODE = 43;
    private static final int ROW_NEKO_EXPERIMENTAL = 44;
    private static final int ROW_EXTERA_GENERAL = 70;
    private static final int ROW_EXTERA_APPEARANCE = 71;
    private static final int ROW_EXTERA_CHATS = 72;
    private static final int ROW_EXTERA_OTHER = 73;
    private static final int ROW_ANTIBLOCK = 75;

    private static final int ROW_HOTMODULES = 20;
    private static final int ROW_PLUGINS = 21;
    private static final int ROW_GUIDE = 22;

    private static final int ROW_BADGES = 30;
    private static final int ROW_UPDATES = 31;
    private static final int ROW_ABOUT = 32;

    // Службові id для прихованого пошукового індексу. Вони не збігаються
    // з id видимих рядків, тому пошук більше не втрачає ці пункти в дедуплі.
    private static final int SEARCH_PASSCODE = 80;
    private static final int SEARCH_ANTIBLOCK = 81;
    private static final int SEARCH_BADGE_WORKSHOP = 82;
    private static final int SEARCH_PLAYER = 83;
    private static final int SEARCH_VAULT = 84;
    private static final int SEARCH_SYSTEM_MODULES = 85;
    private static final int SEARCH_HISTORY = 86;

    /** Сторонні модули получают стабильный диапазон идентификаторов. */
    private static final int ROW_CUSTOM_BASE = 1000;

    private static final Set<String> BUILTIN_IDS = new HashSet<>(Arrays.asList(
            "ghost", "vault", "player", "ame", "tiktok", "ai", "experimental", "stt", "demo", "automation",
            "ui", "fileorganization"
    ));

    private YumiSettingsView settingsView;
    private final List<HotModulesManager.InstalledInfo> customInstalledModules = new ArrayList<>();

    public AmegramSettingsActivity() {
    }

    /**
     * @deprecated Окрему сторінку «Налаштування клієнта» прибрано: усі
     * канонічні розділи тепер живуть на головному екрані. Конструктор
     * залишено для сумісності зі старими диплінками та викликами.
     */
    @Deprecated
    public AmegramSettingsActivity(boolean ignored) {
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
                route(quick.id, quick.moduleId);
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
        buildMainModel(model);
        HotSettingsUiGate.customizeModel(model);
        finalizeModel(model);
        return model;
    }

    /**
     * Службовий етап після кастомізації hot-модулем. Модуль settings_ui
     * історично додавав дублюючі кнопки в підвал і пошукові рядки з чужими
     * id. Хаб сам відповідає за навігацию та пошук, тому футер і індекс
     * перебудовуються тут — незалежно від версії встановленого модуля.
     */
    private void finalizeModel(YumiSettingsModel model) {
        model.footerPrimary = null;
        model.footerSecondary = null;
        // Старі версії модуля settings_ui перезаписували нотатку підвалу.
        model.footerNote = MiogramLocale.get(
                "Yumigram " + BuildConfig.VERSION_NAME + " · Клієнт → Розширення → Спільнота",
                "Yumigram " + BuildConfig.VERSION_NAME + " · Клиент → Расширения → Сообщество",
                "Yumigram " + BuildConfig.VERSION_NAME + " · Client → Extensions → Community");

        model.searchIndex.clear();
        addSearchRow(model, SEARCH_PASSCODE, R.drawable.yumi_ic_lock,
                MiogramLocale.get("Код-пароль і Подвійне дно", "Код-пароль и Двойное дно", "Passcode & Double Bottom"),
                MiogramLocale.get("PIN, приховані акаунти та аварійний код", "PIN, скрытые аккаунты и аварийный код", "PIN, hidden accounts and duress code"));
        addSearchRow(model, SEARCH_ANTIBLOCK, R.drawable.yumi_ic_vpn,
                MiogramLocale.get("Обхід блокувань (Антиблок)", "Обход блокировок (Антиблок)", "Anti-block bypass"),
                MiogramLocale.get("Автоматичні проксі, MTProto та захист з'єднання", "Автоматические прокси, MTProto и защита соединения", "Automatic proxies, MTProto and connection protection"));
        addSearchRow(model, SEARCH_BADGE_WORKSHOP, R.drawable.yumi_ic_badges,
                MiogramLocale.get("Майстерня бейджиків", "Мастерская бейджиков", "Badge Workshop"),
                MiogramLocale.get("Створення та подання власного SVG-бейджа", "Создание и подача собственного SVG-бейджа", "Create and submit your own SVG badge"));
        addSearchRow(model, SEARCH_PLAYER, R.drawable.yumi_ic_music,
                MiogramLocale.get("Музичний плеєр і тексти", "Музыкальный плеер и тексты", "Music player & lyrics"),
                MiogramLocale.get("Пошук треків, LRC та міні-плеєр", "Поиск треков, LRC и мини-плеер", "Track search, LRC and mini player"));
        addSearchRow(model, SEARCH_VAULT, R.drawable.yumi_ic_storage,
                MiogramLocale.get("Хмарне сховище", "Облачное хранилище", "Cloud vault"),
                MiogramLocale.get("AES-256 диск і шифрування", "AES-256 диск и шифрование", "AES-256 disk and encryption"));
        addSearchRow(model, SEARCH_SYSTEM_MODULES, R.drawable.yumi_ic_extension,
                MiogramLocale.get("Системні модулі", "Системные модули", "System modules"),
                MiogramLocale.get("Базові компоненти Yumi та їх оновлення", "Базовые компоненты Yumi и их обновления", "Yumi core components and updates"));
        addSearchRow(model, SEARCH_HISTORY, R.drawable.yumi_ic_history,
                MiogramLocale.get("Історія та видалені повідомлення", "История и удалённые сообщения", "History & deleted messages"),
                MiogramLocale.get("Лабораторія: збереження видалених, regex, glyphs", "Лаборатория: сохранение удалённых, regex, glyphs", "Lab: deleted messages, regex, glyphs"));
    }

    private void addSearchRow(YumiSettingsModel model, int id, int icon, CharSequence title, CharSequence subtitle) {
        YumiSettingsModel.Row row = row(id, icon, YumiMd3.primary(), title, subtitle);
        model.searchIndex.add(row);
    }

    private void buildMainModel(YumiSettingsModel model) {
        model.pageTitle = MiogramLocale.get("Налаштування Yumigram", "Настройки Yumigram", "Yumigram settings");
        model.hero.title = model.pageTitle;
        model.hero.subtitle = MiogramLocale.get(
                "Клієнт, розширення та спільнота — в одному місці.",
                "Клиент, расширения и сообщество — в одном месте.",
                "Client, extensions and community — all in one place.");
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
        YumiSettingsModel.Quick ui = quick(uiInstalled ? ROW_UI : ROW_PERSONALIZATION,
                uiInstalled ? "ui" : null, R.drawable.yumi_ic_palette, YumiMd3.primary(),
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

        // --- Клієнт: один канонічний екран на область --------------------
        YumiSettingsModel.Section client = section(
                MiogramLocale.get("Клієнт", "Клиент", "Client"),
                MiogramLocale.get("Один екран на кожну область — без дублів", "Один экран на каждую область — без дублей", "One screen per domain — no duplicates"));
        client.rows.add(row(ROW_PERSONALIZATION, R.drawable.yumi_ic_palette, YumiMd3.primary(),
                MiogramLocale.get("Персоналізація", "Персонализация", "Personalization"),
                MiogramLocale.get("Тема, стиль, шрифт, аватари та кастомізація", "Тема, стиль, шрифт, аватары и кастомизация", "Theme, style, fonts, avatars and customization")));
        client.rows.add(row(ROW_CHATS_MEDIA, R.drawable.yumi_ic_chat, YumiMd3.primary(),
                MiogramLocale.get("Чати та медіа", "Чаты и медиа", "Chats & media"),
                MiogramLocale.get("Повідомлення, меню, камера, стікери та папки", "Сообщения, меню, камера, стикеры и папки", "Messages, menus, camera, stickers and folders")));
        client.rows.add(row(ROW_NOTIFICATIONS, R.drawable.yumi_ic_notifications, YumiMd3.primary(),
                MiogramLocale.get("Сповіщення", "Уведомления", "Notifications"),
                app.miogram.bridge.push.MiogramPushSheet.getShortStatus()
                        + " • " + MiogramLocale.get("фон і батарея", "фон и батарея", "background & battery")));
        client.rows.add(row(ROW_PRIVACY_SECURITY, R.drawable.yumi_ic_lock, YumiMd3.primary(),
                MiogramLocale.get("Приватність і безпека", "Приватность и безопасность", "Privacy & security"),
                MiogramLocale.get("Ghost, анти-видалення, PIN і антиблок", "Ghost, анти-удаление, PIN и антиблок", "Ghost, anti-delete, PIN and anti-block")));
        client.rows.add(row(ROW_DATA_STORAGE, R.drawable.yumi_ic_storage, YumiMd3.primary(),
                MiogramLocale.get("Дані та сховище", "Данные и хранилище", "Data & storage"),
                MiogramLocale.get("Медіа, кеш, мережа та автозавантаження", "Медиа, кеш, сеть и автозагрузка", "Media, cache, network and auto-download")));
        client.rows.add(row(ROW_NEKO_TRANSLATOR, R.drawable.yumi_ic_translate, YumiMd3.primary(),
                MiogramLocale.get("Перекладач", "Переводчик", "Translator"),
                MiogramLocale.get("Мови, сервіси та поведінка перекладу", "Языки, сервисы и поведение перевода", "Languages, providers and translation behavior")));
        model.sections.add(client);

        // --- Розширення -------------------------------------------------
        YumiSettingsModel.Section extensions = section(
                MiogramLocale.get("Розширення", "Расширения", "Extensions"),
                MiogramLocale.get("Модулі, плагіни та довідка", "Модули, плагины и справка", "Modules, plugins and guides"));
        extensions.rows.add(row(ROW_HOTMODULES, R.drawable.yumi_ic_extension, YumiMd3.primary(),
                MiogramLocale.get("Керування модулями", "Управление модулями", "Manage modules"),
                enabledCount > 0
                        ? MiogramLocale.get("Увімкнено: " + enabledCount + " • Каталог та версії",
                        "Включено: " + enabledCount + " • Каталог и версии",
                        "Active: " + enabledCount + " • Catalog & versions")
                        : MiogramLocale.get("Каталог модулів • 0 активних",
                        "Каталог модулей • 0 активных",
                        "Module catalog • 0 active")));
        extensions.rows.add(row(ROW_PLUGINS, R.drawable.yumi_ic_apps, YumiMd3.primary(),
                MiogramLocale.get("Плагіни", "Плагины", "Plugins"),
                MiogramLocale.get("Python + Lua + Java скрипти", "Python + Lua + Java скрипты", "Python + Lua + Java scripts")));
        extensions.rows.add(row(ROW_GUIDE, R.drawable.yumi_ic_help, YumiMd3.primary(),
                MiogramLocale.get("Гід по розширеннях", "Гид по расширениям", "Extensions guide"),
                MiogramLocale.get("Як встановлювати плагіни та модулі", "Как устанавливать плагины и модули", "How to install plugins and modules")));
        model.sections.add(extensions);

        // --- Швидкі модулі (перемикачі встановлених) -----------------
        YumiSettingsModel.Section modules = section(
                MiogramLocale.get("Швидкі модулі", "Быстрые модули", "Quick modules"),
                MiogramLocale.get("Швидке вмикання та вимкнення", "Быстрое включение и выключение", "Toggle installed modules"));

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
            if (info == null || info.manifest == null || BUILTIN_IDS.contains(info.manifest.id) || HotModulesManager.isSystem(info.manifest.id)) {
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

        // --- Спільнота --------------------------------------------------
        YumiSettingsModel.Section community = section(
                MiogramLocale.get("Спільнота", "Сообщество", "Community"),
                null);
        community.rows.add(row(ROW_BADGES, R.drawable.yumi_ic_badges, YumiMd3.primary(),
                MiogramLocale.get("Бейджики", "Бейджики", "Badges"),
                MiogramLocale.get("Мій бейдж, каталог стилів і майстерня", "Мой бейдж, каталог стилей и мастерская", "My badge, style catalog and workshop")));
        community.rows.add(row(ROW_CHANNEL, R.drawable.msg_channel, YumiMd3.tertiary(),
                MiogramLocale.get("Канал Yumigram", "Канал Yumigram", "Yumigram channel"),
                "@" + MiogramAboutActivity.CHANNEL_USERNAME + " • "
                        + MiogramLocale.get("новини та оновлення", "новости и обновления", "news & updates")));
        model.sections.add(community);

        // --- Система ----------------------------------------------------
        YumiSettingsModel.Section system = section(
                MiogramLocale.get("Система", "Система", "System"),
                null);
        system.rows.add(row(ROW_NEKO_EXPERIMENTAL, R.drawable.yumi_ic_science, YumiMd3.primary(),
                MiogramLocale.get("Лабораторія", "Лаборатория", "Laboratory"),
                MiogramLocale.get("Видалені повідомлення, regex, безліміти та експерименти", "Удалённые сообщения, regex, безлимиты и эксперименты", "Deleted messages, regex, unlimited and experiments")));
        system.rows.add(row(ROW_UPDATES, R.drawable.yumi_ic_update, YumiMd3.primary(),
                MiogramLocale.get("Оновлення застосунку", "Обновление приложения", "App updates"),
                "v" + BuildConfig.VERSION_NAME + " • " + app.miogram.bridge.updater.MiogramUpdater.getUpdateChannelName()));
        system.rows.add(row(ROW_ABOUT, R.drawable.yumi_ic_info, YumiMd3.primary(),
                MiogramLocale.get("Про Yumigram", "О Yumigram", "About Yumigram"),
                MiogramLocale.get("Інформація про клієнт та ліцензії", "Информация о клиенте и лицензии", "Client information and licenses")));
        system.rows.add(row(ROW_ADVANCED, R.drawable.yumi_ic_tune, YumiMd3.secondary(),
                MiogramLocale.get("Додатково (Extera)", "Дополнительно (Extera)", "Advanced (Extera)"),
                MiogramLocale.get("Застарілі екрани на час міграції", "Устаревшие экраны на время миграции", "Legacy screens during migration")));
        model.sections.add(system);
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
            if (!HotModulesManager.isModuleEnabled(moduleId)) {
                showToast(MiogramLocale.get(
                        "Модуль вимкнено. Увімкніть його для переходу в налаштування.",
                        "Модуль выключен. Включите его для перехода в настройки.",
                        "Module is disabled. Enable it to access settings."));
                return;
            }
            presentFragment(new HotModuleSettingsActivity(moduleId));
        } else {
            presentFragment(new HotModulesActivity());
        }
    }

    private void route(int id, String moduleId) {
        if (id >= ROW_CUSTOM_BASE && moduleId != null) {
            if (!HotModulesManager.isModuleEnabled(moduleId)) {
                showToast(MiogramLocale.get(
                        "Модуль вимкнено. Увімкніть його для переходу в налаштування.",
                        "Модуль выключен. Включите его для перехода в настройки.",
                        "Module is disabled. Enable it to access settings."));
                return;
            }
            presentFragment(new HotModuleSettingsActivity(moduleId));
            return;
        }
        if (id == ROW_PERSONALIZATION) {
            presentFragment(new MiogramVisualsActivity());
        } else if (id == ROW_CHATS_MEDIA) {
            presentFragment(new app.exteraless.settings.OpenExteraChatsActivity());
        } else if (id == ROW_NOTIFICATIONS) {
            new app.miogram.bridge.push.MiogramPushSheet(getParentActivity(), getResourceProvider()).show();
        } else if (id == ROW_PRIVACY_SECURITY) {
            presentFragment(new MiogramPrivacySettingsActivity());
        } else if (id == ROW_DATA_STORAGE) {
            presentFragment(new org.telegram.ui.DataSettingsActivity());
        } else if (id == ROW_NEKO_TRANSLATOR) {
            presentFragment(new NekoTranslatorSettingsActivity());
        } else if (id == ROW_NEKO_EXPERIMENTAL || id == SEARCH_HISTORY) {
            presentFragment(new NekoExperimentalSettingsActivity());
        } else if (id == ROW_CHANNEL) {
            openChannel();
        } else if (id == ROW_ADVANCED) {
            presentFragment(new AmegramAdvancedSettingsActivity());
        } else if (id == ROW_NEKO_GENERAL) {
            presentFragment(new NekoGeneralSettingsActivity());
        } else if (id == ROW_NEKO_CHAT) {
            presentFragment(new NekoChatSettingsActivity());
        } else if (id == ROW_NEKO_PASSCODE || id == SEARCH_PASSCODE) {
            presentFragment(new app.miogram.bridge.vault.MiogramDoubleBottomActivity());
        } else if (id == ROW_ANTIBLOCK || id == SEARCH_ANTIBLOCK) {
            presentFragment(new app.miogram.bridge.bypass.MiogramAntiBlockActivity());
        } else if (id == ROW_EXTERA_GENERAL) {
            presentFragment(new app.exteraless.settings.OpenExteraGeneralActivity());
        } else if (id == ROW_EXTERA_APPEARANCE) {
            presentFragment(new app.exteraless.settings.OpenExteraAppearanceActivity());
        } else if (id == ROW_EXTERA_CHATS) {
            presentFragment(new app.exteraless.settings.OpenExteraChatsActivity());
        } else if (id == ROW_EXTERA_OTHER) {
            presentFragment(new app.exteraless.settings.OpenExteraOtherActivity());
        } else if (id == ROW_HOTMODULES || id == SEARCH_SYSTEM_MODULES) {
            presentFragment(new HotModulesActivity());
        } else if (id == ROW_PLUGINS) {
            presentFragment(new PluginsActivity());
        } else if (id == ROW_GUIDE) {
            presentFragment(new AmegramGuideActivity());
        } else if (id == ROW_BADGES) {
            app.miogram.bridge.badge.MiogramBadgeManager.openBadgesSettings(this);
        } else if (id == SEARCH_BADGE_WORKSHOP) {
            presentFragment(new AmegramBadgeWorkshopActivity());
        } else if (id == ROW_UPDATES) {
            presentFragment(new AmegramUpdateSettingsActivity());
        } else if (id == ROW_ABOUT) {
            presentFragment(new MiogramAboutActivity());
        } else if (id == ROW_GHOST) {
            openModule("ghost");
        } else if (id == ROW_VAULT || id == SEARCH_VAULT) {
            openModule("vault");
        } else if (id == ROW_PLAYER || id == SEARCH_PLAYER) {
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
        }
    }

    private void openChannel() {
        try {
            org.telegram.messenger.MessagesController.getInstance(currentAccount)
                    .openByUserName(MiogramAboutActivity.CHANNEL_USERNAME, this, 1);
        } catch (Throwable ignore) {
            org.telegram.messenger.browser.Browser.openUrl(getParentActivity(),
                    "https://t.me/" + MiogramAboutActivity.CHANNEL_USERNAME);
        }
    }

    @Override
    public boolean isLightStatusBar() {
        return !Theme.isCurrentThemeDark();
    }
}
