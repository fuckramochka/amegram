package app.amegram.hot.api;

import android.content.Context;
import org.telegram.ui.ActionBar.BaseFragment;

/**
 * Контракт модуля Kangel Plugins Manager (KPM).
 * Дозволяє модулю керувати каталогом плагінів KPM Store,
 * обробляти deep-link'и (tg://kpm?install=...), перевіряти оновлення плагінів.
 */
public interface HotKpm {

    /** Відкрити магазин плагінів KPM Store. */
    void openStore(BaseFragment fragment, Context context);

    /** Обробити deep-link (tg://kpm?install=... або kpm://...). */
    boolean handleDeepLink(BaseFragment fragment, String url);

    /** Чи увімкнено аудит безпеки перед встановленням. */
    boolean isSecurityAuditEnabled();

    /** Активне дзеркало KPM Store. */
    String getActiveMirror();
}
