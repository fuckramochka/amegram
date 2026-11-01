from server.core.router import Router, Request
from server.core.response import json_response, error_response
from server.config import FOUNDER_SECRET, FOUNDER_USER_ID
from server.modules.badges import service

def register_badges_routes(router: Router):

    @router.get("/api/badges")
    def list_badges(req: Request):
        """Standard endpoint queried by Yumigram clients."""
        badges = service.get_all_active_badges()
        return json_response(badges)

    @router.get("/api/badges/{user_id}")
    def get_user_badge(req: Request):
        try:
            uid = int(req.params.get("user_id", 0))
        except ValueError:
            return error_response("Invalid user_id", status=400)
            
        badge = service.get_badge_by_id(uid)
        if not badge or not badge["is_active"]:
            return error_response("Badge not found", status=404, code="NOT_FOUND")
        return json_response(badge)

    @router.post("/api/badges/grant")
    def grant_badge(req: Request):
        """Secured endpoint to grant or update a community badge."""
        data = req.json()
        secret = data.get("secret") or req.headers.get("x-founder-secret") or req.headers.get("authorization")
        if secret and secret.startswith("Bearer "):
            secret = secret[7:]
            
        if secret != FOUNDER_SECRET:
            return error_response("Unauthorized: invalid founder secret", status=403, code="FORBIDDEN")

        try:
            target_uid = int(data.get("target_user_id") or data.get("user_id") or 0)
        except (ValueError, TypeError):
            return error_response("Invalid target_user_id", status=400)

        if target_uid <= 0:
            return error_response("target_user_id must be positive", status=400)

        badge_id = data.get("badge_id", "original")
        title = data.get("title")
        reason = data.get("reason") or data.get("obtained_reason")

        badge = service.grant_badge(
            user_id=target_uid,
            badge_id=badge_id,
            title=title,
            reason=reason,
            # Only the founder can reach this endpoint, so provenance is fixed
            # server-side instead of trusting a client-supplied grantor_id.
            grantor_id=FOUNDER_USER_ID
        )
        return json_response({
            "ok": True,
            "badge": badge,
            "message": f"Badge '{badge_id}' granted to user {target_uid}"
        })

    @router.post("/api/badges/revoke")
    def revoke_badge(req: Request):
        """Secured endpoint to revoke a badge."""
        data = req.json()
        secret = data.get("secret") or req.headers.get("x-founder-secret") or req.headers.get("authorization")
        if secret and secret.startswith("Bearer "):
            secret = secret[7:]
            
        if secret != FOUNDER_SECRET:
            return error_response("Unauthorized: invalid founder secret", status=403, code="FORBIDDEN")

        try:
            target_uid = int(data.get("target_user_id") or data.get("user_id") or 0)
        except (ValueError, TypeError):
            return error_response("Invalid target_user_id", status=400)

        service.revoke_badge(target_uid)
        return json_response({
            "ok": True,
            "message": f"Badge revoked for user {target_uid}"
        })

    @router.get("/api/badges/export")
    def export_badges_for_git(req: Request):
        """Returns JSON formatted for raw github badges.json mirror."""
        payload = service.export_github_json()
        return json_response(payload)
