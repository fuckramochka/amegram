package app.miogram.bridge.ecosystem;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;

import app.miogram.bridge.MiogramLocale;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

/**
 * Settings screen for the TikTok MI <-> Amegram Ecosystem.
 */
public class AmegramTikTokSettingsActivity extends BaseNekoSettingsActivity {

    private static final String CHANNEL_URL = "https://t.me/fuckramochka";

    // Section 1: Connection Status
    private int headerStatusRow;
    private int statusRow;
    private int launchRow;
    private int statusInfoRow;

    // Section 2: Links & Navigation
    private int headerLinksRow;
    private int openDirectRow;
    private int cleanUrlsRow;
    private int linksInfoRow;

    // Section 3: Sync & Media
    private int headerSyncRow;
    private int themeSyncRow;
    private int clipVaultRow;
    private int soundboardRow;
    private int syncNowRow;
    private int syncInfoRow;

    @Override
    protected String getActionBarTitle() {
        return MiogramLocale.get("TikTok MI Екосистема ໒꒱", "TikTok MI Экосистема ໒꒱", "TikTok MI Ecosystem ໒꒱");
    }

    @Override
    protected void updateRows() {
        super.updateRows();

        // 1. Connection Status
        headerStatusRow = addRow();
        statusRow = addRow();
        launchRow = addRow();
        statusInfoRow = addRow();

        // 2. Links & Navigation
        headerLinksRow = addRow();
        openDirectRow = addRow();
        cleanUrlsRow = addRow();
        linksInfoRow = addRow();

        // 3. Sync & Media
        headerSyncRow = addRow();
        themeSyncRow = addRow();
        clipVaultRow = addRow();
        soundboardRow = addRow();
        syncNowRow = addRow();
        syncInfoRow = addRow();
    }

    @Override
    public void onItemClick(View view, int position, float x, float y) {
        Context context = getParentActivity();
        if (context == null) return;

        boolean isInstalled = AmegramTikTokBridge.isTikTokMiInstalled(context);

        if (position == launchRow) {
            if (isInstalled) {
                String pkg = AmegramTikTokBridge.getInstalledTikTokPackage(context);
                try {
                    Intent launch = context.getPackageManager().getLaunchIntentForPackage(pkg);
                    if (launch != null) {
                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(launch);
                    }
                } catch (Throwable ignored) {
                }
            } else {
                try {
                    Intent browser = new Intent(Intent.ACTION_VIEW, Uri.parse(CHANNEL_URL));
                    browser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(browser);
                } catch (Throwable ignored) {
                }
            }
        } else if (position == openDirectRow) {
            boolean v = !AmegramTikTokBridge.isOpenDirectEnabled();
            AmegramTikTokBridge.setOpenDirectEnabled(v);
            if (view instanceof TextCheckCell) ((TextCheckCell) view).setChecked(v);
        } else if (position == cleanUrlsRow) {
            boolean v = !AmegramTikTokBridge.isCleanUrlsEnabled();
            AmegramTikTokBridge.setCleanUrlsEnabled(v);
            if (view instanceof TextCheckCell) ((TextCheckCell) view).setChecked(v);
        } else if (position == themeSyncRow) {
            boolean v = !AmegramTikTokBridge.isThemeSyncEnabled();
            AmegramTikTokBridge.setThemeSyncEnabled(v);
            if (view instanceof TextCheckCell) ((TextCheckCell) view).setChecked(v);
        } else if (position == clipVaultRow) {
            boolean v = !AmegramTikTokBridge.isClipVaultEnabled();
            AmegramTikTokBridge.setClipVaultEnabled(v);
            if (view instanceof TextCheckCell) ((TextCheckCell) view).setChecked(v);
        } else if (position == soundboardRow) {
            boolean v = !AmegramTikTokBridge.isSoundboardEnabled();
            AmegramTikTokBridge.setSoundboardEnabled(v);
            if (view instanceof TextCheckCell) ((TextCheckCell) view).setChecked(v);
        } else if (position == syncNowRow) {
            if (!isInstalled) {
                Toast.makeText(context, MiogramLocale.get("TikTok MI не встановлено", "TikTok MI не установлен", "TikTok MI not installed"), Toast.LENGTH_SHORT).show();
                return;
            }
            int accent = Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader);
            if (accent == 0) accent = Theme.getColor(Theme.key_actionBarDefault);
            boolean dark = Theme.isCurrentThemeDark();
            boolean amoled = dark && (Theme.getColor(Theme.key_windowBackgroundWhite) == 0xFF000000);

            AmegramTikTokBridge.syncThemeToTikTokMi(context, accent, dark, amoled);
            Toast.makeText(context, MiogramLocale.get("Тему синхронізовано з TikTok MI ໒꒱", "Тема синхронизирована с TikTok MI ໒꒱", "Theme synced with TikTok MI ໒꒱"), Toast.LENGTH_SHORT).show();
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
            if (position == headerStatusRow || position == headerLinksRow || position == headerSyncRow) {
                return TYPE_HEADER;
            } else if (position == openDirectRow || position == cleanUrlsRow || position == themeSyncRow || position == clipVaultRow || position == soundboardRow) {
                return TYPE_CHECK;
            } else if (position == statusInfoRow || position == linksInfoRow || position == syncInfoRow) {
                return TYPE_INFO_PRIVACY;
            }
            return TYPE_TEXT;
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean partial) {
            Context context = getParentActivity();
            boolean isInstalled = AmegramTikTokBridge.isTikTokMiInstalled(context);

            switch (holder.getItemViewType()) {
                case TYPE_HEADER: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    if (position == headerStatusRow) {
                        cell.setText(MiogramLocale.get("Статус підключення", "Статус подключения", "Connection Status"));
                    } else if (position == headerLinksRow) {
                        cell.setText(MiogramLocale.get("Посилання та навігація", "Ссылки и навигация", "Links & Navigation"));
                    } else if (position == headerSyncRow) {
                        cell.setText(MiogramLocale.get("Синхронізація та медіа", "Синхронизация и медиа", "Sync & Media"));
                    }
                    break;
                }

                case TYPE_CHECK: {
                    TextCheckCell cell = (TextCheckCell) holder.itemView;
                    if (position == openDirectRow) {
                        cell.setTextAndCheck(
                                MiogramLocale.get("Відкривати посилання у TikTok MI", "Открывать ссылки в TikTok MI", "Open links directly in TikTok MI"),
                                AmegramTikTokBridge.isOpenDirectEnabled(),
                                true
                        );
                    } else if (position == cleanUrlsRow) {
                        cell.setTextAndCheck(
                                MiogramLocale.get("Очищення посилань від трекінгу", "Очистка ссылок от трекинга", "Clean URLs from tracking params"),
                                AmegramTikTokBridge.isCleanUrlsEnabled(),
                                false
                        );
                    } else if (position == themeSyncRow) {
                        cell.setTextAndCheck(
                                MiogramLocale.get("Двостороння синхронізація теми", "Двусторонняя синхронизация темы", "Two-way theme & accent sync"),
                                AmegramTikTokBridge.isThemeSyncEnabled(),
                                true
                        );
                    } else if (position == clipVaultRow) {
                        cell.setTextAndCheck(
                                MiogramLocale.get("Фоновий буфер ClipVault", "Фоновый буфер ClipVault", "Ambient ClipVault buffer"),
                                AmegramTikTokBridge.isClipVaultEnabled(),
                                true
                        );
                    } else if (position == soundboardRow) {
                        cell.setTextAndCheck(
                                MiogramLocale.get("TikTok Звуки (Soundboard)", "TikTok Звуки (Soundboard)", "TikTok Soundboard"),
                                AmegramTikTokBridge.isSoundboardEnabled(),
                                true
                        );
                    }
                    break;
                }

                case TYPE_INFO_PRIVACY: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == statusInfoRow) {
                        cell.setText(MiogramLocale.get(
                                "Прямий IPC-міст між Amegram та TikTok MI забезпечує обмін медіа та темами з затримкою менше 2 мс.",
                                "Прямой IPC-мост между Amegram и TikTok MI обеспечивает обмен медиа и темами с задержкой менее 2 мс.",
                                "Direct IPC bridge between Amegram and TikTok MI enables sub-2ms media and theme exchange."
                        ));
                    } else if (position == linksInfoRow) {
                        cell.setText(MiogramLocale.get(
                                "При переході за посиланнями TikTok вони автоматично відкриваються у TikTok MI без будь-яких діалогів чи підтверджень.",
                                "При переходе по ссылкам TikTok они автоматически открываются в TikTok MI без диалогов и подтверждений.",
                                "TikTok links automatically open directly in TikTok MI without any interrupting dialogs."
                        ));
                    } else if (position == syncInfoRow) {
                        cell.setText(MiogramLocale.get(
                                "ClipVault створює делікатну неблокуючу хмарку над полем вводу чату, коли ви копіюєте медіа у TikTok MI.",
                                "ClipVault создаёт деликатное неблокирующее облачко над полем ввода чата при копировании медиа в TikTok MI.",
                                "ClipVault shows a subtle non-blocking pill above the chat input when you copy media in TikTok MI."
                        ));
                    }
                    break;
                }

                case TYPE_TEXT: {
                    TextCell cell = (TextCell) holder.itemView;
                    if (position == statusRow) {
                        String pkg = AmegramTikTokBridge.getInstalledTikTokPackage(context);
                        cell.setTextAndValueAndIcon(
                                "TikTok MI",
                                isInstalled ? (pkg != null ? pkg : MiogramLocale.get("Підключено", "Подключено", "Connected")) : MiogramLocale.get("Не знайдено", "Не найдено", "Not found"),
                                R.drawable.msg_fave,
                                true
                        );
                    } else if (position == launchRow) {
                        cell.setTextAndIcon(
                                isInstalled ? MiogramLocale.get("Відкрити TikTok MI", "Открыть TikTok MI", "Open TikTok MI") : MiogramLocale.get("Канал @fuckramochka", "Канал @fuckramochka", "Channel @fuckramochka"),
                                R.drawable.msg_openin,
                                false
                        );
                    } else if (position == syncNowRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Синхронізувати тему зараз", "Синхронизировать тему сейчас", "Sync theme now"),
                                R.drawable.msg_theme,
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
