package app.amegram.database;

import android.content.Context;
import android.widget.Toast;

import com.radolyn.ayugram.AyuConstants;
import com.radolyn.ayugram.database.AyuData;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;

import app.miogram.bridge.MiogramLocale;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Експорт та резервне копіювання бази видалених/відредагованих повідомлень Yumigram у Збережене (Saved Messages).
 */
public final class YumiBackupHelper {

    private YumiBackupHelper() {
    }

    public static void exportDatabaseToSavedMessages(int currentAccount, Context context, Runnable onFinished) {
        AlertDialog progressDialog = null;
        if (context != null) {
            try {
                progressDialog = new AlertDialog(context, AlertDialog.ALERT_TYPE_SPINNER);
                progressDialog.setCanCancel(false);
                progressDialog.setMessage(MiogramLocale.get("Підготовка резервної копії...", "Подготовка резервной копии...", "Preparing backup..."));
                progressDialog.show();
            } catch (Throwable ignore) {
            }
        }
        final AlertDialog finalProgress = progressDialog;

        Utilities.globalQueue.postRunnable(() -> {
            boolean success = false;
            String errorMessage = null;
            File exportFile = null;
            try {
                File dbFile = ApplicationLoader.applicationContext.getDatabasePath(AyuConstants.AYU_DATABASE);
                if (dbFile == null || !dbFile.exists()) {
                    throw new IOException(MiogramLocale.get("База даних видалених повідомлень ще порожня", "База данных удалённых сообщений ещё пуста", "Deleted messages database is empty"));
                }

                // Скидаємо транзакції з WAL-журналу на диск перед копіюванням
                try {
                    AyuData.checkpointDatabase();
                } catch (Throwable e) {
                    FileLog.e("Ayu checkpoint error: ", e);
                }

                String dateStr = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(new Date());
                exportFile = new File(AndroidUtilities.getCacheDir(), "yumigram_messages_" + dateStr + ".db");
                if (!AndroidUtilities.copyFile(dbFile, exportFile)) {
                    if (!exportFile.delete()) exportFile.deleteOnExit();
                    throw new IOException(MiogramLocale.get("Не вдалося скопіювати базу даних", "Не удалось скопировать базу данных", "Failed to copy database"));
                }

                success = true;
            } catch (Throwable t) {
                FileLog.e("exportDatabaseToSavedMessages error: ", t);
                errorMessage = t.getMessage();
            }

            final boolean finalSuccess = success;
            final String finalErr = errorMessage;
            final File finalFile = exportFile;

            AndroidUtilities.runOnUIThread(() -> {
                if (finalProgress != null) {
                    try {
                        finalProgress.dismiss();
                    } catch (Throwable ignore) {
                    }
                }

                if (finalSuccess && finalFile != null && finalFile.exists()) {
                    try {
                        long targetUserId = UserConfig.getInstance(currentAccount).getClientUserId();
                        String timeStr = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());
                        String caption = MiogramLocale.get(
                                "Yumigram — Резервна копія бази повідомлень\nСтворено: " + timeStr + "\nРозмір: " + AndroidUtilities.formatFileSize(finalFile.length()) + "\nМістить збережені видалені та відредаговані повідомлення",
                                "Yumigram — Резервная копия базы сообщений\nСоздано: " + timeStr + "\nРазмер: " + AndroidUtilities.formatFileSize(finalFile.length()) + "\nСодержит сохранённые удалённые и отредактированные сообщения",
                                "Yumigram — Message database backup\nCreated: " + timeStr + "\nSize: " + AndroidUtilities.formatFileSize(finalFile.length()) + "\nContains saved deleted and edited messages"
                        );

                        SendMessagesHelper.prepareSendingDocument(
                                AccountInstance.getInstance(currentAccount),
                                finalFile.getAbsolutePath(),
                                finalFile.getAbsolutePath(),
                                null,
                                caption,
                                "application/octet-stream",
                                targetUserId,
                                null, null, null, null, null,
                                true, 0, null, null, false
                        );

                        Toast.makeText(ApplicationLoader.applicationContext,
                                MiogramLocale.get("Резервну копію успішно надіслано в Збережене (Saved Messages)!", "Резервная копия успешно отправлена в Избранное (Saved Messages)!", "Backup successfully sent to Saved Messages!"),
                                Toast.LENGTH_LONG).show();
                    } catch (Throwable e) {
                        FileLog.e("Failed to send export document: ", e);
                        Toast.makeText(ApplicationLoader.applicationContext,
                                MiogramLocale.get("Помилка відправки документа в Збережене", "Ошибка отправки документа в Избранное", "Failed to send document to Saved Messages"),
                                Toast.LENGTH_SHORT).show();
                    }
                } else {
                    String msg = finalErr != null ? finalErr : MiogramLocale.get("Помилка при експорті бази", "Ошибка при экспорте базы", "Error exporting database");
                    Toast.makeText(ApplicationLoader.applicationContext,
                            msg,
                            Toast.LENGTH_LONG).show();
                }

                if (onFinished != null) {
                    onFinished.run();
                }
            });
        });
    }
}
