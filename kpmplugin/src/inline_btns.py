import threading
import weakref
from base_plugin import MethodHook
from hook_utils import find_class, set_private_field
from android_utils import run_on_ui_thread

from .methods import log, log_error

KPM_SEND_FILE_BTN_ID = 10077
_sending_locks = set()


def _is_kpm_inline_message(message_object):
    try:
        owner = message_object.messageOwner
        if owner is None:
            return False
        msg_params = owner.params
        if msg_params is None:
            return False
        return msg_params.get("kpm_inline") == "1"
    except:
        return False


def _build_kpm_send_file_button():
    ButtonBase = find_class("org.telegram.messenger.BotInlineKeyboard$ButtonCustom")
    if ButtonBase is None:
        return None

    from extera_utils.classes import Base, java_subclass, joverride
    from java import jint

    @java_subclass(ButtonBase)
    class KPMSendFileButton(Base):
        @joverride("getText")
        def get_text(self):
            try:
                from elyx import strings as s
                val = s.get("inline_send_file")
                if val:
                    return str(val)
            except Exception:
                pass
            return "Send file"

        @joverride("getIconRes")
        def get_icon_res(self):
            try:
                from .inline_icons import get_client_drawable_res
                res = get_client_drawable_res("msg_download", "msg_addbot", "msg_settings")
                if res:
                    return jint(res)
                log("[KPM] send file button: no client drawable for icon placeholder")
            except Exception as e:
                log_error(e, context="send file button get_icon_res")
            return 0

        @joverride("getIconEmoji")
        def get_icon_emoji(self):
            return 0

    return KPMSendFileButton


_SendFileButtonClass = None


def _get_send_file_button_class():
    global _SendFileButtonClass
    if _SendFileButtonClass is None:
        _SendFileButtonClass = _build_kpm_send_file_button()
    return _SendFileButtonClass


class _MeasureInlineButtonsHook(MethodHook):
    def __init__(self, plugin):
        MethodHook.__init__(self)
        self._plugin_ref = weakref.ref(plugin)

    def after_hooked_method(self, hook_param):
        try:
            message_object = hook_param.thisObject
            if not _is_kpm_inline_message(message_object):
                return

            SendFileButton = _get_send_file_button_class()

            BotInlineKeyboard = find_class("org.telegram.messenger.BotInlineKeyboard")
            if BotInlineKeyboard is None:
                return

            builder = BotInlineKeyboard.Builder()
            existing = message_object.getInlineBotButtons()
            if existing is not None:
                builder.addKeyboardSource(existing)
                builder.addSeparator()

            from java import jint
            from java import dynamic_proxy
            SourceInterface = find_class("org.telegram.messenger.BotInlineKeyboard$Source")

            send_file_btn = None
            if SendFileButton is not None:
                send_file_btn = SendFileButton.new_java_instance(
                    jint(KPM_SEND_FILE_BTN_ID), jint(0), jint(0),
                )

            buttons = [send_file_btn] if send_file_btn is not None else []
            if not buttons:
                return

            class ButtonRowSource(dynamic_proxy(SourceInterface)):
                def __init__(self, btns):
                    super().__init__()
                    self._btns = btns

                def getRowsCount(self):
                    return 1

                def getColumnsCount(self, row_idx):
                    return len(self._btns)

                def getButton(self, row_idx, col):
                    return self._btns[col]

                def hasSeparator(self, row_idx):
                    return False

            builder.addKeyboardSource(ButtonRowSource(buttons))
            new_source = builder.build()
            set_private_field(message_object, "inlineKeyboardSource", new_source)

        except Exception as e:
            log_error(e, context="_MeasureInlineButtonsHook")


class _DidPressCustomBotButtonHook(MethodHook):
    def __init__(self, plugin):
        MethodHook.__init__(self)
        self._plugin_ref = weakref.ref(plugin)

    def before_hooked_method(self, hook_param):
        try:
            if len(hook_param.args) < 2:
                return

            button = hook_param.args[1]
            if button is None:
                return

            try:
                btn_id = button.id
            except:
                return

            if btn_id != KPM_SEND_FILE_BTN_ID:
                return

            cell = hook_param.args[0]
            if cell is None:
                return

            try:
                message_object = cell.getMessageObject()
            except:
                return

            if message_object is None:
                return

            if not _is_kpm_inline_message(message_object):
                return

            plugin = self._plugin_ref()
            if not plugin:
                return

            if btn_id == KPM_SEND_FILE_BTN_ID:
                msg_id = message_object.getId()
                if msg_id in _sending_locks:
                    return
                _sending_locks.add(msg_id)
                threading.Thread(
                    target=_do_send_file_inline,
                    args=(plugin, message_object),
                    daemon=True
                ).start()

        except Exception as e:
            log_error(e, context="_DidPressCustomBotButtonHook")


def _do_send_file_inline(plugin, message_object):
    msg_id = message_object.getId()
    try:
        import os

        owner = message_object.messageOwner
        if owner is None:
            return

        msg_params = owner.params
        if msg_params is None:
            return

        plugin_id = str(msg_params.get("kpm_plugin_id") or "")
        version = str(msg_params.get("kpm_version") or "")
        ptype = str(msg_params.get("kpm_type") or "store").lower()

        if not plugin_id:
            log("[KPM] send_file_inline: no plugin_id in message params")
            return

        dialog_id = message_object.getDialogId()
        topic_msg_obj = _get_topic_msg_obj_from_message(message_object)

        log(f"[KPM] send_file_inline: plugin_id={plugin_id}, version={version}, type={ptype}")

        if ptype == "store":
            if not plugin.plugins_list:
                try:
                    plugin.store.refresh(force=False)
                except Exception as e:
                    log_error(e, context="store refresh in send_file_inline")

            info = (plugin.plugins_list or {}).get(plugin_id) or plugin.store.get_plugin_info(plugin_id)
            url = None
            if isinstance(info, dict):
                if version:
                    legacy = info.get("legacy_version", {})
                    if isinstance(legacy, dict) and version in legacy:
                        url = legacy[version].get("url") if isinstance(legacy[version], dict) else legacy[version]
                if not url:
                    url = info.get("url", "")
            elif isinstance(info, str):
                url = info

            if url:
                is_eaf = url.rstrip('/').endswith('.eaf')
                ext = ".eaf" if is_eaf else ".plugin"

                import requests as _req
                log(f"[KPM] send_file: downloading {plugin_id} from store URL {url}")
                resp = _req.get(url, timeout=30)
                if resp.status_code == 200 and resp.content:
                    from .inline_bot import _get_temp_file
                    temp = _get_temp_file(f"{plugin_id}{ext}")
                    if temp:
                        file_path = temp.getAbsolutePath()
                        with open(file_path, "wb") as f:
                            f.write(resp.content)
                        log(f"[KPM] send_file: downloaded store file to {file_path}, sending...")
                        _send_file_to_chat(dialog_id, file_path, topic_msg_obj)
                        return
                else:
                    log(f"[KPM] send_file: store download failed HTTP {resp.status_code}")
            else:
                log(f"[KPM] send_file: no store URL for {plugin_id}")

        from hook_utils import find_class
        controller = find_class("com.exteragram.messenger.plugins.PluginsController").getInstance()
        path = None
        try:
            pl_obj = controller.plugins.get(plugin_id) if controller else None
            if pl_obj:
                raw = controller.getPluginPath(plugin_id)
                if raw:
                    path = str(raw)
                    if not os.path.exists(path):
                        path = None
        except Exception as e:
            log_error(e, context="send_file local lookup")

        if not path:
            for candidate in (
                os.path.join(plugin.plugins_dir, f"{plugin_id}.plugin"),
                os.path.join(plugin.plugins_dir, f"{plugin_id}.eaf"),
                os.path.join(plugin.plugins_dir, f"{plugin_id}.py"),
                os.path.join(plugin.plugins_dir, "ElyxPlugins", plugin_id),
            ):
                if os.path.exists(candidate):
                    path = candidate
                    break

        if path and os.path.exists(path):
            log(f"[KPM] send_file: sending local file {path}")
            from .inline_bot import _prepare_local_plugin_send_path
            send_path = _prepare_local_plugin_send_path(plugin_id, path)
            if send_path:
                _send_file_to_chat(dialog_id, send_path, topic_msg_obj)
                return

        if ptype != "store":
            info = (plugin.plugins_list or {}).get(plugin_id)
            if info and isinstance(info, dict) and info.get("url"):
                url = info.get("url")
                is_eaf = url.rstrip('/').endswith('.eaf')
                ext = ".eaf" if is_eaf else ".plugin"
                import requests as _req
                resp = _req.get(url, timeout=30)
                if resp.status_code == 200 and resp.content:
                    from .inline_bot import _get_temp_file
                    temp = _get_temp_file(f"{plugin_id}{ext}")
                    if temp:
                        file_path = temp.getAbsolutePath()
                        with open(file_path, "wb") as f:
                            f.write(resp.content)
                        _send_file_to_chat(dialog_id, file_path, topic_msg_obj)
                        return

        log(f"[KPM] send_file_inline: failed to find file for {plugin_id}")

    except Exception as e:
        log_error(e, context="_do_send_file_inline")
    finally:
        _sending_locks.discard(msg_id)


def _get_topic_msg_obj_from_message(message_object):
    try:
        owner = message_object.messageOwner
        if owner is None:
            return None
        reply_to = getattr(owner, "reply_to", None)
        if reply_to is None:
            return None
        topic_id = getattr(reply_to, "reply_to_top_id", None) or getattr(reply_to, "reply_to_msg_id", None)
        if not topic_id:
            return None

        from org.telegram.messenger import AccountInstance, MessageObject
        from org.telegram.tgnet import TLRPC

        account = AccountInstance.getInstance(0)
        reply_message = TLRPC.TL_message()
        reply_message.message = ""
        reply_message.id = topic_id
        reply_message.peer_id = account.getMessagesController().getPeer(message_object.getDialogId())
        topic_msg_obj = MessageObject(account.getUserConfig().selectedAccount, reply_message, False, False)
        topic_msg_obj.isTopicMainMessage = True
        return topic_msg_obj
    except Exception as e:
        log_error(e, context="_get_topic_msg_obj_from_message")
    return None


def _send_file_to_chat(dialog_id, file_path, topic_msg_obj=None):
    try:
        from .compat.sender import send_document_compat
        send_document_compat(
            dialog_id=dialog_id,
            file_path=file_path,
            reply_to_msg=topic_msg_obj,
            reply_to_top_msg=topic_msg_obj
        )
    except Exception as e:
        log_error(e, context="_send_file_to_chat")


def setup_inline_buttons(plugin):
    try:
        log("[KPM] setup_inline_buttons start")
        MessageObject = find_class("org.telegram.messenger.MessageObject")
        if MessageObject is None:
            log("[KPM] setup_inline_buttons: MessageObject not found")
            return

        measure_method = None
        for m in MessageObject.getClass().getDeclaredMethods():
            try:
                if m.getName() == "measureInlineBotButtons" and len(m.getParameterTypes()) == 0:
                    measure_method = m
                    break
            except:
                continue

        if measure_method is not None:
            measure_method.setAccessible(True)
            plugin._track_hook(plugin.hook_method(measure_method, _MeasureInlineButtonsHook(plugin)))
            log("[KPM] inline buttons measure hook installed")
        else:
            log("[KPM] setup_inline_buttons: measureInlineBotButtons not found")

        ChatMessageCellDelegate = find_class("org.telegram.ui.ChatActivity$ChatMessageCellDelegate")
        if ChatMessageCellDelegate is not None:
            plugin._track_hooks(plugin.hook_all_methods(ChatMessageCellDelegate, "didPressCustomBotButton", _DidPressCustomBotButtonHook(plugin)))
            log("[KPM] inline buttons didPressCustomBotButton hook installed")
        else:
            log("[KPM] setup_inline_buttons: ChatMessageCellDelegate not found")

    except Exception as e:
        log_error(e, context="setup_inline_buttons")
