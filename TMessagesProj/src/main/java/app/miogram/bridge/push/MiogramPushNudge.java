package app.miogram.bridge.push;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.Settings;

import org.telegram.ui.ActionBar.BaseFragment;

import app.miogram.bridge.MiogramLocale;

/**
 * Одноразовий (раз на 14 днів) нудж про зняття оптимізації батареї.
 * Це головна причина симптому "десь через час сповіщення перестають
 * приходити": Doze + вбивця фону зупиняють процес, а FCM-пуші на
 * кастомних api_id/прошивках не будять. Без вайтліста не допоможе
 * ні keep-alive, ні перереєстрація токена.
 */
public final class MiogramPushNudge {

    private static final String PREFS = "miogram_push_nudge";
    private static final String KEY_LAST = "last_nudge";
    private static final long INTERVAL_MS = 14L * 24 * 60 * 60 * 1000L;

    private MiogramPushNudge() {
    }

    public static boolean isBatteryRestricted(Context context) {
        if (context == null) return false;
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            return pm != null && !pm.isIgnoringBatteryOptimizations(context.getPackageName());
        } catch (Throwable ignore) {
            return false;
        }
    }

    public static void maybeShow(BaseFragment fragment) {
        if (fragment == null || fragment.getParentActivity() == null) return;
        Context context = fragment.getParentActivity();
        if (!isBatteryRestricted(context)) return;
        SharedPreferences prefs;
        try {
            prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        } catch (Throwable ignore) {
            return;
        }
        long last = prefs.getLong(KEY_LAST, 0);
        if (System.currentTimeMillis() - last < INTERVAL_MS) return;
        prefs.edit().putLong(KEY_LAST, System.currentTimeMillis()).apply();

        try {
            org.telegram.ui.ActionBar.AlertDialog.Builder b =
                    new org.telegram.ui.ActionBar.AlertDialog.Builder(context);
            b.setTitle(MiogramLocale.get("Сповіщення можуть зникати",
                    "Уведомления могут пропадать", "Notifications may stop"));
            b.setMessage(MiogramLocale.get(
                    "Система обмежує AmeGram у фоні. Через якийсь час без відкриття додатка сповіщення перестануть приходити. Зніміть оптимізацію батареї — це головний фікс.",
                    "Система ограничивает AmeGram в фоне. Через какое-то время без открытия приложения уведомления перестанут приходить. Снимите оптимизацию батареи — это главный фикс.",
                    "The system restricts AmeGram in background. After a while without opening the app, notifications will stop. Lift the battery restriction — this is the main fix."));
            b.setPositiveButton(MiogramLocale.get("Зняти обмеження", "Снять ограничение", "Unrestrict"),
                    (d, w) -> {
                        try {
                            Intent intent = new Intent(
                                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.parse("package:" + context.getPackageName()));
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            context.startActivity(intent);
                        } catch (Throwable t) {
                            try {
                                Intent intent = new Intent(
                                        Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                context.startActivity(intent);
                            } catch (Throwable ignore) {
                            }
                        }
                    });
            b.setNegativeButton(MiogramLocale.get("Пізніше", "Позже", "Later"), null);
            b.show();
        } catch (Throwable ignore) {
        }
    }
}
