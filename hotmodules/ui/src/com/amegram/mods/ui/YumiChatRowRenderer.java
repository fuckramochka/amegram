package com.amegram.mods.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.TextUtils;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

import app.amegram.hot.api.HotHost;

/**
 * Yumi chat-list row renderer, owned by the UI module.
 *
 * <p>The official {@code DialogCell} remains the adapter item and keeps all of
 * its gestures, swipe actions, action mode, selection and avatar/story logic.
 * After Telegram has drawn the cell, the module paints the Yumi card over the
 * old row. A circular cutout keeps the original avatar/story pixels visible.
 * The text is Telegram's own pre-built {@code StaticLayout}, so emoji, spoilers
 * and text metrics are preserved without parsing message content again.</p>
 *
 * <p>Binding happens only after a cell update, never in {@link #draw}. The draw
 * path only sets existing paints and reuses one {@link Path}/{@link RectF} per
 * cell, so there are no per-frame allocations.</p>
 */
public final class YumiChatRowRenderer {

    private final UiModule owner;
    private final HotHost host;
    private final YumiPalette palette = new YumiPalette(null);
    private final Map<View, Row> rows = new WeakHashMap<>();
    private final Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgeTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF cardRect = new RectF();
    private final RectF holeRect = new RectF();
    private final Path cardPath = new Path();
    private Drawable pinDrawable;
    private Drawable muteDrawable;

    public YumiChatRowRenderer(UiModule owner, HotHost host) {
        this.owner = owner;
        this.host = host;
        strokePaint.setStyle(Paint.Style.STROKE);
        badgeTextPaint.setTextAlign(Paint.Align.CENTER);
        badgeTextPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        badgeTextPaint.setColor(Color.WHITE);
    }

    public void refreshPalette(Context context) {
        palette.refresh(context);
        strokePaint.setStrokeWidth(dp(context, 1));
        tintIcons();
    }

    public void invalidateAll() {
        for (View cell : rows.keySet()) {
            cell.invalidate();
        }
    }

    public void bind(View cell) {
        if (!owner.isExpressive()) {
            return;
        }
        final Row row = stateFor(cell);
        row.read(cell);
        if (cell.getContext() != null && pinDrawable == null) {
            pinDrawable = icon(cell.getContext(), "yumi_ic_pin");
            muteDrawable = icon(cell.getContext(), "yumi_ic_mute");
            tintIcons();
        }
    }

    public void draw(View cell, Canvas canvas) {
        if (!owner.isExpressive() || cell.getWidth() <= 0 || cell.getHeight() <= 0) {
            return;
        }
        final int listStyle = owner.chatListStyle();
        if (listStyle == UiModule.CHAT_STYLE_FLAT) {
            // Apple Flat: чистий стиль із фірмовим волосковим роздільником
            final boolean rtl = cell.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            final Context context = cell.getContext();
            final int sepStart = rtl ? dp(context, 16) : dp(context, 78);
            final int sepEnd = rtl ? cell.getWidth() - dp(context, 78) : cell.getWidth() - dp(context, 16);
            strokePaint.setColor(palette.separator);
            strokePaint.setStrokeWidth(Math.max(1, dp(context, 0.5f)));
            canvas.drawLine(sepStart, cell.getHeight() - strokePaint.getStrokeWidth(),
                    sepEnd, cell.getHeight() - strokePaint.getStrokeWidth(), strokePaint);
            return;
        }
        if (cell.getTranslationX() != 0f || cell.getTranslationY() != 0f) {
            // Reveal/swipe in progress: let the original row own the frame.
            return;
        }
        final Row row = rows.get(cell);
        if (row == null || !row.hasContent()) {
            return;
        }
        final int width = cell.getWidth();
        final int height = cell.getHeight();
        final boolean rtl = cell.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        final Context context = cell.getContext();
        final boolean islands = listStyle == UiModule.CHAT_STYLE_ISLANDS;
        final int side = islands ? dp(context, 8) : dp(context, 12);
        final int vertical = islands ? dp(context, 3) : dp(context, 5);
        final float radius = islands ? dp(context, 16) : dp(context, 21);
        final float holeX = rtl ? width - dp(context, 39) : dp(context, 39);
        final float holeY = height / 2f;
        final float holeR = dp(context, 28);

        cardRect.set(side, vertical, width - side, height - vertical);
        cardPath.rewind();
        cardPath.setFillType(Path.FillType.EVEN_ODD);
        cardPath.addRoundRect(cardRect, radius, radius, Path.Direction.CW);
        if (owner.isChatSquircleAvatars()) {
            final float sqRadius = dp(context, 16);
            holeRect.set(holeX - holeR, holeY - holeR, holeX + holeR, holeY + holeR);
            cardPath.addRoundRect(holeRect, sqRadius, sqRadius, Path.Direction.CW);
        } else {
            cardPath.addCircle(holeX, holeY, holeR, Path.Direction.CW);
        }

        final boolean pinnedAccent = row.pinned && owner.isChatPinnedAccent();
        cardPaint.setColor(pinnedAccent
                ? blend(palette.cardSurface, palette.primaryContainer, 0.24f)
                : palette.cardSurface);
        canvas.drawPath(cardPath, cardPaint);
        if (owner.isChatCardOutline()) {
            strokePaint.setColor(palette.cardRim);
            strokePaint.setStrokeWidth(Math.max(1, dp(context, 0.75f)));
            canvas.drawPath(cardPath, strokePaint);
        }

        // A small Yumi color mark gives each card a clear leading edge while
        // leaving the official avatar, gestures and text layouts untouched.
        if (row.pinned && owner.isChatPinnedAccent()) {
            final float markW = dp(context, 3);
            final float markTop = cardRect.top + dp(context, 12);
            final float markBottom = cardRect.bottom - dp(context, 12);
            final float markX = rtl ? cardRect.right - markW : cardRect.left;
            badgePaint.setColor(palette.primary);
            canvas.drawRoundRect(markX, markTop, markX + markW, markBottom,
                    markW, markW, badgePaint);
        }

        final int start = rtl ? dp(context, 16) : dp(context, 78);
        final int end = rtl ? width - dp(context, 78) : width - dp(context, 16);
        final int top = dp(context, 11);
        final int timeRight = rtl ? dp(context, 16) : width - dp(context, 16);
        final int timeX = rtl ? timeRight : timeRight - row.timeWidth;
        final int timeTop = dp(context, 11);
        final int nameMax = Math.max(0, Math.abs(rtl ? end - timeX : timeX - start) - dp(context, 8));
        final int messageTop = top + dp(context, 22);
        final int messageBottom = height - dp(context, 7);

        final int nameWidth = row.nameLayout != null ? Math.min(row.nameLayout.getWidth(), nameMax) : 0;
        final int nameX = rtl ? end - nameWidth : start;
        canvas.save();
        canvas.clipRect(rtl ? end - nameMax : start, top,
                rtl ? end : start + nameMax, top + dp(context, 21));
        row.drawName(canvas, nameX, top);
        canvas.restore();

        if (row.messageLayout != null) {
            final int messageWidth = Math.min(row.messageLayout.getWidth(), Math.abs(end - start));
            final int messageX = rtl ? end - messageWidth : start;
            canvas.save();
            canvas.clipRect(rtl ? start : start, messageTop,
                    rtl ? end : end, messageBottom);
            row.drawMessage(canvas, messageX, messageTop);
            canvas.restore();
        }

        if (row.timeLayout != null) {
            canvas.save();
            canvas.clipRect(rtl ? timeRight : timeX, timeTop,
                    rtl ? timeRight + row.timeWidth : timeRight, timeTop + dp(context, 20));
            row.drawTime(canvas, timeX, timeTop);
            canvas.restore();
        }

        if (row.pinned && pinDrawable != null) {
            final int size = dp(context, 14);
            final int x = rtl ? timeRight + row.timeWidth + dp(context, 5) : timeX - size - dp(context, 5);
            pinDrawable.setBounds(x, timeTop + dp(context, 2), x + size, timeTop + dp(context, 2) + size);
            pinDrawable.draw(canvas);
        }

        if (row.hasBadge()) {
            final float badgeH = dp(context, 20);
            final float padH = dp(context, 6);
            badgeTextPaint.setTextSize(dp(context, 11));
            final float textW = !TextUtils.isEmpty(row.badgeText) ? badgeTextPaint.measureText(row.badgeText) : 0f;
            final float badgeW = Math.max(badgeH, textW + padH * 2);
            final float badgeR = badgeH / 2f;
            final float bx = rtl ? dp(context, 16) : width - dp(context, 16) - badgeW;
            final float by = height - dp(context, 9) - badgeH;

            // Apple dynamic capsule badge
            badgePaint.setColor(row.muted ? palette.secondaryFill : palette.primary);
            canvas.drawRoundRect(bx, by, bx + badgeW, by + badgeH, badgeR, badgeR, badgePaint);
            if (!TextUtils.isEmpty(row.badgeText)) {
                badgeTextPaint.setColor(row.muted ? palette.textSecondary : palette.onPrimary);
                final float baseline = by + badgeH / 2f
                        - (badgeTextPaint.descent() + badgeTextPaint.ascent()) / 2f;
                canvas.drawText(row.badgeText, bx + badgeW / 2f, baseline, badgeTextPaint);
            } else {
                dotPaint.setColor(palette.onPrimary);
                canvas.drawCircle(bx + badgeW / 2f, by + badgeH / 2f, dp(context, 3.5f), dotPaint);
            }
        }

        if (row.muted && muteDrawable != null) {
            final int size = dp(context, 13);
            final int x = rtl ? dp(context, 78) - size : end - size;
            final int y = messageTop + dp(context, 2);
            muteDrawable.setBounds(x, y, x + size, y + size);
            muteDrawable.draw(canvas);
        }
    }

    private Row stateFor(View cell) {
        Row row = rows.get(cell);
        if (row == null) {
            row = new Row();
            rows.put(cell, row);
        }
        return row;
    }

    private void tintIcons() {
        if (pinDrawable != null) {
            pinDrawable.setColorFilter(new PorterDuffColorFilter(
                    palette.navInactive, PorterDuff.Mode.SRC_IN));
        }
        if (muteDrawable != null) {
            muteDrawable.setColorFilter(new PorterDuffColorFilter(
                    palette.navInactive, PorterDuff.Mode.SRC_IN));
        }
    }

    private Drawable icon(Context context, String name) {
        final int id = context.getResources().getIdentifier(name, "drawable", context.getPackageName());
        return id == 0 ? null : context.getResources().getDrawable(id).mutate();
    }

    private static int blend(int from, int to, float amount) {
        final float inv = 1f - amount;
        return Color.argb(
                Math.round(Color.alpha(from) * inv + Color.alpha(to) * amount),
                Math.round(Color.red(from) * inv + Color.red(to) * amount),
                Math.round(Color.green(from) * inv + Color.green(to) * amount),
                Math.round(Color.blue(from) * inv + Color.blue(to) * amount));
    }

    private static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static final class Row {
        Layout nameLayout;
        Layout messageLayout;
        Layout timeLayout;
        int timeWidth;
        String badgeText = "";
        boolean pinned;
        boolean muted;
        int unreadCount;
        int mentionCount;
        boolean markUnread;

        boolean hasContent() {
            return nameLayout != null;
        }

        boolean hasBadge() {
            return unreadCount > 0 || mentionCount > 0 || markUnread;
        }

        void drawName(Canvas canvas, int x, int y) {
            if (nameLayout == null) {
                return;
            }
            canvas.save();
            canvas.translate(x, y);
            nameLayout.draw(canvas);
            canvas.restore();
        }

        void drawMessage(Canvas canvas, int x, int y) {
            if (messageLayout == null) {
                return;
            }
            canvas.save();
            canvas.translate(x, y);
            messageLayout.draw(canvas);
            canvas.restore();
        }

        void drawTime(Canvas canvas, int x, int y) {
            if (timeLayout == null) {
                return;
            }
            canvas.save();
            canvas.translate(x, y);
            timeLayout.draw(canvas);
            canvas.restore();
        }

        void read(View cell) {
            try {
                final Class<?> type = cell.getClass();
                nameLayout = (Layout) field(type, "nameLayout").get(cell);
                messageLayout = (Layout) field(type, "messageLayout").get(cell);
                timeLayout = (Layout) field(type, "timeLayout").get(cell);
                final Field unread = field(type, "unreadCount");
                unreadCount = unread.getInt(cell);
                final Field mention = field(type, "mentionCount");
                mentionCount = mention.getInt(cell);
                final Field mark = field(type, "markUnread");
                markUnread = mark.getBoolean(cell);
                final Field mutedField = field(type, "dialogMuted");
                muted = mutedField.getBoolean(cell);
                final Method isPinned = type.getMethod("getIsPinned");
                final Object pinnedValue = isPinned.invoke(cell);
                pinned = pinnedValue instanceof Boolean && (Boolean) pinnedValue;
                if (mentionCount > 0) {
                    badgeText = "@";
                } else if (unreadCount > 99) {
                    badgeText = "99+";
                } else if (unreadCount > 0) {
                    badgeText = String.valueOf(unreadCount);
                } else {
                    badgeText = "";
                }
                timeWidth = timeLayout != null ? timeLayout.getWidth() : 0;
            } catch (Throwable ignore) {
                nameLayout = null;
                messageLayout = null;
                timeLayout = null;
                timeWidth = 0;
            }
        }

        private static Field field(Class<?> type, String name) throws NoSuchFieldException {
            Class<?> current = type;
            while (current != null) {
                try {
                    final Field field = current.getDeclaredField(name);
                    field.setAccessible(true);
                    return field;
                } catch (NoSuchFieldException ignored) {
                    current = current.getSuperclass();
                }
            }
            throw new NoSuchFieldException(name);
        }
    }
}
