package app.amegram.hot;

import app.amegram.hot.api.HotFileOrganization;
import app.amegram.hot.api.HotServices;

/** Stable client boundary for optional file organization behavior. */
public final class HotFileOrganizationGate {
    private HotFileOrganizationGate() {}

    public static String chatSubfolder(String chatTitle, long peerId) {
        try {
            HotFileOrganization service = HotModulesManager.getService(HotServices.FILE_ORGANIZATION);
            return service == null ? null : service.chatSubfolder(chatTitle, peerId);
        } catch (Throwable error) {
            return null;
        }
    }
}
