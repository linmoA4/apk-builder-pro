package com.LM.pack.build;

import android.content.Context;
import android.util.Log;
import com.LM.pack.env.EnvironmentManager;
import com.LM.pack.model.BuildIssue;
import com.LM.pack.model.BuildResult;
import com.LM.pack.model.ProjectSigningConfig;
import com.LM.pack.project.ProjectManager;
import com.LM.pack.util.CommonUtils;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class BuildManager {
    private static final String TAG = "BuildManager";
    private static final long BUILD_TIMEOUT_MS = 25L * 60L * 1000L;

    public static final int EXIT_CODE_CANCELLED = -1301;
    public static final int EXIT_CODE_TIMEOUT = -1302;

    public interface BuildListener {
        void onLogLine(String line);
        void onFinished(BuildResult result);
    }

    public interface OfflineGradleListener {
        void onProgress(String message, int percent, boolean indeterminate);
        void onSuccess(File gradleExecutable);
        void onError(String message);
    }

    public interface WrapperRepairListener {
        void onProgress(String message, int percent, boolean indeterminate);
        void onSuccess();
        void onError(String message);
    }

    private interface ProgressCallback {
        void onProgress(String message, int percent, boolean indeterminate);
    }

    private final Context context;
    private final EnvironmentManager environmentManager;
    private volatile Process currentBuildProcess;
    private volatile boolean cancelRequested;

    public BuildManager(Context context, EnvironmentManager environmentManager) {
        this.context = context.getApplicationContext();
        this.environmentManager = environmentManager;
    }

    public boolean isOfflineGradlePrepared() {
        File executable = locateOfflineGradleExecutable();
        return executable != null && executable.exists() && executable.isFile();
    }

    public void prepareOfflineGradleAsync(final OfflineGradleListener listener) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File executable = prepareOfflineGradleInternal(new ProgressCallback() {
                        @Override
                        public void onProgress(String message, int percent, boolean indeterminate) {
                            if (listener != null) {
                                listener.onProgress(message, percent, indeterminate);
                            }
                        }
                    });
                    if (listener != null) {
                        listener.onSuccess(executable);
                    }
                } catch (Exception e) {
                    if (listener != null) {
                        listener.onError("Gradle 准备失败：" + safeMessage(e));
                    }
                }
            }
        }).start();
    }

    public void runGradleBuild(
        final String projectDirPath,
        final String jdkDir,
        final String androidSdkDir,
        final String ndkDir,
        final String selectedJdkName,
        final BuildListener listener
    ) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                ArrayList<String> logLines = new ArrayList<String>();
                File signingInitScript = null;
                BuildResult result;
                try {
                    cancelRequested = false;
                    File projectDir = new File(safeText(projectDirPath));
                    if (!projectDir.exists() || !projectDir.isDirectory()) {
                        ArrayList<BuildIssue> issues = new ArrayList<BuildIssue>();
                        issues.add(new BuildIssue(
                            safeText(projectDirPath),
                            -1,
                            "项目目录不存在",
                            "请先确认项目仍然存在，然后重新打开项目再打包。"
                        ));
                        finishBuild(listener, new BuildResult(false, 1, "项目目录不存在", "", issues));
                        return;
                    }

                    File gradleExecutable = locateOfflineGradleExecutable();
                    if (gradleExecutable == null || !gradleExecutable.exists()) {
                        emitLog(listener, logLines, "未检测到离线 Gradle，开始自动准备 Gradle " + EnvironmentManager.DEFAULT_GRADLE_VERSION + " ...");
                        gradleExecutable = prepareOfflineGradleInternal(null);
                        emitLog(listener, logLines, "离线 Gradle 已准备完成：" + gradleExecutable.getAbsolutePath());
                    }

                    writeLocalProperties(projectDir, androidSdkDir, ndkDir);

                    ProjectSigningConfig signingConfig = new ProjectManager().readSigningConfig(projectDir);
                    boolean useReleaseBuild = signingConfig != null && signingConfig.isComplete();
                    if (useReleaseBuild) {
                        signingInitScript = createSigningInitScript(projectDir, signingConfig);
                        emitLog(listener, logLines, "检测到项目签名配置，当前将构建已签名 release APK。");
                    } else {
                        emitLog(listener, logLines, "未检测到完整签名配置，当前将构建 debug APK。");
                    }
                    emitLog(listener, logLines, "当前 JDK： " + safeText(selectedJdkName, "未指定"));
                    emitLog(listener, logLines, "Android SDK： " + safeText(androidSdkDir, "未指定"));
                    if (safeText(ndkDir).length() > 0) {
                        emitLog(listener, logLines, "NDK： " + ndkDir);
                    }

                    ArrayList<String> command = buildGradleCommand(gradleExecutable, projectDir, useReleaseBuild, signingInitScript);
                    ProcessBuilder processBuilder = new ProcessBuilder(command);
                    processBuilder.directory(projectDir);
                    processBuilder.redirectErrorStream(true);
                    configureBuildEnvironment(processBuilder.environment(), jdkDir, androidSdkDir, ndkDir);

                    emitLog(listener, logLines, "开始执行 Gradle 任务：" + (useReleaseBuild ? "assembleRelease" : "assembleDebug"));
                    Process process = processBuilder.start();
                    currentBuildProcess = process;

                    LogPump pump = new LogPump(process.getInputStream(), logLines, listener);
                    pump.start();

                    long startedAt = System.currentTimeMillis();
                    while (true) {
                        if (cancelRequested) {
                            destroyProcess(process);
                            pump.join(1500L);
                            result = new BuildResult(
                                false,
                                EXIT_CODE_CANCELLED,
                                "构建已取消",
                                "",
                                parseBuildIssues(logLines, projectDir)
                            );
                            finishBuild(listener, result);
                            return;
                        }
                        if (System.currentTimeMillis() - startedAt > BUILD_TIMEOUT_MS) {
                            destroyProcess(process);
                            pump.join(1500L);
                            ArrayList<BuildIssue> issues = parseBuildIssues(logLines, projectDir);
                            if (issues.isEmpty()) {
                                issues.add(new BuildIssue(
                                    projectDir.getAbsolutePath(),
                                    -1,
                                    "Gradle 构建超时",
                                    "请检查依赖下载、Gradle 任务是否卡住，必要时切换下载路线后重试。"
                                ));
                            }
                            result = new BuildResult(false, EXIT_CODE_TIMEOUT, "Gradle 构建超时，已自动停止。", "", issues);
                            finishBuild(listener, result);
                            return;
                        }
                        if (process.waitFor(1000L, TimeUnit.MILLISECONDS)) {
                            break;
                        }
                    }

                    pump.join(2000L);
                    int exitCode = process.exitValue();
                    ArrayList<BuildIssue> issues = parseBuildIssues(logLines, projectDir);
                    if (exitCode == 0) {
                        File apkFile = findLatestApk(projectDir, useReleaseBuild ? "release" : "debug");
                        if (apkFile != null && apkFile.exists()) {
                            result = new BuildResult(
                                true,
                                0,
                                "Gradle 构建成功",
                                apkFile.getAbsolutePath(),
                                issues
                            );
                        } else {
                            if (issues.isEmpty()) {
                                issues.add(new BuildIssue(
                                    new File(projectDir, "app/build/outputs/apk").getAbsolutePath(),
                                    -1,
                                    "构建任务已经完成，但没有找到 APK 输出文件",
                                    "请检查项目是不是应用模块、输出目录是否被改动，或是否实际生成了 AAB 而不是 APK。"
                                ));
                            }
                            result = new BuildResult(false, 1, "构建完成，但未找到 APK 输出文件。", "", issues);
                        }
                    } else {
                        if (issues.isEmpty()) {
                            issues.add(new BuildIssue(
                                projectDir.getAbsolutePath(),
                                -1,
                                "Gradle 返回退出码 " + exitCode,
                                "请查看构建日志中的首个 ERROR / FAILURE 段落，修复后再试。"
                            ));
                        }
                        result = new BuildResult(false, exitCode, "Gradle 构建失败", "", issues);
                    }
                } catch (Exception e) {
                    ArrayList<BuildIssue> issues = new ArrayList<BuildIssue>();
                    issues.add(new BuildIssue(
                        safeText(projectDirPath),
                        -1,
                        "Gradle 构建异常：" + safeMessage(e),
                        "请检查 Android SDK、JDK、NDK 路径和网络环境，然后重新尝试。"
                    ));
                    result = new BuildResult(false, 1, "Gradle 构建异常", "", issues);
                } finally {
                    currentBuildProcess = null;
                    if (signingInitScript != null && signingInitScript.exists() && !signingInitScript.delete()) {
                        Log.w(TAG, "删除临时签名脚本失败: " + signingInitScript.getAbsolutePath());
                    }
                }
                finishBuild(listener, result);
            }
        }).start();
    }

    public void repairGradleWrapperAsync(
        final String projectDirPath,
        final boolean useOfficialSource,
        final WrapperRepairListener listener
    ) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File projectDir = new File(safeText(projectDirPath));
                    if (!projectDir.exists() || !projectDir.isDirectory()) {
                        throw new IOException("目标项目目录不存在");
                    }
                    String gradleVersion = readWrapperGradleVersion(projectDir);
                    if (gradleVersion.length() == 0) {
                        gradleVersion = environmentManager.recommendGradleVersion(projectDir);
                    }
                    if (gradleVersion.length() == 0) {
                        gradleVersion = EnvironmentManager.DEFAULT_GRADLE_VERSION;
                    }

                    File wrapperDir = new File(projectDir, "gradle/wrapper");
                    ensureDir(wrapperDir);
                    File wrapperJar = new File(wrapperDir, "gradle-wrapper.jar");
                    if (listener != null) {
                        listener.onProgress("正在准备 Gradle Wrapper Jar ...", 8, false);
                    }
                    downloadWrapperJar(wrapperJar, gradleVersion, useOfficialSource, new ProgressCallback() {
                        @Override
                        public void onProgress(String message, int percent, boolean indeterminate) {
                            if (listener != null) {
                                listener.onProgress(message, percent, indeterminate);
                            }
                        }
                    });

                    if (listener != null) {
                        listener.onProgress("正在生成 gradle-wrapper.properties ...", 82, false);
                    }
                    writeText(
                        new File(wrapperDir, "gradle-wrapper.properties"),
                        environmentManager.buildWrapperPropertiesContent(gradleVersion)
                    );

                    if (listener != null) {
                        listener.onProgress("正在补齐 gradlew 启动脚本 ...", 92, false);
                    }
                    ensureWrapperScripts(projectDir);

                    if (listener != null) {
                        listener.onProgress("Gradle Wrapper 已补齐", 100, false);
                        listener.onSuccess();
                    }
                } catch (Exception e) {
                    if (listener != null) {
                        listener.onError("补齐 Gradle Wrapper 失败：" + safeMessage(e));
                    }
                }
            }
        }).start();
    }

    public void cancelCurrentBuild() {
        cancelRequested = true;
        destroyProcess(currentBuildProcess);
    }

    private void finishBuild(BuildListener listener, BuildResult result) {
        if (listener != null) {
            listener.onFinished(result);
        }
    }

    private void emitLog(BuildListener listener, ArrayList<String> logLines, String line) {
        if (line == null) {
            return;
        }
        synchronized (logLines) {
            logLines.add(line);
        }
        if (listener != null) {
            listener.onLogLine(line);
        }
    }

    private File prepareOfflineGradleInternal(ProgressCallback callback) throws Exception {
        File executable = locateOfflineGradleExecutable();
        if (executable != null && executable.exists()) {
            executable.setExecutable(true);
            if (callback != null) {
                callback.onProgress("已检测到本地 Gradle，可直接复用。", 100, false);
            }
            return executable;
        }

        String gradleVersion = EnvironmentManager.DEFAULT_GRADLE_VERSION;
        File archiveFile = new File(environmentManager.getGradlePackageArchivePath(gradleVersion));
        ensureDir(archiveFile.getParentFile());
        if (!archiveFile.exists() || archiveFile.length() == 0L) {
            if (callback != null) {
                callback.onProgress("正在校验 Gradle 下载链路 ...", 4, false);
            }
            String url = resolveDownloadUrl(environmentManager.getGradleDownloadCandidates(gradleVersion));
            if (callback != null) {
                callback.onProgress("正在下载 Gradle " + gradleVersion + " ...", 8, false);
            }
            downloadToFile(url, archiveFile, callback, "正在下载 Gradle " + gradleVersion);
        } else if (callback != null) {
            callback.onProgress("已找到本地 Gradle 压缩包缓存。", 42, false);
        }

        File installRoot = new File(environmentManager.getGradleInstallDir());
        clearDirectory(installRoot);
        ensureDir(installRoot);
        extractZip(archiveFile, installRoot, callback, "正在解压 Gradle");

        executable = locateOfflineGradleExecutable();
        if (executable == null || !executable.exists()) {
            throw new IOException("Gradle 解压完成后未找到可执行文件");
        }
        executable.setExecutable(true);
        if (callback != null) {
            callback.onProgress("Gradle 已准备完成。", 100, false);
        }
        return executable;
    }

    private ArrayList<String> buildGradleCommand(
        File gradleExecutable,
        File projectDir,
        boolean useReleaseBuild,
        File signingInitScript
    ) {
        ArrayList<String> command = new ArrayList<String>();
        command.add(gradleExecutable.getAbsolutePath());
        command.add("-p");
        command.add(projectDir.getAbsolutePath());
        if (signingInitScript != null && signingInitScript.exists()) {
            command.add("-I");
            command.add(signingInitScript.getAbsolutePath());
        }
        command.add("--no-daemon");
        command.add("--console=plain");
        command.add(useReleaseBuild ? "assembleRelease" : "assembleDebug");
        return command;
    }

    private void configureBuildEnvironment(Map<String, String> env, String jdkDir, String androidSdkDir, String ndkDir) {
        String currentPath = env.get("PATH");
        if (safeText(jdkDir).length() > 0) {
            env.put("JAVA_HOME", jdkDir);
            env.put(
                "PATH",
                new File(jdkDir, "bin").getAbsolutePath()
                    + File.pathSeparator
                    + safeText(currentPath)
            );
        }
        if (safeText(androidSdkDir).length() > 0) {
            env.put("ANDROID_HOME", androidSdkDir);
            env.put("ANDROID_SDK_ROOT", androidSdkDir);
        }
        if (safeText(ndkDir).length() > 0) {
            env.put("ANDROID_NDK_HOME", ndkDir);
            env.put("ANDROID_NDK_ROOT", ndkDir);
            env.put("NDK_HOME", ndkDir);
            env.put("NDK_ROOT", ndkDir);
        }
        env.put("GRADLE_USER_HOME", environmentManager.getGradleUserHomeDir());
        env.put("TERM", "dumb");
    }

    private void writeLocalProperties(File projectDir, String androidSdkDir, String ndkDir) throws IOException {
        StringBuilder builder = new StringBuilder();
        if (safeText(androidSdkDir).length() > 0) {
            builder.append("sdk.dir=").append(escapeLocalPropertiesPath(androidSdkDir)).append('\n');
        }
        if (safeText(ndkDir).length() > 0) {
            builder.append("ndk.dir=").append(escapeLocalPropertiesPath(ndkDir)).append('\n');
        }
        if (builder.length() == 0) {
            return;
        }
        writeText(new File(projectDir, "local.properties"), builder.toString());
    }

    private String escapeLocalPropertiesPath(String path) {
        return safeText(path).replace("\\", "\\\\").replace(":", "\\:");
    }

    private File createSigningInitScript(File projectDir, ProjectSigningConfig config) throws IOException {
        File metaDir = new File(projectDir, ".lmproject");
        ensureDir(metaDir);
        File initScript = new File(metaDir, "signing-init.gradle");
        String script =
            "allprojects {\n" +
            "    afterEvaluate { project ->\n" +
            "        def androidExt = project.extensions.findByName('android')\n" +
            "        if (androidExt == null) {\n" +
            "            return\n" +
            "        }\n" +
            "        if (!androidExt.hasProperty('buildTypes') || !androidExt.hasProperty('signingConfigs')) {\n" +
            "            return\n" +
            "        }\n" +
            "        def releaseConfig = androidExt.signingConfigs.findByName('release') ?: androidExt.signingConfigs.create('release')\n" +
            "        releaseConfig.storeFile = new File('" + escapeGroovy(config.getStoreFilePath()) + "')\n" +
            "        releaseConfig.storePassword = '" + escapeGroovy(config.getStorePassword()) + "'\n" +
            "        releaseConfig.keyAlias = '" + escapeGroovy(config.getKeyAlias()) + "'\n" +
            "        releaseConfig.keyPassword = '" + escapeGroovy(config.getKeyPassword()) + "'\n" +
            "        def releaseType = androidExt.buildTypes.findByName('release')\n" +
            "        if (releaseType != null) {\n" +
            "            releaseType.signingConfig = releaseConfig\n" +
            "        }\n" +
            "    }\n" +
            "}\n";
        writeText(initScript, script);
        return initScript;
    }

    private String escapeGroovy(String value) {
        return safeText(value).replace("\\", "\\\\").replace("'", "\\'");
    }

    private File findLatestApk(File projectDir, String variant) {
        File baseDir = new File(projectDir, "app/build/outputs/apk");
        if (!baseDir.exists()) {
            return null;
        }
        File preferred = new File(baseDir, variant);
        File found = findNewestFileBySuffix(preferred, ".apk");
        if (found != null) {
            return found;
        }
        return findNewestFileBySuffix(baseDir, ".apk");
    }

    private File findNewestFileBySuffix(File dir, String suffix) {
        if (dir == null || !dir.exists()) {
            return null;
        }
        if (dir.isFile()) {
            return dir.getName().endsWith(suffix) ? dir : null;
        }
        File[] children = dir.listFiles();
        if (children == null || children.length == 0) {
            return null;
        }
        File newest = null;
        for (int i = 0; i < children.length; i++) {
            File candidate = findNewestFileBySuffix(children[i], suffix);
            if (candidate == null) {
                continue;
            }
            if (newest == null || candidate.lastModified() > newest.lastModified()) {
                newest = candidate;
            }
        }
        return newest;
    }

    private ArrayList<BuildIssue> parseBuildIssues(ArrayList<String> logLines, File projectDir) {
        ArrayList<BuildIssue> issues = new ArrayList<BuildIssue>();
        LinkedHashSet<String> dedupe = new LinkedHashSet<String>();
        Pattern fileLinePattern = Pattern.compile("(.+?\\.(?:java|kt|gradle|kts|xml|properties)):(\\d+)(?::\\d+)?:\\s*(.*)");
        Pattern buildFileLinePattern = Pattern.compile("Build file '(.+?)' line: (\\d+)");
        for (int i = 0; i < logLines.size(); i++) {
            String line = safeText(logLines.get(i));
            if (line.length() == 0) {
                continue;
            }
            Matcher matcher = fileLinePattern.matcher(line);
            if (matcher.find()) {
                String filePath = normalizeIssuePath(projectDir, matcher.group(1));
                int lineNumber = parseIntSafe(matcher.group(2));
                String message = cleanupIssueMessage(matcher.group(3), line);
                addIssue(issues, dedupe, filePath, lineNumber, message, buildSuggestion(message));
                continue;
            }

            matcher = buildFileLinePattern.matcher(line);
            if (matcher.find()) {
                String filePath = normalizeIssuePath(projectDir, matcher.group(1));
                int lineNumber = parseIntSafe(matcher.group(2));
                String nextLine = i + 1 < logLines.size() ? safeText(logLines.get(i + 1)) : "构建脚本报错";
                addIssue(issues, dedupe, filePath, lineNumber, nextLine, buildSuggestion(nextLine));
                continue;
            }

            String lower = line.toLowerCase();
            if (lower.contains("gradle-wrapper.jar")) {
                addIssue(
                    issues,
                    dedupe,
                    "gradle/wrapper/gradle-wrapper.jar",
                    -1,
                    "Gradle Wrapper Jar 缺失或不可用",
                    "可先使用应用内的一键补齐功能恢复 `gradle-wrapper.jar`，再重新检测。"
                );
            } else if (lower.contains("gradle-wrapper.properties")) {
                addIssue(
                    issues,
                    dedupe,
                    "gradle/wrapper/gradle-wrapper.properties",
                    -1,
                    "Gradle Wrapper 配置缺失或不可用",
                    "请先补齐 `gradle-wrapper.properties`，再重新发起构建。"
                );
            } else if (lower.contains("failed to resolve") || lower.contains("could not resolve")) {
                addIssue(
                    issues,
                    dedupe,
                    "app/build.gradle",
                    -1,
                    line,
                    "这通常是仓库源、依赖坐标或网络问题。请先确认仓库配置和下载路线。"
                );
            } else if (lower.startsWith("execution failed for task") || lower.startsWith("* what went wrong:")) {
                addIssue(
                    issues,
                    dedupe,
                    projectDir.getAbsolutePath(),
                    -1,
                    line,
                    "先查看上方更早出现的文件级错误；如果没有，再检查任务对应的插件、SDK 组件和签名配置。"
                );
            }
        }
        return issues;
    }

    private void addIssue(
        ArrayList<BuildIssue> issues,
        LinkedHashSet<String> dedupe,
        String filePath,
        int lineNumber,
        String message,
        String suggestion
    ) {
        String safeFilePath = safeText(filePath);
        String safeMessage = safeText(message, "构建失败");
        String key = safeFilePath + "|" + lineNumber + "|" + safeMessage;
        if (dedupe.contains(key)) {
            return;
        }
        dedupe.add(key);
        issues.add(new BuildIssue(
            safeFilePath.length() == 0 ? "构建日志" : safeFilePath,
            lineNumber,
            safeMessage,
            safeText(suggestion, "请检查相关文件后重试。")
        ));
    }

    private String cleanupIssueMessage(String message, String fallbackLine) {
        String clean = safeText(message);
        if (clean.startsWith("error:")) {
            clean = safeText(clean.substring("error:".length()));
        }
        if (clean.length() == 0) {
            return safeText(fallbackLine, "构建失败");
        }
        return clean;
    }

    private String buildSuggestion(String message) {
        String lower = safeText(message).toLowerCase();
        if (lower.contains("manifest")) {
            return "请检查 Manifest 合并冲突、组件声明和 `package` / `namespace` 设置。";
        }
        if (lower.contains("sdk")) {
            return "请确认 Android SDK 已安装对应平台、Build-Tools，并且 `local.properties` 指向正确目录。";
        }
        if (lower.contains("ndk")) {
            return "请确认项目要求的 NDK 版本已安装，并检查 `ndkVersion` 与当前选择是否匹配。";
        }
        if (lower.contains("sign")) {
            return "请检查 keystore 路径、别名、密码和 release 签名配置是否正确。";
        }
        if (lower.contains("java") || lower.contains("jdk")) {
            return "请切换到更合适的 JDK 版本，或修正源码 / 插件对 Java 版本的要求。";
        }
        if (lower.contains("resolve") || lower.contains("dependency")) {
            return "请检查仓库配置、网络环境和依赖版本是否存在。";
        }
        return "请先定位并修复这条错误，再重新尝试构建。";
    }

    private String normalizeIssuePath(File projectDir, String rawPath) {
        String path = safeText(rawPath).replace("'", "");
        if (path.length() == 0) {
            return path;
        }
        try {
            File file = new File(path);
            if (!file.isAbsolute()) {
                return path;
            }
            String projectPath = projectDir.getCanonicalPath();
            String filePath = file.getCanonicalPath();
            if (filePath.startsWith(projectPath + File.separator)) {
                return filePath.substring(projectPath.length() + 1);
            }
            return filePath;
        } catch (Exception e) {
            return path;
        }
    }

    private void downloadWrapperJar(
        File targetFile,
        String gradleVersion,
        boolean useOfficialSource,
        ProgressCallback callback
    ) throws Exception {
        String[] candidates;
        if (useOfficialSource) {
            candidates = new String[] {
                environmentManager.getOfficialWrapperJarUrl(gradleVersion)
            };
        } else {
            candidates = environmentManager.getRepositoryWrapperJarCandidates();
        }
        String resolvedUrl = resolveDownloadUrl(candidates);
        downloadToFile(resolvedUrl, targetFile, callback, "正在下载 Gradle Wrapper Jar");
    }

    private void ensureWrapperScripts(File projectDir) throws IOException {
        File gradlew = new File(projectDir, "gradlew");
        if (!gradlew.exists()) {
            writeText(gradlew, buildGradlewScript());
        }
        gradlew.setExecutable(true);

        File gradlewBat = new File(projectDir, "gradlew.bat");
        if (!gradlewBat.exists()) {
            writeText(gradlewBat, buildGradlewBatScript());
        }
    }

    private String buildGradlewScript() {
        return "#!/usr/bin/env sh\n"
            + "APP_HOME=$(CDPATH= cd -- \"$(dirname -- \"$0\")\" && pwd)\n"
            + "CLASSPATH=\"$APP_HOME/gradle/wrapper/gradle-wrapper.jar\"\n"
            + "exec java -classpath \"$CLASSPATH\" org.gradle.wrapper.GradleWrapperMain \"$@\"\n";
    }

    private String buildGradlewBatScript() {
        return "@ECHO OFF\r\n"
            + "SET DIRNAME=%~dp0\r\n"
            + "SET APP_HOME=%DIRNAME%\r\n"
            + "SET CLASSPATH=%APP_HOME%\\gradle\\wrapper\\gradle-wrapper.jar\r\n"
            + "java -classpath \"%CLASSPATH%\" org.gradle.wrapper.GradleWrapperMain %*\r\n";
    }

    private String readWrapperGradleVersion(File projectDir) {
        File propertiesFile = new File(projectDir, "gradle/wrapper/gradle-wrapper.properties");
        if (!propertiesFile.exists()) {
            return "";
        }
        try {
            Properties properties = new Properties();
            InputStream inputStream = new FileInputStream(propertiesFile);
            try {
                properties.load(inputStream);
            } finally {
                inputStream.close();
            }
            String distributionUrl = safeText(properties.getProperty("distributionUrl", ""));
            Matcher matcher = Pattern.compile("gradle-([0-9][0-9A-Za-z.\\-]*)-(?:bin|all)\\.zip").matcher(distributionUrl);
            return matcher.find() ? safeText(matcher.group(1)) : "";
        } catch (Exception e) {
            Log.w(TAG, "读取 wrapper 版本失败", e);
            return "";
        }
    }

    private File locateOfflineGradleExecutable() {
        File installRoot = new File(environmentManager.getGradleInstallDir());
        File direct = new File(new File(installRoot, "bin"), "gradle");
        if (direct.exists()) {
            return direct;
        }
        File versioned = new File(new File(installRoot, "gradle-" + EnvironmentManager.DEFAULT_GRADLE_VERSION), "bin/gradle");
        if (versioned.exists()) {
            return versioned;
        }
        File newest = findGradleExecutableRecursively(installRoot, 3);
        return newest != null && newest.exists() ? newest : null;
    }

    private File findGradleExecutableRecursively(File dir, int depth) {
        if (dir == null || !dir.exists() || depth < 0) {
            return null;
        }
        if (dir.isFile()) {
            return "gradle".equals(dir.getName()) ? dir : null;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return null;
        }
        for (int i = 0; i < children.length; i++) {
            File child = children[i];
            if (child.isFile() && "gradle".equals(child.getName()) && child.getParentFile() != null && "bin".equals(child.getParentFile().getName())) {
                return child;
            }
        }
        for (int i = 0; i < children.length; i++) {
            File result = findGradleExecutableRecursively(children[i], depth - 1);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private String resolveDownloadUrl(String[] candidates) throws Exception {
        LinkedHashSet<String> urls = new LinkedHashSet<String>();
        appendUrls(urls, candidates);
        if (urls.isEmpty()) {
            throw new IOException("没有可用下载地址");
        }
        java.util.Iterator<String> iterator = urls.iterator();
        while (iterator.hasNext()) {
            String candidate = iterator.next();
            if (isUrlReachable(candidate, 0)) {
                return candidate;
            }
        }
        throw new IOException("所有下载地址都不可用");
    }

    private void appendUrls(LinkedHashSet<String> urls, String[] candidates) {
        if (candidates == null) {
            return;
        }
        for (int i = 0; i < candidates.length; i++) {
            String candidate = safeText(candidates[i]);
            if (candidate.length() > 0) {
                urls.add(candidate);
            }
        }
    }

    private boolean isUrlReachable(String urlString, int redirectCount) {
        if (safeText(urlString).length() == 0 || redirectCount > 5) {
            return false;
        }
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(urlString).openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(12000);
            connection.setRequestMethod("HEAD");
            connection.setRequestProperty("User-Agent", "LM-APK-Builder/2.1");
            int code = connection.getResponseCode();
            if (code >= 300 && code < 400) {
                String redirect = connection.getHeaderField("Location");
                return safeText(redirect).length() > 0 && isUrlReachable(redirect, redirectCount + 1);
            }
            return code >= 200 && code < 400;
        } catch (Exception e) {
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void downloadToFile(String urlString, File targetFile, ProgressCallback callback, String label) throws Exception {
        downloadToFile(urlString, targetFile, callback, label, 0);
    }

    private void downloadToFile(String urlString, File targetFile, ProgressCallback callback, String label, int redirectCount) throws Exception {
        if (redirectCount > 5) {
            throw new IOException("下载重定向次数过多");
        }
        HttpURLConnection connection = null;
        InputStream inputStream = null;
        BufferedOutputStream outputStream = null;
        File partialFile = null;
        try {
            connection = (HttpURLConnection) new URL(urlString).openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(60000);
            connection.setReadTimeout(60000);
            connection.setRequestProperty("User-Agent", "LM-APK-Builder/2.1");
            connection.connect();
            int code = connection.getResponseCode();
            if (code >= 300 && code < 400) {
                String redirect = connection.getHeaderField("Location");
                if (safeText(redirect).length() > 0) {
                    connection.disconnect();
                    downloadToFile(redirect, targetFile, callback, label, redirectCount + 1);
                    return;
                }
            }
            if (code >= 400) {
                throw new IOException("下载失败，HTTP " + code);
            }
            long totalBytes = connection.getContentLengthLong();
            ensureDir(targetFile.getParentFile());
            partialFile = new File(targetFile.getAbsolutePath() + ".part");
            deleteQuietly(partialFile);
            inputStream = new BufferedInputStream(connection.getInputStream());
            outputStream = new BufferedOutputStream(new FileOutputStream(partialFile));
            copyStream(inputStream, outputStream, totalBytes, callback, label);
            outputStream.flush();
            outputStream.close();
            outputStream = null;
            if (targetFile.exists() && !targetFile.delete()) {
                throw new IOException("无法替换旧文件：" + targetFile.getAbsolutePath());
            }
            if (!partialFile.renameTo(targetFile)) {
                throw new IOException("无法写入目标文件：" + targetFile.getAbsolutePath());
            }
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
            if (outputStream != null) {
                outputStream.close();
            }
            if (connection != null) {
                connection.disconnect();
            }
            deleteQuietly(partialFile);
        }
    }

    private void copyStream(
        InputStream inputStream,
        BufferedOutputStream outputStream,
        long totalBytes,
        ProgressCallback callback,
        String label
    ) throws Exception {
        byte[] buffer = new byte[8192];
        long copied = 0L;
        int lastPercent = -1;
        int len;
        while ((len = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, len);
            copied += len;
            if (callback != null) {
                if (totalBytes > 0L) {
                    int percent = (int) Math.min(100L, (copied * 100L) / totalBytes);
                    if (percent != lastPercent) {
                        callback.onProgress(label + "  " + formatSize(copied) + " / " + formatSize(totalBytes), percent, false);
                        lastPercent = percent;
                    }
                } else {
                    callback.onProgress(label + "  已下载 " + formatSize(copied), 0, true);
                }
            }
        }
    }

    private void extractZip(File archiveFile, File targetDir, ProgressCallback callback, String label) throws Exception {
        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(archiveFile);
            long totalBytes = calculateZipTotalBytes(zipFile);
            long extractedBytes = 0L;
            int lastPercent = -1;
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                File outFile = new File(targetDir, entry.getName());
                String targetPath = targetDir.getCanonicalPath();
                String outPath = outFile.getCanonicalPath();
                if (!outPath.startsWith(targetPath + File.separator) && !outPath.equals(targetPath)) {
                    throw new IOException("压缩包包含非法路径：" + entry.getName());
                }
                if (entry.isDirectory()) {
                    ensureDir(outFile);
                    continue;
                }
                ensureDir(outFile.getParentFile());
                InputStream entryInput = null;
                BufferedOutputStream outputStream = null;
                try {
                    entryInput = new BufferedInputStream(zipFile.getInputStream(entry));
                    outputStream = new BufferedOutputStream(new FileOutputStream(outFile));
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = entryInput.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, len);
                        extractedBytes += len;
                        if (callback != null && totalBytes > 0L) {
                            int percent = (int) Math.min(100L, (extractedBytes * 100L) / totalBytes);
                            if (percent != lastPercent) {
                                callback.onProgress(
                                    label + "  " + simplifyEntryName(entry.getName()) + "  " + percent + "%",
                                    percent,
                                    false
                                );
                                lastPercent = percent;
                            }
                        }
                    }
                    outputStream.flush();
                } finally {
                    if (entryInput != null) {
                        entryInput.close();
                    }
                    if (outputStream != null) {
                        outputStream.close();
                    }
                }
            }
            if (callback != null) {
                callback.onProgress(label + " 完成", 100, false);
            }
        } finally {
            if (zipFile != null) {
                zipFile.close();
            }
        }
    }

    private long calculateZipTotalBytes(ZipFile zipFile) {
        long total = 0L;
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (!entry.isDirectory() && entry.getSize() > 0L) {
                total += entry.getSize();
            }
        }
        return total;
    }

    private String simplifyEntryName(String name) {
        String safe = safeText(name);
        if (safe.length() <= 48) {
            return safe;
        }
        return "..." + safe.substring(safe.length() - 45);
    }

    private void ensureDir(File dir) throws IOException {
        if (dir == null) {
            return;
        }
        if (dir.exists()) {
            if (!dir.isDirectory()) {
                throw new IOException("路径不是目录：" + dir.getAbsolutePath());
            }
            return;
        }
        if (!dir.mkdirs() && !dir.exists()) {
            throw new IOException("无法创建目录：" + dir.getAbsolutePath());
        }
    }

    private void clearDirectory(File file) throws IOException {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (int i = 0; i < children.length; i++) {
                    clearDirectory(children[i]);
                }
            }
        }
        if (!file.delete()) {
            throw new IOException("无法删除旧文件：" + file.getAbsolutePath());
        }
    }

    private void writeText(File file, String content) throws IOException {
        ensureDir(file.getParentFile());
        FileWriter writer = null;
        try {
            writer = new FileWriter(file, false);
            writer.write(content == null ? "" : content);
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }

    private void destroyProcess(Process process) {
        if (process == null) {
            return;
        }
        try {
            process.destroy();
            if (!process.waitFor(1500L, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
            }
        } catch (Exception e) {
            try {
                process.destroyForcibly();
            } catch (Exception ignored) {
            }
        }
    }

    private void deleteQuietly(File file) {
        if (file != null && file.exists() && !file.delete()) {
            Log.w(TAG, "删除文件失败: " + file.getAbsolutePath());
        }
    }

    private int parseIntSafe(String value) {
        return CommonUtils.parseIntSafe(value);
    }

    private String safeText(String value) {
        return CommonUtils.safeText(value);
    }

    private String safeText(String value, String fallback) {
        return CommonUtils.safeText(value, fallback);
    }

    private String safeMessage(Exception e) {
        if (e == null || e.getMessage() == null || e.getMessage().trim().length() == 0) {
            return "未知错误";
        }
        return e.getMessage().trim();
    }

    private String formatSize(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        double value = bytes / 1024.0;
        if (value < 1024.0) {
            return String.format(java.util.Locale.US, "%.1f KB", value);
        }
        value = value / 1024.0;
        if (value < 1024.0) {
            return String.format(java.util.Locale.US, "%.1f MB", value);
        }
        return String.format(java.util.Locale.US, "%.1f GB", value / 1024.0);
    }

    private static final class LogPump extends Thread {
        private final InputStream inputStream;
        private final ArrayList<String> logLines;
        private final BuildListener listener;

        LogPump(InputStream inputStream, ArrayList<String> logLines, BuildListener listener) {
            this.inputStream = inputStream;
            this.logLines = logLines;
            this.listener = listener;
        }

        @Override
        public void run() {
            BufferedReader reader = null;
            try {
                reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    synchronized (logLines) {
                        logLines.add(line);
                    }
                    if (listener != null) {
                        listener.onLogLine(line);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "读取构建输出失败", e);
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }
}
