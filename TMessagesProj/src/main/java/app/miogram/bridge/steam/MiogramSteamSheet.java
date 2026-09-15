package app.miogram.bridge.steam;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.ScaleStateListAnimator;

import app.miogram.bridge.MiogramLocale;
import app.miogram.bridge.customui.MiogramHaptic;

/**
 * Interactive Steam Profile & Gaming Hub BottomSheet.
 * Allows users to link their Steam profile (custom vanity URL, friend ID, or SteamID64),
 * toggle cloud broadcasting via Supabase, and preview their live Steam gaming card.
 */
public class MiogramSteamSheet extends BottomSheet {

    private final Theme.ResourcesProvider resourcesProvider;
    private final MiogramSteamManager steamManager;
    private final EditText inputField;
    private final TextView btnSave;
    private final RadialProgressView progressBar;
    private final FrameLayout previewContainer;
    private final MiogramSteamProfileCard previewCard;
    private final TextCheckCell broadcastToggleCell;
    private final TextView statusHintView;
    private LinearLayout watchContainer;
    private boolean watchRefreshing = false;

    public MiogramSteamSheet(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, false, resourcesProvider);
        this.resourcesProvider = resourcesProvider;
        this.steamManager = MiogramSteamManager.getInstance();

        setApplyBottomPadding(false);
        setApplyTopPadding(false);

        int bgColor = getThemedColor(Theme.key_dialogBackground);
        if (bgColor == 0) bgColor = 0xFF101822;
        fixNavigationBar(bgColor);

        int textColor = getThemedColor(Theme.key_dialogTextBlack);
        if (textColor == 0) textColor = 0xFFFFFFFF;
        int subTextColor = getThemedColor(Theme.key_dialogTextGray2);
        if (subTextColor == 0) subTextColor = 0xAAFFFFFF;

        ScrollView scrollView = new ScrollView(context);
        scrollView.setVerticalScrollBarEnabled(false);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bgColor);
        root.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(12), AndroidUtilities.dp(20), AndroidUtilities.dp(24));

        // Drag Handle
        View dragHandle = new View(context);
        GradientDrawable handleBg = new GradientDrawable();
        handleBg.setColor(0x44888888);
        handleBg.setCornerRadius(AndroidUtilities.dp(2.5f));
        dragHandle.setBackground(handleBg);
        root.addView(dragHandle, LayoutHelper.createLinear(38, 5, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 16));

        // Title
        TextView titleView = new TextView(context);
        titleView.setText(MiogramLocale.get("Steam Профіль", "Steam Профиль", "Steam Profile"));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(textColor);
        titleView.setGravity(Gravity.CENTER);
        root.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 4));

        // Subtitle
        TextView subtitleView = new TextView(context);
        subtitleView.setText(MiogramLocale.get(
                "Трансляція поточної гри, часу та додавання в друзі Steam",
                "Трансляция текущей игры, времени и добавление в друзья Steam",
                "Broadcast current game, playtime and quick add Steam friends"
        ));
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitleView.setTextColor(subTextColor);
        subtitleView.setGravity(Gravity.CENTER);
        root.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 18));

        // Input container
        LinearLayout inputCard = new LinearLayout(context);
        inputCard.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable inputCardBg = new GradientDrawable();
        inputCardBg.setColor(0x18FFFFFF);
        inputCardBg.setCornerRadius(AndroidUtilities.dp(14));
        inputCardBg.setStroke(AndroidUtilities.dp(1), 0x2266C0F4);
        inputCard.setBackground(inputCardBg);
        inputCard.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12), AndroidUtilities.dp(14), AndroidUtilities.dp(12));
        root.addView(inputCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));

        TextView inputLabel = new TextView(context);
        inputLabel.setText(MiogramLocale.get(
                "Посилання або нікнейм Steam:",
                "Ссылка или никнейм Steam:",
                "Steam URL or Custom ID:"
        ));
        inputLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        inputLabel.setTypeface(AndroidUtilities.bold());
        inputLabel.setTextColor(0xFF66C0F4);
        inputCard.addView(inputLabel, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

        LinearLayout inputRow = new LinearLayout(context);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        inputCard.addView(inputRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        inputField = new EditText(context);
        inputField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        inputField.setTextColor(textColor);
        inputField.setHintTextColor(0x77FFFFFF);
        inputField.setHint("steamcommunity.com/id/... або нік");
        inputField.setBackground(null);
        inputField.setSingleLine(true);
        inputField.setImeOptions(EditorInfo.IME_ACTION_DONE);
        String savedId = steamManager.getLinkedSteamId();
        if (!TextUtils.isEmpty(savedId)) {
            inputField.setText(savedId);
        }
        inputRow.addView(inputField, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL));

        progressBar = new RadialProgressView(context);
        progressBar.setSize(AndroidUtilities.dp(24));
        progressBar.setProgressColor(0xFF66C0F4);
        progressBar.setVisibility(View.GONE);
        inputRow.addView(progressBar, LayoutHelper.createLinear(24, 24, Gravity.CENTER_VERTICAL, 6, 0, 6, 0));

        btnSave = new TextView(context);
        btnSave.setText(MiogramLocale.get("Підключити", "Подключить", "Connect"));
        btnSave.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        btnSave.setTypeface(AndroidUtilities.bold());
        btnSave.setTextColor(0xFFFFFFFF);
        btnSave.setGravity(Gravity.CENTER);
        btnSave.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(8), AndroidUtilities.dp(14), AndroidUtilities.dp(8));
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(0xFF2B5E1B);
        btnBg.setCornerRadius(AndroidUtilities.dp(10));
        btnSave.setBackground(btnBg);
        ScaleStateListAnimator.apply(btnSave, 0.035f, 1.4f);
        btnSave.setOnClickListener(v -> checkAndConnect());
        inputRow.addView(btnSave, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        // Status / error hint
        statusHintView = new TextView(context);
        statusHintView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11.5f);
        statusHintView.setTextColor(0xAAFFFFFF);
        statusHintView.setText(MiogramLocale.get(
                "Профіль Steam має бути відкритим (Public) у налаштуваннях приватності Steam.",
                "Профиль Steam должен быть открытым (Public) в настройках приватности Steam.",
                "Your Steam Profile & Game Details must be set to Public in Steam Privacy Settings."
        ));
        inputCard.addView(statusHintView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));

        // Toggle broadcast cell
        broadcastToggleCell = new TextCheckCell(context, resourcesProvider);
        broadcastToggleCell.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(12), 0x12FFFFFF, 0x22FFFFFF));
        broadcastToggleCell.setTextAndCheck(
                MiogramLocale.get("Транслювати статус у Telegram", "Транслировать статус в Telegram", "Broadcast status to Telegram"),
                steamManager.isBroadcastEnabled(),
                false
        );
        broadcastToggleCell.setOnClickListener(v -> {
            boolean current = steamManager.isBroadcastEnabled();
            steamManager.setBroadcastEnabled(!current);
            broadcastToggleCell.setChecked(!current);
            MiogramHaptic.click(v);
        });
        root.addView(broadcastToggleCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        // Preview Section Label
        TextView previewLabel = new TextView(context);
        previewLabel.setText(MiogramLocale.get("Попередній перегляд картки:", "Предпросмотр карточки:", "Card Preview:"));
        previewLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        previewLabel.setTypeface(AndroidUtilities.bold());
        previewLabel.setTextColor(0x88FFFFFF);
        root.addView(previewLabel, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 4, 0, 0, 6));

        // Preview Card Container
        previewContainer = new FrameLayout(context);
        previewCard = new MiogramSteamProfileCard(context, resourcesProvider);
        previewCard.setPadding(0, 0, 0, 0);
        previewContainer.addView(previewCard, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        root.addView(previewContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        // Friends watchlist Section
        TextView watchLabel = new TextView(context);
        watchLabel.setText(MiogramLocale.get("Друзі (вотчліст):", "Друзья (вотчлист):", "Friends (watchlist):"));
        watchLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        watchLabel.setTypeface(AndroidUtilities.bold());
        watchLabel.setTextColor(0x88FFFFFF);
        root.addView(watchLabel, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 4, 0, 0, 6));

        watchContainer = new LinearLayout(context);
        watchContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(watchContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        TextView btnAddFriend = new TextView(context);
        btnAddFriend.setText(MiogramLocale.get("＋ Додати друга за посиланням вище", "＋ Добавить друга по ссылке выше", "＋ Watch friend from the link above"));
        btnAddFriend.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        btnAddFriend.setTypeface(AndroidUtilities.bold());
        btnAddFriend.setTextColor(0xFF66C0F4);
        btnAddFriend.setGravity(Gravity.CENTER);
        btnAddFriend.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10), AndroidUtilities.dp(16), AndroidUtilities.dp(10));
        GradientDrawable addFriendBg = new GradientDrawable();
        addFriendBg.setColor(0x1866C0F4);
        addFriendBg.setCornerRadius(AndroidUtilities.dp(12));
        addFriendBg.setStroke(AndroidUtilities.dp(1), 0x3366C0F4);
        btnAddFriend.setBackground(addFriendBg);
        ScaleStateListAnimator.apply(btnAddFriend, 0.035f, 1.4f);
        btnAddFriend.setOnClickListener(v -> {
            MiogramHaptic.click(v);
            String q = inputField.getText() != null ? inputField.getText().toString().trim() : "";
            if (TextUtils.isEmpty(q)) {
                Toast.makeText(getContext(), MiogramLocale.get("Вставте посилання на профіль друга вище", "Вставьте ссылку на профиль друга выше", "Paste a friend's profile link above first"), Toast.LENGTH_SHORT).show();
                return;
            }
            progressBar.setVisibility(View.VISIBLE);
            steamManager.resolvePublicSteam(q, profile -> {
                progressBar.setVisibility(View.GONE);
                if (profile != null && !TextUtils.isEmpty(profile.steamId)) {
                    steamManager.addWatchedId(profile.steamId);
                    refreshWatchList();
                    Toast.makeText(getContext(), MiogramLocale.get("Додано в вотчліст: ", "Добавлен в вотчлист: ", "Watching: ") + profile.personaName, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getContext(), MiogramLocale.get("Не вдалося знайти профіль", "Не удалось найти профиль", "Could not resolve the profile"), Toast.LENGTH_SHORT).show();
                }
            });
        });
        root.addView(btnAddFriend, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        // Open Steam App Button
        TextView btnOpenSteam = new TextView(context);
        btnOpenSteam.setText(MiogramLocale.get("Відкрити додаток Steam", "Открыть приложение Steam", "Open Steam App"));
        btnOpenSteam.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        btnOpenSteam.setTypeface(AndroidUtilities.bold());
        btnOpenSteam.setTextColor(0xFF66C0F4);
        btnOpenSteam.setGravity(Gravity.CENTER);
        btnOpenSteam.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
        GradientDrawable openSteamBg = new GradientDrawable();
        openSteamBg.setColor(0x1866C0F4);
        openSteamBg.setCornerRadius(AndroidUtilities.dp(12));
        openSteamBg.setStroke(AndroidUtilities.dp(1), 0x3366C0F4);
        btnOpenSteam.setBackground(openSteamBg);
        ScaleStateListAnimator.apply(btnOpenSteam, 0.035f, 1.4f);
        btnOpenSteam.setOnClickListener(v -> {
            MiogramHaptic.click(v);
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("steam://open/main"));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Throwable t) {
                try {
                    Intent web = new Intent(Intent.ACTION_VIEW, Uri.parse("https://store.steampowered.com/"));
                    web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(web);
                } catch (Throwable ignore) {}
            }
        });
        root.addView(btnOpenSteam, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        scrollView.addView(root);
        setCustomView(scrollView);

        // Initial preview load if already linked
        if (!TextUtils.isEmpty(savedId)) {
            loadPreview(savedId);
        } else {
            previewContainer.setVisibility(View.GONE);
        }
        refreshWatchList();
    }

    private void refreshWatchList() {
        if (watchContainer == null || watchRefreshing) return;
        watchRefreshing = true;
        steamManager.refreshWatched((profiles, events) -> {
            watchRefreshing = false;
            if (watchContainer == null) return;
            Context ctx = getContext();
            watchContainer.removeAllViews();
            if (profiles.isEmpty()) {
                TextView empty = new TextView(ctx);
                empty.setText(MiogramLocale.get("Поки нікого. Вставте посилання друга вище і натисніть «＋».",
                        "Пока никого. Вставьте ссылку друга выше и нажмите «＋».",
                        "Nobody yet. Paste a friend's link above and tap ＋."));
                empty.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
                empty.setTextColor(0x77FFFFFF);
                empty.setGravity(Gravity.CENTER);
                empty.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4));
                watchContainer.addView(empty, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                return;
            }
            for (MiogramSteamManager.SteamProfile p : profiles) {
                watchContainer.addView(buildWatchRow(ctx, p),
                        LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));
            }
        });
    }

    private View buildWatchRow(Context ctx, MiogramSteamManager.SteamProfile p) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x18FFFFFF);
        bg.setCornerRadius(AndroidUtilities.dp(12));
        row.setBackground(bg);
        row.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(8), AndroidUtilities.dp(12), AndroidUtilities.dp(8));

        boolean inGame = p.isLiveGame() && !TextUtils.isEmpty(p.gameName);
        View dot = new View(ctx);
        dot.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(5),
                inGame ? 0xFF66C0F4 : 0xFF23A55A));
        row.addView(dot, LayoutHelper.createLinear(10, 10, Gravity.CENTER_VERTICAL, 0, 0, 8, 0));

        LinearLayout texts = new LinearLayout(ctx);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(ctx);
        name.setText(p.personaName != null && !p.personaName.isEmpty() ? p.personaName : p.steamId);
        name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        name.setTypeface(AndroidUtilities.bold());
        name.setTextColor(0xFFFFFFFF);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(name, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        TextView status = new TextView(ctx);
        String st = inGame ? p.gameName
                : (!TextUtils.isEmpty(p.stateMessage) ? p.stateMessage : MiogramLocale.get("Офлайн", "Оффлайн", "Offline"));
        status.setText(st);
        status.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        status.setTextColor(inGame ? 0xFF66C0F4 : 0x88FFFFFF);
        status.setSingleLine(true);
        status.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(status, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        row.addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL));

        row.setOnClickListener(v -> {
            MiogramHaptic.click(v);
            showFriendActions(p);
        });
        return row;
    }

    private void showFriendActions(MiogramSteamManager.SteamProfile p) {
        Context ctx = getContext();
        if (ctx == null) return;
        org.telegram.ui.ActionBar.AlertDialog.Builder builder =
                new org.telegram.ui.ActionBar.AlertDialog.Builder(ctx);
        builder.setTitle(p.personaName != null ? p.personaName : p.steamId);
        java.util.ArrayList<CharSequence> items = new java.util.ArrayList<>();
        java.util.ArrayList<Runnable> actions = new java.util.ArrayList<>();
        boolean inGame = p.isLiveGame() && !TextUtils.isEmpty(p.gameName) && !TextUtils.isEmpty(p.gameId);
        if (inGame) {
            items.add(MiogramLocale.get("Запустити гру", "Запустить игру", "Launch game"));
            actions.add(() -> steamManager.openGame(ctx, p.gameId));
        }
        items.add(MiogramLocale.get("Профіль Steam", "Профиль Steam", "Steam profile"));
        actions.add(() -> steamManager.openProfile(ctx, p.profileUrl, p.steamId));
        items.add(MiogramLocale.get("Запросити в друзі", "Пригласить в друзья", "Send friend request"));
        actions.add(() -> steamManager.addFriend(ctx, p.steamId));
        items.add(MiogramLocale.get("Прибрати з вотчліста", "Убрать из вотчлиста", "Stop watching"));
        actions.add(() -> {
            steamManager.removeWatchedId(p.steamId);
            refreshWatchList();
        });
        builder.setItems(items.toArray(new CharSequence[0]), (d, which) -> {
            if (which >= 0 && which < actions.size()) {
                try {
                    actions.get(which).run();
                } catch (Throwable ignore) {}
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        try {
            builder.create().show();
        } catch (Throwable ignore) {}
    }

    private void checkAndConnect() {
        String query = inputField.getText() != null ? inputField.getText().toString().trim() : "";
        if (TextUtils.isEmpty(query)) {
            Toast.makeText(getContext(), MiogramLocale.get("Введіть нік або посилання Steam", "Введите ник или ссылку Steam", "Enter Steam username or URL"), Toast.LENGTH_SHORT).show();
            return;
        }

        MiogramHaptic.click(btnSave);
        progressBar.setVisibility(View.VISIBLE);
        btnSave.setEnabled(false);
        btnSave.setAlpha(0.6f);

        steamManager.resolvePublicSteam(query, profile -> {
            progressBar.setVisibility(View.GONE);
            btnSave.setEnabled(true);
            btnSave.setAlpha(1f);

            if (profile != null) {
                String idToSave = !TextUtils.isEmpty(profile.steamId) ? profile.steamId : query;
                steamManager.setLinkedSteamId(idToSave);
                long myUserId = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
                steamManager.syncSelfToCloud(myUserId, profile, null);

                previewContainer.setVisibility(View.VISIBLE);
                previewCard.setProfile(profile);

                BulletinFactory.global().createSimpleBulletin(
                        R.drawable.baseline_videogame_asset_16,
                        MiogramLocale.get("Steam підключено успішно!", "Steam успешно подключен!", "Steam connected successfully!")
                ).show();
            } else {
                statusHintView.setTextColor(0xFFFF6B6B);
                statusHintView.setText(MiogramLocale.get(
                        "⚠️ Не вдалося знайти або профіль закрито. Введіть пряме посилання (steamcommunity.com/id/...) або 17-значний SteamID64, та перевірте публічність профілю у Steam.",
                        "⚠️ Не удалось найти или профиль закрыт. Введите прямую ссылку (steamcommunity.com/id/...) или 17-значный SteamID64, и проверьте публичность профиля в Steam.",
                        "⚠️ Could not resolve Steam profile. Please enter your direct URL (steamcommunity.com/id/...) or 17-digit SteamID64, and ensure profile is Public."
                ));
            }
        });
    }

    private void loadPreview(String query) {
        steamManager.resolvePublicSteam(query, profile -> {
            if (profile != null) {
                previewContainer.setVisibility(View.VISIBLE);
                previewCard.setProfile(profile);
            }
        });
    }
}
