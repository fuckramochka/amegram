#!/usr/bin/env python3
"""Генератор modules.json з папки .hmod файлів.

Розкладіть зібрані модулі як hmods/<id>-<version>.hmod, поруч покладіть
meta.json з описами/категоріями (див. meta.example.json), потім:
  python3 make_modules_json.py hmods/ meta.json https://BASE_URL/hmods/ > modules.json
  python3 lint_manifest.py --catalog modules.json --strict

sha256/sizeBytes рахуються автоматично. Гілки: meta.json задає branch
для кожного id (default stable); старіші версії того ж id йдуть в history.
"""
import hashlib
import json
import os
import re
import sys
import zipfile


def manifest_of(path):
    z = zipfile.ZipFile(path)
    return json.loads(z.read("manifest.json").decode("utf-8"))


def sha256_of(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def main(argv):
    if len(argv) not in (4, 5):
        print(__doc__)
        return 2
    hdir, metapath, base = argv[1], argv[2], argv[3].rstrip("/") + "/"
    out = argv[4] if len(argv) > 4 else None
    meta = json.load(open(metapath, encoding="utf-8"))
    by_id = {}
    pat = re.compile(r"^(.+)-(\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?)\.hmod$")
    for fn in sorted(os.listdir(hdir)):
        m = pat.match(fn)
        if not m:
            continue
        mid, ver = m.group(1), m.group(2)
        path = os.path.join(hdir, fn)
        try:
            mf = manifest_of(path)
        except Exception as e:
            print(f"SKIP {fn}: {e}", file=sys.stderr)
            continue
        by_id.setdefault(mid, []).append((ver, path, mf))
    modules = []
    for mid, items in sorted(by_id.items()):
        items.sort(key=lambda t: t[0])
        info = meta.get(mid, {})
        branch = info.get("branch", "stable")
        first = items[-1]
        entry = {
            "id": mid,
            "name": info.get("name", first[2].get("name", mid)),
            "description": info.get("description", ""),
            "entry": first[2].get("entry", ""),
            "author": info.get("author", "AmeGram Team"),
            "category": info.get("category", "other"),
            "featured": bool(info.get("featured", False)),
            "branches": {},
            "history": [],
        }
        for ver, path, mf in items:
            b = {
                "version": ver,
                "url": base + os.path.basename(path),
                "sha256": sha256_of(path),
                "sizeBytes": os.path.getsize(path),
                "minApp": mf.get("minApp", info.get("minApp", 0)),
                "changelog": info.get("changelog", {}).get(ver, ""),
                "permissions": info.get("permissions", []),
            }
            if ver == first[0]:
                entry["branches"][branch] = dict(b, branch=branch)
            else:
                entry["history"].append(dict(b, branch=branch))
        modules.append(entry)
    doc = json.dumps({"updated": 0, "modules": modules}, ensure_ascii=False, indent=2)
    if out:
        open(out, "w", encoding="utf-8").write(doc + "\n")
    else:
        print(doc)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
