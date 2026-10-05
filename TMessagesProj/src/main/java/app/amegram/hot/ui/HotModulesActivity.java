package app.amegram.hot.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Switch;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import app.amegram.hot.HotModulesManager;
import app.miogram.bridge.MiogramLocale;

/**
 * Керування хот-модулями:
 * перегляд встановлених розширень, зміна версії (новіша/старіша),
 * видалення, увімкнення/вимкнення, навантаження (RAM/DEX) та опис.
 */
public class HotModulesActivity extends BaseFragment implements HotModulesManager.ModulesChangeListener {

    private static final int MENU_ADD = 1;

    private UniversalRecyclerView listView;
    private final List<HotModulesManager.InstalledInfo> shown = new ArrayList<>();

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(MiogramLocale.get("Хот-модулі", "Хот-модули", "Hot modules"));
        actionBar.setActionBarMenuOnItemClick(new org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == MENU_ADD) {
                    HotCatalogSheet sheet = new HotCatalogSheet(getContext(), () -> refresh());
                    sheet.show();
                }
            }
        });
        actionBar.createMenu().addItem(MENU_ADD, R.drawable.filled_new_contact_24);

        fragmentView = listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, null);
        listView.setSections();
        HotModulesManager.init(context.getApplicationContext());
        HotModulesManager.addListener(this);
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        HotModulesManager.removeListener(this);
    }

    @Override
    public void onModulesChanged() {
        refresh();
    }

    private void refresh() {
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        shown.clear();
        Context context = getContext();
        if (context == null) return;

        Map<String, HotModulesManager.InstalledInfo> byId = new LinkedHashMap<>();
        for (HotModulesManager.InstalledInfo info : HotModulesManager.listInstalled()) {
            HotModulesManager.InstalledInfo cur = byId.get(info.manifest.id);
            if (cur == null || info.active) {
                byId.put(info.manifest.id, info.active ? info : cur == null ? info : cur);
            }
        }
        shown.addAll(byId.values());

        items.add(UItem.asHeader(MiogramLocale.get("Встановлені модулі", "Установленные модули", "Installed modules")));

        if (shown.isEmpty()) {
            items.add(UItem.asCustom(buildEmptyStateView(context)));
        } else {
            for (int i = 0; i < shown.size(); i++) {
                HotModulesManager.InstalledInfo info = shown.get(i);
                View cardView = buildModuleCard(context, info, i);
                items.add(UItem.asCustom(cardView));
            }
        }

        items.add(UItem.asShadow(MiogramLocale.get(
                "Модулі є пісочницею нативних розширень. Вимкнений модуль вивантажується з пам'яті (0 KB RAM). Керуйте функціоналом у головних налаштуваннях Amegram.",
                "Модули являются песочницей нативных расширений. Выключенный модуль выгружается из памяти (0 KB RAM). Управляйте функционалом в главных настройках Amegram.",
                "Modules are isolated native extensions. Disabled modules are unloaded (0 KB RAM). Configure features in main Amegram settings.")));
    }

    private View buildEmptyStateView(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(32), AndroidUtilities.dp(24), AndroidUtilities.dp(32));

        ImageView icon = new ImageView(context);
        icon.setImageResource(R.drawable.msg_plugins);
        icon.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        layout.addView(icon, LayoutHelper.createLinear(48, 48, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 14));

        TextView title = new TextView(context);
        title.setText(MiogramLocale.get("Немає встановлених модулів", "Нет установленных модулей", "No installed modules"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setGravity(Gravity.CENTER);
        layout.addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

        TextView sub = new TextView(context);
        sub.setText(MiogramLocale.get(
                "Ви самі обираєте, що завантажувати. Перейдіть у каталог, щоб переглянути описи та встановити потрібні розширення.",
                "Вы сами выбираете, что скачивать. Перейдите в каталог, чтобы посмотреть описания и установить нужные расширения.",
                "You choose what to install. Open catalog to view descriptions and install extensions."));
        sub.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        sub.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        sub.setGravity(Gravity.CENTER);
        layout.addView(sub, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        TextView btnCatalog = new TextView(context);
        btnCatalog.setText(MiogramLocale.get("Відкрити каталог модулів", "Открыть каталог модулей", "Open module catalog"));
        btnCatalog.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        btnCatalog.setTypeface(AndroidUtilities.bold());
        btnCatalog.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        btnCatalog.setGravity(Gravity.CENTER);

        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setCornerRadius(AndroidUtilities.dp(10));
        btnBg.setColor(Theme.getColor(Theme.key_featuredStickers_addButton));
        btnCatalog.setBackground(btnBg);
        btnCatalog.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(10), AndroidUtilities.dp(20), AndroidUtilities.dp(10));
        btnCatalog.setOnClickListener(v -> {
            HotCatalogSheet sheet = new HotCatalogSheet(context, () -> refresh());
            sheet.show();
        });
        layout.addView(btnCatalog, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        return layout;
    }

    private View buildModuleCard(Context context, HotModulesManager.InstalledInfo info, int index) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);

        int cardBg = Theme.getColor(Theme.key_windowBackgroundWhite);
        if (cardBg == 0) cardBg = Theme.isCurrentThemeDark() ? 0xFF1C1D26 : 0xFFFFFFFF;

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(AndroidUtilities.dp(16));
        bg.setColor(cardBg);
        boolean isDark = Theme.isCurrentThemeDark();
        bg.setStroke(AndroidUtilities.dp(1), isDark ? 0x22FFFFFF : 0x14000000);
        card.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(AndroidUtilities.dp(14), AndroidUtilities.dp(5), AndroidUtilities.dp(14), AndroidUtilities.dp(5));
        card.setLayoutParams(lp);
        card.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12), AndroidUtilities.dp(14), AndroidUtilities.dp(10));

        // Головний рядок: Іконка + Опис + Світч
        LinearLayout topRow = new LinearLayout(context);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        // 1. Іконка модуля
        int modColor = getModuleColor(info.manifest.id);
        FrameLayout iconFrame = new FrameLayout(context);
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setCornerRadius(AndroidUtilities.dp(12));
        iconBg.setColor(modColor);
        iconFrame.setBackground(iconBg);

        ImageView iconView = new ImageView(context);
        iconView.setImageResource(getModuleIcon(info.manifest.id));
        iconView.setColorFilter(0xFFFFFFFF);
        iconFrame.addView(iconView, LayoutHelper.createFrame(22, 22, Gravity.CENTER));
        topRow.addView(iconFrame, LayoutHelper.createLinear(42, 42, 0, 0, 12, 0));

        // 2. Інформація (Заголовок, бейдж версії, навантаження, опис)
        LinearLayout infoCol = new LinearLayout(context);
        infoCol.setOrientation(LinearLayout.VERTICAL);

        LinearLayout titleRow = new LinearLayout(context);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView titleView = new TextView(context);
        titleView.setText(info.manifest.name);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleRow.addView(titleView);

        // Бейдж версії
        TextView verBadge = new TextView(context);
        verBadge.setText("v" + info.manifest.version);
        verBadge.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        verBadge.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        verBadge.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(1), AndroidUtilities.dp(6), AndroidUtilities.dp(1));
        GradientDrawable verBg = new GradientDrawable();
        verBg.setCornerRadius(AndroidUtilities.dp(6));
        verBg.setColor(isDark ? 0x22FFFFFF : 0x14000000);
        verBadge.setBackground(verBg);
        titleRow.addView(verBadge, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 6, 0, 0, 0));

        infoCol.addView(titleRow);

        // Навантаження / статус пам'яті в реальному часі
        TextView loadView = new TextView(context);
        loadView.setText(HotModulesManager.getModuleLoadEstimate(info.manifest.id));
        loadView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        loadView.setTextColor(info.enabled ? 0xFF4CAF50 : Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        infoCol.addView(loadView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 2));

        // Опис модуля
        TextView descView = new TextView(context);
        descView.setText(getModuleDescription(info.manifest.id));
        descView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        descView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        descView.setMaxLines(2);
        infoCol.addView(descView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        topRow.addView(infoCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, 8, 0));

        // 3. Світч увімкнення/вимкнення
        Switch sw = new Switch(context);
        sw.setChecked(info.enabled, false);
        sw.setOnCheckedChangeListener((view, isChecked) -> {
            HotModulesManager.setEnabled(info.manifest.id, isChecked, (ok, msg, data) -> refresh());
        });
        topRow.addView(sw, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        card.addView(topRow);

        // Нижній рядок дій: [Версії / Оновити] [Видалити]
        LinearLayout bottomRow = new LinearLayout(context);
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setGravity(Gravity.END);

        TextView btnVersions = new TextView(context);
        btnVersions.setText(info.manifest.branch + " • " + MiogramLocale.get("Версії / Оновити", "Версии / Обновить", "Versions / Update"));
        btnVersions.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        btnVersions.setTypeface(AndroidUtilities.bold());
        btnVersions.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton));
        btnVersions.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(4), AndroidUtilities.dp(8), AndroidUtilities.dp(4));
        btnVersions.setOnClickListener(v -> {
            HotModuleVersionsSheet sheet = new HotModuleVersionsSheet(context, info.manifest.id, null, () -> refresh());
            sheet.show();
        });
        bottomRow.addView(btnVersions);

        TextView btnDelete = new TextView(context);
        btnDelete.setText(MiogramLocale.get("Видалити", "Удалить", "Delete"));
        btnDelete.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        btnDelete.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
        btnDelete.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(4), AndroidUtilities.dp(8), AndroidUtilities.dp(4));
        btnDelete.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(MiogramLocale.get("Видалення модуля", "Удаление модуля", "Delete module"));
            builder.setMessage(MiogramLocale.get(
                    "Ви дійсно хочете видалити модуль " + info.manifest.name + " та звільнити пам'ять?",
                    "Вы действительно хотите удалить модуль " + info.manifest.name + " и освободить память?",
                    "Are you sure you want to delete module " + info.manifest.name + "?"));
            builder.setPositiveButton(MiogramLocale.get("Видалити", "Удалить", "Delete"), (d, w) -> {
                HotModulesManager.deleteModule(info.manifest.id);
                refresh();
            });
            builder.setNegativeButton(MiogramLocale.get("Скасувати", "Отмена", "Cancel"), null);
            builder.show();
        });
        bottomRow.addView(btnDelete);

        card.addView(bottomRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 0));

        card.setOnClickListener(v -> {
            HotModuleVersionsSheet sheet = new HotModuleVersionsSheet(context, info.manifest.id, null, () -> refresh());
            sheet.show();
        });

        return card;
    }

    private static int getModuleColor(String id) {
        if ("ghost".equals(id)) return 0xFF8E24AA;        // Purple
        if ("player".equals(id)) return 0xFF00ACC1;       // Cyan
        if ("ame".equals(id)) return 0xFFE91E63;          // Pink
        if ("vault".equals(id)) return 0xFFFF8F00;        // Amber
        if ("tiktok".equals(id)) return 0xFFEE1D52;       // Red
        if ("stt".equals(id)) return 0xFF00897B;          // Teal
        if ("ai".equals(id)) return 0xFF7E57C2;           // Violet
        if ("experimental".equals(id)) return 0xFF43A047; // Green
        if ("automation".equals(id)) return 0xFF546E7A;   // Blue-Gray
        return 0xFF2A87FF;                                // Blue
    }

    private static int getModuleIcon(String id) {
        if ("ghost".equals(id)) return R.drawable.msg_secret;
        if ("player".equals(id)) return R.drawable.baseline_music_note_24;
        if ("ame".equals(id)) return R.drawable.msg_customize;
        if ("vault".equals(id)) return R.drawable.msg_saved;
        if ("tiktok".equals(id)) return R.drawable.msg_video;
        if ("stt".equals(id)) return R.drawable.msg_bot;
        if ("ai".equals(id)) return R.drawable.baseline_stars_24;
        if ("experimental".equals(id)) return R.drawable.msg_fave;
        if ("automation".equals(id)) return R.drawable.msg_download_solar;
        return R.drawable.msg_plugins;
    }

    private static String getModuleDescription(String id) {
        if ("ghost".equals(id)) {
            return MiogramLocale.get(
                    "Приховування прочитання, історій, онлайну та набору тексту",
                    "Скрытие прочитанного, историй, онлайна и набора текста",
                    "Hide read receipts, stories views, online status & typing");
        }
        if ("player".equals(id)) {
            return MiogramLocale.get(
                    "Пошук музики з 6 сервісів, кастомний плеєр, візуалізатор, тексти LRC",
                    "Поиск музыки из 6 сервисов, кастомный плеер, визуализатор, тексты LRC",
                    "Search 6 music sources, custom player, visualizer, LRC lyrics");
        }
        if ("ame".equals(id)) {
            return MiogramLocale.get(
                    "Аме-студія XML-карток профіля, градієнти, картки діалогів, glass blur",
                    "Аме-студия XML-карточек профиля, градиенты, карточки диалогов, glass blur",
                    "Ame Studio XML cards, gradients, dialog cards, glass blur");
        }
        if ("vault".equals(id)) {
            return MiogramLocale.get(
                    "Шифрований AES-256-GCM віртуальний диск з автонарізанням на чанки",
                    "Зашифрованный AES-256-GCM виртуальный диск с чанкованием",
                    "Encrypted AES-256-GCM virtual drive with file chunking");
        }
        if ("tiktok".equals(id)) {
            return MiogramLocale.get(
                    "Вбудований плеєр без ватермарок, чисті URL та синхронізація акаунта",
                    "Встроенный плеер без ватермарок, чистые URL и синхронизация аккаунта",
                    "Watermark-free in-app player, clean URLs & account sync");
        }
        if ("stt".equals(id)) {
            return MiogramLocale.get(
                    "ШІ-розпізнавання голосових та відеоповідомлень через Gemini або Whisper",
                    "ИИ-распознавание голосовых и видеосообщений через Gemini или Whisper",
                    "AI transcription for voice & video notes via Gemini or Whisper");
        }
        if ("ai".equals(id)) {
            return MiogramLocale.get(
                    "ШІ-супутник Ame / KAngel з пам'яттю та окремим екраном чату",
                    "ИИ-спутник Ame / KAngel с памятью и отдельным экраном чата",
                    "AI companion Ame / KAngel with memory & dedicated chat screen");
        }
        if ("experimental".equals(id)) {
            return MiogramLocale.get(
                    "Безліміт закріплених чатів, upload boost, збереження видалених",
                    "Безлимит закрепленных чатов, upload boost, сохранение удаленных",
                    "Unlimited pinned chats, upload boost, save deleted messages");
        }
        if ("automation".equals(id)) {
            return MiogramLocale.get(
                    "Фонова автосинхронізація хмари, автобекап Обраного та очищення кешу",
                    "Фоновая автосинхронизация облака, автобэкап Избранного и очистка кеша",
                    "Background cloud auto-sync, Saved Messages backup & cache cleaner");
        }
        return MiogramLocale.get(
                "Нативне розширення клієнта Amegram",
                "Нативное расширение клиента Amegram",
                "Native Amegram client extension");
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        // Кліки обробляються прямо всередині карток
    }

    @Override
    public boolean isLightStatusBar() {
        return !Theme.isCurrentThemeDark();
    }
}
