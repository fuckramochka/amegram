package app.miogram.bridge.settings;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.BuildVars;
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
 * 7 Clear Categories with 1-2 focused buttons each opening dedicated screens.
 * 1. Візуал (Visuals & Badges)
 * 2. Функціонал (Chats & Privacy)
 * 3. Аудіо (Player & Connected Music)
 * 4. Додаткові фішки Miogram (Cloud Vault & Userbot)
 * 5. ШІ (AI Companion & Engine)
 * 6. Плагіни (Plugins)
 * 7. Про Miogram (About & Updates)
 */
public class MiogramSettingsActivity extends BaseNekoSettingsActivity {

    // 1. Візуал
    private int headerVisualsRow;
    private int visualsRow;
    private int badgeStudioRow;

    // 2. Функціонал
    private int headerFunctionalityRow;
    private int chatsRow;
    private int privacyRow;

    // 3. Аудіо
    private int headerAudioRow;
    private int playerEditRow;
    private int spotifyRow;

    // 4. Додаткові фішки Miogram
    private int headerExtrasRow;
    private int cloudVaultRow;
    private int userbotHubRow;

    // 5. ШІ
    private int headerAiRow;
    private int companionRow;
    private int aiEngineRow;

    // 6. Плагіни
    private int headerPluginsRow;
    private int pluginsRow;

    // 7. Про Miogram
    private int headerAboutRow;
    private int aboutRow;
    private int updaterRow;

    @Override
    protected String getActionBarTitle() {
        return MiogramLocale.get("Налаштування Miogram", "Настройки Miogram", "Miogram Settings");
    }

    @Override
    protected void updateRows() {
        super.updateRows();

        // 1. Візуал
        headerVisualsRow = addRow();
        visualsRow = addRow();
        badgeStudioRow = addRow();

        // 2. Функціонал
        headerFunctionalityRow = addRow();
        chatsRow = addRow();
        privacyRow = addRow();

        // 3. Аудіо
        headerAudioRow = addRow();
        playerEditRow = addRow();
        spotifyRow = addRow();

        // 4. Додаткові фішки Miogram
        headerExtrasRow = addRow();
        cloudVaultRow = addRow();
        userbotHubRow = addRow();

        // 5. ШІ
        headerAiRow = addRow();
        companionRow = addRow();
        aiEngineRow = addRow();

        // 6. Плагіни
        headerPluginsRow = addRow();
        pluginsRow = addRow();

        // 7. Про Miogram
        headerAboutRow = addRow();
        aboutRow = addRow();
        updaterRow = addRow();
    }

    @Override
    public void onItemClick(View view, int position, float x, float y) {
        // 1. Візуал
        if (position == visualsRow) {
            presentFragment(new MiogramVisualsActivity());
        } else if (position == badgeStudioRow) {
            long clientUserId = UserConfig.getInstance(currentAccount).getClientUserId();
            app.miogram.bridge.badge.MiogramBadgeBottomSheet.show(getParentActivity(), clientUserId);
        }
        // 2. Функціонал
        else if (position == chatsRow) {
            presentFragment(new MiogramChatsSettingsActivity());
        } else if (position == privacyRow) {
            presentFragment(new MiogramPrivacySettingsActivity());
        }
        // 3. Аудіо
        else if (position == playerEditRow) {
            try {
                AudioPlayerAlert alert = new AudioPlayerAlert(getParentActivity(), getResourceProvider());
                showDialog(alert);
                if (alert.getModernPlayerLayout() != null) {
                    alert.getModernPlayerLayout().post(() -> alert.getModernPlayerLayout().setEditMode(true));
                }
            } catch (Throwable ignore) {}
        } else if (position == spotifyRow) {
            app.miogram.bridge.presence.MiogramConnectedAppsSheet sheet = new app.miogram.bridge.presence.MiogramConnectedAppsSheet(getParentActivity(), null);
            sheet.show();
        }
        // 4. Додаткові фішки Miogram
        else if (position == cloudVaultRow) {
            presentFragment(new app.miogram.bridge.cloudvault.MiogramCloudVaultActivity());
        } else if (position == userbotHubRow) {
            presentFragment(new MiogramHerokuActivity());
        }
        // 5. ШІ
        else if (position == companionRow) {
            presentFragment(new MiogramCompanionActivity());
        } else if (position == aiEngineRow) {
            presentFragment(new MiogramAiSettingsActivity());
        }
        // 6. Плагіни
        else if (position == pluginsRow) {
            presentFragment(new app.exteraless.plugins.ui.PluginsActivity());
        }
        // 7. Про Miogram
        else if (position == aboutRow) {
            presentFragment(new MiogramAboutActivity());
        } else if (position == updaterRow) {
            MiogramUpdater.checkAndShowUpdate(this, true);
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
            if (position == headerVisualsRow || position == headerFunctionalityRow ||
                    position == headerAudioRow || position == headerExtrasRow ||
                    position == headerAiRow || position == headerPluginsRow ||
                    position == headerAboutRow) {
                return TYPE_HEADER;
            }
            return TYPE_TEXT;
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean partial) {
            switch (holder.getItemViewType()) {
                case TYPE_HEADER: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    if (position == headerVisualsRow) {
                        cell.setText(MiogramLocale.get("1. Візуал", "1. Визуал", "1. Visuals"));
                    } else if (position == headerFunctionalityRow) {
                        cell.setText(MiogramLocale.get("2. Функціонал", "2. Функционал", "2. Functionality"));
                    } else if (position == headerAudioRow) {
                        cell.setText(MiogramLocale.get("3. Аудіо", "3. Аудио", "3. Audio"));
                    } else if (position == headerExtrasRow) {
                        cell.setText(MiogramLocale.get("4. Додаткові фішки Miogram", "4. Дополнительные фишки Miogram", "4. Miogram Extras"));
                    } else if (position == headerAiRow) {
                        cell.setText(MiogramLocale.get("5. ШІ", "5. ИИ", "5. AI"));
                    } else if (position == headerPluginsRow) {
                        cell.setText(MiogramLocale.get("6. Плагіни", "6. Плагины", "6. Plugins"));
                    } else if (position == headerAboutRow) {
                        cell.setText(MiogramLocale.get("Про Miogram", "О Miogram", "About Miogram"));
                    }
                    break;
                }
                case TYPE_TEXT: {
                    TextCell cell = (TextCell) holder.itemView;
                    // 1. Візуал
                    if (position == visualsRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Зовнішній вигляд та оформлення", "Внешний вид и оформление", "Appearance & Theming"),
                                R.drawable.msg_theme,
                                true
                        );
                    } else if (position == badgeStudioRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Стрілочки та бейджі спільноти", "Стрелочки и бейджи сообщества", "Community Badges & Arrows"),
                                R.drawable.msg_fave,
                                false
                        );
                    }
                    // 2. Функціонал
                    else if (position == chatsRow) {
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
                    // 3. Аудіо
                    else if (position == playerEditRow) {
                        cell.setTextAndValueAndIcon(
                                MiogramLocale.get("Кастомізація плеєра", "Кастомизация плеера", "Player Customization"),
                                MiogramLocale.get("Режим редагування", "Режим редактирования", "Edit Mode"),
                                R.drawable.msg_customize,
                                true
                        );
                    } else if (position == spotifyRow) {
                        boolean linked = app.miogram.bridge.spotify.MiogramSpotifyManager.getInstance().isLinked();
                        cell.setTextAndValueAndIcon(
                                MiogramLocale.get("Музичні сервіси (Spotify / Presence)", "Музыкальные сервисы (Spotify / Presence)", "Music Services (Spotify / Presence)"),
                                linked ? MiogramLocale.get("Підключено", "Подключено", "Connected") : MiogramLocale.get("Не підключено", "Не подключено", "Not linked"),
                                R.drawable.baseline_music_note_24,
                                false
                        );
                    }
                    // 4. Додаткові фішки Miogram
                    else if (position == cloudVaultRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Хмарне сховище (Cloud Vault)", "Облачное хранилище (Cloud Vault)", "Cloud Storage (Cloud Vault)"),
                                R.drawable.msg_saved,
                                true
                        );
                    } else if (position == userbotHubRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Userbot та автоматизація", "Юзербот и автоматизация", "Userbot & Automation"),
                                R.drawable.msg_contacts,
                                false
                        );
                    }
                    // 5. ШІ
                    else if (position == companionRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("ШІ-Супутниця Miogram", "ИИ-Спутница Miogram", "AI Companion"),
                                R.drawable.baseline_stars_24,
                                true
                        );
                    } else if (position == aiEngineRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Налаштування рушія та моделей ШІ", "Настройки движка и моделей ИИ", "AI Engine & Models"),
                                R.drawable.msg_bot,
                                false
                        );
                    }
                    // 6. Плагіни
                    else if (position == pluginsRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Каталог плагінів та модулів (.mioplugin)", "Каталог плагинов и модулей (.mioplugin)", "Plugins & Modules Catalog"),
                                R.drawable.msg_plugins,
                                false
                        );
                    }
                    // 7. Про Miogram
                    else if (position == aboutRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Про Miogram", "О Miogram", "About Miogram"),
                                R.drawable.msg_info,
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
