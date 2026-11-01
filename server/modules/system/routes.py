import time
from server.core.router import Router, Request
from server.core.response import json_response
from server.config import SERVER_NAME, SERVER_VERSION, FOUNDER_USER_ID
from server.modules.badges.service import get_all_active_badges

START_TIME = time.time()

def register_system_routes(router: Router):

    @router.get("/health")
    def health_check(req: Request):
        return json_response({
            "status": "online",
            "uptime_seconds": int(time.time() - START_TIME),
            "server": SERVER_NAME,
            "version": SERVER_VERSION
        })

    @router.get("/api/status")
    def system_status(req: Request):
        badges_count = len(get_all_active_badges())
        return json_response({
            "status": "online",
            "server": SERVER_NAME,
            "version": SERVER_VERSION,
            "founder_id": FOUNDER_USER_ID,
            "modules": {
                "badges": {
                    "enabled": True,
                    "active_badges": badges_count
                },
                "system": {
                    "enabled": True,
                    "uptime": int(time.time() - START_TIME)
                }
            }
        })
