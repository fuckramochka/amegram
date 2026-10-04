"""Etap 1 guardrails: ghost decisions flow through Amegram Module, one chokepoint."""
import os
import re

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA = os.path.join(REPO, "TMessagesProj", "src", "main", "java")

POLICY = os.path.join(JAVA, "app", "amegram", "module", "features", "ghost", "AmegramGhostPolicy.java")
CONTROLLER = os.path.join(JAVA, "app", "amegram", "module", "features", "ghost", "AmegramGhostController.java")
INTERCEPTOR = os.path.join(REPO, "TMessagesProj", "src", "main", "java",
                           "com", "radolyn", "ayugram", "utils", "AyuGhostUtils.java")
CONN = os.path.join(JAVA, "org", "telegram", "tgnet", "ConnectionsManager.java")


def _read(p):
    with open(p, encoding="utf-8", errors="replace") as fh:
        return fh.read()


def test_policy_is_pure_jvm():
    src = _read(POLICY)
    assert "resolveSendRead" in src
    assert "shouldBlockTyping" in src
    assert "shouldForceOffline" in src
    for banned in ("import android.", "import org.telegram.", "NekoConfig", "ConnectionsManager"):
        assert banned not in src, f"policy must stay pure, found {banned}"


def test_controller_has_no_compile_time_legacy_dep():
    src = _read(CONTROLLER)
    imports = [l for l in src.splitlines() if l.strip().startswith("import ")]
    assert not any("nekogram" in l or "exteraless" in l or "miogram" in l for l in imports), imports
    assert "Class.forName(\"tw.nekomimi.nekogram.NekoConfig\")" in src  # reflection-only migration
    assert "tryMigrateFromLegacy" in src
    assert "isModuleActive" in src


def test_interceptor_delegates_to_module():
    src = _read(INTERCEPTOR)
    assert "AmegramGhostController" in src
    # Hub owns ghost: OFF kills it regardless of legacy flags.
    assert "isOn()" in src
    assert "hideRead()" in src and "hideTyping()" in src and "hideOnline()" in src
    body = src[src.index("public static InterceptResult interceptRequest"):
               src.index("private static void handleReadAfterSend")]
    assert "NekoConfig.send" not in body, body


def test_single_chokepoint_preserved():
    src = _read(CONN)
    assert src.count("GhostFilter.intercept") == 1
    assert "AyuGhostUtils.interceptRequest" not in src
    assert "AmegramHooks" not in src  # ghost stays behind the single chokepoint


def test_no_new_view_hacks():
    for path, name in ((INTERCEPTOR, "interceptor"), (CONTROLLER, "controller"), (POLICY, "policy")):
        src = _read(path)
        assert "addView" not in src, name
        assert "setVisibility" not in src, name
        assert "Theme.chat_msgTextPaint" not in src, name
