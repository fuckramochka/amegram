package app.miogram.bridge.music;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessageChatArguments;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.EmptyTextProgressView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.telegram.ui.ActionBar.AlertDialog;
import app.miogram.bridge.MiogramLocale;
import app.miogram.bridge.cloudvault.MiogramCloudVaultEngine;
import app.miogram.bridge.customui.MiogramHaptic;

public class MiogramMusicSearchActivity extends BaseFragment {

    private enum SourceFilter {
        ALL("Усі джерела", "Все источники", "All Sources"),
        TELEGRAM("Telegram Cloud", "Telegram Cloud", "Telegram Cloud"),
        YOUTUBE_MUSIC("YouTube Music", "YouTube Music", "YouTube Music"),
        DEEZER("Deezer", "Deezer", "Deezer"),
        ITUNES("iTunes Store", "iTunes Store", "iTunes Store"),
        JAMENDO("Jamendo HQ", "Jamendo HQ", "Jamendo HQ");

        public final String uk, ru, en;
        SourceFilter(String uk, String ru, String en) {
            this.uk = uk; this.ru = ru; this.en = en;
        }

        public String getTitle() {
            return MiogramLocale.get(uk, ru, en);
        }
    }

    private long targetDialogId;
    private long targetTopicId;
    private ChatActivity targetChatActivity;

    public MiogramMusicSearchActivity() {
        this(0, 0L, null);
    }

    public MiogramMusicSearchActivity(long dialogId, ChatActivity chatActivity) {
        this(dialogId, chatActivity != null ? chatActivity.getTopicId() : 0L, chatActivity);
    }

    public MiogramMusicSearchActivity(long dialogId, long topicId, ChatActivity chatActivity) {
        super();
        this.targetDialogId = dialogId;
        this.targetTopicId = topicId;
        this.targetChatActivity = chatActivity;
        if (this.targetTopicId == 0 && chatActivity != null) {
            this.targetTopicId = chatActivity.getTopicId();
        }
    }

    public static MiogramMusicSearchActivity createForChat(long dialogId, ChatActivity chatActivity) {
        return new MiogramMusicSearchActivity(dialogId, chatActivity);
    }

    public static MiogramMusicSearchActivity createForChat(long dialogId, long topicId, ChatActivity chatActivity) {
        return new MiogramMusicSearchActivity(dialogId, topicId, chatActivity);
    }

    private EditText searchEditText;
    private RecyclerListView listView;
    private TrackAdapter adapter;
    private EmptyTextProgressView emptyView;
    private SourceFilter currentFilter = SourceFilter.ALL;

    private final List<MiogramMusicTrack> allTracks = new ArrayList<>();
    private final List<MiogramMusicTrack> displayTracks = new ArrayList<>();
    private Runnable searchRunnable;
    private boolean isSearching = false;
    private int searchGeneration = 0;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(targetDialogId != 0
                ? MiogramLocale.get("Надіслати музику", "Отправить музыку", "Send Music")
                : MiogramLocale.get("Пошук музики", "Поиск музыки", "Music Search"));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout contentView = new FrameLayout(context);
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        fragmentView = contentView;

        LinearLayout contentLayout = new LinearLayout(context);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentView.addView(contentLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // 1. Search Bar Card
        FrameLayout searchContainer = new FrameLayout(context);
        searchContainer.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        int padH = AndroidUtilities.dp(14);
        int padV = AndroidUtilities.dp(8);
        searchContainer.setPadding(padH, padV, padH, padV);

        FrameLayout searchInner = new FrameLayout(context);
        GradientDrawable searchBg = new GradientDrawable();
        searchBg.setColor(Theme.getColor(Theme.key_chat_messagePanelBackground));
        searchBg.setCornerRadius(AndroidUtilities.dp(12));
        searchInner.setBackground(searchBg);

        ImageView searchIcon = new ImageView(context);
        searchIcon.setImageResource(R.drawable.outline_header_search);
        searchIcon.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        searchInner.addView(searchIcon, LayoutHelper.createFrame(22, 22, Gravity.CENTER_VERTICAL | Gravity.LEFT, 12, 0, 0, 0));

        searchEditText = new EditText(context);
        searchEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        searchEditText.setHint(MiogramLocale.get("Введіть назву треку або артиста...", "Введите трек или артиста...", "Search track or artist..."));
        searchEditText.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        searchEditText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        searchEditText.setBackground(null);
        searchEditText.setSingleLine(true);
        searchEditText.setPadding(AndroidUtilities.dp(44), 0, AndroidUtilities.dp(36), 0);
        searchInner.addView(searchEditText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 46, Gravity.CENTER_VERTICAL));

        ImageView clearBtn = new ImageView(context);
        clearBtn.setImageResource(R.drawable.ic_close_white);
        clearBtn.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        clearBtn.setVisibility(View.GONE);
        clearBtn.setOnClickListener(v -> searchEditText.setText(""));
        searchInner.addView(clearBtn, LayoutHelper.createFrame(24, 24, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 0, 10, 0));

        searchContainer.addView(searchInner, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        contentLayout.addView(searchContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // 2. Source Filter Chips Row
        HorizontalScrollView chipScrollView = new HorizontalScrollView(context);
        chipScrollView.setHorizontalScrollBarEnabled(false);
        chipScrollView.setClipToPadding(false);
        chipScrollView.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(4), AndroidUtilities.dp(14), AndroidUtilities.dp(8));

        LinearLayout chipLayout = new LinearLayout(context);
        chipLayout.setOrientation(LinearLayout.HORIZONTAL);
        chipScrollView.addView(chipLayout, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        for (SourceFilter sf : SourceFilter.values()) {
            TextView chip = new TextView(context);
            chip.setText(sf.getTitle());
            chip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            chip.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(6), AndroidUtilities.dp(14), AndroidUtilities.dp(6));
            chip.setGravity(Gravity.CENTER);
            updateChipStyle(chip, sf == currentFilter);

            chip.setOnClickListener(v -> {
                MiogramHaptic.tap(v);
                currentFilter = sf;
                for (int i = 0; i < chipLayout.getChildCount(); i++) {
                    View c = chipLayout.getChildAt(i);
                    if (c instanceof TextView) {
                        updateChipStyle((TextView) c, c == chip);
                    }
                }
                filterAndDisplay();
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = AndroidUtilities.dp(8);
            chipLayout.addView(chip, lp);
        }

        contentLayout.addView(chipScrollView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // 3. Results Recycler List
        FrameLayout listContainer = new FrameLayout(context);

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        adapter = new TrackAdapter();
        listView.setAdapter(adapter);
        listContainer.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        emptyView = new EmptyTextProgressView(context);
        emptyView.setShowAtCenter(true);
        emptyView.setText(MiogramLocale.get("Шукайте музику за назвою чи виконавцем", "Ищите музыку по названию или исполнителю", "Search music by title or artist"));
        listContainer.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setEmptyView(emptyView);

        contentLayout.addView(listContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1.0f));

        // Search Input Listener
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                clearBtn.setVisibility(TextUtils.isEmpty(s) ? View.GONE : View.VISIBLE);
                if (searchRunnable != null) {
                    AndroidUtilities.cancelRunOnUIThread(searchRunnable);
                }
                final String q = s.toString().trim();
                if (q.isEmpty()) {
                    allTracks.clear();
                    displayTracks.clear();
                    adapter.notifyDataSetChanged();
                    emptyView.showTextView();
                    emptyView.setText(MiogramLocale.get("Шукайте музику за назвою чи виконавцем", "Ищите музыку по названию или исполнителю", "Search music by title or artist"));
                    return;
                }

                searchRunnable = () -> performSearch(q);
                AndroidUtilities.runOnUIThread(searchRunnable, 350);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        return fragmentView;
    }

    private void updateChipStyle(TextView chip, boolean selected) {
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(AndroidUtilities.dp(16));
        if (selected) {
            gd.setColor(Theme.getColor(Theme.key_chats_actionBackground));
            chip.setTextColor(0xFFFFFFFF);
        } else {
            gd.setColor(Theme.getColor(Theme.key_chat_messagePanelBackground));
            chip.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        }
        chip.setBackground(gd);
    }

    private void performSearch(String query) {
        final int gen = ++searchGeneration;
        isSearching = true;
        if (emptyView != null) emptyView.showProgress();

        MiogramMusicSearchEngine.searchAll(query, currentAccount, new MiogramMusicSearchEngine.SearchCallback() {
            @Override
            public void onResults(List<MiogramMusicTrack> tracks, boolean isFinal) {
                if (gen != searchGeneration || fragmentView == null) return;
                if (isFinal) isSearching = false;
                allTracks.clear();
                if (tracks != null) {
                    allTracks.addAll(tracks);
                }
                filterAndDisplay();
            }

            @Override
            public void onError(String error) {
                if (gen != searchGeneration || fragmentView == null) return;
                isSearching = false;
                if (allTracks.isEmpty() && emptyView != null) {
                    emptyView.showTextView();
                    String msg = error != null ? error : "network";
                    emptyView.setText(MiogramLocale.get("Помилка пошуку: ", "Ошибка поиска: ", "Search error: ") + msg);
                }
            }
        });
    }

    private void filterAndDisplay() {
        displayTracks.clear();
        for (MiogramMusicTrack t : allTracks) {
            if (t == null) continue;
            if (currentFilter == SourceFilter.ALL) {
                displayTracks.add(t);
            } else if (currentFilter == SourceFilter.TELEGRAM && t.source == MiogramMusicTrack.Source.TELEGRAM) {
                displayTracks.add(t);
            } else if (currentFilter == SourceFilter.YOUTUBE_MUSIC && t.source == MiogramMusicTrack.Source.YOUTUBE_MUSIC) {
                displayTracks.add(t);
            } else if (currentFilter == SourceFilter.DEEZER && t.source == MiogramMusicTrack.Source.DEEZER) {
                displayTracks.add(t);
            } else if (currentFilter == SourceFilter.ITUNES && t.source == MiogramMusicTrack.Source.ITUNES) {
                displayTracks.add(t);
            } else if (currentFilter == SourceFilter.JAMENDO && t.source == MiogramMusicTrack.Source.JAMENDO) {
                displayTracks.add(t);
            }
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        if (emptyView != null && !isSearching) {
            if (displayTracks.isEmpty()) {
                emptyView.showTextView();
                if (allTracks.isEmpty()) {
                    emptyView.setText(MiogramLocale.get("Нічого не знайдено", "Ничего не найдено", "No tracks found"));
                } else {
                    emptyView.setText(MiogramLocale.get("У цьому джерелі нічого нема — зміни фільтр", "В этом источнике ничего нет — смени фильтр", "Nothing in this source — change filter"));
                }
            }
        }
    }

    private static android.media.MediaPlayer activePlayer = null;
    private static MiogramMusicTrack currentlyPlayingTrack = null;

    private void safeToast(String msg) {
        try {
            Context ctx = getParentActivity() != null ? getParentActivity() : getContext();
            if (ctx == null || fragmentView == null) return;
            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show();
        } catch (Throwable ignore) {}
    }

    private void playStreamTrack(MiogramMusicTrack track) {
        if (track == null || track.streamUrl == null || track.streamUrl.isEmpty()) return;
        // YouTube watch URLs are not direct streams — don't feed them to MediaPlayer.
        if (track.streamUrl.contains("youtube.com/watch") || track.streamUrl.contains("youtu.be/")) {
            safeToast(MiogramLocale.get("Це YouTube-посилання — відкриваю в браузері", "Это YouTube-ссылка — открываю в браузере", "YouTube link — opening in browser"));
            try {
                org.telegram.messenger.browser.Browser.openUrl(getParentActivity(), track.streamUrl);
            } catch (Throwable ignore) {}
            return;
        }

        Context ctx = getParentActivity() != null ? getParentActivity() : getContext();

        if (currentlyPlayingTrack == track && activePlayer != null) {
            try {
                boolean playing = false;
                try {
                    playing = activePlayer.isPlaying();
                } catch (IllegalStateException ise) {
                    playing = false;
                }
                if (playing) {
                    activePlayer.pause();
                } else {
                    activePlayer.start();
                }
            } catch (Throwable ignore) {}
            if (adapter != null) adapter.notifyDataSetChanged();
            return;
        }

        stopActivePlayer();
        try {
            MediaController.getInstance().cleanupPlayer(true, true);
        } catch (Throwable ignore) {}

        try {
            currentlyPlayingTrack = track;
            if (adapter != null) adapter.notifyDataSetChanged();

            activePlayer = new android.media.MediaPlayer();
            activePlayer.setDataSource(track.streamUrl);
            activePlayer.setOnPreparedListener(mp -> {
                try {
                    mp.start();
                } catch (Throwable ignore) {}
                if (adapter != null) adapter.notifyDataSetChanged();
            });
            activePlayer.setOnCompletionListener(mp -> {
                stopActivePlayer();
                if (adapter != null) adapter.notifyDataSetChanged();
            });
            activePlayer.setOnErrorListener((mp, what, extra) -> {
                stopActivePlayer();
                if (adapter != null) adapter.notifyDataSetChanged();
                safeToast(MiogramLocale.get("Помилка відтворення потоку", "Ошибка воспроизведения потока", "Stream playback error"));
                return true;
            });
            try {
                activePlayer.setAudioAttributes(new android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build());
            } catch (Throwable ignore) {}
            activePlayer.prepareAsync();
            safeToast("▶ " + (track.getDisplayTitle() != null ? track.getDisplayTitle() : ""));
        } catch (Throwable t) {
            stopActivePlayer();
            if (adapter != null) adapter.notifyDataSetChanged();
            Toast.makeText(ctx, MiogramLocale.get("Помилка відтворення", "Ошибка воспроизведения", "Playback error") + (t.getMessage() != null ? ": " + t.getMessage() : ""), Toast.LENGTH_SHORT).show();
        }
    }

    private static void stopActivePlayer() {
        if (activePlayer != null) {
            try {
                if (activePlayer.isPlaying()) activePlayer.stop();
                activePlayer.release();
            } catch (Throwable ignore) {}
            activePlayer = null;
        }
        currentlyPlayingTrack = null;
    }

    @Override
    public void onFragmentDestroy() {
        stopActivePlayer();
        super.onFragmentDestroy();
    }

    private class TrackAdapter extends RecyclerListView.SelectionAdapter {

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public int getItemCount() {
            return displayTracks.size();
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new RecyclerListView.Holder(new TrackCell(parent.getContext()));
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (holder.itemView instanceof TrackCell) {
                ((TrackCell) holder.itemView).setTrack(displayTracks.get(position));
            }
        }
    }

    public void sendTrackToChat(MiogramMusicTrack track, ProgressBar loadingBar, ImageView sendButton) {
        if (track == null || targetDialogId == 0) return;

        MessageObject replyToTopMsg = null;
        if (targetChatActivity != null && targetChatActivity.getThreadMessage() != null) {
            replyToTopMsg = targetChatActivity.getThreadMessage();
        } else if (targetTopicId != 0) {
            TLRPC.Message syntheticMsg = new TLRPC.TL_message();
            syntheticMsg.id = (int) targetTopicId;
            syntheticMsg.dialog_id = targetDialogId;
            syntheticMsg.reply_to = new TLRPC.TL_messageReplyHeader();
            syntheticMsg.reply_to.reply_to_top_id = (int) targetTopicId;
            syntheticMsg.reply_to.reply_to_msg_id = (int) targetTopicId;
            syntheticMsg.reply_to.forum_topic = true;
            replyToTopMsg = new MessageObject(currentAccount, syntheticMsg, false, false);
        }
        SendMessageChatArguments chatArgs = targetChatActivity != null ? targetChatActivity.getMessageChatSendParams() : null;

        if (track.telegramMessage != null) {
            ArrayList<MessageObject> forwardList = new ArrayList<>();
            forwardList.add(track.telegramMessage);
            SendMessagesHelper.getInstance(currentAccount).sendMessage(
                    forwardList, targetDialogId, true, true, true, 0, replyToTopMsg, -1, 0L
            );
            Toast.makeText(getParentActivity() != null ? getParentActivity() : getContext(),
                    MiogramLocale.get("Трек надіслано в чат", "Трек отправлен в чат", "Track sent to chat"),
                    Toast.LENGTH_SHORT).show();
            finishFragment();
            return;
        }

        if (track.source == MiogramMusicTrack.Source.YOUTUBE_MUSIC) {
            String text = "🎵 " + track.getDisplayArtist() + " — " + track.getDisplayTitle() + "\n" + (track.streamUrl != null ? track.streamUrl : "");
            SendMessagesHelper.SendMessageParams params = SendMessagesHelper.SendMessageParams.of(
                    text, targetDialogId, null, replyToTopMsg, null, true, null, null, null, true, 0, 0, null, false
            );
            if (chatArgs != null) {
                params.sendMessageChatArguments = chatArgs;
            }
            SendMessagesHelper.getInstance(currentAccount).sendMessage(params);
            Toast.makeText(getParentActivity() != null ? getParentActivity() : getContext(),
                    MiogramLocale.get("Трек надіслано в чат", "Трек отправлен в чат", "Track sent to chat"),
                    Toast.LENGTH_SHORT).show();
            finishFragment();
            return;
        }

        if (track.isInstalled && track.localFile != null && track.localFile.exists()) {
            SendMessagesHelper.prepareSendingDocument(
                    getAccountInstance(),
                    track.localFile.getAbsolutePath(),
                    track.localFile.getAbsolutePath(),
                    null,
                    null,
                    "audio/mpeg",
                    targetDialogId,
                    null, replyToTopMsg, null, null, null,
                    true, 0, null, chatArgs, false
            );
            Toast.makeText(getParentActivity() != null ? getParentActivity() : getContext(),
                    MiogramLocale.get("Трек надіслано в чат", "Трек отправлен в чат", "Track sent to chat"),
                    Toast.LENGTH_SHORT).show();
            finishFragment();
            return;
        }

        if (loadingBar != null) loadingBar.setVisibility(View.VISIBLE);
        if (sendButton != null) sendButton.setVisibility(View.GONE);

        final MessageObject finalReplyToTopMsg = replyToTopMsg;
        final SendMessageChatArguments finalChatArgs = chatArgs;

        MiogramMusicSearchEngine.fastInstallTrack(getParentActivity() != null ? getParentActivity() : getContext(), track, currentAccount, new MiogramMusicSearchEngine.InstallCallback() {
            @Override
            public void onProgress(float progress) {
            }

            @Override
            public void onSuccess(File localFile) {
                if (loadingBar != null) loadingBar.setVisibility(View.GONE);
                if (sendButton != null) sendButton.setVisibility(View.VISIBLE);

                SendMessagesHelper.prepareSendingDocument(
                        getAccountInstance(),
                        localFile.getAbsolutePath(),
                        localFile.getAbsolutePath(),
                        null,
                        null,
                        "audio/mpeg",
                        targetDialogId,
                        null, finalReplyToTopMsg, null, null, null,
                        true, 0, null, finalChatArgs, false
                );
                Toast.makeText(getParentActivity() != null ? getParentActivity() : getContext(),
                        MiogramLocale.get("Трек надіслано в чат", "Трек отправлен в чат", "Track sent to chat"),
                        Toast.LENGTH_SHORT).show();
                finishFragment();
            }

            @Override
            public void onError(String error) {
                if (loadingBar != null) loadingBar.setVisibility(View.GONE);
                if (sendButton != null) sendButton.setVisibility(View.VISIBLE);
                Toast.makeText(getParentActivity() != null ? getParentActivity() : getContext(),
                        MiogramLocale.get("Помилка завантаження", "Ошибка загрузки", "Download error") + (error != null ? ": " + error : ""),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showTrackOptionsMenu(MiogramMusicTrack track) {
        if (track == null || getParentActivity() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(track.getDisplayArtist() + " — " + track.getDisplayTitle());

        ArrayList<String> items = new ArrayList<>();
        ArrayList<Integer> actions = new ArrayList<>();

        items.add(MiogramLocale.get("Слухати", "Слушать", "Play"));
        actions.add(1);

        items.add(MiogramLocale.get("Завантажити трек", "Скачать трек", "Download track"));
        actions.add(2);

        items.add(MiogramLocale.get("Завантажити текст пісні (.lrc)", "Скачать текст песни (.lrc)", "Download lyrics (.lrc)"));
        actions.add(3);

        items.add(MiogramLocale.get("Копіювати назву", "Скопировать название", "Copy title"));
        actions.add(4);

        if (MiogramCloudVaultEngine.hasVault(currentAccount)) {
            items.add(MiogramLocale.get("Зберегти в Cloud Vault", "Сохранить в Cloud Vault", "Save to Cloud Vault"));
            actions.add(5);
        }

        builder.setItems(items.toArray(new CharSequence[0]), (d, which) -> {
            int action = actions.get(which);
            if (action == 1) {
                if (track.source == MiogramMusicTrack.Source.YOUTUBE_MUSIC && track.streamUrl != null) {
                    org.telegram.messenger.browser.Browser.openUrl(getParentActivity(), track.streamUrl);
                } else if (track.telegramMessage != null) {
                    stopActivePlayer();
                    MediaController.getInstance().playMessage(track.telegramMessage);
                    if (adapter != null) adapter.notifyDataSetChanged();
                } else if (track.streamUrl != null) {
                    playStreamTrack(track);
                }
            } else if (action == 2) {
                if (track.source == MiogramMusicTrack.Source.YOUTUBE_MUSIC) {
                    if (track.streamUrl != null) {
                        org.telegram.messenger.browser.Browser.openUrl(getParentActivity(), track.streamUrl);
                    }
                    return;
                }
                track.isDownloading = true;
                if (adapter != null) adapter.notifyDataSetChanged();
                MiogramMusicSearchEngine.fastInstallTrack(getParentActivity(), track, currentAccount, new MiogramMusicSearchEngine.InstallCallback() {
                    @Override
                    public void onProgress(float progress) {
                        track.downloadProgress = progress;
                    }

                    @Override
                    public void onSuccess(File localFile) {
                        track.isDownloading = false;
                        track.isInstalled = true;
                        track.localFile = localFile;
                        if (adapter != null) adapter.notifyDataSetChanged();
                        Toast.makeText(getParentActivity(), MiogramLocale.get("Трек збережено!", "Трек сохранён!", "Track saved!"), Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(String error) {
                        track.isDownloading = false;
                        if (adapter != null) adapter.notifyDataSetChanged();
                        Toast.makeText(getParentActivity(), MiogramLocale.get("Помилка завантаження", "Ошибка загрузки", "Download error"), Toast.LENGTH_SHORT).show();
                    }
                });
            } else if (action == 3) {
                Toast.makeText(getParentActivity(), MiogramLocale.get("Пошук тексту пісні...", "Поиск текста песни...", "Fetching lyrics..."), Toast.LENGTH_SHORT).show();
                MiogramMusicSearchEngine.fetchLyrics(track.getDisplayArtist(), track.getDisplayTitle(), track.durationSeconds, new MiogramMusicSearchEngine.LyricsCallback() {
                    @Override
                    public void onLyrics(String syncedLrc, String plainLyrics) {
                        String content = (syncedLrc != null && !syncedLrc.isEmpty()) ? syncedLrc : plainLyrics;
                        if (content != null && !content.isEmpty()) {
                            track.lyrics = content;
                            File targetDir = MiogramMusicSearchEngine.getTargetMusicDir(getParentActivity());
                            String cleanName = track.getDisplayArtist() + " - " + track.getDisplayTitle() + ".lrc";
                            cleanName = cleanName.replaceAll("[\\\\/:*?\"<>|]", "_");
                            File lrcFile = new File(targetDir, cleanName);
                            try {
                                FileOutputStream fos = new FileOutputStream(lrcFile);
                                fos.write(content.getBytes(StandardCharsets.UTF_8));
                                fos.flush();
                                fos.close();
                                Toast.makeText(getParentActivity(), MiogramLocale.get("Текст пісні збережено у файлі .lrc!", "Текст песни сохранён в .lrc!", "Lyrics saved as .lrc!"), Toast.LENGTH_SHORT).show();
                            } catch (Throwable t) {
                                Toast.makeText(getParentActivity(), MiogramLocale.get("Помилка збереження", "Ошибка сохранения", "Save error"), Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(getParentActivity(), MiogramLocale.get("Текст пісні не знайдено", "Текст песни не найден", "Lyrics not found"), Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onError(String error) {
                        Toast.makeText(getParentActivity(), MiogramLocale.get("Текст пісні не знайдено", "Текст песни не найден", "Lyrics not found"), Toast.LENGTH_SHORT).show();
                    }
                });
            } else if (action == 4) {
                AndroidUtilities.addToClipboard(track.getDisplayArtist() + " - " + track.getDisplayTitle());
                Toast.makeText(getParentActivity(), MiogramLocale.get("Скопійовано в буфер", "Скопировано в буфер", "Copied to clipboard"), Toast.LENGTH_SHORT).show();
            } else if (action == 5) {
                if (track.localFile != null && track.localFile.exists()) {
                    MiogramCloudVaultEngine.uploadFileToVault(currentAccount, track.localFile, track.localFile.getName(), "audio/mpeg", null, null, null);
                    Toast.makeText(getParentActivity(), MiogramLocale.get("Збережено в Cloud Vault", "Сохранено в Cloud Vault", "Saved to Cloud Vault"), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getParentActivity(), MiogramLocale.get("Спочатку завантажте трек", "Сначала скачайте трек", "Download the track first"), Toast.LENGTH_SHORT).show();
                }
            }
        });
        showDialog(builder.create());
    }

    private class TrackCell extends FrameLayout {

        private final BackupImageView coverView;
        private final TextView titleView;
        private final TextView artistView;
        private final TextView badgeView;
        private final TextView durationView;
        private final ImageView playBtn;
        private final ImageView downloadBtn;
        private final ProgressBar progressBar;
        private final ImageView sendBtn;
        private final ProgressBar sendProgressBar;

        private MiogramMusicTrack currentTrack;

        public TrackCell(Context context) {
            super(context);
            setBackground(Theme.getSelectorDrawable(false));
            setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(8), AndroidUtilities.dp(14), AndroidUtilities.dp(8));

            // Album Cover
            coverView = new BackupImageView(context);
            coverView.setRoundRadius(AndroidUtilities.dp(8));
            coverView.setImageResource(R.drawable.nocover_big);
            addView(coverView, LayoutHelper.createFrame(52, 52, Gravity.CENTER_VERTICAL | Gravity.LEFT));

            // Middle info column
            LinearLayout infoCol = new LinearLayout(context);
            infoCol.setOrientation(LinearLayout.VERTICAL);

            titleView = new TextView(context);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            titleView.setSingleLine(true);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            infoCol.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            LinearLayout subRow = new LinearLayout(context);
            subRow.setOrientation(LinearLayout.HORIZONTAL);
            subRow.setGravity(Gravity.CENTER_VERTICAL);

            badgeView = new TextView(context);
            badgeView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10);
            badgeView.setTextColor(0xFFFFFFFF);
            badgeView.setPadding(AndroidUtilities.dp(5), AndroidUtilities.dp(1), AndroidUtilities.dp(5), AndroidUtilities.dp(1));
            subRow.addView(badgeView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 6, 0));

            artistView = new TextView(context);
            artistView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            artistView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            artistView.setSingleLine(true);
            artistView.setEllipsize(TextUtils.TruncateAt.END);
            subRow.addView(artistView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));

            durationView = new TextView(context);
            durationView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            durationView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            subRow.addView(durationView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 6, 0, 0, 0));

            infoCol.addView(subRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 3, 0, 0));

            int rightPadding = targetDialogId != 0 ? 132 : 92;
            addView(infoCol, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 64, 0, rightPadding, 0));

            // Right action buttons
            LinearLayout actionsRow = new LinearLayout(context);
            actionsRow.setOrientation(LinearLayout.HORIZONTAL);
            actionsRow.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);

            // Play Button
            playBtn = new ImageView(context);
            playBtn.setImageResource(R.drawable.ic_play);
            playBtn.setColorFilter(Theme.getColor(Theme.key_chats_actionBackground));
            playBtn.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 1));
            playBtn.setContentDescription(MiogramLocale.get("Слухати", "Слушать", "Preview"));
            playBtn.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(6), AndroidUtilities.dp(6), AndroidUtilities.dp(6));
            playBtn.setOnClickListener(v -> {
                MiogramHaptic.tap(v);
                if (currentTrack != null) {
                    if (currentTrack.source == MiogramMusicTrack.Source.YOUTUBE_MUSIC && currentTrack.streamUrl != null) {
                        org.telegram.messenger.browser.Browser.openUrl(getParentActivity() != null ? getParentActivity() : getContext(), currentTrack.streamUrl);
                    } else if (currentTrack.telegramMessage != null) {
                        stopActivePlayer();
                        MediaController.getInstance().playMessage(currentTrack.telegramMessage);
                        if (adapter != null) adapter.notifyDataSetChanged();
                    } else if (currentTrack.streamUrl != null) {
                        playStreamTrack(currentTrack);
                    }
                }
            });
            actionsRow.addView(playBtn, LayoutHelper.createLinear(36, 36, 0, 0, 4, 0));

            // Fast Install / Download Button
            FrameLayout downloadContainer = new FrameLayout(context);

            downloadBtn = new ImageView(context);
            downloadBtn.setImageResource(R.drawable.msg_download);
            downloadBtn.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            downloadBtn.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 1));
            downloadBtn.setContentDescription(MiogramLocale.get("Завантажити трек", "Скачать трек", "Download track"));
            downloadBtn.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(6), AndroidUtilities.dp(6), AndroidUtilities.dp(6));

            progressBar = new ProgressBar(context);
            progressBar.setVisibility(View.GONE);

            downloadContainer.addView(downloadBtn, LayoutHelper.createFrame(36, 36, Gravity.CENTER));
            downloadContainer.addView(progressBar, LayoutHelper.createFrame(30, 30, Gravity.CENTER));

            downloadBtn.setOnClickListener(v -> {
                MiogramHaptic.tap(v);
                if (currentTrack != null) {
                    if (currentTrack.source == MiogramMusicTrack.Source.YOUTUBE_MUSIC) {
                        if (currentTrack.streamUrl != null) {
                            org.telegram.messenger.browser.Browser.openUrl(context, currentTrack.streamUrl);
                            Toast.makeText(context, MiogramLocale.get("Відкриття у YouTube Music...", "Открытие в YouTube Music...", "Opening in YouTube Music..."), Toast.LENGTH_SHORT).show();
                        }
                        return;
                    }
                    if (!currentTrack.isDownloading && !currentTrack.isInstalled) {
                        final MiogramMusicTrack targetTrack = currentTrack;
                        targetTrack.isDownloading = true;
                        downloadBtn.setVisibility(View.GONE);
                        progressBar.setVisibility(View.VISIBLE);

                        MiogramMusicSearchEngine.fastInstallTrack(context, targetTrack, currentAccount, new MiogramMusicSearchEngine.InstallCallback() {
                            @Override
                            public void onProgress(float progress) {
                                targetTrack.downloadProgress = progress;
                            }

                            @Override
                            public void onSuccess(File localFile) {
                                targetTrack.isDownloading = false;
                                targetTrack.isInstalled = true;
                                targetTrack.localFile = localFile;
                                if (adapter != null) adapter.notifyDataSetChanged();
                                Toast.makeText(context, MiogramLocale.get("Трек збережено у 'Збережені' та папку Музика!", "Трек сохранён в 'Избранное' и папку Музыка!", "Saved to Cloud & Device Music!"), Toast.LENGTH_LONG).show();
                            }

                            @Override
                            public void onError(String error) {
                                targetTrack.isDownloading = false;
                                if (adapter != null) adapter.notifyDataSetChanged();
                                Toast.makeText(context, MiogramLocale.get("Помилка завантаження", "Ошибка загрузки", "Download error") + (error != null ? ": " + error : ""), Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }
            });

            actionsRow.addView(downloadContainer, LayoutHelper.createLinear(36, 36, 0, 0, targetDialogId != 0 ? 4 : 0, 0));

            // Send to Chat Button (if opened from chat)
            if (targetDialogId != 0) {
                FrameLayout sendContainer = new FrameLayout(context);

                sendBtn = new ImageView(context);
                sendBtn.setImageResource(R.drawable.attach_send);
                sendBtn.setColorFilter(Theme.getColor(Theme.key_chats_actionBackground));
                sendBtn.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 1));
                sendBtn.setContentDescription(MiogramLocale.get("Надіслати в чат", "Отправить в чат", "Send to chat"));
                sendBtn.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(6), AndroidUtilities.dp(6), AndroidUtilities.dp(6));

                sendProgressBar = new ProgressBar(context);
                sendProgressBar.setVisibility(View.GONE);

                sendContainer.addView(sendBtn, LayoutHelper.createFrame(36, 36, Gravity.CENTER));
                sendContainer.addView(sendProgressBar, LayoutHelper.createFrame(30, 30, Gravity.CENTER));

                sendBtn.setOnClickListener(v -> {
                    MiogramHaptic.tap(v);
                    sendTrackToChat(currentTrack, sendProgressBar, sendBtn);
                });

                actionsRow.addView(sendContainer, LayoutHelper.createLinear(36, 36));

                setOnClickListener(v -> {
                    MiogramHaptic.tap(v);
                    sendTrackToChat(currentTrack, sendProgressBar, sendBtn);
                });
            } else {
                sendBtn = null;
                sendProgressBar = null;

                setOnClickListener(v -> {
                    MiogramHaptic.tap(v);
                    playBtn.performClick();
                });
            }

            setOnLongClickListener(v -> {
                MiogramHaptic.tap(v);
                showTrackOptionsMenu(currentTrack);
                return true;
            });

            addView(actionsRow, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.RIGHT));
        }

        public void setTrack(MiogramMusicTrack track) {
            this.currentTrack = track;
            titleView.setText(track.getDisplayTitle());
            artistView.setText(track.getDisplayArtist());
            durationView.setText(track.getFormattedDuration());

            boolean isPlaying = (currentlyPlayingTrack == track && activePlayer != null && activePlayer.isPlaying());
            playBtn.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
            playBtn.setContentDescription(isPlaying
                ? MiogramLocale.get("Пауза", "Пауза", "Pause")
                : MiogramLocale.get("Слухати", "Слушать", "Preview"));

            if (track.source != null) {
                badgeView.setText(track.source.label);
                GradientDrawable gd = new GradientDrawable();
                gd.setCornerRadius(AndroidUtilities.dp(4));
                int color = track.source.badgeColor;
                int bgColor = (color & 0x00FFFFFF) | 0x22000000;
                gd.setColor(bgColor);
                badgeView.setBackground(gd);
                badgeView.setTextColor(color);
                badgeView.setVisibility(View.VISIBLE);
            } else {
                badgeView.setVisibility(View.GONE);
            }

            if (track.coverUrl != null && !track.coverUrl.isEmpty()) {
                coverView.setImage(track.coverUrl, null, getResources().getDrawable(R.drawable.nocover_big));
            } else {
                coverView.setImageResource(R.drawable.nocover_big);
            }

            if (track.isDownloading) {
                downloadBtn.setVisibility(View.GONE);
                progressBar.setVisibility(View.VISIBLE);
            } else if (track.isInstalled) {
                progressBar.setVisibility(View.GONE);
                downloadBtn.setVisibility(View.VISIBLE);
                downloadBtn.setImageResource(R.drawable.msg_check);
                downloadBtn.setColorFilter(0xFF34C759);
            } else {
                progressBar.setVisibility(View.GONE);
                downloadBtn.setVisibility(View.VISIBLE);
                downloadBtn.setImageResource(R.drawable.msg_download);
                downloadBtn.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            }
        }
    }
}
