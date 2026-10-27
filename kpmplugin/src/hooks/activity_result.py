import weakref
import traceback

from hook_utils import find_class

from ..methods import log, log_error


def install_activity_result_hook(pl):
    try:
        _pl_ref = weakref.ref(pl)

        class ActivityResultHook:
            def before_hooked_method(self, param):
                try:
                    _pl = _pl_ref()
                    if not _pl:
                        return
                    request_code = int(param.args[0])
                    result_code = int(param.args[1])
                    data = param.args[2]
                    handled = _pl._handle_collection_import_result(request_code, result_code, data)
                    if handled:
                        param.setResult(None)
                except Exception as e:
                    log_error(e, context="activity result hook")

        ActivityClass = find_class("android.app.Activity")
        if not ActivityClass:
            log("[KPM] Activity class not found for result hook")
            return

        on_activity_result_method = None
        IntentClass = find_class("android.content.Intent")
        cls_obj = ActivityClass.getClass() if hasattr(ActivityClass, "getClass") else ActivityClass
        for m in cls_obj.getDeclaredMethods():
            try:
                if m.getName() == "onActivityResult":
                    params = m.getParameterTypes()
                    if len(params) == 3:
                        if (str(params[0]) == "int" and str(params[1]) == "int" and
                                IntentClass and str(params[2].getName()) == "android.content.Intent"):
                            on_activity_result_method = m
                            break
            except Exception:
                pass

        if not on_activity_result_method:
            log("[KPM] onActivityResult method not found")
            return

        on_activity_result_method.setAccessible(True)
        pl._track_hook(pl.hook_method(on_activity_result_method, ActivityResultHook()))
        log("[KPM] Activity result hook installed")
    except Exception as e:
        log_error(e, context="install_activity_result_hook")
