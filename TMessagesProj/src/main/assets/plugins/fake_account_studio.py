"""
Fake Account & Privacy Demo Studio Plugin for Yumigram / Amegram
Allows creating simulated demo profiles (with toggleable Premium, verified badge, custom phone, username, bio)
and injecting realistic demo chats for video ad filming and testing without leaking personal data.
"""

import time
import json
from typing import Any, Dict, List, Optional

from base_plugin import BasePlugin, MethodHook, HookResult, HookStrategy
from hook_utils import find_class
from android_utils import run_on_ui_thread, R
import ui.settings as _settings
from ui.bulletin import BulletinHelper

__id__ = "fake_account_studio"
__name__ = "Fake Account & Demo Studio"
__version__ = "1.0.0"
__author__ = "@dkramochka"
__description__ = (
    "Studio for filming video ads and testing without leaking personal data.\n"
    "• Fake profile (name, phone, @username, bio)\n"
    "• Toggleable Telegram Premium (with star badge & emoji)\n"
    "• Realistic demo chats (Durov, Support, Updates, Dev Team)\n"
    "• Anti-Leak privacy mode and interactive auto-replies"
)
__icon__ = "outline_account_circle_24"
__app_version__ = ">=12.5.0"
__sdk_version__ = ">=1.0.0"
__permissions__ = ["hooks", "ui", "settings", "messages.read", "messages.send"]


def _jclass(name: str):
    """Safely resolve a Java class via Chaquopy or hook_utils without crashing on host."""
    try:
        from java import jclass
        return jclass(name)
    except Exception:
        pass
    try:
        return find_class(name)
    except Exception:
        return None


# Pre-defined realistic demo peers for ad shoots
DEFAULT_DEMO_PEERS: Dict[int, Dict[str, Any]] = {
    99990001: {
        "type": "user",
        "first_name": "Pavel",
        "last_name": "Durov",
        "username": "durov",
        "phone": "15552887683",
        "bio": "Building Telegram & Free Internet ✈️",
        "verified": True,
        "premium": True,
        "pinned": True,
        "unread_count": 1,
        "initial_message": "Yumigram has taken UI fluidity to a whole new level! 🔥",
        "history": [
            ("in", "Hey! Just saw the latest build with custom player and fluid shaders."),
            ("out", "Hi Pavel! How do you like our new 120 FPS physics and UI Studio?"),
            ("in", "Yumigram has taken UI fluidity to a whole new level! 🔥"),
        ],
        "replies": [
            "Great work! Animations and customization feel extraordinarily smooth. Looking forward to the promo video! 🚀",
            "Looks fantastic. The main thing is to keep privacy and stability top-notch.",
            "Impressive release, keep raising the bar! 👍",
        ]
    },
    99990002: {
        "type": "user",
        "first_name": "Yumigram",
        "last_name": "Support",
        "username": "yumi_support",
        "phone": "15550000001",
        "bio": "Official Yumigram Support & Help Center 💬",
        "verified": True,
        "premium": True,
        "pinned": True,
        "unread_count": 0,
        "initial_message": "Welcome! Your client is fully configured and ready for the video shoot. ✨",
        "history": [
            ("in", "Welcome! Your client is fully configured and ready for the video shoot. ✨"),
        ],
        "replies": [
            "Thank you for reaching out! If you have any questions regarding plugin setup or theme customization, we're here to help. 🤝",
            "All client systems are running smoothly (120 FPS). Have a great recording session!",
        ]
    },
    -99990003: {
        "type": "channel",
        "title": "Yumigram Updates & News",
        "username": "yumigram_official",
        "bio": "Official news and release announcements for Yumigram.",
        "verified": True,
        "unread_count": 3,
        "initial_message": "🚀 Yumigram v12.9 is live! Enjoy 120 FPS ultra-smooth scrolling, Glassmorphism, and custom UI themes.",
        "history": [
            ("in", "🚀 Yumigram v12.9 is live! Enjoy 120 FPS ultra-smooth scrolling, Glassmorphism, and custom UI themes.\n\n• Complete data privacy in Demo mode\n• Ultra-smooth 120 FPS physics\n• Full profile and drawer customization"),
        ],
        "replies": [
            "New updates will be published soon! Stay tuned 📣",
        ]
    },
    99990004: {
        "type": "user",
        "first_name": "Alice",
        "last_name": "Designer",
        "username": "alice_design",
        "phone": "15557890123",
        "bio": "UI/UX & Shader Designer @ Yumigram 🎨",
        "verified": False,
        "premium": True,
        "pinned": False,
        "unread_count": 0,
        "initial_message": "Hey! Just sent the new shader concepts in Figma, check them out when you have a minute 🎨",
        "history": [
            ("in", "Hey! Just sent the new shader concepts in Figma, check them out when you have a minute 🎨"),
            ("out", "Awesome! Recording promo footage right now, the effects look stunning."),
            ("in", "Agreed, waiting for the final video! Don't forget to showcase the side drawer 💜"),
        ],
        "replies": [
            "Awesome, the promo video is going to look incredible! 💜",
            "The color palette in dark mode is simply gorgeous! ✨",
        ]
    },
    -99990005: {
        "type": "chat",
        "title": "Yumi Core Dev Team",
        "bio": "Engineers working on Yumigram core, renderer, and plugins.",
        "verified": False,
        "unread_count": 5,
        "initial_message": "Mark: ARM64 release build compiled cleanly! Rock solid 120 FPS across all devices.",
        "history": [
            ("in", "Alex: Render latency dropped to 2ms, zero frame drops."),
            ("in", "Mark: ARM64 release build compiled cleanly! Rock solid 120 FPS across all devices."),
        ],
        "replies": [
            "Alex: Verified! Ran memory leak diagnostics, everything is pristine.",
            "Mark: Excellent, tagging the release in git now!",
        ]
    },
    99990006: {
        "type": "user",
        "first_name": "TON",
        "last_name": "Whale",
        "username": "ton_whale",
        "phone": "15558880099",
        "bio": "Web3 Investor & TON Ecosystem Contributor 💎",
        "verified": False,
        "premium": True,
        "pinned": False,
        "unread_count": 0,
        "initial_message": "Check out the new TON Connect integration in the latest build. Lightning fast! ⚡️",
        "history": [
            ("in", "Check out the new TON Connect integration in the latest build. Lightning fast! ⚡️"),
        ],
        "replies": [
            "LFG! Web3 features in Yumigram are revolutionary 💎",
        ]
    }
}

DEMO_PEERS: Dict[int, Dict[str, Any]] = dict(DEFAULT_DEMO_PEERS)


class FakeAccountStudioPlugin(BasePlugin):

    def __init__(self):
        super().__init__()
        self._hooks: List[Any] = []
        self._local_settings: Dict[str, Any] = {}
        self._original_user_backup: Optional[Dict[str, Any]] = None
        self._custom_chats: Dict[int, Dict[str, Any]] = {}
        self._demo_users: Dict[int, Any] = {}
        self._demo_chats: Dict[int, Any] = {}
        self._demo_full_users: Dict[int, Any] = {}
        self._demo_full_chats: Dict[int, Any] = {}
        self._chat_histories: Dict[int, List[Dict[str, Any]]] = {}
        self._reply_index: Dict[int, int] = {}
        self._next_msg_id = 500000

    def set_setting(self, key: str, value, reload_settings: bool = False):
        self._local_settings[key] = value
        super().set_setting(key, value, reload_settings)

    def get_setting(self, key: str, default=None):
        if self._exteraless_bridge_available():
            val = super().get_setting(key, default=None)
            if val is not None:
                return val
        return self._local_settings.get(key, default)

    # ==================== Lifecycle ====================

    def on_plugin_load(self):
        self.log("FakeAccountStudioPlugin: loading Fake Account & Privacy Demo Studio...")
        try:
            self._load_custom_chats()
        except Exception as e:
            self.log(f"FakeAccountStudioPlugin: load_custom_chats warning: {e}")

        try:
            self._init_demo_data()
        except Exception as e:
            self.log(f"FakeAccountStudioPlugin: init_demo_data warning: {e}")

        try:
            self._install_hooks()
        except Exception as e:
            self.log(f"FakeAccountStudioPlugin: install_hooks warning: {e}")

        try:
            self.add_on_send_message_hook(priority=100)
        except Exception as e:
            self.log(f"FakeAccountStudioPlugin: add_on_send_message_hook warning: {e}")

        # If fake profile is enabled, apply to current user
        try:
            if self.is_fake_account_active():
                self._apply_fake_identity_to_current_user()
        except Exception as e:
            self.log(f"FakeAccountStudioPlugin: apply_fake_identity warning: {e}")

        self.log("FakeAccountStudioPlugin: initialized successfully")

    def on_plugin_unload(self):
        self.log("FakeAccountStudioPlugin: unloading and restoring data...")
        self.remove_hook("on_send_message_hook")
        self._restore_real_user()

        if self._hooks:
            for hook in self._hooks:
                try:
                    self.unhook_method(hook)
                except Exception as e:
                    self.log(f"FakeAccountStudioPlugin: unhook error: {e}")
            self._hooks.clear()

        self._notify_ui_refresh()
        self.log("FakeAccountStudioPlugin: unloaded cleanly")

    # ==================== Data Initialization ====================

    def _load_custom_chats(self):
        raw = self.get_setting("custom_demo_chats_json", "")
        if raw:
            try:
                chats = json.loads(raw)
                for c in chats:
                    cid = int(c["id"])
                    self._custom_chats[cid] = c
                    DEMO_PEERS[cid] = c
            except Exception as e:
                self.log(f"_load_custom_chats error: {e}")

    def _save_custom_chats(self):
        try:
            data = json.dumps(list(self._custom_chats.values()))
            self.set_setting("custom_demo_chats_json", data)
        except Exception as e:
            self.log(f"_save_custom_chats error: {e}")

    def _init_demo_data(self):
        """Build in-memory message history and peer structures."""
        current_time = int(time.time())
        for peer_id, info in DEMO_PEERS.items():
            if peer_id not in self._chat_histories:
                history_list = []
                for idx, (direction, text) in enumerate(info.get("history", [])):
                    msg_date = current_time - (len(info["history"]) - idx) * 120
                    history_list.append({
                        "id": self._generate_msg_id(),
                        "peer_id": peer_id,
                        "text": text,
                        "out": (direction == "out"),
                        "date": msg_date,
                    })
                self._chat_histories[peer_id] = history_list

    def _generate_msg_id(self) -> int:
        self._next_msg_id += 1
        return self._next_msg_id

    # ==================== Settings State ====================

    def is_fake_account_active(self) -> bool:
        return bool(self.get_setting("fake_account_enabled", True))

    def is_fake_premium(self) -> bool:
        return bool(self.get_setting("fake_premium", True))

    def is_fake_verified(self) -> bool:
        return bool(self.get_setting("fake_verified", True))

    def is_hide_real_chats(self) -> bool:
        # Default False: real chats are NOT replaced or hidden by default!
        return bool(self.get_setting("hide_real_chats", False))

    def is_demo_chats_active(self) -> bool:
        return bool(self.get_setting("demo_chats_enabled", True))

    def is_auto_reply_enabled(self) -> bool:
        return bool(self.get_setting("auto_reply_enabled", True))

    def get_fake_first_name(self) -> str:
        return str(self.get_setting("fake_first_name", "Alex"))

    def get_fake_last_name(self) -> str:
        return str(self.get_setting("fake_last_name", "Smith"))

    def get_fake_username(self) -> str:
        uname = str(self.get_setting("fake_username", "yumi_creator"))
        return uname.lstrip("@")

    def get_fake_phone(self) -> str:
        return str(self.get_setting("fake_phone", "+1 555 777-0199"))

    def get_fake_bio(self) -> str:
        return str(self.get_setting("fake_bio", "Creator of Yumigram 🚀 Modern aesthetic Telegram client."))

    def is_demo_dialog(self, dialog_id: int) -> bool:
        try:
            did = int(dialog_id)
            return (did in DEMO_PEERS) or (did in self._chat_histories)
        except Exception:
            return False

    def get_client_user_id(self) -> int:
        UserConfig = _jclass("org.telegram.messenger.UserConfig")
        if UserConfig is not None:
            try:
                account = int(UserConfig.selectedAccount)
                return int(UserConfig.getInstance(account).getClientUserId())
            except Exception:
                pass
        return 0

    # ==================== Hooks Installation ====================

    def _check_hooks_permission(self) -> bool:
        try:
            from app.exteraless.plugins import PythonBridge
            if PythonBridge is not None and self._exteraless_plugin_id:
                return bool(PythonBridge.hasPermission(self._exteraless_plugin_id, "hooks"))
        except Exception:
            pass
        return True

    def _install_hooks(self):
        if not self._check_hooks_permission():
            self.log("FakeAccountStudioPlugin: 'hooks' permission not yet granted.")
            return

        UserConfigClass = find_class("org.telegram.messenger.UserConfig")
        MessagesControllerClass = find_class("org.telegram.messenger.MessagesController")
        MessagesStorageClass = find_class("org.telegram.messenger.MessagesStorage")

        if not UserConfigClass or not MessagesControllerClass:
            self.log("FakeAccountStudioPlugin: failed to resolve key Telegram classes")
            return

        plugin = self

        # 1. UserConfig.getCurrentUser()
        class GetCurrentUserHook(MethodHook):
            def after_hooked_method(self, param):
                try:
                    if not plugin.is_fake_account_active():
                        return
                    user = param.getResult()
                    if user is not None:
                        plugin.apply_fake_identity_to_user(user)
                except Exception as e:
                    plugin.log(f"GetCurrentUserHook error: {e}")

        # 2. UserConfig.getClientPhone()
        class GetClientPhoneHook(MethodHook):
            def before_hooked_method(self, param):
                try:
                    if plugin.is_fake_account_active():
                        phone = plugin.get_fake_phone().replace("+", "").replace(" ", "").replace("-", "")
                        param.setResult(phone)
                except Exception as e:
                    plugin.log(f"GetClientPhoneHook error: {e}")

        # 3. UserConfig.isPremium() & isPremiumOrLocal()
        class IsPremiumHook(MethodHook):
            def before_hooked_method(self, param):
                try:
                    if plugin.is_fake_account_active():
                        param.setResult(plugin.is_fake_premium())
                except Exception as e:
                    plugin.log(f"IsPremiumHook error: {e}")

        # 4. MessagesController.getUser(Long)
        class GetUserHook(MethodHook):
            def after_hooked_method(self, param):
                try:
                    uid = int(param.args[0]) if len(param.args) > 0 else 0
                    if uid in plugin._demo_users:
                        param.setResult(plugin._demo_users[uid])
                        return
                    user = param.getResult()
                    if user is not None and plugin.is_fake_account_active() and user.id == plugin.get_client_user_id():
                        plugin.apply_fake_identity_to_user(user)
                except Exception as e:
                    plugin.log(f"GetUserHook error: {e}")

        # 5. MessagesController.getChat(Long)
        class GetChatHook(MethodHook):
            def after_hooked_method(self, param):
                try:
                    cid = int(param.args[0]) if len(param.args) > 0 else 0
                    if cid in plugin._demo_chats:
                        param.setResult(plugin._demo_chats[cid])
                except Exception as e:
                    plugin.log(f"GetChatHook error: {e}")

        # 6. MessagesController.isPremiumUser(TLRPC.User)
        class IsPremiumUserHook(MethodHook):
            def before_hooked_method(self, param):
                try:
                    user = param.args[0] if len(param.args) > 0 else None
                    if user is not None:
                        uid = int(getattr(user, "id", 0))
                        if plugin.is_fake_account_active() and uid == plugin.get_client_user_id():
                            param.setResult(plugin.is_fake_premium())
                        elif uid in DEMO_PEERS:
                            param.setResult(bool(DEMO_PEERS[uid].get("premium", False)))
                except Exception as e:
                    plugin.log(f"IsPremiumUserHook error: {e}")

        # 7. MessagesController.getUserFull(long)
        class GetUserFullHook(MethodHook):
            def after_hooked_method(self, param):
                try:
                    uid = int(param.args[0]) if len(param.args) > 0 else 0
                    if uid in plugin._demo_full_users:
                        param.setResult(plugin._demo_full_users[uid])
                        return
                    if plugin.is_fake_account_active() and uid == plugin.get_client_user_id():
                        user_full = param.getResult()
                        if user_full is not None:
                            user_full.about = plugin.get_fake_bio()
                except Exception as e:
                    plugin.log(f"GetUserFullHook error: {e}")

        # 8. MessagesController.getChatFull(long)
        class GetChatFullHook(MethodHook):
            def after_hooked_method(self, param):
                try:
                    cid = int(param.args[0]) if len(param.args) > 0 else 0
                    if cid in plugin._demo_full_chats:
                        param.setResult(plugin._demo_full_chats[cid])
                except Exception as e:
                    plugin.log(f"GetChatFullHook error: {e}")

        # 9. MessagesController.getDialogs(int folderId)
        class GetDialogsHook(MethodHook):
            def after_hooked_method(self, param):
                try:
                    folder_id = int(param.args[0]) if len(param.args) > 0 else 0
                    if folder_id != 0:
                        return
                    if not plugin.is_demo_chats_active() and not plugin.is_hide_real_chats():
                        return
                    real_dialogs = param.getResult()
                    param.setResult(plugin.build_dialogs_list(real_dialogs))
                except Exception as e:
                    plugin.log(f"GetDialogsHook error: {e}")

        # 10. MessagesController.loadMessages(...)
        class LoadMessagesHook(MethodHook):
            def before_hooked_method(self, param):
                try:
                    if not param.args or len(param.args) < 1:
                        return
                    dialog_id = int(param.args[0])
                    if not plugin.is_demo_dialog(dialog_id):
                        return  # Real chat: do not intercept!

                    class_guid = int(param.args[8]) if len(param.args) > 8 else 0
                    load_type = int(param.args[9]) if len(param.args) > 9 else 2
                    mode = int(param.args[11]) if len(param.args) > 11 else 0

                    if len(param.args) >= 20:
                        load_index = int(param.args[13])
                    elif len(param.args) >= 16:
                        load_index = int(param.args[14])
                    else:
                        load_index = 0

                    plugin.deliver_demo_chat_history(dialog_id, class_guid, load_index, load_type, mode)
                    param.setResult(None)
                except Exception as e:
                    plugin.log(f"LoadMessagesHook error: {e}")

        # 11. MessagesController.loadUserInfo(TLRPC.User, ...)
        class LoadUserInfoHook(MethodHook):
            def before_hooked_method(self, param):
                try:
                    user = param.args[0] if len(param.args) > 0 else None
                    if user is not None:
                        uid = int(getattr(user, "id", 0))
                        if plugin.is_demo_dialog(uid):
                            if uid in plugin._demo_full_users:
                                uf = plugin._demo_full_users[uid]
                                plugin._notify_user_info_loaded(uid, uf)
                            param.setResult(None)
                except Exception as e:
                    plugin.log(f"LoadUserInfoHook error: {e}")

        try:
            h1 = self.hook_method((UserConfigClass, "getCurrentUser"), GetCurrentUserHook(self))
            h2 = self.hook_method((UserConfigClass, "getClientPhone"), GetClientPhoneHook(self))
            h3 = self.hook_method((UserConfigClass, "isPremium"), IsPremiumHook(self))
            h4 = self.hook_method((UserConfigClass, "isPremiumOrLocal"), IsPremiumHook(self))
            h5 = self.hook_method((MessagesControllerClass, "getUser"), GetUserHook(self))
            h6 = self.hook_method((MessagesControllerClass, "getChat"), GetChatHook(self))
            h7 = self.hook_method((MessagesControllerClass, "isPremiumUser"), IsPremiumUserHook(self))
            h8 = self.hook_method((MessagesControllerClass, "getUserFull"), GetUserFullHook(self))
            h9 = self.hook_method((MessagesControllerClass, "getChatFull"), GetChatFullHook(self))
            h10 = self.hook_method((MessagesControllerClass, "getDialogs"), GetDialogsHook(self))
            h11 = self.hook_all_methods(MessagesControllerClass, "loadMessages", LoadMessagesHook(self))
            h12 = self.hook_all_methods(MessagesControllerClass, "loadUserInfo", LoadUserInfoHook(self))

            for h in (h1, h2, h3, h4, h5, h6, h7, h8, h9, h10, h11, h12):
                if h is not None:
                    self._hooks.append(h)
            self.log(f"FakeAccountStudioPlugin: successfully installed {len(self._hooks)} hooks")
        except Exception as e:
            self.log(f"FakeAccountStudioPlugin: hook registration error: {e}")

    # ==================== Fake Profile Logic ====================

    def apply_fake_identity_to_user(self, user):
        """In-place modification of TLRPC.User object to spoof on-screen details."""
        if user is None:
            return

        # Backup real values once before modifying
        if self._original_user_backup is None:
            self._original_user_backup = {
                "first_name": getattr(user, "first_name", "") or "",
                "last_name": getattr(user, "last_name", "") or "",
                "username": getattr(user, "username", "") or "",
                "phone": getattr(user, "phone", "") or "",
                "premium": bool(getattr(user, "premium", False)),
                "verified": bool(getattr(user, "verified", False)),
            }

        user.first_name = self.get_fake_first_name()
        user.last_name = self.get_fake_last_name()
        user.username = self.get_fake_username()
        user.phone = self.get_fake_phone().replace("+", "").replace(" ", "").replace("-", "")
        user.premium = self.is_fake_premium()
        user.verified = self.is_fake_verified()

    def _apply_fake_identity_to_current_user(self):
        UserConfig = _jclass("org.telegram.messenger.UserConfig")
        if UserConfig is None:
            return
        try:
            account = int(UserConfig.selectedAccount)
            user_config = UserConfig.getInstance(account)
            user = user_config.getCurrentUser()
            if user is not None:
                self.apply_fake_identity_to_user(user)
        except Exception as e:
            self.log(f"_apply_fake_identity_to_current_user error: {e}")

    def _restore_real_user(self):
        """Restore backed-up profile fields."""
        if not self._original_user_backup:
            return
        UserConfig = _jclass("org.telegram.messenger.UserConfig")
        if UserConfig is None:
            return
        try:
            account = int(UserConfig.selectedAccount)
            user_config = UserConfig.getInstance(account)
            user = user_config.getCurrentUser()
            if user is not None:
                user.first_name = self._original_user_backup["first_name"]
                user.last_name = self._original_user_backup["last_name"]
                user.username = self._original_user_backup["username"]
                user.phone = self._original_user_backup["phone"]
                user.premium = self._original_user_backup["premium"]
                user.verified = self._original_user_backup["verified"]
                self._original_user_backup = None
        except Exception as e:
            self.log(f"_restore_real_user error: {e}")

    # ==================== Demo Dialogs & Messages ====================

    def build_dialogs_list(self, real_dialogs) -> Any:
        """Construct the list of dialogs shown in DialogsActivity."""
        ArrayList = _jclass("java.util.ArrayList")
        if ArrayList is None:
            return real_dialogs

        result = ArrayList()

        # 1. Add demo chats at the top
        if self.is_demo_chats_active():
            demo_list = self._ensure_demo_dialogs()
            if demo_list is not None:
                for i in range(demo_list.size()):
                    result.add(demo_list.get(i))

        # 2. Add real chats (keep intact unless Anti-Leak mode is explicitly enabled)
        if not self.is_hide_real_chats() and real_dialogs is not None:
            for i in range(real_dialogs.size()):
                d = real_dialogs.get(i)
                if d is not None and not self.is_demo_dialog(int(d.id)):
                    result.add(d)

        return result

    def _ensure_demo_dialogs(self) -> Any:
        """Create Java TLRPC.TL_dialog and message structures for demo peers."""
        TLRPC = _jclass("org.telegram.tgnet.TLRPC")
        ArrayList = _jclass("java.util.ArrayList")
        UserConfig = _jclass("org.telegram.messenger.UserConfig")
        AccountInstance = _jclass("org.telegram.messenger.AccountInstance")

        if TLRPC is None or ArrayList is None or UserConfig is None or AccountInstance is None:
            return None

        account = int(UserConfig.selectedAccount)
        account_instance = AccountInstance.getInstance(account)
        mc = account_instance.getMessagesController()

        dialogs_list = ArrayList()

        for peer_id, info in DEMO_PEERS.items():
            # Peer structures
            if peer_id > 0:
                peer = TLRPC.TL_peerUser()
                peer.user_id = int(peer_id)

                user = TLRPC.TL_user()
                user.id = int(peer_id)
                user.first_name = info.get("first_name", "")
                user.last_name = info.get("last_name", "")
                user.username = info.get("username", "")
                user.phone = info.get("phone", "")
                user.verified = bool(info.get("verified", False))
                user.premium = bool(info.get("premium", False))
                user.status = TLRPC.TL_userStatusRecently()

                user_full = TLRPC.TL_userFull()
                user_full.id = int(peer_id)
                user_full.about = info.get("bio", "Verified User")
                user_full.phone_calls_available = False
                user_full.video_calls_available = False
                user_full.flags = 0

                self._demo_users[peer_id] = user
                self._demo_full_users[peer_id] = user_full
                mc.putUser(user, False)
                try:
                    mc.fullUsers.put(int(peer_id), user_full)
                except Exception:
                    pass
            else:
                peer = TLRPC.TL_peerChannel()
                peer.channel_id = int(-peer_id)

                chat = TLRPC.TL_channel()
                chat.id = int(-peer_id)
                chat.title = info.get("title", "Demo Channel")
                chat.username = info.get("username", "")
                chat.verified = bool(info.get("verified", False))
                chat.broadcast = (info.get("type") == "channel")

                chat_full = TLRPC.TL_channelFull()
                chat_full.id = int(-peer_id)
                chat_full.about = info.get("bio", "Official Channel")
                chat_full.participants_count = 14500

                self._demo_chats[-peer_id] = chat
                self._demo_full_chats[-peer_id] = chat_full
                mc.putChat(chat, False)
                try:
                    mc.fullChats.put(int(-peer_id), chat_full)
                except Exception:
                    pass

            # Top message in chat list
            history = self._chat_histories.get(peer_id, [])
            last_item = history[-1] if history else None
            top_msg_id = last_item["id"] if last_item else 100
            msg_date = last_item["date"] if last_item else int(time.time())
            msg_text = last_item["text"] if last_item else info.get("initial_message", "")
            msg_out = last_item["out"] if last_item else False

            tl_msg = TLRPC.TL_message()
            tl_msg.id = int(top_msg_id)
            tl_msg.date = int(msg_date)
            tl_msg.message = str(msg_text)
            tl_msg.dialog_id = int(peer_id)
            tl_msg.peer_id = peer
            tl_msg.out = bool(msg_out)
            tl_msg.unread = False

            from_peer = TLRPC.TL_peerUser()
            from_peer.user_id = self.get_client_user_id() if msg_out else int(abs(peer_id))
            tl_msg.from_id = from_peer

            MessageObject = _jclass("org.telegram.messenger.MessageObject")
            msg_obj = MessageObject(account, tl_msg, False, False)
            msg_list = ArrayList()
            msg_list.add(msg_obj)

            mc.dialogMessage.put(int(peer_id), msg_list)
            try:
                mc.dialogMessagesByIds.put(int(top_msg_id), msg_obj)
            except Exception:
                pass

            # Dialog item
            dialog = TLRPC.TL_dialog()
            dialog.id = int(peer_id)
            dialog.peer = peer
            dialog.top_message = int(top_msg_id)
            dialog.last_message_date = int(msg_date)
            dialog.unread_count = int(info.get("unread_count", 0))
            dialog.pinned = bool(info.get("pinned", False))
            dialog.flags = 0

            mc.dialogs_dict.put(int(peer_id), dialog)
            dialogs_list.add(dialog)

        return dialogs_list

    def deliver_demo_chat_history(self, dialog_id: int, class_guid: int, load_index: int = 0, load_type: int = 2, mode: int = 0):
        """
        Deliver mock message history to ChatActivity with matching classGuid and loadIndex.
        Completely eliminates infinite chat loading spinner.
        """
        TLRPC = _jclass("org.telegram.tgnet.TLRPC")
        ArrayList = _jclass("java.util.ArrayList")
        MessageObject = _jclass("org.telegram.messenger.MessageObject")
        UserConfig = _jclass("org.telegram.messenger.UserConfig")
        NotificationCenter = _jclass("org.telegram.messenger.NotificationCenter")
        AndroidUtilities = _jclass("org.telegram.messenger.AndroidUtilities")
        Long = _jclass("java.lang.Long")
        Integer = _jclass("java.lang.Integer")
        Boolean = _jclass("java.lang.Boolean")

        if TLRPC is None or ArrayList is None or MessageObject is None or UserConfig is None or NotificationCenter is None:
            return

        account = int(UserConfig.selectedAccount)
        objects = ArrayList()

        # If mode == 0 (normal messages): populate history descending (newest first)
        if mode == 0:
            history = list(self._chat_histories.get(dialog_id, []))
            history.sort(key=lambda m: m["id"], reverse=True)

            for item in history:
                tl_msg = TLRPC.TL_message()
                tl_msg.id = int(item["id"])
                tl_msg.date = int(item["date"])
                tl_msg.message = str(item["text"])
                tl_msg.dialog_id = int(dialog_id)
                tl_msg.out = bool(item["out"])
                tl_msg.unread = False

                if dialog_id > 0:
                    p = TLRPC.TL_peerUser()
                    p.user_id = int(dialog_id)
                else:
                    p = TLRPC.TL_peerChannel()
                    p.channel_id = int(-dialog_id)
                tl_msg.peer_id = p

                from_p = TLRPC.TL_peerUser()
                from_p.user_id = self.get_client_user_id() if item["out"] else int(abs(dialog_id))
                tl_msg.from_id = from_p

                msg_obj = MessageObject(account, tl_msg, False, False)
                objects.add(msg_obj)

        def _post_notification():
            try:
                nc = NotificationCenter.getInstance(account)
                # Matches ChatActivity didReceivedNotification_messagesDidLoad (args 0..14)
                nc.postNotificationName(
                    NotificationCenter.messagesDidLoad,
                    Long(int(dialog_id)),
                    Integer(objects.size()),
                    objects,
                    Boolean(True),           # isCache
                    Integer(0),              # first_unread_final
                    Integer(0),              # last_message_id
                    Integer(0),              # unread_count
                    Integer(0),              # last_date
                    Integer(int(load_type)), # load_type
                    Boolean(True),           # isEnd (no more pages on server)
                    Integer(int(class_guid)),# classGuid
                    Integer(int(load_index)),# loadIndex (matches waitingForLoad!)
                    Integer(0),              # max_id
                    Integer(0),              # mentionsCount
                    Integer(int(mode))       # mode
                )
            except Exception as e:
                self.log(f"deliver_demo_chat_history postNotification error: {e}")

        if AndroidUtilities is not None:
            try:
                AndroidUtilities.runOnUIThread(R(_post_notification))
            except Exception:
                run_on_ui_thread(_post_notification)
        else:
            run_on_ui_thread(_post_notification)

    def _notify_user_info_loaded(self, uid: int, user_full):
        NotificationCenter = _jclass("org.telegram.messenger.NotificationCenter")
        UserConfig = _jclass("org.telegram.messenger.UserConfig")
        Long = _jclass("java.lang.Long")
        if NotificationCenter is None or UserConfig is None:
            return
        def _notify():
            try:
                account = int(UserConfig.selectedAccount)
                nc = NotificationCenter.getInstance(account)
                nc.postNotificationName(NotificationCenter.userInfoDidLoad, Long(int(uid)), user_full)
            except Exception as e:
                self.log(f"_notify_user_info_loaded error: {e}")
        run_on_ui_thread(_notify)

    # ==================== Outgoing Message Interception & Auto-Reply ====================

    def on_send_message_hook(self, account, params):
        """Intercept messages sent to demo chats: cancel network request and simulate reply."""
        try:
            peer_id = getattr(params, "peer", 0)
            if self.is_demo_dialog(peer_id):
                text = str(getattr(params, "message", "") or "")
                self._handle_demo_sent_message(account, int(peer_id), text)
                return HookResult(strategy=HookStrategy.CANCEL)
        except Exception as e:
            self.log(f"on_send_message_hook error: {e}")
        return HookResult(strategy=HookStrategy.DEFAULT)

    def _handle_demo_sent_message(self, account: int, peer_id: int, text: str):
        """Append user message to mock history and schedule auto-reply."""
        msg_id = self._generate_msg_id()
        current_time = int(time.time())

        if peer_id not in self._chat_histories:
            self._chat_histories[peer_id] = []

        self._chat_histories[peer_id].append({
            "id": msg_id,
            "peer_id": peer_id,
            "text": text,
            "out": True,
            "date": current_time,
        })

        # Update last message in chat list
        self._ensure_demo_dialogs()
        self.deliver_demo_chat_history(peer_id, 0)
        self._notify_dialogs_reload()

        # Auto reply simulation
        if self.is_auto_reply_enabled():
            self._schedule_auto_reply(account, peer_id)

    def _schedule_auto_reply(self, account: int, peer_id: int):
        AndroidUtilities = _jclass("org.telegram.messenger.AndroidUtilities")
        if AndroidUtilities is None:
            return

        def _do_reply():
            try:
                info = DEMO_PEERS.get(peer_id, {})
                replies = info.get("replies", [
                    "Looks amazing! ✨",
                    "Message received, everything is working flawlessly! 🚀"
                ])
                idx = self._reply_index.get(peer_id, 0)
                reply_text = replies[idx % len(replies)]
                self._reply_index[peer_id] = idx + 1

                reply_id = self._generate_msg_id()
                self._chat_histories[peer_id].append({
                    "id": reply_id,
                    "peer_id": peer_id,
                    "text": reply_text,
                    "out": False,
                    "date": int(time.time()),
                })

                self._ensure_demo_dialogs()
                self.deliver_demo_chat_history(peer_id, 0)
                self._notify_dialogs_reload()
            except Exception as e:
                self.log(f"_schedule_auto_reply error: {e}")

        # 1.5 seconds delay for realistic reply feel
        try:
            AndroidUtilities.runOnUIThread(R(_do_reply), 1500)
        except Exception:
            run_on_ui_thread(_do_reply)

    # ==================== Presets ====================

    def _apply_preset(self, preset_key: str):
        presets = {
            "creator": {
                "fake_first_name": "Alex",
                "fake_last_name": "Smith",
                "fake_username": "yumi_creator",
                "fake_phone": "+1 555 777-0199",
                "fake_bio": "Creator of Yumigram 🚀 Modern aesthetic Telegram client.",
                "fake_premium": True,
                "fake_verified": True,
                "name_label": "Alex Smith (Yumigram Creator)",
            },
            "durov": {
                "fake_first_name": "Pavel",
                "fake_last_name": "Durov",
                "fake_username": "durov",
                "fake_phone": "+1 555 288-7683",
                "fake_bio": "Building Telegram & Free Internet ✈️",
                "fake_premium": True,
                "fake_verified": True,
                "name_label": "Pavel Durov (@durov)",
            },
            "tech": {
                "fake_first_name": "Tech",
                "fake_last_name": "Reviewer",
                "fake_username": "tech_reviews",
                "fake_phone": "+1 555 432-1098",
                "fake_bio": "Gadgets, Tech & App Reviews 📱",
                "fake_premium": True,
                "fake_verified": False,
                "name_label": "Tech Reviewer",
            },
            "anon": {
                "fake_first_name": "Anonymous",
                "fake_last_name": "Tester",
                "fake_username": "demo_tester",
                "fake_phone": "+1 555 000-0000",
                "fake_bio": "Clean test environment for app demos.",
                "fake_premium": False,
                "fake_verified": False,
                "name_label": "Clean Anonymous (No Premium)",
            }
        }

        data = presets.get(preset_key)
        if not data:
            return

        for k, v in data.items():
            if k != "name_label":
                self.set_setting(k, v)

        self._apply_fake_identity_to_current_user()
        self._notify_ui_refresh()
        BulletinHelper.show_success(f"Applied preset '{data['name_label']}'!")

    # ==================== Custom Demo Chat Creation ====================

    def _add_custom_chat_clicked(self, view=None):
        title = str(self.get_setting("new_chat_title", "Ad Agency") or "New Contact").strip()
        msg_text = str(self.get_setting("new_chat_message", "Contract signed, launch the campaign! 🚀") or "Hello!").strip()
        is_channel = bool(self.get_setting("new_chat_is_channel", False))

        new_id = 99991000 + len(self._custom_chats)
        chat_id = -new_id if is_channel else new_id

        custom_chat = {
            "id": chat_id,
            "type": "channel" if is_channel else "user",
            "first_name": title if not is_channel else "",
            "last_name": "",
            "title": title if is_channel else "",
            "username": f"custom_{abs(chat_id)}",
            "bio": "Custom demo interlocutor for video recordings.",
            "verified": False,
            "premium": not is_channel,
            "pinned": True,
            "unread_count": 1,
            "initial_message": msg_text,
            "history": [
                ("in", msg_text),
            ],
            "replies": [
                "Thank you, received! Looks incredible.",
                "Everything is verified and ready for the recording! 🔥",
            ]
        }
        self._custom_chats[chat_id] = custom_chat
        self._save_custom_chats()
        DEMO_PEERS[chat_id] = custom_chat

        self._init_demo_data()
        self._ensure_demo_dialogs()
        self._notify_dialogs_reload()
        BulletinHelper.show_success(f"Chat '{title}' added to chat list!")

    # ==================== UI Refresh & Notifications ====================

    def _notify_ui_refresh(self):
        UserConfig = _jclass("org.telegram.messenger.UserConfig")
        NotificationCenter = _jclass("org.telegram.messenger.NotificationCenter")
        AccountInstance = _jclass("org.telegram.messenger.AccountInstance")

        if UserConfig is None or NotificationCenter is None:
            return

        try:
            account = int(UserConfig.selectedAccount)
            nc = NotificationCenter.getInstance(account)

            # Update profile info everywhere (Drawer, Settings, Headers)
            nc.postNotificationName(NotificationCenter.mainUserInfoChanged)
            nc.postNotificationName(NotificationCenter.currentUserPremiumStatusChanged)
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.premiumStatusChangedGlobal)

            # Update dialogs list
            nc.postNotificationName(NotificationCenter.dialogsNeedReload)
            nc.postNotificationName(NotificationCenter.updateInterfaces, 0xFFFFFFFF)

            # Update MessagesController premium state
            if AccountInstance is not None:
                mc = AccountInstance.getInstance(account).getMessagesController()
                mc.updatePremium(self.is_fake_premium() if self.is_fake_account_active() else False)
        except Exception as e:
            self.log(f"_notify_ui_refresh error: {e}")

    def _notify_dialogs_reload(self):
        UserConfig = _jclass("org.telegram.messenger.UserConfig")
        NotificationCenter = _jclass("org.telegram.messenger.NotificationCenter")
        if UserConfig is None or NotificationCenter is None:
            return
        try:
            account = int(UserConfig.selectedAccount)
            NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogsNeedReload)
        except Exception as e:
            self.log(f"_notify_dialogs_reload error: {e}")

    # ==================== Settings UI Callbacks ====================

    def _on_toggle_fake_account(self, enabled=True):
        if enabled:
            self._apply_fake_identity_to_current_user()
            BulletinHelper.show_success("Fake profile enabled!")
        else:
            self._restore_real_user()
            BulletinHelper.show_info("Real profile restored!")
        self._notify_ui_refresh()

    def _on_toggle_premium(self, premium=True):
        self._apply_fake_identity_to_current_user()
        self._notify_ui_refresh()
        status = "enabled" if premium else "disabled"
        BulletinHelper.show_info(f"Telegram Premium status {status}!")

    def _on_toggle_verified(self, verified=True):
        self._apply_fake_identity_to_current_user()
        self._notify_ui_refresh()
        status = "enabled" if verified else "disabled"
        BulletinHelper.show_info(f"Verified badge {status}!")

    def _on_toggle_chats(self, val=None):
        self._notify_dialogs_reload()

    def _on_field_changed(self, val=None):
        if self.is_fake_account_active():
            self._apply_fake_identity_to_current_user()
            self._notify_ui_refresh()

    def _rebuild_demo_chats(self, view=None):
        self._init_demo_data()
        self._ensure_demo_dialogs()
        self._notify_dialogs_reload()
        BulletinHelper.show_success("Demo chats refreshed!")

    def _clear_demo_chats(self, view=None):
        global DEMO_PEERS
        self._custom_chats.clear()
        self.set_setting("custom_demo_chats_json", "")
        DEMO_PEERS = dict(DEFAULT_DEMO_PEERS)
        self._chat_histories.clear()
        self._demo_users.clear()
        self._demo_chats.clear()
        self._demo_full_users.clear()
        self._demo_full_chats.clear()
        self._init_demo_data()
        self._ensure_demo_dialogs()
        self._notify_dialogs_reload()
        BulletinHelper.show_info("Demo chats reset to default!")

    def _force_refresh_ui(self, view=None):
        if self.is_fake_account_active():
            self._apply_fake_identity_to_current_user()
        self._notify_ui_refresh()
        BulletinHelper.show_success("UI refreshed successfully!")

    def _restore_real_account(self, view=None):
        self.set_setting("fake_account_enabled", False)
        self.set_setting("hide_real_chats", False)
        self.set_setting("demo_chats_enabled", False)
        self._restore_real_user()
        self._notify_ui_refresh()
        BulletinHelper.show_success("All real data and chats restored!")

    # ==================== Settings Screen Builder ====================

    def _grant_and_activate_hooks(self, view=None):
        try:
            from app.exteraless.plugins import PluginPermissions, PluginTrustLevel
            if self._exteraless_plugin_id:
                PluginTrustLevel.setLevel(self._exteraless_plugin_id, PluginTrustLevel.TRUSTED)
                PluginPermissions.grant(self._exteraless_plugin_id, "hooks")
                PluginPermissions.grant(self._exteraless_plugin_id, "messages.send")
                PluginPermissions.grant(self._exteraless_plugin_id, "messages.read")
                self._install_hooks()
                self._notify_ui_refresh()
                BulletinHelper.show_success("Hooks permission activated!")
        except Exception as e:
            self.log(f"grant hooks error: {e}")
            BulletinHelper.show_info("Enable permission in: Overflow (⋮) -> Plugin Permissions")

    def create_settings(self) -> List[Any]:
        items: List[Any] = []

        if not self._check_hooks_permission():
            items.extend([
                _settings.Header(text="⚠️ ATTENTION: HOOKS PERMISSION REQUIRED"),
                _settings.Text(
                    text="Activate 'Hooks' Permission",
                    subtext="Hooks are required to spoof profile info and mock chats. Tap here or enable in ⋮ -> Plugin Permissions",
                    accent=True,
                    red=True,
                    on_click=self._grant_and_activate_hooks
                ),
                _settings.Divider(),
            ])

        items.extend([
            _settings.Header(text="🛡️ Privacy Mode & Profile Spoofing"),
            _settings.Switch(
                key="fake_account_enabled",
                text="Spoof Active Profile",
                default=True,
                subtext="Overlays fake name, phone, @username, and bio on your active profile for filming",
                on_change=self._on_toggle_fake_account
            ),
            _settings.Switch(
                key="fake_premium",
                text="Telegram Premium Status",
                default=True,
                subtext="Displays Premium star badge, unlocks animated emoji, and removes visual restrictions",
                on_change=self._on_toggle_premium
            ),
            _settings.Switch(
                key="fake_verified",
                text="Verified Badge",
                default=True,
                subtext="Displays official blue verification checkmark next to your name",
                on_change=self._on_toggle_verified
            ),
            _settings.Divider(text="Profile Details"),
            _settings.Input(
                key="fake_first_name",
                text="First Name",
                default="Alex",
                subtext="Displayed in drawer header, settings, and chat headers",
                on_change=self._on_field_changed
            ),
            _settings.Input(
                key="fake_last_name",
                text="Last Name",
                default="Smith",
                subtext="Last name part of your profile",
                on_change=self._on_field_changed
            ),
            _settings.Input(
                key="fake_username",
                text="Username (@username)",
                default="yumi_creator",
                subtext="Public handle shown on your profile",
                on_change=self._on_field_changed
            ),
            _settings.Input(
                key="fake_phone",
                text="Phone Number",
                default="+1 555 777-0199",
                subtext="Mock phone number to hide your real SIM card",
                on_change=self._on_field_changed
            ),
            _settings.Input(
                key="fake_bio",
                text="Bio (About)",
                default="Creator of Yumigram 🚀 Modern aesthetic Telegram client.",
                subtext="Profile description text in settings",
                on_change=self._on_field_changed
            ),
            _settings.Divider(text="⚡ Quick Profile Presets"),
            _settings.Text(
                text="Preset: Alex Smith (Yumigram Creator, Premium)",
                subtext="Developer branded profile with Premium status and verified badge",
                accent=True,
                on_click=lambda view=None: self._apply_preset("creator")
            ),
            _settings.Text(
                text="Preset: Pavel Durov (@durov, Premium)",
                subtext="Official Pavel Durov profile with verified checkmark and Premium",
                accent=True,
                on_click=lambda view=None: self._apply_preset("durov")
            ),
            _settings.Text(
                text="Preset: Tech Reviewer (Gadgets & App Reviews)",
                subtext="Tech blogger profile tailored for client review videos",
                on_click=lambda view=None: self._apply_preset("tech")
            ),
            _settings.Text(
                text="Preset: Clean Anonymous (No Premium)",
                subtext="Standard clean profile without subscriptions or badges",
                on_click=lambda view=None: self._apply_preset("anon")
            ),
            _settings.Divider(text="💬 Demo Chats & Privacy (Anti-Leak)"),
            _settings.Switch(
                key="demo_chats_enabled",
                text="Enable Demo Chats",
                default=True,
                subtext="Injects realistic mock chats (Durov, Support, Updates, Dev Team) into chat list",
                on_change=self._on_toggle_chats
            ),
            _settings.Switch(
                key="hide_real_chats",
                text="Hide Real Chats (Anti-Leak Mode)",
                default=False,
                subtext="Hides personal conversations from the chat list during recording (keep OFF to show both)",
                on_change=self._on_toggle_chats
            ),
            _settings.Switch(
                key="auto_reply_enabled",
                text="Interactive Auto-Replies",
                default=True,
                subtext="Interlocutors automatically reply when you send messages in demo chats"
            ),
            _settings.Text(
                text="Refresh / Rebuild Demo Chats",
                subtext="Regenerate demo chats and latest messages now",
                accent=True,
                on_click=self._rebuild_demo_chats
            ),
            _settings.Text(
                text="Reset Demo Chats",
                subtext="Reset demo dialogs to original default peers",
                red=True,
                on_click=self._clear_demo_chats
            ),
            _settings.Divider(text="➕ Create Custom Demo Chat"),
            _settings.Input(
                key="new_chat_title",
                text="New Chat Title",
                default="Ad Agency",
                subtext="Contact name or channel title"
            ),
            _settings.Input(
                key="new_chat_message",
                text="Last Message",
                default="Contract signed, launch the promo campaign! 🚀",
                subtext="Preview message shown in the dialogs list"
            ),
            _settings.Switch(
                key="new_chat_is_channel",
                text="Create as Channel / Broadcast",
                default=False,
                subtext="Toggle ON to create a channel instead of a direct user chat"
            ),
            _settings.Text(
                text="Add This Chat to List",
                subtext="Create and insert a brand new custom chat alongside other chats",
                accent=True,
                on_click=self._add_custom_chat_clicked
            ),
            _settings.Divider(text="Controls & Reset"),
            _settings.Text(
                text="Apply Changes & Refresh UI",
                subtext="Force reload drawer, settings, and chat list",
                accent=True,
                on_click=self._force_refresh_ui
            ),
            _settings.Text(
                text="Restore Real Account & Chats",
                subtext="Restore original name, phone number, and display all real chats",
                red=True,
                on_click=self._restore_real_account
            ),
        ])
        return items
