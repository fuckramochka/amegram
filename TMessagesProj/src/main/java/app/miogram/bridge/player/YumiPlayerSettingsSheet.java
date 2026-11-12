package app.miogram.bridge.player;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import app.amegram.theme.YumiSwitch;
import app.miogram.bridge.MiogramLocale;
import app.miogram.bridge.customui.MiogramHaptic;
import app.miogram.bridge.playlist.YumiPlaylist;
import app.miogram.bridge.playlist.YumiPlaylistManager;
import app.miogram.bridge.playlist.YumiPlaylistSheet;

/**
 * Dedicated, full-fidelity Yumi Audio Player Settings screen.
 * Formatted with canonical Yumi MD3 cards, clean typography, live switches,
 * segmented tabs (Line/Word/Syllable), visual effects, and playlists.
 */
public class YumiPlayerSettingsSheet extends BottomSheet {

    private final Activity activity;
    private final int accentColor;
    private final int surfaceColor;
    private final int cardBgColor;

    public static void show(Activity activity, Theme.ResourcesProvider rp) {
        if (activity == null) return;
        new YumiPlayerSettingsSheet(activity, rp).show();
    }

    public YumiPlayerSettingsSheet(Activity activity, Theme.ResourcesProvider resourcesProvider) {
        super(activity, false, resourcesProvider);
        this.activity = activity;

        setApplyTopPadding(false);
        setApplyBottomPadding(false);

        int bgColor = getThemedColor(Theme.key_dialogBackground);
        if (bgColor == 0) bgColor = 0xFF12141C;
        this.surfaceColor = bgColor;
        fixNavigationBar(surfaceColor);

        int accent = getThemedColor(Theme.key_windowBackgroundWhiteBlueHeader);
        if (accent == 0) accent = 0xFF5B8DEF;
        this.accentColor = accent;

        cardBgColor = ColorUtils.blendARGB(surfaceColor, 0xFFFFFFFF, 0.08f);

        FrameLayout root = new FrameLayout(activity) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int h = (int) (MeasureSpec.getSize(heightMeasureSpec) * 0.88f);
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
            }
        };
        GradientDrawable rootBg = new GradientDrawable();
        rootBg.setColor(surfaceColor);
        rootBg.setCornerRadii(new float[]{
                AndroidUtilities.dp(24), AndroidUtilities.dp(24),
                AndroidUtilities.dp(24), AndroidUtilities.dp(24),
                0, 0, 0, 0
        });
        root.setBackground(rootBg);
        containerView = root;

        // Drag handle
        View handle = new View(activity);
        GradientDrawable handleBg = new GradientDrawable();
        handleBg.setColor(0x44FFFFFF);
        handleBg.setCornerRadius(AndroidUtilities.dp(3));
        handle.setBackground(handleBg);
        root.addView(handle, LayoutHelper.createFrame(36, 4, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 10, 0, 0));

        // Header Title
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(22), AndroidUtilities.dp(20), AndroidUtilities.dp(8));

        TextView title = new TextView(activity);
        title.setText(MiogramLocale.get("Налаштування плеера Юми", "Настройки плеера Юми", "Yumi Player Settings"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(0xFFFFFFFF);
        header.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView sub = new TextView(activity);
        sub.setText(MiogramLocale.get("Караоке, візуальні ефекти, вініл, кросфейд та плейлисти", "Караоке, визуальные эффекты, винил, кроссфейд и плейлисты", "Karaoke, visual effects, vinyl, crossfade & playlists"));
        sub.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        sub.setTextColor(0x99FFFFFF);
        header.addView(sub, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 3, 0, 4));
        root.addView(header, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        // Scrollable content
        ScrollView scroll = new ScrollView(activity);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(8), AndroidUtilities.dp(16), AndroidUtilities.dp(32));

        // --- CARD 1: LYRICS & KARAOKE ---
        LinearLayout lyricsCard = createCard(activity);
        addCardHeader(lyricsCard, activity, R.drawable.msg_theme, MiogramLocale.get("Тексти та Караоке", "Тексты и Караоке", "Lyrics & Karaoke"));

        // Segmented Karaoke Mode (Line / Word / Syllable)
        addSegmentedControl(lyricsCard, activity, MiogramLocale.get("Режим відображення", "Режим отображения", "Display Mode"),
                new String[]{
                        MiogramLocale.get("Построчно", "Построчно", "Line"),
                        MiogramLocale.get("Пословно", "Пословно", "Word"),
                        MiogramLocale.get("Послогово", "Послогово", "Syllable")
                }, MiogramPlayerPrefs.getLyricsKaraokeMode(), mode -> {
                    MiogramPlayerPrefs.setLyricsKaraokeMode(mode);
                });

        addSwitchRow(lyricsCard, activity, MiogramLocale.get("Неонове сяйво активного тексту", "Неоновое сияние активного текста", "Active text neon glow"),
                MiogramLocale.get("М'яке підсвічування тексту кольором обкладинки", "Мягкая подсветка текста цветом обложки", "Soft highlight with cover art accent"),
                MiogramPlayerPrefs.isLyricsActiveGlow(), checked -> {
                    MiogramPlayerPrefs.setLyricsActiveGlow(checked);
                });

        // Offset Quick Adjust
        addOffsetAdjustRow(lyricsCard, activity);

        content.addView(lyricsCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));

        // --- CARD 2: VISUAL EFFECTS & VINYL ---
        LinearLayout visualCard = createCard(activity);
        addCardHeader(visualCard, activity, R.drawable.msg_theme, MiogramLocale.get("Візуальні ефекти", "Визуальные эффекты", "Visual Effects"));

        addSwitchRow(visualCard, activity, MiogramLocale.get("Живий градієнт Fluid Aura", "Живой градиент Fluid Aura", "Fluid Mesh Aura background"),
                MiogramLocale.get("Плавне перетікання кольорових хвиль обкладинки на фоні", "Плавное перетекание цветовых волн обложки на фоне", "Dynamic morphing mesh gradient background"),
                MiogramPlayerPrefs.isFluidAuraEnabled(), checked -> {
                    MiogramPlayerPrefs.setFluidAuraEnabled(checked);
                });

        addSwitchRow(visualCard, activity, MiogramLocale.get("Режим вінілової платівки (Vinyl 2.0)", "Режим виниловой пластинки (Vinyl 2.0)", "Vinyl 2.0 Mode"),
                MiogramLocale.get("Реалістична обертова платівка з канавками", "Реалистичная вращающаяся пластинка с канавками", "Spinning vinyl record with realistic grooves"),
                MiogramPlayerPrefs.isVinylModeEnabled(), checked -> {
                    MiogramPlayerPrefs.setVinylModeEnabled(checked);
                });

        addSwitchRow(visualCard, activity, MiogramLocale.get("Анімована голка (тонарм)", "Анимированная игла (тонарм)", "Animated tonearm"),
                MiogramLocale.get("Голка фізично опускається на платівку при відтворенні", "Игла физично опускается на пластинку при старте", "Needle lowers onto record on play and lifts on pause"),
                MiogramPlayerPrefs.isVinylArmEnabled(), checked -> {
                    MiogramPlayerPrefs.setVinylArmEnabled(checked);
                });

        addSwitchRow(visualCard, activity, MiogramLocale.get("3D-перегортання обкладинок", "3D-переворот обложек", "3D Cover Transition"),
                MiogramLocale.get("Об'ємна перспективна анімація при свайпі треку", "Объёмная перспективная анимация при свайпе трека", "Perspective 3D flip animation when swiping tracks"),
                MiogramPlayerPrefs.isCover3dEnabled(), checked -> {
                    MiogramPlayerPrefs.setCover3dEnabled(checked);
                });

        content.addView(visualCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));

        // --- CARD 3: MINI-PLAYER (DYNAMIC ISLAND) ---
        LinearLayout miniCard = createCard(activity);
        addCardHeader(miniCard, activity, R.drawable.msg_playback_play, MiogramLocale.get("Міні-плеєр", "Мини-плеер", "Mini-Player"));

        addSwitchRow(miniCard, activity, MiogramLocale.get("Dynamic Island капсула", "Dynamic Island капсула", "Dynamic Island pill"),
                MiogramLocale.get("Паряща закруглена форма 26dp над нижньою навігацією", "Парящая закруглённая форма 26dp над нижней навигацией", "Floating rounded 26dp capsule above navigation"),
                MiogramPlayerPrefs.isDynamicIslandMiniPlayer(), checked -> {
                    MiogramPlayerPrefs.setDynamicIslandMiniPlayer(checked);
                });

        addSwitchRow(miniCard, activity, MiogramLocale.get("Жести керування", "Жесты управления", "Swipe Gestures"),
                MiogramLocale.get("Свайп вліво/вправо — треки, свайп вгору — розгорнути", "Свайп влево/вправо — треки, свайп вверх — развернуть", "Swipe left/right to skip, swipe up to expand"),
                MiogramPlayerPrefs.isMiniPlayerGesturesEnabled(), checked -> {
                    MiogramPlayerPrefs.setMiniPlayerGesturesEnabled(checked);
                });

        content.addView(miniCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));

        // --- CARD 4: SOUND & PLAYBACK ---
        LinearLayout soundCard = createCard(activity);
        addCardHeader(soundCard, activity, R.drawable.baseline_music_note_24, MiogramLocale.get("Звук та відтворення", "Звук и воспроизведение", "Sound & Playback"));

        addSegmentedControl(soundCard, activity, MiogramLocale.get("Кросфейд (плавний перехід)", "Кроссфейд (плавный переход)", "Crossfade"),
                new String[]{"0s", "2s", "4s", "6s", "8s", "12s"},
                getCrossfadeIndex(MiogramPlayerPrefs.getCrossfadeDurationSec()), idx -> {
                    int[] secValues = {0, 2, 4, 6, 8, 12};
                    MiogramPlayerPrefs.setCrossfadeDurationSec(secValues[idx]);
                });

        addSwitchRow(soundCard, activity, MiogramLocale.get("Пропуск тиші (Skip Silence)", "Пропуск тишины (Skip Silence)", "Skip Silence"),
                MiogramLocale.get("Автоматичний пропуск пауз на початку та в кінці файлу", "Автоматический пропуск пауз в начале и конце файла", "Automatically skip silence at start and end of tracks"),
                MiogramPlayerPrefs.isSkipSilenceEnabled(), checked -> {
                    MiogramPlayerPrefs.setSkipSilenceEnabled(checked);
                });

        addSwitchRow(soundCard, activity, MiogramLocale.get("Запам'ятовувати трек і час", "Запоминать трек и время", "Remember track & position"),
                MiogramLocale.get("Зберігати позицію секунди і чергу після перезапуску", "Сохранять позицию секунды и очередь после перезапуска", "Preserve playback position and queue across app restarts"),
                MiogramPlayerPrefs.isResumePlaybackEnabled(), checked -> {
                    MiogramPlayerPrefs.setResumePlaybackEnabled(checked);
                });

        content.addView(soundCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));

        // --- CARD 5: PLAYLISTS ---
        LinearLayout plCard = createCard(activity);
        addCardHeader(plCard, activity, R.drawable.msg_list, MiogramLocale.get("Плейлисти .yumi", "Плейлисты .yumi", "Yumi Playlists"));

        addClickButtonRow(plCard, activity, MiogramLocale.get("Відкрити «Мої плейлисти»", "Открыть «Мои плейлисты»", "Open My Playlists"),
                MiogramLocale.get("Керувати збереженими треками, порядок та експорт", "Управлять сохранёнными треками, порядок и экспорт", "Manage collected tracks, reordering & export"), v -> {
                    dismiss();
                    java.util.List<YumiPlaylist> all = YumiPlaylistManager.getAllPlaylists();
                    if (!all.isEmpty()) {
                        YumiPlaylistSheet.showPlaylist(activity, all.get(0), resourcesProvider);
                    } else {
                        YumiPlaylist empty = new YumiPlaylist(MiogramLocale.get("Мій плейлист", "Мой плейлист", "My Playlist"), "Yumigram");
                        YumiPlaylistManager.savePlaylist(empty);
                        YumiPlaylistSheet.showPlaylist(activity, empty, resourcesProvider);
                    }
                });

        content.addView(plCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        scroll.addView(content);
        FrameLayout.LayoutParams scrollLp = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP);
        scrollLp.topMargin = AndroidUtilities.dp(78);
        root.addView(scroll, scrollLp);
    }

    private int getCrossfadeIndex(int sec) {
        if (sec <= 0) return 0;
        if (sec <= 2) return 1;
        if (sec <= 4) return 2;
        if (sec <= 6) return 3;
        if (sec <= 8) return 4;
        return 5;
    }

    private LinearLayout createCard(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16));
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(cardBgColor);
        gd.setCornerRadius(AndroidUtilities.dp(20));
        card.setBackground(gd);
        return card;
    }

    private void addCardHeader(LinearLayout card, Context context, int iconRes, String title) {
        LinearLayout h = new LinearLayout(context);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setPadding(0, 0, 0, AndroidUtilities.dp(12));

        ImageView icon = new ImageView(context);
        icon.setImageResource(iconRes);
        icon.setColorFilter(new PorterDuffColorFilter(accentColor, PorterDuff.Mode.SRC_IN));
        h.addView(icon, LayoutHelper.createLinear(22, 22, 0, 0, 10, 0));

        TextView tv = new TextView(context);
        tv.setText(title);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        tv.setTypeface(AndroidUtilities.bold());
        tv.setTextColor(0xFFFFFFFF);
        h.addView(tv, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        card.addView(h);
    }

    private void addSwitchRow(LinearLayout card, Context context, String title, String subtitle, boolean initialChecked, SwitchCallback callback) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));

        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView t = new TextView(context);
        t.setText(title);
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        t.setTypeface(AndroidUtilities.bold());
        t.setTextColor(0xFFFFFFFF);
        textCol.addView(t);

        if (!TextUtils.isEmpty(subtitle)) {
            TextView s = new TextView(context);
            s.setText(subtitle);
            s.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
            s.setTextColor(0x99FFFFFF);
            textCol.addView(s, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        }
        row.addView(textCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, 0, 0, 12, 0));

        YumiSwitch sw = new YumiSwitch(context);
        sw.setChecked(initialChecked, false);
        sw.setOnCheckedChangeListener((btn, isChecked) -> {
            MiogramHaptic.click(sw);
            callback.onChanged(isChecked);
        });
        row.addView(sw, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        row.setOnClickListener(v -> sw.setChecked(!sw.isChecked(), true));
        card.addView(row);
    }

    private void addSegmentedControl(LinearLayout card, Context context, String label, String[] options, int selectedIndex, SegmentCallback callback) {
        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setPadding(0, AndroidUtilities.dp(6), 0, AndroidUtilities.dp(8));

        TextView l = new TextView(context);
        l.setText(label);
        l.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        l.setTextColor(0xCCFFFFFF);
        l.setPadding(0, 0, 0, AndroidUtilities.dp(8));
        wrapper.addView(l);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable rowBg = new GradientDrawable();
        rowBg.setColor(0x18FFFFFF);
        rowBg.setCornerRadius(AndroidUtilities.dp(14));
        row.setBackground(rowBg);
        row.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4));

        final TextView[] buttons = new TextView[options.length];
        for (int i = 0; i < options.length; i++) {
            final int idx = i;
            TextView btn = new TextView(context);
            btn.setText(options[i]);
            btn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            btn.setTypeface(AndroidUtilities.bold());
            btn.setGravity(Gravity.CENTER);
            btn.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));

            boolean active = (i == selectedIndex);
            updateSegmentBtn(btn, active);

            btn.setOnClickListener(v -> {
                MiogramHaptic.click(v);
                for (int j = 0; j < buttons.length; j++) {
                    updateSegmentBtn(buttons[j], j == idx);
                }
                callback.onSelected(idx);
            });
            buttons[i] = btn;
            row.addView(btn, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));
        }
        wrapper.addView(row);
        card.addView(wrapper);
    }

    private void updateSegmentBtn(TextView btn, boolean active) {
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(AndroidUtilities.dp(10));
        if (active) {
            gd.setColor(accentColor);
            btn.setTextColor(0xFFFFFFFF);
        } else {
            gd.setColor(0);
            btn.setTextColor(0xAAFFFFFF);
        }
        btn.setBackground(gd);
    }

    private void addOffsetAdjustRow(LinearLayout card, Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(4));

        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(context);
        title.setText(MiogramLocale.get("Синхронізація слів (LRC Offset)", "Синхронизация слов (LRC Offset)", "Lyrics sync offset"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        title.setTextColor(0xCCFFFFFF);
        textCol.addView(title);

        TextView valTv = new TextView(context);
        long offset = MiogramPlayerPrefs.getLyricsOffsetMs();
        valTv.setText(String.format(java.util.Locale.US, "%+.1fs", offset / 1000f));
        valTv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        valTv.setTextColor(accentColor);
        textCol.addView(valTv);

        row.addView(textCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));

        // Minus button (-0.5s)
        TextView minus = createSmallPill(context, "-0.5s", v -> {
            MiogramHaptic.click(v);
            long cur = MiogramPlayerPrefs.getLyricsOffsetMs() - 500L;
            MiogramPlayerPrefs.setLyricsOffsetMs(cur);
            valTv.setText(String.format(java.util.Locale.US, "%+.1fs", cur / 1000f));
        });
        row.addView(minus, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 32, 0, 0, 6, 0));

        // Reset button (0s)
        TextView reset = createSmallPill(context, "0s", v -> {
            MiogramHaptic.click(v);
            MiogramPlayerPrefs.setLyricsOffsetMs(0L);
            valTv.setText("+0.0s");
        });
        row.addView(reset, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 32, 0, 0, 6, 0));

        // Plus button (+0.5s)
        TextView plus = createSmallPill(context, "+0.5s", v -> {
            MiogramHaptic.click(v);
            long cur = MiogramPlayerPrefs.getLyricsOffsetMs() + 500L;
            MiogramPlayerPrefs.setLyricsOffsetMs(cur);
            valTv.setText(String.format(java.util.Locale.US, "%+.1fs", cur / 1000f));
        });
        row.addView(plus, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 32));

        card.addView(row);
    }

    private TextView createSmallPill(Context context, String text, View.OnClickListener click) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextColor(0xFFFFFFFF);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        tv.setTypeface(AndroidUtilities.bold());
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10), 0);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(0x28FFFFFF);
        gd.setCornerRadius(AndroidUtilities.dp(10));
        tv.setBackground(gd);
        tv.setOnClickListener(click);
        return tv;
    }

    private void addClickButtonRow(LinearLayout card, Context context, String title, String subtitle, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(4));

        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView t = new TextView(context);
        t.setText(title);
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        t.setTypeface(AndroidUtilities.bold());
        t.setTextColor(accentColor);
        textCol.addView(t);

        if (!TextUtils.isEmpty(subtitle)) {
            TextView s = new TextView(context);
            s.setText(subtitle);
            s.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
            s.setTextColor(0x99FFFFFF);
            textCol.addView(s, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        }
        row.addView(textCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));

        ImageView arrow = new ImageView(context);
        arrow.setImageResource(R.drawable.msg_arrowright);
        arrow.setColorFilter(new PorterDuffColorFilter(0x66FFFFFF, PorterDuff.Mode.SRC_IN));
        row.addView(arrow, LayoutHelper.createLinear(20, 20));

        row.setOnClickListener(click);
        card.addView(row);
    }

    private interface SwitchCallback {
        void onChanged(boolean checked);
    }

    private interface SegmentCallback {
        void onSelected(int index);
    }
}
