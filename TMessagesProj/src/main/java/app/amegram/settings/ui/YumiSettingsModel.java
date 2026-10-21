package app.amegram.settings.ui;

import java.util.ArrayList;
import java.util.List;

/** Plain, renderable description of the Yumi settings hub. */
public class YumiSettingsModel {

    public static final int KIND_NAV = 0;
    public static final int KIND_SWITCH = 1;
    public static final int KIND_VALUE = 2;
    public static final int KIND_DANGER = 3;

    public CharSequence pageTitle;
    public final Hero hero = new Hero();
    public final List<Quick> quick = new ArrayList<>();
    public final List<Section> sections = new ArrayList<>();
    /** Пункты клиентских настроек, доступные глобальному поиску с главного экрана. */
    public final List<Row> searchIndex = new ArrayList<>();
    public Action footerPrimary;
    public Action footerSecondary;
    public CharSequence footerNote;

    public static class Hero {
        public CharSequence eyebrow = "YUMIGRAM";
        public CharSequence title;
        public CharSequence subtitle;
        public CharSequence statModules;
        public CharSequence statVersion;
        public CharSequence statSecurity;
        public int logoRes;
    }

    public static class Quick {
        public int id;
        public String moduleId;
        public int icon;
        public int accent;
        public CharSequence title;
        public CharSequence subtitle;
        public boolean toggle;
        public boolean checked;
    }

    public static class Section {
        public CharSequence title;
        public CharSequence subtitle;
        public final List<Row> rows = new ArrayList<>();
    }

    public static class Row {
        public int id;
        public String moduleId;
        public int icon;
        public int accent;
        public CharSequence title;
        public CharSequence subtitle;
        public CharSequence value;
        public int kind = KIND_NAV;
        public boolean checked;
        public boolean enabled = true;
    }

    public static class Action {
        public int id;
        public String moduleId;
        public CharSequence label;
        public int icon;
        public boolean primary;
    }
}
