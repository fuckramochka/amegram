package app.amegram.hot;

import app.amegram.hot.api.HotServices;
import app.amegram.hot.api.HotTikTok;

/** Гейт TikTok-посилань: модуль tiktok відкриває у внутрішньому плеєрі. */
public final class HotTikTokGate {

    private HotTikTokGate() {
    }

    public static boolean isTikTokUrl(String url) {
        if (url == null) return false;
        String u = url.toLowerCase();
        return u.contains("tiktok.com") || u.contains("vt.tiktok") || u.contains("vm.tiktok")
                || u.contains("tiktokv.com") || u.contains("tiktokcdn.com");
    }

    /** true = модуль обробив. */
    public static boolean tryOpen(String url) {
        if (!isTikTokUrl(url)) return false;
        try {
            HotTikTok svc = HotModulesManager.getService(HotServices.TIKTOK);
            if (svc != null) return svc.openUrl(url);
        } catch (Throwable ignore) {
        }
        return false;
    }
}
