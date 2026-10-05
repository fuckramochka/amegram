package com.amegram.mods.tiktok;

import android.content.Context;

import java.util.List;

import app.amegram.hot.api.HotHost;
import app.amegram.hot.api.HotModule;
import app.amegram.hot.api.HotRow;
import app.amegram.hot.api.HotServices;
import app.amegram.hot.api.HotTikTok;

/** TikTok MI: внутрішній плеєр без водяних знаків та екосистема. Портовано з ecosystem. */
public class TikModule implements HotModule, HotTikTok {

    private HotHost host;

    @Override
    public String moduleId() {
        return "tiktok";
    }

    @Override
    public void onAttach(Context appContext, HotHost host) {
        this.host = host;
        host.registerService(HotServices.TIKTOK, this);
    }

    @Override
    public void onDetach() {
        if (host != null) host.unregisterService(HotServices.TIKTOK);
        host = null;
    }

    @Override
    public boolean openUrl(String url) {
        try {
            if (!TikBridge.isPlayInAppEnabled()) return false;
            android.content.Context actx = null;
            try {
                org.telegram.ui.ActionBar.BaseFragment last =
                        org.telegram.ui.LaunchActivity.getLastFragment();
                if (last != null) actx = last.getParentActivity();
            } catch (Throwable ignore) {
            }
            if (actx == null && host != null) actx = host.context();
            TikPlayer.show(actx, url);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    private void openSyncSheet() {
        try {
            org.telegram.ui.ActionBar.BaseFragment last =
                    org.telegram.ui.LaunchActivity.getLastFragment();
            android.content.Context actx = last != null ? last.getParentActivity() : null;
            if (actx == null && host != null) actx = host.context();
            if (actx == null) return;
            new TikLinkSheet(actx).show();
        } catch (Throwable ignore) {
        }
    }

    @Override
    public boolean hasSettings() {
        return true;
    }

    @Override
    public String settingsTitle() {
        return "TikTok MI";
    }

    @Override
    public void fillSettings(List<HotRow> rows) {
        rows.add(HotRow.header("Плеєр відео"));
        rows.add(HotRow.switchRow("play_in_app", "Дивитись в Амэграм",
                "Посилання tiktok відкриваються у внутрішньому плеєрі без ватермарок",
                TikBridge.isPlayInAppEnabled()));
        rows.add(HotRow.switchRow("open_direct", "Відкривати одразу",
                "Без проміжного діалогу підтвердження", TikBridge.isOpenDirectEnabled()));
        rows.add(HotRow.switchRow("clean_urls", "Чистити посилання",
                "Прибирати трекінг-параметри при відправці та копіюванні", TikBridge.isCleanUrlsEnabled()));

        rows.add(HotRow.header("Екосистема"));
        rows.add(HotRow.button("open_sync", "Синхронізація TikTok MI",
                "Підключити акаунт та закладки"));
        rows.add(HotRow.info("Екосистема дозволяє миттєво відкривати та завантажувати ролики з TikTok у високій якості."));
    }

    @Override
    public void onSettingsToggle(String key, boolean value) {
        if ("play_in_app".equals(key)) TikBridge.setPlayInAppEnabled(value);
        else if ("open_direct".equals(key)) TikBridge.setOpenDirectEnabled(value);
        else if ("clean_urls".equals(key)) TikBridge.setCleanUrlsEnabled(value);
    }

    @Override
    public void onSettingsAction(String rowId) {
        if ("open_sync".equals(rowId)) {
            openSyncSheet();
        }
    }

    @Override
    public void fillHubRows(List<HotRow> rows) {
        rows.add(HotRow.button("tiktok_hub", "TikTok MI", "Екосистема та відеоплеєр"));
    }

    @Override
    public void onHubAction(String rowId) {
        if ("tiktok_hub".equals(rowId)) {
            openSyncSheet();
        }
    }
}
