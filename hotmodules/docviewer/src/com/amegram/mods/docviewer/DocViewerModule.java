package com.amegram.mods.docviewer;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
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
import android.widget.ImageView;
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

import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

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
            hookFileOpener();
            host.log("DocViewerModule attached successfully");
        } catch (Throwable t) {
            host.log("Failed to attach DocViewerModule: " + t.getMessage());
        }
    }

    @Override
    public void onDetach() {
        for (HotHook.Unhook u : unhooks) {
            try {
                u.unhook();
            } catch (Throwable ignore) {
            }
        }
        unhooks.clear();
        if (currentDialog != null) {
            try {
                currentDialog.dismiss();
            } catch (Throwable ignore) {
            }
            currentDialog = null;
        }
        if (host != null) {
            host.log("DocViewerModule detached");
            host = null;
        }
    }

    @Override
    public boolean hasSettings() {
        return true;
    }

    @Override
    public String settingsTitle() {
        return "Doc Viewer Pro";
    }

    @Override
    public void fillSettings(List<HotRow> rows) {
        rows.add(HotRow.header("Doc Viewer Pro"));
        rows.add(HotRow.switchRow("enabled", "Увімкнути внутрішній переглядач",
                "Відкривати документи, офісні файли та код безпосередньо у клієнті",
                host != null ? host.getBool("enabled", true) : true));
        rows.add(HotRow.switchRow("line_numbers", "Нумерація рядків",
                "Показувати номери рядків для файлів коду та тексту",
                host != null ? host.getBool("line_numbers", true) : true));
        rows.add(HotRow.switchRow("keep_screen_on", "Не вимикати екран",
                "Тримати екран активним під час читання великих документів",
                host != null ? host.getBool("keep_screen_on", true) : true));
    }

    private void hookFileOpener() {
        try {
            Class<?> au = Class.forName("org.telegram.messenger.AndroidUtilities");
            List<HotHook.Unhook> list = host.hookAll(au, "openForView", new HotHook.Callback() {
                @Override
                public void before(HotHook.Call call) throws Throwable {
                    try {
                        if (host == null || !host.getBool("enabled", true)) {
                            return;
                        }
                        Object[] args = call.getArgs();
                        Object arg0 = args != null && args.length > 0 ? args[0] : null;
                        if (arg0 == null) return;

                        if (handleOpenFile(arg0)) {
                            call.setResult(true);
                        }
                    } catch (Throwable t) {
                        if (host != null) {
                            host.log("Error in openForView hook: " + t.getMessage());
                        }
                    }
                }
            });
            if (list != null) {
                unhooks.addAll(list);
            }
        } catch (Throwable t) {
            if (host != null) {
                host.log("hookFileOpener failed: " + t.getMessage());
            }
        }
    }

    private boolean handleOpenFile(Object arg) {
        try {
            File file = null;
            String fileName = null;
            if (arg instanceof File) {
                file = (File) arg;
                fileName = file.getName();
            } else {
                Class<?> moClass = Class.forName("org.telegram.messenger.MessageObject");
                if (moClass.isInstance(arg)) {
                    Method getFileNameMethod = moClass.getMethod("getFileName");
                    fileName = (String) getFileNameMethod.invoke(arg);

                    Class<?> fmc = Class.forName("org.telegram.messenger.FileLoader");
                    Method getPathToMessage = fmc.getMethod("getPathToMessage", Class.forName("org.telegram.tgnet.TLRPC$Message"));
                    Object messageOwner = moClass.getField("messageOwner").get(arg);
                    if (messageOwner != null) {
                        file = (File) getPathToMessage.invoke(null, messageOwner);
                    }
                }
            }

            if (fileName == null && file != null) {
                fileName = file.getName();
            }

            if (fileName == null) return false;

            int dot = fileName.lastIndexOf('.');
            if (dot < 0 || dot >= fileName.length() - 1) return false;
            String ext = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);

            if (!isSupportedFormat(ext)) return false;

            if (file != null && file.exists()) {
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
        if (ext == null) return false;
        ext = ext.toLowerCase(Locale.ROOT);
        return "pdf".equals(ext) || "zip".equals(ext) || "tar".equals(ext) || "gz".equals(ext)
                || "docx".equals(ext) || "doc".equals(ext) || "xlsx".equals(ext) || "xls".equals(ext)
                || "pptx".equals(ext) || "ppt".equals(ext) || "epub".equals(ext) || "fb2".equals(ext)
                || "txt".equals(ext) || "log".equals(ext) || "json".equals(ext) || "csv".equals(ext)
                || "xml".equals(ext) || "py".equals(ext) || "java".equals(ext) || "kt".equals(ext)
                || "c".equals(ext) || "cpp".equals(ext) || "h".equals(ext) || "js".equals(ext)
                || "ts".equals(ext) || "html".equals(ext) || "htm".equals(ext) || "md".equals(ext)
                || "css".equals(ext) || "sql".equals(ext) || "sh".equals(ext) || "gradle".equals(ext);
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

            if ("zip".equals(ext) || "tar".equals(ext) || "gz".equals(ext)) {
                buildArchiveView(act, root, file);
            } else if ("pdf".equals(ext)) {
                buildPdfView(act, root, file);
            } else {
                buildWebView(act, root, file, ext);
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
            topBar.bringToFront();

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

    private void buildWebView(Activity act, FrameLayout root, File file, String ext) {
        WebView webView = new WebView(act);
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(false);
        ws.setAllowFileAccess(false);
        ws.setAllowContentAccess(false);
        ws.setBuiltInZoomControls(true);
        ws.setDisplayZoomControls(false);
        ws.setUseWideViewPort(true);
        ws.setLoadWithOverviewMode(true);
        ws.setDefaultTextEncodingName("utf-8");
        webView.setBackgroundColor(Color.parseColor("#18181e"));

        String bodyContent;
        if ("docx".equals(ext)) {
            bodyContent = parseDocx(file);
        } else if ("doc".equals(ext)) {
            bodyContent = parseLegacyDoc(file);
        } else if ("xlsx".equals(ext) || "xls".equals(ext)) {
            bodyContent = parseXlsx(file);
        } else if ("pptx".equals(ext) || "ppt".equals(ext)) {
            bodyContent = parsePptx(file);
        } else if ("epub".equals(ext)) {
            bodyContent = parseEpub(file);
        } else if ("fb2".equals(ext)) {
            bodyContent = parseFb2(file);
        } else if ("html".equals(ext) || "htm".equals(ext)) {
            bodyContent = readRawText(file, false);
        } else {
            // Code & Plain Text
            boolean lineNumbers = host != null && host.getBool("line_numbers", true);
            bodyContent = "<pre class='code-block'><code>" + escapeHtml(readRawText(file, lineNumbers)) + "</code></pre>";
        }

        String fullHtml = wrapHtml(bodyContent);
        webView.loadDataWithBaseURL(null, fullHtml, "text/html", "utf-8", null);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        lp.topMargin = 130;
        root.addView(webView, lp);
    }

    private String wrapHtml(String body) {
        return "<!DOCTYPE html><html><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width, initial-scale=1.0, user-scalable=yes'>"
                + "<style>"
                + "body { background-color: #18181e; color: #E0E0E6; font-family: -apple-system, Roboto, sans-serif; padding: 20px; line-height: 1.6; font-size: 16px; margin: 0; }"
                + "h1, h2, h3 { color: #50A2E9; margin-top: 1.2em; font-weight: 600; }"
                + "p { margin: 0.8em 0; }"
                + "table { border-collapse: collapse; width: 100%; margin: 16px 0; font-size: 14px; }"
                + "th, td { border: 1px solid #33333E; padding: 8px 12px; text-align: left; }"
                + "th { background-color: #23232C; color: #50A2E9; }"
                + "tr:nth-child(even) { background-color: #1E1E26; }"
                + ".slide-card { background: #22222B; border-radius: 10px; padding: 18px; margin-bottom: 22px; border: 1px solid #32323E; }"
                + ".slide-title { color: #4DA3FF; font-size: 14px; font-weight: bold; margin-bottom: 10px; text-transform: uppercase; letter-spacing: 0.5px; }"
                + ".code-block { background: #1F1F28; border-radius: 8px; padding: 14px; overflow-x: auto; font-family: monospace; font-size: 13.5px; line-height: 1.5; color: #D8D8E0; border: 1px solid #2D2D3A; }"
                + "</style></head><body>"
                + body
                + "</body></html>";
    }

    private String parseDocx(File file) {
        StringBuilder sb = new StringBuilder();
        try (ZipFile zf = new ZipFile(file)) {
            ZipEntry entry = zf.getEntry("word/document.xml");
            if (entry == null) {
                return "<p style='color:#E57373;'>Не знайдено word/document.xml у документі</p>";
            }
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(false);
            Document doc = dbf.newDocumentBuilder().parse(zf.getInputStream(entry));
            Element body = doc.getDocumentElement();
            extractDocxNode(body, sb);
        } catch (java.util.zip.ZipException ze) {
            return parseLegacyDoc(file);
        } catch (Throwable t) {
            return "<p style='color:#E57373;'>Помилка обробки DOCX: " + escapeHtml(t.getMessage()) + "</p>";
        }
        String res = sb.toString().trim();
        return res.isEmpty() ? "<p style='color:#8E8E93;'>(Документ порожній)</p>" : res;
    }

    private void extractDocxNode(Node node, StringBuilder sb) {
        if (node.getNodeType() == Node.TEXT_NODE) {
            String val = node.getNodeValue();
            if (val != null) sb.append(escapeHtml(val));
            return;
        }
        String name = node.getNodeName();
        boolean isPara = name.endsWith(":p") || name.equals("p");
        boolean isTable = name.endsWith(":tbl") || name.equals("tbl");
        boolean isRow = name.endsWith(":tr") || name.equals("tr");
        boolean isCell = name.endsWith(":tc") || name.equals("tc");
        boolean isBreak = name.endsWith(":br") || name.equals("br");
        boolean isTab = name.endsWith(":tab") || name.equals("tab");

        if (isTable) sb.append("<table border='1' cellspacing='0' cellpadding='6'>");
        if (isRow) sb.append("<tr>");
        if (isCell) sb.append("<td>");
        if (isPara) sb.append("<p>");
        if (isBreak) sb.append("<br/>");
        if (isTab) sb.append("&emsp;");

        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            extractDocxNode(children.item(i), sb);
        }

        if (isPara) sb.append("</p>\n");
        if (isCell) sb.append("</td>");
        if (isRow) sb.append("</tr>\n");
        if (isTable) sb.append("</table>\n");
    }

    private String parseLegacyDoc(File file) {
        StringBuilder sb = new StringBuilder("<p style='color:#50A2E9;'><b>(Текстовий вміст .doc)</b></p><p>");
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buf = new byte[Math.min((int) file.length(), 512 * 1024)];
            int len = fis.read(buf);
            StringBuilder cur = new StringBuilder();
            for (int i = 0; i < len; i++) {
                char c = (char) (buf[i] & 0xFF);
                if ((c >= 32 && c <= 126) || c == '\n' || c == '\t' || c >= 160) {
                    cur.append(c);
                } else {
                    if (cur.length() >= 4) {
                        sb.append(escapeHtml(cur.toString())).append("<br/>");
                    }
                    cur.setLength(0);
                }
            }
            sb.append("</p>");
            return sb.toString();
        } catch (Throwable t) {
            return "<p style='color:#E57373;'>Помилка .doc: " + escapeHtml(t.getMessage()) + "</p>";
        }
    }

    private String parseXlsx(File file) {
        StringBuilder sb = new StringBuilder();
        try (ZipFile zf = new ZipFile(file)) {
            List<String> sharedStrings = new ArrayList<>();
            ZipEntry sstEntry = zf.getEntry("xl/sharedStrings.xml");
            if (sstEntry != null) {
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                dbf.setNamespaceAware(false);
                Document doc = dbf.newDocumentBuilder().parse(zf.getInputStream(sstEntry));
                NodeList siList = doc.getElementsByTagName("si");
                for (int i = 0; i < siList.getLength(); i++) {
                    Element si = (Element) siList.item(i);
                    NodeList tList = si.getElementsByTagName("t");
                    StringBuilder ssi = new StringBuilder();
                    for (int j = 0; j < tList.getLength(); j++) {
                        ssi.append(tList.item(j).getTextContent());
                    }
                    sharedStrings.add(ssi.toString());
                }
            }

            Enumeration<? extends ZipEntry> entries = zf.entries();
            int sheetNum = 1;
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                String ename = e.getName();
                if (ename.startsWith("xl/worksheets/sheet") && ename.endsWith(".xml")) {
                    sb.append("<h3>Аркуш ").append(sheetNum++).append("</h3>");
                    sb.append("<table border='1' cellspacing='0' cellpadding='6'>");
                    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                    dbf.setNamespaceAware(false);
                    Document sdoc = dbf.newDocumentBuilder().parse(zf.getInputStream(e));
                    NodeList rows = sdoc.getElementsByTagName("row");
                    int maxRows = Math.min(rows.getLength(), 200);
                    for (int r = 0; r < maxRows; r++) {
                        sb.append("<tr>");
                        Element rowEl = (Element) rows.item(r);
                        NodeList cells = rowEl.getElementsByTagName("c");
                        for (int c = 0; c < cells.getLength(); c++) {
                            Element cellEl = (Element) cells.item(c);
                            String type = cellEl.getAttribute("t");
                            String val = "";
                            NodeList vList = cellEl.getElementsByTagName("v");
                            if (vList.getLength() > 0) {
                                val = vList.item(0).getTextContent();
                            }
                            if ("s".equals(type)) {
                                try {
                                    int idx = Integer.parseInt(val);
                                    if (idx >= 0 && idx < sharedStrings.size()) {
                                        val = sharedStrings.get(idx);
                                    }
                                } catch (Throwable ignore) {
                                }
                            }
                            sb.append("<td>").append(escapeHtml(val)).append("</td>");
                        }
                        sb.append("</tr>\n");
                    }
                    sb.append("</table><br/>");
                }
            }
        } catch (Throwable t) {
            return "<p style='color:#E57373;'>Помилка XLSX: " + escapeHtml(t.getMessage()) + "</p>";
        }
        String res = sb.toString().trim();
        return res.isEmpty() ? "<p style='color:#8E8E93;'>(Таблиця порожня)</p>" : res;
    }

    private String parsePptx(File file) {
        StringBuilder sb = new StringBuilder();
        try (ZipFile zf = new ZipFile(file)) {
            List<String> slideNames = new ArrayList<>();
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry ze = entries.nextElement();
                if (ze.getName().startsWith("ppt/slides/slide") && ze.getName().endsWith(".xml")) {
                    slideNames.add(ze.getName());
                }
            }
            slideNames.sort((a, b) -> {
                int numA = extractNum(a);
                int numB = extractNum(b);
                return Integer.compare(numA, numB);
            });

            for (int i = 0; i < slideNames.size(); i++) {
                String sname = slideNames.get(i);
                ZipEntry se = zf.getEntry(sname);
                if (se == null) continue;
                sb.append("<div class='slide-card'>");
                sb.append("<div class='slide-title'>Слайд ").append(i + 1).append("</div>");
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                dbf.setNamespaceAware(false);
                Document sdoc = dbf.newDocumentBuilder().parse(zf.getInputStream(se));
                NodeList tList = sdoc.getElementsByTagName("a:t");
                if (tList.getLength() == 0) tList = sdoc.getElementsByTagName("t");
                for (int t = 0; t < tList.getLength(); t++) {
                    String text = tList.item(t).getTextContent();
                    if (text != null && !text.trim().isEmpty()) {
                        sb.append("<p>").append(escapeHtml(text)).append("</p>");
                    }
                }
                sb.append("</div>\n");
            }
        } catch (Throwable t) {
            return "<p style='color:#E57373;'>Помилка PPTX: " + escapeHtml(t.getMessage()) + "</p>";
        }
        String res = sb.toString().trim();
        return res.isEmpty() ? "<p style='color:#8E8E93;'>(Презентація порожня)</p>" : res;
    }

    private String parseFb2(File file) {
        StringBuilder sb = new StringBuilder();
        try (FileInputStream fis = new FileInputStream(file)) {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(false);
            Document doc = dbf.newDocumentBuilder().parse(fis);
            NodeList titles = doc.getElementsByTagName("book-title");
            if (titles.getLength() > 0) {
                sb.append("<h2>").append(escapeHtml(titles.item(0).getTextContent())).append("</h2>");
            }
            NodeList pList = doc.getElementsByTagName("p");
            int max = Math.min(pList.getLength(), 1000);
            for (int i = 0; i < max; i++) {
                sb.append("<p>").append(escapeHtml(pList.item(i).getTextContent())).append("</p>\n");
            }
        } catch (Throwable t) {
            return "<p style='color:#E57373;'>Помилка FB2: " + escapeHtml(t.getMessage()) + "</p>";
        }
        return sb.toString();
    }

    private String parseEpub(File file) {
        StringBuilder sb = new StringBuilder();
        try (ZipFile zf = new ZipFile(file)) {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry ze = entries.nextElement();
                String name = ze.getName().toLowerCase(Locale.ROOT);
                if ((name.endsWith(".xhtml") || name.endsWith(".html") || name.endsWith(".htm"))
                        && !name.contains("toc") && !name.contains("nav")) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(zf.getInputStream(ze), "UTF-8"))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line).append("\n");
                        }
                    }
                }
            }
        } catch (Throwable t) {
            return "<p style='color:#E57373;'>Помилка EPUB: " + escapeHtml(t.getMessage()) + "</p>";
        }
        String res = sb.toString().trim();
        return res.isEmpty() ? "<p style='color:#8E8E93;'>(Книга порожня)</p>" : res;
    }

    private String readRawText(File file, boolean lineNumbers) {
        StringBuilder sb = new StringBuilder();
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
            sb.append("Помилка: ").append(t.getMessage());
        }
        return sb.toString();
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

            int limit = Math.min(pageCount, 15);
            for (int i = 0; i < limit; i++) {
                PdfRenderer.Page page = renderer.openPage(i);
                int w = act.getResources().getDisplayMetrics().widthPixels;
                int h = (int) ((float) page.getHeight() / page.getWidth() * w);
                Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                bmp.eraseColor(Color.WHITE);
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                page.close();

                ImageView iv = new ImageView(act);
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

    private static int extractNum(String s) {
        try {
            String digits = s.replaceAll("\\D+", "");
            return digits.isEmpty() ? 0 : Integer.parseInt(digits);
        } catch (Throwable ignore) {
            return 0;
        }
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " Б";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.ROOT, "%.1f КБ", kb);
        double mb = kb / 1024.0;
        return String.format(Locale.ROOT, "%.1f МБ", mb);
    }
}
