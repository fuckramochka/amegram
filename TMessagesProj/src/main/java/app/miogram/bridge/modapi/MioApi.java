package app.miogram.bridge.modapi;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Stable mod-API surface of Miogram (e.g. for a TikTok mod built on it).
 *
 * <p>Rules the platform guarantees:
 * <ul>
 *   <li>{@link #MIO_API_VERSION} only ever grows; mods declare the minimum
 *       they need via {@link #supports(int)}.</li>
 *   <li>Everything reachable from here keeps its signature within a major
 *       version — engines may gain methods, never lose them.</li>
 *   <li>{@link #features()} lets a mod degrade gracefully when a capability
 *       is off (no key, no link, old build).</li>
 * </ul>
 */
public final class MioApi {

    private MioApi() {}

    /** Mod-API contract version. Bump on any breaking change. */
    public static final int MIO_API_VERSION = 1;

    public static boolean supports(int api) {
        return api <= MIO_API_VERSION;
    }

    /**
     * Capability flags for feature detection. Keys: downloader, ai, vault,
     * updater, presence, hooks, tools, companion. "ai" reflects whether an
     * API key is configured right now; the rest report engine availability.
     */
    public static Map<String, Boolean> features() {
        LinkedHashMap<String, Boolean> out = new LinkedHashMap<>();
        out.put("downloader", true);
        out.put("hooks", true);
        out.put("tools", true);
        out.put("updater", true);
        out.put("companion", true);
        out.put("vault", true);
        out.put("presence", true);
        boolean ai = false;
        try {
            ai = app.miogram.bridge.ai.MiogramAiService.hasApiKey();
        } catch (Throwable ignore) {}
        out.put("ai", ai);
        return out;
    }

    private static final AtomicBoolean toolsRegistered = new AtomicBoolean(false);

    /**
     * Registers mod-API tools (download_media, ...) into the shared
     * {@code MioTool} runtime exactly once. Safe to call any time.
     */
    public static void ensureTools() {
        if (!toolsRegistered.compareAndSet(false, true)) return;
        try {
            app.miogram.bridge.ai.tools.MioTool.register(new app.miogram.bridge.ai.tools.MioTool.Def(
                    "download_media",
                    "Download media",
                    "download_media(url) - resolve a TikTok/YouTube/Instagram/X/Pinterest page (or direct file) and save it locally. Returns the file path.",
                    false,
                    (account, params, cb) -> {
                        String url = params != null ? params.optString("url", "") : "";
                        if (url.isEmpty() && params != null) url = params.optString("text", "");
                        if (url.isEmpty()) {
                            cb.run("No url provided.");
                            return;
                        }
                        MioDownload.download(url, null, null, new MioDownload.FileCallback() {
                            @Override
                            public void onDone(java.io.File file) {
                                cb.run(file.getAbsolutePath());
                            }

                            @Override
                            public void onError(String error) {
                                cb.run("Download failed: " + (error != null ? error : ""));
                            }
                        });
                    },
                    "media",
                    1
            ));
        } catch (Throwable ignore) {}
    }
}
