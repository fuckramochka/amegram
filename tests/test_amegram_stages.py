"""Stages 2-4 guardrails: player, badges, hub, hotfix gate, app init."""
import os
import re

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA = os.path.join(REPO, "TMessagesProj", "src", "main", "java")


def _read(*parts):
    with open(os.path.join(*parts), encoding="utf-8", errors="replace") as fh:
        return fh.read()


def test_player_config_migrates_by_filename():
    src = _read(JAVA, "app", "amegram", "module", "features", "player", "AmegramPlayerConfig.java")
    assert 'LEGACY_PREFS = "miogram_player_prefs"' in src
    assert "player_migrated" in src
    assert "controls_order" in src and "player_layout_preset" in src
    imports = [l for l in src.splitlines() if l.strip().startswith("import ")]
    assert not any("app.miogram" in l or "nekogram" in l for l in imports), imports


def test_player_feature_loads_lazily():
    src = _read(JAVA, "app", "amegram", "module", "features", "player", "AmegramPlayerFeature.java")
    assert "AmegramPlayerConfig.load()" in src
    imports = [l for l in src.splitlines() if l.strip().startswith("import ")]
    assert not any("org.telegram" in l for l in imports), imports  # no upstream refs


def test_no_perframe_allocs_in_player_buttons():
    src = _read(JAVA, "app", "miogram", "bridge", "ui", "player", "MiogramAppleMusicSheet.java")
    for m in re.finditer(r"protected void onDraw\(.*?^        \}", src, re.M | re.S):
        body = m.group(0)
        assert "new Path(" not in body, body[:200]
        assert "new RectF(" not in body, body[:200]
        assert "new Paint(" not in body, body[:200]
    assert "path.rewind()" in src
    assert "playPath.rewind()" in src
    assert "heart.rewind()" in src
    assert "leftBar.set(" in src and "rightBar.set(" in src


def test_badge_drawable_caches_shader():
    src = _read(JAVA, "app", "miogram", "bridge", "badge", "MiogramArrowDrawable.java")
    assert "cachedBloom" in src and "cachedBloomKey" in src
    bloom = src[src.index("drawAtmosphericBloom"):]
    assert bloom.count("new RadialGradient(") == 1
    assert "Theme." not in src  # no static paint mutation


def test_badges_cache_offline_first():
    src = _read(JAVA, "app", "amegram", "module", "features", "badges", "AmegramBadgesCache.java")
    assert "getCached" in src and "resolve" in src
    assert "new Thread(" in src  # never main thread
    assert "supabase.co" not in src.lower()  # no hardcoded cloud in module transport
    assert "apikey" not in src.lower()
    assert "badges_endpoint" in src


def test_hub_exists_and_wires_toggles():
    src = _read(JAVA, "app", "amegram", "module", "ui", "AmegramModulesActivity.java")
    for token in ("AmegramGhostController", "player_visualizer",
                  "hotfix_code_patches", "AmegramWelcomeSheet", "BaseNekoSettingsActivity"):
        assert token in src, token
    assert "fragmentView" not in src  # no addView-hacks into upstream fragments
    assert "ContentView" not in src


def test_welcome_sheet_compact():
    src = _read(JAVA, "app", "amegram", "module", "ui", "AmegramWelcomeSheet.java")
    assert src.count("class ") == 1
    assert "guide_shown" in src
    assert "setEnabled" in src
    assert len(src.splitlines()) < 300  # compact: persona + branch + modules + start


def test_code_patches_gated_off():
    src = _read(JAVA, "app", "miogram", "bridge", "patch", "AmegramPatchManager.java")
    assert "isCodePatchAllowed()" in src
    assert '"hotfix_code_patches"' in src
    assert "continue;" in src  # bytecode skipped when gate closed


def test_module_init_wired():
    src = _read(JAVA, "org", "telegram", "messenger", "ApplicationLoader.java")
    assert "app.amegram.module.AmegramModule.init(this)" in src


def test_launch_wires_welcome_hook():
    src = _read(JAVA, "org", "telegram", "ui", "LaunchActivity.java")
    assert "AmegramHooks.onLaunchCreated(this)" in src
    hooks = _read(JAVA, "app", "amegram", "module", "AmegramHooks.java")
    assert "AmegramWelcomeSheet.wasShown()" in hooks
    assert "postDelayed" in hooks
    sheet = _read(JAVA, "app", "amegram", "module", "ui", "AmegramWelcomeSheet.java")
    assert "guide_shown" in sheet


def test_release_blockers_fixed():
    heroku = _read(JAVA, "app", "miogram", "bridge", "userbot", "MiogramHerokuManager.java")
    assert ".isMentioned()" not in heroku
    assert ".messageOwner.mentioned" in heroku
    lua = _read(JAVA, "app", "exteraless", "plugins", "LuaPluginsEngine.java")
    assert ".getFromId()" not in lua
    assert ".getSenderId()" in lua
    assert "varargsOf(new LuaValue[]{" in lua


def test_manager_registers_all_features():
    src = _read(JAVA, "app", "amegram", "module", "AmegramFeatureManager.java")
    for fqcn in ("AmegramGhostController", "AmegramPlayerFeature", "AmegramBadgesFeature",
                 "AmegramGuideFeature", "AmegramAntiBlockFeature",
                 "AmegramAmeProfileFeature", "AmegramHotfixFeature"):
        assert fqcn in src, fqcn


def test_new_controllers_have_no_legacy_imports():
    for rel in (("app", "amegram", "module", "features", "net", "AmegramAntiBlockFeature.java"),
                ("app", "amegram", "module", "features", "profile", "AmegramAmeProfileFeature.java"),
                ("app", "amegram", "module", "features", "system", "AmegramHotfixFeature.java")):
        src = _read(JAVA, *rel)
        imports = [l for l in src.splitlines() if l.strip().startswith("import ")]
        assert not any("app.miogram" in l or "nekogram" in l or "org.telegram" in l
                       for l in imports), (rel, imports)
        assert "Class.forName" in src or "AmegramConfig" in src


def test_antiblock_gated_in_own_file():
    src = _read(JAVA, "app", "miogram", "bridge", "bypass", "MiogramAntiBlockEngine.java")
    assert "isModuleEnabled()" in src
    assert '"antiblock_enabled", true' in src
    assert "disabled via Amegram Modules hub" in src


def test_ameprofile_off_is_vanilla():
    src = _read(JAVA, "app", "amegram", "bridge", "ameprofile", "AmeProfileEngine.java")
    assert "applyVanillaDefaults()" in src
    assert "customCards.clear()" in src
    assert "bannerVisible = false" in src
    assert "presenceVisible = false" in src
    assert "avatarRotation = 0f" in src
    assert '"ameprofile_enabled", true' in src


def test_hub_covers_new_modules():
    src = _read(JAVA, "app", "amegram", "module", "ui", "AmegramModulesActivity.java")
    # toggles moved into cards: all 7 builtins listed + generic toggle path
    for mod in ("ghost", "player", "badges", "antiblock", "ameprofile", "hotfix", "doublebottom"):
        assert '"%s"' % mod in src, mod
    assert "toggleCard" in src and "setEnabled" in src


def test_welcome_uses_live_defaults():
    src = _read(JAVA, "app", "amegram", "module", "ui", "AmegramWelcomeSheet.java")
    assert "f.isEnabled()" in src


def test_doublebottom_argon2_verifiers():
    src = _read(JAVA, "app", "miogram", "bridge", "vault", "MiogramDoubleBottomManager.java")
    assert "Argon2BytesGenerator" in src and "ARGON2_id" in src
    assert '"v2$argon2id$"' in src or "HASH2_PREFIX" in src
    assert "parts.length != 5" in src  # v2 has 5 $-segments
    assert "checkPasscodeAsync" in src and "PinVerdictCallback" in src
    assert "globalQueue" in src and "runOnUIThread" in src
    # timing equalization: both slots always verified
    assert "verifyPin(KEY_REAL_PIN" in src and "verifyPin(KEY_DURESS_PIN" in src
    # v1 transparent upgrade path preserved
    assert "storePin(key, pin)" in src
    # master switch gates duress
    assert '"doublebottom_enabled", true' in src
    assert "Arrays.fill" in src  # key material zeroized


def test_passcodeview_async_verdict():
    src = _read(JAVA, "org", "telegram", "ui", "Components", "PasscodeView.java")
    assert "duressChecking" in src
    assert "checkPasscodeAsync" in src
    assert "applyDuressVerdict" in src
    # old sync manager call gone from the unlock path (legacy SharedConfig/PasscodeHelper path stays)
    assert "DoubleBottomManager.checkPasscode(" not in src


def test_doublebottom_registered_and_hubbed():
    mgr = _read(JAVA, "app", "amegram", "module", "AmegramFeatureManager.java")
    assert "AmegramDoubleBottomFeature" in mgr
    hub = _read(JAVA, "app", "amegram", "module", "ui", "AmegramModulesActivity.java")
    assert "vaultRow" in hub and "MiogramDoubleBottomActivity" in hub
    feat = _read(JAVA, "app", "amegram", "module", "features", "vault",
                "AmegramDoubleBottomFeature.java")
    assert '"doublebottom"' in feat and "setDuressActive" in feat


def test_updater_throttled_not_300s():
    src = _read(JAVA, "app", "miogram", "bridge", "updater", "MiogramUpdater.java")
    assert "scheduleWithFixedDelay" not in src
    assert "12L * 60 * 60 * 1000L" in src or "CHECK_INTERVAL_MS" in src
