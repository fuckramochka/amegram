package app.miogram.bridge.settings;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.BuildVars;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Components.AudioPlayerAlert;

import app.miogram.bridge.MiogramLocale;
import app.miogram.bridge.ai.companion.MiogramCompanionActivity;
import app.miogram.bridge.ui.MiogramAiSettingsActivity;
import app.miogram.bridge.ui.MiogramChatsSettingsActivity;
import app.miogram.bridge.ui.MiogramPrivacySettingsActivity;
import app.miogram.bridge.ui.MiogramVisualsActivity;
import app.miogram.bridge.updater.MiogramUpdater;
import app.miogram.bridge.userbot.MiogramHerokuActivity;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

/**
 * Miogram Settings Hub:
 * 3 Clear Groups with focused lead buttons opening dedicated screens.
 * 1. Кастом, аудіо та функціонал (Visuals, Badges, Player, Connected Music, Chats, Privacy)
 * 2. Додаткові фішки, ШІ та плагіни (Cloud Vault, AI Companion, AI Engine, Plugins, Userbot)
 * 3. Підтримка проекту, Про Miogram та оновлення (About, Official Channel @dkmiogram, Updates)
 */
public class MiogramSettingsActivity extends BaseNekoSettingsActivity {

    public static final String CHANNEL_USERNAME = MiogramUpdater.CHANNEL_USERNAME;
    public static final String FALLBACK_CHANNEL_USERNAME = MiogramUpdater.FALLBACK_CHANNEL_USERNAME;

    // Group 1: Кастом, аудіо та функціонал
    private int headerCustomRow;
    private int visualsRow;
    private int playerEditRow;
    private int badgeStudioRow;
    private int spotifyRow;
    private int tiktokEcosystemRow;
    private int chatsRow;
    private int privacyRow;
    private int mioMomentsRow;

    // Group 2: Додаткові фішки, ШІ та плагіни
    private int headerExtrasRow;
    private int cloudVaultRow;
    private int companionRow;
    private int aiEngineRow;
    private int pluginsRow;
    private int userbotHubRow;

    // Group 3: Підтримка проекту, Про Miogram та оновлення
    private int headerAboutRow;
    private int aboutRow;
    private int channelRow;
    private int updaterRow;

    @Override
    protected String getActionBarTitle() {
        return "Amegram";
    }

    @Override
    protected void updateRows() {
        super.updateRows();

        // Оформлення
        headerCustomRow = addRow();
        visualsRow = addRow();
        playerEditRow = addRow();
        badgeStudioRow = addRow();
        spotifyRow = addRow();
        tiktokEcosystemRow = addRow();

        // Приватність і чати
        headerExtrasRow = addRow();
        chatsRow = addRow();
        privacyRow = addRow();
        mioMomentsRow = addRow();

        // Штучний інтелект і плагіни
        headerAboutRow = addRow(); // using headerAboutRow as 3rd header
        companionRow = addRow();
        aiEngineRow = addRow();
        cloudVaultRow = addRow();
        pluginsRow = addRow();
        userbotHubRow = addRow();

        // Про додаток
        aboutRow = addRow();
        channelRow = addRow();
        updaterRow = addRow();
    }

    @Override
    public void onItemClick(View view, int position, float x, float y) {
        // Оформлення
        if (position == visualsRow) {
            presentFragment(new MiogramVisualsActivity());
        } else if (position == playerEditRow) {
            try {
                AudioPlayerAlert alert = new AudioPlayerAlert(getParentActivity(), getResourceProvider());
                showDialog(alert);
                if (alert.getModernPlayerLayout() != null) {
                    alert.getModernPlayerLayout().post(() -> alert.getModernPlayerLayout().setEditMode(true));
                }
            } catch (Throwable ignore) {}
        } else if (position == badgeStudioRow) {
            long clientUserId = UserConfig.getInstance(currentAccount).getClientUserId();
            app.miogram.bridge.badge.MiogramBadgeBottomSheet.show(getParentActivity(), clientUserId);
        } else if (position == spotifyRow) {
            app.miogram.bridge.presence.MiogramConnectedAppsSheet sheet = new app.miogram.bridge.presence.MiogramConnectedAppsSheet(getParentActivity(), null);
            sheet.show();
        } else if (position == tiktokEcosystemRow) {
            presentFragment(new app.miogram.bridge.ecosystem.AmegramTikTokSettingsActivity());
        }
        // Приватність і чати
        else if (position == chatsRow) {
            presentFragment(new MiogramChatsSettingsActivity());
        } else if (position == privacyRow) {
            presentFragment(new MiogramPrivacySettingsActivity());
        } else if (position == mioMomentsRow) {
            presentFragment(new tw.nekomimi.nekogram.settings.NekoExperimentalSettingsActivity());
        }
        // Штучний інтелект і плагіни
        else if (position == companionRow) {
            presentFragment(new MiogramCompanionActivity());
        } else if (position == aiEngineRow) {
            presentFragment(new MiogramAiSettingsActivity());
        } else if (position == cloudVaultRow) {
            presentFragment(new app.miogram.bridge.cloudvault.MiogramCloudVaultActivity());
        } else if (position == pluginsRow) {
            presentFragment(new app.exteraless.plugins.ui.PluginsActivity());
        } else if (position == userbotHubRow) {
            presentFragment(new MiogramHerokuActivity());
        }
        // Про додаток
        else if (position == aboutRow) {
            presentFragment(new MiogramAboutActivity());
        } else if (position == channelRow) {
            openChannel();
        } else if (position == updaterRow) {
            MiogramUpdater.checkAndShowUpdate(this, true);
        }
    }

    private void openChannel() {
        try {
            MessagesController mc = MessagesController.getInstance(currentAccount);
            mc.getUserNameResolver().resolve(CHANNEL_USERNAME, (peerId) -> {
                if (peerId != null) {
                    mc.openByUserName(CHANNEL_USERNAME, this, 1);
                } else {
                    mc.openByUserName(FALLBACK_CHANNEL_USERNAME, this, 1);
                }
            });
        } catch (Throwable ignore) {
            openUsername(CHANNEL_USERNAME);
        }
    }

    private void openUsername(String username) {
        try {
            MessagesController.getInstance(currentAccount).openByUserName(username, this, 1);
        } catch (Throwable ignore) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/" + username));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getParentActivity().startActivity(intent);
            } catch (Throwable ignored) {}
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
            if (position == headerCustomRow || position == headerExtrasRow || position == headerAboutRow) {
                return TYPE_HEADER;
            }
            return TYPE_TEXT;
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean partial) {
            switch (holder.getItemViewType()) {
                case TYPE_HEADER: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    if (position == headerCustomRow) {
                        cell.setText(MiogramLocale.get("Оформлення", "Оформление", "Appearance"));
                    } else if (position == headerExtrasRow) {
                        cell.setText(MiogramLocale.get("Приватність і чати", "Приватность и чаты", "Privacy & Chats"));
                    } else if (position == headerAboutRow) {
                        cell.setText(MiogramLocale.get("Штучний інтелект і плагіни", "Искусственный интеллект и плагины", "AI & Plugins"));
                    }
                    break;
                }
                case TYPE_TEXT: {
                    TextCell cell = (TextCell) holder.itemView;
                    // Оформлення
                    if (position == visualsRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Зовнішній вигляд", "Внешний вид", "Appearance"),
                                R.drawable.msg_theme,
                                true
                        );
                    } else if (position == playerEditRow) {
                        cell.setTextAndValueAndIcon(
                                MiogramLocale.get("Аудіоплеєр", "Аудиоплеер", "Audio Player"),
                                MiogramLocale.get("Налаштувати", "Настроить", "Customize"),
                                R.drawable.msg_customize,
                                true
                        );
                    } else if (position == badgeStudioRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Стрілочки та бейджі", "Стрелочки и бейджи", "Badges & Arrows"),
                                R.drawable.msg_fave,
                                true
                        );
                    } else if (position == spotifyRow) {
                        boolean linked = app.miogram.bridge.spotify.MiogramSpotifyManager.getInstance().isLinked();
                        cell.setTextAndValueAndIcon(
                                MiogramLocale.get("Музичні сервіси", "Музыкальные сервисы", "Music Services"),
                                linked ? MiogramLocale.get("Підключено", "Подключено", "Connected") : MiogramLocale.get("Вимкнено", "Отключено", "Off"),
                                R.drawable.baseline_music_note_24,
                                false
                        );
                    } else if (position == tiktokEcosystemRow) {
                        boolean installed = app.miogram.bridge.ecosystem.AmegramTikTokBridge.isTikTokMiInstalled(getParentActivity());
                        cell.setTextAndValueAndIcon(
                                MiogramLocale.get("TikTok MI Екосистема", "TikTok MI Экосистема", "TikTok MI Ecosystem"),
                                installed ? MiogramLocale.get("Підключено", "Подключено", "Connected") : MiogramLocale.get("Вимкнено", "Отключено", "Off"),
                                R.drawable.msg_fave,
                                true
                        );
                    }
                    // Приватність і чати
                    else if (position == chatsRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Налаштування чатів", "Настройки чатов", "Chat Settings"),
                                R.drawable.msg_message,
                                true
                        );
                    } else if (position == privacyRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Приватність", "Приватность", "Privacy"),
                                R.drawable.msg_secret,
                                true
                        );
                    } else if (position == mioMomentsRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Історія повідомлень", "История сообщений", "Message History"),
                                R.drawable.msg_delete,
                                false
                        );
                    }
                    // Штучний інтелект і плагіни
                    else if (position == companionRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("ШІ-Супутниця", "ИИ-Спутница", "AI Companion"),
                                R.drawable.baseline_stars_24,
                                true
                        );
                    } else if (position == aiEngineRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Моделі ШІ", "Модели ИИ", "AI Models"),
                                R.drawable.msg_bot,
                                true
                        );
                    } else if (position == cloudVaultRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Хмарне сховище", "Облачное хранилище", "Cloud Storage"),
                                R.drawable.msg_saved,
                                true
                        );
                    } else if (position == pluginsRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Плагіни", "Плагины", "Plugins"),
                                R.drawable.msg_plugins,
                                true
                        );
                    } else if (position == userbotHubRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Автоматизація", "Автоматизация", "Automation"),
                                R.drawable.msg_contacts,
                                false
                        );
                    }
                    // Про додаток
                    else if (position == aboutRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Про Amegram", "Об Amegram", "About Amegram"),
                                R.drawable.msg_info,
                                true
                        );
                    } else if (position == channelRow) {
                        cell.setTextAndValueAndIcon(
                                MiogramLocale.get("Канал спільноти", "Канал сообщества", "Community Channel"),
                                "@" + CHANNEL_USERNAME,
                                R.drawable.msg_channel,
                                true
                        );
                    } else if (position == updaterRow) {
                        String branch = MiogramUpdater.getUpdateChannelName();
                        String ver = "v" + BuildVars.BUILD_VERSION_STRING + " (" + branch + ")";
                        cell.setTextAndValueAndIcon(
                                MiogramLocale.get("Оновлення", "Обновления", "Updates"),
                                ver,
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
