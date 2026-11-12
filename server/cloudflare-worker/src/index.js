/**
 * Yumi Badges Cloudflare Worker
 * Ultra-fast Edge API for community badge resolution & admin management.
 * Works 24/7 globally with 0ms cold starts and free Cloudflare KV storage.
 */

const CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Authorization, X-Founder-Secret",
  "Content-Type": "application/json; charset=utf-8"
};

// Fallback initial badges if KV is empty
const INITIAL_BADGES = [
  {
    user_id: 8011880648,
    badge_id: "devil",
    title: "Yumi Creator ໒꒱",
    obtained_reason: "Засновник та головний архітектор клієнта Yumi",
    obtained_at: "2026-09-01",
    is_active: true,
    verified: true,
    grantor_id: 8011880648
  }
];

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    const path = url.pathname.replace(/\/+$/, "");

    // 1. Handle CORS preflight
    if (request.method === "OPTIONS") {
      return new Response(null, {
        status: 204,
        headers: CORS_HEADERS
      });
    }

    try {
      // 2. Health check
      if (path === "" || path === "/health" || path === "/api/health") {
        return jsonResponse({
          status: "ok",
          service: "yumi-badges-worker",
          version: "2.0.0",
          timestamp: new Date().toISOString()
        });
      }

      // 3. GET /api/badges -> List all active badges
      if (path === "/api/badges" && request.method === "GET") {
        const badges = await getAllBadges(env);
        return new Response(JSON.stringify(badges), {
          status: 200,
          headers: {
            ...CORS_HEADERS,
            "Cache-Control": "public, max-age=30, s-maxage=60"
          }
        });
      }

      // 4. GET /api/badges/:userId -> Single badge lookup
      const userMatch = path.match(/^\/api\/badges\/(\d+)$/);
      if (userMatch && request.method === "GET") {
        const userId = Number(userMatch[1]);
        const badge = await getBadgeForUser(env, userId);
        if (!badge) {
          return jsonResponse({ error: "Badge not found", user_id: userId }, 404);
        }
        return jsonResponse(badge);
      }

      // 5. POST /api/badges/grant -> Admin Grant/Update
      if (path === "/api/badges/grant" && request.method === "POST") {
        const body = await request.json().catch(() => ({}));
        const secret = body.secret || request.headers.get("X-Founder-Secret") || "";
        const expectedSecret = env.FOUNDER_SECRET || env.ADMIN_SECRET || "yumi_default_secret_change_me";

        if (!secret || secret !== expectedSecret) {
          return jsonResponse({ success: false, message: "Невірний секрет засновника / Invalid secret" }, 403);
        }

        const targetUserId = Number(body.target_user_id || body.user_id);
        if (!targetUserId || isNaN(targetUserId)) {
          return jsonResponse({ success: false, message: "Некоректний ID користувача" }, 400);
        }

        const badgeRecord = {
          user_id: targetUserId,
          badge_id: String(body.badge_id || "original"),
          title: String(body.title || "Yumi Community ໒꒱"),
          obtained_reason: String(body.reason || body.obtained_reason || "Верифікований учасник Yumi"),
          obtained_at: new Date().toISOString().split("T")[0],
          is_active: true,
          verified: true,
          grantor_id: Number(body.grantor_id || 0)
        };

        await saveBadge(env, badgeRecord);

        return jsonResponse({
          success: true,
          message: "Бейдж успішно видано ✓",
          badge: badgeRecord
        });
      }

      // 6. POST /api/badges/revoke -> Admin Revoke
      if (path === "/api/badges/revoke" && request.method === "POST") {
        const body = await request.json().catch(() => ({}));
        const secret = body.secret || request.headers.get("X-Founder-Secret") || "";
        const expectedSecret = env.FOUNDER_SECRET || env.ADMIN_SECRET || "yumi_default_secret_change_me";

        if (!secret || secret !== expectedSecret) {
          return jsonResponse({ success: false, message: "Невірний секрет засновника / Invalid secret" }, 403);
        }

        const targetUserId = Number(body.target_user_id || body.user_id);
        if (!targetUserId || isNaN(targetUserId)) {
          return jsonResponse({ success: false, message: "Некоректний ID користувача" }, 400);
        }

        await removeBadge(env, targetUserId);

        return jsonResponse({
          success: true,
          message: "Бейдж відкликано ✓",
          user_id: targetUserId
        });
      }

      return jsonResponse({ error: "Not Found", path }, 404);

    } catch (err) {
      return jsonResponse({ error: "Internal Server Error", details: err.message }, 500);
    }
  }
};

/** Helpers */
function jsonResponse(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: CORS_HEADERS
  });
}

async function getAllBadges(env) {
  if (env.BADGES_KV) {
    const data = await env.BADGES_KV.get("badges_list", "json");
    if (data && Array.isArray(data)) {
      return data;
    }
  }
  return INITIAL_BADGES;
}

async function getBadgeForUser(env, userId) {
  const all = await getAllBadges(env);
  return all.find(b => b.user_id === userId && b.is_active) || null;
}

async function saveBadge(env, newBadge) {
  let all = await getAllBadges(env);
  const idx = all.findIndex(b => b.user_id === newBadge.user_id);
  if (idx >= 0) {
    all[idx] = newBadge;
  } else {
    all.push(newBadge);
  }
  if (env.BADGES_KV) {
    await env.BADGES_KV.put("badges_list", JSON.stringify(all));
  }
}

async function removeBadge(env, userId) {
  let all = await getAllBadges(env);
  all = all.filter(b => b.user_id !== userId);
  if (env.BADGES_KV) {
    await env.BADGES_KV.put("badges_list", JSON.stringify(all));
  }
}
