package app.miogram.bridge.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.widget.NestedScrollView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.io.File;

import app.miogram.bridge.MiogramLocale;
import app.miogram.bridge.updater.MiogramDownloadManager;
import app.miogram.bridge.updater.MiogramUpdater;

/**
 * Premium, Informative In-App Update Dialog for Amegram:
 * - Clear version comparison (current vs new build)
 * - Channel badge (Beta / Stable) and exact download size display
 * - Cleaned and formatted changelog view
 * - Real-time download progress with MB/s speed and ETA
 * - 24-hour smart snooze when dismissed
 * - Background minimization without cancelling download
 */
public class MiogramUpdateBottomSheet extends BottomSheet implements MiogramDownloadManager.DownloadListener {

    private final boolean hasUpdate;
    private final String versionName;
    private final String changelog;
    private final String apkDownloadUrl;
    private final long apkSize;

    private TextView installButton;
    private TextView cancelButton;
    private LinearLayout progressContainer;
    private ProgressBar progressBar;
    private TextView progressTextView;
    private TextView progressSpeedTextView;

    public MiogramUpdateBottomSheet(BaseFragment fragment, boolean hasUpdate, String versionName, String changelog, String apkDownloadUrl) {
        this(fragment, hasUpdate, versionName, changelog, apkDownloadUrl, 0L);
    }

    public MiogramUpdateBottomSheet(BaseFragment fragment, boolean hasUpdate, String versionName, String changelog, String apkDownloadUrl, long apkSize) {
        super(fragment.getParentActivity(), false, fragment.getResourceProvider());
        this.hasUpdate = hasUpdate;
        this.versionName = versionName;
        this.changelog = changelog;
        this.apkDownloadUrl = apkDownloadUrl;
        this.apkSize = apkSize;

        setApplyBottomPadding(false);
        setApplyTopPadding(false);
        fixNavigationBar(Theme.getColor(Theme.key_windowBackgroundWhite));

        Context ctx = fragment.getParentActivity();
        if (ctx == null) ctx = ApplicationLoader.applicationContext;

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setClickable(true);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        root.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(16), AndroidUtilities.dp(20), AndroidUtilities.dp(16));

        // 1. Title
        SimpleTextView title = new SimpleTextView(ctx);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextSize(18);
        title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        title.setText(hasUpdate
                ? MiogramLocale.get("Вийшло нове оновлення!", "Вышло новое обновление!", "New Update Available!")
                : MiogramLocale.get("Встановлена остання версія", "Установлена последняя версия", "Latest Version Installed"));
        title.setGravity(Gravity.CENTER);
        root.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 6));

        // 2. Version Comparison & Badges Row
        String currentVer = MiogramUpdater.getCurrentAppVersion();
        boolean isCached = hasUpdate && MiogramDownloadManager.isApkCached(ctx, versionName);

        LinearLayout badgesRow = new LinearLayout(ctx);
        badgesRow.setOrientation(LinearLayout.HORIZONTAL);
        badgesRow.setGravity(Gravity.CENTER);

        // Version pill
        TextView versionBadge = new TextView(ctx);
        if (hasUpdate) {
            versionBadge.setText("v" + currentVer + " ➔ v" + versionName);
        } else {
            versionBadge.setText("v" + currentVer + " (" + MiogramLocale.get("актуальна", "актуальная", "up to date") + ")");
        }
        versionBadge.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        versionBadge.setTypeface(AndroidUtilities.bold());
        versionBadge.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(4), AndroidUtilities.dp(10), AndroidUtilities.dp(4));
        int badgeBg = hasUpdate ? Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText), 0.12f)
                : Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2), 0.12f);
        versionBadge.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(10), badgeBg));
        versionBadge.setTextColor(hasUpdate ? Theme.getColor(Theme.key_windowBackgroundWhiteBlueText) : Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        badgesRow.addView(versionBadge, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 6, 0));

        // Channel & Size pill
        if (hasUpdate) {
            TextView channelBadge = new TextView(ctx);
            String channelName = MiogramUpdater.getUpdateChannelName();
            String sizeStr = "";
            File cachedFile = MiogramDownloadManager.getCachedApk(ctx, versionName);
            if (cachedFile != null && cachedFile.length() > 0) {
                sizeStr = " • " + AndroidUtilities.formatFileSize(cachedFile.length());
            } else if (apkSize > 0) {
                sizeStr = " • " + AndroidUtilities.formatFileSize(apkSize);
            }
            channelBadge.setText(channelName + sizeStr);
            channelBadge.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            channelBadge.setTypeface(AndroidUtilities.bold());
            channelBadge.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(4), AndroidUtilities.dp(10), AndroidUtilities.dp(4));
            int chanBg = Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteGreenText), 0.12f);
            channelBadge.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(10), chanBg));
            channelBadge.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGreenText));
            badgesRow.addView(channelBadge, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        }

        root.addView(badgesRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 10));

        // 3. Illustration Preview (Smooth rounded corners 12dp)
        ImageView illustrationView = new ImageView(ctx);
        illustrationView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        int imageRes = hasUpdate ? R.drawable.img_update_available : R.drawable.img_update_none;
        try {
            Bitmap raw = BitmapFactory.decodeResource(ctx.getResources(), imageRes);
            if (raw != null) {
                illustrationView.setImageBitmap(getSmoothRounded16by9Bitmap(raw, AndroidUtilities.dp(12)));
            } else {
                illustrationView.setImageResource(imageRes);
            }
        } catch (Throwable t) {
            illustrationView.setImageResource(imageRes);
        }
        root.addView(illustrationView, LayoutHelper.createLinear(220, 115, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 10));

        // 4. Soft Card Container for Changelog
        LinearLayout cardLayout = new LinearLayout(ctx);
        cardLayout.setOrientation(LinearLayout.VERTICAL);
        cardLayout.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(10), Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundGray), 0.85f)));
        cardLayout.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12), AndroidUtilities.dp(14), AndroidUtilities.dp(12));

        TextView changelogTitle = new TextView(ctx);
        changelogTitle.setText(MiogramLocale.get("Що нового:", "Что нового:", "What's new:"));
        changelogTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13.5f);
        changelogTitle.setTypeface(AndroidUtilities.bold());
        changelogTitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        cardLayout.addView(changelogTitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 4));

        NestedScrollView changelogScroll = new NestedScrollView(ctx);
        TextView descriptionView = new TextView(ctx);
        if (hasUpdate) {
            descriptionView.setText(formatChangelog(changelog));
        } else {
            descriptionView.setText(MiogramLocale.get("У вас встановлено найновішу збірку Amegram.",
                    "У вас установлена самая новая сборка Amegram.",
                    "You have the latest build of Amegram."));
        }
        descriptionView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        descriptionView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        descriptionView.setLineSpacing(AndroidUtilities.dp(2), 1.15f);

        changelogScroll.addView(descriptionView);
        cardLayout.addView(changelogScroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        root.addView(cardLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        // 5. Progress Container
        progressContainer = new LinearLayout(ctx);
        progressContainer.setOrientation(LinearLayout.VERTICAL);
        progressContainer.setVisibility(View.GONE);

        progressBar = new ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        progressContainer.addView(progressBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 4, 0, 0, 0, 4));

        LinearLayout progressLabels = new LinearLayout(ctx);
        progressLabels.setOrientation(LinearLayout.HORIZONTAL);

        progressTextView = new TextView(ctx);
        progressTextView.setText(MiogramLocale.get("Завантаження: 0%", "Загрузка: 0%", "Downloading: 0%"));
        progressTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        progressTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        progressLabels.addView(progressTextView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));

        progressSpeedTextView = new TextView(ctx);
        progressSpeedTextView.setText("");
        progressSpeedTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        progressSpeedTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        progressSpeedTextView.setGravity(Gravity.RIGHT);
        progressLabels.addView(progressSpeedTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        progressContainer.addView(progressLabels, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));
        root.addView(progressContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

        // 6. Action Buttons
        installButton = new TextView(ctx);
        if (isCached) {
            installButton.setText(MiogramLocale.get("Встановити оновлення", "Установить обновление", "Install Update"));
            final Context finalCtx = ctx;
            installButton.setOnClickListener(v -> {
                File apk = MiogramDownloadManager.getCachedApk(finalCtx, versionName);
                MiogramDownloadManager.promptInstall(finalCtx, apk);
                dismiss();
            });
        } else if (hasUpdate) {
            installButton.setText(MiogramLocale.get("Завантажити та встановити", "Скачать и установить", "Download & Install"));
            installButton.setOnClickListener(v -> startDownload(fragment));
        } else {
            installButton.setText(MiogramLocale.get("Зрозуміло", "Понятно", "Got it"));
            installButton.setOnClickListener(v -> dismiss());
        }
        installButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14.5f);
        installButton.setTypeface(AndroidUtilities.bold());
        installButton.setGravity(Gravity.CENTER);
        installButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(10), Theme.getColor(Theme.key_featuredStickers_addButton), Theme.getColor(Theme.key_featuredStickers_addButton)));
        installButton.setTextColor(0xFFFFFFFF);
        root.addView(installButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, Gravity.TOP, 0, 0, 0, hasUpdate ? 6 : 0));

        if (hasUpdate) {
            cancelButton = new TextView(ctx);
            cancelButton.setText(MiogramLocale.get("Нагадати пізніше", "Напомнить позже", "Remind me later"));
            cancelButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13.5f);
            cancelButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            cancelButton.setGravity(Gravity.CENTER);
            cancelButton.setPadding(0, AndroidUtilities.dp(6), 0, AndroidUtilities.dp(6));
            cancelButton.setOnClickListener(v -> {
                MiogramUpdater.snoozeUpdate(versionName);
                dismiss();
            });
            root.addView(cancelButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0));
        }

        FrameLayout fl = new FrameLayout(ctx);
        fl.addView(root);

        NestedScrollView sv = new NestedScrollView(ctx);
        sv.addView(fl);
        setCustomView(sv);

        MiogramDownloadManager dm = MiogramDownloadManager.getInstance();
        if (dm.isDownloading()) {
            dm.addListener(this);
            showProgressUI();
        }
    }

    private String formatChangelog(String raw) {
        if (TextUtils.isEmpty(raw)) {
            return MiogramLocale.get(
                    "• Оновлено двигун Amegram AI\n• Нативна розшифровка голосових повідомлень\n• Оптимізація та підвищення стабільності",
                    "• Обновлен движок Amegram AI\n• Нативная расшифровка голосовых сообщений\n• Оптимизация и повышение стабильности",
                    "• Amegram AI engine updates\n• Native voice message transcription\n• Stability and performance optimizations"
            );
        }

        String[] lines = raw.replace("\r", "").split("\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            if (t.startsWith("#")) {
                t = t.replaceAll("^#+\\s*", "");
                if (sb.length() > 0) sb.append("\n");
                sb.append(t).append(":\n");
            } else if (t.startsWith("- ") || t.startsWith("* ")) {
                sb.append("• ").append(t.substring(2).trim()).append("\n");
            } else if (t.startsWith("• ")) {
                sb.append(t).append("\n");
            } else {
                sb.append("• ").append(t).append("\n");
            }
        }
        return sb.toString().trim();
    }

    private void showProgressUI() {
        if (installButton != null) {
            installButton.setText(MiogramLocale.get("Згорнути", "Свернуть", "Minimize"));
            installButton.setEnabled(true);
            installButton.setAlpha(1.0f);
            installButton.setOnClickListener(v -> dismiss());
        }
        if (cancelButton != null) {
            cancelButton.setVisibility(View.VISIBLE);
            cancelButton.setText(MiogramLocale.get("Скасувати завантаження", "Отменить загрузку", "Cancel Download"));
            cancelButton.setOnClickListener(v -> {
                MiogramDownloadManager.getInstance().cancelDownload();
                dismiss();
            });
        }
        if (progressContainer != null) progressContainer.setVisibility(View.VISIBLE);
    }

    private void startDownload(BaseFragment fragment) {
        Context ctx = fragment.getParentActivity();
        if (ctx == null) ctx = ApplicationLoader.applicationContext;

        MiogramDownloadManager dm = MiogramDownloadManager.getInstance();
        dm.addListener(this);
        showProgressUI();
        dm.startDownload(ctx, apkDownloadUrl, versionName, changelog);
    }

    private Bitmap getSmoothRounded16by9Bitmap(Bitmap bitmap, int pixels) {
        int width = bitmap.getWidth();
        int targetHeight = (width * 9) / 16;
        if (targetHeight > bitmap.getHeight()) {
            targetHeight = bitmap.getHeight();
        }

        Bitmap cropped = Bitmap.createBitmap(bitmap, 0, Math.max(0, (bitmap.getHeight() - targetHeight) / 2), width, targetHeight);
        Bitmap output = Bitmap.createBitmap(cropped.getWidth(), cropped.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final Rect rect = new Rect(0, 0, cropped.getWidth(), cropped.getHeight());
        final RectF rectF = new RectF(rect);

        paint.setColor(0xff424242);
        canvas.drawRoundRect(rectF, pixels, pixels, paint);

        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(cropped, rect, rect, paint);

        return output;
    }

    @Override
    public void onProgress(int percent, long downloadedBytes, long totalBytes) {
        onProgressDetailed(percent, downloadedBytes, totalBytes, 0, 0);
    }

    @Override
    public void onProgressDetailed(int percent, long downloadedBytes, long totalBytes, long speedBytesPerSec, int etaSeconds) {
        new Handler(Looper.getMainLooper()).post(() -> {
            showProgressUI();
            if (progressBar != null) progressBar.setProgress(percent);
            if (progressTextView != null) {
                String dl = AndroidUtilities.formatFileSize(downloadedBytes);
                String total = totalBytes > 0 ? AndroidUtilities.formatFileSize(totalBytes) : "...";
                progressTextView.setText(MiogramLocale.format("Завантаження: %d%% (%s / %s)", "Загрузка: %d%% (%s / %s)", "Downloading: %d%% (%s / %s)", percent, dl, total));
            }
            if (progressSpeedTextView != null) {
                String speedStr = speedBytesPerSec > 0 ? AndroidUtilities.formatFileSize(speedBytesPerSec) + "/s" : "";
                String etaStr = etaSeconds > 0 ? " ~" + etaSeconds + "s" : "";
                progressSpeedTextView.setText(speedStr + (etaStr.isEmpty() ? "" : " •" + etaStr));
            }
        });
    }

    @Override
    public void onComplete(File apkFile) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (installButton != null) {
                installButton.setText(MiogramLocale.get("Встановити оновлення", "Установить обновление", "Install Update"));
                installButton.setOnClickListener(v -> {
                    Context ctx = getContext();
                    MiogramDownloadManager.promptInstall(ctx, apkFile);
                    dismiss();
                });
            }
            dismiss();
        });
    }

    @Override
    public void onError(String error) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (installButton != null) {
                installButton.setText(MiogramLocale.get("Повторити", "Повторить", "Retry"));
                installButton.setEnabled(true);
                installButton.setAlpha(1f);
                installButton.setOnClickListener(v -> {
                    Context ctx = getContext();
                    MiogramDownloadManager.getInstance().startDownload(ctx, apkDownloadUrl, versionName, changelog);
                });
            }
            if (cancelButton != null) {
                cancelButton.setText(MiogramLocale.get("Закрити", "Закрыть", "Close"));
                cancelButton.setOnClickListener(v -> dismiss());
            }
        });
    }

    @Override
    public void dismiss() {
        MiogramDownloadManager.getInstance().removeListener(this);
        super.dismiss();
    }
}
