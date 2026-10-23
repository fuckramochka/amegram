package app.miogram.bridge.wall;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ImageLocation;
import app.miogram.bridge.MiogramLocale;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;

import java.io.File;

import app.miogram.bridge.wall.WallModels.WallElement;

/**
 * Перегляд медіа-елемента стіни: фото/GIF на всю ширину,
 * відео з прев'ю, файл з відкриттям через FileProvider.
 */
public class WallMediaViewerSheet extends BottomSheet {

    private final WallElement element;

    public WallMediaViewerSheet(Context context, WallElement element) {
        super(context, false, null);
        this.element = element;
        setApplyTopPadding(true);
        setApplyBottomPadding(true);

        FrameLayout root = new FrameLayout(context);
        root.setBackground(WallUi.pill(WallUi.dark() ? 0xF2150F2E : 0xF8FFFFFF, 0, 20));
        root.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(10), AndroidUtilities.dp(10), AndroidUtilities.dp(12));

        int maxH = Math.min(AndroidUtilities.dp(560), (int) (AndroidUtilities.displaySize.x * 0.72f));

        if (WallModels.TYPE_VIDEO.equals(element.type)) {
            FrameLayout video = new FrameLayout(context);
            video.setBackground(WallUi.pill(0, 0, 16));
            video.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(android.view.View v, android.graphics.Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), AndroidUtilities.dp(16));
                }
            });
            video.setClipToOutline(true);
            BackupImageView iv = new BackupImageView(context);
            String src = TextUtils.isEmpty(element.thumbPath) ? element.mediaPath : element.thumbPath;
            iv.setImage(ImageLocation.getForPath(src == null ? "" : src), "800_1200", (Drawable) null, null);
            iv.setAspectFit(true);
            video.addView(iv, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, maxH, Gravity.CENTER));
            video.setOnClickListener(v -> openExternally(context));
            root.addView(video, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            TextView hint = new TextView(context);
            hint.setText(MiogramLocale.get("▶ тапни, щоб відкрити відео", "▶ тапни, чтобы открыть видео", "▶ tap to open the video"));
            hint.setTextColor(0x99FFFFFF);
            hint.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 11.5f);
            hint.setGravity(Gravity.CENTER);
            root.addView(hint, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM, 0, 0, 0, AndroidUtilities.dp(26)));
        } else if (WallModels.TYPE_FILE.equals(element.type)) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(WallUi.pill(WallUi.dark() ? 0x1FFFFFFF : 0x14000000, 0, 14));
            row.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(10), AndroidUtilities.dp(10), AndroidUtilities.dp(10));
            row.setMinimumHeight(AndroidUtilities.dp(70));
            row.setOnClickListener(v -> openExternally(context));
            TextView icon = new TextView(context);
            icon.setText("📎");
            icon.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 18);
            FrameLayout wrap = new FrameLayout(context);
            wrap.setBackground(WallUi.pill(WallUi.dark() ? 0x26FFFFFF : 0x14000000, 0, 12));
            wrap.addView(icon, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
            row.addView(wrap, LayoutHelper.createLinear(44, 44, 0, 0, 10, 0, 0));
            LinearLayout col = new LinearLayout(context);
            col.setOrientation(LinearLayout.VERTICAL);
            TextView name = new TextView(context);
            name.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 14);
            name.setTypeface(AndroidUtilities.bold());
            name.setTextColor(WallUi.textPrimary());
            name.setText(element.fileName.isEmpty() ? "Файл" : element.fileName);
            TextView meta = new TextView(context);
            meta.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 11.5f);
            meta.setTextColor(WallUi.textSecondary());
            meta.setText(WallUi.formatSize(element.fileSize));
            col.addView(name, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            col.addView(meta, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
            row.addView(col, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
            root.addView(row, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        } else {
            FrameLayout frame = new FrameLayout(context);
            frame.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(android.view.View v, android.graphics.Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), AndroidUtilities.dp(16));
                }
            });
            frame.setClipToOutline(true);
            BackupImageView iv = new BackupImageView(context);
            String path = element.mediaPath == null ? "" : element.mediaPath;
            iv.setImage(ImageLocation.getForPath(path), "1000_1400", (Drawable) null, null);
            iv.setAspectFit(true);
            frame.addView(iv, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, maxH));
            root.addView(frame, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        setCustomView(root);
    }

    private void openExternally(Context context) {
        try {
            if (TextUtils.isEmpty(element.mediaPath)) return;
            File f = new File(element.mediaPath);
            if (!f.exists()) return;
            Uri uri = FileProvider.getUriForFile(context, ApplicationLoader.getApplicationId() + ".provider", f);
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
            intent.setDataAndType(uri, element.mime == null || element.mime.isEmpty() ? "*/*" : element.mime);
            intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(intent);
        } catch (Throwable e) {
            Toast.makeText(context, "Не вдалося відкрити", Toast.LENGTH_SHORT).show();
        }
    }
}
