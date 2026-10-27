import os
import shutil
import threading
from android_utils import run_on_ui_thread
from client_utils import get_last_fragment
from org.telegram.messenger import AndroidUtilities, ApplicationLoader
from org.telegram.ui.ActionBar import Theme
from ui.settings import Header, Text, Switch, Divider, Custom

from ...methods import _tr, log, log_error, get_log_buffer, clear_log_buffer, get_log_file_path


def _c(color):
    import ctypes
    return ctypes.c_int32(color).value


def _alpha(color, a):
    return _c((a << 24) | (color & 0x00FFFFFF))


def _sget(fn, fallback="?"):
    try:
        v = fn()
        return str(v) if v is not None and str(v) else fallback
    except Exception:
        return fallback


def _detect_client():
    package_name = ""
    try:
        package_name = str(ApplicationLoader.applicationContext.getPackageName()).lower()
    except Exception:
        pass

    bv_str = ""
    try:
        from org.telegram.messenger import BuildVars
        bv_str = str(getattr(BuildVars, "BUILD_VERSION_STRING", "")).lower()
    except Exception:
        pass

    has_exteraless_cls = False
    try:
        from hook_utils import find_class
        has_exteraless_cls = bool(
            find_class("app.exteraless.OpenExteraConfig")
            or find_class("app.exteraless.general.GeneralConfig")
            or find_class("com.exteraless.app.BuildConfig")
        )
    except Exception:
        pass

    has_ayugram_cls = False
    try:
        from hook_utils import find_class
        has_ayugram_cls = bool(find_class("com.radolyn.ayugram.AyuConfig") or find_class("com.radolyn.ayugram.AyuConstants"))
    except Exception:
        pass

    has_exteragram_cls = False
    try:
        from hook_utils import find_class
        has_exteragram_cls = bool(find_class("com.exteragram.messenger.ExteraConfig"))
    except Exception:
        pass

    if "exteraless" in package_name or "exteraless" in bv_str or has_exteraless_cls:
        return "exteraless", "exteraless"
    elif "yumigram" in package_name or "yumigram" in bv_str or "amegram" in package_name or "amegram" in bv_str:
        return "yumigram", "Yumigram"
    elif "ayugram" in package_name or "ayugram" in bv_str or has_ayugram_cls:
        return "ayugram", "AyuGram"
    elif "extera" in package_name or "extera" in bv_str or has_exteragram_cls:
        return "exteragram", "exteraGram"
    return "unknown", "Unknown"


def _get_device_info():
    from android.os import Build
    from hook_utils import find_class

    mfg = _sget(lambda: str(Build.MANUFACTURER))
    mfg = mfg[:1].upper() + mfg[1:] if mfg else "?"
    model = _sget(lambda: str(Build.MODEL))
    device_name = f"{mfg} {model}".strip()

    android_ver = _sget(lambda: str(Build.VERSION.RELEASE))
    sdk_ver = _sget(lambda: str(Build.VERSION.SDK_INT))
    arch = _sget(lambda: str(Build.SUPPORTED_ABIS[0]))

    app_ver = "?"
    try:
        BuildVars = find_class("org.telegram.messenger.BuildVars")
        app_ver = str(BuildVars.BUILD_VERSION_STRING)
    except Exception:
        pass

    package_name = "?"
    try:
        package_name = str(ApplicationLoader.applicationContext.getPackageName())
    except Exception:
        pass

    try:
        from ...methods import __version__ as kpm_ver
    except Exception:
        kpm_ver = "?"

    client_tag, client_name = _detect_client()

    return {
        "device": device_name,
        "android": f"{android_ver} (SDK {sdk_ver})",
        "arch": arch,
        "app": app_ver,
        "package": package_name,
        "kpm": str(kpm_ver),
        "client_name": client_name,
    }


def _make_info_card(ctx, pl):
    try:
        from android.view import Gravity
        from android.widget import LinearLayout, TextView, FrameLayout
        from android.util import TypedValue
        from android.graphics.drawable import GradientDrawable
        from org.telegram.ui.Components import LayoutHelper

        dp = AndroidUtilities.dp
        info = _get_device_info()

        accent = Theme.getColor(Theme.key_featuredStickers_addButton)
        text_black = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText)
        text_gray = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText)
        bg_color = Theme.getColor(Theme.key_windowBackgroundWhite)

        root_card = FrameLayout(ctx)
        root_card.setPadding(0, 0, 0, 0)

        card_bg = GradientDrawable()
        card_bg.setShape(GradientDrawable.RECTANGLE)
        card_bg.setCornerRadius(float(dp(20)))
        card_bg.setColor(bg_color)
        root_card.setBackground(card_bg)

        outer = LinearLayout(ctx)
        outer.setOrientation(LinearLayout.VERTICAL)
        outer.setPadding(dp(16), dp(14), dp(16), dp(14))

        header = LinearLayout(ctx)
        header.setOrientation(LinearLayout.HORIZONTAL)
        header.setGravity(Gravity.CENTER_VERTICAL)

        header_tv = TextView(ctx)
        header_tv.setText(_tr("debug_info_title"))
        header_tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16)
        try:
            header_tv.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"))
        except Exception:
            pass
        header_tv.setTextColor(_c(text_black))
        header.addView(header_tv, LayoutHelper.createLinear(-1, -2, Gravity.CENTER_VERTICAL))

        outer.addView(header, LayoutHelper.createLinear(-1, -2, 0, 0, 0, 12))

        tiles = [
            (_tr("debug_info_device"), info["device"]),
            (_tr("debug_info_android"), info["android"]),
            (_tr("debug_info_arch"), info["arch"]),
            (_tr("debug_info_app"), info["client_name"]),
            (_tr("debug_info_version"), info["app"]),
            ("KPM", info["kpm"]),
        ]

        def make_tile(label, val):
            tile = LinearLayout(ctx)
            tile.setOrientation(LinearLayout.VERTICAL)
            tile.setPadding(dp(10), dp(8), dp(10), dp(8))

            t_bg = GradientDrawable()
            t_bg.setShape(GradientDrawable.RECTANGLE)
            t_bg.setCornerRadius(float(dp(12)))
            t_bg.setColor(_alpha(accent, 0x14))
            tile.setBackground(t_bg)

            lbl_tv = TextView(ctx)
            lbl_tv.setText(label.upper())
            lbl_tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10)
            lbl_tv.setTextColor(_c(text_gray))
            tile.addView(lbl_tv, LayoutHelper.createLinear(-1, -2))

            v_tv = TextView(ctx)
            v_tv.setText(val)
            v_tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13)
            try:
                v_tv.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"))
            except Exception:
                pass
            v_tv.setTextColor(_c(text_black))
            v_tv.setSingleLine(True)
            try:
                from android.text.TextUtils import TruncateAt
                v_tv.setEllipsize(TruncateAt.END)
            except Exception:
                pass
            tile.addView(v_tv, LayoutHelper.createLinear(-1, -2, 0, 2, 0, 0))
            return tile

        for i in range(0, len(tiles), 2):
            row = LinearLayout(ctx)
            row.setOrientation(LinearLayout.HORIZONTAL)
            t1 = make_tile(*tiles[i])
            row.addView(t1, LayoutHelper.createLinear(0, -2, 1.0, 0, 0, 4, 0))
            if i + 1 < len(tiles):
                t2 = make_tile(*tiles[i + 1])
                row.addView(t2, LayoutHelper.createLinear(0, -2, 1.0, 4, 0, 0, 0))
            outer.addView(row, LayoutHelper.createLinear(-1, -2, 0, 0, 0, 6))

        root_card.addView(outer, LayoutHelper.createFrame(-1, -2))
        return root_card
    except Exception as e:
        log_error(e, context="_make_info_card")
        return None


def _get_latest_log_path():
    try:
        path = get_log_file_path()
        if not path:
            files_dir = ApplicationLoader.applicationContext.getFilesDir().getAbsolutePath()
            path = os.path.join(files_dir, "kpm", "latestlog.txt")
        return path
    except Exception:
        return None


def _ensure_log_file_on_disk():
    path = _get_latest_log_path()
    if not path:
        return None
    try:
        os.makedirs(os.path.dirname(path), exist_ok=True)
        if not os.path.exists(path) or os.path.getsize(path) == 0:
            logs = get_log_buffer()
            if logs:
                with open(path, "w", encoding="utf-8", errors="ignore") as f:
                    f.write("\n".join(logs) + "\n")
            else:
                with open(path, "w", encoding="utf-8", errors="ignore") as f:
                    f.write("No logs recorded yet.\n")
    except Exception as e:
        log_error(e, context="_ensure_log_file_on_disk")
    return path


def _get_share_staging_path():
    try:
        try:
            from org.telegram.messenger import AndroidUtilities
            d = AndroidUtilities.getSharingDirectory()
            base = str(d.getAbsolutePath())
            os.makedirs(base, exist_ok=True)
            return os.path.join(base, "kpm_latestlog.txt")
        except Exception:
            pass
        try:
            c = ApplicationLoader.applicationContext.getExternalCacheDir()
            if c is not None:
                base = str(c.getAbsolutePath())
                os.makedirs(base, exist_ok=True)
                return os.path.join(base, "kpm_latestlog.txt")
        except Exception:
            pass
        c = ApplicationLoader.applicationContext.getCacheDir().getAbsolutePath()
        os.makedirs(c, exist_ok=True)
        return os.path.join(str(c), "kpm_latestlog.txt")
    except Exception as e:
        
        log_error(e, context="_get_share_staging_path")
        return None


def _build_share_alert(ctx, file_path):
    from hook_utils import find_class
    ShareAlert = find_class("org.telegram.ui.Components.ShareAlert")
    if ShareAlert is None:
        log("[KPM] ShareAlert class not found")
        return None
    try:
        alert = ShareAlert(ctx, None, None, file_path, None, None, False, None, None, False, False, False, None, None)
        if alert is not None:
            log("[KPM] ShareAlert built in file mode")
            return alert
    except Exception as e:
        log_error(e, context="_build_share_alert file ctor")
    return None


def _send_error(text):
    try:
        from ui.bulletin import BulletinHelper
        run_on_ui_thread(lambda: BulletinHelper.show_error(text))
    except Exception as e:
        log_error(e, context="_send_error")


def _send_latest_log(view=None):
    try:
        log_path = _ensure_log_file_on_disk()
        if not log_path or not os.path.exists(log_path):
            _send_error(_tr("debug_logs_empty"))
            return
        if os.path.getsize(log_path) == 0:
            _send_error(_tr("debug_logs_empty"))
            return
        staging = _get_share_staging_path()
        if not staging:
            _send_error(_tr("debug_send_failed"))
            return
        try:
            shutil.copyfile(log_path, staging)
        except Exception as e:
            log_error(e, context="send_latest_log copy")
            _send_error(_tr("debug_send_failed"))
            return
        frag = get_last_fragment()
        if not frag:
            _send_error(_tr("debug_send_failed"))
            return
        try:
            ctx = frag.getParentActivity()
        except Exception:
            ctx = None
        if ctx is None:
            try:
                ctx = ApplicationLoader.applicationContext
            except Exception:
                _send_error(_tr("debug_send_failed"))
                return
        alert = _build_share_alert(ctx, staging)
        if alert is None:
            _send_error(_tr("debug_send_failed"))
            return
        try:
            run_on_ui_thread(lambda: frag.showDialog(alert))
        except Exception:
            run_on_ui_thread(lambda: alert.show())
    except Exception as e:
        log_error(e, context="_send_latest_log")


def _view_latest_log(view=None):
    try:
        log_path = _ensure_log_file_on_disk()
        log(f"[KPM] view_logs tapped: path={log_path}")
        if not log_path or not os.path.exists(log_path):
            log("[KPM] view_logs: no log file on disk")
            return
        frag = get_last_fragment()
        if not frag:
            log("[KPM] view_logs: no active fragment")
            return

        with open(log_path, "r", encoding="utf-8", errors="replace") as f:
            log_text = f.read()

        if not log_text.strip():
            log_text = "No logs recorded yet.\n"

        from ..sheets.code_viewer import open_in_article_viewer
        if open_in_article_viewer("latestlog.txt", log_text, lang="plain", target_fragment=frag, file_path=log_path):
            log("[KPM] view_logs: opened via ArticleViewer")
            return
        log("[KPM] view_logs: open_in_article_viewer returned False")
    except Exception as e:
        log_error(e, context="_view_latest_log")


def _copy_logs(pl=None, dialog=None):
    try:
        logs = get_log_buffer()
        if not logs:
            log_path = _get_latest_log_path()
            if log_path and os.path.exists(log_path):
                try:
                    with open(log_path, "r", encoding="utf-8", errors="ignore") as f:
                        logs = f.read().splitlines()
                except Exception:
                    pass
        if logs:
            info = _get_device_info()
            header = f"KPM v{info['kpm']} | {info['client_name']} ({info['app']}) | {info['device']} | Android {info['android']}\n" + "-" * 40 + "\n"
            full_text = header + "\n".join(logs)
            AndroidUtilities.addToClipboard(full_text)
            log("[KPM] logs copied to clipboard")
        if dialog:
            dialog.dismiss()
    except Exception as e:
        log_error(e, context="_copy_logs")


def _clear_logs_action(pl=None):
    try:
        clear_log_buffer()
        log_path = _get_latest_log_path()
        if log_path and os.path.exists(log_path):
            try:
                os.remove(log_path)
            except Exception:
                pass
        log("[KPM] logs cleared")
        try:
            from ui.bulletin import BulletinHelper
            run_on_ui_thread(lambda: BulletinHelper.show_success(_tr("debug_logs_cleared")))
        except Exception as e:
            log_error(e, context="_clear_logs bulletin")
    except Exception as e:
        log_error(e, context="_clear_logs")


def build_debug_page(pl=None):
    try:
        fragment = get_last_fragment()
        ctx = fragment.getParentActivity() if fragment else None
        items = []

        if ctx is not None:
            card = _make_info_card(ctx, pl)
            if card is not None:
                items.append(Custom(view=card))
                items.append(Divider())

        items.extend([
            Header(text=_tr("debug_logs_header")),
            Switch(
                key="logs_enabled",
                text=_tr("logs"),
                subtext=_tr("logs_sub"),
                default=True,
                icon="msg_log",
                link_alias="logs",
            ),
            Text(
                text=_tr("debug_view_logs"),
                subtext=_tr("debug_view_logs_sub"),
                icon="msg_sendfile",
                on_click=lambda _: _view_latest_log(),
                link_alias="view_logs",
            ),
            Text(
                text=_tr("debug_send_logs"),
                subtext=_tr("debug_send_logs_sub"),
                icon="msg_send",
                on_click=lambda _: _send_latest_log(),
                link_alias="send_logs",
            ),
            Text(
                text=_tr("debug_copy_logs"),
                subtext=_tr("debug_copy_logs_sub"),
                icon="msg_share",
                on_click=lambda _: _copy_logs(pl),
                link_alias="copy_logs",
            ),
            Text(
                text=_tr("debug_clear_logs"),
                subtext=_tr("debug_clear_logs_sub"),
                icon="msg_clear",
                on_click=lambda _: _clear_logs_action(pl),
                link_alias="clear_logs",
            ),
        ])
        return items
    except Exception as e:
        log_error(e, context="build_debug_page")
        return [Header(text=_tr("debug_menu")), Text(text=f"Error: {e}", red=True)]
