package app.amegram.hot.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.Switch;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;
import java.util.List;

import app.amegram.hot.HotModulesManager;
import app.amegram.hot.api.HotHost;
import app.amegram.hot.api.HotModule;
import app.amegram.hot.api.HotRow;
import app.amegram.theme.YumiTheme;
import app.miogram.bridge.MiogramLocale;

/**
 * Вкладка настроек включённого модуля: хост рисует HotRow'ы модуля,
 * свитчи хранит сам, модуль узнаёт об изменениях через колбэки.
 */
public class HotModuleSettingsActivity extends BaseFragment {

    private String moduleId;
    private UniversalRecyclerView listView;
    private final List<HotRow> rows = new ArrayList<>();

    public HotModuleSettingsActivity() {
        this.moduleId = "";
    }

    public HotModuleSettingsActivity(String moduleId) {
        this.moduleId = moduleId != null ? moduleId : "";
    }

    private String resolveTitle() {
        HotModulesManager.Handle handle = HotModulesManager.getHandle(moduleId);
        if (handle != null && handle.instance != null) {
            String t = handle.instance.settingsTitle();
            if (t != null && !t.isEmpty()) return t;
        }
        HotModule modInst = HotModulesManager.getModuleForSettings(moduleId);
        if (modInst != null) {
            String t = modInst.settingsTitle();
            if (t != null && !t.isEmpty()) return t;
        }
        if ("settings_ui".equals(moduleId)) {
            return MiogramLocale.get("Інтерфейс налаштувань Yumi", "Интерфейс настроек Yumi", "Yumi Settings UI");
        }
        if ("ghost".equals(moduleId)) {
            return MiogramLocale.get("Режим привида", "Режим призрака", "Ghost mode");
        }
        if ("vault".equals(moduleId)) {
            return MiogramLocale.get("Хмарне сховище", "Облачное хранилище", "Cloud vault");
        }
        if ("player".equals(moduleId)) {
            return MiogramLocale.get("Музичний плеєр", "Музыкальный плеер", "Music player");
        }
        if ("ame".equals(moduleId)) {
            return MiogramLocale.get("Кастомізація", "Кастомизация", "Customization");
        }
        if ("tiktok".equals(moduleId)) {
            return "TikTok MI";
        }
        if ("ai".equals(moduleId)) {
            return MiogramLocale.get("Штучний інтелект", "Искусственный интеллект", "Artificial Intelligence");
        }
        if ("experimental".equals(moduleId)) {
            return MiogramLocale.get("Експерименти", "Эксперименты", "Experimental");
        }
        if ("automation".equals(moduleId)) {
            return MiogramLocale.get("Автоматизація", "Автоматизация", "Automation");
        }
        if ("ui".equals(moduleId)) {
            return MiogramLocale.get("Інтерфейс Yumi", "Интерфейс Yumi", "Yumi Interface");
        }
        if ("fileorganization".equals(moduleId)) {
            return MiogramLocale.get("Організація файлів", "Организация файлов", "File Organization");
        }
        return moduleId;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(resolveTitle());
        actionBar.setActionBarMenuOnItemClick(new org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });
        fragmentView = listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, null);
        fragmentView.setBackgroundColor(org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_windowBackgroundGray));
        listView.setSections();
        return fragmentView;
    }

    private HotModulesManager.Handle handle() {
        return HotModulesManager.getHandle(moduleId);
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        rows.clear();
        Context context = getContext();
        if (context == null) return;

        boolean isEnabled = HotModulesManager.isModuleEnabled(moduleId);
        items.add(UItem.asCustom(buildHeroCard(context, isEnabled)));

        if (!isEnabled) {
            items.add(UItem.asCustom(buildInfoCard(context, MiogramLocale.get(
                    "Модуль наразі вимкнено. Увімкніть його перемикачем угорі, щоб налаштувати параметри.",
                    "Модуль сейчас выключен. Включите его переключателем вверху, чтобы настроить параметры.",
                    "Module is currently disabled. Enable it with the switch above to configure settings."))));
            return;
        }

        HotModulesManager.Handle h = handle();
        HotModule mod = (h != null && h.instance != null) ? h.instance : HotModulesManager.getModuleForSettings(moduleId);
        if (mod == null) {
            items.add(UItem.asCustom(buildInfoCard(context, MiogramLocale.get(
                    "Модуль не знайдено або він видалений.",
                    "Модуль не найден или удалён.",
                    "Module not found or deleted."))));
            return;
        }

        HotHost host = HotModulesManager.hostFor(moduleId);
        try {
            mod.fillSettings(rows);
        } catch (Throwable ignore) {
        }
        // Блок 5: точка входу в тексти пісень прямо з налаштувань player.
        // AI_WORD шлях існує (MiogramLyricsEngine.SOURCE_AI_WORD + MiogramLyricsView.transcribeWithAiWordTimed) — не чіпаємо, тільки вхід.
        if ("player".equals(moduleId)) {
            try {
                if (!hasRowId("player_lyrics_open")) {
                    rows.add(HotRow.button("player_lyrics_open",
                            MiogramLocale.get("Тексти пісень (LRC + ШІ)", "Тексты песен (LRC + ИИ)", "Lyrics (LRC + AI)"),
                            MiogramLocale.get("LRCLib • NetEase • ШІ-розшифровка через MiogramLyricsView", "LRCLib • NetEase • ИИ-расшифровка через MiogramLyricsView", "LRCLib • NetEase • AI transcription via MiogramLyricsView")));
                }
                if (!hasRowId("player_lyrics_ai")) {
                    rows.add(HotRow.button("player_lyrics_ai",
                            MiogramLocale.get("Розшифрувати через ШІ", "Расшифровать через ИИ", "Transcribe with AI"),
                            MiogramLocale.get("Gemini AI → слова з таймінгами (AI_WORD)", "Gemini AI → слова с таймингами (AI_WORD)", "Gemini AI → lyrics with timestamps (AI_WORD)")));
                }
            } catch (Throwable ignore) {
            }
        }
        if ("ai".equals(moduleId)) {
            try {
                if (!hasRowId("ai_providers_open")) {
                    rows.add(HotRow.button("ai_providers_open",
                            MiogramLocale.get("Провайдери та моделі ШІ", "Провайдеры и модели ИИ", "AI Providers & Models"),
                            MiogramLocale.get("Ключі API Gemini, вибір моделі та розпізнавання", "Ключи API Gemini, выбор модели и распознавание", "Gemini API keys, model selection & transcription")));
                }
                if (!hasRowId("ai_companion_open")) {
                    rows.add(HotRow.button("ai_companion_open",
                            MiogramLocale.get("ШІ-компаньйон (Ame / KAngel)", "ИИ-компаньон (Ame / KAngel)", "AI Companion (Ame / KAngel)"),
                            MiogramLocale.get("Персона, стиль відповідей та пам'ять", "Персона, стиль ответов и память", "Persona, tone and memory")));
                }
            } catch (Throwable ignore) {
            }
        }
        if ("vault".equals(moduleId)) {
            try {
                if (!hasRowId("vault_manager_open")) {
                    rows.add(HotRow.button("vault_manager_open",
                            MiogramLocale.get("Відкрити хмарне сховище", "Открыть облачное хранилище", "Open Cloud Vault"),
                            MiogramLocale.get("Файловий менеджер, захищене сховище", "Файловый менеджер, защищённое хранилище", "File manager, secure storage")));
                }
            } catch (Throwable ignore) {
            }
        }
        if ("automation".equals(moduleId)) {
            try {
                if (!hasRowId("automation_account_open")) {
                    rows.add(HotRow.button("automation_account_open",
                            MiogramLocale.get("Автоматизація акаунта", "Автоматизация аккаунта", "Account Automation"),
                            MiogramLocale.get("Юзербот, Heroku, авто-дії", "Юзербот, Heroku, авто-действия", "Userbot, Heroku, auto-actions")));
                }
            } catch (Throwable ignore) {
            }
        }
        if ("tiktok".equals(moduleId)) {
            try {
                if (!hasRowId("tiktok_ecosystem_open")) {
                    rows.add(HotRow.button("tiktok_ecosystem_open",
                            MiogramLocale.get("Екосистема TikTok MI", "Экосистема TikTok MI", "TikTok MI ecosystem"),
                            MiogramLocale.get("Підключення акаунта та інтеграція", "Подключение аккаунта и интеграция", "Account linking and integration")));
                }
            } catch (Throwable ignore) {
            }
        }
        if (rows.isEmpty()) {
            items.add(UItem.asCustom(buildInfoCard(context, MiogramLocale.get(
                    "Немає додаткових налаштувань.", "Нет дополнительных настроек.", "No additional settings."))));
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            HotRow r = rows.get(i);
            if (r.type == HotRow.HEADER) {
                items.add(UItem.asCustom(buildSectionHeader(context, r.title)));
            } else if (r.type == HotRow.INFO) {
                items.add(UItem.asCustom(buildInfoCard(context, r.title)));
            } else if (r.type == HotRow.SWITCH) {
                View v = buildSwitchCard(context, host, mod, r, i);
                applySegmentedShape(v, i);
                items.add(UItem.asCustom(v));
            } else if (r.type == HotRow.BUTTON) {
                View v = buildButtonCard(context, mod, r, i);
                applySegmentedShape(v, i);
                items.add(UItem.asCustom(v));
            } else if (r.type == HotRow.INPUT) {
                View v = buildInputCard(context, host, mod, r, i);
                applySegmentedShape(v, i);
                items.add(UItem.asCustom(v));
            } else if (r.type == HotRow.SLIDER) {
                View v = buildSliderCard(context, host, mod, r, i);
                applySegmentedShape(v, i);
                items.add(UItem.asCustom(v));
            } else if (r.type == HotRow.CHOICE) {
                View v = buildChoiceCard(context, host, mod, r, i);
                applySegmentedShape(v, i);
                items.add(UItem.asCustom(v));
            } else if (r.type == HotRow.COLOR) {
                View v = buildColorCard(context, host, mod, r, i);
                applySegmentedShape(v, i);
                items.add(UItem.asCustom(v));
            }
        }
    }

    private boolean hasRowId(String id) {
        for (HotRow r : rows) {
            if (r != null && id.equals(r.id)) return true;
        }
        return false;
    }

    private boolean isSettingRow(int index) {
        if (index < 0 || index >= rows.size()) return false;
        HotRow r = rows.get(index);
        return r != null && r.type != HotRow.HEADER && r.type != HotRow.INFO;
    }

    private void applySegmentedShape(View card, int index) {
        boolean first = !isSettingRow(index - 1);
        boolean last = !isSettingRow(index + 1);
        float r = AndroidUtilities.dp(16);
        float[] radii;
        int topM = first ? AndroidUtilities.dp(4) : 0;
        int botM = last ? AndroidUtilities.dp(6) : 0;
        if (first && last) {
            radii = new float[]{r, r, r, r, r, r, r, r};
        } else if (first) {
            radii = new float[]{r, r, r, r, 0, 0, 0, 0};
        } else if (last) {
            radii = new float[]{0, 0, 0, 0, r, r, r, r};
        } else {
            radii = new float[]{0, 0, 0, 0, 0, 0, 0, 0};
        }
        card.setBackground(YumiTheme.buttonRipple(YumiTheme.getSurfaceContainer(), radii));
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) card.getLayoutParams();
        if (lp != null) {
            lp.setMargins(AndroidUtilities.dp(14), topM, AndroidUtilities.dp(14), botM);
            card.setLayoutParams(lp);
        }
    }

    private View buildHeroCard(Context context, boolean isEnabled) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(YumiTheme.cardBackground(YumiTheme.RADIUS_LG));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(AndroidUtilities.dp(14), AndroidUtilities.dp(8), AndroidUtilities.dp(14), AndroidUtilities.dp(6));
        card.setLayoutParams(lp);
        card.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16));

        LinearLayout topRow = new LinearLayout(context);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        int modColor = HotModuleMeta.color(moduleId);
        FrameLayout iconFrame = new FrameLayout(context);
        iconFrame.setBackground(YumiTheme.squircleIconBackground(modColor));
        ImageView iconView = new ImageView(context);
        iconView.setImageResource(HotModuleMeta.icon(moduleId));
        iconView.setColorFilter(0xFFFFFFFF);
        iconFrame.addView(iconView, LayoutHelper.createFrame(26, 26, Gravity.CENTER));
        topRow.addView(iconFrame, LayoutHelper.createLinear(48, 48, Gravity.CENTER_VERTICAL, 0, 0, 14, 0));

        LinearLayout infoCol = new LinearLayout(context);
        infoCol.setOrientation(LinearLayout.VERTICAL);

        TextView titleView = new TextView(context);
        titleView.setText(resolveTitle());
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        infoCol.addView(titleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 4));

        TextView badgeView = new TextView(context);
        badgeView.setText(isEnabled
                ? MiogramLocale.get("● Активно", "● Активно", "● Active")
                : MiogramLocale.get("○ Вимкнено", "○ Выключено", "○ Disabled"));
        badgeView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11.5f);
        badgeView.setTypeface(AndroidUtilities.bold());
        badgeView.setTextColor(isEnabled ? 0xFF4CAF50 : Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setCornerRadius(AndroidUtilities.dp(99));
        badgeBg.setColor(isEnabled ? 0x224CAF50 : 0x18888888);
        badgeView.setBackground(badgeBg);
        badgeView.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(3), AndroidUtilities.dp(8), AndroidUtilities.dp(3));
        infoCol.addView(badgeView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        topRow.addView(infoCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        Switch sw = new Switch(context);
        sw.setChecked(isEnabled, false);
        sw.setColors(Theme.key_switchTrack, Theme.key_switchTrackChecked, Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundWhite);
        topRow.addView(sw, LayoutHelper.createLinear(42, 28, Gravity.CENTER_VERTICAL, 8, 0, 0, 0));

        card.addView(topRow);

        String desc = HotModuleMeta.fallbackDescription(moduleId);
        if (desc != null && !desc.isEmpty()) {
            TextView descView = new TextView(context);
            descView.setText(desc);
            descView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            descView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            descView.setLineSpacing(AndroidUtilities.dp(2), 1f);
            card.addView(descView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0));
        }

        View.OnClickListener toggle = v -> {
            card.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            boolean next = !HotModulesManager.isModuleEnabled(moduleId);
            sw.setChecked(next, true);
            HotModulesManager.setEnabled(moduleId, next, (ok, msg, data) -> {
                if (actionBar != null) actionBar.setTitle(resolveTitle());
                if (listView != null && listView.adapter != null) {
                    listView.adapter.update(true);
                }
            });
        };
        card.setOnClickListener(toggle);
        sw.setOnClickListener(toggle);
        ScaleStateListAnimator.apply(card, 0.02f, 1.4f);

        return card;
    }

    private View buildSectionHeader(Context context, String title) {
        TextView tv = new TextView(context);
        tv.setText(title != null ? title.toUpperCase() : "");
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f);
        tv.setTypeface(AndroidUtilities.bold());
        tv.setTextColor(YumiTheme.getPrimary());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(AndroidUtilities.dp(18), AndroidUtilities.dp(16), AndroidUtilities.dp(18), AndroidUtilities.dp(6));
        tv.setLayoutParams(lp);
        return tv;
    }

    private View buildSwitchCard(Context context, HotHost host, HotModule mod, HotRow r, int index) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setMinimumHeight(AndroidUtilities.dp(68));
        card.setBackground(YumiTheme.buttonRipple(YumiTheme.getSurfaceContainer(), 18));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(AndroidUtilities.dp(14), AndroidUtilities.dp(4), AndroidUtilities.dp(14), AndroidUtilities.dp(4));
        card.setLayoutParams(lp);
        card.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12), AndroidUtilities.dp(14), AndroidUtilities.dp(12));

        FrameLayout iconFrame = new FrameLayout(context);
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setCornerRadius(AndroidUtilities.dp(12));
        iconBg.setColor(Theme.multAlpha(YumiTheme.getPrimary(), 0.12f));
        iconFrame.setBackground(iconBg);

        ImageView iconView = new ImageView(context);
        iconView.setImageResource(resolveRowIcon(r));
        iconView.setColorFilter(YumiTheme.getPrimary());
        iconFrame.addView(iconView, LayoutHelper.createFrame(20, 20, Gravity.CENTER));
        card.addView(iconFrame, LayoutHelper.createLinear(38, 38, Gravity.CENTER_VERTICAL, 0, 0, 14, 0));

        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView titleView = new TextView(context);
        titleView.setText(r.title);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15.5f);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textCol.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        if (r.subtitle != null && !r.subtitle.isEmpty()) {
            TextView subView = new TextView(context);
            subView.setText(r.subtitle);
            subView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
            subView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            subView.setLineSpacing(AndroidUtilities.dp(1.5f), 1f);
            textCol.addView(subView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        }

        card.addView(textCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, 10, 0));

        boolean checked;
        try {
            checked = host.getBool(r.id, r.checked);
        } catch (Throwable ignore) {
            checked = r.checked;
        }
        Switch sw = new Switch(context);
        sw.setChecked(checked, false);
        sw.setColors(Theme.key_switchTrack, Theme.key_switchTrackChecked, Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundWhite);
        card.addView(sw, LayoutHelper.createLinear(40, 26, Gravity.CENTER_VERTICAL));

        View.OnClickListener clickAction = v -> {
            card.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            boolean cur = host.getBool(r.id, r.checked);
            boolean next = !cur;
            host.putBool(r.id, next);
            sw.setChecked(next, true);
            try {
                mod.onSettingsToggle(r.id, next);
            } catch (Throwable ignore) {
            }
            if ("ui".equals(moduleId)) {
                try {
                    app.amegram.hot.HotUiGate.applyLive();
                } catch (Throwable ignore) {
                }
            }
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        };
        card.setOnClickListener(clickAction);
        sw.setOnClickListener(clickAction);
        ScaleStateListAnimator.apply(card, 0.025f, 1.4f);

        return card;
    }

    private View buildButtonCard(Context context, HotModule mod, HotRow r, int index) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setMinimumHeight(AndroidUtilities.dp(60));
        card.setBackground(YumiTheme.buttonRipple(YumiTheme.getSurfaceContainer(), 18));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(AndroidUtilities.dp(14), AndroidUtilities.dp(5), AndroidUtilities.dp(14), AndroidUtilities.dp(5));
        card.setLayoutParams(lp);
        card.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12), AndroidUtilities.dp(14), AndroidUtilities.dp(12));

        FrameLayout iconFrame = new FrameLayout(context);
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setCornerRadius(AndroidUtilities.dp(12));
        iconBg.setColor(YumiTheme.getPrimary());
        iconFrame.setBackground(iconBg);

        ImageView iconView = new ImageView(context);
        iconView.setImageResource(resolveRowIcon(r));
        iconView.setColorFilter(0xFFFFFFFF);
        iconFrame.addView(iconView, LayoutHelper.createFrame(20, 20, Gravity.CENTER));
        card.addView(iconFrame, LayoutHelper.createLinear(40, 40, Gravity.CENTER_VERTICAL, 0, 0, 14, 0));

        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView titleView = new TextView(context);
        titleView.setText(r.title);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15.5f);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textCol.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        if (r.subtitle != null && !r.subtitle.isEmpty()) {
            TextView subView = new TextView(context);
            subView.setText(r.subtitle);
            subView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
            subView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            subView.setLineSpacing(AndroidUtilities.dp(1.5f), 1f);
            textCol.addView(subView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        }

        card.addView(textCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, 10, 0));

        ImageView arrowView = new ImageView(context);
        arrowView.setImageResource(R.drawable.arrow_more);
        arrowView.setColorFilter(YumiTheme.getPrimary());
        card.addView(arrowView, LayoutHelper.createLinear(16, 16, Gravity.CENTER_VERTICAL));

        card.setOnClickListener(v -> {
            card.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (handleCustomAction(r)) {
                return;
            }
            try {
                mod.onSettingsAction(r.id);
            } catch (Throwable ignore) {
            }
            if ("ui".equals(moduleId)) {
                try {
                    app.amegram.hot.HotUiGate.applyLive();
                } catch (Throwable ignore) {
                }
            }
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        });
        ScaleStateListAnimator.apply(card, 0.035f, 1.5f);

        return card;
    }

    private View buildInputCard(Context context, HotHost host, HotModule mod, HotRow r, int index) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setMinimumHeight(AndroidUtilities.dp(62));
        card.setBackground(YumiTheme.buttonRipple(YumiTheme.getSurfaceContainer(), 18));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(AndroidUtilities.dp(14), AndroidUtilities.dp(4), AndroidUtilities.dp(14), AndroidUtilities.dp(4));
        card.setLayoutParams(lp);
        card.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12), AndroidUtilities.dp(14), AndroidUtilities.dp(12));

        FrameLayout iconFrame = new FrameLayout(context);
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setCornerRadius(AndroidUtilities.dp(12));
        iconBg.setColor(Theme.multAlpha(YumiTheme.getPrimary(), 0.12f));
        iconFrame.setBackground(iconBg);

        ImageView iconView = new ImageView(context);
        iconView.setImageResource(R.drawable.baseline_edit_24);
        iconView.setColorFilter(YumiTheme.getPrimary());
        iconFrame.addView(iconView, LayoutHelper.createFrame(20, 20, Gravity.CENTER));
        card.addView(iconFrame, LayoutHelper.createLinear(38, 38, Gravity.CENTER_VERTICAL, 0, 0, 14, 0));

        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView titleView = new TextView(context);
        titleView.setText(r.title);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15.5f);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textCol.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        String curVal = "";
        try {
            curVal = host.getString(r.id, r.subtitle != null ? r.subtitle : "");
        } catch (Throwable ignore) {
        }
        if (!curVal.isEmpty()) {
            TextView valView = new TextView(context);
            valView.setText(curVal);
            valView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
            valView.setTextColor(YumiTheme.getPrimary());
            textCol.addView(valView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        }

        card.addView(textCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, 10, 0));

        ImageView arrowView = new ImageView(context);
        arrowView.setImageResource(R.drawable.arrow_more);
        arrowView.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        card.addView(arrowView, LayoutHelper.createLinear(16, 16, Gravity.CENTER_VERTICAL));

        card.setOnClickListener(v -> {
            card.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            showInputDialog(host, mod, r);
        });
        ScaleStateListAnimator.apply(card, 0.025f, 1.4f);

        return card;
    }

    private View buildSliderCard(Context context, HotHost host, HotModule mod, HotRow r, int index) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(YumiTheme.cardBackground(18));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(AndroidUtilities.dp(14), AndroidUtilities.dp(4), AndroidUtilities.dp(14), AndroidUtilities.dp(4));
        card.setLayoutParams(lp);
        card.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12), AndroidUtilities.dp(14), AndroidUtilities.dp(12));

        LinearLayout topRow = new LinearLayout(context);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout iconFrame = new FrameLayout(context);
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setCornerRadius(AndroidUtilities.dp(12));
        iconBg.setColor(Theme.multAlpha(YumiTheme.getPrimary(), 0.12f));
        iconFrame.setBackground(iconBg);

        ImageView iconView = new ImageView(context);
        iconView.setImageResource(resolveRowIcon(r));
        iconView.setColorFilter(YumiTheme.getPrimary());
        iconFrame.addView(iconView, LayoutHelper.createFrame(20, 20, Gravity.CENTER));
        topRow.addView(iconFrame, LayoutHelper.createLinear(38, 38, Gravity.CENTER_VERTICAL, 0, 0, 14, 0));

        TextView titleView = new TextView(context);
        titleView.setText(r.title);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15.5f);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        topRow.addView(titleView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        final int initialVal;
        int readVal = r.intValue;
        try {
            readVal = host.getInt(r.id, r.intValue);
        } catch (Throwable ignore) {
        }
        initialVal = readVal;

        final TextView valView = new TextView(context);
        final String unit = r.subtitle != null ? r.subtitle : "";
        valView.setText(initialVal + (unit.isEmpty() ? "" : " " + unit));
        valView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f);
        valView.setTypeface(AndroidUtilities.bold());
        valView.setTextColor(YumiTheme.getPrimary());
        topRow.addView(valView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        card.addView(topRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        SeekBar seekBar = new SeekBar(context);
        final int min = r.minValue;
        final int max = Math.max(r.maxValue, min + 1);
        seekBar.setMax(max - min);
        seekBar.setProgress(Math.max(0, initialVal - min));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    int val = min + progress;
                    valView.setText(val + (unit.isEmpty() ? "" : " " + unit));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int val = min + seekBar.getProgress();
                host.putInt(r.id, val);
                try {
                    mod.onSettingsSlider(r.id, val);
                } catch (Throwable ignore) {
                }
            }
        });
        card.addView(seekBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));

        return card;
    }

    private View buildChoiceCard(Context context, HotHost host, HotModule mod, HotRow r, int index) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setMinimumHeight(AndroidUtilities.dp(62));
        card.setBackground(YumiTheme.buttonRipple(YumiTheme.getSurfaceContainer(), 18));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(AndroidUtilities.dp(14), AndroidUtilities.dp(4), AndroidUtilities.dp(14), AndroidUtilities.dp(4));
        card.setLayoutParams(lp);
        card.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12), AndroidUtilities.dp(14), AndroidUtilities.dp(12));

        FrameLayout iconFrame = new FrameLayout(context);
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setCornerRadius(AndroidUtilities.dp(12));
        iconBg.setColor(Theme.multAlpha(YumiTheme.getPrimary(), 0.12f));
        iconFrame.setBackground(iconBg);

        ImageView iconView = new ImageView(context);
        iconView.setImageResource(resolveRowIcon(r));
        iconView.setColorFilter(YumiTheme.getPrimary());
        iconFrame.addView(iconView, LayoutHelper.createFrame(20, 20, Gravity.CENTER));
        card.addView(iconFrame, LayoutHelper.createLinear(38, 38, Gravity.CENTER_VERTICAL, 0, 0, 14, 0));

        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView titleView = new TextView(context);
        titleView.setText(r.title);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15.5f);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textCol.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        int curIdx;
        try {
            curIdx = host.getInt(r.id, r.intValue);
        } catch (Throwable ignore) {
            curIdx = r.intValue;
        }
        if (r.options != null && curIdx >= 0 && curIdx < r.options.length) {
            TextView valView = new TextView(context);
            valView.setText(r.options[curIdx]);
            valView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f);
            valView.setTextColor(YumiTheme.getPrimary());
            textCol.addView(valView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        } else if (r.subtitle != null && !r.subtitle.isEmpty()) {
            TextView subView = new TextView(context);
            subView.setText(r.subtitle);
            subView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
            subView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            textCol.addView(subView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        }

        card.addView(textCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, 10, 0));

        ImageView arrowView = new ImageView(context);
        arrowView.setImageResource(R.drawable.arrow_more);
        arrowView.setColorFilter(YumiTheme.getPrimary());
        card.addView(arrowView, LayoutHelper.createLinear(16, 16, Gravity.CENTER_VERTICAL));

        card.setOnClickListener(v -> {
            card.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (r.options == null || r.options.length == 0) return;
            org.telegram.ui.ActionBar.AlertDialog.Builder builder = new org.telegram.ui.ActionBar.AlertDialog.Builder(context);
            builder.setTitle(r.title);
            builder.setItems(r.options, (dialog, which) -> {
                host.putInt(r.id, which);
                try {
                    mod.onSettingsChoice(r.id, which);
                } catch (Throwable ignore) {
                }
                dialog.dismiss();
                if (listView != null && listView.adapter != null) {
                    listView.adapter.update(true);
                }
            });
            builder.setNegativeButton(MiogramLocale.get("Скасувати", "Отмена", "Cancel"), null);
            showDialog(builder.create());
        });

        return card;
    }

    private View buildColorCard(Context context, HotHost host, HotModule mod, HotRow r, int index) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setMinimumHeight(AndroidUtilities.dp(60));
        card.setBackground(YumiTheme.buttonRipple(YumiTheme.getSurfaceContainer(), 18));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(AndroidUtilities.dp(14), AndroidUtilities.dp(4), AndroidUtilities.dp(14), AndroidUtilities.dp(4));
        card.setLayoutParams(lp);
        card.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12), AndroidUtilities.dp(14), AndroidUtilities.dp(12));

        FrameLayout iconFrame = new FrameLayout(context);
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setCornerRadius(AndroidUtilities.dp(12));
        iconBg.setColor(Theme.multAlpha(YumiTheme.getPrimary(), 0.12f));
        iconFrame.setBackground(iconBg);

        ImageView iconView = new ImageView(context);
        iconView.setImageResource(R.drawable.baseline_brush_24);
        iconView.setColorFilter(YumiTheme.getPrimary());
        iconFrame.addView(iconView, LayoutHelper.createFrame(20, 20, Gravity.CENTER));
        card.addView(iconFrame, LayoutHelper.createLinear(38, 38, Gravity.CENTER_VERTICAL, 0, 0, 14, 0));

        TextView titleView = new TextView(context);
        titleView.setText(r.title);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15.5f);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        card.addView(titleView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, 10, 0));

        final int currentColor;
        int cVal = r.intValue;
        try {
            cVal = host.getInt(r.id, r.intValue);
        } catch (Throwable ignore) {
        }
        currentColor = cVal;

        View colorPreview = new View(context);
        GradientDrawable previewBg = new GradientDrawable();
        previewBg.setShape(GradientDrawable.OVAL);
        previewBg.setColor(currentColor != 0 ? currentColor : YumiTheme.getPrimary());
        previewBg.setStroke(AndroidUtilities.dp(2), 0x33000000);
        colorPreview.setBackground(previewBg);
        card.addView(colorPreview, LayoutHelper.createLinear(28, 28, Gravity.CENTER_VERTICAL));

        card.setOnClickListener(v -> {
            card.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            final int[] palette = new int[]{
                    0xFFE5486B, 0xFF4355B9, 0xFF006C50, 0xFF8F4C38,
                    0xFF6750A4, 0xFF006874, 0xFF386A20, 0xFF9C4146,
                    0xFF2196F3, 0xFFFF9800, 0xFF9C27B0, 0xFF607D8B
            };
            org.telegram.ui.ActionBar.AlertDialog.Builder builder = new org.telegram.ui.ActionBar.AlertDialog.Builder(context);
            builder.setTitle(r.title);
            LinearLayout layout = new LinearLayout(context);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(16), AndroidUtilities.dp(20), AndroidUtilities.dp(16));

            int rowsCount = (palette.length + 3) / 4;
            for (int rowIdx = 0; rowIdx < rowsCount; rowIdx++) {
                LinearLayout rowLayout = new LinearLayout(context);
                rowLayout.setOrientation(LinearLayout.HORIZONTAL);
                rowLayout.setGravity(Gravity.CENTER);
                for (int colIdx = 0; colIdx < 4; colIdx++) {
                    int pIdx = rowIdx * 4 + colIdx;
                    if (pIdx >= palette.length) break;
                    int color = palette[pIdx];
                    View swatch = new View(context);
                    GradientDrawable sBg = new GradientDrawable();
                    sBg.setShape(GradientDrawable.OVAL);
                    sBg.setColor(color);
                    swatch.setBackground(sBg);
                    swatch.setOnClickListener(sv -> {
                        host.putInt(r.id, color);
                        try {
                            mod.onSettingsColor(r.id, color);
                        } catch (Throwable ignore) {
                        }
                        if (listView != null && listView.adapter != null) {
                            listView.adapter.update(true);
                        }
                    });
                    rowLayout.addView(swatch, LayoutHelper.createLinear(44, 44, 0, 8, 8, 8, 8));
                }
                layout.addView(rowLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            }
            builder.setView(layout);
            builder.setNegativeButton(MiogramLocale.get("Закрити", "Закрыть", "Close"), null);
            showDialog(builder.create());
        });

        return card;
    }

    private View buildInfoCard(Context context, String text) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f);
        tv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        tv.setLineSpacing(AndroidUtilities.dp(2), 1f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(AndroidUtilities.dp(18), AndroidUtilities.dp(4), AndroidUtilities.dp(18), AndroidUtilities.dp(10));
        tv.setLayoutParams(lp);
        return tv;
    }

    private int resolveRowIcon(HotRow r) {
        if (r == null || r.id == null) return R.drawable.baseline_settings_24;
        String id = r.id.toLowerCase();
        if (id.contains("expressive") || id.contains("theme") || id.contains("color") || id.contains("style")) {
            return R.drawable.baseline_brush_24;
        }
        if (id.contains("sticky") || id.contains("anim") || id.contains("pixel") || id.contains("bouncy")) {
            return R.drawable.baseline_stars_24;
        }
        if (id.contains("player") || id.contains("lyrics") || id.contains("music")) {
            return R.drawable.baseline_music_note_24;
        }
        if (id.contains("glass") || id.contains("blur") || id.contains("menu")) {
            return R.drawable.msg_customize;
        }
        if (id.contains("loading") || id.contains("update") || id.contains("apply")) {
            return R.drawable.baseline_system_update_24;
        }
        if (id.contains("reset")) {
            return R.drawable.msg_reset;
        }
        if (id.contains("ghost") || id.contains("secret")) {
            return R.drawable.msg_secret;
        }
        if (id.contains("vault") || id.contains("saved")) {
            return R.drawable.msg_saved;
        }
        if (id.contains("ai") || id.contains("bot") || id.contains("transcribe")) {
            return R.drawable.baseline_stars_24;
        }
        return R.drawable.baseline_settings_24;
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == 10) {
            boolean next = !HotModulesManager.isModuleEnabled(moduleId);
            HotModulesManager.setEnabled(moduleId, next, (ok, msg, data) -> {
                if (actionBar != null) actionBar.setTitle(resolveTitle());
                if (listView != null && listView.adapter != null) {
                    listView.adapter.update(true);
                }
            });
            return;
        }
        int idx = item.id - 100;
        if (idx < 0 || idx >= rows.size()) return;
        HotRow r = rows.get(idx);
        HotModulesManager.Handle h = handle();
        HotModule mod = (h != null && h.instance != null) ? h.instance : HotModulesManager.createBuiltinModule(moduleId);
        if (mod == null) return;
        HotHost host = HotModulesManager.hostFor(moduleId);
        try {
            if (r.type == HotRow.BUTTON && handleCustomAction(r)) {
                return;
            }
            if (r.type == HotRow.SWITCH) {
                boolean cur = host.getBool(r.id, r.checked);
                host.putBool(r.id, !cur);
                try {
                    mod.onSettingsToggle(r.id, !cur);
                } catch (Throwable ignore) {
                }
                // Модуль ui застосовується на льоту: перебудова фрагментів одразу.
                if ("ui".equals(moduleId)) {
                    try {
                        app.amegram.hot.HotUiGate.applyLive();
                    } catch (Throwable ignore) {
                    }
                }
                listView.adapter.update(true);
            } else if (r.type == HotRow.BUTTON) {
                try {
                    mod.onSettingsAction(r.id);
                } catch (Throwable ignore) {
                }
                if ("ui".equals(moduleId)) {
                    try {
                        app.amegram.hot.HotUiGate.applyLive();
                    } catch (Throwable ignore) {
                    }
                }
                listView.adapter.update(true);
            } else if (r.type == HotRow.INPUT) {
                showInputDialog(host, mod, r);
            }
        } catch (Throwable ignore) {
        }
    }

    @Override
    public boolean isLightStatusBar() {
        return !Theme.isCurrentThemeDark();
    }

    private boolean handleCustomAction(HotRow r) {
        if (r == null) return false;
        if ("player".equals(moduleId)) {
            if ("player_lyrics_open".equals(r.id)) {
                openPlayerLyrics();
                return true;
            }
            if ("player_lyrics_ai".equals(r.id)) {
                upgradePlayerLyricsToAi();
                return true;
            }
        } else if ("ai".equals(moduleId)) {
            if ("ai_providers_open".equals(r.id)) {
                presentFragment(new app.miogram.bridge.ui.MiogramAiSettingsActivity());
                return true;
            }
            if ("ai_companion_open".equals(r.id)) {
                presentFragment(new app.miogram.bridge.ai.companion.MiogramCompanionActivity());
                return true;
            }
        } else if ("vault".equals(moduleId)) {
            if ("vault_manager_open".equals(r.id)) {
                presentFragment(new app.miogram.bridge.cloudvault.MiogramCloudVaultActivity());
                return true;
            }
        } else if ("automation".equals(moduleId)) {
            if ("automation_account_open".equals(r.id)) {
                presentFragment(new app.miogram.bridge.userbot.MiogramHerokuActivity());
                return true;
            }
        } else if ("tiktok".equals(moduleId)) {
            if ("tiktok_ecosystem_open".equals(r.id)) {
                presentFragment(new app.miogram.bridge.ecosystem.AmegramTikTokSettingsActivity());
                return true;
            }
        }
        return false;
    }

    /** Блок 5: відкрити плеєр з текстами (MiogramModernPlayerLayout lyrics / MiogramLyricsView) через Md3Router. */
    private void openPlayerLyrics() {
        try {
            android.content.Context ctx = getParentActivity() != null ? getParentActivity() : getContext();
            if (ctx == null) return;
            org.telegram.messenger.MessageObject playing = null;
            try {
                playing = org.telegram.messenger.MediaController.getInstance().getPlayingMessageObject();
            } catch (Throwable ignore) {
            }
            if (playing == null) {
                try {
                    android.widget.Toast.makeText(ctx, MiogramLocale.get("Увімкніть трек", "Включите трек", "Play a track first"), android.widget.Toast.LENGTH_SHORT).show();
                } catch (Throwable ignore) {
                }
            }
            // Md3Router.create повертає PlayerSheet (exteraLess, з лірикою) для музики
            // або AudioPlayerAlert з MiogramModernPlayerLayout (MiogramLyricsView всередині).
            org.telegram.ui.ActionBar.BottomSheet sheet =
                    app.amegram.hot.Md3Router.create(ctx, getResourceProvider());
            if (sheet != null) {
                showDialog(sheet);
            }
        } catch (Throwable ignore) {
        }
        try {
            if (listView != null && listView.adapter != null) listView.adapter.update(true);
        } catch (Throwable ignore) {
        }
    }

    /** Блок 5: апгрейд поточних текстів до ШІ (MiogramLyricsView.upgradeLyricsToAi(), AI_WORD шлях). */
    private void upgradePlayerLyricsToAi() {
        try {
            android.content.Context ctx = getParentActivity() != null ? getParentActivity() : getContext();
            if (ctx == null) return;
            org.telegram.messenger.MessageObject playing = null;
            try {
                playing = org.telegram.messenger.MediaController.getInstance().getPlayingMessageObject();
            } catch (Throwable ignore) {
            }
            if (playing == null) {
                try {
                    android.widget.Toast.makeText(ctx, MiogramLocale.get("Увімкніть трек", "Включите трек", "Play a track first"), android.widget.Toast.LENGTH_SHORT).show();
                } catch (Throwable ignore) {
                }
                return;
            }
            // Прямого хендла MiogramLyricsView тут нема (він живе в MiogramModernPlayerLayout lyrics
            // та викликає upgradeLyricsToAi() → transcribeAudioWithAiWordTimed → SOURCE_AI_WORD).
            // Тому відкриваємо шит плеєра, де кнопка ✨ / Розшифровка викликає той самий шлях.
            org.telegram.ui.ActionBar.BottomSheet sheet =
                    app.amegram.hot.Md3Router.create(ctx, getResourceProvider());
            if (sheet != null) {
                showDialog(sheet);
                try {
                    android.widget.Toast.makeText(ctx,
                            MiogramLocale.get("Відкрито плеєр — натисніть Розшифровка в текстах", "Открыт плеер — нажмите Расшифровка в текстах", "Player opened — tap Transcribe in lyrics"),
                            android.widget.Toast.LENGTH_LONG).show();
                } catch (Throwable ignore) {
                }
            }
        } catch (Throwable ignore) {
        }
    }

    private void showInputDialog(HotHost host, HotModule mod, HotRow r) {
        try {
            android.content.Context ctx = getContext();
            if (ctx == null) return;
            String cur = "";
            try {
                cur = host.getString(r.id, r.subtitle != null ? r.subtitle : "");
            } catch (Throwable ignore) {
            }
            final android.widget.EditText input = new android.widget.EditText(ctx);
            input.setText(cur);
            input.setSingleLine(true);
            int pad = org.telegram.messenger.AndroidUtilities.dp(16);
            input.setPadding(pad, pad, pad, pad);
            org.telegram.ui.ActionBar.AlertDialog.Builder builder =
                    new org.telegram.ui.ActionBar.AlertDialog.Builder(ctx);
            builder.setTitle(r.title);
            builder.setView(input);
            builder.setPositiveButton(MiogramLocale.get("ОК", "ОК", "OK"), (d, w) -> {
                String v = input.getText().toString().trim();
                try {
                    host.putString(r.id, v);
                    mod.onSettingsInput(r.id, v);
                } catch (Throwable ignore) {
                }
                listView.adapter.update(true);
            });
            builder.setNegativeButton(MiogramLocale.get("Скасувати", "Отмена", "Cancel"), null);
            builder.show();
        } catch (Throwable ignore) {
        }
    }
}
