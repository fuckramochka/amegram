package app.miogram.bridge.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.net.Uri;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SeekBarView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import app.exteraless.ai.AiConfig;
import app.exteraless.ai.AiController;
import app.exteraless.ai.data.Message;
import app.exteraless.ai.data.ModelInfo;
import app.exteraless.ai.data.Provider;
import app.exteraless.ai.data.Role;
import app.exteraless.ai.data.Service;
import app.exteraless.ai.network.Client;
import app.exteraless.ai.network.GenerationCallback;
import app.exteraless.ai.network.ModelsCatalog;
import app.exteraless.ai.ui.AiModelPicker;
import app.miogram.bridge.MiogramLocale;
import app.miogram.bridge.ai.AmegramTranscriptionPrefs;
import app.miogram.bridge.ai.MiogramAiService;
import app.miogram.bridge.ai.companion.MiogramCompanionActivity;
import app.miogram.bridge.ai.companion.MiogramCompanionPrefs;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

/**
 * Unified Yumi AI Settings Module:
 * - AI Companion (Ame-chan & KAngel)
 * - AI Engine, Provider & Models (Gemini, OpenAI, DeepSeek, OpenRouter, Custom URL)
 * - Unified Keyring & Authentication (Multi-key rotation & connection testing)
 * - Features & Privacy (STT voice transcription, Telegram Premium override, PII Shield)
 * - Generation Parameters (System prompt / roles, Temperature slider, Streaming, History)
 */
public class MiogramAiSettingsActivity extends BaseNekoSettingsActivity {

    private static final String PREFS = "miogram_ai_prefs";
    private static final int TYPE_TEMPERATURE = 100;
    private static final int MAX_TEMPERATURE = 20;

    private boolean sliderTouched;
    private TemperatureCell temperatureCell;
    private Client testClient;
    private String testRequestId;
    private CharSequence testStatus;
    private boolean testFailed;

    // Section 1: Companion
    private int headerCompanionRow;
    private int openCompanionRow;
    private int companionChoiceRow;
    private int companionBottomTabRow;
    private int companionInfoRow;

    // Section 2: Provider & Model
    private int headerProviderRow;
    private int providerRow;
    private int modelRow;
    private int urlRow;
    private int presetsRow;
    private int reasoningRow;
    private int hardwareRow;
    private int hardwareInfoRow;

    // Section 3: Keyring & Auth
    private int headerKeysRow;
    private int keyRow;
    private int getKeyRow;
    private int testRow;
    private int keysInfoRow;

    // Section 4: Features & Privacy
    private int headerFeaturesRow;
    private int voiceTranscribeInfoRow;
    private int transcribePreferForPremiumRow;
    private int piiMaskRow;
    private int featuresInfoRow;

    // Section 5: Generation
    private int headerGenerationRow;
    private int roleRow;
    private int temperatureRow;
    private int streamingRow;
    private int saveHistoryRow;
    private int clearHistoryRow;
    private int generationInfoRow;

    @Override
    protected String getActionBarTitle() {
        return "Yumi AI";
    }

    @Override
    public int getSearchGuid() {
        return 26000;
    }

    @Override
    public int getSearchIcon() {
        return R.drawable.ai_chat;
    }

    @Override
    public String getSearchPrefix() {
        return "OEAi";
    }

    @Override
    protected String getKey() {
        return "yumi_ai";
    }

    @Override
    public boolean onFragmentCreate() {
        ModelsCatalog.load(loaded -> {
            if (loaded && listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
        });
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        if (testClient != null && testRequestId != null) {
            testClient.cancel(testRequestId);
        }
        super.onFragmentDestroy();
    }

    @Override
    public boolean isSwipeBackEnabled(MotionEvent event) {
        return !sliderTouched;
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    @Override
    protected void updateRows() {
        super.updateRows();

        // 1. Companion
        headerCompanionRow = addRow();
        openCompanionRow = addRow();
        companionChoiceRow = addRow();
        companionBottomTabRow = addRow();
        companionInfoRow = addRow();

        // 2. Provider & Model
        headerProviderRow = addRow();
        providerRow = addRow();
        modelRow = addRow();
        urlRow = isCustomProvider() ? addRow() : -1;
        presetsRow = addRow();
        reasoningRow = addRow();
        hardwareRow = addRow();
        hardwareInfoRow = addRow();

        // 3. Keyring & Auth
        headerKeysRow = addRow();
        keyRow = addRow();
        getKeyRow = keyUrl() != null ? addRow() : -1;
        testRow = addRow();
        keysInfoRow = addRow();

        // 4. Features & Privacy
        headerFeaturesRow = addRow();
        voiceTranscribeInfoRow = addRow();
        transcribePreferForPremiumRow = addRow();
        piiMaskRow = addRow();
        featuresInfoRow = addRow();

        // 5. Generation
        headerGenerationRow = addRow();
        roleRow = addRow();
        temperatureRow = addRow();
        streamingRow = addRow();
        saveHistoryRow = addRow();
        clearHistoryRow = addRow();
        generationInfoRow = addRow();
    }

    private android.content.SharedPreferences prefs() {
        Context ctx = getParentActivity() != null ? getParentActivity() : ApplicationLoader.applicationContext;
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private Service service() {
        Service selected = AiController.getSelected();
        return selected == null ? AiConfig.DEFAULT_SERVICE : selected;
    }

    private boolean isCustomProvider() {
        return Provider.matching(service().getUrl()) == null;
    }

    private String keyUrl() {
        Provider provider = Provider.matching(service().getUrl());
        return provider == null ? "https://aistudio.google.com/app/apikey" : provider.getKeyUrl();
    }

    private String savedKey() {
        List<String> keys = MiogramAiService.getApiKeys();
        return keys.isEmpty() ? "" : keys.get(0);
    }

    private String savedKeysText() {
        return TextUtils.join("\n", MiogramAiService.getApiKeys());
    }

    private String keySummary() {
        int count = MiogramAiService.getApiKeys().size();
        if (count == 0) return maskKey("");
        if (count == 1) return maskKey(savedKey());
        return MiogramLocale.get(count + " ключі", count + " ключей", count + " keys") + " · " + maskKey(savedKey());
    }

    private static String maskKey(String key) {
        if (key == null || key.isEmpty()) {
            return MiogramLocale.get("Не встановлено", "Не установлено", "Not set");
        }
        int length = key.length();
        return length <= 8 ? "••••" : key.substring(0, Math.min(4, length)) + "••••" + key.substring(Math.max(0, length - 4));
    }

    private boolean piiMaskEnabled() {
        return prefs().getBoolean("pii_mask", true);
    }

    private void store(Service service) {
        AiController.saveService(service);
        if (AiConfig.isGeminiService(service)) {
            if (!TextUtils.isEmpty(service.getModel())) {
                MiogramAiService.setModel(service.getModel());
            }
        }
        testStatus = null;
        testFailed = false;
        rebuildRowsAndNotify();
    }

    private void saveKeys(String raw) {
        List<String> parsed = MiogramAiService.parseApiKeys(raw);
        MiogramAiService.setApiKeys(parsed);
        String primary = parsed.isEmpty() ? "" : parsed.get(0);
        Service service = service();
        service.setKey(primary);
        AiController.saveService(service);
        testStatus = null;
        testFailed = false;
        rebuildRowsAndNotify();
    }

    private void saveModel(String model) {
        Service service = service();
        service.setModel(model);
        if (AiConfig.isGeminiService(service)) {
            MiogramAiService.setModel(model);
        }
        AiController.saveService(service);
        rebuildRowsAndNotify();
    }

    private void rebuildRowsAndNotify() {
        updateRows();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    private void notifyRow(int row) {
        if (listAdapter != null && row >= 0) {
            listAdapter.notifyItemChanged(row);
        }
    }

    private String modelTitle() {
        ModelInfo info = ModelsCatalog.get(service().getModel());
        return info == null ? service().getModel() : info.getName();
    }

    private String providerTitle() {
        Provider provider = Provider.matching(service().getUrl());
        return provider == null ? getString(R.string.OEAiProviderCustom) : provider.getTitle();
    }

    private String reasoningTitle() {
        switch (service().getReasoningEffort()) {
            case Service.REASONING_LOW:
                return getString(R.string.OEAiReasoningLow);
            case Service.REASONING_MEDIUM:
                return getString(R.string.OEAiReasoningMedium);
            case Service.REASONING_HIGH:
                return getString(R.string.OEAiReasoningHigh);
            default:
                return getString(R.string.OEAiReasoningOff);
        }
    }

    private String describe(Service service) {
        String host = AndroidUtilities.getHostAuthority(service.getUrl());
        if (TextUtils.isEmpty(host)) {
            host = service.getUrl();
        }
        String model = service.getShortModel();
        if (TextUtils.isEmpty(host)) {
            return model;
        }
        return TextUtils.isEmpty(model) ? host : host + " · " + model;
    }

    @Override
    public void onItemClick(View view, int position, float x, float y) {
        if (position == openCompanionRow) {
            presentFragment(new MiogramCompanionActivity());
        } else if (position == companionChoiceRow) {
            showCompanionPicker();
        } else if (position == companionBottomTabRow) {
            boolean next = !MiogramCompanionPrefs.isContactsReplacedWithAi();
            MiogramCompanionPrefs.setContactsReplacedWithAi(next);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(next);
            }
        } else if (position == providerRow) {
            showProviderSelector();
        } else if (position == modelRow) {
            showModelPicker();
        } else if (position == urlRow) {
            showInput(getString(R.string.OEAiUrl), service().getUrl(), InputType.TYPE_TEXT_VARIATION_URI, value -> {
                Service service = service();
                service.setUrl(value);
                store(service);
            });
        } else if (position == presetsRow) {
            showPresets();
        } else if (position == reasoningRow) {
            showReasoningSelector();
        } else if (position == keyRow) {
            showKeyDialog();
        } else if (position == getKeyRow) {
            String url = keyUrl();
            if (url != null && getParentActivity() != null) {
                Browser.openUrl(getParentActivity(), url);
            }
        } else if (position == testRow) {
            testConnection();
        } else if (position == voiceTranscribeInfoRow) {
            showTranscribeProviderPicker();
        } else if (position == transcribePreferForPremiumRow) {
            boolean next = !AmegramTranscriptionPrefs.isPreferAmegramForPremium();
            AmegramTranscriptionPrefs.setPreferAmegramForPremium(next);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(next);
            }
        } else if (position == piiMaskRow) {
            boolean next = !piiMaskEnabled();
            prefs().edit().putBoolean("pii_mask", next).apply();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(next);
            }
        } else if (position == roleRow) {
            showRoleSelector();
        } else if (position == streamingRow) {
            AiConfig.setResponseStreaming(!AiConfig.getResponseStreaming());
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(AiConfig.getResponseStreaming());
            }
        } else if (position == saveHistoryRow) {
            AiConfig.setSaveHistory(!AiConfig.getSaveHistory());
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(AiConfig.getSaveHistory());
            }
        } else if (position == clearHistoryRow) {
            AiConfig.clearConversationHistory();
            BulletinFactory.of(this).createSimpleBulletin(R.raw.ic_delete, getString(R.string.OEAiHistoryCleared)).show();
        }
    }

    private void showCompanionPicker() {
        Context ctx = getParentActivity();
        if (ctx == null) return;
        String[] companions = {
                "Юмі (Yumi ໒꒱) (" + MiogramLocale.get("Цундере-хакерка, гостра на язик", "Цундере-хакерша, острая на язык", "Tsundere hacker, sharp-tongued") + ")",
                "Міо (Mio ☕) (" + MiogramLocale.get("Домашній затишок, лагідна, чай", "Домашний уют, мягкая, чай", "Cozy peace, gentle, brews tea") + ")"
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        builder.setTitle(MiogramLocale.get("Оберіть ШІ-Супутницю", "Выберите ИИ-Спутницу", "Select AI Companion"));
        builder.setItems(companions, (dialog, which) -> {
            MiogramCompanionPrefs.setActiveCompanion(which == 0 ? MiogramCompanionPrefs.COMPANION_YUMI : MiogramCompanionPrefs.COMPANION_MIO);
            rebuildRowsAndNotify();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void showProviderSelector() {
        Context context = getParentActivity();
        if (context == null) return;
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);

        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle(getString(R.string.OEAiProvider));
        builder.setView(list);
        builder.setNegativeButton(getString(R.string.Cancel), null);
        AlertDialog dialog = builder.create();

        Provider current = Provider.matching(service().getUrl());
        for (Provider provider : Provider.PRESETS) {
            list.addView(providerRow(context, provider.getTitle(), provider.getIconUrl(),
                    provider == current, v -> {
                        dialog.dismiss();
                        Service service = service();
                        service.setUrl(provider.getUrl());
                        service.setModel(provider.getModel());
                        store(service);
                    }), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));
        }
        list.addView(providerRow(context, getString(R.string.OEAiProviderCustom), null,
                current == null, v -> {
                    dialog.dismiss();
                    showInput(getString(R.string.OEAiUrl), service().getUrl(),
                            InputType.TYPE_TEXT_VARIATION_URI, value -> {
                                Service service = service();
                                service.setUrl(value);
                                store(service);
                            });
                }), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));

        showDialog(dialog);
    }

    private View providerRow(Context context, String title, String iconUrl, boolean selected, View.OnClickListener listener) {
        return optionRow(context, title, null, iconUrl, selected, listener, null);
    }

    private View optionRow(Context context, String title, String subtitle, String iconUrl,
                           boolean selected, View.OnClickListener listener, View.OnClickListener onDelete) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(22), 0, dp(onDelete == null ? 22 : 8), 0);
        row.setBackground(Theme.getSelectorDrawable(false));
        row.setOnClickListener(listener);

        if (TextUtils.isEmpty(iconUrl)) {
            TextView letter = new TextView(context);
            letter.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            letter.setTypeface(AndroidUtilities.bold());
            letter.setGravity(Gravity.CENTER);
            letter.setTextColor(getThemedColor(Theme.key_dialogTextBlue));
            letter.setBackground(Theme.createRoundRectDrawable(dp(9), Theme.multAlpha(
                    getThemedColor(Theme.key_dialogTextBlue), 0.12f)));
            letter.setText(TextUtils.isEmpty(title) ? "?" : title.substring(0, 1).toUpperCase());
            row.addView(letter, LayoutHelper.createLinear(32, 32, Gravity.CENTER_VERTICAL, 0, 0, 14, 0));
        } else {
            BackupImageView image = new BackupImageView(context);
            image.setRoundRadius(dp(9));
            image.setImage(ImageLocation.getForPath(iconUrl), "60_60", null, null, null);
            row.addView(image, LayoutHelper.createLinear(32, 32, Gravity.CENTER_VERTICAL, 0, 0, 14, 0));
        }

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);

        TextView name = new TextView(context);
        name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        name.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setText(title);
        texts.addView(name, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        if (!TextUtils.isEmpty(subtitle)) {
            TextView hint = new TextView(context);
            hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            hint.setTextColor(getThemedColor(Theme.key_dialogTextGray2));
            hint.setSingleLine(true);
            hint.setEllipsize(TextUtils.TruncateAt.END);
            hint.setText(subtitle);
            texts.addView(hint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 1, 0, 0));
        }

        row.addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL));

        if (selected) {
            ImageView check = new ImageView(context);
            check.setImageResource(R.drawable.msg_check);
            check.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_dialogTextBlue), PorterDuff.Mode.SRC_IN));
            row.addView(check, LayoutHelper.createLinear(24, 24, Gravity.CENTER_VERTICAL, 12, 0, 0, 0));
        }
        if (onDelete != null) {
            ImageView delete = new ImageView(context);
            delete.setScaleType(ImageView.ScaleType.CENTER);
            delete.setImageResource(R.drawable.msg_delete);
            delete.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_dialogTextGray2), PorterDuff.Mode.SRC_IN));
            delete.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_dialogButtonSelector), 1));
            delete.setOnClickListener(onDelete);
            row.addView(delete, LayoutHelper.createLinear(40, 40, Gravity.CENTER_VERTICAL, 4, 0, 0, 0));
        }
        return row;
    }

    private void showModelPicker() {
        Context context = getParentActivity();
        if (context == null) return;
        Service current = service();
        if (AiConfig.isGeminiService(current)) {
            String[] models = {
                    "gemini-3.5-flash-lite (" + MiogramLocale.get("За замовчуванням, 10x ліміти, швидка", "По умолчанию, 10x лимиты, быстрая", "Default, 10x limits, fast") + ")",
                    "gemini-3.8-flash (" + MiogramLocale.get("Флагман 2026, міркування та код", "Флагман 2026, рассуждения и код", "Frontier 2026 reasoning & code") + ")",
                    "gemini-3.5-flash (" + MiogramLocale.get("Збалансована модель 2026", "Сбалансированная модель 2026", "Balanced 2026 Flash") + ")",
                    "local-litert (" + MiogramLocale.get("Локальний ШІ: LiteRT-LM / On-Device", "Локальный ИИ: LiteRT-LM / On-Device", "Local AI: LiteRT-LM / On-Device") + ")",
                    MiogramLocale.get("Огляд каталогу моделей…", "Обзор каталога моделей…", "Browse models catalog…")
            };
            String[] modelKeys = {"gemini-3.5-flash-lite", "gemini-3.8-flash", "gemini-3.5-flash", "local-litert", "catalog"};

            AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
            builder.setTitle(MiogramLocale.get("Модель Yumi AI", "Модель Yumi AI", "Yumi AI Model"));
            builder.setItems(models, (dialog, which) -> {
                if ("catalog".equals(modelKeys[which])) {
                    AiModelPicker.show(getParentActivity(), resourcesProvider, current.getModel(), this::saveModel);
                } else {
                    saveModel(modelKeys[which]);
                }
            });
            builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
            showDialog(builder.create());
        } else {
            AiModelPicker.show(getParentActivity(), resourcesProvider, current.getModel(), this::saveModel);
        }
    }

    private void showPresets() {
        Context context = getParentActivity();
        if (context == null) return;
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);

        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle(getString(R.string.OEAiPresets));
        builder.makeCustomMaxHeight();
        builder.setView(scrollable(context, list));
        builder.setNegativeButton(getString(R.string.Cancel), null);
        AlertDialog dialog = builder.create();

        String currentId = service().getId();
        for (Service preset : AiConfig.getServices()) {
            Provider provider = Provider.matching(preset.getUrl());
            boolean selected = TextUtils.equals(preset.getId(), currentId);
            list.addView(optionRow(context, preset.getDisplayName(), describe(preset),
                    provider == null ? null : provider.getIconUrl(), selected, v -> {
                        dialog.dismiss();
                        AiConfig.setSelectedServices(preset);
                        testStatus = null;
                        testFailed = false;
                        rebuildRowsAndNotify();
                    }, selected ? null : v -> {
                        dialog.dismiss();
                        AiController.removeService(preset);
                        rebuildRowsAndNotify();
                    }), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 52));
        }
        list.addView(optionRow(context, getString(R.string.OEAiPresetSave), null, null, false,
                v -> {
                    dialog.dismiss();
                    savePresetAs();
                }, null), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 52));

        showDialog(dialog);
    }

    private void savePresetAs() {
        showInput(getString(R.string.OEAiPresetName), service().getDisplayName(), InputType.TYPE_CLASS_TEXT, value -> {
            if (TextUtils.isEmpty(value)) return;
            Service preset = service().copy();
            preset.setName(value);
            AiController.saveService(preset);
            rebuildRowsAndNotify();
        });
    }

    private ScrollView scrollable(Context context, View content) {
        ScrollView scrollView = new ScrollView(context);
        scrollView.addView(content, new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        return scrollView;
    }

    private void showReasoningSelector() {
        Context context = getParentActivity();
        if (context == null) return;
        CharSequence[] items = new CharSequence[]{
                getString(R.string.OEAiReasoningOff),
                getString(R.string.OEAiReasoningLow),
                getString(R.string.OEAiReasoningMedium),
                getString(R.string.OEAiReasoningHigh)};
        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle(getString(R.string.OEAiReasoning));
        builder.setItems(items, (dialog, which) -> {
            Service service = service();
            service.setReasoningEffort(which);
            store(service);
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void showKeyDialog() {
        Context ctx = getParentActivity();
        if (ctx == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        builder.setTitle(MiogramLocale.get("Ключі API", "Ключи API", "API Keys"));

        EditTextBoldCursor input = new EditTextBoldCursor(ctx);
        input.setText(savedKeysText());
        input.setHint(MiogramLocale.get("Введіть ключ(і) по одному на рядок…", "Введите ключ(и) по одному в строке…", "Enter key(s) one per line…"));
        input.setSingleLine(false);
        input.setMinLines(3);
        input.setMaxLines(6);
        input.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        input.setHintColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        input.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(12), AndroidUtilities.dp(24), AndroidUtilities.dp(12));

        builder.setView(input);
        builder.setPositiveButton(LocaleController.getString(R.string.Save), (dialog, which) -> {
            saveKeys(input.getText().toString());
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.setNeutralButton(MiogramLocale.get("Очистити", "Очистить", "Clear"), (dialog, which) -> {
            saveKeys("");
        });
        showDialog(builder.create());
    }

    private void testConnection() {
        if (testRequestId != null) return;
        if (!AiController.canUseAI()) {
            BulletinFactory.of(this).createErrorBulletin(getString(R.string.OEAiNotConfigured)).show();
            return;
        }
        testClient = new Client.Builder().build();
        testRequestId = UUID.randomUUID().toString();
        testFailed = false;
        testStatus = null;
        notifyRow(testRow);
        ArrayList<Message> messages = new ArrayList<>();
        messages.add(new Message("user", "ping"));
        testClient.generate(testRequestId, messages, new GenerationCallback() {
            @Override
            public void onChunk(String chunk) {}

            @Override
            public void onResponse(String response) {
                testRequestId = null;
                testFailed = false;
                testStatus = getString(R.string.OEAiTestOk);
                notifyRow(testRow);
            }

            @Override
            public void onError(int code, String message) {
                testRequestId = null;
                testFailed = true;
                testStatus = code == 0 ? getString(R.string.OEAiFailed) : getString(R.string.OEAiFailed) + " · " + code;
                notifyRow(testRow);
            }

            @Override
            public void onThinking() {}
        });
    }

    private void showTranscribeProviderPicker() {
        Context ctx = getParentActivity();
        if (ctx == null) return;
        String[] labels = AmegramTranscriptionPrefs.getProviderLabels();
        int[] values = AmegramTranscriptionPrefs.getProviderValues();

        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        builder.setTitle(MiogramLocale.get("Розпізнавання голосу", "Распознавание голоса", "Voice Transcription Engine"));
        builder.setItems(labels, (dialog, which) -> {
            AmegramTranscriptionPrefs.setProvider(values[which]);
            notifyRow(voiceTranscribeInfoRow);
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void showRoleSelector() {
        Context context = getParentActivity();
        if (context == null) return;
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);

        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle(getString(R.string.OEAiRole));
        builder.makeCustomMaxHeight();
        builder.setView(scrollable(context, list));
        builder.setNegativeButton(getString(R.string.Cancel), null);
        AlertDialog dialog = builder.create();

        String selected = AiController.getSelectedRole().getName();
        for (Role role : AiController.getAllRoles()) {
            boolean current = TextUtils.equals(role.getName(), selected);
            list.addView(optionRow(context, role.getName(), role.getPrompt(), null, current,
                    v -> {
                        dialog.dismiss();
                        AiConfig.setSelectedAiRole(role);
                        notifyRow(roleRow);
                    }, role.isSuggestion() ? null : v -> {
                        dialog.dismiss();
                        AiController.removeRole(role);
                        notifyRow(roleRow);
                    }), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 56));
        }
        list.addView(optionRow(context, getString(R.string.OEAiRoleAdd), null, null, false,
                v -> {
                    dialog.dismiss();
                    showRoleEditor();
                }, null), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 56));

        showDialog(dialog);
    }

    private void showRoleEditor() {
        Context context = getParentActivity();
        if (context == null) return;
        EditTextBoldCursor name = editField(context, getString(R.string.OEAiRoleName), null, true);
        EditTextBoldCursor prompt = editField(context, getString(R.string.OEAiRolePrompt), null, false);

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.addView(name, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24f, 0f, 24f, 12f));
        container.addView(prompt, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24f, 0f, 24f, 10f));

        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle(getString(R.string.OEAiRoleAdd));
        builder.makeCustomMaxHeight();
        builder.setView(container);
        builder.setPositiveButton(getString(R.string.Done), (dialog, which) -> {
            String title = name.getText().toString().trim();
            String text = prompt.getText().toString().trim();
            if (TextUtils.isEmpty(title) || TextUtils.isEmpty(text)) return;
            Role role = new Role(title, text);
            if (AiController.addRole(role)) {
                AiConfig.setSelectedAiRole(role);
                notifyRow(roleRow);
            }
            AndroidUtilities.hideKeyboard(name);
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> AndroidUtilities.runOnUIThread(() -> {
            name.requestFocus();
            AndroidUtilities.showKeyboard(name);
        }, 60));
        showDialog(dialog);
    }

    private EditTextBoldCursor editField(Context context, String hint, String value, boolean singleLine) {
        EditTextBoldCursor editText = new EditTextBoldCursor(context);
        editText.lineYFix = true;
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        editText.setText(value == null ? "" : value);
        editText.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        editText.setHintColor(getThemedColor(Theme.key_groupcreate_hintText));
        editText.setHintText(hint);
        editText.setSingleLine(singleLine);
        if (!singleLine) {
            editText.setMaxLines(4);
            editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        }
        editText.setBackground(null);
        editText.setLineColors(getThemedColor(Theme.key_windowBackgroundWhiteInputField),
                getThemedColor(Theme.key_windowBackgroundWhiteInputFieldActivated),
                getThemedColor(Theme.key_text_RedRegular));
        editText.setCursorColor(getThemedColor(Theme.key_windowBackgroundWhiteInputFieldActivated));
        editText.setPadding(0, dp(6), 0, dp(6));
        return editText;
    }

    private void showInput(String title, String current, int inputType, Consumer<String> onDone) {
        Context context = getParentActivity();
        if (context == null) return;
        EditTextBoldCursor editText = new EditTextBoldCursor(context);
        editText.lineYFix = true;
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setText(current == null ? "" : current);
        editText.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        editText.setHintColor(getThemedColor(Theme.key_groupcreate_hintText));
        editText.setHintText(title);
        editText.setFocusable(true);
        editText.setSingleLine(true);
        editText.setInputType(inputType);
        editText.setBackground(null);
        editText.setLineColors(getThemedColor(Theme.key_windowBackgroundWhiteInputField),
                getThemedColor(Theme.key_windowBackgroundWhiteInputFieldActivated),
                getThemedColor(Theme.key_text_RedRegular));
        editText.setCursorColor(getThemedColor(Theme.key_windowBackgroundWhiteInputFieldActivated));
        editText.setPadding(0, dp(6), 0, dp(6));

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24f, 0f, 24f, 10f));

        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle(title);
        builder.makeCustomMaxHeight();
        builder.setView(container);
        builder.setWidth(dp(292));
        builder.setPositiveButton(getString(R.string.Done), (dialog, which) -> {
            String value = editText.getText() == null ? "" : editText.getText().toString().trim();
            onDone.accept(value);
            dialog.dismiss();
        });
        builder.setNegativeButton(getString(R.string.Cancel), (dialog, which) -> dialog.dismiss());
        showDialog(builder.create());
    }

    private class TemperatureCell extends FrameLayout {

        private final SeekBarView seekBar;
        private final TextView valueView;

        public TemperatureCell(Context context) {
            super(context);
            setWillNotDraw(false);

            LinearLayout header = new LinearLayout(context);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);

            TextView title = new TextView(context);
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            title.setTypeface(AndroidUtilities.bold());
            title.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueHeader));
            title.setText(getString(R.string.OEAiTemperature));
            header.addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

            valueView = new TextView(context);
            valueView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            valueView.setTypeface(AndroidUtilities.bold());
            valueView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueHeader));
            valueView.setPadding(dp(5.33f), dp(2), dp(5.33f), dp(2));
            valueView.setBackground(Theme.createRoundRectDrawable(dp(4),
                    Theme.multAlpha(getThemedColor(Theme.key_windowBackgroundWhiteBlueHeader), 0.15f)));
            header.addView(valueView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 6, 1, 0, 0));

            addView(header, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 21, 15, 21, 0));

            FrameLayout edges = new FrameLayout(context);
            TextView left = new TextView(context);
            left.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            left.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
            left.setText(getString(R.string.OEAiTemperatureExact));
            edges.addView(left, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL));
            TextView right = new TextView(context);
            right.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            right.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
            right.setText(getString(R.string.OEAiTemperatureVaried));
            edges.addView(right, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL));
            addView(edges, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 21, 48, 21, 0));

            seekBar = new SeekBarView(context);
            seekBar.setReportChanges(true);
            seekBar.setSeparatorsCount(MAX_TEMPERATURE + 1);
            seekBar.setDelegate((stop, progress) -> {
                AiConfig.setTemperature(Math.round(progress * MAX_TEMPERATURE));
                updateValue();
            });
            addView(seekBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, Gravity.LEFT | Gravity.TOP, 9, 72, 9, 0));

            updateValue();
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                float y = event.getY();
                if (y >= seekBar.getTop() - dp(8) && y <= seekBar.getBottom() + dp(8)) {
                    sliderTouched = true;
                    ViewParent parent = getParent();
                    if (parent != null) {
                        parent.requestDisallowInterceptTouchEvent(true);
                    }
                }
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                sliderTouched = false;
            }
            return super.dispatchTouchEvent(event);
        }

        private void updateValue() {
            valueView.setText(String.format(Locale.US, "%.1f", AiConfig.getTemperature() / 10f));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(dp(118), MeasureSpec.EXACTLY));
            seekBar.setProgress(AiConfig.getTemperature() / (float) MAX_TEMPERATURE);
            updateValue();
        }
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == headerCompanionRow || position == headerProviderRow || position == headerKeysRow
                    || position == headerFeaturesRow || position == headerGenerationRow) {
                return TYPE_HEADER;
            } else if (position == companionChoiceRow || position == providerRow || position == modelRow
                    || position == urlRow || position == presetsRow || position == reasoningRow
                    || position == hardwareRow || position == keyRow || position == testRow
                    || position == voiceTranscribeInfoRow || position == roleRow) {
                return TYPE_SETTINGS;
            } else if (position == openCompanionRow || position == getKeyRow || position == clearHistoryRow) {
                return TYPE_TEXT;
            } else if (position == companionBottomTabRow || position == transcribePreferForPremiumRow
                    || position == piiMaskRow || position == streamingRow || position == saveHistoryRow) {
                return TYPE_CHECK;
            } else if (position == companionInfoRow || position == hardwareInfoRow || position == keysInfoRow
                    || position == featuresInfoRow || position == generationInfoRow) {
                return TYPE_INFO_PRIVACY;
            } else if (position == temperatureRow) {
                return TYPE_TEMPERATURE;
            }
            return TYPE_SETTINGS;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            if (viewType == TYPE_TEMPERATURE) {
                temperatureCell = new TemperatureCell(mContext);
                temperatureCell.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                temperatureCell.setLayoutParams(new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return new RecyclerListView.Holder(temperatureCell);
            }
            return super.onCreateViewHolder(parent, viewType);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            if (holder.getItemViewType() == TYPE_TEMPERATURE) {
                return false;
            }
            return super.isEnabled(holder);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean partial) {
            switch (holder.getItemViewType()) {
                case TYPE_HEADER: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    if (position == headerCompanionRow) {
                        cell.setText(MiogramLocale.get("ШІ-Супутниці (Юмі & Міо)", "ИИ-Спутницы (Юми & Мио)", "AI Companions (Yumi & Mio)"));
                    } else if (position == headerProviderRow) {
                        cell.setText(MiogramLocale.get("Провайдер та рушій ШІ", "Провайдер и движок ИИ", "AI Provider & Engine"));
                    } else if (position == headerKeysRow) {
                        cell.setText(MiogramLocale.get("Ключі API та перевірка", "Ключи API и проверка", "API Keys & Verification"));
                    } else if (position == headerFeaturesRow) {
                        cell.setText(MiogramLocale.get("Можливості та приватність", "Возможности и приватность", "Features & Privacy"));
                    } else if (position == headerGenerationRow) {
                        cell.setText(getString(R.string.OEAiGenerationHeader));
                    }
                    break;
                }
                case TYPE_SETTINGS: {
                    TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                    cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    cell.setTextValueColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText));
                    cell.setDrawLoading(false, 20, false);

                    if (position == companionChoiceRow) {
                        boolean isYumi = MiogramCompanionPrefs.isYumiActive();
                        String companionName = isYumi ? "Юмі ໒꒱" : "Міо ☕";
                        cell.setTextAndValue(MiogramLocale.get("Активна супутниця", "Активная спутница", "Active Companion"), companionName, true);
                    } else if (position == providerRow) {
                        cell.setTextAndValue(getString(R.string.OEAiProvider), providerTitle(), true);
                    } else if (position == modelRow) {
                        cell.setTextAndValue(getString(R.string.OEAiModel), modelTitle(), true);
                    } else if (position == urlRow) {
                        cell.setTextAndValue(getString(R.string.OEAiUrl), service().getUrl(), true);
                    } else if (position == presetsRow) {
                        cell.setTextAndValue(getString(R.string.OEAiPresets), describe(service()), true);
                    } else if (position == reasoningRow) {
                        cell.setTextAndValue(getString(R.string.OEAiReasoning), reasoningTitle(), true);
                    } else if (position == hardwareRow) {
                        MiogramAiService.DeviceHardwareInfo hw = MiogramAiService.getDeviceHardwareInfo();
                        String nanoSuffix = "";
                        try {
                            if (app.amegram.hot.HotAiGate.isGeminiNano()) {
                                nanoSuffix = " • Nano " + (app.amegram.hot.HotAiGate.isNanoDownloading()
                                        ? app.amegram.hot.HotAiGate.getNanoDownloadProgress() + "%" : "Active");
                            }
                        } catch (Throwable ignore) {}
                        cell.setTextAndValue(
                                MiogramLocale.get("Оптимізація під пристрій", "Оптимизация под устройство", "Device Optimization"),
                                hw.deviceSummary + nanoSuffix,
                                false
                        );
                    } else if (position == keyRow) {
                        cell.setTextAndValue(MiogramLocale.get("API Ключі", "API Ключи", "API Keys"), keySummary(), true);
                    } else if (position == testRow) {
                        cell.setTextAndValue(getString(R.string.OEAiTest), testStatus == null ? "" : testStatus, false);
                        if (testStatus != null) {
                            cell.setTextValueColor(getThemedColor(testFailed ? Theme.key_text_RedRegular : Theme.key_windowBackgroundWhiteGreenText));
                        }
                        cell.setDrawLoading(testRequestId != null, 20, true);
                    } else if (position == voiceTranscribeInfoRow) {
                        cell.setTextAndValue(MiogramLocale.get("Розпізнавання голосу (STT)", "Распознавание голоса (STT)", "Voice Transcription (STT)"),
                                AmegramTranscriptionPrefs.getProviderTitle(AmegramTranscriptionPrefs.getProvider()), true);
                    } else if (position == roleRow) {
                        cell.setTextAndValue(getString(R.string.OEAiRole), AiController.getSelectedRole().getName(), true);
                    }
                    break;
                }
                case TYPE_TEXT: {
                    TextCell cell = (TextCell) holder.itemView;
                    if (position == openCompanionRow) {
                        cell.setTextAndIcon(MiogramLocale.get("Відкрити чат зі супутником", "Открыть чат со спутником", "Open Companion Chat"), R.drawable.baseline_stars_24, true);
                    } else if (position == getKeyRow) {
                        cell.setTextAndIcon(MiogramLocale.get("Отримати безкоштовний ключ на Google AI Studio", "Получить бесплатный ключ на Google AI Studio", "Get free API key on Google AI Studio"), R.drawable.msg_bot, true);
                    } else if (position == clearHistoryRow) {
                        cell.setText(getString(R.string.OEAiClearHistory), false);
                        cell.setTextColor(getThemedColor(Theme.key_text_RedRegular));
                    }
                    break;
                }
                case TYPE_CHECK: {
                    TextCheckCell cell = (TextCheckCell) holder.itemView;
                    if (position == companionBottomTabRow) {
                        cell.setTextAndCheck(MiogramLocale.get("Замінити вкладку «Контакти» на ШІ", "Заменить вкладку «Контакты» на ИИ", "Replace «Contacts» tab with AI"),
                                MiogramCompanionPrefs.isContactsReplacedWithAi(), false);
                    } else if (position == transcribePreferForPremiumRow) {
                        cell.setTextAndCheck(MiogramLocale.get("Надавати перевагу Yumi для TG Premium", "Предпочитать Yumi для TG Premium", "Prefer Yumi over TG Premium"),
                                AmegramTranscriptionPrefs.isPreferAmegramForPremium(), true);
                    } else if (position == piiMaskRow) {
                        cell.setTextAndCheck(MiogramLocale.get("Приховувати персональні дані (PII Shield)", "Скрывать личные данные (PII Shield)", "Protect Personal Data (PII Shield)"),
                                piiMaskEnabled(), false);
                    } else if (position == streamingRow) {
                        cell.setTextAndCheck(getString(R.string.OEAiStreaming), AiConfig.getResponseStreaming(), true);
                    } else if (position == saveHistoryRow) {
                        cell.setTextAndCheck(getString(R.string.OEAiSaveHistory), AiConfig.getSaveHistory(), true);
                    }
                    break;
                }
                case TYPE_INFO_PRIVACY: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == companionInfoRow) {
                        cell.setText(MiogramLocale.get(
                                "Персоналізовані ШІ-супутниці з власним стилем спілкування та пам'яттю діалогу.",
                                "Персонализированные ИИ-спутницы с собственным стилем общения и памятью диалога.",
                                "Personalized AI companions with unique personality styles and conversation memory."
                        ));
                    } else if (position == hardwareInfoRow) {
                        MiogramAiService.DeviceHardwareInfo hw = MiogramAiService.getDeviceHardwareInfo();
                        cell.setText(hw.recommendationReason);
                    } else if (position == keysInfoRow) {
                        cell.setText(MiogramLocale.get(
                                "Ключі зберігаються локально на пристрої та використовуються для розпізнавання голосу, скорочення повідомлень та чат-асистента. Підтримується кілька ключів з автоматичною ротацією.",
                                "Ключи хранятся локально на устройстве и используются для распознавания голоса, саммари сообщений и чат-ассистента. Поддерживается несколько ключей с авто-ротацией.",
                                "Keys are stored locally and used across voice transcription, message summaries, and in-chat AI. Multiple keys with automatic rotation are supported."
                        ));
                    } else if (position == featuresInfoRow) {
                        cell.setText(MiogramLocale.get(
                                "PII Shield автоматично маскує номери телефонів, email та особисті адреси перед відправкою запитів до сторонніх хмарних моделей.",
                                "PII Shield автоматически маскирует номера телефонов, email и личные адреса перед отправкой запросов во внешние облачные модели.",
                                "PII Shield automatically redacts phone numbers, email addresses, and personal credentials before sending requests to cloud LLMs."
                        ));
                    } else if (position == generationInfoRow) {
                        cell.setText(MiogramLocale.get(
                                "Налаштуйте поведінку ШІ, креативність відповідей та параметри збереження історії діалогів.",
                                "Настройте поведение ИИ, креативность ответов и параметры сохранения истории диалогов.",
                                "Customize AI behavior, response creativity, and dialog history retention."
                        ));
                    }
                    break;
                }
            }
        }
    }
}
