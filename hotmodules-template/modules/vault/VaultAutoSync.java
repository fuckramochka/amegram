package com.amegram.mods.vault;

import android.content.Context;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Автоматизація хмарного сховища:
 * - Фонова періодична синхронізація списку файлів і чанків.
 * - Автоматичний бекап вхідних файлів із чатів / Saved Messages у зашифроване сховище.
 */
public final class VaultAutoSync {

    private static ScheduledExecutorService scheduler;
    private static final AtomicBoolean running = new AtomicBoolean(false);

    public static synchronized void start(Context context, long intervalMinutes) {
        stop();
        if (intervalMinutes <= 0) intervalMinutes = 30;
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                syncNow();
            } catch (Throwable e) {
                FileLog.e("VaultAutoSync error", e);
            }
        }, 1, intervalMinutes, TimeUnit.MINUTES);
    }

    public static synchronized void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    public static void syncNow() {
        if (running.getAndSet(true)) return;
        Utilities.globalQueue.postRunnable(() -> {
            try {
                int account = UserConfig.selectedAccount;
                if (!VaultEngine.hasVault(account)) return;
                long chatId = VaultEngine.getVaultChatId(account);
                VaultEngine.syncVaultFiles(account, chatId, new VaultEngine.SyncCallback() {
                    @Override
                    public void onSyncProgress(int count) {
                    }

                    @Override
                    public void onSyncComplete(java.util.ArrayList<VaultFile> files) {
                        running.set(false);
                    }

                    @Override
                    public void onSyncError(String message) {
                        running.set(false);
                    }
                });
            } catch (Throwable e) {
                running.set(false);
            }
        });
    }
}
