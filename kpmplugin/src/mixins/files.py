import os
import weakref

from android_utils import log as _log, run_on_ui_thread
from client_utils import get_last_fragment, run_on_queue

from ..methods import _normalize_min_version, _normalize_requirements, log_error
from ..store import Store


class FilesMixin:

    def fetch_remote_bytes(self, url):
        return Store.fetch_bytes(url)

    def sha256(self, data):
        return Store.sha256(data)

    def read_file_bytes(self, path):
        try:
            with open(path, "rb") as f:
                return f.read()
        except Exception:
            return None

    def parse_plugin_metadata(self, content):
        metadata = {
            "name": None,
            "description": None,
            "author": None,
            "version": None,
            "icon": None,
            "min_version": None,
            "app_version": None,
            "requirements": [],
        }
        try:
            lines = content.decode('utf-8', errors='ignore').split('\n')
            in_multiline = False
            multiline_key = None
            multiline_value = []

            for line in lines:
                line_stripped = line.strip()
                if in_multiline:
                    if '"""' in line_stripped:
                        multiline_value.append(line_stripped.split('"""')[0])
                        metadata[multiline_key] = '\n'.join(multiline_value).strip()
                        in_multiline = False
                        multiline_key = None
                        multiline_value = []
                    else:
                        multiline_value.append(line_stripped)
                    continue

                for key in ['__name__', '__description__', '__author__', '__version__', '__icon__', '__min_version__', '__app_version__', '__app_cersion__', '__requirements__']:
                    if line_stripped.startswith(key):
                        parts = line_stripped.split('=', 1)
                        if len(parts) == 2:
                            value = parts[1].strip()
                            metadata_key = key[2:-2]
                            if metadata_key == "app_cersion":
                                metadata_key = "app_version"
                            if value.startswith('"""'):
                                if value.count('"""') >= 2:
                                    parsed_value = value.strip('"').strip()
                                else:
                                    in_multiline = True
                                    multiline_key = metadata_key
                                    multiline_value = [value[3:]]
                                    continue
                            else:
                                parsed_value = value.strip('"').strip("'")
                            if metadata_key == "requirements":
                                metadata[metadata_key] = _normalize_requirements(parsed_value)
                            else:
                                metadata[metadata_key] = parsed_value
                        break
        except Exception as e:
            _log_error(e, context="Error parsing metadata")
        if metadata.get("app_version") and not metadata.get("min_version"):
            metadata["min_version"] = _normalize_min_version(metadata.get("app_version"))
        return metadata

    def _cache_sticker(self, cache_key, location, document):
        try:
            if not hasattr(self, '_sticker_cache'):
                self._sticker_cache = {}
            if len(self._sticker_cache) >= self._sticker_cache_limit:
                try:
                    oldest = next(iter(self._sticker_cache))
                    self._sticker_cache.pop(oldest, None)
                except Exception:
                    pass
            self._sticker_cache[cache_key] = {
                'location': location,
                'document': document,
                'size': "150_150"
            }
        except Exception:
            pass

    def preload_sticker_sets(self):
        try:
            from org.telegram.messenger import MediaDataController
            from org.telegram.tgnet import TLRPC

            current_account = 0
            try:
                fragment = get_last_fragment()
                if fragment:
                    current_account = fragment.getCurrentAccount()
            except Exception:
                pass

            media_controller = MediaDataController.getInstance(current_account)
            seen = set()

            for plugin_id in self.store.get_all_ids():
                try:
                    info = self.store.get_plugin_info(plugin_id)
                    if not info:
                        continue
                    icon = info.get('icon', '') if isinstance(info, dict) else ''
                    if icon and '/' in icon:
                        set_name = icon.split('/')[0]
                        if set_name not in seen:
                            seen.add(set_name)
                            sticker_set = media_controller.getStickerSetByName(set_name)
                            if not sticker_set:
                                input_set = TLRPC.TL_inputStickerSetShortName()
                                input_set.short_name = set_name
                                media_controller.getStickerSet(input_set, None, False, None)
                except Exception:
                    pass
        except Exception:
            pass

    def _load_sticker_async(self, image_view, sticker_set_name, sticker_index, cache_key=None):
        _keep_alive = [image_view]
        _iv_ref = weakref.ref(image_view)
        try:
            sticker_index = int(sticker_index)
        except Exception:
            sticker_index = 0
        eff_key = f"{cache_key}#{sticker_index}" if cache_key else None
        _log(f"[KPM] sticker load: pack={sticker_set_name} idx={sticker_index} key={eff_key}")

        def _doc_id(doc):
            try:
                return doc.id
            except Exception:
                return "?"

        def _safe_set_image(loc, size, doc):
            iv = _iv_ref()
            try:
                if iv is not None:
                    _log(f"[KPM] sticker set: pack={sticker_set_name} idx={sticker_index} doc={_doc_id(doc)}")
                    run_on_ui_thread(lambda: iv.setImage(loc, size, None, 0, doc))
                else:
                    _log(f"[KPM] sticker set skipped (view gone): pack={sticker_set_name} idx={sticker_index}")
            finally:
                try:
                    _keep_alive.clear()
                except Exception:
                    pass

        def load_in_background():
            try:
                if eff_key and hasattr(self, '_sticker_cache') and eff_key in self._sticker_cache:
                    cached = self._sticker_cache[eff_key]
                    _log(f"[KPM] sticker cache hit: key={eff_key} doc={_doc_id(cached.get('document'))}")
                    _safe_set_image(cached['location'], cached['size'], cached['document'])
                    return
                _log(f"[KPM] sticker cache miss: key={eff_key}")

                current_account = 0
                try:
                    fragment = get_last_fragment()
                    if fragment:
                        current_account = fragment.getCurrentAccount()
                except Exception:
                    pass

                from org.telegram.messenger import MediaDataController, ImageLocation
                media_controller = MediaDataController.getInstance(current_account)
                sticker_set = media_controller.getStickerSetByName(sticker_set_name)

                if sticker_set and sticker_set.documents and sticker_set.documents.size() > sticker_index:
                    sticker_document = sticker_set.documents.get(sticker_index)
                    image_location = ImageLocation.getForDocument(sticker_document)
                    if eff_key:
                        self._cache_sticker(eff_key, image_location, sticker_document)
                    _safe_set_image(image_location, "150_150", sticker_document)
                else:
                    _log(f"[KPM] sticker not in local set, fetching: pack={sticker_set_name} idx={sticker_index}")
                    try:
                        from org.telegram.tgnet import TLRPC
                        input_set = TLRPC.TL_inputStickerSetShortName()
                        input_set.short_name = sticker_set_name

                        def on_set_response(result):
                            try:
                                if result and result.documents and result.documents.size() > sticker_index:
                                    doc = result.documents.get(sticker_index)
                                    img_loc = ImageLocation.getForDocument(doc)
                                    if eff_key:
                                        self._cache_sticker(eff_key, img_loc, doc)
                                    _safe_set_image(img_loc, "150_150", doc)
                                else:
                                    _log(f"[KPM] sticker fetch empty: pack={sticker_set_name} idx={sticker_index}")
                            except Exception as e:
                                log_error(e, context="sticker fetch response")

                        from org.telegram.messenger import Utilities
                        from java import dynamic_proxy

                        class CallbackProxy(dynamic_proxy(Utilities.Callback)):
                            def __init__(self, fn):
                                super().__init__()
                                self._fn = fn
                            def run(self, arg):
                                try:
                                    self._fn(arg)
                                except Exception:
                                    pass

                        media_controller.getStickerSet(input_set, None, False, CallbackProxy(on_set_response))
                    except Exception as e:
                        log_error(e, context=f"sticker fetch pack={sticker_set_name} idx={sticker_index}")
            except Exception as e:
                log_error(e, context=f"sticker load pack={sticker_set_name} idx={sticker_index}")

        run_on_queue(load_in_background)
