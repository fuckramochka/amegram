package com.amegram.mods.experimental;

import android.content.Context;

import java.util.List;

import app.amegram.hot.api.HotExperimental;
import app.amegram.hot.api.HotHost;
import app.amegram.hot.api.HotModule;
import app.amegram.hot.api.HotRow;
import app.amegram.hot.api.HotServices;

/**
 * Експериментальні налаштування:
 * - Безліміт закріплених чатів та стікерів
 * - Прискорення завантаження файлів (Upload Boost)
 * - Шумозаглушення аудіо
 * - Підвищений бітрейт та якість відео
 * - Локальне збереження видалених повідомлень та історії редагувань
 * - Розширені функції Amegram:
 *   * Дії свайпом (swipe actions)
 *   * Широкі пости у каналах (wide posts)
 *   * Обчислення у полі вводу (inline math result)
 *   * Переклад чатів без Telegram Premium
 *   * Адблок у вбудованому браузері
 *   * Лічильник пересилань у каналах
 *   * Приховування часу на стікерах
 *   * Лічильник папок тільки для чатів зі звуком
 */
public class ExperimentalModule implements HotModule, HotExperimental {

    private HotHost host;

    @Override
    public String moduleId() {
        return "experimental";
    }

    @Override
    public void onAttach(Context appContext, HotHost host) {
        this.host = host;
        host.registerService(HotServices.EXPERIMENTAL, this);
    }

    @Override
    public void onDetach() {
        if (host != null) host.unregisterService(HotServices.EXPERIMENTAL);
        host = null;
    }

    private boolean get(String key, boolean def) {
        return host != null && host.getBool(key, def);
    }

    @Override
    public boolean isUnlimitedPinned() {
        return get("unlimited_pinned", true);
    }

    @Override
    public boolean isUnlimitedFavStickers() {
        return get("unlimited_fav_stickers", true);
    }

    @Override
    public boolean isUploadBoost() {
        return get("upload_boost", true);
    }

    @Override
    public boolean isNoiseSuppression() {
        return get("noise_suppression", false);
    }

    @Override
    public boolean isEnhancedVideoBitrate() {
        return get("enhanced_video_bitrate", true);
    }

    @Override
    public boolean isSendMp4AsVideo() {
        return get("send_mp4_as_video", true);
    }

    @Override
    public boolean isPreferHardwareDecoder() {
        return get("hardware_decoder", true);
    }

    @Override
    public boolean isSaveDeletedMessages() {
        return get("save_deleted_messages", true);
    }

    @Override
    public boolean isSaveEditHistory() {
        return get("save_edit_history", true);
    }

    // Розширення Amegram:
    @Override
    public boolean isWidePosts() {
        return get("wide_posts", true);
    }

    @Override
    public boolean isInlineMathResult() {
        return get("inline_math", true);
    }

    @Override
    public boolean isSwipeActions() {
        return get("swipe_actions", true);
    }

    @Override
    public boolean isTranslateChatNoPremium() {
        return get("translate_no_premium", true);
    }

    @Override
    public boolean isBrowserAdblock() {
        return get("browser_adblock", true);
    }

    @Override
    public boolean isShowForwardsCount() {
        return get("forwards_count", true);
    }

    @Override
    public boolean isHideStickerTime() {
        return get("hide_sticker_time", false);
    }

    @Override
    public boolean isTabCounterUnmutedOnly() {
        return get("tab_counter_unmuted", false);
    }

    @Override
    public boolean hasSettings() {
        return true;
    }

    @Override
    public String settingsTitle() {
        return "Експерименти";
    }

    @Override
    public void fillSettings(List<HotRow> rows) {
        rows.add(HotRow.header("Інтерфейс та жести (12.10.6)"));
        rows.add(HotRow.switchRow("swipe_actions", "Дії свайпом повідомлення",
                "Свайп вліво та вгору/вниз для швидкого вибору дії", isSwipeActions()));
        rows.add(HotRow.switchRow("wide_posts", "Широкі пости у каналах",
                "Пости на всю ширину екрана без зайвих відступів", isWidePosts()));
        rows.add(HotRow.switchRow("inline_math", "Обчислення у полі вводу",
                "Автоматичний розрахунок математичних виразів (наприклад 128*8=)", isInlineMathResult()));
        rows.add(HotRow.switchRow("forwards_count", "Лічильник пересилань у каналах",
                "Показувати точну кількість пересилань біля переглядів", isShowForwardsCount()));
        rows.add(HotRow.switchRow("hide_stickerTime", "Приховати час на стікерах",
                "Прибирати мітку часу поверх стікерів", isHideStickerTime()));

        rows.add(HotRow.header("Безкоштовний переклад та AdBlock"));
        rows.add(HotRow.switchRow("translate_no_premium", "Переклад чатів без Premium",
                "Кнопка перекладу цілих чатів через безкоштовні перекладачі", isTranslateChatNoPremium()));
        rows.add(HotRow.switchRow("browser_adblock", "Адблок у вбудованому браузері",
                "Блокування до 90% реклами та банерів при відкритті посилань", isBrowserAdblock()));

        rows.add(HotRow.header("Діалоги та сповіщення"));
        rows.add(HotRow.switchRow("unlimited_pinned", "Безліміт закріплених чатів",
                "Знімає обмеження на кількість закріплених діалогів", isUnlimitedPinned()));
        rows.add(HotRow.switchRow("unlimited_fav_stickers", "Безліміт улюблених стікерів",
                "Дозволяє додавати необмежену кількість стікерів в обране", isUnlimitedFavStickers()));
        rows.add(HotRow.switchRow("tab_counter_unmuted", "Лічильник папок тільки зі звуком",
                "Ігнорувати чати без звуку у лічильниках вкладок", isTabCounterUnmutedOnly()));

        rows.add(HotRow.header("Мережа та звук"));
        rows.add(HotRow.switchRow("upload_boost", "Прискорення завантаження",
                "Паралельні з'єднання для швидшого надсилання великих файлів", isUploadBoost()));
        rows.add(HotRow.switchRow("noise_suppression", "Шумозаглушення аудіо",
                "Фільтрація сторонніх шумів при записі голосових повідомлень", isNoiseSuppression()));

        rows.add(HotRow.header("Медіа та відео"));
        rows.add(HotRow.switchRow("enhanced_video_bitrate", "Підвищений бітрейт відео",
                "Збереження високої якості та чіткості при стисненні", isEnhancedVideoBitrate()));
        rows.add(HotRow.switchRow("send_mp4_as_video", "Надсилати MP4 як відео",
                "Автоматично надсилати MP4 як відеопотік, а не документ", isSendMp4AsVideo()));
        rows.add(HotRow.switchRow("hardware_decoder", "Апаратний декодер відео",
                "Відтворення важких відео з апаратним прискоренням GPU", isPreferHardwareDecoder()));

        rows.add(HotRow.info("Видалені повідомлення переїхали в модуль Привида (одне ціле)."));
        rows.add(HotRow.info("Експериментальні оптимізації працюють нативно у клієнті Yumigram."));
    }

    @Override
    public void onSettingsToggle(String key, boolean value) {
        if (host != null) {
            host.setBool(key, value);
        }
    }

    @Override
    public void onSettingsAction(String rowId) {
        if ("export_saved_messages".equals(rowId)) {
            app.amegram.database.YumiBackupHelper.exportDatabaseToSavedMessages(
                    org.telegram.messenger.UserConfig.selectedAccount,
                    host != null ? host.context() : null,
                    null
            );
        }
    }

    @Override
    public void fillHubRows(List<HotRow> rows) {
        rows.add(HotRow.button("exp_hub", "Експерименти", "Твіки швидкості, жести та безліміти"));
    }

    @Override
    public void onHubAction(String rowId) {
        if ("exp_hub".equals(rowId) && host != null) {
            host.openSettings();
        }
    }
}
