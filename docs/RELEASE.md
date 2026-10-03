# Релиз Amegram — как собирать и что проверено

## Сборка (два пути)

1. **Через CI (основной):** пуш в `main` → `.github/workflows/release.yml` →
   `./gradlew TMessagesProj:assembleRelease` (JDK 21 Temurin, NDK, ccache, arm64-v8a).
   Требует секретов в репозитории (подпись, постинг в канал — см. workflow).
2. **Локально:** JDK 21 + Android SDK 36 + NDK `27.2.12479018`
   (требования чистой официалки `DrKLO/Telegram`).
   Свой `api_id`/`api_hash` в `BuildVars.java`, свой `release.keystore`
   в `TMessagesProj/config`, свой `google-services.json`. Без этого —
   только debug-сборка на dummy-значениях из репо.

## Что проверено в этом цикле (без полной сборки)

- `tools/verify-module.sh` — настоящий `javac 21`: все 18 файлов
  `app.amegram.module` компилируются (ядро + 8 фич + хаб + шит).
  По пути поймано и починено 2 реальных бага (лямбды, сегменты v2).
- `tests/test_ghost_module.py` + `tests/test_amegram_stages.py` — 25/25 PASS.
- `AmegramGhostPolicyTest.kt` — 6 unit-тестов для `testReleaseUnitTest`.
- Холодный старт: модуль не ходит в сеть, тяжёлые вьюхи только по включению.

## Известные границы релиза (не скрывать от пользователей)

- Двойное дно скрывает рабочее пространство (чаты, поиск, пуши, открытие чата),
  PIN — Argon2id. Но `cache4.db` на диске НЕ зашифрован: от изъятия с ADB
  защищает только полное шифрование (SQLCipher-интеграция — отдельный этап).
- Chaquopy/Python тянет вес APK (плагиновый движок). WASM-рантайм не подключён.
- Удаление `app.miogram`/`app.exteraless` — только после vanilla-rebase
  (`tools/strip-legacy.sh`, критерий: 0 референсов из `org.telegram`).
