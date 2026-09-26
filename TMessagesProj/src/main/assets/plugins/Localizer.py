"""
String Localizer Plugin for Amegram / Miogram
Allows dynamic interface string customization, override, and localization without altering the core client.
"""

from base_plugin import BasePlugin
from ui.settings import Header, Input, Text, Divider
from ui.bulletin import BulletinHelper
from ui.alert import AlertDialogBuilder
from java import jclass

PythonBridge = jclass("app.exteraless.plugins.PythonBridge")

__id__ = "localizer"
__name__ = "Локалізатор (Localizer)"
__version__ = "1.1.0"
__author__ = "Amegram Team"
__description__ = "Динамічний переклад, заміна та експорт будь-яких рядків інтерфейсу в реальному часі."
__icon__ = "outline_translate_24"
__app_version__ = ">=12.5.1"
__sdk_version__ = ">=1.4.0"


class LocalizerPlugin(BasePlugin):

    def on_plugin_load(self):
        PythonBridge.log("localizer", "Localizer plugin loaded successfully")

    def on_plugin_unload(self):
        PythonBridge.log("localizer", "Localizer plugin unloaded")

    def create_settings(self):
        return [
            Header(text="Переозначення рядків (String Overrides)"),
            Input(
                key="override_target_key",
                text="Ключ рядка (ID або системний ключ)",
                default="AppName",
                subtext="Наприклад: AppName, Settings, Chats, Cancel, Delete, etc."
            ),
            Input(
                key="override_custom_text",
                text="Кастомний текст",
                default="Amegram",
                subtext="Текст, який показуватиметься замість стандартного"
            ),
            Text(
                text="Застосувати заміну рядка",
                subtext="Зберегти та активувати кастомний рядок у додатку",
                accent=True,
                on_click=self._apply_override
            ),
            Text(
                text="Скинути заміну для цього ключа",
                subtext="Повернути оригінальний рядок для обраного ключа",
                on_click=self._remove_override
            ),
            Divider(text="Резервне копіювання та очищення"),
            Text(
                text="Експортувати заміни в JSON",
                subtext="Показати всі активні заміни у форматі JSON",
                on_click=self._export_json
            ),
            Text(
                text="Очистити всі кастомні рядки",
                subtext="Повернути всі оригінальні локалізації",
                red=True,
                on_click=self._clear_all
            ),
        ]

    def _apply_override(self, view=None):
        key = self.get_setting("override_target_key", "AppName")
        val = self.get_setting("override_custom_text", "")
        if key and val:
            PythonBridge.setStringOverride(str(key).strip(), str(val).strip())
            BulletinHelper.show_success(f"Замінено: '{key}' → '{val}'")
        else:
            BulletinHelper.show_error("Вкажіть і ключ, і кастомний текст!")

    def _remove_override(self, view=None):
        key = self.get_setting("override_target_key", "")
        if key:
            PythonBridge.removeStringOverride(str(key).strip())
            BulletinHelper.show_info(f"Скинуто заміну для '{key}'")
        else:
            BulletinHelper.show_error("Вкажіть ключ для скидання!")

    def _export_json(self, view=None):
        data = PythonBridge.exportStringOverrides()
        builder = AlertDialogBuilder()
        builder.set_title("Експорт рядків").set_message(str(data or "{}")).set_positive_button("OK", None).show()

    def _clear_all(self, view=None):
        PythonBridge.clearStringOverrides()
        BulletinHelper.show_success("Усі замінені рядки очищено!")
