package app.miogram.bridge.wall;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import app.miogram.bridge.MiogramLocale;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.Components.LayoutHelper;

import java.util.List;

import app.miogram.bridge.wall.WallModels.Wall;
import app.miogram.bridge.wall.WallModels.WallComment;
import app.miogram.bridge.wall.WallModels.WallElement;

/**
 * Відповіді/коментарі елемента: список, лайки коментарів, введення.
 * Видалення доступне авторові коментаря та автору стіни (довгий тап).
 */
public class WallCommentsSheet extends BottomSheet {

    public interface Listener {
        void onCommentsChanged();
    }

    private final Context context;
    private final BaseFragment parentFragment;
    private final Wall wall;
    private final WallElement element;
    private final Listener listener;
    private final long myId;
    private LinearLayout list;
    private EditText input;
    private TextView likeBtn;
    private TextView likeCount;
    private TextView pollVotes;

    private boolean rebuildScheduled;

    public WallCommentsSheet(Context context, BaseFragment parentFragment, Wall wall, WallElement element, Listener listener) {
        super(context, false, null);
        this.context = context;
        this.parentFragment = parentFragment;
        this.wall = wall;
        this.element = element;
        this.listener = listener;
        this.myId = WallStorage.currentUserId();

        setApplyTopPadding(true);
        setApplyBottomPadding(true);
        setTitle(MiogramLocale.get("Відповіді", "Ответы", "Answers"));

        boolean dark = WallUi.dark();
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(WallUi.pill(dark ? 0xF2150F2E : 0xF8FFFFFF, 0x2EFFFFFF, 20));
        root.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(10), AndroidUtilities.dp(14), AndroidUtilities.dp(12));

        TextView message = new TextView(context);
        String previewText;
        if (!TextUtils.isEmpty(element.emoji)) {
            previewText = element.emoji;
        } else if (!TextUtils.isEmpty(element.text)) {
            previewText = element.text;
        } else {
            previewText = "…";
        }
        message.setText(previewText);
        WallUi.styleBody(message, previewText.length() <= 24 ? 16 : 13, WallUi.textPrimary());
        message.setMaxLines(3);
        root.addView(message, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

        LinearLayout likeRow = new LinearLayout(context);
        likeRow.setOrientation(LinearLayout.HORIZONTAL);
        likeRow.setGravity(Gravity.CENTER_VERTICAL);
        likeBtn = new TextView(context);
        likeBtn.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 18);
        likeBtn.setOnClickListener(v -> {
            boolean liked = !element.likedBy(myId);
            likeBtn.setText(liked ? "♥" : "♡");
            likeBtn.setTextColor(liked ? WallUi.PINK : WallUi.textSecondary());
            if (liked) {
                likeBtn.animate().scaleX(1.4f).scaleY(1.4f).setDuration(120)
                        .withEndAction(() -> likeBtn.animate().scaleX(1f).scaleY(1f).setDuration(160).start()).start();
            }
            WallUi.hapticTap(v);
            WallStorage.toggleLike(wall, element.id, myId, liked);
            updateCounters();
        });
        likeRow.addView(likeBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        likeCount = new TextView(context);
        likeCount.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 12);
        likeCount.setTextColor(WallUi.textSecondary());
        likeCount.setPadding(AndroidUtilities.dp(6), 0, 0, 0);
        likeRow.addView(likeCount, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        space = new View(context);
        likeRow.addView(space, LayoutHelper.createLinear(0, 1, 1f));

        pollVotes = new TextView(context);
        pollVotes.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 12);
        pollVotes.setTextColor(WallUi.textSecondary());
        likeRow.addView(pollVotes, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        root.addView(likeRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 10));

        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFillViewport(true);
        list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f, 0, 0, 0, 8));

        LinearLayout inputRow = new LinearLayout(context);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        input = new EditText(context);
        input.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 14);
        input.setTextColor(WallUi.textPrimary());
        input.setHintTextColor(WallUi.textTertiary());
        input.setHint(MiogramLocale.get("Написати відповідь…", "Написать ответ…", "Write an answer…"));
        input.setBackground(WallUi.pill(dark ? 0x18FFFFFF : 0x12000000, 0, 20));
        input.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(11), AndroidUtilities.dp(14), AndroidUtilities.dp(11));
        input.setSingleLine(true);
        input.setInputType(EditorInfo.TYPE_CLASS_TEXT);
        input.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        inputRow.addView(input, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        ImageView send = new ImageView(context);
        send.setImageResource(org.telegram.messenger.R.drawable.attach_send);
        send.setColorFilter(0xFFFFFFFF);
        send.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, android.graphics.Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), AndroidUtilities.dp(22));
            }
        });
        send.setClipToOutline(true);
        send.setBackground(WallUi.brandPill(21));
        send.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12));
        send.setOnClickListener(v -> {
            String text = input.getText().toString().trim();
            if (text.isEmpty()) return;
            WallUi.hapticClick(v);
            WallComment c = WallModels.newComment(myId, WallStorage.displayName(myId), text);
            WallStorage.addComment(wall, element.id, c);
            input.setText("");
            AndroidUtilities.hideKeyboard(input);
            rebuildComments();
            if (listener != null) listener.onCommentsChanged();
        });
        inputRow.addView(send, LayoutHelper.createLinear(44, 44, 0, 4, 0, 0));
        root.addView(inputRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        setCustomView(root);
        int screenH = context.getResources().getDisplayMetrics().heightPixels;
        root.setMinimumHeight(Math.min(AndroidUtilities.dp(520), screenH - AndroidUtilities.dp(120)));
        updateCounters();
        rebuildComments();
    }

    private View space;

    private void updateCounters() {
        if (likeCount == null) return;
        likeCount.setText(String.valueOf(element.likeCount()));
        likeBtn.setText(element.likedBy(myId) ? "♥" : "♡");
        likeBtn.setTextColor(element.likedBy(myId) ? WallUi.PINK : WallUi.textSecondary());
        if (WallModels.TYPE_POLL.equals(element.type)) {
            pollVotes.setVisibility(View.VISIBLE);
            pollVotes.setText("📊 " + element.voteCount() + " · сер. " + Math.max(0, element.averageVote()) + "%");
        } else {
            pollVotes.setVisibility(View.GONE);
        }
    }

    private void rebuildComments() {
        Context context = getContext();
        if (context == null || list == null) return;
        list.removeAllViews();
        List<WallComment> comments = element.comments;
        if (comments == null || comments.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText(MiogramLocale.get("Ще немає відповідей — перша твоя ✦",
                    "Еще нет ответов — первая твоя ✦", "No answers yet — yours will be first ✦"));
            empty.setTextColor(WallUi.textTertiary());
            empty.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 13);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, AndroidUtilities.dp(24), 0, AndroidUtilities.dp(24));
            list.addView(empty, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        } else {
            for (int i = 0; i < comments.size(); i++) {
                list.addView(buildCommentRow(context, comments.get(i)));
            }
        }
        if (!rebuildScheduled) {
            rebuildScheduled = true;
            list.postDelayed(() -> {
                rebuildScheduled = false;
                try {
                    ((ScrollView) list.getParent()).smoothScrollTo(0, Integer.MAX_VALUE);
                } catch (Throwable ignore) {
                }
            }, 90);
        }
    }

    private View buildCommentRow(Context context, WallComment comment) {
        LinearLayout col = new LinearLayout(context);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, 0, 0, AndroidUtilities.dp(9));

        LinearLayout headRow = new LinearLayout(context);
        headRow.setOrientation(LinearLayout.HORIZONTAL);
        headRow.setGravity(Gravity.CENTER_VERTICAL);

        WallAvatarView avatar = new WallAvatarView(context);
        avatar.setData(comment.authorName, comment.authorId);
        headRow.addView(avatar, LayoutHelper.createLinear(26, 26, 0, 0, 8, 0, 0));

        TextView name = new TextView(context);
        name.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 11.5f);
        name.setTypeface(AndroidUtilities.bold());
        name.setTextColor(WallUi.textSecondary());
        name.setText(comment.authorName + "  ·  " + WallUi.timeAgo(comment.createdAt));
        headRow.addView(name, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        col.addView(headRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 2));

        TextView text = new TextView(context);
        WallUi.styleBody(text, 13.5f, WallUi.textPrimary());
        text.setText(comment.text);
        col.addView(text, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        LinearLayout actionsRow = new LinearLayout(context);
        actionsRow.setOrientation(LinearLayout.HORIZONTAL);
        actionsRow.setGravity(Gravity.CENTER_VERTICAL);

        boolean liked = Boolean.TRUE.equals(comment.likes.get(String.valueOf(myId)));
        TextView like = new TextView(context);
        like.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 11.5f);
        like.setTextColor(liked ? WallUi.PINK : WallUi.textTertiary());
        like.setText((comment.likeCount() > 0 ? comment.likeCount() + "  " : "") + (liked ? "♥" : "♡"));
        like.setOnClickListener(v -> {
            boolean nowLiked = !liked;
            WallUi.hapticTap(v);
            WallStorage.toggleCommentLike(wall, element.id, comment.id, myId, nowLiked);
            like.setTextColor(nowLiked ? WallUi.PINK : WallUi.textTertiary());
            like.setText((comment.likeCount() > 0 ? comment.likeCount() + "  " : "") + (nowLiked ? "♥" : "♡"));
            if (nowLiked) {
                like.animate().scaleX(1.25f).scaleY(1.25f).setDuration(110)
                        .withEndAction(() -> like.animate().scaleX(1f).scaleY(1f).setDuration(150).start()).start();
            }
            if (listener != null) listener.onCommentsChanged();
        });
        actionsRow.addView(like, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        col.addView(actionsRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 1, 0, 0));
        col.setOnClickListener(v -> {
            // тап нічого не робить — дії через лонгтап
        });

        col.setOnLongClickListener(v -> {
            boolean canDelete = comment.authorId == myId || (wall != null && wall.ownerId == myId);
            if (!canDelete || parentFragment == null || parentFragment.getParentActivity() == null) {
                return false;
            }
            AlertDialog.Builder b = new AlertDialog.Builder(parentFragment.getParentActivity());
            b.setTitle(MiogramLocale.get("Видалити відповідь?", "Удалить ответ?", "Delete answer?"));
            b.setPositiveButton(MiogramLocale.get("Видалити", "Удалить", "Delete"), (d, w) -> {
                WallStorage.removeComment(wall, element.id, comment.id);
                rebuildComments();
                if (listener != null) listener.onCommentsChanged();
            });
            b.setNegativeButton(MiogramLocale.get("Скасувати", "Отмена", "Cancel"), null);
            parentFragment.showDialog(b.create());
            return true;
        });
        return col;
    }
}
