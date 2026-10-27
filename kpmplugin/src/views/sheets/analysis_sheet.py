import os
import threading
import traceback

from android_utils import OnClickListener, run_on_ui_thread
from java import dynamic_proxy
from hook_utils import find_class
from java import jint
from org.telegram.messenger import AndroidUtilities
from org.telegram.ui.ActionBar import Theme
from org.telegram.ui.Components import LayoutHelper

from ...methods import log, log_error, _tr
from ...security.plugin_analysis import analyze_plugin, AnalysisCancelled


def _field(target, name):
    current = target.getClass() if target else None
    while current:
        try:
            field = current.getDeclaredField(name)
            field.setAccessible(True)
            return field.get(target)
        except Exception:
            current = current.getSuperclass()
    return None


def _find_tag(view, tag):
    if not view:
        return None
    try:
        if str(view.getTag()) == tag:
            return view
    except Exception:
        pass
    if hasattr(view, "getChildCount"):
        for index in range(view.getChildCount()):
            found = _find_tag(view.getChildAt(index), tag)
            if found:
                return found
    return None


def _find_anchor(view, image_view_class):
    if not view:
        return None
    try:
        if isinstance(view, image_view_class) and view.isClickable():
            return view
    except Exception:
        pass
    if hasattr(view, "getChildCount"):
        for index in range(view.getChildCount() - 1, -1, -1):
            found = _find_anchor(view.getChildAt(index), image_view_class)
            if found:
                return found
    return None


STATUS_SVG_MAP = {
    "verified": "safe.svg",
    "failed": "warning.svg",
    "unknown": "question.svg",
}

STATUS_ICON_NAMES = {
    "plain": ("outline_shield_plain_24", "shield_plain_24"),
}


def _shield_drawable(context, size, color, status="plain"):
    if status in STATUS_SVG_MAP:
        try:
            from android.graphics import PorterDuff, PorterDuffColorFilter
            from android.graphics.drawable import BitmapDrawable
            from org.telegram.messenger import AndroidUtilities, SvgHelper
            import elyx

            svg_name = STATUS_SVG_MAP[status]
            asset = elyx.assets.get(f"statues/{svg_name}")
            if asset is not None:
                size_px = AndroidUtilities.dp(size) if size < 100 else size
                d = None
                try:
                    d = asset.to_svg_drawable(width=size_px, height=size_px)
                except Exception:
                    pass
                if d is None:
                    try:
                        d = asset.to_svg_drawable()
                    except Exception:
                        pass
                if d is None:
                    try:
                        content = asset.content_string()
                        bmp = SvgHelper.getBitmap(content, size_px, size_px, False)
                        if bmp:
                            d = BitmapDrawable(context.getResources(), bmp)
                    except Exception:
                        pass
                if d is not None:
                    d = d.mutate()
                    if color is not None:
                        d.setColorFilter(PorterDuffColorFilter(jint(int(color)), PorterDuff.Mode.SRC_IN))
                    return d
        except Exception as e_asset:
            log_error(e_asset, context="_shield_drawable svg asset")

    try:
        from android.graphics import PorterDuff, PorterDuffColorFilter
        from androidx.core.content import ContextCompat
        res = context.getResources()
        pkg = context.getPackageName()
        res_id = 0
        names = STATUS_ICON_NAMES.get("plain", ("outline_shield_plain_24", "shield_plain_24"))
        for name in names:
            try:
                rid = int(res.getIdentifier(name, "drawable", pkg))
                if rid:
                    res_id = rid
                    break
            except Exception:
                pass

        if res_id:
            drawable = ContextCompat.getDrawable(context, res_id)
            if drawable:
                d = drawable.mutate()
                if color is not None:
                    d.setColorFilter(PorterDuffColorFilter(jint(int(color)), PorterDuff.Mode.SRC_IN))
                return d
    except Exception as error:
        log_error(error, context="system shield drawable")
    return None


def _resolve_drawable(context, names):
    try:
        from androidx.core.content import ContextCompat
        res = context.getResources()
        pkg = context.getPackageName()
        for name in names:
            try:
                rid = int(res.getIdentifier(name, "drawable", pkg))
                if rid:
                    drawable = ContextCompat.getDrawable(context, rid)
                    if drawable:
                        return drawable.mutate()
            except Exception:
                continue
    except Exception as error:
        log_error(error, context="resolve drawable")
    return None


def apply_left_button_style(install_btn, bg_color, text_color):
    try:
        bg_left = Theme.createRoundRectDrawable(
            AndroidUtilities.dp(24), AndroidUtilities.dp(4),
            AndroidUtilities.dp(4), AndroidUtilities.dp(24),
            int(bg_color)
        )
        install_btn.setBackground(bg_left)

        ripple_color = (int(text_color) & 0x00FFFFFF) | 0x33000000
        try:
            ripple_view = install_btn.rippleView
            if ripple_view:
                ripple_view.setBackground(
                    Theme.createRadSelectorDrawable(
                        int(ripple_color),
                        24, 4, 4, 24
                    )
                )
        except Exception:
            pass
    except Exception as e:
        log_error(e, context="apply_left_button_style")


def create_split_analysis_button(context, file_path, plugin_id, resources_provider=None, bg_color=None, text_color=None, sheet=None, on_click=None, radius=24):
    try:
        FrameLayout = find_class("android.widget.FrameLayout")
        ImageView = find_class("android.widget.ImageView")
        View = find_class("android.view.View")
        Gravity = find_class("android.view.Gravity")
        MotionEvent = find_class("android.view.MotionEvent")
        OnTouchInterface = find_class("android.view.View$OnTouchListener")
        ScaleStateListAnimator = find_class("org.telegram.ui.Components.ScaleStateListAnimator")

        if not FrameLayout or not ImageView or not View:
            log("[KPM] create_split_analysis_button: missing classes")
            return None

        if bg_color is None:
            bg_color = Theme.getColor(Theme.key_featuredStickers_addButton, resources_provider) if resources_provider else Theme.getColor(Theme.key_featuredStickers_addButton)
        if text_color is None:
            text_color = Theme.getColor(Theme.key_featuredStickers_buttonText, resources_provider) if resources_provider else Theme.getColor(Theme.key_featuredStickers_buttonText)

        container = FrameLayout(context)
        container.setTag("kpm_plugin_analysis")
        container.setContentDescription(_tr("a_analyze"))
        container.setClickable(True)
        container.setFocusable(True)

        state = {
            "bg_color": bg_color,
            "text_color": text_color,
            "ripple_color": (int(text_color) & 0x00FFFFFF) | 0x33000000,
            "bg_norm": None,
            "bg_press": None,
            "rip_norm": None,
            "rip_press": None,
            "is_pressed": False,
        }

        def rebuild_drawables(new_bg, new_text):
            state["bg_color"] = new_bg
            state["text_color"] = new_text
            state["ripple_color"] = (int(new_text) & 0x00FFFFFF) | 0x33000000

            state["bg_norm"] = Theme.createRoundRectDrawable(
                AndroidUtilities.dp(4), AndroidUtilities.dp(radius),
                AndroidUtilities.dp(radius), AndroidUtilities.dp(4),
                int(state["bg_color"])
            )
            state["bg_press"] = Theme.createRoundRectDrawable(
                AndroidUtilities.dp(radius), AndroidUtilities.dp(radius),
                AndroidUtilities.dp(radius), AndroidUtilities.dp(radius),
                int(state["bg_color"])
            )

            try:
                state["rip_norm"] = Theme.createRadSelectorDrawable(int(state["ripple_color"]), 4, radius, radius, 4)
                state["rip_press"] = Theme.createRadSelectorDrawable(int(state["ripple_color"]), radius, radius, radius, radius)
            except Exception:
                state["rip_norm"] = None
                state["rip_press"] = None

        rebuild_drawables(bg_color, text_color)
        container.setBackground(state["bg_norm"])

        ripple_view = View(context)
        if state["rip_norm"]:
            ripple_view.setBackground(state["rip_norm"])
        container.addView(ripple_view, LayoutHelper.createFrame(-1, -1))

        icon_view = ImageView(context)
        icon_view.setImageDrawable(_shield_drawable(context, 22, text_color, status="plain"))
        icon_view.setScaleType(ImageView.ScaleType.CENTER)
        container.addView(icon_view, LayoutHelper.createFrame(-1, -1, Gravity.CENTER))

        if ScaleStateListAnimator:
            try:
                ScaleStateListAnimator.apply(container)
            except Exception:
                pass

        try:
            if OnTouchInterface and MotionEvent:
                OnTouch = dynamic_proxy(OnTouchInterface)

                class AnalysisTouchListener(OnTouch):
                    def onTouch(self, v, event):
                        try:
                            action = event.getAction() & 0xFF
                            if action == 0:
                                if not self.s_ref["is_pressed"]:
                                    self.s_ref["is_pressed"] = True
                                    if self.s_ref["bg_press"]:
                                        self.c_view.setBackground(self.s_ref["bg_press"])
                                    if self.r_view and self.s_ref["rip_press"]:
                                        self.r_view.setBackground(self.s_ref["rip_press"])
                            elif action in (1, 3):
                                if self.s_ref["is_pressed"]:
                                    self.s_ref["is_pressed"] = False
                                    if self.s_ref["bg_norm"]:
                                        self.c_view.setBackground(self.s_ref["bg_norm"])
                                    if self.r_view and self.s_ref["rip_norm"]:
                                        self.r_view.setBackground(self.s_ref["rip_norm"])
                        except Exception:
                            pass
                        return False

                touch_listener = AnalysisTouchListener()
                touch_listener.c_view = container
                touch_listener.r_view = ripple_view
                touch_listener.s_ref = state
                container.setOnTouchListener(touch_listener)
        except Exception as te:
            log_error(te, context="split analysis button OnTouchListener")

        def _on_analysis_click(_):
            try:
                fp = file_path
                pid = plugin_id
                if not fp and sheet:
                    p = _field(sheet, "params")
                    if p and hasattr(p, "getFilePath"):
                        fp = str(p.getFilePath())
                if not pid and sheet:
                    po = _field(sheet, "plugin")
                    if po and hasattr(po, "getId"):
                        pid = str(po.getId())
                PluginAnalysisSheet(context, fp, pid, resources_provider).show()
            except Exception as e:
                log_error(e, context="on_analysis_click")

        container.setOnClickListener(OnClickListener(on_click if on_click is not None else _on_analysis_click))

        def update_colors(new_bg, new_text):
            try:
                rebuild_drawables(new_bg, new_text)
                container.setBackground(state["bg_press"] if state["is_pressed"] else state["bg_norm"])
                if ripple_view and state["rip_norm"]:
                    ripple_view.setBackground(state["rip_press"] if state["is_pressed"] else state["rip_norm"])
                icon_view.setImageDrawable(_shield_drawable(context, 22, new_text, status="plain"))
            except Exception as ce:
                log_error(ce, context="update_colors")

        container._update_colors = update_colors

        return container
    except Exception as e:
        log_error(e, context="create_split_analysis_button")
        return None


def setup_split_install_button(sheet, install_btn, bottom_container, file_path, plugin_id, resources_provider=None, on_click=None):
    try:
        log(f"[KPM] setup_split_install_button ENTER: btn={install_btn}, bc={bottom_container}")
        LinearLayout = find_class("android.widget.LinearLayout")
        if not install_btn or not bottom_container or not LinearLayout:
            log(f"[KPM] setup_split_install_button: missing views/classes: btn={install_btn}, bc={bottom_container}, LL={LinearLayout}")
            return None

        curr_parent = install_btn.getParent()
        if curr_parent and str(curr_parent.getTag()) == "kpm_split_container":
            log("[KPM] install_btn already in split_container")
            return curr_parent

        context = bottom_container.getContext()
        bg_color = Theme.getColor(Theme.key_featuredStickers_addButton, resources_provider) if resources_provider else Theme.getColor(Theme.key_featuredStickers_addButton)
        text_color = Theme.getColor(Theme.key_featuredStickers_buttonText, resources_provider) if resources_provider else Theme.getColor(Theme.key_featuredStickers_buttonText)

        try:
            bf = install_btn.getClass().getDeclaredField("backgroundColor")
            bf.setAccessible(True)
            custom_bg = bf.getInt(install_btn)
            if custom_bg != 0:
                bg_color = custom_bg
        except Exception:
            pass

        analysis_btn = create_split_analysis_button(
            context, file_path, plugin_id, resources_provider,
            bg_color=bg_color, text_color=text_color, sheet=sheet, on_click=on_click
        )
        if not analysis_btn:
            log("[KPM] could not create analysis_btn")
            return None

        btn_idx = -1
        bc_count = bottom_container.getChildCount() if hasattr(bottom_container, "getChildCount") else 0
        for i in range(bc_count):
            c = bottom_container.getChildAt(i)
            if c == install_btn or (hasattr(c, 'equals') and c.equals(install_btn)):
                btn_idx = i
                break

        log(f"[KPM] setup_split_install_button: found btn_idx={btn_idx} of {bc_count}")
        if btn_idx < 0:
            btn_idx = 0

        orig_lp = install_btn.getLayoutParams()
        
        p = install_btn.getParent()
        if p and hasattr(p, 'removeView'):
            p.removeView(install_btn)

        split_container = LinearLayout(context)
        split_container.setTag("kpm_split_container")
        split_container.setOrientation(LinearLayout.HORIZONTAL)
        
        split_lp = LinearLayout.LayoutParams(-1, AndroidUtilities.dp(48))
        if orig_lp and hasattr(orig_lp, 'leftMargin'):
            split_lp.leftMargin = orig_lp.leftMargin
            split_lp.rightMargin = orig_lp.rightMargin
            split_lp.topMargin = orig_lp.topMargin
            split_lp.bottomMargin = orig_lp.bottomMargin
        else:
            bg_pad = getattr(sheet, "backgroundPaddingLeft", 0) or 0
            gap = AndroidUtilities.dp(12)
            split_lp.setMargins(bg_pad + gap, gap, bg_pad + gap, gap)
        split_container.setLayoutParams(split_lp)

        apply_left_button_style(install_btn, bg_color, text_color)

        left_lp = LayoutHelper.createLinear(0, 48, 1.0, 0, 0, 3, 0)
        split_container.addView(install_btn, left_lp)

        right_lp = LayoutHelper.createLinear(48, 48)
        split_container.addView(analysis_btn, right_lp)

        cur_count = bottom_container.getChildCount()
        insert_idx = btn_idx if (0 <= btn_idx <= cur_count) else 0
        bottom_container.addView(split_container, insert_idx, split_lp)

        log(f"[KPM] MD3 split button successfully set up in bottom_container at index {insert_idx}")
        return split_container
    except Exception as error:
        log_error(error, context="setup_split_install_button")
        return None


def attach_analysis_button(sheet, file_path, plugin_id, resources_provider=None, install_btn=None, bottom_container=None, on_click=None):
    if install_btn and bottom_container:
        return setup_split_install_button(sheet, install_btn, bottom_container, file_path, plugin_id, resources_provider, on_click=on_click)
    return None


def show_analysis_choice(sheet, on_local, on_ai):
    try:
        from android.widget import LinearLayout, TextView, ImageView
        from android.view import Gravity
        from android.graphics import PorterDuff, PorterDuffColorFilter
        from androidx.core.widget import NestedScrollView
        from org.telegram.ui.ActionBar import BottomSheet
        from org.telegram.messenger import AndroidUtilities
        from org.telegram.ui.ActionBar import Theme

        ctx = sheet.getContext()
        rp = None
        try:
            rp = _field(sheet, "resourcesProvider")
        except Exception:
            pass

        try:
            accent = Theme.getColor(Theme.key_featuredStickers_addButton, rp) if rp else Theme.getColor(Theme.key_featuredStickers_addButton)
        except Exception:
            accent = 0xFF3399FF
        try:
            accent = int(accent)
            if accent >= 0x80000000:
                accent -= 0x100000000
        except Exception:
            pass

        bs = BottomSheet(ctx, True, rp)
        bs.setApplyBottomPadding(False)
        bs.setApplyTopPadding(False)
        try:
            bs.fixNavigationBar(Theme.getColor(Theme.key_dialogBackground))
        except Exception:
            pass

        ll = LinearLayout(ctx)
        ll.setOrientation(LinearLayout.VERTICAL)
        ll.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8))

        header = TextView(ctx)
        header.setText(_tr("a_analyze"))
        header.setTypeface(AndroidUtilities.bold())
        header.setTextSize(18)
        try:
            header.setTextColor(Theme.getColor(Theme.key_dialogTextBlack))
        except Exception:
            pass
        header.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(12), AndroidUtilities.dp(20), AndroidUtilities.dp(8))
        ll.addView(header)

        try:
            text_color = Theme.getColor(Theme.key_dialogTextBlack)
        except Exception:
            text_color = 0xFF000000

        def _pick(spec, icon_drawable):
            row = LinearLayout(ctx)
            row.setOrientation(LinearLayout.HORIZONTAL)
            row.setGravity(Gravity.CENTER_VERTICAL)
            try:
                row.setBackground(Theme.getSelectorDrawable(False))
            except Exception:
                pass
            row.setClickable(True)
            row.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(12), AndroidUtilities.dp(20), AndroidUtilities.dp(12))
            if icon_drawable is not None:
                try:
                    icon_drawable.setColorFilter(PorterDuffColorFilter(int(accent), PorterDuff.Mode.SRC_IN))
                except Exception:
                    pass
                iv = ImageView(ctx)
                iv.setImageDrawable(icon_drawable)
                row.addView(iv, LayoutHelper.createLinear(24, 24, Gravity.CENTER_VERTICAL, 0, 0, 16, 0))
            tv = TextView(ctx)
            tv.setText(spec)
            tv.setTextSize(16)
            try:
                tv.setTextColor(int(text_color))
            except Exception:
                pass
            row.addView(tv, LayoutHelper.createLinear(0, -2, 1.0, Gravity.CENTER_VERTICAL, 0, 0, 0, 0))
            return row

        local_icon = _shield_drawable(ctx, 24, accent, status="plain")
        ai_icon = _resolve_drawable(ctx, ("msg_bot", "msg_addbot"))

        local_row = _pick(_tr("a_local"), local_icon)
        ai_row = _pick(_tr("a_ai"), ai_icon)

        def _fire(cb):
            try:
                bs.dismiss()
            except Exception:
                pass
            try:
                if cb:
                    run_on_ui_thread(cb)
            except Exception as e:
                log_error(e, context="analysis choice callback")

        local_row.setOnClickListener(OnClickListener(lambda v: _fire(on_local)))
        ai_row.setOnClickListener(OnClickListener(lambda v: _fire(on_ai)))
        ll.addView(local_row, LayoutHelper.createLinear(-1, -2))
        ll.addView(ai_row, LayoutHelper.createLinear(-1, -2))

        sv = NestedScrollView(ctx)
        sv.addView(ll)
        bs.setCustomView(sv)
        bs.show()
    except Exception as e:
        log_error(e, context="show_analysis_choice")


class PluginAnalysisSheet:
    def __init__(self, context, file_path, plugin_id, resources_provider=None):
        self.context = context
        self.file_path = file_path
        self.plugin_id = plugin_id or os.path.basename(file_path)
        self.plugin_name = self._resolve_plugin_name(file_path, self.plugin_id)
        self.resources_provider = resources_provider
        self.sheet = None
        self.content = None
        self._dismissed = False

    def _resolve_plugin_name(self, file_path, default_val):
        try:
            if file_path and os.path.exists(file_path):
                from ...elyx_parser import extract_plugin_metadata
                meta = extract_plugin_metadata(file_path)
                if meta and meta.get("name"):
                    return meta["name"]
        except Exception:
            pass
        return default_val

    def show(self):
        try:
            self._build()
            self.sheet.show()
            self._start_analysis()
        except Exception as error:
            log_error(error, context="analysis sheet show")

    def _start_analysis(self):
        def _runner():
            import time
            time.sleep(0.05)
            if self._dismissed:
                return
            self._analyze()

        try:
            from client_utils import run_on_queue
            run_on_queue(_runner)
        except Exception as e:
            log_error(e, context="run_on_queue fallback to thread")
            threading.Thread(target=_runner, daemon=True).start()

    def _build(self):
        BottomSheet = find_class("org.telegram.ui.ActionBar.BottomSheet")
        LinearLayout = find_class("android.widget.LinearLayout")
        TextView = find_class("android.widget.TextView")
        ImageView = find_class("android.widget.ImageView")
        ProgressBar = find_class("android.widget.ProgressBar")
        NestedScrollView = find_class("androidx.core.widget.NestedScrollView")
        Gravity = find_class("android.view.Gravity")

        self.sheet = BottomSheet(self.context, False, self.resources_provider)
        self.sheet.setApplyBottomPadding(False)
        self.sheet.fixNavigationBar(Theme.getColor(Theme.key_windowBackgroundWhite))

        root = LinearLayout(self.context)
        root.setOrientation(LinearLayout.VERTICAL)
        root.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12))

        title = TextView(self.context)
        title.setText(f"{_tr('a_title')}: {self.plugin_name}")
        title.setTextSize(1, 19.0)
        title.setTypeface(AndroidUtilities.bold())
        title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack))
        title.setGravity(Gravity.CENTER)
        root.addView(title, LayoutHelper.createLinear(-1, -2, 20, 8, 20, 12))

        scroll = NestedScrollView(self.context)
        self.content = LinearLayout(self.context)
        self.content.setOrientation(LinearLayout.VERTICAL)
        self.content.setPadding(AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14), AndroidUtilities.dp(10))

        shield = ImageView(self.context)
        shield.setImageDrawable(_shield_drawable(self.context, 32, Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), status="plain"))
        self.content.addView(shield, LayoutHelper.createLinear(42, 42, Gravity.CENTER_HORIZONTAL, 0, 8, 0, 8))
        progress_view = None
        try:
            RadialProgressView = find_class("org.telegram.ui.Components.RadialProgressView")
            if RadialProgressView:
                try:
                    progress_view = RadialProgressView(self.context, self.resources_provider)
                except Exception:
                    progress_view = RadialProgressView(self.context)
                try:
                    progress_color = Theme.getColor(Theme.key_featuredStickers_addButton, self.resources_provider) if self.resources_provider else Theme.getColor(Theme.key_featuredStickers_addButton)
                except Exception:
                    progress_color = 0xFF3399FF
                try:
                    progress_view.setProgressColor(int(progress_color))
                except Exception:
                    pass
                try:
                    progress_view.setSize(AndroidUtilities.dp(28))
                except Exception:
                    pass
                self.content.addView(progress_view, LayoutHelper.createLinear(36, 36, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 10))
        except Exception as pe:
            log_error(pe, context="RadialProgressView init")

        if progress_view is None:
            progress = ProgressBar(self.context)
            progress.setIndeterminate(True)
            self.content.addView(progress, LayoutHelper.createLinear(28, 28, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 10))
        self._add_plain(_tr("a_loading"), 15, True, Gravity.CENTER)
        self._add_plain(_tr("a_loading_hint"), 13, False, Gravity.CENTER)

        scroll.addView(self.content, LayoutHelper.createScroll(-1, -2, 0))
        root.addView(scroll, LayoutHelper.createLinear(-1, -2))
        self.sheet.setCustomView(root)

        _sheet_ref = self
        DialogInterface = find_class("android.content.DialogInterface")

        class DismissListener(dynamic_proxy(DialogInterface.OnDismissListener)):
            def __init__(self, ref):
                super().__init__()
                self._ref = ref
            def onDismiss(self, dialog):
                self._ref._dismissed = True

        self.sheet.setOnDismissListener(DismissListener(_sheet_ref))

    def _analyze(self):
        try:
            if self._dismissed:
                return
            if not self.file_path or not os.path.exists(self.file_path):
                log(f"[KPM] analysis file_path not found or empty: {self.file_path}")
                run_on_ui_thread(self._render_error)
                return
            report = analyze_plugin(self.file_path, is_cancelled=lambda: self._dismissed)
            if self._dismissed:
                return
            run_on_ui_thread(lambda: self._render(report))
        except AnalysisCancelled:
            log("[KPM] plugin analysis cancelled by user dismiss")
        except Exception as error:
            log_error(error, context="plugin analysis")
            if not self._dismissed:
                run_on_ui_thread(self._render_error)

    def _render_error(self):
        try:
            if self._dismissed or not self.content:
                return
            self.content.removeAllViews()
            self._add_card(_tr("a_error"), [_tr("a_error")])
        except Exception as e:
            log_error(e, context="analysis render error")

    def _render(self, report):
        try:
            if self._dismissed:
                return
            self.content.animate().alpha(0.0).setDuration(90).start()
            self.content.removeAllViews()
            self._add_verdict(report)
            if report.get("threats"):
                self._add_card(_tr("a_threats"), report["threats"])
            if report["has_dex"]:
                self._add_section(_tr("a_dex"))
                self._add_card(_tr("a_structure"), report["stats"])
                self._add_card(_tr("a_capabilities"), report["capabilities"])
                self._add_card(_tr("a_dangerous"), report["dangerous"])
                self._add_card(_tr("a_packages"), report["packages"])
            else:
                self._add_card(_tr("a_no_dex"), [_tr("a_no_dex_hint")], True)
            self._add_files(report["files"])
            self._add_card(_tr("a_external"), report["external"])
            self._add_card(_tr("a_network"), report["network"], True)
            self._add_card(_tr("a_native"), report["native"])
            if self.content.getChildCount() == 0:
                self._add_card(_tr("a_empty"), [_tr("a_empty")])
            self.content.setAlpha(0.0)
            self.content.setTranslationY(float(AndroidUtilities.dp(12)))
            self.content.animate().alpha(1.0).translationY(0.0).setDuration(260).start()
        except Exception as error:
            log_error(error, context="analysis render")
            self._render_error()

    def _add_verdict(self, report):
        try:
            level = str(report.get("threat_level") or "clean")
            score = int(report.get("threat_score") or 0)
        except Exception:
            level, score = "clean", 0
        try:
            if level == "high":
                color = Theme.getColor(Theme.key_text_RedRegular)
            elif level == "medium":
                color = 0xFFF59E0B
            elif level == "low":
                color = Theme.getColor(Theme.key_windowBackgroundWhiteValueText)
            else:
                level = "clean"
                color = Theme.getColor(Theme.key_windowBackgroundWhiteGreenText)
        except Exception:
            color = 0xFF888888
        try:
            if int(color) >= 0x80000000:
                color = int(color) - 0x100000000
        except Exception:
            pass
        self._add_card(_tr("threat_" + level), [f"score: {score}", _tr("a_heuristic_note")], True, title_color=color)

    def _add_plain(self, value, size, bold=False, gravity=None):
        TextView = find_class("android.widget.TextView")
        view = TextView(self.context)
        view.setText(value)
        view.setTextSize(1, float(size))
        view.setTextColor(Theme.getColor(Theme.key_dialogTextBlack if bold else Theme.key_dialogTextGray2))
        if bold:
            view.setTypeface(AndroidUtilities.bold())
        if gravity is not None:
            view.setGravity(gravity)
        self.content.addView(view, LayoutHelper.createLinear(-1, -2, 8, 3, 8, 3))

    def _add_section(self, title):
        TextView = find_class("android.widget.TextView")
        view = TextView(self.context)
        view.setText(title)
        view.setTextSize(1, 14.0)
        view.setTypeface(AndroidUtilities.bold())
        view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader))
        self.content.addView(view, LayoutHelper.createLinear(-1, -2, 8, 10, 8, 5))

    def _add_card(self, title, items, show_empty=False, title_color=None):
        if not items and not show_empty:
            return
        LinearLayout = find_class("android.widget.LinearLayout")
        TextView = find_class("android.widget.TextView")
        card = LinearLayout(self.context)
        card.setOrientation(LinearLayout.VERTICAL)
        pad = AndroidUtilities.dp(12)
        card.setPadding(pad, AndroidUtilities.dp(10), pad, AndroidUtilities.dp(10))
        card.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(13), Theme.getColor(Theme.key_windowBackgroundGray)))
        heading = TextView(self.context)
        heading.setText(title)
        heading.setTextSize(1, 13.5)
        heading.setTypeface(AndroidUtilities.bold())
        try:
            heading.setTextColor(int(title_color) if title_color is not None else Theme.getColor(Theme.key_dialogTextBlack))
        except Exception:
            pass
        card.addView(heading, LayoutHelper.createLinear(-1, -2))
        body = TextView(self.context)
        values = items or [_tr("a_empty")]
        body.setText("\n".join(f"• {value}" for value in values))
        body.setTextSize(1, 12.5)
        body.setTextColor(Theme.getColor(Theme.key_dialogTextGray2))
        body.setTextIsSelectable(True)
        card.addView(body, LayoutHelper.createLinear(-1, -2, 0, 5, 0, 0))
        self.content.addView(card, LayoutHelper.createLinear(-1, -2, 2, 4, 2, 4))

    def _add_files(self, files):
        if not files:
            return
        self._add_section(_tr("a_files"))
        LinearLayout = find_class("android.widget.LinearLayout")
        TextView = find_class("android.widget.TextView")
        for item in files:
            row = LinearLayout(self.context)
            row.setOrientation(LinearLayout.VERTICAL)
            pad = AndroidUtilities.dp(12)
            row.setPadding(pad, AndroidUtilities.dp(9), pad, AndroidUtilities.dp(9))
            row.setBackground(Theme.getSelectorDrawable(False))
            name = TextView(self.context)
            name.setText(item["name"])
            name.setTextSize(1, 14.0)
            name.setTextColor(Theme.getColor(Theme.key_dialogTextBlack))
            row.addView(name, LayoutHelper.createLinear(-1, -2))
            detail = TextView(self.context)
            detail.setText(f"{item['kind']} · {item['size']:,} {_tr('a_bytes')}")
            detail.setTextSize(1, 12.0)
            detail.setTextColor(Theme.getColor(Theme.key_dialogTextGray2))
            row.addView(detail, LayoutHelper.createLinear(-1, -2, 0, 2, 0, 0))
            row.setOnClickListener(OnClickListener(lambda _, value=item: TextPreviewSheet(self.context, value["name"], value["preview"], self.resources_provider).show()))
            self.content.addView(row, LayoutHelper.createLinear(-1, -2, 2, 2, 2, 2))


class TextPreviewSheet:
    def __init__(self, context, title, content, resources_provider=None):
        self.context = context
        self.title = title
        self.content = content
        self.resources_provider = resources_provider

    def show(self):
        BottomSheet = find_class("org.telegram.ui.ActionBar.BottomSheet")
        LinearLayout = find_class("android.widget.LinearLayout")
        TextView = find_class("android.widget.TextView")
        NestedScrollView = find_class("androidx.core.widget.NestedScrollView")
        root = LinearLayout(self.context)
        root.setOrientation(LinearLayout.VERTICAL)
        root.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(18))
        title = TextView(self.context)
        title.setText(self.title)
        title.setTextSize(1, 18.0)
        title.setTypeface(AndroidUtilities.bold())
        title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack))
        root.addView(title, LayoutHelper.createLinear(-1, -2, 0, 0, 0, 10))
        body = TextView(self.context)
        body.setText(self.content or _tr("a_empty"))
        body.setTextSize(1, 12.0)
        body.setTextColor(Theme.getColor(Theme.key_dialogTextGray2))
        body.setTextIsSelectable(True)
        scroll = NestedScrollView(self.context)
        scroll.addView(body)
        root.addView(scroll, LayoutHelper.createLinear(-1, -2))
        sheet = BottomSheet(self.context, False, self.resources_provider)
        sheet.setCustomView(root)
        sheet.show()
