package app.miogram.bridge.wall;

import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import app.miogram.bridge.MiogramLocale;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Switch;

import java.util.ArrayList;
import java.util.List;

import app.miogram.bridge.wall.WallModels.Wall;

/**
 * Налаштування стіни (лише для автора):
 * • хто може додавати елементи (я / всі / контакти / папка 1 / обрані люди);
 * • лайки, відповіді, малюнки, показ на банері;
 * • скинути стіну; скопіювати JSON для майбутнього синку з VPS.
 */
public class WallSettingsSheet extends BottomSheet {

    public interface PeoplePickerLauncher {
        void launchPeoplePicker(List<Long> currentIds);
    }

    public interface RefreshCallback {
        void onChanged();
    }

    private final Context context;
    private final BaseFragment parentFragment;
    private final Wall wall;
    private final PeoplePickerLauncher peopleLauncher;
    private final RefreshCallback refresh;

    private LinearLayout modeRowValue;

    public WallSettingsSheet(Context context, BaseFragment parentFragment, Wall wall, PeoplePickerLauncher peopleLauncher, RefreshCallback refresh) {
        super(context, false, null);
        this.context = context;
        this.parentFragment = parentFragment;
        this.wall = wall;
        this.peopleLauncher = peopleLauncher;
        this.refresh = refresh;

        setApplyTopPadding(true);
        setApplyBottomPadding(true);
        setTitle(MiogramLocale.get("Налаштування стіни", "Настройки стены", "Wall settings"));

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(WallUi.pill(WallUi.dark() ? 0xF2150F2E : 0xF8FFFFFF, 0x2EFFFFFF, 20));
        root.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(10), AndroidUtilities.dp(14), AndroidUtilities.dp(14));

        // --- хто може правити ---
        TextView modeCaption = new TextView(context);
        modeCaption.setText(MiogramLocale.get("Хто може додавати елементи", "Кто может добавлять элементы", "Who can add elements"));
        WallUi.styleTitle(modeCaption, 13.5f);
        root.addView(modeCaption, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 6));

        modeRowValue = new LinearLayout(context);
        modeRowValue.setOrientation(LinearLayout.VERTICAL);
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(WallUi.pill(WallUi.dark() ? 0x18FFFFFF : 0x12000000, 0x33FFFFFF, 16));
        card.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(8), AndroidUtilities.dp(10), AndroidUtilities.dp(8));
        card.addView(modeRowValue, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        card.setOnClickListener(v -> showModesDialog());
        root.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));
        refreshModeRow(context);

        // --- перемикачі ---
        LinearLayout toggles = new LinearLayout(context);
        toggles.setOrientation(LinearLayout.VERTICAL);
        toggles.setBackground(WallUi.pill(WallUi.dark() ? 0x18FFFFFF : 0x12000000, 0, 16));
        toggles.setPadding(AndroidUtilities.dp(6), 0, AndroidUtilities.dp(6), 0);
        toggles.addView(buildToggleRow(context, MiogramLocale.get("Лайки", "Лайки", "Likes"), () -> wall.allowLikes,
                v -> {
                    wall.allowLikes = v;
                    save();
                }));
        toggles.addView(buildDivider(context));
        toggles.addView(buildToggleRow(context, MiogramLocale.get("Відповіді", "Ответы", "Answers"), () -> wall.allowComments,
                v -> {
                    wall.allowComments = v;
                    save();
                }));
        toggles.addView(buildDivider(context));
        toggles.addView(buildToggleRow(context, MiogramLocale.get("Малюнки дозволяти", "Рисунки разрешить", "Allow drawings"), () -> wall.allowDrawings,
                v -> {
                    wall.allowDrawings = v;
                    save();
                }));
        toggles.addView(buildDivider(context));
        toggles.addView(buildToggleRow(context, MiogramLocale.get("Показувати стіну на банері", "Показывать стену на банере", "Show wall on banner"), () -> wall.showOnBanner,
                v -> {
                    wall.showOnBanner = v;
                    save();
                }));
        root.addView(toggles, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));

        // --- статус та синхронізація ---
        TextView syncStatus = new TextView(context);
        syncStatus.setText("◉ " + MiogramLocale.get("Локальна бета · синхронізація з VPS: незабаром", "Локальная бета · синхронизация с VPS: скоро", "Local beta · VPS sync: soon"));
        WallUi.styleBody(syncStatus, 11.5f, 0x88FFFFFF);
        root.addView(syncStatus, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 2));
        TextView copyJson = new TextView(context);
        WallUi.styleBody(copyJson, 12.5f, WallUi.CYAN);
        copyJson.setText("⇪ " + MiogramLocale.get("Скопіювати JSON стіни (для VPS)", "Скопировать JSON стены (для VPS)", "Copy wall JSON (for VPS)"));
        copyJson.setOnClickListener(v -> {
            try {
                WallUi.hapticTap(v);
                android.content.ClipboardManager cm = (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("wall", WallStorage.buildSyncEnvelope(wall)));
                    toast("JSON у буфері обміну ✓", "JSON в буфере обмена ✓", "JSON copied ✓");
                }
            } catch (Throwable ignore) {
            }
        });
        root.addView(copyJson, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 12));

        // --- скинути ---
        TextView reset = new TextView(context);
        WallUi.styleBody(reset, 13f, 0xFFFF6B81);
        reset.setText("✕ " + MiogramLocale.get("Скинути стіну (видалити всі елементи)", "Сбросить стену (удалить все элементы)", "Reset wall (delete all elements)"));
        reset.setOnClickListener(v -> {
            WallUi.hapticClick(v);
            if (parentFragment == null || parentFragment.getParentActivity() == null) return;
            AlertDialog.Builder b = new AlertDialog.Builder(parentFragment.getParentActivity());
            b.setTitle(MiogramLocale.get("Скинути стіну?", "Сбросить стену?", "Reset wall?"));
            b.setMessage(MiogramLocale.get("Всі елементи, лайки та відповіді буде видалено. Це необоротно.",
                    "Все элементы, лайки и ответы будут удалены. Это необратимо.",
                    "All elements, likes and answers will be deleted. This cannot be undone."));
            b.setPositiveButton(MiogramLocale.get("Скинути", "Сбросить", "Reset"), (d, w) -> {
                WallStorage.clearWall(wall);
                save();
                onChanged();
                dismiss();
            });
            b.setNegativeButton(MiogramLocale.get("Скасувати", "Отмена", "Cancel"), null);
            parentFragment.showDialog(b.create());
        });
        root.addView(reset, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 8));

        setCustomView(root);
    }

    private void save() {
        WallStorage.saveWall(wall);
        onChanged();
    }

    private void onChanged() {
        if (refresh != null) refresh.onChanged();
    }

    private void toast(String uk, String ru, String en) {
        try {
            android.widget.Toast.makeText(context, MiogramLocale.get(uk, ru, en), android.widget.Toast.LENGTH_SHORT).show();
        } catch (Throwable ignore) {
        }
    }

    private void refreshModeRow(Context context) {
        if (modeRowValue == null || context == null) return;
        modeRowValue.removeAllViews();
        String value = modeLabel(wall.editMode);
        TextView current = new TextView(context);
        current.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        current.setTextColor(WallUi.textPrimary());
        current.setTypeface(AndroidUtilities.bold());
        current.setText(value + "  ›");
        modeRowValue.addView(current, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        String sub = "";
        if (WallModels.EDIT_SELECTED.equals(wall.editMode)) {
            sub = (wall.editorIds == null ? 0 : wall.editorIds.size()) + " " + MiogramLocale.get("людей", "человек", "people");
        } else if (WallModels.EDIT_FOLDER.equals(wall.editMode)) {
            sub = WallStorage.folderName(Math.max(0, wall.folderIndex));
        } else if (WallModels.EDIT_EVERYONE.equals(wall.editMode)) {
            sub = MiogramLocale.get("кожен може покласти свій елемент", "каждый может положить свой элемент", "everyone can place their element");
        } else if (WallModels.EDIT_CONTACTS.equals(wall.editMode)) {
            sub = MiogramLocale.get("редагування — тільки для контактів", "редактирование — только для контактов", "editing for your contacts");
        }
        TextView subView = new TextView(context);
        subView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11.5f);
        subView.setTextColor(0x88FFFFFF);
        subView.setText(sub);
        modeRowValue.addView(subView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
    }

    private String modeLabel(String mode) {
        String m = mode == null ? WallModels.EDIT_EVERYONE : mode;
        switch (m) {
            case WallModels.EDIT_OWNER:
                return "🔒 " + MiogramLocale.get("Тільки я", "Только я", "Only me");
            case WallModels.EDIT_CONTACTS:
                return "👤 " + MiogramLocale.get("Мої контакти", "Мои контакты", "My contacts");
            case WallModels.EDIT_FOLDER:
                return "🗂 " + MiogramLocale.get("Папка чатів", "Папка чатов", "Chats folder");
            case WallModels.EDIT_SELECTED:
                return "★ " + MiogramLocale.get("Обрані люди", "Избранные люди", "Selected people");
            case WallModels.EDIT_EVERYONE:
            default:
                return "🌏 " + MiogramLocale.get("Усі", "Все", "Everyone");
        }
    }

    private void showModesDialog() {
        if (parentFragment == null || parentFragment.getParentActivity() == null) return;
        WallUi.hapticTap(modeRowValue);
        String[] labels = new String[]{
                "🔒 " + MiogramLocale.get("Тільки я", "Только я", "Only me"),
                "🌏 " + MiogramLocale.get("Усі", "Все", "Everyone"),
                "👤 " + MiogramLocale.get("Мої контакти", "Мои контакты", "My contacts"),
                "🗂 " + MiogramLocale.get("Папка чатів", "Папка чатов", "Chats folder"),
                "★ " + MiogramLocale.get("Обрані люди", "Избранные люди", "Selected people")
        };
        String[] modes = {WallModels.EDIT_OWNER, WallModels.EDIT_EVERYONE, WallModels.EDIT_CONTACTS, WallModels.EDIT_FOLDER, WallModels.EDIT_SELECTED};
        int checked = 0;
        for (int i = 0; i < modes.length; i++) {
            if (modes[i].equals(wall.editMode)) {
                checked = i;
            }
        }
        CharSequence[] items = new CharSequence[labels.length];
        for (int i = 0; i < labels.length; i++) {
            items[i] = (i == checked ? "✓  " : "    ") + labels[i];
        }
        AlertDialog.Builder b = new AlertDialog.Builder(parentFragment.getParentActivity());
        b.setTitle(MiogramLocale.get("Хто може додавати елементи", "Кто может добавлять элементы", "Who can add elements"));
        b.setItems(items, (d, w) -> {
            wall.editMode = modes[w];
            if (WallModels.EDIT_SELECTED.equals(wall.editMode) && (wall.editorIds == null || wall.editorIds.isEmpty())) {
                d.dismiss();
                if (peopleLauncher != null) {
                    peopleLauncher.launchPeoplePicker(wall.editorIds == null ? new ArrayList<>() : wall.editorIds);
                }
                return;
            }
            save();
            Context context = getContext();
            refreshModeRow(context);
        });
        b.setNegativeButton(MiogramLocale.get("Скасувати", "Отмена", "Cancel"), null);
        parentFragment.showDialog(b.create());
    }

    public void onPeopleSelected() {
        Context ctx = getContext();
        refreshModeRow(ctx);
    }

    private interface BoolGetter {
        boolean get();
    }

    private interface BoolSetter {
        void set(boolean value);
    }

    private View buildToggleRow(Context context, String title, BoolGetter getter, BoolSetter setter) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(10), AndroidUtilities.dp(4), AndroidUtilities.dp(10));

        TextView label = new TextView(context);
        label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13.5f);
        label.setTextColor(WallUi.textPrimary());
        label.setText(title);
        row.addView(label, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        Switch sw = new Switch(context);
        boolean checked = getter != null && getter.get();
        sw.setChecked(checked, false);
        row.addView(sw, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 4, 0));

        row.setOnClickListener(v -> {
            boolean newValue = !sw.isChecked();
            sw.setChecked(newValue, true);
            WallUi.hapticToggle(v, newValue);
            if (setter != null) {
                setter.set(newValue);
            }
        });
        return row;
    }

    private View buildDivider(Context context) {
        View divider = new View(context);
        divider.setBackgroundColor(WallUi.divider());
        divider.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, AndroidUtilities.dp(0.6f))));
        return divider;
    }
}
