package app.miogram.ui.yumi;

import app.miogram.bridge.MiogramLocale;

/** Inline uk/ru/en strings for the shared Yumi widgets. */
public final class YumiLocale {

    private YumiLocale() {
    }

    public static String back() {
        return MiogramLocale.get("Назад", "Назад", "Back");
    }

    public static String close() {
        return MiogramLocale.get("Закрити", "Закрыть", "Close");
    }

    public static String more() {
        return MiogramLocale.get("Ще", "Ещё", "More");
    }

    public static String search() {
        return MiogramLocale.get("Пошук", "Поиск", "Search");
    }

    public static String clear() {
        return MiogramLocale.get("Очистити", "Очистить", "Clear");
    }

    public static String done() {
        return MiogramLocale.get("Готово", "Готово", "Done");
    }

    public static String cancel() {
        return MiogramLocale.get("Скасувати", "Отмена", "Cancel");
    }

    public static String save() {
        return MiogramLocale.get("Зберегти", "Сохранить", "Save");
    }

    public static String retry() {
        return MiogramLocale.get("Повторити", "Повторить", "Retry");
    }

    public static String loading() {
        return MiogramLocale.get("Завантаження", "Загрузка", "Loading");
    }

    public static String selected() {
        return MiogramLocale.get("Вибрано", "Выбрано", "Selected");
    }

    public static String notSelected() {
        return MiogramLocale.get("Не вибрано", "Не выбрано", "Not selected");
    }

    public static String on() {
        return MiogramLocale.get("Увімкнено", "Включено", "On");
    }

    public static String off() {
        return MiogramLocale.get("Вимкнено", "Выключено", "Off");
    }

    public static String selectedCount(int count) {
        final String noun = MiogramLocale.plural(count,
                "вибраний", "вибраних", "вибраних",
                "выбран", "выбрано", "выбрано",
                "selected", "selected");
        return count + " " + noun;
    }

    public static String itemsCount(int count) {
        final String noun = MiogramLocale.plural(count,
                "елемент", "елементи", "елементів",
                "элемент", "элемента", "элементов",
                "item", "items");
        return count + " " + noun;
    }
}
