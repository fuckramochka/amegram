from android_utils import OnClickListener, run_on_ui_thread
from client_utils import get_last_fragment, run_on_queue

from ...methods import _tr, log, log_error


MAX_CELLS = 200
COLS = 4


def show_sticker_picker(pl, initial_pack="", on_pick=None):
    try:
        fragment = get_last_fragment()
        if not fragment:
            return
        ctx = fragment.getParentActivity()
        if not ctx:
            return
        try:
            account = fragment.getCurrentAccount()
        except Exception:
            account = 0

        from android.widget import LinearLayout, TextView
        from androidx.core.widget import NestedScrollView
        from org.telegram.ui.ActionBar import BottomSheet, Theme
        from org.telegram.ui.Components import LayoutHelper
        from org.telegram.messenger import AndroidUtilities, MediaDataController

        sheet = BottomSheet(ctx, True, fragment.getResourceProvider())
        sheet.setApplyBottomPadding(False)
        sheet.setApplyTopPadding(False)
        sheet.fixNavigationBar(Theme.getColor(Theme.key_dialogBackground))

        root = LinearLayout(ctx)
        root.setOrientation(LinearLayout.VERTICAL)
        root.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(8),
                        AndroidUtilities.dp(20), AndroidUtilities.dp(8))

        header = TextView(ctx)
        header.setText(_tr("collection_pick_sticker"))
        header.setTypeface(AndroidUtilities.bold())
        header.setTextSize(18)
        header.setTextColor(Theme.getColor(Theme.key_dialogTextBlack))
        header.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(8))
        root.addView(header, LayoutHelper.createLinear(-1, -2))

        stage = LinearLayout(ctx)
        stage.setOrientation(LinearLayout.VERTICAL)

        sv = NestedScrollView(ctx)
        sv.addView(stage)
        root.addView(sv, LayoutHelper.createLinear(-1, 0, 1.0))

        no_icon_row = TextView(ctx)
        no_icon_row.setText(_tr("collection_no_icon"))
        no_icon_row.setTextSize(15)
        try:
            no_icon_row.setTypeface(AndroidUtilities.bold())
        except Exception:
            pass
        no_icon_row.setGravity(17)
        try:
            no_icon_row.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText))
        except Exception:
            pass
        no_icon_row.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12),
                               AndroidUtilities.dp(16), AndroidUtilities.dp(12))
        no_icon_row.setOnClickListener(OnClickListener(lambda v: _choose(sheet, on_pick, "")))
        root.addView(no_icon_row, LayoutHelper.createLinear(-1, -2, 17, 0, 8, 0, 0))

        cancel_btn = TextView(ctx)
        cancel_btn.setText(_tr("cancel"))
        cancel_btn.setTextSize(14)
        cancel_btn.setGravity(17)
        try:
            cancel_btn.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText))
        except Exception:
            pass
        cancel_btn.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10),
                              AndroidUtilities.dp(16), AndroidUtilities.dp(10))
        cancel_btn.setOnClickListener(OnClickListener(lambda v: sheet.dismiss()))
        root.addView(cancel_btn, LayoutHelper.createLinear(-1, -2, 17, 0, 0, 0, 8))

        state = {"sheet": sheet, "stage": stage, "on_pick": on_pick, "account": account}

        packs = _installed_packs(account)
        _show_packs(pl, state, packs, initial_pack or "")

        sheet.setCustomView(root)
        sheet.show()
    except Exception as e:
        log_error(e, context="sticker picker show")


def _choose(sheet, on_pick, spec):
    log(f"[KPM] sticker picked: spec={spec}")
    try:
        sheet.dismiss()
    except Exception:
        pass
    try:
        if on_pick:
            on_pick(spec)
    except Exception as e:
        log_error(e, context="sticker pick callback")


def _installed_packs(account):
    packs = []
    try:
        from org.telegram.messenger import MediaDataController
        mdc = MediaDataController.getInstance(account)
        raw = mdc.getStickerSets(0)
        if raw is None:
            return packs
        for i in range(raw.size()):
            try:
                ss = raw.get(i)
                st = ss.set
                docs = ss.documents
                if st is None or docs is None or docs.size() <= 0:
                    continue
                packs.append({
                    "title": str(st.title or st.short_name),
                    "short_name": str(st.short_name),
                    "docs": [docs.get(j) for j in range(docs.size())],
                })
            except Exception:
                continue
    except Exception as e:
        log_error(e, context="installed sticker packs")
    return packs


def _clear_stage(state):
    try:
        state["stage"].removeAllViews()
    except Exception:
        pass


def _show_packs(pl, state, packs, initial_pack):
    try:
        from android.widget import LinearLayout, TextView
        from android.view import Gravity
        from org.telegram.ui.ActionBar import Theme
        from org.telegram.ui.Components import BackupImageView, LayoutHelper
        from org.telegram.messenger import AndroidUtilities, ImageLocation
        from android_utils import OnClickListener

        _clear_stage(state)
        stage = state["stage"]
        sheet = state["sheet"]
        ctx = stage.getContext()

        jumped = False
        if initial_pack:
            for p in packs:
                if p["short_name"] == initial_pack:
                    _show_grid(state, p["short_name"], p["docs"])
                    jumped = True
                    break
        if jumped:
            return

        for p in packs:
            row = LinearLayout(ctx)
            row.setOrientation(LinearLayout.HORIZONTAL)
            row.setGravity(Gravity.CENTER_VERTICAL)
            try:
                row.setBackground(Theme.getSelectorDrawable(False))
            except Exception:
                pass
            row.setClickable(True)
            row.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(10),
                           AndroidUtilities.dp(4), AndroidUtilities.dp(10))

            cover = BackupImageView(ctx)
            cover.setRoundRadius(AndroidUtilities.dp(8))
            try:
                cover.setAspectFit(True)
            except Exception:
                pass
            try:
                cover.setImage(ImageLocation.getForDocument(p["docs"][0]), "100_100", None, p["docs"][0])
            except Exception:
                pass
            row.addView(cover, LayoutHelper.createLinear(40, 40, Gravity.CENTER_VERTICAL, 0, 0, 14, 0))

            texts = LinearLayout(ctx)
            texts.setOrientation(LinearLayout.VERTICAL)

            tv = TextView(ctx)
            tv.setText(p["title"])
            tv.setTextSize(16)
            tv.setTextColor(Theme.getColor(Theme.key_dialogTextBlack))
            texts.addView(tv, LayoutHelper.createLinear(-1, -2))

            sub = TextView(ctx)
            sub.setText("@" + p["short_name"])
            sub.setTextSize(13)
            try:
                sub.setTextColor(Theme.getColor(Theme.key_dialogTextGray2))
            except Exception:
                pass
            texts.addView(sub, LayoutHelper.createLinear(-1, -2, 0, 2, 0, 0))

            row.addView(texts, LayoutHelper.createLinear(0, -2, 1.0, Gravity.CENTER_VERTICAL, 0, 0, 0, 0))

            def make_click(pack=p):
                def _click(v):
                    _show_grid(state, pack["short_name"], pack["docs"])
                return _click

            row.setOnClickListener(OnClickListener(make_click()))
            stage.addView(row, LayoutHelper.createLinear(-1, -2))

        manual_row = LinearLayout(ctx)
        manual_row.setOrientation(LinearLayout.HORIZONTAL)
        manual_row.setGravity(Gravity.CENTER_VERTICAL)
        stage.addView(manual_row, LayoutHelper.createLinear(-1, -2, 0, 8, 0, 0))

        pack_input = _make_field(ctx, _tr("collection_pack_hint"))
        manual_row.addView(pack_input, LayoutHelper.createLinear(0, -2, 1.0, Gravity.CENTER_VERTICAL, 0, 0, 8, 0))

        load_btn = TextView(ctx)
        load_btn.setText(_tr("collection_pack_load"))
        load_btn.setTextColor(-1)
        load_btn.setTextSize(15)
        load_btn.setTypeface(AndroidUtilities.bold())
        load_btn.setGravity(Gravity.CENTER)
        load_btn.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12),
                            AndroidUtilities.dp(16), AndroidUtilities.dp(12))
        try:
            load_btn.setBackground(Theme.createRoundRectDrawable(
                AndroidUtilities.dp(12), Theme.getColor(Theme.key_featuredStickers_addButton)))
        except Exception:
            pass
        manual_row.addView(load_btn, LayoutHelper.createLinear(-2, -2, Gravity.CENTER_VERTICAL))

        def _do_manual():
            try:
                pack = str(pack_input.getText()).strip()
            except Exception:
                pack = ""
            if pack:
                _load_pack_remote(state, pack)

        load_btn.setOnClickListener(OnClickListener(lambda v: _do_manual()))
    except Exception as e:
        log_error(e, context="sticker packs view")


def _show_grid(state, pack_name, docs):
    try:
        from android.widget import LinearLayout, TextView, GridLayout
        from android.view import Gravity
        from org.telegram.ui.ActionBar import Theme
        from org.telegram.ui.Components import BackupImageView, LayoutHelper
        from org.telegram.messenger import AndroidUtilities, ImageLocation
        from android_utils import OnClickListener

        _clear_stage(state)
        stage = state["stage"]
        sheet = state["sheet"]
        ctx = stage.getContext()

        back = TextView(ctx)
        back.setText("‹ " + _tr("guide_back"))
        back.setTextSize(15)
        back.setTypeface(AndroidUtilities.bold())
        try:
            back.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText))
        except Exception:
            pass
        back.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(8),
                        AndroidUtilities.dp(4), AndroidUtilities.dp(8))

        def _go_back(v):
            try:
                packs = _installed_packs(state["account"])
            except Exception:
                packs = []
            _show_packs(None, state, packs, "")

        back.setOnClickListener(OnClickListener(_go_back))
        stage.addView(back, LayoutHelper.createLinear(-1, -2, 0, 0, 0, 4))

        if not docs:
            empty = TextView(ctx)
            empty.setText(_tr("collection_pack_not_found"))
            empty.setTextSize(14)
            empty.setGravity(Gravity.CENTER)
            try:
                empty.setTextColor(Theme.getColor(Theme.key_dialogTextGray2))
            except Exception:
                pass
            empty.setPadding(0, AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16))
            stage.addView(empty, LayoutHelper.createLinear(-1, -2))
            return

        grid = GridLayout(ctx)
        grid.setColumnCount(COLS)
        stage.addView(grid, LayoutHelper.createLinear(-2, -2, Gravity.CENTER_HORIZONTAL))

        dm = ctx.getResources().getDisplayMetrics()
        try:
            screen_dp = dm.widthPixels / float(dm.density)
        except Exception:
            screen_dp = 360
        cell_dp = max(56, int((screen_dp - 48 - 8 * COLS) / COLS))
        cell = AndroidUtilities.dp(float(cell_dp))
        margin = AndroidUtilities.dp(4)
        shown = 0
        for idx, doc in enumerate(docs):
            if shown >= MAX_CELLS:
                break
            try:
                iv = BackupImageView(ctx)
                iv.setRoundRadius(AndroidUtilities.dp(10))
                try:
                    iv.setAspectFit(True)
                except Exception:
                    pass
                try:
                    iv.setImage(ImageLocation.getForDocument(doc), "100_100", None, doc)
                except Exception:
                    continue
                spec = f"{pack_name}/{idx}"

                def make_click(s=spec):
                    def _click(v):
                        _choose(sheet, state.get("on_pick"), s)
                    return _click

                iv.setOnClickListener(OnClickListener(make_click()))
                params = GridLayout.LayoutParams(
                    GridLayout.spec(shown // COLS), GridLayout.spec(shown % COLS))
                params.width = cell
                params.height = cell
                params.setMargins(margin, margin, margin, margin)
                params.setGravity(Gravity.CENTER)
                grid.addView(iv, params)
                shown += 1
            except Exception:
                continue
    except Exception as e:
        log_error(e, context="sticker grid view")


def _load_pack_remote(state, pack_name):
    def work():
        try:
            from org.telegram.messenger import MediaDataController
            from org.telegram.tgnet import TLRPC
            from org.telegram.messenger import Utilities
            from java import dynamic_proxy

            mdc = MediaDataController.getInstance(state["account"])
            try:
                ss = mdc.getStickerSetByName(pack_name)
                if ss is not None and ss.documents is not None and ss.documents.size() > 0:
                    docs = [ss.documents.get(i) for i in range(ss.documents.size())]
                    run_on_ui_thread(lambda: _show_grid(state, pack_name, docs))
                    return
            except Exception:
                pass

            input_set = TLRPC.TL_inputStickerSetShortName()
            input_set.short_name = pack_name

            class CallbackProxy(dynamic_proxy(Utilities.Callback)):
                def __init__(self, fn):
                    super().__init__()
                    self._fn = fn

                def run(self, arg):
                    try:
                        self._fn(arg)
                    except Exception:
                        pass

            def on_result(res):
                try:
                    if res is not None and res.documents is not None and res.documents.size() > 0:
                        docs2 = [res.documents.get(i) for i in range(res.documents.size())]
                        run_on_ui_thread(lambda: _show_grid(state, pack_name, docs2))
                    else:
                        run_on_ui_thread(lambda: _show_grid(state, pack_name, []))
                except Exception:
                    run_on_ui_thread(lambda: _show_grid(state, pack_name, []))

            mdc.getStickerSet(input_set, None, False, CallbackProxy(on_result))
        except Exception as e:
            log_error(e, context="sticker pack load remote")
            run_on_ui_thread(lambda: _show_grid(state, pack_name, []))

    run_on_queue(work)


def _make_field(ctx, hint, multiline=False):
    from android.widget import EditText
    from android.text import InputType
    from org.telegram.ui.ActionBar import Theme
    from org.telegram.messenger import AndroidUtilities

    try:
        from org.telegram.ui.Components import EditTextBoldCursor
        edit = EditTextBoldCursor(ctx)
    except Exception:
        edit = EditText(ctx)
    try:
        edit.setTextColor(Theme.getColor(Theme.key_dialogTextBlack))
    except Exception:
        pass
    try:
        edit.setHintTextColor(Theme.getColor(Theme.key_groupcreate_hintText))
    except Exception:
        try:
            edit.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText))
        except Exception:
            pass
    try:
        edit.setBackground(Theme.createEditTextDrawable(ctx, True))
    except Exception:
        pass
    try:
        if hasattr(edit, "setCursorColor"):
            edit.setCursorColor(Theme.getColor(Theme.key_dialogInputFieldActivated))
            edit.setCursorSize(AndroidUtilities.dp(20))
            edit.setCursorWidth(1.5)
    except Exception:
        pass
    try:
        edit.setTextSize(16)
    except Exception:
        pass
    try:
        pad_h = AndroidUtilities.dp(12)
        pad_v = AndroidUtilities.dp(10)
        edit.setPadding(pad_h, pad_v, pad_h, pad_v)
    except Exception:
        pass
    try:
        edit.setHint(hint)
        if multiline:
            edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE)
            edit.setSingleLine(False)
            edit.setMinLines(2)
        else:
            edit.setInputType(InputType.TYPE_CLASS_TEXT)
            edit.setSingleLine(True)
    except Exception:
        pass
    return edit
