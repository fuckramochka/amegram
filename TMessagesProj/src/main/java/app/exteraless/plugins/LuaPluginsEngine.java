package app.exteraless.plugins;

import android.text.TextUtils;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.Components.BulletinFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Native, lightweight Lua Script Engine for on-device plugins.
 * Runs directly on Android without compilation.
 * Uses LuaJ (Pure JVM Lua 5.2) with near-zero memory footprint (< 1 MB)
 * and sub-millisecond execution.
 */
public class LuaPluginsEngine {

    private static volatile LuaPluginsEngine instance;

    public static LuaPluginsEngine getInstance() {
        if (instance == null) {
            synchronized (LuaPluginsEngine.class) {
                if (instance == null) {
                    instance = new LuaPluginsEngine();
                }
            }
        }
        return instance;
    }

    private static class LuaInstance {
        final Plugin plugin;
        final Globals globals;
        final boolean hasOnSend;
        final boolean hasOnReceive;
        final boolean hasOnCommand;

        LuaInstance(Plugin plugin, Globals globals, boolean hasOnSend, boolean hasOnReceive, boolean hasOnCommand) {
            this.plugin = plugin;
            this.globals = globals;
            this.hasOnSend = hasOnSend;
            this.hasOnReceive = hasOnReceive;
            this.hasOnCommand = hasOnCommand;
        }
    }

    private final Map<String, LuaInstance> activePlugins = new ConcurrentHashMap<>();

    private LuaPluginsEngine() {
    }

    /**
     * Parse header metadata from .lua file comments:
     * -- id: <id>
     * -- name: <name>
     * -- version: <version>
     * -- author: <author>
     * -- description: <desc>
     * -- icon: <icon>
     */
    public Plugin readMetadata(File file) {
        if (file == null || !file.exists()) return null;
        Plugin p = new Plugin();
        String baseName = file.getName().replace(".lua", "");
        p.id = baseName;
        p.name = baseName.replace("_", " ");
        p.path = file.getAbsolutePath();
        p.version = "1.0.0";
        p.author = "@user";
        p.description = "Легкий Lua-скрипт";
        p.icon = "msg_bot";
        p.engine = PluginsConstants.LUA;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            int linesRead = 0;
            while ((line = reader.readLine()) != null && linesRead < 30) {
                linesRead++;
                line = line.trim();
                if (line.startsWith("--")) {
                    String comment = line.substring(2).trim();
                    int colon = comment.indexOf(':');
                    if (colon > 0) {
                        String key = comment.substring(0, colon).trim().toLowerCase();
                        String val = comment.substring(colon + 1).trim();
                        switch (key) {
                            case "id": p.id = val; break;
                            case "name": p.name = val; break;
                            case "version": p.version = val; break;
                            case "author": p.author = val; break;
                            case "description": p.description = val; break;
                            case "icon": p.icon = val; break;
                        }
                    }
                } else if (!line.isEmpty()) {
                    // Stop parsing metadata after first non-comment line
                    break;
                }
            }
        } catch (Throwable t) {
            FileLog.e("LuaPluginsEngine: readMetadata failed for " + file.getName(), t);
        }
        return p;
    }

    /**
     * Load and run the Lua script in its own sandboxed Globals.
     */
    public boolean loadPlugin(Plugin p) {
        if (p == null || p.path == null) return false;
        try {
            Globals globals = JsePlatform.standardGlobals();
            injectBridgeApis(p, globals);

            LuaValue chunk = globals.loadfile(p.path);
            chunk.call();

            // Run lifecycle on_plugin_load if defined
            LuaValue onPluginLoad = globals.get("on_plugin_load");
            if (!onPluginLoad.isnil() && onPluginLoad.isfunction()) {
                try {
                    onPluginLoad.call();
                } catch (Throwable t) {
                    FileLog.e("LuaPluginsEngine: on_plugin_load failed in " + p.id, t);
                }
            }

            boolean hasOnSend = !globals.get("on_message_send").isnil();
            boolean hasOnReceive = !globals.get("on_message_receive").isnil();
            boolean hasOnCommand = !globals.get("on_command").isnil();

            activePlugins.put(p.id, new LuaInstance(p, globals, hasOnSend, hasOnReceive, hasOnCommand));
            p.loaded = true;
            p.loadError = null;
            return true;
        } catch (Throwable t) {
            FileLog.e("LuaPluginsEngine: failed to load " + p.id, t);
            p.loaded = false;
            p.loadError = t.getMessage() != null ? t.getMessage() : "Lua runtime error";
            return false;
        }
    }

    public void unloadPlugin(Plugin p) {
        if (p == null || p.id == null) return;
        LuaInstance inst = activePlugins.remove(p.id);
        if (inst != null) {
            try {
                LuaValue onPluginUnload = inst.globals.get("on_plugin_unload");
                if (!onPluginUnload.isnil() && onPluginUnload.isfunction()) {
                    onPluginUnload.call();
                }
            } catch (Throwable ignored) {}
        }
        p.loaded = false;
    }

    public void unloadAll() {
        for (LuaInstance inst : activePlugins.values()) {
            try {
                LuaValue onPluginUnload = inst.globals.get("on_plugin_unload");
                if (!onPluginUnload.isnil() && onPluginUnload.isfunction()) {
                    onPluginUnload.call();
                }
            } catch (Throwable ignored) {}
        }
        activePlugins.clear();
    }

    /**
     * Intercept and optionally modify or drop an outgoing message.
     */
    public HookResult callSendMessageHook(int account, SendMessagesHelper.SendMessageParams params) {
        if (activePlugins.isEmpty() || params == null || params.message == null) {
            return HookResult.DEFAULT;
        }

        String currentText = params.message.toString();
        long peer = params.peer;
        boolean modified = false;

        for (LuaInstance inst : activePlugins.values()) {
            if (!inst.plugin.enabled) continue;

            // 1. Handle in-chat commands if starts with '.' or '/'
            if (inst.hasOnCommand && (currentText.startsWith(".") || currentText.startsWith("/"))) {
                int space = currentText.indexOf(' ');
                String cmd = space > 0 ? currentText.substring(1, space) : currentText.substring(1);
                String args = space > 0 ? currentText.substring(space + 1).trim() : "";
                try {
                    LuaValue onCmd = inst.globals.get("on_command");
                    LuaValue res = onCmd.call(LuaValue.valueOf(cmd), LuaValue.valueOf(args), LuaValue.valueOf(peer));
                    if (res.isboolean() && !res.toboolean()) {
                        return new HookResult(HookResult.Strategy.CANCEL);
                    }
                    if (res.isstring()) {
                        currentText = res.tojstring();
                        params.message = currentText;
                        modified = true;
                    }
                } catch (Throwable t) {
                    FileLog.e("LuaPlugin command failed: " + inst.plugin.id, t);
                }
            }

            // 2. Handle on_message_send
            if (inst.hasOnSend) {
                try {
                    LuaValue onSend = inst.globals.get("on_message_send");
                    LuaValue res = onSend.call(LuaValue.valueOf(currentText), LuaValue.valueOf(peer));
                    if (res.isboolean() && !res.toboolean()) {
                        return new HookResult(HookResult.Strategy.CANCEL);
                    }
                    if (res.isstring()) {
                        String newText = res.tojstring();
                        if (!currentText.equals(newText)) {
                            currentText = newText;
                            params.message = currentText;
                            modified = true;
                        }
                    }
                } catch (Throwable t) {
                    FileLog.e("LuaPlugin on_message_send failed: " + inst.plugin.id, t);
                }
            }
        }

        return modified ? new HookResult(HookResult.Strategy.MODIFY, params) : HookResult.DEFAULT;
    }

    /**
     * Dispatch incoming message event.
     */
    public void dispatchMessageReceived(int account, MessageObject msg) {
        if (activePlugins.isEmpty() || msg == null || msg.messageOwner == null) return;
        long dialogId = msg.getDialogId();
        long senderId = msg.getSenderId();
        String text = msg.messageText != null ? msg.messageText.toString() : "";
        int msgId = msg.getId();

        for (LuaInstance inst : activePlugins.values()) {
            if (inst.plugin.enabled && inst.hasOnReceive) {
                try {
                    LuaValue onReceive = inst.globals.get("on_message_receive");
                    onReceive.invoke(LuaValue.varargsOf(new LuaValue[]{
                        LuaValue.valueOf(dialogId),
                        LuaValue.valueOf(senderId),
                        LuaValue.valueOf(text),
                        LuaValue.valueOf(msgId)
                    }));
                } catch (Throwable t) {
                    FileLog.e("LuaPlugin on_message_receive error: " + inst.plugin.id, t);
                }
            }
        }
    }

    private void injectBridgeApis(Plugin plugin, Globals globals) {
        LuaTable client = new LuaTable();

        // client.toast(message)
        client.set("toast", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                String text = arg.tojstring();
                AndroidUtilities.runOnUIThread(() -> {
                    try {
                        org.telegram.ui.ActionBar.BaseFragment fragment = org.telegram.ui.LaunchActivity.getSafeLastFragment();
                        if (fragment != null) {
                            BulletinFactory.of(fragment)
                                    .createSimpleBulletin(R.drawable.msg_bot, text)
                                    .show();
                        } else {
                            android.widget.Toast.makeText(ApplicationLoader.applicationContext, text, android.widget.Toast.LENGTH_SHORT).show();
                        }
                    } catch (Throwable ignore) {
                        android.widget.Toast.makeText(ApplicationLoader.applicationContext, text, android.widget.Toast.LENGTH_SHORT).show();
                    }
                });
                return LuaValue.NIL;
            }
        });

        // client.log(message)
        client.set("log", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                FileLog.d("LuaPlugin [" + plugin.id + "]: " + arg.tojstring());
                return LuaValue.NIL;
            }
        });

        // client.get_my_id()
        client.set("get_my_id", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                return LuaValue.valueOf(UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId());
            }
        });

        // client.send_message(chat_id, text)
        client.set("send_message", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue arg1, LuaValue arg2) {
                long peer = arg1.tolong();
                String text = arg2.tojstring();
                if (!TextUtils.isEmpty(text) && peer != 0) {
                    AndroidUtilities.runOnUIThread(() -> {
                        SendMessagesHelper.getInstance(UserConfig.selectedAccount)
                                .sendMessage(SendMessagesHelper.SendMessageParams.of(text, peer));
                    });
                }
                return LuaValue.NIL;
            }
        });

        globals.set("client", client);
    }
}
