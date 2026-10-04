package app.amegram.core.net;

import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.tgnet.tl.TL_stories;

/**
 * Ghost enforcement, written from scratch for the core. Protocol behavior
 * matches the proven legacy filter 1:1 (block typing, block reads with fake
 * response, force offline, read-after-send, offline-after-send, per-chat
 * exclusions), but state lives in Amegram flags + {@link GhostExclusions} —
 * zero Neko/Ayu imports.
 */
public final class GhostFilter {

    private static final int OFFLINE_DELAY_MS = 1000;

    /** One-shot allow for explicit "mark as read" actions (menu button). */
    private static volatile boolean allowReadOnce;

    private GhostFilter() {
    }

    public static void allowReadOnce() {
        allowReadOnce = true;
    }

    /** Hub/UI helper: is read-hiding actually active right now? */
    public static boolean isReadHidden() {
        return ghostOn() && hideRead();
    }

    private static boolean consumeReadOnce() {
        boolean v = allowReadOnce;
        allowReadOnce = false;
        return v;
    }

    public static final class Result {
        public final boolean blocked;
        public final RequestDelegate callback;

        private Result(boolean blocked, RequestDelegate callback) {
            this.blocked = blocked;
            this.callback = callback;
        }

        public static Result blocked(RequestDelegate original) {
            return new Result(true, original);
        }

        public static Result proceed(RequestDelegate effective) {
            return new Result(false, effective);
        }
    }

    private static boolean ghostOn() {
        try {
            return app.amegram.module.AmegramConfig.getBool("ghost_enabled", false);
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean hideRead() {
        try {
            return app.amegram.module.AmegramConfig.getBool("ghost_hide_read", true);
        } catch (Throwable t) {
            return true;
        }
    }

    private static boolean hideOnline() {
        try {
            return app.amegram.module.AmegramConfig.getBool("ghost_hide_online", true);
        } catch (Throwable t) {
            return true;
        }
    }

    private static boolean hideTyping() {
        try {
            return app.amegram.module.AmegramConfig.getBool("ghost_hide_typing", true);
        } catch (Throwable t) {
            return true;
        }
    }

    public static Result intercept(TLObject object, RequestDelegate onCompleteOrig) {
        if (!ghostOn()) {
            return Result.proceed(onCompleteOrig);
        }
        Long dialogId = extractDialogId(object);
        boolean readExcluded = dialogId != null && GhostExclusions.getReadExclusion(dialogId);
        boolean typingExcluded = dialogId != null && GhostExclusions.getTypingExclusion(dialogId);

        boolean sendTyping = !hideTyping();
        boolean sendRead = !hideRead();
        boolean sendOnline = !hideOnline();

        if (!sendTyping && !typingExcluded
                && (object instanceof TLRPC.TL_messages_setTyping
                || object instanceof TLRPC.TL_messages_setEncryptedTyping)) {
            FileLog.d("Ghost: blocking typing.");
            return Result.blocked(onCompleteOrig);
        }
        if (!sendRead && isReadMessageRequest(object)) {
            if (!consumeReadOnce() && !readExcluded) {
                FileLog.d("Ghost: blocking read, fake response.");
                sendFakeReadResponse(onCompleteOrig);
                return Result.blocked(onCompleteOrig);
            }
        }
        if (!sendRead && isReadStoriesRequest(object) && !readExcluded) {
            FileLog.d("Ghost: blocking story read.");
            return Result.blocked(onCompleteOrig);
        }
        if (!sendOnline && object instanceof TL_account.updateStatus updateStatus) {
            FileLog.d("Ghost: forcing offline.");
            updateStatus.offline = true;
        }

        handleReadAfterSend(object, sendRead);
        RequestDelegate effective = handleOfflineAfterSend(object, onCompleteOrig);
        return Result.proceed(effective);
    }

    private static boolean markReadAfterSend() {
        try {
            return app.amegram.module.AmegramConfig.getBool("ghost_mark_read_after_send", true);
        } catch (Throwable t) {
            return true;
        }
    }

    private static boolean offlineAfterSend() {
        try {
            return app.amegram.module.AmegramConfig.getBool("ghost_offline_after_send", false);
        } catch (Throwable t) {
            return false;
        }
    }

    private static void handleReadAfterSend(TLObject object, boolean sendRead) {
        if (sendRead || !markReadAfterSend()) {
            return;
        }
        TLRPC.InputPeer peer = extractPeerFromSendObject(object);
        if (peer == null) {
            return;
        }
        Long dialogId = getDialogId(peer);
        if (dialogId != null && GhostExclusions.getReadExclusion(dialogId)) {
            return;
        }
        try {
            MessagesStorage storage = MessagesStorage.getInstance(UserConfig.selectedAccount);
            storage.getStorageQueue().postRunnable(() ->
                    storage.getDialogMaxMessageId(dialogId, maxId ->
                            markReadOnServer(maxId, peer, true)));
        } catch (Throwable ignore) {
        }
    }

    private static RequestDelegate handleOfflineAfterSend(TLObject object, RequestDelegate orig) {
        if (!offlineAfterSend() || !isMessageSendRequest(object)) {
            return orig;
        }
        TLRPC.InputPeer peer = extractPeerFromSendObject(object);
        if (peer != null) {
            Long dialogId = getDialogId(peer);
            if (dialogId != null && GhostExclusions.getTypingExclusion(dialogId)) {
                return orig;
            }
        }
        return (response, error) -> {
            if (orig != null) {
                Utilities.stageQueue.postRunnable(() -> orig.run(response, error));
            }
            Utilities.globalQueue.postRunnable(() -> performStatusRequest(true), OFFLINE_DELAY_MS);
        };
    }

    public static void performStatusRequest(boolean offline) {
        try {
            TL_account.updateStatus req = new TL_account.updateStatus();
            req.offline = offline;
            ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(req,
                    (response, error) -> FileLog.d("Ghost: status request done."));
        } catch (Throwable ignore) {
        }
    }

    public static void markReadOnServer(int messageId, TLRPC.InputPeer peer, boolean internal) {
        try {
            TLObject req;
            if (peer instanceof TLRPC.TL_inputPeerChannel) {
                TLRPC.TL_channels_readHistory request = new TLRPC.TL_channels_readHistory();
                request.channel = MessagesController.getInputChannel(peer);
                request.max_id = messageId;
                req = request;
            } else {
                TLRPC.TL_messages_readHistory request = new TLRPC.TL_messages_readHistory();
                request.peer = peer;
                request.max_id = messageId;
                req = request;
            }
            allowReadOnce = true;
            ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(req, (response, error) -> {
                if (error == null && response instanceof TLRPC.TL_messages_affectedMessages res) {
                    MessagesController.getInstance(UserConfig.selectedAccount)
                            .processNewDifferenceParams(-1, res.pts, -1, res.pts_count);
                }
                if (internal) {
                    FileLog.d("Ghost: read-after-send done.");
                }
            });
        } catch (Throwable ignore) {
        }
    }

    public static void markReadOnServer(MessageObject message, boolean internal) {
        try {
            int messageId = message.getId();
            long dialogId = message.getDialogId();
            int account = UserConfig.selectedAccount;
            MessagesController mc = MessagesController.getInstance(account);
            TLRPC.EncryptedChat encryptedChat =
                    mc.getEncryptedChat(DialogObject.getEncryptedChatId(dialogId));
            TLRPC.InputPeer inputPeer = mc.getInputPeer(message.messageOwner.peer_id);
            boolean readContents = message.isVoice() || message.isRoundVideo();
            TLObject req;
            if (inputPeer instanceof TLRPC.TL_inputPeerChannel) {
                if (readContents) {
                    TLRPC.TL_channels_readMessageContents request =
                            new TLRPC.TL_channels_readMessageContents();
                    request.channel = MessagesController.getInputChannel(inputPeer);
                    request.id.add(messageId);
                    req = request;
                } else {
                    TLRPC.TL_channels_readHistory request = new TLRPC.TL_channels_readHistory();
                    request.channel = MessagesController.getInputChannel(inputPeer);
                    request.max_id = messageId;
                    req = request;
                }
            } else if (encryptedChat != null) {
                TLRPC.TL_messages_readEncryptedHistory request =
                        new TLRPC.TL_messages_readEncryptedHistory();
                request.peer = new TLRPC.TL_inputEncryptedChat();
                request.peer.chat_id = encryptedChat.id;
                request.peer.access_hash = encryptedChat.access_hash;
                request.max_date = message.messageOwner.date != 0 ? message.messageOwner.date
                        : ConnectionsManager.getInstance(account).getCurrentTime();
                req = request;
            } else if (readContents) {
                TLRPC.TL_messages_readMessageContents request =
                        new TLRPC.TL_messages_readMessageContents();
                request.id.add(messageId);
                req = request;
            } else {
                TLRPC.TL_messages_readHistory request = new TLRPC.TL_messages_readHistory();
                request.peer = inputPeer;
                request.max_id = messageId;
                req = request;
            }
            allowReadOnce = true;
            ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
                if (error == null && response instanceof TLRPC.TL_messages_affectedMessages res) {
                    mc.processNewDifferenceParams(-1, res.pts, -1, res.pts_count);
                }
                if (internal) {
                    FileLog.d("Ghost: read-after-send done.");
                }
            });
        } catch (Throwable ignore) {
        }
    }

    private static Long extractDialogId(TLObject object) {
        if (object instanceof TLRPC.TL_messages_setTyping obj) {
            return getDialogId(obj.peer);
        } else if (object instanceof TLRPC.TL_messages_setEncryptedTyping obj) {
            return getDialogId(obj.peer);
        } else if (object instanceof TLRPC.TL_messages_readHistory obj) {
            return getDialogId(obj.peer);
        } else if (object instanceof TLRPC.TL_messages_readEncryptedHistory obj) {
            return getDialogId(obj.peer);
        } else if (object instanceof TLRPC.TL_messages_readDiscussion obj) {
            return getDialogId(obj.peer);
        } else if (object instanceof TLRPC.TL_messages_sendMessage obj) {
            return getDialogId(obj.peer);
        } else if (object instanceof TLRPC.TL_messages_sendMedia obj) {
            return getDialogId(obj.peer);
        } else if (object instanceof TLRPC.TL_messages_sendMultiMedia obj) {
            return getDialogId(obj.peer);
        } else if (object instanceof TL_stories.TL_stories_readStories obj) {
            return getDialogId(obj.peer);
        } else if (object instanceof TL_stories.TL_stories_incrementStoryViews obj) {
            return getDialogId(obj.peer);
        } else if (object instanceof TLRPC.TL_channels_readHistory obj) {
            return getDialogId(obj.channel);
        } else if (object instanceof TLRPC.TL_channels_readMessageContents obj) {
            return getDialogId(obj.channel);
        } else if (object instanceof TLRPC.TL_messages_getMessagesViews obj) {
            return getDialogId(obj.peer);
        }
        return null;
    }

    private static void sendFakeReadResponse(RequestDelegate onCompleteOrig) {
        TLRPC.TL_messages_affectedMessages fake = new TLRPC.TL_messages_affectedMessages();
        fake.pts = -1;
        fake.pts_count = 0;
        Utilities.stageQueue.postRunnable(() -> {
            try {
                if (onCompleteOrig != null) {
                    onCompleteOrig.run(fake, null);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    private static TLRPC.InputPeer extractPeerFromSendObject(TLObject object) {
        if (object instanceof TLRPC.TL_messages_sendMessage m) {
            return m.peer;
        } else if (object instanceof TLRPC.TL_messages_sendMedia m) {
            return m.peer;
        } else if (object instanceof TLRPC.TL_messages_sendMultiMedia m) {
            return m.peer;
        }
        return null;
    }

    private static boolean isReadMessageRequest(TLObject object) {
        return object instanceof TLRPC.TL_messages_readHistory
                || object instanceof TLRPC.TL_messages_readEncryptedHistory
                || object instanceof TLRPC.TL_messages_readDiscussion
                || object instanceof TLRPC.TL_messages_readMessageContents
                || object instanceof TLRPC.TL_channels_readMessageContents
                || object instanceof TLRPC.TL_channels_readHistory
                || object instanceof TLRPC.TL_messages_getMessagesViews views && views.increment;
    }

    private static boolean isReadStoriesRequest(TLObject object) {
        return object instanceof TL_stories.TL_stories_readStories
                || object instanceof TL_stories.TL_stories_incrementStoryViews;
    }

    private static boolean isMessageSendRequest(TLObject object) {
        return object instanceof TLRPC.TL_messages_sendMessage
                || object instanceof TLRPC.TL_messages_sendMedia
                || object instanceof TLRPC.TL_messages_sendMultiMedia;
    }

    public static Long getDialogId(TLRPC.InputPeer peer) {
        if (peer == null) {
            return null;
        }
        if (peer.chat_id != 0) {
            return -peer.chat_id;
        } else if (peer.channel_id != 0) {
            return -peer.channel_id;
        }
        return peer.user_id;
    }

    public static Long getDialogId(TLRPC.InputChannel peer) {
        return peer == null ? null : -peer.channel_id;
    }

    public static Long getDialogId(TLRPC.TL_inputEncryptedChat peer) {
        if (peer == null) {
            return null;
        }
        return (long) DialogObject.getEncryptedChatId(peer.chat_id);
    }
}
