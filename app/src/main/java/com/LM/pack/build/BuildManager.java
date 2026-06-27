package com.LM.pack.build;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import com.LM.pack.env.EnvironmentManager;
import com.LM.pack.model.BuildIssue;
import com.LM.pack.model.BuildResult;
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
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BuildManager {

    public interface OfflineGradleListener {
        void onProgress(String message, int percent, boolean indeterminate);
        void onSuccess(File gradleExecutable);
        void onError(String message);
    }

    private static final String OFFLINE_GRADLE_ASSET_PATH = "toolchains/gradle/gradle-8.7-bin.zip";
    private static final String[] OFFLINE_GRADLE_URLS = EnvironmentManager.GRADLE_VERIFIED_DIRECT_URLS;

    private final Context context;
    private final EnvironmentManager environmentManager;

    public BuildManager(Context context, EnvironmentManager environmentManager) {
        this.context = context.getApplicationContext();
        this.environmentManager = environmentManager;
    }

    public interface BuildListener {
        void onLogLine(String line);
        void onFinished(BuildResult result);
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
        new Thread(new Runnable() {
            @Override
            public void run() {
                executeGradleBuild(projectDir, jdkDir, sdkDir, ndkDir, selectedJdkName, listener);
            }
        }).start();
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
        ArrayList<BuildIssue> issues = new ArrayList<BuildIssue>();
        String apkPath = "";
        try {
            File projectRoot = new File(projectDir);
            File gradlew = new File(projectRoot, "gradlew");
            ProjectRequirements requirements = inspectProjectRequirements(projectRoot);
            logResolvedEnvironment(requirements, selectedJdkName, jdkDir, sdkDir, ndkDir, listener);
            syncProjectLocalProperties(projectRoot, sdkDir, ndkDir, listener);
            prepareRequiredSdkPackages(projectRoot, jdkDir, sdkDir, listener, requirements);
            File offlineGradleExecutable = prepareOfflineGradle(null, listener);
            ProcessBuilder processBuilder;
            if (offlineGradleExecutable != null) {
                listener.onLogLine("开始执行真实构建命令。");
                listener.onLogLine("构建目录: " + projectDir);
                listener.onLogLine("JDK 环境: " + selectedJdkName);
                if (sdkDir != null && sdkDir.length() > 0) {
                    listener.onLogLine("Android SDK: " + sdkDir);
                }
                listener.onLogLine("Gradle 模式: 内置离线 Gradle 8.7");
                listener.onLogLine("Gradle 任务: assembleDebug --stacktrace");
                processBuilder = new ProcessBuilder(
                    offlineGradleExecutable.getAbsolutePath(),
                    "-p",
                    projectDir,
                    "assembleDebug",
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
                listener.onLogLine("Gradle 任务: assembleDebug --stacktrace");
                processBuilder = new ProcessBuilder("./gradlew", "assembleDebug", "--stacktrace");
            }
            processBuilder.directory(projectRoot);
            processBuilder.redirectErrorStream(true);
            processBuilder.environment().put("JAVA_HOME", jdkDir);
            if (sdkDir != null && sdkDir.length() > 0) {
                processBuilder.environment().put("ANDROID_HOME", sdkDir);
                processBuilder.environment().put("ANDROID_SDK_ROOT", sdkDir);
            }
            processBuilder.environment().put("ANDROID_NDK_HOME", ndkDir);
            processBuilder.environment().put("ANDROID_NDK_ROOT", ndkDir);
            processBuilder.environment().put("GRADLE_USER_HOME", environmentManager.getGradleUserHomeDir());
            String currentPath = processBuilder.environment().get("PATH");
            if (currentPath == null) {
                currentPath = "";
            }
            processBuilder.environment().put("PATH", jdkDir + "/bin:" + currentPath);

            process = processBuilder.start();
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                listener.onLogLine(line);
                collectIssue(line, issues);
                String detectedApkPath = detectApkPath(line);
                if (detectedApkPath.length() > 0) {
                    apkPath = detectedApkPath;
                }
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                if (apkPath.length() == 0) {
                    apkPath = guessApkPath(projectRoot);
                }
                listener.onFinished(new BuildResult(true, 0, "真实打包完成", apkPath, issues));
            } else {
                listener.onFinished(new BuildResult(false, exitCode, "真实打包失败，Gradle 退出码：" + exitCode, apkPath, issues));
            }
        } catch (Exception e) {
            listener.onFinished(new BuildResult(false, -1, "真实打包异常：" + e.getMessage(), apkPath, issues));
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (Exception e) {
            }
            if (process != null) {
                process.destroy();
            }
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
        listener.onLogLine("JDK 环境: " + selectedJdkName + "  ->  " + jdkDir);
        listener.onLogLine("NDK 环境: NDK r27  ->  " + ndkDir);
        listener.onLogLine("Gradle 环境: 内置 Gradle 8.7");
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
        ensureSdkLicenses(sdkRoot);
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
        return compileSdk + ".0.0";
    }

    private void ensureSdkLicenses(File sdkRoot) throws Exception {
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
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (listener != null) {
                    listener.onLogLine(line);
                }
            }
            int exitCode = process.waitFor();
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
            if (process != null) {
                process.destroy();
            }
        }
    }

    private File prepareOfflineGradle(ProgressCallback progressCallback, BuildListener listener) {
        try {
            ensureDir(new File(environmentManager.getPackageCacheDir()));
            File archiveFile = new File(environmentManager.getGradlePackageArchivePath());
            if (!archiveFile.exists() || archiveFile.length() == 0L) {
                if (assetExists(OFFLINE_GRADLE_ASSET_PATH)) {
                    listener.onLogLine("检测到内置 Gradle 资源，正在复制到本地缓存。");
                    copyAssetToFile(OFFLINE_GRADLE_ASSET_PATH, archiveFile, progressCallback, "正在复制 Gradle 8.7");
                } else {
                    String downloadUrl = resolveAvailableGradleUrl();
                    if (downloadUrl == null) {
                        return null;
                    }
                    listener.onLogLine("未找到内置 Gradle，正在下载可用的 Gradle 8.7 安装包。");
                    downloadToFile(downloadUrl, archiveFile, progressCallback, "正在下载 Gradle 8.7");
                }
            }
            File installRoot = new File(environmentManager.getGradleInstallDir());
            File gradleBin = findGradleExecutable(installRoot);
            if (gradleBin != null) {
                if (progressCallback != null) {
                    progressCallback.onProgress("Gradle 8.7 已就绪", 100, false);
                }
                return gradleBin;
            }
            listener.onLogLine("正在准备离线 Gradle 目录。");
            clearDirectory(installRoot);
            ensureDir(installRoot);
            extractZip(archiveFile, installRoot, progressCallback, "正在解压 Gradle 8.7");
            gradleBin = findGradleExecutable(installRoot);
            if (gradleBin == null) {
                throw new IllegalStateException("离线 Gradle 解压后未找到 bin/gradle");
            }
            gradleBin.setExecutable(true);
            if (progressCallback != null) {
                progressCallback.onProgress("Gradle 8.7 已准备完成", 100, false);
            }
            return gradleBin;
        } catch (Exception e) {
            if (listener != null) {
                listener.onLogLine("离线 Gradle 准备失败，回退到项目 Wrapper：" + e.getMessage());
            }
            return null;
        }
    }

    private String resolveAvailableGradleUrl() {
        for (int i = 0; i < OFFLINE_GRADLE_URLS.length; i++) {
            if (isUrlReachable(OFFLINE_GRADLE_URLS[i], 0)) {
                return OFFLINE_GRADLE_URLS[i];
            }
        }
        return null;
    }

    private boolean assetExists(String assetPath) {
        InputStream inputStream = null;
        try {
            inputStream = context.getAssets().open(assetPath);
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

    private void copyAssetToFile(String assetPath, File targetFile, ProgressCallback progressCallback, String label) throws Exception {
        ensureDir(targetFile.getParentFile());
        AssetManager assetManager = context.getAssets();
        AssetFileDescriptor descriptor = null;
        InputStream inputStream = null;
        BufferedOutputStream outputStream = null;
        try {
            long totalBytes = -1L;
            try {
                descriptor = assetManager.openFd(assetPath);
                totalBytes = descriptor.getLength();
            } catch (Exception ignored) {
            }
            inputStream = assetManager.open(assetPath);
            outputStream = new BufferedOutputStream(new FileOutputStream(targetFile));
            copyStreamWithProgress(inputStream, outputStream, totalBytes, progressCallback, label);
        } finally {
            if (descriptor != null) {
                descriptor.close();
            }
            if (inputStream != null) {
                inputStream.close();
            }
            if (outputStream != null) {
                outputStream.close();
            }
        }
    }

    private void downloadToFile(String urlString, File targetFile, ProgressCallback progressCallback, String label) throws Exception {
        java.net.HttpURLConnection connection = null;
        InputStream inputStream = null;
        BufferedOutputStream outputStream = null;
        try {
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
            outputStream = new BufferedOutputStream(new FileOutputStream(targetFile));
            copyStreamWithProgress(inputStream, outputStream, totalBytes, progressCallback, label);
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
        Matcher lineColumnMatcher = Pattern.compile("^(.+?):(\\d+):(\\d+):\\s*(?:error:)?\\s*(.+)$").matcher(trimmed);
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
        Matcher lineErrorMatcher = Pattern.compile("^(.+?):(\\d+):\\s*(?:error:)?\\s*(.+)$").matcher(trimmed);
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

    private String guessApkPath(File projectRoot) {
        File apk = new File(projectRoot, "app/build/outputs/apk/debug/app-debug.apk");
        if (apk.exists()) {
            return apk.getAbsolutePath();
        }
        return "";
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
            return "优先使用内置 Gradle 8.7，或补齐 `gradle/wrapper` 目录。";
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
        byte[] buffer = new byte[8192];
        int len;
        long copiedBytes = 0L;
        int lastPercent = -1;
        while ((len = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, len);
            copiedBytes += len;
            if (progressCallback != null) {
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
        byte[] buffer = new byte[8192];
        int len;
        int lastPercent = -1;
        while ((len = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, len);
            extractedBytes[0] += len;
            if (progressCallback != null) {
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
