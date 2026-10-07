# hotmod-sdk (бік клієнта)

Розкол репозиторіїв:

- **yuimodules** — пише і збирає модулі. Канонічний авторський набір:
  `../yuimodules-kit/` (шаблон, збірка, генератор каталога, CI).
  Скопіювати в корінь того репо як `tools/` + workflow.
- **цей репо (клієнт)** — тільки споживає. Тут лишається мінімум:
  - `lint_manifest.py` — дзеркало лінтера для перевірки слепка в CI.
    Канон — у `../yuimodules-kit/lint_manifest.py`, при розсинхроні
    оновити обидва.
  - `sync_from_yuimodules.sh` — одна команда щоб перетягнути свіжі
    `modules.json` + `.hmod` з yuimodules в `assets/hotmodules/`.
