package app.miogram.bridge.ecosystem;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.List;

import app.miogram.bridge.MiogramLocale;
import app.miogram.bridge.customui.MiogramHaptic;
import app.miogram.bridge.presence.MiogramCloudPresence;
import app.miogram.bridge.ui.SlideToConfirmView;

/**
 * Bottom Sheet for linking Telegram accounts to TikTok MI.
 * Displays interactive account selector + TikTok preview + slide-to-confirm gesture.
 */
public class AmegramTikTokLinkSheet extends BottomSheet {

    public static void show(Context context, String tiktokUid, String tiktokUsername, String tiktokNickname, String tiktokAvatar) {
        if (context == null) return;
        AmegramTikTokLinkSheet sheet = new AmegramTikTokLinkSheet(context, tiktokUid, tiktokUsername, tiktokNickname, tiktokAvatar);
        sheet.show();
    }

    private final String tiktokUid;
    private final String tiktokUsername;
    private final String tiktokNickname;
    private final String tiktokAvatar;

    private int selectedAccountIndex = UserConfig.selectedAccount;
    private final List<AccountHolder> accountHolders = new ArrayList<>();

    private static class AccountHolder {
        int accountIndex;
        TLRPC.User user;
        LinearLayout cardView;
        TextView checkView;
    }

    public AmegramTikTokLinkSheet(Context context, String tiktokUid, String tiktokUsername, String tiktokNickname, String tiktokAvatar) {
        super(context, false);
        this.tiktokUid = !TextUtils.isEmpty(tiktokUid) ? tiktokUid : "";
        this.tiktokUsername = !TextUtils.isEmpty(tiktokUsername) ? tiktokUsername : "";
        this.tiktokNickname = !TextUtils.isEmpty(tiktokNickname) ? tiktokNickname : "";
        this.tiktokAvatar = !TextUtils.isEmpty(tiktokAvatar) ? tiktokAvatar : "";

        setApplyBottomPadding(false);
        setApplyTopPadding(false);
        fixNavigationBar(0xFF0E131B);

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(0xFF0E131B);

        ScrollView scrollView = new ScrollView(context);
        scrollView.setVerticalScrollBarEnabled(false);
        root.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(12), AndroidUtilities.dp(18), AndroidUtilities.dp(24));
        scrollView.addView(layout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        // Top drag handle
        View handle = new View(context);
        GradientDrawable handleBg = new GradientDrawable();
        handleBg.setColor(0x44888888);
        handleBg.setCornerRadius(AndroidUtilities.dp(2.5f));
        handle.setBackground(handleBg);
        layout.addView(handle, LayoutHelper.createLinear(38, 5, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 14));

        // Header Title
        TextView title = new TextView(context);
        title.setText(MiogramLocale.get("Прив'язка до TikTok MI ໒꒱", "Привязка к TikTok MI ໒꒱", "Link to TikTok MI ໒꒱"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(0xFFFFFFFF);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

        // Subtitle question
        TextView subtitle = new TextView(context);
        subtitle.setText(MiogramLocale.get(
                "Який акаунт Telegram ви хочете прив'язати?",
                "Какой аккаунт Telegram вы хотите привязать?",
                "Which Telegram account do you want to link?"
        ));
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitle.setTextColor(0xAAFFFFFF);
        subtitle.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        // 1. Account Selector Section
        TextView accountsLabel = new TextView(context);
        accountsLabel.setText(MiogramLocale.get("Оберіть акаунт Telegram:", "Выберите аккаунт Telegram:", "Select Telegram Account:"));
        accountsLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        accountsLabel.setTypeface(AndroidUtilities.bold());
        accountsLabel.setTextColor(0xFF00F2FE);
        layout.addView(accountsLabel, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 4, 0, 0, 8));

        LinearLayout accountsContainer = new LinearLayout(context);
        accountsContainer.setOrientation(LinearLayout.VERTICAL);
        layout.addView(accountsContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        // Collect and build active accounts
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            UserConfig uc = UserConfig.getInstance(i);
            if (uc.isClientActivated()) {
                TLRPC.User u = uc.getCurrentUser();
                if (u != null) {
                    AccountHolder holder = createAccountRow(context, i, u);
                    accountHolders.add(holder);
                    accountsContainer.addView(holder.cardView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));
                }
            }
        }
        updateAccountSelectionUI();

        // 2. Incoming TikTok MI Account Card
        TextView tiktokLabel = new TextView(context);
        tiktokLabel.setText(MiogramLocale.get("Акаунт TikTok MI для зв'язування:", "Аккаунт TikTok MI для привязки:", "TikTok MI Account to link:"));
        tiktokLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        tiktokLabel.setTypeface(AndroidUtilities.bold());
        tiktokLabel.setTextColor(0xFFFF70A6);
        layout.addView(tiktokLabel, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 4, 0, 0, 8));

        LinearLayout tiktokCard = createTikTokCard(context);
        layout.addView(tiktokCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 20));

        // Hint about presence and badges
        TextView syncHint = new TextView(context);
        syncHint.setText(MiogramLocale.get(
                "✦ Прив'язаний акаунт з'явиться на карточці присутності (Spotify, Steam, TikTok MI) в профілі.\n✦ У TikTok MI відкриється вибір стилю бейджика (Telegram Pink або Mint).",
                "✦ Привязанный аккаунт появится на карточке присутствия (Spotify, Steam, TikTok MI) в профиле.\n✦ В TikTok MI откроется выбор стиля значка (Telegram Pink или Mint).",
                "✦ Linked account will appear on the digital presence card (Spotify, Steam, TikTok MI).\n✦ You can choose between Telegram Pink and Mint badge in TikTok MI."
        ));
        syncHint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        syncHint.setTextColor(0x88FFFFFF);
        syncHint.setLineSpacing(AndroidUtilities.dp(3), 1.0f);
        layout.addView(syncHint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 4, 0, 0, 18));

        // 3. Slide to Confirm Slider
        SlideToConfirmView slider = new SlideToConfirmView(context);
        slider.setHint(MiogramLocale.get(
                "Протягніть для прив'язки  ›››",
                "Протяните для привязки  ›››",
                "Slide to link accounts  ›››"
        ));
        slider.setOnConfirmListener(() -> executeLinking(context));
        layout.addView(slider, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 54, 0, 0, 0, 10));

        setCustomView(root);
    }

    private AccountHolder createAccountRow(Context context, final int accountIndex, final TLRPC.User user) {
        final AccountHolder holder = new AccountHolder();
        holder.accountIndex = accountIndex;
        holder.user = user;

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(12), AndroidUtilities.dp(10));
        row.setClickable(true);
        row.setFocusable(true);

        BackupImageView avatarView = new BackupImageView(context);
        avatarView.setRoundRadius(AndroidUtilities.dp(22));
        AvatarDrawable avatarDrawable = new AvatarDrawable(user);
        avatarView.setForUserOrChat(user, avatarDrawable);
        row.addView(avatarView, LayoutHelper.createLinear(44, 44, 0, 0, 12, 0));

        LinearLayout textLayout = new LinearLayout(context);
        textLayout.setOrientation(LinearLayout.VERTICAL);
        row.addView(textLayout, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL));

        LinearLayout nameRow = new LinearLayout(context);
        nameRow.setOrientation(LinearLayout.HORIZONTAL);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);
        textLayout.addView(nameRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView nameText = new TextView(context);
        nameText.setText(Emoji.replaceEmoji(UserObject.getUserName(user), nameText.getPaint().getFontMetricsInt(), false));
        nameText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        nameText.setTypeface(AndroidUtilities.bold());
        nameText.setTextColor(0xFFFFFFFF);
        nameText.setSingleLine(true);
        nameText.setEllipsize(TextUtils.TruncateAt.END);
        nameRow.addView(nameText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        if (accountIndex == UserConfig.selectedAccount) {
            TextView currentBadge = new TextView(context);
            currentBadge.setText(MiogramLocale.get("ПОТОЧНИЙ", "ТЕКУЩИЙ", "ACTIVE"));
            currentBadge.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10);
            currentBadge.setTypeface(AndroidUtilities.bold());
            currentBadge.setTextColor(0xFF00F2FE);
            currentBadge.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(1), AndroidUtilities.dp(6), AndroidUtilities.dp(1));
            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setColor(0x2200F2FE);
            badgeBg.setCornerRadius(AndroidUtilities.dp(6));
            currentBadge.setBackground(badgeBg);
            nameRow.addView(currentBadge, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 8, 0, 0, 0));
        }

        TextView userHandle = new TextView(context);
        String handle = !TextUtils.isEmpty(user.username) ? "@" + user.username : user.phone;
        userHandle.setText(handle != null ? handle : "ID: " + user.id);
        userHandle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        userHandle.setTextColor(0x88FFFFFF);
        textLayout.addView(userHandle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        TextView checkView = new TextView(context);
        checkView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        checkView.setTypeface(AndroidUtilities.bold());
        row.addView(checkView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 8, 0, 4, 0));

        holder.cardView = row;
        holder.checkView = checkView;

        row.setOnClickListener(v -> {
            selectedAccountIndex = accountIndex;
            MiogramHaptic.perform(v, MiogramHaptic.KEYBOARD_TAP);
            updateAccountSelectionUI();
        });

        return holder;
    }

    private void updateAccountSelectionUI() {
        for (AccountHolder h : accountHolders) {
            boolean isSelected = (h.accountIndex == selectedAccountIndex);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(AndroidUtilities.dp(12));
            if (isSelected) {
                bg.setColor(0x2500F2FE);
                bg.setStroke(AndroidUtilities.dp(1.5f), 0xFF00F2FE);
                h.checkView.setText("●");
                h.checkView.setTextColor(0xFF00F2FE);
            } else {
                bg.setColor(0x11FFFFFF);
                bg.setStroke(AndroidUtilities.dp(1), 0x22FFFFFF);
                h.checkView.setText("○");
                h.checkView.setTextColor(0x44FFFFFF);
            }
            h.cardView.setBackground(bg);
        }
    }

    private LinearLayout createTikTokCard(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(12), AndroidUtilities.dp(10));

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(AndroidUtilities.dp(12));
        bg.setColor(0x18FF70A6);
        bg.setStroke(AndroidUtilities.dp(1), 0x44FF70A6);
        card.setBackground(bg);

        BackupImageView avatarView = new BackupImageView(context);
        avatarView.setRoundRadius(AndroidUtilities.dp(22));
        if (!TextUtils.isEmpty(tiktokAvatar)) {
            avatarView.setImage(ImageLocation.getForPath(tiktokAvatar), "80_80", null, 0, null);
        } else {
            avatarView.setImageResource(R.drawable.msg_fave);
        }
        card.addView(avatarView, LayoutHelper.createLinear(44, 44, 0, 0, 12, 0));

        LinearLayout textLayout = new LinearLayout(context);
        textLayout.setOrientation(LinearLayout.VERTICAL);
        card.addView(textLayout, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL));

        TextView nameText = new TextView(context);
        String displayName = !TextUtils.isEmpty(tiktokNickname) ? tiktokNickname : (!TextUtils.isEmpty(tiktokUsername) ? "@" + tiktokUsername : "TikTok MI User");
        nameText.setText(displayName);
        nameText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        nameText.setTypeface(AndroidUtilities.bold());
        nameText.setTextColor(0xFFFFFFFF);
        nameText.setSingleLine(true);
        textLayout.addView(nameText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView handleText = new TextView(context);
        String handle = !TextUtils.isEmpty(tiktokUsername) ? "@" + tiktokUsername : "TikTok MI Ecosystem";
        handleText.setText(handle);
        handleText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        handleText.setTextColor(0xFFFF70A6);
        textLayout.addView(handleText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        return card;
    }

    private void executeLinking(Context context) {
        TLRPC.User selectedUser = null;
        for (AccountHolder h : accountHolders) {
            if (h.accountIndex == selectedAccountIndex) {
                selectedUser = h.user;
                break;
            }
        }
        if (selectedUser == null) {
            selectedUser = UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser();
        }
        if (selectedUser == null) return;

        final long tgUserId = selectedUser.id;
        final String tgUsername = selectedUser.username != null ? selectedUser.username : "";
        final String effectiveTikTokUser = !TextUtils.isEmpty(tiktokUsername)
                ? tiktokUsername
                : (!TextUtils.isEmpty(tiktokNickname) ? tiktokNickname : "tiktok_user");

        // 1. Save link in AmegramTikTokManager
        AmegramTikTokManager.getInstance().setLinkedProfile(
                effectiveTikTokUser,
                !TextUtils.isEmpty(tiktokNickname) ? tiktokNickname : effectiveTikTokUser,
                tiktokAvatar,
                0, 0, 0, ""
        );
        AmegramTikTokManager.getInstance().setLinkedTelegramAccount(tgUserId, tgUsername);

        // 2. Sync to cloud presence
        MiogramCloudPresence.syncSelfToCloud(tgUserId);

        // 3. Broadcast to TikTok MI
        try {
            Intent broadcast = new Intent("mi.tiktokmi.ACTION_AMEGRAM_LINKED");
            broadcast.putExtra("tg_user_id", tgUserId);
            broadcast.putExtra("tg_username", tgUsername);
            broadcast.putExtra("tiktok_user", effectiveTikTokUser);
            context.sendBroadcast(broadcast);
        } catch (Throwable ignored) {
        }

        // 4. Success Toast
        String msg = MiogramLocale.get(
                "Акаунт @" + (!TextUtils.isEmpty(tgUsername) ? tgUsername : selectedUser.first_name) + " успішно прив'язано до TikTok MI ໒꒱",
                "Аккаунт @" + (!TextUtils.isEmpty(tgUsername) ? tgUsername : selectedUser.first_name) + " успешно привязан к TikTok MI ໒꒱",
                "Account @" + (!TextUtils.isEmpty(tgUsername) ? tgUsername : selectedUser.first_name) + " linked to TikTok MI ໒꒱"
        );
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show();

        // 5. Dismiss sheet
        AndroidUtilities.runOnUIThread(this::dismiss, 350);
    }
}
