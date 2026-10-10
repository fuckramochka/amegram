package app.amegram.settings;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Cells.TextCheckCell;

import java.io.File;

import app.miogram.bridge.MiogramLocale;
import app.miogram.bridge.updater.MiogramDownloadManager;
import app.miogram.bridge.updater.MiogramUpdater;

/**
 * MD3 Updates Screen (Обновление) faithfully matching the Yumi concept from screenshot 15.
 * Features:
 * - Animated top banner with 26dp rounded corners, central scalloped Telegram badge, and floating shapes.
 * - Rounded status card with update icon and state description.
 * - "О клиенте" section with white circular icon containers for Client Version, API Version, and Build Number.
 * - Preferences card for update channel, auto-check, and Wi-Fi only settings.
 * - Full-width bottom pill button "Проверить" / "Установить".
 */
public class AmegramUpdateSettingsActivity extends BaseFragment {

    private YumiUpdateBannerView bannerView;
    private TextView statusTextView;
    private TextView statusActionBtn;
    private TextView checkButton;
    private TextView channelValueView;
    private TextCheckCell autoCheckCell;
    private TextCheckCell wifiOnlyCell;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(MiogramLocale.get("Оновлення", "Обновление", "Updates"));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        int bgColor = Theme.isCurrentThemeDark() ? 0xFF0E0E10 : 0xFFF5F5F7;
        int cardBgColor = Theme.isCurrentThemeDark() ? 0xFF1C1C1F : 0xFFFFFFFF;
        int textPrimary = Theme.isCurrentThemeDark() ? 0xFFFFFFFF : 0xFF1C1C1F;
        int textSecondary = 0xFF8E8E93;

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(bgColor);

        ScrollView scrollView = new ScrollView(context);
        scrollView.setVerticalScrollBarEnabled(false);
        root.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, AndroidUtilities.dp(88)); // Bottom padding for floating button
        scrollView.addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // 1. Animated Top Banner (26dp radius)
        bannerView = new YumiUpdateBannerView(context);
        LinearLayout.LayoutParams bannerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(196));
        bannerLp.setMargins(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), 0);
        content.addView(bannerView, bannerLp);

        // 2. Status Card (24dp radius)
        LinearLayout statusCard = new LinearLayout(context);
        statusCard.setOrientation(LinearLayout.HORIZONTAL);
        statusCard.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable statusCardBg = new GradientDrawable();
        statusCardBg.setColor(cardBgColor);
        statusCardBg.setCornerRadius(AndroidUtilities.dp(24));
        statusCard.setBackground(statusCardBg);
        statusCard.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16));

        LinearLayout.LayoutParams statusCardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statusCardLp.setMargins(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), 0);
        content.addView(statusCard, statusCardLp);

        // Left Icon in squircle/circle container
        FrameLayout statusIconBox = new FrameLayout(context);
        GradientDrawable statusIconBg = new GradientDrawable();
        statusIconBg.setColor(Theme.isCurrentThemeDark() ? 0xFF2A2A2E : 0xFFE5E5EA);
        statusIconBg.setCornerRadius(AndroidUtilities.dp(14));
        statusIconBox.setBackground(statusIconBg);

        ImageView statusIcon = new ImageView(context);
        statusIcon.setImageResource(R.drawable.msg_download_solar);
        statusIcon.setColorFilter(textPrimary);
        statusIconBox.addView(statusIcon, LayoutHelper.createFrame(22, 22, Gravity.CENTER));
        statusCard.addView(statusIconBox, LayoutHelper.createLinear(40, 40, Gravity.CENTER_VERTICAL, 0, 0, 14, 0));

        statusTextView = new TextView(context);
        statusTextView.setText(MiogramLocale.get("Встановлена остання версія", "Установлена последняя версия", "Latest version installed"));
        statusTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        statusTextView.setTypeface(AndroidUtilities.bold());
        statusTextView.setTextColor(textPrimary);
        statusCard.addView(statusTextView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));

        statusActionBtn = new TextView(context);
        statusActionBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        statusActionBtn.setTypeface(AndroidUtilities.bold());
        statusActionBtn.setTextColor(0xFF3B82F6);
        statusActionBtn.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(6), AndroidUtilities.dp(10), AndroidUtilities.dp(6));
        statusActionBtn.setVisibility(View.GONE);
        statusCard.addView(statusActionBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        // 3. "О клиенте" Section Header
        TextView infoHeader = new TextView(context);
        infoHeader.setText(MiogramLocale.get("О клієнті", "О клиенте", "About client"));
        infoHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        infoHeader.setTypeface(AndroidUtilities.bold());
        infoHeader.setTextColor(textSecondary);
        LinearLayout.LayoutParams infoHeaderLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        infoHeaderLp.setMargins(AndroidUtilities.dp(20), AndroidUtilities.dp(22), AndroidUtilities.dp(16), AndroidUtilities.dp(8));
        content.addView(infoHeader, infoHeaderLp);

        // 4. Info Card (24dp radius)
        LinearLayout infoCard = new LinearLayout(context);
        infoCard.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable infoCardBg = new GradientDrawable();
        infoCardBg.setColor(cardBgColor);
        infoCardBg.setCornerRadius(AndroidUtilities.dp(24));
        infoCard.setBackground(infoCardBg);
        infoCard.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(8), AndroidUtilities.dp(16), AndroidUtilities.dp(8));

        LinearLayout.LayoutParams infoCardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        infoCardLp.setMargins(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);
        content.addView(infoCard, infoCardLp);

        String clientVer;
        try {
            clientVer = BuildConfig.BUILD_VERSION_STRING;
        } catch (Throwable t) {
            clientVer = "0.9.2";
        }
        String buildNum;
        try {
            buildNum = String.valueOf(BuildConfig.VERSION_CODE);
        } catch (Throwable t) {
            buildNum = "7112";
        }

        // Row 1: Версия клиента
        infoCard.addView(createInfoRow(context, R.drawable.msg_info,
                MiogramLocale.get("Версія клієнта", "Версия клиента", "Client version"),
                null, clientVer, textPrimary, textSecondary));

        // Row 2: Версия API
        infoCard.addView(createInfoRow(context, R.drawable.msg_info,
                MiogramLocale.get("Версія API", "Версия API", "API version"),
                "Telegram API (TDLib)", "1.8.65", textPrimary, textSecondary));

        // Row 3: Номер сборки
        infoCard.addView(createInfoRow(context, R.drawable.msg_settings,
                MiogramLocale.get("Номер збірки", "Номер сборки", "Build number"),
                null, buildNum, textPrimary, textSecondary));

        // 5. Preferences & Channel Card (24dp radius)
        LinearLayout prefsHeader = new LinearLayout(context);
        TextView prefsTitle = new TextView(context);
        prefsTitle.setText(MiogramLocale.get("Параметри оновлень", "Параметры обновлений", "Update preferences"));
        prefsTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        prefsTitle.setTypeface(AndroidUtilities.bold());
        prefsTitle.setTextColor(textSecondary);
        LinearLayout.LayoutParams prefsHeaderLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        prefsHeaderLp.setMargins(AndroidUtilities.dp(20), AndroidUtilities.dp(20), AndroidUtilities.dp(16), AndroidUtilities.dp(8));
        content.addView(prefsTitle, prefsHeaderLp);

        LinearLayout prefsCard = new LinearLayout(context);
        prefsCard.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable prefsCardBg = new GradientDrawable();
        prefsCardBg.setColor(cardBgColor);
        prefsCardBg.setCornerRadius(AndroidUtilities.dp(24));
        prefsCard.setBackground(prefsCardBg);
        prefsCard.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(6), AndroidUtilities.dp(8), AndroidUtilities.dp(6));

        LinearLayout.LayoutParams prefsCardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        prefsCardLp.setMargins(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);
        content.addView(prefsCard, prefsCardLp);

        // Update channel row
        LinearLayout channelRow = new LinearLayout(context);
        channelRow.setOrientation(LinearLayout.HORIZONTAL);
        channelRow.setGravity(Gravity.CENTER_VERTICAL);
        channelRow.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12));
        channelRow.setBackground(Theme.createSelectorDrawable(0x1A888888, Theme.RIPPLE_MASK_ALL));
        channelRow.setOnClickListener(v -> showChannelChooser());

        TextView channelTitle = new TextView(context);
        channelTitle.setText(MiogramLocale.get("Канал оновлень", "Канал обновлений", "Update channel"));
        channelTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        channelTitle.setTextColor(textPrimary);
        channelRow.addView(channelTitle, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));

        channelValueView = new TextView(context);
        updateChannelLabel();
        channelValueView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        channelValueView.setTextColor(textSecondary);
        channelRow.addView(channelValueView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        prefsCard.addView(channelRow);

        // Auto-check switch
        autoCheckCell = new TextCheckCell(context);
        autoCheckCell.setTextAndCheck(MiogramLocale.get("Фонова автоперевірка", "Фоновая автопроверка", "Background auto-check"),
                MiogramUpdater.isAutoCheckEnabled(), true);
        autoCheckCell.setOnClickListener(v -> {
            boolean next = !MiogramUpdater.isAutoCheckEnabled();
            MiogramUpdater.setAutoCheckEnabled(next);
            autoCheckCell.setChecked(next);
        });
        prefsCard.addView(autoCheckCell);

        // Wi-Fi only switch
        wifiOnlyCell = new TextCheckCell(context);
        wifiOnlyCell.setTextAndCheck(MiogramLocale.get("Завантажувати лише через Wi-Fi", "Загружать только по Wi-Fi", "Download on Wi-Fi only"),
                MiogramUpdater.isWifiOnlyEnabled(), false);
        wifiOnlyCell.setOnClickListener(v -> {
            boolean next = !MiogramUpdater.isWifiOnlyEnabled();
            MiogramUpdater.setWifiOnlyEnabled(next);
            wifiOnlyCell.setChecked(next);
        });
        prefsCard.addView(wifiOnlyCell);

        // 6. Bottom Pinned Pill Button (28dp radius, 56dp height)
        FrameLayout buttonContainer = new FrameLayout(context);
        checkButton = new TextView(context);
        checkButton.setText(MiogramLocale.get("Перевірити", "Проверить", "Check"));
        checkButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        checkButton.setTypeface(AndroidUtilities.bold());
        checkButton.setTextColor(0xFF000000);
        checkButton.setGravity(Gravity.CENTER);

        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setColor(0xFFFFFFFF);
        pillBg.setCornerRadius(AndroidUtilities.dp(28));
        RippleDrawable pillRipple = new RippleDrawable(ColorStateList.valueOf(0x33000000), pillBg, null);
        checkButton.setBackground(pillRipple);

        checkButton.setOnClickListener(v -> onCheckClicked());

        FrameLayout.LayoutParams btnLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(56));
        btnLp.setMargins(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), AndroidUtilities.dp(16));
        btnLp.gravity = Gravity.BOTTOM;
        root.addView(checkButton, btnLp);

        refreshCachedFileState();
        fragmentView = root;
        return fragmentView;
    }

    private View createInfoRow(Context context, int iconRes, String title, String subtitle, String value, int titleColor, int valColor) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));

        // Circular white container (40dp) with dark icon
        FrameLayout iconBox = new FrameLayout(context);
        GradientDrawable iconBoxBg = new GradientDrawable();
        iconBoxBg.setColor(0xFFFFFFFF);
        iconBoxBg.setCornerRadius(AndroidUtilities.dp(20));
        iconBox.setBackground(iconBoxBg);

        ImageView icon = new ImageView(context);
        icon.setImageResource(iconRes);
        icon.setColorFilter(0xFF000000);
        iconBox.addView(icon, LayoutHelper.createFrame(20, 20, Gravity.CENTER));
        row.addView(iconBox, LayoutHelper.createLinear(40, 40, Gravity.CENTER_VERTICAL, 0, 0, 14, 0));

        // Title and optional subtitle
        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(titleColor);
        textCol.addView(titleView);

        if (subtitle != null) {
            TextView subView = new TextView(context);
            subView.setText(subtitle);
            subView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            subView.setTextColor(valColor);
            textCol.addView(subView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        }
        row.addView(textCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));

        // Value on the right
        TextView valView = new TextView(context);
        valView.setText(value);
        valView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        valView.setTextColor(valColor);
        row.addView(valView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        return row;
    }

    private void updateChannelLabel() {
        if (channelValueView == null) return;
        boolean beta = !MiogramUpdater.CHANNEL_STABLE.equals(MiogramUpdater.getUpdateChannel());
        channelValueView.setText(beta
                ? MiogramLocale.get("Бета (Beta)", "Бета (Beta)", "Beta")
                : MiogramLocale.get("Стабільний (Stable)", "Стабильный (Stable)", "Stable"));
    }

    private void refreshCachedFileState() {
        if (bannerView != null) {
            bannerView.setDownloading(MiogramDownloadManager.getInstance().isDownloading());
        }
        Context ctx = getContext();
        File cached = ctx != null ? MiogramDownloadManager.getCachedApk(ctx, null) : null;
        if (cached != null && cached.exists() && cached.length() > 0) {
            if (statusTextView != null) {
                statusTextView.setText(MiogramLocale.get("Оновлення завантажено", "Обновление скачано", "Update downloaded"));
            }
            if (statusActionBtn != null) {
                statusActionBtn.setVisibility(View.VISIBLE);
                statusActionBtn.setText(MiogramLocale.get("Встановити", "Установить", "Install"));
                statusActionBtn.setOnClickListener(v -> {
                    if (ctx != null) MiogramDownloadManager.promptInstall(ctx, cached);
                });
            }
            if (checkButton != null) {
                checkButton.setText(MiogramLocale.get("Встановити", "Установить", "Install"));
            }
        } else {
            if (statusActionBtn != null) {
                statusActionBtn.setVisibility(View.GONE);
            }
            if (checkButton != null) {
                checkButton.setText(MiogramLocale.get("Перевірити", "Проверить", "Check"));
            }
        }
    }

    private void onCheckClicked() {
        Context ctx = getContext();
        File cached = ctx != null ? MiogramDownloadManager.getCachedApk(ctx, null) : null;
        if (cached != null && cached.exists() && cached.length() > 0) {
            MiogramDownloadManager.promptInstall(ctx, cached);
            return;
        }

        if (bannerView != null) {
            bannerView.setChecking(true);
        }
        if (statusTextView != null) {
            statusTextView.setText(MiogramLocale.get("Перевірка оновлень...", "Проверка обновлений...", "Checking for updates..."));
        }

        MiogramUpdater.checkAndShowUpdate(this, true);

        AndroidUtilities.runOnUIThread(() -> {
            if (bannerView != null) {
                bannerView.setChecking(false);
            }
            refreshCachedFileState();
        }, 2200);
    }

    private void showChannelChooser() {
        try {
            Context ctx = getContext();
            if (ctx == null) return;
            boolean beta = !MiogramUpdater.CHANNEL_STABLE.equals(MiogramUpdater.getUpdateChannel());
            String[] items = new String[]{
                    MiogramLocale.get("Бета (Beta) — раніше фічі", "Бета (Beta) — раньше фичи", "Beta — earlier features"),
                    MiogramLocale.get("Стабільний (Stable) — перевірені релізи", "Стабильный (Stable) — проверенные релизы", "Stable — tested releases")
            };
            org.telegram.ui.ActionBar.AlertDialog.Builder b =
                    new org.telegram.ui.ActionBar.AlertDialog.Builder(ctx);
            b.setTitle(MiogramLocale.get("Канал оновлень", "Канал обновлений", "Update channel"));
            b.setItems(items, (d, which) -> {
                MiogramUpdater.setUpdateChannel(which == 0
                        ? MiogramUpdater.CHANNEL_BETA : MiogramUpdater.CHANNEL_STABLE);
                updateChannelLabel();
            });
            b.setNegativeButton(MiogramLocale.get("Скасувати", "Отмена", "Cancel"), null);
            b.show();
        } catch (Throwable ignore) {
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshCachedFileState();
    }

    @Override
    public boolean isLightStatusBar() {
        return !Theme.isCurrentThemeDark();
    }
}
