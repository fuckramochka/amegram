import os

from android_utils import run_on_ui_thread
from client_utils import get_last_fragment
from hook_utils import find_class
from org.telegram.messenger import AndroidUtilities
from org.telegram.ui.ActionBar import Theme

from ....methods import log, log_error, _tr, register_active_sheet, unregister_active_sheet

_CLASS_CACHE = {}


def _cached_class(name):
    cls = _CLASS_CACHE.get(name)
    if cls is None:
        try:
            cls = find_class(name)
            if cls is not None:
                _CLASS_CACHE[name] = cls
        except Exception:
            cls = None
    return cls


def show_web_ide_sheet(activity, fragment, html_payload: str, target_title: str) -> bool:
    """
    Renders the modern Web-based IDE inside an Android BottomSheet using streamed cache loading.
    """
    def _ui_task():
        try:
            BottomSheet = _cached_class("org.telegram.ui.ActionBar.BottomSheet")
            WebView = _cached_class("android.webkit.WebView")
            FrameLayout = _cached_class("android.widget.FrameLayout")
            LinearLayout = _cached_class("android.widget.LinearLayout")
            View_cls = _cached_class("android.view.View")
            GradientDrawable = _cached_class("android.graphics.drawable.GradientDrawable")
            Gravity = _cached_class("android.view.Gravity")
            ClipboardManager = _cached_class("android.content.ClipboardManager")
            ClipData = _cached_class("android.content.ClipData")
            Context = _cached_class("android.content.Context")
            View_OnTouchListener = _cached_class("android.view.View$OnTouchListener")

            from java import dynamic_proxy

            if not (BottomSheet and WebView):
                log("[KPM] _show_web_ide_sheet: required Android UI classes missing")
                return

            sheet = BottomSheet(activity, False, fragment.getResourceProvider() if hasattr(fragment, "getResourceProvider") else None)
            sheet.setApplyBottomPadding(False)
            sheet.setApplyTopPadding(False)
            if hasattr(sheet, "fixNavigationBar"):
                bg_col = Theme.getColor(Theme.key_windowBackgroundWhite) if hasattr(Theme, "key_windowBackgroundWhite") else 0xFF141416
                sheet.fixNavigationBar(bg_col)

            try:
                register_active_sheet(sheet)
                DialogInterface = _cached_class("android.content.DialogInterface")
                if DialogInterface:
                    class _DismissProxy(dynamic_proxy(DialogInterface.OnDismissListener)):
                        def onDismiss(self, d):
                            try:
                                unregister_active_sheet(sheet)
                            except Exception:
                                pass
                    sheet.setOnDismissListener(_DismissProxy())
            except Exception:
                pass

            screen_h = AndroidUtilities.displaySize.y if hasattr(AndroidUtilities, "displaySize") else 1920
            target_height = int(screen_h * 0.90)

            # WebView initialization
            web_view = WebView(activity)
            settings = web_view.getSettings()
            settings.setJavaScriptEnabled(True)
            settings.setDomStorageEnabled(True)
            settings.setLoadWithOverviewMode(True)
            settings.setUseWideViewPort(True)
            settings.setBuiltInZoomControls(False)
            settings.setDisplayZoomControls(False)
            settings.setAllowFileAccess(True)

            # Prevent BottomSheet from stealing vertical scroll gestures (fix scroll-up bug)
            if View_OnTouchListener:
                try:
                    class _WebViewTouchListener(dynamic_proxy(View_OnTouchListener)):
                        def onTouch(self, v, event):
                            try:
                                action = event.getActionMasked() if hasattr(event, "getActionMasked") else event.getAction()
                                if action == 0:  # ACTION_DOWN
                                    parent = v.getParent()
                                    if parent:
                                        parent.requestDisallowInterceptTouchEvent(True)
                                elif action in (1, 3):  # ACTION_UP or ACTION_CANCEL
                                    parent = v.getParent()
                                    if parent:
                                        parent.requestDisallowInterceptTouchEvent(False)
                            except Exception:
                                pass
                            return False

                    web_view.setOnTouchListener(_WebViewTouchListener())
                except Exception as te:
                    log_error(te, context="setOnTouchListener on web_view")

            DownloadListener = _cached_class("android.webkit.DownloadListener")
            if DownloadListener:
                try:
                    class _IdeDownloadListener(dynamic_proxy(DownloadListener)):
                        def onDownloadStart(self, url, userAgent, contentDisposition, mimetype, contentLength):
                            try:
                                url_str = str(url or "")
                                disp_str = str(contentDisposition or "")
                                if "kpm_close" in url_str or "kpm_close" in disp_str:
                                    sheet.dismiss()
                                elif "kpm_copy" in url_str or "kpm_copy" in disp_str:
                                    from urllib.parse import unquote
                                    raw_data = ""
                                    if "::" in url_str:
                                        raw_data = url_str.split("::", 1)[1]
                                    elif "kpm_copy" in url_str:
                                        raw_data = url_str.replace("data:text/plain;charset=utf-8,kpm_copy", "").strip(":")
                                    if raw_data:
                                        decoded = unquote(raw_data)
                                        cm = activity.getSystemService(Context.CLIPBOARD_SERVICE)
                                        if cm and ClipData:
                                            clip = ClipData.newPlainText("Plugin Code", decoded)
                                            cm.setPrimaryClip(clip)
                            except Exception as de:
                                log_error(de, context="onDownloadStart")

                    web_view.setDownloadListener(_IdeDownloadListener())
                except Exception as le:
                    log_error(le, context="setDownloadListener")

            try:
                cache_dir = activity.getCacheDir().getAbsolutePath()
                cache_file = os.path.join(cache_dir, "kpm_web_ide_preview.html")
                with open(cache_file, "w", encoding="utf-8") as f:
                    f.write(html_payload)
                web_view.loadUrl(f"file://{cache_file}")
            except Exception as fe:
                log_error(fe, context="writing cache html for web_view")
                web_view.loadDataWithBaseURL("https://kpm.local/", html_payload, "text/html", "utf-8", None)

            # Container layout
            root_layout = LinearLayout(activity)
            root_layout.setOrientation(LinearLayout.VERTICAL)

            # Top Drag Handle
            handle_container = FrameLayout(activity)
            handle_h = AndroidUtilities.dp(12) if hasattr(AndroidUtilities, "dp") else 24
            handle_lp = LinearLayout.LayoutParams(-1, handle_h)
            handle_container.setLayoutParams(handle_lp)

            handle_bar = View_cls(activity)
            handle_w = AndroidUtilities.dp(36) if hasattr(AndroidUtilities, "dp") else 72
            handle_bar_h = AndroidUtilities.dp(4) if hasattr(AndroidUtilities, "dp") else 8
            handle_bar_lp = FrameLayout.LayoutParams(handle_w, handle_bar_h)
            if Gravity and hasattr(Gravity, "CENTER"):
                handle_bar_lp.gravity = Gravity.CENTER
            handle_bar.setLayoutParams(handle_bar_lp)

            bar_drawable = GradientDrawable()
            bar_drawable.setShape(GradientDrawable.RECTANGLE)
            bar_drawable.setCornerRadius(float(AndroidUtilities.dp(2) if hasattr(AndroidUtilities, "dp") else 4))
            bar_drawable.setColor(0x33888888)
            handle_bar.setBackground(bar_drawable)

            handle_container.addView(handle_bar)
            root_layout.addView(handle_container)

            # Add WebView
            web_lp = LinearLayout.LayoutParams(-1, target_height - handle_h)
            web_view.setLayoutParams(web_lp)
            root_layout.addView(web_view)

            sheet.setCustomView(root_layout)
            sheet.show()
            log(f"[KPM] show_web_ide_sheet: displayed Web IDE for '{target_title}'")
        except Exception as e:
            log_error(e, context="_show_web_ide_sheet._ui_task")

    run_on_ui_thread(_ui_task)
    return True
