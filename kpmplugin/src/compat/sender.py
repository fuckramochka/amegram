import os
import traceback
from java.util import ArrayList
from java.lang import String as JString, Long as JLong, Integer as JInteger, Boolean as JBoolean
from java import jlong, jint

from ..methods import log, log_error


def send_document_compat(
    dialog_id,
    file_path,
    caption="",
    entities=None,
    reply_to_msg=None,
    reply_to_top_msg=None,
    mime_type="application/octet-stream"
):
    log(f"[KPM] send_document_compat: dialog_id={dialog_id}, file={file_path}, caption='{caption[:30]}...'")

    if not os.path.exists(file_path):
        log(f"[KPM] send_document_compat: file does not exist at {file_path}")
        return False

    try:
        from client_utils import send_document
        kwargs = {}
        if caption:
            kwargs["caption"] = caption
        if reply_to_msg is not None:
            kwargs["replyToMsg"] = reply_to_msg
        if reply_to_top_msg is not None:
            kwargs["replyToTopMsg"] = reply_to_top_msg
        send_document(dialog_id, file_path, **kwargs)
        log("[KPM] send_document_compat: successfully sent via client_utils.send_document")
        return True
    except Exception as e_cu:
        log_error(e_cu, context="send_document_compat: client_utils.send_document failed, trying SendMessagesHelper reflection")

    try:
        from hook_utils import find_class
        SendMessagesHelper = find_class("org.telegram.messenger.SendMessagesHelper")
        AccountInstance = find_class("org.telegram.messenger.AccountInstance")
        UserConfig = find_class("org.telegram.messenger.UserConfig")

        if not SendMessagesHelper or not AccountInstance:
            log("[KPM] send_document_compat: SendMessagesHelper or AccountInstance not found")
            return False

        account_id = 0
        try:
            if UserConfig:
                account_id = getattr(UserConfig, "selectedAccount", 0)
        except Exception:
            pass

        account_instance = AccountInstance.getInstance(account_id)
        target_dialog_id = jlong(int(dialog_id))

        paths = ArrayList()
        orig_paths = ArrayList()
        paths.add(file_path)
        orig_paths.add(file_path)

        entities_list = entities if entities is not None else None

        smh_cls = SendMessagesHelper.getClass() if hasattr(SendMessagesHelper, "getClass") else SendMessagesHelper
        methods = smh_cls.getDeclaredMethods()

        matching_methods = []
        for m in methods:
            try:
                name = m.getName()
                if name in ("prepareSendingDocuments", "prepareSendingDocument"):
                    matching_methods.append(m)
            except Exception:
                pass

        matching_methods.sort(key=lambda m: (0 if m.getName() == "prepareSendingDocuments" else 1, len(m.getParameterTypes())))

        for m in matching_methods:
            try:
                m.setAccessible(True)
                param_types = m.getParameterTypes()
                arg_count = len(param_types)
                args = []

                str_idx = 0
                array_idx = 0

                for i, ptype in enumerate(param_types):
                    pt_name = str(ptype.getName())

                    if "AccountInstance" in pt_name:
                        args.append(account_instance)
                    elif "ArrayList" in pt_name or "List" in pt_name:
                        if array_idx == 0:
                            args.append(paths)
                        elif array_idx == 1:
                            args.append(orig_paths)
                        elif array_idx == 2 and "MessageEntity" in str(param_types[i]):
                            args.append(entities_list)
                        else:
                            args.append(None)
                        array_idx += 1
                    elif "String" in pt_name or "CharSequence" in pt_name:
                        if m.getName() == "prepareSendingDocument" and str_idx == 0:
                            args.append(JString(file_path))
                        elif m.getName() == "prepareSendingDocument" and str_idx == 1:
                            args.append(JString(file_path))
                        elif "caption" in pt_name.lower() or str_idx == 0 or (m.getName() == "prepareSendingDocument" and str_idx == 2):
                            args.append(JString(caption) if caption else None)
                        elif "mime" in pt_name.lower() or (m.getName() == "prepareSendingDocument" and str_idx == 3):
                            args.append(JString(mime_type))
                        else:
                            args.append(None)
                        str_idx += 1
                    elif pt_name == "long" or "Long" in pt_name:
                        if i < 10:
                            args.append(target_dialog_id)
                        else:
                            args.append(jlong(0))
                    elif pt_name == "int" or "Integer" in pt_name:
                        args.append(jint(0))
                    elif pt_name == "boolean" or "Boolean" in pt_name:
                        args.append(True if i < 15 else False)
                    elif "MessageObject" in pt_name:
                        if reply_to_msg is not None and "reply" in pt_name.lower():
                            args.append(reply_to_msg)
                        elif reply_to_top_msg is not None:
                            args.append(reply_to_top_msg)
                        else:
                            args.append(None)
                    else:
                        args.append(None)

                log(f"[KPM] Attempting {m.getName()} with {arg_count} args...")
                m.invoke(None, *args)
                log(f"[KPM] Successfully sent document using {m.getName()} ({arg_count} args)")
                return True
            except Exception as e_invoke:
                log(f"[KPM] Method {m.getName()}({len(m.getParameterTypes())} args) invocation failed: {e_invoke}")

    except Exception as e_all:
        log_error(e_all, context="send_document_compat failed completely")

    return False
