"""
Fake Account & Privacy Demo Studio Plugin for Yumigram / Amegram
Allows creating simulated fake profiles (with toggleable Premium, verified badge, fake phone, username, bio)
and injecting realistic demo chats for video recording and testing without leaking personal data.
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
    "Студія для зйомки реклами та тестів без витоку особистих даних.\n"
    "• Фейковий профіль (ім'я, телефон, @username, біо)\n"
    "• Перемикач Telegram Premium (із зіркою та бейджами)\n"
    "• Приховування справжніх чатів (Anti-Leak) та створення реалістичних демо-чатів (Дуров, Підтримка, Анонси)\n"
    "• Симуляція чатів та інтерактивні автовідповіді"
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


# Pre-defined realistic demo interlocutors
DEMO_PEERS = {
    99990001: {
        "type": "user",
        "first_name": "Павел",
        "last_name": "Дуров",
        "username": "durov",
        "phone": "15552887683",
        "verified": True,
        "premium": True,
        "pinned": True,
        "unread_count": 1,
        "initial_message": "Yumigram действительно вышел на новый уровень плавности! 🔥",
        "history": [
            ("in", "Привет! Видел последний билд с кастомным плеером."),
            ("out", "Привет, Павел! Как тебе наша новая физика скролла и UI Studio?"),
            ("in", "Yumigram действительно вышел на новый уровень плавности! 🔥"),
        ],
        "replies": [
            "Отличная работа! Анимации и кастомизация сделаны очень плавно. Жду промо-видео! 🚀",
            "Выглядит замечательно. Главное сохранять приватность и стабильность.",
            "Здорово получилось, держите планку! 👍",
        ]
    },
    99990002: {
        "type": "user",
        "first_name": "Підтримка",
        "last_name": "Yumigram",
        "username": "yumi_support",
        "phone": "15550000001",
        "verified": True,
        "premium": True,
        "pinned": True,
        "unread_count": 0,
        "initial_message": "Вітаємо! Ваш клієнт повністю налаштований та готовий до зйомки. ✨",
        "history": [
            ("in", "Вітаємо! Ваш клієнт повністю налаштований та готовий до зйомки. ✨"),
        ],
        "replies": [
            "Дякуємо за звернення! Якщо виникнуть запитання щодо налаштування плагінів чи тем, ми на зв'язку. 🤝",
            "Усі системи клієнта працюють у штатному режимі (120 FPS). Бажаємо гарного запису!",
        ]
    },
    -99990003: {
        "type": "channel",
        "title": "Yumigram Updates & News",
        "username": "yumigram_official",
        "verified": True,
        "unread_count": 3,
        "initial_message": "🚀 Оновлення Yumigram v12.9! Новий кастомний UI, скляні віджети та плагінна екосистема.",
        "history": [
            ("in", "🚀 Оновлення Yumigram v12.9! Новий кастомний UI, скляні віджети та плагінна екосистема.\n\n• Повна ізоляція даних у режимі Demo\n• Плавний 120 FPS скрол\n• Кастомізація профілю без обмежень"),
        ],
        "replies": [
            "Нові публікації будуть доступні найближчим часом! Слідкуйте за оновленнями 📣",
        ]
    },
    99990004: {
        "type": "user",
        "first_name": "Аліса",
        "last_name": "UI/UX",
        "username": "alice_design",
        "phone": "15557890123",
        "verified": False,
        "premium": True,
        "pinned": False,
        "unread_count": 0,
        "initial_message": "Скинула нові шейдери для плеєра в Figma, глянь коли буде хвилинка 🎨",
        "history": [
            ("in", "Привіт! Скинула нові шейдери для плеєра в Figma, глянь коли буде хвилинка 🎨"),
            ("out", "Супер, зараз записую футажі для реклами, виглядає топово!"),
            ("in", "Домовилися, чекаю фінальне відео! Не забудь показати бокове меню 💜"),
        ],
        "replies": [
            "Супер, ролик вийде топовим! Скоро закінчу нові банери для релізу 💜",
            "Кольорова гама в нічній темі просто космос! ✨",
        ]
    },
    -99990005: {
        "type": "chat",
        "title": "Yumi Core Dev Team",
        "verified": False,
        "unread_count": 5,
        "initial_message": "Марк: Билд arm64 собрался без ошибок! 120 FPS на всех экранах.",
        "history": [
            ("in", "Денис: Задержка отрисовки упала до 2ms, дропов кадров нет."),
            ("in", "Марк: Билд arm64 собрался без ошибок! 120 FPS на всех экранах."),
        ],
        "replies": [
            "Денис: Принято! Прогнал тесты на утечки памяти, всё чисто.",
            "Марк: Отлично, готовим релизный тег в репозитории!",
        ]
    },
    99990006: {
        "type": "user",
        "first_name": "TON",
        "last_name": "Whale",
        "username": "ton_whale",
        "phone": "15558880099",
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


class FakeAccountStudioPlugin(BasePlugin):

    def __init__(self):
        super().__init__()
        self._hooks: List[Any] = []
        self._local_settings: Dict[str, Any] = {}
        self._original_user_backup: Optional[Dict[str, Any]] = None
        self._cached_demo_dialogs: Optional[Any] = None
        self._demo_users: Dict[int, Any] = {}
        self._demo_chats: Dict[int, Any] = {}
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
        self.log("FakeAccountStudioPlugin: завантаження плагіна конфіденційності...")
        self._init_demo_data()
        self._install_hooks()
        self.add_on_send_message_hook(priority=100)

        # Якщо фейковий акаунт увімкнений — застосувати до поточного користувача
        if self.is_fake_account_active():
            self._apply_fake_identity_to_current_user()

        self.log("FakeAccountStudioPlugin: успішно ініціалізовано")

    def on_plugin_unload(self):
        self.log("FakeAccountStudioPlugin: вивантаження та відновлення даних...")
        self.remove_hook("on_send_message_hook")
        self._restore_real_user()

        # Зняти всі Xposed хуки
        if self._hooks:
            for hook in self._hooks:
                try:
                    self.unhook_method(hook)
                except Exception as e:
                    self.log(f"FakeAccountStudioPlugin: помилка unhook: {e}")
            self._hooks.clear()

        self._cached_demo_dialogs = None
        self._notify_ui_refresh()
        self.log("FakeAccountStudioPlugin: вивантажено")

    # ==================== Data Initialization ====================

    def _init_demo_data(self):
        """Build in-memory message history and peer structures."""
        current_time = int(time.time())
        for peer_id, info in DEMO_PEERS.items():
            if peer_id not in self._chat_histories:
                history_list = []
                for idx, (direction, text) in enumerate(info["history"]):
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
        return bool(self.get_setting("hide_real_chats", True))

    def is_demo_chats_active(self) -> bool:
        return bool(self.get_setting("demo_chats_enabled", True))

    def is_auto_reply_enabled(self) -> bool:
        return bool(self.get_setting("auto_reply_enabled", True))

    def get_fake_first_name(self) -> str:
        return str(self.get_setting("fake_first_name", "Олексій"))

    def get_fake_last_name(self) -> str:
        return str(self.get_setting("fake_last_name", "Юміграм"))

    def get_fake_username(self) -> str:
        uname = str(self.get_setting("fake_username", "yumi_creator"))
        return uname.lstrip("@")

    def get_fake_phone(self) -> str:
        return str(self.get_setting("fake_phone", "+1 555 777-0199"))

    def get_fake_bio(self) -> str:
        return str(self.get_setting("fake_bio", "Розробник Yumigram 🚀 Офіційний акаунт для тестів та відео."))

    def is_demo_dialog(self, dialog_id: int) -> bool:
        return (dialog_id in DEMO_PEERS) or (dialog_id in self._chat_histories)

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

    def _install_hooks(self):
        UserConfigClass = find_class("org.telegram.messenger.UserConfig")
        MessagesControllerClass = find_class("org.telegram.messenger.MessagesController")

        if not UserConfigClass or not MessagesControllerClass:
            self.log("FakeAccountStudioPlugin: не знайдено ключові класи Telegram")
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
                    user = param.getResult()
                    if user is None:
                        uid = param.args[0]
                        if uid in plugin._demo_users:
                            param.setResult(plugin._demo_users[uid])
                        return
                    if plugin.is_fake_account_active() and user.id == plugin.get_client_user_id():
                        plugin.apply_fake_identity_to_user(user)
                except Exception as e:
                    plugin.log(f"GetUserHook error: {e}")

        # 5. MessagesController.getChat(Long)
        class GetChatHook(MethodHook):
            def after_hooked_method(self, param):
                try:
                    chat = param.getResult()
                    if chat is None:
                        cid = param.args[0]
                        if cid in plugin._demo_chats:
                            param.setResult(plugin._demo_chats[cid])
                except Exception as e:
                    plugin.log(f"GetChatHook error: {e}")

        # 6. MessagesController.isPremiumUser(TLRPC.User)
        class IsPremiumUserHook(MethodHook):
            def before_hooked_method(self, param):
                try:
                    if plugin.is_fake_account_active():
                        user = param.args[0]
                        if user is not None and user.id == plugin.get_client_user_id():
                            param.setResult(plugin.is_fake_premium())
                except Exception as e:
                    plugin.log(f"IsPremiumUserHook error: {e}")

        # 7. MessagesController.getUserFull(long)
        class GetUserFullHook(MethodHook):
            def after_hooked_method(self, param):
                try:
                    if not plugin.is_fake_account_active():
                        return
                    uid = param.args[0]
                    if uid == plugin.get_client_user_id():
                        user_full = param.getResult()
                        if user_full is not None:
                            user_full.about = plugin.get_fake_bio()
                except Exception as e:
                    plugin.log(f"GetUserFullHook error: {e}")

        # 8. MessagesController.getDialogs(int folderId)
        class GetDialogsHook(MethodHook):
            def after_hooked_method(self, param):
                try:
                    folder_id = param.args[0] if len(param.args) > 0 else 0
                    if folder_id != 0:
                        return
                    if not plugin.is_demo_chats_active() and not plugin.is_hide_real_chats():
                        return
                    real_dialogs = param.getResult()
                    param.setResult(plugin.build_dialogs_list(real_dialogs))
                except Exception as e:
                    plugin.log(f"GetDialogsHook error: {e}")

        # 9. MessagesController.loadMessages(...)
        class LoadMessagesHook(MethodHook):
            def before_hooked_method(self, param):
                try:
                    dialog_id = param.args[0]
                    if plugin.is_demo_dialog(dialog_id):
                        class_guid = param.args[8] if len(param.args) > 8 else 0
                        plugin.deliver_demo_chat_history(dialog_id, class_guid)
                        param.setResult(None)
                except Exception as e:
                    plugin.log(f"LoadMessagesHook error: {e}")

        try:
            h1 = self.hook_method((UserConfigClass, "getCurrentUser"), GetCurrentUserHook(self))
            h2 = self.hook_method((UserConfigClass, "getClientPhone"), GetClientPhoneHook(self))
            h3 = self.hook_method((UserConfigClass, "isPremium"), IsPremiumHook(self))
            h4 = self.hook_method((UserConfigClass, "isPremiumOrLocal"), IsPremiumHook(self))
            h5 = self.hook_method((MessagesControllerClass, "getUser"), GetUserHook(self))
            h6 = self.hook_method((MessagesControllerClass, "getChat"), GetChatHook(self))
            h7 = self.hook_method((MessagesControllerClass, "isPremiumUser"), IsPremiumUserHook(self))
            h8 = self.hook_method((MessagesControllerClass, "getUserFull"), GetUserFullHook(self))
            h9 = self.hook_method((MessagesControllerClass, "getDialogs"), GetDialogsHook(self))
            h10 = self.hook_all_methods(MessagesControllerClass, "loadMessages", LoadMessagesHook(self))

            for h in (h1, h2, h3, h4, h5, h6, h7, h8, h9, h10):
                if h is not None:
                    self._hooks.append(h)
            self.log(f"FakeAccountStudioPlugin: встановлено {len(self._hooks)} хуків")
        except Exception as e:
            self.log(f"FakeAccountStudioPlugin: помилка реєстрації хуків: {e}")

    # ==================== Fake Profile Logic ====================

    def apply_fake_identity_to_user(self, user):
        """In-place modification of TLRPC.User object to spoof on-screen details."""
        if user is None:
            return

        # Backup real values once
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

        # 1. Додаємо тестові чати на початок списку
        if self.is_demo_chats_active():
            demo_list = self._ensure_demo_dialogs()
            if demo_list is not None:
                for i in range(demo_list.size()):
                    result.add(demo_list.get(i))

        # 2. Якщо режим Anti-Leak вимкнено — додаємо реальні чати після тестових
        if not self.is_hide_real_chats() and real_dialogs is not None:
            for i in range(real_dialogs.size()):
                result.add(real_dialogs.get(i))

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
            # Peer
            if peer_id > 0:
                peer = TLRPC.TL_peerUser()
                peer.user_id = int(peer_id)

                user = TLRPC.TL_user()
                user.id = int(peer_id)
                user.first_name = info["first_name"]
                user.last_name = info.get("last_name", "")
                user.username = info.get("username", "")
                user.phone = info.get("phone", "")
                user.verified = bool(info.get("verified", False))
                user.premium = bool(info.get("premium", False))
                user.status = TLRPC.TL_userStatusRecently()

                self._demo_users[peer_id] = user
                mc.putUser(user, False)
            else:
                peer = TLRPC.TL_peerChannel()
                peer.channel_id = int(-peer_id)

                chat = TLRPC.TL_channel()
                chat.id = int(-peer_id)
                chat.title = info["title"]
                chat.username = info.get("username", "")
                chat.verified = bool(info.get("verified", False))
                chat.broadcast = (info["type"] == "channel")

                self._demo_chats[-peer_id] = chat
                mc.putChat(chat, False)

            # Last message
            history = self._chat_histories.get(peer_id, [])
            last_item = history[-1] if history else None
            top_msg_id = last_item["id"] if last_item else 100
            msg_date = last_item["date"] if last_item else int(time.time())
            msg_text = last_item["text"] if last_item else info["initial_message"]
            msg_out = last_item["out"] if last_item else False

            tl_msg = TLRPC.TL_message()
            tl_msg.id = int(top_msg_id)
            tl_msg.date = int(msg_date)
            tl_msg.message = str(msg_text)
            tl_msg.dialog_id = int(peer_id)
            tl_msg.peer_id = peer
            tl_msg.out = bool(msg_out)

            from_peer = TLRPC.TL_peerUser()
            from_peer.user_id = self.get_client_user_id() if msg_out else int(abs(peer_id))
            tl_msg.from_id = from_peer

            MessageObject = _jclass("org.telegram.messenger.MessageObject")
            msg_obj = MessageObject(account, tl_msg, False, False)
            msg_list = ArrayList()
            msg_list.add(msg_obj)

            mc.dialogMessage.put(int(peer_id), msg_list)

            # Dialog
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

    def deliver_demo_chat_history(self, dialog_id: int, class_guid: int):
        """Called when opening a demo chat in ChatActivity to serve mock messages."""
        TLRPC = _jclass("org.telegram.tgnet.TLRPC")
        ArrayList = _jclass("java.util.ArrayList")
        UserConfig = _jclass("org.telegram.messenger.UserConfig")
        AccountInstance = _jclass("org.telegram.messenger.AccountInstance")

        if TLRPC is None or ArrayList is None or UserConfig is None:
            return

        account = int(UserConfig.selectedAccount)
        mc = AccountInstance.getInstance(account).getMessagesController()

        res = TLRPC.TL_messages_messages()
        res.messages = ArrayList()
        res.users = ArrayList()
        res.chats = ArrayList()

        # Add interlocutor peer
        if dialog_id in self._demo_users:
            res.users.add(self._demo_users[dialog_id])
        if (-dialog_id) in self._demo_chats:
            res.chats.add(self._demo_chats[-dialog_id])

        # Self user
        client_user = UserConfig.getInstance(account).getCurrentUser()
        if client_user is not None:
            res.users.add(client_user)

        # Build message history
        history = self._chat_histories.get(dialog_id, [])
        for item in reversed(history):  # ChatActivity expects messages in descending order (newest first)
            msg = TLRPC.TL_message()
            msg.id = int(item["id"])
            msg.date = int(item["date"])
            msg.message = str(item["text"])
            msg.dialog_id = int(dialog_id)
            msg.out = bool(item["out"])

            if dialog_id > 0:
                p = TLRPC.TL_peerUser()
                p.user_id = int(dialog_id)
            else:
                p = TLRPC.TL_peerChannel()
                p.channel_id = int(-dialog_id)
            msg.peer_id = p

            from_p = TLRPC.TL_peerUser()
            from_p.user_id = self.get_client_user_id() if item["out"] else int(abs(dialog_id))
            msg.from_id = from_p

            res.messages.add(msg)

        mc.processLoadedMessages(
            res, res.messages.size(), int(dialog_id), 0, 100, 0, 0,
            False, int(class_guid), 0, 0, 0, 0, 0, False, 0, 0, 0,
            False, 0, True, False, None
        )

    # ==================== Outgoing Message Interception & Auto-Reply ====================

    def on_send_message_hook(self, account, params):
        """Intercept messages sent to demo chats: cancel network request and simulate reply."""
        try:
            peer_id = getattr(params, "peer", 0)
            if self.is_demo_dialog(peer_id):
                text = str(getattr(params, "message", "") or "")
                self._handle_demo_sent_message(account, peer_id, text)
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
                    "Чудово виглядає! ✨",
                    "Повідомлення отримано, все працює ідеально! 🚀"
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

        # 1.5 seconds delay
        try:
            AndroidUtilities.runOnUIThread(R(_do_reply), 1500)
        except Exception:
            run_on_ui_thread(_do_reply)

    # ==================== Presets ====================

    def _apply_preset(self, preset_key: str):
        presets = {
            "durov": {
                "fake_first_name": "Павел",
                "fake_last_name": "Дуров",
                "fake_username": "durov",
                "fake_phone": "+1 555 288-7683",
                "fake_bio": "Building Telegram & Free Internet ✈️",
                "fake_premium": True,
                "fake_verified": True,
                "name_label": "Павел Дуров (@durov)",
            },
            "creator": {
                "fake_first_name": "Олексій",
                "fake_last_name": "Юміграм",
                "fake_username": "yumi_creator",
                "fake_phone": "+380 99 777-01-23",
                "fake_bio": "Creator of Yumigram 🚀 Modern aesthetic Telegram client.",
                "fake_premium": True,
                "fake_verified": True,
                "name_label": "Yumigram Creator",
            },
            "tech": {
                "fake_first_name": "Влад",
                "fake_last_name": "Техноблог",
                "fake_username": "vlad_tech",
                "fake_phone": "+1 555 432-1098",
                "fake_bio": "Tech, Gadgets & App Reviews 📱",
                "fake_premium": True,
                "fake_verified": False,
                "name_label": "Tech Reviewer",
            },
            "anon": {
                "fake_first_name": "Anonymous",
                "fake_last_name": "User",
                "fake_username": "demo_tester",
                "fake_phone": "+1 555 000-0000",
                "fake_bio": "Private test environment.",
                "fake_premium": False,
                "fake_verified": False,
                "name_label": "Clean Anonymous",
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
        BulletinHelper.show_success(f"Застосовано пресет '{data['name_label']}'!")

    # ==================== Custom Demo Chat Creation ====================

    def _add_custom_chat_clicked(self, view=None):
        title = str(self.get_setting("new_chat_title", "Рекламна агенція") or "Новий контакт").strip()
        msg_text = str(self.get_setting("new_chat_message", "Контракт підписано! 🚀") or "Привіт!").strip()

        # Generate unique fake peer ID
        new_id = 99991000 + len(self._chat_histories)
        DEMO_PEERS[new_id] = {
            "type": "user",
            "first_name": title,
            "last_name": "",
            "username": f"custom_{new_id}",
            "verified": False,
            "premium": False,
            "pinned": True,
            "unread_count": 1,
            "initial_message": msg_text,
            "history": [
                ("in", msg_text),
            ],
            "replies": [
                "Дякую, все отримано!",
                "Чудово виглядає в кадрі! 🔥",
            ]
        }
        self._init_demo_data()
        self._ensure_demo_dialogs()
        self._notify_dialogs_reload()
        BulletinHelper.show_success(f"Чат '{title}' додано до списку!")

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

            # Also update MessagesController premium state
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
            BulletinHelper.show_success("Фейковий акаунт увімкнено!")
        else:
            self._restore_real_user()
            BulletinHelper.show_info("Справжній акаунт відновлено!")
        self._notify_ui_refresh()

    def _on_toggle_premium(self, premium=True):
        self._apply_fake_identity_to_current_user()
        self._notify_ui_refresh()
        status = "увімкнено" if premium else "вимкнено"
        BulletinHelper.show_info(f"Преміум-статус {status}!")

    def _on_toggle_verified(self, verified=True):
        self._apply_fake_identity_to_current_user()
        self._notify_ui_refresh()
        status = "увімкнено" if verified else "вимкнено"
        BulletinHelper.show_info(f"Галочку верифікації {status}!")

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
        BulletinHelper.show_success("Тестові чати оновлено!")

    def _clear_demo_chats(self, view=None):
        DEMO_PEERS.clear()
        self._chat_histories.clear()
        self._demo_users.clear()
        self._demo_chats.clear()
        self._notify_dialogs_reload()
        BulletinHelper.show_info("Тестові чати очищено!")

    def _force_refresh_ui(self, view=None):
        if self.is_fake_account_active():
            self._apply_fake_identity_to_current_user()
        self._notify_ui_refresh()
        BulletinHelper.show_success("Інтерфейс повністю оновлено!")

    def _restore_real_account(self, view=None):
        self.set_setting("fake_account_enabled", False)
        self.set_setting("hide_real_chats", False)
        self.set_setting("demo_chats_enabled", False)
        self._restore_real_user()
        self._notify_ui_refresh()
        BulletinHelper.show_success("Всі справжні дані та чати повернуто!")

    # ==================== Settings Screen Builder ====================

    def create_settings(self) -> List[Any]:
        return [
            _settings.Header(text="🛡️ Режим конфіденційності та фейковий акаунт"),
            _settings.Switch(
                key="fake_account_enabled",
                text="Увімкнути фейковий акаунт",
                default=True,
                subtext="Замінює справжні ім'я, телефон, @username та біо на тестові дані для зйомки",
                on_change=self._on_toggle_fake_account
            ),
            _settings.Switch(
                key="fake_premium",
                text="Telegram Premium статус",
                default=True,
                subtext="Відображає зірку Premium, розширені емодзі та знімає візуальні обмеження",
                on_change=self._on_toggle_premium
            ),
            _settings.Switch(
                key="fake_verified",
                text="Галочка верифікації (Verified)",
                default=True,
                subtext="Додає офіційну синю галочку поруч з ім'ям",
                on_change=self._on_toggle_verified
            ),
            _settings.Divider(text="Налаштування профілю"),
            _settings.Input(
                key="fake_first_name",
                text="Ім'я (First Name)",
                default="Олексій",
                subtext="Відображається в шапці меню, налаштуваннях та діалогах",
                on_change=self._on_field_changed
            ),
            _settings.Input(
                key="fake_last_name",
                text="Прізвище (Last Name)",
                default="Юміграм",
                subtext="Друга частина імені",
                on_change=self._on_field_changed
            ),
            _settings.Input(
                key="fake_username",
                text="Юзернейм (@username)",
                default="yumi_creator",
                subtext="Публічний нікнейм у профілі",
                on_change=self._on_field_changed
            ),
            _settings.Input(
                key="fake_phone",
                text="Номер телефону",
                default="+1 555 777-0199",
                subtext="Фейковий номер для приховання справжньої SIM-картки",
                on_change=self._on_field_changed
            ),
            _settings.Input(
                key="fake_bio",
                text="Про себе (Bio)",
                default="Розробник Yumigram 🚀 Офіційний акаунт для тестів та відео.",
                subtext="Текст опису профілю в налаштуваннях",
                on_change=self._on_field_changed
            ),
            _settings.Divider(text="⚡ Швидкі пресети акаунта"),
            _settings.Text(
                text="Пресет: Павел Дуров (@durov, Premium)",
                subtext="Офіційний акаунт Павла Дурова з верифікацією та Premium",
                accent=True,
                on_click=lambda view=None: self._apply_preset("durov")
            ),
            _settings.Text(
                text="Пресет: Yumigram Creator (Олексій Юміграм)",
                subtext="Брендовий акаунт розробника з Premium-статусом",
                accent=True,
                on_click=lambda view=None: self._apply_preset("creator")
            ),
            _settings.Text(
                text="Пресет: Tech Reviewer (Влад Техноблог)",
                subtext="Профіль техноблогера для оглядів додатків",
                on_click=lambda view=None: self._apply_preset("tech")
            ),
            _settings.Text(
                text="Пресет: Anonymous Tester (Чистий без Premium)",
                subtext="Стандартний акаунт без платної підписки та галочок",
                on_click=lambda view=None: self._apply_preset("anon")
            ),
            _settings.Divider(text="💬 Демо-чати та захист від витоку (Anti-Leak)"),
            _settings.Switch(
                key="hide_real_chats",
                text="Приховати справжні чати (Anti-Leak)",
                default=True,
                subtext="Повністю ховає особисті листування зі списку чатів під час відеозапису",
                on_change=self._on_toggle_chats
            ),
            _settings.Switch(
                key="demo_chats_enabled",
                text="Увімкнути тестові чати",
                default=True,
                subtext="Створює набір реалістичних чатів (Дуров, Підтримка, Анонси, Команда)",
                on_change=self._on_toggle_chats
            ),
            _settings.Switch(
                key="auto_reply_enabled",
                text="Інтерактивні автовідповіді",
                default=True,
                subtext="При відправці повідомлення в тестовий чат співрозмовник реалістично відповість"
            ),
            _settings.Text(
                text="Сформувати / оновити тестові чати",
                subtext="Згенерувати актуальні діалоги з повідомленнями прямо зараз",
                accent=True,
                on_click=self._rebuild_demo_chats
            ),
            _settings.Text(
                text="Очистити всі тестові чати",
                subtext="Видалити всі демо-діалоги",
                red=True,
                on_click=self._clear_demo_chats
            ),
            _settings.Divider(text="➕ Створити власний фейковий чат"),
            _settings.Input(
                key="new_chat_title",
                text="Назва нового чату",
                default="Рекламна агенція",
                subtext="Ім'я контакту або назва каналу"
            ),
            _settings.Input(
                key="new_chat_message",
                text="Останнє повідомлення",
                default="Контракт підписано, запускаємо промо-кампанію! 🚀",
                subtext="Текст останнього повідомлення в списку"
            ),
            _settings.Text(
                text="Додати цей чат до списку",
                subtext="Створити новий кастомний чат із вказаними даними",
                accent=True,
                on_click=self._add_custom_chat_clicked
            ),
            _settings.Divider(text="Керування та скидання"),
            _settings.Text(
                text="Застосувати зміни та оновити інтерфейс",
                subtext="Примусово оновити шторку, налаштування та список діалогів",
                accent=True,
                on_click=self._force_refresh_ui
            ),
            _settings.Text(
                text="Скинути та повернути справжній акаунт",
                subtext="Повернути оригінальне ім'я, номер телефону та реальні чати",
                red=True,
                on_click=self._restore_real_account
            ),
        ]
