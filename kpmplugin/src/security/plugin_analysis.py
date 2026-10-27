import ast
import base64
import binascii
import hashlib
import io
import os
import re
import struct
import time
import zipfile
import zlib

from ..methods import log, log_error


class AnalysisCancelled(BaseException):
    pass


MAX_FILE_SIZE = 32 * 1024 * 1024
MAX_ARCHIVE_SIZE = 64 * 1024 * 1024
MAX_PREVIEW_SIZE = 256 * 1024
MAX_EMBEDDED_VALUES = 128

BOT_TOKEN_RE = re.compile(r"\b\d{8,10}:[A-Za-z0-9_-]{35}\b")
IP_LITERAL_RE = re.compile(r"\b(?:\d{1,3}\.){3}\d{1,3}\b")


def _t(key, **kwargs):
    try:
        from elyx import strings
        val = strings(key, **kwargs)
        if val and val != key:
            return val
    except Exception:
        pass
    try:
        from ..methods import _tr
        val = _tr(key)
        if val and val != key:
            return val.format(**kwargs) if kwargs else val
    except Exception:
        pass
    try:
        return key.format(**kwargs) if kwargs else key
    except Exception:
        return key


def _w(word):
    core = re.escape(word)
    if len(core) < 3:
        return core
    return core[0] + "[" + core[1] + "]" + core[2:]


def _has(pattern, text):
    return re.search(pattern, text) is not None


_TG_SEND_NAMES = (
    "send_" "message", "send_" "document", "send_" "photo", "send_" "media",
    "forward_" "message", "forward_" "messages", "send_" "file",
)

_TG_LOOP_SEND = frozenset(_TG_SEND_NAMES)

_FETCH_BASES = ("requests", "httpx", "urllib.request", "openerdirector", "session", "sess", "http", "api", "aiohttp")

_PURE_WRAPPERS = frozenset({
    "bytearray", "bytes", "str", "list", "tuple", "set", "frozenset",
    "enumerate", "reversed", "sorted", "zip", "map", "filter", "repr",
})

_DECODE_FUNCS = frozenset({
    "b64decode", "decodebytes", "a85decode", "b32decode", "decodestring",
    "b85decode", "unhexlify", "fromhex", "decompress", "loads", "load",
})

_EXEC_DANGER = frozenset({"exec", "eval", "compile", "__import__", "open"})

_BORING_IMPORTS = frozenset({
    "java", "threading", "random", "time", "datetime", "re", "json",
    "sys", "os", "hashlib", "struct", "collections", "itertools",
    "functools", "math", "typing", "zlib",
})

_TDATA_RE = re.compile(r"(?<![A-Za-z0-9_])t[d]ata", re.IGNORECASE)

_SESSION_STR_RE = re.compile(
    r"\.session\b(?!\s*\()|" + _w("auth" "_key") + r"|" + _w("tgne" "t.dat")
    + r"|" + _w("cache4" ".db") + r"|" + _w("string" "session"),
    re.IGNORECASE,
)

_B64ISH_RE = re.compile(r"[A-Za-z0-9+/]{64,}={0,2}")

_WIPER_DEL_CALLS = frozenset({"deletedialog", "deletehistory", "clearhistory", "deletedialogs", "cleardialogs"})
_WIPER_OUT_CALLS = frozenset({"logout", "deleteaccount", "deactivateaccount", "resetauthorizations"})

_OWN_DIR_RE = re.compile(r"work_dir|staging|resources_root|cache|tmp|temp|plugin_dir|files_dir|download|output_dir|dest_dir|app_dir", re.IGNORECASE)

_SENSITIVE_DEL_RE = re.compile(r"acc(ount)?|session|database|\.db\b|\bdata\b|key_datas|files\b", re.IGNORECASE)

_MALNAME_RE = re.compile(r"ratk|trojan|hijack|backdoor|wiper|stealer|keylog|ransom|stager", re.IGNORECASE)
_MALNAME_SAFE_RE = re.compile(r"antivirus|antiscam|guard|secur|scanner|verifier|protect", re.IGNORECASE)

_SCAM_RE = re.compile(
    r"security alert|permanent ban"
    r"|support.{0,120}(delet\w*|\bban\b|block\w*|удален|удалён|забан|блокиров)"
    r"|поддержк.{0,120}(удален|удалён|забан|блокиров)"
    r"|send (us|me).{0,40}(code|sms)|отправ.{0,40}(смс|sms)"
    r"|sms.{0,25}code.{0,60}ban",
    re.IGNORECASE,
)

_FW_SESSION_RE = re.compile(r"(laravel|rack|django|express|rails|flask|spring|cookies?|php|asp)\.session\b", re.IGNORECASE)


def _dotted(node):
    if isinstance(node, ast.Name):
        return node.id
    if isinstance(node, ast.Attribute):
        base = _dotted(node.value)
        return f"{base}.{node.attr}" if base else None
    return None


def _const_str(node):
    try:
        if isinstance(node, ast.Constant) and isinstance(node.value, str):
            return node.value
        if isinstance(node, ast.Call) and isinstance(node.func, ast.Name):
            if node.func.id == "chr" and len(node.args) == 1:
                arg = node.args[0]
                if isinstance(arg, ast.Constant) and isinstance(arg.value, int):
                    return chr(arg.value)
            return None
        if isinstance(node, ast.BinOp) and isinstance(node.op, ast.Add):
            left = _const_str(node.left)
            right = _const_str(node.right)
            if left is not None and right is not None:
                return left + right
    except Exception:
        pass
    return None


def _has_chr_call(node):
    try:
        for sub in ast.walk(node):
            if (isinstance(sub, ast.Call) and isinstance(sub.func, ast.Name)
                    and sub.func.id == "chr"):
                return True
    except Exception:
        pass
    return False


def _collect_danger_aliases(tree):
    aliases = {}
    try:
        for _ in range(3):
            changed = False
            for node in ast.walk(tree):
                if isinstance(node, ast.Assign) and len(node.targets) == 1:
                    target = node.targets[0]
                    if isinstance(target, ast.Name):
                        kind = _danger_kind(node.value, aliases)
                        if kind and aliases.get(target.id) != kind:
                            aliases[target.id] = kind
                            changed = True
                elif (isinstance(node, ast.AnnAssign) and isinstance(node.target, ast.Name)
                        and node.value is not None):
                    kind = _danger_kind(node.value, aliases)
                    if kind and aliases.get(node.target.id) != kind:
                        aliases[node.target.id] = kind
                        changed = True
                elif isinstance(node, ast.ImportFrom):
                    mod = (node.module or "").lower()
                    if mod == "builtins":
                        for alias in node.names:
                            if (alias.name or "").lower() in _EXEC_DANGER and alias.asname:
                                if aliases.get(alias.asname) != alias.name.lower():
                                    aliases[alias.asname] = alias.name.lower()
                                    changed = True
                    else:
                        for alias in node.names:
                            if alias.asname and (alias.name or "").lower() in _EXEC_DANGER | _DECODE_FUNCS:
                                if aliases.get(alias.asname) != alias.name.lower():
                                    aliases[alias.asname] = alias.name.lower()
                                    changed = True
            if not changed:
                break
    except Exception:
        pass
    return aliases


def _danger_kind(node, aliases):
    try:
        if isinstance(node, ast.Name):
            if node.id in ("getattr", "globals", "vars", "dir"):
                return node.id
            if node.id in _EXEC_DANGER:
                return node.id
            return aliases.get(node.id)
        if isinstance(node, ast.Attribute):
            base = node.value
            if (isinstance(base, ast.Name)
                    and base.id in ("builtins", "__builtin__", "__builtins__")
                    and node.attr in _EXEC_DANGER):
                return node.attr
            return None
        if isinstance(node, ast.Call) and isinstance(node.func, ast.Name):
            fname = node.func.id
            if fname not in ("getattr", "__import__", "import_module") and aliases.get(fname) not in ("getattr", "__import__", "import_module"):
                return None
            if len(node.args) < 1:
                return None
            if fname == "getattr" or aliases.get(fname) == "getattr":
                if len(node.args) < 2:
                    return None
                name = _const_str(node.args[1])
                if name is None or name.lower() not in _EXEC_DANGER | _DECODE_FUNCS | {"getattr", "globals", "vars"}:
                    return None
                return name.lower()
            arg0 = _const_str(node.args[0])
            if arg0 is None:
                return "module"
            if arg0.lower() in ("builtins", "__builtin__", "__builtins__"):
                return "builtinsmod"
            return "module:" + arg0.lower()
    except Exception:
        pass
    return None


def _session_hint(value):
    if not isinstance(value, str):
        return False
    if _TDATA_RE.search(value):
        return True
    s = value.lower()
    return ("string" "session" in s or "auth_" "key" in s
            or "tgnet" ".dat" in s or "cache4" ".db" in s or "." "session" in s or "pyro" "gram" in s
            or "teleth" "on" in s or "account" "instance" in s)


def _has_ip_literal(node):
    try:
        if isinstance(node, ast.Constant) and isinstance(node.value, str):
            return bool(IP_LITERAL_RE.search(node.value))
        if isinstance(node, (ast.Tuple, ast.List)):
            return any(_has_ip_literal(e) for e in node.elts)
    except Exception:
        pass
    return False


def _chain_call_taint(node, taint, aliases=None, danger=None):
    func = node.func
    arg_taint = set()
    try:
        for arg in node.args:
            arg_taint |= _chain_taint(arg, taint, aliases, danger)
        for kw in node.keywords or []:
            arg_taint |= _chain_taint(kw.value, taint, aliases, danger)
    except Exception:
        pass
    if isinstance(func, ast.Attribute):
        attr = (func.attr or "").lower()
        base = _call_name(func.value).lower()
        try:
            recv_taint = _chain_taint(func.value, taint, aliases, danger)
        except Exception:
            recv_taint = set()
        rbase = aliases.get(base, base) if aliases else base
        if attr == "socket" and rbase == "socket":
            return {"socket"}
        if attr in ("urlopen", "urlretrieve"):
            return arg_taint | {"net"}
        if attr in ("get", "post", "request") and rbase in _FETCH_BASES:
            return arg_taint | {"net"}
        if attr in ("recv", "recv_into", "recvfrom"):
            return recv_taint | {"net"}
        if attr == "accept":
            return {"socket"}
        if attr in _DECODE_FUNCS:
            return recv_taint | arg_taint | {"decoded"}
        if attr in ("read", "readline", "readlines", "json", "decode", "decrypt",
                    "format", "join", "replace",
                    "strip", "upper", "lower", "split", "encode", "format_map", "text"):
            return recv_taint | arg_taint
        return set()
    if isinstance(func, ast.Name):
        fid = func.id
        resolved = (danger.get(fid) if danger else None) or fid
        if fid in ("socket", "socketpair", "create_connection", "accept"):
            return {"socket"}
        if resolved in ("compile", "eval", "exec"):
            return arg_taint
        if resolved in _DECODE_FUNCS:
            return arg_taint | {"decoded"}
        if resolved in _PURE_WRAPPERS:
            return arg_taint
        if fid == "open" or resolved == "open":
            try:
                if node.args and isinstance(node.args[0], ast.Constant) and _session_hint(node.args[0].value):
                    return {"session"}
            except Exception:
                pass
            return arg_taint
        return set()
    return set()


def _chain_taint(node, taint, aliases=None, danger=None):
    try:
        if isinstance(node, ast.Name):
            return set(taint.get(node.id, ()))
        if isinstance(node, ast.Constant):
            if _session_hint(node.value):
                return {"session"}
            return set()
        if isinstance(node, (ast.Attribute, ast.Subscript)):
            if isinstance(node, ast.Attribute):
                try:
                    key = _dotted(node)
                    if key and key in taint:
                        return set(taint.get(key, ()))
                except Exception:
                    pass
            return _chain_taint(node.value, taint, aliases, danger)
        if isinstance(node, ast.BinOp):
            return _chain_taint(node.left, taint, aliases, danger) | _chain_taint(node.right, taint, aliases, danger)
        if isinstance(node, ast.JoinedStr):
            out = set()
            for v in node.values:
                out |= _chain_taint(v, taint, aliases, danger)
            return out
        if isinstance(node, ast.FormattedValue):
            return _chain_taint(node.value, taint, aliases, danger)
        if isinstance(node, (ast.List, ast.Tuple, ast.Set)):
            out = set()
            for e in node.elts:
                out |= _chain_taint(e, taint, aliases, danger)
            return out
        if isinstance(node, ast.Dict):
            out = set()
            for v in node.values:
                if v is not None:
                    out |= _chain_taint(v, taint, aliases, danger)
            return out
        if isinstance(node, (ast.ListComp, ast.SetComp, ast.GeneratorExp)):
            out = _chain_taint(node.elt, taint, aliases, danger)
            for gen in node.generators:
                out |= _chain_taint(gen.iter, taint, aliases, danger)
            return out
        if isinstance(node, ast.DictComp):
            out = _chain_taint(node.key, taint, aliases, danger) | _chain_taint(node.value, taint, aliases, danger)
            for gen in node.generators:
                out |= _chain_taint(gen.iter, taint, aliases, danger)
            return out
        if isinstance(node, ast.NamedExpr):
            return _chain_taint(node.value, taint, aliases, danger)
        if isinstance(node, ast.Starred):
            return _chain_taint(node.value, taint, aliases, danger)
        if isinstance(node, ast.IfExp):
            return _chain_taint(node.body, taint, aliases, danger) | _chain_taint(node.orelse, taint, aliases, danger)
        if isinstance(node, ast.Await):
            return _chain_taint(node.value, taint, aliases, danger)
        if isinstance(node, ast.Call):
            return _chain_call_taint(node, taint, aliases, danger)
    except Exception:
        pass
    return set()


def _bind_chain_target(target, tags, taint):
    try:
        if isinstance(target, ast.Name):
            cur = taint.get(target.id, set())
            if not tags <= cur:
                taint[target.id] = cur | tags
                return True
        elif isinstance(target, ast.Starred):
            return _bind_chain_target(target.value, tags, taint)
        elif isinstance(target, (ast.Tuple, ast.List)):
            changed = False
            for e in target.elts:
                changed |= _bind_chain_target(e, tags, taint)
            return changed
        elif isinstance(target, ast.Attribute):
            key = _dotted(target)
            if key:
                cur = taint.get(key, set())
                if not tags <= cur:
                    taint[key] = cur | tags
                    return True
    except Exception:
        pass
    return False


def _chain_flows(tree, yield_fn=None):
    chains = {"rce": False, "c2": False, "exfil": False, "hard_ip": False, "stagexec": False}
    taint = {}
    aliases = {}
    try:
        danger = _collect_danger_aliases(tree)
    except Exception:
        danger = {}
    try:
        for node in ast.walk(tree):
            if isinstance(node, ast.Import):
                for alias in node.names:
                    if alias.asname:
                        aliases[alias.asname.lower()] = (alias.name or "").lower()
            elif isinstance(node, ast.ImportFrom):
                mod = (node.module or "").lower()
                for alias in node.names:
                    if alias.asname:
                        aliases[alias.asname.lower()] = f"{mod}.{alias.name}".lower() if mod else (alias.name or "").lower()
    except AnalysisCancelled:
        raise
    except Exception:
        pass
    try:
        for _ in range(4):
            if yield_fn:
                yield_fn()
            changed = False
            for node in ast.walk(tree):
                if isinstance(node, ast.Assign):
                    try:
                        tags = _chain_taint(node.value, taint, aliases, danger)
                    except Exception:
                        tags = set()
                    for target in node.targets:
                        changed |= _bind_chain_target(target, tags, taint)
                elif isinstance(node, ast.AnnAssign) and node.value is not None:
                    try:
                        tags = _chain_taint(node.value, taint, aliases, danger)
                    except Exception:
                        tags = set()
                    changed |= _bind_chain_target(node.target, tags, taint)
                elif isinstance(node, ast.AugAssign):
                    try:
                        tags = _chain_taint(node.value, taint, aliases, danger)
                    except Exception:
                        tags = set()
                    changed |= _bind_chain_target(node.target, tags, taint)
                elif isinstance(node, ast.For):
                    try:
                        tags = _chain_taint(node.iter, taint, aliases, danger)
                    except Exception:
                        tags = set()
                    changed |= _bind_chain_target(node.target, tags, taint)
                elif isinstance(node, ast.With):
                    for item in node.items:
                        if item.optional_vars is not None:
                            try:
                                tags = _chain_taint(item.context_expr, taint, aliases, danger)
                            except Exception:
                                tags = set()
                            changed |= _bind_chain_target(item.optional_vars, tags, taint)
            if not changed:
                break
    except AnalysisCancelled:
        raise
    except Exception:
        pass
    try:
        if yield_fn:
            yield_fn()
        parts = {}
        for node in ast.walk(tree):
            if not isinstance(node, ast.Call):
                continue
            func = node.func
            if isinstance(func, ast.Name):
                resolved = (danger.get(func.id) if danger else None) or func.id
                if resolved in ("exec", "eval", "compile") and node.args:
                    try:
                        arg_tags = _chain_taint(node.args[0], taint, aliases, danger)
                        if "net" in arg_tags:
                            chains["rce"] = True
                        if "decoded" in arg_tags:
                            chains["stagexec"] = True
                    except Exception:
                        pass
                continue
            if not isinstance(func, ast.Attribute):
                continue
            attr = (func.attr or "").lower()
            root = _dotted(func.value)
            try:
                root_tags = _chain_taint(func.value, taint, aliases, danger)
            except Exception:
                root_tags = set()
            if "socket" in root_tags:
                bucket = parts.setdefault(root or "#sock", set())
                if attr == "connect":
                    bucket.add("connect")
                    for arg in node.args:
                        if _has_ip_literal(arg):
                            chains["hard_ip"] = True
                            break
                elif attr in ("send", "sendall", "sendto"):
                    bucket.add("send")
                    for arg in list(node.args) + [kw.value for kw in node.keywords or []]:
                        try:
                            if "session" in _chain_taint(arg, taint, aliases, danger):
                                chains["exfil"] = True
                                break
                        except Exception:
                            pass
                elif attr in ("recv", "recv_into", "recvfrom"):
                    bucket.add("recv")
            if attr in _TG_SEND_NAMES:
                for arg in list(node.args) + [kw.value for kw in node.keywords or []]:
                    try:
                        if "session" in _chain_taint(arg, taint, aliases, danger):
                            chains["exfil"] = True
                            break
                    except Exception:
                        pass
            base = _call_name(func.value).lower()
            if attr in ("post", "put", "patch", "request") and base in _FETCH_BASES:
                for kw in node.keywords or []:
                    if (kw.arg or "").lower() in ("data", "json", "content", "files", "params"):
                        try:
                            if "session" in _chain_taint(kw.value, taint, aliases, danger):
                                chains["exfil"] = True
                                break
                        except Exception:
                            pass
            if attr in ("urlopen", "urlretrieve"):
                for arg in list(node.args) + [kw.value for kw in node.keywords or []]:
                    try:
                        if "session" in _chain_taint(arg, taint, aliases, danger):
                            chains["exfil"] = True
                            break
                    except Exception:
                        pass
        for have in parts.values():
            if {"connect", "send", "recv"} <= have:
                chains["c2"] = True
                break
    except AnalysisCancelled:
        raise
    except Exception:
        pass
    return chains


_BENIGN_DEL_RE = re.compile(
    r"tmp|temp|cache|stag(e|ing)|thumb|preview|draft|\block\b|trash|journal|\.tmp$|\.temp$|download|cookie",
    re.IGNORECASE,
)

_DANGER_PATH_RE = re.compile(
    r"tdata|\.session|tgnet|cache4|\.db\b|/data/|userconfig|passcode|authkey|/sdcard(\s|$)|^\s*/|\*|~|/system|/proc|dcim|camera",
    re.IGNORECASE,
)

_TRUSTED_FIELD_RE = re.compile(r"getdeclaredfield\s*\(\s*['\"]trusted['\"]", re.IGNORECASE)

_WIPER_DEL_RE = re.compile(r"deletedialog|deletehistory|clearhistory|delete_dialogs|cleardialogs", re.IGNORECASE)
_WIPER_OUT_RE = re.compile(r"log_?out|delete_?account|deactivate_?account", re.IGNORECASE)


def _unparse_low(node):
    try:
        return ast.unparse(node).lower()
    except Exception:
        return ""


def _session_refs(tree, low):
    try:
        if tree is not None:
            hit_ident = False
            for node in ast.walk(tree):
                if isinstance(node, ast.Constant) and isinstance(node.value, str):
                    value = _FW_SESSION_RE.sub("", node.value)
                    if _TDATA_RE.search(value) or _SESSION_STR_RE.search(value):
                        return True
            for node in ast.walk(tree):
                name = None
                if isinstance(node, ast.Name):
                    name = node.id
                elif isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef, ast.ClassDef)):
                    name = node.name
                elif isinstance(node, ast.Attribute):
                    name = node.attr
                elif isinstance(node, ast.arg):
                    name = node.arg
                if name and (_TDATA_RE.search(name)
                             or (name.lower() in ("auth_key", "stringsession")
                                 and "api_key" not in low and "apikey" not in low)):
                    hit_ident = True
                    break
            if hit_ident:
                return True
            return False
    except Exception:
        pass
    return bool(_TDATA_RE.search(low) or _SESSION_STR_RE.search(low)
                or re.search(_w("teleth" "on") + r"|" + _w("pyro" "gram"), low))


def _delete_calls(tree, low):
    dangerous = False
    filedel = False
    try:
        if tree is not None:
            for node in ast.walk(tree):
                if not isinstance(node, ast.Call):
                    continue
                func = node.func
                is_rmtree = False
                if isinstance(func, ast.Attribute):
                    if func.attr.lower() not in ("remove", "unlink", "rmtree", "removedirs"):
                        continue
                    is_rmtree = func.attr.lower() == "rmtree"
                elif isinstance(func, ast.Name):
                    if func.id.lower() not in ("remove", "unlink"):
                        continue
                else:
                    continue
                if isinstance(func, ast.Attribute):
                    recv = _call_name(func.value).lower()
                    if recv.split(".")[-1] not in ("os", "shutil", "path", "file", "files", "dir", "folder"):
                        if func.attr.lower() == "remove":
                            continue
                arg = node.args[0] if node.args else None
                argtext = _unparse_low(arg) if arg is not None else ""
                if isinstance(arg, ast.Constant) and isinstance(arg.value, str):
                    if _DANGER_PATH_RE.search(arg.value):
                        dangerous = True
                    elif is_rmtree and _SENSITIVE_DEL_RE.search(arg.value):
                        dangerous = True
                    else:
                        filedel = True
                elif isinstance(arg, ast.Name):
                    if _BENIGN_DEL_RE.search(arg.id):
                        continue
                    if is_rmtree and _SENSITIVE_DEL_RE.search(arg.id):
                        dangerous = True
                    else:
                        filedel = True
                else:
                    if _BENIGN_DEL_RE.search(argtext) and not _DANGER_PATH_RE.search(argtext):
                        continue
                    if _DANGER_PATH_RE.search(argtext):
                        dangerous = True
                    elif _OWN_DIR_RE.search(argtext):
                        filedel = True
                    elif is_rmtree and _SENSITIVE_DEL_RE.search(argtext):
                        dangerous = True
                    else:
                        filedel = True
            for node in ast.walk(tree):
                if not isinstance(node, ast.Call):
                    continue
                nm = _call_name(node.func).lower().split(".")[-1]
                if nm in ("system", "popen") and node.args:
                    argtext = _unparse_low(node.args[0])
                    if re.search(r"rm\s+-rf|format\s+[a-z]:|mkfs|dd\s+if=", argtext):
                        dangerous = True
            if dangerous or filedel:
                return dangerous, filedel
            return False, False
    except Exception:
        pass
    if _has(r"shutil\.rmtree|rm\s+-rf|format\s+[a-z]:", low):
        return True, filedel
    if _has(r"os\.remove|os\.unlink", low):
        return dangerous, True
    return dangerous, filedel


def _authcode_ctx(tree, low):
    try:
        if re.search(r"777000|service_message|otp_bot|code_bot|verification_bot", low):
            return True
        if tree is None:
            return False
        for node in ast.walk(tree):
            name = None
            if isinstance(node, ast.Name):
                name = node.id
            elif isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef, ast.ClassDef)):
                name = node.name
            elif isinstance(node, ast.Attribute):
                name = node.attr
            elif isinstance(node, ast.arg):
                name = node.arg
            if name and re.search(r"auth_?code|login_?code|verification_?code|\botp\b|\b2fa\b", name, re.IGNORECASE):
                return True
    except Exception:
        pass
    return False


def _acckill_call(tree):
    try:
        if tree is None:
            return True
        for node in ast.walk(tree):
            if not isinstance(node, ast.Call):
                continue
            nm = _call_name(node.func).lower()
            if re.search(r"log_?out|delete_?account|deactivate_?account|reset_?authorizations", nm):
                return True
    except Exception:
        pass
    return False


def _wiper_calls(tree, low):
    try:
        if tree is not None:
            found_del = False
            found_out = False
            for node in ast.walk(tree):
                if not isinstance(node, ast.Call):
                    continue
                nm = _call_name(node.func).lower().split(".")[-1]
                if nm in _WIPER_DEL_CALLS:
                    found_del = True
                elif nm in _WIPER_OUT_CALLS:
                    found_out = True
                if found_del and found_out:
                    return True
            return False
    except Exception:
        pass
    return bool(_WIPER_DEL_RE.search(low)) and bool(_WIPER_OUT_RE.search(low))


def _kill_proc(tree, low):
    try:
        if tree is not None:
            for node in ast.walk(tree):
                if not isinstance(node, ast.Call):
                    continue
                func = node.func
                name = ""
                if isinstance(func, ast.Attribute):
                    name = (func.attr or "").lower()
                elif isinstance(func, ast.Name):
                    name = func.id.lower()
                if name not in ("killprocess", "kill_process"):
                    continue
                if node.args and ("mypid" in _unparse_low(node.args[0])
                                  or "getpid" in _unparse_low(node.args[0])):
                    continue
                return True
            return False
    except Exception:
        pass
    return bool(re.search(r"kill_?process", low)) and "mypid" not in low and "getpid" not in low


def _loop_send(tree):
    try:
        if tree is None:
            return False
        for node in ast.walk(tree):
            if isinstance(node, (ast.For, ast.While)):
                for sub in ast.walk(node):
                    if sub is node or not isinstance(sub, ast.Call):
                        continue
                    if _call_name(sub.func).lower().split(".")[-1] in _TG_LOOP_SEND:
                        return True
    except Exception:
        pass
    return False


def _obfuscated(tree, danger):
    try:
        if tree is None:
            return False
        for node in ast.walk(tree):
            if isinstance(node, ast.Call) and isinstance(node.func, ast.Name):
                fid = node.func.id
                resolved = (danger.get(fid) if danger else None) or fid
                if resolved in ("getattr", "__import__") or fid in ("getattr", "__import__"):
                    for arg in node.args:
                        if _has_chr_call(arg):
                            return True
    except Exception:
        pass
    return False


def _b64_blob_size(tree):
    total = 0
    try:
        if tree is None:
            return 0
        for node in ast.walk(tree):
            if isinstance(node, ast.Constant) and isinstance(node.value, str):
                value = node.value.strip()
                if len(value) >= 64 and _B64ISH_RE.search(value):
                    total += len(value)
    except Exception:
        pass
    return total


def _file_features(source, yield_fn=None):
    low = source.lower()
    feats = {
        "exec": False, "eval": False, "compile": False, "dynimport": False,
        "imp_socket": False, "imp_subproc": False, "imp_marshal": False,
        "imp_thread": False, "imp_hash": False, "imp_b64": False,
        "imp_urllib": False, "imp_requests": False,
        "m_connect": False, "m_send": False, "m_recv": False,
        "m_fetch": False,
        "chain_rce": False, "chain_c2": False, "chain_exfil": False, "chain_hardip": False,
        "chain_stagexec": False, "stagexec_text": False,
        "tg_intercept": False, "destructive": False, "filedel": False,
        "obfus": False, "trustforge": False, "iplog": False, "wiper": False,
        "spamloop": False, "scamtext": False, "authcode_ctx": False,
    }
    try:
        tree = ast.parse(source)
    except Exception:
        tree = None
    try:
        danger = _collect_danger_aliases(tree) if tree is not None else {}
    except Exception:
        danger = {}
    if tree is not None:
        for node in ast.walk(tree):
            if isinstance(node, ast.Import):
                for alias in node.names:
                    top = (alias.name or "").split(".")[0].lower()
                    if top == "socket":
                        feats["imp_socket"] = True
                    elif top == "subprocess":
                        feats["imp_subproc"] = True
                    elif top == "marshal":
                        feats["imp_marshal"] = True
                    elif top == "threading":
                        feats["imp_thread"] = True
                    elif top == "hashlib":
                        feats["imp_hash"] = True
                    elif top == "base64":
                        feats["imp_b64"] = True
                    elif top == "urllib":
                        feats["imp_urllib"] = True
                    elif top in ("requests", "httpx", "aiohttp"):
                        feats["imp_requests"] = True
            elif isinstance(node, ast.ImportFrom):
                mod = (node.module or "").split(".")[0].lower()
                if mod == "socket":
                    feats["imp_socket"] = True
                elif mod == "subprocess":
                    feats["imp_subproc"] = True
                elif mod == "threading":
                    feats["imp_thread"] = True
                elif mod == "marshal":
                    feats["imp_marshal"] = True
                elif mod == "hashlib":
                    feats["imp_hash"] = True
                elif mod == "base64":
                    feats["imp_b64"] = True
                elif mod == "urllib":
                    feats["imp_urllib"] = True
                elif mod in ("requests", "httpx", "aiohttp"):
                    feats["imp_requests"] = True
                else:
                    for alias in node.names:
                        if (alias.name or "").lower() in ("b64decode", "decodebytes", "a85decode", "b32decode", "decodestring", "b85decode", "decompress", "loads", "dumps"):
                            feats["imp_b64"] = True
                            break
            elif isinstance(node, ast.Call):
                func = node.func
                if isinstance(func, ast.Name):
                    resolved = danger.get(func.id, func.id)
                    if resolved == "exec":
                        feats["exec"] = True
                    elif resolved == "eval":
                        feats["eval"] = True
                    elif resolved == "compile":
                        feats["compile"] = True
                    elif resolved == "__import__":
                        try:
                            arg0 = node.args[0] if node.args else None
                            modname = _const_str(arg0) if arg0 is not None else None
                            if (modname is None
                                    or (modname.lower() not in _BORING_IMPORTS
                                        and "." not in modname)):
                                feats["dynimport"] = True
                        except Exception:
                            pass
                elif isinstance(func, ast.Attribute):
                    attr = (func.attr or "").lower()
                    if attr in ("exec", "eval"):
                        feats["subproc"] = True
                    elif attr == "connect":
                        feats["m_connect"] = True
                    elif attr in ("send", "sendall", "sendto"):
                        feats["m_send"] = True
                    elif attr in ("recv", "recv_into"):
                        feats["m_recv"] = True
                    elif attr in ("sha256", "sha1", "sha512", "sha224", "sha384", "md5"):
                        feats["imp_hash"] = True
                    elif attr in ("b64decode", "decodebytes", "a85decode", "b32decode"):
                        feats["imp_b64"] = True
                    elif attr in ("urlopen", "urlretrieve") or (attr in ("get", "post", "request") and _call_name(func.value).lower() in ("requests", "httpx", "urllib.request", "openerdirector")):
                        feats["m_fetch"] = True
                elif isinstance(func, ast.Subscript):
                    try:
                        key = ast.literal_eval(func.slice)
                    except Exception:
                        key = None
                    if key in ("exec", "eval"):
                        feats["exec" if key == "exec" else "eval"] = True

    # Telegram message intercept checks
    if _has(r"sendmessageshelper|messagescontroller|chatactivity", low) and _has(r"sendmessage|forwardmessage|sendphoto|senddocument", low) and "@hook" in source:
        feats["tg_intercept"] = True

    feats["sessionf"] = _session_refs(tree, low)
    feats["destructive"], feats["filedel"] = _delete_calls(tree, low)
    feats["spamloop"] = bool(feats["imp_thread"]) and _loop_send(tree)
    feats["obfus"] = _obfuscated(tree, danger)
    feats["trustforge"] = bool(_TRUSTED_FIELD_RE.search(source)) and "setboolean" in low
    feats["iplog"] = bool(re.search(r"whologger|iplogger|ip_logger|grabify", source, re.IGNORECASE)) and bool(feats["imp_urllib"] or feats["imp_requests"] or feats["m_fetch"])
    feats["wiper"] = _wiper_calls(tree, low)
    feats["scamtext"] = bool(_SCAM_RE.search(source))
    feats["authcode_ctx"] = _authcode_ctx(tree, low)
    if (feats["exec"] or feats["eval"] or feats["compile"]) and _b64_blob_size(tree) >= 2048:
        try:
            has_decode = False
            if tree is not None:
                for node in ast.walk(tree):
                    if isinstance(node, ast.Call):
                        nm = _call_name(node.func).lower().split(".")[-1]
                        if (nm in _DECODE_FUNCS or (isinstance(node.func, ast.Name)
                                and (danger.get(node.func.id) or "") in _DECODE_FUNCS)):
                            has_decode = True
                            break
            else:
                has_decode = _has(_w("b64de" "code") + r"|decompress|" + _w("b85de" "code"), low)
            if has_decode:
                feats["stagexec_text"] = True
        except Exception:
            pass

    text_flags = {
        "shellstr": _has(r"/b" r"in/sh|/b" r"in/bash|cmd\.exe|\b" + _w("p" "ty") + r"\b", low),
        "harvest": _has(_w("get_part" "icipants") + r"|" + _w("iter_par" "ticipants") + r"|" + _w("gethi" "story") + r"|" + _w("getfu" "lluser") + r"|" + _w("get_d" "ialogs") + r"|" + _w("channelpa" "rticipants"), low),
        "contact": _has(_w("getco" "ntacts") + r"|" + _w("phone_" "number") + r"|" + _w("tl_contacts_getcontacts"), low),
        "authcode": _has(_w("auth" "_code") + r"|" + _w("login" "_code") + r"|verification_?code|two.?factor|\b" + _w("2" "fa") + r"\b", low),
        "xorop": _has(r"\b" + _w("x" "or") + r"\b|\^ ?0x|" + _w("ro" "t13") + r"|" + _w("rot" "_13"), low),
        "devid": _has(_w("getde" "viceid") + r"|" + _w("getsubs" "criberid") + r"|" + _w("getsimse" "rial") + r"|" + _w("andro" "id_id") + r"|" + _w("telephon" "ymanager"), low),
        "datathief": _has(r"\b" + _w("st" "eal") + r"(?!th)\w*|" + _w("сти" "ллер") + r"|" + _w("уг" "он") + r"|" + _w("hij" "ack") + r"|" + _w("хай" "джек") + r"|" + _w("тро" "ян"), low),
        "ratkw": _has(r"\b" + _w("r" "at") + r"\b|" + _w("ra" "tnik") + r"|" + _w("back" "door") + r"|" + _w("бэк" "дор") + r"|" + _w("бек" "дор") + r"|" + _w("кр" "ыс") + r"|\b" + _w("ра" "тк") + r"\w*|\bratk[ao]\b", low),
        "deviceprint": _has(_w("finge" "rprint") + r"|\b" + _w("ser" "ial") + r"\b", low),
        "acckill": _has(r"delete_?account|reset_?account|deactivate_?account|account\.deleteaccount|reset_?authorizations|auth\.resetauthorizations", low) and _acckill_call(tree),
        "killproc": _kill_proc(tree, low),
        "starflag": _has(_w("star" "gift") + r"|" + _w("stars" "controller") + r"|" + _w("getba" "lance") + r"|" + _w("sendstarsform") + r"|" + _w("transferstar" "gift"), low),
        "webserver": _has(_w("https" "erver") + r"|" + _w("basehttpreq" "uesthandler") + r"|\b" + _w("fl" "ask") + r"\b|" + _w("web" "hook"), low),
        "cloudexfil": _has(r"drive\.goog" + _w("le") + r"|discord(app)?\.com/api/webho" + _w("oks") + r"|webho" + _w("ok.site") + r"|file\.io|transfer\.sh|uguu\.se|catbox\.moe|0x0\.st|tmpfiles\.org|gofile\.io|pixel" "drain", low),
        "tgsend": _has(_w("send_m" "essage") + r"|" + _w("send_d" "ocument") + r"|" + _w("send_" "photo") + r"|" + _w("send" "media") + r"|forward_?message", low),
        "bottoken": bool(BOT_TOKEN_RE.findall(source)),
        "tgapi": _has(r"api\.telegram\.org", low),
        "apkurl": _has(r"https?://\S+?\.(apk|dex|so|jar)([?#'\" ]|$)", low),
        "hard_ip": _has(r"\.connect\s*\([^)]*\d{1,3}\.\d{1,3}", source),
    }
    feats.update(text_flags)
    feats["botcount"] = len(BOT_TOKEN_RE.findall(source))
    if tree is None:
        if _has(r"\b" + _w("ex" "ec") + r"\s*\(", low):
            feats["exec"] = True
        if _has(r"\b" + _w("ev" "al") + r"\s*\(", low):
            feats["eval"] = True
        if _has(r"\b" + _w("com" "pile") + r"\s*\(", low):
            feats["compile"] = True
        if _has(_w("__imp" "ort__") + r"\s*\(", low):
            feats["dynimport"] = True
    feats["dex_loader"] = _has(_w("dexclas" "sloader") + r"|" + _w("pathcla" "ssloader") + r"|" + _w("inmemorydex" "classloader"), low)
    if tree is not None:
        try:
            chains = _chain_flows(tree, yield_fn=yield_fn)
            feats["chain_rce"] = bool(chains.get("rce"))
            feats["chain_c2"] = bool(chains.get("c2"))
            feats["chain_exfil"] = bool(chains.get("exfil"))
            feats["chain_hardip"] = bool(chains.get("hard_ip"))
            feats["chain_stagexec"] = bool(chains.get("stagexec"))
        except AnalysisCancelled:
            raise
        except Exception:
            pass
    feats["b64"] = feats["imp_b64"]
    feats["hash"] = feats["imp_hash"]
    feats["thread"] = feats["imp_thread"]
    feats["urlfetch"] = feats["imp_urllib"] or feats["imp_requests"] or feats["m_fetch"]
    feats["socket"] = feats["imp_socket"]
    feats["connect"] = feats["m_connect"]
    feats["send"] = feats["m_send"]
    feats["recv"] = feats["m_recv"]
    feats["subproc"] = feats["imp_subproc"] or bool(feats.get("subproc"))
    feats["marshal"] = feats["imp_marshal"]
    return feats


def _malware_findings(features):
    f = features
    exfil = f["tgsend"] or f["tgapi"] or f["send"] or f["urlfetch"] or f["bottoken"] or f["cloudexfil"]
    exfil_ext = f["tgapi"] or f["send"] or f["urlfetch"] or f["bottoken"] or f["cloudexfil"]
    netread = f["urlfetch"] or (f["socket"] and f["recv"])
    findings = []

    def add(weight, text):
        findings.append((weight, text))

    if f.get("chain_stagexec") or f.get("stagexec_text"):
        add(30 if f.get("chain_stagexec") else 25, _t("t_stagexec"))
    if f.get("obfus"):
        add(12, _t("t_obfus"))
    if f.get("trustforge"):
        add(25, _t("t_trustforge"))
    if f.get("iplog"):
        add(20, _t("t_iplog"))
    if f.get("wiper"):
        add(25, _t("t_wiper"))
    if f.get("scamtext"):
        add(15 if (f["tgsend"] or f["send"] or f["urlfetch"] or f["thread"]) else 8, _t("t_scamtext"))
    if f["exec"] and netread:
        add(30, _t("t_exec_net_chain") if f.get("chain_rce") else _t("t_exec_net"))
    if f["socket"] and f["connect"] and f["send"] and f["recv"]:
        add(20, _t("t_c2_chain") if f.get("chain_c2") else _t("t_c2"))
    if f["bottoken"] and exfil:
        add(25, _t("t_bot_token", n=f["botcount"]))
    if f.get("tg_intercept") and exfil_ext:
        add(25, _t("t_tg_intercept"))
    if f.get("destructive"):
        add(20, _t("t_destructive"))
    if f.get("filedel"):
        add(5, _t("t_filedel"))
    if f["deviceprint"] and f["hash"] and (f["socket"] or f["urlfetch"] or f["tgapi"]):
        add(8, _t("t_deviceprint"))
    if f["sessionf"] and exfil_ext:
        add(30, _t("t_session_chain") if f.get("chain_exfil") else _t("t_session"))
    elif f["sessionf"] and f["tgsend"]:
        add(12, _t("t_session"))
    elif f["sessionf"]:
        add(6, _t("t_session"))
    if f["harvest"] and exfil:
        add(12, _t("t_harvest"))
    if f["shellstr"] and (f["socket"] or f["subproc"]):
        add(15, _t("t_shell"))
    if f["apkurl"]:
        add(12, _t("t_apkurl"))
    if f["cloudexfil"]:
        add(8, _t("t_cloud"))
    if f["b64"] and (f["exec"] or f["compile"] or f["eval"]):
        add(15, _t("t_b64exec"))
    if f["marshal"]:
        add(12, _t("t_marshal"))
    if f.get("dynimport"):
        add(5, _t("t_dynimport"))
    if f["contact"] and exfil:
        add(12, _t("t_contact"))
    if f["authcode"] and exfil:
        add(25 if f.get("authcode_ctx") else 10, _t("t_authcode"))
    if f["xorop"] and (f["exec"] or f["b64"] or f["compile"]):
        add(12, _t("t_xor"))
    if f["datathief"]:
        add(5, _t("t_datathief"))
    if f["devid"] and (f["send"] or f["recv"] or f["urlfetch"] or f["tgapi"] or f["tgsend"] or f["connect"] or f["cloudexfil"]):
        add(8, _t("t_devid_net"))
    elif f["devid"]:
        add(3, _t("t_devid"))
    if f["subproc"]:
        add(5, _t("t_subproc"))
    if f["webserver"]:
        add(3, _t("t_webserver"))
    if f["ratkw"]:
        add(5, _t("t_ratkw"))
    if f["acckill"]:
        add(15, _t("t_acckill"))
    if f["killproc"]:
        add(4, _t("t_killproc"))
    if f["starflag"] and (f.get("wiper") or f.get("trustforge") or f.get("obfus")):
        add(20, _t("t_star"))
    elif f["starflag"] and exfil_ext:
        add(20, _t("t_star"))
    elif f["starflag"] and f["tgsend"]:
        add(12, _t("t_star"))
    if f.get("spamloop"):
        add(8, _t("t_spam"))
    if f["hard_ip"] and f["socket"]:
        add(10, _t("t_hardip_chain") if f.get("chain_hardip") else _t("t_hardip"))
    if f["dex_loader"] and f["apkurl"]:
        add(15, _t("t_dex"))
    seen = set()
    unique = []
    for weight, text in findings:
        if text not in seen:
            seen.add(text)
            unique.append((weight, text))
    return unique


def _threat_level(score):
    if score >= 40:
        return "high"
    if score >= 20:
        return "medium"
    if score >= 8:
        return "low"
    return "clean"


def _is_version_like_ip(value):
    host = value.split(":", 1)[0].split("/", 1)[0]
    if not re.fullmatch(r"\d{1,3}(?:\.\d{1,3}){3}", host):
        return False
    octets = [int(part) for part in host.split(".")]
    if any(octet > 255 for octet in octets):
        return True
    if re.search(r"(?::\d{1,5})(?:/|$)", value) or "/" in value[len(host):]:
        return False
    if all(octet <= 99 for octet in octets):
        return True
    if 1900 <= octets[0] <= 2099:
        return True
    return False


def _external_link(value):
    return re.search(r"\.(?:dex|so|jar|zip|apk)(?:[?#]|$)", value, re.IGNORECASE) is not None


def _unique(values):
    result = []
    seen = set()
    for value in values:
        normalized = str(value).strip()
        if normalized and normalized not in seen:
            seen.add(normalized)
            result.append(normalized)
    return result


def _read_uleb128_mem(data, offset):
    result = 0
    shift = 0
    d_len = len(data)
    while offset < d_len:
        b = data[offset]
        offset += 1
        result |= (b & 0x7F) << shift
        if (b & 0x80) == 0:
            break
        shift += 7
    return result, offset


def _format_dex_preview(strings, classes):
    lines = [f"// Classes ({len(classes)}):"]
    for c_name, methods in sorted(classes.items())[:20]:
        lines.append(f"class {c_name} {{")
        for m in methods[:10]:
            lines.append(f"    {m}();")
        if len(methods) > 10:
            lines.append(f"    // ... +{len(methods) - 10} more methods")
        lines.append("}")
    if len(classes) > 20:
        lines.append(f"// ... +{len(classes) - 20} more classes")
    if strings:
        lines.append("")
        lines.append(f"// Strings ({len(strings)}):")
        for s in strings[:30]:
            if s.strip():
                lines.append(f"//   {s[:120]}")
    return "\n".join(lines)


def parse_dex(data, yield_fn=None):
    """
    Ultra-fast Dalvik DEX bytecode parser operating directly on memory slices.
    Extracts string pool, class hierarchy, and referenced method signatures in < 5ms.
    """
    strings = []
    classes = {}
    if len(data) < 112 or not data.startswith(b"dex\n"):
        return strings, classes
    try:
        string_ids_size, string_ids_off = struct.unpack_from("<II", data, 56)
        type_ids_size, type_ids_off = struct.unpack_from("<II", data, 64)
        proto_ids_size, proto_ids_off = struct.unpack_from("<II", data, 72)
        field_ids_size, field_ids_off = struct.unpack_from("<II", data, 80)
        method_ids_size, method_ids_off = struct.unpack_from("<II", data, 88)
        class_defs_size, class_defs_off = struct.unpack_from("<II", data, 96)

        d_len = len(data)

        # 1. Direct memory string pool parsing
        max_strings = min(string_ids_size, 50000)
        for i in range(max_strings):
            if yield_fn and (i & 0x7FF) == 0:
                yield_fn()
            str_off_pos = string_ids_off + i * 4
            if str_off_pos + 4 > d_len:
                break
            s_off = struct.unpack_from("<I", data, str_off_pos)[0]
            if s_off < d_len:
                _, str_start = _read_uleb128_mem(data, s_off)
                null_pos = data.find(b"\x00", str_start)
                if null_pos != -1:
                    raw_str = data[str_start:null_pos]
                    strings.append(raw_str.decode("utf-8", errors="ignore"))
                else:
                    strings.append("")
            else:
                strings.append("")

        # 2. Type table parsing
        types = []
        max_types = min(type_ids_size, 30000)
        for i in range(max_types):
            if yield_fn and (i & 0x7FF) == 0:
                yield_fn()
            pos = type_ids_off + i * 4
            if pos + 4 > d_len:
                break
            desc_idx = struct.unpack_from("<I", data, pos)[0]
            types.append(strings[desc_idx] if desc_idx < len(strings) else "")

        # 3. Method table parsing
        max_methods = min(method_ids_size, 40000)
        for i in range(max_methods):
            if yield_fn and (i & 0x7FF) == 0:
                yield_fn()
            pos = method_ids_off + i * 8
            if pos + 8 > d_len:
                break
            class_idx, proto_idx, name_idx = struct.unpack_from("<HHI", data, pos)
            if class_idx < len(types) and name_idx < len(strings):
                raw_cls = types[class_idx]
                cls_name = raw_cls.lstrip("L").rstrip(";").replace("/", ".")
                m_name = strings[name_idx]
                if cls_name and m_name:
                    bucket = classes.setdefault(cls_name, set())
                    bucket.add(m_name)

        # Convert class method sets to sorted lists
        classes = {k: sorted(list(v)) for k, v in classes.items()}

    except AnalysisCancelled:
        raise
    except Exception:
        return [value for value in strings if value], {k: list(v) if isinstance(v, set) else v for k, v in classes.items()}

    return [value for value in strings if value], classes


def _call_name(node):
    if isinstance(node, ast.Name):
        return node.id
    if isinstance(node, ast.Attribute):
        prefix = _call_name(node.value)
        return f"{prefix}.{node.attr}" if prefix else node.attr
    return ""


def _expression_text(node):
    try:
        value = ast.literal_eval(node)
        if isinstance(value, str):
            return value
    except Exception:
        pass
    if isinstance(node, ast.JoinedStr):
        parts = []
        for value in node.values:
            if isinstance(value, ast.Constant):
                parts.append(str(value.value))
            else:
                parts.append("{…}")
        return "".join(parts)
    try:
        return ast.unparse(node)
    except Exception:
        return "dynamic address"


def extract_network_requests(source):
    requests = []

    def add(method, target):
        value = " ".join(f"{method} {target}".split())[:320]
        if value and value not in requests:
            requests.append(value)

    try:
        tree = ast.parse(source)
        for node in ast.walk(tree):
            if not isinstance(node, ast.Call):
                continue
            name = _call_name(node.func)
            lower = name.lower()
            method = ""
            argument_index = 0
            if lower.startswith(("requests.", "httpx.", "aiohttp.")):
                method = name.rsplit(".", 1)[-1].upper()
                if method == "REQUEST" and node.args:
                    method = _expression_text(node.args[0]).upper()
                    argument_index = 1
            elif lower.endswith(("urlopen", "openconnection")):
                method = "URL"
            elif lower.endswith("query_db_native"):
                method = "GET"
            elif lower.endswith("send_request"):
                method = "TELEGRAM"
            elif lower in ("url", "java.net.url") or lower.endswith(".url"):
                method = "URL"
            if method and len(node.args) > argument_index:
                add(method, _expression_text(node.args[argument_index]))
    except Exception:
        pass
    for value in re.findall(r"https?://[^\s\"'<>]+", source):
        cleaned = value.rstrip("),];}")
        if "{" in cleaned or "}" in cleaned:
            continue
        if not any(cleaned in request for request in requests):
            add("URL", cleaned)
    for value in re.findall(r"(?<![\d.])(?:\d{1,3}\.){3}\d{1,3}(?::\d{1,5})?(?:/[A-Za-z0-9_?=&%./:-]*)?", source):
        cleaned = value.rstrip("),];}")
        if _is_version_like_ip(cleaned):
            continue
        if not any(cleaned in request for request in requests):
            add("IP", cleaned)
    return requests


def _extract_ascii_strings(data):
    return [
        match.decode("utf-8", errors="ignore")
        for match in re.findall(rb"[\x20-\x7e]{4,}", data)
    ]


def _decode_embedded(value):
    compact = re.sub(r"\s+", "", str(value or ""))
    if len(compact) < 24 or len(compact) > MAX_FILE_SIZE * 2:
        return None
    try:
        pad_needed = (4 - (len(compact) % 4)) % 4
        padded = compact + ("=" * pad_needed)
        decoded = base64.b64decode(padded, validate=True)
        if decoded:
            return decoded
    except Exception:
        try:
            pad_needed = (4 - (len(compact) % 4)) % 4
            padded = compact + ("=" * pad_needed)
            decoded = base64.b64decode(padded)
            if decoded:
                return decoded
        except Exception:
            pass
    if len(compact) % 2 == 0 and re.fullmatch(r"[0-9a-fA-F]+", compact):
        try:
            return binascii.unhexlify(compact)
        except Exception:
            pass
    return None


def _decompress(data):
    for window_bits in (zlib.MAX_WBITS, zlib.MAX_WBITS | 16):
        try:
            decoder = zlib.decompressobj(window_bits)
            result = decoder.decompress(data, MAX_ARCHIVE_SIZE + 1)
            if 0 < len(result) <= MAX_ARCHIVE_SIZE:
                return result
        except Exception:
            pass
    return data


def _merge_classes(target, source):
    for class_name, methods in source.items():
        bucket = target.setdefault(class_name, [])
        for method in methods:
            if method not in bucket:
                bucket.append(method)


class PluginAnalyzer:
    def __init__(self, is_cancelled=None):
        self.is_cancelled = is_cancelled
        self.dex_strings = []
        self.classes = {}
        self.files = []
        self.network = []
        self.external = []
        self.native_markers = []
        self.errors = []
        self.py_sources = []
        self.bin_texts = []
        self.b64_findings = []
        self._processed = set()

    def _check_cancelled(self):
        if self.is_cancelled and self.is_cancelled():
            raise AnalysisCancelled("Analysis cancelled")

    def _yield(self):
        self._check_cancelled()

    def analyze(self, file_path):
        t_start = time.time()
        try:
            self._check_cancelled()
            self._target_name = os.path.basename(file_path)
            size = os.path.getsize(file_path)
            log(f"[KPM] [Analysis] 🔍 Starting security analysis for '{self._target_name}' ({size} bytes)")
            if size > MAX_ARCHIVE_SIZE:
                raise ValueError("file is too large")
            with open(file_path, "rb") as source:
                data = source.read(MAX_ARCHIVE_SIZE + 1)
            self._scan_payload(os.path.basename(file_path), data, 0)
        except AnalysisCancelled:
            log("[KPM] [Analysis] ⏹️ Analysis cancelled by user")
            raise
        except Exception as error:
            log_error(error, context="PluginAnalyzer.analyze")
            self.errors.append(str(error))
        self._check_cancelled()
        report = self._build_report()
        elapsed_ms = (time.time() - t_start) * 1000
        log(f"[KPM] [Analysis] ✅ Analysis complete for '{self._target_name}' in {elapsed_ms:.1f}ms: level={report.get('threat_level')}, score={report.get('threat_score')}, threats={len(report.get('threats', []))}")
        return report

    def _scan_payload(self, name, data, depth):
        self._check_cancelled()
        if depth > 4 or not data or len(data) > MAX_ARCHIVE_SIZE:
            return
        payload = _decompress(data)
        content_hash = hashlib.sha256(payload).digest()
        if content_hash in self._processed:
            return
        self._processed.add(content_hash)
        lower_name = name.lower()

        # 1. Dalvik DEX
        if payload.startswith(b"dex\n") or lower_name.endswith(".dex"):
            log(f"[KPM] [Analysis] 📱 Scanning Dalvik DEX: '{name}' ({len(payload)} bytes)")
            self._scan_dex(name, payload)
            return

        # 2. Native ELF (.so)
        if payload.startswith(b"\x7fELF") or lower_name.endswith(".so"):
            log(f"[KPM] [Analysis] ⚙️ Scanning Native ELF .so: '{name}' ({len(payload)} bytes)")
            self._scan_so(name, payload)
            return

        # 3. Zip / Plugin Archive (.zip / .eaf)
        if payload.startswith(b"PK\x03\x04") or lower_name.endswith((".eaf", ".elyx", ".zip")):
            log(f"[KPM] [Analysis] 📦 Scanning Zip archive: '{name}' ({len(payload)} bytes, depth={depth})")
            self._scan_archive(name, payload, depth)
            return

        # 4. Compiled Python (.pyc)
        if lower_name.endswith(".pyc") or (len(payload) > 4 and (payload[2:4] in (b"\r\n", b"\x0d\x0a") or (payload[0] in (0xEE, 0x33, 0x55, 0x6F, 0xA7, 0xCB, 0x42, 0x61, 0x7E, 0x8B, 0xA1, 0x0D, 0x1B, 0x24, 0x31) and payload[1] == 0x0D))):
            log(f"[KPM] [Analysis] ⚡ Scanning compiled Python .pyc: '{name}' ({len(payload)} bytes)")
            self._scan_pyc(name, payload, depth)
            return

        # 5. Python / Plugin / JSON / Text
        if lower_name.endswith((".py", ".pyw", ".plugin", ".txt", ".json", ".yml", ".yaml")) or depth == 0 or ":embedded" in lower_name or "[base64" in lower_name:
            self._scan_text(name, payload, depth)
            return

        if lower_name.endswith(".bin"):
            self._scan_binary(payload)

    def _scan_pyc(self, name, data, depth):
        self._yield()
        try:
            from ..decompiler import decompile_pyc
            dis_src = decompile_pyc(data, name)
            if dis_src and not dis_src.startswith("# Error"):
                log(f"[KPM] [Analysis] ⚡ Decompiled .pyc '{name}' -> {len(dis_src)} chars of Python code")
                self._scan_text(f"{name} [decompiled]", dis_src.encode("utf-8"), depth)
                return
        except Exception as e:
            log_error(e, context=f"decompile_pyc in analysis ({name})")
        self._scan_binary(data)

    def _scan_archive(self, name, data, depth):
        total = 0
        try:
            with zipfile.ZipFile(io.BytesIO(data)) as archive:
                items = archive.infolist()
                log(f"[KPM] [Analysis] 📦 Archive '{name}' contains {len(items)} files")
                for item in items:
                    self._yield()
                    if item.is_dir() or item.file_size > MAX_FILE_SIZE:
                        continue
                    total += item.file_size
                    if total > MAX_ARCHIVE_SIZE:
                        break
                    with archive.open(item) as source:
                        content = source.read(MAX_FILE_SIZE + 1)
                    if len(content) > MAX_FILE_SIZE:
                        continue
                    self._scan_payload(item.filename, content, depth + 1)
        except AnalysisCancelled:
            raise
        except Exception as error:
            log_error(error, context=f"scan_archive ({name})")
            self.errors.append(f"{name}: {error}")

    def _scan_text(self, name, data, depth):
        self._yield()
        source = data.decode("utf-8", errors="ignore")
        if len(source) > 4:
            self.py_sources.append(source)
        self.network.extend(extract_network_requests(source))
        self.external.extend(
            value for value in re.findall(r"https?://[^\s\"'<>]+", source)
            if _external_link(value)
        )

        # Decompile and extract any embedded Base64 payloads (pyc, dex, class, so, zip, multiline chunks)
        if depth <= 3 and any(name.lower().endswith(ext) for ext in (".py", ".pyw", ".plugin", ".json", ".yml", ".yaml", ".txt", ".sh", ".js")):
            try:
                from ..decompiler import extract_base64_payloads
                extracted = extract_base64_payloads(source, source_name=name)
                for item in extracted:
                    item_kind = item.get("kind", "")
                    item_name = item.get("name", "")
                    item_content = item.get("content", "")
                    raw_bytes = item.get("raw_bytes")
                    if item_kind in ("java", "c", "python") and item_name not in self.b64_findings:
                        self.b64_findings.append(item_name)
                        log(f"[KPM] [Analysis] Extracted Base64 payload '{item_name}' (kind={item_kind}) from '{name}'")
                    if raw_bytes:
                        self._scan_payload(item_name, raw_bytes, depth + 1)
                    elif item_kind == "python" and item_content:
                        self._scan_text(item_name, item_content.encode("utf-8"), depth + 1)
            except Exception as ex:
                log_error(ex, context="extract_base64_payloads in analysis")

        values = []
        try:
            for node in ast.walk(ast.parse(source)):
                if isinstance(node, ast.Constant) and isinstance(node.value, str):
                    values.append(node.value)
                elif isinstance(node, ast.Constant) and isinstance(node.value, bytes):
                    try:
                        values.append(node.value.decode("ascii", errors="strict"))
                    except Exception:
                        pass
        except Exception:
            values = re.findall(r"[\"']([A-Za-z0-9+/=]{48,})[\"']", source)
        for index, value in enumerate(values[:MAX_EMBEDDED_VALUES]):
            decoded = _decode_embedded(value.strip())
            if decoded:
                self._scan_payload(f"{name}:embedded-{index + 1}", decoded, depth + 1)

    def _scan_dex(self, name, data):
        self._yield()
        t0 = time.time()
        strings, classes = parse_dex(data, yield_fn=self._yield)
        elapsed_ms = (time.time() - t0) * 1000
        log(f"[KPM] [Analysis] 📱 Parsed DEX '{name}': {len(strings)} strings, {len(classes)} classes ({sum(len(m) for m in classes.values())} methods) in {elapsed_ms:.1f}ms")
        self.dex_strings.extend(strings)
        _merge_classes(self.classes, classes)
        
        # Deep inspection of DEX bytecode and decompilation via inspect_dex
        try:
            from ..decompiler import inspect_dex
            dex_report = inspect_dex(data, name)
            preview = dex_report if dex_report and not dex_report.startswith("// [DEX Error]") else _format_dex_preview(strings, classes)
        except Exception as e:
            log_error(e, context="inspect_dex in scan_dex")
            preview = _format_dex_preview(strings, classes)

        self.files.append({"name": name, "size": len(data), "kind": "DEX", "preview": preview[:MAX_PREVIEW_SIZE]})
        for value in strings:
            if re.match(r"^https?://", value):
                self.network.append(f"DEX {value[:320]}")
            elif re.match(r"(?:\d{1,3}\.){3}\d{1,3}", value) and not _is_version_like_ip(value):
                self.network.append(f"DEX {value[:320]}")

    def _scan_binary(self, data):
        self._yield()
        strings = _extract_ascii_strings(data)
        text = "\n".join(strings)
        if len(text) > 4:
            self.bin_texts.append(text)
        self.network.extend(extract_network_requests(text))
        self.external.extend(
            value for value in re.findall(r"https?://[^\s\"'<>]+", text)
            if _external_link(value)
        )

    def _scan_so(self, name, data):
        self._yield()
        strings = _extract_ascii_strings(data)
        text = "\n".join(strings)
        if len(text) > 4:
            self.bin_texts.append(text)

        preview = ""
        try:
            from ..decompiler import inspect_elf
            elf_report = inspect_elf(data, name)
            if elf_report and not elf_report.startswith("// [ELF Error]"):
                preview = elf_report
        except Exception as e:
            log_error(e, context="inspect_elf in scan_so")

        markers = (
            "execve", "popen", "socket", "connect", "ptrace", "mprotect", "dlopen", "dlsym",
            "/bin/sh", "system", "fork", "execl", "chmod", "setuid", "memfd_create"
        )
        marker_res = [re.compile(_w(marker)) for marker in markers]
        found = [value[:200] for value in strings if any(rx.search(value.lower()) for rx in marker_res)]
        self.native_markers.extend(found)
        self.files.append({"name": name, "size": len(data), "kind": "SO", "preview": (preview or "\n".join(strings))[:MAX_PREVIEW_SIZE]})
        for value in strings:
            if value.startswith(("http://", "https://")):
                self.network.append(f"SO {value[:320]}")

    def _build_report(self):
        self._yield()
        strings = _unique(self.dex_strings)
        searchable = "\n".join(strings + list(self.classes) + [method for methods in self.classes.values() for method in methods]).lower()
        capabilities = []
        capability_patterns = (
            ("cap_net", ("okhttp", "retrofit", "urlconnection", "java.net", "socket")),
            ("cap_files", ("java.io.file", "fileinputstream", "fileoutputstream")),
            ("cap_dexload", ("dexclassloader", "pathclassloader", "inmemorydexclassloader")),
            ("cap_exec", ("runtime.exec", "processbuilder", "/bin/sh")),
            ("cap_reflect", ("java.lang.reflect", "class.forname", "method.invoke")),
            ("cap_crypto", ("javax.crypto", "messagedigest", "secretkeyspec")),
            ("cap_devinfo", ("telephonymanager", "android_id", "getdeviceid", "settings.secure")),
            ("cap_webview", ("android.webkit", "addjavascriptinterface")),
            ("cap_native", ("system.loadlibrary", "system.load", "native method")),
        )
        for key, patterns in capability_patterns:
            if any(re.search(_w(pattern), searchable) for pattern in patterns):
                capabilities.append(_t(key))

        dangerous = []
        dangerous_patterns = (
            ("DexClassLoader", "dng_dexload"),
            ("Runtime.exec", "dng_exec"),
            ("ProcessBuilder", "dng_procbuild"),
            ("addJavascriptInterface", "dng_jsbridge"),
            ("getDeviceId", "dng_devid"),
            ("getInstalledPackages", "dng_packages"),
            ("loadLibrary", "dng_loadlib"),
        )
        for api, desc_key in dangerous_patterns:
            if re.search(_w(api.lower()), searchable):
                dangerous.append(f"{api}: {_t(desc_key)}")

        packages = {}
        for class_name in self.classes:
            parts = class_name.split(".")
            package = ".".join(parts[:3] if len(parts) >= 3 else parts[:-1])
            if package:
                packages[package] = packages.get(package, 0) + 1
        package_rows = [
            f"{name} — {_t('t_classes', n=count)}"
            for name, count in sorted(packages.items(), key=lambda value: (-value[1], value[0]))[:12]
        ]
        dex_text = "\n".join(strings)
        bin_text = "\n".join(self.bin_texts)
        threat_findings = []
        file_units = list(self.py_sources)

        for unit in file_units:
            self._yield()
            threat_findings.extend(_malware_findings(_file_features(unit, yield_fn=self._yield)))

        # Native and DEX raw text scans for Bot Tokens and C2 endpoints
        file_tokens = set()
        file_exfil = False
        all_units = file_units + [dex_text, bin_text]
        for unit in all_units:
            if not unit:
                continue
            self._check_cancelled()
            file_tokens.update(BOT_TOKEN_RE.findall(unit))
            ulow = unit.lower()
            if (_has(_w("send_m" "essage") + r"|" + _w("send_d" "ocument"), ulow)
                    or "api.tele" "gram.org" in ulow
                    or _has(r"\.sendall\s*\(|\.send\s*\(", ulow)
                    or _has(r"drive\.google|discord(app)?\.com/a" r"pi|file\.io|transfer\.sh|catbox\.moe|gofile\.io", ulow)):
                file_exfil = True

        if file_tokens and file_exfil:
            threat_findings.append((25, _t("t_bot_token", n=len(file_tokens))))
        for b64_item in self.b64_findings:
            threat_findings.append((15, _t("t_embedded_b64", name=b64_item)))

        # Native .so threats
        if self.native_markers:
            susp_native = [m for m in self.native_markers if any(k in m.lower() for k in ("execve", "system", "popen", "/bin/sh", "ptrace", "mprotect", "connect"))]
            if susp_native:
                threat_findings.append((20, _t("t_native_dangerous", funcs=", ".join(_unique(susp_native)[:4]))))

        base_name = (getattr(self, "_target_name", "") or "").lower()
        if _MALNAME_RE.search(base_name) and not _MALNAME_SAFE_RE.search(base_name):
            threat_findings.append((12, _t("t_malname")))
        if re.search(r"(?:^|[^a-z0-9])(?:" + r"|".join((
            "r" "at", "back" "door", "ste" r"al\w*", "sp" "am",
            "ra" "id", "flo" "od", "drop" "per", "key" "log", "mi" "ner",
            "sni" "ffer", "payl" "oad",
            "sta" "ger", "loa" "der", "sess" "ion",
        )) + r")(?:[^a-z0-9]|$)", base_name):
            threat_findings.append((3, _t("t_filename")))
        elif re.fullmatch(r"[a-f0-9]{32,}\.(plugin|py|eaf)", base_name):
            threat_findings.append((3, _t("t_hashname")))
        seen_threats = set()
        unique_threats = []
        for weight, text in threat_findings:
            if text not in seen_threats:
                seen_threats.add(text)
                unique_threats.append((weight, text))
        threat_findings = unique_threats
        threat_score = sum(weight for weight, _ in threat_findings)
        threat_level = _threat_level(threat_score)
        return {
            "has_dex": bool(self.classes or self.dex_strings),
            "stats": [
                _t("t_classes", n=len(self.classes)),
                _t("t_methods", n=sum(len(methods) for methods in self.classes.values())),
                _t("t_strings", n=len(strings)),
            ],
            "capabilities": capabilities,
            "dangerous": dangerous,
            "packages": package_rows,
            "files": self.files,
            "network": _unique(self.network),
            "external": _unique(self.external),
            "native": _unique(self.native_markers)[:30],
            "errors": _unique(self.errors),
            "threats": [text for _, text in threat_findings],
            "threat_score": threat_score,
            "threat_level": threat_level,
        }


def _format_dex_preview(strings, classes):
    rows = []
    for class_name in sorted(classes):
        rows.append(class_name)
        rows.extend(f"  {method}()" for method in sorted(classes[class_name]))
    clean_strings = [value for value in strings if len(value) > 3 and value.isprintable()]
    if clean_strings:
        rows.extend(("", _t("t_dex_strings"), ""))
        rows.extend(clean_strings)
    return "\n".join(rows)[:MAX_PREVIEW_SIZE]


def analyze_plugin(file_path, is_cancelled=None):
    return PluginAnalyzer(is_cancelled=is_cancelled).analyze(file_path)
