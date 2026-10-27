import os
import json
import traceback
import time
from base_plugin import MethodHook
from android_utils import run_on_ui_thread, OnClickListener
from client_utils import get_last_fragment, run_on_queue
from hook_utils import find_class, get_private_field
from com.exteragram.messenger.plugins import Plugin, PluginsController
from com.exteragram.messenger.plugins.ui.components import PluginCellDelegate
from com.exteragram.messenger.plugins.ui.components import PluginCell
from com.exteragram.messenger.plugins.ui.components.templates import UniversalFragment
from java import dynamic_proxy, jint, jarray
from java.lang import Integer, CharSequence, Boolean
from java.util import ArrayList
from org.telegram.messenger import AndroidUtilities, Utilities
from org.telegram.messenger import R as R_tg
from org.telegram.ui.ActionBar import Theme
from org.telegram.ui.Components import BackupImageView, LayoutHelper, UItem, UniversalRecyclerView
from android.text import TextUtils, SpannableString, Spanned
from android.text.style import StrikethroughSpan
from android.util import TypedValue
from android.view import Gravity, View
from android.widget import FrameLayout, LinearLayout, TextView, ImageView, PopupWindow
from android.graphics.drawable import InsetDrawable, ColorDrawable
from android.graphics import PorterDuff, Color

from ...methods import (
    _tr, _status_label, _status_sort_key, _parse_markdown,
    _normalize_requirements, _normalize_min_version, _fix_plurals,
    log, log_error, format_with_usernames, is_beta_code_viewer_enabled,
)
from ...compat import find_class_safe
PLUGIN_ID = "kangel_plugins_manager"
INSTALL = 0
UPDATE = 1

from .ai_search import show_ai_search_dialog
from .card_menu import _show_card_actions_menu


def show_plugin_list_fragment(pl, title, plugin_ids, display_names, update_mode=False):
    fragment = get_last_fragment()
    if not fragment:
        return
    ftype = UPDATE if update_mode else INSTALL
    delegate = PluginListDelegate(pl, title, plugin_ids, display_names, ftype)
    frag = UniversalFragment(delegate)
    delegate.fragment = frag
    fragment.presentFragment(frag)
    frag.setTitle(title, False, 0)
    _setup_search(frag, delegate, pl)


def _setup_search(frag, delegate, pl):
    action_bar = frag.getActionBar()
    try:
        from org.telegram.ui.ActionBar import BackDrawable
        action_bar.setBackButtonDrawable(BackDrawable(False))
    except Exception:
        action_bar.setBackButtonImage(R_tg.drawable.ic_ab_back)

    def handle_back_action():
        try:
            if action_bar and hasattr(action_bar, "isSearchFieldVisible") and action_bar.isSearchFieldVisible():
                action_bar.closeSearchField()
                return True
        except Exception:
            pass
        frag.finishFragment()
        return False

    if hasattr(action_bar, "backButtonImageView") and action_bar.backButtonImageView:
        action_bar.backButtonImageView.setOnClickListener(OnClickListener(lambda *_: handle_back_action()))

    menu = action_bar.createMenu()
    search_item = menu.addItem(0, R_tg.drawable.outline_header_search).setIsSearchField(True)
    sort_item = menu.addItem(5, R_tg.drawable.menu_sort_value)
    ai_item = menu.addItem(6, R_tg.drawable.msg_addbot)

    try:
        from extera_utils.classes import Base, java_subclass, joverride
        ActionBarMenuItemSearchListener = find_class("org.telegram.ui.ActionBar.ActionBarMenuItem$ActionBarMenuItemSearchListener")

        @java_subclass(ActionBarMenuItemSearchListener)
        class CustomSearchListener(Base):
            @joverride("onSearchExpand")
            def on_search_expand(self):
                try:
                    try:
                        recycler = getattr(delegate, "recycler", None)
                        lm = recycler.getLayoutManager() if recycler else None
                        if lm and hasattr(lm, "findFirstVisibleItemPosition"):
                            pos = lm.findFirstVisibleItemPosition()
                            child = recycler.findViewHolderForAdapterPosition(pos)
                            offset = child.itemView.getTop() if child else 0
                            delegate._saved_scroll_pos = (pos, offset)
                            log(f"[KPM] search expand: saved pos={pos} offset={offset}")
                        else:
                            delegate._saved_scroll_pos = None
                    except Exception as se:
                        log_error(se, context="search expand save scroll")
                        delegate._saved_scroll_pos = None
                    if sort_item:
                        sort_item.setVisibility(View.GONE)
                    if ai_item:
                        ai_item.setVisibility(View.GONE)
                    if delegate.adapter:
                        delegate.adapter.update(True)
                except Exception as e:
                    log_error(e, context="search expand")

            @joverride("onSearchCollapse")
            def on_search_collapse(self):
                try:
                    delegate.search_query = None
                    if sort_item:
                        sort_item.setVisibility(View.VISIBLE)
                    if ai_item:
                        ai_item.setVisibility(View.VISIBLE)
                    if hasattr(delegate, "_apply_header_label") and callable(delegate._apply_header_label):
                        delegate._apply_header_label()
                    saved_pos = getattr(delegate, "_saved_scroll_pos", None)
                    delegate._saved_scroll_pos = None
                    if delegate.adapter:
                        delegate.adapter.update(False)
                    if saved_pos is not None:
                        pos, offset = saved_pos
                        recycler = getattr(delegate, "recycler", None)
                        if recycler:
                            try:
                                Runnable = find_class("java.lang.Runnable")
                                class RestoreScroll(dynamic_proxy(Runnable)):
                                    def run(self):
                                        try:
                                            lm = recycler.getLayoutManager()
                                            if lm and hasattr(lm, "scrollToPositionWithOffset"):
                                                lm.scrollToPositionWithOffset(pos, offset)
                                                log(f"[KPM] scroll restored pos={pos} offset={offset}")
                                        except Exception as e:
                                            log_error(e, context="scroll restore")
                                recycler.post(RestoreScroll())
                            except Exception as e:
                                log_error(e, context="scroll restore post")
                except Exception as e:
                    log_error(e, context="search collapse")

            @joverride("onTextChanged")
            def on_text_changed(self, edit_text):
                try:
                    q = str(edit_text.getText()).strip() if edit_text else ""
                    delegate.search_query = q if q else None
                    if q:
                        delegate.ai_result_ids = None
                    if hasattr(delegate, "_apply_header_label") and callable(delegate._apply_header_label):
                        delegate._apply_header_label()
                    delegate.fill_id += 1
                    if delegate.adapter:
                        delegate.adapter.update(True)
                except Exception as e:
                    log_error(e, context="search text changed")

        search_item.setActionBarMenuItemSearchListener(CustomSearchListener.new_java_instance())
        log("[KPM] ActionBarMenuItemSearchListener attached successfully")
    except Exception as se:
        log(f"[KPM] ActionBarMenuItemSearchListener setup fallback: {se}")
        try:
            search_field = search_item.getSearchField()
            listener = get_private_field(search_field, "mListeners").get(0)

            class SearchChanged(MethodHook):
                def before_hooked_method(self, param):
                    q = search_field.getText().toString().strip()
                    delegate.search_query = q if q else None
                    if q:
                        delegate.ai_result_ids = None
                    if hasattr(delegate, "_apply_header_label") and callable(delegate._apply_header_label):
                        delegate._apply_header_label()
                    delegate.fill_id += 1
                    if delegate.adapter:
                        delegate.adapter.update(True)

            search_hook = pl.hook_method(
                listener.getClass().getDeclaredMethod("onTextChanged", CharSequence, Integer.TYPE, Integer.TYPE, Integer.TYPE),
                SearchChanged())
            delegate._search_hook = search_hook
        except Exception as e:
            log_error(e, context="search changed hook fallback")

    statuses = [
        ("*", _tr("filter_all")),
        ("customization", _tr("status_customization")),
        ("utilities", _tr("status_utilities")),
        ("informational", _tr("status_informational")),
        ("messages", _tr("status_messages")),
        ("fun", _tr("status_fun")),
        ("library", _tr("status_library")),
    ]

    sort_modes = [
        (0, _tr("sort_none")),
        (1, _tr("sort_az")),
        (2, _tr("sort_za")),
        (3, _tr("sort_author_az")),
        (4, _tr("sort_author_za")),
    ]

    def _label_for(key):
        for k, l in statuses:
            if k == key:
                return l
        return _tr("filter_all")

    def _apply_header_label(key=None):
        try:
            if key is None:
                key = str(pl.get_setting("plugins_status_filter", "*") or "*")
            if delegate.ai_result_ids is not None:
                label = f"{_tr('ai_search')}: {len(delegate.ai_result_ids)}"
            elif key == "*" or not key:
                label = _tr("filter_all")
            else:
                keys = [k.strip() for k in key.split(",") if k.strip()]
                names = [_label_for(k) for k in keys if k != "*"]
                if not names:
                    label = _tr("filter_all")
                elif len(names) == 1:
                    label = names[0]
                elif len(names) == 2:
                    label = f"{names[0]}, {names[1]}"
                else:
                    label = f"{names[0]} +{len(names) - 1}"
            action_bar.setSubtitle(str(label))
        except Exception:
            pass
    delegate._apply_header_label = _apply_header_label

    def show_sort_and_filter_dialog(*_):
        try:
            from ui.alert import AlertDialogBuilder
            from android.widget import LinearLayout, TextView, HorizontalScrollView
            from androidx.core.widget import NestedScrollView
            from android.graphics.drawable import GradientDrawable

            act = frag.getParentActivity()
            if not act:
                return

            raw_status = str(pl.get_setting("plugins_status_filter", "*") or "*")
            if raw_status == "*" or not raw_status:
                selected_statuses = set(["*"])
            else:
                selected_statuses = set(k.strip() for k in raw_status.split(",") if k.strip())

            cur_sort = [int(pl.get_setting("plugins_sort_mode", 0) or 0)]

            root = LinearLayout(act)
            root.setOrientation(LinearLayout.VERTICAL)
            root.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(8), AndroidUtilities.dp(16), AndroidUtilities.dp(8))

            def _to_jint(color: int) -> int:
                if color > 0x7FFFFFFF:
                    return color - 0x100000000
                return color

            try:
                accent_color = Theme.getColor(Theme.key_featuredStickers_addButton)
            except Exception:
                accent_color = 0xFF3390EC

            def _make_header(text):
                tv = TextView(act)
                tv.setText(str(text))
                tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14)
                tv.setTypeface(AndroidUtilities.bold())
                try:
                    tv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader))
                except Exception:
                    tv.setTextColor(Theme.getColor(Theme.key_dialogTextBlack))
                tv.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(8), AndroidUtilities.dp(4), AndroidUtilities.dp(6))
                return tv

            root.addView(_make_header(_tr("filter_status")))

            cat_scroll = HorizontalScrollView(act)
            cat_scroll.setHorizontalScrollBarEnabled(False)
            cat_row = LinearLayout(act)
            cat_row.setOrientation(LinearLayout.HORIZONTAL)
            cat_row.setPadding(0, 0, 0, AndroidUtilities.dp(4))
            cat_scroll.addView(cat_row, LinearLayout.LayoutParams(-2, -2))
            root.addView(cat_scroll, LayoutHelper.createLinear(-1, -2, 0, 0, 0, 10))

            cat_chips = []

            def _refresh_cat_chips():
                for k, btn, gd in cat_chips:
                    sel = (k in selected_statuses)
                    if sel:
                        fill_color = (_to_jint(accent_color) & 0x00FFFFFF) | 0x22000000
                        gd.setColor(fill_color)
                        gd.setStroke(AndroidUtilities.dp(1.5), _to_jint(accent_color))
                        btn.setTextColor(_to_jint(accent_color))
                    else:
                        gd.setColor(0)
                        stroke_col = (_to_jint(Theme.getColor(Theme.key_dialogTextGray)) & 0x00FFFFFF) | 0x33000000
                        gd.setStroke(AndroidUtilities.dp(1.0), stroke_col)
                        btn.setTextColor(_to_jint(Theme.getColor(Theme.key_dialogTextBlack)))

            for key, label in statuses:
                btn = TextView(act)
                btn.setText(str(label))
                btn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13)
                btn.setTypeface(AndroidUtilities.bold())
                btn.setSingleLine(True)
                btn.setGravity(Gravity.CENTER)
                btn.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(7), AndroidUtilities.dp(12), AndroidUtilities.dp(7))

                gd = GradientDrawable()
                gd.setShape(GradientDrawable.RECTANGLE)
                gd.setCornerRadius(float(AndroidUtilities.dp(14)))
                btn.setBackground(gd)

                def on_cat_click(k=key):
                    if k == "*":
                        selected_statuses.clear()
                        selected_statuses.add("*")
                    else:
                        selected_statuses.discard("*")
                        if k in selected_statuses:
                            selected_statuses.remove(k)
                            if not selected_statuses:
                                selected_statuses.add("*")
                        else:
                            selected_statuses.add(k)
                    _refresh_cat_chips()

                btn.setOnClickListener(OnClickListener(lambda *_, k=key: on_cat_click(k)))
                lp = LinearLayout.LayoutParams(-2, -2)
                lp.rightMargin = AndroidUtilities.dp(6)
                cat_row.addView(btn, lp)
                cat_chips.append((key, btn, gd))

            _refresh_cat_chips()

            root.addView(_make_header(_tr("plugins_sort")))

            sort_chips = []

            def _refresh_sort_chips():
                for sm, btn, gd in sort_chips:
                    sel = (sm == cur_sort[0])
                    if sel:
                        fill_color = (_to_jint(accent_color) & 0x00FFFFFF) | 0x22000000
                        gd.setColor(fill_color)
                        gd.setStroke(AndroidUtilities.dp(1.5), _to_jint(accent_color))
                        btn.setTextColor(_to_jint(accent_color))
                    else:
                        gd.setColor(0)
                        stroke_col = (_to_jint(Theme.getColor(Theme.key_dialogTextGray)) & 0x00FFFFFF) | 0x33000000
                        gd.setStroke(AndroidUtilities.dp(1.0), stroke_col)
                        btn.setTextColor(_to_jint(Theme.getColor(Theme.key_dialogTextBlack)))

            sort_container = LinearLayout(act)
            sort_container.setOrientation(LinearLayout.VERTICAL)
            root.addView(sort_container, LayoutHelper.createLinear(-1, -2))

            for sm_id, sm_label in sort_modes:
                btn = TextView(act)
                btn.setText(str(sm_label))
                btn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14)
                btn.setTypeface(AndroidUtilities.bold())
                btn.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL)
                btn.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(10), AndroidUtilities.dp(14), AndroidUtilities.dp(10))

                gd = GradientDrawable()
                gd.setShape(GradientDrawable.RECTANGLE)
                gd.setCornerRadius(float(AndroidUtilities.dp(12)))
                btn.setBackground(gd)

                def on_sort_click(s=sm_id):
                    cur_sort[0] = s
                    _refresh_sort_chips()

                btn.setOnClickListener(OnClickListener(lambda *_, s=sm_id: on_sort_click(s)))
                lp = LinearLayout.LayoutParams(-1, -2)
                lp.bottomMargin = AndroidUtilities.dp(6)
                sort_container.addView(btn, lp)
                sort_chips.append((sm_id, btn, gd))

            _refresh_sort_chips()

            scroll = NestedScrollView(act)
            scroll.addView(root, LayoutHelper.createFrame(-1, -2))

            builder = AlertDialogBuilder(act)
            builder.set_title(_tr("plugins_sort"))
            builder.set_view(scroll)

            def do_apply(d, w):
                try:
                    d.dismiss()
                except Exception:
                    pass
                final_status = "*" if ("*" in selected_statuses or not selected_statuses) else ",".join(sorted(selected_statuses))
                pl.set_setting("plugins_status_filter", final_status, reload_settings=False)
                pl.set_setting("plugins_sort_mode", int(cur_sort[0]), reload_settings=False)
                _apply_header_label(final_status)
                if delegate.adapter:
                    delegate.adapter.update(True)

            builder.set_positive_button(_tr("done"), do_apply)
            builder.set_negative_button(_tr("cancel"), lambda d, w: d.dismiss())
            builder.show()

        except Exception as e:
            log_error(e, context="sort/filter dialog")

    try:
        sort_item.setOnClickListener(OnClickListener(show_sort_and_filter_dialog))
    except Exception as e:
        log_error(e, context="set sort_item click listener")

    try:
        def open_ai_search(*_):
            show_ai_search_dialog(frag, delegate, pl, _apply_header_label)

        ai_item.setOnClickListener(OnClickListener(open_ai_search))
    except Exception as e:
        log_error(e, context="set ai_item click listener")

    try:
        current_filter = str(pl.get_setting("plugins_status_filter", "*") or "*")
    except Exception:
        current_filter = "*"
    _apply_header_label(current_filter)


class PluginListDelegate(dynamic_proxy(UniversalFragment.UniversalFragmentDelegate)):

    class C2(dynamic_proxy(Utilities.Callback2)):
        def __init__(self, fn):
            super().__init__()
            self.fn = fn
        def run(self, *args):
            self.fn(*args)

    class C5(dynamic_proxy(Utilities.Callback5)):
        def __init__(self, fn):
            super().__init__()
            self.fn = fn
        def run(self, *args):
            self.fn(*args)

    class C5R(dynamic_proxy(Utilities.Callback5Return)):
        def __init__(self, fn):
            super().__init__()
            self.fn = fn
        def run(self, *args):
            return self.fn(*args)

    class CellDelegate(dynamic_proxy(PluginCellDelegate)):
        def __init__(self, pid, outer):
            super().__init__()
            self.pid = pid
            self.outer = outer
        def canOpenInExternalApp(self):
            return False
        def deletePlugin(self):
            return None
        def openInExternalApp(self):
            pass
        def openPluginSettings(self):
            pass
        def pinPlugin(self, view):
            pass
        def sharePlugin(self):
            fragment = get_last_fragment()
            if not fragment:
                return
            plugin_id = self.pid
            link = f"tg://kpm_install?plugin={plugin_id}"
            AndroidUtilities.addToClipboard(link)
            from ui.bulletin import BulletinHelper
            BulletinHelper.show_copied_to_clipboard()
        def togglePlugin(self, view):
            pass

    class PluginCellHook(MethodHook):
        def __init__(self, outer):
            self.create_button_method = None
            self.outer = outer

        def after_hooked_method(self, param):
            try:
                this = param.thisObject
                p = param.args[0]
                if not p or p.getEngine() != PLUGIN_ID:
                    return

                try:
                    this.setNeedDivider(False)
                except Exception:
                    pass

                try:
                    get_private_field(this, "pinButton").setVisibility(View.GONE)
                except Exception:
                    pass
                try:
                    get_private_field(this, "checkBox").setVisibility(View.GONE)
                except Exception:
                    pass
                try:
                    get_private_field(this, "deleteButton").setVisibility(View.GONE)
                except Exception:
                    pass
                try:
                    get_private_field(this, "openInButton").setVisibility(View.GONE)
                except Exception:
                    pass
                try:
                    get_private_field(this, "shareButton").setVisibility(View.GONE)
                except Exception:
                    pass

                plugin_id = p.getId()
                plugin_info = self.outer.pl.store.get_plugin_info(plugin_id)
                if not plugin_info:
                    plugin_info = self.outer.pl.plugins_list.get(plugin_id, {})

                try:
                    from ...inline_icons import InlineIconManager
                    image_view = get_private_field(this, "imageView")
                    if image_view is not None:
                        icon_spec = plugin_info.get("icon") if isinstance(plugin_info, dict) else None
                        if not icon_spec and hasattr(p, "getPack") and hasattr(p, "getIndex"):
                            pack = p.getPack()
                            idx = p.getIndex()
                            if pack and idx is not None and idx >= 0:
                                icon_spec = f"{pack}/{idx}"
                        InlineIconManager.apply_icon_to_backup_image_view(
                            image_view, icon_spec, tag_prefix=f"kpm_store_{plugin_id}", placeholder_size_dp=56
                        )
                except Exception as e:
                    log_error(e, context="PluginCellHook icon apply")

                author = ""
                description = ""
                min_version = ""
                app_version = ""
                dependencies = []
                requirements = []
                status = "plugin"
                if isinstance(plugin_info, dict):
                    author = plugin_info.get("author", "")
                    description = plugin_info.get("description", "")
                    min_version = plugin_info.get("min_version", "") or _normalize_min_version(plugin_info.get("app_version", ""))
                    app_version = plugin_info.get("app_version", "") or ""
                    dependencies = _normalize_requirements(plugin_info.get("dependencies", []) or [])
                    requirements = _normalize_requirements(plugin_info.get("requirements", []) or [])
                    status = plugin_info.get("status", "plugin") or "plugin"

                try:
                    from ...methods import _get_localized_description
                    loc_desc = _get_localized_description(
                        plugin_info, self.outer.pl.plugins_dir, plugin_id)
                    if loc_desc:
                        description = loc_desc
                except Exception:
                    pass

                client_version = self.outer.pl._mkstats_get_client_version()
                version_compatible = True
                if min_version and client_version and client_version != "unknown":
                    try:
                        def parse_ver(v):
                            return [int(x) if x.isdigit() else 0 for x in str(v).split(".")]
                        cv = parse_ver(client_version)
                        mv = parse_ver(min_version)
                        for i in range(max(len(cv), len(mv))):
                            c = cv[i] if i < len(cv) else 0
                            m = mv[i] if i < len(mv) else 0
                            if c < m:
                                version_compatible = False
                                break
                            elif c > m:
                                break
                    except Exception:
                        version_compatible = False
                elif min_version:
                    version_compatible = False
                if not version_compatible and min_version:
                    description = _tr("requires_version").format(min_version)

                try:
                    desc = get_private_field(this, "subtitleView")
                    reserve_right_dp = 0
                    try:
                        title_view = get_private_field(this, "titleView")
                        title_view.setPadding(title_view.getPaddingLeft(), title_view.getPaddingTop(), reserve_right_dp, title_view.getPaddingBottom())
                    except Exception:
                        pass
                    try:
                        desc.setPadding(desc.getPaddingLeft(), desc.getPaddingTop(), reserve_right_dp, desc.getPaddingBottom())
                    except Exception:
                        pass

                    if self.outer.ftype == UPDATE:
                        old, new = self.outer.display_names.get("vers", {}).get(plugin_id, ("", ""))
                        if old and new:
                            label = f'{old} -> {new}'
                            s = SpannableString(label)
                            try:
                                s.setSpan(StrikethroughSpan(), 0, len(old), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            except Exception:
                                pass
                            desc.setText(s)
                        else:
                            desc.setText(p.getVersion() or "")
                    else:
                        version_text = p.getVersion() or ""
                        if author:
                            import re as _re
                            author_str = str(author).strip()
                            if _re.match(r'^[a-zA-Z0-9_]{3,32}$', author_str):
                                author_str = f"@{author_str}"
                            frag = getattr(self.outer, "fragment", None)
                            formatted_author = format_with_usernames(author_str, fragment=frag)
                            if version_text:
                                from android.text import SpannableStringBuilder
                                builder = SpannableStringBuilder(f"{version_text} \u2022 ")
                                builder.append(formatted_author or author_str)
                                desc.setText(builder)
                            else:
                                desc.setText(formatted_author or author_str)
                            try:
                                desc.setMovementMethod(AndroidUtilities.LinkMovementMethodMy())
                                desc.setHighlightColor(0)
                            except Exception:
                                pass
                        else:
                            desc.setText(version_text)
                except Exception:
                    pass

                try:
                    old_bubbles = this.findViewWithTag("kpm_bubbles")
                    if old_bubbles:
                        this.removeView(old_bubbles)
                except Exception:
                    pass

                try:
                    bubbles_enabled = self.outer.pl.get_setting("show_plugin_bubbles", True)
                    if bubbles_enabled:
                        ctx2 = this.getContext()
                        bubbles = LinearLayout(ctx2)
                        bubbles.setTag("kpm_bubbles")
                        bubbles.setOrientation(LinearLayout.HORIZONTAL)
                        bubbles.setGravity(Gravity.LEFT)
                        try:
                            bubbles.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(4), AndroidUtilities.dp(12), AndroidUtilities.dp(4))
                        except Exception:
                            pass

                        def _make_bubble(text, color_key, alpha=0x22):
                            tv = TextView(ctx2)
                            tv.setText(str(text))
                            tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12)
                            tv.setSingleLine(True)
                            tv.setEllipsize(TextUtils.TruncateAt.END)
                            try:
                                tv.setTypeface(AndroidUtilities.bold())
                            except Exception:
                                pass
                            try:
                                c = Theme.getColor(color_key)
                            except Exception:
                                c = Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader)
                            tv.setTextColor(c)
                            try:
                                bg = Theme.createRoundRectDrawable(AndroidUtilities.dp(14), (c & 0x00FFFFFF) | (alpha << 24))
                                tv.setBackground(bg)
                            except Exception:
                                pass
                            tv.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(4), AndroidUtilities.dp(10), AndroidUtilities.dp(4))
                            tv.setMaxWidth(AndroidUtilities.dp(150))
                            lp = LinearLayout.LayoutParams(-2, -2)
                            lp.rightMargin = AndroidUtilities.dp(6)
                            tv.setLayoutParams(lp)
                            return tv

                        try:
                            bubbles.addView(_make_bubble(_status_label(status), Theme.key_windowBackgroundWhiteBlueHeader, alpha=0x18))
                        except Exception:
                            pass

                        try:
                            dep_names = []
                            for dep_id in dependencies:
                                dep_info = self.outer.pl.plugins_list.get(dep_id)
                                if isinstance(dep_info, dict) and dep_info.get("name"):
                                    dep_names.append(dep_info.get("name"))
                                else:
                                    dep_names.append(str(dep_id))
                            dep_text = ", ".join(dep_names[:2])
                            if len(dep_names) > 2:
                                dep_text += f" +{len(dep_names) - 2}"
                            if dep_text:
                                bubbles.addView(_make_bubble(dep_text, Theme.key_windowBackgroundWhiteBlueText, alpha=0x18))
                        except Exception:
                            pass

                        try:
                            this.addView(bubbles, LayoutHelper.createFrame(-2, -2, Gravity.LEFT | Gravity.BOTTOM, 12, 0, 104, 8))
                        except Exception:
                            try:
                                this.addView(bubbles)
                            except Exception:
                                pass
                except Exception:
                    pass

                current_desc = description
                pid = p.getId()
                if self.outer.active_translations.get(pid, False):
                    cached_tr = self.outer.translated_descriptions.get(pid)
                    if cached_tr:
                        current_desc = cached_tr

                try:
                    description_view = get_private_field(this, "descriptionView")
                    try:
                        description_view.setPadding(description_view.getPaddingLeft(), description_view.getPaddingTop(), 0, description_view.getPaddingBottom())
                    except Exception:
                        pass
                    if current_desc:
                        description_view.setVisibility(View.VISIBLE)
                        if not version_compatible and min_version:
                            try:
                                description_view.setTextColor(-0x10000)
                                description_view.setTypeface(AndroidUtilities.bold())
                                description_view.invalidate()
                            except Exception:
                                pass
                        spannable, plain = _parse_markdown(current_desc)
                        if spannable:
                            description_view.setText(spannable)
                        else:
                            description_view.setText(plain)
                        description_view.invalidate()
                        description_view.setMaxLines(10 ** 8)
                        description_view.setEllipsize(TextUtils.TruncateAt.END)
                    else:
                        description_view.setVisibility(View.GONE)
                except Exception:
                    pass

                try:
                    share_button = get_private_field(this, "shareButton")
                except Exception:
                    share_button = None

                try:
                    submenu_mode = bool(self.outer.pl.get_setting("card_actions_submenu", False))
                except Exception:
                    submenu_mode = False

                if not self.create_button_method:
                    self.create_button_method = this.getClass().getDeclaredMethod("createButton", find_class("android.content.Context"), Integer.TYPE, Boolean.TYPE, find_class("android.view.View$OnClickListener"))
                    self.create_button_method.setAccessible(True)

                def create_button(icon, fn):
                    return self.create_button_method.invoke(
                        this,
                        get_last_fragment().getContext(),
                        jint(icon),
                        False,
                        OnClickListener(lambda *_: fn()))

                _btn_trans_holder = [None]

                def _do_translate_description():
                    try:
                        cur_pid = p.getId()
                        cached_tr = self.outer.translated_descriptions.get(cur_pid)
                        if cached_tr:
                            if self.outer.active_translations.get(cur_pid, False):
                                self.outer.active_translations[cur_pid] = False
                                if _btn_trans_holder[0] and hasattr(_btn_trans_holder[0], "setState"):
                                    _btn_trans_holder[0].setState(0)
                                if description_view:
                                    spannable, plain = _parse_markdown(description)
                                    description_view.setText(spannable if spannable else plain)
                                    description_view.invalidate()
                            else:
                                self.outer.active_translations[cur_pid] = True
                                if _btn_trans_holder[0] and hasattr(_btn_trans_holder[0], "setState"):
                                    _btn_trans_holder[0].setState(2)
                                if description_view:
                                    spannable, plain = _parse_markdown(cached_tr)
                                    description_view.setText(spannable if spannable else plain)
                                    description_view.invalidate()
                            return

                        if not description:
                            return

                        if _btn_trans_holder[0] and hasattr(_btn_trans_holder[0], "setState"):
                            _btn_trans_holder[0].setState(1)

                        TranslatorUtils = find_class_safe("com.exteragram.messenger.translator.TranslatorUtils")
                        if not TranslatorUtils:
                            if _btn_trans_holder[0] and hasattr(_btn_trans_holder[0], "setState"):
                                _btn_trans_holder[0].setState(0)
                            return

                        to_lang = TranslatorUtils.getResolvedTargetLanguageCode()

                        outer_delegate = self.outer

                        class TransCallback(dynamic_proxy(TranslatorUtils.TranslateCallback)):
                            def onSuccess(self, *args):
                                res = ""
                                for arg in args:
                                    if isinstance(arg, str):
                                        res = arg
                                        break
                                    elif hasattr(arg, "text"):
                                        res = str(arg.text)
                                        break

                                def _apply_ui():
                                    if res:
                                        outer_delegate.translated_descriptions[cur_pid] = res
                                        outer_delegate.active_translations[cur_pid] = True
                                        if _btn_trans_holder[0] and hasattr(_btn_trans_holder[0], "setState"):
                                            _btn_trans_holder[0].setState(2)
                                        if description_view:
                                            spannable, plain = _parse_markdown(res)
                                            description_view.setText(spannable if spannable else plain)
                                            description_view.invalidate()
                                    else:
                                        if _btn_trans_holder[0] and hasattr(_btn_trans_holder[0], "setState"):
                                            _btn_trans_holder[0].setState(0)
                                run_on_ui_thread(_apply_ui)

                            def onFailed(self, *args):
                                def _fail_ui():
                                    if _btn_trans_holder[0] and hasattr(_btn_trans_holder[0], "setState"):
                                        _btn_trans_holder[0].setState(0)
                                run_on_ui_thread(_fail_ui)

                        TranslatorUtils.translate(description, "auto", to_lang, TransCallback())
                    except Exception as e:
                        log_error(e, context="translate description")
                        if _btn_trans_holder[0] and hasattr(_btn_trans_holder[0], "setState"):
                            _btn_trans_holder[0].setState(0)

                share_action = None
                menu_actions = []

                if self.outer.ftype == INSTALL:
                    if share_button is not None:
                        share_action = (R_tg.drawable.msg_share, _tr("menu_share"), lambda: share_button.performClick())

                    def open_raw():
                        try:
                            plugin_id = p.getId()
                            plugin_info2 = self.outer.pl.plugins_list.get(plugin_id)
                            if not plugin_info2:
                                return
                            url2 = plugin_info2.get("url") if isinstance(plugin_info2, dict) else plugin_info2
                            if url2:
                                raw_url = str(url2)
                                is_beta_viewer = is_beta_code_viewer_enabled(self.outer.pl)
                                if is_beta_viewer:
                                    from ..sheets import open_remote_code_or_archive
                                    raw_file = raw_url.split("?")[0].split("/")[-1]
                                    ext = os.path.splitext(raw_file)[1] if "." in raw_file else ".plugin"
                                    title = f"{plugin_id}{ext}"
                                    frag = getattr(self.outer, "fragment", None) or get_last_fragment()
                                    open_remote_code_or_archive(raw_url, title=title, target_fragment=frag)
                                else:
                                    from ...methods import open_link
                                    open_link(raw_url)
                        except Exception as e:
                            log_error(e, context="open_raw")
                    menu_actions.append((R_tg.drawable.menu_instant_view, _tr("menu_view_raw"), open_raw))

                    if version_compatible:
                        try:
                            pid_for_versions = p.getId()
                            pinfo_for_versions = self.outer.pl.plugins_list.get(pid_for_versions, {})
                            if isinstance(pinfo_for_versions, dict):
                                legacy_v = pinfo_for_versions.get("legacy_version", {})
                                cur_url = pinfo_for_versions.get("url", "")
                                cur_hash = pinfo_for_versions.get("hash", "")
                                ver_list = []
                                cur_ver = pinfo_for_versions.get("version", "")
                                if cur_url:
                                    ver_label = f"{_tr('current_version')} ({cur_ver})" if cur_ver else _tr("current_version")
                                    ver_list.append({"label": ver_label, "url": cur_url, "hash": cur_hash})
                                for vk in sorted(legacy_v.keys(), reverse=True):
                                    vi = legacy_v[vk]
                                    if isinstance(vi, dict):
                                        ver_list.append({"label": str(vk), "url": vi.get("url", ""), "hash": vi.get("hash", "")})
                                if len(ver_list) > 1:
                                    menu_actions.append((R_tg.drawable.msg_forward_replace, _tr("menu_version_history"), lambda pid=pid_for_versions, vl=ver_list: self.outer.pl._show_version_picker(pid, vl)))
                        except Exception:
                            pass
                        menu_actions.append((R_tg.drawable.msg_download, _tr("install_button"), lambda: self.outer.pl.show_plugin_info_and_install(p.getId())))

                elif self.outer.ftype == UPDATE:
                    def do_single_update():
                        self.outer.plugin_ids.remove(p.getId())
                        run_on_ui_thread(lambda: self.outer.adapter.update(True))
                        self.outer.pl.update_selected_plugins([p.getId()], lambda: None)
                    menu_actions.append((R_tg.drawable.msg_retry, _tr("menu_update"), do_single_update))

                    def on_ignore():
                        pid = p.getId()
                        self.outer.pl.ignore_update(pid)
                        self.outer.plugin_ids.remove(pid)
                        self.outer.adapter.update(True)
                    try:
                        ignore_icon = R_tg.drawable.msg_close
                    except Exception:
                        ignore_icon = R_tg.drawable.msg_delete
                    menu_actions.append((ignore_icon, _tr("menu_ignore_update"), on_ignore))

                translatable_desc = False
                TranslatorUtils = find_class_safe("com.exteragram.messenger.translator.TranslatorUtils")
                PluginTranslateButton = find_class_safe("com.exteragram.messenger.plugins.ui.components.PluginTranslateButton")
                if description and TranslatorUtils is not None and hasattr(TranslatorUtils, "translate") and hasattr(TranslatorUtils, "getResolvedTargetLanguageCode"):
                    try:
                        translatable_desc = True
                    except Exception:
                        translatable_desc = False

                old_trans_btn = this.findViewWithTag("kpm_translate_btn")
                if old_trans_btn:
                    this.removeView(old_trans_btn)

                if translatable_desc:
                    if description_view:
                        try:
                            description_view.setClickable(True)
                            description_view.setOnClickListener(OnClickListener(lambda *_: _do_translate_description()))
                        except Exception:
                            pass

                    if submenu_mode:
                        menu_actions.append((R_tg.drawable.msg_translate, _tr("menu_translate"), _do_translate_description))

                    try:
                        ctx = get_last_fragment().getContext()
                        btn_color = Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon)
                        if PluginTranslateButton is not None:
                            trans_btn = PluginTranslateButton(ctx, btn_color)
                            _btn_trans_holder[0] = trans_btn
                            cur_pid = p.getId()
                            if self.outer.active_translations.get(cur_pid, False):
                                trans_btn.setState(2)
                            else:
                                trans_btn.setState(0)
                        else:
                            trans_btn = ImageView(ctx)
                            trans_btn.setScaleType(ImageView.ScaleType.CENTER)
                            trans_btn.setImageResource(R_tg.drawable.msg_translate)
                            trans_btn.setColorFilter(btn_color, PorterDuff.Mode.SRC_IN)
                            _btn_trans_holder[0] = trans_btn

                        trans_btn.setTag("kpm_translate_btn")

                        try:
                            from android.graphics.drawable import GradientDrawable, StateListDrawable
                            stroke_color = Theme.getColor(Theme.key_divider)
                            bg_normal = GradientDrawable()
                            bg_normal.setShape(GradientDrawable.OVAL)
                            bg_normal.setColor(Theme.getColor(Theme.key_windowBackgroundWhite))
                            bg_normal.setStroke(AndroidUtilities.dp(1), stroke_color)

                            bg_pressed = GradientDrawable()
                            bg_pressed.setShape(GradientDrawable.OVAL)
                            bg_pressed.setColor(Theme.getColor(Theme.key_listSelector))
                            bg_pressed.setStroke(AndroidUtilities.dp(1), Theme.getColor(Theme.key_featuredStickers_addButton))

                            states = StateListDrawable()
                            states.addState([16842919], bg_pressed)
                            states.addState([], bg_normal)
                            trans_btn.setBackground(states)
                        except Exception:
                            try:
                                trans_btn.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector), 1, AndroidUtilities.dp(16)))
                            except Exception:
                                pass

                        try:
                            ScaleStateListAnimator = find_class_safe("org.telegram.ui.Components.ScaleStateListAnimator")
                            if ScaleStateListAnimator is not None:
                                ScaleStateListAnimator.apply(trans_btn, 0.15, 1.5)
                        except Exception:
                            pass

                        trans_btn.setOnClickListener(OnClickListener(lambda *_: _do_translate_description()))
                        this.addView(trans_btn, LayoutHelper.createFrame(32, 32, Gravity.TOP | Gravity.RIGHT, 0, 10, 12, 0))
                    except Exception as e:
                        log_error(e, context="create translate button")

                existing_layout = this.findViewWithTag("kpm_buttons")
                if existing_layout:
                    this.removeView(existing_layout)

                new_buttons_layout = LinearLayout(get_last_fragment().getContext())
                new_buttons_layout.setTag("kpm_buttons")
                new_buttons_layout.setOrientation(LinearLayout.HORIZONTAL)
                gravity = Gravity.BOTTOM

                def _row_button_params(is_last=False):
                    lp = LinearLayout.LayoutParams(AndroidUtilities.dp(40), AndroidUtilities.dp(40))
                    lp.gravity = Gravity.CENTER_VERTICAL
                    if not is_last:
                        lp.rightMargin = AndroidUtilities.dp(6)
                    return lp

                if submenu_mode:
                    try:
                        if share_button is not None:
                            share_button.setVisibility(View.GONE)
                    except Exception:
                        pass

                    all_actions = ([share_action] if share_action else []) + menu_actions
                    if all_actions:
                        try:
                            menu_icon = R_tg.drawable.ic_ab_other
                        except Exception:
                            menu_icon = R_tg.drawable.msg_download

                        menu_button_holder = []

                        def open_menu():
                            ctx = get_last_fragment().getContext()
                            _show_card_actions_menu(ctx, menu_button_holder[0], all_actions)

                        menu_button = create_button(menu_icon, open_menu)
                        menu_button_holder.append(menu_button)
                        new_buttons_layout.addView(menu_button, _row_button_params(True))
                else:
                    try:
                        if share_button is not None:
                            if self.outer.ftype == INSTALL:
                                share_button.setVisibility(View.VISIBLE)
                                if share_button.getParent():
                                    share_button.getParent().removeView(share_button)
                                new_buttons_layout.addView(share_button, _row_button_params(False))
                            else:
                                share_button.setVisibility(View.GONE)
                    except Exception:
                        pass

                    for idx, (icon_res, _label, fn) in enumerate(menu_actions):
                        if icon_res is None:
                            continue
                        is_last = (idx == len(menu_actions) - 1)
                        btn = create_button(icon_res, fn)
                        new_buttons_layout.addView(btn, _row_button_params(is_last))

                this.addView(new_buttons_layout, LayoutHelper.createFrame(-2, -2, Gravity.BOTTOM | Gravity.RIGHT, 0, 0, 16, 8))

                this.setAlpha(1.0)
                this.setScaleX(1.0)
                this.setScaleY(1.0)
                this.setTranslationY(0.0)

            except Exception as e:
                log_error(e, context="PluginCellHook")

    CHUNK_SIZE = 25

    def __init__(self, pl, title, plugin_ids, display_names, ftype=0):
        super().__init__()
        self.pl = pl
        self.title_text = title
        self.plugin_ids = list(plugin_ids)
        self.display_names = dict(display_names) if isinstance(display_names, dict) else display_names
        self.ftype = ftype
        self.search_query = None
        self.adapter = None
        self.fill_id = 0
        self.ai_result_ids = None
        self._saved_scroll_pos = None
        self.scroll_controls = None
        self.scroll_label = None
        self.scroll_icon = None
        self.scroll_controls_visible = True
        self.empty_view = None
        self.md3_enabled = False
        self.translated_descriptions = {}
        self.active_translations = {}

    def getTitle(self):
        return self.title_text

    def onBackPressed(self):
        try:
            frag = get_last_fragment()
            if frag and hasattr(frag, "getActionBar"):
                ab = frag.getActionBar()
                if ab and hasattr(ab, "isSearchFieldVisible") and ab.isSearchFieldVisible():
                    ab.closeSearchField()
                    self.search_query = None
                    if hasattr(self, "_apply_header_label") and callable(self._apply_header_label):
                        self._apply_header_label()
                    if self.adapter:
                        self.adapter.update(True)
                    return False
        except Exception as e:
            log_error(e, context="onBackPressed")
        return None

    def _scroll_list_to(self, edge):
        try:
            if not getattr(self, "recycler", None):
                return
            adapter = self.recycler.getAdapter() if hasattr(self.recycler, "getAdapter") else None
            item_count = adapter.getItemCount() if adapter and hasattr(adapter, "getItemCount") else 0
            if item_count <= 0:
                return
            position = 0 if edge == "top" else max(0, item_count - 1)
            try:
                self.recycler.smoothScrollToPosition(position)
            except Exception:
                self.recycler.scrollToPosition(position)
        except Exception:
            pass

    def _handle_scroll_control_click(self):
        try:
            self._scroll_list_to("bottom")
        except Exception:
            pass

    def _update_scroll_control_state(self):
        try:
            if not self.scroll_controls or not self.scroll_label or not self.scroll_icon or not getattr(self, "recycler", None):
                return
            if self.ftype == UPDATE and len(self.plugin_ids) > 0:
                return
            adapter = self.recycler.getAdapter() if hasattr(self.recycler, "getAdapter") else None
            item_count = adapter.getItemCount() if adapter and hasattr(adapter, "getItemCount") else 0
            if item_count <= 1:
                self.scroll_controls.setVisibility(View.GONE)
                self.scroll_controls_visible = False
                return
            self.scroll_label.setText(_tr("scroll_to_end"))
            self.scroll_icon.setImageResource(R_tg.drawable.msg_go_down)
            if self.scroll_controls_visible:
                self.scroll_controls.setVisibility(View.VISIBLE)
        except Exception:
            pass

    def _set_scroll_control_visible(self, visible, animated=True):
        try:
            if not self.scroll_controls:
                return
            if self.ftype == UPDATE and len(self.plugin_ids) > 0:
                return
            if self.scroll_controls_visible == visible and self.scroll_controls.getVisibility() in [View.VISIBLE, View.GONE]:
                return
            self.scroll_controls_visible = visible
            if visible:
                self.scroll_controls.setVisibility(View.VISIBLE)
                if animated:
                    try:
                        self.scroll_controls.animate().alpha(1.0).translationY(0).scaleX(1.0).scaleY(1.0).setDuration(200).start()
                    except Exception:
                        pass
            else:
                if animated:
                    try:
                        self.scroll_controls.animate().alpha(0.0).translationY(AndroidUtilities.dp(28)).scaleX(0.90).scaleY(0.90).setDuration(240).start()
                        run_on_ui_thread(lambda: self.scroll_controls.setVisibility(View.GONE) if self.scroll_controls else None, 250)
                    except Exception:
                        pass
                else:
                    self.scroll_controls.setAlpha(0.0)
                    self.scroll_controls.setVisibility(View.GONE)
        except Exception:
            pass

    def beforeCreateView(self):
        ctx = get_last_fragment().getContext()
        self.content_view = FrameLayout(ctx)
        self.content_view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray))

        self.recycler = UniversalRecyclerView(
            get_last_fragment(),
            self.C2(self.fillItems),
            self.C5(self.onClick),
            self.C5R(self.onLongClick)
        )

        try:
            self.recycler.setSections()
        except Exception:
            pass

        try:
            self.recycler.setClipToPadding(False)
            self.recycler.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8))
        except Exception as e:
            log_error(e, context="recycler setClipToPadding")

        try:
            if hasattr(self.recycler, "adapter") and self.recycler.adapter and hasattr(self.recycler.adapter, "setApplyBackground"):
                self.recycler.adapter.setApplyBackground(False)
        except Exception:
            pass

        self.content_view.addView(self.recycler, LayoutHelper.createFrame(-1, -1))

        try:
            empty_view = TextView(ctx)
            empty_view.setGravity(Gravity.CENTER)
            empty_view.setText(_tr("no_plugins_found"))
            empty_view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16)
            empty_view.setTypeface(AndroidUtilities.bold())
            empty_view.setVisibility(View.GONE)
            empty_view.setTextColor(Theme.getColor(Theme.key_dialogTextGray2))
            self.empty_view = empty_view
            self.content_view.addView(empty_view, LayoutHelper.createFrame(-1, -1, Gravity.CENTER, 24, 0, 24, 0))
        except Exception:
            pass

        try:
            controls = LinearLayout(ctx)
            controls.setOrientation(LinearLayout.HORIZONTAL)
            controls.setGravity(Gravity.CENTER_VERTICAL)
            controls.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(10), AndroidUtilities.dp(12), AndroidUtilities.dp(10))
            controls.setOnClickListener(OnClickListener(lambda *_: self._handle_scroll_control_click()))
            controls.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(20), Theme.getColor(Theme.key_featuredStickers_addButton)))

            label = TextView(ctx)
            label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13)
            label.setTypeface(AndroidUtilities.bold())
            label.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText))
            label.setText(_tr("scroll_to_end"))
            controls.addView(label, LinearLayout.LayoutParams(-2, -2))

            from android.widget import ImageView
            icon = ImageView(ctx)
            icon.setScaleType(ImageView.ScaleType.CENTER)
            icon.setImageResource(R_tg.drawable.msg_go_down)
            icon.setColorFilter(Theme.getColor(Theme.key_featuredStickers_buttonText))
            icon_lp = LinearLayout.LayoutParams(AndroidUtilities.dp(20), AndroidUtilities.dp(20))
            icon_lp.leftMargin = AndroidUtilities.dp(8)
            controls.addView(icon, icon_lp)

            self.scroll_controls = controls
            self.scroll_label = label
            self.scroll_icon = icon
            self.scroll_controls.setAlpha(1.0)
            self.scroll_controls.setTranslationY(0.0)
            if self.ftype == UPDATE and len(self.plugin_ids) > 0:
                controls.setVisibility(View.GONE)
            self.content_view.addView(controls, LayoutHelper.createFrame(-2, -2, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, 0, 0, 28))

            OnScrollChangeListener = find_class("android.view.View$OnScrollChangeListener")
            outer_ref = self

            class KPMScrollListener(dynamic_proxy(OnScrollChangeListener)):
                def __init__(self):
                    super().__init__()
                def onScrollChange(self, view, scrollX, scrollY, oldScrollX, oldScrollY):
                    dy = int(scrollY) - int(oldScrollY)
                    if dy > 0:
                        outer_ref._set_scroll_control_visible(False, True)
                    elif dy < 0:
                        outer_ref._set_scroll_control_visible(True, True)

            self.scroll_listener = KPMScrollListener()
            self.recycler.setOnScrollChangeListener(self.scroll_listener)
            run_on_ui_thread(lambda: self._update_scroll_control_state(), 50)
            run_on_ui_thread(lambda: self._set_scroll_control_visible(True, False), 50)
        except Exception:
            pass

        if self.ftype == UPDATE and len(self.plugin_ids) > 0:
            try:
                updating = False

                update_pill = LinearLayout(ctx)
                update_pill.setOrientation(LinearLayout.HORIZONTAL)
                update_pill.setGravity(Gravity.CENTER_VERTICAL)
                update_pill.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(10), AndroidUtilities.dp(12), AndroidUtilities.dp(10))
                update_pill.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(20), Theme.getColor(Theme.key_featuredStickers_addButton)))

                update_all_label = TextView(ctx)
                update_all_label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13)
                update_all_label.setTypeface(AndroidUtilities.bold())
                update_all_label.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText))
                update_all_label.setText(f"{_tr('update_plugins')} ({len(self.plugin_ids)})")
                update_pill.addView(update_all_label, LinearLayout.LayoutParams(-2, -2))

                update_all_icon = ImageView(ctx)
                update_all_icon.setScaleType(ImageView.ScaleType.CENTER)
                update_all_icon.setImageResource(R_tg.drawable.msg_retry)
                update_all_icon.setColorFilter(Theme.getColor(Theme.key_featuredStickers_buttonText))
                icon_lp = LinearLayout.LayoutParams(AndroidUtilities.dp(20), AndroidUtilities.dp(20))
                icon_lp.leftMargin = AndroidUtilities.dp(8)
                update_pill.addView(update_all_icon, icon_lp)

                update_all_button = update_pill

                def on_click(*_):
                    nonlocal updating
                    if updating:
                        log("[KPM] update_all: already updating, skip")
                        return
                    updating = True
                    update_all_button.setEnabled(False)
                    update_all_button.setAlpha(0.5)
                    update_all_label.setText(_tr("updating_plugins"))

                    progress_dialog = None
                    try:
                        from org.telegram.ui.ActionBar import AlertDialog
                        progress_dialog = AlertDialog(ctx, 3)
                        progress_dialog.setCanCancel(False)
                        progress_dialog.show()
                    except Exception as pe:
                        log_error(pe, context="update_all progress dialog")

                    ids_to_update = list(self.plugin_ids)
                    log(f"[KPM] update_all: clicked, {len(ids_to_update)} plugins to update: {ids_to_update}")

                    def _dismiss_dlg():
                        if progress_dialog:
                            try:
                                run_on_ui_thread(lambda: progress_dialog.dismiss() if progress_dialog.isShowing() else None)
                            except Exception:
                                pass

                    def on_plugin_done(pid, ok):
                        try:
                            if ok:
                                if pid in self.plugin_ids:
                                    self.plugin_ids.remove(pid)
                                    log(f"[KPM] update_all: {pid} done, removing from list, left={len(self.plugin_ids)}")
                            else:
                                log(f"[KPM] update_all: {pid} failed, keeping in list")
                            if hasattr(update_all_label, "setText"):
                                update_all_label.setText(f"{_tr('update_plugins')} ({len(self.plugin_ids)})")
                            self.adapter.update(True)
                            if not self.plugin_ids:
                                nonlocal updating
                                updating = False
                                update_all_button.setEnabled(True)
                                update_all_button.setAlpha(1.0)
                                update_all_label.setText(f"{_tr('update_plugins')} ({len(self.plugin_ids)})")
                                _dismiss_dlg()
                                try:
                                    from .. import dismiss_all_md3
                                    dismiss_all_md3()
                                except Exception:
                                    pass
                        except Exception as e:
                            log_error(e, context="update_all on_plugin_done")

                    def on_done():
                        log("[KPM] update_all: on_done called")
                        def _finish():
                            nonlocal updating
                            updating = False
                            update_all_button.setEnabled(True)
                            update_all_button.setAlpha(1.0)
                            update_all_label.setText(f"{_tr('update_plugins')} ({len(self.plugin_ids)})")
                            _dismiss_dlg()
                            if self.plugin_ids:
                                self.adapter.update(True)
                            try:
                                from .. import dismiss_all_md3
                                dismiss_all_md3()
                            except Exception:
                                pass
                        run_on_ui_thread(_finish)
                        log("[KPM] update_all: adapter updated")

                    self.pl.update_selected_plugins(ids_to_update, on_done, on_plugin_done)

                update_all_button.setOnClickListener(OnClickListener(on_click))
                self.content_view.addView(update_all_button, LayoutHelper.createFrame(-2, -2, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, 0, 0, 28))
            except Exception:
                pass

        return self.content_view

    def fillItems(self, items, adapter):
        self.adapter = adapter
        self.fill_id += 1

        plugin_ids = list(self.plugin_ids)

        if self.ai_result_ids is not None:
            plugin_ids = [pid for pid in self.ai_result_ids if pid in self.plugin_ids]
        elif self.search_query:
            query = str(self.search_query)
            smart_search = bool(self.pl.get_setting("smart_search", True))
            if smart_search and len(query.strip()) >= 3 and getattr(self.pl, "_trigram_index", None):
                ranked = self.pl._trigram_search(query, allowed_ids=self.plugin_ids)
                if ranked:
                    plugin_ids = [pid for pid in ranked if pid in self.plugin_ids]
                else:
                    plugin_ids = []
            else:
                def match(pid):
                    name = self.display_names.get(pid, pid)
                    if isinstance(name, dict):
                        name = name.get("name", pid)
                    return query.lower() in str(name).lower() or query.lower() in pid.lower()
                plugin_ids = [pid for pid in self.plugin_ids if match(pid)]
        else:
            plugin_ids = list(self.plugin_ids)

        try:
            status_filter_raw = str(self.pl.get_setting("plugins_status_filter", "*") or "*")
            active_filters = set(k.strip() for k in status_filter_raw.split(",") if k.strip()) if status_filter_raw != "*" else {"*"}
            if "*" not in active_filters and active_filters:
                def _matches_status(pid):
                    try:
                        info = self.pl.plugins_list.get(pid)
                        if isinstance(info, dict):
                            st = info.get("status") or "plugin"
                            return st in active_filters
                    except Exception:
                        pass
                    return False
                plugin_ids = [pid for pid in plugin_ids if _matches_status(pid)]
        except Exception:
            pass

        try:
            mode = int(self.pl.get_setting("plugins_sort_mode", 0) or 0)
            if mode == 1:
                plugin_ids.sort(key=lambda p: str(self.display_names.get(p, p)).lower())
            elif mode == 2:
                plugin_ids.sort(key=lambda p: str(self.display_names.get(p, p)).lower(), reverse=True)
            elif mode == 3:
                def _author(pid):
                    try:
                        info = self.pl.plugins_list.get(pid)
                        if isinstance(info, dict):
                            return (info.get("author") or "").lower()
                    except Exception:
                        pass
                    return ""
                plugin_ids.sort(key=lambda p: (_author(p), str(self.display_names.get(p, p)).lower()))
            elif mode == 4:
                def _author2(pid):
                    try:
                        info = self.pl.plugins_list.get(pid)
                        if isinstance(info, dict):
                            return (info.get("author") or "").lower()
                    except Exception:
                        pass
                    return ""
                plugin_ids.sort(key=lambda p: (_author2(p), str(self.display_names.get(p, p)).lower()), reverse=True)
            elif mode == 5:
                def _status(pid):
                    try:
                        info = self.pl.plugins_list.get(pid)
                        if isinstance(info, dict):
                            return info.get("status", "plugin")
                    except Exception:
                        pass
                    return "plugin"
                plugin_ids.sort(key=lambda p: (_status_sort_key(_status(p)), str(self.display_names.get(p, p)).lower()))
        except Exception:
            pass

        try:
            if self.empty_view:
                self.empty_view.setVisibility(View.VISIBLE if len(plugin_ids) == 0 else View.GONE)
        except Exception:
            pass

        self._addItems(plugin_ids, items)
        run_on_ui_thread(lambda: self._update_scroll_control_state(), 50)

    def _addItems(self, plugin_ids, items):
        try:
            for pid in plugin_ids:
                if self.ftype == UPDATE:
                    name_data = self.display_names.get("d", {}) if isinstance(self.display_names, dict) and "d" in self.display_names else self.display_names
                    name = name_data.get(pid, pid) if isinstance(name_data, dict) else pid
                else:
                    name_data = self.display_names
                    name = name_data.get(pid, pid) if isinstance(name_data, dict) else pid

                p = Plugin(pid, name)
                p.setEngine(PLUGIN_ID)
                icon = None
                ver = None
                info = self.pl.plugins_list.get(pid) or self.pl.store.get_plugin_info(pid)
                if info:
                    icon = info.get("icon") if isinstance(info, dict) else None
                    ver = info.get("version") if isinstance(info, dict) else None
                if isinstance(icon, str):
                    p.setIcon(icon)
                if isinstance(ver, str):
                    p.setVersion(ver)
                try:
                    items.add(PluginCell.Factory.asPlugin(p, self.CellDelegate(pid, self)))
                except Exception:
                    try:
                        items.add(UItem.asPlugin(1, p, self.CellDelegate(pid, self)))
                    except Exception as e:
                        log_error(e, context=f"_addItems cell pid={pid}")
                space_item = UItem.asSpace(AndroidUtilities.dp(8))
                try:
                    space_item.transparent = True
                except Exception:
                    pass
                items.add(space_item)

            if self.ftype == UPDATE:
                space_bottom = UItem.asSpace(AndroidUtilities.dp(96))
            else:
                space_bottom = UItem.asSpace(AndroidUtilities.dp(56))
            try:
                space_bottom.transparent = True
            except Exception:
                pass
            items.add(space_bottom)

            log(f"[KPM] _addItems done: {len(plugin_ids)} plugins")
        except Exception as e:
            log_error(e, context="_addItems")

    def onClick(self, item, view, position, x, y):
        return False

    def onLongClick(self, item, view, position, x, y):
        return False

    def afterCreateView(self, view):
        pass

    def onFragmentCreate(self, *_):
        try:
            self.set_hook = self.pl.hook_method(
                PluginCell.getClass().getDeclaredMethod("set", Plugin, PluginCellDelegate),
                self.PluginCellHook(self))
        except Exception as e:
            log_error(e, context="PluginCell set hook")

    def onFragmentDestroy(self, *_):
        self.fill_id += 1
        self.ai_result_ids = None
        try:
            if self.set_hook:
                self.pl.unhook_method(self.set_hook)
        except Exception:
            pass
        try:
            if getattr(self, '_search_hook', None):
                self.pl.unhook_method(self._search_hook)
                self._search_hook = None
        except Exception:
            pass
        try:
            if self.content_view:
                parent = self.content_view.getParent()
                if parent and hasattr(parent, 'removeView'):
                    parent.removeView(self.content_view)
        except Exception:
            pass
        