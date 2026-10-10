package com.amegram.mods.fileorganization;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.List;

import app.amegram.hot.api.HotFileOrganization;
import app.amegram.hot.api.HotHost;
import app.amegram.hot.api.HotModule;
import app.amegram.hot.api.HotRow;
import app.amegram.hot.api.HotServices;

/**
 * Блок 12: мінімальний модуль організації файлів.
 * Дулює тогли підпапок + SaveToChatSubfolder, реєструє HotServices.FILE_ORGANIZATION.
 * api-класи — тільки compileOnly, жодних app.* / org.telegram.* імпортів.
 */
public class FileOrgModule implements HotModule, HotFileOrganization {

    private static final String NEKO_PREFS = "nkmrcfg";
    private static final String KEY_SAVE_TO_CHAT_SUBFOLDER = "SaveToChatSubfolder";

    private static final String SUBFOLDER_PREFS = "miogram_subfolders_prefs";
    private static final String KEY_SUBFOLDERS = "subfolders_enabled";

    private HotHost host;

    public FileOrgModule() {
    }

    @Override
    public String moduleId() {
        return "fileorganization";
    }

    @Override
    public void onAttach(Context appContext, HotHost host) {
        this.host = host;
        try {
            host.registerService(HotServices.FILE_ORGANIZATION, this);
        } catch (Throwable ignore) {
        }
        try {
            host.log("fileorganization attached");
        } catch (Throwable ignore) {
        }
    }

    @Override
    public void onDetach() {
        try {
            if (host != null) {
                host.unregisterService(HotServices.FILE_ORGANIZATION);
            }
        } catch (Throwable ignore) {
        }
        host = null;
    }

    @Override
    public boolean hasSettings() {
        return true;
    }

    @Override
    public String settingsTitle() {
        return "Організація файлів";
    }

    private boolean readSubfoldersEnabled() {
        try {
            if (host != null) {
                return host.getBool("subfolders", readAppSubfoldersEnabled());
            }
        } catch (Throwable ignore) {
        }
        return readAppSubfoldersEnabled();
    }

    private boolean readSaveToChatSubfolder() {
        try {
            if (host != null) {
                return host.getBool("SaveToChatSubfolder", readAppSaveToChatSubfolder());
            }
        } catch (Throwable ignore) {
        }
        return readAppSaveToChatSubfolder();
    }

    private boolean readAppSubfoldersEnabled() {
        try {
            Context ctx = host != null ? host.context() : null;
            if (ctx != null) {
                SharedPreferences p = ctx.getSharedPreferences(SUBFOLDER_PREFS, Context.MODE_PRIVATE);
                return p.getBoolean(KEY_SUBFOLDERS, true);
            }
        } catch (Throwable ignore) {
        }
        return true;
    }

    private boolean readAppSaveToChatSubfolder() {
        try {
            Context ctx = host != null ? host.context() : null;
            if (ctx != null) {
                SharedPreferences p = ctx.getSharedPreferences(NEKO_PREFS, Context.MODE_PRIVATE);
                return p.getBoolean(KEY_SAVE_TO_CHAT_SUBFOLDER, false);
            }
        } catch (Throwable ignore) {
        }
        return false;
    }

    private void writeAppPrefs(String key, boolean value) {
        try {
            Context ctx = host != null ? host.context() : null;
            if (ctx == null) return;
            if ("subfolders".equals(key)) {
                ctx.getSharedPreferences(SUBFOLDER_PREFS, Context.MODE_PRIVATE)
                        .edit().putBoolean(KEY_SUBFOLDERS, value).apply();
            } else if ("SaveToChatSubfolder".equals(key)) {
                ctx.getSharedPreferences(NEKO_PREFS, Context.MODE_PRIVATE)
                        .edit().putBoolean(KEY_SAVE_TO_CHAT_SUBFOLDER, value).apply();
            }
        } catch (Throwable ignore) {
        }
    }

    @Override
    public void fillSettings(List<HotRow> rows) {
        rows.add(HotRow.switchRow("subfolders",
                "Підпапки чатів",
                "Горизонтальна смужка підпапок (MiogramSubfolder)",
                readSubfoldersEnabled()));
        rows.add(HotRow.switchRow("SaveToChatSubfolder",
                "Збереження по папках чатів",
                "Save Attachments by Chat Name (NaConfig)",
                readSaveToChatSubfolder()));
    }

    @Override
    public void onSettingsToggle(String key, boolean value) {
        try {
            if (host != null) {
                host.putBool(key, value);
            }
        } catch (Throwable ignore) {
        }
        writeAppPrefs(key, value);
    }

    @Override
    public Object createScreen(String screenId) {
        return null;
    }

    // ---- HotFileOrganization: безпечне ім'я підпапки або null = хост-фолбек ----

    @Override
    public String chatSubfolder(String chatTitle, long peerId) {
        try {
            if (!readSaveToChatSubfolder()) return null;
            if (chatTitle == null) return null;
            String safe = chatTitle.trim()
                    .replaceAll("[\\\\/:*?\"<>|]", "_")
                    .replaceAll("\\s+", " ").trim();
            if (safe.isEmpty()) return null;
            if (safe.length() > 64) safe = safe.substring(0, 64).trim();
            if (safe.isEmpty()) return null;
            return safe;
        } catch (Throwable ignore) {
            return null;
        }
    }
}
