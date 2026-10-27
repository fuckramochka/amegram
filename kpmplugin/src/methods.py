import os
import re
from java.util import Locale


_kpm_instance = None


def _set_kpm_instance(inst):
    global _kpm_instance
    _kpm_instance = inst
    try:
        import kpm_bridge
        kpm_bridge.register_kpm_instance(inst)
    except Exception:
        pass


def get_kpm_instance():
    return _kpm_instance


SELF_PLUGIN_ID = "kangel_plugins_manager"


def _is_self_plugin(plugin_id):
    try:
        return str(plugin_id or "").strip().lower() == SELF_PLUGIN_ID
    except Exception:
        return False

try:
    from elyx import metainfo as _metainfo
    __version__ = _metainfo.get("version", "1.6.0")
    __icon__ = _metainfo.get("icon", "Kangelcons_by_fStikBot/5")
    __codename__ = _metainfo.get("codename", "Angel's Rest")
except Exception:
    __version__ = "1.6.0"
    __icon__ = "Kangelcons_by_fStikBot/5"
    __codename__ = "Angel's Rest"




def log(msg):
    text = str(msg)
    if not text.startswith("[KPM]"):
        text = f"[KPM] {text}"
    _push_log(text)
    try:
        if _kpm_instance and not _kpm_instance.get_setting("logs_enabled", True):
            return
    except Exception:
        pass
    from android_utils import log as _alog
    _alog(text)


def log_error(err, context=None):
    try:
        import traceback
        tb = traceback.format_exc()
        prefix = f"[{context}] " if context else ""
        log(f"{prefix}Error: {err}\nTraceback:\n{tb}")
    except Exception:
        log(f"Error: {err}")


LOG_CAPACITY = 20000
_log_buffer = []


def _push_log(line):
    try:
        from time import strftime, localtime
        ts = strftime("%H:%M:%S", localtime())
        entry = f"[{ts}] {line}"
        _log_buffer.append(entry)
        if len(_log_buffer) > LOG_CAPACITY:
            del _log_buffer[:len(_log_buffer) - LOG_CAPACITY]
        _write_log_to_file(entry)
    except Exception:
        pass


def get_log_buffer():
    path = get_log_file_path()
    if path and os.path.exists(path):
        try:
            with open(path, "r", encoding="utf-8", errors="ignore") as f:
                return f.read().splitlines()
        except Exception:
            pass
    return list(_log_buffer)


def get_log_file_path():
    try:
        from org.telegram.messenger import ApplicationLoader
        files_dir = ApplicationLoader.applicationContext.getFilesDir().getAbsolutePath()
        return os.path.join(files_dir, "kpm", "latestlog.txt")
    except Exception:
        return None


def _write_log_to_file(entry):
    try:
        path = get_log_file_path()
        if not path:
            return
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "a", encoding="utf-8", errors="ignore") as f:
            f.write(f"{entry}\n")
    except Exception:
        pass


def clear_log_buffer():
    _log_buffer.clear()
    try:
        path = get_log_file_path()
        if path and os.path.exists(path):
            os.remove(path)
    except Exception:
        pass


def _normalize_lang(lang):
    if not lang:
        return None
    lang = str(lang).lower()
    if lang.startswith("ru"):
        return "ru"
    elif lang.startswith("uk") or lang.startswith("ua"):
        return "uk"
    elif lang.startswith("de"):
        return "de"
    elif lang.startswith("es"):
        return "es"
    elif lang.startswith("fr"):
        return "fr"
    elif lang.startswith("pt"):
        return "pt"
    elif lang.startswith("zh") or lang.startswith("cn") or lang.startswith("chi"):
        return "zh"
    elif lang.startswith("en"):
        return "en"
    return None


def _get_lang():
    raw_values = []

    try:
        from org.telegram.messenger import LocaleController
        if hasattr(LocaleController, "getLocaleStringIso639"):
            raw_values.append(str(LocaleController.getLocaleStringIso639()).lower())
    except Exception:
        pass

    try:
        from org.telegram.messenger import LocaleController
        lc = LocaleController.getInstance()
        if lc:
            curr_info = getattr(lc, "getCurrentLocaleInfo", lambda: None)()
            if curr_info:
                if hasattr(curr_info, "shortName") and curr_info.shortName:
                    raw_values.append(str(curr_info.shortName).lower())
                elif hasattr(curr_info, "getLangCode"):
                    raw_values.append(str(curr_info.getLangCode()).lower())
            if not raw_values:
                curr_loc = getattr(lc, "getCurrentLocale", lambda: None)()
                if curr_loc and hasattr(curr_loc, "getLanguage"):
                    raw_values.append(str(curr_loc.getLanguage()).lower())
    except Exception:
        pass

    try:
        from org.telegram.messenger import MessagesController
        prefs = MessagesController.getGlobalMainSettings()
        if prefs:
            lkey = prefs.getString("language", None)
            if lkey:
                raw_values.append(str(lkey).lower())
    except Exception:
        pass

    try:
        raw_values.append(str(Locale.getDefault().getLanguage()).lower())
    except Exception:
        pass

    for raw in raw_values:
        result = _normalize_lang(raw)
        if result:
            return result

    return "en"


def _get_faq_url():
    lang = _get_lang()
    if lang == "ru":
        return "https://t.me/KangelPluginsManager/81/206"
    return "https://t.me/KangelPluginsManager/81/464"


def _get_localized_description(plugin_info, plugins_dir, plugin_id):
    cur_lang = _get_lang()
    desc = plugin_info.get("description", "") if isinstance(plugin_info, dict) else ""
    if isinstance(plugin_info, dict):
        lang_desc = plugin_info.get(f"description_{cur_lang}")
        if lang_desc:
            return lang_desc
    if not desc:
        from .elyx_parser import get_localized_description_from_dir
        for eaf_dir in (
            os.path.join(plugins_dir, "ElyxPlugins", plugin_id),
            os.path.join(plugins_dir, plugin_id),
        ):
            if os.path.isdir(eaf_dir):
                loc_desc = get_localized_description_from_dir(eaf_dir, cur_lang)
                if loc_desc:
                    return loc_desc
                break
    return desc


def _tr(key):
    try:
        from elyx import strings as _elyx_strings
        val = _elyx_strings.get(key)
        if val is not None:
            return str(val)
    except Exception:
        pass
    return key


def _normalize_search_text(text):
    try:
        if not text:
            return ""
        s = str(text).lower()
        s = re.sub(r"[^0-9a-zа-яё_\-\s]", " ", s, flags=re.IGNORECASE)
        s = re.sub(r"\s+", " ", s).strip()
        return s
    except:
        return ""


def _iter_trigrams(text):
    s = _normalize_search_text(text)
    if not s or len(s) < 3:
        return []
    return [s[i:i+3] for i in range(0, len(s) - 2)]


def _build_trigram_index(plugins_list):
    index = {}
    cache = {}
    for pid, info in (plugins_list or {}).items():
        try:
            name = ""
            author = ""
            if isinstance(info, dict):
                name = info.get("name") or ""
                author = info.get("author") or ""
            text = f"{pid} {name} {author}"
            norm = _normalize_search_text(text)
            cache[pid] = norm
            for tri in _iter_trigrams(norm):
                bucket = index.get(tri)
                if bucket is None:
                    bucket = set()
                    index[tri] = bucket
                bucket.add(pid)
        except:
            continue
    return index, cache


def _trigram_search(query, trigram_index, allowed_ids=None):
    q = _normalize_search_text(query)
    if not q or len(q) < 3:
        return []
    trigs = _iter_trigrams(q)
    if not trigs:
        return []
    allowed_set = set(allowed_ids) if allowed_ids is not None else None
    scores = {}
    for tri in trigs:
        ids = trigram_index.get(tri)
        if not ids:
            continue
        for pid in ids:
            if allowed_set is None or pid in allowed_set:
                scores[pid] = scores.get(pid, 0) + 1
    if not scores:
        return []
    min_score = max(1, int(len(trigs) * 0.35))
    filtered = [(pid, sc) for pid, sc in scores.items() if sc >= min_score]
    filtered.sort(key=lambda x: (-x[1], x[0]))
    return [pid for pid, _ in filtered]


def _fix_plurals(text, count):
    try:
        from elyx import strings as _elyx_strings
        full = _elyx_strings.pluralize(count, "plugin_forms")
        word = full.split(' ', 1)[-1] if ' ' in full else full
        for old in ("плагинов", "плагина", "плагин",
                     "плагінів", "плагіни", "плагін",
                     "plugins", "Plugins"):
            if old in text:
                return text.replace(old, word, 1)
    except Exception:
        pass
    return text


def _status_label(status):
    s = (status or "plugin").lower()
    labels = {
        "library": "status_library",
        "customization": "status_customization",
        "utilities": "status_utilities",
        "informational": "status_informational",
        "fun": "status_fun",
        "messages": "status_messages",
    }
    return _tr(labels.get(s, "status_plugin"))


def _status_sort_key(status):
    s = (status or "plugin").lower()
    order = {"library": 0, "customization": 1, "utilities": 2,
             "informational": 3, "messages": 4, "fun": 5}
    return order.get(s, 99)


def _normalize_min_version(value):
    if not value:
        return ""
    m = re.search(r"(\d+(?:\.\d+)+)", str(value))
    return m.group(1) if m else str(value).strip()


def _normalize_requirements(value):
    if not value:
        return []
    if isinstance(value, (list, tuple, set)):
        items = list(value)
    else:
        items = str(value).split(",")
    result = []
    for item in items:
        text = str(item).strip()
        if text and text not in result:
            result.append(text)
    return result


def _format_badge_compact(prefix, values, max_items=2):
    parts = _normalize_requirements(values)
    if not parts:
        return ""
    text = ", ".join(parts[:max_items])
    if len(parts) > max_items:
        text += f" +{len(parts) - max_items}"
    return f"{prefix} {text}".strip()


_URLSpanClass = None


def _get_url_span_class():
    global _URLSpanClass
    if _URLSpanClass is not None:
        return _URLSpanClass
    try:
        from hook_utils import find_class
        cls = find_class("org.telegram.ui.Components.URLSpanNoUnderline")
        if cls is not None:
            _URLSpanClass = cls
            return _URLSpanClass
    except Exception:
        pass
    try:
        from android.text.style import URLSpan
        _URLSpanClass = URLSpan
        return _URLSpanClass
    except Exception:
        return None


def _create_link_span(url, text_color=None):
    cls = _get_url_span_class()
    if cls is None:
        return None
    try:
        return cls(str(url))
    except Exception:
        return None


_active_sheets = set()
_sheet_dismissers = []


def register_active_sheet(sheet):
    if sheet is not None:
        try:
            _active_sheets.add(sheet)
        except Exception:
            pass


def unregister_active_sheet(sheet):
    try:
        _active_sheets.discard(sheet)
    except Exception:
        pass


def register_sheet_dismisser(dismisser_fn):
    if dismisser_fn not in _sheet_dismissers:
        _sheet_dismissers.append(dismisser_fn)


def unregister_sheet_dismisser(dismisser_fn):
    try:
        _sheet_dismissers.remove(dismisser_fn)
    except Exception:
        pass


def dismiss_active_sheets():
    from android_utils import run_on_ui_thread

    def _do():
        for fn in list(_sheet_dismissers):
            try:
                fn()
            except Exception as e:
                log_error(e, context="dismisser")

        for s in list(_active_sheets):
            try:
                if s is not None and hasattr(s, "dismiss"):
                    s.dismiss()
                    log(f"[KPM] dismiss_active_sheets: dismissed {s}")
            except Exception as se:
                log_error(se, context="dismiss_active_sheets")
        _active_sheets.clear()

        try:
            from .hooks.install_sheet import dismiss_active_install_sheet
            dismiss_active_install_sheet()
        except Exception:
            pass

    run_on_ui_thread(_do)


def _handle_internal_url(url, widget=None):
    if not url:
        return
    dismiss_active_sheets()
    u = str(url).strip()

    clean_user = None
    if u.startswith("@"):
        clean_user = u[1:].strip()
    elif u.startswith("tg://resolve?domain="):
        after = u[len("tg://resolve?domain="):]
        clean_user = after.split("&", 1)[0].split("#", 1)[0].strip()
    else:
        m = re.match(r'^(?:https?://)?(?:www\.)?(?:t\.me|telegram\.me)/([a-zA-Z0-9_]+)/?$', u, re.IGNORECASE)
        if m:
            cand = m.group(1).strip()
            reserved = {"joinchat", "addstickers", "addtheme", "setlanguage", "share", "c", "socks", "proxy", "login", "iv"}
            if cand.lower() not in reserved:
                clean_user = cand

    if clean_user:
        open_username(clean_user)
        return

    open_link(u)


_LocaleUtils_cls = None


def _get_locale_utils():
    global _LocaleUtils_cls
    if _LocaleUtils_cls is not None:
        return _LocaleUtils_cls
    try:
        from hook_utils import find_class
        for name in (
            "com.exteragram.messenger.utils.text.LocaleUtils",
            "com.exteragram.messenger.utils.LocaleUtils",
        ):
            cls = find_class(name)
            if cls:
                _LocaleUtils_cls = cls
                return cls
    except Exception:
        pass
    return None


def _resolve_fragment(frag=None):
    if frag is not None:
        return frag
    try:
        from client_utils import get_last_fragment
        f = get_last_fragment()
        if f:
            return f
    except Exception:
        pass
    try:
        from org.telegram.ui import LaunchActivity
        if hasattr(LaunchActivity, "getSafeLastFragment"):
            f = LaunchActivity.getSafeLastFragment()
            if f:
                return f
        if hasattr(LaunchActivity, "getLastFragment"):
            f = LaunchActivity.getLastFragment()
            if f:
                return f
    except Exception:
        pass
    return None


def _make_runnable(callback):
    if not callback:
        return None
    try:
        from hook_utils import find_class
        from java import dynamic_proxy
        Runnable_cls = find_class("java.lang.Runnable")
        if not Runnable_cls:
            return None

        class _FnRunnable(dynamic_proxy(Runnable_cls)):
            def __init__(self, fn):
                super().__init__()
                self.fn = fn

            def run(self):
                try:
                    if self.fn:
                        self.fn()
                except Exception as e:
                    log_error(e, context="runnable")

        return _FnRunnable(callback)
    except Exception as e:
        log_error(e, context="_make_runnable")
        return None


def format_with_usernames(text, fragment=None, on_dismiss=None):
    if not text:
        return text
    try:
        from hook_utils import find_class
        from android.text import SpannableStringBuilder, Spanned
        URLSpanNoUnderline = find_class("org.telegram.ui.Components.URLSpanNoUnderline")
        if not URLSpanNoUnderline:
            from android.text.style import URLSpan as URLSpanNoUnderline

        raw_str = str(text)
        builder = SpannableStringBuilder(raw_str)
        mention_re = re.compile(r'(?:@|(?:https?://)?(?:www\.)?(?:t\.me|telegram\.me)/)([a-zA-Z0-9_]{3,32})')
        found = False
        for m in mention_re.finditer(raw_str):
            start = m.start()
            end = m.end()
            username = m.group(1)
            span = URLSpanNoUnderline(f"https://t.me/{username}")
            builder.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            found = True
        if found:
            return builder
    except Exception as e:
        log_error(e, context="format_with_usernames")
    spannable, _ = _parse_markdown(str(text))
    return spannable or text


def fully_format_text(text, fragment=None, on_dismiss=None):
    if not text:
        return text
    try:
        spannable, plain = _parse_markdown(str(text))
        if spannable:
            return spannable
        if plain:
            return format_with_usernames(plain, fragment, on_dismiss)
    except Exception as e:
        log_error(e, context="fully_format_text")
    return text


def attach_link_handler(view, on_link_click=None):
    pass



def _parse_markdown(text, text_color=None):
    if not text:
        return None, text
    try:
        from android.text import SpannableString
        from android.text.style import StyleSpan, ForegroundColorSpan
        from android.graphics import Typeface

        def to_java_pos(py_pos, original_text):
            java_pos = 0
            for i, char in enumerate(original_text):
                if i >= py_pos:
                    break
                java_pos += 2 if ord(char) > 0xFFFF else 1
            return java_pos

        spans = []
        plain_text = text

        def apply_pattern(source_text, pattern, span_factory, text_getter):
            local_spans = []
            cursor = 0
            parts = []
            for match in pattern.finditer(source_text):
                parts.append(source_text[cursor:match.start()])
                replacement = text_getter(match)
                start = sum(len(p) for p in parts)
                end = start + len(replacement)
                span_obj = span_factory(match)
                if isinstance(span_obj, (list, tuple)):
                    for item in span_obj:
                        if item is not None:
                            local_spans.append((start, end, item))
                elif span_obj is not None:
                    local_spans.append((start, end, span_obj))
                parts.append(replacement)
                cursor = match.end()
            parts.append(source_text[cursor:])
            return "".join(parts), local_spans

        plain_text, local_spans = apply_pattern(
            plain_text, re.compile(r'\*\*(.+?)\*\*'),
            lambda m: StyleSpan(Typeface.BOLD), lambda m: m.group(1))
        spans.extend(local_spans)

        plain_text, local_spans = apply_pattern(
            plain_text, re.compile(r'(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)'),
            lambda m: StyleSpan(Typeface.ITALIC), lambda m: m.group(1))
        spans.extend(local_spans)

        plain_text, local_spans = apply_pattern(
            plain_text, re.compile(r'\[([^\]]+)\]\(([^)]+)\)'),
            lambda m: _create_link_span(m.group(2), text_color), lambda m: m.group(1))
        spans.extend(local_spans)

        mention_pattern = re.compile(r'@([a-zA-Z0-9_]+)')
        for match in mention_pattern.finditer(plain_text):
            start = match.start()
            username = match.group(1)
            end = start + len(match.group(0))
            url = f"https://t.me/{username}"
            span = _create_link_span(url, text_color)
            if span is not None:
                spans.append((start, end, span))
            if text_color is not None:
                spans.append((start, end, ForegroundColorSpan(text_color)))

        url_pattern = re.compile(r'(https?://[^\s\)\]\n]+)')
        for match in url_pattern.finditer(plain_text):
            start = match.start()
            url = match.group(1)
            end = start + len(url)
            span = _create_link_span(url, text_color)
            if span is not None:
                spans.append((start, end, span))
            if text_color is not None:
                spans.append((start, end, ForegroundColorSpan(text_color)))

        java_len = to_java_pos(len(plain_text), plain_text)
        result = SpannableString(plain_text)
        for start, end, span in spans:
            java_start = to_java_pos(start, plain_text)
            java_end = to_java_pos(end, plain_text)
            try:
                if 0 <= java_start < java_end <= java_len:
                    result.setSpan(span, java_start, java_end,
                                   SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
            except Exception:
                pass
        return result, plain_text
    except Exception:
        return None, text


def open_link(arg1, arg2=None, allow_internal=True):
    from android_utils import run_on_ui_thread
    from client_utils import get_last_fragment

    if arg2 is not None:
        passed_context, url = arg1, str(arg2)
    else:
        passed_context, url = None, str(arg1)

    def go():
        dismiss_active_sheets()
        u = url.strip()
        if allow_internal:
            clean_user = None
            if u.startswith("@"):
                clean_user = u[1:].strip()
            elif u.startswith("tg://resolve?domain="):
                after = u[len("tg://resolve?domain="):]
                clean_user = after.split("&", 1)[0].split("#", 1)[0].strip()
            else:
                m = re.match(r'^(?:https?://)?(?:www\.)?(?:t\.me|telegram\.me)/([a-zA-Z0-9_]+)/?$', u, re.IGNORECASE)
                if m:
                    cand = m.group(1).strip()
                    reserved = {"joinchat", "addstickers", "addtheme", "setlanguage", "share", "c", "socks", "proxy", "login", "iv"}
                    if cand.lower() not in reserved:
                        clean_user = cand

            if clean_user:
                open_username(clean_user)
                return

        context = passed_context
        try:
            if context is None:
                fragment = get_last_fragment()
                if fragment:
                    context = fragment.getParentActivity()
        except Exception:
            pass
        if context is None:
            try:
                from org.telegram.ui import LaunchActivity
                context = getattr(LaunchActivity, "instance", None)
            except Exception:
                pass
        if context is None:
            try:
                from org.telegram.messenger import ApplicationLoader
                context = ApplicationLoader.applicationContext
            except Exception:
                pass
        if context is None:
            return
        try:
            from org.telegram.messenger.browser import Browser
            Browser.openUrl(context, url)
            return
        except Exception:
            pass
        try:
            from android.content import Intent
            from android.net import Uri
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        except Exception:
            pass

    run_on_ui_thread(go)


def open_username(username):
    from android_utils import run_on_ui_thread
    from client_utils import get_last_fragment

    def go():
        dismiss_active_sheets()
        clean_user = str(username).strip()
        try:
            if clean_user.startswith("tg://resolve?domain="):
                after = clean_user[len("tg://resolve?domain="):]
                clean_user = after.split("&", 1)[0].split("#", 1)[0].strip()
            else:
                m = re.match(r'^(?:https?://)?(?:www\.)?(?:t\.me|telegram\.me)/([a-zA-Z0-9_]+)/?$', clean_user, re.IGNORECASE)
                if m:
                    clean_user = m.group(1).strip()

            clean_user = clean_user.lstrip("@").strip()
            if not clean_user:
                return

            log(f"[KPM] open_username: resolving @{clean_user}")

            fragment = _resolve_fragment()
            if fragment is not None:
                try:
                    ctrl = getattr(fragment, "getMessagesController", lambda: None)()
                    if not ctrl:
                        from org.telegram.messenger import MessagesController, UserConfig
                        account = UserConfig.selectedAccount
                        if hasattr(fragment, "getCurrentAccount"):
                            account = fragment.getCurrentAccount()
                        elif hasattr(fragment, "currentAccount"):
                            account = fragment.currentAccount
                        ctrl = MessagesController.getInstance(account)

                    if ctrl and hasattr(ctrl, "openByUserName"):
                        log(f"[KPM] open_username: calling openByUserName for @{clean_user}")
                        try:
                            ctrl.openByUserName(clean_user, fragment, 0)
                            return
                        except Exception as oe0:
                            log_error(oe0, context="openByUserName type=0")
                            try:
                                ctrl.openByUserName(clean_user, fragment, 1)
                                return
                            except Exception as oe1:
                                log_error(oe1, context="openByUserName type=1")
                except Exception as oe:
                    log_error(oe, context="openByUserName")

            from org.telegram.ui import LaunchActivity
            act = getattr(LaunchActivity, "instance", None)
            if not act and fragment:
                try:
                    act = fragment.getParentActivity()
                except Exception:
                    pass


            # 2. Try Browser.openAsInternalIntent (direct in-app routing to LaunchActivity)
            if act:
                try:
                    from org.telegram.messenger.browser import Browser
                    tme_url = f"https://t.me/{clean_user}"
                    log(f"[KPM] open_username: calling Browser.openAsInternalIntent for @{clean_user}")
                    if Browser.openAsInternalIntent(act, tme_url):
                        return
                except Exception as be:
                    log_error(be, context="openAsInternalIntent")

            # 3. Try Browser.openUrl
            if act:
                try:
                    from org.telegram.messenger.browser import Browser
                    log(f"[KPM] open_username: calling Browser.openUrl for @{clean_user}")
                    Browser.openUrl(act, f"https://t.me/{clean_user}")
                    return
                except Exception as ue:
                    log_error(ue, context="Browser.openUrl")

        except Exception as e:
            log_error(e, context="open_username")

        # Fallback to open_link without recursion
        open_link(f"https://t.me/{clean_user}", allow_internal=False)

    run_on_ui_thread(go)


def is_beta_code_viewer_enabled(pl=None) -> bool:
    if pl is None:
        pl = _kpm_instance
    if not pl:
        return False
    try:
        val = pl.get_setting("beta_code_viewer", False)
        if isinstance(val, bool):
            return val
        if isinstance(val, (int, float)):
            return val != 0
        if isinstance(val, str):
            return val.strip().lower() in ("true", "1", "yes", "on")
        return bool(val)
    except Exception:
        return False




