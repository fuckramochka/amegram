package com.amegram.mods.ui;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;

/** Module-owned search field for official list screens. */
public final class YumiSearchField extends FrameLayout {

    private final YumiPalette palette;
    private final EditText editText;
    private final ImageView clearView;
    private final ImageView searchView;
    private final GradientDrawable background = new GradientDrawable();

    public YumiSearchField(Context context, YumiPalette palette) {
        super(context);
        this.palette = palette;
        setClickable(true);
        setFocusable(true);
        setBackground(background);
        updateBackground(false);

        searchView = new ImageView(context);
        searchView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        final int searchRes = res(context, "yumi_ic_search");
        if (searchRes != 0) {
            searchView.setImageResource(searchRes);
        }
        searchView.setColorFilter(new PorterDuffColorFilter(
                palette.navInactive, PorterDuff.Mode.SRC_IN));
        searchView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(searchView, new LayoutParams(dp(20), dp(20), Gravity.CENTER_VERTICAL | Gravity.START));

        editText = new EditText(context);
        editText.setSingleLine(true);
        editText.setBackground(null);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
        editText.setTextColor(palette.onSurface);
        editText.setHintTextColor(palette.navInactive);
        editText.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editText.setPaddingRelative(dp(44), 0, dp(40), 0);
        editText.setGravity(Gravity.CENTER_VERTICAL);
        editText.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        addView(editText, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        clearView = new ImageView(context);
        clearView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        final int clearRes = res(context, "yumi_ic_close");
        if (clearRes != 0) {
            clearView.setImageResource(clearRes);
        }
        clearView.setColorFilter(new PorterDuffColorFilter(
                palette.navInactive, PorterDuff.Mode.SRC_IN));
        clearView.setContentDescription(YumiText.tr("Очистити", "Очистить", "Clear"));
        clearView.setVisibility(GONE);
        clearView.setClickable(true);
        clearView.setOnClickListener(v -> editText.setText(""));
        addView(clearView, new LayoutParams(dp(32), dp(32),
                Gravity.CENTER_VERTICAL | Gravity.END));

        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                clearView.setVisibility(TextUtils.isEmpty(s) ? GONE : VISIBLE);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        editText.setOnFocusChangeListener((v, hasFocus) -> updateBackground(hasFocus));
    }

    public EditText editText() {
        return editText;
    }

    public void setHint(CharSequence hint) {
        editText.setHint(hint);
        editText.setContentDescription(hint);
    }

    public void setText(CharSequence text) {
        editText.setText(text);
        if (text != null) {
            editText.setSelection(text.length());
        }
    }

    public void focus() {
        editText.requestFocus();
        android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager) getContext()
                        .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(editText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        }
    }

    public void refreshColors(YumiPalette updated) {
        editText.setTextColor(updated.onSurface);
        editText.setHintTextColor(updated.navInactive);
        searchView.setColorFilter(new PorterDuffColorFilter(
                updated.navInactive, PorterDuff.Mode.SRC_IN));
        clearView.setColorFilter(new PorterDuffColorFilter(
                updated.navInactive, PorterDuff.Mode.SRC_IN));
        updateBackground(editText.hasFocus());
    }

    private void updateBackground(boolean focused) {
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(dp(20));
        final boolean dark = (palette.onSurface & 0xFF) > 128;
        background.setColor(dark ? 0x24767680 : 0x14000000);
        background.setStroke(Math.max(1, dp(focused ? 1.5f : 0.75f)),
                focused ? palette.primary : (dark ? 0x1AFFFFFF : 0x12000000));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        final boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        final int searchSize = dp(20);
        final int clearSize = dp(32);
        if (rtl) {
            searchView.layout(getWidth() - dp(16) - searchSize, (getHeight() - searchSize) / 2,
                    getWidth() - dp(16), (getHeight() + searchSize) / 2);
            clearView.layout(dp(10), (getHeight() - clearSize) / 2,
                    dp(10) + clearSize, (getHeight() + clearSize) / 2);
        } else {
            searchView.layout(dp(16), (getHeight() - searchSize) / 2,
                    dp(16) + searchSize, (getHeight() + searchSize) / 2);
            clearView.layout(getWidth() - dp(10) - clearSize, (getHeight() - clearSize) / 2,
                    getWidth() - dp(10), (getHeight() + clearSize) / 2);
        }
    }

    private static int res(Context context, String name) {
        return context.getResources().getIdentifier(name, "drawable", context.getPackageName());
    }

    private static int dp(float value) {
        return Math.round(value * android.content.res.Resources.getSystem()
                .getDisplayMetrics().density);
    }
}
