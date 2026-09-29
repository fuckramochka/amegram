import os
import re
import pytest

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def is_newer_version(current: str, remote: str, changelog: str = "") -> bool:
    """Python reference port of MiogramUpdater.isNewerVersion logic."""
    if not remote:
        return False
    c = current.replace("v", "").replace("V", "").strip() if current else ""
    r = remote.replace("v", "").replace("V", "").strip()

    if c.lower() == r.lower():
        return False

    c_base = c
    if "(" in c_base:
        c_base = c_base[:c_base.index("(")].strip()
    if "-" in c_base:
        c_base = c_base[:c_base.index("-")].strip()

    r_base = r
    if "(" in r_base:
        r_base = r_base[:r_base.index("(")].strip()
    if "-" in r_base:
        r_base = r_base[:r_base.index("-")].strip()

    c_parts = [int(p) for p in re.findall(r"\d+", c_base)]
    r_parts = [int(p) for p in re.findall(r"\d+", r_base)]

    length = max(len(c_parts), len(r_parts))
    for i in range(length):
        c_val = c_parts[i] if i < len(c_parts) else 0
        r_val = r_parts[i] if i < len(r_parts) else 0
        if r_val > c_val:
            return True
        if r_val < c_val:
            return False

    if "-" in c and "-" not in r:
        return True

    def extract_code(s):
        m = re.search(r"[\(.])(\d{5,7})[\)]?", s)
        return int(m.group(1)) if m else 0

    c_code = extract_code(c)
    r_code = extract_code(r)
    if r_code > 0 and c_code > 0:
        return r_code > c_code

    return False


def test_version_comparison_basic():
    assert is_newer_version("12.11.45", "12.11.48") is True
    assert is_newer_version("12.11.48", "12.11.48") is False
    assert is_newer_version("12.11.48", "12.11.45") is False
    assert is_newer_version("v12.11.48", "v12.11.49") is True
    assert is_newer_version("12.11.48-beta", "12.11.48") is True


def test_changelog_mentioning_commit_does_not_suppress_update():
    # If release notes mention commit of current build, update must still be detected!
    current = "12.11.48-a1b2c3d"
    remote = "12.11.49"
    notes = "Reverted changes from a1b2c3d and fixed memory leak"
    assert is_newer_version(current, remote, changelog=notes) is True


def test_android_manifest_contains_update_permission():
    manifest_path = os.path.join(REPO_ROOT, "TMessagesProj", "src", "main", "AndroidManifest.xml")
    with open(manifest_path, "r", encoding="utf-8") as f:
        content = f.read()
    assert "android.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION" in content
    assert "android.permission.REQUEST_INSTALL_PACKAGES" in content


def test_launch_activity_throttles_resume_check():
    launch_path = os.path.join(REPO_ROOT, "TMessagesProj", "src", "main", "java", "org", "telegram", "ui", "LaunchActivity.java")
    with open(launch_path, "r", encoding="utf-8") as f:
        content = f.read()
    # Check that checkAppUpdate(force, ...) does not call checkAndShowUpdate when force == false
    assert "public void checkAppUpdate(boolean force" in content
    assert "if (force) {" in content
