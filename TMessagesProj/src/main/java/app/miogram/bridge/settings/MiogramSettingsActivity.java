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

    public static final String CHANNEL_USERNAME = "dkmiogram";

    // Group 1: Кастом, аудіо та функціонал
    private int headerCustomRow;
    private int visualsRow;
    private int playerEditRow;
    private int badgeStudioRow;
    private int spotifyRow;
    private int chatsRow;
    private int privacyRow;

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
        return MiogramLocale.get("Налаштування Miogram", "Настройки Miogram", "Miogram Settings");
    }

    @Override
    protected void updateRows() {
        super.updateRows();

        // Group 1: Кастом, аудіо та функціонал
        headerCustomRow = addRow();
        visualsRow = addRow();
        playerEditRow = addRow();
        badgeStudioRow = addRow();
        spotifyRow = addRow();
        chatsRow = addRow();
        privacyRow = addRow();

        // Group 2: Додаткові фішки, ШІ та плагіни
        headerExtrasRow = addRow();
        cloudVaultRow = addRow();
        companionRow = addRow();
        aiEngineRow = addRow();
        pluginsRow = addRow();
        userbotHubRow = addRow();

        // Group 3: Підтримка проекту, Про Miogram та оновлення
        headerAboutRow = addRow();
        aboutRow = addRow();
        channelRow = addRow();
        updaterRow = addRow();
    }

    @Override
    public void onItemClick(View view, int position, float x, float y) {
        // Group 1: Кастом, аудіо та функціонал
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
        } else if (position == chatsRow) {
            presentFragment(new MiogramChatsSettingsActivity());
        } else if (position == privacyRow) {
            presentFragment(new MiogramPrivacySettingsActivity());
        }
        // Group 2: Додаткові фішки, ШІ та плагіни
        else if (position == cloudVaultRow) {
            presentFragment(new app.miogram.bridge.cloudvault.MiogramCloudVaultActivity());
        } else if (position == companionRow) {
            presentFragment(new MiogramCompanionActivity());
        } else if (position == aiEngineRow) {
            presentFragment(new MiogramAiSettingsActivity());
        } else if (position == pluginsRow) {
            presentFragment(new app.exteraless.plugins.ui.PluginsActivity());
        } else if (position == userbotHubRow) {
            presentFragment(new MiogramHerokuActivity());
        }
        // Group 3: Підтримка проекту, Про Miogram та оновлення
        else if (position == aboutRow) {
            presentFragment(new MiogramAboutActivity());
        } else if (position == channelRow) {
            openUsername(CHANNEL_USERNAME);
        } else if (position == updaterRow) {
            MiogramUpdater.checkAndShowUpdate(this, true);
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
                        cell.setText(MiogramLocale.get("1. Кастом, аудіо та функціонал", "1. Кастом, аудио и функционал", "1. Custom, Audio & Features"));
                    } else if (position == headerExtrasRow) {
                        cell.setText(MiogramLocale.get("2. Додаткові фішки, ШІ та плагіни", "2. Дополнительные фишки, ИИ и плагины", "2. Extras, AI & Plugins"));
                    } else if (position == headerAboutRow) {
                        cell.setText(MiogramLocale.get("3. Підтримка проекту, Про Miogram та оновлення", "3. Поддержка проекта, О Miogram и обновления", "3. Support, About Miogram & Updates"));
                    }
                    break;
                }
                case TYPE_TEXT: {
                    TextCell cell = (TextCell) holder.itemView;
                    // Group 1: Кастом, аудіо та функціонал
                    if (position == visualsRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Зовнішній вигляд та оформлення", "Внешний вид и оформление", "Appearance & Theming"),
                                R.drawable.msg_theme,
                                true
                        );
                    } else if (position == playerEditRow) {
                        cell.setTextAndValueAndIcon(
                                MiogramLocale.get("Кастомізація аудіоплеєра", "Кастомизация аудиоплеера", "Audio Player Customization"),
                                MiogramLocale.get("Режим редагування", "Режим редактирования", "Edit Mode"),
                                R.drawable.msg_customize,
                                true
                        );
                    } else if (position == badgeStudioRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Стрілочки та бейджі спільноти", "Стрелочки и бейджи сообщества", "Community Badges & Arrows"),
                                R.drawable.msg_fave,
                                true
                        );
                    } else if (position == spotifyRow) {
                        boolean linked = app.miogram.bridge.spotify.MiogramSpotifyManager.getInstance().isLinked();
                        cell.setTextAndValueAndIcon(
                                MiogramLocale.get("Музичні сервіси (Spotify / Presence)", "Музыкальные сервисы (Spotify / Presence)", "Music Services (Spotify / Presence)"),
                                linked ? MiogramLocale.get("Підключено", "Подключено", "Connected") : MiogramLocale.get("Не підключено", "Не подключено", "Not linked"),
                                R.drawable.baseline_music_note_24,
                                true
                        );
                    } else if (position == chatsRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Налаштування чатів та перекладач", "Настройки чатов и переводчик", "Chat Settings & Translator"),
                                R.drawable.msg_message,
                                true
                        );
                    } else if (position == privacyRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Приватність та Ghost Mode", "Приватность и Ghost Mode", "Privacy & Ghost Mode"),
                                R.drawable.msg_secret,
                                false
                        );
                    }
                    // Group 2: Додаткові фішки, ШІ та плагіни
                    else if (position == cloudVaultRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Хмарне сховище (Cloud Vault)", "Облачное хранилище (Cloud Vault)", "Cloud Storage (Cloud Vault)"),
                                R.drawable.msg_saved,
                                true
                        );
                    } else if (position == companionRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("ШІ-Супутниця Miogram", "ИИ-Спутница Miogram", "AI Companion"),
                                R.drawable.baseline_stars_24,
                                true
                        );
                    } else if (position == aiEngineRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Налаштування рушія та моделей ШІ", "Настройки движка и моделей ИИ", "AI Engine & Models"),
                                R.drawable.msg_bot,
                                true
                        );
                    } else if (position == pluginsRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Каталог плагінів та модулів (.mioplugin)", "Каталог плагинов и модулей (.mioplugin)", "Plugins & Modules Catalog"),
                                R.drawable.msg_plugins,
                                true
                        );
                    } else if (position == userbotHubRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Userbot та автоматизація (Heroku)", "Юзербот и автоматизация (Heroku)", "Userbot & Automation (Heroku)"),
                                R.drawable.msg_contacts,
                                false
                        );
                    }
                    // Group 3: Підтримка проекту, Про Miogram та оновлення
                    else if (position == aboutRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Про Miogram", "О Miogram", "About Miogram"),
                                R.drawable.msg_info,
                                true
                        );
                    } else if (position == channelRow) {
                        cell.setTextAndValueAndIcon(
                                MiogramLocale.get("Офіційний канал @dkmiogram", "Официальный канал @dkmiogram", "Official Channel @dkmiogram"),
                                "@dkmiogram",
                                R.drawable.msg_channel,
                                true
                        );
                    } else if (position == updaterRow) {
                        String branch = MiogramUpdater.getUpdateChannelName();
                        String ver = "v" + BuildVars.BUILD_VERSION_STRING + " (" + branch + ")";
                        cell.setTextAndValueAndIcon(
                                MiogramLocale.get("Оновлення Miogram", "Обновления Miogram", "Miogram Updates"),
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
