import os
import re
import zipfile
import json

def log(msg):
    try:
        from .methods import log as _log
        _log(msg)
    except Exception:
        pass


def log_error(err, context=None):
    try:
        from .methods import log_error as _log_error
        _log_error(err, context=context)
    except Exception:
        pass


def parse_refmap(data):
    if isinstance(data, str):
        data = json.loads(data)
    return {
        "metainfo": data.get("metainfo", ""),
        "main": data.get("main", "main.py"),
        "strings": data.get("strings", ""),
        "assets": data.get("assets", ""),
        "wheels": data.get("wheels", ""),
    }


def _parse_yaml_simple(text):
    result = {}
    for line in text.splitlines():
        line = line.rstrip()
        if not line or line.startswith("#"):
            continue
        if ":" not in line:
            continue
        key, val = line.split(":", 1)
        key = key.strip()
        val = val.strip()
        if not key:
            continue
        if val.startswith('"') and val.endswith('"'):
            val = val[1:-1]
        elif val.startswith("'") and val.endswith("'"):
            val = val[1:-1]
        result[key] = val
    return result


def _load_strings_from_zip(zf, strings_path):
    strings = {}
    if not strings_path:
        return strings
    if strings_path.endswith(".json") or strings_path.endswith(".yml") or strings_path.endswith(".yaml"):
        try:
            raw = zf.read(strings_path).decode("utf-8", errors="ignore")
            if strings_path.endswith(".json"):
                strings.update(json.loads(raw))
            else:
                strings.update(_parse_yaml_simple(raw))
        except Exception:
            pass
        return strings
    prefix = strings_path.rstrip("/") + "/"
    for name in zf.namelist():
        if not name.startswith(prefix):
            continue
        rel = name[len(prefix):]
        if not rel or "/" in rel:
            continue
        lang = os.path.splitext(rel)[0]
        try:
            raw = zf.read(name).decode("utf-8", errors="ignore")
            if rel.endswith(".json"):
                strings[lang] = json.loads(raw)
            else:
                strings[lang] = _parse_yaml_simple(raw)
        except Exception:
            pass
    return strings


def _resolve_description(template, strings_map):
    if not template or "{" not in template:
        return template
    def replacer(m):
        key = m.group(1)
        for lang in ("ru", "en"):
            bucket = strings_map.get(lang, {})
            if key in bucket:
                return bucket[key]
        for bucket in strings_map.values():
            if isinstance(bucket, dict) and key in bucket:
                return bucket[key]
        return key
    return re.sub(r"\{(\w+)\}", replacer, template)


def extract_eaf_metadata(file_path):
    meta = {
        "id": None,
        "name": None,
        "description": "",
        "author": "",
        "version": "1.0.0",
        "icon": "",
        "min_version": "",
        "app_version": "",
        "sdk_version": "",
        "requirements": "",
        "elyx_version": "",
    }
    try:
        if not os.path.isfile(file_path):
            return meta
        with zipfile.ZipFile(file_path, "r") as zf:
            namelist = set(zf.namelist())
            refmap_data = None
            for rname in ("refmap.json", "refmap.yml", "refmap.yaml"):
                if rname in namelist:
                    raw = zf.read(rname).decode("utf-8", errors="ignore")
                    if rname.endswith(".json"):
                        refmap_data = json.loads(raw)
                    else:
                        refmap_data = _parse_yaml_simple(raw)
                    break

            metainfo_path = ""
            strings_path = ""
            if refmap_data:
                metainfo_path = refmap_data.get("metainfo", "")
                strings_path = refmap_data.get("strings", "")

            if not metainfo_path:
                for candidate in ("metainfo.json", "metainfo.yml", "metainfo.yaml", "meta.json", "meta.yml", "meta.yaml"):
                    if candidate in namelist:
                        metainfo_path = candidate
                        break

            if metainfo_path and metainfo_path in namelist:
                raw = zf.read(metainfo_path).decode("utf-8", errors="ignore")
                if metainfo_path.endswith(".json"):
                    md = json.loads(raw)
                else:
                    md = _parse_yaml_simple(raw)
                for k in ("id", "name", "description", "author", "version", "icon",
                           "min_version", "app_version", "sdk_version", "requirements",
                           "elyx_version", "sdk_version"):
                    if k in md and md[k]:
                        meta[k] = str(md[k])

            strings_map = {}
            if strings_path:
                strings_map = _load_strings_from_zip(zf, strings_path)
                if isinstance(strings_map, str):
                    strings_map = {}
            if not strings_map:
                for name in namelist:
                    if name.startswith("strings/") and name.endswith(".json"):
                        lang = os.path.basename(name).replace(".json", "")
                        try:
                            raw = zf.read(name).decode("utf-8", errors="ignore")
                            strings_map[lang] = json.loads(raw)
                        except Exception:
                            pass
                    elif name.startswith("locales/") and name.endswith(".json"):
                        lang = os.path.basename(name).replace(".json", "")
                        try:
                            raw = zf.read(name).decode("utf-8", errors="ignore")
                            strings_map[lang] = json.loads(raw)
                        except Exception:
                            pass

            if meta["description"] and "{" in meta["description"]:
                meta["description"] = _resolve_description(meta["description"], strings_map)
            elif not meta["description"]:
                for lang in ("ru", "en"):
                    bucket = strings_map.get(lang, {})
                    if "description" in bucket:
                        meta["description"] = bucket["description"]
                        break

    except Exception as e:
        log_error(e, context="extract_eaf_metadata")
    return meta


def parse_py_metadata(content):
    metadata = {
        "id": None,
        "name": None,
        "description": "",
        "author": "",
        "version": "1.0",
        "icon": "",
        "min_version": "",
        "app_version": "",
        "sdk_version": "",
        "requirements": "",
        "elyx_version": "",
    }
    try:
        if isinstance(content, bytes):
            text = content.decode("utf-8", errors="ignore")
        else:
            text = content
        lines = text.split("\n")
        in_multiline = False
        multiline_key = None
        multiline_value = []
        for line in lines:
            line_stripped = line.strip()
            if in_multiline:
                if '"""' in line_stripped:
                    multiline_value.append(line_stripped.split('"""')[0])
                    metadata[multiline_key] = "\n".join(multiline_value).strip()
                    in_multiline = False
                    multiline_key = None
                    multiline_value = []
                else:
                    multiline_value.append(line_stripped)
                continue

            for key in ["__id__", "__name__", "__description__", "__author__",
                        "__version__", "__icon__", "__min_version__",
                        "__app_version__", "__app_cersion__", "__requirements__",
                        "__elyx_version__"]:
                if line_stripped.startswith(key):
                    parts = line_stripped.split("=", 1)
                    if len(parts) == 2:
                        value = parts[1].strip()
                        metadata_key = key[2:-2]
                        if metadata_key == "app_cersion":
                            metadata_key = "app_version"
                        if value.startswith('"""'):
                            if value.count('"""') >= 2:
                                parsed_value = value.strip('"').strip()
                            else:
                                in_multiline = True
                                multiline_key = metadata_key
                                multiline_value = [value[3:]]
                                continue
                        else:
                            parsed_value = value.strip('"').strip("'")
                        metadata[metadata_key] = parsed_value
                    break
        if metadata.get("app_version") and not metadata.get("min_version"):
            import re as _re
            m = _re.search(r"(\d+(?:\.\d+)+)", str(metadata["app_version"]))
            if m:
                metadata["min_version"] = m.group(1)
    except Exception:
        pass
    return metadata


def extract_plugin_metadata(file_path):
    name = os.path.basename(str(file_path))
    if name.endswith(".eaf"):
        meta = extract_eaf_metadata(file_path)
        if meta.get("id"):
            return meta
        with open(file_path, "rb") as f:
            py_meta = parse_py_metadata(f.read())
        for k, v in py_meta.items():
            if v and not meta.get(k):
                meta[k] = v
        return meta
    try:
        with open(file_path, "rb") as f:
            content = f.read()
        return parse_py_metadata(content)
    except Exception:
        return {"version": "1.0", "id": None, "name": None}


def extract_plugin_metadata_bytes(data):
    import tempfile
    tmp_path = None
    try:
        with tempfile.NamedTemporaryFile(suffix=".eaf", delete=False) as tmp:
            tmp.write(data)
            tmp_path = tmp.name
        return extract_plugin_metadata(tmp_path)
    except Exception:
        return {}
    finally:
        if tmp_path:
            try:
                os.remove(tmp_path)
            except Exception:
                pass


def read_plugin_id_from_file(file_path):
    _debug_id = None
    try:
        meta = extract_eaf_metadata(file_path)
        if meta.get("id"):
            _debug_id = meta["id"]
            return meta["id"]
    except Exception:
        pass
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()
        import re as _re
        id_match = _re.search(r'__id__\s*=\s*["\']([^"\']+)["\']', content)
        if id_match:
            _debug_id = id_match.group(1)
            return id_match.group(1)
    except Exception:
        pass
    name = os.path.basename(str(file_path))
    for ext in (".eaf", ".elyx", ".zip", ".plugin"):
        if name.endswith(ext):
            result = name[:-len(ext)]
            return result
    return name


def _load_strings_from_dir(eaf_dir):
    strings_map = {}
    locales_dir = os.path.join(eaf_dir, "locales")
    if os.path.isdir(locales_dir):
        for fname in os.listdir(locales_dir):
            lang = None
            if fname.startswith("strings_"):
                lang = fname[len("strings_"):]
            elif fname.startswith("string_"):
                lang = fname[len("string_"):]
            elif fname.startswith("locale_"):
                lang = fname[len("locale_"):]
            elif fname.startswith("lang_"):
                lang = fname[len("lang_"):]
            if not lang:
                continue
            lang = os.path.splitext(lang)[0].lower()
            fpath = os.path.join(locales_dir, fname)
            try:
                with open(fpath, "r", encoding="utf-8") as f:
                    raw = f.read()
                if fname.endswith(".json"):
                    strings_map[lang] = json.loads(raw)
                else:
                    strings_map[lang] = _parse_yaml_simple(raw)
            except Exception:
                pass
    if not strings_map:
        strings_dir = os.path.join(eaf_dir, "strings")
        if os.path.isdir(strings_dir):
            for fname in os.listdir(strings_dir):
                lang = None
                if fname.startswith("strings_"):
                    lang = fname[len("strings_"):]
                elif fname.startswith("string_"):
                    lang = fname[len("string_"):]
                if not lang:
                    continue
                lang = os.path.splitext(lang)[0].lower()
                fpath = os.path.join(strings_dir, fname)
                try:
                    with open(fpath, "r", encoding="utf-8") as f:
                        raw = f.read()
                    if fname.endswith(".json"):
                        strings_map[lang] = json.loads(raw)
                    else:
                        strings_map[lang] = _parse_yaml_simple(raw)
                except Exception:
                    pass
    return strings_map


def get_localized_description_from_dir(eaf_dir, lang):
    """Get localized description from installed Elyx plugin directory.

    Args:
        eaf_dir: path to installed Elyx plugin directory
        lang: current language code (e.g. 'ru', 'en', 'uk')

    Returns:
        resolved description string or empty string
    """
    try:
        meta = None
        for candidate in ("meta.yml", "meta.json", "metainfo.yml", "metainfo.json"):
            fpath = os.path.join(eaf_dir, candidate)
            if os.path.exists(fpath):
                with open(fpath, "r", encoding="utf-8") as f:
                    raw = f.read()
                if candidate.endswith(".json"):
                    meta = json.loads(raw)
                else:
                    meta = _parse_yaml_simple(raw)
                break
        if not meta:
            return ""

        description = meta.get("description", "")
        strings_map = _load_strings_from_dir(eaf_dir)

        if description and "{" in description and strings_map:
            description = _resolve_description(description, strings_map)

        if not description and strings_map:
            for try_lang in (lang, "ru", "en"):
                bucket = strings_map.get(try_lang, {})
                if "description" in bucket:
                    description = bucket["description"]
                    break
            if not description:
                for bucket in strings_map.values():
                    if isinstance(bucket, dict) and "description" in bucket:
                        description = bucket["description"]
                        break

        if description and "{" in description and strings_map:
            description = _resolve_description(description, strings_map)

        return description or ""
    except Exception as e:
        log_error(e, context="get_localized_description_from_dir")
        return ""
