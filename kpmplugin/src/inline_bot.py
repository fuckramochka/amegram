import os
import weakref
from java.util import ArrayList
from org.telegram.tgnet import TLRPC

from base_plugin import MethodHook
from android_utils import run_on_ui_thread
from client_utils import run_on_queue
from hook_utils import find_class, get_private_field, set_private_field

from .methods import log, log_error


def install_inline_hooks(pl):
    try:
        from .inline_icons import install_inline_cell_hooks
        pl._kpm_class_cache = {}
        pl._kpm_inline_plugins = {}
        _hook_mentions_adapter(pl)
        _hook_send_inline_result(pl)
        install_inline_cell_hooks(pl)
        log("[KPM] Inline autocomplete hooks installed")
    except Exception as e:
        log_error(e, context="install_inline_hooks")


def _get_class(pl, class_name):
    if class_name not in pl._kpm_class_cache:
        pl._kpm_class_cache[class_name] = find_class(class_name)
    return pl._kpm_class_cache[class_name]


def _get_trigger(pl):
    try:
        from elyx import settings as _s
        cmd = _s.get("inline_search_trigger", "kpm").strip()
    except Exception:
        cmd = "kpm"
    if cmd.startswith("@"):
        cmd = cmd[1:]
    return cmd


def _inline_search_enabled(pl):
    try:
        from elyx import settings as _s
        return bool(_s.get("inline_search_enabled", True))
    except Exception:
        return True


class _InlineSearchBotHook(MethodHook):
    def __init__(self, plugin):
        MethodHook.__init__(self)
        self._plugin_ref = weakref.ref(plugin)

    def before_hooked_method(self, hook_param):
        plugin = self._plugin_ref()
        if not plugin:
            return
        try:
            if not _inline_search_enabled(plugin):
                return
            if len(hook_param.args) < 2:
                return
            username = hook_param.args[0]
            query = hook_param.args[1]
            trigger = _get_trigger(plugin)
            if username and str(username).lower() == trigger.lower():
                hook_param.setResult(None)
                _process_inline_search(plugin, hook_param.thisObject, query)
        except Exception as e:
            log_error(e, context="_InlineSearchBotHook")


class _InlineSearchHashtagHook(MethodHook):
    def __init__(self, plugin):
        MethodHook.__init__(self)
        self._plugin_ref = weakref.ref(plugin)

    def before_hooked_method(self, hook_param):
        plugin = self._plugin_ref()
        if not plugin:
            return
        try:
            if not _inline_search_enabled(plugin):
                return
            if not hook_param.args:
                return
            text = str(hook_param.args[0])
            trigger = "@" + _get_trigger(plugin)
            if text.lower().startswith(trigger.lower()):
                hook_param.setResult(None)
                query = text[len(trigger):].strip()
                _process_inline_search(plugin, hook_param.thisObject, query)
        except Exception as e:
            log_error(e, context="_InlineSearchHashtagHook")


def _hook_mentions_adapter(pl):
    try:
        MentionsAdapter = _get_class(pl, "org.telegram.ui.Adapters.MentionsAdapter")
        if not MentionsAdapter:
            log("[KPM] MentionsAdapter not found")
            return
        pl._track_hooks(pl.hook_all_methods(MentionsAdapter.getClass(), "searchForContextBot", _InlineSearchBotHook(pl)))
        pl._track_hooks(pl.hook_all_methods(MentionsAdapter.getClass(), "searchUsernameOrHashtag", _InlineSearchHashtagHook(pl)))
        log("[KPM] MentionsAdapter inline hooks installed")
    except Exception as e:
        log_error(e, context="_hook_mentions_adapter")


class _SendInlineResultHook(MethodHook):
    def __init__(self, plugin):
        MethodHook.__init__(self)
        self._plugin_ref = weakref.ref(plugin)

    def before_hooked_method(self, hook_param):
        plugin = self._plugin_ref()
        if not plugin:
            return
        try:
            if len(hook_param.args) < 1:
                return
            bot_inline_result = hook_param.args[0]
            if bot_inline_result is None:
                return
            res_id = getattr(bot_inline_result, "id", None)
            if not res_id:
                return
            res_id = str(res_id)
            if not (res_id.startswith("local|") or res_id.startswith("remote|")):
                return

            hook_param.setResult(None)

            chat_activity = hook_param.thisObject
            chat_id = _get_chat_id(chat_activity)
            topic_msg_obj = _get_topic_message_object(chat_activity, chat_id)

            try:
                ev = get_private_field(chat_activity, "chatActivityEnterView")
                if ev:
                    ev.setFieldText("")
            except Exception:
                pass
            try:
                chat_activity.hideFieldPanel(False)
            except Exception:
                pass

            if chat_id:
                run_on_queue(lambda: _process_inline_selection(plugin, res_id, chat_id, topic_msg_obj))
        except Exception as e:
            log_error(e, context="_SendInlineResultHook")


def _hook_send_inline_result(pl):
    try:
        ChatActivity = _get_class(pl, "org.telegram.ui.ChatActivity")
        if not ChatActivity:
            log("[KPM] ChatActivity not found")
            return
        pl._track_hooks(pl.hook_all_methods(ChatActivity.getClass(), "sendBotInlineResult", _SendInlineResultHook(pl)))
        log("[KPM] sendBotInlineResult hook installed")
    except Exception as e:
        log_error(e, context="_hook_send_inline_result")


_plugins_cache = None
_plugins_cache_mtime = 0


def _get_plugins_cached(pl):
    global _plugins_cache, _plugins_cache_mtime
    try:
        cache_file = os.path.join(pl.plugins_dir, ".kpm_cache.json")
        mtime = os.path.getmtime(cache_file) if os.path.exists(cache_file) else 0
        if _plugins_cache is not None and mtime == _plugins_cache_mtime:
            return _plugins_cache
    except Exception:
        if _plugins_cache is not None:
            return _plugins_cache
    _plugins_cache = _load_plugins_from_cache(pl)
    _plugins_cache_mtime = mtime
    log(f"[KPM] inline: plugins cache refreshed ({len(_plugins_cache)} items)")
    return _plugins_cache


def _process_inline_search(pl, adapter, query):
    try:
        query = str(query).lower().strip() if query else ""
        all_plugins = _get_plugins_cached(pl)
        results = ArrayList()
        local_results = ArrayList()
        remote_results = ArrayList()
        pl._kpm_inline_plugins = {}

        for p in all_plugins:
            pid = p.get("id", "")
            name = p.get("name", "") or ""
            author = p.get("author", "") or ""
            desc = p.get("description", "") or ""
            ver = p.get("version", "1.0") or "1.0"
            ptype = p.get("type", "store") or "store"

            if query:
                match = (query in name.lower() or query in str(pid).lower()
                         or query in author.lower()
                         or (desc and query in desc.lower()))
            else:
                match = True

            if match:
                if ptype == "local":
                    res_id = f"local|{pid}"
                    title = f"[Local] {name}"
                    description = f"v{ver} | {author}"
                else:
                    res_id = f"remote|{pid}"
                    title = f"[Store] {name}"
                    description = f"v{ver} | {author} | Tap to download & send"
                result = _create_inline_result(pl, res_id, title, description)
                if result:
                    if ptype == "local":
                        local_results.add(result)
                    else:
                        remote_results.add(result)
                    pl._kpm_inline_plugins[res_id] = p

        while local_results.size() + remote_results.size() > 50:
            if remote_results.size() > 0:
                remote_results.remove(remote_results.size() - 1)
            else:
                local_results.remove(local_results.size() - 1)

        for i in range(local_results.size()):
            results.add(local_results.get(i))
        for i in range(remote_results.size()):
            results.add(remote_results.get(i))

        run_on_ui_thread(lambda: _inject_inline_results(pl, adapter, results))
    except Exception as e:
        log_error(e, context="_process_inline_search")


def _create_inline_result(pl, res_id, title, description):
    try:
        res = TLRPC.TL_botInlineResult()
        res.id = res_id
        res.type = "article"
        res.title = title
        res.description = description
        res.send_message = TLRPC.TL_botInlineMessageText()
        res.send_message.message = f".kpm_send {res_id}"
        res.send_message.no_webpage = True
        return res
    except Exception as e:
        log_error(e, context="_create_inline_result")
        return None


def _inject_inline_results(pl, adapter, results):
    try:
        from java.lang import Boolean

        fake_bot = TLRPC.TL_user()
        fake_bot.id = 987654321
        fake_bot.username = _get_trigger(pl)
        fake_bot.bot = True

        set_private_field(adapter, "foundContextBot", fake_bot)
        set_private_field(adapter, "searchResultBotContext", results)
        adapter.notifyDataSetChanged()

        delegate = get_private_field(adapter, "delegate")
        if delegate:
            try:
                m = delegate.getClass().getDeclaredMethod("needChangePanelVisibility", Boolean.TYPE)
                m.setAccessible(True)
                m.invoke(delegate, True)
            except Exception as e:
                log_error(e, context="panel visibility needChangePanelVisibility")
        else:
            log("[KPM] no delegate found")
    except Exception as e:
        log_error(e, context="_inject_inline_results")


def _process_inline_selection(pl, res_id, chat_id, topic_msg_obj=None):
    try:
        plugin_data = (pl._kpm_inline_plugins or {}).get(str(res_id))
        if not plugin_data:
            log(f"[KPM] no plugin_data for {res_id}")
            return
        if str(plugin_data.get("type", "store")) == "local":
            _send_local_plugin_file(pl, plugin_data.get("id"), plugin_data.get("name"), plugin_data.get("version"), chat_id, topic_msg_obj)
        else:
            _send_plugin_info(pl, plugin_data, chat_id, topic_msg_obj)
    except Exception as e:
        log_error(e, context="_process_inline_selection")


def _get_topic_message_object(frag, chat_id):
    try:
        if not frag or not chat_id or not getattr(frag, "isTopic", False):
            return None
        topic_id = frag.getTopicId()
        if not topic_id:
            return None
        from org.telegram.messenger import MessageObject as MsgObj
        mc = frag.getMessagesController()
        topic = mc.getTopicsController().findTopic(-chat_id, topic_id)
        if topic is not None and topic.topicStartMessage is not None:
            topic_msg_obj = MsgObj(frag.getCurrentAccount(), topic.topicStartMessage, False, False)
            topic_msg_obj.isTopicMainMessage = True
            return topic_msg_obj
    except Exception as e:
        log_error(e, context="_get_topic_message_object")
    return None


def _load_plugins_from_cache(pl):
    plugins_list = []
    try:
        cache_file = os.path.join(pl.plugins_dir, ".kpm_cache.json")
        if os.path.exists(cache_file):
            import json
            with open(cache_file, "r", encoding="utf-8") as f:
                data = json.load(f)
            plugins_dict = data.get("plugins_list", {})
            for pid, info in plugins_dict.items():
                if isinstance(info, dict):
                    plugins_list.append({
                        "id": pid,
                        "name": info.get("name", pid),
                        "version": info.get("version", "1.0"),
                        "author": info.get("author", ""),
                        "description": info.get("description", ""),
                        "url": info.get("url", ""),
                        "icon": info.get("icon") or "",
                        "type": "store"
                    })
    except Exception as e:
        log_error(e, context="_load_plugins_from_cache json")

    try:
        controller = find_class("com.exteragram.messenger.plugins.PluginsController").getInstance()
        if controller:
            pls = controller.plugins
            for pid in pls.keySet().toArray():
                try:
                    pl_obj = pls.get(pid)
                    icon_val = None
                    if hasattr(pl_obj, "getIcon"):
                        try:
                            icon_val = pl_obj.getIcon()
                        except Exception:
                            pass
                    if not icon_val and hasattr(pl_obj, "getPack") and hasattr(pl_obj, "getIndex"):
                        try:
                            pack = pl_obj.getPack()
                            idx = pl_obj.getIndex()
                            if pack and idx is not None and int(idx) >= 0:
                                icon_val = f"{pack}/{idx}"
                        except Exception:
                            pass

                    plugins_list.append({
                        "id": pid,
                        "name": pl_obj.getName() or pid,
                        "version": pl_obj.getVersion() or "1.0",
                        "author": pl_obj.getAuthor() or "",
                        "description": pl_obj.getDescription() or "",
                        "icon": icon_val or "",
                        "type": "local"
                    })
                except:
                    pass
    except:
        pass

    return plugins_list


def _send_plugin_info(pl, plugin_data, chat_id, topic_msg_obj=None):
    try:
        plugin_id = plugin_data.get("id", "unknown")
        name = plugin_data.get("name", "Unknown Plugin")
        version = plugin_data.get("version", "")
        ptype = plugin_data.get("type", "store")

        plugin_link = f"tg://kpm_install?plugin={plugin_id}&version={version}"
        forum_link = "https://t.me/KangelPluginsManager"

        entities = []
        parts = []
        offset = 0

        name_text = f"{name}"
        parts.append(name_text)
        e = TLRPC.TL_messageEntityTextUrl()
        e.offset = offset
        e.length = len(name_text)
        e.url = plugin_link
        entities.append(e)
        b = TLRPC.TL_messageEntityBold()
        b.offset = offset
        b.length = len(name_text)
        entities.append(b)
        offset += len(name_text)

        if version:
            vt = f" v{version}"
            parts.append(vt)
            offset += len(vt)

        parts.append(" - Install from ")
        offset += len(" - Install from ")

        kpm_text = "Kangel Plugins Manager"
        parts.append(kpm_text)
        ek = TLRPC.TL_messageEntityTextUrl()
        ek.offset = offset
        ek.length = len(kpm_text)
        ek.url = forum_link
        entities.append(ek)

        message_text = "".join(parts)

        try:
            from client_utils import send_message
            message_data = {
                "peer": chat_id,
                "message": message_text,
                "entities": entities,
                "searchLinks": False,
                "params": {
                    "kpm_inline": "1",
                    "kpm_plugin_id": plugin_id,
                    "kpm_version": version,
                    "kpm_name": name,
                    "kpm_type": ptype,
                },
            }
            if topic_msg_obj is not None:
                message_data["replyToMsg"] = topic_msg_obj
                message_data["replyToTopMsg"] = topic_msg_obj
            send_message(message_data)
        except Exception:
            try:
                from org.telegram.messenger import SendMessagesHelper as SMH
                smh = SMH.getInstance(0)
                params = SMH.SendMessageParams.of(message_text, chat_id)
                params.entities = entities
                params.searchLinks = False
                if topic_msg_obj is not None:
                    params.replyToMsg = topic_msg_obj
                    params.replyToTopMsg = topic_msg_obj
                params.additionalParams = {
                    "kpm_inline": "1",
                    "kpm_plugin_id": plugin_id,
                    "kpm_version": version,
                    "kpm_name": name,
                    "kpm_type": ptype,
                }
                smh.sendMessage(params)
            except Exception as e2:
                log_error(e2, context="send plugin info fallback SMH")

    except Exception as e:
        log_error(e, context="_send_plugin_info")


def _prepare_local_plugin_send_path(plugin_id, path):
    try:
        import os
        import shutil
        import zipfile

        if os.path.isdir(path):
            temp_file = _get_temp_file(f"{plugin_id}.eaf")
            if not temp_file:
                return None
            send_path = temp_file.getAbsolutePath()
            with zipfile.ZipFile(send_path, "w", zipfile.ZIP_DEFLATED) as zf:
                for root, dirs, files in os.walk(path):
                    for fn in files:
                        full = os.path.join(root, fn)
                        rel = os.path.relpath(full, path)
                        zf.write(full, rel)
            return send_path

        ext = os.path.splitext(path)[1].lower()
        base_lower = os.path.basename(path).lower()
        if base_lower.endswith(".elyx.zip") or ext == ".zip":
            target_ext = ".eaf"
        else:
            target_ext = ".plugin" if ext == ".py" else ext
        temp_file = _get_temp_file(f"{plugin_id}{target_ext}")
        if not temp_file:
            return None
        send_path = temp_file.getAbsolutePath()
        shutil.copy2(path, send_path)
        return send_path
    except Exception as e:
        log_error(e, context="_prepare_local_plugin_send_path")
        return None


def _send_local_plugin_file(pl, plugin_id, name, version, chat_id, topic_msg_obj=None):
    try:
        from hook_utils import find_class
        import os
        import shutil

        controller = find_class("com.exteragram.messenger.plugins.PluginsController").getInstance()

        path = None
        try:
            raw = controller.getPluginPath(plugin_id)
            if raw:
                path = str(raw)
        except Exception:
            pass

        if not path or not os.path.exists(path):
            for candidate in (
                os.path.join(pl.plugins_dir, f"{plugin_id}.plugin"),
                os.path.join(pl.plugins_dir, f"{plugin_id}.eaf"),
                os.path.join(pl.plugins_dir, f"{plugin_id}.py"),
                os.path.join(pl.plugins_dir, "ElyxPlugins", plugin_id),
            ):
                if os.path.exists(candidate):
                    path = candidate
                    break

        if not path or not os.path.exists(path):
            log(f"[KPM] send_local: no file for {plugin_id}")
            return

        send_path = _prepare_local_plugin_send_path(plugin_id, path)
        if not send_path:
            log(f"[KPM] send_local: could not prepare {plugin_id}")
            return

        forum_link = "https://t.me/KangelPluginsManager"
        ver_str = f" v{version}" if version else ""
        caption = f"{name}{ver_str} - Sended from Kangel Plugins Manager"

        kpm_anchor = "Kangel Plugins Manager"
        kpm_offset = caption.rfind(kpm_anchor)
        entities = None
        if kpm_offset >= 0:
            entities = ArrayList()
            e1 = TLRPC.TL_messageEntityTextUrl()
            e1.offset = len(caption[:kpm_offset].encode('utf_16_le')) // 2
            e1.length = len(kpm_anchor.encode('utf_16_le')) // 2
            e1.url = forum_link
            entities.add(e1)

        from .compat.sender import send_document_compat
        send_document_compat(
            dialog_id=chat_id,
            file_path=send_path,
            caption=caption,
            entities=entities,
            reply_to_msg=topic_msg_obj,
            reply_to_top_msg=topic_msg_obj
        )

    except Exception as e:
        log_error(e, context="_send_local_plugin_file")


def _send_document_with_links(peer_id, file_path, display_name, caption, install_url, topic_msg_obj=None):
    try:
        entities = ArrayList()
        e1 = TLRPC.TL_messageEntityTextUrl()
        e1.offset = 0
        e1.length = len(display_name.encode('utf_16_le')) // 2
        e1.url = install_url
        entities.add(e1)

        from .compat.sender import send_document_compat
        send_document_compat(
            dialog_id=peer_id,
            file_path=file_path,
            caption=caption,
            entities=entities,
            reply_to_msg=topic_msg_obj,
            reply_to_top_msg=topic_msg_obj
        )
        log("[KPM] Document sent successfully")
    except Exception as e:
        log_error(e, context="_send_document_with_links")


def _get_chat_id(frag):
    try:
        dialog_id = frag.getDialogId()
        if dialog_id:
            return dialog_id
    except:
        pass
    try:
        current_chat = frag.getCurrentChat()
        if current_chat and hasattr(current_chat, 'id'):
            return current_chat.id
    except:
        pass
    try:
        current_user = frag.getCurrentUser()
        if current_user and hasattr(current_user, 'id'):
            return current_user.id
    except:
        pass
    try:
        current_peer = frag.getCurrentPeer()
        if current_peer and hasattr(current_peer, 'id'):
            return current_peer.id
    except:
        pass
    return None


def _get_temp_file(filename):
    try:
        from java.io import File
        from org.telegram.messenger import ApplicationLoader
        d = File(ApplicationLoader.applicationContext.getExternalCacheDir(), "kpm_inline")
        if not d.exists():
            d.mkdirs()
        return File(d, filename)
    except:
        return None
