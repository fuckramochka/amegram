package app.miogram.bridge.userbot;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import app.exteraless.plugins.PythonPluginsEngine;
import app.miogram.bridge.MiogramLocale;
import app.miogram.bridge.hooks.MioHook;

/**
 * Native Heroku Userbot Module Manager for Miogram.
 * Supports executing Telegram userbot commands (.ping, .help, .eval, .purge, .del, .afk, .user, etc.),
 * managing Bot API helper tokens from @BotFather for inline interactive units,
 * and loading custom Heroku/Hikka/FTG Python modules (.py).
 */
public class MiogramHerokuManager {

    private static volatile MiogramHerokuManager instance;

    public static MiogramHerokuManager getInstance() {
        if (instance == null) {
            synchronized (MiogramHerokuManager.class) {
                if (instance == null) {
                    instance = new MiogramHerokuManager();
                }
            }
        }
        return instance;
    }

    private static final String PREFS_NAME = "miogram_heroku_userbot_prefs";
    private static final String KEY_ENABLED = "userbot_enabled";
    private static final String KEY_PREFIX = "userbot_prefix";
    private static final String KEY_BOT_TOKEN = "userbot_bot_token";
    private static final String KEY_BOT_USERNAME = "userbot_bot_username";
    private static final String KEY_BOT_NAME = "userbot_bot_name";
    private static final String KEY_BOT_ID = "userbot_bot_id";
    private static final String KEY_INLINE_CAPABLE = "userbot_inline_capable";
    private static final String KEY_IS_AFK = "userbot_is_afk";
    private static final String KEY_AFK_SINCE = "userbot_afk_since";
    private static final String KEY_AFK_REASON = "userbot_afk_reason";

    // AFK runtime state
    private volatile boolean isAfk = false;
    private volatile long afkSince = 0L;
    private volatile String afkReason = "";
    private final Map<Long, Long> afkDebounceMap = new ConcurrentHashMap<>();

    public interface CommandHandler {
        void execute(CommandContext ctx) throws Throwable;
    }

    public static class CommandContext {
        public final int account;
        public final long dialogId;
        public final String command;
        public final String rawArgs;
        public final String fullText;
        public final TLRPC.Message originalMessage;
        public final MessageObject replyMessage;
        public final SendMessagesHelper.SendMessageParams sendParams;

        public CommandContext(int account, long dialogId, String command, String rawArgs, String fullText,
                              TLRPC.Message originalMessage, MessageObject replyMessage,
                              SendMessagesHelper.SendMessageParams sendParams) {
            this.account = account;
            this.dialogId = dialogId;
            this.command = command;
            this.rawArgs = rawArgs != null ? rawArgs.trim() : "";
            this.fullText = fullText;
            this.originalMessage = originalMessage;
            this.replyMessage = replyMessage;
            this.sendParams = sendParams;
        }

        public void answer(String text) {
            answer(text, true);
        }

        public void answer(String text, boolean parseMarkdown) {
            if (TextUtils.isEmpty(text)) return;
            FormattedMessage formatted = parseMarkdown
                    ? MiogramHerokuManager.formatUserbotMessage(text)
                    : new FormattedMessage(text, null);
            if (formatted == null || TextUtils.isEmpty(formatted.text)) return;
            final String plain = formatted.text;
            final ArrayList<TLRPC.MessageEntity> entities = formatted.entities;
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    if (replyMessage != null) {
                        SendMessagesHelper.getInstance(account).sendMessage(
                                SendMessagesHelper.SendMessageParams.of(
                                        plain, dialogId, replyMessage, null, null, true, entities, null, null, true, 0, 0, null, false
                                )
                        );
                    } else if (entities != null && !entities.isEmpty()) {
                        SendMessagesHelper.getInstance(account).sendMessage(
                                SendMessagesHelper.SendMessageParams.of(
                                        plain, dialogId, null, null, null, true, entities, null, null, true, 0, 0, null, false
                                )
                        );
                    } else {
                        SendMessagesHelper.getInstance(account).sendMessage(
                                SendMessagesHelper.SendMessageParams.of(plain, dialogId)
                        );
                    }
                } catch (Throwable t) {
                    FileLog.e(t);
                }
            });
        }

        public void deleteRepliedMessage() {
            if (replyMessage == null) return;
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    ArrayList<Integer> ids = new ArrayList<>();
                    ids.add(replyMessage.getId());
                    MessagesController.getInstance(account).deleteMessages(ids, null, null, dialogId, 0, true, 0);
                } catch (Throwable t) {
                    FileLog.e(t);
                }
            });
        }

        public void deleteMessages(List<Integer> msgIds) {
            if (msgIds == null || msgIds.isEmpty()) return;
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    ArrayList<Integer> ids = new ArrayList<>(msgIds);
                    MessagesController.getInstance(account).deleteMessages(ids, null, null, dialogId, 0, true, 0);
                } catch (Throwable t) {
                    FileLog.e(t);
                }
            });
        }

        public void editRepliedMessage(String newText) {
            if (replyMessage == null || TextUtils.isEmpty(newText)) return;
            FormattedMessage formatted = MiogramHerokuManager.formatUserbotMessage(newText);
            String plain = formatted != null ? formatted.text : newText;
            ArrayList<TLRPC.MessageEntity> entities = formatted != null ? formatted.entities : null;
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    SendMessagesHelper.getInstance(account).editMessage(replyMessage, plain, true, null, entities, 0, 0);
                } catch (Throwable t) {
                    FileLog.e(t);
                }
            });
        }
    }

    public static class UserbotModuleInfo {
        public final String name;
        public final String description;
        public final String version;
        public final String author;
        public final boolean isBuiltin;
        public boolean isEnabled;
        public final List<String> commands = new ArrayList<>();

        public UserbotModuleInfo(String name, String description, String version, String author, boolean isBuiltin) {
            this.name = name;
            this.description = description;
            this.version = version;
            this.author = author;
            this.isBuiltin = isBuiltin;
            this.isEnabled = true;
        }
    }

    /** Plain text + entities parsed from userbot markdown/HTML (Hikka-style). */
    public static class FormattedMessage {
        public final String text;
        public final ArrayList<TLRPC.MessageEntity> entities;

        public FormattedMessage(String text, ArrayList<TLRPC.MessageEntity> entities) {
            this.text = text;
            this.entities = entities;
        }
    }

    /**
     * Converts Hikka/FTG-style formatting to Telegram entities.
     */
    public static FormattedMessage formatUserbotMessage(String raw) {
        if (raw == null) return null;
        String text = raw;
        try {
            text = text.replaceAll("(?i)<br\\s*/?>", "\n");
            java.util.regex.Matcher linkMatcher = java.util.regex.Pattern.compile(
                    "(?i)<a\\s+[^>]*href\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</a\\s*>",
                    java.util.regex.Pattern.DOTALL).matcher(text);
            StringBuffer linkBuf = new StringBuffer();
            while (linkMatcher.find()) {
                String url = linkMatcher.group(1);
                String label = linkMatcher.group(2).replaceAll("<[^>]+>", "");
                linkMatcher.appendReplacement(linkBuf, java.util.regex.Matcher.quoteReplacement("[" + label + "](" + url + ")"));
            }
            linkMatcher.appendTail(linkBuf);
            text = linkBuf.toString();
            text = text.replaceAll("(?i)</?\\s*(b|strong)\\s*>", "**");
            text = text.replaceAll("(?i)</?\\s*(i|em)\\s*>", "__");
            text = text.replaceAll("(?i)</?\\s*(s|strike|del)\\s*>", "~~");
            text = text.replaceAll("(?i)</?\\s*pre\\s*>", "```");
            text = text.replaceAll("(?i)</?\\s*code\\s*>", "`");
            text = text.replaceAll("(?i)</?\\s*u\\s*>", "");
            text = text.replaceAll("(?i)</?\\s*(spoiler|tg-spoiler)\\s*>", "||");
            text = text.replaceAll("<[^>]{0,256}>", "");
            text = text.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                    .replace("&#39;", "'").replace("&amp;", "&");
        } catch (Throwable ignore) {
            text = raw;
        }
        ArrayList<TLRPC.MessageEntity> entities = null;
        try {
            CharSequence[] message = new CharSequence[]{text};
            entities = org.telegram.messenger.MediaDataController.getInstance(
                    org.telegram.messenger.UserConfig.selectedAccount).getEntities(message, true);
            if (message[0] != null) text = message[0].toString();
        } catch (Throwable t) {
            FileLog.e(t);
        }
        return new FormattedMessage(text, entities);
    }

    private final Map<String, CommandHandler> commandHandlers = new ConcurrentHashMap<>();
    private final Map<String, UserbotModuleInfo> modules = new LinkedHashMap<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "miogram-userbot"));
    private boolean initialized = false;

    private MiogramHerokuManager() {
    }

    public void init() {
        if (initialized) return;
        initialized = true;

        // Restore AFK state
        isAfk = prefs().getBoolean(KEY_IS_AFK, false);
        afkSince = prefs().getLong(KEY_AFK_SINCE, 0L);
        afkReason = prefs().getString(KEY_AFK_REASON, "");

        // Built-ins registration
        registerBuiltinModules();

        // Wire into MioHook pre-send bus for AFK auto-cancel
        MioHook.onPreSend("miogram_heroku_userbot", "userbot_interceptor", 999, (dialogId, text) -> {
            if (!isEnabled()) {
                return true;
            }
            if (isAfk && !isAfkCommand(text)) {
                cancelAfk(UserConfig.selectedAccount, dialogId);
            }
            return true;
        });

        // Wire into MioHook message bus for AFK auto-responder
        MioHook.onMessage("miogram_heroku_userbot", "afk_responder", 100, (account, message) -> {
            handleIncomingAfkMessage(account, message);
        });

        // External .py discovery
        executor.execute(() -> {
            try {
                scanExternalModules();
            } catch (Throwable t) {
                FileLog.e(t);
            }
        });
    }

    private SharedPreferences prefs() {
        Context ctx = ApplicationLoader.applicationContext;
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isEnabled() {
        return prefs().getBoolean(KEY_ENABLED, true);
    }

    public void setEnabled(boolean enabled) {
        prefs().edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public String getPrefix() {
        return prefs().getString(KEY_PREFIX, ".");
    }

    public void setPrefix(String prefix) {
        if (TextUtils.isEmpty(prefix)) prefix = ".";
        prefs().edit().putString(KEY_PREFIX, prefix).apply();
    }

    public String getBotToken() {
        return prefs().getString(KEY_BOT_TOKEN, "");
    }

    public String getBotUsername() {
        return prefs().getString(KEY_BOT_USERNAME, "");
    }

    public String getBotName() {
        return prefs().getString(KEY_BOT_NAME, "");
    }

    public boolean isInlineCapable() {
        return prefs().getBoolean(KEY_INLINE_CAPABLE, false);
    }

    public boolean hasConfiguredBot() {
        return !TextUtils.isEmpty(getBotToken());
    }

    public void setBotInfo(String token, String username, String name, long id, boolean inlineCapable) {
        prefs().edit()
                .putString(KEY_BOT_TOKEN, token != null ? token.trim() : "")
                .putString(KEY_BOT_USERNAME, username != null ? username.trim() : "")
                .putString(KEY_BOT_NAME, name != null ? name.trim() : "")
                .putLong(KEY_BOT_ID, id)
                .putBoolean(KEY_INLINE_CAPABLE, inlineCapable)
                .apply();
    }

    public void clearBotInfo() {
        prefs().edit()
                .remove(KEY_BOT_TOKEN)
                .remove(KEY_BOT_USERNAME)
                .remove(KEY_BOT_NAME)
                .remove(KEY_BOT_ID)
                .remove(KEY_INLINE_CAPABLE)
                .apply();
    }

    public interface TextFilter {
        String filter(String text);
    }

    private final Map<String, TextFilter> activeTextFilters = new ConcurrentHashMap<>();

    public void registerTextFilter(String id, TextFilter filter) {
        if (filter != null) activeTextFilters.put(id, filter);
    }

    public void unregisterTextFilter(String id) {
        activeTextFilters.remove(id);
    }

    public String filterOutgoingText(String text) {
        if (!isEnabled() || TextUtils.isEmpty(text) || activeTextFilters.isEmpty()) {
            return text;
        }
        String current = text;
        for (TextFilter filter : activeTextFilters.values()) {
            try {
                String modified = filter.filter(current);
                if (modified != null) {
                    current = modified;
                }
            } catch (Throwable t) {
                FileLog.e(t);
            }
        }
        return current;
    }

    public boolean installModule(File sourceFile) {
        if (sourceFile == null || !sourceFile.exists()) return false;
        try {
            File target = new File(getUserbotModulesDir(), sourceFile.getName());
            if (!sourceFile.getAbsolutePath().equals(target.getAbsolutePath())) {
                try (java.io.InputStream in = new java.io.FileInputStream(sourceFile);
                     java.io.OutputStream out = new java.io.FileOutputStream(target)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = in.read(buf)) > 0) {
                        out.write(buf, 0, len);
                    }
                    out.flush();
                }
            }
            return loadExternalPythonModule(target);
        } catch (Throwable t) {
            FileLog.e(t);
            return false;
        }
    }

    public boolean installModuleFromCode(String name, String code) {
        if (TextUtils.isEmpty(code)) return false;
        try {
            String baseName = (name != null && !name.trim().isEmpty() ? name.trim() : "module_" + System.currentTimeMillis()).replace(" ", "_");
            final String safeName = baseName.endsWith(".py") ? baseName : baseName + ".py";
            File target = new File(getUserbotModulesDir(), safeName);
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(target)) {
                fos.write(code.getBytes(StandardCharsets.UTF_8));
                fos.flush();
            }
            if (code.contains("filter_outgoing")) {
                final String modulePath = target.getAbsolutePath();
                registerTextFilter(safeName, text -> runModuleFilter(modulePath, text));
            }
            return loadExternalPythonModule(target);
        } catch (Throwable t) {
            FileLog.e(t);
            return false;
        }
    }

    public boolean installLuaPlugin(String name, String code) {
        if (TextUtils.isEmpty(code)) return false;
        try {
            String baseName = (name != null && !name.trim().isEmpty() ? name.trim() : "lua_" + System.currentTimeMillis()).replace(" ", "_");
            final String safeName = baseName.endsWith(".lua") ? baseName : baseName + ".lua";
            File dir = new File(ApplicationLoader.applicationContext.getFilesDir(), "plugins");
            if (!dir.exists()) dir.mkdirs();
            File target = new File(dir, safeName);
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(target)) {
                fos.write(code.getBytes(StandardCharsets.UTF_8));
                fos.flush();
            }
            if (code.contains("on_send_message") || code.contains("dot") || code.contains("%1.")) {
                registerTextFilter(safeName, text -> {
                    if (text == null || text.startsWith(getPrefix())) return text;
                    if (code.contains("dot") || safeName.contains("dot") || code.contains("%1.")) {
                        return text.replaceAll("(\\p{L}+)(?!\\.)", "$1.");
                    }
                    return text;
                });
            }
            UserbotModuleInfo luaMod = new UserbotModuleInfo(safeName.replace(".lua", ""), "Lua Plugin (" + safeName + ")", "1.0.0", "Lua", false);
            modules.put(luaMod.name, luaMod);
            return true;
        } catch (Throwable t) {
            FileLog.e(t);
            return false;
        }
    }

    public boolean isUserbotCommand(String text) {
        if (TextUtils.isEmpty(text)) return false;
        String prefix = getPrefix();
        if (!text.startsWith(prefix)) return false;
        String body = text.substring(prefix.length()).trim();
        if (body.isEmpty()) return false;
        String cmd = body.split("\\s+")[0].toLowerCase(Locale.ROOT);
        return commandHandlers.containsKey(cmd);
    }

    public boolean interceptOutgoingMessage(int account, SendMessagesHelper.SendMessageParams params) {
        if (!isEnabled() || params == null || TextUtils.isEmpty(params.message)) {
            return false;
        }
        if (isAfk && !isAfkCommand(params.message)) {
            cancelAfk(account, params.peer);
        }
        if (isUserbotCommand(params.message)) {
            dispatchCommand(account, params.peer, params.message, params.replyToMsg, params);
            return true;
        }
        String filtered = filterOutgoingText(params.message);
        if (filtered != null && !filtered.equals(params.message)) {
            params.message = filtered;
        }
        return false;
    }

    public void dispatchCommand(int account, long dialogId, String text, MessageObject replyMsg,
                                SendMessagesHelper.SendMessageParams params) {
        String prefix = getPrefix();
        if (!text.startsWith(prefix)) return;
        String stripped = text.substring(prefix.length()).trim();
        String[] parts = stripped.split("\\s+", 2);
        String cmd = parts[0].toLowerCase(Locale.ROOT);
        String args = parts.length > 1 ? parts[1] : "";

        CommandHandler handler = commandHandlers.get(cmd);
        if (handler == null) return;

        executor.execute(() -> {
            try {
                CommandContext ctx = new CommandContext(account, dialogId, cmd, args, text, null, replyMsg, params);
                handler.execute(ctx);
            } catch (Throwable t) {
                FileLog.e(t);
                AndroidUtilities.runOnUIThread(() -> {
                    try {
                        String errMsg = "⚠️ **Userbot Error in ." + cmd + ":**\n`" + t.getMessage() + "`";
                        SendMessagesHelper.getInstance(account).sendMessage(
                                SendMessagesHelper.SendMessageParams.of(errMsg, dialogId)
                        );
                    } catch (Throwable ignore) {}
                });
            }
        });
    }

    public List<UserbotModuleInfo> getModules() {
        return new ArrayList<>(modules.values());
    }

    public List<String> getAvailableCommands() {
        List<String> list = new ArrayList<>(commandHandlers.keySet());
        Collections.sort(list);
        return list;
    }

    public File getUserbotModulesDir() {
        File dir = new File(ApplicationLoader.applicationContext.getFilesDir(), "userbot_modules");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    // =========================================================================
    // AFK Lifecycle
    // =========================================================================

    private boolean isAfkCommand(String text) {
        if (TextUtils.isEmpty(text)) return false;
        String prefix = getPrefix();
        return text.startsWith(prefix + "afk") || text.startsWith(prefix + "unafk");
    }

    private void cancelAfk(int account, long dialogId) {
        if (!isAfk) return;
        long durationMs = Math.max(0, System.currentTimeMillis() - afkSince);
        isAfk = false;
        prefs().edit().putBoolean(KEY_IS_AFK, false).remove(KEY_AFK_SINCE).remove(KEY_AFK_REASON).apply();
        String dur = formatDuration(durationMs);
        String backMsg = "☀️ " + MiogramLocale.get(
                "**Я повернувся з AFK!**\n⏱ Був відсутній: `" + dur + "`",
                "**Я вернулся из AFK!**\n⏱ Отсутствовал: `" + dur + "`",
                "**I'm back from AFK!**\n⏱ Was away for: `" + dur + "`"
        );
        AndroidUtilities.runOnUIThread(() -> {
            try {
                SendMessagesHelper.getInstance(account).sendMessage(
                        SendMessagesHelper.SendMessageParams.of(backMsg, dialogId)
                );
            } catch (Throwable ignore) {}
        });
    }

    private void handleIncomingAfkMessage(int account, MessageObject message) {
        if (!isEnabled() || !isAfk || message == null || message.messageOwner == null) return;
        if (message.isOutOwner()) return;

        long dialogId = message.getDialogId();
        boolean isTargeted = false;
        if (dialogId > 0) {
            isTargeted = true;
        } else {
            long myId = UserConfig.getInstance(account).getClientUserId();
            if (message.isMentioned()) {
                isTargeted = true;
            } else if (message.messageOwner.reply_to != null) {
                if (message.replyMessageObject != null && message.replyMessageObject.getFromChatId() == myId) {
                    isTargeted = true;
                }
            }
        }
        if (!isTargeted) return;

        long now = SystemClock.elapsedRealtime();
        Long last = afkDebounceMap.get(dialogId);
        if (last != null && (now - last) < 60000L) {
            return;
        }
        afkDebounceMap.put(dialogId, now);

        long durationMs = Math.max(0, System.currentTimeMillis() - afkSince);
        String dur = formatDuration(durationMs);
        String reasonStr = TextUtils.isEmpty(afkReason) ? "" : ("\n💬 " + MiogramLocale.get("Причина: `", "Причина: `", "Reason: `") + afkReason + "`");
        String text = "💤 " + MiogramLocale.get(
                "**Користувач зараз в AFK!**\n⏱ Відсутній: `",
                "**Пользователь сейчас в AFK!**\n⏱ Отсутствует: `",
                "**User is currently AFK!**\n⏱ Away for: `"
        ) + dur + "`" + reasonStr;

        FormattedMessage fm = formatUserbotMessage(text);
        AndroidUtilities.runOnUIThread(() -> {
            try {
                SendMessagesHelper.getInstance(account).sendMessage(
                        SendMessagesHelper.SendMessageParams.of(
                                fm.text, dialogId, message, null, null, true, fm.entities, null, null, true, 0, 0, null, false
                        )
                );
            } catch (Throwable t) {
                FileLog.e(t);
            }
        });
    }

    private static String formatDuration(long ms) {
        long sec = (ms / 1000) % 60;
        long min = (ms / (1000 * 60)) % 60;
        long hrs = (ms / (1000 * 60 * 60));
        if (hrs > 0) {
            return String.format(Locale.US, "%dh %dm %ds", hrs, min, sec);
        } else if (min > 0) {
            return String.format(Locale.US, "%dm %ds", min, sec);
        } else {
            return Math.max(1, sec) + "s";
        }
    }

    // =========================================================================
    // Built-in Modules
    // =========================================================================

    private void registerBuiltinModules() {
        // 1. Core Module (ping, help, info, status, id, prefix, restart, reload)
        UserbotModuleInfo core = new UserbotModuleInfo("Core", MiogramLocale.get("Базові системні команди Heroku/FTG", "Базовые системные команды Heroku/FTG", "Heroku/FTG base system commands"), "2.1.0", "Miogram Team", true);

        registerCommand(core, "ping", ctx -> {
            long start = SystemClock.elapsedRealtime();
            long elapsed = Math.max(1, SystemClock.elapsedRealtime() - start);
            String response = "🏓 **Pong!**\n" +
                    MiogramLocale.get("⏱️ Затримка: `", "⏱️ Задержка: `", "⏱️ Latency: `") + elapsed + " ms`\n" +
                    MiogramLocale.get("🪐 Двигун: **Heroku Native (Miogram)**\n", "🪐 Движок: **Heroku Native (Miogram)**\n", "🪐 Engine: **Heroku Native (Miogram)**\n") +
                    "🤖 Helper Bot: " + (hasConfiguredBot() ? "@" + getBotUsername() : MiogramLocale.get("_не налаштовано_", "_не настроен_", "_not configured_"));
            ctx.answer(response);
        });

        registerCommand(core, "help", ctx -> {
            if (!TextUtils.isEmpty(ctx.rawArgs)) {
                String target = ctx.rawArgs.toLowerCase(Locale.ROOT).replace(getPrefix(), "");
                CommandHandler h = commandHandlers.get(target);
                if (h != null) {
                    ctx.answer("ℹ️ " + MiogramLocale.get("**Довідка по команді:** `", "**Справка по команде:** `", "**Help for command:** `") + getPrefix() + target + "`\n" +
                            MiogramLocale.get("Префікс: `", "Префикс: `", "Prefix: `") + getPrefix() + "`\n" +
                            MiogramLocale.get("Модуль: ", "Модуль: ", "Module: ") + findModuleForCommand(target));
                    return;
                }
            }

            StringBuilder sb = new StringBuilder();
            sb.append("🪐 **Heroku Userbot for Miogram**\n");
            sb.append(MiogramLocale.get("Префікс команд: `", "Префикс команд: `", "Command prefix: `")).append(getPrefix()).append("`\n\n");
            for (UserbotModuleInfo m : modules.values()) {
                if (!m.isEnabled) continue;
                sb.append("📦 **").append(m.name).append("** (v").append(m.version).append("):\n");
                for (String c : m.commands) {
                    sb.append("  • `").append(getPrefix()).append(c).append("`\n");
                }
            }
            sb.append("\n💡 ").append(MiogramLocale.get("_Напишіть `", "_Напишите `", "_Type `")).append(getPrefix()).append(MiogramLocale.get("help <команда>` для деталей._", "help <команда>` для деталей._", "help <command>` for details._"));
            ctx.answer(sb.toString());
        });

        CommandHandler infoHandler = ctx -> {
            int modulesCount = modules.size();
            int commandsCount = commandHandlers.size();
            String res = "🪐 **Heroku Userbot Status**\n\n" +
                    "• " + MiogramLocale.get("Клієнт: **Miogram ", "Клиент: **Miogram ", "Client: **Miogram ") + BuildVars.BUILD_VERSION_STRING + "**\n" +
                    "• " + MiogramLocale.get("Пристрій: ", "Устройство: ", "Device: ") + Build.MANUFACTURER + " " + Build.MODEL + " (Android " + Build.VERSION.RELEASE + ")\n" +
                    "• " + MiogramLocale.get("Префікс: `", "Префикс: `", "Prefix: `") + getPrefix() + "`\n" +
                    "• " + MiogramLocale.get("Модулів: `", "Модулей: `", "Modules: `") + modulesCount + "` | " + MiogramLocale.get("Команд: `", "Команд: `", "Commands: `") + commandsCount + "`\n" +
                    "• Helper Bot: " + (hasConfiguredBot() ? ("@" + getBotUsername() + (isInlineCapable() ? " [Inline OK]" : "")) : MiogramLocale.get("❌ Відсутній", "❌ Отсутствует", "❌ Missing")) + "\n" +
                    "• AFK Mode: " + (isAfk ? "💤 " + MiogramLocale.get("Увімкнено", "Включен", "Enabled") : "☀️ " + MiogramLocale.get("Вимкнено", "Выключен", "Disabled")) + "\n" +
                    "• MioHook: " + MiogramLocale.get("**Активний ໒꒱**", "**Активен ໒꒱**", "**Active ໒꒱**");
            ctx.answer(res);
        };
        registerCommand(core, "info", infoHandler);
        registerCommand(core, "status", infoHandler);

        registerCommand(core, "id", ctx -> {
            long peer = ctx.dialogId;
            long myId = UserConfig.getInstance(ctx.account).getClientUserId();
            StringBuilder sb = new StringBuilder();
            sb.append("🆔 **Telegram Identifiers:**\n\n");
            sb.append("• ").append(MiogramLocale.get("**Чат ID:** `", "**Чат ID:** `", "**Chat ID:** `")).append(peer).append("`\n");
            sb.append("• ").append(MiogramLocale.get("**Мій ID:** `", "**Мой ID:** `", "**My ID:** `")).append(myId).append("`\n");
            if (ctx.replyMessage != null) {
                sb.append("• ").append(MiogramLocale.get("**Повідомлення ID:** `", "**Сообщение ID:** `", "**Message ID:** `")).append(ctx.replyMessage.getId()).append("`\n");
                sb.append("• ").append(MiogramLocale.get("**Відправник ID:** `", "**Отправитель ID:** `", "**Sender ID:** `")).append(ctx.replyMessage.getFromChatId()).append("`\n");
            }
            ctx.answer(sb.toString());
        });

        registerCommand(core, "prefix", ctx -> {
            if (!TextUtils.isEmpty(ctx.rawArgs)) {
                String newPrefix = ctx.rawArgs.trim().substring(0, 1);
                setPrefix(newPrefix);
                ctx.answer("⚡️ " + MiogramLocale.get("Префікс команд змінено на: `", "Префикс команд изменен на: `", "Command prefix changed to: `") + newPrefix + "`");
            } else {
                ctx.answer("⚡️ " + MiogramLocale.get("Поточний префікс: `", "Текущий префикс: `", "Current prefix: `") + getPrefix() + "`");
            }
        });

        CommandHandler restartHandler = ctx -> {
            executor.execute(() -> {
                commandHandlers.clear();
                modules.clear();
                registerBuiltinModules();
                scanExternalModules();
                ctx.answer("🔄 " + MiogramLocale.get("Userbot перезавантажено! Активних модулів: **", "Userbot перезагружен! Активных модулей: **", "Userbot reloaded! Active modules: **") + modules.size() + "**");
            });
        };
        registerCommand(core, "restart", restartHandler);
        registerCommand(core, "reload", restartHandler);

        modules.put(core.name, core);

        // 2. Chat Tools Module (del, purge, edit, pin, unpin, r, quote, tagall, tag)
        UserbotModuleInfo chatTools = new UserbotModuleInfo("ChatTools", MiogramLocale.get("Управління повідомленнями та чатами (purge, del, pin, tagall)", "Управление сообщениями и чатами (purge, del, pin, tagall)", "Chat and message moderation (purge, del, pin, tagall)"), "2.1.0", "Miogram Team", true);

        registerCommand(chatTools, "del", ctx -> {
            if (ctx.replyMessage != null) {
                ctx.deleteRepliedMessage();
            } else {
                ctx.answer("⚠️ " + MiogramLocale.get("Відповідайте на повідомлення для видалення: `", "Ответьте на сообщение для удаления: `", "Reply to a message to delete: `") + getPrefix() + "del`");
            }
        });

        registerCommand(chatTools, "purge", ctx -> {
            if (ctx.replyMessage != null) {
                int startId = ctx.replyMessage.getId();
                int count = 50;
                if (!TextUtils.isEmpty(ctx.rawArgs)) {
                    try {
                        count = Math.min(100, Math.max(1, Integer.parseInt(ctx.rawArgs.trim())));
                    } catch (Throwable ignore) {}
                }
                ArrayList<Integer> ids = new ArrayList<>();
                for (int i = 0; i <= count; i++) {
                    ids.add(startId + i);
                }
                ctx.deleteMessages(ids);
                ctx.answer("🗑 " + MiogramLocale.get("Повідомлення успішно очищено.", "Сообщения успешно очищены.", "Messages purged successfully."));
            } else {
                ctx.answer("⚠️ " + MiogramLocale.get("Відповідайте на перше повідомлення для очищення: `", "Ответьте на первое сообщение для очистки: `", "Reply to the first message to purge: `") + getPrefix() + "purge [ліміт]`");
            }
        });

        registerCommand(chatTools, "edit", ctx -> {
            if (ctx.replyMessage != null && ctx.replyMessage.isOutOwner()) {
                if (TextUtils.isEmpty(ctx.rawArgs)) {
                    ctx.answer("⚠️ " + MiogramLocale.get("Вкажіть новий текст: `", "Укажите новый текст: `", "Specify new text: `") + getPrefix() + "edit новий текст`");
                    return;
                }
                ctx.editRepliedMessage(ctx.rawArgs);
            } else {
                ctx.answer("⚠️ " + MiogramLocale.get("Відповідайте на своє повідомлення для редагування.", "Ответьте на своё сообщение для редактирования.", "Reply to your own message to edit it."));
            }
        });

        registerCommand(chatTools, "pin", ctx -> {
            if (ctx.replyMessage != null) {
                int replyId = ctx.replyMessage.getId();
                if (ctx.dialogId < 0) {
                    TLRPC.Chat chat = MessagesController.getInstance(ctx.account).getChat(-ctx.dialogId);
                    if (chat != null) {
                        MessagesController.getInstance(ctx.account).pinMessage(chat, null, replyId, false, false, false);
                        ctx.answer("📌 " + MiogramLocale.get("Повідомлення закріплено.", "Сообщение закреплено.", "Message pinned."));
                    }
                } else {
                    TLRPC.User user = MessagesController.getInstance(ctx.account).getUser(ctx.dialogId);
                    if (user != null) {
                        MessagesController.getInstance(ctx.account).pinMessage(null, user, replyId, false, false, false);
                        ctx.answer("📌 " + MiogramLocale.get("Повідомлення закріплено.", "Сообщение закреплено.", "Message pinned."));
                    }
                }
            } else {
                ctx.answer("⚠️ " + MiogramLocale.get("Відповідайте на повідомлення для закріплення.", "Ответьте на сообщение для закрепления.", "Reply to a message to pin it."));
            }
        });

        registerCommand(chatTools, "unpin", ctx -> {
            if (ctx.replyMessage != null) {
                int replyId = ctx.replyMessage.getId();
                if (ctx.dialogId < 0) {
                    TLRPC.Chat chat = MessagesController.getInstance(ctx.account).getChat(-ctx.dialogId);
                    if (chat != null) {
                        MessagesController.getInstance(ctx.account).pinMessage(chat, null, replyId, true, false, false);
                        ctx.answer("📌 " + MiogramLocale.get("Повідомлення відкріплено.", "Сообщение откреплено.", "Message unpinned."));
                    }
                } else {
                    TLRPC.User user = MessagesController.getInstance(ctx.account).getUser(ctx.dialogId);
                    if (user != null) {
                        MessagesController.getInstance(ctx.account).pinMessage(null, user, replyId, true, false, false);
                        ctx.answer("📌 " + MiogramLocale.get("Повідомлення відкріплено.", "Сообщение откреплено.", "Message unpinned."));
                    }
                }
            } else {
                ctx.answer("⚠️ " + MiogramLocale.get("Відповідайте на повідомлення для відкріплення.", "Ответьте на сообщение для открепления.", "Reply to a message to unpin it."));
            }
        });

        CommandHandler repeatHandler = ctx -> {
            if (ctx.replyMessage != null) {
                String text = !TextUtils.isEmpty(ctx.rawArgs) ? ctx.rawArgs : (ctx.replyMessage.messageOwner != null ? ctx.replyMessage.messageOwner.message : "");
                ctx.answer(text);
            } else if (!TextUtils.isEmpty(ctx.rawArgs)) {
                ctx.answer(ctx.rawArgs);
            }
        };
        registerCommand(chatTools, "r", repeatHandler);
        registerCommand(chatTools, "quote", repeatHandler);

        registerCommand(chatTools, "tagall", ctx -> {
            if (ctx.dialogId >= 0) {
                ctx.answer("⚠️ " + MiogramLocale.get("Команда доступна тільки у групах.", "Команда доступна только в группах.", "Command only available in groups."));
                return;
            }
            final String tagText = !TextUtils.isEmpty(ctx.rawArgs) ? ctx.rawArgs : "📢";
            TLRPC.ChatFull chatFull = MessagesController.getInstance(ctx.account).getChatFull(-ctx.dialogId);
            if (chatFull != null && chatFull.participants != null && chatFull.participants.participants != null) {
                StringBuilder sb = new StringBuilder();
                sb.append(tagText).append(" ");
                int count = 0;
                for (TLRPC.ChatParticipant p : chatFull.participants.participants) {
                    TLRPC.User u = MessagesController.getInstance(ctx.account).getUser(p.user_id);
                    if (u != null && !u.bot && !u.self) {
                        String name = !TextUtils.isEmpty(u.first_name) ? u.first_name : "User";
                        sb.append("[").append(name).append("](tg://user?id=").append(u.id).append(") ");
                        count++;
                        if (count >= 5) {
                            ctx.answer(sb.toString().trim());
                            sb.setLength(0);
                            sb.append(tagText).append(" ");
                            count = 0;
                        }
                    }
                }
                if (count > 0) {
                    ctx.answer(sb.toString().trim());
                }
            } else {
                long chatId = -ctx.dialogId;
                TLRPC.TL_channels_getParticipants req = new TLRPC.TL_channels_getParticipants();
                req.channel = MessagesController.getInstance(ctx.account).getInputChannel(chatId);
                req.limit = 50;
                req.filter = new TLRPC.TL_channelParticipantsRecent();
                ConnectionsManager.getInstance(ctx.account).sendRequest(req, (response, error) -> {
                    if (response instanceof TLRPC.TL_channels_channelParticipants) {
                        TLRPC.TL_channels_channelParticipants cp = (TLRPC.TL_channels_channelParticipants) response;
                        StringBuilder sb = new StringBuilder();
                        sb.append(tagText).append(" ");
                        int count = 0;
                        for (TLRPC.User u : cp.users) {
                            if (u != null && !u.bot && !u.self) {
                                String name = !TextUtils.isEmpty(u.first_name) ? u.first_name : "User";
                                sb.append("[").append(name).append("](tg://user?id=").append(u.id).append(") ");
                                count++;
                                if (count >= 5) {
                                    ctx.answer(sb.toString().trim());
                                    sb.setLength(0);
                                    sb.append(tagText).append(" ");
                                    count = 0;
                                }
                            }
                        }
                        if (count > 0) {
                            ctx.answer(sb.toString().trim());
                        }
                    }
                });
            }
        });

        registerCommand(chatTools, "tag", ctx -> {
            if (TextUtils.isEmpty(ctx.rawArgs)) {
                ctx.answer("⚠️ " + MiogramLocale.get("Вкажіть юзера або ID: `", "Укажите юзера или ID: `", "Specify user or ID: `") + getPrefix() + "tag @user текст`");
                return;
            }
            String[] parts = ctx.rawArgs.split("\\s+", 2);
            String target = parts[0].replace("@", "");
            String text = parts.length > 1 ? parts[1] : "Hey";
            TLRPC.User user = null;
            if (target.matches("\\d+")) {
                try {
                    user = MessagesController.getInstance(ctx.account).getUser(Long.parseLong(target));
                } catch (Throwable ignore) {}
            } else {
                user = MessagesController.getInstance(ctx.account).getUser(target);
            }
            if (user != null) {
                ctx.answer(text + " [" + (!TextUtils.isEmpty(user.first_name) ? user.first_name : "User") + "](tg://user?id=" + user.id + ")");
            } else {
                ctx.answer(text + " @" + target);
            }
        });

        modules.put(chatTools.name, chatTools);

        // 3. AFK & Persona Module (afk, unafk, shrug, flip, unflip, lenny, disapprove)
        UserbotModuleInfo afkMod = new UserbotModuleInfo("AFKMod", MiogramLocale.get("Режим відсутності (AFK) та канонічні каомодзі", "Режим отсутствия (AFK) и каноничные каомодзи", "Away From Keyboard mode (AFK) and kaomojis"), "2.1.0", "Miogram Team", true);

        registerCommand(afkMod, "afk", ctx -> {
            isAfk = true;
            afkSince = System.currentTimeMillis();
            afkReason = ctx.rawArgs;
            prefs().edit()
                    .putBoolean(KEY_IS_AFK, true)
                    .putLong(KEY_AFK_SINCE, afkSince)
                    .putString(KEY_AFK_REASON, afkReason)
                    .apply();
            String reasonStr = TextUtils.isEmpty(afkReason) ? "" : ("\n💬 " + MiogramLocale.get("Причина: `", "Причина: `", "Reason: `") + afkReason + "`");
            ctx.answer("💤 **" + MiogramLocale.get("Режим AFK активовано!**", "Режим AFK активирован!**", "AFK mode enabled!**") + reasonStr);
        });

        registerCommand(afkMod, "unafk", ctx -> {
            if (!isAfk) {
                ctx.answer("ℹ️ " + MiogramLocale.get("Ви зараз не в режимі AFK.", "Вы сейчас не в режиме AFK.", "You are not in AFK mode."));
                return;
            }
            long durationMs = Math.max(0, System.currentTimeMillis() - afkSince);
            isAfk = false;
            prefs().edit().putBoolean(KEY_IS_AFK, false).remove(KEY_AFK_SINCE).remove(KEY_AFK_REASON).apply();
            ctx.answer("☀️ **" + MiogramLocale.get("Режим AFK вимкнено!**\n⏱ Був відсутній: `", "Режим AFK выключен!**\n⏱ Отсутствовал: `", "AFK mode disabled!**\n⏱ Away for: `") + formatDuration(durationMs) + "`");
        });

        registerCommand(afkMod, "shrug", ctx -> ctx.answer("¯\\_(ツ)_/¯", false));
        registerCommand(afkMod, "flip", ctx -> ctx.answer("(╯°□°)╯︵ ┻━┻", false));
        registerCommand(afkMod, "unflip", ctx -> ctx.answer("┬─┬ノ( º _ ºノ)", false));
        registerCommand(afkMod, "lenny", ctx -> ctx.answer("( ͡° ͜ʖ ͡°)", false));
        registerCommand(afkMod, "disapprove", ctx -> ctx.answer("ಠ_ಠ", false));

        modules.put(afkMod.name, afkMod);

        // 4. Entities & Information Module (user, whois, chat, chatinfo)
        UserbotModuleInfo whois = new UserbotModuleInfo("WhoIsMod", MiogramLocale.get("Детальна інформація про користувачів та чати", "Детальная информация о пользователях и чатах", "Detailed information about users and chats"), "2.1.0", "Miogram Team", true);

        CommandHandler userHandler = ctx -> {
            long targetUserId = 0;
            String targetUsername = null;
            if (ctx.replyMessage != null) {
                targetUserId = ctx.replyMessage.getFromChatId();
            } else if (!TextUtils.isEmpty(ctx.rawArgs)) {
                String arg = ctx.rawArgs.replace("@", "").trim();
                if (arg.matches("\\d+")) {
                    try {
                        targetUserId = Long.parseLong(arg);
                    } catch (Throwable ignore) {}
                } else {
                    targetUsername = arg;
                }
            } else {
                targetUserId = UserConfig.getInstance(ctx.account).getClientUserId();
            }
            TLRPC.User user = null;
            if (targetUserId != 0) {
                user = MessagesController.getInstance(ctx.account).getUser(targetUserId);
            } else if (targetUsername != null) {
                user = MessagesController.getInstance(ctx.account).getUser(targetUsername);
            }
            if (user == null) {
                ctx.answer("❌ " + MiogramLocale.get("Користувача не знайдено в локальному кеші.", "Пользователь не найден в локальном кэше.", "User not found in local cache."));
                return;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("👤 **").append(MiogramLocale.get("Інформація про користувача:**\n\n", "Информация о пользователе:**\n\n", "User Information:**\n\n"));
            sb.append("• ").append(MiogramLocale.get("Ім'я: **", "Имя: **", "Name: **")).append(UserObject.getUserName(user)).append("**\n");
            sb.append("• ID: `").append(user.id).append("`\n");
            if (!TextUtils.isEmpty(user.username)) {
                sb.append("• ").append(MiogramLocale.get("Юзернейм: @", "Юзернейм: @", "Username: @")).append(user.username).append("\n");
            }
            sb.append("• ").append(MiogramLocale.get("Посилання: [Перейти](tg://user?id=", "Ссылка: [Перейти](tg://user?id=", "Permalink: [Open](tg://user?id=")).append(user.id).append(")\n");
            sb.append("• Bot: ").append(user.bot ? "✅ " + MiogramLocale.get("Так", "Да", "Yes") : "❌ " + MiogramLocale.get("Ні", "Нет", "No")).append("\n");
            sb.append("• Telegram Premium: ").append(user.premium ? "⭐️ " + MiogramLocale.get("Так", "Да", "Yes") : "❌ " + MiogramLocale.get("Ні", "Нет", "No")).append("\n");
            sb.append("• Verified: ").append(user.verified ? "☑️ " + MiogramLocale.get("Так", "Да", "Yes") : "❌ " + MiogramLocale.get("Ні", "Нет", "No")).append("\n");
            if (user.photo != null) {
                sb.append("• DC ID: `").append(user.photo.dc_id).append("`\n");
            }
            ctx.answer(sb.toString());
        };
        registerCommand(whois, "user", userHandler);
        registerCommand(whois, "whois", userHandler);

        CommandHandler chatHandler = ctx -> {
            if (ctx.dialogId >= 0) {
                TLRPC.User u = MessagesController.getInstance(ctx.account).getUser(ctx.dialogId);
                StringBuilder sb = new StringBuilder();
                sb.append("💬 **").append(MiogramLocale.get("Приватний діалог:**\n\n", "Личный диалог:**\n\n", "Private Dialog:**\n\n"));
                sb.append("• ID: `").append(ctx.dialogId).append("`\n");
                if (u != null) {
                    sb.append("• ").append(MiogramLocale.get("Співрозмовник: **", "Собеседник: **", "User: **")).append(UserObject.getUserName(u)).append("**\n");
                    if (!TextUtils.isEmpty(u.username)) sb.append("• Username: @").append(u.username).append("\n");
                }
                ctx.answer(sb.toString());
            } else {
                TLRPC.Chat chat = MessagesController.getInstance(ctx.account).getChat(-ctx.dialogId);
                if (chat == null) {
                    ctx.answer("❌ " + MiogramLocale.get("Інформація про чат недоступна.", "Информация о чате недоступна.", "Chat info unavailable."));
                    return;
                }
                StringBuilder sb = new StringBuilder();
                sb.append("💬 **").append(MiogramLocale.get("Інформація про чат:**\n\n", "Информация о чате:**\n\n", "Chat Information:**\n\n"));
                sb.append("• ").append(MiogramLocale.get("Назва: **", "Название: **", "Title: **")).append(chat.title).append("**\n");
                sb.append("• ID: `").append(ctx.dialogId).append("`\n");
                String type = chat.broadcast ? MiogramLocale.get("Канал 📢", "Канал 📢", "Channel 📢") : (chat.megagroup ? MiogramLocale.get("Супергрупа 👥", "Супергруппа 👥", "Supergroup 👥") : MiogramLocale.get("Група 👥", "Группа 👥", "Group 👥"));
                sb.append("• ").append(MiogramLocale.get("Тип: ", "Тип: ", "Type: ")).append(type).append("\n");
                if (!TextUtils.isEmpty(chat.username)) {
                    sb.append("• Username: @").append(chat.username).append("\n");
                }
                if (chat.participants_count > 0) {
                    sb.append("• ").append(MiogramLocale.get("Учасників: `", "Участников: `", "Members: `")).append(chat.participants_count).append("`\n");
                }
                sb.append("• ").append(MiogramLocale.get("Творець: ", "Создатель: ", "Creator: ")).append(chat.creator ? "👑 " + MiogramLocale.get("Так", "Да", "Yes") : "❌ " + MiogramLocale.get("Ні", "Нет", "No")).append("\n");
                ctx.answer(sb.toString());
            }
        };
        registerCommand(whois, "chat", chatHandler);
        registerCommand(whois, "chatinfo", chatHandler);

        modules.put(whois.name, whois);

        // 5. Utilities Module (calc, tr, eval, exec, dlmod, ulmod, modules, mods)
        UserbotModuleInfo utils = new UserbotModuleInfo("Utilities", MiogramLocale.get("Корисні утиліти, перекладач, калькулятор та модулі", "Полезные утилиты, переводчик, калькулятор и модули", "Useful utilities, translator, calculator and modules"), "2.1.0", "Miogram Team", true);

        registerCommand(utils, "calc", ctx -> {
            if (TextUtils.isEmpty(ctx.rawArgs)) {
                ctx.answer("⚠️ " + MiogramLocale.get("Вкажіть вираз для обчислення. Наприклад: `", "Укажите выражение для вычисления. Например: `", "Specify expression to calculate. Example: `") + getPrefix() + "calc 25 * 4 + 10`");
                return;
            }
            try {
                double result = evaluateMathExpression(ctx.rawArgs);
                String formatted = (result == (long) result) ? String.format(Locale.US, "%d", (long) result) : String.format(Locale.US, "%s", result);
                ctx.answer("🔢 " + MiogramLocale.get("**Результат:**\n`", "**Результат:**\n`", "**Result:**\n`") + ctx.rawArgs + "` = **" + formatted + "**");
            } catch (Throwable t) {
                ctx.answer("❌ " + MiogramLocale.get("Помилка в математичному виразі: ", "Ошибка в математическом выражении: ", "Error in math expression: ") + t.getMessage());
            }
        });

        registerCommand(utils, "tr", ctx -> {
            String text = ctx.rawArgs;
            String lang = "uk";
            if (TextUtils.isEmpty(text) && ctx.replyMessage != null && ctx.replyMessage.messageOwner != null) {
                text = ctx.replyMessage.messageOwner.message;
            }
            if (TextUtils.isEmpty(text)) {
                ctx.answer("⚠️ " + MiogramLocale.get("Вкажіть текст або відповідайте на повідомлення: `", "Укажите текст или ответьте на сообщение: `", "Provide text or reply to a message: `") + getPrefix() + "tr [uk/en] текст`");
                return;
            }
            String[] parts = text.split("\\s+", 2);
            if (parts.length > 1 && parts[0].length() == 2) {
                lang = parts[0];
                text = parts[1];
            }
            final String fLang = lang;
            final String fText = text;
            translateTextAsync(fText, fLang, result -> {
                if (result != null) {
                    ctx.answer("🌐 " + MiogramLocale.get("**Переклад (", "**Перевод (", "**Translation (") + fLang.toUpperCase(Locale.ROOT) + "):**\n" + result);
                } else {
                    ctx.answer("❌ " + MiogramLocale.get("Не вдалося перекласти текст.", "Не удалось перевести текст.", "Failed to translate text."));
                }
            });
        });

        CommandHandler evalHandler = ctx -> {
            if (TextUtils.isEmpty(ctx.rawArgs)) {
                ctx.answer("⚠️ " + MiogramLocale.get("Вкажіть Python вираз для виконання: `", "Укажите Python выражение для выполнения: `", "Provide Python expression to execute: `") + getPrefix() + "eval 2 + 2`");
                return;
            }
            executePythonEval(ctx.rawArgs, output -> {
                ctx.answer("🐍 **Python Eval:**\n" + MiogramLocale.get("**Вхід:**\n```python\n", "**Вход:**\n```python\n", "**Input:**\n```python\n") + ctx.rawArgs + "\n```\n" + MiogramLocale.get("**Вихід:**\n```\n", "**Выход:**\n```\n", "**Output:**\n```\n") + output + "\n```");
            });
        };
        registerCommand(utils, "eval", evalHandler);
        registerCommand(utils, "exec", evalHandler);

        CommandHandler dlmodHandler = ctx -> {
            String url = ctx.rawArgs != null ? ctx.rawArgs.trim().split("\\s+")[0] : "";
            if (TextUtils.isEmpty(url) || (!url.startsWith("http://") && !url.startsWith("https://"))) {
                ctx.answer("⚠️ " + MiogramLocale.get("Вкажіть пряме посилання на `.py` модуль: `", "Укажите прямую ссылку на `.py` модуль: `", "Provide a direct link to a `.py` module: `") + getPrefix() + "dlm https://…/mymod.py`");
                return;
            }
            if (!url.toLowerCase(Locale.ROOT).split("\\?")[0].endsWith(".py")) {
                ctx.answer("❌ " + MiogramLocale.get("Посилання має вести на `.py` файл.", "Ссылка должна вести на `.py` файл.", "Link must point to a `.py` file."));
                return;
            }
            ctx.answer("⏳ " + MiogramLocale.get("Завантажую модуль…", "Загружаю модуль…", "Downloading module…"));
            downloadModuleFromUrl(url, new Utilities.Callback2<File, String>() {
                @Override
                public void run(File file, String error) {
                    if (file != null) {
                        HikkaModuleMeta meta = parseModuleMeta(file);
                        String name = !meta.name.isEmpty() ? meta.name : file.getName().replace(".py", "");
                        if (!meta.requires.isEmpty()) {
                            executePipCommand("install", meta.requires, null);
                        }
                        ctx.answer("✅ " + MiogramLocale.get("Модуль **", "Модуль **", "Module **") + name + "** "
                                + MiogramLocale.get("встановлено", "установлен", "installed")
                                + (meta.commands.isEmpty() ? "" : " (" + meta.commands.size() + " "
                                + MiogramLocale.get("команд", "команд", "commands") + ")")
                                + (!meta.requires.isEmpty() ? ("\n📦 " + MiogramLocale.get("Встановлюю залежності: `", "Устанавливаю зависимости: `", "Installing dependencies: `") + meta.requires + "`") : ""));
                    } else {
                        ctx.answer("❌ " + MiogramLocale.get("Не вдалося завантажити: ", "Не удалось загрузить: ", "Download failed: ") + (error != null ? error : ""));
                    }
                }
            });
        };
        registerCommand(utils, "dlmod", dlmodHandler);
        registerCommand(utils, "dlm", dlmodHandler);

        CommandHandler lmodHandler = ctx -> {
            if (ctx.replyMessage == null) {
                ctx.answer("⚠️ " + MiogramLocale.get("Відповідайте на `.py` файл або повідомлення з кодом модуля: `", "Ответьте на `.py` файл или сообщение с кодом модуля: `", "Reply to a `.py` file or message with module code: `") + getPrefix() + "lm`");
                return;
            }

            if (ctx.replyMessage.isDocument()) {
                TLRPC.Document document = ctx.replyMessage.getDocument();
                String docName = ctx.replyMessage.getDocumentName();
                if (TextUtils.isEmpty(docName)) {
                    docName = FileLoader.getDocumentFileName(document);
                }
                if (TextUtils.isEmpty(docName)) {
                    docName = "module_" + System.currentTimeMillis() + ".py";
                }

                ctx.answer("⏳ " + MiogramLocale.get("Завантажую та встановлюю файл модуля з Telegram...", "Загружаю и устанавливаю файл модуля из Telegram...", "Downloading and installing module file from Telegram..."));
                final String fDocName = docName;
                executor.execute(() -> {
                    File file = FileLoader.getInstance(ctx.account).getPathToAttach(document, true);
                    if (file == null || !file.exists() || file.length() == 0) {
                        FileLoader.getInstance(ctx.account).loadFile(document, ctx.replyMessage, 2, 0);
                        for (int i = 0; i < 40; i++) {
                            try {
                                Thread.sleep(250);
                            } catch (InterruptedException ignore) {}
                            file = FileLoader.getInstance(ctx.account).getPathToAttach(document, true);
                            if (file != null && file.exists() && file.length() > 0) {
                                break;
                            }
                        }
                    }

                    if (file != null && file.exists() && file.length() > 0) {
                        if (fDocName.toLowerCase(Locale.ROOT).endsWith(".lua")) {
                            try {
                                byte[] bytes = new byte[(int) file.length()];
                                try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
                                    fis.read(bytes);
                                }
                                String code = new String(bytes, StandardCharsets.UTF_8);
                                installLuaPlugin(fDocName, code);
                                ctx.answer("✅ " + MiogramLocale.get("Lua плагін **", "Lua плагин **", "Lua plugin **") + fDocName + "** " + MiogramLocale.get("успішно встановлено!", "успешно установлен!", "installed successfully!"));
                            } catch (Throwable t) {
                                ctx.answer("❌ " + MiogramLocale.get("Помилка читання файлу: ", "Ошибка чтения файла: ", "File read error: ") + t.getMessage());
                            }
                        } else {
                            File target = new File(getUserbotModulesDir(), fDocName.endsWith(".py") ? fDocName : (fDocName + ".py"));
                            try {
                                try (java.io.InputStream in = new java.io.FileInputStream(file);
                                     java.io.OutputStream out = new java.io.FileOutputStream(target)) {
                                    byte[] buf = new byte[8192];
                                    int len;
                                    while ((len = in.read(buf)) > 0) {
                                        out.write(buf, 0, len);
                                    }
                                    out.flush();
                                }
                                loadExternalPythonModule(target);
                                HikkaModuleMeta meta = parseModuleMeta(target);
                                String modName = !meta.name.isEmpty() ? meta.name : target.getName().replace(".py", "");
                                if (!meta.requires.isEmpty()) {
                                    executePipCommand("install", meta.requires, null);
                                }
                                ctx.answer("✅ " + MiogramLocale.get("Модуль **", "Модуль **", "Module **") + modName + "** "
                                        + MiogramLocale.get("успішно встановлено з файлу", "успешно установлен из файла", "successfully installed from file")
                                        + (meta.commands.isEmpty() ? "" : " (" + meta.commands.size() + " "
                                        + MiogramLocale.get("команд", "команд", "commands") + ")")
                                        + (!meta.requires.isEmpty() ? ("\n📦 " + MiogramLocale.get("Встановлюю залежності: `", "Устанавливаю зависимости: `", "Installing dependencies: `") + meta.requires + "`") : ""));
                            } catch (Throwable t) {
                                ctx.answer("❌ " + MiogramLocale.get("Помилка встановлення: ", "Ошибка установки: ", "Install error: ") + t.getMessage());
                            }
                        }
                    } else {
                        ctx.answer("❌ " + MiogramLocale.get("Не вдалося завантажити файл з Telegram.", "Не удалось загрузить файл из Telegram.", "Failed to download file from Telegram."));
                    }
                });
                return;
            }

            if (ctx.replyMessage.messageOwner != null && !TextUtils.isEmpty(ctx.replyMessage.messageOwner.message)) {
                String code = ctx.replyMessage.messageOwner.message;
                String baseName = !TextUtils.isEmpty(ctx.rawArgs) ? ctx.rawArgs.trim() : null;
                if (installModuleFromCode(baseName, code)) {
                    File[] files = getUserbotModulesDir().listFiles((d, n) -> n.endsWith(".py"));
                    String name = baseName != null ? baseName : "модуль";
                    if (files != null && files.length > 0) {
                        File newest = files[0];
                        for (File f : files) {
                            if (f.lastModified() > newest.lastModified()) newest = f;
                        }
                        HikkaModuleMeta meta = parseModuleMeta(newest);
                        if (!meta.name.isEmpty()) name = meta.name;
                        if (!meta.requires.isEmpty()) {
                            executePipCommand("install", meta.requires, null);
                        }
                    }
                    ctx.answer("✅ " + MiogramLocale.get("Модуль **", "Модуль **", "Module **") + name + "** "
                            + MiogramLocale.get("успішно встановлено з коду повідомлення!", "успешно установлен из кода сообщения!", "successfully installed from message code!"));
                } else {
                    ctx.answer("❌ " + MiogramLocale.get("Помилка збереження або завантаження модуля з тексту.", "Ошибка сохранения или загрузки модуля из текста.", "Failed to save or load module from text."));
                }
            } else {
                ctx.answer("⚠️ " + MiogramLocale.get("Повідомлення не містить файлу або коду модуля.", "Сообщение не содержит файла или кода модуля.", "Message contains no file or module code."));
            }
        };
        registerCommand(utils, "lm", lmodHandler);
        registerCommand(utils, "lmod", lmodHandler);

        registerCommand(utils, "pip", ctx -> {
            if (TextUtils.isEmpty(ctx.rawArgs)) {
                executePipCommand("list", "", result -> ctx.answer(result));
                return;
            }
            String[] parts = ctx.rawArgs.trim().split("\\s+", 2);
            String sub = parts[0].toLowerCase(Locale.ROOT);
            String args = parts.length > 1 ? parts[1].trim() : "";
            if ("install".equals(sub)) {
                if (TextUtils.isEmpty(args)) {
                    ctx.answer("⚠️ " + MiogramLocale.get("Вкажіть бібліотеку для встановлення: `", "Укажите библиотеку для установки: `", "Specify library to install: `") + getPrefix() + "pip install <бібліотека>`");
                    return;
                }
                ctx.answer("⏳ " + MiogramLocale.get("Встановлюю бібліотеку з PyPI...", "Устанавливаю библиотеку из PyPI...", "Installing library from PyPI..."));
                executePipCommand("install", args, result -> ctx.answer(result));
            } else if ("uninstall".equals(sub) || "remove".equals(sub)) {
                if (TextUtils.isEmpty(args)) {
                    ctx.answer("⚠️ " + MiogramLocale.get("Вкажіть бібліотеку для видалення: `", "Укажите библиотеку для удаления: `", "Specify library to uninstall: `") + getPrefix() + "pip uninstall <бібліотека>`");
                    return;
                }
                executePipCommand("uninstall", args, result -> ctx.answer(result));
            } else if ("list".equals(sub)) {
                executePipCommand("list", "", result -> ctx.answer(result));
            } else {
                ctx.answer("⏳ " + MiogramLocale.get("Встановлюю бібліотеку з PyPI...", "Устанавливаю библиотеку из PyPI...", "Installing library from PyPI..."));
                executePipCommand("install", ctx.rawArgs.trim(), result -> ctx.answer(result));
            }
        });

        registerCommand(utils, "ulmod", ctx -> {
            String name = ctx.rawArgs != null ? ctx.rawArgs.trim() : "";
            if (TextUtils.isEmpty(name)) {
                ctx.answer("⚠️ " + MiogramLocale.get("Вкажіть назву модуля: `", "Укажите название модуля: `", "Provide module name: `") + getPrefix() + "ulmod mymod`");
                return;
            }
            if (unloadExternalModule(name)) {
                ctx.answer("✅ " + MiogramLocale.get("Модуль видалено: `", "Модуль удалён: `", "Module removed: `") + name + "`");
            } else {
                ctx.answer("❌ " + MiogramLocale.get("Модуль не знайдено або він вбудований: `", "Модуль не найден или он встроенный: `", "Module not found or is built-in: `") + name + "`");
            }
        });

        CommandHandler modulesHandler = ctx -> {
            StringBuilder sb = new StringBuilder();
            sb.append("📦 **").append(MiogramLocale.get("Встановлені модулі Userbot:**\n\n", "Установленные модули Userbot:**\n\n", "Installed Userbot Modules:**\n\n"));
            for (UserbotModuleInfo m : modules.values()) {
                sb.append(m.isBuiltin ? "🔹 " : "🐍 ");
                sb.append("**").append(m.name).append("** (v").append(m.version).append(")\n");
                sb.append("  ").append(m.description).append("\n");
                sb.append("  • ").append(MiogramLocale.get("Команд: ", "Команд: ", "Commands: ")).append(m.commands.size()).append("\n\n");
            }
            ctx.answer(sb.toString());
        };
        registerCommand(utils, "modules", modulesHandler);
        registerCommand(utils, "mods", modulesHandler);

        modules.put(utils.name, utils);
    }

    private void registerCommand(UserbotModuleInfo module, String cmdName, CommandHandler handler) {
        String lower = cmdName.toLowerCase(Locale.ROOT);
        commandHandlers.put(lower, handler);
        if (!module.commands.contains(lower)) {
            module.commands.add(lower);
        }
    }

    private String findModuleForCommand(String cmd) {
        for (UserbotModuleInfo m : modules.values()) {
            if (m.commands.contains(cmd)) return m.name;
        }
        return "Unknown";
    }

    // =========================================================================
    // External .py Module Scanner
    // =========================================================================

    public void scanExternalModules() {
        File dir = getUserbotModulesDir();
        File[] files = dir.listFiles((d, name) -> name.endsWith(".py"));
        if (files == null) return;

        for (File f : files) {
            loadExternalPythonModule(f);
        }
    }

    /** Hikka/FTG-style metadata parsed from a .py module file. */
    public static class HikkaModuleMeta {
        public String name = "";
        public String version = "1.0.0";
        public String description = "";
        public String requires = "";
        public final List<String> commands = new ArrayList<>();
    }

    public static HikkaModuleMeta parseModuleMeta(File file) {
        HikkaModuleMeta meta = new HikkaModuleMeta();
        String content;
        try {
            byte[] bytes = new byte[(int) Math.min(file.length(), 512 * 1024)];
            try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
                int read = 0;
                while (read < bytes.length) {
                    int n = fis.read(bytes, read, bytes.length - read);
                    if (n < 0) break;
                    read += n;
                }
                content = new String(bytes, 0, read, StandardCharsets.UTF_8);
            }
        } catch (Throwable ignore) {
            return meta;
        }
        try {
            java.util.regex.Matcher vm = java.util.regex.Pattern.compile(
                    "__version__\\s*=\\s*[\"']([^\"']+)[\"']").matcher(content);
            if (vm.find()) meta.version = vm.group(1).trim();
        } catch (Throwable ignore) {}
        try {
            java.util.regex.Matcher nm = java.util.regex.Pattern.compile(
                    "[\"']name[\"']\\s*:\\s*[\"']([^\"']+)[\"']").matcher(content);
            if (nm.find()) meta.name = nm.group(1).trim();
        } catch (Throwable ignore) {}
        try {
            java.util.regex.Matcher rm = java.util.regex.Pattern.compile(
                    "^\\s*#\\s*requires\\s*:(.*)$", java.util.regex.Pattern.MULTILINE | java.util.regex.Pattern.CASE_INSENSITIVE).matcher(content);
            if (rm.find()) meta.requires = rm.group(1).trim();
        } catch (Throwable ignore) {}
        try {
            java.util.regex.Matcher dm = java.util.regex.Pattern.compile(
                    "\\A\\s*(?:\"\"\"|''')(.*?)(\"\"\"|''')", java.util.regex.Pattern.DOTALL).matcher(content);
            if (dm.find()) {
                String doc = dm.group(1).trim().split("\n")[0].trim();
                if (doc.length() > 140) doc = doc.substring(0, 140) + "…";
                meta.description = doc;
            }
        } catch (Throwable ignore) {}
        try {
            java.util.regex.Matcher cm = java.util.regex.Pattern.compile(
                    "@loader\\.command[^\\n]*\\n\\s*(?:async\\s+)?def\\s+(\\w+)\\s*\\(").matcher(content);
            while (cm.find()) {
                String fn = cm.group(1);
                String cmd = fn.endsWith("cmd") ? fn.substring(0, fn.length() - 3) : fn;
                if (!cmd.isEmpty() && !meta.commands.contains(cmd.toLowerCase(Locale.ROOT))) {
                    meta.commands.add(cmd.toLowerCase(Locale.ROOT));
                }
            }
            if (meta.commands.isEmpty()) {
                java.util.regex.Matcher fm = java.util.regex.Pattern.compile(
                        "(?:async\\s+)?def\\s+(\\w+cmd)\\s*\\(\\s*self").matcher(content);
                while (fm.find()) {
                    String fn = fm.group(1);
                    String cmd = fn.substring(0, fn.length() - 3).toLowerCase(Locale.ROOT);
                    if (!meta.commands.contains(cmd)) meta.commands.add(cmd);
                }
            }
        } catch (Throwable ignore) {}
        return meta;
    }

    public boolean loadExternalPythonModule(File file) {
        if (file == null || !file.exists()) return false;
        String fileBase = file.getName().replace(".py", "");
        HikkaModuleMeta meta = parseModuleMeta(file);
        String modName = !meta.name.isEmpty() ? meta.name : fileBase;
        String desc = !meta.description.isEmpty() ? meta.description
                : (MiogramLocale.get("Зовнішній Heroku модуль (", "Внешний Heroku модуль (", "External Heroku module (") + file.getName() + ")");
        if (!meta.requires.isEmpty()) {
            desc += MiogramLocale.get(" · Залежності: ", " · Зависимости: ", " · Needs: ") + meta.requires;
        }
        UserbotModuleInfo mod = new UserbotModuleInfo(modName, desc,
                !meta.version.isEmpty() ? meta.version : "1.0.0", "External", false);

        List<String> cmds = new ArrayList<>(meta.commands);
        if (cmds.isEmpty()) cmds.add(fileBase.toLowerCase(Locale.ROOT));
        for (String cmd : cmds) {
            final String c = cmd;
            registerCommand(mod, c, ctx -> {
                executePythonModuleFile(file, ctx);
            });
        }

        try {
            if (moduleDeclaresFilter(file)) {
                final String modulePath = file.getAbsolutePath();
                registerTextFilter(mod.name, text -> {
                    if (text == null || text.startsWith(getPrefix())) return text;
                    return runModuleFilter(modulePath, text);
                });
            }
        } catch (Throwable ignore) {}

        modules.put(mod.name, mod);
        return true;
    }

    private static boolean moduleDeclaresFilter(File file) {
        if (file == null || !file.isFile()) return false;
        try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
            byte[] buf = new byte[(int) Math.min(file.length(), 65536)];
            int read = 0;
            while (read < buf.length) {
                int n = in.read(buf, read, buf.length - read);
                if (n < 0) break;
                read += n;
            }
            String head = new String(buf, 0, read, StandardCharsets.UTF_8);
            return head.contains("filter_outgoing");
        } catch (Throwable ignore) {
            return false;
        }
    }

    private void executePythonModuleFile(File file, CommandContext ctx) {
        try {
            if (PythonPluginsEngine.getInstance().isStarted()) {
                com.chaquo.python.Python py = com.chaquo.python.Python.getInstance();
                com.chaquo.python.PyObject runner = py.getModule("heroku_compat.module_runner");
                String replyText = (ctx.replyMessage != null && ctx.replyMessage.messageOwner != null) ? ctx.replyMessage.messageOwner.message : "";
                int replyId = ctx.replyMessage != null ? ctx.replyMessage.getId() : 0;
                com.chaquo.python.PyObject res = runner.callAttr("run_command",
                        file.getAbsolutePath(), ctx.command, ctx.rawArgs, ctx.fullText, replyText, replyId, ctx.dialogId);
                if (res != null) {
                    org.json.JSONObject out = new org.json.JSONObject(res.toString());
                    org.json.JSONArray replies = out.optJSONArray("replies");
                    boolean handled = false;
                    if (replies != null) {
                        for (int i = 0; i < replies.length(); i++) {
                            org.json.JSONArray pair = replies.optJSONArray(i);
                            if (pair != null && pair.length() >= 2) {
                                String kind = pair.optString(0, "respond");
                                String val = pair.optString(1, "");
                                if ("delete".equals(kind)) {
                                    ctx.deleteRepliedMessage();
                                    handled = true;
                                } else if ("edit".equals(kind)) {
                                    if (ctx.replyMessage != null && ctx.replyMessage.isOutOwner()) {
                                        ctx.editRepliedMessage(val);
                                    } else {
                                        ctx.answer(val);
                                    }
                                    handled = true;
                                } else if ("respond".equals(kind) && !TextUtils.isEmpty(val)) {
                                    ctx.answer(val);
                                    handled = true;
                                }
                            }
                        }
                    }
                    if (handled) return;
                    String err = out.optString("error", "");
                    if (!TextUtils.isEmpty(err) && !"command not found in module".equals(err)) {
                        ctx.answer("❌ " + MiogramLocale.get("Помилка модуля: ", "Ошибка модуля: ", "Module error: ") + "`" + err + "`");
                        return;
                    }
                }
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }

        HikkaModuleMeta meta = parseModuleMeta(file);
        StringBuilder sb = new StringBuilder();
        sb.append("🪐 **").append(!meta.name.isEmpty() ? meta.name : file.getName().replace(".py", "")).append("**\n");
        if (!meta.commands.isEmpty()) {
            sb.append(MiogramLocale.get("Команди: ", "Команды: ", "Commands: "));
            for (int i = 0; i < meta.commands.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append("`").append(getPrefix()).append(meta.commands.get(i)).append("`");
            }
            sb.append("\n");
        }
        if (!meta.requires.isEmpty()) {
            sb.append(MiogramLocale.get("⚠️ Потребує pip-залежностей: `", "⚠️ Требует pip-зависимостей: `", "⚠️ Needs pip packages: `")).append(meta.requires).append("`\n");
        }
        sb.append(MiogramLocale.get("_Модуль не відповів (потрібен Python-рантайм)._",
                "_Модуль не ответил (нужен Python-рантайм)._",
                "_Module gave no reply (needs Python runtime)._"));
        ctx.answer(sb.toString());
    }

    private String runModuleFilter(String modulePath, String text) {
        if (TextUtils.isEmpty(text) || TextUtils.isEmpty(modulePath)) return text;
        try {
            if (!PythonPluginsEngine.getInstance().isStarted()) return text;
            File f = new File(modulePath);
            if (!f.isFile()) return text;
            com.chaquo.python.Python py = com.chaquo.python.Python.getInstance();
            com.chaquo.python.PyObject runner = py.getModule("heroku_compat.module_runner");
            com.chaquo.python.PyObject res = runner.callAttr("run_filter", modulePath, text);
            if (res == null) return text;
            org.json.JSONObject out = new org.json.JSONObject(res.toString());
            String filtered = out.optString("text", text);
            return filtered != null ? filtered : text;
        } catch (Throwable t) {
            FileLog.e(t);
            return text;
        }
    }

    private void executePythonEval(String code, Utilities.Callback<String> callback) {
        executor.execute(() -> {
            try {
                if (PythonPluginsEngine.getInstance().isStarted()) {
                    com.chaquo.python.Python py = com.chaquo.python.Python.getInstance();
                    com.chaquo.python.PyObject builtins = py.getBuiltins();
                    com.chaquo.python.PyObject evalFunc = builtins.get("eval");
                    if (evalFunc != null) {
                        com.chaquo.python.PyObject result = evalFunc.call(code);
                        String str = result != null ? result.toString() : "None";
                        AndroidUtilities.runOnUIThread(() -> callback.run(str));
                        return;
                    }
                }
            } catch (Throwable t) {
                AndroidUtilities.runOnUIThread(() -> callback.run("Error: " + t.getMessage()));
                return;
            }

            try {
                double val = evaluateMathExpression(code);
                AndroidUtilities.runOnUIThread(() -> callback.run(String.valueOf(val)));
            } catch (Throwable ignore) {
                AndroidUtilities.runOnUIThread(() -> callback.run("Evaluated: " + code));
            }
        });
    }

    public void executePipCommand(String action, String rawArgs, Utilities.Callback<String> callback) {
        executor.execute(() -> {
            try {
                if (!PythonPluginsEngine.getInstance().isStarted()) {
                    Context appCtx = ApplicationLoader.applicationContext;
                    PythonPluginsEngine.getInstance().ensureStarted(appCtx, ok -> {});
                    for (int i = 0; i < 20 && !PythonPluginsEngine.getInstance().isStarted(); i++) {
                        try { Thread.sleep(200); } catch (InterruptedException ignore) {}
                    }
                }
                if (PythonPluginsEngine.getInstance().isStarted()) {
                    com.chaquo.python.Python py = com.chaquo.python.Python.getInstance();
                    com.chaquo.python.PyObject pipRunner = py.getModule("heroku_compat.pip_runner");
                    com.chaquo.python.PyObject res = pipRunner.callAttr("run_pip", action, rawArgs != null ? rawArgs : "");
                    if (res != null) {
                        JSONObject out = new JSONObject(res.toString());
                        boolean success = out.optBoolean("success", false);
                        if (success) {
                            if ("install".equals(action)) {
                                JSONArray installed = out.optJSONArray("installed");
                                StringBuilder sb = new StringBuilder();
                                sb.append("✅ **").append(MiogramLocale.get("Бібліотеку успішно встановлено у віртуальне середовище:**\n",
                                        "Библиотека успешно установлена в виртуальное окружение:**\n",
                                        "Library successfully installed to virtual environment:**\n"));
                                if (installed != null) {
                                    for (int i = 0; i < installed.length(); i++) {
                                        JSONObject item = installed.optJSONObject(i);
                                        if (item != null) {
                                            sb.append("• **").append(item.optString("name")).append("** (v").append(item.optString("version")).append(")\n");
                                        }
                                    }
                                }
                                final String msg = sb.toString();
                                AndroidUtilities.runOnUIThread(() -> { if (callback != null) callback.run(msg); });
                                return;
                            } else if ("uninstall".equals(action) || "remove".equals(action)) {
                                JSONArray removed = out.optJSONArray("removed");
                                StringBuilder sb = new StringBuilder();
                                sb.append("🗑 **").append(MiogramLocale.get("Бібліотеку видалено:**\n", "Библиотека удалена:**\n", "Library removed:**\n"));
                                if (removed != null) {
                                    for (int i = 0; i < removed.length(); i++) {
                                        sb.append("• `").append(removed.optString(i)).append("`\n");
                                    }
                                }
                                final String msg = sb.toString();
                                AndroidUtilities.runOnUIThread(() -> { if (callback != null) callback.run(msg); });
                                return;
                            } else {
                                JSONArray packages = out.optJSONArray("packages");
                                StringBuilder sb = new StringBuilder();
                                sb.append("🐍 **").append(MiogramLocale.get("Встановлені Python бібліотеки (Userbot Virtualenv):**\n\n",
                                        "Установленные Python библиотеки (Userbot Virtualenv):**\n\n",
                                        "Installed Python libraries (Userbot Virtualenv):**\n\n"));
                                if (packages != null && packages.length() > 0) {
                                    for (int i = 0; i < packages.length(); i++) {
                                        JSONObject item = packages.optJSONObject(i);
                                        if (item != null) {
                                            sb.append("• **").append(item.optString("name")).append("** — `v").append(item.optString("version")).append("`\n");
                                        }
                                    }
                                } else {
                                    sb.append(MiogramLocale.get("_Ще не встановлено додаткових бібліотек._\n💡 Встановіть через `",
                                            "_Ещё не установлено дополнительных библиотек._\n💡 Установите через `",
                                            "_No additional libraries installed yet._\n💡 Install with `")).append(getPrefix()).append("pip install <пакет>`");
                                }
                                final String msg = sb.toString();
                                AndroidUtilities.runOnUIThread(() -> { if (callback != null) callback.run(msg); });
                                return;
                            }
                        } else {
                            String err = out.optString("error", "Unknown pip error");
                            final String errMsg = "❌ " + MiogramLocale.get("Помилка PIP: ", "Ошибка PIP: ", "PIP error: ") + "`" + err + "`";
                            AndroidUtilities.runOnUIThread(() -> { if (callback != null) callback.run(errMsg); });
                            return;
                        }
                    }
                }
                final String errNotStarted = "❌ " + MiogramLocale.get("Python-рантайм недоступний.", "Python-рантайм недоступен.", "Python runtime unavailable.");
                AndroidUtilities.runOnUIThread(() -> { if (callback != null) callback.run(errNotStarted); });
            } catch (Throwable t) {
                FileLog.e(t);
                final String errEx = "❌ " + MiogramLocale.get("Помилка: ", "Ошибка: ", "Error: ") + t.getMessage();
                AndroidUtilities.runOnUIThread(() -> { if (callback != null) callback.run(errEx); });
            }
        });
    }

    public void downloadModuleFromUrl(String urlStr, Utilities.Callback2<File, String> callback) {
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(urlStr);
                String path = url.getPath();
                String base = path.substring(path.lastIndexOf('/') + 1);
                if (base.isEmpty() || !base.toLowerCase(Locale.ROOT).endsWith(".py")) base = "module_" + System.currentTimeMillis() + ".py";
                base = base.replaceAll("[^A-Za-z0-9_.-]", "_");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(20000);
                conn.setRequestProperty("User-Agent", "MiogramTelegramClient/1.0");
                int code = conn.getResponseCode();
                if (code < 200 || code >= 300) {
                    throw new Exception("HTTP " + code);
                }
                File dir = getUserbotModulesDir();
                File target = new File(dir, base);
                try (InputStream in = conn.getInputStream();
                     java.io.FileOutputStream out = new java.io.FileOutputStream(target)) {
                    byte[] buf = new byte[8192];
                    long total = 0;
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        total += n;
                        if (total > 2L * 1024 * 1024) throw new Exception("file too large (>2MB)");
                        out.write(buf, 0, n);
                    }
                }
                if (target.length() == 0) {
                    target.delete();
                    throw new Exception("empty file");
                }
                loadExternalPythonModule(target);
                final File f = target;
                AndroidUtilities.runOnUIThread(() -> callback.run(f, null));
            } catch (Throwable t) {
                FileLog.e(t);
                final String err = t.getMessage() != null ? t.getMessage() : "download error";
                AndroidUtilities.runOnUIThread(() -> callback.run(null, err));
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    public boolean unloadExternalModule(String name) {
        if (TextUtils.isEmpty(name)) return false;
        String key = name.trim().toLowerCase(Locale.ROOT).replace(".py", "");
        UserbotModuleInfo target = null;
        for (UserbotModuleInfo m : new ArrayList<>(modules.values())) {
            if (m.isBuiltin) continue;
            if (m.name.equalsIgnoreCase(key) || m.name.toLowerCase(Locale.ROOT).contains(key)
                    || m.commands.contains(key)) {
                target = m;
                break;
            }
        }
        if (target == null) return false;
        for (String cmd : new ArrayList<>(target.commands)) {
            commandHandlers.remove(cmd.toLowerCase(Locale.ROOT));
        }
        modules.remove(target.name);
        try {
            File dir = getUserbotModulesDir();
            File[] files = dir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).startsWith(key) && n.endsWith(".py"));
            if (files != null) for (File f : files) f.delete();
        } catch (Throwable ignore) {}
        return true;
    }

    // =========================================================================
    // Bot API Token Verification (@BotFather)
    // =========================================================================

    public interface BotTokenCallback {
        void onResult(boolean success, String username, String name, long id, boolean canJoinGroups, boolean supportsInline, String error);
    }

    public void verifyBotToken(String token, BotTokenCallback callback) {
        if (TextUtils.isEmpty(token)) {
            if (callback != null) callback.onResult(false, null, null, 0, false, false, "Token is empty");
            return;
        }
        final String fToken = token.trim();
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL("https://api.telegram.org/bot" + fToken + "/getMe");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                int code = conn.getResponseCode();
                InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();

                JSONObject obj = new JSONObject(sb.toString());
                boolean ok = obj.optBoolean("ok", false);
                if (ok && obj.has("result")) {
                    JSONObject res = obj.getJSONObject("result");
                    long id = res.optLong("id", 0);
                    String username = res.optString("username", "");
                    String firstName = res.optString("first_name", "");
                    boolean canJoin = res.optBoolean("can_join_groups", true);
                    boolean supportsInline = res.optBoolean("supports_inline_queries", false);

                    setBotInfo(fToken, username, firstName, id, supportsInline);

                    AndroidUtilities.runOnUIThread(() -> {
                        if (callback != null) {
                            callback.onResult(true, username, firstName, id, canJoin, supportsInline, null);
                        }
                    });
                } else {
                    String desc = obj.optString("description", "Invalid bot token or unauthorized");
                    AndroidUtilities.runOnUIThread(() -> {
                        if (callback != null) {
                            callback.onResult(false, null, null, 0, false, false, desc);
                        }
                    });
                }
            } catch (Throwable t) {
                FileLog.e(t);
                AndroidUtilities.runOnUIThread(() -> {
                    if (callback != null) {
                        callback.onResult(false, null, null, 0, false, false, t.getMessage());
                    }
                });
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }

    // =========================================================================
    // Utilities: Math & Translation
    // =========================================================================

    private double evaluateMathExpression(final String str) {
        return new Object() {
            int pos = -1, ch;
            void nextChar() { ch = (++pos < str.length()) ? str.charAt(pos) : -1; }
            boolean eat(int charToEat) {
                while (ch == ' ') nextChar();
                if (ch == charToEat) { nextChar(); return true; }
                return false;
            }
            double parse() {
                nextChar();
                double x = parseExpression();
                if (pos < str.length()) throw new RuntimeException("Unexpected: " + (char)ch);
                return x;
            }
            double parseExpression() {
                double x = parseTerm();
                for (;;) {
                    if (eat('+')) x += parseTerm();
                    else if (eat('-')) x -= parseTerm();
                    else return x;
                }
            }
            double parseTerm() {
                double x = parseFactor();
                for (;;) {
                    if (eat('*')) x *= parseFactor();
                    else if (eat('/')) x /= parseFactor();
                    else return x;
                }
            }
            double parseFactor() {
                if (eat('+')) return parseFactor();
                if (eat('-')) return -parseFactor();
                double x;
                int startPos = this.pos;
                if (eat('(')) {
                    x = parseExpression();
                    eat(')');
                } else if ((ch >= '0' && ch <= '9') || ch == '.') {
                    while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                    x = Double.parseDouble(str.substring(startPos, this.pos));
                } else if (ch >= 'a' && ch <= 'z') {
                    while (ch >= 'a' && ch <= 'z') nextChar();
                    String func = str.substring(startPos, this.pos);
                    x = parseFactor();
                    if (func.equals("sqrt")) x = Math.sqrt(x);
                    else if (func.equals("sin")) x = Math.sin(Math.toRadians(x));
                    else if (func.equals("cos")) x = Math.cos(Math.toRadians(x));
                    else if (func.equals("tan")) x = Math.tan(Math.toRadians(x));
                    else throw new RuntimeException("Unknown function: " + func);
                } else {
                    throw new RuntimeException("Unexpected: " + (char)ch);
                }
                if (eat('^')) x = Math.pow(x, parseFactor());
                return x;
            }
        }.parse();
    }

    private void translateTextAsync(String text, String targetLang, Utilities.Callback<String> callback) {
        executor.execute(() -> {
            try {
                String encoded = java.net.URLEncoder.encode(text, "UTF-8");
                URL url = new URL("https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=" + targetLang + "&dt=t&q=" + encoded);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                org.json.JSONArray arr = new org.json.JSONArray(sb.toString());
                org.json.JSONArray sub = arr.getJSONArray(0);
                StringBuilder result = new StringBuilder();
                for (int i = 0; i < sub.length(); i++) {
                    result.append(sub.getJSONArray(i).getString(0));
                }
                AndroidUtilities.runOnUIThread(() -> callback.run(result.toString()));
            } catch (Throwable t) {
                FileLog.e(t);
                AndroidUtilities.runOnUIThread(() -> callback.run(null));
            }
        });
    }
}
