package app.amegram.hot;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.LaunchActivity;

import java.io.File;

import app.amegram.hot.api.HotServices;
import app.amegram.hot.api.HotTranscribe;

/**
 * Гейт розшифровки: якщо модуль stt увімкнено — ведемо через нього,
 * результат показуємо діалогом з копіюванням. Інакше — сток (premium).
 */
public final class HotTranscribeGate {

    private HotTranscribeGate() {
    }

    /** true = перехопили (сток далі не йде). */
    public static boolean tryTranscribe(MessageObject messageObject) {
        HotTranscribe svc;
        try {
            svc = HotModulesManager.getService(HotServices.TRANSCRIBE);
        } catch (Throwable ignore) {
            return false;
        }
        if (svc == null || messageObject == null || messageObject.messageOwner == null) {
            return false;
        }
        File audio;
        try {
            audio = FileLoader.getInstance(messageObject.currentAccount)
                    .getPathToMessage(messageObject.messageOwner);
        } catch (Throwable ignore) {
            return false;
        }
        if (audio == null || !audio.exists()) {
            return false;
        }
        boolean isVideo = messageObject.isVideo();
        String path = audio.getAbsolutePath();
        new Thread(() -> svc.transcribe(path, isVideo, new HotTranscribe.Callback() {
            @Override
            public void onResult(String text) {
                AndroidUtilities.runOnUIThread(() -> showResult(text));
            }

            @Override
            public void onError(String error) {
                AndroidUtilities.runOnUIThread(() -> showResult("⚠️ " + error));
            }
        }), "hotmods-stt").start();
        return true;
    }

    private static void showResult(String text) {
        try {
            if (LaunchActivity.instance == null) return;
            AlertDialog.Builder builder = new AlertDialog.Builder(LaunchActivity.instance);
            builder.setTitle("Розшифровка");
            builder.setMessage(text != null ? text : "—");
            builder.setPositiveButton("OK", null);
            builder.setNegativeButton("Копіювати", (d, w) -> {
                try {
                    android.content.ClipboardManager cm = (android.content.ClipboardManager)
                            ApplicationLoader.applicationContext.getSystemService(
                                    android.content.Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("stt", text));
                } catch (Throwable ignore) {
                }
            });
            builder.show();
        } catch (Throwable ignore) {
        }
    }
}
