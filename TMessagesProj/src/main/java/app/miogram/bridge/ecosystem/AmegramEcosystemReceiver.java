package app.miogram.bridge.ecosystem;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.Stories.recorder.StoryEntry;
import org.telegram.ui.Stories.recorder.StoryRecorder;

import java.io.File;

/**
 * BroadcastReceiver for Amegram Ecosystem events:
 * - Theme & Accent changes from TikTok MI
 * - 1-Tap Story posting
 * - ClipVault synchronization
 */
public class AmegramEcosystemReceiver extends BroadcastReceiver {

    public static final String ACTION_THEME_CHANGED = "app.amegram.ACTION_THEME_CHANGED";
    public static final String ACTION_POST_STORY = "app.amegram.POST_STORY";
    public static final String ACTION_CLIP_VAULT = "app.amegram.ACTION_CLIP_VAULT";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        String action = intent.getAction();

        switch (action) {
            case ACTION_THEME_CHANGED: {
                // Amegram: ignore incoming theme pushes unless the user explicitly enabled sync.
                if (!AmegramTikTokBridge.isThemeSyncEnabled()) break;
                int accent = intent.getIntExtra("accent", 0);
                boolean dark = intent.getBooleanExtra("dark", true);
                boolean amoled = intent.getBooleanExtra("amoled", false);

                if (accent != 0) {
                    AndroidUtilities.runOnUIThread(() -> applyEcosystemTheme(accent, dark, amoled));
                }
                break;
            }

            case ACTION_POST_STORY: {
                Uri uri = intent.getData();
                if (uri == null) {
                    uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                }
                String caption = intent.getStringExtra("caption");
                if (caption == null) {
                    caption = intent.getStringExtra(Intent.EXTRA_TEXT);
                }

                if (uri != null) {
                    final Uri finalUri = uri;
                    final String finalCaption = caption;
                    AndroidUtilities.runOnUIThread(() -> openStoryEditor(context, finalUri, finalCaption));
                }
                break;
            }

            case ACTION_CLIP_VAULT: {
                String url = intent.getStringExtra("url");
                String title = intent.getStringExtra("title");
                String author = intent.getStringExtra("author");
                boolean isVideo = intent.getBooleanExtra("isVideo", true);

                if (!TextUtils.isEmpty(url)) {
                    AmegramEcosystemProvider.lastClipVaultItem =
                            new AmegramEcosystemProvider.ClipVaultItem(url, title, author, isVideo, System.currentTimeMillis());

                    AndroidUtilities.runOnUIThread(() -> {
                        NotificationCenter.getGlobalInstance().postNotificationName(
                                NotificationCenter.fileLoaded, "clipvault_" + url, null);
                    });
                }
                break;
            }
        }
    }

    private static void applyEcosystemTheme(int accentColor, boolean dark, boolean amoled) {
        try {
            Theme.ThemeInfo themeInfo = dark ? Theme.getTheme(amoled ? "Night" : "Dark Blue") : Theme.getTheme("Day");
            if (themeInfo == null) {
                themeInfo = dark ? Theme.getCurrentNightTheme() : Theme.getCurrentTheme();
            }
            if (themeInfo == null) return;

            Theme.ThemeAccent accent = themeInfo.getAccent(true);
            if (accent != null) {
                accent.accentColor = accentColor;
                accent.myMessagesAccentColor = accentColor;
                themeInfo.setCurrentAccentId(accent.id);
            }

            Theme.applyTheme(themeInfo, true, dark);
            NotificationCenter.getGlobalInstance().postNotificationName(
                    NotificationCenter.needSetDayNightTheme, themeInfo, dark, null, -1);
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    private static void openStoryEditor(Context context, Uri uri, String caption) {
        try {
            LaunchActivity activity = LaunchActivity.instance;
            if (activity == null || activity.isFinishing()) {
                // Launch activity first
                Intent launchIntent = new Intent(context, LaunchActivity.class);
                launchIntent.setAction(ACTION_POST_STORY);
                launchIntent.setData(uri);
                launchIntent.putExtra(Intent.EXTRA_STREAM, uri);
                if (caption != null) launchIntent.putExtra("caption", caption);
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                context.startActivity(launchIntent);
                return;
            }

            String path = AndroidUtilities.getPath(uri);
            if (path == null) {
                path = uri.getPath();
            }
            if (path != null) {
                File file = new File(path);
                if (file.exists()) {
                    int currentAccount = UserConfig.selectedAccount;
                    StoryEntry entry = StoryEntry.fromVideoShoot(file, null, 15000);
                    if (caption != null && !caption.isEmpty()) {
                        entry.caption = new android.text.SpannableStringBuilder(caption);
                    }
                    StoryRecorder editor = StoryRecorder.getInstance(activity, currentAccount);
                    if (editor != null) {
                        editor.openEdit(null, entry, 0, false);
                    }
                }
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }
}
