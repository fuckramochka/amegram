package app.amegram.module.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.Switch;

import java.util.ArrayList;
import java.util.List;

import app.amegram.core.modules.ModuleManager;
import app.amegram.hot.HotCatalog;
import app.amegram.hot.HotModulesManager;
import app.amegram.hot.ui.HotModuleDetailSheet;
import app.amegram.hot.ui.HotStoreActivity;
import app.amegram.module.AmegramConfig;
import app.amegram.module.AmegramFeature;
import app.amegram.module.AmegramFeatureManager;
import app.amegram.module.AmegramModule;
import app.amegram.module.features.ghost.AmegramGhostController;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

/**
 * Amegram -> Modules hub (spec):
 * - зверху картки скачаних модулів (стиль як у каталозі плагінів):
 *   назва, версія, опис, навантаження, перемикач, «Видалити»;
 * - кнопка «+» докачує з каталогу GitHub;
 * - секції налаштувань існують тільки для увімкнених модулів
 *   (вимкнув/видалив — розділ зникає, рядки перебудовуються);
 * - нижче — плагіни; все нативно (TextCell/Switch/Theme).
 * При всіх увімкнених — поведінка 1:1 як нинішній мод.
 */
public class AmegramModulesActivity extends BaseNekoSettingsActivity {

    private static final int TYPE_MODULE_CARD = 100;

    private static final String[] CARD_ORDER = {
            "ghost", "badges", "antiblock", "ameprofile", "hotfix", "doublebottom"
    };

    private static class CardRef {
        String id;
        boolean isAmod;
    }

    /**
     * Мапінг карток хаба на Hot-модулі (.hmod з магазину).
     * badges/antiblock/hotfix повертають null: це функції ядра без .hmod —
     * вони чесно підписані "ядро", без фейкових версій і видалення.
     */
    private static String hotIdFor(String legacyId) {
        if ("ghost".equals(legacyId)) return "ghost";
        if ("ameprofile".equals(legacyId)) return "ame";
        if ("doublebottom".equals(legacyId)) return "vault";
        return null;
    }

    private static String installedHotVersion(String hotId) {
        try {
            for (HotModulesManager.InstalledInfo i : HotModulesManager.listInstalled()) {
                if (i.manifest.id.equals(hotId) && i.active) return i.manifest.version;
            }
        } catch (Throwable ignore) {
        }
        return "";
    }

    private void toast(String msg) {
        try {
            android.widget.Toast.makeText(getParentActivity(), msg,
                    android.widget.Toast.LENGTH_LONG).show();
        } catch (Throwable ignore) {
        }
    }

    private List<CardRef> cardRefs;

    private int headerModulesRow;
    private int plusRow;

    private int headerGhostRow;
    private int ghostOfflineRow;
    private int ghostReadRow;
    private int ghostOnlineRow;
    private int ghostTypingRow;
    private int vaultRow;
    private int ghostInfoRow;

    private int headerPluginsRow;
    private int pluginsRow;

    private int headerSystemRow;
    private int guideRow;
    private int hotfixRow;
    private int hotfixCodeRow;
    private int versionRow;

    @Override
    protected String getActionBarTitle() {
        return "Amegram \u2022 \u041c\u043e\u0434\u0443\u043b\u0438";
    }

    private boolean ghostOn() {
        return AmegramConfig.getBool("ghost_enabled", false);
    }

    private boolean vaultOn() {
        return AmegramConfig.getBool("doublebottom_enabled", true);
    }

    @Override
    protected void updateRows() {
        super.updateRows();
        if (cardRefs == null) {
            cardRefs = new ArrayList<>();
        }
        cardRefs.clear();

        headerModulesRow = addRow();
        for (String id : CARD_ORDER) {
            CardRef ref = new CardRef();
            ref.id = id;
            ref.isAmod = false;
            cardRefs.add(ref);
            addRow();
        }
        try {
            for (ModuleManager.Installed inst : ModuleManager.all()) {
                CardRef ref = new CardRef();
                ref.id = inst.manifest.id;
                ref.isAmod = true;
                cardRefs.add(ref);
                addRow();
            }
        } catch (Throwable ignore) {
        }
        plusRow = addRow();

        if (ghostOn()) {
            headerGhostRow = addRow();
            ghostOfflineRow = addRow();
            ghostReadRow = addRow();
            ghostOnlineRow = addRow();
            ghostTypingRow = addRow();
            ghostInfoRow = addRow();
        } else {
            headerGhostRow = ghostOfflineRow = ghostReadRow = ghostOnlineRow = ghostTypingRow = ghostInfoRow = -1;
        }
        if (vaultOn()) {
            vaultRow = addRow();
        } else {
            vaultRow = -1;
        }

        headerPluginsRow = addRow();
        pluginsRow = addRow();

        headerSystemRow = addRow();
        guideRow = addRow();
        hotfixRow = addRow();
        hotfixCodeRow = addRow();
        versionRow = addRow();
    }

    private CardRef cardAt(int position) {
        if (cardRefs == null) {
            return null;
        }
        int idx = position - headerModulesRow - 1;
        if (idx < 0 || idx >= cardRefs.size()) {
            return null;
        }
        return cardRefs.get(idx);
    }

    private static String descFor(String id) {
        if ("ghost".equals(id)) {
            return "Невидимка: читай непомітно (read/online/typing)";
        } else if ("badges".equals(id)) {
            return "10 піксельних бейджів з хмарною синхронізацією";
        } else if ("antiblock".equals(id)) {
            return "Обхід блокувань: зонди + fallback-проксі";
        } else if ("ameprofile".equals(id)) {
            return "Живий XML-профіль: банери, картки, стиль";
        } else if ("hotfix".equals(id)) {
            return "Швидкі фікси без перевстановлення APK";
        } else if ("doublebottom".equals(id)) {
            return "Два PIN: свій відкриває все, тривожний — decoy";
        }
        return "Модуль з каталогу";
    }

    private static String versionFor(CardRef ref) {
        if (ref.isAmod) {
            try {
                for (ModuleManager.Installed inst : ModuleManager.all()) {
                    if (inst.manifest.id.equals(ref.id)) {
                        return "v" + inst.manifest.version;
                    }
                }
            } catch (Throwable ignore) {
            }
            return "";
        }
        String hotId = hotIdFor(ref.id);
        if (hotId != null) {
            String v = installedHotVersion(hotId);
            return v.isEmpty() ? "з магазину" : "v" + v;
        }
        return "ядро";
    }

    private static boolean enabledFor(CardRef ref) {
        if (ref.isAmod) {
            return AmegramConfig.getBool("amod_" + ref.id, true);
        }
        String hotId = hotIdFor(ref.id);
        if (hotId != null) {
            return HotModulesManager.isModuleEnabled(hotId);
        }
        AmegramFeature f = AmegramFeatureManager.get(ref.id);
        return f != null && f.isEnabled();
    }

    private static String loadFor(CardRef ref) {
        String base;
        if (ref.isAmod) {
            base = "з каталогу";
        } else {
            String hotId = hotIdFor(ref.id);
            if (hotId != null) {
                if (HotModulesManager.isModuleInstalled(hotId)) {
                    base = HotModulesManager.getModuleLoadEstimate(hotId);
                    return base;
                }
                base = "не встановлено";
            } else {
                AmegramFeature f = AmegramFeatureManager.get(ref.id);
                base = (f != null ? f.ramEstimate() : "") + " • ядро";
            }
        }
        return base + (enabledFor(ref) ? " \u2022 \u0443\u0432\u0456\u043c\u043a\u043d\u0435\u043d\u043e" : " \u2022 \u0441\u043f\u0438\u0442\u044c");
    }

    private static String titleFor(CardRef ref) {
        if (!ref.isAmod) {
            AmegramFeature f = AmegramFeatureManager.get(ref.id);
            if (f != null) {
                return f.title();
            }
        }
        return ref.id;
    }

    @Override
    public void onItemClick(View view, int position, float x, float y) {
        if (position == plusRow) {
            try {
                presentFragment(new HotStoreActivity());
            } catch (Throwable ignore) {
            }
        } else if (position == ghostReadRow) {
            boolean v = !AmegramGhostController.hideRead();
            AmegramGhostController.setHideRead(v);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(v);
            }
        } else if (position == ghostOnlineRow) {
            boolean v = !AmegramGhostController.hideOnline();
            AmegramGhostController.setHideOnline(v);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(v);
            }
        } else if (position == ghostTypingRow) {
            boolean v = !AmegramGhostController.hideTyping();
            AmegramGhostController.setHideTyping(v);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(v);
            }
        } else if (position == ghostOfflineRow) {
            boolean v = !AmegramConfig.getBool("ghost_offline_after_send", false);
            AmegramConfig.setBool("ghost_offline_after_send", v);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(v);
            }
        } else if (position == vaultRow) {
            try {
                presentFragment(new app.miogram.bridge.vault.MiogramDoubleBottomActivity());
            } catch (Throwable ignore) {
            }
        } else if (position == pluginsRow) {
            try {
                presentFragment(new app.exteraless.plugins.ui.PluginsActivity());
            } catch (Throwable ignore) {
            }
        } else if (position == guideRow) {
            try {
                new AmegramWelcomeSheet(getParentActivity()).show();
            } catch (Throwable ignore) {
            }
        } else if (position == hotfixRow) {
            try {
                app.miogram.bridge.patch.AmegramPatchManager.getInstance()
                        .checkForPatches((n, msg) -> refresh());
            } catch (Throwable ignore) {
            }
        } else if (position == hotfixCodeRow) {
            boolean v = !AmegramConfig.getBool("hotfix_code_patches", false);
            AmegramConfig.setBool("hotfix_code_patches", v);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(v);
            }
        }
    }

    private void toggleCard(CardRef ref, boolean on) {
        if (ref.isAmod) {
            AmegramConfig.setBool("amod_" + ref.id, on);
            refresh();
            return;
        }
        String hotId = hotIdFor(ref.id);
        if (hotId == null) {
            AmegramFeatureManager.setEnabled(ref.id, on);
            refresh();
            return;
        }
        if (!HotModulesManager.isModuleInstalled(hotId)) {
            if (!on) {
                refresh();
                return;
            }
            // Модуля нема — качаємо stable з магазину і вмикаємо. Причина провалу — в тост.
            toast("Качаю " + ref.id + " з магазину…");
            HotModulesManager.fetchCatalog(false, (ok, msg, catalog) -> {
                HotCatalog.Entry e = (ok && catalog != null) ? catalog.find(hotId) : null;
                HotCatalog.Build def = e != null ? e.latestCompatible(HotModulesManager.appVersion()) : null;
                if (def == null && e != null) def = e.defaultBuild();
                if (e == null || def == null) {
                    toast("Немає в каталозі: " + hotId);
                    refresh();
                    return;
                }
                HotModulesManager.downloadBuild(hotId, def, true,
                        new HotModulesManager.ProgressCallback<Void>() {
                            @Override
                            public void onProgress(long downloaded, long total) {
                            }

                            @Override
                            public void onDone(boolean ok2, String msg2, Void d) {
                                toast(ok2 ? "✓ " + hotId + " v" + msg2 + " увімкнено" : String.valueOf(msg2));
                                refresh();
                            }
                        });
            });
            return;
        }
        HotModulesManager.setEnabled(hotId, on, (ok, msg, d) -> {
            if (!ok && msg != null && !msg.isEmpty()) toast(msg);
            refresh();
        });
    }

    /** Тап по Hot-картці → деталка з описом, версіями і великою кнопкою. */
    private void openHotDetail(String hotId) {
        HotModulesManager.fetchCatalog(false, (ok, msg, catalog) -> {
            HotCatalog.Entry found = (ok && catalog != null) ? catalog.find(hotId) : null;
            HotCatalog.Build sel = null;
            if (found != null) {
                String iv = installedHotVersion(hotId);
                for (HotCatalog.Build b : found.branches.values()) {
                    if (b.version.equals(iv)) {
                        sel = b;
                        break;
                    }
                }
                if (sel == null) sel = found.defaultBuild();
            }
            try {
                new HotModuleDetailSheet(getParentActivity(), hotId, found, sel,
                        AmegramModulesActivity.this::refresh).show();
            } catch (Throwable ignore) {
            }
        });
    }

    private void deleteCard(CardRef ref) {
        if (ref.isAmod) {
            try {
                ModuleManager.uninstall(ref.id);
            } catch (Throwable ignore) {
            }
            refresh();
            return;
        }
        String hotId = hotIdFor(ref.id);
        if (hotId != null && HotModulesManager.isModuleInstalled(hotId)) {
            HotModulesManager.deleteModule(hotId);
            refresh();
        }
    }

    private void refresh() {
        try {
            HotModulesManager.init(getParentActivity().getApplicationContext());
            updateRows();
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
            getNotificationCenter().postNotificationName(NotificationCenter.mainUserInfoChanged);
        } catch (Throwable ignore) {
        }
    }

    private static class CardHolder extends RecyclerListView.Holder {
        ImageView icon;
        FrameLayout iconFrame;
        TextView title;
        TextView version;
        TextView desc;
        TextView load;
        Switch toggle;
        TextView deleteBtn;
        TextView rollbackBtn;
        CardRef ref;

        CardHolder(View itemView) {
            super(itemView);
        }
    }

    private class ListAdapter extends BaseListAdapter {
        ListAdapter(Context context) {
            super(context);
        }

        @Override
        public int getItemViewType(int position) {
            if (position == headerModulesRow || position == headerGhostRow
                    || position == headerPluginsRow
                    || position == headerSystemRow) {
                return TYPE_HEADER;
            }
            if (cardAt(position) != null) {
                return TYPE_MODULE_CARD;
            }
            if (position == ghostOfflineRow || position == ghostReadRow || position == ghostOnlineRow || position == ghostTypingRow
                    || position == hotfixCodeRow) {
                return TYPE_CHECK;
            }
            if (position == ghostInfoRow) {
                return TYPE_INFO_PRIVACY;
            }
            return TYPE_TEXT;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_MODULE_CARD) {
                Context context = parent.getContext();
                LinearLayout card = new LinearLayout(context);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setBackground(app.amegram.theme.YumiTheme.cardBackground());
                card.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12),
                        AndroidUtilities.dp(16), AndroidUtilities.dp(12));

                LinearLayout topRow = new LinearLayout(context);
                topRow.setOrientation(LinearLayout.HORIZONTAL);
                topRow.setGravity(Gravity.CENTER_VERTICAL);

                FrameLayout iconFrame = new FrameLayout(context);
                ImageView iconView = new ImageView(context);
                iconView.setColorFilter(0xFFFFFFFF);
                iconFrame.addView(iconView, LayoutHelper.createFrame(22, 22, Gravity.CENTER));
                topRow.addView(iconFrame, LayoutHelper.createLinear(
                        40, 40, Gravity.CENTER_VERTICAL, 0, 0, 12, 0));

                LinearLayout titleBox = new LinearLayout(context);
                titleBox.setOrientation(LinearLayout.VERTICAL);

                LinearLayout nameRow = new LinearLayout(context);
                nameRow.setOrientation(LinearLayout.HORIZONTAL);
                nameRow.setGravity(Gravity.CENTER_VERTICAL);
                TextView title = new TextView(context);
                title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15.5f);
                title.setTypeface(AndroidUtilities.bold());
                try {
                    title.setTextColor(getThemedColor(
                            org.telegram.ui.ActionBar.Theme.key_dialogTextBlack));
                } catch (Throwable t) {
                    title.setTextColor(0xFF000000);
                }
                nameRow.addView(title, LayoutHelper.createLinear(
                        LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
                TextView version = new TextView(context);
                version.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11.5f);
                version.setPadding(AndroidUtilities.dp(6), 0, 0, 0);
                try {
                    version.setTextColor(getThemedColor(
                            org.telegram.ui.ActionBar.Theme.key_dialogTextGray2));
                } catch (Throwable t) {
                    version.setTextColor(0xFF8A8A8A);
                }
                nameRow.addView(version, LayoutHelper.createLinear(
                        LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
                titleBox.addView(nameRow, LayoutHelper.createLinear(
                        LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

                TextView desc = new TextView(context);
                desc.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
                try {
                    desc.setTextColor(getThemedColor(
                            org.telegram.ui.ActionBar.Theme.key_dialogTextBlack));
                } catch (Throwable t) {
                    desc.setTextColor(0xFF000000);
                }
                titleBox.addView(desc, LayoutHelper.createLinear(
                        LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
                topRow.addView(titleBox, LayoutHelper.createLinear(
                        0, LayoutHelper.WRAP_CONTENT, 1.0f));

                // Switch has no onMeasure: fixed 37x20 like TextCheckCell, else it stretches.
                Switch toggle = new Switch(context);
                topRow.addView(toggle, LayoutHelper.createLinear(
                        37, 20, Gravity.CENTER_VERTICAL, 8, 0, 0, 0));
                card.addView(topRow, LayoutHelper.createLinear(
                        LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

                LinearLayout bottomRow = new LinearLayout(context);
                bottomRow.setOrientation(LinearLayout.HORIZONTAL);
                bottomRow.setGravity(Gravity.CENTER_VERTICAL);
                TextView load = new TextView(context);
                load.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11.5f);
                try {
                    load.setTextColor(getThemedColor(
                            org.telegram.ui.ActionBar.Theme.key_dialogTextGray2));
                } catch (Throwable t) {
                    load.setTextColor(0xFF8A8A8A);
                }
                bottomRow.addView(load, LayoutHelper.createLinear(
                        0, LayoutHelper.WRAP_CONTENT, 1.0f));
                TextView rollbackBtn = new TextView(context);
                rollbackBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
                rollbackBtn.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(4),
                        AndroidUtilities.dp(8), AndroidUtilities.dp(4));
                bottomRow.addView(rollbackBtn, LayoutHelper.createLinear(
                        LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
                TextView deleteBtn = new TextView(context);
                deleteBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
                deleteBtn.setTextColor(0xFFFF3B30);
                deleteBtn.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(4),
                        AndroidUtilities.dp(8), AndroidUtilities.dp(4));
                bottomRow.addView(deleteBtn, LayoutHelper.createLinear(
                        LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
                card.addView(bottomRow, LayoutHelper.createLinear(
                        LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 0));

                CardHolder holder = new CardHolder(card);
                holder.icon = iconView;
                holder.iconFrame = iconFrame;
                holder.title = title;
                holder.version = version;
                holder.desc = desc;
                holder.load = load;
                holder.toggle = toggle;
                holder.deleteBtn = deleteBtn;
                holder.rollbackBtn = rollbackBtn;
                toggle.setOnClickListener(v -> {
                    if (holder.ref != null) {
                        toggleCard(holder.ref, !enabledFor(holder.ref));
                    }
                });
                deleteBtn.setOnClickListener(v -> {
                    if (holder.ref != null) {
                        deleteCard(holder.ref);
                    }
                });
                rollbackBtn.setOnClickListener(v -> {
                    if (holder.ref != null && holder.ref.isAmod) {
                        int r = ModuleManager.rollback(holder.ref.id);
                        if (r == ModuleManager.ROLLBACK_NEED_MANUAL) {
                            new ModuleVersionsSheet(v.getContext(), holder.ref.id,
                                    AmegramModulesActivity.this::refresh).show();
                        } else {
                            refresh();
                        }
                    }
                });
                return holder;
            }
            return super.onCreateViewHolder(parent, viewType);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean partial) {
            if (holder instanceof CardHolder) {
                bindCard((CardHolder) holder, position);
                return;
            }
            switch (holder.getItemViewType()) {
                case TYPE_HEADER: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    if (position == headerModulesRow) {
                        cell.setText("\u041c\u043e\u0434\u0443\u043b\u0456");
                    } else if (position == headerGhostRow) {
                        cell.setText("\u041d\u0435\u0432\u0438\u0434\u0438\u043c\u043a\u0430");
                    } else if (position == headerPluginsRow) {
                        cell.setText("\u041f\u043b\u0430\u0433\u0456\u043d\u0438");
                    } else if (position == headerSystemRow) {
                        cell.setText("\u0421\u0438\u0441\u0442\u0435\u043c\u0430");
                    }
                    break;
                }
                case TYPE_CHECK: {
                    TextCheckCell cell = (TextCheckCell) holder.itemView;
                    if (position == ghostOfflineRow) {
                        cell.setTextAndCheck("Уходити в офлайн після відправки",
                                AmegramConfig.getBool("ghost_offline_after_send", false), false);
                    } else if (position == ghostReadRow) {
                        cell.setTextAndCheck("\u0421\u043a\u0440\u0438\u0432\u0430\u0442\u044c \u043f\u0440\u043e\u0447\u0442\u0435\u043d\u0438\u0435",
                                AmegramGhostController.hideRead(), true);
                    } else if (position == ghostOnlineRow) {
                        cell.setTextAndCheck("\u0421\u043a\u0440\u044b\u0432\u0430\u0442\u044c \u043e\u043d\u043b\u0430\u0439\u043d",
                                AmegramGhostController.hideOnline(), true);
                    } else if (position == ghostTypingRow) {
                        cell.setTextAndCheck("\u0421\u043a\u0440\u044b\u0432\u0430\u0442\u044c \u043d\u0430\u0431\u043e\u0440 \u0442\u0435\u043a\u0441\u0442\u0430",
                                AmegramGhostController.hideTyping(), false);
                    } else if (position == hotfixCodeRow) {
                        cell.setTextAndCheck("\u0420\u0430\u0437\u0440\u0435\u0448\u0438\u0442\u044c code-\u043f\u0430\u0442\u0447\u0438 (.dex)",
                                AmegramConfig.getBool("hotfix_code_patches", false), false);
                    }
                    break;
                }
                case TYPE_TEXT: {
                    TextCell cell = (TextCell) holder.itemView;
                    if (position == plusRow) {
                        cell.setTextAndIcon("+ \u0414\u043e\u043a\u0430\u0447\u0430\u0442\u0438 \u043c\u043e\u0434\u0443\u043b\u0456",
                                R.drawable.msg_download_solar, true);
                    } else if (position == vaultRow) {
                        cell.setTextAndValue("\u0414\u0432\u043e\u0439\u043d\u043e\u0435 \u0434\u043d\u043e",
                                "\u041d\u0430\u0441\u0442\u0440\u043e\u0435\u043d\u043e", true);
                    } else if (position == pluginsRow) {
                        cell.setTextAndIcon("\u041a\u0430\u0442\u0430\u043b\u043e\u0433 \u043f\u043b\u0430\u0433\u0456\u043d\u0456\u0432",
                                R.drawable.msg_plugins, true);
                    } else if (position == guideRow) {
                        cell.setTextAndIcon("\u0413\u0438\u0434 \u043f\u043e Amegram",
                                R.drawable.msg_bot, true);
                    } else if (position == hotfixRow) {
                        int applied = 0;
                        try {
                            applied = app.miogram.bridge.patch.AmegramPatchManager
                                    .getInstance().getAppliedPatchCount();
                        } catch (Throwable ignore) {
                        }
                        cell.setTextAndValueAndIcon("\u041f\u0440\u043e\u0432\u0435\u0440\u0438\u0442\u044c \u0445\u043e\u0442\u0444\u0438\u043a\u0441\u044b",
                                applied > 0 ? "\u041f\u0440\u0438\u043c\u0435\u043d\u0435\u043d\u043e: " + applied : "OK",
                                R.drawable.msg_download_solar, false);
                    } else if (position == versionRow) {
                        cell.setTextAndValue("\u042f\u0434\u0440\u043e / \u043c\u043e\u0434\u0443\u043b\u044c",
                                app.amegram.core.AmegramCore.CORE_VERSION
                                        + " / " + AmegramModule.MODULE_VERSION, false);
                    }
                    break;
                }
                case TYPE_INFO_PRIVACY: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == ghostInfoRow) {
                        cell.setText("\u0427\u0438\u0442\u0430\u0439 \u043d\u0435\u0437\u0430\u043c\u0435\u0442\u043d\u043e: \u0431\u043b\u043e\u043a\u0438\u0440\u0443\u0435\u0442\u0441\u044f \u043d\u0430 \u0443\u0440\u043e\u0432\u043d\u0435 \u0441\u0435\u0442\u0438.");
                    }
                    break;
                }
            }
        }

        private void bindCard(CardHolder holder, int position) {
            CardRef ref = cardAt(position);
            holder.ref = ref;
            if (ref == null) {
                return;
            }
            // Іконка + колір: Hot-модулі через HotModuleMeta, ядро — нейтральний плагін.
            try {
                String hotTap = !ref.isAmod ? hotIdFor(ref.id) : null;
                String metaId = hotTap != null ? hotTap : ref.id;
                holder.icon.setImageResource(app.amegram.hot.ui.HotModuleMeta.icon(metaId));
                holder.iconFrame.setBackground(
                        app.amegram.theme.YumiTheme.squircleIconBackground(
                                app.amegram.hot.ui.HotModuleMeta.color(metaId)));
            } catch (Throwable ignore) {
            }
            holder.title.setText(titleFor(ref));
            holder.version.setText(versionFor(ref));
            holder.desc.setText(descFor(ref.id));
            holder.load.setText(loadFor(ref));
            boolean on = enabledFor(ref);
            try {
                if (holder.toggle.isChecked() != on) {
                    holder.toggle.setChecked(on, false);
                }
            } catch (Throwable ignore) {
            }
            if (ref.isAmod) {
                holder.deleteBtn.setVisibility(View.VISIBLE);
                holder.deleteBtn.setText("\u0412\u0438\u0434\u0430\u043b\u0438\u0442\u0438");
                List<String> hist = ModuleManager.historyVersions(ref.id);
                if (!hist.isEmpty()) {
                    holder.rollbackBtn.setVisibility(View.VISIBLE);
                    holder.rollbackBtn.setText("\u21a9 \u0412\u0456\u0434\u043a\u043e\u0442\u0438\u0442\u0438");
                } else {
                    holder.rollbackBtn.setVisibility(View.GONE);
                }
            } else if (hotIdFor(ref.id) != null
                    && HotModulesManager.isModuleInstalled(hotIdFor(ref.id))) {
                holder.deleteBtn.setVisibility(View.VISIBLE);
                holder.deleteBtn.setText("\u0412\u0438\u0434\u0430\u043b\u0438\u0442\u0438");
                holder.rollbackBtn.setVisibility(View.GONE);
            } else {
                holder.deleteBtn.setVisibility(View.GONE);
                holder.rollbackBtn.setVisibility(View.GONE);
            }
            final String hotTap = !ref.isAmod ? hotIdFor(ref.id) : null;
            holder.itemView.setOnClickListener(hotTap != null
                    ? v -> openHotDetail(hotTap) : null);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }
}
