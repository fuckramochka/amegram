import os
import traceback

from android_utils import log as _log, run_on_ui_thread
from client_utils import get_last_fragment, run_on_queue
from ui.bulletin import BulletinHelper
from com.exteragram.messenger.plugins import PluginsController

from ..methods import __version__, _tr, _fix_plurals, _is_self_plugin, log_error
from ..sbroka import normalize_collection_plugins
from ..store import Store


class DialogsMixin:

    def show_plugin_info_and_install(self, plugin_id, enable_after=False):
        _log(f"[KPM] [Deeplink] show_plugin_info_and_install called for {plugin_id!r}, enable_after={enable_after}")
        if _is_self_plugin(plugin_id):
            _log(f"[KPM] [Deeplink] show_plugin_info_and_install: refusing self install of {plugin_id}")
            self._show_self_install_dialog()
            return
        def load_info():
            _log(f"[KPM] [Deeplink] load_info started for {plugin_id!r}")
            try:
                try:
                    self.update_store_mirror()
                except Exception:
                    pass
                if not self.store.plugins_list:
                    _log(f"[KPM] [Deeplink] plugins_list is empty, refreshing store...")
                    try:
                        self.store.refresh(force=True)
                    except Exception as e:
                        log_error(e, context="store refresh in show_plugin_info_and_install")

                target_pid = plugin_id
                plugin_info = self.store.plugins_list.get(plugin_id)
                if not plugin_info and self.store.plugins_list:
                    for k, v in self.store.plugins_list.items():
                        if str(k).strip().lower() == str(plugin_id).strip().lower():
                            _log(f"[KPM] [Deeplink] matched case-insensitive {plugin_id!r} -> {k!r}")
                            target_pid = k
                            plugin_info = v
                            break

                if not plugin_info:
                    _log(f"[KPM] [Deeplink] Plugin {plugin_id!r} not in plugins_list (total={len(self.store.plugins_list)}), attempting force refresh...")
                    try:
                        self.store.refresh(force=True)
                        plugin_info = self.store.plugins_list.get(plugin_id)
                        if not plugin_info and self.store.plugins_list:
                            for k, v in self.store.plugins_list.items():
                                if str(k).strip().lower() == str(plugin_id).strip().lower():
                                    target_pid = k
                                    plugin_info = v
                                    break
                    except Exception as e:
                        log_error(e, context="store fallback refresh in show_plugin_info_and_install")

                if not plugin_info:
                    error_msg = f"Plugin {plugin_id} not found in store.json"
                    _log(f"[KPM] [Deeplink] {error_msg}. Available IDs: {list(self.store.plugins_list.keys())[:15]}")
                    run_on_ui_thread(lambda: BulletinHelper.show_error(error_msg))
                    return
                if not self._ensure_plugin_version_supported(target_pid, plugin_info):
                    return

                url = plugin_info.get("url") if isinstance(plugin_info, dict) else plugin_info
                if not url:
                    error_msg = f"No URL found in store.json for plugin {target_pid}"
                    _log(f"[KPM] [Deeplink] {error_msg}")
                    run_on_ui_thread(lambda: BulletinHelper.show_error(error_msg))
                    return

                _log(f"[KPM] [Deeplink] Installing {target_pid} from store.json URL: {url}")

                info = self.get_plugin_full_info(target_pid)
                if not info:
                    run_on_ui_thread(lambda: BulletinHelper.show_error(f"Failed to load {target_pid}"))
                    return

                if not info.get('url') or info['url'] != url:
                    _log(f"[KPM] [Deeplink] Warning: URL mismatch, using store.json URL: {url}")
                    info['url'] = url

                dependencies = info.get("dependencies", [])

                def install_main_plugin():
                    progress_ref = [None]
                    def show_progress():
                        try:
                            from org.telegram.ui.ActionBar import AlertDialog
                            fragment = get_last_fragment()
                            if fragment:
                                ctx = fragment.getParentActivity()
                                if ctx:
                                    pd = AlertDialog(ctx, 3)
                                    pd.setCanCancel(True)
                                    pd.show()
                                    progress_ref[0] = pd
                        except Exception:
                            pass
                    run_on_ui_thread(show_progress)

                    plugin_url = info.get('url')
                    temp_ext = ".plugin"
                    if plugin_url:
                        clean_url = str(plugin_url).split("?")[0].rstrip("/")
                        for ext_cand in (".eaf", ".elyx", ".plugin", ".py", ".zip"):
                            if clean_url.endswith(ext_cand):
                                temp_ext = ext_cand
                                break
                    temp_path = os.path.join(self.plugins_dir, f".temp_{plugin_id}{temp_ext}")
                    try:
                        if not plugin_url:
                            raise Exception(f"No URL in info for {plugin_id}")
                        _log(f"[KPM] Downloading plugin from URL: {plugin_url}")
                        plugin_content = Store.fetch_bytes(plugin_url)
                        with open(temp_path, 'wb') as f:
                            f.write(plugin_content)

                        def open_install_dialog():
                            fragment = get_last_fragment()
                            if not fragment:
                                return
                            PluginsController.getInstance().showInstallDialog(fragment, temp_path, True)
                            _log(f"[KPM] Opened Extera install dialog for {plugin_id}")

                        if progress_ref[0]:
                            run_on_ui_thread(lambda: progress_ref[0].dismiss() if progress_ref[0].isShowing() else None)
                        run_on_ui_thread(open_install_dialog)
                    except Exception as e:
                        if progress_ref[0]:
                            run_on_ui_thread(lambda: progress_ref[0].dismiss() if progress_ref[0].isShowing() else None)
                        log_error(e, context=f"Error showing Extera install dialog for {plugin_id}")
                        if os.path.exists(temp_path):
                            try:
                                os.remove(temp_path)
                            except Exception:
                                pass
                        run_on_ui_thread(lambda msg=str(e): BulletinHelper.show_error(f"Error: {msg}"))

                if dependencies:
                    _log(f"[KPM] Found {len(dependencies)} dependencies for {plugin_id}: {dependencies}")
                install_main_plugin()

            except Exception as e:
                log_error(e, context=f"loading plugin info for {plugin_id}")

        run_on_queue(load_info)

    def _show_requirements_dialog(self, plugin_id, min_version, client_version):
        def show():
            from ..views import show_requirements_dialog
            show_requirements_dialog(plugin_id, min_version, client_version)
        run_on_ui_thread(show)

    def _show_self_install_dialog(self):
        def show():
            from ..views import show_self_install_dialog
            show_self_install_dialog(self)
        run_on_ui_thread(show)

    def _show_untrusted_warning(self, plugin_id):
        def show():
            from ..views import show_untrusted_warning
            show_untrusted_warning(self, plugin_id)
        run_on_ui_thread(show)

    def _get_plugin_info_text(self):
        plugin_count = len(self.plugins_list) if self.plugins_list else 0
        from ..methods import _fix_plurals
        return _fix_plurals(_tr("plugin_info_text").format(__version__, plugin_count), plugin_count)

    def _show_about_dialog(self):
        from ..views import show_about_dialog
        show_about_dialog(self)

    def _show_md3_loading(self, duration_ms=3000):
        from ..views import show_md3_loading
        return show_md3_loading(duration_ms)

    def _show_version_picker(self, plugin_id, versions):
        def show():
            from ..views import show_version_picker
            show_version_picker(self, plugin_id, versions)
        run_on_ui_thread(show)

    def _get_ignored_updates(self):
        try:
            raw = self.get_setting("ignored_updates", [])
            return list(raw) if isinstance(raw, list) else []
        except Exception:
            return []

    def _set_ignored_updates(self, ids):
        try:
            self.set_setting("ignored_updates", list(ids))
        except Exception as e:
            log_error(e, context="_set_ignored_updates")

    def ignore_update(self, plugin_id):
        try:
            ignored = self._get_ignored_updates()
            if plugin_id not in ignored:
                ignored.append(plugin_id)
                self._set_ignored_updates(ignored)
                _log(f"[KPM] Ignored update for {plugin_id}")
                run_on_ui_thread(lambda: BulletinHelper.show_info(_tr("update_ignored").format(plugin_id)))
        except Exception as e:
            log_error(e, context=f"ignore_update for {plugin_id}")

    def unignore_update(self, plugin_id):
        try:
            ignored = self._get_ignored_updates()
            if plugin_id in ignored:
                ignored.remove(plugin_id)
                self._set_ignored_updates(ignored)
                _log(f"[KPM] Unignored update for {plugin_id}")
                run_on_ui_thread(lambda: BulletinHelper.show_info(_tr("update_unignored").format(plugin_id)))
        except Exception as e:
            log_error(e, context=f"unignore_update for {plugin_id}")

    def _get_plugin_display_name_from_file(self, plugin_id):
        try:
            from ..elyx_parser import load_plugin_metadata
            plugins_dir = PluginsController.getInstance().getPluginsDir()
            for filename in os.listdir(plugins_dir):
                if filename.endswith('.eaf'):
                    path = os.path.join(plugins_dir, filename)
                    try:
                        meta = load_plugin_metadata(path)
                        if meta.get('id') == plugin_id and meta.get('name'):
                            return meta['name']
                    except Exception:
                        pass
        except Exception:
            pass
        return plugin_id

    def _show_ignored_updates_picker(self):
        if not self.plugins_list:
            try:
                self.refresh_store()
            except Exception:
                pass
        installed = self.list_installed_plugins()
        _log(f"[KPM] ignored updates picker: installed={installed}")
        if not installed:
            run_on_ui_thread(lambda: BulletinHelper.show_error(_tr("no_installed")))
            return
        ignored = set(self._get_ignored_updates())
        _log(f"[KPM] ignored updates: {ignored}")
        metadata = {}
        for pid in installed:
            info = self.plugins_list.get(pid)
            if not info or not isinstance(info, dict):
                continue
            name = info.get('name') or pid
            desc = info.get('description', '')
            try:
                from ..methods import _get_localized_description
                loc_desc = _get_localized_description(info, self.plugins_dir, pid)
                if loc_desc:
                    desc = loc_desc
            except Exception:
                pass
            icon = info.get('icon', '')
            version = info.get('version', '')
            metadata[pid] = {
                'name': name,
                'icon': icon,
                'version': version,
                'author': info.get('author', ''),
                'description': desc,
            }

        _log(f"[KPM] ignored picker metadata: {len(metadata)} plugins")
        if not metadata:
            run_on_ui_thread(lambda: BulletinHelper.show_error(_tr("no_installed")))
            return
        from ..views import show_ignored_updates_picker
        show_ignored_updates_picker(self, list(metadata.keys()), metadata, ignored)

    def open_update_dialog(self):
        loading_dialog = self._show_md3_loading(3000)

        def load_and_show():
            try:
                installed = self.list_installed_plugins()
                if not installed:
                    if loading_dialog:
                        try:
                            run_on_ui_thread(lambda: loading_dialog.dismiss() if loading_dialog.isShowing() else None)
                        except Exception:
                            pass
                    run_on_ui_thread(lambda: BulletinHelper.show_error(_tr("no_installed")))
                    return
                self.refresh_store(force=True, has_bulletin=False, md3_anim=False)

                ignored = set(self._get_ignored_updates())
                updates = []
                display_names = {"d": {}, "ic": {}, "vers": {}}

                for pid in installed:
                    try:
                        plugin_info = self.plugins_list.get(pid)
                        if not plugin_info:
                            _log(f"[KPM] update check: {pid} NOT in plugins_list, skip")
                            continue

                        url = plugin_info.get("url") if isinstance(plugin_info, dict) else plugin_info

                        eaf_dir = os.path.join(self.plugins_dir, "ElyxPlugins", pid)
                        if not os.path.isdir(eaf_dir):
                            eaf_dir = os.path.join(self.plugins_dir, pid)

                        local_version = None
                        if os.path.isdir(eaf_dir) and (
                            os.path.exists(os.path.join(eaf_dir, "refmap.yml")) or
                            os.path.exists(os.path.join(eaf_dir, "refmap.json")) or
                            os.path.exists(os.path.join(eaf_dir, "refmap.yaml")) or
                            os.path.exists(os.path.join(eaf_dir, "meta.yml")) or
                            os.path.exists(os.path.join(eaf_dir, "meta.json"))
                        ):
                            meta = self._parse_eaf_meta(eaf_dir)
                            local_version = meta.get("version")
                            _log(f"[KPM] update check: {pid} EAF meta={meta}, local_version={local_version}")

                        if local_version is None:
                            local_path = os.path.join(self.plugins_dir, f"{pid}.py")
                            if not os.path.exists(local_path):
                                _log(f"[KPM] update check: {pid} no eaf dir and no {pid}.py, skip")
                                continue

                            local_bytes = self.read_file_bytes(local_path)
                            if not local_bytes:
                                _log(f"[KPM] update check: {pid} {pid}.py empty, skip")
                                continue

                            local_metadata = self.parse_plugin_metadata(local_bytes)
                            local_version = local_metadata.get("version") or "1.0"
                            _log(f"[KPM] update check: {pid} py local_version={local_version}")

                        remote_version = str(plugin_info.get("version", "0"))
                        _log(f"[KPM] update check: {pid} (local='{local_version}', remote='{remote_version}') -> diff={remote_version != local_version}")

                        if remote_version != local_version:
                            if pid in ignored:
                                _log(f"[KPM] [IGNORED UPDATE] Plugin '{pid}' has update available ({local_version} -> {remote_version}), but is in ignored list. Skipping.")
                                continue
                            updates.append(pid)
                            display_names["d"][pid] = plugin_info.get("name", pid)
                            display_names["ic"][pid] = plugin_info.get("icon")
                            display_names["vers"][pid] = (local_version, remote_version)
                    except Exception as e:
                        log_error(e, context=f"Error checking updates for {pid}")
                        continue

                _log(f"[KPM] update check done: installed={len(installed)}, updates found={len(updates)}")

                def show_dialog():
                    if loading_dialog:
                        try:
                            if loading_dialog.isShowing():
                                loading_dialog.dismiss()
                        except Exception:
                            pass
                    if not updates:
                        BulletinHelper.show_error(_tr("no_installed"))
                        return
                    self._show_plugin_list(_tr("update_plugins"), updates, display_names, self.UPDATE)

                run_on_ui_thread(show_dialog)
            except Exception as e:
                log_error(e, context="Error loading updates")
                if loading_dialog:
                    try:
                        run_on_ui_thread(lambda: loading_dialog.dismiss() if loading_dialog.isShowing() else None)
                    except Exception:
                        pass

        run_on_queue(load_and_show)

    def open_install_dialog(self):
        def load():
            ids = self.list_available_ids()
            if not ids:
                self.refresh_store(force=True, has_bulletin=False)
                ids = self.list_available_ids()
            names = {}
            for pid in ids:
                names[pid] = self.get_plugin_display_name(pid)
            from ..views import show_plugin_list_fragment
            run_on_ui_thread(lambda: show_plugin_list_fragment(
                self, _tr("install_title"), ids, names, update_mode=False))
            if self.get_setting("auto_refresh_catalog", True):
                run_on_queue(lambda: self.refresh_store(force=True, has_bulletin=False, md3_anim=False))
        run_on_queue(load)

    def _show_plugin_list(self, title, ids, display_names, type=0):
        from ..views import show_plugin_list_fragment
        show_plugin_list_fragment(self, title, ids, display_names, update_mode=(type == 1))

    def _handle_deep_link_install(self, plugin_id, version=None):
        if not plugin_id:
            _log("[KPM] [Deeplink] _handle_deep_link_install called with empty plugin_id")
            return
        plugin_id = str(plugin_id).strip()
        _log(f"[KPM] [Deeplink] _handle_deep_link_install: plugin_id={plugin_id!r}, version={version!r}")
        if _is_self_plugin(plugin_id):
            _log(f"[KPM] [Deeplink] _handle_deep_link_install: refusing self install of {plugin_id}")
            self._show_self_install_dialog()
            return
        try:
            self.update_store_mirror()
            _log(f"[KPM] [Deeplink] current enabled mirrors: {getattr(self.store, 'enabled_mirrors', None)}")
        except Exception as me:
            log_error(me, context="_handle_deep_link_install update_store_mirror")
        info = self.store.get_plugin_info(plugin_id)
        target_pid = plugin_id
        if not info and self.store.plugins_list:
            for k, v in self.store.plugins_list.items():
                if str(k).strip().lower() == plugin_id.lower():
                    _log(f"[KPM] [Deeplink] matched case-insensitive {plugin_id!r} -> {k!r}")
                    target_pid = k
                    info = v
                    break

        if not info or not self.store.plugins_list:
            try:
                _log(f"[KPM] [Deeplink] plugin {plugin_id!r} not found in cache (len={len(self.store.plugins_list)}), refreshing store force=True...")
                ok = self.store.refresh(force=True)
                _log(f"[KPM] [Deeplink] _handle_deep_link_install: sync refresh ok={ok}, plugins={len(self.store.plugins_list)}")
            except Exception as e:
                log_error(e, context="_handle_deep_link_install sync refresh")
            info = self.store.get_plugin_info(plugin_id)
            if not info and self.store.plugins_list:
                for k, v in self.store.plugins_list.items():
                    if str(k).strip().lower() == plugin_id.lower():
                        _log(f"[KPM] [Deeplink] matched case-insensitive after refresh: {plugin_id!r} -> {k!r}")
                        target_pid = k
                        info = v
                        break

        if not info:
            _log(f"[KPM] [Deeplink] Plugin {plugin_id!r} NOT FOUND in store after refresh. Total in store: {len(self.store.plugins_list)}")
            run_on_ui_thread(lambda: BulletinHelper.show_error(_tr("plugin_not_found")))
            return
        if not self._ensure_plugin_version_supported(target_pid, info):
            return
        if not version:
            _log(f"[KPM] [Deeplink] No version specified, showing info and install for {target_pid}")
            self.show_plugin_info_and_install(target_pid)
            return
        if isinstance(info, dict) and str(version) == str(info.get("version", "")):
            _log(f"[KPM] [Deeplink] Requested version {version} matches current version, showing install for {target_pid}")
            self.show_plugin_info_and_install(target_pid)
            return
        url, expected_hash = None, None
        if isinstance(info, dict):
            legacy = info.get("legacy_version", {})
            if isinstance(legacy, dict):
                vi = legacy.get(version)
                if isinstance(vi, dict):
                    url = vi.get("url", "")
                    expected_hash = vi.get("hash", "")
                elif isinstance(vi, str):
                    url = vi
        if not url:
            _log(f"[KPM] [Deeplink] Version {version} not found for {target_pid}. Legacy versions: {info.get('legacy_version') if isinstance(info, dict) else None}")
            run_on_ui_thread(lambda: BulletinHelper.show_error(_tr("version_not_found").format(version)))
            return
        _log(f"[KPM] [Deeplink] Installing specific version {version} for {target_pid} from {url}")
        from ..views import _install_version
        run_on_queue(lambda: _install_version(self, target_pid, url, expected_hash))
        if self.get_setting("auto_refresh_catalog", True):
            run_on_queue(lambda: self.refresh_store(force=True, has_bulletin=False, md3_anim=False))

    def _show_import_collection_dialog(self):
        _log("[KPM] _show_import_collection_dialog: opening file picker")
        try:
            from android.app import Activity
            from android.content import Intent
            fragment = get_last_fragment()
            if not fragment:
                _log("[KPM] import dialog error: no fragment")
                return
            activity = fragment.getParentActivity()
            if not activity:
                _log("[KPM] import dialog error: no activity")
                return
            intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.setType("*/*")
            fragment.startActivityForResult(intent, 4242)
            _log("[KPM] import dialog: startActivityForResult(4242) sent")
        except Exception as e:
            log_error(e, context="_show_import_collection_dialog")

    def _show_import_collection_designer(self, metadata, plugin_ids):
        from ..views import show_import_collection_designer
        show_import_collection_designer(self, metadata, plugin_ids)

    def _import_collection_plugins(self, metadata, plugin_ids):
        ids = normalize_collection_plugins(plugin_ids)
        _log(f"[KPM] _import_collection_plugins: {len(ids)} ids to install")
        if not ids:
            BulletinHelper.show_error(_tr("collection_plugins_empty"))
            return

        def do_install():
            import time
            import gc
            _log(f"[KPM] import_collection_plugins do_install: starting {len(ids)} installs")
            from ..views import show_import_progress
            ctrl = show_import_progress(len(ids))
            ok = 0
            fail = 0
            for i, pid in enumerate(ids, 1):
                try:
                    name = pid
                    if isinstance(self.store.plugins_list.get(pid), dict):
                        name = self.store.plugins_list[pid].get("name", pid)
                    _log(f"[KPM] import installing ({i}/{len(ids)}): {pid}")
                    run_on_ui_thread(lambda c=i, n=name: ctrl.update(c, n))
                    self.install_plugin_by_id(pid, silent=True)
                    ok += 1
                    _log(f"[KPM] import installed ({i}/{len(ids)}): {pid}")
                    gc.collect()
                    time.sleep(0.5)
                except Exception as e:
                    fail += 1
                    log_error(e, context=f"import_collection_plugins ({i}/{len(ids)}): {pid}")
            _log(f"[KPM] import done: ok={ok}, fail={fail}")
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

    def _show_export_collection_dialog(self):
        _log("[KPM] _show_export_collection_dialog called")
        try:
            from ..views import show_export_picker
            installed = self.list_installed_plugins()
            store_ids = list(self.store.plugins_list.keys())

            installed_in_store = [pid for pid in installed if pid in self.store.plugins_list]

            _log(f"[KPM] export: {len(installed_in_store)} installed in store, {len(store_ids)} total in store")

            def on_export(selected_ids):
                _log(f"[KPM] export on_export: {len(selected_ids)} selected")
                if not selected_ids:
                    run_on_ui_thread(lambda: BulletinHelper.show_error(_tr("collection_plugins_empty")))
                    return
                from ..views import show_export_metadata
                show_export_metadata(self, selected_ids)

            show_export_picker(self, installed_in_store, store_ids, on_export)
        except Exception as e:
            BulletinHelper.show_error(f"[KPM] export error: {e}")
            log_error(e, context="_show_export_collection_dialog")

    def _handle_kpm_file_open(self, message_object):
        """Handle .kpm file opened from chat"""
        try:
            from ..hooks.kpm_file import _get_message_file_path
            file_path = _get_message_file_path(message_object)
            if not file_path:
                _log("[KPM] kpm file: could not get path")
                return
            _log(f"[KPM] kpm file opened: {file_path}")
            from ..sbroka import import_collection
            import_collection(self, file_path, auto_install=False)
        except Exception as e:
            log_error(e, context="_handle_kpm_file_open")

    def _handle_collection_import_result(self, request_code, result_code, data):
        """Handle result from ACTION_OPEN_DOCUMENT for .kpm import"""
        _log(f"[KPM] activity result: request={request_code}, result={result_code}, data={data}")
        if request_code != 4242:
            return False
        if result_code != -1:
            _log(f"[KPM] import result not OK: {result_code}")
            return False
        if not data:
            _log("[KPM] import result: no data")
            return False
        try:
            from android.net import Uri
            uri = data.getData()
            if not uri:
                return False
            from java.io import File, FileInputStream, FileOutputStream

            fragment = get_last_fragment()
            if not fragment:
                return False
            activity = fragment.getParentActivity()
            if not activity:
                return False
            resolver = activity.getContentResolver()
            if not resolver:
                return False

            fd = resolver.openFileDescriptor(uri, "r")
            if not fd:
                return False
            input_stream = FileInputStream(fd.getFileDescriptor())
            temp_file = File.createTempFile("kpm_import_", ".kpm", activity.getCacheDir())
            temp_file.deleteOnExit()
            output_stream = FileOutputStream(temp_file)
            buf = bytearray(8192)
            while True:
                n = input_stream.read(buf)
                if n <= 0:
                    break
                output_stream.write(buf, 0, n)
            input_stream.close()
            output_stream.close()
            fd.close()
            file_path = temp_file.getAbsolutePath()
            _log(f"[KPM] import uri copied to: {file_path}")
            if file_path and os.path.exists(file_path):
                from ..sbroka import import_collection
                import_collection(self, file_path, auto_install=False)
                return True
        except Exception as e:
            log_error(e, context="_handle_collection_import_result")
        return False
