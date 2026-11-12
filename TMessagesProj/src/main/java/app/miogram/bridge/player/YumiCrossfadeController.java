package app.miogram.bridge.player;

import android.os.Handler;
import android.os.Looper;

import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;

/**
 * Handles smooth audio crossfade and gapless transitions between tracks.
 * Ramps volume down when nearing track end, and ramps volume up when track starts.
 */
public final class YumiCrossfadeController implements NotificationCenter.NotificationCenterDelegate {

    private static YumiCrossfadeController instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean fadingIn;
    private boolean fadingOut;
    private float currentVolume = 1.0f;
    private long lastSaveTime = 0;

    public static synchronized YumiCrossfadeController getInstance() {
        if (instance == null) {
            instance = new YumiCrossfadeController();
        }
        return instance;
    }

    private YumiCrossfadeController() {
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            NotificationCenter.getInstance(i).addObserver(this, NotificationCenter.messagePlayingDidStart);
            NotificationCenter.getInstance(i).addObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
            NotificationCenter.getInstance(i).addObserver(this, NotificationCenter.messagePlayingDidReset);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        int fadeSec = MiogramPlayerPrefs.getCrossfadeDurationSec();
        long fadeMs = fadeSec * 1000L;

        if (id == NotificationCenter.messagePlayingDidStart) {
            MessageObject mo = (MessageObject) args[0];
            if (mo != null && mo.isMusic()) {
                if (fadeSec > 0) {
                    startFadeIn(fadeMs);
                }
                YumiPlayerState.saveState(mo, 0, MediaController.getInstance().getPlaylist());
            }
        } else if (id == NotificationCenter.messagePlayingProgressDidChanged) {
            MessageObject current = MediaController.getInstance().getPlayingMessageObject();
            if (current != null && current.isMusic()) {
                int totalDuration = current.audioPlayerDuration > 0 ? current.audioPlayerDuration : (int) Math.round(current.getDuration());
                float progress = current.audioProgress;
                long currentMs = (long) (progress * totalDuration * 1000L);

                long now = System.currentTimeMillis();
                if (now - lastSaveTime > 2500) {
                    lastSaveTime = now;
                    YumiPlayerState.saveState(current, currentMs, MediaController.getInstance().getPlaylist());
                }

                if (fadeSec > 0 && totalDuration > fadeSec + 2) {
                    long totalMs = totalDuration * 1000L;
                    long remainingMs = totalMs - currentMs;

                    if (remainingMs <= fadeMs && remainingMs > 100 && !fadingOut) {
                        float vol = Math.max(0.05f, Math.min(1.0f, (float) remainingMs / (float) fadeMs));
                        applyVolume(vol);
                    }
                }
            }
        } else if (id == NotificationCenter.messagePlayingDidReset) {
            fadingOut = false;
            fadingIn = false;
            applyVolume(1.0f);
        }
    }

    private void startFadeIn(long fadeMs) {
        fadingIn = true;
        fadingOut = false;
        final long start = System.currentTimeMillis();
        handler.removeCallbacksAndMessages(null);

        handler.post(new Runnable() {
            @Override
            public void run() {
                if (!fadingIn) return;
                long elapsed = System.currentTimeMillis() - start;
                float progress = Math.min(1.0f, (float) elapsed / (float) fadeMs);
                applyVolume(progress);
                if (progress < 1.0f) {
                    handler.postDelayed(this, 30);
                } else {
                    fadingIn = false;
                    applyVolume(1.0f);
                }
            }
        });
    }

    private void applyVolume(float vol) {
        currentVolume = vol;
        try {
            MediaController.getInstance().setVolume(vol);
        } catch (Throwable ignore) {
        }
    }
}
