package com.LM.pack.build;

import com.LM.pack.env.EnvironmentManager;
import com.LM.pack.env.IntegrityVerifier;
import com.LM.pack.model.BuildIssue;
import com.LM.pack.model.EnvironmentState;
import com.LM.pack.project.ProjectManager;
import java.io.FileInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.CancellationException;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.SAXParseException;

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
        throwIfCancelled(cancellationSignal);
        if (!projectPrepared || projectDir == null) {
            issues.add(new BuildIssue("项目", -1, "当前没有可打包的项目。", "先从首页创建或导入工程，再进入编辑页打包。"));
            return issues;
        }
        if (!environmentManager.isSelectedJdkInstalled(selectedJdkIndex, environmentState)) {
            issues.add(new BuildIssue("JDK", -1, "当前所选 JDK 未安装。", "建议使用 " + environmentManager.getSelectedJdkName(selectedJdkIndex) + "。先进入设置页按推荐链接下载并登记，再继续打包。"));
        }
        if (!environmentManager.isSelectedNdkInstalled(selectedNdkIndex, environmentState)) {
            issues.add(new BuildIssue("NDK", -1, "当前所选 NDK 未安装。", "建议使用 " + environmentManager.getSelectedNdkName(selectedNdkIndex) + "。先进入设置页按推荐链接下载并登记，再继续打包。"));
        }
        if (!projectDir.exists()) {
            issues.add(new BuildIssue(projectDir.getAbsolutePath(), -1, "项目目录不存在。", "重新导入项目，确保目录仍然存在。"));
            return issues;
        }

        validateRequiredStructure(projectDir, issues, cancellationSignal);
        validateGradleWrapper(projectDir, issues, cancellationSignal);

        File appGradleFile = resolveAppGradleFile(projectDir);
        int compileSdk = readCompileSdk(projectDir, appGradleFile, issues, cancellationSignal);
        String sdkDir = readSdkDirForValidation(projectDir, environmentState, issues, cancellationSignal);
        validateAndroidSdkPackages(projectDir, appGradleFile, sdkDir, compileSdk, issues, cancellationSignal);

        validateXmlFile(new File(projectDir, "app/src/main/AndroidManifest.xml"), issues, cancellationSignal);
        validateXmlDirectory(new File(projectDir, "app/src/main/res"), issues, cancellationSignal);
        validateSourceDirectory(new File(projectDir, "app/src/main/java"), issues, cancellationSignal);
        validateSourceDirectory(new File(projectDir, "app/src/main/kotlin"), issues, cancellationSignal);
        return issues;
    }

    private void validateRequiredStructure(File projectDir, ArrayList<BuildIssue> issues, CancellationSignal cancellationSignal) {
        throwIfCancelled(cancellationSignal);
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

    private void validateGradleWrapper(File projectDir, ArrayList<BuildIssue> issues, CancellationSignal cancellationSignal) {
        throwIfCancelled(cancellationSignal);
        File wrapperJar = new File(projectDir, "gradle/wrapper/gradle-wrapper.jar");
        File wrapperProperties = new File(projectDir, "gradle/wrapper/gradle-wrapper.properties");
        if (!wrapperJar.exists()) {
            issues.add(new BuildIssue(wrapperJar.getAbsolutePath(), -1, "缺少 gradle-wrapper.jar。", "可在应用自定义弹窗里一键从仓库源或官方源补齐 `gradle/wrapper/gradle-wrapper.jar`。"));
        } else if (wrapperJar.length() == 0L) {
            issues.add(new BuildIssue(wrapperJar.getAbsolutePath(), -1, "gradle-wrapper.jar 为空文件。", "删除损坏文件后重新下载官方 `gradle-wrapper.jar`。"));
        } else {
            validateWrapperJarIntegrity(wrapperJar, wrapperProperties, issues);
        }
        if (!wrapperProperties.exists()) {
            issues.add(new BuildIssue(wrapperProperties.getAbsolutePath(), -1, "缺少 gradle-wrapper.properties。", "可在应用自定义弹窗里一键补齐 `gradle/wrapper/gradle-wrapper.properties`。"));
        } else {
            validateWrapperProperties(wrapperProperties, issues);
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

    private int readCompileSdk(File projectDir, File appGradleFile, ArrayList<BuildIssue> issues, CancellationSignal cancellationSignal) {
        throwIfCancelled(cancellationSignal);
        if (appGradleFile == null || !appGradleFile.exists()) {
            return -1;
        }
        try {
            String content = projectManager.readText(appGradleFile);
            Matcher matcher = Pattern.compile("compileSdk(?:Version)?\\s*(?:=\\s*)?(\\d+)").matcher(content);
            if (matcher.find()) {
                return parseIntSafe(matcher.group(1));
            }
            issues.add(new BuildIssue(appGradleFile.getAbsolutePath(), -1, "没有识别到 compileSdk。", "请在 `android {}` 中显式声明 `compileSdkVersion 36`，或至少补一个明确的 `compileSdk = 数字`。"));
        } catch (Exception e) {
            issues.add(new BuildIssue(appGradleFile.getAbsolutePath(), -1, "读取构建脚本失败。", "检查 `app/build.gradle` 是否是可读的 UTF-8 文本。"));
        }
        return -1;
    }

    private String readSdkDirForValidation(File projectDir, EnvironmentState environmentState, ArrayList<BuildIssue> issues, CancellationSignal cancellationSignal) {
        throwIfCancelled(cancellationSignal);
        File localProperties = new File(projectDir, "local.properties");
        String preferredSdkDir = environmentState == null ? "" : safeText(environmentState.getAndroidSdkDir());
        try {
            if (!localProperties.exists()) {
                if (!environmentManager.isExistingDirectory(preferredSdkDir)) {
                    issues.add(
                        new BuildIssue(
                            localProperties.getAbsolutePath(),
                            -1,
                            "缺少 local.properties。",
                            "先准备可用的 Android SDK，再决定是否手动补充 `sdk.dir=`。"
                        )
                    );
                }
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
        ArrayList<BuildIssue> issues,
        CancellationSignal cancellationSignal
    ) {
        throwIfCancelled(cancellationSignal);
        File localProperties = new File(projectDir, "local.properties");
        if (!environmentManager.isExistingDirectory(sdkDir)) {
            issues.add(new BuildIssue(localProperties.getAbsolutePath(), -1, "Android SDK 目录不可用。", "先在设置页登记有效的 Android SDK 目录，再重新执行预检查。"));
            return;
        }

        File platformToolsDir = new File(sdkDir, "platform-tools");
        File cmdlineToolsDir = new File(new File(sdkDir, "cmdline-tools"), "latest");
        if ((!platformToolsDir.exists() || !platformToolsDir.isDirectory())
            && (!cmdlineToolsDir.exists() || !cmdlineToolsDir.isDirectory())) {
            issues.add(new BuildIssue(localProperties.getAbsolutePath(), -1, "Android SDK 还不完整。", "先在设置页自动检测环境，确保 SDK 命令行工具已经准备完成。"));
            return;
        }

        if (compileSdk > 0) {
            File platformDir = new File(new File(sdkDir, "platforms"), "android-" + compileSdk);
            if (!platformDir.exists() || !platformDir.isDirectory()) {
                issues.add(new BuildIssue(appGradleFile == null ? localProperties.getAbsolutePath() : appGradleFile.getAbsolutePath(), -1, "缺少 Android 平台 android-" + compileSdk + "。", "先安装 `platforms;android-" + compileSdk + "`，再重新构建。"));
                return;
            }
        }

        String buildToolsVersion = readBuildToolsVersion(appGradleFile);
        File buildToolsRoot = new File(sdkDir, "build-tools");
        if (buildToolsVersion.length() > 0) {
            File expectedBuildTools = new File(buildToolsRoot, buildToolsVersion);
            if (!expectedBuildTools.exists() || !expectedBuildTools.isDirectory()) {
                issues.add(new BuildIssue(appGradleFile == null ? localProperties.getAbsolutePath() : appGradleFile.getAbsolutePath(), -1, "缺少 Build-Tools " + buildToolsVersion + "。", "先安装 `build-tools;" + buildToolsVersion + "`，再重新构建。"));
                return;
            }
            return;
        }

        File[] children = buildToolsRoot.listFiles();
        if (children == null || children.length == 0) {
            issues.add(new BuildIssue(localProperties.getAbsolutePath(), -1, "没有检测到任何 Build-Tools。", "先准备至少一个可用的 `build-tools` 版本，再继续构建。"));
            return;
        }
    }

    private void validateWrapperJarIntegrity(File wrapperJar, File wrapperProperties, ArrayList<BuildIssue> issues) {
        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(wrapperJar);
        } catch (Exception e) {
            issues.add(new BuildIssue(wrapperJar.getAbsolutePath(), -1, "gradle-wrapper.jar 已损坏或不是有效的 JAR。", "删除该文件后重新下载官方 `gradle-wrapper.jar`。"));
            return;
        } finally {
            if (zipFile != null) {
                try {
                    zipFile.close();
                } catch (Exception ignored) {
                }
            }
        }
        String gradleVersion = readWrapperGradleVersion(wrapperProperties);
        String expectedSha256 = environmentManager.getGradleWrapperSha256(gradleVersion);
        if (expectedSha256.length() > 0 && !IntegrityVerifier.matchesSha256(wrapperJar, expectedSha256)) {
            issues.add(new BuildIssue(wrapperJar.getAbsolutePath(), -1, "gradle-wrapper.jar 校验值不匹配。", "重新补齐官方 `gradle-wrapper.jar`，避免使用被篡改或版本不匹配的 Wrapper。"));
        }
    }

    private void validateWrapperProperties(File wrapperProperties, ArrayList<BuildIssue> issues) {
        Properties properties = new Properties();
        FileInputStream inputStream = null;
        try {
            inputStream = new FileInputStream(wrapperProperties);
            properties.load(inputStream);
            String distributionUrl = safeText(properties.getProperty("distributionUrl"));
            String declaredSha256 = safeText(properties.getProperty("distributionSha256Sum"));
            String gradleVersion = extractGradleVersionFromDistributionUrl(distributionUrl);
            String expectedSha256 = environmentManager.getGradleDistributionSha256(gradleVersion);
            if (distributionUrl.length() == 0) {
                issues.add(new BuildIssue(wrapperProperties.getAbsolutePath(), -1, "gradle-wrapper.properties 缺少 distributionUrl。", "补齐官方 Gradle 分发地址和 `distributionSha256Sum`。"));
                return;
            }
            if (expectedSha256.length() > 0 && declaredSha256.length() == 0) {
                issues.add(new BuildIssue(wrapperProperties.getAbsolutePath(), -1, "gradle-wrapper.properties 缺少 distributionSha256Sum。", "补齐官方 SHA-256 校验值，避免下载到损坏或被替换的 Gradle 包。"));
            } else if (expectedSha256.length() > 0 && !expectedSha256.equalsIgnoreCase(declaredSha256)) {
                issues.add(new BuildIssue(wrapperProperties.getAbsolutePath(), -1, "distributionSha256Sum 与目标 Gradle 版本不匹配。", "把 `distributionSha256Sum` 改成对应版本的官方 SHA-256。"));
            }
        } catch (Exception e) {
            issues.add(new BuildIssue(wrapperProperties.getAbsolutePath(), -1, "读取 gradle-wrapper.properties 失败。", "检查 Wrapper 配置文件是否可读且格式正确。"));
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private String readWrapperGradleVersion(File wrapperProperties) {
        if (wrapperProperties == null || !wrapperProperties.exists()) {
            return EnvironmentManager.DEFAULT_GRADLE_VERSION;
        }
        Properties properties = new Properties();
        FileInputStream inputStream = null;
        try {
            inputStream = new FileInputStream(wrapperProperties);
            properties.load(inputStream);
            return extractGradleVersionFromDistributionUrl(properties.getProperty("distributionUrl"));
        } catch (Exception e) {
            return EnvironmentManager.DEFAULT_GRADLE_VERSION;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private String extractGradleVersionFromDistributionUrl(String distributionUrl) {
        Matcher matcher = Pattern.compile("gradle-([0-9][^/\\\\-]*)-").matcher(safeText(distributionUrl));
        if (matcher.find()) {
            return matcher.group(1);
        }
        return EnvironmentManager.DEFAULT_GRADLE_VERSION;
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

    private void validateXmlDirectory(File dir, ArrayList<BuildIssue> issues, CancellationSignal cancellationSignal) {
        throwIfCancelled(cancellationSignal);
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (int i = 0; i < files.length; i++) {
            throwIfCancelled(cancellationSignal);
            File file = files[i];
            if (file.isDirectory()) {
                validateXmlDirectory(file, issues, cancellationSignal);
            } else if (file.getName().toLowerCase().endsWith(".xml")) {
                validateXmlFile(file, issues, cancellationSignal);
            }
        }
    }

    private void validateXmlFile(File file, ArrayList<BuildIssue> issues, CancellationSignal cancellationSignal) {
        throwIfCancelled(cancellationSignal);
        if (!file.exists()) {
            return;
        }
        try {
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file);
        } catch (SAXParseException e) {
            issues.add(new BuildIssue(file.getAbsolutePath(), normalizeLineNumber(e.getLineNumber()), e.getMessage(), "检查 XML 标签是否闭合、属性引号是否完整。"));
        } catch (Exception e) {
            issues.add(new BuildIssue(file.getAbsolutePath(), -1, e.getMessage(), "检查 XML 结构和资源引用。"));
        }
    }

    private void validateSourceDirectory(File dir, ArrayList<BuildIssue> issues, CancellationSignal cancellationSignal) {
        throwIfCancelled(cancellationSignal);
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (int i = 0; i < files.length; i++) {
            throwIfCancelled(cancellationSignal);
            File file = files[i];
            if (file.isDirectory()) {
                validateSourceDirectory(file, issues, cancellationSignal);
            } else if (isTextEditableFile(file)) {
                validateTextFile(file, issues, cancellationSignal);
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

    private void validateTextFile(File file, ArrayList<BuildIssue> issues, CancellationSignal cancellationSignal) {
        throwIfCancelled(cancellationSignal);
        try {
            String content = projectManager.readText(file);
            if (content.indexOf('\u0000') >= 0) {
                issues.add(new BuildIssue(file.getAbsolutePath(), -1, "文件内容异常。", "重新保存该文件为 UTF-8 文本。"));
            }
            if (content.contains("<<<<<<<") || content.contains("=======") || content.contains(">>>>>>>")) {
                issues.add(new BuildIssue(file.getAbsolutePath(), -1, "发现未解决的合并冲突标记。", "先处理 Git 合并冲突标记，再继续构建或编辑。"));
            }
            String name = file.getName().toLowerCase();
            if (name.endsWith(".java")) {
                validateJavaSyntax(file, content, issues, cancellationSignal);
            } else if (name.endsWith(".kt")) {
                validateKotlinSyntax(file, content, issues, cancellationSignal);
            }
        } catch (CancellationException cancelled) {
            throw cancelled;
        } catch (Exception e) {
            issues.add(new BuildIssue(file.getAbsolutePath(), -1, "读取文件失败：" + e.getMessage(), "确认文件可读且不是二进制格式。"));
        }
    }

    private void validateJavaSyntax(File file, String content, ArrayList<BuildIssue> issues, CancellationSignal cancellationSignal) {
        int braceDepth = 0;
        int parenDepth = 0;
        int bracketDepth = 0;
        boolean inSingleLineComment = false;
        boolean inMultiLineComment = false;
        boolean inString = false;
        boolean inChar = false;
        char prev = 0;
        char prevPrev = 0;
        String[] lines = content.split("\n", -1);
        for (int lineIdx = 0; lineIdx < lines.length; lineIdx++) {
            throwIfCancelled(cancellationSignal);
            String line = lines[lineIdx];
            int lineNum = lineIdx + 1;
            inSingleLineComment = false;
            for (int col = 0; col < line.length(); col++) {
                throwIfCancelled(cancellationSignal);
                char ch = line.charAt(col);
                if (inMultiLineComment) {
                    if (ch == '/' && prev == '*') {
                        inMultiLineComment = false;
                    }
                } else if (inSingleLineComment) {
                    continue;
                } else if (inString) {
                    if (ch == '"' && prev != '\\') {
                        inString = false;
                    }
                } else if (inChar) {
                    if (ch == '\'' && prev != '\\') {
                        inChar = false;
                    }
                } else if (ch == '/' && prev == '/') {
                    inSingleLineComment = true;
                } else if (ch == '*' && prev == '/') {
                    inMultiLineComment = true;
                } else if (ch == '"') {
                    inString = true;
                } else if (ch == '\'') {
                    inChar = true;
                } else if (ch == '{') {
                    braceDepth++;
                } else if (ch == '}') {
                    braceDepth--;
                    if (braceDepth < 0) {
                        issues.add(new BuildIssue(file.getAbsolutePath(), lineNum, "多余的右大括号 '}'", "删除或修正多余的大括号。"));
                        braceDepth = 0;
                    }
                } else if (ch == '(') {
                    parenDepth++;
                } else if (ch == ')') {
                    parenDepth--;
                    if (parenDepth < 0) {
                        issues.add(new BuildIssue(file.getAbsolutePath(), lineNum, "多余的右圆括号 ')'", "删除或修正多余的圆括号。"));
                        parenDepth = 0;
                    }
                } else if (ch == '[') {
                    bracketDepth++;
                } else if (ch == ']') {
                    bracketDepth--;
                    if (bracketDepth < 0) {
                        issues.add(new BuildIssue(file.getAbsolutePath(), lineNum, "多余的右方括号 ']'", "删除或修正多余的方括号。"));
                        bracketDepth = 0;
                    }
                }
                prevPrev = prev;
                prev = ch;
            }
            if (!inMultiLineComment && !inSingleLineComment) {
                String trimmed = line.trim();
                if (trimmed.length() > 0
                    && !trimmed.equals("{")
                    && !trimmed.equals("}")
                    && !trimmed.startsWith("@")
                    && !trimmed.startsWith("//")
                    && !trimmed.startsWith("/*")
                    && !trimmed.equals("*/")
                    && !trimmed.endsWith("{")
                    && !trimmed.endsWith("}")
                    && !trimmed.endsWith(";")
                    && !trimmed.endsWith(",")
                    && !trimmed.endsWith(":")
                    && !trimmed.startsWith("package ")
                    && !trimmed.startsWith("import ")
                    && !trimmed.startsWith("if (")
                    && !trimmed.startsWith("for (")
                    && !trimmed.startsWith("while (")
                    && !trimmed.startsWith("try")
                    && !trimmed.startsWith("catch")
                    && !trimmed.startsWith("else")
                    && !trimmed.startsWith("do")
                    && !trimmed.matches(".*\\s*\\*.*")) {
                    issues.add(new BuildIssue(file.getAbsolutePath(), lineNum, "可能缺少分号 ';'", "在语句末尾添加分号。"));
                }
            }
        }
        if (braceDepth != 0) {
            issues.add(new BuildIssue(file.getAbsolutePath(), -1, "大括号不匹配，差 " + braceDepth + " 个（" + (braceDepth > 0 ? "多" : "少") + "）", "检查 {} 是否成对出现。"));
        }
        if (parenDepth != 0) {
            issues.add(new BuildIssue(file.getAbsolutePath(), -1, "圆括号不匹配，差 " + parenDepth + " 个（" + (parenDepth > 0 ? "多" : "少") + "）", "检查 () 是否成对出现。"));
        }
        if (bracketDepth != 0) {
            issues.add(new BuildIssue(file.getAbsolutePath(), -1, "方括号不匹配，差 " + bracketDepth + " 个（" + (bracketDepth > 0 ? "多" : "少") + "）", "检查 [] 是否成对出现。"));
        }
    }

    private void validateKotlinSyntax(File file, String content, ArrayList<BuildIssue> issues, CancellationSignal cancellationSignal) {
        int braceDepth = 0;
        int parenDepth = 0;
        int bracketDepth = 0;
        boolean inSingleLineComment = false;
        boolean inMultiLineComment = false;
        boolean inString = false;
        boolean inRawString = false;
        int rawStringHash = 0;
        boolean inChar = false;
        char prev = 0;
        String[] lines = content.split("\n", -1);
        for (int lineIdx = 0; lineIdx < lines.length; lineIdx++) {
            throwIfCancelled(cancellationSignal);
            String line = lines[lineIdx];
            int lineNum = lineIdx + 1;
            inSingleLineComment = false;
            for (int col = 0; col < line.length(); col++) {
                throwIfCancelled(cancellationSignal);
                char ch = line.charAt(col);
                if (inMultiLineComment) {
                    if (ch == '/' && prev == '*') {
                        inMultiLineComment = false;
                    }
                } else if (inSingleLineComment) {
                    continue;
                } else if (inRawString) {
                    if (ch == '"' && col + rawStringHash < line.length()) {
                        boolean match = true;
                        for (int i = 1; i <= rawStringHash; i++) {
                            if (line.charAt(col + i) != '"') {
                                match = false;
                                break;
                            }
                        }
                        if (match) {
                            inRawString = false;
                            rawStringHash = 0;
                        }
                    }
                } else if (inString) {
                    if (ch == '"' && prev != '\\') {
                        inString = false;
                    }
                } else if (inChar) {
                    if (ch == '\'' && prev != '\\') {
                        inChar = false;
                    }
                } else if (ch == '/' && prev == '/') {
                    inSingleLineComment = true;
                } else if (ch == '*' && prev == '/') {
                    inMultiLineComment = true;
                } else if (ch == '"') {
                    int hashCount = 0;
                    while (col + hashCount + 1 < line.length() && line.charAt(col + hashCount + 1) == '"') {
                        hashCount++;
                    }
                    if (hashCount >= 2) {
                        inRawString = true;
                        rawStringHash = hashCount;
                    } else {
                        inString = true;
                    }
                } else if (ch == '\'') {
                    inChar = true;
                } else if (ch == '{') {
                    braceDepth++;
                } else if (ch == '}') {
                    braceDepth--;
                    if (braceDepth < 0) {
                        issues.add(new BuildIssue(file.getAbsolutePath(), lineNum, "多余的右大括号 '}'", "删除或修正多余的大括号。"));
                        braceDepth = 0;
                    }
                } else if (ch == '(') {
                    parenDepth++;
                } else if (ch == ')') {
                    parenDepth--;
                    if (parenDepth < 0) {
                        issues.add(new BuildIssue(file.getAbsolutePath(), lineNum, "多余的右圆括号 ')'", "删除或修正多余的圆括号。"));
                        parenDepth = 0;
                    }
                } else if (ch == '[') {
                    bracketDepth++;
                } else if (ch == ']') {
                    bracketDepth--;
                    if (bracketDepth < 0) {
                        issues.add(new BuildIssue(file.getAbsolutePath(), lineNum, "多余的右方括号 ']'", "删除或修正多余的方括号。"));
                        bracketDepth = 0;
                    }
                }
                prev = ch;
            }
        }
        if (braceDepth != 0) {
            issues.add(new BuildIssue(file.getAbsolutePath(), -1, "大括号不匹配，差 " + braceDepth + " 个（" + (braceDepth > 0 ? "多" : "少") + "）", "检查 {} 是否成对出现。"));
        }
        if (parenDepth != 0) {
            issues.add(new BuildIssue(file.getAbsolutePath(), -1, "圆括号不匹配，差 " + parenDepth + " 个（" + (parenDepth > 0 ? "多" : "少") + "）", "检查 () 是否成对出现。"));
        }
        if (bracketDepth != 0) {
            issues.add(new BuildIssue(file.getAbsolutePath(), -1, "方括号不匹配，差 " + bracketDepth + " 个（" + (bracketDepth > 0 ? "多" : "少") + "）", "检查 [] 是否成对出现。"));
        }
    }

    private int parseIntSafe(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return -1;
        }
    }

    private void throwIfCancelled(CancellationSignal cancellationSignal) {
        if ((cancellationSignal != null && cancellationSignal.isCancelled()) || Thread.currentThread().isInterrupted()) {
            throw new CancellationException("预检查已取消");
        }
    }

    private int normalizeLineNumber(int lineNumber) {
        return lineNumber > 0 ? lineNumber : 1;
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }
}
