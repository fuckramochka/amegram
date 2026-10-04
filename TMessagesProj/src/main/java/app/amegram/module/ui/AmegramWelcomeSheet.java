package app.amegram.module.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.List;

import app.amegram.module.AmegramConfig;
import app.amegram.module.AmegramFeature;
import app.amegram.module.AmegramFeatureManager;

/**
 * Compact welcome: persona pick + module checklist + start.
 * Shown once per install (flag "guide_shown"). No images, no network,
 * no Discord/AGSL upsell — 3 choices and out.
 */
public class AmegramWelcomeSheet extends BottomSheet {

    private static final String KEY_SHOWN = "guide_shown";

    private boolean selectedAme = true;
    private boolean selectedBeta = true;
    private final List<CheckBox> boxes = new ArrayList<>();
    private final List<String> ids = new ArrayList<>();

    public AmegramWelcomeSheet(Context context) {
        super(context, false);

        int bg = getThemedColor(Theme.key_dialogBackground);
        if (bg == 0) {
            bg = 0xFF14151F;
        }
        int accent = getThemedColor(Theme.key_windowBackgroundWhiteBlueHeader);
        if (accent == 0) {
            accent = 0xFF6C63FF;
        }
        int text = getThemedColor(Theme.key_dialogTextBlack);
        if (text == 0) {
            text = 0xFFFFFFFF;
        }
        int sub = getThemedColor(Theme.key_dialogTextGray2);
        if (sub == 0) {
            sub = 0xAAFFFFFF;
        }
        final int accentF = accent;
        final int textF = text;

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        root.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(12),
                AndroidUtilities.dp(20), AndroidUtilities.dp(20));

        TextView title = new TextView(context);
        title.setText("\u0414\u043e\u0431\u0440\u043e \u043f\u043e\u0436\u0430\u043b\u043e\u0432\u0430\u0442\u044c \u0432 Amegram");
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(text);
        root.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 0, 0, 4));

        TextView sub1 = new TextView(context);
        sub1.setText("\u0412\u044b\u0431\u0435\u0440\u0438 \u0441\u043f\u0443\u0442\u043d\u0438\u0446\u0443 \u0438 \u043c\u043e\u0434\u0443\u043b\u0438. \u0412\u0441\u0451 \u043e\u0441\u0442\u0430\u043b\u044c\u043d\u043e\u0435 \u0434\u043e\u043a\u0430\u0447\u0430\u0435\u0442\u0441\u044f \u043f\u043e \u0436\u0435\u043b\u0430\u043d\u0438\u044e.");
        sub1.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        sub1.setTextColor(sub);
        root.addView(sub1, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));

        LinearLayout personaRow = new LinearLayout(context);
        personaRow.setOrientation(LinearLayout.HORIZONTAL);
        final TextView ameCard = makeCard(context, "\u0410\u043c\u0435", true, accentF, textF);
        final TextView kangelCard = makeCard(context, "\u041a\u0430\u043d\u0433\u0435\u043b\u044c", false, accentF, textF);
        ameCard.setOnClickListener(v -> {
            selectedAme = true;
            paintCard(ameCard, true, accentF);
            paintCard(kangelCard, false, accentF);
        });
        kangelCard.setOnClickListener(v -> {
            selectedAme = false;
            paintCard(ameCard, false, accentF);
            paintCard(kangelCard, true, accentF);
        });
        personaRow.addView(ameCard, new LinearLayout.LayoutParams(0, LayoutHelper.WRAP_CONTENT, 1f));
        personaRow.addView(kangelCard, new LinearLayout.LayoutParams(0, LayoutHelper.WRAP_CONTENT, 1f));
        root.addView(personaRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        // Вітка оновлень — пише справжній ключ апдейтера (miogram_updater_prefs).
        selectedBeta = !"stable".equals(readUpdateChannel(context));
        LinearLayout branchRow = new LinearLayout(context);
        branchRow.setOrientation(LinearLayout.HORIZONTAL);
        final TextView betaCard = makeCard(context, "Beta", selectedBeta, accentF, textF);
        final TextView stableCard = makeCard(context, "Stable", !selectedBeta, accentF, textF);
        betaCard.setOnClickListener(v -> {
            selectedBeta = true;
            paintCard(betaCard, true, accentF);
            paintCard(stableCard, false, accentF);
        });
        stableCard.setOnClickListener(v -> {
            selectedBeta = false;
            paintCard(betaCard, false, accentF);
            paintCard(stableCard, true, accentF);
        });
        branchRow.addView(betaCard, new LinearLayout.LayoutParams(0, LayoutHelper.WRAP_CONTENT, 1f));
        branchRow.addView(stableCard, new LinearLayout.LayoutParams(0, LayoutHelper.WRAP_CONTENT, 1f));
        root.addView(branchRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));

        ScrollView scroll = new ScrollView(context);
        LinearLayout mods = new LinearLayout(context);
        mods.setOrientation(LinearLayout.VERTICAL);
        for (AmegramFeature f : AmegramFeatureManager.all()) {
            if ("guide".equals(f.id())) {
                continue;
            }
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(6),
                    AndroidUtilities.dp(4), AndroidUtilities.dp(6));

            TextView name = new TextView(context);
            name.setText(f.title() + " \u2022 " + f.ramEstimate());
            name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            name.setTextColor(text);
            row.addView(name, new LinearLayout.LayoutParams(0, LayoutHelper.WRAP_CONTENT, 1f));

            CheckBox cb = new CheckBox(context);
            boolean on;
            try {
                on = f.isEnabled();
            } catch (Throwable t) {
                on = false;
            }
            cb.setChecked(on);
            boxes.add(cb);
            ids.add(f.id());
            row.addView(cb, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT,
                    LayoutHelper.WRAP_CONTENT));

            final CheckBox ref = cb;
            row.setOnClickListener(v -> ref.setChecked(!ref.isChecked()));
            mods.addView(row);
        }
        scroll.addView(mods);
        root.addView(scroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        TextView start = new TextView(context);
        start.setText("\u041d\u0430\u0447\u0430\u0442\u044c");
        start.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        start.setTypeface(AndroidUtilities.bold());
        start.setTextColor(0xFFFFFFFF);
        start.setGravity(Gravity.CENTER);
        GradientDrawable bgBtn = new GradientDrawable();
        bgBtn.setColor(accent);
        bgBtn.setCornerRadius(AndroidUtilities.dp(14));
        start.setBackground(bgBtn);
        start.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12));
        start.setOnClickListener(v -> {
            applySelection();
            dismiss();
        });
        root.addView(start, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0));

        setCustomView(root);
    }

    private void applySelection() {
        AmegramConfig.setString("companion", selectedAme ? "ame" : "kangel");
        writeUpdateChannel(selectedBeta ? "beta" : "stable");
        for (int i = 0; i < ids.size(); i++) {
            AmegramFeatureManager.setEnabled(ids.get(i), boxes.get(i).isChecked());
        }
        markShown();
    }

    private static String readUpdateChannel(Context context) {
        try {
            Context app = context.getApplicationContext();
            if (app == null) {
                app = context;
            }
            return app.getSharedPreferences("miogram_updater_prefs", Context.MODE_PRIVATE)
                    .getString("update_channel", "beta");
        } catch (Throwable t) {
            return "beta";
        }
    }

    private void writeUpdateChannel(String channel) {
        try {
            Context context = getContext();
            Context app = context != null ? context.getApplicationContext() : null;
            if (app == null && context != null) {
                app = context;
            }
            if (app != null) {
                app.getSharedPreferences("miogram_updater_prefs", Context.MODE_PRIVATE)
                        .edit().putString("update_channel", channel).apply();
            }
        } catch (Throwable ignore) {
        }
    }

    @Override
    public void dismiss() {
        markShown();
        super.dismiss();
    }

    private static TextView makeCard(Context ctx, String name, boolean selected, int accent, int text) {
        TextView tv = new TextView(ctx);
        tv.setText(name);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        tv.setTypeface(AndroidUtilities.bold());
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14));
        paintCard(tv, selected, accent);
        tv.setTextColor(selected ? accent : text);
        return tv;
    }

    private static void paintCard(TextView tv, boolean selected, int accent) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(AndroidUtilities.dp(14));
        if (selected) {
            d.setColor((accent & 0x00FFFFFF) | 0x22000000);
            d.setStroke(AndroidUtilities.dp(2), accent);
        } else {
            d.setColor(0x11FFFFFF);
            d.setStroke(AndroidUtilities.dp(1), 0x22FFFFFF);
        }
        tv.setBackground(d);
    }

    public static boolean wasShown() {
        try {
            return AmegramConfig.getBool(KEY_SHOWN, false);
        } catch (Throwable t) {
            return true;
        }
    }

    public static void markShown() {
        try {
            AmegramConfig.setBool(KEY_SHOWN, true);
        } catch (Throwable ignore) {
        }
    }
}
