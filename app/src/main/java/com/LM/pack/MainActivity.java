package com.LM.pack;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.LM.pack.build.BuildManager;
import com.LM.pack.build.ProjectPreflightChecker;
import com.LM.pack.editor.CodeEditorView;
import com.LM.pack.editor.EditorTabManager;
import com.LM.pack.env.EnvironmentManager;
import com.LM.pack.log.LogManager;
import com.LM.pack.model.BuildIssue;
import com.LM.pack.model.BuildResult;
import com.LM.pack.model.EnvironmentState;
import com.LM.pack.model.ProjectConfig;
import com.LM.pack.model.ProjectEntry;
import com.LM.pack.project.ProjectManager;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class MainActivity extends Activity {

    private Handler handler;
    private ProgressDialog progressDialog;

    private LogManager logManager;
    private EnvironmentManager environmentManager;
    private ProjectManager projectManager;
    private BuildManager buildManager;
    private ProjectPreflightChecker preflightChecker;
    private EnvironmentState environmentState;

    private LinearLayout homePane;
    private LinearLayout editorPane;
    private LinearLayout emptyState;
    private LinearLayout fileDrawer;
    private LinearLayout suggestionCard;
    private LinearLayout editorTabContainer;
    private TextView tvToolbarTitle;
    private TextView tvEditorProject;
    private TextView tvCurrentFilePath;
    private TextView tvIssueTitle;
    private TextView tvIssueFix;
    private Button btnBackHome;
    private Button btnSettings;
    private Button btnFabAdd;
    private Button btnToggleFiles;
    private Button btnBug;
    private Button btnSaveFile;
    private Button btnBuild;
    private Button btnCopyFix;
    private ListView lvProjects;
    private ListView lvFiles;
    private CodeEditorView etEditor;

    private final ArrayList<ProjectEntry> projectEntries = new ArrayList<ProjectEntry>();
    private final ArrayList<FileTreeItem> fileTreeItems = new ArrayList<FileTreeItem>();
    private final ArrayList<String> expandedDirs = new ArrayList<String>();
    private final ArrayList<BuildIssue> lastBuildIssues = new ArrayList<BuildIssue>();

    private ProjectAdapter projectAdapter;
    private FileTreeAdapter fileTreeAdapter;
    private EditorTabManager editorTabManager;
    private ProjectEntry currentProject;
    private File currentOpenFile;
    private boolean projectPrepared = false;
    private boolean isBuildRunning = false;
    private int selectedJdkIndex = 3;
    private int selectedNdkIndex = 0;
    private String currentCopiedFix = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        handler = new Handler(getMainLooper());
        SharedPreferences sharedPreferences = getSharedPreferences(EnvironmentManager.PREFS_NAME, MODE_PRIVATE);
        environmentManager = new EnvironmentManager(this, sharedPreferences);
        projectManager = new ProjectManager();
        buildManager = new BuildManager();
        preflightChecker = new ProjectPreflightChecker(projectManager, environmentManager);
        environmentState = environmentManager.loadState();
        selectedJdkIndex = environmentManager.loadSelectedJdkIndex();
        selectedNdkIndex = environmentManager.loadSelectedNdkIndex();

        bindViews();
        initAdapters();
        bindEvents();
        appendStartupLogs();
        refreshProjectList();
        showHome();
    }

    @Override
    protected void onResume() {
        super.onResume();
        environmentState = environmentManager.loadState();
        selectedJdkIndex = environmentManager.loadSelectedJdkIndex();
        selectedNdkIndex = environmentManager.loadSelectedNdkIndex();
    }

    private void bindViews() {
        homePane = (LinearLayout) findViewById(R.id.homePane);
        editorPane = (LinearLayout) findViewById(R.id.editorPane);
        emptyState = (LinearLayout) findViewById(R.id.emptyState);
        fileDrawer = (LinearLayout) findViewById(R.id.fileDrawer);
        suggestionCard = (LinearLayout) findViewById(R.id.suggestionCard);
        editorTabContainer = (LinearLayout) findViewById(R.id.editorTabContainer);
        tvToolbarTitle = (TextView) findViewById(R.id.tvToolbarTitle);
        tvEditorProject = (TextView) findViewById(R.id.tvEditorProject);
        tvCurrentFilePath = (TextView) findViewById(R.id.tvCurrentFilePath);
        tvIssueTitle = (TextView) findViewById(R.id.tvIssueTitle);
        tvIssueFix = (TextView) findViewById(R.id.tvIssueFix);
        btnBackHome = (Button) findViewById(R.id.btnBackHome);
        btnSettings = (Button) findViewById(R.id.btnSettings);
        btnFabAdd = (Button) findViewById(R.id.btnFabAdd);
        btnToggleFiles = (Button) findViewById(R.id.btnToggleFiles);
        btnBug = (Button) findViewById(R.id.btnBug);
        btnSaveFile = (Button) findViewById(R.id.btnSaveFile);
        btnBuild = (Button) findViewById(R.id.btnBuild);
        btnCopyFix = (Button) findViewById(R.id.btnCopyFix);
        lvProjects = (ListView) findViewById(R.id.lvProjects);
        lvFiles = (ListView) findViewById(R.id.lvFiles);
        etEditor = (CodeEditorView) findViewById(R.id.etEditor);

        TextView tvLogs = (TextView) findViewById(R.id.tvLogs);
        ScrollView logScrollView = (ScrollView) findViewById(R.id.logScrollView);
        logManager = new LogManager(tvLogs, logScrollView);

        ViewGroup.LayoutParams layoutParams = fileDrawer.getLayoutParams();
        layoutParams.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.38f);
        fileDrawer.setLayoutParams(layoutParams);
    }

    private void initAdapters() {
        projectAdapter = new ProjectAdapter();
        fileTreeAdapter = new FileTreeAdapter();
        editorTabManager = new EditorTabManager(this, editorTabContainer);
        editorTabManager.setTabListener(new EditorTabManager.TabListener() {
            @Override
            public void onTabSelected(File file) {
                if (file == null || sameFile(file, currentOpenFile)) {
                    return;
                }
                saveCurrentFile();
                loadFileIntoEditor(file, false);
            }

            @Override
            public void onActiveTabClosed(File fallbackFile) {
                saveCurrentFile();
                if (fallbackFile != null) {
                    loadFileIntoEditor(fallbackFile, false);
                } else {
                    clearEditor();
                }
            }
        });
        lvProjects.setAdapter(projectAdapter);
        lvFiles.setAdapter(fileTreeAdapter);
    }

    private void bindEvents() {
        btnBackHome.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveCurrentFile();
                showHome();
            }
        });

        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });

        btnFabAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAddActionDialog();
            }
        });

        btnToggleFiles.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleFileDrawer();
            }
        });

        btnSaveFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveCurrentFile();
            }
        });

        btnBuild.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                detectAndBuild();
            }
        });

        btnBug.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showIssueChooser();
            }
        });

        btnCopyFix.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentCopiedFix.length() == 0) {
                    toast("当前没有可复制的修复建议");
                    return;
                }
                ClipboardManager clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                clipboardManager.setPrimaryClip(ClipData.newPlainText("修复建议", currentCopiedFix));
                toast("修复建议已复制");
            }
        });

        lvProjects.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                openProject(projectEntries.get(position));
            }
        });

        lvFiles.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                handleFileTreeClick(position);
            }
        });

        etEditor.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideFileDrawer();
            }
        });
    }

    private void appendStartupLogs() {
        logManager.appendLogLine("INFO", "首页保持紧凑深色工作区，编辑区已切到更接近 AIDE 的多标签结构。");
        logManager.appendKeyValue("INFO", "当前 JDK", EnvironmentManager.JDK_NAMES[selectedJdkIndex]);
        logManager.appendKeyValue("INFO", "当前 NDK", EnvironmentManager.NDK_NAMES[selectedNdkIndex]);
        logManager.appendKeyValue("INFO", "Android SDK", safeText(environmentState.getAndroidSdkDir(), "未登记"));
        logManager.appendKeyValue(
            "INFO",
            "JDK 安装状态",
            environmentManager.isSelectedJdkInstalled(selectedJdkIndex, environmentState) ? "已安装" : "未安装"
        );
        logManager.appendKeyValue(
            "INFO",
            "NDK 安装状态",
            environmentManager.isSelectedNdkInstalled(selectedNdkIndex, environmentState) ? "已安装" : "未安装"
        );
    }

    private void refreshProjectList() {
        projectEntries.clear();
        projectEntries.addAll(
            projectManager.scanProjects(
                environmentManager.getManagedProjectRootDir(),
                environmentManager.getImportedProjectRootDir()
            )
        );
        Collections.sort(projectEntries, new Comparator<ProjectEntry>() {
            @Override
            public int compare(ProjectEntry a, ProjectEntry b) {
                return a.getProjectName().compareToIgnoreCase(b.getProjectName());
            }
        });
        projectAdapter.notifyDataSetChanged();
        if (projectEntries.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            lvProjects.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            lvProjects.setVisibility(View.VISIBLE);
        }
        logManager.appendKeyValue("INFO", "已发现项目数", String.valueOf(projectEntries.size()));
    }

    private void showHome() {
        homePane.setVisibility(View.VISIBLE);
        editorPane.setVisibility(View.GONE);
        btnBackHome.setVisibility(View.GONE);
        btnFabAdd.setVisibility(View.VISIBLE);
        btnToggleFiles.setText("目录");
        tvToolbarTitle.setText("APK Builder Pro");
        suggestionCard.setVisibility(View.GONE);
        hideFileDrawer();
        currentProject = null;
        projectPrepared = false;
        editorTabManager.clear();
        clearEditor();
    }

    private void openProject(ProjectEntry entry) {
        currentProject = entry;
        projectPrepared = true;
        homePane.setVisibility(View.GONE);
        editorPane.setVisibility(View.VISIBLE);
        btnBackHome.setVisibility(View.VISIBLE);
        btnFabAdd.setVisibility(View.GONE);
        tvToolbarTitle.setText("编辑器");
        tvEditorProject.setText(entry.getProjectName() + "  ·  " + safeText(entry.getPackageName(), "未识别包名"));
        tvCurrentFilePath.setText(entry.getProjectDir());
        suggestionCard.setVisibility(View.GONE);
        lastBuildIssues.clear();
        updateBugButtonState();
        editorTabManager.clear();
        loadProjectFiles();
        fileDrawer.setVisibility(View.VISIBLE);
        btnToggleFiles.setText("收起");
        openDefaultEditorFile(entry);
        logManager.appendKeyValue("INFO", "已打开项目", entry.getProjectDir());
    }

    private void openDefaultEditorFile(ProjectEntry entry) {
        File manifestFile = new File(entry.getProjectDir(), "app/src/main/AndroidManifest.xml");
        File buildGradle = new File(entry.getProjectDir(), "app/build.gradle");
        File buildGradleKts = new File(entry.getProjectDir(), "app/build.gradle.kts");
        String packageName = safeText(entry.getPackageName(), "");
        File mainJava = new File(entry.getProjectDir(), "app/src/main/java/" + packageName.replace('.', '/') + "/MainActivity.java");
        File mainKt = new File(entry.getProjectDir(), "app/src/main/kotlin/" + packageName.replace('.', '/') + "/MainActivity.kt");
        if (mainJava.exists()) {
            loadFileIntoEditor(mainJava, true);
        } else if (mainKt.exists()) {
            loadFileIntoEditor(mainKt, true);
        } else if (manifestFile.exists()) {
            loadFileIntoEditor(manifestFile, true);
        } else if (buildGradle.exists()) {
            loadFileIntoEditor(buildGradle, true);
        } else if (buildGradleKts.exists()) {
            loadFileIntoEditor(buildGradleKts, true);
        } else {
            File fallbackMainActivity = findFileBySuffix(new File(entry.getProjectDir()), "MainActivity.java");
            if (fallbackMainActivity == null) {
                fallbackMainActivity = findFileBySuffix(new File(entry.getProjectDir()), "MainActivity.kt");
            }
            if (fallbackMainActivity != null) {
                loadFileIntoEditor(fallbackMainActivity, true);
            } else {
                clearEditor();
                tvCurrentFilePath.setText("没有找到可编辑的文本文件");
            }
        }
    }

    private void showAddActionDialog() {
        final String[] items = {"创建项目", "导入压缩包", "导入文件夹"};
        new AlertDialog.Builder(this)
            .setTitle("新建或导入")
            .setItems(items, (dialog, which) -> {
                if (which == 0) {
                    showCreateProjectDialog();
                } else if (which == 1) {
                    showStoragePicker("选择压缩包", false, true, new PathPickListener() {
                        @Override
                        public void onPicked(File path) {
                            importZipProject(path);
                        }
                    });
                } else {
                    toast("点击文件夹进入，长按文件夹才会选择导入。");
                    showStoragePicker("选择项目文件夹", true, false, new PathPickListener() {
                        @Override
                        public void onPicked(File path) {
                            importFolderProject(path);
                        }
                    });
                }
            })
            .show();
    }

    private void showCreateProjectDialog() {
        final LinearLayout container = buildDialogContainer();
        final EditText etAppName = createDialogField("应用名称", "我的应用", "LM打包工具");
        final EditText etPackageName = createDialogField("包名", "com.example.app", "com.LM.pack");
        final EditText etVersionName = createDialogField("版本号", "1.0.0", "1.0.0");
        final EditText etVersionCode = createDialogField("版本代码", "1", "1");
        etVersionCode.setInputType(InputType.TYPE_CLASS_NUMBER);
        final EditText etMinSdk = createDialogField("最低 SDK", "29", "29");
        etMinSdk.setInputType(InputType.TYPE_CLASS_NUMBER);
        final EditText etTargetSdk = createDialogField("目标 SDK", "36", "36");
        etTargetSdk.setInputType(InputType.TYPE_CLASS_NUMBER);
        final EditText etIconPath = createDialogField("图标路径", "点击下方按钮选择图片", "");
        final EditText etSplashPath = createDialogField("启动图路径", "点击下方按钮选择图片", "");

        container.addView(buildFieldLabel("创建一个可直接打包的 Android 空壳工程"));
        container.addView(etAppName);
        container.addView(etPackageName);
        container.addView(etVersionName);
        container.addView(etVersionCode);
        container.addView(etMinSdk);
        container.addView(etTargetSdk);
        container.addView(buildPickerRow("选择图标", etIconPath, false));
        container.addView(etIconPath);
        container.addView(buildPickerRow("选择启动图", etSplashPath, false));
        container.addView(etSplashPath);

        new AlertDialog.Builder(this)
            .setTitle("创建项目")
            .setView(container)
            .setPositiveButton("创建", (dialog, which) -> {
                String appName = etAppName.getText().toString().trim();
                String packageName = etPackageName.getText().toString().trim();
                String versionName = etVersionName.getText().toString().trim();
                int versionCode = parseInt(etVersionCode.getText().toString().trim(), 1);
                int minSdk = parseInt(etMinSdk.getText().toString().trim(), 29);
                int targetSdk = parseInt(etTargetSdk.getText().toString().trim(), 36);

                if (appName.length() == 0) {
                    toast("应用名称不能为空");
                    return;
                }
                if (!isValidPackageName(packageName)) {
                    toast("包名格式不合法");
                    return;
                }
                try {
                    ProjectConfig config = new ProjectConfig(
                        packageName,
                        appName,
                        minSdk,
                        targetSdk,
                        versionCode,
                        versionName.length() == 0 ? "1.0.0" : versionName,
                        etIconPath.getText().toString().trim(),
                        etSplashPath.getText().toString().trim()
                    );
                    File rootDir = projectManager.createShellProject(this, config, environmentManager.getProjectRootDir(appName));
                    logManager.appendLogLine("INFO", "项目已创建，可直接进入编辑页继续修改。");
                    logManager.appendKeyValue("INFO", "创建目录", rootDir.getAbsolutePath());
                    refreshProjectList();
                    openProject(projectManager.readProjectEntry(rootDir));
                } catch (Exception e) {
                    logManager.appendLogLine("ERROR", "创建项目失败：" + e);
                    logManager.appendKeyValue("ERROR", "目标目录", environmentManager.getProjectRootDir(appName));
                    appendExceptionDetailToLogs(e);
                    toast("创建项目失败");
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private LinearLayout buildPickerRow(String buttonText, final EditText targetField, final boolean zipOnly) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(10), 0, dp(10));

        Button button = new Button(this);
        button.setText(buttonText);
        button.setTextColor(Color.WHITE);
        button.setBackgroundColor(Color.parseColor("#2D7DFA"));
        row.addView(button, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView hint = new TextView(this);
        hint.setText(zipOnly ? "  仅可选择 zip 文件" : "  支持 png / jpg / webp");
        hint.setTextColor(Color.parseColor("#95A1B6"));
        hint.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(hint, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showStoragePicker("选择文件", false, zipOnly, new PathPickListener() {
                    @Override
                    public void onPicked(File path) {
                        targetField.setText(path.getAbsolutePath());
                    }
                });
            }
        });
        return row;
    }

    private LinearLayout buildDialogContainer() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(20), dp(10), dp(20), 0);
        return container;
    }

    private TextView buildFieldLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(Color.WHITE);
        label.setPadding(0, 0, 0, dp(12));
        return label;
    }

    private EditText createDialogField(String hint, String placeholder, String defaultValue) {
        EditText editText = new EditText(this);
        editText.setHint(hint + "，例如 " + placeholder);
        editText.setText(defaultValue);
        editText.setTextColor(Color.WHITE);
        editText.setHintTextColor(Color.GRAY);
        editText.setBackgroundColor(Color.parseColor("#1E1E1E"));
        editText.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(12);
        editText.setLayoutParams(params);
        return editText;
    }

    private void showStoragePicker(final String title, final boolean folderSelect, final boolean zipOnly, final PathPickListener listener) {
        final LinearLayout container = buildDialogContainer();
        final TextView tvPath = buildFieldLabel("");
        final ListView listView = new ListView(this);
        final ArrayList<File> visibleFiles = new ArrayList<File>();
        final ArrayList<String> labels = new ArrayList<String>();
        final ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, labels);
        final File[] currentDir = {environmentManager.getDefaultBrowseRootDir()};

        listView.setAdapter(adapter);
        container.addView(tvPath);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            TextView tip = buildFieldLabel("提示：当前未授予“管理所有文件”权限，可能无法浏览下载目录或根目录。你可以先授权，再回来继续导入。");
            tip.setTextColor(Color.parseColor("#8FA3BF"));
            container.addView(tip);

            Button btnGrant = new Button(this);
            btnGrant.setText("去授权文件访问");
            btnGrant.setTextColor(Color.WHITE);
            btnGrant.setBackgroundColor(Color.parseColor("#2563EB"));
            btnGrant.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                        toast("请在设置里开启文件访问权限，然后返回继续导入");
                    } catch (Exception e) {
                        try {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                            startActivity(intent);
                        } catch (Exception ignored) {
                            toast("无法打开授权页面，请到系统设置中手动开启文件访问权限");
                        }
                    }
                }
            });
            container.addView(btnGrant);
        }

        container.addView(listView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(420)));

        final AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(title)
            .setView(container)
            .setNegativeButton("取消", null)
            .create();

        final Runnable refresh = new Runnable() {
            @Override
            public void run() {
                populateFileBrowser(currentDir[0], visibleFiles, labels);
                tvPath.setText("当前位置：" + currentDir[0].getAbsolutePath() + (folderSelect ? "  · 长按文件夹可导入" : ""));
                adapter.notifyDataSetChanged();
            }
        };

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                File clicked = visibleFiles.get(position);
                if (clicked == null) {
                    File parentDir = currentDir[0].getParentFile();
                    if (parentDir != null) {
                        currentDir[0] = parentDir;
                        refresh.run();
                    }
                    return;
                }
                if (clicked.isDirectory()) {
                    currentDir[0] = clicked;
                    refresh.run();
                    return;
                }
                if (zipOnly && !projectManager.isZipFile(clicked)) {
                    toast("这里只能选择 zip 压缩包");
                    return;
                }
                listener.onPicked(clicked);
                dialog.dismiss();
            }
        });

        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                File clicked = visibleFiles.get(position);
                if (folderSelect && clicked != null && clicked.isDirectory()) {
                    listener.onPicked(clicked);
                    dialog.dismiss();
                    return true;
                }
                return false;
            }
        });

        refresh.run();
        dialog.show();
    }

    private void populateFileBrowser(File dir, ArrayList<File> visibleFiles, ArrayList<String> labels) {
        visibleFiles.clear();
        labels.clear();
        if (dir.getParentFile() != null) {
            visibleFiles.add(null);
            labels.add("..  返回上一级");
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        ArrayList<File> directories = new ArrayList<File>();
        ArrayList<File> normalFiles = new ArrayList<File>();
        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            if (file.isDirectory()) {
                directories.add(file);
            } else {
                normalFiles.add(file);
            }
        }
        Collections.sort(directories, new FileNameComparator());
        Collections.sort(normalFiles, new FileNameComparator());
        for (int i = 0; i < directories.size(); i++) {
            File directory = directories.get(i);
            visibleFiles.add(directory);
            labels.add("📁 " + directory.getName());
        }
        for (int i = 0; i < normalFiles.size(); i++) {
            File file = normalFiles.get(i);
            visibleFiles.add(file);
            labels.add("📄 " + file.getName());
        }
    }

    private void importZipProject(final File zipFile) {
        showProgressDialog("导入项目", "正在解压压缩包...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File tempRoot = new File(environmentManager.getImportTempDir(), sanitizeName(zipFile.getName()));
                    projectManager.extractZipToTemp(zipFile, tempRoot);
                    final File detectedRoot = projectManager.deepFindAndroidProject(tempRoot);
                    if (detectedRoot == null) {
                        throw new IllegalStateException("没有从压缩包中识别到有效的 Android 工程根目录。");
                    }
                    final File importedRoot = projectManager.importProject(detectedRoot, stripExtension(zipFile.getName()), environmentManager.getImportedProjectRootDir());
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            dismissProgressDialog();
                            logManager.appendLogLine("INFO", "压缩包已解压并导入到管理目录。");
                            logManager.appendKeyValue("INFO", "原压缩包保留", zipFile.getAbsolutePath());
                            logManager.appendKeyValue("INFO", "识别根目录", detectedRoot.getAbsolutePath());
                            logManager.appendKeyValue("INFO", "导入目录", importedRoot.getAbsolutePath());
                            refreshProjectList();
                            openProject(projectManager.readProjectEntry(importedRoot));
                        }
                    });
                } catch (final Exception e) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            dismissProgressDialog();
                            logManager.appendLogLine("ERROR", "导入压缩包失败：" + e);
                            appendExceptionDetailToLogs(e);
                            toast("导入压缩包失败");
                        }
                    });
                }
            }
        }).start();
    }

    private void importFolderProject(final File folder) {
        showProgressDialog("导入项目", "正在深度检查目录结构...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final File detectedRoot = projectManager.deepFindAndroidProject(folder);
                    if (detectedRoot == null) {
                        throw new IllegalStateException("没有找到完整的 Android 工程目录。");
                    }
                    final File importedRoot = projectManager.importProject(detectedRoot, detectedRoot.getName(), environmentManager.getImportedProjectRootDir());
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            dismissProgressDialog();
                            logManager.appendLogLine("INFO", "文件夹已导入到管理目录。");
                            logManager.appendKeyValue("INFO", "源目录", folder.getAbsolutePath());
                            logManager.appendKeyValue("INFO", "识别根目录", detectedRoot.getAbsolutePath());
                            logManager.appendKeyValue("INFO", "实际导入目录", importedRoot.getAbsolutePath());
                            refreshProjectList();
                            openProject(projectManager.readProjectEntry(importedRoot));
                        }
                    });
                } catch (final Exception e) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            dismissProgressDialog();
                            logManager.appendLogLine("ERROR", "导入文件夹失败：" + e);
                            appendExceptionDetailToLogs(e);
                            toast("导入文件夹失败");
                        }
                    });
                }
            }
        }).start();
    }

    private void loadProjectFiles() {
        fileTreeItems.clear();
        expandedDirs.clear();
        if (currentProject == null) {
            fileTreeAdapter.notifyDataSetChanged();
            return;
        }
        expandedDirs.add(currentProject.getProjectDir());
        expandedDirs.add(new File(currentProject.getProjectDir(), "app").getAbsolutePath());
        expandedDirs.add(new File(currentProject.getProjectDir(), "app/src").getAbsolutePath());
        expandedDirs.add(new File(currentProject.getProjectDir(), "app/src/main").getAbsolutePath());
        flattenDirectory(new File(currentProject.getProjectDir()), 0);
        fileTreeAdapter.notifyDataSetChanged();
    }

    private void flattenDirectory(File dir, int depth) {
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }
        fileTreeItems.add(new FileTreeItem(dir, depth, true));
        if (!expandedDirs.contains(dir.getAbsolutePath())) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        ArrayList<File> directories = new ArrayList<File>();
        ArrayList<File> normalFiles = new ArrayList<File>();
        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            if (shouldIgnore(file)) {
                continue;
            }
            if (file.isDirectory()) {
                directories.add(file);
            } else if (isTextEditableFile(file)) {
                normalFiles.add(file);
            }
        }
        Collections.sort(directories, new FileNameComparator());
        Collections.sort(normalFiles, new FileNameComparator());
        for (int i = 0; i < directories.size(); i++) {
            flattenDirectory(directories.get(i), depth + 1);
        }
        for (int i = 0; i < normalFiles.size(); i++) {
            fileTreeItems.add(new FileTreeItem(normalFiles.get(i), depth + 1, false));
        }
    }

    private boolean shouldIgnore(File file) {
        String name = file.getName();
        return ".git".equals(name) || ".gradle".equals(name) || "build".equals(name) || ".lmproject".equals(name);
    }

    private boolean isTextEditableFile(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".java")
            || name.endsWith(".kt")
            || name.endsWith(".xml")
            || name.endsWith(".gradle")
            || name.endsWith(".kts")
            || name.endsWith(".properties")
            || name.endsWith(".txt")
            || name.endsWith(".md")
            || "gradlew".equals(name);
    }

    private void handleFileTreeClick(int position) {
        if (position < 0 || position >= fileTreeItems.size()) {
            return;
        }
        FileTreeItem item = fileTreeItems.get(position);
        if (item.isDirectory) {
            String path = item.file.getAbsolutePath();
            if (expandedDirs.contains(path)) {
                expandedDirs.remove(path);
            } else {
                expandedDirs.add(path);
            }
            fileTreeItems.clear();
            flattenDirectory(new File(currentProject.getProjectDir()), 0);
            fileTreeAdapter.notifyDataSetChanged();
            return;
        }
        openTextFile(item.file);
        hideFileDrawer();
    }

    private void openTextFile(File file) {
        if (file == null) {
            return;
        }
        if (!sameFile(file, currentOpenFile)) {
            saveCurrentFile();
        }
        loadFileIntoEditor(file, true);
    }

    private void loadFileIntoEditor(File file, boolean trackTab) {
        try {
            String content = projectManager.readText(file);
            currentOpenFile = file;
            etEditor.setFileName(file.getName());
            etEditor.setText(content);
            tvCurrentFilePath.setText(buildDisplayPath(file));
            suggestionCard.setVisibility(View.GONE);
            if (trackTab) {
                editorTabManager.openTab(file);
            } else {
                editorTabManager.activate(file);
            }
        } catch (Exception e) {
            logManager.appendLogLine("ERROR", "打开文件失败：" + e.getMessage());
            toast("打开文件失败");
        }
    }

    private void clearEditor() {
        currentOpenFile = null;
        etEditor.setFileName("");
        etEditor.setText("");
        tvCurrentFilePath.setText("请选择一个文件");
        suggestionCard.setVisibility(View.GONE);
    }

    private String buildDisplayPath(File file) {
        if (file == null) {
            return "请选择一个文件";
        }
        if (currentProject == null) {
            return file.getAbsolutePath();
        }
        String projectRoot = currentProject.getProjectDir();
        String fullPath = file.getAbsolutePath();
        if (fullPath.startsWith(projectRoot)) {
            String relative = fullPath.substring(projectRoot.length());
            if (relative.startsWith(File.separator)) {
                relative = relative.substring(1);
            }
            return currentProject.getProjectName() + "  /  " + relative;
        }
        return fullPath;
    }

    private void saveCurrentFile() {
        if (currentOpenFile == null) {
            return;
        }
        try {
            projectManager.writeText(currentOpenFile, etEditor.getText().toString());
            logManager.appendKeyValue("INFO", "已保存文件", currentOpenFile.getAbsolutePath());
        } catch (Exception e) {
            logManager.appendLogLine("ERROR", "保存文件失败：" + e.getMessage());
            toast("保存失败");
        }
    }

    private void toggleFileDrawer() {
        if (fileDrawer.getVisibility() == View.VISIBLE) {
            hideFileDrawer();
        } else {
            fileDrawer.setVisibility(View.VISIBLE);
            btnToggleFiles.setText("收起");
        }
    }

    private void hideFileDrawer() {
        fileDrawer.setVisibility(View.GONE);
        btnToggleFiles.setText("目录");
    }

    private void detectAndBuild() {
        if (currentProject == null) {
            toast("请先打开一个项目");
            return;
        }
        if (isBuildRunning) {
            toast("已有打包任务正在执行");
            return;
        }
        saveCurrentFile();
        showProgressDialog("检查错误代码", "正在进行打包前检查...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final ArrayList<BuildIssue> issues = preflightChecker.collectProjectIssues(
                    new File(currentProject.getProjectDir()),
                    projectPrepared,
                    environmentState,
                    selectedJdkIndex,
                    selectedNdkIndex
                );
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!issues.isEmpty()) {
                            dismissProgressDialog();
                            lastBuildIssues.clear();
                            lastBuildIssues.addAll(issues);
                            updateBugButtonState();
                            logManager.appendLogLine("ERROR", "打包前检查未通过，已停止构建。");
                            appendIssueListToLogs("预检查发现的问题", lastBuildIssues);
                            showIssueDialog("检查发现错误", lastBuildIssues);
                            return;
                        }
                        updateProgressDialog("检查通过，开始调用 Gradle 构建...");
                        startRealBuild();
                    }
                });
            }
        }).start();
    }

    private void startRealBuild() {
        isBuildRunning = true;
        buildManager.runGradleBuild(
            currentProject.getProjectDir(),
            environmentState.getInstalledJdkDir(),
            environmentState.getAndroidSdkDir(),
            environmentState.getInstalledNdkDir(),
            EnvironmentManager.JDK_NAMES[selectedJdkIndex],
            new BuildManager.BuildListener() {
                @Override
                public void onLogLine(final String line) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            appendBuildOutput(line);
                        }
                    });
                }

                @Override
                public void onFinished(final BuildResult result) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            dismissProgressDialog();
                            isBuildRunning = false;
                            lastBuildIssues.clear();
                            lastBuildIssues.addAll(result.getIssues());
                            updateBugButtonState();
                            if (result.isSuccess()) {
                                logManager.appendLogLine("INFO", result.getMessage());
                                if (result.getApkPath() != null && result.getApkPath().length() > 0) {
                                    logManager.appendKeyValue("INFO", "APK 输出", result.getApkPath());
                                }
                                toast("打包成功");
                            } else {
                                logManager.appendLogLine("ERROR", result.getMessage());
                                if (!lastBuildIssues.isEmpty()) {
                                    appendIssueListToLogs("构建失败的问题列表", lastBuildIssues);
                                    showIssueDialog("构建失败，可点击定位错误", lastBuildIssues);
                                } else {
                                    toast("打包失败");
                                }
                            }
                        }
                    });
                }
            }
        );
    }

    private void showIssueChooser() {
        if (lastBuildIssues.isEmpty()) {
            toast("当前没有错误记录");
            return;
        }
        showIssueDialog("错误列表", lastBuildIssues);
    }

    private void appendIssueListToLogs(String title, ArrayList<BuildIssue> issues) {
        if (issues == null || issues.isEmpty()) {
            return;
        }
        logManager.appendLogLine("ERROR", "---- " + title + "（" + issues.size() + "条）----");
        for (int i = 0; i < issues.size(); i++) {
            BuildIssue issue = issues.get(i);
            logManager.appendLogLine("ERROR", issue.getDisplayText());
            String suggestion = issue.getSuggestion();
            if (suggestion != null && suggestion.length() > 0) {
                logManager.appendLogLine("WARN", "建议: " + suggestion);
            }
        }
        logManager.appendLogLine("ERROR", "---- 结束 ----");
    }

    private void showIssueDialog(String title, final ArrayList<BuildIssue> issues) {
        String[] items = new String[issues.size()];
        for (int i = 0; i < issues.size(); i++) {
            items[i] = issues.get(i).getDisplayText();
        }
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(items, (dialog, which) -> openIssue(issues.get(which)))
            .setNegativeButton("关闭", null)
            .show();
    }

    private void openIssue(BuildIssue issue) {
        File issueFile = resolveIssueFile(issue.getFilePath());
        if (issueFile != null && issueFile.exists() && issueFile.isFile() && isTextEditableFile(issueFile)) {
            openTextFile(issueFile);
            if (issue.getLineNumber() > 0) {
                moveCursorToLine(issue.getLineNumber());
            }
        }
        suggestionCard.setVisibility(View.VISIBLE);
        tvIssueTitle.setText(issue.getMessage());
        tvIssueFix.setText(issue.getSuggestion());
        currentCopiedFix = issue.getSuggestion();
        toast("已定位到错误代码");
    }

    private File resolveIssueFile(String path) {
        if (path == null || path.length() == 0 || currentProject == null) {
            return null;
        }
        File direct = new File(path);
        if (direct.exists()) {
            return direct;
        }
        File relative = new File(currentProject.getProjectDir(), path);
        if (relative.exists()) {
            return relative;
        }
        return findFileBySuffix(new File(currentProject.getProjectDir()), new File(path).getName());
    }

    private File findFileBySuffix(File dir, String name) {
        if (!dir.exists() || !dir.isDirectory()) {
            return null;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return null;
        }
        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            if (file.isDirectory()) {
                File found = findFileBySuffix(file, name);
                if (found != null) {
                    return found;
                }
            } else if (file.getName().equals(name)) {
                return file;
            }
        }
        return null;
    }

    private void moveCursorToLine(int lineNumber) {
        String text = etEditor.getText().toString();
        int line = 1;
        int index = 0;
        while (index < text.length() && line < lineNumber) {
            if (text.charAt(index) == '\n') {
                line++;
            }
            index++;
        }
        int end = index;
        while (end < text.length() && text.charAt(end) != '\n') {
            end++;
        }
        etEditor.requestFocus();
        etEditor.setSelection(index, Math.min(end, etEditor.getText().length()));
    }

    private void updateBugButtonState() {
        if (lastBuildIssues.isEmpty()) {
            btnBug.setBackgroundResource(R.drawable.bg_button_ghost);
        } else {
            btnBug.setBackgroundResource(R.drawable.bg_button_warn);
        }
    }

    private void appendExceptionDetailToLogs(Exception e) {
        if (e == null) {
            return;
        }
        Throwable t = e;
        int depth = 0;
        while (t != null && depth < 4) {
            logManager.appendLogLine("ERROR", "原因: " + t.getClass().getSimpleName() + " - " + safeText(t.getMessage(), ""));
            t = t.getCause();
            depth++;
        }
        logManager.appendLogLine("WARN", "提示: 如果报权限或无法创建目录，通常与 Android 11+ 的存储限制有关。");
    }

    private void appendBuildOutput(String line) {
        String lower = line.toLowerCase();
        if (lower.contains("error") || line.contains("失败")) {
            logManager.appendLogLine("ERROR", line);
        } else if (lower.contains("warning") || lower.contains("warn")) {
            logManager.appendLogLine("WARN", line);
        } else {
            logManager.appendLogLine("INFO", line);
        }
    }

    private void showProgressDialog(String title, String message) {
        dismissProgressDialog();
        progressDialog = new ProgressDialog(this);
        progressDialog.setTitle(title);
        progressDialog.setMessage(message);
        progressDialog.setCancelable(false);
        progressDialog.show();
    }

    private void updateProgressDialog(String message) {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.setMessage(message);
        }
    }

    private void dismissProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    private boolean isValidPackageName(String packageName) {
        return packageName.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+");
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return fallback;
        }
    }

    private String safeText(String value, String fallback) {
        if (value == null || value.length() == 0) {
            return fallback;
        }
        return value;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable roundedDrawable(String fillColor, String strokeColor, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.parseColor(fillColor));
        drawable.setCornerRadius(dp((int) radiusDp));
        if (strokeColor != null && strokeColor.length() > 0) {
            drawable.setStroke(1, Color.parseColor(strokeColor));
        }
        return drawable;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private String stripExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex <= 0) {
            return fileName;
        }
        return fileName.substring(0, dotIndex);
    }

    private String sanitizeName(String value) {
        return value.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private boolean sameFile(File first, File second) {
        return first != null && second != null && first.getAbsolutePath().equals(second.getAbsolutePath());
    }

    private class ProjectAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return projectEntries.size();
        }

        @Override
        public Object getItem(int position) {
            return projectEntries.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ProjectCardHolder holder;
            if (convertView == null) {
                LinearLayout card = new LinearLayout(MainActivity.this);
                card.setOrientation(LinearLayout.HORIZONTAL);
                card.setGravity(Gravity.CENTER_VERTICAL);
                card.setPadding(dp(14), dp(14), dp(14), dp(14));
                card.setBackground(roundedDrawable("#182231", "#263246", 12));

                ImageView iconView = new ImageView(MainActivity.this);
                LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(52), dp(52));
                iconParams.rightMargin = dp(14);
                iconView.setLayoutParams(iconParams);
                iconView.setBackground(roundedDrawable("#0F141B", "#2A3850", 10));
                iconView.setPadding(dp(8), dp(8), dp(8), dp(8));
                iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                card.addView(iconView);

                LinearLayout textColumn = new LinearLayout(MainActivity.this);
                textColumn.setOrientation(LinearLayout.VERTICAL);
                textColumn.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                TextView title = new TextView(MainActivity.this);
                title.setTextColor(Color.parseColor("#F3F7FD"));
                title.setTextSize(14f);
                textColumn.addView(title);

                TextView sub = new TextView(MainActivity.this);
                sub.setTextColor(Color.parseColor("#A2B0C3"));
                sub.setTextSize(11f);
                sub.setPadding(0, dp(2), 0, 0);
                textColumn.addView(sub);

                TextView meta = new TextView(MainActivity.this);
                meta.setTextColor(Color.parseColor("#8FB6FF"));
                meta.setTextSize(11f);
                meta.setPadding(0, dp(4), 0, 0);
                textColumn.addView(meta);

                card.addView(textColumn);
                holder = new ProjectCardHolder(iconView, title, sub, meta);
                card.setTag(holder);
                convertView = card;
            } else {
                holder = (ProjectCardHolder) convertView.getTag();
            }

            ProjectEntry entry = projectEntries.get(position);
            holder.title.setText(entry.getProjectName());
            holder.sub.setText(safeText(entry.getPackageName(), "未识别包名"));
            holder.meta.setText(entry.getMode() + "  ·  v" + safeText(entry.getVersionName(), "1.0"));
            Bitmap bitmap = null;
            if (entry.getIconPath() != null && entry.getIconPath().length() > 0 && !entry.getIconPath().endsWith(".xml")) {
                bitmap = BitmapFactory.decodeFile(entry.getIconPath());
            }
            if (bitmap != null) {
                holder.icon.setImageBitmap(bitmap);
            } else {
                holder.icon.setImageResource(android.R.drawable.sym_def_app_icon);
            }
            return convertView;
        }
    }

    private class FileTreeAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return fileTreeItems.size();
        }

        @Override
        public Object getItem(int position) {
            return fileTreeItems.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView textView;
            if (convertView == null) {
                textView = new TextView(MainActivity.this);
                textView.setTextColor(Color.parseColor("#F3F7FD"));
                textView.setPadding(dp(12), dp(10), dp(12), dp(10));
                textView.setTextSize(12f);
                textView.setBackground(roundedDrawable("#151B24", "", 8));
                convertView = textView;
            } else {
                textView = (TextView) convertView;
            }
            FileTreeItem item = fileTreeItems.get(position);
            String prefix = item.isDirectory ? (expandedDirs.contains(item.file.getAbsolutePath()) ? "⌄  " : "›  ") : "·  ";
            textView.setPadding(dp(14) + item.depth * dp(18), dp(10), dp(12), dp(10));
            textView.setText(prefix + item.file.getName());
            textView.setTextColor(item.isDirectory ? Color.parseColor("#F3F7FD") : Color.parseColor("#B8C9E0"));
            return convertView;
        }
    }

    private static class ProjectCardHolder {
        final ImageView icon;
        final TextView title;
        final TextView sub;
        final TextView meta;

        ProjectCardHolder(ImageView icon, TextView title, TextView sub, TextView meta) {
            this.icon = icon;
            this.title = title;
            this.sub = sub;
            this.meta = meta;
        }
    }

    private static class FileTreeItem {
        final File file;
        final int depth;
        final boolean isDirectory;

        FileTreeItem(File file, int depth, boolean isDirectory) {
            this.file = file;
            this.depth = depth;
            this.isDirectory = isDirectory;
        }
    }

    private static class FileNameComparator implements Comparator<File> {
        @Override
        public int compare(File a, File b) {
            return a.getName().compareToIgnoreCase(b.getName());
        }
    }

    private interface PathPickListener {
        void onPicked(File path);
    }
}
