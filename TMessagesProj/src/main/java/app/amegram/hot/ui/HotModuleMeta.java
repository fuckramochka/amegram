package app.amegram.hot.ui;

import org.telegram.messenger.R;

import java.util.ArrayList;
import java.util.List;

import app.miogram.bridge.MiogramLocale;

/** Спільна візуальна мета модулів: колір, іконка, категорія, автор, дозволи, паки. */
public final class HotModuleMeta {

    private HotModuleMeta() {
    }

    public static int color(String id) {
        if ("ghost".equals(id)) return 0xFF8E24AA;
        if ("player".equals(id)) return 0xFF00ACC1;
        if ("ame".equals(id)) return 0xFFE91E63;
        if ("vault".equals(id)) return 0xFFFF8F00;
        if ("tiktok".equals(id)) return 0xFFEE1D52;
        if ("stt".equals(id)) return 0xFF00897B;
        if ("ai".equals(id)) return 0xFF7E57C2;
        if ("experimental".equals(id)) return 0xFF43A047;
        if ("automation".equals(id)) return 0xFF546E7A;
        if ("demo".equals(id)) return 0xFF78909C;
        if ("md3player".equals(id)) return 0xFF7E57C2;
        if ("fileorganization".equals(id)) return 0xFF00897B;
        if ("ui".equals(id)) return 0xFF6C63FF;
        return 0xFF2A87FF;
    }

    public static int icon(String id) {
        if ("ghost".equals(id)) return R.drawable.msg_secret;
        if ("player".equals(id)) return R.drawable.baseline_music_note_24;
        if ("ame".equals(id)) return R.drawable.msg_customize;
        if ("vault".equals(id)) return R.drawable.msg_saved;
        if ("tiktok".equals(id)) return R.drawable.msg_video;
        if ("stt".equals(id)) return R.drawable.msg_bot;
        if ("ai".equals(id)) return R.drawable.baseline_stars_24;
        if ("experimental".equals(id)) return R.drawable.msg_fave;
        if ("automation".equals(id)) return R.drawable.msg_download_solar;
        if ("md3player".equals(id)) return R.drawable.baseline_music_note_24;
        if ("fileorganization".equals(id)) return R.drawable.msg_download_solar;
        if ("ui".equals(id)) return R.drawable.msg_theme;
        return R.drawable.msg_plugins;
    }

    /** Фолбек-категорія, якщо каталог її не вказав. */
    public static String category(String id) {
        if ("ghost".equals(id) || "vault".equals(id)) return "privacy";
        if ("player".equals(id) || "tiktok".equals(id) || "stt".equals(id)) return "media";
        if ("ai".equals(id) || "automation".equals(id) || "experimental".equals(id)) return "power";
        if ("ui".equals(id)) return "custom";
        if ("fileorganization".equals(id)) return "power";
        if ("md3player".equals(id)) return "media";
        if ("ame".equals(id)) return "custom";
        return "other";
    }

    public static String categoryTitle(String category) {
        if ("privacy".equals(category)) return MiogramLocale.get("Приватність", "Приватность", "Privacy");
        if ("media".equals(category)) return MiogramLocale.get("Медіа", "Медиа", "Media");
        if ("power".equals(category)) return MiogramLocale.get("Потужність", "Мощь", "Power");
        if ("custom".equals(category)) return MiogramLocale.get("Кастом", "Кастом", "Custom");
        return MiogramLocale.get("Інше", "Другое", "Other");
    }

    public static String author(String id, String fromCatalog) {
        if (fromCatalog != null && !fromCatalog.isEmpty()) return fromCatalog;
        return "Yumi Team";
    }

    /** Людський опис дозволу для екрана згоди перед установкою. */
    public static String permissionTitle(String perm) {
        if ("hook_net".equals(perm)) return MiogramLocale.get("Доступ до мережевого шару", "Доступ к сетевому слою", "Network layer hooks");
        if ("hook_ui".equals(perm)) return MiogramLocale.get("Зміна інтерфейсу", "Изменение интерфейса", "UI modifications");
        if ("storage".equals(perm)) return MiogramLocale.get("Файли і кеш", "Файлы и кэш", "Files & cache");
        if ("network".equals(perm)) return MiogramLocale.get("Інтернет", "Интернет", "Internet");
        if ("background".equals(perm)) return MiogramLocale.get("Фонова робота", "Фоновая работа", "Background work");
        return perm;
    }

    public static String permissionHint(String perm) {
        if ("hook_net".equals(perm)) return MiogramLocale.get("Перехоплює статуси online/typing/прочитань",
                "Перехватывает статусы online/typing/прочтений", "Intercepts online/typing/read states");
        if ("hook_ui".equals(perm)) return MiogramLocale.get("Додає екрани і кнопки в клієнт",
                "Добавляет экраны и кнопки в клиент", "Adds screens & buttons to the client");
        if ("storage".equals(perm)) return MiogramLocale.get("Зберігає кеш і файли модуля",
                "Хранит кэш и файлы модуля", "Stores module cache & files");
        if ("network".equals(perm)) return MiogramLocale.get("Запити до серверів модуля",
                "Запросы к серверам модуля", "Requests to module servers");
        if ("background".equals(perm)) return MiogramLocale.get("Періодичні фонові задачі",
                "Периодические фоновые задачи", "Periodic background tasks");
        return "";
    }

    /** Фолбек-дозволи, якщо каталог їх не вказав для збірки. */
    public static List<String> fallbackPermissions(String id) {
        List<String> res = new ArrayList<>();
        if ("ghost".equals(id)) {
            res.add("hook_net");
        } else if ("player".equals(id)) {
            res.add("hook_ui");
            res.add("network");
            res.add("storage");
        } else if ("vault".equals(id)) {
            res.add("storage");
            res.add("network");
        } else if ("tiktok".equals(id)) {
            res.add("network");
            res.add("storage");
        } else if ("stt".equals(id) || "ai".equals(id)) {
            res.add("network");
        } else if ("ame".equals(id)) {
            res.add("hook_ui");
            res.add("storage");
        } else if ("automation".equals(id)) {
            res.add("background");
            res.add("storage");
        } else if ("md3player".equals(id)) {
            res.add("hook_ui");
        } else if ("fileorganization".equals(id)) {
            res.add("storage");
        } else if ("ui".equals(id)) {
            res.add("hook_ui");
        }
        return res;
    }

    // ---------- паки для онбордингу (установка набором в 1 тап) ----------

    public static final class Pack {
        public final String id;
        public final String emoji;
        public final String title;
        public final String desc;
        public final String[] modules;

        Pack(String id, String emoji, String title, String desc, String[] modules) {
            this.id = id;
            this.emoji = emoji;
            this.title = title;
            this.desc = desc;
            this.modules = modules;
        }
    }

    public static List<Pack> packs() {
        List<Pack> res = new ArrayList<>();
        res.add(new Pack("privacy", "",
                MiogramLocale.get("Приватність", "Приватность", "Privacy"),
                MiogramLocale.get("Невидимка + сейф",
                        "Невидимка + сейф", "Ghost + vault"),
                new String[]{"ghost", "vault"}));
        res.add(new Pack("media", "",
                MiogramLocale.get("Медіа", "Медиа", "Media"),
                MiogramLocale.get("Плеєр + TikTok + розшифровка голосу",
                        "Плеер + TikTok + расшифровка голоса", "Player + TikTok + voice STT"),
                new String[]{"player", "tiktok", "stt"}));
        res.add(new Pack("power", "",
                MiogramLocale.get("Потужність", "Мощь", "Power"),
                MiogramLocale.get("ШІ-супутник + автоматизація + кастом + папки + інтерфейс",
                        "ИИ-спутник + автоматизация + кастом + папки + интерфейс", "AI buddy + automation + custom + folders + interface"),
                new String[]{"ai", "automation", "ame", "fileorganization", "ui"}));
        res.add(new Pack("start", "",
                MiogramLocale.get("Старт", "Старт", "Start"),
                MiogramLocale.get("Привид + плеєр + ШІ для швидкого старту",
                        "Призрак + плеер + ИИ для быстрого старта", "Ghost + player + AI for quick start"),
                new String[]{"ghost", "player", "ai"}));
        return res;
    }

    public static String fallbackDescription(String id) {
        if ("ghost".equals(id)) {
            return MiogramLocale.get(
                    "Приховування прочитання, історій, онлайну, набору + анти-видалення",
                    "Скрытие прочитанного, историй, онлайна, набора + анти-удаление",
                    "Hide read receipts, stories views, online, typing + anti-delete");
        }
        if ("player".equals(id)) {
            return MiogramLocale.get(
                    "Пошук музики з 6 сервісів, кастомний плеєр, візуалізатор, тексти LRC + пошук текстів тут",
                    "Поиск музыки из 6 сервисов, кастомный плеер, визуализатор, тексты LRC + поиск текстов тут",
                    "Search 6 music sources, custom player, visualizer, LRC lyrics + find lyrics here");
        }
        if ("ame".equals(id)) {
            return MiogramLocale.get(
                    "Yumi-студія: картки профіля, градієнти, скляне розмиття",
                    "Yumi-студия: карточки профиля, градиенты, glass blur",
                    "Yumi Studio: profile cards, gradients, glass blur");
        }
        if ("vault".equals(id)) {
            return MiogramLocale.get(
                    "Шифрований AES-256-GCM диск з автонарізанням на чанки",
                    "Зашифрованный AES-256-GCM диск с чанкованием",
                    "Encrypted AES-256-GCM drive with chunking");
        }
        if ("tiktok".equals(id)) {
            return MiogramLocale.get(
                    "Плеєр без ватермарок, чисті URL, синхронізація акаунта",
                    "Плеер без ватермарок, чистые URL и синхронизация",
                    "Watermark-free player, clean URLs & sync");
        }
        if ("stt".equals(id)) {
            return MiogramLocale.get(
                    "ШІ-транскрипція голосових через Gemini або Whisper",
                    "ИИ-транскрибация голосовых через Gemini или Whisper",
                    "AI transcription via Gemini or Whisper");
        }
        if ("ai".equals(id)) {
            return MiogramLocale.get(
                    "ШІ-супутник Ame / KAngel з пам'яттю та чатом",
                    "ИИ-спутник Ame / KAngel с памятью и чатом",
                    "AI companion Ame / KAngel with memory & chat");
        }
        if ("experimental".equals(id)) {
            return MiogramLocale.get(
                    "Безліміт закріпів, upload boost, жести",
                    "Безлимит закрепов, upload boost, жесты",
                    "Unlimited pins, upload boost, gestures");
        }
        if ("automation".equals(id)) {
            return MiogramLocale.get(
                    "Автосинхронізація хмари, бекап Обраного, чистка кешу",
                    "Автосинхронизация облака, бэкап Избранного, чистка кэша",
                    "Cloud auto-sync, backup & cache cleaner");
        }
        if ("md3player".equals(id)) {
            return MiogramLocale.get(
                    "MD3-шит і міні-бар плеєра з лірикою та morph-переходом",
                    "MD3-шит и мини-бар плеера с лирикой и morph-переходом",
                    "MD3 player sheet & mini bar with lyrics and morph transition");
        }
        if ("fileorganization".equals(id)) {
            return MiogramLocale.get(
                    "Підпапки чатів + збереження по папках",
                    "Подпапки чатов + сохранение по папкам",
                    "Chat subfolders + save by folders");
        }
        if ("ui".equals(id)) {
            return MiogramLocale.get(
                    "Yougram Expressive ↔ Classic на льоту: скло, M3-сегменти, радіуси",
                    "Yougram Expressive ↔ Classic на лету: стекло, M3-сегменты, радиусы",
                    "Yougram Expressive ↔ Classic live: glass, M3 segments, radii");
        }
        return MiogramLocale.get(
                "Нативне розширення клієнта Yumigram",
                "Нативное расширение клиента Yumigram",
                "Native Yumigram extension");
    }
}
