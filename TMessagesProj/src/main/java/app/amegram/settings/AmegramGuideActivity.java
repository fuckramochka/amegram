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
import app.exteraless.plugins.ui.PluginsActivity;
import app.miogram.bridge.MiogramLocale;
import app.miogram.bridge.plugins.MiogramPluginsMarket;

/**
 * Руководство по плагинам и скриптам в стиле Material Design 3.
 * Наглядные карточки шагов вместо нечитаемого мелкого шрифта сносок.
 */
public class AmegramGuideActivity extends BaseFragment {

    private static final int ROW_STEP1 = 1;
    private static final int ROW_STEP2 = 2;
    private static final int ROW_STEP3 = 3;
    private static final int ROW_OPEN_PLUGINS = 10;
    private static final int ROW_OPEN_CATALOG = 11;

    private UniversalRecyclerView listView;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(MiogramLocale.get("Гід по плагінах", "Гид по плагинам", "Plugins guide"));
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
        items.add(UItem.asHeader(MiogramLocale.get("Швидкий старт", "Быстрый старт", "Quick start")));

        items.add(YumiSettingCell.Factory.of(ROW_STEP1, R.drawable.msg_settings_old, 0xFF2A87FF,
                MiogramLocale.get("Крок 1: Активація движка", "Шаг 1: Активация движка", "Step 1: Enable engine"),
                MiogramLocale.get("Відкрий розділ «Плагіни» та увімкни перемикач. Безпечний режим вимикає все в 1 клік.",
                        "Открой раздел «Плагины» и включи переключатель. Безопасный режим выключает всё в 1 клик.",
                        "Open Plugins section and toggle switch. Safe mode disables everything instantly.")));

        items.add(YumiSettingCell.Factory.of(ROW_STEP2, R.drawable.msg_download_solar, 0xFF00ACC1,
                MiogramLocale.get("Крок 2: Встановлення прикладів", "Шаг 2: Установка примеров", "Step 2: Install samples"),
                MiogramLocale.get("Обери плагін із каталогу: petpet.py, boykisser_meow.py, shrug_and_calc.lua.",
                        "Выбери плагин из каталога: petpet.py, boykisser_meow.py, shrug_and_calc.lua.",
                        "Pick a plugin from catalog: petpet.py, boykisser_meow.py, shrug_and_calc.lua.")));

        items.add(YumiSettingCell.Factory.of(ROW_STEP3, R.drawable.msg_bot, 0xFF8E24AA,
                MiogramLocale.get("Крок 3: Власні сценарії", "Шаг 3: Свои сценарии", "Step 3: Custom scripts"),
                MiogramLocale.get("Пиши скрипти на Python 3.11, Lua або Java. Кожен плагін просить дозволи окремо.",
                        "Пиши скрипты на Python 3.11, Lua или Java. Каждый плагин просит разрешения отдельно.",
                        "Write scripts in Python 3.11, Lua or Java. Each plugin requests permissions separately.")));

        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(MiogramLocale.get("Швидкі дії", "Быстрые действия", "Quick actions")));

        items.add(YumiSettingCell.Factory.of(ROW_OPEN_PLUGINS, R.drawable.msg_bot, 0xFFE5486B,
                MiogramLocale.get("Керування плагінами", "Управление плагинами", "Manage plugins"),
                MiogramLocale.get("Перегляд встановлених, увімкнення та логи",
                        "Просмотр установленных, включение и логи",
                        "Installed list, runtime toggles and audit log")));

        int count = 0;
        try {
            count = MiogramPluginsMarket.getCatalog().size();
        } catch (Throwable ignore) {}

        String catSubtitle = count > 0
                ? MiogramLocale.get("Доступно плагінів у каталозі: " + count, "Доступно плагинов в каталоге: " + count, "Available in catalog: " + count)
                : MiogramLocale.get("Перегляд онлайн-каталогу плагінів", "Просмотр онлайн-каталога плагинов", "Browse online plugin catalog");

        items.add(YumiSettingCell.Factory.of(ROW_OPEN_CATALOG, R.drawable.msg_fave, 0xFFFF8F00,
                MiogramLocale.get("Каталог плагінів", "Каталог плагинов", "Plugin catalog"),
                catSubtitle));

        items.add(UItem.asShadow(MiogramLocale.get(
                "Повний посібник для розробників: docs/AMEGRAM_PLUGINS_GUIDE.md",
                "Полное руководство для разработчиков: docs/AMEGRAM_PLUGINS_GUIDE.md",
                "Full developer guide in docs/AMEGRAM_PLUGINS_GUIDE.md")));
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ROW_OPEN_PLUGINS || item.id == ROW_OPEN_CATALOG) {
            presentFragment(new PluginsActivity());
        }
    }

    @Override
    public boolean isLightStatusBar() {
        return !Theme.isCurrentThemeDark();
    }
}
