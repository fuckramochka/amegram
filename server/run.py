#!/usr/bin/env python3
import os
import sys
import socket
from pathlib import Path

# Add project root to sys.path so 'import server.*' works from anywhere
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from http.server import ThreadingHTTPServer

from server.config import HOST, PORT, SERVER_NAME, SERVER_VERSION, DB_PATH, FOUNDER_SECRET_PATH
from server.core.db import init_db
from server.core.router import Router, YumiHTTPHandler

# Import Feature Modules
from server.modules.system.routes import register_system_routes
from server.modules.badges.service import init_badges_table
from server.modules.badges.routes import register_badges_routes
from server.modules.admin.routes import register_admin_routes

def get_local_ip():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"

def create_server():
    # 1. Initialize Database & Modules
    init_db()
    init_badges_table()

    # 2. Setup Central Router
    router = Router()
    register_system_routes(router)
    register_badges_routes(router)
    register_admin_routes(router)

    # 3. Attach handler
    YumiHTTPHandler.router = router
    httpd = ThreadingHTTPServer((HOST, PORT), YumiHTTPHandler)
    return httpd

def main():
    httpd = create_server()
    local_ip = get_local_ip()

    print("=" * 64)
    print(f"✨ {SERVER_NAME} v{SERVER_VERSION}")
    print("=" * 64)
    print(f"💾 База даних: {DB_PATH.resolve()}")
    if os.getenv("YUMI_FOUNDER_SECRET"):
        print("🔑 Секрет засновника: заданий через YUMI_FOUNDER_SECRET")
    else:
        print(f"🔑 Секрет засновника: {FOUNDER_SECRET_PATH.resolve()}")
        print("   Введіть його в застосунку: Бейджики → Секрет засновника.")
    print(f"🌐 Локальний веб-інтерфейс: http://localhost:{PORT}/")
    print(f"📱 Адреса для телефону (Wi-Fi): http://{local_ip}:{PORT}")
    print(f"📡 API бейджиків: http://{local_ip}:{PORT}/api/badges")
    print("-" * 64)
    print("💡 Для роботи через мобільний інтернет поза домом запустіть:")
    print(f"   cloudflared tunnel --url http://localhost:{PORT}")
    print("=" * 64)
    print("Натисніть Ctrl+C для зупинки сервера.\n")

    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\nСервер зупинено.")
        httpd.server_close()
        sys.exit(0)

if __name__ == "__main__":
    main()
