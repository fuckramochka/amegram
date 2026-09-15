package app.miogram.bridge.mini;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.app.Person;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.OpenChatReceiver;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.BubbleActivity;

import app.miogram.bridge.MiogramLocale;

/**
 * Opens any chat as a floating Android bubble ("mini window").
 *
 * Reuses the stock bubble pipeline (dynamic shortcut + BubbleMetadata into
 * {@link BubbleActivity}) that notifications use — no custom windowing, no
 * extra permissions, zero cost when unused.
 */
public final class MiogramMiniChat {

    private MiogramMiniChat() {}

    public static boolean isSupported() {
        return Build.VERSION.SDK_INT >= 29;
    }

    public static boolean areBubblesAllowed() {
        try {
            if (!isSupported()) return false;
            if (tw.nekomimi.nekogram.NekoConfig.disableNotificationBubbles.Bool()) return false;
            NotificationManager nm = (NotificationManager) ApplicationLoader.applicationContext
                    .getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return false;
            if (Build.VERSION.SDK_INT >= 29 && !nm.areBubblesAllowed()) return false;
        } catch (Throwable ignore) {}
        return true;
    }

    @SuppressLint("RestrictedApi")
    public static void openInMiniWindow(Context context, int account, long dialogId) {
        Context ctx = context != null ? context : ApplicationLoader.applicationContext;
        if (ctx == null || dialogId == 0 || !UserConfig.isValidAccount(account)) return;
        if (!isSupported()) {
            Toast.makeText(ctx, MiogramLocale.get("Міні-вікна потребують Android 10+", "Мини-окна требуют Android 10+", "Mini windows need Android 10+"), Toast.LENGTH_SHORT).show();
            return;
        }
        if (!areBubblesAllowed()) {
            Toast.makeText(ctx, MiogramLocale.get("Дозволь бульбашки для Miogram у налаштуваннях системи", "Разреши всплывающие окна для Miogram в настройках системы", "Allow bubbles for Miogram in system settings"), Toast.LENGTH_LONG).show();
            return;
        }
        try {
            MessagesController mc = MessagesController.getInstance(account);
            TLRPC.User user = dialogId > 0 ? mc.getUser(dialogId) : null;
            TLRPC.Chat chat = dialogId < 0 ? mc.getChat(-dialogId) : null;
            if (user == null && chat == null) {
                Toast.makeText(ctx, MiogramLocale.get("Чат ще не завантажився", "Чат ещё не загрузился", "Chat is not loaded yet"), Toast.LENGTH_SHORT).show();
                return;
            }
            if (chat != null && ChatObject.isChannel(chat) && !chat.megagroup) {
                Toast.makeText(ctx, MiogramLocale.get("Канали не відкриваються в міні-вікні", "Каналы не открываются в мини-окне", "Broadcast channels can't open in a mini window"), Toast.LENGTH_SHORT).show();
                return;
            }
            String name = user != null ? UserObject.getUserName(user)
                    : (chat.title != null ? chat.title : MiogramLocale.get("Чат", "Чат", "Chat"));

            String shortcutId = "miogram_mini_" + account + "_" + dialogId;

            Intent shortcutIntent = new Intent(ApplicationLoader.applicationContext, OpenChatReceiver.class);
            shortcutIntent.setAction("com.tmessages.openchat" + dialogId + "_" + account);
            if (dialogId > 0) {
                shortcutIntent.putExtra("userId", dialogId);
            } else {
                shortcutIntent.putExtra("chatId", -dialogId);
            }

            Person person = new Person.Builder()
                    .setName(name)
                    .setKey(shortcutId)
                    .build();
            ShortcutInfoCompat shortcut = new ShortcutInfoCompat.Builder(ApplicationLoader.applicationContext, shortcutId)
                    .setShortLabel(name)
                    .setLongLabel(name)
                    .setIntent(shortcutIntent)
                    .setPerson(person)
                    .setLongLived(true)
                    .build();
            try {
                ShortcutManagerCompat.pushDynamicShortcut(ApplicationLoader.applicationContext, shortcut);
            } catch (Throwable ignore) {}

            Intent bubbleIntent = new Intent(ApplicationLoader.applicationContext, BubbleActivity.class);
            bubbleIntent.setAction("com.tmessages.openchat" + dialogId + "_" + account + "_mini");
            if (dialogId > 0) {
                bubbleIntent.putExtra("userId", dialogId);
            } else {
                bubbleIntent.putExtra("chatId", -dialogId);
            }
            bubbleIntent.putExtra("currentAccount", account);

            IconCompat icon;
            if (user != null) {
                icon = IconCompat.createWithResource(ApplicationLoader.applicationContext,
                        user.bot ? R.drawable.book_bot : R.drawable.book_user);
            } else {
                icon = IconCompat.createWithResource(ApplicationLoader.applicationContext, R.drawable.book_group);
            }
            PendingIntent bubblePi = PendingIntent.getActivity(ApplicationLoader.applicationContext, (int) (dialogId ^ (dialogId >>> 32)),
                    bubbleIntent, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            NotificationCompat.BubbleMetadata.Builder bubbleBuilder =
                    new NotificationCompat.BubbleMetadata.Builder(bubblePi, icon);
            bubbleBuilder.setSuppressNotification(true);
            bubbleBuilder.setAutoExpandBubble(true);
            bubbleBuilder.setDesiredHeight(AndroidUtilities.dp(640));

            String channelId = ensureMiniChannel(account);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(ApplicationLoader.applicationContext, channelId)
                    .setSmallIcon(R.drawable.notification)
                    .setContentTitle(name)
                    .setContentText(MiogramLocale.get("Міні-вікно чату", "Мини-окно чата", "Mini chat window"))
                    .setShortcutInfo(shortcut)
                    .setBubbleMetadata(bubbleBuilder.build())
                    .setContentIntent(bubblePi)
                    .setAutoCancel(false)
                    .setOngoing(false);

            NotificationManager nm = (NotificationManager) ApplicationLoader.applicationContext
                    .getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            nm.notify("miogram_mini", (int) (dialogId ^ (dialogId >>> 32)), builder.build());
        } catch (Throwable t) {
            FileLog.e(t);
            try {
                Toast.makeText(ctx, MiogramLocale.get("Не вийшло відкрити міні-вікно", "Не вышло открыть мини-окно", "Could not open the mini window"), Toast.LENGTH_SHORT).show();
            } catch (Throwable ignore) {}
        }
    }

    private static String ensureMiniChannel(int account) {
        String channelId = account + "_miogram_mini_windows";
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationManager nm = (NotificationManager) ApplicationLoader.applicationContext
                        .getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) {
                    NotificationChannel channel = new NotificationChannel(channelId,
                            MiogramLocale.get("Міні-вікна чатів", "Мини-окна чатов", "Mini chat windows"),
                            NotificationManager.IMPORTANCE_DEFAULT);
                    channel.setSound(null, null);
                    channel.enableVibration(false);
                    channel.setShowBadge(false);
                    nm.createNotificationChannel(channel);
                }
            }
        } catch (Throwable ignore) {}
        return channelId;
    }
}
