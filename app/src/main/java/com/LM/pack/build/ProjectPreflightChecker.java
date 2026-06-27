package com.LM.pack.build;

import com.LM.pack.env.EnvironmentManager;
import com.LM.pack.model.BuildIssue;
import com.LM.pack.model.EnvironmentState;
import com.LM.pack.project.ProjectManager;
import java.io.File;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.SAXParseException;

public class ProjectPreflightChecker {

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
        int selectedNdkIndex
    ) {
        ArrayList<BuildIssue> issues = new ArrayList<BuildIssue>();
        if (!projectPrepared || projectDir == null) {
            issues.add(new BuildIssue("项目", -1, "当前没有可打包的项目。", "先从首页创建或导入工程，再进入编辑页打包。"));
            return issues;
        }
        if (!environmentManager.isSelectedJdkInstalled(selectedJdkIndex, environmentState)) {
            issues.add(new BuildIssue("JDK", -1, "当前所选 JDK 未安装。", "先进入设置安装或登记 JDK 目录。"));
        }
        if (!environmentManager.isSelectedNdkInstalled(selectedNdkIndex, environmentState)) {
            issues.add(new BuildIssue("NDK", -1, "当前所选 NDK 未安装。", "先进入设置安装或登记 NDK 目录。"));
        }
        if (!projectDir.exists()) {
            issues.add(new BuildIssue(projectDir.getAbsolutePath(), -1, "项目目录不存在。", "重新导入项目，确保目录仍然存在。"));
            return issues;
        }

        validateRequiredStructure(projectDir, issues);
        validateGradleWrapper(projectDir, issues);

        File appGradleFile = resolveAppGradleFile(projectDir);
        int compileSdk = readCompileSdk(projectDir, appGradleFile, issues);
        String sdkDir = readSdkDirForValidation(projectDir, environmentState, issues);
        validateAndroidSdkPackages(projectDir, appGradleFile, sdkDir, compileSdk, issues);

        validateXmlFile(new File(projectDir, "app/src/main/AndroidManifest.xml"), issues);
        validateXmlDirectory(new File(projectDir, "app/src/main/res"), issues);
        validateSourceDirectory(new File(projectDir, "app/src/main/java"), issues);
        validateSourceDirectory(new File(projectDir, "app/src/main/kotlin"), issues);
        return issues;
    }

    private void validateRequiredStructure(File projectDir, ArrayList<BuildIssue> issues) {
        File gradlew = new File(projectDir, "gradlew");
        File appGradle = resolveAppGradleFile(projectDir);
        File manifest = new File(projectDir, "app/src/main/AndroidManifest.xml");
        if (!gradlew.exists()) {
            issues.add(new BuildIssue(gradlew.getAbsolutePath(), -1, "缺少 gradlew。", "补齐 Gradle Wrapper 后再打包。"));
        }
        if (appGradle == null || !appGradle.exists()) {
            issues.add(new BuildIssue(new File(projectDir, "app").getAbsolutePath(), -1, "缺少 app/build.gradle 或 app/build.gradle.kts。", "检查导入目录是否选中了真正的 Android 工程根目录。"));
        }
        if (!manifest.exists()) {
            issues.add(new BuildIssue(manifest.getAbsolutePath(), -1, "缺少 AndroidManifest.xml。", "确认项目结构至少包含 `app/src/main/AndroidManifest.xml`。"));
        }
    }

    private void validateGradleWrapper(File projectDir, ArrayList<BuildIssue> issues) {
        File wrapperJar = new File(projectDir, "gradle/wrapper/gradle-wrapper.jar");
        File wrapperProperties = new File(projectDir, "gradle/wrapper/gradle-wrapper.properties");
        if (!wrapperJar.exists()) {
            issues.add(new BuildIssue(wrapperJar.getAbsolutePath(), -1, "缺少 gradle-wrapper.jar。", "补齐 `gradle/wrapper/gradle-wrapper.jar` 后再打包。"));
        }
        if (!wrapperProperties.exists()) {
            issues.add(new BuildIssue(wrapperProperties.getAbsolutePath(), -1, "缺少 gradle-wrapper.properties。", "补齐 `gradle/wrapper/gradle-wrapper.properties` 后再打包。"));
        }
    }

    private File resolveAppGradleFile(File projectDir) {
        File groovy = new File(projectDir, "app/build.gradle");
        if (groovy.exists()) {
            return groovy;
        }
        File kotlin = new File(projectDir, "app/build.gradle.kts");
        if (kotlin.exists()) {
            return kotlin;
        }
        return groovy;
    }

    private int readCompileSdk(File projectDir, File appGradleFile, ArrayList<BuildIssue> issues) {
        if (appGradleFile == null || !appGradleFile.exists()) {
            return -1;
        }
        try {
            String content = projectManager.readText(appGradleFile);
            Matcher matcher = Pattern.compile("compileSdk(?:Version)?\\s*(?:=\\s*)?(\\d+)").matcher(content);
            if (matcher.find()) {
                return parseIntSafe(matcher.group(1));
            }
            issues.add(new BuildIssue(appGradleFile.getAbsolutePath(), -1, "没有识别到 compileSdk。", "请在 `android {}` 中显式声明 `compileSdkVersion 34` 或 `compileSdk = 34`。"));
        } catch (Exception e) {
            issues.add(new BuildIssue(appGradleFile.getAbsolutePath(), -1, "读取构建脚本失败。", "检查 `app/build.gradle` 是否是可读的 UTF-8 文本。"));
        }
        return -1;
    }

    private String readSdkDirForValidation(File projectDir, EnvironmentState environmentState, ArrayList<BuildIssue> issues) {
        File localProperties = new File(projectDir, "local.properties");
        String preferredSdkDir = environmentState == null ? "" : safeText(environmentState.getAndroidSdkDir());
        try {
            if (!localProperties.exists()) {
                issues.add(
                    new BuildIssue(
                        localProperties.getAbsolutePath(),
                        -1,
                        "缺少 local.properties。",
                        environmentManager.isExistingDirectory(preferredSdkDir)
                            ? "当前预检查只做只读检查，不会自动改写项目。请手动补充 `sdk.dir=`，或在单独修复流程里生成该文件。"
                            : "先在设置页登记有效的 Android SDK 路径，再手动补充 `sdk.dir=`。"
                    )
                );
                return preferredSdkDir;
            }

            String content = projectManager.readText(localProperties);
            Matcher matcher = Pattern.compile("(?m)^sdk\\.dir\\s*=\\s*(.+)\\s*$").matcher(content);
            if (matcher.find()) {
                String sdkDir = unescapeLocalPropertiesPath(matcher.group(1).trim());
                if (!environmentManager.isExistingDirectory(sdkDir)) {
                    issues.add(new BuildIssue(localProperties.getAbsolutePath(), -1, "sdk.dir 指向的目录不存在。", "检查 Android SDK 路径是否有效，并确认平台和 build-tools 已安装。"));
                }
                return sdkDir;
            }

            if (environmentManager.isExistingDirectory(preferredSdkDir)) {
                issues.add(
                    new BuildIssue(
                        localProperties.getAbsolutePath(),
                        -1,
                        "local.properties 中没有 sdk.dir。",
                        "当前预检查不会自动修改项目。请手动补充 `sdk.dir=`，或在后续修复流程中生成该配置。"
                    )
                );
                return preferredSdkDir;
            }
            issues.add(new BuildIssue(localProperties.getAbsolutePath(), -1, "local.properties 中没有 sdk.dir。", "先在设置页登记 Android SDK 路径，或手动补充 `sdk.dir=`。"));
        } catch (Exception e) {
            issues.add(new BuildIssue(localProperties.getAbsolutePath(), -1, "读取 local.properties 失败。", "检查文件编码、目录权限和可读性。"));
        }
        return "";
    }

    private void validateAndroidSdkPackages(
        File projectDir,
        File appGradleFile,
        String sdkDir,
        int compileSdk,
        ArrayList<BuildIssue> issues
    ) {
        File localProperties = new File(projectDir, "local.properties");
        if (!environmentManager.isExistingDirectory(sdkDir)) {
            return;
        }

        File platformToolsDir = new File(sdkDir, "platform-tools");
        if (!platformToolsDir.exists() || !platformToolsDir.isDirectory()) {
            issues.add(new BuildIssue(localProperties.getAbsolutePath(), -1, "Android SDK 缺少 platform-tools。", "至少安装 `platform-tools`，否则 adb 和基础构建工具不可用。"));
        }

        if (compileSdk > 0) {
            File platformDir = new File(new File(sdkDir, "platforms"), "android-" + compileSdk);
            if (!platformDir.exists() || !platformDir.isDirectory()) {
                issues.add(new BuildIssue(localProperties.getAbsolutePath(), -1, "缺少 compileSdk 对应平台 android-" + compileSdk + "。", "在 Android SDK 中安装 `platforms;android-" + compileSdk + "` 后再打包。"));
            }
        }

        String buildToolsVersion = readBuildToolsVersion(appGradleFile);
        File buildToolsRoot = new File(sdkDir, "build-tools");
        if (buildToolsVersion.length() > 0) {
            File expectedBuildTools = new File(buildToolsRoot, buildToolsVersion);
            if (!expectedBuildTools.exists() || !expectedBuildTools.isDirectory()) {
                issues.add(new BuildIssue(localProperties.getAbsolutePath(), -1, "缺少 build-tools " + buildToolsVersion + "。", "安装构建脚本中声明的 `build-tools;" + buildToolsVersion + "`。"));
            }
            return;
        }

        File[] children = buildToolsRoot.listFiles();
        if (children == null || children.length == 0) {
            issues.add(new BuildIssue(localProperties.getAbsolutePath(), -1, "Android SDK 缺少 build-tools。", "至少安装一套 build-tools，Gradle 才能执行资源编译与打包。"));
        }
    }

    private String readBuildToolsVersion(File appGradleFile) {
        if (appGradleFile == null || !appGradleFile.exists()) {
            return "";
        }
        try {
            String content = projectManager.readText(appGradleFile);
            Matcher matcher = Pattern.compile("buildToolsVersion\\s*(?:=\\s*)?[\"']([^\"']+)[\"']").matcher(content);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        } catch (Exception e) {
        }
        return "";
    }

    private String unescapeLocalPropertiesPath(String value) {
        return value.replace("\\:", ":").replace("\\\\", "\\");
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

    private boolean isTextEditableFile(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".java")
            || name.endsWith(".kt")
            || name.endsWith(".xml")
            || name.endsWith(".gradle")
            || name.endsWith(".kts")
            || name.endsWith(".properties")
            || name.endsWith(".txt")
            || name.endsWith(".md");
    }

    private void validateTextFile(File file, ArrayList<BuildIssue> issues) {
        try {
            String content = projectManager.readText(file);
            if (content.indexOf('\u0000') >= 0) {
                issues.add(new BuildIssue(file.getAbsolutePath(), -1, "文件内容异常。", "重新保存该文件为 UTF-8 文本。"));
            }
            if (content.contains("<<<<<<<") || content.contains("=======") || content.contains(">>>>>>>")) {
                issues.add(new BuildIssue(file.getAbsolutePath(), -1, "发现未解决的合并冲突标记。", "先处理 Git 合并冲突标记，再继续构建或编辑。"));
            }
        } catch (Exception e) {
            issues.add(new BuildIssue(file.getAbsolutePath(), -1, "读取文件失败：" + e.getMessage(), "确认文件可读且不是二进制格式。"));
        }
    }

    private int parseIntSafe(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return -1;
        }
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }
}
