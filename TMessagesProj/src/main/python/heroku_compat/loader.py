"""Heroku & Hikka Userbot Loader compatibility layer for Miogram."""

import inspect
import logging
from typing import Any, Callable, Dict, List, Optional

logger = logging.getLogger(__name__)


class Strings(dict):
    """Dual dict and callable string lookup for FTG and Hikka modules."""
    def __call__(self, key: str, default: str = "") -> str:
        return self.get(key, default or str(key))


def tds(cls):
    """Decorator to mark Heroku module class (translatable docstring)."""
    cls.__heroku_module__ = True
    if hasattr(cls, "strings") and isinstance(cls.strings, dict) and not isinstance(cls.strings, Strings):
        cls.strings = Strings(cls.strings)
    return cls


translatable_docstring = tds


def command(name: Optional[str] = None):
    """Decorator for module commands."""
    def decorator(func: Callable):
        func.__heroku_cmd__ = True
        func.__heroku_cmd_name__ = name or func.__name__
        return func
    return decorator


def ratelimit(func: Callable):
    """Rate limit decorator."""
    func.ratelimit = True
    return func


# Security & permission decorators (FTG and Hikka)
def _noop_decorator(func: Callable):
    return func


owner = _noop_decorator
sudo = _noop_decorator
support = _noop_decorator
group_owner = _noop_decorator
group_admin = _noop_decorator
group_admin_add_admins = _noop_decorator
group_admin_change_info = _noop_decorator
group_admin_ban_users = _noop_decorator
group_admin_delete_messages = _noop_decorator
group_admin_pin_messages = _noop_decorator
group_admin_invite_users = _noop_decorator
group_member = _noop_decorator
pm = _noop_decorator
unrestricted = _noop_decorator
inline_handler = _noop_decorator
raw_handler = _noop_decorator
tag = _noop_decorator
test = _noop_decorator


class ConfigValue:
    def __init__(self, key: str, default: Any, doc: str = "", validator: Any = None):
        self.key = key
        self.default = default
        self.doc = doc
        self.validator = validator
        self.value = default


class ModuleConfig(dict):
    """Flexible config supporting both Hikka (ConfigValue objects) and FTG (key, default, doc triples)."""

    def __init__(self, *args):
        super().__init__()
        self._configs: Dict[str, ConfigValue] = {}
        self._docstrings: Dict[str, str] = {}

        if args and isinstance(args[0], ConfigValue):
            for c in args:
                if isinstance(c, ConfigValue):
                    self._configs[c.key] = c
                    self[c.key] = c.value
                    self._docstrings[c.key] = c.doc
        elif args:
            # FTG triple convention: ("key1", val1, "doc1", "key2", val2, "doc2", ...)
            i = 0
            while i < len(args):
                key = str(args[i])
                val = args[i + 1] if i + 1 < len(args) else None
                doc = str(args[i + 2]) if i + 2 < len(args) else ""
                cfg = ConfigValue(key, val, doc)
                self._configs[key] = cfg
                self[key] = val
                self._docstrings[key] = doc
                i += 3

    def __getitem__(self, item: str) -> Any:
        if item in self._configs:
            return self._configs[item].value
        return super().get(item, None)

    def __setitem__(self, key: str, value: Any):
        super().__setitem__(key, value)
        if key in self._configs:
            self._configs[key].value = value
        else:
            self._configs[key] = ConfigValue(key, value)

    def get(self, key: str, default: Any = None) -> Any:
        if key in self._configs:
            return self._configs[key].value
        return super().get(key, default)

    def getdoc(self, key: str, message: Any = None) -> str:
        return self._docstrings.get(key, "")


class Validators:
    class Boolean:
        def validate(self, v): return bool(v)

    class Choice:
        def __init__(self, choices): self.choices = choices
        def validate(self, v): return v in self.choices

    class String:
        def validate(self, v): return str(v)

    class Integer:
        def validate(self, v): return int(v)


validators = Validators()


class _FakeAllModules:
    def __init__(self):
        self.modules = []

    async def log(self, event: str, **kwargs):
        pass


class Module:
    """Base class for all Heroku/Hikka/FTG userbot modules."""
    strings = {"name": "Unnamed Module"}

    def __init__(self):
        self.config = ModuleConfig()
        self._client = None
        self.client = None
        self.inline = None
        self.allmodules = _FakeAllModules()
        self._db = {}
        if hasattr(self, "strings") and isinstance(self.strings, dict) and not isinstance(self.strings, Strings):
            self.strings = Strings(self.strings)

    def get_string(self, key: str, default: str = "") -> str:
        if isinstance(self.strings, dict):
            return self.strings.get(key, default or key)
        return str(key)

    def get(self, key: str, default: Any = None) -> Any:
        return self._db.get(key, default)

    def set(self, key: str, value: Any) -> None:
        self._db[key] = value

    async def client_ready(self, client: Any, db: Any = None):
        self._client = client
        self.client = client
        self._db = db if db is not None else {}

    async def on_unload(self):
        pass
