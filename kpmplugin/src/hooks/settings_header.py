from base_plugin import MethodHook
from hook_utils import find_class

from ..methods import log, log_error, _tr

_cached_git_drawable = None


def _get_git_drawable():
    global _cached_git_drawable
    try:
        if _cached_git_drawable is None:
            from elyx import assets
            from org.telegram.messenger import AndroidUtilities, ApplicationLoader
            from android.graphics.drawable import BitmapDrawable
            size = int(AndroidUtilities.dp(24))

            asset = None
            try:
                asset = assets.icons.git
            except Exception:
                pass
            if asset is None:
                try:
                    asset = assets.get("icons/git.svg")
                except Exception:
                    pass
            if asset is None:
                try:
                    asset = assets.get("icons/git")
                except Exception:
                    pass

            if asset is not None:
                try:
                    _cached_git_drawable = asset.to_svg_drawable(width=size, height=size)
                except Exception:
                    try:
                        bm = asset.to_svg_bitmap(size, size)
                        if bm is not None:
                            _cached_git_drawable = BitmapDrawable(ApplicationLoader.applicationContext.getResources(), bm)
                    except Exception:
                        try:
                            _cached_git_drawable = asset.to_svg_drawable()
                        except Exception:
                            pass

        if _cached_git_drawable is not None:
            try:
                cs = _cached_git_drawable.getConstantState()
                if cs is not None:
                    d = cs.newDrawable().mutate()
                    d.clearColorFilter()
                    return d
            except Exception:
                pass
            try:
                d = _cached_git_drawable.mutate()
                d.clearColorFilter()
                return d
            except Exception:
                return _cached_git_drawable
    except Exception as e:
        log_error(e, context="_get_git_drawable")
    return _cached_git_drawable


def install_settings_header_hook(pl):
    try:
        class SettingsHeaderHook(MethodHook):
            def __init__(self, plugin):
                self.plugin = plugin

            def after_hooked_method(self, param):
                try:
                    activity = param.thisObject
                    items = param.args[0]
                    if not items or items.size() == 0:
                        return

                    plugin_obj = None
                    try:
                        f = activity.getClass().getDeclaredField("plugin")
                        f.setAccessible(True)
                        plugin_obj = f.get(activity)
                    except Exception:
                        pass
                    if not plugin_obj:
                        return
                    pid = str(plugin_obj.getId()) if hasattr(plugin_obj, 'getId') else ""
                    if pid != "kangel_plugins_manager":
                        return

                    try:
                        for i in range(items.size()):
                            it = items.get(i)
                            if not it:
                                continue
                            si = getattr(it, "settingItem", None)
                            alias = ""
                            if si:
                                try:
                                    if hasattr(si, "getLinkAlias"):
                                        alias = str(si.getLinkAlias() or "")
                                    elif hasattr(si, "linkAlias"):
                                        alias = str(si.linkAlias or "")
                                except Exception:
                                    pass
                            txt = str(it.text or "")
                            if alias == "source_code" or txt == _tr("source_code"):
                                d = _get_git_drawable()
                                if d:
                                    try:
                                        it.object = d
                                    except Exception:
                                        pass
                                    try:
                                        it.iconResId = 0
                                    except Exception:
                                        pass
                                    try:
                                        it.accent = False
                                    except Exception:
                                        pass
                                    try:
                                        it.red = False
                                    except Exception:
                                        pass
                                    try:
                                        it.iconColor = None
                                    except Exception:
                                        pass
                                    log("[KPM] successfully replaced source_code icon with git.svg")
                    except Exception as e:
                        log_error(e, context="replace source_code icon")

                    try:
                        f2 = activity.getClass().getDeclaredField("createSubFragmentCallback")
                        f2.setAccessible(True)
                        if f2.get(activity) is not None:
                            return
                    except Exception:
                        pass

                    context = activity.getContext()
                    header = _create_settings_header(context, self.plugin)
                    if header:
                        from com.exteragram.messenger.plugins.models import HeaderSetting
                        from org.telegram.ui.Components import UItem
                        item = UItem.asCustom(header)
                        item.settingItem = HeaderSetting("kpm_header")
                        try:
                            item.setTransparent(True)
                        except Exception:
                            pass
                        items.add(0, item)
                        items.add(1, UItem.asShadow())
                except Exception as e:
                    log_error(e, context="settings header hook after_hooked_method")

        PSA = find_class("com.exteragram.messenger.plugins.ui.PluginSettingsActivity")
        if PSA:
            pl._track_hooks(pl.hook_all_methods(PSA, "fillItems", SettingsHeaderHook(pl)))
            log("[KPM] settings header hook installed")
    except Exception as e:
        log_error(e, context="install_settings_header_hook")


def _create_settings_header(context, plugin):
    try:
        from android.util import TypedValue
        from android.view import Gravity
        from android.widget import FrameLayout, LinearLayout, TextView
        from org.telegram.messenger import AndroidUtilities
        from org.telegram.ui.ActionBar import Theme
        from org.telegram.ui.Components import BackupImageView, LayoutHelper

        container = FrameLayout(context)
        main_layout = LinearLayout(context)
        main_layout.setOrientation(LinearLayout.HORIZONTAL)
        main_layout.setGravity(Gravity.CENTER_VERTICAL)
        main_layout.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(20), AndroidUtilities.dp(20), AndroidUtilities.dp(20))

        imageView = BackupImageView(context)
        imageView.setRoundRadius(0)
        try:
            from ..main import __id__
            icon_str = plugin.store.plugins_list.get("kangel_plugins_manager", {}).get("icon", "") if isinstance(plugin.store.plugins_list.get("kangel_plugins_manager"), dict) else ""
            if not icon_str:
                icon_str = "Kangelcons_by_fStikBot/5"
            parts = icon_str.split("/")
            if len(parts) == 2:
                plugin._load_sticker_async(imageView, parts[0], int(parts[1]), f"header_{parts[0]}")
        except Exception:
            pass
        main_layout.addView(imageView, LayoutHelper.createLinear(72, 72, 0, 0, 16, 0))

        text_container = LinearLayout(context)
        text_container.setOrientation(LinearLayout.VERTICAL)
        text_container.setGravity(Gravity.CENTER_VERTICAL)

        title = TextView(context)
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
        title.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM))
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18)
        try:
            from ..methods import __version__
            title.setText(f"Kangel Plugins Manager {__version__}")
        except Exception:
            title.setText("Kangel Plugins Manager")
        title.setSingleLine(True)
        title.setGravity(Gravity.START)
        text_container.addView(title, LayoutHelper.createLinear(-1, -2, 0, 0, 4, 0))

        subtitle = TextView(context)
        subtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText))
        subtitle.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_REGULAR))
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13)
        try:
            subtitle.setText(_tr("settings_subtitle"))
        except Exception:
            subtitle.setText("Plugin Manager that just works")
        subtitle.setGravity(Gravity.START)
        text_container.addView(subtitle, LayoutHelper.createLinear(-1, -2, 0, 0, 0, 0))

        main_layout.addView(text_container, LayoutHelper.createLinear(0, -2, 1.0, Gravity.CENTER_VERTICAL, 0, 0, 0, 0))
        container.addView(main_layout, LayoutHelper.createFrame(-1, -2, Gravity.TOP | Gravity.START, 0, 0, 0, 0))
        return container
    except Exception as e:
        log_error(e, context="_create_settings_header")
        return None
