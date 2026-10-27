import json
from android.widget import HorizontalScrollView, LinearLayout, TextView, ImageView
from android.util import TypedValue
from android.view import Gravity, View
from org.telegram.messenger import AndroidUtilities, UserConfig, MessagesController
from org.telegram.messenger import R as R_tg
from org.telegram.ui.Components import BackupImageView, LayoutHelper, AvatarDrawable
from org.telegram.ui.ActionBar import Theme
from hook_utils import find_class
from java import dynamic_proxy
from client_utils import send_request, run_on_ui_thread, get_last_fragment
from ...methods import log, log_error, _get_lang, _tr, open_username

DEVS_URLS = [
    "https://git.kangel.xyz/KangelPlugins/KPM-Resources/raw/branch/main/devs.json",
    "https://raw.githubusercontent.com/Kangel-Plugins/KPM-Resources/main/devs.json",
    "https://github.com/Kangel-Plugins/KPM-Resources/raw/main/devs.json",
]
OnClickListener = find_class("android.view.View$OnClickListener")
_cached_devs = None


def _ctx():
    frag = get_last_fragment()
    return frag.getContext() if frag else None


def build_dev_cards_view(pl):
    global _cached_devs
    ctx = _ctx()
    scroll = HorizontalScrollView(ctx)
    scroll.setHorizontalScrollBarEnabled(False)

    row = LinearLayout(ctx)
    row.setOrientation(LinearLayout.HORIZONTAL)
    row.setGravity(Gravity.CENTER_VERTICAL)
    row.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8),
                   AndroidUtilities.dp(8), AndroidUtilities.dp(8))

    scroll.addView(row, LinearLayout.LayoutParams(-1, -2))

    if _cached_devs is not None:
        run_on_ui_thread(lambda: _build_cards(row, _cached_devs, pl))
        return scroll

    def _load():
        global _cached_devs
        try:
            data = None
            for url in DEVS_URLS:
                try:
                    raw = pl.fetch_remote_bytes(url)
                    if raw:
                        data = json.loads(raw.decode("utf-8") if isinstance(raw, bytes) else str(raw))
                        if data:
                            break
                except Exception:
                    pass
            if data is None:
                try:
                    from elyx import assets
                    a = assets.get("devs.json")
                    if a:
                        data = json.loads(a.decode("utf-8") if isinstance(a, bytes) else str(a))
                except Exception:
                    pass
            if data:
                _cached_devs = data
                run_on_ui_thread(lambda: _build_cards(row, data, pl))
        except Exception as e:
            log_error(e, context="about load devs")

    from client_utils import run_on_queue
    run_on_queue(_load)
    return scroll


def _build_cards(container, devs, pl):
    ctx = _ctx()
    if not ctx:
        return
    lang = _get_lang()
    dp = AndroidUtilities.dp

    for uid_str, dev in devs.items():
        try:
            uid = int(uid_str)
        except Exception:
            continue
        username = dev.get("username", "")
        desc = dev.get("desc", {})
        desc_text = desc.get(lang) or desc.get("en") or desc.get("ru") or ""
        ch = dev.get("channel", {})
        ch_username = ch.get("username", "")
        ch_text = ch.get("text", {})
        ch_label = ch_text.get(lang) or ch_text.get("en") or ch_text.get("ru") or ("@" + str(ch_username))

        card = LinearLayout(ctx)
        card.setOrientation(LinearLayout.VERTICAL)
        card.setGravity(Gravity.CENTER_HORIZONTAL)
        card.setPadding(dp(16), dp(14), dp(16), dp(12))
        try:
            card.setBackground(Theme.createSimpleSelectorRoundRectDrawable(
                dp(18),
                Theme.getColor(Theme.key_windowBackgroundWhite),
                Theme.getColor(Theme.key_windowBackgroundWhite)))
        except Exception:
            pass

        avatar = BackupImageView(ctx)
        avatar.setRoundRadius(dp(30))
        avatar.setAspectFit(True)
        card.addView(avatar, LayoutHelper.createLinear(dp(60), dp(60),
                                                       Gravity.CENTER_HORIZONTAL, 0, 0, 0, 8))
        _load_avatar(avatar, uid)

        name = TextView(ctx)
        name.setText("@" + str(username))
        name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15)
        name.setGravity(Gravity.CENTER)
        name.setSingleLine(True)
        try:
            name.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
            name.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM))
        except Exception:
            pass
        card.addView(name, LayoutHelper.createLinear(-1, -2, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 4))

        d = TextView(ctx)
        d.setText(desc_text)
        d.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13)
        d.setGravity(Gravity.CENTER)
        try:
            d.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText))
        except Exception:
            pass
        card.addView(d, LayoutHelper.createLinear(-1, -2, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 6))

        sep = _make_separator(ctx)
        if sep:
            card.addView(sep, LinearLayout.LayoutParams(-1, -2))

        if ch_username:
            ch_row = _make_channel_row(ctx, ch_label, ch_username)
            if ch_row:
                card.addView(ch_row, LayoutHelper.createLinear(-1, -2))
        else:
            dm_row = _make_dm_row(ctx, username)
            if dm_row:
                card.addView(dm_row, LayoutHelper.createLinear(-1, -2))

        un_ref = username

        class CardClick(dynamic_proxy(OnClickListener)):
            def onClick(self, v, _u=un_ref):
                try:
                    if _u:
                        open_username(_u)
                except Exception as e:
                    log_error(e, context="dev card click")

        card.setOnClickListener(CardClick())

        wrapper = LinearLayout(ctx)
        wrapper.addView(card, LinearLayout.LayoutParams(dp(280), -2))
        container.addView(wrapper, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT))


def _make_separator(ctx):
    try:
        sep = View(ctx)
        sep.setBackgroundColor(Theme.getColor(Theme.key_divider))
        sep_lp = LinearLayout.LayoutParams(-1, 1)
        sep_lp.setMargins(AndroidUtilities.dp(16), AndroidUtilities.dp(4), AndroidUtilities.dp(16), AndroidUtilities.dp(4))
        wrapper = LinearLayout(ctx)
        wrapper.addView(sep, sep_lp)
        return wrapper
    except Exception:
        return None


def _make_channel_row(ctx, label, username):
    try:
        dp = AndroidUtilities.dp
        row = LinearLayout(ctx)
        row.setOrientation(LinearLayout.HORIZONTAL)
        row.setGravity(Gravity.CENTER_VERTICAL)
        row.setPadding(dp(8), dp(6), dp(8), dp(6))
        row.setClickable(True)

        try:
            selector = Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 2)
            row.setBackground(selector)
        except Exception:
            pass

        icon = ImageView(ctx)
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE)
        try:
            res_id = getattr(R_tg.drawable, "msg_channel")
            from androidx.core.content import ContextCompat
            drawable = ContextCompat.getDrawable(ctx, res_id)
            if drawable is not None:
                drawable.setTint(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon))
                icon.setImageDrawable(drawable)
        except Exception:
            pass
        row.addView(icon, LayoutHelper.createLinear(dp(20), dp(20), Gravity.CENTER_VERTICAL, 0, 0, dp(3), 0))

        tv = TextView(ctx)
        tv.setText(label)
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13)
        tv.setMaxLines(3)
        try:
            from android.text import TextUtils
            tv.setEllipsize(TextUtils.TruncateAt.END)
        except Exception:
            pass
        tv.setHorizontallyScrolling(False)
        tv.setGravity(Gravity.START | Gravity.CENTER_VERTICAL)
        try:
            tv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText))
        except Exception:
            pass
        row.addView(tv, LinearLayout.LayoutParams(0, -2, 1.0))

        class ChClick(dynamic_proxy(OnClickListener)):
            def onClick(self, v):
                open_username(username)

        row.setOnClickListener(ChClick())
        return row
    except Exception:
        return None


def _make_dm_row(ctx, username):
    try:
        dp = AndroidUtilities.dp
        label = _tr("write_to_dm")

        row = LinearLayout(ctx)
        row.setOrientation(LinearLayout.HORIZONTAL)
        row.setGravity(Gravity.CENTER_VERTICAL)
        row.setPadding(dp(8), dp(6), dp(8), dp(6))
        row.setClickable(True)

        try:
            selector = Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 2)
            row.setBackground(selector)
        except Exception:
            pass

        icon = ImageView(ctx)
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE)
        try:
            res_id = getattr(R_tg.drawable, "msg_forward")
            from androidx.core.content import ContextCompat
            drawable = ContextCompat.getDrawable(ctx, res_id)
            if drawable is not None:
                drawable.setTint(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon))
                icon.setImageDrawable(drawable)
        except Exception:
            pass
        row.addView(icon, LayoutHelper.createLinear(dp(20), dp(20), Gravity.CENTER_VERTICAL, 0, 0, dp(3), 0))

        tv = TextView(ctx)
        tv.setText(label)
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13)
        tv.setMaxLines(2)
        try:
            from android.text import TextUtils
            tv.setEllipsize(TextUtils.TruncateAt.END)
        except Exception:
            pass
        tv.setHorizontallyScrolling(False)
        tv.setGravity(Gravity.START | Gravity.CENTER_VERTICAL)
        try:
            tv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText))
        except Exception:
            pass
        row.addView(tv, LinearLayout.LayoutParams(0, -2, 1.0))

        un = username

        class DmClick(dynamic_proxy(OnClickListener)):
            def onClick(self, v):
                open_username(un)

        row.setOnClickListener(DmClick())
        return row
    except Exception:
        return None


def _load_avatar(img, uid):
    from org.telegram.tgnet import TLRPC
    from org.telegram.messenger import ImageLocation as IL
    mc = MessagesController.getInstance(UserConfig.selectedAccount)
    account = UserConfig.selectedAccount

    def _set_avatar(user):
        try:
            drawable = AvatarDrawable(user)
            loc = IL.getForUserOrChat(account, user, 0)
            thumb_loc = IL.getForUserOrChat(account, user, 2)
            img.setImage(loc, "640_640", thumb_loc, "50_50_b", drawable, user)
        except Exception:
            try:
                img.setForUserOrChat(user, AvatarDrawable(user))
            except Exception:
                pass

    cached = mc.getUser(uid)
    if cached and not getattr(cached, 'min', True):
        _set_avatar(cached)
        return

    req = TLRPC.TL_users_getUsers()
    inp = TLRPC.TL_inputUser()
    inp.user_id = uid
    inp.access_hash = 0
    req.id.add(inp)

    def _on_resp(resp, err):
        user = None
        if not err and resp:
            try:
                if resp.objects.size() > 0:
                    user = resp.objects.get(0)
                    mc.putUser(user, False)
            except Exception:
                pass
        if user:
            run_on_ui_thread(lambda u=user: _set_avatar(u))

    send_request(req, _on_resp)
