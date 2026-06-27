package com.LM.pack.project;

import android.content.Context;
import android.content.res.AssetManager;
import com.LM.pack.model.ProjectConfig;
import com.LM.pack.model.ProjectEntry;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ProjectManager {

    private static final String META_DIR = ".lmproject";
    private static final String META_FILE = "meta.properties";

    public interface ExtractProgressListener {
        void onProgress(String message, int percent);
    }

    public interface CopyProgressListener {
        void onProgress(String message, int percent);
    }

    public File createShellProject(Context context, ProjectConfig config, String projectRootPath) throws IOException {
        File projectRoot = new File(projectRootPath);
        File appDir = new File(projectRoot, "app");
        File javaDir = new File(appDir, "src/main/java/" + config.getPackagePath());
        File resLayoutDir = new File(appDir, "src/main/res/layout");
        File resValuesDir = new File(appDir, "src/main/res/values");
        File resDrawableDir = new File(appDir, "src/main/res/drawable");
        File manifestFile = new File(appDir, "src/main/AndroidManifest.xml");

        ensureDir(projectRoot);
        ensureDir(new File(projectRoot, "gradle/wrapper"));
        ensureDir(appDir);
        ensureDir(new File(appDir, "src/main"));
        ensureDir(javaDir);
        ensureDir(resLayoutDir);
        ensureDir(resValuesDir);
        ensureDir(resDrawableDir);

        writeText(new File(projectRoot, "settings.gradle"), buildSettingsGradle(config));
        writeText(new File(projectRoot, "build.gradle"), buildRootGradle());
        writeText(new File(projectRoot, "gradle.properties"), buildGradleProperties());
        writeText(new File(appDir, "build.gradle"), buildAppGradle(config));
        writeText(manifestFile, buildManifest(config));
        writeText(new File(javaDir, "MainActivity.java"), buildShellMainActivity(config));
        writeText(new File(resLayoutDir, "activity_main.xml"), buildShellLayout());
        writeText(new File(resValuesDir, "strings.xml"), buildStrings(config));
        writeText(new File(resValuesDir, "colors.xml"), buildColors());
        writeText(new File(resValuesDir, "styles.xml"), buildStyles());
        writeText(new File(appDir, "src/main/res/mipmap-anydpi-v26/ic_launcher.xml"), buildAdaptiveIconXml());
        writeText(new File(appDir, "src/main/res/drawable/ic_launcher_foreground.xml"), buildDefaultLauncherForeground());
        writeText(new File(appDir, "src/main/res/drawable/ic_launcher_background.xml"), buildDefaultLauncherBackground());
        writeText(new File(appDir, "src/main/res/drawable/splash_image.xml"), buildDefaultSplashDrawable());
        writeText(new File(appDir, "proguard-rules.pro"), "");

        copyWrapperAssets(context, projectRoot);
        applyBrandingAssets(config, projectRoot);
        saveProjectMeta(projectRoot, config.getAppName(), config.getPackageName(), "创建项目", config.getVersionName(), findProjectIcon(projectRoot));
        return projectRoot;
    }

    public void updateProjectConfig(ProjectConfig config, String projectRootPath) throws IOException {
        File projectRoot = new File(projectRootPath);
        File appDir = new File(projectRoot, "app");
        File manifestFile = new File(appDir, "src/main/AndroidManifest.xml");
        File appBuildGradle = new File(appDir, "build.gradle");
        File stringsFile = new File(appDir, "src/main/res/values/strings.xml");

        ensureDir(projectRoot);
        ensureDir(appDir);
        ensureDir(new File(appDir, "src/main/res/values"));

        writeText(new File(projectRoot, "settings.gradle"), buildSettingsGradle(config));
        if (!new File(projectRoot, "build.gradle").exists()) {
            writeText(new File(projectRoot, "build.gradle"), buildRootGradle());
        }
        if (!new File(projectRoot, "gradle.properties").exists()) {
            writeText(new File(projectRoot, "gradle.properties"), buildGradleProperties());
        }
        if (!appBuildGradle.exists()) {
            writeText(appBuildGradle, buildAppGradle(config));
        } else {
            writeText(appBuildGradle, updateAppBuildGradle(readText(appBuildGradle), config));
        }

        if (!manifestFile.exists()) {
            writeText(manifestFile, buildManifest(config));
        } else {
            writeText(manifestFile, updateManifest(readText(manifestFile), config));
        }

        if (!stringsFile.exists()) {
            writeText(stringsFile, buildStrings(config));
        } else {
            writeText(stringsFile, updateStrings(readText(stringsFile), config));
        }

        updateExistingMainActivityPackage(projectRoot, config);
        applyBrandingAssets(config, projectRoot);
        saveProjectMeta(projectRoot, config.getAppName(), config.getPackageName(), "创建项目", config.getVersionName(), findProjectIcon(projectRoot));
    }

    public ArrayList<ProjectEntry> scanProjects(String... rootPaths) {
        ArrayList<ProjectEntry> entries = new ArrayList<ProjectEntry>();
        for (int i = 0; i < rootPaths.length; i++) {
            File root = new File(rootPaths[i]);
            if (!root.exists() || !root.isDirectory()) {
                continue;
            }
            File[] children = root.listFiles();
            if (children == null) {
                continue;
            }
            for (int j = 0; j < children.length; j++) {
                File child = children[j];
                if (!child.isDirectory()) {
                    continue;
                }
                if (looksLikeAndroidProject(child)) {
                    entries.add(readProjectEntry(child));
                }
            }
        }
        return entries;
    }

    public boolean isZipFile(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".zip");
    }

    public File extractZipToTemp(File zipFile, File tempDir) throws IOException {
        return extractZipToTemp(zipFile, tempDir, null);
    }

    public File extractZipToTemp(File zipFile, File tempDir, ExtractProgressListener listener) throws IOException {
        clearDirectory(tempDir);
        ensureDir(tempDir);
        ZipFile zip = null;
        try {
            zip = new ZipFile(zipFile);
            long totalBytes = calculateZipTotalBytes(zip);
            long copiedBytes = 0L;
            int processedEntries = 0;
            int totalEntries = Math.max(1, zip.size());
            if (listener != null) {
                listener.onProgress("正在分析压缩包结构...", 2);
            }
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                File outputFile = new File(tempDir, entry.getName());
                String targetPath = tempDir.getCanonicalPath();
                String outputPath = outputFile.getCanonicalPath();
                if (!outputPath.startsWith(targetPath + File.separator) && !outputPath.equals(targetPath)) {
                    throw new IOException("压缩包包含非法路径: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    ensureDir(outputFile);
                } else {
                    ensureDir(outputFile.getParentFile());
                    FileOutputStream outputStream = null;
                    InputStream entryInputStream = null;
                    try {
                        outputStream = new FileOutputStream(outputFile);
                        entryInputStream = new BufferedInputStream(zip.getInputStream(entry));
                        copiedBytes += copyStreamWithCount(entryInputStream, outputStream);
                    } finally {
                        if (entryInputStream != null) {
                            entryInputStream.close();
                        }
                        if (outputStream != null) {
                            outputStream.close();
                        }
                    }
                }
                processedEntries++;
                if (listener != null) {
                    int percent = computeZipProgress(totalBytes, copiedBytes, processedEntries, totalEntries);
                    listener.onProgress("正在解压：" + simplifyZipEntryName(entry.getName()), percent);
                }
            }
            if (listener != null) {
                listener.onProgress("压缩包解压完成", 100);
            }
            return tempDir;
        } finally {
            if (zip != null) {
                zip.close();
            }
        }
    }

    public File deepFindAndroidProject(File candidate) {
        if (candidate == null || !candidate.exists() || !candidate.isDirectory()) {
            return null;
        }
        ArrayList<File> candidates = new ArrayList<File>();
        collectAndroidProjectCandidates(candidate, candidates);
        if (candidates.isEmpty()) {
            return null;
        }
        File best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < candidates.size(); i++) {
            File projectRoot = candidates.get(i);
            int score = scoreAndroidProjectRoot(candidate, projectRoot);
            if (best == null || score > bestScore) {
                best = projectRoot;
                bestScore = score;
            }
        }
        return best;
    }

    public boolean looksLikeAndroidProject(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return false;
        }
        boolean hasManifest = new File(dir, "app/src/main/AndroidManifest.xml").exists();
        boolean hasAppGradle = new File(dir, "app/build.gradle").exists() || new File(dir, "app/build.gradle.kts").exists();
        boolean hasSettings = new File(dir, "settings.gradle").exists() || new File(dir, "settings.gradle.kts").exists();
        boolean hasRootGradle = new File(dir, "build.gradle").exists() || new File(dir, "build.gradle.kts").exists();
        return hasManifest && hasAppGradle && (hasSettings || hasRootGradle);
    }

    public File importProject(File detectedProjectDir, String projectName, String destinationRootDir) throws IOException {
        return importProject(detectedProjectDir, projectName, destinationRootDir, null);
    }

    public File importProject(
        File detectedProjectDir,
        String projectName,
        String destinationRootDir,
        CopyProgressListener listener
    ) throws IOException {
        if (!looksLikeAndroidProject(detectedProjectDir)) {
            throw new IOException("目录结构不是有效的 Android 工程。");
        }
        File targetDir = resolveImportTargetDir(new File(destinationRootDir), projectName);
        copyDirectory(detectedProjectDir, targetDir, listener);
        ProjectEntry entry = readProjectEntry(targetDir);
        saveProjectMeta(
            targetDir,
            safeText(entry.getProjectName(), projectName),
            safeText(entry.getPackageName(), ""),
            "导入项目",
            safeText(entry.getVersionName(), "1.0"),
            findProjectIcon(targetDir)
        );
        return targetDir;
    }

    public ProjectEntry readProjectEntry(File projectRoot) {
        Properties meta = loadProjectMeta(projectRoot);
        String detectedPackageName = readPackageName(projectRoot);
        String projectName = firstNonEmpty(
            meta.getProperty("projectName"),
            readAppName(projectRoot),
            projectRoot.getName()
        );
        String packageName = firstNonEmpty(detectedPackageName, meta.getProperty("packageName"), "");
        String versionName = firstNonEmpty(meta.getProperty("versionName"), readVersionName(projectRoot), "1.0");
        String iconPath = firstNonEmpty(meta.getProperty("iconPath"), findProjectIcon(projectRoot), "");
        String mode = firstNonEmpty(meta.getProperty("mode"), "项目");
        return new ProjectEntry(projectName, packageName, projectRoot.getAbsolutePath(), iconPath, mode, versionName);
    }

    public String readAppName(File projectRoot) {
        try {
            File stringsFile = new File(projectRoot, "app/src/main/res/values/strings.xml");
            if (!stringsFile.exists()) {
                return "";
            }
            Matcher matcher = java.util.regex.Pattern.compile("<string\\s+name=\"app_name\">(.*?)</string>").matcher(readText(stringsFile));
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        } catch (Exception e) {
        }
        return "";
    }

    public String readPackageName(File projectRoot) {
        ArrayList<String> candidates = readPackageCandidates(projectRoot);
        return candidates.isEmpty() ? "" : candidates.get(0);
    }

    public ArrayList<String> readPackageCandidates(File projectRoot) {
        ArrayList<String> candidates = new ArrayList<String>();
        try {
            File appGradle = new File(projectRoot, "app/build.gradle");
            if (!appGradle.exists()) {
                appGradle = new File(projectRoot, "app/build.gradle.kts");
            }
            if (appGradle.exists()) {
                String gradleContent = readText(appGradle);
                addUniqueNonEmpty(candidates, extractFirst(gradleContent, "namespace\\s*(?:=\\s*)?[\"']([^\"']+)[\"']"));
                addUniqueNonEmpty(candidates, extractFirst(gradleContent, "applicationId\\s*(?:=\\s*)?[\"']([^\"']+)[\"']"));
            }
        } catch (Exception e) {
        }

        try {
            File manifestFile = new File(projectRoot, "app/src/main/AndroidManifest.xml");
            if (manifestFile.exists()) {
                String manifestContent = readText(manifestFile);
                addUniqueNonEmpty(candidates, extractFirst(manifestContent, "package=\"([^\"]+)\""));
            }
        } catch (Exception e) {
        }
        return candidates;
    }

    public String readVersionName(File projectRoot) {
        try {
            File appGradle = new File(projectRoot, "app/build.gradle");
            if (!appGradle.exists()) {
                appGradle = new File(projectRoot, "app/build.gradle.kts");
            }
            if (!appGradle.exists()) {
                return "";
            }
            Matcher matcher = java.util.regex.Pattern.compile("versionName\\s*(?:=\\s*)?\"([^\"]+)\"").matcher(readText(appGradle));
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        } catch (Exception e) {
        }
        return "";
    }

    public String findProjectIcon(File projectRoot) {
        String[] candidates = {
            "app/src/main/res/mipmap-xxxhdpi/ic_launcher.png",
            "app/src/main/res/mipmap-xxhdpi/ic_launcher.png",
            "app/src/main/res/mipmap-xhdpi/ic_launcher.png",
            "app/src/main/res/mipmap-hdpi/ic_launcher.png",
            "app/src/main/res/mipmap-mdpi/ic_launcher.png",
            "app/src/main/res/drawable/ic_launcher.png",
            "app/src/main/res/drawable/ic_launcher.webp",
            "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml"
        };
        for (int i = 0; i < candidates.length; i++) {
            File file = new File(projectRoot, candidates[i]);
            if (file.exists()) {
                return file.getAbsolutePath();
            }
        }
        return "";
    }

    public String readText(File file) throws IOException {
        InputStream inputStream = null;
        try {
            inputStream = new FileInputStream(file);
            byte[] buffer = new byte[4096];
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, len);
            }
            return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }
    }

    public void writeText(File file, String content) throws IOException {
        ensureDir(file.getParentFile());
        FileOutputStream outputStream = null;
        try {
            outputStream = new FileOutputStream(file);
            outputStream.write(content.getBytes(StandardCharsets.UTF_8));
        } finally {
            if (outputStream != null) {
                outputStream.close();
            }
        }
    }

    public void ensureDir(File dir) throws IOException {
        if (dir == null || dir.exists()) {
            return;
        }
        if (!dir.mkdirs()) {
            throw new IOException("无法创建目录: " + dir.getAbsolutePath());
        }
    }

    public void clearDirectory(File file) throws IOException {
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
        if (!file.delete()) {
            throw new IOException("无法删除: " + file.getAbsolutePath());
        }
    }

    public void copyDirectory(File source, File target) throws IOException {
        copyDirectory(source, target, null);
    }

    public void copyDirectory(File source, File target, CopyProgressListener listener) throws IOException {
        long totalBytes = listener == null ? 0L : Math.max(1L, calculateDirectoryTotalBytes(source));
        long[] copiedBytes = {0L};
        copyDirectoryInternal(source, target, listener, totalBytes, copiedBytes);
        if (listener != null) {
            listener.onProgress("项目文件复制完成", 100);
        }
    }

    private void copyDirectoryInternal(
        File source,
        File target,
        CopyProgressListener listener,
        long totalBytes,
        long[] copiedBytes
    ) throws IOException {
        if (source.isDirectory()) {
            ensureDir(target);
            File[] children = source.listFiles();
            if (children == null) {
                return;
            }
            for (int i = 0; i < children.length; i++) {
                File child = children[i];
                copyDirectoryInternal(child, new File(target, child.getName()), listener, totalBytes, copiedBytes);
            }
        } else {
            ensureDir(target.getParentFile());
            InputStream inputStream = null;
            FileOutputStream outputStream = null;
            try {
                inputStream = new FileInputStream(source);
                outputStream = new FileOutputStream(target);
                copyStream(inputStream, outputStream);
            } finally {
                if (inputStream != null) {
                    inputStream.close();
                }
                if (outputStream != null) {
                    outputStream.close();
                }
            }
            if (listener != null) {
                copiedBytes[0] += Math.max(0L, source.length());
                int percent = (int) Math.min(100L, (copiedBytes[0] * 100L) / totalBytes);
                listener.onProgress("正在导入：" + source.getName(), Math.max(4, percent));
            }
        }
    }

    private void updateExistingMainActivityPackage(File projectRoot, ProjectConfig config) throws IOException {
        File javaRoot = new File(projectRoot, "app/src/main/java");
        if (!javaRoot.exists()) {
            return;
        }
        File existingMainActivity = findFileByName(javaRoot, "MainActivity.java");
        if (existingMainActivity == null) {
            return;
        }
        String javaContent = readText(existingMainActivity);
        String updatedContent;
        if (javaContent.contains("package ")) {
            updatedContent = javaContent.replaceFirst(
                "package\\s+[A-Za-z0-9_\\.]+;",
                "package " + Matcher.quoteReplacement(config.getPackageName()) + ";"
            );
        } else {
            updatedContent = "package " + config.getPackageName() + ";\n\n" + javaContent;
        }
        writeText(existingMainActivity, updatedContent);
    }

    private void applyBrandingAssets(ProjectConfig config, File projectRoot) throws IOException {
        copyIconIfPresent(config.getIconSourcePath(), projectRoot);
        copySplashIfPresent(config.getSplashSourcePath(), projectRoot);
    }

    private void copyIconIfPresent(String sourcePath, File projectRoot) throws IOException {
        if (sourcePath == null || sourcePath.trim().length() == 0) {
            return;
        }
        File sourceFile = new File(sourcePath);
        if (!sourceFile.exists() || sourceFile.isDirectory()) {
            return;
        }
        String lower = sourceFile.getName().toLowerCase();
        if (!(lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp"))) {
            return;
        }
        File placeholderXml = new File(projectRoot, "app/src/main/res/drawable/ic_launcher_foreground.xml");
        if (placeholderXml.exists()) {
            placeholderXml.delete();
        }
        File backgroundXml = new File(projectRoot, "app/src/main/res/drawable/ic_launcher_background.xml");
        if (!backgroundXml.exists()) {
            writeText(backgroundXml, buildDefaultLauncherBackground());
        }
        String extension = lower.endsWith(".webp") ? ".webp" : ".png";
        File foregroundFile = new File(projectRoot, "app/src/main/res/drawable/ic_launcher_foreground" + extension);
        copyDirectory(sourceFile, foregroundFile);
        String[] folders = {"mipmap-mdpi", "mipmap-hdpi", "mipmap-xhdpi", "mipmap-xxhdpi", "mipmap-xxxhdpi"};
        for (int i = 0; i < folders.length; i++) {
            File targetFile = new File(projectRoot, "app/src/main/res/" + folders[i] + "/ic_launcher" + extension);
            copyDirectory(sourceFile, targetFile);
        }
    }

    private void copySplashIfPresent(String sourcePath, File projectRoot) throws IOException {
        if (sourcePath == null || sourcePath.trim().length() == 0) {
            return;
        }
        File sourceFile = new File(sourcePath);
        if (!sourceFile.exists() || sourceFile.isDirectory()) {
            return;
        }
        String lower = sourceFile.getName().toLowerCase();
        if (!(lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp"))) {
            return;
        }
        File placeholderXml = new File(projectRoot, "app/src/main/res/drawable/splash_image.xml");
        if (placeholderXml.exists()) {
            placeholderXml.delete();
        }
        String extension = lower.endsWith(".webp") ? ".webp" : ".png";
        File targetFile = new File(projectRoot, "app/src/main/res/drawable/splash_image" + extension);
        copyDirectory(sourceFile, targetFile);
    }

    private void saveProjectMeta(
        File projectRoot,
        String projectName,
        String packageName,
        String mode,
        String versionName,
        String iconPath
    ) throws IOException {
        File metaDir = new File(projectRoot, META_DIR);
        ensureDir(metaDir);
        Properties properties = new Properties();
        properties.setProperty("projectName", safeText(projectName, projectRoot.getName()));
        properties.setProperty("packageName", safeText(packageName, ""));
        properties.setProperty("mode", safeText(mode, "项目"));
        properties.setProperty("versionName", safeText(versionName, "1.0"));
        properties.setProperty("iconPath", safeText(iconPath, ""));
        FileOutputStream outputStream = null;
        try {
            outputStream = new FileOutputStream(new File(metaDir, META_FILE));
            properties.store(outputStream, "LM project metadata");
        } finally {
            if (outputStream != null) {
                outputStream.close();
            }
        }
    }

    private Properties loadProjectMeta(File projectRoot) {
        Properties properties = new Properties();
        File metaFile = new File(new File(projectRoot, META_DIR), META_FILE);
        if (!metaFile.exists()) {
            return properties;
        }
        InputStream inputStream = null;
        try {
            inputStream = new FileInputStream(metaFile);
            properties.load(inputStream);
        } catch (Exception e) {
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (Exception e) {
            }
        }
        return properties;
    }

    private String updateAppBuildGradle(String content, ProjectConfig config) {
        String updated = content;
        updated = updated.replaceAll("namespace\\s+'[^']+'", "namespace '" + Matcher.quoteReplacement(config.getPackageName()) + "'");
        updated = updated.replaceAll("applicationId\\s+\"[^\"]+\"", "applicationId \"" + Matcher.quoteReplacement(config.getPackageName()) + "\"");
        updated = updated.replaceAll("minSdkVersion\\s+\\d+", "minSdkVersion " + config.getMinSdk());
        updated = updated.replaceAll("targetSdkVersion\\s+\\d+", "targetSdkVersion " + config.getTargetSdk());
        updated = updated.replaceAll("versionCode\\s+\\d+", "versionCode " + config.getVersionCode());
        updated = updated.replaceAll("versionName\\s+\"[^\"]+\"", "versionName \"" + Matcher.quoteReplacement(config.getVersionName()) + "\"");
        if (!updated.contains("namespace")) {
            return buildAppGradle(config);
        }
        return updated;
    }

    private String updateManifest(String content, ProjectConfig config) {
        String updated = content;
        if (updated.contains("package=\"")) {
            updated = updated.replaceFirst("package=\"[^\"]+\"", "package=\"" + Matcher.quoteReplacement(config.getPackageName()) + "\"");
        }
        if (!updated.contains("android:label=\"@string/app_name\"")) {
            updated = updated.replaceFirst("<application", "<application\n        android:label=\"@string/app_name\"");
        }
        if (!updated.contains("android:icon=\"@mipmap/ic_launcher\"")) {
            updated = updated.replaceFirst("<application", "<application\n        android:icon=\"@mipmap/ic_launcher\"");
        }
        if (!updated.contains("android:roundIcon=\"@mipmap/ic_launcher\"")) {
            updated = updated.replaceFirst("<application", "<application\n        android:roundIcon=\"@mipmap/ic_launcher\"");
        }
        return updated;
    }

    private String updateStrings(String content, ProjectConfig config) {
        String result = content;
        if (result.contains("name=\"app_name\"")) {
            result = result.replaceFirst(
                "<string\\s+name=\"app_name\">.*?</string>",
                "<string name=\"app_name\">" + escapeXml(config.getAppName()) + "</string>"
            );
        } else {
            result = result.replace("</resources>", "    <string name=\"app_name\">" + escapeXml(config.getAppName()) + "</string>\n</resources>");
        }
        if (!result.contains("name=\"welcome_message\"")) {
            result = result.replace(
                "</resources>",
                "    <string name=\"welcome_message\">欢迎使用 " + escapeXml(config.getAppName()) + "</string>\n</resources>"
            );
        }
        return result;
    }

    private String buildSettingsGradle(ProjectConfig config) {
        return "rootProject.name = \"" + escapeGradle(config.getAppName()) + "\"\ninclude ':app'\n";
    }

    private String buildRootGradle() {
        return "buildscript {\n"
            + "    repositories {\n"
            + "        google()\n"
            + "        mavenCentral()\n"
            + "    }\n"
            + "    dependencies {\n"
            + "        classpath 'com.android.tools.build:gradle:8.5.2'\n"
            + "    }\n"
            + "}\n\n"
            + "allprojects {\n"
            + "    repositories {\n"
            + "        google()\n"
            + "        mavenCentral()\n"
            + "    }\n"
            + "}\n\n"
            + "task clean(type: Delete) {\n"
            + "    delete rootProject.buildDir\n"
            + "}\n";
    }

    private String buildGradleProperties() {
        return "org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8\n"
            + "android.useAndroidX=true\n"
            + "android.enableJetifier=true\n"
            + "android.nonTransitiveRClass=false\n"
            + "android.nonFinalResIds=false\n";
    }

    private String buildAppGradle(ProjectConfig config) {
        return "apply plugin: 'com.android.application'\n\n"
            + "android {\n"
            + "    namespace '" + escapeGradle(config.getPackageName()) + "'\n"
            + "    compileSdkVersion 36\n"
            + "    ndkVersion \"27.2.12479018\"\n\n"
            + "    defaultConfig {\n"
            + "        applicationId \"" + escapeGradle(config.getPackageName()) + "\"\n"
            + "        minSdkVersion " + config.getMinSdk() + "\n"
            + "        targetSdkVersion " + config.getTargetSdk() + "\n"
            + "        versionCode " + config.getVersionCode() + "\n"
            + "        versionName \"" + escapeGradle(config.getVersionName()) + "\"\n"
            + "        ndk {\n"
            + "            abiFilters 'arm64-v8a'\n"
            + "        }\n"
            + "    }\n\n"
            + "    compileOptions {\n"
            + "        sourceCompatibility JavaVersion.VERSION_21\n"
            + "        targetCompatibility JavaVersion.VERSION_21\n"
            + "    }\n\n"
            + "    buildFeatures {\n"
            + "        buildConfig true\n"
            + "    }\n\n"
            + "    buildTypes {\n"
            + "        release {\n"
            + "            minifyEnabled false\n"
            + "            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'\n"
            + "        }\n"
            + "        debug {\n"
            + "            minifyEnabled false\n"
            + "        }\n"
            + "    }\n\n"
            + "    lintOptions {\n"
            + "        abortOnError false\n"
            + "    }\n"
            + "}\n\n"
            + "dependencies {\n"
            + "}\n";
    }

    private String buildManifest(ProjectConfig config) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
            + "    package=\"" + escapeXml(config.getPackageName()) + "\">\n\n"
            + "    <uses-permission android:name=\"android.permission.INTERNET\" />\n\n"
            + "    <application\n"
            + "        android:allowBackup=\"true\"\n"
            + "        android:icon=\"@mipmap/ic_launcher\"\n"
            + "        android:roundIcon=\"@mipmap/ic_launcher\"\n"
            + "        android:label=\"@string/app_name\"\n"
            + "        android:supportsRtl=\"true\"\n"
            + "        android:theme=\"@style/AppTheme\">\n\n"
            + "        <activity\n"
            + "            android:name=\".MainActivity\"\n"
            + "            android:exported=\"true\">\n"
            + "            <intent-filter>\n"
            + "                <action android:name=\"android.intent.action.MAIN\" />\n"
            + "                <category android:name=\"android.intent.category.LAUNCHER\" />\n"
            + "            </intent-filter>\n"
            + "        </activity>\n\n"
            + "    </application>\n\n"
            + "</manifest>\n";
    }

    private String buildShellMainActivity(ProjectConfig config) {
        return "package " + config.getPackageName() + ";\n\n"
            + "import android.app.Activity;\n"
            + "import android.os.Bundle;\n"
            + "import android.widget.TextView;\n\n"
            + "public class MainActivity extends Activity {\n\n"
            + "    @Override\n"
            + "    protected void onCreate(Bundle savedInstanceState) {\n"
            + "        super.onCreate(savedInstanceState);\n"
            + "        setContentView(R.layout.activity_main);\n"
            + "        TextView textView = (TextView) findViewById(R.id.tvHello);\n"
            + "        textView.setText(\"欢迎使用 " + escapeJava(config.getAppName()) + "\");\n"
            + "    }\n"
            + "}\n";
    }

    private String buildShellLayout() {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
            + "    android:layout_width=\"match_parent\"\n"
            + "    android:layout_height=\"match_parent\"\n"
            + "    android:background=\"#10131B\"\n"
            + "    android:gravity=\"center\"\n"
            + "    android:orientation=\"vertical\"\n"
            + "    android:padding=\"24dp\">\n\n"
            + "    <ImageView\n"
            + "        android:layout_width=\"120dp\"\n"
            + "        android:layout_height=\"120dp\"\n"
            + "        android:layout_marginBottom=\"20dp\"\n"
            + "        android:scaleType=\"centerCrop\"\n"
            + "        android:src=\"@drawable/splash_image\" />\n\n"
            + "    <TextView\n"
            + "        android:id=\"@+id/tvHello\"\n"
            + "        android:layout_width=\"wrap_content\"\n"
            + "        android:layout_height=\"wrap_content\"\n"
            + "        android:text=\"@string/welcome_message\"\n"
            + "        android:textColor=\"#FFFFFF\"\n"
            + "        android:textSize=\"24sp\"\n"
            + "        android:textStyle=\"bold\" />\n\n"
            + "</LinearLayout>\n";
    }

    private String buildStrings(ProjectConfig config) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<resources>\n"
            + "    <string name=\"app_name\">" + escapeXml(config.getAppName()) + "</string>\n"
            + "    <string name=\"welcome_message\">欢迎使用 " + escapeXml(config.getAppName()) + "</string>\n"
            + "</resources>\n";
    }

    private String buildColors() {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<resources>\n"
            + "    <color name=\"background_dark\">#10131B</color>\n"
            + "    <color name=\"panel_dark\">#171C26</color>\n"
            + "    <color name=\"launcher_background\">#5B8CFF</color>\n"
            + "    <color name=\"text_primary\">#FFFFFF</color>\n"
            + "</resources>\n";
    }

    private String buildStyles() {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<resources>\n"
            + "    <style name=\"AppTheme\" parent=\"@android:style/Theme.DeviceDefault.NoActionBar\">\n"
            + "        <item name=\"android:windowBackground\">@color/background_dark</item>\n"
            + "    </style>\n"
            + "</resources>\n";
    }

    private String buildAdaptiveIconXml() {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<adaptive-icon xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
            + "    <background android:drawable=\"@drawable/ic_launcher_background\" />\n"
            + "    <foreground android:drawable=\"@drawable/ic_launcher_foreground\" />\n"
            + "</adaptive-icon>\n";
    }

    private String buildDefaultLauncherForeground() {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<shape xmlns:android=\"http://schemas.android.com/apk/res/android\" android:shape=\"oval\">\n"
            + "    <size android:width=\"108dp\" android:height=\"108dp\" />\n"
            + "    <solid android:color=\"#FFFFFF\" />\n"
            + "</shape>\n";
    }

    private String buildDefaultLauncherBackground() {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<shape xmlns:android=\"http://schemas.android.com/apk/res/android\" android:shape=\"rectangle\">\n"
            + "    <solid android:color=\"@color/launcher_background\" />\n"
            + "</shape>\n";
    }

    private String buildDefaultSplashDrawable() {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<shape xmlns:android=\"http://schemas.android.com/apk/res/android\" android:shape=\"rectangle\">\n"
            + "    <corners android:radius=\"28dp\" />\n"
            + "    <gradient android:angle=\"45\" android:startColor=\"#5B8CFF\" android:endColor=\"#7E57C2\" />\n"
            + "</shape>\n";
    }

    private void copyWrapperAssets(Context context, File projectRoot) throws IOException {
        copyAsset(context.getAssets(), "project_template/gradlew", new File(projectRoot, "gradlew"));
        copyAsset(context.getAssets(), "project_template/gradlew.bat", new File(projectRoot, "gradlew.bat"));
        copyAsset(context.getAssets(), "project_template/gradle/wrapper/gradle-wrapper.jar", new File(projectRoot, "gradle/wrapper/gradle-wrapper.jar"));
        copyAsset(context.getAssets(), "project_template/gradle/wrapper/gradle-wrapper.properties", new File(projectRoot, "gradle/wrapper/gradle-wrapper.properties"));
        new File(projectRoot, "gradlew").setExecutable(true);
    }

    private void copyAsset(AssetManager assetManager, String assetPath, File targetFile) throws IOException {
        ensureDir(targetFile.getParentFile());
        InputStream inputStream = null;
        FileOutputStream outputStream = null;
        try {
            inputStream = assetManager.open(assetPath);
            outputStream = new FileOutputStream(targetFile);
            copyStream(inputStream, outputStream);
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
            if (outputStream != null) {
                outputStream.close();
            }
        }
    }

    private void copyStream(InputStream inputStream, FileOutputStream outputStream) throws IOException {
        byte[] buffer = new byte[8192];
        int len;
        while ((len = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, len);
        }
        outputStream.flush();
    }

    private long copyStreamWithCount(InputStream inputStream, FileOutputStream outputStream) throws IOException {
        byte[] buffer = new byte[8192];
        int len;
        long copied = 0L;
        while ((len = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, len);
            copied += len;
        }
        outputStream.flush();
        return copied;
    }

    private long calculateZipTotalBytes(ZipFile zipFile) {
        long total = 0L;
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            long size = entry.getSize();
            if (!entry.isDirectory() && size > 0L) {
                total += size;
            }
        }
        return total;
    }

    private int computeZipProgress(long totalBytes, long copiedBytes, int processedEntries, int totalEntries) {
        if (totalBytes > 0L) {
            return Math.max(2, Math.min(100, (int) ((copiedBytes * 100L) / totalBytes)));
        }
        return Math.max(2, Math.min(100, (processedEntries * 100) / Math.max(1, totalEntries)));
    }

    private long calculateDirectoryTotalBytes(File file) {
        if (file == null || !file.exists()) {
            return 0L;
        }
        if (file.isFile()) {
            return Math.max(0L, file.length());
        }
        long total = 0L;
        File[] children = file.listFiles();
        if (children == null) {
            return 0L;
        }
        for (int i = 0; i < children.length; i++) {
            total += calculateDirectoryTotalBytes(children[i]);
        }
        return total;
    }

    private String simplifyZipEntryName(String entryName) {
        if (entryName == null || entryName.length() == 0) {
            return "文件";
        }
        String normalized = entryName.replace('\\', '/');
        int lastSlash = normalized.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < normalized.length() - 1) {
            return normalized.substring(lastSlash + 1);
        }
        return normalized;
    }

    private File findFileByName(File dir, String fileName) {
        File[] files = dir.listFiles();
        if (files == null) {
            return null;
        }
        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            if (file.isDirectory()) {
                File match = findFileByName(file, fileName);
                if (match != null) {
                    return match;
                }
            } else if (fileName.equals(file.getName())) {
                return file;
            }
        }
        return null;
    }

    private void collectAndroidProjectCandidates(File dir, ArrayList<File> candidates) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return;
        }
        if (looksLikeAndroidProject(dir)) {
            candidates.add(dir);
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (int i = 0; i < files.length; i++) {
            File child = files[i];
            if (child.isDirectory()) {
                collectAndroidProjectCandidates(child, candidates);
            }
        }
    }

    private int scoreAndroidProjectRoot(File searchRoot, File projectRoot) {
        int score = 0;
        if (new File(projectRoot, "gradlew").exists()) {
            score += 40;
        }
        if (new File(projectRoot, "settings.gradle").exists() || new File(projectRoot, "settings.gradle.kts").exists()) {
            score += 30;
        }
        if (new File(projectRoot, "build.gradle").exists() || new File(projectRoot, "build.gradle.kts").exists()) {
            score += 18;
        }
        if (new File(projectRoot, "gradle/wrapper/gradle-wrapper.properties").exists()) {
            score += 16;
        }
        if (new File(projectRoot, "app/src/main/java").exists() || new File(projectRoot, "app/src/main/kotlin").exists()) {
            score += 10;
        }
        if (new File(projectRoot, "app/src/main/res").exists()) {
            score += 10;
        }
        if (new File(projectRoot, "local.properties").exists()) {
            score += 5;
        }
        score -= relativeDepth(searchRoot, projectRoot);
        score += projectRoot.getName().toLowerCase().contains("app") ? 2 : 0;
        return score;
    }

    private int relativeDepth(File searchRoot, File projectRoot) {
        try {
            String rootPath = searchRoot.getCanonicalPath();
            String projectPath = projectRoot.getCanonicalPath();
            if (!projectPath.startsWith(rootPath)) {
                return 0;
            }
            String relative = projectPath.substring(rootPath.length());
            int depth = 0;
            for (int i = 0; i < relative.length(); i++) {
                if (relative.charAt(i) == File.separatorChar) {
                    depth++;
                }
            }
            return depth;
        } catch (IOException e) {
            return 0;
        }
    }

    private File resolveImportTargetDir(File destinationRootDir, String projectName) throws IOException {
        ensureDir(destinationRootDir);
        String safeProjectName = sanitizeName(projectName);
        if (safeProjectName.length() == 0) {
            safeProjectName = "imported_project";
        }
        File preferred = new File(destinationRootDir, safeProjectName);
        if (!preferred.exists()) {
            return preferred;
        }
        if (preferred.isDirectory() && isDirectoryEmpty(preferred)) {
            return preferred;
        }
        int suffix = 2;
        while (true) {
            File candidate = new File(destinationRootDir, safeProjectName + "-" + suffix);
            if (!candidate.exists()) {
                return candidate;
            }
            suffix++;
        }
    }

    private boolean isDirectoryEmpty(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return false;
        }
        File[] files = dir.listFiles();
        return files == null || files.length == 0;
    }

    private void addUniqueNonEmpty(ArrayList<String> values, String candidate) {
        if (candidate == null) {
            return;
        }
        String value = candidate.trim();
        if (value.length() == 0 || values.contains(value)) {
            return;
        }
        values.add(value);
    }

    private String extractFirst(String content, String regex) {
        if (content == null || content.length() == 0) {
            return "";
        }
        Matcher matcher = java.util.regex.Pattern.compile(regex).matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "";
    }

    private String sanitizeName(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    private String firstNonEmpty(String a, String b, String c) {
        if (a != null && a.length() > 0) {
            return a;
        }
        if (b != null && b.length() > 0) {
            return b;
        }
        return c == null ? "" : c;
    }

    private String safeText(String value, String fallback) {
        return value == null || value.length() == 0 ? fallback : value;
    }

    private String escapeJava(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String escapeXml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String escapeGradle(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
