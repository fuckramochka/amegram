import os

from android_utils import OnClickListener
from client_utils import get_last_fragment
from hook_utils import find_class
from java import dynamic_proxy, jlong

from ..methods import log, log_error, _tr

_CLASS_CACHE = {}
_R_BASES = ("org.telegram.messenger.R", "org.telegram.messenger.C2856R", "org.telegram.messenger.C2818R")
_OPENIN_ICON_NAMES = ("msg_openin",)


def _cached_class(name):
    cls = _CLASS_CACHE.get(name)
    if cls is None:
        cls = find_class(name)
        if cls is not None:
            _CLASS_CACHE[name] = cls
    return cls


def _field(target, name):
    current = target.getClass() if target else None
    while current:
        try:
            f = current.getDeclaredField(name)
            f.setAccessible(True)
            return f.get(target)
        except Exception:
            current = current.getSuperclass()
    return None


def _themed_color(sheet, key, fallback=None):
    try:
        if sheet and hasattr(sheet, "getThemedColor"):
            res = sheet.getThemedColor(key)
            if res is not None and int(res) != 0:
                return int(res)
    except Exception:
        pass
    try:
        Theme = _cached_class("org.telegram.ui.ActionBar.Theme")
        if Theme:
            return int(Theme.getColor(key))
    except Exception:
        pass
    return fallback


def _is_trusted(sheet, install_params):
    params_obj = install_params or _field(sheet, "params")
    if not params_obj:
        return False
    for getter in ("getTrusted", "isTrusted"):
        if hasattr(params_obj, getter):
            try:
                return bool(getattr(params_obj, getter)())
            except Exception:
                continue
    for attr in ("trusted",):
        if hasattr(params_obj, attr):
            val = getattr(params_obj, attr)
            try:
                return bool(val() if callable(val) else val)
            except Exception:
                continue
    return False


def _res_id(name, kind="drawable"):
    try:
        from org.telegram.messenger import R
        holder = getattr(R, kind, None)
        if holder:
            val = getattr(holder, name, None)
            if val:
                return int(val)
    except Exception:
        pass
    Class = _cached_class("java.lang.Class")
    for base in _R_BASES:
        for holder_name in (base + "$" + kind, base):
            holder = _cached_class(holder_name)
            if holder is None and Class is not None and "$" in holder_name:
                try:
                    holder = Class.forName(holder_name)
                except Exception:
                    holder = None
            if holder is None:
                continue
            if "$" not in holder_name:
                try:
                    holder = getattr(holder, kind)
                except Exception:
                    continue
            try:
                value = int(getattr(holder, name))
            except Exception:
                continue
            if value:
                return value
    return 0


def _load_icon(context, names):
    for name in names:
        res_id = _res_id(name)
        if not res_id:
            log(f"[KPM] drawable '{name}' not found in R classes")
            continue
        drawable = None
        ContextCompat = _cached_class("androidx.core.content.ContextCompat")
        if ContextCompat:
            try:
                drawable = ContextCompat.getDrawable(context, int(res_id))
            except Exception as e:
                log_error(e, context=f"ContextCompat.getDrawable({name})")
        if drawable is None:
            try:
                drawable = context.getResources().getDrawable(int(res_id))
            except Exception as e:
                log_error(e, context=f"Resources.getDrawable({name})")
        if drawable is not None:
            try:
                drawable = drawable.mutate()
            except Exception:
                pass
            log(f"[KPM] drawable '{name}' resolved as 0x{int(res_id):x}")
            return drawable
    return None


def _is_frame_top_end(view):
    try:
        lp = view.getLayoutParams()
        if not lp:
            return False
        if str(lp.getClass().getName()) != "android.widget.FrameLayout$LayoutParams":
            return False
        gravity = int(lp.gravity)
        return (gravity & 5) == 5 and (gravity & 0x30) == 0x30
    except Exception:
        return False


def _find_open_in(view):
    if not view or not hasattr(view, "getChildCount"):
        return None
    for i in range(view.getChildCount()):
        child = view.getChildAt(i)
        if child is None:
            continue
        if isinstance(child, _cached_class("android.widget.ImageView")) and _is_frame_top_end(child):
            return child
    return None


def _style_button(btn, icon_color, Theme, AndroidUtilities):
    try:
        ScaleStateListAnimator = _cached_class("org.telegram.ui.Components.ScaleStateListAnimator")
        if ScaleStateListAnimator:
            ScaleStateListAnimator.apply(btn, 0.15, 1.5)
    except Exception:
        pass
    PorterDuffColorFilter = _cached_class("android.graphics.PorterDuffColorFilter")
    PorterDuff = _cached_class("android.graphics.PorterDuff")
    if PorterDuffColorFilter and PorterDuff:
        try:
            btn.setColorFilter(PorterDuffColorFilter(icon_color, PorterDuff.Mode.MULTIPLY))
        except Exception:
            pass
    ImageView = _cached_class("android.widget.ImageView")
    btn.setScaleType(ImageView.ScaleType.CENTER)
    btn.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector), 1, AndroidUtilities.dp(20)))


def _on_local_analysis_click(sheet, plugin, file_path_str, plugin_id_raw):
    try:
        from ..views.sheets.analysis_sheet import PluginAnalysisSheet
        curr_fp = file_path_str or _params_file_path(sheet)
        rp = _field(sheet, "resourcesProvider")
        PluginAnalysisSheet(sheet.getContext(), curr_fp, plugin_id_raw, rp).show()
    except Exception as e:
        log_error(e, context="_on_local_analysis_click")


def _build_open_in_button(sheet, file_path_str, plugin_id_raw, icon_color, Theme, AndroidUtilities, LayoutHelper):
    context = sheet.getContext()
    ImageView = _cached_class("android.widget.ImageView")
    btn = ImageView(context)
    btn.setTag("kpm_open_in_btn")
    drawable = _load_icon(context, _OPENIN_ICON_NAMES)
    if drawable is not None:
        btn.setImageDrawable(drawable)
    else:
        log("[KPM] open-in button without drawable")
    _style_button(btn, icon_color, Theme, AndroidUtilities)
    btn.setOnClickListener(OnClickListener(lambda v: _open_code_click(sheet, file_path_str, plugin_id_raw)))
    return btn

def _open_code_click(sheet, file_path_str, plugin_id_raw):
    try:
        curr_fp = file_path_str or _params_file_path(sheet)
        if not curr_fp or not os.path.exists(curr_fp):
            return
        f_name = os.path.basename(curr_fp)
        if plugin_id_raw and not f_name.startswith(str(plugin_id_raw)):
            ext = os.path.splitext(f_name)[1] or ".py"
            f_name = f"{plugin_id_raw}{ext}"

        from ..methods import is_beta_code_viewer_enabled
        if is_beta_code_viewer_enabled():
            from ..views.sheets import open_local_file_or_archive
            if open_local_file_or_archive(curr_fp, title=f_name):
                try:
                    sheet.dismiss()
                except Exception:
                    pass
                return

        # Native viewer fallback
        PluginFileViewer = (
            _cached_class("com.exteragram.messenger.plugins.ui.components.PluginFileViewer")
            or _cached_class("app.exteraless.plugins.ui.components.PluginFileViewer")
        )
        if PluginFileViewer:
            from client_utils import get_last_fragment
            from client_utils import run_on_ui_thread
            frag = getattr(sheet, "fragment", None) or get_last_fragment()
            from java.io import File
            java_file = File(curr_fp)
            try:
                inst = getattr(PluginFileViewer, "INSTANCE", None)
            except Exception:
                inst = None
            if inst is not None:
                run_on_ui_thread(lambda: inst.open(frag, java_file, f_name))
            else:
                run_on_ui_thread(lambda: PluginFileViewer.open(frag, java_file, f_name))
    except Exception as e:
        log_error(e, context="_open_code_click")


def _params_file_path(sheet):
    try:
        params_obj = _field(sheet, "params")
        if params_obj is not None:
            return str(params_obj.getFilePath())
    except Exception:
        pass
    return None


def _on_report_click(sheet, plugin, file_path_str, plugin_id_raw):
    try:
        curr_fp = file_path_str or _params_file_path(sheet)
        curr_pid = plugin_id_raw
        if not curr_pid and curr_fp:
            from ..elyx_parser import read_plugin_id_from_file
            curr_pid = read_plugin_id_from_file(curr_fp)
        bot_id = int(str(plugin._get_submit_bot_id()))
        log(f"[KPM] report_btn clicked, bot_id={bot_id}, file={curr_fp}")
        if not bot_id:
            from ui.bulletin import BulletinHelper
            BulletinHelper.show_error("bot username not set")
            return

        from ..compat.sender import send_document_compat
        if not send_document_compat(dialog_id=bot_id, file_path=curr_fp, caption=str(curr_pid or "")):
            log("[KPM] report_btn: send_document_compat failed")

        from android.os import Bundle
        ChatActivity = _cached_class("org.telegram.ui.ChatActivity")
        if not ChatActivity:
            return
        args = Bundle()
        args.putLong("user_id", jlong(bot_id))
        try:
            sheet.dismiss()
        except Exception:
            pass
        frg = get_last_fragment()
        if frg:
            frg.presentFragment(ChatActivity(args))
    except Exception as e:
        log_error(e, context="_on_report_click")
        from ui.bulletin import BulletinHelper
        BulletinHelper.show_error(f"Send error: {e}")


def _attach_report_button(sheet, plugin, file_path_str, plugin_id_raw, custom_view):
    ImageView = _cached_class("android.widget.ImageView")
    LinearLayout = _cached_class("android.widget.LinearLayout")
    Theme = _cached_class("org.telegram.ui.ActionBar.Theme")
    LayoutHelper = _cached_class("org.telegram.ui.Components.LayoutHelper")
    AndroidUtilities = _cached_class("org.telegram.messenger.AndroidUtilities")
    if not (ImageView and Theme and LayoutHelper and AndroidUtilities):
        log("[KPM] top actions: required classes missing, skipping")
        return

    icon_color = _themed_color(sheet, Theme.key_windowBackgroundWhiteGrayIcon, 0xFF888888)

    pill = _field(sheet, "actionsPill")
    if pill is not None and hasattr(pill, "addView"):
        return

    for candidate in (custom_view, _field(sheet, "headerView")):
        open_in = _find_open_in(candidate)
        if open_in is not None and hasattr(open_in, "getParent") and open_in.getParent():
            return

    anchor = _field(sheet, "headerView") or custom_view
    if not anchor or not hasattr(anchor, "addView"):
        log("[KPM] top actions: no host view, skipping")
        return
    if not LinearLayout:
        log("[KPM] top actions: LinearLayout missing, skipping")
        return

    context = anchor.getContext()
    container = LinearLayout(context)
    container.setTag("kpm_top_actions")
    container.setOrientation(0)
    container.setGravity(17)
    container.setPadding(AndroidUtilities.dp(2), AndroidUtilities.dp(2), AndroidUtilities.dp(2), AndroidUtilities.dp(2))
    try:
        container.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(22), AndroidUtilities.dp(22), int(Theme.getColor(Theme.key_windowBackgroundGray))))
    except Exception:
        pass
    open_in = _build_open_in_button(sheet, file_path_str, plugin_id_raw, icon_color, Theme, AndroidUtilities, LayoutHelper)
    container.addView(open_in, LayoutHelper.createLinear(40, 40, 0, 0, 0, 0))
    container.setLayoutParams(LayoutHelper.createFrame(-2, 44.0, 53, 0.0, 14.0, 12.0, 0.0))
    anchor.addView(container)
    log("[KPM] top actions container created in header")


def setup_top_actions(sheet, plugin, file_path_str, plugin_id_raw, custom_view=None, install_params=None):
    try:
        if not sheet:
            return
        if _is_trusted(sheet, install_params):
            log(f"[KPM] top actions: pid={plugin_id_raw} trusted, native UI kept")
            return
        log(f"[KPM] top actions START: pid={plugin_id_raw}, fp={file_path_str}")
        _attach_report_button(sheet, plugin, file_path_str, plugin_id_raw, custom_view)
    except Exception as e:
        log_error(e, context="setup_top_actions")
