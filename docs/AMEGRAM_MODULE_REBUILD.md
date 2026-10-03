# Amegram Module — план пересборки на чистой официалке

> База: `DrKLO/Telegram` master (требования: Android Studio 2025.1.4, NDK 27.2.12479018, SDK 36).
> Текущий Amegram: `APP_VERSION 12.11.0 / 7040`, 135 файлов `app.miogram` + 200 файлов `app.exteraless` + 5 файлов `app.amegram`, врезки в **116 файлов** `org.telegram.*`.
> Диагноз из `MI0GRAM_DEEP_AUDIT.md`: placebo-security, фантомный WASM, зомби-UI в `DialogsActivity`, 10 prefs-файлов, поллер 300с, мутации static Paint.

Принцип: **чистая официалка + один каталог `app.amegram.module` + ленивые фичи-плагины**.
Никаких правок в 116 файлах. Только ~5 точек хуков через `AmegramHooks`.

---

## 1. ЧТО ПЕРЕНОСИМ ПОЛНОСТЬЮ (ядро, делает Амеграм Амеграмом)

| # | Фича | Откуда | Что забираем |
|---|------|--------|--------------|
| 1 | **Режим призрака (Ghost)** | `NekoConfig.sendRead/Online/UploadPackets` + `GhostPill.java` + `MiogramPrivacySettingsActivity.java` | Реально работает. Логика: не слать read/online/typing. |
| 2 | **Аудиоплеер + тексты** | `bridge/player/MiogramModernPlayerLayout.java` + `MiogramBassVisualizer` + `bridge/lyrics/*` (Engine/View/LrcModel/SourceSelect) + `MiogramPlayerPrefs.java` | Флагман. Живой визуализатор, LRC-караоке, 6 кнопок, пресеты default/lyrics/minimal/vinyl/compact. |
| 3 | **Бейджики (10 пиксельных)** | `bridge/badge/*` (Manager/Type/ArrowDrawable/BottomSheet/SupabaseBridge) | Уникальность. 10 стилей: Visor, Neon Pink, Cyan Cyber, Dark Velvet, Halo, Horns, Prismatic, Wireframe, Glitch, Crown. |
| 4 | **Гид (онбординг)** | `bridge/onboarding/AmegramGuideSheet.java` (5 шагов, 896 строк) | Лицо продукта. Ужать до 3 шагов. |
| 5 | **Аме-профиль (XML)** | `app.amegram.bridge.ameprofile/*` (Engine/Sheet/MediaEngine/GradientHelper/CardCell, 5 файлов) | Единственная живая кастомизация профиля. Замена мертвому `cpb_core.bin`. |
| 6 | **Anti-block движок** | `bridge/bypass/*` | Локальные зонды `ya.ru/vk.com/77.88.8.8`, fallback-прокси. Реально нужно в RU. |
| 7 | **Hotfix-патчи** | `bridge/patch/AmegramPatchManager.java` | Обновление без переустановки APK. |
| 8 | **AI-спутница (Аме/Кангель)** | `bridge/ai/companion/*` + `MiogramAiService.java` + `MiogramAiSettingsActivity.java` | Персона, память, тулбокс. Только как опциональный модуль. |

## 2. ЧТО ПЕРЕПИСЫВАЕМ (архитектура, иначе снова засрём)

| Проблема сейчас | Как должно быть в Module |
|---|---|
| 10 prefs-файлов (`naconfig`, `appearance_config`, `miogram_*`...) конфликтуют, радиус bubble в двух местах | **Один `AmegramConfig`** — фасад + мигратор старых ключей. См. `app/amegram/module/AmegramConfig.java`. |
| 116 врезок в `org.telegram` (`ChatMessageCell` мутирует `Theme.*Paint`, `DialogsActivity` делает `addView(discordRail)` + `actionBar.GONE` навсегда, двойной таббар) | **Один `AmegramHooks`** — 5 точек: `onAppCreate`, `onChatCellDraw`, `onDialogsCreate`, `onPlayerOpen`, `onMessagePrivacy`. Фичи подписываются, а не патчат ядро. `ChatMessageCell`: только локальные `Paint`, ноль аллокаций в `onDraw`. `DialogsActivity`: `LayoutDelegate` + `rebuildAllFragments(true)` при смене пресета, `actionBar` всегда восстанавливается. |
| `MiogramUpdater` будит CPU каждые 300с, неавторизованный GitHub API → 403 + жор 3–7%/сутки | WorkManager **раз в 24ч + ручная кнопка** в `Amegram → Обновления`. Никаких `scheduleWithFixedDelay(300s)`. |
| `MiogramDuressConfig` — SHA-256 без соли в открытом XML, фильтр только в `DialogsAdapter`, поиск/пуши/`cache4.db` всё палят | Либо честный `ProfileVault` (Argon2id + SQLCipher + `zeroizeNow` + `MiogramGate` в `PasscodeView`), либо честно переименовать в «Скрытие чатов», а не «шифрование». Фейк удаляем в первую очередь. |
| Плагины: WAMR отсутствует в сабмодулях (`UnsatisfiedLinkError`), `PluginEngine.kt` никем не вызывается, `isPluginActive("in_app_notifications")` — хардкод, Chaquopy тянет +45МБ | Лёгкий **Java/Kotlin Plugin API + Ed25519-подпись + скачивание по требованию**. Chaquopy/Python и WASM — только как опциональные плагины, не в base APK. Хочешь «только плеер и призрак» — качается только их `.apm` (~200–400КБ), остальное не грузится в RAM. |
| `MiogramPlayerPrefs` — 516 строк геттеров/сеттеров с `notifyChanged()` в каждом | `PlayerConfig` — один `data class` + `StateFlow`, сериализация JSON, пресеты как объекты. |
| `AmegramGuideSheet` — 896 строк, тянет Discord + AGSL + маркет в онбординг | 3 шага: 1) Персона, 2) Выбор модулей (плеер/призрак/бейджи), 3) Готово. Никакого авто-включения Discord. |
| Настройки: `MiogramSettingsActivity` дублирует Nagram/Ayu/Neko вкладки | Один хаб **`Amegram → Модули`** (см. ниже): каждый модуль — строка с toggle + «скачать», настройки модуля открываются внутри. Нативно: `TextCell`/`TextCheckCell`, `Theme.getColor`, `LayoutHelper`, без XML. |

## 3. ЧТО ВЫКИДЫВАЕМ БЕЗ ЖАЛОСТИ (мертвый/вредный код)

1. `assets/cpb_core.bin` (1.6МБ) + `Aliuhook`/`LSPlant`/`Xposed`/`dexmaker`/`mvel2` — `SecurityException: Writable dex` на Android 14/15, бан Play Protect. Заменено нативным `AmeProfileEngine`.
2. `MiogramAmeAesthetic.java` — placebo, **0 вызовов** во всём клиенте.
3. `MiogramInAppNotifications.java` (`isPluginActive("in_app_notifications")`), `MiogramVaultActivity.java` — фейк-фасады.
4. `MiogramDuressConfig.java` — фейк-безопасность (см. выше).
5. Дубль-конфиги `NaConfig`/`AppearanceConfig`/`AyuConfig`/`NekoConfig` для визуала — после миграции удаляем, оставляем только `NekoConfig` для ghost-флагов на переходный период.
6. Тяжёлая экосистема из ядра: TikTok MI плеер, Spotify, Roblox, Steam, Kanban, Multichat-пузыри, Feed-дайджесты, Heroku-Userbot, PillStack целиком — **всё это плагины**, не base. В clean-сборке их нет.
7. `jni/miogram/miogram_wasm.c` без `third_party/wasm-micro-runtime` — либо доводим WAMR до рабочего JNI, либо удаляем файл.

## 4. Amegram Module — структура (уже заложена в коде)

```
TMessagesProj/src/main/java/app/amegram/module/
  AmegramModule.java          — точка входа, init(context), версия, список фич
  AmegramFeature.java         — интерфейс: id/load/unload/isLoaded/ramEstimate
  AmegramFeatureManager.java  — реестр + скачивание .apm по требованию + toggle
  AmegramConfig.java          — ЕДИНСТВЕННЫЙ prefs (amegram_module_prefs.xml) + мигратор
  AmegramHooks.java           — единственные 5 точек врезки в org.telegram
  features/
    ghost/AmegramGhostController.java    — призрак без Neko/Pill зависимости
    player/AmegramPlayerFeature.java      — фасад плеера (ленивая загрузка layout)
    badges/AmegramBadgesFeature.java     — lazy Supabase + кэш
    guide/AmegramGuideFeature.java        — онбординг 1 раз на версию
```

Правила модуля:
- Ноль аллокаций в `onDraw` (все `Paint/Rect/Path` — поля).
- Весь network/IO — `Utilities.globalQueue`, никогда main thread.
- Нет `R.string.*`, которых нет в официалке — только прямые строки (UA/RU/EN через `AmegramStrings`).
- Каждый модуль показывает RAM-оценку (плеер ~2МБ при открытии, призрак ~50КБ, бейджи ~300КБ кэш).
- Настройки выглядят нативно: `BaseFragment` + `TextCell`, цвета из `Theme`.

## 5. Как накатить на чистую официалку (не «докидывать файлы»)

```bash
# 1. Чистая официалка рядом (не внутрь этого репо):
git clone --recursive --shallow-submodules https://github.com/DrKLO/Telegram.git Telegram-vanilla
cd Telegram-vanilla && git log --oneline -1   # фиксируем коммит базы

# 2. Копируем ТОЛЬКО модуль (он самодостаточен):
cp -r ../amegram/TMessagesProj/src/main/java/app/amegram/module \
        TMessagesProj/src/main/java/app/amegram/module

# 3. Пять однострочных хуков (см. AmegramHooks.java, раздел HOOK POINTS):
#   ApplicationLoader.onCreate        -> AmegramModule.init(this)
#   LaunchActivity.onCreate           -> AmegramHooks.onLaunchCreated()
#   DialogsActivity.createView        -> AmegramHooks.onDialogsCreate(fragment)
#   ChatMessageCell.onDraw            -> AmegramHooks.onChatCellDraw(cell, canvas) // только если фича включена
#   AudioPlayerAlert constructor       -> AmegramHooks.onPlayerOpen(alert)        // лениво грузит плеер

# 4. Свой api_id/hash в BuildVars.java + package app.amegram, сборка:
./gradlew assembleAfatRelease
```

Почему не клонирую гигабайт прямо сейчас в этот репо: это убьёт историю и CI.
Правильный путь — `Telegram-vanilla` рядом + перенос `app.amegram.module` (уже начат ниже) +
пофайловый перенос логики из таблицы п.1 с чисткой (Player ~2000 строк → фасад + view отдельно).

## 6. Порядок работ (что уже сделано / что дальше)

- [x] Этап 0: скелет модуля (Config/Manager/Hooks + 4 фичи-заглушки с честным API).
- [x] Этап 1: Ghost — модуль владеет решениями через `AmegramGhostPolicy` + `effSend*` в `AyuGhostUtils.interceptRequest` (единственный чокпоинт в `ConnectionsManager`). Legacy NekoConfig — fallback пока модуль спит; миграция — рефлексией без compile-зависимости. Исключения per-chat, fake-read и offline-after-send сохранены 1-в-1. Тесты: `TMessagesProj/src/test/kotlin/app/amegram/module/ghost/AmegramGhostPolicyTest.kt`, `tests/test_ghost_module.py`.
- [x] Этап 2: Player — `AmegramPlayerConfig` (типизированный снапшот вместо 516 строк `MiogramPlayerPrefs`, миграция по имени файла без импортов legacy); `AmegramPlayerFeature.load()` мигрирует конфиг и только тогда цепляет тяжёлую вьюху; убраны все per-frame аллокации в `MiogramAppleMusicSheet` (`SkipVectorButton.path`, `PlayPauseVectorButton.leftBar/rightBar/playPath`, `HeartVectorButton.heart` — поля + `rewind()/set()`).
- [x] Этап 3: Badges — `MiogramArrowDrawable`: `RadialGradient` кэшируется (`cachedBloom` + ключ геометрии), ноль `Theme.*` мутаций; `AmegramBadgesCache`: offline-first (память + JSON в `AmegramConfig`), сеть только при открытии профиля с неизвестным бейджем, фоновый поток, без хардкода Supabase-ключей (endpoint настраивается).
- [x] Этап 4: Хаб `AmegramModulesActivity` (Amegram → Модули: призрак/плеер/бейджи/антиблок/аме-профиль с RAM-оценками, гид, хотфиксы, code-патчи opt-in) + компактный `AmegramWelcomeSheet` (персона + живой чеклист модулей из `FeatureManager.all()`, флаг `guide_shown`). Вход из `MiogramSettingsActivity` одной строкой.
- [x] Этап 5 (модули-движки): AntiBlock — гейт в `MiogramAntiBlockEngine.start()` на ключ `antiblock_enabled` (default ON = сегодняшнее поведение), включение из хаба дожимает `start()` рефлексией; AmeProfile — `ensureInitialized()` при выключенном модуле кладёт ванильные дефолты (`applyVanillaDefaults` сбрасывает ВСЁ состояние + `customCards.clear()`), переключение живое через сброс `isInitialized`; Hotfix — формальная фича поверх уже готового гейта code-патчей. Контроллеры без compile-зависимостей (только `Class.forName`). Тесты: `tests/test_amegram_stages.py` (21 тест суммарно с ghost-файлом).
- [x] Этап 5в (Двойное дно, по-настоящему): PIN-вериферы переведены с одного SHA-256 на memory-hard Argon2id (`v2$argon2id$m=16384,t=2,p=1$…`, BouncyCastle, чистый Java без native); v1/plaintext мигрируют прозрачно при успешном вводе; вердикт всегда проверяет ОБА слота (real+duress) — выровнен тайминг; `PasscodeView` переведён на `checkPasscodeAsync` (globalQueue → UI, флаг `duressChecking` от дабл-тапов); мастер-рубильник `doublebottom_enabled` душит duress во всех точках (все проверки идут через `isDuressActive()`); модуль `doublebottom` + строка в хабе с открытием настроек. Покрытие уже было и осталось: PasscodeView, DialogsActivity, DialogsSearchAdapter, NotificationsController, ChatActivity, LaunchActivity.
- [x] Этап 5б (безопасность + запуск): `AmegramModule.init()` вшит в `ApplicationLoader.onCreate`; bytecode-патчи (`.dex` без подписи) заблокированы по умолчанию — ключ `hotfix_code_patches`, только config-патчи; апдейтер проверен: троттлинг 12ч на месте (утверждение аудита про 300с устарело — в коде `CHECK_INTERVAL_MS = 12h`).
- [ ] Этап 6 (vanilla rebase, отдельным PR): скрипт `tools/strip-legacy.sh` копирует `app.amegram.module` на чистый `DrKLO/Telegram`, дальше пофайловый перенос тяжёлых вьюх. Удалять `app.miogram`/`app.exteraless` здесь и сейчас НЕЛЬЗЯ — 116 файлов `org.telegram` на них ссылаются, сборка ляжет. Критерий готовности: `grep -r "app.miogram\|app.exteraless" org.telegram` → 0.

## 8. Верификация (чем доказано, а не словами)

- `tools/verify-module.sh` — настоящий `javac 21.0.12.1` компилирует **все 17 файлов модуля** (ядро + 7 фич + хаб + шит) против `android.jar` + `org.json` + стабов с дословными сигнатурами из репо. UI-шит проверен тем же компилятором — он уже поймал и мы починили реальный баг: нефинальные `accent`/`text` в лямбдах `AmegramWelcomeSheet` (не скомпилировалось бы и в проекте).
- `tests/test_ghost_module.py` + `tests/test_amegram_stages.py` — 21 структурная проверка (все PASS без pytest, чистым python3).
- `TMessagesProj/src/test/kotlin/app/amegram/module/ghost/AmegramGhostPolicyTest.kt` — 6 unit-тестов таблицы призрака для `./gradlew testReleaseUnitTest`.
- Полная сборка APK (`assembleAfatRelease`, NDK 27, SDK 36) — только на машине разработчика: в этой песочнице нет рута для установки SDK/NDK.

| Функция | Статус | Где рубильник |
|---|---|---|
| Призрак (read/online/typing + per-chat исключения) | Модуль `ghost`, владеет решениями | Amegram → Модули → Невидимка |
| Плеер + тексты (пресеты, визуализатор, LRC) | Модуль `player`, ленивая загрузка | Amegram → Модули → Аудиоплеер |
| Бейджи 10 шт (кэш + облако по требованию) | Модуль `badges` | Amegram → Модули → Бейджики |
| Антиблок (зонды + fallback-прокси) | Модуль `antiblock`, default ON | Amegram → Модули → Антиблок |
| Аме-профиль (XML-стили) | Модуль `ameprofile`, default ON | Amegram → Модули → Аме-профиль |
| Хотфиксы (config-патчи; .dex только opt-in) | Модуль `hotfix` + гейт в PatchManager | Amegram → Модули → Система |
| Двойное дно (real/duress PIN, decoy-аккаунт, panic logout) | Модуль `doublebottom`: Argon2id-вериферы, async-вердикт, покрытие чаты+поиск+пуши+открытие чата | Amegram → Модули → Двойное дно |
| Гид-приветствие | Модуль `guide`, 1 раз | Amegram → Модули → Гид |
| AI-спутница, TikTok/Spotify-экосистема, Kanban, Multichat, Userbot, PillStack, Discord/iOS-раскладки | Пока legacy (`app.miogram`/`app.exteraless`), НЕ модули | Старые экраны настроек; портируются следующими этапами — НЕ удалены, всё работает как раньше |
| Duress/«двойное дно» (`MiogramDuressConfig`) | НЕ переносится: фейк-безопасность (SHA-256 без соли, поиск и `cache4.db` всё палят — см. аудит). Удаление — отдельным решением, не тихий перенос | — |
- [ ] Этап 2: Player — перенести `MiogramModernPlayerLayout` как `AmegramPlayerView` за `PlayerFeature.load()`, `PlayerPrefs` → `PlayerConfig`, убрать аллокации в визуализаторе.
- [ ] Этап 3: Badges — `SupabaseBridge` → lazy `OkHttp` + Room-кэш, `ArrowDrawable` без static-мутаций.
- [ ] Этап 4: Guide + Settings-хаб «Amegram → Модули» с кнопками Скачать/Вкл.
- [ ] Этап 5: Удалить `app.miogram`/`app.exteraless` из сборки, оставить только `app.amegram.module` + официалку. Сверка: `grep -r "app.miogram\|app.exteraless" org.telegram` → 0 совпадений.
