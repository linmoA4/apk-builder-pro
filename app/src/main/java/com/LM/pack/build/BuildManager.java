package com.LM.pack.build;

import android.content.Context;
import com.LM.pack.env.EnvironmentManager;
import com.LM.pack.env.IntegrityVerifier;
import com.LM.pack.model.BuildIssue;
import com.LM.pack.model.BuildResult;
import com.LM.pack.model.ProjectSigningConfig;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BuildManager {
    public static final int EXIT_CODE_CANCELLED = -2;
    public static final int EXIT_CODE_TIMEOUT = -3;
    private static final String META_DIR = ".lmproject";
    private static final String SIGNING_FILE = "signing.properties";
    private static final String SIGNING_BLOCK_BEGIN = "// APK_BUILDER_PRO_SIGNING_BEGIN";
    private static final String SIGNING_BLOCK_END = "// APK_BUILDER_PRO_SIGNING_END";
    private static final long DEFAULT_BUILD_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(10);

    private static final class ManagedSigningScriptState {
        final File targetFile;
        final String originalContent;

        ManagedSigningScriptState(File targetFile, String originalContent) {
            this.targetFile = targetFile;
            this.originalContent = originalContent;
        }
    }

    private static final class ScriptBlockSpan {
        final int start;
        final int openBrace;
        final int endExclusive;

        ScriptBlockSpan(int start, int openBrace, int endExclusive) {
            this.start = start;
            this.openBrace = openBrace;
            this.endExclusive = endExclusive;
        }
    }

    private static final class BuildTimeoutState {
        final long timeoutMillis;
        volatile boolean triggered;

        BuildTimeoutState(long timeoutMillis) {
            this.timeoutMillis = timeoutMillis;
        }

        boolean markTriggered() {
            if (triggered) {
                return false;
            }
            triggered = true;
            return true;
        }
    }

    public interface OfflineGradleListener {
        void onProgress(String message, int percent, boolean indeterminate);
        void onSuccess(File gradleExecutable);
        void onError(String message);
    }

    private final Context context;
    private final EnvironmentManager environmentManager;
    private final Object processLock = new Object();
    private volatile Process activeProcess;
    private volatile boolean cancelRequested = false;

    public BuildManager(Context context, EnvironmentManager environmentManager) {
        this.context = context.getApplicationContext();
        this.environmentManager = environmentManager;
    }

    public interface BuildListener {
        void onLogLine(String line);
        void onFinished(BuildResult result);
    }

    public interface WrapperRepairListener {
        void onProgress(String message, int percent, boolean indeterminate);
        void onSuccess();
        void onError(String message);
    }

    private static class ProjectRequirements {
        final int compileSdk;
        final String buildToolsVersion;
        final String ndkVersion;

        ProjectRequirements(int compileSdk, String buildToolsVersion, String ndkVersion) {
            this.compileSdk = compileSdk;
            this.buildToolsVersion = buildToolsVersion == null ? "" : buildToolsVersion.trim();
            this.ndkVersion = ndkVersion == null ? "" : ndkVersion.trim();
        }
    }

    public void runGradleBuild(
        final String projectDir,
        final String jdkDir,
        final String sdkDir,
        final String ndkDir,
        final String selectedJdkName,
        final BuildListener listener
    ) {
        cancelRequested = false;
        new Thread(new Runnable() {
            @Override
            public void run() {
                executeGradleBuild(projectDir, jdkDir, sdkDir, ndkDir, selectedJdkName, listener);
            }
        }).start();
    }

    public void cancelCurrentBuild() {
        cancelRequested = true;
        Process process;
        synchronized (processLock) {
            process = activeProcess;
        }
        destroyProcessQuietly(process);
    }

    public void prepareOfflineGradleAsync(final OfflineGradleListener listener) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (listener != null) {
                        listener.onProgress("正在检查 Gradle 运行环境...", 0, true);
                    }
                    File gradleExecutable = prepareOfflineGradle(new ProgressCallback() {
                        @Override
                        public void onProgress(String message, int percent, boolean indeterminate) {
                            if (listener != null) {
                                listener.onProgress(message, percent, indeterminate);
                            }
                        }
                    }, new BuildListener() {
                        @Override
                        public void onLogLine(String line) {
                        }

                        @Override
                        public void onFinished(BuildResult result) {
                        }
                    });
                    if (gradleExecutable != null && gradleExecutable.exists()) {
                        if (listener != null) {
                            listener.onSuccess(gradleExecutable);
                        }
                    } else if (listener != null) {
                        listener.onError("Gradle 运行环境准备失败");
                    }
                } catch (Exception e) {
                    if (listener != null) {
                        listener.onError("Gradle 运行环境准备失败：" + e.getMessage());
                    }
                }
            }
        }).start();
    }

    public boolean isOfflineGradlePrepared() {
        File gradleExecutable = findGradleExecutable(new File(environmentManager.getGradleInstallDir()));
        return gradleExecutable != null && gradleExecutable.exists() && gradleExecutable.isFile();
    }

    public void repairGradleWrapperAsync(final String projectDir, final boolean useOfficialSource, final WrapperRepairListener listener) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File projectRoot = new File(projectDir);
                    String gradleVersion = environmentManager.recommendGradleVersion(projectRoot);
                    if (listener != null) {
                        listener.onProgress("正在检查 Gradle Wrapper 缺失项...", 0, true);
                    }
                    File wrapperDir = new File(projectRoot, "gradle/wrapper");
                    ensureDir(wrapperDir);
                    File wrapperJar = new File(wrapperDir, "gradle-wrapper.jar");
                    File wrapperProperties = new File(wrapperDir, "gradle-wrapper.properties");
                    if (shouldRefreshWrapperJar(wrapperJar, gradleVersion)) {
                        String url = useOfficialSource
                            ? environmentManager.getOfficialWrapperJarUrl(gradleVersion)
                            : environmentManager.getRepositoryWrapperJarUrl();
                        if (listener != null) {
                            listener.onProgress("正在下载 gradle-wrapper.jar ...", 18, false);
                        }
                        downloadToFile(url, wrapperJar, null, "正在下载 gradle-wrapper.jar");
                        verifyWrapperJar(wrapperJar, gradleVersion);
                    }
                    if (shouldRewriteWrapperProperties(wrapperProperties, gradleVersion)) {
                        if (listener != null) {
                            listener.onProgress("正在写入 gradle-wrapper.properties ...", 72, false);
                        }
                        writeText(wrapperProperties, environmentManager.buildWrapperPropertiesContent(gradleVersion));
                    }
                    File gradlew = new File(projectRoot, "gradlew");
                    if (gradlew.exists()) {
                        gradlew.setExecutable(true);
                    }
                    if (listener != null) {
                        listener.onProgress("Gradle Wrapper 已补齐", 100, false);
                        listener.onSuccess();
                    }
                } catch (Exception e) {
                    if (listener != null) {
                        listener.onError("Gradle Wrapper 补齐失败：" + e.getMessage());
                    }
                }
            }
        }).start();
    }

    private void executeGradleBuild(
        String projectDir,
        String jdkDir,
        String sdkDir,
        String ndkDir,
        String selectedJdkName,
        BuildListener listener
    ) {
        Process process = null;
        BufferedReader reader = null;
        Thread timeoutWatcher = null;
        ArrayList<BuildIssue> issues = new ArrayList<BuildIssue>();
        String apkPath = "";
        ManagedSigningScriptState managedSigningScriptState = null;
        BuildTimeoutState timeoutState = new BuildTimeoutState(DEFAULT_BUILD_TIMEOUT_MS);
        try {
            File projectRoot = new File(projectDir);
            File gradlew = new File(projectRoot, "gradlew");
            ProjectSigningConfig signingConfig = readSigningConfig(projectRoot);
            boolean releaseSigningEnabled = signingConfig.isEnabled();
            if (releaseSigningEnabled) {
                String signingValidationMessage = validateSigningConfig(signingConfig);
                if (signingValidationMessage.length() > 0) {
                    issues.add(
                        new BuildIssue(
                            "签名配置",
                            -1,
                            signingValidationMessage,
                            "请重新选择 keystore，并确认别名、store password、key password 都已填写。"
                        )
                    );
                    listener.onFinished(new BuildResult(false, -1, "签名配置无效，已停止构建", "", issues));
                    return;
                }
                managedSigningScriptState = applyManagedSigningBlock(projectRoot, signingConfig, listener);
            }
            String gradleTask = releaseSigningEnabled ? "assembleRelease" : "assembleDebug";
            ProjectRequirements requirements = inspectProjectRequirements(projectRoot);
            logResolvedEnvironment(projectRoot, requirements, selectedJdkName, jdkDir, sdkDir, ndkDir, listener);
            syncProjectLocalProperties(projectRoot, sdkDir, ndkDir, listener);
            if (cancelRequested) {
                listener.onFinished(createCancelledResult(apkPath, issues));
                return;
            }
            prepareRequiredSdkPackages(projectRoot, jdkDir, sdkDir, listener, requirements);
            if (cancelRequested) {
                listener.onFinished(createCancelledResult(apkPath, issues));
                return;
            }
            File offlineGradleExecutable = prepareOfflineGradle(null, listener);
            if (cancelRequested) {
                listener.onFinished(createCancelledResult(apkPath, issues));
                return;
            }
            ProcessBuilder processBuilder;
            if (offlineGradleExecutable != null) {
                listener.onLogLine("开始执行真实构建命令。");
                listener.onLogLine("构建目录: " + projectDir);
                listener.onLogLine("JDK 环境: " + selectedJdkName);
                if (sdkDir != null && sdkDir.length() > 0) {
                    listener.onLogLine("Android SDK: " + sdkDir);
                }
                listener.onLogLine("Gradle 模式: 外置下载 Gradle " + environmentManager.recommendGradleVersion(projectRoot));
                listener.onLogLine("构建输出: " + (releaseSigningEnabled ? "已启用签名的 release APK" : "debug APK"));
                listener.onLogLine("Gradle 任务: " + gradleTask + " --stacktrace");
                processBuilder = new ProcessBuilder(
                    offlineGradleExecutable.getAbsolutePath(),
                    "-p",
                    projectDir,
                    gradleTask,
                    "--stacktrace"
                );
            } else {
                if (!gradlew.exists()) {
                    listener.onFinished(new BuildResult(false, -1, "未找到 gradlew，且当前也没有可用的离线 Gradle。", "", issues));
                    return;
                }
                gradlew.setExecutable(true);
                listener.onLogLine("开始执行真实构建命令。");
                listener.onLogLine("构建目录: " + projectDir);
                listener.onLogLine("JDK 环境: " + selectedJdkName);
                if (sdkDir != null && sdkDir.length() > 0) {
                    listener.onLogLine("Android SDK: " + sdkDir);
                }
                listener.onLogLine("Gradle 模式: 项目 Gradle Wrapper");
                listener.onLogLine("构建输出: " + (releaseSigningEnabled ? "已启用签名的 release APK" : "debug APK"));
                listener.onLogLine("Gradle 任务: " + gradleTask + " --stacktrace");
                processBuilder = new ProcessBuilder("./gradlew", gradleTask, "--stacktrace");
            }
            processBuilder.directory(projectRoot);
            processBuilder.redirectErrorStream(true);
            if (jdkDir != null && jdkDir.length() > 0) {
                processBuilder.environment().put("JAVA_HOME", jdkDir);
            }
            if (sdkDir != null && sdkDir.length() > 0) {
                processBuilder.environment().put("ANDROID_HOME", sdkDir);
                processBuilder.environment().put("ANDROID_SDK_ROOT", sdkDir);
            }
            if (ndkDir != null && ndkDir.length() > 0) {
                processBuilder.environment().put("ANDROID_NDK_HOME", ndkDir);
                processBuilder.environment().put("ANDROID_NDK_ROOT", ndkDir);
            }
            processBuilder.environment().put("GRADLE_USER_HOME", environmentManager.getGradleUserHomeDir());
            String currentPath = processBuilder.environment().get("PATH");
            if (currentPath == null) {
                currentPath = "";
            }
            if (jdkDir != null && jdkDir.length() > 0) {
                processBuilder.environment().put("PATH", jdkDir + "/bin:" + currentPath);
            }

            process = processBuilder.start();
            setActiveProcess(process);
            timeoutWatcher = startBuildTimeoutWatcher(process, listener, timeoutState);
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (cancelRequested || timeoutState.triggered) {
                    break;
                }
                listener.onLogLine(line);
                collectIssue(line, issues);
                String detectedApkPath = detectApkPath(line);
                if (detectedApkPath.length() > 0) {
                    apkPath = detectedApkPath;
                }
            }

            int exitCode = process.waitFor();
            if (timeoutState.triggered) {
                listener.onFinished(createTimedOutResult(apkPath, issues, timeoutState.timeoutMillis));
                return;
            }
            if (cancelRequested) {
                listener.onFinished(createCancelledResult(apkPath, issues));
                return;
            }
            if (exitCode == 0) {
                if (apkPath.length() == 0) {
                    apkPath = guessApkPath(projectRoot, releaseSigningEnabled);
                }
                listener.onFinished(new BuildResult(true, 0, releaseSigningEnabled ? "签名 APK 构建完成" : "真实打包完成", apkPath, issues));
            } else {
                listener.onFinished(new BuildResult(false, exitCode, "真实打包失败，Gradle 退出码：" + exitCode, apkPath, issues));
            }
        } catch (Exception e) {
            if (timeoutState.triggered) {
                listener.onFinished(createTimedOutResult(apkPath, issues, timeoutState.timeoutMillis));
            } else if (cancelRequested) {
                listener.onFinished(createCancelledResult(apkPath, issues));
            } else {
                listener.onFinished(new BuildResult(false, -1, "真实打包异常：" + e.getMessage(), apkPath, issues));
            }
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (Exception e) {
            }
            if (timeoutWatcher != null) {
                timeoutWatcher.interrupt();
            }
            restoreManagedSigningBlock(managedSigningScriptState, listener);
            clearActiveProcess(process);
            destroyProcessQuietly(process);
            cancelRequested = false;
        }
    }

    private interface ProgressCallback {
        void onProgress(String message, int percent, boolean indeterminate);
    }

    private ProjectRequirements inspectProjectRequirements(File projectRoot) {
        File appGradle = new File(projectRoot, "app/build.gradle");
        if (!appGradle.exists()) {
            appGradle = new File(projectRoot, "app/build.gradle.kts");
        }
        if (!appGradle.exists()) {
            return new ProjectRequirements(-1, "", "");
        }
        try {
            String content = readText(appGradle);
            int compileSdk = extractInt(content, "compileSdk(?:Version)?\\s*(?:=\\s*)?(\\d+)");
            String buildToolsVersion = extractString(content, "buildToolsVersion\\s*(?:=\\s*)?[\"']([^\"']+)[\"']");
            String ndkVersion = extractString(content, "ndkVersion\\s*(?:=\\s*)?[\"']([^\"']+)[\"']");
            return new ProjectRequirements(compileSdk, buildToolsVersion, ndkVersion);
        } catch (Exception e) {
            return new ProjectRequirements(-1, "", "");
        }
    }

    private void logResolvedEnvironment(
        File projectRoot,
        ProjectRequirements requirements,
        String selectedJdkName,
        String jdkDir,
        String sdkDir,
        String ndkDir,
        BuildListener listener
    ) {
        if (listener == null) {
            return;
        }
        listener.onLogLine("正在分析项目所需环境并进行智能分配。");
        listener.onLogLine(environmentManager.buildToolchainRecommendationSummary(projectRoot));
        listener.onLogLine("JDK 环境: " + selectedJdkName + "  ->  " + jdkDir);
        listener.onLogLine("NDK 环境: " + environmentManager.getRecommendedNdkName(projectRoot) + "  ->  " + ndkDir);
        listener.onLogLine("Gradle 环境: 外置 Gradle " + environmentManager.recommendGradleVersion(projectRoot));
        if (requirements.compileSdk > 0) {
            listener.onLogLine("项目 compileSdk: android-" + requirements.compileSdk);
        }
        if (requirements.buildToolsVersion.length() > 0) {
            listener.onLogLine("项目 buildToolsVersion: " + requirements.buildToolsVersion);
        }
        if (requirements.ndkVersion.length() > 0) {
            listener.onLogLine("项目 ndkVersion: " + requirements.ndkVersion);
        }
        if (sdkDir != null && sdkDir.length() > 0) {
            listener.onLogLine("Android SDK: " + sdkDir);
        }
    }

    private void prepareRequiredSdkPackages(
        File projectRoot,
        String jdkDir,
        String sdkDir,
        BuildListener listener,
        ProjectRequirements requirements
    ) throws Exception {
        if (sdkDir == null || sdkDir.trim().length() == 0) {
            if (listener != null) {
                listener.onLogLine("Android SDK 尚未登记，跳过自动补齐 SDK 组件。");
            }
            return;
        }
        File sdkRoot = new File(sdkDir);
        if (!sdkRoot.exists() || !sdkRoot.isDirectory()) {
            if (listener != null) {
                listener.onLogLine("Android SDK 目录不存在，跳过自动补齐 SDK 组件。");
            }
            return;
        }
        File sdkManager = new File(environmentManager.getSdkManagerPath());
        if (!sdkManager.exists()) {
            if (listener != null) {
                listener.onLogLine("未找到 sdkmanager，当前只使用已存在的 SDK 组件。");
            }
            return;
        }
        ArrayList<String> missingPackages = collectMissingSdkPackages(sdkRoot, requirements);
        if (missingPackages.isEmpty()) {
            if (listener != null) {
                listener.onLogLine("Android SDK 组件已齐全，无需额外安装。");
            }
            return;
        }
        if (!environmentManager.isSdkLicenseAccepted()) {
            throw new IllegalStateException("检测到缺失的 Android SDK 组件，但你还没有显式确认 Android SDK 许可证。请先到设置页的“安装 Android API”中确认许可后再重试构建。");
        }
        ensureAcceptedSdkLicenses(sdkRoot);
        sdkManager.setExecutable(true);
        if (listener != null) {
            listener.onLogLine("检测到缺失的 SDK 组件，正在自动补齐：");
            for (int i = 0; i < missingPackages.size(); i++) {
                listener.onLogLine("  - " + missingPackages.get(i));
            }
        }
        ArrayList<String> command = new ArrayList<String>();
        command.add(sdkManager.getAbsolutePath());
        command.add("--sdk_root=" + sdkRoot.getAbsolutePath());
        for (int i = 0; i < missingPackages.size(); i++) {
            command.add(missingPackages.get(i));
        }
        runLoggedProcess(command, sdkRoot, jdkDir, sdkRoot.getAbsolutePath(), "", listener, "SDK 自动补齐");
    }

    private ArrayList<String> collectMissingSdkPackages(File sdkRoot, ProjectRequirements requirements) {
        ArrayList<String> missingPackages = new ArrayList<String>();
        if (!isSdkPackageInstalled(sdkRoot, "platform-tools")) {
            missingPackages.add("platform-tools");
        }
        if (requirements.compileSdk > 0) {
            String platformPackage = "platforms;android-" + requirements.compileSdk;
            if (!isSdkPackageInstalled(sdkRoot, platformPackage)) {
                missingPackages.add(platformPackage);
            }
        }
        String desiredBuildTools = requirements.buildToolsVersion;
        if (desiredBuildTools.length() == 0 && !hasAnyBuildToolsInstalled(sdkRoot)) {
            desiredBuildTools = guessBuildToolsVersion(requirements.compileSdk);
        }
        if (desiredBuildTools.length() > 0) {
            String buildToolsPackage = "build-tools;" + desiredBuildTools;
            if (!isSdkPackageInstalled(sdkRoot, buildToolsPackage)) {
                missingPackages.add(buildToolsPackage);
            }
        }
        return missingPackages;
    }

    private boolean isSdkPackageInstalled(File sdkRoot, String packageName) {
        if (sdkRoot == null || packageName == null || packageName.length() == 0) {
            return false;
        }
        if ("platform-tools".equals(packageName)) {
            return new File(sdkRoot, "platform-tools").isDirectory();
        }
        if (packageName.startsWith("platforms;android-")) {
            return new File(new File(sdkRoot, "platforms"), packageName.substring("platforms;".length())).isDirectory();
        }
        if (packageName.startsWith("build-tools;")) {
            return new File(new File(sdkRoot, "build-tools"), packageName.substring("build-tools;".length())).isDirectory();
        }
        return false;
    }

    private boolean hasAnyBuildToolsInstalled(File sdkRoot) {
        File buildToolsRoot = new File(sdkRoot, "build-tools");
        File[] children = buildToolsRoot.listFiles();
        return children != null && children.length > 0;
    }

    private String guessBuildToolsVersion(int compileSdk) {
        if (compileSdk <= 0) {
            return "";
        }
        if (compileSdk >= 36) {
            return "36.0.0";
        }
        if (compileSdk == 35) {
            return "35.0.1";
        }
        if (compileSdk == 34) {
            return "34.0.0";
        }
        return compileSdk + ".0.0";
    }

    private void ensureAcceptedSdkLicenses(File sdkRoot) throws Exception {
        File licensesDir = new File(sdkRoot, "licenses");
        ensureDir(licensesDir);
        writeText(
            new File(licensesDir, "android-sdk-license"),
            "24333f8a63b6825ea9c5514f83c2829b004d1fee\n"
                + "d56f5187479451eabf01fb78af6dfcb131a6481e\n"
        );
        writeText(new File(licensesDir, "android-sdk-preview-license"), "84831b9409646a918e30573bab4c9c91346d8abd\n");
    }

    private void runLoggedProcess(
        ArrayList<String> command,
        File workingDir,
        String jdkDir,
        String sdkDir,
        String ndkDir,
        BuildListener listener,
        String title
    ) throws Exception {
        Process process = null;
        BufferedReader reader = null;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(workingDir);
            processBuilder.redirectErrorStream(true);
            if (jdkDir != null && jdkDir.length() > 0) {
                processBuilder.environment().put("JAVA_HOME", jdkDir);
                String currentPath = processBuilder.environment().get("PATH");
                processBuilder.environment().put("PATH", jdkDir + "/bin:" + (currentPath == null ? "" : currentPath));
            }
            if (sdkDir != null && sdkDir.length() > 0) {
                processBuilder.environment().put("ANDROID_HOME", sdkDir);
                processBuilder.environment().put("ANDROID_SDK_ROOT", sdkDir);
            }
            if (ndkDir != null && ndkDir.length() > 0) {
                processBuilder.environment().put("ANDROID_NDK_HOME", ndkDir);
                processBuilder.environment().put("ANDROID_NDK_ROOT", ndkDir);
            }
            if (listener != null) {
                listener.onLogLine(title + " 开始执行。");
            }
            process = processBuilder.start();
            setActiveProcess(process);
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (cancelRequested) {
                    break;
                }
                if (listener != null) {
                    listener.onLogLine(line);
                }
            }
            int exitCode = process.waitFor();
            if (cancelRequested) {
                throw new InterruptedException(title + " 已取消");
            }
            if (exitCode != 0) {
                throw new IllegalStateException(title + " 失败，退出码：" + exitCode);
            }
            if (listener != null) {
                listener.onLogLine(title + " 完成。");
            }
        } finally {
            if (reader != null) {
                reader.close();
            }
            clearActiveProcess(process);
            destroyProcessQuietly(process);
        }
    }

    private void destroyProcessQuietly(Process process) {
        if (process == null) {
            return;
        }
        process.destroy();
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        } catch (Exception ignored) {
            try {
                process.destroyForcibly();
            } catch (Exception innerIgnored) {
            }
        }
    }

    private void setActiveProcess(Process process) {
        synchronized (processLock) {
            activeProcess = process;
        }
    }

    private void clearActiveProcess(Process process) {
        synchronized (processLock) {
            if (activeProcess == process) {
                activeProcess = null;
            }
        }
    }

    private BuildResult createCancelledResult(String apkPath, ArrayList<BuildIssue> issues) {
        return new BuildResult(false, EXIT_CODE_CANCELLED, "构建已取消", apkPath, issues);
    }

    private BuildResult createTimedOutResult(String apkPath, ArrayList<BuildIssue> issues, long timeoutMillis) {
        int timeoutMinutes = (int) Math.max(1L, TimeUnit.MILLISECONDS.toMinutes(timeoutMillis));
        issues.add(
            new BuildIssue(
                "Gradle 构建",
                -1,
                "构建超过 " + timeoutMinutes + " 分钟仍未完成，已自动停止。",
                "如果是首次拉依赖可稍后重试；若反复发生，请检查 Gradle 任务、网络下载或死循环。"
            )
        );
        return new BuildResult(false, EXIT_CODE_TIMEOUT, "构建超时，已在 " + timeoutMinutes + " 分钟后自动取消", apkPath, issues);
    }

    private Thread startBuildTimeoutWatcher(final Process process, final BuildListener listener, final BuildTimeoutState timeoutState) {
        Thread watcher = new Thread(new Runnable() {
            @Override
            public void run() {
                long deadlineAt = System.currentTimeMillis() + timeoutState.timeoutMillis;
                while (!cancelRequested && !timeoutState.triggered) {
                    if (process == null || !process.isAlive()) {
                        return;
                    }
                    long remaining = deadlineAt - System.currentTimeMillis();
                    if (remaining <= 0L) {
                        if (process.isAlive() && timeoutState.markTriggered()) {
                            if (listener != null) {
                                listener.onLogLine("构建超过 10 分钟仍未结束，已自动终止 Gradle 进程。");
                            }
                            destroyProcessQuietly(process);
                        }
                        return;
                    }
                    try {
                        Thread.sleep(Math.min(remaining, 1000L));
                    } catch (InterruptedException ignored) {
                        return;
                    }
                }
            }
        }, "build-timeout-watcher");
        watcher.setDaemon(true);
        watcher.start();
        return watcher;
    }

    private File prepareOfflineGradle(ProgressCallback progressCallback, BuildListener listener) {
        try {
            ensureDir(new File(environmentManager.getPackageCacheDir()));
            String gradleVersion = EnvironmentManager.DEFAULT_GRADLE_VERSION;
            File archiveFile = new File(environmentManager.getGradlePackageArchivePath(gradleVersion));
            if (!archiveFile.exists() || archiveFile.length() == 0L) {
                String downloadUrl = resolveAvailableGradleUrl(gradleVersion);
                if (downloadUrl == null) {
                    return null;
                }
                listener.onLogLine("正在从外置下载链路获取 Gradle " + gradleVersion + " 安装包。");
                downloadToFile(
                    downloadUrl,
                    archiveFile,
                    remapProgress(progressCallback, 0, 58),
                    "正在下载 Gradle " + gradleVersion
                );
            }
            if (!verifyGradleArchive(archiveFile, gradleVersion)) {
                listener.onLogLine("检测到 Gradle 安装包校验不通过，准备重新下载。");
                if (archiveFile.exists()) {
                    archiveFile.delete();
                }
                String downloadUrl = resolveAvailableGradleUrl(gradleVersion);
                if (downloadUrl == null) {
                    throw new IllegalStateException("没有可用的 Gradle 下载地址");
                }
                downloadToFile(
                    downloadUrl,
                    archiveFile,
                    remapProgress(progressCallback, 0, 58),
                    "正在重新下载 Gradle " + gradleVersion
                );
                if (!verifyGradleArchive(archiveFile, gradleVersion)) {
                    throw new IllegalStateException("Gradle 安装包 SHA-256 校验失败");
                }
            }
            File installRoot = new File(environmentManager.getGradleInstallDir());
            File gradleBin = findGradleExecutable(installRoot);
            if (gradleBin != null) {
                if (progressCallback != null) {
                    progressCallback.onProgress("Gradle " + gradleVersion + " 已就绪", 100, false);
                }
                return gradleBin;
            }
            listener.onLogLine("正在准备离线 Gradle 目录。");
            clearDirectory(installRoot);
            ensureDir(installRoot);
            if (progressCallback != null) {
                progressCallback.onProgress("正在整理 Gradle 安装目录...", 60, false);
            }
            extractZip(
                archiveFile,
                installRoot,
                remapProgress(progressCallback, 58, 42),
                "正在解压 Gradle " + gradleVersion
            );
            gradleBin = findGradleExecutable(installRoot);
            if (gradleBin == null) {
                throw new IllegalStateException("离线 Gradle 解压后未找到 bin/gradle");
            }
            gradleBin.setExecutable(true);
            if (progressCallback != null) {
                progressCallback.onProgress("Gradle " + gradleVersion + " 已准备完成", 100, false);
            }
            return gradleBin;
        } catch (Exception e) {
            if (listener != null) {
                listener.onLogLine("离线 Gradle 准备失败，回退到项目 Wrapper：" + e.getMessage());
            }
            return null;
        }
    }

    private String resolveAvailableGradleUrl(String gradleVersion) {
        String[] candidates = environmentManager.getGradleDownloadCandidates(gradleVersion);
        for (int i = 0; i < candidates.length; i++) {
            if (isUrlReachable(candidates[i], 0)) {
                return candidates[i];
            }
        }
        return null;
    }

    private void downloadToFile(String urlString, File targetFile, ProgressCallback progressCallback, String label) throws Exception {
        java.net.HttpURLConnection connection = null;
        InputStream inputStream = null;
        BufferedOutputStream outputStream = null;
        File partialFile = null;
        try {
            ensureDir(targetFile == null ? null : targetFile.getParentFile());
            partialFile = buildPartialDownloadFile(targetFile);
            deleteQuietly(partialFile);
            connection = (java.net.HttpURLConnection) new java.net.URL(urlString).openConnection();
            connection.setConnectTimeout(60000);
            connection.setReadTimeout(60000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "LM-APK-Builder/2.1");
            connection.connect();
            if (connection.getResponseCode() >= 400) {
                throw new IllegalStateException("Gradle 下载失败，HTTP " + connection.getResponseCode());
            }
            long totalBytes = connection.getContentLengthLong();
            inputStream = new BufferedInputStream(connection.getInputStream());
            outputStream = new BufferedOutputStream(new FileOutputStream(partialFile));
            copyStreamWithProgress(inputStream, outputStream, totalBytes, progressCallback, label);
            outputStream.flush();
            outputStream.close();
            outputStream = null;
            replaceFileAtomically(partialFile, targetFile);
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

    private boolean isUrlReachable(String urlString, int redirectCount) {
        if (urlString == null || redirectCount > 5) {
            return false;
        }
        java.net.HttpURLConnection connection = null;
        try {
            connection = (java.net.HttpURLConnection) new java.net.URL(urlString).openConnection();
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(12000);
            connection.setRequestMethod("HEAD");
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "LM-APK-Builder/2.1");
            int responseCode = connection.getResponseCode();
            if (responseCode >= 300 && responseCode < 400) {
                String redirect = connection.getHeaderField("Location");
                return redirect != null && isUrlReachable(redirect, redirectCount + 1);
            }
            return responseCode >= 200 && responseCode < 400;
        } catch (Exception e) {
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private boolean shouldRefreshWrapperJar(File wrapperJar, String gradleVersion) {
        if (wrapperJar == null || !wrapperJar.exists() || wrapperJar.length() == 0L) {
            return true;
        }
        String expectedSha256 = environmentManager.getGradleWrapperSha256(gradleVersion);
        if (expectedSha256.length() == 0) {
            return false;
        }
        return !IntegrityVerifier.matchesSha256(wrapperJar, expectedSha256);
    }

    private void verifyWrapperJar(File wrapperJar, String gradleVersion) throws Exception {
        String expectedSha256 = environmentManager.getGradleWrapperSha256(gradleVersion);
        if (expectedSha256.length() == 0) {
            return;
        }
        if (!IntegrityVerifier.matchesSha256(wrapperJar, expectedSha256)) {
            throw new IllegalStateException("gradle-wrapper.jar SHA-256 校验失败");
        }
    }

    private boolean shouldRewriteWrapperProperties(File wrapperProperties, String gradleVersion) {
        if (wrapperProperties == null || !wrapperProperties.exists() || wrapperProperties.length() == 0L) {
            return true;
        }
        Properties properties = new Properties();
        FileInputStream inputStream = null;
        try {
            inputStream = new FileInputStream(wrapperProperties);
            properties.load(inputStream);
            String currentUrl = safeText(properties.getProperty("distributionUrl"));
            String currentSha256 = safeText(properties.getProperty("distributionSha256Sum"));
            String expectedUrl = "https://services.gradle.org/distributions/gradle-" + safeGradleVersion(gradleVersion) + "-bin.zip";
            String expectedSha256 = safeText(environmentManager.getGradleDistributionSha256(gradleVersion));
            if (!expectedUrl.equals(currentUrl)) {
                return true;
            }
            return expectedSha256.length() > 0 && !expectedSha256.equalsIgnoreCase(currentSha256);
        } catch (Exception e) {
            return true;
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (Exception ignored) {
            }
        }
    }

    private boolean verifyGradleArchive(File archiveFile, String gradleVersion) {
        String expectedSha256 = environmentManager.getGradleDistributionSha256(gradleVersion);
        if (expectedSha256.length() == 0) {
            return archiveFile != null && archiveFile.exists() && archiveFile.length() > 0L;
        }
        return IntegrityVerifier.matchesSha256(archiveFile, expectedSha256);
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private File buildPartialDownloadFile(File targetFile) {
        if (targetFile == null) {
            return null;
        }
        return new File(targetFile.getAbsolutePath() + ".part");
    }

    private void replaceFileAtomically(File sourceFile, File targetFile) throws Exception {
        if (sourceFile == null || targetFile == null || !sourceFile.exists()) {
            throw new IllegalStateException("下载文件不存在，无法写入目标文件");
        }
        if (targetFile.exists() && !targetFile.delete()) {
            throw new IllegalStateException("无法替换旧文件：" + targetFile.getAbsolutePath());
        }
        if (!sourceFile.renameTo(targetFile)) {
            throw new IllegalStateException("无法完成下载文件写入：" + targetFile.getAbsolutePath());
        }
    }

    private void deleteQuietly(File file) {
        if (file != null && file.exists()) {
            file.delete();
        }
    }

    private String safeGradleVersion(String value) {
        String version = safeText(value);
        return version.length() == 0 ? EnvironmentManager.DEFAULT_GRADLE_VERSION : version;
    }

    private void extractZip(File archiveFile, File targetDir, ProgressCallback progressCallback, String label) throws Exception {
        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(archiveFile);
            long totalBytes = calculateZipTotalBytes(zipFile);
            long[] extractedBytes = {0L};
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                File outputFile = new File(targetDir, entry.getName());
                String targetPath = targetDir.getCanonicalPath();
                String outputPath = outputFile.getCanonicalPath();
                if (!outputPath.startsWith(targetPath + File.separator) && !outputPath.equals(targetPath)) {
                    throw new IllegalStateException("检测到非法路径：" + entry.getName());
                }
                if (entry.isDirectory()) {
                    ensureDir(outputFile);
                    continue;
                }
                ensureDir(outputFile.getParentFile());
                InputStream entryInputStream = null;
                BufferedOutputStream outputStream = null;
                try {
                    entryInputStream = new BufferedInputStream(zipFile.getInputStream(entry));
                    outputStream = new BufferedOutputStream(new FileOutputStream(outputFile));
                    copyArchiveEntryStream(
                        entryInputStream,
                        outputStream,
                        totalBytes,
                        extractedBytes,
                        progressCallback,
                        label + "  " + simplifyEntryName(entry.getName())
                    );
                } finally {
                    if (entryInputStream != null) {
                        entryInputStream.close();
                    }
                    if (outputStream != null) {
                        outputStream.close();
                    }
                }
            }
        } finally {
            if (zipFile != null) {
                zipFile.close();
            }
        }
    }

    private File findGradleExecutable(File dir) {
        if (dir == null || !dir.exists()) {
            return null;
        }
        File direct = new File(dir, "bin/gradle");
        if (direct.exists() && direct.isFile()) {
            direct.setExecutable(true);
            return direct;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return null;
        }
        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            if (!file.isDirectory()) {
                continue;
            }
            File found = findGradleExecutable(file);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private void clearDirectory(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (int i = 0; i < files.length; i++) {
                    clearDirectory(files[i]);
                }
            }
        }
        file.delete();
    }

    private void ensureDir(File dir) {
        if (dir == null || dir.exists()) {
            return;
        }
        dir.mkdirs();
    }

    private void syncProjectLocalProperties(File projectRoot, String sdkDir, String ndkDir, BuildListener listener) {
        if (projectRoot == null || !projectRoot.exists()) {
            return;
        }
        try {
            File localProperties = new File(projectRoot, "local.properties");
            Properties properties = new Properties();
            if (localProperties.exists()) {
                FileInputStream inputStream = null;
                try {
                    inputStream = new FileInputStream(localProperties);
                    properties.load(inputStream);
                } finally {
                    if (inputStream != null) {
                        inputStream.close();
                    }
                }
            }
            boolean changed = false;
            if (sdkDir != null && sdkDir.length() > 0) {
                String escapedSdk = escapeLocalPropertiesPath(sdkDir);
                if (!escapedSdk.equals(properties.getProperty("sdk.dir"))) {
                    properties.setProperty("sdk.dir", escapedSdk);
                    changed = true;
                }
            }
            if (ndkDir != null && ndkDir.length() > 0) {
                String escapedNdk = escapeLocalPropertiesPath(ndkDir);
                if (!escapedNdk.equals(properties.getProperty("ndk.dir"))) {
                    properties.setProperty("ndk.dir", escapedNdk);
                    changed = true;
                }
            }
            if (!changed) {
                return;
            }
            FileOutputStream outputStream = null;
            try {
                outputStream = new FileOutputStream(localProperties);
                properties.store(outputStream, "Generated by APK Builder Pro");
            } finally {
                if (outputStream != null) {
                    outputStream.close();
                }
            }
            if (listener != null) {
                listener.onLogLine("已自动同步项目 local.properties 中的 SDK / NDK 路径。");
            }
        } catch (Exception e) {
            if (listener != null) {
                listener.onLogLine("自动同步 local.properties 失败：" + e.getMessage());
            }
        }
    }

    private String escapeLocalPropertiesPath(String path) {
        return path.replace("\\", "\\\\").replace(":", "\\:");
    }

    private void collectIssue(String line, ArrayList<BuildIssue> issues) {
        if (line == null) {
            return;
        }
        String trimmed = line.trim();
        Matcher kotlinMatcher = Pattern.compile("^e:\\s+(.+?):\\s*\\((\\d+),\\s*(\\d+)\\):\\s*(.+)$").matcher(trimmed);
        if (kotlinMatcher.find()) {
            addIssueIfAbsent(
                issues,
                new BuildIssue(
                    kotlinMatcher.group(1).trim(),
                    parseIntSafe(kotlinMatcher.group(2)),
                    kotlinMatcher.group(4).trim(),
                    suggestFix(kotlinMatcher.group(4), "")
                )
            );
            return;
        }
        Matcher lineColumnMatcher = Pattern.compile("^(.+?):(\\d+):(\\d+):\\s*error:\\s*(.+)$").matcher(trimmed);
        if (lineColumnMatcher.find()) {
            addIssueIfAbsent(
                issues,
                new BuildIssue(
                    lineColumnMatcher.group(1).trim(),
                    parseIntSafe(lineColumnMatcher.group(2)),
                    lineColumnMatcher.group(4).trim(),
                    suggestFix(lineColumnMatcher.group(4), "")
                )
            );
            return;
        }
        Matcher lineErrorMatcher = Pattern.compile("^(.+?):(\\d+):\\s*error:\\s*(.+)$").matcher(trimmed);
        if (lineErrorMatcher.find()) {
            String filePath = lineErrorMatcher.group(1).trim();
            int lineNumber = parseIntSafe(lineErrorMatcher.group(2));
            String message = lineErrorMatcher.group(3).trim();
            addIssueIfAbsent(issues, new BuildIssue(filePath, lineNumber, message, suggestFix(message, "")));
            return;
        }

        Matcher javaStyleMatcher = Pattern.compile("^(.*\\.(?:java|kt|xml|gradle|kts|properties)):\\s*(.+)$").matcher(trimmed);
        if (javaStyleMatcher.find() && line.toLowerCase().indexOf("error") >= 0) {
            addIssueIfAbsent(issues, new BuildIssue(javaStyleMatcher.group(1).trim(), -1, javaStyleMatcher.group(2).trim(), suggestFix(line, "")));
            return;
        }
        if (trimmed.startsWith("* What went wrong:") || trimmed.startsWith("Execution failed for task")
            || trimmed.startsWith("A problem occurred") || trimmed.startsWith("FAILURE: Build failed")) {
            addIssueIfAbsent(issues, new BuildIssue("Gradle", -1, trimmed, suggestFix(trimmed, "")));
            return;
        }
        if (trimmed.startsWith("Caused by:") || trimmed.startsWith("> ")) {
            addIssueIfAbsent(issues, new BuildIssue("Gradle", -1, trimmed, suggestFix(trimmed, "")));
        }
    }

    private String detectApkPath(String line) {
        Matcher matcher = Pattern.compile("(\\/.*?\\.apk)").matcher(line);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private String guessApkPath(File projectRoot, boolean releaseSigningEnabled) {
        if (releaseSigningEnabled) {
            File signedRelease = new File(projectRoot, "app/build/outputs/apk/release/app-release.apk");
            if (signedRelease.exists()) {
                return signedRelease.getAbsolutePath();
            }
        }
        File apk = new File(projectRoot, "app/build/outputs/apk/debug/app-debug.apk");
        if (apk.exists()) {
            return apk.getAbsolutePath();
        }
        File releaseApk = new File(projectRoot, "app/build/outputs/apk/release/app-release.apk");
        if (releaseApk.exists()) {
            return releaseApk.getAbsolutePath();
        }
        return "";
    }

    private ProjectSigningConfig readSigningConfig(File projectRoot) {
        Properties properties = new Properties();
        File signingFile = new File(new File(projectRoot, META_DIR), SIGNING_FILE);
        if (!signingFile.exists()) {
            return new ProjectSigningConfig(false, "", "", "", "");
        }
        FileInputStream inputStream = null;
        try {
            inputStream = new FileInputStream(signingFile);
            properties.load(inputStream);
            return new ProjectSigningConfig(
                Boolean.parseBoolean(safeText(properties.getProperty("enabled"))),
                safeText(properties.getProperty("storeFilePath")),
                safeText(properties.getProperty("storePassword")),
                safeText(properties.getProperty("keyAlias")),
                safeText(properties.getProperty("keyPassword"))
            );
        } catch (Exception e) {
            return new ProjectSigningConfig(false, "", "", "", "");
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (Exception ignored) {
            }
        }
    }

    private String validateSigningConfig(ProjectSigningConfig config) {
        if (config == null || !config.isEnabled()) {
            return "";
        }
        if (config.getStoreFilePath().length() == 0) {
            return "签名文件路径为空";
        }
        File storeFile = new File(config.getStoreFilePath());
        if (!storeFile.exists() || !storeFile.isFile()) {
            return "签名文件不存在：" + config.getStoreFilePath();
        }
        if (config.getStorePassword().length() == 0) {
            return "store password 不能为空";
        }
        if (config.getKeyAlias().length() == 0) {
            return "key alias 不能为空";
        }
        if (config.getKeyPassword().length() == 0) {
            return "key password 不能为空";
        }
        return "";
    }

    private ManagedSigningScriptState applyManagedSigningBlock(File projectRoot, ProjectSigningConfig config, BuildListener listener) throws Exception {
        File appGradle = new File(projectRoot, "app/build.gradle");
        File appGradleKts = new File(projectRoot, "app/build.gradle.kts");
        File targetFile = appGradle.exists() ? appGradle : appGradleKts;
        if (!targetFile.exists()) {
            throw new IllegalStateException("未找到 app/build.gradle 或 app/build.gradle.kts，无法注入签名配置");
        }
        String content = readText(targetFile);
        String cleaned = removeManagedSigningBlock(content);
        String finalContent = injectManagedSigning(cleaned, config, targetFile.getName().endsWith(".kts"));
        writeText(targetFile, finalContent);
        if (listener != null) {
            listener.onLogLine("已将 APK 签名配置注入到 " + targetFile.getName() + "。");
        }
        return new ManagedSigningScriptState(targetFile, content);
    }

    private void restoreManagedSigningBlock(ManagedSigningScriptState state, BuildListener listener) {
        if (state == null || state.targetFile == null || state.originalContent == null) {
            return;
        }
        try {
            writeText(state.targetFile, state.originalContent);
            if (listener != null) {
                listener.onLogLine("已恢复 " + state.targetFile.getName() + " 中的原始签名脚本内容。");
            }
        } catch (Exception e) {
            if (listener != null) {
                listener.onLogLine("恢复签名脚本失败：" + e.getMessage());
            }
        }
    }

    private String removeManagedSigningBlock(String content) {
        if (content == null || content.length() == 0) {
            return "";
        }
        Pattern managedBlockPattern = Pattern.compile(
            Pattern.quote(SIGNING_BLOCK_BEGIN) + "[\\s\\S]*?" + Pattern.quote(SIGNING_BLOCK_END) + "\\s*",
            Pattern.MULTILINE
        );
        return managedBlockPattern.matcher(content).replaceAll("").trim() + "\n";
    }

    private String injectManagedSigning(String content, ProjectSigningConfig config, boolean kotlinDsl) {
        ScriptBlockSpan androidBlock = findBlock(content, "(?m)^\\s*android\\s*\\{", 0, content.length());
        if (androidBlock == null) {
            throw new IllegalStateException("未找到 android { } 配置块，无法注入签名配置");
        }
        String updated = injectSigningConfig(content, androidBlock, config, kotlinDsl);
        androidBlock = findBlock(updated, "(?m)^\\s*android\\s*\\{", 0, updated.length());
        if (androidBlock == null) {
            throw new IllegalStateException("签名注入后未能重新定位 android { } 配置块");
        }
        updated = injectReleaseSigning(updated, androidBlock, kotlinDsl);
        return updated.trim() + "\n";
    }

    private String injectSigningConfig(String content, ScriptBlockSpan androidBlock, ProjectSigningConfig config, boolean kotlinDsl) {
        ScriptBlockSpan signingConfigsBlock = findBlock(
            content,
            "(?m)^\\s*signingConfigs\\s*\\{",
            androidBlock.openBrace + 1,
            androidBlock.endExclusive - 1
        );
        if (signingConfigsBlock != null) {
            String blockIndent = getLineIndent(content, signingConfigsBlock.start);
            String childIndent = blockIndent + detectIndentUnit(blockIndent);
            String snippet = buildManagedSigningConfigEntry(config, kotlinDsl, childIndent, detectIndentUnit(blockIndent));
            return insertIntoBlock(content, signingConfigsBlock, snippet);
        }
        String androidIndent = getLineIndent(content, androidBlock.start);
        String childIndent = androidIndent + detectIndentUnit(androidIndent);
        String snippet = buildManagedSigningConfigBlock(config, kotlinDsl, childIndent, detectIndentUnit(androidIndent));
        ScriptBlockSpan buildTypesBlock = findBlock(
            content,
            "(?m)^\\s*buildTypes\\s*\\{",
            androidBlock.openBrace + 1,
            androidBlock.endExclusive - 1
        );
        if (buildTypesBlock != null) {
            return insertAt(content, buildTypesBlock.start, ensureBlankLineBefore(snippet));
        }
        return insertIntoBlock(content, androidBlock, snippet);
    }

    private String injectReleaseSigning(String content, ScriptBlockSpan androidBlock, boolean kotlinDsl) {
        ScriptBlockSpan buildTypesBlock = findBlock(
            content,
            "(?m)^\\s*buildTypes\\s*\\{",
            androidBlock.openBrace + 1,
            androidBlock.endExclusive - 1
        );
        if (buildTypesBlock == null) {
            String androidIndent = getLineIndent(content, androidBlock.start);
            String childIndent = androidIndent + detectIndentUnit(androidIndent);
            String snippet = buildManagedBuildTypesBlock(kotlinDsl, childIndent, detectIndentUnit(androidIndent));
            return insertIntoBlock(content, androidBlock, ensureBlankLineBefore(snippet));
        }
        ScriptBlockSpan releaseBlock = findBlock(
            content,
            kotlinDsl
                ? "(?m)^\\s*(?:getByName|named|create)\\(\"release\"\\)\\s*\\{"
                : "(?m)^\\s*release\\s*\\{",
            buildTypesBlock.openBrace + 1,
            buildTypesBlock.endExclusive - 1
        );
        if (releaseBlock == null) {
            String buildTypesIndent = getLineIndent(content, buildTypesBlock.start);
            String childIndent = buildTypesIndent + detectIndentUnit(buildTypesIndent);
            String snippet = buildManagedReleaseBlock(kotlinDsl, childIndent, detectIndentUnit(buildTypesIndent));
            return insertIntoBlock(content, buildTypesBlock, ensureBlankLineBefore(snippet));
        }
        String releaseBody = content.substring(releaseBlock.openBrace + 1, releaseBlock.endExclusive - 1);
        releaseBody = stripExistingSigningConfigAssignments(releaseBody);
        releaseBody = ensureBodyEndsWithNewline(releaseBody);
        String releaseIndent = getLineIndent(content, releaseBlock.start);
        String childIndent = releaseIndent + detectIndentUnit(releaseIndent);
        String snippet = buildManagedReleaseAssignment(kotlinDsl, childIndent);
        return content.substring(0, releaseBlock.openBrace + 1)
            + releaseBody
            + snippet
            + releaseIndent + "}"
            + content.substring(releaseBlock.endExclusive);
    }

    private String buildManagedSigningConfigBlock(ProjectSigningConfig config, boolean kotlinDsl, String baseIndent, String indentUnit) {
        StringBuilder builder = new StringBuilder();
        builder.append(baseIndent).append(SIGNING_BLOCK_BEGIN).append('\n');
        builder.append(baseIndent).append("signingConfigs {\n");
        builder.append(buildSigningConfigBody(config, kotlinDsl, baseIndent + indentUnit, indentUnit));
        builder.append(baseIndent).append("}\n");
        builder.append(baseIndent).append(SIGNING_BLOCK_END).append('\n');
        return builder.toString();
    }

    private String buildManagedSigningConfigEntry(ProjectSigningConfig config, boolean kotlinDsl, String baseIndent, String indentUnit) {
        StringBuilder builder = new StringBuilder();
        builder.append(baseIndent).append(SIGNING_BLOCK_BEGIN).append('\n');
        builder.append(buildSigningConfigBody(config, kotlinDsl, baseIndent, indentUnit));
        builder.append(baseIndent).append(SIGNING_BLOCK_END).append('\n');
        return builder.toString();
    }

    private String buildSigningConfigBody(ProjectSigningConfig config, boolean kotlinDsl, String baseIndent, String indentUnit) {
        StringBuilder builder = new StringBuilder();
        if (kotlinDsl) {
            builder.append(baseIndent).append("create(\"apkBuilderRelease\") {\n");
            builder.append(baseIndent).append(indentUnit).append("storeFile = file(\"").append(escapeGradleString(config.getStoreFilePath())).append("\")\n");
            builder.append(baseIndent).append(indentUnit).append("storePassword = \"").append(escapeGradleString(config.getStorePassword())).append("\"\n");
            builder.append(baseIndent).append(indentUnit).append("keyAlias = \"").append(escapeGradleString(config.getKeyAlias())).append("\"\n");
            builder.append(baseIndent).append(indentUnit).append("keyPassword = \"").append(escapeGradleString(config.getKeyPassword())).append("\"\n");
            builder.append(baseIndent).append(indentUnit).append("enableV1Signing = true\n");
            builder.append(baseIndent).append(indentUnit).append("enableV2Signing = true\n");
            builder.append(baseIndent).append("}\n");
        } else {
            builder.append(baseIndent).append("apkBuilderRelease {\n");
            builder.append(baseIndent).append(indentUnit).append("storeFile file(\"").append(escapeGradleString(config.getStoreFilePath())).append("\")\n");
            builder.append(baseIndent).append(indentUnit).append("storePassword \"").append(escapeGradleString(config.getStorePassword())).append("\"\n");
            builder.append(baseIndent).append(indentUnit).append("keyAlias \"").append(escapeGradleString(config.getKeyAlias())).append("\"\n");
            builder.append(baseIndent).append(indentUnit).append("keyPassword \"").append(escapeGradleString(config.getKeyPassword())).append("\"\n");
            builder.append(baseIndent).append(indentUnit).append("v1SigningEnabled true\n");
            builder.append(baseIndent).append(indentUnit).append("v2SigningEnabled true\n");
            builder.append(baseIndent).append("}\n");
        }
        return builder.toString();
    }

    private String buildManagedBuildTypesBlock(boolean kotlinDsl, String baseIndent, String indentUnit) {
        StringBuilder builder = new StringBuilder();
        builder.append(baseIndent).append(SIGNING_BLOCK_BEGIN).append('\n');
        builder.append(baseIndent).append("buildTypes {\n");
        if (kotlinDsl) {
            builder.append(baseIndent).append(indentUnit).append("getByName(\"release\") {\n");
            builder.append(baseIndent).append(indentUnit).append(indentUnit).append("signingConfig = signingConfigs.getByName(\"apkBuilderRelease\")\n");
            builder.append(baseIndent).append(indentUnit).append("}\n");
        } else {
            builder.append(baseIndent).append(indentUnit).append("release {\n");
            builder.append(baseIndent).append(indentUnit).append(indentUnit).append("signingConfig signingConfigs.apkBuilderRelease\n");
            builder.append(baseIndent).append(indentUnit).append("}\n");
        }
        builder.append(baseIndent).append("}\n");
        builder.append(baseIndent).append(SIGNING_BLOCK_END).append('\n');
        return builder.toString();
    }

    private String buildManagedReleaseBlock(boolean kotlinDsl, String baseIndent, String indentUnit) {
        StringBuilder builder = new StringBuilder();
        builder.append(baseIndent).append(SIGNING_BLOCK_BEGIN).append('\n');
        if (kotlinDsl) {
            builder.append(baseIndent).append("getByName(\"release\") {\n");
            builder.append(baseIndent).append(indentUnit).append("signingConfig = signingConfigs.getByName(\"apkBuilderRelease\")\n");
            builder.append(baseIndent).append("}\n");
        } else {
            builder.append(baseIndent).append("release {\n");
            builder.append(baseIndent).append(indentUnit).append("signingConfig signingConfigs.apkBuilderRelease\n");
            builder.append(baseIndent).append("}\n");
        }
        builder.append(baseIndent).append(SIGNING_BLOCK_END).append('\n');
        return builder.toString();
    }

    private String buildManagedReleaseAssignment(boolean kotlinDsl, String baseIndent) {
        StringBuilder builder = new StringBuilder();
        builder.append(baseIndent).append(SIGNING_BLOCK_BEGIN).append('\n');
        if (kotlinDsl) {
            builder.append(baseIndent).append("signingConfig = signingConfigs.getByName(\"apkBuilderRelease\")\n");
        } else {
            builder.append(baseIndent).append("signingConfig signingConfigs.apkBuilderRelease\n");
        }
        builder.append(baseIndent).append(SIGNING_BLOCK_END).append('\n');
        return builder.toString();
    }

    private String stripExistingSigningConfigAssignments(String content) {
        return content.replaceAll("(?m)^\\s*signingConfig(?:\\s*=|\\s+).*(?:\\r?\\n)?", "");
    }

    private String ensureBodyEndsWithNewline(String body) {
        if (body == null || body.length() == 0) {
            return "\n";
        }
        return body.endsWith("\n") ? body : body + "\n";
    }

    private String ensureBlankLineBefore(String snippet) {
        return "\n" + snippet;
    }

    private String insertIntoBlock(String content, ScriptBlockSpan block, String snippet) {
        int insertIndex = block.endExclusive - 1;
        String normalizedSnippet = ensureBodyEndsWithNewline(snippet);
        if (insertIndex > 0) {
            char previous = content.charAt(insertIndex - 1);
            if (previous != '\n' && previous != '\r') {
                normalizedSnippet = "\n" + normalizedSnippet;
            }
        }
        return insertAt(content, insertIndex, normalizedSnippet);
    }

    private String insertAt(String content, int index, String snippet) {
        return content.substring(0, index) + snippet + content.substring(index);
    }

    private ScriptBlockSpan findBlock(String content, String regex, int regionStart, int regionEnd) {
        if (content == null || regionStart < 0 || regionEnd <= regionStart || regionEnd > content.length()) {
            return null;
        }
        Matcher matcher = Pattern.compile(regex, Pattern.MULTILINE).matcher(content);
        matcher.region(regionStart, regionEnd);
        if (!matcher.find()) {
            return null;
        }
        int openBrace = content.indexOf('{', matcher.start());
        if (openBrace < 0 || openBrace >= regionEnd) {
            return null;
        }
        int closeBrace = findMatchingBrace(content, openBrace);
        if (closeBrace < 0) {
            return null;
        }
        return new ScriptBlockSpan(matcher.start(), openBrace, closeBrace + 1);
    }

    private int findMatchingBrace(String content, int openBrace) {
        int depth = 0;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        for (int i = openBrace; i < content.length(); i++) {
            char c = content.charAt(i);
            char previous = i > 0 ? content.charAt(i - 1) : '\0';
            if (c == '"' && !inSingleQuote && previous != '\\') {
                inDoubleQuote = !inDoubleQuote;
                continue;
            }
            if (c == '\'' && !inDoubleQuote && previous != '\\') {
                inSingleQuote = !inSingleQuote;
                continue;
            }
            if (inSingleQuote || inDoubleQuote) {
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private String getLineIndent(String content, int index) {
        int lineStart = index;
        while (lineStart > 0) {
            char c = content.charAt(lineStart - 1);
            if (c == '\n' || c == '\r') {
                break;
            }
            lineStart--;
        }
        int cursor = lineStart;
        while (cursor < content.length()) {
            char c = content.charAt(cursor);
            if (c == ' ' || c == '\t') {
                cursor++;
                continue;
            }
            break;
        }
        return content.substring(lineStart, cursor);
    }

    private String detectIndentUnit(String baseIndent) {
        return baseIndent.indexOf('\t') >= 0 ? "\t" : "    ";
    }

    private String escapeGradleString(String value) {
        return safeText(value).replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private int parseIntSafe(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return -1;
        }
    }

    private String suggestFix(String message, String codeLine) {
        String lower = message == null ? "" : message.toLowerCase();
        if (lower.indexOf("';' expected") >= 0 || lower.indexOf("expecting ';'") >= 0) {
            return "检查这一行结尾是否缺少分号 `;`。";
        }
        if (lower.indexOf("cannot find symbol") >= 0) {
            return "检查类名、方法名、变量名和 import 是否写错，必要时同步依赖。";
        }
        if (lower.indexOf("package") >= 0 && lower.indexOf("does not exist") >= 0) {
            return "当前包或依赖不存在，检查 `import`、包名目录和 Gradle 依赖。";
        }
        if (lower.indexOf("android resource linking failed") >= 0) {
            return "资源链接失败，优先检查 XML 是否闭合、资源名是否存在、引用格式是否正确。";
        }
        if (lower.indexOf("parseerror") >= 0 || lower.indexOf("xml") >= 0) {
            return "XML 可能有标签未闭合、属性引号不完整，或特殊字符未转义。";
        }
        if (lower.indexOf("sdk location not found") >= 0 || lower.indexOf("sdk.dir") >= 0) {
            return "检查项目 `local.properties` 是否已写入正确的 `sdk.dir`，并确认 Android SDK 已准备完成。";
        }
        if (lower.indexOf("ndk") >= 0 && lower.indexOf("not configured") >= 0) {
            return "检查 `ndk.dir`、`ndkVersion` 和当前选择的 NDK 27 是否一致。";
        }
        if (lower.indexOf("gradle") >= 0 && lower.indexOf("wrapper") >= 0) {
            return "优先使用外置 Gradle 下载链路，或在应用自定义弹窗中补齐 `gradle/wrapper` 目录。";
        }
        if (lower.indexOf("license") >= 0 || lower.indexOf("licenses") >= 0) {
            return "请先到设置页执行“安装 Android API”，显式确认 Android SDK 许可证后再重新构建。";
        }
        if (codeLine != null && codeLine.trim().length() > 0) {
            return "先修正当前高亮行的语法，再重新打包验证。";
        }
        return "先根据错误行号检查代码，再重新执行打包。";
    }

    private int extractInt(String content, String regex) {
        String value = extractString(content, regex);
        return parseIntSafe(value);
    }

    private String extractString(String content, String regex) {
        if (content == null || content.length() == 0) {
            return "";
        }
        Matcher matcher = Pattern.compile(regex).matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "";
    }

    private String readText(File file) throws Exception {
        InputStream inputStream = null;
        try {
            inputStream = new FileInputStream(file);
            byte[] buffer = new byte[4096];
            java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, len);
            }
            return new String(outputStream.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }
    }

    private void writeText(File file, String content) throws Exception {
        ensureDir(file.getParentFile());
        FileOutputStream outputStream = null;
        try {
            outputStream = new FileOutputStream(file);
            outputStream.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            outputStream.flush();
        } finally {
            if (outputStream != null) {
                outputStream.close();
            }
        }
    }

    private void copyStreamWithProgress(
        InputStream inputStream,
        BufferedOutputStream outputStream,
        long totalBytes,
        ProgressCallback progressCallback,
        String label
    ) throws Exception {
        byte[] buffer = new byte[65536];
        int len;
        long copiedBytes = 0L;
        int lastPercent = -1;
        while ((len = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, len);
            copiedBytes += len;
            if (progressCallback != null && copiedBytes % 262144 == 0) {
                if (totalBytes > 0L) {
                    int percent = (int) Math.min(100L, (copiedBytes * 100L) / totalBytes);
                    if (percent != lastPercent) {
                        progressCallback.onProgress(
                            label + "  " + formatSize(copiedBytes) + " / " + formatSize(totalBytes),
                            percent,
                            false
                        );
                        lastPercent = percent;
                    }
                } else {
                    progressCallback.onProgress(label + "  已完成 " + formatSize(copiedBytes), 0, true);
                }
            }
        }
        if (progressCallback != null && totalBytes > 0L) {
            progressCallback.onProgress(label + "  完成 " + formatSize(copiedBytes), 100, false);
        }
        outputStream.flush();
    }

    private void copyArchiveEntryStream(
        InputStream inputStream,
        BufferedOutputStream outputStream,
        long totalBytes,
        long[] extractedBytes,
        ProgressCallback progressCallback,
        String label
    ) throws Exception {
        byte[] buffer = new byte[65536];
        int len;
        int lastPercent = -1;
        while ((len = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, len);
            extractedBytes[0] += len;
            if (progressCallback != null && extractedBytes[0] % 262144 == 0) {
                if (totalBytes > 0L) {
                    int percent = (int) Math.min(100L, (extractedBytes[0] * 100L) / totalBytes);
                    if (percent != lastPercent) {
                        progressCallback.onProgress(
                            label + "  " + formatSize(extractedBytes[0]) + " / " + formatSize(totalBytes),
                            percent,
                            false
                        );
                        lastPercent = percent;
                    }
                } else {
                    progressCallback.onProgress(label + "  已完成 " + formatSize(extractedBytes[0]), 0, true);
                }
            }
        }
        outputStream.flush();
    }

    private ProgressCallback remapProgress(final ProgressCallback callback, final int startPercent, final int weightPercent) {
        if (callback == null) {
            return null;
        }
        return new ProgressCallback() {
            @Override
            public void onProgress(String message, int percent, boolean indeterminate) {
                int safePercent = percent < 0 ? 0 : Math.min(100, percent);
                int mapped = Math.min(100, startPercent + ((safePercent * weightPercent) / 100));
                callback.onProgress(message, mapped, false);
            }
        };
    }

    private long calculateZipTotalBytes(ZipFile zipFile) {
        long totalBytes = 0L;
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (!entry.isDirectory() && entry.getSize() > 0L) {
                totalBytes += entry.getSize();
            }
        }
        return totalBytes;
    }

    private void addIssueIfAbsent(ArrayList<BuildIssue> issues, BuildIssue newIssue) {
        if (newIssue == null) {
            return;
        }
        for (int i = 0; i < issues.size(); i++) {
            BuildIssue existing = issues.get(i);
            if (sameText(existing.getFilePath(), newIssue.getFilePath())
                && existing.getLineNumber() == newIssue.getLineNumber()
                && sameText(existing.getMessage(), newIssue.getMessage())) {
                return;
            }
        }
        issues.add(newIssue);
    }

    private boolean sameText(String first, String second) {
        String a = first == null ? "" : first.trim();
        String b = second == null ? "" : second.trim();
        return a.equals(b);
    }

    private String simplifyEntryName(String entryName) {
        if (entryName == null || entryName.length() == 0) {
            return "当前文件";
        }
        String normalized = entryName.replace('\\', '/');
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1) : normalized;
    }

    private String formatSize(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024L) {
            return String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0);
        }
        if (bytes < 1024L * 1024L * 1024L) {
            return String.format(java.util.Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0);
        }
        return String.format(java.util.Locale.US, "%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0);
    }
}
