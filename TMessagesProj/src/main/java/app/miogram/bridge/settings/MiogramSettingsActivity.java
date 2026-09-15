package app.miogram.bridge.settings;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextSettingsCell;

import app.miogram.bridge.MiogramLocale;
import app.miogram.bridge.ai.companion.MiogramCompanionActivity;
import app.miogram.bridge.ai.companion.MiogramCompanionPrefs;
import app.miogram.bridge.badge.MiogramSupabaseBridge;
import app.miogram.bridge.ui.MiogramAiSettingsActivity;
import app.miogram.bridge.ui.MiogramChatsSettingsActivity;
import app.miogram.bridge.ui.MiogramPerformanceActivity;
import app.miogram.bridge.ui.MiogramPrivacySettingsActivity;
import app.miogram.bridge.ui.MiogramVisualsActivity;
import app.miogram.bridge.updater.MiogramUpdater;
import app.miogram.bridge.userbot.MiogramHerokuActivity;
import app.miogram.bridge.userbot.MiogramHerokuManager;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.settings.NekoTranslatorSettingsActivity;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

/**
 * Clean, Streamlined Main Miogram Settings Hub.
 * Beautifully organized into 5 structured sections with dynamic status subtitles.
 */
public class MiogramSettingsActivity extends BaseNekoSettingsActivity {

    // 1. Aesthetics & Atmosphere
    private int headerCustomizationRow;
    private int visualsRow;
    private int iconPacksRow;
    private int navigationRow;
    private int subfoldersRow;
    private int badgeStudioRow;
    private int connectedAppsRow;

    // 2. Chats, Multichat & Private Vault
    private int headerChatsPrivacyRow;
    private int multichatRow;
    private int chatsRow;
    private int cloudVaultRow;
    private int privacyRow;
    private int antiBlockRow;
    private int translatorRow;
    private int localizerRow;

    // 3. AI & Companions
    private int headerAiRow;
    private int companionRow;
    private int aiEngineRow;

    // 4. Heroku Userbot & Automation
    private int headerUserbotRow;
    private int userbotHubRow;

    // 5. System, Plugins & Updates
    private int headerSystemRow;
    private int pluginsRow;
    private int performanceRow;
    private int pushRow;
    private int generalRow;
    private int updaterRow;

    // 6. About
    private int headerAboutRow;
    private int aboutRow;
    private int donateRow;

    @Override
    protected String getActionBarTitle() {
        return MiogramLocale.get("Налаштування Miogram", "Настройки Miogram", "Miogram Settings");
    }

    @Override
    protected void updateRows() {
        super.updateRows();

        // 1. Aesthetics & Atmosphere
        headerCustomizationRow = addRow();
        visualsRow = addRow();
        iconPacksRow = addRow();
        navigationRow = addRow();
        subfoldersRow = addRow();
        badgeStudioRow = addRow();
        connectedAppsRow = addRow();

        // 2. Chats, Multichat & Private Vault
        headerChatsPrivacyRow = addRow();
        multichatRow = addRow();
        chatsRow = addRow();
        cloudVaultRow = addRow();
        privacyRow = addRow();
        antiBlockRow = addRow();
        translatorRow = addRow();
        localizerRow = addRow();

        // 3. AI & Companions
        headerAiRow = addRow();
        companionRow = addRow();
        aiEngineRow = addRow();

        // 4. Heroku Userbot & Automation
        headerUserbotRow = addRow();
        userbotHubRow = addRow();

        // 5. System, Plugins & Updates
        headerSystemRow = addRow();
        pluginsRow = addRow();
        performanceRow = addRow();
        pushRow = addRow();
        generalRow = addRow();
        updaterRow = addRow();

        // 6. About
        headerAboutRow = addRow();
        aboutRow = addRow();
        donateRow = addRow();
    }

    @Override
    public void onItemClick(View view, int position, float x, float y) {
        if (position == visualsRow) {
            presentFragment(new MiogramVisualsActivity());
        } else if (position == iconPacksRow) {
            presentFragment(new app.exteraless.icons.IconPacksActivity());
        } else if (position == navigationRow) {
            presentFragment(new app.exteraless.settings.OpenExteraAppNavigationActivity());
        } else if (position == subfoldersRow) {
            presentFragment(new app.miogram.bridge.folders.MiogramSubfolderSettingsActivity());
        } else if (position == badgeStudioRow) {
            long clientUserId = UserConfig.getInstance(currentAccount).getClientUserId();
            app.miogram.bridge.badge.MiogramBadgeBottomSheet.show(getParentActivity(), clientUserId);
        } else if (position == connectedAppsRow) {
            app.miogram.bridge.presence.MiogramConnectedAppsSheet sheet = new app.miogram.bridge.presence.MiogramConnectedAppsSheet(getParentActivity(), null);
            sheet.setOnAppsChangedListener(() -> {
                if (listView != null && listView.getAdapter() != null) {
                    listView.getAdapter().notifyDataSetChanged();
                }
            });
            sheet.show();
        } else if (position == multichatRow) {
            presentFragment(new app.miogram.bridge.multichat.MiogramSplitChatActivity(0, 0));
        } else if (position == chatsRow) {
            presentFragment(new MiogramChatsSettingsActivity());
        } else if (position == cloudVaultRow) {
            presentFragment(new app.miogram.bridge.cloudvault.MiogramCloudVaultActivity());
        } else if (position == privacyRow) {
            presentFragment(new MiogramPrivacySettingsActivity());
        } else if (position == antiBlockRow) {
            presentFragment(new app.miogram.bridge.bypass.MiogramAntiBlockActivity());
        } else if (position == translatorRow) {
            presentFragment(new NekoTranslatorSettingsActivity());
        } else if (position == localizerRow) {
            presentFragment(new app.miogram.bridge.localizer.MiogramLocalizerActivity());
        } else if (position == companionRow) {
            presentFragment(new MiogramCompanionActivity());
        } else if (position == aiEngineRow) {
            presentFragment(new MiogramAiSettingsActivity());
        } else if (position == userbotHubRow) {
            presentFragment(new MiogramHerokuActivity());
        } else if (position == pluginsRow) {
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
            MiogramUpdater.checkAndShowUpdate(this, true);
        } else if (position == aboutRow) {
            presentFragment(new MiogramAboutActivity());
        } else if (position == donateRow) {
            android.widget.Toast.makeText(getParentActivity() != null ? getParentActivity() : getContext(),
                    MiogramLocale.get("Підтримка розробки тимчасово недоступна", "Поддержка разработки временно недоступна", "Donations are temporarily unavailable"),
                    android.widget.Toast.LENGTH_SHORT).show();
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
            if (position == headerCustomizationRow || position == headerChatsPrivacyRow ||
                    position == headerAiRow || position == headerUserbotRow || position == headerSystemRow || position == headerAboutRow) {
                return TYPE_HEADER;
            }
            return TYPE_TEXT;
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean partial) {
            switch (holder.getItemViewType()) {
                case TYPE_HEADER: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    if (position == headerCustomizationRow) {
                        cell.setText(MiogramLocale.get("Кастомізація та дизайн", "Кастомизация и дизайн", "Customization & Design"));
                    } else if (position == headerChatsPrivacyRow) {
                        cell.setText(MiogramLocale.get("Чати, мультичат та сховище", "Чаты, мультичат и хранилище", "Chats, Multichat & Vault"));
                    } else if (position == headerAiRow) {
                        cell.setText(MiogramLocale.get("Штучний інтелект та супутники", "Искусственный интеллект и спутники", "AI & Companions"));
                    } else if (position == headerUserbotRow) {
                        cell.setText(MiogramLocale.get("Heroku Юзербот та автоматизація", "Heroku Юзербот и автоматизация", "Heroku Userbot & Automation"));
                    } else if (position == headerSystemRow) {
                        cell.setText(MiogramLocale.get("Система, плагіни та екосистема", "Система, плагины и экосистема", "System, Plugins & Ecosystem"));
                    } else if (position == headerAboutRow) {
                        cell.setText(MiogramLocale.get("Про застосунок", "О приложении", "About"));
                    }
                    break;
                }
                case TYPE_TEXT: {
                    TextCell cell = (TextCell) holder.itemView;
                    // Section 1
                    if (position == visualsRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Візуальні стилі та пресети", "Визуальные стили и пресеты", "Visual Styles & Presets"),
                                R.drawable.msg_theme,
                                true
                        );
                    } else if (position == iconPacksRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Паки іконок", "Паки иконок", "Icon Packs"),
                                R.drawable.msg_customize,
                                true
                        );
                    } else if (position == navigationRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Навігація та панель вкладок", "Навигация и панель вкладок", "Navigation & Tab Bar"),
                                R.drawable.msg_folders,
                                true
                        );
                    } else if (position == subfoldersRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Підпапки чатів", "Подпапки чатов", "Chat Subfolders"),
                                R.drawable.msg_archive,
                                true
                        );
                    } else if (position == badgeStudioRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Студія бейджів спільноти", "Студия бейджей сообщества", "Community Badge Studio"),
                                R.drawable.msg_fave,
                                true
                        );
                    } else if (position == connectedAppsRow) {
                        int count = 0;
                        if (app.miogram.bridge.steam.MiogramSteamManager.getInstance().isLinked()) count++;
                        if (app.miogram.bridge.github.MiogramGitHubManager.getInstance().isLinked()) count++;
                        if (app.miogram.bridge.discord.MiogramDiscordManager.getInstance().isLinked()) count++;
                        if (app.miogram.bridge.spotify.MiogramSpotifyManager.getInstance().isLinked()) count++;
                        if (app.miogram.bridge.roblox.MiogramRobloxManager.getInstance().isLinked()) count++;
                        String val = count > 0
                                ? (count + " " + MiogramLocale.get("підключено", "подключено", "linked"))
                                : MiogramLocale.get("Steam, GitHub, Discord, Spotify, Roblox", "Steam, GitHub, Discord, Spotify, Roblox", "Steam, GitHub, Discord, Spotify, Roblox");
                        cell.setTextAndValueAndIcon(
                                MiogramLocale.get("Прив'язані додатки", "Привязанные приложения", "Connected Apps"),
                                val,
                                R.drawable.msg_openin,
                                false
                        );
                    }
                    // Section 2
                    else if (position == multichatRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Мультичат (Split Screen)", "Мультичат (Split Screen)", "Multichat (Split Screen)"),
                                R.drawable.msg_openin,
                                true
                        );
                    } else if (position == chatsRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Налаштування чатів", "Настройки чатов", "Chat Settings"),
                                R.drawable.msg_message,
                                true
                        );
                    } else if (position == cloudVaultRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Miogram Cloud Vault", "Miogram Cloud Vault", "Miogram Cloud Vault"),
                                R.drawable.msg_saved,
                                true
                        );
                    } else if (position == privacyRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Приватність та Ghost Mode", "Приватность и Ghost Mode", "Privacy & Ghost Mode"),
                                R.drawable.msg_secret,
                                true
                        );
                    } else if (position == antiBlockRow) {
                        boolean active = app.miogram.bridge.bypass.MiogramAntiBlockEngine.getInstance().isBypassActive();
                        boolean auto = app.miogram.bridge.bypass.MiogramAntiBlockEngine.getInstance().isAutoBypassEnabled();
                        String val = active
                                ? MiogramLocale.get("Захищено (Fake-TLS)", "Защищено (Fake-TLS)", "Protected (Fake-TLS)")
                                : (auto
                                        ? MiogramLocale.get("Розумний авто-обхід", "Умный авто-обход", "Smart Auto-Bypass")
                                        : MiogramLocale.get("Вимкнено (пряме)", "Отключено (прямое)", "Disabled (direct)"));
                        cell.setTextAndValueAndIcon(
                                MiogramLocale.get("Обхід блокувань (Анти-ТСПУ)", "Обход блокировок (Анти-ТСПУ)", "Anti-Censorship & Bypass"),
                                val,
                                R.drawable.msg_bot,
                                true
                        );
                    } else if (position == translatorRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Вбудований перекладач", "Встроенный переводчик", "Built-in Translator"),
                                R.drawable.msg2_language,
                                true
                        );
                    } else if (position == localizerRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Локалізатор застосунку", "Локализатор приложения", "App Localizer"),
                                R.drawable.msg_language,
                                false
                        );
                    }
                    // Section 3
                    else if (position == companionRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("ШІ Супутник", "ИИ Спутник", "AI Companion"),
                                R.drawable.baseline_stars_24,
                                true
                        );
                    } else if (position == aiEngineRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Налаштування ШІ та Ключі", "Настройки ИИ и Ключи", "AI Engine & Keyring"),
                                R.drawable.msg_bot,
                                false
                        );
                    }
                    // Section 4
                    else if (position == userbotHubRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Userbot та автоматизація", "Юзербот и автоматизация", "Userbot & Automation"),
                                R.drawable.msg_contacts,
                                false
                        );
                    }
                    // Section 5
                    else if (position == pluginsRow) {
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
                                true
                        );
                    }
                    // Section 6
                    else if (position == aboutRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Про Miogram", "О Miogram", "About Miogram"),
                                R.drawable.msg_info,
                                true
                        );
                    } else if (position == donateRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Підтримати розробку", "Поддержать разработку", "Support development"),
                                R.drawable.msg_gift_premium,
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
