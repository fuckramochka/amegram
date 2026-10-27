import io
import struct

def _format_size(size_bytes: int) -> str:
    if size_bytes < 1024:
        return f"{size_bytes} B"
    elif size_bytes < 1024 * 1024:
        return f"{size_bytes / 1024:.1f} KB"
    else:
        return f"{size_bytes / (1024 * 1024):.2f} MB"


def _parse_descriptor(desc: str):
    if not desc:
        return "void", []
    if "(" not in desc or ")" not in desc:
        return _parse_single_type(desc), []
    
    params_part, ret_part = desc[1:].split(")", 1)
    ret_type = _parse_single_type(ret_part)
    
    params = []
    i = 0
    while i < len(params_part):
        char = params_part[i]
        if char in "ZBCSIJFD":
            params.append(_parse_single_type(char))
            i += 1
        elif char == "[":
            dim = 1
            while i + 1 < len(params_part) and params_part[i + 1] == "[":
                dim += 1
                i += 1
            if i + 1 < len(params_part):
                if params_part[i + 1] == "L":
                    end_pos = params_part.find(";", i + 1)
                    if end_pos != -1:
                        base = _parse_single_type(params_part[i+1 : end_pos+1])
                        params.append(base + ("[]" * dim))
                        i = end_pos + 1
                    else:
                        i += 1
                else:
                    base = _parse_single_type(params_part[i + 1])
                    params.append(base + ("[]" * dim))
                    i += 2
            else:
                i += 1
        elif char == "L":
            end_pos = params_part.find(";", i)
            if end_pos != -1:
                params.append(_parse_single_type(params_part[i : end_pos+1]))
                i = end_pos + 1
            else:
                i += 1
        else:
            i += 1
            
    return ret_type, params


def _parse_single_type(t: str) -> str:
    if not t:
        return "void"
    if t == "V": return "void"
    if t == "Z": return "boolean"
    if t == "B": return "byte"
    if t == "C": return "char"
    if t == "S": return "short"
    if t == "I": return "int"
    if t == "J": return "long"
    if t == "F": return "float"
    if t == "D": return "double"
    if t.startswith("["):
        return _parse_single_type(t[1:]) + "[]"
    if t.startswith("L") and t.endswith(";"):
        clean = t[1:-1].replace("/", ".")
        if clean.startswith("java.lang.") and clean.count(".") == 2:
            return clean[10:]
        return clean
    return t.replace("/", ".")


def inspect_jvm_class(raw_bytes: bytes, filename: str) -> str:
    if len(raw_bytes) < 32 or raw_bytes[:4] != b"\xCA\xFE\xBA\xBE":
        return f"// [JVM Error] Invalid Java Class header ({filename})"
    try:
        minor, major = struct.unpack(">HH", raw_bytes[4:8])
        java_ver_map = {
            50: "Java 6", 51: "Java 7", 52: "Java 8", 53: "Java 9", 54: "Java 10",
            55: "Java 11", 56: "Java 12", 57: "Java 13", 58: "Java 14", 59: "Java 15",
            60: "Java 16", 61: "Java 17", 62: "Java 18", 63: "Java 19", 64: "Java 20",
            65: "Java 21", 66: "Java 22", 67: "Java 23", 68: "Java 24"
        }
        java_ver_str = java_ver_map.get(major, f"Class major {major}")
        
        cp_count = struct.unpack(">H", raw_bytes[8:10])[0]
        offset = 10

        cp = [None] * cp_count
        i = 1
        while i < cp_count:
            if offset >= len(raw_bytes):
                break
            tag = raw_bytes[offset]
            offset += 1
            if tag == 1:
                length = struct.unpack(">H", raw_bytes[offset:offset+2])[0]
                offset += 2
                s_bytes = raw_bytes[offset : offset + length]
                offset += length
                cp[i] = ("utf8", s_bytes.decode("utf-8", errors="replace"))
            elif tag in (3, 4):
                offset += 4
                cp[i] = ("num", None)
            elif tag in (5, 6):
                offset += 8
                cp[i] = ("num_long", None)
                i += 1
            elif tag in (7, 8):
                name_idx = struct.unpack(">H", raw_bytes[offset:offset+2])[0]
                offset += 2
                cp[i] = ("ref", name_idx)
            elif tag in (9, 10, 11):
                c_idx, nt_idx = struct.unpack(">HH", raw_bytes[offset:offset+4])
                offset += 4
                cp[i] = ("member", c_idx, nt_idx)
            elif tag == 12:
                n_idx, d_idx = struct.unpack(">HH", raw_bytes[offset:offset+4])
                offset += 4
                cp[i] = ("name_type", n_idx, d_idx)
            elif tag in (15, 16, 18, 19, 20):
                offset += 4 if tag in (18, 19, 20) else (3 if tag == 15 else 2)
                cp[i] = ("misc", None)
            else:
                break
            i += 1
            
        def get_cp_utf8(idx):
            if 0 < idx < len(cp) and cp[idx] and cp[idx][0] == "utf8":
                return cp[idx][1]
            if 0 < idx < len(cp) and cp[idx] and cp[idx][0] == "ref":
                return get_cp_utf8(cp[idx][1])
            return ""
            
        if offset + 6 > len(raw_bytes):
            return f"// [JVM Error] Corrupted class file"
            
        access_flags, this_class_idx, super_class_idx = struct.unpack(">HHH", raw_bytes[offset : offset + 6])
        offset += 6
        
        this_class_raw = get_cp_utf8(this_class_idx).replace("/", ".")
        super_class_raw = get_cp_utf8(super_class_idx).replace("/", ".")
        if super_class_raw == "java.lang.Object":
            super_class_raw = ""
            
        interfaces_count = struct.unpack(">H", raw_bytes[offset : offset + 2])[0]
        offset += 2
        interfaces = []
        for _ in range(interfaces_count):
            if offset + 2 <= len(raw_bytes):
                if_idx = struct.unpack(">H", raw_bytes[offset : offset + 2])[0]
                offset += 2
                if_name = get_cp_utf8(if_idx).replace("/", ".")
                if if_name:
                    interfaces.append(if_name)
                    
        fields_count = struct.unpack(">H", raw_bytes[offset : offset + 2])[0] if offset + 2 <= len(raw_bytes) else 0
        offset += 2
        fields = []
        for _ in range(fields_count):
            if offset + 8 > len(raw_bytes):
                break
            f_flags, f_name_idx, f_desc_idx, f_attr_count = struct.unpack(">HHHH", raw_bytes[offset : offset + 8])
            offset += 8
            f_name = get_cp_utf8(f_name_idx)
            f_type = _parse_single_type(get_cp_utf8(f_desc_idx))
            fields.append((f_type, f_name, f_flags))
            for _ in range(f_attr_count):
                if offset + 6 > len(raw_bytes):
                    break
                _, a_len = struct.unpack(">HI", raw_bytes[offset : offset + 6])
                offset += 6 + a_len
                
        methods_count = struct.unpack(">H", raw_bytes[offset : offset + 2])[0] if offset + 2 <= len(raw_bytes) else 0
        offset += 2
        methods = []
        for _ in range(methods_count):
            if offset + 8 > len(raw_bytes):
                break
            m_flags, m_name_idx, m_desc_idx, m_attr_count = struct.unpack(">HHHH", raw_bytes[offset : offset + 8])
            offset += 8
            m_name = get_cp_utf8(m_name_idx)
            m_desc = get_cp_utf8(m_desc_idx)
            ret_t, params = _parse_descriptor(m_desc)
            methods.append((ret_t, m_name, params, m_flags))
            for _ in range(m_attr_count):
                if offset + 6 > len(raw_bytes):
                    break
                _, a_len = struct.unpack(">HI", raw_bytes[offset : offset + 6])
                offset += 6 + a_len
                
        out = io.StringIO()
        out.write("/* ========================================================\n")
        out.write(" * JAVA JVM CLASS DECOMPILED SOURCES\n")
        out.write(f" * File: {filename}\n")
        out.write(f" * Target: {java_ver_str} (Bytecode {major}.{minor})\n")
        out.write(f" * File Size: {_format_size(len(raw_bytes))} ({len(raw_bytes):,} bytes)\n")
        out.write(" * ======================================================== */\n\n")
        
        parts = this_class_raw.rsplit(".", 1)
        pkg = parts[0] if len(parts) == 2 else ""
        c_name = parts[1] if len(parts) == 2 else this_class_raw
        
        if pkg:
            out.write(f"package {pkg};\n\n")
            
        is_interface = bool(access_flags & 0x0200)
        is_enum = bool(access_flags & 0x4000)
        is_abstract = bool(access_flags & 0x0400) and not is_interface
        is_final = bool(access_flags & 0x0010)
        
        modifiers = ["public"]
        if is_abstract: modifiers.append("abstract")
        if is_final and not is_enum: modifiers.append("final")
        
        kind_str = "interface" if is_interface else ("enum" if is_enum else "class")
        extends_str = f" extends {super_class_raw}" if super_class_raw else ""
        implements_str = f" implements {', '.join(interfaces)}" if interfaces else ""
        
        out.write(f"{' '.join(modifiers)} {kind_str} {c_name}{extends_str}{implements_str} {{\n")
        
        if fields:
            for f_type, f_name, f_flags in fields:
                is_st = bool(f_flags & 0x0008)
                is_fn = bool(f_flags & 0x0010)
                f_mod = "public" + (" static" if is_st else "") + (" final" if is_fn else "")
                out.write(f"    {f_mod} {f_type} {f_name};\n")
            out.write("\n")
            
        if methods:
            for ret_t, m_name, params, m_flags in methods:
                is_st = bool(m_flags & 0x0008)
                p_strs = [f"{pt} p{idx+1}" for idx, pt in enumerate(params)]
                m_prefix = "public static" if is_st else "public"
                
                if m_name == "<init>":
                    out.write(f"    {m_prefix} {c_name}({', '.join(p_strs)}) {{\n        /* constructor */\n    }}\n\n")
                elif m_name == "<clinit>":
                    out.write("    static {\n        /* static initializer */\n    }\n\n")
                else:
                    ret_stmt = ""
                    if ret_t not in ("void", ""):
                        if ret_t in ("int", "byte", "short", "long", "char"):
                            ret_stmt = "return 0;"
                        elif ret_t in ("float", "double"):
                            ret_stmt = "return 0.0;"
                        elif ret_t == "boolean":
                            ret_stmt = "return false;"
                        else:
                            ret_stmt = "return null;"
                    body = f"        {ret_stmt}\n    " if ret_stmt else "    "
                    out.write(f"    {m_prefix} {ret_t} {m_name}({', '.join(p_strs)}) {{\n{body}}}\n\n")
        else:
            out.write("    /* no declared methods */\n")
            
        out.write("}\n")
        return out.getvalue()
    except Exception as e:
        return f"// [JVM Class Error] Decompilation error: {e}"
