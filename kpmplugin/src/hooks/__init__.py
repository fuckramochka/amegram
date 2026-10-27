import weakref
import traceback

from base_plugin import MethodHook
from android_utils import run_on_ui_thread, OnClickListener
from client_utils import get_last_fragment, run_on_queue
from hook_utils import find_class
from java import dynamic_proxy, jint
from java.lang import Boolean

from org.telegram.messenger import R as R_tg

from ..methods import log, _tr, _status_label, _parse_markdown, _normalize_requirements

_delayed_runnable_refs = {}


def _hold_runnable(runnable):
    _delayed_runnable_refs[id(runnable)] = runnable
    return runnable


def _release_runnable(runnable):
    try:
        _delayed_runnable_refs.pop(id(runnable), None)
    except Exception:
        pass


from .deeplink import install_deeplink_hook
from .install_sheet import install_install_sheet_hook
from .plugins_activity import install_plugins_activity_hook
from .settings_header import install_settings_header_hook
from .settings_activity import install_settings_activity_hook
from .badges import install_badge_hook
from .kpm_file import install_kpm_file_hook
from .main_tabs import install_main_tabs_hook, remove_bottom_tabs, refresh_bottom_tab
from .activity_result import install_activity_result_hook
from .promo import install_promo_hook
