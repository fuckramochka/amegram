package app.miogram.bridge.wall;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.RecyclerListView;

import app.amegram.hot.HotModulesManager;
import app.amegram.hot.api.HotServices;
import app.amegram.hot.api.HotWall;
import app.miogram.bridge.MiogramLocale;
import app.miogram.bridge.wall.WallModels.Wall;
import app.miogram.bridge.wall.WallModels.WallElement;

/**
 * Публічна точка входу бета-модуля «Стіна профілю».
 * Гібрид: працює вбудовано з APK і вмикається .hmod-модулем (HotServices.WALL).
 */
public final class WallModule {

    public static final String MODULE_ID = "wall";
    public static final String VERSION = "1.0.0-beta";

    private WallModule() {
    }

    // ---------- доступність ----------

    public static boolean isHotInstalled() {
        try {
            return HotModulesManager.isModuleInstalled(MODULE_ID);
        } catch (Throwable ignore) {
            return false;
        }
    }

    public static boolean isHotEnabled() {
        try {
            return HotModulesManager.isModuleEnabled(MODULE_ID);
        } catch (Throwable ignore) {
            return false;
        }
    }

    public static boolean isAvailable() {
        if (isHotEnabled()) return true;
        return WallPrefs.isBetaEnabled();
    }

    public static boolean setBetaEnabled(boolean enabled) {
        WallPrefs.setBetaEnabled(enabled);
        return enabled;
    }

    public static HotWall hotService() {
        try {
            return HotModulesManager.getService(HotServices.WALL);
        } catch (Throwable ignore) {
            return null;
        }
    }

    public static String sourceLabel() {
        if (isHotEnabled()) {
            return MiogramLocale.get("Хот-модуль .hmod", "Хот-модуль .hmod", ".hmod hot module");
        }
        return MiogramLocale.get("Вбудований бета-модуль", "Встроенный бета-модуль", "Built-in beta module");
    }

    // ---------- відкриття ----------

    public static void openOwnWall(BaseFragment from) {
        if (from == null) return;
        long myId = WallStorage.currentUserId();
        openWall(from, myId, WallStorage.displayName(myId), true);
    }

    public static void openWall(BaseFragment from, long ownerId, String ownerName, boolean isSelf) {
        if (from == null) return;
        if (!isAvailable()) {
            BulletinFactory.of(from).createSimpleBulletin(org.telegram.messenger.R.raw.info,
                    MiogramLocale.get("Увімкни бета-модуль «Стіна» в налаштуваннях Yumigram",
                            "Включи бета-модуль «Стена» в настройках Yumigram",
                            "Enable the Wall beta module in Yumigram settings")).show();
            return;
        }
        WallPrefs.setLastOwner(ownerId);
        from.presentFragment(new WallActivity(ownerId, ownerName, isSelf));
    }

    public static void openBetaSettings(BaseFragment from) {
        if (from == null) return;
        from.presentFragment(new WallBetaSettingsActivity());
    }

    // ---------- підказка на банері профілю ----------

    private static final Paint chipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint chipStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint chipText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint chipStar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF chipRect = new RectF();
    private static Shader chipShader;
    private static int chipShaderWidth = -1;

    public static void drawProfileEntry(Canvas canvas, int width, int headerHeight, int topOffset, int count) {
        if (canvas == null || width <= 0 || !isAvailable()) return;
        if (headerHeight < AndroidUtilities.dp(150)) return;
        int padH = AndroidUtilities.dp(12);
        int h = AndroidUtilities.dp(30);
        int y = topOffset + AndroidUtilities.dp(10);
        if (y + h > headerHeight - AndroidUtilities.dp(4)) return;

        chipText.setTextSize(AndroidUtilities.dp(12.5f));
        chipText.setTypeface(AndroidUtilities.bold());
        String label = MiogramLocale.get("стіна", "стена", "wall");
        float textW = chipText.measureText(label);
        float countW = count > 0 ? AndroidUtilities.dp(26) : 0;
        float w = padH * 2 + AndroidUtilities.dp(14) + textW + countW + AndroidUtilities.dp(6);
        float right = width - AndroidUtilities.dp(14);
        float left = right - w;
        float top = y;
        float bottom = y + h;
        chipRect.set(left, top, right, bottom);

        if (chipShader == null || chipShaderWidth != width) {
            chipShader = new LinearGradient(0, 0, width, 0,
                    new int[]{0xE6FF4D8D, 0xE68A5CFF, 0xE635E0FF},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
            chipShaderWidth = width;
        }
        chipPaint.setShader(chipShader);
        chipPaint.setAlpha(220);
        canvas.drawRoundRect(chipRect, h / 2f, h / 2f, chipPaint);
        chipStroke.setStyle(Paint.Style.STROKE);
        chipStroke.setStrokeWidth(AndroidUtilities.dp(1));
        chipStroke.setColor(0x66FFFFFF);
        canvas.drawRoundRect(chipRect, h / 2f, h / 2f, chipStroke);

        float starX = left + padH - AndroidUtilities.dp(1);
        float centerY = top + h / 2f + AndroidUtilities.dp(4.2f);
        chipStar.setColor(0xFFFFFFFF);
        chipStar.setTextSize(AndroidUtilities.dp(13));
        chipStar.setTypeface(AndroidUtilities.bold());
        canvas.drawText("✦", starX, centerY, chipStar);

        chipText.setColor(0xFFFFFFFF);
        float tx = left + padH + AndroidUtilities.dp(14);
        canvas.drawText(label, tx, centerY, chipText);

        if (count > 0) {
            float cx = tx + textW + AndroidUtilities.dp(9);
            float cy = top + h / 2f;
            chipPaint.setShader(null);
            chipPaint.setColor(0x33FFFFFF);
            canvas.drawCircle(cx, cy, AndroidUtilities.dp(8), chipPaint);
            chipText.setTextSize(AndroidUtilities.dp(9.5f));
            String cnt = String.valueOf(Math.min(count, 99));
            float cw = chipText.measureText(cnt);
            canvas.drawText(cnt, cx - cw / 2f, cy + AndroidUtilities.dp(3.2f), chipText);
        }
        chipPaint.setShader(null);
    }

    /**
     * Тап по порожній частині банера у власному профілі відкриває стіну.
     * Вішаємо слухач на RecyclerView: він не блокує скрол, реагує лише на
     * короткий тап у зоні header-падінгу на самому верху списку.
     */
    public static void attachBannerTap(RecyclerListView listView, BaseFragment fragment, long ownerId, String ownerName) {
        if (listView == null || fragment == null || !isAvailable()) return;
        final int touchSlop = ViewConfiguration.get(fragment.getContext()).getScaledTouchSlop();
        final float[] downX = new float[1];
        final float[] downY = new float[1];
        final long[] downTime = new long[1];
        listView.addOnItemTouchListener(new RecyclerView.OnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(RecyclerView rv, MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    downX[0] = e.getX();
                    downY[0] = e.getY();
                    downTime[0] = System.currentTimeMillis();
                }
                return false;
            }

            @Override
            public void onTouchEvent(RecyclerView rv, MotionEvent e) {
            }

            @Override
            public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
            }
        });
        listView.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                downX[0] = event.getX();
                downY[0] = event.getY();
                downTime[0] = System.currentTimeMillis();
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                float dx = Math.abs(event.getX() - downX[0]);
                float dy = Math.abs(event.getY() - downY[0]);
                long dt = System.currentTimeMillis() - downTime[0];
                boolean atTop = !listView.canScrollVertically(-1);
                boolean inHeader = downY[0] > AndroidUtilities.dp(6) && downY[0] < listView.getPaddingTop() - AndroidUtilities.dp(8);
                if (atTop && inHeader && dx < touchSlop && dy < touchSlop && dt < 450) {
                    WallUi.hapticSelect(v);
                    openWall(fragment, ownerId, ownerName, true);
                    return true;
                }
            }
            return false;
        });
    }

    // ---------- «На стіну» з контекстного меню повідомлення ----------

    public static void addForwardFromMessage(BaseFragment fragment, MessageObject message) {
        if (fragment == null || message == null) return;
        if (!isAvailable()) {
            if (fragment.getParentActivity() != null) {
                BulletinFactory.of(fragment).createSimpleBulletin(org.telegram.messenger.R.raw.info,
                        MiogramLocale.get("Стіна вимкнена", "Стена выключена", "Wall is off")).show();
            }
            return;
        }
        long myId = WallStorage.currentUserId();
        String myName = WallStorage.displayName(myId);
        Wall wall = WallStorage.loadWall(myId, myName, WallStorage.username(myId));
        WallElement existing = WallStorage.findElementOfAuthor(wall, myId);
        if (existing != null) {
            AlertDialog.Builder b = new AlertDialog.Builder(fragment.getParentActivity());
            b.setTitle(MiogramLocale.get("Замінити елемент?", "Заменить элемент?", "Replace element?"));
            b.setMessage(MiogramLocale.get("На стіні вже є твій елемент. Переслане повідомлення замінить його.",
                    "На стене уже есть твой элемент. Пересланное сообщение заменит его.",
                    "You already have an element. The forwarded message will replace it."));
            b.setPositiveButton(MiogramLocale.get("Замінити", "Заменить", "Replace"), (d, w) -> {
                WallStorage.removeElement(wall, existing.id);
                addForwardInternal(fragment, message, wall);
            });
            b.setNegativeButton(MiogramLocale.get("Скасувати", "Отмена", "Cancel"), null);
            fragment.showDialog(b.create());
            return;
        }
        addForwardInternal(fragment, message, wall);
    }

    private static void addForwardInternal(BaseFragment fragment, MessageObject message, Wall wall) {
        long myId = WallStorage.currentUserId();
        WallElement e = WallModels.newElement(WallModels.TYPE_FORWARD, myId,
                WallStorage.displayName(myId), WallStorage.username(myId));
        String text = message.messageOwner != null ? message.messageOwner.message : null;
        if (TextUtils.isEmpty(text)) text = message.caption == null ? null : message.caption.toString();
        if (TextUtils.isEmpty(text)) {
            if (message.isPhoto()) text = "📷 Фото";
            else if (message.isVideo()) text = "🎬 Відео";
            else if (message.isVoice()) text = "🎤 Голосове";
            else if (message.isMusic()) text = "🎵 Музика";
            else if (message.getDocument() != null) text = "📎 " + (message.getDocumentName() == null ? "Файл" : message.getDocumentName());
            else text = "Переслане повідомлення";
        }
        e.text = text;
        String forwarded = message.getForwardedName();
        e.subtitle = forwarded == null ? "" : forwarded;
        e.sourceUrl = "";
        e.size = "full";
        e.style = WallModels.STYLE_NIGHT;
        WallStorage.upsertElement(wall, e);
        AndroidUtilities.runOnUIThread(() -> {
            if (fragment.getParentActivity() != null) {
                BulletinFactory.of(fragment).createSimpleBulletin(org.telegram.messenger.R.raw.info,
                        MiogramLocale.get("Додано на твою стіну ✦", "Добавлено на твою стену ✦", "Added to your wall ✦")).show();
            }
        });
    }
}
