package app.miogram.bridge.settings;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.R;
import org.telegram.ui.Cells.TextCell;

import app.miogram.bridge.MiogramLocale;
import app.miogram.bridge.ai.companion.MiogramCompanionActivity;
import app.miogram.bridge.ui.MiogramAiSettingsActivity;
import app.miogram.bridge.ui.MiogramChatsSettingsActivity;
import app.miogram.bridge.ui.MiogramPrivacySettingsActivity;
import app.miogram.bridge.ui.MiogramVisualsActivity;
import app.miogram.bridge.userbot.MiogramHerokuActivity;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

/**
 * Slim Main Miogram Settings Hub: sections only, zero switches.
 * Every row opens a dedicated hub screen.
 */
public class MiogramSettingsActivity extends BaseNekoSettingsActivity {

    // 1. Style
    private int headerCustomizationRow;
    private int visualsRow;

    // 2. Chats & Privacy
    private int headerChatsPrivacyRow;
    private int chatsRow;
    private int privacyRow;

    // 3. AI & Companions
    private int headerAiRow;
    private int companionRow;
    private int aiEngineRow;

    // 4. Heroku Userbot & Automation
    private int headerUserbotRow;
    private int userbotHubRow;

    // 5. System
    private int headerSystemRow;
    private int systemRow;

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

        // 1. Style
        headerCustomizationRow = addRow();
        visualsRow = addRow();

        // 2. Chats & Privacy
        headerChatsPrivacyRow = addRow();
        chatsRow = addRow();
        privacyRow = addRow();

        // 3. AI & Companions
        headerAiRow = addRow();
        companionRow = addRow();
        aiEngineRow = addRow();

        // 4. Heroku Userbot & Automation
        headerUserbotRow = addRow();
        userbotHubRow = addRow();

        // 5. System
        headerSystemRow = addRow();
        systemRow = addRow();

        // 6. About
        headerAboutRow = addRow();
        aboutRow = addRow();
        donateRow = addRow();
    }

    @Override
    public void onItemClick(View view, int position, float x, float y) {
        if (position == visualsRow) {
            presentFragment(new MiogramVisualsActivity());
        } else if (position == chatsRow) {
            presentFragment(new MiogramChatsSettingsActivity());
        } else if (position == privacyRow) {
            presentFragment(new MiogramPrivacySettingsActivity());
        } else if (position == companionRow) {
            presentFragment(new MiogramCompanionActivity());
        } else if (position == aiEngineRow) {
            presentFragment(new MiogramAiSettingsActivity());
        } else if (position == userbotHubRow) {
            presentFragment(new MiogramHerokuActivity());
        } else if (position == systemRow) {
            presentFragment(new MiogramSystemActivity());
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
                        cell.setText(MiogramLocale.get("Стиль", "Стиль", "Style"));
                    } else if (position == headerChatsPrivacyRow) {
                        cell.setText(MiogramLocale.get("Чати та приватність", "Чаты и приватность", "Chats & Privacy"));
                    } else if (position == headerAiRow) {
                        cell.setText(MiogramLocale.get("Штучний інтелект та супутники", "Искусственный интеллект и спутники", "AI & Companions"));
                    } else if (position == headerUserbotRow) {
                        cell.setText(MiogramLocale.get("Heroku Юзербот та автоматизація", "Heroku Юзербот и автоматизация", "Heroku Userbot & Automation"));
                    } else if (position == headerSystemRow) {
                        cell.setText(MiogramLocale.get("Система", "Система", "System"));
                    } else if (position == headerAboutRow) {
                        cell.setText(MiogramLocale.get("Про застосунок", "О приложении", "About"));
                    }
                    break;
                }
                case TYPE_TEXT: {
                    TextCell cell = (TextCell) holder.itemView;
                    if (position == visualsRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Зовнішній вигляд і стилі", "Внешний вид и стили", "Appearance & Styles"),
                                R.drawable.msg_theme,
                                true
                        );
                    } else if (position == chatsRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Чати", "Чаты", "Chats"),
                                R.drawable.msg_message,
                                true
                        );
                    } else if (position == privacyRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Приватність і захист", "Приватность и защита", "Privacy & Protection"),
                                R.drawable.msg_secret,
                                false
                        );
                    } else if (position == companionRow) {
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
                    } else if (position == userbotHubRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Userbot та автоматизація", "Юзербот и автоматизация", "Userbot & Automation"),
                                R.drawable.msg_contacts,
                                false
                        );
                    } else if (position == systemRow) {
                        cell.setTextAndIcon(
                                MiogramLocale.get("Система, плагіни та оновлення", "Система, плагины и обновления", "System, Plugins & Updates"),
                                R.drawable.msg_settings,
                                true
                        );
                    } else if (position == aboutRow) {
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
