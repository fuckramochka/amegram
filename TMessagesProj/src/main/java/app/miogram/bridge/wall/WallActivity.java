package app.miogram.bridge.wall;

import android.content.Intent;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import org.telegram.messenger.AndroidUtilities;
import app.miogram.bridge.MiogramLocale;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import app.miogram.bridge.wall.WallModels.Wall;
import app.miogram.bridge.wall.WallModels.WallElement;

/**
 * Екран стіни: аврора-фон, masonry-сітка карток, чипи фільтрів,
 * кнопка «Додати», порожній стан та онбординг. Уся логіка локальна.
 */
public class WallActivity extends BaseFragment {

    static final int REQ_MEDIA = 9410;
    static final int REQ_DRAW = 9411;
    static final int REQ_PEOPLE = 9412;

    private final long ownerId;
    private final String ownerName;
    private final boolean isSelf;

    private Wall wall;
    private long myId;
    private String myName;

    private WallAuroraView aurora;
    private RecyclerListView listView;
    private WallAdapter adapter;
    private View addFab;
    private TextView emptyTitle;
    private TextView emptySubtitle;
    private View emptyView;
    private LinearLayout chipsRow;
    private TextView statsView;
    private String filter = "all";
    private WallComposerSheet composer;
    private WallSettingsSheet settingsSheet;

    public WallActivity() {
        this(WallStorage.currentUserId(), "", true);
    }

    public WallActivity(long ownerId, String ownerName, boolean isSelf) {
        this.ownerId = ownerId;
        this.ownerName = ownerName == null ? "" : ownerName;
        this.isSelf = isSelf;
    }

    @Override
    public View createView(Context context) {
        hasOwnBackground = true;
        myId = WallStorage.currentUserId();
        myName = WallStorage.displayName(myId);
        wall = WallStorage.loadWall(ownerId, this.ownerName, WallStorage.username(ownerId));

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(WallUi.surface());

        aurora = new WallAuroraView(context);
        root.addView(aurora, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        root.addView(column, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setTitle(MiogramLocale.get("✦ стіна", "✦ стена", "✦ wall"));
        actionBar.setSubtitle(wallSubtitle());
        actionBar.setTitleColor(0xFFFFFFFF);
        actionBar.setItemsColor(0xFFFFFFFF, false);
        actionBar.setSubtitleColor(0xAAFFFFFF);
        actionBar.setCastShadows(false);
        actionBar.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == 1) {
                    openSettings();
                }
            }
        });
        if (isSelf) {
            actionBar.createMenu().addItem(1, org.telegram.messenger.R.drawable.msg_settings_old).setAlpha(0.95f);
        }
        column.addView(actionBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // ----- шапка: статистика + чипи фільтрів -----
        LinearLayout headerBlock = new LinearLayout(context);
        headerBlock.setOrientation(LinearLayout.VERTICAL);
        headerBlock.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(6), AndroidUtilities.dp(16), AndroidUtilities.dp(4));

        statsView = new TextView(context);
        WallUi.styleBody(statsView, 12.5f, 0xB3FFFFFF);
        headerBlock.addView(statsView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        chipsRow = new LinearLayout(context);
        chipsRow.setOrientation(LinearLayout.HORIZONTAL);
        chipsRow.setGravity(Gravity.CENTER_VERTICAL);
        HorizontalScrollView chipsScroll = new HorizontalScrollView(context);
        chipsScroll.setHorizontalScrollBarEnabled(false);
        chipsScroll.addView(chipsRow, new HorizontalScrollView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        headerBlock.addView(chipsScroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        column.addView(headerBlock, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // ----- сітка -----
        listView = new RecyclerListView(context);
        listView.setClipToPadding(false);
        listView.setLayoutManager(new StaggeredGridLayoutManager(2, LinearLayoutManager.VERTICAL));
        listView.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(8), AndroidUtilities.dp(10), AndroidUtilities.dp(120));
        listView.setBackgroundColor(Color.TRANSPARENT);
        adapter = new WallAdapter(context, new WallAdapter.Listener() {
            @Override
            public void onElementClick(WallElement element) {
                openElementDetails(element);
            }

            @Override
            public void onLike(WallElement element, boolean like) {
                WallStorage.toggleLike(wall, element.id, myId, like);
                adapter.rebind(element);
                refreshWall(false);
            }

            @Override
            public void onComments(WallElement element) {
                openComments(element);
            }

            @Override
            public void onEdit(WallElement element) {
                openComposer(element);
            }

            @Override
            public void onDelete(WallElement element) {
                confirmDelete(element);
            }

            @Override
            public void onVote(WallElement element, int value) {
                WallStorage.vote(wall, element.id, myId, value);
                adapter.rebind(element);
                refreshWall(false);
            }

            @Override
            public void onMediaClick(WallElement element) {
                openMedia(element);
            }

            @Override
            public void onAction(WallElement element) {
                runAction(element);
            }

            @Override
            public void onOpenHtml(WallElement element) {
                new WallHtmlSheet(context, WallActivity.this, element).show();
            }
        });
        listView.setAdapter(adapter);
        adapter.setParentFragment(this);
        adapter.setMyId(myId);
        listView.setOnItemClickListener((view, position) -> {
            WallElement element = adapter.itemAt(position);
            if (element != null) {
                openElementDetails(element);
            }
        });
        listView.setOnItemLongClickListener((view, position) -> {
            WallElement element = adapter.itemAt(position);
            if (element != null) {
                openComposer(element);
                return true;
            }
            return false;
        });
        column.addView(listView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        // ----- порожній стан -----
        emptyView = buildEmptyState(context);
        root.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 32, 0, 32, 120));

        // ----- кнопка «Додати» -----
        addFab = buildFab(context);
        root.addView(addFab, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.END, 0, 0, 18, 28));

        buildChips();
        refreshWall(false);

        if (!WallPrefs.isOnboarded() && isSelf) {
            AndroidUtilities.runOnUIThread(this::showOnboarding, 500);
        }

        return root;
    }

    private String wallSubtitle() {
        String label = WallModule.isHotEnabled() ? ".hmod" : "local";
        int count = wall == null || wall.elements == null ? 0 : wall.elements.size();
        if (isSelf) {
            return WallUi.formatCount(count) + " · " + label + " · β";
        }
        return label + " · β";
    }

    private View buildFab(Context context) {
        LinearLayout pill = new LinearLayout(context);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
        pill.setBackground(WallUi.brandPill(24));
        pill.setElevation(AndroidUtilities.dp(8));
        pill.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), AndroidUtilities.dp(24));
            }
        });
        pill.setClipToOutline(true);

        ImageView icon = new ImageView(context);
        icon.setImageResource(org.telegram.messenger.R.drawable.msg_add);
        icon.setColorFilter(0xFFFFFFFF);
        icon.setPadding(0, AndroidUtilities.dp(14), AndroidUtilities.dp(7), AndroidUtilities.dp(14));
        pill.addView(icon, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        TextView label = new TextView(context);
        label.setText(MiogramLocale.get("Додати", "Добавить", "Add"));
        label.setTextColor(0xFFFFFFFF);
        label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 14.5f);
        label.setTypeface(AndroidUtilities.bold());
        pill.addView(label, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        pill.setOnClickListener(v -> {
            WallUi.hapticClick(v);
            openComposer(null);
        });
        return pill;
    }

    private View buildEmptyState(Context context) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);

        TextView mark = new TextView(context);
        mark.setText(MiogramLocale.get("✦ стіна", "✦ стена", "✦ wall"));
        mark.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 34);
        mark.setTypeface(AndroidUtilities.bold());
        mark.setTextColor(0xFFFFFFFF);
        mark.setShadowLayer(AndroidUtilities.dp(6), 0, 0, 0x66FF4D8D);
        box.addView(mark, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        emptyTitle = new TextView(context);
        emptyTitle.setText(MiogramLocale.get("Тут поки тихо…", "Здесь пока тихо…", "It's quiet here…"));
        WallUi.styleTitle(emptyTitle, 16);
        box.addView(emptyTitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 10, 0, 6));

        emptySubtitle = new TextView(context);
        emptySubtitle.setGravity(Gravity.CENTER);
        WallUi.styleBody(emptySubtitle, 13, 0x99FFFFFF);
        box.addView(emptySubtitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        return box;
    }

    // ---------- дані ----------

    public void refreshWall(boolean animate) {
        wall = WallStorage.loadWall(ownerId, ownerName, WallStorage.username(ownerId));
        adapter.setWall(wall, filter);
        refreshHeader();
        if (actionBar != null) {
            actionBar.setSubtitle(wallSubtitle());
        }
        updateFab();
        updateEmpty();
    }

    private void refreshHeader() {
        int count = wall == null || wall.elements == null ? 0 : wall.elements.size();
        int likes = WallStorage.totalLikes(wall);
        int comments = WallStorage.totalComments(wall);
        statsView.setText("◆ " + count
                + "  ·  ♥ " + WallUi.formatCount(likes)
                + "  ·  ◔ " + comments);
    }

    private void updateFab() {
        boolean visible = isSelf || WallStorage.canEdit(wall, myId);
        addFab.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) {
            boolean hasMine = WallStorage.findElementOfAuthor(wall, myId) != null;
            TextView label = (TextView) ((LinearLayout) addFab).getChildAt(1);
            label.setText(hasMine ? MiogramLocale.get("Твій елемент", "Твой элемент", "Your element")
                    : MiogramLocale.get("Додати", "Добавить", "Add"));
        }
    }

    private void updateEmpty() {
        int count = adapter.realCount();
        if (count == 0) {
            emptyView.setVisibility(View.VISIBLE);
            emptySubtitle.setText(MiogramLocale.get(
                    "Поклади перший елемент — думку, фото або опитування.",
                    "Положи первый элемент — мысль, фото или опрос.",
                    "Put the first element — a thought, photo or poll."));
        } else {
            emptyView.setVisibility(View.GONE);
        }
    }

    private void buildChips() {
        Context context = getContext();
        if (context == null || chipsRow == null) return;
        chipsRow.removeAllViews();
        String[] keys = {"all", WallModels.TYPE_TEXT, "media", WallModels.TYPE_POLL, WallModels.TYPE_DRAWING, WallModels.TYPE_BUTTON, WallModels.TYPE_HTML};
        String[] labels = {
                MiogramLocale.get("Усе", "Всё", "All"),
                MiogramLocale.get("Текст", "Текст", "Text"),
                MiogramLocale.get("Медіа", "Медиа", "Media"),
                MiogramLocale.get("Опитування", "Опросы", "Polls"),
                MiogramLocale.get("Малюнки", "Рисунки", "Drawings"),
                MiogramLocale.get("Кнопки", "Кнопки", "Buttons"),
                MiogramLocale.get("HTML", "HTML", "HTML")
        };
        for (int i = 0; i < keys.length; i++) {
            final String key = keys[i];
            boolean selected = filter.equals(key);
            TextView chip = new TextView(context);
            chip.setText(labels[i]);
            chip.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 12.5f);
            chip.setTypeface(AndroidUtilities.bold());
            chip.setTextColor(selected ? 0xFFFFFFFF : 0xB3FFFFFF);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14), 0);
            chip.setMinHeight(AndroidUtilities.dp(30));
            chip.setBackground(selected ? WallUi.brandPill(15) : WallUi.pill(0x1FFFFFFF, 0x22FFFFFF, 15));
            chip.setOnClickListener(v -> {
                WallUi.hapticSelect(v);
                filter = key;
                buildChips();
                adapter.setWall(wall, filter);
                updateEmpty();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = AndroidUtilities.dp(8);
            chipsRow.addView(chip, lp);
        }
    }

    // ---------- дії ----------

    private void openElementDetails(WallElement element) {
        if (WallModels.TYPE_HTML.equals(element.type)) {
            new WallHtmlSheet(getContext(), this, element).show();
            return;
        }
        openComments(element);
    }

    private void openComments(WallElement element) {
        WallCommentsSheet sheet = new WallCommentsSheet(getContext(), this, wall, element, () -> {
            adapter.rebind(element);
            refreshWall(false);
        });
        sheet.show();
    }

    private void openComposer(WallElement editing) {
        if (composer != null && composer.isShowing()) return;
        if (editing == null) {
            editing = WallStorage.findElementOfAuthor(wall, myId);
        }
        if (editing == null && !WallStorage.canEdit(wall, myId)) {
            if (getParentActivity() != null) {
                BulletinFactory.of(this).createSimpleBulletin(org.telegram.messenger.R.raw.info,
                        MiogramLocale.get("Автор обмежив право додавати елементи",
                                "Автор ограничил право добавлять элементы",
                                "The author restricted adding elements")).show();
            }
            return;
        }
        composer = new WallComposerSheet(getContext(), this, wall, editing, published -> {
            WallStorage.upsertElement(wall, published);
            refreshWall(true);
        });
        composer.show();
    }

    private void confirmDelete(WallElement element) {
        if (getParentActivity() == null) return;
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(MiogramLocale.get("Видалити елемент?", "Удалить элемент?", "Delete element?"));
        b.setMessage(MiogramLocale.get("Це необоротно. Елемент зникне зі стіни.",
                "Это необратимо. Элемент исчезнет со стены.",
                "This cannot be undone. The element will disappear from the wall."));
        b.setPositiveButton(MiogramLocale.get("Видалити", "Удалить", "Delete"), (d, w) -> {
            WallStorage.deleteFile(element.mediaPath);
            WallStorage.deleteFile(element.thumbPath);
            WallStorage.removeElement(wall, element.id);
            refreshWall(true);
        });
        b.setNegativeButton(MiogramLocale.get("Скасувати", "Отмена", "Cancel"), null);
        showDialog(b.create());
    }

    private void openSettings() {
        if (settingsSheet != null && settingsSheet.isShowing()) return;
        settingsSheet = new WallSettingsSheet(getContext(), this, wall,
                currentIds -> {
                    // відкриваємо пікер людей; результат — назад у цю стіну
                    presentFragment(new WallPeoplePickerActivity(currentIds, selected -> {
                        wall.editorIds = selected;
                        WallStorage.saveWall(wall);
                        refreshWall(false);
                        AndroidUtilities.runOnUIThread(() -> {
                            if (settingsSheet != null && settingsSheet.isShowing()) {
                                settingsSheet.onPeopleSelected();
                            }
                        });
                    }));
                },
                () -> refreshWall(false));
        settingsSheet.show();
    }

    private void runAction(WallElement element) {
        if (element.action == null || getParentActivity() == null) return;
        String kind = element.action.kind == null ? "url" : element.action.kind;
        try {
            if ("copy".equals(kind)) {
                android.content.ClipboardManager cm = (android.content.ClipboardManager) getParentActivity().getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("wall", element.action.value));
                    BulletinFactory.of(this).createSimpleBulletin(org.telegram.messenger.R.raw.info,
                            MiogramLocale.get("Скопійовано ✓", "Скопировано ✓", "Copied ✓")).show();
                }
            } else {
                String value = element.action.value == null ? "" : element.action.value.trim();
                if ("chat".equals(kind) && value.startsWith("@")) {
                    value = "https://t.me/" + value.substring(1);
                }
                if (TextUtils.isEmpty(value)) return;
                if (!value.contains("://")) {
                    value = "https://" + value;
                }
                getParentActivity().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(value)));
            }
        } catch (Throwable ignore) {
        }
    }

    private void openMedia(WallElement element) {
        new WallMediaViewerSheet(getContext(), element).show();
    }

    private void showOnboarding() {
        if (getParentActivity() == null || WallPrefs.isOnboarded()) return;
        WallPrefs.setOnboarded(true);
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(MiogramLocale.get("Це твоя стіна ✦", "Это твоя стена ✦", "This is your wall ✦"));
        b.setMessage(MiogramLocale.get(
                "Тапни на банер свого профілю, щоб відкрити її звідси.\n\n• Кожен може покласти лише 1 елемент\n• Ти — автор стіни: можеш видаляти чужі елементи та налаштовувати доступ\n• Поки все локально, згодом синхронізується з VPS",
                "Тапни по баннеру своего профиля, чтобы открыть её отсюда.\n\n• Каждый может положить только 1 элемент\n• Ты — автор стены: можешь удалять чужие элементы и настраивать доступ\n• Пока всё локально, позже синхронизируется с VPS",
                "Tap your profile banner to open it from there.\n\n• Everyone can place only 1 element\n• You own the wall: delete others' elements and set access\n• Local beta for now, later synced with VPS"));
        b.setPositiveButton(MiogramLocale.get("Круто!", "Круто!", "Nice!"), null);
        showDialog(b.create());
    }

    // ---------- результати activity ----------

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        super.onActivityResultFragment(requestCode, resultCode, data);
        if (composer != null && composer.isShowing()) {
            composer.handleActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (aurora != null) aurora.stop();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (aurora != null) aurora.start();
        if (wall != null) {
            refreshWall(false);
        }
    }

    @Override
    public boolean isLightStatusBar() {
        return false;
    }
}
