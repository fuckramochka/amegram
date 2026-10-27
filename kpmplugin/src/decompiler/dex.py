import io
import struct

def _read_uleb128(data: bytes, offset: int):
    result = 0
    shift = 0
    while offset < len(data):
        b = data[offset]
        offset += 1
        result |= (b & 0x7F) << shift
        if (b & 0x80) == 0:
            break
        shift += 7
    return result, offset


def _format_size(size_bytes: int) -> str:
    if size_bytes < 1024:
        return f"{size_bytes} B"
    elif size_bytes < 1024 * 1024:
        return f"{size_bytes / 1024:.1f} KB"
    else:
        return f"{size_bytes / (1024 * 1024):.2f} MB"


def inspect_dex(raw_bytes: bytes, filename: str) -> str:
    if len(raw_bytes) < 112 or not raw_bytes.startswith(b"dex\n"):
        return f"// [DEX Error] Invalid DEX header ({filename})"
    try:
        endian = "<"
        magic_version = raw_bytes[4:7].decode("ascii", errors="replace")
        checksum = struct.unpack(f"{endian}I", raw_bytes[8:12])[0]
        
        string_ids_size, string_ids_off = struct.unpack(f"{endian}II", raw_bytes[56:64])
        type_ids_size, type_ids_off = struct.unpack(f"{endian}II", raw_bytes[64:72])
        proto_ids_size, proto_ids_off = struct.unpack(f"{endian}II", raw_bytes[72:80])
        field_ids_size, field_ids_off = struct.unpack(f"{endian}II", raw_bytes[80:88])
        method_ids_size, method_ids_off = struct.unpack(f"{endian}II", raw_bytes[88:96])
        class_defs_size, class_defs_off = struct.unpack(f"{endian}II", raw_bytes[96:104])
        
        strings = []
        max_str_count = min(string_ids_size, 30000)
        for i in range(max_str_count):
            str_meta_off = string_ids_off + i * 4
            if str_meta_off + 4 > len(raw_bytes):
                break
            s_off = struct.unpack(f"{endian}I", raw_bytes[str_meta_off : str_meta_off + 4])[0]
            if s_off < len(raw_bytes):
                utf16_len, str_start = _read_uleb128(raw_bytes, s_off)
                null_pos = raw_bytes.find(b"\x00", str_start)
                if null_pos != -1:
                    raw_str = raw_bytes[str_start:null_pos]
                    strings.append(raw_str.decode("utf-8", errors="replace"))
                else:
                    strings.append("")
            else:
                strings.append("")
                
        def get_string(idx):
            if 0 <= idx < len(strings):
                return strings[idx]
            return ""
            
        types = []
        max_type_count = min(type_ids_size, 15000)
        for i in range(max_type_count):
            t_meta_off = type_ids_off + i * 4
            if t_meta_off + 4 > len(raw_bytes):
                break
            desc_idx = struct.unpack(f"{endian}I", raw_bytes[t_meta_off : t_meta_off + 4])[0]
            types.append(get_string(desc_idx))
            
        def type_to_java(t_desc):
            if not t_desc:
                return "void"
            if t_desc == "V": return "void"
            if t_desc == "Z": return "boolean"
            if t_desc == "B": return "byte"
            if t_desc == "S": return "short"
            if t_desc == "C": return "char"
            if t_desc == "I": return "int"
            if t_desc == "J": return "long"
            if t_desc == "F": return "float"
            if t_desc == "D": return "double"
            if t_desc.startswith("["):
                return type_to_java(t_desc[1:]) + "[]"
            if t_desc.startswith("L") and t_desc.endswith(";"):
                clean = t_desc[1:-1].replace("/", ".")
                if clean.startswith("java.lang.") and clean.count(".") == 2:
                    return clean[10:]
                return clean
            return t_desc.replace("/", ".")
            
        protos = []
        max_proto_count = min(proto_ids_size, 15000)
        for i in range(max_proto_count):
            p_off = proto_ids_off + i * 12
            if p_off + 12 > len(raw_bytes):
                break
            shorty_idx, return_type_idx, params_off = struct.unpack(f"{endian}III", raw_bytes[p_off : p_off + 12])
            ret_type = types[return_type_idx] if return_type_idx < len(types) else "V"
            
            param_types = []
            if params_off != 0 and params_off + 4 <= len(raw_bytes):
                param_size = struct.unpack(f"{endian}I", raw_bytes[params_off : params_off + 4])[0]
                for p_i in range(min(param_size, 30)):
                    t_idx_off = params_off + 4 + p_i * 2
                    if t_idx_off + 2 <= len(raw_bytes):
                        t_idx = struct.unpack(f"{endian}H", raw_bytes[t_idx_off : t_idx_off + 2])[0]
                        param_types.append(types[t_idx] if t_idx < len(types) else "Object")
            protos.append((type_to_java(ret_type), [type_to_java(p) for p in param_types]))
            
        fields = []
        max_field_count = min(field_ids_size, 15000)
        for i in range(max_field_count):
            f_off = field_ids_off + i * 8
            if f_off + 8 > len(raw_bytes):
                break
            class_idx, type_idx, name_idx = struct.unpack(f"{endian}HHI", raw_bytes[f_off : f_off + 8])
            cls_name = type_to_java(types[class_idx]) if class_idx < len(types) else ""
            f_type = type_to_java(types[type_idx]) if type_idx < len(types) else "Object"
            f_name = get_string(name_idx)
            fields.append((cls_name, f_type, f_name))
            
        methods = []
        max_method_count = min(method_ids_size, 20000)
        for i in range(max_method_count):
            m_off = method_ids_off + i * 8
            if m_off + 8 > len(raw_bytes):
                break
            class_idx, proto_idx, name_idx = struct.unpack(f"{endian}HHI", raw_bytes[m_off : m_off + 8])
            cls_name = type_to_java(types[class_idx]) if class_idx < len(types) else ""
            proto = protos[proto_idx] if proto_idx < len(protos) else ("void", [])
            m_name = get_string(name_idx)
            methods.append((cls_name, proto[0], m_name, proto[1]))
            
        def decompile_code_item(code_off, param_types, is_static=False):
            if not code_off or code_off + 16 > len(raw_bytes):
                return []
            regs_size, ins_size, outs_size, tries_size, debug_off, insns_size = struct.unpack(
                f"{endian}HHHHII", raw_bytes[code_off : code_off + 16]
            )
            if insns_size == 0 or code_off + 16 + insns_size * 2 > len(raw_bytes):
                return []
            
            insns_bytes = raw_bytes[code_off + 16 : code_off + 16 + insns_size * 2]
            insns = [struct.unpack(f"{endian}H", insns_bytes[j*2 : (j+1)*2])[0] for j in range(insns_size)]
            
            statements = []
            reg_values = {}
            param_start = regs_size - ins_size
            
            p_idx = 0
            if not is_static:
                reg_values[param_start] = "this"
                p_idx = 1
                
            for i, p_type in enumerate(param_types):
                reg_values[param_start + p_idx + i] = f"p{i+1}"
                
            last_call_expr = None
            pc = 0
            total_units = len(insns)
            
            def get_reg(r):
                return reg_values.get(r, f"v{r}")
            def set_reg(r, val):
                reg_values[r] = val
                
            while pc < total_units:
                inst = insns[pc]
                op = inst & 0xFF
                b_byte = (inst >> 8) & 0xFF
                
                if op == 0x00:
                    pc += 1
                elif op in (0x01, 0x07):
                    set_reg(b_byte & 0x0F, get_reg((b_byte >> 4) & 0x0F))
                    pc += 1
                elif op in (0x02, 0x08):
                    if pc + 1 < total_units:
                        set_reg(b_byte, get_reg(insns[pc + 1]))
                    pc += 2
                elif op in (0x0a, 0x0c, 0x0d):
                    if last_call_expr:
                        set_reg(b_byte, last_call_expr)
                        last_call_expr = None
                    pc += 1
                elif op == 0x0e:
                    statements.append("return;")
                    pc += 1
                elif op in (0x0f, 0x11):
                    statements.append(f"return {get_reg(b_byte)};")
                    pc += 1
                elif op == 0x12:
                    vA = b_byte & 0x0F
                    val = (b_byte >> 4) & 0x0F
                    if val & 0x08: val -= 16
                    set_reg(vA, "false" if val == 0 else ("true" if val == 1 else str(val)))
                    pc += 1
                elif op == 0x13:
                    if pc + 1 < total_units:
                        val = struct.unpack("<h", struct.pack("<H", insns[pc + 1]))[0]
                        set_reg(b_byte, str(val))
                    pc += 2
                elif op == 0x14:
                    if pc + 2 < total_units:
                        val = struct.unpack("<i", struct.pack("<HH", insns[pc + 1], insns[pc + 2]))[0]
                        set_reg(b_byte, str(val))
                    pc += 3
                elif op == 0x1a:
                    if pc + 1 < total_units:
                        s_idx = insns[pc + 1]
                        set_reg(b_byte, repr(strings[s_idx] if s_idx < len(strings) else ""))
                    pc += 2
                elif op == 0x1b:
                    if pc + 2 < total_units:
                        s_idx = insns[pc + 1] | (insns[pc + 2] << 16)
                        set_reg(b_byte, repr(strings[s_idx] if s_idx < len(strings) else ""))
                    pc += 3
                elif op == 0x1c:
                    if pc + 1 < total_units:
                        t_idx = insns[pc + 1]
                        set_reg(b_byte, f"{type_to_java(types[t_idx]) if t_idx < len(types) else 'Object'}.class")
                    pc += 2
                elif op == 0x22:
                    if pc + 1 < total_units:
                        t_idx = insns[pc + 1]
                        set_reg(b_byte, f"new {type_to_java(types[t_idx]) if t_idx < len(types) else 'Object'}()")
                    pc += 2
                elif op == 0x27:
                    statements.append(f"throw {get_reg(b_byte)};")
                    pc += 1
                elif 0x38 <= op <= 0x3d:
                    r_val = get_reg(b_byte)
                    cond = f"{r_val} != null" if op == 0x39 else (f"{r_val} == null" if op == 0x38 else f"{r_val} != 0")
                    statements.append(f"if ({cond}) {{")
                    pc += 2
                elif 0x52 <= op <= 0x58:
                    vA, vB = b_byte & 0x0F, (b_byte >> 4) & 0x0F
                    if pc + 1 < total_units:
                        f_idx = insns[pc + 1]
                        f_info = fields[f_idx] if f_idx < len(fields) else ("", "", "field")
                        set_reg(vA, f"{get_reg(vB)}.{f_info[2]}")
                    pc += 2
                elif 0x59 <= op <= 0x5f:
                    vA, vB = b_byte & 0x0F, (b_byte >> 4) & 0x0F
                    if pc + 1 < total_units:
                        f_idx = insns[pc + 1]
                        f_info = fields[f_idx] if f_idx < len(fields) else ("", "", "field")
                        statements.append(f"{get_reg(vB)}.{f_info[2]} = {get_reg(vA)};")
                    pc += 2
                elif 0x60 <= op <= 0x66:
                    if pc + 1 < total_units:
                        f_idx = insns[pc + 1]
                        f_info = fields[f_idx] if f_idx < len(fields) else ("", "", "field")
                        set_reg(b_byte, f"{f_info[0]}.{f_info[2]}")
                    pc += 2
                elif 0x67 <= op <= 0x6d:
                    if pc + 1 < total_units:
                        f_idx = insns[pc + 1]
                        f_info = fields[f_idx] if f_idx < len(fields) else ("", "", "field")
                        statements.append(f"{f_info[0]}.{f_info[2]} = {get_reg(b_byte)};")
                    pc += 2
                elif 0x6e <= op <= 0x72:
                    arg_count = (b_byte >> 4) & 0x0F
                    if pc + 2 < total_units:
                        m_idx = insns[pc + 1]
                        rw = insns[pc + 2]
                        reg_list = [rw & 0x0F, (rw >> 4) & 0x0F, (rw >> 8) & 0x0F, (rw >> 12) & 0x0F, b_byte & 0x0F][:arg_count]
                        m_info = methods[m_idx] if m_idx < len(methods) else ("Obj", "void", "func", [])
                        cls_t, ret_t, m_name, params = m_info
                        
                        if op == 0x71:
                            call_args = [get_reg(r) for r in reg_list]
                            call_expr = f"{cls_t}.{m_name}({', '.join(call_args)})"
                        elif op == 0x6f:
                            call_args = [get_reg(r) for r in reg_list[1:]]
                            call_expr = f"super.{m_name}({', '.join(call_args)})"
                        elif op == 0x70 and m_name == "<init>":
                            call_args = [get_reg(r) for r in reg_list[1:]]
                            target_obj = get_reg(reg_list[0]) if reg_list else "this"
                            call_expr = f"super({', '.join(call_args)})" if target_obj == "this" else f"new {cls_t}({', '.join(call_args)})"
                            if reg_list and target_obj != "this":
                                set_reg(reg_list[0], call_expr)
                        else:
                            target_obj = get_reg(reg_list[0]) if reg_list else "this"
                            call_args = [get_reg(r) for r in reg_list[1:]]
                            call_expr = f"{target_obj}.{m_name}({', '.join(call_args)})"
                            
                        last_call_expr = call_expr
                        statements.append(f"{call_expr};")
                    pc += 3
                elif 0x74 <= op <= 0x78:
                    arg_count = b_byte
                    if pc + 3 < total_units:
                        m_idx = insns[pc + 1]
                        vCCCC = insns[pc + 2]
                        reg_list = [vCCCC + i for i in range(arg_count)]
                        m_info = methods[m_idx] if m_idx < len(methods) else ("Obj", "void", "func", [])
                        cls_t, ret_t, m_name, params = m_info
                        
                        if op == 0x77:
                            call_args = [get_reg(r) for r in reg_list]
                            call_expr = f"{cls_t}.{m_name}({', '.join(call_args)})"
                        else:
                            target_obj = get_reg(reg_list[0]) if reg_list else "this"
                            call_args = [get_reg(r) for r in reg_list[1:]]
                            call_expr = f"{target_obj}.{m_name}({', '.join(call_args)})"
                        last_call_expr = call_expr
                        statements.append(f"{call_expr};")
                    pc += 3
                else:
                    pc += 1
                    
            clean_res = []
            for s in statements:
                if s not in clean_res or s == "return;":
                    clean_res.append(s)
            return clean_res

        class_defs = []
        max_class_count = min(class_defs_size, 3000)
        for i in range(max_class_count):
            c_off = class_defs_off + i * 32
            if c_off + 32 > len(raw_bytes):
                break
            class_idx, access_flags, super_idx, interfaces_off, source_file_idx, annot_off, class_data_off, stat_val_off = struct.unpack(
                f"{endian}IIIIIIII", raw_bytes[c_off : c_off + 32]
            )
            cls_name = type_to_java(types[class_idx]) if class_idx < len(types) else f"Class{i}"
            super_name = type_to_java(types[super_idx]) if (super_idx != 0xFFFFFFFF and super_idx < len(types)) else ""
            if super_name == "Object":
                super_name = ""
                
            interfaces = []
            if interfaces_off != 0 and interfaces_off + 4 <= len(raw_bytes):
                if_size = struct.unpack(f"{endian}I", raw_bytes[interfaces_off : interfaces_off + 4])[0]
                for if_i in range(min(if_size, 10)):
                    t_idx_off = interfaces_off + 4 + if_i * 2
                    if t_idx_off + 2 <= len(raw_bytes):
                        t_idx = struct.unpack(f"{endian}H", raw_bytes[t_idx_off : t_idx_off + 2])[0]
                        if t_idx < len(types):
                            interfaces.append(type_to_java(types[t_idx]))
                            
            class_method_codes = {}
            if class_data_off != 0 and class_data_off < len(raw_bytes):
                cd_off = class_data_off
                static_fields_size, cd_off = _read_uleb128(raw_bytes, cd_off)
                instance_fields_size, cd_off = _read_uleb128(raw_bytes, cd_off)
                direct_methods_size, cd_off = _read_uleb128(raw_bytes, cd_off)
                virtual_methods_size, cd_off = _read_uleb128(raw_bytes, cd_off)
                
                for _ in range(static_fields_size):
                    _, cd_off = _read_uleb128(raw_bytes, cd_off)
                    _, cd_off = _read_uleb128(raw_bytes, cd_off)
                for _ in range(instance_fields_size):
                    _, cd_off = _read_uleb128(raw_bytes, cd_off)
                    _, cd_off = _read_uleb128(raw_bytes, cd_off)
                    
                cur_m_idx = 0
                for _ in range(direct_methods_size):
                    diff, cd_off = _read_uleb128(raw_bytes, cd_off)
                    cur_m_idx += diff
                    flags, cd_off = _read_uleb128(raw_bytes, cd_off)
                    c_off_val, cd_off = _read_uleb128(raw_bytes, cd_off)
                    class_method_codes[cur_m_idx] = (c_off_val, flags)
                    
                cur_m_idx = 0
                for _ in range(virtual_methods_size):
                    diff, cd_off = _read_uleb128(raw_bytes, cd_off)
                    cur_m_idx += diff
                    flags, cd_off = _read_uleb128(raw_bytes, cd_off)
                    c_off_val, cd_off = _read_uleb128(raw_bytes, cd_off)
                    class_method_codes[cur_m_idx] = (c_off_val, flags)
                    
            class_defs.append({
                "name": cls_name,
                "flags": access_flags,
                "super": super_name,
                "interfaces": interfaces,
                "method_codes": class_method_codes
            })
            
        out = io.StringIO()
        out.write("/* ========================================================\n")
        out.write(" * ANDROID DALVIK EXECUTABLE (DEX) DECOMPILED JAVA SOURCES\n")
        out.write(f" * File: {filename}\n")
        out.write(f" * DEX Version: {magic_version} | Checksum: 0x{checksum:08X}\n")
        out.write(f" * Classes: {class_defs_size:,} | Methods: {method_ids_size:,} | Fields: {field_ids_size:,}\n")
        out.write(f" * Types: {type_ids_size:,} | Strings: {string_ids_size:,}\n")
        out.write(f" * File Size: {_format_size(len(raw_bytes))} ({len(raw_bytes):,} bytes)\n")
        out.write(" * ======================================================== */\n\n")
        
        classes_by_pkg = {}
        for cdef in class_defs:
            full_name = cdef["name"]
            parts = full_name.rsplit(".", 1)
            pkg = parts[0] if len(parts) == 2 else "default"
            c_name = parts[1] if len(parts) == 2 else full_name
            if pkg not in classes_by_pkg:
                classes_by_pkg[pkg] = []
            classes_by_pkg[pkg].append((c_name, cdef))
            
        for pkg, cls_list in sorted(classes_by_pkg.items()):
            if pkg != "default":
                out.write(f"// ========================================================\n")
                out.write(f"// PACKAGE: {pkg}\n")
                out.write(f"// ========================================================\n")
                out.write(f"package {pkg};\n\n")
                
            for c_name, cdef in cls_list:
                full_cls_name = cdef["name"]
                flags = cdef["flags"]
                
                is_interface = bool(flags & 0x0200)
                is_enum = bool(flags & 0x4000)
                is_abstract = bool(flags & 0x0400) and not is_interface
                is_final = bool(flags & 0x0010)
                
                modifiers = ["public"]
                if is_abstract: modifiers.append("abstract")
                if is_final and not is_enum: modifiers.append("final")
                
                kind_str = "interface" if is_interface else ("enum" if is_enum else "class")
                extends_str = f" extends {cdef['super']}" if cdef["super"] else ""
                implements_str = f" implements {', '.join(cdef['interfaces'])}" if cdef["interfaces"] else ""
                
                out.write(f"{' '.join(modifiers)} {kind_str} {c_name}{extends_str}{implements_str} {{\n")
                
                for cls_t, f_type, f_name in fields:
                    if cls_t == full_cls_name:
                        out.write(f"    public {f_type} {f_name};\n")
                        
                has_methods = False
                m_codes = cdef.get("method_codes", {})
                
                for m_i, (cls_t, ret_t, m_name, params) in enumerate(methods):
                    if cls_t == full_cls_name:
                        has_methods = True
                        p_strs = [f"{pt} p{idx+1}" for idx, pt in enumerate(params)]
                        code_info = m_codes.get(m_i)
                        c_off_val = code_info[0] if code_info else 0
                        m_flags = code_info[1] if code_info else 0
                        is_static_m = bool(m_flags & 0x0008)
                        
                        stmts = decompile_code_item(c_off_val, params, is_static=is_static_m)
                        
                        m_prefix = "public static" if is_static_m else "public"
                        if m_name == "<init>":
                            out.write(f"    {m_prefix} {c_name}({', '.join(p_strs)}) {{\n")
                        elif m_name == "<clinit>":
                            out.write("    static {\n")
                        else:
                            out.write(f"    {m_prefix} {ret_t} {m_name}({', '.join(p_strs)}) {{\n")
                            
                        if stmts:
                            for s in stmts:
                                out.write(f"        {s}\n")
                        else:
                            if ret_t not in ("void", ""):
                                if ret_t in ("int", "byte", "short", "long", "char"):
                                    out.write("        return 0;\n")
                                elif ret_t in ("float", "double"):
                                    out.write("        return 0.0;\n")
                                elif ret_t == "boolean":
                                    out.write("        return false;\n")
                                else:
                                    out.write("        return null;\n")
                            else:
                                out.write("        /* empty */\n")
                                
                        out.write("    }\n\n")
                        
                if not has_methods:
                    out.write("    /* no declared methods */\n")
                    
                out.write("}\n\n")
                
        return out.getvalue()
    except Exception as e:
        return f"// [DEX Error] Decompilation error: {e}"
