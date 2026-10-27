import weakref

from android_utils import run_on_ui_thread
from base_plugin import MethodHook
from client_utils import get_last_fragment, run_on_queue
from hook_utils import find_class, get_private_field
from java import dynamic_proxy, jint

from .methods import _tr, log, log_error

KPM_PILL_ID = 123456789
_TAG_KEY = jint(0x4B504D50)

_ACTION_INSTALL = 0
_ACTION_UPDATE = 1
_ACTION_REFRESH = 2
_ACTION_SETTINGS = 3

_plugin_ref = [None]
_creating = [False]
_active_pills = weakref.WeakSet()
_creator_refs = []
_menu_runnable_refs = []
_hooks_installed = [False]


_cached_icon_drawable = None


def _get_kpm_icon_drawable():
    global _cached_icon_drawable
    if _cached_icon_drawable is not None:
        return _cached_icon_drawable
    try:
        from elyx import assets
        icon_asset = None
        for key in ["icons/icon.svg", "icons/icon", "icon.svg", "icon"]:
            try:
                icon_asset = assets.get(key)
                if icon_asset is not None:
                    break
            except Exception:
                pass
        if icon_asset is None:
            try:
                icon_asset = getattr(getattr(assets, "icons", None), "icon", None)
            except Exception:
                pass
        if icon_asset is None:
            icon_asset = getattr(assets, "icon", None)
        if icon_asset is not None:
            size = 56
            try:
                from org.telegram.messenger import AndroidUtilities
                size = int(AndroidUtilities.dp(28))
            except Exception:
                pass

            try:
                _cached_icon_drawable = icon_asset.to_svg_drawable(width=size, height=size)
            except Exception:
                try:
                    _cached_icon_drawable = icon_asset.to_svg_drawable(size, size)
                except Exception:
                    try:
                        _cached_icon_drawable = icon_asset.to_svg_drawable()
                    except Exception as e3:
                        log_error(e3, context="pill to_svg_drawable")

            if _cached_icon_drawable is None:
                try:
                    bm = icon_asset.to_svg_bitmap(size, size)
                    if bm is not None:
                        from android.graphics.drawable import BitmapDrawable
                        from org.telegram.messenger import ApplicationLoader
                        _cached_icon_drawable = BitmapDrawable(ApplicationLoader.applicationContext.getResources(), bm)
                except Exception as eb:
                    log_error(eb, context="pill to_svg_bitmap")

            if _cached_icon_drawable is None:
                try:
                    _cached_icon_drawable = icon_asset.to_drawable()
                except Exception as ed:
                    log_error(ed, context="pill to_drawable")
    except Exception as e:
        log_error(e, context="Failed to load pill icon drawable")
    return _cached_icon_drawable


def _get_icon_res():
    try:
        from .inline_icons import get_client_drawable_res
        res = get_client_drawable_res("msg_addbot")
        if res:
            return res
    except Exception as error:
        log_error(error, context="_get_icon_res delegation")
    return 0


def _is_exteraless_pill(pill):
    try:
        return str(pill.getClass().getName()).startswith("app.exteraless.")
    except Exception:
        return False


def _fresh_icon_drawable():
    drawable = _get_kpm_icon_drawable()
    if drawable is None:
        return None
    try:
        state = drawable.getConstantState()
        if state is not None:
            return state.newDrawable().mutate()
    except Exception:
        pass
    return drawable


def _tint_pill_icon(pill, icon_view):
    try:
        from android.graphics import PorterDuff
        from org.telegram.ui.ActionBar import Theme
        if _is_exteraless_pill(pill):
            color = int(pill.getThemedColor(Theme.key_windowBackgroundWhiteBlackText, 0.75))
            icon_view.setColorFilter(jint(color), PorterDuff.Mode.SRC_IN)
        elif not Theme.isCurrentThemeMonet():
            icon_view.setColorFilter(jint(-1), PorterDuff.Mode.SRC_IN)
    except Exception:
        pass


def _is_kpm_pill(pill):
    try:
        return pill.getTag(_TAG_KEY) is not None and int(pill.getTag(_TAG_KEY)) == KPM_PILL_ID
    except Exception:
        return False


def _set_pill_content(pill):
    plugin = _plugin_ref[0]
    if not plugin:
        return
    try:
        text_view = get_private_field(pill, "textView")
        if text_view:
            text_view.setText(_tr("pill_store_count").format(len(plugin.plugins_list or {})))
        icon_view = get_private_field(pill, "iconView")
        if icon_view:
            drawable = _fresh_icon_drawable() if _is_exteraless_pill(pill) else _get_kpm_icon_drawable()
            if drawable is not None:
                icon_view.setImageDrawable(drawable)
                icon_view.setVisibility(0)
                _tint_pill_icon(pill, icon_view)
            else:
                icon_res = _get_icon_res()
                if icon_res:
                    icon_view.setImageResource(jint(icon_res))
    except Exception as error:
        log_error(error, context="pill render")


def _on_click():
    plugin = _plugin_ref[0]
    if not plugin:
        return
    action = int(plugin.get_setting("pill_click_action", _ACTION_INSTALL) or 0)
    if action == _ACTION_UPDATE:
        plugin.open_update_dialog()
    elif action == _ACTION_REFRESH:
        run_on_queue(lambda: plugin.refresh_store(force=True, has_bulletin=True, md3_anim=False))
    elif action == _ACTION_SETTINGS:
        plugin.open_settings_page()
    else:
        plugin.open_install_dialog()


def _show_menu(pill):
    plugin = _plugin_ref[0]
    if not plugin:
        return
    try:
        from org.telegram.ui.Components import ItemOptions

        fragment = get_last_fragment()
        if not fragment:
            return
        options = ItemOptions.makeOptions(fragment, pill)
        Runnable = find_class("java.lang.Runnable")
        ApplicationLoader = find_class("org.telegram.messenger.ApplicationLoader")
        context = ApplicationLoader.applicationContext
        resources = context.getResources()
        package = context.getPackageName()

        def drawable(name, fallback="msg_settings"):
            result = int(resources.getIdentifier(name, "drawable", package))
            return jint(result or int(resources.getIdentifier(fallback, "drawable", package)))

        class ActionRunnable(dynamic_proxy(Runnable)):
            def __init__(self, action):
                super().__init__()
                self.action = action

            def run(self):
                plugin.set_setting("pill_click_action", self.action)
                _on_click()

        actions = (
            (_ACTION_INSTALL, "pill_action_install", "msg_download"),
            (_ACTION_UPDATE, "pill_action_update", "msg_retry"),
            (_ACTION_REFRESH, "pill_action_refresh", "msg_retry"),
            (_ACTION_SETTINGS, "pill_action_settings", "msg_settings"),
        )
        refs = []
        for action, text_key, icon in actions:
            runnable = ActionRunnable(action)
            refs.append(runnable)
            options.add(drawable(icon), _tr(text_key), runnable)

        options.addGap()

        class PillStackSettingsRunnable(dynamic_proxy(Runnable)):
            def run(self):
                try:
                    activity = find_class("com.exteragram.messenger.pillstack.ui.PillStackPreferencesActivity")
                    current = get_last_fragment()
                    if activity and current:
                        current.presentFragment(activity())
                except Exception as error:
                    log_error(error, context="pill stack settings")

        stack_settings = PillStackSettingsRunnable()
        refs.append(stack_settings)
        options.add(drawable("msg_settings"), "Pill Stack", stack_settings)
        _menu_runnable_refs[:] = refs
        options.setDrawScrim(False).setDimAlpha(jint(0)).show()
    except Exception as error:
        log_error(error, context="pill menu")


def _install_dispatch_hooks(plugin, weather_pill):
    if _hooks_installed[0]:
        return

    class GetIdHook(MethodHook):
        def before_hooked_method(self, param):
            if _creating[0]:
                param.thisObject.setTag(_TAG_KEY, jint(KPM_PILL_ID))
            if _is_kpm_pill(param.thisObject):
                _active_pills.add(param.thisObject)
                param.setResult(jint(KPM_PILL_ID))

    class InteractionHook(MethodHook):
        def before_hooked_method(self, param):
            if not _is_kpm_pill(param.thisObject):
                return
            _active_pills.add(param.thisObject)
            method = param.method.getName()
            if method == "onPillClicked":
                _on_click()
                param.setResult(None)
            elif method == "onPillLongClicked":
                run_on_ui_thread(lambda: _show_menu(param.thisObject))
                param.setResult(True)
            elif method in ("onUpdateData", "setData"):
                _set_pill_content(param.thisObject)
                param.setResult(None)

    class ColorsHook(MethodHook):
        def after_hooked_method(self, param):
            pill = param.thisObject
            if not _is_kpm_pill(pill) or not _is_exteraless_pill(pill):
                return
            try:
                icon_view = get_private_field(pill, "iconView")
                if icon_view:
                    _tint_pill_icon(pill, icon_view)
            except Exception as error:
                log_error(error, context="pill colors")

    plugin._track_hooks(plugin.hook_all_methods(weather_pill, "getPillId", GetIdHook()))
    plugin._track_hooks(plugin.hook_all_methods(weather_pill, "onPillClicked", InteractionHook()))
    plugin._track_hooks(plugin.hook_all_methods(weather_pill, "onPillLongClicked", InteractionHook()))
    plugin._track_hooks(plugin.hook_all_methods(weather_pill, "onUpdateData", InteractionHook()))
    plugin._track_hooks(plugin.hook_all_methods(weather_pill, "setData", InteractionHook()))
    try:
        plugin._track_hooks(plugin.hook_all_methods(weather_pill, "updateColors", ColorsHook()))
    except Exception as error:
        log_error(error, context="pill colors hook")
    _hooks_installed[0] = True


def _activate():
    PillStackConfig = find_class("com.exteragram.messenger.pillstack.core.PillStackConfig")
    if not PillStackConfig:
        return
    try:
        active = PillStackConfig.activePills
        if not any(int(active.get(i)) == KPM_PILL_ID for i in range(active.size())):
            active.add(jint(KPM_PILL_ID))
            PillStackConfig.savePillsLayout()
        PillStackConfig.notifySettingsChanged()
    except Exception as error:
        log_error(error, context="pill activation")


def setup_pill(plugin):
    if not bool(plugin.get_setting("pill_enabled", True)):
        return False
    try:
        PillRegistry = find_class("com.exteragram.messenger.pillstack.core.PillRegistry")
        PillInfo = find_class("com.exteragram.messenger.pillstack.core.PillRegistry$PillInfo")
        PillCreator = find_class("com.exteragram.messenger.pillstack.core.PillRegistry$PillCreator")
        WeatherPill = find_class("com.exteragram.messenger.pillstack.ui.pills.weather.WeatherPill")
        if not all((PillRegistry, PillInfo, PillCreator, WeatherPill)):
            log(f"[KPM] pill classes missing: registry={PillRegistry is not None}, info={PillInfo is not None}, creator={PillCreator is not None}, weather={WeatherPill is not None}")
            return False
        _plugin_ref[0] = plugin
        _install_dispatch_hooks(plugin, WeatherPill)

        class Creator(dynamic_proxy(PillCreator)):
            def create(self, context, resources_provider):
                try:
                    _creating[0] = True
                    pill = WeatherPill(context, resources_provider)
                    pill.setTag(_TAG_KEY, jint(KPM_PILL_ID))
                    _active_pills.add(pill)
                    _set_pill_content(pill)
                    return pill
                except Exception as error:
                    log_error(error, context="pill create")
                    return None
                finally:
                    _creating[0] = False

        creator = Creator()
        info = PillInfo(
            jint(KPM_PILL_ID), _tr("pill_title"), jint(_get_icon_res()),
            jint(-8695125), jint(-10801024), creator,
        )
        _creator_refs[:] = [creator, info]
        PillRegistry.register(info)
        _install_pill_stack_icon_hook(plugin)
        run_on_ui_thread(_activate)
        log(f"[KPM] pill registered id={KPM_PILL_ID}")
        return True
    except Exception as error:
        log_error(error, context="pill setup")
        return False


def unregister_pill():
    try:
        PillRegistry = find_class("com.exteragram.messenger.pillstack.core.PillRegistry")
        if PillRegistry:
            PillRegistry.unregister(jint(KPM_PILL_ID))
    except Exception as error:
        log_error(error, context="pill unregister")
    finally:
        _active_pills.clear()
        _creator_refs.clear()
        _menu_runnable_refs.clear()
        _plugin_ref[0] = None


def _install_exteraless_pill_stack_icon_hook(plugin):
    adapter = find_class("app.exteraless.pillstack.PillStackSettingsActivity$ListAdapter")
    if adapter is None:
        return False

    class BindHook(MethodHook):
        def after_hooked_method(self, param):
            try:
                from org.telegram.ui.Cells import TextCell
                cell = param.args[0].itemView
                if not isinstance(cell, TextCell):
                    return
                if str(cell.textView.getText() or "") != str(_tr("pill_title")):
                    return
                drawable = _fresh_icon_drawable()
                if drawable is not None:
                    cell.imageView.setImageDrawable(drawable)
            except Exception as error:
                log_error(error, context="exteraless pill stack icon")

    plugin._track_hooks(plugin.hook_all_methods(adapter, "onBindViewHolder", BindHook()))
    log("[KPM] exteraless pill stack icon hook installed")
    return True


def _install_pill_stack_icon_hook(plugin):
    try:
        if _install_exteraless_pill_stack_icon_hook(plugin):
            return
    except Exception as e:
        log_error(e, context="exteraless pill stack icon hook")
    try:
        PSA = find_class("com.exteragram.messenger.pillstack.ui.PillStackPreferencesActivity")
        if PSA is None:
            log("[KPM] PillStackPreferencesActivity not found for icon hook")
            return
        ArrayList = find_class("java.util.ArrayList")
        UniversalAdapter = find_class("org.telegram.ui.Components.UniversalAdapter")
        if ArrayList is None or UniversalAdapter is None:
            return

        class FillItemsHook(MethodHook):
            def after_hooked_method(self, param):
                try:
                    items = param.args[0]
                    if items is None:
                        return
                    for i in range(items.size()):
                        item = items.get(i)
                        if item is None:
                            continue
                        try:
                            if int(item.id) != KPM_PILL_ID:
                                continue
                        except Exception:
                            continue
                        _patch_pill_stack_item(item)
                        break
                except Exception as e:
                    log_error(e, context="pill stack icon hook FillItemsHook")

        method = PSA.getClass().getDeclaredMethod("fillItems", ArrayList, UniversalAdapter)
        method.setAccessible(True)
        plugin._track_hook(plugin.hook_method(method, FillItemsHook()))
        log("[KPM] pill stack icon hook installed")
    except Exception as e:
        log_error(e, context="pill stack icon hook setup")


def _patch_pill_stack_item(item):
    try:
        from org.telegram.ui.Cells import TextCell

        Callback = find_class("org.telegram.messenger.Utilities$Callback")
        if Callback is None:
            return

        original_bind = item.bind

        class IconBind(dynamic_proxy(Callback)):
            def run(self, view):
                try:
                    if original_bind is not None:
                        original_bind.run(view)
                    if isinstance(view, TextCell):
                        drawable = _get_kpm_icon_drawable()
                        if drawable is not None:
                            view.imageView.setImageDrawable(drawable)
                            log("[KPM] pill stack icon replaced with SVG")
                        else:
                            log("[KPM] pill stack SVG drawable is None, keeping original")
                except Exception as e:
                    log_error(e, context="pill stack bind")

        item.bind = IconBind()
        log("[KPM] pill stack item patched")
    except Exception as e:
        log_error(e, context="pill stack patch")


def refresh_pill():
    for pill in list(_active_pills):
        _set_pill_content(pill)
