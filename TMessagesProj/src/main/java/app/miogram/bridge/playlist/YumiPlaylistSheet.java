package app.miogram.bridge.playlist;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;

import java.io.File;
import java.util.Collections;

import app.miogram.bridge.MiogramLocale;
import app.miogram.bridge.customui.MiogramHaptic;

/**
 * Modern Yumi Playlist Sheet for viewing, editing, reordering, and playing .yumi playlists.
 * Automatically shown when opening a .yumi file from chats, downloads, or storage.
 */
public class YumiPlaylistSheet extends BottomSheet {

    private final Activity activity;
    private final YumiPlaylist playlist;
    private final boolean isLocalFile;
    private TracksAdapter adapter;
    private TextView subtitleView;
    private TextView titleView;

    public static boolean openIfPlaylist(Activity activity, File file, Theme.ResourcesProvider rp) {
        if (activity == null || file == null || !file.exists()) return false;
        String name = file.getName().toLowerCase();
        if (name.endsWith(".yumi") || name.endsWith(".yumiplay") || file.length() < 1024 * 512) {
            YumiPlaylist pl = YumiPlaylist.fromFile(file);
            if (pl != null && pl.tracks != null) {
                new YumiPlaylistSheet(activity, pl, rp, false).show();
                return true;
            }
        }
        return false;
    }

    public static void showPlaylist(Activity activity, YumiPlaylist playlist, Theme.ResourcesProvider rp) {
        if (activity == null || playlist == null) return;
        new YumiPlaylistSheet(activity, playlist, rp, true).show();
    }

    public YumiPlaylistSheet(Activity activity, YumiPlaylist playlist, Theme.ResourcesProvider resourcesProvider, boolean isLocal) {
        super(activity, false, resourcesProvider);
        this.activity = activity;
        this.playlist = playlist;
        this.isLocalFile = isLocal;

        setApplyTopPadding(false);
        setApplyBottomPadding(false);

        int bgColor = getThemedColor(Theme.key_dialogBackground);
        if (bgColor == 0) bgColor = 0xFF14161F;
        fixNavigationBar(bgColor);

        int accent = getThemedColor(Theme.key_windowBackgroundWhiteBlueHeader);
        if (accent == 0) accent = 0xFF5B8DEF;

        FrameLayout root = new FrameLayout(activity) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int h = (int) (MeasureSpec.getSize(heightMeasureSpec) * 0.82f);
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
            }
        };
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadii(new float[]{
                AndroidUtilities.dp(24), AndroidUtilities.dp(24),
                AndroidUtilities.dp(24), AndroidUtilities.dp(24),
                0, 0, 0, 0
        });
        root.setBackground(bg);
        containerView = root;

        // Drag Handle
        View handle = new View(activity);
        GradientDrawable handleBg = new GradientDrawable();
        handleBg.setColor(0x44FFFFFF);
        handleBg.setCornerRadius(AndroidUtilities.dp(3));
        handle.setBackground(handleBg);
        root.addView(handle, LayoutHelper.createFrame(36, 4, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 10, 0, 0));

        // Header
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(24), AndroidUtilities.dp(20), AndroidUtilities.dp(8));

        titleView = new TextView(activity);
        titleView.setText(playlist.title != null ? playlist.title : "Yumi Playlist");
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(0xFFFFFFFF);
        header.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        subtitleView = new TextView(activity);
        updateSubtitle();
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitleView.setTextColor(0x99FFFFFF);
        subtitleView.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(12));
        header.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // Action Buttons Row: [ Play All ] [ Shuffle ] [ Save / Share ]
        LinearLayout actionsRow = new LinearLayout(activity);
        actionsRow.setOrientation(LinearLayout.HORIZONTAL);
        actionsRow.setGravity(Gravity.CENTER_VERTICAL);

        // Play Button
        TextView playBtn = createPillButton(activity, accent, 0xFFFFFFFF, R.drawable.ic_play,
                MiogramLocale.get("Слухати", "Слушать", "Play All"), v -> {
                    MiogramHaptic.click(v);
                    dismiss();
                    YumiPlaylistManager.playPlaylist(playlist, 0, false);
                });
        actionsRow.addView(playBtn, LayoutHelper.createLinear(0, 42, 1.2f, 0, 0, 8, 0));

        // Shuffle Button
        TextView shuffleBtn = createPillButton(activity, 0x22FFFFFF, 0xFFFFFFFF, R.drawable.player_new_shuffle,
                MiogramLocale.get("Перемішати", "Перемешать", "Shuffle"), v -> {
                    MiogramHaptic.click(v);
                    dismiss();
                    YumiPlaylistManager.playPlaylist(playlist, 0, true);
                });
        actionsRow.addView(shuffleBtn, LayoutHelper.createLinear(0, 42, 1.0f, 0, 0, 8, 0));

        // Save or Share Button
        TextView actionBtn = createPillButton(activity, 0x22FFFFFF, 0xFFFFFFFF, R.drawable.msg_shareout,
                isLocalFile ? MiogramLocale.get("Поділитися", "Поделиться", "Share") : MiogramLocale.get("Зберегти", "Сохранить", "Save"), v -> {
                    MiogramHaptic.click(v);
                    if (isLocalFile) {
                        YumiPlaylistManager.sharePlaylistFile(activity, playlist);
                    } else {
                        YumiPlaylistManager.savePlaylist(playlist);
                        BulletinFactory.of(container, resourcesProvider)
                                .createSimpleBulletin(R.drawable.msg_saved, MiogramLocale.get("Збережено в «Мої плейлисти»", "Сохранено в «Мои плейлисты»", "Saved to My Playlists"))
                                .show();
                    }
                });
        actionsRow.addView(actionBtn, LayoutHelper.createLinear(0, 42, 1.0f));

        header.addView(actionsRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        root.addView(header, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        // Tracks RecyclerView
        RecyclerView list = new RecyclerView(activity);
        list.setLayoutManager(new LinearLayoutManager(activity));
        adapter = new TracksAdapter();
        list.setAdapter(adapter);

        // Touch helper for Drag & Drop reordering
        ItemTouchHelper touchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                int from = viewHolder.getAdapterPosition();
                int to = target.getAdapterPosition();
                if (from < 0 || to < 0 || from >= playlist.tracks.size() || to >= playlist.tracks.size()) return false;
                Collections.swap(playlist.tracks, from, to);
                adapter.notifyItemMoved(from, to);
                YumiPlaylistManager.savePlaylist(playlist);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return true;
            }
        });
        touchHelper.attachToRecyclerView(list);

        FrameLayout.LayoutParams listLp = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP);
        listLp.topMargin = AndroidUtilities.dp(140);
        listLp.bottomMargin = AndroidUtilities.dp(16);
        root.addView(list, listLp);
    }

    private void updateSubtitle() {
        int count = playlist.tracks.size();
        String dur = playlist.getFormattedDuration();
        String author = playlist.author != null ? playlist.author : "Yumigram";
        subtitleView.setText(author + " • " + count + " " + LocaleController.formatPluralString("OEPlayerTracks", count) + " • " + dur);
    }

    private TextView createPillButton(Context context, int bgCol, int textCol, int iconRes, String text, View.OnClickListener click) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextColor(textCol);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        tv.setTypeface(AndroidUtilities.bold());
        tv.setGravity(Gravity.CENTER);
        tv.setCompoundDrawablePadding(AndroidUtilities.dp(6));
        tv.setPadding(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10), 0);
        tv.setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0);
        if (tv.getCompoundDrawables()[0] != null) {
            tv.getCompoundDrawables()[0].setColorFilter(new PorterDuffColorFilter(textCol, PorterDuff.Mode.SRC_IN));
        }
        GradientDrawable d = new GradientDrawable();
        d.setColor(bgCol);
        d.setCornerRadius(AndroidUtilities.dp(21));
        tv.setBackground(d);
        tv.setOnClickListener(click);
        return tv;
    }

    private class TracksAdapter extends RecyclerView.Adapter<TrackViewHolder> {

        @NonNull
        @Override
        public TrackViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(parent.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(10), AndroidUtilities.dp(20), AndroidUtilities.dp(10));

            TextView num = new TextView(parent.getContext());
            num.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            num.setTextColor(0x66FFFFFF);
            num.setGravity(Gravity.CENTER);
            row.addView(num, LayoutHelper.createLinear(28, LayoutHelper.WRAP_CONTENT, 0, 0, 10, 0));

            LinearLayout info = new LinearLayout(parent.getContext());
            info.setOrientation(LinearLayout.VERTICAL);

            TextView title = new TextView(parent.getContext());
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            title.setTypeface(AndroidUtilities.bold());
            title.setTextColor(0xFFFFFFFF);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            info.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            TextView artist = new TextView(parent.getContext());
            artist.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            artist.setTextColor(0x99FFFFFF);
            artist.setSingleLine(true);
            artist.setEllipsize(TextUtils.TruncateAt.END);
            info.addView(artist, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

            row.addView(info, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));

            TextView dur = new TextView(parent.getContext());
            dur.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            dur.setTextColor(0x88FFFFFF);
            row.addView(dur, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 8, 0, 8, 0));

            ImageView deleteBtn = new ImageView(parent.getContext());
            deleteBtn.setImageResource(R.drawable.msg_delete);
            deleteBtn.setColorFilter(new PorterDuffColorFilter(0x66FFFFFF, PorterDuff.Mode.SRC_IN));
            deleteBtn.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4));
            row.addView(deleteBtn, LayoutHelper.createLinear(28, 28));

            return new TrackViewHolder(row, num, title, artist, dur, deleteBtn);
        }

        @Override
        public void onBindViewHolder(@NonNull TrackViewHolder holder, int position) {
            YumiPlaylist.Track track = playlist.tracks.get(position);
            holder.number.setText(String.valueOf(position + 1));
            holder.title.setText(track.title != null ? track.title : "Unknown Title");
            holder.artist.setText(track.artist != null ? track.artist : "Unknown Artist");
            int m = track.duration / 60;
            int s = track.duration % 60;
            holder.duration.setText(String.format("%d:%02d", m, s));

            holder.itemView.setOnClickListener(v -> {
                MiogramHaptic.click(v);
                dismiss();
                YumiPlaylistManager.playPlaylist(playlist, position, false);
            });

            holder.deleteBtn.setOnClickListener(v -> {
                MiogramHaptic.click(v);
                int pos = holder.getAdapterPosition();
                if (pos >= 0 && pos < playlist.tracks.size()) {
                    YumiPlaylistManager.removeTrack(playlist, pos);
                    notifyItemRemoved(pos);
                    notifyItemRangeChanged(pos, playlist.tracks.size() - pos);
                    updateSubtitle();
                }
            });
        }

        @Override
        public int getItemCount() {
            return playlist.tracks.size();
        }
    }

    private static class TrackViewHolder extends RecyclerView.ViewHolder {
        final TextView number;
        final TextView title;
        final TextView artist;
        final TextView duration;
        final ImageView deleteBtn;

        TrackViewHolder(View root, TextView number, TextView title, TextView artist, TextView duration, ImageView deleteBtn) {
            super(root);
            this.number = number;
            this.title = title;
            this.artist = artist;
            this.duration = duration;
            this.deleteBtn = deleteBtn;
        }
    }
}
