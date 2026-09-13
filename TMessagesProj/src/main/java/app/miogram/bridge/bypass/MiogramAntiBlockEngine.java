package app.miogram.bridge.bypass;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.Components.BulletinFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.miogram.bridge.MiogramLocale;

/**
 * MiogramAntiBlockEngine:
 * High-performance anti-censorship engine specifically countering TSPU / RKN DPI blocking in RF.
 * Utilizes MTProto Fake-TLS with SNI domain spoofing (docs.yandex.ru, yandex.ru, ya.ru),
 * which bypasses EcoFilter inspection by masquerading as whitelisted domestic services.
 */
public class MiogramAntiBlockEngine implements NotificationCenter.NotificationCenterDelegate {

    private static final String TAG = "MiogramAntiBlockEngine";
    private static final String PREFS_NAME = "miogram_bypass";

    // TSPU throttling detection thresholds
    public static final long DEFAULT_DETECTION_TIMEOUT_MS = 20000L; // 20 seconds stuck connecting directly
    private static final long PROXY_STUCK_TIMEOUT_MS = 10000L; // 10 seconds stuck connecting to proxy
    private static final long MAX_ACCEPTABLE_PING_MS = 2500L;

    // Direct Telegram DC probes for deep diagnostic check
    public static final String[][] TELEGRAM_CORE_DCS = {
            {"DC2 (Amsterdam)", "149.154.167.51", "443"},
            {"DC4 (Amsterdam)", "149.154.167.91", "443"},
            {"DC1 (Miami)", "149.154.175.50", "443"},
            {"DC5 (Singapore)", "91.108.56.165", "443"}
    };

    public interface DiagnosticCallback {
        void onResult(boolean isBlocked, String diagnosticReport);
    }

    private static volatile MiogramAntiBlockEngine instance;

    public static MiogramAntiBlockEngine getInstance() {
        if (instance == null) {
            synchronized (MiogramAntiBlockEngine.class) {
                if (instance == null) {
                    instance = new MiogramAntiBlockEngine();
                }
            }
        }
        return instance;
    }

    public static class BypassServer {
        public String id;
        public String name;
        public String address;
        public int port;
        public String secret;
        public String sniDomain;
        public long ping = 0;
        public boolean available = false;
        public boolean checking = false;
        public long checkTime = 0;
        public boolean isCustom = false;

        public BypassServer(String id, String name, String address, int port, String secret, String sniDomain, boolean isCustom) {
            this.id = id;
            this.name = name;
            this.address = address;
            this.port = port;
            this.secret = secret;
            this.sniDomain = sniDomain;
            this.isCustom = isCustom;
        }

        public boolean isYandexSni() {
            if (sniDomain == null) return false;
            String lower = sniDomain.toLowerCase();
            return lower.contains("yandex") || lower.contains("ya.ru");
        }

        public SharedConfig.ProxyInfo toProxyInfo() {
            SharedConfig.ProxyInfo info = new SharedConfig.ProxyInfo(address, port, "", "", secret);
            info.ping = ping;
            info.available = available;
            info.availableCheckTime = checkTime;
            return info;
        }
    }

    private final SharedPreferences prefs;
    private final List<BypassServer> builtInServers = new ArrayList<>();
    private final List<BypassServer> customServers = new ArrayList<>();
    private final List<BypassServer> remoteServers = new ArrayList<>();

    private boolean isInitialized = false;
    private boolean isEngaging = false;

    private final Runnable throttleCheckRunnable = () -> {
        if (!isAutoBypassEnabled() || SharedConfig.isProxyEnabled()) {
            return;
        }
        int currentAccount = UserConfig.selectedAccount;
        int state = ConnectionsManager.getInstance(currentAccount).getConnectionState();
        if (state == ConnectionsManager.ConnectionStateConnecting && ApplicationLoader.isNetworkOnline()) {
            if (isDeepDiagnosticsEnabled()) {
                FileLog.d(TAG + ": Connection hanging. Performing comprehensive deep block check before engaging bypass...");
                performDeepBlockCheck((isBlocked, report) -> {
                    if (isBlocked) {
                        FileLog.d(TAG + ": Deep check CONFIRMED TSPU block! (" + report + "). Engaging Fake-TLS bypass!");
                        engageFastestBypassServer(true);
                    } else {
                        FileLog.d(TAG + ": Deep check passed: direct connection works or general network offline (" + report + "). Bypass NOT engaged.");
                    }
                });
            } else {
                FileLog.d(TAG + ": Direct connection hanging. Engaging bypass directly.");
                engageFastestBypassServer(true);
            }
        }
    };

    private final Runnable proxyStuckRunnable = () -> {
        if (!isAutoRotateEnabled() || !SharedConfig.isProxyEnabled()) {
            return;
        }
        int currentAccount = UserConfig.selectedAccount;
        int state = ConnectionsManager.getInstance(currentAccount).getConnectionState();
        if (state == ConnectionsManager.ConnectionStateConnectingToProxy && ApplicationLoader.isNetworkOnline()) {
            FileLog.d(TAG + ": Current proxy stuck connecting (>8s). Auto-rotating to next available Fake-TLS node!");
            rotateToNextAvailableServer();
        }
    };

    private MiogramAntiBlockEngine() {
        prefs = ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        initBuiltInServers();
        loadCustomServers();
        loadRemoteServers();
    }

    public void start() {
        if (isInitialized) return;
        isInitialized = true;

        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            NotificationCenter.getInstance(i).addObserver(this, NotificationCenter.didUpdateConnectionState);
        }
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.proxySettingsChanged);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.proxyCheckDone);

        FileLog.d(TAG + ": Engine started. Auto-bypass = " + isAutoBypassEnabled() + ", Prioritize Yandex = " + isPrioritizeYandexEnabled());

        // Background update of remote proxy pool if needed (once every 12h)
        fetchRemotePoolAsync(false, null);
    }

    private void initBuiltInServers() {
        builtInServers.clear();

        // 1. High-speed Yandex-masqueraded Fake-TLS endpoints (whitelisted by TSPU EcoFilter in RF)
        builtInServers.add(new BypassServer(
                "builtin_ya_1",
                "Яндекс РФ #1 (ya.ru)",
                "bonus.growthtrade.eu",
                443,
                "ee0fff11aae73289dec968ad854b3ffe0279612e7275",
                "ya.ru",
                false
        ));

        builtInServers.add(new BypassServer(
                "builtin_ya_2",
                "Яндекс РФ #2 (ya.ru)",
                "proxy.growthtrade.eu",
                443,
                "ee54e6e41e680ad7765a0679a5a24eb49b79612e7275",
                "ya.ru",
                false
        ));

        builtInServers.add(new BypassServer(
                "builtin_ya_3",
                "Яндекс РФ #3 (ya.ru)",
                "welcome.kisex.top",
                443,
                "ee0fff527a5a724628bab1a2ae5fff27d379612e7275",
                "ya.ru",
                false
        ));

        // 2. Yandex Browser & Search SNI
        builtInServers.add(new BypassServer(
                "builtin_yb_1",
                "Яндекс Браузер #1",
                "he.18.mtproto.ru",
                443,
                "ee2111222233334444555566667777888862726f777365722e79616e6465782e636f6d",
                "browser.yandex.com",
                false
        ));

        builtInServers.add(new BypassServer(
                "builtin_yb_2",
                "Яндекс Браузер #2",
                "he.de-fa.1.mtproto.ru",
                443,
                "ee2111222233334444555566667777888862726f777365722e79616e6465782e636f6d",
                "browser.yandex.com",
                false
        ));

        builtInServers.add(new BypassServer(
                "builtin_yb_3",
                "Яндекс Браузер #3",
                "he.de-nu.2.mtproto.ru",
                443,
                "ee2111222233334444555566667777888862726f777365722e79616e6465782e636f6d",
                "browser.yandex.com",
                false
        ));

        builtInServers.add(new BypassServer(
                "builtin_yd_1",
                "Яндекс Документи & Пошук",
                "85.17.89.193",
                443,
                "ee76f6583e6b509d946c56287a9ca59dc362726f7773696e672e79616e6465782e636f6d",
                "docs.yandex.ru",
                false
        ));

        builtInServers.add(new BypassServer(
                "builtin_ym_1",
                "Яндекс Карти (api-maps)",
                "150.241.70.0",
                443,
                "eefd4933b3863b668128a246b6cb2249016170692d6d6170732e79616e6465782e7275",
                "api-maps.yandex.ru",
                false
        ));

        // 3. Ozon / Domestic Fallbacks
        builtInServers.add(new BypassServer(
                "builtin_oz_1",
                "Озон РФ Резерв #1",
                "a01.lovely.lat",
                443,
                "eeef7017f26c9ecb71ed8d760999294318786170692e6f7a6f6e2e7275",
                "xapi.ozon.ru",
                false
        ));

        builtInServers.add(new BypassServer(
                "builtin_oz_2",
                "Озон РФ Резерв #2",
                "a02.lovely.lat",
                443,
                "eeef7017f26c9ecb71ed8d760999294318786170692e6f7a6f6e2e7275",
                "xapi.ozon.ru",
                false
        ));
    }

    // Config Getters & Setters
    public boolean isAutoBypassEnabled() {
        return prefs.getBoolean("auto_bypass", true);
    }

    public void setAutoBypassEnabled(boolean enabled) {
        prefs.edit().putBoolean("auto_bypass", enabled).apply();
    }

    public boolean isDeepDiagnosticsEnabled() {
        return prefs.getBoolean("deep_diagnostics", true);
    }

    public void setDeepDiagnosticsEnabled(boolean enabled) {
        prefs.edit().putBoolean("deep_diagnostics", enabled).apply();
    }

    public long getDetectionDelayMs() {
        return prefs.getLong("detection_delay_ms", DEFAULT_DETECTION_TIMEOUT_MS);
    }

    public void setDetectionDelayMs(long delayMs) {
        prefs.edit().putLong("detection_delay_ms", delayMs).apply();
    }

    public boolean isPrioritizeYandexEnabled() {
        return prefs.getBoolean("prioritize_yandex", true);
    }

    public void setPrioritizeYandexEnabled(boolean enabled) {
        prefs.edit().putBoolean("prioritize_yandex", enabled).apply();
    }

    public boolean isAutoRotateEnabled() {
        return prefs.getBoolean("auto_rotate", true);
    }

    public void setAutoRotateEnabled(boolean enabled) {
        prefs.edit().putBoolean("auto_rotate", enabled).apply();
    }

    public boolean isNotifyOnActivation() {
        return prefs.getBoolean("notify_on_activation", true);
    }

    public void setNotifyOnActivation(boolean enabled) {
        prefs.edit().putBoolean("notify_on_activation", enabled).apply();
    }

    public boolean isBypassActive() {
        if (!SharedConfig.isProxyEnabled() || SharedConfig.currentProxy == null) {
            return false;
        }
        String secret = SharedConfig.currentProxy.secret;
        String sni = extractSniDomain(secret);
        if (sni != null && (sni.contains("yandex") || sni.contains("ya.ru") || sni.contains("ozon.ru"))) {
            return true;
        }
        for (BypassServer s : getAllServers()) {
            if (s.address.equalsIgnoreCase(SharedConfig.currentProxy.address) && s.port == SharedConfig.currentProxy.port) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public BypassServer getCurrentActiveBypassServer() {
        if (!SharedConfig.isProxyEnabled() || SharedConfig.currentProxy == null) {
            return null;
        }
        for (BypassServer s : getAllServers()) {
            if (s.address.equalsIgnoreCase(SharedConfig.currentProxy.address) && s.port == SharedConfig.currentProxy.port) {
                return s;
            }
        }
        return null;
    }

    public synchronized List<BypassServer> getAllServers() {
        List<BypassServer> list = new ArrayList<>();
        list.addAll(customServers);
        list.addAll(builtInServers);
        list.addAll(remoteServers);
        return list;
    }

    public void addCustomServer(BypassServer server) {
        synchronized (this) {
            server.isCustom = true;
            customServers.add(0, server);
            saveCustomServers();
        }
    }

    public void removeCustomServer(BypassServer server) {
        synchronized (this) {
            customServers.remove(server);
            saveCustomServers();
        }
    }

    private void saveCustomServers() {
        try {
            JSONArray arr = new JSONArray();
            for (BypassServer s : customServers) {
                JSONObject obj = new JSONObject();
                obj.put("id", s.id);
                obj.put("name", s.name);
                obj.put("address", s.address);
                obj.put("port", s.port);
                obj.put("secret", s.secret);
                obj.put("sniDomain", s.sniDomain);
                arr.put(obj);
            }
            prefs.edit().putString("custom_servers", arr.toString()).apply();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void loadCustomServers() {
        customServers.clear();
        String json = prefs.getString("custom_servers", null);
        if (TextUtils.isEmpty(json)) return;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                customServers.add(new BypassServer(
                        obj.optString("id", "custom_" + i),
                        obj.optString("name", "Custom Server"),
                        obj.getString("address"),
                        obj.getInt("port"),
                        obj.getString("secret"),
                        obj.optString("sniDomain", extractSniDomain(obj.getString("secret"))),
                        true
                ));
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void saveRemoteServers() {
        try {
            JSONArray arr = new JSONArray();
            for (BypassServer s : remoteServers) {
                JSONObject obj = new JSONObject();
                obj.put("id", s.id);
                obj.put("name", s.name);
                obj.put("address", s.address);
                obj.put("port", s.port);
                obj.put("secret", s.secret);
                obj.put("sniDomain", s.sniDomain);
                arr.put(obj);
            }
            prefs.edit().putString("remote_servers", arr.toString()).apply();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void loadRemoteServers() {
        remoteServers.clear();
        String json = prefs.getString("remote_servers", null);
        if (TextUtils.isEmpty(json)) return;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                remoteServers.add(new BypassServer(
                        obj.optString("id", "remote_" + i),
                        obj.optString("name", "Cloud Node"),
                        obj.getString("address"),
                        obj.getInt("port"),
                        obj.getString("secret"),
                        obj.optString("sniDomain", extractSniDomain(obj.getString("secret"))),
                        false
                ));
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didUpdateConnectionState && account == UserConfig.selectedAccount) {
            onConnectionStateChanged(account);
        } else if (id == NotificationCenter.proxySettingsChanged) {
            AndroidUtilities.cancelRunOnUIThread(throttleCheckRunnable);
            AndroidUtilities.cancelRunOnUIThread(proxyStuckRunnable);
        } else if (id == NotificationCenter.proxyCheckDone) {
            // Updated ping
            if (args != null && args.length > 0 && args[0] instanceof SharedConfig.ProxyInfo) {
                SharedConfig.ProxyInfo info = (SharedConfig.ProxyInfo) args[0];
                for (BypassServer s : getAllServers()) {
                    if (s.address.equalsIgnoreCase(info.address) && s.port == info.port) {
                        s.ping = info.ping;
                        s.available = info.available;
                        s.checkTime = info.availableCheckTime;
                        s.checking = false;
                        break;
                    }
                }
            }
        }
    }

    private void onConnectionStateChanged(int account) {
        int state = ConnectionsManager.getInstance(account).getConnectionState();
        boolean proxyEnabled = SharedConfig.isProxyEnabled();

        if (!proxyEnabled) {
            if (state == ConnectionsManager.ConnectionStateConnecting) {
                if (ApplicationLoader.isNetworkOnline() && isAutoBypassEnabled()) {
                    AndroidUtilities.cancelRunOnUIThread(throttleCheckRunnable);
                    AndroidUtilities.runOnUIThread(throttleCheckRunnable, getDetectionDelayMs());
                }
            } else {
                AndroidUtilities.cancelRunOnUIThread(throttleCheckRunnable);
            }
        } else {
            AndroidUtilities.cancelRunOnUIThread(throttleCheckRunnable);
            if (state == ConnectionsManager.ConnectionStateConnectingToProxy) {
                if (isAutoRotateEnabled()) {
                    AndroidUtilities.cancelRunOnUIThread(proxyStuckRunnable);
                    AndroidUtilities.runOnUIThread(proxyStuckRunnable, PROXY_STUCK_TIMEOUT_MS);
                }
            } else {
                AndroidUtilities.cancelRunOnUIThread(proxyStuckRunnable);
            }
        }
    }

    /**
     * Conducts a comprehensive multi-DC socket probe to verify if Telegram
     * is genuinely blocked by government/ISP censorship (TSPU / DPI),
     * preventing false-positive bypass activation for users without blocks.
     */
    public void performDeepBlockCheck(DiagnosticCallback callback) {
        Utilities.globalQueue.postRunnable(() -> {
            if (!ApplicationLoader.isNetworkOnline()) {
                if (callback != null) {
                    AndroidUtilities.runOnUIThread(() -> callback.onResult(false, MiogramLocale.get("Немає підключення до мережі інтернет", "Нет подключения к сети интернет", "No internet network connection")));
                }
                return;
            }

            // 1. Probe neutral external internet host (1.1.1.1 or 8.8.8.8) to verify device has general network access
            boolean neutralReachable = false;
            try {
                java.net.Socket s = new java.net.Socket();
                s.connect(new java.net.InetSocketAddress("1.1.1.1", 443), 3000);
                neutralReachable = true;
                s.close();
            } catch (Throwable t) {
                try {
                    java.net.Socket s2 = new java.net.Socket();
                    s2.connect(new java.net.InetSocketAddress("8.8.8.8", 53), 3000);
                    neutralReachable = true;
                    s2.close();
                } catch (Throwable ignored) {}
            }

            if (!neutralReachable) {
                if (callback != null) {
                    AndroidUtilities.runOnUIThread(() -> callback.onResult(false, MiogramLocale.get("Загальний інтернет відсутній (не блокування Telegram)", "Общий интернет недоступен (не блокировка Telegram)", "General internet unavailable (not a Telegram block)")));
                }
                return;
            }

            // 2. Probe Telegram Core DCs directly (TCP port 443)
            int reachableCount = 0;
            int failedCount = 0;
            StringBuilder dcLog = new StringBuilder();

            for (String[] dc : TELEGRAM_CORE_DCS) {
                String name = dc[0];
                String ip = dc[1];
                int port = Integer.parseInt(dc[2]);
                long start = SystemClock.elapsedRealtime();
                try {
                    java.net.Socket socket = new java.net.Socket();
                    socket.connect(new java.net.InetSocketAddress(ip, port), 3500);
                    long rtt = SystemClock.elapsedRealtime() - start;
                    socket.close();
                    reachableCount++;
                    dcLog.append(name).append(": OK (").append(rtt).append("ms); ");
                } catch (Throwable e) {
                    failedCount++;
                    dcLog.append(name).append(": BLOCKED/TIMEOUT; ");
                }
            }

            final boolean isBlocked;
            final String report;

            // Block confirmed ONLY when neutral internet works BUT Telegram DCs are systematically dead/reset
            if (reachableCount == 0 && failedCount >= 3) {
                isBlocked = true;
                report = MiogramLocale.get("Виявлено блокування ТСПУ: сервери Telegram недоступні (" + dcLog.toString().trim() + ")",
                        "Обнаружена блокировка ТСПУ: серверы Telegram недоступны (" + dcLog.toString().trim() + ")",
                        "TSPU block detected: Telegram DCs unreachable (" + dcLog.toString().trim() + ")");
            } else {
                isBlocked = false;
                report = MiogramLocale.get("Блокувань не виявлено: пряме підключення до Telegram працює (" + dcLog.toString().trim() + ")",
                        "Блокировок не обнаружено: прямое подключение к Telegram работает (" + dcLog.toString().trim() + ")",
                        "No blocks detected: direct Telegram connection works (" + dcLog.toString().trim() + ")");
            }

            if (callback != null) {
                AndroidUtilities.runOnUIThread(() -> callback.onResult(isBlocked, report));
            }
        });
    }

    /**
     * Finds and engages the fastest responsive Fake-TLS server.
     */
    public void engageFastestBypassServer(boolean showBulletin) {
        if (isEngaging) return;
        isEngaging = true;

        List<BypassServer> pool = new ArrayList<>(getAllServers());
        if (pool.isEmpty()) {
            isEngaging = false;
            return;
        }

        // Check if we have an immediately available server with recent check (<2 min)
        long now = SystemClock.elapsedRealtime();
        BypassServer bestCandidate = null;
        for (BypassServer s : pool) {
            if (s.available && s.ping > 0 && (now - s.checkTime < 120000L)) {
                if (bestCandidate == null || s.ping < bestCandidate.ping) {
                    bestCandidate = s;
                }
            }
        }

        if (bestCandidate != null) {
            FileLog.d(TAG + ": Quick activate cached best node: " + bestCandidate.name + " (" + bestCandidate.ping + "ms)");
            activateServer(bestCandidate, showBulletin);
            isEngaging = false;
            return;
        }

        // Parallel ping testing to find responsive node
        int currentAccount = UserConfig.selectedAccount;
        final boolean[] activated = new boolean[]{false};

        // Sort candidates prioritizing Yandex if enabled
        Collections.sort(pool, (o1, o2) -> {
            int b1 = (isPrioritizeYandexEnabled() && o1.isYandexSni()) ? -1000 : 0;
            int b2 = (isPrioritizeYandexEnabled() && o2.isYandexSni()) ? -1000 : 0;
            return Integer.compare(b1, b2);
        });

        int checkCount = Math.min(pool.size(), 6);
        for (int i = 0; i < checkCount; i++) {
            final BypassServer s = pool.get(i);
            s.checking = true;
            ConnectionsManager.getInstance(currentAccount).checkProxy(
                    s.address,
                    s.port,
                    "",
                    "",
                    s.secret,
                    time -> AndroidUtilities.runOnUIThread(() -> {
                        s.checking = false;
                        s.checkTime = SystemClock.elapsedRealtime();
                        if (time >= 0) {
                            s.available = true;
                            s.ping = time;
                            if (!activated[0]) {
                                activated[0] = true;
                                isEngaging = false;
                                activateServer(s, showBulletin);
                            }
                        } else {
                            s.available = false;
                            s.ping = 0;
                        }
                    })
            );
        }

        // Fallback timeout: if no response within 4s, activate the first Yandex node anyway
        AndroidUtilities.runOnUIThread(() -> {
            if (!activated[0] && isEngaging) {
                activated[0] = true;
                isEngaging = false;
                activateServer(pool.get(0), showBulletin);
            }
        }, 4000L);
    }

    /**
     * Activates the selected BypassServer in Telegram's SharedConfig and ConnectionsManager.
     */
    public void activateServer(BypassServer server, boolean showBulletin) {
        if (server == null) return;
        AndroidUtilities.runOnUIThread(() -> {
            SharedConfig.ProxyInfo proxyInfo = server.toProxyInfo();
            SharedConfig.ProxyInfo added = SharedConfig.addProxy(proxyInfo);

            SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
            editor.putString("proxy_ip", added.address);
            editor.putString("proxy_pass", added.password);
            editor.putString("proxy_user", added.username);
            editor.putInt("proxy_port", added.port);
            editor.putString("proxy_secret", added.secret);
            editor.putBoolean("proxy_enabled", true);
            if (!added.secret.isEmpty()) {
                editor.putBoolean("proxy_enabled_calls", false);
            }
            editor.apply();

            SharedConfig.currentProxy = added;
            ConnectionsManager.setProxySettings(true, added.address, added.port, added.username, added.password, added.secret);
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);

            FileLog.d(TAG + ": Activated Fake-TLS node -> " + server.name + " (" + server.sniDomain + ")");

            if (showBulletin && isNotifyOnActivation()) {
                String domain = !TextUtils.isEmpty(server.sniDomain) ? server.sniDomain : "docs.yandex.ru";
                String pingText = server.ping > 0 ? (server.ping + " ms") : "Fake-TLS";
                try {
                    BulletinFactory.global().createSimpleBulletin(
                            R.raw.saved_messages,
                            MiogramLocale.get(
                                    "🛡️ Обхід блокувань активовано (" + domain + " • " + pingText + ")",
                                    "🛡️ Обход блокировок активирован (" + domain + " • " + pingText + ")",
                                    "🛡️ Anti-Block engaged (" + domain + " • " + pingText + ")"
                            )
                    ).show();
                } catch (Throwable ignored) {}
            }
        });
    }

    /**
     * Disables proxy and returns to direct connection.
     */
    public void disconnectBypass() {
        AndroidUtilities.runOnUIThread(() -> {
            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            preferences.edit().putBoolean("proxy_enabled", false).apply();
            ConnectionsManager.setProxySettings(false, "", 0, "", "", "");
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
        });
    }

    /**
     * Auto-rotates to another healthy server if current one degrades.
     */
    public void rotateToNextAvailableServer() {
        List<BypassServer> pool = new ArrayList<>(getAllServers());
        if (pool.size() <= 1) return;

        BypassServer current = getCurrentActiveBypassServer();
        BypassServer nextCandidate = null;

        for (BypassServer s : pool) {
            if (s == current || !s.available || s.ping <= 0 || s.ping > MAX_ACCEPTABLE_PING_MS) {
                continue;
            }
            if (nextCandidate == null || s.ping < nextCandidate.ping) {
                nextCandidate = s;
            }
        }

        if (nextCandidate != null) {
            FileLog.d(TAG + ": Rotating to next node: " + nextCandidate.name);
            activateServer(nextCandidate, true);
        } else {
            // Run fresh test
            engageFastestBypassServer(true);
        }
    }

    /**
     * Ping all servers in pool for UI latency testing.
     */
    public void pingAllServers(Runnable onFinished) {
        List<BypassServer> list = getAllServers();
        if (list.isEmpty()) {
            if (onFinished != null) onFinished.run();
            return;
        }

        int currentAccount = UserConfig.selectedAccount;
        int[] remaining = new int[]{list.size()};

        for (BypassServer s : list) {
            s.checking = true;
            ConnectionsManager.getInstance(currentAccount).checkProxy(
                    s.address,
                    s.port,
                    "",
                    "",
                    s.secret,
                    time -> AndroidUtilities.runOnUIThread(() -> {
                        s.checking = false;
                        s.checkTime = SystemClock.elapsedRealtime();
                        if (time >= 0) {
                            s.available = true;
                            s.ping = time;
                        } else {
                            s.available = false;
                            s.ping = 0;
                        }
                        remaining[0]--;
                        if (remaining[0] <= 0 && onFinished != null) {
                            onFinished.run();
                        }
                    })
            );
        }
    }

    /**
     * Fetches updated proxy list asynchronously from cloud sources.
     */
    public void fetchRemotePoolAsync(boolean force, @Nullable Utilities.Callback<Boolean> callback) {
        long now = System.currentTimeMillis();
        long lastFetch = prefs.getLong("last_remote_fetch", 0);
        if (!force && (now - lastFetch < 12 * 60 * 60 * 1000L)) {
            if (callback != null) callback.run(false);
            return;
        }

        Utilities.globalQueue.postRunnable(() -> {
            boolean success = false;
            try {
                URL url = new URL("https://raw.githubusercontent.com/oznurakaro04/telegram-proxy-live/main/proxies.txt");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Telegram-Bypass)");

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    String line;
                    List<BypassServer> fetched = new ArrayList<>();
                    int idx = 0;

                    while ((line = reader.readLine()) != null && idx < 20) {
                        line = line.trim();
                        if (!line.startsWith("https://t.me/proxy?")) continue;

                        try {
                            android.net.Uri uri = android.net.Uri.parse(line);
                            String server = uri.getQueryParameter("server");
                            int port = Utilities.parseInt(uri.getQueryParameter("port"));
                            String secret = uri.getQueryParameter("secret");

                            if (!TextUtils.isEmpty(server) && port > 0 && !TextUtils.isEmpty(secret) && secret.startsWith("ee")) {
                                String sni = extractSniDomain(secret);
                                if (sni != null && (sni.contains("yandex") || sni.contains("ya.ru") || sni.contains("ozon.ru") || sni.contains("vk.com"))) {
                                    String displayName = "Хмарний вузол: " + sni;
                                    fetched.add(new BypassServer("remote_" + idx, displayName, server, port, secret, sni, false));
                                    idx++;
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                    reader.close();

                    if (!fetched.isEmpty()) {
                        synchronized (MiogramAntiBlockEngine.this) {
                            remoteServers.clear();
                            remoteServers.addAll(fetched);
                            saveRemoteServers();
                        }
                        prefs.edit().putLong("last_remote_fetch", now).apply();
                        success = true;
                        FileLog.d(TAG + ": Fetched " + fetched.size() + " fresh Fake-TLS nodes from cloud.");
                    }
                }
            } catch (Exception e) {
                FileLog.e(TAG + ": Cloud fetch failed: " + e.getMessage());
            }

            final boolean finalSuccess = success;
            if (callback != null) {
                AndroidUtilities.runOnUIThread(() -> callback.run(finalSuccess));
            }
        });
    }

    /**
     * Extracts plain text SNI domain from an MTProto Fake-TLS secret (starts with 'ee' + 32-hex key + hex domain).
     */
    @Nullable
    public static String extractSniDomain(String secret) {
        if (TextUtils.isEmpty(secret) || !secret.startsWith("ee") || secret.length() <= 34) {
            return null;
        }
        try {
            String hex = secret.substring(34);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < hex.length() - 1; i += 2) {
                int val = Integer.parseInt(hex.substring(i, i + 2), 16);
                if (val >= 32 && val <= 126) {
                    sb.append((char) val);
                } else {
                    return null;
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Builds a Fake-TLS secret for any 32-character hex key with custom SNI masking (e.g. docs.yandex.ru).
     */
    public static String buildFakeTlsSecret(String hexKey32, String domain) {
        if (TextUtils.isEmpty(domain)) {
            return hexKey32;
        }
        StringBuilder sb = new StringBuilder("ee");
        if (!TextUtils.isEmpty(hexKey32) && hexKey32.length() == 32) {
            sb.append(hexKey32);
        } else {
            sb.append("00000000000000000000000000000000");
        }
        byte[] bytes = domain.getBytes(StandardCharsets.UTF_8);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
