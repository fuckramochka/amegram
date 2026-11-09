package com.amegram.mods.docviewer;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import app.amegram.hot.api.HotHook;
import app.amegram.hot.api.HotHost;
import app.amegram.hot.api.HotModule;
import app.amegram.hot.api.HotRow;

public class DocViewerModule implements HotModule {

    private HotHost host;
    private List<HotHook.Unhook> unhooks = new ArrayList<>();
    private Dialog currentDialog;

    public DocViewerModule() {
    }

    @Override
    public String moduleId() {
        return "docviewer";
    }

    @Override
    public void onAttach(Context appContext, HotHost host) {
        this.host = host;
        try {
            Class<?> au = Class.forName("org.telegram.messenger.AndroidUtilities");
            unhooks = host.hookAll(au, "openForView", new HotHook.Callback() {
                @Override
                public void before(HotHook.Call call) throws Throwable {
                    if (DocViewerModule.this.host == null || !DocViewerModule.this.host.getBool("enabled", true)) {
                        return;
                    }
                    Object[] args = call.getArgs();
                    if (args == null || args.length == 0 || args[0] == null) {
                        return;
                    }
                    if (handleOpenFile(args)) {
                        call.setResult(true);
                    }
                }
            });
            host.log("docviewer module attached, hooked openForView");
        } catch (Throwable t) {
            host.log("docviewer hook error: " + t.getMessage());
        }
    }

    @Override
    public void onDetach() {
        if (unhooks != null) {
            for (HotHook.Unhook u : unhooks) {
                try {
                    u.unhook();
                } catch (Throwable ignore) {
                }
            }
            unhooks.clear();
        }
        if (currentDialog != null) {
            try {
                currentDialog.dismiss();
            } catch (Throwable ignore) {
            }
            currentDialog = null;
        }
        host = null;
    }

    private static String t(String uk, String ru, String en) {
        try {
            Class<?> locale = Class.forName("app.miogram.bridge.MiogramLocale");
            Method get = locale.getMethod("get", String.class, String.class, String.class);
            Object value = get.invoke(null, uk, ru, en);
            if (value instanceof String) return (String) value;
        } catch (Throwable ignore) {
        }
        return uk != null ? uk : (en != null ? en : "");
    }

    @Override
    public boolean hasSettings() {
        return true;
    }

    @Override
    public String settingsTitle() {
        return t("Doc Viewer Pro", "Doc Viewer Pro", "Doc Viewer Pro");
    }

    @Override
    public void fillSettings(List<HotRow> rows) {
        boolean enabled = host != null && host.getBool("enabled", true);
        boolean lineNumbers = host != null && host.getBool("line_numbers", false);
        boolean keepScreen = host != null && host.getBool("keep_screen_on", true);

        rows.add(HotRow.header(t("Перегляд документів", "Просмотр документов", "Document Viewer")));
        rows.add(HotRow.switchRow("enabled",
                t("Відкривати файли в клієнті", "Открывать файлы в клиенте", "Open files inside client"),
                t("Перегляд PDF, коду, архівів та офісних файлів", "Просмотр PDF, кода, архивов и офиса", "Preview PDF, code, archives & office"),
                enabled));
        rows.add(HotRow.switchRow("line_numbers",
                t("Номери рядків у коді", "Номера строк в коде", "Line numbers in code"),
                "",
                lineNumbers));
        rows.add(HotRow.switchRow("keep_screen_on",
                t("Не гасити екран при читанні", "Не гасить экран при чтении", "Keep screen on when reading"),
                "",
                keepScreen));
    }

    @Override
    public void onSettingsToggle(String key, boolean value) {
        if (host != null) {
            host.putBool(key, value);
        }
    }

    private boolean handleOpenFile(Object[] args) {
        try {
            File file = null;
            String fileName = null;

            Object first = args[0];
            String className = first.getClass().getName();

            if (first instanceof File) {
                file = (File) first;
                if (args.length > 1 && args[1] instanceof String) {
                    fileName = (String) args[1];
                }
            } else if (className.endsWith("MessageObject")) {
                Method getFile = first.getClass().getMethod("getDocument");
                Object doc = getFile.invoke(first);
                if (doc != null) {
                    Class<?> fl = Class.forName("org.telegram.messenger.FileLoader");
                    fileName = (String) fl.getMethod("getDocumentFileName", doc.getClass()).invoke(null, doc);
                    int currentAccount = first.getClass().getField("currentAccount").getInt(first);
                    Object messageOwner = first.getClass().getField("messageOwner").get(first);
                    Object flInst = fl.getMethod("getInstance", int.class).invoke(null, currentAccount);
                    file = (File) fl.getMethod("getPathToMessage", messageOwner.getClass()).invoke(flInst, messageOwner);
                }
            }

            if (file == null || !file.exists() || file.length() == 0) {
                return false;
            }
            if (fileName == null || fileName.isEmpty()) {
                fileName = file.getName();
            }

            String ext = "";
            int dot = fileName.lastIndexOf('.');
            if (dot >= 0) {
                ext = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
            }

            if (isSupportedFormat(ext)) {
                final File finalFile = file;
                final String finalName = fileName;
                final String finalExt = ext;
                new Handler(Looper.getMainLooper()).post(() -> launchViewer(finalFile, finalName, finalExt));
                return true;
            }
        } catch (Throwable t) {
            if (host != null) {
                host.log("handleOpenFile error: " + t.getMessage());
            }
        }
        return false;
    }

    private boolean isSupportedFormat(String ext) {
        return "pdf".equals(ext) || "zip".equals(ext) || "tar".equals(ext) || "gz".equals(ext)
                || "txt".equals(ext) || "log".equals(ext) || "json".equals(ext) || "csv".equals(ext)
                || "xml".equals(ext) || "py".equals(ext) || "java".equals(ext) || "kt".equals(ext)
                || "c".equals(ext) || "cpp".equals(ext) || "h".equals(ext) || "js".equals(ext)
                || "ts".equals(ext) || "html".equals(ext) || "md".equals(ext) || "css".equals(ext)
                || "sql".equals(ext) || "sh".equals(ext) || "gradle".equals(ext) || "docx".equals(ext)
                || "xlsx".equals(ext) || "pptx".equals(ext) || "epub".equals(ext) || "fb2".equals(ext);
    }

    private void launchViewer(File file, String name, String ext) {
        try {
            if (currentDialog != null) {
                try {
                    currentDialog.dismiss();
                } catch (Throwable ignore) {
                }
                currentDialog = null;
            }

            Class<?> laClass = Class.forName("org.telegram.ui.LaunchActivity");
            Activity act = (Activity) laClass.getField("instance").get(null);
            if (act == null || act.isFinishing()) return;

            FrameLayout root = new FrameLayout(act);
            root.setBackgroundColor(Color.parseColor("#18181e"));

            if ("zip".equals(ext)) {
                buildArchiveView(act, root, file);
            } else if ("pdf".equals(ext)) {
                buildPdfView(act, root, file);
            } else {
                buildTextView(act, root, file, ext);
            }

            // Top Header Bar
            LinearLayout topBar = new LinearLayout(act);
            topBar.setOrientation(LinearLayout.HORIZONTAL);
            topBar.setGravity(Gravity.CENTER_VERTICAL);
            topBar.setBackgroundColor(Color.parseColor("#E6202028"));
            topBar.setPadding(30, 20, 30, 20);

            TextView btnBack = new TextView(act);
            btnBack.setText("←");
            btnBack.setTextSize(22f);
            btnBack.setTextColor(Color.WHITE);
            btnBack.setGravity(Gravity.CENTER);
            btnBack.setPadding(10, 10, 30, 10);
            topBar.addView(btnBack);

            LinearLayout titleBox = new LinearLayout(act);
            titleBox.setOrientation(LinearLayout.VERTICAL);
            TextView tName = new TextView(act);
            tName.setText(name);
            tName.setTextColor(Color.WHITE);
            tName.setTextSize(16f);
            tName.setSingleLine(true);
            titleBox.addView(tName);

            TextView tSub = new TextView(act);
            tSub.setText(humanSize(file.length()) + " · " + ext.toUpperCase(Locale.ROOT));
            tSub.setTextColor(Color.parseColor("#8E8E93"));
            tSub.setTextSize(12f);
            titleBox.addView(tSub);

            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            topBar.addView(titleBox, tlp);

            FrameLayout.LayoutParams fblp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            fblp.gravity = Gravity.TOP;
            root.addView(topBar, fblp);

            final Dialog dialog = new Dialog(act, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
            dialog.setContentView(root);
            currentDialog = dialog;

            Window win = dialog.getWindow();
            if (win != null) {
                win.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                if (host != null && host.getBool("keep_screen_on", true)) {
                    win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
            }

            btnBack.setOnClickListener(v -> dialog.dismiss());
            dialog.setOnDismissListener(d -> currentDialog = null);
            dialog.show();
        } catch (Throwable t) {
            if (host != null) {
                host.log("launchViewer failed: " + t.getMessage());
            }
        }
    }

    private void buildTextView(Activity act, FrameLayout root, File file, String ext) {
        ScrollView sv = new ScrollView(act);
        TextView tv = new TextView(act);
        tv.setTextColor(Color.parseColor("#EEEEF0"));
        tv.setTextSize(15f);
        tv.setPadding(35, 140, 35, 60);
        tv.setTextIsSelectable(true);

        StringBuilder sb = new StringBuilder();
        boolean lineNumbers = host != null && host.getBool("line_numbers", false);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            String line;
            int count = 1;
            while ((line = reader.readLine()) != null && count <= 5000) {
                if (lineNumbers) {
                    sb.append(String.format(Locale.ROOT, "%4d │ ", count));
                }
                sb.append(line).append("\n");
                count++;
            }
        } catch (Throwable t) {
            sb.append("Помилка читання файлу: ").append(t.getMessage());
        }

        tv.setText(sb.toString());
        sv.addView(tv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void buildArchiveView(Activity act, FrameLayout root, File file) {
        ScrollView sv = new ScrollView(act);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(35, 140, 35, 60);

        try (ZipFile zf = new ZipFile(file)) {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            int count = 0;
            while (entries.hasMoreElements() && count < 500) {
                ZipEntry entry = entries.nextElement();
                TextView item = new TextView(act);
                String prefix = entry.isDirectory() ? "📁 " : "📄 ";
                item.setText(prefix + entry.getName() + " (" + humanSize(entry.getSize()) + ")");
                item.setTextColor(entry.isDirectory() ? Color.parseColor("#4DA3FF") : Color.WHITE);
                item.setTextSize(14.5f);
                item.setPadding(0, 15, 0, 15);
                box.addView(item);
                count++;
            }
        } catch (Throwable t) {
            TextView err = new TextView(act);
            err.setText("Помилка читання архіву: " + t.getMessage());
            err.setTextColor(Color.RED);
            box.addView(err);
        }

        sv.addView(box, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void buildPdfView(Activity act, FrameLayout root, File file) {
        try {
            ParcelFileDescriptor pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            PdfRenderer renderer = new PdfRenderer(pfd);
            int pageCount = renderer.getPageCount();

            ScrollView sv = new ScrollView(act);
            LinearLayout box = new LinearLayout(act);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(20, 140, 20, 60);

            // Render first 10 pages into ImageViews
            int limit = Math.min(pageCount, 15);
            for (int i = 0; i < limit; i++) {
                PdfRenderer.Page page = renderer.openPage(i);
                int w = act.getResources().getDisplayMetrics().widthPixels;
                int h = (int) ((float) page.getHeight() / page.getWidth() * w);
                android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
                bmp.eraseColor(Color.WHITE);
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                page.close();

                android.widget.ImageView iv = new android.widget.ImageView(act);
                iv.setImageBitmap(bmp);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMargins(0, 0, 0, 20);
                box.addView(iv, lp);
            }

            renderer.close();
            pfd.close();

            sv.addView(box, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            root.addView(sv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } catch (Throwable t) {
            TextView tv = new TextView(act);
            tv.setText("Помилка PDF: " + t.getMessage());
            tv.setTextColor(Color.RED);
            tv.setPadding(40, 150, 40, 40);
            root.addView(tv);
        }
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " Б";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.ROOT, "%.1f КБ", kb);
        double mb = kb / 1024.0;
        return String.format(Locale.ROOT, "%.1f МБ", mb);
    }
}
