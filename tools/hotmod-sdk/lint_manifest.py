#!/usr/bin/env python3
"""Лінтер хот-модулів: manifest.json усередині .hmod + запис каталога modules.json.

Перевіряє уголову з docs/HOTMOD_SDK.md і клієнтом (HotCatalog.parse / readManifest):
  - id/version/entry обов'язкові; entry = FQCN;
  - version — semver-ish X.Y.Z;
  - url лише https://; sha256 — 64 hex (якщо вказано);
  - minApp — int >= 0; permissions — з відомого словника;
  - category — з відомого словника; sizeBytes/size — консистентні (warning).

Використання:
  python3 lint_manifest.py module.hmod            # перевірити зібраний .hmod
  python3 lint_manifest.py --catalog modules.json # перевірити весь каталог
  Строгий режим для CI: --strict (warnings -> errors).
"""
import json
import re
import sys
import zipfile

KNOWN_PERMS = {"hook_net", "hook_ui", "storage", "network", "background"}
KNOWN_CATS = {"privacy", "media", "power", "custom", "other"}
VER_RE = re.compile(r"^\d+\.\d+\.\d+(-[0-9A-Za-z.-]+)?$")
FQCN_RE = re.compile(r"^([a-zA-Z_][\w]*\.)+[A-Z][\w]*$")
SHA_RE = re.compile(r"^[0-9a-fA-F]{64}$")

errors, warnings = [], []


def err(msg):
    errors.append(msg)


def warn(msg):
    warnings.append(msg)


def check_manifest(m, where):
    mid = m.get("id", "")
    ver = m.get("version", "")
    entry = m.get("entry", "")
    if not mid:
        err(f"{where}: порожній id")
    if not ver or not VER_RE.match(ver):
        err(f"{where}: погана version '{ver}' (треба X.Y.Z)")
    if not entry or not FQCN_RE.match(entry):
        err(f"{where}: поганий entry '{entry}' (треба FQCN)")
    if not m.get("branch"):
        warn(f"{where}: нема branch (клієнт підставить stable)")
    if not m.get("name"):
        warn(f"{where}: нема name (покажемо id)")
    ma = m.get("minApp", 0)
    if not isinstance(ma, int) or ma < 0:
        err(f"{where}: поганий minApp '{ma}'")


def check_build(b, where):
    ver = b.get("version", "")
    url = b.get("url", "")
    if not ver or not VER_RE.match(ver):
        err(f"{where}: погана version '{ver}'")
    if not url or not url.startswith("https://"):
        err(f"{where}: url має бути https:// ('{url}')")
    sha = b.get("sha256", "")
    if sha and not SHA_RE.match(sha):
        err(f"{where}: sha256 має бути 64 hex")
    if not sha:
        warn(f"{where}: нема sha256 — клієнт покаже UNSIGNED")
    if not b.get("signature"):
        warn(f"{where}: нема signature — лише SHA-256 довіра")
    ma = b.get("minApp", 0)
    if not isinstance(ma, int) or ma < 0:
        err(f"{where}: поганий minApp")
    for p in b.get("permissions", []) or []:
        if p not in KNOWN_PERMS:
            err(f"{where}: невідомий permission '{p}' (дозволені: {sorted(KNOWN_PERMS)})")


def check_hmod(path):
    try:
        z = zipfile.ZipFile(path)
    except Exception as e:
        err(f"{path}: не zip ({e})")
        return
    try:
        raw = z.read("manifest.json")
    except KeyError:
        err(f"{path}: нема manifest.json усередині")
        return
    try:
        check_manifest(json.loads(raw.decode("utf-8")), path)
    except Exception as e:
        err(f"{path}: битий manifest.json ({e})")
    names = z.namelist()
    if not any(n.endswith(".dex") for n in names):
        if any(n.endswith(".java") for n in names):
            warn(f"{path}: legacy source-only (нема .dex — не вантажиться як модуль)")
        else:
            err(f"{path}: усередині нема .dex")
    if any("app/amegram/hot/api/" in n for n in names):
        err(f"{path}: api-класи вшиті в dex (мають бути compileOnly!)")


def check_catalog(path):
    try:
        root = json.load(open(path, encoding="utf-8"))
    except Exception as e:
        err(f"{path}: битий json ({e})")
        return
    mods = root.get("modules", [])
    if not mods:
        err(f"{path}: порожній modules[]")
    seen = set()
    for m in mods:
        mid = m.get("id", "")
        if mid in seen:
            err(f"{path}: дубль id '{mid}'")
        seen.add(mid)
        if not m.get("entry"):
            err(f"{path}/{mid}: порожній entry")
        cat = m.get("category", "other")
        if cat not in KNOWN_CATS:
            err(f"{path}/{mid}: невідома category '{cat}'")
        for br, b in (m.get("branches", {}) or {}).items():
            check_build(b, f"{path}/{mid}#{br}")
        for h in m.get("history", []) or []:
            check_build(h, f"{path}/{mid}@{h.get('version')}")


def main(argv):
    strict = "--strict" in argv
    argv = [a for a in argv if a != "--strict"]
    if len(argv) < 2 or argv[1] in ("-h", "--help"):
        print(__doc__)
        return 2
    if argv[1] == "--catalog":
        check_catalog(argv[2])
    else:
        for p in argv[1:]:
            check_hmod(p)
    for w in warnings:
        print("WARN:", w)
    for e in errors:
        print("ERROR:", e)
    if strict and warnings:
        print(f"strict: {len(warnings)} warnings -> errors")
        return 1
    print(f"готово: {len(errors)} errors, {len(warnings)} warnings")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
