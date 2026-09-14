package app.miogram.bridge.media;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;

import app.miogram.bridge.MiogramLocale;
import app.miogram.bridge.customui.MiogramHaptic;

/**
 * Native floating suggestion cloud ("хмарка") that appears above chat input
 * when user pastes a video/media link (TikTok, YouTube, Instagram, X/Twitter).
 * Offers one-tap direct download & sending as native video or photo album + audio.
 */
public class MiogramMediaDownloadPill extends FrameLayout {

    private final Theme.ResourcesProvider resourcesProvider;
    private final Runnable onSendCompleted;
    private final OnStartDownloadListener onStartDownloadListener;

    public interface OnStartDownloadListener {
        void onStartDownload(String mediaUrl, MiogramMediaDownloader.ProgressListener progressListener, MiogramMediaDownloader.CompletionCallback callback);
    }

    private String currentUrl;
    private MiogramMediaDownloader.MediaLinkInfo currentInfo;
    private boolean isDownloading = false;
    private boolean isDismissedForCurrentUrl = false;
    // Docked Y offset above the input (negative). Replaces the old pattern of
    // animating translationY back to 0, which dropped the pill onto the text.
    private float dockTranslationY = 0f;
    private boolean showAnimRunning = false;

    // UI elements
    private final FrameLayout pillContainer;
    private final FrameLayout badgeIconHolder;
    private final ImageView badgeIcon;
    private final LinearLayout textColumn;
    private final TextView titleView;
    private final TextView subtitleView;
    private final ProgressBar progressBar;
    private final FrameLayout actionBtn;
    private final ImageView actionIcon;
    private final ImageView closeBtn;

    public MiogramMediaDownloadPill(Context context, Theme.ResourcesProvider resourcesProvider,
                                    OnStartDownloadListener onStartDownloadListener, Runnable onSendCompleted) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        this.onStartDownloadListener = onStartDownloadListener;
        this.onSendCompleted = onSendCompleted;

        setClipChildren(false);
        setClipToPadding(false);

        int accentColor = getThemedColor(Theme.key_chat_attachAudioBackground);
        if (accentColor == 0) accentColor = getThemedColor(Theme.key_windowBackgroundWhiteBlueHeader);
        if (accentColor == 0) accentColor = 0xFF3390EC;

        int surfaceColor = getThemedColor(Theme.key_dialogBackground);
        if (surfaceColor == 0) surfaceColor = 0xFF1E202B;

        pillContainer = new FrameLayout(context);
        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setColor(ColorUtils.setAlphaComponent(surfaceColor, 246));
        pillBg.setCornerRadius(AndroidUtilities.dp(20));
        pillBg.setStroke(AndroidUtilities.dp(1), ColorUtils.setAlphaComponent(accentColor, 90));
        pillContainer.setBackground(pillBg);
        pillContainer.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(6), AndroidUtilities.dp(8), AndroidUtilities.dp(6));

        if (Build.VERSION.SDK_INT >= 21) {
            pillContainer.setElevation(AndroidUtilities.dp(6));
        }

        LinearLayout contentRow = new LinearLayout(context);
        contentRow.setOrientation(LinearLayout.HORIZONTAL);
        contentRow.setGravity(Gravity.CENTER_VERTICAL);

        // 1. Badge Icon (32x32)
        badgeIconHolder = new FrameLayout(context);
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setShape(GradientDrawable.OVAL);
        badgeBg.setColor(ColorUtils.setAlphaComponent(accentColor, 36));
        badgeIconHolder.setBackground(badgeBg);

        badgeIcon = new ImageView(context);
        badgeIcon.setImageResource(R.drawable.msg_video);
        badgeIcon.setScaleType(ImageView.ScaleType.CENTER);
        badgeIcon.setColorFilter(new PorterDuffColorFilter(accentColor, PorterDuff.Mode.SRC_IN));
        badgeIconHolder.addView(badgeIcon, LayoutHelper.createFrame(20, 20, Gravity.CENTER));

        contentRow.addView(badgeIconHolder, LayoutHelper.createLinear(34, 34, Gravity.CENTER_VERTICAL, 0, 0, 8, 0));

        // 2. Text Column (Title + Subtitle / Progress)
        textColumn = new LinearLayout(context);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        textColumn.setGravity(Gravity.CENTER_VERTICAL);

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(getThemedColor(Theme.key_dialogTextBlack) != 0 ? getThemedColor(Theme.key_dialogTextBlack) : 0xFFFFFFFF);
        titleView.setSingleLine(true);
        textColumn.addView(titleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        subtitleView = new TextView(context);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        subtitleView.setTextColor(ColorUtils.setAlphaComponent(titleView.getCurrentTextColor(), 170));
        subtitleView.setSingleLine(true);
        textColumn.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 1, 0, 0));

        // Horizontal mini progress bar (shown during download)
        progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        progressBar.setVisibility(View.GONE);
        if (Build.VERSION.SDK_INT >= 21) {
            progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(accentColor));
        }
        textColumn.addView(progressBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 4, 0, 3, 0, 0));

        contentRow.addView(textColumn, new LinearLayout.LayoutParams(0, LayoutHelper.WRAP_CONTENT, 1.0f));

        // 3. Action Button (Send / Download arrow)
        actionBtn = new FrameLayout(context);
        GradientDrawable actionBg = new GradientDrawable();
        actionBg.setShape(GradientDrawable.OVAL);
        actionBg.setColor(accentColor);
        actionBtn.setBackground(actionBg);

        actionIcon = new ImageView(context);
        actionIcon.setImageResource(R.drawable.arrow_more);
        actionIcon.setScaleType(ImageView.ScaleType.CENTER);
        actionIcon.setColorFilter(new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN));
        actionBtn.addView(actionIcon, LayoutHelper.createFrame(16, 16, Gravity.CENTER));

        actionBtn.setOnClickListener(v -> triggerDownload());
        contentRow.addView(actionBtn, LayoutHelper.createLinear(28, 28, Gravity.CENTER_VERTICAL, 6, 0, 4, 0));

        // 4. Dismiss Close Button ("✕")
        closeBtn = new ImageView(context);
        closeBtn.setImageResource(R.drawable.ic_ab_close);
        closeBtn.setScaleType(ImageView.ScaleType.CENTER);
        closeBtn.setColorFilter(new PorterDuffColorFilter(ColorUtils.setAlphaComponent(titleView.getCurrentTextColor(), 140), PorterDuff.Mode.SRC_IN));
        closeBtn.setOnClickListener(v -> {
            MiogramHaptic.tap(v);
            isDismissedForCurrentUrl = true;
            hide();
        });
        contentRow.addView(closeBtn, LayoutHelper.createLinear(26, 26, Gravity.CENTER_VERTICAL, 2, 0, 0, 0));

        pillContainer.addView(contentRow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));
        addView(pillContainer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        // Tapping the whole pill also triggers the download
        pillContainer.setOnClickListener(v -> triggerDownload());

        setVisibility(View.GONE);
    }

    /**
     * Inspects candidate text and shows the cloud pill if a media URL is found.
     */
    public void inspectText(CharSequence text) {
        if (isDownloading) return; // Don't interrupt active download

        MiogramMediaDownloader.MediaLinkInfo info = MiogramMediaDownloader.extractSupportedUrl(text);
        if (info == null) {
            currentUrl = null;
            currentInfo = null;
            isDismissedForCurrentUrl = false;
            hide();
            return;
        }

        if (info.url.equals(currentUrl)) {
            if (isDismissedForCurrentUrl) return;
            if (getVisibility() != View.VISIBLE) show();
            return;
        }

        // New URL found
        currentUrl = info.url;
        currentInfo = info;
        isDismissedForCurrentUrl = false;

        // Configure presentation
        updateBadgeAndTexts(info);
        show();
    }

    private void updateBadgeAndTexts(MiogramMediaDownloader.MediaLinkInfo info) {
        int accentColor = getThemedColor(Theme.key_chat_attachAudioBackground);
        if (accentColor == 0) accentColor = 0xFF3390EC;

        if (info.isLikelySlideshow) {
            titleView.setText(MiogramLocale.get("Надіслати як альбом + аудіо 📸", "Отправить как альбом + аудио 📸", "Send as photo album + audio 📸"));
            subtitleView.setText(info.platformName + MiogramLocale.get(" • Всі фото та оригінальний звук", " • Все фото и оригинальный звук", " • Photos & sound"));
            badgeIcon.setImageResource(R.drawable.msg_photos);
        } else {
            titleView.setText(MiogramLocale.get("Надіслати як відео 🎬", "Отправить как видео 🎬", "Send as video 🎬"));
            subtitleView.setText(info.platformName + MiogramLocale.get(" • Без водяного знаку у високій якості", " • Без водяного знака в высоком качестве", " • HD no-watermark"));
            badgeIcon.setImageResource(R.drawable.msg_video);
        }

        progressBar.setVisibility(View.GONE);
        subtitleView.setVisibility(View.VISIBLE);
        actionBtn.setVisibility(View.VISIBLE);
        closeBtn.setVisibility(View.VISIBLE);
    }

    private void triggerDownload() {
        if (isDownloading || currentUrl == null) return;
        isDownloading = true;
        MiogramHaptic.tap(this);

        // Switch to progress mode
        titleView.setText(MiogramLocale.get("Завантаження медіа...", "Загрузка медиа...", "Downloading media..."));
        subtitleView.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setProgress(0);
        closeBtn.setVisibility(View.GONE);

        // Tactile squeeze animation
        animate().scaleX(0.96f).scaleY(0.96f).setDuration(80).withEndAction(() -> {
            animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
        }).start();

        if (onStartDownloadListener != null) {
            onStartDownloadListener.onStartDownload(currentUrl, (percent, statusText) -> {
                titleView.setText(statusText);
                progressBar.setProgress(percent);
            }, new MiogramMediaDownloader.CompletionCallback() {
                @Override
                public void onSuccess(String message) {
                    isDownloading = false;
                    titleView.setText(message);
                    progressBar.setVisibility(View.GONE);

                    if (onSendCompleted != null) {
                        onSendCompleted.run();
                    }

                    postDelayed(() -> hide(), 800);
                }

                @Override
                public void onError(String errorText) {
                    isDownloading = false;
                    titleView.setText(errorText);
                    progressBar.setVisibility(View.GONE);
                    subtitleView.setVisibility(View.VISIBLE);
                    subtitleView.setText(MiogramLocale.get("Торкніться, щоб спробувати знову", "Коснитесь, чтобы попробовать снова", "Tap to retry"));
                    closeBtn.setVisibility(View.VISIBLE);

                    // Shake animation
                    animate().translationXBy(AndroidUtilities.dp(8)).setDuration(60).withEndAction(() -> {
                        animate().translationXBy(-AndroidUtilities.dp(16)).setDuration(60).withEndAction(() -> {
                            animate().translationX(0).setDuration(60).start();
                        }).start();
                    }).start();

                    postDelayed(() -> {
                        if (!isDownloading) hide();
                    }, 2800);
                }
            });
        }
    }

    /**
     * Updates where the pill docks above the input. Applied immediately when
     * visible and idle, otherwise picked up by the next show() animation.
     */
    public void setDockTranslationY(float y) {
        dockTranslationY = y;
        if (getVisibility() == View.VISIBLE && !showAnimRunning) {
            setTranslationY(y);
        }
    }

    public float getDockTranslationY() {
        return dockTranslationY;
    }

    public void show() {
        if (getVisibility() == View.VISIBLE && getAlpha() >= 0.9f) return;

        animate().cancel();
        setVisibility(View.VISIBLE);
        setAlpha(0f);
        setScaleX(0.82f);
        setScaleY(0.82f);
        setTranslationY(dockTranslationY + AndroidUtilities.dp(16));

        showAnimRunning = true;
        animate()
                .alpha(1.0f)
                .scaleX(1.0f)
                .scaleY(1.0f)
                .translationY(dockTranslationY)
                .setDuration(240)
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        showAnimRunning = false;
                        setTranslationY(dockTranslationY);
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        showAnimRunning = false;
                    }
                })
                .start();
    }

    public void hide() {
        if (getVisibility() != View.VISIBLE) return;

        animate().cancel();
        animate()
                .alpha(0f)
                .scaleX(0.82f)
                .scaleY(0.82f)
                .translationY(dockTranslationY + AndroidUtilities.dp(12))
                .setDuration(180)
                .setInterpolator(CubicBezierInterpolator.DEFAULT)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        setVisibility(View.GONE);
                        setTranslationY(dockTranslationY);
                        setScaleX(1f);
                        setScaleY(1f);
                    }
                })
                .start();
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }
}
