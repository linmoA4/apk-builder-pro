package com.LM.pack;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
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
import com.LM.pack.env.EnvironmentManager;
import com.LM.pack.env.ToolchainInstaller;
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
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.SAXParseException;

public class MainActivity extends Activity {

    private static final String[] CORE_TOOL_ITEMS = {
        "Gradle 官方发行版\nhttps://services.gradle.org/distributions/",
        "Maven 中央仓库\nhttps://mvnrepository.com/",
        "Google Maven 仓库\nhttps://maven.google.com/"
    };

    private static final String[] CROSS_PLATFORM_ITEMS = {
        "Flutter 官方中文文档与下载\nhttps://flutterchina.club/",
        "Flutter 清华镜像克隆\ngit clone -b master https://mirrors.tuna.tsinghua.edu.cn/git/flutter-sdk.git",
        "Node.js 官方下载\nhttps://nodejs.org/",
        "npm 淘宝镜像设置\nnpm config set registry https://registry.npm.taobao.org",
        "Xamarin Android Workload\ndotnet workload install android",
        "Xamarin 开源地址\nhttps://gitcode.com/gh_mirrors/xa/xamarin-android",
        "uni-app 脚手架\nvue create -p dcloudio/uni-preset-vue my-first-uni-app",
        "uni-app 源码与文档\nhttps://gitcode.com/dcloud/uni-app"
    };

    private static final String[] TEST_TOOL_ITEMS = {
        "JUnit 4 发布页\nhttps://github.com/junit-team/junit4/releases/",
        "JUnit 5 发布页\nhttps://github.com/junit-team/junit5/releases/",
        "Android 模拟器 QEMU 核心\nhttps://qemu.weilnetz.de/w64/"
    };

    private static final String[] DEVOPS_TOOL_ITEMS = {
        "Git 官方下载\nhttps://git-scm.com/",
        "Postman 官方下载\nhttps://www.postman.com/downloads/"
    };

    private static final String[] MIRROR_COMMAND_ITEMS = {
        "npm registry\nnpm config set registry https://registry.npm.taobao.org",
        "npm disturl\nnpm config set disturl https://npm.taobao.org/dist",
        "Flutter PUB_HOSTED_URL\nPUB_HOSTED_URL=https://pub.flutter-io.cn",
        "Flutter FLUTTER_STORAGE_BASE_URL\nFLUTTER_STORAGE_BASE_URL=https://storage.flutter-io.cn"
    };

    private Handler handler;
    private ProgressDialog progressDialog;

    private LogManager logManager;
    private EnvironmentManager environmentManager;
    private ProjectManager projectManager;
    private BuildManager buildManager;
    private ToolchainInstaller toolchainInstaller;
    private EnvironmentState environmentState;

    private LinearLayout homePane;
    private LinearLayout editorPane;
    private LinearLayout emptyState;
    private LinearLayout fileDrawer;
    private LinearLayout suggestionCard;
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
    private EditText etEditor;

    private final ArrayList<ProjectEntry> projectEntries = new ArrayList<ProjectEntry>();
    private final ArrayList<FileTreeItem> fileTreeItems = new ArrayList<FileTreeItem>();
    private final ArrayList<String> expandedDirs = new ArrayList<String>();
    private final ArrayList<BuildIssue> lastBuildIssues = new ArrayList<BuildIssue>();

    private ProjectAdapter projectAdapter;
    private FileTreeAdapter fileTreeAdapter;
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
        environmentManager = new EnvironmentManager(sharedPreferences);
        projectManager = new ProjectManager();
        buildManager = new BuildManager();
        toolchainInstaller = new ToolchainInstaller(this, environmentManager);
        environmentState = environmentManager.loadState();

        bindViews();
        initAdapters();
        bindEvents();
        appendStartupLogs();
        refreshProjectList();
        showHome();
    }

    private void bindViews() {
        homePane = (LinearLayout) findViewById(R.id.homePane);
        editorPane = (LinearLayout) findViewById(R.id.editorPane);
        emptyState = (LinearLayout) findViewById(R.id.emptyState);
        fileDrawer = (LinearLayout) findViewById(R.id.fileDrawer);
        suggestionCard = (LinearLayout) findViewById(R.id.suggestionCard);
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
        etEditor = (EditText) findViewById(R.id.etEditor);

        TextView tvLogs = (TextView) findViewById(R.id.tvLogs);
        ScrollView logScrollView = (ScrollView) findViewById(R.id.logScrollView);
        logManager = new LogManager(tvLogs, logScrollView);

        ViewGroup.LayoutParams layoutParams = fileDrawer.getLayoutParams();
        layoutParams.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.46f);
        fileDrawer.setLayoutParams(layoutParams);
    }

    private void initAdapters() {
        projectAdapter = new ProjectAdapter();
        fileTreeAdapter = new FileTreeAdapter();
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
                showSettingsDialog();
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
        logManager.appendLogLine("INFO", "新版首页已切换为项目卡片模式，可从右下角创建或导入工程。");
        logManager.appendKeyValue("INFO", "当前 JDK", EnvironmentManager.JDK_NAMES[selectedJdkIndex]);
        logManager.appendKeyValue("INFO", "当前 NDK", EnvironmentManager.NDK_NAMES[selectedNdkIndex]);
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
        tvToolbarTitle.setText("APK Builder Pro");
        hideFileDrawer();
    }

    private void openProject(ProjectEntry entry) {
        currentProject = entry;
        currentOpenFile = null;
        projectPrepared = true;
        homePane.setVisibility(View.GONE);
        editorPane.setVisibility(View.VISIBLE);
        btnBackHome.setVisibility(View.VISIBLE);
        btnFabAdd.setVisibility(View.GONE);
        tvToolbarTitle.setText(entry.getProjectName());
        tvEditorProject.setText(entry.getProjectName() + "  ·  " + safeText(entry.getPackageName(), "未识别包名"));
        tvCurrentFilePath.setText(entry.getProjectDir());
        suggestionCard.setVisibility(View.GONE);
        lastBuildIssues.clear();
        updateBugButtonState();
        loadProjectFiles();
        openDefaultEditorFile(entry);
        logManager.appendKeyValue("INFO", "已打开项目", entry.getProjectDir());
    }

    private void openDefaultEditorFile(ProjectEntry entry) {
        File manifestFile = new File(entry.getProjectDir(), "app/src/main/AndroidManifest.xml");
        File buildGradle = new File(entry.getProjectDir(), "app/build.gradle");
        String packageName = safeText(entry.getPackageName(), "");
        File mainJava = new File(entry.getProjectDir(), "app/src/main/java/" + packageName.replace('.', '/') + "/MainActivity.java");
        if (mainJava.exists()) {
            openTextFile(mainJava);
        } else if (manifestFile.exists()) {
            openTextFile(manifestFile);
        } else if (buildGradle.exists()) {
            openTextFile(buildGradle);
        } else {
            File fallbackMainActivity = findFileBySuffix(new File(entry.getProjectDir()), "MainActivity.java");
            if (fallbackMainActivity != null) {
                openTextFile(fallbackMainActivity);
                return;
            }
            etEditor.setText("");
            tvCurrentFilePath.setText("没有找到可编辑的文本文件");
        }
    }

    private void showAddActionDialog() {
        final String[] items = {"创建项目", "导入压缩包", "导入文件夹"};
        new AlertDialog.Builder(this)
            .setTitle("新建或导入")
            .setItems(items, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
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
            .setPositiveButton("创建", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    try {
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
                        File rootDir = projectManager.createShellProject(MainActivity.this, config, environmentManager.getProjectRootDir(appName));
                        logManager.appendLogLine("INFO", "项目已创建，可直接进入编辑页继续修改。");
                        logManager.appendKeyValue("INFO", "创建目录", rootDir.getAbsolutePath());
                        refreshProjectList();
                        openProject(projectManager.readProjectEntry(rootDir));
                    } catch (Exception e) {
                        logManager.appendLogLine("ERROR", "创建项目失败：" + e.getMessage());
                        toast("创建项目失败");
                    }
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private LinearLayout buildPickerRow(String buttonText, final EditText targetField, final boolean zipOnly) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 10, 0, 10);

        Button button = new Button(this);
        button.setText(buttonText);
        button.setTextColor(Color.WHITE);
        button.setBackgroundColor(Color.parseColor("#2D7DFA"));
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        row.addView(button, buttonParams);

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
        container.setPadding(40, 20, 40, 0);
        return container;
    }

    private TextView buildFieldLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(Color.WHITE);
        label.setPadding(0, 0, 0, 12);
        return label;
    }

    private EditText createDialogField(String hint, String placeholder, String defaultValue) {
        EditText editText = new EditText(this);
        editText.setHint(hint + "，例如 " + placeholder);
        editText.setText(defaultValue);
        editText.setTextColor(Color.WHITE);
        editText.setHintTextColor(Color.GRAY);
        editText.setBackgroundColor(Color.parseColor("#1E1E1E"));
        editText.setPadding(20, 20, 20, 20);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = 12;
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
        final File[] currentDir = {new File("/storage/emulated/0")};

        listView.setAdapter(adapter);
        container.addView(tvPath);
        container.addView(listView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 900));

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
                        throw new IllegalStateException("深度检查后仍未找到有效 Android 架构。");
                    }
                    final File importedRoot = projectManager.importProject(detectedRoot, stripExtension(zipFile.getName()), environmentManager.getImportedProjectRootDir());
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            dismissProgressDialog();
                            logManager.appendLogLine("INFO", "压缩包已解压并导入到 android/data 目录。");
                            logManager.appendKeyValue("INFO", "原压缩包保留", zipFile.getAbsolutePath());
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
                            logManager.appendLogLine("ERROR", "导入压缩包失败：" + e.getMessage());
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
                            logManager.appendLogLine("INFO", "文件夹已导入到 android/data 目录。");
                            logManager.appendKeyValue("INFO", "源目录", folder.getAbsolutePath());
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
                            logManager.appendLogLine("ERROR", "导入文件夹失败：" + e.getMessage());
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
        saveCurrentFile();
        openTextFile(item.file);
        hideFileDrawer();
    }

    private void openTextFile(File file) {
        try {
            String content = projectManager.readText(file);
            currentOpenFile = file;
            etEditor.setText(content);
            tvCurrentFilePath.setText(file.getAbsolutePath());
        } catch (Exception e) {
            logManager.appendLogLine("ERROR", "打开文件失败：" + e.getMessage());
            toast("打开文件失败");
        }
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
        }
    }

    private void hideFileDrawer() {
        fileDrawer.setVisibility(View.GONE);
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
                final ArrayList<BuildIssue> issues = collectProjectErrors();
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!issues.isEmpty()) {
                            dismissProgressDialog();
                            lastBuildIssues.clear();
                            lastBuildIssues.addAll(issues);
                            updateBugButtonState();
                            logManager.appendLogLine("ERROR", "打包前检查未通过，已停止构建。");
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

    private ArrayList<BuildIssue> collectProjectErrors() {
        ArrayList<BuildIssue> issues = new ArrayList<BuildIssue>();
        if (!projectPrepared || currentProject == null) {
            issues.add(new BuildIssue("项目", -1, "当前没有可打包的项目。", "先从首页创建或导入工程，再进入编辑页打包。"));
            return issues;
        }
        if (!environmentManager.isSelectedJdkInstalled(selectedJdkIndex, environmentState)) {
            issues.add(new BuildIssue("JDK", -1, "当前所选 JDK 未安装。", "先进入设置安装或登记 JDK 目录。"));
        }
        if (!environmentManager.isSelectedNdkInstalled(selectedNdkIndex, environmentState)) {
            issues.add(new BuildIssue("NDK", -1, "当前所选 NDK 未安装。", "先进入设置安装或登记 NDK 目录。"));
        }
        File projectDir = new File(currentProject.getProjectDir());
        if (!projectDir.exists()) {
            issues.add(new BuildIssue(projectDir.getAbsolutePath(), -1, "项目目录不存在。", "重新导入项目，确保目录仍然存在。"));
            return issues;
        }
        validateRequiredStructure(projectDir, issues);
        validateXmlFile(new File(projectDir, "app/src/main/AndroidManifest.xml"), issues);
        validateXmlDirectory(new File(projectDir, "app/src/main/res"), issues);
        validateSourceDirectory(new File(projectDir, "app/src/main/java"), issues);
        return issues;
    }

    private void validateRequiredStructure(File projectDir, ArrayList<BuildIssue> issues) {
        File gradlew = new File(projectDir, "gradlew");
        File appGradle = new File(projectDir, "app/build.gradle");
        File manifest = new File(projectDir, "app/src/main/AndroidManifest.xml");
        if (!gradlew.exists()) {
            issues.add(new BuildIssue(gradlew.getAbsolutePath(), -1, "缺少 gradlew。", "补齐 Gradle Wrapper 后再打包。"));
        }
        if (!appGradle.exists()) {
            issues.add(new BuildIssue(appGradle.getAbsolutePath(), -1, "缺少 app/build.gradle。", "检查导入目录是否选中了真正的 Android 工程根目录。"));
        }
        if (!manifest.exists()) {
            issues.add(new BuildIssue(manifest.getAbsolutePath(), -1, "缺少 AndroidManifest.xml。", "确认项目结构至少包含 `app/src/main/AndroidManifest.xml`。"));
        }
    }

    private void validateXmlDirectory(File dir, ArrayList<BuildIssue> issues) {
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            if (file.isDirectory()) {
                validateXmlDirectory(file, issues);
            } else if (file.getName().toLowerCase().endsWith(".xml")) {
                validateXmlFile(file, issues);
            }
        }
    }

    private void validateXmlFile(File file, ArrayList<BuildIssue> issues) {
        if (!file.exists()) {
            return;
        }
        try {
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file);
        } catch (SAXParseException e) {
            issues.add(new BuildIssue(file.getAbsolutePath(), e.getLineNumber(), e.getMessage(), "检查 XML 标签是否闭合、属性引号是否完整。"));
        } catch (Exception e) {
            issues.add(new BuildIssue(file.getAbsolutePath(), -1, e.getMessage(), "检查 XML 结构和资源引用。"));
        }
    }

    private void validateSourceDirectory(File dir, ArrayList<BuildIssue> issues) {
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            if (file.isDirectory()) {
                validateSourceDirectory(file, issues);
            } else if (isTextEditableFile(file)) {
                validateTextFile(file, issues);
            }
        }
    }

    private void validateTextFile(File file, ArrayList<BuildIssue> issues) {
        try {
            String content = projectManager.readText(file);
            if (countChar(content, '{') != countChar(content, '}')) {
                issues.add(new BuildIssue(file.getAbsolutePath(), -1, "大括号数量不匹配。", "检查是否有未闭合的代码块或多余的 `}`。"));
            }
            if (countChar(content, '(') != countChar(content, ')')) {
                issues.add(new BuildIssue(file.getAbsolutePath(), -1, "小括号数量不匹配。", "检查方法调用和条件语句是否缺少括号。"));
            }
            if (content.indexOf('\u0000') >= 0) {
                issues.add(new BuildIssue(file.getAbsolutePath(), -1, "文件内容异常。", "重新保存该文件为 UTF-8 文本。"));
            }
        } catch (Exception e) {
            issues.add(new BuildIssue(file.getAbsolutePath(), -1, "读取文件失败：" + e.getMessage(), "确认文件可读且不是二进制格式。"));
        }
    }

    private int countChar(String content, char target) {
        int count = 0;
        for (int i = 0; i < content.length(); i++) {
            if (content.charAt(i) == target) {
                count++;
            }
        }
        return count;
    }

    private void startRealBuild() {
        isBuildRunning = true;
        buildManager.runGradleBuild(
            currentProject.getProjectDir(),
            environmentState.getInstalledJdkDir(),
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

    private void showIssueDialog(String title, final ArrayList<BuildIssue> issues) {
        String[] items = new String[issues.size()];
        for (int i = 0; i < issues.size(); i++) {
            items[i] = issues.get(i).getDisplayText();
        }
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(items, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    openIssue(issues.get(which));
                }
            })
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
            btnBug.setBackgroundColor(Color.parseColor("#3E465A"));
        } else {
            btnBug.setBackgroundColor(Color.parseColor("#E5484D"));
        }
    }

    private void showSettingsDialog() {
        String[] items = {
            "选择 JDK 版本",
            "安装当前 JDK",
            "选择 NDK 版本",
            "安装当前 NDK",
            "登记 JDK 路径",
            "登记 NDK 路径",
            "查看环境状态",
            "查看安装目录规划",
            "核心构建工具",
            "跨平台开发框架",
            "测试与仿真环境",
            "版本控制与调试",
            "国内镜像命令",
            "输出全部工具链资料"
        };

        new AlertDialog.Builder(this)
            .setTitle("工具链设置")
            .setItems(items, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (which == 0) {
                        showJdkChooser();
                    } else if (which == 1) {
                        installSelectedJdk();
                    } else if (which == 2) {
                        showNdkChooser();
                    } else if (which == 3) {
                        installSelectedNdk();
                    } else if (which == 4) {
                        showRegisterEnvironmentDialog(true);
                    } else if (which == 5) {
                        showRegisterEnvironmentDialog(false);
                    } else if (which == 6) {
                        showEnvironmentStatus();
                    } else if (which == 7) {
                        showInstallDirectoryPlan();
                    } else if (which == 8) {
                        showInfoListDialog("核心构建与包管理工具", CORE_TOOL_ITEMS, "核心工具");
                    } else if (which == 9) {
                        showInfoListDialog("跨平台开发框架环境", CROSS_PLATFORM_ITEMS, "跨平台");
                    } else if (which == 10) {
                        showInfoListDialog("测试与仿真环境", TEST_TOOL_ITEMS, "测试环境");
                    } else if (which == 11) {
                        showInfoListDialog("版本控制与接口调试", DEVOPS_TOOL_ITEMS, "调试工具");
                    } else if (which == 12) {
                        showInfoListDialog("国内镜像与加速命令", MIRROR_COMMAND_ITEMS, "镜像命令");
                    } else if (which == 13) {
                        dumpAllToolResources();
                    }
                }
            })
            .setNegativeButton("关闭", null)
            .show();
    }

    private void showInfoListDialog(final String title, final String[] items, final String tag) {
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(items, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    logManager.appendLogLine("INFO", "正在输出 " + tag + " 资料。");
                    logManager.appendCatalogItem(tag, items[which]);
                }
            })
            .setPositiveButton("全部输出到日志", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    logManager.appendCatalogGroup(tag, items);
                }
            })
            .setNegativeButton("关闭", null)
            .show();
    }

    private void showRegisterEnvironmentDialog(final boolean forJdk) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(40, 20, 40, 0);

        final EditText etPath = new EditText(this);
        etPath.setHint(forJdk ? "输入已安装 JDK 目录" : "输入已安装 NDK 目录");
        etPath.setText(forJdk ? environmentState.getInstalledJdkDir() : environmentState.getInstalledNdkDir());
        etPath.setTextColor(Color.WHITE);
        etPath.setHintTextColor(Color.GRAY);
        etPath.setBackgroundColor(Color.parseColor("#1E1E1E"));
        etPath.setPadding(20, 20, 20, 20);

        container.addView(etPath);

        new AlertDialog.Builder(this)
            .setTitle(forJdk ? "登记 JDK 路径" : "登记 NDK 路径")
            .setView(container)
            .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    String path = etPath.getText().toString().trim();
                    if (path.length() == 0) {
                        logManager.appendLogLine("ERROR", (forJdk ? "JDK" : "NDK") + " 路径不能为空。");
                        return;
                    }
                    if (!environmentManager.isExistingDirectory(path)) {
                        logManager.appendLogLine("ERROR", (forJdk ? "JDK" : "NDK") + " 目录不存在，无法登记为已安装环境。");
                        return;
                    }
                    if (forJdk) {
                        environmentState = environmentManager.saveInstalledJdk(EnvironmentManager.JDK_NAMES[selectedJdkIndex], path);
                        logManager.appendLogLine("INFO", "JDK 已登记为已安装。");
                        logManager.appendKeyValue("INFO", "JDK 路径", path);
                    } else {
                        environmentState = environmentManager.saveInstalledNdk(EnvironmentManager.NDK_NAMES[selectedNdkIndex], path);
                        logManager.appendLogLine("INFO", "NDK 已登记为已安装。");
                        logManager.appendKeyValue("INFO", "NDK 路径", path);
                    }
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void showEnvironmentStatus() {
        logManager.appendLogLine("INFO", "正在输出当前环境状态。");
        logManager.appendKeyValue("INFO", "已登记 JDK", safeText(environmentState.getInstalledJdkName(), "未登记"));
        logManager.appendKeyValue("INFO", "JDK 目录", safeText(environmentState.getInstalledJdkDir(), "未登记"));
        logManager.appendKeyValue("INFO", "已登记 NDK", safeText(environmentState.getInstalledNdkName(), "未登记"));
        logManager.appendKeyValue("INFO", "NDK 目录", safeText(environmentState.getInstalledNdkDir(), "未登记"));
        logManager.appendKeyValue(
            "INFO",
            "当前所选 JDK 可用",
            environmentManager.isSelectedJdkInstalled(selectedJdkIndex, environmentState) ? "是" : "否"
        );
        logManager.appendKeyValue(
            "INFO",
            "当前所选 NDK 可用",
            environmentManager.isSelectedNdkInstalled(selectedNdkIndex, environmentState) ? "是" : "否"
        );
    }

    private void showJdkChooser() {
        new AlertDialog.Builder(this)
            .setTitle("选择 JDK 版本")
            .setSingleChoiceItems(EnvironmentManager.JDK_NAMES, selectedJdkIndex, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    selectedJdkIndex = which;
                }
            })
            .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    logManager.appendLogLine("INFO", "JDK 版本已切换。");
                    logManager.appendKeyValue("INFO", "已选 JDK", EnvironmentManager.JDK_NAMES[selectedJdkIndex]);
                    logManager.appendKeyValue("INFO", "安装来源", describeJdkSource(selectedJdkIndex));
                    logManager.appendKeyValue("INFO", "下载地址", EnvironmentManager.JDK_URLS[selectedJdkIndex]);
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void showNdkChooser() {
        new AlertDialog.Builder(this)
            .setTitle("选择 NDK 版本")
            .setSingleChoiceItems(EnvironmentManager.NDK_NAMES, selectedNdkIndex, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    selectedNdkIndex = which;
                }
            })
            .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    logManager.appendLogLine("INFO", "NDK 版本已切换。");
                    logManager.appendKeyValue("INFO", "已选 NDK", EnvironmentManager.NDK_NAMES[selectedNdkIndex]);
                    logManager.appendKeyValue("INFO", "安装来源", describeNdkSource(selectedNdkIndex));
                    logManager.appendKeyValue("INFO", "下载地址", EnvironmentManager.NDK_URLS[selectedNdkIndex]);
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void installSelectedJdk() {
        final String jdkName = EnvironmentManager.JDK_NAMES[selectedJdkIndex];
        final String jdkUrl = EnvironmentManager.JDK_URLS[selectedJdkIndex];

        logManager.appendLogLine("INFO", "开始安装 JDK...");
        logManager.appendKeyValue("INFO", "JDK 版本", jdkName);
        logManager.appendKeyValue("INFO", "安装来源", describeJdkSource(selectedJdkIndex));
        logManager.appendKeyValue("INFO", "下载地址", jdkUrl);
        logManager.appendKeyValue("INFO", "解压目录", environmentManager.getJdkInstallDir(jdkName));

        showProgressDialog("工具链安装", "正在准备 JDK 安装资源...");
        toolchainInstaller.installJdk(selectedJdkIndex, new ToolchainInstaller.InstallListener() {
            @Override
            public void onProgress(final String message) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        updateProgressDialog(message);
                        logManager.appendLogLine("INFO", message);
                    }
                });
            }
            
            @Override
            public void onSuccess(final String installedDir) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        dismissProgressDialog();
                        environmentState = environmentManager.saveInstalledJdk(jdkName, installedDir);
                        logManager.appendLogLine("INFO", "JDK 安装完成并已登记。");
                        logManager.appendKeyValue("INFO", "JDK 路径", installedDir);
                        toast("JDK 安装完成");
                    }
                });
            }

            @Override
            public void onError(final String message) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        dismissProgressDialog();
                        logManager.appendLogLine("ERROR", message);
                        toast("JDK 安装失败");
                    }
                });
            }
        });
    }

    private void installSelectedNdk() {
        final String ndkName = EnvironmentManager.NDK_NAMES[selectedNdkIndex];
        final String ndkUrl = EnvironmentManager.NDK_URLS[selectedNdkIndex];

        logManager.appendLogLine("INFO", "开始安装 NDK...");
        logManager.appendKeyValue("INFO", "NDK 版本", ndkName);
        logManager.appendKeyValue("INFO", "安装来源", describeNdkSource(selectedNdkIndex));
        logManager.appendKeyValue("INFO", "下载地址", ndkUrl);
        logManager.appendKeyValue("INFO", "解压目录", environmentManager.getNdkInstallDir(ndkName));

        showProgressDialog("工具链安装", "正在准备 NDK 安装资源...");
        toolchainInstaller.installNdk(selectedNdkIndex, new ToolchainInstaller.InstallListener() {
            @Override
            public void onProgress(final String message) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        updateProgressDialog(message);
                        logManager.appendLogLine("INFO", message);
                    }
                });
            }
            
            @Override
            public void onSuccess(final String installedDir) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        dismissProgressDialog();
                        environmentState = environmentManager.saveInstalledNdk(ndkName, installedDir);
                        logManager.appendLogLine("INFO", "NDK 安装完成并已登记。");
                        logManager.appendKeyValue("INFO", "NDK 路径", installedDir);
                        toast("NDK 安装完成");
                    }
                });
            }

            @Override
            public void onError(final String message) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        dismissProgressDialog();
                        logManager.appendLogLine("ERROR", message);
                        toast("NDK 安装失败");
                    }
                });
            }
        });
    }

    private void showInstallDirectoryPlan() {
        logManager.appendLogLine("INFO", "正在输出工具链目录规划。");
        logManager.appendKeyValue("INFO", "安装策略", "默认内嵌 JDK 21 + NDK r27c，其余版本联网下载");
        logManager.appendKeyValue("INFO", "安装包缓存目录", environmentManager.getPackageCacheDir());
        logManager.appendKeyValue("INFO", "JDK 根目录", "/storage/emulated/0/LMBuildTools/jdk/");
        logManager.appendKeyValue("INFO", "NDK 根目录", "/storage/emulated/0/LMBuildTools/ndk/");
        logManager.appendKeyValue("INFO", "项目根目录", "/storage/emulated/0/LMBuildTools/projects/");
        logManager.appendKeyValue("INFO", "当前 JDK 目录", environmentManager.getJdkInstallDir(EnvironmentManager.JDK_NAMES[selectedJdkIndex]));
        logManager.appendKeyValue("INFO", "当前 NDK 目录", environmentManager.getNdkInstallDir(EnvironmentManager.NDK_NAMES[selectedNdkIndex]));
    }

    private void dumpAllToolResources() {
        logManager.appendLogLine("INFO", "开始输出完整工具链资料库。");
        logManager.appendCatalogGroup("核心工具", CORE_TOOL_ITEMS);
        logManager.appendCatalogGroup("跨平台", CROSS_PLATFORM_ITEMS);
        logManager.appendCatalogGroup("测试环境", TEST_TOOL_ITEMS);
        logManager.appendCatalogGroup("调试工具", DEVOPS_TOOL_ITEMS);
        logManager.appendCatalogGroup("镜像命令", MIRROR_COMMAND_ITEMS);
        logManager.appendLogLine("INFO", "工具链资料库输出完成。");
    }

    private void appendBuildOutput(String line) {
        String lower = line.toLowerCase();
        if (lower.indexOf("error") >= 0 || line.indexOf("失败") >= 0) {
            logManager.appendLogLine("ERROR", line);
        } else if (lower.indexOf("warning") >= 0 || lower.indexOf("warn") >= 0) {
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

    private String describeJdkSource(int index) {
        if (environmentManager.isEmbeddedJdk(index)) {
            return "内嵌资源";
        }
        return "联网下载";
    }

    private String describeNdkSource(int index) {
        if (environmentManager.isEmbeddedNdk(index)) {
            return "内嵌资源";
        }
        return "联网下载";
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
                card.setPadding(24, 24, 24, 24);
                GradientDrawable drawable = new GradientDrawable();
                drawable.setColor(Color.parseColor("#171C26"));
                drawable.setCornerRadius(28f);
                card.setBackground(drawable);

                ImageView iconView = new ImageView(MainActivity.this);
                LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(96, 96);
                iconParams.rightMargin = 24;
                iconView.setLayoutParams(iconParams);
                iconView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                card.addView(iconView);

                LinearLayout textColumn = new LinearLayout(MainActivity.this);
                textColumn.setOrientation(LinearLayout.VERTICAL);
                textColumn.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                TextView title = new TextView(MainActivity.this);
                title.setTextColor(Color.WHITE);
                title.setTextSize(18f);
                title.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
                textColumn.addView(title);

                TextView sub = new TextView(MainActivity.this);
                sub.setTextColor(Color.parseColor("#95A1B6"));
                textColumn.addView(sub);

                TextView meta = new TextView(MainActivity.this);
                meta.setTextColor(Color.parseColor("#6EE7B7"));
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
                textView.setTextColor(Color.WHITE);
                textView.setPadding(12, 16, 12, 16);
                convertView = textView;
            } else {
                textView = (TextView) convertView;
            }
            FileTreeItem item = fileTreeItems.get(position);
            String prefix = item.isDirectory ? (expandedDirs.contains(item.file.getAbsolutePath()) ? "▾ " : "▸ ") : "• ";
            textView.setPadding(20 + item.depth * 28, 16, 12, 16);
            textView.setText(prefix + item.file.getName());
            textView.setTextColor(item.isDirectory ? Color.WHITE : Color.parseColor("#C9D7EA"));
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
