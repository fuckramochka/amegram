package app.miogram.bridge.wall;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ImageLocation;
import app.miogram.bridge.MiogramLocale;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;

import java.io.File;
import java.util.ArrayList;

import app.miogram.bridge.wall.WallModels.Wall;
import app.miogram.bridge.wall.WallModels.WallElement;

/**
 * Карточка елемента стіни: гарний градієнтний фон, аватар автора,
 * тіло за типом (текст, медіа, стикер, файл, переслане, опитування,
 * малюнок, кнопка, HTML) та футер з лайками/відповідями.
 */
public class WallCardView extends FrameLayout {

    private final LinearLayout content;
    private final FrameLayout bodyContainer;
    private final WallAvatarView avatar;
    private final TextView nameView;
    private final TextView timeView;
    private final TextView menuView;
    private final View pinBadge;
    private final LinearLayout likeBtn;
    private final ImageView likeIcon;
    private final TextView likeCount;
    private final LinearLayout commentsBtn;
    private final TextView commentsCount;
    private final TextView pollVotes;

    private WallElement element;
    private Wall wall;
    private long myId;
    private WallAdapter.Listener listener;
    private WebView webView;
    private BackupImageView imageView;
    private BaseFragment parentFragment;

    public WallCardView(Context context) {
        super(context);
        setClickable(true);
        setElevation(AndroidUtilities.dp(6));
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), AndroidUtilities.dp(20));
            }
        });

        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(12), AndroidUtilities.dp(10));
        addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        avatar = new WallAvatarView(context);
        header.addView(avatar, LayoutHelper.createLinear(30, 30, 0, 0, 8, 0, 0));

        LinearLayout nameBlock = new LinearLayout(context);
        nameBlock.setOrientation(LinearLayout.VERTICAL);
        nameView = new TextView(context);
        WallUi.styleTitle(nameView, 13);
        nameView.setMaxLines(1);
        nameBlock.addView(nameView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        header.addView(nameBlock, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        pinBadge = buildBadge(context, "📌", 0x33FFC24D);
        header.addView(pinBadge, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 6, 0));

        timeView = new TextView(context);
        timeView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 10.5f);
        timeView.setTextColor(0x77FFFFFF);
        timeView.setPadding(AndroidUtilities.dp(6), 0, AndroidUtilities.dp(2), 0);
        header.addView(timeView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        menuView = new TextView(context);
        menuView.setText("⋯");
        menuView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 16);
        menuView.setTextColor(0xAAFFFFFF);
        menuView.setPadding(AndroidUtilities.dp(8), 0, 0, 0);
        menuView.setOnClickListener(v -> showMenu());
        header.addView(menuView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        content.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        bodyContainer = new FrameLayout(context);
        content.addView(bodyContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        // футер
        LinearLayout footer = new LinearLayout(context);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);

        likeBtn = new LinearLayout(context);
        likeBtn.setOrientation(LinearLayout.HORIZONTAL);
        likeBtn.setGravity(Gravity.CENTER_VERTICAL);
        likeIcon = new ImageView(context);
        likeIcon.setImageResource(org.telegram.messenger.R.drawable.media_like);
        likeIcon.setColorFilter(0x99FFFFFF);
        likeBtn.addView(likeIcon, LayoutHelper.createLinear(16, 16));
        likeCount = new TextView(context);
        likeCount.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 11.5f);
        likeCount.setTextColor(0x99FFFFFF);
        likeCount.setPadding(AndroidUtilities.dp(5), 0, 0, 1);
        likeBtn.addView(likeCount, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        likeBtn.setOnClickListener(v -> {
            if (element == null || wall == null || !wall.allowLikes) return;
            boolean liked = !element.likedBy(myId);
            likeIcon.animate().scaleX(1.3f).scaleY(1.3f).setDuration(110)
                    .withEndAction(() -> likeIcon.animate().scaleX(1f).scaleY(1f).setDuration(160).start()).start();
            WallUi.hapticTap(likeIcon);
            if (listener != null) listener.onLike(element, liked);
        });
        footer.addView(likeBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 14, 0));

        commentsBtn = new LinearLayout(context);
        commentsBtn.setOrientation(LinearLayout.HORIZONTAL);
        commentsBtn.setGravity(Gravity.CENTER_VERTICAL);
        ImageView cIcon = new ImageView(context);
        cIcon.setImageResource(org.telegram.messenger.R.drawable.menu_comments);
        cIcon.setColorFilter(0x99FFFFFF);
        commentsBtn.addView(cIcon, LayoutHelper.createLinear(15, 15));
        commentsCount = new TextView(context);
        commentsCount.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 11.5f);
        commentsCount.setTextColor(0x99FFFFFF);
        commentsCount.setPadding(AndroidUtilities.dp(5), 0, 0, 1);
        commentsBtn.addView(commentsCount, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        commentsBtn.setOnClickListener(v -> {
            if (element != null && listener != null) listener.onComments(element);
        });
        footer.addView(commentsBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 14, 0));

        pollVotes = new TextView(context);
        pollVotes.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 11.5f);
        pollVotes.setTextColor(0x99FFFFFF);
        pollVotes.setPadding(AndroidUtilities.dp(5), 0, 0, 1);
        footer.addView(pollVotes, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        content.addView(footer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        setOnClickListener(v -> {
            if (element == null || listener == null) return;
            WallUi.hapticTap(v);
            if (WallModels.TYPE_BUTTON.equals(element.type)) {
                listener.onAction(element);
            } else if (WallModels.TYPE_HTML.equals(element.type)) {
                listener.onOpenHtml(element);
            } else if (TextUtils.isEmpty(element.text) && isMedia(element)) {
                listener.onMediaClick(element);
            } else {
                listener.onComments(element);
            }
        });
    }

    private View buildBadge(Context context, String text, int bg) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 10);
        tv.setTextColor(0xFFFFFFFF);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(2), AndroidUtilities.dp(6), AndroidUtilities.dp(2));
        tv.setBackground(WallUi.pill(bg, 0, 10));
        return tv;
    }

    private boolean isMedia(WallElement e) {
        String t = e.type;
        return WallModels.TYPE_PHOTO.equals(t) || WallModels.TYPE_GIF.equals(t)
                || WallModels.TYPE_VIDEO.equals(t) || WallModels.TYPE_FILE.equals(t);
    }

    // ---------- бінд ----------

    public void bind(WallElement element, Wall wall, long myId, WallAdapter.Listener listener, BaseFragment parentFragment) {
        this.element = element;
        this.wall = wall;
        this.myId = myId;
        this.listener = listener;
        this.parentFragment = parentFragment;

        boolean drawing = WallModels.TYPE_DRAWING.equals(element.type);
        if (drawing) {
            // малюнок живе на «чистому місці» стіни: прозорий фон без картонки
            setBackground(WallUi.pill(0x00000000, 0x1AFFFFFF, 18));
            content.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(6), AndroidUtilities.dp(6), AndroidUtilities.dp(6));
            setElevation(0);
        } else {
            setBackground(WallUi.cardBackground(element.style));
            content.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(12), AndroidUtilities.dp(10));
            setElevation(AndroidUtilities.dp(6));
        }
        // легкий нахил картки для «життя» на стіні
        float rot = drawing ? 0f : WallUi.rotationFor(element.id);
        setRotation(rot);

        avatar.setData(element.authorName == null || element.authorName.isEmpty() ? "?" : element.authorName,
                element.authorId != 0 ? element.authorId : element.authorName == null ? 7 : element.authorName.length());
        nameView.setText(element.authorName == null || element.authorName.isEmpty()
                ? MiogramLocale.get("Анонім", "Аноним", "Anonymous") : element.authorName);
        timeView.setText(WallUi.timeAgo(element.createdAt));
        menuView.setVisibility((element.authorId == myId || (wall != null && wall.ownerId == myId)) ? View.VISIBLE : View.GONE);
        pinBadge.setVisibility(element.pinned ? View.VISIBLE : View.GONE);

        boolean liked = element.likedBy(myId);
        likeIcon.setImageResource(liked
                ? org.telegram.messenger.R.drawable.media_like_active
                : org.telegram.messenger.R.drawable.media_like);
        likeIcon.setColorFilter(liked ? WallUi.PINK : 0x99FFFFFF);
        likeCount.setText(String.valueOf(element.likeCount()));
        likeBtn.setVisibility(wall != null && wall.allowLikes ? View.VISIBLE : View.GONE);

        commentsCount.setText(String.valueOf(element.commentCount()));
        commentsBtn.setVisibility(wall != null && wall.allowComments ? View.VISIBLE : View.GONE);

        if (WallModels.TYPE_POLL.equals(element.type)) {
            pollVotes.setVisibility(View.VISIBLE);
            int n = element.voteCount();
            int avg = element.averageVote();
            pollVotes.setText(avg >= 0
                    ? n + (n == 1 ? " голос" : " голосів") + " · сер. " + avg + "%"
                    : n + " відповідей");
        } else {
            pollVotes.setVisibility(View.GONE);
        }

        buildBody();
    }

    // ---------- тіло ----------

    private void buildBody() {
        Context context = getContext();
        bodyContainer.removeAllViews();
        if (element == null) return;
        View body;
        switch (element.type) {
            case WallModels.TYPE_PHOTO:
            case WallModels.TYPE_GIF:
                body = buildImage(context, false);
                break;
            case WallModels.TYPE_VIDEO:
                body = buildImage(context, true);
                break;
            case WallModels.TYPE_STICKER:
                body = buildSticker(context);
                break;
            case WallModels.TYPE_FILE:
                body = buildFile(context);
                break;
            case WallModels.TYPE_FORWARD:
                body = buildForward(context);
                break;
            case WallModels.TYPE_POLL:
                body = buildPoll(context);
                break;
            case WallModels.TYPE_DRAWING:
                body = buildDrawing(context);
                break;
            case WallModels.TYPE_BUTTON:
                body = buildButton(context);
                break;
            case WallModels.TYPE_HTML:
                body = buildHtml(context);
                break;
            case WallModels.TYPE_TEXT:
            default:
                body = buildText(context);
        }
        if (body != null) {
            bodyContainer.addView(body);
        }
    }

    private TextView buildText(Context context) {
        TextView tv = new TextView(context);
        String text = element.text == null ? "" : element.text;
        float sp = text.length() <= 28 ? 19f : text.length() <= 120 ? 15f : 14f;
        int color = WallModels.STYLE_NIGHT.equals(element.style) ? 0xFFFFFFFF : WallUi.styleTextColor(element.style);
        WallUi.styleBody(tv, sp, color);
        tv.setText(text);
        tv.setMaxLines(text.length() <= 28 ? 3 : 8);
        return tv;
    }

    private View buildImage(Context context, boolean video) {
        FrameLayout frame = new FrameLayout(context);

        int cardW = "full".equals(element.size)
                ? context.getResources().getDisplayMetrics().widthPixels - AndroidUtilities.dp(46)
                : (context.getResources().getDisplayMetrics().widthPixels - AndroidUtilities.dp(72)) / 2;
        int height;
        if (element.mediaHeight > 0 && element.mediaWidth > 0) {
            int h = Math.round(cardW * (element.mediaHeight / (float) element.mediaWidth));
            height = Math.max(AndroidUtilities.dp("full".equals(element.size) ? 140 : 116),
                    Math.min(AndroidUtilities.dp(300), h));
        } else {
            height = AndroidUtilities.dp("full".equals(element.size) ? 210 : 150);
        }

        imageView = new BackupImageView(context);
        String path = TextUtils.isEmpty(element.thumbPath) && video ? element.mediaPath : element.mediaPath;
        imageView.setImage(ImageLocation.getForPath(path == null ? "" : path),
                "full".equals(element.size) ? "700_1200" : "400_600", (Drawable) null, null);
        imageView.setAspectFit(false);
        frame.addView(imageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, height));

        if (!TextUtils.isEmpty(element.thumbPath) && video && !path.equals(element.thumbPath)) {
            imageView.setImage(ImageLocation.getForPath(element.thumbPath), "600_800", (Drawable) null, null);
        }

        if (video) {
            ImageView play = new ImageView(context);
            play.setImageResource(org.telegram.messenger.R.drawable.ic_play);
            play.setColorFilter(0xE6FFFFFF);
            play.setBackground(WallUi.pill(0x66000000, 0, 30));
            play.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12));
            frame.addView(play, LayoutHelper.createFrame(60, 60, Gravity.CENTER));
            TextView tag = new TextView(context);
            tag.setText("▶");
            tag.setTextColor(0xFFFFFFFF);
            tag.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 10);
            tag.setTypeface(AndroidUtilities.bold());
            tag.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(2), AndroidUtilities.dp(8), AndroidUtilities.dp(2));
            tag.setBackground(WallUi.pill(0x66000000, 0, 10));
            frame.addView(tag, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.START, 8, 8, 0, 0));
        }
        return frame;
    }

    private View buildSticker(Context context) {
        TextView tv = new TextView(context);
        tv.setText(element.emoji == null || element.emoji.isEmpty() ? "🫧" : element.emoji);
        tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 60);
        tv.setGravity(Gravity.CENTER);
        int min = AndroidUtilities.dp("full".equals(element.size) ? 170 : 116);
        tv.setMinimumHeight(min);
        tv.setShadowLayer(AndroidUtilities.dp(8), 0, 0, WallUi.alpha(WallUi.colorForString(element.emoji), 0.55f));
        return tv;
    }

    private View buildFile(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(WallUi.pill(WallUi.dark() ? 0x18FFFFFF : 0x0F000000, 0, 14));
        row.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(8), AndroidUtilities.dp(10), AndroidUtilities.dp(8));
        row.setMinimumHeight(AndroidUtilities.dp(66));
        row.setOnClickListener(v -> {
            WallUi.hapticClick(row);
            openFile();
        });
        TextView icon = new TextView(context);
        icon.setText("📎");
        icon.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 16);
        FrameLayout iconWrap = new FrameLayout(context);
        iconWrap.setBackground(WallUi.pill(WallUi.dark() ? 0x26FFFFFF : 0x14000000, 0, 12));
        iconWrap.addView(icon, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        row.addView(iconWrap, LayoutHelper.createLinear(40, 40, 0, 0, 10, 0, 0));
        LinearLayout col = new LinearLayout(context);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(context);
        WallUi.styleTitle(name, 13);
        name.setMaxLines(1);
        name.setText(element.fileName == null || element.fileName.isEmpty() ? "Файл" : element.fileName);
        TextView meta = new TextView(context);
        meta.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 11);
        meta.setTextColor(0x99FFFFFF);
        meta.setText(WallUi.formatSize(element.fileSize) + (element.mime == null || element.mime.isEmpty() ? "" : " · " + element.mime));
        col.addView(name, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        col.addView(meta, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        row.addView(col, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        return row;
    }

    private void openFile() {
        try {
            if (parentFragment == null || parentFragment.getParentActivity() == null || TextUtils.isEmpty(element.mediaPath)) {
                return;
            }
            File f = new File(element.mediaPath);
            if (!f.exists()) return;
            Uri uri = androidx.core.content.FileProvider.getUriForFile(parentFragment.getParentActivity(),
                    ApplicationLoader.getApplicationId() + ".provider", f);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, element.mime == null || element.mime.isEmpty() ? "*/*" : element.mime);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            parentFragment.getParentActivity().startActivity(intent);
        } catch (Throwable e) {
            if (getContext() != null) {
                Toast.makeText(getContext(), "Не вдалося відкрити файл", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private View buildForward(Context context) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView from = new TextView(context);
        from.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 11.5f);
        from.setTypeface(AndroidUtilities.bold());
        from.setTextColor(WallUi.alpha(WallUi.CYAN, WallModels.STYLE_NIGHT.equals(element.style) ? 1f : 0.8f));
        from.setText("➦ Переслано від " + (TextUtils.isEmpty(element.subtitle) ? "—" : element.subtitle));
        box.addView(from, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 5));

        TextView text = new TextView(context);
        String body = element.text == null ? "" : element.text;
        int color = WallModels.STYLE_NIGHT.equals(element.style) ? 0xFFFFFFFF : WallUi.styleTextColor(element.style);
        WallUi.styleBody(text, 14.5f, color);
        text.setText(body);
        text.setMaxLines(9);
        box.addView(text, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        if (!TextUtils.isEmpty(element.sourceUrl)) {
            TextView link = new TextView(context);
            link.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 11);
            link.setTextColor(0x77FFFFFF);
            link.setText(element.sourceUrl);
            box.addView(link, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 3, 0, 0));
        }
        return box;
    }

    private View buildPoll(Context context) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView q = new TextView(context);
        int color = WallModels.STYLE_NIGHT.equals(element.style) ? 0xFFFFFFFF : WallUi.styleTextColor(element.style);
        WallUi.styleTitle(q, 15.5f);
        q.setTextColor(color);
        q.setText(element.text == null ? "" : element.text);
        box.addView(q, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        WallPollView pollView = new WallPollView(context);
        pollView.setData(element, myId, value -> {
            if (listener != null) listener.onVote(element, value);
        });
        box.addView(pollView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        return box;
    }

    private View buildDrawing(Context context) {
        ImageView iv = new ImageView(context);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        File f = TextUtils.isEmpty(element.mediaPath) ? null : new File(element.mediaPath);
        Bitmap bmp = null;
        if (f != null && f.exists()) {
            try {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(f.getAbsolutePath(), bounds);
                BitmapFactory.Options opts = new BitmapFactory.Options();
                int max = Math.max(bounds.outWidth, bounds.outHeight);
                int sample = 1;
                while (max / (sample * 2) >= 1080) sample *= 2;
                opts.inSampleSize = Math.max(1, sample);
                bmp = BitmapFactory.decodeFile(f.getAbsolutePath(), opts);
            } catch (Throwable ignore) {
            }
        }
        if (bmp != null) {
            iv.setImageBitmap(bmp);
        } else {
            iv.setImageResource(org.telegram.messenger.R.drawable.baseline_brush_24);
            iv.setColorFilter(0x66FFFFFF);
        }
        return iv;
    }

    private View buildButton(Context context) {
        LinearLayout btn = new LinearLayout(context);
        btn.setOrientation(LinearLayout.HORIZONTAL);
        btn.setGravity(Gravity.CENTER);
        btn.setMinimumHeight(AndroidUtilities.dp(46));
        btn.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(8), AndroidUtilities.dp(14), AndroidUtilities.dp(8));
        String kind = element.action == null || element.action.kind == null
                ? "url" : element.action.kind;
        btn.setBackground(WallUi.actionPill(kind));
        TextView label = new TextView(context);
        label.setTextColor(0xFFFFFFFF);
        label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 14);
        label.setTypeface(AndroidUtilities.bold());
        label.setText(TextUtils.isEmpty(element.text) ? "Кнопка" : element.text);
        label.setSingleLine(true);
        btn.addView(label, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        TextView arrow = new TextView(context);
        arrow.setText(" →");
        arrow.setTextColor(0xB3FFFFFF);
        arrow.setTypeface(AndroidUtilities.bold());
        btn.addView(arrow, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        btn.setOnClickListener(v -> {
            WallUi.hapticClick(btn);
            if (listener != null) listener.onAction(element);
        });
        return btn;
    }

    private View buildHtml(Context context) {
        FrameLayout frame = new FrameLayout(context);
        if (webView == null) {
            webView = new WebView(context);
            WebSettings st = webView.getSettings();
            st.setJavaScriptEnabled(true);
            st.setDomStorageEnabled(true);
            st.setAllowFileAccess(false);
            st.setAllowContentAccess(false);
            try {
                st.setBlockNetworkLoads(true);
            } catch (Throwable ignore) {
            }
            webView.setBackgroundColor(Color.TRANSPARENT);
            webView.setOnTouchListener((v, e) -> true); // не краде скрол; тап відкриває прев'ю
            frame.addView(webView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(150)));
        }
        webView.loadDataWithBaseURL(null, WallHtmlSheet.buildHtml(element), "text/html", "UTF-8", null);

        TextView tag = new TextView(context);
        tag.setText("⚡ HTML · тап → прев'ю");
        tag.setTextColor(0xE6FFFFFF);
        tag.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 10);
        tag.setTypeface(AndroidUtilities.bold());
        tag.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(3), AndroidUtilities.dp(8), AndroidUtilities.dp(3));
        tag.setBackground(WallUi.pill(0x66000000, 0, 10));
        frame.addView(tag, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.END, 8, 8, 0, 0));
        return frame;
    }

    // ---------- меню автора/власника ----------

    private void showMenu() {
        if (element == null || wall == null || parentFragment == null || parentFragment.getParentActivity() == null) {
            return;
        }
        WallUi.hapticTap(menuView);
        boolean canEditSelf = element.authorId == myId;
        boolean owner = wall.ownerId == myId;
        ArrayList<String> items = new ArrayList<>();
        ArrayList<Integer> acts = new ArrayList<>();
        if (canEditSelf) {
            items.add(MiogramLocale.get("Редагувати", "Редактировать", "Edit"));
            acts.add(0);
        }
        if (owner) {
            items.add(element.pinned
                    ? MiogramLocale.get("Відкріпити", "Открепить", "Unpin")
                    : MiogramLocale.get("Закріпити", "Закрепить", "Pin"));
            acts.add(1);
        }
        if (canEditSelf || owner) {
            items.add(MiogramLocale.get("Видалити", "Удалить", "Delete"));
            acts.add(2);
        }
        if (acts.isEmpty()) return;
        AlertDialog.Builder b = new AlertDialog.Builder(parentFragment.getParentActivity());
        b.setItems(items.toArray(new String[0]), (d, w) -> {
            int act = acts.get(w);
            if (act == 0) {
                if (listener != null) listener.onEdit(element);
            } else if (act == 1) {
                element.pinned = !element.pinned;
                WallStorage.saveWall(wall);
                if (listener != null) listener.onEdit(element);
            } else {
                if (listener != null) listener.onDelete(element);
            }
        });
        b.setNegativeButton(MiogramLocale.get("Скасувати", "Отмена", "Cancel"), null);
        parentFragment.showDialog(b.create());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
    }
}
