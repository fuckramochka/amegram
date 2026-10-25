package com.amegram.mods.settings;

import android.content.Context;
import android.view.View;

import org.telegram.ui.ActionBar.BaseFragment;

import java.util.List;

import app.amegram.hot.api.HotHost;
import app.amegram.hot.api.HotModule;
import app.amegram.hot.api.HotRow;
import app.amegram.hot.api.HotServices;
import app.amegram.hot.api.HotSettingsUi;
import app.amegram.settings.ui.YumiSettingsModel;

/**
 * Системний модуль «Внутрішнього світу Yumi»:
 * керування та динамічна кастомізація інтерфейсу налаштувань Yumigram.
 */
public class SettingsUiModule implements HotModule, HotSettingsUi {

    private static final String[] EYEBROW_PRESETS = new String[]{
            "YUMIGRAM",
            "НАЛАШТУВАННЯ",
            "K-ANGEL EDITION",
            "ПРИВАТНІСТЬ ТА БЕЗПЕКА"
    };

    private HotHost host;

    public SettingsUiModule() {
    }

    @Override
    public String moduleId() {
        return "settings_ui";
    }

    @Override
    public void onAttach(Context appContext, HotHost host) {
        this.host = host;
        try {
            host.registerService(HotServices.SETTINGS_UI, this);
        } catch (Throwable ignore) {
        }
        try {
            host.log("settings_ui system module attached");
        } catch (Throwable ignore) {
        }
    }

    @Override
    public void onDetach() {
        try {
            if (host != null) {
                host.unregisterService(HotServices.SETTINGS_UI);
            }
        } catch (Throwable ignore) {
        }
        host = null;
    }

    @Override
    public boolean hasSettings() {
        return true;
    }

    @Override
    public String settingsTitle() {
        return "Інтерфейс налаштувань Yumi";
    }

    private boolean getHostBool(String key, boolean def) {
        return host != null ? host.getBool(key, def) : def;
    }

    private void setHostBool(String key, boolean val) {
        if (host != null) host.putBool(key, val);
    }

    private int getHostInt(String key, int def) {
        return host != null ? host.getInt(key, def) : def;
    }

    private void setHostInt(String key, int val) {
        if (host != null) host.putInt(key, val);
    }

    private String getHostString(String key, String def) {
        return host != null ? host.getString(key, def) : def;
    }

    private void setHostString(String key, String val) {
        if (host != null) host.putString(key, val);
    }

    private String getEffectiveEyebrow() {
        String custom = getHostString("hero_custom_eyebrow", "");
        if (custom != null && !custom.trim().isEmpty()) {
            return custom.trim();
        }
        int preset = getHostInt("hero_eyebrow_preset", 0);
        if (preset < 0 || preset >= EYEBROW_PRESETS.length) {
            preset = 0;
        }
        return EYEBROW_PRESETS[preset];
    }

    @Override
    public void fillSettings(List<HotRow> rows) {
        rows.add(HotRow.header("Головний банер (Hero)"));
        rows.add(HotRow.switchRow("hero_show_stats", "Блок статистики",
                "Показувати плашки активних модулів, версії та безпеки",
                getHostBool("hero_show_stats", true)));
        rows.add(HotRow.switchRow("hero_compact", "Компактний режим Hero",
                "Зменшити висоту банера та спростити підзаголовок",
                getHostBool("hero_compact", false)));
        rows.add(HotRow.button("action_cycle_eyebrow", "Стиль бейджа Hero",
                "Поточний: " + getEffectiveEyebrow()));
        rows.add(HotRow.inputRow("hero_custom_eyebrow", "Власний напис бейджа",
                getHostString("hero_custom_eyebrow", "")));

        rows.add(HotRow.header("Швидкі перемикачі (Quick Controls)"));
        rows.add(HotRow.switchRow("quick_tiles_visible", "Показувати швидкі картки",
                "Блок швидкого доступу 2×2 на головному екрані налаштувань",
                getHostBool("quick_tiles_visible", true)));
        rows.add(HotRow.switchRow("quick_ghost_first", "Пріоритет режиму привида",
                "Закріпити картку Ghost Mode першою у швидких діях",
                getHostBool("quick_ghost_first", true)));

        rows.add(HotRow.header("Списки та розділи"));
        rows.add(HotRow.switchRow("clean_subtitles", "Мінімалістичні розділи",
                "Приховати розлогі підзаголовки категорій",
                getHostBool("clean_subtitles", false)));
        rows.add(HotRow.switchRow("show_module_badges", "Позначки стану модулів",
                "Відображати статус активності у рядках списку",
                getHostBool("show_module_badges", true)));

        rows.add(HotRow.header("Системні інструменти"));
        rows.add(HotRow.button("action_reset_ui", "Скинути налаштування інтерфейсу",
                "Повернути стандартні параметри за замовчуванням"));
        rows.add(HotRow.info("Рушій: " + getSettingsEngineName()));
        rows.add(HotRow.info("Статус: Активний системний модуль з підтримкою гарячого оновлення."));
    }

    @Override
    public void onSettingsToggle(String key, boolean value) {
        setHostBool(key, value);
        if (host != null) {
            host.log("settings_ui toggle: " + key + " = " + value);
        }
    }

    @Override
    public void onSettingsAction(String key) {
        if (host == null) return;
        if ("action_cycle_eyebrow".equals(key)) {
            int current = getHostInt("hero_eyebrow_preset", 0);
            int next = (current + 1) % EYEBROW_PRESETS.length;
            setHostInt("hero_eyebrow_preset", next);
            setHostString("hero_custom_eyebrow", "");
            host.toast("Стиль бейджа: " + EYEBROW_PRESETS[next]);
        } else if ("action_reset_ui".equals(key)) {
            setHostBool("hero_show_stats", true);
            setHostBool("hero_compact", false);
            setHostInt("hero_eyebrow_preset", 0);
            setHostString("hero_custom_eyebrow", "");
            setHostBool("quick_tiles_visible", true);
            setHostBool("quick_ghost_first", true);
            setHostBool("clean_subtitles", false);
            setHostBool("show_module_badges", true);
            host.toast("Параметри інтерфейсу повернуто до стандартних");
        }
    }

    @Override
    public void onSettingsInput(String key, String value) {
        if (host == null) return;
        setHostString(key, value != null ? value.trim() : "");
        host.toast("Збережено");
    }

    // --- HotSettingsUi implementation ---

    @Override
    public void customizeModel(YumiSettingsModel model) {
        if (model == null) return;

        // 1. Hero banner
        if (model.hero != null) {
            model.hero.eyebrow = getEffectiveEyebrow();

            boolean showStats = getHostBool("hero_show_stats", true);
            if (!showStats) {
                model.hero.statModules = "";
                model.hero.statVersion = "";
                model.hero.statSecurity = "";
            } else {
                if (model.hero.statSecurity == null || model.hero.statSecurity.length() == 0) {
                    model.hero.statSecurity = "🔒 Приватність";
                }
            }

            boolean compact = getHostBool("hero_compact", false);
            if (compact) {
                model.hero.subtitle = "Швидке керування модулями та параметрами Yumi";
            }
        }

        // 2. Quick controls
        boolean showQuick = getHostBool("quick_tiles_visible", true);
        if (!showQuick) {
            model.quick.clear();
        } else if (getHostBool("quick_ghost_first", true)) {
            int ghostIdx = -1;
            for (int i = 0; i < model.quick.size(); i++) {
                YumiSettingsModel.Quick q = model.quick.get(i);
                if (q != null && "ghost".equals(q.moduleId)) {
                    ghostIdx = i;
                    break;
                }
            }
            if (ghostIdx > 0) {
                YumiSettingsModel.Quick g = model.quick.remove(ghostIdx);
                model.quick.add(0, g);
            }
        }

        // 3. Clean subtitles
        boolean cleanSubs = getHostBool("clean_subtitles", false);
        if (cleanSubs) {
            for (YumiSettingsModel.Section sec : model.sections) {
                if (sec != null) {
                    sec.subtitle = null;
                }
            }
        }

        // 4. Module badges / tags
        boolean showBadges = getHostBool("show_module_badges", true);
        if (showBadges) {
            for (YumiSettingsModel.Section sec : model.sections) {
                if (sec == null || sec.rows == null) continue;
                for (YumiSettingsModel.Row r : sec.rows) {
                    if (r != null && r.kind == YumiSettingsModel.KIND_SWITCH && r.checked && (r.value == null || r.value.length() == 0)) {
                        r.value = "Активний";
                    }
                }
            }
        }

        // 5. Підвал і пошуковий індекс більше НЕ налаштовуються модулем.
        // Хаб сам будує footer та searchIndex у finalizeModel() — це
        // захищає від дублів, якщо встановлена стара версія модуля.
    }

    @Override
    public View createCustomSettingsView(BaseFragment fragment, Context context) {
        // null означає використання оптимізованого реактивного YumiSettingsView
        // з урахуванням виклику customizeModel
        return null;
    }

    @Override
    public String getSettingsEngineName() {
        return "Yumigram Settings v1.1";
    }
}
