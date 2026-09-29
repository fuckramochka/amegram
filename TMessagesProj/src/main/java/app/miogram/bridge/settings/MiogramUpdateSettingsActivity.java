package app.miogram.bridge.settings;

import android.content.Context;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.R;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;

import java.io.File;

import app.miogram.bridge.MiogramLocale;
import app.miogram.bridge.updater.MiogramDownloadManager;
import app.miogram.bridge.updater.MiogramUpdater;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

/**
 * Dedicated Update Management Screen for Amegram:
 * - Current build status & instant manual check button
 * - Update channel switcher (Beta vs Stable)
 * - Auto-check and Wi-Fi-only network controls
 * - Cached installer management (install / free disk space)
 */
public class MiogramUpdateSettingsActivity extends BaseNekoSettingsActivity {

    private int headerStatusRow;
    private int versionDetailRow;
    private int checkNowRow;
    private int lastCheckInfoRow;

    private int headerChannelRow;
    private int channelBetaRow;
    private int channelStableRow;
    private int channelInfoRow;

    private int headerPreferencesRow;
    private int autoCheckRow;
    private int wifiOnlyRow;
    private int preferencesInfoRow;

    private int headerCacheRow = -1;
    private int installCachedRow = -1;
    private int deleteCachedRow = -1;
    private int cacheInfoRow = -1;

    @Override
    protected String getActionBarTitle() {
        return MiogramLocale.get("Оновлення Amegram", "Обновления Amegram", "Amegram Updates");
    }

    @Override
    protected void updateRows() {
        super.updateRows();

        headerStatusRow = addRow();
        versionDetailRow = addRow();
        checkNowRow = addRow();
        lastCheckInfoRow = addRow();

        headerChannelRow = addRow();
        channelBetaRow = addRow();
        channelStableRow = addRow();
        channelInfoRow = addRow();

        headerPreferencesRow = addRow();
        autoCheckRow = addRow();
        wifiOnlyRow = addRow();
        preferencesInfoRow = addRow();

        Context ctx = getParentActivity() != null ? getParentActivity() : getContext();
        File cached = MiogramDownloadManager.getCachedApk(ctx, null);
        if (cached != null && cached.exists() && cached.length() > 0) {
            headerCacheRow = addRow();
            installCachedRow = addRow();
            deleteCachedRow = addRow();
            cacheInfoRow = addRow();
        } else {
            headerCacheRow = -1;
            installCachedRow = -1;
            deleteCachedRow = -1;
            cacheInfoRow = -1;
        }
    }

    @Override
    public void onItemClick(View view, int position, float x, float y) {
        if (position == checkNowRow) {
            MiogramUpdater.checkAndShowUpdate(this, true);
            AndroidUtilities.runOnUIThread(() -> {
                if (listAdapter != null) listAdapter.notifyItemChanged(lastCheckInfoRow);
            }, 1000);
        } else if (position == channelBetaRow) {
            MiogramUpdater.setUpdateChannel(MiogramUpdater.CHANNEL_BETA);
            if (listAdapter != null) {
                listAdapter.notifyItemChanged(channelBetaRow);
                listAdapter.notifyItemChanged(channelStableRow);
                listAdapter.notifyItemChanged(versionDetailRow);
            }
        } else if (position == channelStableRow) {
            MiogramUpdater.setUpdateChannel(MiogramUpdater.CHANNEL_STABLE);
            if (listAdapter != null) {
                listAdapter.notifyItemChanged(channelBetaRow);
                listAdapter.notifyItemChanged(channelStableRow);
                listAdapter.notifyItemChanged(versionDetailRow);
            }
        } else if (position == autoCheckRow) {
            boolean v = !MiogramUpdater.isAutoCheckEnabled();
            MiogramUpdater.setAutoCheckEnabled(v);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(v);
            }
        } else if (position == wifiOnlyRow) {
            boolean v = !MiogramUpdater.isWifiOnlyEnabled();
            MiogramUpdater.setWifiOnlyEnabled(v);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(v);
            }
        } else if (position == installCachedRow) {
            Context ctx = getParentActivity() != null ? getParentActivity() : getContext();
            File cached = MiogramDownloadManager.getCachedApk(ctx, null);
            if (cached != null) {
                MiogramDownloadManager.promptInstall(ctx, cached);
            }
        } else if (position == deleteCachedRow) {
            Context ctx = getParentActivity() != null ? getParentActivity() : getContext();
            File cached = MiogramDownloadManager.getCachedApk(ctx, null);
            if (cached != null && cached.exists()) {
                cached.delete();
            }
            Toast.makeText(ctx, MiogramLocale.get("Кеш інсталяторів видалено", "Кэш установщиков удален", "Installer cache deleted"), Toast.LENGTH_SHORT).show();
            updateRows();
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
        }
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == headerStatusRow || position == headerChannelRow
                    || position == headerPreferencesRow || position == headerCacheRow) {
                return TYPE_HEADER;
            } else if (position == versionDetailRow) {
                return TYPE_DETAIL_SETTINGS;
            } else if (position == channelBetaRow || position == channelStableRow
                    || position == autoCheckRow || position == wifiOnlyRow) {
                return TYPE_CHECK;
            } else if (position == checkNowRow || position == installCachedRow || position == deleteCachedRow) {
                return TYPE_TEXT;
            } else if (position == lastCheckInfoRow || position == channelInfoRow
                    || position == preferencesInfoRow || position == cacheInfoRow) {
                return TYPE_INFO_PRIVACY;
            }
            return TYPE_TEXT;
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean partial) {
            switch (holder.getItemViewType()) {
                case TYPE_HEADER: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    if (position == headerStatusRow) {
                        cell.setText(MiogramLocale.get("Поточний стан", "Текущее состояние", "Current Status"));
                    } else if (position == headerChannelRow) {
                        cell.setText(MiogramLocale.get("Канал оновлень", "Канал обновлений", "Update Channel"));
                    } else if (position == headerPreferencesRow) {
                        cell.setText(MiogramLocale.get("Параметри", "Параметры", "Preferences"));
                    } else if (position == headerCacheRow) {
                        cell.setText(MiogramLocale.get("Завантажений файл оновлення", "Загруженный файл обновления", "Downloaded Update File"));
                    }
                    break;
                }
                case TYPE_DETAIL_SETTINGS: {
                    TextDetailSettingsCell cell = (TextDetailSettingsCell) holder.itemView;
                    if (position == versionDetailRow) {
                        String currentVer = MiogramUpdater.getCurrentAppVersion();
                        int code = MiogramUpdater.getCurrentAppVersionCode();
                        String branch = MiogramUpdater.getUpdateChannelName();
                        String subtitle = MiogramLocale.format("Збірка %d • Канал: %s", "Сборка %d • Канал: %s", "Build %d • Channel: %s", code, branch);
                        cell.setTextAndValue("Amegram v" + currentVer, subtitle, true);
                    }
                    break;
                }
                case TYPE_TEXT: {
                    TextCell cell = (TextCell) holder.itemView;
                    if (position == checkNowRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Перевірити наявність оновлень", "Проверить наличие обновлений", "Check for updates now"),
                                R.drawable.msg_retry,
                                false
                        );
                    } else if (position == installCachedRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Встановити оновлення зараз", "Установить обновление сейчас", "Install update now"),
                                R.drawable.msg_download_solar,
                                true
                        );
                    } else if (position == deleteCachedRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Видалити завантажений файл APK", "Удалить загруженный файл APK", "Delete downloaded APK file"),
                                R.drawable.msg_delete,
                                false
                        );
                    }
                    break;
                }
                case TYPE_CHECK: {
                    TextCheckCell cell = (TextCheckCell) holder.itemView;
                    if (position == channelBetaRow) {
                        boolean isBeta = MiogramUpdater.CHANNEL_BETA.equals(MiogramUpdater.getUpdateChannel());
                        cell.setTextAndCheck(
                                MiogramLocale.get("Бета-версії (Beta)", "Бета-версии (Beta)", "Beta releases"),
                                isBeta,
                                true
                        );
                    } else if (position == channelStableRow) {
                        boolean isStable = MiogramUpdater.CHANNEL_STABLE.equals(MiogramUpdater.getUpdateChannel());
                        cell.setTextAndCheck(
                                MiogramLocale.get("Стабільні релізи (Stable)", "Стабильные релизы (Stable)", "Stable releases"),
                                isStable,
                                false
                        );
                    } else if (position == autoCheckRow) {
                        cell.setTextAndCheck(
                                MiogramLocale.get("Автоматична перевірка оновлень", "Автоматическая проверка обновлений", "Auto-check for updates"),
                                MiogramUpdater.isAutoCheckEnabled(),
                                true
                        );
                    } else if (position == wifiOnlyRow) {
                        cell.setTextAndCheck(
                                MiogramLocale.get("Завантажувати лише через Wi-Fi", "Загружать только по Wi-Fi", "Download via Wi-Fi only"),
                                MiogramUpdater.isWifiOnlyEnabled(),
                                false
                        );
                    }
                    break;
                }
                case TYPE_INFO_PRIVACY: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == lastCheckInfoRow) {
                        String time = MiogramUpdater.getLastCheckTimeFormatted();
                        cell.setText(MiogramLocale.get("Остання перевірка: ", "Последняя проверка: ", "Last check: ") + time);
                    } else if (position == channelInfoRow) {
                        cell.setText(MiogramLocale.get(
                                "Бета-канал дозволяє отримувати всі оновлення та виправлення одразу після публікації. Стабільний канал містить тільки перевірені релізи.",
                                "Бета-канал позволяет получать все обновления и исправления сразу после публикации. Стабильный канал содержит только проверенные релизы.",
                                "Beta channel delivers all updates and rapid fixes immediately. Stable channel includes only vetted releases."
                        ));
                    } else if (position == preferencesInfoRow) {
                        cell.setText(MiogramLocale.get(
                                "Перевірка виконується у фоні не частіше ніж раз на 12–24 години. Якщо ви відхилили пропозицію оновитись («Нагадати пізніше»), повторне сповіщення надійде не раніше ніж через добу.",
                                "Проверка выполняется в фоне не чаще чем раз в 12–24 часа. Если вы отклонили предложение обновиться («Напомнить позже»), повторное уведомление придет не раньше чем через сутки.",
                                "Background checks run at most once every 12–24 hours without draining battery. If you dismissed an update ('Remind me later'), it will be snoozed for 24 hours."
                        ));
                    } else if (position == cacheInfoRow) {
                        Context ctx = getParentActivity() != null ? getParentActivity() : getContext();
                        File cached = MiogramDownloadManager.getCachedApk(ctx, null);
                        String size = (cached != null) ? AndroidUtilities.formatFileSize(cached.length()) : "";
                        cell.setText(MiogramLocale.get(
                                "Завантажений інсталятор займає " + size + " на внутрішньому сховищі.",
                                "Загруженный установщик занимает " + size + " во внутренней памяти.",
                                "Downloaded installer uses " + size + " of internal storage."
                        ));
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
