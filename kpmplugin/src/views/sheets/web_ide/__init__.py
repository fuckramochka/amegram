"""Modern Web-based IDE BottomSheet and Payload Builder."""

from client_utils import get_last_fragment
from org.telegram.ui.ActionBar import Theme

from ....methods import log, log_error
from .builder import build_archive_payload, build_single_code_payload
from .sheet import show_web_ide_sheet


def show_archive_web_ide(archive_source, title: str = None, target_fragment=None) -> bool:
    """
    Directly opens an archive (.eaf, .zip, .elyx) in the modern Web-based IDE BottomSheet.
    """
    try:
        frag = target_fragment or get_last_fragment()
        if not frag:
            log("[KPM] show_archive_web_ide: target_fragment is None, aborting")
            return False

        activity = frag.getParentActivity() if hasattr(frag, "getParentActivity") else None
        if not activity:
            log("[KPM] show_archive_web_ide: parentActivity is None, aborting")
            return False

        is_dark = Theme.isCurrentThemeDark() if hasattr(Theme, "isCurrentThemeDark") else True
        target_title = title or "Plugin Archive"
        log(f"[KPM] show_archive_web_ide: building payload for {target_title} (is_dark={is_dark})...")

        html_payload = build_archive_payload(archive_source, title=target_title, is_dark=is_dark)
        return show_web_ide_sheet(activity, frag, html_payload, target_title)
    except Exception as ex:
        log_error(ex, context="show_archive_web_ide")
        return False


def show_code_web_ide(code_str: str, title: str = None, lang: str = "python", target_fragment=None) -> bool:
    """
    Directly opens a single source code file in the modern Web-based IDE BottomSheet.
    """
    try:
        frag = target_fragment or get_last_fragment()
        if not frag:
            log("[KPM] show_code_web_ide: target_fragment is None, aborting")
            return False

        activity = frag.getParentActivity() if hasattr(frag, "getParentActivity") else None
        if not activity:
            log("[KPM] show_code_web_ide: parentActivity is None, aborting")
            return False

        is_dark = Theme.isCurrentThemeDark() if hasattr(Theme, "isCurrentThemeDark") else True
        target_title = title or "source.py"
        log(f"[KPM] show_code_web_ide: building payload for {target_title} (lang={lang})...")

        html_payload = build_single_code_payload(code_str, title=target_title, lang=lang, is_dark=is_dark)
        return show_web_ide_sheet(activity, frag, html_payload, target_title)
    except Exception as ex:
        log_error(ex, context="show_code_web_ide")
        return False


__all__ = [
    "show_archive_web_ide",
    "show_code_web_ide",
    "build_archive_payload",
    "build_single_code_payload",
    "show_web_ide_sheet",
]
