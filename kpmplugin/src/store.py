import traceback
import hashlib
from android_utils import log
import os
import json
import time
import requests

from .methods import log_error


STORE_MIRRORS = {
    "github": {
        "urls": [
            "https://raw.githubusercontent.com/Kangel-Plugins/Plugins-Store/refs/heads/main/store.json",
            "https://raw.githubusercontent.com/Kangel-Plugins/Plugins-Store/main/store.json",
        ],
        "api": "https://api.github.com/repos/Kangel-Plugins/Plugins-Store/commits/main",
    },
    "codeberg": {
        "urls": [
            "https://codeberg.org/Kangel/Plugins-Store/raw/branch/main/store.json",
        ],
        "api": "https://codeberg.org/api/v1/repos/Kangel/Plugins-Store/commits?limit=1",
    },
    "gitverse": {
        "urls": [
            "https://gitverse.ru/api/repos/bigfishtheory/Plugins-Store/raw/branch/main/store.json",
        ],
        "api": "https://gitverse.ru/api/repos/bigfishtheory/Plugins-Store/commits?page=1&limit=1",
    },
    "kangel": {
        "urls": [
            "https://git.kangel.xyz/KangelPlugins/Plugins-Store/raw/branch/main/store.json",
        ],
        "api": "https://git.kangel.xyz/api/v1/repos/KangelPlugins/Plugins-Store/commits?limit=1",
    },
}


_ALL_MIRRORS = ["kangel", "github", "codeberg", "gitverse"]

MIRROR_BASE_URLS = {
    "kangel": "https://git.kangel.xyz/KangelPlugins/Plugins-Store/raw/branch/main/",
    "github": "https://raw.githubusercontent.com/Kangel-Plugins/Plugins-Store/main/",
    "codeberg": "https://codeberg.org/Kangel/Plugins-Store/raw/branch/main/",
    "gitverse": "https://gitverse.ru/api/repos/bigfishtheory/Plugins-Store/raw/branch/main/",
}


class Store:
    _active_enabled_mirrors = list(_ALL_MIRRORS)

    def __init__(self, plugins_dir, enabled_mirrors=None):
        self.plugins_dir = plugins_dir
        if enabled_mirrors is not None and isinstance(enabled_mirrors, (list, tuple)):
            self.enabled_mirrors = [m for m in enabled_mirrors if m in STORE_MIRRORS]
        else:
            self.enabled_mirrors = list(_ALL_MIRRORS)
        if not self.enabled_mirrors:
            self.enabled_mirrors = ["kangel"]
        Store._active_enabled_mirrors = list(self.enabled_mirrors)
        self.cache_file = os.path.join(plugins_dir, ".kpm_cache.json")
        self.plugins_list = {}
        self.plugin_names_cache = {}
        self.last_used_mirror = None
        self.failed_mirrors = []
        self.fallback_used = False
        self._load_cache()

    def _load_cache(self):
        try:
            if os.path.exists(self.cache_file):
                with open(self.cache_file, "r", encoding="utf-8") as f:
                    data = json.load(f)
                self.plugins_list = data.get("plugins_list", {})
                self.plugin_names_cache = data.get("plugin_names", {})
        except Exception as e:
            log_error(e, context="store _load_cache")

    def _save_cache(self, commit_sha):
        try:
            data = {
                "plugins_list": self.plugins_list,
                "plugin_names": self.plugin_names_cache,
                "commit_sha": commit_sha,
                "timestamp": int(time.time()),
            }
            with open(self.cache_file, "w", encoding="utf-8") as f:
                json.dump(data, f, ensure_ascii=False, indent=2)
        except Exception as e:
            log_error(e, context="store _save_cache")

    def clear_cache(self):
        try:
            if os.path.exists(self.cache_file):
                os.remove(self.cache_file)
        except Exception as e:
            log_error(e, context="store clear_cache")
        self.plugins_list.clear()
        self.plugin_names_cache.clear()

    def _fetch_commit_sha(self, mirror):
        if mirror not in STORE_MIRRORS:
            return None
        try:
            api_url = STORE_MIRRORS[mirror]["api"]
            log(f"[KPM] get_latest_commit_sha: requesting {api_url} (mirror={mirror})")
            r = requests.get(api_url, headers={"User-Agent": "KPM"}, timeout=3.5)
            log(f"[KPM] get_latest_commit_sha: status={r.status_code}")
            if r.status_code == 200:
                data = json.loads(r.text)
                if isinstance(data, list):
                    sha = data[0].get("sha", "") if data else ""
                elif isinstance(data, dict):
                    if "data" in data and isinstance(data["data"], list) and data["data"]:
                        sha = data["data"][0].get("sha", "")
                    else:
                        sha = data.get("sha", "")
                else:
                    sha = ""
                log(f"[KPM] get_latest_commit_sha: sha={sha[:12] if sha else ''} from mirror={mirror}")
                return sha
        except Exception as e:
            log_error(e, context=f"get_latest_commit_sha mirror={mirror}")
        return None

    def get_latest_commit_sha(self):
        for m in self.enabled_mirrors:
            sha = self._fetch_commit_sha(m)
            if sha:
                return sha
        return None

    def get_cached_commit_sha(self):
        try:
            if os.path.exists(self.cache_file):
                with open(self.cache_file, "r", encoding="utf-8") as f:
                    sha = json.load(f).get("commit_sha", "")
                    log(f"[KPM] get_cached_commit_sha: {sha[:12] if sha else 'empty'}")
                    return sha
        except Exception as e:
            log_error(e, context="get_cached_commit_sha")
        return ""

    def get_cached_timestamp(self):
        try:
            if os.path.exists(self.cache_file):
                with open(self.cache_file, "r", encoding="utf-8") as f:
                    return int(json.load(f).get("timestamp", 0) or 0)
        except Exception:
            pass
        return 0

    def is_cache_valid(self, max_age=300):
        cached = self.get_cached_commit_sha()
        if not cached:
            log("[KPM] is_cache_valid: no cached sha -> False")
            return False
        ts = self.get_cached_timestamp()
        if ts > 0 and (time.time() - ts) > max_age:
            log(f"[KPM] is_cache_valid: cache expired by age ({int(time.time() - ts)}s > {max_age}s) -> False")
            return False
        latest = self.get_latest_commit_sha()
        if not latest:
            fresh = (time.time() - ts) < 60 if ts > 0 else False
            log(f"[KPM] is_cache_valid: no latest sha -> fallback ({fresh})")
            return fresh
        valid = cached == latest
        log(f"[KPM] is_cache_valid: cached={cached[:8]} latest={latest[:8]} -> {valid}")
        return valid

    def refresh(self, force=False):
        if not force and self.is_cache_valid():
            return True

        self.last_used_mirror = None
        self.failed_mirrors = []
        self.fallback_used = False

        mirrors_to_try = [m for m in self.enabled_mirrors if m in STORE_MIRRORS]
        if not mirrors_to_try:
            mirrors_to_try = ["kangel"]

        all_other = [m for m in _ALL_MIRRORS if m not in mirrors_to_try]
        search_mirrors = mirrors_to_try + all_other

        first_mirror = mirrors_to_try[0]
        for mirror_key in search_mirrors:
            urls = STORE_MIRRORS[mirror_key]["urls"]
            for i, url in enumerate(urls):
                try:
                    log(f"[KPM] store refresh: trying {url} (mirror={mirror_key})")
                    r = requests.get(url, headers={"User-Agent": "KPM"}, timeout=4.5)
                    if r.status_code != 200 or not r.text:
                        log(f"[KPM] store refresh: mirror={mirror_key} url#{i} status={r.status_code} len={len(r.text) if r.text else 0}")
                        continue
                    store_data = json.loads(r.text)
                    if not isinstance(store_data, dict):
                        log(f"[KPM] store refresh: mirror={mirror_key} url#{i} not a dict: {type(store_data)}")
                        continue

                    normalized = self._parse_store_data(store_data)
                    self.plugins_list = normalized
                    sha = self._fetch_commit_sha(mirror_key)
                    if sha:
                        self._save_cache(sha)
                    self.last_used_mirror = mirror_key
                    if mirror_key != first_mirror:
                        self.fallback_used = True
                    log(f"[KPM] store refresh OK: {len(normalized)} plugins from mirror={mirror_key}")
                    return True
                except Exception as e:
                    log_error(e, context=f"store refresh mirror={mirror_key} url#{i}")
                    continue
            self.failed_mirrors.append(mirror_key)

        log("[KPM] store refresh FAILED: all mirrors failed")
        return False

    def _parse_store_data(self, store_data):
        normalized = {}
        for pid, value in store_data.items():
            if isinstance(value, str):
                normalized[pid] = {"url": value}
            elif isinstance(value, dict):
                normalized[pid] = {
                    "url": value.get("url", ""),
                    "dependencies": value.get("dependencies", []),
                    "requirements": self._normalize_list(value.get("requirements", [])),
                }
                for key in ("name", "version", "icon", "author", "description",
                             "min_version", "app_version", "hash", "signature",
                             "legacy_version", "status"):
                    if value.get(key):
                        normalized[pid][key] = value[key]
                for key, val in value.items():
                    if key.startswith("description_") and val:
                        normalized[pid][key] = val
                if value.get("app_version") and not normalized[pid].get("min_version"):
                    import re
                    m = re.search(r"(\d+(?:\.\d+)+)", str(value["app_version"]))
                    if m:
                        normalized[pid]["min_version"] = m.group(1)
        return normalized

    def _normalize_list(self, value):
        if not value:
            return []
        if isinstance(value, (list, tuple, set)):
            return [str(x).strip() for x in value if x]
        return [x.strip() for x in str(value).split(",") if x.strip()]

    def get_plugin_info(self, plugin_id):
        return self.plugins_list.get(plugin_id)

    def get_all_ids(self):
        return list(self.plugins_list.keys())

    def get_display_name(self, plugin_id):
        if plugin_id in self.plugin_names_cache:
            return self.plugin_names_cache[plugin_id]
        info = self.plugins_list.get(plugin_id)
        if isinstance(info, dict) and info.get("name"):
            name = info["name"]
            self.plugin_names_cache[plugin_id] = name
            return name
        return plugin_id

    def fetch_plugin_full_info(self, plugin_id):
        info = self.plugins_list.get(plugin_id)
        if not info:
            return None
        result = {
            "plugin_id": plugin_id,
            "url": info.get("url", ""),
            "name": info.get("name", plugin_id),
            "version": info.get("version", "1.0"),
            "author": info.get("author", ""),
            "description": info.get("description", ""),
            "icon": info.get("icon", ""),
            "min_version": info.get("min_version", ""),
            "app_version": info.get("app_version", ""),
            "requirements": self._normalize_list(info.get("requirements", [])),
            "dependencies": info.get("dependencies", []),
            "status": info.get("status", "plugin"),
            "hash": info.get("hash", ""),
            "signature": info.get("signature", ""),
            "legacy_version": info.get("legacy_version", {}),
        }
        if result["app_version"] and not result["min_version"]:
            import re
            m = re.search(r"(\d+(?:\.\d+)+)", str(result["app_version"]))
            if m:
                result["min_version"] = m.group(1)
        return result

    @classmethod
    def get_candidate_urls(cls, url: str, enabled_mirrors=None):
        if not url:
            return []
        url_str = str(url).strip()

        rel_path = None
        for marker in ("/Plugins/", "/legacy_versions/", "/meta/"):
            idx = url_str.find(marker)
            if idx != -1:
                rel_path = url_str[idx + 1:]
                break

        if not rel_path:
            return [url_str]

        candidates = []
        mirrors = enabled_mirrors
        if mirrors is None:
            mirrors = getattr(cls, "_active_enabled_mirrors", None) or _ALL_MIRRORS

        for m in mirrors:
            base = MIRROR_BASE_URLS.get(m)
            if base:
                c_url = base + rel_path
                if c_url not in candidates:
                    candidates.append(c_url)

        for m in _ALL_MIRRORS:
            base = MIRROR_BASE_URLS.get(m)
            if base:
                c_url = base + rel_path
                if c_url not in candidates:
                    candidates.append(c_url)

        if url_str not in candidates:
            candidates.append(url_str)

        return candidates

    @classmethod
    def fetch_bytes(cls, url, enabled_mirrors=None, timeout=6.0):
        candidates = cls.get_candidate_urls(url, enabled_mirrors=enabled_mirrors)
        last_err = None
        for cand_url in candidates:
            try:
                log(f"[KPM] Store.fetch_bytes: trying {cand_url}")
                r = requests.get(cand_url, headers={"User-Agent": "KPM"}, timeout=timeout)
                if r.status_code == 200 and r.content:
                    log(f"[KPM] Store.fetch_bytes: OK ({len(r.content)} bytes) from {cand_url}")
                    return r.content
                log(f"[KPM] Store.fetch_bytes: {cand_url} returned HTTP {r.status_code}")
            except Exception as e:
                last_err = e
                log_error(e, context=f"Store.fetch_bytes candidate={cand_url}")
        if last_err:
            raise last_err
        raise Exception(f"Failed to fetch {url} from any mirror ({len(candidates)} tried)")

    @staticmethod
    def sha256(data):
        return hashlib.sha256(data).hexdigest()
