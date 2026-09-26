package app.miogram.bridge.folders;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.support.LongSparseIntArray;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import app.miogram.bridge.MiogramLocale;

/**
 * Miogram Subfolder & Smart Category Engine.
 * Provides hierarchical folder grouping (e.g. "Work / Dev"),
 * smart type filters (Personal, Groups, Channels, Bots, Unread),
 * and client-side local subfolders without burning Telegram server limits.
 */
public class MiogramSubfolderEngine {

    private static final String PREFS_NAME = "miogram_subfolders_prefs";
    private static final String KEY_ENABLED = "subfolders_enabled";
    private static final String KEY_HIERARCHICAL = "subfolders_hierarchical";
    private static final String KEY_COLLAPSE_MAIN_TABS = "subfolders_collapse_main_tabs";
    private static final String KEY_SMART_FILTERS = "subfolders_smart_filters";
    private static final String KEY_SHOW_COUNTERS = "subfolders_show_counters";

    public static final int TYPE_ALL = 0;
    public static final int TYPE_PERSONAL = 1;
    public static final int TYPE_GROUPS = 2;
    public static final int TYPE_CHANNELS = 3;
    public static final int TYPE_BOTS = 4;
    public static final int TYPE_UNREAD = 5;

    private static final String[] SEPARATORS = {" / ", " > ", " | ", ": ", "/"};

    private static final ConcurrentHashMap<Integer, Integer> activeParentTabMap = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, Integer> activeChildFilterMap = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, Integer> activeLocalSubfolderIdMap = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, Integer> activeSubfolderTypeMap = new ConcurrentHashMap<>();

    private static SharedPreferences getPrefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isSubfoldersEnabled() {
        return getPrefs().getBoolean(KEY_ENABLED, true);
    }

    public static void setSubfoldersEnabled(boolean enabled) {
        getPrefs().edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public static boolean isHierarchicalEnabled() {
        return getPrefs().getBoolean(KEY_HIERARCHICAL, true);
    }

    public static void setHierarchicalEnabled(boolean enabled) {
        getPrefs().edit().putBoolean(KEY_HIERARCHICAL, enabled).apply();
    }

    public static boolean isCollapseSubfoldersEnabled() {
        return getPrefs().getBoolean(KEY_COLLAPSE_MAIN_TABS, true);
    }

    public static void setCollapseSubfoldersEnabled(boolean enabled) {
        getPrefs().edit().putBoolean(KEY_COLLAPSE_MAIN_TABS, enabled).apply();
    }

    public static boolean isSmartFiltersEnabled() {
        return getPrefs().getBoolean(KEY_SMART_FILTERS, true);
    }

    public static void setSmartFiltersEnabled(boolean enabled) {
        getPrefs().edit().putBoolean(KEY_SMART_FILTERS, enabled).apply();
    }

    public static boolean isShowCountersEnabled() {
        return getPrefs().getBoolean(KEY_SHOW_COUNTERS, true);
    }

    public static void setShowCountersEnabled(boolean enabled) {
        getPrefs().edit().putBoolean(KEY_SHOW_COUNTERS, enabled).apply();
    }

    public static int getActiveParentTabId(int currentAccount) {
        Integer val = activeParentTabMap.get(currentAccount);
        return val != null ? val : 0;
    }

    public static void setActiveParentTabId(int currentAccount, int tabId) {
        activeParentTabMap.put(currentAccount, tabId);
    }

    public static int getActiveChildFilterId(int currentAccount) {
        Integer val = activeChildFilterMap.get(currentAccount);
        return val != null ? val : 0;
    }

    public static void setActiveChildFilterId(int currentAccount, int filterId) {
        activeChildFilterMap.put(currentAccount, filterId);
    }

    public static int getActiveSubfolderType(int currentAccount) {
        Integer val = activeSubfolderTypeMap.get(currentAccount);
        return val != null ? val : TYPE_ALL;
    }

    public static void setActiveSubfolderType(int currentAccount, int type) {
        activeSubfolderTypeMap.put(currentAccount, type);
    }

    public static int getActiveLocalSubfolderId(int currentAccount) {
        Integer val = activeLocalSubfolderIdMap.get(currentAccount);
        return val != null ? val : 0;
    }

    public static void setActiveLocalSubfolderId(int currentAccount, int id) {
        activeLocalSubfolderIdMap.put(currentAccount, id);
    }

    public static void resetActiveSubfolder(int currentAccount) {
        activeChildFilterMap.put(currentAccount, 0);
        activeLocalSubfolderIdMap.put(currentAccount, 0);
        activeSubfolderTypeMap.put(currentAccount, TYPE_ALL);
    }

    public static class LocalSubfolder {
        public int id;
        public int parentFilterId;
        public String name;
        public int flags;
        public ArrayList<Long> dialogIds = new ArrayList<>();

        public JSONObject toJson() {
            try {
                JSONObject obj = new JSONObject();
                obj.put("id", id);
                obj.put("parentFilterId", parentFilterId);
                obj.put("name", name);
                obj.put("flags", flags);
                JSONArray arr = new JSONArray();
                for (Long did : dialogIds) {
                    arr.put(did);
                }
                obj.put("dialogIds", arr);
                return obj;
            } catch (Exception e) {
                return null;
            }
        }

        public static LocalSubfolder fromJson(JSONObject obj) {
            try {
                LocalSubfolder s = new LocalSubfolder();
                s.id = obj.getInt("id");
                s.parentFilterId = obj.optInt("parentFilterId", 0);
                s.name = obj.getString("name");
                s.flags = obj.optInt("flags", 0);
                JSONArray arr = obj.optJSONArray("dialogIds");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        s.dialogIds.add(arr.getLong(i));
                    }
                }
                return s;
            } catch (Exception e) {
                return null;
            }
        }
    }

    public static ArrayList<LocalSubfolder> getLocalSubfolders(int currentAccount) {
        ArrayList<LocalSubfolder> list = new ArrayList<>();
        String json = getPrefs().getString("local_subfolders_" + currentAccount, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                LocalSubfolder s = LocalSubfolder.fromJson(arr.getJSONObject(i));
                if (s != null) {
                    list.add(s);
                }
            }
        } catch (Exception ignore) {}
        return list;
    }

    public static void saveLocalSubfolders(int currentAccount, ArrayList<LocalSubfolder> list) {
        JSONArray arr = new JSONArray();
        for (LocalSubfolder s : list) {
            JSONObject obj = s.toJson();
            if (obj != null) {
                arr.put(obj);
            }
        }
        getPrefs().edit().putString("local_subfolders_" + currentAccount, arr.toString()).apply();
    }

    public static ArrayList<LocalSubfolder> getLocalSubfoldersForParent(int currentAccount, int parentFilterId) {
        ArrayList<LocalSubfolder> all = getLocalSubfolders(currentAccount);
        ArrayList<LocalSubfolder> res = new ArrayList<>();
        for (LocalSubfolder s : all) {
            if (s.parentFilterId == parentFilterId) {
                res.add(s);
            }
        }
        return res;
    }

    public static LocalSubfolder getLocalSubfolderById(int currentAccount, int id) {
        for (LocalSubfolder s : getLocalSubfolders(currentAccount)) {
            if (s.id == id) {
                return s;
            }
        }
        return null;
    }

    public static void deleteLocalSubfolder(BaseFragment fragment, int currentAccount, int id, Runnable onDeleted) {
        ArrayList<LocalSubfolder> all = getLocalSubfolders(currentAccount);
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id == id) {
                all.remove(i);
                break;
            }
        }
        saveLocalSubfolders(currentAccount, all);
        if (getActiveLocalSubfolderId(currentAccount) == id) {
            resetActiveSubfolder(currentAccount);
        }
        if (onDeleted != null) {
            onDeleted.run();
        }
        try {
            if (fragment != null) {
                BulletinFactory.of(fragment).createSimpleBulletin(R.raw.filter_reorder, MiogramLocale.get("Підпапку видалено", "Подпапка удалена", "Subfolder deleted")).show();
            }
        } catch (Exception ignore) {}
    }

    public static String getParentName(String fullName) {
        if (fullName == null) return null;
        for (String sep : SEPARATORS) {
            int idx = fullName.indexOf(sep);
            if (idx > 0) {
                return fullName.substring(0, idx).trim();
            }
        }
        return null;
    }

    public static String getChildName(String fullName) {
        if (fullName == null) return fullName;
        for (String sep : SEPARATORS) {
            int idx = fullName.indexOf(sep);
            if (idx > 0) {
                return fullName.substring(idx + sep.length()).trim();
            }
        }
        return fullName;
    }

    public static boolean isChildFilter(ArrayList<MessagesController.DialogFilter> filters, MessagesController.DialogFilter filter) {
        if (filter == null || filter.name == null) return false;
        String parentName = getParentName(filter.name.trim());
        if (parentName == null || parentName.isEmpty() || parentName.equalsIgnoreCase(filter.name.trim())) {
            return false;
        }
        for (int i = 0; i < filters.size(); i++) {
            MessagesController.DialogFilter f = filters.get(i);
            if (f != null && f.name != null && f.name.trim().equalsIgnoreCase(parentName)) {
                return true;
            }
        }
        return false;
    }

    public static ArrayList<MessagesController.DialogFilter> getChildFiltersForParent(int currentAccount, MessagesController.DialogFilter parentFilter) {
        ArrayList<MessagesController.DialogFilter> result = new ArrayList<>();
        if (parentFilter == null || parentFilter.name == null) return result;
        String parentName = parentFilter.name.trim();
        ArrayList<MessagesController.DialogFilter> allFilters = MessagesController.getInstance(currentAccount).getDialogFilters();
        for (int i = 0; i < allFilters.size(); i++) {
            MessagesController.DialogFilter f = allFilters.get(i);
            if (f != null && f.id != parentFilter.id && f.name != null) {
                String p = getParentName(f.name);
                if (p != null && p.equalsIgnoreCase(parentName)) {
                    result.add(f);
                }
            }
        }
        return result;
    }

    public static boolean matchesType(int currentAccount, TLRPC.Dialog d, int type) {
        if (d == null) return false;
        long dialogId = d.id;
        MessagesController mc = MessagesController.getInstance(currentAccount);
        if (mc == null) return false;

        if (type == TYPE_PERSONAL) {
            if (DialogObject.isUserDialog(dialogId)) {
                TLRPC.User user = mc.getUser(dialogId);
                return user != null && !user.bot && !UserObject.isUserSelf(user);
            }
            return false;
        } else if (type == TYPE_GROUPS) {
            if (DialogObject.isChatDialog(dialogId)) {
                TLRPC.Chat chat = mc.getChat(-dialogId);
                return chat != null && (!ChatObject.isChannel(chat) || ChatObject.isMegagroup(chat));
            }
            return false;
        } else if (type == TYPE_CHANNELS) {
            if (DialogObject.isChatDialog(dialogId)) {
                TLRPC.Chat chat = mc.getChat(-dialogId);
                return chat != null && ChatObject.isChannel(chat) && !ChatObject.isMegagroup(chat);
            }
            return false;
        } else if (type == TYPE_BOTS) {
            if (DialogObject.isUserDialog(dialogId)) {
                TLRPC.User user = mc.getUser(dialogId);
                return user != null && user.bot;
            }
            return false;
        } else if (type == TYPE_UNREAD) {
            return d.unread_count > 0 || d.unread_mentions_count > 0 || d.unread_reactions_count > 0 || d.unread_mark || (mc != null && mc.getDialogUnreadCount(d) > 0);
        }
        return true;
    }

    public static boolean matchesLocalSubfolder(int currentAccount, TLRPC.Dialog d, LocalSubfolder subfolder) {
        if (d == null || subfolder == null) return false;
        if (subfolder.dialogIds != null && subfolder.dialogIds.contains(d.id)) {
            return true;
        }
        int flags = subfolder.flags;
        if (flags == 0) return true;
        MessagesController mc = MessagesController.getInstance(currentAccount);
        if (mc == null) return false;

        boolean isUser = DialogObject.isUserDialog(d.id);
        boolean isChat = DialogObject.isChatDialog(d.id);

        if (isUser) {
            TLRPC.User user = mc.getUser(d.id);
            if (user != null) {
                if (user.bot) {
                    if ((flags & MessagesController.DIALOG_FILTER_FLAG_BOTS) != 0) return true;
                } else if (user.contact) {
                    if ((flags & MessagesController.DIALOG_FILTER_FLAG_CONTACTS) != 0) return true;
                } else {
                    if ((flags & MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS) != 0) return true;
                }
            }
        } else if (isChat) {
            TLRPC.Chat chat = mc.getChat(-d.id);
            if (chat != null) {
                if (ChatObject.isChannel(chat) && !ChatObject.isMegagroup(chat)) {
                    if ((flags & MessagesController.DIALOG_FILTER_FLAG_CHANNELS) != 0) return true;
                } else {
                    if ((flags & MessagesController.DIALOG_FILTER_FLAG_GROUPS) != 0) return true;
                }
            }
        }
        return false;
    }

    public static ArrayList<TLRPC.Dialog> applySubfolderFiltering(int currentAccount, ArrayList<TLRPC.Dialog> baseList) {
        if (!isSubfoldersEnabled() || baseList == null) {
            return baseList;
        }
        MessagesController mc = MessagesController.getInstance(currentAccount);
        if (mc == null) return baseList;

        ArrayList<TLRPC.Dialog> sourceList = baseList;
        int childFilterId = getActiveChildFilterId(currentAccount);
        if (childFilterId > 0) {
            MessagesController.DialogFilter childFilter = mc.dialogFiltersById.get(childFilterId);
            if (childFilter != null && childFilter.dialogs != null) {
                sourceList = childFilter.dialogs;
            }
        }

        int activeLocalId = getActiveLocalSubfolderId(currentAccount);
        if (activeLocalId > 0) {
            LocalSubfolder subfolder = getLocalSubfolderById(currentAccount, activeLocalId);
            if (subfolder != null) {
                ArrayList<TLRPC.Dialog> localFiltered = new ArrayList<>();
                for (int i = 0; i < sourceList.size(); i++) {
                    TLRPC.Dialog d = sourceList.get(i);
                    if (d != null && matchesLocalSubfolder(currentAccount, d, subfolder)) {
                        localFiltered.add(d);
                    }
                }
                sourceList = localFiltered;
            }
        }

        int subfolderType = getActiveSubfolderType(currentAccount);
        if (subfolderType == TYPE_ALL) {
            return sourceList;
        }

        ArrayList<TLRPC.Dialog> filtered = new ArrayList<>();
        for (int i = 0; i < sourceList.size(); i++) {
            TLRPC.Dialog d = sourceList.get(i);
            if (d != null && matchesType(currentAccount, d, subfolderType)) {
                filtered.add(d);
            }
        }
        return filtered;
    }

    public static int calculateUnreadCount(int currentAccount, ArrayList<TLRPC.Dialog> list, int type) {
        if (!isShowCountersEnabled() || list == null || list.isEmpty()) return 0;
        MessagesController mc = MessagesController.getInstance(currentAccount);
        int count = 0;
        for (int i = 0; i < list.size(); i++) {
            TLRPC.Dialog d = list.get(i);
            if (d == null) continue;
            if (type != TYPE_ALL && !matchesType(currentAccount, d, type)) {
                continue;
            }
            if (d.unread_count > 0 || d.unread_mentions_count > 0 || d.unread_reactions_count > 0 || d.unread_mark || (mc != null && mc.getDialogUnreadCount(d) > 0)) {
                count++;
            }
        }
        return count;
    }

    public static void showCreateSubfolderDialog(BaseFragment fragment, int currentAccount, MessagesController.DialogFilter parentFilter, Runnable onCreated) {
        if (fragment == null || fragment.getParentActivity() == null) return;
        Context context = fragment.getParentActivity();

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(MiogramLocale.get("Нова підпапка", "Новая подпапка", "New subfolder"));

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(10), AndroidUtilities.dp(20), AndroidUtilities.dp(10));

        final String parentName = (parentFilter != null && !parentFilter.isDefault() && !TextUtils.isEmpty(parentFilter.name)) ? parentFilter.name.trim() : "";

        if (!TextUtils.isEmpty(parentName)) {
            TextView parentDesc = new TextView(context);
            parentDesc.setText(MiogramLocale.get("У папці: ", "В папке: ", "In folder: ") + parentName);
            parentDesc.setTextSize(13);
            parentDesc.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            layout.addView(parentDesc);
        }

        EditTextBoldCursor editText = new EditTextBoldCursor(context);
        editText.setHint(MiogramLocale.get("Назва підпапки", "Название подпапки", "Subfolder name"));
        editText.setTextSize(16);
        editText.setSingleLine(true);
        editText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        editText.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        etLp.topMargin = AndroidUtilities.dp(8);
        etLp.bottomMargin = AndroidUtilities.dp(12);
        layout.addView(editText, etLp);

        TextView filterTypesDesc = new TextView(context);
        filterTypesDesc.setText(MiogramLocale.get("Включити типи чатів:", "Включить типы чатов:", "Include chat types:"));
        filterTypesDesc.setTextSize(12);
        filterTypesDesc.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        layout.addView(filterTypesDesc);

        CheckBoxCell cbContacts = new CheckBoxCell(context, 1);
        cbContacts.setText(MiogramLocale.get("Контакти", "Контакты", "Contacts"), "", true, false);
        layout.addView(cbContacts);

        CheckBoxCell cbNonContacts = new CheckBoxCell(context, 1);
        cbNonContacts.setText(MiogramLocale.get("Неконтакти", "Неконтакты", "Non-contacts"), "", true, false);
        layout.addView(cbNonContacts);

        CheckBoxCell cbGroups = new CheckBoxCell(context, 1);
        cbGroups.setText(MiogramLocale.get("Групи", "Группы", "Groups"), "", true, false);
        layout.addView(cbGroups);

        CheckBoxCell cbChannels = new CheckBoxCell(context, 1);
        cbChannels.setText(MiogramLocale.get("Канали", "Каналы", "Channels"), "", true, false);
        layout.addView(cbChannels);

        CheckBoxCell cbBots = new CheckBoxCell(context, 1);
        cbBots.setText(MiogramLocale.get("Боти", "Боты", "Bots"), "", true, false);
        layout.addView(cbBots);

        builder.setView(layout);

        builder.setPositiveButton(MiogramLocale.get("Створити", "Создать", "Create"), (dialog, which) -> {
            String name = editText.getText().toString().trim();
            if (TextUtils.isEmpty(name)) {
                Toast.makeText(context, MiogramLocale.get("Введіть назву підпапки", "Введите название подпапки", "Enter a subfolder name"), Toast.LENGTH_SHORT).show();
                return;
            }

            int flags = 0;
            if (cbContacts.isChecked()) flags |= MessagesController.DIALOG_FILTER_FLAG_CONTACTS;
            if (cbNonContacts.isChecked()) flags |= MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS;
            if (cbGroups.isChecked()) flags |= MessagesController.DIALOG_FILTER_FLAG_GROUPS;
            if (cbChannels.isChecked()) flags |= MessagesController.DIALOG_FILTER_FLAG_CHANNELS;
            if (cbBots.isChecked()) flags |= MessagesController.DIALOG_FILTER_FLAG_BOTS;
            if (flags == 0) flags = MessagesController.DIALOG_FILTER_FLAG_ALL_CHATS;

            LocalSubfolder sub = new LocalSubfolder();
            sub.id = (int) (System.currentTimeMillis() % 1000000000L);
            sub.parentFilterId = parentFilter != null ? parentFilter.id : 0;
            sub.name = name;
            sub.flags = flags;

            ArrayList<LocalSubfolder> all = getLocalSubfolders(currentAccount);
            all.add(sub);
            saveLocalSubfolders(currentAccount, all);

            if (onCreated != null) {
                onCreated.run();
            }

            try {
                BulletinFactory.of(fragment).createSimpleBulletin(R.raw.filter_reorder, MiogramLocale.get("Підпапку '", "Подпапка '", "Subfolder '") + name + MiogramLocale.get("' створено", "' создана", "' created")).show();
            } catch (Exception ignore) {}
        });

        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.show();
    }

    public static void deleteSubfolder(BaseFragment fragment, int currentAccount, MessagesController.DialogFilter filter, Runnable onDeleted) {
        if (fragment == null || filter == null) return;
        TLRPC.TL_messages_updateDialogFilter req = new TLRPC.TL_messages_updateDialogFilter();
        req.id = filter.id;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, null);
        MessagesController.getInstance(currentAccount).removeFilter(filter);
        MessagesStorage.getInstance(currentAccount).deleteDialogFilter(filter);
        if (onDeleted != null) {
            onDeleted.run();
        }
        try {
            BulletinFactory.of(fragment).createSimpleBulletin(R.raw.filter_reorder, "Підпапку видалено").show();
        } catch (Exception ignore) {}
    }
}
