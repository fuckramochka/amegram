package app.exteraless.plugins;

import android.webkit.MimeTypeMap;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.SendMessageChatArguments;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

public final class PluginMediaServices {
    private PluginMediaServices() {
    }

    private static File readableFile(String path) {
        File file = new File(path);
        if (!file.isFile() || !file.canRead()) {
            throw new IllegalArgumentException("Media file is not readable: " + path);
        }
        return file;
    }

    private static String mimeType(String path) {
        int dot = path.lastIndexOf('.');
        String extension = dot < 0 ? "" : path.substring(dot + 1).toLowerCase(Locale.ROOT);
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        return mime == null ? "application/octet-stream" : mime;
    }

    public static TLRPC.TL_document prepareDocument(String path) {
        File file = readableFile(path);
        TLRPC.TL_document document = new TLRPC.TL_document();
        document.file_reference = new byte[0];
        document.date = (int) (System.currentTimeMillis() / 1000L);
        document.size = file.length();
        document.mime_type = mimeType(path);
        TLRPC.TL_documentAttributeFilename filename = new TLRPC.TL_documentAttributeFilename();
        filename.file_name = file.getName();
        document.attributes.add(filename);
        return document;
    }

    public static TLRPC.TL_photo generatePhotoSizes(int account, String path, boolean highQuality, TLRPC.TL_photo photo) {
        readableFile(path);
        TLRPC.TL_photo res = null;
        try {
            res = SendMessagesHelper.getInstance(account).generatePhotoSizes(photo, path, null, highQuality);
        } catch (Exception e) {
            org.telegram.messenger.FileLog.e(e);
        }
        if (res == null) {
            res = photo != null ? photo : new TLRPC.TL_photo();
            res.date = (int) (System.currentTimeMillis() / 1000L);
            res.file_reference = new byte[0];
            if (res.sizes == null) {
                res.sizes = new ArrayList<>();
            }
        }
        return res;
    }

    public static TLRPC.TL_photo generatePhotoSizes(int account, String path, boolean highQuality) {
        return generatePhotoSizes(account, path, highQuality, null);
    }

    public static TLRPC.TL_photo generatePhotoSizes(String path, boolean highQuality) {
        return generatePhotoSizes(org.telegram.messenger.UserConfig.selectedAccount, path, highQuality, null);
    }

    public static TLRPC.TL_photo generatePhotoSizes(String path) {
        return generatePhotoSizes(path, false);
    }

    public static TLRPC.TL_documentAttributeVideo addVideoAttributes(TLRPC.TL_document document, String path, int width, int height, int duration, boolean supportsStreaming) {
        TLRPC.TL_documentAttributeVideo attributeVideo = new TLRPC.TL_documentAttributeVideo();
        attributeVideo.supports_streaming = supportsStreaming;
        if (path != null && !path.isEmpty()) {
            readableFile(path);
            try {
                SendMessagesHelper.fillVideoAttribute(path, attributeVideo, null);
            } catch (Exception ignored) {
            }
        }
        if (width > 0) {
            attributeVideo.w = width;
        }
        if (height > 0) {
            attributeVideo.h = height;
        }
        if (duration > 0) {
            attributeVideo.duration = duration;
        }
        if (document != null) {
            if (document.attributes == null) {
                document.attributes = new ArrayList<>();
            }
            for (int i = document.attributes.size() - 1; i >= 0; i--) {
                if (document.attributes.get(i) instanceof TLRPC.TL_documentAttributeVideo) {
                    document.attributes.remove(i);
                }
            }
            document.attributes.add(attributeVideo);
        }
        return attributeVideo;
    }

    public static TLRPC.TL_documentAttributeVideo addVideoAttributes(TLRPC.TL_document document, String path) {
        return addVideoAttributes(document, path, 0, 0, 0, true);
    }



    public static void editMedia(int account, MessageObject message, String path, String caption,
                                 ArrayList<TLRPC.MessageEntity> entities, boolean spoiler) {
        readableFile(path);
        if (message == null || message.messageOwner == null || message.currentAccount != account) {
            throw new IllegalArgumentException("The message must belong to the selected account");
        }
        String mime = mimeType(path);
        SendMessagesHelper.SendingMediaInfo media = new SendMessagesHelper.SendingMediaInfo();
        media.path = path;
        media.isVideo = mime.startsWith("video/");
        media.caption = caption == null ? message.messageOwner.message : caption;
        media.entities = caption == null ? message.messageOwner.entities : entities;
        media.hasMediaSpoilers = spoiler;
        message.editingMessage = media.caption;
        message.editingMessageEntities = media.entities;
        ArrayList<SendMessagesHelper.SendingMediaInfo> items = new ArrayList<>();
        items.add(media);
        boolean forceDocument = !mime.startsWith("image/") && !media.isVideo;
        SendMessagesHelper.prepareSendingMedia(AccountInstance.getInstance(account), items, message.getDialogId(),
                null, null, null, null, forceDocument, false, message, true,
                message.scheduled ? message.messageOwner.date : 0, 0, 0, false,
                null, SendMessageChatArguments.EMPTY, 0, false, 0, 0, null);
    }
}
