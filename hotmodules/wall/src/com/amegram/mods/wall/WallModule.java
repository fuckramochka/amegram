package com.amegram.mods.wall;

import android.content.Context;

import java.util.List;

import app.amegram.hot.api.HotHost;
import app.amegram.hot.api.HotModule;
import app.amegram.hot.api.HotRow;
import app.amegram.hot.api.HotServices;
import app.amegram.hot.api.HotWall;

/**
 * Хот-модуль «Стіна профілю».
 * Тонка обгортка навколо вбудованого в APK коду стіни (app.miogram.bridge.wall).
 * Реєструє HotServices.WALL та делегує відкриття стіни через reflection.
 */
public class WallModule implements HotModule, HotWall {

    private HotHost host;

    public WallModule() {
    }

    @Override
    public String moduleId() {
        return "wall";
    }

    @Override
    public void onAttach(Context appContext, HotHost host) {
        this.host = host;
        try {
            host.registerService(HotServices.WALL, this);
        } catch (Throwable ignore) {
        }
        try {
            host.log("wall attached");
        } catch (Throwable ignore) {
        }
    }

    @Override
    public void onDetach() {
        try {
            if (host != null) {
                host.unregisterService(HotServices.WALL);
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
        return "Стіна профілю";
    }

    @Override
    public void fillSettings(List<HotRow> rows) {
        rows.add(HotRow.header("Бета-модуль «Стіна»"));
        rows.add(HotRow.info("Стіна у власному профілі. Відкривається тапом по банеру свого профілю."));
        rows.add(HotRow.button("open_wall", "Відкрити свою стіну"));
        rows.add(HotRow.button("open_settings", "Налаштування бета-модуля"));
    }

    @Override
    public void onSettingsAction(String rowId) {
        if ("open_wall".equals(rowId)) {
            openWallScreen();
        } else if ("open_settings".equals(rowId)) {
            if (host != null) {
                host.openModuleScreen("settings");
            }
        }
    }

    @Override
    public Object createScreen(String screenId) {
        try {
            if ("settings".equals(screenId)) {
                return Class.forName("app.miogram.bridge.wall.WallBetaSettingsActivity")
                        .getDeclaredConstructor().newInstance();
            }
            return Class.forName("app.miogram.bridge.wall.WallActivity")
                    .getDeclaredConstructor().newInstance();
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String versionName() {
        return "1.0.0";
    }

    @Override
    public void openWallScreen() {
        if (host != null) {
            host.openModuleScreen("wall");
        }
    }
}
