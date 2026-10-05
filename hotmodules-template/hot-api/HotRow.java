package app.amegram.hot.api;

import java.util.ArrayList;
import java.util.List;

/**
 * Декларативная строка настроек / хаба от хот-модуля.
 * Модуль не трогает Telegram UI-классы — хост сам рисует строки.
 * Этот файл входит в hot-api.jar, против которого собираются модули.
 */
public final class HotRow {

    public static final int HEADER = 0;
    public static final int INFO = 1;
    public static final int SWITCH = 2;
    public static final int BUTTON = 3;
    public static final int INPUT = 4;

    public final int type;
    /** SWITCH: ключ в [hotmod_<id>] префах. BUTTON/др.: id для onSettingsAction/onHubAction. */
    public final String id;
    public final String title;
    public final String subtitle;
    public final boolean checked;

    private HotRow(int type, String id, String title, String subtitle, boolean checked) {
        this.type = type;
        this.id = id == null ? "" : id;
        this.title = title == null ? "" : title;
        this.subtitle = subtitle == null ? "" : subtitle;
        this.checked = checked;
    }

    public static HotRow header(String title) {
        return new HotRow(HEADER, "", title, "", false);
    }

    public static HotRow info(String text) {
        return new HotRow(INFO, "", text, "", false);
    }

    public static HotRow button(String id, String title, String subtitle) {
        return new HotRow(BUTTON, id, title, subtitle, false);
    }

    public static HotRow button(String id, String title) {
        return button(id, title, "");
    }

    public static HotRow switchRow(String key, String title, String subtitle, boolean checked) {
        return new HotRow(SWITCH, key, title, subtitle, checked);
    }

    /** Текстове поле (API-ключі, моделі). value — поточне значення. */
    public static HotRow inputRow(String key, String title, String value) {
        return new HotRow(INPUT, key, title, value != null ? value : "", false);
    }

    /** Удобный сборщик для fillSettings/fillHubRows. */
    public static final class ListBuilder {
        private final ArrayList<HotRow> rows = new ArrayList<>();

        public ListBuilder header(String title) {
            rows.add(HotRow.header(title));
            return this;
        }

        public ListBuilder info(String text) {
            rows.add(HotRow.info(text));
            return this;
        }

        public ListBuilder button(String id, String title, String subtitle) {
            rows.add(HotRow.button(id, title, subtitle));
            return this;
        }

        public ListBuilder button(String id, String title) {
            rows.add(HotRow.button(id, title));
            return this;
        }

        public ListBuilder switchRow(String key, String title, String subtitle, boolean checked) {
            rows.add(HotRow.switchRow(key, title, subtitle, checked));
            return this;
        }

        public ListBuilder inputRow(String key, String title, String value) {
            rows.add(HotRow.inputRow(key, title, value));
            return this;
        }

        public List<HotRow> build() {
            return rows;
        }
    }
}
