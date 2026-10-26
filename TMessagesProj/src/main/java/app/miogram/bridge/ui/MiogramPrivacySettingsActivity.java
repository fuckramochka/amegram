package app.miogram.bridge.ui;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;

import app.miogram.bridge.MiogramLocale;
import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;
import xyz.nextalone.nagram.NaConfig;

/**
 * Unified Miogram Privacy & Security Settings with dynamic multilingual localization.
 */
public class MiogramPrivacySettingsActivity extends BaseNekoSettingsActivity {

    private int headerVaultRow;
    private int vaultManageRow;
    private int vaultInfoRow;

    private int headerGhostRow;
    private int ghostManageRow;
    private int ghostInfoRow;

    private int headerGeneralPrivacyRow;
    private int hidePhoneRow;
    private int allowScreenshotRow;
    private int generalPrivacyInfoRow;

    private int headerExtraRow;
    private int antiblockRow;
    private int telegramPrivacyRow;
    private int extraInfoRow;

    @Override
    protected String getActionBarTitle() {
        return MiogramLocale.get("Приватність і безпека", "Приватность и безопасность", "Privacy & Security");
    }

    private boolean isGhostOn() {
        try {
            return app.amegram.hot.HotModulesManager.isModuleEnabled("ghost");
        } catch (Throwable ignore) {
            return false;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    protected void updateRows() {
        super.updateRows();

        if (!app.miogram.bridge.vault.MiogramDoubleBottomManager.isDuressActive()) {
            headerVaultRow = addRow();
            vaultManageRow = addRow();
            vaultInfoRow = addRow();
        } else {
            headerVaultRow = -1;
            vaultManageRow = -1;
            vaultInfoRow = -1;
        }

        // Привид + анти-видалення: єдиний вхід у хот-модуль Ghost
        headerGhostRow = addRow();
        ghostManageRow = addRow();
        ghostInfoRow = addRow();

        headerGeneralPrivacyRow = addRow();
        hidePhoneRow = addRow();
        allowScreenshotRow = addRow();
        generalPrivacyInfoRow = addRow();

        headerExtraRow = addRow();
        antiblockRow = addRow();
        telegramPrivacyRow = addRow();
        extraInfoRow = addRow();
    }

    @Override
    public void onItemClick(View view, int position, float x, float y) {
        if (position == vaultManageRow) {
            presentFragment(new app.miogram.bridge.vault.MiogramDoubleBottomActivity());
        } else if (position == ghostManageRow) {
            presentFragment(new app.amegram.hot.ui.HotModuleSettingsActivity("ghost"));
        } else if (position == hidePhoneRow) {
            boolean v = !NekoConfig.hidePhone.Bool();
            NekoConfig.hidePhone.setConfigBool(v);
            if (view instanceof TextCheckCell) ((TextCheckCell) view).setChecked(v);
            getNotificationCenter().postNotificationName(NotificationCenter.mainUserInfoChanged);
        } else if (position == allowScreenshotRow) {
            boolean v = !NekoConfig.ignoreContentRestrictions.Bool();
            NekoConfig.ignoreContentRestrictions.setConfigBool(v);
            if (view instanceof TextCheckCell) ((TextCheckCell) view).setChecked(v);
        } else if (position == antiblockRow) {
            presentFragment(new app.miogram.bridge.bypass.MiogramAntiBlockActivity());
        } else if (position == telegramPrivacyRow) {
            presentFragment(new org.telegram.ui.PrivacySettingsActivity());
        }
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public int getItemViewType(int position) {
            if (position == headerVaultRow || position == headerGhostRow
                    || position == headerGeneralPrivacyRow || position == headerExtraRow) {
                return TYPE_HEADER;
            } else if (position == hidePhoneRow || position == allowScreenshotRow) {
                return TYPE_CHECK;
            } else if (position == vaultInfoRow || position == ghostInfoRow
                    || position == generalPrivacyInfoRow || position == extraInfoRow) {
                return TYPE_INFO_PRIVACY;
            }
            return TYPE_TEXT;
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean partial) {
            switch (holder.getItemViewType()) {
                case TYPE_HEADER: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    if (position == headerVaultRow) {
                        cell.setText(MiogramLocale.get("Подвійне сховище (Double Bottom)", "Двойное дно (Double Bottom)", "Double Bottom (Hidden Accounts)"));
                    } else if (position == headerGhostRow) {
                        cell.setText(MiogramLocale.get("Режим невидимки (Ghost Mode)", "Режим невидимки (Ghost Mode)", "Ghost Mode"));
                    } else if (position == headerGeneralPrivacyRow) {
                        cell.setText(MiogramLocale.get("Загальна конфіденційність", "Общая конфиденциальность", "General Privacy"));
                    } else if (position == headerExtraRow) {
                        cell.setText(MiogramLocale.get("З'єднання та акаунт", "Соединение и аккаунт", "Connection & Account"));
                    }
                    break;
                }
                case TYPE_CHECK: {
                    TextCheckCell cell = (TextCheckCell) holder.itemView;
                    if (position == hidePhoneRow) {
                        cell.setTextAndCheck(MiogramLocale.get("Приховати номер телефону в меню та налаштуваннях", "Скрыть номер телефона в меню и настройках", "Hide phone number in menu & settings"), NekoConfig.hidePhone.Bool(), true, true);
                    } else if (position == allowScreenshotRow) {
                        cell.setTextAndCheck(MiogramLocale.get("Дозволити знімки екрана та копіювання в обмежених чатах", "Разрешить снимки экрана и копирование в ограниченных чатах", "Allow screenshots in protected content chats"), NekoConfig.ignoreContentRestrictions.Bool(), false, true);
                    }
                    break;
                }
                case TYPE_TEXT: {
                    TextCell cell = (TextCell) holder.itemView;
                    if (position == vaultManageRow) {
                        cell.setTextAndIcon(MiogramLocale.get("Налаштування прихованих акаунтів та кодів", "Настройки скрытых аккаунтов и кодов", "Hidden accounts & Passcodes setup"), R.drawable.msg_permissions, false);
                    } else if (position == ghostManageRow) {
                        cell.setTextAndValueAndIcon(
                                MiogramLocale.get("Налаштування привида та анти-видалення", "Настройки призрака и анти-удаление", "Ghost Mode & Anti-Delete Settings"),
                                isGhostOn() ? MiogramLocale.get("Увімкнено", "Включено", "Enabled") : MiogramLocale.get("Вимкнено", "Выключено", "Disabled"),
                                R.drawable.msg_permissions, false);
                    } else if (position == antiblockRow) {
                        cell.setTextAndIcon(MiogramLocale.get("Обхід блокувань (Антиблок)", "Обход блокировок (Антиблок)", "Anti-block bypass"), R.drawable.msg_permissions, true);
                    } else if (position == telegramPrivacyRow) {
                        cell.setTextAndIcon(MiogramLocale.get("Приватність Telegram", "Приватность Telegram", "Telegram privacy"), R.drawable.msg_permissions, false);
                    }
                    break;
                }
                case TYPE_INFO_PRIVACY: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == vaultInfoRow) {
                        cell.setText(MiogramLocale.get("Встановіть різні PIN-коди для кожного акаунта. Прихований акаунт не відображатиметься в інтерфейсі та відкриватиметься лише при введенні його секретного PIN-коду на екрані блокування. Екстрений PIN миттєво видаляє сесії схованих акаунтів.",
                                "Установите разные PIN-коды для каждого аккаунта. Скрытый аккаунт не отображается в интерфейсе и открывается только при вводе его секретного PIN-кода на экране блокировки. Экстренный PIN мгновенно завершает сессии защищённых аккаунтов.",
                                "Configure separate passcodes for each account. Hidden accounts are completely invisible and unlocked strictly via their secret passcode on the lockscreen. Panic code immediately terminates sessions of protected accounts."));
                    } else if (position == ghostInfoRow) {
                        cell.setText(MiogramLocale.get("Керування невидимістю читання, онлайну, статусів набору, історій та локальне збереження видалених повідомлень.",
                                "Управление невидимостью прочтения, онлайна, статусов набора, историй и локальное сохранение удаленных сообщений.",
                                "Stealth reading receipts, online presence, typing indicators, stories, and local retention of deleted messages."));
                    } else if (position == generalPrivacyInfoRow) {
                        cell.setText(MiogramLocale.get("Додатковий захист персональних даних та екрану.",
                                "Дополнительная защита персональных данных и экрана.",
                                "Additional protection for personal identity and screen captures."));
                    } else if (position == extraInfoRow) {
                        cell.setText(MiogramLocale.get("Антиблок керує проксі та з'єднанням. Приватність Telegram відкриває нативні правила видимості акаунта.",
                                "Антиблок управляет прокси и соединением. Приватность Telegram открывает нативные правила видимости аккаунта.",
                                "Anti-block manages proxies and connectivity. Telegram privacy opens the native account visibility rules."));
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
