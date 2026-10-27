"""Single entry point for UI. Layers: settings -> sheets -> lists.

Mixins/hooks import only from here, never deep paths, so internal
moves don't touch callers. Lazy: submodules load on first use,
same timing as before (nothing imports views at plugin load).
"""

_LAZY = {
    "show_plugin_list_fragment": ".lists.plugin_list",
    "show_export_picker": ".lists.pickers",
    "show_export_metadata": ".lists.pickers",
    "show_collection_picker": ".lists.pickers",
    "show_ignored_updates_picker": ".lists.pickers",
    "show_requirements_dialog": ".sheets.dialogs",
    "show_self_install_dialog": ".sheets.dialogs",
    "show_untrusted_warning": ".sheets.dialogs",
    "show_about_dialog": ".sheets.dialogs",
    "show_version_picker": ".sheets.dialogs",
    "show_md3_loading": ".sheets.dialogs",
    "dismiss_all_md3": ".sheets.dialogs",
    "show_import_progress": ".sheets.dialogs",
    "_install_version": ".sheets.dialogs",
    "show_import_collection_designer": ".sheets.designer",
    "open_faq": ".sheets.faq",
    "maybe_show_guide": ".sheets.guide",
    "attach_analysis_button": ".sheets.analysis_sheet",
    "setup_split_install_button": ".sheets.analysis_sheet",
    "apply_left_button_style": ".sheets.analysis_sheet",
    "create_settings": ".settings.plugin_settings",
}

__all__ = sorted(_LAZY)


def __getattr__(name):
    if name in _LAZY:
        import importlib
        return getattr(importlib.import_module(_LAZY[name], __name__), name)
    raise AttributeError(f"module {__name__!r} has no attribute {name!r}")
