import os
import math
import traceback

from base_plugin import MethodHook
from android_utils import run_on_ui_thread, OnClickListener
from client_utils import get_last_fragment, run_on_queue
from hook_utils import find_class
from java import dynamic_proxy

from ..methods import log, log_error, _tr, _status_label, is_beta_code_viewer_enabled
from ..views.sheets.analysis_sheet import attach_analysis_button, setup_split_install_button, apply_left_button_style
from . import _hold_runnable, _release_runnable
from .install_top_actions import setup_top_actions, _on_report_click
from .install_sheet_exteraless import is_exteraless_sheet, handle_exteraless_sheet


def _get_field(target, name):
    current = target.getClass() if target else None
    while current:
        try:
            field = current.getDeclaredField(name)
            field.setAccessible(True)
            return field.get(target)
        except Exception:
            current = current.getSuperclass()
    return None


def _set_field(target, name, value):
    current = target.getClass() if target else None
    while current:
        try:
            field = current.getDeclaredField(name)
            field.setAccessible(True)
            field.set(target, value)
            return True
        except Exception:
            current = current.getSuperclass()
    return False


_CLASS_CACHE = {}


def _get_cached_class(name):
    cls = _CLASS_CACHE.get(name)
    if cls is None:
        cls = find_class(name)
        if cls is not None:
            _CLASS_CACHE[name] = cls
    return cls


_active_sheet = [None]


def _safe_dismiss(sheet_candidate=None, view_obj=None):
    from android_utils import run_on_ui_thread

    def _do():
        dismissed = False
        target = sheet_candidate or _active_sheet[0]
        if target is not None:
            try:
                if hasattr(target, "dismiss"):
                    target.dismiss()
                    log("[KPM] _safe_dismiss: target.dismiss() succeeded")
                    dismissed = True
            except Exception as e:
                log_error(e, context="_safe_dismiss target.dismiss")

        if not dismissed and _active_sheet[0] is not None and _active_sheet[0] != target:
            try:
                if hasattr(_active_sheet[0], "dismiss"):
                    _active_sheet[0].dismiss()
                    log("[KPM] _safe_dismiss: _active_sheet[0].dismiss() succeeded")
                    dismissed = True
            except Exception as e:
                log_error(e, context="_safe_dismiss _active_sheet")

        cand_view = view_obj
        if not cand_view and target is not None:
            try:
                View_cls = _get_cached_class("android.view.View")
                if View_cls and isinstance(target, View_cls):
                    cand_view = target
            except Exception:
                pass

        if cand_view is not None:
            try:
                curr = cand_view
                for _ in range(30):
                    if curr is None:
                        break
                    try:
                        cls = curr.getClass()
                        while cls is not None:
                            try:
                                f = cls.getDeclaredField("this$0")
                                f.setAccessible(True)
                                enclosing = f.get(curr)
                                if enclosing is not None and hasattr(enclosing, "dismiss"):
                                    enclosing.dismiss()
                                    log(f"[KPM] _safe_dismiss: dismissed via this$0 on {curr}")
                                    dismissed = True
                                    break
                            except Exception:
                                pass
                            cls = cls.getSuperclass()
                        if dismissed:
                            break
                    except Exception:
                        pass

                    try:
                        if hasattr(curr, "dismiss"):
                            curr.dismiss()
                            log(f"[KPM] _safe_dismiss: curr.dismiss() succeeded on {curr}")
                            dismissed = True
                            break
                    except Exception:
                        pass

                    if hasattr(curr, "getParent"):
                        curr = curr.getParent()
                    else:
                        break
            except Exception as ve:
                log_error(ve, context="_safe_dismiss view hierarchy search")

        _active_sheet[0] = None

    run_on_ui_thread(_do)


def dismiss_active_install_sheet():
    _safe_dismiss()


def _get_sheet_id(sheet):
    try:
        System = _get_cached_class("java.lang.System")
        if System:
            return int(System.identityHashCode(sheet))
    except Exception:
        pass
    try:
        return int(sheet.hashCode())
    except Exception:
        pass
    return id(sheet)


def _get_themed_color(sheet, key, fallback=None):
    try:
        if sheet and hasattr(sheet, "getThemedColor"):
            res = sheet.getThemedColor(key)
            if res is not None and int(res) != 0:
                return int(res)
    except Exception:
        pass
    try:
        Theme = _get_cached_class("org.telegram.ui.ActionBar.Theme")
        rp = _get_field(sheet, "resourcesProvider") if sheet else None
        if not rp and sheet and hasattr(sheet, "getResourceProvider"):
            try:
                rp = sheet.getResourceProvider()
            except Exception:
                pass
        if Theme:
            if rp:
                return int(Theme.getColor(key, rp))
            return int(Theme.getColor(key))
    except Exception:
        pass
    return fallback




def _create_diffuse_glow_drawable(context, stock_color):
    try:
        Bitmap = _get_cached_class("android.graphics.Bitmap")
        BitmapConfig = _get_cached_class("android.graphics.Bitmap$Config")
        BitmapDrawable = _get_cached_class("android.graphics.drawable.BitmapDrawable")

        if not Bitmap or not BitmapConfig or not BitmapDrawable:
            return None

        W = 48
        H = 24
        bitmap = Bitmap.createBitmap(W, H, getattr(BitmapConfig, "ARGB_8888"))

        r = (stock_color >> 16) & 0xFF
        g = (stock_color >> 8) & 0xFF
        b = stock_color & 0xFF

        for y in range(H):
            v = y / float(H - 1)

            for x in range(W):
                u = x / float(W - 1)

                dx_center = (u - 0.5) * 2.0
                lift = dx_center * dx_center
                start_v = 0.22 * (1.0 - lift)

                if v <= start_v:
                    fade = 0.0
                else:
                    prog = (v - start_v) / (1.0 - start_v)
                    prog = min(1.0, max(0.0, prog))
                    fade = prog * prog * (3.0 - 2.0 * prog)
                    tail_v = min(1.0, max(0.0, (1.0 - v) / 0.3))
                    tail = tail_v * tail_v * (3.0 - 2.0 * tail_v)
                    fade *= tail

                diag = (u + (1.0 - v)) * 0.5

                cur_r = int(r * (0.75 + 0.25 * diag))
                cur_g = int(g * (0.75 + 0.25 * diag))
                cur_b = int(b * (0.75 + 0.25 * diag))

                alpha_base = 0.14 + 0.20 * diag
                final_alpha = int(255.0 * alpha_base * fade)

                if final_alpha <= 0:
                    pixel = 0
                else:
                    pixel = (final_alpha << 24) | (cur_r << 16) | (cur_g << 8) | cur_b
                    if pixel >= 0x80000000:
                        pixel -= 0x100000000

                bitmap.setPixel(x, y, int(pixel))

        drawable = BitmapDrawable(context.getResources(), bitmap)
        try:
            drawable.setFilterBitmap(True)
        except Exception:
            pass
        return drawable
    except Exception as e:
        log_error(e, context="_create_diffuse_glow_drawable")
        return None


def _dump_view_tree(view, depth=0, max_depth=6):
    if not view or depth > max_depth:
        return
    try:
        indent = "  " * depth
        cls_name = str(view.getClass().getSimpleName()) if hasattr(view, "getClass") else type(view).__name__
        tag = str(view.getTag()) if hasattr(view, "getTag") and view.getTag() is not None else ""
        vid = str(view.getId()) if hasattr(view, "getId") and view.getId() != -1 else ""
        txt = ""
        try:
            if hasattr(view, "getText") and view.getText():
                txt = f" text='{str(view.getText())[:40]}'"
        except Exception:
            pass
        lp_info = ""
        try:
            lp = view.getLayoutParams()
            if lp:
                lp_info = f" w={lp.width} h={lp.height}"
                if hasattr(lp, "gravity"):
                    lp_info += f" grav={lp.gravity}"
                if hasattr(lp, "leftMargin"):
                    lp_info += f" m=({lp.leftMargin},{lp.topMargin},{lp.rightMargin},{lp.bottomMargin})"
        except Exception:
            pass
        extra = ""
        if tag:
            extra += f" tag={tag}"
        if vid:
            extra += f" id={vid}"
        child_count = 0
        if hasattr(view, "getChildCount"):
            try:
                child_count = view.getChildCount()
            except Exception:
                pass
        log(f"[KPM] {indent}[{depth}] {cls_name}{extra}{lp_info}{txt} (children={child_count})")
        if hasattr(view, "getChildCount"):
            for i in range(view.getChildCount()):
                _dump_view_tree(view.getChildAt(i), depth + 1, max_depth)
    except Exception as e:
        log_error(e, context="dump_view_tree")


def _log_sheet_state(sheet, tag=""):
    try:
        if not sheet:
            log(f"[KPM] {tag} sheet is None")
            return
        cls_name = str(sheet.getClass().getName()) if hasattr(sheet, "getClass") else type(sheet).__name__
        super_name = str(sheet.getClass().getSuperclass().getName()) if hasattr(sheet, "getClass") and sheet.getClass().getSuperclass() else "None"
        log(f"[KPM] {tag} sheet={cls_name}, super={super_name}")
        fields = ["button", "customView", "bottomContainer", "headerView", "descriptionView", "openIn", "params", "plugin", "adapter", "containerView", "container"]
        for fn in fields:
            val = _get_field(sheet, fn)
            if val is not None:
                val_cls = str(val.getClass().getName()) if hasattr(val, "getClass") else type(val).__name__
                p_cls = str(val.getParent().getClass().getName()) if hasattr(val, "getParent") and val.getParent() else "None"
                log(f"[KPM] {tag}   field '{fn}': class={val_cls}, parent={p_cls}")
            else:
                log(f"[KPM] {tag}   field '{fn}': None")
    except Exception as e:
        log_error(e, context="log_sheet_state")


def _setup_description_with_certificate(sheet, plugin, plugin_id_raw, file_path_str, custom_view=None):
    try:
        log(f"[KPM] _setup_description_with_certificate START: pid={plugin_id_raw}, fp={file_path_str}")
        if not sheet or (not plugin_id_raw and not file_path_str):
            log("[KPM] _setup_description_with_certificate: missing sheet or ids, exiting")
            return

        header_view = _get_field(sheet, "headerView")
        if header_view and hasattr(header_view, "findViewWithTag"):
            old_badge = header_view.findViewWithTag("kpm_security_badge")
            if old_badge:
                p = old_badge.getParent()
                if p and hasattr(p, "removeView"):
                    p.removeView(old_badge)

        f_desc = None
        curr_desc = None
        try:
            f_desc = sheet.getClass().getDeclaredField("descriptionView")
            f_desc.setAccessible(True)
            curr_desc = f_desc.get(sheet)
            log(f"[KPM] descriptionView field found: {curr_desc}")
        except Exception as fe:
            log(f"[KPM] descriptionView field not found: {fe}")
            f_desc = None
            curr_desc = None

        if not curr_desc and custom_view:
            log("[KPM] searching description in custom_view...")
            TextView_cls = _get_cached_class("android.widget.TextView")
            ButtonWithCounterView = _get_cached_class("org.telegram.ui.Stories.recorder.ButtonWithCounterView")
            candidates = []
            def _collect_tv(v, d=0):
                if not v or d > 8:
                    return
                tag = str(v.getTag()) if hasattr(v, "getTag") and v.getTag() else ""
                if tag in ("kpm_description_container", "kpm_source_strip", "kpm_top_actions", "kpm_split_container"):
                    return
                if ButtonWithCounterView and isinstance(v, ButtonWithCounterView):
                    return
                if TextView_cls and isinstance(v, TextView_cls):
                    txt = str(v.getText() or "").strip()
                    if txt:
                        candidates.append((v, txt))
                if hasattr(v, "getChildCount"):
                    for i in range(v.getChildCount()):
                        _collect_tv(v.getChildAt(i), d + 1)
            _collect_tv(custom_view)
            log(f"[KPM] _find_desc collected {len(candidates)} text views")
            for v, txt in candidates:
                grav = v.getGravity() if hasattr(v, "getGravity") else 0
                log(f"[KPM] candidate: '{txt[:30]}', grav={grav}")
                if (grav & 7) == 3 or grav == 3 or grav == 8388611:
                    curr_desc = v
                    log(f"[KPM] _find_desc matched by gravity: '{txt[:30]}'")
                    break
            if not curr_desc and len(candidates) >= 3:
                curr_desc = candidates[2][0]
                log(f"[KPM] _find_desc matched by idx 2: '{candidates[2][1][:30]}'")
            elif not curr_desc and candidates:
                curr_desc = max(candidates, key=lambda c: len(c[1]))[0]
                log(f"[KPM] _find_desc matched by max length")
            log(f"[KPM] _find_desc final result: {curr_desc}")

        if not curr_desc:
            log("[KPM] curr_desc is None, exiting _setup_description_with_certificate")
            return

        if hasattr(curr_desc, "getTag") and str(curr_desc.getTag()) == "kpm_description_container":
            log("[KPM] curr_desc already in kpm_description_container, exiting")
            return

        context = curr_desc.getContext()
        AndroidUtilities = _get_cached_class("org.telegram.messenger.AndroidUtilities")
        LayoutHelper = _get_cached_class("org.telegram.ui.Components.LayoutHelper")
        Theme = _get_cached_class("org.telegram.ui.ActionBar.Theme")
        ImageView = _get_cached_class("android.widget.ImageView")
        TextView = _get_cached_class("android.widget.TextView")
        LinearLayout = _get_cached_class("android.widget.LinearLayout")
        ScaleStateListAnimator = _get_cached_class("org.telegram.ui.Components.ScaleStateListAnimator")
        OnClickListenerInterface = _get_cached_class("android.view.View$OnClickListener")

        if not AndroidUtilities or not LayoutHelper or not Theme or not LinearLayout:
            return

        verdict = plugin._get_plugin_verdict(plugin_id_raw, file_path_str)
        log(f"[KPM] security verdict for {plugin_id_raw}: {verdict}")

        rp = _get_field(sheet, "resourcesProvider") if sheet else None
        is_monet = False
        try:
            if hasattr(Theme, "isCurrentThemeMonet"):
                is_monet = bool(Theme.isCurrentThemeMonet(rp) if rp else Theme.isCurrentThemeMonet())
        except Exception:
            pass

        if verdict == "verified":
            raw_text = _tr("security_verified")
            if is_monet:
                stock_color = _get_themed_color(sheet, Theme.key_featuredStickers_addButton)
            else:
                stock_color = _get_themed_color(sheet, getattr(Theme, "key_botKeyboard_button_success", Theme.key_windowBackgroundWhiteGreenText))
            if stock_color is None:
                stock_color = _get_themed_color(sheet, Theme.key_windowBackgroundWhiteGreenText, 0xFF10B981)
        elif verdict == "failed":
            raw_text = _tr("security_failed")
            stock_color = _get_themed_color(sheet, getattr(Theme, "key_botKeyboard_button_danger", Theme.key_text_RedRegular))
            if stock_color is None:
                stock_color = _get_themed_color(sheet, Theme.key_text_RedRegular, 0xFFEF4444)
        else:
            raw_text = _tr("security_unknown")
            stock_color = _get_themed_color(sheet, Theme.key_windowBackgroundWhiteValueText)
            if stock_color is None:
                stock_color = _get_themed_color(sheet, getattr(Theme, "key_botKeyboard_button_primary", Theme.key_windowBackgroundWhiteBlueText), 0xFFF59E0B)

        if stock_color is None:
            stock_color = 0xFFF59E0B
        if stock_color >= 0x80000000:
            stock_color -= 0x100000000

        parts = str(raw_text).split("\n", 1)
        title_str = parts[0]
        sub_str = parts[1] if len(parts) > 1 else ""

        cert_container = LinearLayout(context)
        cert_container.setTag("kpm_cert_container")
        cert_container.setOrientation(LinearLayout.HORIZONTAL)
        cert_container.setGravity(16)

        bg_drawable = _create_diffuse_glow_drawable(context, stock_color)
        if bg_drawable:
            cert_container.setBackground(bg_drawable)

        cert_container.setPadding(
            AndroidUtilities.dp(16),
            AndroidUtilities.dp(14),
            AndroidUtilities.dp(16),
            AndroidUtilities.dp(16)
        )

        from ..views.sheets.analysis_sheet import _shield_drawable
        icon_view = ImageView(context)
        icon_view.setImageDrawable(_shield_drawable(context, 24, stock_color, status=verdict))
        icon_view.setScaleType(ImageView.ScaleType.CENTER)
        cert_container.addView(icon_view, LayoutHelper.createLinear(24, 24, 16, 0, 0, 14, 0))

        text_layout = LinearLayout(context)
        text_layout.setOrientation(LinearLayout.VERTICAL)
        text_layout.setGravity(16)

        title_view = TextView(context)
        title_view.setText(title_str)
        title_view.setTypeface(AndroidUtilities.bold())
        title_view.setTextSize(1, 14.0)
        title_view.setTextColor(stock_color)
        text_layout.addView(title_view, LayoutHelper.createLinear(-1, -2))

        if sub_str:
            sub_view = TextView(context)
            sub_view.setText(sub_str)
            sub_view.setTextSize(1, 12.0)
            sub_color = _get_themed_color(sheet, Theme.key_windowBackgroundWhiteGrayText, 0xFF888888)
            sub_view.setTextColor(sub_color)
            text_layout.addView(sub_view, LayoutHelper.createLinear(-1, -2, 0, 0, 2, 0, 0))

        cert_container.addView(text_layout, LayoutHelper.createLinear(0, -2, 1.0, 16, 0, 0, 0))

        cert_container.setClickable(False)
        cert_container.setFocusable(False)

        composite_container = LinearLayout(context)
        composite_container.setTag("kpm_description_container")
        composite_container.setOrientation(LinearLayout.VERTICAL)

        p = curr_desc.getParent()
        idx = -1
        orig_lp = None
        if p and hasattr(p, "indexOfChild"):
            idx = p.indexOfChild(curr_desc)
            orig_lp = curr_desc.getLayoutParams()
        if p and hasattr(p, "removeView"):
            p.removeView(curr_desc)

        card_col = _get_themed_color(sheet, Theme.key_windowBackgroundWhite, 0xFFFFFFFF)

        if f_desc:
            curr_desc.setPadding(
                AndroidUtilities.dp(16),
                AndroidUtilities.dp(14),
                AndroidUtilities.dp(16),
                AndroidUtilities.dp(6)
            )
        else:
            card_bg = Theme.createRoundRectDrawable(
                AndroidUtilities.dp(16),
                card_col
            )
            composite_container.setBackground(card_bg)
            curr_desc.setPadding(
                AndroidUtilities.dp(16),
                AndroidUtilities.dp(14),
                AndroidUtilities.dp(16),
                AndroidUtilities.dp(8)
            )

        composite_container.addView(curr_desc, LayoutHelper.createLinear(-1, -2))
        composite_container.addView(cert_container, LayoutHelper.createLinear(-1, -2))



        ViewOutlineProviderImpl = _get_cached_class("org.telegram.messenger.utils.ViewOutlineProviderImpl")
        ViewOutlineProvider = _get_cached_class("android.view.ViewOutlineProvider")

        if ViewOutlineProviderImpl:
            try:
                composite_container.setOutlineProvider(ViewOutlineProviderImpl.boundsWithRoundRect(float(AndroidUtilities.dp(16))))
                composite_container.setClipToOutline(True)
            except Exception:
                pass
        elif ViewOutlineProvider:
            try:
                class CardOutline(dynamic_proxy(ViewOutlineProvider)):
                    def getOutline(self, view, outline):
                        try:
                            outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), float(AndroidUtilities.dp(16)))
                        except Exception:
                            pass
                composite_container.setOutlineProvider(CardOutline())
                composite_container.setClipToOutline(True)
            except Exception:
                pass

        if ViewOutlineProvider:
            try:
                class CertBottomOutline(dynamic_proxy(ViewOutlineProvider)):
                    def getOutline(self, view, outline):
                        try:
                            r = float(AndroidUtilities.dp(16))
                            outline.setRoundRect(0, -int(r), view.getWidth(), view.getHeight(), r)
                        except Exception:
                            pass
                cert_container.setOutlineProvider(CertBottomOutline())
                cert_container.setClipToOutline(True)
            except Exception:
                pass

        if f_desc:
            try:
                f_desc.set(sheet, composite_container)
                log("[KPM] replaced sheet.descriptionView with composite kpm_description_container")
            except Exception:
                pass
        elif p and idx >= 0:
            try:
                p.addView(composite_container, idx, LayoutHelper.createLinear(-1, -2, 0, 16, 16, 16, 0))
                log("[KPM] inserted composite_container into description parent at idx")
            except Exception as pe:
                log_error(pe, context="addView composite_container")

        adapter = _get_field(sheet, "adapter")
        if adapter:
            try:
                items = _get_field(adapter, "items")
                if items:
                    for i in range(items.size()):
                        it = items.get(i)
                        if it and it.view == curr_desc:
                            it.view = composite_container
                            log("[KPM] updated item.view in adapter.items to composite_container")
                            break
                if hasattr(adapter, "notifyDataSetChanged"):
                    adapter.notifyDataSetChanged()
                elif hasattr(adapter, "update"):
                    adapter.update(False)
            except Exception as ae:
                log_error(ae, context="adapter update")
    except Exception as e:
        log_error(e, context="_setup_description_with_certificate")


def _composite_color(fg_color, fg_alpha_ratio, bg_color):
    try:
        ColorUtils = _get_cached_class("androidx.core.graphics.ColorUtils")
        if ColorUtils:
            fg_a = int(255 * fg_alpha_ratio)
            fg = (fg_color & 0x00FFFFFF) | (fg_a << 24)
            return int(ColorUtils.compositeColors(fg, bg_color))
    except Exception:
        pass
    r1 = (fg_color >> 16) & 0xFF
    g1 = (fg_color >> 8) & 0xFF
    b1 = fg_color & 0xFF
    r2 = (bg_color >> 16) & 0xFF
    g2 = (bg_color >> 8) & 0xFF
    b2 = bg_color & 0xFF
    a = max(0.0, min(1.0, float(fg_alpha_ratio)))
    r = int(r1 * a + r2 * (1.0 - a))
    g = int(g1 * a + g2 * (1.0 - a))
    b = int(b1 * a + b2 * (1.0 - a))
    return int((0xFF << 24) | (r << 16) | (g << 8) | b)


def _setup_folder_strip(sheet, custom_view, status_label):
    try:
        header_view = _get_field(sheet, "headerView")
        LL = _get_cached_class("android.widget.LinearLayout")
        TextView = _get_cached_class("android.widget.TextView")
        ImageView = _get_cached_class("android.widget.ImageView")
        AndroidUtilities = _get_cached_class("org.telegram.messenger.AndroidUtilities")
        LayoutHelper = _get_cached_class("org.telegram.ui.Components.LayoutHelper")
        Theme = _get_cached_class("org.telegram.ui.ActionBar.Theme")
        ScaleStateListAnimator = _get_cached_class("org.telegram.ui.Components.ScaleStateListAnimator")

        if not LL or not TextView or not ImageView or not AndroidUtilities or not LayoutHelper or not Theme:
            return

        for root_view in (header_view, custom_view):
            if root_view and hasattr(root_view, "findViewWithTag"):
                old_badge = root_view.findViewWithTag("kpm_status_badge")
                if old_badge and hasattr(old_badge, "getParent") and old_badge.getParent():
                    try:
                        old_badge.getParent().removeView(old_badge)
                    except Exception:
                        pass

        def _find_source_container(root):
            if not root:
                return None
            try:
                tag = root.getTag() if hasattr(root, "getTag") else None
                if tag and str(tag) == "kpm_source_strip":
                    return root
            except Exception:
                pass
            if isinstance(root, LL) and hasattr(root, "getOrientation") and root.getOrientation() == 0:
                count = root.getChildCount() if hasattr(root, "getChildCount") else 0
                if count >= 2:
                    has_iv = False
                    has_tv = False
                    for i in range(count):
                        c = root.getChildAt(i)
                        if isinstance(c, ImageView):
                            has_iv = True
                        elif isinstance(c, TextView):
                            has_tv = True
                    if has_iv and has_tv:
                        return root
            count = root.getChildCount() if hasattr(root, "getChildCount") else 0
            for i in range(count):
                found = _find_source_container(root.getChildAt(i))
                if found:
                    return found
            return None

        source_badge = _find_source_container(header_view)
        log(f"[KPM] _setup_folder_strip: source_badge in header_view={source_badge}")
        if not source_badge and custom_view:
            source_badge = _find_source_container(custom_view)
            log(f"[KPM] _setup_folder_strip: source_badge in custom_view={source_badge}")

        if not source_badge:
            log("[KPM] _setup_folder_strip: source_badge not found, exiting")
            return

        try:
            tag = source_badge.getTag() if hasattr(source_badge, "getTag") else None
            if tag and str(tag) == "kpm_source_strip":
                log("[KPM] _setup_folder_strip: already has kpm_source_strip, exiting")
                return
        except Exception:
            pass

        iv = None
        tv_src = None
        for i in range(source_badge.getChildCount()):
            c = source_badge.getChildAt(i)
            if isinstance(c, ImageView) and not iv:
                iv = c
            elif isinstance(c, TextView) and not tv_src:
                tv_src = c

        themedColor = int(tv_src.getCurrentTextColor()) if tv_src else int(Theme.getColor(Theme.key_text_RedRegular))
        card_bg = int(Theme.getColor(Theme.key_windowBackgroundWhite))
        track_bg = _composite_color(themedColor, 0.12, card_bg)
        tab_bg = _composite_color(themedColor, 0.22, card_bg)

        source_badge.removeAllViews()
        source_badge.setTag("kpm_source_strip")
        source_badge.setOrientation(0)
        source_badge.setGravity(17)

        track_drawable = Theme.createRoundRectDrawable(AndroidUtilities.dp(16), AndroidUtilities.dp(16), int(track_bg))
        source_badge.setBackground(track_drawable)
        source_badge.setPadding(AndroidUtilities.dp(3), AndroidUtilities.dp(3), AndroidUtilities.dp(3), AndroidUtilities.dp(3))
        source_badge.setLayoutParams(LayoutHelper.createLinear(-2, -2, 17, 0, 12, 0, 0))

        ctx = source_badge.getContext()

        cat_tab = LL(ctx)
        cat_tab.setOrientation(0)
        cat_tab.setGravity(17)
        cat_drawable = Theme.createRoundRectDrawable(AndroidUtilities.dp(13), AndroidUtilities.dp(13), int(tab_bg))
        cat_tab.setBackground(cat_drawable)
        cat_tab.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(4), AndroidUtilities.dp(12), AndroidUtilities.dp(4))

        cat_tv = TextView(ctx)
        cat_tv.setText(str(status_label))
        cat_tv.setTypeface(AndroidUtilities.bold())
        cat_tv.setTextSize(1, 12.5)
        cat_tv.setTextColor(themedColor)
        cat_tv.setIncludeFontPadding(False)
        cat_tab.addView(cat_tv, LayoutHelper.createLinear(-2, -2))
        source_badge.addView(cat_tab, LayoutHelper.createLinear(-2, -2, 16, 0, 0, 4, 0))

        src_tab = LL(ctx)
        src_tab.setOrientation(0)
        src_tab.setGravity(17)
        src_tab.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(4), AndroidUtilities.dp(10), AndroidUtilities.dp(4))

        if iv:
            src_tab.addView(iv, LayoutHelper.createLinear(14, 14, 16, 0, 0, 5, 0))
        if tv_src:
            tv_src.setTypeface(AndroidUtilities.bold())
            tv_src.setTextSize(1, 12.5)
            tv_src.setTextColor(themedColor)
            tv_src.setIncludeFontPadding(False)
            src_tab.addView(tv_src, LayoutHelper.createLinear(-2, -2, 16, 0, 0, 0, 0))

        source_badge.addView(src_tab, LayoutHelper.createLinear(-2, -2, 16, 0, 0, 0, 0))

        if ScaleStateListAnimator:
            try:
                ScaleStateListAnimator.apply(source_badge, 0.03, 1.5)
            except Exception:
                pass
    except Exception as e:
        log_error(e, context="_setup_folder_strip")


def _setup_top_actions(sheet, plugin, file_path_str, plugin_id_raw, custom_view=None, install_params=None):
    setup_top_actions(sheet, plugin, file_path_str, plugin_id_raw, custom_view=custom_view, install_params=install_params)


def install_install_sheet_hook(pl):
    try:
        log("[KPM] install_install_sheet_hook START")
        try:
            from ui.bulletin import BulletinHelper
            _orig_swb = getattr(BulletinHelper, "show_with_button", None)
            if _orig_swb:
                def _safe_swb(*args, **kwargs):
                    if "icon_res_id" in kwargs:
                        if not isinstance(kwargs["icon_res_id"], int):
                            kwargs["icon_res_id"] = 0
                    elif len(args) > 4 and not isinstance(args[4], int):
                        args_l = list(args)
                        args_l[4] = 0
                        args = tuple(args_l)
                    return _orig_swb(*args, **kwargs)
                BulletinHelper.show_with_button = staticmethod(_safe_swb)
                log("[KPM] BulletinHelper.show_with_button safe wrapper applied")
        except Exception as b_ex:
            log_error(b_ex, context="BulletinHelper patch")
        _pending_file_path = [None]
        _modified_sheets = [set()]
        _install_sheet_ids = [set()]
        Handler = find_class("android.os.Handler")
        Looper = find_class("android.os.Looper")
        Runnable = find_class("java.lang.Runnable")
        if Handler is None or Looper is None or Runnable is None:
            log(f"[KPM] Handler/Looper/Runnable not found: {Handler}/{Looper}/{Runnable}")

        class InstallDialogGuardHook(MethodHook):
            def __init__(self, plugin):
                self.plugin = plugin

            def before_hooked_method(self, param):
                try:
                    log(f"[KPM] PythonPluginsEngine.showInstallDialog called, args_len={len(param.args) if param.args else 0}")
                    install_params = param.args[1]
                    if not install_params:
                        return
                    path_field = install_params.getClass().getDeclaredField("filePath")
                    path_field.setAccessible(True)
                    file_path = path_field.get(install_params)
                    if not file_path:
                        return
                    file_path_str = str(file_path)

                    try:
                        system_trusted = install_params.getTrusted()
                    except Exception:
                        system_trusted = False

                    _pending_file_path[0] = file_path_str
                    if system_trusted:
                        return

                    from ..elyx_parser import read_plugin_id_from_file
                    pid = read_plugin_id_from_file(file_path_str)
                    if not pid:
                        return

                    is_safe = self.plugin._check_plugin_safety(pid, file_path_str)
                    in_store = any(k.lower() == pid.lower() for k in (self.plugin.plugins_list or {}))

                    if is_safe and in_store:
                        try:
                            install_params.setTrusted(True)
                        except Exception:
                            pass
                    else:
                        try:
                            blocking = str(self.plugin.get_setting("block_untrusted_plugins", True)).lower()
                            if blocking not in ("false", "0", "none", "no", ""):
                                param.setResult(None)
                                run_on_ui_thread(lambda: self.plugin._show_untrusted_warning(pid))
                        except Exception:
                            pass
                except Exception as e:
                    log_error(e, context="install guard")

        def _find_sheet_in_view(view):
            try:
                if hasattr(view, 'getClass'):
                    cls_name = str(view.getClass().getName())
                    if 'InstallPluginBottomSheet' in cls_name and hasattr(view, "dismiss"):
                        return view
                    if ('BottomSheet' in cls_name.split('.')[-1] if cls_name.split('.') else False) and hasattr(view, "dismiss"):
                        return view
                    try:
                        f = view.getClass().getDeclaredField("this$0")
                        f.setAccessible(True)
                        encl = f.get(view)
                        if encl is not None and hasattr(encl, "dismiss"):
                            return encl
                    except Exception:
                        pass
            except Exception:
                pass
            try:
                if hasattr(view, 'getChildCount'):
                    for i in range(view.getChildCount()):
                        child = view.getChildAt(i)
                        result = _find_sheet_in_view(child)
                        if result:
                            return result
            except Exception:
                pass
            return None

        def _find_button_in_tree(view, depth=0):
            if not view or depth > 12:
                return None
            ButtonWithCounterView = _get_cached_class("org.telegram.ui.Stories.recorder.ButtonWithCounterView")
            if ButtonWithCounterView and isinstance(view, ButtonWithCounterView):
                return view
            cls_name = str(view.getClass().getName()) if hasattr(view, "getClass") else ""
            if "ButtonWithCounterView" in cls_name:
                return view
            if hasattr(view, "getChildCount"):
                for i in range(view.getChildCount()):
                    res = _find_button_in_tree(view.getChildAt(i), depth + 1)
                    if res:
                        return res
            return None

        def _refresh_split_button_style(sheet):
            try:
                if not sheet:
                    return
                btn_f = sheet.getClass().getDeclaredField("button")
                btn_f.setAccessible(True)
                btn = btn_f.get(sheet)
                if not btn:
                    return
                parent = btn.getParent()
                if not parent or str(parent.getTag()) != "kpm_split_container":
                    return

                resources_provider = None
                try:
                    m = sheet.getClass().getDeclaredMethod("getResourceProvider")
                    m.setAccessible(True)
                    resources_provider = m.invoke(sheet)
                except Exception:
                    pass

                Theme_cls = _get_cached_class("org.telegram.ui.ActionBar.Theme")
                bg_color = Theme_cls.getColor(Theme_cls.key_featuredStickers_addButton, resources_provider) if resources_provider else Theme_cls.getColor(Theme_cls.key_featuredStickers_addButton)
                text_color = Theme_cls.getColor(Theme_cls.key_featuredStickers_buttonText, resources_provider) if resources_provider else Theme_cls.getColor(Theme_cls.key_featuredStickers_buttonText)

                try:
                    bf = btn.getClass().getDeclaredField("backgroundColor")
                    bf.setAccessible(True)
                    c_bg = bf.getInt(btn)
                    if c_bg != 0:
                        bg_color = c_bg
                except Exception:
                    pass

                apply_left_button_style(btn, bg_color, text_color)

                if parent.getChildCount() > 1:
                    a_btn = parent.getChildAt(1)
                    if hasattr(a_btn, "_update_colors"):
                        a_btn._update_colors(bg_color, text_color)
            except Exception as e:
                log_error(e, context="_refresh_split_button_style")

        def _apply_sheet_colors(sheet):
            try:
                _active_sheet[0] = sheet
                try:
                    from ..methods import register_active_sheet
                    register_active_sheet(sheet)
                except Exception:
                    pass
                Theme = _get_cached_class("org.telegram.ui.ActionBar.Theme")
                if Theme and sheet:
                    if hasattr(sheet, "getThemedColor"):
                        sheet_bg = int(sheet.getThemedColor(Theme.key_windowBackgroundGray))
                    else:
                        sheet_bg = int(Theme.getColor(Theme.key_windowBackgroundGray))
                    if hasattr(sheet, "setBackgroundColor"):
                        sheet.setBackgroundColor(sheet_bg)

                    try:
                        sheet.occupyNavigationBar = True
                        sheet.drawNavigationBar = False
                        sheet.drawDoubleNavigationBar = False
                    except Exception:
                        pass
                    _set_field(sheet, "occupyNavigationBar", True)
                    _set_field(sheet, "drawNavigationBar", False)
                    _set_field(sheet, "drawDoubleNavigationBar", False)
                    _set_field(sheet, "navBarColor", 0)
                    _set_field(sheet, "navBarColorKey", -1)
                    _set_field(sheet, "overlayDrawNavBarColor", 0)
                    if hasattr(sheet, "container") and sheet.container:
                        sheet.container.invalidate()

                    AndroidUtilities = _get_cached_class("org.telegram.messenger.AndroidUtilities")
                    is_light = bool(AndroidUtilities.computePerceivedBrightness(sheet_bg) >= 0.721) if AndroidUtilities else False

                    LaunchActivity = _get_cached_class("org.telegram.ui.LaunchActivity")
                    if LaunchActivity and hasattr(LaunchActivity, "instance") and LaunchActivity.instance:
                        la = LaunchActivity.instance
                        if hasattr(la, "getWindow") and la.getWindow():
                            la.getWindow().setNavigationBarColor(0)
                            if AndroidUtilities:
                                try:
                                    AndroidUtilities.setLightNavigationBar(la, is_light)
                                except Exception:
                                    try:
                                        AndroidUtilities.setLightNavigationBar(la.getWindow().getDecorView(), is_light)
                                    except Exception:
                                        pass

                    if hasattr(sheet, "getWindow") and sheet.getWindow():
                        sheet.getWindow().setNavigationBarColor(0)
                        if AndroidUtilities:
                            try:
                                AndroidUtilities.setLightNavigationBar(sheet, is_light)
                            except Exception:
                                try:
                                    AndroidUtilities.setLightNavigationBar(sheet.getWindow().getDecorView(), is_light)
                                except Exception:
                                    pass
            except Exception as ce:
                log_error(ce, context="_apply_sheet_colors")

        def _handle_sheet(plugin, sheet, retry=0, custom_view=None):
            try:
                if not sheet or retry > 3:
                    return
                if is_exteraless_sheet(sheet):
                    if handle_exteraless_sheet(plugin, sheet, _pending_file_path[0]):
                        _pending_file_path[0] = None
                    return
                _apply_sheet_colors(sheet)
                sheet_id = _get_sheet_id(sheet)
                log(f"[KPM] _handle_sheet ENTER: sheet_id={sheet_id}, retry={retry}")

                cv = custom_view or _get_field(sheet, "customView") or _get_field(sheet, "containerView")

                if sheet_id in _modified_sheets[0]:
                    log(f"[KPM] _handle_sheet: sheet {sheet_id} already modified")
                    _refresh_split_button_style(sheet)
                    return

                btn = _get_field(sheet, "button")
                if not btn and cv:
                    btn = _find_button_in_tree(cv)
                bc = _get_field(sheet, "bottomContainer")
                if not bc and btn and hasattr(btn, "getParent"):
                    bc = btn.getParent()

                log(f"[KPM] _handle_sheet: btn={btn}, bc={bc}, cv={cv}")
                if not btn and not cv:
                    if retry < 2:
                        run_on_ui_thread(lambda: _handle_sheet(plugin, sheet, retry + 1, custom_view=custom_view))
                    return

                if bc and hasattr(bc, "findViewWithTag") and bc.findViewWithTag("kpm_split_container") is not None:
                    log(f"[KPM] _handle_sheet: split_container already in bc, marking modified")
                    _modified_sheets[0].add(sheet_id)
                    _refresh_split_button_style(sheet)
                    return

                log(f"[KPM] _handle_sheet: calling _modify_install_sheet_ui...")
                _modify_install_sheet_ui(plugin, None, None, sheet=sheet, custom_view=cv)
            except Exception as e:
                log_error(e, context="_handle_sheet")

        def _modify_install_sheet_ui(plugin, file_path_str, install_params, sheet=None, retry=0, custom_view=None):
            try:
                if retry > 5:
                    return
                log(f"[KPM] _modify_install_sheet_ui called for {file_path_str}, sheet={sheet}")

                if sheet is not None:
                    try:
                        sheet_cls = str(sheet.getClass().getName())
                        if "InstallPluginBottomSheet" not in sheet_cls and "BottomSheet" not in sheet_cls:
                            log(f"[KPM] _modify_install_sheet_ui: skip non-install sheet {sheet_cls}")
                            return
                    except Exception:
                        pass

                if not sheet:
                    last_frag = get_last_fragment()
                    if not last_frag:
                        log("[KPM] no last_frag")
                        return
                    activity = last_frag.getParentActivity()
                    if not activity:
                        log("[KPM] no activity")
                        return

                    try:
                        decor = activity.getWindow().getDecorView()
                        sheet = _find_sheet_in_view(decor)
                    except Exception:
                        pass

                if not sheet:
                    log("[KPM] sheet not found after traversal")
                    return

                if is_exteraless_sheet(sheet):
                    if handle_exteraless_sheet(plugin, sheet, file_path_str or _pending_file_path[0]):
                        _pending_file_path[0] = None
                    return

                _apply_sheet_colors(sheet)
                sheet_id = _get_sheet_id(sheet)

                if not custom_view:
                    custom_view = _get_field(sheet, "customView") or _get_field(sheet, "containerView")

                if sheet_id in _modified_sheets[0]:
                    log(f"[KPM] skipping duplicate modify for sheet {sheet_id}")
                    _refresh_split_button_style(sheet)
                    return

                bottom_container = _get_field(sheet, "bottomContainer")
                install_btn_field = _get_field(sheet, "button")
                if not install_btn_field and custom_view:
                    install_btn_field = _find_button_in_tree(custom_view)
                if not bottom_container and install_btn_field and hasattr(install_btn_field, "getParent"):
                    bottom_container = install_btn_field.getParent()

                if custom_view and not _get_field(sheet, "adapter"):
                    try:
                        AndroidUtilities = _get_cached_class("org.telegram.messenger.AndroidUtilities")
                        if AndroidUtilities and hasattr(custom_view, "setClipToPadding"):
                            custom_view.setClipToPadding(False)
                            nav_h = getattr(AndroidUtilities, "navigationBarHeight", 0)
                            if nav_h:
                                custom_view.setPadding(0, 0, 0, int(nav_h))
                    except Exception:
                        pass

                if not bottom_container and not install_btn_field and not custom_view:
                    if retry < 2:
                        run_on_ui_thread(lambda: _modify_install_sheet_ui(plugin, file_path_str, install_params, sheet=sheet, retry=retry + 1, custom_view=custom_view))
                    return

                if bottom_container and hasattr(bottom_container, "findViewWithTag") and bottom_container.findViewWithTag("kpm_split_container") is not None:
                    log(f"[KPM] bottom_container already contains kpm_split_container, skipping duplicate modify")
                    _modified_sheets[0].add(sheet_id)
                    _refresh_split_button_style(sheet)
                    return

                try:
                    _ms = _modified_sheets
                    _sid = sheet_id
                    class _CleanupDismiss(dynamic_proxy(find_class("android.content.DialogInterface").OnDismissListener)):
                        def onDismiss(self, d):
                            try:
                                _ms[0].discard(_sid)
                                _install_sheet_ids[0].discard(_sid)
                            except Exception:
                                pass
                            try:
                                _active_sheet[0] = None
                                _pending_file_path[0] = None
                                from ..methods import unregister_active_sheet
                                unregister_active_sheet(sheet)
                            except Exception:
                                pass
                                _pending_file_path[0] = None
                            except Exception:
                                pass
                            try:
                                LaunchActivity = _get_cached_class("org.telegram.ui.LaunchActivity")
                                if LaunchActivity and hasattr(LaunchActivity, "instance") and LaunchActivity.instance:
                                    LaunchActivity.instance.checkSystemBarColors(True, True, True)
                            except Exception:
                                pass
                    sheet.setOnDismissListener(_CleanupDismiss())
                except Exception:
                    pass

                log(f"[KPM] found sheet: {sheet.getClass().getName()}")

                if not file_path_str:
                    try:
                        f_params = sheet.getClass().getDeclaredField("params")
                        f_params.setAccessible(True)
                        p_obj = f_params.get(sheet)
                        if p_obj and hasattr(p_obj, "getFilePath"):
                            file_path_str = str(p_obj.getFilePath())
                    except Exception:
                        pass

                if not file_path_str and _pending_file_path[0]:
                    file_path_str = _pending_file_path[0]

                if not file_path_str:
                    file_path_str = ""

                filename = os.path.basename(file_path_str) if file_path_str else ""
                plugin_id_raw = None
                try:
                    f_plugin = sheet.getClass().getDeclaredField("plugin")
                    f_plugin.setAccessible(True)
                    pl_obj = f_plugin.get(sheet)
                    if pl_obj and hasattr(pl_obj, "getId"):
                        plugin_id_raw = str(pl_obj.getId())
                except Exception:
                    pass

                if not plugin_id_raw and file_path_str:
                    from ..elyx_parser import read_plugin_id_from_file
                    plugin_id_raw = read_plugin_id_from_file(file_path_str)

                if not plugin_id_raw and filename:
                    plugin_id_raw = filename.replace('.plugin', '').replace('.eaf', '').strip()
                    if plugin_id_raw.startswith('.temp_'):
                        plugin_id_raw = plugin_id_raw.replace('.temp_', '')

                log(f"[KPM] file_path_str={file_path_str}, filename={filename}, plugin_id_raw={plugin_id_raw}")

                dependencies = []
                plugin_info = {}
                store = plugin.plugins_list or {}
                store_keys = list(store.keys())[:10]
                log(f"[KPM] store keys sample: {store_keys}, total={len(store)}")
                if plugin_id_raw:
                    pid_lower = plugin_id_raw.lower()
                    matched = next((k for k in store if k.lower() == pid_lower), None)
                    if matched:
                        plugin_info = store[matched]
                        if isinstance(plugin_info, dict):
                            dependencies = plugin_info.get("dependencies", [])

                if not custom_view:
                    try:
                        curr_cls = sheet.getClass()
                        while curr_cls is not None:
                            try:
                                f = curr_cls.getDeclaredField("customView")
                                f.setAccessible(True)
                                custom_view = f.get(sheet)
                                if custom_view:
                                    break
                            except Exception:
                                pass
                            curr_cls = curr_cls.getSuperclass()
                    except Exception:
                        pass

                if not custom_view:
                    try:
                        curr_cls = sheet.getClass()
                        while curr_cls is not None:
                            try:
                                f = curr_cls.getDeclaredField("containerView")
                                f.setAccessible(True)
                                custom_view = f.get(sheet)
                                if custom_view:
                                    break
                            except Exception:
                                pass
                            curr_cls = curr_cls.getSuperclass()
                    except Exception:
                        pass

                if not custom_view:
                    try:
                        f = sheet.getClass().getDeclaredField("sheetContainer")
                        f.setAccessible(True)
                        custom_view = f.get(sheet)
                    except Exception:
                        pass

                if not install_btn_field:
                    try:
                        f = sheet.getClass().getDeclaredField("button")
                        f.setAccessible(True)
                        install_btn_field = f.get(sheet)
                    except Exception:
                        pass

                if not install_btn_field and custom_view:
                    install_btn_field = _find_button_in_tree(custom_view)

                if not bottom_container:
                    try:
                        f = sheet.getClass().getDeclaredField("bottomContainer")
                        f.setAccessible(True)
                        bottom_container = f.get(sheet)
                    except Exception:
                        pass

                if not bottom_container and install_btn_field and hasattr(install_btn_field, "getParent"):
                    bottom_container = install_btn_field.getParent()

                log(f"[KPM] custom_view resolved: {custom_view}")
                if custom_view:
                    pass

                if not custom_view and not bottom_container and not install_btn_field:
                    log("[KPM] no custom_view/containerView/bottomContainer/button found")
                    return

                log(f"[KPM] resolved: custom_view={type(custom_view).__name__ if custom_view else None}, "
                    f"bottom_container={type(bottom_container).__name__ if bottom_container else None}, "
                    f"install_btn_field={type(install_btn_field).__name__ if install_btn_field else None}")

                from android.util import TypedValue
                from android.view import Gravity
                ButtonWithCounterView = find_class("org.telegram.ui.Stories.recorder.ButtonWithCounterView")
                ViewGroup = find_class("android.view.ViewGroup")
                LinearLayout = find_class("android.widget.LinearLayout")
                Theme_obj = find_class("org.telegram.ui.ActionBar.Theme")
                AndroidUtilities = find_class("org.telegram.messenger.AndroidUtilities")
                LayoutHelper = find_class("org.telegram.ui.Components.LayoutHelper")

                install_btn = None
                parent_layout = None

                if install_btn_field:
                    install_btn = install_btn_field
                    parent_layout = install_btn_field.getParent()
                    log(f"[KPM] install_btn found via button field directly")

                if not install_btn and bottom_container:
                    ButtonWithCounterView = find_class("org.telegram.ui.Stories.recorder.ButtonWithCounterView")
                    if bottom_container and hasattr(bottom_container, 'getChildCount'):
                        for i in range(bottom_container.getChildCount()):
                            child = bottom_container.getChildAt(i)
                            try:
                                if child and isinstance(child, ButtonWithCounterView):
                                    install_btn = child
                                    parent_layout = bottom_container
                                    log(f"[KPM] install_btn found via bottomContainer scan")
                                    break
                            except Exception:
                                pass

                if not install_btn:
                    search_root = custom_view or bottom_container
                    if search_root:
                        ButtonWithCounterView = find_class("org.telegram.ui.Stories.recorder.ButtonWithCounterView")
                        ViewGroup_cls = find_class("android.view.ViewGroup")

                        def scan_view(view, depth=0):
                            nonlocal install_btn, parent_layout
                            if not view or depth > 15:
                                return
                            if isinstance(view, ButtonWithCounterView):
                                try:
                                    btn_text = str(view.getText())
                                    if btn_text and any(w in btn_text for w in ("Install", "Установить", "Update", "Обновить")):
                                        if not install_btn:
                                            install_btn = view
                                            parent_layout = view.getParent()
                                except Exception:
                                    if not install_btn:
                                        install_btn = view
                                        parent_layout = view.getParent()
                            if isinstance(view, ViewGroup_cls):
                                for i in range(view.getChildCount()):
                                    scan_view(view.getChildAt(i), depth + 1)

                        scan_view(search_root)

                if not install_btn:
                    log("[KPM] no install_btn found")
                    return

                if not parent_layout and bottom_container:
                    parent_layout = bottom_container
                if not bottom_container and parent_layout:
                    bottom_container = parent_layout

                if not parent_layout:
                    log("[KPM] no parent_layout found")
                    return

                log(f"[KPM] found install_btn, adding UI elements")

                context = sheet.getContext()
                resources_provider = None
                try:
                    curr_cls = sheet.getClass()
                    while curr_cls is not None:
                        try:
                            m = curr_cls.getDeclaredMethod("getResourceProvider")
                            m.setAccessible(True)
                            resources_provider = m.invoke(sheet)
                            if resources_provider:
                                break
                        except Exception:
                            pass
                        curr_cls = curr_cls.getSuperclass()
                except Exception:
                    pass
                if not resources_provider:
                    resources_provider = _get_field(sheet, "resourcesProvider")

                def _shield_choice(v):
                    try:
                        from ..views.sheets.analysis_sheet import show_analysis_choice, PluginAnalysisSheet
                        show_analysis_choice(
                            sheet,
                            lambda: PluginAnalysisSheet(context, file_path_str, plugin_id_raw, resources_provider).show(),
                            lambda: _on_report_click(sheet, plugin, file_path_str, plugin_id_raw),
                        )
                    except Exception as e:
                        log_error(e, context="shield choice")

                split_container = setup_split_install_button(
                    sheet,
                    install_btn,
                    bottom_container,
                    file_path_str,
                    plugin_id_raw,
                    resources_provider,
                    on_click=_shield_choice,
                )

                if dependencies:
                    deps_btn = ButtonWithCounterView(context, True, resources_provider)
                    deps_btn.setText(_tr("install_dependencies"), False)
                    deps_color = Theme_obj.getColor(Theme_obj.key_windowBackgroundWhiteBlueText, resources_provider) if resources_provider else Theme_obj.getColor(Theme_obj.key_windowBackgroundWhiteBlueText)
                    deps_bg = Theme_obj.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(16), deps_color, deps_color)
                    deps_btn.setBackground(deps_bg)

                    class DepsClickListener(dynamic_proxy(find_class("android.view.View$OnClickListener"))):
                        def __init__(self, plugin_instance, deps_list):
                            super().__init__()
                            self.plugin_instance = plugin_instance
                            self.deps_list = deps_list
                        def onClick(self, v):
                            log(f"[KPM] deps btn onClick fired, deps={self.deps_list}")
                            run_on_queue(lambda: self._install_dependencies())
                        def _install_dependencies(self):
                            log(f"[KPM] _install_dependencies started, deps={self.deps_list}")
                            success = 0
                            failed = 0
                            failed_deps = set()
                            for dep_id in self.deps_list:
                                try:
                                    log(f"[KPM] deps installing: {dep_id}")
                                    self.plugin_instance.install_plugin_by_id(dep_id, enable_after=True)
                                    log(f"[KPM] deps install_plugin_by_id returned for {dep_id}")
                                    success += 1
                                except Exception as dep_ex:
                                    log_error(dep_ex, context=f"deps install {dep_id}")
                                    failed += 1
                                    failed_deps.add(dep_id)
                            log(f"[KPM] deps done: success={success}, failed={failed}, failed_deps={failed_deps}")
                            if failed == 0:
                                title = _tr("dep_install_complete").format(success, len(self.deps_list))
                            else:
                                title = _tr("dep_install_error").format(success, len(self.deps_list), failed)
                            try:
                                def show_alert():
                                    try:
                                        from ui.alert import AlertDialogBuilder
                                        fragment = get_last_fragment()
                                        ctx = fragment.getParentActivity() if fragment else None
                                        log(f"[KPM] deps showing alert: {title}, ctx={ctx}")
                                        if ctx:
                                            builder = AlertDialogBuilder(ctx)
                                            builder.set_title(_tr("install_dependencies"))
                                            builder.set_message(title)
                                            builder.set_positive_button(_tr("done"), lambda d, w: d.dismiss())
                                            builder.show()
                                        else:
                                            from ui.bulletin import BulletinHelper
                                            BulletinHelper.show_info(title)
                                    except Exception as bul_ex:
                                        log_error(bul_ex, context="deps show_alert")
                                run_on_ui_thread(show_alert)
                            except Exception as bul_ex:
                                log_error(bul_ex, context="deps alert outer")

                    deps_btn.setOnClickListener(DepsClickListener(plugin, dependencies))
                    try:
                        install_btn.setText(_tr("install_button"), False)
                    except Exception:
                        pass

                    target_anchor = split_container if split_container else install_btn
                    target_parent = bottom_container if bottom_container else parent_layout
                    if target_parent and hasattr(target_parent, "findViewWithTag") and target_parent.findViewWithTag("kpm_deps_btn") is not None:
                        log("[KPM] deps_btn already exists in target_parent, skipping")
                    else:
                        deps_btn.setTag("kpm_deps_btn")
                        idx = -1
                        for i in range(target_parent.getChildCount()):
                            if target_parent.getChildAt(i) == target_anchor:
                                idx = i
                                break
                        if idx >= 0:
                            try:
                                old_lp = target_anchor.getLayoutParams()
                                if isinstance(old_lp, LinearLayout.LayoutParams):
                                    deps_lp = LinearLayout.LayoutParams(old_lp)
                                    deps_lp.topMargin = AndroidUtilities.dp(8)
                                    deps_lp.bottomMargin = AndroidUtilities.dp(8)
                                    target_parent.addView(deps_btn, idx, deps_lp)
                            except Exception:
                                pass

                try:
                    target_parent = bottom_container if bottom_container else parent_layout
                    if target_parent and hasattr(target_parent, "findViewWithTag"):
                        old_check = target_parent.findViewWithTag("kpm_check_btn")
                        if old_check and hasattr(old_check, "getParent") and old_check.getParent():
                            try:
                                old_check.getParent().removeView(old_check)
                            except Exception:
                                pass
                    log("[KPM] calling _setup_top_actions...")
                    _setup_top_actions(sheet, plugin, file_path_str, plugin_id_raw, custom_view=custom_view, install_params=install_params)
                except Exception as e:
                    log_error(e, context="top actions setup")

                try:
                    log("[KPM] resolving status for folder strip...")
                    status = None
                    in_repo = False
                    if plugin_id_raw:
                        if not plugin.plugins_list:
                            log("[KPM] store empty, refreshing...")
                            try:
                                plugin.store.refresh(force=True)
                            except Exception as e:
                                log_error(e, context="store refresh in folder strip")
                        store = plugin.plugins_list or {}
                        matched_key = next((k for k in store if k.lower() == plugin_id_raw.lower()), None)
                        if matched_key:
                            in_repo = True
                            pi = store.get(matched_key)
                            if isinstance(pi, dict):
                                status = pi.get("status")
                                log(f"[KPM] status from store match '{matched_key}': {status}")
                        else:
                            log(f"[KPM] no store match for '{plugin_id_raw}'")
                    if in_repo and not status:
                        fi = plugin.store.fetch_plugin_full_info(plugin_id_raw)
                        if fi and isinstance(fi, dict):
                            status = fi.get("status")
                        log(f"[KPM] status fallback via fetch_plugin_full_info: {status}")
                    if not in_repo:
                        status = "plugin"
                    status_label = _status_label(status)
                    log(f"[KPM] status: {status} -> {status_label}, calling _setup_folder_strip...")
                    _setup_folder_strip(sheet, custom_view, status_label)
                except Exception as f_ex:
                    log_error(f_ex, context="folder strip setup")

                try:
                    log("[KPM] calling _setup_description_with_certificate...")
                    _setup_description_with_certificate(sheet, plugin, plugin_id_raw, file_path_str, custom_view=custom_view)
                except Exception as c_ex:
                    log_error(c_ex, context="cert setup")

                _modified_sheets[0].add(sheet_id)
                log("[KPM] _modify_install_sheet_ui completed successfully!")
            except Exception as e:
                log_error(e, context="_modify_install_sheet_ui fatal")

        PythonPluginsEngine = find_class("com.exteragram.messenger.plugins.PythonPluginsEngine")
        PluginsController = find_class("com.exteragram.messenger.plugins.PluginsController")
        BaseFragment = find_class("org.telegram.ui.ActionBar.BaseFragment")
        PluginInstallParams = find_class("com.exteragram.messenger.plugins.ui.components.InstallPluginBottomSheet$PluginInstallParams")
        String = find_class("java.lang.String")

        if PythonPluginsEngine:
            try:
                pl._track_hooks(pl.hook_all_methods(PythonPluginsEngine, "showInstallDialog", InstallDialogGuardHook(pl)))
                log("[KPM] PythonPluginsEngine.showInstallDialog hook installed")
            except Exception as e:
                log_error(e, context="PythonPluginsEngine hook")

        InstallPluginBottomSheet_cls = find_class("com.exteragram.messenger.plugins.ui.components.InstallPluginBottomSheet")
        log(f"[KPM] InstallPluginBottomSheet class: {InstallPluginBottomSheet_cls}")
        if InstallPluginBottomSheet_cls:
            try:
                class ConstructorHook(MethodHook):
                    def __init__(self, plugin):
                        self.plugin = plugin

                    def before_hooked_method(self, param):
                        try:
                            log("[KPM] InstallPluginBottomSheet constructor BEFORE")
                        except Exception:
                            pass

                    def after_hooked_method(self, param):
                        try:
                            sheet = param.thisObject
                            log(f"[KPM] InstallPluginBottomSheet constructor AFTER: sheet={sheet}")
                            if sheet:
                                _install_sheet_ids[0].add(_get_sheet_id(sheet))
                                run_on_ui_thread(lambda: _handle_sheet(self.plugin, sheet))
                        except Exception as e:
                            log_error(e, context="ConstructorHook")

                class ApplyButtonStateHook(MethodHook):
                    def __init__(self, plugin):
                        self.plugin = plugin

                    def after_hooked_method(self, param):
                        try:
                            sheet = param.thisObject
                            log(f"[KPM] ApplyButtonStateHook after: sheet={sheet}")
                            if sheet:
                                _handle_sheet(self.plugin, sheet)
                        except Exception as e:
                            log_error(e, context="ApplyButtonStateHook")

                class OnViewCreatedHook(MethodHook):
                    def __init__(self, plugin):
                        self.plugin = plugin

                    def after_hooked_method(self, param):
                        try:
                            sheet = param.thisObject
                            log(f"[KPM] OnViewCreatedHook after: sheet={sheet}")
                            if sheet:
                                run_on_ui_thread(lambda: _handle_sheet(self.plugin, sheet))
                        except Exception:
                            pass

                class RestoreTextHook(MethodHook):
                    def __init__(self, plugin):
                        self.plugin = plugin
                    def after_hooked_method(self, param):
                        try:
                            sheet = param.thisObject
                            log(f"[KPM] RestoreTextHook after: sheet={sheet}")
                            if sheet:
                                run_on_ui_thread(lambda: _handle_sheet(self.plugin, sheet))
                        except Exception:
                            pass

                class SetLastVisibleHook(MethodHook):
                    def __init__(self, plugin):
                        self.plugin = plugin
                    def after_hooked_method(self, param):
                        try:
                            sheet = param.thisObject
                            log(f"[KPM] InstallPluginBottomSheet.setLastVisible after: sheet={sheet}")
                            if sheet:
                                run_on_ui_thread(lambda: _handle_sheet(self.plugin, sheet))
                        except Exception as e:
                            log_error(e, context="SetLastVisibleHook")

                try:
                    pl._track_hooks(pl.hook_all_constructors(InstallPluginBottomSheet_cls, ConstructorHook(pl)))
                    log("[KPM] hook_all_constructors registered for InstallPluginBottomSheet")
                except Exception as he:
                    log_error(he, context="hook_all_constructors InstallPluginBottomSheet")

                try:
                    pl._track_hooks(pl.hook_all_methods(InstallPluginBottomSheet_cls, "setLastVisible", SetLastVisibleHook(pl)))
                except Exception:
                    pass
                try:
                    pl._track_hooks(pl.hook_all_methods(InstallPluginBottomSheet_cls, "applyButtonState", ApplyButtonStateHook(pl)))
                except Exception:
                    pass
                try:
                    pl._track_hooks(pl.hook_all_methods(InstallPluginBottomSheet_cls, "onViewCreated", OnViewCreatedHook(pl)))
                except Exception:
                    pass
                class OpenPluginFileHook(MethodHook):
                    def __init__(self, plugin):
                        self.plugin = plugin

                    def before_hooked_method(self, param):
                        self._handle(param)

                    def beforeHookedMethod(self, param):
                        self._handle(param)

                    def _handle(self, param):
                        try:
                            sheet = param.thisObject
                            if not sheet:
                                return
                            params_obj = getattr(sheet, "params", None) or _get_field(sheet, "params")
                            if not params_obj:
                                return
                            file_path = str(params_obj.getFilePath())
                            if not file_path or not os.path.exists(file_path):
                                return

                            log(f"[KPM] openPluginFile hooked for: {file_path}")
                            from ..views.sheets.code_viewer import is_elyx_archive, open_local_file_or_archive

                            beta_viewer = is_beta_code_viewer_enabled(self.plugin)
                            if not beta_viewer:
                                log(f"[KPM] openPluginFile: beta_code_viewer disabled, passing to system viewer")
                                return

                            file_name = os.path.basename(file_path)
                            try:
                                p_obj = getattr(sheet, "plugin", None) or _get_field(sheet, "plugin")
                                if p_obj and hasattr(p_obj, "getId"):
                                    pid = str(p_obj.getId())
                                    ext = os.path.splitext(file_path)[1] or (".eaf" if is_elyx_archive(file_path) else ".plugin")
                                    file_name = f"{pid}{ext}"
                            except Exception:
                                pass

                            fragment = getattr(sheet, "fragment", None) or _get_field(sheet, "fragment")
                            if open_local_file_or_archive(file_path, title=file_name, target_fragment=fragment, use_sheet=True):
                                try:
                                    sheet.dismiss()
                                except Exception:
                                    pass
                                param.setResult(None)
                        except Exception as e:
                            log_error(e, context="OpenPluginFileHook")

                PluginFileViewer = (
                    find_class("com.exteragram.messenger.plugins.ui.components.PluginFileViewer")
                    or find_class("app.exteraless.plugins.ui.components.PluginFileViewer")
                )
                if PluginFileViewer:
                    class PluginFileViewerHook(MethodHook):
                        def __init__(self, plugin):
                            self.plugin = plugin

                        def before_hooked_method(self, param):
                            self._handle(param)

                        def beforeHookedMethod(self, param):
                            self._handle(param)

                        def _handle(self, param):
                            try:
                                args = param.args
                                if not args or len(args) < 2:
                                    return
                                target_file = args[1]
                                if not target_file:
                                    return
                                file_path = str(target_file.getAbsolutePath()) if hasattr(target_file, "getAbsolutePath") else str(target_file)
                                if not file_path or not os.path.exists(file_path):
                                    return
                                if "/files/kpm/" in file_path.replace("\\", "/"):
                                    return

                                beta_viewer = is_beta_code_viewer_enabled(self.plugin)
                                if not beta_viewer:
                                    return

                                file_name = os.path.basename(file_path)
                                if len(args) > 2 and args[2]:
                                    file_name = str(args[2])

                                fn_lower = str(file_name).lower()
                                fp_lower = str(file_path).lower()
                                if fn_lower.endswith(".txt") or fp_lower.endswith(".txt") or "log" in fn_lower or "log" in fp_lower:
                                    return

                                ext = os.path.splitext(file_path)[1].lower()
                                if ext not in (".py", ".eaf", ".plugin", ".zip"):
                                    return

                                from ..views.sheets.code_viewer import open_local_file_or_archive
                                fragment = args[0]
                                if open_local_file_or_archive(file_path, title=file_name, target_fragment=fragment, use_sheet=True):
                                    param.setResult(True)
                            except Exception as e:
                                log_error(e, context="PluginFileViewer.open hook")

                    try:
                        pl._track_hooks(pl.hook_all_methods(PluginFileViewer, "open", PluginFileViewerHook(pl)))
                        log("[KPM] hook PluginFileViewer.open registered")
                    except Exception as fe:
                        log_error(fe, context="hook PluginFileViewer.open")

                try:
                    pl._track_hooks(pl.hook_all_methods(InstallPluginBottomSheet_cls, "openPluginFile", OpenPluginFileHook(pl)))
                    log("[KPM] hook openPluginFile registered for InstallPluginBottomSheet")
                except Exception as he:
                    log_error(he, context="hook openPluginFile")

                try:
                    pl._track_hooks(pl.hook_all_methods(InstallPluginBottomSheet_cls, "restoreButtonText", RestoreTextHook(pl)))
                except Exception:
                    pass
                log("[KPM] InstallPluginBottomSheet method hooks installed")
            except Exception as e:
                log_error(e, context="InstallPluginBottomSheet hooks")

        if PluginsController:
            try:
                class PCShowHook(MethodHook):
                    def __init__(self, plugin):
                        self.plugin = plugin

                    def _resolve_full_path(self, msg_obj, synthetic_name):
                        try:
                            FileLoader = find_class("org.telegram.messenger.FileLoader")
                            UserConfig = find_class("org.telegram.messenger.UserConfig")
                            account = UserConfig.selectedAccount
                            loader = FileLoader.getInstance(account)
                            msg_owner_f = msg_obj.getClass().getDeclaredField("messageOwner")
                            msg_owner_f.setAccessible(True)
                            msg_owner = msg_owner_f.get(msg_obj)
                            if msg_owner:
                                full_file = loader.getPathToMessage(msg_owner, False, False)
                                if full_file and full_file.exists():
                                    return str(full_file.getAbsolutePath())
                        except Exception as e:
                            log_error(e, context="FileLoader.getPathToMessage")
                        try:
                            FileLoader = find_class("org.telegram.messenger.FileLoader")
                            UserConfig = find_class("org.telegram.messenger.UserConfig")
                            account = UserConfig.selectedAccount
                            loader = FileLoader.getInstance(account)
                            get_doc = msg_obj.getClass().getDeclaredMethod("getDocument")
                            get_doc.setAccessible(True)
                            doc = get_doc.invoke(msg_obj)
                            if doc:
                                full_file = loader.getPathToAttach(doc, None, False, False)
                                if full_file and full_file.exists():
                                    return str(full_file.getAbsolutePath())
                        except Exception as e:
                            log_error(e, context="FileLoader.getPathToAttach")
                        return None

                    def before_hooked_method(self, param):
                        try:
                            log(f"[KPM] PluginsController.showInstallDialog called, args_len={len(param.args) if param.args else 0}")
                            if not param.args or len(param.args) < 2:
                                return
                            arg1 = param.args[1]
                            if not arg1:
                                return
                            file_path_str = None
                            MessageObject = _get_cached_class("org.telegram.messenger.MessageObject")
                            if MessageObject and isinstance(arg1, MessageObject):
                                synthetic_name = None
                                try:
                                    get_file = arg1.getClass().getDeclaredMethod("getFileName")
                                    get_file.setAccessible(True)
                                    result = get_file.invoke(arg1)
                                    if result:
                                        synthetic_name = str(result)
                                except Exception:
                                    pass
                                if synthetic_name and os.path.isfile(synthetic_name):
                                    file_path_str = synthetic_name
                                else:
                                    resolved = self._resolve_full_path(arg1, synthetic_name)
                                    if resolved:
                                        file_path_str = resolved
                                    elif synthetic_name:
                                        file_path_str = synthetic_name
                            elif hasattr(arg1, "getFilePath"):
                                file_path_str = str(arg1.getFilePath())
                            elif isinstance(arg1, str) or str(type(arg1).__name__) in ("String", "str"):
                                file_path_str = str(arg1)

                            if file_path_str:
                                _pending_file_path[0] = file_path_str
                                log(f"[KPM] PCShowHook: _pending_file_path={file_path_str}")
                                file_ext = file_path_str.lower()
                                if file_ext.endswith(('.eaf', '.elyx', '.zip', '.plugin')):
                                    plugin_ref = self.plugin
                                    from ..elyx_parser import read_plugin_id_from_file
                                    pid = read_plugin_id_from_file(file_path_str)
                                    if pid:
                                        try:
                                            is_safe = plugin_ref._check_plugin_safety(pid, file_path_str)
                                            in_store = any(k.lower() == pid.lower() for k in (plugin_ref.plugins_list or {}))
                                            if not is_safe or not in_store:
                                                try:
                                                    blocking = str(plugin_ref.get_setting("block_untrusted_plugins", True)).lower()
                                                    if blocking not in ("false", "0", "none", "no", ""):
                                                        param.setResult(None)
                                                        log(f"[KPM] PCShowHook: blocking untrusted plugin {pid}")
                                                        run_on_ui_thread(lambda: plugin_ref._show_untrusted_warning(pid))
                                                        return
                                                except Exception:
                                                    pass
                                            else:
                                                if len(param.args) > 2 and isinstance(param.args[2], bool):
                                                    param.args[2] = True
                                                elif hasattr(arg1, "setTrusted"):
                                                    try:
                                                        arg1.setTrusted(True)
                                                    except Exception:
                                                        pass
                                        except Exception as e:
                                            log_error(e, context="PCShowHook safety check")
                        except Exception as e:
                            log_error(e, context="PCShowHook")

                    def after_hooked_method(self, param):
                        try:
                            log("[KPM] PluginsController.showInstallDialog AFTER")
                        except Exception as pe:
                            log_error(pe, context="PCShowHook after")

                pl._track_hooks(pl.hook_all_methods(PluginsController, "showInstallDialog", PCShowHook(pl)))
                log("[KPM] PluginsController.showInstallDialog hook installed")
            except Exception as e:
                log_error(e, context="PluginsController hook")

        BottomSheet = find_class("org.telegram.ui.ActionBar.BottomSheet")
        log(f"[KPM] BottomSheet class: {BottomSheet}")
        if BottomSheet:
            try:
                class SetCustomViewHook(MethodHook):
                    def __init__(self, plugin):
                        self.plugin = plugin
                    def after_hooked_method(self, param):
                        try:
                            sheet = param.thisObject
                            view = param.args[0] if param.args else None
                            if sheet:
                                if getattr(sheet, "_kpm_custom_sheet", False) or (view and getattr(view, "getTag", None) and str(view.getTag()) == "kpm_web_ide"):
                                    return
                                sheet_id = _get_sheet_id(sheet)
                                cls_name = str(sheet.getClass().getName())
                                is_install = sheet_id in _install_sheet_ids[0]
                                if "InstallPluginBottomSheet" in cls_name:
                                    _install_sheet_ids[0].add(sheet_id)
                                    is_install = True
                                elif not is_install and _pending_file_path[0] is not None and cls_name != "org.telegram.ui.ActionBar.BottomSheet":
                                    is_install = True
                                log(f"[KPM] BottomSheet.setCustomView for: {cls_name}, view={view}, is_install={is_install}")
                                if is_install:
                                    run_on_ui_thread(lambda: _handle_sheet(self.plugin, sheet, custom_view=view))
                        except Exception as e:
                            log_error(e, context="SetCustomViewHook")

                class OnAttachedHook(MethodHook):
                    def __init__(self, plugin):
                        self.plugin = plugin
                    def after_hooked_method(self, param):
                        try:
                            sheet = param.thisObject
                            if sheet:
                                if getattr(sheet, "_kpm_custom_sheet", False):
                                    return
                                sheet_id = _get_sheet_id(sheet)
                                cls_name = str(sheet.getClass().getName())
                                is_install = sheet_id in _install_sheet_ids[0]
                                if "InstallPluginBottomSheet" in cls_name:
                                    _install_sheet_ids[0].add(sheet_id)
                                    is_install = True
                                elif not is_install and _pending_file_path[0] is not None and cls_name != "org.telegram.ui.ActionBar.BottomSheet":
                                    is_install = True
                                log(f"[KPM] BottomSheet.onAttachedToWindow for: {cls_name}, is_install={is_install}")
                                if is_install:
                                    run_on_ui_thread(lambda: _handle_sheet(self.plugin, sheet))
                        except Exception as e:
                            log_error(e, context="OnAttachedHook")

                class BottomSheetShowHook(MethodHook):
                    def __init__(self, plugin):
                        self.plugin = plugin

                    def before_hooked_method(self, param):
                        try:
                            sheet = param.thisObject
                            if not sheet or getattr(sheet, "_kpm_custom_sheet", False):
                                return
                            sheet_id = _get_sheet_id(sheet)
                            cls_name = str(sheet.getClass().getName())
                            is_install = sheet_id in _install_sheet_ids[0]
                            if "InstallPluginBottomSheet" in cls_name:
                                _install_sheet_ids[0].add(sheet_id)
                                is_install = True
                            elif not is_install and _pending_file_path[0] is not None and cls_name != "org.telegram.ui.ActionBar.BottomSheet":
                                is_install = True
                            log(f"[KPM] BottomSheet.show (before) for: {cls_name}, is_install={is_install}")
                            if is_install:
                                run_on_ui_thread(lambda: _handle_sheet(self.plugin, sheet))
                        except Exception as e:
                            log_error(e, context="BottomSheet show hook before")

                    def after_hooked_method(self, param):
                        try:
                            sheet = param.thisObject
                            if not sheet or getattr(sheet, "_kpm_custom_sheet", False):
                                return
                            sheet_id = _get_sheet_id(sheet)
                            cls_name = str(sheet.getClass().getName())
                            is_install = sheet_id in _install_sheet_ids[0]
                            if "InstallPluginBottomSheet" in cls_name:
                                _install_sheet_ids[0].add(sheet_id)
                                is_install = True
                            elif not is_install and _pending_file_path[0] is not None and cls_name != "org.telegram.ui.ActionBar.BottomSheet":
                                is_install = True
                            log(f"[KPM] BottomSheet.show (after) for: {cls_name}, is_install={is_install}")
                            if is_install:
                                run_on_ui_thread(lambda: _handle_sheet(self.plugin, sheet))
                        except Exception as e:
                            log_error(e, context="BottomSheet show hook after")

                try:
                    pl._track_hooks(pl.hook_all_methods(BottomSheet, "setCustomView", SetCustomViewHook(pl)))
                    log("[KPM] BottomSheet.setCustomView hook installed")
                except Exception as e:
                    log_error(e, context="BottomSheet.setCustomView hook")

                try:
                    pl._track_hooks(pl.hook_all_methods(BottomSheet, "onAttachedToWindow", OnAttachedHook(pl)))
                    log("[KPM] BottomSheet.onAttachedToWindow hook installed")
                except Exception as e:
                    log_error(e, context="BottomSheet.onAttachedToWindow hook")

                pl._track_hooks(pl.hook_all_methods(BottomSheet, "show", BottomSheetShowHook(pl)))
                if InstallPluginBottomSheet_cls:
                    pl._track_hooks(pl.hook_all_methods(InstallPluginBottomSheet_cls, "show", BottomSheetShowHook(pl)))
                log("[KPM] BottomSheet.show hook installed")
            except Exception as e:
                log_error(e, context="BottomSheet.show hook")

    except Exception as e:
        log_error(e, context="install_install_sheet_hook")
