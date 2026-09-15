package app.miogram.bridge.bypass;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.List;

import app.miogram.bridge.MiogramLocale;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;

/**
 * MiogramAntiBlockActivity:
 * Native Telegram settings hub for Anti-Censorship, Russian TSPU throttling bypass,
 * and Fake-TLS MTProto Yandex proxy pool management with live latency metrics.
 */
public class MiogramAntiBlockActivity extends BaseNekoSettingsActivity implements NotificationCenter.NotificationCenterDelegate {

    private int headerStatusRow;
    private int statusCardRow;
    private int masterToggleRow;

    private int headerSettingsRow;
    private int autoBypassRow;
    private int deepDiagnosticsRow;
    private int sensitivityRow;
    private int prioritizeYandexRow;
    private int autoRotateRow;
    private int notifyRow;
    private int runDiagnosticsRow;
    private int settingsInfoRow;

    private int headerServersRow;
    private int pingAllRow;
    private int refreshPoolRow;
    private int addCustomProxyRow;

    private int serversStartRow;
    private int serversEndRow;
    private int serversInfoRow;

    private final List<MiogramAntiBlockEngine.BypassServer> currentDisplayServers = new ArrayList<>();

    @Override
    protected String getActionBarTitle() {
        return MiogramLocale.get("Обхід блокувань (Анти-ТСПУ)", "Обход блокировок (Анти-ТСПУ)", "Anti-Censorship & Bypass");
    }

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.proxySettingsChanged);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.proxyCheckDone);
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            NotificationCenter.getInstance(i).addObserver(this, NotificationCenter.didUpdateConnectionState);
        }
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.proxySettingsChanged);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.proxyCheckDone);
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            NotificationCenter.getInstance(i).removeObserver(this, NotificationCenter.didUpdateConnectionState);
        }
    }

    @Override
    protected void updateRows() {
        super.updateRows();

        // 1. Status Section
        headerStatusRow = addRow();
        statusCardRow = addRow();
        masterToggleRow = addRow();

        // 2. Intelligence & Automation Section
        headerSettingsRow = addRow();
        autoBypassRow = addRow();
        deepDiagnosticsRow = addRow();
        sensitivityRow = addRow();
        prioritizeYandexRow = addRow();
        autoRotateRow = addRow();
        notifyRow = addRow();
        runDiagnosticsRow = addRow();
        settingsInfoRow = addRow();

        // 3. Fake-TLS Pool Section
        headerServersRow = addRow();
        pingAllRow = addRow();
        refreshPoolRow = addRow();
        addCustomProxyRow = addRow();

        currentDisplayServers.clear();
        currentDisplayServers.addAll(MiogramAntiBlockEngine.getInstance().getAllServers());

        if (!currentDisplayServers.isEmpty()) {
            serversStartRow = rowCount;
            rowCount += currentDisplayServers.size();
            serversEndRow = rowCount;
        } else {
            serversStartRow = -1;
            serversEndRow = -1;
        }

        serversInfoRow = addRow();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.proxySettingsChanged || id == NotificationCenter.didUpdateConnectionState || id == NotificationCenter.proxyCheckDone) {
            if (listView == null || listView.getAdapter() == null) return;
            // Connection state broadcasts arrive on the main thread and can hit us
            // mid-layout/scroll -> notifyDataSetChanged() would throw IllegalStateException.
            // Post to the next frame so RecyclerView finishes computing layout first.
            listView.post(() -> {
                if (listView == null || listView.getAdapter() == null) return;
                if (getParentActivity() == null) return;
                try {
                    updateRows();
                    listView.getAdapter().notifyDataSetChanged();
                } catch (Throwable ignore) {}
            });
        }
    }

    @Override
    public void onItemClick(View view, int position, float x, float y) {
        MiogramAntiBlockEngine engine = MiogramAntiBlockEngine.getInstance();

        if (position == masterToggleRow || position == statusCardRow) {
            if (engine.isBypassActive()) {
                engine.disconnectBypass();
                BulletinFactory.of(this).createSimpleBulletin(
                        R.raw.info,
                        MiogramLocale.get("Обхід вимкнено (пряме з'єднання)", "Обход отключен (прямое соединение)", "Bypass disabled (direct connection)")
                ).show();
            } else {
                engine.engageFastestBypassServer(true);
            }
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(engine.isBypassActive());
            }
            updateRows();
            if (listAdapter != null) listAdapter.notifyDataSetChanged();
        } else if (position == autoBypassRow) {
            boolean next = !engine.isAutoBypassEnabled();
            engine.setAutoBypassEnabled(next);
            if (view instanceof TextCheckCell) ((TextCheckCell) view).setChecked(next);
        } else if (position == deepDiagnosticsRow) {
            boolean next = !engine.isDeepDiagnosticsEnabled();
            engine.setDeepDiagnosticsEnabled(next);
            if (view instanceof TextCheckCell) ((TextCheckCell) view).setChecked(next);
        } else if (position == sensitivityRow) {
            AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
            b.setTitle(MiogramLocale.get("Затримка перед перевіркою", "Задержка перед проверкой", "Block Check Delay"));
            String[] options = {
                    "15 " + MiogramLocale.get("секунд", "секунд", "seconds"),
                    "20 " + MiogramLocale.get("секунд (рекомендовано)", "секунд (рекомендовано)", "seconds (recommended)"),
                    "30 " + MiogramLocale.get("секунд", "секунд", "seconds"),
                    "45 " + MiogramLocale.get("секунд", "секунд", "seconds")
            };
            long[] delays = {15000L, 20000L, 30000L, 45000L};
            b.setItems(options, (dialog, which) -> {
                engine.setDetectionDelayMs(delays[which]);
                if (listAdapter != null) listAdapter.notifyDataSetChanged();
            });
            b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
            showDialog(b.create());
        } else if (position == prioritizeYandexRow) {
            boolean next = !engine.isPrioritizeYandexEnabled();
            engine.setPrioritizeYandexEnabled(next);
            if (view instanceof TextCheckCell) ((TextCheckCell) view).setChecked(next);
        } else if (position == autoRotateRow) {
            boolean next = !engine.isAutoRotateEnabled();
            engine.setAutoRotateEnabled(next);
            if (view instanceof TextCheckCell) ((TextCheckCell) view).setChecked(next);
        } else if (position == notifyRow) {
            boolean next = !engine.isNotifyOnActivation();
            engine.setNotifyOnActivation(next);
            if (view instanceof TextCheckCell) ((TextCheckCell) view).setChecked(next);
        } else if (position == runDiagnosticsRow) {
            BulletinFactory.of(this).createSimpleBulletin(
                    R.raw.chats_infotip,
                    MiogramLocale.get("Виконується велика перевірка зв'язку з Telegram DC…", "Выполняется глубокая проверка связи с Telegram DC…", "Testing connection to Telegram DCs…")
            ).show();
            engine.performDeepBlockCheck((isBlocked, report) -> {
                AlertDialog.Builder resDialog = new AlertDialog.Builder(getParentActivity());
                resDialog.setTitle(isBlocked
                        ? MiogramLocale.get("⚠️ Виявлено блокування Telegram!", "⚠️ Обнаружена блокировка Telegram!", "⚠️ Telegram Block Detected!")
                        : MiogramLocale.get("✅ Блокувань не виявлено", "✅ Блокировок не обнаружено", "✅ No Blocks Detected"));
                resDialog.setMessage(report);
                if (isBlocked) {
                    resDialog.setPositiveButton(MiogramLocale.get("Підключити Fake-TLS", "Подключить Fake-TLS", "Connect Fake-TLS"), (d, w) -> {
                        engine.engageFastestBypassServer(true);
                        updateRows();
                        if (listAdapter != null) listAdapter.notifyDataSetChanged();
                    });
                }
                resDialog.setNegativeButton(LocaleController.getString(R.string.Close), null);
                showDialog(resDialog.create());
            });
        } else if (position == pingAllRow) {
            BulletinFactory.of(this).createSimpleBulletin(
                    R.raw.chats_infotip,
                    MiogramLocale.get("Перевірка затримок серверів…", "Проверка задержек серверов…", "Testing server latencies…")
            ).show();
            engine.pingAllServers(() -> {
                if (listView != null && listView.getAdapter() != null) {
                    listView.getAdapter().notifyDataSetChanged();
                }
            });
        } else if (position == refreshPoolRow) {
            BulletinFactory.of(this).createSimpleBulletin(
                    R.raw.chats_infotip,
                    MiogramLocale.get("Оновлення пулу вузлів…", "Обновление пула узлов…", "Refreshing nodes from cloud…")
            ).show();
            engine.fetchRemotePoolAsync(true, success -> {
                updateRows();
                if (listView != null && listView.getAdapter() != null) {
                    listView.getAdapter().notifyDataSetChanged();
                }
                if (Boolean.TRUE.equals(success)) {
                    BulletinFactory.of(this).createSimpleBulletin(
                            R.raw.saved_messages,
                            MiogramLocale.get("Пул Fake-TLS вузлів успішно оновлено", "Пул Fake-TLS узлов успешно обновлен", "Fake-TLS pool refreshed")
                    ).show();
                } else {
                    BulletinFactory.of(this).createSimpleBulletin(
                            R.raw.info,
                            MiogramLocale.get("Використовується актуальний вбудований пул", "Используется актуальный встроенный пул", "Pool up to date")
                    ).show();
                }
            });
        } else if (position == addCustomProxyRow) {
            showAddCustomProxyDialog();
        } else if (position >= serversStartRow && position < serversEndRow) {
            int index = position - serversStartRow;
            if (index >= 0 && index < currentDisplayServers.size()) {
                MiogramAntiBlockEngine.BypassServer server = currentDisplayServers.get(index);
                engine.activateServer(server, true);
                updateRows();
                if (listAdapter != null) listAdapter.notifyDataSetChanged();
            }
        }
    }

    @Override
    public boolean onItemLongClick(View view, int position, float x, float y) {
        if (position >= serversStartRow && position < serversEndRow) {
            int index = position - serversStartRow;
            if (index >= 0 && index < currentDisplayServers.size()) {
                showServerMenu(currentDisplayServers.get(index));
                return true;
            }
        }
        return false;
    }

    private void showServerMenu(MiogramAntiBlockEngine.BypassServer server) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(server.name);

        List<CharSequence> items = new ArrayList<>();
        List<Integer> actions = new ArrayList<>();

        items.add(MiogramLocale.get("Підключитися", "Подключиться", "Connect"));
        actions.add(0);

        items.add(MiogramLocale.get("Перевірити затримку (ping)", "Проверить пинг", "Check ping"));
        actions.add(1);

        items.add(MiogramLocale.get("Копіювати посилання tg://proxy", "Копировать ссылку tg://proxy", "Copy proxy link"));
        actions.add(2);

        if (server.isCustom) {
            items.add(MiogramLocale.get("Видалити сервер", "Удалить сервер", "Delete server"));
            actions.add(3);
        }

        builder.setItems(items.toArray(new CharSequence[0]), (dialog, which) -> {
            int action = actions.get(which);
            if (action == 0) {
                MiogramAntiBlockEngine.getInstance().activateServer(server, true);
            } else if (action == 1) {
                ConnectionsManager.getInstance(currentAccount).checkProxy(
                        server.address, server.port, "", "", server.secret,
                        time -> AndroidUtilities.runOnUIThread(() -> {
                            server.checkTime = System.currentTimeMillis();
                            if (time >= 0) {
                                server.available = true;
                                server.ping = time;
                            } else {
                                server.available = false;
                                server.ping = 0;
                            }
                            if (listAdapter != null) listAdapter.notifyDataSetChanged();
                        })
                );
            } else if (action == 2) {
                SharedConfig.ProxyInfo info = server.toProxyInfo();
                String link = info.getLink();
                ClipboardManager clipboard = (ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("proxy", link);
                clipboard.setPrimaryClip(clip);
                BulletinFactory.of(this).createCopyLinkBulletin().show();
            } else if (action == 3 && server.isCustom) {
                MiogramAntiBlockEngine.getInstance().removeCustomServer(server);
                updateRows();
                if (listAdapter != null) listAdapter.notifyDataSetChanged();
            }
        });

        showDialog(builder.create());
    }

    private void showAddCustomProxyDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(MiogramLocale.get("Додати Fake-TLS сервер", "Добавить Fake-TLS сервер", "Add Fake-TLS Server"));

        LinearLayout layout = new LinearLayout(getParentActivity());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(10), AndroidUtilities.dp(24), AndroidUtilities.dp(10));

        final EditText addressInput = new EditText(getParentActivity());
        addressInput.setHint(MiogramLocale.get("Хост / IP (напр. proxy.example.com)", "Хост / IP", "Host / IP"));
        layout.addView(addressInput);

        final EditText portInput = new EditText(getParentActivity());
        portInput.setHint(MiogramLocale.get("Порт (зазвичай 443)", "Порт (обычно 443)", "Port (usually 443)"));
        portInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        portInput.setText("443");
        layout.addView(portInput);

        final EditText secretInput = new EditText(getParentActivity());
        secretInput.setHint(MiogramLocale.get("Секрет (ee... або класичний hex)", "Секрет (ee... или hex)", "Secret (ee... or hex)"));
        layout.addView(secretInput);

        final EditText sniInput = new EditText(getParentActivity());
        sniInput.setHint(MiogramLocale.get("Маскування SNI (за замовчуванням docs.yandex.ru)", "Маскировка SNI (по умолчанию docs.yandex.ru)", "SNI Masking (default docs.yandex.ru)"));
        sniInput.setText("docs.yandex.ru");
        layout.addView(sniInput);

        builder.setView(layout);

        builder.setPositiveButton(MiogramLocale.get("Додати", "Добавить", "Add"), (dialog, which) -> {
            String address = addressInput.getText().toString().trim();
            int port = 443;
            try {
                port = Utilities.parseInt(portInput.getText().toString().trim());
            } catch (Exception ignored) {}
            String secret = secretInput.getText().toString().trim();
            String sni = sniInput.getText().toString().trim();

            if (TextUtils.isEmpty(address) || port <= 0 || TextUtils.isEmpty(secret)) {
                return;
            }

            // If secret is regular hex without 'ee', automatically convert with SNI
            if (!secret.startsWith("ee") && !TextUtils.isEmpty(sni)) {
                secret = MiogramAntiBlockEngine.buildFakeTlsSecret(secret, sni);
            }

            String id = "custom_" + System.currentTimeMillis();
            String name = "Власний: " + (!TextUtils.isEmpty(sni) ? sni : address);
            MiogramAntiBlockEngine.BypassServer server = new MiogramAntiBlockEngine.BypassServer(id, name, address, port, secret, sni, true);
            MiogramAntiBlockEngine.getInstance().addCustomServer(server);
            updateRows();
            if (listAdapter != null) listAdapter.notifyDataSetChanged();
            BulletinFactory.of(this).createSimpleBulletin(
                    R.raw.saved_messages,
                    MiogramLocale.get("Сервер успішно додано", "Сервер успешно добавлен", "Server added")
            ).show();
        });

        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public int getItemViewType(int position) {
            if (position == headerStatusRow || position == headerSettingsRow || position == headerServersRow) {
                return TYPE_HEADER;
            } else if (position == autoBypassRow || position == deepDiagnosticsRow || position == prioritizeYandexRow || position == autoRotateRow || position == notifyRow) {
                return TYPE_CHECK;
            } else if (position == settingsInfoRow || position == serversInfoRow) {
                return TYPE_INFO_PRIVACY;
            } else if (position == statusCardRow || position == sensitivityRow) {
                return TYPE_DETAIL_SETTINGS;
            } else if (position == masterToggleRow || position == pingAllRow || position == refreshPoolRow || position == addCustomProxyRow || position == runDiagnosticsRow) {
                return TYPE_TEXT;
            } else if (position >= serversStartRow && position < serversEndRow) {
                return TYPE_DETAIL_SETTINGS;
            }
            return TYPE_SETTINGS;
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean partial) {
            MiogramAntiBlockEngine engine = MiogramAntiBlockEngine.getInstance();
            boolean isBypassActive = engine.isBypassActive();

            switch (holder.getItemViewType()) {
                case TYPE_HEADER: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    if (position == headerStatusRow) {
                        cell.setText(MiogramLocale.get("Стан захисту та з'єднання", "Состояние защиты и соединения", "Connection & Protection Status"));
                    } else if (position == headerSettingsRow) {
                        cell.setText(MiogramLocale.get("Параметри розумного обходу", "Параметры умного обхода", "Smart Bypass Intelligence"));
                    } else if (position == headerServersRow) {
                        cell.setText(MiogramLocale.get("Пул Fake-TLS серверів (Яндекс)", "Пул Fake-TLS серверов (Яндекс)", "Fake-TLS Proxy Pool (Yandex)"));
                    }
                    break;
                }
                case TYPE_DETAIL_SETTINGS: {
                    TextDetailSettingsCell cell = (TextDetailSettingsCell) holder.itemView;
                    if (position == statusCardRow) {
                        int state = ConnectionsManager.getInstance(currentAccount).getConnectionState();
                        String title;
                        String subtitle;

                        if (isBypassActive) {
                            MiogramAntiBlockEngine.BypassServer active = engine.getCurrentActiveBypassServer();
                            String domain = active != null && active.sniDomain != null ? active.sniDomain : "docs.yandex.ru";
                            long ping = SharedConfig.currentProxy != null ? SharedConfig.currentProxy.ping : 0;
                            String pingStr = ping > 0 ? (ping + " ms") : "активно";

                            title = "🛡️ " + MiogramLocale.get("Захист активовано (ТСПУ обійдено)", "Защита активна (ТСПУ обойден)", "Protected (TSPU Bypassed)");
                            subtitle = MiogramLocale.get(
                                    "Маскування: " + domain + " • Затримка: " + pingStr,
                                    "Маскировка: " + domain + " • Задержка: " + pingStr,
                                    "SNI Masking: " + domain + " • Latency: " + pingStr
                            );
                        } else if (state == ConnectionsManager.ConnectionStateConnecting) {
                            title = "⚠️ " + MiogramLocale.get("Виявлено затримку прямого зв'язку…", "Обнаружена задержка прямого соединения…", "Detecting throttling…");
                            subtitle = MiogramLocale.get("Пряме з'єднання сповільнено, підготовка до обходу", "Прямое соединение замедлено, подготовка к обходу", "Direct connection throttled");
                        } else {
                            title = "⚪ " + MiogramLocale.get("Пряме з'єднання", "Прямое соединение", "Direct Connection");
                            subtitle = MiogramLocale.get("Проксі вимкнено • Прямий маршрут до Telegram", "Прокси выключен • Прямой маршрут", "Proxy disabled • Direct routing");
                        }
                        cell.setTextAndValue(title, subtitle, false);
                    } else if (position == sensitivityRow) {
                        long delay = engine.getDetectionDelayMs() / 1000L;
                        String val = delay + " " + MiogramLocale.get("сек", "сек", "s");
                        cell.setTextAndValue(
                                MiogramLocale.get("Затримка перед перевіркою блокування", "Задержка перед проверкой блокировки", "Delay before block check"),
                                val,
                                true
                        );
                    } else if (position >= serversStartRow && position < serversEndRow) {
                        int index = position - serversStartRow;
                        if (index >= 0 && index < currentDisplayServers.size()) {
                            MiogramAntiBlockEngine.BypassServer s = currentDisplayServers.get(index);
                            boolean isCurrent = SharedConfig.isProxyEnabled() && SharedConfig.currentProxy != null &&
                                    s.address.equalsIgnoreCase(SharedConfig.currentProxy.address) && s.port == SharedConfig.currentProxy.port;

                            String pingIndicator;
                            if (s.checking) {
                                pingIndicator = "⏳ " + MiogramLocale.get("Перевірка…", "Проверка…", "Testing…");
                            } else if (s.available && s.ping > 0) {
                                String colorDot = s.ping < 120 ? "🟢" : (s.ping < 350 ? "🟡" : "🟠");
                                pingIndicator = colorDot + " " + s.ping + " ms";
                            } else if (s.checkTime > 0 && !s.available) {
                                pingIndicator = "🔴 " + MiogramLocale.get("Недоступний", "Недоступен", "Offline");
                            } else {
                                pingIndicator = "⚪ " + MiogramLocale.get("Не перевірено", "Не проверен", "Untested");
                            }

                            String title = (isCurrent ? "✓ " : "") + s.name;
                            String subtitle = s.address + ":" + s.port + " • SNI: " + (!TextUtils.isEmpty(s.sniDomain) ? s.sniDomain : "docs.yandex.ru") + " • " + pingIndicator;
                            cell.setTextAndValue(title, subtitle, index < currentDisplayServers.size() - 1);
                        }
                    }
                    break;
                }
                case TYPE_TEXT: {
                    TextCell cell = (TextCell) holder.itemView;
                    if (position == masterToggleRow) {
                        if (isBypassActive) {
                            cell.setTextAndIcon(MiogramLocale.get("Вимкнути обхід (повернутися до прямого)", "Отключить обход", "Disable bypass (switch to direct)"), R.drawable.msg_cancel, false);
                        } else {
                            cell.setTextAndIcon(MiogramLocale.get("⚡ Підключити найшвидший Fake-TLS вузол", "⚡ Подключить быстрейший Fake-TLS узел", "⚡ Connect fastest Fake-TLS node"), R.drawable.msg_bot, false);
                        }
                    } else if (position == pingAllRow) {
                        cell.setTextAndIcon(MiogramLocale.get("⚡ Перевірити швидкість усіх серверів", "⚡ Проверить пинг всех серверов", "⚡ Ping all servers"), R.drawable.msg_retry, true);
                    } else if (position == refreshPoolRow) {
                        cell.setTextAndIcon(MiogramLocale.get("🔄 Оновити список серверів з хмари", "🔄 Обновить список с облака", "🔄 Refresh cloud pool"), R.drawable.msg_channel, true);
                    } else if (position == addCustomProxyRow) {
                        cell.setTextAndIcon(MiogramLocale.get("➕ Додати власний Fake-TLS сервер", "➕ Добавить свой Fake-TLS сервер", "➕ Add custom Fake-TLS server"), R.drawable.msg_add, true);
                    } else if (position == runDiagnosticsRow) {
                        cell.setTextAndIcon(MiogramLocale.get("🔍 Провести діагностику блокування зараз", "🔍 Провести диагностику блокировки сейчас", "🔍 Run Block Diagnostics Now"), R.drawable.msg_retry, false);
                    }
                    break;
                }
                case TYPE_CHECK: {
                    TextCheckCell cell = (TextCheckCell) holder.itemView;
                    if (position == autoBypassRow) {
                        cell.setTextAndValueAndCheck(
                                MiogramLocale.get("Розумний авто-обхід ТСПУ", "Умный авто-обход ТСПУ", "Smart TSPU Auto-Bypass"),
                                MiogramLocale.get("Автоматично активувати Fake-TLS при підтвердженні блокування", "Автоматически включать Fake-TLS при подтверждении блокировки", "Engage Fake-TLS when block is confirmed"),
                                engine.isAutoBypassEnabled(),
                                true,
                                true
                        );
                    } else if (position == deepDiagnosticsRow) {
                        cell.setTextAndValueAndCheck(
                                MiogramLocale.get("Велика перевірка блокування", "Глубокая проверка блокировки", "Deep Block Diagnostics"),
                                MiogramLocale.get("Перевіряти всі DC Telegram перед увімкненням, щоб не вмикати обхід при звичайних лагах", "Проверять все DC Telegram перед включением, чтобы не включать обход при обычных лагах", "Probe all Telegram DCs before enabling to prevent false triggers on lag"),
                                engine.isDeepDiagnosticsEnabled(),
                                true,
                                true
                        );
                    } else if (position == prioritizeYandexRow) {
                        cell.setTextAndValueAndCheck(
                                MiogramLocale.get("Маскування під Яндекс (docs.yandex.ru)", "Маскировка под Яндекс (docs.yandex.ru)", "Yandex SNI Masking"),
                                MiogramLocale.get("Пріоритет вузлів з білого списку РФ для уникнення фільтрації DPI", "Приоритет узлов из белого списка РФ", "Prioritize RF whitelisted SNIs"),
                                engine.isPrioritizeYandexEnabled(),
                                true,
                                true
                        );
                    } else if (position == autoRotateRow) {
                        cell.setTextAndValueAndCheck(
                                MiogramLocale.get("Автоматична ротація серверів", "Автоматическая ротация серверов", "Automatic Failover Rotation"),
                                MiogramLocale.get("Перемикатися на інший вузол при зростанні затримки > 2.5 сек", "Переключаться при задержке > 2.5 сек", "Switch if latency exceeds 2.5s"),
                                engine.isAutoRotateEnabled(),
                                true,
                                true
                        );
                    } else if (position == notifyRow) {
                        cell.setTextAndValueAndCheck(
                                MiogramLocale.get("Сповіщення про активацію", "Уведомление об активации", "Activation Notification"),
                                MiogramLocale.get("Показувати сповіщення при автоматичному увімкненні обходу", "Показывать уведомление при автоматическом включении", "Show banner when bypass engages"),
                                engine.isNotifyOnActivation(),
                                true,
                                false
                        );
                    }
                    break;
                }
                case TYPE_INFO_PRIVACY: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == settingsInfoRow) {
                        cell.setText(MiogramLocale.get(
                                "Анти-ТСПУ постійно моніторить з'єднання. Якщо прямий маршрут блокується оператором, Miogram безшовно перемикається на замаскований канал без перезапуску чатів.",
                                "Анти-ТСПУ постоянно отслеживает соединение. Если прямой маршрут блокируется оператором, Miogram бесшовно переключается на замаскированный канал без перезапуска чатов.",
                                "Smart Anti-Bypass continuously monitors connectivity and transparently switches to the masked channel upon detecting throttling."
                        ));
                    } else if (position == serversInfoRow) {
                        cell.setText(MiogramLocale.get(
                                "Fake-TLS маскує початковий пакет рукостискання під звичайний TLS 1.3 ClientHello до сервісів Яндекса (docs.yandex.ru, ya.ru). Завдяки цьому ТСПУ та DPI пропускають весь трафік на повній швидкості без блокувань.",
                                "Fake-TLS маскирует начальный пакет рукопожатия под обычный TLS 1.3 ClientHello к сервисам Яндекса (docs.yandex.ru, ya.ru). Благодаря этому ТСПУ и DPI пропускают трафик на полной скорости.",
                                "Fake-TLS masks initial packets as authentic TLS 1.3 ClientHello to Yandex services, fully bypassing EcoFilter DPI."
                        ));
                    }
                    break;
                }
            }
        }
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }
}
