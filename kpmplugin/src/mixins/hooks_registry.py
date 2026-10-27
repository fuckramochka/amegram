import traceback

from android_utils import log as _log, run_on_ui_thread
from client_utils import get_last_fragment
from base_plugin import MenuItemData, MenuItemType
from com.exteragram.messenger.plugins import PluginsController
from ..methods import log_error


class HooksRegistryMixin:

    def add_drawer_menu_items(self):
        try:
            if self.get_setting("show_drawer_menu", True):
                item_id = self.add_menu_item(MenuItemData(
                    menu_type=MenuItemType.DRAWER_MENU,
                    text="Plugin Manager",
                    icon="msg_addbot",
                    on_click=lambda ctx: self.open_settings_page(),
                ))
                if item_id:
                    self._menu_item_id = item_id
        except Exception as e:
            log_error(e, context="add_drawer_menu_items")

    def _track_hook(self, hook):
        if hook is not None:
            self._kpm_hooks.append(hook)
        return hook

    def _track_hooks(self, hooks):
        if hooks:
            for h in hooks:
                if h is not None:
                    self._kpm_hooks.append(h)
        return hooks

    def _unhook_all(self):
        for h in list(self._kpm_hooks):
            try:
                self.unhook_method(h)
            except Exception as e:
                log_error(e, context="unhook_method")
        self._kpm_hooks.clear()

    def open_settings_page(self, target_setting=None):
        try:
            from com.exteragram.messenger.plugins import PluginsController
            if target_setting:
                try:
                    run_on_ui_thread(lambda: PluginsController.openPluginSettings(self.id, str(target_setting)))
                    return
                except Exception:
                    pass
                try:
                    from ..methods import open_link
                    open_link(f"https://t.me/exteraSettings?p={self.id}&s={target_setting}")
                    return
                except Exception:
                    pass

            try:
                run_on_ui_thread(lambda: PluginsController.openPluginSettings(self.id))
                return
            except Exception:
                pass

            try:
                pc = PluginsController.getInstance()
                get_plugins = getattr(pc, "getPlugins", None)
                plugins_map = get_plugins() if callable(get_plugins) else getattr(pc, "plugins", None)
                java_plugin = plugins_map.get(self.id) if plugins_map else None
                fragment = get_last_fragment()
                if java_plugin and fragment:
                    from com.exteragram.messenger.plugins.ui import PluginSettingsActivity
                    run_on_ui_thread(lambda: fragment.presentFragment(PluginSettingsActivity(java_plugin)))
                    return
            except Exception:
                pass

            try:
                from ..methods import open_link
                open_link(f"https://t.me/exteraSettings?p={self.id}")
            except Exception:
                pass
        except Exception as e:
            log_error(e, context="open_settings_page")

    def add_deeplink_hook(self):
        from ..hooks import install_deeplink_hook
        install_deeplink_hook(self)

    def add_install_sheet_hook(self):
        from ..hooks import install_install_sheet_hook
        install_install_sheet_hook(self)

    def add_plugins_activity_hook(self):
        from ..hooks import install_plugins_activity_hook
        install_plugins_activity_hook(self)

    def add_badge_hook(self):
        from ..hooks import install_badge_hook
        install_badge_hook(self)

    def add_main_tabs_hook(self):
        from ..hooks import install_main_tabs_hook
        install_main_tabs_hook(self)

    def refresh_bottom_tab(self):
        from ..hooks import refresh_bottom_tab as _refresh_bottom_tab
        _refresh_bottom_tab(self)

    def remove_bottom_tabs(self):
        from ..hooks import remove_bottom_tabs as _remove_bottom_tabs
        _remove_bottom_tabs(self)

    def _setup_kpm_file_hook(self):
        from ..hooks import install_kpm_file_hook
        install_kpm_file_hook(self)

    def _setup_settings_header_hook(self):
        from ..hooks import install_settings_header_hook
        install_settings_header_hook(self)

    def _setup_settings_activity_hook(self):
        from ..hooks import install_settings_activity_hook
        install_settings_activity_hook(self)

    def add_on_send_message_hook(self):
        pass

    def process_inline_search(self, adapter, query):
        pass

    def _register_kpm_pill(self):
        enabled = bool(self.get_setting("pill_enabled", True))
        if not enabled:
            return
        try:
            from .. import pill as _pill_mod
            _pill_mod.setup_pill(self)
        except Exception as e:
            log_error(e, context="pill setup")

    def _update_all_pills(self):
        try:
            from .. import pill as _pill_mod
            if bool(self.get_setting("pill_enabled", True)):
                _pill_mod.refresh_pill()
            else:
                _pill_mod.unregister_pill()
        except Exception as e:
            log_error(e, context="update_all_pills")

    def _unregister_kpm_pill(self):
        try:
            from .. import pill as _pill_mod
            _pill_mod.unregister_pill()
        except Exception as e:
            log_error(e, context="unregister_pill")
