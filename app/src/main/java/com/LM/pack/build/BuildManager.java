package com.LM.pack.build;

import android.content.Context;
import android.content.res.AssetManager;
import com.LM.pack.env.EnvironmentManager;
import com.LM.pack.model.BuildIssue;
import com.LM.pack.model.BuildResult;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BuildManager {

    private static final String OFFLINE_GRADLE_ASSET_PATH = "toolchains/gradle/gradle-8.7-bin.zip";

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
            File offlineGradleExecutable = prepareOfflineGradle(listener);
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

    private File prepareOfflineGradle(BuildListener listener) {
        try {
            ensureDir(new File(environmentManager.getPackageCacheDir()));
            File archiveFile = new File(environmentManager.getGradlePackageArchivePath());
            if (!archiveFile.exists() || archiveFile.length() == 0L) {
                if (!assetExists(OFFLINE_GRADLE_ASSET_PATH)) {
                    return null;
                }
                listener.onLogLine("检测到内置 Gradle 资源，正在复制到本地缓存。");
                copyAssetToFile(OFFLINE_GRADLE_ASSET_PATH, archiveFile);
            }
            File installRoot = new File(environmentManager.getGradleInstallDir());
            File gradleBin = findGradleExecutable(installRoot);
            if (gradleBin != null) {
                return gradleBin;
            }
            listener.onLogLine("正在准备离线 Gradle 目录。");
            clearDirectory(installRoot);
            ensureDir(installRoot);
            extractZip(archiveFile, installRoot);
            gradleBin = findGradleExecutable(installRoot);
            if (gradleBin == null) {
                throw new IllegalStateException("离线 Gradle 解压后未找到 bin/gradle");
            }
            gradleBin.setExecutable(true);
            return gradleBin;
        } catch (Exception e) {
            if (listener != null) {
                listener.onLogLine("离线 Gradle 准备失败，回退到项目 Wrapper：" + e.getMessage());
            }
            return null;
        }
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

    private void copyAssetToFile(String assetPath, File targetFile) throws Exception {
        ensureDir(targetFile.getParentFile());
        AssetManager assetManager = context.getAssets();
        InputStream inputStream = null;
        BufferedOutputStream outputStream = null;
        try {
            inputStream = assetManager.open(assetPath);
            outputStream = new BufferedOutputStream(new FileOutputStream(targetFile));
            byte[] buffer = new byte[8192];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, len);
            }
            outputStream.flush();
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
            if (outputStream != null) {
                outputStream.close();
            }
        }
    }

    private void extractZip(File archiveFile, File targetDir) throws Exception {
        ZipInputStream zipInputStream = null;
        try {
            zipInputStream = new ZipInputStream(new BufferedInputStream(new java.io.FileInputStream(archiveFile)));
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                File outputFile = new File(targetDir, entry.getName());
                String targetPath = targetDir.getCanonicalPath();
                String outputPath = outputFile.getCanonicalPath();
                if (!outputPath.startsWith(targetPath + File.separator) && !outputPath.equals(targetPath)) {
                    throw new IllegalStateException("检测到非法路径：" + entry.getName());
                }
                if (entry.isDirectory()) {
                    ensureDir(outputFile);
                } else {
                    ensureDir(outputFile.getParentFile());
                    BufferedOutputStream outputStream = null;
                    try {
                        outputStream = new BufferedOutputStream(new FileOutputStream(outputFile));
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zipInputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, len);
                        }
                        outputStream.flush();
                    } finally {
                        if (outputStream != null) {
                            outputStream.close();
                        }
                    }
                }
                zipInputStream.closeEntry();
            }
        } finally {
            if (zipInputStream != null) {
                zipInputStream.close();
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

    private void collectIssue(String line, ArrayList<BuildIssue> issues) {
        if (line == null) {
            return;
        }
        Matcher lineErrorMatcher = Pattern.compile("^(.+?):(\\d+):\\s*(?:error:)?\\s*(.+)$").matcher(line.trim());
        if (lineErrorMatcher.find()) {
            String filePath = lineErrorMatcher.group(1).trim();
            int lineNumber = parseIntSafe(lineErrorMatcher.group(2));
            String message = lineErrorMatcher.group(3).trim();
            issues.add(new BuildIssue(filePath, lineNumber, message, suggestFix(message, "")));
            return;
        }

        Matcher javaStyleMatcher = Pattern.compile("^(.*\\.(?:java|kt|xml|gradle)):\\s*(.+)$").matcher(line.trim());
        if (javaStyleMatcher.find() && line.toLowerCase().indexOf("error") >= 0) {
            issues.add(new BuildIssue(javaStyleMatcher.group(1).trim(), -1, javaStyleMatcher.group(2).trim(), suggestFix(line, "")));
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
        if (codeLine != null && codeLine.trim().length() > 0) {
            return "先修正当前高亮行的语法，再重新打包验证。";
        }
        return "先根据错误行号检查代码，再重新执行打包。";
    }
}
