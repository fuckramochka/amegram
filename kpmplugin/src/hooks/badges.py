# Thanks @mr_Vestr for logic badges :)
import json
import ssl
import threading
import urllib.request

from base_plugin import MethodHook
from hook_utils import find_class

from java.lang import Long
from android_utils import run_on_ui_thread

from ..methods import log, log_error, _get_lang, _normalize_lang

try:
    from hook_utils import get_private_field
except ImportError:
    def get_private_field(obj, field_name):
        try:
            field = obj.getClass().getDeclaredField(field_name)
            field.setAccessible(True)
            return field.get(obj)
        except Exception:
            return None


def _get_context():
    try:
        from org.telegram.messenger import ApplicationLoader
        if ApplicationLoader:
            return ApplicationLoader.applicationContext
    except Exception:
        pass
    return None


def _save_cached_badges(data):
    try:
        ctx = _get_context()
        if not ctx or not data:
            return
        prefs = ctx.getSharedPreferences("kpm_badges", 0)
        editor = prefs.edit()
        editor.putString("config", json.dumps(data))
        editor.apply()
    except Exception as e:
        log_error(e, context="_save_cached_badges")


def _load_cached_badges():
    try:
        ctx = _get_context()
        if not ctx:
            return None
        prefs = ctx.getSharedPreferences("kpm_badges", 0)
        raw = prefs.getString("config", None)
        if raw:
            return json.loads(raw)
    except Exception as e:
        log_error(e, context="_load_cached_badges")
    return None


def _fetch_url(url, timeout=10):
    try:
        ctx = ssl.create_default_context()
        ctx.check_hostname = False
        ctx.verify_mode = ssl.CERT_NONE
        req = urllib.request.Request(
            url,
            headers={"User-Agent": "Mozilla/5.0 (Android; Telegram)"}
        )
        with urllib.request.urlopen(req, context=ctx, timeout=timeout) as resp:
            return resp.read().decode("utf-8")
    except Exception as e:
        log_error(e, context=f"_fetch_url {url}")
        return None


class _BadgeHook(MethodHook):
    def __init__(self, manager):
        super().__init__()
        self.manager = manager

    def before_hooked_method(self, param):
        try:
            if not param.args:
                return
            curr_lang = _get_lang()
            if curr_lang != self.manager.lang:
                self.manager.update_lang(curr_lang)

            entity_id = param.args[0]
            if entity_id in self.manager.custom_badges:
                param.setResult(self.manager.custom_badges[entity_id])
                return
            if entity_id is not None:
                try:
                    eid_int = int(entity_id)
                    if eid_int in self.manager.custom_badges:
                        param.setResult(self.manager.custom_badges[eid_int])
                        return
                except Exception:
                    pass
                try:
                    eid_str = str(entity_id)
                    if eid_str in self.manager.custom_badges:
                        param.setResult(self.manager.custom_badges[eid_str])
                        return
                except Exception:
                    pass
        except Exception:
            pass


class _BadgesControllerGetBadgeHook(MethodHook):
    def __init__(self, manager):
        super().__init__()
        self.manager = manager

    def before_hooked_method(self, param):
        try:
            curr_lang = _get_lang()
            if curr_lang != self.manager.lang:
                self.manager.update_lang(curr_lang)

            if not param.args:
                return
            arg = param.args[0]
            if not arg:
                return

            entity_id = getattr(arg, "id", None)
            if entity_id is None:
                entity_id = arg

            if entity_id in self.manager.custom_badges:
                param.setResult(self.manager.custom_badges[entity_id])
                return
            if entity_id is not None:
                try:
                    eid_int = int(entity_id)
                    if eid_int in self.manager.custom_badges:
                        param.setResult(self.manager.custom_badges[eid_int])
                        return
                except Exception:
                    pass
                try:
                    eid_str = str(entity_id)
                    if eid_str in self.manager.custom_badges:
                        param.setResult(self.manager.custom_badges[eid_str])
                        return
                except Exception:
                    pass
        except Exception:
            pass


class _DeveloperHook(MethodHook):
    def __init__(self, manager):
        super().__init__()
        self.manager = manager

    def before_hooked_method(self, param):
        try:
            if not param.args:
                return
            entity_id = param.args[0]
            if entity_id in self.manager.custom_badges:
                param.setResult(True)
                return
            if entity_id is not None:
                try:
                    eid_int = int(entity_id)
                    if eid_int in self.manager.custom_badges:
                        param.setResult(True)
                        return
                except Exception:
                    pass
                try:
                    eid_str = str(entity_id)
                    if eid_str in self.manager.custom_badges:
                        param.setResult(True)
                        return
                except Exception:
                    pass
        except Exception:
            pass


class _ChangeHook(MethodHook):
    def __init__(self, manager):
        super().__init__()
        self.manager = manager

    def before_hooked_method(self, param):
        try:
            if not param.args:
                return
            entity_id = param.args[0]
            if entity_id in self.manager.custom_badges:
                param.setResult(True)
                return
            if entity_id is not None:
                try:
                    eid_int = int(entity_id)
                    if eid_int in self.manager.custom_badges:
                        param.setResult(True)
                        return
                except Exception:
                    pass
                try:
                    eid_str = str(entity_id)
                    if eid_str in self.manager.custom_badges:
                        param.setResult(True)
                        return
                except Exception:
                    pass
        except Exception:
            pass


class _ShowBadgeBulletinHook(MethodHook):
    def __init__(self, manager):
        super().__init__()
        self.manager = manager

    def before_hooked_method(self, param):
        try:
            curr_lang = _get_lang()
            if curr_lang != self.manager.lang:
                self.manager.update_lang(curr_lang)

            if not param.args:
                return

            badge_dto = None
            user_or_chat = None
            entity_id = None

            for arg in param.args:
                if not arg:
                    continue
                try:
                    cls_name = arg.getClass().getName()
                except Exception:
                    continue
                if "BadgeDTO" in cls_name:
                    badge_dto = arg
                elif "TLRPC$User" in cls_name or "TLRPC$Chat" in cls_name:
                    user_or_chat = arg
                    entity_id = getattr(arg, "id", None)

            entry = None
            if entity_id is not None:
                entry = self.manager.get_entry_for_entity(entity_id)

            if not entry and badge_dto:
                try:
                    doc_id = getattr(badge_dto, "getDocumentId", lambda: None)()
                    if doc_id:
                        entry = self.manager.get_entry_for_doc_id(doc_id)
                except Exception:
                    pass

            if entry:
                new_text = self.manager.format_badge_text(entry, curr_lang, user_or_chat)
                if new_text:
                    if badge_dto and hasattr(badge_dto, "setText"):
                        try:
                            badge_dto.setText(new_text)
                        except Exception:
                            pass

                    if len(param.args) >= 3:
                        arg2 = param.args[2]
                        if arg2 is not None:
                            try:
                                arg2_cls = arg2.getClass().getName()
                                if "String" in arg2_cls or "CharSequence" in arg2_cls or "Spannable" in arg2_cls:
                                    try:
                                        LocaleUtils = find_class("com.exteragram.messenger.utils.LocaleUtils")
                                        formatted_seq = LocaleUtils.formatWithUsernames(new_text) if LocaleUtils else None
                                        param.args[2] = formatted_seq or new_text
                                    except Exception:
                                        param.args[2] = new_text
                            except Exception:
                                pass
        except Exception:
            pass


class _ApplyLanguageHook(MethodHook):
    def __init__(self, manager):
        super().__init__()
        self.manager = manager

    def after_hooked_method(self, param):
        try:
            new_lang = None
            if param.args and len(param.args) > 0:
                loc_info = param.args[0]
                if loc_info:
                    if hasattr(loc_info, "shortName") and loc_info.shortName:
                        new_lang = str(loc_info.shortName).lower()
                    elif hasattr(loc_info, "getLangCode"):
                        new_lang = str(loc_info.getLangCode()).lower()

            clean_lang = _normalize_lang(new_lang) if new_lang else None
            if not clean_lang:
                clean_lang = _get_lang()

            def _refresh():
                try:
                    self.manager.update_lang(clean_lang)
                    LaunchActivity = find_class("org.telegram.ui.LaunchActivity")
                    ProfileActivity = find_class("org.telegram.ui.ProfileActivity")
                    if LaunchActivity and ProfileActivity:
                        last = getattr(LaunchActivity, "getLastFragment", lambda: None)()
                        if last and isinstance(last, ProfileActivity):
                            if hasattr(last, "updateProfileData"):
                                last.updateProfileData(True)
                except Exception:
                    pass

            run_on_ui_thread(_refresh)
        except Exception:
            pass


class BadgeManager:
    def __init__(self, plugin):
        self.plugin = plugin
        self.custom_badges = {}
        self._badge_entries = []
        self._hook_refs = []
        self.api_badge_source = None
        self.lang = _get_lang()
        self.raw_badges_data = None
        self._observer = None
        self._setup_observer()
        self._get_api_badge_source()
        self._setup_hooks()

    def _setup_observer(self):
        def _attach():
            try:
                from org.telegram.messenger import NotificationCenter
                from java import dynamic_proxy
                delegate_cls = dynamic_proxy(NotificationCenter.NotificationCenterDelegate)

                mgr_ref = [self]

                class LangObserver(delegate_cls):
                    def didReceivedNotification(self, id, account, *args):
                        try:
                            if id == NotificationCenter.reloadInterface:
                                run_on_ui_thread(lambda: mgr_ref[0].update_lang())
                        except Exception:
                            pass

                self._observer = LangObserver()
                NotificationCenter.getGlobalInstance().addObserver(self._observer, NotificationCenter.reloadInterface)
            except Exception as e:
                log_error(e, context="badges observer setup")

        run_on_ui_thread(_attach)

    def get_entry_for_entity(self, entity_id):
        try:
            eid = int(entity_id)
            for entry in self._badge_entries:
                uid = entry.get("user_id")
                if uid is not None and int(uid) == eid:
                    return entry
                cid = entry.get("chat_id")
                if cid is not None:
                    cid_int = int(cid)
                    pos_id = abs(cid_int) - 1000000000000 if str(cid_int).startswith("-100") else abs(cid_int)
                    if eid in (cid_int, pos_id, -pos_id, -abs(cid_int)):
                        return entry
        except Exception:
            pass
        return None

    def get_entry_for_doc_id(self, doc_id):
        try:
            did = int(doc_id)
            for entry in self._badge_entries:
                edid = entry.get("emoji_id") or entry.get("documentId") or entry.get("document_id")
                if edid and int(edid) == did:
                    return entry
        except Exception:
            pass
        return None

    def format_badge_text(self, entry, lang, user_or_chat=None):
        if not entry:
            return ""
        raw_text = entry.get("text")
        text = None
        if isinstance(raw_text, dict):
            text = (
                raw_text.get(lang)
                or raw_text.get("ru")
                or raw_text.get("en")
                or next(iter(raw_text.values()), "")
            )
        elif isinstance(raw_text, str):
            text = raw_text

        if not text:
            text = entry.get(f"text_{lang}") or entry.get("text_ru") or entry.get("text_en") or ""

        if not text:
            return ""

        name = ""
        if user_or_chat:
            try:
                cls_name = user_or_chat.getClass().getName()
                if "TLRPC$User" in cls_name:
                    fn = getattr(user_or_chat, "first_name", "") or ""
                    ln = getattr(user_or_chat, "last_name", "") or ""
                    name = f"{fn} {ln}".strip()
                elif "TLRPC$Chat" in cls_name:
                    name = getattr(user_or_chat, "title", "") or ""
            except Exception:
                pass

        if not name:
            uid = entry.get("user_id")
            cid = entry.get("chat_id")
            try:
                MessagesController = find_class("org.telegram.messenger.MessagesController")
                mc = MessagesController.getInstance(0) if MessagesController else None
                if uid and mc:
                    u = mc.getUser(Long.valueOf(int(uid))) or mc.getUser(int(uid))
                    if u and getattr(u, "first_name", None):
                        name = f"{u.first_name} {u.last_name or ''}".strip()
                elif cid and mc:
                    c = mc.getChat(Long.valueOf(int(cid))) or mc.getChat(int(cid))
                    if c and getattr(c, "title", None):
                        name = c.title
            except Exception:
                pass

        if not name:
            name = f"User {entry.get('user_id')}" if entry.get('user_id') else f"Chat {entry.get('chat_id')}"

        try:
            return text.format(name=name, user_name=name, chat_name=name)
        except Exception:
            return text

    def update_lang(self, new_lang=None):
        try:
            if new_lang is None:
                new_lang = _get_lang()
            log(f"[KPM] dynamic badge update: lang='{self.lang}' -> '{new_lang}'")
            self.lang = new_lang
            if not self._badge_entries and self.raw_badges_data:
                self._load_badges(self.raw_badges_data)
            else:
                self._apply_badges_for_lang(new_lang)
        except Exception as e:
            log_error(e, context="BadgeManager update_lang")

    def setup(self, badges_data):
        try:
            self.raw_badges_data = badges_data
            self._get_api_badge_source()
            self._setup_hooks()
            self._load_badges(badges_data)
        except Exception as e:
            log_error(e, context="BadgeManager setup")

    def _get_api_badge_source(self):
        try:
            BadgesController = find_class("com.exteragram.messenger.badges.BadgesController")
            if not BadgesController:
                return
            instance = getattr(BadgesController, "INSTANCE", BadgesController)
            self.api_badge_source = get_private_field(instance, "apiBadgeSource")
            if not self.api_badge_source:
                self.api_badge_source = get_private_field(BadgesController, "apiBadgeSource")
        except Exception:
            pass

    def _setup_hooks(self):
        try:
            if self._hook_refs:
                return

            ApiBadgeSource = find_class("com.exteragram.messenger.badges.source.ApiBadgeSource")
            if ApiBadgeSource:
                for method_name, hook_cls in [
                    ("getBadge", _BadgeHook),
                    ("isDeveloper", _DeveloperHook),
                    ("canChangeBadge", _ChangeHook),
                ]:
                    try:
                        refs = self.plugin.hook_all_methods(ApiBadgeSource, method_name, hook_cls(self))
                        if refs:
                            if isinstance(refs, list):
                                self._hook_refs.extend(refs)
                            else:
                                self._hook_refs.append(refs)
                    except Exception:
                        pass

            BadgesController = find_class("com.exteragram.messenger.badges.BadgesController")
            if BadgesController:
                for method_name, hook_cls in [
                    ("getBadge", _BadgesControllerGetBadgeHook),
                    ("showBadgeBulletin", _ShowBadgeBulletinHook),
                ]:
                    try:
                        refs = self.plugin.hook_all_methods(BadgesController, method_name, hook_cls(self))
                        if refs:
                            if isinstance(refs, list):
                                self._hook_refs.extend(refs)
                            else:
                                self._hook_refs.append(refs)
                    except Exception:
                        pass

            LocaleController = find_class("org.telegram.messenger.LocaleController")
            if LocaleController:
                try:
                    refs = self.plugin.hook_all_methods(LocaleController, "applyLanguage", _ApplyLanguageHook(self))
                    if refs:
                        if isinstance(refs, list):
                            self._hook_refs.extend(refs)
                        else:
                            self._hook_refs.append(refs)
                except Exception:
                    pass

            if hasattr(self.plugin, "_track_hooks") and self._hook_refs:
                self.plugin._track_hooks(self._hook_refs)
        except Exception as e:
            log_error(e, context="BadgeManager _setup_hooks")

    def _load_badges(self, badges_data):
        try:
            if not badges_data:
                return

            self.raw_badges_data = badges_data
            normalized_entries = []

            if isinstance(badges_data, dict):
                if "badges" in badges_data and isinstance(badges_data["badges"], list):
                    normalized_entries.extend(badges_data["badges"])
                else:
                    for eid_str, binfo in badges_data.items():
                        if not isinstance(binfo, dict):
                            continue
                        try:
                            eid = int(eid_str)
                        except Exception:
                            continue
                        doc_id = binfo.get("documentId") or binfo.get("emoji_id") or binfo.get("document_id")
                        entry_dict = {
                            "emoji_id": doc_id,
                            "text": binfo.get("text"),
                        }
                        for k, v in binfo.items():
                            if k.startswith("text_"):
                                entry_dict[k] = v

                        if eid > 0:
                            entry_dict["user_id"] = eid
                        else:
                            entry_dict["chat_id"] = eid
                        normalized_entries.append(entry_dict)
            elif isinstance(badges_data, list):
                normalized_entries = badges_data

            self._badge_entries = normalized_entries
            self._apply_badges_for_lang(self.lang)
        except Exception as e:
            log_error(e, context="BadgeManager _load_badges")

    def _apply_badges_for_lang(self, lang):
        try:
            if not self._badge_entries:
                return

            if not self.api_badge_source:
                self._get_api_badge_source()

            cache = None
            if self.api_badge_source:
                try:
                    cache = get_private_field(self.api_badge_source, "cache")
                except Exception:
                    cache = None

            BadgeDTO = find_class("com.exteragram.messenger.api.dto.BadgeDTO")
            BadgeInfo = find_class("com.exteragram.messenger.badges.source.BadgeInfo")
            ProfileStatus = find_class("com.exteragram.messenger.api.model.ProfileStatus")

            if not BadgeDTO:
                return

            for entry in self._badge_entries:
                if not isinstance(entry, dict):
                    continue
                emoji_id = entry.get("emoji_id") or entry.get("documentId") or entry.get("document_id")
                if not emoji_id:
                    continue
                try:
                    emoji_id = int(emoji_id)
                except Exception:
                    continue

                formatted = self.format_badge_text(entry, lang)
                if not formatted:
                    continue

                user_id = entry.get("user_id")
                chat_id = entry.get("chat_id")

                if user_id:
                    try:
                        uid = int(user_id)
                    except Exception:
                        continue

                    existing_dto = self.custom_badges.get(uid) or self.custom_badges.get(str(uid))
                    if existing_dto and hasattr(existing_dto, "setText"):
                        try:
                            existing_dto.setText(formatted)
                            dto = existing_dto
                        except Exception:
                            dto = BadgeDTO(emoji_id, formatted)
                    else:
                        dto = BadgeDTO(emoji_id, formatted)

                    for key in [uid, str(uid)]:
                        self.custom_badges[key] = dto
                    try:
                        self.custom_badges[Long.valueOf(uid)] = dto
                    except Exception:
                        pass

                    if cache and BadgeInfo and ProfileStatus:
                        try:
                            info = BadgeInfo(dto, ProfileStatus.DEVELOPER, True)
                            cache.put(Long.valueOf(uid), info)
                        except Exception:
                            pass

                elif chat_id:
                    try:
                        cid = int(chat_id)
                    except Exception:
                        continue
                    pos_id = abs(cid) - 1000000000000 if str(cid).startswith("-100") else abs(cid)

                    existing_dto = self.custom_badges.get(cid) or self.custom_badges.get(str(cid))
                    if existing_dto and hasattr(existing_dto, "setText"):
                        try:
                            existing_dto.setText(formatted)
                            dto = existing_dto
                        except Exception:
                            dto = BadgeDTO(emoji_id, formatted)
                    else:
                        dto = BadgeDTO(emoji_id, formatted)

                    ids_to_add = [cid, pos_id, -pos_id, -abs(cid)]
                    for eid in set(ids_to_add):
                        self.custom_badges[eid] = dto
                        self.custom_badges[str(eid)] = dto
                        try:
                            self.custom_badges[Long.valueOf(eid)] = dto
                        except Exception:
                            pass

                    if cache and BadgeInfo and ProfileStatus:
                        try:
                            info = BadgeInfo(dto, ProfileStatus.DEVELOPER, True)
                            for eid in set(ids_to_add):
                                cache.put(Long.valueOf(eid), info)
                        except Exception:
                            pass

        except Exception as e:
            log_error(e, context="BadgeManager _apply_badges_for_lang")

    def cleanup(self):
        try:
            if self._observer:
                def _detach(obs):
                    try:
                        from org.telegram.messenger import NotificationCenter
                        NotificationCenter.getGlobalInstance().removeObserver(obs, NotificationCenter.reloadInterface)
                    except Exception:
                        pass
                run_on_ui_thread(lambda: _detach(self._observer))
                self._observer = None

            for ref in self._hook_refs:
                try:
                    self.plugin.unhook_method(ref)
                except Exception:
                    pass
            self._hook_refs.clear()
            if self.api_badge_source and self.custom_badges:
                try:
                    cache = get_private_field(self.api_badge_source, "cache")
                    if cache:
                        for eid in list(self.custom_badges.keys()):
                            try:
                                cache.remove(Long.valueOf(int(eid)))
                            except Exception:
                                pass
                except Exception:
                    pass
            self.custom_badges.clear()
            self._badge_entries.clear()
        except Exception as e:
            log_error(e, context="BadgeManager cleanup")


def install_badge_hook(pl):
    try:
        if hasattr(pl, "_badge_manager") and pl._badge_manager:
            try:
                pl._badge_manager.cleanup()
            except Exception:
                pass
        pl._badge_manager = BadgeManager(pl)

        cached = _load_cached_badges()
        if cached:
            pl._badge_manager.setup(cached)

        def _load_cached_delayed():
            c = _load_cached_badges()
            if c and hasattr(pl, "_badge_manager") and pl._badge_manager:
                pl._badge_manager.setup(c)
        run_on_ui_thread(_load_cached_delayed, 1000)

        def _fetch_and_apply():
            badges_data = None
            urls = [
                "https://git.kangel.xyz/KangelPlugins/KPM-Resources/raw/branch/main/badges.json",
                "https://raw.githubusercontent.com/Kangel-Plugins/KPM-Resources/main/badges.json",
                "https://github.com/Kangel-Plugins/KPM-Resources/raw/main/badges.json",
            ]
            for u in urls:
                raw_kpm = _fetch_url(u)
                if raw_kpm:
                    try:
                        badges_data = json.loads(raw_kpm)
                        if badges_data:
                            break
                    except Exception as ex:
                        log_error(ex, context=f"badges json parse {u}")

            if badges_data and hasattr(pl, "_badge_manager") and pl._badge_manager:
                run_on_ui_thread(lambda: pl._badge_manager.setup(badges_data))
                _save_cached_badges(badges_data)
            else:
                c = _load_cached_badges()
                if c and hasattr(pl, "_badge_manager") and pl._badge_manager:
                    run_on_ui_thread(lambda: pl._badge_manager.setup(c))

        threading.Thread(target=_fetch_and_apply, daemon=True).start()
    except Exception as e:
        log_error(e, context="install_badge_hook")
