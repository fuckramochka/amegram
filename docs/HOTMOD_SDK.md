# HotMod SDK — як зібрати свій модуль

> **Розкол репозиторіїв.** Модулі живуть в окремому репо `yuimodules`,
> клієнт їх тільки споживає. Авторський набір (цей документ, шаблон, збірка,
> генератор, CI) канонічно лежить у `tools/yuimodules-kit/` — копіюється
> в корінь yuimodules як `tools/`. У клієнті лишається тільки
> `tools/hotmod-sdk/` (лінт слепка + `sync_from_yuimodules.sh`).

Хот-модуль = один `.hmod` (zip: `manifest.json` + `classes.dex`).
Клієнт чистий: 0 вбудованих, все ставиться з магазину або офлайн-seed.

## 1. Контракт

Entry-клас реалізує `app.amegram.hot.api.HotModule`:

- публічний конструктор без аргументів;
- `moduleId()` == `id` з `manifest.json`;
- `onAttach()` — у фоні, важке виносьте у свій потік;
- `onDetach()` — зняти слухачі/треди (потім ClassLoader віддадуть GC);
- `api`-класи (`HotModule/Host/Service`) — **compileOnly**, інакше лінтер не пропустить.

Шаблон: `template/DemoModule.java` + `manifest.json` (поруч із цим документом у кіті).

## 2. manifest.json

```json
{
  "id": "demo",
  "version": "1.0.0",
  "branch": "stable",
  "entry": "com.example.hotmod.DemoModule",
  "name": "Demo Module",
  "minApp": 7112
}
```

`version` — X.Y.Z. `minApp` — мінімальний білд апки (несумісні гілки ховаються самі).

## 3. Збірка

```bash
./build_hmod.sh com.example.hotmod.DemoModule \
  DemoModule.java manifest.json ./hmods/
# -> hmods/demo-1.0.0.hmod
```

## 4. Каталог

Опишіть модуль у `meta.json` (приклад: `meta.example.json` у кіті):

- `category`: `privacy | media | power | custom | other`;
- `permissions`: `hook_net | hook_ui | storage | network | background`;
- `featured`: потрапляє в карусель «Вибір редакції»;
- `changelog`: `{ "1.0.1": "що нового" }` — показується в деталці і версіях.

```bash
python3 make_modules_json.py hmods/ meta.json \
  https://raw.githubusercontent.com/<org>/yuimodules/main/hmods/ modules.json
python3 lint_manifest.py --catalog modules.json --strict
```

## 5. Довіра

- `sha256` рахується генератором і перевіряється клієнтом при кожному завантаженні;
- `signature` (detached, base64) — опціонально; без нього деталка показує «SHA-256»,
  з ним — «Підписано»;
- несумісні (`minApp` вище за апку) не ставляться — кнопка показує `minApp`.

## 6. Життєвий цикл у клієнті

- Вимкнений модуль = 0 байт у пам'яті, вантажиться ліниво в ізольований ClassLoader;
- 3 провали завантаження поспіль = карантин (авто-вимкнення, бейдж `⛔`);
- оновлення: магазин ховає встановлені, список показує `↑ vX доступно` + «Оновити все»;
- відкат: попередня версія лишається на диску (`KEEP_VERSIONS=2`), кнопка «Відкотити»;
- бекап набору: меню ⋮ → експорт/імпорт JSON;
- діплінки: `amegram://module/<id>` (деталка), `amegram://modules` (магазин).

## 7. Синхронізація слепка в клієнті

Після пушу в yuimodules оновіть офлайн-копію в APK однією командою
(з кореня клієнта):

```bash
./tools/hotmod-sdk/sync_from_yuimodules.sh [branch]
```

Скрипт тягне `modules.json` + всі `.hmod`, лінтить і розкладає
в `TMessagesProj/src/main/assets/hotmodules/`.
