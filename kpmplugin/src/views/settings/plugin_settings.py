import os
from ui.settings import Header, Text, Switch, Selector, Input, Divider, Custom
from ...methods import open_link, open_username, _tr, log, log_error
from .icon_selector import IconSelector, MirrorSelector
from client_utils import run_on_queue


def create_settings(pl):
    try:
        return [
            Header(text=_tr("header")),
            Text(text=_tr("plugin_management"), icon="msg_addbot",
                 create_sub_fragment=lambda: _plugin_management(pl),
                 link_alias="plugin_management"),
            Text(text=_tr("ai_search"), icon="msg_bot",
                 create_sub_fragment=lambda: _ai_search(pl),
                 link_alias="ai_search"),
            Text(text=_tr("auto_updates"), icon="msg_retry",
                 create_sub_fragment=lambda: _auto_updates(pl),
                 link_alias="auto_updates"),
            Text(text=_tr("security"), icon="msg_permissions",
                 create_sub_fragment=lambda: _security(pl),
                 link_alias="security"),
            Text(text=_tr("ui_settings"), icon="msg_theme",
                 create_sub_fragment=lambda: _ui_settings(pl),
                 link_alias="ui_settings"),
            Text(text=_tr("help_section"), icon="msg_help",
                 create_sub_fragment=lambda: _help_section(pl),
                 link_alias="help_section"),
            Text(text=_tr("debug_menu"), icon="msg_log",
                 create_sub_fragment=lambda: _debug_section(pl),
                 link_alias="debug_menu"),
            Text(text=_tr("about_plugin"), icon="msg_info",
                 create_sub_fragment=lambda: _about(pl),
                 link_alias="about_plugin"),
        ]
    except Exception as e:
        log_error(e, context="create_settings")
        return []


def _plugin_management(pl):
    try:
        return [
            Header(text=_tr("actions_header")),
            MirrorSelector(pl=pl, key="enabled_store_mirrors", text=_tr("store_mirrors"),
                           icon="msg_link",
                           on_change=lambda _: pl.update_store_mirror(),
                           link_alias="store_mirrors"),
            Text(text=_tr("refresh_list"), icon="msg_download",
                 on_click=lambda _: run_on_queue(lambda: pl.refresh_store()),
                 link_alias="refresh_list"),
            Text(text=_tr("install_plugin"), icon="msg_addbot",
                 on_click=lambda _: pl.open_install_dialog(),
                 link_alias="install_plugin"),
            Text(text=_tr("update_plugins"), icon="msg_retry",
                 on_click=lambda _: pl.open_update_dialog(),
                 link_alias="update_plugins"),
            Text(text=_tr("clear_cache"), icon="msg_clear",
                 on_click=lambda _: pl.clear_cache(),
                 link_alias="clear_cache"),
            Divider(),
            Header(text=_tr("plugin_collections")),
            Text(text=_tr("export_collection"), icon="msg_share",
                 on_click=lambda _: pl._show_export_collection_dialog(),
                 link_alias="export_collection"),
            Text(text=_tr("import_collection"), icon="msg_download",
                 on_click=lambda _: pl._show_import_collection_dialog(),
                 link_alias="import_collection"),
            Divider(),
            Header(text=_tr("ignored_updates")),
            Text(text=_tr("ignored_updates"), icon="msg_block",
                 on_click=lambda _: pl._show_ignored_updates_picker(),
                 link_alias="ignored_updates"),
        ]
    except Exception as e:
        log_error(e, context="_plugin_management")
        return [Header(text=_tr("plugin_management")), Text(text=f"Error: {e}", red=True)]


def _get_ai_routers_data(pl):
    try:
        from elyx import assets
        if hasattr(assets, "ai_routers"):
            asset = assets.ai_routers
            if hasattr(asset, "content_json"):
                return asset.content_json()
            if hasattr(asset, "content"):
                return asset.content()
        for cand in ("ai_routers.json", "ai_routers"):
            try:
                if hasattr(assets, "get"):
                    a = assets.get(cand)
                    if a:
                        if hasattr(a, "content_json"):
                            return a.content_json()
                        if hasattr(a, "content"):
                            return a.content()
            except Exception:
                pass
            try:
                a = assets[cand]
                if a:
                    if hasattr(a, "content_json"):
                        return a.content_json()
                    if hasattr(a, "content"):
                        return a.content()
            except Exception:
                pass
    except Exception as e:
        log_error(e, context="Error loading ai_routers from elyx.assets")

    try:
        import json
        plugin_path = getattr(pl, "path", None) or getattr(pl, "plugin_dir", "")
        if plugin_path:
            p = os.path.join(plugin_path, "res", "ai_routers.json")
            if os.path.exists(p):
                with open(p, "r", encoding="utf-8") as f:
                    return json.load(f)
    except Exception as e:
        log_error(e, context="Error loading ai_routers from plugin_path")

    try:
        import json
        here = os.path.dirname(__file__)
        p = os.path.normpath(os.path.join(here, "..", "..", "res", "ai_routers.json"))
        if os.path.exists(p):
            with open(p, "r", encoding="utf-8") as f:
                return json.load(f)
    except Exception as e:
        log_error(e, context="Error loading ai_routers from relative path")

    return None


def _ai_search(pl):
    try:
        data = _get_ai_routers_data(pl) or {}
        providers = data.get("providers", [])
        if not providers:
            log("[KPM] _ai_search: no providers found in ai_routers.json")
            return [
                Header(text=_tr("ai_search")),
                Text(text="Error: providers not found in ai_routers.json", icon="msg_error", red=True)
            ]

        provider_items = [
            {"text": p.get("title", p.get("id", "")), "subtitle": p.get("subtitle", ""), "icon": p.get("icon", "")}
            for p in providers
        ]

        provider_idx = int(pl.get_setting("ai_provider", 0) or 0)
        cur_provider = providers[provider_idx] if 0 <= provider_idx < len(providers) else (providers[0] if providers else {})
        pid = cur_provider.get("id", "mistral")

        models = cur_provider.get("models", [])
        model_idx = int(pl.get_setting(f"{pid}_model_idx", 0) or 0)
        is_custom_model = bool(models and model_idx == len(models) - 1 and models[-1].get("id") == "custom")

        def _localize_model_subtitle(raw_sub):
            if not raw_sub:
                return ""
            replacements = (
                ("Бесплатно", _tr("openrouter_sub_free")),
                ("Быстрая", _tr("openrouter_sub_fast")),
                ("Рассуждения", _tr("openrouter_sub_reasoning")),
                ("Кодинг", _tr("openrouter_sub_coding")),
            )
            res = raw_sub
            for src, tr in replacements:
                if tr and not tr.startswith("openrouter_sub_"):
                    res = res.replace(src, tr)
            return res

        model_items = []
        for m in models:
            sub_key = m.get("sub_key")
            sub_tr = _tr(sub_key) if sub_key else None
            if sub_tr and sub_tr != sub_key:
                sub = sub_tr
            else:
                sub = _localize_model_subtitle(m.get("subtitle", ""))
            model_items.append({
                "text": m.get("name", m.get("id", "")),
                "subtitle": sub,
                "icon": m.get("icon", "")
            })

        items = [
            Header(text=_tr("ai_search")),
            IconSelector(pl=pl, key="ai_provider", text=_tr("ai_provider"), default=0,
                         items=provider_items, icon="msg_bot", link_alias="ai_provider"),
            Divider(),
            Header(text=cur_provider.get("title", pid)),
        ]

        if models:
            items.append(
                IconSelector(pl=pl, key=f"{pid}_model_idx", text=_tr(f"{pid}_model"), default=0,
                             items=model_items, icon="msg_bot", link_alias=f"{pid}_model")
            )
            if is_custom_model:
                items.append(
                    Input(key=f"{pid}_custom_model", text=_tr(f"{pid}_custom_model"),
                          default="", icon="msg_edit",
                          subtext=_tr(f"{pid}_custom_model_sub"),
                          link_alias=f"{pid}_custom_model")
                )

        items.append(
            Input(key=f"{pid}_api_key", text=_tr(f"{pid}_api_key"),
                  default=cur_provider.get("default_key", ""), icon="msg_permissions",
                  subtext=_tr(f"{pid}_api_key_sub"),
                  link_alias=f"{pid}_api_key")
        )

        key_url = cur_provider.get("key_url")
        if key_url:
            items.append(
                Text(text=_tr(f"{pid}_get_key"), icon="msg_link",
                     on_click=lambda _: open_link(key_url),
                     link_alias=f"{pid}_get_key")
            )

        return items
    except Exception as e:
        log_error(e, context="_ai_search")
        return [
            Header(text=_tr("ai_search")),
            Text(text=f"Error: {e}", icon="msg_error", red=True)
        ]


def _auto_updates(pl):
    try:
        return [
            Header(text=_tr("updates_header")),
            Switch(key="auto_update_on_start", text=_tr("auto_update"), default=False,
                   icon="msg_autodelete", subtext=_tr("auto_update_sub"),
                   link_alias="auto_update"),
            Switch(key="auto_update_installed", text=_tr("auto_update_installed"), default=False,
                   icon="msg_retry", subtext=_tr("auto_update_installed_sub"),
                   link_alias="auto_update_installed"),
            Switch(key="auto_refresh_catalog", text=_tr("auto_refresh_catalog"), default=True,
                   icon="msg_download", subtext=_tr("auto_refresh_catalog_sub"),
                   link_alias="auto_refresh_catalog"),
        ]
    except Exception as e:
        log_error(e, context="_auto_updates")
        return [Header(text=_tr("auto_updates")), Text(text=f"Error: {e}", red=True)]


def _security(pl):
    try:
        return [
            Header(text=_tr("security")),
            Switch(key="block_untrusted_plugins", text=_tr("block_untrusted"), default=True,
                   icon="msg_block", subtext=_tr("block_untrusted_sub"),
                   link_alias="block_untrusted"),
            Switch(key="enforce_version_requirements", text=_tr("version_guard"), default=True,
                   icon="msg_report_other", subtext=_tr("version_guard_sub"),
                   link_alias="version_guard"),
            Divider(),
            Header(text=_tr("submit_bot")),
            Text(text=_tr("submit_bot_sub"), icon="msg_info",
                 link_alias="submit_bot_info"),
            Selector(key="submit_bot_choice", text=_tr("submit_bot"), default=0,
                     items=["@CodeAnalyzeBot", "@pluggy_robot", _tr("submit_bot_custom")],
                     icon="msg_bot",
                     link_alias="submit_bot"),
            Input(key="submit_bot_custom_username", text=_tr("submit_bot_custom_sub"),
                  default="", icon="msg_edit", subtext=_tr("submit_bot_custom_sub"),
                  link_alias="submit_bot_custom"),
        ]
    except Exception as e:
        log_error(e, context="_security")
        return [Header(text=_tr("security")), Text(text=f"Error: {e}", red=True)]


def _ui_settings(pl):
    try:
        return _ui_entry_points(pl) + _ui_plugin_list(pl) + _ui_inline_search(pl) + _ui_pill(pl) + _ui_beta_features(pl)
    except Exception as e:
        log_error(e, context="_ui_settings")
        return [Header(text=_tr("ui_settings")), Text(text=f"Error: {e}", red=True)]


def _ui_beta_features(pl):
    try:
        return [
            Divider(),
            Header(text=_tr("beta_features")),
            Switch(key="beta_code_viewer", text=_tr("beta_code_viewer"), default=False,
                   icon="msg_view_file", subtext=_tr("beta_code_viewer_sub"),
                   link_alias="beta_code_viewer"),
        ]
    except Exception as e:
        log_error(e, context="_ui_beta_features")
        return [Header(text=_tr("beta_features")), Text(text=f"Error: {e}", red=True)]


def _ui_entry_points(pl):
    try:
        return [
            Header(text=_tr("ui_entry_points")),
            Switch(key="show_drawer_menu", text=_tr("show_drawer_menu"), default=True,
                   icon="msg_addbot", subtext=_tr("show_drawer_menu_sub"),
                   on_change=lambda _: pl.add_drawer_menu_items(),
                   link_alias="show_drawer_menu"),
            Switch(key="show_settings_button", text=_tr("show_settings_button"), default=True,
                   icon="msg_settings_old", subtext=_tr("show_settings_button_sub"),
                   link_alias="show_settings_button"),
            Switch(key="show_bottom_tab", text=_tr("show_bottom_tab"), default=True,
                   icon="msg_plugins", subtext=_tr("show_bottom_tab_sub"),
                   on_change=lambda _: pl.refresh_bottom_tab(),
                   link_alias="show_bottom_tab"),
        ]
    except Exception as e:
        log_error(e, context="_ui_entry_points")
        return [Header(text=_tr("ui_entry_points")), Text(text=f"Error: {e}", red=True)]


def _ui_plugin_list(pl):
    try:
        return [
            Header(text=_tr("ui_plugin_list")),
            Switch(key="show_add_button", text=_tr("show_add_button"), default=True,
                   icon="msg_add", subtext=_tr("show_add_button_sub"),
                   link_alias="show_add_button"),
            Switch(key="show_update_button", text=_tr("show_update_button"), default=True,
                   icon="msg_retry", subtext=_tr("show_update_button_sub"),
                   link_alias="show_update_button"),
            Switch(key="show_plugin_bubbles", text=_tr("show_plugin_bubbles"), default=True,
                   icon="msg_sendfile", subtext=_tr("show_plugin_bubbles_sub"),
                   link_alias="show_plugin_bubbles"),
            Switch(key="smart_search", text=_tr("smart_search"), default=True,
                   icon="msg_search", subtext=_tr("smart_search_sub"),
                   link_alias="smart_search"),
            Switch(key="card_actions_submenu", text=_tr("card_actions_submenu"), default=False,
                   icon="msg_fave", subtext=_tr("card_actions_submenu_sub"),
                   link_alias="card_actions_submenu"),
        ]
    except Exception as e:
        log_error(e, context="_ui_plugin_list")
        return [Header(text=_tr("ui_plugin_list")), Text(text=f"Error: {e}", red=True)]


def _ui_inline_search(pl):
    try:
        return [
            Divider(),
            Header(text=_tr("inline_search_header")),
            Switch(key="inline_search_enabled", text=_tr("inline_search_enabled"), default=True,
                   icon="msg_bot", subtext=_tr("inline_search_enabled_sub"),
                   link_alias="inline_search_enabled"),
            Input(key="inline_search_trigger", text=_tr("inline_search_trigger"),
                  default="kpm", icon="msg_edit", subtext=_tr("inline_search_trigger_sub"),
                  link_alias="inline_search_trigger"),
        ]
    except Exception as e:
        log_error(e, context="_ui_inline_search")
        return [Header(text=_tr("inline_search_header")), Text(text=f"Error: {e}", red=True)]


def _ui_pill(pl):
    try:
        return [
            Divider(),
            Header(text=_tr("pill_header")),
            Switch(key="pill_enabled", text=_tr("pill_enabled"), default=True,
                   icon="msg_addbot", subtext=_tr("pill_enabled_sub"),
                   link_alias="pill_enabled"),
            Selector(key="pill_click_action", text=_tr("pill_click_action"), default=0,
                     items=[_tr("pill_action_install"), _tr("pill_action_update"),
                            _tr("pill_action_refresh"), _tr("pill_action_settings")],
                     icon="msg_actions",
                     link_alias="pill_click_action"),
        ]
    except Exception as e:
        log_error(e, context="_ui_pill")
        return [Header(text=_tr("pill_header")), Text(text=f"Error: {e}", red=True)]


def _help_section(pl):
    try:
        from ..sheets.guide import guide_settings_row
        from ..sheets.faq import open_faq
        return [
            Header(text=_tr("help_section")),
            guide_settings_row(pl),
            Text(text=_tr("faq"), icon="msg_help",
                 on_click=lambda _: open_faq(pl),
                 link_alias="faq"),
        ]
    except Exception as e:
        log_error(e, context="_help_section")
        return [Header(text=_tr("help_section")), Text(text=f"Error: {e}", red=True)]


def _debug_section(pl):
    try:
        from .debug_settings import build_debug_page
        return build_debug_page(pl)
    except Exception as e:
        log_error(e, context="PluginSettings._debug_section")
        return [Header(text=_tr("debug_menu")), Text(text=f"Error: {e}", red=True)]


def _about(pl):
    try:
        from .dev_cards import build_dev_cards_view
        return [
            Header(text=_tr("community")),
            Text(text=_tr("open_forum"), icon="msg_groups_solar",
                 on_click=lambda _: open_username("KangelPluginsManager"),
                 link_alias="forum"),
            Text(text=_tr("source_code"), icon="msg_link",
                 on_click=lambda _: open_link("https://git.kangel.xyz/KangelPlugins/PluginManager"),
                 link_alias="source_code"),
            Text(text=_tr("send_to_bot"), icon="msg_forward",
                 on_click=lambda _: open_username("KPMAppealBot"),
                 link_alias="appeal_bot"),
            Divider(),
            Header(text=_tr("developers")),
            Custom(view=build_dev_cards_view(pl)),
            Divider(),
            Text(text=pl._get_plugin_info_text(), icon="filled_info",
                 on_click=lambda _: pl._show_about_dialog()),
        ]
    except Exception as e:
        log_error(e, context="_about")
        return [Header(text=_tr("about_plugin")), Text(text=f"Error: {e}", red=True)]


def _tr(key):
    from ...methods import _tr as _mtr
    return _mtr(key)
