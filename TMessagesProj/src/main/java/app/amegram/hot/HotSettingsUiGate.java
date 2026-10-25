package app.amegram.hot;

import android.content.Context;
import android.view.View;

import org.telegram.ui.ActionBar.BaseFragment;

import app.amegram.hot.api.HotServices;
import app.amegram.hot.api.HotSettingsUi;
import app.amegram.settings.ui.YumiSettingsModel;

/**
 * Гейт до системного модуля налаштувань («Внутрішній світ Yumi»).
 * Дозволяє модулю керувати дизайном та структурою екрана налаштувань.
 * За відсутності модуля діє прозорий надійний фолбек на стоковий YumiSettingsView.
 */
public final class HotSettingsUiGate {

    private HotSettingsUiGate() {
    }

    public static HotSettingsUi get() {
        try {
            return HotModulesManager.getService(HotServices.SETTINGS_UI);
        } catch (Throwable ignore) {
            return null;
        }
    }

    public static boolean isAvailable() {
        return get() != null;
    }

    public static void customizeModel(YumiSettingsModel model) {
        if (model == null) return;
        HotSettingsUi ui = get();
        if (ui != null) {
            try {
                ui.customizeModel(model);
            } catch (Throwable ignore) {
            }
        }
    }

    public static View createCustomSettingsView(BaseFragment fragment, Context context) {
        HotSettingsUi ui = get();
        if (ui != null) {
            try {
                return ui.createCustomSettingsView(fragment, context);
            } catch (Throwable ignore) {
            }
        }
        return null;
    }

    public static String getEngineName() {
        HotSettingsUi ui = get();
        if (ui != null) {
            try {
                String name = ui.getSettingsEngineName();
                if (name != null && !name.isEmpty()) return name;
            } catch (Throwable ignore) {
            }
        }
        return "Вбудований Yumi Core";
    }
}
