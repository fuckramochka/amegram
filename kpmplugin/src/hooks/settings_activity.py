from base_plugin import MethodHook
from android_utils import run_on_ui_thread
from client_utils import get_last_fragment
from hook_utils import find_class
from java import jint

from ..methods import log, log_error

_SETTINGS_ITEM_ID = 880100
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
            except Exception as e1:
                log_error(e1, context="to_svg_drawable(w,h)")
                try:
                    _cached_icon_drawable = icon_asset.to_svg_drawable(size, size)
                except Exception as e2:
                    log_error(e2, context="to_svg_drawable(s,s)")
                    try:
                        _cached_icon_drawable = icon_asset.to_svg_drawable()
                    except Exception as e3:
                        log_error(e3, context="to_svg_drawable()")

            if _cached_icon_drawable is None:
                try:
                    bm = icon_asset.to_svg_bitmap(size, size)
                    if bm is not None:
                        from android.graphics.drawable import BitmapDrawable
                        from org.telegram.messenger import ApplicationLoader
                        _cached_icon_drawable = BitmapDrawable(ApplicationLoader.applicationContext.getResources(), bm)
                except Exception as eb:
                    log_error(eb, context="to_svg_bitmap")

            if _cached_icon_drawable is None:
                try:
                    _cached_icon_drawable = icon_asset.to_drawable()
                except Exception as ed:
                    log_error(ed, context="to_drawable")
    except Exception as e:
        log_error(e, context="Failed to load icon drawable")
    return _cached_icon_drawable


def install_settings_activity_hook(pl):
    try:
        _get_kpm_icon_drawable()

        SA = find_class("org.telegram.ui.SettingsActivity")
        if SA is None:
            log("[KPM] SettingsActivity not found for settings hook")
            return

        ArrayList = find_class("java.util.ArrayList")
        UniversalAdapter = find_class("org.telegram.ui.Components.UniversalAdapter")
        SettingCellFactory = find_class("org.telegram.ui.SettingsActivity$SettingCell$Factory")

        if ArrayList is None or UniversalAdapter is None:
            return

        class FillItemsHook(MethodHook):
            def __init__(self, plugin):
                self.plugin = plugin

            def after_hooked_method(self, param):
                try:
                    log("[KPM] SettingsActivity.fillItems hooked fired")
                    if not self.plugin.get_setting("show_settings_button", True):
                        log("[KPM] fillItems: show_settings_button disabled, skip")
                        return

                    items = param.args[0]
                    if items is None or items.size() == 0:
                        log("[KPM] fillItems: items empty, skip")
                        return

                    for i in range(items.size()):
                        item = items.get(i)
                        try:
                            if item is not None and int(item.id) == _SETTINGS_ITEM_ID:
                                log("[KPM] fillItems: item already present, skip")
                                return
                        except Exception:
                            pass

                    extera_idx = -1
                    for i in range(items.size()):
                        item = items.get(i)
                        try:
                            if item is not None and int(item.id) == -1:
                                extera_idx = i
                                break
                        except Exception:
                            pass

                    if extera_idx < 0:
                        log("[KPM] fillItems: no item with id=-1 found, cannot insert")
                        return

                    if SettingCellFactory is None:
                        log("[KPM] fillItems: SettingCellFactory is None, skip")
                        return

                    from org.telegram.messenger import R as R_tg
                    icon_id = 0
                    try:
                        icon_id = int(R_tg.drawable.msg_plugins)
                    except Exception:
                        pass

                    of_method = None
                    for m in SettingCellFactory.getClass().getDeclaredMethods():
                        try:
                            if m.getName() == "of" and len(m.getParameterTypes()) == 5:
                                of_method = m
                                break
                        except Exception:
                            pass

                    if of_method is None:
                        log("[KPM] fillItems: SettingCell.Factory.of(5) not found, skip")
                        return

                    of_method.setAccessible(True)

                    _ICON_BG = jint(-8630785)
                    label = "Kangel Plugins Manager"

                    kpm_item = of_method.invoke(
                        None,
                        [jint(_SETTINGS_ITEM_ID), _ICON_BG, _ICON_BG, jint(icon_id), label]
                    )
                    if kpm_item is None:
                        log("[KPM] fillItems: SettingCellFactory.of returned None")
                        return

                    items.add(extera_idx + 1, kpm_item)
                    log("[KPM] fillItems: item added at index " + str(extera_idx + 1))
                except Exception as e:
                    log_error(e, context="settings activity FillItemsHook")

        class OnClickHook(MethodHook):
            def before_hooked_method(self, param):
                try:
                    uItem = param.args[0]
                    if uItem is None:
                        return
                    if int(uItem.id) != _SETTINGS_ITEM_ID:
                        return

                    param.setResult(None)

                    def open():
                        try:
                            frag = get_last_fragment()
                            from com.exteragram.messenger.plugins import PluginsController
                            from com.exteragram.messenger.plugins.ui import PluginSettingsActivity
                            plugin_obj = PluginsController.getInstance().plugins.get(pl.id)
                            if plugin_obj and frag:
                                frag.presentFragment(PluginSettingsActivity(plugin_obj))
                        except Exception as e:
                            log_error(e, context="settings activity open")

                    run_on_ui_thread(open)
                except Exception as e:
                    log_error(e, context="settings activity OnClickHook")

        class BindViewHook(MethodHook):
            def after_hooked_method(self, param):
                try:
                    uItem = param.args[1]
                    view = param.args[0]
                    icon_view = view.getIconView()
                    if icon_view is None:
                        return
                    if uItem is not None and int(uItem.id) == _SETTINGS_ITEM_ID:
                        drawable = _get_kpm_icon_drawable()
                        if drawable is not None:
                            icon_view.setImageDrawable(drawable)
                        from android.graphics import PorterDuff
                        from org.telegram.ui.ActionBar import Theme
                        if not Theme.isCurrentThemeMonet():
                            icon_view.setColorFilter(jint(-1), PorterDuff.Mode.SRC_IN)
                except Exception as e:
                    log_error(e, context="settings activity BindViewHook")

        pl._track_hooks(pl.hook_all_methods(SA, "fillItems", FillItemsHook(pl)))
        pl._track_hooks(pl.hook_all_methods(SA, "onClick", OnClickHook()))

        if SettingCellFactory is not None:
            pl._track_hooks(pl.hook_all_methods(SettingCellFactory, "bindView", BindViewHook()))

        log("[KPM] Settings activity hook installed")
    except Exception as e:
        log_error(e, context="settings activity hook")
