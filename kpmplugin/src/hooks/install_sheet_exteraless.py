from android_utils import OnClickListener, run_on_ui_thread
from client_utils import run_on_queue
from hook_utils import find_class
from java import jint

from ..methods import log, log_error, _tr, _status_label

EXTERALESS_SHEET = "app.exteraless.plugins.ui.PluginInstallBottomSheet"

_CLASSES = {}


def _cls(name):
    c = _CLASSES.get(name)
    if c is None:
        c = find_class(name)
        if c is not None:
            _CLASSES[name] = c
    return c


def _field(target, name):
    current = target.getClass() if target else None
    while current:
        try:
            f = current.getDeclaredField(name)
            f.setAccessible(True)
            return f.get(target)
        except Exception:
            current = current.getSuperclass()
    return None


def _dp(value):
    return int(_cls("org.telegram.messenger.AndroidUtilities").dp(float(value)))


def _signed(color):
    color = int(color)
    return color - 0x100000000 if color >= 0x80000000 else color


def _drawable_res(name):
    try:
        R = _cls("org.telegram.messenger.R")
        return int(getattr(R.drawable, name, 0) or 0)
    except Exception:
        return 0


def is_exteraless_sheet(sheet):
    try:
        return sheet is not None and str(sheet.getClass().getName()) == EXTERALESS_SHEET
    except Exception:
        return False


def _layout(sheet):
    root = _field(sheet, "containerView")
    scroll = _field(sheet, "scroll")
    if root is None or scroll is None or scroll.getChildCount() == 0:
        return None
    content = scroll.getChildAt(0)
    TextView = _cls("android.widget.TextView")
    LinearLayout = _cls("android.widget.LinearLayout")
    bar = None
    for i in range(root.getChildCount()):
        child = root.getChildAt(i)
        if child != scroll:
            bar = child
    button = None
    if bar is not None and hasattr(bar, "getChildCount"):
        for i in range(bar.getChildCount()):
            child = bar.getChildAt(i)
            if str(child.getTag()) == "kpm_split_container" and child.getChildCount() > 0:
                button = child.getChildAt(0)
                break
            if isinstance(child, TextView):
                button = child
                break
    header = None
    titles = None
    description = None
    for i in range(content.getChildCount()):
        child = content.getChildAt(i)
        if header is None:
            if (isinstance(child, LinearLayout) and child.getOrientation() == LinearLayout.HORIZONTAL
                    and child.getChildCount() >= 2 and isinstance(child.getChildAt(1), LinearLayout)):
                header = child
                titles = child.getChildAt(1)
            continue
        if isinstance(child, TextView) and "LinksTextView" in str(child.getClass().getName()):
            description = child
        break
    return {
        "root": root,
        "content": content,
        "bar": bar,
        "button": button,
        "header": header,
        "titles": titles,
        "description": description,
        "colors": _field(sheet, "colors"),
    }


def _resolve_ids(sheet, pending_file_path):
    file_path = ""
    try:
        params = _field(sheet, "params")
        if params is not None:
            file_path = str(params.getFilePath() or "")
    except Exception:
        pass
    if not file_path and pending_file_path:
        file_path = pending_file_path
    plugin_id = None
    try:
        p = _field(sheet, "plugin")
        if p is not None and p.getId():
            plugin_id = str(p.getId())
    except Exception:
        pass
    if not plugin_id and file_path:
        try:
            from ..elyx_parser import read_plugin_id_from_file
            plugin_id = read_plugin_id_from_file(file_path)
        except Exception:
            pass
    return file_path, plugin_id


def _setup_split_button(plugin, sheet, views, file_path, plugin_id):
    bar = views["bar"]
    button = views["button"]
    colors = views["colors"]
    if bar is None or button is None or colors is None:
        return False
    parent = button.getParent()
    if parent is not None and str(parent.getTag()) == "kpm_split_container":
        return True

    from ..views.sheets.analysis_sheet import create_split_analysis_button, show_analysis_choice, PluginAnalysisSheet
    from .install_top_actions import _on_report_click
    LinearLayout = _cls("android.widget.LinearLayout")
    FrameLayout = _cls("android.widget.FrameLayout")
    LayoutHelper = _cls("org.telegram.ui.Components.LayoutHelper")
    Theme = _cls("org.telegram.ui.ActionBar.Theme")
    ColorUtils = _cls("androidx.core.graphics.ColorUtils")

    context = bar.getContext()
    primary = _signed(colors.primary)
    on_primary = _signed(colors.onPrimary)
    resources_provider = _field(sheet, "resourcesProvider")

    def shield_choice(v):
        try:
            show_analysis_choice(
                sheet,
                lambda: PluginAnalysisSheet(context, file_path, plugin_id, resources_provider).show(),
                lambda: _on_report_click(sheet, plugin, file_path, plugin_id),
            )
        except Exception as e:
            log_error(e, context="exteraless shield choice")

    analysis = create_split_analysis_button(
        context, file_path, plugin_id, resources_provider,
        bg_color=primary, text_color=on_primary, sheet=sheet, on_click=shield_choice, radius=28,
    )
    if analysis is None:
        return False

    orig_lp = button.getLayoutParams()
    parent.removeView(button)

    split = LinearLayout(context)
    split.setTag("kpm_split_container")
    split.setOrientation(LinearLayout.HORIZONTAL)

    pressed = _signed(ColorUtils.blendARGB(primary, on_primary, 0.16))
    button.setBackground(Theme.createSimpleSelectorRoundRectDrawable(
        _dp(28), _dp(4), _dp(4), _dp(28), jint(primary), jint(pressed), jint(pressed)))
    split.addView(button, LayoutHelper.createLinear(0, 56, 1.0, 0, 0, 3, 0))
    split.addView(analysis, LayoutHelper.createLinear(56, 56))

    if orig_lp is not None and isinstance(orig_lp, FrameLayout.LayoutParams):
        bar.addView(split, FrameLayout.LayoutParams(orig_lp))
    else:
        bar.addView(split, LayoutHelper.createFrame(-1, 56.0, 48, 24.0, 12.0, 24.0, 16.0))
    return True


def _make_chip(context, colors, icon_name, text):
    TextView = _cls("android.widget.TextView")
    AndroidUtilities = _cls("org.telegram.messenger.AndroidUtilities")
    Theme = _cls("org.telegram.ui.ActionBar.Theme")
    ColorUtils = _cls("androidx.core.graphics.ColorUtils")
    PorterDuff = _cls("android.graphics.PorterDuff")
    PorterDuffColorFilter = _cls("android.graphics.PorterDuffColorFilter")

    fg = _signed(colors.onSecondaryContainer)
    bg = _signed(colors.secondaryContainer)
    chip = TextView(context)
    chip.setTextSize(1, 14.0)
    chip.setTypeface(AndroidUtilities.bold())
    chip.setTextColor(jint(fg))
    chip.setGravity(16)
    chip.setSingleLine(True)
    chip.setText(str(text))
    icon_res = _drawable_res(icon_name)
    chip.setPadding(_dp(12 if icon_res else 16), 0, _dp(16), 0)
    if icon_res:
        try:
            icon = context.getResources().getDrawable(jint(icon_res)).mutate()
            icon.setColorFilter(PorterDuffColorFilter(jint(fg), PorterDuff.Mode.SRC_IN))
            icon.setBounds(0, 0, _dp(18), _dp(18))
            chip.setCompoundDrawables(icon, None, None, None)
            chip.setCompoundDrawablePadding(_dp(8))
        except Exception:
            pass
    pressed = _signed(ColorUtils.blendARGB(bg, fg, 0.12))
    chip.setBackground(Theme.createSimpleSelectorRoundRectDrawable(_dp(18), jint(bg), jint(pressed)))
    return chip


def _chips_row(views):
    content = views["content"]
    existing = content.findViewWithTag("kpm_chips_row")
    if existing is not None:
        return existing.getChildAt(0)
    HorizontalScrollView = _cls("android.widget.HorizontalScrollView")
    LinearLayout = _cls("android.widget.LinearLayout")
    LayoutHelper = _cls("org.telegram.ui.Components.LayoutHelper")
    context = content.getContext()
    scroller = HorizontalScrollView(context)
    scroller.setTag("kpm_chips_row")
    scroller.setHorizontalScrollBarEnabled(False)
    scroller.setOverScrollMode(2)
    row = LinearLayout(context)
    row.setOrientation(LinearLayout.HORIZONTAL)
    scroller.addView(row, LayoutHelper.createFrame(-2, 36.0))
    anchor = views["description"] or views["header"]
    index = content.indexOfChild(anchor) + 1 if anchor is not None else content.getChildCount()
    after_chips = False
    while index < content.getChildCount():
        child = content.getChildAt(index)
        if not (isinstance(child, LinearLayout) and child.getOrientation() == LinearLayout.HORIZONTAL):
            break
        after_chips = True
        index += 1
    content.addView(scroller, index, LayoutHelper.createLinear(-1, 36, 0, 8 if after_chips else 16, 0, 0))
    return row


def _add_chip(views, tag, icon_name, text, on_click):
    row = _chips_row(views)
    if row.findViewWithTag(tag) is not None:
        return
    LayoutHelper = _cls("org.telegram.ui.Components.LayoutHelper")
    chip = _make_chip(row.getContext(), views["colors"], icon_name, text)
    chip.setTag(tag)
    chip.setOnClickListener(OnClickListener(lambda v: on_click()))
    if row.getChildCount() > 0:
        row.addView(chip, LayoutHelper.createLinear(-2, 36, 8, 0, 0, 0))
    else:
        row.addView(chip, LayoutHelper.createLinear(-2, 36))


def _install_dependencies(plugin, deps):
    success = 0
    failed = 0
    for dep_id in deps:
        try:
            plugin.install_plugin_by_id(dep_id, enable_after=True)
            success += 1
        except Exception as e:
            log_error(e, context=f"exteraless deps install {dep_id}")
            failed += 1
    if failed == 0:
        title = _tr("dep_install_complete").format(success, len(deps))
    else:
        title = _tr("dep_install_error").format(success, len(deps), failed)

    def show():
        try:
            from ui.bulletin import BulletinHelper
            BulletinHelper.show_info(title)
        except Exception as e:
            log_error(e, context="exteraless deps bulletin")

    run_on_ui_thread(show)


def _verdict_color(sheet, views, verdict):
    Theme = _cls("org.telegram.ui.ActionBar.Theme")
    colors = views["colors"]
    if verdict == "verified":
        try:
            if Theme.isCurrentThemeMonet():
                return _signed(colors.primary)
        except Exception:
            pass
        return _signed(Theme.getColor(Theme.key_windowBackgroundWhiteGreenText))
    if verdict == "failed":
        return _signed(Theme.getColor(Theme.key_text_RedRegular))
    return _signed(colors.onSurfaceVariant)


def _add_certificate_card(plugin, sheet, views, file_path, plugin_id, verdict):
    content = views["content"]
    colors = views["colors"]
    if content.findViewWithTag("kpm_cert_card") is not None:
        return
    from ..views.sheets.analysis_sheet import PluginAnalysisSheet, _shield_drawable
    LinearLayout = _cls("android.widget.LinearLayout")
    TextView = _cls("android.widget.TextView")
    ImageView = _cls("android.widget.ImageView")
    LayoutHelper = _cls("org.telegram.ui.Components.LayoutHelper")
    AndroidUtilities = _cls("org.telegram.messenger.AndroidUtilities")
    Theme = _cls("org.telegram.ui.ActionBar.Theme")
    ColorUtils = _cls("androidx.core.graphics.ColorUtils")
    ScaleStateListAnimator = _cls("org.telegram.ui.Components.ScaleStateListAnimator")

    key = {"verified": "security_verified", "failed": "security_failed"}.get(verdict, "security_unknown")
    parts = str(_tr(key)).split("\n", 1)
    accent = _verdict_color(sheet, views, verdict)
    surface = _signed(colors.surfaceContainer)
    bg = _signed(ColorUtils.blendARGB(surface, accent, 0.12))
    pressed = _signed(ColorUtils.blendARGB(bg, accent, 0.12))

    context = content.getContext()
    card = LinearLayout(context)
    card.setTag("kpm_cert_card")
    card.setOrientation(LinearLayout.HORIZONTAL)
    card.setGravity(16)
    card.setPadding(_dp(16), _dp(14), _dp(16), _dp(14))
    card.setBackground(Theme.createSimpleSelectorRoundRectDrawable(_dp(24), jint(bg), jint(pressed)))

    icon = ImageView(context)
    icon.setImageDrawable(_shield_drawable(context, 24, accent, status=verdict))
    icon.setScaleType(ImageView.ScaleType.CENTER)
    card.addView(icon, LayoutHelper.createLinear(24, 24, 16, 0, 0, 14, 0))

    texts = LinearLayout(context)
    texts.setOrientation(LinearLayout.VERTICAL)
    title = TextView(context)
    title.setText(parts[0])
    title.setTypeface(AndroidUtilities.bold())
    title.setTextSize(1, 14.0)
    title.setTextColor(jint(accent))
    texts.addView(title, LayoutHelper.createLinear(-1, -2))
    if len(parts) > 1 and parts[1]:
        sub = TextView(context)
        sub.setText(parts[1])
        sub.setTextSize(1, 12.0)
        sub.setTextColor(jint(_signed(colors.onSurfaceVariant)))
        texts.addView(sub, LayoutHelper.createLinear(-1, -2, 0, 2, 0, 0))
    card.addView(texts, LayoutHelper.createLinear(0, -2, 1.0, 16))

    def on_click():
        try:
            PluginAnalysisSheet(context, file_path, plugin_id, _field(sheet, "resourcesProvider")).show()
        except Exception as e:
            log_error(e, context="exteraless cert click")

    card.setOnClickListener(OnClickListener(lambda v: on_click()))
    if ScaleStateListAnimator:
        try:
            ScaleStateListAnimator.apply(card, 0.02, 1.5)
        except Exception:
            pass

    anchor = views["description"] or views["header"]
    index = content.indexOfChild(anchor) + 1 if anchor is not None else content.getChildCount()
    content.addView(card, index, LayoutHelper.createLinear(-1, -2, 0, 16, 0, 0))


def _add_status_badge(views, label):
    titles = views["titles"]
    colors = views["colors"]
    if titles is None or titles.findViewWithTag("kpm_status_badge") is not None:
        return
    TextView = _cls("android.widget.TextView")
    AndroidUtilities = _cls("org.telegram.messenger.AndroidUtilities")
    LayoutHelper = _cls("org.telegram.ui.Components.LayoutHelper")
    Theme = _cls("org.telegram.ui.ActionBar.Theme")
    badge = TextView(titles.getContext())
    badge.setTag("kpm_status_badge")
    badge.setText(str(label))
    badge.setTypeface(AndroidUtilities.bold())
    badge.setTextSize(1, 12.0)
    badge.setIncludeFontPadding(False)
    badge.setTextColor(jint(_signed(colors.onPrimaryContainer)))
    badge.setPadding(_dp(8), _dp(4), _dp(8), _dp(4))
    badge.setBackground(Theme.createRoundRectDrawable(_dp(8), jint(_signed(colors.primaryContainer))))
    titles.addView(badge, LayoutHelper.createLinear(-2, -2, 0, 6, 0, 0))


def _store_info(plugin, plugin_id):
    if not plugin_id:
        return None, None, []
    if not plugin.plugins_list:
        try:
            plugin.store.refresh(force=True)
        except Exception as e:
            log_error(e, context="exteraless store refresh")
    store = plugin.plugins_list or {}
    key = next((k for k in store if k.lower() == plugin_id.lower()), None)
    if not key:
        return None, None, []
    info = store.get(key)
    status = info.get("status") if isinstance(info, dict) else None
    deps = list(info.get("dependencies", []) or []) if isinstance(info, dict) else []
    if not status:
        try:
            full = plugin.store.fetch_plugin_full_info(plugin_id)
            if isinstance(full, dict):
                status = full.get("status")
        except Exception:
            pass
    return key, status, deps


def handle_exteraless_sheet(plugin, sheet, pending_file_path=None):
    try:
        views = _layout(sheet)
        if views is None or views["colors"] is None:
            return False
        bar = views["bar"]
        if bar is not None and bar.findViewWithTag("kpm_split_container") is not None:
            return True
        file_path, plugin_id = _resolve_ids(sheet, pending_file_path)
        log(f"[KPM] exteraless sheet: pid={plugin_id}, fp={file_path}")

        _setup_split_button(plugin, sheet, views, file_path, plugin_id)

        def background():
            try:
                key, status, deps = _store_info(plugin, plugin_id)
                verdict = plugin._get_plugin_verdict(plugin_id, file_path) if plugin_id and file_path else "unknown"
            except Exception as e:
                log_error(e, context="exteraless sheet background")
                return

            def apply():
                try:
                    if key:
                        _add_status_badge(views, _status_label(status))
                    if deps:
                        _add_chip(views, "kpm_deps_btn", "msg_download", _tr("install_dependencies"),
                                  lambda: run_on_queue(lambda: _install_dependencies(plugin, deps)))
                    _add_certificate_card(plugin, sheet, views, file_path, plugin_id, verdict)
                except Exception as e:
                    log_error(e, context="exteraless sheet apply")

            run_on_ui_thread(apply)

        run_on_queue(background)
        return True
    except Exception as e:
        log_error(e, context="handle_exteraless_sheet")
        return False
