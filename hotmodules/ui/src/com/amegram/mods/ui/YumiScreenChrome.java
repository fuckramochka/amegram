package com.amegram.mods.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

import app.amegram.hot.api.HotHost;

/**
 * Module-owned chrome for official list fragments (contacts, calls, folders).
 *
 * <p>The official fragment, adapters, list views, insets and lifecycle are
 * untouched. After createView the module inserts a Yumi top app bar and, for
 * contacts, a Yumi search field. The legacy ActionBar remains measured and is
 * made invisible so the status insets and list padding stay exactly as the
 * upstream code expects. Action mode temporarily brings it back.</p>
 */
public final class YumiScreenChrome {

    public static final String KIND_CONTACTS = "contacts";
    public static final String KIND_CALLS = "calls";
    public static final String KIND_FOLDERS = "folders";

    private final UiModule owner;
    private final HotHost host;
    private final YumiPalette palette = new YumiPalette(null);
    private final Map<View, ScreenState> byRoot = new WeakHashMap<>();
    private final Map<View, ScreenState> byActionBar = new WeakHashMap<>();

    public YumiScreenChrome(UiModule owner, HotHost host) {
        this.owner = owner;
        this.host = host;
    }

    public void restoreAll() {
        for (ScreenState state : byRoot.values()) {
            if (state.topBar.getParent() instanceof ViewGroup) {
                ((ViewGroup) state.topBar.getParent()).removeView(state.topBar);
            }
            if (state.searchField != null && state.searchField.getParent() instanceof ViewGroup) {
                ((ViewGroup) state.searchField.getParent()).removeView(state.searchField);
            }
            state.actionBar.setVisibility(state.originalActionBarVisibility);
            state.actionBar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            if (state.oldSearch != null) {
                state.oldSearch.setVisibility(View.VISIBLE);
            }
            if (state.list != null) {
                state.list.setPadding(state.list.getPaddingLeft(), state.originalListTopPadding,
                        state.list.getPaddingRight(), state.list.getPaddingBottom());
            }
        }
        byRoot.clear();
        byActionBar.clear();
    }

    public void refreshPalette(Context context) {
        palette.refresh(context);
        for (ScreenState state : byRoot.values()) {
            state.topBar.refreshColors(palette);
            if (state.searchField != null) {
                state.searchField.refreshColors(palette);
            }
        }
    }

    public void apply(Object fragment, Context context, String kind) {
        if (!owner.isExpressive() || fragment == null || context == null) {
            return;
        }
        final View root = viewField(fragment, "contentView");
        final View actionBar = viewField(fragment, "actionBar");
        final View fragmentView = viewField(fragment, "fragmentView");
        final View actualRoot = root != null ? root : fragmentView;
        if (actualRoot == null || actionBar == null || !(actualRoot instanceof ViewGroup)) {
            return;
        }
        ScreenState existing = byRoot.get(actualRoot);
        if (existing != null) {
            existing.updateActionMode(actionBar.isShown());
            return;
        }
        palette.refresh(context);

        final boolean hasMainTabs = boolField(fragment, "hasMainTabs");
        final YumiTopBar topBar = new YumiTopBar(context, palette, !hasMainTabs);
        topBar.setElevation(dp(context, 2));
        final String title = titleOf(actionBar, kind);
        topBar.setTitle(title);
        final Activity activity = activityOf(fragment);
        if (!hasMainTabs && activity != null) {
            topBar.setOnBackClickListener(v -> activity.onBackPressed());
        }

        final ScreenState state = new ScreenState(actualRoot, actionBar, topBar);
        state.originalActionBarVisibility = actionBar.getVisibility();
        actionBar.setVisibility(View.INVISIBLE);
        actionBar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);

        ((ViewGroup) actualRoot).addView(topBar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, YumiTopBar.topBarHeight(context),
                Gravity.TOP));

        if (KIND_CONTACTS.equals(kind)) {
            setupContactsSearch(fragment, context, state, topBar);
        } else if (KIND_CALLS.equals(kind)) {
            setupAction(fragment, "otherItem", context, topBar, "yumi_ic_more");
            final View list = viewField(fragment, "listView");
            if (list != null) {
                state.list = list;
                state.originalListTopPadding = list.getPaddingTop();
                list.setPadding(list.getPaddingLeft(),
                        Math.max(state.originalListTopPadding, YumiTopBar.topBarHeight(context)),
                        list.getPaddingRight(), list.getPaddingBottom());
            }
        } else if (KIND_FOLDERS.equals(kind)) {
            final View list = viewField(fragment, "listView");
            if (list != null) {
                state.list = list;
                state.originalListTopPadding = list.getPaddingTop();
                list.setPadding(list.getPaddingLeft(),
                        Math.max(state.originalListTopPadding, YumiTopBar.topBarHeight(context)),
                        list.getPaddingRight(), list.getPaddingBottom());
            }
        }

        byRoot.put(actualRoot, state);
        byActionBar.put(actionBar, state);
    }

    private void setupContactsSearch(Object fragment, Context context, ScreenState state,
                                     YumiTopBar topBar) {
        final View oldSearch = viewField(fragment, "searchField");
        final View oldEditView = viewField(oldSearch, "editText");
        final EditText oldEdit = oldEditView instanceof EditText ? (EditText) oldEditView : null;
        final YumiSearchField search = new YumiSearchField(context, palette);
        final CharSequence hint = oldEdit != null && !android.text.TextUtils.isEmpty(oldEdit.getHint())
                ? oldEdit.getHint() : null;
        if (hint != null) {
            search.setHint(hint);
        }
        final FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 52), Gravity.TOP);
        lp.leftMargin = dp(context, 8);
        lp.rightMargin = dp(context, 8);
        lp.topMargin = YumiTopBar.topBarHeight(context) - dp(context, 4);
        ((ViewGroup) state.root).addView(search, lp);
        state.searchField = search;
        state.oldSearch = oldSearch;
        if (oldSearch != null) {
            oldSearch.setVisibility(View.GONE);
        }

        final View list = viewField(fragment, "listView");
        if (list != null) {
            state.list = list;
            state.originalListTopPadding = list.getPaddingTop();
            final int totalTop = YumiTopBar.topBarHeight(context) + dp(context, 54);
            list.setPadding(list.getPaddingLeft(),
                    Math.max(state.originalListTopPadding, totalTop),
                    list.getPaddingRight(), list.getPaddingBottom());
        }

        final boolean[] syncing = new boolean[1];
        search.editText().addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (oldEdit == null || syncing[0]) {
                    return;
                }
                syncing[0] = true;
                oldEdit.setText(s);
                oldEdit.setSelection(oldEdit.getText().length());
                syncing[0] = false;
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        if (oldEdit != null) {
            if (!android.text.TextUtils.isEmpty(oldEdit.getText())) {
                search.setText(oldEdit.getText());
            }
            oldEdit.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (syncing[0] || android.text.TextUtils.equals(s, search.editText().getText())) {
                        return;
                    }
                    syncing[0] = true;
                    search.setText(s);
                    syncing[0] = false;
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });
        }

        setupAction(fragment, "sortItem", context, topBar, "yumi_ic_tune");
    }

    private void setupAction(Object fragment, String field, Context context,
                             YumiTopBar topBar, String iconName) {
        final View item = viewField(fragment, field);
        if (item == null || item.getVisibility() == View.GONE) {
            return;
        }
        final int res = context.getResources().getIdentifier(
                iconName, "drawable", context.getPackageName());
        if (res == 0) {
            return;
        }
        topBar.addAction(res, item.getContentDescription(), v -> {
            if (item.isShown()) {
                item.performClick();
            } else {
                // The legacy item is intentionally invisible; performClick still
                // opens its popup for the user after we hide the old bar.
                item.performClick();
            }
        });
    }

    public void onActionMode(View actionBar, boolean visible) {
        final ScreenState state = byActionBar.get(actionBar);
        if (state == null) {
            return;
        }
        state.updateActionMode(visible);
    }

    private static String titleOf(View actionBar, String kind) {
        try {
            final Method getTitle = actionBar.getClass().getMethod("getTitle");
            final Object value = getTitle.invoke(actionBar);
            if (value instanceof CharSequence && ((CharSequence) value).length() > 0) {
                return value.toString();
            }
        } catch (Throwable ignore) {
        }
        if (KIND_CONTACTS.equals(kind)) {
            return YumiText.tr("Контакти", "Контакты", "Contacts");
        }
        if (KIND_CALLS.equals(kind)) {
            return YumiText.tr("Дзвінки", "Звонки", "Calls");
        }
        return YumiText.tr("Папки", "Папки", "Folders");
    }

    private static Activity activityOf(Object fragment) {
        try {
            final Method method = fragment.getClass().getMethod("getParentActivity");
            final Object value = method.invoke(fragment);
            return value instanceof Activity ? (Activity) value : null;
        } catch (Throwable ignore) {
            return null;
        }
    }

    private static View viewField(Object target, String name) {
        final Object value = field(target, name);
        return value instanceof View ? (View) value : null;
    }

    private static boolean boolField(Object target, String name) {
        final Object value = field(target, name);
        return value instanceof Boolean && (Boolean) value;
    }

    private static Object field(Object target, String name) {
        if (target == null) {
            return null;
        }
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                final Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (Throwable ignore) {
                return null;
            }
        }
        return null;
    }

    private static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static final class ScreenState {
        final View root;
        final View actionBar;
        final YumiTopBar topBar;
        YumiSearchField searchField;
        View oldSearch;
        View list;
        int originalActionBarVisibility;
        int originalListTopPadding;
        boolean actionMode;

        ScreenState(View root, View actionBar, YumiTopBar topBar) {
            this.root = root;
            this.actionBar = actionBar;
            this.topBar = topBar;
        }

        void updateActionMode(boolean visible) {
            actionMode = visible;
            if (visible) {
                topBar.setVisibility(View.GONE);
                if (searchField != null) {
                    searchField.setVisibility(View.GONE);
                }
                actionBar.setVisibility(View.VISIBLE);
                actionBar.setImportantForAccessibility(
                        View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            } else {
                actionBar.setVisibility(View.INVISIBLE);
                actionBar.setImportantForAccessibility(
                        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
                topBar.setVisibility(View.VISIBLE);
                if (searchField != null) {
                    searchField.setVisibility(View.VISIBLE);
                }
            }
        }
    }
}
