import os
import sys
import zipfile
import pytest

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PYTHON_ROOT = os.path.join(REPO_ROOT, "TMessagesProj", "src", "main", "python")
KPM_EAF = os.path.join(REPO_ROOT, "TMessagesProj", "src", "main", "assets", "plugins", "kangel_plugins_manager.eaf")
KPMPLUGIN_DIR = os.path.join(REPO_ROOT, "kpmplugin")

if PYTHON_ROOT not in sys.path:
    sys.path.insert(0, PYTHON_ROOT)


def test_kpm_eaf_exists_and_is_valid_zip():
    assert os.path.isfile(KPM_EAF), f"Expected {KPM_EAF} to exist"
    assert zipfile.is_zipfile(KPM_EAF), f"{KPM_EAF} is not a valid zip archive"

    with zipfile.ZipFile(KPM_EAF) as zf:
        namelist = zf.namelist()
        assert "refmap.json" in namelist
        assert "kpmplugin/metainfo.json" in namelist
        assert "kpmplugin/src/main.py" in namelist
        assert "kpmplugin/src/methods.py" in namelist
        assert "kpmplugin/src/views/lists/plugin_list.py" in namelist


def test_kpm_metadata_reading():
    import elyx_runtime
    raw_json = elyx_runtime.read_metadata_json(KPM_EAF)
    assert raw_json is not None
    import json
    data = json.loads(raw_json)
    assert data.get("ok") is True
    meta = data.get("meta", {})
    assert meta.get("id") == "kangel_plugins_manager"
    assert meta.get("version") == "1.6.0"
    assert "Kangel Plugins Manager" in meta.get("name")


def test_kpm_bridge_module():
    import kpm_bridge
    assert kpm_bridge.is_kpm_available() is True
    assert hasattr(kpm_bridge, "open_store")
    assert hasattr(kpm_bridge, "open_updates")
    assert hasattr(kpm_bridge, "handle_deep_link")
    assert hasattr(kpm_bridge, "register_kpm_instance")


def test_kpm_python_files_compile():
    import py_compile
    for root, dirs, files in os.walk(KPMPLUGIN_DIR):
        for f in files:
            if f.endswith(".py"):
                path = os.path.join(root, f)
                py_compile.compile(path, doraise=True)
