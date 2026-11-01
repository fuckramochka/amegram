import os
import secrets
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent

# Server Network Config
HOST = os.getenv("YUMI_HOST", "0.0.0.0")
PORT = int(os.getenv("YUMI_PORT", "8765"))

# Security & Ownership
FOUNDER_USER_ID = int(os.getenv("YUMI_FOUNDER_ID", "8011880648"))

# Local storage. The founder secret is never shipped in the repository:
# it comes from YUMI_FOUNDER_SECRET or is generated once in data/founder_secret.txt.
DATA_DIR = BASE_DIR / "data"
DATA_DIR.mkdir(parents=True, exist_ok=True)
FOUNDER_SECRET_PATH = DATA_DIR / "founder_secret.txt"


def _load_founder_secret() -> str:
    env_secret = (os.getenv("YUMI_FOUNDER_SECRET") or "").strip()
    if env_secret:
        return env_secret
    try:
        if FOUNDER_SECRET_PATH.exists():
            existing = FOUNDER_SECRET_PATH.read_text(encoding="utf-8").strip()
            if existing:
                return existing
        generated = "MIO-GRANT-" + secrets.token_urlsafe(32)
        FOUNDER_SECRET_PATH.write_text(generated + "\n", encoding="utf-8")
        try:
            os.chmod(FOUNDER_SECRET_PATH, 0o600)
        except OSError:
            pass
        return generated
    except OSError as exc:
        raise RuntimeError(
            f"Cannot read/create founder secret at {FOUNDER_SECRET_PATH}: {exc}"
        ) from exc


FOUNDER_SECRET = _load_founder_secret()

# Database
DB_PATH = DATA_DIR / "yumi.db"

# GitHub Fallback mirror export path (optional)
EXPORT_JSON_PATH = DATA_DIR / "badges_export.json"

# Server info
SERVER_NAME = "Yumi Ecosystem Server"
SERVER_VERSION = "2.0.0-modular"
