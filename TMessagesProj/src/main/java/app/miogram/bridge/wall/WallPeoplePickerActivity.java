package app.miogram.bridge.wall;

import android.content.Context;
import android.graphics.Canvas;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;

import org.telegram.messenger.AndroidUtilities;
import app.miogram.bridge.MiogramLocale;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * Мультивибір людей для режиму «Обрані люди» у правах на стіну.
 * Пошук за ім'ям/юзернеймом, чекбокси, кнопка «Готово».
 */
public class WallPeoplePickerActivity extends BaseFragment {

    public interface Delegate {
        void onSelected(List<Long> selectedIds);
    }

    private final List<Long> preselected;
    private final Delegate delegate;
    private final HashMap<Long, TLRPC.User> usersById = new HashMap<>();
    private final List<TLRPC.User> allUsers = new ArrayList<>();
    private final List<TLRPC.User> filteredUsers = new ArrayList<>();
    private final List<Long> selected = new ArrayList<>();

    private RecyclerListView listView;
    private PickerAdapter adapter;
    private EditText searchField;

    public WallPeoplePickerActivity(List<Long> preselected, Delegate delegate) {
        if (preselected != null) {
            this.preselected = new ArrayList<>(preselected);
        } else {
            this.preselected = new ArrayList<>();
        }
        this.delegate = delegate;
    }

    @Override
    public View createView(Context context) {
        hasOwnBackground = true;

        ActionBar actionBar = this.actionBar;
        actionBar.setTitle(MiogramLocale.get("Обрати людей", "Выбрать людей", "Select people"));
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == 1) {
                    if (delegate != null) {
                        delegate.onSelected(new ArrayList<>(selected));
                    }
                    finishFragment();
                }
            }
        });
        ActionBarMenu menu = actionBar.createMenu();
        ActionBarMenuItem done = menu.addItem(1, MiogramLocale.get("Готово", "Готово", "Done"));

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        LinearLayout searchWrap = new LinearLayout(context);
        searchWrap.setOrientation(LinearLayout.HORIZONTAL);
        searchWrap.setGravity(android.view.Gravity.CENTER_VERTICAL);
        searchWrap.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(6), AndroidUtilities.dp(12), AndroidUtilities.dp(6));
        TextView magnifier = new TextView(context);
        magnifier.setText("🔍");
        magnifier.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 14);
        searchWrap.addView(magnifier, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0));
        searchField = new EditText(context);
        searchField.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 14);
        searchField.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        searchField.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        searchField.setHint(MiogramLocale.get("Пошук", "Поиск", "Search"));
        searchField.setBackground(null);
        searchField.setSingleLine(true);
        searchField.setPadding(0, AndroidUtilities.dp(9), 0, AndroidUtilities.dp(9));
        searchWrap.addView(searchField, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        column.addView(searchWrap, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        View divider = new View(context);
        divider.setBackgroundColor(Theme.getColor(Theme.key_divider));
        column.addView(divider, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, Math.max(1, AndroidUtilities.dp(0.66f))));

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        adapter = new PickerAdapter(context);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((view, position) -> {
            if (position < 0 || position >= filteredUsers.size()) return;
            TLRPC.User user = filteredUsers.get(position);
            long id = user.id;
            if (selected.contains(id)) {
                selected.remove(id);
            } else {
                selected.add(id);
            }
            adapter.notifyItemChanged(position);
            done.setAlpha(selected.isEmpty() ? 0.5f : 1f);
            done.invalidate();
        });
        column.addView(listView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        for (Long id : preselected) {
            if (id != null) {
                selected.add(id);
            }
        }
        loadUsers();
        searchField.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                filterUsers(s == null ? "" : s.toString());
            }
        });

        return column;
    }

    private void loadUsers() {
        int account = UserConfig.selectedAccount;
        MessagesController mc = MessagesController.getInstance(account);
        try {
            org.telegram.messenger.ContactsController cc = org.telegram.messenger.ContactsController.getInstance(account);
            if (cc.contacts != null) {
                for (int i = 0; i < cc.contacts.size(); i++) {
                    TLRPC.TL_contact c = cc.contacts.get(i);
                    TLRPC.User u = mc.getUser(c.user_id);
                    if (u != null && !u.bot && !u.deleted) {
                        if (u.id == UserConfig.getInstance(account).getClientUserId()) continue;
                        usersById.put(u.id, u);
                        allUsers.add(u);
                    }
                }
            }
        } catch (Throwable ignore) {
        }
        // обрані з попередніх сесій
        for (Long id : selected) {
            if (!usersById.containsKey(id)) {
                TLRPC.User u = mc.getUser(id);
                if (u != null) {
                    usersById.put(u.id, u);
                    allUsers.add(u);
                }
            }
        }
        sortByName();
        filterUsers("");
    }

    private void sortByName() {
        java.util.Collections.sort(allUsers, (a, b) -> {
            String an = UserObject.getUserName(a).toLowerCase(Locale.getDefault());
            String bn = UserObject.getUserName(b).toLowerCase(Locale.getDefault());
            return an.compareTo(bn);
        });
    }

    private void filterUsers(String query) {
        filteredUsers.clear();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.getDefault());
        if (q.isEmpty()) {
            filteredUsers.addAll(allUsers);
        } else {
            for (TLRPC.User u : allUsers) {
                String name = UserObject.getUserName(u).toLowerCase(Locale.getDefault());
                String un = u.username == null ? "" : u.username.toLowerCase(Locale.getDefault());
                if (name.contains(q) || un.contains(q)) {
                    filteredUsers.add(u);
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    private class PickerAdapter extends RecyclerListView.SelectionAdapter {

        private final Context context;

        PickerAdapter(Context context) {
            this.context = context;
        }

        @Override
        public androidx.recyclerview.widget.RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            UserCell cell = new UserCell(context, 7, 1, false, false, getResourceProvider());
            cell.setLayoutParams(new androidx.recyclerview.widget.RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(androidx.recyclerview.widget.RecyclerView.ViewHolder holder, int position) {
            if (position < 0 || position >= filteredUsers.size()) return;
            TLRPC.User user = filteredUsers.get(position);
            UserCell cell = (UserCell) holder.itemView;
            String name = UserObject.getUserName(user);
            String status = "@" + (user.username == null ? "" : user.username);
            cell.setData(user, name, status.isEmpty() ? null : status, 0);
            cell.setChecked(selected.contains(user.id), false);
        }

        @Override
        public int getItemCount() {
            return filteredUsers.size();
        }

        @Override
        public boolean isEnabled(androidx.recyclerview.widget.RecyclerView.ViewHolder holder) {
            return true;
        }
    }
}
