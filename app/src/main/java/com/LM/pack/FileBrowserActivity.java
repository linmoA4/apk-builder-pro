package com.LM.pack;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import com.LM.pack.model.FileTreeItem;
import com.LM.pack.model.ProjectEntry;
import com.LM.pack.service.ProjectFileService;
import com.LM.pack.theme.AppThemePalette;
import com.LM.pack.theme.LiquidGlassBackgroundView;
import com.LM.pack.theme.ThemeManager;
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
        currentProject = new ProjectEntry(projectName, packageName, projectDir, "", "项目", "");
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
        tvTitle.setText("..  " + title);
        tvPath.setText(projectFileService.buildDisplayPath(currentProject, currentDir));
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
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(dp(14), dp(12), dp(14), dp(12));
                row.setGravity(Gravity.CENTER_VERTICAL);

                TextView title = new TextView(FileBrowserActivity.this);
                title.setTextSize(14f);
                row.addView(title);

                TextView sub = new TextView(FileBrowserActivity.this);
                sub.setTextSize(11f);
                sub.setPadding(0, dp(4), 0, 0);
                row.addView(sub);

                holder = new Holder(title, sub);
                row.setTag(holder);
                convertView = row;
            } else {
                holder = (Holder) convertView.getTag();
            }

            FileTreeItem item = items.get(position);
            convertView.setBackground(themeManager.createPanelDrawable(palette, false));
            holder.title.setText((item.isDirectory() ? "📁  " : "📄  ") + item.getFile().getName());
            holder.title.setTextColor(item.isDirectory() ? palette.textPrimary : palette.textSecondary);
            holder.sub.setText(item.isDirectory() ? "点击进入下一级目录" : "点击打开到编辑器");
            holder.sub.setTextColor(palette.textMuted);
            return convertView;
        }
    }

    private static class Holder {
        final TextView title;
        final TextView sub;

        Holder(TextView title, TextView sub) {
            this.title = title;
            this.sub = sub;
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
