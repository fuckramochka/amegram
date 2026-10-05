package app.amegram.hot.ui;

import android.content.Context;
import android.view.View;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;
import java.util.List;

import app.amegram.hot.HotModulesManager;
import app.amegram.hot.api.HotHost;
import app.amegram.hot.api.HotModule;
import app.amegram.hot.api.HotRow;
import app.miogram.bridge.MiogramLocale;

/**
 * Вкладка настроек включённого модуля: хост рисует HotRow'ы модуля,
 * свитчи хранит сам, модуль узнаёт об изменениях через колбэки.
 */
public class HotModuleSettingsActivity extends BaseFragment {

    private String moduleId;
    private UniversalRecyclerView listView;
    private final List<HotRow> rows = new ArrayList<>();

    public HotModuleSettingsActivity() {
        this.moduleId = "";
    }

    public HotModuleSettingsActivity(String moduleId) {
        this.moduleId = moduleId != null ? moduleId : "";
    }

    private String resolveTitle() {
        HotModulesManager.Handle handle = HotModulesManager.getHandle(moduleId);
        if (handle != null && handle.instance != null) {
            String t = handle.instance.settingsTitle();
            if (t != null && !t.isEmpty()) return t;
        }
        if ("ghost".equals(moduleId)) {
            return MiogramLocale.get("Режим привида", "Режим призрака", "Ghost mode");
        }
        if ("vault".equals(moduleId)) {
            return MiogramLocale.get("Хмарне сховище", "Облачное хранилище", "Cloud vault");
        }
        if ("player".equals(moduleId)) {
            return MiogramLocale.get("Музичний плеєр", "Музыкальный плеер", "Music player");
        }
        if ("ame".equals(moduleId)) {
            return MiogramLocale.get("Кастомізація", "Кастомизация", "Customization");
        }
        if ("tiktok".equals(moduleId)) {
            return "TikTok MI";
        }
        if ("ai".equals(moduleId)) {
            return MiogramLocale.get("Штучний інтелект", "Искусственный интеллект", "Artificial Intelligence");
        }
        if ("experimental".equals(moduleId)) {
            return MiogramLocale.get("Експерименти", "Эксперименты", "Experimental");
        }
        if ("automation".equals(moduleId)) {
            return MiogramLocale.get("Автоматизація", "Автоматизация", "Automation");
        }
        return moduleId;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(resolveTitle());
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

    private HotModulesManager.Handle handle() {
        return HotModulesManager.getHandle(moduleId);
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        rows.clear();
        boolean isEnabled = HotModulesManager.isModuleEnabled(moduleId);
        items.add(UItem.asButtonCheck(10, MiogramLocale.get("Увімкнути функцію", "Включить функцию", "Enable feature"),
                isEnabled ? MiogramLocale.get("Функція активна", "Функция активна", "Feature active")
                          : MiogramLocale.get("Вимкнено", "Выключено", "Disabled")).setChecked(isEnabled));
        items.add(UItem.asShadow(null));

        HotModulesManager.Handle h = handle();
        HotModule mod = (h != null && h.instance != null) ? h.instance : HotModulesManager.createBuiltinModule(moduleId);
        if (mod == null) {
            items.add(UItem.asShadow(MiogramLocale.get(
                    "Модуль не знайдено або він видалений.",
                    "Модуль не найден или удалён.",
                    "Module not found or deleted.")));
            return;
        }

        HotHost host = HotModulesManager.hostFor(moduleId);
        try {
            mod.fillSettings(rows);
        } catch (Throwable ignore) {
        }
        if (rows.isEmpty()) {
            items.add(UItem.asShadow(MiogramLocale.get(
                    "Немає додаткових налаштувань.", "Нет дополнительных настроек.", "No additional settings.")));
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            HotRow r = rows.get(i);
            if (r.type == HotRow.HEADER) {
                items.add(UItem.asHeader(r.title));
            } else if (r.type == HotRow.INFO) {
                items.add(UItem.asShadow(r.title));
            } else if (r.type == HotRow.SWITCH) {
                boolean checked;
                try {
                    checked = host.getBool(r.id, r.checked);
                } catch (Throwable ignore) {
                    checked = r.checked;
                }
                items.add(UItem.asButtonCheck(100 + i, r.title, r.subtitle != null ? r.subtitle : "").setChecked(checked));
            } else if (r.type == HotRow.BUTTON) {
                items.add(UItem.asSettingsCell(100 + i, r.title,
                        r.subtitle != null ? r.subtitle : ""));
            } else if (r.type == HotRow.INPUT) {
                String cur = "";
                try {
                    cur = host.getString(r.id, r.subtitle != null ? r.subtitle : "");
                } catch (Throwable ignore) {
                }
                items.add(UItem.asSettingsCell(100 + i, r.title, cur));
            }
        }
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == 10) {
            boolean next = !HotModulesManager.isModuleEnabled(moduleId);
            HotModulesManager.setEnabled(moduleId, next, (ok, msg, data) -> {
                if (actionBar != null) actionBar.setTitle(resolveTitle());
                if (listView != null && listView.adapter != null) {
                    listView.adapter.update(true);
                }
            });
            return;
        }
        int idx = item.id - 100;
        if (idx < 0 || idx >= rows.size()) return;
        HotRow r = rows.get(idx);
        HotModulesManager.Handle h = handle();
        HotModule mod = (h != null && h.instance != null) ? h.instance : HotModulesManager.createBuiltinModule(moduleId);
        if (mod == null) return;
        HotHost host = HotModulesManager.hostFor(moduleId);
        try {
            if (r.type == HotRow.SWITCH) {
                boolean cur = host.getBool(r.id, r.checked);
                host.putBool(r.id, !cur);
                try {
                    mod.onSettingsToggle(r.id, !cur);
                } catch (Throwable ignore) {
                }
                listView.adapter.update(true);
            } else if (r.type == HotRow.BUTTON) {
                try {
                    mod.onSettingsAction(r.id);
                } catch (Throwable ignore) {
                }
                listView.adapter.update(true);
            } else if (r.type == HotRow.INPUT) {
                showInputDialog(host, mod, r);
            }
        } catch (Throwable ignore) {
        }
    }

    @Override
    public boolean isLightStatusBar() {
        return !Theme.isCurrentThemeDark();
    }

    private void showInputDialog(HotHost host, HotModule mod, HotRow r) {
        try {
            android.content.Context ctx = getContext();
            if (ctx == null) return;
            String cur = "";
            try {
                cur = host.getString(r.id, r.subtitle != null ? r.subtitle : "");
            } catch (Throwable ignore) {
            }
            final android.widget.EditText input = new android.widget.EditText(ctx);
            input.setText(cur);
            input.setSingleLine(true);
            int pad = org.telegram.messenger.AndroidUtilities.dp(16);
            input.setPadding(pad, pad, pad, pad);
            org.telegram.ui.ActionBar.AlertDialog.Builder builder =
                    new org.telegram.ui.ActionBar.AlertDialog.Builder(ctx);
            builder.setTitle(r.title);
            builder.setView(input);
            builder.setPositiveButton("OK", (d, w) -> {
                String v = input.getText().toString().trim();
                try {
                    host.putString(r.id, v);
                    mod.onSettingsInput(r.id, v);
                } catch (Throwable ignore) {
                }
                listView.adapter.update(true);
            });
            builder.setNegativeButton("Скасувати", null);
            builder.show();
        } catch (Throwable ignore) {
        }
    }
}
