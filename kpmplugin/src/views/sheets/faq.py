from android_utils import OnClickListener, run_on_ui_thread
from client_utils import get_last_fragment
from org.telegram.messenger import AndroidUtilities
from org.telegram.ui.ActionBar import Theme

from ...methods import _tr, log, log_error, open_link
from .guide import DIM_ALPHA, FAQ_URL, _accent, _close, _later, _page, _state, _turn, _watch

STICKERS = [
    ("Kangelcons_by_fStikBot", 5),
    ("OMGKAWAIIANGELGOD", 22),
    ("LostChocolateFlamingo_by_fStikBot", 8),
    ("LostChocolateFlamingo_by_fStikBot", 11),
    ("LostChocolateFlamingo_by_fStikBot", 17),
    ("LostChocolateFlamingo_by_fStikBot", 26),
    ("LostChocolateFlamingo_by_fStikBot", 12),
]
COUNT = len(STICKERS)

_faq = {"strip": None, "tabs": []}


def _chip(ctx, label, action):
    from android.graphics.drawable import GradientDrawable
    from android.view import Gravity
    from android.widget import TextView

    view = TextView(ctx)
    view.setText(label)
    view.setTextSize(13)
    view.setSingleLine(True)
    view.setGravity(Gravity.CENTER)
    view.setTypeface(AndroidUtilities.bold())
    view.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(7),
                    AndroidUtilities.dp(14), AndroidUtilities.dp(7))
    background = GradientDrawable()
    background.setShape(GradientDrawable.RECTANGLE)
    background.setCornerRadius(AndroidUtilities.dp(16))
    view.setBackground(background)
    view.setOnClickListener(OnClickListener(lambda v: action()))
    return view


def _paint(index):
    from java import jint

    for i, chip in enumerate(_faq["tabs"]):
        picked = i == index
        chip.getBackground().setColor(_accent() if picked else Theme.getColor(Theme.key_listSelector))
        chip.setTextColor(jint(-1) if picked else Theme.getColor(Theme.key_dialogTextBlack))


def _reveal(index):
    strip = _faq["strip"]
    tabs = _faq["tabs"]
    if strip is None or index >= len(tabs):
        return
    chip = tabs[index]
    x = chip.getLeft() - (strip.getWidth() - chip.getWidth()) // 2
    strip.smoothScrollTo(max(0, x), 0)


def _show(index):
    pages = _state.get("pages")
    if not pages or index == _state.get("at") or index >= len(pages):
        return
    _turn(pages, _state.get("at", 0), index)
    _state["at"] = index
    _paint(index)
    _reveal(index)
    log(f"[KPM] faq: tab {index + 1}")


def _buttons():
    return [
        (_tr("faq_forum"), False, lambda: open_link(FAQ_URL)),
        (_tr("guide_got_it"), True, _close),
    ]


def _open(pl, index):
    fragment = get_last_fragment()
    ctx = fragment.getParentActivity() if fragment else None
    if ctx is None:
        log("[KPM] faq: no activity")
        return False

    from android.view import Gravity, View
    from android.widget import FrameLayout, HorizontalScrollView, LinearLayout
    from androidx.core.widget import NestedScrollView
    from org.telegram.ui.ActionBar import BottomSheet
    from org.telegram.ui.Components import LayoutHelper

    _close()
    row = LinearLayout(ctx)
    row.setOrientation(LinearLayout.HORIZONTAL)
    strip = HorizontalScrollView(ctx)
    strip.setHorizontalScrollBarEnabled(False)
    strip.addView(row, LayoutHelper.createFrame(-2, -2, Gravity.LEFT))
    frame = FrameLayout(ctx)
    pages = []
    tabs = []
    for i in range(COUNT):
        page = _page(ctx, pl, STICKERS[i], _tr(f"faq_{i + 1}_q"), _tr(f"faq_{i + 1}_a"), _buttons(), None)
        frame.addView(page, LayoutHelper.createFrame(-1, -2, Gravity.TOP))
        if i != index:
            page.setVisibility(View.GONE)
        pages.append(page)
        chip = _chip(ctx, _tr(f"faq_{i + 1}_tab"), lambda i=i: _show(i))
        row.addView(chip, LayoutHelper.createLinear(
            -2, -2, Gravity.CENTER_VERTICAL, 16 if i == 0 else 8, 0, 16 if i == COUNT - 1 else 0, 0))
        tabs.append(chip)

    scroll = NestedScrollView(ctx)
    scroll.addView(frame, LayoutHelper.createFrame(-1, -2, Gravity.TOP))
    holder = FrameLayout(ctx)
    holder.addView(scroll, LayoutHelper.createFrame(-1, -2, Gravity.TOP))
    column = LinearLayout(ctx)
    column.setOrientation(LinearLayout.VERTICAL)
    column.addView(strip, LayoutHelper.createLinear(-1, -2, Gravity.TOP, 0, 16, 0, 0))
    column.addView(holder, LayoutHelper.createLinear(-1, -2, Gravity.TOP))
    builder = BottomSheet.Builder(ctx)
    builder.setCustomView(column)
    made = builder.create()
    try:
        made.setDimBehindAlpha(DIM_ALPHA)
    except Exception:
        pass
    _watch(made)
    made.show()
    _state["sheet"] = made
    _state["frame"] = frame
    _state["holder"] = holder
    _state["pages"] = pages
    _state["at"] = index
    _faq["strip"] = strip
    _faq["tabs"] = tabs
    _paint(index)
    _later(lambda: _reveal(index), 50)
    log(f"[KPM] faq: shown tab {index + 1}")
    return True


def open_faq(pl, index=0):
    def go():
        try:
            _open(pl, max(0, min(int(index), COUNT - 1)))
        except Exception as e:
            log_error(e, context="faq open")

    run_on_ui_thread(go)
