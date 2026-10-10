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
import app.miogram.bridge.ui.MiogramAiSettingsActivity;
import app.miogram.bridge.ui.MiogramChatsSettingsActivity;
import app.miogram.bridge.ui.MiogramPrivacySettingsActivity;
import app.miogram.bridge.ui.MiogramVisualsActivity;
import app.miogram.bridge.ai.companion.MiogramCompanionActivity;
import tw.nekomimi.nekogram.settings.NekoChatSettingsActivity;
import tw.nekomimi.nekogram.settings.NekoExperimentalSettingsActivity;
import tw.nekomimi.nekogram.settings.NekoGeneralSettingsActivity;
import tw.nekomimi.nekogram.settings.NekoPasscodeSettingsActivity;
import tw.nekomimi.nekogram.settings.NekoTranslatorSettingsActivity;

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
    private static final int ROW_NATIVE_MODULES = 74;

    private static final int ROW_HOTMODULES = 20;
    private static final int ROW_PLUGINS = 21;
    private static final int ROW_GUIDE = 22;

    private static final int ROW_BADGES = 30;
    private static final int ROW_UPDATES = 31;
    private static final int ROW_ABOUT = 32;
    private static final int ROW_CLIENT_SETTINGS = 33;

    private static final Set<String> BUILTIN_IDS = new HashSet<>(Arrays.asList(
            "ghost", "vault", "player", "ame", "tiktok", "ai", "experimental", "stt", "demo", "automation",
            "ui", "fileorganization"
    ));

    private UniversalRecyclerView listView;
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
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(clientSettingsPage
                ? MiogramLocale.get("Налаштування клієнта", "Настройки клиента", "Client settings")
                : MiogramLocale.get("Налаштування Yumigram", "Настройки Yumigram", "Yumigram settings"));
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

    private String moduleSubtitle(String moduleId, boolean enabled, String description) {
        if (!HotModulesManager.isModuleInstalled(moduleId)) {
            return MiogramLocale.get("Не встановлено · " + description,
                    "Не установлено · " + description,
                    "Not installed · " + description);
        }
        String state = enabled
                ? MiogramLocale.get("Увімкнено", "Включено", "Enabled")
                : MiogramLocale.get("Вимкнено", "Выключено", "Disabled");
        return state + " · " + description;
    }

    private void openModule(String moduleId) {
        if (HotModulesManager.isModuleInstalled(moduleId)) {
            presentFragment(new HotModuleSettingsActivity(moduleId));
        } else {
            presentFragment(new HotModulesActivity());
        }
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        customInstalledModules.clear();
        if (clientSettingsPage) {
            fillClientSettings(items);
            return;
        }

        // Лише встановлені модулі відображаються у списку налаштувань.
        // Видалений модуль повністю зникає з меню налаштувань.
        items.add(UItem.asHeader(MiogramLocale.get("Можливості Yumigram", "Возможности Yumigram", "Yumigram features")));
        items.add(YumiSettingCell.Factory.of(ROW_CLIENT_SETTINGS, R.drawable.msg_settings, 0xFF546E7A,
                MiogramLocale.get("Інші налаштування клієнта", "Другие настройки клиента", "More client settings"),
                MiogramLocale.get("Чати, приватність, перекладач, оформлення та інше",
                        "Чаты, приватность, переводчик, оформление и другое",
                        "Chats, privacy, translation, appearance and more")));

        if (HotModulesManager.isModuleInstalled("ghost")) {
            boolean ghostOn = HotModulesManager.isModuleEnabled("ghost");
            items.add(YumiSettingCell.Factory.of(ROW_GHOST, R.drawable.msg_secret, 0xFF9C27B0,
                    MiogramLocale.get("Режим привида", "Режим призрака", "Ghost mode"),
                    moduleSubtitle("ghost", ghostOn, MiogramLocale.get("Скритність онлайну та прочитання",
                                    "Скрытность онлайна и прочтения", "Hide online, read receipts & typing"))));
        }

        if (HotModulesManager.isModuleInstalled("experimental")) {
            boolean expOn = HotModulesManager.isModuleEnabled("experimental");
            items.add(YumiSettingCell.Factory.of(ROW_EXPERIMENTAL, R.drawable.msg_fave, 0xFF43A047,
                    MiogramLocale.get("Експерименти та видалені", "Эксперименты и удалённые", "Experiments & Deleted"),
                    moduleSubtitle("experimental", expOn, MiogramLocale.get("Збереження повідомлень, безліміти",
                                    "Сохранение сообщений, безлимиты", "Save deleted messages, unlimited pins"))));
        }

        if (HotModulesManager.isModuleInstalled("vault")) {
            boolean vaultOn = HotModulesManager.isModuleEnabled("vault");
            items.add(YumiSettingCell.Factory.of(ROW_VAULT, R.drawable.msg_saved, 0xFFE5486B,
                    MiogramLocale.get("Хмарне сховище", "Облачное хранилище", "Cloud vault"),
                    moduleSubtitle("vault", vaultOn, MiogramLocale.get("AES-256 диск, шифрування",
                                    "AES-256 диск, шифрование", "AES-256 virtual disk, encryption"))));
        }

        if (HotModulesManager.isModuleInstalled("player")) {
            boolean playerOn = HotModulesManager.isModuleEnabled("player");
            items.add(YumiSettingCell.Factory.of(ROW_PLAYER, R.drawable.baseline_music_note_24, 0xFF00ACC1,
                    MiogramLocale.get("Пошук музики та тексти (.hmod)", "Поиск музыки и тексты (.hmod)", "Music search & lyrics (.hmod)"),
                    moduleSubtitle("player", playerOn, MiogramLocale.get("Пошук у музичних сервісах, відтворення, LRC",
                                     "Поиск в музыкальных сервисах, воспроизведение, LRC", "Music service search, playback and LRC lyrics"))));
        }

        if (HotModulesManager.isModuleInstalled("ai")) {
            boolean aiOn = HotModulesManager.isModuleEnabled("ai");
            items.add(YumiSettingCell.Factory.of(ROW_AI, R.drawable.baseline_stars_24, 0xFF8B5CF6,
                    MiogramLocale.get("Штучний інтелект (ШІ)", "Искусственный интеллект (ИИ)", "Artificial Intelligence (AI)"),
                    moduleSubtitle("ai", aiOn, MiogramLocale.get("Асистент Ame / KAngel, чат і генерація тексту",
                                   "Ассистент Ame / KAngel, чат и генерация текста", "Ame / KAngel companion, chat & text generation"))));
        }

        if (HotModulesManager.isModuleInstalled("tiktok")) {
            boolean tikOn = HotModulesManager.isModuleEnabled("tiktok");
            items.add(YumiSettingCell.Factory.of(ROW_TIKTOK, R.drawable.msg_video, 0xFFEE1D52,
                    "TikTok MI",
                    moduleSubtitle("tiktok", tikOn, MiogramLocale.get("Відео без водяних знаків, прямі посилання",
                                  "Видео без водяных знаков, прямые ссылки", "Watermark-free videos, direct links"))));
        }

        if (HotModulesManager.isModuleInstalled("ame")) {
            boolean ameOn = HotModulesManager.isModuleEnabled("ame");
            items.add(YumiSettingCell.Factory.of(ROW_AME, R.drawable.msg_customize, 0xFFE91E63,
                    MiogramLocale.get("Кастомізація UI", "Кастомизация UI", "Customization"),
                    moduleSubtitle("ame", ameOn, MiogramLocale.get("Картки профілю, діалоги, ефекти",
                                  "Карточки профиля, диалоги, эффекты", "Profile cards, dialogs, visual effects"))));
        }

        for (String moduleId : new String[]{"stt", "automation"}) {
            if (HotModulesManager.isModuleInstalled(moduleId)) {
                boolean enabled = HotModulesManager.isModuleEnabled(moduleId);
                int rowId = "stt".equals(moduleId) ? ROW_STT : ROW_AUTOMATION;
                String title = "stt".equals(moduleId)
                        ? MiogramLocale.get("Розпізнавання мовлення", "Распознавание речи", "Speech recognition")
                        : MiogramLocale.get("Автоматизація", "Автоматизация", "Automation");
                String description = "stt".equals(moduleId)
                        ? MiogramLocale.get("Розшифровка голосових і відеоповідомлень", "Расшифровка голосовых и видеосообщений", "Transcribe voice and video messages")
                        : MiogramLocale.get("Синхронізація, резервні копії та очищення", "Синхронизация, резервные копии и очистка", "Sync, backups and cleanup");
                items.add(YumiSettingCell.Factory.of(rowId, R.drawable.msg_plugins, 0xFF607D8B,
                        title, moduleSubtitle(moduleId, enabled, description)));
            }
        }

        if (HotModulesManager.isModuleInstalled("ui")) {
            boolean uiOn = HotModulesManager.isModuleEnabled("ui");
            items.add(YumiSettingCell.Factory.of(ROW_UI, R.drawable.msg_theme, 0xFF6C63FF,
                    MiogramLocale.get("Інтерфейс Yumi", "Интерфейс Yumi", "Yumi Interface"),
                    moduleSubtitle("ui", uiOn, MiogramLocale.get("Yougram Expressive ↔ Classic на льоту",
                                  "Yougram Expressive ↔ Classic на лету", "Yougram Expressive ↔ Classic live"))));
        }

        if (HotModulesManager.isModuleInstalled("fileorganization")) {
            boolean fileorgOn = HotModulesManager.isModuleEnabled("fileorganization");
            items.add(YumiSettingCell.Factory.of(ROW_FILEORG, R.drawable.msg_download_solar, 0xFF00897B,
                    MiogramLocale.get("Організація файлів", "Организация файлов", "File Organization"),
                    moduleSubtitle("fileorganization", fileorgOn, MiogramLocale.get("Збереження завантажень у папки чатів",
                                  "Сохранение загрузок в папки чатов", "Save downloads into chat folders"))));
        }

        // Сторонні завантажені модулі
        for (HotModulesManager.InstalledInfo info : HotModulesManager.listInstalled()) {
            if (info != null && info.active && info.manifest != null
                    && !BUILTIN_IDS.contains(info.manifest.id)) {
                customInstalledModules.add(info);
            }
        }
        for (int i = 0; i < customInstalledModules.size(); i++) {
            HotModulesManager.InstalledInfo info = customInstalledModules.get(i);
            String title = info.manifest.name;
            String status = info.enabled
                    ? MiogramLocale.get("Увімкнено", "Включено", "Enabled")
                    : MiogramLocale.get("Вимкнено", "Выключено", "Disabled");
            items.add(YumiSettingCell.Factory.of(100 + i, R.drawable.msg_plugins, 0xFF2A87FF,
                    title, status + " · " + info.manifest.name));
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

        // 3. Нативні налаштування та спільнота
        items.add(UItem.asHeader(MiogramLocale.get("Спільнота та інформація", "Сообщество и информация", "Community & about")));

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
                "Yumigram: модульний клієнт Telegram із єдиним центром налаштувань.",
                "Yumigram: модульный клиент Telegram с единым центром настроек.",
                "Yumigram: a modular Telegram client with one settings hub.")));
    }

    private void fillClientSettings(ArrayList<UItem> items) {
        items.add(UItem.asHeader(MiogramLocale.get("Основні налаштування", "Основные настройки", "General settings")));
        items.add(YumiSettingCell.Factory.of(ROW_NATIVE_MODULES, R.drawable.msg_plugins, 0xFF607D8B,
                MiogramLocale.get("Вбудовані функції", "Встроенные функции", "Built-in features"),
                MiogramLocale.get("Перемикачі функцій, що входять до Yumigram", "Переключатели функций, встроенных в Yumigram", "Manage features shipped inside Yumigram")));
        items.add(YumiSettingCell.Factory.of(ROW_NEKO_GENERAL, R.drawable.msg_settings, 0xFF546E7A,
                MiogramLocale.get("Загальні", "Общие", "General"),
                MiogramLocale.get("Мова, поведінка та параметри застосунку", "Язык, поведение и параметры приложения", "Language, behavior and app options")));
        items.add(YumiSettingCell.Factory.of(ROW_NEKO_CHAT, R.drawable.msg_message, 0xFF039BE5,
                MiogramLocale.get("Чати", "Чаты", "Chats"),
                MiogramLocale.get("Налаштування чатів та повідомлень", "Настройки чатов и сообщений", "Chat and message options")));
        items.add(YumiSettingCell.Factory.of(ROW_NEKO_TRANSLATOR, R.drawable.ic_translate, 0xFF00897B,
                MiogramLocale.get("Перекладач", "Переводчик", "Translator"),
                MiogramLocale.get("Мови, сервіси та поведінка перекладу", "Языки, сервисы и поведение перевода", "Languages, providers and translation behavior")));
        items.add(YumiSettingCell.Factory.of(ROW_NEKO_PASSCODE, R.drawable.msg_permissions, 0xFF5E35B1,
                MiogramLocale.get("Код-пароль", "Код-пароль", "Passcode"),
                MiogramLocale.get("Блокування, PIN і подвійне дно (реальний + дурний код)", "Блокировка, PIN и двойное дно (реальный + ложный код)", "Lock, PIN & double bottom (real + duress code)")));
        items.add(YumiSettingCell.Factory.of(ROW_NEKO_EXPERIMENTAL, R.drawable.msg_fave, 0xFF43A047,
                MiogramLocale.get("Експериментальні функції", "Экспериментальные функции", "Experimental features"),
                MiogramLocale.get("Додаткові можливості клієнта", "Дополнительные возможности клиента", "Additional client features")));

        items.add(UItem.asShadow(null));
        items.add(UItem.asHeader(MiogramLocale.get("Оформлення, приватність та інструменти", "Оформление, приватность и инструменты", "Appearance, privacy & tools")));
        items.add(YumiSettingCell.Factory.of(ROW_MIO_VISUALS, R.drawable.msg_theme, 0xFFE91E63,
                MiogramLocale.get("Оформлення та інтерфейс", "Оформление и интерфейс", "Appearance & interface"),
                MiogramLocale.get("Вигляд клієнта та елементи інтерфейсу", "Вид клиента и элементы интерфейса", "Client look and interface elements")));
        items.add(YumiSettingCell.Factory.of(ROW_MIO_CHATS, R.drawable.msg_message, 0xFF039BE5,
                MiogramLocale.get("Додаткові налаштування чатів", "Дополнительные настройки чатов", "More chat options"),
                MiogramLocale.get("Жести, панелі та вигляд списку чатів", "Жесты, панели и вид списка чатов", "Gestures, panels and chat list layout")));
        items.add(YumiSettingCell.Factory.of(ROW_MIO_PRIVACY, R.drawable.msg_secret, 0xFF7E57C2,
                MiogramLocale.get("Приватність Yumigram", "Приватность Yumigram", "Yumigram privacy"),
                MiogramLocale.get("Додаткові параметри приватності", "Дополнительные параметры приватности", "Additional privacy controls")));
        items.add(YumiSettingCell.Factory.of(ROW_MIO_COMPANION, R.drawable.baseline_stars_24, 0xFF8B5CF6,
                MiogramLocale.get("ШІ-компаньйон", "ИИ-компаньон", "AI companion"),
                MiogramLocale.get("Персона, пам'ять та поведінка супутника", "Персона, память и поведение компаньона", "Persona, memory and companion behavior")));
        items.add(YumiSettingCell.Factory.of(ROW_MIO_AI, R.drawable.msg_bot, 0xFF5C6BC0,
                MiogramLocale.get("Провайдери та моделі ШІ", "Провайдеры и модели ИИ", "AI providers & models"),
                MiogramLocale.get("Ключі API, сервіси та моделі", "Ключи API, сервисы и модели", "API keys, providers and models")));
        items.add(YumiSettingCell.Factory.of(ROW_MIO_VAULT, R.drawable.msg_saved, 0xFFE5486B,
                MiogramLocale.get("Хмарне сховище Yumigram", "Облачное хранилище Yumigram", "Yumigram cloud vault"),
                MiogramLocale.get("Стан сховища та керування файлами", "Состояние хранилища и управление файлами", "Vault status and file management")));
        items.add(YumiSettingCell.Factory.of(ROW_MIO_PLAYER, R.drawable.baseline_music_note_24, 0xFF00ACC1,
                MiogramLocale.get("Налаштування MD3-плеєра", "Настройки MD3-плеера", "MD3 player settings"),
                MiogramLocale.get("Тексти, черга та вигляд музичного плеєра", "Тексты, очередь и вид музыкального плеера", "Lyrics, queue and music player appearance")));
        items.add(YumiSettingCell.Factory.of(ROW_MIO_BADGES, R.drawable.msg_premium_normal, 0xFFE5486B,
                MiogramLocale.get("Бейджі та стрілки", "Бейджи и стрелки", "Badges & arrows"),
                MiogramLocale.get("Стилі та значки профілю", "Стили и значки профиля", "Profile badges and styles")));
        items.add(YumiSettingCell.Factory.of(ROW_MIO_MUSIC, R.drawable.baseline_music_note_24, 0xFF43A047,
                MiogramLocale.get("Підключені музичні сервіси", "Подключённые музыкальные сервисы", "Connected music services"),
                MiogramLocale.get("Spotify та інтеграції присутності", "Spotify и интеграции присутствия", "Spotify and presence integrations")));
        items.add(YumiSettingCell.Factory.of(ROW_MIO_TIKTOK, R.drawable.msg_video, 0xFFEE1D52,
                MiogramLocale.get("Екосистема TikTok MI", "Экосистема TikTok MI", "TikTok MI ecosystem"),
                MiogramLocale.get("Підключення акаунта та інтеграція", "Подключение аккаунта и интеграция", "Account linking and integration")));
        items.add(YumiSettingCell.Factory.of(ROW_MIO_PUSH, R.drawable.msg_notifications_solar, 0xFF039BE5,
                MiogramLocale.get("Сповіщення та робота у фоні", "Уведомления и работа в фоне", "Notifications & background"),
                app.miogram.bridge.push.MiogramPushSheet.getShortStatus()));
        items.add(YumiSettingCell.Factory.of(ROW_MIO_HISTORY, R.drawable.msg_delete, 0xFF78909C,
                MiogramLocale.get("Історія повідомлень", "История сообщений", "Message history"),
                MiogramLocale.get("Перегляд збережених змін повідомлень", "Просмотр сохранённых изменений сообщений", "Review saved message changes")));
        items.add(YumiSettingCell.Factory.of(ROW_MIO_AUTOMATION, R.drawable.msg_contacts, 0xFF607D8B,
                MiogramLocale.get("Автоматизація акаунта", "Автоматизация аккаунта", "Account automation"),
                MiogramLocale.get("Синхронізація, резервні копії та обслуговування", "Синхронизация, резервные копии и обслуживание", "Sync, backups and maintenance")));

        items.add(UItem.asShadow(null));
        items.add(UItem.asHeader(MiogramLocale.get("Інші параметри клієнта", "Другие параметры клиента", "Additional client options")));
        items.add(YumiSettingCell.Factory.of(ROW_EXTERA_GENERAL, R.drawable.msg_media, 0xFF546E7A,
                MiogramLocale.get("Загальні функції", "Общие функции", "General options"), ""));
        items.add(YumiSettingCell.Factory.of(ROW_EXTERA_APPEARANCE, R.drawable.msg_theme, 0xFFE91E63,
                MiogramLocale.get("Зовнішній вигляд", "Внешний вид", "Appearance"), ""));
        items.add(YumiSettingCell.Factory.of(ROW_EXTERA_CHATS, R.drawable.msg_discussion, 0xFF039BE5,
                MiogramLocale.get("Налаштування чатів", "Настройки чатов", "Chat options"), ""));
        items.add(YumiSettingCell.Factory.of(ROW_EXTERA_OTHER, R.drawable.msg_fave, 0xFF607D8B,
                MiogramLocale.get("Інші налаштування", "Другие настройки", "Other options"), ""));
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ROW_CLIENT_SETTINGS) {
            presentFragment(new AmegramSettingsActivity(true));
        } else if (item.id == ROW_GHOST) {
            openModule("ghost");
        } else if (item.id == ROW_VAULT) {
            openModule("vault");
        } else if (item.id == ROW_PLAYER) {
            openModule("player");
        } else if (item.id == ROW_AME) {
            openModule("ame");
        } else if (item.id == ROW_TIKTOK) {
            openModule("tiktok");
        } else if (item.id == ROW_AI) {
            openModule("ai");
        } else if (item.id == ROW_EXPERIMENTAL) {
            openModule("experimental");
        } else if (item.id == ROW_STT) {
            openModule("stt");
        } else if (item.id == ROW_AUTOMATION) {
            openModule("automation");
        } else if (item.id == ROW_UI) {
            openModule("ui");
        } else if (item.id == ROW_FILEORG) {
            openModule("fileorganization");
        } else if (item.id == ROW_DEMO) {
            openModule("demo");
        } else if (item.id == ROW_NEKO_GENERAL) {
            presentFragment(new NekoGeneralSettingsActivity());
        } else if (item.id == ROW_NEKO_CHAT) {
            presentFragment(new NekoChatSettingsActivity());
        } else if (item.id == ROW_NEKO_TRANSLATOR) {
            presentFragment(new NekoTranslatorSettingsActivity());
        } else if (item.id == ROW_NEKO_PASSCODE) {
            presentFragment(new NekoPasscodeSettingsActivity());
        } else if (item.id == ROW_NEKO_EXPERIMENTAL) {
            presentFragment(new NekoExperimentalSettingsActivity());
        } else if (item.id == ROW_NATIVE_MODULES) {
            presentFragment(new app.amegram.module.ui.AmegramModulesActivity());
        } else if (item.id == ROW_MIO_VISUALS) {
            presentFragment(new MiogramVisualsActivity());
        } else if (item.id == ROW_MIO_CHATS) {
            presentFragment(new MiogramChatsSettingsActivity());
        } else if (item.id == ROW_MIO_PRIVACY) {
            presentFragment(new MiogramPrivacySettingsActivity());
        } else if (item.id == ROW_MIO_COMPANION) {
            presentFragment(new MiogramCompanionActivity());
        } else if (item.id == ROW_MIO_AI) {
            presentFragment(new MiogramAiSettingsActivity());
        } else if (item.id == ROW_MIO_VAULT) {
            presentFragment(new app.miogram.bridge.cloudvault.MiogramCloudVaultActivity());
        } else if (item.id == ROW_MIO_PLAYER) {
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
            } catch (Throwable ignored) {}
        } else if (item.id == ROW_MIO_BADGES) {
            app.miogram.bridge.badge.MiogramBadgeBottomSheet.show(getParentActivity(),
                    org.telegram.messenger.UserConfig.getInstance(currentAccount).getClientUserId());
        } else if (item.id == ROW_MIO_MUSIC) {
            new app.miogram.bridge.presence.MiogramConnectedAppsSheet(getParentActivity(), null).show();
        } else if (item.id == ROW_MIO_TIKTOK) {
            presentFragment(new app.miogram.bridge.ecosystem.AmegramTikTokSettingsActivity());
        } else if (item.id == ROW_MIO_PUSH) {
            new app.miogram.bridge.push.MiogramPushSheet(getParentActivity(), getResourceProvider()).show();
        } else if (item.id == ROW_MIO_HISTORY) {
            presentFragment(new NekoExperimentalSettingsActivity());
        } else if (item.id == ROW_MIO_AUTOMATION) {
            presentFragment(new app.miogram.bridge.userbot.MiogramHerokuActivity());
        } else if (item.id == ROW_EXTERA_GENERAL) {
            presentFragment(new app.exteraless.settings.OpenExteraGeneralActivity());
        } else if (item.id == ROW_EXTERA_APPEARANCE) {
            presentFragment(new app.exteraless.settings.OpenExteraAppearanceActivity());
        } else if (item.id == ROW_EXTERA_CHATS) {
            presentFragment(new app.exteraless.settings.OpenExteraChatsActivity());
        } else if (item.id == ROW_EXTERA_OTHER) {
            presentFragment(new app.exteraless.settings.OpenExteraOtherActivity());
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
            if (idx >= 0 && idx < customInstalledModules.size()) {
                presentFragment(new HotModuleSettingsActivity(
                        customInstalledModules.get(idx).manifest.id));
            }
        }
    }

    @Override
    public boolean isLightStatusBar() {
        return !Theme.isCurrentThemeDark();
    }
}
