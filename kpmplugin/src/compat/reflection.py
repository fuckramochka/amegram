import traceback
from hook_utils import find_class
from ..methods import log, log_error


_CLASS_CACHE = {}


def find_class_safe(*class_names):
    for name in class_names:
        if not name:
            continue
        if name in _CLASS_CACHE:
            return _CLASS_CACHE[name]
        try:
            cls = find_class(name)
            if cls is not None:
                _CLASS_CACHE[name] = cls
                log(f"[KPM] Resolved class '{name}'")
                return cls
        except Exception as e:
            log_error(e, context=f"Failed to resolve class '{name}'")
    return None


def get_field_safe(target, *field_names):
    if target is None:
        return None
    current = target.getClass() if hasattr(target, "getClass") else None
    while current:
        for name in field_names:
            try:
                field = current.getDeclaredField(name)
                field.setAccessible(True)
                val = field.get(target)
                return val
            except Exception:
                pass
        try:
            current = current.getSuperclass()
        except Exception:
            break
    return None


def set_field_safe(target, field_name, value):
    if target is None:
        return False
    current = target.getClass() if hasattr(target, "getClass") else None
    while current:
        try:
            field = current.getDeclaredField(field_name)
            field.setAccessible(True)
            field.set(target, value)
            return True
        except Exception:
            pass
        try:
            current = current.getSuperclass()
        except Exception:
            break
    return False


def get_method_safe(target_cls, method_name, param_count=None):
    if target_cls is None:
        return None
    cls_obj = target_cls.getClass() if hasattr(target_cls, "getClass") and not str(type(target_cls).__name__).endswith("Class") else target_cls
    try:
        methods = cls_obj.getDeclaredMethods() if hasattr(cls_obj, "getDeclaredMethods") else []
        for m in methods:
            try:
                if m.getName() == method_name:
                    if param_count is None or len(m.getParameterTypes()) == param_count:
                        m.setAccessible(True)
                        return m
            except Exception:
                pass
    except Exception as e:
        log_error(e, context=f"get_method_safe for {method_name}")
    return None
