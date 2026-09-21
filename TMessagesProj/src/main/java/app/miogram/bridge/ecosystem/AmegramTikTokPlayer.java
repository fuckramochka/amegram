package app.miogram.bridge.ecosystem;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.ScaleStateListAnimator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

import app.miogram.bridge.MiogramLocale;
import app.miogram.bridge.customui.MiogramHaptic;

/**
 * Native in-app video player bottom sheet for TikTok links in Amegram.
 * Plays clean no-watermark HD video without leaving the messenger.
 * Includes direct Saved Messages sharing, local MP4 download, and TikTok MI sync.
 */
public class AmegramTikTokPlayer extends BottomSheet implements SurfaceHolder.Callback {

    private final String originalUrl;
    private SurfaceView surfaceView;
    private MediaPlayer mediaPlayer;
    private RadialProgressView loadingView;
    private FrameLayout videoContainer;
    private ImageView playPauseBtn;
    private TextView titleView;
    private TextView authorView;
    private BackupImageView coverView;

    private String resolvedVideoUrl;
    private String videoTitle = "";
    private String videoAuthor = "";
    private String coverUrl = "";
    private File cachedVideoFile;
    private boolean isPlaying = false;
    private boolean isSurfaceReady = false;

    public static void show(Context context, String url) {
        if (context == null || TextUtils.isEmpty(url)) return;
        AmegramTikTokPlayer sheet = new AmegramTikTokPlayer(context, url);
        sheet.show();
    }

    public AmegramTikTokPlayer(Context context, String url) {
        super(context, false);
        this.originalUrl = url;
        setApplyTopPadding(false);
        setApplyBottomPadding(false);

        initUi(context);
        resolveAndPlay();
    }

    private void initUi(Context context) {
        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(0xFF0F141C);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(16));
        root.addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // 1. Header Bar: TikTok MI Badge + Close
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        // Aesthetic TikTok pill badge
        TextView badge = new TextView(context);
        badge.setText("TIKTOK MI • IN-APP PLAYER");
        badge.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        badge.setTypeface(AndroidUtilities.bold());
        badge.setTextColor(0xFF00F2FE);
        badge.setLetterSpacing(0.04f);
        header.addView(badge, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL));

        TextView closeBtn = new TextView(context);
        closeBtn.setText("✕");
        closeBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        closeBtn.setTextColor(0x88FFFFFF);
        closeBtn.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(4), AndroidUtilities.dp(8), AndroidUtilities.dp(4));
        closeBtn.setOnClickListener(v -> dismiss());
        header.addView(closeBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        // 2. Video View Container (9:16 vertical ratio max height)
        int screenW = AndroidUtilities.displaySize.x;
        int screenH = AndroidUtilities.displaySize.y;
        int videoH = (int) Math.min(screenH * 0.52f, AndroidUtilities.dp(380));

        videoContainer = new FrameLayout(context);
        GradientDrawable videoBg = new GradientDrawable();
        videoBg.setColor(0xFF000000);
        videoBg.setCornerRadius(AndroidUtilities.dp(16));
        videoContainer.setBackground(videoBg);
        videoContainer.setClipToOutline(true);
        content.addView(videoContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, videoH, 0, 0, 0, 12));

        surfaceView = new SurfaceView(context);
        surfaceView.getHolder().addCallback(this);
        videoContainer.addView(surfaceView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));

        coverView = new BackupImageView(context);
        coverView.setRoundRadius(AndroidUtilities.dp(16));
        videoContainer.addView(coverView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        loadingView = new RadialProgressView(context);
        loadingView.setProgressColor(0xFF00F2FE);
        loadingView.setSize(AndroidUtilities.dp(40));
        videoContainer.addView(loadingView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        playPauseBtn = new ImageView(context);
        playPauseBtn.setImageResource(R.drawable.ic_play);
        playPauseBtn.setColorFilter(0xFFFFFFFF);
        playPauseBtn.setVisibility(View.GONE);
        videoContainer.addView(playPauseBtn, LayoutHelper.createFrame(56, 56, Gravity.CENTER));

        videoContainer.setOnClickListener(v -> {
            if (mediaPlayer != null && isPlaying) {
                mediaPlayer.pause();
                isPlaying = false;
                playPauseBtn.setVisibility(View.VISIBLE);
            } else if (mediaPlayer != null) {
                mediaPlayer.start();
                isPlaying = true;
                playPauseBtn.setVisibility(View.GONE);
            }
        });

        // 3. Info texts: Author + Title
        authorView = new TextView(context);
        authorView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        authorView.setTextColor(0xFF00F2FE);
        authorView.setTypeface(AndroidUtilities.bold());
        authorView.setSingleLine(true);
        authorView.setEllipsize(TextUtils.TruncateAt.END);
        content.addView(authorView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 2));

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14.5f);
        titleView.setTextColor(0xFFFFFFFF);
        titleView.setMaxLines(2);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        content.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));

        // 4. Action buttons: Save to TG, Download, Open in TT MI
        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        content.addView(actions, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView btnSave = createActionButton(context, MiogramLocale.get("В Збережене", "В Избранное", "Saved Messages"), 0xFF00F2FE, 0xFF000000);
        btnSave.setOnClickListener(v -> {
            MiogramHaptic.click(v);
            saveToSavedMessages();
        });
        actions.addView(btnSave, LayoutHelper.createLinear(0, 40, 1.2f, 0, 0, 6, 0));

        TextView btnDownload = createActionButton(context, MiogramLocale.get("Завантажити", "Скачать", "Download"), 0x2AFFFFFF, 0xFFFFFFFF);
        btnDownload.setOnClickListener(v -> {
            MiogramHaptic.click(v);
            downloadVideo();
        });
        actions.addView(btnDownload, LayoutHelper.createLinear(0, 40, 1f, 0, 0, 6, 0));

        TextView btnTtmi = createActionButton(context, "TikTok MI", 0x1AFE2C55, 0xFFFE2C55);
        btnTtmi.setOnClickListener(v -> {
            MiogramHaptic.click(v);
            AmegramTikTokBridge.openInTikTokMi(getContext(), originalUrl);
            dismiss();
        });
        actions.addView(btnTtmi, LayoutHelper.createLinear(0, 40, 1f, 0, 0, 0, 0));

        setCustomView(root);
    }

    private TextView createActionButton(Context context, String text, int bgColor, int textColor) {
        TextView btn = new TextView(context);
        btn.setText(text);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
        btn.setTypeface(AndroidUtilities.bold());
        btn.setTextColor(textColor);
        btn.setGravity(Gravity.CENTER);
        btn.setSingleLine(true);
        btn.setEllipsize(TextUtils.TruncateAt.END);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(AndroidUtilities.dp(12));
        btn.setBackground(bg);

        ScaleStateListAnimator.apply(btn, 0.035f, 1.4f);
        return btn;
    }

    private void resolveAndPlay() {
        titleView.setText(MiogramLocale.get("Аналіз посилання TikTok...", "Анализ ссылки TikTok...", "Resolving TikTok link..."));
        authorView.setText("@tiktok");

        Utilities.globalQueue.postRunnable(() -> {
            try {
                String target = originalUrl;
                // Resolve short redirects
                if (target.contains("vm.tiktok.com") || target.contains("vt.tiktok.com")) {
                    HttpURLConnection conn = (HttpURLConnection) new URL(target).openConnection();
                    conn.setInstanceFollowRedirects(false);
                    conn.connect();
                    String loc = conn.getHeaderField("Location");
                    if (!TextUtils.isEmpty(loc)) target = loc;
                }

                String apiUrl = "https://www.tikwm.com/api/?url=" + URLEncoder.encode(target, "UTF-8") + "&hd=1";
                HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(10000);

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String l;
                    while ((l = reader.readLine()) != null) sb.append(l);
                    reader.close();

                    JSONObject json = new JSONObject(sb.toString());
                    if (json.optInt("code", -1) == 0 && json.has("data")) {
                        JSONObject data = json.getJSONObject("data");
                        String hdplay = data.optString("hdplay", "");
                        String play = data.optString("play", "");
                        resolvedVideoUrl = !TextUtils.isEmpty(hdplay) ? hdplay : play;
                        if (!TextUtils.isEmpty(resolvedVideoUrl) && resolvedVideoUrl.startsWith("/")) {
                            resolvedVideoUrl = "https://www.tikwm.com" + resolvedVideoUrl;
                        }

                        videoTitle = data.optString("title", "");
                        JSONObject authorObj = data.optJSONObject("author");
                        videoAuthor = authorObj != null ? authorObj.optString("unique_id", "creator") : "creator";
                        coverUrl = data.optString("cover", "");

                        // Cache video file for smooth playback & instant sharing
                        File cacheDir = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE);
                        cachedVideoFile = new File(cacheDir, "tiktok_stream_" + System.currentTimeMillis() + ".mp4");

                        HttpURLConnection vConn = (HttpURLConnection) new URL(resolvedVideoUrl).openConnection();
                        vConn.setConnectTimeout(8000);
                        vConn.setReadTimeout(15000);
                        if (vConn.getResponseCode() == 200) {
                            try (InputStream in = vConn.getInputStream();
                                 FileOutputStream out = new FileOutputStream(cachedVideoFile)) {
                                byte[] buf = new byte[8192];
                                int len;
                                while ((len = in.read(buf)) > 0) {
                                    out.write(buf, 0, len);
                                }
                            }
                        }

                        // Register active watching in bridge
                        AmegramTikTokBridge.setCurrentlyWatching(originalUrl, videoTitle, videoAuthor, coverUrl);

                        AndroidUtilities.runOnUIThread(this::startPlayback);
                        return;
                    }
                }
            } catch (Throwable t) {
                FileLog.e("AmegramTikTokPlayer: resolve error", t);
            }

            AndroidUtilities.runOnUIThread(() -> {
                loadingView.setVisibility(View.GONE);
                titleView.setText(MiogramLocale.get("Не вдалося завантажити відео", "Не удалось загрузить видео", "Failed to load video"));
                Toast.makeText(getContext(), MiogramLocale.get("Відкриваємо у TikTok MI...", "Открываем в TikTok MI...", "Opening in TikTok MI..."), Toast.LENGTH_SHORT).show();
                AmegramTikTokBridge.openInTikTokMi(getContext(), originalUrl);
                dismiss();
            });
        });
    }

    private void startPlayback() {
        loadingView.setVisibility(View.GONE);
        coverView.setVisibility(View.GONE);
        authorView.setText("@" + videoAuthor);
        titleView.setText(!TextUtils.isEmpty(videoTitle) ? videoTitle : MiogramLocale.get("Відео TikTok MI", "Видео TikTok MI", "TikTok MI Video"));

        if (cachedVideoFile == null || !cachedVideoFile.exists() || !isSurfaceReady) {
            return;
        }

        try {
            if (mediaPlayer != null) {
                mediaPlayer.release();
            }
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDisplay(surfaceView.getHolder());
            mediaPlayer.setDataSource(cachedVideoFile.getAbsolutePath());
            mediaPlayer.setLooping(true);
            mediaPlayer.prepareAsync();
            mediaPlayer.setOnPreparedListener(mp -> {
                mp.start();
                isPlaying = true;
            });
        } catch (Throwable t) {
            FileLog.e("AmegramTikTokPlayer: playback error", t);
        }
    }

    private void saveToSavedMessages() {
        if (cachedVideoFile == null || !cachedVideoFile.exists()) {
            Toast.makeText(getContext(), MiogramLocale.get("Відео ще завантажується...", "Видео еще загружается...", "Video still loading..."), Toast.LENGTH_SHORT).show();
            return;
        }

        int currentAccount = UserConfig.selectedAccount;
        long clientUserId = UserConfig.getInstance(currentAccount).getClientUserId();

        SendMessagesHelper.SendingMediaInfo info = new SendMessagesHelper.SendingMediaInfo();
        info.path = cachedVideoFile.getAbsolutePath();
        info.isVideo = true;
        info.caption = (!TextUtils.isEmpty(videoAuthor) ? "@" + videoAuthor + " • " : "") + videoTitle + "\n" + originalUrl;

        java.util.ArrayList<SendMessagesHelper.SendingMediaInfo> list = new java.util.ArrayList<>();
        list.add(info);

        SendMessagesHelper.prepareSendingMedia(
                org.telegram.messenger.AccountInstance.getInstance(currentAccount),
                list,
                clientUserId, // Saved Messages
                null, null, null, null, false, true, null, true, 0, 0, 0, false, null, null, 0L, false, 0L, 0L, null
        );

        MiogramHaptic.success();
        Toast.makeText(getContext(), MiogramLocale.get("Збережено в Збережені повідомлення!", "Сохранено в Избранное!", "Saved to Saved Messages!"), Toast.LENGTH_SHORT).show();
    }

    private void downloadVideo() {
        if (cachedVideoFile == null || !cachedVideoFile.exists()) {
            Toast.makeText(getContext(), MiogramLocale.get("Відео ще завантажується...", "Видео еще загружается...", "Video still loading..."), Toast.LENGTH_SHORT).show();
            return;
        }

        Utilities.globalQueue.postRunnable(() -> {
            try {
                File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File ttDir = new File(downloads, "TikTok MI");
                if (!ttDir.exists()) ttDir.mkdirs();

                String cleanName = "tiktok_" + videoAuthor + "_" + System.currentTimeMillis() + ".mp4";
                File dest = new File(ttDir, cleanName);

                try (InputStream in = new java.io.FileInputStream(cachedVideoFile);
                     FileOutputStream out = new FileOutputStream(dest)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = in.read(buf)) > 0) {
                        out.write(buf, 0, len);
                    }
                }

                AndroidUtilities.runOnUIThread(() -> {
                    MiogramHaptic.success();
                    Toast.makeText(getContext(), MiogramLocale.get("Відео збережено в Завантаження/TikTok MI!", "Видео сохранено в Загрузки/TikTok MI!", "Saved to Downloads/TikTok MI!"), Toast.LENGTH_LONG).show();
                });
            } catch (Throwable t) {
                FileLog.e(t);
            }
        });
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        isSurfaceReady = true;
        if (cachedVideoFile != null && cachedVideoFile.exists() && (mediaPlayer == null || !isPlaying)) {
            startPlayback();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        isSurfaceReady = false;
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
            isPlaying = false;
        }
    }

    @Override
    public void dismiss() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
                mediaPlayer.release();
            } catch (Throwable ignore) {}
            mediaPlayer = null;
            isPlaying = false;
        }
        super.dismiss();
    }

    public static void sendToSavedMessages(String videoUrl, String videoTitle) {
        if (TextUtils.isEmpty(videoUrl)) return;
        int currentAccount = UserConfig.selectedAccount;
        long clientUserId = UserConfig.getInstance(currentAccount).getClientUserId();
        String text = (!TextUtils.isEmpty(videoTitle) ? videoTitle + "\n" : "") + videoUrl;
        SendMessagesHelper.getInstance(currentAccount).sendMessage(
                SendMessagesHelper.SendMessageParams.of(text, clientUserId)
        );
        MiogramHaptic.success();
        Toast.makeText(ApplicationLoader.applicationContext, MiogramLocale.get("Збережено в Збережені повідомлення!", "Сохранено в Избранное!", "Saved to Saved Messages!"), Toast.LENGTH_SHORT).show();
    }
}
