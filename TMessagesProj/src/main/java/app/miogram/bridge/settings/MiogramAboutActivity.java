package app.miogram.bridge.settings;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.Components.RecyclerListView;

import app.miogram.bridge.MiogramLocale;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

/**
 * About Miogram: what it is, who builds it, how old it is, where to help.
 */
public class MiogramAboutActivity extends BaseNekoSettingsActivity {

    public static final String CREATOR_USERNAME = "dkramochka";
    public static final String CHANNEL_USERNAME = "dkmiogram";
    /** Miogram birthday: 1 September 2026. */
    public static final long BIRTHDAY_MS = 1788220800000L;

    private static final int TYPE_CREATOR = 100;

    private int headerCreatorRow;
    private int creatorRow;
    private int creatorInfoRow;

    private int headerAboutRow;
    private int aboutInfoRow;

    private int headerLinksRow;
    private int channelRow;
    private int donateRow;

    private TLRPC.User creatorUser;

    @Override
    protected String getActionBarTitle() {
        return MiogramLocale.get("Про Miogram", "О Miogram", "About Miogram");
    }

    @Override
    public boolean onFragmentCreate() {
        resolveCreator();
        return super.onFragmentCreate();
    }

    @Override
    protected void updateRows() {
        super.updateRows();

        headerCreatorRow = addRow();
        creatorRow = addRow();
        creatorInfoRow = addRow();

        headerAboutRow = addRow();
        aboutInfoRow = addRow();

        headerLinksRow = addRow();
        channelRow = addRow();
        donateRow = addRow();
    }

    @Override
    public void onItemClick(View view, int position, float x, float y) {
        if (position == creatorRow) {
            openUsername(CREATOR_USERNAME);
        } else if (position == channelRow) {
            openUsername(CHANNEL_USERNAME);
        } else if (position == donateRow) {
            Toast.makeText(getParentActivity() != null ? getParentActivity() : getContext(),
                    MiogramLocale.get("Підтримка розробки тимчасово недоступна", "Поддержка разработки временно недоступна", "Donations are temporarily unavailable"),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void openUsername(String username) {
        try {
            MessagesController.getInstance(currentAccount).openByUserName(username, this, 1);
        } catch (Throwable ignore) {
            try {
                android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://t.me/" + username));
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                getParentActivity().startActivity(intent);
            } catch (Throwable ignored) {}
        }
    }

    private void resolveCreator() {
        try {
            Object cached = MessagesController.getInstance(currentAccount).getUserOrChat(CREATOR_USERNAME);
            if (cached instanceof TLRPC.User) {
                creatorUser = (TLRPC.User) cached;
                refreshCreatorRow();
                return;
            }
        } catch (Throwable ignore) {}
        try {
            TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
            req.username = CREATOR_USERNAME;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                try {
                    if (response instanceof TLRPC.TL_contacts_resolvedPeer) {
                        TLRPC.TL_contacts_resolvedPeer res = (TLRPC.TL_contacts_resolvedPeer) response;
                        MessagesController mc = MessagesController.getInstance(currentAccount);
                        mc.putUsers(res.users, false);
                        mc.putChats(res.chats, false);
                        if (res.peer instanceof TLRPC.TL_peerUser) {
                            TLRPC.User u = mc.getUser(((TLRPC.TL_peerUser) res.peer).user_id);
                            if (u != null) {
                                creatorUser = u;
                                refreshCreatorRow();
                            }
                        }
                    }
                } catch (Throwable ignore) {}
            }));
        } catch (Throwable ignore) {}
    }

    private void refreshCreatorRow() {
        try {
            if (listView != null && listView.getAdapter() != null) {
                listView.getAdapter().notifyItemChanged(creatorRow);
            }
        } catch (Throwable ignore) {}
    }

    private String creatorStatus() {
        return MiogramLocale.get("Засновник і розробник Miogram", "Основатель и разработчик Miogram", "Miogram founder & developer");
    }

    private static String ageText() {
        long days = Math.max(0, (System.currentTimeMillis() - BIRTHDAY_MS) / 86400000L);
        String lang = MiogramLocale.get("uk", "ru", "en");
        if ("en".equals(lang)) {
            return days + (days == 1 ? " day" : " days");
        }
        long d10 = days % 10;
        long d100 = days % 100;
        String form;
        if (d10 == 1 && d100 != 11) {
            form = MiogramLocale.get("день", "день", "day");
        } else if (d10 >= 2 && d10 <= 4 && (d100 < 12 || d100 > 14)) {
            form = MiogramLocale.get("дні", "дня", "days");
        } else {
            form = MiogramLocale.get("днів", "дней", "days");
        }
        return days + " " + form;
    }

    private String aboutText() {
        return MiogramLocale.get(
                "Miogram — кастомний Telegram-клієнт: хмарне сховище, плагіни, юзербот, присутність (Steam, Spotify, Discord, GitHub), ШІ-супутниці Аме та KAngel, теми і обхід блокувань.\n\nРозробка: @dkramochka, відкрито і по живому — новини, баги та ідеї летять у @dkmiogram.\n\nНародився 1 вересня 2026 — живе вже " + ageText() + ".",
                "Miogram — кастомный Telegram-клиент: облачное хранилище, плагины, юзербот, присутствие (Steam, Spotify, Discord, GitHub), ИИ-спутницы Аме и KAngel, темы и обход блокировок.\n\nРазработка: @dkramochka, открыто и вживую — новости, баги и идеи летят в @dkmiogram.\n\nРодился 1 сентября 2026 — живёт уже " + ageText() + ".",
                "Miogram is a custom Telegram client: cloud vault, plugins, userbot, presence (Steam, Spotify, Discord, GitHub), AI companions Ame & KAngel, themes and anti-block.\n\nBuilt by @dkramochka in the open — news, bugs and ideas live in @dkmiogram.\n\nBorn September 1, 2026 — alive for " + ageText() + "."
        );
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return super.isEnabled(holder) || holder.getItemViewType() == TYPE_CREATOR;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == headerCreatorRow || position == headerAboutRow || position == headerLinksRow) {
                return TYPE_HEADER;
            } else if (position == creatorRow) {
                return TYPE_CREATOR;
            } else if (position == creatorInfoRow || position == aboutInfoRow) {
                return TYPE_INFO_PRIVACY;
            }
            return TYPE_TEXT;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            if (viewType == TYPE_CREATOR) {
                UserCell cell = new UserCell(mContext, 1, 0, false);
                cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                cell.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
                return new RecyclerListView.Holder(cell);
            }
            return super.onCreateViewHolder(parent, viewType);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean partial) {
            switch (holder.getItemViewType()) {
                case TYPE_HEADER: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    if (position == headerCreatorRow) {
                        cell.setText(MiogramLocale.get("Творець", "Создатель", "Creator"));
                    } else if (position == headerAboutRow) {
                        cell.setText(MiogramLocale.get("Що таке Miogram", "Что такое Miogram", "What is Miogram"));
                    } else if (position == headerLinksRow) {
                        cell.setText(MiogramLocale.get("Посилання та підтримка", "Ссылки и поддержка", "Links & Support"));
                    }
                    break;
                }
                case TYPE_CREATOR: {
                    UserCell cell = (UserCell) holder.itemView;
                    if (creatorUser != null) {
                        cell.setData(creatorUser, UserObject.getUserName(creatorUser), "@" + CREATOR_USERNAME, 0);
                    } else {
                        cell.setData(null, "@" + CREATOR_USERNAME, creatorStatus(), R.drawable.msg_contact);
                    }
                    break;
                }
                case TYPE_INFO_PRIVACY: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == creatorInfoRow) {
                        cell.setText(creatorStatus() + ". " + MiogramLocale.get("Натисни, щоб відкрити профіль.", "Нажми, чтобы открыть профиль.", "Tap to open the profile."));
                    } else if (position == aboutInfoRow) {
                        cell.setText(aboutText());
                    }
                    break;
                }
                case TYPE_TEXT: {
                    TextCell cell = (TextCell) holder.itemView;
                    if (position == channelRow) {
                        cell.setTextAndIcon("@dkmiogram — " + MiogramLocale.get("канал новин і багів", "канал новостей и багов", "news & bugs channel"), R.drawable.msg_channel, true);
                    } else if (position == donateRow) {
                        cell.setTextAndIcon(MiogramLocale.get("Підтримати розробку", "Поддержать разработку", "Support development"), R.drawable.msg_gift_premium, false);
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
