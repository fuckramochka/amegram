import traceback

from android_utils import OnClickListener, run_on_ui_thread
from client_utils import get_last_fragment
from org.telegram.messenger import AndroidUtilities
from org.telegram.ui.ActionBar import Theme, SimpleTextView

from ...methods import _get_faq_url, _parse_markdown, _tr, log, log_error

FAQ_URL = _get_faq_url()

DONE_KEY = "guide_seen"
FIRST_DELAY_MS = 1800
MAX_TRIES = 3
RETRY_DELAY_MS = 4000
SETTINGS_DELAY_MS = 450
PAGE_MS = 240
DIM_ALPHA = 60

STEPS = [
    ("Kangelcons_by_fStikBot", 5, "guide_s1_title", "guide_s1_text", None),
    ("OMGKAWAIIANGELGOD", 22, "guide_s2_title", "guide_s2_text", "plugin_management"),
    ("LostChocolateFlamingo_by_fStikBot", 41, "guide_ai_title", "guide_ai_text", "ai_search"),
    ("LostChocolateFlamingo_by_fStikBot", 8, "guide_s3_title", "guide_s3_text", "auto_updates"),
    ("LostChocolateFlamingo_by_fStikBot", 11, "guide_s4_title", "guide_s4_text", "security"),
    ("LostChocolateFlamingo_by_fStikBot", 12, "guide_s5_title", "guide_s5_text", "ui_settings"),
    ("LostChocolateFlamingo_by_fStikBot", 17, "guide_s6_title", "guide_s6_text", "help_section"),
    ("LostChocolateFlamingo_by_fStikBot", 26, "guide_s7_title", "guide_s7_text", None),
]

_state = {"spot": None, "spot_root": None, "at": -1,
          "forced": False, "done": False, "pl": None}
_runnable_refs = {}


def _text(key):
    return _tr(key)


def _later(action, delay_ms):
    try:
        from hook_utils import find_class
        from java import dynamic_proxy

        Runnable = find_class("java.lang.Runnable")

        class Later(dynamic_proxy(Runnable)):
            def run(self):
                try:
                    action()
                except Exception as e:
                    log_error(e, context="guide later runnable")
                finally:
                    _runnable_refs.pop(id(self), None)

        holder = Later()
        _runnable_refs[id(holder)] = holder
        AndroidUtilities.runOnUIThread(holder, delay_ms)
    except Exception as e:
        log_error(e, context="guide schedule")
        run_on_ui_thread(action)


def _close():
    _unlight()
    _state["at"] = -1


def _find_text(root, wanted):
    stack = [root]
    while stack:
        view = stack.pop()
        if view is None:
            continue
        try:
            text = view.getText()
        except Exception:
            text = None
        if text is not None and str(text) == wanted:
            return view
        try:
            count = view.getChildCount()
        except Exception:
            count = 0
        for i in range(count):
            try:
                stack.append(view.getChildAt(i))
            except Exception:
                pass
    return None


def _row_of(view, root):
    row = view
    for _ in range(4):
        try:
            parent = row.getParent()
        except Exception:
            return row
        if parent is None or parent == root:
            return row
        try:
            wide = parent.getWidth() >= root.getWidth() * 0.8
        except Exception:
            wide = False
        row = parent
        if wide:
            return row
    return row


def _unlight():
    spot = _state.get("spot")
    _state["spot"] = None
    _state["spot_root"] = None
    if spot is not None:
        try:
            spot.detach()
        except Exception as e:
            log_error(e, context="guide spot detach")


def _accent():
    from java import jint

    try:
        return Theme.getColor(Theme.key_featuredStickers_addButton)
    except Exception:
        return jint(-8630785)


def _overlay(pl):
    from .spotlight import attach_spot

    fragment = get_last_fragment()
    activity = fragment.getParentActivity() if fragment else None
    decor = None
    if activity is not None:
        try:
            decor = activity.getWindow().getDecorView()
        except Exception:
            decor = None
    if decor is None:
        return None
    spot = _state.get("spot")
    if spot is not None:
        try:
            if _state.get("spot_root") is not None and _state.get("spot_root").equals(decor):
                try:
                    spot.set_next(lambda: _on_tap(pl))
                    spot.set_watch(lambda: _recover(pl))
                except Exception:
                    pass
                return spot
        except Exception:
            pass
        try:
            spot.detach()
        except Exception:
            pass
        _state["spot"] = None
        _state["spot_root"] = None
    made = attach_spot(decor, _accent())
    if made is None:
        return None
    try:
        made.set_next(lambda: _on_tap(pl))
        made.set_watch(lambda: _recover(pl))
    except Exception as e:
        log_error(e, context="guide callbacks")
    _state["spot"] = made
    _state["spot_root"] = decor
    return made


def _find_row(section):
    try:
        if not section:
            return None
        fragment = get_last_fragment()
        root = fragment.getFragmentView() if fragment else None
        if root is None:
            return None
        found = _find_text(root, _tr(section))
        if found is None:
            return None
        return _row_of(found, root)
    except Exception:
        return None


def _on_tap(pl):
    try:
        _step(pl, _state.get("at", 0) + 1)
    except Exception as e:
        log_error(e, context="guide tap")


def _recover(pl, tries=0):
    try:
        if _state.get("done"):
            return
        if not _state.get("forced"):
            _state["pl"] = None
            try:
                _close()
            except Exception:
                pass
            return
        if tries == 0:
            if _state.get("recovering"):
                return
            _state["recovering"] = True
        if _ready():
            _state["recovering"] = False
            try:
                at = _state.get("at", -1)
                if at is None or int(at) < 0:
                    _offer(pl)
                else:
                    _step(pl, int(at))
            except Exception as e:
                log_error(e, context="guide recover step")
            return
        if tries >= 10:
            _state["recovering"] = False
            _state["forced"] = False
            _state["pl"] = None
            try:
                _close()
            except Exception:
                pass
            return
        try:
            pl.open_settings_page()
        except Exception as e:
            log_error(e, context="guide open settings page in recover")
        _later(lambda: _recover(pl, tries + 1), SETTINGS_DELAY_MS)
    except Exception as e:
        log_error(e, context="guide recover")


def _button(ctx, label, accent, action):
    from android.graphics.drawable import GradientDrawable
    from android.view import Gravity
    from android.widget import TextView
    from java import jint

    view = TextView(ctx)
    view.setText(label)
    view.setTextSize(14)
    view.setGravity(Gravity.CENTER)
    view.setTypeface(AndroidUtilities.bold())
    view.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12),
                    AndroidUtilities.dp(16), AndroidUtilities.dp(12))
    background = GradientDrawable()
    background.setShape(GradientDrawable.RECTANGLE)
    background.setCornerRadius(AndroidUtilities.dp(8))
    if accent:
        background.setColor(_accent())
        view.setTextColor(jint(-1))
    else:
        background.setColor(Theme.getColor(Theme.key_listSelector))
        view.setTextColor(Theme.getColor(Theme.key_dialogTextBlack))
    view.setBackground(background)
    view.setOnClickListener(OnClickListener(lambda v: action()))
    return view


def _page(ctx, pl, sticker, title_text, body_text, buttons, note=None):
    from android.view import Gravity
    from android.widget import LinearLayout, TextView
    from org.telegram.ui.Components import BackupImageView, LayoutHelper

    root = LinearLayout(ctx)
    root.setOrientation(LinearLayout.VERTICAL)

    image = BackupImageView(ctx)
    image.setRoundRadius(0)
    image.getImageReceiver().setCrossfadeWithOldImage(True)
    root.addView(image, LayoutHelper.createLinear(
        120, 120, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 20, 0, 0))
    try:
        pl._load_sticker_async(image, sticker[0], sticker[1], f"kpm_guide_{sticker[0]}_{sticker[1]}")
    except Exception:
        pass

    title = SimpleTextView(ctx)
    title.setTypeface(AndroidUtilities.bold())
    title.setTextSize(20)
    title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack))
    title.setText(title_text)
    title.setGravity(Gravity.CENTER)
    root.addView(title, LayoutHelper.createLinear(
        -2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 10, 22, 10, 0))

    if note:
        mark = TextView(ctx)
        mark.setText(note)
        mark.setTextSize(13)
        mark.setGravity(Gravity.CENTER)
        mark.setTextColor(Theme.getColor(Theme.key_dialogTextGray3))
        root.addView(mark, LayoutHelper.createLinear(
            -1, -2, Gravity.TOP, 24, 8, 24, 0))

    body = TextView(ctx)
    spannable, plain = _parse_markdown(
        body_text, Theme.getColor(Theme.key_windowBackgroundWhiteBlueText))
    body.setText(spannable if spannable else plain)
    if spannable:
        _linkify(body, spannable)
    body.setTextColor(Theme.getColor(Theme.key_dialogTextBlack))
    body.setTextSize(15)
    body.setLineSpacing(float(AndroidUtilities.dp(2)), 1.0)
    body.setGravity(Gravity.CENTER)
    root.addView(body, LayoutHelper.createLinear(
        -1, -2, Gravity.TOP, 24, 16, 24, 16))

    row = LinearLayout(ctx)
    row.setOrientation(LinearLayout.HORIZONTAL)
    for index, (label, accent, action) in enumerate(buttons):
        view = _button(ctx, label, accent, action)
        left = 0 if index == 0 else 8
        row.addView(view, LayoutHelper.createLinear(
            0, -2, 1.0, Gravity.CENTER_VERTICAL, left, 0, 0, 0))
    root.addView(row, LayoutHelper.createLinear(
        -1, -2, Gravity.CENTER_HORIZONTAL, 24, 0, 24, 20))

    return root


def _linkify(body, spannable):
    from android.text.method import LinkMovementMethod
    from android.text.style import URLSpan
    from android.view import MotionEvent
    from hook_utils import find_class
    from java import dynamic_proxy
    from org.telegram.ui.Components import URLSpanNoUnderline

    spans = spannable.getSpans(0, spannable.length(), URLSpan)
    if len(spans) == 0:
        return
    for span in spans:
        start = spannable.getSpanStart(span)
        end = spannable.getSpanEnd(span)
        flags = spannable.getSpanFlags(span)
        spannable.removeSpan(span)
        spannable.setSpan(URLSpanNoUnderline(span.getURL()), start, end, flags)

    class Tap(dynamic_proxy(find_class("android.view.View$OnTouchListener"))):
        def onTouch(self, view, event):
            try:
                if event.getAction() == MotionEvent.ACTION_UP and _link_at(view, event):
                    _close()
            except Exception as e:
                log_error(e, context="guide link")
            return False

    keeper = Tap()
    _runnable_refs[id(keeper)] = keeper
    body.setMovementMethod(LinkMovementMethod.getInstance())
    body.setHighlightColor(0)
    body.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText))
    body.setOnTouchListener(keeper)


def _link_at(view, event):
    from android.text.style import ClickableSpan

    layout = view.getLayout()
    if layout is None:
        return False
    x = event.getX() - view.getTotalPaddingLeft() + view.getScrollX()
    y = int(event.getY() - view.getTotalPaddingTop() + view.getScrollY())
    offset = layout.getOffsetForHorizontal(layout.getLineForVertical(y), x)
    return len(view.getText().getSpans(offset, offset, ClickableSpan)) > 0


def _finish(pl):
    _close()
    _state["forced"] = False
    _state["done"] = True
    _state["pl"] = None
    _state["recovering"] = False
    _mark_done(pl)


def _finish_faq(pl):
    from .faq import open_faq

    _finish(pl)
    open_faq(pl)


def _buttons(pl, index):
    if index == len(STEPS) - 1:
        return [
            (_text("guide_faq"), False, lambda: _finish_faq(pl)),
            (_text("guide_got_it"), True, lambda: _finish(pl)),
        ]
    if index == 0:
        return [
            (_text("guide_next"), True, lambda: _step(pl, index + 1)),
        ]
    return [
        (_text("guide_back"), False, lambda: _step(pl, index - 1)),
        (_text("guide_next"), True, lambda: _step(pl, index + 1)),
    ]


def _watch(sheet):
    try:
        from hook_utils import find_class
        from java import dynamic_proxy

        Listener = find_class("android.content.DialogInterface$OnDismissListener")

        class Gone(dynamic_proxy(Listener)):
            def onDismiss(self, dialog):
                try:
                    _state["frame"] = None
                    _state["pages"] = None
                    _state["at"] = -1
                    _unlight()
                except Exception:
                    pass
                finally:
                    _runnable_refs.pop(id(self), None)

        keeper = Gone()
        _runnable_refs[id(keeper)] = keeper
        sheet.setOnDismissListener(keeper)
    except Exception as e:
        log_error(e, context="guide watch")


def _turn(pages, was, now):
    if was == now or was < 0 or was >= len(pages) or now < 0 or now >= len(pages):
        return
    from android.view import View

    side = 1 if now > was else -1
    old = pages[was]
    page = pages[now]
    width = 0
    try:
        width = old.getWidth()
    except Exception:
        width = 0
    if width <= 0:
        width = AndroidUtilities.dp(320)
    try:
        page.setVisibility(View.VISIBLE)
        page.setTranslationX(float(side * width))
        page.setAlpha(0.0)
        page.animate().translationX(0.0).alpha(1.0).setDuration(PAGE_MS).start()
        old.animate().translationX(float(-side * width)).alpha(0.0) \
            .setDuration(PAGE_MS).start()
    except Exception as e:
        log_error(e, context="guide turn")
        return
    _later(lambda: _hide_page(old), PAGE_MS + 40)


def _hide_page(page):
    from android.view import View

    try:
        page.setVisibility(View.GONE)
        page.setTranslationX(0.0)
        page.setAlpha(1.0)
    except Exception:
        pass


def _offer(pl):
    try:
        spot = _overlay(pl)
        if spot is None:
            return False
        fragment = get_last_fragment()
        ctx = fragment.getParentActivity() if fragment else None
        if ctx is None:
            return False
        try:
            spot.aim(None)
        except Exception as e:
            log_error(e, context="guide offer aim")
        try:
            spot.show_card(_page(ctx, pl, ("Kangelcons_by_fStikBot", 5),
                                 _text("guide_offer_title"), _text("guide_offer_text"),
                                 [(_text("guide_yes"), True, lambda: _step(pl, 0))]))
        except Exception as e:
            log_error(e, context="guide offer card")
            return False
        _state["at"] = -1
        log("[KPM] guide offer shown")
        return True
    except Exception as e:
        log_error(e, context="guide offer")
        return False


def _step(pl, index):
    if index < 0 or index >= len(STEPS):
        _finish(pl)
        return
    try:
        spot = _overlay(pl)
        if spot is None:
            _finish(pl)
            return
        fragment = get_last_fragment()
        ctx = fragment.getParentActivity() if fragment else None
        if ctx is None:
            _finish(pl)
            return
        set_name, sticker_index, title_key, text_key, section = STEPS[index]
        row = _find_row(section)
        try:
            spot.aim(row)
        except Exception as e:
            log_error(e, context="guide aim")
        try:
            spot.show_card(_page(ctx, pl, (set_name, sticker_index), _text(title_key),
                                 _text(text_key), _buttons(pl, index),
                                 "%d/%d" % (index + 1, len(STEPS))))
        except Exception as e:
            log_error(e, context="guide card")
            _finish(pl)
            return
        _state["at"] = index
        log(f"[KPM] guide step {index + 1}/{len(STEPS)}")
    except Exception as e:
        log_error(e, context="guide step")
        _finish(pl)


def start_guide(pl):
    log("[KPM] guide: opened by hand")
    _state["forced"] = False
    _state["done"] = False
    _state["pl"] = pl

    def go():
        try:
            pl.open_settings_page()
        except Exception as e:
            log_error(e, context="guide start open settings")
        _later(lambda: _step(pl, 0), SETTINGS_DELAY_MS)

    run_on_ui_thread(go)


def _mark_done(pl):
    try:
        pl.set_setting(DONE_KEY, True, reload_settings=False)
    except Exception as e:
        log_error(e, context="guide mark done")


def _ready():
    try:
        fragment = get_last_fragment()
        return fragment is not None \
            and fragment.getFragmentView() is not None \
            and fragment.getParentActivity() is not None
    except Exception:
        return False


def _begin_forced(pl, tries=0):
    def go():
        try:
            pl.open_settings_page()
        except Exception as e:
            log_error(e, context="guide begin forced open settings")

        def after():
            if _ready():
                _offer(pl)
            elif tries < MAX_TRIES:
                log("[KPM] guide: nothing to show on, retrying")
                _later(lambda: _begin_forced(pl, tries + 1), RETRY_DELAY_MS)
            else:
                _state["forced"] = False
                _state["pl"] = None

        _later(after, SETTINGS_DELAY_MS)

    run_on_ui_thread(go)


def maybe_show_guide(pl):
    try:
        if pl.get_setting(DONE_KEY, False):
            log("[KPM] guide: already seen")
            return
        log("[KPM] guide: fresh install, scheduled")
        _state["forced"] = True
        _state["done"] = False
        _state["pl"] = pl
        _later(lambda: _begin_forced(pl), FIRST_DELAY_MS)
    except Exception as e:
        log_error(e, context="guide start")


def guide_settings_row(pl):
    from ui.settings import Text

    return Text(text=_text("guide_replay"), icon="msg_info",
                on_click=lambda _: start_guide(pl),
                link_alias="guide_replay")
