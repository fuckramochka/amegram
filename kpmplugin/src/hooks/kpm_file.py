import weakref

from hook_utils import find_class

from ..methods import log, log_error


def install_kpm_file_hook(pl):
    try:
        from base_plugin import MethodHook
        AndroidUtilitiesClass = find_class("org.telegram.messenger.AndroidUtilities")
        if not AndroidUtilitiesClass:
            log("[KPM] AndroidUtilities class not found for file hook")
            return

        class OpenForViewHook(MethodHook):
            def __init__(self, plugin):
                super().__init__()
                self._plugin_ref = weakref.ref(plugin)

            def before_hooked_method(self, param):
                try:
                    plugin = self._plugin_ref()
                    if not plugin:
                        return
                    if param.args and len(param.args) >= 1:
                        msg = param.args[0]
                        if msg and hasattr(msg, "getDocumentName"):
                            name = msg.getDocumentName()
                            if name and str(name).lower().endswith(".kpm"):
                                log(f"[KPM] Intercepted .kpm file open: {name}")
                                plugin._handle_kpm_file_open(msg)
                                param.setResult(False)
                except Exception as e:
                    log_error(e, context="kpm_file before_hooked_method")

        pl._track_hooks(pl.hook_all_methods(AndroidUtilitiesClass, "openForView", OpenForViewHook(pl)))
        log("[KPM] KPM file hook installed")
    except Exception as e:
        log_error(e, context="install_kpm_file_hook")


def _get_message_file_path(message_object):
    try:
        try:
            ChatUtils = find_class("com.exteragram.messenger.utils.chats.ChatUtils") or find_class("com.exteragram.messenger.utils.ChatUtils")
            if ChatUtils:
                path = ChatUtils.getInstance().getPathToMessage(message_object)
                if path:
                    return str(path)
        except Exception:
            pass
        try:
            if hasattr(message_object, "messageOwner") and message_object.messageOwner:
                doc = message_object.messageOwner.media and message_object.messageOwner.media.document
                if doc and hasattr(doc, "localPath") and doc.localPath:
                    return str(doc.localPath)
        except Exception:
            pass
        try:
            doc = message_object.getDocument()
            if doc and hasattr(doc, "getPathName"):
                path = doc.getPathName()
                if path:
                    return str(path)
        except Exception:
            pass
        try:
            doc = message_object.getDocument()
            if doc and hasattr(doc, "getLocalPath"):
                path = doc.getLocalPath()
                if path:
                    return str(path)
        except Exception:
            pass
        return None
    except Exception:
        return None
