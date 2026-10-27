import os
import re
import time
import hashlib
import traceback

from android_utils import log as _log, run_on_ui_thread
from client_utils import run_on_queue
from ui.bulletin import BulletinHelper

from ..methods import _tr, _fix_plurals, _normalize_min_version, _is_self_plugin, log_error


class StoreOpsMixin:

    def _get_submit_bot_id(self):
        try:
            choice = int(self.get_setting("submit_bot_choice", 0) or 0)
            if choice == 0:
                return 8927478894
            elif choice == 1:
                return 8683783103
            elif choice == 2:
                custom = str(self.get_setting("submit_bot_custom_username", "") or "").strip().lstrip("@")
                if custom:
                    try:
                        from org.telegram.messenger import ContactsController, UserConfig
                        current_account = UserConfig.selectedAccount
                        user = ContactsController.getInstance(current_account).getUser(custom)
                        if not user:
                            user = ContactsController.getInstance(current_account).getUser(custom.lower())
                        if user:
                            return user.id
                    except Exception:
                        pass
                    return 0
            return 8927478894
        except Exception:
            return 8927478894

    def load_cache(self):
        self.store._load_cache()

    def save_cache(self, sha):
        self.store._save_cache(sha)

    def clear_cache(self):
        self.store.clear_cache()
        from ui.bulletin import BulletinHelper
        run_on_ui_thread(lambda: BulletinHelper.show_info(_tr("cache_cleared")))

    def update_store_mirror(self):
        stored_mirrors = self.get_setting("enabled_store_mirrors", None)
        valid_mirrors = ["kangel", "github", "codeberg", "gitverse"]
        if stored_mirrors is not None and isinstance(stored_mirrors, (list, tuple)):
            enabled_list = [m for m in stored_mirrors if m in valid_mirrors]
        else:
            enabled_list = list(valid_mirrors)
        if not enabled_list:
            enabled_list = ["kangel"]

        if hasattr(self, "store") and self.store:
            if getattr(self.store, "enabled_mirrors", None) != enabled_list:
                self.store.enabled_mirrors = enabled_list
                _log(f"[KPM] update_store_mirror: enabled_mirrors set to {enabled_list}")
        from ..store import Store
        Store._active_enabled_mirrors = list(enabled_list)

    def refresh_store(self, force=False, has_bulletin=True, md3_anim=True):
        _log(f"[KPM] refresh_store called: force={force}, md3_anim={md3_anim}")
        self.update_store_mirror()
        if has_bulletin:
            from ui.bulletin import BulletinHelper
            run_on_ui_thread(lambda: BulletinHelper.show_info(_tr("please_wait")))
        _log(f"[KPM] refresh_store: checking cache valid (mirrors={getattr(self.store, 'enabled_mirrors', None)})")
        cache_valid = self.store.is_cache_valid()
        _log(f"[KPM] refresh_store: cache_valid={cache_valid}, force={force}")
        ctrl = [None]
        if md3_anim and (force or not cache_valid):
            try:
                from ..views import show_md3_loading
                ctrl[0] = show_md3_loading(5000)
                _log(f"[KPM] md3 loading returned: {ctrl[0]}")
            except Exception as e:
                log_error(e, context="md3 loading in refresh_store")
        def do_refresh():
            _log("[KPM] do_refresh start")
            if not force and self.store.is_cache_valid():
                _log("[KPM] Cache is valid, skipping refresh")
                if ctrl[0]:
                    try:
                        ctrl[0].dismiss()
                    except Exception:
                        pass
                if has_bulletin:
                    from ui.bulletin import BulletinHelper
                    run_on_ui_thread(lambda: BulletinHelper.show_info(
                        _fix_plurals(_tr("cache_used").format(len(self.store.plugins_list)),
                                      len(self.store.plugins_list))))
                return
            try:
                _log(f"[KPM] do_refresh: calling store.refresh force={force} mirrors={getattr(self.store, 'enabled_mirrors', None)}")
                ok = self.store.refresh(force=force)
                _log(f"[KPM] do_refresh: store.refresh returned {ok}")
            except Exception as e:
                _log_error(e, context="do_refresh store.refresh")
                ok = False
            try:
                self._update_all_pills()
            except Exception:
                pass
            if ctrl[0]:
                try:
                    ctrl[0].dismiss()
                except Exception:
                    pass
            if has_bulletin:
                from ui.bulletin import BulletinHelper
                _MIRROR_DISPLAY = {"kangel": "Forgejo", "github": "GitHub", "codeberg": "CodeBerg", "gitverse": "GitVerse"}
                if ok:
                    count = len(self.store.plugins_list)
                    if getattr(self.store, "fallback_used", False) and getattr(self.store, "last_used_mirror", None):
                        used = _MIRROR_DISPLAY.get(self.store.last_used_mirror, self.store.last_used_mirror)
                        failed = ", ".join([_MIRROR_DISPLAY.get(m, m) for m in getattr(self.store, "failed_mirrors", ["kangel"])])
                        msg = _tr("store_loaded_fallback").format(count, used, failed)
                        msg = _fix_plurals(msg, count)
                        run_on_ui_thread(lambda: BulletinHelper.show_success(msg))
                    else:
                        run_on_ui_thread(lambda: BulletinHelper.show_success(
                            _fix_plurals(_tr("store_loaded").format(count), count)))
                else:
                    run_on_ui_thread(lambda: BulletinHelper.show_error(_tr("store_load_failed_all_mirrors")))
        run_on_queue(do_refresh)

    def force_refresh_store(self):
        self.store.clear_cache()
        self.refresh_store(force=True)

    def list_available_ids(self):
        if not self.store.plugins_list:
            self.refresh_store()
        return [pid for pid in self.store.get_all_ids() if not _is_self_plugin(pid)]

    def get_plugin_full_info(self, plugin_id):
        return self.store.fetch_plugin_full_info(plugin_id)

    def get_plugin_display_name(self, plugin_id):
        return self.store.get_display_name(plugin_id)

    def list_installed_plugins(self):
        installed = []
        try:
            if not os.path.isdir(self.plugins_dir):
                return installed
            if hasattr(self, '_installed_cache_time') and (time.time() - self._installed_cache_time) < 5:
                if hasattr(self, '_installed_cache'):
                    return self._installed_cache

            files = os.listdir(self.plugins_dir)
            for name in files:
                path = os.path.join(self.plugins_dir, name)

                if name.endswith(".py"):
                    try:
                        with open(path, "rb") as f:
                            content = f.read()

                        plugin_id = None
                        if content:
                            match = re.search(rb'__id__\s*=\s*["\'](.*?)["\']', content)
                            if match:
                                plugin_id = match.group(1).decode('utf-8', errors='ignore')

                        if not plugin_id:
                            plugin_id = name[:-3]

                        installed.append(plugin_id)
                    except Exception:
                        continue

                elif os.path.isdir(path):
                    has_meta = (
                        os.path.exists(os.path.join(path, "meta.yml")) or
                        os.path.exists(os.path.join(path, "meta.json"))
                    )
                    has_refmap = (
                        os.path.exists(os.path.join(path, "refmap.yml")) or
                        os.path.exists(os.path.join(path, "refmap.yaml")) or
                        os.path.exists(os.path.join(path, "refmap.json"))
                    )
                    has_main = (
                        os.path.exists(os.path.join(path, "src", "main.py")) or
                        os.path.exists(os.path.join(path, "main.py")) or
                        os.path.exists(os.path.join(path, "plugin.py"))
                    )
                    if has_meta or has_main:
                        meta = self._parse_eaf_meta(path) if hasattr(self, '_parse_eaf_meta') else {}
                        plugin_id = meta.get("id", name)
                        installed.append(plugin_id)
                    elif has_refmap:
                        plugin_id = self._detect_eaf_id_from_refmap(path, name)
                        if plugin_id:
                            installed.append(plugin_id)

            elyx_dir = os.path.join(self.plugins_dir, "ElyxPlugins")
            if os.path.isdir(elyx_dir):
                for name in os.listdir(elyx_dir):
                    path = os.path.join(elyx_dir, name)
                    if not os.path.isdir(path) or name == "archives":
                        continue
                    has_meta = (
                        os.path.exists(os.path.join(path, "meta.yml")) or
                        os.path.exists(os.path.join(path, "meta.json"))
                    )
                    has_refmap = (
                        os.path.exists(os.path.join(path, "refmap.yml")) or
                        os.path.exists(os.path.join(path, "refmap.yaml")) or
                        os.path.exists(os.path.join(path, "refmap.json"))
                    )
                    has_main = (
                        os.path.exists(os.path.join(path, "src", "main.py")) or
                        os.path.exists(os.path.join(path, "main.py")) or
                        os.path.exists(os.path.join(path, "plugin.py"))
                    )
                    if has_meta or has_main:
                        meta = self._parse_eaf_meta(path) if hasattr(self, '_parse_eaf_meta') else {}
                        plugin_id = meta.get("id", name)
                        installed.append(plugin_id)
                    elif has_refmap:
                        plugin_id = self._detect_eaf_id_from_refmap(path, name)
                        if plugin_id:
                            installed.append(plugin_id)

            installed = list(set(installed))
            self._installed_cache = installed
            self._installed_cache_time = time.time()

            return installed
        except Exception as e:
            _log_error(e, context="Error listing installed plugins")
            return installed

    def _detect_eaf_id_from_refmap(self, eaf_dir, fallback_name):
        """Detect plugin ID from refmap.yml when meta.yml is in a subdirectory."""
        try:
            import json as _json
            for refmap_name in ("refmap.yml", "refmap.yaml", "refmap.json"):
                refmap_path = os.path.join(eaf_dir, refmap_name)
                if not os.path.exists(refmap_path):
                    continue
                with open(refmap_path, "r", encoding="utf-8") as f:
                    raw = f.read()
                if refmap_name.endswith(".json"):
                    refmap = _json.loads(raw)
                else:
                    refmap = self._parse_yaml_simple(raw) if hasattr(self, '_parse_yaml_simple') else {}
                metainfo_ref = refmap.get("metainfo", "")
                if not metainfo_ref:
                    continue
                metainfo_ref = str(metainfo_ref).lstrip("./")
                metainfo_path = os.path.join(eaf_dir, metainfo_ref)
                if not os.path.exists(metainfo_path):
                    for name in os.listdir(eaf_dir):
                        if name.endswith(metainfo_ref.split("/")[-1]):
                            metainfo_path = os.path.join(eaf_dir, name)
                            break
                if os.path.exists(metainfo_path):
                    with open(metainfo_path, "r", encoding="utf-8") as f:
                        raw = f.read()
                    if metainfo_path.endswith(".json"):
                        meta = _json.loads(raw)
                    else:
                        meta = self._parse_yaml_simple(raw) if hasattr(self, '_parse_yaml_simple') else {}
                    if meta.get("id"):
                        return meta["id"]
            for sub in os.listdir(eaf_dir):
                sub_path = os.path.join(eaf_dir, sub)
                if os.path.isdir(sub_path):
                    meta = self._parse_eaf_meta(sub_path) if hasattr(self, '_parse_eaf_meta') else {}
                    if meta.get("id"):
                        return meta["id"]
        except Exception as e:
            log_error(e, context="_detect_eaf_id_from_refmap")
        return fallback_name

    def get_local_plugin_path(self, plugin_id):
        return os.path.join(self.plugins_dir, f"{plugin_id}.plugin")

    def _calculate_file_hash(self, file_path):
        try:
            if not file_path or not os.path.exists(file_path):
                return None
            h = hashlib.sha256()
            with open(file_path, "rb") as f:
                for block in iter(lambda: f.read(4096), b""):
                    h.update(block)
            return h.hexdigest()
        except Exception:
            return None

    def _verify_plugin_signature(self, plugin_id, version, file_hash, signature):
        try:
            from ..security.verify import verify_plugin_signature
            return verify_plugin_signature(plugin_id, version, file_hash, signature)
        except Exception:
            return False

    def _evaluate_hash_verdict(self, plugin_id, file_hash):
        if not self.store.plugins_list:
            self.load_cache()
        if not self.store.plugins_list:
            return "unknown"
        plugins_set = {k.lower(): k for k in self.store.plugins_list.keys()}
        real_id = plugins_set.get(plugin_id.lower())
        if not real_id:
            return "unknown"
        info = self.store.plugins_list.get(real_id, {})
        if not isinstance(info, dict):
            return "unknown"
        store_hash = info.get("hash", "")
        if store_hash and file_hash.lower() == store_hash.lower():
            signature = info.get("signature", "")
            if signature:
                ok = self._verify_plugin_signature(real_id, info.get("version", ""), file_hash, signature)
                return "verified" if ok else "failed"
            return "verified"
        legacy = info.get("legacy_version", {})
        if isinstance(legacy, dict):
            for vk, vi in legacy.items():
                if isinstance(vi, dict) and vi.get("hash"):
                    if file_hash.lower() == vi["hash"].lower():
                        signature = vi.get("signature", "")
                        if signature:
                            ok = self._verify_plugin_signature(real_id, vk, file_hash, signature)
                            return "verified" if ok else "failed"
                        return "verified"
        return "mismatch"

    def _get_plugin_verdict(self, plugin_id, file_path):
        try:
            file_hash = self._calculate_file_hash(file_path)
            if not file_hash:
                return "unknown"

            verdict = self._evaluate_hash_verdict(plugin_id, file_hash)
            if verdict == "mismatch":
                return "failed"
            return verdict
        except Exception:
            return "unknown"

    def _check_plugin_safety(self, plugin_id, file_path):
        try:
            file_hash = self._calculate_file_hash(file_path)
            if not file_hash:
                return False

            verdict = self._evaluate_hash_verdict(plugin_id, file_hash)
            _log(f"[KPM] _check_plugin_safety for {plugin_id}: verdict={verdict}")
            return verdict == "verified"
        except Exception as e:
            log_error(e, context=f"_check_plugin_safety {plugin_id}")
            return False

    def _check_plugin_hash(self, plugin_id, file_path):
        return self._check_plugin_safety(plugin_id, file_path)

    def _check_plugin_data(self, plugin_id, data):
        try:
            if not data:
                return False
            file_hash = hashlib.sha256(data).hexdigest()
            verdict = self._evaluate_hash_verdict(plugin_id, file_hash)
            _log(f"[KPM] _check_plugin_data for {plugin_id}: verdict={verdict}")
            return verdict == "verified"
        except Exception as e:
            log_error(e, context=f"_check_plugin_data {plugin_id}")
            return False

    def _mkstats_get_client_version(self):
        try:
            from org.telegram.messenger import BuildVars
            v = getattr(BuildVars, "BUILD_VERSION_STRING", None) or getattr(BuildVars, "BUILD_VERSION", None)
            if v:
                return str(v)
        except Exception:
            pass
        try:
            from com.exteragram.messenger import BuildConfig
            v = getattr(BuildConfig, "VERSION_NAME", None)
            if v:
                return str(v)
        except Exception:
            pass
        try:
            from com.radolyn.ayugram import BuildConfig
            v = getattr(BuildConfig, "VERSION_NAME", None)
            if v:
                return str(v)
        except Exception:
            pass
        return "unknown"

    def _compare_versions(self, v1, v2):
        try:
            def nums(v):
                return [int(x) for x in re.findall(r'\d+', str(v))]
            n1, n2 = nums(v1), nums(v2)
            ml = max(len(n1), len(n2))
            n1 += [0] * (ml - len(n1))
            n2 += [0] * (ml - len(n2))
            for a, b in zip(n1, n2):
                if a != b:
                    return 1 if a > b else -1
            return 0
        except Exception:
            return 0

    def _is_client_version_at_least(self, min_version):
        try:
            cv = self._mkstats_get_client_version()
            if not cv or cv == "unknown":
                return False
            return self._compare_versions(cv, min_version) >= 0
        except Exception:
            return False

    def _get_plugin_version_state(self, plugin_id, plugin_info=None):
        try:
            info = self.get_plugin_full_info(plugin_id) or plugin_info or self.store.plugins_list.get(plugin_id)
            min_version = ""
            if isinstance(info, dict):
                min_version = info.get("min_version", "") or _normalize_min_version(info.get("app_version", ""))
            cv = self._mkstats_get_client_version() or "unknown"
            compatible = True
            if min_version:
                compatible = self._is_client_version_at_least(min_version)
            return min_version, cv, compatible
        except Exception:
            return "", "unknown", True

    def _ensure_plugin_version_supported(self, plugin_id, plugin_info=None, show_dialog=True):
        try:
            if not bool(self.get_setting("enforce_version_requirements", True)):
                return True
        except Exception as e:
            log_error(e, context="_ensure_plugin_version_supported get_setting")
        mv, cv, compat = self._get_plugin_version_state(plugin_id, plugin_info)
        if compat or not mv:
            return True
        if show_dialog:
            run_on_ui_thread(lambda: self._show_requirements_dialog(plugin_id, mv, cv))
        return False
