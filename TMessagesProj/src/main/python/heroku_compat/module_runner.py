"""On-device executor for Hikka/Heroku .py userbot modules (Miogram).

Loads a module file with the local device-safe heroku_compat shims,
finds the loader.Module subclass, and runs a command handler or filter_outgoing
with an enhanced mock message object. All replies, edits, and deletes are
captured and returned as JSON so Java can execute them in Telegram.
"""

import asyncio
import importlib.util
import inspect
import json
import os
import sys
import traceback
import types
from typing import Any, Dict, List, Optional


def _setup_compat_packages():
    """Ensure relative imports like 'from .. import loader, utils' and 'from hikka import ...' resolve."""
    from heroku_compat import loader as compat_loader
    from heroku_compat import utils as compat_utils

    for pkg_name in ("friendly_telegram", "hikka"):
        if pkg_name not in sys.modules:
            pkg = types.ModuleType(pkg_name)
            pkg.loader = compat_loader
            pkg.utils = compat_utils
            sys.modules[pkg_name] = pkg

        subpkg_name = f"{pkg_name}.modules"
        if subpkg_name not in sys.modules:
            subpkg = types.ModuleType(subpkg_name)
            sys.modules[subpkg_name] = subpkg

    # Mock telethon if not installed on device
    if "telethon" not in sys.modules:
        try:
            import telethon  # noqa: F401
        except ImportError:
            t = types.ModuleType("telethon")
            t_tl = types.ModuleType("telethon.tl")
            t_types = types.ModuleType("telethon.tl.types")
            t_funcs = types.ModuleType("telethon.tl.functions")
            t_utils = types.ModuleType("telethon.utils")

            class _DummyType:
                def __init__(self, *args, **kwargs):
                    self.id = kwargs.get("id", 0)
                    self.user_id = kwargs.get("user_id", 0)
                    self.first_name = kwargs.get("first_name", "User")
                    self.last_name = kwargs.get("last_name", "")
                    self.username = kwargs.get("username", "")

            t_types.User = _DummyType
            t_types.Channel = _DummyType
            t_types.Chat = _DummyType
            t_types.Message = _DummyType
            t_types.PeerUser = _DummyType
            t_types.PeerChat = _DummyType
            t_types.PeerChannel = _DummyType

            t_utils.get_display_name = lambda u: getattr(u, "first_name", "User")
            t_utils.resolve_id = lambda i: (i, "user")
            t_utils.get_peer_id = lambda p: getattr(p, "id", 0)

            t.tl = t_tl
            t.tl.types = t_types
            t.tl.functions = t_funcs
            t.utils = t_utils
            sys.modules["telethon"] = t
            sys.modules["telethon.tl"] = t_tl
            sys.modules["telethon.tl.types"] = t_types
            sys.modules["telethon.tl.functions"] = t_funcs
            sys.modules["telethon.utils"] = t_utils


class _FakeEntity:
    def __init__(self, entity_id: int = 0, name: str = "User"):
        self.id = entity_id
        self.first_name = name
        self.last_name = ""
        self.username = f"user_{entity_id}" if entity_id else ""

    def __str__(self):
        return self.first_name


class _FakeClient:
    def __init__(self, message: "_FakeMessage"):
        self._msg = message

    async def get_me(self):
        return _FakeEntity(777000, "Me")

    async def get_entity(self, entity_id: Any):
        eid = int(entity_id) if str(entity_id).isdigit() else 12345
        return _FakeEntity(eid, str(entity_id))

    async def is_bot(self):
        return False

    async def send_message(self, entity: Any, text: str, **kwargs):
        self._msg.replies.append(["respond", str(text)])
        return text

    async def send_file(self, entity: Any, file: Any, **kwargs):
        caption = kwargs.get("caption", "")
        self._msg.replies.append(["respond", f"📎 {caption}"])
        return True

    async def delete_messages(self, entity: Any, message_ids: Any):
        self._msg.replies.append(["delete_messages", str(message_ids)])
        return True

    async def download_profile_photo(self, entity: Any, file: Any = None):
        return None

    async def iter_messages(self, *args, **kwargs):
        if False:
            yield None

    async def iter_participants(self, *args, **kwargs):
        if False:
            yield None


class _FakeReplyMessage:
    def __init__(self, reply_text: str = "", reply_id: int = 1, sender_id: int = 0):
        self.id = reply_id
        self.text = reply_text or ""
        self.raw_text = reply_text or ""
        self.message = reply_text or ""
        self.sender_id = sender_id
        self.from_id = sender_id
        self.sender = _FakeEntity(sender_id, "User")


class _FakeMessage:
    """Full-featured stand-in for a Telethon message."""

    def __init__(self, full_text: str, args: str, reply_text: Optional[str] = None, reply_id: int = 0, chat_id: int = 0):
        self.text = full_text or ""
        self.raw_text = full_text or ""
        self.message = full_text or ""
        self.args = args or ""
        self.id = 9999
        self.chat_id = chat_id
        self.to_id = chat_id
        self.sender_id = 777000
        self.is_channel = chat_id < 0
        self.is_group = chat_id < 0
        self.is_private = chat_id > 0
        self.reply_to_msg_id = reply_id
        self.is_reply = bool(reply_id > 0 or reply_text)
        self._reply_obj = _FakeReplyMessage(reply_text or "", reply_id) if self.is_reply else None
        self.replies: List[List[str]] = []
        self.client = _FakeClient(self)

    async def edit(self, text, **kwargs):
        self.replies.append(["edit", str(text)])
        return text

    async def respond(self, text, **kwargs):
        self.replies.append(["respond", str(text)])
        return text

    async def reply(self, text, **kwargs):
        self.replies.append(["respond", str(text)])
        return text

    async def delete(self):
        self.replies.append(["delete", ""])
        return True

    async def get_reply_message(self):
        return self._reply_obj


def _run_coro(coro, timeout):
    try:
        loop = asyncio.new_event_loop()
        try:
            return (True, loop.run_until_complete(asyncio.wait_for(coro, timeout)))
        finally:
            try:
                loop.close()
            except Exception:
                pass
    except Exception as e:
        return (False, repr(e))


_MODULE_CACHE: Dict[str, Any] = {}


def _load_module(path: str):
    _setup_compat_packages()
    from heroku_compat import loader as compat_loader

    try:
        st = os.stat(path)
        sig = (st.st_mtime_ns, st.st_size)
    except Exception:
        sig = None

    if sig is not None:
        hit = _MODULE_CACHE.get(path)
        if hit is not None and hit[0] == sig:
            return hit[1], compat_loader, hit[2]

    mod_name = "friendly_telegram.modules.user_mod_%d" % (abs(hash(path)) % 1000000)
    spec = importlib.util.spec_from_file_location(mod_name, path)
    if spec is None or spec.loader is None:
        return None, None, "cannot load module file"

    mod = importlib.util.module_from_spec(spec)
    mod.__package__ = "friendly_telegram.modules"
    sys.modules[mod_name] = mod

    try:
        spec.loader.exec_module(mod)
    except Exception as e:
        if sig is not None:
            _MODULE_CACHE[path] = (sig, None, f"import error: {repr(e)}")
        return None, None, f"import error: {repr(e)}"

    if sig is not None:
        _MODULE_CACHE[path] = (sig, mod, None)
    return mod, compat_loader, None


def _iter_module_classes(mod, compat_loader):
    found = []
    for attr in dir(mod):
        if attr.startswith("__"):
            continue
        try:
            obj = getattr(mod, attr)
        except Exception:
            continue
        try:
            if inspect.isclass(obj) and issubclass(obj, compat_loader.Module) and obj is not compat_loader.Module:
                found.append(obj)
        except Exception:
            continue
    return found


def _match_cmd(fn_name: str, marked_name: Optional[str], cmd: str) -> bool:
    cmd = (cmd or "").lower()
    if marked_name and str(marked_name).lower() == cmd:
        return True
    base = fn_name[:-3] if fn_name.endswith("cmd") else fn_name
    return base.lower() == cmd


def _find_handler(mod, compat_loader, cmd: str):
    """Returns (instance, bound_fn) or (None, None)."""
    for cls in _iter_module_classes(mod, compat_loader):
        try:
            inst = cls()
        except Exception:
            continue
        for mname in dir(inst):
            if mname.startswith("_"):
                continue
            try:
                fn = getattr(inst, mname)
            except Exception:
                continue
            if not callable(fn):
                continue
            raw = getattr(fn, "__func__", fn)
            marked = getattr(fn, "__heroku_cmd__", False) or getattr(raw, "__heroku_cmd__", False)
            marked_name = getattr(fn, "__heroku_cmd_name__", None) or getattr(raw, "__heroku_cmd_name__", None)
            fname = getattr(fn, "__name__", None) or getattr(raw, "__name__", mname)
            if (marked and _match_cmd(fname, marked_name, cmd)) or _match_cmd(fname, None, cmd):
                return inst, fn

    # Fallback: module-level function
    for fname in (cmd, cmd + "cmd"):
        try:
            fn = getattr(mod, fname, None)
        except Exception:
            fn = None
        if callable(fn):
            return None, fn
    return None, None


def run_command(path: str, cmd: str, args: str, full_text: str, reply_text: Optional[str] = None, reply_id: int = 0, chat_id: int = 0, timeout: int = 25) -> str:
    """Runs one module command. Returns JSON: {"replies": [[kind, text]], "error": str|None}."""
    out: Dict[str, Any] = {"replies": [], "error": None}
    try:
        mod, compat_loader, err = _load_module(path)
        if err is not None:
            out["error"] = err
            return json.dumps(out)

        inst, fn = _find_handler(mod, compat_loader, cmd or "")
        if fn is None:
            out["error"] = "command not found in module"
            return json.dumps(out)

        msg = _FakeMessage(full_text, args, reply_text=reply_text, reply_id=reply_id, chat_id=chat_id)

        async def _call():
            res = fn(msg)
            if inspect.isawaitable(res):
                await res
            return True

        ok, res = _run_coro(_call(), timeout)
        if not ok:
            out["error"] = str(res)
        out["replies"] = list(msg.replies)
        return json.dumps(out)
    except Exception:
        out["error"] = traceback.format_exc(limit=3)
        return json.dumps(out)


def has_filter(path: str) -> bool:
    """True if the module defines filter_outgoing."""
    try:
        mod, compat_loader, err = _load_module(path)
        if err is not None:
            return False
        for cls in _iter_module_classes(mod, compat_loader):
            if hasattr(cls, "filter_outgoing"):
                return True
        return hasattr(mod, "filter_outgoing")
    except Exception:
        return False


def run_filter(path: str, text: str, timeout: int = 8) -> str:
    """Runs filter_outgoing(self, text). Returns JSON: {"text": str, "error": str|None}."""
    out: Dict[str, Any] = {"text": text, "error": None}
    try:
        mod, compat_loader, err = _load_module(path)
        if err is not None:
            out["error"] = err
            return json.dumps(out)

        target = None
        inst = None
        for cls in _iter_module_classes(mod, compat_loader):
            if hasattr(cls, "filter_outgoing"):
                try:
                    inst = cls()
                    target = getattr(inst, "filter_outgoing")
                    break
                except Exception:
                    continue

        if target is None and hasattr(mod, "filter_outgoing"):
            try:
                target = getattr(mod, "filter_outgoing")
            except Exception:
                target = None

        if target is None:
            out["error"] = "no filter_outgoing"
            return json.dumps(out)

        async def _call():
            res = target(text) if inst is None else target(text)
            if inspect.isawaitable(res):
                res = await res
            return res

        ok, res = _run_coro(_call(), timeout)
        if not ok:
            out["error"] = str(res)
        elif isinstance(res, str):
            out["text"] = res
        return json.dumps(out)
    except Exception:
        out["error"] = traceback.format_exc(limit=2)
        return json.dumps(out)
