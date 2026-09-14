package app.miogram.bridge.push;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.PushListenerController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;

import app.miogram.bridge.MiogramLocale;
import app.miogram.bridge.customui.MiogramHaptic;
import xyz.nextalone.nagram.NaConfig;

/**
 * Push Notifications Diagnostics & Repair for Miogram.
 *
 * Shows the full client-side push chain state (service type, Play Services,
 * FCM token, server registration, last received push, runtime permission,
 * battery optimization, keep-alive) and offers one-tap repairs.
 *
 * Honest scope: this fixes everything fixable on-device. If the token is OK
 * but pushes still never arrive while the app is closed, the break is
 * server-side (FCM key of api_id) or an OEM task killer — the guaranteed
 * fallback is the keep-alive connection below.
 */
public class MiogramPushSheet extends BottomSheet {

    private final Theme.ResourcesProvider resourcesProvider;
    private LinearLayout statusContainer;
    private LinearLayout actionsContainer;

    public MiogramPushSheet(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, false, resourcesProvider);
        this.resourcesProvider = resourcesProvider;

        setApplyBottomPadding(false);
        setApplyTopPadding(false);

        int bgColor = getThemedColor(Theme.key_dialogBackground);
        if (bgColor == 0) bgColor = 0xFF101822;
        fixNavigationBar(bgColor);

        int textColor = getThemedColor(Theme.key_dialogTextBlack);
        if (textColor == 0) textColor = 0xFFFFFFFF;
        int subTextColor = getThemedColor(Theme.key_dialogTextGray2);
        if (subTextColor == 0) subTextColor = 0xAAFFFFFF;

        ScrollView scrollView = new ScrollView(context);
        scrollView.setVerticalScrollBarEnabled(false);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bgColor);
        root.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(12), AndroidUtilities.dp(20), AndroidUtilities.dp(28));

        android.view.View dragHandle = new android.view.View(context);
        GradientDrawable handleBg = new GradientDrawable();
        handleBg.setColor(0x44888888);
        handleBg.setCornerRadius(AndroidUtilities.dp(2.5f));
        dragHandle.setBackground(handleBg);
        root.addView(dragHandle, LayoutHelper.createLinear(38, 5, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 16));

        TextView titleView = new TextView(context);
        titleView.setText(MiogramLocale.get("Сповіщення та фон", "Уведомления и фон", "Notifications & Background"));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(textColor);
        titleView.setGravity(Gravity.CENTER);
        root.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 4));

        TextView subtitleView = new TextView(context);
        subtitleView.setText(MiogramLocale.get(
                "Діагностика push-ланцюжка та швидке лагодження",
                "Диагностика push-цепочки и быстрое исправление",
                "Push chain diagnostics & quick repair"
        ));
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitleView.setTextColor(subTextColor);
        subtitleView.setGravity(Gravity.CENTER);
        root.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 18));

        statusContainer = new LinearLayout(context);
        statusContainer.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(0x14FFFFFF);
        cardBg.setCornerRadius(AndroidUtilities.dp(14));
        cardBg.setStroke(AndroidUtilities.dp(1), 0x22FFFFFF);
        statusContainer.setBackground(cardBg);
        statusContainer.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12), AndroidUtilities.dp(14), AndroidUtilities.dp(12));
        root.addView(statusContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));

        actionsContainer = new LinearLayout(context);
        actionsContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(actionsContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        scrollView.addView(root);
        setCustomView(scrollView);

        refreshAll();
    }

    private void refreshAll() {
        Context context = getContext();
        statusContainer.removeAllViews();
        actionsContainer.removeAllViews();

        PushState st = collectState();

        addStatusLine(context, MiogramLocale.get("Push-сервіс", "Push-сервис", "Push service"), st.serviceName, st.serviceOk);
        addStatusLine(context, "Google Play Services", st.playServices ? "OK" : MiogramLocale.get("немає", "нет", "missing"), st.playServices);
        addStatusLine(context, "FCM token", st.tokenState, st.tokenOk);
        addStatusLine(context, MiogramLocale.get("Реєстрація на сервері", "Регистрация на сервере", "Server registration"), st.regState, st.regOk);
        addStatusLine(context, MiogramLocale.get("Останній push", "Последний push", "Last push"), st.lastPushText, st.lastPushOk);
        addStatusLine(context, MiogramLocale.get("Дозвіл на сповіщення", "Разрешение на уведомления", "Notification permission"), st.permissionText, st.permissionOk);
        addStatusLine(context, MiogramLocale.get("Батарея (без оптимізації)", "Батарея (без оптимизации)", "Battery (unrestricted)"), st.batteryText, st.batteryOk);
        addStatusLine(context, "Keep-alive", st.keepAlive ? MiogramLocale.get("увімкнено", "включено", "enabled") : MiogramLocale.get("вимкнено", "выключено", "disabled"), true);

        addAction(context, MiogramLocale.get("Перереєструвати push", "Перерегистрировать push", "Re-register push"), 0x3366C0F4, 0xFF66C0F4, v -> {
            try {
                PushListenerController.getProvider().onRequestPushToken();
                PushListenerController.refreshRegistration();
                Toast.makeText(context, MiogramLocale.get("Запит токена надіслано, зачекайте 10 сек", "Запрос токена отправлен, подождите 10 сек", "Token requested, wait 10 sec"), Toast.LENGTH_SHORT).show();
            } catch (Throwable t) {
                Toast.makeText(context, t.getMessage(), Toast.LENGTH_SHORT).show();
            }
            AndroidUtilities.runOnUIThread(this::refreshAll, 3000);
        });

        addAction(context, MiogramLocale.get("Тестове сповіщення в шторку", "Тестовое уведомление в шторку", "Send test notification"), 0x3323836E, 0xFFFFFFFF, v -> {
            postTestNotification(context);
        });

        if (!st.permissionOk) {
            addAction(context, MiogramLocale.get("Дозволити сповіщення", "Разрешить уведомления", "Allow notifications"), 0x3323836E, 0xFFFFFFFF, v -> {
                try {
                    if (getContext() instanceof android.app.Activity) {
                        ((android.app.Activity) getContext()).requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 991);
                    } else {
                        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(intent);
                    }
                } catch (Throwable ignore) {}
            });
        }

        if (!st.batteryOk) {
            addAction(context, MiogramLocale.get("Вимкнути оптимізацію батареї", "Отключить оптимизацию батареи", "Disable battery optimization"), 0x18FFFFFF, 0xFFD2DBE3, v -> {
                try {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:" + context.getPackageName()));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                } catch (Throwable t) {
                    try {
                        Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(intent);
                    } catch (Throwable ignore) {}
                }
                AndroidUtilities.runOnUIThread(this::refreshAll, 2000);
            });
        }

        boolean keepAlive = st.keepAlive;
        addAction(context, keepAlive
                ? MiogramLocale.get("Вимкнути keep-alive (економія батареї)", "Отключить keep-alive (экономия батареи)", "Disable keep-alive (save battery)")
                : MiogramLocale.get("Увімкнути keep-alive (сповіщення без FCM)", "Включить keep-alive (уведомления без FCM)", "Enable keep-alive (notify without FCM)"),
                keepAlive ? 0x22FF4B4B : 0x3323836E,
                keepAlive ? 0xFFFF6B6B : 0xFFFFFFFF, v -> {
            try {
                SharedPreferences prefs = MessagesController.getNotificationsSettings(UserConfig.selectedAccount);
                prefs.edit().putBoolean("pushService", !keepAlive).commit();
                ApplicationLoader.startPushService();
                Toast.makeText(context, !keepAlive
                        ? MiogramLocale.get("Keep-alive увімкнено: тримає з'єднання у фоні", "Keep-alive включён: держит соединение в фоне", "Keep-alive on: holds connection in background")
                        : MiogramLocale.get("Keep-alive вимкнено", "Keep-alive отключён", "Keep-alive off"), Toast.LENGTH_SHORT).show();
            } catch (Throwable ignore) {}
            refreshAll();
        });

        TextView hint = new TextView(context);
        hint.setText(MiogramLocale.get(
                "Якщо токен OK, а пуші при закритому додатку все одно не йдуть — рветься серверна ланка (FCM-ключ api_id) або вбивця фону в прошивці. Keep-alive обходить обидві проблеми ціною батареї.",
                "Если токен OK, а пуши при закрытом приложении всё равно не идут — рвётся серверное звено (FCM-ключ api_id) или убийца фона в прошивке. Keep-alive обходит обе проблемы ценой батареи.",
                "If the token is OK but pushes still never arrive while closed, the server link (api_id FCM key) or a firmware task killer is at fault. Keep-alive bypasses both at a battery cost."
        ));
        hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        hint.setTextColor(0x88FFFFFF);
        actionsContainer.addView(hint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0));
    }

    private static class PushState {
        String serviceName; boolean serviceOk;
        boolean playServices;
        String tokenState; boolean tokenOk;
        String regState; boolean regOk;
        String lastPushText; boolean lastPushOk;
        String permissionText; boolean permissionOk;
        String batteryText; boolean batteryOk;
        boolean keepAlive;
    }

    private PushState collectState() {
        PushState st = new PushState();
        Context context = getContext();

        int serviceType = 1;
        try {
            serviceType = NaConfig.INSTANCE.getPushServiceType().Int();
        } catch (Throwable ignore) {}
        if (serviceType == 1 || serviceType == 3) {
            st.serviceName = "Google FCM";
            st.serviceOk = true;
        } else if (serviceType == 2) {
            st.serviceName = "UnifiedPush";
            st.serviceOk = true;
        } else {
            st.serviceName = MiogramLocale.get("Вимкнено", "Отключено", "Disabled");
            st.serviceOk = false;
        }

        boolean hasServices = false;
        try {
            hasServices = PushListenerController.getProvider().hasServices();
        } catch (Throwable ignore) {}
        st.playServices = hasServices;

        String status = SharedConfig.pushStringStatus;
        String token = SharedConfig.pushString;
        if (!TextUtils.isEmpty(token)) {
            st.tokenState = "OK • " + token.substring(0, Math.min(10, token.length())) + "…";
            st.tokenOk = true;
        } else if (!TextUtils.isEmpty(status)) {
            st.tokenState = status;
            st.tokenOk = false;
        } else {
            st.tokenState = MiogramLocale.get("немає", "нет", "missing");
            st.tokenOk = false;
        }

        int pushType = 0;
        try {
            pushType = SharedConfig.pushType;
        } catch (Throwable ignore) {}
        if (pushType == PushListenerController.PUSH_TYPE_FIREBASE) {
            st.regState = "FCM";
            st.regOk = st.tokenOk;
        } else if (pushType != 0) {
            st.regState = "type=" + pushType;
            st.regOk = st.tokenOk;
        } else {
            st.regState = MiogramLocale.get("немає", "нет", "missing");
            st.regOk = false;
        }

        long lastPush = 0;
        try {
            lastPush = SharedConfig.pushLastReceivedTime;
        } catch (Throwable ignore) {}
        if (lastPush <= 0) {
            st.lastPushText = MiogramLocale.get("ніколи", "никогда", "never");
            st.lastPushOk = false;
        } else {
            long mins = (System.currentTimeMillis() - lastPush) / 60000;
            st.lastPushText = mins < 1
                    ? MiogramLocale.get("щойно", "только что", "just now")
                    : (mins < 60 ? mins + MiogramLocale.get(" хв тому", " мин назад", " min ago")
                    : (mins / 60) + MiogramLocale.get(" год тому", " ч назад", " h ago"));
            st.lastPushOk = true;
        }

        boolean permOk = true;
        if (Build.VERSION.SDK_INT >= 33 && context != null) {
            permOk = context.checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED;
        }
        st.permissionOk = permOk;
        st.permissionText = permOk
                ? MiogramLocale.get("надано", "разрешено", "granted")
                : MiogramLocale.get("НЕМАЄ — увімкніть!", "НЕТ — включите!", "MISSING — enable!");

        boolean battOk = true;
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            battOk = pm == null || pm.isIgnoringBatteryOptimizations(context.getPackageName());
        } catch (Throwable ignore) {}
        st.batteryOk = battOk;
        st.batteryText = battOk
                ? MiogramLocale.get("без обмежень", "без ограничений", "unrestricted")
                : MiogramLocale.get("обмежена!", "ограничена!", "restricted!");

        boolean ka = false;
        try {
            SharedPreferences prefs = MessagesController.getNotificationsSettings(UserConfig.selectedAccount);
            ka = prefs.getBoolean("pushService", MessagesController.getInstance(UserConfig.selectedAccount).keepAliveService);
        } catch (Throwable ignore) {}
        st.keepAlive = ka;

        return st;
    }

    /**
     * Posts a local test notification through the system tray (own channel).
     * Verifies the on-device half: runtime permission, channels, tray delivery.
     * If this shows but real messages don't while closed — the server/FCM link is at fault.
     */
    private void postTestNotification(Context context) {
        try {
            android.app.NotificationManager nm = (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            String channelId = "miogram_push_test";
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                android.app.NotificationChannel ch = new android.app.NotificationChannel(
                        channelId,
                        MiogramLocale.get("Тест сповіщень Miogram", "Тест уведомлений Miogram", "Miogram notification test"),
                        android.app.NotificationManager.IMPORTANCE_HIGH);
                nm.createNotificationChannel(ch);
            }
            android.content.Intent openIntent = new android.content.Intent(context, org.telegram.ui.LaunchActivity.class);
            openIntent.setAction("miogram_push_test");
            android.app.PendingIntent pi = android.app.PendingIntent.getActivity(context, 991,
                    openIntent, android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
            androidx.core.app.NotificationCompat.Builder b = new androidx.core.app.NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(R.drawable.exteraless_notification)
                    .setContentTitle(MiogramLocale.get("Тест Miogram", "Тест Miogram", "Miogram test"))
                    .setContentText(MiogramLocale.get("Якщо бачиш це — шторка, дозвіл і канали працюють. Згорни додаток і попроси друга написати тобі.", "Если видишь это — шторка, разрешение и каналы работают. Сверни приложение и попроси друга написать тебе.", "If you see this — tray, permission and channels work. Minimize the app and ask a friend to message you."))
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH);
            nm.notify(991001, b.build());
            Toast.makeText(context, MiogramLocale.get("Тест надіслано в шторку", "Тест отправлен в шторку", "Test sent to tray"), Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            Toast.makeText(context, String.valueOf(t.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    /** Short one-line status for the settings row: OK or what is broken. */    public static String getShortStatus() {
        try {
            int serviceType = NaConfig.INSTANCE.getPushServiceType().Int();
            boolean hasServices = false;
            try {
                hasServices = PushListenerController.getProvider().hasServices();
            } catch (Throwable ignore) {}
            boolean tokenOk = !TextUtils.isEmpty(SharedConfig.pushString);
            boolean permOk = Build.VERSION.SDK_INT < 33 || ApplicationLoader.applicationContext == null
                    || ApplicationLoader.applicationContext.checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED;
            if ((serviceType == 1 || serviceType == 3) && hasServices && tokenOk && permOk) {
                return "FCM • OK";
            }
            if (!permOk) return MiogramLocale.get("Немає дозволу!", "Нет разрешения!", "No permission!");
            if (!tokenOk) return MiogramLocale.get("Немає токена", "Нет токена", "No token");
            if (!hasServices) return MiogramLocale.get("Немає Play Services", "Нет Play Services", "No Play Services");
            return MiogramLocale.get("Перевірити", "Проверить", "Check");
        } catch (Throwable ignore) {
            return "FCM";
        }
    }

    private void addStatusLine(Context context, String label, String value, boolean ok) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        statusContainer.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 3, 0, 3));

        android.view.View dot = new android.view.View(context);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(ok ? 0xFF238636 : 0xFFFF4B4B);
        dot.setBackground(dotBg);
        row.addView(dot, LayoutHelper.createLinear(8, 8, 0, 0, 8, 0));

        TextView labelView = new TextView(context);
        labelView.setText(label);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        labelView.setTextColor(0xAAFFFFFF);
        row.addView(labelView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL));

        TextView valueView = new TextView(context);
        valueView.setText(value);
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        valueView.setTypeface(AndroidUtilities.bold());
        valueView.setTextColor(ok ? 0xFFFFFFFF : 0xFFFF8A80);
        valueView.setGravity(Gravity.END);
        row.addView(valueView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
    }

    private void addAction(Context context, String text, int bgColor, int textColor, android.view.View.OnClickListener onClick) {
        TextView btn = new TextView(context);
        btn.setText(text);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13.5f);
        btn.setTypeface(AndroidUtilities.bold());
        btn.setTextColor(textColor);
        btn.setGravity(Gravity.CENTER);
        btn.setSingleLine(false);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(AndroidUtilities.dp(10));
        btn.setBackground(bg);
        btn.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(11), AndroidUtilities.dp(12), AndroidUtilities.dp(11));
        ScaleStateListAnimator.apply(btn, 0.035f, 1.4f);
        btn.setOnClickListener(v -> {
            MiogramHaptic.click(v);
            onClick.onClick(v);
        });
        actionsContainer.addView(btn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));
    }
}
