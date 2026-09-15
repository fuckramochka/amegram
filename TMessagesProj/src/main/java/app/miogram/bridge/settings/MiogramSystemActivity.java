package app.miogram.bridge.settings;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;

import app.miogram.bridge.MiogramLocale;
import app.miogram.bridge.ui.MiogramPerformanceActivity;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

/**
 * System hub: plugins, performance, notifications, advanced client settings
 * and updates. Keeps the main Miogram menu to a handful of rows.
 */
public class MiogramSystemActivity extends BaseNekoSettingsActivity {

    private int headerSystemRow;
    private int pluginsRow;
    private int performanceRow;
    private int pushRow;
    private int generalRow;
    private int updaterRow;
    private int externalModsRow;

    @Override
    protected String getActionBarTitle() {
        return MiogramLocale.get("Система", "Система", "System");
    }

    @Override
    protected void updateRows() {
        super.updateRows();

        headerSystemRow = addRow();
        pluginsRow = addRow();
        performanceRow = addRow();
        pushRow = addRow();
        generalRow = addRow();
        updaterRow = addRow();
        externalModsRow = addRow();
    }

    @Override
    public void onItemClick(View view, int position, float x, float y) {
        if (position == pluginsRow) {
            presentFragment(new app.exteraless.plugins.ui.PluginsActivity());
        } else if (position == performanceRow) {
            presentFragment(new MiogramPerformanceActivity());
        } else if (position == pushRow) {
            app.miogram.bridge.push.MiogramPushSheet sheet = new app.miogram.bridge.push.MiogramPushSheet(getParentActivity(), null);
            sheet.show();
            if (listView != null && listView.getAdapter() != null) {
                AndroidUtilities.runOnUIThread(() -> listView.getAdapter().notifyDataSetChanged(), 1500);
            }
        } else if (position == generalRow) {
            presentFragment(new app.exteraless.settings.OpenExteraGeneralActivity());
        } else if (position == updaterRow) {
            app.miogram.bridge.updater.MiogramUpdater.checkAndShowUpdate(this, true);
        } else if (position == externalModsRow) {
            boolean next = !app.miogram.bridge.modapi.MioBridgeService.isExternalModsEnabled();
            app.miogram.bridge.modapi.MioBridgeService.setExternalModsEnabled(next);
            if (view instanceof TextCheckCell) ((TextCheckCell) view).setChecked(next);
        }
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public int getItemViewType(int position) {
            if (position == headerSystemRow) {
                return TYPE_HEADER;
            } else if (position == externalModsRow) {
                return TYPE_CHECK;
            }
            return TYPE_TEXT;
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean partial) {
            switch (holder.getItemViewType()) {
                case TYPE_HEADER: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    cell.setText(MiogramLocale.get("Система, плагіни та екосистема", "Система, плагины и экосистема", "System, Plugins & Ecosystem"));
                    break;
                }
                case TYPE_CHECK: {
                    TextCheckCell cell = (TextCheckCell) holder.itemView;
                    cell.setTextAndCheck(
                            MiogramLocale.get("Зовнішні моди (AIDL-міст)", "Внешние моды (AIDL-мост)", "External mods (AIDL bridge)"),
                            app.miogram.bridge.modapi.MioBridgeService.isExternalModsEnabled(),
                            false
                    );
                    break;
                }
                case TYPE_TEXT: {
                    TextCell cell = (TextCell) holder.itemView;
                    if (position == pluginsRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Плагіни та модулі", "Плагины и модули", "Plugins & Modules"),
                                R.drawable.msg_plugins,
                                true
                        );
                    } else if (position == performanceRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Продуктивність та оптимізація", "Производительность и оптимизация", "Performance & Optimization"),
                                R.drawable.msg_speed_solar,
                                true
                        );
                    } else if (position == pushRow) {
                        cell.setTextAndValueAndIcon(
                                MiogramLocale.get("Сповіщення та фон", "Уведомления и фон", "Notifications & Background"),
                                app.miogram.bridge.push.MiogramPushSheet.getShortStatus(),
                                R.drawable.baseline_notifications_24,
                                true
                        );
                    } else if (position == generalRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Розширені налаштування клієнта", "Расширенные настройки клиента", "Advanced Client Settings"),
                                R.drawable.msg_settings,
                                true
                        );
                    } else if (position == updaterRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Перевірити оновлення", "Проверить обновления", "Check for Updates"),
                                R.drawable.msg_download_solar,
                                false
                        );
                    }
                    break;
                }
            }
        }
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }
}
