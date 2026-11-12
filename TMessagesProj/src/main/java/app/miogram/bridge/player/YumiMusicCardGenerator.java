package app.miogram.bridge.player;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;

import androidx.core.content.FileProvider;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.ui.Components.BulletinFactory;

import java.io.File;
import java.io.FileOutputStream;

import app.miogram.bridge.MiogramLocale;
import app.miogram.bridge.customui.MiogramHaptic;

/**
 * Generates aesthetic Music Story / Share Cards (9:16 for Stories or 1:1 for chats).
 * Features album art, artist typography, glowing waveform, optional lyric quote, and Yumi branding.
 */
public final class YumiMusicCardGenerator {

    private YumiMusicCardGenerator() {
    }

    public static Bitmap generateStoryCard(MessageObject mo, Bitmap coverBitmap, String lyricQuote) {
        final int W = 1080;
        final int H = 1920;
        Bitmap bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // 1. Fluid Gradient Background
        int colTop = 0xFF141727;
        int colMid = 0xFF2A1C3C;
        int colBot = 0xFF0B0C12;
        if (coverBitmap != null) {
            try {
                int p1 = coverBitmap.getPixel(coverBitmap.getWidth() / 4, coverBitmap.getHeight() / 4);
                int p2 = coverBitmap.getPixel(coverBitmap.getWidth() * 3 / 4, coverBitmap.getHeight() * 3 / 4);
                colTop = 0xFF000000 | p1;
                colMid = 0xFF000000 | p2;
            } catch (Throwable ignore) {
            }
        }

        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setShader(new LinearGradient(0, 0, 0, H, new int[]{colTop, colMid, colBot}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, W, H, bgPaint);

        // 2. Ambient radial glow in background
        Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowPaint.setShader(new LinearGradient(0, 300, W, 900, 0x44FFFFFF & colTop, 0x00000000, Shader.TileMode.CLAMP));
        canvas.drawCircle(W / 2f, 650, 480, glowPaint);

        // 3. Album Art Card
        int artSize = 640;
        int artLeft = (W - artSize) / 2;
        int artTop = 320;
        RectF artRect = new RectF(artLeft, artTop, artLeft + artSize, artTop + artSize);

        Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setColor(0x66000000);
        shadowPaint.setShadowLayer(48, 0, 24, 0x88000000);
        canvas.drawRoundRect(artRect, 48, 48, shadowPaint);

        Paint artPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        if (coverBitmap != null) {
            canvas.save();
            android.graphics.Path path = new android.graphics.Path();
            path.addRoundRect(artRect, 48, 48, android.graphics.Path.Direction.CW);
            canvas.clipPath(path);
            canvas.drawBitmap(coverBitmap, new Rect(0, 0, coverBitmap.getWidth(), coverBitmap.getHeight()), artRect, artPaint);
            canvas.restore();
        } else {
            Paint placeholder = new Paint(Paint.ANTI_ALIAS_FLAG);
            placeholder.setColor(0xFF232736);
            canvas.drawRoundRect(artRect, 48, 48, placeholder);
        }

        // 4. Track Title & Artist
        String title = mo != null ? mo.getMusicTitle() : "Song Title";
        String artist = mo != null ? mo.getMusicAuthor() : "Artist Name";

        TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(0xFFFFFFFF);
        titlePaint.setTextSize(54);
        titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        titlePaint.setTextAlign(Paint.Align.CENTER);

        float titleY = artTop + artSize + 110;
        canvas.drawText(TextUtils.ellipsize(title, titlePaint, W - 160, TextUtils.TruncateAt.END).toString(), W / 2f, titleY, titlePaint);

        TextPaint artistPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        artistPaint.setColor(0xB3FFFFFF);
        artistPaint.setTextSize(38);
        artistPaint.setTypeface(Typeface.DEFAULT);
        artistPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(TextUtils.ellipsize(artist, artistPaint, W - 180, TextUtils.TruncateAt.END).toString(), W / 2f, titleY + 60, artistPaint);

        // 5. Stylized Audio Waveform Bars
        Paint wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        wavePaint.setColor(0xFF5B8DEF);
        wavePaint.setStrokeCap(Paint.Cap.ROUND);
        wavePaint.setStrokeWidth(8);

        float waveY = titleY + 160;
        int barCount = 38;
        float barSpacing = (W - 240) / (float) barCount;
        float startX = 120 + barSpacing / 2f;
        for (int i = 0; i < barCount; i++) {
            float norm = (float) Math.sin((i / (float) barCount) * Math.PI);
            float h = 16 + (float) Math.abs(Math.sin(i * 0.45f) * 44f * norm);
            canvas.drawLine(startX + i * barSpacing, waveY - h, startX + i * barSpacing, waveY + h, wavePaint);
        }

        // 6. Optional Lyric Quote in Glass Capsule
        if (!TextUtils.isEmpty(lyricQuote)) {
            float quoteTop = waveY + 80;
            float quoteW = W - 200;
            float quoteLeft = 100;

            TextPaint quotePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            quotePaint.setColor(0xFFFFFFFF);
            quotePaint.setTextSize(34);
            quotePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.ITALIC));

            StaticLayout sl = new StaticLayout(lyricQuote, quotePaint, (int) quoteW - 60, Layout.Alignment.ALIGN_CENTER, 1.2f, 0, false);
            float capsuleH = sl.getHeight() + 60;
            RectF capsuleRect = new RectF(quoteLeft, quoteTop, quoteLeft + quoteW, quoteTop + capsuleH);

            Paint capsuleBg = new Paint(Paint.ANTI_ALIAS_FLAG);
            capsuleBg.setColor(0x28FFFFFF);
            canvas.drawRoundRect(capsuleRect, 32, 32, capsuleBg);

            canvas.save();
            canvas.translate(quoteLeft + 30, quoteTop + 30);
            sl.draw(canvas);
            canvas.restore();
        }

        // 7. Yumigram Music Branding Badge at Bottom
        Paint badgeBg = new Paint(Paint.ANTI_ALIAS_FLAG);
        badgeBg.setColor(0x33000000);
        RectF badgeRect = new RectF(W / 2f - 180, H - 180, W / 2f + 180, H - 110);
        canvas.drawRoundRect(badgeRect, 35, 35, badgeBg);

        TextPaint badgePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        badgePaint.setColor(0xDDFFFFFF);
        badgePaint.setTextSize(26);
        badgePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        badgePaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("🌸 YUMIGRAM MUSIC", W / 2f, H - 136, badgePaint);

        return bitmap;
    }

    public static File saveCardToCache(Bitmap card) {
        if (card == null) return null;
        try {
            File cacheDir = new File(ApplicationLoader.applicationContext.getCacheDir(), "story_cards");
            if (!cacheDir.exists()) cacheDir.mkdirs();
            File out = new File(cacheDir, "yumi_music_story_" + System.currentTimeMillis() + ".png");
            try (FileOutputStream fos = new FileOutputStream(out)) {
                card.compress(Bitmap.CompressFormat.PNG, 95, fos);
                fos.flush();
            }
            return out;
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    public static void shareCard(Activity activity, MessageObject mo, Bitmap cover, String lyric) {
        if (activity == null || mo == null) return;
        Bitmap card = generateStoryCard(mo, cover, lyric);
        File file = saveCardToCache(card);
        if (file == null || !file.exists()) return;

        try {
            Uri uri = FileProvider.getUriForFile(activity, ApplicationLoader.getApplicationId() + ".provider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("image/png");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.putExtra(Intent.EXTRA_TEXT, mo.getMusicAuthor() + " — " + mo.getMusicTitle() + " 🎶 #Yumigram");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(Intent.createChooser(intent, MiogramLocale.get("Поділитися музичною карткою", "Поделиться музыкальной карточкой", "Share Music Card")));
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }
}
