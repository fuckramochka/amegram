package app.miogram.ui.yumi;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import app.amegram.theme.YumiMd3;

/** A 56dp filled Yumi text field with focus outline, clear button and error text. */
public class YumiTextField extends LinearLayout {

    private final FrameLayout field;
    private final ImageView leading;
    private final ImageView clear;
    private final EditText inputView;
    private final TextView helperView;
    private final TextView counterView;
    private final YumiRoundRectDrawable fieldBackground;
    private final TextWatcher watcher;
    private boolean error;
    private int maxLength = -1;

    public YumiTextField(@NonNull Context context) {
        super(context);
        setOrientation(VERTICAL);
        setClipChildren(false);

        fieldBackground = new YumiRoundRectDrawable(
                YumiMd3.chatField(), YumiMd3.RADIUS_M_DP,
                YumiMd3.chatFieldBorder(), 1f);
        field = new FrameLayout(context);
        field.setBackground(fieldBackground);
        field.setMinimumHeight(YumiUi.dp(56));
        field.setClipChildren(false);
        addView(field, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        leading = new ImageView(context);
        leading.setScaleType(ImageView.ScaleType.CENTER);
        leading.setVisibility(GONE);
        leading.setColorFilter(new PorterDuffColorFilter(
                YumiMd3.onSurfaceVariant(), PorterDuff.Mode.SRC_IN));
        final FrameLayout.LayoutParams leadingLp = new FrameLayout.LayoutParams(
                YumiUi.dp(24), YumiUi.dp(24), Gravity.START | Gravity.CENTER_VERTICAL);
        leadingLp.setMarginStart(YumiUi.dp(16));
        field.addView(leading, leadingLp);

        inputView = new EditText(context);
        inputView.setBackground(null);
        inputView.setSingleLine(true);
        inputView.setTextSize(TypedValue.COMPLEX_UNIT_SP, YumiMd3.BODY);
        inputView.setTypeface(YumiMd3.typeface(YumiMd3.REGULAR));
        inputView.setTextColor(YumiMd3.onSurface());
        inputView.setHintTextColor(YumiMd3.onSurfaceVariant());
        inputView.setPadding(0, 0, 0, 0);
        inputView.setGravity((YumiUi.isRtl() ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        inputView.setImeOptions(EditorInfo.IME_ACTION_DONE);
        inputView.setIncludeFontPadding(false);
        inputView.setMinHeight(YumiUi.dp(56));
        final FrameLayout.LayoutParams inputLp = new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL);
        inputLp.setMarginStart(YumiUi.dp(16));
        inputLp.setMarginEnd(YumiUi.dp(16));
        field.addView(inputView, inputLp);

        clear = new ImageView(context);
        clear.setImageResource(org.telegram.messenger.R.drawable.yumi_ic_close);
        clear.setScaleType(ImageView.ScaleType.CENTER);
        clear.setContentDescription(YumiLocale.clear());
        clear.setColorFilter(new PorterDuffColorFilter(
                YumiMd3.onSurfaceVariant(), PorterDuff.Mode.SRC_IN));
        clear.setBackground(YumiUi.selectable(0, YumiMd3.RADIUS_FULL_DP));
        clear.setVisibility(GONE);
        final FrameLayout.LayoutParams clearLp = new FrameLayout.LayoutParams(
                YumiUi.dp(44), YumiUi.dp(44), Gravity.END | Gravity.CENTER_VERTICAL);
        clearLp.setMarginEnd(YumiUi.dp(6));
        field.addView(clear, clearLp);
        clear.setOnClickListener(v -> {
            inputView.setText("");
            inputView.requestFocus();
            YumiUi.haptic(v);
        });

        helperView = YumiUi.text(context, "", 12f, YumiMd3.onSurfaceVariant(), YumiMd3.REGULAR);
        helperView.setVisibility(GONE);
        helperView.setPadding(YumiUi.dp(16), YumiUi.dp(6), YumiUi.dp(16), 0);
        addView(helperView, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        counterView = YumiUi.text(context, "", 11f, YumiMd3.onSurfaceVariant(), YumiMd3.REGULAR);
        counterView.setGravity(Gravity.END);
        counterView.setVisibility(GONE);
        addView(counterView, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                clear.setVisibility(TextUtils.isEmpty(s) ? GONE : VISIBLE);
                if (maxLength > 0) {
                    counterView.setVisibility(VISIBLE);
                    counterView.setText(s.length() + " / " + maxLength);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };
        inputView.addTextChangedListener(watcher);

        inputView.setOnFocusChangeListener((v, hasFocus) -> {
            if (!error) {
                fieldBackground.setStroke(hasFocus ? YumiMd3.primary() : YumiMd3.chatFieldBorder(),
                        hasFocus ? 2f : 1f);
            }
            if (hasFocus) {
                clear.setVisibility(TextUtils.isEmpty(inputView.getText()) ? GONE : VISIBLE);
            }
        });
    }

    public EditText getInput() {
        return inputView;
    }

    public void setText(CharSequence text) {
        inputView.setText(text);
        inputView.setSelection(inputView.length());
    }

    public CharSequence getText() {
        return inputView.getText();
    }

    public void setHint(CharSequence hint) {
        inputView.setHint(hint);
        inputView.setContentDescription(hint);
    }

    public void setLeadingIcon(@DrawableRes int iconRes) {
        if (iconRes == 0) {
            leading.setVisibility(GONE);
            field.setPadding(0, 0, 0, 0);
        } else {
            leading.setImageResource(iconRes);
            leading.setVisibility(VISIBLE);
        }
        updateInputInsets();
    }

    public void setLeadingIconTint(int color) {
        leading.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
    }

    public void setInputType(int type) {
        inputView.setInputType(type);
    }

    public void setSingleLine(boolean singleLine) {
        inputView.setSingleLine(singleLine);
        inputView.setMinHeight(singleLine ? YumiUi.dp(56) : YumiUi.dp(56));
    }

    public void setMaxLength(int maxLength) {
        this.maxLength = maxLength;
        counterView.setVisibility(maxLength > 0 ? VISIBLE : GONE);
        if (maxLength > 0) {
            counterView.setText(inputView.length() + " / " + maxLength);
        }
    }

    public void setHelper(CharSequence helper) {
        helperView.setText(helper);
        helperView.setTextColor(YumiMd3.onSurfaceVariant());
        helperView.setVisibility(TextUtils.isEmpty(helper) ? GONE : VISIBLE);
    }

    public void setError(CharSequence message) {
        error = !TextUtils.isEmpty(message);
        helperView.setText(message);
        helperView.setTextColor(error ? YumiMd3.error() : YumiMd3.onSurfaceVariant());
        helperView.setVisibility(TextUtils.isEmpty(message) ? GONE : VISIBLE);
        fieldBackground.setStroke(error ? YumiMd3.error() : YumiMd3.chatFieldBorder(),
                error ? 2f : 1f);
        inputView.setContentDescription(error ? message : inputView.getHint());
        if (error) {
            inputView.announceForAccessibility(message);
        }
    }

    public void setOnEditorActionListener(TextView.OnEditorActionListener listener) {
        inputView.setOnEditorActionListener(listener);
    }

    public void setOnFocusChangeListener(View.OnFocusChangeListener listener) {
        inputView.setOnFocusChangeListener(listener);
    }

    private void updateInputInsets() {
        final FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) inputView.getLayoutParams();
        lp.setMarginStart(YumiUi.dp(leading.getVisibility() == VISIBLE ? 52 : 16));
        inputView.setLayoutParams(lp);
    }

    public void updateColors() {
        fieldBackground.setFillColor(YumiMd3.chatField());
        fieldBackground.setStroke(error ? YumiMd3.error() : YumiMd3.chatFieldBorder(), error ? 2f : 1f);
        inputView.setTextColor(YumiMd3.onSurface());
        inputView.setHintTextColor(YumiMd3.onSurfaceVariant());
        leading.setColorFilter(new PorterDuffColorFilter(
                YumiMd3.onSurfaceVariant(), PorterDuff.Mode.SRC_IN));
        clear.setColorFilter(new PorterDuffColorFilter(
                YumiMd3.onSurfaceVariant(), PorterDuff.Mode.SRC_IN));
        helperView.setTextColor(error ? YumiMd3.error() : YumiMd3.onSurfaceVariant());
        counterView.setTextColor(YumiMd3.onSurfaceVariant());
    }
}
