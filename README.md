<div align="center">

<img src="assets/logo.png" width="128" alt="Amegram Logo">

# Amegram (Амеграм)

### *More than just a messenger. Telegram, but make it cute, powerful & unstoppable.*
**Next-Generation Telegram Client with Zero-Trust Security, Modern Audio Player, 2026 Frontier AI, WASM Plugins & TikTok MI Ecosystem**

[![Download Latest APK](https://img.shields.io/badge/Download-Latest%20APK-00F0FF?style=for-the-badge&logo=android&logoColor=black)](https://github.com/fuckramochka/amegram/releases/latest)
[![Official Website](https://img.shields.io/badge/Website-Amegram%20Portal-FF69B4?style=for-the-badge&logo=googlechrome&logoColor=white)](https://fuckramochka.github.io/amegram/)
[![Author](https://img.shields.io/badge/Author-@dkamegram-FF2A93?style=for-the-badge&logo=telegram&logoColor=white)](https://t.me/dkamegram)
[![License](https://img.shields.io/badge/License-GPL%20v3-9D4EDD?style=for-the-badge)](LICENSE)
[![Android 15](https://img.shields.io/badge/Android%2015-16KB%20ELF%20Ready-3DDC84?style=for-the-badge&logo=android&logoColor=white)](docs/BUILD.md)

</div>

---

## Overview / Про проєкт

**Amegram** — незалежний високоефективний клієнт Telegram для Android, створений для тих, хто цінує абсолютну конфіденційність, естетику, передовий штучний інтелект 2026 року, першокласний звук і безшовну екосистему.

Amegram поєднує в собі:
- 🛡️ **Zero-Trust сховище «Подвійне дно»** з екстреним Duress PIN та апаратною ізоляцією StrongBox.
- 🚀 **Мікро-патчі на льоту (`AmegramPatchManager`)**: оновлення критичних компонентів напряму з GitHub без перевстановлення APK.
- 🌐 **Anti-Censorship & Anti-Block Engine**: миттєвий обхід блокувань ТСПУ/РКН з локальними зондами (`ya.ru`, `vk.com`, `77.88.8.8`) та динамічним проксі.
- 🧠 **Frontier AI 2026 & On-Device Models**: інтеграція Gemini 3.5 Flash-Lite (ліміти в 10 разів вищі), Gemini 3.8 Flash, LiteRT-LM / AI Edge SDK (Gemini Nano, Gemma 4 E2B/E4B, Qwen 2.5/3.5) з аналізом заліза пристрою та генератором плагінів.
- 📱 **Безшовна екосистема з TikTok MI**: вбудований відеоплеєр `AmegramTikTokPlayer`, картка цифрової присутності з неоновим градієнтом, 1-tap надсилання у «Збережене» та ClipVault.
- 🎵 **Сучасний аудіоплеєр** з живою візуалізацією басів, текстами пісень та ергономікою Apple Music / Spotify.
- 🎨 **Мультимакетний інтерфейс**: миттєве перемикання між стилями Discord, iOS, Minimalist та класичним Telegram.
- ⚡ **WebAssembly (WASM) Rust & Python плагін-рушій** з холодним запуском < 1 мс.
- ʚ♡ɞ **10 канонічних піксельних бейджів** з хмарною синхронізацією через Supabase.

---

## Ключові можливості / Key Features

### 1. 🛡️ Захист від примусу (Duress PIN) та шифрування SQLCipher
* **Два незалежних PIN-коди:**
  * *Звичайний PIN:* розблоковує ваше основне захищене робоче середовище.
  * *Duress (Тривожний) PIN:* миттєво відкриває нейтральний декой-екран (`MiogramDecoyActivity`) без дешифрування справжніх ключів.
* **Argon2id KDF (RFC 9106) + Timing Equalization:** вирівнювання часу деривації для виключення таймінг-атак.
* **Апаратний захист StrongBox / TEE:** неекспортовані ключі в AndroidKeyStore.
* **Захист від примусової біометрії:** у захищеному режимі сканування відбитка вимикається, запобігаючи розблокуванню уві сні.
* **Миттєве очищення пам'яті (`zeroizeNow`):** асинхронне стирання відкритих ключів при переході у фоновий режим.
* **Повне шифрування бази даних:** рушій SQLCipher з перевіркою цілісності сторінок.

📖 *Детальніше у [Security Whitepaper](docs/SECURITY.md).*

---

### 2. 🚀 Мікро-патчі на льоту (Rapid Hotfixes)
* **`AmegramPatchManager`:** система гарячого застосування мікро-патчів з репозиторію GitHub без потреби завантажувати й перевстановлювати весь APK.
* **Керування в Налаштуваннях:** розділ «Оновлення та інформація» містить статус застосованих патчів та кнопку ручної перевірки з детальним звітом.

---

### 3. 🌐 Обхід блокувань та цензури ТСПУ/РКН (Anti-Block Engine)
* **`MiogramAntiBlockEngine`:** активна діагностика блокування з таймаутом усього 3.5 секунди для миттєвого підключення обхідних маршрутів.
* **Стійкі локальні зонди:** перевірка доступності через внутрішні вузли (`ya.ru:443`, `vk.com:443`, `77.88.8.8:53`), які не фільтруються ТСПУ, на відміну від заблокованих `1.1.1.1` та `8.8.8.8`.
* **Автоматичний fallback:** у разі виявлення блокування застосунок автоматично підключає вбудовані стійкі проксі-канали.

---

### 4. 🧠 Frontier AI 2026 & Локальні моделі (LiteRT-LM)
* **Моделі 2026 року:**
  * `gemini-3.5-flash-lite`: основна хмарна модель за замовчуванням. Має в 10 разів вищі ліміти запитів, наднизьку затримку та максимальну стабільність.
  * `gemini-3.8-flash`: передова модель для кодингу, міркувань та складних задач.
  * `gemini-3.5-flash`: збалансований варіант для загальних задач.
  * `local-litert`: підтримка локального виконання нейромереж на пристрої через Google AI Edge SDK / Android AICore (Gemini Nano, Gemma 4 E2B/E4B, Qwen 2.5/3.5 Mobile).
* **Аналіз заліза (Hardware Profiling):** застосунок аналізує обсяг оперативної пам'яті, процесорні ядра та архітектуру і надає персоналізовану рекомендацію моделі для найкращого балансу швидкості та якості.
* **Генератор плагінів Amegram:** створення валідних плагінів для Python (`BasePlugin`), Java та Rust в один клік через AI.

---

### 5. 📱 Екосистема TikTok MI
* **Вбудований відеоплеєр (`AmegramTikTokPlayer`):** перегляд відео за посиланнями TikTok безпосередньо в Amegram без водяних знаків та реклами.
* **Цифрова присутність (Digital Presence):** відображення статусу перегляду TikTok у картці профілю з неоновим градієнтом `#00F2FE` (Cyan) / `#FE2C55` (Pink).
* **1-Tap експорт:** збереження відео у «Збережені повідомлення», експорт звукових доріжок у плеєр та надсилання у Telegram Stories.
* **Синхронізація тем та буфера ClipVault:** передача акцентів та скопійованих медіа між Amegram та TikTok MI.

---

### 6. 🎵 Сучасний аудіоплеєр з візуалізацією басів
* **Жива візуалізація басів (`MiogramBassVisualizer`):** плавний мультисмуговий спектральний аналізатор у компактному та повноекранному режимах.
* **Повноекранна обкладинка з інфо:** назва треку, автор, кнопка улюбленого та живий візуалізатор відображаються прямо поверх повноформатної обкладинки.
* **6-кнопкова ергономічна панель:** Shuffle, Repeat з контекстним меню, Prev/Next, Play/Pause та черга.
* **Синхронізовані тексти пісень (LRC)** та жестове перемотування.

📖 *Детальніше у [Audio Player Architecture](docs/AUDIO_PLAYER.md).*

---

### 7. 🎨 Мультимакетний інтерфейс (Layout Switcher)
* **Discord Layout:** бічні сервери та канали, знайома структура для геймерів та спільнот.
* **iOS Cupertino:** витончена нижня панель та напівпрозорий розмитий заголовок.
* **Minimalist Rail:** ультракомпактна бічна колонка для фокусування на повідомленнях.
* **Classic & Modern Telegram:** перевірений часом швидкий інтерфейс.

---

### 8. ⚡ WebAssembly (WASM) Rust & Python плагін-рушій
* **Субмілісекундний запуск:** виконання на базі мікрорантайму WAMR без важких інтерпретаторів.
* **Мінімальний оверхед:** лише ~150 КБ оперативної пам'яті та ~85 КБ у фінальному APK.
* **Офіційний Rust SDK (`sdk/rust/miogram-plugin-sdk`):** набір інструментів з макросом `register!`, типізованими конвертами та нульовим копіюванням.
* **Криптографічний підпис Ed25519:** захист плагінів від модифікації.
* **Паралельна підтримка Python-плагінів (Chaquopy 3.11)** з валідними метаданими та класами `BasePlugin`.

📖 *Детальніше у [Plugin Developer Guide](docs/PLUGINS_DEV_GUIDE.md).*

---

### 9. ʚ♡ɞ 10 канонічних піксельних бейджів та Supabase
* **10 унікальних стилів:** Original Visor, Neon Pink, Cyan Cyber, Dark Velvet, Angel Halo, Devil Horns, Rainbow Prismatic, Wireframe Outline, Chromatic Glitch, Royal Golden Crown.
* **Хмарна синхронізація Supabase:** статус учасника та історія нагородження зберігаються у базі PostgREST та кешуються локально.

---

### 10. 🗑️ Великоднє яйце «Мусордроп» (`tg://musor_drop`)
* Інтерактивне відео-яйце з підтримкою відтворення як `.mp4`, так і `.mp3`.
* Вбудований ассет прямо в APK (`assets/musordrop.mp4`) забезпечує гарантовану роботу з коробки.

📖 *Детальніше у [Easter Eggs Guide](docs/EASTER_EGGS.md).*

---

## Вебсайт та завантаження / Website & Downloads

* **Офіційний портал:** [https://fuckramochka.github.io/amegram/](https://fuckramochka.github.io/amegram/)
* **GitHub Releases:** [https://github.com/fuckramochka/amegram/releases/latest](https://github.com/fuckramochka/amegram/releases/latest)
* **Канал проєкту:** [@dkamegram](https://t.me/dkamegram)

---

## Архітектура проєкту / Project Structure

Amegram слідує суворій односпрямованій архітектурі:
```
app.miogram.ui        →    app.miogram.bridge    →    app.miogram.core
(Activities, Views)        (System Keystore, DB)      (Pure JVM Crypto, Vault, WASM)
```

* `app.miogram.core` — 100% чиста JVM-логіка (криптографія, політики, кодеки), що тестується без емулятора.
* `app.miogram.bridge` — адаптери до Android-системи (AndroidKeyStore, Room, SQLCipher, Supabase, Ecosystem).
* `sdk/rust/miogram-plugin-sdk` — Rust-бібліотека для розробки WASM-плагінів.
* `website/` — офіційний вебсайт проєкту на базі React 19, Vite та Tailwind.

---

## Збирання з вихідного коду / Building from Source

### Системні вимоги:
* **JDK:** 21 (Eclipse Temurin або OpenJDK)
* **Android SDK:** Platform `37`, Build-Tools `36.0.0`
* **Android NDK:** `27.2.12479018`
* **Android 15 Сумісність:** вирівнювання 16 KB ELF встановлено у всіх нативних бібліотеках (`-Wl,-z,max-page-size=16384`).

### Команди збирання:
```bash
# 1. Клонувати репозиторій з субмодулями:
git clone --recursive https://github.com/fuckramochka/amegram.git
cd amegram

# 2. Зібрати Debug APK:
./gradlew assembleDebug

# 3. Зібрати оптимізований Release APK:
./gradlew assembleAfatRelease

# 4. Запустити модульні JVM-тести ядра:
./gradlew testReleaseUnitTest
```

📖 *Повний посібник зі збирання: [docs/BUILD.md](docs/BUILD.md).*

---

## Документація / Documentation Index

| Документ | Опис |
|---|---|
| 📐 [Architecture Blueprint](docs/ARCHITECTURE.md) | Цільова архітектура, правила ізоляції, карта інтеграції з ядром Telegram |
| 🛡️ [Security Whitepaper](docs/SECURITY.md) | Модель загроз, Duress PIN, Argon2id, StrongBox, SQLCipher, zeroizeNow |
| 🎵 [Audio Player Architecture](docs/AUDIO_PLAYER.md) | Живий візуалізатор басів, ергономіка керування, тексти пісень, жести |
| ⚡ [Plugin Developer Guide](docs/PLUGINS_DEV_GUIDE.md) | Створення плагінів на Rust (WASM), Python (Chaquopy) та Java/Kotlin |
| 🛠️ [Build & Compilation Guide](docs/BUILD.md) | Налаштування оточення, Gradle-скрипти, перевірка 16 KB ELF для Android 15 |
| 🎀 [Easter Eggs](docs/EASTER_EGGS.md) | Секретні команди та конвеєр відтворення Мусордропу |

---

## Ліцензія / License

Код Amegram поширюється під ліцензією **GNU General Public License v3.0 (GPL-3.0)**.  
Дивіться файл [LICENSE](LICENSE) для отримання повної інформації.

---

<div align="center">
Made with ♡ by <b>@fuckramochka</b> and the Amegram Community.<br>
<i>Stay soft. Stay safe. Stay you. ✧</i>
</div>
