package app.miogram.bridge.vault;

import android.content.Context;
import android.content.SharedPreferences;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Clean, rock-solid manager for Double Bottom (Подвійне сховище).
 * Provides:
 *  - Real Passcode (full access)
 *  - Emergency / Duress Passcode (decoy mode)
 *  - Designated Emergency Account
 *  - Whitelisted / Allowed Chats per account in Emergency mode
 *  - Panic Logout option
 */
public class MiogramDoubleBottomManager {

    private static final String PREFS_NAME = "miogram_double_bottom";
    private static final String KEY_REAL_PIN = "real_pin";
    private static final String KEY_DURESS_PIN = "duress_pin";
    private static final String KEY_DECOY_ACCOUNT = "decoy_account";
    private static final String KEY_PANIC_LOGOUT = "panic_logout";
    private static final String PREF_ALLOWED_PREFIX = "allowed_chats_";

    public static final int VERDICT_NONE = 0;
    public static final int VERDICT_REAL = 1;
    public static final int VERDICT_DURESS = 2;

    private static final String KEY_DURESS_ACTIVE = "duress_active";

    public static volatile boolean isDuressActive = false;

    private static SharedPreferences getPrefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isConfigured() {
        return hasRealPasscode() && hasDuressPasscode();
    }

    /** Non-revealing existence checks — raw PINs are never readable (salted-hash storage). */
    public static boolean hasRealPasscode() {
        String stored = getPrefs().getString(KEY_REAL_PIN, "");
        return stored != null && !stored.isEmpty();
    }

    public static boolean hasDuressPasscode() {
        String stored = getPrefs().getString(KEY_DURESS_PIN, "");
        return stored != null && !stored.isEmpty();
    }

    public static boolean isDuressActive() {
        if (!isModuleEnabled() || !isConfigured()) {
            return false;
        }
        return isDuressActive || getPrefs().getBoolean(KEY_DURESS_ACTIVE, false);
    }

    /**
     * Amegram Modules hub master switch ("doublebottom", default ON).
     * Read by prefs FILE NAME so this file keeps zero new imports.
     * OFF = duress workspace can never activate; stock lock screen applies.
     */
    public static boolean isModuleEnabled() {
        try {
            Context ctx = ApplicationLoader.applicationContext;
            if (ctx != null) {
                return ctx.getSharedPreferences("amegram_module_prefs", Context.MODE_PRIVATE)
                        .getBoolean("doublebottom_enabled", true);
            }
        } catch (Throwable ignore) {
        }
        return true;
    }

    public static void setDuressActive(boolean active) {
        isDuressActive = active;
        getPrefs().edit().putBoolean(KEY_DURESS_ACTIVE, active).apply();
    }

    /**
     * @deprecated Returns "••••" when a PIN is set, "" otherwise. Raw PINs are
     * never readable anymore (salted-hash storage). Kept for API compat.
     */
    @Deprecated
    public static String getRealPasscode() {
        return hasRealPasscode() ? "••••" : "";
    }

    public static void setRealPasscode(String pin) {
        String trimmed = pin != null ? pin.trim() : "";
        storePin(KEY_REAL_PIN, trimmed);
        if (!trimmed.isEmpty()) {
            syncToTelegramPasscode(trimmed);
        }
    }

    /**
     * @deprecated See {@link #getRealPasscode()}.
     */
    @Deprecated
    public static String getDuressPasscode() {
        return hasDuressPasscode() ? "••••" : "";
    }

    public static void setDuressPasscode(String pin) {
        storePin(KEY_DURESS_PIN, pin);
    }

    public static int getDecoyAccount() {
        return getPrefs().getInt(KEY_DECOY_ACCOUNT, -1);
    }

    public static void setDecoyAccount(int account) {
        getPrefs().edit().putInt(KEY_DECOY_ACCOUNT, account).apply();
    }

    public static boolean isPanicLogoutEnabled() {
        return getPrefs().getBoolean(KEY_PANIC_LOGOUT, false);
    }

    public static void setPanicLogoutEnabled(boolean enabled) {
        getPrefs().edit().putBoolean(KEY_PANIC_LOGOUT, enabled).apply();
    }

    public static Set<Long> getAllowedDialogIds(int account) {
        Set<String> stringSet = getPrefs().getStringSet(PREF_ALLOWED_PREFIX + account, null);
        Set<Long> result = new HashSet<>();
        if (stringSet != null) {
            for (String s : stringSet) {
                try {
                    result.add(Long.parseLong(s));
                } catch (Exception ignore) {}
            }
        }
        return result;
    }

    public static void setAllowedDialogIds(int account, Collection<Long> ids) {
        Set<String> stringSet = new HashSet<>();
        if (ids != null) {
            for (Long id : ids) {
                if (id != null) {
                    stringSet.add(String.valueOf(id));
                }
            }
        }
        getPrefs().edit().putStringSet(PREF_ALLOWED_PREFIX + account, stringSet).apply();
    }

    public static boolean hasAllowedDialogs(int account) {
        Set<String> set = getPrefs().getStringSet(PREF_ALLOWED_PREFIX + account, null);
        return set != null && !set.isEmpty();
    }

    public static boolean isChatAllowed(int account, long dialogId) {
        if (!isDuressActive()) {
            return true;
        }
        int decoy = getDecoyAccount();
        if (decoy >= 0) {
            if (account == decoy) {
                if (!hasAllowedDialogs(account)) {
                    return true;
                }
                return getAllowedDialogIds(account).contains(dialogId);
            }
            return false;
        }
        if (!hasAllowedDialogs(account)) {
            return false;
        }
        return getAllowedDialogIds(account).contains(dialogId);
    }

    public static int checkPasscode(String pin) {
        if (pin == null || pin.isEmpty()) {
            return VERDICT_NONE;
        }
        if (verifyPin(KEY_REAL_PIN, pin)) {
            return VERDICT_REAL;
        }
        if (verifyPin(KEY_DURESS_PIN, pin)) {
            return VERDICT_DURESS;
        }
        return VERDICT_NONE;
    }

    /** Async PIN verdict for the lock screen. Argon2id must never run on the UI thread. */
    public interface PinVerdictCallback {
        void onVerdict(int verdict);
    }

    private static final java.util.concurrent.ExecutorService PIN_EXECUTOR =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "doublebottom-pin");
                t.setPriority(Thread.NORM_PRIORITY - 1);
                return t;
            });

    public static void checkPasscodeAsync(final String pin, final PinVerdictCallback callback) {
        PIN_EXECUTOR.execute(() -> {
            int verdict = VERDICT_NONE;
            try {
                // Module OFF: duress slot ignored (real PIN still unlocks).
                // ON: always verify BOTH slots (timing equalization).
                boolean real = pin != null && !pin.isEmpty() && verifyPin(KEY_REAL_PIN, pin);
                boolean duress = isModuleEnabled() && pin != null && !pin.isEmpty()
                        && verifyPin(KEY_DURESS_PIN, pin);
                verdict = real ? VERDICT_REAL : (duress ? VERDICT_DURESS : VERDICT_NONE);
            } catch (Throwable t) {
                verdict = VERDICT_NONE;
            }
            final int result = verdict;
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    callback.onVerdict(result);
                } catch (Throwable ignore) {
                }
            });
        });
    }

    private static final String HASH_PREFIX = "v1$";
    private static final String HASH2_PREFIX = "v2$argon2id$";

    // Single memory-hard profile for unlock-time PINs: ~16 MiB, fast enough
    // for the lock screen (~100-200ms off-thread), brutal for 4-digit brute force.
    private static final int PIN_KDF_MEMORY_KIB = 16384;
    private static final int PIN_KDF_ITERATIONS = 2;
    private static final int PIN_KDF_PARALLELISM = 1;
    private static final int PIN_SALT_BYTES = 16;
    private static final int PIN_TAG_BYTES = 32;

    private static byte[] argon2id(byte[] pinUtf8, byte[] salt) {
        Argon2Parameters params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withSalt(salt)
                .withIterations(PIN_KDF_ITERATIONS)
                .withMemoryAsKB(PIN_KDF_MEMORY_KIB)
                .withParallelism(PIN_KDF_PARALLELISM)
                .build();
        Argon2BytesGenerator gen = new Argon2BytesGenerator();
        gen.init(params);
        byte[] out = new byte[PIN_TAG_BYTES];
        gen.generateBytes(pinUtf8, out);
        return out;
    }

    private static void storePin(String key, String pin) {
        String trimmed = pin != null ? pin.trim() : "";
        if (trimmed.isEmpty()) {
            getPrefs().edit().remove(key).apply();
            return;
        }
        // v2: memory-hard Argon2id verifier. PIN bytes are zeroized after use.
        byte[] salt = new byte[PIN_SALT_BYTES];
        Utilities.random.nextBytes(salt);
        byte[] pinBytes = trimmed.getBytes(StandardCharsets.UTF_8);
        try {
            byte[] tag = argon2id(pinBytes, salt);
            try {
                String record = HASH2_PREFIX
                        + "m=" + PIN_KDF_MEMORY_KIB + ",t=" + PIN_KDF_ITERATIONS + ",p=" + PIN_KDF_PARALLELISM
                        + "$" + Utilities.bytesToHex(salt) + "$" + Utilities.bytesToHex(tag);
                getPrefs().edit().putString(key, record).apply();
            } finally {
                Arrays.fill(tag, (byte) 0);
            }
        } finally {
            Arrays.fill(pinBytes, (byte) 0);
            Arrays.fill(salt, (byte) 0);
        }
    }

    private static boolean verifyPin(String key, String pin) {
        String stored = getPrefs().getString(key, "");
        if (stored == null || stored.isEmpty() || pin == null) return false;
        if (stored.startsWith(HASH2_PREFIX)) {
            return verifyPinV2(key, stored, pin);
        }
        if (stored.startsWith(HASH_PREFIX)) {
            try {
                String[] parts = stored.split("\\$");
                if (parts.length != 3) return false;
                String saltHex = parts[1];
                String expectHex = parts[2];
                byte[] both = (saltHex + "\n" + pin).getBytes(StandardCharsets.UTF_8);
                String actualHex = Utilities.bytesToHex(Utilities.computeSHA256(both, 0, both.length));
                boolean ok = constantTimeEquals(expectHex, actualHex);
                if (ok) {
                    // Transparent upgrade: v1 -> memory-hard v2 on next successful unlock.
                    storePin(key, pin);
                }
                return ok;
            } catch (Throwable t) {
                return false;
            }
        }
        // Legacy plaintext entry: verify, then transparently migrate to hash.
        boolean ok = constantTimeEquals(stored, pin);
        if (ok) {
            try {
                storePin(key, pin);
            } catch (Throwable ignored) {}
        }
        return ok;
    }

    private static boolean verifyPinV2(String key, String stored, String pin) {
        try {
            // v2$argon2id$m=..,t=..,p=..$saltHex$tagHex  -> 5 segments
            String[] parts = stored.split("\\$");
            if (parts.length != 5) return false;
            String[] cost = parts[2].split(",");
            int m = PIN_KDF_MEMORY_KIB, t = PIN_KDF_ITERATIONS, p = PIN_KDF_PARALLELISM;
            for (String c : cost) {
                if (c.startsWith("m=")) m = Integer.parseInt(c.substring(2));
                else if (c.startsWith("t=")) t = Integer.parseInt(c.substring(2));
                else if (c.startsWith("p=")) p = Integer.parseInt(c.substring(2));
            }
            byte[] salt = Utilities.hexToBytes(parts[3]);
            byte[] expect = Utilities.hexToBytes(parts[4]);
            byte[] pinBytes = pin.getBytes(StandardCharsets.UTF_8);
            byte[] actual;
            try {
                Argon2Parameters params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                        .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                        .withSalt(salt)
                        .withIterations(t)
                        .withMemoryAsKB(m)
                        .withParallelism(p)
                        .build();
                Argon2BytesGenerator gen = new Argon2BytesGenerator();
                gen.init(params);
                actual = new byte[expect.length];
                gen.generateBytes(pinBytes, actual);
            } finally {
                Arrays.fill(pinBytes, (byte) 0);
                Arrays.fill(salt, (byte) 0);
            }
            boolean ok = constantTimeBytesEquals(expect, actual);
            Arrays.fill(actual, (byte) 0);
            Arrays.fill(expect, (byte) 0);
            return ok;
        } catch (Throwable th) {
            return false;
        }
    }

    private static boolean constantTimeBytesEquals(byte[] a, byte[] b) {
        if (a == null || b == null || a.length != b.length) return false;
        int diff = 0;
        for (int i = 0; i < a.length; i++) diff |= a[i] ^ b[i];
        return diff == 0;
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] ab = a.getBytes(StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(StandardCharsets.UTF_8);
        if (ab.length != bb.length) return false;
        int diff = 0;
        for (int i = 0; i < ab.length; i++) diff |= ab[i] ^ bb[i];
        return diff == 0;
    }

    public static void clearAll() {
        getPrefs().edit().clear().apply();
        isDuressActive = false;
        SharedConfig.passcodeHash = "";
        SharedConfig.appLocked = false;
        SharedConfig.saveConfig();
    }

    private static void syncToTelegramPasscode(String pin) {
        try {
            SharedConfig.passcodeSalt = new byte[16];
            Utilities.random.nextBytes(SharedConfig.passcodeSalt);
            byte[] passcodeBytes = pin.getBytes(StandardCharsets.UTF_8);
            byte[] bytes = new byte[32 + passcodeBytes.length];
            System.arraycopy(SharedConfig.passcodeSalt, 0, bytes, 0, 16);
            System.arraycopy(passcodeBytes, 0, bytes, 16, passcodeBytes.length);
            System.arraycopy(SharedConfig.passcodeSalt, 0, bytes, passcodeBytes.length + 16, 16);
            SharedConfig.passcodeHash = Utilities.bytesToHex(Utilities.computeSHA256(bytes, 0, bytes.length));
            SharedConfig.passcodeType = SharedConfig.PASSCODE_TYPE_PIN;
            SharedConfig.appLocked = false;
            SharedConfig.saveConfig();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }
}
