package app.amegram.settings;

import android.content.Context;
import android.view.View;
import android.widget.Toast;

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
import app.miogram.bridge.badge.MiogramSupabaseBridge;

/**
 * Екран бейджиків спільноти у сучасному стилі Material You / YumiMd3.
 * Відзнаки спільноти Miyumi з автоматичною хмарною синхронізацією.
 */
public class AmegramBadgesActivity extends BaseFragment {

    private static final int ROW_MY_BADGE = 1;
    private static final int ROW_GRANT = 2;
    private static final int ROW_MASTER_TOGGLE = 3;
    private static final int ROW_WORKSHOP = 4;
    private static final int ROW_STYLE_BASE = 100;

    private UniversalRecyclerView listView;
    private final java.util.List<MiogramBadgeType> catalogOrder = new java.util.ArrayList<>();

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(MiogramLocale.get("Бейджики спільноти", "Бейджики сообщества", "Community Badges"));
        actionBar.setActionBarMenuOnItemClick(new org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });
        fragmentView = listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, null);
        listView.setSections();

        // Автоматична фонова синхронізація без нав'язливих кнопок
        MiogramBadgeManager.refreshBadges(() -> {
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        });

        return fragmentView;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(MiogramLocale.get("Керування та статус", "Управление и статус", "Controls & Status")));

        boolean masterEnabled = MiogramBadgeManager.isModuleEnabled();
        items.add(UItem.asCheck(ROW_MASTER_TOGGLE,
                MiogramLocale.get("Відображати бейджики спільноти", "Отображать бейджики сообщества", "Display community badges"))
                .setChecked(masterEnabled));

        long selfId = getUserConfig() != null && getUserConfig().getCurrentUser() != null
                ? getUserConfig().getCurrentUser().id : 0;
        boolean hasBadge = selfId > 0 && MiogramBadgeManager.hasArrow(selfId);

        if (hasBadge) {
            String badgeTitle = MiogramBadgeManager.getBadgeTitle(selfId);
            android.graphics.drawable.Drawable myIcon = null;
            try {
                myIcon = new app.miogram.bridge.badge.MiogramArrowDrawable(28,
                        MiogramBadgeManager.getBadgeType(selfId));
            } catch (Throwable ignore) {
            }
            if (myIcon != null) {
                items.add(YumiSettingCell.Factory.ofDrawable(ROW_MY_BADGE, myIcon,
                        MiogramLocale.get("Мій бейдж", "Мой бейдж", "My badge"),
                        badgeTitle + " • " + MiogramLocale.get("Натисніть для деталей", "Нажмите для подробностей", "Tap for details")));
            } else {
                items.add(YumiSettingCell.Factory.of(ROW_MY_BADGE, R.drawable.msg_premium_normal, YumiTheme.getPrimary(),
                        MiogramLocale.get("Мій бейдж", "Мой бейдж", "My badge"),
                        badgeTitle + " • " + MiogramLocale.get("Натисніть для деталей", "Нажмите для подробностей", "Tap for details")));
            }
        } else {
            items.add(YumiSettingCell.Factory.of(ROW_MY_BADGE, R.drawable.msg_settings_premium, Theme.getColor(Theme.key_windowBackgroundWhiteGrayText),
                    MiogramLocale.get("Мій бейдж", "Мой бейдж", "My badge"),
                    MiogramLocale.get("Відзнака відсутня • Видається засновником", "Отличие отсутствует • Выдаётся создателем", "No badge • Granted by founder")));
        }

        items.add(YumiSettingCell.Factory.of(ROW_WORKSHOP, R.drawable.msg_edit, YumiTheme.getPrimary(),
                MiogramLocale.get("Майстерня бейджиків", "Мастерская бейджиков", "Badge Workshop"),
                MiogramLocale.get("Створення та перегляд SVG/PNG бейджів 128x128",
                        "Создание и просмотр SVG/PNG бейджей 128x128",
                        "Design & test custom 128x128 vector/pixel badges")));

        items.add(UItem.asShadow(MiogramLocale.get(
                "Бейджики видаються спільнотою Miyumi (@fuckramochka). Синхронізація відбувається автоматично у фоні.",
                "Бейджики выдаются сообществом Miyumi (@fuckramochka). Синхронизация происходит автоматически в фоне.",
                "Badges are granted by the Miyumi community (@fuckramochka). Sync runs automatically in background.")));

        // Опція видачі для адміністратора/засновника
        if (selfId == MiogramBadgeManager.FOUNDER_USER_ID) {
            items.add(UItem.asHeader(MiogramLocale.get("Керування спільнотою", "Управление сообществом", "Community Management")));
            items.add(YumiSettingCell.Factory.of(ROW_GRANT, R.drawable.msg_fave, 0xFF7E57C2,
                    MiogramLocale.get("Видати бейдж користувачу", "Выдать бейдж пользователю", "Grant badge to user"),
                    MiogramLocale.get("Призначити або змінити стиль відзнаки за User ID", "Назначить или изменить стиль отличия по User ID", "Assign or change badge style by User ID")));
            items.add(UItem.asShadow(null));
        }

        // Каталог стилів
        items.add(UItem.asHeader(MiogramLocale.get("Каталог стилів", "Каталог стилей", "Style Catalog")));
        catalogOrder.clear();
        MiogramBadgeType[] types = MiogramBadgeType.values();
        MiogramBadgeType myActiveType = selfId > 0 ? MiogramBadgeManager.getBadgeType(selfId) : null;

        for (int i = 0; i < types.length; i++) {
            catalogOrder.add(types[i]);
            MiogramBadgeType type = types[i];
            String title = type.getTitle();
            boolean isCurrent = (myActiveType != null && myActiveType == type);
            String subtitle = type.getCode() + (isCurrent ? " • " + MiogramLocale.get("Активний ✓", "Активен ✓", "Active ✓") : "");

            android.graphics.drawable.Drawable icon = null;
            try {
                icon = new app.miogram.bridge.badge.MiogramArrowDrawable(28, type);
            } catch (Throwable ignore) {
            }
            if (icon != null) {
                items.add(YumiSettingCell.Factory.ofDrawable(ROW_STYLE_BASE + i, icon, title, subtitle));
            } else {
                int color = getBadgeAccentColor(type);
                items.add(YumiSettingCell.Factory.of(ROW_STYLE_BASE + i, R.drawable.msg_settings_premium, color, title, subtitle));
            }
        }
        items.add(UItem.asShadow(MiogramLocale.get(
                "Торкніться будь-якого стилю для перегляду або вибору для власного профілю.",
                "Нажмите на любой стиль для просмотра или выбора для своего профиля.",
                "Tap any style to preview or apply to your profile.")));
    }

    private int getBadgeAccentColor(MiogramBadgeType type) {
        if (type == null) return YumiTheme.getPrimary();
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

        if (item.id == ROW_MASTER_TOGGLE) {
            MiogramBadgeManager.setModuleEnabled(!MiogramBadgeManager.isModuleEnabled());
            if (listView != null && listView.adapter != null) listView.adapter.update(true);
        } else if (item.id == ROW_MY_BADGE) {
            long selfId = getUserConfig() != null && getUserConfig().getCurrentUser() != null
                    ? getUserConfig().getCurrentUser().id : 0;
            if (selfId > 0 && MiogramBadgeManager.hasArrow(selfId)) {
                MiogramBadgeBottomSheet.show(ctx, selfId);
            } else {
                Toast.makeText(ctx, MiogramLocale.get(
                        "У вас наразі немає активного бейджа. Відзнаки видаються виключно засновником спільноти Miyumi (@fuckramochka).",
                        "У вас пока нет активного отличия. Отличия выдаются исключительно создателем сообщества Miyumi (@fuckramochka).",
                        "You currently do not have an active badge. Badges are granted exclusively by the founder of Miyumi (@fuckramochka)."), Toast.LENGTH_LONG).show();
            }
        } else if (item.id == ROW_WORKSHOP) {
            presentFragment(new AmegramBadgeWorkshopActivity());
        } else if (item.id == ROW_GRANT) {
            MiogramBadgeGrantSheet.show(ctx);
        } else if (item.id >= ROW_STYLE_BASE && item.id < ROW_STYLE_BASE + catalogOrder.size()) {
            int idx = item.id - ROW_STYLE_BASE;
            MiogramBadgeType pickedType = catalogOrder.get(idx);
            long selfId = getUserConfig() != null && getUserConfig().getCurrentUser() != null
                    ? getUserConfig().getCurrentUser().id : 0;

            if (selfId > 0 && (MiogramBadgeManager.hasArrow(selfId) || selfId == MiogramBadgeManager.FOUNDER_USER_ID)) {
                MiogramSupabaseBridge.setSelectedBadgeForAccount(ctx, selfId, pickedType);
                Toast.makeText(ctx, MiogramLocale.get("Стиль обрано: ", "Стиль выбран: ", "Style selected: ") + pickedType.getTitle(), Toast.LENGTH_SHORT).show();
                if (listView != null && listView.adapter != null) {
                    listView.adapter.update(true);
                }
            }
            MiogramBadgeBottomSheet.show(ctx, selfId, pickedType);
        }
    }

    @Override
    public boolean isLightStatusBar() {
        return !Theme.isCurrentThemeDark();
    }
}
