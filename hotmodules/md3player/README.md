# md3player — MD3-плеєр як хот-модуль

MD3-шит (`PlayerSheet`), міні-бар (`PlayerBarView`) і лірика живуть у
`app.exteraless.player.*` всередині APK. Цей модуль — тонкий entry
(`Md3PlayerModule`), який реєструє сервіс `md3player` (`HotMd3`) і дає
ядру (`Md3Router`) шит/бар замість стокових.

Чому не весь код у dex: класи плеєра вже є в батьківському лоадері —
дублювати їх у dex означало б другу копію статиків (`PlayerSheet.instance`).
Тому `.hmod` важить ~3 КБ.

## Збірка (потрібен Android SDK)

```bash
./build.sh $ANDROID_HOME/platforms/android-34/android.jar ./out/
```

## Публікація

1. Скопіюй `out/md3player-1.0.0.hmod` в `yuimodules/hmods/`.
2. Додай у `meta.json`:
```json
"md3player": {
  "name": "MD3-плеєр",
  "description": "Повний шит і міні-бар у стилі Material 3 з лірикою LRCLIB.",
  "author": "AmeGram Team",
  "category": "media",
  "featured": true,
  "branch": "stable",
  "permissions": ["hook_ui"],
  "changelog": { "1.0.0": "Перший реліз: шит, міні-бар, morph-перехід." }
}
```
3. `make_modules_json.py` → коміт → на клієнті `sync_from_yuimodules.sh`.

## Поведінка в клієнті

- Модуль встановлено й увімкнено + `md3Player/md3MiniPlayer` у зовнішності →
  MD3-шит/бар з модуля.
- Модуля нема → `Md3Router` іде у вбудований фолбек (ті самі класи з APK).
- Прапорці вимкнено → стоковий `AudioPlayerAlert`.
