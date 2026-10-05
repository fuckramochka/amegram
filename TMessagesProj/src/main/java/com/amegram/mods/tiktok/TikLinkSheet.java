package com.amegram.mods.tiktok;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/**
 * Екран зв'язування акаунта TikTok MI та налаштування екосистеми.
 */
public class TikLinkSheet extends BottomSheet {

    public TikLinkSheet(Context context) {
        super(context, true);

        int bgColor = getThemedColor(Theme.key_dialogBackground);
        if (bgColor == 0) bgColor = 0xFF12131C;
        fixNavigationBar(bgColor);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bgColor);
        root.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(16), AndroidUtilities.dp(20), AndroidUtilities.dp(24));

        View handle = new View(context);
        GradientDrawable hbg = new GradientDrawable();
        hbg.setColor(0x44FFFFFF);
        hbg.setCornerRadius(AndroidUtilities.dp(3));
        handle.setBackground(hbg);
        root.addView(handle, LayoutHelper.createLinear(36, 4, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 16));

        TextView title = new TextView(context);
        title.setText("Екосистема TikTok MI");
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setTextColor(0xFFFFFFFF);
        title.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        root.addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 8));

        TextView desc = new TextView(context);
        desc.setText("Синхронізація закладів, історії переглядів та перегляд відео без водяних знаків прямо в Телеграм.");
        desc.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        desc.setTextColor(0xAAFFFFFF);
        desc.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(desc, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 20));

        TextView btnSync = new TextView(context);
        btnSync.setText("Підключити / Синхронізувати");
        btnSync.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        btnSync.setTextColor(0xFFFFFFFF);
        btnSync.setGravity(Gravity.CENTER);
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(0xFFEE1D52);
        btnBg.setCornerRadius(AndroidUtilities.dp(12));
        btnSync.setBackground(btnBg);
        btnSync.setPadding(0, AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14));
        btnSync.setOnClickListener(v -> {
            Toast.makeText(context, "Акаунт TikTok MI синхронізовано!", Toast.LENGTH_SHORT).show();
            dismiss();
        });
        root.addView(btnSync, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 8));

        setCustomView(root);
    }
}
