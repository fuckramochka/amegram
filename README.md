<div align="center">

<img src="assets/logo.png" width="128" alt="Yumi Logo">

# Yumi (Юмі)

### *Telegram, but make it cute, aesthetic, fast & unstoppable.*
**Next-Generation Telegram Client with Material You 3 Expressive, Hi-Fi Apple Music Player, Unified AI & Edge Cloud Badges**

[![Download Latest APK](https://img.shields.io/badge/Download-Latest%20APK-00F0FF?style=for-the-badge&logo=android&logoColor=black)](https://github.com/fuckramochka/amegram/releases/latest)
[![Author](https://img.shields.io/badge/Author-@dkamegram-FF2A93?style=for-the-badge&logo=telegram&logoColor=white)](https://t.me/dkamegram)
[![License](https://img.shields.io/badge/License-GPL%20v3-9D4EDD?style=for-the-badge)](LICENSE)
[![Build Status](https://img.shields.io/github/actions/workflow/status/fuckramochka/amegram/release.yml?style=for-the-badge&logo=githubactions&logoColor=white)](https://github.com/fuckramochka/amegram/actions)

</div>

---

## 🌸 Overview / Про проєкт

**Yumi** — незалежний швидкісний клієнт Telegram для Android, сфокусований на естетиці **Material You 3 Expressive**, бездоганному звуці та зручності щоденного спілкування.

### Головні переваги Yumi:
- 🎨 **Material You 3 Expressive UI**: єдина, відполірована дизайн-система із закругленими картками, адаптивними пастельними кольорами та ергономічним нижнім баром.
- 🎵 **Hi-Fi аудіоплеєр у стилі Apple Music**:
  - Синхронізовані караоке-тексти пісень (по рядках, словах та складах) з живим сяйвом (Glow effect).
  - Плавний градієнтний фон аури альбому (`YumiFluidAuraView`) та режим вінілової платівки.
  - Повноцінна черга треків, розумний вибір «Відтворити наступним» та експорт плейлистів у форматі `.yumi`.
- 🤖 **Єдиний Yumi AI (BYOK)**:
  - Підключення власного API-ключа (Google Gemini, OpenAI, Claude).
  - Швидкі дії в полі вводу чату: переписування тону, виправлення помилок, скорочення та авто-продовження думок.
  - Розумний конспект довгих повідомлень та аудіо-записів в один тап.
- 🏷️ **Спільнота та бейджики 24/7**:
  - Автономний Edge API бекенд на **Cloudflare Worker** з миттєвою роздачею по всьому світу.
  - Видача та видалення значків прямо зі шторки профілю користувача в додатку з адмінського акаунта.
- 👻 **Примарний режим (Ghost Mode)**:
  - Читання повідомлень без відмітки «переглянуто».
  - Приховування статусу «онлайн» та статусу набору тексту.
  - Декой-екран (Decoy Screen) з окремим паролем для захисту від допитливих очей.
- 🚀 **Хмарна збірка та швидкі оновлення**:
  - Усі APK збираються автоматично на серверах GitHub Actions.
  - Вбудований In-App APK Updater сповіщає про нові релізи та оновлює застосунок без крашів і витоків пам'яті.

---

## 📱 Архітектура та структура проєкту

```
TMessagesProj/src/main/java/
├── org/telegram/           # Офіційне ядро Telegram для Android
├── app/miogram/bridge/     # Мости інтеграції та сервіси Yumi
│   ├── ai/                 # Єдиний Yumi AI сервіс (MiogramAiService)
│   ├── badge/              # Хмарний міст бейджів (Cloudflare Worker API)
│   ├── player/             # Yumi Player Settings, аура та вініл
│   ├── playlist/           # Менеджер плейлистів .yumi та Smart Queue
│   └── updater/            # Нативний In-App GitHub Releases Updater
└── app/exteraless/player/  # Apple Music PlayerSheet, LyricsView караоке
```

---

## ⚡ Налаштування Cloudflare Worker для бейджів

Серверна частина знаходиться в папці [`server/cloudflare-worker/`](server/cloudflare-worker/):
1. Перейдіть у `server/cloudflare-worker/`.
2. Запустіть `npx wrangler deploy` (або скопіюйте код `src/index.js` у веб-панель Cloudflare).
3. Вставте URL воркера та свій секрет у **Налаштування Yumi → Бейджики**.
4. Бейджі оновлюються 24/7 глобально без необхідності тримати ПК увімкненим.

---

## 🛠️ Збірка / Build

Проєкт налаштовано для автоматичного збирання у хмарі:
* Просто зробіть `git push` у гілку `main` або поставте релізний тег `v*`.
* GitHub Actions автоматично збере релізний APK та опублікує його в Releases.

---

## 📄 Ліцензія

Yumi поширюється за ліцензією **GNU GPL v3.0**. Деталі у файлі [LICENSE](LICENSE).
