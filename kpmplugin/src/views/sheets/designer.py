import traceback

from android_utils import OnClickListener, run_on_ui_thread
from client_utils import get_last_fragment
from org.telegram.messenger import AndroidUtilities
from org.telegram.ui.ActionBar import Theme
from org.telegram.ui.Components import BackupImageView, LayoutHelper

from ...methods import _tr, _parse_markdown, log, log_error, format_with_usernames, fully_format_text


def show_import_collection_designer(pl, metadata, plugin_ids):
    log(f"[KPM] show_import_collection_designer: ids={plugin_ids[:5]}... total={len(plugin_ids)}")
    try:
        fragment = get_last_fragment()
        if not fragment:
            log("[KPM] designer: no fragment")
            return
        ctx = fragment.getParentActivity()
        if not ctx:
            log("[KPM] designer: no activity")
            return

        from org.telegram.ui.ActionBar import BottomSheet
        from android.widget import LinearLayout, TextView, FrameLayout
        from android.view import Gravity, MotionEvent
        from androidx.core.widget import NestedScrollView
        from android.graphics.drawable import GradientDrawable
        from android.text import Spannable
        from android.text.style import ClickableSpan
        from java import jint
        from hook_utils import find_class
        from java import dynamic_proxy

        sheet = BottomSheet(ctx, False, fragment.getResourceProvider())
        sheet.setApplyBottomPadding(False)
        sheet.setApplyTopPadding(False)
        sheet.fixNavigationBar(Theme.getColor(Theme.key_windowBackgroundWhite))
        try:
            from ...methods import register_active_sheet, unregister_active_sheet
            register_active_sheet(sheet)
            DialogInterface = find_class("android.content.DialogInterface")
            class _DesignerDismiss(dynamic_proxy(DialogInterface.OnDismissListener)):
                def onDismiss(self, d):
                    try:
                        unregister_active_sheet(sheet)
                    except Exception:
                        pass
            sheet.setOnDismissListener(_DesignerDismiss())
        except Exception:
            pass



        ll = LinearLayout(ctx)
        ll.setOrientation(LinearLayout.VERTICAL)
        ll.setClickable(True)

        name = str(metadata.get("name") or "Unnamed Collection")
        author = str(metadata.get("author") or "").strip()
        desc = str(metadata.get("description") or "").strip()
        icon_str = str(metadata.get("icon") or "").strip()

        state = {"ids": [str(pid) for pid in plugin_ids]}

        def _plugin_name(pid):
            try:
                info = pl.store.get_plugin_info(pid) or {}
                return info.get("name", pid) if isinstance(info, dict) else pid
            except Exception:
                return pid

        def _preview_text(ids):
            names = [_plugin_name(pid) for pid in ids[:4]]
            if not names:
                return _tr("collection_plugins_empty")
            text = ", ".join(names)
            if len(ids) > 4:
                text += _tr("collection_plugins_more").format(len(ids) - 4)
            return text

        header_row = LinearLayout(ctx)
        header_row.setOrientation(LinearLayout.HORIZONTAL)
        header_row.setGravity(Gravity.CENTER_VERTICAL)
        ll.addView(header_row, LayoutHelper.createLinear(-1, -2, Gravity.TOP, 24, 24, 24, 0))

        icon_size = AndroidUtilities.dp(44)
        icon_holder = FrameLayout(ctx)
        header_row.addView(icon_holder, LayoutHelper.createLinear(icon_size, icon_size, Gravity.CENTER_VERTICAL, 0, 0, 0, 0))

        if icon_str and "/" in icon_str:
            sticker_view = BackupImageView(ctx)
            sticker_view.setRoundRadius(AndroidUtilities.dp(12))
            parts = icon_str.split("/")
            try:
                sticker_index = int(parts[1])
            except Exception:
                sticker_index = 0
            icon_holder.addView(sticker_view, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            import hashlib
            _icon_cache_key = "kpm_import_" + hashlib.md5(icon_str.encode()).hexdigest()[:12]
            pl._load_sticker_async(sticker_view, parts[0], sticker_index, _icon_cache_key)
        else:
            letter = TextView(ctx)
            letter.setText((name[:1] or "K").upper())
            letter.setTextColor(-1)
            letter.setTextSize(18)
            letter.setTypeface(AndroidUtilities.bold())
            letter.setGravity(Gravity.CENTER)
            icon_bg = GradientDrawable()
            icon_bg.setCornerRadius(AndroidUtilities.dp(12))
            try:
                icon_bg.setColor(Theme.getColor(Theme.key_featuredStickers_addButton))
            except Exception:
                icon_bg.setColor(jint(-8630785))
            icon_holder.setBackground(icon_bg)
            icon_holder.addView(letter, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        text_col = LinearLayout(ctx)
        text_col.setOrientation(LinearLayout.VERTICAL)
        text_col.setGravity(Gravity.CENTER_VERTICAL)
        header_row.addView(text_col, LayoutHelper.createLinear(0, -2, 1.0, Gravity.CENTER_VERTICAL, 14, 0, 0, 0))

        title = TextView(ctx)
        title.setText(name)
        title.setTypeface(AndroidUtilities.bold())
        title.setTextSize(18)
        title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack))
        text_col.addView(title, LayoutHelper.createLinear(-1, -2, Gravity.CENTER_VERTICAL))

        if author:
            import re as _re
            clean_author = str(author).strip()
            if _re.match(r'^[a-zA-Z0-9_]{3,32}$', clean_author):
                clean_author = f"@{clean_author}"
            auth_tv = TextView(ctx)
            auth_text = format_with_usernames(f"by {clean_author}", on_dismiss=sheet.dismiss)
            auth_tv.setText(auth_text)
            try:
                auth_tv.setMovementMethod(AndroidUtilities.LinkMovementMethodMy())
                auth_tv.setHighlightColor(0)
            except Exception:
                pass
            auth_tv.setTextSize(14)
            auth_tv.setTextColor(Theme.getColor(Theme.key_dialogTextGray))
            text_col.addView(auth_tv, LayoutHelper.createLinear(-1, -2, Gravity.CENTER_VERTICAL, 0, 2, 0, 0))

        if desc:
            desc_tv = TextView(ctx)
            desc_text = fully_format_text(desc, on_dismiss=sheet.dismiss)
            desc_tv.setText(desc_text)
            try:
                desc_tv.setMovementMethod(AndroidUtilities.LinkMovementMethodMy())
                desc_tv.setHighlightColor(0)
            except Exception:
                pass
            desc_tv.setTextSize(15)
            desc_tv.setTextColor(Theme.getColor(Theme.key_dialogTextGray2))
            desc_tv.setGravity(Gravity.CENTER)
            desc_tv.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(14), AndroidUtilities.dp(24), 0)
            ll.addView(desc_tv, LayoutHelper.createLinear(-1, -2, Gravity.TOP))

        count_tv = TextView(ctx)
        count_tv.setTextSize(14)
        count_tv.setTypeface(AndroidUtilities.bold())
        count_tv.setTextColor(Theme.getColor(Theme.key_dialogTextBlack))
        count_tv.setGravity(Gravity.CENTER)
        ll.addView(count_tv, LayoutHelper.createLinear(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 10, 16, 10, 0))

        preview_tv = TextView(ctx)
        preview_tv.setTextSize(14)
        preview_tv.setTextColor(Theme.getColor(Theme.key_dialogTextGray2))
        preview_tv.setGravity(Gravity.CENTER)
        preview_tv.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(6), AndroidUtilities.dp(24), 0)
        ll.addView(preview_tv, LayoutHelper.createLinear(-1, -2, Gravity.TOP))

        def _on_picker_import(selected):
            try:
                run_on_ui_thread(lambda: pl._import_collection_plugins(metadata, list(selected)))
            except Exception:
                pass

        def _open_plugins_list():
            try:
                sheet.dismiss()
            except Exception:
                pass
            from ..lists.pickers import show_collection_picker
            show_collection_picker(
                pl, _tr("collection_pick_plugins"),
                [str(pid) for pid in plugin_ids],
                state["ids"], _on_picker_import, _tr("import_button"))

        list_btn = TextView(ctx)
        list_btn.setText(_tr("collection_pick_plugins"))
        list_btn.setTextSize(15)
        list_btn.setTypeface(AndroidUtilities.bold())
        list_btn.setGravity(Gravity.CENTER)
        list_btn.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12))
        try:
            lb_bg = GradientDrawable()
            lb_bg.setShape(GradientDrawable.RECTANGLE)
            lb_bg.setCornerRadius(AndroidUtilities.dp(12))
            lb_bg.setStroke(AndroidUtilities.dp(2), Theme.getColor(Theme.key_windowBackgroundWhiteBlueText))
            lb_bg.setColor(0)
            list_btn.setBackground(lb_bg)
        except Exception:
            pass
        list_btn.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText))
        list_btn.setOnClickListener(OnClickListener(lambda v: _open_plugins_list()))
        ll.addView(list_btn, LayoutHelper.createLinear(-1, -2, Gravity.TOP, 24, 12, 24, 0))

        install_btn = TextView(ctx)
        install_btn.setText(_tr("install_button"))
        install_btn.setTextColor(-1)
        install_btn.setTextSize(15)
        install_btn.setTypeface(AndroidUtilities.bold())
        install_btn.setGravity(Gravity.CENTER)
        install_btn.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(13), AndroidUtilities.dp(16), AndroidUtilities.dp(13))
        install_btn.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(12), Theme.getColor(Theme.key_featuredStickers_addButton)))
        install_btn.setOnClickListener(OnClickListener(lambda v: _on_install(sheet, pl, metadata, state)))
        ll.addView(install_btn, LayoutHelper.createLinear(-1, -2, Gravity.TOP, 24, 12, 24, 4))

        cancel_btn = TextView(ctx)
        cancel_btn.setText(_tr("cancel"))
        cancel_btn.setTextSize(14)
        cancel_btn.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText))
        cancel_btn.setGravity(Gravity.CENTER)
        cancel_btn.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10), AndroidUtilities.dp(16), AndroidUtilities.dp(10))
        cancel_btn.setOnClickListener(OnClickListener(lambda v: sheet.dismiss()))
        ll.addView(cancel_btn, LayoutHelper.createLinear(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 10, 0, 10, 20))

        count_tv.setText(_tr("collection_plugins_count").format(len(plugin_ids)))
        preview_tv.setText(_preview_text(plugin_ids))

        fl = FrameLayout(ctx)
        fl.addView(ll)

        from org.telegram.messenger import R as R_tg
        from android.widget import ImageView
        close_view = ImageView(ctx)
        close_view.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector)))
        close_view.setColorFilter(Theme.getColor(Theme.key_sheet_other))
        close_view.setImageResource(R_tg.drawable.ic_layer_close)
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

        sv = NestedScrollView(ctx)
        sv.addView(fl)
        sheet.setCustomView(sv)
        sheet.show()
    except Exception as e:
        log_error(e, context="import designer")


def _on_install(sheet, pl, metadata, state):
    try:
        sheet.dismiss()
    except Exception:
        pass
    pl._import_collection_plugins(metadata, state["ids"])