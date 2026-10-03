package app.amegram.module.ui;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;

import app.amegram.module.AmegramConfig;
import app.amegram.module.AmegramFeature;
import app.amegram.module.AmegramFeatureManager;
import app.amegram.module.AmegramModule;
import app.amegram.module.features.ghost.AmegramGhostController;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

/**
 * Amegram -> Modules hub. The single native-looking place where the user
 * picks "only player + ghost": each row shows RAM cost and downloads
 * nothing until enabled. Everything here is lazy — the hub itself loads
 * no feature views.
 */
public class AmegramModulesActivity extends BaseNekoSettingsActivity {

    private int headerGhostRow;
    private int ghostMasterRow;
    private int ghostReadRow;
    private int ghostOnlineRow;
    private int ghostTypingRow;
    private int vaultRow;
    private int ghostInfoRow;

    private int headerMediaRow;
    private int playerRow;
    private int playerVisualizerRow;
    private int badgesRow;
    private int antiblockRow;
    private int ameprofileRow;
    private int mediaInfoRow;

    private int headerSystemRow;
    private int guideRow;
    private int hotfixRow;
    private int hotfixCodeRow;
    private int versionRow;

    @Override
    protected String getActionBarTitle() {
        return "Amegram \u2022 \u041c\u043e\u0434\u0443\u043b\u0438";
    }

    @Override
    protected void updateRows() {
        super.updateRows();

        headerGhostRow = addRow();
        ghostMasterRow = addRow();
        ghostReadRow = addRow();
        ghostOnlineRow = addRow();
        ghostTypingRow = addRow();
        vaultRow = addRow();
        ghostInfoRow = addRow();

        headerMediaRow = addRow();
        playerRow = addRow();
        playerVisualizerRow = addRow();
        badgesRow = addRow();
        antiblockRow = addRow();
        ameprofileRow = addRow();
        mediaInfoRow = addRow();

        headerSystemRow = addRow();
        guideRow = addRow();
        hotfixRow = addRow();
        hotfixCodeRow = addRow();
        versionRow = addRow();
    }

    @Override
    public void onItemClick(View view, int position, float x, float y) {
        if (position == ghostMasterRow) {
            boolean v = !AmegramConfig.getBool("ghost_enabled", false);
            AmegramFeatureManager.setEnabled("ghost", v);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(v);
            }
            refresh();
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
        } else if (position == vaultRow) {
            if (!AmegramConfig.getBool("doublebottom_enabled", true)) {
                AmegramFeatureManager.setEnabled("doublebottom", true);
                refresh();
            }
            try {
                presentFragment(new app.miogram.bridge.vault.MiogramDoubleBottomActivity());
            } catch (Throwable ignore) {
            }
        } else if (position == playerRow) {
            boolean v = !AmegramConfig.getBool("player_enabled", true);
            AmegramFeatureManager.setEnabled("player", v);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(v);
            }
            refresh();
        } else if (position == playerVisualizerRow) {
            boolean v = !AmegramConfig.getBool("player_visualizer", false);
            AmegramConfig.setBool("player_visualizer", v);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(v);
            }
        } else if (position == badgesRow) {
            boolean v = !AmegramConfig.getBool("badges_enabled", true);
            AmegramFeatureManager.setEnabled("badges", v);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(v);
            }
            refresh();
        } else if (position == antiblockRow) {
            boolean v = !AmegramConfig.getBool("antiblock_enabled", true);
            AmegramFeatureManager.setEnabled("antiblock", v);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(v);
            }
            refresh();
        } else if (position == ameprofileRow) {
            boolean v = !AmegramConfig.getBool("ameprofile_enabled", true);
            AmegramFeatureManager.setEnabled("ameprofile", v);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(v);
            }
            refresh();
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

    private void refresh() {
        try {
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
            getNotificationCenter().postNotificationName(NotificationCenter.mainUserInfoChanged);
        } catch (Throwable ignore) {
        }
    }

    private class ListAdapter extends BaseListAdapter {
        ListAdapter(Context context) {
            super(context);
        }

        @Override
        public int getItemViewType(int position) {
            if (position == headerGhostRow || position == headerMediaRow || position == headerSystemRow) {
                return TYPE_HEADER;
            }
            if (position == ghostMasterRow || position == ghostReadRow || position == ghostOnlineRow
                    || position == ghostTypingRow || position == playerRow
                    || position == playerVisualizerRow || position == badgesRow
                    || position == antiblockRow || position == ameprofileRow
                    || position == hotfixCodeRow) {
                return TYPE_CHECK;
            }
            if (position == ghostInfoRow || position == mediaInfoRow) {
                return TYPE_INFO_PRIVACY;
            }
            return TYPE_TEXT;
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean partial) {
            switch (holder.getItemViewType()) {
                case TYPE_HEADER: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    if (position == headerGhostRow) {
                        cell.setText("\u0420\u0435\u0436\u0438\u043c \u043f\u0440\u0438\u0437\u0440\u0430\u043a\u0430");
                    } else if (position == headerMediaRow) {
                        cell.setText("\u041c\u0435\u0434\u0438\u0430 \u0438 \u0431\u0435\u0439\u0434\u0436\u0438");
                    } else if (position == headerSystemRow) {
                        cell.setText("\u0421\u0438\u0441\u0442\u0435\u043c\u0430");
                    }
                    break;
                }
                case TYPE_CHECK: {
                    TextCheckCell cell = (TextCheckCell) holder.itemView;
                    if (position == ghostMasterRow) {
                        cell.setTextAndCheck("\u041d\u0435\u0432\u0438\u0434\u0438\u043c\u043a\u0430",
                                AmegramConfig.getBool("ghost_enabled", false), true);
                    } else if (position == ghostReadRow) {
                        cell.setTextAndCheck("\u0421\u043a\u0440\u044b\u0432\u0430\u0442\u044c \u043f\u0440\u043e\u0447\u0442\u0435\u043d\u0438\u0435",
                                AmegramGhostController.hideRead(), true);
                    } else if (position == ghostOnlineRow) {
                        cell.setTextAndCheck("\u0421\u043a\u0440\u044b\u0432\u0430\u0442\u044c \u043e\u043d\u043b\u0430\u0439\u043d",
                                AmegramGhostController.hideOnline(), true);
                    } else if (position == ghostTypingRow) {
                        cell.setTextAndCheck("\u0421\u043a\u0440\u044b\u0432\u0430\u0442\u044c \u043d\u0430\u0431\u043e\u0440 \u0442\u0435\u043a\u0441\u0442\u0430",
                                AmegramGhostController.hideTyping(), false);
                    } else if (position == playerRow) {
                        cell.setTextAndCheck(featureTitle("player"),
                                AmegramConfig.getBool("player_enabled", true), true);
                    } else if (position == playerVisualizerRow) {
                        cell.setTextAndCheck("\u0412\u0438\u0437\u0443\u0430\u043b\u0438\u0437\u0430\u0442\u043e\u0440 \u0431\u0430\u0441\u043e\u0432",
                                AmegramConfig.getBool("player_visualizer", false), false);
                    } else if (position == badgesRow) {
                        cell.setTextAndCheck(featureTitle("badges"),
                                AmegramConfig.getBool("badges_enabled", true), true);
                    } else if (position == antiblockRow) {
                        cell.setTextAndCheck(featureTitle("antiblock"),
                                AmegramConfig.getBool("antiblock_enabled", true), true);
                    } else if (position == ameprofileRow) {
                        cell.setTextAndCheck(featureTitle("ameprofile"),
                                AmegramConfig.getBool("ameprofile_enabled", true), false);
                    } else if (position == hotfixCodeRow) {
                        cell.setTextAndCheck("\u0420\u0430\u0437\u0440\u0435\u0448\u0438\u0442\u044c code-\u043f\u0430\u0442\u0447\u0438 (.dex)",
                                AmegramConfig.getBool("hotfix_code_patches", false), false);
                    }
                    break;
                }
                case TYPE_TEXT: {
                    TextCell cell = (TextCell) holder.itemView;
                    if (position == vaultRow) {
                        cell.setTextAndValue(featureTitle("doublebottom"),
                                AmegramConfig.getBool("doublebottom_enabled", true)
                                        ? "\u041d\u0430\u0441\u0442\u0440\u043e\u0435\u043d\u043e" : "\u0412\u044b\u043a\u043b",
                                true);
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
                        cell.setTextAndValue("\u0412\u0435\u0440\u0441\u0438\u044f \u043c\u043e\u0434\u0443\u043b\u044f",
                                AmegramModule.MODULE_VERSION, false);
                    }
                    break;
                }
                case TYPE_INFO_PRIVACY: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == ghostInfoRow) {
                        cell.setText("\u0427\u0438\u0442\u0430\u0439 \u043d\u0435\u0437\u0430\u043c\u0435\u0442\u043d\u043e: \u0431\u043b\u043e\u043a\u0438\u0440\u0443\u0435\u0442\u0441\u044f \u043d\u0430 \u0443\u0440\u043e\u0432\u043d\u0435 \u0441\u0435\u0442\u0438. \u0418\u0441\u043a\u043b\u044e\u0447\u0435\u043d\u0438\u044f \u043f\u043e \u0447\u0430\u0442\u0430\u043c \u0440\u0430\u0431\u043e\u0442\u0430\u044e\u0442 \u043a\u0430\u043a \u0440\u0430\u043d\u044c\u0448\u0435.");
                    } else if (position == mediaInfoRow) {
                        cell.setText("\u0412\u044b\u043a\u043b\u044e\u0447\u0435\u043d\u043d\u044b\u0439 \u043c\u043e\u0434\u0443\u043b\u044c \u0433\u0440\u0443\u0437\u0438\u0442\u0441\u044f \u0432 \u043f\u0430\u043c\u044f\u0442\u044c, \u0432\u044b\u043a\u043b\u044e\u0447\u0435\u043d\u043d\u044b\u0439 \u2014 \u043d\u0435\u0442.");
                    }
                    break;
                }
            }
        }
    }

    private static String featureTitle(String id) {
        try {
            AmegramFeature f = AmegramFeatureManager.get(id);
            if (f != null) {
                return f.title() + " \u2022 " + f.ramEstimate();
            }
        } catch (Throwable ignore) {
        }
        return id;
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
