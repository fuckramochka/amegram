package app.miogram.bridge.privacy;

import android.text.TextUtils;

import org.telegram.messenger.FileLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;

import java.io.File;

/**
 * Ghost keeper for view-once / self-destruct media.
 *
 * When the user opens a TTL photo/video, the client reports the view to the
 * server (interlocutor sees "viewed", media is deleted for them) — but the
 * file is already in our local cache. If both anti-delete toggles are ON and
 * a Cloud Vault is linked, the file is silently copied into the vault, so it
 * survives locally and in the cloud.
 */
public class MiogramGhostKeeper {

    /** True only when saving deleted messages AND their media are both enabled. */
    public static boolean isGhostKeepEnabled() {
        try {
            return xyz.nextalone.nagram.NaConfig.INSTANCE.getEnableSaveDeletedMessages().Bool()
                    && xyz.nextalone.nagram.NaConfig.INSTANCE.getMessageSavingSaveMedia().Bool();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Called from the view-report path (UI thread). Resolves the already cached
     * file and hands it to the vault uploader (which runs in background).
     * Never throws, never blocks.
     */
    public static void keepViewedTtlMedia(int account, long dialogId, int mid) {
        if (mid == 0) return;
        try {
            if (!isGhostKeepEnabled()) return;
            if (app.miogram.bridge.cloudvault.MiogramCloudVaultEngine.getVaultChatId(account) == 0) return;

            MessagesController mc = MessagesController.getInstance(account);
            if (mc == null) return;
            MessageObject mo;
            try {
                mo = mc.dialogMessagesByIds.get(mid);
            } catch (Throwable ignore) {
                return;
            }
            if (mo == null || mo.messageOwner == null || mo.messageOwner.media == null) return;

            File cached;
            try {
                cached = FileLoader.getInstance(account).getPathToMessage(mo.messageOwner);
            } catch (Throwable ignore) {
                return;
            }
            if (cached == null || !cached.exists() || cached.length() == 0) return;

            String name = "ghost_media_" + mid;
            String mime = "application/octet-stream";
            try {
                if (mo.getDocument() != null) {
                    String docName = FileLoader.getDocumentFileName(mo.getDocument());
                    if (!TextUtils.isEmpty(docName)) name = "ghost_" + docName;
                    if (!TextUtils.isEmpty(mo.getMimeType())) mime = mo.getMimeType();
                } else if (mo.isVideo() || mo.isRoundVideo()) {
                    name = "ghost_video_" + mid + ".mp4";
                    mime = "video/mp4";
                } else if (mo.isPhoto()) {
                    name = "ghost_photo_" + mid + ".jpg";
                    mime = "image/jpeg";
                } else if (mo.isVoice() || mo.isMusic()) {
                    name = "ghost_audio_" + mid + ".ogg";
                    mime = "audio/ogg";
                } else {
                    return;
                }
            } catch (Throwable ignore) {
                return;
            }

            app.miogram.bridge.cloudvault.MiogramCloudVaultEngine.uploadFileToVault(
                    account, cached, name, mime, null, null, null);
        } catch (Throwable ignore) {}
    }
}
