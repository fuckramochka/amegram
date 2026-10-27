import dis
import io
import marshal
import re
import struct

def _fallback_decompile_pyc(raw_bytes: bytes, filename: str) -> str:
    out = io.StringIO()
    found_strings = []
    for match in re.finditer(rb"[a-zA-Z_][a-zA-Z0-9_]{1,64}", raw_bytes):
        s = match.group().decode("ascii", errors="ignore")
        if s not in ("None", "code", "module", "stdin", "utf_8", "builtins"):
            found_strings.append(s)
            
    seen = set()
    unique_names = []
    for s in found_strings:
        if s not in seen:
            seen.add(s)
            unique_names.append(s)
            
    known_std = {
        "os", "sys", "re", "json", "time", "math", "base64", "io", "struct",
        "threading", "subprocess", "logging", "asyncio", "hashlib", "shutil",
        "pathlib", "datetime", "typing", "collections", "random", "urllib"
    }
    
    imports = []
    classes = []
    functions = []
    
    for name in unique_names:
        if name in known_std:
            imports.append(f"import {name}")
        elif name[0].isupper() and "_" not in name and not name.isupper():
            classes.append(name)
        elif name.startswith(("get_", "set_", "is_", "on_", "handle_", "init_", "create_", "update_", "delete_", "load_", "save_", "parse_")) or name.islower():
            if len(name) > 2 and name not in known_std:
                functions.append(name)
                
    if imports:
        for imp in imports:
            out.write(f"{imp}\n")
        out.write("\n")
        
    if classes:
        for cls in classes[:30]:
            out.write(f"class {cls}:\n    pass\n\n")
            
    if functions:
        for fn in functions[:50]:
            out.write(f"def {fn}(*args, **kwargs):\n    pass\n\n")
            
    res = out.getvalue().strip()
    return res if res else "# [PYC] Compiled bytecode contains no string literals"


def _decompile_code_object(co, indent="", is_class=False):
    lines = []
    name = getattr(co, "co_name", "<module>")
    is_module = (name == "<module>")
    
    consts = getattr(co, "co_consts", ())
    varnames = getattr(co, "co_varnames", ())
    argcount = getattr(co, "co_argcount", 0)
    flags = getattr(co, "co_flags", 0)
    kwonly = getattr(co, "co_kwonlyargcount", 0)
    
    body_indent = indent
    if not is_module and not is_class:
        args = list(varnames[:argcount])
        if flags & 0x04:
            args.append("*" + varnames[argcount + kwonly])
        if flags & 0x08:
            args.append("**" + varnames[-1])
        is_coroutine = bool(flags & 0x80)
        prefix = "async def " if is_coroutine else "def "
        sig = f"{indent}{prefix}{name}({', '.join(args)}):"
        lines.append(sig)
        body_indent = indent + "    "
    elif is_class:
        body_indent = indent
        
    if consts and isinstance(consts[0], str) and consts[0] and not consts[0].startswith("<"):
        if not is_module and consts[0] != name:
            lines.append(f'{body_indent}"""{consts[0]}"""')
        
    stack = []
    statements = []
    last_import_module = None
    
    try:
        instructions = list(dis.get_instructions(co))
        idx = 0
        n_inst = len(instructions)
        
        while idx < n_inst:
            inst = instructions[idx]
            op = inst.opname
            arg = inst.argval
            arg_raw = inst.arg
            
            if op in ("RESUME", "CACHE", "PRECALL", "PUSH_NULL", "NOP", "RETURN_GENERATOR", "MAKE_CELL", "NOT_TAKEN", "TO_BOOL"):
                idx += 1
                continue
                
            if "LOAD_FAST" in op or "LOAD_GLOBAL" in op or "LOAD_DEREF" in op or "LOAD_NAME" in op or "LOAD_CLASSDEREF" in op:
                if isinstance(arg, (tuple, list)):
                    for item in arg:
                        stack.append(str(item))
                elif arg is not None:
                    stack.append(str(arg))
            elif op in ("LOAD_CONST", "LOAD_SMALL_INT"):
                if hasattr(arg, "co_code"):
                    stack.append(arg)
                else:
                    stack.append(repr(arg))
            elif op == "LOAD_BUILD_CLASS":
                stack.append("__build_class__")
            elif op == "LOAD_LOCALS":
                stack.append("locals()")
            elif op in ("STORE_FAST", "STORE_NAME", "STORE_GLOBAL", "STORE_DEREF"):
                val = stack.pop() if stack else "None"
                var = str(arg) if arg is not None else f"var_{arg_raw}"
                if isinstance(val, tuple) and val[0] == "__class__":
                    pass
                elif not (is_class and var == "__doc__"):
                    statements.append(f"{body_indent}{var} = {val}")
            elif op == "IMPORT_NAME":
                mod_name = str(arg) if arg is not None else "mod"
                last_import_module = mod_name
                from_list = stack.pop() if stack else "None"
                level = stack.pop() if stack else "0"
                if from_list == "None" or from_list == "()" or from_list == "None":
                    statements.append(f"{body_indent}import {mod_name}")
                    stack.append(mod_name)
                else:
                    stack.append(f"__import__('{mod_name}')")
            elif op == "IMPORT_FROM":
                from_name = str(arg) if arg is not None else "item"
                mod = last_import_module or (stack[-1] if stack else "module")
                statements.append(f"{body_indent}from {mod} import {from_name}")
                stack.append(from_name)
            elif op in ("BINARY_OP", "COMPARE_OP", "IS_OP", "CONTAINS_OP"):
                right = stack.pop() if stack else "y"
                left = stack.pop() if stack else "x"
                sym = str(arg) if arg is not None else "+"
                op_map = {
                    "ADD": "+", "SUBTRACT": "-", "MULTIPLY": "*", "MATRIX_MULTIPLY": "@",
                    "TRUE_DIVIDE": "/", "FLOOR_DIVIDE": "//", "MODULO": "%",
                    "POWER": "**", "LSHIFT": "<<", "RSHIFT": ">>", "AND": "&",
                    "OR": "|", "XOR": "^"
                }
                sym = op_map.get(sym, sym)
                stack.append(f"({left} {sym} {right})")
            elif "UNARY_" in op:
                val = stack.pop() if stack else "x"
                sym = "-" if "NEGATIVE" in op else ("not " if "NOT" in op else "~")
                stack.append(f"{sym}{val}")
            elif op in ("BUILD_LIST", "BUILD_TUPLE", "BUILD_SET"):
                count = arg_raw if arg_raw is not None else 0
                items = [stack.pop() for _ in range(min(count, len(stack)))]
                items.reverse()
                if "LIST" in op:
                    stack.append(f"[{', '.join(items)}]")
                elif "TUPLE" in op:
                    stack.append(f"({', '.join(items)})" if len(items) != 1 else f"({items[0]},)")
                else:
                    stack.append(f"{{{', '.join(items)}}}")
            elif op == "BUILD_MAP":
                count = arg_raw if arg_raw is not None else 0
                entries = []
                for _ in range(min(count, len(stack) // 2)):
                    val = stack.pop()
                    key = stack.pop()
                    entries.append(f"{key}: {val}")
                entries.reverse()
                stack.append(f"{{{', '.join(entries)}}}")
            elif op == "BINARY_SUBSCR":
                sub = stack.pop() if stack else "0"
                container = stack.pop() if stack else "obj"
                stack.append(f"{container}[{sub}]")
            elif op == "STORE_SUBSCR":
                sub = stack.pop() if stack else "0"
                container = stack.pop() if stack else "obj"
                val = stack.pop() if stack else "None"
                statements.append(f"{body_indent}{container}[{sub}] = {val}")
            elif op == "LOAD_ATTR":
                obj = stack.pop() if stack else "obj"
                attr = str(arg) if arg is not None else "attr"
                stack.append(f"{obj}.{attr}")
            elif op == "STORE_ATTR":
                obj = stack.pop() if stack else "obj"
                val = stack.pop() if stack else "None"
                attr = str(arg) if arg is not None else "attr"
                statements.append(f"{body_indent}{obj}.{attr} = {val}")
            elif op == "CALL_FUNCTION" or op == "CALL" or op == "CALL_KW":
                argc = arg_raw if arg_raw is not None else 0
                call_args = [stack.pop() for _ in range(min(argc, len(stack)))]
                call_args.reverse()
                fn = stack.pop() if stack else "func"
                if fn == "__build_class__":
                    cls_code = call_args[0] if len(call_args) > 0 else None
                    cls_name = call_args[1].strip("'\"") if len(call_args) > 1 and isinstance(call_args[1], str) else "Class"
                    bases = call_args[2:] if len(call_args) > 2 else []
                    bases_str = f"({', '.join(map(str, bases))})" if bases else ""
                    statements.append(f"\n{body_indent}class {cls_name}{bases_str}:")
                    if hasattr(cls_code, "co_code"):
                        cls_body = _decompile_code_object(cls_code, body_indent + "    ", is_class=True)
                        statements.append(cls_body)
                        stack.append(("__class__", cls_name))
                    else:
                        stack.append(f"{fn}({', '.join(map(str, call_args))})")
                else:
                    stack.append(f"{fn}({', '.join(map(str, call_args))})")
            elif op in ("RETURN_VALUE", "RETURN_CONST"):
                val = repr(arg) if op == "RETURN_CONST" else (stack.pop() if stack else "None")
                if not is_module and not is_class:
                    if val != "None" or len(statements) == 0:
                        statements.append(f"{body_indent}return {val}")
            elif op == "YIELD_VALUE":
                val = stack.pop() if stack else "None"
                statements.append(f"{body_indent}yield {val}")
            elif op == "RAISE_VARARGS":
                val = stack.pop() if stack else "Exception"
                statements.append(f"{body_indent}raise {val}")
            elif op == "POP_TOP":
                if stack:
                    expr = stack.pop()
                    if isinstance(expr, tuple) and expr[0] == "__class__":
                        pass
                    else:
                        expr_str = str(expr)
                        if "(" in expr_str or "=" in expr_str:
                            if not expr_str.startswith("__build_class__"):
                                statements.append(f"{body_indent}{expr_str}")
            elif op in ("POP_JUMP_IF_FALSE", "POP_JUMP_FORWARD_IF_FALSE"):
                cond = stack.pop() if stack else "cond"
                statements.append(f"{body_indent}if {cond}:")
            
            idx += 1
            
    except Exception as e:
        statements.append(f"{body_indent}# [Decompilation error: {e}]")
        
    if not is_module and not statements:
        statements.append(f"{body_indent}pass")
        
    lines.extend(statements)
    return "\n".join(l for l in lines if l is not None)


def decompile_pyc(raw_bytes: bytes, filename: str) -> str:
    try:
        code_obj = None
        try:
            import importlib.util
            own_magic = importlib.util.MAGIC_NUMBER[:4]
            foreign = bytes(raw_bytes[:4]) != bytes(own_magic)
        except Exception:
            foreign = False
        if not foreign:
            for offset in (16, 12, 8):
                try:
                    code_obj = marshal.loads(raw_bytes[offset:])
                    if code_obj and hasattr(code_obj, "co_code"):
                        break
                except Exception:
                    code_obj = None

        if code_obj:
            decompiled_python = _decompile_code_object(code_obj)
            if decompiled_python and decompiled_python.strip():
                return decompiled_python.strip()
                
        return _fallback_decompile_pyc(raw_bytes, filename)
    except Exception:
        return _fallback_decompile_pyc(raw_bytes, filename)
