package app.amegram.hot.api;

import android.content.Context;
import android.view.View;
import org.telegram.ui.ActionBar.BaseFragment;
import app.amegram.settings.ui.YumiSettingsModel;

/**
 * Контракт системного модуля керування інтерфейсом налаштувань Yumigram.
 * Належить до «Внутрішнього світу Yumi» (System HotModule).
 */
public interface HotSettingsUi {

    /**
     * Кастомізувати або модифікувати модель налаштувань перед побудовою View
     * (зміна Hero-банера, заголовків, швидких карток, стилів, секцій).
     */
    void customizeModel(YumiSettingsModel model);

    /**
     * Надати альтернативний повний View налаштувань замість стандартного.
     * Якщо повертає null — використовується оптимізований стандартний YumiSettingsView
     * з урахуванням виклику {@link #customizeModel(YumiSettingsModel)}.
     */
    View createCustomSettingsView(BaseFragment fragment, Context context);

    /**
     * Назва стилю або активного рушія налаштувань (для відображення статусу).
     */
    String getSettingsEngineName();
}
