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
import app.exteraless.settings.OpenExteraAppearanceActivity;
import app.exteraless.settings.OpenExteraChatsActivity;
import app.exteraless.settings.OpenExteraGeneralActivity;
import app.exteraless.settings.OpenExteraOtherActivity;
import app.miogram.bridge.MiogramLocale;
import app.miogram.bridge.ui.MiogramChatsSettingsActivity;
import tw.nekomimi.nekogram.settings.NekoChatSettingsActivity;
import tw.nekomimi.nekogram.settings.NekoGeneralSettingsActivity;

/**
 * «Додатково (Extera)» — тимчасова полиця для класичних екранів Neko та
 * Extera. Вони більше не мають верхнього рівня в хабі, але залишаються
 * доступними, доки їхні унікальні параметри не перенесені в канонічні
 * розділи «Клієнт → …».
 */
public class AmegramAdvancedSettingsActivity extends BaseFragment {

    private static final int ROW_NEKO_GENERAL = 1;
    private static final int ROW_NEKO_CHAT = 2;
    private static final int ROW_EXTERA_GENERAL = 10;
    private static final int ROW_EXTERA_APPEARANCE = 11;
    private static final int ROW_EXTERA_CHATS = 12;
    private static final int ROW_EXTERA_OTHER = 13;
    private static final int ROW_MIO_CHATS = 20;

    private UniversalRecyclerView listView;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(MiogramLocale.get("Додатково (Extera)", "Дополнительно (Extera)", "Advanced (Extera)"));
        actionBar.setActionBarMenuOnItemClick(new org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });
        fragmentView = listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, null);
        listView.setSections();
        return fragmentView;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(MiogramLocale.get("Класичні екрани Neko", "Классические экраны Neko", "Classic Neko screens")));

        items.add(YumiSettingCell.Factory.of(ROW_NEKO_GENERAL, R.drawable.yumi_ic_settings, 0xFF2A87FF,
                MiogramLocale.get("Загальні (Neko)", "Общие (Neko)", "General (Neko)"),
                MiogramLocale.get("Мова, поведінка, папки, сховище та сповіщення",
                        "Язык, поведение, папки, хранилище и уведомления",
                        "Language, behavior, folders, storage and notifications")));

        items.add(YumiSettingCell.Factory.of(ROW_NEKO_CHAT, R.drawable.yumi_ic_chat, 0xFF00ACC1,
                MiogramLocale.get("Чати (Neko)", "Чаты (Neko)", "Chats (Neko)"),
                MiogramLocale.get("Стікери, шрифт, меню, жести та службові функції",
                        "Стикеры, шрифт, меню, жесты и служебные функции",
                        "Stickers, fonts, menus, gestures and service tweaks")));

        items.add(UItem.asShadow(MiogramLocale.get(
                "Ці екрани залишені для сумісності. Їхні параметри синхронізовані з основними розділами.",
                "Эти экраны оставлены для совместимости. Их параметры синхронизированы с основными разделами.",
                "These screens stay for compatibility. Their options share the same storage as the canonical sections.")));

        items.add(UItem.asHeader(MiogramLocale.get("Розширені екрани Extera", "Расширенные экраны Extera", "Extera advanced screens")));

        items.add(YumiSettingCell.Factory.of(ROW_EXTERA_GENERAL, R.drawable.yumi_ic_tune, 0xFF8E24AA,
                MiogramLocale.get("Розширені параметри (Extera)", "Расширенные параметры (Extera)", "Advanced options (Extera)"),
                MiogramLocale.get("Переклад, швидкість, профіль, карти та push",
                        "Перевод, скорость, профиль, карты и push",
                        "Translation, speed, profile, maps and push")));

        items.add(YumiSettingCell.Factory.of(ROW_EXTERA_APPEARANCE, R.drawable.yumi_ic_palette, 0xFFE5486B,
                MiogramLocale.get("Розширене оформлення (Extera)", "Расширенное оформление (Extera)", "Advanced appearance (Extera)"),
                MiogramLocale.get("Шрифти, список чатів, папки, розмиття та роздільники",
                        "Шрифты, список чатов, папки, размытие и разделители",
                        "Fonts, chat list, folders, blur and dividers")));

        items.add(YumiSettingCell.Factory.of(ROW_EXTERA_CHATS, R.drawable.yumi_ic_chat, 0xFFFF8F00,
                MiogramLocale.get("Чати (Extera)", "Чаты (Extera)", "Chats (Extera)"),
                MiogramLocale.get("Повний класичний екран чатів Extera",
                        "Полный классический экран чатов Extera",
                        "Full legacy Extera chats screen")));

        items.add(YumiSettingCell.Factory.of(ROW_EXTERA_OTHER, R.drawable.yumi_ic_science, 0xFF607D8B,
                MiogramLocale.get("Інші (Extera)", "Другие (Extera)", "Other (Extera)"),
                MiogramLocale.get("Regex, glyphs, бекапи та небезпечні дії",
                        "Regex, glyphs, бэкапы и опасные действия",
                        "Regex, glyphs, backups and danger-zone actions")));

        items.add(YumiSettingCell.Factory.of(ROW_MIO_CHATS, R.drawable.yumi_ic_camera, 0xFF00C853,
                MiogramLocale.get("Камера та папки (Miogram)", "Камера и папки (Miogram)", "Camera & folders (Miogram)"),
                MiogramLocale.get("Камера, субпапки, стікери та дії з повідомленнями",
                        "Камера, субпапки, стикеры и действия с сообщениями",
                        "Camera, subfolders, stickers and message actions")));

        items.add(UItem.asShadow(MiogramLocale.get(
                "Поступово ці екрани зливаються з основними. Новий верхній рівень — лише «Клієнт → …».",
                "Постепенно эти экраны сливаются с основными. Новый верхний уровень — только «Клиент → …».",
                "These screens are being merged into the canonical sections. The only top level is “Client → …”.")));
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ROW_NEKO_GENERAL) {
            presentFragment(new NekoGeneralSettingsActivity());
        } else if (item.id == ROW_NEKO_CHAT) {
            presentFragment(new NekoChatSettingsActivity());
        } else if (item.id == ROW_EXTERA_GENERAL) {
            presentFragment(new OpenExteraGeneralActivity());
        } else if (item.id == ROW_EXTERA_APPEARANCE) {
            presentFragment(new OpenExteraAppearanceActivity());
        } else if (item.id == ROW_EXTERA_CHATS) {
            presentFragment(new OpenExteraChatsActivity());
        } else if (item.id == ROW_EXTERA_OTHER) {
            presentFragment(new OpenExteraOtherActivity());
        } else if (item.id == ROW_MIO_CHATS) {
            presentFragment(new MiogramChatsSettingsActivity());
        }
    }

    @Override
    public boolean isLightStatusBar() {
        return !Theme.isCurrentThemeDark();
    }
}
