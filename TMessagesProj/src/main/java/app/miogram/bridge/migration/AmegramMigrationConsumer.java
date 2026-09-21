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
    private static final String[] LEGACY_PACKAGES = new String[]{"app.miogram", "com.exteraless.app", "org.telegram.messenger"};

    public static boolean isMigrationNeeded(Context context) {
        if (context == null) return false;
        String myPkg = context.getPackageName();
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_MIGRATION_DONE, false)) {
            return false;
        }
        // If current app already has active accounts, don't overwrite
        if (UserConfig.getActivatedAccountsCount() > 0) {
            prefs.edit().putBoolean(KEY_MIGRATION_DONE, true).apply();
            return false;
        }
        // Check if any legacy app is installed
        for (String pkg : LEGACY_PACKAGES) {
            if (pkg.equals(myPkg)) continue;
            try {
                context.getPackageManager().getPackageInfo(pkg, 0);
                return true;
            } catch (PackageManager.NameNotFoundException ignored) {}
        }
        // Also check if legacy shared prefs exist locally
        File prefsDir = new File(context.getApplicationInfo().dataDir, "shared_prefs");
        if (prefsDir.exists() && prefsDir.isDirectory()) {
            File[] files = prefsDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.getName().startsWith("miogram_")) {
                        return true;
                    }
                }
            }
        }
        prefs.edit().putBoolean(KEY_MIGRATION_DONE, true).apply();
        return false;
    }

    public static void runMigrationIfAvailable(Context context, Runnable onComplete) {
        // First migrate in-app legacy SharedPreferences (miogram_* -> amegram_*)
        migrateLocalPreferences(context);

        if (!isMigrationNeeded(context)) {
            if (onComplete != null) onComplete.run();
            return;
        }

        new Thread(() -> {
            boolean success = false;
            String foundLegacyPkg = null;

            for (String pkg : LEGACY_PACKAGES) {
                if (pkg.equals(context.getPackageName())) continue;
                try {
                    context.getPackageManager().getPackageInfo(pkg, 0);
                } catch (Exception e) {
                    continue;
                }

                String uriStr = "content://" + pkg + ".migration/backup_archive";
                try {
                    Uri uri = Uri.parse(uriStr);
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
                        foundLegacyPkg = pkg;
                        break;
                    }
                } catch (Throwable t) {
                    FileLog.e(t);
                }
            }

            if (success) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit().putBoolean(KEY_MIGRATION_DONE, true).apply();

                migrateLocalPreferences(context);

                // Reload UserConfig accounts
                for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                    try {
                        UserConfig.getInstance(a).loadConfig();
                    } catch (Throwable ignore) {}
                }
            }

            final boolean migrated = success;
            final String legacyToPrompt = foundLegacyPkg;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (onComplete != null) onComplete.run();
                if (migrated) {
                    showMigrationSuccessNotification(context);
                    if (context instanceof Activity && legacyToPrompt != null) {
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            promptUninstallLegacy((Activity) context, legacyToPrompt);
                        }, 1500);
                    }
                }
            });
        }, "AmegramMigrationConsumerThread").start();
    }

    private static void migrateLocalPreferences(Context context) {
        if (context == null) return;
        try {
            File prefsDir = new File(context.getApplicationInfo().dataDir, "shared_prefs");
            if (!prefsDir.exists() || !prefsDir.isDirectory()) return;
            File[] files = prefsDir.listFiles();
            if (files == null) return;
            for (File file : files) {
                String name = file.getName();
                if (name.startsWith("miogram_") && name.endsWith(".xml")) {
                    String base = name.substring("miogram_".length(), name.length() - ".xml".length());
                    String amegramName = "amegram_" + base + ".xml";
                    File target = new File(prefsDir, amegramName);
                    if (!target.exists()) {
                        AndroidUtilities.copyFile(file, target);
                    }
                }
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    public static void showMigrationSuccessNotification(Context context) {
        if (context == null) return;
        Toast.makeText(context, MiogramLocale.get(
                "Усі дані та акаунти успішно перенесено в Amegram!",
                "Все данные и аккаунты успешно перенесены в Amegram!",
                "All data and accounts successfully migrated to Amegram!"),
                Toast.LENGTH_LONG).show();
    }

    public static void promptUninstallLegacy(Activity activity, String legacyPkg) {
        if (activity == null || activity.isFinishing()) return;
        final String targetPkg = legacyPkg != null ? legacyPkg : "app.miogram";
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(MiogramLocale.get("Видалити старий додаток?", "Удалить старое приложение?", "Uninstall old app?"));
        builder.setMessage(MiogramLocale.get(
                "Дані перенесені в Amegram. Ви можете видалити старий додаток для звільнення памʼяті.",
                "Данные перенесены в Amegram. Вы можете удалить старое приложение для освобождения памяти.",
                "Data has been migrated to Amegram. You can uninstall the old app to free up storage."));
        builder.setPositiveButton(MiogramLocale.get("Видалити", "Удалить", "Uninstall"), (d, w) -> {
            d.dismiss();
            try {
                Intent intent = new Intent(Intent.ACTION_DELETE);
                intent.setData(Uri.parse("package:" + targetPkg));
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
