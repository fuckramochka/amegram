import random
import time
from base_plugin import MethodHook
from hook_utils import find_class
from android_utils import run_on_ui_thread, OnClickListener
from client_utils import get_last_fragment, send_request, RequestCallback
from java import dynamic_proxy
from java.lang import Long
from org.telegram.messenger import MessagesController, UserConfig, ChatObject
from org.telegram.tgnet import TLRPC

from ..methods import log, log_error, _tr, open_link
from .badges import get_private_field
from .settings_activity import _get_kpm_icon_drawable
from . import _hold_runnable, _release_runnable

CHANNEL_URL = "https://t.me/KangelPluginsManager"
FORUM_USERNAME = "kangelpluginsmanager"
FORUM_CHAT_ID = 4357768423

_last_said = ""
_closed_session = False
_is_member_cached = None
_last_resolve_time = 0
_our_cell_active = False
_promo_allowed = None


def _say(msg):
    global _last_said
    if msg == _last_said:
        return
    _last_said = msg
    log(msg)


def _get_mc(fragment=None):
    if fragment is not None and hasattr(fragment, "getMessagesController"):
        try:
            mc = fragment.getMessagesController()
            if mc is not None:
                return mc
        except Exception:
            pass
    try:
        return MessagesController.getInstance(UserConfig.selectedAccount)
    except Exception:
        return None


def _is_chat_member(chat):
    if chat is None:
        return False
    try:
        if ChatObject is not None and hasattr(ChatObject, "isNotInChat"):
            return not bool(ChatObject.isNotInChat(chat))
    except Exception as e:
        log_error(e, context="promo ChatObject.isNotInChat")
    left = getattr(chat, "left", False)
    kicked = getattr(chat, "kicked", False)
    deactivated = getattr(chat, "deactivated", False)
    return not (left or kicked or deactivated)


def _find_forum_chat(mc):
    if mc is None:
        return None

    try:
        obj = mc.getUserOrChat(FORUM_USERNAME)
        if obj is not None and isinstance(obj, TLRPC.Chat):
            return obj
    except Exception:
        pass

    try:
        chat = mc.getChat(Long.valueOf(FORUM_CHAT_ID)) or mc.getChat(FORUM_CHAT_ID)
        if chat is not None:
            return chat
    except Exception:
        pass

    try:
        chats = mc.getChats()
        if chats is not None:
            for chat in chats.values():
                if chat is None:
                    continue
                uname = str(getattr(chat, "username", "") or "").lower()
                if uname == FORUM_USERNAME:
                    return chat
                usernames = getattr(chat, "usernames", None)
                if usernames is not None:
                    for u in usernames:
                        if str(getattr(u, "username", "") or "").lower() == FORUM_USERNAME:
                            return chat
    except Exception:
        pass

    return None


def _is_in_forum(fragment=None):
    global _is_member_cached, _last_resolve_time
    if _is_member_cached is True:
        return True

    mc = _get_mc(fragment)
    if mc is None:
        _say("[KPM] promo: MessagesController not available")
        return False

    chat = _find_forum_chat(mc)
    if chat is not None:
        in_chat = _is_chat_member(chat)
        _say(f"[KPM] promo: forum chat found (id={getattr(chat, 'id', None)}), is_member={in_chat}")
        if in_chat:
            _is_member_cached = True
            return True
        return False

    try:
        d_dict = getattr(mc, "dialogs_dict", None)
        if d_dict is not None:
            dialog = d_dict.get(-FORUM_CHAT_ID) or d_dict.get(Long.valueOf(-FORUM_CHAT_ID))
            if dialog is not None:
                _say("[KPM] promo: forum found in dialogs_dict, user is member")
                _is_member_cached = True
                return True
    except Exception as e:
        log_error(e, context="promo dialogs_dict check")

    now = time.time()
    if now - _last_resolve_time > 60:
        _last_resolve_time = now
        _say("[KPM] promo: forum chat not in memory, resolving remotely...")
        _resolve_forum_remote(fragment)

    return False


def _resolve_forum_remote(fragment=None):
    def on_resolve(response, error):
        global _is_member_cached
        if error:
            log(f"[KPM] promo remote resolve error from server: {getattr(error, 'text', error)}")
            return
        if not response:
            log("[KPM] promo remote resolve: empty response")
            return
        chats = getattr(response, "chats", None)
        if not chats or chats.isEmpty():
            log("[KPM] promo remote resolve: no chats found in response")
            return
        chat = chats.get(0)
        mc = _get_mc(fragment)
        if mc is not None:
            try:
                mc.putChats(chats, False)
                if getattr(response, "users", None):
                    mc.putUsers(response.users, False)
            except Exception as e:
                log_error(e, context="promo putChats")
        in_chat = _is_chat_member(chat)
        log(f"[KPM] promo remote resolve complete: chat_id={getattr(chat, 'id', None)}, is_member={in_chat}")
        if in_chat:
            _is_member_cached = True
            def hide():
                diag = fragment or _find_dialogs()
                if diag is not None:
                    _update_hint(diag)
            run_on_ui_thread(hide)

    try:
        req = TLRPC.TL_contacts_resolveUsername()
        req.username = FORUM_USERNAME
        send_request(req, RequestCallback(on_resolve))
    except Exception as e:
        log_error(e, context="promo remote resolve")


class _PromoHook(MethodHook):
    def __init__(self, plugin):
        super().__init__()
        self.plugin = plugin

    def after_hooked_method(self, param):
        try:
            if param.thisObject is not None:
                _fill(self.plugin, param.thisObject)
        except Exception as e:
            log_error(e, context="promo hook")


def install_promo_hook(pl):
    try:
        DialogsActivity = find_class("org.telegram.ui.DialogsActivity")
        if not DialogsActivity:
            log("[KPM] DialogsActivity not found for promo hook")
            return
        pl._track_hooks(pl.hook_all_methods(DialogsActivity, "updateDialogsHint", _PromoHook(pl)))
        log("[KPM] promo hook installed")
    except Exception as e:
        log_error(e, context="install promo hook")


def _hidden(fragment):
    if fragment.isInPreviewMode():
        return True
    right = fragment.rightSlidingDialogContainer
    if right is not None and right.hasFragment():
        return True
    search = get_private_field(fragment, "animatorSearchVisible")
    return search is not None and search.getValue()


def _fill(pl, fragment):
    global _our_cell_active, _promo_allowed
    cell = get_private_field(fragment, "dialogsHintCell")
    panel = get_private_field(fragment, "topPanelLayout")
    if cell is None or panel is None:
        _say("[KPM] promo: hint cell not found")
        return

    if _is_in_forum(fragment):
        _say("[KPM] promo: user is in forum, skipping")
        _our_cell_active = False
        if panel.isViewVisible(cell):
            panel.setViewVisible(cell, False)
        return

    if _closed_session:
        _say("[KPM] promo: closed in this session")
        _our_cell_active = False
        return

    if _promo_allowed is None:
        _promo_allowed = random.random() < 0.5

    if not _promo_allowed:
        _say("[KPM] promo: skipped by roll (50% chance)")
        _our_cell_active = False
        return

    if _hidden(fragment):
        _say("[KPM] promo: hints hidden by client")
        return

    if panel.isViewVisible(cell) and not _our_cell_active:
        _say("[KPM] promo: slot taken by client")
        return

    cell.setText(_tr("promo_title"), _tr("promo_text"))
    icon = _get_kpm_icon_drawable()
    if icon is not None:
        cell.showImage()
        cell.imageView.setImageDrawable(icon)
    cell.setOnClickListener(OnClickListener(lambda *_: open_link(CHANNEL_URL)))
    cell.setOnCloseListener(OnClickListener(lambda *_: _close(pl, fragment)))
    log(f"[KPM] promo: (re)set close listener, our_cell_active={_our_cell_active}")

    if not _our_cell_active:
        panel.setViewVisible(cell, True)
        _our_cell_active = True
        root = fragment.getFragmentView()
        if root is not None:
            root.requestLayout()

        class Finish(dynamic_proxy(find_class("java.lang.Runnable"))):
            def run(self):
                try:
                    if cell.getAlpha() < 1.0:
                        panel.setChildVisibilityFactor(cell, 1.0)
                    _say(f"[KPM] promo: shown {cell.getWidth()}x{cell.getHeight()}")
                except Exception as e:
                    log_error(e, context="promo finish")
                finally:
                    _release_runnable(self)

        cell.post(_hold_runnable(Finish()))


def _close(pl, fragment):
    global _closed_session, _our_cell_active
    _closed_session = True
    _our_cell_active = False
    log("[KPM] promo: closed for this session")
    try:
        cell = get_private_field(fragment, "dialogsHintCell")
        panel = get_private_field(fragment, "topPanelLayout")
        if cell is not None and panel is not None:
            panel.setViewVisible(cell, False)
            log("[KPM] promo: cell hidden directly")
            return
    except Exception as e:
        log_error(e, context="promo direct hide")
    _update_hint(fragment)


def _update_hint(fragment):
    try:
        fragment.updateDialogsHint()
        return
    except Exception as e:
        log_error(e, context="promo updateDialogsHint call")
    try:
        cls = find_class("org.telegram.ui.DialogsActivity")
        for m in cls.getDeclaredMethods():
            if str(m.getName()) == "updateDialogsHint":
                m.setAccessible(True)
                m.invoke(fragment)
                return
    except Exception as e:
        log_error(e, context="promo update hint reflect")


def _find_dialogs():
    DialogsActivity = find_class("org.telegram.ui.DialogsActivity")
    top = get_last_fragment()
    if top is None:
        return None
    if isinstance(top, DialogsActivity):
        return top
    stack = top.getParentLayout().getFragmentStack()
    for i in range(stack.size() - 1, -1, -1):
        fragment = stack.get(i)
        if isinstance(fragment, DialogsActivity):
            return fragment
    return None


def refresh_promo(pl=None):
    global _closed_session, _is_member_cached, _our_cell_active, _promo_allowed
    _closed_session = False
    _is_member_cached = None
    _our_cell_active = False
    _promo_allowed = None

    def go():
        try:
            fragment = _find_dialogs()
            if fragment is None:
                log("[KPM] promo: dialogs fragment not found")
                return
            _update_hint(fragment)
        except Exception as e:
            log_error(e, context="promo refresh")

    run_on_ui_thread(go)
