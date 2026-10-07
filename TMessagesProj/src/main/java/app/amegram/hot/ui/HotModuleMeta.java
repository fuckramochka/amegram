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
        return R.drawable.msg_plugins;
    }

    /** Фолбек-категорія, якщо каталог її не вказав. */
    public static String category(String id) {
        if ("ghost".equals(id) || "vault".equals(id)) return "privacy";
        if ("player".equals(id) || "tiktok".equals(id) || "stt".equals(id)) return "media";
        if ("ai".equals(id) || "automation".equals(id) || "experimental".equals(id)) return "power";
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
        return "AmeGram Team";
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
        res.add(new Pack("privacy", "🥷",
                MiogramLocale.get("Приватність", "Приватность", "Privacy"),
                MiogramLocale.get("Невидимка + сейф + анти-відкликання",
                        "Невидимка + сейф + анти-отзыв", "Ghost + vault + anti-recall"),
                new String[]{"ghost", "vault", "experimental"}));
        res.add(new Pack("media", "🎧",
                MiogramLocale.get("Медіа", "Медиа", "Media"),
                MiogramLocale.get("Плеєр + TikTok + розшифровка голосу",
                        "Плеер + TikTok + расшифровка голоса", "Player + TikTok + voice STT"),
                new String[]{"player", "tiktok", "stt"}));
        res.add(new Pack("power", "⚡",
                MiogramLocale.get("Потужність", "Мощь", "Power"),
                MiogramLocale.get("ШІ-супутник + автоматизація + кастом",
                        "ИИ-спутник + автоматизация + кастом", "AI buddy + automation + custom"),
                new String[]{"ai", "automation", "ame"}));
        return res;
    }

    public static String fallbackDescription(String id) {
        if ("ghost".equals(id)) {
            return MiogramLocale.get(
                    "Приховування прочитання, історій, онлайну та набору тексту",
                    "Скрытие прочитанного, историй, онлайна и набора текста",
                    "Hide read receipts, stories views, online & typing");
        }
        if ("player".equals(id)) {
            return MiogramLocale.get(
                    "Пошук музики з 6 сервісів, кастомний плеєр, візуалізатор, тексти LRC",
                    "Поиск музыки из 6 сервисов, кастомный плеер, визуализатор, тексты LRC",
                    "Search 6 music sources, custom player, visualizer, LRC lyrics");
        }
        if ("ame".equals(id)) {
            return MiogramLocale.get(
                    "Аме-студія: картки профіля, градієнти, скляне розмиття",
                    "Аме-студия: карточки профиля, градиенты, glass blur",
                    "Ame Studio: profile cards, gradients, glass blur");
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
                    "Безліміт закріпів, upload boost, збереження видалених",
                    "Безлимит закрепов, upload boost, сохранение удаленных",
                    "Unlimited pins, upload boost, save deleted");
        }
        if ("automation".equals(id)) {
            return MiogramLocale.get(
                    "Автосинхронізація хмари, бекап Обраного, чистка кешу",
                    "Автосинхронизация облака, бэкап Избранного, чистка кэша",
                    "Cloud auto-sync, backup & cache cleaner");
        }
        return MiogramLocale.get(
                "Нативне розширення клієнта Amegram",
                "Нативное расширение клиента Amegram",
                "Native Amegram extension");
    }
}
