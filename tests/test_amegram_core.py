"""Core v2 guardrails: new core is dependency-free, hooks fixed at 10, modules signed."""
import json
import os

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA = os.path.join(REPO, "TMessagesProj", "src", "main", "java")
CORE = os.path.join(JAVA, "app", "amegram", "core")


def _read(*parts):
    with open(os.path.join(*parts), encoding="utf-8", errors="replace") as fh:
        return fh.read()


def _core(rel):
    return _read(CORE, *rel.split("/"))


def test_core_has_zero_legacy_imports():
    for root, _, files in os.walk(CORE):
        for fn in files:
            if not fn.endswith(".java"):
                continue;
            path = os.path.join(root, fn)
            with open(path, encoding="utf-8", errors="replace") as fh:
                src = fh.read()
            imports = [l for l in src.splitlines() if l.strip().startswith("import ")]
            bad = [l for l in imports
                   if "app.miogram" in l or "app.exteraless" in l or "nekogram" in l
                   or "org.telegram" in l or "com.radolyn" in l or "com.exteragram" in l]
            assert not bad, (fn, bad)


def test_hook_emit_sites_wired():
    wiring = {
        "PRE_REQUEST": "org/telegram/tgnet/ConnectionsManager.java",
        "NOTIFICATION_INCOMING": "org/telegram/messenger/NotificationsController.java",
        "CHAT_OPEN": "org/telegram/ui/ChatActivity.java",
        "PROFILE_OPEN": "org/telegram/ui/ProfileActivity.java",
        "PLAYER_OPEN": "org/telegram/ui/Components/AudioPlayerAlert.java",
        "MENU_BUILD": "org/telegram/ui/Components/ItemOptions.java",
        "SEARCH_QUERY": "org/telegram/ui/Adapters/DialogsSearchAdapter.java",
    }
    for point, rel in wiring.items():
        src = _read(JAVA, *rel.split("/"))
        assert "HookPoint.%s" % point in src, (point, rel)
        assert "catch (Throwable ignore)" in src
    # APP_CREATE + LAUNCH_CREATED wired via module entry points
    assert "AmegramCore.init" in _read(JAVA, "app", "amegram", "module",
                                       "AmegramModule.java")
    assert "onLaunchCreated(this)" in _read(JAVA, "org", "telegram", "ui",
                                            "LaunchActivity.java")


def test_hook_points_exactly_ten():
    src = _core("hooks/HookPoint.java")
    points = [l.strip().rstrip(",") for l in src.splitlines()
              if l.strip().endswith(",") or l.strip() in ("NOTIFICATION_INCOMING",)]
    names = [p for p in points if p and p[0].isupper() and "(" not in p]
    assert len(names) == 10, names
    for required in ("PRE_REQUEST", "NOTIFICATION_INCOMING", "MENU_BUILD", "APP_CREATE"):
        assert required in names, required
    reg = _core("hooks/HookRegistry.java")
    assert "CopyOnWriteArrayList" in reg
    assert "new ArrayList<>(list)" not in reg  # zero-alloc hot path


def test_module_wire_format():
    with open(os.path.join(REPO, "TMessagesProj", "src", "main", "assets",
                           "amegram_catalog.json"), encoding="utf-8") as fh:
        catalog = json.load(fh)
    assert catalog["catalogVersion"] == 1
    ids = [m["id"] for m in catalog["modules"]]
    for required in ("ghost", "player", "badges", "antiblock", "ameprofile",
                     "hotfix", "doublebottom", "guide"):
        assert required in ids, required
    for m in catalog["modules"]:
        for field in ("id", "version", "minCoreAbi", "kind", "entry", "permissions"):
            assert field in m, (m.get("id"), field)
        assert m["kind"] in ("builtin", "python", "dex", "hybrid")
    mgr = _core("modules/ModuleManager.java")
    assert "Ed25519Signer" in mgr and "verifySignature" in mgr
    assert "uninstall" in mgr and "downloadAndInstall" in mgr
    assert "getCanonicalPath" in mgr  # zip-slip guard
    man = _core("modules/ModuleManifest.java")
    assert "isKnown" in man  # unknown permission rejects module


def test_xposed_and_python_contract_preserved():
    xposed = _core("xposed/XposedCompat.java")
    assert "addXposedHook" in xposed and "removeXposedHook" in xposed
    assert "before" in xposed  # before/after semantics kept
    py = _core("engine/PythonContract.java")
    assert "PythonPluginsEngine" in py
    assert "dispatchMessageReceived" in py
    assert "getParameterTypes" in py  # lookup by arity, never exact-type getMethod
    imports = [l for l in py.splitlines() if l.strip().startswith("import ")]
    assert not any("exteraless" in l for l in imports)


def test_security_primitives():
    audit = _core("security/AuditLog.java")
    assert "CAPACITY" in audit and "snapshot" in audit
    perms = _core("security/Permissions.java")
    for p in ("network", "storage", "hook_net", "hook_ui"):
        assert '"%s"' % p in perms, p
    cfg = _core("config/CoreConfig.java")
    assert "amegram_core_prefs" in cfg


def test_r8_keeps_reflection_targets():
    with open(os.path.join(REPO, "TMessagesProj", "proguard-rules.pro"),
              encoding="utf-8", errors="replace") as fh:
        rules = fh.read()
    for target in ("tw.nekomimi.nekogram.NekoConfig",
                   "app.miogram.bridge.bypass.MiogramAntiBlockEngine",
                   "app.amegram.bridge.ameprofile.AmeProfileEngine",
                   "app.miogram.bridge.vault.MiogramDoubleBottomManager",
                   "app.exteraless.plugins.PythonPluginsEngine",
                   "app.amegram.core.**",
                   "app.amegram.module.**"):
        assert target in rules, target


def test_module_rollback_policy():
    src = _core("modules/ModuleManager.java")
    assert "MAX_AUTO_ROLLBACKS = 5" in src
    assert "ROLLBACK_NEED_MANUAL" in src
    assert "historyVersions" in src
    assert "installHistoryVersion" in src
    assert "markHealthy" in src
    assert "pushHistory" in src
    assert ".hist/" in src


def test_hub_cards_and_dynamic_sections():
    hub = _read(JAVA, "app", "amegram", "module", "ui", "AmegramModulesActivity.java")
    assert "TYPE_MODULE_CARD = 100" in hub
    assert "CardHolder" in hub and "RecyclerListView.Holder" in hub
    assert "deleteCard" in hub and "ModuleManager.uninstall" in hub
    assert "ModuleCatalogSheet" in hub and "+ " in hub
    assert "ModuleVersionsSheet" in hub and "ROLLBACK_NEED_MANUAL" in hub
    assert "PluginsActivity" in hub
    # sections exist only when enabled: rows rebuilt in refresh
    assert "ghostOn()" in hub and "= -1" in hub
    assert "updateRows();" in hub.split("private void refresh()")[1]
    assert "Switch" in hub and "setChecked" in hub
    # card style mirrors plugin catalog
    assert "setCornerRadius(AndroidUtilities.dp(16))" in hub


def test_guide_selects_branch():
    sheet = _read(JAVA, "app", "amegram", "module", "ui", "AmegramWelcomeSheet.java")
    assert "miogram_updater_prefs" in sheet
    assert '"update_channel"' in sheet
    assert '"beta"' in sheet and '"stable"' in sheet


def test_catalog_sheet_github_first():
    sheet = _read(JAVA, "app", "amegram", "module", "ui", "ModuleCatalogSheet.java")
    assert "raw.githubusercontent.com" in sheet
    assert "amegram_catalog.json" in sheet  # asset fallback
    assert "downloadAndInstall" in sheet
