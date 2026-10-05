package com.amegram.mods.ai;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Екран ШІ-супутника. Потребує APP_API_JAR при збірці (класи телеграма). */
public class CompanionFragment extends BaseFragment {

    public interface TextService {
        String ask(String userText) throws Exception;
    }

    private final TextService service;
    private final String title;
    private LinearLayout messages;
    private ScrollView scroll;
    private final List<String[]> history = new ArrayList<>();

    public CompanionFragment(TextService service, String title) {
        this.service = service;
        this.title = title;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(title);
        actionBar.setActionBarMenuOnItemClick(new org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        scroll = new ScrollView(context);
        messages = new LinearLayout(context);
        messages.setOrientation(LinearLayout.VERTICAL);
        messages.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(12),
                AndroidUtilities.dp(12), AndroidUtilities.dp(12));
        scroll.addView(messages);
        root.addView(scroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        LinearLayout inputRow = new LinearLayout(context);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        inputRow.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        inputRow.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8),
                AndroidUtilities.dp(8), AndroidUtilities.dp(8));

        EditText input = new EditText(context);
        input.setHint("Повідомлення…");
        input.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        inputRow.addView(input, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        TextView send = new TextView(context);
        send.setText("➤");
        send.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        send.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
        send.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(6),
                AndroidUtilities.dp(4), AndroidUtilities.dp(6));
        send.setOnClickListener(v -> {
            String text = input.getText().toString().trim();
            if (text.isEmpty()) return;
            input.setText("");
            addMessage(context, true, text);
            send.setEnabled(false);
            new Thread(() -> {
                String reply;
                try {
                    reply = service.ask(text);
                } catch (Exception e) {
                    reply = "⚠️ " + (e.getMessage() != null ? e.getMessage() : "помилка");
                }
                final String r = reply;
                AndroidUtilities.runOnUIThread(() -> {
                    addMessage(context, false, r);
                    send.setEnabled(true);
                });
            }).start();
        });
        inputRow.addView(send);

        root.addView(inputRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT));

        fragmentView = root;
        return fragmentView;
    }

    private void addMessage(Context context, boolean mine, String text) {
        history.add(new String[]{mine ? "u" : "a", text});
        TextView bubble = new TextView(context);
        bubble.setText(text);
        bubble.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        bubble.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        bubble.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(12),
                Theme.getColor(mine ? Theme.key_chat_inBubble : Theme.key_chat_outBubble)));
        bubble.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(8),
                AndroidUtilities.dp(12), AndroidUtilities.dp(8));
        LinearLayout.LayoutParams lp = LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT, mine ? Gravity.END : Gravity.START, 0, 4, 0, 4);
        messages.addView(bubble, lp);
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }
}
