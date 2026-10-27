from android_utils import OnClickListener
from client_utils import get_last_fragment
from hook_utils import find_class
from java import dynamic_proxy, jint, jarray
from org.telegram.messenger import AndroidUtilities
from org.telegram.ui.ActionBar import Theme
from android.util import TypedValue
from android.view import Gravity, View
from android.widget import LinearLayout, TextView, ImageView, PopupWindow
from android.graphics.drawable import ColorDrawable
from android.graphics import PorterDuff, Color

from ...methods import log, log_error

_menu_runnables_holder = []


def _show_card_actions_menu(context, anchor_view, actions):
    global _menu_runnables_holder
    try:
        from org.telegram.ui.Components import ItemOptions
        fragment = get_last_fragment()
        if fragment and anchor_view:
            options = ItemOptions.makeOptions(fragment, anchor_view)
            options.setDrawScrim(False)
            options.setDimAlpha(0)

            Runnable = find_class("java.lang.Runnable")
            new_runnables = []

            class ActionWrapper(dynamic_proxy(Runnable)):
                def __init__(self, callback):
                    super().__init__()
                    self.callback = callback
                def run(self):
                    try:
                        self.callback()
                    except Exception as e:
                        log_error(e, context="card menu action")

            for icon_res, label, callback in actions:
                r = ActionWrapper(callback)
                new_runnables.append(r)
                if icon_res:
                    options.add(jint(icon_res), str(label), r)
                else:
                    options.add(str(label), r)

            _menu_runnables_holder = new_runnables
            options.show()
            log("[KPM] card actions menu shown via ItemOptions")
            return
    except Exception as e:
        log_error(e, context="ItemOptions menu")

    try:
        container = LinearLayout(context)
        container.setOrientation(LinearLayout.VERTICAL)
        container.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4))
        container.setMinimumWidth(AndroidUtilities.dp(180))

        try:
            bg_color = Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground)
        except Exception:
            bg_color = Theme.getColor(Theme.key_dialogBackground)
        container.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(10), bg_color))

        try:
            text_color = Theme.getColor(Theme.key_actionBarDefaultSubmenuItem)
        except Exception:
            text_color = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText)
        try:
            icon_color = Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon)
        except Exception:
            icon_color = text_color

        popup_holder = []

        for icon_res, label, callback in actions:
            row = LinearLayout(context)
            row.setOrientation(LinearLayout.HORIZONTAL)
            row.setGravity(Gravity.CENTER_VERTICAL)
            row.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(10), AndroidUtilities.dp(14), AndroidUtilities.dp(10))
            try:
                row.setBackground(Theme.getSelectorDrawable(Theme.getColor(Theme.key_listSelector), False))
            except Exception:
                pass

            if icon_res:
                icon_view = ImageView(context)
                icon_view.setImageResource(icon_res)
                try:
                    icon_view.setColorFilter(icon_color, PorterDuff.Mode.SRC_IN)
                except Exception:
                    pass
                icon_lp = LinearLayout.LayoutParams(AndroidUtilities.dp(20), AndroidUtilities.dp(20))
                icon_lp.rightMargin = AndroidUtilities.dp(12)
                row.addView(icon_view, icon_lp)

            label_view = TextView(context)
            label_view.setText(str(label))
            label_view.setTextColor(text_color)
            label_view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15)
            row.addView(label_view, LinearLayout.LayoutParams(-2, -2))

            def on_row_click(view, fn=callback):
                try:
                    if popup_holder:
                        popup_holder[0].dismiss()
                except Exception:
                    pass
                fn()

            row.setOnClickListener(OnClickListener(on_row_click))
            container.addView(row, LinearLayout.LayoutParams(-1, -2))

        popup = PopupWindow(container, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, True)
        popup.setOutsideTouchable(True)
        popup.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        popup_holder.append(popup)

        container.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        measured_h = container.getMeasuredHeight()

        loc = [0, 0]
        if hasattr(anchor_view, "getLocationOnScreen"):
            try:
                arr = jarray(jint)(2)
                anchor_view.getLocationOnScreen(arr)
                loc = [int(arr[0]), int(arr[1])]
            except Exception:
                pass

        display_h = getattr(AndroidUtilities.displaySize, "y", 0) or 1920
        anchor_h = anchor_view.getHeight() if hasattr(anchor_view, "getHeight") else 0

        if loc[1] + anchor_h + measured_h > display_h - AndroidUtilities.dp(60):
            popup.showAsDropDown(anchor_view, 0, -(measured_h + anchor_h + AndroidUtilities.dp(4)), Gravity.END)
        else:
            popup.showAsDropDown(anchor_view, 0, AndroidUtilities.dp(4), Gravity.END)
    except Exception as pe:
        log_error(pe, context="card menu popup fallback")
