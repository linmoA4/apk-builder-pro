package com.LM.pack;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import com.LM.pack.env.EnvironmentManager;
import com.LM.pack.env.ToolchainInstaller;
import com.LM.pack.model.EnvironmentState;
import com.LM.pack.theme.AppThemePalette;
import com.LM.pack.theme.LiquidGlassBackgroundView;
import com.LM.pack.theme.ThemeManager;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Map;

public class SettingsActivity extends Activity {

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
    private EnvironmentManager environmentManager;
    private ToolchainInstaller toolchainInstaller;
    private ThemeManager themeManager;
    private EnvironmentState environmentState;
    private AppThemePalette palette;
    private int selectedJdkIndex;
    private int selectedNdkIndex;
    private boolean preparingEmbeddedTools;

    private LiquidGlassBackgroundView bgSceneView;
    private Button btnBackSettings;
    private Button btnPrepareEmbedded;
    private Button btnOpenSdkDir;
    private Button btnOpenJdkDir;
    private Button btnOpenNdkDir;
    private Button btnSelectOnlineJdk;
    private Button btnSelectOnlineNdk;
    private Button btnAppearanceMode;
    private Button btnSurfaceStyle;
    private Button btnCoreTools;
    private Button btnCrossTools;
    private Button btnTestTools;
    private Button btnDevopsTools;
    private Button btnMirrorTools;
    private TextView tvEnvironmentSummary;
    private TextView tvSdkHint;
    private TextView tvJdkSourceHint;
    private TextView tvNdkSourceHint;
    private TextView tvThemeSummary;
    private TextView tvDirectoryPlan;
    private TextView tvReferenceOutput;
    private View progressOverlay;
    private TextView tvProgressTitle;
    private TextView tvProgressMessage;
    private TextView tvProgressPercent;
    private ProgressBar progressBarInstall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        handler = new Handler(getMainLooper());
        SharedPreferences sharedPreferences = getSharedPreferences(EnvironmentManager.PREFS_NAME, MODE_PRIVATE);
        environmentManager = new EnvironmentManager(this, sharedPreferences);
        toolchainInstaller = new ToolchainInstaller(this, environmentManager);
        themeManager = new ThemeManager(this);
        environmentState = environmentManager.loadState();
        selectedJdkIndex = environmentManager.loadSelectedJdkIndex();
        selectedNdkIndex = environmentManager.loadSelectedNdkIndex();

        bindViews();
        bindEvents();
        applyThemeUi();
        refreshUi();
        maybePrepareEmbeddedTools(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        environmentState = environmentManager.loadState();
        selectedJdkIndex = environmentManager.loadSelectedJdkIndex();
        selectedNdkIndex = environmentManager.loadSelectedNdkIndex();
        applyThemeUi();
        refreshUi();
    }

    private void bindViews() {
        bgSceneView = (LiquidGlassBackgroundView) findViewById(R.id.bgSceneView);
        btnBackSettings = (Button) findViewById(R.id.btnBackSettings);
        btnPrepareEmbedded = (Button) findViewById(R.id.btnPrepareEmbedded);
        btnOpenSdkDir = (Button) findViewById(R.id.btnOpenSdkDir);
        btnOpenJdkDir = (Button) findViewById(R.id.btnOpenJdkDir);
        btnOpenNdkDir = (Button) findViewById(R.id.btnOpenNdkDir);
        btnSelectOnlineJdk = (Button) findViewById(R.id.btnSelectOnlineJdk);
        btnSelectOnlineNdk = (Button) findViewById(R.id.btnSelectOnlineNdk);
        btnAppearanceMode = (Button) findViewById(R.id.btnAppearanceMode);
        btnSurfaceStyle = (Button) findViewById(R.id.btnSurfaceStyle);
        btnCoreTools = (Button) findViewById(R.id.btnCoreTools);
        btnCrossTools = (Button) findViewById(R.id.btnCrossTools);
        btnTestTools = (Button) findViewById(R.id.btnTestTools);
        btnDevopsTools = (Button) findViewById(R.id.btnDevopsTools);
        btnMirrorTools = (Button) findViewById(R.id.btnMirrorTools);
        tvEnvironmentSummary = (TextView) findViewById(R.id.tvEnvironmentSummary);
        tvSdkHint = (TextView) findViewById(R.id.tvSdkHint);
        tvJdkSourceHint = (TextView) findViewById(R.id.tvJdkSourceHint);
        tvNdkSourceHint = (TextView) findViewById(R.id.tvNdkSourceHint);
        tvThemeSummary = (TextView) findViewById(R.id.tvThemeSummary);
        tvDirectoryPlan = (TextView) findViewById(R.id.tvDirectoryPlan);
        tvReferenceOutput = (TextView) findViewById(R.id.tvReferenceOutput);
        progressOverlay = findViewById(R.id.progressOverlay);
        tvProgressTitle = (TextView) findViewById(R.id.tvProgressTitle);
        tvProgressMessage = (TextView) findViewById(R.id.tvProgressMessage);
        tvProgressPercent = (TextView) findViewById(R.id.tvProgressPercent);
        progressBarInstall = (ProgressBar) findViewById(R.id.progressBarInstall);
    }

    private void bindEvents() {
        btnBackSettings.setOnClickListener(v -> finish());
        btnPrepareEmbedded.setOnClickListener(v -> maybePrepareEmbeddedTools(true));
        btnOpenSdkDir.setOnClickListener(v -> showDirectoryDialog("Android SDK 目录", new File(environmentManager.getEmbeddedSdkInstallDir())));
        btnOpenJdkDir.setOnClickListener(v -> showDirectoryDialog("JDK 目录", new File(new File(environmentManager.getBaseDir()), "jdk")));
        btnOpenNdkDir.setOnClickListener(v -> showDirectoryDialog("NDK 目录", new File(new File(environmentManager.getBaseDir()), "ndk")));
        btnSelectOnlineJdk.setOnClickListener(v -> showVersionDialog(true));
        btnSelectOnlineNdk.setOnClickListener(v -> showVersionDialog(false));
        btnAppearanceMode.setOnClickListener(v -> showAppearanceModeDialog());
        btnSurfaceStyle.setOnClickListener(v -> showSurfaceStyleDialog());
        btnCoreTools.setOnClickListener(v -> showCatalogDialog("核心构建工具", CORE_TOOL_ITEMS));
        btnCrossTools.setOnClickListener(v -> showCatalogDialog("跨平台开发框架", CROSS_PLATFORM_ITEMS));
        btnTestTools.setOnClickListener(v -> showCatalogDialog("测试与仿真环境", TEST_TOOL_ITEMS));
        btnDevopsTools.setOnClickListener(v -> showCatalogDialog("版本控制与调试", DEVOPS_TOOL_ITEMS));
        btnMirrorTools.setOnClickListener(v -> showCatalogDialog("国内镜像命令", MIRROR_COMMAND_ITEMS));
    }

    private void applyThemeUi() {
        palette = themeManager.getPalette(this);
        themeManager.applyActivityWindow(this, palette);
        if (bgSceneView != null) {
            bgSceneView.setPalette(palette);
        }
        themeManager.applyTaggedStyles(findViewById(R.id.settingsRoot), palette);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            progressBarInstall.setProgressTintList(ColorStateList.valueOf(palette.accent));
            progressBarInstall.setIndeterminateTintList(ColorStateList.valueOf(palette.accent));
        }
    }

    private void refreshUi() {
        environmentState = environmentManager.loadState();
        tvEnvironmentSummary.setText(buildEnvironmentSummary());
        tvSdkHint.setText(buildEmbeddedSummary());
        tvJdkSourceHint.setText("当前 JDK：" + EnvironmentManager.JDK_NAMES[selectedJdkIndex] + " · " + describeJdkSource(selectedJdkIndex));
        tvNdkSourceHint.setText("当前 NDK：" + EnvironmentManager.NDK_NAMES[selectedNdkIndex] + " · " + describeNdkSource(selectedNdkIndex));
        tvThemeSummary.setText(buildThemeSummary());
        tvDirectoryPlan.setText(buildDirectoryPlan());
        tvReferenceOutput.setText("资料入口已经改成按钮弹窗，当前页不再铺长文本。");
    }

    private void maybePrepareEmbeddedTools(boolean userTriggered) {
        if (preparingEmbeddedTools) {
            return;
        }
        final int embeddedJdkIndex = environmentManager.findEmbeddedJdkIndex();
        final int embeddedNdkIndex = environmentManager.findEmbeddedNdkIndex();
        boolean needSdk = !environmentManager.isAndroidSdkRegistered(environmentState);
        boolean needJdk = embeddedJdkIndex >= 0 && !environmentManager.isSelectedJdkInstalled(embeddedJdkIndex, environmentState);
        boolean needNdk = embeddedNdkIndex >= 0 && !environmentManager.isSelectedNdkInstalled(embeddedNdkIndex, environmentState);
        if (!needSdk && !needJdk && !needNdk) {
            if (userTriggered) {
                toast("内置环境已经准备完成");
            }
            return;
        }
        if (!assetExists(EnvironmentManager.SDK_ASSET_ARCHIVE) && needSdk) {
            if (userTriggered) {
                toast("当前 APK 内还没有打进 SDK 压缩包");
            }
            return;
        }
        preparingEmbeddedTools = true;
        showProgressOverlay("内置环境", "正在准备内置工具链...", 0, true);
        prepareEmbeddedSdkThenRest(embeddedJdkIndex, embeddedNdkIndex, userTriggered);
    }

    private void prepareEmbeddedSdkThenRest(final int embeddedJdkIndex, final int embeddedNdkIndex, final boolean userTriggered) {
        boolean needSdk = !environmentManager.isAndroidSdkRegistered(environmentState);
        if (!needSdk) {
            prepareEmbeddedJdkThenNdk(embeddedJdkIndex, embeddedNdkIndex, userTriggered);
            return;
        }
        toolchainInstaller.installEmbeddedSdk(new ToolchainInstaller.InstallListener() {
            @Override
            public void onProgress(final String message, final int percent, final boolean indeterminate) {
                handler.post(() -> showProgressOverlay("解压内置 SDK", message, percent, indeterminate));
            }

            @Override
            public void onSuccess(final String installedDir) {
                handler.post(() -> {
                    environmentState = environmentManager.saveAndroidSdkDir(installedDir);
                    prepareEmbeddedJdkThenNdk(embeddedJdkIndex, embeddedNdkIndex, userTriggered);
                });
            }

            @Override
            public void onError(final String message) {
                handler.post(() -> finishPreparationWithError(message));
            }
        });
    }

    private void prepareEmbeddedJdkThenNdk(final int embeddedJdkIndex, final int embeddedNdkIndex, final boolean userTriggered) {
        boolean needJdk = embeddedJdkIndex >= 0 && !environmentManager.isSelectedJdkInstalled(embeddedJdkIndex, environmentState);
        if (!needJdk) {
            prepareEmbeddedNdk(embeddedNdkIndex, userTriggered);
            return;
        }
        toolchainInstaller.installJdk(embeddedJdkIndex, new ToolchainInstaller.InstallListener() {
            @Override
            public void onProgress(final String message, final int percent, final boolean indeterminate) {
                handler.post(() -> showProgressOverlay("解压内置 JDK 21", message, percent, indeterminate));
            }

            @Override
            public void onSuccess(final String installedDir) {
                handler.post(() -> {
                    environmentState = environmentManager.saveInstalledJdk(EnvironmentManager.JDK_NAMES[embeddedJdkIndex], installedDir);
                    if (selectedJdkIndex == embeddedJdkIndex || selectedJdkIndex < 0) {
                        environmentManager.saveSelectedJdkIndex(embeddedJdkIndex);
                        selectedJdkIndex = embeddedJdkIndex;
                    }
                    prepareEmbeddedNdk(embeddedNdkIndex, userTriggered);
                });
            }

            @Override
            public void onError(final String message) {
                handler.post(() -> finishPreparationWithError(message));
            }
        });
    }

    private void prepareEmbeddedNdk(final int embeddedNdkIndex, final boolean userTriggered) {
        boolean needNdk = embeddedNdkIndex >= 0 && !environmentManager.isSelectedNdkInstalled(embeddedNdkIndex, environmentState);
        if (!needNdk) {
            finishPreparationSuccess(userTriggered);
            return;
        }
        toolchainInstaller.installNdk(embeddedNdkIndex, new ToolchainInstaller.InstallListener() {
            @Override
            public void onProgress(final String message, final int percent, final boolean indeterminate) {
                handler.post(() -> showProgressOverlay("解压内置 NDK r27", message, percent, indeterminate));
            }

            @Override
            public void onSuccess(final String installedDir) {
                handler.post(() -> {
                    environmentState = environmentManager.saveInstalledNdk(EnvironmentManager.NDK_NAMES[embeddedNdkIndex], installedDir);
                    if (selectedNdkIndex == embeddedNdkIndex || selectedNdkIndex < 0) {
                        environmentManager.saveSelectedNdkIndex(embeddedNdkIndex);
                        selectedNdkIndex = embeddedNdkIndex;
                    }
                    finishPreparationSuccess(userTriggered);
                });
            }

            @Override
            public void onError(final String message) {
                handler.post(() -> finishPreparationWithError(message));
            }
        });
    }

    private void finishPreparationSuccess(boolean userTriggered) {
        preparingEmbeddedTools = false;
        hideProgressOverlay();
        refreshUi();
        if (userTriggered) {
            toast("内置环境准备完成");
        }
    }

    private void finishPreparationWithError(String message) {
        preparingEmbeddedTools = false;
        hideProgressOverlay();
        refreshUi();
        toast(message);
    }

    private void installSelectedJdk() {
        final String jdkName = EnvironmentManager.JDK_NAMES[selectedJdkIndex];
        showProgressOverlay("安装 JDK", "正在准备 " + jdkName + " ...", 0, true);
        toolchainInstaller.installJdk(selectedJdkIndex, new ToolchainInstaller.InstallListener() {
            @Override
            public void onProgress(final String message, final int percent, final boolean indeterminate) {
                handler.post(() -> showProgressOverlay("安装 " + jdkName, message, percent, indeterminate));
            }

            @Override
            public void onSuccess(final String installedDir) {
                handler.post(() -> {
                    hideProgressOverlay();
                    environmentState = environmentManager.saveInstalledJdk(jdkName, installedDir);
                    refreshUi();
                    toast("JDK 安装完成");
                });
            }

            @Override
            public void onError(final String message) {
                handler.post(() -> {
                    hideProgressOverlay();
                    toast(message);
                });
            }
        });
    }

    private void installSelectedNdk() {
        final String ndkName = EnvironmentManager.NDK_NAMES[selectedNdkIndex];
        showProgressOverlay("安装 NDK", "正在准备 " + ndkName + " ...", 0, true);
        toolchainInstaller.installNdk(selectedNdkIndex, new ToolchainInstaller.InstallListener() {
            @Override
            public void onProgress(final String message, final int percent, final boolean indeterminate) {
                handler.post(() -> showProgressOverlay("安装 " + ndkName, message, percent, indeterminate));
            }

            @Override
            public void onSuccess(final String installedDir) {
                handler.post(() -> {
                    hideProgressOverlay();
                    environmentState = environmentManager.saveInstalledNdk(ndkName, installedDir);
                    refreshUi();
                    toast("NDK 安装完成");
                });
            }

            @Override
            public void onError(final String message) {
                handler.post(() -> {
                    hideProgressOverlay();
                    toast(message);
                });
            }
        });
    }

    private void showAppearanceModeDialog() {
        final String[] labels = {"跟随系统", "浅色", "深色"};
        final String[] values = {ThemeManager.MODE_SYSTEM, ThemeManager.MODE_LIGHT, ThemeManager.MODE_DARK};
        int checked = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(themeManager.getAppearanceMode())) {
                checked = i;
                break;
            }
        }
        new AlertDialog.Builder(this)
            .setTitle("选择亮暗模式")
            .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                themeManager.setAppearanceMode(values[which]);
                applyThemeUi();
                refreshUi();
                dialog.dismiss();
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void showSurfaceStyleDialog() {
        final String[] labels = {"正常主题", "液态玻璃主题"};
        final String[] values = {ThemeManager.STYLE_NORMAL, ThemeManager.STYLE_LIQUID};
        int checked = ThemeManager.STYLE_LIQUID.equals(themeManager.getSurfaceStyle()) ? 1 : 0;
        new AlertDialog.Builder(this)
            .setTitle("选择主题材质")
            .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                themeManager.setSurfaceStyle(values[which]);
                applyThemeUi();
                refreshUi();
                dialog.dismiss();
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void showVersionDialog(final boolean forJdk) {
        final String[] names = forJdk ? EnvironmentManager.JDK_NAMES : EnvironmentManager.NDK_NAMES;
        final String[] items = new String[names.length];
        for (int i = 0; i < names.length; i++) {
            items[i] = names[i] + "\n" + (forJdk ? describeJdkSource(i) : describeNdkSource(i));
        }
        new AlertDialog.Builder(this)
            .setTitle(forJdk ? "选择 JDK 版本" : "选择 NDK 版本")
            .setItems(items, (dialog, which) -> {
                if (forJdk) {
                    selectedJdkIndex = which;
                    environmentManager.saveSelectedJdkIndex(which);
                    refreshUi();
                    if (environmentManager.isEmbeddedJdk(which)) {
                        maybePrepareEmbeddedTools(true);
                    } else {
                        installSelectedJdk();
                    }
                } else {
                    selectedNdkIndex = which;
                    environmentManager.saveSelectedNdkIndex(which);
                    refreshUi();
                    if (environmentManager.isEmbeddedNdk(which)) {
                        maybePrepareEmbeddedTools(true);
                    } else {
                        installSelectedNdk();
                    }
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void showCatalogDialog(String title, String[] items) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < items.length; i++) {
            if (i > 0) {
                builder.append("\n\n");
            }
            builder.append(i + 1).append(". ").append(items[i]);
        }
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(builder.toString())
            .setPositiveButton("关闭", null)
            .show();
    }

    private void showDirectoryDialog(final String title, File initialDir) {
        final ArrayList<File> visibleFiles = new ArrayList<File>();
        final ArrayList<String> labels = new ArrayList<String>();
        final ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, labels);
        final File[] currentDir = {initialDir};
        final ListView listView = new ListView(this);
        listView.setAdapter(adapter);

        final AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(title)
            .setView(listView)
            .setNegativeButton("关闭", null)
            .create();

        final Runnable refresh = new Runnable() {
            @Override
            public void run() {
                populateDirectoryEntries(currentDir[0], visibleFiles, labels);
                dialog.setTitle(title + "\n" + currentDir[0].getAbsolutePath());
                adapter.notifyDataSetChanged();
            }
        };

        listView.setOnItemClickListener((parent, view, position, id) -> {
            File file = visibleFiles.get(position);
            if (file == null) {
                File parentDir = currentDir[0].getParentFile();
                if (parentDir != null) {
                    currentDir[0] = parentDir;
                    refresh.run();
                }
                return;
            }
            if (file.isDirectory()) {
                currentDir[0] = file;
                refresh.run();
                return;
            }
            toast(file.getName());
        });

        refresh.run();
        dialog.show();
    }

    private void populateDirectoryEntries(File dir, ArrayList<File> visibleFiles, ArrayList<String> labels) {
        visibleFiles.clear();
        labels.clear();
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            labels.add("目录不存在");
            visibleFiles.add(new File(""));
            return;
        }
        if (dir.getParentFile() != null) {
            visibleFiles.add(null);
            labels.add(".. 返回上一级");
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        ArrayList<File> dirs = new ArrayList<File>();
        ArrayList<File> normals = new ArrayList<File>();
        for (int i = 0; i < files.length; i++) {
            if (files[i].isDirectory()) {
                dirs.add(files[i]);
            } else {
                normals.add(files[i]);
            }
        }
        java.util.Collections.sort(dirs, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        java.util.Collections.sort(normals, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (int i = 0; i < dirs.size(); i++) {
            visibleFiles.add(dirs.get(i));
            labels.add("📁 " + dirs.get(i).getName());
        }
        for (int i = 0; i < normals.size(); i++) {
            visibleFiles.add(normals.get(i));
            labels.add("📄 " + normals.get(i).getName());
        }
    }

    private void showProgressOverlay(String title, String message, int percent, boolean indeterminate) {
        progressOverlay.setVisibility(View.VISIBLE);
        tvProgressTitle.setText(title);
        tvProgressMessage.setText(message);
        progressBarInstall.setIndeterminate(indeterminate);
        if (!indeterminate) {
            progressBarInstall.setProgress(percent);
            tvProgressPercent.setText(percent + "%");
        } else {
            tvProgressPercent.setText("处理中");
        }
    }

    private void hideProgressOverlay() {
        progressOverlay.setVisibility(View.GONE);
        progressBarInstall.setIndeterminate(false);
        progressBarInstall.setProgress(0);
        tvProgressPercent.setText("");
    }

    private String buildEnvironmentSummary() {
        StringBuilder builder = new StringBuilder();
        builder.append("Android SDK：").append(safeText(environmentState.getAndroidSdkDir(), "未准备")).append('\n');
        builder.append("JDK：").append(EnvironmentManager.JDK_NAMES[selectedJdkIndex]).append(" · ")
            .append(safeText(environmentManager.getSelectedJdkDir(selectedJdkIndex, environmentState), "未准备")).append('\n');
        builder.append("NDK：").append(EnvironmentManager.NDK_NAMES[selectedNdkIndex]).append(" · ")
            .append(safeText(environmentManager.getSelectedNdkDir(selectedNdkIndex, environmentState), "未准备")).append('\n');
        builder.append("已登记 JDK：").append(buildRegisteredToolSummary(environmentState.getInstalledJdks())).append('\n');
        builder.append("已登记 NDK：").append(buildRegisteredToolSummary(environmentState.getInstalledNdks())).append('\n');
        builder.append("当前选择可用：JDK ")
            .append(environmentManager.isSelectedJdkInstalled(selectedJdkIndex, environmentState) ? "是" : "否")
            .append(" / NDK ")
            .append(environmentManager.isSelectedNdkInstalled(selectedNdkIndex, environmentState) ? "是" : "否");
        return builder.toString();
    }

    private String buildEmbeddedSummary() {
        String sdkStatus = environmentManager.isAndroidSdkRegistered(environmentState) ? "已解压" : "未解压";
        int embeddedJdk = environmentManager.findEmbeddedJdkIndex();
        int embeddedNdk = environmentManager.findEmbeddedNdkIndex();
        String jdkStatus = embeddedJdk >= 0 && environmentManager.isSelectedJdkInstalled(embeddedJdk, environmentState) ? "已准备" : "未准备";
        String ndkStatus = embeddedNdk >= 0 && environmentManager.isSelectedNdkInstalled(embeddedNdk, environmentState) ? "已准备" : "未准备";
        return "SDK 状态：" + sdkStatus + "；JDK 21：" + jdkStatus + "；NDK r27：" + ndkStatus
            + "。内置资源会自动解压到工作目录，设置页不再显示旧的 NDK 安装表单。";
    }

    private String buildThemeSummary() {
        String appearanceLabel = "跟随系统";
        if (ThemeManager.MODE_LIGHT.equals(themeManager.getAppearanceMode())) {
            appearanceLabel = "浅色";
        } else if (ThemeManager.MODE_DARK.equals(themeManager.getAppearanceMode())) {
            appearanceLabel = "深色";
        }
        String surfaceLabel = ThemeManager.STYLE_LIQUID.equals(themeManager.getSurfaceStyle()) ? "液态玻璃主题" : "正常主题";
        return "亮暗模式：" + appearanceLabel + "；材质主题：" + surfaceLabel
            + "。液态玻璃会启用高斯感背景、冷色菲涅尔边缘和高透明大圆角容器。";
    }

    private String buildDirectoryPlan() {
        StringBuilder builder = new StringBuilder();
        builder.append("工作目录：").append(environmentManager.getBaseDir()).append('\n');
        builder.append("缓存目录：").append(environmentManager.getPackageCacheDir()).append('\n');
        builder.append("SDK 根目录：").append(environmentManager.getEmbeddedSdkInstallDir()).append('\n');
        builder.append("SDK cmdline-tools：").append(environmentManager.getEmbeddedSdkCmdlineToolsDir()).append('\n');
        builder.append("JDK 根目录：").append(new File(environmentManager.getBaseDir(), "jdk").getAbsolutePath()).append('\n');
        builder.append("NDK 根目录：").append(new File(environmentManager.getBaseDir(), "ndk").getAbsolutePath()).append('\n');
        builder.append("项目目录：").append(environmentManager.getManagedProjectRootDir()).append('\n');
        builder.append("Gradle 离线目录：").append(environmentManager.getGradleInstallDir());
        return builder.toString();
    }

    private String describeJdkSource(int index) {
        return environmentManager.isEmbeddedJdk(index) ? "内置压缩包自动解压" : "联网校验下载链路后安装";
    }

    private String describeNdkSource(int index) {
        return environmentManager.isEmbeddedNdk(index) ? "内置压缩包自动解压" : "联网校验下载链路后安装";
    }

    private String buildRegisteredToolSummary(Map<String, String> tools) {
        if (tools == null || tools.isEmpty()) {
            return "未登记";
        }
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : tools.entrySet()) {
            if (!first) {
                builder.append("；");
            }
            builder.append(entry.getKey());
            first = false;
        }
        return builder.toString();
    }

    private boolean assetExists(String assetPath) {
        InputStream inputStream = null;
        try {
            inputStream = getAssets().open(assetPath);
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (Exception ignored) {
            }
        }
    }

    private String safeText(String value, String fallback) {
        if (value == null || value.trim().length() == 0) {
            return fallback;
        }
        return value.trim();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
