import os
import traceback

from base_plugin import BasePlugin, MethodHook
from file_utils import get_plugins_dir
from com.exteragram.messenger.plugins import PluginsController
from android_utils import log as _log, run_on_ui_thread
from client_utils import run_on_queue

from .mixins.store_ops import StoreOpsMixin
from .mixins.installer import InstallerMixin
from .mixins.dialogs import DialogsMixin
from .mixins.hooks_registry import HooksRegistryMixin
from .mixins.files import FilesMixin

from .store import Store

__id__ = "kangel_plugins_manager"
try:
    from elyx import metainfo as _metainfo
    __version__ = _metainfo.get("version", "1.6.0")
except Exception:
    __version__ = "1.6.0"

def _get_plugins_dir():
    try:
        controller = PluginsController.getInstance()
        if controller:
            plugins_dir = controller.getPluginsDir()
            if plugins_dir is not None:
                if hasattr(plugins_dir, 'getAbsolutePath'):
                    plugins_dir = plugins_dir.getAbsolutePath()
                else:
                    plugins_dir = str(plugins_dir)
            if plugins_dir and os.path.isdir(plugins_dir):
                _log(f"[KPM] plugins_dir from PluginsController: {plugins_dir}")
                return plugins_dir
    except Exception as e:
        log_error(e, context="error getting plugins_dir from PluginsController")

    for cand in [
        "/data/data/app.amegram/files/plugins/",
        "/data/data/com.exteragram.messenger/files/plugins/",
    ]:
        if os.path.isdir(cand):
            _log(f"[KPM] using real plugins_dir: {cand}")
            return cand

    dir_from_sdk = get_plugins_dir()
    _log(f"[KPM] plugins_dir from get_plugins_dir (SDK): {dir_from_sdk}")
    return dir_from_sdk

PLUGINS_DIR = _get_plugins_dir()


def log(*args, **kwargs):
    try:
        from .methods import _kpm_instance
        if _kpm_instance and not _kpm_instance.get_setting("logs_enabled", True):
            return
    except Exception:
        pass
    try:
        _log(*args, **kwargs)
    except Exception as e:
        try:
            from .methods import log_error
            log_error(e, context="main.log")
        except Exception:
            pass


class KangelPluginsManagerPlugin(
        StoreOpsMixin, InstallerMixin, DialogsMixin,
        HooksRegistryMixin, FilesMixin, BasePlugin):

    def __init__(self):
        super().__init__()
        try:
            from .methods import _set_kpm_instance
            _set_kpm_instance(self)
        except Exception as e:
            try:
                from .methods import log_error
                log_error(e, context="set_kpm_instance in __init__")
            except Exception:
                pass
        self.plugins_dir = PLUGINS_DIR
        stored_mirrors = self.get_setting("enabled_store_mirrors", None)
        valid_mirrors = ["kangel", "github", "codeberg", "gitverse"]
        if stored_mirrors is not None and isinstance(stored_mirrors, (list, tuple)):
            enabled_list = [m for m in stored_mirrors if m in valid_mirrors]
        else:
            enabled_list = list(valid_mirrors)
        if not enabled_list:
            enabled_list = ["kangel"]
        self.store = Store(PLUGINS_DIR, enabled_mirrors=enabled_list)
        self._sticker_cache = {}
        self._badge_stop_event = None
        self._badge_thread = None
        self._sticker_cache_limit = 64
        self.search_listener_hooks = None
        self._kpm_hooks = []
        self._menu_item_id = None

    INSTALL = 0
    UPDATE = 1

    def has_settings(self):
        return True

    @property
    def plugins_list(self):
        return self.store.plugins_list

    @property
    def plugin_names_cache(self):
        return self.store.plugin_names_cache

    def on_plugin_load(self):
        log("[KPM] on_plugin_load start")
        from .methods import log_error
        try:
            self.add_drawer_menu_items()
        except Exception as e:
            log_error(e, context="add_drawer_menu_items")
        try:
            self.add_deeplink_hook()
        except Exception as e:
            log_error(e, context="add_deeplink_hook")
        try:
            self.add_install_sheet_hook()
        except Exception as e:
            log_error(e, context="add_install_sheet_hook")
        try:
            self.add_plugins_activity_hook()
        except Exception as e:
            log_error(e, context="add_plugins_activity_hook")
        try:
            self._setup_kpm_file_hook()
        except Exception as e:
            log_error(e, context="_setup_kpm_file_hook")
        try:
            self._setup_settings_header_hook()
        except Exception as e:
            log_error(e, context="_setup_settings_header_hook")
        try:
            self._setup_settings_activity_hook()
        except Exception as e:
            log_error(e, context="_setup_settings_activity_hook")
        try:
            from .hooks import install_activity_result_hook
            install_activity_result_hook(self)
        except Exception as e:
            log_error(e, context="install_activity_result_hook")
        try:
            from .hooks import install_promo_hook
            install_promo_hook(self)
        except Exception as e:
            log_error(e, context="install_promo_hook")
        try:
            self.add_badge_hook()
        except Exception as e:
            log_error(e, context="add_badge_hook")
        try:
            self.add_main_tabs_hook()
        except Exception as e:
            log_error(e, context="add_main_tabs_hook")
        try:
            from .inline_bot import install_inline_hooks
            install_inline_hooks(self)
        except Exception as e:
            log_error(e, context="install_inline_hooks")
        try:
            from .inline_btns import setup_inline_buttons
            setup_inline_buttons(self)
        except Exception as e:
            log_error(e, context="setup_inline_buttons")
        try:
            self._register_kpm_pill()
        except Exception as e:
            log_error(e, context="_register_kpm_pill")
        try:
            run_on_queue(self.preload_sticker_sets)
        except Exception as e:
            log_error(e, context="preload_sticker_sets")
        try:
            from .views import maybe_show_guide
            maybe_show_guide(self)
        except Exception as e:
            log_error(e, context="maybe_show_guide")
        if self.get_setting("auto_update_on_start", False):
            run_on_queue(self.refresh_store)
        if self.get_setting("auto_update_installed", False):
            run_on_queue(self.update_installed_from_store)
        log("[KPM] on_plugin_load done")

    def on_plugin_unload(self):
        from .methods import log_error
        try:
            self._unhook_all()
        except Exception as e:
            log_error(e, context="unload _unhook_all")
        try:
            if self._menu_item_id:
                self.remove_menu_item(self._menu_item_id)
                self._menu_item_id = None
        except Exception as e:
            log_error(e, context="unload remove_menu_item")
        try:
            self._unregister_kpm_pill()
        except Exception:
            pass
        try:
            self.remove_bottom_tabs()
        except Exception:
            pass
        try:
            if hasattr(self, "_badge_manager") and self._badge_manager:
                self._badge_manager.cleanup()
                self._badge_manager = None
            if self._badge_stop_event is not None:
                self._badge_stop_event.set()
                self._badge_stop_event = None
            self._badge_thread = None
            self._sticker_cache.clear()
        except Exception as e:
            log_error(e, context="unload badge/sticker cleanup")
        try:
            from .methods import _set_kpm_instance
            _set_kpm_instance(None)
        except Exception:
            pass
        try:
            from .views.sheets import dialogs as _dlg_mod
            _dlg_mod.dismiss_all_md3()
            _dlg_mod._delayed_runnable_refs.clear()
        except Exception:
            pass
        try:
            from .hooks import __init__ as _hooks_init_mod
            _hooks_init_mod._delayed_runnable_refs.clear()
        except Exception:
            pass


    def create_settings(self):
        from .views import create_settings
        return create_settings(self)
