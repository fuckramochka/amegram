package app.miogram.bridge.migration;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import app.miogram.bridge.MiogramLocale;

/**
 * Consumer that restores 100% of Telegram data, Room databases, and preferences
 * from the legacy package (com.exteraless.app) into the new package (app.amegram).
 */
public class AmegramMigrationConsumer {

    private static final String PREFS_NAME = "amegram_migration_prefs";
    private static final String KEY_MIGRATION_DONE = "migration_done";
    private static final String LEGACY_PACKAGE = "com.exteraless.app";
    private static final String MIGRATION_URI = "content://com.exteraless.app.migration/backup_archive";

    public static boolean isMigrationNeeded(Context context) {
        if (context == null) return false;
        if (LEGACY_PACKAGE.equals(context.getPackageName())) {
            return false; // We are in the legacy app itself
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_MIGRATION_DONE, false)) {
            return false;
        }
        // If current app already has active accounts, don't overwrite
        if (UserConfig.getActivatedAccountsCount() > 0) {
            prefs.edit().putBoolean(KEY_MIGRATION_DONE, true).apply();
            return false;
        }
        // Check if legacy app is installed
        try {
            context.getPackageManager().getPackageInfo(LEGACY_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {
            prefs.edit().putBoolean(KEY_MIGRATION_DONE, true).apply();
            return false;
        }
    }

    public static void runMigrationIfAvailable(Context context, Runnable onComplete) {
        if (!isMigrationNeeded(context)) {
            if (onComplete != null) onComplete.run();
            return;
        }

        new Thread(() -> {
            boolean success = false;
            try {
                Uri uri = Uri.parse(MIGRATION_URI);
                InputStream is = context.getContentResolver().openInputStream(uri);
                if (is != null) {
                    ZipInputStream zis = new ZipInputStream(is);
                    ZipEntry entry;
                    byte[] buffer = new byte[8192];

                    File filesDir = context.getFilesDir();
                    File dbDir = context.getDatabasePath("dummy").getParentFile();
                    File prefsDir = new File(context.getApplicationInfo().dataDir, "shared_prefs");

                    if (filesDir != null) filesDir.mkdirs();
                    if (dbDir != null) dbDir.mkdirs();
                    if (prefsDir != null) prefsDir.mkdirs();

                    while ((entry = zis.getNextEntry()) != null) {
                        String name = entry.getName();
                        File target = null;
                        if (name.startsWith("files/")) {
                            target = new File(filesDir, name.substring("files/".length()));
                        } else if (name.startsWith("databases/")) {
                            target = new File(dbDir, name.substring("databases/".length()));
                        } else if (name.startsWith("shared_prefs/")) {
                            target = new File(prefsDir, name.substring("shared_prefs/".length()));
                        }

                        if (target != null) {
                            if (entry.isDirectory()) {
                                target.mkdirs();
                            } else {
                                File parent = target.getParentFile();
                                if (parent != null) parent.mkdirs();

                                try (FileOutputStream fos = new FileOutputStream(target)) {
                                    int len;
                                    while ((len = zis.read(buffer)) != -1) {
                                        fos.write(buffer, 0, len);
                                    }
                                }
                            }
                        }
                        zis.closeEntry();
                    }
                    zis.close();
                    success = true;
                }
            } catch (Throwable t) {
                FileLog.e(t);
            }

            if (success) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit().putBoolean(KEY_MIGRATION_DONE, true).apply();

                // Reload UserConfig accounts
                for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                    try {
                        UserConfig.getInstance(a).loadConfig();
                    } catch (Throwable ignore) {}
                }
            }

            final boolean migrated = success;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (onComplete != null) onComplete.run();
                if (migrated) {
                    showMigrationSuccessNotification(context);
                    if (context instanceof Activity) {
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            promptUninstallLegacy((Activity) context);
                        }, 1500);
                    }
                }
            });
        }, "AmegramMigrationConsumerThread").start();
    }

    public static void showMigrationSuccessNotification(Context context) {
        if (context == null) return;
        Toast.makeText(context, MiogramLocale.get(
                "Усі дані та акаунти успішно перенесено в Amegram!",
                "Все данные и аккаунты успешно перенесены в Amegram!",
                "All data and accounts successfully migrated to Amegram!"),
                Toast.LENGTH_LONG).show();
    }

    public static void promptUninstallLegacy(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(MiogramLocale.get("Видалити старий додаток?", "Удалить старое приложение?", "Uninstall old app?"));
        builder.setMessage(MiogramLocale.get(
                "Дані перенесені в Amegram. Ви можете видалити старий додаток Miogram для звільнення памʼяті.",
                "Данные перенесены в Amegram. Вы можете удалить старое приложение Miogram для освобождения памяти.",
                "Data has been migrated to Amegram. You can uninstall the old Miogram app to free up storage."));
        builder.setPositiveButton(MiogramLocale.get("Видалити", "Удалить", "Uninstall"), (d, w) -> {
            d.dismiss();
            try {
                Intent intent = new Intent(Intent.ACTION_DELETE);
                intent.setData(Uri.parse("package:" + LEGACY_PACKAGE));
                activity.startActivity(intent);
            } catch (Throwable t) {
                FileLog.e(t);
            }
        });
        builder.setNegativeButton(MiogramLocale.get("Пізніше", "Позже", "Later"), (d, w) -> d.dismiss());
        try {
            builder.show();
        } catch (Throwable ignore) {}
    }
}
