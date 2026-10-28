package com.amegram.mods.ui;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import app.amegram.hot.api.HotHost;

/**
 * Module-owned rendering of the Yumi main navigation.
 *
 * <p>The client's MainTabsActivity is not modified. This controller is invoked
 * from module hooks after the activity has built its normal navigation and
 * replaces only the visible layer. Touch handling, long presses, badges,
 * selected-tab gestures and view-pager integration stay in the original view
 * tree.</p>
 */
public final class YumiNavController {

    private static final String[] ICONS = {
            "yumi_ic_chat",
            "yumi_ic_contacts",
            "yumi_ic_settings",
            "yumi_ic_call",
            "yumi_ic_person",
            "yumi_ic_feed"
    };
    private static final int AVATAR_INDEX = 4;

    private final UiModule owner;
    private final HotHost host;
    private final Map<View, TabState> tabs = new WeakHashMap<>();
    private final YumiPalette palette = new YumiPalette(null);
    private WeakReference<Object> lastActivity = new WeakReference<>(null);
    private WeakReference<View> navContainer = new WeakReference<>(null);
    private WeakReference<View> searchButton = new WeakReference<>(null);
    private WeakReference<ImageView> searchIcon = new WeakReference<>(null);
    private Drawable originalNavBackground;
    private Drawable originalSearchBackground;
    private boolean active;

    public YumiNavController(UiModule owner, HotHost host) {
        this.owner = owner;
        this.host = host;
    }

    public void refreshPalette(Context context) {
        palette.refresh(context);
        if (!active) {
            return;
        }
        final View nav = navContainer.get();
        if (nav != null) {
            nav.setBackground(navBackground(nav.getContext()));
        }
        final View search = searchButton.get();
        if (search != null) {
            search.setBackground(searchBackground(search.getContext()));
        }
        final ImageView icon = searchIcon.get();
        if (icon != null) {
            icon.setColorFilter(new android.graphics.PorterDuffColorFilter(
                    palette.navActive, PorterDuff.Mode.SRC_IN));
        }
        for (TabState state : tabs.values()) {
            if (state.item != null) {
                state.item.refreshColors(palette);
            }
        }
    }

    public void apply(Object activity) {
        if (activity == null) {
            return;
        }
        lastActivity = new WeakReference<>(activity);
        final Context context = contextOf(activity);
        if (context == null) {
            return;
        }
        palette.refresh(context);
        active = true;

        final View nav = (View) getField(activity, "tabsView");
        final View search = (View) getField(activity, "searchButton");
        final ImageView icon = (ImageView) getField(activity, "searchIcon");
        final Object tabsArray = getField(activity, "tabs");

        final View previousNav = navContainer.get();
        if (previousNav != nav) {
            originalNavBackground = nav != null ? nav.getBackground() : null;
            originalSearchBackground = search != null ? search.getBackground() : null;
        }
        navContainer = new WeakReference<>(nav);
        searchButton = new WeakReference<>(search);
        searchIcon = new WeakReference<>(icon);

        if (nav != null) {
            nav.setBackground(navBackground(context));
            nav.setElevation(dp(context, 8));
            if (nav instanceof ViewGroup) {
                ((ViewGroup) nav).setClipChildren(false);
            }
        }
        if (search != null) {
            search.setBackground(searchBackground(context));
            search.setElevation(dp(context, 10));
            owner.applySpringStateListAnimator(search);
        }
        if (icon != null) {
            icon.setColorFilter(new android.graphics.PorterDuffColorFilter(
                    palette.navActive, PorterDuff.Mode.SRC_IN));
        }

        if (tabsArray instanceof Object[]) {
            final Object[] array = (Object[]) tabsArray;
            for (int i = 0; i < array.length; i++) {
                if (array[i] instanceof View) {
                    installTab((View) array[i], i);
                }
            }
        }
    }

    public void refresh() {
        if (!owner.isExpressive()) {
            restore();
            return;
        }
        if (active) {
            refreshPalette(host != null ? host.context() : null);
            final Object activity = lastActivity.get();
            if (activity != null) {
                apply(activity);
            }
        }
    }

    public void restore() {
        active = false;
        final View nav = navContainer.get();
        if (nav != null) {
            nav.setBackground(originalNavBackground);
        }
        final View search = searchButton.get();
        if (search != null) {
            search.setBackground(originalSearchBackground);
        }
        for (TabState state : tabs.values()) {
            final View tab = state.tab;
            if (tab instanceof ViewGroup && state.item != null) {
                final ViewGroup group = (ViewGroup) tab;
                group.removeView(state.item);
                state.item = null;
                for (View child : state.children) {
                    if (child.getParent() != null) {
                        ((ViewGroup) child.getParent()).removeView(child);
                    }
                    group.addView(child);
                }
                setSkipDrawSelector(tab, false);
                group.requestLayout();
            }
        }
    }

    private void installTab(View tab, int index) {
        TabState state = tabs.get(tab);
        if (state == null) {
            state = new TabState(tab);
            state.label = textOf(tab);
            state.selected = isSelected(tab);
            state.avatar = avatarOf(tab);
            state.children.addAll(childrenOf(tab));
            tabs.put(tab, state);
        }
        if (state.item != null) {
            state.item.setLabel(state.label);
            state.item.setSelectedState(state.selected);
            state.item.setCounter(state.counter, state.counterError);
            if (state.avatar != null) {
                state.item.setAvatar(state.avatar);
            }
            return;
        }

        final String iconName = index >= 0 && index < ICONS.length ? ICONS[index] : ICONS[0];
        state.item = new YumiNavItem(tab.getContext(), palette, iconName, index == AVATAR_INDEX);
        state.item.setLabel(state.label);
        state.item.setSelectedState(state.selected);
        state.item.setCounter(state.counter, state.counterError);
        if (state.avatar != null) {
            state.item.setAvatar(state.avatar);
        }

        if (tab instanceof ViewGroup) {
            final ViewGroup group = (ViewGroup) tab;
            group.removeAllViews();
            group.addView(state.item, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            setSkipDrawSelector(tab, true);
            clearLegacyCounter(tab);
            group.requestLayout();
        }
    }

    /** Stops GlassTabView from drawing its old counter behind the Yumi badge. */
    private static void clearLegacyCounter(View tab) {
        try {
            final Field counterField = tab.getClass().getDeclaredField("counter");
            counterField.setAccessible(true);
            final Object counter = counterField.get(tab);
            if (counter != null) {
                try {
                    final Method setText = counter.getClass().getMethod("setText",
                            CharSequence.class, boolean.class);
                    setText.invoke(counter, "", false);
                } catch (Throwable ignore) {
                }
            }
            final Field visibleField = tab.getClass().getDeclaredField("isHasCounterAnimator");
            visibleField.setAccessible(true);
            final Object animator = visibleField.get(tab);
            if (animator != null) {
                try {
                    final Method setValue = animator.getClass().getMethod("setValue",
                            boolean.class, boolean.class);
                    setValue.invoke(animator, false, false);
                } catch (Throwable ignore) {
                }
            }
        } catch (Throwable ignore) {
        }
    }

    public void onCounter(View tab, String text, boolean error) {
        if (!owner.isExpressive()) {
            return;
        }
        final TabState state = stateFor(tab);
        state.counter = text;
        state.counterError = error;
        if (state.item != null) {
            state.item.setCounter(text, error);
        }
    }

    public void onSelected(View tab, boolean selected) {
        if (!owner.isExpressive()) {
            return;
        }
        final TabState state = stateFor(tab);
        state.selected = selected;
        if (state.item != null) {
            state.item.setSelectedState(selected);
        }
    }

    public void onText(View tab, CharSequence text) {
        if (!owner.isExpressive()) {
            return;
        }
        final TabState state = stateFor(tab);
        state.label = text;
        if (state.item != null) {
            state.item.setLabel(text);
        }
        tab.setContentDescription(text);
    }

    public void onAvatar(View tab) {
        if (!owner.isExpressive()) {
            return;
        }
        final TabState state = stateFor(tab);
        state.avatar = avatarOf(tab);
        if (state.item != null && state.avatar != null) {
            state.item.setAvatar(state.avatar);
        }
    }

    public void onLayout(View tab) {
        final TabState state = tabs.get(tab);
        if (state != null && state.item != null) {
            state.item.layout(0, 0, tab.getWidth(), tab.getHeight());
        }
    }

    public void onColors(View tab) {
        final TabState state = tabs.get(tab);
        if (state != null && state.item != null) {
            state.item.refreshColors(palette);
        }
    }

    private TabState stateFor(View tab) {
        TabState state = tabs.get(tab);
        if (state == null) {
            state = new TabState(tab);
            state.label = textOf(tab);
            state.selected = isSelected(tab);
            state.avatar = avatarOf(tab);
            state.children.addAll(childrenOf(tab));
            tabs.put(tab, state);
        }
        return state;
    }

    private Drawable navBackground(Context context) {
        final GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(palette.navSurface);
        drawable.setStroke(Math.max(1, dp(context, 1f)), palette.navBorder);
        drawable.setCornerRadius(dp(context, 22));
        return drawable;
    }

    private Drawable searchBackground(Context context) {
        final GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(palette.primaryContainer);
        drawable.setStroke(Math.max(1, dp(context, 1f)), palette.navBorder);
        return drawable;
    }

    private static Context contextOf(Object activity) {
        if (activity instanceof Context) {
            return (Context) activity;
        }
        if (activity instanceof View) {
            return ((View) activity).getContext();
        }
        return null;
    }

    private static List<View> childrenOf(View tab) {
        final List<View> result = new ArrayList<>();
        if (tab instanceof ViewGroup) {
            final ViewGroup group = (ViewGroup) tab;
            for (int i = 0; i < group.getChildCount(); i++) {
                result.add(group.getChildAt(i));
            }
        }
        return result;
    }

    private static CharSequence textOf(View tab) {
        try {
            final Field field = tab.getClass().getDeclaredField("textView");
            field.setAccessible(true);
            final Object value = field.get(tab);
            if (value instanceof android.widget.TextView) {
                return ((android.widget.TextView) value).getText();
            }
        } catch (Throwable ignore) {
        }
        return tab.getContentDescription();
    }

    private static boolean isSelected(View tab) {
        try {
            final Method method = tab.getClass().getMethod("isTabSelected");
            final Object value = method.invoke(tab);
            return value instanceof Boolean && (Boolean) value;
        } catch (Throwable ignore) {
            return tab.isSelected();
        }
    }

    private static Drawable avatarOf(View tab) {
        try {
            final Field field = tab.getClass().getDeclaredField("backupImageView");
            field.setAccessible(true);
            final Object value = field.get(tab);
            if (value instanceof ImageView) {
                return ((ImageView) value).getDrawable();
            }
        } catch (Throwable ignore) {
        }
        return null;
    }

    private static void setSkipDrawSelector(View tab, boolean skip) {
        try {
            final Method method = tab.getClass().getMethod("setSkipDrawSelector", boolean.class);
            method.invoke(tab, skip);
            return;
        } catch (Throwable ignore) {
        }
        try {
            final Field field = tab.getClass().getDeclaredField("skipDrawSelector");
            field.setAccessible(true);
            field.setBoolean(tab, skip);
        } catch (Throwable ignore) {
        }
    }

    private static Object getField(Object target, String name) {
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

    private static final class TabState {
        final View tab;
        final List<View> children = new ArrayList<>();
        YumiNavItem item;
        CharSequence label = "";
        boolean selected;
        String counter = "";
        boolean counterError;
        Drawable avatar;

        TabState(View tab) {
            this.tab = tab;
        }
    }
}
