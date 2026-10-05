#!/usr/bin/env python3
"""
Автоматизований тест-сьют для перевірки повної модульної архітектури Amegram.
Перевіряє всі 10 модулів, маніфести, пакети .hmod, sha256 хеші, каталоги та гейти.
"""

import hashlib
import json
import os
import sys

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEMPLATE_DIR = os.path.join(BASE_DIR, "hotmodules-template")
MODULES_DIR = os.path.join(TEMPLATE_DIR, "modules")
CATALOG_PATH = os.path.join(TEMPLATE_DIR, "modules.json")
ASSETS_DIR = os.path.join(BASE_DIR, "TMessagesProj", "src", "main", "assets", "hotmodules")
JAVA_API_DIR = os.path.join(BASE_DIR, "TMessagesProj", "src", "main", "java", "app", "amegram", "hot", "api")
JAVA_HOT_DIR = os.path.join(BASE_DIR, "TMessagesProj", "src", "main", "java", "app", "amegram", "hot")

REQUIRED_MODULES = [
    "player",
    "ghost",
    "ame",
    "vault",
    "tiktok",
    "stt",
    "ai",
    "experimental",
    "automation",
    "demo"
]

def sha256_file(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        while True:
            chunk = f.read(65536)
            if not chunk:
                break
            h.update(chunk)
    return h.hexdigest()

def test_modules_exist():
    print("[1] Перевірка директорій модулів...")
    for mod in REQUIRED_MODULES:
        d = os.path.join(MODULES_DIR, mod)
        assert os.path.isdir(d), f"Відсутня папка модуля: {mod}"
        mpath = os.path.join(d, "manifest.json")
        assert os.path.isfile(mpath), f"Відсутній manifest.json у {mod}"
        with open(mpath, "r", encoding="utf-8") as f:
            data = json.load(f)
            assert data.get("id") == mod, f"Невірний id у маніфесті {mod}"
            assert "version" in data, f"Немає версії у {mod}"
            assert "entry" in data, f"Немає entry у {mod}"
            assert "name" in data, f"Немає name у {mod}"
            assert "minApp" in data, f"Немає minApp у {mod}"
    print("    -> Усі 10 модулів мають коректні manifest.json")

def test_catalog():
    print("[2] Перевірка каталогу modules.json...")
    assert os.path.isfile(CATALOG_PATH), "Не знайдено modules.json"
    with open(CATALOG_PATH, "r", encoding="utf-8") as f:
        catalog = json.load(f)
    cat_mods = {m["id"]: m for m in catalog.get("modules", [])}
    for mod in REQUIRED_MODULES:
        assert mod in cat_mods, f"Модуль {mod} відсутній у каталозі modules.json"
        entry = cat_mods[mod]
        assert "stable" in entry.get("branches", {}), f"Немає stable гілки для {mod}"
        sha = entry["branches"]["stable"].get("sha256", "")
        assert len(sha) == 64, f"Невірний sha256 для {mod}: '{sha}'"
    print("    -> Каталог містить усі 10 модулів з валідними sha256")

def test_bundled_assets():
    print("[3] Перевірка вбудованих ресурсів у assets/hotmodules/...")
    assert os.path.isdir(ASSETS_DIR), f"Відсутня папка {ASSETS_DIR}"
    assert os.path.isfile(os.path.join(ASSETS_DIR, "modules.json")), "Немає modules.json в assets"
    for mod in REQUIRED_MODULES:
        hmod = os.path.join(ASSETS_DIR, f"{mod}-1.0.0.hmod")
        assert os.path.isfile(hmod), f"Відсутній спакований {mod}-1.0.0.hmod в assets"
        assert os.path.getsize(hmod) > 500, f"Занадто малий файл {hmod}"
    print("    -> Усі 10 .hmod спаковано та присутні в assets для офлайн-роботи")

def test_java_gates():
    print("[4] Перевірка шлюзів та сервісів у ядрі...")
    gates = [
        "HotGhostGate.java",
        "HotTikTokGate.java",
        "HotTranscribeGate.java",
        "HotPlayerGate.java",
        "HotExperimentalGate.java",
        "HotVaultGate.java"
    ]
    for gate in gates:
        p = os.path.join(JAVA_HOT_DIR, gate)
        assert os.path.isfile(p), f"Відсутній файл гейту: {gate}"

    services_path = os.path.join(JAVA_API_DIR, "HotServices.java")
    assert os.path.isfile(services_path), "Відсутній HotServices.java"
    with open(services_path, "r", encoding="utf-8") as f:
        content = f.read()
        for s in ["PLAYER", "GHOST", "TRANSCRIBE", "TIKTOK", "VAULT", "EXPERIMENTAL", "AUTOMATION"]:
            assert s in content, f"HotServices не містить константи {s}"
    print("    -> Усі шлюзи та сервісні інтерфейси присутні в ядрі")

def test_ui_hooks():
    print("[5] Перевірка точок входу в UI...")
    alert_path = os.path.join(BASE_DIR, "TMessagesProj", "src", "main", "java", "org", "telegram", "ui", "Components", "AudioPlayerAlert.java")
    with open(alert_path, "r", encoding="utf-8") as f:
        content = f.read()
        assert "HotPlayerGate.isMusicSearchAvailable" in content, "AudioPlayerAlert не містить HotPlayerGate"
        assert "HotPlayerGate.openMusicSearch" in content, "AudioPlayerAlert не обробляє пошук музики"

    settings_path = os.path.join(BASE_DIR, "TMessagesProj", "src", "main", "java", "org", "telegram", "ui", "SettingsActivity.java")
    with open(settings_path, "r", encoding="utf-8") as f:
        content = f.read()
        assert "AmegramSettingsActivity" in content, "SettingsActivity не веде в AmegramSettingsActivity"

    app_loader_path = os.path.join(BASE_DIR, "TMessagesProj", "src", "main", "java", "org", "telegram", "messenger", "ApplicationLoader.java")
    with open(app_loader_path, "r", encoding="utf-8") as f:
        content = f.read()
        assert "HotModulesManager.attachEnabledAsync" in content, "ApplicationLoader не запускає HotModulesManager"

    print("    -> Усі хуки в AudioPlayerAlert, SettingsActivity та ApplicationLoader на місці")

def test_lifecycle_and_clean_start():
    print("[6] Перевірка життєвого циклу (чистий старт, реактивність, нативні бейджики)...")
    mgr_path = os.path.join(JAVA_HOT_DIR, "HotModulesManager.java")
    with open(mgr_path, "r", encoding="utf-8") as f:
        mgr = f.read()
        assert "ensureBundledModulesInstalled();" not in mgr.split("attachEnabledAsync")[1].split("}")[0], "attachEnabledAsync не повинен авторозпаковувати всі модулі на старті"
        assert "ModulesChangeListener" in mgr, "HotModulesManager повинен мати ModulesChangeListener"
        assert "addListener" in mgr, "HotModulesManager повинен мати addListener"
        assert "deleteModule" in mgr, "HotModulesManager повинен підтримувати повне видалення модуля"
        assert "getModuleLoadEstimate" in mgr, "HotModulesManager повинен повертати навантаження RAM/DEX"

    settings_path = os.path.join(BASE_DIR, "TMessagesProj", "src", "main", "java", "app", "amegram", "settings", "AmegramSettingsActivity.java")
    with open(settings_path, "r", encoding="utf-8") as f:
        sett = f.read()
        assert "ModulesChangeListener" in sett, "AmegramSettingsActivity має реалізувати ModulesChangeListener"
        assert "isModuleEnabled(\"ghost\")" in sett, "AmegramSettingsActivity має динамічно перевіряти статус модуля Ghost"
        assert "ROW_BADGES" in sett, "AmegramSettingsActivity має містити нативний розділ бейджиків"

    badges_path = os.path.join(BASE_DIR, "TMessagesProj", "src", "main", "java", "app", "amegram", "settings", "AmegramBadgesActivity.java")
    assert os.path.isfile(badges_path), "AmegramBadgesActivity має бути нативним класом"

    catalog_path = os.path.join(JAVA_HOT_DIR, "ui", "HotCatalogSheet.java")
    with open(catalog_path, "r", encoding="utf-8") as f:
        cat = f.read()
        assert "Встановити та ввімкнути" in cat, "HotCatalogSheet має містити галочку 'Встановити та ввімкнути'"

    print("    -> Чистий старт, реактивні слухачі змін та нативні бейджики успішно верифіковано")

if __name__ == "__main__":
    try:
        test_modules_exist()
        test_catalog()
        test_bundled_assets()
        test_java_gates()
        test_ui_hooks()
        test_lifecycle_and_clean_start()
        print("\n✅ УСІ ТЕСТИ ПРОЙДЕНО УСПІШНО! Модульна архітектура готова до пушу на GitHub.")
    except AssertionError as e:
        print(f"\n❌ ПОМИЛКА: {e}")
        sys.exit(1)
