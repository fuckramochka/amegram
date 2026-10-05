package app.amegram.settings;

import android.content.Context;
import android.view.View;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

import app.amegram.theme.YumiSettingCell;
import app.amegram.theme.YumiTheme;
import app.miogram.bridge.MiogramLocale;
import app.miogram.bridge.badge.MiogramBadgeBottomSheet;
import app.miogram.bridge.badge.MiogramBadgeGrantSheet;
import app.miogram.bridge.badge.MiogramBadgeManager;
import app.miogram.bridge.badge.MiogramBadgeType;

/**
 * Экран бейджиков сообщества в стиле Material Design 3.
 * Каталог стилей бейджей с предпросмотром, человекочитаемыми названиями
 * и тональными сквирклами вместо сырых кодовых строк.
 */
public class AmegramBadgesActivity extends BaseFragment {

    private static final int ROW_MY_BADGE = 1;
    private static final int ROW_GRANT = 2;

    private UniversalRecyclerView listView;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(MiogramLocale.get("Бейджики", "Бейджики", "Badges"));
        actionBar.setActionBarMenuOnItemClick(new org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });
        fragmentView = listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, null);
        listView.setSections();
        return fragmentView;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(MiogramLocale.get("Твій статус", "Твой статус", "Your status")));

        long selfId = getUserConfig() != null && getUserConfig().getCurrentUser() != null
                ? getUserConfig().getCurrentUser().id : 0;
        String badgeTitle = selfId > 0 ? MiogramBadgeManager.getBadgeTitle(selfId)
                : MiogramLocale.get("Увійди, щоб побачити бейдж", "Войди, чтобы увидеть бейдж", "Log in to see your badge");

        items.add(YumiSettingCell.Factory.of(ROW_MY_BADGE, R.drawable.msg_premium_normal, YumiTheme.getPrimary(),
                MiogramLocale.get("Мій бейдж", "Мой бейдж", "My badge"),
                badgeTitle + " • " + MiogramLocale.get("Натисніть для деталей", "Нажмите для подробностей", "Tap for details")));

        items.add(UItem.asShadow(MiogramLocale.get(
                "Бейджі видає спільнота Yumigram. Кеш оновлюється при відкритті профілю.",
                "Бейджи выдаёт сообщество Yumigram. Кэш обновляется при открытии профиля.",
                "Badges are granted by the Yumigram community. Cache refreshes on profile open.")));

        items.add(UItem.asHeader(MiogramLocale.get("Каталог стилів", "Каталог стилей", "Style catalog")));
        MiogramBadgeType[] types = MiogramBadgeType.values();
        for (int i = 0; i < types.length; i++) {
            MiogramBadgeType type = types[i];
            int color = getBadgeAccentColor(type);
            String title = type.getTitle();
            String subtitle = type.getCode() + " • " + MiogramLocale.get("Натисніть для перегляду", "Нажмите для предпросмотра", "Tap to preview");
            items.add(YumiSettingCell.Factory.of(100 + i, R.drawable.msg_settings_premium, color, title, subtitle));
        }

        items.add(UItem.asShadow(MiogramLocale.get(
                "Торкніться будь-якого стилю для демонстрації в інтерактивному вікні.",
                "Коснитесь любого стиля для демонстрации в интерактивном окне.",
                "Tap any style to see interactive preview sheet.")));

        // Опція видачі для адміністраторів/розробників
        if (selfId == MiogramBadgeManager.FOUNDER_USER_ID) {
            items.add(UItem.asHeader(MiogramLocale.get("Керування", "Управление", "Management")));
            items.add(YumiSettingCell.Factory.of(ROW_GRANT, R.drawable.msg_fave, 0xFF7E57C2,
                    MiogramLocale.get("Видати бейдж користувачу", "Выдать бейдж пользователю", "Grant badge to user"),
                    MiogramLocale.get("Діалог призначення бейджа за User ID", "Диалог назначения бейджа по User ID", "Assign badge by User ID")));
            items.add(UItem.asShadow(null));
        }
    }

    private int getBadgeAccentColor(MiogramBadgeType type) {
        switch (type) {
            case ANGEL: return 0xFF7E57C2;
            case DARK: return 0xFF37474F;
            case GLITCH: return 0xFF00C853;
            case PINK: return 0xFFFF4081;
            case CYAN: return 0xFF00B0FF;
            case DEVIL: return 0xFFD50000;
            case RAINBOW: return 0xFFFF6D00;
            case OUTLINE: return 0xFF607D8B;
            case PREMIUM: return 0xFFFFB300;
            case ORIGINAL:
            default:
                return YumiTheme.getPrimary();
        }
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        Context ctx = getContext();
        if (ctx == null) return;

        if (item.id == ROW_MY_BADGE) {
            long selfId = getUserConfig() != null && getUserConfig().getCurrentUser() != null
                    ? getUserConfig().getCurrentUser().id : MiogramBadgeManager.FOUNDER_USER_ID;
            MiogramBadgeBottomSheet.show(ctx, selfId);
        } else if (item.id == ROW_GRANT) {
            MiogramBadgeGrantSheet.show(ctx, 0, null);
        } else if (item.id >= 100) {
            // Интерактивный предпросмотр выбранного стиля
            MiogramBadgeBottomSheet.show(ctx, MiogramBadgeManager.FOUNDER_USER_ID);
        }
    }

    @Override
    public boolean isLightStatusBar() {
        return !Theme.isCurrentThemeDark();
    }
}
