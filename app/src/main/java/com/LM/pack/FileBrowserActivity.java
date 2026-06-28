package com.LM.pack;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import com.LM.pack.R;
import com.LM.pack.model.FileTreeItem;
import com.LM.pack.model.ProjectEntry;
import com.LM.pack.service.ProjectFileService;
import com.LM.pack.theme.AppThemePalette;
import com.LM.pack.theme.LiquidGlassBackgroundView;
import com.LM.pack.theme.ThemeManager;
import com.LM.pack.util.CommonUtils;
import java.io.File;
import java.util.ArrayList;

public class FileBrowserActivity extends Activity {

    public static final String EXTRA_PROJECT_NAME = "project_name";
    public static final String EXTRA_PROJECT_DIR = "project_dir";
    public static final String EXTRA_PROJECT_PACKAGE = "project_package";
    public static final String EXTRA_CURRENT_DIR = "current_dir";
    public static final String EXTRA_SELECTED_FILE = "selected_file";

    private ThemeManager themeManager;
    private ProjectFileService projectFileService;
    private AppThemePalette palette;
    private ProjectEntry currentProject;
    private File currentDir;
    private ArrayList<FileTreeItem> items = new ArrayList<FileTreeItem>();

    private LiquidGlassBackgroundView bgSceneView;
    private TextView tvTitle;
    private TextView tvPath;
    private Button btnBack;
    private ListView lvFiles;
    private FileListAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_browser);
        themeManager = new ThemeManager(this);
        projectFileService = new ProjectFileService();
        bindViews();
        readIntent();
        adapter = new FileListAdapter();
        lvFiles.setAdapter(adapter);
        bindEvents();
        applyThemeUi();
        refreshList();
    }

    private void bindViews() {
        bgSceneView = (LiquidGlassBackgroundView) findViewById(R.id.bgSceneView);
        tvTitle = (TextView) findViewById(R.id.tvTitle);
        tvPath = (TextView) findViewById(R.id.tvPath);
        btnBack = (Button) findViewById(R.id.btnBack);
        lvFiles = (ListView) findViewById(R.id.lvFiles);
    }

    private void readIntent() {
        Intent intent = getIntent();
        String projectName = intent.getStringExtra(EXTRA_PROJECT_NAME);
        String projectDir = intent.getStringExtra(EXTRA_PROJECT_DIR);
        String packageName = intent.getStringExtra(EXTRA_PROJECT_PACKAGE);
        String currentDirPath = intent.getStringExtra(EXTRA_CURRENT_DIR);
        currentProject = new ProjectEntry(projectName, packageName, projectDir, "", getString(R.string.project_mode_generic), "");
        currentDir = currentDirPath == null || currentDirPath.length() == 0 ? new File(projectDir) : new File(currentDirPath);
    }

    private void bindEvents() {
        btnBack.setOnClickListener(v -> finish());
        lvFiles.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= items.size()) {
                return;
            }
            FileTreeItem item = items.get(position);
            if (item.isDirectory()) {
                Intent intent = new Intent(FileBrowserActivity.this, FileBrowserActivity.class);
                intent.putExtra(EXTRA_PROJECT_NAME, currentProject.getProjectName());
                intent.putExtra(EXTRA_PROJECT_DIR, currentProject.getProjectDir());
                intent.putExtra(EXTRA_PROJECT_PACKAGE, currentProject.getPackageName());
                intent.putExtra(EXTRA_CURRENT_DIR, item.getFile().getAbsolutePath());
                startActivityForResult(intent, 1001);
                return;
            }
            Intent resultIntent = new Intent();
            resultIntent.putExtra(EXTRA_SELECTED_FILE, item.getFile().getAbsolutePath());
            setResult(RESULT_OK, resultIntent);
            finish();
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            setResult(RESULT_OK, data);
            finish();
        }
    }

    private void applyThemeUi() {
        palette = themeManager.getPalette(this);
        themeManager.applyActivityWindow(this, palette);
        themeManager.applyTaggedStyles(findViewById(R.id.fileBrowserRoot), palette);
        if (bgSceneView != null) {
            bgSceneView.setPalette(palette);
        }
    }

    private void refreshList() {
        items.clear();
        items.addAll(projectFileService.listDirectory(currentProject, currentDir));
        String title = currentDir.getName();
        if (title == null || title.length() == 0) {
            title = currentProject.getProjectName();
        }
        tvTitle.setText(title);
        tvPath.setText(getString(R.string.file_browser_path_format, projectFileService.buildDisplayPath(currentProject, currentDir)));
        adapter.notifyDataSetChanged();
    }

    private class FileListAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Holder holder;
            if (convertView == null) {
                LinearLayout row = new LinearLayout(FileBrowserActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(dp(14), dp(12), dp(14), dp(12));
                row.setGravity(Gravity.CENTER_VERTICAL);

                TextView iconBadge = new TextView(FileBrowserActivity.this);
                iconBadge.setTextSize(11f);
                iconBadge.setGravity(Gravity.CENTER);
                iconBadge.setPadding(dp(8), dp(4), dp(8), dp(4));
                iconBadge.setAllCaps(true);
                LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                    dp(40), dp(28)
                );
                iconParams.setMargins(0, 0, dp(10), 0);
                row.addView(iconBadge, iconParams);

                LinearLayout textBlock = new LinearLayout(FileBrowserActivity.this);
                textBlock.setOrientation(LinearLayout.VERTICAL);
                textBlock.setGravity(Gravity.CENTER_VERTICAL);
                LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                );
                row.addView(textBlock, textParams);

                TextView title = new TextView(FileBrowserActivity.this);
                title.setTextSize(14f);
                textBlock.addView(title);

                TextView sub = new TextView(FileBrowserActivity.this);
                sub.setTextSize(11f);
                sub.setPadding(0, dp(4), 0, 0);
                textBlock.addView(sub);

                holder = new Holder(iconBadge, title, sub);
                row.setTag(holder);
                convertView = row;
            } else {
                holder = (Holder) convertView.getTag();
            }

            FileTreeItem item = items.get(position);
            convertView.setBackground(themeManager.createPanelDrawable(palette, false));

            String fileName = item.getFile().getName();
            if (item.isDirectory()) {
                holder.iconBadge.setText("DIR");
                holder.iconBadge.setTextColor(palette.textPrimary);
                GradientDrawable bg = new GradientDrawable();
                bg.setShape(GradientDrawable.RECTANGLE);
                bg.setCornerRadius(dp(4));
                bg.setColor(makeAlpha(palette.accentColor, 40));
                bg.setStroke(dp(1), makeAlpha(palette.accentColor, 120));
                holder.iconBadge.setBackground(bg);
                holder.title.setText(fileName);
                holder.title.setTextColor(palette.textPrimary);
            } else {
                FormatBadge badge = getFormatBadge(fileName);
                holder.iconBadge.setText(badge.label);
                holder.iconBadge.setTextColor(badge.textColor);
                GradientDrawable bg = new GradientDrawable();
                bg.setShape(GradientDrawable.RECTANGLE);
                bg.setCornerRadius(dp(4));
                bg.setColor(badge.bgColor);
                holder.iconBadge.setBackground(bg);
                holder.title.setText(fileName);
                holder.title.setTextColor(palette.textSecondary);
            }
            holder.sub.setText(item.isDirectory() ? R.string.file_browser_enter_directory : R.string.file_browser_open_file);
            holder.sub.setTextColor(palette.textMuted);
            return convertView;
        }
    }

    private static class FormatBadge {
        final String label;
        final int textColor;
        final int bgColor;

        FormatBadge(String label, int textColor, int bgColor) {
            this.label = label;
            this.textColor = textColor;
            this.bgColor = bgColor;
        }
    }

    private FormatBadge getFormatBadge(String fileName) {
        int dot = fileName.lastIndexOf('.');
        String ext = dot >= 0 ? fileName.substring(dot + 1).toLowerCase() : "";
        switch (ext) {
            case "java": return new FormatBadge("JAVA", 0xFFFFFFFF, 0xFFB07219);
            case "kt": return new FormatBadge("KT", 0xFF000000, 0xFF7F52FF);
            case "xml": return new FormatBadge("XML", 0xFF000000, 0xFF34A853);
            case "gradle": return new FormatBadge("GRD", 0xFFFFFFFF, 0xFF02303A);
            case "kts": return new FormatBadge("KTS", 0xFF000000, 0xFF7F52FF);
            case "png": case "jpg": case "jpeg": case "webp": case "gif": case "bmp": case "svg":
                return new FormatBadge("IMG", 0xFFFFFFFF, 0xFFE91E63);
            case "json": return new FormatBadge("JSON", 0xFFFFFFFF, 0xFF607D8B);
            case "properties": return new FormatBadge("PROP", 0xFF000000, 0xFF8BC34A);
            case "pro": return new FormatBadge("PRO", 0xFFFFFFFF, 0xFF795548);
            case "txt": return new FormatBadge("TXT", 0xFFFFFFFF, 0xFF9E9E9E);
            case "md": return new FormatBadge("MD", 0xFFFFFFFF, 0xFF42A5F5);
            case "sh": return new FormatBadge("SH", 0xFF000000, 0xFF00BCD4);
            case "bat": return new FormatBadge("BAT", 0xFFFFFFFF, 0xFF4CAF50);
            case "aidl": return new FormatBadge("AIDL", 0xFFFFFFFF, 0xFF7C4DFF);
            case "rs": return new FormatBadge("RS", 0xFF000000, 0xFFFFAB00);
            case "jar": return new FormatBadge("JAR", 0xFFFFFFFF, 0xFFD84315);
            case "aar": return new FormatBadge("AAR", 0xFFFFFFFF, 0xFFFF6F00);
            case "apk": return new FormatBadge("APK", 0xFFFFFFFF, 0xFF2196F3);
            case "so": return new FormatBadge("SO", 0xFF000000, 0xFFCDDC39);
            case "dex": return new FormatBadge("DEX", 0xFFFFFFFF, 0xFF1A237E);
            case "html": return new FormatBadge("HTML", 0xFFFFFFFF, 0xFFE44D26);
            case "css": return new FormatBadge("CSS", 0xFFFFFFFF, 0xFF2965F1);
            case "js": return new FormatBadge("JS", 0xFF000000, 0xFFF7DF1E);
            case "ts": return new FormatBadge("TS", 0xFFFFFFFF, 0xFF3178C6);
            case "c": return new FormatBadge("C", 0xFFFFFFFF, 0xFF555555);
            case "cpp": case "cxx": case "cc": return new FormatBadge("C++", 0xFF000000, 0xFFF34B7D);
            case "h": return new FormatBadge("H", 0xFFFFFFFF, 0xFFA8B400);
            case "mk": return new FormatBadge("MK", 0xFF000000, 0xFFB0BEC5);
            case "cmake": return new FormatBadge("CMAKE", 0xFFFFFFFF, 0xFF064F8C);
            default: return new FormatBadge("FILE", 0xFF000000, 0xFFCFD8DC);
        }
    }

    private int makeAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static class Holder {
        final TextView iconBadge;
        final TextView title;
        final TextView sub;

        Holder(TextView iconBadge, TextView title, TextView sub) {
            this.iconBadge = iconBadge;
            this.title = title;
            this.sub = sub;
        }
    }

    private int dp(int value) {
        return CommonUtils.dp(this, (float) value);
    }
}
