package com.LM.pack;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.os.Handler;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
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
import com.LM.pack.model.FileTreeItem;
import com.LM.pack.model.ProjectConfig;
import com.LM.pack.model.ProjectEntry;
import com.LM.pack.project.ProjectManager;
import com.LM.pack.service.BuildWorkflowService;
import com.LM.pack.service.ProjectFileService;
import com.LM.pack.service.ProjectWorkspaceService;
import com.LM.pack.theme.AppThemePalette;
import com.LM.pack.theme.GlassProgressBarView;
import com.LM.pack.theme.LiquidGlassBackgroundView;
import com.LM.pack.theme.ThemeManager;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;
import androidx.documentfile.provider.DocumentFile;

public class MainActivity extends Activity {

    private static final int REQUEST_FILE_BROWSER = 4101;
    private static final int REQUEST_IMPORT_ZIP = 4102;
    private static final int REQUEST_IMPORT_FOLDER = 4103;
    private static final int REQUEST_PICK_IMAGE = 4104;

    private Handler handler;

    private LogManager logManager;
    private EnvironmentManager environmentManager;
    private ThemeManager themeManager;
    private ProjectManager projectManager;
    private BuildManager buildManager;
    private ProjectPreflightChecker preflightChecker;
    private ProjectWorkspaceService projectWorkspaceService;
    private ProjectFileService projectFileService;
    private BuildWorkflowService buildWorkflowService;
    private EnvironmentState environmentState;

    private LinearLayout homePane;
    private LinearLayout editorPane;
    private LinearLayout emptyState;
    private LinearLayout fileDrawer;
    private LinearLayout suggestionCard;
    private LinearLayout editorTabContainer;
    private LinearLayout addActionOverlay;
    private LiquidGlassBackgroundView bgSceneView;
    private TextView tvToolbarTitle;
    private TextView tvEditorProject;
    private TextView tvCurrentFilePath;
    private TextView tvIssueTitle;
    private TextView tvIssueFix;
    private Button btnBackHome;
    private Button btnSettings;
    private Button btnFabAdd;
    private Button btnCreateAction;
    private Button btnImportAction;
    private Button btnToggleFiles;
    private Button btnBug;
    private Button btnSaveFile;
    private Button btnBuild;
    private Button btnCopyFix;
    private ListView lvProjects;
    private ListView lvFiles;
    private CodeEditorView etEditor;
    private View progressOverlay;
    private View progressCard;
    private TextView tvProgressTitle;
    private TextView tvProgressMessage;
    private TextView tvProgressPercent;
    private GlassProgressBarView progressBarFancy;

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
    private String lastSavedText = "";
    private boolean suppressEditorWriteback = false;
    private boolean swipeHandled = false;
    private boolean addActionAnimating = false;
    private boolean fileDrawerAnimating = false;
    private int lastAnimatedProjectPosition = -1;
    private int lastAnimatedFilePosition = -1;
    private float gestureStartX = 0f;
    private float gestureStartY = 0f;
    private Runnable autoSaveRunnable;
    private Runnable validationRunnable;
    private AppThemePalette palette;
    private ImagePickerTarget pendingImagePickerTarget;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        handler = new Handler(getMainLooper());
        SharedPreferences sharedPreferences = getSharedPreferences(EnvironmentManager.PREFS_NAME, MODE_PRIVATE);
        environmentManager = new EnvironmentManager(this, sharedPreferences);
        themeManager = new ThemeManager(this);
        projectManager = new ProjectManager();
        buildManager = new BuildManager(this, environmentManager);
        preflightChecker = new ProjectPreflightChecker(projectManager, environmentManager);
        projectWorkspaceService = new ProjectWorkspaceService(projectManager, environmentManager);
        projectFileService = new ProjectFileService();
        buildWorkflowService = new BuildWorkflowService(buildManager, preflightChecker, environmentManager, handler);
        environmentState = environmentManager.loadState();
        selectedJdkIndex = environmentManager.loadSelectedJdkIndex();
        selectedNdkIndex = environmentManager.loadSelectedNdkIndex();

        bindViews();
        initAdapters();
        bindEvents();
        initEditorWriteback();
        applyThemeUi();
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
        applyThemeUi();
    }

    private void bindViews() {
        homePane = (LinearLayout) findViewById(R.id.homePane);
        editorPane = (LinearLayout) findViewById(R.id.editorPane);
        emptyState = (LinearLayout) findViewById(R.id.emptyState);
        fileDrawer = (LinearLayout) findViewById(R.id.fileDrawer);
        suggestionCard = (LinearLayout) findViewById(R.id.suggestionCard);
        editorTabContainer = (LinearLayout) findViewById(R.id.editorTabContainer);
        addActionOverlay = (LinearLayout) findViewById(R.id.addActionOverlay);
        bgSceneView = (LiquidGlassBackgroundView) findViewById(R.id.bgSceneView);
        tvToolbarTitle = (TextView) findViewById(R.id.tvToolbarTitle);
        tvEditorProject = (TextView) findViewById(R.id.tvEditorProject);
        tvCurrentFilePath = (TextView) findViewById(R.id.tvCurrentFilePath);
        tvIssueTitle = (TextView) findViewById(R.id.tvIssueTitle);
        tvIssueFix = (TextView) findViewById(R.id.tvIssueFix);
        btnBackHome = (Button) findViewById(R.id.btnBackHome);
        btnSettings = (Button) findViewById(R.id.btnSettings);
        btnFabAdd = (Button) findViewById(R.id.btnFabAdd);
        btnCreateAction = (Button) findViewById(R.id.btnCreateAction);
        btnImportAction = (Button) findViewById(R.id.btnImportAction);
        btnToggleFiles = (Button) findViewById(R.id.btnToggleFiles);
        btnBug = (Button) findViewById(R.id.btnBug);
        btnSaveFile = (Button) findViewById(R.id.btnSaveFile);
        btnBuild = (Button) findViewById(R.id.btnBuild);
        btnCopyFix = (Button) findViewById(R.id.btnCopyFix);
        lvProjects = (ListView) findViewById(R.id.lvProjects);
        lvFiles = (ListView) findViewById(R.id.lvFiles);
        etEditor = (CodeEditorView) findViewById(R.id.etEditor);
        progressOverlay = findViewById(R.id.progressOverlay);
        progressCard = findViewById(R.id.progressCard);
        tvProgressTitle = (TextView) findViewById(R.id.tvProgressTitle);
        tvProgressMessage = (TextView) findViewById(R.id.tvProgressMessage);
        tvProgressPercent = (TextView) findViewById(R.id.tvProgressPercent);
        progressBarFancy = (GlassProgressBarView) findViewById(R.id.progressBarFancy);

        TextView tvLogs = (TextView) findViewById(R.id.tvLogs);
        ScrollView logScrollView = (ScrollView) findViewById(R.id.logScrollView);
        logManager = new LogManager(tvLogs, logScrollView);

        ViewGroup.LayoutParams layoutParams = fileDrawer.getLayoutParams();
        layoutParams.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.88f);
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
                flushPendingAutoSave();
                loadFileIntoEditor(file, false);
            }

            @Override
            public void onActiveTabClosed(File fallbackFile) {
                flushPendingAutoSave();
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
                flushPendingAutoSave();
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
                toggleAddActionOverlay();
            }
        });

        btnCreateAction.setOnClickListener(v -> {
            hideAddActionOverlay(true);
            showCreateProjectDialog();
        });

        btnImportAction.setOnClickListener(v -> {
            hideAddActionOverlay(true);
            showImportPicker();
        });

        btnToggleFiles.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleFileDrawer();
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
                hideFileDrawer(true);
                hideAddActionOverlay(true);
            }
        });
    }

    private void initEditorWriteback() {
        autoSaveRunnable = new Runnable() {
            @Override
            public void run() {
                saveCurrentFile();
            }
        };
        validationRunnable = new Runnable() {
            @Override
            public void run() {
                validateCurrentEditorContent();
            }
        };
        etEditor.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (suppressEditorWriteback || currentOpenFile == null) {
                    return;
                }
                scheduleAutoSave();
                scheduleValidation();
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
        projectEntries.addAll(projectWorkspaceService.loadProjects());
        lastAnimatedProjectPosition = -1;
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

    private void applyThemeUi() {
        palette = themeManager.getPalette(this);
        themeManager.applyActivityWindow(this, palette);
        themeManager.applyTaggedStyles(findViewById(R.id.mainRoot), palette);
        if (bgSceneView != null) {
            bgSceneView.setPalette(palette);
        }
        if (etEditor != null) {
            etEditor.applyThemePalette(palette);
        }
        if (editorTabManager != null) {
            editorTabManager.setPalette(palette);
        }
        if (btnBug != null) {
            updateBugButtonState();
        }
        if (projectAdapter != null) {
            projectAdapter.notifyDataSetChanged();
        }
        if (fileTreeAdapter != null) {
            fileTreeAdapter.notifyDataSetChanged();
        }
    }

    private void showHome() {
        homePane.setVisibility(View.VISIBLE);
        editorPane.setVisibility(View.GONE);
        btnBackHome.setVisibility(View.GONE);
        btnFabAdd.setVisibility(View.VISIBLE);
        btnToggleFiles.setText("目录");
        tvToolbarTitle.setText("APK Builder Pro");
        setSuggestionCardVisible(false, false);
        hideFileDrawer(false);
        hideAddActionOverlay(false);
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
        hideAddActionOverlay(false);
        tvToolbarTitle.setText("编辑器");
        tvEditorProject.setText(entry.getProjectName() + "  ·  " + safeText(entry.getPackageName(), "未识别包名"));
        tvCurrentFilePath.setText(entry.getProjectDir());
        setSuggestionCardVisible(false, false);
        lastBuildIssues.clear();
        updateBugButtonState();
        editorTabManager.clear();
        loadProjectFiles();
        showFileDrawer(false);
        btnToggleFiles.setText("目录");
        openDefaultEditorFile(entry);
        logManager.appendKeyValue("INFO", "已打开项目", entry.getProjectDir());
    }

    private void openDefaultEditorFile(ProjectEntry entry) {
        File defaultFile = projectWorkspaceService.resolveDefaultEditorFile(entry);
        if (defaultFile != null) {
            loadFileIntoEditor(defaultFile, true);
        } else {
            clearEditor();
            tvCurrentFilePath.setText("没有找到可编辑的文本文件");
        }
    }

    private void toggleAddActionOverlay() {
        if (addActionAnimating) {
            return;
        }
        if (addActionOverlay.getVisibility() == View.VISIBLE && addActionOverlay.getAlpha() > 0.01f) {
            hideAddActionOverlay(true);
        } else {
            showAddActionOverlay();
        }
    }

    private void showAddActionOverlay() {
        if (addActionOverlay == null || btnImportAction == null || btnCreateAction == null) {
            return;
        }
        addActionAnimating = true;
        addActionOverlay.setVisibility(View.VISIBLE);
        addActionOverlay.setAlpha(1f);
        btnFabAdd.animate().rotation(45f).setDuration(220L).start();
        prepareActionButtonForEntrance(btnImportAction, 68, 12);
        prepareActionButtonForEntrance(btnCreateAction, 96, 20);
        animateActionButtonIn(btnImportAction, 0L);
        animateActionButtonIn(btnCreateAction, 82L);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                addActionAnimating = false;
            }
        }, 280L);
    }

    private void hideAddActionOverlay(boolean animated) {
        if (addActionOverlay == null) {
            return;
        }
        btnFabAdd.animate().rotation(0f).setDuration(180L).start();
        if (!animated || addActionOverlay.getVisibility() != View.VISIBLE) {
            addActionOverlay.animate().cancel();
            addActionOverlay.setVisibility(View.GONE);
            addActionOverlay.setAlpha(0f);
            btnImportAction.setAlpha(0f);
            btnCreateAction.setAlpha(0f);
            btnImportAction.setTranslationX(dp(68));
            btnCreateAction.setTranslationX(dp(96));
            return;
        }
        addActionAnimating = true;
        animateActionButtonOut(btnCreateAction, 0L);
        animateActionButtonOut(btnImportAction, 58L);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                addActionOverlay.setVisibility(View.GONE);
                addActionOverlay.setAlpha(0f);
                addActionAnimating = false;
            }
        }, 230L);
    }

    private void prepareActionButtonForEntrance(View button, int translationDp, int translationYDp) {
        if (button == null) {
            return;
        }
        button.animate().cancel();
        button.setVisibility(View.VISIBLE);
        button.setAlpha(0f);
        button.setTranslationX(dp(translationDp));
        button.setTranslationY(dp(translationYDp));
        button.setScaleX(0.92f);
        button.setScaleY(0.92f);
    }

    private void animateActionButtonIn(View button, long delayMs) {
        if (button == null) {
            return;
        }
        button.animate()
            .alpha(1f)
            .translationX(0f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setStartDelay(delayMs)
            .setDuration(220L)
            .start();
    }

    private void animateActionButtonOut(View button, long delayMs) {
        if (button == null) {
            return;
        }
        button.animate()
            .alpha(0f)
            .translationX(dp(74))
            .translationY(dp(10))
            .scaleX(0.92f)
            .scaleY(0.92f)
            .setStartDelay(delayMs)
            .setDuration(170L)
            .start();
    }

    private void showImportPicker() {
        showStoragePicker("导入项目", true, false, new PathPickListener() {
            @Override
            public void onPicked(File path) {
                if (path == null) {
                    return;
                }
                if (path.isDirectory()) {
                    importFolderProject(path);
                    return;
                }
                if (projectWorkspaceService.isZipFile(path)) {
                    importZipProject(path);
                    return;
                }
                toast("这里只支持导入文件夹或 zip 压缩包");
            }
        });
    }

    private void showCreateProjectDialog() {
        final LinearLayout container = buildDialogContainer();
        final EditText etAppName = createDialogField("应用名称", "我的应用", "LM打包工具");
        final EditText etPackageName = createDialogField("包名", "com.example.app", "com.LM.pack");
        final EditText etVersionName = createDialogField("版本号", "1.0.0", "1.0.0");
        final EditText etVersionCode = createDialogField("版本代码", "1", "1");
        etVersionCode.setInputType(InputType.TYPE_CLASS_NUMBER);
        final String[] minLabels = {"安卓 10", "安卓 9", "安卓 8.1"};
        final int[] minValues = {29, 28, 27};
        final String[] targetLabels = {"安卓 11", "安卓 12", "安卓 13", "安卓 14", "安卓 15", "安卓 16"};
        final int[] targetValues = {30, 31, 33, 34, 35, 36};
        final int[] minSelection = {0};
        final int[] targetSelection = {5};
        final Button btnMinSdk = createDialogSelectButton("安卓版本：安卓 10（最低）");
        final Button btnTargetSdk = createDialogSelectButton("最高版本：安卓 16");
        final ImagePickerTarget iconTarget = buildImagePickerCard("应用图标", "建议使用方形 png / webp，选中后会直接显示预览");
        final ImagePickerTarget splashTarget = buildImagePickerCard("启动图", "支持 png / jpg / webp，创建项目时会自动使用缓存文件");

        btnMinSdk.setOnClickListener(v -> showOptionListDialog(
            "安卓版本",
            "这里只保留安卓 10 到安卓 8.1，直接选最低兼容版本。",
            minLabels,
            minSelection[0],
            which -> {
                minSelection[0] = which;
                btnMinSdk.setText("安卓版本：" + minLabels[which] + "（最低）");
            }
        ));
        btnTargetSdk.setOnClickListener(v -> showOptionListDialog(
            "最高版本",
            "这里是当前生成项目的最高支持版本，范围固定为安卓 11 到安卓 16。",
            targetLabels,
            targetSelection[0],
            which -> {
                targetSelection[0] = which;
                btnTargetSdk.setText("最高版本：" + targetLabels[which]);
            }
        ));

        container.addView(buildFieldLabel("创建一个可直接打包的 Android 空壳工程"));
        container.addView(iconTarget.cardView);
        container.addView(etAppName);
        container.addView(etPackageName);
        container.addView(etVersionName);
        container.addView(etVersionCode);
        container.addView(btnMinSdk);
        container.addView(btnTargetSdk);
        container.addView(splashTarget.cardView);

        AlertDialog createDialog = new AlertDialog.Builder(this)
            .setTitle("创建项目")
            .setView(container)
            .setPositiveButton("创建", (dialog, which) -> {
                String appName = etAppName.getText().toString().trim();
                String packageName = etPackageName.getText().toString().trim();
                String versionName = etVersionName.getText().toString().trim();
                int versionCode = parseInt(etVersionCode.getText().toString().trim(), 1);
                int minSdk = minValues[minSelection[0]];
                int targetSdk = targetValues[targetSelection[0]];

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
                        iconTarget.cachedPath,
                        splashTarget.cachedPath
                    );
                    File rootDir = projectWorkspaceService.createProject(this, config);
                    logManager.appendLogLine("INFO", "项目已创建，可直接进入编辑页继续修改。");
                    logManager.appendKeyValue("INFO", "创建目录", rootDir.getAbsolutePath());
                    refreshProjectList();
                    openProject(projectWorkspaceService.readProjectEntry(rootDir));
                } catch (Exception e) {
                    logManager.appendLogLine("ERROR", "创建项目失败：" + e);
                    logManager.appendKeyValue("ERROR", "目标目录", projectWorkspaceService.getPlannedProjectRoot(appName));
                    appendExceptionDetailToLogs(e);
                    toast("创建项目失败");
                }
            })
            .setNegativeButton("取消", null)
            .create();
        createDialog.show();
        animatePopupCard(createDialog.getWindow() == null ? null : createDialog.getWindow().getDecorView());
    }

    private ImagePickerTarget buildImagePickerCard(final String title, String hintText) {
        final ImagePickerTarget target = new ImagePickerTarget();

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(12);
        card.setLayoutParams(cardParams);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setCornerRadius(dp(16));
        if (palette != null) {
            cardBg.setColor(palette.surfaceRaised);
            cardBg.setStroke(dp(1), palette.stroke);
        } else {
            cardBg.setColor(Color.parseColor("#172033"));
            cardBg.setStroke(dp(1), Color.parseColor("#2B3A55"));
        }
        card.setBackground(cardBg);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(palette == null ? Color.WHITE : palette.textPrimary);
        titleView.setTextSize(16f);
        card.addView(titleView);

        TextView hintView = new TextView(this);
        hintView.setText(hintText);
        hintView.setTextColor(palette == null ? Color.parseColor("#95A1B6") : palette.textMuted);
        hintView.setPadding(0, dp(6), 0, dp(12));
        card.addView(hintView);

        ImageView preview = new ImageView(this);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(132));
        previewParams.bottomMargin = dp(10);
        preview.setLayoutParams(previewParams);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setAdjustViewBounds(false);
        GradientDrawable previewBg = new GradientDrawable();
        previewBg.setCornerRadius(dp(12));
        previewBg.setColor(palette == null ? Color.parseColor("#101826") : palette.surface);
        previewBg.setStroke(dp(1), palette == null ? Color.parseColor("#30415F") : palette.stroke);
        preview.setBackground(previewBg);
        preview.setPadding(dp(10), dp(10), dp(10), dp(10));
        card.addView(preview);

        TextView statusView = new TextView(this);
        statusView.setTextColor(palette == null ? Color.parseColor("#95A1B6") : palette.textSecondary);
        statusView.setPadding(0, 0, 0, dp(10));
        card.addView(statusView);

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);

        Button pickButton = new Button(this);
        pickButton.setText("选择图片");
        if (palette != null) {
            pickButton.setTextColor(palette.textPrimary);
            pickButton.setBackground(themeManager.createPrimaryButtonDrawable(palette));
        } else {
            pickButton.setTextColor(Color.WHITE);
            pickButton.setBackgroundColor(Color.parseColor("#2D7DFA"));
        }
        actionRow.addView(pickButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button clearButton = new Button(this);
        clearButton.setText("清除");
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clearParams.leftMargin = dp(10);
        if (palette != null) {
            clearButton.setTextColor(palette.textSecondary);
            clearButton.setBackground(themeManager.createGhostButtonDrawable(palette));
        } else {
            clearButton.setTextColor(Color.WHITE);
            clearButton.setBackgroundColor(Color.parseColor("#3C465A"));
        }
        actionRow.addView(clearButton, clearParams);
        card.addView(actionRow);

        target.cardView = card;
        target.previewView = preview;
        target.statusView = statusView;
        target.clearButton = clearButton;
        updateImagePickerCard(target, null);

        final Runnable openPicker = new Runnable() {
            @Override
            public void run() {
                pendingImagePickerTarget = target;
                launchImagePicker();
            }
        };
        pickButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openPicker.run();
            }
        });
        preview.setOnClickListener(v -> openPicker.run());
        card.setOnClickListener(v -> openPicker.run());
        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateImagePickerCard(target, null);
            }
        });
        return target;
    }

    private void launchZipImportPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {"application/zip", "application/x-zip-compressed", "*/*"});
        startActivityForResult(intent, REQUEST_IMPORT_ZIP);
    }

    private void launchFolderImportPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(intent, REQUEST_IMPORT_FOLDER);
    }

    private void launchImagePicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            intent.setType("image/*");
            startActivityForResult(intent, REQUEST_PICK_IMAGE);
        } catch (Exception e) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            startActivityForResult(intent, REQUEST_PICK_IMAGE);
        }
    }

    private void importZipProjectFromUri(final Uri uri) {
        showProgressDialog("导入项目", "正在读取压缩包...", 0, false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final File cachedZip = copyDocumentUriToTempFile(uri, "import_zip", ".zip");
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            importZipProject(cachedZip);
                        }
                    });
                } catch (final Exception e) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            dismissProgressDialog();
                            logManager.appendLogLine("ERROR", "读取 zip 文件失败：" + e);
                            appendExceptionDetailToLogs(e);
                            toast("读取 zip 文件失败");
                        }
                    });
                }
            }
        }).start();
    }

    private void importFolderProjectFromUri(final Uri treeUri) {
        showProgressDialog("导入项目", "正在复制目录授权内容...", -1, true);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    takeReadPermission(treeUri);
                    File tempRoot = new File(environmentManager.getImportTempDir(), "tree_import_" + System.currentTimeMillis());
                    clearDirectory(tempRoot);
                    ensureDir(tempRoot);
                    DocumentFile pickedTree = DocumentFile.fromTreeUri(MainActivity.this, treeUri);
                    if (pickedTree == null || !pickedTree.exists() || !pickedTree.isDirectory()) {
                        throw new IllegalStateException("没有读取到可用的项目目录");
                    }
                    String rootName = safeText(pickedTree.getName(), "project_root");
                    File copiedRoot = new File(tempRoot, sanitizeFileName(rootName));
                    copyDocumentTreeToDirectory(pickedTree, copiedRoot);
                    final File importSource = copiedRoot;
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            importFolderProject(importSource);
                        }
                    });
                } catch (final Exception e) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            dismissProgressDialog();
                            logManager.appendLogLine("ERROR", "读取项目目录失败：" + e);
                            appendExceptionDetailToLogs(e);
                            toast("读取项目目录失败");
                        }
                    });
                }
            }
        }).start();
    }

    private void applyPickedImageUri(final Uri uri) {
        if (pendingImagePickerTarget == null) {
            toast("当前没有待更新的图片区域");
            return;
        }
        try {
            takeReadPermission(uri);
            File cachedImage = copyDocumentUriToTempFile(uri, "picked_image", guessImageExtension(uri));
            updateImagePickerCard(pendingImagePickerTarget, cachedImage);
        } catch (Exception e) {
            logManager.appendLogLine("ERROR", "读取图片失败：" + e);
            appendExceptionDetailToLogs(e);
            toast("读取图片失败");
        } finally {
            pendingImagePickerTarget = null;
        }
    }

    private void updateImagePickerCard(ImagePickerTarget target, File cachedImage) {
        if (target == null) {
            return;
        }
        target.cachedPath = cachedImage == null ? "" : cachedImage.getAbsolutePath();
        if (cachedImage == null || !cachedImage.exists()) {
            target.previewView.setImageDrawable(null);
            target.previewView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            target.previewView.setImageResource(android.R.drawable.ic_menu_gallery);
            target.statusView.setText("未选择图片");
            target.clearButton.setEnabled(false);
            target.clearButton.setAlpha(0.5f);
            return;
        }
        Bitmap bitmap = BitmapFactory.decodeFile(cachedImage.getAbsolutePath());
        if (bitmap != null) {
            target.previewView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            target.previewView.setImageBitmap(bitmap);
        } else {
            target.previewView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            target.previewView.setImageResource(android.R.drawable.ic_menu_report_image);
        }
        target.statusView.setText("已选择：" + cachedImage.getName());
        target.clearButton.setEnabled(true);
        target.clearButton.setAlpha(1f);
    }

    private File copyDocumentUriToTempFile(Uri uri, String prefix, String fallbackExtension) throws Exception {
        ContentResolver resolver = getContentResolver();
        String displayName = resolveDisplayName(uri);
        String fileName = displayName.length() > 0 ? sanitizeFileName(displayName) : prefix + "_" + System.currentTimeMillis() + fallbackExtension;
        if (fileName.indexOf('.') < 0 && fallbackExtension != null && fallbackExtension.length() > 0) {
            fileName = fileName + fallbackExtension;
        }
        File targetDir = new File(environmentManager.getImportTempDir(), "picked_files");
        ensureDir(targetDir);
        File targetFile = new File(targetDir, fileName);
        InputStream inputStream = null;
        FileOutputStream outputStream = null;
        try {
            inputStream = resolver.openInputStream(uri);
            if (inputStream == null) {
                throw new IllegalStateException("无法打开所选文件");
            }
            outputStream = new FileOutputStream(targetFile);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, len);
            }
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
            if (outputStream != null) {
                outputStream.close();
            }
        }
        return targetFile;
    }

    private void copyDocumentTreeToDirectory(DocumentFile sourceDir, File targetDir) throws Exception {
        if (sourceDir == null || !sourceDir.exists()) {
            throw new IllegalStateException("目录授权内容不可用");
        }
        ensureDir(targetDir);
        DocumentFile[] children = sourceDir.listFiles();
        if (children == null) {
            return;
        }
        for (int i = 0; i < children.length; i++) {
            DocumentFile child = children[i];
            if (child == null || !child.exists()) {
                continue;
            }
            String childName = sanitizeFileName(safeText(child.getName(), "entry_" + i));
            File target = new File(targetDir, childName);
            if (child.isDirectory()) {
                copyDocumentTreeToDirectory(child, target);
            } else {
                copyDocumentFile(child, target);
            }
        }
    }

    private void copyDocumentFile(DocumentFile documentFile, File targetFile) throws Exception {
        ensureDir(targetFile.getParentFile());
        InputStream inputStream = null;
        FileOutputStream outputStream = null;
        try {
            inputStream = getContentResolver().openInputStream(documentFile.getUri());
            if (inputStream == null) {
                throw new IllegalStateException("无法读取文件：" + safeText(documentFile.getName(), "unknown"));
            }
            outputStream = new FileOutputStream(targetFile);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, len);
            }
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
            if (outputStream != null) {
                outputStream.close();
            }
        }
    }

    private void takeReadPermission(Uri uri) {
        if (uri == null) {
            return;
        }
        try {
            int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
            getContentResolver().takePersistableUriPermission(uri, flags);
        } catch (Exception ignored) {
        }
    }

    private String resolveDisplayName(Uri uri) {
        if (uri == null) {
            return "";
        }
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String value = cursor.getString(index);
                    return value == null ? "" : value.trim();
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        String lastSegment = uri.getLastPathSegment();
        return lastSegment == null ? "" : lastSegment;
    }

    private String guessImageExtension(Uri uri) {
        String displayName = resolveDisplayName(uri).toLowerCase();
        if (displayName.endsWith(".jpg") || displayName.endsWith(".jpeg")) {
            return ".jpg";
        }
        if (displayName.endsWith(".webp")) {
            return ".webp";
        }
        return ".png";
    }

    private String sanitizeFileName(String value) {
        String clean = safeText(value, "");
        if (clean.length() == 0) {
            return "file";
        }
        return clean.replace('\\', '_').replace('/', '_').replace(':', '_');
    }

    private void ensureDir(File dir) {
        if (dir == null || dir.exists()) {
            return;
        }
        dir.mkdirs();
    }

    private void clearDirectory(File dir) {
        if (dir == null || !dir.exists()) {
            return;
        }
        if (dir.isDirectory()) {
            File[] children = dir.listFiles();
            if (children != null) {
                for (int i = 0; i < children.length; i++) {
                    clearDirectory(children[i]);
                }
            }
        }
        dir.delete();
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
        label.setTextColor(palette == null ? Color.WHITE : palette.textPrimary);
        label.setPadding(0, 0, 0, dp(12));
        return label;
    }

    private EditText createDialogField(String hint, String placeholder, String defaultValue) {
        EditText editText = new EditText(this);
        editText.setHint(hint + "，例如 " + placeholder);
        editText.setText(defaultValue);
        if (palette != null) {
            editText.setTextColor(palette.textPrimary);
            editText.setHintTextColor(palette.textMuted);
            editText.setBackground(themeManager.createEditorDrawable(palette));
        } else {
            editText.setTextColor(Color.WHITE);
            editText.setHintTextColor(Color.GRAY);
            editText.setBackgroundColor(Color.parseColor("#1E1E1E"));
        }
        editText.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(12);
        editText.setLayoutParams(params);
        return editText;
    }

    private Button createDialogSelectButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setPadding(dp(16), dp(14), dp(16), dp(14));
        if (palette != null) {
            button.setTextColor(palette.textPrimary);
            button.setBackground(themeManager.createGhostButtonDrawable(palette));
        } else {
            button.setTextColor(Color.WHITE);
            button.setBackgroundColor(Color.parseColor("#1E1E1E"));
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(12);
        button.setLayoutParams(params);
        return button;
    }

    private void showOptionListDialog(String title, String subtitle, String[] labels, int checkedIndex, final OptionSelectListener listener) {
        final Dialog dialog = createAppDialog(title, subtitle);
        ListView listView = (ListView) dialog.findViewById(R.id.lvDialogItems);
        Button btnPrimary = (Button) dialog.findViewById(R.id.btnDialogPrimary);
        Button btnSecondary = (Button) dialog.findViewById(R.id.btnDialogSecondary);
        Button btnNeutral = (Button) dialog.findViewById(R.id.btnDialogNeutral);
        listView.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, labels));
        if (checkedIndex >= 0 && checkedIndex < labels.length) {
            listView.setSelection(checkedIndex);
        }
        listView.setOnItemClickListener((parent, view, which, id) -> {
            dialog.dismiss();
            if (listener != null) {
                listener.onSelected(which);
            }
        });
        btnPrimary.setVisibility(View.GONE);
        btnNeutral.setVisibility(View.GONE);
        btnSecondary.setText("关闭");
        btnSecondary.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
        animatePopupCard(dialog.getWindow() == null ? null : dialog.getWindow().getDecorView());
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
            TextView tip = buildFieldLabel("提示：当前还没有完整文件访问权限。开启后才能直接读取内部储存、文件夹和压缩包。");
            tip.setTextColor(palette == null ? Color.parseColor("#8FA3BF") : palette.textSecondary);
            container.addView(tip);

            Button btnGrant = new Button(this);
                btnGrant.setText("去开启文件访问");
            if (palette != null) {
                btnGrant.setTextColor(palette.textPrimary);
                btnGrant.setBackground(themeManager.createPrimaryButtonDrawable(palette));
            } else {
                btnGrant.setTextColor(Color.WHITE);
                btnGrant.setBackgroundColor(Color.parseColor("#2563EB"));
            }
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
                String suffix = folderSelect ? "  · 点文件夹进入，长按文件夹导入，点 zip 自动解压导入" : "";
                tvPath.setText("当前位置：" + currentDir[0].getAbsolutePath() + suffix);
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
                if (zipOnly && !projectWorkspaceService.isZipFile(clicked)) {
                    toast("这里只能选择 zip 压缩包");
                    return;
                }
                if (!folderSelect && !projectWorkspaceService.isZipFile(clicked)) {
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
        showProgressDialog("导入项目", "正在分析压缩包...", 0, false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final ProjectWorkspaceService.ImportResult importResult = projectWorkspaceService.importZipProject(
                        zipFile,
                        new ProjectWorkspaceService.ImportProgressListener() {
                            @Override
                            public void onProgress(final String message, final int percent) {
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        updateProgressDialog(message, percent, false);
                                    }
                                });
                            }
                        }
                    );
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            dismissProgressDialog();
                            logManager.appendLogLine("INFO", "压缩包已解压并导入到管理目录。");
                            logManager.appendKeyValue("INFO", "原压缩包保留", zipFile.getAbsolutePath());
                            logManager.appendKeyValue("INFO", "识别根目录", importResult.getDetectedRoot().getAbsolutePath());
                            logManager.appendKeyValue("INFO", "导入目录", importResult.getImportedRoot().getAbsolutePath());
                            refreshProjectList();
                            openProject(projectWorkspaceService.readProjectEntry(importResult.getImportedRoot()));
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
        showProgressDialog("导入项目", "正在深度检查目录结构...", 0, false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final ProjectWorkspaceService.ImportResult importResult = projectWorkspaceService.importFolderProject(
                        folder,
                        new ProjectWorkspaceService.ImportProgressListener() {
                            @Override
                            public void onProgress(final String message, final int percent) {
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        updateProgressDialog(message, percent, false);
                                    }
                                });
                            }
                        }
                    );
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            dismissProgressDialog();
                            logManager.appendLogLine("INFO", "文件夹已导入到管理目录。");
                            logManager.appendKeyValue("INFO", "源目录", folder.getAbsolutePath());
                            logManager.appendKeyValue("INFO", "识别根目录", importResult.getDetectedRoot().getAbsolutePath());
                            logManager.appendKeyValue("INFO", "实际导入目录", importResult.getImportedRoot().getAbsolutePath());
                            refreshProjectList();
                            openProject(projectWorkspaceService.readProjectEntry(importResult.getImportedRoot()));
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
        if (currentProject == null) {
            fileTreeItems.clear();
            lastAnimatedFilePosition = -1;
            fileTreeAdapter.notifyDataSetChanged();
            return;
        }
        fileTreeItems.clear();
        fileTreeItems.addAll(projectFileService.listDirectory(currentProject, new File(currentProject.getProjectDir())));
        lastAnimatedFilePosition = -1;
        fileTreeAdapter.notifyDataSetChanged();
    }

    private void handleFileTreeClick(int position) {
        if (position < 0 || position >= fileTreeItems.size()) {
            return;
        }
        FileTreeItem item = fileTreeItems.get(position);
        if (item.isDirectory()) {
            openDirectoryBrowser(item.getFile());
            return;
        }
        openTextFile(item.getFile());
        hideFileDrawer(true);
    }

    private void openDirectoryBrowser(File directory) {
        if (currentProject == null || directory == null || !directory.isDirectory()) {
            return;
        }
        Intent intent = new Intent(this, FileBrowserActivity.class);
        intent.putExtra(FileBrowserActivity.EXTRA_PROJECT_NAME, currentProject.getProjectName());
        intent.putExtra(FileBrowserActivity.EXTRA_PROJECT_DIR, currentProject.getProjectDir());
        intent.putExtra(FileBrowserActivity.EXTRA_PROJECT_PACKAGE, currentProject.getPackageName());
        intent.putExtra(FileBrowserActivity.EXTRA_CURRENT_DIR, directory.getAbsolutePath());
        startActivityForResult(intent, REQUEST_FILE_BROWSER);
    }

    private void openTextFile(File file) {
        if (file == null) {
            return;
        }
        if (!sameFile(file, currentOpenFile)) {
            flushPendingAutoSave();
        }
        loadFileIntoEditor(file, true);
    }

    private void loadFileIntoEditor(File file, boolean trackTab) {
        try {
            String content = projectWorkspaceService.readText(file);
            currentOpenFile = file;
            lastSavedText = content;
            etEditor.setFileName(file.getName());
            suppressEditorWriteback = true;
            etEditor.setText(content);
            suppressEditorWriteback = false;
            tvCurrentFilePath.setText(projectFileService.buildDisplayPath(currentProject, file));
            setSuggestionCardVisible(false, true);
            etEditor.clearDiagnosticLines();
            if (trackTab) {
                editorTabManager.openTab(file);
            } else {
                editorTabManager.activate(file);
            }
            validateCurrentEditorContent();
        } catch (Exception e) {
            logManager.appendLogLine("ERROR", "打开文件失败：" + e.getMessage());
            toast("打开文件失败");
        }
    }

    private void clearEditor() {
        flushPendingAutoSave();
        currentOpenFile = null;
        lastSavedText = "";
        etEditor.setFileName("");
        suppressEditorWriteback = true;
        etEditor.setText("");
        suppressEditorWriteback = false;
        etEditor.clearDiagnosticLines();
        tvCurrentFilePath.setText("请选择一个文件");
        setSuggestionCardVisible(false, false);
    }

    private void saveCurrentFile() {
        if (currentOpenFile == null) {
            return;
        }
        try {
            String content = etEditor.getText().toString();
            if (content.equals(lastSavedText)) {
                return;
            }
            projectWorkspaceService.writeText(currentOpenFile, content);
            lastSavedText = content;
        } catch (Exception e) {
            logManager.appendLogLine("ERROR", "保存文件失败：" + e.getMessage());
            toast("保存失败");
        }
    }

    private void scheduleAutoSave() {
        handler.removeCallbacks(autoSaveRunnable);
        handler.postDelayed(autoSaveRunnable, 220L);
    }

    private void scheduleValidation() {
        handler.removeCallbacks(validationRunnable);
        handler.postDelayed(validationRunnable, 360L);
    }

    private void flushPendingAutoSave() {
        handler.removeCallbacks(autoSaveRunnable);
        saveCurrentFile();
    }

    private void toggleFileDrawer() {
        if (fileDrawerAnimating) {
            return;
        }
        if (fileDrawer.getVisibility() == View.VISIBLE && fileDrawer.getAlpha() > 0.01f) {
            hideFileDrawer(true);
        } else {
            showFileDrawer(true);
        }
    }

    private void showFileDrawer(boolean animated) {
        if (fileDrawer == null) {
            return;
        }
        btnToggleFiles.setText("目录");
        if (!animated) {
            fileDrawer.animate().cancel();
            fileDrawer.setVisibility(View.VISIBLE);
            fileDrawer.setAlpha(1f);
            fileDrawer.setTranslationX(0f);
            return;
        }
        fileDrawerAnimating = true;
        fileDrawer.setVisibility(View.VISIBLE);
        fileDrawer.setAlpha(0f);
        fileDrawer.setTranslationX(-Math.max(fileDrawer.getWidth(), dp(220)));
        fileDrawer.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(240L)
            .withEndAction(new Runnable() {
                @Override
                public void run() {
                    fileDrawerAnimating = false;
                }
            })
            .start();
    }

    private void hideFileDrawer(boolean animated) {
        if (fileDrawer == null) {
            return;
        }
        btnToggleFiles.setText("目录");
        if (!animated || fileDrawer.getVisibility() != View.VISIBLE) {
            fileDrawer.animate().cancel();
            fileDrawer.setVisibility(View.GONE);
            fileDrawer.setAlpha(1f);
            fileDrawer.setTranslationX(0f);
            fileDrawerAnimating = false;
            return;
        }
        fileDrawerAnimating = true;
        fileDrawer.animate()
            .alpha(0f)
            .translationX(-Math.max(fileDrawer.getWidth(), dp(220)))
            .setDuration(210L)
            .withEndAction(new Runnable() {
                @Override
                public void run() {
                    fileDrawer.setVisibility(View.GONE);
                    fileDrawer.setAlpha(1f);
                    fileDrawer.setTranslationX(0f);
                    fileDrawerAnimating = false;
                }
            })
            .start();
    }

    private void setSuggestionCardVisible(boolean visible, boolean animated) {
        if (suggestionCard == null) {
            return;
        }
        suggestionCard.animate().cancel();
        if (!animated) {
            suggestionCard.setVisibility(visible ? View.VISIBLE : View.GONE);
            suggestionCard.setAlpha(1f);
            suggestionCard.setTranslationY(0f);
            return;
        }
        if (visible) {
            if (suggestionCard.getVisibility() != View.VISIBLE) {
                suggestionCard.setVisibility(View.VISIBLE);
                suggestionCard.setAlpha(0f);
                suggestionCard.setTranslationY(-dp(12));
            }
            suggestionCard.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(220L)
                .start();
            return;
        }
        if (suggestionCard.getVisibility() != View.VISIBLE) {
            suggestionCard.setVisibility(View.GONE);
            return;
        }
        suggestionCard.animate()
            .alpha(0f)
            .translationY(-dp(10))
            .setDuration(170L)
            .withEndAction(new Runnable() {
                @Override
                public void run() {
                    suggestionCard.setVisibility(View.GONE);
                    suggestionCard.setAlpha(1f);
                    suggestionCard.setTranslationY(0f);
                }
            })
            .start();
    }

    private void animateOverlayIn(View overlay, View card) {
        if (overlay == null) {
            return;
        }
        if (overlay.getVisibility() == View.VISIBLE && overlay.getAlpha() > 0.98f) {
            return;
        }
        overlay.animate().cancel();
        if (card != null) {
            card.animate().cancel();
        }
        overlay.setVisibility(View.VISIBLE);
        overlay.setAlpha(0f);
        overlay.animate().alpha(1f).setDuration(160L).start();
        if (card != null) {
            card.setAlpha(0f);
            card.setScaleX(0.94f);
            card.setScaleY(0.94f);
            card.setTranslationY(dp(22));
            card.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(220L)
                .start();
        }
    }

    private void animateOverlayOut(final View overlay, final View card) {
        if (overlay == null || overlay.getVisibility() != View.VISIBLE) {
            return;
        }
        overlay.animate().cancel();
        if (card != null) {
            card.animate().cancel();
            card.animate()
                .alpha(0f)
                .scaleX(0.96f)
                .scaleY(0.96f)
                .translationY(dp(14))
                .setDuration(150L)
                .start();
        }
        overlay.animate()
            .alpha(0f)
            .setDuration(160L)
            .withEndAction(new Runnable() {
                @Override
                public void run() {
                    overlay.setVisibility(View.GONE);
                    overlay.setAlpha(1f);
                    if (card != null) {
                        card.setAlpha(1f);
                        card.setScaleX(1f);
                        card.setScaleY(1f);
                        card.setTranslationY(0f);
                    }
                }
            })
            .start();
    }

    private void animatePopupCard(View content) {
        if (content == null) {
            return;
        }
        content.animate().cancel();
        content.setAlpha(0f);
        content.setScaleX(0.94f);
        content.setScaleY(0.94f);
        content.setTranslationY(dp(18));
        content.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(220L)
            .start();
    }

    private void animateListItem(View itemView, int position, boolean projectList) {
        if (itemView == null) {
            return;
        }
        int lastPosition = projectList ? lastAnimatedProjectPosition : lastAnimatedFilePosition;
        if (position <= lastPosition) {
            itemView.setAlpha(1f);
            itemView.setTranslationX(0f);
            itemView.setTranslationY(0f);
            return;
        }
        itemView.animate().cancel();
        itemView.setAlpha(0f);
        itemView.setTranslationX(projectList ? dp(18) : -dp(14));
        itemView.setTranslationY(dp(6));
        itemView.animate()
            .alpha(1f)
            .translationX(0f)
            .translationY(0f)
            .setDuration(220L)
            .setStartDelay(Math.min(150L, position * 22L))
            .start();
        if (projectList) {
            lastAnimatedProjectPosition = position;
        } else {
            lastAnimatedFilePosition = position;
        }
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
        File projectDir = new File(currentProject.getProjectDir());
        showBuildPreparationDialog(projectDir);
    }

    private void showBuildPreparationDialog(final File projectDir) {
        final int recommendedJdk = environmentManager.recommendJdkIndex(projectDir);
        final int recommendedNdk = environmentManager.recommendNdkIndex(projectDir);
        final Dialog dialog = createAppDialog("打包环境推荐", buildProjectBuildSummary(projectDir));
        ListView listView = (ListView) dialog.findViewById(R.id.lvDialogItems);
        Button btnPrimary = (Button) dialog.findViewById(R.id.btnDialogPrimary);
        Button btnSecondary = (Button) dialog.findViewById(R.id.btnDialogSecondary);
        Button btnNeutral = (Button) dialog.findViewById(R.id.btnDialogNeutral);
        String[] items = new String[] {
            environmentManager.buildToolchainRecommendationSummary(projectDir),
            "推荐架构：" + readAbiSummary(projectDir),
            "当前路线：" + environmentManager.getDownloadRouteDisplayName()
        };
        listView.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, items));
        btnPrimary.setText("推荐路线");
        btnSecondary.setText("取消");
        btnNeutral.setVisibility(View.VISIBLE);
        btnNeutral.setText("自选路线");
        btnSecondary.setOnClickListener(v -> dialog.dismiss());
        btnPrimary.setOnClickListener(v -> {
            dialog.dismiss();
            environmentManager.saveSelectedJdkIndex(recommendedJdk);
            environmentManager.saveSelectedNdkIndex(recommendedNdk);
            selectedJdkIndex = recommendedJdk;
            selectedNdkIndex = recommendedNdk;
            executeDetectAndBuild();
        });
        btnNeutral.setOnClickListener(v -> {
            dialog.dismiss();
            showCustomBuildChoiceFlow(projectDir, recommendedJdk, recommendedNdk);
        });
        dialog.show();
    }

    private void showCustomBuildChoiceFlow(final File projectDir, final int recommendedJdk, final int recommendedNdk) {
        final String[] jdkLabels = withRecommendation(EnvironmentManager.JDK_NAMES, recommendedJdk);
        final String[] ndkLabels = withRecommendation(EnvironmentManager.NDK_NAMES, recommendedNdk);
        final String[] routeLabels = {"国内路线（镜像优先）", "国外路线（官方优先）"};
        final String[] routeValues = {EnvironmentManager.DOWNLOAD_ROUTE_CHINA, EnvironmentManager.DOWNLOAD_ROUTE_GLOBAL};
        showOptionListDialog("自选 JDK", "打包前会先检查项目底层文件，再按你的选择继续。", jdkLabels, selectedJdkIndex, jdkIndex ->
            showOptionListDialog("自选 NDK", "这里可以切换你偏好的 NDK 版本。", ndkLabels, selectedNdkIndex, ndkIndex ->
                showOptionListDialog("下载路线", "安装环境和后续补齐文件时可走国内或国外路线。", routeLabels,
                    EnvironmentManager.DOWNLOAD_ROUTE_GLOBAL.equals(environmentManager.loadDownloadRoute()) ? 1 : 0,
                    routeIndex -> {
                        environmentManager.saveSelectedJdkIndex(jdkIndex);
                        environmentManager.saveSelectedNdkIndex(ndkIndex);
                        environmentManager.saveDownloadRoute(routeValues[routeIndex]);
                        selectedJdkIndex = jdkIndex;
                        selectedNdkIndex = ndkIndex;
                        executeDetectAndBuild();
                    }
                )
            )
        );
    }

    private void executeDetectAndBuild() {
        flushPendingAutoSave();
        showProgressDialog("检查错误代码", "正在进行打包前检查...");
        buildWorkflowService.checkAndBuild(
            currentProject,
            projectPrepared,
            environmentState,
            selectedJdkIndex,
            selectedNdkIndex,
            new BuildWorkflowService.Listener() {
                @Override
                public void onPreflightFailed(ArrayList<BuildIssue> issues) {
                    dismissProgressDialog();
                    lastBuildIssues.clear();
                    lastBuildIssues.addAll(issues);
                    updateBugButtonState();
                    logManager.appendLogLine("ERROR", "打包前检查未通过，已停止构建。");
                    appendIssueListToLogs("预检查发现的问题", lastBuildIssues);
                    if (!maybeShowWrapperRepairDialog("检查发现错误", lastBuildIssues)) {
                        showIssueDialog("检查发现错误", lastBuildIssues);
                    }
                }

                @Override
                public void onBuildStarted() {
                    isBuildRunning = true;
                    updateProgressDialog("检查通过，开始调用 Gradle 构建...");
                }

                @Override
                public void onBuildLog(String line) {
                    appendBuildOutput(line);
                }

                @Override
                public void onBuildFinished(BuildResult result) {
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
                            if (!maybeShowWrapperRepairDialog("构建失败，可点击定位错误", lastBuildIssues)) {
                                showIssueDialog("构建失败，可点击定位错误", lastBuildIssues);
                            }
                        } else {
                            toast("打包失败");
                        }
                    }
                }
            }
        );
    }

    private String[] withRecommendation(String[] source, int recommendedIndex) {
        String[] labels = new String[source.length];
        for (int i = 0; i < source.length; i++) {
            labels[i] = source[i] + (i == recommendedIndex ? "  · 推荐" : "");
        }
        return labels;
    }

    private String buildProjectBuildSummary(File projectDir) {
        StringBuilder builder = new StringBuilder();
        builder.append("打包前会自动检查架构、Gradle Wrapper、Manifest、构建脚本和环境匹配。\n");
        builder.append("当前检测到的架构：").append(readAbiSummary(projectDir)).append('\n');
        builder.append("底层文件：").append(readProjectStructureSummary(projectDir)).append('\n');
        builder.append("你可以直接走推荐路线，也可以改成自选路线。");
        return builder.toString();
    }

    private String readAbiSummary(File projectDir) {
        File appGradle = new File(projectDir, "app/build.gradle");
        if (!appGradle.exists()) {
            appGradle = new File(projectDir, "app/build.gradle.kts");
        }
        if (!appGradle.exists()) {
            return "未识别";
        }
        try {
            String content = projectWorkspaceService.readText(appGradle);
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("abiFilters\\s+([^\\n\\r]+)").matcher(content);
            if (matcher.find()) {
                String raw = matcher.group(1).replace("'", "").replace("\"", "").trim();
                raw = raw.replace(",", " / ");
                return raw.length() == 0 ? "未限制架构" : raw;
            }
        } catch (Exception e) {
        }
        return "未限制架构";
    }

    private String readProjectStructureSummary(File projectDir) {
        ArrayList<String> parts = new ArrayList<String>();
        parts.add(new File(projectDir, "app/src/main/AndroidManifest.xml").exists() ? "Manifest 已识别" : "Manifest 缺失");
        parts.add((new File(projectDir, "app/build.gradle").exists() || new File(projectDir, "app/build.gradle.kts").exists()) ? "构建脚本已识别" : "构建脚本缺失");
        parts.add(new File(projectDir, "gradle/wrapper/gradle-wrapper.properties").exists() ? "Wrapper 配置已识别" : "Wrapper 配置缺失");
        parts.add(new File(projectDir, "gradle/wrapper/gradle-wrapper.jar").exists() ? "Wrapper Jar 已识别" : "Wrapper Jar 缺失");
        return android.text.TextUtils.join(" / ", parts);
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

    private boolean maybeShowWrapperRepairDialog(String title, final ArrayList<BuildIssue> issues) {
        if (currentProject == null || issues == null || issues.isEmpty()) {
            return false;
        }
        boolean missingWrapper = false;
        final ArrayList<String> missingItems = new ArrayList<String>();
        for (int i = 0; i < issues.size(); i++) {
            BuildIssue issue = issues.get(i);
            String lower = issue.getMessage() == null ? "" : issue.getMessage().toLowerCase();
            if (lower.contains("gradle-wrapper.jar") || lower.contains("gradle-wrapper.properties")) {
                missingWrapper = true;
                missingItems.add(issue.getDisplayText());
            }
        }
        if (!missingWrapper) {
            return false;
        }
        showWrapperRepairDialog(title, missingItems);
        return true;
    }

    private void showIssueDialog(String title, final ArrayList<BuildIssue> issues) {
        final String[] items = new String[issues.size()];
        for (int i = 0; i < issues.size(); i++) {
            items[i] = issues.get(i).getDisplayText();
        }
        final Dialog dialog = createAppDialog(title + "（" + issues.size() + "条）", "点任意错误可直接定位到文件和行号。");
        ListView listView = (ListView) dialog.findViewById(R.id.lvDialogItems);
        Button btnPrimary = (Button) dialog.findViewById(R.id.btnDialogPrimary);
        Button btnSecondary = (Button) dialog.findViewById(R.id.btnDialogSecondary);
        Button btnNeutral = (Button) dialog.findViewById(R.id.btnDialogNeutral);
        listView.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, items));
        listView.setOnItemClickListener((parent, view, which, id) -> {
            dialog.dismiss();
            openIssue(issues.get(which));
        });
        btnNeutral.setVisibility(View.GONE);
        btnSecondary.setText("关闭");
        btnPrimary.setText("一键提取全部错误");
        btnSecondary.setOnClickListener(v -> dialog.dismiss());
        btnPrimary.setOnClickListener(v -> {
                ClipboardManager clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                clipboardManager.setPrimaryClip(ClipData.newPlainText("全部错误", buildIssueExtractionText(issues)));
                toast("全部错误已提取到剪贴板");
                dialog.dismiss();
            });
        dialog.show();
    }

    private void showWrapperRepairDialog(String title, ArrayList<String> missingItems) {
        final Dialog dialog = createAppDialog(title, "检测到目标项目缺少 Gradle Wrapper 文件。你可以直接在应用里一键补齐后继续打包。");
        ListView listView = (ListView) dialog.findViewById(R.id.lvDialogItems);
        Button btnPrimary = (Button) dialog.findViewById(R.id.btnDialogPrimary);
        Button btnSecondary = (Button) dialog.findViewById(R.id.btnDialogSecondary);
        Button btnNeutral = (Button) dialog.findViewById(R.id.btnDialogNeutral);
        listView.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, missingItems.toArray(new String[0])));
        listView.setOnItemClickListener((parent, view, which, id) -> {
        });
        btnPrimary.setText("仓库源补齐");
        btnNeutral.setVisibility(View.VISIBLE);
        btnNeutral.setText("官方源补齐");
        btnSecondary.setText("稍后处理");
        btnSecondary.setOnClickListener(v -> dialog.dismiss());
        btnPrimary.setOnClickListener(v -> {
            dialog.dismiss();
            repairWrapperAndRetry(false);
        });
        btnNeutral.setOnClickListener(v -> {
            dialog.dismiss();
            repairWrapperAndRetry(true);
        });
        dialog.show();
    }

    private void repairWrapperAndRetry(boolean useOfficialSource) {
        if (currentProject == null) {
            return;
        }
        String sourceLabel = useOfficialSource ? "官方源" : "仓库源";
        showProgressDialog("补齐 Gradle Wrapper", "正在通过" + sourceLabel + "补齐缺失文件...");
        buildManager.repairGradleWrapperAsync(currentProject.getProjectDir(), useOfficialSource, new BuildManager.WrapperRepairListener() {
            @Override
            public void onProgress(String message, int percent, boolean indeterminate) {
                handler.post(() -> updateProgressDialog(message, percent, indeterminate));
            }

            @Override
            public void onSuccess() {
                handler.post(() -> {
                    dismissProgressDialog();
                    toast("Gradle Wrapper 已补齐，正在重新检测");
                    detectAndBuild();
                });
            }

            @Override
            public void onError(String message) {
                handler.post(() -> {
                    dismissProgressDialog();
                    toast(message);
                });
            }
        });
    }

    private Dialog createAppDialog(String title, String subtitle) {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_issue_center);
        final View content = dialog.findViewById(R.id.tvDialogTitle).getRootView();
        if (palette != null) {
            themeManager.applyTaggedStyles(content, palette);
        }
        ((TextView) dialog.findViewById(R.id.tvDialogTitle)).setText(title);
        ((TextView) dialog.findViewById(R.id.tvDialogSubtitle)).setText(subtitle);
        dialog.setCancelable(true);
        dialog.setOnShowListener(dialogInterface -> animatePopupCard(content));
        return dialog;
    }

    private void openIssue(BuildIssue issue) {
        File issueFile = projectFileService.resolveIssueFile(currentProject, issue.getFilePath());
        if (issueFile != null && issueFile.exists() && issueFile.isFile() && projectFileService.isTextEditableFile(issueFile)) {
            openTextFile(issueFile);
            if (issue.getLineNumber() > 0) {
                etEditor.setDiagnosticLine(issue.getLineNumber());
                moveCursorToLine(issue.getLineNumber());
            } else {
                etEditor.clearDiagnosticLines();
            }
        }
        setSuggestionCardVisible(true, true);
        tvIssueTitle.setText(issue.getMessage());
        tvIssueFix.setText(issue.getSuggestion());
        currentCopiedFix = issue.getSuggestion();
        toast("已定位到错误代码");
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
        if (palette == null) {
            if (lastBuildIssues.isEmpty()) {
                btnBug.setBackgroundResource(R.drawable.bg_button_ghost);
            } else {
                btnBug.setBackgroundResource(R.drawable.bg_button_warn);
            }
            return;
        }
        if (lastBuildIssues.isEmpty()) {
            btnBug.setBackground(themeManager.createGhostButtonDrawable(palette));
        } else {
            btnBug.setBackground(themeManager.createWarnButtonDrawable(palette));
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

    private String buildIssueExtractionText(ArrayList<BuildIssue> issues) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < issues.size(); i++) {
            BuildIssue issue = issues.get(i);
            if (i > 0) {
                builder.append("\n\n");
            }
            builder.append(i + 1).append(". ").append(issue.getDisplayText());
            if (issue.getSuggestion() != null && issue.getSuggestion().length() > 0) {
                builder.append("\n建议：").append(issue.getSuggestion());
            }
        }
        return builder.toString();
    }

    private void validateCurrentEditorContent() {
        if (currentOpenFile == null) {
            etEditor.clearDiagnosticLines();
            return;
        }
        ArrayList<BuildIssue> issues = collectEditorIssues(currentOpenFile.getName(), etEditor.getText().toString());
        if (issues.isEmpty()) {
            etEditor.clearDiagnosticLines();
            if (suggestionCard.getVisibility() == View.VISIBLE && currentCopiedFix.startsWith("[编辑器检测]")) {
                setSuggestionCardVisible(false, true);
                currentCopiedFix = "";
            }
            return;
        }
        ArrayList<Integer> lines = new ArrayList<Integer>();
        for (int i = 0; i < issues.size(); i++) {
            if (issues.get(i).getLineNumber() > 0) {
                lines.add(issues.get(i).getLineNumber());
            }
        }
        etEditor.setDiagnosticLines(lines);
        BuildIssue firstIssue = issues.get(0);
        setSuggestionCardVisible(true, true);
        tvIssueTitle.setText(firstIssue.getMessage());
        tvIssueFix.setText(firstIssue.getSuggestion());
        currentCopiedFix = "[编辑器检测] " + firstIssue.getSuggestion();
    }

    private ArrayList<BuildIssue> collectEditorIssues(String fileName, String content) {
        ArrayList<BuildIssue> issues = new ArrayList<BuildIssue>();
        if (content == null) {
            return issues;
        }
        if (content.contains("<<<<<<<") || content.contains("=======") || content.contains(">>>>>>>")) {
            issues.add(new BuildIssue(fileName, -1, "检测到未处理的合并冲突标记。", "先删除冲突标记并保留正确代码，再继续编辑或打包。"));
        }
        String lowerName = fileName == null ? "" : fileName.toLowerCase();
        if (lowerName.endsWith(".xml")) {
            collectXmlEditorIssues(fileName, content, issues);
        } else if (lowerName.endsWith(".java") || lowerName.endsWith(".kt") || lowerName.endsWith(".gradle") || lowerName.endsWith(".kts")) {
            collectBraceIssues(fileName, content, issues);
        }
        return issues;
    }

    private void collectXmlEditorIssues(String fileName, String content, ArrayList<BuildIssue> issues) {
        try {
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader(content)));
        } catch (SAXParseException e) {
            issues.add(new BuildIssue(fileName, e.getLineNumber(), e.getMessage(), "检查 XML 标签闭合、属性引号和资源引用格式。"));
        } catch (Exception e) {
            issues.add(new BuildIssue(fileName, -1, e.getMessage(), "检查 XML 结构是否完整。"));
        }
    }

    private void collectBraceIssues(String fileName, String content, ArrayList<BuildIssue> issues) {
        int roundBalance = 0;
        int curlyBalance = 0;
        int squareBalance = 0;
        int lineNumber = 1;
        boolean inString = false;
        char stringQuote = 0;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '\n') {
                lineNumber++;
            }
            if (inString) {
                if (c == stringQuote && (i == 0 || content.charAt(i - 1) != '\\')) {
                    inString = false;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                inString = true;
                stringQuote = c;
                continue;
            }
            if (c == '(') {
                roundBalance++;
            } else if (c == ')') {
                roundBalance--;
            } else if (c == '{') {
                curlyBalance++;
            } else if (c == '}') {
                curlyBalance--;
            } else if (c == '[') {
                squareBalance++;
            } else if (c == ']') {
                squareBalance--;
            }
            if (roundBalance < 0 || curlyBalance < 0 || squareBalance < 0) {
                issues.add(new BuildIssue(fileName, lineNumber, "括号提前闭合，结构不平衡。", "检查这一行附近是否多写了 `)`、`}` 或 `]`。"));
                return;
            }
        }
        if (inString) {
            issues.add(new BuildIssue(fileName, lineNumber, "字符串没有正常闭合。", "检查最后一个字符串的引号是否缺失。"));
            return;
        }
        if (roundBalance != 0 || curlyBalance != 0 || squareBalance != 0) {
            issues.add(new BuildIssue(fileName, lineNumber, "括号数量不平衡。", "检查最近修改的位置，确认 `() { } [ ]` 是否成对出现。"));
        }
    }

    private void showProgressDialog(String title, String message) {
        showProgressDialog(title, message, -1, true);
    }

    private void updateProgressDialog(String message) {
        updateProgressDialog(message, -1, true);
    }

    private void dismissProgressDialog() {
        if (progressOverlay != null) {
            if (progressOverlay.getVisibility() == View.VISIBLE) {
                animateOverlayOut(progressOverlay, progressCard);
            } else {
                progressOverlay.setVisibility(View.GONE);
            }
        }
        if (progressBarFancy != null) {
            progressBarFancy.setIndeterminate(false);
            progressBarFancy.setProgress(0);
        }
    }

    private void showProgressDialog(String title, String message, int percent, boolean indeterminate) {
        if (progressOverlay == null) {
            return;
        }
        tvProgressTitle.setText(title);
        animateOverlayIn(progressOverlay, progressCard);
        updateProgressDialog(message, percent, indeterminate);
    }

    private void updateProgressDialog(String message, int percent, boolean indeterminate) {
        if (progressOverlay == null || progressBarFancy == null) {
            return;
        }
        tvProgressMessage.setText(message);
        progressBarFancy.setIndeterminate(indeterminate);
        if (indeterminate) {
            tvProgressPercent.setText("处理中");
        } else {
            int safePercent = Math.max(0, Math.min(100, percent));
            progressBarFancy.setProgress(safePercent);
            tvProgressPercent.setText(safePercent + "%");
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

    private String buildFileSecondaryText(FileTreeItem item) {
        if (item == null || item.getFile() == null) {
            return "";
        }
        File parent = item.getFile().getParentFile();
        if (currentProject == null || parent == null) {
            return item.isDirectory() ? "文件夹" : "文件";
        }
        String projectDir = currentProject.getProjectDir();
        String parentPath = parent.getAbsolutePath();
        if (parentPath.startsWith(projectDir)) {
            String relative = parentPath.substring(projectDir.length());
            if (relative.length() == 0) {
                relative = "/";
            }
            return (item.isDirectory() ? "目录 · 点进后进入新页面" : "文件 · ") + relative;
        }
        return item.isDirectory() ? "目录" : "文件";
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) {
            if (requestCode == REQUEST_PICK_IMAGE) {
                pendingImagePickerTarget = null;
            }
            return;
        }
        if (requestCode == REQUEST_FILE_BROWSER && resultCode == RESULT_OK && data != null) {
            String filePath = data.getStringExtra(FileBrowserActivity.EXTRA_SELECTED_FILE);
            if (filePath != null && filePath.length() > 0) {
                openTextFile(new File(filePath));
                hideFileDrawer(true);
            }
            return;
        }
        Uri pickedUri = data.getData();
        if (pickedUri == null) {
            if (requestCode == REQUEST_PICK_IMAGE) {
                pendingImagePickerTarget = null;
            }
            return;
        }
        if (requestCode == REQUEST_IMPORT_ZIP) {
            importZipProjectFromUri(pickedUri);
            return;
        }
        if (requestCode == REQUEST_IMPORT_FOLDER) {
            importFolderProjectFromUri(pickedUri);
            return;
        }
        if (requestCode == REQUEST_PICK_IMAGE) {
            applyPickedImageUri(pickedUri);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (editorPane != null && editorPane.getVisibility() == View.VISIBLE && event != null) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    gestureStartX = event.getX();
                    gestureStartY = event.getY();
                    swipeHandled = false;
                    break;
                case MotionEvent.ACTION_MOVE:
                case MotionEvent.ACTION_UP:
                    if (!swipeHandled) {
                        float dx = event.getX() - gestureStartX;
                        float dy = event.getY() - gestureStartY;
                        if (Math.abs(dx) > dp(56) && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                            if (dx > 0 && fileDrawer.getVisibility() != View.VISIBLE) {
                                showFileDrawer(true);
                                swipeHandled = true;
                            } else if (dx < 0 && fileDrawer.getVisibility() == View.VISIBLE) {
                                hideFileDrawer(true);
                                swipeHandled = true;
                            }
                        }
                    }
                    break;
                default:
                    break;
            }
        }
        return super.dispatchTouchEvent(event);
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
                card.setBackground(palette == null ? roundedDrawable("#182231", "#263246", 12) : themeManager.createPanelDrawable(palette, true));

                ImageView iconView = new ImageView(MainActivity.this);
                LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(52), dp(52));
                iconParams.rightMargin = dp(14);
                iconView.setLayoutParams(iconParams);
                iconView.setBackground(palette == null ? roundedDrawable("#0F141B", "#2A3850", 10) : themeManager.createChipDrawable(palette));
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
            convertView.setBackground(palette == null ? roundedDrawable("#182231", "#263246", 12) : themeManager.createPanelDrawable(palette, true));
            holder.icon.setBackground(palette == null ? roundedDrawable("#0F141B", "#2A3850", 10) : themeManager.createChipDrawable(palette));
            holder.title.setTextColor(palette == null ? Color.parseColor("#F3F7FD") : palette.textPrimary);
            holder.sub.setTextColor(palette == null ? Color.parseColor("#A2B0C3") : palette.textSecondary);
            holder.meta.setTextColor(palette == null ? Color.parseColor("#8FB6FF") : palette.accentStrong);
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
            animateListItem(convertView, position, true);
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
            FileTreeRowHolder holder;
            if (convertView == null) {
                LinearLayout row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(dp(12), dp(10), dp(12), dp(10));

                TextView title = new TextView(MainActivity.this);
                title.setTextSize(12.5f);
                title.setSingleLine(true);
                title.setEllipsize(android.text.TextUtils.TruncateAt.END);
                row.addView(title);

                TextView sub = new TextView(MainActivity.this);
                sub.setTextSize(10.5f);
                sub.setPadding(0, dp(3), 0, 0);
                sub.setSingleLine(true);
                sub.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
                row.addView(sub);

                holder = new FileTreeRowHolder(title, sub);
                row.setTag(holder);
                convertView = row;
            } else {
                holder = (FileTreeRowHolder) convertView.getTag();
            }
            FileTreeItem item = fileTreeItems.get(position);
            String prefix = item.isDirectory() ? "›  " : "·  ";
            convertView.setPadding(dp(16), dp(10), dp(12), dp(10));
            convertView.setBackground(palette == null ? roundedDrawable("#151B24", "", 8) : themeManager.createPanelDrawable(palette, false));
            holder.title.setText(prefix + item.getFile().getName());
            holder.title.setTextColor(item.isDirectory()
                ? (palette == null ? Color.parseColor("#F3F7FD") : palette.textPrimary)
                : (palette == null ? Color.parseColor("#B8C9E0") : palette.textSecondary));
            holder.sub.setText(buildFileSecondaryText(item));
            holder.sub.setTextColor(palette == null ? Color.parseColor("#7D8DA4") : palette.textMuted);
            holder.sub.setVisibility(View.VISIBLE);
            animateListItem(convertView, position, false);
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

    private static class FileTreeRowHolder {
        final TextView title;
        final TextView sub;

        FileTreeRowHolder(TextView title, TextView sub) {
            this.title = title;
            this.sub = sub;
        }
    }

    private static class FileNameComparator implements Comparator<File> {
        @Override
        public int compare(File a, File b) {
            return a.getName().compareToIgnoreCase(b.getName());
        }
    }

    private static class ImagePickerTarget {
        LinearLayout cardView;
        ImageView previewView;
        TextView statusView;
        Button clearButton;
        String cachedPath = "";
    }

    private interface PathPickListener {
        void onPicked(File path);
    }

    private interface OptionSelectListener {
        void onSelected(int which);
    }
}
