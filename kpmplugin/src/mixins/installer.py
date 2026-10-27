import os
import io
import json
import shutil
import traceback
import zipfile

from android_utils import log as _log, run_on_ui_thread
from client_utils import get_last_fragment, run_on_queue
from ui.bulletin import BulletinHelper
from org.telegram.messenger import NotificationCenter

from ..methods import _tr, _is_self_plugin, log_error


class InstallerMixin:

    def _is_eaf_url(self, url):
        return isinstance(url, str) and url.rstrip('/').endswith('.eaf')

    def _is_local_eaf(self, path):
        return isinstance(path, str) and path.endswith('.eaf')

    def _eaf_dir(self, plugin_id):
        return os.path.join(self.plugins_dir, "ElyxPlugins", plugin_id)

    def _resolve_eaf_dir(self, plugin_id):
        primary = self._eaf_dir(plugin_id)
        if os.path.isdir(primary):
            return primary
        fallback = os.path.join(self.plugins_dir, plugin_id)
        if os.path.isdir(fallback):
            return fallback
        return primary

    def _install_eaf(self, plugin_id, data, enable_after=False):
        target = self._resolve_eaf_dir(plugin_id)
        try:
            import shutil as _shutil
            if os.path.isdir(target):
                _shutil.rmtree(target, ignore_errors=True)
            os.makedirs(target, exist_ok=True)
            with zipfile.ZipFile(io.BytesIO(data)) as zf:
                zf.extractall(target)

            meta = self._parse_eaf_meta(target)

            dex_dir = os.path.join(target, "dex")
            if os.path.isdir(dex_dir):
                self._install_dex_files(plugin_id, dex_dir)

            wheels_dir = os.path.join(target, "wheels")
            if os.path.isdir(wheels_dir):
                self._install_wheels(plugin_id, wheels_dir)

            native_dir = os.path.join(target, "native")
            if os.path.isdir(native_dir):
                self._install_native(plugin_id, native_dir)

            return meta
        except Exception as e:
            log_error(e, context=f"installing .eaf {plugin_id}")
            raise

    def _parse_eaf_meta(self, eaf_dir):
        meta = {}
        _log(f"[KPM] _parse_eaf_meta: eaf_dir={eaf_dir}, isdir={os.path.isdir(eaf_dir)}")
        if eaf_dir and os.path.isdir(eaf_dir):
            try:
                _log(f"[KPM] _parse_eaf_meta: listing={os.listdir(eaf_dir)}")
            except Exception:
                pass
        meta_yml = os.path.join(eaf_dir, "meta.yml")
        if os.path.exists(meta_yml):
            _log(f"[KPM] _parse_eaf_meta: meta.yml found at root")
            try:
                import yaml
                with open(meta_yml, "r", encoding="utf-8") as f:
                    meta = yaml.safe_load(f) or {}
            except Exception as e:
                log_error(e, context="_parse_eaf_meta meta.yml")

        meta_json = os.path.join(eaf_dir, "meta.json")
        if not meta and os.path.exists(meta_json):
            try:
                with open(meta_json, "r", encoding="utf-8") as f:
                    meta = json.load(f)
            except Exception:
                pass

        if not meta:
            for refmap_name in ("refmap.yml", "refmap.yaml", "refmap.json"):
                refmap_path = os.path.join(eaf_dir, refmap_name)
                _log(f"[KPM] _parse_eaf_meta: checking refmap {refmap_path}, exists={os.path.exists(refmap_path)}")
                if not os.path.exists(refmap_path):
                    continue
                try:
                    with open(refmap_path, "r", encoding="utf-8") as f:
                        raw = f.read()
                    if refmap_name.endswith(".json"):
                        refmap = json.loads(raw)
                    else:
                        refmap = self._parse_yaml_simple(raw)
                    metainfo_ref = refmap.get("metainfo", "")
                    _log(f"[KPM] _parse_eaf_meta: refmap={refmap}, metainfo_ref={metainfo_ref}")
                    if metainfo_ref:
                        metainfo_ref = str(metainfo_ref).lstrip("./")
                        metainfo_path = os.path.join(eaf_dir, metainfo_ref)
                        _log(f"[KPM] _parse_eaf_meta: metainfo_path={metainfo_path}, exists={os.path.exists(metainfo_path)}")
                        if os.path.exists(metainfo_path):
                            with open(metainfo_path, "r", encoding="utf-8") as f:
                                raw = f.read()
                            if metainfo_path.endswith(".json"):
                                meta = json.loads(raw)
                            else:
                                meta = self._parse_yaml_simple(raw)
                            break
                except Exception as e:
                    _log_error(e, context=f"_parse_eaf_meta: refmap {refmap_name}")
                    pass

        if not meta:
            for sub in os.listdir(eaf_dir):
                sub_path = os.path.join(eaf_dir, sub)
                if os.path.isdir(sub_path):
                    sub_meta_yml = os.path.join(sub_path, "meta.yml")
                    if os.path.exists(sub_meta_yml):
                        try:
                            import yaml
                            with open(sub_meta_yml, "r", encoding="utf-8") as f:
                                meta = yaml.safe_load(f) or {}
                            if meta:
                                break
                        except Exception:
                            pass
                    sub_meta_json = os.path.join(sub_path, "meta.json")
                    if os.path.exists(sub_meta_json):
                        try:
                            with open(sub_meta_json, "r", encoding="utf-8") as f:
                                meta = json.load(f)
                            if meta:
                                break
                        except Exception:
                            pass

        return meta

    def _parse_yaml_simple(self, text):
        result = {}
        for line in text.splitlines():
            line = line.rstrip()
            if not line or line.startswith("#"):
                continue
            if ":" not in line:
                continue
            key, val = line.split(":", 1)
            key = key.strip()
            val = val.strip()
            if not key:
                continue
            if val.startswith('"') and val.endswith('"'):
                val = val[1:-1]
            elif val.startswith("'") and val.endswith("'"):
                val = val[1:-1]
            result[key] = val
        return result

    def _install_dex_files(self, plugin_id, dex_dir):
        try:
            from java import autoclass
            ByteBuffer = autoclass("java.nio.ByteBuffer")
            InMemoryDexClassLoader = autoclass("dalvik.system.InMemoryDexClassLoader")

            for fname in os.listdir(dex_dir):
                if fname.endswith(".dex"):
                    dex_path = os.path.join(dex_dir, fname)
                    with open(dex_path, "rb") as f:
                        dex_bytes = f.read()
                    buf = ByteBuffer.wrap(dex_bytes)
                    InMemoryDexClassLoader(buf, None)
                    _log(f"[KPM] Loaded .dex: {plugin_id}/{fname}")
        except Exception as e:
            log_error(e, context=f"loading .dex for {plugin_id}")

    def _install_wheels(self, plugin_id, wheels_dir):
        try:
            import subprocess
            for fname in os.listdir(wheels_dir):
                if fname.endswith(".whl"):
                    whl_path = os.path.join(wheels_dir, fname)
                    subprocess.run(
                        ["pip", "install", whl_path, "--break-system-packages", "-q"],
                        timeout=30
                    )
                    _log(f"[KPM] Installed wheel: {plugin_id}/{fname}")
        except Exception as e:
            log_error(e, context=f"installing wheels for {plugin_id}")

    def _install_native(self, plugin_id, native_dir):
        try:
            import ctypes
            for arch_dir in os.listdir(native_dir):
                arch_path = os.path.join(native_dir, arch_dir)
                if os.path.isdir(arch_path):
                    for fname in os.listdir(arch_path):
                        if fname.endswith(".so"):
                            so_path = os.path.join(arch_path, fname)
                            try:
                                ctypes.CDLL(so_path)
                                _log(f"[KPM] Loaded native: {plugin_id}/{arch_dir}/{fname}")
                            except Exception:
                                _log(f"[KPM] Error loading native {fname}: not loadable yet")
        except Exception as e:
            log_error(e, context=f"handling native for {plugin_id}")

    def _get_eaf_main_path(self, eaf_dir):
        for candidate in ["src/main.py", "main.py", "plugin.py"]:
            path = os.path.join(eaf_dir, candidate)
            if os.path.exists(path):
                return path
        return None

    def install_plugin_by_id(self, plugin_id, enable_after=False, silent=False):
        progress_ref = [None]
        if not silent:
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

        def do_install():
            try:
                if _is_self_plugin(plugin_id):
                    _log(f"[KPM] install_plugin_by_id: refusing self install of {plugin_id}")
                    if progress_ref[0]:
                        run_on_ui_thread(lambda: progress_ref[0].dismiss() if progress_ref[0].isShowing() else None)
                    if not silent:
                        self._show_self_install_dialog()
                    return
                if not self.plugins_list:
                    try:
                        self.store.refresh(force=True)
                    except Exception as e:
                        log_error(e, context="store refresh in install_plugin_by_id")
                if plugin_id not in self.plugins_list:
                    if progress_ref[0]:
                        run_on_ui_thread(lambda: progress_ref[0].dismiss() if progress_ref[0].isShowing() else None)
                    error_msg = f"Plugin {plugin_id} not found in store"
                    run_on_ui_thread(lambda: BulletinHelper.show_error(error_msg))
                    return

                plugin_info = self.plugins_list[plugin_id]
                if not self._ensure_plugin_version_supported(plugin_id, plugin_info):
                    if progress_ref[0]:
                        run_on_ui_thread(lambda: progress_ref[0].dismiss() if progress_ref[0].isShowing() else None)
                    return
                url = plugin_info.get("url") if isinstance(plugin_info, dict) else plugin_info

                remote = self.fetch_remote_bytes(url)

                blocking = str(self.get_setting("block_untrusted_plugins", True)).lower()
                if blocking not in ("false", "0", "none", "no", ""):
                    if not self._check_plugin_data(plugin_id, remote):
                        if progress_ref[0]:
                            run_on_ui_thread(lambda: progress_ref[0].dismiss() if progress_ref[0].isShowing() else None)
                        _log(f"[KPM] install_plugin_by_id: signature/hash check FAILED for {plugin_id}, blocked")
                        run_on_ui_thread(lambda: BulletinHelper.show_error(_tr("untrusted_blocked")))
                        self._show_untrusted_warning(plugin_id)
                        return

                if self._is_eaf_url(url):
                    meta = self._install_eaf(plugin_id, remote, enable_after)
                else:
                    target = os.path.join(self.plugins_dir, f"{plugin_id}.py")

                    with open(target, "wb") as f:
                        f.write(remote)

                if enable_after:
                    try:
                        from hook_utils import find_class
                        controller = find_class("com.exteragram.messenger.plugins.PluginsController").getInstance()
                        Boolean = find_class("java.lang.Boolean")
                        method = controller.getClass().getMethod(
                            "setPluginEnabled",
                            find_class("java.lang.String"),
                            Boolean.TYPE,
                            find_class("org.telegram.messenger.Utilities$Callback"),
                        )
                        _log(f"[KPM] enabling plugin after install: {plugin_id}")
                        method.invoke(controller, plugin_id, True, None)
                    except Exception as enable_e:
                        log_error(enable_e, context=f"enabling {plugin_id} after install")

                if not silent:
                    def notify_installed():
                        try:
                            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.pluginsUpdated)
                        except Exception as e:
                            log_error(e, context="notify pluginsUpdated")
                    run_on_ui_thread(notify_installed)
                if progress_ref[0]:
                    run_on_ui_thread(lambda: progress_ref[0].dismiss() if progress_ref[0].isShowing() else None)
            except Exception as e:
                if progress_ref[0]:
                    run_on_ui_thread(lambda: progress_ref[0].dismiss() if progress_ref[0].isShowing() else None)
                log_error(e, context=f"installing {plugin_id}")
                run_on_ui_thread(lambda: BulletinHelper.show_error(_tr("error_write").format(e)))

        run_on_queue(do_install)

    def _is_eaf_installed(self, plugin_id):
        eaf_dir = self._resolve_eaf_dir(plugin_id)
        if not os.path.isdir(eaf_dir):
            return False
        if (
            os.path.exists(os.path.join(eaf_dir, "src", "main.py")) or
            os.path.exists(os.path.join(eaf_dir, "main.py")) or
            os.path.exists(os.path.join(eaf_dir, "meta.yml")) or
            os.path.exists(os.path.join(eaf_dir, "meta.json"))
        ):
            return True
        meta = self._parse_eaf_meta(eaf_dir)
        return bool(meta and meta.get("id"))

    def _get_local_eaf_version(self, plugin_id):
        eaf_dir = self._resolve_eaf_dir(plugin_id)
        meta = self._parse_eaf_meta(eaf_dir)
        return meta.get("version", "0")

    def update_selected_plugins(self, plugin_ids, fn, on_plugin_done=None):
        def do_update():
            _log(f"[KPM] update_selected_plugins: starting, {len(plugin_ids)} plugins")
            updated = 0
            failed = 0
            first_incompatible = None
            for pid in plugin_ids:
                done_ok = False
                try:
                    plugin_info = self.plugins_list.get(pid)
                    if not plugin_info:
                        _log(f"[KPM] update_selected_plugins: {pid} not in plugins_list, skip")
                        failed += 1
                        continue
                    if not self._ensure_plugin_version_supported(pid, plugin_info, show_dialog=False):
                        _log(f"[KPM] update_selected_plugins: {pid} version incompatible, skip")
                        if first_incompatible is None:
                            first_incompatible = self._get_plugin_version_state(pid, plugin_info)
                            first_incompatible = (pid, first_incompatible[0], first_incompatible[1])
                        failed += 1
                        continue

                    url = plugin_info.get("url") if isinstance(plugin_info, dict) else plugin_info
                    local_is_eaf = self._is_eaf_installed(pid)
                    remote_is_eaf = self._is_eaf_url(url)
                    _log(f"[KPM] update_selected_plugins: {pid} url={url}, local_is_eaf={local_is_eaf}, remote_is_eaf={remote_is_eaf}")

                    if local_is_eaf:
                        local_version = self._get_local_eaf_version(pid)
                    else:
                        filename = f"{pid}.py"
                        local_path = os.path.join(self.plugins_dir, filename)
                        if not os.path.exists(local_path):
                            _log(f"[KPM] update_selected_plugins: {pid} local file not found, skip")
                            failed += 1
                            continue
                        with open(local_path, "rb") as f:
                            local_bytes = f.read()
                        if not local_bytes:
                            failed += 1
                            continue
                        local_metadata = self.parse_plugin_metadata(local_bytes)
                        local_version = local_metadata.get("version", "0")

                    _log(f"[KPM] update_selected_plugins: {pid} local_version={local_version}")
                    try:
                        remote = self.fetch_remote_bytes(url)
                        if remote_is_eaf:
                            import io, zipfile
                            with zipfile.ZipFile(io.BytesIO(remote)) as zf:
                                meta_content = None
                                for name in zf.namelist():
                                    if name.endswith("meta.yml") or name.endswith("meta.json"):
                                        meta_content = zf.read(name)
                                        break
                                if meta_content:
                                    import yaml
                                    try:
                                        remote_meta = yaml.safe_load(meta_content) or {}
                                    except Exception:
                                        remote_meta = json.loads(meta_content)
                                    remote_version = remote_meta.get("version", "0")
                                else:
                                    remote_version = "0"
                        else:
                            remote_metadata = self.parse_plugin_metadata(remote)
                            remote_version = remote_metadata.get("version", "0")
                    except Exception as e:
                        log_error(e, context=f"update_selected_plugins fetch {pid}")
                        failed += 1
                        continue

                    _log(f"[KPM] update_selected_plugins: {pid} remote_version={remote_version}")
                    if local_version != remote_version:
                        try:
                            if remote_is_eaf:
                                self._install_eaf(pid, remote)
                                stale_py = os.path.join(self.plugins_dir, f"{pid}.py")
                                if os.path.exists(stale_py):
                                    os.remove(stale_py)
                                    _log(f"[KPM] update_selected_plugins: {pid} removed stale {pid}.py")
                            else:
                                local_path = os.path.join(self.plugins_dir, f"{pid}.py")
                                with open(local_path, "wb") as f:
                                    f.write(remote)
                                eaf_dir = self._resolve_eaf_dir(pid)
                                if os.path.isdir(eaf_dir):
                                    import shutil as _shutil
                                    _shutil.rmtree(eaf_dir, ignore_errors=True)
                                    _log(f"[KPM] update_selected_plugins: {pid} removed stale eaf dir {eaf_dir}")
                            updated += 1
                            done_ok = True
                            _log(f"[KPM] update_selected_plugins: {pid} updated successfully")
                        except Exception as e:
                            log_error(e, context=f"update_selected_plugins install {pid}")
                            failed += 1
                    else:
                        done_ok = True
                        _log(f"[KPM] Plugin {pid} is already up to date")
                except Exception as e:
                    log_error(e, context=f"update_selected_plugins updating {pid}")
                    failed += 1
                if on_plugin_done:
                    try:
                        run_on_ui_thread(lambda p=pid, ok=done_ok: on_plugin_done(p, ok))
                    except Exception:
                        pass

            msg = _tr("updated_stats").format(updated, failed)
            _log(f"[KPM] update_selected_plugins: done. updated={updated}, failed={failed}")
            try:
                fn()
            except Exception as e:
                log_error(e, context="update_selected_plugins fn callback")
            if first_incompatible is not None:
                run_on_ui_thread(lambda pid=first_incompatible[0], min_version=first_incompatible[1], client_version=first_incompatible[2]:
                    self._show_requirements_dialog(pid, min_version, client_version))
            run_on_ui_thread(lambda: BulletinHelper.show_error(msg))

        run_on_queue(do_update)

    def update_installed_from_store(self):
        if not self.plugins_list:
            self.refresh_store()
        if not self.plugins_list:
            return
        updated = 0
        skipped = 0
        failed = 0
        installed_ids = self.list_installed_plugins()
        for pid in installed_ids:
            plugin_info = self.plugins_list.get(pid)
            if not plugin_info:
                continue
            url = plugin_info.get("url") if isinstance(plugin_info, dict) else plugin_info
            remote_is_eaf = self._is_eaf_url(url)

            if self._is_eaf_installed(pid):
                local_version = self._get_local_eaf_version(pid)
            else:
                local_path = os.path.join(self.plugins_dir, f"{pid}.py")
                if not os.path.exists(local_path):
                    continue
                local_bytes = self.read_file_bytes(local_path)
                if not local_bytes:
                    continue
                local_metadata = self.parse_plugin_metadata(local_bytes)
                local_version = local_metadata.get("version", "1.0")

            remote_version = str(plugin_info.get("version", "0"))
            if remote_version != local_version:
                try:
                    remote = self.fetch_remote_bytes(url)
                    if remote_is_eaf:
                        self._install_eaf(pid, remote)
                        stale_py = os.path.join(self.plugins_dir, f"{pid}.py")
                        if os.path.exists(stale_py):
                            os.remove(stale_py)
                    else:
                        local_path = os.path.join(self.plugins_dir, f"{pid}.py")
                        with open(local_path, "wb") as f:
                            f.write(remote)
                        eaf_dir = self._resolve_eaf_dir(pid)
                        if os.path.isdir(eaf_dir):
                            import shutil as _shutil
                            _shutil.rmtree(eaf_dir, ignore_errors=True)
                    updated += 1
                except Exception:
                    failed += 1
            else:
                skipped += 1
        run_on_ui_thread(lambda: BulletinHelper.show_error(_tr("updated_stats").format(updated, skipped, failed)))
