package app.miogram.ui.yumi;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import app.amegram.theme.YumiMd3;

/** A Material 3 expressive grouped container with first/middle/last corners. */
public class YumiGroupCard extends LinearLayout {

    private final LinearLayout content;
    private final List<YumiCell> cells = new ArrayList<>();
    private TextView header;

    public YumiGroupCard(@NonNull Context context) {
        super(context);
        setOrientation(VERTICAL);
        setClipChildren(false);
        setClipToPadding(false);

        content = new LinearLayout(context);
        content.setOrientation(VERTICAL);
        content.setClipChildren(false);
        addView(content, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    public YumiGroupCard setHeader(CharSequence text) {
        if (header == null) {
            header = YumiUi.text(getContext(), "", YumiMd3.LABEL,
                    YumiMd3.onSurfaceVariant(), YumiMd3.MEDIUM);
            header.setLetterSpacing(0.02f);
            header.setPadding(YumiUi.dp(4), YumiUi.dp(14), YumiUi.dp(4), YumiUi.dp(6));
            addView(header, 0, new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        }
        header.setText(text);
        header.setVisibility(text == null || text.length() == 0 ? GONE : VISIBLE);
        return this;
    }

    public YumiCell addCell(YumiCell cell) {
        cells.add(cell);
        content.addView(cell, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        updateCorners();
        return cell;
    }

    public YumiCell addCell(@NonNull CharSequence title, CharSequence subtitle) {
        final YumiCell cell = new YumiCell(getContext());
        cell.setTitle(title);
        cell.setSubtitle(subtitle);
        return addCell(cell);
    }

    public void addFooterView(View view) {
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        lp.topMargin = YumiUi.dp(6);
        addView(view, lp);
    }

    public List<YumiCell> getCells() {
        return cells;
    }

    public void updateCorners() {
        final int count = cells.size();
        for (int i = 0; i < count; i++) {
            cells.get(i).setGroupPosition(i == 0, i > 0 && i < count - 1, i == count - 1);
        }
    }

    public void updateColors() {
        if (header != null) {
            header.setTextColor(YumiMd3.onSurfaceVariant());
        }
        for (YumiCell cell : cells) {
            cell.updateColors();
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        for (YumiCell cell : cells) {
            cell.setEnabled(enabled);
            cell.setAlpha(enabled ? 1f : 0.55f);
        }
    }
}
