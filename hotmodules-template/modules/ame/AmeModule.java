package com.amegram.mods.ame;

import android.content.Context;

import java.util.List;

import app.amegram.hot.api.HotHost;
import app.amegram.hot.api.HotModule;
import app.amegram.hot.api.HotRow;

/**
 * Кастомізація профіля та інтерфейсу:
 * - XML-студія, картки профіля, градієнти, приховування полів (phone, bio, username).
 * - Оформлення чатів: картки діалогів, glass blur, кольори бульбашок, світіння, haptic.
 */
public class AmeModule implements HotModule {

    private HotHost host;

    @Override
    public String moduleId() {
        return "ame";
    }

    @Override
    public void onAttach(Context appContext, HotHost host) {
        this.host = host;
        AmeEngine.ensureInitialized();
    }

    @Override
    public void onDetach() {
        host = null;
    }

    private void openStudio() {
        try {
            org.telegram.ui.ActionBar.BaseFragment last =
                    org.telegram.ui.LaunchActivity.getLastFragment();
            android.content.Context actx = last != null ? last.getParentActivity() : null;
            if (actx == null && host != null) actx = host.context();
            if (actx == null) return;
            new AmeStudio(actx).show();
        } catch (Throwable ignore) {
        }
    }

    @Override
    public boolean hasSettings() {
        return true;
    }

    @Override
    public String settingsTitle() {
        return "Кастомізація";
    }

    @Override
    public void fillSettings(List<HotRow> rows) {
        rows.add(HotRow.header("Кастомізація профіля"));
        rows.add(HotRow.button("open_studio", "Аме-студія профіля", "Дизайн • XML • картки • градієнти"));
        rows.add(HotRow.switchRow("hide_phone", "Приховати номер",
                "Рядок номеру телефону в профілі", UiPrefs.isHideRowPhone()));
        rows.add(HotRow.switchRow("hide_bio", "Приховати опис (Bio)",
                "Рядок інформації про себе", UiPrefs.isHideRowBio()));
        rows.add(HotRow.switchRow("hide_username", "Приховати юзернейм",
                "Рядок @username у профілі", UiPrefs.isHideRowUsername()));

        rows.add(HotRow.header("Кастомізація інтерфейсу"));
        rows.add(HotRow.switchRow("ui_dialog_cards", "Картки діалогів",
                "Стиль карток з відступами у списку чатів", UiPrefs.isDialogCardsEnabled()));
        rows.add(HotRow.switchRow("ui_glass_blur", "Скляне розмиття (Glass Blur)",
                "Ефект напівпрозорого скла для шапки і панелей", UiPrefs.isGlassBlurEnabled()));
        rows.add(HotRow.switchRow("bubble_color_enabled", "Кольорові повідомлення",
                "Власні відтінки та градієнти бульбашок чату", UiPrefs.isBubbleColorEnabled()));
        rows.add(HotRow.switchRow("bubble_glow_enabled", "Світіння бульбашок",
                "М'яке неонове підсвічування повідомлень", UiPrefs.isBubbleGlowEnabled()));
        rows.add(HotRow.switchRow("name_glow_enabled", "Світіння нікнеймів",
                "Неоновий акцент для імен співрозмовників", UiPrefs.isNameGlowEnabled()));
        rows.add(HotRow.switchRow("avatar_ring_enabled", "Кільця навколо аватарів",
                "Неоновий контур аватарів та історій", UiPrefs.isAvatarRingEnabled()));
        rows.add(HotRow.switchRow("ui_haptic", "Тактильний відгук (Haptic)",
                "Приємна вібрація при натисканнях та свайпах", UiPrefs.isHapticEnabled()));

        rows.add(HotRow.info("Всі зміни застосовуються миттєво до інтерфейсу та профілю."));
    }

    @Override
    public void onSettingsToggle(String key, boolean value) {
        if ("hide_phone".equals(key)) {
            UiPrefs.setHideRowPhone(value);
        } else if ("hide_bio".equals(key)) {
            UiPrefs.setHideRowBio(value);
        } else if ("hide_username".equals(key)) {
            UiPrefs.setHideRowUsername(value);
        } else if ("ui_dialog_cards".equals(key)) {
            UiPrefs.setDialogCardsEnabled(value);
        } else if ("ui_glass_blur".equals(key)) {
            UiPrefs.setGlassBlurEnabled(value);
        } else if ("bubble_color_enabled".equals(key)) {
            UiPrefs.setBubbleColorEnabled(value);
        } else if ("bubble_glow_enabled".equals(key)) {
            UiPrefs.setBubbleGlowEnabled(value);
        } else if ("name_glow_enabled".equals(key)) {
            UiPrefs.setNameGlowEnabled(value);
        } else if ("avatar_ring_enabled".equals(key)) {
            UiPrefs.setAvatarRingEnabled(value);
        } else if ("ui_haptic".equals(key)) {
            UiPrefs.setHapticEnabled(value);
        }
    }

    @Override
    public void onSettingsAction(String rowId) {
        if ("open_studio".equals(rowId)) openStudio();
    }

    @Override
    public void fillHubRows(List<HotRow> rows) {
        rows.add(HotRow.button("ame_studio", "Аме-студія", "Кастомізація профіля та XML"));
    }

    @Override
    public void onHubAction(String rowId) {
        if ("ame_studio".equals(rowId)) openStudio();
    }
}
