import math

from ...methods import log, log_error

DIM_ALPHA = 204
PAD_DP = 14
CORNER_MAX_DP = 22
RING_DP = 2.5
WAVE_DP = 1.5
WAVE_GROW_DP = 26
WAVE_PERIOD = 1900
WAVE_COUNT = 2
WAVE_ALPHA = 150
REVEAL_MS = 420
TRAVEL_MS = 350
CARD_MARGIN_DP = 18
CARD_MAX_DP = 320
CARD_IN_MS = 300
CARD_SHIFT_DP = 16
ARROW_W_DP = 40
ARROW_H_DP = 24
ARROW_BAR_DP = 20
ARROW_THICK_DP = 3
ARROW_BOB_DP = 3
ARROW_GAP_DP = 2
RING_POP = 1.18
WATCH_MS = 800


def _dp(v):
    try:
        from org.telegram.messenger import AndroidUtilities
        return AndroidUtilities.dp(float(v))
    except Exception:
        return int(v)


def _offset(view, root):
    x = 0
    y = 0
    current = view
    for _ in range(12):
        if current is None or current.equals(root):
            break
        x += current.getLeft()
        y += current.getTop()
        parent = current.getParent()
        if parent is None or parent.equals(root):
            break
        try:
            x -= parent.getScrollX()
            y -= parent.getScrollY()
        except Exception:
            break
        current = parent
    return x, y


def _settings_alive():
    try:
        from client_utils import get_last_fragment
        fragment = get_last_fragment()
        view = fragment.getFragmentView() if fragment else None
        return view is not None and view.isShown()
    except Exception:
        return False


class Spot:
    def __init__(self):
        self._root = None
        self._box = None
        self._dims = []
        self._ring = None
        self._ring_bg = None
        self._waves = []
        self._arrow = None
        self._arrow_bars = None
        self._card = None
        self._card_page = None
        self._placed_rect = None
        self._rect = None
        self._from = None
        self._to = None
        self._ring_scale = 1.0
        self._dim_alpha = 0.0
        self._revealed = False
        self._gliding = False
        self._centered = True
        self._below = True
        self._row = None
        self._accent = 0
        self._next_cb = None
        self._watch_cb = None
        self._watch_last = 0
        self._waves0 = 0
        self._glide_anim = None
        self._wave_anim = None
        self._gone = False

    def set_next(self, cb):
        self._next_cb = cb

    def set_watch(self, cb):
        self._watch_cb = cb

    def aim(self, row):
        try:
            target = self._target(row)
            if target is None:
                self._centered = True
                self._rect = None
                self._revealed = True
                self._dim_alpha = float(DIM_ALPHA)
                self._apply()
                return True
            self._centered = False
            self._row = row
            self._to = target
            if self._rect is None:
                self._from = target
                self._rect = target
            else:
                self._from = self._rect
            self._ring_scale = RING_POP
            self._glide(REVEAL_MS if not self._revealed else TRAVEL_MS)
            return True
        except Exception as e:
            log_error(e, context="spot aim")
            return False

    def detach(self):
        self._gone = True
        for key in ("_glide_anim", "_wave_anim"):
            try:
                held = getattr(self, key)
                if held is not None:
                    held[0].cancel()
            except Exception:
                pass
            try:
                setattr(self, key, None)
            except Exception:
                pass
        self._next_cb = None
        self._watch_cb = None
        try:
            box = self._box
            self._box = None
            if box is not None:
                parent = box.getParent()
                if parent is not None:
                    parent.removeView(box)
        except Exception:
            pass
        self._row = None
        self._root = None
        self._card = None

    def show_card(self, card):
        try:
            self._drop_card()
        except Exception:
            pass
        if card is None:
            return
        try:
            from android.view import ViewGroup
            from android.widget import FrameLayout
            from androidx.core.widget import NestedScrollView

            scroll = NestedScrollView(self._box.getContext())
            scroll.addView(card, FrameLayout.LayoutParams(-1, -2))
            _card_bg(scroll)
            try:
                scroll.setClipToOutline(True)
            except Exception:
                pass
            self._box.addView(scroll, ViewGroup.LayoutParams(-1, -2))
            self._card = scroll
            self._card_page = card
            self._place_card()
            try:
                below = self._below and not self._centered
                shift = float(_dp(CARD_SHIFT_DP)) * (1.0 if below else -1.0)
                scroll.setAlpha(0.0)
                scroll.setTranslationY(shift)
                scroll.animate().alpha(1.0).translationY(0.0) \
                    .setDuration(CARD_IN_MS).start()
            except Exception:
                pass
        except Exception as e:
            log_error(e, context="spot card")

    def _drop_card(self):
        card = self._card
        self._card = None
        self._card_page = None
        self._placed_rect = None
        if card is not None:
            try:
                self._box.removeView(card)
            except Exception:
                pass

    def _target(self, row):
        try:
            root = self._root
            if row is None or root is None:
                return None
            if not row.isShown():
                return None
            w = row.getWidth()
            h = row.getHeight()
            if w <= 0 or h <= 0:
                return None
            x, y = _offset(row, root)
            pad = float(_dp(PAD_DP))
            return (float(x) + w / 2.0, float(y) + h / 2.0,
                    w / 2.0 + pad, h / 2.0 + pad)
        except Exception:
            return None

    def _sizes(self):
        sw = 0
        sh = 0
        try:
            if self._box is not None:
                sw = self._box.getWidth()
                sh = self._box.getHeight()
        except Exception:
            pass
        if sw <= 0 or sh <= 0:
            try:
                from org.telegram.messenger import AndroidUtilities
                sw = AndroidUtilities.displaySize.x
                sh = AndroidUtilities.displaySize.y
            except Exception:
                pass
        return sw, sh

    def _follow(self):
        try:
            if self._gliding or not self._revealed or self._centered:
                return
            target = self._target(self._row)
            if target is not None and target != self._rect:
                self._rect = target
                self._apply()
                self._place_card()
        except Exception:
            pass

    def _place(self, view, x, y, w, h):
        try:
            params = view.getLayoutParams()
            if params is None:
                return
            params.width = int(w)
            params.height = int(h)
            try:
                params.leftMargin = int(x)
                params.topMargin = int(y)
            except Exception:
                pass
            view.setLayoutParams(params)
        except Exception:
            pass

    def _show(self, view, visible):
        try:
            from android.view import View
            view.setVisibility(View.VISIBLE if visible else View.GONE)
        except Exception:
            pass

    def _apply(self):
        try:
            sw, sh = self._sizes()
            if sw <= 0 or sh <= 0:
                return
            alpha = int(self._dim_alpha) / 255.0
            rect = None if self._centered else self._rect
            if rect is None:
                dims = [(0, 0, sw, sh), None, None, None]
            else:
                cx, cy, hw, hh = rect
                dims = [(0, 0, cx - hw, sh),
                        (cx + hw, 0, sw - (cx + hw), sh),
                        (cx - hw, 0, (cx + hw) - (cx - hw), cy - hh),
                        (cx - hw, cy + hh, (cx + hw) - (cx - hw), sh - (cy + hh))]
            for view, geom in zip(self._dims, dims):
                if geom is None:
                    self._show(view, False)
                    continue
                x, y, w, h = geom
                if w <= 0 or h <= 0:
                    self._show(view, False)
                    continue
                self._place(view, max(0, x), max(0, y), w, h)
                try:
                    view.setAlpha(alpha)
                except Exception:
                    pass
                self._show(view, True)
            if rect is None:
                self._show(self._ring, False)
                for view, _bg in self._waves:
                    self._show(view, False)
                self._show(self._arrow, False)
                return
            cx, cy, hw, hh = rect
            s = self._ring_scale
            try:
                corner = min(float(_dp(CORNER_MAX_DP)), hw, hh)
            except Exception:
                corner = 0.0
            self._place(self._ring, cx - hw * s, cy - hh * s,
                        hw * 2 * s, hh * 2 * s)
            try:
                self._ring_bg.setCornerRadius(corner * s)
            except Exception:
                pass
            self._show(self._ring, True)
            if self._revealed:
                self._tick_waves(sw, sh, cx, cy, hw, hh, corner)
                self._draw_arrow(sw, sh, cx, cy, hw, hh)
            else:
                for view, _bg in self._waves:
                    self._show(view, False)
                self._show(self._arrow, False)
        except Exception as e:
            log_error(e, context="spot apply")

    def _draw_arrow(self, sw, sh, cx, cy, hw, hh):
        try:
            box = self._arrow
            bars = self._arrow_bars
            if box is None or not bars:
                return
            below = self._below
            w = _dp(ARROW_W_DP)
            h = _dp(ARROW_H_DP)
            x = min(max(cx, w / 2.0), sw - w / 2.0)
            if below:
                top = cy + hh + _dp(ARROW_GAP_DP)
                rot = 0.0
            else:
                top = cy - hh - _dp(ARROW_GAP_DP) - h
                rot = 180.0
            self._place(box, x - w / 2.0, top, w, h)
            try:
                box.setRotation(float(rot))
            except Exception:
                pass
            try:
                for bar in bars:
                    bar.setAlpha(230 / 255.0)
            except Exception:
                pass
            self._show(box, True)
        except Exception:
            pass

    def _tick_waves(self, sw, sh, cx, cy, hw, hh, corner):
        try:
            from android.os import SystemClock
            now = SystemClock.uptimeMillis()
            if not self._waves0:
                self._waves0 = now
            grow = float(_dp(WAVE_GROW_DP))
            for i, (view, bg) in enumerate(self._waves):
                phase = ((float(now - self._waves0) / float(WAVE_PERIOD))
                         + float(i) / float(WAVE_COUNT)) % 1.0
                alpha = int(WAVE_ALPHA * (1.0 - phase))
                if alpha <= 0:
                    self._show(view, False)
                    continue
                g = grow * phase
                self._place(view, cx - hw - g, cy - hh - g,
                            (hw + g) * 2, (hh + g) * 2)
                try:
                    bg.setCornerRadius(corner + g)
                except Exception:
                    pass
                try:
                    view.setAlpha(alpha / 255.0)
                except Exception:
                    pass
                self._show(view, True)
        except Exception:
            pass

    def _place_card(self):
        scroll = self._card
        page = self._card_page
        if scroll is None or page is None:
            return
        try:
            from android.view import Gravity, View
            from android.widget import FrameLayout

            sw, sh = self._sizes()
            if sw <= 0 or sh <= 0:
                return
            margin = _dp(CARD_MARGIN_DP)
            maxw = _dp(CARD_MAX_DP)
            card_w = min(sw - margin * 2, maxw)
            if card_w <= 0:
                return
            page.measure(View.MeasureSpec.makeMeasureSpec(card_w, View.MeasureSpec.EXACTLY),
                         View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            full_h = page.getMeasuredHeight()
            if full_h <= 0:
                return
            edge = _dp(12)
            rect = None if self._centered else self._rect
            if rect is None:
                left = (sw - card_w) // 2
                top = (sh - min(full_h, sh - edge * 2)) // 2
                card_h = min(full_h, sh - edge * 2)
                self._below = True
            else:
                cx, cy, hw, hh = rect
                gap = _dp(ARROW_H_DP) + _dp(8)
                left = int(min(max(cx - card_w / 2.0, margin), sw - card_w - margin))
                below_space = sh - edge - (cy + hh + gap)
                above_space = (cy - hh - gap) - edge
                if below_space >= above_space:
                    top = int(cy + hh + gap)
                    card_h = min(full_h, max(int(below_space), 1))
                    self._below = True
                else:
                    full = min(full_h, max(int(above_space), 1))
                    top = int(cy - hh - gap - full)
                    card_h = full
                    self._below = False
            if card_h <= 0:
                return
            params = FrameLayout.LayoutParams(int(card_w), int(card_h))
            try:
                params.gravity = Gravity.TOP | Gravity.LEFT
            except Exception:
                pass
            params.leftMargin = int(left)
            params.topMargin = int(top)
            scroll.setLayoutParams(params)
            self._placed_rect = None if rect is None else tuple(rect)
        except Exception as e:
            log_error(e, context="spot place")

    def _glide(self, duration):
        from android.animation import ValueAnimator
        from hook_utils import find_class
        from java import dynamic_proxy

        try:
            if self._glide_anim is not None:
                self._glide_anim[0].cancel()
        except Exception:
            pass
        self._glide_anim = None
        self._gliding = True
        me = self

        class Glide(dynamic_proxy(find_class("android.animation.ValueAnimator$AnimatorUpdateListener"))):
            def onAnimationUpdate(self, animation):
                try:
                    p = float(animation.getAnimatedValue())
                    e = 1.0 - (1.0 - p) ** 3
                    fx, fy, fw, fh = me._from
                    tx, ty, tw, th = me._to
                    me._rect = (fx + (tx - fx) * e, fy + (ty - fy) * e,
                                fw + (tw - fw) * e, fh + (th - fh) * e)
                    me._ring_scale = 1.0 + (RING_POP - 1.0) * (1.0 - p) ** 2
                    if not me._revealed:
                        me._dim_alpha = float(DIM_ALPHA) * e
                    if p >= 1.0:
                        me._gliding = False
                        me._revealed = True
                        me._dim_alpha = float(DIM_ALPHA)
                    me._apply()
                    me._place_card()
                except Exception:
                    pass

        try:
            cb = Glide()
            anim = ValueAnimator.ofFloat(0.0, 1.0)
            anim.setDuration(int(duration))
            anim.addUpdateListener(cb)
            self._glide_anim = (anim, cb)
            anim.start()
        except Exception as e:
            log_error(e, context="spot glide")
            self._gliding = False
            self._revealed = True
            self._dim_alpha = float(DIM_ALPHA)
            try:
                self._apply()
                self._place_card()
            except Exception:
                pass

    def _tick(self):
        try:
            if self._gone or self._box is None:
                return
            rect = None if self._centered else self._rect
            if rect is not None and self._revealed:
                sw, sh = self._sizes()
                if sw > 0 and sh > 0:
                    cx, cy, hw, hh = rect
                    try:
                        corner = min(float(_dp(CORNER_MAX_DP)), hw, hh)
                    except Exception:
                        corner = 0.0
                    self._tick_waves(sw, sh, cx, cy, hw, hh, corner)
                    try:
                        if self._arrow is not None:
                            from android.os import SystemClock
                            now = SystemClock.uptimeMillis()
                            bob = math.sin(float(now) / 1000.0 * 2.0 * math.pi / 1.2) \
                                * float(_dp(ARROW_BOB_DP))
                            self._arrow.setTranslationY(float(bob))
                    except Exception:
                        pass
            self._follow()
            if self._card is not None:
                placed = self._placed_rect
                current = None if rect is None else tuple(rect)
                if placed != current:
                    try:
                        self._place_card()
                    except Exception:
                        pass
            if self._watch_cb is not None:
                try:
                    from android.os import SystemClock
                    now = SystemClock.uptimeMillis()
                except Exception:
                    now = 0
                if now - self._watch_last > WATCH_MS:
                    self._watch_last = now
                    if not _settings_alive():
                        try:
                            self._watch_cb()
                        except Exception as e:
                            log_error(e, context="spot watch callback")
        except Exception:
            pass

    def _start_ticker(self):
        from android.animation import ValueAnimator
        from hook_utils import find_class
        from java import dynamic_proxy

        me = self

        class Tick(dynamic_proxy(find_class("android.animation.ValueAnimator$AnimatorUpdateListener"))):
            def onAnimationUpdate(self, animation):
                me._tick()

        try:
            cb = Tick()
            anim = ValueAnimator.ofFloat(0.0, 1.0)
            anim.setDuration(WAVE_PERIOD)
            try:
                anim.setRepeatCount(ValueAnimator.INFINITE)
            except Exception:
                anim.setRepeatCount(-1)
            anim.addUpdateListener(cb)
            self._wave_anim = (anim, cb)
            anim.start()
        except Exception as e:
            log_error(e, context="spot ticker start")


def _stroke_bg(color, width_dp, corner):
    from android.graphics.drawable import GradientDrawable

    made = GradientDrawable()
    made.setShape(GradientDrawable.RECTANGLE)
    try:
        made.setStroke(int(width_dp), int(color))
    except Exception:
        pass
    try:
        made.setCornerRadius(float(corner))
    except Exception:
        pass
    try:
        made.setColor(0)
    except Exception:
        pass
    return made


def _card_bg(card):
    try:
        from android.graphics.drawable import GradientDrawable
        from org.telegram.ui.ActionBar import Theme

        try:
            color = Theme.getColor(Theme.key_windowBackgroundWhite)
        except Exception:
            color = -1
        bg = GradientDrawable()
        bg.setShape(GradientDrawable.RECTANGLE)
        bg.setCornerRadius(float(_dp(20)))
        bg.setColor(int(color))
        card.setBackground(bg)
    except Exception as e:
        log_error(e, context="spot card bg")


def attach_spot(root, accent):
    try:
        if root is None:
            return None
        from android.view import Gravity, View, ViewGroup
        from android.widget import FrameLayout

        spot = Spot()
        spot._root = root
        spot._accent = accent
        box = FrameLayout(root.getContext())
        spot._box = box

        corner = float(_dp(CORNER_MAX_DP))
        dims = []
        for _ in range(4):
            view = View(root.getContext())
            try:
                view.setBackgroundColor(-16777216)
            except Exception:
                pass
            try:
                view.setAlpha(0.0)
            except Exception:
                pass
            dims.append(view)
            box.addView(view, FrameLayout.LayoutParams(0, 0))
        spot._dims = dims

        ring_bg = _stroke_bg(accent, _dp(RING_DP), corner)
        ring = View(root.getContext())
        try:
            ring.setBackground(ring_bg)
        except Exception:
            pass
        spot._ring = ring
        spot._ring_bg = ring_bg
        box.addView(ring, FrameLayout.LayoutParams(0, 0))

        waves = []
        for _ in range(WAVE_COUNT):
            bg = _stroke_bg(accent, _dp(WAVE_DP), corner)
            view = View(root.getContext())
            try:
                view.setBackground(bg)
            except Exception:
                pass
            waves.append((view, bg))
            box.addView(view, FrameLayout.LayoutParams(0, 0))
        spot._waves = waves

        arrow_box = FrameLayout(root.getContext())
        bar_len = _dp(ARROW_BAR_DP)
        bar_th = _dp(ARROW_THICK_DP)
        bars = []
        try:
            for left, rot in ((3, 45.0), (17, -45.0)):
                bar = View(root.getContext())
                try:
                    bar.setBackgroundColor(accent)
                except Exception:
                    pass
                arrow_box.addView(bar, FrameLayout.LayoutParams(bar_len, bar_th))
                try:
                    params = bar.getLayoutParams()
                    params.leftMargin = _dp(left)
                    params.topMargin = _dp(13)
                    bar.setLayoutParams(params)
                except Exception:
                    pass
                try:
                    bar.setRotation(float(rot))
                except Exception:
                    pass
                bars.append(bar)
        except Exception as e:
            log_error(e, context="spot arrow bars")
        spot._arrow = arrow_box
        spot._arrow_bars = bars
        box.addView(arrow_box, FrameLayout.LayoutParams(_dp(ARROW_W_DP), _dp(ARROW_H_DP)))
        try:
            from hook_utils import find_class
            from java import dynamic_proxy

            Listener = find_class("android.view.View$OnTouchListener")

            class Touch(dynamic_proxy(Listener)):
                def onTouch(self, view, event):
                    try:
                        from android.view import MotionEvent
                        if event.getAction() == MotionEvent.ACTION_UP:
                            rect = spot._rect
                            if rect is not None and not spot._centered:
                                cx, cy, hw, hh = rect
                                if abs(event.getX() - cx) <= hw \
                                        and abs(event.getY() - cy) <= hh:
                                    cb = spot._next_cb
                                    if cb is not None:
                                        cb()
                    except Exception as e:
                        log_error(e, context="spot touch event")
                    return True

            keeper = Touch()
            spot._touch_keeper = keeper
            box.setOnTouchListener(keeper)
        except Exception as e:
            log_error(e, context="spot touch bind")
        try:
            box.setClickable(True)
        except Exception:
            pass

        try:
            params = ViewGroup.LayoutParams(-1, -1)
        except Exception:
            params = None
        try:
            if params is not None:
                root.addView(box, params)
            else:
                root.addView(box)
        except Exception as e:
            log_error(e, context="spot add view to root")
            return None
        try:
            for view in dims:
                _place_params(view)
            _place_params(ring)
            for view, _bg in waves:
                _place_params(view)
            _place_params(arrow_box)
            try:
                from android.view import View as _V
                arrow_box.setVisibility(_V.GONE)
            except Exception:
                pass
        except Exception:
            pass
        spot._start_ticker()
        return spot
    except Exception as e:
        log_error(e, context="spot attach")
        return None


def _place_params(view):
    try:
        from android.view import Gravity
        from android.widget import FrameLayout

        params = FrameLayout.LayoutParams(0, 0)
        try:
            params.gravity = Gravity.TOP | Gravity.LEFT
        except Exception:
            pass
        view.setLayoutParams(params)
    except Exception:
        pass
