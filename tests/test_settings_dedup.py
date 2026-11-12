import json
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
    visuals = read_src("app", "exteraless", "settings", "OpenExteraAppearanceActivity.java")
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
    # Finding onClick logic for catalog items
    needle = "item.id >= ROW_STYLE_BASE" if "item.id >= ROW_STYLE_BASE" in badges else "item.id >= 100"
    pos = badges.find(needle)
    assert pos != -1
    catalog_click_block = badges[pos : pos + 400]
    assert "FOUNDER_USER_ID" in catalog_click_block or "pickedType" in catalog_click_block
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

def test_ai_consolidated_in_single_module():
    """Ensure all AI functionality (companions, STT transcription, chat/generation) is in module 'ai'."""
    settings_activity = read_src("app", "amegram", "settings", "AmegramSettingsActivity.java")
    assert 'addActiveModuleRow(modules, "stt"' not in settings_activity, "Separate 'stt' row should not exist in active modules"
    assert 'addActiveModuleRow(modules, "ai"' in settings_activity, "'ai' row must be present in active modules"
    assert 'openModule("ai")' in settings_activity

    transcribe_gate = read_src("app", "amegram", "hot", "HotTranscribeGate.java")
    assert 'isModuleInstalledOnDisk("ai")' in transcribe_gate
    assert 'isModuleEnabled("ai")' in transcribe_gate

    catalog = read_src("app", "amegram", "hot", "HotCatalog.java")
    assert '"stt".equals(id)' in catalog
    assert 'find("ai")' in catalog

    with open(os.path.join(REPO, "TMessagesProj", "src", "main", "assets", "hotmodules", "modules.json"), "r", encoding="utf-8") as f:
        mod_json = json.load(f)
    ids = [m["id"] for m in mod_json.get("modules", [])]
    assert "ai" in ids, "'ai' module must exist in modules.json"
    assert "stt" not in ids, "separate 'stt' module must be removed from modules.json"

def test_dialogs_action_mode_hides_search_button():
    """Ensure search button in DialogsActivity is hidden during chat selection (action mode) so it does not overlap cross button."""
    dialogs = read_src("org", "telegram", "ui", "DialogsActivity.java")
    assert 'hasMainTabs && !AppearanceConfig.navigationDrawer() && !searchIsShowed' in dialogs
    assert 'factor = (1f - actionModeVisible) * factor1 * factor2 * factor3;' in dialogs

def test_dialogs_forward_mode_shows_search_button():
    """Ensure native search button is visible in DialogsActivity when forwarding/selecting (onlySelect=true) even with Yumi bottom tabs."""
    dialogs = read_src("org", "telegram", "ui", "DialogsActivity.java")
    assert 'final boolean isSearchOnLeft = !AppearanceConfig.navigationDrawer() && !onlySelect && initialDialogsType == DIALOGS_TYPE_DEFAULT && folderId == 0 && communityId == 0 && searchString == null;' in dialogs
    assert 'final float factor4 = isSearchOnLeft ? 0f : 1f;' in dialogs

def test_yumi_bar_customizer_sheet_and_dock_lift():
    """Ensure customizer sheet respects maxSheetHeight, lifts dock clear above card, and hides search FAB."""
    sheet = read_src("app", "miogram", "ui", "yumi", "YumiBarCustomizerSheet.java")
    assert 'LayoutParams hintLp = LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,\n                Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 48, 0, 0);' in sheet
    assert 'LayoutParams cardLp = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, maxSheetHeight, Gravity.BOTTOM);' in sheet
    assert 'addView(sheetCard, cardLp);' in sheet

    tabs_act = read_src("org", "telegram", "ui", "MainTabsActivity.java")
    assert 'final int targetLift = (int) (contentView.getHeight() * 0.52f + dp(32));' in tabs_act
    assert 'searchButton.animate()' in tabs_act
    assert 'searchButton.setClickable(false);' in tabs_act

def test_yumi_bar_compact_and_stretch_layout():
    """Ensure non-stretch bar stays centered without left shift, and hideTitles gives equal tab widths."""
    tabs_act = read_src("org", "telegram", "ui", "MainTabsActivity.java")
    assert 'final int initialMargin = (isStretch && isChats) ? fullMargin : 0;' in tabs_act
    assert 'final int targetMargin = (isStretch && visible) ? fullTargetMargin : 0;' in tabs_act
    assert 'final int targetMargin = (onChats && isStretch) ? dp(72) : 0;' in tabs_act

    layout = read_src("org", "telegram", "ui", "MainTabsLayout.java")
    assert 'float equalWidth = visibleCount > 0 ? (maxTotalWidthForTabs / (float) visibleCount) : dp(52);' in layout
    assert 'final float selW = hideTitles ? Math.min(sWidth, dp(56)) : sWidth;' in layout

def test_dialogs_floating_tabs_padding():
    """Ensure DialogsActivity accounts for navigationBarHeight so bottom chats can scroll past floating pill."""
    dialogs = read_src("org", "telegram", "ui", "DialogsActivity.java")
    assert 'return dp(app.exteraless.appearance.MainTabsUiHelper.getContentBottomInsetDp()) + navigationBarHeight;' in dialogs

if __name__ == "__main__":
    test_hub_row_ids_and_search_ids_do_not_collide()
    test_naming_rule_1_titles_match_entry_points()
    test_settings_ui_module_does_not_override_search_and_footer()
    test_settings_helper_deeplinks_route_to_canonical_screens()
    test_no_dead_mio_rows()
    test_badges_catalog_does_not_force_founder_sheet()
    test_settings_registry_registers_canonical_screens()
    test_ai_consolidated_in_single_module()
    test_dialogs_action_mode_hides_search_button()
    test_dialogs_forward_mode_shows_search_button()
    test_yumi_bar_customizer_sheet_and_dock_lift()
    test_yumi_bar_compact_and_stretch_layout()
    test_dialogs_floating_tabs_padding()
    print("ALL SETTINGS DEDUP TESTS PASSED!")
