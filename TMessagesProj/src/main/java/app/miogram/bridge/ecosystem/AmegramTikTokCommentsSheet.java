package app.miogram.bridge.ecosystem;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadialProgressView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import app.miogram.bridge.MiogramLocale;

/**
 * Slide-up Bottom Sheet for viewing TikTok video comments in Amegram.
 * Directly resolves comments via TikWM comment list API.
 */
public class AmegramTikTokCommentsSheet extends BottomSheet {

    private final String videoUrl;
    private final List<TikTokComment> comments = new ArrayList<>();
    private CommentAdapter adapter;
    private RadialProgressView progressView;
    private TextView emptyView;
    private TextView countView;

    public static class TikTokComment {
        public String id = "";
        public String text = "";
        public String authorName = "";
        public String authorHandle = "";
        public String authorAvatar = "";
        public long diggCount = 0;
        public long createTime = 0;
    }

    public static void show(Context context, String url) {
        if (context == null || TextUtils.isEmpty(url)) return;
        AmegramTikTokCommentsSheet sheet = new AmegramTikTokCommentsSheet(context, url);
        sheet.show();
    }

    public AmegramTikTokCommentsSheet(Context context, String url) {
        super(context, false);
        this.videoUrl = url;

        setApplyBottomPadding(false);
        setApplyTopPadding(false);
        fixNavigationBar(0xFF0F141C);

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(0xFF0F141C);

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(16));
        root.addView(layout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // Drag handle
        View handle = new View(context);
        GradientDrawable handleBg = new GradientDrawable();
        handleBg.setColor(0x44888888);
        handleBg.setCornerRadius(AndroidUtilities.dp(2.5f));
        handle.setBackground(handleBg);
        layout.addView(handle, LayoutHelper.createLinear(38, 5, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 12));

        // Header: Title + count
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        layout.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        TextView title = new TextView(context);
        title.setText(MiogramLocale.get("Коментарі", "Комментарии", "Comments"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(0xFFFFFFFF);
        header.addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        countView = new TextView(context);
        countView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        countView.setTextColor(0xFF00F2FE);
        countView.setTypeface(AndroidUtilities.bold());
        countView.setPadding(AndroidUtilities.dp(8), 0, 0, 0);
        header.addView(countView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL));

        TextView closeBtn = new TextView(context);
        closeBtn.setText("✕");
        closeBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        closeBtn.setTextColor(0x88FFFFFF);
        closeBtn.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(4), AndroidUtilities.dp(8), AndroidUtilities.dp(4));
        closeBtn.setOnClickListener(v -> dismiss());
        header.addView(closeBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        // Content Area (RecyclerView, Progress, Empty)
        FrameLayout contentBox = new FrameLayout(context);
        int sheetHeight = (int) Math.min(AndroidUtilities.displaySize.y * 0.55f, AndroidUtilities.dp(420));
        layout.addView(contentBox, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, sheetHeight));

        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        adapter = new CommentAdapter();
        recyclerView.setAdapter(adapter);
        contentBox.addView(recyclerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        progressView = new RadialProgressView(context);
        progressView.setProgressColor(0xFF00F2FE);
        progressView.setSize(AndroidUtilities.dp(36));
        contentBox.addView(progressView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        emptyView = new TextView(context);
        emptyView.setText(MiogramLocale.get("Коментарів не знайдено", "Комментариев не найдено", "No comments found"));
        emptyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        emptyView.setTextColor(0x88FFFFFF);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setVisibility(View.GONE);
        contentBox.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        setCustomView(root);
        loadComments();
    }

    private void loadComments() {
        progressView.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);

        Utilities.globalQueue.postRunnable(() -> {
            List<TikTokComment> loaded = new ArrayList<>();
            int total = 0;
            try {
                String target = videoUrl;
                if (target.contains("vm.tiktok.com") || target.contains("vt.tiktok.com")) {
                    HttpURLConnection conn = (HttpURLConnection) new URL(target).openConnection();
                    conn.setInstanceFollowRedirects(false);
                    conn.connect();
                    String loc = conn.getHeaderField("Location");
                    if (!TextUtils.isEmpty(loc)) target = loc;
                }

                String apiUrl = "https://www.tikwm.com/api/comment/list?url=" + URLEncoder.encode(target, "UTF-8") + "&count=50";
                HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();

                    JSONObject json = new JSONObject(sb.toString());
                    if (json.optInt("code", -1) == 0 && json.has("data")) {
                        JSONObject data = json.getJSONObject("data");
                        total = data.optInt("total", 0);
                        JSONArray commentsArray = data.optJSONArray("comments");
                        if (commentsArray != null) {
                            for (int i = 0; i < commentsArray.length(); i++) {
                                JSONObject cObj = commentsArray.getJSONObject(i);
                                TikTokComment c = new TikTokComment();
                                c.id = cObj.optString("id", "");
                                c.text = cObj.optString("text", "");
                                c.diggCount = cObj.optLong("digg_count", 0);
                                c.createTime = cObj.optLong("create_time", 0);

                                JSONObject uObj = cObj.optJSONObject("user");
                                if (uObj != null) {
                                    c.authorHandle = uObj.optString("unique_id", "");
                                    c.authorName = uObj.optString("nickname", c.authorHandle);
                                    c.authorAvatar = uObj.optString("avatar", "");
                                }
                                loaded.add(c);
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                FileLog.e("AmegramTikTokComments: error loading comments", t);
            }

            final int fTotal = total;
            AndroidUtilities.runOnUIThread(() -> {
                progressView.setVisibility(View.GONE);
                comments.clear();
                comments.addAll(loaded);
                adapter.notifyDataSetChanged();

                if (fTotal > 0 || !comments.isEmpty()) {
                    countView.setText(String.valueOf(fTotal > 0 ? fTotal : comments.size()));
                }

                if (comments.isEmpty()) {
                    emptyView.setVisibility(View.VISIBLE);
                }
            });
        });
    }

    private class CommentAdapter extends RecyclerView.Adapter<CommentViewHolder> {

        @NonNull
        @Override
        public CommentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(parent.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));

            BackupImageView avatar = new BackupImageView(parent.getContext());
            avatar.setRoundRadius(AndroidUtilities.dp(18));
            row.addView(avatar, LayoutHelper.createLinear(36, 36, 0, 0, 10, 0));

            LinearLayout info = new LinearLayout(parent.getContext());
            info.setOrientation(LinearLayout.VERTICAL);
            row.addView(info, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

            TextView name = new TextView(parent.getContext());
            name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            name.setTypeface(AndroidUtilities.bold());
            name.setTextColor(0xFF00F2FE);
            info.addView(name, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 2));

            TextView text = new TextView(parent.getContext());
            text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13.5f);
            text.setTextColor(0xFFFFFFFF);
            info.addView(text, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 2));

            LinearLayout subRow = new LinearLayout(parent.getContext());
            subRow.setOrientation(LinearLayout.HORIZONTAL);
            info.addView(subRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            TextView likes = new TextView(parent.getContext());
            likes.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
            likes.setTextColor(0x88FFFFFF);
            subRow.addView(likes, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0));

            return new CommentViewHolder(row, avatar, name, text, likes);
        }

        @Override
        public void onBindViewHolder(@NonNull CommentViewHolder holder, int position) {
            TikTokComment c = comments.get(position);
            holder.nameView.setText(!TextUtils.isEmpty(c.authorName) ? c.authorName : "@" + c.authorHandle);
            holder.textView.setText(Emoji.replaceEmoji(c.text, holder.textView.getPaint().getFontMetricsInt(), false));

            if (!TextUtils.isEmpty(c.authorAvatar)) {
                holder.avatarView.setImage(ImageLocation.getForPath(c.authorAvatar), "50_50", null, 0, null);
            } else {
                holder.avatarView.setImageResource(R.drawable.msg_media);
            }

            if (c.diggCount > 0) {
                holder.likesView.setText("♥ " + AmegramTikTokManager.formatCount(c.diggCount));
                holder.likesView.setVisibility(View.VISIBLE);
            } else {
                holder.likesView.setVisibility(View.GONE);
            }
        }

        @Override
        public int getItemCount() {
            return comments.size();
        }
    }

    private static class CommentViewHolder extends RecyclerView.ViewHolder {
        final BackupImageView avatarView;
        final TextView nameView;
        final TextView textView;
        final TextView likesView;

        public CommentViewHolder(View itemView, BackupImageView avatar, TextView name, TextView text, TextView likes) {
            super(itemView);
            this.avatarView = avatar;
            this.nameView = name;
            this.textView = text;
            this.likesView = likes;
        }
    }
}
