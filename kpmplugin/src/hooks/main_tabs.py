import traceback
import weakref

from base_plugin import MethodHook
from client_utils import run_on_queue
from hook_utils import find_class, get_private_field
from java import dynamic_proxy

from ..methods import log, log_error, _tr

TAB_TAG = "kpm_bottom_tab"


def _get_tabs_view(activity):
    try:
        return get_private_field(activity, "tabsView")
    except Exception:
        return None


def _find_our_tab(tabs_view):
    try:
        for i in range(tabs_view.getChildCount()):
            try:
                v = tabs_view.getChildAt(i)
                if v is not None and v.getTag() == TAB_TAG:
                    return v
            except Exception:
                continue
    except Exception:
        pass
    return None


def _is_exteraless():
    try:
        Class = find_class("java.lang.Class")
        if Class is not None:
            try:
                Class.forName("app.exteraless.appearance.MainTabsUiHelper")
                return True
            except Exception:
                return False
    except Exception:
        pass
    return False


def _create_tab(GTV, ctx, rp, anim, title_res):
    factory = getattr(GTV, "createMainNavigationTab", None) or getattr(GTV, "createMainTab", None)
    if factory is None:
        return None
    return factory(ctx, rp, anim, title_res)


def _match_native_tab_style(activity, tab):
    try:
        MainTabsHelper = find_class("tw.nekomimi.nekogram.helpers.MainTabsHelper")
        if MainTabsHelper is not None:
            tab.setMainTabsCompact(bool(MainTabsHelper.isMainTabsHideTitleStyle()))
    except Exception:
        pass


def _deselect_native_tabs(activity):
    try:
        tabs = getattr(activity, "tabs", None)
        if tabs is None:
            return
        for i in range(len(tabs)):
            try:
                t = tabs[i]
                if t is not None:
                    t.setSelected(False, False)
            except Exception:
                continue
    except Exception:
        pass


def _restore_tabs_state(activity):
    try:
        tabs_view = _get_tabs_view(activity)
        if tabs_view is None:
            return
        ours = _find_our_tab(tabs_view)
        if ours is not None:
            try:
                ours.setSelected(False, False)
            except Exception:
                pass
        vp = getattr(activity, "viewPager", None)
        if vp is not None:
            try:
                pos = vp.getCurrentPosition()
                activity.selectTab(pos, False)
            except Exception:
                pass
    except Exception:
        pass


def _maybe_add_tab(pl, activity):
    try:
        if not bool(pl.get_setting("show_bottom_tab", True)):
            log("[KPM] bottom tab skipped: disabled in settings")
            return
        tabs_view = _get_tabs_view(activity)
        if tabs_view is None:
            log("[KPM] bottom tab skipped: tabsView not found")
            return
        if _find_our_tab(tabs_view) is not None:
            return
        ctx = None
        try:
            ctx = activity.getContext()
        except Exception:
            pass
        if ctx is None:
            log("[KPM] bottom tab skipped: no context")
            return
        rp = None
        try:
            rp = activity.getResourceProvider()
        except Exception:
            pass
        GTV = find_class("org.telegram.ui.Components.glass.GlassTabView")
        if not GTV:
            log("[KPM] bottom tab skipped: GlassTabView not found")
            return
        anim = None
        try:
            anim_cls = getattr(GTV, "TabAnimation", None)
            if anim_cls is None:
                anim_cls = find_class("org.telegram.ui.Components.glass.GlassTabView$TabAnimation")
            anim = getattr(anim_cls, "SETTINGS", None)
        except Exception:
            anim = None
        if anim is None:
            log("[KPM] bottom tab skipped: TabAnimation.SETTINGS not found")
            return
        title_res = 0
        try:
            from org.telegram.messenger import R as R_tg
            title_res = int(getattr(R_tg.string, "OK", 0) or 0)
        except Exception:
            title_res = 0
        exteraless = _is_exteraless()
        tab = _create_tab(GTV, ctx, rp, anim, title_res)
        if tab is None:
            log("[KPM] bottom tab skipped: no tab factory")
            return
        if exteraless:
            _match_native_tab_style(activity, tab)
        try:
            tab.setText("KPM")
        except Exception:
            pass
        _apply_tab_logo(pl, tab)
        try:
            tab.setTag(TAB_TAG)
        except Exception:
            pass

        OnClickListener = find_class("android.view.View$OnClickListener")
        OnLongClickListener = find_class("android.view.View$OnLongClickListener")

        class TabClickListener(dynamic_proxy(OnClickListener)):
            def onClick(self, v):
                if not exteraless:
                    try:
                        _deselect_native_tabs(activity)
                    except Exception:
                        pass
                    try:
                        tab.setSelected(True, True)
                    except Exception:
                        pass
                try:
                    pl.open_settings_page()
                except Exception as e:
                    log_error(e, context="bottom tab click")

        def _show_tab_options(view):
            try:
                from org.telegram.ui.Components import ItemOptions
                options = ItemOptions.makeOptions(activity, view)
                Runnable = find_class("java.lang.Runnable")
                ApplicationLoader = find_class("org.telegram.messenger.ApplicationLoader")
                context = ApplicationLoader.applicationContext
                resources = context.getResources()
                pkg = context.getPackageName()

                def _res_id(name, fallback_id=0):
                    try:
                        rid = resources.getIdentifier(name, "drawable", pkg)
                        return rid if rid != 0 else fallback_id
                    except Exception:
                        return fallback_id

                runnables = []

                def _add_action(icon_res, text, on_click_fn):
                    class ActionRunnable(dynamic_proxy(Runnable)):
                        def run(self):
                            try:
                                on_click_fn()
                            except Exception as err:
                                log_error(err, context="tab action")
                    r = ActionRunnable()
                    runnables.append(r)
                    options.add(icon_res, text, r)

                _add_action(_res_id("msg_download", 0), _tr("tab_opt_refresh"), lambda: run_on_queue(lambda: pl.refresh_store(force=True, has_bulletin=True, md3_anim=False)))
                _add_action(_res_id("msg_retry", 0), _tr("tab_opt_update"), lambda: pl.open_update_dialog())
                _add_action(_res_id("msg_addbot", 0), _tr("tab_opt_install"), lambda: pl.open_install_dialog())
                _add_action(_res_id("msg_settings", 0), _tr("tab_opt_settings"), lambda: pl.open_settings_page())

                try:
                    options.setGravity(1)
                except Exception:
                    pass

                try:
                    AndroidUtilities = find_class("org.telegram.messenger.AndroidUtilities")
                    if AndroidUtilities is not None:
                        options.translate(0.0, float(-AndroidUtilities.dp(4.0)))
                except Exception:
                    pass

                try:
                    MainTabsUiHelper = find_class("com.exteragram.messenger.utils.ui.MainTabsUiHelper")
                    if MainTabsUiHelper is not None:
                        res_provider = getattr(activity, "resourceProvider", None)
                        options.setScrimViewBackground(MainTabsUiHelper.createMainTabsScrimBackground(res_provider, False))
                except Exception:
                    pass

                try:
                    options.setDismissOnMoveOutside(True)
                except Exception:
                    pass

                try:
                    options.setSwipebackGravity(False, True)
                except Exception:
                    pass

                try:
                    options.setSwipebackCenterHorizontal(True)
                except Exception:
                    pass

                options.show()
                return True
            except Exception as e:
                log_error(e, context="bottom tab long click")
                return False

        class TabLongClickListener(dynamic_proxy(OnLongClickListener)):
            def onLongClick(self, v):
                return _show_tab_options(v)

        try:
            tab.setOnClickListener(TabClickListener())
        except Exception:
            return
        try:
            tab.setOnLongClickListener(TabLongClickListener())
        except Exception:
            pass
        try:
            tab.setLongClickable(True)
        except Exception:
            pass
        try:
            profile_tab = None
            tabs = getattr(activity, "tabs", None)
            if tabs is not None and len(tabs) > 4 and tabs[4] is not None:
                profile_tab = tabs[4]

            insert_idx = -1
            if profile_tab is not None:
                try:
                    insert_idx = tabs_view.indexOfChild(profile_tab)
                except Exception:
                    insert_idx = -1

            if insert_idx < 0:
                child_count = tabs_view.getChildCount()
                if child_count > 0:
                    insert_idx = child_count - 1

            if insert_idx >= 0:
                tabs_view.addView(tab, insert_idx)
            else:
                tabs_view.addView(tab)
        except Exception:
            return
        try:
            tabs_view.addTabToIgnoreClick(tab)
        except Exception:
            pass
        try:
            tabs_view.setViewVisible(tab, True, False)
        except Exception:
            try:
                tab.setVisibility(0)
            except Exception:
                pass
        try:
            known = getattr(pl, "_bottom_tabs", None) or []
            known = [t for t in known if t is not None]
            known.append(tab)
            pl._bottom_tabs = known
        except Exception:
            pass
        log("[KPM] bottom tab added")
    except Exception as e:
        log_error(e, context="bottom tab add")


def _apply_tab_logo(pl, tab):
    try:
        from ..views.settings.icon_selector import _get_icon_drawable
        drawable, _ = _get_icon_drawable(pl, "icon.svg", size_dp=24)
        if drawable is None:
            return
        iv = get_private_field(tab, "imageView")
        if iv is None:
            return
        iv.setImageDrawable(drawable)
        f = tab.getClass().getDeclaredField("tabAnimation")
        f.setAccessible(True)
        f.set(tab, None)
        log("[KPM] bottom tab logo applied")
    except Exception as e:
        log_error(e, context="bottom tab logo")


def remove_bottom_tabs(pl):
    try:
        tabs = getattr(pl, "_bottom_tabs", None) or []
        for tab in tabs:
            try:
                parent = tab.getParent()
                if parent is not None:
                    parent.removeView(tab)
            except Exception:
                continue
        pl._bottom_tabs = []
    except Exception as e:
        log_error(e, context="remove bottom tabs")


def refresh_bottom_tab(pl):
    try:
        if not bool(pl.get_setting("show_bottom_tab", True)):
            remove_bottom_tabs(pl)
            return
        act = None
        try:
            ref = getattr(pl, "_main_tabs_ref", None)
            act = ref() if ref else None
        except Exception:
            act = None
        if act is not None:
            _maybe_add_tab(pl, act)
    except Exception as e:
        log_error(e, context="refresh bottom tab")


def install_main_tabs_hook(pl):
    try:
        class MainTabsHook(MethodHook):
            def __init__(self, plugin):
                self.plugin = plugin

            def after_hooked_method(self, param):
                try:
                    activity = param.thisObject
                    try:
                        self.plugin._main_tabs_ref = weakref.ref(activity)
                    except Exception:
                        pass
                    _maybe_add_tab(self.plugin, activity)
                except Exception as e:
                    log_error(e, context="main tabs createView hook")

        class SelectTabHook(MethodHook):
            def after_hooked_method(self, param):
                try:
                    tabs_view = _get_tabs_view(param.thisObject)
                    if tabs_view is None:
                        return
                    ours = _find_our_tab(tabs_view)
                    if ours is not None:
                        try:
                            ours.setSelected(False, False)
                        except Exception:
                            pass
                except Exception:
                    pass

        class ResumeHook(MethodHook):
            def __init__(self, plugin):
                self.plugin = plugin

            def after_hooked_method(self, param):
                try:
                    activity = param.thisObject
                    try:
                        self.plugin._main_tabs_ref = weakref.ref(activity)
                    except Exception:
                        pass
                    _maybe_add_tab(self.plugin, activity)
                    _restore_tabs_state(activity)
                except Exception as e:
                    log_error(e, context="main tabs onResume hook")

        class VisibleHook(MethodHook):
            def __init__(self, plugin):
                self.plugin = plugin

            def after_hooked_method(self, param):
                try:
                    activity = param.thisObject
                    _restore_tabs_state(activity)
                except Exception as e:
                    log_error(e, context="main tabs onBecomeFullyVisible hook")

        MTA = find_class("org.telegram.ui.MainTabsActivity")
        if MTA:
            pl._track_hooks(pl.hook_all_methods(MTA, "createView", MainTabsHook(pl)))
            pl._track_hooks(pl.hook_all_methods(MTA, "onResume", ResumeHook(pl)))
            pl._track_hooks(pl.hook_all_methods(MTA, "onBecomeFullyVisible", VisibleHook(pl)))
            pl._track_hooks(pl.hook_all_methods(MTA, "selectTab", SelectTabHook()))
            log("[KPM] main tabs hook installed")
        else:
            log("[KPM] MainTabsActivity not found for bottom tab hook")
    except Exception as e:
        log_error(e, context="install_main_tabs_hook")
