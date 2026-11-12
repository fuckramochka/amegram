# ⚡ Yumi Badges Cloudflare Worker

Глобальний безкоштовний Edge API бекенд для системи бейджиків клієнта **Yumi**.
Працює 24/7/365 по всьому світу без необхідності тримати ПК увімкненим.

---

## 🚀 Як розгорнути за 2 хвилини

### Спосіб 1: Через консоль (CLI)
```bash
cd server/cloudflare-worker

# 1. Авторизація у Cloudflare (якщо ще не авторизовані)
npx wrangler login

# 2. Створення безкоштовного KV-сховища для збереження бейджів
npx wrangler kv:namespace create BADGES_KV

# Скопіюйте отриманий `id` у файл wrangler.toml:
# [[kv_namespaces]]
# binding = "BADGES_KV"
# id = "ВАШ_ID"

# 3. Деплой
npx wrangler deploy
```
Після команди ви отримаєте готове посилання, наприклад:  
`https://yumi-badges-worker.<your-subdomain>.workers.dev`

---

### Спосіб 2: Прямо через браузер (Cloudflare Dashboard — без терміналу)
1. Зайдіть на [dash.cloudflare.com](https://dash.cloudflare.com/) -> **Workers & Pages** -> **Create application** -> **Create Worker**.
2. Натисніть **Deploy**.
3. Натисніть **Edit code** і просто вставте весь вміст файлу `src/index.js`.
4. Натисніть **Save and deploy**.
5. У вкладці **Settings** -> **Variables and Secrets** додайте змінну `FOUNDER_SECRET` (ваш секретний пароль засновника).
6. Скопіюйте URL воркера.

---

## 📱 Як підключити в клієнті Yumi на телефоні
1. Відкрийте **Налаштування Yumi** -> **Бейджики**.
2. В поле **Адреса сервера Yumi** вставте URL вашого воркера:  
   `https://yumi-badges-worker.<your-subdomain>.workers.dev`
3. В поле **Секрет засновника** введіть той самий `FOUNDER_SECRET`.
4. **Готово!** Тепер:
   - Всі бейджі завантажуються миттєво з Edge CDN Cloudflare.
   - Зайшовши в профіль будь-якого користувача у Yumi, натисніть значок бейджа -> виберіть стиль стрілочки -> натисніть **«Зберегти / Видати»** або **«Видалити»**.
   - Дія виконається миттєво з вашого телефону, без комп'ютера і без локального Wi-Fi.
