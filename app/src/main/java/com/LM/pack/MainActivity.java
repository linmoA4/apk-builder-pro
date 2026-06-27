package com.LM.pack;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.LM.pack.build.BuildManager;
import com.LM.pack.env.EnvironmentManager;
import com.LM.pack.env.ToolchainInstaller;
import com.LM.pack.log.LogManager;
import com.LM.pack.model.BuildResult;
import com.LM.pack.model.EnvironmentState;
import com.LM.pack.model.ProjectConfig;
import com.LM.pack.project.ProjectManager;
import java.io.File;
import java.util.ArrayList;

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

    private EditText etPackageName;
    private EditText etAppName;
    private EditText etMinSdk;
    private EditText etTargetSdk;
    private Handler handler;
    private ProgressDialog progressDialog;

    private LogManager logManager;
    private EnvironmentManager environmentManager;
    private ProjectManager projectManager;
    private BuildManager buildManager;
    private ToolchainInstaller toolchainInstaller;
    private EnvironmentState environmentState;

    private boolean projectPrepared = false;
    private boolean isBuildRunning = false;
    private String currentProjectMode = "未创建";
    private String importedProjectPath = "";
    private String currentProjectDir = "";
    private String currentProjectName = "LM打包工具空壳项目";

    private int selectedJdkIndex = 3;
    private int selectedNdkIndex = 0;

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

        etPackageName = (EditText) findViewById(R.id.etPackageName);
        etAppName = (EditText) findViewById(R.id.etAppName);
        etMinSdk = (EditText) findViewById(R.id.etMinSdk);
        etTargetSdk = (EditText) findViewById(R.id.etTargetSdk);

        TextView tvLogs = (TextView) findViewById(R.id.tvLogs);
        ScrollView logScrollView = (ScrollView) findViewById(R.id.logScrollView);
        logManager = new LogManager(tvLogs, logScrollView);

        Button btnSettings = (Button) findViewById(R.id.btnSettings);
        Button btnCreateProject = (Button) findViewById(R.id.btnCreateProject);
        Button btnImportProject = (Button) findViewById(R.id.btnImportProject);
        Button btnGenerate = (Button) findViewById(R.id.btnGenerate);
        Button btnBuild = (Button) findViewById(R.id.btnBuild);

        appendStartupLogs();

        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSettingsDialog();
            }
        });

        btnCreateProject.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                createShellProject();
            }
        });

        btnImportProject.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showImportProjectDialog();
            }
        });

        btnGenerate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                generateOrUpdateConfig();
            }
        });

        btnBuild.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                detectAndBuild();
            }
        });
    }

    private void appendStartupLogs() {
        logManager.appendLogLine("INFO", "欢迎使用 LM打包工具，当前版本已支持真实项目生成与配置写入。");
        logManager.appendKeyValue("INFO", "默认包名", etPackageName.getText().toString());
        logManager.appendKeyValue("INFO", "默认应用名", etAppName.getText().toString());
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
                        Toast.makeText(MainActivity.this, "JDK 安装完成", Toast.LENGTH_SHORT).show();
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
                        Toast.makeText(MainActivity.this, "JDK 安装失败", Toast.LENGTH_SHORT).show();
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
                        Toast.makeText(MainActivity.this, "NDK 安装完成", Toast.LENGTH_SHORT).show();
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
                        Toast.makeText(MainActivity.this, "NDK 安装失败", Toast.LENGTH_SHORT).show();
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

    private void createShellProject() {
        ProjectConfig config = buildProjectConfig(true);
        if (config == null) {
            return;
        }
        String projectDir = environmentManager.getProjectRootDir(config.getAppName());
        try {
            File rootDir = projectManager.createShellProject(this, config, projectDir);
            currentProjectDir = rootDir.getAbsolutePath();
            currentProjectMode = "空壳项目";
            currentProjectName = config.getAppName();
            importedProjectPath = "";
            projectPrepared = true;

            logManager.appendLogLine("INFO", "空壳项目已真实生成。");
            logManager.appendKeyValue("INFO", "项目名称", currentProjectName);
            logManager.appendKeyValue("INFO", "项目模式", currentProjectMode);
            logManager.appendKeyValue("INFO", "项目目录", currentProjectDir);
            logManager.appendKeyValue("INFO", "包名", config.getPackageName());
            logManager.appendKeyValue("INFO", "最低 SDK", String.valueOf(config.getMinSdk()));
            logManager.appendKeyValue("INFO", "目标 SDK", String.valueOf(config.getTargetSdk()));
            logManager.appendLogLine("INFO", "已写入 Gradle、Manifest、布局、字符串和 Wrapper 文件。");
            Toast.makeText(this, "空壳项目已创建", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            logManager.appendLogLine("ERROR", "创建空壳项目失败：" + e.getMessage());
            Toast.makeText(this, "创建项目失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void showImportProjectDialog() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(40, 20, 40, 0);

        final EditText etProjectName = new EditText(this);
        etProjectName.setHint("输入导入后的项目名称");
        etProjectName.setText(getSafeAppName());
        etProjectName.setTextColor(Color.WHITE);
        etProjectName.setHintTextColor(Color.GRAY);
        etProjectName.setBackgroundColor(Color.parseColor("#1E1E1E"));
        etProjectName.setPadding(20, 20, 20, 20);

        final EditText etImportPath = new EditText(this);
        etImportPath.setHint("输入项目路径，例如 /sdcard/MyProject");
        etImportPath.setText(importedProjectPath);
        etImportPath.setInputType(InputType.TYPE_CLASS_TEXT);
        etImportPath.setTextColor(Color.WHITE);
        etImportPath.setHintTextColor(Color.GRAY);
        etImportPath.setBackgroundColor(Color.parseColor("#1E1E1E"));
        etImportPath.setPadding(20, 20, 20, 20);

        container.addView(etProjectName);
        container.addView(etImportPath);

        new AlertDialog.Builder(this)
            .setTitle("导入项目")
            .setView(container)
            .setPositiveButton("导入", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    String projectName = etProjectName.getText().toString().trim();
                    String importPath = etImportPath.getText().toString().trim();
                    if (projectName.length() == 0) {
                        logManager.appendLogLine("ERROR", "导入项目失败：项目名称不能为空。");
                        Toast.makeText(MainActivity.this, "项目名称不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (importPath.length() == 0) {
                        logManager.appendLogLine("ERROR", "导入项目失败：项目路径不能为空。");
                        Toast.makeText(MainActivity.this, "项目路径不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    currentProjectName = projectName;
                    currentProjectMode = "导入项目";
                    currentProjectDir = importPath;
                    importedProjectPath = importPath;
                    projectPrepared = true;

                    logManager.appendLogLine("INFO", "开始导入现有项目...");
                    logManager.appendKeyValue("INFO", "项目名称", currentProjectName);
                    logManager.appendKeyValue("INFO", "项目路径", importedProjectPath);
                    logManager.appendLogLine("INFO", "项目导入完成，后续配置更新会真实写回文件。");
                    Toast.makeText(MainActivity.this, "项目导入完成", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void generateOrUpdateConfig() {
        ProjectConfig config = buildProjectConfig(false);
        if (config == null) {
            return;
        }

        String targetProjectDir = currentProjectDir;
        if (targetProjectDir.length() == 0) {
            targetProjectDir = environmentManager.getProjectRootDir(config.getAppName());
            currentProjectMode = "空壳项目";
        }

        try {
            File targetDir = new File(targetProjectDir);
            if (!targetDir.exists()) {
                projectManager.createShellProject(this, config, targetProjectDir);
                logManager.appendLogLine("INFO", "未检测到目标工程，已自动创建新项目。");
            } else {
                projectManager.updateProjectConfig(config, targetProjectDir);
                logManager.appendLogLine("INFO", "已将配置真实写回现有工程。");
            }

            currentProjectDir = targetProjectDir;
            currentProjectName = config.getAppName();
            projectPrepared = true;

            logManager.appendKeyValue("INFO", "目标包名", config.getPackageName());
            logManager.appendKeyValue("INFO", "应用名称", config.getAppName());
            logManager.appendKeyValue("INFO", "最低 SDK", String.valueOf(config.getMinSdk()));
            logManager.appendKeyValue("INFO", "目标 SDK", String.valueOf(config.getTargetSdk()));
            logManager.appendKeyValue("INFO", "项目目录", currentProjectDir);
            logManager.appendKeyValue("INFO", "JDK 环境", EnvironmentManager.JDK_NAMES[selectedJdkIndex]);
            logManager.appendKeyValue("INFO", "NDK 环境", EnvironmentManager.NDK_NAMES[selectedNdkIndex]);
            Toast.makeText(this, "配置已写入文件", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            logManager.appendLogLine("ERROR", "配置写入失败：" + e.getMessage());
            Toast.makeText(this, "配置写入失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void detectAndBuild() {
        if (isBuildRunning) {
            logManager.appendLogLine("WARN", "当前已有打包任务正在执行，请稍后再试。");
            return;
        }
        logManager.appendLogLine("INFO", "收到一键打包请求，正在启动实时错误检测。");
        showProgressDialog("实时检测", "正在扫描工程文件...");

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                ArrayList<String> errors = collectProjectErrors();
                if (errors.size() > 0) {
                    dismissProgressDialog();
                    logManager.appendLogLine("ERROR", "错误检测未通过，本次打包已终止。");
                    for (int i = 0; i < errors.size(); i++) {
                        logManager.appendLogLine("ERROR", errors.get(i));
                    }
                    Toast.makeText(MainActivity.this, "检测到错误，已停止打包", Toast.LENGTH_SHORT).show();
                    return;
                }

                updateProgressDialog("检测无错，准备启动真实构建...");
                logManager.appendLogLine("INFO", "检测无错，开始真实打包。");
                startRealBuild();
            }
        }, 900);
    }

    private ArrayList<String> collectProjectErrors() {
        ArrayList<String> errors = new ArrayList<String>();
        ProjectConfig config = buildProjectConfig(false);

        if (!projectPrepared) {
            errors.add("请先创建空壳项目、导入项目，或点击生成/更新配置。");
        }
        if (config == null) {
            errors.add("当前配置无效，请检查包名和 SDK 参数。");
        }
        if (!environmentManager.isSelectedJdkInstalled(selectedJdkIndex, environmentState)) {
            errors.add("没有安装当前所选 JDK 环境，请先安装或登记正确的 JDK 路径。");
        }
        if (!environmentManager.isSelectedNdkInstalled(selectedNdkIndex, environmentState)) {
            errors.add("没有安装当前所选 NDK 环境，请先安装或登记正确的 NDK 路径。");
        }
        if (currentProjectDir.length() == 0) {
            errors.add("当前项目目录为空，无法继续打包。");
            return errors;
        }

        File projectDir = new File(currentProjectDir);
        if (!projectDir.exists() || !projectDir.isDirectory()) {
            errors.add("项目目录不存在，无法真实打包。");
            return errors;
        }

        if (!new File(projectDir, "gradlew").exists()) {
            errors.add("导入目录缺少 gradlew，当前无法执行真实打包。");
        }
        if (!new File(projectDir, "app/build.gradle").exists()) {
            errors.add("项目缺少 app/build.gradle。");
        }
        if (!new File(projectDir, "app/src/main/AndroidManifest.xml").exists()) {
            errors.add("项目缺少 AndroidManifest.xml。");
        }
        return errors;
    }

    private void startRealBuild() {
        isBuildRunning = true;
        updateProgressDialog("正在调用 Gradle Wrapper...");
        buildManager.runGradleBuild(
            currentProjectDir,
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
                            if (result.isSuccess()) {
                                logManager.appendLogLine("INFO", result.getMessage());
                                logManager.appendKeyValue("INFO", "输出类型", "assembleDebug");
                                logManager.appendKeyValue("INFO", "项目目录", currentProjectDir);
                                Toast.makeText(MainActivity.this, "真实打包成功", Toast.LENGTH_SHORT).show();
                            } else {
                                logManager.appendLogLine("ERROR", result.getMessage());
                                logManager.appendLogLine("WARN", "请检查 JDK/NDK 路径、Gradle Wrapper 和项目依赖是否完整。");
                                Toast.makeText(MainActivity.this, "真实打包失败", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                }
            }
        );
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

    private ProjectConfig buildProjectConfig(boolean strictMessage) {
        String packageName = getSafePackageName();
        String appName = getSafeAppName();
        int[] sdkPair = parseSdkPair(strictMessage);
        if (sdkPair == null) {
            return null;
        }
        if (!isValidPackageName(packageName)) {
            if (strictMessage) {
                logManager.appendLogLine("ERROR", "包名格式不合法，示例：com.LM.pack");
                Toast.makeText(this, "包名格式不合法", Toast.LENGTH_SHORT).show();
            }
            return null;
        }
        return new ProjectConfig(packageName, appName, sdkPair[0], sdkPair[1]);
    }

    private int[] parseSdkPair(boolean strictMessage) {
        try {
            int min = Integer.parseInt(getInputText(etMinSdk));
            int target = Integer.parseInt(getInputText(etTargetSdk));
            if (target < min) {
                if (strictMessage) {
                    logManager.appendLogLine("ERROR", "目标 SDK 版本不能低于最低 SDK 版本。");
                    Toast.makeText(this, "目标 SDK 不能低于最低 SDK", Toast.LENGTH_SHORT).show();
                }
                return null;
            }
            if (min < 29 && strictMessage) {
                logManager.appendLogLine("WARN", "当前最低 SDK 低于 29，可能不符合预设兼容要求。");
            }
            return new int[] {min, target};
        } catch (Exception e) {
            if (strictMessage) {
                logManager.appendLogLine("ERROR", "SDK 参数格式错误，请输入数字。");
                Toast.makeText(this, "SDK 参数格式错误", Toast.LENGTH_SHORT).show();
            }
            return null;
        }
    }

    private boolean isValidPackageName(String packageName) {
        return packageName.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+");
    }

    private String getSafePackageName() {
        String packageName = getInputText(etPackageName);
        if (packageName.length() == 0) {
            return "com.LM.pack";
        }
        return packageName;
    }

    private String getSafeAppName() {
        String appName = getInputText(etAppName);
        if (appName.length() == 0) {
            return "LM打包工具";
        }
        return appName;
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

    private String getInputText(EditText editText) {
        return editText.getText().toString().trim();
    }

    private String safeText(String value, String fallback) {
        if (value == null || value.length() == 0) {
            return fallback;
        }
        return value;
    }
}
