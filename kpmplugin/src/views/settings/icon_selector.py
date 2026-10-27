import os
from android.view import Gravity, View
from android.widget import LinearLayout, TextView, ImageView
from androidx.core.widget import NestedScrollView
from android_utils import OnClickListener, run_on_ui_thread
from client_utils import get_last_fragment
from org.telegram.messenger import AndroidUtilities, ApplicationLoader, R as R_tg
from org.telegram.ui.ActionBar import BottomSheet, Theme
from org.telegram.ui.Components import LayoutHelper, Switch
from ui.settings import Text

from ...methods import log, log_error, _tr


def _get_icon_drawable(pl, icon_spec, size_dp=24):
    if not icon_spec:
        return None, False
    size_px = int(AndroidUtilities.dp(size_dp))
    ctx = ApplicationLoader.applicationContext
    if isinstance(icon_spec, str) and not icon_spec.endswith(".svg"):
        res_id = getattr(R_tg.drawable, icon_spec, 0)
        if res_id:
            return ctx.getResources().getDrawable(res_id), True

    filename = icon_spec if icon_spec.endswith(".svg") else f"{icon_spec}.svg"
    stem = filename.replace(".svg", "")

    try:
        from elyx import assets
        asset = None
        candidates = [
            f"icons/{filename}",
            f"icons/{stem}",
            filename,
            stem,
        ]
        for key in candidates:
            try:
                if hasattr(assets, "get"):
                    asset = assets.get(key)
                    if asset is not None:
                        break
            except Exception:
                pass
            try:
                asset = assets[key]
                if asset is not None:
                    break
            except Exception:
                pass

        if asset is None:
            try:
                icons_dir = getattr(assets, "icons", None)
                if icons_dir is not None:
                    clean_stem = stem.replace("-", "_")
                    if hasattr(icons_dir, clean_stem):
                        asset = getattr(icons_dir, clean_stem)
                    elif hasattr(icons_dir, "get"):
                        asset = icons_dir.get(filename) or icons_dir.get(stem)
            except Exception:
                pass

        if asset is not None:
            if filename.endswith(".svg") or getattr(asset, "ext", "") == "svg":
                try:
                    d = asset.to_svg_drawable(width=size_px, height=size_px)
                    if d is not None:
                        return d, False
                except Exception:
                    pass
                try:
                    d = asset.to_svg_drawable()
                    if d is not None:
                        return d, False
                except Exception:
                    pass
                try:
                    from org.telegram.messenger import SvgHelper
                    from android.graphics.drawable import BitmapDrawable
                    content = asset.content_string()
                    bmp = SvgHelper.getBitmap(content, size_px, size_px, False)
                    if bmp:
                        return BitmapDrawable(ctx.getResources(), bmp), False
                except Exception:
                    pass

            try:
                d = asset.to_drawable()
                if d is not None:
                    return d, False
            except Exception:
                pass
            try:
                d = asset.to_bitmap_drawable(width=size_px, height=size_px)
                if d is not None:
                    return d, False
            except Exception:
                pass
    except Exception as e:
        log_error(e, context=f"load asset for {icon_spec}")

    try:
        plugin_paths = []
        for attr in ("path", "plugin_dir", "plugins_dir"):
            val = getattr(pl, attr, None)
            if val and isinstance(val, str):
                plugin_paths.append(val)
        plugin_paths.append(os.path.dirname(os.path.dirname(os.path.dirname(__file__))))

        for base in plugin_paths:
            for rel in (os.path.join("res", "icons", filename), os.path.join("res", filename)):
                fpath = os.path.join(base, rel)
                if os.path.isfile(fpath):
                    from org.telegram.messenger import SvgHelper
                    from android.graphics.drawable import BitmapDrawable
                    with open(fpath, "r", encoding="utf-8") as f:
                        svg_content = f.read()
                    bmp = SvgHelper.getBitmap(svg_content, size_px, size_px, False)
                    if bmp:
                        return BitmapDrawable(ctx.getResources(), bmp), False
    except Exception as e:
        log_error(e, context=f"disk search for {icon_spec}")

    if isinstance(icon_spec, int) and icon_spec > 0:
        return ctx.getResources().getDrawable(icon_spec), True

    return None, False


def show_icon_selector_dialog(pl, title, items, current_index, on_select):
    try:
        fragment = get_last_fragment()
        if not fragment:
            return
        ctx = fragment.getParentActivity()
        if not ctx:
            return

        sheet = BottomSheet(ctx, True, fragment.getResourceProvider())
        sheet.setApplyBottomPadding(False)
        sheet.setApplyTopPadding(False)
        sheet.fixNavigationBar(Theme.getColor(Theme.key_dialogBackground))

        ll = LinearLayout(ctx)
        ll.setOrientation(LinearLayout.VERTICAL)
        ll.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8))

        header = TextView(ctx)
        header.setText(title)
        header.setTypeface(AndroidUtilities.bold())
        header.setTextSize(18)
        header.setTextColor(Theme.getColor(Theme.key_dialogTextBlack))
        header.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(12), AndroidUtilities.dp(20), AndroidUtilities.dp(8))
        ll.addView(header)

        for idx, item in enumerate(items):
            item_text = item.get("text", "")
            item_icon = item.get("icon")

            row = LinearLayout(ctx)
            row.setOrientation(LinearLayout.HORIZONTAL)
            row.setGravity(Gravity.CENTER_VERTICAL)
            row.setBackground(Theme.getSelectorDrawable(False))
            row.setClickable(True)
            row.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(12), AndroidUtilities.dp(20), AndroidUtilities.dp(12))

            icon_url = None
            if isinstance(item_icon, str) and (item_icon.startswith("http://") or item_icon.startswith("https://")):
                icon_url = item_icon
            elif not item_icon and item.get("domain"):
                icon_url = f"https://www.google.com/s2/favicons?domain={item['domain']}&sz=64"

            if icon_url:
                try:
                    from org.telegram.ui.Components import BackupImageView
                    from org.telegram.messenger import ImageLocation
                    image_view = BackupImageView(ctx)
                    image_view.setRoundRadius(AndroidUtilities.dp(8))
                    image_view.setImage(ImageLocation.getForPath(icon_url), "60_60", None, None, None)
                    row.addView(image_view, LayoutHelper.createLinear(28, 28, Gravity.CENTER_VERTICAL, 0, 0, 16, 0))
                except Exception as e:
                    log_error(e, context=f"BackupImageView for {icon_url}")
            elif item_icon:
                drawable, _ = _get_icon_drawable(pl, item_icon, size_dp=24)
                if drawable:
                    icon_view = ImageView(ctx)
                    icon_view.setImageDrawable(drawable)
                    icon_view.setColorFilter(Theme.getColor(Theme.key_dialogIcon))
                    row.addView(icon_view, LayoutHelper.createLinear(24, 24, Gravity.CENTER_VERTICAL, 0, 0, 16, 0))
                else:
                    try:
                        from android.util import TypedValue
                        letter = TextView(ctx)
                        letter.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14)
                        letter.setTypeface(AndroidUtilities.bold())
                        letter.setGravity(Gravity.CENTER)
                        letter.setTextColor(Theme.getColor(Theme.key_dialogTextBlue))
                        letter.setBackground(Theme.createRoundRectDrawable(
                            AndroidUtilities.dp(8),
                            Theme.multAlpha(Theme.getColor(Theme.key_dialogTextBlue), 0.12)
                        ))
                        letter.setText(item_text[:1].upper() if item_text else "?")
                        row.addView(letter, LayoutHelper.createLinear(24, 24, Gravity.CENTER_VERTICAL, 0, 0, 16, 0))
                    except Exception:
                        pass
            else:
                try:
                    from android.util import TypedValue
                    letter = TextView(ctx)
                    letter.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14)
                    letter.setTypeface(AndroidUtilities.bold())
                    letter.setGravity(Gravity.CENTER)
                    letter.setTextColor(Theme.getColor(Theme.key_dialogTextBlue))
                    letter.setBackground(Theme.createRoundRectDrawable(
                        AndroidUtilities.dp(8),
                        Theme.multAlpha(Theme.getColor(Theme.key_dialogTextBlue), 0.12)
                    ))
                    letter.setText(item_text[:1].upper() if item_text else "?")
                    row.addView(letter, LayoutHelper.createLinear(28, 28, Gravity.CENTER_VERTICAL, 0, 0, 16, 0))
                except Exception:
                    pass

            item_subtitle = item.get("subtitle")
            if item_subtitle:
                texts = LinearLayout(ctx)
                texts.setOrientation(LinearLayout.VERTICAL)

                tv = TextView(ctx)
                tv.setText(item_text)
                tv.setTextSize(16)
                tv.setTextColor(Theme.getColor(Theme.key_dialogTextBlack))
                texts.addView(tv, LayoutHelper.createLinear(-1, -2))

                sub_tv = TextView(ctx)
                sub_tv.setText(item_subtitle)
                sub_tv.setTextSize(13)
                sub_tv.setTextColor(Theme.getColor(Theme.key_dialogTextGray2))
                texts.addView(sub_tv, LayoutHelper.createLinear(-1, -2, 0, 2, 0, 0))

                row.addView(texts, LayoutHelper.createLinear(0, -2, 1.0, Gravity.CENTER_VERTICAL, 0, 0, 0, 0))
            else:
                tv = TextView(ctx)
                tv.setText(item_text)
                tv.setTextSize(16)
                tv.setTextColor(Theme.getColor(Theme.key_dialogTextBlack))
                row.addView(tv, LayoutHelper.createLinear(0, -2, 1.0, Gravity.CENTER_VERTICAL, 0, 0, 0, 0))

  
            if idx == current_index:
                check_view = ImageView(ctx)
                check_res = getattr(R_tg.drawable, "msg_check", 0)
                if check_res:
                    check_view.setImageResource(check_res)
                check_view.setColorFilter(Theme.getColor(Theme.key_dialogRadioBackgroundChecked))
                row.addView(check_view, LayoutHelper.createLinear(20, 20, Gravity.CENTER_VERTICAL, 8, 0, 0, 0))

            def make_click(i):
                def _click(_):
                    sheet.dismiss()
                    on_select(i)
                return _click

            row.setOnClickListener(OnClickListener(make_click(idx)))
            ll.addView(row, LayoutHelper.createLinear(-1, -2))

        sv = NestedScrollView(ctx)
        sv.addView(ll)
        sheet.setCustomView(sv)
        sheet.show()
    except Exception as e:
        log_error(e, context="show_icon_selector_dialog")


def IconSelector(pl, key, text, items, default=0, icon="msg_link", on_change=None, link_alias=None):

    cur_val = pl.get_setting(key, default)
    try:
        cur_val = int(cur_val or 0)
    except Exception:
        cur_val = default

    cur_item = items[cur_val] if 0 <= cur_val < len(items) else {}
    cur_title = cur_item.get("text", "")
    if cur_item.get("subtitle"):
        cur_title = f"{cur_title} · {cur_item['subtitle']}"

    def _open_dialog():
        current = pl.get_setting(key, default)
        try:
            current = int(current or 0)
        except Exception:
            current = default

        def _on_selected(new_val):
            try:
                pl.set_setting(key, new_val)
            except Exception:
                pass

            if on_change:
                on_change(new_val)

        show_icon_selector_dialog(pl, text, items, current, _on_selected)

    return Text(
        text=text,
        subtext=cur_title,
        icon=icon,
        on_click=lambda _: _open_dialog(),
        link_alias=link_alias,
    )


def show_mirror_selector_dialog(pl, title, items, enabled_keys, on_change):
    try:
        fragment = get_last_fragment()
        if not fragment:
            return
        ctx = fragment.getParentActivity()
        if not ctx:
            return

        sheet = BottomSheet(ctx, True, fragment.getResourceProvider())
        sheet.setApplyBottomPadding(False)
        sheet.setApplyTopPadding(False)
        sheet.fixNavigationBar(Theme.getColor(Theme.key_dialogBackground))

        ll = LinearLayout(ctx)
        ll.setOrientation(LinearLayout.VERTICAL)
        ll.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8))

        header = TextView(ctx)
        header.setText(title)
        header.setTypeface(AndroidUtilities.bold())
        header.setTextSize(18)
        header.setTextColor(Theme.getColor(Theme.key_dialogTextBlack))
        header.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(12), AndroidUtilities.dp(20), AndroidUtilities.dp(8))
        ll.addView(header)

        current_enabled = set(enabled_keys)
        switches = {}

        for item in items:
            key = item.get("key")
            item_text = item.get("text", "")
            item_icon = item.get("icon")

            row = LinearLayout(ctx)
            row.setOrientation(LinearLayout.HORIZONTAL)
            row.setGravity(Gravity.CENTER_VERTICAL)
            row.setBackground(Theme.getSelectorDrawable(False))
            row.setClickable(True)
            row.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(12), AndroidUtilities.dp(20), AndroidUtilities.dp(12))

            if item_icon:
                drawable, _ = _get_icon_drawable(pl, item_icon, size_dp=24)
                if drawable:
                    icon_view = ImageView(ctx)
                    icon_view.setImageDrawable(drawable)
                    icon_view.setColorFilter(Theme.getColor(Theme.key_dialogIcon))
                    row.addView(icon_view, LayoutHelper.createLinear(24, 24, Gravity.CENTER_VERTICAL, 0, 0, 16, 0))

            tv = TextView(ctx)
            tv.setText(item_text)
            tv.setTextSize(16)
            tv.setTextColor(Theme.getColor(Theme.key_dialogTextBlack))
            row.addView(tv, LayoutHelper.createLinear(0, -2, 1.0, Gravity.CENTER_VERTICAL, 0, 0, 10, 0))

            switch_view = Switch(ctx)
            try:
                switch_view.setColors(
                    Theme.key_switchTrack,
                    Theme.key_switchTrackChecked,
                    Theme.key_windowBackgroundWhite,
                    Theme.key_windowBackgroundWhite
                )
            except Exception:
                pass

            is_checked = key in current_enabled
            switch_view.setChecked(is_checked, False)
            switch_view.setFocusable(False)
            switch_view.setClickable(False)
            switches[key] = switch_view
            row.addView(switch_view, LayoutHelper.createLinear(46, 26, Gravity.CENTER_VERTICAL, 10, 0, 0, 0))

            def make_toggle(target_key):
                def _click(_):
                    if target_key in current_enabled:
                        if len(current_enabled) <= 1:
                            return
                        current_enabled.remove(target_key)
                        is_active = False
                    else:
                        current_enabled.add(target_key)
                        is_active = True

                    sw = switches.get(target_key)
                    if sw:
                        sw.setChecked(is_active, True)

                    new_list = [it["key"] for it in items if it["key"] in current_enabled]
                    on_change(new_list)
                return _click

            row.setOnClickListener(OnClickListener(make_toggle(key)))
            ll.addView(row, LayoutHelper.createLinear(-1, -2))

        sv = NestedScrollView(ctx)
        sv.addView(ll)
        sheet.setCustomView(sv)
        sheet.show()
    except Exception as e:
        log_error(e, context="show_mirror_selector_dialog")


def MirrorSelector(pl, key="enabled_store_mirrors", text=None, items=None, default=None, icon="msg_link", on_change=None, link_alias="store_mirrors"):
    if text is None:
        text = _tr("store_mirrors")
    if items is None:
        items = [
            {"key": "kangel",   "text": "Forgejo",  "icon": "fj.svg"},
            {"key": "github",   "text": "GitHub",   "icon": "gh.svg"},
            {"key": "codeberg", "text": "CodeBerg", "icon": "cb.svg"},
            {"key": "gitverse", "text": "GitVerse", "icon": "gv.svg"},
        ]
    if default is None:
        default = ["kangel", "github", "codeberg", "gitverse"]

    stored = pl.get_setting(key, default)
    if isinstance(stored, (list, tuple)):
        current_list = [k for k in stored if any(it["key"] == k for it in items)]
    else:
        current_list = list(default)
    if not current_list:
        current_list = ["kangel"]

    active_names = [it["text"] for it in items if it["key"] in current_list]
    subtitle = ", ".join(active_names)

    def _open_dialog():
        stored_now = pl.get_setting(key, default)
        if isinstance(stored_now, (list, tuple)):
            cur_keys = [k for k in stored_now if any(it["key"] == k for it in items)]
        else:
            cur_keys = list(default)
        if not cur_keys:
            cur_keys = ["kangel"]

        def _on_updated(new_list):
            try:
                pl.set_setting(key, new_list, reload_settings=True)
            except Exception:
                pass
            if on_change:
                on_change(new_list)

        show_mirror_selector_dialog(pl, text, items, cur_keys, _on_updated)

    return Text(
        text=text,
        subtext=subtitle,
        icon=icon,
        on_click=lambda _: _open_dialog(),
        link_alias=link_alias,
    )

