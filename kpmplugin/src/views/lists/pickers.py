import time
import traceback
import weakref
from android_utils import run_on_ui_thread, OnClickListener
from client_utils import get_last_fragment, run_on_queue
from hook_utils import find_class
from java import dynamic_proxy
from com.exteragram.messenger.plugins import Plugin
from com.exteragram.messenger.plugins.ui.components import PluginCell, PluginCellDelegate
from com.exteragram.messenger.plugins.ui.components.templates import UniversalFragment
from org.telegram.messenger import AndroidUtilities, Utilities
from org.telegram.messenger import R as R_tg
from org.telegram.ui.Components import LayoutHelper, UItem
from org.telegram.ui.ActionBar import Theme
from android.widget import FrameLayout, LinearLayout, TextView
from android.view import Gravity, View

from ...methods import log, log_error, _tr, _is_self_plugin


class _Callback2(dynamic_proxy(Utilities.Callback2)):
    def __init__(self, fn):
        super().__init__()
        self.fn = fn
    def run(self, *args):
        self.fn(*args)


class _Callback5(dynamic_proxy(Utilities.Callback5)):
    def __init__(self, fn):
        super().__init__()
        self.fn = fn
    def run(self, *args):
        self.fn(*args)


class _Callback5Return(dynamic_proxy(Utilities.Callback5Return)):
    def __init__(self, fn):
        super().__init__()
        self.fn = fn
    def run(self, *args):
        return self.fn(*args)


_pick_accent_color = None


def _pick_accent():
    global _pick_accent_color
    if _pick_accent_color is None:
        try:
            _pick_accent_color = Theme.getColor(Theme.key_featuredStickers_addButton)
        except Exception:
            try:
                _pick_accent_color = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText)
            except Exception:
                _pick_accent_color = 0xFF3399FF
    return _pick_accent_color


def _pick_to_jint(c):
    try:
        c = int(c)
    except Exception:
        return 0xFF3399FF - 0x100000000
    return c - 0x100000000 if c > 0x7FFFFFFF else c


def _paint_pick_card(card, selected):
    try:
        from android.graphics.drawable import GradientDrawable
        d = GradientDrawable()
        d.setShape(GradientDrawable.RECTANGLE)
        d.setCornerRadius(float(AndroidUtilities.dp(16)))
        if selected:
            accent = _pick_accent()
            d.setStroke(AndroidUtilities.dp(1.5), _pick_to_jint(accent))
            d.setColor(_pick_to_jint(accent) & 0x12FFFFFF | 0x18000000)
        else:
            try:
                d.setColor(_pick_to_jint(Theme.getColor(Theme.key_windowBackgroundWhite)))
            except Exception:
                d.setColor(_pick_to_jint(0xFFFFFFFF))
        card.setBackground(d)
    except Exception:
        pass


def _paint_pick_title(title_tv, selected):
    try:
        if selected:
            title_tv.setTextColor(_pick_to_jint(_pick_accent()))
        else:
            title_tv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
    except Exception:
        pass


def _find_by_tag(view, tag):
    try:
        if view is None:
            return None
        if view.getTag() == tag:
            return view
        for i in range(view.getChildCount()):
            found = _find_by_tag(view.getChildAt(i), tag)
            if found is not None:
                return found
    except Exception:
        pass
    return None


def _build_bottom_button(label, on_click):
    from android.widget import TextView
    from android.view import Gravity
    from android.graphics.drawable import GradientDrawable
    ctx = get_last_fragment().getContext()
    btn = TextView(ctx)
    btn.setText(label)
    btn.setTextSize(15)
    try:
        btn.setTypeface(AndroidUtilities.bold())
    except Exception:
        pass
    btn.setGravity(Gravity.CENTER)
    btn.setTextColor(-1)
    btn.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(13),
                   AndroidUtilities.dp(16), AndroidUtilities.dp(13))
    try:
        bg = GradientDrawable()
        bg.setShape(GradientDrawable.RECTANGLE)
        bg.setCornerRadius(float(AndroidUtilities.dp(14)))
        bg.setColor(_pick_to_jint(Theme.getColor(Theme.key_featuredStickers_addButton)))
        btn.setBackground(bg)
    except Exception:
        pass
    btn.setClickable(True)
    btn.setFocusable(True)
    btn.setOnClickListener(OnClickListener(lambda v: on_click()))
    return btn


def show_collection_picker(plugin, title, all_plugin_ids, initial_selected_ids, on_done, accept_label=None):
    try:
        UItem = find_class("org.telegram.ui.Components.UItem")
        UniversalRecyclerView = find_class("org.telegram.ui.Components.UniversalRecyclerView")
        Theme = find_class("org.telegram.ui.ActionBar.Theme")
        LayoutHelper = find_class("org.telegram.ui.Components.LayoutHelper")
        FrameLayout = find_class("android.widget.FrameLayout")

        fragment = get_last_fragment()
        if not fragment:
            return

        delegate = _CollectionPickerDelegate(plugin, title, all_plugin_ids, initial_selected_ids, on_done, accept_label)
        picker = UniversalFragment(delegate)
        fragment.presentFragment(picker)
        _setup_search(picker, delegate, plugin)
    except Exception as e:
        log_error(e, context="collection picker")


class _CollectionPickerDelegate(dynamic_proxy(UniversalFragment.UniversalFragmentDelegate)):
    def __init__(self, plugin, title, all_ids, selected_ids, on_done, accept_label=None):
        super().__init__()
        self.plugin = plugin
        self._title = title
        self.all_ids = all_ids
        self.selected = set(selected_ids)
        self.on_done = on_done
        self.accept_label = accept_label or _tr("export_button")
        self.adapter = None
        self.search_query = None
        self._check_ids = []
        self._meta = {}
        try:
            for pid, info in plugin.store.plugins_list.items():
                if isinstance(info, dict) and info:
                    self._meta[pid] = info
        except Exception:
            pass

    def getTitle(self):
        return self._title

    def onBackPressed(self):
        get_last_fragment().finishFragment()
        return True

    def beforeCreateView(self):
        Theme = find_class("org.telegram.ui.ActionBar.Theme")
        FrameLayout = find_class("android.widget.FrameLayout")
        LayoutHelper = find_class("org.telegram.ui.Components.LayoutHelper")
        UniversalRecyclerView = find_class("org.telegram.ui.Components.UniversalRecyclerView")
        Utilities = find_class("org.telegram.messenger.Utilities")
        self.content_view = FrameLayout(get_last_fragment().getContext())
        self.content_view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray))
        self.recycler = UniversalRecyclerView(
            get_last_fragment(),
            _Callback2(self.fillItems),
            _Callback5(self.onClick),
            None
        )
        self.recycler.setClipToPadding(False)
        self.recycler.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8))
        self.content_view.addView(self.recycler, LayoutHelper.createFrame(-1, -1, 51, 0, 0, 0, 82))
        self.accept_btn = self._build_accept_button()
        self.content_view.addView(self.accept_btn, LayoutHelper.createFrame(-1, 50, 80, 16, 0, 16, 16))
        return self.content_view

    def _build_accept_button(self):
        return _build_bottom_button(self.accept_label, self._accept)

    def _accept(self):
        try:
            selected = list(self.selected)
            if self.on_done:
                self.on_done(selected)
            try:
                get_last_fragment().finishFragment()
            except Exception:
                pass
        except Exception as e:
            log_error(e, context="collection accept")

    def fillItems(self, items, adapter):
        self.adapter = adapter
        filtered = self.all_ids
        if self.search_query:
            q = self.search_query.lower()
            filtered = [pid for pid in self.all_ids if q in self._display_name(pid).lower() or q in pid.lower()]
        UItem = find_class("org.telegram.ui.Components.UItem")
        self._check_ids = []
        for pid in sorted(filtered, key=lambda x: self._display_name(x).lower()):
            self._check_ids.append(pid)
            item = UItem.asCustom(self._build_row_view(pid))
            item.id = len(self._check_ids) - 1
            items.add(item)

    def onClick(self, item, view, position, x, y):
        try:
            pid = self._check_ids[item.id]
        except Exception:
            return
        self._toggle(pid, view)

    def _display_name(self, pid):
        meta = self._meta.get(pid)
        if isinstance(meta, dict) and meta.get('name'):
            return str(meta['name'])
        try:
            return self.plugin.get_plugin_display_name(pid)
        except Exception:
            return pid

    def _version(self, pid):
        meta = self._meta.get(pid)
        if isinstance(meta, dict) and meta.get('version'):
            return str(meta['version'])
        return ""

    def _icon(self, pid):
        meta = self._meta.get(pid)
        if isinstance(meta, dict):
            return str(meta.get('icon', ''))
        return ""

    def _build_row_view(self, pid):
        from android.widget import LinearLayout, TextView, FrameLayout
        from android.view import Gravity, View
        from org.telegram.ui.Components import BackupImageView, LayoutHelper

        ctx = get_last_fragment().getContext()
        is_on = pid in self.selected

        card = LinearLayout(ctx)
        card.setOrientation(LinearLayout.HORIZONTAL)
        card.setGravity(Gravity.CENTER_VERTICAL)
        card.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12),
                        AndroidUtilities.dp(12), AndroidUtilities.dp(12))
        card.setTag("pick_card")
        _paint_pick_card(card, is_on)

        icon_view = BackupImageView(ctx)
        icon_view.setRoundRadius(AndroidUtilities.dp(10))
        try:
            from ...inline_icons import InlineIconManager
            InlineIconManager.apply_icon_to_backup_image_view(
                icon_view, self._icon(pid), tag_prefix=f"kpm_import_{pid}", placeholder_size_dp=52
            )
        except Exception:
            icon_view.setVisibility(View.GONE)
        card.addView(icon_view, LayoutHelper.createLinear(52, 52, Gravity.CENTER_VERTICAL, 0, 0, 12, 0))

        text_col = LinearLayout(ctx)
        text_col.setOrientation(LinearLayout.VERTICAL)
        text_col.setGravity(Gravity.CENTER_VERTICAL)

        title_tv = TextView(ctx)
        title_tv.setText(self._display_name(pid))
        title_tv.setTextSize(17)
        title_tv.setTypeface(AndroidUtilities.bold())
        title_tv.setSingleLine(True)
        title_tv.setTag("pick_title")
        _paint_pick_title(title_tv, is_on)
        text_col.addView(title_tv, LayoutHelper.createLinear(-1, -2, Gravity.CENTER_VERTICAL))

        version = self._version(pid)
        if version:
            sub_tv = TextView(ctx)
            sub_tv.setText(version)
            sub_tv.setTextSize(15)
            sub_tv.setSingleLine(True)
            try:
                sub_tv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText))
            except Exception:
                pass
            text_col.addView(sub_tv, LayoutHelper.createLinear(-1, -2, Gravity.CENTER_VERTICAL))

        card.addView(text_col, LayoutHelper.createLinear(0, -2, 1.0, Gravity.CENTER_VERTICAL))

        delegate = self
        OnClickListener = find_class("android.view.View$OnClickListener")

        class RowClickListener(dynamic_proxy(OnClickListener)):
            def onClick(self, v):
                delegate._toggle(pid, v)

        try:
            card.setOnClickListener(RowClickListener())
        except Exception:
            pass

        wrapper = FrameLayout(ctx)
        wrapper.setPadding(0, 0, 0, AndroidUtilities.dp(8))
        wrapper.addView(card, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        return wrapper

    def _toggle(self, pid, view):
        try:
            if pid in self.selected:
                self.selected.discard(pid)
            else:
                self.selected.add(pid)
            is_on = pid in self.selected
            card = _find_by_tag(view, "pick_card")
            if card is not None:
                _paint_pick_card(card, is_on)
            title = _find_by_tag(view, "pick_title")
            if title is not None:
                _paint_pick_title(title, is_on)
        except Exception as e:
            log_error(e, context="import toggle")


def _setup_search(frag, delegate, plugin):
    try:
        item = frag.getActionBar().createMenu().addItem(0, R_tg.drawable.outline_header_search).setIsSearchField(True)
        search_field = item.getSearchField()
        TextWatcherCls = find_class("android.text.TextWatcher")

        class SearchWatcher(dynamic_proxy(TextWatcherCls)):
            def beforeTextChanged(self, s, start, count, after):
                pass

            def onTextChanged(self, s, start, before, count):
                try:
                    q = str(search_field.getText().toString())
                except Exception:
                    q = ""
                delegate.search_query = q.strip() or None
                if delegate.adapter:
                    delegate.adapter.update(True)

            def afterTextChanged(self, s):
                pass

        search_field.addTextChangedListener(SearchWatcher())
        log("[KPM] export search watcher attached")
    except Exception as e:
        log_error(e, context="export setup_search")
        _dump_error(traceback.format_exc())


class _ExportPickerDelegate(dynamic_proxy(UniversalFragment.UniversalFragmentDelegate)):
    CHUNK_SIZE = 25

    def __init__(self, plugin, installed_ids, store_ids, on_export):
        super().__init__()
        self.plugin = plugin
        self.store_ids = set(store_ids)
        self.on_export = on_export
        self.adapter = None
        self.search_query = None
        self.fill_id = 0
        self.selected = set(pid for pid in installed_ids if not _is_self_plugin(pid))
        self._visible_ids = []
        self._meta = {}
        self._icon_queue = []
        self._icon_busy = 0
        self._icon_max = 2
        try:
            for pid, info in plugin.store.plugins_list.items():
                if isinstance(info, dict) and info:
                    self._meta[pid] = info
        except Exception:
            pass
        self._sorted_installed = sorted(
            [pid for pid in installed_ids if pid in self.store_ids and not _is_self_plugin(pid)],
            key=lambda x: self._display_name(x).lower())
        self._sorted_store = sorted(
            [pid for pid in self.store_ids if pid not in installed_ids and not _is_self_plugin(pid)],
            key=lambda x: self._display_name(x).lower())
        log(f"[KPM] ExportPickerDelegate created: installed={len(self._sorted_installed)}, store={len(self._sorted_store)}")

    def getTitle(self):
        return _tr("export_collection")

    def onBackPressed(self):
        get_last_fragment().finishFragment()
        return True

    def beforeCreateView(self):
        log("[KPM] export beforeCreateView enter")
        Theme = find_class("org.telegram.ui.ActionBar.Theme")
        FrameLayout = find_class("android.widget.FrameLayout")
        LayoutHelper = find_class("org.telegram.ui.Components.LayoutHelper")
        UniversalRecyclerView = find_class("org.telegram.ui.Components.UniversalRecyclerView")
        self.content_view = FrameLayout(get_last_fragment().getContext())
        self.content_view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray))
        self.recycler = UniversalRecyclerView(
            get_last_fragment(),
            _Callback2(self.fillItems),
            _Callback5(self.onClick),
            _Callback5Return(self.onLongClick)
        )
        try:
            if hasattr(self.recycler, "adapter") and self.recycler.adapter and hasattr(self.recycler.adapter, "setApplyBackground"):
                self.recycler.adapter.setApplyBackground(False)
        except Exception:
            pass
        self.recycler.setClipToPadding(False)
        self.recycler.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8))
        self.content_view.addView(self.recycler, LayoutHelper.createFrame(-1, -1, 51, 0, 0, 0, 82))
        self.export_btn = self._build_export_button()
        self.content_view.addView(self.export_btn, LayoutHelper.createFrame(-1, 50, 80, 16, 0, 16, 16))
        log("[KPM] export beforeCreateView done")
        return self.content_view

    def _build_export_button(self):
        return _build_bottom_button(_tr("export_button"), self._do_export)

    def _do_export(self):
        try:
            selected = list(self.selected)
            if not selected:
                from ui.bulletin import BulletinHelper
                BulletinHelper.show_error(_tr("collection_plugins_empty"))
                return
            try:
                get_last_fragment().finishFragment()
            except Exception:
                pass
            if self.on_export:
                self.on_export(selected)
        except Exception as e:
            log_error(e, context="export click")

    def fillItems(self, items, adapter):
        try:
            self.adapter = adapter
            self.fill_id += 1
            self._visible_ids = []
            q = self.search_query.lower() if self.search_query else None

            def matches(pid):
                if not q:
                    return True
                return q in self._display_name(pid).lower() or q in pid.lower()

            installed = [pid for pid in self._sorted_installed if matches(pid)]
            store = [pid for pid in self._sorted_store if matches(pid)]
            plan = []
            if installed:
                plan.append(('header', 'installed'))
                plan.extend(('row', pid) for pid in installed)
            if store:
                plan.append(('header', 'store'))
                plan.extend(('row', pid) for pid in store)
            self._plan = plan
            self.fill_id += 1
            token = self.fill_id
            log(f"[KPM] export fillItems: plan={len(plan)} (installed={len(installed)}, store={len(store)}), token={token}")
            self._add_items(0, items, token)
        except Exception as e:
            tb = traceback.format_exc()
            log_error(e, context="export fillItems")
            _dump_error(tb)
            run_on_ui_thread(lambda: _show_error_dialog(tb))

    def _add_items(self, i, items, fid):
        try:
            if i != 0 and fid != self.fill_id:
                log(f"[KPM] export chunk {i} dropped (stale fill {fid}, current {self.fill_id})")
                return
            plan = self._plan
            from_ = i * self.CHUNK_SIZE
            len_ = len(plan)
            get_ = min(self.CHUNK_SIZE, len_ - from_)
            stop = from_ + get_
            if get_ <= 0:
                log(f"[KPM] export chunk {i}: get_={get_} (len={len_}), finishing")
                return
            if i == 0:
                rows = self._build_chunk_rows(from_, stop, plan)
                self._apply_chunk(0, from_, get_, stop, len_, items, fid, rows)
            else:
                run_on_queue(lambda: self._build_chunk_async(i, from_, get_, stop, len_, items, fid, plan))
        except Exception as e:
            tb = traceback.format_exc()
            log_error(e, context="export addItems")
            _dump_error(tb)
            run_on_ui_thread(lambda: _show_error_dialog(tb))

    def _build_chunk_async(self, i, from_, get_, stop, len_, items, fid, plan):
        try:
            rows = self._build_chunk_rows(from_, stop, plan)
            run_on_ui_thread(lambda: self._apply_chunk(i, from_, get_, stop, len_, items, fid, rows))
        except Exception as e:
            tb = traceback.format_exc()
            log_error(e, context="export chunk build")
            _dump_error(tb)
            run_on_ui_thread(lambda: _show_error_dialog(tb))

    def _build_chunk_rows(self, from_, stop, plan):
        rows = []
        for j in range(from_, stop):
            kind, val = plan[j]
            if kind == 'header':
                rows.append(('header', val))
            else:
                rows.append(('row', val, self._build_row_view(val)))
        return rows

    def _apply_chunk(self, i, from_, get_, stop, len_, items, fid, rows):
        try:
            if i != 0 and fid != self.fill_id:
                log(f"[KPM] export chunk {i} dropped at apply (stale fill {fid}, current {self.fill_id})")
                return
            UItem = find_class("org.telegram.ui.Components.UItem")
            for entry in rows:
                if entry[0] == 'header':
                    items.add(UItem.asHeader(_tr("collection_installed" if entry[1] == 'installed' else "collection_store")))
                else:
                    pid = entry[1]
                    self._visible_ids.append(pid)
                    item = UItem.asCustom(entry[2])
                    item.id = len(self._visible_ids) - 1
                    items.add(item)
            if i != 0:
                try:
                    self.adapter.notifyItemRangeInserted(from_, get_)
                except Exception as e:
                    log_error(e, context="export notifyItemRangeInserted")
            if stop >= len_:
                log(f"[KPM] export fill done: {len_} items (visible={len(self._visible_ids)})")
                return
            log(f"[KPM] export chunk {i}: applied {get_} (from {from_}), scheduling next")
            delay = 100 if i < 2 else 200
            run_on_ui_thread(lambda: self._add_items(i + 1, items, fid), delay)
        except Exception as e:
            tb = traceback.format_exc()
            log_error(e, context="export apply")
            _dump_error(tb)
            run_on_ui_thread(lambda: _show_error_dialog(tb))

    def _display_name(self, pid):
        meta = self._meta.get(pid)
        if isinstance(meta, dict) and meta.get('name'):
            return str(meta['name'])
        try:
            return self.plugin.get_plugin_display_name(pid)
        except Exception:
            return pid

    def _version(self, pid):
        meta = self._meta.get(pid)
        if isinstance(meta, dict) and meta.get('version'):
            return str(meta['version'])
        return ""

    def _icon(self, pid):
        meta = self._meta.get(pid)
        if isinstance(meta, dict):
            return str(meta.get('icon', ''))
        return ""

    def _queue_icon(self, image_view, sticker_set_name, sticker_index, cache_key):
        try:
            for q in self._icon_queue:
                if q[3] == cache_key:
                    return
            self._icon_queue.append((image_view, sticker_set_name, sticker_index, cache_key))
            run_on_queue(self._pump_icons)
        except Exception:
            pass

    def _pump_icons(self):
        try:
            while self._icon_queue and self._icon_busy < self._icon_max:
                item = self._icon_queue.pop(0)
                self._icon_busy += 1
                run_on_queue(lambda it=item: self._load_one_icon(*it))
            if self._icon_queue and self._icon_busy < self._icon_max:
                run_on_queue(self._pump_icons)
        except Exception:
            pass

    def _load_one_icon(self, image_view, sticker_set_name, sticker_index, cache_key):
        finished = []
        def finish():
            if finished:
                return
            finished.append(True)
            try:
                self._icon_busy -= 1
                run_on_queue(self._pump_icons)
            except Exception:
                pass
        try:
            _keep_alive = [image_view]
            _iv_ref = weakref.ref(image_view)

            def safe_set_image(loc, size, doc):
                try:
                    iv = _iv_ref()
                    if iv is not None:
                        run_on_ui_thread(lambda: iv.setImage(loc, size, None, 0, doc))
                except Exception:
                    pass
                try:
                    _keep_alive.clear()
                except Exception:
                    pass
                finish()

            try:
                if cache_key and cache_key in getattr(self.plugin, '_sticker_cache', {}):
                    cached = self.plugin._sticker_cache[cache_key]
                    safe_set_image(cached['location'], cached['size'], cached['document'])
                    return
            except Exception:
                pass

            current_account = 0
            try:
                fragment = get_last_fragment()
                if fragment:
                    current_account = fragment.getCurrentAccount()
            except Exception:
                pass

            from org.telegram.messenger import MediaDataController, ImageLocation
            media_controller = MediaDataController.getInstance(current_account)
            sticker_set = media_controller.getStickerSetByName(sticker_set_name)

            if sticker_set and sticker_set.documents and sticker_set.documents.size() > sticker_index:
                sticker_document = sticker_set.documents.get(sticker_index)
                image_location = ImageLocation.getForDocument(sticker_document)
                try:
                    self.plugin._cache_sticker(cache_key, image_location, sticker_document)
                except Exception:
                    pass
                safe_set_image(image_location, "150_150", sticker_document)
                return

            try:
                from org.telegram.tgnet import TLRPC
                from org.telegram.messenger import Utilities
                from java import dynamic_proxy
                input_set = TLRPC.TL_inputStickerSetShortName()
                input_set.short_name = sticker_set_name

                class CallbackProxy(dynamic_proxy(Utilities.Callback)):
                    def __init__(self, fn):
                        super().__init__()
                        self._fn = fn
                    def run(self, arg):
                        try:
                            self._fn(arg)
                        except Exception:
                            pass

                def on_set_response(result):
                    try:
                        if result and result.documents and result.documents.size() > sticker_index:
                            doc = result.documents.get(sticker_index)
                            img_loc = ImageLocation.getForDocument(doc)
                            try:
                                self.plugin._cache_sticker(cache_key, img_loc, doc)
                            except Exception:
                                pass
                            safe_set_image(img_loc, "150_150", doc)
                            return
                    except Exception:
                        pass
                    finish()

                media_controller.getStickerSet(input_set, None, False, CallbackProxy(on_set_response))
            except Exception:
                finish()
        except Exception:
            finish()

    def _build_row_view(self, pid):
        from org.telegram.ui.ActionBar import Theme
        from org.telegram.ui.Components import BackupImageView, LayoutHelper
        from android.widget import LinearLayout, TextView
        from android.view import Gravity, View

        ctx = get_last_fragment().getContext()

        card = LinearLayout(ctx)
        card.setOrientation(LinearLayout.HORIZONTAL)
        card.setGravity(Gravity.CENTER_VERTICAL)
        card.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(14),
                        AndroidUtilities.dp(12), AndroidUtilities.dp(14))
        card.setTag("export_card")

        _paint_pick_card(card, pid in self.selected)

        icon_view = BackupImageView(ctx)
        icon_view.setRoundRadius(AndroidUtilities.dp(10))
        icon = self._icon(pid)
        try:
            from ...inline_icons import InlineIconManager
            InlineIconManager.apply_icon_to_backup_image_view(
                icon_view, icon, tag_prefix=f"kpm_export_{pid}", placeholder_size_dp=52
            )
        except Exception:
            icon_view.setVisibility(View.GONE)
        card.addView(icon_view, LayoutHelper.createLinear(52, 52, Gravity.CENTER_VERTICAL, 0, 0, 12, 0))

        text_col = LinearLayout(ctx)
        text_col.setOrientation(LinearLayout.VERTICAL)
        text_col.setGravity(Gravity.CENTER_VERTICAL)

        title_tv = TextView(ctx)
        title_tv.setText(self._display_name(pid))
        title_tv.setTextSize(17)
        title_tv.setSingleLine(True)
        title_tv.setTag("export_title")
        _paint_pick_title(title_tv, pid in self.selected)
        text_col.addView(title_tv, LayoutHelper.createLinear(-1, -2, Gravity.CENTER_VERTICAL))

        version = self._version(pid)
        if version:
            sub_tv = TextView(ctx)
            sub_tv.setText(version)
            sub_tv.setTextSize(15)
            sub_tv.setSingleLine(True)
            try:
                sub_tv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText))
            except Exception:
                pass
            text_col.addView(sub_tv, LayoutHelper.createLinear(-1, -2, Gravity.CENTER_VERTICAL))

        card.addView(text_col, LayoutHelper.createLinear(0, -2, 1.0, Gravity.CENTER_VERTICAL))

        OnClickListener = find_class("android.view.View$OnClickListener")
        delegate = self

        class RowClickListener(dynamic_proxy(OnClickListener)):
            def onClick(self, v):
                delegate._toggle(pid, card)

        try:
            card.setOnClickListener(RowClickListener())
        except Exception:
            pass

        wrapper = FrameLayout(ctx)
        wrapper.setPadding(0, 0, 0, AndroidUtilities.dp(8))
        wrapper.addView(card, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        return wrapper

    def _toggle(self, pid, row):
        try:
            if pid in self.selected:
                self.selected.discard(pid)
            else:
                self.selected.add(pid)
            is_on = pid in self.selected
            card = _find_by_tag(row, "export_card")
            if card is not None:
                _paint_pick_card(card, is_on)
            title = _find_by_tag(row, "export_title")
            if title is not None:
                _paint_pick_title(title, is_on)
        except Exception as e:
            log_error(e, context="export toggle")

    def onClick(self, item, view, position, x, y):
        try:
            idx = item.id
            if idx < 0 or idx >= len(self._visible_ids):
                return
            pid = self._visible_ids[idx]
            self._toggle(pid, view)
        except Exception as e:
            log_error(e, context="export onClick")

    def onLongClick(self, item, view, position, x, y):
        return False


def _dump_error(text):
    try:
        import os
        from com.exteragram.messenger.plugins import PluginsController
        controller = PluginsController.getInstance()
        if controller:
            d = controller.getPluginsDir()
            path = os.path.join(str(d.getAbsolutePath()), "kpm_export_error.log")
            with open(path, "a") as f:
                f.write(f"\n===== {time.time()} =====\n{text}\n")
            log(f"[KPM] error dumped to {path}")
    except Exception as e:
        log_error(e, context="_dump_error")


def _show_error_dialog(text):
    try:
        fragment = get_last_fragment()
        ctx = fragment.getParentActivity() if fragment else None
        if not ctx:
            raise RuntimeError("no activity for error dialog")
        from android.app import AlertDialog
        from android.widget import ScrollView, TextView
        sv = ScrollView(ctx)
        tv = TextView(ctx)
        tv.setTextSize(12)
        tv.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(20),
                      AndroidUtilities.dp(20), AndroidUtilities.dp(20))
        tv.setText(text)
        sv.addView(tv)
        builder = AlertDialog.Builder(ctx)
        builder.setTitle("KPM export error")
        builder.setView(sv)
        builder.setPositiveButton("OK", lambda d, w: d.dismiss())
        builder.show()
    except Exception as e:
        log_error(e, context="_show_error_dialog")
        try:
            from ui.bulletin import BulletinHelper
            BulletinHelper.show_error(text)
        except Exception:
            pass


def show_export_metadata(plugin, plugin_ids):
    try:
        fragment = get_last_fragment()
        if not fragment:
            log("[KPM] export metadata: no fragment")
            return
        delegate = _ExportMetadataDelegate(plugin, plugin_ids)
        picker = UniversalFragment(delegate)
        fragment.presentFragment(picker)
        log(f"[KPM] export metadata presented: {len(plugin_ids)} plugins")
    except Exception as e:
        tb = traceback.format_exc()
        log_error(e, context="export metadata")


class _ExportMetadataDelegate(dynamic_proxy(UniversalFragment.UniversalFragmentDelegate)):
    FIELDS = (
        ("name", "collection_name", "msg_edit", False),
        ("author", "collection_author", "msg_contact", False),
        ("description", "collection_description", "msg_info", True),
    )

    def __init__(self, plugin, plugin_ids):
        super().__init__()
        self.plugin = plugin
        self.plugin_ids = list(plugin_ids)
        self.state = {"name": "", "author": "", "description": "", "icon": ""}
        self.adapter = None

    def getTitle(self):
        return _tr("export_collection")

    def onBackPressed(self):
        get_last_fragment().finishFragment()
        return True

    def beforeCreateView(self):
        Theme = find_class("org.telegram.ui.ActionBar.Theme")
        FrameLayout = find_class("android.widget.FrameLayout")
        LayoutHelper = find_class("org.telegram.ui.Components.LayoutHelper")
        UniversalRecyclerView = find_class("org.telegram.ui.Components.UniversalRecyclerView")
        self.content_view = FrameLayout(get_last_fragment().getContext())
        self.content_view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray))
        self.recycler = UniversalRecyclerView(
            get_last_fragment(),
            _Callback2(self.fillItems),
            None,
            None
        )
        try:
            if hasattr(self.recycler, "adapter") and self.recycler.adapter and hasattr(self.recycler.adapter, "setApplyBackground"):
                self.recycler.adapter.setApplyBackground(False)
        except Exception:
            pass
        self.recycler.setClipToPadding(False)
        self.recycler.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8))
        self.content_view.addView(self.recycler, LayoutHelper.createFrame(-1, -1, 51, 0, 0, 0, 82))
        self.export_btn = self._build_export_button()
        self.content_view.addView(self.export_btn, LayoutHelper.createFrame(-1, 50, 80, 16, 0, 16, 16))
        return self.content_view

    def _build_export_button(self):
        return _build_bottom_button(_tr("export_button"), self._do_export)

    def _do_export(self):
        try:
            st = self.state
            metadata = {
                "name": (st["name"] or "").strip() or "My Collection",
                "author": (st["author"] or "").strip(),
                "description": (st["description"] or "").strip(),
                "icon": st["icon"] or "",
            }
            from ...sbroka import export_collection
            export_collection(self.plugin, metadata, self.plugin_ids)
            try:
                get_last_fragment().finishFragment()
            except Exception:
                pass
        except Exception as e:
            log_error(e, context="export metadata done")

    def fillItems(self, items, adapter):
        try:
            self.adapter = adapter
            UItem = find_class("org.telegram.ui.Components.UItem")
            for field, title_key, icon_name, multiline in self.FIELDS:
                items.add(UItem.asCustom(self._build_field_row(field, title_key, icon_name, multiline)))
            items.add(UItem.asCustom(self._build_icon_row()))
        except Exception as e:
            log_error(e, context="export metadata fill")

    def _row_card(self):
        from android.widget import LinearLayout
        from android.view import Gravity
        from org.telegram.ui.Components import LayoutHelper
        ctx = get_last_fragment().getContext()
        card = LinearLayout(ctx)
        card.setOrientation(LinearLayout.HORIZONTAL)
        card.setGravity(Gravity.CENTER_VERTICAL)
        card.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12),
                        AndroidUtilities.dp(14), AndroidUtilities.dp(12))
        _paint_pick_card(card, False)
        wrapper = LinearLayout(ctx)
        wrapper.setOrientation(LinearLayout.VERTICAL)
        wrapper.setPadding(0, 0, 0, AndroidUtilities.dp(8))
        wrapper.addView(card, LayoutHelper.createLinear(-1, -2))
        return wrapper, card

    def _row_icon(self, card, icon_name):
        try:
            from android.widget import ImageView
            from android.view import Gravity
            from org.telegram.ui.Components import LayoutHelper
            from org.telegram.messenger import R as R_tg
            from androidx.core.content import ContextCompat
            res_id = getattr(R_tg.drawable, icon_name, 0)
            if not res_id:
                return
            ctx = card.getContext()
            drawable = ContextCompat.getDrawable(ctx, res_id)
            if drawable is None:
                return
            try:
                drawable.setTint(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon))
            except Exception:
                pass
            icon = ImageView(ctx)
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE)
            icon.setImageDrawable(drawable)
            card.addView(icon, LayoutHelper.createLinear(24, 24, Gravity.CENTER_VERTICAL, 0, 0, 14, 0))
        except Exception:
            pass

    def _build_field_row(self, field, title_key, icon_name, multiline):
        from android.widget import LinearLayout, TextView
        from android.view import Gravity
        from org.telegram.ui.Components import LayoutHelper
        from android.text import TextUtils

        title = _tr(title_key)
        value = (self.state.get(field) or "").strip()
        wrapper, card = self._row_card()
        self._row_icon(card, icon_name)

        texts = LinearLayout(card.getContext())
        texts.setOrientation(LinearLayout.VERTICAL)
        texts.setGravity(Gravity.CENTER_VERTICAL)

        title_tv = TextView(card.getContext())
        title_tv.setText(title)
        title_tv.setTextSize(16)
        try:
            title_tv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
        except Exception:
            pass
        texts.addView(title_tv, LayoutHelper.createLinear(-1, -2))

        value_tv = TextView(card.getContext())
        value_tv.setText(value if value else _tr(title_key))
        value_tv.setTextSize(13)
        value_tv.setSingleLine(True)
        value_tv.setEllipsize(TextUtils.TruncateAt.END)
        try:
            value_tv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText))
        except Exception:
            pass
        texts.addView(value_tv, LayoutHelper.createLinear(-1, -2, 0, 2, 0, 0))

        card.addView(texts, LayoutHelper.createLinear(0, -2, 1.0, Gravity.CENTER_VERTICAL))

        delegate = self
        OnClickListener = find_class("android.view.View$OnClickListener")

        class RowClickListener(dynamic_proxy(OnClickListener)):
            def onClick(self, v):
                delegate._open_editor(field, title, multiline)

        try:
            card.setOnClickListener(RowClickListener())
        except Exception:
            pass
        return wrapper

    def _build_icon_row(self):
        from android.widget import LinearLayout, TextView
        from android.view import Gravity, View
        from android.graphics.drawable import GradientDrawable
        from org.telegram.ui.Components import BackupImageView, LayoutHelper

        ctx = get_last_fragment().getContext()
        spec = self.state.get("icon") or ""
        log(f"[KPM] export icon row: spec={spec}")
        wrapper, card = self._row_card()

        preview = BackupImageView(ctx)
        preview.setRoundRadius(AndroidUtilities.dp(10))
        try:
            preview.setAspectFit(True)
        except Exception:
            pass
        if spec and "/" in spec:
            parts = spec.split("/")
            try:
                self.plugin._load_sticker_async(preview, parts[0], int(parts[1]),
                                                f"kpm_export_icon_{parts[0]}")
            except Exception:
                pass
        else:
            preview.setVisibility(View.GONE)
        card.addView(preview, LayoutHelper.createLinear(44, 44, Gravity.CENTER_VERTICAL, 0, 0, 14, 0))

        if not (spec and "/" in spec):
            letter = TextView(ctx)
            try:
                letter.setText((((self.state.get("name") or "").strip()[:1]) or "K").upper())
            except Exception:
                letter.setText("K")
            letter.setTextColor(-1)
            letter.setTextSize(18)
            try:
                letter.setTypeface(AndroidUtilities.bold())
            except Exception:
                pass
            letter.setGravity(Gravity.CENTER)
            try:
                letter_bg = GradientDrawable()
                letter_bg.setShape(GradientDrawable.RECTANGLE)
                letter_bg.setCornerRadius(float(AndroidUtilities.dp(10)))
                letter_bg.setColor(_pick_to_jint(Theme.getColor(Theme.key_featuredStickers_addButton)))
                letter.setBackground(letter_bg)
            except Exception:
                pass
            card.addView(letter, LayoutHelper.createLinear(44, 44, Gravity.CENTER_VERTICAL, 0, 0, 14, 0))

        texts = LinearLayout(ctx)
        texts.setOrientation(LinearLayout.VERTICAL)
        texts.setGravity(Gravity.CENTER_VERTICAL)

        title_tv = TextView(ctx)
        title_tv.setText(_tr("collection_icon"))
        title_tv.setTextSize(16)
        try:
            title_tv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
        except Exception:
            pass
        texts.addView(title_tv, LayoutHelper.createLinear(-1, -2))

        spec_tv = TextView(ctx)
        spec_tv.setText(spec if spec else _tr("collection_icon_hint"))
        spec_tv.setTextSize(13)
        spec_tv.setSingleLine(True)
        try:
            from android.text import TextUtils
            spec_tv.setEllipsize(TextUtils.TruncateAt.END)
        except Exception:
            pass
        try:
            spec_tv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText))
        except Exception:
            pass
        texts.addView(spec_tv, LayoutHelper.createLinear(-1, -2, 0, 2, 0, 0))

        card.addView(texts, LayoutHelper.createLinear(0, -2, 1.0, Gravity.CENTER_VERTICAL))

        delegate = self
        OnClickListener = find_class("android.view.View$OnClickListener")

        class IconClickListener(dynamic_proxy(OnClickListener)):
            def onClick(self, v):
                delegate._open_sticker_picker()

        try:
            card.setOnClickListener(IconClickListener())
        except Exception:
            pass
        return wrapper

    def _open_editor(self, field, title, multiline):
        try:
            from ui.alert import AlertDialogBuilder
            from android.widget import LinearLayout
            from org.telegram.ui.Components import LayoutHelper
            from ..sheets.sticker_picker import _make_field

            fragment = get_last_fragment()
            if not fragment:
                return
            ctx = fragment.getParentActivity()
            if not ctx:
                return

            builder = AlertDialogBuilder(ctx)
            builder.set_title(title)

            box = LinearLayout(ctx)
            box.setOrientation(LinearLayout.VERTICAL)
            box.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(8),
                           AndroidUtilities.dp(20), AndroidUtilities.dp(8))

            edit = _make_field(ctx, title, multiline)
            try:
                cur = self.state.get(field) or ""
                edit.setText(cur)
                edit.setSelection(len(cur))
            except Exception:
                pass
            box.addView(edit, LayoutHelper.createLinear(-1, -2))
            builder.set_view(box)

            delegate = self

            def on_ok(d, w):
                try:
                    delegate.state[field] = str(edit.getText())
                except Exception:
                    pass
                try:
                    d.dismiss()
                except Exception:
                    pass
                try:
                    if delegate.adapter:
                        delegate.adapter.update(True)
                except Exception:
                    pass

            builder.set_positive_button(_tr("ok_button"), on_ok)
            builder.set_negative_button(_tr("cancel"), lambda d, w: d.dismiss())
            builder.show()
        except Exception as e:
            log_error(e, context="export editor")

    def _open_sticker_picker(self):
        try:
            from ..sheets.sticker_picker import show_sticker_picker
            cur = self.state.get("icon") or ""
            pack = cur.split("/")[0] if "/" in cur else ""

            def on_pick(spec):
                self.state["icon"] = spec or ""
                try:
                    if self.adapter:
                        self.adapter.update(True)
                except Exception:
                    pass

            show_sticker_picker(self.plugin, pack, on_pick)
        except Exception as e:
            log_error(e, context="export icon pick")


def show_export_picker(plugin, installed_ids, store_ids, on_export):
    try:
        fragment = get_last_fragment()
        if not fragment:
            log("[KPM] export picker: no fragment")
            return
        delegate = _ExportPickerDelegate(plugin, installed_ids, store_ids, on_export)
        picker = UniversalFragment(delegate)
        fragment.presentFragment(picker)
        _setup_search(picker, delegate, plugin)
        log(f"[KPM] export picker presented: installed={len(delegate._sorted_installed)}, store={len(delegate._sorted_store)}")
    except Exception as e:
        tb = traceback.format_exc()
        log_error(e, context="export picker")
        _dump_error(tb)
        _show_error_dialog(tb)


def show_ignored_updates_picker(plugin, plugin_ids, metadata, initially_ignored):
    try:
        fragment = get_last_fragment()
        if not fragment:
            return
        delegate = _IgnoredUpdatesPickerDelegate(plugin, plugin_ids, metadata, initially_ignored)
        picker = UniversalFragment(delegate)
        fragment.presentFragment(picker)
    except Exception as e:
        log_error(e, context="ignored updates picker")


class _IgnoredUpdatesPickerDelegate(dynamic_proxy(UniversalFragment.UniversalFragmentDelegate)):
    def __init__(self, plugin, plugin_ids, metadata, initially_ignored):
        super().__init__()
        self.plugin = plugin
        self.plugin_ids = plugin_ids
        self.metadata = metadata
        self.selected = set(initially_ignored)
        self.adapter = None
        self._sorted_ids = []
        self._icon_queue = []
        self._icon_busy = 0
        self._icon_max = 3

    def getTitle(self):
        return _tr("ignored_updates")

    def onBackPressed(self):
        self._save_and_close()
        return True

    def beforeCreateView(self):
        Theme = find_class("org.telegram.ui.ActionBar.Theme")
        FrameLayout = find_class("android.widget.FrameLayout")
        LayoutHelper = find_class("org.telegram.ui.Components.LayoutHelper")
        UniversalRecyclerView = find_class("org.telegram.ui.Components.UniversalRecyclerView")
        self.content_view = FrameLayout(get_last_fragment().getContext())
        self.content_view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray))
        self.recycler = UniversalRecyclerView(
            get_last_fragment(),
            _Callback2(self.fillItems),
            None,
            None
        )
        try:
            if hasattr(self.recycler, "adapter") and self.recycler.adapter and hasattr(self.recycler.adapter, "setApplyBackground"):
                self.recycler.adapter.setApplyBackground(False)
        except Exception:
            pass
        self.recycler.setClipChildren(False)
        self.recycler.setClipToPadding(False)
        self.recycler.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(16))
        self.content_view.addView(self.recycler, LayoutHelper.createFrame(-1, -1))
        return self.content_view

    def fillItems(self, items, adapter):
        log(f"[KPM] ignored fillItems: {len(self.plugin_ids)} plugins")
        self.adapter = adapter
        self._sorted_ids = sorted(self.plugin_ids, key=lambda p: self._display_name(p).lower())
        UItem = find_class("org.telegram.ui.Components.UItem")
        items.add(UItem.asHeader(_tr("ignored_updates")))
        for pid in self._sorted_ids:
            row_view = self._build_row_view(pid)
            items.add(UItem.asCustom(row_view))
        log(f"[KPM] ignored fillItems done: {len(self._sorted_ids)} items added")

    def _display_name(self, pid):
        meta = self.metadata.get(pid)
        if isinstance(meta, dict) and meta.get('name'):
            return str(meta['name'])
        try:
            return self.plugin.get_plugin_display_name(pid)
        except Exception:
            return pid

    def _version(self, pid):
        meta = self.metadata.get(pid)
        if isinstance(meta, dict) and meta.get('version'):
            return str(meta['version'])
        return ""

    def _icon(self, pid):
        meta = self.metadata.get(pid)
        if isinstance(meta, dict):
            return str(meta.get('icon', ''))
        return ""

    def _queue_icon(self, image_view, sticker_set_name, sticker_index, cache_key):
        try:
            for q in self._icon_queue:
                if q[3] == cache_key:
                    return
            self._icon_queue.append((image_view, sticker_set_name, sticker_index, cache_key))
            run_on_queue(self._pump_icons)
        except Exception:
            pass

    def _pump_icons(self):
        try:
            while self._icon_queue and self._icon_busy < self._icon_max:
                item = self._icon_queue.pop(0)
                self._icon_busy += 1
                run_on_queue(lambda it=item: self._load_one_icon(*it))
            if self._icon_queue and self._icon_busy < self._icon_max:
                run_on_queue(self._pump_icons)
        except Exception:
            pass

    def _load_one_icon(self, image_view, sticker_set_name, sticker_index, cache_key):
        finished = []
        def finish():
            if finished:
                return
            finished.append(True)
            try:
                self._icon_busy -= 1
                run_on_queue(self._pump_icons)
            except Exception:
                pass
        try:
            _keep_alive = [image_view]
            _iv_ref = weakref.ref(image_view)

            def safe_set_image(loc, size, doc):
                try:
                    iv = _iv_ref()
                    if iv is not None:
                        run_on_ui_thread(lambda: iv.setImage(loc, size, None, 0, doc))
                except Exception:
                    pass
                try:
                    _keep_alive.clear()
                except Exception:
                    pass
                finish()

            try:
                if cache_key and cache_key in getattr(self.plugin, '_sticker_cache', {}):
                    cached = self.plugin._sticker_cache[cache_key]
                    safe_set_image(cached['location'], cached['size'], cached['document'])
                    return
            except Exception:
                pass

            current_account = 0
            try:
                fragment = get_last_fragment()
                if fragment:
                    current_account = fragment.getCurrentAccount()
            except Exception:
                pass

            from org.telegram.messenger import MediaDataController, ImageLocation
            media_controller = MediaDataController.getInstance(current_account)
            sticker_set = media_controller.getStickerSetByName(sticker_set_name)

            if sticker_set and sticker_set.documents and sticker_set.documents.size() > sticker_index:
                sticker_document = sticker_set.documents.get(sticker_index)
                image_location = ImageLocation.getForDocument(sticker_document)
                try:
                    self.plugin._cache_sticker(cache_key, image_location, sticker_document)
                except Exception:
                    pass
                safe_set_image(image_location, "150_150", sticker_document)
                return

            try:
                from org.telegram.tgnet import TLRPC
                from org.telegram.messenger import Utilities
                from java import dynamic_proxy
                input_set = TLRPC.TL_inputStickerSetShortName()
                input_set.short_name = sticker_set_name

                class CallbackProxy(dynamic_proxy(Utilities.Callback)):
                    def __init__(self, fn):
                        super().__init__()
                        self._fn = fn
                    def run(self, arg):
                        try:
                            self._fn(arg)
                        except Exception:
                            pass

                def on_set_response(result):
                    try:
                        if result and result.documents and result.documents.size() > sticker_index:
                            doc = result.documents.get(sticker_index)
                            img_loc = ImageLocation.getForDocument(doc)
                            try:
                                self.plugin._cache_sticker(cache_key, img_loc, doc)
                            except Exception:
                                pass
                            safe_set_image(img_loc, "150_150", doc)
                            return
                    except Exception:
                        pass
                    finish()

                media_controller.getStickerSet(input_set, None, False, CallbackProxy(on_set_response))
            except Exception:
                finish()
        except Exception:
            finish()

    def _build_row_view(self, pid):
        from org.telegram.ui.ActionBar import Theme
        from org.telegram.ui.Components import BackupImageView, LayoutHelper
        from android.widget import LinearLayout, TextView, FrameLayout
        from android.view import Gravity, View
        from android.text import TextUtils

        ctx = get_last_fragment().getContext()
        is_on = pid in self.selected

        card = LinearLayout(ctx)
        card.setOrientation(LinearLayout.HORIZONTAL)
        card.setGravity(Gravity.CENTER_VERTICAL)
        card.setClipChildren(False)
        card.setClipToPadding(False)
        card.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12),
                        AndroidUtilities.dp(12), AndroidUtilities.dp(12))
        card.setTag("pick_card")
        _paint_pick_card(card, is_on)

        icon_view = BackupImageView(ctx)
        icon_view.setRoundRadius(AndroidUtilities.dp(10))
        icon = self._icon(pid)
        try:
            from ...inline_icons import InlineIconManager
            InlineIconManager.apply_icon_to_backup_image_view(
                icon_view, icon, tag_prefix=f"kpm_ignored_{pid}", placeholder_size_dp=52
            )
        except Exception:
            icon_view.setVisibility(View.GONE)
        card.addView(icon_view, LayoutHelper.createLinear(52, 52, Gravity.CENTER_VERTICAL, 0, 0, 12, 0))

        text_col = LinearLayout(ctx)
        text_col.setOrientation(LinearLayout.VERTICAL)
        text_col.setGravity(Gravity.CENTER_VERTICAL)

        title_tv = TextView(ctx)
        title_tv.setText(self._display_name(pid))
        title_tv.setTextSize(17)
        title_tv.setTypeface(AndroidUtilities.bold())
        title_tv.setSingleLine(True)
        title_tv.setEllipsize(TextUtils.TruncateAt.END)
        title_tv.setTag("pick_title")
        _paint_pick_title(title_tv, is_on)
        text_col.addView(title_tv, LayoutHelper.createLinear(-1, -2, Gravity.CENTER_VERTICAL))

        version = self._version(pid)
        if version:
            sub_tv = TextView(ctx)
            sub_tv.setText(f"v{version}" if not version.startswith("v") else version)
            sub_tv.setTextSize(14)
            sub_tv.setSingleLine(True)
            sub_tv.setEllipsize(TextUtils.TruncateAt.END)
            try:
                sub_tv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText))
            except Exception:
                pass
            text_col.addView(sub_tv, LayoutHelper.createLinear(-1, -2, Gravity.CENTER_VERTICAL, 0, 2, 0, 0))

        card.addView(text_col, LayoutHelper.createLinear(0, -2, 1.0, Gravity.CENTER_VERTICAL))

        delegate = self
        OnClickListener = find_class("android.view.View$OnClickListener")

        class RowClickListener(dynamic_proxy(OnClickListener)):
            def onClick(self, v):
                delegate._toggle(pid, v)

        try:
            card.setOnClickListener(RowClickListener())
        except Exception:
            pass

        wrapper = FrameLayout(ctx)
        wrapper.setClipChildren(False)
        wrapper.setClipToPadding(False)
        wrapper.setPadding(AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14), AndroidUtilities.dp(8))
        wrapper.addView(card, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        return wrapper

    def _toggle(self, pid, view):
        try:
            if pid in self.selected:
                self.selected.remove(pid)
            else:
                self.selected.add(pid)
            is_on = pid in self.selected
            card = _find_by_tag(view, "pick_card")
            if card is not None:
                _paint_pick_card(card, is_on)
            title = _find_by_tag(view, "pick_title")
            if title is not None:
                _paint_pick_title(title, is_on)
            self.plugin._set_ignored_updates(list(self.selected))
            log(f"[KPM] ignored toggle {pid}: checked={is_on}")
        except Exception as e:
            log_error(e, context="ignored toggle")

    def _save_and_close(self):
        try:
            self.plugin._set_ignored_updates(list(self.selected))
            from ui.bulletin import BulletinHelper
            run_on_ui_thread(lambda: BulletinHelper.show_success(
                _tr("ignored_updates_saved").format(len(self.selected))
            ))
        except Exception as e:
            log_error(e, context="save ignored updates")
        get_last_fragment().finishFragment()