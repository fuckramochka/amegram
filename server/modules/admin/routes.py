from server.core.router import Router, Request
from server.core.response import html_response
from server.config import FOUNDER_USER_ID, FOUNDER_SECRET, SERVER_NAME, SERVER_VERSION

ADMIN_HTML = """<!DOCTYPE html>
<html lang="uk">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Yumi Ecosystem Server — Dashboard</title>
    <style>
        :root {
            --bg: #0f0f13;
            --surface: #17171e;
            --surface-hover: #21212b;
            --border: #2a2a38;
            --primary: #9b51e0;
            --primary-glow: rgba(155, 81, 224, 0.25);
            --text: #f0f0f5;
            --text-muted: #8e8ea0;
            --success: #27ae60;
            --danger: #eb5757;
            --radius: 14px;
        }
        * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; }
        body { background: var(--bg); color: var(--text); padding: 24px; min-height: 100vh; }
        .container { max-width: 900px; margin: 0 auto; }
        header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 28px; padding-bottom: 18px; border-bottom: 1px solid var(--border); }
        .logo { font-size: 22px; font-weight: 700; color: #fff; display: flex; align-items: center; gap: 8px; }
        .logo span { color: var(--primary); }
        .status-badge { background: rgba(39, 174, 96, 0.15); color: var(--success); border: 1px solid rgba(39, 174, 96, 0.3); padding: 5px 12px; border-radius: 20px; font-size: 13px; font-weight: 600; display: flex; align-items: center; gap: 6px; }
        .status-dot { width: 8px; height: 8px; background: var(--success); border-radius: 50%; box-shadow: 0 0 8px var(--success); }
        
        .card { background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius); padding: 22px; margin-bottom: 24px; box-shadow: 0 8px 24px rgba(0,0,0,0.2); }
        .card-title { font-size: 17px; font-weight: 600; margin-bottom: 16px; display: flex; align-items: center; justify-content: space-between; }
        
        .form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; margin-bottom: 14px; }
        .form-full { grid-column: span 2; }
        label { display: block; font-size: 12px; font-weight: 600; color: var(--text-muted); margin-bottom: 6px; text-transform: uppercase; letter-spacing: 0.5px; }
        input, select, textarea { width: 100%; background: #0b0b0e; border: 1px solid var(--border); color: #fff; padding: 11px 14px; border-radius: 10px; font-size: 14px; outline: none; transition: 0.2s; }
        input:focus, select:focus, textarea:focus { border-color: var(--primary); box-shadow: 0 0 0 3px var(--primary-glow); }
        
        button.btn-primary { background: var(--primary); color: #fff; border: none; padding: 12px 20px; border-radius: 10px; font-size: 14px; font-weight: 600; cursor: pointer; transition: 0.2s; width: 100%; }
        button.btn-primary:hover { opacity: 0.9; transform: translateY(-1px); }
        button.btn-danger { background: rgba(235, 87, 87, 0.15); color: var(--danger); border: 1px solid rgba(235, 87, 87, 0.3); padding: 6px 12px; border-radius: 8px; font-size: 12px; font-weight: 600; cursor: pointer; }
        button.btn-danger:hover { background: var(--danger); color: #fff; }

        table { width: 100%; border-collapse: collapse; margin-top: 10px; }
        th { text-align: left; padding: 12px; font-size: 12px; color: var(--text-muted); border-bottom: 1px solid var(--border); text-transform: uppercase; }
        td { padding: 14px 12px; font-size: 13.5px; border-bottom: 1px solid var(--border); vertical-align: middle; }
        tr:hover { background: var(--surface-hover); }
        .badge-pill { display: inline-block; padding: 4px 10px; border-radius: 14px; font-size: 12px; font-weight: 600; background: rgba(155, 81, 224, 0.15); color: var(--primary); border: 1px solid rgba(155, 81, 224, 0.3); }
        .toast { position: fixed; bottom: 24px; right: 24px; background: var(--surface); border: 1px solid var(--primary); color: #fff; padding: 12px 20px; border-radius: 10px; font-size: 14px; box-shadow: 0 10px 30px rgba(0,0,0,0.5); display: none; }
    </style>
</head>
<body>
    <div class="container">
        <header>
            <div class="logo">Yumi <span>Server</span> ໒꒱</div>
            <div class="status-badge"><div class="status-dot"></div> Онлайн • :8765</div>
        </header>

        <!-- Grant Badge Card -->
        <div class="card">
            <div class="card-title">Видати або оновити бейдж</div>
            <form id="grantForm">
                <div class="form-grid">
                    <div>
                        <label>User ID (Telegram ID)</label>
                        <input type="number" id="targetId" required placeholder="наприклад 8011880648">
                    </div>
                    <div>
                        <label>Стиль бейджа</label>
                        <select id="badgeId">
                            <option value="original">Original (Канонічне серце)</option>
                            <option value="devil">Devil (Ріжки)</option>
                            <option value="angel">Angel (Німб)</option>
                            <option value="pink">Pink (Рожевий неоновий)</option>
                            <option value="cyan">Cyan (Кібер-блакитний)</option>
                            <option value="dark">Dark (Темний вельвет)</option>
                            <option value="prismatic">Prismatic (Призма)</option>
                            <option value="wireframe">Wireframe (Вайрфрейм)</option>
                            <option value="glitch">Glitch (Глітч)</option>
                            <option value="crown">Crown (Корона)</option>
                        </select>
                    </div>
                    <div class="form-full">
                        <label>Титул / Назва відзнаки</label>
                        <input type="text" id="badgeTitle" placeholder="Засновник & Архітектор Miyumi ໒꒱">
                    </div>
                    <div class="form-full">
                        <label>Обґрунтування видачі (Lore)</label>
                        <input type="text" id="badgeReason" placeholder="Особиста відзнака за внесок у розвиток">
                    </div>
                    <div class="form-full">
                        <label>Секрет засновника (Founder Secret)</label>
                        <input type="password" id="secretKey" placeholder="Введіть секрет із server/data/founder_secret.txt">
                        <div style="font-size:12px; color:var(--text-muted); margin-top:8px;">
                            Секрет не зберігається в репозиторії. Сервер створює його у
                            <code>server/data/founder_secret.txt</code> або бере з <code>YUMI_FOUNDER_SECRET</code>.
                        </div>
                    </div>
                </div>
                <button type="submit" class="btn-primary">Призначити відзнаку</button>
            </form>
        </div>

        <!-- Badges List Card -->
        <div class="card">
            <div class="card-title">
                Активні відзнаки спільноти
                <span id="badgeCount" style="font-size: 13px; color: var(--text-muted);">Завантаження...</span>
            </div>
            <table>
                <thead>
                    <tr>
                        <th>User ID</th>
                        <th>Стиль</th>
                        <th>Назва & Обґрунтування</th>
                        <th>Дата</th>
                        <th>Дія</th>
                    </tr>
                </thead>
                <tbody id="badgesTableBody">
                </tbody>
            </table>
        </div>
    </div>

    <div class="toast" id="toast"></div>

    <script>
        const secretInput = document.getElementById('secretKey');
        localStorage.removeItem('yumi_secret');
        secretInput.value = localStorage.getItem('yumi_secret_v2') || '';
        secretInput.addEventListener('input', () => localStorage.setItem('yumi_secret_v2', secretInput.value));

        function showToast(text, color) {
            const toast = document.getElementById('toast');
            toast.innerText = text;
            toast.style.borderColor = color || 'var(--primary)';
            toast.style.display = 'block';
            setTimeout(() => toast.style.display = 'none', 3000);
        }

        async function loadBadges() {
            try {
                const res = await fetch('/api/badges');
                const data = await res.json();
                document.getElementById('badgeCount').innerText = `Всього: ${data.length}`;
                const tbody = document.getElementById('badgesTableBody');
                tbody.innerHTML = '';
                
                data.forEach(b => {
                    const tr = document.createElement('tr');
                    tr.innerHTML = `
                        <td><strong>${b.user_id}</strong></td>
                        <td><span class="badge-pill">${b.badge_id}</span></td>
                        <td>
                            <div style="font-weight:600;">${b.title}</div>
                            <div style="font-size:12px; color:var(--text-muted);">${b.obtained_reason || '—'}</div>
                        </td>
                        <td style="color:var(--text-muted); font-size:12px;">${b.obtained_at}</td>
                        <td>
                            <button class="btn-danger" onclick="revokeBadge(${b.user_id})">Відкликати</button>
                        </td>
                    `;
                    tbody.appendChild(tr);
                });
            } catch (e) {
                console.error(e);
            }
        }

        async function revokeBadge(userId) {
            if (!confirm(`Відкликати бейдж у користувача ${userId}?`)) return;
            const res = await fetch('/api/badges/revoke', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    secret: secretInput.value,
                    target_user_id: userId
                })
            });
            const r = await res.json();
            if (r.ok) {
                showToast(`Бейдж користувача ${userId} успішно відкликано ✓`);
                loadBadges();
            } else {
                showToast(r.error || 'Помилка відкликання', 'var(--danger)');
            }
        }

        document.getElementById('grantForm').addEventListener('submit', async (e) => {
            e.preventDefault();
            const payload = {
                secret: secretInput.value,
                target_user_id: parseInt(document.getElementById('targetId').value),
                badge_id: document.getElementById('badgeId').value,
                title: document.getElementById('badgeTitle').value,
                reason: document.getElementById('badgeReason').value
            };

            const res = await fetch('/api/badges/grant', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
            const r = await res.json();
            if (r.ok) {
                showToast(`Бейдж успішно призначено для ${payload.target_user_id} ✓`);
                document.getElementById('targetId').value = '';
                document.getElementById('badgeTitle').value = '';
                document.getElementById('badgeReason').value = '';
                loadBadges();
            } else {
                showToast(r.error || 'Помилка видачі', 'var(--danger)');
            }
        });

        loadBadges();
    </script>
</body>
</html>
"""

def register_admin_routes(router: Router):
    @router.get("/")
    def admin_root(req: Request):
        return html_response(ADMIN_HTML)

    @router.get("/admin")
    def admin_page(req: Request):
        return html_response(ADMIN_HTML)
