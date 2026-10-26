package app.amegram.hot.api;

import java.util.ArrayList;
import java.util.List;

/**
 * Декларативна строка налаштувань / хабу від хот-модуля.
 * Модуль не чіпає Telegram UI-класи — хост сам малює рядки.
 * Цей файл входить до hot-api.jar, проти якого збираються модулі.
 */
public final class HotRow {

    public static final int HEADER = 0;
    public static final int INFO = 1;
    public static final int SWITCH = 2;
    public static final int BUTTON = 3;
    public static final int INPUT = 4;
    public static final int SLIDER = 5;
    public static final int CHOICE = 6;
    public static final int COLOR = 7;

    public final int type;
    /** SWITCH/INPUT/SLIDER/CHOICE/COLOR: ключ у префах. BUTTON/інше: id для onSettingsAction. */
    public final String id;
    public final String title;
    public final String subtitle;
    public final boolean checked;
    public final int minValue;
    public final int maxValue;
    public final int intValue;
    public final String[] options;

    private HotRow(int type, String id, String title, String subtitle, boolean checked) {
        this(type, id, title, subtitle, checked, 0, 0, 0, null);
    }

    private HotRow(int type, String id, String title, String subtitle, boolean checked,
                   int minValue, int maxValue, int intValue, String[] options) {
        this.type = type;
        this.id = id == null ? "" : id;
        this.title = title == null ? "" : title;
        this.subtitle = subtitle == null ? "" : subtitle;
        this.checked = checked;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.intValue = intValue;
        this.options = options != null ? options : new String[0];
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

    /** Текстове поле (API-ключі, моделі, токени). value — поточне значення. */
    public static HotRow inputRow(String key, String title, String value) {
        return new HotRow(INPUT, key, title, value != null ? value : "", false);
    }

    /** Повзунок значень (радіуси, прозорість, висота). */
    public static HotRow slider(String key, String title, int min, int max, int value, String unit) {
        return new HotRow(SLIDER, key, title, unit != null ? unit : "", false, min, max, value, null);
    }

    public static HotRow slider(String key, String title, int min, int max, int value) {
        return slider(key, title, min, max, value, "");
    }

    /** Вибір одного з варіантів (стилі списку, теми, пресети). */
    public static HotRow choice(String key, String title, String subtitle, String[] options, int selectedIndex) {
        return new HotRow(CHOICE, key, title, subtitle != null ? subtitle : "", false, 0, 0, selectedIndex, options);
    }

    public static HotRow choice(String key, String title, String[] options, int selectedIndex) {
        return choice(key, title, "", options, selectedIndex);
    }

    /** Вибір кольору (акценти, підсвічування). */
    public static HotRow color(String key, String title, int color) {
        return new HotRow(COLOR, key, title, "", false, 0, 0, color, null);
    }

    /** Зручний збирач для fillSettings/fillHubRows. */
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

        public ListBuilder slider(String key, String title, int min, int max, int value, String unit) {
            rows.add(HotRow.slider(key, title, min, max, value, unit));
            return this;
        }

        public ListBuilder choice(String key, String title, String subtitle, String[] options, int selectedIndex) {
            rows.add(HotRow.choice(key, title, subtitle, options, selectedIndex));
            return this;
        }

        public ListBuilder color(String key, String title, int color) {
            rows.add(HotRow.color(key, title, color));
            return this;
        }

        public List<HotRow> build() {
            return rows;
        }
    }
}
