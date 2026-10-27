import io
import os
import re
import struct

def _format_size(size_bytes: int) -> str:
    if size_bytes < 1024:
        return f"{size_bytes} B"
    elif size_bytes < 1024 * 1024:
        return f"{size_bytes / 1024:.1f} KB"
    else:
        return f"{size_bytes / (1024 * 1024):.2f} MB"


def inspect_elf(raw_bytes: bytes, filename: str) -> str:
    if len(raw_bytes) < 52 or raw_bytes[:4] != b"\x7fELF":
        return f"// [ELF Error] Invalid ELF header ({filename})"
    try:
        ei_class = raw_bytes[4]
        is_64 = (ei_class == 2)
        ei_data = raw_bytes[5]
        endian = "<" if ei_data == 1 else ">"
        
        e_machine = struct.unpack(f"{endian}H", raw_bytes[18:20])[0]
        arch_map = {
            0x03: "x86 (IA-32)",
            0x28: "ARM (32-bit)",
            0x3E: "x86-64 (AMD64)",
            0xB7: "AArch64 (ARM 64-bit)",
            0xF3: "RISC-V",
        }
        arch_str = arch_map.get(e_machine, f"Unknown (0x{e_machine:04X})")
        
        sample = raw_bytes[:256 * 1024]
        raw_jni = set(re.findall(rb"Java_[a-zA-Z0-9_]+", sample))
        jni_methods = [m.decode("ascii", errors="ignore") for m in raw_jni]
        
        raw_libs = set(re.findall(rb"lib[a-zA-Z0-9_.-]+\.so", sample))
        base_fn = os.path.basename(filename).encode("ascii", errors="ignore")
        libraries = [l.decode("ascii", errors="ignore") for l in raw_libs if l != base_fn]
        
        out = io.StringIO()
        out.write("/* ========================================================\n")
        out.write(" * NATIVE ELF SHARED LIBRARY ANALYSIS (C/C++ Header)\n")
        out.write(f" * File: {filename}\n")
        out.write(f" * Architecture: {arch_str} ({'64-bit' if is_64 else '32-bit'})\n")
        out.write(f" * Endianness: {'Little Endian' if ei_data == 1 else 'Big Endian'}\n")
        out.write(f" * File Size: {_format_size(len(raw_bytes))} ({len(raw_bytes):,} bytes)\n")
        out.write(" * ======================================================== */\n\n")
        
        out.write("#include <jni.h>\n\n")
        
        if libraries:
            out.write(f"// --- DEPENDENT SHARED LIBRARIES ({len(libraries)}) ---\n")
            for lib in sorted(libraries)[:30]:
                out.write(f"#include <{lib}>\n")
            out.write("\n")
            
        if jni_methods:
            out.write(f"// --- EXPORTED JNI METHODS ({len(jni_methods)}) ---\n")
            out.write('#ifdef __cplusplus\nextern "C" {\n#endif\n\n')
            for jni in sorted(jni_methods)[:50]:
                out.write(f"JNIEXPORT void JNICALL {jni}(JNIEnv *env, jobject thiz);\n")
            out.write('\n#ifdef __cplusplus\n}\n#endif\n')
            
        return out.getvalue()
    except Exception as e:
        return f"// [ELF Error] Analysis error: {e}"
