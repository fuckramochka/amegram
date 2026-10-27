import json
import os


def normalize_collection_plugins(raw_plugins):
    result = []
    seen = set()
    for item in raw_plugins or []:
        plugin_id = ""
        if isinstance(item, dict):
            plugin_id = str(item.get("id") or item.get("plugin_id") or "").strip()
        else:
            text = str(item or "").strip()
            if text.startswith("tg://kpm_install?plugin="):
                from urllib.parse import urlparse, parse_qs
                parsed = parse_qs(urlparse(text).query)
                plugin_id = (parsed.get("plugin") or [None])[0]
            else:
                plugin_id = text
        if plugin_id == "kangel_plugins_manager":
            continue
        if plugin_id and plugin_id not in seen:
            seen.add(plugin_id)
            result.append(plugin_id)
    return result


def build_collection_payload(metadata, plugin_ids):
    clean = {
        "name": str((metadata or {}).get("name") or "").strip(),
        "author": str((metadata or {}).get("author") or "").strip(),
        "description": str((metadata or {}).get("description") or "").strip(),
        "icon": str((metadata or {}).get("icon") or "").strip(),
    }
    ids = normalize_collection_plugins(plugin_ids)
    return {
        "metadata": clean,
        "plugins": [f"tg://kpm_install?plugin={pid}" for pid in ids],
        "format": "kpm_collection_v2",
    }


def load_collection_file(file_path):
    resolved = file_path
    if not os.path.isabs(file_path):
        if os.path.exists(file_path):
            resolved = os.path.abspath(file_path)
    if not os.path.exists(resolved):
        raise FileNotFoundError(f"Collection not found: {file_path}")
    with open(resolved, "r", encoding="utf-8") as f:
        collection = json.load(f)
    metadata = dict(collection.get("metadata") or {})
    ids = normalize_collection_plugins(
        collection.get("plugins") or collection.get("links") or []
    )
    return metadata, ids, collection


def export_collection(plugin_instance, metadata, plugin_ids):
    from ui.bulletin import BulletinHelper
    from .methods import _tr, log_error
    collection = build_collection_payload(metadata, plugin_ids)
    name = collection["metadata"].get("name") or "kpm_collection"
    safe = "".join(ch if ch.isalnum() or ch in "-_ " else "_" for ch in name).strip() or "kpm_collection"
    path = os.path.join(plugin_instance.plugins_dir, f"{safe}.kpm")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(collection, f, ensure_ascii=False, indent=4)
    try:
        from org.telegram.messenger import UserConfig
        from .methods import _tr, log
        from .compat.sender import send_document_compat
        cap_name = collection["metadata"].get("name") or "My Collection"
        cap_author = collection["metadata"].get("author") or ""
        caption = f'Pack "{cap_name}"'
        if cap_author:
            caption += f" by {cap_author}"
        caption += " — Install from Kangel Plugins Manager"

        account = UserConfig.selectedAccount if UserConfig else 0
        user_id = UserConfig.getInstance(account).getClientUserId() if UserConfig else 0
        if user_id:
            send_document_compat(user_id, path, caption=caption)
            BulletinHelper.show_success(_tr("collection_exported"))
        else:
            BulletinHelper.show_error("Could not determine user ID")
    except Exception as e:
        log_error(e, context="export_collection")
        BulletinHelper.show_error(str(e))


def import_collection(plugin_instance, file_path, auto_install=False):
    from ui.bulletin import BulletinHelper
    from .methods import _tr, _fix_plurals, log, log_error
    log(f"[KPM] import_collection called: file={file_path}, auto_install={auto_install}")
    try:
        metadata, ids, _ = load_collection_file(file_path)
        log(f"[KPM] import_collection loaded: ids={ids[:5]}... total={len(ids)}")
        if auto_install:
            if "kangel_plugins_manager" in ids:
                ids.remove("kangel_plugins_manager")
            if ids:
                from client_utils import run_on_queue
                def do_install():
                    import time
                    import gc
                    from .views import show_import_progress
                    ctrl = show_import_progress(len(ids))
                    ok = 0
                    fail = 0
                    for i, pid in enumerate(ids, 1):
                        try:
                            name = pid
                            if isinstance(plugin_instance.store.plugins_list.get(pid), dict):
                                name = plugin_instance.store.plugins_list[pid].get("name", pid)
                            from android_utils import run_on_ui_thread
                            run_on_ui_thread(lambda c=i, n=name: ctrl.update(c, n))
                            plugin_instance.install_plugin_by_id(pid, silent=True)
                            ok += 1
                            gc.collect()
                            time.sleep(0.5)
                        except Exception as e_item:
                            log_error(e_item, context=f"import_collection install plugin {pid}")
                            fail += 1
                    from android_utils import run_on_ui_thread
                    run_on_ui_thread(lambda: ctrl.dismiss())
                    def finish():
                        from org.telegram.messenger import NotificationCenter
                        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.pluginsUpdated)
                        if fail:
                            BulletinHelper.show_info(_tr("import_done_fail").format(ok, fail))
                        else:
                            BulletinHelper.show_success(_tr("import_done").format(ok))
                    run_on_ui_thread(finish)
                run_on_queue(do_install)
            else:
                BulletinHelper.show_info(_tr("auto_install_none"))
        else:
            plugin_instance._show_import_collection_designer(metadata, ids)
    except Exception as e:
        log_error(e, context="import_collection")
        BulletinHelper.show_error(str(e))
