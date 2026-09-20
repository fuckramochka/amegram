package app.miogram.bridge.migration;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.FileLog;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Signature-protected ContentProvider to allow seamless data migration
 * from legacy package (com.exteraless.app) to new Amegram package (app.amegram).
 * 
 * Cryptographically locked by Android OS using protectionLevel="signature".
 * Only apps signed with the EXACT same release.keystore can read this stream.
 */
public class AmegramMigrationProvider extends ContentProvider {

    public static final String AUTHORITY_LEGACY = "com.exteraless.app.migration";
    public static final String PATH_BACKUP_ARCHIVE = "backup_archive";

    @Override
    public boolean onCreate() {
        return true;
    }

    private void verifyCallerSignature() {
        Context context = getContext();
        if (context == null) {
            throw new SecurityException("No context available");
        }
        int callingUid = Binder.getCallingUid();
        int myUid = android.os.Process.myUid();
        if (callingUid == myUid) {
            return; // Self access
        }
        PackageManager pm = context.getPackageManager();
        if (pm.checkSignatures(callingUid, myUid) != PackageManager.SIGNATURE_MATCH) {
            throw new SecurityException("Unauthorized: Calling package signature does not match!");
        }
    }

    @Nullable
    @Override
    public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode) {
        verifyCallerSignature();
        Context context = getContext();
        if (context == null) return null;

        String path = uri.getPath();
        if (path == null || !path.contains(PATH_BACKUP_ARCHIVE)) {
            return null;
        }

        try {
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            ParcelFileDescriptor readSide = pipe[0];
            ParcelFileDescriptor writeSide = pipe[1];

            new Thread(() -> {
                try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(writeSide.getFileDescriptor()))) {
                    // 1. Files dir (cache4.db, accounts, tgnet keys)
                    File filesDir = context.getFilesDir();
                    if (filesDir != null && filesDir.exists()) {
                        zipDirectory(filesDir, "files/", zos);
                    }

                    // 2. Databases (ayu-data, etc.)
                    File dbDir = context.getDatabasePath("dummy").getParentFile();
                    if (dbDir != null && dbDir.exists()) {
                        zipDirectory(dbDir, "databases/", zos);
                    }

                    // 3. SharedPreferences
                    File prefsDir = new File(context.getApplicationInfo().dataDir, "shared_prefs");
                    if (prefsDir.exists()) {
                        zipDirectory(prefsDir, "shared_prefs/", zos);
                    }

                    zos.finish();
                    zos.flush();
                } catch (Throwable t) {
                    FileLog.e(t);
                } finally {
                    try {
                        writeSide.close();
                    } catch (IOException ignore) {}
                }
            }, "MigrationArchiveThread").start();

            return readSide;
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    private void zipDirectory(File folder, String parentPath, ZipOutputStream zos) {
        File[] files = folder.listFiles();
        if (files == null) return;

        byte[] buffer = new byte[8192];
        for (File file : files) {
            String name = file.getName();
            // Skip volatile/large caches to keep migration instant and lightweight
            if (name.equals("cache") || name.equals("temp") || name.equals("media") || name.endsWith(".tmp")) {
                continue;
            }

            if (file.isDirectory()) {
                zipDirectory(file, parentPath + name + "/", zos);
            } else {
                try (FileInputStream fis = new FileInputStream(file)) {
                    ZipEntry entry = new ZipEntry(parentPath + name);
                    zos.putNextEntry(entry);
                    int count;
                    while ((count = fis.read(buffer)) != -1) {
                        zos.write(buffer, 0, count);
                    }
                    zos.closeEntry();
                } catch (Throwable ignore) {}
            }
        }
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        verifyCallerSignature();
        return null;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return "application/zip";
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }
}
