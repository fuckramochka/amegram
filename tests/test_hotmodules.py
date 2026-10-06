#!/usr/bin/env python3
"""
Тест хот-модулів (тонкий клієнт).
Джерела модулів живуть в окремому репозиторії fuckramochka/yuimodules;
тут перевіряємо: каталог-фолбек і .hmod-сіди в assets (з dex і sha),
гейти ядра, точки входу в UI та менеджер без вбудованих impl.
"""

import hashlib
import json
import os
import sys
import zipfile

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS_DIR = os.path.join(BASE_DIR, "TMessagesProj", "src", "main", "assets", "hotmodules")
ASSET_CATALOG = os.path.join(ASSETS_DIR, "modules.json")
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


def test_catalog():
    print("[1] Перевірка каталогу-фолбеку в assets...")
    with open(ASSET_CATALOG, "r", encoding="utf-8") as f:
        catalog = json.load(f)
    cat_mods = {m["id"]: m for m in catalog.get("modules", [])}
    for mod in REQUIRED_MODULES:
        assert mod in cat_mods, f"Модуль {mod} відсутній у каталозі"
        stable = cat_mods[mod].get("branches", {}).get("stable", {})
        assert stable.get("version"), f"Немає stable-версії для {mod}"
        assert stable.get("url", "").startswith("https://"), f"Немає url для {mod}"
        assert len(stable.get("sha256", "")) == 64, f"Немає sha256 для {mod}"
    print("    -> Каталог містить усі 10 модулів з версіями, url і sha256")


def test_bundled_assets():
    print("[2] Перевірка .hmod-сідів в assets (мають містити classes.dex)...")
    with open(ASSET_CATALOG, "r", encoding="utf-8") as f:
        catalog = json.load(f)
    cat_mods = {m["id"]: m for m in catalog.get("modules", [])}
    for mod in REQUIRED_MODULES:
        ver = cat_mods[mod]["branches"]["stable"]["version"]
        path = os.path.join(ASSETS_DIR, f"{mod}-{ver}.hmod")
        assert os.path.isfile(path), f"Відсутній сід {mod}-{ver}.hmod в assets"
        with zipfile.ZipFile(path) as z:
            names = z.namelist()
            assert "classes.dex" in names, f"{mod}-{ver}.hmod без classes.dex (не завантажиться)"
            assert "manifest.json" in names, f"{mod}-{ver}.hmod без manifest.json"
        sha = sha256_file(path)
        assert sha == cat_mods[mod]["branches"]["stable"]["sha256"], f"sha256 {mod} не збігається з каталогом"
    print("    -> Усі .hmod з dex і коректними sha256")


def test_no_builtin_impl():
    print("[3] Перевірка тонкого клієнта (жодного вбудованого impl)...")
    mods_dir = os.path.join(BASE_DIR, "TMessagesProj", "src", "main", "java", "com", "amegram", "mods")
    assert not os.path.isdir(mods_dir), "com.amegram.mods має бути видалено з APK"
    mgr_path = os.path.join(JAVA_HOT_DIR, "HotModulesManager.java")
    with open(mgr_path, "r", encoding="utf-8") as f:
        mgr = f.read()
    assert "return new com.amegram.mods." not in mgr, "createBuiltinModule має повертати null"
    assert "ensureBundledModulesInstalled();" in mgr, "attach має розпаковувати сід на старті"
    print("    -> APK без impl-коду, завантаження тільки з диска")


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
    print("[6] Перевірка життєвого циклу...")
    mgr_path = os.path.join(JAVA_HOT_DIR, "HotModulesManager.java")
    with open(mgr_path, "r", encoding="utf-8") as f:
        mgr = f.read()
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

    print("    -> Життєвий цикл, реактивні слухачі та нативні бейджики успішно верифіковано")


if __name__ == "__main__":
    try:
        test_catalog()
        test_bundled_assets()
        test_no_builtin_impl()
        test_java_gates()
        test_ui_hooks()
        test_lifecycle_and_clean_start()
        print("\nУСІ ТЕСТИ ПРОЙДЕНО УСПІШНО! Тонкий клієнт готовий до пушу на GitHub.")
    except AssertionError as e:
        print(f"\nПОМИЛКА: {e}")
        sys.exit(1)
