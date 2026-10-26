package app.amegram.hot.api;

import android.content.Context;
import android.graphics.drawable.Drawable;
import org.telegram.ui.ActionBar.BaseFragment;

/**
 * Контракт системного модуля бейджів та статусів спільноти (badges).
 * Завантажує актуальний реєстр бейджів із GitHub-репозиторію (badges.json),
 * кешує локально, надає векторний/піксельний рендеринг та діалог інформації.
 */
public interface HotBadges {

    /**
     * Чи має користувач активний бейдж у системі.
     */
    boolean hasBadge(long userId);

    /**
     * Отримати об'єкт Drawable для рендерингу бейджа.
     */
    Drawable getBadgeDrawable(long userId, int sizeDp);

    /**
     * Текстова назва або статус бейджа.
     */
    String getBadgeTitle(long userId);

    /**
     * Показати bottom sheet з інформацією про бейдж та вибором стилю.
     */
    void showBadgeSheet(Context context, long userId);

    /**
     * Відкрити фрагмент керування бейджами (майстерня / налаштування).
     */
    void openBadgesSettings(BaseFragment fragment);

    /**
     * Запустити примусове оновлення каталогу з GitHub (ETag).
     */
    void refreshBadges(Runnable onComplete);
}
