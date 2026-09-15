// Miogram mod-API bridge (separate APKs, e.g. a TikTok mod).
// Copy this file into the client app under the SAME path
// (src/main/aidl/app/miogram/bridge/modapi/IMioBridge.aidl) and bind with
// action "app.miogram.bridge.modapi.BIND_BRIDGE".
//
// Threading: calls run on Miogram binder pool threads (never UI).
// resolveMedia/fetchToCache/ai* may block for seconds — call off the UI
// thread. vaultList entries are capped (200). openFile only serves files
// under Miogram's own files/cache dirs.
interface IMioBridge {
    /** MioApi contract version (1). Reject if greater than yours. */
    int getApiVersion();

    /** JSON map from MioApi.features(). */
    String getFeatures();

    /** Resolve a TT/YT/IG/X/Pinterest page to {"url","title","filename"} or {"error"}. */
    String resolveMedia(String pageUrl);

    /** Resolve + download into Miogram cache. Returns {"path"} or {"error"}. */
    String fetchToCache(String pageUrl, String filename);

    /** Read-only fd for a path previously returned by fetchToCache. */
    ParcelFileDescriptor openFile(String path);

    /** Translate text, returns {"text"} or {"error"}. Needs AI key on Miogram side. */
    String aiTranslate(String text, String targetLang);

    /** Bullet summary, returns {"text"} or {"error"}. */
    String aiSummarize(String text);

    /** Queue a world-readable file into the account vault. Returns {"accepted"} or {"error"}. */
    String vaultPut(int account, String name, String srcPath);

    /** Vault manifests as JSON array (capped). */
    String vaultList(int account, int limit);
}
