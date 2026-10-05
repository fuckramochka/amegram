# Хот-модули Амэграм

Нативные доверенные расширения из отдельного репозитория. Отличия от плагинов:

| | Плагины | Хот-модули |
|---|---|---|
| Кто пишет | кто угодно | разработчик Амэграм |
| Что делают | добавляют новое | меняют сам клиент (плеер, переводчик) |
| Рантайм | Python/Lua/Java через движки | dex в процессе, без прослоек |
| Нагрузка | движок всегда в памяти | выключен = ноль кода, включен = один ClassLoader |
| Откуда | файлы/каталог в приложении | отдельный репозиторий, докачка |

## Как это работает у пользователя

Амэграм → Хот-модули → **+** → каталог из `HotConfig.CATALOG_URL`.
Скачал → включил тумблером → вкладка настроек модуля появилась
в разделе Амэграм сама. Тап по модулю → версии и ветки:
stable/beta, история, activate/delete. Новый модуль в `modules.json`
появляется в каталоге без обновления приложения (TTL кэша 6 часов,
asset-фолбэк `assets/hotmodules/modules.json`).

## Форматы

Каталог: `hotmodules-template/modules.json`. Пакет `.hmod` = zip
(`manifest.json` + `classes.dex`, см. `hotmodules-template/README.md`).
Только https, sha256 обязателен, `minApp` сверяется с версией приложения.

## API модуля (hot-api)

`HotModule` (entry-класс, конструктор без аргументов):
- `onAttach` — фон, долгая работа запрещена; `onDetach` — снять сервисы;
- `hasSettings/settingsTitle/fillSettings` — вкладка в Амэграм,
  строки `HotRow` (header/info/switch/button), свитчи хранит хост;
- `onSettingsToggle/onSettingsAction` — реакции;
- `fillHubRows/onHubAction` — свои строки прямо в хабе.

`HotHost`: префы `hotmod_<id>`, toast/log, `registerService/getService/emit`.
`HotServices`: `TRANSLATOR` (`HotTranslator`), `PLAYER` — ядро использует
сервис если он есть, иначе сток. Новые точки встраиваются так:
`HotModulesManager.getService(...)` в месте стокового поведения.

## Нагрузка и безопасность

- Выключенный модуль: читаются только манифест и префы, dex не грузится.
- Включённый: один `DexClassLoader` на модуль, attach через 3 сек после старта в фоне.
- Хранится текущая + предыдущая версии (откат выбором версии).
- Не грузящийся модуль при включении — автовыключение с сообщением.
- Доверие: твой репозиторий + https + sha256. Подписей Ed25519 нет
  сознательно (первая версия); чужие .hmod ставить некуда — только каталог.

## Файлы в приложении

- `app/amegram/hot/api/*` — контракт (копия в `hotmodules-template/hot-api/`, держать синхронно!);
- `HotConfig / HotCatalog / HotDownloader / HotModulesManager`;
- `hot/ui/`: `HotModulesActivity`, `HotCatalogSheet`,
  `HotModuleVersionsSheet`, `HotModuleSettingsActivity`;
- хаб `AmegramSettingsActivity` (строки 100+/1000+), attach в `ApplicationLoader`.
