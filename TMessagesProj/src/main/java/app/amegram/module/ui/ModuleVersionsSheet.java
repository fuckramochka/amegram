package app.amegram.module.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.List;

import app.amegram.core.modules.ModuleManager;

/**
 * Ручний вибір версії після 5 авто-відкатів (spec): список зі збережених
 * версій + кнопка «Вимкнути модуль». Жодної консолі.
 */
public class ModuleVersionsSheet extends BottomSheet {

    public interface OnChanged {
        void onChanged();
    }

    public ModuleVersionsSheet(Context context, final String moduleId, final OnChanged onChanged) {
        super(context, false);

        int bg = getThemedColor(Theme.key_dialogBackground);
        if (bg == 0) {
            bg = 0xFF14151F;
        }
        int text = getThemedColor(Theme.key_dialogTextBlack);
        if (text == 0) {
            text = 0xFFFFFFFF;
        }
        int accent = getThemedColor(Theme.key_windowBackgroundWhiteBlueHeader);
        if (accent == 0) {
            accent = 0xFF6C63FF;
        }

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        root.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(12),
                AndroidUtilities.dp(20), AndroidUtilities.dp(20));

        TextView title = new TextView(context);
        title.setText("\u0412\u0435\u0440\u0441\u0456\u0457: " + moduleId);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(text);
        root.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 0, 0, 4));

        TextView sub = new TextView(context);
        sub.setText("5 \u0430\u0432\u0442\u043e-\u0432\u0456\u0434\u043a\u043e\u0442\u0456\u0432 \u0432\u0438\u0447\u0435\u0440\u043f\u0430\u043d\u043e. \u0412\u0438\u0431\u0435\u0440\u0456\u0442\u044c \u0432\u0435\u0440\u0441\u0456\u044e \u0432\u0440\u0443\u0447\u043d\u0443 \u0430\u0431\u043e \u0432\u0438\u043c\u043a\u043d\u0456\u0442\u044c \u043c\u043e\u0434\u0443\u043b\u044c.");
        sub.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        root.addView(sub, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        ScrollView scroll = new ScrollView(context);
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        List<String> versions = ModuleManager.historyVersions(moduleId);
        for (final String ver : versions) {
            TextView row = new TextView(context);
            row.setText("v" + ver);
            row.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            row.setTextColor(text);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10),
                    AndroidUtilities.dp(12), AndroidUtilities.dp(10));
            GradientDrawable rowBg = new GradientDrawable();
            rowBg.setColor(0x11FFFFFF);
            rowBg.setCornerRadius(AndroidUtilities.dp(12));
            row.setBackground(rowBg);
            row.setOnClickListener(v -> {
                if (ModuleManager.installHistoryVersion(moduleId, ver)) {
                    ModuleManager.markHealthy(moduleId);
                    if (onChanged != null) {
                        onChanged.onChanged();
                    }
                    dismiss();
                }
            });
            list.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                    LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));
        }
        scroll.addView(list);
        root.addView(scroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        TextView off = new TextView(context);
        off.setText("\u0412\u0438\u043c\u043a\u043d\u0443\u0442\u0438 \u043c\u043e\u0434\u0443\u043b\u044c");
        off.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        off.setTypeface(AndroidUtilities.bold());
        off.setTextColor(0xFFFFFFFF);
        off.setGravity(Gravity.CENTER);
        GradientDrawable offBg = new GradientDrawable();
        offBg.setColor(0xFFFF3B30);
        offBg.setCornerRadius(AndroidUtilities.dp(14));
        off.setBackground(offBg);
        off.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12));
        off.setOnClickListener(v -> {
            app.amegram.module.AmegramConfig.setBool("amod_" + moduleId, false);
            if (onChanged != null) {
                onChanged.onChanged();
            }
            dismiss();
        });
        root.addView(off, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0));

        setCustomView(root);
    }
}
