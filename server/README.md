# Yumi Badge Server

Локальний сервер бейджиків Yumigram: SQLite + REST API + веб-адмінка.

## Запуск

З кореня репозиторію:

```bash
python3 -m server.run
```

За замовчуванням сервер слухає `0.0.0.0:8765`. Для телефону в тій самій Wi-Fi мережі відкрий:

```text
http://<LAN-IP-ПК>:8765
```

IP і адресу API сервер друкує під час старту. Для доступу з мобільного інтернету можна використати Cloudflare Tunnel:

```bash
cloudflared tunnel --url http://localhost:8765
```

## Секрет засновника

Секрет **не зберігається в репозиторії**. Під час першого запуску сервер створює його тут:

```text
server/data/founder_secret.txt
```

Файл має права `600`. Його потрібно один раз ввести в застосунку:

```text
Бейджики → Керування спільнотою → Секрет засновника
```

Або задати власний секрет через змінну середовища:

```bash
YUMI_FOUNDER_SECRET='MIO-GRANT-...' python3 -m server.run
```

Після зміни секрету на сервері його треба оновити й у застосунку.

## API

| Метод | Шлях | Призначення |
| --- | --- | --- |
| `GET` | `/health` | Статус сервера |
| `GET` | `/api/status` | Статус + кількість активних бейджів |
| `GET` | `/api/badges` | Усі активні бейджі |
| `GET` | `/api/badges/{user_id}` | Бейдж конкретного користувача |
| `POST` | `/api/badges/grant` | Видати/оновити бейдж (потрібен секрет) |
| `POST` | `/api/badges/revoke` | Відкликати бейдж (потрібен секрет) |
| `GET` | `/api/badges/export` | JSON для GitHub-фолбеку |
| `GET` | `/` | Веб-адмінка |

Приклад видачі:

```bash
curl -X POST http://localhost:8765/api/badges/grant \
  -H 'Content-Type: application/json' \
  -d '{
    "secret": "ВАШ_СЕКРЕТ",
    "target_user_id": 123456789,
    "badge_id": "cyan",
    "title": "Учасник спільноти",
    "reason": "За внесок у спільноту"
  }'
```

## Дані

- `server/data/yumi.db` — SQLite з бейджами (ігнорується git).
- `server/data/badges_export.json` — експорт у форматі GitHub-фолбеку.
- `server/data/founder_secret.txt` — секрет засновника (ігнорується git).

Сервер оновлює `badges_export.json` після кожної видачі/відкликання. Щоб GitHub-фолбек був актуальним, цей файл треба опублікувати в репозиторії `yuimodules` (автоматичного git push поки немає).
