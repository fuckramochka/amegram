package app.miogram.bridge.localizer;

import android.net.Uri;
import android.os.Environment;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import app.miogram.bridge.MiogramLocale;
import app.miogram.bridge.cloudvault.MiogramCloudVaultEngine;

/**
 * High-performance Chat Archiver, Deleted Messages Preserver, and Data Localizer Engine (.amagram).
 * Packages chat histories, media deduplication, deleted message states, and cloud vault backups.
 */
public class MiogramDataLocalizerEngine {

    public interface BackupCallback {
        void onProgress(int current, int total, String status);
        void onSuccess(File archiveFile, int totalMessages, int deletedCount, int mediaCount);
        void onError(String error);
    }

    public static class ManifestInfo {
        public String version;
        public long dialogId;
        public String dialogTitle;
        public long exportedAt;
        public int totalMessages;
        public int deletedMessages;
        public int mediaFiles;
        public long archiveSize;
    }

    /**
     * Exports full chat history (including deleted and preserved messages) to a .amagram archive.
     */
    public static void exportChatToAmagram(int currentAccount, long dialogId, boolean includeMedia, boolean uploadToVault, BackupCallback callback) {
        MessagesStorage storage = MessagesStorage.getInstance(currentAccount);
        storage.getStorageQueue().postRunnable(() -> {
            SQLiteDatabase db = storage.getDatabase();
            if (db == null) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (callback != null) callback.onError("Storage database is not accessible");
                });
                return;
            }

            File tempDir = null;
            ZipOutputStream zos = null;
            try {
                // Determine dialog title
                String chatTitle = "Chat_" + dialogId;
                if (dialogId > 0) {
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
                    if (user != null) {
                        chatTitle = (user.first_name != null ? user.first_name : "") + (user.last_name != null ? " " + user.last_name : "");
                    }
                } else {
                    TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
                    if (chat != null && chat.title != null) {
                        chatTitle = chat.title;
                    }
                }
                chatTitle = chatTitle.trim();
                if (chatTitle.isEmpty()) chatTitle = "Dialog_" + dialogId;

                String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                String safeTitle = chatTitle.replaceAll("[\\\\/:*?\"<>|]", "_");
                String archiveFileName = safeTitle + "_" + timeStamp + ".amagram";

                File baseDir = getBackupDirectory();
                File amagramArchiveFile = new File(baseDir, archiveFileName);

                zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(amagramArchiveFile)));

                SQLiteCursor cursor = db.queryFinalized(String.format(Locale.US,
                        "SELECT mid, date, data FROM messages_v2 WHERE uid = %d ORDER BY mid ASC", dialogId));

                JSONArray messagesArray = new JSONArray();
                int totalCount = 0;
                int deletedCount = 0;
                int mediaCount = 0;

                // Cache for deduplicating media files by SHA-256
                Map<String, String> mediaHashMap = new HashMap<>();

                while (cursor.next()) {
                    totalCount++;
                    int mid = cursor.intValue(0);
                    int date = cursor.intValue(1);
                    NativeByteBuffer byteBuffer = cursor.byteBufferValue(2);

                    if (byteBuffer != null) {
                        try {
                            TLRPC.Message message = TLRPC.Message.TLdeserialize(byteBuffer, byteBuffer.readInt32(false), false);
                            if (message != null) {
                                JSONObject msgObj = new JSONObject();
                                msgObj.put("id", message.id);
                                msgObj.put("date", message.date);
                                msgObj.put("editDate", message.edit_date);
                                msgObj.put("out", message.out);
                                msgObj.put("fromId", message.from_id != null ? message.from_id.user_id : 0);
                                msgObj.put("message", message.message != null ? message.message : "");

                                boolean isDeleted = message.ayuDeleted;
                                if (isDeleted) {
                                    deletedCount++;
                                }
                                msgObj.put("isDeleted", isDeleted);

                                if (message.reply_to != null) {
                                    msgObj.put("replyToMsgId", message.reply_to.reply_to_msg_id);
                                }

                                // Media extraction & deduplication
                                if (includeMedia && message.media != null && !(message.media instanceof TLRPC.TL_messageMediaEmpty)) {
                                    File mediaFile = FileLoader.getInstance(currentAccount).getPathToMessage(message);
                                    if (mediaFile != null && mediaFile.exists() && mediaFile.length() > 0) {
                                        String hash = mediaHashMap.get(mediaFile.getAbsolutePath());
                                        if (hash == null) {
                                            hash = calculateSHA256(mediaFile);
                                            mediaHashMap.put(mediaFile.getAbsolutePath(), hash);

                                            String ext = getFileExtension(mediaFile.getName());
                                            String entryName = "media/" + hash + (ext.isEmpty() ? "" : "." + ext);

                                            ZipEntry mediaEntry = new ZipEntry(entryName);
                                            zos.putNextEntry(mediaEntry);
                                            writeFileToZip(mediaFile, zos);
                                            zos.closeEntry();
                                            mediaCount++;
                                        }

                                        msgObj.put("mediaHash", hash);
                                        msgObj.put("mediaName", mediaFile.getName());
                                        msgObj.put("mediaSize", mediaFile.length());
                                    }
                                }

                                messagesArray.put(msgObj);
                            }
                        } catch (Throwable t) {
                            FileLog.e("DataLocalizer: error deserializing message " + mid, t);
                        } finally {
                            byteBuffer.reuse();
                        }
                    }

                    if (totalCount % 50 == 0) {
                        final int cur = totalCount;
                        AndroidUtilities.runOnUIThread(() -> {
                            if (callback != null) callback.onProgress(cur, -1, MiogramLocale.get("Архівування повідомлень...", "Архивация сообщений...", "Archiving messages..."));
                        });
                    }
                }
                cursor.dispose();

                // Write chat_history.json
                ZipEntry historyEntry = new ZipEntry("chat_history.json");
                zos.putNextEntry(historyEntry);
                byte[] historyBytes = messagesArray.toString(2).getBytes(StandardCharsets.UTF_8);
                zos.write(historyBytes);
                zos.closeEntry();

                // Write manifest.json
                JSONObject manifest = new JSONObject();
                manifest.put("version", "1.0");
                manifest.put("app", "Amegram Data Localizer");
                manifest.put("dialogId", dialogId);
                manifest.put("dialogTitle", chatTitle);
                manifest.put("exportedAt", System.currentTimeMillis());
                manifest.put("totalMessages", totalCount);
                manifest.put("deletedMessages", deletedCount);
                manifest.put("mediaFiles", mediaCount);
                manifest.put("mediaDeduplicated", mediaHashMap.size());

                ZipEntry manifestEntry = new ZipEntry("manifest.json");
                zos.putNextEntry(manifestEntry);
                byte[] manifestBytes = manifest.toString(2).getBytes(StandardCharsets.UTF_8);
                zos.write(manifestBytes);
                zos.closeEntry();

                zos.finish();
                zos.close();
                zos = null;

                final int finalTotal = totalCount;
                final int finalDeleted = deletedCount;
                final int finalMedia = mediaCount;
                final File finalArchive = amagramArchiveFile;

                if (uploadToVault && MiogramCloudVaultEngine.hasVault(currentAccount)) {
                    long vaultChatId = MiogramCloudVaultEngine.getVaultChatId(currentAccount);
                    AndroidUtilities.runOnUIThread(() -> {
                        if (callback != null) callback.onProgress(finalTotal, finalTotal, MiogramLocale.get("Завантаження в Cloud Vault...", "Загрузка в Cloud Vault...", "Uploading to Cloud Vault..."));
                    });

                    // Ensure hidden system topic "__localizer_backups" exists
                    MiogramCloudVaultEngine.getOrCreateSystemTopic(currentAccount, vaultChatId, "__localizer_backups", topicId -> {
                        MiogramCloudVaultEngine.uploadFileToVault(
                                currentAccount,
                                finalArchive,
                                finalArchive.getName(),
                                "application/zip",
                                null,
                                null,
                                null
                        );
                        AndroidUtilities.runOnUIThread(() -> {
                            if (callback != null) callback.onSuccess(finalArchive, finalTotal, finalDeleted, finalMedia);
                        });
                    });
                } else {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (callback != null) callback.onSuccess(finalArchive, finalTotal, finalDeleted, finalMedia);
                    });
                }

            } catch (Throwable e) {
                FileLog.e("DataLocalizer export error", e);
                AndroidUtilities.runOnUIThread(() -> {
                    if (callback != null) callback.onError(e.getMessage());
                });
            } finally {
                if (zos != null) {
                    try { zos.close(); } catch (Throwable ignored) {}
                }
            }
        });
    }

    /**
     * Reads manifest metadata from an existing .amagram archive file.
     */
    public static ManifestInfo readManifest(File amagramFile) {
        if (amagramFile == null || !amagramFile.exists()) return null;
        try (ZipFile zip = new ZipFile(amagramFile)) {
            ZipEntry entry = zip.getEntry("manifest.json");
            if (entry == null) return null;
            BufferedReader br = new BufferedReader(new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            JSONObject json = new JSONObject(sb.toString());

            ManifestInfo info = new ManifestInfo();
            info.version = json.optString("version", "1.0");
            info.dialogId = json.optLong("dialogId");
            info.dialogTitle = json.optString("dialogTitle");
            info.exportedAt = json.optLong("exportedAt");
            info.totalMessages = json.optInt("totalMessages");
            info.deletedMessages = json.optInt("deletedMessages");
            info.mediaFiles = json.optInt("mediaFiles");
            info.archiveSize = amagramFile.length();
            return info;
        } catch (Throwable t) {
            FileLog.e(t);
            return null;
        }
    }

    public static File getBackupDirectory() {
        File dir = new File(ApplicationLoader.applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "AmegramBackups");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private static String calculateSHA256(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            byte[] hash = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Throwable t) {
            return String.valueOf(file.length()) + "_" + file.getName().hashCode();
        }
    }

    private static void writeFileToZip(File file, ZipOutputStream zos) throws Exception {
        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = bis.read(buffer)) != -1) {
                zos.write(buffer, 0, count);
            }
        }
    }

    private static String getFileExtension(String name) {
        if (name == null) return "";
        int idx = name.lastIndexOf('.');
        return (idx > 0 && idx < name.length() - 1) ? name.substring(idx + 1) : "";
    }
}
