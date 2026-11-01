import json
import time
from datetime import datetime
from server.core.db import get_connection, transaction
from server.config import FOUNDER_USER_ID, EXPORT_JSON_PATH

def init_badges_table():
    with transaction() as conn:
        conn.execute("""
            CREATE TABLE IF NOT EXISTS badges (
                user_id INTEGER PRIMARY KEY,
                badge_id TEXT NOT NULL DEFAULT 'original',
                title TEXT NOT NULL DEFAULT 'Miyumi Community ໒꒱',
                obtained_reason TEXT NOT NULL DEFAULT 'Офіційна відзнака спільноти Miyumi',
                obtained_at TEXT NOT NULL,
                is_active INTEGER NOT NULL DEFAULT 1,
                verified INTEGER NOT NULL DEFAULT 1,
                grantor_id INTEGER NOT NULL DEFAULT 0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
        """)
        # Pre-seed Founder Badge (8011880648)
        row = conn.execute("SELECT user_id FROM badges WHERE user_id = ?", (FOUNDER_USER_ID,)).fetchone()
        if not row:
            conn.execute("""
                INSERT INTO badges (user_id, badge_id, title, obtained_reason, obtained_at, is_active, verified, grantor_id)
                VALUES (?, ?, ?, ?, ?, 1, 1, ?);
            """, (
                FOUNDER_USER_ID,
                "devil",
                "Засновник & Архітектор Miyumi ໒꒱",
                "Особиста відзнака засновника та головного архітектора спільноти Miyumi (@fuckramochka).",
                "01.09.2026",
                FOUNDER_USER_ID
            ))

def get_all_active_badges():
    conn = get_connection()
    cursor = conn.execute("""
        SELECT user_id, badge_id, title, obtained_reason, obtained_at, is_active, verified, grantor_id
        FROM badges
        WHERE is_active = 1
        ORDER BY (user_id = ?) DESC, updated_at DESC;
    """, (FOUNDER_USER_ID,))
    
    rows = cursor.fetchall()
    result = []
    for r in rows:
        result.append({
            "user_id": int(r["user_id"]),
            "badge_id": r["badge_id"],
            "title": r["title"],
            "obtained_reason": r["obtained_reason"],
            "obtained_at": r["obtained_at"],
            "is_active": bool(r["is_active"]),
            "verified": bool(r["verified"]),
            "grantor_id": int(r["grantor_id"])
        })
    return result

def get_badge_by_id(user_id: int):
    conn = get_connection()
    row = conn.execute("""
        SELECT user_id, badge_id, title, obtained_reason, obtained_at, is_active, verified, grantor_id
        FROM badges WHERE user_id = ?;
    """, (user_id,)).fetchone()
    if not row:
        return None
    return {
        "user_id": int(row["user_id"]),
        "badge_id": row["badge_id"],
        "title": row["title"],
        "obtained_reason": row["obtained_reason"],
        "obtained_at": row["obtained_at"],
        "is_active": bool(row["is_active"]),
        "verified": bool(row["verified"]),
        "grantor_id": int(row["grantor_id"])
    }

def grant_badge(user_id: int, badge_id: str, title: str = None, reason: str = None, grantor_id: int = 0):
    if user_id <= 0:
        raise ValueError("Invalid user_id")
    
    date_str = datetime.now().strftime("%d.%m.%Y")
    f_badge = (badge_id or "").strip() or "original"
    f_title = (title or "").strip() or "Miyumi Community ໒꒱"
    f_reason = (reason or "").strip() or "Офіційна відзнака спільноти Miyumi"
    
    with transaction() as conn:
        conn.execute("""
            INSERT INTO badges (user_id, badge_id, title, obtained_reason, obtained_at, is_active, verified, grantor_id, updated_at)
            VALUES (?, ?, ?, ?, ?, 1, 1, ?, CURRENT_TIMESTAMP)
            ON CONFLICT(user_id) DO UPDATE SET
                badge_id = excluded.badge_id,
                title = excluded.title,
                obtained_reason = excluded.obtained_reason,
                is_active = 1,
                verified = 1,
                grantor_id = excluded.grantor_id,
                updated_at = CURRENT_TIMESTAMP;
        """, (user_id, f_badge, f_title, f_reason, date_str, grantor_id or FOUNDER_USER_ID))

    # Auto-export to fallback JSON
    export_github_json()
    return get_badge_by_id(user_id)

def revoke_badge(user_id: int):
    with transaction() as conn:
        conn.execute("""
            UPDATE badges
            SET is_active = 0, updated_at = CURRENT_TIMESTAMP
            WHERE user_id = ?;
        """, (user_id,))
    export_github_json()
    return True

def export_github_json():
    """Generates a clean badges.json compatible with GitHub raw mirror."""
    badges = get_all_active_badges()
    payload = {
        "version": 1,
        "updated": int(time.time()),
        "server_url": None,
        "badges": [
            {
                "user_id": b["user_id"],
                "type": b["badge_id"],
                "title": b["title"],
                "lore": b["obtained_reason"],
                "date": b["obtained_at"],
                "verified": b["verified"]
            }
            for b in badges
        ]
    }
    try:
        with open(EXPORT_JSON_PATH, "w", encoding="utf-8") as f:
            json.dump(payload, f, ensure_ascii=False, indent=2)
    except Exception:
        pass
    return payload
