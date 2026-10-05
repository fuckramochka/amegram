package app.amegram.hot.api;

/**
 * Інтерфейс сервісу автоматизації (модуль automation).
 */
public interface HotAutomation {
    boolean isAutoSyncVaultEnabled();
    boolean isAutoBackupSavedMessages();
    boolean isAutoCleanCacheEnabled();
    void triggerVaultSync();
}
