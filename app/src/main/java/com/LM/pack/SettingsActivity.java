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
import com.LM.pack.build.BuildManager;
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
    private BuildManager buildManager;
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
    private Button btnOpenGradleDir;
    private Button btnAppearanceMode;
    private Button btnSurfaceStyle;
    private TextView tvEnvironmentSummary;
    private TextView tvSdkHint;
    private TextView tvConfigHint;
    private TextView tvGradleHint;
    private TextView tvThemeSummary;
    private TextView tvDirectoryPlan;
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
        buildManager = new BuildManager(this, environmentManager);
        themeManager = new ThemeManager(this);
        environmentState = environmentManager.loadState();
        selectedJdkIndex = EnvironmentManager.EMBEDDED_JDK_INDEX;
        selectedNdkIndex = EnvironmentManager.EMBEDDED_NDK_INDEX;
        environmentManager.saveSelectedJdkIndex(selectedJdkIndex);
        environmentManager.saveSelectedNdkIndex(selectedNdkIndex);

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
        selectedJdkIndex = EnvironmentManager.EMBEDDED_JDK_INDEX;
        selectedNdkIndex = EnvironmentManager.EMBEDDED_NDK_INDEX;
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
        btnOpenGradleDir = (Button) findViewById(R.id.btnOpenGradleDir);
        btnAppearanceMode = (Button) findViewById(R.id.btnAppearanceMode);
        btnSurfaceStyle = (Button) findViewById(R.id.btnSurfaceStyle);
        tvEnvironmentSummary = (TextView) findViewById(R.id.tvEnvironmentSummary);
        tvSdkHint = (TextView) findViewById(R.id.tvSdkHint);
        tvConfigHint = (TextView) findViewById(R.id.tvConfigHint);
        tvGradleHint = (TextView) findViewById(R.id.tvGradleHint);
        tvThemeSummary = (TextView) findViewById(R.id.tvThemeSummary);
        tvDirectoryPlan = (TextView) findViewById(R.id.tvDirectoryPlan);
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
        btnOpenGradleDir.setOnClickListener(v -> showDirectoryDialog("Gradle 目录", new File(environmentManager.getGradleInstallDir())));
        btnAppearanceMode.setOnClickListener(v -> showAppearanceModeDialog());
        btnSurfaceStyle.setOnClickListener(v -> showSurfaceStyleDialog());
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
        tvConfigHint.setText(buildConfigSummary());
        tvGradleHint.setText(buildGradleSummary());
        tvThemeSummary.setText(buildThemeSummary());
        tvDirectoryPlan.setText(buildDirectoryPlan());
        btnPrepareEmbedded.setText(buildPrepareButtonText());
    }

    private void maybePrepareEmbeddedTools(boolean userTriggered) {
        if (preparingEmbeddedTools) {
            return;
        }
        final int embeddedJdkIndex = EnvironmentManager.EMBEDDED_JDK_INDEX;
        final int embeddedNdkIndex = EnvironmentManager.EMBEDDED_NDK_INDEX;
        boolean needSdk = !environmentManager.isAndroidSdkRegistered(environmentState);
        boolean needJdk = embeddedJdkIndex >= 0 && !environmentManager.isSelectedJdkInstalled(embeddedJdkIndex, environmentState);
        boolean needNdk = embeddedNdkIndex >= 0 && !environmentManager.isSelectedNdkInstalled(embeddedNdkIndex, environmentState);
        boolean needGradle = !buildManager.isOfflineGradlePrepared();
        if (!needSdk && !needJdk && !needNdk && !needGradle) {
            if (userTriggered) {
                toast("4 个环境已经准备完成");
            }
            return;
        }
        environmentManager.saveSelectedJdkIndex(embeddedJdkIndex);
        environmentManager.saveSelectedNdkIndex(embeddedNdkIndex);
        selectedJdkIndex = embeddedJdkIndex;
        selectedNdkIndex = embeddedNdkIndex;
        preparingEmbeddedTools = true;
        showProgressOverlay("自动检测环境", "正在检测 SDK / JDK 21 / NDK 27 / Gradle 8.7 ...", 0, true);
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
            prepareOfflineGradle(userTriggered);
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
                    prepareOfflineGradle(userTriggered);
                });
            }

            @Override
            public void onError(final String message) {
                handler.post(() -> finishPreparationWithError(message));
            }
        });
    }

    private void prepareOfflineGradle(final boolean userTriggered) {
        if (buildManager.isOfflineGradlePrepared()) {
            finishPreparationSuccess(userTriggered);
            return;
        }
        buildManager.prepareOfflineGradleAsync(new BuildManager.OfflineGradleListener() {
            @Override
            public void onProgress(final String message, final int percent, final boolean indeterminate) {
                handler.post(() -> showProgressOverlay("解压内置 Gradle 8.7", message, percent, indeterminate));
            }

            @Override
            public void onSuccess(final File gradleExecutable) {
                handler.post(() -> finishPreparationSuccess(userTriggered));
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
            toast("SDK / JDK 21 / NDK 27 / Gradle 8.7 已准备完成");
        }
    }

    private void finishPreparationWithError(String message) {
        preparingEmbeddedTools = false;
        hideProgressOverlay();
        refreshUi();
        toast(message);
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
        builder.append("Android SDK：").append(environmentManager.isAndroidSdkRegistered(environmentState) ? "已准备" : "未准备").append('\n');
        builder.append("JDK 21：").append(environmentManager.isSelectedJdkInstalled(selectedJdkIndex, environmentState) ? "已准备" : "未准备").append('\n');
        builder.append("NDK r27：").append(environmentManager.isSelectedNdkInstalled(selectedNdkIndex, environmentState) ? "已准备" : "未准备").append('\n');
        builder.append("Gradle 8.7：").append(buildManager.isOfflineGradlePrepared() ? "已准备" : "未准备").append('\n');
        builder.append("SDK 目录：").append(safeText(environmentState.getAndroidSdkDir(), "未登记")).append('\n');
        builder.append("JDK 目录：").append(safeText(environmentManager.getSelectedJdkDir(selectedJdkIndex, environmentState), "未登记")).append('\n');
        builder.append("NDK 目录：").append(safeText(environmentManager.getSelectedNdkDir(selectedNdkIndex, environmentState), "未登记")).append('\n');
        builder.append("Gradle 目录：").append(environmentManager.getGradleInstallDir());
        return builder.toString();
    }

    private String buildEmbeddedSummary() {
        String sdkStatus = environmentManager.isAndroidSdkRegistered(environmentState) ? "已解压" : "未解压";
        int embeddedJdk = EnvironmentManager.EMBEDDED_JDK_INDEX;
        int embeddedNdk = EnvironmentManager.EMBEDDED_NDK_INDEX;
        String jdkStatus = embeddedJdk >= 0 && environmentManager.isSelectedJdkInstalled(embeddedJdk, environmentState) ? "已准备" : "未准备";
        String ndkStatus = embeddedNdk >= 0 && environmentManager.isSelectedNdkInstalled(embeddedNdk, environmentState) ? "已准备" : "未准备";
        String gradleStatus = buildManager.isOfflineGradlePrepared() ? "已准备" : "未准备";
        return "自动检测按钮会统一处理 SDK、JDK 21、NDK r27 和 Gradle 8.7。"
            + " 当前状态：SDK " + sdkStatus + "；JDK 21 " + jdkStatus + "；NDK r27 " + ndkStatus + "；Gradle 8.7 " + gradleStatus + "。";
    }

    private String buildConfigSummary() {
        return "当前已经固定为内置环境优先：JDK 21、NDK r27、Android SDK、Gradle 8.7。"
            + " 如果 APK 内没有打包资源，会自动走联网下载并继续显示真实解压进度。"
            + " 下载器里也已经配置了直链源：SDK " + EnvironmentManager.SDK_VERIFIED_DIRECT_URLS.length
            + " 条、JDK " + EnvironmentManager.JDK_VERIFIED_DIRECT_URLS.length
            + " 条、Gradle " + EnvironmentManager.GRADLE_VERIFIED_DIRECT_URLS.length + " 条。";
    }

    private String buildGradleSummary() {
        return buildManager.isOfflineGradlePrepared()
            ? "Gradle 8.7 已解压完成，打包时会优先直接使用。"
            : "Gradle 8.7 还没准备好，点击上方按钮后会自动检测、下载或解压。";
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
        builder.append("工作根目录：").append(environmentManager.getBaseDir()).append('\n');
        builder.append("1. packages：保存下载或复制过来的原始压缩包缓存，避免重复下载。").append('\n');
        builder.append("2. sdk：Android SDK 解压后的实际工作目录，cmdline-tools 会整理到 latest。").append('\n');
        builder.append("3. jdk：固定放 JDK 21 目录。").append('\n');
        builder.append("4. ndk：固定放 NDK r27 目录。").append('\n');
        builder.append("5. gradle：固定放 Gradle 8.7 离线运行目录。").append('\n');
        builder.append("6. projects：创建的新项目目录。").append('\n');
        builder.append("7. android/data：导入进来的现有项目目录。").append('\n');
        builder.append("8. import_temp：导入压缩包时的临时解压目录。");
        return builder.toString();
    }

    private String buildPrepareButtonText() {
        boolean allReady = environmentManager.isAndroidSdkRegistered(environmentState)
            && environmentManager.isSelectedJdkInstalled(selectedJdkIndex, environmentState)
            && environmentManager.isSelectedNdkInstalled(selectedNdkIndex, environmentState)
            && buildManager.isOfflineGradlePrepared();
        return allReady ? "重新检测 4 个环境" : "自动检测并准备 4 个环境";
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
