import os
import re
try:
    import pytest
except ImportError:
    pytest = None

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(REPO, "TMessagesProj", "src", "main", "java")

def read_src(*parts):
    with open(os.path.join(SRC, *parts), "r", encoding="utf-8") as f:
        return f.read()

def test_hub_row_ids_and_search_ids_do_not_collide():
    """Ensure search index IDs in AmegramSettingsActivity never collide with visible ROW_ IDs."""
    content = read_src("app", "amegram", "settings", "AmegramSettingsActivity.java")

    row_ids = {}
    for match in re.finditer(r"private static final int (ROW_\w+)\s*=\s*(\d+);", content):
        name, val = match.group(1), int(match.group(2))
        assert val not in row_ids, f"Collision: {name} and {row_ids[val]} both have id {val}"
        row_ids[val] = name

    search_ids = {}
    for match in re.finditer(r"private static final int (SEARCH_\w+)\s*=\s*(\d+);", content):
        name, val = match.group(1), int(match.group(2))
        assert val not in row_ids, f"Collision: {name} ({val}) collides with {row_ids[val]}"
        assert val not in search_ids, f"Duplicate search id: {name} and {search_ids[val]}"
        search_ids[val] = name

def test_naming_rule_1_titles_match_entry_points():
    """Naming Rule 1: Screen title == Entry point text in AmegramSettingsActivity."""
    hub = read_src("app", "amegram", "settings", "AmegramSettingsActivity.java")

    # 1. Personalization
    visuals = read_src("app", "miogram", "bridge", "ui", "MiogramVisualsActivity.java")
    assert 'MiogramLocale.get("Персоналізація"' in visuals
    assert 'MiogramLocale.get("Персоналізація"' in hub

    # 2. Chats & Media
    chats = read_src("app", "exteraless", "settings", "OpenExteraChatsActivity.java")
    assert 'MiogramLocale.get("Чати та медіа"' in chats
    assert 'MiogramLocale.get("Чати та медіа"' in hub

    # 3. Privacy & Security
    privacy = read_src("app", "miogram", "bridge", "ui", "MiogramPrivacySettingsActivity.java")
    assert 'MiogramLocale.get("Приватність і безпека"' in privacy
    assert 'MiogramLocale.get("Приватність і безпека"' in hub

    # 4. Modules management
    modules = read_src("app", "amegram", "hot", "ui", "HotModulesActivity.java")
    assert 'MiogramLocale.get("Керування модулями"' in modules
    assert 'MiogramLocale.get("Керування модулями"' in hub

    # 5. Guide
    guide = read_src("app", "amegram", "settings", "AmegramGuideActivity.java")
    assert 'MiogramLocale.get("Гід по розширеннях"' in guide
    assert 'MiogramLocale.get("Гід по розширеннях"' in hub

    # 6. Updates
    updates = read_src("app", "amegram", "settings", "AmegramUpdateSettingsActivity.java")
    assert 'MiogramLocale.get("Оновлення застосунку"' in updates
    assert 'MiogramLocale.get("Оновлення застосунку"' in hub

    # 7. Laboratory
    lab = read_src("tw", "nekomimi", "nekogram", "settings", "NekoExperimentalSettingsActivity.java")
    assert 'MiogramLocale.get("Лабораторія"' in lab
    assert 'MiogramLocale.get("Лабораторія"' in hub

def test_settings_ui_module_does_not_override_search_and_footer():
    """Ensure SettingsUiModule does not inject duplicate footer buttons (id 20, 33) or conflicting search IDs."""
    path = os.path.join(REPO, "hotmodules", "settings_ui", "src", "com", "amegram", "mods", "settings", "SettingsUiModule.java")
    with open(path, "r", encoding="utf-8") as f:
        mod = f.read()
    assert "footerPrimary" not in mod
    assert "footerSecondary" not in mod
    assert "addSearchRow" not in mod

def test_settings_helper_deeplinks_route_to_canonical_screens():
    """Ensure SettingsHelper routes deep links to the new canonical screens."""
    helper = read_src("tw", "nekomimi", "nekogram", "helpers", "SettingsHelper.java")
    # Chats deep links route to OpenExteraChatsActivity
    assert 'case "chats":' in helper
    assert 'case "appearance":' in helper
    assert 'MiogramVisualsActivity' in helper
    assert 'OpenExteraChatsActivity' in helper

def test_no_dead_mio_rows():
    """Verify dead ROW_MIO_* and ROW_BETA_MODULES are completely removed."""
    hub = read_src("app", "amegram", "settings", "AmegramSettingsActivity.java")
    assert "ROW_MIO_VISUALS" not in hub
    assert "ROW_MIO_CHATS" not in hub
    assert "ROW_MIO_PRIVACY" not in hub
    assert "ROW_BETA_MODULES" not in hub
    assert "ROW_MIO_HISTORY" not in hub

def test_badges_catalog_does_not_force_founder_sheet():
    """Ensure style catalog click in AmegramBadgesActivity does not hardcode FOUNDER_USER_ID."""
    badges = read_src("app", "amegram", "settings", "AmegramBadgesActivity.java")
    # Finding onClick logic for catalog items (item.id >= 100)
    catalog_click_block = badges[badges.find("item.id >= 100") : badges.find("item.id >= 100") + 400]
    assert "FOUNDER_USER_ID" not in catalog_click_block
    assert "pickedType" in catalog_click_block

def test_settings_registry_registers_canonical_screens():
    """Ensure SettingsRegistry has icons for all primary screens."""
    registry = read_src("app", "exteraless", "settings", "utils", "SettingsRegistry.java")
    assert "AmegramSettingsActivity.class" in registry
    assert "MiogramVisualsActivity.class" in registry
    assert "MiogramPrivacySettingsActivity.class" in registry
    assert "HotModulesActivity.class" in registry
    assert "PluginsActivity.class" in registry
    assert "NekoExperimentalSettingsActivity.class" in registry

if __name__ == "__main__":
    test_hub_row_ids_and_search_ids_do_not_collide()
    test_naming_rule_1_titles_match_entry_points()
    test_settings_ui_module_does_not_override_search_and_footer()
    test_settings_helper_deeplinks_route_to_canonical_screens()
    test_no_dead_mio_rows()
    test_badges_catalog_does_not_force_founder_sheet()
    test_settings_registry_registers_canonical_screens()
    print("ALL SETTINGS DEDUP TESTS PASSED!")
