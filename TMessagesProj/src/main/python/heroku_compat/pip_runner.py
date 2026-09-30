"""PIP bridge for Heroku/Hikka Userbot in Miogram."""

import json
import traceback
from typing import Any, Dict, List

import pip_controller


def run_pip(action: str, raw_args: str) -> str:
    """Executes pip actions (install, uninstall, list) and returns JSON string."""
    action = (action or "list").strip().lower()
    raw_args = (raw_args or "").strip()

    try:
        if action == "install":
            packages = raw_args.split()
            if not packages:
                return json.dumps({"success": False, "error": "No packages specified to install"})

            installed_list: List[Dict[str, str]] = []
            for pkg in packages:
                info = pip_controller.install_package(pkg, owner="userbot")
                installed_list.append(info)

            return json.dumps({
                "success": True,
                "action": "install",
                "installed": installed_list
            })

        elif action in ("uninstall", "remove"):
            packages = raw_args.split()
            if not packages:
                return json.dumps({"success": False, "error": "No packages specified to uninstall"})

            removed: List[str] = []
            not_found: List[str] = []
            for pkg in packages:
                ok = pip_controller.uninstall_package(pkg)
                if ok:
                    removed.append(pkg)
                else:
                    not_found.append(pkg)

            return json.dumps({
                "success": True,
                "action": "uninstall",
                "removed": removed,
                "not_found": not_found
            })

        elif action in ("list", ""):
            packages_map = pip_controller.installed_packages()
            items = []
            for name, meta in sorted(packages_map.items()):
                items.append({
                    "name": name,
                    "version": meta.get("version", "unknown"),
                    "path": meta.get("path", "")
                })
            return json.dumps({
                "success": True,
                "action": "list",
                "packages": items
            })

        else:
            return json.dumps({
                "success": False,
                "error": f"Unknown pip command: '{action}'. Available: install, uninstall, list."
            })

    except Exception as e:
        return json.dumps({
            "success": False,
            "error": str(e)
        })
