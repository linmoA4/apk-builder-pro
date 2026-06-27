package com.LM.pack;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;
import com.LM.pack.env.EnvironmentManager;
import com.LM.pack.env.ToolchainInstaller;
import com.LM.pack.model.EnvironmentState;
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
    private EnvironmentState environmentState;
    private int selectedJdkIndex;
    private int selectedNdkIndex;

    private Button btnBackSettings;
    private Button btnInstallJdk;
    private Button btnInstallNdk;
    private Button btnSaveSdkPath;
    private Button btnSaveJdkPath;
    private Button btnSaveNdkPath;
    private Button btnCoreTools;
    private Button btnCrossTools;
    private Button btnTestTools;
    private Button btnDevopsTools;
    private Button btnMirrorTools;
    private EditText etSdkPath;
    private EditText etJdkPath;
    private EditText etNdkPath;
    private TextView tvEnvironmentSummary;
    private TextView tvSdkHint;
    private TextView tvJdkSourceHint;
    private TextView tvNdkSourceHint;
    private TextView tvDirectoryPlan;
    private TextView tvReferenceOutput;
    private LinearLayout jdkOptionContainer;
    private LinearLayout ndkOptionContainer;
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
        environmentState = environmentManager.loadState();
        selectedJdkIndex = environmentManager.loadSelectedJdkIndex();
        selectedNdkIndex = environmentManager.loadSelectedNdkIndex();

        bindViews();
        bindEvents();
        renderOptionLists();
        refreshUi();
        tvReferenceOutput.setText("点击上面的按钮，可以直接在这里查看工具资料和镜像命令。");
    }

    private void bindViews() {
        btnBackSettings = (Button) findViewById(R.id.btnBackSettings);
        btnInstallJdk = (Button) findViewById(R.id.btnInstallJdk);
        btnInstallNdk = (Button) findViewById(R.id.btnInstallNdk);
        btnSaveSdkPath = (Button) findViewById(R.id.btnSaveSdkPath);
        btnSaveJdkPath = (Button) findViewById(R.id.btnSaveJdkPath);
        btnSaveNdkPath = (Button) findViewById(R.id.btnSaveNdkPath);
        btnCoreTools = (Button) findViewById(R.id.btnCoreTools);
        btnCrossTools = (Button) findViewById(R.id.btnCrossTools);
        btnTestTools = (Button) findViewById(R.id.btnTestTools);
        btnDevopsTools = (Button) findViewById(R.id.btnDevopsTools);
        btnMirrorTools = (Button) findViewById(R.id.btnMirrorTools);
        etSdkPath = (EditText) findViewById(R.id.etSdkPath);
        etJdkPath = (EditText) findViewById(R.id.etJdkPath);
        etNdkPath = (EditText) findViewById(R.id.etNdkPath);
        tvEnvironmentSummary = (TextView) findViewById(R.id.tvEnvironmentSummary);
        tvSdkHint = (TextView) findViewById(R.id.tvSdkHint);
        tvJdkSourceHint = (TextView) findViewById(R.id.tvJdkSourceHint);
        tvNdkSourceHint = (TextView) findViewById(R.id.tvNdkSourceHint);
        tvDirectoryPlan = (TextView) findViewById(R.id.tvDirectoryPlan);
        tvReferenceOutput = (TextView) findViewById(R.id.tvReferenceOutput);
        jdkOptionContainer = (LinearLayout) findViewById(R.id.jdkOptionContainer);
        ndkOptionContainer = (LinearLayout) findViewById(R.id.ndkOptionContainer);
        progressOverlay = findViewById(R.id.progressOverlay);
        tvProgressTitle = (TextView) findViewById(R.id.tvProgressTitle);
        tvProgressMessage = (TextView) findViewById(R.id.tvProgressMessage);
        tvProgressPercent = (TextView) findViewById(R.id.tvProgressPercent);
        progressBarInstall = (ProgressBar) findViewById(R.id.progressBarInstall);
    }

    private void bindEvents() {
        btnBackSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        btnInstallJdk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                installSelectedJdk();
            }
        });

        btnInstallNdk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                installSelectedNdk();
            }
        });

        btnSaveSdkPath.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveRegisteredSdkPath();
            }
        });

        btnSaveJdkPath.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveRegisteredPath(true);
            }
        });

        btnSaveNdkPath.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveRegisteredPath(false);
            }
        });

        btnCoreTools.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tvReferenceOutput.setText(buildCatalogText("核心构建工具", CORE_TOOL_ITEMS));
            }
        });

        btnCrossTools.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tvReferenceOutput.setText(buildCatalogText("跨平台开发框架", CROSS_PLATFORM_ITEMS));
            }
        });

        btnTestTools.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tvReferenceOutput.setText(buildCatalogText("测试与仿真环境", TEST_TOOL_ITEMS));
            }
        });

        btnDevopsTools.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tvReferenceOutput.setText(buildCatalogText("版本控制与调试", DEVOPS_TOOL_ITEMS));
            }
        });

        btnMirrorTools.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tvReferenceOutput.setText(buildCatalogText("国内镜像命令", MIRROR_COMMAND_ITEMS));
            }
        });
    }

    private void renderOptionLists() {
        renderToolOptions(jdkOptionContainer, EnvironmentManager.JDK_NAMES, true);
        renderToolOptions(ndkOptionContainer, EnvironmentManager.NDK_NAMES, false);
    }

    private void renderToolOptions(LinearLayout container, String[] names, final boolean forJdk) {
        container.removeAllViews();
        for (int i = 0; i < names.length; i++) {
            final int index = i;
            RadioButton radioButton = new RadioButton(this);
            radioButton.setText(names[i] + "\n" + (forJdk ? describeJdkSource(i) : describeNdkSource(i)));
            radioButton.setTextColor(Color.parseColor("#F3F7FD"));
            radioButton.setTextSize(13f);
            radioButton.setPadding(dp(10), dp(10), dp(10), dp(10));
            radioButton.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#4E89FF")));
            radioButton.setBackground(roundedDrawable("#151B24", "#263246", 10));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) {
                params.topMargin = dp(8);
            }
            radioButton.setLayoutParams(params);
            radioButton.setChecked(forJdk ? (selectedJdkIndex == i) : (selectedNdkIndex == i));
            radioButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (forJdk) {
                        selectedJdkIndex = index;
                        environmentManager.saveSelectedJdkIndex(index);
                    } else {
                        selectedNdkIndex = index;
                        environmentManager.saveSelectedNdkIndex(index);
                    }
                    renderOptionLists();
                    refreshUi();
                }
            });
            container.addView(radioButton);
        }
    }

    private void refreshUi() {
        environmentState = environmentManager.loadState();
        etSdkPath.setText(environmentState.getAndroidSdkDir());
        etJdkPath.setText(environmentManager.getSelectedJdkDir(selectedJdkIndex, environmentState));
        etNdkPath.setText(environmentManager.getSelectedNdkDir(selectedNdkIndex, environmentState));
        tvEnvironmentSummary.setText(buildEnvironmentSummary());
        tvSdkHint.setText(buildSdkHint());
        tvJdkSourceHint.setText("当前选择：" + EnvironmentManager.JDK_NAMES[selectedJdkIndex] + "  ·  " + describeJdkSource(selectedJdkIndex));
        tvNdkSourceHint.setText("当前选择：" + EnvironmentManager.NDK_NAMES[selectedNdkIndex] + "  ·  " + describeNdkSource(selectedNdkIndex));
        tvDirectoryPlan.setText(buildDirectoryPlan());
    }

    private void saveRegisteredSdkPath() {
        String path = etSdkPath.getText().toString().trim();
        if (path.length() == 0) {
            toast("Android SDK 路径不能为空");
            return;
        }
        if (!environmentManager.isExistingDirectory(path)) {
            toast("Android SDK 目录不存在");
            return;
        }
        environmentState = environmentManager.saveAndroidSdkDir(path);
        refreshUi();
        toast("Android SDK 路径已登记");
    }

    private void saveRegisteredPath(boolean forJdk) {
        String path = (forJdk ? etJdkPath : etNdkPath).getText().toString().trim();
        if (path.length() == 0) {
            toast((forJdk ? "JDK" : "NDK") + " 路径不能为空");
            return;
        }
        if (!environmentManager.isExistingDirectory(path)) {
            toast((forJdk ? "JDK" : "NDK") + " 目录不存在");
            return;
        }
        if (forJdk) {
            environmentState = environmentManager.saveInstalledJdk(EnvironmentManager.JDK_NAMES[selectedJdkIndex], path);
        } else {
            environmentState = environmentManager.saveInstalledNdk(EnvironmentManager.NDK_NAMES[selectedNdkIndex], path);
        }
        refreshUi();
        toast((forJdk ? "JDK" : "NDK") + " 路径已登记");
    }

    private void installSelectedJdk() {
        final String jdkName = EnvironmentManager.JDK_NAMES[selectedJdkIndex];
        showProgressOverlay("工具链安装", "正在准备 " + jdkName + " ...", 0, true);
        toolchainInstaller.installJdk(selectedJdkIndex, new ToolchainInstaller.InstallListener() {
            @Override
            public void onProgress(final String message, final int percent, final boolean indeterminate) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        showProgressOverlay("安装 " + jdkName, message, percent, indeterminate);
                    }
                });
            }

            @Override
            public void onSuccess(final String installedDir) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        hideProgressOverlay();
                        environmentState = environmentManager.saveInstalledJdk(jdkName, installedDir);
                        refreshUi();
                        toast("JDK 安装完成");
                    }
                });
            }

            @Override
            public void onError(final String message) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        hideProgressOverlay();
                        toast(message);
                    }
                });
            }
        });
    }

    private void installSelectedNdk() {
        final String ndkName = EnvironmentManager.NDK_NAMES[selectedNdkIndex];
        showProgressOverlay("工具链安装", "正在准备 " + ndkName + " ...", 0, true);
        toolchainInstaller.installNdk(selectedNdkIndex, new ToolchainInstaller.InstallListener() {
            @Override
            public void onProgress(final String message, final int percent, final boolean indeterminate) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        showProgressOverlay("安装 " + ndkName, message, percent, indeterminate);
                    }
                });
            }

            @Override
            public void onSuccess(final String installedDir) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        hideProgressOverlay();
                        environmentState = environmentManager.saveInstalledNdk(ndkName, installedDir);
                        refreshUi();
                        toast("NDK 安装完成");
                    }
                });
            }

            @Override
            public void onError(final String message) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        hideProgressOverlay();
                        toast(message);
                    }
                });
            }
        });
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
        String selectedJdkName = environmentManager.getSelectedJdkName(selectedJdkIndex);
        String selectedNdkName = environmentManager.getSelectedNdkName(selectedNdkIndex);
        builder.append("Android SDK 路径：").append(safeText(environmentState.getAndroidSdkDir(), "未登记")).append('\n');
        builder.append("Android SDK 可用：").append(environmentManager.isAndroidSdkRegistered(environmentState) ? "是" : "否").append('\n');
        builder.append("当前选择 JDK：").append(selectedJdkName).append('\n');
        builder.append("当前 JDK 路径：").append(safeText(environmentManager.getSelectedJdkDir(selectedJdkIndex, environmentState), "未登记")).append('\n');
        builder.append("已登记 JDK 列表：").append(buildRegisteredToolSummary(environmentState.getInstalledJdks())).append('\n');
        builder.append("当前选择 NDK：").append(selectedNdkName).append('\n');
        builder.append("当前 NDK 路径：").append(safeText(environmentManager.getSelectedNdkDir(selectedNdkIndex, environmentState), "未登记")).append('\n');
        builder.append("已登记 NDK 列表：").append(buildRegisteredToolSummary(environmentState.getInstalledNdks())).append('\n');
        builder.append("当前选择的 JDK 是否可用：")
            .append(environmentManager.isSelectedJdkInstalled(selectedJdkIndex, environmentState) ? "是" : "否")
            .append('\n');
        builder.append("当前选择的 NDK 是否可用：")
            .append(environmentManager.isSelectedNdkInstalled(selectedNdkIndex, environmentState) ? "是" : "否");
        return builder.toString();
    }

    private String buildSdkHint() {
        if (environmentManager.isAndroidSdkRegistered(environmentState)) {
            return "已登记有效 SDK。构建前会校验 `platforms/android-<compileSdk>`、`build-tools`、`platform-tools`；预检查现在只读，不会再自动改写 `local.properties`。";
        }
        return "请先登记 Android SDK 根目录。后续会用它做只读校验，并提示你手动补充 `local.properties` 的 `sdk.dir`。";
    }

    private String buildDirectoryPlan() {
        StringBuilder builder = new StringBuilder();
        builder.append("安装策略：默认优先使用内置 assets 中的 JDK 21 和 NDK r27c，其余版本联网下载。\n");
        builder.append("Android SDK：这里只登记系统中已有的 SDK 根目录，不负责安装；构建前会复用这里的路径做只读校验，不会再自动修改项目 `local.properties`。\n");
        builder.append("工作目录：").append(environmentManager.getBaseDir()).append('\n');
        builder.append("安装包缓存目录：").append(environmentManager.getPackageCacheDir()).append('\n');
        builder.append("已登记 SDK：").append(safeText(environmentState.getAndroidSdkDir(), "未登记")).append('\n');
        builder.append("JDK 根目录：").append(new java.io.File(environmentManager.getBaseDir(), "jdk").getAbsolutePath()).append('\n');
        builder.append("NDK 根目录：").append(new java.io.File(environmentManager.getBaseDir(), "ndk").getAbsolutePath()).append('\n');
        builder.append("项目根目录：").append(environmentManager.getManagedProjectRootDir()).append('\n');
        builder.append("当前 JDK 安装目标：").append(environmentManager.getJdkInstallDir(EnvironmentManager.JDK_NAMES[selectedJdkIndex])).append('\n');
        builder.append("当前 NDK 安装目标：").append(environmentManager.getNdkInstallDir(EnvironmentManager.NDK_NAMES[selectedNdkIndex]));
        return builder.toString();
    }

    private String buildCatalogText(String title, String[] items) {
        StringBuilder builder = new StringBuilder();
        builder.append(title).append('\n').append('\n');
        for (int i = 0; i < items.length; i++) {
            builder.append(i + 1).append(". ").append(items[i]).append('\n').append('\n');
        }
        return builder.toString().trim();
    }

    private String describeJdkSource(int index) {
        if (environmentManager.isEmbeddedJdk(index)) {
            return "优先使用应用内置 assets 安装包";
        }
        return "联网下载并缓存到本地";
    }

    private String describeNdkSource(int index) {
        if (environmentManager.isEmbeddedNdk(index)) {
            return "优先使用应用内置 assets 安装包";
        }
        return "联网下载并缓存到本地";
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

    private GradientDrawable roundedDrawable(String fillColor, String strokeColor, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.parseColor(fillColor));
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(1, Color.parseColor(strokeColor));
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
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
}
