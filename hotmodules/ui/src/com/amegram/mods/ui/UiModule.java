package com.amegram.mods.ui;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.List;

import app.amegram.hot.api.HotHost;
import app.amegram.hot.api.HotModule;
import app.amegram.hot.api.HotRow;
import app.amegram.hot.api.HotServices;
import app.amegram.hot.api.HotUi;

/**
 * Модуль інтерфейсу Yumi: Yougram Expressive ↔ Classic на льоту.
 * Пише прямо в префи AppearanceConfig (nkmrcfg: OEAppearance*), тому стиль
 * застосовується rebuild'ом фрагментів — без перевстановлення APK.
 * api-класи — тільки compileOnly, жодних app.* / org.telegram.* імпортів.
 */
public class UiModule implements HotModule, HotUi {

    private static final String NEKO_PREFS = "nkmrcfg";
    private static final String KEY_STYLE = "OEAppearanceUiStyleMode";
    private static final String KEY_TOUCHED = "OEAppearanceUiStyleModeTouched";
    private static final String KEY_RADIUS = "OEAppearanceSectionRadius";
    private static final String KEY_DIVIDER = "OEAppearanceDividerStyle";
    private static final String KEY_SQUARE_FAB = "OEAppearanceSquareFab";
    private static final String KEY_GLASS_MENU = "OEAppearanceGlassMessageMenu";
    private static final String KEY_M3_LIST = "OEAppearanceM3ListItems";
    private static final String KEY_MINI_PLAYER = "OEAppearanceMd3MiniPlayer";
    private static final String KEY_LOADING = "OEAppearanceNewLoadingStyle";

    private HotHost host;

    public UiModule() {
    }

    @Override
    public String moduleId() {
        return "ui";
    }

    @Override
    public void onAttach(Context appContext, HotHost host) {
        this.host = host;
        try {
            host.registerService(HotServices.UI, this);
        } catch (Throwable ignore) {
        }
        try {
            host.log("ui attached");
        } catch (Throwable ignore) {
        }
    }

    @Override
    public void onDetach() {
        try {
            if (host != null) {
                host.unregisterService(HotServices.UI);
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
        return "Інтерфейс Yumi";
    }

    private SharedPreferences prefs() {
        try {
            Context ctx = host != null ? host.context() : null;
            if (ctx != null) return ctx.getSharedPreferences(NEKO_PREFS, Context.MODE_PRIVATE);
        } catch (Throwable ignore) {
        }
        return null;
    }

    private int readInt(String key, int def) {
        try {
            if (host != null) return host.getInt(key, def);
        } catch (Throwable ignore) {
        }
        try {
            SharedPreferences p = prefs();
            if (p != null) return p.getInt(key, def);
        } catch (Throwable ignore) {
        }
        return def;
    }

    private boolean readBool(String key, boolean def) {
        try {
            if (host != null) return host.getBool(key, def);
        } catch (Throwable ignore) {
        }
        try {
            SharedPreferences p = prefs();
            if (p != null) return p.getBoolean(key, def);
        } catch (Throwable ignore) {
        }
        return def;
    }

    private void writeInt(String key, int value) {
        try {
            if (host != null) host.putInt(key, value);
        } catch (Throwable ignore) {
        }
        try {
            SharedPreferences p = prefs();
            if (p != null) p.edit().putInt(key, value).apply();
        } catch (Throwable ignore) {
        }
    }

    private void writeBool(String key, boolean value) {
        try {
            if (host != null) host.putBool(key, value);
        } catch (Throwable ignore) {
        }
        try {
            SharedPreferences p = prefs();
            if (p != null) p.edit().putBoolean(key, value).apply();
        } catch (Throwable ignore) {
        }
    }

    private void markTouched() {
        writeBool(KEY_TOUCHED, true);
        try {
            if (host != null) host.emit("ui_changed", "{\"style\":" + styleMode() + "}");
        } catch (Throwable ignore) {
        }
    }

    @Override
    public int styleMode() {
        int v = readInt(KEY_STYLE, readInt("style", 1));
        return v == 0 ? 0 : 1;
    }

    @Override
    public void fillSettings(List<HotRow> rows) {
        boolean exp = styleMode() == 1;
        rows.add(HotRow.header("Стиль (на льоту, без APK)"));
        rows.add(HotRow.switchRow("expressive",
                "Yougram Expressive",
                "Рідке скло + M3 сегменти 28dp. Вимкнено = Classic.",
                exp));
        rows.add(HotRow.button("go_expressive", "✨ Увімкнути Expressive", "Скло, сегменти, Monet"));
        rows.add(HotRow.button("go_classic", "⬜ Увімкнути Classic", "Стандартні картки 20dp"));
        rows.add(HotRow.header("Деталі Expressive"));
        rows.add(HotRow.switchRow(KEY_SQUARE_FAB, "Squircle FAB", "Квадратна кнопка 16dp", readBool(KEY_SQUARE_FAB, true)));
        rows.add(HotRow.switchRow(KEY_GLASS_MENU, "Скляне меню", "GlassMessageMenu", readBool(KEY_GLASS_MENU, true)));
        rows.add(HotRow.switchRow(KEY_M3_LIST, "M3-рядки", "Вищі рядки, кольорові іконки", readBool(KEY_M3_LIST, false)));
        rows.add(HotRow.switchRow(KEY_MINI_PLAYER, "MD3 міні-плеєр", "Плашка в чатах", readBool(KEY_MINI_PLAYER, true)));
        rows.add(HotRow.switchRow(KEY_LOADING, "M3-лоадери", "Нові індикатори", readBool(KEY_LOADING, true)));
        rows.add(HotRow.info("Перемикання застосовується одразу: хост перебудовує фрагменти."));
    }

    @Override
    public void onSettingsToggle(String key, boolean value) {
        if ("expressive".equals(key)) {
            writeInt(KEY_STYLE, value ? 1 : 0);
            writeInt("style", value ? 1 : 0);
            markTouched();
            return;
        }
        writeBool(key, value);
        markTouched();
    }

    @Override
    public void onSettingsAction(String rowId) {
        if ("go_expressive".equals(rowId)) {
            writeInt(KEY_STYLE, 1);
            writeInt("style", 1);
            markTouched();
            if (host != null) host.toast("✨ Expressive — застосовано на льоту");
        } else if ("go_classic".equals(rowId)) {
            writeInt(KEY_STYLE, 0);
            writeInt("style", 0);
            markTouched();
            if (host != null) host.toast("⬜ Classic — застосовано на льоту");
        }
    }

    @Override
    public Object createScreen(String screenId) {
        return null;
    }
}
