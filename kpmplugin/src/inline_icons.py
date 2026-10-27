import weakref
from org.telegram.tgnet import TLRPC

from base_plugin import MethodHook
from android_utils import run_on_ui_thread
from hook_utils import find_class, get_private_field, set_private_field

from .methods import log, log_error

_MAX_CACHE_SIZE = 500
_sticker_doc_cache = {}
_pending_pack_callbacks = {}
_default_bitmap = None
_default_bitmap_theme_key = None

try:
    from org.telegram.messenger import Utilities
    from java import dynamic_proxy

    class _StickerCallback(dynamic_proxy(Utilities.Callback)):
        def __init__(self, fn):
            super().__init__()
            self._fn = fn

        def run(self, res_obj):
            try:
                self._fn(res_obj)
            except Exception as ex:
                log_error(ex, context="_StickerCallback run")
except Exception:
    _StickerCallback = None


class InlineIconManager:
    @staticmethod
    def parse_icon_spec(icon_spec):
        if not icon_spec or not isinstance(icon_spec, str) or "/" not in icon_spec:
            return None, None
        try:
            pack, idx_str = icon_spec.rsplit("/", 1)
            pack = pack.strip()
            idx = int(idx_str.strip())
            if pack and idx >= 0:
                return pack, idx
        except Exception:
            pass
        return None, None

    @classmethod
    def get_cached_document(cls, icon_spec):
        pack, idx = cls.parse_icon_spec(icon_spec)
        if not pack:
            return None
        key = f"{pack.lower()}/{idx}"
        if key in _sticker_doc_cache:
            return _sticker_doc_cache[key]

        try:
            MediaDataController = find_class("org.telegram.messenger.MediaDataController")
            UserConfig = find_class("org.telegram.messenger.UserConfig")
            if MediaDataController and UserConfig:
                mdc = MediaDataController.getInstance(UserConfig.selectedAccount)
                if mdc:
                    input_set = TLRPC.TL_inputStickerSetShortName()
                    input_set.short_name = pack
                    st_set = mdc.getStickerSet(input_set, False)
                    if st_set and st_set.documents:
                        docs = st_set.documents
                        for i in range(docs.size()):
                            cls._store_document(f"{pack.lower()}/{i}", docs.get(i))
                        if idx < docs.size():
                            return docs.get(idx)
        except Exception:
            pass
        return None

    @classmethod
    def preload_icon(cls, icon_spec, on_loaded=None):
        try:
            pack, idx = cls.parse_icon_spec(icon_spec)
            if not pack:
                return

            doc = cls.get_cached_document(icon_spec)
            if doc:
                if on_loaded:
                    try:
                        on_loaded(doc)
                    except Exception:
                        pass
                return

            MediaDataController = find_class("org.telegram.messenger.MediaDataController")
            UserConfig = find_class("org.telegram.messenger.UserConfig")
            if not MediaDataController or not UserConfig or not _StickerCallback:
                return

            mdc = MediaDataController.getInstance(UserConfig.selectedAccount)
            if not mdc:
                return

            pack_key = pack.lower()
            if pack_key in _pending_pack_callbacks:
                if on_loaded:
                    _pending_pack_callbacks[pack_key].append((idx, on_loaded))
                return

            _pending_pack_callbacks[pack_key] = [(idx, on_loaded)] if on_loaded else []

            input_set = TLRPC.TL_inputStickerSetShortName()
            input_set.short_name = pack

            def _on_sticker_loaded(res_obj, p_key=pack_key):
                try:
                    callbacks = _pending_pack_callbacks.pop(p_key, [])
                    if res_obj is None:
                        return
                    st_set = res_obj
                    if hasattr(st_set, "documents") and st_set.documents:
                        docs = st_set.documents
                        for i in range(docs.size()):
                            cls._store_document(f"{p_key}/{i}", docs.get(i))
                        for target_idx, cb in callbacks:
                            if target_idx < docs.size():
                                found_doc = docs.get(target_idx)
                                if found_doc and cb:
                                    run_on_ui_thread(lambda c=cb, d=found_doc: c(d))
                except Exception as ex:
                    log_error(ex, context="sticker callback parse")

            mdc.getStickerSet(input_set, 0, False, _StickerCallback(_on_sticker_loaded))
        except Exception as e:
            _pending_pack_callbacks.pop(pack.lower() if pack else "", None)
            log_error(e, context="preload_icon")

    @classmethod
    def apply_icon_to_backup_image_view(cls, image_view, icon_spec, tag_prefix="kpm", placeholder_size_dp=56):
        if image_view is None:
            return
        try:
            from org.telegram.messenger import ImageLocation, DocumentObject
            from org.telegram.ui.ActionBar import Theme
            from android.view import View

            image_view.setVisibility(View.VISIBLE)
            doc = cls.get_cached_document(icon_spec) if icon_spec else None
            if doc:
                try:
                    key_icon = int(Theme.key_windowBackgroundWhiteGrayIcon)
                except Exception:
                    key_icon = 0
                img_loc = ImageLocation.getForDocument(doc)
                svg_thumb = DocumentObject.getSvgThumb(doc, key_icon, 0.2, 1.0, None)
                image_view.setImage(img_loc, "150_150", svg_thumb, 0, doc)
                image_view.invalidate()
                return

            bmp = cls.get_default_plugin_bitmap(placeholder_size_dp)
            if bmp:
                image_view.setImageBitmap(bmp)
            else:
                image_view.setImageDrawable(None)
            image_view.invalidate()

            if icon_spec:
                tag = f"{tag_prefix}_{icon_spec}"
                image_view.setTag(tag)
                keep_alive = [image_view]
                iv_ref = weakref.ref(image_view)

                def _on_loaded(loaded_doc, t=tag, ref=iv_ref, keep=keep_alive):
                    try:
                        target_iv = ref()
                        if not target_iv:
                            return
                        curr_tag = getattr(target_iv, "getTag", lambda: None)()
                        if str(curr_tag) != t:
                            return
                        try:
                            try:
                                k_icon = int(Theme.key_windowBackgroundWhiteGrayIcon)
                            except Exception:
                                k_icon = 0
                            loc = ImageLocation.getForDocument(loaded_doc)
                            thumb = DocumentObject.getSvgThumb(loaded_doc, k_icon, 0.2, 1.0, None)
                            target_iv.setImage(loc, "150_150", thumb, 0, loaded_doc)
                            target_iv.invalidate()
                        except Exception as ex:
                            log_error(ex, context="apply_icon_to_backup_image_view on_loaded")
                    finally:
                        try:
                            keep.clear()
                        except Exception:
                            pass

                cls.preload_icon(icon_spec, on_loaded=_on_loaded)
        except Exception as e:
            log_error(e, context="apply_icon_to_backup_image_view")

    @classmethod
    def _store_document(cls, key, doc):
        if not key or not doc:
            return
        if len(_sticker_doc_cache) >= _MAX_CACHE_SIZE:
            try:
                _sticker_doc_cache.pop(next(iter(_sticker_doc_cache)))
            except Exception:
                _sticker_doc_cache.clear()
        _sticker_doc_cache[key] = doc

    @classmethod
    def get_default_plugin_bitmap(cls, size_dp=56):
        global _default_bitmap, _default_bitmap_theme_key
        try:
            Theme = find_class("org.telegram.ui.ActionBar.Theme")
            try:
                bg_color = int(Theme.getColor(Theme.key_featuredStickers_addButton))
            except Exception:
                bg_color = 0xFF2196F3
            theme_key = f"{bg_color}_{size_dp}"

            if _default_bitmap is not None and _default_bitmap_theme_key == theme_key:
                return _default_bitmap

            AndroidUtilities = find_class("org.telegram.messenger.AndroidUtilities")
            ApplicationLoader = find_class("org.telegram.messenger.ApplicationLoader")
            Bitmap = find_class("android.graphics.Bitmap")
            Canvas = find_class("android.graphics.Canvas")
            Paint = find_class("android.graphics.Paint")
            RectF = find_class("android.graphics.RectF")

            if not AndroidUtilities or not ApplicationLoader or not Bitmap or not Canvas:
                return None

            size = AndroidUtilities.dp(float(size_dp))
            if size <= 0:
                size = int(size_dp * 3)

            bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            c = Canvas(bmp)

            bg_paint = Paint(Paint.ANTI_ALIAS_FLAG)
            from java import jint
            bg_paint.setColor(jint(int(bg_color)))
            radius = float(AndroidUtilities.dp(float(size_dp * 0.22)))
            rect = RectF(0.0, 0.0, float(size), float(size))
            c.drawRoundRect(rect, radius, radius, bg_paint)

            icon_res_id = get_client_drawable_res(
                "plugins_filled", "plugins_filled_remix", "msg_plugins",
                "msg_plugins_remix", "msg_plugin", "msg_bot", "msg_settings",
            )

            if icon_res_id:
                try:
                    ctx = ApplicationLoader.applicationContext
                    ContextCompat = find_class("androidx.core.content.ContextCompat")
                    PorterDuff = find_class("android.graphics.PorterDuff")
                    PorterDuffColorFilter = find_class("android.graphics.PorterDuffColorFilter")
                    drawable = ContextCompat.getDrawable(ctx, icon_res_id) if ContextCompat else None
                    if drawable:
                        icon_color = Theme.getColor(Theme.key_featuredStickers_buttonText) if Theme else -1
                        drawable.setColorFilter(PorterDuffColorFilter(jint(int(icon_color)), PorterDuff.Mode.SRC_IN))
                        icon_size = AndroidUtilities.dp(float(size_dp * 0.55))
                        pad = (size - icon_size) // 2
                        drawable.setBounds(pad, pad, pad + icon_size, pad + icon_size)
                        drawable.draw(c)
                except Exception as e:
                    log_error(e, context="default bitmap plugins icon draw")
            else:
                log("[KPM] default bitmap: no plugins drawable found in client")

            _default_bitmap = bmp
            _default_bitmap_theme_key = theme_key
            return _default_bitmap
        except Exception as e:
            log_error(e, context="get_default_plugin_bitmap")
            return None


def get_client_drawable_res(*names):
    if not names:
        return 0
    try:
        from org.telegram.messenger import ApplicationLoader
        ctx = ApplicationLoader.applicationContext
        res = ctx.getResources()
        pkg = ctx.getPackageName()
        for name in names:
            try:
                rid = int(res.getIdentifier(name, "drawable", pkg))
                if rid:
                    return rid
            except Exception:
                continue
    except Exception as e:
        log_error(e, context="get_client_drawable_res")
    return 0


class _ContextLinkCellSetLinkHook(MethodHook):
    def __init__(self, plugin):
        MethodHook.__init__(self)
        self._plugin_ref = weakref.ref(plugin)

    def after_hooked_method(self, hook_param):
        try:
            plugin = self._plugin_ref()
            if not plugin:
                return
            if len(hook_param.args) < 1:
                return

            res = hook_param.args[0]
            if res is None:
                return
            res_id = getattr(res, "id", None)
            if not res_id:
                return
            res_id_str = str(res_id)
            if not (res_id_str.startswith("local|") or res_id_str.startswith("remote|")):
                return

            cell = hook_param.thisObject
            plugin_data = getattr(plugin, "_kpm_inline_plugins", {}).get(res_id_str, {})
            plugin_name = plugin_data.get("name") or "Plugin"

            letter_drawable = get_private_field(cell, "letterDrawable")
            if letter_drawable:
                try:
                    letter_drawable.setTitle(plugin_name)
                except Exception:
                    pass

            set_private_field(cell, "drawLinkImageView", True)

            img_receiver = cell.getPhotoImage() if hasattr(cell, "getPhotoImage") else None
            if not img_receiver:
                img_receiver = get_private_field(cell, "linkImageView")

            if img_receiver:
                try:
                    img_receiver.clearImage()
                except Exception:
                    pass

            icon_spec = plugin_data.get("icon")
            doc = InlineIconManager.get_cached_document(icon_spec) if icon_spec else None

            if doc and img_receiver:
                _apply_sticker_to_cell(cell, img_receiver, doc, letter_drawable)
            else:
                set_private_field(cell, "documentAttach", None)
                bmp = InlineIconManager.get_default_plugin_bitmap(52)
                if bmp and img_receiver:
                    img_receiver.setImageBitmap(bmp)
                    set_private_field(cell, "drawLinkImageView", True)
                    if letter_drawable:
                        try:
                            letter_drawable.setAlpha(0)
                        except Exception:
                            pass
                else:
                    set_private_field(cell, "drawLinkImageView", False)
                    if letter_drawable:
                        try:
                            letter_drawable.setAlpha(255)
                        except Exception:
                            pass

                if icon_spec:
                    cell_ref = weakref.ref(cell)
                    def _on_sticker_ready(loaded_doc):
                        target_cell = cell_ref()
                        if not target_cell:
                            return
                        curr_res = getattr(target_cell, "getResult", lambda: None)()
                        if not curr_res or str(getattr(curr_res, "id", "")) != res_id_str:
                            return
                        target_receiver = target_cell.getPhotoImage() if hasattr(target_cell, "getPhotoImage") else None
                        if not target_receiver:
                            target_receiver = get_private_field(target_cell, "linkImageView")
                        if target_receiver and loaded_doc:
                            target_ld = get_private_field(target_cell, "letterDrawable")
                            _apply_sticker_to_cell(target_cell, target_receiver, loaded_doc, target_ld)
                            target_cell.invalidate()

                    InlineIconManager.preload_icon(icon_spec, on_loaded=_on_sticker_ready)

        except Exception as e:
            log_error(e, context="_ContextLinkCellSetLinkHook")


class _ContextLinkCellMeasureHook(MethodHook):
    def __init__(self, plugin):
        MethodHook.__init__(self)
        self._plugin_ref = weakref.ref(plugin)

    def after_hooked_method(self, hook_param):
        try:
            cell = hook_param.thisObject
            res = getattr(cell, "getResult", lambda: None)()
            if not res:
                res = get_private_field(cell, "inlineResult")
            if not res:
                return

            res_id = getattr(res, "id", None)
            if not res_id:
                return
            res_id_str = str(res_id)
            if res_id_str.startswith("local|") or res_id_str.startswith("remote|"):
                doc_attach = get_private_field(cell, "documentAttach")
                img_recv = cell.getPhotoImage() if hasattr(cell, "getPhotoImage") else None
                if not img_recv:
                    img_recv = get_private_field(cell, "linkImageView")
                has_image = bool(doc_attach or (img_recv and hasattr(img_recv, "hasBitmapImage") and img_recv.hasBitmapImage()))
                set_private_field(cell, "drawLinkImageView", has_image)
                letter_drawable = get_private_field(cell, "letterDrawable")
                if letter_drawable:
                    try:
                        letter_drawable.setAlpha(0 if has_image else 255)
                    except Exception:
                        pass
        except Exception:
            pass


def _apply_sticker_to_cell(cell, img_receiver, doc, letter_drawable=None):
    try:
        set_private_field(cell, "documentAttach", doc)
        set_private_field(cell, "drawLinkImageView", True)
        if letter_drawable:
            try:
                letter_drawable.setAlpha(0)
            except Exception:
                pass

        ImageLocation = find_class("org.telegram.messenger.ImageLocation")
        DocumentObject = find_class("org.telegram.messenger.DocumentObject")
        Theme = find_class("org.telegram.ui.ActionBar.Theme")
        if not ImageLocation or not DocumentObject:
            return

        try:
            key_icon = int(Theme.key_windowBackgroundWhiteGrayIcon)
        except Exception:
            key_icon = 0

        img_loc = ImageLocation.getForDocument(doc)
        svg_thumb = DocumentObject.getSvgThumb(doc, key_icon, 0.2, 1.0, None)
        img_receiver.setImage(img_loc, "80_80", svg_thumb, 0, doc, 0)
    except Exception as e:
        log_error(e, context="_apply_sticker_to_cell")


def install_inline_cell_hooks(plugin):
    try:
        ContextLinkCell = find_class("org.telegram.ui.Cells.ContextLinkCell")
        if not ContextLinkCell:
            return

        plugin._track_hooks(plugin.hook_all_methods(
            ContextLinkCell, "setLink", _ContextLinkCellSetLinkHook(plugin)))
        plugin._track_hooks(plugin.hook_all_methods(
            ContextLinkCell, "onMeasure", _ContextLinkCellMeasureHook(plugin)))
        log("[KPM] inline cell hooks installed")
    except Exception as e:
        log_error(e, context="install_inline_cell_hooks")
