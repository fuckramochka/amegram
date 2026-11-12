package app.miogram.bridge.playlist;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.List;

import app.miogram.bridge.MiogramLocale;
import app.miogram.bridge.customui.MiogramHaptic;

/**
 * BottomSheet to collect and add songs ONE BY ONE into .yumi playlists.
 * Allows picking an existing playlist or creating a brand new .yumi playlist on the spot.
 */
public class YumiAddToPlaylistSheet extends BottomSheet {

    private final MessageObject targetTrack;
    private final List<YumiPlaylist> playlists = new ArrayList<>();
    private PlaylistsAdapter adapter;

    public static void show(Context context, MessageObject track, Theme.ResourcesProvider rp) {
        if (context == null || track == null || !track.isMusic()) return;
        new YumiAddToPlaylistSheet(context, track, rp).show();
    }

    public YumiAddToPlaylistSheet(Context context, MessageObject track, Theme.ResourcesProvider resourcesProvider) {
        super(context, false, resourcesProvider);
        this.targetTrack = track;

        setApplyTopPadding(false);
        setApplyBottomPadding(false);

        int bgColor = getThemedColor(Theme.key_dialogBackground);
        if (bgColor == 0) bgColor = 0xFF14161F;
        fixNavigationBar(bgColor);

        int accent = getThemedColor(Theme.key_windowBackgroundWhiteBlueHeader);
        if (accent == 0) accent = 0xFF5B8DEF;

        FrameLayout root = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int h = (int) (MeasureSpec.getSize(heightMeasureSpec) * 0.72f);
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

        // Drag handle
        View handle = new View(context);
        GradientDrawable handleBg = new GradientDrawable();
        handleBg.setColor(0x44FFFFFF);
        handleBg.setCornerRadius(AndroidUtilities.dp(3));
        handle.setBackground(handleBg);
        root.addView(handle, LayoutHelper.createFrame(36, 4, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 10, 0, 0));

        // Header section
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(22), AndroidUtilities.dp(20), AndroidUtilities.dp(8));

        TextView title = new TextView(context);
        title.setText(MiogramLocale.get("Додати до плейлиста", "Добавить в плейлист", "Add to Playlist"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(0xFFFFFFFF);
        header.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView trackInfo = new TextView(context);
        trackInfo.setText(track.getMusicAuthor() + " — " + track.getMusicTitle());
        trackInfo.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        trackInfo.setTextColor(0x99FFFFFF);
        trackInfo.setSingleLine(true);
        trackInfo.setEllipsize(TextUtils.TruncateAt.END);
        trackInfo.setPadding(0, AndroidUtilities.dp(3), 0, AndroidUtilities.dp(10));
        header.addView(trackInfo, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // "Create New Playlist" card
        LinearLayout newPlaylistCard = new LinearLayout(context);
        newPlaylistCard.setOrientation(LinearLayout.HORIZONTAL);
        newPlaylistCard.setGravity(Gravity.CENTER_VERTICAL);
        newPlaylistCard.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
        GradientDrawable newCardBg = new GradientDrawable();
        newCardBg.setColor(0x22FFFFFF);
        newCardBg.setCornerRadius(AndroidUtilities.dp(16));
        newCardBg.setStroke(AndroidUtilities.dp(1), 0x33FFFFFF);
        newPlaylistCard.setBackground(newCardBg);

        ImageView addIcon = new ImageView(context);
        addIcon.setImageResource(R.drawable.msg_add);
        addIcon.setColorFilter(new PorterDuffColorFilter(accent, PorterDuff.Mode.SRC_IN));
        newPlaylistCard.addView(addIcon, LayoutHelper.createLinear(24, 24, 0, 0, 12, 0));

        TextView newText = new TextView(context);
        newText.setText(MiogramLocale.get("Створити новий .yumi плейлист", "Создать новый .yumi плейлист", "Create new .yumi playlist"));
        newText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        newText.setTypeface(AndroidUtilities.bold());
        newText.setTextColor(0xFFFFFFFF);
        newPlaylistCard.addView(newText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        newPlaylistCard.setOnClickListener(v -> {
            MiogramHaptic.click(v);
            showCreatePlaylistDialog(context);
        });
        header.addView(newPlaylistCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 4));

        root.addView(header, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        // Load existing playlists
        loadPlaylists();

        RecyclerView list = new RecyclerView(context);
        list.setLayoutManager(new LinearLayoutManager(context));
        adapter = new PlaylistsAdapter();
        list.setAdapter(adapter);

        FrameLayout.LayoutParams listLp = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP);
        listLp.topMargin = AndroidUtilities.dp(148);
        listLp.bottomMargin = AndroidUtilities.dp(16);
        root.addView(list, listLp);
    }

    private void loadPlaylists() {
        playlists.clear();
        playlists.addAll(YumiPlaylistManager.getAllPlaylists());
    }

    private void showCreatePlaylistDialog(Context context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle(MiogramLocale.get("Новий плейлист", "Новый плейлист", "New Playlist"));

        final EditText input = new EditText(context);
        input.setHint(MiogramLocale.get("Назва плейлиста", "Название плейлиста", "Playlist Title"));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setTextColor(0xFFFFFFFF);
        input.setHintTextColor(0x77FFFFFF);
        input.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        input.setSingleLine(true);
        input.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(14), AndroidUtilities.dp(20), AndroidUtilities.dp(14));

        FrameLayout frame = new FrameLayout(context);
        frame.addView(input, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 16, 8, 16, 8));
        builder.setView(frame);

        builder.setPositiveButton(MiogramLocale.get("Створити", "Создать", "Create"), (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (TextUtils.isEmpty(name)) {
                name = MiogramLocale.get("Мій плейлист", "Мой плейлист", "My Playlist");
            }
            YumiPlaylist pl = new YumiPlaylist(name, "Yumigram");
            pl.addTrack(targetTrack);
            YumiPlaylistManager.savePlaylist(pl);

            dismiss();
            BulletinFactory.of(container, resourcesProvider)
                    .createSimpleBulletin(R.drawable.msg_saved, MiogramLocale.get("Трек додано до «" + pl.title + "»", "Трек добавлен в «" + pl.title + "»", "Track added to \"" + pl.title + "\""))
                    .show();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.show();
        input.requestFocus();
    }

    private class PlaylistsAdapter extends RecyclerView.Adapter<PlaylistViewHolder> {

        @NonNull
        @Override
        public PlaylistViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(parent.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(12), AndroidUtilities.dp(20), AndroidUtilities.dp(12));

            ImageView folderIcon = new ImageView(parent.getContext());
            folderIcon.setImageResource(R.drawable.msg_list);
            folderIcon.setColorFilter(new PorterDuffColorFilter(0x88FFFFFF, PorterDuff.Mode.SRC_IN));
            row.addView(folderIcon, LayoutHelper.createLinear(28, 28, 0, 0, 14, 0));

            LinearLayout info = new LinearLayout(parent.getContext());
            info.setOrientation(LinearLayout.VERTICAL);

            TextView title = new TextView(parent.getContext());
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            title.setTypeface(AndroidUtilities.bold());
            title.setTextColor(0xFFFFFFFF);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            info.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            TextView subtitle = new TextView(parent.getContext());
            subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            subtitle.setTextColor(0x99FFFFFF);
            info.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

            row.addView(info, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));

            ImageView checkIcon = new ImageView(parent.getContext());
            row.addView(checkIcon, LayoutHelper.createLinear(24, 24));

            return new PlaylistViewHolder(row, title, subtitle, checkIcon);
        }

        @Override
        public void onBindViewHolder(@NonNull PlaylistViewHolder holder, int position) {
            YumiPlaylist pl = playlists.get(position);
            holder.title.setText(pl.title != null ? pl.title : "Playlist");
            int count = pl.tracks.size();
            holder.subtitle.setText(count + " " + LocaleController.formatPluralString("OEPlayerTracks", count) + " • " + pl.getFormattedDuration());

            boolean alreadyIn = pl.contains(targetTrack);
            if (alreadyIn) {
                holder.checkIcon.setImageResource(R.drawable.msg_check);
                holder.checkIcon.setColorFilter(new PorterDuffColorFilter(0xFF34C759, PorterDuff.Mode.SRC_IN));
                holder.subtitle.setText(holder.subtitle.getText() + " • " + MiogramLocale.get("Вже додано", "Уже добавлен", "Already added"));
            } else {
                holder.checkIcon.setImageResource(R.drawable.msg_add);
                holder.checkIcon.setColorFilter(new PorterDuffColorFilter(0x66FFFFFF, PorterDuff.Mode.SRC_IN));
            }

            holder.itemView.setOnClickListener(v -> {
                MiogramHaptic.click(v);
                if (alreadyIn) {
                    BulletinFactory.of(container, resourcesProvider)
                            .createSimpleBulletin(R.drawable.msg_info, MiogramLocale.get("Трек вже є у цьому плейлисті", "Трек уже есть в этом плейлисте", "Track is already in this playlist"))
                            .show();
                    return;
                }
                pl.addTrack(targetTrack);
                YumiPlaylistManager.savePlaylist(pl);
                dismiss();
                BulletinFactory.of(container, resourcesProvider)
                        .createSimpleBulletin(R.drawable.msg_saved, MiogramLocale.get("Додано до «" + pl.title + "»", "Добавлено в «" + pl.title + "»", "Added to \"" + pl.title + "\""))
                        .show();
            });
        }

        @Override
        public int getItemCount() {
            return playlists.size();
        }
    }

    private static class PlaylistViewHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView subtitle;
        final ImageView checkIcon;

        PlaylistViewHolder(View root, TextView title, TextView subtitle, ImageView checkIcon) {
            super(root);
            this.title = title;
            this.subtitle = subtitle;
            this.checkIcon = checkIcon;
        }
    }
}
