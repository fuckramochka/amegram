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

import app.miogram.bridge.MiogramLocale;
import app.miogram.bridge.badge.MiogramBadgeManager;
import app.miogram.bridge.badge.MiogramBadgeType;

/**
 * Бейджики сообщества. Офлайн-first поверх AmegramBadgesCache,
 * без тяжёлого MiogramSupabaseBridge (1187 строк не портируем).
 */
public class AmegramBadgesActivity extends BaseFragment {

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
        String title = selfId > 0 ? MiogramBadgeManager.getBadgeTitle(selfId)
                : MiogramLocale.get("Увійди, щоб побачити бейдж", "Войди, чтобы увидеть бейдж", "Log in to see your badge");
        items.add(UItem.asSettingsCell(1, MiogramLocale.get("Мій бейдж", "Мой бейдж", "My badge"), title));
        items.add(UItem.asShadow(MiogramLocale.get(
                "Бейджі видає спільнота. Кеш оновлюється лише при відкритті профілю.",
                "Бейджи выдаёт сообщество. Кэш обновляется только при открытии профиля.",
                "Badges are granted by the community. Cache refreshes only on profile open.")));

        items.add(UItem.asHeader(MiogramLocale.get("Всі стилі", "Все стили", "All styles")));
        MiogramBadgeType[] types = MiogramBadgeType.values();
        for (int i = 0; i < types.length; i++) {
            items.add(UItem.asSettingsCell(100 + i, types[i].getCode(), ""));
        }
        items.add(UItem.asShadow(null));
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        // превью-шит по тапу на стиль
        if (item.id >= 100) {
            try {
                Context ctx = getContext();
                if (ctx != null) {
                    app.miogram.bridge.badge.MiogramBadgeBottomSheet.show(ctx, MiogramBadgeManager.FOUNDER_USER_ID);
                }
            } catch (Throwable ignore) {}
        }
    }

    @Override
    public boolean isLightStatusBar() {
        return !Theme.isCurrentThemeDark();
    }
}
