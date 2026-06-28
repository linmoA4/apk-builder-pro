package com.LM.pack.build;

import com.LM.pack.env.EnvironmentManager;
import com.LM.pack.model.BuildIssue;
import com.LM.pack.model.EnvironmentState;
import com.LM.pack.model.ProjectSigningConfig;
import com.LM.pack.project.ProjectManager;
import com.LM.pack.util.CommonUtils;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Properties;
import java.util.concurrent.CancellationException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProjectPreflightChecker {

    public interface CancellationSignal {
        boolean isCancelled();
    }

    private final ProjectManager projectManager;
    private final EnvironmentManager environmentManager;

    public ProjectPreflightChecker(ProjectManager projectManager, EnvironmentManager environmentManager) {
        this.projectManager = projectManager;
        this.environmentManager = environmentManager;
    }

    public ArrayList<BuildIssue> collectProjectIssues(
        File projectDir,
        boolean projectPrepared,
        EnvironmentState environmentState,
        int selectedJdkIndex,
        int selectedNdkIndex,
        CancellationSignal cancellationSignal
    ) {
        ArrayList<BuildIssue> issues = new ArrayList<BuildIssue>();
        checkCancelled(cancellationSignal);

        if (projectDir == null || !projectDir.exists() || !projectDir.isDirectory()) {
            issues.add(new BuildIssue(
                projectDir == null ? "" : projectDir.getAbsolutePath(),
                -1,
                "项目目录不存在",
                "请先确认项目目录仍然存在，再重新打开项目。"
            ));
            return issues;
        }

        if (!projectPrepared) {
            issues.add(new BuildIssue(
                projectDir.getAbsolutePath(),
                -1,
                "项目尚未完成打开流程",
                "请先从首页重新打开一次项目，再发起构建。"
            ));
        }

        checkCancelled(cancellationSignal);
        checkProjectStructure(projectDir, issues);

        checkCancelled(cancellationSignal);
        checkToolchain(projectDir, environmentState, selectedJdkIndex, selectedNdkIndex, issues);

        checkCancelled(cancellationSignal);
        checkSigningConfig(projectDir, issues);

        checkCancelled(cancellationSignal);
        checkWrapperConfiguration(projectDir, issues);

        return issues;
    }

    private void checkProjectStructure(File projectDir, ArrayList<BuildIssue> issues) {
        if (!projectManager.looksLikeAndroidProject(projectDir)) {
            issues.add(new BuildIssue(
                projectDir.getAbsolutePath(),
                -1,
                "当前目录没有被识别为有效的 Android 工程",
                "请确认目录中至少存在 `settings.gradle`、`app/build.gradle` 和 `AndroidManifest.xml`。"
            ));
        }

        ensureFileExists(
            issues,
            new File(projectDir, "settings.gradle"),
            new File(projectDir, "settings.gradle.kts"),
            "settings.gradle",
            "项目缺少 Gradle Settings 文件",
            "请补齐 `settings.gradle` 或 `settings.gradle.kts`，否则无法识别模块结构。"
        );

        ensureFileExists(
            issues,
            new File(projectDir, "app/build.gradle"),
            new File(projectDir, "app/build.gradle.kts"),
            "app/build.gradle",
            "项目缺少应用模块构建脚本",
            "请补齐 `app/build.gradle` 或 `app/build.gradle.kts`。"
        );

        ensureFileExists(
            issues,
            new File(projectDir, "app/src/main/AndroidManifest.xml"),
            null,
            "app/src/main/AndroidManifest.xml",
            "项目缺少 AndroidManifest.xml",
            "请先恢复 `app/src/main/AndroidManifest.xml`，再继续构建。"
        );

        File appGradle = resolveAppGradle(projectDir);
        if (appGradle != null && appGradle.exists()) {
            String compileSdk = environmentManager.recommendCompileSdk(projectDir);
            if (safeText(compileSdk).length() == 0) {
                issues.add(new BuildIssue(
                    relativePath(projectDir, appGradle),
                    -1,
                    "没有识别到 `compileSdk` 配置",
                    "请在应用模块构建脚本中声明 `compileSdk` 或 `compileSdkVersion`。"
                ));
            }
        }
    }

    private void checkToolchain(
        File projectDir,
        EnvironmentState state,
        int selectedJdkIndex,
        int selectedNdkIndex,
        ArrayList<BuildIssue> issues
    ) {
        String sdkDir = state == null ? "" : safeText(state.getAndroidSdkDir());
        if (!environmentManager.isAndroidSdkRegistered(state)) {
            issues.add(new BuildIssue(
                "Android SDK",
                -1,
                "Android SDK 尚未准备完成",
                "请先在设置页执行“自动检测环境”，补齐 Android SDK 与命令行工具。"
            ));
            return;
        }

        String selectedJdkName = environmentManager.getSelectedJdkName(selectedJdkIndex);
        if (!environmentManager.isSelectedJdkInstalled(selectedJdkIndex, state)) {
            issues.add(new BuildIssue(
                "JDK",
                -1,
                "当前选中的 " + selectedJdkName + " 还未安装",
                "请先安装该 JDK，或切换到已安装的 JDK 版本。"
            ));
        }

        String selectedNdkName = environmentManager.getSelectedNdkName(selectedNdkIndex);
        boolean projectNeedsNdk = projectRequiresNdk(projectDir);
        if (projectNeedsNdk && !environmentManager.isSelectedNdkInstalled(selectedNdkIndex, state)) {
            issues.add(new BuildIssue(
                "NDK",
                -1,
                "项目检测到原生构建需求，但当前选中的 " + selectedNdkName + " 还未安装",
                "请先安装 NDK，或切换到与项目 `ndkVersion` 更匹配的版本。"
            ));
        }

        if (!(new File(environmentManager.getSdkManagerPath()).exists())) {
            issues.add(new BuildIssue(
                "sdk/cmdline-tools",
                -1,
                "当前 Android SDK 中没有找到 `sdkmanager`",
                "请重新准备 Android SDK 命令行工具，确保 `cmdline-tools/latest/bin/sdkmanager` 存在。"
            ));
        }

        File sdkRoot = new File(sdkDir);
        if (!new File(sdkRoot, "platform-tools").exists()) {
            issues.add(new BuildIssue(
                "platform-tools",
                -1,
                "缺少 `platform-tools` 组件",
                "请在设置页安装 `platform-tools`。"
            ));
        }

        String compileSdk = environmentManager.recommendCompileSdk(projectDir);
        int compileSdkInt = CommonUtils.parseIntSafe(compileSdk);
        if (compileSdkInt > 0) {
            File platformDir = new File(sdkRoot, "platforms/android-" + compileSdkInt);
            if (!platformDir.exists()) {
                issues.add(new BuildIssue(
                    "platforms/android-" + compileSdkInt,
                    -1,
                    "缺少 Android API " + compileSdkInt + " 平台包",
                    "请先安装 `platforms;android-" + compileSdkInt + "`。"
                ));
            }
        }

        String buildToolsVersion = environmentManager.recommendBuildToolsVersion(projectDir);
        if (safeText(buildToolsVersion).length() > 0) {
            File buildToolsDir = new File(sdkRoot, "build-tools/" + buildToolsVersion);
            if (!buildToolsDir.exists()) {
                issues.add(new BuildIssue(
                    "build-tools/" + buildToolsVersion,
                    -1,
                    "缺少 Build-Tools " + buildToolsVersion,
                    "请在设置页安装 `build-tools;" + buildToolsVersion + "`。"
                ));
            }
        }
    }

    private void checkSigningConfig(File projectDir, ArrayList<BuildIssue> issues) {
        ProjectSigningConfig config = projectManager.readSigningConfig(projectDir);
        if (!config.isEnabled()) {
            return;
        }
        if (!config.isComplete()) {
            issues.add(new BuildIssue(
                ".lmproject/signing.properties",
                -1,
                "已启用 APK 签名，但签名配置还不完整",
                "请补齐 keystore 路径、别名、storePassword 和 keyPassword。"
            ));
            return;
        }
        File keystoreFile = new File(config.getStoreFilePath());
        if (!keystoreFile.exists() || !keystoreFile.isFile()) {
            issues.add(new BuildIssue(
                config.getStoreFilePath(),
                -1,
                "签名文件不存在：" + config.getStoreFilePath(),
                "请重新选择 keystore 文件，或更新当前项目的签名配置。"
            ));
        }
    }

    private void checkWrapperConfiguration(File projectDir, ArrayList<BuildIssue> issues) {
        File wrapperJar = new File(projectDir, "gradle/wrapper/gradle-wrapper.jar");
        if (!wrapperJar.exists()) {
            issues.add(new BuildIssue(
                "gradle/wrapper/gradle-wrapper.jar",
                -1,
                "缺少 `gradle-wrapper.jar`",
                "可以直接使用应用内的“一键补齐 Gradle Wrapper”恢复这个文件。"
            ));
        }

        File wrapperProperties = new File(projectDir, "gradle/wrapper/gradle-wrapper.properties");
        if (!wrapperProperties.exists()) {
            issues.add(new BuildIssue(
                "gradle/wrapper/gradle-wrapper.properties",
                -1,
                "缺少 `gradle-wrapper.properties`",
                "可使用应用内的 Wrapper 补齐流程自动生成该文件。"
            ));
        } else {
            String distributionUrl = readDistributionUrl(wrapperProperties);
            if (distributionUrl.length() == 0) {
                issues.add(new BuildIssue(
                    "gradle/wrapper/gradle-wrapper.properties",
                    -1,
                    "`gradle-wrapper.properties` 中没有有效的 `distributionUrl`",
                    "请重新生成 Wrapper 配置，或手动写入可用的 Gradle 分发地址。"
                ));
            }
        }

        File gradlew = new File(projectDir, "gradlew");
        if (!gradlew.exists()) {
            issues.add(new BuildIssue(
                "gradlew",
                -1,
                "项目缺少 `gradlew` 启动脚本",
                "建议通过 Wrapper 修复流程一起补齐 `gradlew` 与 `gradlew.bat`。"
            ));
        }
    }

    private boolean projectRequiresNdk(File projectDir) {
        File appGradle = resolveAppGradle(projectDir);
        if (appGradle != null && appGradle.exists()) {
            try {
                String content = projectManager.readText(appGradle);
                String lower = content.toLowerCase();
                return lower.contains("ndkversion")
                    || lower.contains("externalnativebuild")
                    || lower.contains("cmake")
                    || lower.contains("ndkpath");
            } catch (Exception ignored) {
            }
        }
        return new File(projectDir, "app/src/main/cpp").exists()
            || new File(projectDir, "src/main/cpp").exists();
    }

    private File resolveAppGradle(File projectDir) {
        File appGradle = new File(projectDir, "app/build.gradle");
        if (appGradle.exists()) {
            return appGradle;
        }
        appGradle = new File(projectDir, "app/build.gradle.kts");
        return appGradle.exists() ? appGradle : null;
    }

    private void ensureFileExists(
        ArrayList<BuildIssue> issues,
        File primary,
        File secondary,
        String issuePath,
        String message,
        String suggestion
    ) {
        boolean exists = primary != null && primary.exists();
        if (!exists && secondary != null) {
            exists = secondary.exists();
        }
        if (!exists) {
            issues.add(new BuildIssue(issuePath, -1, message, suggestion));
        }
    }

    private String readDistributionUrl(File propertiesFile) {
        if (propertiesFile == null || !propertiesFile.exists()) {
            return "";
        }
        FileInputStream inputStream = null;
        try {
            Properties properties = new Properties();
            inputStream = new FileInputStream(propertiesFile);
            properties.load(inputStream);
            return safeText(properties.getProperty("distributionUrl", ""));
        } catch (Exception e) {
            return "";
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private String relativePath(File projectDir, File target) {
        if (projectDir == null || target == null) {
            return "";
        }
        try {
            String root = projectDir.getCanonicalPath();
            String path = target.getCanonicalPath();
            if (path.startsWith(root + File.separator)) {
                return path.substring(root.length() + 1);
            }
            return path;
        } catch (Exception e) {
            return target.getAbsolutePath();
        }
    }

    private void checkCancelled(CancellationSignal signal) {
        if (signal != null && signal.isCancelled()) {
            throw new CancellationException("预检查已取消");
        }
    }

    private String safeText(String value) {
        return CommonUtils.safeText(value);
    }
}
