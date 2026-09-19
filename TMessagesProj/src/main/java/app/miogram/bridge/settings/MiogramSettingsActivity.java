package app.miogram.bridge.settings;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.AudioPlayerAlert;

import app.miogram.bridge.MiogramLocale;
import app.miogram.bridge.ai.companion.MiogramCompanionActivity;
import app.miogram.bridge.badge.MiogramSupabaseBridge;
import app.miogram.bridge.player.MiogramPlayerPrefs;
import app.miogram.bridge.player.MiogramPlayerSectionSheet;
import app.miogram.bridge.plugins.MiogramPluginForgeActivity;
import app.miogram.bridge.ui.MiogramAiSettingsActivity;
import app.miogram.bridge.ui.MiogramChatsSettingsActivity;
import app.miogram.bridge.ui.MiogramPerformanceActivity;
import app.miogram.bridge.ui.MiogramPrivacySettingsActivity;
import app.miogram.bridge.ui.MiogramVisualsActivity;
import app.miogram.bridge.updater.MiogramUpdater;
import app.miogram.bridge.userbot.MiogramHerokuActivity;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.settings.NekoTranslatorSettingsActivity;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

/**
 * Miogram Settings Hub:
 * 6 Clear Categories + Dedicated Bottom Section ("Про Miogram" / "Оновлення").
 * 1. Візуал
 * 2. Функціонал
 * 3. Аудіо
 * 4. Додаткові фішки Miogram
 * 5. ШІ
 * 6. Плагіни
 * + Про Miogram / Підтримати розробку
 */
public class MiogramSettingsActivity extends BaseNekoSettingsActivity {

    // 1. Візуал
    private int headerVisualsRow;
    private int visualsRow;
    private int iconPacksRow;
    private int navigationRow;
    private int badgeStudioRow;

    // 2. Функціонал
    private int headerFunctionalityRow;
    private int chatsRow;
    private int multichatRow;
    private int privacyRow;
    private int subfoldersRow;
    private int translatorRow;
    private int localizerRow;
    private int antiBlockRow;
    private int performanceRow;
    private int pushRow;
    private int telemetryRow;

    // 3. Аудіо
    private int headerAudioRow;
    private int playerEditRow;
    private int modernPlayerRow;
    private int lyricsRow;
    private int bassVisualizerRow;
    private int spotifyRow;

    // 4. Додаткові фішки Miogram
    private int headerExtrasRow;
    private int cloudVaultRow;
    private int connectedAppsRow;
    private int userbotHubRow;
    private int generalRow;

    // 5. ШІ
    private int headerAiRow;
    private int companionRow;
    private int aiEngineRow;
    private int aiPluginForgeRow;

    // 6. Плагіни
    private int headerPluginsRow;
    private int pluginsRow;
    private int miopluginFolderRow;

    // Bottom Section: Про Miogram
    private int headerAboutRow;
    private int updaterRow;
    private int channelRow;
    private int supportRow;

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
        iconPacksRow = addRow();
        navigationRow = addRow();
        badgeStudioRow = addRow();

        // 2. Функціонал
        headerFunctionalityRow = addRow();
        chatsRow = addRow();
        multichatRow = addRow();
        privacyRow = addRow();
        subfoldersRow = addRow();
        translatorRow = addRow();
        localizerRow = addRow();
        antiBlockRow = addRow();
        performanceRow = addRow();
        pushRow = addRow();
        telemetryRow = addRow();

        // 3. Аудіо
        headerAudioRow = addRow();
        playerEditRow = addRow();
        modernPlayerRow = addRow();
        lyricsRow = addRow();
        bassVisualizerRow = addRow();
        spotifyRow = addRow();

        // 4. Додаткові фішки Miogram
        headerExtrasRow = addRow();
        cloudVaultRow = addRow();
        connectedAppsRow = addRow();
        userbotHubRow = addRow();
        generalRow = addRow();

        // 5. ШІ
        headerAiRow = addRow();
        companionRow = addRow();
        aiEngineRow = addRow();
        aiPluginForgeRow = addRow();

        // 6. Плагіни
        headerPluginsRow = addRow();
        pluginsRow = addRow();
        miopluginFolderRow = addRow();

        // Bottom Section: Про Miogram
        headerAboutRow = addRow();
        updaterRow = addRow();
        channelRow = addRow();
        supportRow = addRow();
    }

    @Override
    public void onItemClick(View view, int position, float x, float y) {
        // 1. Візуал
        if (position == visualsRow) {
            presentFragment(new MiogramVisualsActivity());
        } else if (position == iconPacksRow) {
            presentFragment(new app.exteraless.icons.IconPacksActivity());
        } else if (position == navigationRow) {
            presentFragment(new app.exteraless.settings.OpenExteraAppNavigationActivity());
        } else if (position == badgeStudioRow) {
            long clientUserId = UserConfig.getInstance(currentAccount).getClientUserId();
            app.miogram.bridge.badge.MiogramBadgeBottomSheet.show(getParentActivity(), clientUserId);
        }
        // 2. Функціонал
        else if (position == chatsRow) {
            presentFragment(new MiogramChatsSettingsActivity());
        } else if (position == multichatRow) {
            presentFragment(new app.miogram.bridge.multichat.MiogramSplitChatActivity(0, 0));
        } else if (position == privacyRow) {
            presentFragment(new MiogramPrivacySettingsActivity());
        } else if (position == subfoldersRow) {
            presentFragment(new app.miogram.bridge.folders.MiogramSubfolderSettingsActivity());
        } else if (position == translatorRow) {
            presentFragment(new NekoTranslatorSettingsActivity());
        } else if (position == localizerRow) {
            presentFragment(new app.miogram.bridge.localizer.MiogramLocalizerActivity());
        } else if (position == antiBlockRow) {
            presentFragment(new app.miogram.bridge.bypass.MiogramAntiBlockActivity());
        } else if (position == performanceRow) {
            presentFragment(new MiogramPerformanceActivity());
        } else if (position == pushRow) {
            app.miogram.bridge.push.MiogramPushSheet sheet = new app.miogram.bridge.push.MiogramPushSheet(getParentActivity(), null);
            sheet.show();
            if (listView != null && listView.getAdapter() != null) {
                AndroidUtilities.runOnUIThread(() -> listView.getAdapter().notifyDataSetChanged(), 1500);
            }
        } else if (position == telemetryRow) {
            boolean nextState = !MiogramSupabaseBridge.isTelemetryEnabled();
            MiogramSupabaseBridge.setTelemetryEnabled(nextState);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(nextState);
            }
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
        } else if (position == modernPlayerRow) {
            new MiogramPlayerSectionSheet(getParentActivity(), getResourceProvider(), null, "controls").show();
        } else if (position == lyricsRow) {
            new MiogramPlayerSectionSheet(getParentActivity(), getResourceProvider(), null, "lyrics").show();
        } else if (position == bassVisualizerRow) {
            boolean next = !MiogramPlayerPrefs.isVisualizerEnabled();
            MiogramPlayerPrefs.setVisualizerEnabled(next);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(next);
            }
        } else if (position == spotifyRow) {
            app.miogram.bridge.presence.MiogramConnectedAppsSheet sheet = new app.miogram.bridge.presence.MiogramConnectedAppsSheet(getParentActivity(), null);
            sheet.show();
        }
        // 4. Додаткові фішки Miogram
        else if (position == cloudVaultRow) {
            presentFragment(new app.miogram.bridge.cloudvault.MiogramCloudVaultActivity());
        } else if (position == connectedAppsRow) {
            app.miogram.bridge.presence.MiogramConnectedAppsSheet sheet = new app.miogram.bridge.presence.MiogramConnectedAppsSheet(getParentActivity(), null);
            sheet.setOnAppsChangedListener(() -> {
                if (listView != null && listView.getAdapter() != null) {
                    listView.getAdapter().notifyDataSetChanged();
                }
            });
            sheet.show();
        } else if (position == userbotHubRow) {
            presentFragment(new MiogramHerokuActivity());
        } else if (position == generalRow) {
            presentFragment(new app.exteraless.settings.OpenExteraGeneralActivity());
        }
        // 5. ШІ
        else if (position == companionRow) {
            presentFragment(new MiogramCompanionActivity());
        } else if (position == aiEngineRow) {
            presentFragment(new MiogramAiSettingsActivity());
        } else if (position == aiPluginForgeRow) {
            presentFragment(new MiogramPluginForgeActivity());
        }
        // 6. Плагіни
        else if (position == pluginsRow) {
            presentFragment(new app.exteraless.plugins.ui.PluginsActivity());
        } else if (position == miopluginFolderRow) {
            presentFragment(new app.exteraless.plugins.ui.PluginsActivity());
        }
        // Bottom Section: Про Miogram
        else if (position == updaterRow) {
            MiogramUpdater.checkAndShowUpdate(this, true);
        } else if (position == channelRow) {
            Browser.openUrl(getParentActivity(), "https://t.me/miogram_app");
        } else if (position == supportRow) {
            Browser.openUrl(getParentActivity(), "https://t.me/miogram_app");
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
            } else if (position == telemetryRow || position == bassVisualizerRow) {
                return TYPE_CHECK;
            }
            return TYPE_TEXT;
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean partial) {
            switch (holder.getItemViewType()) {
                case TYPE_CHECK: {
                    TextCheckCell cell = (TextCheckCell) holder.itemView;
                    if (position == telemetryRow) {
                        cell.setTextAndCheck(
                                MiogramLocale.get("Хмарна синхронізація та аналітика", "Облачная синхронизация и аналитика", "Cloud Sync & Analytics"),
                                MiogramSupabaseBridge.isTelemetryEnabled(),
                                true
                        );
                    } else if (position == bassVisualizerRow) {
                        cell.setTextAndCheck(
                                MiogramLocale.get("Візуалізатор басу в плеєрі", "Визуалайзер баса в плеере", "Bass visualizer in player"),
                                MiogramPlayerPrefs.isVisualizerEnabled(),
                                true
                        );
                    }
                    break;
                }
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
                    } else if (position == badgeStudioRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Студія бейджів спільноти", "Студия бейджей сообщества", "Community Badge Studio"),
                                R.drawable.msg_fave,
                                false
                        );
                    }
                    // 2. Функціонал
                    else if (position == chatsRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Налаштування чатів (Двійний тап, жести)", "Настройки чатов (Двойной тап, жесты)", "Chat Settings (Double tap, gestures)"),
                                R.drawable.msg_message,
                                true
                        );
                    } else if (position == multichatRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Мультичат (Split Screen)", "Мультичат (Split Screen)", "Multichat (Split Screen)"),
                                R.drawable.msg_openin,
                                true
                        );
                    } else if (position == privacyRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Приватність та Ghost Mode", "Приватность и Ghost Mode", "Privacy & Ghost Mode"),
                                R.drawable.msg_secret,
                                true
                        );
                    } else if (position == subfoldersRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Підпапки чатів", "Подпапки чатов", "Chat Subfolders"),
                                R.drawable.msg_archive,
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
                    } else if (position == performanceRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Продуктивність та оптимізація", "Производительность и оптимизация", "Performance & Optimization"),
                                R.drawable.msg_speed_solar,
                                true
                        );
                    } else if (position == pushRow) {
                        cell.setTextAndValueAndIcon(
                                MiogramLocale.get("Сповіщення та фонова робота", "Уведомления и фоновая работа", "Notifications & Background"),
                                app.miogram.bridge.push.MiogramPushSheet.getShortStatus(),
                                R.drawable.baseline_notifications_24,
                                false
                        );
                    }
                    // 3. Аудіо
                    else if (position == playerEditRow) {
                        cell.setTextAndValueAndIcon(
                                MiogramLocale.get("Редагувати вигляд плеєра", "Редактировать вид плеера", "Edit Player Appearance"),
                                MiogramLocale.get("Режим кастомізації (Jiggle Mode)", "Режим настройки (Jiggle Mode)", "Customization (Jiggle Mode)"),
                                R.drawable.msg_edit,
                                true
                        );
                    } else if (position == modernPlayerRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Сучасний плеєр (Фон, Кнопки)", "Современный плеер (Фон, Кнопки)", "Modern Player (Backdrop, Controls)"),
                                R.drawable.player_new_order,
                                true
                        );
                    } else if (position == lyricsRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Текст пісень (Шрифт, Сяйво, Підказка)", "Текст песни (Шрифт, Сияние, Подсказка)", "Lyrics (Font, Glow, Chat Hint)"),
                                R.drawable.ic_lyrics,
                                true
                        );
                    } else if (position == spotifyRow) {
                        boolean linked = app.miogram.bridge.spotify.MiogramSpotifyManager.getInstance().isLinked();
                        cell.setTextAndValueAndIcon(
                                "Spotify",
                                linked ? MiogramLocale.get("Підключено", "Подключено", "Connected") : MiogramLocale.get("Не підключено", "Не подключено", "Not linked"),
                                R.drawable.msg_openin,
                                false
                        );
                    }
                    // 4. Додаткові фішки Miogram
                    else if (position == cloudVaultRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Miogram Cloud Vault", "Miogram Cloud Vault", "Miogram Cloud Vault"),
                                R.drawable.msg_saved,
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
                                : "Steam, GitHub, Discord, Spotify, Roblox";
                        cell.setTextAndValueAndIcon(
                                MiogramLocale.get("Прив'язані додатки", "Привязанные приложения", "Connected Apps"),
                                val,
                                R.drawable.msg_openin,
                                true
                        );
                    } else if (position == userbotHubRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Heroku Юзербот та автоматизація", "Heroku Юзербот и автоматизация", "Heroku Userbot & Automation"),
                                R.drawable.msg_contacts,
                                true
                        );
                    } else if (position == generalRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Розширені налаштування клієнта", "Расширенные настройки клиента", "Advanced Client Settings"),
                                R.drawable.msg_settings,
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
                                MiogramLocale.get("Налаштування ШІ та Ключі (Gemini)", "Настройки ИИ и Ключи (Gemini)", "AI Engine & Keyring (Gemini)"),
                                R.drawable.msg_bot,
                                true
                        );
                    } else if (position == aiPluginForgeRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Кузня плагінів через ШІ (Plugin Forge)", "Кузница плагинов через ИИ (Plugin Forge)", "AI Plugin Forge"),
                                R.drawable.msg_edit,
                                false
                        );
                    }
                    // 6. Плагіни
                    else if (position == pluginsRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Каталог плагінів та модулів", "Каталог плагинов и модулей", "Plugins & Modules Catalog"),
                                R.drawable.msg_plugins,
                                true
                        );
                    } else if (position == miopluginFolderRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Автоматичний імпорт (.mioplugin)", "Автоматический импорт (.mioplugin)", "Auto-import folder (.mioplugin)"),
                                R.drawable.msg_folders,
                                false
                        );
                    }
                    // Bottom Section: Про Miogram
                    else if (position == updaterRow) {
                        String branch = MiogramUpdater.getUpdateChannelName();
                        String ver = "v" + BuildVars.BUILD_VERSION_STRING + " (" + branch + ")";
                        cell.setTextAndValueAndIcon(
                                MiogramLocale.get("Оновлення Miogram", "Обновления Miogram", "Miogram Updates"),
                                ver,
                                R.drawable.msg_download_solar,
                                true
                        );
                    } else if (position == channelRow) {
                        cell.setTextAndValueAndIcon(
                                MiogramLocale.get("Офіційний канал", "Официальный канал", "Official Channel"),
                                "@miogram_app",
                                R.drawable.msg_channel,
                                true
                        );
                    } else if (position == supportRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Підтримати розробку", "Поддержать разработку", "Support Development"),
                                R.drawable.msg_fave,
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
