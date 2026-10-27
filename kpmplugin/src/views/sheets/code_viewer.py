import io
import os
import sys
import threading
import time
import zipfile

from android_utils import run_on_ui_thread
from client_utils import get_last_fragment
from hook_utils import find_class

from ...methods import log, log_error, _tr, is_beta_code_viewer_enabled
from ...decompiler import (
    decompile_pyc as _disassemble_pyc,
    inspect_elf as _inspect_elf,
    inspect_dex as _inspect_dex,
)
from .web_ide import show_archive_web_ide, show_code_web_ide

_CLASS_CACHE = {}


def open_in_article_viewer(title: str, text: str, lang: str = "plain", target_fragment=None, file_path: str = None) -> bool:
    try:
        fragment = target_fragment or get_last_fragment()
        if not fragment:
            log("[KPM] open_in_article_viewer: no active fragment")
            return False

        TLRPC = _cached_class("org.telegram.tgnet.TLRPC")
        UserConfig = _cached_class("org.telegram.messenger.UserConfig")
        MessageObject = _cached_class("org.telegram.messenger.MessageObject")
        TL_iv = _cached_class("org.telegram.tgnet.tl.TL_iv")
        if not (TLRPC and UserConfig and MessageObject and TL_iv):
            log("[KPM] open_in_article_viewer: required classes missing")
            return False

        page = TL_iv.TL_page()
        page.url = str(title or "source.py")
        if file_path:
            try:
                from java.io import File
                page.local = File(file_path)
            except Exception:
                pass

        chunk_size = 8192
        start = 0
        target_text = text or ""
        text_len = len(target_text)
        if text_len == 0:
            tp = TL_iv.textPlain()
            tp.text = ""
            pb = TL_iv.pageBlockPreformatted()
            pb.text = tp
            pb.language = str(lang or "plain")
            page.blocks.add(pb)
        else:
            while start < text_len:
                end = min(text_len, start + chunk_size)
                if end < text_len:
                    nl_pos = target_text.rfind("\n", start, end)
                    if nl_pos > start:
                        end = nl_pos + 1
                chunk = target_text[start:end]
                tp = TL_iv.textPlain()
                tp.text = chunk
                pb = TL_iv.pageBlockPreformatted()
                pb.text = tp
                pb.language = str(lang or "plain")
                page.blocks.add(pb)
                start = end

        web_page = TLRPC.TL_webPage()
        page_id = abs(hash(title or "source.py")) & 0x7FFFFFFF
        from java import jlong
        web_page.id = jlong(page_id)
        web_page.url = str(title or "source.py")
        web_page.display_url = str(title or "source.py")
        web_page.site_name = str(title or "Source")
        web_page.title = str(title or "Source")
        web_page.flags = int(getattr(web_page, "flags", 0)) | 1028
        web_page.cached_page = page

        account = UserConfig.selectedAccount
        user_config = UserConfig.getInstance(account)
        self_id = user_config.clientUserId

        msg = TLRPC.TL_message()
        msg.id = 0
        msg.date = int(time.time())
        msg.message = str(title or "source.py")
        msg.out = True

        peer = TLRPC.TL_peerUser()
        peer.user_id = self_id
        msg.peer_id = peer

        from_u = TLRPC.TL_peerUser()
        from_u.user_id = self_id
        msg.from_id = from_u

        media = TLRPC.TL_messageMediaWebPage()
        media.webpage = web_page
        msg.media = media

        message_obj = MessageObject(account, msg, False, True)

        def _do_open():
            try:
                viewer = None
                if hasattr(fragment, "createArticleViewer"):
                    try:
                        viewer = fragment.createArticleViewer(False)
                    except Exception as ex:
                        log_error(ex, context="fragment.createArticleViewer")
                        viewer = None
                if not viewer:
                    ArticleViewer_cls = _cached_class("org.telegram.ui.ArticleViewer")
                    if ArticleViewer_cls:
                        viewer = ArticleViewer_cls.getInstance()
                        act = fragment.getParentActivity() if hasattr(fragment, "getParentActivity") else None
                        if act:
                            try:
                                viewer.setParentActivity(act, None)
                            except Exception:
                                viewer.setParentActivity(act)
                if viewer:
                    ok = viewer.open(message_obj)
                    log(f"[KPM] ArticleViewer.open: {ok}")
                    return ok
                else:
                    log("[KPM] ArticleViewer instance could not be initialized")
                    return False
            except Exception as e:
                log_error(e, context="_do_open ArticleViewer")
                return False

        run_on_ui_thread(_do_open)
        return True
    except Exception as ex:
        log_error(ex, context="open_in_article_viewer")
        return False


def open_native_viewer(file_path: str, title: str = None, target_fragment=None) -> bool:
    try:
        frag = target_fragment or get_last_fragment()
        if not frag:
            return False

        ext = os.path.splitext(file_path)[1].lower()
        if ext in (".txt", ".log") or "log" in os.path.basename(file_path).lower():
            try:
                with open(file_path, "r", encoding="utf-8", errors="replace") as f:
                    content = f.read()
                return open_in_article_viewer(title or os.path.basename(file_path), content, lang="plain", target_fragment=frag, file_path=file_path)
            except Exception as ex:
                log_error(ex, context="open_native_viewer text fallback")

        from java.io import File
        PluginFileViewer = (
            find_class("com.exteragram.messenger.plugins.ui.components.PluginFileViewer")
            or find_class("app.exteraless.plugins.ui.components.PluginFileViewer")
        )
        if PluginFileViewer is not None:
            f = File(file_path)
            if f.exists() and f.isFile():
                name = title or os.path.basename(file_path)
                try:
                    inst = getattr(PluginFileViewer, "INSTANCE", None)
                except Exception:
                    inst = None
                try:
                    if inst is not None:
                        run_on_ui_thread(lambda: inst.open(frag, f, name))
                    else:
                        run_on_ui_thread(lambda: PluginFileViewer.open(frag, f, name))
                    return True
                except Exception as pfe:
                    log_error(pfe, context="PluginFileViewer.open")

        # Fallback to article viewer if PluginFileViewer failed or unavailable
        try:
            with open(file_path, "r", encoding="utf-8", errors="replace") as f:
                content = f.read()
            lang = _get_lang_from_name(title or file_path)
            return open_in_article_viewer(title or os.path.basename(file_path), content, lang=lang, target_fragment=frag, file_path=file_path)
        except Exception:
            pass

        return False
    except Exception as ex:
        log_error(ex, context="open_native_viewer")
        return False


def _cached_class(name):
    cls = _CLASS_CACHE.get(name)
    if cls is None:
        try:
            cls = find_class(name)
            if cls is not None:
                _CLASS_CACHE[name] = cls
        except Exception:
            cls = None
    return cls


def _format_size(size_bytes: int) -> str:
    if size_bytes < 1024:
        return f"{size_bytes} B"
    elif size_bytes < 1024 * 1024:
        return f"{size_bytes / 1024:.1f} KB"
    else:
        return f"{size_bytes / (1024 * 1024):.2f} MB"


def _get_lang_from_name(name: str) -> str:
    name = (name or "").lower()
    if name.endswith((".py", ".pyw", ".plugin", ".pyc")):
        return "python"
    if name.endswith((".json", ".webmanifest")):
        return "json"
    if name.endswith((".yml", ".yaml")):
        return "yaml"
    if name.endswith((".sh", ".bash")):
        return "bash"
    if name.endswith((".xml", ".html", ".htm")):
        return "html"
    if name.endswith((".ini", ".toml", ".conf", ".cfg", ".env")):
        return "ini"
    if name.endswith((".md", ".markdown")):
        return "markdown"
    if name.endswith((".txt", ".log")):
        return "text"
    if name.endswith((".png", ".jpg", ".jpeg", ".webp", ".gif", ".svg", ".ico")):
        return "image"
    return "python"


def _get_file_kind(name: str):
    lower = (name or "").lower()
    if lower.endswith(".pyc"):
        return "pyc", "⚡", "Python Bytecode"
    if lower.endswith((".py", ".pyw", ".plugin")):
        return "py", "🐍", "Python Script"
    if lower.endswith((".yml", ".yaml")):
        return "yml", "⚙️", "YAML Config"
    if lower.endswith((".json", ".webmanifest")):
        return "json", "⚙️", "JSON Data"
    if lower.endswith((".ini", ".toml", ".conf", ".cfg", ".env")):
        return "ini", "⚙️", "Config File"
    if lower.endswith((".txt", ".md", ".markdown", ".rst", ".log")):
        return "txt", "📄", "Documentation"
    if lower.endswith((".png", ".jpg", ".jpeg", ".webp", ".gif", ".svg", ".ico")):
        return "img", "🖼️", "Image Asset"
    return "bin", "📦", "Binary Asset"


def is_elyx_archive(source) -> bool:
    if source is None:
        return False
    try:
        zf = None
        source_label = "bytes"
        if isinstance(source, str):
            source_label = source
            if not os.path.isfile(source):
                log(f"[KPM] is_elyx_archive: file does not exist: {source}")
                return False
            if not zipfile.is_zipfile(source):
                try:
                    with open(source, "rb") as f:
                        head = f.read(4)
                    if not head.startswith((b"PK\x03\x04", b"PK\x05\x06", b"PK\x07\x08")):
                        log(f"[KPM] is_elyx_archive: not a zip header: {head[:4]}")
                        return False
                except Exception:
                    return False
            zf = zipfile.ZipFile(source, "r")
        elif isinstance(source, (bytes, bytearray)):
            source_label = f"bytes({len(source)})"
            if not source.startswith((b"PK\x03\x04", b"PK\x05\x06", b"PK\x07\x08")):
                log("[KPM] is_elyx_archive: byte stream missing PK zip header")
                return False
            zf = zipfile.ZipFile(io.BytesIO(source), "r")
        elif hasattr(source, "read"):
            source_label = "stream"
            zf = zipfile.ZipFile(source, "r")
        else:
            return False

        with zf:
            names = [n.lower() for n in zf.namelist()]
            log(f"[KPM] is_elyx_archive: inspecting {source_label}, entries={len(names)}")
            for marker in ("meta.yml", "meta.yaml", "meta.json", "refmap.json", "refmap.yml", "plugin.json"):
                if marker in names or any(n.endswith(f"/{marker}") for n in names):
                    log(f"[KPM] is_elyx_archive: confirmed Elyx archive by marker '{marker}'")
                    return True
            if any(n.endswith(".py") or n.endswith(".pyc") or n.endswith(".plugin") for n in names):
                log(f"[KPM] is_elyx_archive: confirmed Elyx archive by python/code entries in zip")
                return True
        log(f"[KPM] is_elyx_archive: zip is not an Elyx plugin: {names[:5]}")
        return False
    except Exception as ex:
        log_error(ex, context="is_elyx_archive")
        return False


def _get_code_cache_file(title: str, content_bytes_or_str) -> str:
    try:
        ApplicationLoader = _cached_class("org.telegram.messenger.ApplicationLoader")
        base_dir = None
        if ApplicationLoader:
            try:
                files_dir = ApplicationLoader.getFilesDirFixed()
                if files_dir:
                    base_dir = str(files_dir.getAbsolutePath())
            except Exception:
                pass
            if not base_dir:
                try:
                    ctx = getattr(ApplicationLoader, "applicationContext", None)
                    if ctx:
                        files_dir = ctx.getFilesDir()
                        if files_dir:
                            base_dir = str(files_dir.getAbsolutePath())
                except Exception:
                    pass
        if not base_dir:
            base_dir = "/tmp"
        cache_dir = os.path.join(base_dir, "kpm_code_cache")
        os.makedirs(cache_dir, exist_ok=True)
        safe_name = os.path.basename(title or "source.py")
        if not (safe_name.endswith(".py") or "." in safe_name):
            safe_name += ".py"
        target_path = os.path.join(cache_dir, safe_name)
        if isinstance(content_bytes_or_str, bytes):
            with open(target_path, "wb") as f:
                f.write(content_bytes_or_str)
        else:
            with open(target_path, "w", encoding="utf-8") as f:
                f.write(str(content_bytes_or_str or ""))
        return target_path
    except Exception as ex:
        log_error(ex, context="_get_code_cache_file")
        return None


def open_code_in_article_viewer(title: str, code_str: str, lang: str = None, url: str = None, target_fragment=None, use_sheet: bool = False) -> bool:
    """Directly open code in modern Web-IDE (native viewer when beta viewer is off)."""
    try:
        if not is_beta_code_viewer_enabled():
            cached = _get_code_cache_file(title or "source.py", code_str or "")
            if cached and open_native_viewer(cached, title=title or "source.py", target_fragment=target_fragment):
                return True
            _show_error("Native viewer unavailable")
            return False
        target_title = title or "source.py"
        target_lang = lang or _get_lang_from_name(target_title)
        code_text = code_str or ""
        log(f"[KPM] open_code_in_article_viewer: delegating to show_code_web_ide for '{target_title}' (chars={len(code_text)}, lang={target_lang})")
        return show_code_web_ide(code_text, title=target_title, lang=target_lang, target_fragment=target_fragment)
    except Exception as ex:
        log_error(ex, context="open_code_in_article_viewer")
        _show_error(str(ex))
        return False


def open_archive_in_article_viewer(archive_source, title: str = None, url: str = None, target_fragment=None, use_sheet: bool = False) -> bool:
    """Directly open archive in modern Web-IDE (native viewer when beta viewer is off)."""
    try:
        if not is_beta_code_viewer_enabled():
            if isinstance(archive_source, str):
                if open_native_viewer(archive_source, title=title, target_fragment=target_fragment):
                    return True
            elif isinstance(archive_source, (bytes, bytearray)):
                cached = _get_code_cache_file(title or "archive.eaf", bytes(archive_source))
                if cached and open_native_viewer(cached, title=title, target_fragment=target_fragment):
                    return True
            _show_error("Native viewer unavailable")
            return False
        target_title = title or "Plugin Archive"
        log(f"[KPM] open_archive_in_article_viewer: delegating to show_archive_web_ide for '{target_title}'")
        return show_archive_web_ide(archive_source, title=target_title, target_fragment=target_fragment)
    except Exception as ex:
        log_error(ex, context="open_archive_in_article_viewer")
        _show_error(str(ex))
        return False


def open_local_file_or_archive(file_path: str, title: str = None, url: str = None, target_fragment=None, use_sheet: bool = False) -> bool:
    """Open a local .eaf/.zip archive or code/text file in modern Web-IDE."""
    if not file_path or not os.path.exists(file_path):
        log(f"[KPM] open_local_file_or_archive: file not found: {file_path}")
        _show_error(f"File not found: {file_path}")
        return False

    raw_name = os.path.basename(file_path)
    target_title = title or raw_name
    log(f"[KPM] open_local_file_or_archive: path='{file_path}', title='{target_title}', target_fragment={target_fragment}")
    if not is_beta_code_viewer_enabled():
        if open_native_viewer(file_path, title=target_title, target_fragment=target_fragment):
            return True
        _show_error("Native viewer unavailable")
        return False
    try:
        if os.path.isdir(file_path):
            log(f"[KPM] open_local_file_or_archive: path is directory, opening as archive in Web-IDE")
            return show_archive_web_ide(file_path, title=target_title, target_fragment=target_fragment)

        is_elyx = is_elyx_archive(file_path)
        log(f"[KPM] open_local_file_or_archive: is_elyx_archive={is_elyx}")
        if is_elyx:
            return show_archive_web_ide(file_path, title=target_title, target_fragment=target_fragment)

        with open(file_path, "rb") as f:
            raw_bytes = f.read()

        if target_title.lower().endswith((".eaf", ".elyx", ".zip")):
            target_title = os.path.splitext(target_title)[0] + (os.path.splitext(raw_name)[1] or ".plugin")

        lower_name = target_title.lower()
        if lower_name.endswith(".pyc") or (len(raw_bytes) > 4 and (raw_bytes[2:4] in (b"\r\n", b"\x0d\x0a") or (raw_bytes[0] in (0xEE, 0x33, 0x55, 0x6F, 0xA7, 0xCB, 0x42, 0x61, 0x7E, 0x8B, 0xA1, 0x0D, 0x1B, 0x24, 0x31) and raw_bytes[1] == 0x0D))):
            text = _disassemble_pyc(raw_bytes, target_title)
            lang = "python"
        elif lower_name.endswith(".so") or raw_bytes.startswith(b"\x7fELF"):
            text = _inspect_elf(raw_bytes, target_title)
            lang = "c"
        elif lower_name.endswith(".dex") or raw_bytes.startswith(b"dex\n"):
            text = _inspect_dex(raw_bytes, target_title)
            lang = "java"
        else:
            text = raw_bytes.decode("utf-8", errors="replace")
            lang = _get_lang_from_name(target_title)

        log(f"[KPM] open_local_file_or_archive: opening in Web-IDE ({len(text)} chars, lang={lang})")
        return show_code_web_ide(text, title=target_title, lang=lang, target_fragment=target_fragment)
    except Exception as e:
        log_error(e, context="open_local_file_or_archive")
        _show_error(str(e))
        return False


def open_remote_code_or_archive(url: str, title: str = None, target_fragment=None):
    """Download remote URL and open archive or code file in modern Web-IDE."""
    if not url:
        return

    origin_fragment = target_fragment or get_last_fragment()
    log(f"[KPM] open_remote_code_or_archive: requested url='{url}', title='{title}', origin_fragment={origin_fragment}")
    state = {
        "cancelled": False,
        "dismissed_by_app": False,
        "dialog": None,
        "origin_fragment": origin_fragment,
    }

    def _show_loading():
        try:
            frag = state["origin_fragment"] or get_last_fragment()
            if not frag:
                return
            activity = frag.getParentActivity() if hasattr(frag, "getParentActivity") else None
            if not activity:
                return
            AlertDialog = _cached_class("org.telegram.ui.ActionBar.AlertDialog")
            if not AlertDialog:
                return
            pd = AlertDialog(activity, 3)
            pd.setCanCancel(True)

            from java import dynamic_proxy
            OnCancelListener = _cached_class("android.content.DialogInterface$OnCancelListener")
            if OnCancelListener:
                class _CancelProxy(dynamic_proxy(OnCancelListener)):
                    def onCancel(self, dialog):
                        if not state["dismissed_by_app"]:
                            state["cancelled"] = True
                            log(f"[KPM] open_remote_code cancelled by user: {url}")
                pd.setOnCancelListener(_CancelProxy())

            state["dialog"] = pd
            pd.show()
        except Exception as ex:
            log_error(ex, context="show loading dialog")

    run_on_ui_thread(_show_loading)

    def _worker():
        try:
            from ...store import Store
            log(f"[KPM] open_remote_code_or_archive: downloading bytes for {url} via Store.fetch_bytes...")
            data = Store.fetch_bytes(url)
            if state["cancelled"]:
                log(f"[KPM] open_remote_code_or_archive: download aborted (cancelled)")
                return

            raw_name = os.path.basename(url.split("?")[0]) or "plugin.py"
            target_title = title or raw_name
            log(f"[KPM] open_remote_code_or_archive: downloaded {len(data)} bytes, target_title='{target_title}'")

            cached_path = _get_code_cache_file(target_title, data)
            log(f"[KPM] open_remote_code_or_archive: saved cache to {cached_path}")

            def _on_success():
                state["dismissed_by_app"] = True
                if state["dialog"]:
                    try:
                        if state["dialog"].isShowing():
                            state["dialog"].dismiss()
                    except Exception:
                        pass
                    state["dialog"] = None
                if state["cancelled"]:
                    return

                active_frag = state["origin_fragment"] or get_last_fragment()
                if cached_path and os.path.exists(cached_path):
                    open_local_file_or_archive(cached_path, title=target_title, url=url, target_fragment=active_frag, use_sheet=False)
                else:
                    is_elyx = is_elyx_archive(data)
                    if is_elyx:
                        open_archive_in_article_viewer(data, title=target_title, url=url, target_fragment=active_frag)
                    else:
                        text = data.decode("utf-8", errors="replace")
                        lang = _get_lang_from_name(target_title)
                        open_code_in_article_viewer(text, title=target_title, lang=lang, url=url, target_fragment=active_frag)

            run_on_ui_thread(_on_success)
        except Exception as ex:
            log_error(ex, context="open_remote_code worker")
            def _on_err():
                state["dismissed_by_app"] = True
                if state["dialog"]:
                    try:
                        if state["dialog"].isShowing():
                            state["dialog"].dismiss()
                    except Exception:
                        pass
                if not state["cancelled"]:
                    _show_error(str(ex))
            run_on_ui_thread(_on_err)

    threading.Thread(target=_worker, daemon=True).start()


def _show_error(err_msg: str):
    def _ui():
        try:
            fragment = get_last_fragment()
            if fragment:
                BulletinFactory = _cached_class("org.telegram.ui.Components.BulletinFactory")
                R_raw = _cached_class("org.telegram.messenger.R$raw")
                if BulletinFactory and R_raw:
                    BulletinFactory.of(fragment).createSimpleBulletin(
                        getattr(R_raw, "error", 0),
                        f"Failed to view code: {err_msg}"
                    ).show()
        except Exception:
            pass
    run_on_ui_thread(_ui)
