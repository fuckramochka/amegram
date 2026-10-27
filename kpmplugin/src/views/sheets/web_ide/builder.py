import base64
import html
import io
import json
import os
import zipfile

from hook_utils import find_class
from org.telegram.ui.ActionBar import Theme

from ....decompiler import (
    decompile_pyc,
    inspect_dex,
    inspect_elf,
    inspect_jvm_class,
    extract_base64_payloads,
)
from ....methods import log, log_error, _tr

_CLASS_CACHE = {}
_TEMPLATE_CACHE = [None]


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
    if name.endswith((".py", ".pyw", ".plugin")):
        return "python"
    if name.endswith((".js", ".mjs", ".cjs", ".jsx", ".ts", ".tsx")):
        return "javascript"
    if name.endswith((".css", ".scss", ".sass", ".less")):
        return "css"
    if name.endswith((".json", ".webmanifest", ".jsonc", ".json5", ".lock")):
        return "json"
    if name.endswith((".yml", ".yaml")):
        return "yaml"
    if name.endswith((".sh", ".bash", ".zsh", ".fish")):
        return "bash"
    if name.endswith((".xml", ".html", ".htm", ".xhtml")):
        return "html"
    if name.endswith((".ini", ".toml", ".conf", ".cfg", ".env", ".properties", ".inf")):
        return "ini"
    if name.endswith((".md", ".markdown", ".rst")):
        return "markdown"
    if name.endswith((".sql",)):
        return "sql"
    if name.endswith((".c", ".cpp", ".cc", ".cxx", ".h", ".hpp")):
        return "c"
    if name.endswith((".java", ".kt", ".kts")):
        return "java"
    if name.endswith((".txt", ".log", ".csv", ".tsv", ".gitignore", ".gitattributes", ".editorconfig", ".license", ".licence")):
        return "text"
    if name.endswith((".png", ".jpg", ".jpeg", ".webp", ".gif", ".svg", ".ico")):
        return "image"
    if name.endswith((".mp3", ".ogg", ".wav", ".m4a", ".aac", ".flac", ".opus")):
        return "audio"
    if name.endswith((".mp4", ".webm", ".mkv", ".mov", ".avi")):
        return "video"
    if name.endswith(".pyc"):
        return "pyc"
    if name.endswith(".dex"):
        return "dex"
    if name.endswith(".class"):
        return "class"
    if name.endswith(".so"):
        return "so"
    return "binary"


def _get_file_icon(name: str) -> str:
    lower = (name or "").lower()
    if "overview" in lower or "analysis" in lower:
        return "📋"
    if lower.endswith(".pyc"):
        return "⚡"
    if lower.endswith((".dex", ".java", ".kt", ".kts", ".class")):
        return "📱"
    if lower.endswith((".so", ".c", ".cpp", ".cc", ".cxx", ".h", ".hpp")):
        return "⚙️"
    if lower.endswith((".py", ".pyw", ".plugin")):
        return "🐍"
    if lower.endswith((".js", ".mjs", ".cjs", ".ts", ".jsx", ".tsx")):
        return "📜"
    if lower.endswith((".css", ".scss", ".sass", ".less")):
        return "🎨"
    if lower.endswith((".html", ".htm", ".xhtml")):
        return "🌐"
    if lower.endswith((".yml", ".yaml", ".json", ".webmanifest")):
        return "⚙️"
    if lower.endswith((".ini", ".toml", ".conf", ".cfg", ".env", ".properties")):
        return "⚙️"
    if lower.endswith((".txt", ".md", ".markdown", ".rst", ".log", ".sql")):
        return "📄"
    if lower.endswith((".png", ".jpg", ".jpeg", ".webp", ".gif", ".svg", ".ico")):
        return "🖼️"
    if lower.endswith((".mp3", ".ogg", ".wav", ".m4a", ".aac", ".flac", ".opus")):
        return "🎵"
    if lower.endswith((".mp4", ".webm", ".mkv", ".mov", ".avi")):
        return "🎬"
    return "📦"


def _load_res_file(rel_name: str) -> str:
    try:
        from elyx import assets
        asset = assets.get(rel_name)
        if asset:
            return asset.content_string()
    except Exception:
        pass

    cand_paths = [
        os.path.join(os.path.dirname(__file__), "..", "..", "..", "..", "res", rel_name),
        os.path.join(os.path.dirname(__file__), "..", "..", "..", "res", rel_name),
        os.path.join(os.path.dirname(__file__), rel_name),
    ]
    for p in cand_paths:
        if os.path.isfile(p):
            try:
                with open(p, "r", encoding="utf-8") as f:
                    return f.read()
            except Exception:
                pass
    return ""


def load_html_template() -> str:
    if _TEMPLATE_CACHE[0]:
        return _TEMPLATE_CACHE[0]

    html_tmpl = _load_res_file("web_ide/index.html") or _load_res_file("web_ide.html")
    css_str = _load_res_file("web_ide/styles.css")
    js_str = _load_res_file("web_ide/app.js")

    if html_tmpl:
        if css_str and "__CSS__" in html_tmpl:
            html_tmpl = html_tmpl.replace("__CSS__", css_str)
        if js_str and "__JS__" in html_tmpl:
            html_tmpl = html_tmpl.replace("__JS__", js_str)
        _TEMPLATE_CACHE[0] = html_tmpl
        return _TEMPLATE_CACHE[0]

    return "<html><body><h3>Template missing</h3></body></html>"


def _int_to_hex(color_int: int) -> str:
    r = (color_int >> 16) & 0xFF
    g = (color_int >> 8) & 0xFF
    b = color_int & 0xFF
    return f"#{r:02x}{g:02x}{b:02x}"


def get_theme_vars(is_dark: bool) -> str:
    def _c(key_name, fallback_hex):
        try:
            Theme_cls = _cached_class("org.telegram.ui.ActionBar.Theme")
            if Theme_cls and hasattr(Theme_cls, key_name):
                val = getattr(Theme_cls, key_name)
                color_int = Theme_cls.getColor(val)
                return _int_to_hex(color_int)
        except Exception:
            pass
        return fallback_hex

    bg_main = _c("key_windowBackgroundWhite", "#141416" if is_dark else "#ffffff")
    bg_surface = _c("key_windowBackgroundGray", "#1e1e24" if is_dark else "#f6f8fa")
    bg_header = _c("key_actionBarDefault", "#19191e" if is_dark else "#ffffff")
    text_main = _c("key_windowBackgroundWhiteBlackText", "#f0f0f5" if is_dark else "#1f2328")
    text_muted = _c("key_windowBackgroundWhiteGrayText", "#8a8a9e" if is_dark else "#656d76")
    accent = _c("key_featuredStickers_addButton", "#5b8cff" if is_dark else "#0969da")
    border = _c("key_divider", "#2c2c36" if is_dark else "#d0d7de")

    if is_dark:
        bg_editor = _c("key_dialogBackground", "#121214")
        bg_sidebar = bg_surface
        gutter_bg = bg_surface
        line_num = text_muted
        tree_hover = "rgba(255, 255, 255, 0.06)"
        tree_active = "rgba(91, 140, 255, 0.16)"
        tok_kw = "#f7768e"
        tok_str = "#9ece6a"
        tok_fn = "#7aa2f7"
        tok_comment = "#565f89"
        tok_num = "#ff9e64"
        tok_key = "#bb9af7"
        tok_bool = "#e0af68"
        tok_dec = "#2ac3de"
    else:
        bg_editor = "#ffffff"
        bg_sidebar = bg_surface
        gutter_bg = "#f6f8fa"
        line_num = text_muted
        tree_hover = "rgba(0, 0, 0, 0.05)"
        tree_active = "rgba(9, 105, 218, 0.12)"
        tok_kw = "#cf222e"
        tok_str = "#0a3069"
        tok_fn = "#8250df"
        tok_comment = "#6e7781"
        tok_num = "#0550ae"
        tok_key = "#953800"
        tok_bool = "#cf222e"
        tok_dec = "#116329"

    return f"""
      --bg-main: {bg_main};
      --bg-surface: {bg_surface};
      --bg-header: {bg_header};
      --bg-sidebar: {bg_sidebar};
      --bg-editor: {bg_editor};
      --border-color: {border};
      --text-main: {text_main};
      --text-muted: {text_muted};
      --accent: {accent};
      --accent-hover: {accent};
      --line-num: {line_num};
      --gutter-bg: {gutter_bg};
      --tree-hover: {tree_hover};
      --tree-active: {tree_active};
      --token-kw: {tok_kw};
      --token-str: {tok_str};
      --token-fn: {tok_fn};
      --token-comment: {tok_comment};
      --token-num: {tok_num};
      --token-key: {tok_key};
      --token-bool: {tok_bool};
      --token-dec: {tok_dec};
    """


def _get_ide_strings() -> dict:
    return {
        "files": _tr("ide_files"),
        "files_lower": _tr("ide_files_lower"),
        "click_to_copy": _tr("ide_click_to_copy"),
        "search_placeholder": _tr("ide_search_placeholder"),
        "code_copied": _tr("ide_code_copied"),
        "path": _tr("ide_path"),
        "size": _tr("ide_size"),
        "type": _tr("ide_type"),
        "bytes": _tr("ide_bytes"),
        "audio_file": _tr("ide_audio_file"),
        "video_file": _tr("ide_video_file"),
        "binary_resource": _tr("ide_binary_resource"),
        "hex_dump": _tr("ide_hex_dump"),
        "audio_not_supported": _tr("ide_audio_not_supported"),
        "video_not_supported": _tr("ide_video_not_supported"),
        "file_too_large": _tr("ide_file_too_large"),
    }


def _inject_template(tmpl: str, title: str, theme_vars: str, files_map: dict, default_file: str) -> str:
    cleaned_files_map = {}
    for path, fobj in files_map.items():
        if isinstance(fobj, dict):
            cleaned_files_map[path] = {
                k: v for k, v in fobj.items()
                if k != "raw_bytes" and not isinstance(v, (bytes, bytearray))
            }
        else:
            cleaned_files_map[path] = fobj

    def _json_fallback(obj):
        if isinstance(obj, (bytes, bytearray)):
            return None
        return str(obj)

    files_json = json.dumps(cleaned_files_map, default=_json_fallback)
    files_b64 = base64.b64encode(files_json.encode("utf-8")).decode("ascii")
    total_size_str = _format_size(sum(f.get("size", 0) for f in files_map.values() if isinstance(f, dict)))
    ide_strings = _get_ide_strings()
    ide_strings_json = json.dumps(ide_strings)

    res = tmpl.replace("__TITLE__", html.escape(title or "Plugin IDE"))
    res = res.replace("__THEME_VARS__", theme_vars)
    res = res.replace("__FILES_COUNT__", str(len(files_map)))
    res = res.replace("__TOTAL_SIZE__", total_size_str)
    res = res.replace("__FILES_B64__", files_b64)
    res = res.replace("__DEFAULT_FILE_JSON__", json.dumps(default_file or ""))

    res = res.replace("__TXT_FILES__", html.escape(ide_strings["files"]))
    res = res.replace("__TXT_FILES_LOWER__", html.escape(ide_strings["files_lower"]))
    res = res.replace("__TXT_CLICK_TO_COPY__", html.escape(ide_strings["click_to_copy"]))
    res = res.replace("__TXT_SEARCH_PLACEHOLDER__", html.escape(ide_strings["search_placeholder"]))
    res = res.replace("__TXT_CODE_COPIED__", html.escape(ide_strings["code_copied"]))
    res = res.replace("__STRINGS_JSON__", ide_strings_json)

    return res


def build_archive_payload(archive_source, title: str, is_dark: bool) -> str:
    """
    Builds the JSON and HTML payload for a full archive with decompiler support and auto-overview analysis.
    """
    zf = None
    if isinstance(archive_source, str) and os.path.isdir(archive_source):
        buf = io.BytesIO()
        with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as z_out:
            for root, dirs, files in os.walk(archive_source):
                for f_name in files:
                    full_p = os.path.join(root, f_name)
                    rel_p = os.path.relpath(full_p, archive_source).replace("\\", "/")
                    z_out.write(full_p, rel_p)
        buf.seek(0)
        zf = zipfile.ZipFile(buf, "r")
    elif isinstance(archive_source, str) and os.path.isfile(archive_source):
        zf = zipfile.ZipFile(archive_source, "r")
    elif isinstance(archive_source, (bytes, bytearray)):
        zf = zipfile.ZipFile(io.BytesIO(archive_source), "r")
    elif hasattr(archive_source, "read"):
        zf = zipfile.ZipFile(archive_source, "r")
    else:
        cand_str = str(archive_source)
        if os.path.isdir(cand_str):
            buf = io.BytesIO()
            with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as z_out:
                for root, dirs, files in os.walk(cand_str):
                    for f_name in files:
                        full_p = os.path.join(root, f_name)
                        rel_p = os.path.relpath(full_p, cand_str).replace("\\", "/")
                        z_out.write(full_p, rel_p)
            buf.seek(0)
            zf = zipfile.ZipFile(buf, "r")
        elif os.path.isfile(cand_str):
            zf = zipfile.ZipFile(cand_str, "r")
        else:
            zf = zipfile.ZipFile(archive_source, "r")

    entries = [info for info in zf.infolist() if not info.is_dir()]
    files_map = {}
    default_file = None

    MAX_TEXT_LEN = 250 * 1024
    MAX_MEDIA_LEN = 2 * 1024 * 1024

    for info in entries:
        norm_name = info.filename.replace("\\", "/").strip("/")
        if not norm_name:
            continue
        kind = _get_lang_from_name(norm_name)
        icon = _get_file_icon(norm_name)
        size_str = _format_size(info.file_size)

        raw_bytes = zf.read(info.filename)

        file_obj = {
            "name": os.path.basename(norm_name),
            "path": norm_name,
            "size": info.file_size,
            "size_str": size_str,
            "kind": kind,
            "icon": icon,
            "crc": f"0x{info.CRC & 0xFFFFFFFF:08X}",
        }

        if kind == "pyc":
            dis_code = decompile_pyc(raw_bytes, norm_name)
            if len(dis_code) > MAX_TEXT_LEN:
                trunc_msg = _tr("ide_content_truncated").format(MAX_TEXT_LEN // 1024)
                dis_code = dis_code[:MAX_TEXT_LEN] + f"\n\n# ... [{trunc_msg}]"
            file_obj["type"] = "text"
            file_obj["content"] = dis_code
            file_obj["lines"] = len(dis_code.splitlines()) if dis_code else 1
            file_obj["kind"] = "python"
        elif kind == "so":
            elf_report = inspect_elf(raw_bytes, norm_name)
            if len(elf_report) > MAX_TEXT_LEN:
                trunc_msg = _tr("ide_content_truncated").format(MAX_TEXT_LEN // 1024)
                elf_report = elf_report[:MAX_TEXT_LEN] + f"\n\n// ... [{trunc_msg}]"
            file_obj["type"] = "text"
            file_obj["content"] = elf_report
            file_obj["lines"] = len(elf_report.splitlines()) if elf_report else 1
            file_obj["kind"] = "c"
        elif kind == "dex":
            dex_report = inspect_dex(raw_bytes, norm_name)
            if len(dex_report) > MAX_TEXT_LEN:
                trunc_msg = _tr("ide_content_truncated").format(MAX_TEXT_LEN // 1024)
                dex_report = dex_report[:MAX_TEXT_LEN] + f"\n\n// ... [{trunc_msg}]"
            file_obj["type"] = "text"
            file_obj["content"] = dex_report
            file_obj["lines"] = len(dex_report.splitlines()) if dex_report else 1
            file_obj["kind"] = "java"
        elif kind == "class":
            class_report = inspect_jvm_class(raw_bytes, norm_name)
            if len(class_report) > MAX_TEXT_LEN:
                trunc_msg = _tr("ide_content_truncated").format(MAX_TEXT_LEN // 1024)
                class_report = class_report[:MAX_TEXT_LEN] + f"\n\n// ... [{trunc_msg}]"
            file_obj["type"] = "text"
            file_obj["content"] = class_report
            file_obj["lines"] = len(class_report.splitlines()) if class_report else 1
            file_obj["kind"] = "java"
        elif kind in ("python", "javascript", "css", "json", "yaml", "bash", "html", "ini", "markdown", "sql", "text", "c", "java"):
            text_val = raw_bytes.decode("utf-8", errors="replace")
            if len(text_val) > MAX_TEXT_LEN:
                trunc_msg = _tr("ide_content_truncated").format(MAX_TEXT_LEN // 1024)
                text_val = text_val[:MAX_TEXT_LEN] + f"\n\n# ... [{trunc_msg}]"
            file_obj["type"] = "text"
            file_obj["content"] = text_val
            file_obj["lines"] = len(text_val.splitlines()) if text_val else 1
        elif kind == "image":
            if len(raw_bytes) > MAX_MEDIA_LEN:
                file_obj["type"] = "large_asset"
                file_obj["note"] = _tr("ide_img_too_large").format(_format_size(len(raw_bytes)))
            else:
                ext = os.path.splitext(norm_name)[1].lower().replace(".", "")
                mime = "image/png"
                if ext in ("jpg", "jpeg"):
                    mime = "image/jpeg"
                elif ext == "webp":
                    mime = "image/webp"
                elif ext == "gif":
                    mime = "image/gif"
                elif ext == "svg":
                    mime = "image/svg+xml"
                b64_data = base64.b64encode(raw_bytes).decode("ascii")
                file_obj["type"] = "image"
                file_obj["data_uri"] = f"data:{mime};base64,{b64_data}"
        elif kind == "audio":
            if len(raw_bytes) > MAX_MEDIA_LEN:
                file_obj["type"] = "large_asset"
                file_obj["note"] = _tr("ide_audio_too_large").format(_format_size(len(raw_bytes)))
            else:
                ext = os.path.splitext(norm_name)[1].lower().replace(".", "")
                mime = f"audio/{ext}"
                if ext == "mp3":
                    mime = "audio/mpeg"
                elif ext in ("m4a", "aac"):
                    mime = "audio/mp4"
                elif ext == "opus":
                    mime = "audio/ogg"
                b64_data = base64.b64encode(raw_bytes).decode("ascii")
                file_obj["type"] = "audio"
                file_obj["data_uri"] = f"data:{mime};base64,{b64_data}"
        elif kind == "video":
            if len(raw_bytes) > MAX_MEDIA_LEN:
                file_obj["type"] = "large_asset"
                file_obj["note"] = _tr("ide_video_too_large").format(_format_size(len(raw_bytes)))
            else:
                ext = os.path.splitext(norm_name)[1].lower().replace(".", "")
                mime = f"video/{ext}"
                if ext == "mp4":
                    mime = "video/mp4"
                elif ext == "webm":
                    mime = "video/webm"
                elif ext in ("mov", "mkv"):
                    mime = "video/mp4"
                b64_data = base64.b64encode(raw_bytes).decode("ascii")
                file_obj["type"] = "video"
                file_obj["data_uri"] = f"data:{mime};base64,{b64_data}"
        else:
            if len(raw_bytes) <= MAX_TEXT_LEN:
                try:
                    text_val = raw_bytes.decode("utf-8")
                    if len(text_val) > MAX_TEXT_LEN:
                        trunc_msg = _tr("ide_content_truncated").format(MAX_TEXT_LEN // 1024)
                        text_val = text_val[:MAX_TEXT_LEN] + f"\n\n# ... [{trunc_msg}]"
                    file_obj["type"] = "text"
                    file_obj["content"] = text_val
                    file_obj["kind"] = "text"
                    file_obj["lines"] = len(text_val.splitlines()) if text_val else 1
                except UnicodeDecodeError:
                    hex_head = " ".join(f"{b:02X}" for b in raw_bytes[:64])
                    file_obj["type"] = "binary"
                    file_obj["hex_preview"] = hex_head
                    file_obj["raw_len"] = len(raw_bytes)
            else:
                hex_head = " ".join(f"{b:02X}" for b in raw_bytes[:64])
                file_obj["type"] = "binary"
                file_obj["hex_preview"] = hex_head
                file_obj["raw_len"] = len(raw_bytes)

        files_map[norm_name] = file_obj

    # Extract and decompile any Base64-encoded payloads inside text/code files
    for fname, fobj in list(files_map.items()):
        if fobj.get("type") == "text" and fobj.get("content"):
            try:
                extracted = extract_base64_payloads(fobj["content"], fname)
                for item in extracted:
                    web_item = {k: v for k, v in item.items() if k != "raw_bytes" and not isinstance(v, (bytes, bytearray))}
                    files_map[web_item["path"]] = web_item
            except Exception as e:
                log_error(e, context="extract_base64_payloads in build_archive_payload")

    if not default_file:
        priority_order = ("plugin.json", "meta.yaml", "meta.yml", "meta.json", "main.py", "__init__.py")
        for p in priority_order:
            for fname in files_map:
                if fname.lower() == p or fname.lower().endswith(f"/{p}"):
                    default_file = fname
                    break
            if default_file:
                break

    if not default_file and files_map:
        for fname, fobj in files_map.items():
            if fobj["type"] == "text":
                default_file = fname
                break
        if not default_file:
            default_file = next(iter(files_map.keys()))

    theme_vars = get_theme_vars(is_dark)
    tmpl = load_html_template()
    return _inject_template(tmpl, title or "Plugin IDE", theme_vars, files_map, default_file or "")


def build_single_code_payload(code_str: str, title: str, lang: str, is_dark: bool) -> str:
    """
    Builds the payload for displaying a single source code file in Web IDE.
    """
    target_title = title or "plugin.py"
    target_lang = lang or _get_lang_from_name(target_title)
    icon = _get_file_icon(target_title)
    text_val = code_str or ""
    size_bytes = len(text_val.encode("utf-8"))
    size_str = _format_size(size_bytes)
    lines_count = len(text_val.splitlines()) if text_val else 1

    file_obj = {
        "name": target_title,
        "path": target_title,
        "size": size_bytes,
        "size_str": size_str,
        "kind": target_lang,
        "icon": icon,
        "crc": "0x00000000",
        "type": "text",
        "content": text_val,
        "lines": lines_count,
    }
    files_map = {target_title: file_obj}

    # Extract any embedded Base64 payloads from single code view
    try:
        extracted = extract_base64_payloads(text_val, target_title)
        for item in extracted:
            web_item = {k: v for k, v in item.items() if k != "raw_bytes" and not isinstance(v, (bytes, bytearray))}
            files_map[web_item["path"]] = web_item
    except Exception as e:
        log_error(e, context="extract_base64_payloads in build_single_code_payload")

    theme_vars = get_theme_vars(is_dark)
    tmpl = load_html_template()
    return _inject_template(tmpl, target_title, theme_vars, files_map, target_title)
