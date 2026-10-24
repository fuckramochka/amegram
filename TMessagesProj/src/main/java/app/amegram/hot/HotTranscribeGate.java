package app.amegram.hot;

import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.LaunchActivity;

import java.io.File;

import app.amegram.hot.api.HotServices;
import app.amegram.hot.api.HotTranscribe;
import app.miogram.bridge.MiogramLocale;
import app.miogram.bridge.ai.AmegramLocalTranscriber;

/**
 * Гейт розшифровки:
 * 1. Якщо stt модуль зареєстрував HotTranscribe — використовуємо його.
 * 2. Інакше — викликаємо вбудований AmegramLocalTranscriber (нативний STT / Gemini AI).
 * Результат встановлюємо у voiceTranscription повідомлення та показуємо діалог.
 */
public final class HotTranscribeGate {

    private HotTranscribeGate() {
    }

    /** true = перехопили (сток далі не йде). */
    public static boolean tryTranscribe(MessageObject messageObject) {
        if (messageObject == null || messageObject.messageOwner == null) {
            return false;
        }

        File audio = null;
        try {
            audio = FileLoader.getInstance(messageObject.currentAccount)
                    .getPathToMessage(messageObject.messageOwner);
        } catch (Throwable ignore) {
        }
        if (audio == null || !audio.exists()) {
            try {
                FileLoader.getInstance(messageObject.currentAccount)
                        .loadFile(messageObject.getDocument(), messageObject, FileLoader.PRIORITY_HIGH, 0);
                if (LaunchActivity.instance != null) {
                    BulletinFactory.of(LaunchActivity.instance).createSimpleBulletin(
                            org.telegram.messenger.R.raw.info,
                            MiogramLocale.get("Завантаження аудіо... Натисніть ще раз через секунду",
                                    "Загрузка аудио... Нажмите ещё раз через секунду",
                                    "Downloading audio... Tap again in a second")
                    ).show();
                }
            } catch (Throwable ignore) {
            }
            return true;
        }

        boolean isVideo = messageObject.isVideo();
        String path = audio.getAbsolutePath();

        HotTranscribe svc = null;
        try {
            svc = HotModulesManager.getService(HotServices.TRANSCRIBE);
        } catch (Throwable ignore) {
        }

        if (svc != null) {
            final HotTranscribe activeSvc = svc;
            new Thread(() -> activeSvc.transcribe(path, isVideo, new HotTranscribe.Callback() {
                @Override
                public void onResult(String text) {
                    AndroidUtilities.runOnUIThread(() -> applyAndShowResult(messageObject, text));
                }

                @Override
                public void onError(String error) {
                    AndroidUtilities.runOnUIThread(() -> applyAndShowResult(messageObject, error));
                }
            }), "hotmods-stt").start();
            return true;
        }

        // Native fallback via AmegramLocalTranscriber
        AmegramLocalTranscriber.transcribe(path, isVideo, (text, err) -> {
            AndroidUtilities.runOnUIThread(() -> {
                if (!TextUtils.isEmpty(text)) {
                    applyAndShowResult(messageObject, text);
                } else {
                    String msg = err != null ? err.getMessage() : "Помилка розпізнавання";
                    applyAndShowResult(messageObject, msg);
                }
            });
        });
        return true;
    }

    private static void applyAndShowResult(MessageObject messageObject, String text) {
        if (messageObject != null && messageObject.messageOwner != null && !TextUtils.isEmpty(text)) {
            messageObject.messageOwner.voiceTranscription = text;
            messageObject.messageOwner.voiceTranscriptionOpen = true;
            messageObject.messageOwner.voiceTranscriptionFinal = true;
            try {
                NotificationCenter.getInstance(messageObject.currentAccount).postNotificationName(
                        NotificationCenter.voiceTranscriptionUpdate,
                        messageObject,
                        0L,
                        text,
                        true,
                        true
                );
            } catch (Throwable ignore) {
            }
        }
        showResult(text);
    }

    private static void showResult(String text) {
        try {
            if (LaunchActivity.instance == null) return;
            AlertDialog.Builder builder = new AlertDialog.Builder(LaunchActivity.instance);
            builder.setTitle(MiogramLocale.get("Розшифровка", "Расшифровка", "Transcription"));
            builder.setMessage(text != null ? text : "—");
            builder.setPositiveButton("OK", null);
            builder.setNegativeButton(MiogramLocale.get("Копіювати", "Копировать", "Copy"), (d, w) -> {
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
