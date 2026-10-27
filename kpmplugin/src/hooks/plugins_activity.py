import os

from base_plugin import MethodHook
from hook_utils import find_class

from ..methods import log, log_error, is_beta_code_viewer_enabled


def _resolve_plugin_path(plugin_id: str, pl=None) -> str:
    """Resolve local path for a given plugin ID across known locations and extensions."""
    if not plugin_id:
        return None
    try:
        PluginsController = (
            find_class("com.exteragram.messenger.plugins.PluginsController")
            or find_class("app.exteraless.plugins.PluginsController")
        )
        plugins_dir = None
        if PluginsController:
            try:
                inst = PluginsController.getInstance()
                if inst:
                    engine = inst.getPluginEngine(plugin_id)
                    if engine and hasattr(engine, "getPluginPath"):
                        p = str(engine.getPluginPath(plugin_id))
                        if p and (os.path.isfile(p) or os.path.isdir(p)):
                            return p
                    if hasattr(inst, "getPluginsDir"):
                        pd = inst.getPluginsDir()
                        if pd:
                            plugins_dir = str(pd.getAbsolutePath())
            except Exception:
                pass

        if not plugins_dir and pl and hasattr(pl, "plugins_dir"):
            plugins_dir = pl.plugins_dir

        if not plugins_dir:
            try:
                from org.telegram.messenger import ApplicationLoader
                files_dir = str(ApplicationLoader.getFilesDirFixed().getAbsolutePath())
                plugins_dir = os.path.join(files_dir, "plugins")
            except Exception:
                pass

        if not plugins_dir:
            return None

        candidates = [
            os.path.join(plugins_dir, "ElyxPlugins", f"{plugin_id}.eaf"),
            os.path.join(plugins_dir, "ElyxPlugins", plugin_id),
            os.path.join(plugins_dir, f"{plugin_id}.eaf"),
            os.path.join(plugins_dir, f"{plugin_id}.plugin"),
            os.path.join(plugins_dir, f"{plugin_id}.py"),
            os.path.join(plugins_dir, "ElyxPlugins", f"{plugin_id}.zip"),
            os.path.join(plugins_dir, f"{plugin_id}.zip"),
            os.path.join(plugins_dir, plugin_id),
            os.path.join(plugins_dir, f".temp_{plugin_id}.eaf"),
            os.path.join(plugins_dir, f".temp_{plugin_id}.plugin"),
            os.path.join(plugins_dir, f".temp_{plugin_id}.py"),
        ]
        for c in candidates:
            if os.path.exists(c):
                return c
    except Exception as e:
        log_error(e, context="_resolve_plugin_path")
    return None


def install_plugins_activity_hook(pl):
    try:
        from java import dynamic_proxy

        class PluginsActivityHook(MethodHook):
            def __init__(self, plugin):
                self.plugin = plugin

            def after_hooked_method(self, param):
                try:
                    show_add = self.plugin.get_setting("show_add_button", True)
                    show_update = self.plugin.get_setting("show_update_button", True)
                    log(f"[KPM] PluginsActivity.createView fired, show_add={show_add}, show_update={show_update}")

                    activity = param.thisObject
                    action_bar = activity.actionBar
                    if not action_bar:
                        return
                    menu = action_bar.menu
                    if not menu:
                        return

                    try:
                        if hasattr(menu, 'ids') and menu.ids:
                            existing = [int(x) for x in menu.ids]
                            if show_add and 2 in existing:
                                show_add = False
                            if show_update and 3 in existing:
                                show_update = False
                    except Exception:
                        pass

                    from org.telegram.messenger import R

                    class AddClickListener(dynamic_proxy(find_class("android.view.View$OnClickListener"))):
                        def __init__(self, plugin_instance):
                            super().__init__()
                            self.p = plugin_instance
                        def onClick(self, v):
                            self.p.open_install_dialog()

                    class UpdateClickListener(dynamic_proxy(find_class("android.view.View$OnClickListener"))):
                        def __init__(self, plugin_instance):
                            super().__init__()
                            self.p = plugin_instance
                        def onClick(self, v):
                            self.p.open_update_dialog()

                    if show_add:
                        item = menu.addItem(2, R.drawable.msg_add)
                        item.setOnClickListener(AddClickListener(self.plugin))
                    if show_update:
                        item = menu.addItem(3, R.drawable.msg_retry)
                        item.setOnClickListener(UpdateClickListener(self.plugin))
                except Exception as e:
                    log_error(e, context="PluginsActivity after_hooked_method")

        PluginsActivity = find_class("com.exteragram.messenger.plugins.ui.PluginsActivity")
        if PluginsActivity:
            pl._track_hooks(pl.hook_all_methods(PluginsActivity, "createView", PluginsActivityHook(pl)))
            log("[KPM] PluginsActivity hook installed")
        else:
            log("[KPM] PluginsActivity not found for plugins activity hook")

        # --- Hook openInButton on PluginCell in plugins list ---
        PluginCell = (
            find_class("com.exteragram.messenger.plugins.ui.components.PluginCell")
            or find_class("app.exteraless.plugins.ui.components.PluginCell")
        )
        if PluginCell:
            from android_utils import OnClickListener

            def _get_field(obj, name):
                try:
                    from hook_utils import get_private_field
                    return get_private_field(obj, name)
                except Exception:
                    pass
                try:
                    field = obj.getClass().getDeclaredField(name)
                    field.setAccessible(True)
                    return field.get(obj)
                except Exception:
                    return None

            class PluginCellSetHook(MethodHook):
                def __init__(self, plugin):
                    self.plugin = plugin

                def after_hooked_method(self, param):
                    self._handle(param)

                def afterHookedMethod(self, param):
                    self._handle(param)

                def _handle(self, param):
                    try:
                        cell = param.thisObject
                        if not cell:
                            return

                        args = param.args
                        if not args or len(args) < 1:
                            return

                        p = args[0]
                        if not p:
                            return

                        # Ignore cells from the KPM store catalog (plugin_list.py)
                        if hasattr(p, "getEngine"):
                            try:
                                if str(p.getEngine()) == "kangel_plugins_manager":
                                    return
                            except Exception:
                                pass

                        delegate = args[1] if len(args) > 1 else None
                        if delegate and (hasattr(delegate, "outer") or hasattr(delegate, "pid")):
                            return

                        plugin_id = str(p.getId()) if hasattr(p, "getId") else ""
                        if not plugin_id:
                            return

                        open_in_button = _get_field(cell, "openInButton")
                        if not open_in_button:
                            return

                        beta_viewer = is_beta_code_viewer_enabled(self.plugin)
                        file_path = _resolve_plugin_path(plugin_id, self.plugin)

                        # When beta viewer is enabled and local sources exist, ensure button is visible
                        if beta_viewer and file_path:
                            try:
                                open_in_button.setVisibility(0)  # View.VISIBLE
                            except Exception:
                                pass

                        def on_click(v=None):
                            try:
                                try:
                                    cell.applyClickAnimation(open_in_button)
                                except Exception:
                                    pass

                                cur_beta = is_beta_code_viewer_enabled(self.plugin)
                                if cur_beta:
                                    cur_file = _resolve_plugin_path(plugin_id, self.plugin)
                                    if cur_file:
                                        from ..views.sheets.code_viewer import open_local_file_or_archive
                                        from client_utils import get_last_fragment
                                        frag = get_last_fragment()
                                        file_name = os.path.basename(cur_file)
                                        log(f"[KPM] openInButton clicked for {plugin_id}: opening in code viewer ('{cur_file}')")
                                        if open_local_file_or_archive(cur_file, title=file_name, target_fragment=frag, use_sheet=True):
                                            return

                                # Fallback to default delegate behavior
                                log(f"[KPM] openInButton clicked for {plugin_id}: fallback to openInExternalApp")
                                if delegate and hasattr(delegate, "openInExternalApp"):
                                    delegate.openInExternalApp()
                            except Exception as ce:
                                log_error(ce, context="PluginCell openInButton on_click")

                        open_in_button.setOnClickListener(OnClickListener(on_click))
                    except Exception as e:
                        log_error(e, context="PluginCellSetHook")

            try:
                pl._track_hooks(pl.hook_all_methods(PluginCell, "set", PluginCellSetHook(pl)))
                log("[KPM] PluginCell.set hook installed (openInButton click listener)")
            except Exception as e:
                log_error(e, context="hook PluginCell.set")

    except Exception as e:
        log_error(e, context="install_plugins_activity_hook")
