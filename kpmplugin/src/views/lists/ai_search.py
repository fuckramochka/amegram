import os
import json
from android_utils import run_on_ui_thread
from client_utils import run_on_queue

from ...methods import _tr, log, log_error


def _build_ai_plugin_index(pl):
    try:
        index = []
        for pid, info in pl.plugins_list.items():
            if isinstance(info, dict):
                name = info.get("name", pid)
                desc = info.get("description", "")
                author = info.get("author", "")
                status = info.get("status", "plugin")
                if len(desc) > 350:
                    desc = desc[:350] + "..."
                index.append({
                    "id": pid,
                    "name": name,
                    "description": desc,
                    "author": author,
                    "status": status
                })
        return index
    except Exception as e:
        log_error(e, context="AI index build")
        return []


def _call_mistral_ai_search(pl, query, plugin_index):
    try:
        import requests
        import time

        plugin_json = json.dumps(plugin_index, ensure_ascii=False)

        available_statuses = sorted(set(
            (p.get("status") or "plugin") for p in plugin_index
        ))
        statuses_str = ", ".join(available_statuses) if available_statuses else "plugin"

        prompt_template = ""
        try:
            from elyx import assets
            if hasattr(assets, "promt") and hasattr(assets.promt, "content_string"):
                prompt_template = assets.promt.content_string()
            elif hasattr(assets, "get"):
                asset = assets.get("promt.txt") or assets.get("promt")
                if asset and hasattr(asset, "content_string"):
                    prompt_template = asset.content_string()
        except Exception as e:
            log_error(e, context="[AI-Search] Loading prompt from assets")

        if not prompt_template:
            for cand in [
                os.path.join(getattr(pl, "plugin_path", ""), "res", "promt.txt"),
                os.path.normpath(os.path.join(os.path.dirname(__file__), "..", "..", "res", "promt.txt")),
            ]:
                if cand and os.path.exists(cand):
                    try:
                        with open(cand, "r", encoding="utf-8") as f:
                            prompt_template = f.read()
                        if prompt_template:
                            break
                    except Exception as e:
                        log_error(e, context=f"[AI-Search] Error reading prompt from {cand}")

        if not prompt_template:
            prompt_template = (
                "You are a plugin search assistant for KPM. Valid statuses: {statuses}.\n"
                "Return ONLY {{\"plugins\": [\"id1\", \"id2\"]}} ordered by relevance.\n"
                "Available plugins:\n{plugins}"
            )

        system_prompt = prompt_template.format(statuses=statuses_str, plugins=plugin_json)

        from ..settings.plugin_settings import _get_ai_routers_data
        data = _get_ai_routers_data(pl) or {}
        providers = data.get("providers", [])
        provider_idx = int(pl.get_setting("ai_provider", 0) or 0)
        provider = providers[provider_idx] if 0 <= provider_idx < len(providers) else (providers[0] if providers else {})
        pid = provider.get("id", "mistral")
        provider_name = provider.get("title", pid)

        api_key = str(pl.get_setting(f"{pid}_api_key", provider.get("default_key", "")) or "").strip()
        if not api_key:
            err_key = f"{pid}_key_required"
            err_msg = _tr(err_key)
            if err_msg == err_key:
                err_msg = f"Please specify {provider_name} API key in settings"
            log(f"[KPM] [AI-Search] Missing API key for {provider_name}")
            return None, err_msg

        models = provider.get("models", [])
        model_idx = int(pl.get_setting(f"{pid}_model_idx", 0) or 0)
        if models and 0 <= model_idx < len(models) - 1:
            model = models[model_idx]["id"]
        elif models and model_idx == len(models) - 1 and models[-1].get("id") == "custom":
            custom = str(pl.get_setting(f"{pid}_custom_model", "") or "").strip()
            model = custom or provider.get("default_model", "")
        else:
            custom = str(pl.get_setting(f"{pid}_custom_model", "") or "").strip()
            model = custom or str(pl.get_setting(f"{pid}_model", "") or "").strip() or provider.get("default_model", "")

        if pid == "mistral" and (not model or model == "mistral-small-latest"):
            model = "ministral-8b-latest"

        api_url = provider.get("url", "")
        headers = {
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json"
        }
        extra_headers = provider.get("extra_headers")
        if isinstance(extra_headers, dict):
            headers.update(extra_headers)

        payload = {
            "model": model,
            "messages": [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": f"Find plugins matching: {query}"}
            ],
            "temperature": 0.0,
            "max_tokens": 4096
        }
        extra_payload = provider.get("extra_payload")
        if isinstance(extra_payload, dict):
            payload.update(extra_payload)

        log(f"[KPM] [AI-Search] Requesting {provider_name} | model='{model}' | query='{query}' | index={len(plugin_index)} plugins...")
        t0 = time.time()
        response = requests.post(api_url, headers=headers, json=payload, timeout=35)
        elapsed = time.time() - t0
        log(f"[KPM] [AI-Search] {provider_name} HTTP {response.status_code} in {elapsed:.2f}s")

        if response.status_code == 429 and pid == "mistral" and model != "ministral-8b-latest":
            log(f"[KPM] [AI-Search] Model '{model}' rate limited (429)! Retrying with fallback 'ministral-8b-latest'...")
            payload["model"] = "ministral-8b-latest"
            t0 = time.time()
            response = requests.post(api_url, headers=headers, json=payload, timeout=35)
            elapsed = time.time() - t0
            log(f"[KPM] [AI-Search] Fallback ministral-8b-latest HTTP {response.status_code} in {elapsed:.2f}s")

        if response.status_code == 429:
            log(f"[KPM] [AI-Search] Rate limit exceeded on {provider_name}")
            return None, _tr("ai_search_error").format("Rate limit")
        if response.status_code != 200:
            log(f"[KPM] [AI-Search] Error response: {response.status_code} - {response.text[:200]}")
            return None, _tr("ai_search_error").format(f"HTTP {response.status_code}")

        data = response.json()
        if not isinstance(data, dict):
            return None, _tr("ai_search_error").format("Invalid response from provider")

        if "error" in data:
            err = data.get("error")
            err_msg = err.get("message") if isinstance(err, dict) else str(err)
            log(f"[KPM] [AI-Search] API returned error in JSON: {err_msg}")
            return None, _tr("ai_search_error").format(err_msg)

        choices = data.get("choices")
        if not choices or not isinstance(choices, list):
            log(f"[KPM] [AI-Search] No choices in response: {str(data)[:300]}")
            return None, _tr("ai_search_error").format("Empty response")

        first_choice = choices[0] if isinstance(choices[0], dict) else {}
        finish_reason = first_choice.get("finish_reason")
        if finish_reason:
            log(f"[KPM] [AI-Search] Choice finish_reason: '{finish_reason}'")

        content = None
        message = first_choice.get("message")
        if isinstance(message, dict):
            content = message.get("content")
            if not content:
                content = (
                    message.get("reasoning")
                    or message.get("reasoning_content")
                    or message.get("thought")
                )
        if not content:
            content = first_choice.get("text")

        if content is None:
            content = ""
        else:
            content = str(content)

        log(f"[KPM] [AI-Search] Response content received (len={len(content)})")
        if not content.strip():
            log(f"[KPM] [AI-Search] Empty content. Full first_choice: {str(first_choice)[:400]}")
            return None, _tr("ai_search_error").format("Empty response from model")

        cleaned = content.strip()
        if "</think>" in cleaned:
            parts = cleaned.split("</think>", 1)
            cleaned = parts[1].strip() or parts[0].strip()
        if "```json" in cleaned:
            cleaned = cleaned.split("```json", 1)[1].split("```", 1)[0].strip()
        elif "```" in cleaned:
            cleaned = cleaned.split("```", 1)[1].split("```", 1)[0].strip()
        else:
            s_idx = cleaned.find("{")
            e_idx = cleaned.rfind("}")
            if s_idx != -1 and e_idx != -1 and e_idx > s_idx:
                cleaned = cleaned[s_idx:e_idx+1]

        ids = []
        try:
            result = json.loads(cleaned)
            ids = result.get("plugins", [])
        except Exception as json_err:
            log(f"[KPM] [AI-Search] json.loads failed ({json_err}), attempting auto-repair/regex...")
            import re
            s = cleaned.strip()
            if s.endswith(","):
                s = s[:-1].strip()
            if not s.endswith("]"):
                s += "]}"
            try:
                result = json.loads(s)
                ids = result.get("plugins", [])
            except Exception:
                matches = re.findall(r'"([a-zA-Z0-9_\-\.]+)"', cleaned)
                ids = [x for x in matches if x not in ("plugins", "id", "name", "status", "message")]

        if not isinstance(ids, list):
            ids = [ids]
        ids = [str(x) for x in ids]
        seen = set()
        unique_ids = [x for x in ids if not (x in seen or seen.add(x))]
        valid_ids = [pid for pid in unique_ids if pid in pl.plugins_list]
        log(f"[KPM] [AI-Search] Model returned {len(ids)} IDs ({len(unique_ids)} unique) -> {len(valid_ids)} matched in store: {valid_ids}")
        return valid_ids, None
    except Exception as e:
        log_error(e, context="[AI-Search] _call_mistral_ai_search")
        return None, _tr("ai_search_error").format(str(e))


def _get_current_ai_info(pl):
    try:
        from ..settings.plugin_settings import _get_ai_routers_data
        data = _get_ai_routers_data(pl) or {}
        providers = data.get("providers", [])
        provider_idx = int(pl.get_setting("ai_provider", 0) or 0)
        provider = providers[provider_idx] if 0 <= provider_idx < len(providers) else (providers[0] if providers else {})
        pid = provider.get("id", "mistral")
        pname = provider.get("title", pid)
        models = provider.get("models", [])
        model_idx = int(pl.get_setting(f"{pid}_model_idx", 0) or 0)
        if models and 0 <= model_idx < len(models) - 1:
            mname = models[model_idx].get("name", models[model_idx].get("id", ""))
        elif models and model_idx == len(models) - 1 and models[-1].get("id") == "custom":
            custom = str(pl.get_setting(f"{pid}_custom_model", "") or "").strip()
            mname = custom or "Custom"
        else:
            custom = str(pl.get_setting(f"{pid}_custom_model", "") or "").strip()
            mname = custom or str(pl.get_setting(f"{pid}_model", "") or "").strip() or provider.get("default_model", "")
        if pid == "mistral" and (not mname or "mistral-small" in mname):
            mname = "Ministral 8B"
        return pname, mname
    except Exception:
        return "Mistral AI", "Ministral 8B"



def _run_ai_search(pl, delegate, query, on_finish=None, progress_dialog=None):
    try:
        from ui.bulletin import BulletinHelper
        log(f"[KPM] [AI-Search] Task started for query='{query}'")

        def _dismiss_progress():
            if progress_dialog:
                try:
                    run_on_ui_thread(lambda: progress_dialog.dismiss())
                except Exception:
                    pass

        if not pl.plugins_list:
            log("[KPM] [AI-Search] Store cache empty, refreshing from store...")
            try:
                pl.store.refresh(force=False)
                log(f"[KPM] [AI-Search] Store refreshed: {len(pl.plugins_list)} plugins loaded")
            except Exception as e:
                log_error(e, context="[AI-Search] Store refresh")

        plugin_index = _build_ai_plugin_index(pl)
        log(f"[KPM] [AI-Search] Built search index: {len(plugin_index)} plugins")
        if not plugin_index:
            log("[KPM] [AI-Search] Search aborted: plugin_index is empty")
            _dismiss_progress()
            def on_no_index():
                if on_finish:
                    on_finish(False)
                BulletinHelper.show_error(_tr("ai_search_error").format("No plugins loaded"))
            run_on_ui_thread(on_no_index)
            return

        result_ids, error = _call_mistral_ai_search(pl, query, plugin_index)
        _dismiss_progress()
        if error:
            log(f"[KPM] [AI-Search] Search failed with error: {error}")
            def on_err():
                if on_finish:
                    on_finish(False)
                BulletinHelper.show_error(error)
            run_on_ui_thread(on_err)
            return

        if not result_ids:
            log("[KPM] [AI-Search] Search completed with 0 results")
            def apply_empty():
                if on_finish:
                    on_finish(True)
                delegate.search_query = None
                delegate.ai_result_ids = []
                if delegate.adapter:
                    delegate.adapter.update(True)
                if hasattr(delegate, "_apply_header_label") and callable(delegate._apply_header_label):
                    try:
                        delegate._apply_header_label()
                    except Exception:
                        pass
                BulletinHelper.show_info(_tr("ai_search_no_results"))
            run_on_ui_thread(apply_empty)
            return

        log(f"[KPM] [AI-Search] Search successful! Applying {len(result_ids)} plugins: {result_ids}")
        def apply_results():
            if on_finish:
                on_finish(True)
            delegate.search_query = None
            delegate.ai_result_ids = list(result_ids)
            if delegate.adapter:
                delegate.adapter.update(True)
            if hasattr(delegate, "_apply_header_label") and callable(delegate._apply_header_label):
                try:
                    delegate._apply_header_label()
                except Exception:
                    pass
            msg = _tr("ai_search_found")
            if msg == "ai_search_found" or "{}" not in msg:
                msg = f"Found {len(result_ids)} plugins"
            else:
                msg = msg.format(len(result_ids))
            BulletinHelper.show_success(msg)
        run_on_ui_thread(apply_results)
    except Exception as e:
        if progress_dialog:
            try:
                run_on_ui_thread(lambda: progress_dialog.dismiss())
            except Exception:
                pass
        log_error(e, context="[AI-Search] Unhandled run")
        from ui.bulletin import BulletinHelper
        def on_fatal():
            if on_finish:
                on_finish(False)
            BulletinHelper.show_error(_tr("ai_search_error").format(str(e)))
        run_on_ui_thread(on_fatal)


def show_ai_search_dialog(frag, delegate, pl, update_subtitle_fn=None):
    try:
        act = frag.getParentActivity()
        if not act:
            return
        log("[KPM] [AI-Search] User opened search dialog (center)")
        from android.widget import EditText, LinearLayout, TextView
        from android.util import TypedValue
        from ui.alert import AlertDialogBuilder
        from org.telegram.ui.ActionBar import Theme
        from org.telegram.messenger import AndroidUtilities
        from org.telegram.ui.Components import LayoutHelper

        container = LinearLayout(act)
        container.setOrientation(LinearLayout.VERTICAL)
        container.setPadding(
            AndroidUtilities.dp(24), AndroidUtilities.dp(4),
            AndroidUtilities.dp(24), AndroidUtilities.dp(8)
        )

        try:
            pname, mname = _get_current_ai_info(pl)
            if pname and mname:
                info_tv = TextView(act)
                info_tv.setText(f"{pname} · {mname}")
                info_tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13)
                try:
                    info_tv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText))
                except Exception:
                    pass
                container.addView(info_tv, LayoutHelper.createLinear(-1, -2, 0, 0, 0, 8))
        except Exception:
            pass

        def _to_jint(color: int) -> int:
            if color > 0x7FFFFFFF:
                return color - 0x100000000
            return color

        try:
            from org.telegram.ui.Components import EditTextBoldCursor
            edit_text = EditTextBoldCursor(act)
        except Exception:
            edit_text = EditText(act)

        edit_text.setHint(_tr("ai_search_hint"))
        edit_text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16)
        try:
            edit_text.setSingleLine(True)
        except Exception:
            pass
        try:
            edit_text.setTextColor(Theme.getColor(Theme.key_dialogTextBlack))
            edit_text.setHintTextColor(Theme.getColor(Theme.key_dialogTextGray))
        except Exception:
            pass

        try:
            accent_color = Theme.getColor(Theme.key_featuredStickers_addButton)
        except Exception:
            try:
                accent_color = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText)
            except Exception:
                accent_color = -8630785

        try:
            edit_text.setCursorColor(accent_color)
            edit_text.setCursorWidth(1.5)
        except Exception:
            pass

        try:
            from android.graphics.drawable import GradientDrawable
            edit_bg = GradientDrawable()
            edit_bg.setShape(GradientDrawable.RECTANGLE)
            edit_bg.setCornerRadius(float(AndroidUtilities.dp(10)))
            try:
                edit_bg.setColor(_to_jint(Theme.getColor(Theme.key_dialogBackground)))
            except Exception:
                edit_bg.setColor(0)
            edit_bg.setStroke(AndroidUtilities.dp(1.5), _to_jint(accent_color))
            edit_text.setBackground(edit_bg)
        except Exception as bge:
            log_error(bge, context="[AI-Search] stroke bg")
            try:
                edit_text.setBackground(Theme.createRoundRectDrawable(
                    AndroidUtilities.dp(8),
                    Theme.getColor(Theme.key_dialogBackground)
                ))
            except Exception:
                pass

        edit_text.setPadding(
            AndroidUtilities.dp(14), AndroidUtilities.dp(12),
            AndroidUtilities.dp(14), AndroidUtilities.dp(12)
        )

        if getattr(delegate, "_last_ai_query", None):
            try:
                edit_text.setText(delegate._last_ai_query)
                edit_text.setSelection(len(delegate._last_ai_query))
            except Exception:
                pass

        container.addView(edit_text, LayoutHelper.createLinear(-1, -2))

        builder = AlertDialogBuilder(act)
        builder.set_title(_tr("ai_search"))
        builder.set_view(container)

        def do_search(d, w):
            query = edit_text.getText().toString().strip()
            if not query:
                return
            delegate._last_ai_query = query
            log(f"[KPM] [AI-Search] User clicked search button with query: '{query}'")
            try:
                d.dismiss()
            except Exception:
                pass
            progress_dialog = None
            try:
                from org.telegram.ui.ActionBar import AlertDialog
                progress_dialog = AlertDialog(act, 3)
                progress_dialog.setCanCancel(True)
                progress_dialog.show()
            except Exception as pe:
                log_error(pe, context="[AI-Search] progress dialog")

            from ui.bulletin import BulletinHelper
            BulletinHelper.show_info(_tr("ai_search_loading"))
            if update_subtitle_fn:
                try:
                    frag.getActionBar().setSubtitle(f"{_tr('ai_search')}...")
                except Exception:
                    pass

            run_on_queue(lambda: _run_ai_search(
                pl, delegate, query,
                on_finish=lambda ok: update_subtitle_fn() if update_subtitle_fn else None,
                progress_dialog=progress_dialog
            ))

        builder.set_positive_button(_tr("ai_search_button"), do_search)
        builder.set_negative_button(_tr("cancel"), lambda d, w: d.dismiss())

        if delegate.ai_result_ids is not None:
            def do_reset(d, w):
                try:
                    d.dismiss()
                except Exception:
                    pass
                log("[KPM] [AI-Search] User reset AI search filter")
                delegate.ai_result_ids = None
                if delegate.adapter:
                    delegate.adapter.update(True)
                if update_subtitle_fn:
                    update_subtitle_fn()
                from ui.bulletin import BulletinHelper
                msg = _tr("ai_search_cleared")
                BulletinHelper.show_info("AI search reset" if msg == "ai_search_cleared" else msg)
            builder.set_neutral_button(_tr("ai_search_reset"), do_reset)

        builder.show()
        run_on_ui_thread(lambda: edit_text.requestFocus(), 200)
    except Exception as e:
        log_error(e, context="[AI-Search] Dialog")
