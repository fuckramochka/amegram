package app.miogram.bridge.cloudvault;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import android.graphics.Outline;
import android.graphics.Rect;
import android.view.ViewOutlineProvider;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import app.amegram.settings.ui.YumiBackgroundView;
import app.amegram.theme.YumiMd3;
import app.miogram.bridge.MiogramLocale;
import app.miogram.bridge.customui.MiogramHaptic;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.TopicsController;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.TopicsFragment;
import org.telegram.messenger.MessagesStorage;
import androidx.core.content.FileProvider;
import org.telegram.ui.Components.LayoutHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Modern Cloud Drive UI for the Miogram Encrypted Cloud Vault:
 * - Google Drive / Nextcloud-style virtual file directory.
 * - Forum Supergroup topics serve as cloud folders.
 * - Files > 2GB are transparently sliced into AES-256 encrypted chunks.
 * - Offline cached indexing + instant cloud sync.
 */
public class MiogramCloudVaultActivity extends BaseFragment {

    private static final int MENU_SEARCH = 1;
    private static final int MENU_OTHER = 2;
    private static final int MENU_VIEW_MODE = 3;
    private static final int SUBMENU_CHAT = 101;
    private static final int SUBMENU_SYNC = 102;
    private static final int SUBMENU_KEY = 103;
    private static final int SUBMENU_UNLINK = 104;
    private static final int SUBMENU_ENCRYPTION = 105;

    public static final int VIEW_TYPE_LIST = 0;
    public static final int VIEW_TYPE_GRID = 1;

    public static final int CATEGORY_ALL = 0;
    public static final int CATEGORY_MEDIA = 1;
    public static final int CATEGORY_DOCS = 2;
    public static final int CATEGORY_AUDIO = 3;
    public static final int CATEGORY_ARCHIVES = 4;

    private static final int REQUEST_PICK_FILE = 2101;

    private FrameLayout rootLayout;
    private LinearLayout onboardingLayout;
    private LinearLayout mainContentLayout;

    private TextView folderChipTitle;
    private TextView secBadgeChip;
    private LinearLayout categoryChipsContainer;
    private RecyclerView filesRecyclerView;
    private FilesAdapter filesAdapter;
    private LinearLayout emptyView;
    private TextView emptyText;
    private TextView emptyHint;
    private FrameLayout fabButton;

    private int currentCategoryFilter = CATEGORY_ALL;
    private int currentViewMode = VIEW_TYPE_GRID; // Default to Gallery!
    private ActionBarMenuItem viewModeItem;

    private long currentSelectedTopicId = 0; // 0 = All files
    private String currentSelectedTopicName = "";
    private String currentSearchQuery = "";

    private final ArrayList<MiogramCloudVaultFile> displayedFiles = new ArrayList<>();
    private final ArrayList<TLRPC.TL_forumTopic> cachedTopics = new ArrayList<>();

    /** Блок 13: фоновий пул прев'ю (завантаження/розшифровка поза UI), UI-шар; протокол чанків не чіпаємо. */
    private final ExecutorService vaultPreviewExecutor = Executors.newCachedThreadPool();
    /** fileId → рядковий статус "Розшифровка N/M чанків… %" для рядка файла. */
    private final HashMap<String, String> vaultPreviewStatus = new HashMap<>();

    @Override
    public View createView(Context context) {
        currentViewMode = ApplicationLoader.applicationContext
                .getSharedPreferences("miogram_cloud_vault_prefs", Context.MODE_PRIVATE)
                .getInt("vault_view_mode", VIEW_TYPE_GRID);

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(MiogramLocale.get("Хмарне сховище", "Облачное хранилище", "Cloud Vault"));
        actionBar.setBackgroundColor(YumiMd3.surface());
        actionBar.setTitleColor(YumiMd3.onSurface());
        actionBar.setSubtitleColor(YumiMd3.onSurfaceVariant());
        actionBar.setItemsColor(YumiMd3.onSurface(), false);
        actionBar.setItemsBackgroundColor(YumiMd3.defaultRippleColor(), false);
        updateSubtitle();

        ActionBarMenu menu = actionBar.createMenu();
        ActionBarMenuItem searchItem = menu.addItem(MENU_SEARCH, R.drawable.outline_header_search);
        searchItem.setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
            @Override
            public void onSearchExpand() {
            }

            @Override
            public void onSearchCollapse() {
                currentSearchQuery = "";
                filterAndReloadFiles();
            }

            @Override
            public void onTextChanged(EditText editText) {
                currentSearchQuery = editText.getText().toString().trim();
                filterAndReloadFiles();
            }
        });
        searchItem.setSearchFieldHint(MiogramLocale.get("Пошук файлів...", "Поиск файлов...", "Search files..."));

        viewModeItem = menu.addItem(MENU_VIEW_MODE, currentViewMode == VIEW_TYPE_GRID ? R.drawable.ic_filter_list : R.drawable.files_gallery);

        ActionBarMenuItem otherItem = menu.addItem(MENU_OTHER, R.drawable.ic_ab_other);
        otherItem.addSubItem(SUBMENU_CHAT, R.drawable.msg_channel, MiogramLocale.get("Відкрити форум у чаті", "Открыть форум в чате", "Open Forum in Chat"));
        otherItem.addSubItem(SUBMENU_SYNC, R.drawable.msg_retry, MiogramLocale.get("Синхронізувати з хмарою", "Синхронизировать с облаком", "Sync with Cloud"));
        otherItem.addSubItem(SUBMENU_ENCRYPTION, R.drawable.msg_secret,
                MiogramCloudVaultEngine.isEncryptionEnabled(currentAccount)
                        ? MiogramLocale.get("Шифрування: Увімкнено (AES-256)", "Шифрование: Включено (AES-256)", "Encryption: Enabled (AES-256)")
                        : MiogramLocale.get("Шифрування: Вимкнено", "Шифрование: Выключено", "Encryption: Disabled"));
        otherItem.addSubItem(SUBMENU_KEY, R.drawable.msg_secret, MiogramLocale.get("Ключ шифрування (AES-256)", "Ключ шифрования (AES-256)", "Encryption Key (AES-256)"));
        otherItem.addSubItem(SUBMENU_UNLINK, R.drawable.msg_delete, MiogramLocale.get("Відв'язати супергрупу", "Отвязать супергруппу", "Unlink Vault Chat"));

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == MENU_VIEW_MODE) {
                    toggleViewMode();
                } else if (id == SUBMENU_CHAT) {
                    openVaultChat();
                } else if (id == SUBMENU_SYNC) {
                    syncFromCloud();
                } else if (id == SUBMENU_ENCRYPTION) {
                    toggleEncryption();
                } else if (id == SUBMENU_KEY) {
                    showMasterKeyDialog();
                } else if (id == SUBMENU_UNLINK) {
                    showUnlinkDialog();
                }
            }
        });

        rootLayout = new FrameLayout(context);
        YumiBackgroundView bgView = new YumiBackgroundView(context);
        rootLayout.addView(bgView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        buildOnboardingView(context);
        buildMainContentView(context);
        buildFab(context);

        updateVaultVisibility();
        fragmentView = rootLayout;
        return fragmentView;
    }

    private void toggleViewMode() {
        currentViewMode = (currentViewMode == VIEW_TYPE_GRID) ? VIEW_TYPE_LIST : VIEW_TYPE_GRID;
        ApplicationLoader.applicationContext
                .getSharedPreferences("miogram_cloud_vault_prefs", Context.MODE_PRIVATE)
                .edit()
                .putInt("vault_view_mode", currentViewMode)
                .apply();
        if (viewModeItem != null) {
            viewModeItem.setIcon(currentViewMode == VIEW_TYPE_GRID ? R.drawable.ic_filter_list : R.drawable.files_gallery);
        }
        updateLayoutManager();
        if (filesAdapter != null) {
            filesAdapter.notifyDataSetChanged();
        }
    }

    private void updateLayoutManager() {
        if (filesRecyclerView == null) return;
        Context context = getContext();
        if (context == null) context = getParentActivity();
        if (context == null) return;
        if (currentViewMode == VIEW_TYPE_GRID) {
            int screenWidth = AndroidUtilities.displaySize.x;
            int spanCount = Math.max(2, screenWidth / AndroidUtilities.dp(120));
            filesRecyclerView.setLayoutManager(new GridLayoutManager(context, spanCount));
        } else {
            filesRecyclerView.setLayoutManager(new LinearLayoutManager(context));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        long vaultChatId = MiogramCloudVaultEngine.getVaultChatId(currentAccount);
        if (MiogramCloudVaultEngine.isSavedMessagesVault(currentAccount, vaultChatId)) {
            migrateVaultToForumChat();
            return;
        }
        updateVaultVisibility();
        if (MiogramCloudVaultEngine.hasVault(currentAccount)) {
            loadTopicsFromTelegram();
            syncFromCloud();
        }
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        // Zero-Knowledge: wipe all in-memory manifests and temp chunks when exiting vault
        MiogramCloudVaultEngine.clearMemoryFiles();
        MiogramCloudVaultEngine.cleanupTempFiles(ApplicationLoader.applicationContext);
        try {
            vaultPreviewExecutor.shutdownNow();
        } catch (Throwable ignore) {
        }
        // Прев'ю розшифровок лежать тільки в cache/vault_preview/ — тремо при виході (Zero-Knowledge).
        try {
            android.content.Context ctx = ApplicationLoader.applicationContext;
            if (ctx != null && ctx.getCacheDir() != null) {
                File dir = new File(ctx.getCacheDir(), "vault_preview");
                File[] kids = dir.listFiles();
                if (kids != null) {
                    for (File k : kids) {
                        try {
                            if (k != null && k.isFile()) k.delete();
                        } catch (Throwable ignore) {
                        }
                    }
                }
            }
        } catch (Throwable ignore) {
        }
    }

    private void toggleEncryption() {
        boolean next = !MiogramCloudVaultEngine.isEncryptionEnabled(currentAccount);
        MiogramCloudVaultEngine.setEncryptionEnabled(currentAccount, next);
        updateSubtitle();
        updateSecBadgeChip();
        Toast.makeText(getParentActivity() != null ? getParentActivity() : getContext(), next
                ? MiogramLocale.get("Шифрування AES-256-GCM увімкнено", "Шифрование AES-256-GCM включено", "AES-256-GCM encryption enabled")
                : MiogramLocale.get("Шифрування вимкнено (прямий диск)", "Шифрование выключено (прямой диск)", "Encryption disabled (direct drive)"),
                Toast.LENGTH_SHORT).show();
    }

    private void updateSubtitle() {
        if (actionBar == null) return;
        long totalSize = MiogramCloudVaultEngine.getTotalVaultSize();
        int count = MiogramCloudVaultEngine.getTotalVaultFilesCount();
        boolean enc = MiogramCloudVaultEngine.isEncryptionEnabled(currentAccount);
        String prefix = enc ? "🔒 AES-256 • " : "📁 " + MiogramLocale.get("Прямий диск • ", "Прямой диск • ", "Direct • ");
        String sub = prefix
                + count + " " + MiogramLocale.get("файлів", "файлов", "files")
                + " (" + AndroidUtilities.formatFileSize(totalSize) + ")";
        actionBar.setSubtitle(sub);
    }

    private void updateFolderChipText() {
        if (folderChipTitle == null) return;
        if (currentSelectedTopicId == 0L || TextUtils.isEmpty(currentSelectedTopicName)) {
            folderChipTitle.setText(MiogramLocale.get("Усі файли", "Все файлы", "All files"));
        } else {
            folderChipTitle.setText(currentSelectedTopicName);
        }
    }

    private void updateSecBadgeChip() {
        if (secBadgeChip == null) return;
        boolean enc = MiogramCloudVaultEngine.isEncryptionEnabled(currentAccount);
        if (enc) {
            secBadgeChip.setText("🔒 AES-256");
            secBadgeChip.setTextColor(YumiMd3.green());
            secBadgeChip.setBackground(YumiMd3.rounded(YumiMd3.tone(YumiMd3.green(), 0.16f), YumiMd3.RADIUS_FULL_DP));
        } else {
            secBadgeChip.setText(MiogramLocale.get("Прямий диск", "Прямой диск", "Direct drive"));
            secBadgeChip.setTextColor(YumiMd3.blue());
            secBadgeChip.setBackground(YumiMd3.rounded(YumiMd3.tone(YumiMd3.blue(), 0.16f), YumiMd3.RADIUS_FULL_DP));
        }
    }

    private void showFolderSelectorDialog() {
        if (getParentActivity() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(MiogramLocale.get("Папки сховища", "Папки хранилища", "Vault folders"));

        ArrayList<String> names = new ArrayList<>();
        ArrayList<Long> ids = new ArrayList<>();

        names.add(MiogramLocale.get("📁 Усі файли", "📁 Все файлы", "📁 All files"));
        ids.add(0L);

        for (TLRPC.TL_forumTopic t : cachedTopics) {
            names.add("📁 " + (t.title != null ? t.title : ("Папка #" + t.id)));
            ids.add((long) t.id);
        }

        names.add("➕ " + MiogramLocale.get("Створити нову папку", "Создать новую папку", "Create new folder"));
        ids.add(-1L);

        CharSequence[] items = names.toArray(new CharSequence[0]);
        builder.setItems(items, (d, which) -> {
            long selectedId = ids.get(which);
            if (selectedId == -1L) {
                promptCreateFolder();
            } else {
                currentSelectedTopicId = selectedId;
                currentSelectedTopicName = (selectedId == 0L) ? "" : names.get(which).replace("📁 ", "");
                updateFolderChipText();
                filterAndReloadFiles();
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void updateVaultVisibility() {
        boolean hasVault = MiogramCloudVaultEngine.hasVault(currentAccount);
        if (onboardingLayout != null) onboardingLayout.setVisibility(hasVault ? View.GONE : View.VISIBLE);
        if (mainContentLayout != null) mainContentLayout.setVisibility(hasVault ? View.VISIBLE : View.GONE);
        if (fabButton != null) fabButton.setVisibility(hasVault ? View.VISIBLE : View.GONE);
    }

    // --- Onboarding / Welcome Hero View ---

    private void buildOnboardingView(Context context) {
        onboardingLayout = new LinearLayout(context);
        onboardingLayout.setOrientation(LinearLayout.VERTICAL);
        onboardingLayout.setGravity(Gravity.CENTER);
        onboardingLayout.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(24), AndroidUtilities.dp(20), AndroidUtilities.dp(24));

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackground(YumiMd3.rounded(YumiMd3.surfaceContainer(), YumiMd3.hairline(), YumiMd3.RADIUS_XXL_DP, 1));
        card.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(28), AndroidUtilities.dp(24), AndroidUtilities.dp(28));

        FrameLayout iconFrame = new FrameLayout(context);
        iconFrame.setBackground(YumiMd3.rounded(YumiMd3.iconTone(YumiMd3.primary(), 0.14f), YumiMd3.RADIUS_L_DP));
        ImageView iconView = new ImageView(context);
        iconView.setImageResource(R.drawable.cloud);
        iconView.setColorFilter(YumiMd3.primary());
        iconFrame.addView(iconView, LayoutHelper.createFrame(34, 34, Gravity.CENTER));
        card.addView(iconFrame, LayoutHelper.createLinear(68, 68, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 16));

        TextView title = new TextView(context);
        title.setText("Miogram Cloud Vault");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, YumiMd3.TITLE_LARGE);
        title.setTypeface(YumiMd3.typeface(YumiMd3.BOLD));
        title.setTextColor(YumiMd3.onSurface());
        title.setGravity(Gravity.CENTER);
        card.addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 6));

        TextView desc = new TextView(context);
        desc.setText(MiogramLocale.get(
                "Приватне хмарне сховище без обмежень на базі Telegram",
                "Приватное облачное хранилище без ограничений на базе Telegram",
                "Private unlimited cloud vault powered by Telegram"
        ));
        desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, YumiMd3.BODY_SMALL);
        desc.setTextColor(YumiMd3.onSurfaceVariant());
        desc.setGravity(Gravity.CENTER);
        card.addView(desc, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 20));

        card.addView(createFeatureRow(context, R.drawable.msg_secret, YumiMd3.violet(),
                MiogramLocale.get("Zero-Knowledge AES-256", "Zero-Knowledge AES-256", "Zero-Knowledge AES-256"),
                MiogramLocale.get("Шифрування файлів локальним ключем перед відправкою.", "Шифрование файлов локальным ключом перед отправкой.", "Client-side file encryption before upload.")));

        card.addView(createFeatureRow(context, R.drawable.cloud, YumiMd3.cyan(),
                MiogramLocale.get("Безлімітне сховище", "Безлимитное хранилище", "Unlimited Storage"),
                MiogramLocale.get("Файли будь-якого розміру автоматично нарізаються на чанки.", "Файлы любого размера автоматически нарезаются на чанки.", "Large files transparently sliced into cloud chunks.")));

        card.addView(createFeatureRow(context, R.drawable.msg_folders, YumiMd3.amber(),
                MiogramLocale.get("Папки та топіки", "Папки и топики", "Folders & Topics"),
                MiogramLocale.get("Топіки форум-супергрупи стають окремими папками сховища.", "Топики форум-супергруппы становятся отдельными папками хранилища.", "Forum supergroup topics act as virtual folders.")));

        View spacer = new View(context);
        card.addView(spacer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 18));

        TextView createBtn = new TextView(context);
        createBtn.setText(MiogramLocale.get("Створити сховище з топіками (1 тап)", "Создать хранилище с топиками (1 тап)", "Create Vault with Topics (1 Tap)"));
        createBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        createBtn.setTypeface(YumiMd3.typeface(YumiMd3.BOLD));
        createBtn.setTextColor(YumiMd3.onActionGradient());
        createBtn.setGravity(Gravity.CENTER);
        createBtn.setBackground(YumiMd3.gradient(YumiMd3.actionGradientStart(), YumiMd3.actionGradientEnd(), YumiMd3.RADIUS_M_DP));
        createBtn.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(14));
        createBtn.setOnClickListener(v -> {
            MiogramHaptic.tap(v);
            createVaultAutomatically();
        });
        card.addView(createBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 0, 0, 10));

        TextView linkBtn = new TextView(context);
        linkBtn.setText(MiogramLocale.get("Прив'язати існуючу групу чи форум", "Привязать существующую группу или форум", "Link Existing Group or Forum"));
        linkBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        linkBtn.setTypeface(YumiMd3.typeface(YumiMd3.MEDIUM));
        linkBtn.setTextColor(YumiMd3.primary());
        linkBtn.setGravity(Gravity.CENTER);
        linkBtn.setBackground(YumiMd3.ripple(YumiMd3.surfaceContainerHigh(), YumiMd3.RADIUS_M_DP, YumiMd3.defaultRippleColor()));
        linkBtn.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(11), AndroidUtilities.dp(16), AndroidUtilities.dp(11));
        linkBtn.setOnClickListener(v -> {
            MiogramHaptic.tap(v);
            showLinkExistingDialog();
        });
        card.addView(linkBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
        clp.gravity = Gravity.CENTER;
        onboardingLayout.addView(card, clp);

        rootLayout.addView(onboardingLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));
    }

    private View createFeatureRow(Context context, int iconRes, int accentColor, String title, String subtitle) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, AndroidUtilities.dp(7), 0, AndroidUtilities.dp(7));

        FrameLayout iconFrame = new FrameLayout(context);
        iconFrame.setBackground(YumiMd3.rounded(YumiMd3.iconTone(accentColor, 0.16f), YumiMd3.RADIUS_S_DP));
        ImageView iv = new ImageView(context);
        iv.setImageResource(iconRes);
        iv.setColorFilter(accentColor);
        iconFrame.addView(iv, LayoutHelper.createFrame(20, 20, Gravity.CENTER));
        row.addView(iconFrame, LayoutHelper.createLinear(38, 38, Gravity.CENTER_VERTICAL, 0, 0, 14, 0));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);

        TextView tView = new TextView(context);
        tView.setText(title);
        tView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tView.setTypeface(YumiMd3.typeface(YumiMd3.MEDIUM));
        tView.setTextColor(YumiMd3.onSurface());
        texts.addView(tView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView sView = new TextView(context);
        sView.setText(subtitle);
        sView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        sView.setTextColor(YumiMd3.onSurfaceVariant());
        texts.addView(sView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        row.addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, Gravity.CENTER_VERTICAL));
        return row;
    }

    // --- Main Content View (Drive Interface) ---

    private void buildMainContentView(Context context) {
        mainContentLayout = new LinearLayout(context);
        mainContentLayout.setOrientation(LinearLayout.VERTICAL);

        // 1. Sub-Header: Folder Selector Chip (left) + E2E Encryption Badge (right)
        LinearLayout subHeader = new LinearLayout(context);
        subHeader.setOrientation(LinearLayout.HORIZONTAL);
        subHeader.setGravity(Gravity.CENTER_VERTICAL);
        subHeader.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(10), AndroidUtilities.dp(14), AndroidUtilities.dp(6));

        // Folder Chip (Dropdown)
        LinearLayout folderChip = new LinearLayout(context);
        folderChip.setOrientation(LinearLayout.HORIZONTAL);
        folderChip.setGravity(Gravity.CENTER_VERTICAL);
        folderChip.setBackground(YumiMd3.rounded(YumiMd3.surfaceContainerHigh(), YumiMd3.hairline(), YumiMd3.RADIUS_FULL_DP, 1));
        folderChip.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(6), AndroidUtilities.dp(10), AndroidUtilities.dp(6));

        ImageView folderIco = new ImageView(context);
        folderIco.setImageResource(R.drawable.msg_folders);
        folderIco.setColorFilter(YumiMd3.primary());
        folderChip.addView(folderIco, LayoutHelper.createLinear(16, 16, Gravity.CENTER_VERTICAL, 0, 0, 6, 0));

        folderChipTitle = new TextView(context);
        folderChipTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        folderChipTitle.setTypeface(YumiMd3.typeface(YumiMd3.MEDIUM));
        folderChipTitle.setTextColor(YumiMd3.onSurface());
        updateFolderChipText();
        folderChip.addView(folderChipTitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 0, 0, 4, 0));

        ImageView chevron = new ImageView(context);
        chevron.setImageResource(R.drawable.ic_arrow_drop_down);
        chevron.setColorFilter(YumiMd3.onSurfaceVariant());
        folderChip.addView(chevron, LayoutHelper.createLinear(16, 16, Gravity.CENTER_VERTICAL));

        folderChip.setOnClickListener(v -> {
            MiogramHaptic.tap(v);
            showFolderSelectorDialog();
        });
        subHeader.addView(folderChip, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        // Spacer
        View spacer = new View(context);
        subHeader.addView(spacer, LayoutHelper.createLinear(0, 0, 1.0f));

        // Encryption Badge Chip
        secBadgeChip = new TextView(context);
        secBadgeChip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        secBadgeChip.setTypeface(YumiMd3.typeface(YumiMd3.BOLD));
        secBadgeChip.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(5), AndroidUtilities.dp(10), AndroidUtilities.dp(5));
        updateSecBadgeChip();
        secBadgeChip.setOnClickListener(v -> {
            MiogramHaptic.tap(v);
            toggleEncryption();
        });
        subHeader.addView(secBadgeChip, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        mainContentLayout.addView(subHeader, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // 2. Category Filter Chips (Horizontal Scroll)
        HorizontalScrollView catScrollView = new HorizontalScrollView(context);
        catScrollView.setHorizontalScrollBarEnabled(false);

        categoryChipsContainer = new LinearLayout(context);
        categoryChipsContainer.setOrientation(LinearLayout.HORIZONTAL);
        categoryChipsContainer.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(2), AndroidUtilities.dp(12), AndroidUtilities.dp(6));
        catScrollView.addView(categoryChipsContainer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));
        mainContentLayout.addView(catScrollView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        refreshCategoryChips();

        // 3. RecyclerView for Files
        FrameLayout listContainer = new FrameLayout(context);

        filesRecyclerView = new RecyclerView(context);
        filesRecyclerView.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(2), AndroidUtilities.dp(6), AndroidUtilities.dp(84));
        filesRecyclerView.setClipToPadding(false);
        updateLayoutManager();
        filesRecyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                if (currentViewMode == VIEW_TYPE_GRID) {
                    int s = AndroidUtilities.dp(4);
                    outRect.set(s, s, s, s);
                } else {
                    outRect.set(0, 0, 0, 0);
                }
            }
        });
        filesAdapter = new FilesAdapter();
        filesRecyclerView.setAdapter(filesAdapter);
        listContainer.addView(filesRecyclerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // Empty view
        emptyView = new LinearLayout(context);
        emptyView.setOrientation(LinearLayout.VERTICAL);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(32), AndroidUtilities.dp(24), AndroidUtilities.dp(32));

        FrameLayout emptyIcoFrame = new FrameLayout(context);
        emptyIcoFrame.setBackground(YumiMd3.rounded(YumiMd3.tone(YumiMd3.primary(), 0.12f), YumiMd3.RADIUS_XL_DP));
        ImageView emptyIco = new ImageView(context);
        emptyIco.setImageResource(R.drawable.baseline_cloud_download_24);
        emptyIco.setColorFilter(YumiMd3.primary());
        emptyIcoFrame.addView(emptyIco, LayoutHelper.createFrame(32, 32, Gravity.CENTER));
        emptyView.addView(emptyIcoFrame, LayoutHelper.createLinear(64, 64, Gravity.CENTER, 0, 0, 0, 16));

        emptyText = new TextView(context);
        emptyText.setText(MiogramLocale.get("У цій папці поки немає файлів", "В этой папке пока нет файлов", "No files in this folder yet"));
        emptyText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        emptyText.setTypeface(YumiMd3.typeface(YumiMd3.BOLD));
        emptyText.setTextColor(YumiMd3.onSurface());
        emptyText.setGravity(Gravity.CENTER);
        emptyView.addView(emptyText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 0, 0, 6));

        emptyHint = new TextView(context);
        emptyHint.setText(MiogramLocale.get("Натисніть (+), щоб завантажити файл будь-якого розміру", "Нажмите (+), чтобы загрузить файл любого размера", "Tap (+) to upload files of any size"));
        emptyHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        emptyHint.setTextColor(YumiMd3.onSurfaceVariant());
        emptyHint.setGravity(Gravity.CENTER);
        emptyView.addView(emptyHint, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 0, 0, 18));

        TextView uploadEmptyBtn = new TextView(context);
        uploadEmptyBtn.setText(MiogramLocale.get("Завантажити файл", "Загрузить файл", "Upload file"));
        uploadEmptyBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        uploadEmptyBtn.setTypeface(YumiMd3.typeface(YumiMd3.MEDIUM));
        uploadEmptyBtn.setTextColor(YumiMd3.primary());
        uploadEmptyBtn.setBackground(YumiMd3.rounded(YumiMd3.surfaceContainerHigh(), YumiMd3.RADIUS_M_DP));
        uploadEmptyBtn.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(10), AndroidUtilities.dp(20), AndroidUtilities.dp(10));
        uploadEmptyBtn.setOnClickListener(v -> {
            MiogramHaptic.tap(v);
            openFilePicker();
        });
        emptyView.addView(uploadEmptyBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        listContainer.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));
        mainContentLayout.addView(listContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1.0f));

        rootLayout.addView(mainContentLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    }

    private void refreshCategoryChips() {
        if (categoryChipsContainer == null) return;
        categoryChipsContainer.removeAllViews();
        Context context = getContext();
        if (context == null) return;

        addCategoryChip(context, CATEGORY_ALL, MiogramLocale.get("Всі", "Все", "All"), currentCategoryFilter == CATEGORY_ALL);
        addCategoryChip(context, CATEGORY_MEDIA, MiogramLocale.get("Фото й відео", "Фото и видео", "Media"), currentCategoryFilter == CATEGORY_MEDIA);
        addCategoryChip(context, CATEGORY_DOCS, MiogramLocale.get("Документи", "Документы", "Documents"), currentCategoryFilter == CATEGORY_DOCS);
        addCategoryChip(context, CATEGORY_AUDIO, MiogramLocale.get("Музика", "Музыка", "Music"), currentCategoryFilter == CATEGORY_AUDIO);
        addCategoryChip(context, CATEGORY_ARCHIVES, MiogramLocale.get("Архіви", "Архивы", "Archives"), currentCategoryFilter == CATEGORY_ARCHIVES);
    }

    private void addCategoryChip(Context context, int category, String title, boolean active) {
        TextView chip = new TextView(context);
        chip.setText(title);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        chip.setTypeface(YumiMd3.typeface(active ? YumiMd3.BOLD : YumiMd3.MEDIUM));
        chip.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(6), AndroidUtilities.dp(14), AndroidUtilities.dp(6));

        if (active) {
            chip.setBackground(YumiMd3.rounded(YumiMd3.primary(), YumiMd3.RADIUS_FULL_DP));
            chip.setTextColor(YumiMd3.onPrimary());
        } else {
            chip.setBackground(YumiMd3.rounded(YumiMd3.surfaceContainer(), YumiMd3.RADIUS_FULL_DP));
            chip.setTextColor(YumiMd3.onSurfaceVariant());
        }

        chip.setOnClickListener(v -> {
            MiogramHaptic.tap(v);
            currentCategoryFilter = category;
            refreshCategoryChips();
            filterAndReloadFiles();
        });

        categoryChipsContainer.addView(chip, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 6, 0));
    }

    // --- Floating Action Button (Upload) ---

    private void buildFab(Context context) {
        fabButton = new FrameLayout(context);
        fabButton.setBackground(YumiMd3.gradient(YumiMd3.actionGradientStart(), YumiMd3.actionGradientEnd(), YumiMd3.RADIUS_M_DP));
        fabButton.setElevation(AndroidUtilities.dp(4));

        ImageView addIcon = new ImageView(context);
        addIcon.setImageResource(R.drawable.baseline_add_24);
        addIcon.setColorFilter(YumiMd3.onActionGradient());
        fabButton.addView(addIcon, LayoutHelper.createFrame(26, 26, Gravity.CENTER));

        fabButton.setOnClickListener(v -> {
            MiogramHaptic.tap(v);
            openFilePicker();
        });

        FrameLayout.LayoutParams lp = LayoutHelper.createFrame(56, 56, Gravity.BOTTOM | Gravity.END, 0, 0, 20, 20);
        rootLayout.addView(fabButton, lp);
    }

    private void promptCreateFolder() {
        if (getParentActivity() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(MiogramLocale.get("Створити папку (Тему форуму)", "Создать папку (Тему форума)", "Create Folder (Forum Topic)"));

        final EditText input = new EditText(getParentActivity());
        input.setHint(MiogramLocale.get("Назва папки (напр. Фотографії)", "Название папки (напр. Фотографии)", "Folder name (e.g. Photos)"));
        input.setSingleLine(true);
        FrameLayout container = new FrameLayout(getParentActivity());
        container.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(8), AndroidUtilities.dp(20), AndroidUtilities.dp(8));
        container.addView(input, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        builder.setView(container);

        builder.setPositiveButton(LocaleController.getString(R.string.Create), (d, w) -> {
            String name = input.getText().toString().trim();
            if (!TextUtils.isEmpty(name)) {
                long vaultChatId = MiogramCloudVaultEngine.getVaultChatId(currentAccount);
                MiogramCloudVaultEngine.createTopic(currentAccount, vaultChatId, name, 0x3390EC);
                Toast.makeText(getParentActivity(), MiogramLocale.get("Папку створено!", "Папка создана!", "Folder created!"), Toast.LENGTH_SHORT).show();
                AndroidUtilities.runOnUIThread(this::loadTopicsFromTelegram, 1000);
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void loadTopicsFromTelegram() {
        long vaultChatId = MiogramCloudVaultEngine.getVaultChatId(currentAccount);
        if (vaultChatId == 0) return;

        if (MiogramCloudVaultEngine.isSavedMessagesVault(currentAccount, vaultChatId)) {
            cachedTopics.clear();
            updateFolderChipText();
            return;
        }

        TopicsController tc = getMessagesController().getTopicsController();
        tc.loadTopics(vaultChatId);
        ArrayList<TLRPC.TL_forumTopic> topics = tc.getTopics(vaultChatId);
        cachedTopics.clear();
        if (topics != null) {
            for (TLRPC.TL_forumTopic t : topics) {
                if (t != null && !MiogramCloudVaultEngine.isSystemHiddenTopic(t.title)) {
                    cachedTopics.add(t);
                }
            }
        }
        updateFolderChipText();
    }

    // --- Files Filtering & Display ---

    private void filterAndReloadFiles() {
        ArrayList<MiogramCloudVaultFile> rawFiles = MiogramCloudVaultEngine.getFilesForTopic(currentSelectedTopicId);
        displayedFiles.clear();

        for (MiogramCloudVaultFile f : rawFiles) {
            if (currentCategoryFilter == CATEGORY_MEDIA && !f.isMedia()) continue;
            if (currentCategoryFilter == CATEGORY_DOCS && !f.isDocument()) continue;
            if (currentCategoryFilter == CATEGORY_AUDIO && !f.isAudio()) continue;
            if (currentCategoryFilter == CATEGORY_ARCHIVES && !f.isArchive()) continue;

            if (!TextUtils.isEmpty(currentSearchQuery)) {
                if (f.name == null || !f.name.toLowerCase().contains(currentSearchQuery.toLowerCase())) {
                    continue;
                }
            }
            displayedFiles.add(f);
        }

        if (filesAdapter != null) {
            filesAdapter.notifyDataSetChanged();
        }

        if (emptyView != null) {
            emptyView.setVisibility(displayedFiles.isEmpty() ? View.VISIBLE : View.GONE);
            if (emptyText != null && emptyHint != null) {
                if (currentCategoryFilter == CATEGORY_MEDIA) {
                    emptyText.setText(MiogramLocale.get("У галереї ще немає медіафайлів", "В галерее еще нет медиафайлов", "No media in gallery yet"));
                    emptyHint.setText(MiogramLocale.get("Збережіть фото чи відео з будь-якого чату!", "Сохраните фото или видео из любого чата!", "Save photos or videos from any chat!"));
                } else {
                    emptyText.setText(MiogramLocale.get("У цій папці поки немає файлів", "В этой папке пока нет файлов", "No files in this folder yet"));
                    emptyHint.setText(MiogramLocale.get("Натисніть (+), щоб завантажити файл будь-якого розміру", "Нажмите (+), чтобы загрузить файл любого размера", "Tap (+) to upload files of any size"));
                }
            }
        }

        updateSubtitle();
        updateFolderChipText();
        updateSecBadgeChip();
    }

    private void syncFromCloud() {
        long vaultChatId = MiogramCloudVaultEngine.getVaultChatId(currentAccount);
        if (vaultChatId == 0) return;

        MiogramCloudVaultEngine.syncVaultFiles(currentAccount, vaultChatId, new MiogramCloudVaultEngine.SyncCallback() {
            @Override
            public void onSyncProgress(int count) {
            }

            @Override
            public void onSyncComplete(ArrayList<MiogramCloudVaultFile> files) {
                filterAndReloadFiles();
            }

            @Override
            public void onSyncError(String message) {
            }
        });
    }

    // --- File Upload Flow ---

    private void openFilePicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent, REQUEST_PICK_FILE);
        } catch (Exception e) {
            FileLog.e(e);
            Toast.makeText(getParentActivity(), MiogramLocale.get("Не вдалося відкрити вибір файлів", "Не удалось открыть выбор файлов", "Failed to open file picker"), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_PICK_FILE && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                processAndUploadFile(uri);
            }
        }
    }

    private void processAndUploadFile(Uri uri) {
        Context context = getParentActivity() != null ? getParentActivity() : getContext();
        if (context == null) return;

        String fileName = "file_" + System.currentTimeMillis();
        long fileSize = 0;
        String mimeType = context.getContentResolver().getType(uri);

        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (nameIndex >= 0) fileName = cursor.getString(nameIndex);
                if (sizeIndex >= 0) fileSize = cursor.getLong(sizeIndex);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }

        final String finalFileName = fileName;
        final long finalFileSize = fileSize;
        final String finalMimeType = mimeType != null ? mimeType : "application/octet-stream";
        final String fileId = UUID.randomUUID().toString();

        MiogramCloudVaultFile dup = findDuplicateFile(finalFileName, finalFileSize);
        if (dup != null) {
            showDuplicateUploadDialog(context, uri, finalFileName, finalFileSize, finalMimeType, fileId);
            return;
        }
        uploadFileInternal(context, uri, finalFileName, finalFileSize, finalMimeType, fileId);
    }

    private void uploadFileInternal(Context context, Uri uri, String finalFileName, long finalFileSize, String finalMimeType, String fileId) {
        final AlertDialog progressDialog = new AlertDialog(context, 3);
        progressDialog.setMessage(MiogramLocale.get("Підготовка файлу...", "Подготовка файла...", "Preparing file..."));
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setCancelable(false);
        progressDialog.show();

        Utilities.globalQueue.postRunnable(() -> {
            try {
                File tempDir = new File(MiogramCloudVaultEngine.getVaultTempDir(context), fileId.substring(0, Math.min(8, fileId.length())));
                if (!tempDir.exists()) tempDir.mkdirs();
                File targetFile = new File(tempDir, finalFileName);

                if (uri != null) {
                    try (InputStream in = context.getContentResolver().openInputStream(uri);
                         FileOutputStream out = new FileOutputStream(targetFile)) {
                        byte[] buf = new byte[128 * 1024];
                        int r;
                        while ((r = in.read(buf)) > 0) {
                            out.write(buf, 0, r);
                        }
                        out.flush();
                    }
                }

                long actualSize = targetFile.length();
                if (actualSize == 0 && finalFileSize > 0) {
                    actualSize = finalFileSize;
                }

                boolean encrypt = MiogramCloudVaultEngine.isEncryptionEnabled(currentAccount);
                boolean needsChunking = encrypt || actualSize > 2000L * 1024L * 1024L;

                ArrayList<File> uploadChunks;
                if (needsChunking) {
                    AndroidUtilities.runOnUIThread(() -> progressDialog.setMessage(
                            encrypt ? MiogramLocale.get("Шифрування AES-256-GCM...", "Шифрование AES-256-GCM...", "Encrypting AES-256-GCM...")
                                    : MiogramLocale.get("Нарізка великого файлу...", "Нарезка большого файла...", "Chunking large file...")
                    ));
                    try (InputStream inStream = new FileInputStream(targetFile)) {
                        uploadChunks = MiogramCloudVaultEngine.splitStream(
                                context, inStream, finalFileName, actualSize, fileId, encrypt,
                                (progress, status) -> AndroidUtilities.runOnUIThread(() -> progressDialog.setMessage(status))
                        );
                    }
                } else {
                    uploadChunks = new ArrayList<>();
                    uploadChunks.add(targetFile);
                }

                MiogramCloudVaultFile vaultFile = new MiogramCloudVaultFile();
                vaultFile.fileId = fileId;
                vaultFile.name = finalFileName;
                vaultFile.totalSize = actualSize;
                vaultFile.mimeType = finalMimeType;
                vaultFile.chunksCount = uploadChunks.size();
                vaultFile.chunkSize = needsChunking ? MiogramCloudVaultEngine.DEFAULT_CHUNK_SIZE : actualSize;
                vaultFile.encrypted = encrypt;
                vaultFile.topicId = currentSelectedTopicId;
                vaultFile.topicName = currentSelectedTopicName;
                vaultFile.date = System.currentTimeMillis() / 1000L;
                vaultFile.localPath = targetFile.getAbsolutePath();

                AndroidUtilities.runOnUIThread(() -> {
                    progressDialog.setMessage(MiogramLocale.get("Відправка в Telegram Cloud...", "Отправка в Telegram Cloud...", "Uploading to Telegram Cloud..."));

                    long vaultChatId = MiogramCloudVaultEngine.getVaultChatId(currentAccount);
                    if (vaultChatId == 0) {
                        progressDialog.dismiss();
                        showVaultRequiredDialog();
                        return;
                    }
                    long targetDialogId = MiogramCloudVaultEngine.getVaultDialogId(currentAccount, vaultChatId);

                    MessageObject replyToTopMsg = null;
                    if (vaultFile.topicId > 0 && !MiogramCloudVaultEngine.isSavedMessagesVault(currentAccount, vaultChatId)) {
                        TLRPC.TL_forumTopic topic = getMessagesController().getTopicsController().findTopic(-targetDialogId, vaultFile.topicId);
                        if (topic != null && topic.topicStartMessage != null) {
                            replyToTopMsg = new MessageObject(currentAccount, topic.topicStartMessage, false, false);
                            replyToTopMsg.isTopicMainMessage = true;
                        } else {
                            TLRPC.TL_message dummyMsg = new TLRPC.TL_message();
                            dummyMsg.id = (int) vaultFile.topicId;
                            dummyMsg.dialog_id = targetDialogId;
                            replyToTopMsg = new MessageObject(currentAccount, dummyMsg, false, false);
                            replyToTopMsg.isTopicMainMessage = true;
                        }
                    }

                    for (int i = 0; i < uploadChunks.size(); i++) {
                        File chunk = uploadChunks.get(i);
                        String caption;
                        if (i == 0) {
                            caption = MiogramCloudVaultEngine.createManifestCaption(vaultFile);
                        } else {
                            caption = MiogramCloudVaultEngine.createPartCaption(vaultFile.fileId, i, uploadChunks.size());
                        }

                        SendMessagesHelper.prepareSendingDocument(
                                getAccountInstance(),
                                chunk.getAbsolutePath(),
                                chunk.getAbsolutePath(),
                                null,
                                caption,
                                encrypt ? "application/octet-stream" : finalMimeType,
                                targetDialogId,
                                replyToTopMsg, replyToTopMsg, null, null, null,
                                true, 0, null, null, false
                        );
                    }

                    MiogramCloudVaultEngine.registerFile(vaultFile);
                    MiogramCloudVaultEngine.saveCache(currentAccount);

                    progressDialog.dismiss();
                    Toast.makeText(context, MiogramLocale.get("Файл завантажено!", "Файл загружен!", "File uploaded!"), Toast.LENGTH_SHORT).show();
                    filterAndReloadFiles();
                    AndroidUtilities.runOnUIThread(() -> syncFromCloud(), 2500);
                });

            } catch (Exception e) {
                FileLog.e(e);
                AndroidUtilities.runOnUIThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(context, MiogramLocale.get("Помилка", "Ошибка", "Error") + (e.getMessage() != null ? ": " + e.getMessage() : ""), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // --- File Download & Decryption Flow (Блок 13: UI-шар прев'ю; протокол чанків/шифрування не чіпаємо) ---

    private void setPreviewStatus(MiogramCloudVaultFile f, String status) {
        try {
            if (f == null || f.fileId == null) return;
            if (status == null) vaultPreviewStatus.remove(f.fileId);
            else vaultPreviewStatus.put(f.fileId, status);
        } catch (Throwable ignore) {
        }
    }

    private String previewStatusFor(MiogramCloudVaultFile f) {
        try {
            if (f != null && f.fileId != null) return vaultPreviewStatus.get(f.fileId);
        } catch (Throwable ignore) {
        }
        return null;
    }

    /** Копія зібраного файла в cache/vault_preview/ для безпечного прев'ю через FileProvider. */
    private File copyToPreviewCache(Context context, File assembled, MiogramCloudVaultFile file) {
        try {
            File dir = new File(context.getCacheDir(), "vault_preview");
            if (!dir.exists()) dir.mkdirs();
            String safe = file != null && file.name != null && !file.name.isEmpty()
                    ? file.name.replaceAll("[\\\\/:*?\"<>|]", "_") : assembled.getName();
            File dst = new File(dir, safe);
            int counter = 1;
            while (dst.exists() && dst.length() != assembled.length()) {
                int dot = safe.lastIndexOf('.');
                if (dot > 0) dst = new File(dir, safe.substring(0, dot) + " (" + counter + ")" + safe.substring(dot));
                else dst = new File(dir, safe + " (" + counter + ")");
                counter++;
            }
            if (!dst.exists() || dst.length() != assembled.length()) {
                java.io.FileInputStream in = new java.io.FileInputStream(assembled);
                java.io.FileOutputStream out = new java.io.FileOutputStream(dst);
                byte[] buf = new byte[256 * 1024];
                int r;
                while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
                out.flush();
                out.close();
                in.close();
            }
            try {
                dst.setReadable(true, false);
            } catch (Throwable ignore) {
            }
            return dst;
        } catch (Throwable e) {
            FileLog.e(e);
            return assembled;
        }
    }

    /** Діалог повтору при помилці чанка: кнопка "🔄 Повторити" + тост з причиною. */
    private void showPreviewRetryDialog(MiogramCloudVaultFile file, Runnable onReady, String reason) {
        try {
            Context context = getParentActivity() != null ? getParentActivity() : getContext();
            if (context == null) return;
            String msg = MiogramLocale.get("Не вдалося зібрати превʼю", "Не удалось собрать превью", "Could not build preview")
                    + (reason != null && !reason.isEmpty() ? ": " + reason : "");
            try {
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
            } catch (Throwable ignore) {
            }
            if (getParentActivity() == null) return;
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(file != null && file.name != null ? file.name : MiogramLocale.get("Помилка чанка", "Ошибка чанка", "Chunk error"));
            builder.setMessage(msg);
            builder.setPositiveButton(MiogramLocale.get("Повторити", "Повторить", "Retry"), (d, w) -> {
                try {
                    if (file != null) {
                        file.isDownloading = false;
                        setPreviewStatus(file, null);
                    }
                } catch (Throwable ignore) {
                }
                ensureFileDownloaded(file, onReady);
            });
            builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
            showDialog(builder.create());
        } catch (Throwable ignore) {
        }
    }

    private void ensureFileDownloaded(MiogramCloudVaultFile file, Runnable onReady) {
        if (!TextUtils.isEmpty(file.localPath) && new File(file.localPath).exists()) {
            if (onReady != null) onReady.run();
            return;
        }

        Context context = getParentActivity() != null ? getParentActivity() : getContext();
        if (context == null) return;

        if (file.chunkDocuments.isEmpty()) {
            final AlertDialog resolvingDialog = new AlertDialog(context, 3);
            resolvingDialog.setMessage(MiogramLocale.get("Синхронізація чанків з хмари...", "Синхронизация чанков из облака...", "Syncing chunks from cloud..."));
            resolvingDialog.setCanceledOnTouchOutside(false);
            resolvingDialog.show();

            long vaultChatId = MiogramCloudVaultEngine.getVaultChatId(currentAccount);
            MiogramCloudVaultEngine.resolveChunkDocuments(currentAccount, vaultChatId, file, () -> {
                resolvingDialog.dismiss();
                if (file.chunkDocuments.isEmpty()) {
                    Toast.makeText(context, MiogramLocale.get("Не вдалося знайти чанки файлу в хмарі", "Не удалось найти чанки файла в облаке", "Could not locate file chunks in cloud"), Toast.LENGTH_SHORT).show();
                    return;
                }
                ensureFileDownloaded(file, onReady);
            });
            return;
        }

        final AlertDialog progressDialog = new AlertDialog(context, 3);
        progressDialog.setMessage(MiogramLocale.get("Завантаження чанків з Telegram...", "Загрузка чанков из Telegram...", "Downloading chunks from Telegram..."));
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.show();

        file.isDownloading = true;
        setPreviewStatus(file, MiogramLocale.get("Завантаження…", "Загрузка…", "Downloading…"));
        if (filesAdapter != null) filesAdapter.notifyDataSetChanged();

        // Фоновий Executor для завантаження/розшифровки (UI не блокуємо; протокол той самий).
        try {
            vaultPreviewExecutor.execute(() -> downloadAndDecryptInBackground(file, onReady, context, progressDialog));
        } catch (Throwable e) {
            FileLog.e(e);
            Utilities.globalQueue.postRunnable(() -> downloadAndDecryptInBackground(file, onReady, context, progressDialog));
        }
    }

    private void downloadAndDecryptInBackground(MiogramCloudVaultFile file, Runnable onReady, Context context, AlertDialog progressDialog) {
        try {
            ArrayList<File> downloadedChunks = new ArrayList<>();
            int total = Math.max(1, file.chunkDocuments.size());
            for (int i = 0; i < file.chunkDocuments.size(); i++) {
                TLRPC.Document doc = file.chunkDocuments.get(i);
                final int idx = i;
                AndroidUtilities.runOnUIThread(() -> {
                    try {
                        progressDialog.setMessage(MiogramLocale.get("Завантаження з Telegram...", "Загрузка из Telegram...", "Downloading from Telegram...")
                                + (total > 1 ? (" " + (idx + 1) + "/" + total) : ""));
                    } catch (Throwable ignore) {
                    }
                    setPreviewStatus(file, MiogramLocale.get("Завантаження", "Загрузка", "Downloading")
                            + (total > 1 ? (" " + (idx + 1) + "/" + total) : ""));
                    if (filesAdapter != null) filesAdapter.notifyDataSetChanged();
                });
                File attachFile = FileLoader.getInstance(currentAccount).getPathToAttach(doc, true);
                if (attachFile == null || !attachFile.exists()) {
                    FileLoader.getInstance(currentAccount).loadFile(doc, null, FileLoader.PRIORITY_HIGH, 0);
                    int waitedMs = 0;
                    int maxWaitMs = 300000;
                    while ((attachFile == null || !attachFile.exists()) && waitedMs < maxWaitMs) {
                        try {
                            Thread.sleep(250);
                        } catch (InterruptedException e) {
                            break;
                        }
                        waitedMs += 250;
                        attachFile = FileLoader.getInstance(currentAccount).getPathToAttach(doc, true);
                    }
                }
                if (attachFile != null && attachFile.exists()) {
                    downloadedChunks.add(attachFile);
                } else {
                    throw new IllegalStateException(MiogramLocale.get("Файл", "Файл", "File")
                            + (total > 1 ? (" (" + (idx + 1) + "/" + total + ")") : "")
                            + MiogramLocale.get(" не завантажився вчасно", " не загрузился вовремя", " download timed out"));
                }
            }

            int requiredChunks = file.chunksCount > 0 ? file.chunksCount : 1;
            boolean isEncrypted = file.encrypted || (!downloadedChunks.isEmpty() && MiogramCloudVaultEngine.isEncryptedChunk(downloadedChunks.get(0)));

            if (isEncrypted && downloadedChunks.size() < requiredChunks) {
                throw new IllegalStateException("Not all chunks could be downloaded (" + downloadedChunks.size() + "/" + requiredChunks + ")");
            }

            File assembled;
            if (isEncrypted) {
                AndroidUtilities.runOnUIThread(() -> {
                    try {
                        progressDialog.setMessage(MiogramLocale.get("Розшифрування та збирання файлу...", "Дешифрование и сборка файла...", "Decrypting and reassembling file..."));
                    } catch (Throwable ignore) {
                    }
                });

                assembled = MiogramCloudVaultEngine.decryptAndReassembleFile(
                        context, file, downloadedChunks, null,
                        (progress, status) -> AndroidUtilities.runOnUIThread(() -> {
                            try {
                                int pct = Math.max(0, Math.min(100, (int) (progress * 100)));
                                // Рядковий статус файла: "Розшифровка N/M чанків… %"
                                String rowStatus = "Розшифровка " + downloadedChunks.size() + "/" + total
                                        + " чанків… " + pct + "%";
                                setPreviewStatus(file, rowStatus);
                                progressDialog.setMessage((status != null ? status : "") + " • " + pct + "%");
                                if (filesAdapter != null) filesAdapter.notifyDataSetChanged();
                            } catch (Throwable ignore) {
                            }
                        })
                );
            } else if (downloadedChunks.size() == 1) {
                assembled = downloadedChunks.get(0);
            } else {
                assembled = MiogramCloudVaultEngine.reassemblePlainFile(
                        context, file, downloadedChunks, null,
                        (progress, status) -> AndroidUtilities.runOnUIThread(() -> {
                            try {
                                int pct = Math.max(0, Math.min(100, (int) (progress * 100)));
                                progressDialog.setMessage((status != null ? status : "") + " • " + pct + "%");
                                if (filesAdapter != null) filesAdapter.notifyDataSetChanged();
                            } catch (Throwable ignore) {
                            }
                        })
                );
            }

            File preview = copyToPreviewCache(context, assembled, file);

            AndroidUtilities.runOnUIThread(() -> {
                file.isDownloading = false;
                file.localPath = preview.getAbsolutePath();
                setPreviewStatus(file, null);
                progressDialog.dismiss();
                if (filesAdapter != null) filesAdapter.notifyDataSetChanged();
                if (onReady != null) onReady.run();
            });

        } catch (Exception e) {
            FileLog.e(e);
            String reason = e.getMessage() != null ? e.getMessage() : e.toString();
            AndroidUtilities.runOnUIThread(() -> {
                file.isDownloading = false;
                setPreviewStatus(file, null);
                try {
                    progressDialog.dismiss();
                } catch (Throwable ignore) {
                }
                if (filesAdapter != null) filesAdapter.notifyDataSetChanged();
                showPreviewRetryDialog(file, onReady, reason);
            });
        }
    }

    private void downloadOrOpenFile(MiogramCloudVaultFile file) {
        ensureFileDownloaded(file, () -> {
            Context context = getParentActivity() != null ? getParentActivity() : getContext();
            if (context != null && !TextUtils.isEmpty(file.localPath)) {
                try {
                    File src = new File(file.localPath);
                    // Прев'ю завжди з cache/vault_preview/ через FileProvider VIEW.
                    File preview = src;
                    try {
                        File cacheDir = new File(context.getCacheDir(), "vault_preview");
                        boolean inPreview = src.getAbsolutePath().contains("vault_preview");
                        if (!inPreview && src.exists()) {
                            preview = copyToPreviewCache(context, src, file);
                            file.localPath = preview.getAbsolutePath();
                        }
                    } catch (Throwable ignore) {
                    }
                    // Фото/відео/аудіо — прямий VIEW з MIME, невідоме — chooser (усередині гейта).
                    app.amegram.hot.HotVaultGate.openPreview(context, preview);
                } catch (Throwable e) {
                    FileLog.e(e);
                    Toast.makeText(context, MiogramLocale.get("Не вдалося відкрити превʼю", "Не удалось открыть превью", "Could not open preview")
                            + (e.getMessage() != null ? ": " + e.getMessage() : ""), Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void shareToTelegramChat(MiogramCloudVaultFile file) {
        ensureFileDownloaded(file, () -> {
            Bundle args = new Bundle();
            args.putBoolean("onlySelect", true);
            args.putBoolean("canSelectTopics", true);
            DialogsActivity dialogs = new DialogsActivity(args);
            dialogs.setDelegate((fragment1, dids, message, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
                if (dids != null && !dids.isEmpty()) {
                    for (int i = 0; i < dids.size(); i++) {
                        long did = dids.get(i).dialogId;
                        SendMessagesHelper.prepareSendingDocument(
                                getAccountInstance(),
                                file.localPath,
                                file.localPath,
                                null,
                                file.name,
                                file.mimeType != null ? file.mimeType : "application/octet-stream",
                                did,
                                null, null, null, null, null,
                                true, 0, null, null, false
                        );
                    }
                    fragment1.finishFragment();
                    Toast.makeText(getParentActivity(), MiogramLocale.get("Файл надіслано в чат!", "Файл отправлен в чат!", "File sent to chat!"), Toast.LENGTH_SHORT).show();
                }
                return true;
            });
            presentFragment(dialogs);
        });
    }

    private void shareFileExternal(MiogramCloudVaultFile file) {
        if (getParentActivity() == null) return;
        ensureFileDownloaded(file, () -> {
            try {
                File f = new File(file.localPath);
                Uri uri = FileProvider.getUriForFile(
                        ApplicationLoader.applicationContext,
                        ApplicationLoader.getApplicationId() + ".provider",
                        f
                );
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType(file.mimeType != null ? file.mimeType : "application/octet-stream");
                intent.putExtra(Intent.EXTRA_STREAM, uri);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                getParentActivity().startActivity(Intent.createChooser(intent, MiogramLocale.get("Поділитися файлом", "Поделиться файлом", "Share File")));
            } catch (Exception e) {
                FileLog.e(e);
                Toast.makeText(getParentActivity(), MiogramLocale.get("Помилка", "Ошибка", "Error") + (e.getMessage() != null ? ": " + e.getMessage() : ""), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showMoveFileToTopicDialog(MiogramCloudVaultFile file) {
        if (getParentActivity() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(MiogramLocale.get("Перемістити в папку", "Переместить в папку", "Move to folder"));

        ArrayList<String> topicNames = new ArrayList<>();
        ArrayList<Long> topicIds = new ArrayList<>();

        topicNames.add(MiogramLocale.get("Головна папка (Усі файли)", "Корень (Все файлы)", "Root (All files)"));
        topicIds.add(0L);

        for (TLRPC.TL_forumTopic topic : cachedTopics) {
            topicNames.add(topic.title);
            topicIds.add((long) topic.id);
        }

        CharSequence[] items = topicNames.toArray(new CharSequence[0]);
        builder.setItems(items, (d, which) -> {
            long newTopicId = topicIds.get(which);
            String newTopicName = which == 0 ? "" : topicNames.get(which);
            file.topicId = newTopicId;
            file.topicName = newTopicName;
            MiogramCloudVaultEngine.saveCache(currentAccount);
            filterAndReloadFiles();
            Toast.makeText(getParentActivity(), MiogramLocale.get("Файл переміщено!", "Файл перемещен!", "File moved!"), Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    // --- Vault Management & Dialogs ---

    /** Finds an already-vaulted file with the same name and size (re-upload guard). */
    private MiogramCloudVaultFile findDuplicateFile(String name, long size) {
        if (name == null) return null;
        try {
            for (MiogramCloudVaultFile f : MiogramCloudVaultEngine.getFilesForTopic(currentSelectedTopicId)) {
                if (f != null && name.equals(f.name) && f.totalSize == size) return f;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private void showDuplicateUploadDialog(Context context, Uri uri, String name, long size, String mimeType, String fileId) {
        if (getParentActivity() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(MiogramLocale.get("Такий файл уже є", "Такой файл уже есть", "File already exists"));
        builder.setMessage(MiogramLocale.get("У сховищі вже лежить '", "В хранилище уже лежит '", "The vault already holds '")
                + name + MiogramLocale.get("' такого ж розміру. Завантажити ще раз?",
                "' такого же размера. Загрузить еще раз?",
                "' of the same size. Upload again?"));
        builder.setPositiveButton(MiogramLocale.get("Завантажити", "Загрузить", "Upload"), (d, w) ->
                uploadFileInternal(context, uri, name, size, mimeType, fileId));
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    /** Shown when the user tries to upload before creating/linking a vault chat. */
    private void showVaultRequiredDialog() {
        if (getParentActivity() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(MiogramLocale.get("Підключення Miogram Cloud Vault", "Подключение Miogram Cloud Vault", "Connect Miogram Cloud Vault"));
        builder.setMessage(MiogramLocale.get(
                "Для завантаження файлів необхідно створити персональне сховище — форум-супергрупу з топіками (Загальне, Медіа, Документи, Архіви).",
                "Для загрузки файлов необходимо создать персональное хранилище — форум-супергруппу с топиками (Общее, Медиа, Документы, Архивы).",
                "To upload files, a personal vault is required — a forum supergroup with topics (General, Media, Documents, Archives)."
        ));
        builder.setPositiveButton(MiogramLocale.get("Створити форум-сховище", "Создать форум-хранилище", "Create Forum Vault"), (d, w) -> createVaultAutomatically());
        builder.setNeutralButton(MiogramLocale.get("Прив'язати ID", "Привязать ID", "Link ID"), (d, w) -> showLinkExistingDialog());
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void migrateVaultToForumChat() {
        if (getParentActivity() == null) return;
        final AlertDialog progressDialog = new AlertDialog(getParentActivity(), 3);
        progressDialog.setMessage(MiogramLocale.get(
                "Міграція сховища у форум-чат з топіками...",
                "Миграция хранилища в форум-чат с топиками...",
                "Migrating vault to forum chat with topics..."
        ));
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setCancelable(false);
        progressDialog.show();

        MiogramCloudVaultEngine.createVaultSupergroup(this, currentAccount, new MiogramCloudVaultEngine.VaultCreatedCallback() {
            @Override
            public void onCreated(long chatId) {
                progressDialog.dismiss();
                Toast.makeText(getParentActivity(), MiogramLocale.get(
                        "Сховище успішно мігровано у форум-чат з топіками!",
                        "Хранилище успешно мигрировано в форум-чат с топиками!",
                        "Vault successfully migrated to forum chat with topics!"
                ), Toast.LENGTH_LONG).show();
                updateVaultVisibility();
                loadTopicsFromTelegram();
                filterAndReloadFiles();
            }

            @Override
            public void onError(String message) {
                progressDialog.dismiss();
                Toast.makeText(getParentActivity(), MiogramLocale.get(
                        "Помилка міграції: " + message,
                        "Ошибка миграции: " + message,
                        "Migration error: " + message
                ), Toast.LENGTH_SHORT).show();
                updateVaultVisibility();
                loadTopicsFromTelegram();
                filterAndReloadFiles();
            }
        });
    }

    private void createVaultAutomatically() {
        if (getParentActivity() == null) return;
        final AlertDialog progressDialog = new AlertDialog(getParentActivity(), 3);
        progressDialog.setMessage(MiogramLocale.get("Створення форум-супергрупи...", "Создание форум-супергруппы...", "Creating forum supergroup..."));
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setCancelable(false);
        progressDialog.show();

        MiogramCloudVaultEngine.createVaultSupergroup(this, currentAccount, new MiogramCloudVaultEngine.VaultCreatedCallback() {
            @Override
            public void onCreated(long chatId) {
                progressDialog.dismiss();
                Toast.makeText(getParentActivity(), MiogramLocale.get("Сховище успішно створено!", "Хранилище успешно создано!", "Vault created successfully!"), Toast.LENGTH_SHORT).show();
                updateVaultVisibility();
                loadTopicsFromTelegram();
                filterAndReloadFiles();
            }

            @Override
            public void onError(String message) {
                progressDialog.dismiss();
                Toast.makeText(getParentActivity(), MiogramLocale.get(
                        "Помилка створення форум-чату: " + message,
                        "Ошибка создания форум-чата: " + message,
                        "Failed to create forum chat: " + message
                ), Toast.LENGTH_LONG).show();
                updateVaultVisibility();
                loadTopicsFromTelegram();
                filterAndReloadFiles();
            }
        });
    }

    private void showLinkExistingDialog() {
        if (getParentActivity() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(MiogramLocale.get("Прив'язати супергрупу", "Привязать супергруппу", "Link Supergroup"));
        builder.setMessage(MiogramLocale.get("Введіть ID супергрупи (без знаку мінус):", "Введите ID супергруппы (без знака минус):", "Enter Supergroup ID (without minus sign):"));

        final EditText input = new EditText(getParentActivity());
        input.setSingleLine(true);
        FrameLayout container = new FrameLayout(getParentActivity());
        container.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(8), AndroidUtilities.dp(20), AndroidUtilities.dp(8));
        container.addView(input, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        builder.setView(container);

        builder.setPositiveButton(LocaleController.getString(R.string.OK), (d, w) -> {
            String str = input.getText().toString().trim().replace("-", "");
            try {
                long id = Long.parseLong(str);
                MiogramCloudVaultEngine.setVaultChatId(currentAccount, id);
                updateVaultVisibility();
                loadTopicsFromTelegram();
                syncFromCloud();
            } catch (Exception e) {
                Toast.makeText(getParentActivity(), MiogramLocale.get("Невірний ID", "Неверный ID", "Invalid ID"), Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void openVaultChat() {
        long vaultChatId = MiogramCloudVaultEngine.getVaultChatId(currentAccount);
        if (vaultChatId == 0) return;
        Bundle args = new Bundle();
        args.putLong("chat_id", vaultChatId);
        presentFragment(new ChatActivity(args));
    }

    private void showMasterKeyDialog() {
        if (getParentActivity() == null) return;
        String hex = MiogramCloudVaultEngine.getMasterKeyHex();

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(MiogramLocale.get("Майстер-ключ AES-256", "Мастер-ключ AES-256", "AES-256 Master Key"));
        builder.setMessage(MiogramLocale.get(
                "Zero-Knowledge Архітектура:\n"
                        + "Це єдиний елемент даних, що зберігається локально на вашому пристрої. Жодні файли чи списки не записуються на диск.\n\n"
                        + "Збережіть цей 256-бітний ключ, щоб мати доступ до файлів з інших пристроїв:\n\n" + hex,
                "Zero-Knowledge Архитектура:\n"
                        + "Это единственный элемент данных, хранящийся локально на вашем устройстве. Никакие файлы и списки не сохраняются на диск.\n\n"
                        + "Сохраните этот 256-битный ключ для доступа к файлам с других устройств:\n\n" + hex,
                "Zero-Knowledge Architecture:\n"
                        + "This key is the only piece of data stored locally on your device. No files or lists are persisted to disk.\n\n"
                        + "Back up this 256-bit key to access your files from other devices:\n\n" + hex
        ));

        builder.setPositiveButton(MiogramLocale.get("Скопіювати ключ", "Скопировать ключ", "Copy Key"), (d, w) -> {
            ClipboardManager cm = (ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("Vault Key", hex));
                Toast.makeText(getParentActivity(), MiogramLocale.get("Ключ скопійовано!", "Ключ скопирован!", "Key copied!"), Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNeutralButton(MiogramLocale.get("Ввести інший", "Ввести другой", "Enter Custom"), (d, w) -> showEnterCustomKeyDialog());
        builder.setNegativeButton(LocaleController.getString(R.string.Close), null);
        showDialog(builder.create());
    }

    private void showEnterCustomKeyDialog() {
        if (getParentActivity() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(MiogramLocale.get("Введіть ключ шифрування", "Введите ключ шифрования", "Enter Encryption Key"));
        builder.setMessage(MiogramLocale.get("Введіть 64-символьний Hex-ключ (256 біт):", "Введите 64-символьный Hex-ключ (256 бит):", "Enter a 64-char hex key (256-bit):"));

        final EditText input = new EditText(getParentActivity());
        input.setSingleLine(true);
        FrameLayout container = new FrameLayout(getParentActivity());
        container.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(8), AndroidUtilities.dp(20), AndroidUtilities.dp(8));
        container.addView(input, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        builder.setView(container);

        builder.setPositiveButton(LocaleController.getString(R.string.Save), (d, w) -> {
            String hex = input.getText().toString().trim();
            if (hex.matches("(?i)[0-9a-f]{64}")) {
                MiogramCloudVaultEngine.setMasterKeyHex(hex);
                Toast.makeText(getParentActivity(), MiogramLocale.get("Ключ оновлено!", "Ключ обновлен!", "Key updated!"), Toast.LENGTH_SHORT).show();
                syncFromCloud();
            } else {
                Toast.makeText(getParentActivity(), MiogramLocale.get("Ключ — рівно 64 hex-символи (0-9, a-f)!", "Ключ — ровно 64 hex-символа (0-9, a-f)!", "Key must be exactly 64 hex chars (0-9, a-f)!"), Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void showUnlinkDialog() {
        if (getParentActivity() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(MiogramLocale.get("Відв'язати супергрупу?", "Отвязать супергруппу?", "Unlink Supergroup?"));
        builder.setMessage(MiogramLocale.get(
                "Супергрупа в Telegram залишиться недоторканою, але цей клієнт перейде в стан налаштування.",
                "Супергруппа в Telegram останется нетронутой, но этот клиент перейдет в состояние настройки.",
                "The Telegram supergroup will remain untouched, but this client will return to setup state."
        ));
        builder.setPositiveButton(MiogramLocale.get("Відв'язати", "Отвязать", "Unlink"), (d, w) -> {
            MiogramCloudVaultEngine.setVaultChatId(currentAccount, 0);
            updateVaultVisibility();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void showFileOptions(MiogramCloudVaultFile file) {
        if (getParentActivity() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(file.name);

        CharSequence[] items = new CharSequence[]{
                MiogramLocale.get("Переглянути / Відкрити", "Просмотреть / Открыть", "Preview / Open"),
                MiogramLocale.get("Зберегти в Завантаження", "Сохранить в Загрузки", "Save to Downloads"),
                MiogramLocale.get("Показати в чаті сховища", "Показать в чате хранилища", "Show in vault chat"),
                MiogramLocale.get("Поділитися в чат Telegram", "Поделиться в чат Telegram", "Share to Telegram Chat"),
                MiogramLocale.get("Поділитися через інші додатки", "Поделиться через другие приложения", "Share via Other Apps"),
                MiogramLocale.get("Перемістити в папку (топік)", "Переместить в папку (топик)", "Move to Folder (Topic)"),
                MiogramLocale.get("Копіювати назву", "Копировать название", "Copy Name"),
                MiogramLocale.get("Видалити зі сховища", "Удалить из хранилища", "Delete from Vault")
        };

        builder.setItems(items, (d, which) -> {
            if (which == 0) {
                downloadOrOpenFile(file);
            } else if (which == 1) {
                exportFileToDownloads(file);
            } else if (which == 2) {
                openFileInVaultChat(file);
            } else if (which == 3) {
                shareToTelegramChat(file);
            } else if (which == 4) {
                shareFileExternal(file);
            } else if (which == 5) {
                showMoveFileToTopicDialog(file);
            } else if (which == 6) {
                ClipboardManager cm = (ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("Filename", file.name));
                    Toast.makeText(getParentActivity(), MiogramLocale.get("Назву скопійовано", "Название скопировано", "Name copied"), Toast.LENGTH_SHORT).show();
                }
            } else if (which == 7) {
                long vaultChatId = MiogramCloudVaultEngine.getVaultChatId(currentAccount);
                MiogramCloudVaultEngine.deleteVaultFile(currentAccount, vaultChatId, file, true);
                filterAndReloadFiles();
                Toast.makeText(getParentActivity(), MiogramLocale.get("Файл видалено", "Файл удален", "File deleted"), Toast.LENGTH_SHORT).show();
            }
        });
        showDialog(builder.create());
    }

    private void exportFileToDownloads(MiogramCloudVaultFile file) {
        ensureFileDownloaded(file, () -> {
            Context context = getParentActivity() != null ? getParentActivity() : getContext();
            if (context == null || TextUtils.isEmpty(file.localPath)) return;
            try {
                File src = new File(file.localPath);
                if (!src.exists()) return;
                File downloadsDir = new File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "Miogram Vault");
                if (!downloadsDir.exists()) downloadsDir.mkdirs();
                String safeFileName = !TextUtils.isEmpty(file.name) ? file.name : src.getName();
                File destFile = new File(downloadsDir, safeFileName);
                int counter = 1;
                while (destFile.exists()) {
                    int dot = safeFileName.lastIndexOf('.');
                    if (dot > 0) {
                        destFile = new File(downloadsDir, safeFileName.substring(0, dot) + " (" + counter + ")" + safeFileName.substring(dot));
                    } else {
                        destFile = new File(downloadsDir, safeFileName + " (" + counter + ")");
                    }
                    counter++;
                }
                try (java.io.FileInputStream in = new java.io.FileInputStream(src);
                     java.io.FileOutputStream out = new java.io.FileOutputStream(destFile)) {
                    byte[] buf = new byte[256 * 1024];
                    int r;
                    while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
                    out.flush();
                }
                android.media.MediaScannerConnection.scanFile(context, new String[]{destFile.getAbsolutePath()}, null, null);
                Toast.makeText(context, MiogramLocale.get("Файл збережено в Завантаження!", "Файл сохранён в Загрузки!", "File saved to Downloads!"), Toast.LENGTH_SHORT).show();
            } catch (Throwable e) {
                FileLog.e(e);
                Toast.makeText(context, MiogramLocale.get("Помилка експорту", "Ошибка экспорта", "Export error") + ": " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** Jumps straight to the file's chunk messages inside the vault supergroup. */
    private void openFileInVaultChat(MiogramCloudVaultFile file) {
        long vaultChatId = MiogramCloudVaultEngine.getVaultChatId(currentAccount);
        if (vaultChatId == 0) {
            Toast.makeText(getParentActivity(), MiogramLocale.get("Сховище не підключено", "Хранилище не подключено", "Vault not linked"), Toast.LENGTH_SHORT).show();
            return;
        }
        if (file.chunkMsgIds.isEmpty()) {
            Toast.makeText(getParentActivity(), MiogramLocale.get("Спочатку синхронізуйте — чанки ще не знайдено", "Сначала синхронизируйтесь — чанки еще не найдены", "Sync first — chunks not located yet"), Toast.LENGTH_SHORT).show();
            syncFromCloud();
            return;
        }
        try {
            Bundle args = new Bundle();
            args.putLong("chat_id", vaultChatId);
            if (file.topicId > 0) {
                // Forum chats can't deep-jump into a topic message from outside —
                // open the topic list; the file's folder shows its chunks.
                presentFragment(new TopicsFragment(args));
            } else {
                args.putInt("message_id", file.chunkMsgIds.get(0));
                presentFragment(new ChatActivity(args));
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    // --- RecyclerView Adapter ---

    private class FilesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        @Override
        public int getItemViewType(int position) {
            return currentViewMode == VIEW_TYPE_GRID ? VIEW_TYPE_GRID : VIEW_TYPE_LIST;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == VIEW_TYPE_GRID) {
                return new GridViewHolder(new VaultGridCell(parent.getContext()));
            } else {
                return new ListViewHolder(new VaultFileCell(parent.getContext()));
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            MiogramCloudVaultFile file = displayedFiles.get(position);
            if (holder instanceof GridViewHolder) {
                ((GridViewHolder) holder).cell.bind(file);
            } else if (holder instanceof ListViewHolder) {
                ((ListViewHolder) holder).cell.bind(file);
            }
        }

        @Override
        public int getItemCount() {
            return displayedFiles.size();
        }
    }

    private static class ListViewHolder extends RecyclerView.ViewHolder {
        VaultFileCell cell;
        public ListViewHolder(VaultFileCell cell) {
            super(cell);
            this.cell = cell;
        }
    }

    private static class GridViewHolder extends RecyclerView.ViewHolder {
        VaultGridCell cell;
        public GridViewHolder(VaultGridCell cell) {
            super(cell);
            this.cell = cell;
        }
    }

    private static int getFileCategoryColor(MiogramCloudVaultFile file) {
        if (file == null) return YumiMd3.primary();
        if (file.isMedia()) return YumiMd3.violet();
        if (file.isDocument()) return YumiMd3.blue();
        if (file.isAudio()) return YumiMd3.orange();
        if (file.isArchive()) return YumiMd3.amber();
        return YumiMd3.cyan();
    }

    // --- Modern Vault Gallery Grid Cell (1:1 Square) ---

    private class VaultGridCell extends FrameLayout {

        private final BackupImageView imageView;
        private final FrameLayout iconBox;
        private final ImageView iconView;
        private final TextView extBadge;
        private final FrameLayout textContainer;
        private final TextView nameView;
        private final TextView sizeView;
        private final LinearLayout videoBadge;
        private final ImageView statusIcon;
        private final ProgressBar progressBar;
        private MiogramCloudVaultFile currentFile;

        public VaultGridCell(Context context) {
            super(context);

            setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), AndroidUtilities.dp(14));
                }
            });
            setClipToOutline(true);
            setBackground(YumiMd3.rounded(YumiMd3.surfaceContainer(), 14));

            // 1. Thumbnail Image (Covering whole card)
            imageView = new BackupImageView(context);
            imageView.setAspectFit(false);
            addView(imageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            // 2. Centered Icon Box (For non-images or files without thumbs)
            iconBox = new FrameLayout(context);
            iconView = new ImageView(context);
            iconBox.addView(iconView, LayoutHelper.createFrame(26, 26, Gravity.CENTER));
            addView(iconBox, LayoutHelper.createFrame(48, 48, Gravity.CENTER, 0, 0, 0, 16));

            // 3. File extension badge (top-left for non-media)
            extBadge = new TextView(context);
            extBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            extBadge.setTypeface(YumiMd3.typeface(YumiMd3.BOLD));
            extBadge.setTextColor(YumiMd3.onSurfaceVariant());
            extBadge.setGravity(Gravity.CENTER);
            extBadge.setBackground(YumiMd3.rounded(YumiMd3.surfaceContainerHighest(), 6));
            extBadge.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(2), AndroidUtilities.dp(6), AndroidUtilities.dp(2));
            addView(extBadge, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.START, 8, 8, 0, 0));

            // 4. Video badge (top-left for videos)
            videoBadge = new LinearLayout(context);
            videoBadge.setOrientation(LinearLayout.HORIZONTAL);
            videoBadge.setGravity(Gravity.CENTER_VERTICAL);
            videoBadge.setBackground(YumiMd3.rounded(0x88000000, 6));
            videoBadge.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(2), AndroidUtilities.dp(6), AndroidUtilities.dp(2));

            ImageView playIco = new ImageView(context);
            playIco.setImageResource(R.drawable.msg_video);
            playIco.setColorFilter(Color.WHITE);
            videoBadge.addView(playIco, LayoutHelper.createLinear(12, 12, Gravity.CENTER_VERTICAL, 0, 0, 4, 0));

            TextView vidLabel = new TextView(context);
            vidLabel.setText("VIDEO");
            vidLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f);
            vidLabel.setTextColor(Color.WHITE);
            vidLabel.setTypeface(YumiMd3.typeface(YumiMd3.BOLD));
            videoBadge.addView(vidLabel, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));
            addView(videoBadge, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.START, 8, 8, 0, 0));

            // 5. Text container at bottom for non-media files
            textContainer = new FrameLayout(context);
            LinearLayout textLayout = new LinearLayout(context);
            textLayout.setOrientation(LinearLayout.VERTICAL);
            textLayout.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), AndroidUtilities.dp(8));

            nameView = new TextView(context);
            nameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            nameView.setTypeface(YumiMd3.typeface(YumiMd3.MEDIUM));
            nameView.setTextColor(YumiMd3.onSurface());
            nameView.setSingleLine(true);
            nameView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            textLayout.addView(nameView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 1));

            sizeView = new TextView(context);
            sizeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
            sizeView.setTextColor(YumiMd3.onSurfaceVariant());
            sizeView.setSingleLine(true);
            textLayout.addView(sizeView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            textContainer.addView(textLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));
            addView(textContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            // 6. Top-right status icon
            statusIcon = new ImageView(context);
            statusIcon.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4));
            addView(statusIcon, LayoutHelper.createFrame(24, 24, Gravity.TOP | Gravity.END, 0, 8, 8, 0));

            progressBar = new ProgressBar(context);
            progressBar.setVisibility(View.GONE);
            addView(progressBar, LayoutHelper.createFrame(22, 22, Gravity.TOP | Gravity.END, 0, 8, 8, 0));

            setOnClickListener(v -> {
                if (currentFile != null) {
                    MiogramHaptic.tap(v);
                    downloadOrOpenFile(currentFile);
                }
            });

            setOnLongClickListener(v -> {
                if (currentFile != null) {
                    MiogramHaptic.tap(v);
                    showFileOptions(currentFile);
                    return true;
                }
                return false;
            });
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, widthMeasureSpec); // Square 1:1 Aspect Ratio
        }

        public void bind(MiogramCloudVaultFile file) {
            this.currentFile = file;
            nameView.setText(file.name);
            sizeView.setText(file.getFormattedSize());
            try {
                String st = previewStatusFor(file);
                if (file.isDownloading && st != null && !st.isEmpty()) {
                    sizeView.setText(file.getFormattedSize() + " • " + st);
                }
            } catch (Throwable ignore) {
            }

            boolean hasLocal = !TextUtils.isEmpty(file.localPath) && new File(file.localPath).exists();
            boolean isMedia = file.isMedia();
            int catColor = getFileCategoryColor(file);

            videoBadge.setVisibility(file.isVideo() ? View.VISIBLE : View.GONE);
            extBadge.setVisibility((!isMedia && !TextUtils.isEmpty(file.getFileExtension())) ? View.VISIBLE : View.GONE);
            if (extBadge.getVisibility() == View.VISIBLE) {
                extBadge.setText(file.getFileExtension().toUpperCase());
            }

            boolean hasThumb = false;
            if (isMedia) {
                if (hasLocal) {
                    imageView.setImage(ImageLocation.getForPath(file.localPath), "200_200", null, null, null);
                    hasThumb = true;
                } else if (!file.chunkDocuments.isEmpty()) {
                    TLRPC.Document doc = file.chunkDocuments.get(0);
                    if (doc.thumbs != null && !doc.thumbs.isEmpty()) {
                        TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(doc.thumbs, 320);
                        imageView.setImage(ImageLocation.getForDocument(thumb, doc), "200_200", null, null, null);
                        hasThumb = true;
                    }
                }
            }

            if (hasThumb) {
                imageView.setVisibility(View.VISIBLE);
                iconBox.setVisibility(View.GONE);
                textContainer.setVisibility(View.GONE); // Pure clean photo preview!
                statusIcon.setBackground(YumiMd3.rounded(0x66000000, 12));
            } else {
                imageView.setVisibility(View.GONE);
                iconBox.setVisibility(View.VISIBLE);
                iconBox.setBackground(YumiMd3.rounded(YumiMd3.iconTone(catColor, 0.16f), 14));
                iconView.setImageResource(file.getIconRes());
                iconView.setColorFilter(catColor);
                textContainer.setVisibility(View.VISIBLE);
                statusIcon.setBackground(YumiMd3.rounded(YumiMd3.surfaceContainerHighest(), 12));
            }

            if (file.isDownloading) {
                progressBar.setVisibility(View.VISIBLE);
                statusIcon.setVisibility(View.GONE);
            } else {
                progressBar.setVisibility(View.GONE);
                statusIcon.setVisibility(View.VISIBLE);
                if (hasLocal) {
                    statusIcon.setImageResource(R.drawable.baseline_check_24);
                    statusIcon.setColorFilter(hasThumb ? Color.WHITE : YumiMd3.green());
                } else {
                    statusIcon.setImageResource(R.drawable.baseline_cloud_download_24);
                    statusIcon.setColorFilter(hasThumb ? Color.WHITE : YumiMd3.primary());
                }
            }
        }
    }

    // --- Vault File Item View ---

    private class VaultFileCell extends FrameLayout {

        private final FrameLayout iconBox;
        private final ImageView iconView;
        private final BackupImageView thumbView;
        private final TextView nameView;
        private final TextView infoView;
        private final ImageView actionButton;
        private final ProgressBar progressBar;
        private final View divider;
        private MiogramCloudVaultFile currentFile;

        public VaultFileCell(Context context) {
            super(context);
            setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(68)));
            setBackground(YumiMd3.transparentRipple(0, YumiMd3.defaultRippleColor()));

            // Left icon / thumb container
            iconBox = new FrameLayout(context);
            iconBox.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), AndroidUtilities.dp(12));
                }
            });
            iconBox.setClipToOutline(true);

            iconView = new ImageView(context);
            iconBox.addView(iconView, LayoutHelper.createFrame(22, 22, Gravity.CENTER));

            thumbView = new BackupImageView(context);
            thumbView.setAspectFit(false);
            iconBox.addView(thumbView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            addView(iconBox, LayoutHelper.createFrame(44, 44, Gravity.CENTER_VERTICAL | Gravity.START, 16, 0, 0, 0));

            // Texts Layout
            LinearLayout textLayout = new LinearLayout(context);
            textLayout.setOrientation(LinearLayout.VERTICAL);

            nameView = new TextView(context);
            nameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            nameView.setTypeface(YumiMd3.typeface(YumiMd3.MEDIUM));
            nameView.setTextColor(YumiMd3.onSurface());
            nameView.setSingleLine(true);
            nameView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            textLayout.addView(nameView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 2));

            infoView = new TextView(context);
            infoView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
            infoView.setTextColor(YumiMd3.onSurfaceVariant());
            infoView.setSingleLine(true);
            infoView.setEllipsize(TextUtils.TruncateAt.END);
            textLayout.addView(infoView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            addView(textLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 72, 0, 52, 0));

            // Right action / download button (48dp touch target)
            actionButton = new ImageView(context);
            actionButton.setScaleType(ImageView.ScaleType.CENTER);
            actionButton.setBackground(YumiMd3.transparentRipple(24, YumiMd3.defaultRippleColor()));
            addView(actionButton, LayoutHelper.createFrame(48, 48, Gravity.CENTER_VERTICAL | Gravity.END, 0, 0, 4, 0));

            progressBar = new ProgressBar(context);
            progressBar.setVisibility(View.GONE);
            addView(progressBar, LayoutHelper.createFrame(26, 26, Gravity.CENTER_VERTICAL | Gravity.END, 0, 0, 15, 0));

            // Inset Divider
            divider = new View(context);
            divider.setBackgroundColor(YumiMd3.hairline());
            addView(divider, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1, Gravity.BOTTOM | Gravity.START, 72, 0, 0, 0));

            setOnClickListener(v -> {
                if (currentFile != null) {
                    MiogramHaptic.tap(v);
                    downloadOrOpenFile(currentFile);
                }
            });

            setOnLongClickListener(v -> {
                if (currentFile != null) {
                    MiogramHaptic.tap(v);
                    showFileOptions(currentFile);
                    return true;
                }
                return false;
            });

            actionButton.setOnClickListener(v -> {
                if (currentFile != null) {
                    MiogramHaptic.tap(v);
                    showFileOptions(currentFile);
                }
            });
        }

        public void bind(MiogramCloudVaultFile file) {
            this.currentFile = file;
            nameView.setText(file.name);
            setContentDescription(file.name);

            int catColor = getFileCategoryColor(file);
            boolean hasLocal = !TextUtils.isEmpty(file.localPath) && new File(file.localPath).exists();
            boolean isMedia = file.isMedia();

            boolean hasThumb = false;
            if (isMedia) {
                if (hasLocal) {
                    thumbView.setImage(ImageLocation.getForPath(file.localPath), "120_120", null, null, null);
                    hasThumb = true;
                } else if (!file.chunkDocuments.isEmpty()) {
                    TLRPC.Document doc = file.chunkDocuments.get(0);
                    if (doc.thumbs != null && !doc.thumbs.isEmpty()) {
                        TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(doc.thumbs, 120);
                        thumbView.setImage(ImageLocation.getForDocument(thumb, doc), "120_120", null, null, null);
                        hasThumb = true;
                    }
                }
            }

            if (hasThumb) {
                thumbView.setVisibility(View.VISIBLE);
                iconView.setVisibility(View.GONE);
                iconBox.setBackground(null);
            } else {
                thumbView.setVisibility(View.GONE);
                iconView.setVisibility(View.VISIBLE);
                iconBox.setBackground(YumiMd3.rounded(YumiMd3.iconTone(catColor, 0.16f), 12));
                iconView.setImageResource(file.getIconRes());
                iconView.setColorFilter(catColor);
            }

            String chunksInfo = file.chunksCount > 1 ? (" • " + file.chunksCount + " " + MiogramLocale.get("част.", "част.", "parts")) : "";
            String topicInfo = (!TextUtils.isEmpty(file.topicName) && currentSelectedTopicId == 0) ? (" • 📁 " + file.topicName) : "";
            String cloudState;
            if (hasLocal) {
                cloudState = " • " + MiogramLocale.get("на пристрої", "на устройстве", "on device");
            } else if (!file.chunkDocuments.isEmpty() || (!file.chunkMsgIds.isEmpty() && file.chunksCount > 0)) {
                cloudState = " • " + MiogramLocale.get("в хмарі", "в облаке", "in cloud");
            } else {
                cloudState = " • " + MiogramLocale.get("очікує синхронізації", "ожидает синхронизации", "pending sync");
            }
            infoView.setText(file.getFormattedSize() + chunksInfo + topicInfo + cloudState + " • " + file.getFormattedDate());
            try {
                String st = previewStatusFor(file);
                if (file.isDownloading && st != null && !st.isEmpty()) {
                    infoView.setText(infoView.getText() + " • " + st);
                }
            } catch (Throwable ignore) {
            }

            if (file.isDownloading) {
                progressBar.setVisibility(View.VISIBLE);
                actionButton.setVisibility(View.GONE);
                actionButton.setContentDescription(null);
            } else {
                progressBar.setVisibility(View.GONE);
                actionButton.setVisibility(View.VISIBLE);
                if (hasLocal) {
                    actionButton.setImageResource(R.drawable.baseline_check_24);
                    actionButton.setColorFilter(YumiMd3.green());
                    actionButton.setContentDescription(MiogramLocale.get("Файл збережено, відкрити опції", "Файл сохранён, открыть опции", "File saved, open options"));
                } else {
                    actionButton.setImageResource(R.drawable.baseline_cloud_download_24);
                    actionButton.setColorFilter(YumiMd3.primary());
                    actionButton.setContentDescription(MiogramLocale.get("Завантажити з хмари", "Скачать из облака", "Download from cloud"));
                }
            }
        }
    }
}
