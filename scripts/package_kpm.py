#!/usr/bin/env python3
"""Package the authentic KPM plugin from kpmplugin/ and refmap.json
into TMessagesProj/src/main/assets/plugins/kangel_plugins_manager.eaf.
"""
import os
import zipfile

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUTPUT_PATH = os.path.join(REPO_ROOT, "TMessagesProj", "src", "main", "assets", "plugins", "kangel_plugins_manager.eaf")
REFMAP_PATH = os.path.join(REPO_ROOT, "refmap.json")
KPMPLUGIN_DIR = os.path.join(REPO_ROOT, "kpmplugin")

def package():
    os.makedirs(os.path.dirname(OUTPUT_PATH), exist_ok=True)
    temp_output = OUTPUT_PATH + ".tmp"
    with zipfile.ZipFile(temp_output, "w", zipfile.ZIP_DEFLATED) as zf:
        # Write refmap.json at archive root
        zf.write(REFMAP_PATH, "refmap.json")
        for root, dirs, files in os.walk(KPMPLUGIN_DIR):
            for f in sorted(files):
                if f.endswith((".pyc", ".pyo")) or "__pycache__" in root:
                    continue
                full_path = os.path.join(root, f)
                rel_path = os.path.relpath(full_path, REPO_ROOT)
                zf.write(full_path, rel_path)
    os.replace(temp_output, OUTPUT_PATH)
    size_kb = os.path.getsize(OUTPUT_PATH) / 1024
    print(f"Packaged KPM to {OUTPUT_PATH} ({size_kb:.1f} KB)")

if __name__ == "__main__":
    package()
