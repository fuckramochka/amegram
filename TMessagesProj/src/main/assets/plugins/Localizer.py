"""
String Localizer Plugin for Amegram / Miogram
Allows dynamic interface string customization, override, and localization without altering the core client.
"""

from base_plugin import BasePlugin
from ui.settings import Header, Input, Switch, Text, Divider
from app.exteraless.plugins import PythonBridge
from ui.alert import show_alert
from ui.bulletin import show_bulletin
import json

__id__ = "localizer"
__name__ = "String Localizer"
__version__ = "1.0.0"
__author__ = "Amegram Team"
__description__ = "Dynamically inspect, override, export, and localize any UI string in the app."
__icon__ = "outline_translate_24"


class LocalizerPlugin(BasePlugin):

    def on_plugin_load(self):
        PythonBridge.log(self.id, "Localizer plugin loaded successfully")

    def on_plugin_unload(self):
        PythonBridge.log(self.id, "Localizer plugin unloaded")

    def create_settings(self):
        items = [
            Header(text="String Overrides"),
            Input(
                key="override_target_key",
                text="String Key (Resource ID or Locale Key)",
                default="AppName",
                subtext="e.g. AppName, Settings, Chats, Cancel"
            ),
            Input(
                key="override_custom_text",
                text="Custom String Value",
                default="Amegram",
                subtext="Text to display instead of default translation"
            ),
            Text(
                text="Apply String Override",
                subtext="Save custom translation for specified key",
                accent=True,
                on_click=self._apply_override
            ),
            Text(
                text="Reset Key Override",
                subtext="Remove override for specified key",
                on_click=self._remove_override
            ),
            Divider(text="Backup & Restore"),
            Text(
                text="Export Overrides to JSON",
                subtext="Copy all custom strings as JSON",
                on_click=self._export_json
            ),
            Text(
                text="Clear All Custom Strings",
                subtext="Revert all localized string overrides",
                red=True,
                on_click=self._clear_all
            ),
        ]
        return items

    def _apply_override(self):
        from plugin_settings import get_setting
        key = get_setting(self.id, "override_target_key", "AppName")
        val = get_setting(self.id, "override_custom_text", "")
        if key and val:
            PythonBridge.setStringOverride(str(key).strip(), str(val).strip())
            show_bulletin(text=f"Override applied for '{key}'!")
        else:
            show_alert("Error", "Please provide both key and custom text.")

    def _remove_override(self):
        from plugin_settings import get_setting
        key = get_setting(self.id, "override_target_key", "")
        if key:
            PythonBridge.removeStringOverride(str(key).strip())
            show_bulletin(text=f"Override removed for '{key}'")
        else:
            show_alert("Error", "Specify key to remove.")

    def _export_json(self):
        data = PythonBridge.exportStringOverrides()
        show_alert("Exported JSON", data or "{}")

    def _clear_all(self):
        PythonBridge.clearStringOverrides()
        show_bulletin(text="All string overrides cleared!")
