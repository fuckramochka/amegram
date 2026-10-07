# yuimodules-kit — авторський набір для РЕПОЗИТОРІЮ МОДУЛІВ

Ця папка — готовий вміст для кореня репо `yuimodules`
(поруч із `modules.json` і `hmods/`). Скопіюйте все як є:

```
yuimodules/
  api/                     <- дзеркало app/amegram/hot/api/*.java з клієнта (compileOnly)
  tools/
    DemoModule.java        <- template/
    manifest.json          <- template/
    build_hmod.sh
    lint_manifest.py
    make_modules_json.py
    meta.example.json  -> перейменувати в meta.json і заповнити
  hmods/                   <- зібрані .hmod
  modules.json             <- генерується
```

> `api/` оновлюється вручну при зміні контракту в клієнті
> (`app.amegram.hot.api`). Лінтер ловить вшиті api-класи в dex.

## Цикл випуску модуля

```bash
./tools/build_hmod.sh com.example.hotmod.DemoModule DemoModule.java manifest.json ./hmods/
python3 tools/make_modules_json.py hmods/ meta.json https://raw.githubusercontent.com/<org>/yuimodules/main/hmods/ modules.json
python3 tools/lint_manifest.py --catalog modules.json --strict
git add hmods modules.json && git commit && git push
```

Клієнт підхопить новий каталог сам (TTL 6 год), слепок в APK оновлюється
скриптом `sync_from_yuimodules.sh` на боці клієнта.

## CI для yuimodules

Покладіть `hotmodules-build.yml` цього набору в `.github/workflows/` репо
модулів — кожен пуш лінтитиме `.hmod` і каталог.
