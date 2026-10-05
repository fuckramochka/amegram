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

import app.exteraless.plugins.ui.PluginsActivity;
import app.miogram.bridge.MiogramLocale;
import app.miogram.bridge.plugins.MiogramPluginsMarket;

/**
 * Гид по плагинам — лёгкая замена AmegramGuideSheet (896 строк с Discord+AGSL).
 * 3 шага текстом + кнопки действий. Дока для разработчиков: docs/AMEGRAM_PLUGINS_GUIDE.md
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
        items.add(UItem.asHeader(MiogramLocale.get("Крок 1", "Шаг 1", "Step 1")));
        items.add(UItem.asShadow(MiogramLocale.get(
                "Відкрий «Плагіни» та ввімкни движок. Безпечний режим вимикає все одним тумблером.",
                "Открой «Плагины» и включи движок. Безопасный режим выключает всё одним тумблером.",
                "Open Plugins and enable the engine. Safe mode disables everything with one toggle.")));

        items.add(UItem.asHeader(MiogramLocale.get("Крок 2", "Шаг 2", "Step 2")));
        items.add(UItem.asShadow(MiogramLocale.get(
                "Постав приклад з каталогу: petpet.py, boykisser_meow.py, shrug_and_calc.lua. Файли лежать у filesDir/plugins.",
                "Поставь пример из каталога: petpet.py, boykisser_meow.py, shrug_and_calc.lua. Файлы лежат в filesDir/plugins.",
                "Install a sample from catalog: petpet.py, boykisser_meow.py, shrug_and_calc.lua under filesDir/plugins.")));

        items.add(UItem.asHeader(MiogramLocale.get("Крок 3", "Шаг 3", "Step 3")));
        items.add(UItem.asShadow(MiogramLocale.get(
                "Пиши свої: Python (Chaquopy 3.11, BasePlugin), Lua або Java-хуки. Кожен плагін просить права окремо.",
                "Пиши свои: Python (Chaquopy 3.11, BasePlugin), Lua или Java-хуки. Каждый плагин просит права отдельно.",
                "Write your own: Python (Chaquopy 3.11, BasePlugin), Lua or Java hooks. Each plugin asks permissions separately.")));

        items.add(UItem.asHeader(MiogramLocale.get("Дії", "Действия", "Actions")));
        items.add(UItem.asSettingsCell(ROW_OPEN_PLUGINS,
                MiogramLocale.get("Відкрити плагіни", "Открыть плагины", "Open plugins"), ""));
        int count = 0;
        try {
            count = MiogramPluginsMarket.getCatalog().size();
        } catch (Throwable ignore) {}
        items.add(UItem.asSettingsCell(ROW_OPEN_CATALOG,
                MiogramLocale.get("Каталог", "Каталог", "Catalog"),
                count > 0 ? String.valueOf(count) : ""));
        items.add(UItem.asShadow(MiogramLocale.get(
                "Повний API — у docs/AMEGRAM_PLUGINS_GUIDE.md",
                "Полный API — в docs/AMEGRAM_PLUGINS_GUIDE.md",
                "Full API in docs/AMEGRAM_PLUGINS_GUIDE.md")));
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ROW_OPEN_PLUGINS) {
            presentFragment(new PluginsActivity());
        } else if (item.id == ROW_OPEN_CATALOG) {
            presentFragment(new PluginsActivity());
        }
    }

    @Override
    public boolean isLightStatusBar() {
        return !Theme.isCurrentThemeDark();
    }
}
