import os
import traceback

from android_utils import run_on_ui_thread
from client_utils import get_last_fragment, run_on_queue
from com.exteragram.messenger.plugins import PluginsController
from org.telegram.messenger import AndroidUtilities
from org.telegram.messenger import R as R_tg
from org.telegram.ui.ActionBar import Theme, SimpleTextView
from org.telegram.ui.Components import BackupImageView, LayoutHelper

from ...methods import _tr, _parse_markdown, log, log_error, fully_format_text, register_active_sheet

_delayed_runnable_refs = {}


def _hold_runnable(runnable):
    _delayed_runnable_refs[id(runnable)] = runnable
    return runnable


def _release_runnable(runnable):
    try:
        _delayed_runnable_refs.pop(id(runnable), None)
    except Exception:
        pass


def show_requirements_dialog(plugin_id, min_version, client_version):
    try:
        fragment = get_last_fragment()
        if not fragment:
            return
        ctx = fragment.getParentActivity()
        if not ctx:
            return
        from org.telegram.ui.ActionBar import BottomSheet
        from android.widget import LinearLayout, TextView
        from android.view import Gravity
        from androidx.core.widget import NestedScrollView

        sheet = BottomSheet(ctx, False, fragment.getResourceProvider())
        sheet.setApplyBottomPadding(False)
        sheet.setApplyTopPadding(False)
        sheet.fixNavigationBar(Theme.getColor(Theme.key_windowBackgroundWhite))

        ll = LinearLayout(ctx)
        ll.setOrientation(LinearLayout.VERTICAL)
        ll.setClickable(True)

        fl = __import__("android.widget", fromlist=["FrameLayout"]).FrameLayout(ctx)
        fl.addView(ll)

        sv = NestedScrollView(ctx)
        sv.addView(fl)
        sheet.setCustomView(sv)

        info = _tr("version_requirements").format(min_version=min_version, client_version=client_version)

        title = SimpleTextView(ctx)
        title.setTypeface(AndroidUtilities.bold())
        title.setTextSize(20)
        title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack))
        title.setText(_tr("requirements_not_met"))
        title.setGravity(Gravity.CENTER)
        ll.addView(title, LayoutHelper.createLinear(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 10, 27, 10, 0))

        tv = TextView(ctx)
        tv.setGravity(Gravity.CENTER)
        tv.setText(fully_format_text(info, on_dismiss=sheet.dismiss))
        tv.setTextColor(Theme.getColor(Theme.key_dialogTextBlack))
        tv.setTextSize(15)
        try:
            tv.setMovementMethod(AndroidUtilities.LinkMovementMethodMy())
            tv.setHighlightColor(0)
        except Exception:
            pass
        register_active_sheet(sheet)
        ll.addView(tv, LayoutHelper.createLinear(-1, -2, Gravity.TOP, 24, 20, 24, 20))

        sheet.show()
    except Exception as e:
        log_error(e, context="requirements dialog")


def show_self_install_dialog(pl=None):
    try:
        fragment = get_last_fragment()
        if not fragment:
            return
        ctx = fragment.getParentActivity()
        if not ctx:
            return
        from org.telegram.ui.ActionBar import BottomSheet
        from android.widget import LinearLayout, TextView
        from android.view import Gravity
        from androidx.core.widget import NestedScrollView

        sheet = BottomSheet(ctx, False, fragment.getResourceProvider())
        sheet.setApplyBottomPadding(False)
        sheet.setApplyTopPadding(False)
        sheet.fixNavigationBar(Theme.getColor(Theme.key_windowBackgroundWhite))

        ll = LinearLayout(ctx)
        ll.setOrientation(LinearLayout.VERTICAL)
        ll.setClickable(True)

        if pl is not None:
            try:
                sticker_image_view = BackupImageView(ctx)
                sticker_image_view.setRoundRadius(0)
                sticker_image_view.setAspectFit(True)
                sticker_image_view.getImageReceiver().setCrossfadeWithOldImage(True)
                ll.addView(sticker_image_view, LayoutHelper.createLinear(150, 150, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 27, 0, 0))
                pl._load_sticker_async(sticker_image_view, "Amechan0_o_by_fStikBot", 2, "self_install_sticker")
            except Exception:
                pass

        fl = __import__("android.widget", fromlist=["FrameLayout"]).FrameLayout(ctx)
        fl.addView(ll)

        sv = NestedScrollView(ctx)
        sv.addView(fl)
        sheet.setCustomView(sv)

        info = _tr("self_install_text")

        title = SimpleTextView(ctx)
        title.setTypeface(AndroidUtilities.bold())
        title.setTextSize(20)
        title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack))
        title.setText(_tr("self_install_title"))
        title.setGravity(Gravity.CENTER)
        ll.addView(title, LayoutHelper.createLinear(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 10, 27, 10, 0))

        tv = TextView(ctx)
        tv.setGravity(Gravity.CENTER)
        tv.setText(fully_format_text(info, on_dismiss=sheet.dismiss))
        tv.setTextColor(Theme.getColor(Theme.key_dialogTextBlack))
        tv.setTextSize(15)
        try:
            tv.setMovementMethod(AndroidUtilities.LinkMovementMethodMy())
            tv.setHighlightColor(0)
        except Exception:
            pass
        register_active_sheet(sheet)
        ll.addView(tv, LayoutHelper.createLinear(-1, -2, Gravity.TOP, 24, 20, 24, 20))

        sheet.show()
    except Exception as e:
        log_error(e, context="self install dialog")


def show_untrusted_warning(pl, plugin_id):
    try:
        fragment = get_last_fragment()
        if not fragment:
            return
        ctx = fragment.getParentActivity()
        if not ctx:
            return
        from org.telegram.ui.ActionBar import BottomSheet
        from android.widget import LinearLayout, TextView
        from android.view import Gravity
        from androidx.core.widget import NestedScrollView
        from org.telegram.ui.Components import BackupImageView, LayoutHelper

        builder = BottomSheet.Builder(ctx)
        ll = LinearLayout(ctx)
        ll.setOrientation(LinearLayout.VERTICAL)

        sticker_image_view = BackupImageView(ctx)
        sticker_image_view.setRoundRadius(0)
        sticker_image_view.getImageReceiver().setCrossfadeWithOldImage(True)
        ll.addView(sticker_image_view, LayoutHelper.createLinear(150, 150, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 27, 0, 0))
        pl._load_sticker_async(sticker_image_view, "LostChocolateFlamingo_by_fStikBot", 11, "untrusted_warning_sticker")

        title = SimpleTextView(ctx)
        title.setTypeface(AndroidUtilities.bold())
        title.setTextSize(20)
        title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack))
        title.setText(_tr("untrusted_warning_title"))
        title.setGravity(Gravity.CENTER)
        ll.addView(title, LayoutHelper.createLinear(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 10, 27, 10, 0))

        text = _tr("untrusted_warning_text")
        tv = TextView(ctx)
        tv.setGravity(Gravity.CENTER)
        tv.setText(fully_format_text(text, on_dismiss=lambda: sheet.dismiss()))
        tv.setTextColor(Theme.getColor(Theme.key_dialogTextBlack))
        tv.setTextSize(15)
        try:
            tv.setMovementMethod(AndroidUtilities.LinkMovementMethodMy())
            tv.setHighlightColor(0)
        except Exception:
            pass
        ll.addView(tv, LayoutHelper.createLinear(-1, -2, Gravity.TOP, 24, 20, 24, 8))

        btn = TextView(ctx)
        btn.setText(_tr("disable_blocking"))
        from java import jint
        btn.setTextColor(jint(-1))
        btn.setTextSize(14)
        btn.setTypeface(AndroidUtilities.bold())
        btn.setGravity(Gravity.CENTER)
        btn.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12))
        from android.graphics.drawable import GradientDrawable
        bg = GradientDrawable()
        bg.setShape(GradientDrawable.RECTANGLE)
        try:
            accent = Theme.getColor(Theme.key_featuredStickers_addButton)
        except Exception:
            accent = jint(-8630785)
        bg.setColor(accent)
        bg.setCornerRadius(AndroidUtilities.dp(8))
        btn.setBackground(bg)
        sheet = builder.create()
        register_active_sheet(sheet)

        def _on_disable_click(v):
            try:
                sheet.dismiss()
            except Exception:
                pass
            try:
                pl.open_settings_page("security:block_untrusted")
            except Exception as e:
                log_error(e, context="opening untrusted settings")
                try:
                    from ...methods import open_link
                    open_link("https://t.me/exteraSettings?p=kangel_plugins_manager&s=security:block_untrusted")
                except Exception:
                    pass

        from android_utils import OnClickListener
        btn.setOnClickListener(OnClickListener(_on_disable_click))
        ll.addView(btn, LayoutHelper.createLinear(-1, -2, Gravity.CENTER_HORIZONTAL, 24, 0, 24, 20))

        sv = NestedScrollView(ctx)
        sv.addView(ll)
        builder.setCustomView(sv)
        sheet.show()
    except Exception as e:
        log_error(e, context="untrusted warning")


def show_about_dialog(pl):
    try:
        fragment = get_last_fragment()
        if not fragment:
            return
        ctx = fragment.getParentActivity()
        if not ctx:
            return
        from org.telegram.ui.ActionBar import BottomSheet, Theme
        from android.widget import LinearLayout, TextView, FrameLayout, ImageView
        from android.view import Gravity
        from androidx.core.widget import NestedScrollView
        from org.telegram.ui.Components import BackupImageView, LayoutHelper

        sheet = BottomSheet(ctx, False, fragment.getResourceProvider())
        sheet.setApplyBottomPadding(False)
        sheet.setApplyTopPadding(False)
        sheet.fixNavigationBar(Theme.getColor(Theme.key_windowBackgroundWhite))

        ll = LinearLayout(ctx)
        ll.setOrientation(LinearLayout.VERTICAL)
        ll.setClickable(True)

        fl = FrameLayout(ctx)
        fl.addView(ll)

        sv = NestedScrollView(ctx)
        sv.addView(fl)
        sheet.setCustomView(sv)

        close_view = ImageView(ctx)
        close_view.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector)))
        close_view.setColorFilter(Theme.getColor(Theme.key_sheet_other))
        from org.telegram.messenger import R as R_tg
        close_view.setImageResource(R_tg.drawable.ic_layer_close)
        from hook_utils import find_class
        from java import dynamic_proxy
        OnClickInterface = find_class("android.view.View$OnClickListener")
        OnClick = dynamic_proxy(OnClickInterface)
        class CloseClickImpl(OnClick):
            def __init__(self, bs):
                super().__init__()
                self.bs = bs
            def onClick(self, v):
                self.bs.dismiss()
        close_view.setOnClickListener(CloseClickImpl(sheet))
        close_pad = AndroidUtilities.dp(8)
        close_view.setPadding(close_pad, close_pad, close_pad, close_pad)
        fl.addView(close_view, LayoutHelper.createFrame(36, 36, Gravity.TOP | Gravity.END, 6, 8, 8, 0))

        sticker_image_view = BackupImageView(ctx)
        sticker_image_view.setRoundRadius(0)
        sticker_image_view.getImageReceiver().setCrossfadeWithOldImage(True)
        ll.addView(sticker_image_view, LayoutHelper.createLinear(150, 150, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 27, 0, 0))
        pl._load_sticker_async(sticker_image_view, "temp_5d5b6o8k_1863611627_by_QuotLyBot", 1, "about_dialog_sticker")

        title_tv = SimpleTextView(ctx)
        title_tv.setTypeface(AndroidUtilities.bold())
        title_tv.setTextSize(20)
        title_tv.setTextColor(Theme.getColor(Theme.key_dialogTextBlack))
        title_tv.setText("Kangel Plugins Manager")
        title_tv.setGravity(Gravity.CENTER)
        ll.addView(title_tv, LayoutHelper.createLinear(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 10, 27, 10, 0))

        from ...methods import __version__, __codename__
        ver_tv = TextView(ctx)
        ver_tv.setGravity(Gravity.CENTER)
        ver_tv.setText(f"{__version__} | '{__codename__}'")
        ver_tv.setTextColor(Theme.getColor(Theme.key_dialogTextGray3))
        ver_tv.setTextSize(14)
        ll.addView(ver_tv, LayoutHelper.createLinear(-1, -2, Gravity.TOP, 24, 4, 24, 0))

        info_tv = TextView(ctx)
        info_tv.setGravity(Gravity.CENTER)
        info_tv.setText(_tr("easter_egg_info"))
        info_tv.setTextColor(Theme.getColor(Theme.key_dialogTextBlack))
        info_tv.setTextSize(15)
        ll.addView(info_tv, LayoutHelper.createLinear(-1, -2, Gravity.TOP, 24, 20, 24, 20))

        sheet.show()
        log("[KPM] About dialog shown (easter egg)")
    except Exception as e:
        log_error(e, context="about dialog")


def show_version_picker(pl, plugin_id, versions):
    def show():
        try:
            from android.widget import LinearLayout, ScrollView, TextView
            from android.view import Gravity
            from android.util import TypedValue
            from android.graphics.drawable import GradientDrawable
            from android.graphics import PorterDuff
            from android_utils import OnClickListener
            from ui.alert import AlertDialogBuilder

            fragment = get_last_fragment()
            if not fragment:
                return
            ctx = fragment.getParentActivity()
            if not ctx:
                return

            info = pl.store.get_plugin_info(plugin_id)
            name = info.get("name", plugin_id) if isinstance(info, dict) else plugin_id

            def _to_jint(c):
                return c - 0x100000000 if c > 0x7FFFFFFF else c

            try:
                bg_color = Theme.getColor(Theme.key_dialogBackground)
            except Exception:
                bg_color = 0xFFFFFFFF
            try:
                text_color = Theme.getColor(Theme.key_dialogTextBlack)
            except Exception:
                text_color = 0xFF000000
            try:
                subtitle_color = Theme.getColor(Theme.key_dialogTextGray)
            except Exception:
                subtitle_color = 0xFF888888
            try:
                accent = Theme.getColor(Theme.key_featuredStickers_addButton)
            except Exception:
                try:
                    accent = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText)
                except Exception:
                    accent = 0xFF3399FF
            try:
                card_bg = Theme.getColor(Theme.key_windowBackgroundWhite)
            except Exception:
                card_bg = 0xFFF5F5F5
            try:
                ripple_color = Theme.getColor(Theme.key_listSelector)
            except Exception:
                ripple_color = 0x22000000

            root = LinearLayout(ctx)
            root.setOrientation(LinearLayout.VERTICAL)
            root.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(8), AndroidUtilities.dp(20), AndroidUtilities.dp(4))

            header = TextView(ctx)
            header.setText(_tr("version_picker_title").format(name))
            header.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17)
            header.setTypeface(AndroidUtilities.bold())
            header.setTextColor(text_color)
            header.setGravity(Gravity.LEFT)
            header_lp = LinearLayout.LayoutParams(-1, -2)
            header_lp.bottomMargin = AndroidUtilities.dp(8)
            root.addView(header, header_lp)

            installed_label = _tr("current_version")
            is_legacy_mode = False
            for v in versions:
                lbl = str(v.get("label", ""))
                if installed_label.lower() not in lbl.lower() and lbl != installed_label:
                    is_legacy_mode = True
                    break

            def _make_version_card(idx, ver):
                ver_label = str(ver.get("label", ""))
                is_current = installed_label.lower() in ver_label.lower() or ver_label == installed_label

                card = LinearLayout(ctx)
                card.setOrientation(LinearLayout.HORIZONTAL)
                card.setGravity(Gravity.CENTER_VERTICAL)
                card.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12), AndroidUtilities.dp(14), AndroidUtilities.dp(12))

                card_bg_drawable = GradientDrawable()
                card_bg_drawable.setShape(GradientDrawable.RECTANGLE)
                card_bg_drawable.setCornerRadius(float(AndroidUtilities.dp(12)))
                card_bg_drawable.setStroke(AndroidUtilities.dp(1.5), _to_jint(accent))
                if is_current:
                    card_bg_drawable.setColor(_to_jint(accent) & 0x12FFFFFF | 0x18000000)
                else:
                    card_bg_drawable.setColor(_to_jint(card_bg))
                card.setBackground(card_bg_drawable)

                left_col = LinearLayout(ctx)
                left_col.setOrientation(LinearLayout.VERTICAL)

                ver_tv = TextView(ctx)
                ver_tv.setText(ver_label)
                ver_tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15)
                ver_tv.setTypeface(AndroidUtilities.bold())
                ver_tv.setTextColor(text_color if not is_current else accent)
                left_col.addView(ver_tv, LinearLayout.LayoutParams(-1, -2))

                card.addView(left_col, LinearLayout.LayoutParams(0, -2, 1.0))

                return card

            scroll = ScrollView(ctx)
            scroll.setOverScrollMode(ScrollView.OVER_SCROLL_ALWAYS)

            list_container = LinearLayout(ctx)
            list_container.setOrientation(LinearLayout.VERTICAL)
            list_container.setPadding(0, 0, 0, AndroidUtilities.dp(4))

            dlg_ref = [None]

            for i, ver in enumerate(versions):
                card = _make_version_card(i, ver)
                lp = LinearLayout.LayoutParams(-1, -2)
                lp.bottomMargin = AndroidUtilities.dp(6)
                list_container.addView(card, lp)

                def make_click(idx=i, v=ver):
                    def on_click(view):
                        if dlg_ref[0]:
                            dlg_ref[0].dismiss()
                        sel = versions[idx]
                        sel_url = sel.get("url", "")
                        sel_hash = sel.get("hash", "")
                        if sel_url:
                            run_on_queue(lambda: _install_version(pl, plugin_id, sel_url, sel_hash))
                    return on_click

                card.setOnClickListener(OnClickListener(make_click(i, ver)))

                def make_long_click(idx=i, v=ver):
                    def on_long_click(view):
                        ver_label = str(v.get("label", ""))
                        link = f"tg://kpm_install?plugin={plugin_id}&version={ver_label}"
                        from org.telegram.messenger import AndroidUtilities as AU
                        AU.addToClipboard(link)
                        from ui.bulletin import BulletinHelper
                        BulletinHelper.show_copied_to_clipboard()
                        return True
                    return on_long_click

                from java import dynamic_proxy
                from hook_utils import find_class as _fc
                OnLongClickListener = _fc("android.view.View$OnLongClickListener")
                _lc_proxy = make_long_click(i, ver)
                class _LC(dynamic_proxy(OnLongClickListener)):
                    def onLongClick(self, v):
                        return _lc_proxy(v)
                card.setOnLongClickListener(_LC())

            scroll.addView(list_container)

            from org.telegram.ui.Components import LayoutHelper
            root.addView(scroll, LayoutHelper.createLinear(-1, 0, 1.0))

            builder = AlertDialogBuilder(ctx)
            builder.set_view(root)
            builder.set_negative_button(_tr("cancel"), lambda b, w: b.dismiss())
            builder.show()
            dlg_ref[0] = builder.get_dialog()

        except Exception as e:
            log_error(e, context="version picker")
    run_on_ui_thread(show)


def _install_version(pl, plugin_id, url, expected_hash):
    progress_ref = [None]
    def show_progress():
        try:
            from org.telegram.ui.ActionBar import AlertDialog
            fragment = get_last_fragment()
            if fragment:
                ctx = fragment.getParentActivity()
                if ctx:
                    pd = AlertDialog(ctx, 3)
                    pd.setCanCancel(True)
                    pd.show()
                    progress_ref[0] = pd
        except Exception:
            pass
    run_on_ui_thread(show_progress)

    def do_install():
        try:
            from ...store import Store
            content = Store.fetch_bytes(url)
            if expected_hash:
                h = Store.sha256(content)
                if h.lower() != expected_hash.lower():
                    if progress_ref[0]:
                        run_on_ui_thread(lambda: progress_ref[0].dismiss() if progress_ref[0].isShowing() else None)
                    run_on_ui_thread(lambda: show_untrusted_warning(pl, plugin_id))
                    return
            temp_ext = ".eaf" if url and url.endswith(".eaf") else ".plugin"
            temp = os.path.join(pl.plugins_dir, f".temp_{plugin_id}{temp_ext}")
            with open(temp, "wb") as f:
                f.write(content)
            fragment = get_last_fragment()
            if progress_ref[0]:
                run_on_ui_thread(lambda: progress_ref[0].dismiss() if progress_ref[0].isShowing() else None)
            run_on_ui_thread(lambda: PluginsController.getInstance().showInstallDialog(fragment, temp, True))
        except Exception as e:
            if progress_ref[0]:
                run_on_ui_thread(lambda: progress_ref[0].dismiss() if progress_ref[0].isShowing() else None)
            log_error(e, context="install version")
    run_on_queue(do_install)


_md3_active_ctrls = set()

def dismiss_all_md3():
    for ctrl in list(_md3_active_ctrls):
        try:
            ctrl.dismiss()
        except Exception:
            pass
    _md3_active_ctrls.clear()

def show_md3_loading(duration_ms=3000):
    log(f"[KPM] md3: show_md3_loading called, duration={duration_ms}")
    try:
        from org.telegram.ui.ActionBar import AlertDialog
        state = {"dialog": None, "dismissed": False}

        def create_dialog():
            try:
                fragment = get_last_fragment()
                if not fragment:
                    return
                activity = fragment.getParentActivity()
                if not activity:
                    return
                pd = AlertDialog(activity, 3)
                pd.setCanCancel(False)
                pd.show()
                state["dialog"] = pd

                from hook_utils import find_class
                Runnable = find_class("java.lang.Runnable")
                from java import dynamic_proxy

                class DismissRun(dynamic_proxy(Runnable)):
                    def __init__(self, s):
                        super().__init__()
                        self.s = s
                    def run(self):
                        try:
                            if not self.s["dismissed"] and self.s["dialog"] and self.s["dialog"].isShowing():
                                self.s["dialog"].dismiss()
                        except Exception:
                            pass
                        finally:
                            _release_runnable(self)

                AndroidUtilities.runOnUIThread(_hold_runnable(DismissRun(state)), duration_ms)
            except Exception as e:
                log_error(e, context="md3 create")

        run_on_ui_thread(create_dialog)

        class Ctrl:
            def dismiss(self):
                state["dismissed"] = True
                _md3_active_ctrls.discard(self)
                if state["dialog"] and state["dialog"].isShowing():
                    run_on_ui_thread(lambda: state["dialog"].dismiss() if state["dialog"] and state["dialog"].isShowing() else None)
            def isShowing(self):
                return not state["dismissed"] and state["dialog"] and state["dialog"].isShowing()

        ctrl = Ctrl()
        _md3_active_ctrls.add(ctrl)
        return ctrl
    except Exception as e:
        log_error(e, context="md3 loading")
        return None


def show_import_progress(total):

    state = {"dialog": None, "dismissed": False, "progress_bar": None, "status_text": None}

    def create_dialog():
        try:
            from hook_utils import find_class
            fragment = get_last_fragment()
            if not fragment:
                return
            activity = fragment.getParentActivity()
            if not activity:
                return

            Dialog = find_class("android.app.Dialog")
            LinearLayout = find_class("android.widget.LinearLayout")
            ProgressBar = find_class("android.widget.ProgressBar")
            TextView = find_class("android.widget.TextView")
            Gravity = find_class("android.view.Gravity")
            ViewGroup = find_class("android.view.ViewGroup")
            ColorDrawable = find_class("android.graphics.drawable.ColorDrawable")
            GradientDrawable = find_class("android.graphics.drawable.GradientDrawable")

            dialog = Dialog(activity)
            dialog.requestWindowFeature(1)

            ll = LinearLayout(activity)
            ll.setOrientation(LinearLayout.VERTICAL)
            ll.setGravity(Gravity.CENTER_HORIZONTAL)
            ll.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(20),
                          AndroidUtilities.dp(24), AndroidUtilities.dp(20))

            try:
                bg = GradientDrawable()
                bg.setCornerRadius(AndroidUtilities.dp(16))
                bg.setColor(Theme.getColor(Theme.key_windowBackgroundWhite))
                ll.setBackground(bg)
            except Exception:
                pass

            title = TextView(activity)
            title.setText(_tr("importing_collection"))
            title.setTypeface(AndroidUtilities.bold())
            title.setTextSize(17)
            title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack))
            title.setGravity(Gravity.CENTER)
            ll.addView(title, LayoutHelper.createLinear(-1, -2, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 12))

            pb = ProgressBar(activity)
            pb.setIndeterminate(False)
            pb.setMax(total)
            pb.setProgress(0)
            state["progress_bar"] = pb
            ll.addView(pb, LayoutHelper.createLinear(-1, AndroidUtilities.dp(4), 0, 0, 0, 8))

            status = TextView(activity)
            status.setText(f"0 / {total}")
            status.setTextSize(14)
            status.setTextColor(Theme.getColor(Theme.key_dialogTextGray2))
            status.setGravity(Gravity.CENTER)
            state["status_text"] = status
            ll.addView(status, LayoutHelper.createLinear(-1, -2, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 0))

            dialog.setContentView(ll, ViewGroup.LayoutParams(
                AndroidUtilities.dp(280), ViewGroup.LayoutParams.WRAP_CONTENT))

            window = dialog.getWindow()
            if window:
                window.setBackgroundDrawable(ColorDrawable(0))
                window.setDimAmount(0.4)

            dialog.show()
            if window:
                window.setLayout(AndroidUtilities.dp(280), ViewGroup.LayoutParams.WRAP_CONTENT)

            state["dialog"] = dialog
        except Exception as e:
            log_error(e, context="import progress create")

    run_on_ui_thread(create_dialog)

    class Ctrl:
        def update(self, current, name=""):
            state["current"] = current
            state["name"] = name
            try:
                if state["progress_bar"]:
                    state["progress_bar"].setProgress(current)
                if state["status_text"]:
                    text = f"{current} / {total}"
                    if name:
                        text = f"{name}\n{text}"
                    state["status_text"].setText(text)
            except Exception:
                pass

        def dismiss(self):
            state["dismissed"] = True
            if state["dialog"] and state["dialog"].isShowing():
                run_on_ui_thread(lambda: state["dialog"].dismiss() if state["dialog"] and state["dialog"].isShowing() else None)

        def isShowing(self):
            return not state["dismissed"] and state["dialog"] and state["dialog"].isShowing()

    return Ctrl()
