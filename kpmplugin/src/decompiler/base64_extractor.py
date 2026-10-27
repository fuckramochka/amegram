import ast
import base64
import binascii
import hashlib
import io
import json
import os
import re
import zipfile
import zlib

from .pyc import decompile_pyc
from .dex import inspect_dex
from .elf import inspect_elf
from .jvm_class import inspect_jvm_class


B64_CALL_RE = re.compile(
    r"""(?:b64decode|decodebytes|decodestring|urlsafe_b64decode|b85decode|a85decode|b32decode|b16decode|unhexlify|fromhex)\s*\(\s*([\s\S]*?)\s*\)""",
    re.MULTILINE,
)

B64_LITERAL_RE = re.compile(
    r"""(?:['"]{1,3}|b['"]{1,3})([A-Za-z0-9+/=_\-\s]{24,})(?:['"]{1,3})""",
    re.MULTILINE,
)

TRIPLE_QUOTE_RE = re.compile(
    r"""(?:[burbURB]{1,3})?(?:'''([\s\S]*?)'''|""\"([\s\S]*?)""\")""",
    re.MULTILINE,
)

B64_ASSIGN_RE = re.compile(
    r"""(?:code|payload|dex|data|content|source|script|plugin|bytecode|encoded|archive|zip_data|b64|base64|blob|raw)\s*[:=]\s*([^\n;]+(?:\n[ \t]+[^\n;]+)*)""",
    re.IGNORECASE | re.MULTILINE,
)

CONCAT_CHUNK_RE = re.compile(
    r"""(?:(?:[burbURB]{0,3}['"]([A-Za-z0-9+/=_\-]{4,})['"])\s*(?:[\+,]?)\s*){2,}""",
    re.MULTILINE,
)

JOIN_BLOCK_RE = re.compile(
    r"""\.join\s*\(\s*[\[\(]([\s\S]*?)[\]\)]\s*\)""",
    re.MULTILINE,
)

MIME_BLOCK_RE = re.compile(
    r"""(?:^[ \t]*[A-Za-z0-9+/=]{32,80}[ \t]*$\r?\n){3,}""",
    re.MULTILINE,
)


def _format_size(size_bytes: int) -> str:
    if size_bytes < 1024:
        return f"{size_bytes} B"
    elif size_bytes < 1024 * 1024:
        return f"{size_bytes / 1024:.1f} KB"
    else:
        return f"{size_bytes / (1024 * 1024):.2f} MB"


def _try_decompress(raw_bytes: bytes) -> tuple[bytes, str]:
    if not raw_bytes or len(raw_bytes) < 4:
        return raw_bytes, ""

    # 1. zlib / gzip / raw deflate
    for wbits, label in (
        (zlib.MAX_WBITS, "zlib"),
        (zlib.MAX_WBITS | 16, "gzip"),
        (-zlib.MAX_WBITS, "deflate"),
    ):
        try:
            decomp = zlib.decompress(raw_bytes, wbits)
            if decomp and len(decomp) > 0 and decomp != raw_bytes:
                return decomp, label
        except Exception:
            pass

    # 2. bz2
    if raw_bytes.startswith(b"BZh"):
        try:
            import bz2
            decomp = bz2.decompress(raw_bytes)
            if decomp and len(decomp) > 0:
                return decomp, "bz2"
        except Exception:
            pass

    # 3. lzma / xz
    if raw_bytes.startswith((b"\xfd7zXZ\x00", b"]\x00\x00")):
        try:
            import lzma
            decomp = lzma.decompress(raw_bytes)
            if decomp and len(decomp) > 0:
                return decomp, "lzma"
        except Exception:
            pass

    return raw_bytes, ""


def _is_probable_pyc(data: bytes) -> bool:
    if not data or len(data) < 16:
        return False
    if data[2:4] in (b"\r\n", b"\x0d\x0a") or (
        data[0] in (0xEE, 0x33, 0x55, 0x6F, 0xA7, 0xCB, 0x42, 0x61, 0x7E, 0x8B, 0xA1, 0x0D, 0x1B, 0x24, 0x31)
        and data[1] == 0x0D
    ):
        return True
    if b"co_code" in data or b"co_names" in data or b"co_consts" in data or b"__code__" in data:
        return True
    return False


def _is_probable_python_source(text: str) -> bool:
    if not text or len(text) < 10:
        return False
    keywords = (
        "import ", "from ", "def ", "class ", "return ", "if __name__",
        "self.", "print(", "@hook", "eval(", "exec(", "def on_load",
        "lambda ", "async def ", "await ", "try:", "except ", "except:",
        "for ", "while ", "with ", "global ", "nonlocal "
    )
    count = sum(1 for kw in keywords if kw in text)
    return count >= 2 or (count >= 1 and ("\n" in text or ";" in text))


def _extract_from_ast(source_text: str, candidates: set) -> None:
    try:
        tree = ast.parse(source_text)
    except Exception:
        return

    var_map = {}

    def _eval_node_str(node) -> str | None:
        if node is None:
            return None
        if isinstance(node, ast.Constant):
            if isinstance(node.value, str):
                return node.value
            elif isinstance(node.value, (bytes, bytearray)):
                try:
                    return node.value.decode("ascii")
                except Exception:
                    try:
                        return node.value.decode("utf-8", errors="ignore")
                    except Exception:
                        return None
        elif isinstance(node, ast.BinOp) and isinstance(node.op, ast.Add):
            left = _eval_node_str(node.left)
            right = _eval_node_str(node.right)
            if left is not None and right is not None:
                return left + right
        elif isinstance(node, (ast.List, ast.Tuple)):
            parts = []
            for elt in node.elts:
                s = _eval_node_str(elt)
                if s is not None:
                    parts.append(s)
                else:
                    return None
            return "".join(parts)
        elif isinstance(node, ast.Name):
            if node.id in var_map:
                return var_map[node.id]
        elif isinstance(node, ast.Call):
            if isinstance(node.func, ast.Attribute) and node.func.attr == "join":
                if node.args:
                    arg_str = _eval_node_str(node.args[0])
                    if arg_str is not None:
                        sep = _eval_node_str(node.func.value) or ""
                        return sep.join(node.args[0].elts) if isinstance(node.args[0], (ast.List, ast.Tuple)) else arg_str
        return None

    # Pass 1: populate symbol table
    for node in ast.walk(tree):
        if isinstance(node, ast.Assign):
            for target in node.targets:
                if isinstance(target, ast.Name):
                    val_str = _eval_node_str(node.value)
                    if val_str:
                        var_map[target.id] = val_str
        elif isinstance(node, ast.AnnAssign):
            if isinstance(node.target, ast.Name) and node.value:
                val_str = _eval_node_str(node.value)
                if val_str:
                    var_map[node.target.id] = val_str

    # Pass 2: find all string constants, additions, joins, decode calls
    for node in ast.walk(tree):
        if isinstance(node, ast.Constant):
            if isinstance(node.value, str):
                cleaned = re.sub(r"\s+", "", node.value)
                if len(cleaned) >= 24:
                    candidates.add(cleaned)
            elif isinstance(node.value, (bytes, bytearray)):
                try:
                    s = node.value.decode("ascii", errors="ignore")
                    cleaned = re.sub(r"\s+", "", s)
                    if len(cleaned) >= 24:
                        candidates.add(cleaned)
                except Exception:
                    pass

        elif isinstance(node, ast.BinOp) and isinstance(node.op, ast.Add):
            res = _eval_node_str(node)
            if res:
                cleaned = re.sub(r"\s+", "", res)
                if len(cleaned) >= 24:
                    candidates.add(cleaned)

        elif isinstance(node, (ast.List, ast.Tuple)):
            res = _eval_node_str(node)
            if res:
                cleaned = re.sub(r"\s+", "", res)
                if len(cleaned) >= 24:
                    candidates.add(cleaned)

        elif isinstance(node, ast.Call):
            func_name = ""
            if isinstance(node.func, ast.Name):
                func_name = node.func.id
            elif isinstance(node.func, ast.Attribute):
                func_name = node.func.attr

            if func_name in (
                "b64decode", "decodebytes", "decodestring", "urlsafe_b64decode",
                "b85decode", "a85decode", "b32decode", "b16decode", "unhexlify", "fromhex",
                "decompress"
            ):
                if node.args:
                    arg_val = _eval_node_str(node.args[0])
                    if arg_val:
                        cleaned = re.sub(r"\s+", "", arg_val)
                        if len(cleaned) >= 16:
                            candidates.add(cleaned)


def _extract_from_text(source_text: str, candidates: set) -> None:
    # 1. Decode call blocks (multiline b64decode(...), decodebytes(...))
    for match in B64_CALL_RE.finditer(source_text):
        inner = match.group(1)
        # Extract quoted strings inside call
        quotes = re.findall(r"""['"]([^'"]+)['"]""", inner)
        if quotes:
            joined = "".join(re.sub(r"\s+", "", q) for q in quotes)
            if len(joined) >= 16:
                candidates.add(joined)
        else:
            clean_inner = re.sub(r"[^A-Za-z0-9+/=_\-]", "", inner)
            if len(clean_inner) >= 16:
                candidates.add(clean_inner)

    # 2. Triple-quoted multiline blocks
    for match in TRIPLE_QUOTE_RE.finditer(source_text):
        content = match.group(1) or match.group(2)
        if content:
            cleaned = re.sub(r"\s+", "", content)
            if len(cleaned) >= 24:
                candidates.add(cleaned)

    # 3. Chained string literals across lines ("chunk1" \n "chunk2" \n "chunk3")
    for match in CONCAT_CHUNK_RE.finditer(source_text):
        full_match = match.group(0)
        quotes = re.findall(r"""['"]([A-Za-z0-9+/=_\-]+)['"]""", full_match)
        if len(quotes) >= 2:
            joined = "".join(quotes)
            if len(joined) >= 24:
                candidates.add(joined)

    # 4. Multiline .join([...]) calls
    for match in JOIN_BLOCK_RE.finditer(source_text):
        inner = match.group(1)
        quotes = re.findall(r"""['"]([^'"]+)['"]""", inner)
        if quotes:
            joined = "".join(re.sub(r"\s+", "", q) for q in quotes)
            if len(joined) >= 24:
                candidates.add(joined)

    # 5. Multiline variable assignments
    for match in B64_ASSIGN_RE.finditer(source_text):
        inner = match.group(1)
        quotes = re.findall(r"""['"]([^'"]+)['"]""", inner)
        if quotes:
            joined = "".join(re.sub(r"\s+", "", q) for q in quotes)
            if len(joined) >= 24:
                candidates.add(joined)
        else:
            clean_inner = re.sub(r"[^A-Za-z0-9+/=_\-]", "", inner)
            if len(clean_inner) >= 24:
                candidates.add(clean_inner)

    # 6. PEM / MIME blocks (consecutive lines of 32-80 base64 chars)
    for match in MIME_BLOCK_RE.finditer(source_text):
        cleaned = re.sub(r"\s+", "", match.group(0))
        if len(cleaned) >= 48:
            candidates.add(cleaned)

    # 7. Single-line literals
    for match in B64_LITERAL_RE.finditer(source_text):
        cleaned = re.sub(r"\s+", "", match.group(1))
        if len(cleaned) >= 24:
            candidates.add(cleaned)

    # 8. JSON structures
    trimmed = source_text.strip()
    if trimmed.startswith(("{", "[")):
        try:
            jdata = json.loads(trimmed)
            def _extract_from_json(obj):
                if isinstance(obj, str):
                    c = re.sub(r"\s+", "", obj)
                    if len(c) >= 24:
                        candidates.add(c)
                elif isinstance(obj, dict):
                    for v in obj.values():
                        _extract_from_json(v)
                elif isinstance(obj, list):
                    str_elements = [re.sub(r"\s+", "", x) for x in obj if isinstance(x, str)]
                    if len(str_elements) >= 2 and sum(len(x) for x in str_elements) >= 24:
                        candidates.add("".join(str_elements))
                    for v in obj:
                        _extract_from_json(v)
            _extract_from_json(jdata)
        except Exception:
            pass


def _try_decode_candidate(cand: str) -> tuple[bytes | None, str]:
    if not cand or len(cand) < 8:
        return None, ""

    # 1. Base64 / URL-safe Base64
    clean_b64 = re.sub(r"[^A-Za-z0-9+/=_\-]", "", cand)
    if len(clean_b64) >= 8:
        pad_needed = (4 - (len(clean_b64) % 4)) % 4
        padded = clean_b64 + ("=" * pad_needed)
        # Standard Base64
        try:
            decoded = base64.b64decode(padded)
            if decoded and len(decoded) >= 4:
                return decoded, "base64"
        except Exception:
            pass
        # URL-safe Base64
        try:
            decoded = base64.urlsafe_b64decode(padded)
            if decoded and len(decoded) >= 4:
                return decoded, "urlsafe_b64"
        except Exception:
            pass

    # 2. Base85 / Ascii85
    clean_85 = re.sub(r"\s+", "", cand)
    if len(clean_85) >= 10:
        try:
            decoded = base64.b85decode(clean_85)
            if decoded and len(decoded) >= 4:
                return decoded, "base85"
        except Exception:
            pass
        try:
            decoded = base64.a85decode(clean_85)
            if decoded and len(decoded) >= 4:
                return decoded, "ascii85"
        except Exception:
            pass

    # 3. Base32
    clean_32 = re.sub(r"[^A-Z2-7=]", "", cand.upper())
    if len(clean_32) >= 16:
        pad32 = (8 - (len(clean_32) % 8)) % 8
        padded32 = clean_32 + ("=" * pad32)
        try:
            decoded = base64.b32decode(padded32)
            if decoded and len(decoded) >= 4:
                return decoded, "base32"
        except Exception:
            pass

    # 4. Hex
    clean_hex = re.sub(r"[^A-Fa-f0-9]", "", cand)
    if len(clean_hex) >= 24 and len(clean_hex) % 2 == 0:
        try:
            decoded = binascii.unhexlify(clean_hex)
            if decoded and len(decoded) >= 8:
                return decoded, "hex"
        except Exception:
            pass

    return None, ""


def extract_base64_payloads(source_text: str, source_name: str = "source.py") -> list:
    if not source_text or len(source_text) < 16:
        return []

    candidates = set()

    # Layer 1: Python AST-based extraction (resolves additions, lists, joins, assignments, constants)
    _extract_from_ast(source_text, candidates)

    # Layer 2: Text / Regex-based extraction (multiline blocks, b64decode calls, triple-quotes, adjacent chunks, PEM, JSON)
    _extract_from_text(source_text, candidates)

    if not candidates:
        return []

    base_label = os.path.basename(source_name)
    extracted_items = []
    seen_hashes = set()
    idx = 1

    # Sort candidates by length descending so larger payloads are decompiled first
    for cand in sorted(list(candidates), key=lambda x: -len(x)):
        try:
            decoded, enc_name = _try_decode_candidate(cand)
            if not decoded or len(decoded) < 8:
                continue

            decomp, comp_algo = _try_decompress(decoded)
            payload_data = decomp if (decomp != decoded and len(decomp) > 0) else decoded
            is_compressed = (decomp != decoded and len(decomp) > 0)
            comp_tag = f" ({comp_algo})" if is_compressed else ""
            enc_tag = f" [{enc_name.upper()}]" if enc_name not in ("base64", "") else ""

            # Deduplicate payloads by SHA-256 hash
            payload_hash = hashlib.sha256(payload_data).hexdigest()
            if payload_hash in seen_hashes:
                continue
            seen_hashes.add(payload_hash)

            # 1. DEX Android executable
            if payload_data.startswith(b"dex\n"):
                dex_code = inspect_dex(payload_data, f"{base_label}_b64_{idx}.dex")
                item_name = f"{base_label} [DEX #{idx}]{enc_tag}{comp_tag}.java"
                extracted_items.append({
                    "name": item_name,
                    "path": item_name,
                    "size": len(payload_data),
                    "size_str": _format_size(len(payload_data)),
                    "kind": "java",
                    "raw_bytes": payload_data,
                    "icon": "📱",
                    "crc": "0x00000000",
                    "type": "text",
                    "content": f"// Decompiled Base64 DEX payload extracted from {base_label}\n// Size: {_format_size(len(payload_data))}\n\n{dex_code}",
                    "lines": len(dex_code.splitlines()) + 3,
                })
                idx += 1
                continue

            # 2. JVM Class bytecode
            if payload_data.startswith(b"\xca\xfe\xba\xbe"):
                class_code = inspect_jvm_class(payload_data, f"{base_label}_b64_{idx}.class")
                item_name = f"{base_label} [Class #{idx}]{enc_tag}{comp_tag}.java"
                extracted_items.append({
                    "name": item_name,
                    "path": item_name,
                    "size": len(payload_data),
                    "size_str": _format_size(len(payload_data)),
                    "kind": "java",
                    "raw_bytes": payload_data,
                    "icon": "📱",
                    "crc": "0x00000000",
                    "type": "text",
                    "content": f"// Decompiled Base64 JVM Class payload extracted from {base_label}\n\n{class_code}",
                    "lines": len(class_code.splitlines()) + 3,
                })
                idx += 1
                continue

            # 3. ELF Native shared library (.so)
            if payload_data.startswith(b"\x7fELF"):
                elf_code = inspect_elf(payload_data, f"{base_label}_b64_{idx}.so")
                item_name = f"{base_label} [ELF #{idx}]{enc_tag}{comp_tag}.c"
                extracted_items.append({
                    "name": item_name,
                    "path": item_name,
                    "size": len(payload_data),
                    "size_str": _format_size(len(payload_data)),
                    "kind": "c",
                    "raw_bytes": payload_data,
                    "icon": "⚙️",
                    "crc": "0x00000000",
                    "type": "text",
                    "content": f"// Base64 ELF Native Library payload extracted from {base_label}\n\n{elf_code}",
                    "lines": len(elf_code.splitlines()) + 3,
                })
                idx += 1
                continue

            # 4. ZIP / EAF Archive
            if payload_data.startswith(b"PK\x03\x04"):
                try:
                    with zipfile.ZipFile(io.BytesIO(payload_data)) as zf:
                        for zinfo in zf.infolist():
                            if zinfo.is_dir() or zinfo.file_size == 0:
                                continue
                            zbytes = zf.read(zinfo.filename)
                            zname = zinfo.filename.replace("\\", "/").strip("/")
                            zitem_name = f"{base_label} [Zip #{idx}]/{zname}"
                            ztext = None
                            if zname.endswith(".pyc") or _is_probable_pyc(zbytes):
                                ztext = decompile_pyc(zbytes, zname)
                            elif zbytes.startswith(b"dex\n"):
                                ztext = inspect_dex(zbytes, zname)
                            elif zbytes.startswith(b"\xca\xfe\xba\xbe"):
                                ztext = inspect_jvm_class(zbytes, zname)
                            elif zbytes.startswith(b"\x7fELF"):
                                ztext = inspect_elf(zbytes, zname)
                            else:
                                try:
                                    ztext = zbytes.decode("utf-8")
                                except Exception:
                                    ztext = f"# Binary file ({len(zbytes)} bytes)"
                            zicon = "⚡" if zname.endswith(".pyc") else "📱" if zname.endswith((".java", ".dex", ".class")) else "⚙️" if zname.endswith((".so", ".c", ".cpp", ".json")) else "🐍" if zname.endswith((".py", ".pyw")) else "📄"
                            extracted_items.append({
                                "name": os.path.basename(zitem_name),
                                "path": zitem_name,
                                "size": len(zbytes),
                                "size_str": _format_size(len(zbytes)),
                                "kind": "python" if zname.endswith((".py", ".pyc")) else "java" if zname.endswith((".java", ".dex", ".class")) else "c" if zname.endswith(".so") else "text",
                                "raw_bytes": zbytes,
                                "icon": zicon,
                                "crc": f"0x{zinfo.CRC & 0xFFFFFFFF:08X}",
                                "type": "text",
                                "content": ztext,
                                "lines": len(ztext.splitlines()) if ztext else 1,
                            })
                    idx += 1
                    continue
                except Exception:
                    pass

            # 5. Python Bytecode (.pyc)
            if _is_probable_pyc(payload_data):
                pyc_code = decompile_pyc(payload_data, f"{base_label}_b64_{idx}.pyc")
                if pyc_code and not pyc_code.startswith("# Error:"):
                    item_name = f"{base_label} [PyC #{idx}]{enc_tag}{comp_tag}.py"
                    extracted_items.append({
                        "name": item_name,
                        "path": item_name,
                        "size": len(payload_data),
                        "size_str": _format_size(len(payload_data)),
                        "kind": "python",
                        "raw_bytes": payload_data,
                        "icon": "⚡",
                        "crc": "0x00000000",
                        "type": "text",
                        "content": f"# Decompiled Base64 Python Bytecode extracted from {base_label}\n# Payload size: {_format_size(len(payload_data))}\n\n{pyc_code}",
                        "lines": len(pyc_code.splitlines()) + 3,
                    })
                    idx += 1
                    continue

            # 6. Decoded UTF-8 source / text / JSON
            try:
                decoded_str = payload_data.decode("utf-8")
                is_py = _is_probable_python_source(decoded_str)
                is_json = decoded_str.strip().startswith(("{", "[")) and len(decoded_str.strip()) > 6
                if len(decoded_str.strip()) > 8 and (is_py or is_compressed or is_json):
                    lang = "python" if is_py else "json" if is_json else "text"
                    ext = ".py" if lang == "python" else ".json" if lang == "json" else ".txt"
                    item_name = f"{base_label} [Decoded #{idx}]{enc_tag}{comp_tag}{ext}"
                    extracted_items.append({
                        "name": item_name,
                        "path": item_name,
                        "size": len(payload_data),
                        "size_str": _format_size(len(payload_data)),
                        "kind": lang,
                        "raw_bytes": payload_data,
                        "icon": "🐍" if lang == "python" else "⚙️" if lang == "json" else "📄",
                        "crc": "0x00000000",
                        "type": "text",
                        "content": f"# Decoded payload from {base_label}\n\n{decoded_str}",
                        "lines": len(decoded_str.splitlines()) + 2,
                    })
                    idx += 1
                    continue
            except UnicodeDecodeError:
                pass

        except Exception:
            continue

    return extracted_items
