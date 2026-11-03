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

    private static String[] getEyebrowPresets() {
        return new String[]{
                "YUMIGRAM",
                t("НАЛАШТУВАННЯ", "НАСТРОЙКИ", "SETTINGS"),
                "K-ANGEL EDITION",
                t("ПРИВАТНІСТЬ ТА БЕЗПЕКА", "ПРИВАТНОСТЬ И БЕЗОПАСНОСТЬ", "PRIVACY & SECURITY")
        };
    }

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

    private static String t(String uk, String ru, String en) {
        try {
            Class<?> locale = Class.forName("app.miogram.bridge.MiogramLocale");
            java.lang.reflect.Method get = locale.getMethod("get", String.class, String.class, String.class);
            Object value = get.invoke(null, uk, ru, en);
            if (value instanceof String) return (String) value;
        } catch (Throwable ignore) {
        }
        try {
            Class<?> lc = Class.forName("org.telegram.messenger.LocaleController");
            Object inst = lc.getMethod("getInstance").invoke(null);
            java.util.Locale current = (java.util.Locale) lc.getMethod("getCurrentLocale").invoke(inst);
            if (current != null) {
                String lang = current.getLanguage().toLowerCase();
                if (lang.startsWith("uk")) return uk;
                if (lang.startsWith("ru") || lang.startsWith("be") || lang.startsWith("kk")) return ru;
            }
        } catch (Throwable ignore) {
        }
        return uk != null ? uk : (en != null ? en : "");
    }

    @Override
    public boolean hasSettings() {
        return true;
    }

    @Override
    public String settingsTitle() {
        return t("Інтерфейс налаштувань Yumi", "Интерфейс настроек Yumi", "Yumi Settings Interface");
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
        String[] presets = getEyebrowPresets();
        int preset = getHostInt("hero_eyebrow_preset", 0);
        if (preset < 0 || preset >= presets.length) {
            preset = 0;
        }
        return presets[preset];
    }

    @Override
    public void fillSettings(List<HotRow> rows) {
        rows.add(HotRow.header(t("Головний банер", "Главный баннер", "Hero Banner")));
        rows.add(HotRow.switchRow("hero_show_stats",
                t("Блок статистики", "Блок статистики", "Statistics Block"),
                t("Показувати плашки активних модулів, версії та безпеки",
                  "Показывать плашки активных модулей, версии и безопасности",
                  "Show active modules, app version and security badges"),
                getHostBool("hero_show_stats", true)));
        rows.add(HotRow.switchRow("hero_compact",
                t("Компактний банер", "Компактный баннер", "Compact Banner"),
                t("Зменшити висоту банера та спростити підзаголовок",
                  "Уменьшить высоту баннера и упростить подзаголовок",
                  "Reduce banner height and streamline subtitle"),
                getHostBool("hero_compact", false)));
        rows.add(HotRow.button("action_cycle_eyebrow",
                t("Стиль бейджа банера", "Стиль бейджа баннера", "Banner Badge Style"),
                t("Поточний: ", "Текущий: ", "Current: ") + getEffectiveEyebrow()));
        rows.add(HotRow.inputRow("hero_custom_eyebrow",
                t("Власний напис бейджа", "Своя надпись бейджа", "Custom Badge Text"),
                getHostString("hero_custom_eyebrow", "")));

        rows.add(HotRow.header(t("Швидкі перемикачі", "Быстрые переключатели", "Quick Controls")));
        rows.add(HotRow.switchRow("quick_tiles_visible",
                t("Показувати швидкі картки", "Показывать быстрые карточки", "Show Quick Tiles"),
                t("Блок швидкого доступу 2×2 на головному екрані налаштувань",
                  "Блок быстрого доступа 2×2 на главном экране настроек",
                  "2×2 quick action grid on the settings screen"),
                getHostBool("quick_tiles_visible", true)));
        rows.add(HotRow.switchRow("quick_ghost_first",
                t("Пріоритет режиму привида", "Приоритет режима призрака", "Ghost Mode Priority"),
                t("Закріпити картку режиму привида першою у швидких діях",
                  "Закрепить карточку режима призрака первой в быстрых действиях",
                  "Pin Ghost Mode card first in quick actions"),
                getHostBool("quick_ghost_first", true)));

        rows.add(HotRow.header(t("Списки та розділи", "Списки и разделы", "Lists & Sections")));
        rows.add(HotRow.switchRow("clean_subtitles",
                t("Мінімалістичні розділи", "Минималистичные разделы", "Minimalist Sections"),
                t("Приховати розлогі підзаголовки категорій",
                  "Скрыть длинные подзаголовки категорий",
                  "Hide verbose category subtitles"),
                getHostBool("clean_subtitles", false)));
        rows.add(HotRow.switchRow("show_module_badges",
                t("Позначки стану модулів", "Отметки состояния модулей", "Module Status Badges"),
                t("Відображати статус активності у рядках списку",
                  "Отображать статус активности в строках списка",
                  "Display activity status tags in list rows"),
                getHostBool("show_module_badges", true)));

        rows.add(HotRow.header(t("Системні інструменти", "Системные инструменты", "System Tools")));
        rows.add(HotRow.button("action_reset_ui",
                t("Скинути налаштування інтерфейсу", "Сбросить настройки интерфейса", "Reset Interface Settings"),
                t("Повернути стандартні параметри за замовчуванням",
                  "Вернуть стандартные параметры по умолчанию",
                  "Restore default appearance parameters")));
        rows.add(HotRow.info(t("Рушій: ", "Движок: ", "Engine: ") + getSettingsEngineName()));
        rows.add(HotRow.info(t("Статус: Активний системний модуль з підтримкою гарячого оновлення.",
                "Статус: Активный системный модуль с поддержкой горячего обновления.",
                "Status: Active system module with hot-reload support.")));
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
            String[] presets = getEyebrowPresets();
            int current = getHostInt("hero_eyebrow_preset", 0);
            int next = (current + 1) % presets.length;
            setHostInt("hero_eyebrow_preset", next);
            setHostString("hero_custom_eyebrow", "");
            host.toast(t("Стиль бейджа: ", "Стиль бейджа: ", "Badge style: ") + presets[next]);
        } else if ("action_reset_ui".equals(key)) {
            setHostBool("hero_show_stats", true);
            setHostBool("hero_compact", false);
            setHostInt("hero_eyebrow_preset", 0);
            setHostString("hero_custom_eyebrow", "");
            setHostBool("quick_tiles_visible", true);
            setHostBool("quick_ghost_first", true);
            setHostBool("clean_subtitles", false);
            setHostBool("show_module_badges", true);
            host.toast(t("Параметри інтерфейсу повернуто до стандартних", "Параметры интерфейса сброшены к стандартным", "Interface settings restored to defaults"));
        }
    }

    @Override
    public void onSettingsInput(String key, String value) {
        if (host == null) return;
        setHostString(key, value != null ? value.trim() : "");
        host.toast(t("Збережено", "Сохранено", "Saved"));
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
                    model.hero.statSecurity = t("🔒 Приватність", "🔒 Приватность", "🔒 Privacy");
                }
            }

            boolean compact = getHostBool("hero_compact", false);
            if (compact) {
                model.hero.subtitle = t("Швидке керування модулями та параметрами Yumi", "Быстрое управление модулями и параметрами Yumi", "Quick management of Yumi modules and settings");
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
                        r.value = t("Активний", "Активен", "Active");
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
