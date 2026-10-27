"""Bridge between Java (HotKpmGate / PluginsActivity) and the authentic KPM plugin."""
import sys
from typing import Optional

_kpm_instance = None


def register_kpm_instance(inst):
    global _kpm_instance
    _kpm_instance = inst


def get_kpm():
    global _kpm_instance
    if _kpm_instance is not None:
        return _kpm_instance
    for name, mod in list(sys.modules.items()):
        if name.endswith(".methods") and hasattr(mod, "_kpm_instance"):
            inst = getattr(mod, "_kpm_instance", None)
            if inst is not None:
                _kpm_instance = inst
                return inst
    return None


def is_kpm_available() -> bool:
    return True


def ensure_kpm_loaded():
    inst = get_kpm()
    if inst is not None:
        return inst
    try:
        from com.exteragram.messenger.plugins import PluginsController, PythonPluginsEngine
        controller = PluginsController.getInstance()
        if not controller:
            return None
        p = controller.getPlugin("kangel_plugins_manager")
        if p and not p.loaded:
            PythonPluginsEngine.getInstance().loadPlugin(p)
            return get_kpm()
    except Exception as e:
        try:
            from android_utils import log
            log(f"[KPM_BRIDGE] ensure_kpm_loaded failed: {e}")
        except Exception:
            pass
    return get_kpm()


def open_store() -> bool:
    kpm = ensure_kpm_loaded()
    if kpm and hasattr(kpm, "open_install_dialog"):
        try:
            kpm.open_install_dialog()
            return True
        except Exception as e:
            try:
                from android_utils import log
                log(f"[KPM_BRIDGE] open_store error: {e}")
            except Exception:
                pass
    return False


def open_updates() -> bool:
    kpm = ensure_kpm_loaded()
    if kpm and hasattr(kpm, "open_update_dialog"):
        try:
            kpm.open_update_dialog()
            return True
        except Exception as e:
            try:
                from android_utils import log
                log(f"[KPM_BRIDGE] open_updates error: {e}")
            except Exception:
                pass
    return False


def handle_deep_link(url: str) -> bool:
    if not url:
        return False
    kpm = ensure_kpm_loaded()
    if not kpm:
        return False
    try:
        import urllib.parse
        from client_utils import run_on_queue
        url_str = str(url)
        if "kpm_install" in url_str or "install=" in url_str or "kpm?install=" in url_str:
            parsed = urllib.parse.urlparse(url_str)
            qs = urllib.parse.parse_qs(parsed.query)
            pid = (qs.get("plugin") or qs.get("id") or qs.get("p") or qs.get("install") or [None])[0]
            ver = (qs.get("version") or qs.get("v") or [None])[0]
            if pid:
                run_on_queue(lambda: kpm._handle_deep_link_install(pid, ver))
                return True
        elif "kpm_list" in url_str:
            run_on_queue(lambda: kpm.refresh_store(force=True, md3_anim=False))
            return True
        elif "kpm_faq" in url_str:
            from ElyxPlugins.kangel_plugins_manager.kpmplugin.src.views import open_faq
            open_faq(kpm, 0)
            return True
    except Exception as e:
        try:
            from android_utils import log
            log(f"[KPM_BRIDGE] handle_deep_link error: {e}")
        except Exception:
            pass
    return False
