package app.amegram.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.TypedValue;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
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
 * Экран бейджиков сообщества в стиле Material Design 3.
 * Каталог стилей бейджей с предпросмотром, человекочитаемыми названиями
 * и тональными сквирклами вместо сырых кодовых строк.
 */
public class AmegramBadgesActivity extends BaseFragment {

    private static final int ROW_MY_BADGE = 1;
    private static final int ROW_GRANT = 2;
    private static final int ROW_MASTER_TOGGLE = 3;
    private static final int ROW_MY_SYNC_TOGGLE = 4;
    private static final int ROW_WORKSHOP = 5;
    private static final int ROW_LAPTOP_TOGGLE = 6;
    private static final int ROW_LAPTOP_URL = 7;
    private static final int ROW_LAPTOP_SYNC = 8;

    private UniversalRecyclerView listView;
    private final java.util.List<MiogramBadgeType> catalogOrder = new java.util.ArrayList<>();

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
        items.add(UItem.asHeader(MiogramLocale.get("Керування та статус", "Управление и статус", "Controls & Status")));

        items.add(YumiSettingCell.Factory.of(ROW_WORKSHOP, R.drawable.msg_edit, YumiTheme.getPrimary(),
                MiogramLocale.get("Майстерня бейджиків", "Мастерская бейджиков", "Badge Workshop"),
                MiogramLocale.get("Створити або надіслати свій SVG-бейдж у t.me/yumigram?direct",
                        "Создать или отправить свой SVG-бейдж в t.me/yumigram?direct",
                        "Create or submit your custom SVG badge to t.me/yumigram?direct")));

        boolean masterEnabled = MiogramBadgeManager.isModuleEnabled();
        items.add(UItem.asCheck(ROW_MASTER_TOGGLE,
                MiogramLocale.get("Відображати бейджики спільноти", "Отображать бейджики сообщества", "Display community badges"))
                .setChecked(masterEnabled));

        long selfId = getUserConfig() != null && getUserConfig().getCurrentUser() != null
                ? getUserConfig().getCurrentUser().id : 0;
        if (selfId > 0 && masterEnabled) {
            boolean selfSync = MiogramSupabaseBridge.isSyncEnabledForAccount(getContext(), selfId);
            items.add(UItem.asCheck(ROW_MY_SYNC_TOGGLE,
                    MiogramLocale.get("Увімкнути бейдж для мого профілю", "Включить бейдж для моего профиля", "Enable badge on my profile"))
                    .setChecked(selfSync));
        }

        String badgeTitle = selfId > 0 ? MiogramBadgeManager.getBadgeTitle(selfId)
                : MiogramLocale.get("Увійди, щоб побачити бейдж", "Войди, чтобы увидеть бейдж", "Log in to see your badge");

        android.graphics.drawable.Drawable myIcon = null;
        try {
            myIcon = new app.miogram.bridge.badge.MiogramArrowDrawable(28,
                    selfId > 0 ? MiogramBadgeManager.getBadgeType(selfId) : MiogramBadgeType.ORIGINAL);
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

        items.add(UItem.asShadow(MiogramLocale.get(
                "Бейджі видає спільнота Yumigram. Кеш оновлюється при відкритті профілю.",
                "Бейджи выдаёт сообщество Yumigram. Кэш обновляется при открытии профиля.",
                "Badges are granted by the Yumigram community. Cache refreshes on profile open.")));

        Context ctx = getContext();
        SharedPreferences modPrefs = ctx != null ? ctx.getSharedPreferences("badges_module_prefs", Context.MODE_PRIVATE) : null;
        boolean useLaptop = modPrefs != null && modPrefs.getBoolean("badges_use_laptop", true);
        String laptopUrl = modPrefs != null ? modPrefs.getString("badges_laptop_url", "http://192.168.1.100:8765") : "http://192.168.1.100:8765";

        items.add(UItem.asHeader(MiogramLocale.get("Сервер на ноутбуці (Локальний бекенд)", "Сервер на ноутбуке (Локальный бэкенд)", "Laptop server (Local backend)")));
        items.add(UItem.asCheck(ROW_LAPTOP_TOGGLE,
                MiogramLocale.get("Синхронізація з ноутбуком", "Синхронизация с ноутбуком", "Sync with laptop"))
                .setChecked(useLaptop));
        if (useLaptop) {
            items.add(YumiSettingCell.Factory.of(ROW_LAPTOP_URL, R.drawable.msg_link, YumiTheme.getPrimary(),
                    MiogramLocale.get("Адреса сервера на ПК", "Адрес сервера на ПК", "Laptop server URL"),
                    laptopUrl));
            items.add(YumiSettingCell.Factory.of(ROW_LAPTOP_SYNC, R.drawable.msg_retry, YumiTheme.getPrimary(),
                    MiogramLocale.get("Синхронізувати зараз", "Синхронизировать сейчас", "Sync now"),
                    MiogramLocale.get("Завантажити бейджі з ноутбука або GitHub", "Загрузить бейджи с ноутбука или GitHub", "Fetch badges from laptop or GitHub")));
        }
        items.add(UItem.asShadow(MiogramLocale.get(
                "Гібридний режим: клієнт бере бейджі з сервера на ноутбуці в локальній мережі, а при його відсутності — автоматично перемикається на GitHub-дзеркало.",
                "Гибридный режим: клиент берёт бейджи с сервера на ноутбуке в локальной сети, а при его отсутствии — автоматически переключается на GitHub-зеркало.",
                "Hybrid mode: client fetches badges from the laptop server in local network, falling back to GitHub mirror when offline.")));

        items.add(UItem.asHeader(MiogramLocale.get("Каталог стилів", "Каталог стилей", "Style catalog")));
        catalogOrder.clear();
        MiogramBadgeType[] types = MiogramBadgeType.values();
        for (int i = 0; i < types.length; i++) {
            catalogOrder.add(types[i]);
            MiogramBadgeType type = types[i];
            String title = type.getTitle();
            String subtitle = type.getCode() + " • " + MiogramLocale.get("Натисніть, щоб обрати стиль", "Нажмите, чтобы выбрать стиль", "Tap to pick this style");
            android.graphics.drawable.Drawable icon = null;
            try {
                icon = new app.miogram.bridge.badge.MiogramArrowDrawable(28, type);
            } catch (Throwable ignore) {
            }
            if (icon != null) {
                items.add(YumiSettingCell.Factory.ofDrawable(100 + i, icon, title, subtitle));
            } else {
                int color = getBadgeAccentColor(type);
                items.add(YumiSettingCell.Factory.of(100 + i, R.drawable.msg_settings_premium, color, title, subtitle));
            }
        }

        items.add(UItem.asShadow(MiogramLocale.get(
                "Оберіть стиль — він одразу синхронізується з вашим профілем.",
                "Выберите стиль — он сразу синхронизируется с вашим профилем.",
                "Pick a style — it syncs to your profile right away.")));

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

        if (item.id == ROW_MASTER_TOGGLE) {
            MiogramBadgeManager.setModuleEnabled(!MiogramBadgeManager.isModuleEnabled());
            if (listView != null && listView.adapter != null) listView.adapter.update(true);
        } else if (item.id == ROW_MY_SYNC_TOGGLE) {
            long selfId = getUserConfig() != null && getUserConfig().getCurrentUser() != null
                    ? getUserConfig().getCurrentUser().id : 0;
            if (selfId > 0) {
                boolean cur = MiogramSupabaseBridge.isSyncEnabledForAccount(ctx, selfId);
                MiogramSupabaseBridge.setSyncEnabledForAccount(ctx, selfId, !cur);
                if (listView != null && listView.adapter != null) listView.adapter.update(true);
            }
        } else if (item.id == ROW_MY_BADGE) {
            long selfId = getUserConfig() != null && getUserConfig().getCurrentUser() != null
                    ? getUserConfig().getCurrentUser().id : 0;
            MiogramBadgeBottomSheet.show(ctx, selfId);
        } else if (item.id == ROW_LAPTOP_TOGGLE) {
            SharedPreferences prefs = ctx.getSharedPreferences("badges_module_prefs", Context.MODE_PRIVATE);
            boolean cur = prefs.getBoolean("badges_use_laptop", true);
            prefs.edit().putBoolean("badges_use_laptop", !cur).apply();
            if (listView != null && listView.adapter != null) listView.adapter.update(true);
            MiogramBadgeManager.refreshBadges(null);
        } else if (item.id == ROW_LAPTOP_URL) {
            SharedPreferences prefs = ctx.getSharedPreferences("badges_module_prefs", Context.MODE_PRIVATE);
            String current = prefs.getString("badges_laptop_url", "http://192.168.1.100:8765");

            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(MiogramLocale.get("Адреса сервера на ПК", "Адрес сервера на ПК", "Laptop Server URL"));
            builder.setMessage(MiogramLocale.get(
                    "Введіть IP-адресу та порт вашого ноутбука у локальній Wi-Fi мережі (наприклад http://192.168.1.100:8765):",
                    "Введите IP-адрес и порт вашего ноутбука в локальной Wi-Fi сети (например http://192.168.1.100:8765):",
                    "Enter your laptop's local Wi-Fi IP and port (e.g. http://192.168.1.100:8765):"));

            FrameLayout frameLayout = new FrameLayout(getParentActivity());
            frameLayout.setPadding(AndroidUtilities.dp(24), 0, AndroidUtilities.dp(24), 0);
            EditTextBoldCursor editText = new EditTextBoldCursor(getParentActivity());
            editText.setText(current);
            editText.setSelection(editText.getText().length());
            editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            editText.setBackground(Theme.createEditTextDrawable(getParentActivity(), false));
            frameLayout.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            builder.setView(frameLayout);

            builder.setPositiveButton(MiogramLocale.get("Зберегти", "Сохранить", "Save"), (dialog, which) -> {
                String val = editText.getText().toString().trim();
                if (!val.isEmpty()) {
                    if (!val.startsWith("http://") && !val.startsWith("https://")) {
                        val = "http://" + val;
                    }
                    prefs.edit().putString("badges_laptop_url", val).apply();
                    if (listView != null && listView.adapter != null) listView.adapter.update(true);
                    Toast.makeText(ctx, MiogramLocale.get("Збережено ✓ Синхронізація...", "Сохранено ✓ Синхронизация...", "Saved ✓ Syncing..."), Toast.LENGTH_SHORT).show();
                    MiogramBadgeManager.refreshBadges(() -> {
                        AndroidUtilities.runOnUIThread(() -> {
                            Toast.makeText(ctx, MiogramLocale.get("Бейджі успішно оновлено ✓", "Бейджи успешно обновлены ✓", "Badges updated ✓"), Toast.LENGTH_SHORT).show();
                            if (listView != null && listView.adapter != null) listView.adapter.update(true);
                        });
                    });
                }
            });
            builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
            showDialog(builder.create());
        } else if (item.id == ROW_LAPTOP_SYNC) {
            Toast.makeText(ctx, MiogramLocale.get("Синхронізація з сервером...", "Синхронизация с сервером...", "Syncing with server..."), Toast.LENGTH_SHORT).show();
            MiogramBadgeManager.refreshBadges(() -> {
                AndroidUtilities.runOnUIThread(() -> {
                    Toast.makeText(ctx, MiogramLocale.get("Бейджі успішно оновлено ✓", "Бейджи успешно обновлены ✓", "Badges updated ✓"), Toast.LENGTH_SHORT).show();
                    if (listView != null && listView.adapter != null) listView.adapter.update(true);
                });
            });
        } else if (item.id == ROW_WORKSHOP) {
            presentFragment(new AmegramBadgeWorkshopActivity());
        } else if (item.id == ROW_GRANT) {
            MiogramBadgeGrantSheet.show(ctx);
        } else if (item.id >= 100) {
            int index = item.id - 100;
            MiogramBadgeType pickedType = (index >= 0 && index < catalogOrder.size())
                    ? catalogOrder.get(index) : null;
            long selfId = getUserConfig() != null && getUserConfig().getCurrentUser() != null
                    ? getUserConfig().getCurrentUser().id : 0;
            if (pickedType != null && selfId > 0) {
                MiogramSupabaseBridge.setSelectedBadgeForAccount(ctx, selfId, pickedType);
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
