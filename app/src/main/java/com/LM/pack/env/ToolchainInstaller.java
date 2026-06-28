package com.LM.pack.env;

import android.content.Context;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Enumeration;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

public class ToolchainInstaller {

    public interface InstallListener {
        void onProgress(String message, int percent, boolean indeterminate);
        void onSuccess(String installedDir);
        void onError(String message);
    }

    private final Context context;
    private final EnvironmentManager environmentManager;

    public ToolchainInstaller(Context context, EnvironmentManager environmentManager) {
        this.context = context.getApplicationContext();
        this.environmentManager = environmentManager;
    }

    public void installJdk(final int index, final InstallListener listener) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String archivePath = environmentManager.getJdkPackageArchivePath(index);
                    File archiveFile = new File(archivePath);
                    ensureDir(archiveFile.getParentFile());

                    prepareArchive(
                        environmentManager.getJdkDownloadCandidates(index),
                        archiveFile,
                        "JDK 安装包",
                        remapListener(listener, 0, 56)
                    );

                    File installRoot = new File(environmentManager.getJdkInstallDir(EnvironmentManager.JDK_NAMES[index]));
                    clearDirectory(installRoot);
                    ensureDir(installRoot);
                    if (listener != null) {
                        listener.onProgress("正在整理 JDK 安装目录...", 58, false);
                    }
                    extractTarGz(archiveFile, installRoot, remapListener(listener, 56, 44), "正在解压 JDK");
                    File actualDir = resolveInstalledHome(installRoot);
                    if (listener != null) {
                        listener.onProgress("JDK 已准备完成", 100, false);
                    }
                    if (listener != null) {
                        listener.onSuccess(actualDir.getAbsolutePath());
                    }
                } catch (Exception e) {
                    if (listener != null) {
                        listener.onError("JDK 安装失败：" + e.getMessage());
                    }
                }
            }
        }).start();
    }

    public void installNdk(final int index, final InstallListener listener) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String archivePath = environmentManager.getNdkPackageArchivePath(index);
                    File archiveFile = new File(archivePath);
                    ensureDir(archiveFile.getParentFile());

                    prepareArchive(
                        environmentManager.getNdkDownloadCandidates(index),
                        archiveFile,
                        "NDK 安装包",
                        remapListener(listener, 0, 56)
                    );

                    File installRoot = new File(environmentManager.getNdkInstallDir(EnvironmentManager.NDK_NAMES[index]));
                    clearDirectory(installRoot);
                    ensureDir(installRoot);
                    if (listener != null) {
                        listener.onProgress("正在整理 NDK 安装目录...", 58, false);
                    }
                    extractZip(archiveFile, installRoot, remapListener(listener, 56, 44), "正在解压 NDK");
                    File actualDir = resolveInstalledHome(installRoot);
                    if (listener != null) {
                        listener.onProgress("NDK 已准备完成", 100, false);
                    }
                    if (listener != null) {
                        listener.onSuccess(actualDir.getAbsolutePath());
                    }
                } catch (Exception e) {
                    if (listener != null) {
                        listener.onError("NDK 安装失败：" + e.getMessage());
                    }
                }
            }
        }).start();
    }

    public void installEmbeddedSdk(final InstallListener listener) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String archivePath = environmentManager.getSdkPackageArchivePath();
                    File archiveFile = new File(archivePath);
                    ensureDir(archiveFile.getParentFile());
                    prepareArchive(
                        environmentManager.getSdkDownloadCandidates(),
                        archiveFile,
                        "Android SDK 命令行工具",
                        remapListener(listener, 0, 54)
                    );

                    File installRoot = new File(environmentManager.getEmbeddedSdkInstallDir());
                    clearDirectory(installRoot);
                    ensureDir(installRoot);
                    if (listener != null) {
                        listener.onProgress("正在整理 Android SDK 目录...", 56, false);
                    }
                    extractZip(archiveFile, installRoot, remapListener(listener, 54, 42), "正在解压 Android SDK");
                    if (listener != null) {
                        listener.onProgress("正在整理命令行工具结构...", 97, false);
                    }
                    normalizeEmbeddedSdkLayout(installRoot);
                    if (listener != null) {
                        listener.onProgress("Android SDK 命令行工具已准备完成", 100, false);
                    }
                    if (listener != null) {
                        listener.onSuccess(installRoot.getAbsolutePath());
                    }
                } catch (Exception e) {
                    if (listener != null) {
                        listener.onError("Android SDK 解压失败：" + e.getMessage());
                    }
                }
            }
        }).start();
    }

    private void prepareArchive(
        String[] candidateUrls,
        File targetFile,
        String displayName,
        InstallListener listener
    ) throws Exception {
        if (targetFile.exists() && targetFile.length() > 0) {
            if (listener != null) {
                listener.onProgress("已找到本地缓存，跳过下载。", 55, false);
            }
            return;
        }
        String resolvedUrl = resolveDownloadUrl(candidateUrls, displayName, listener);
        if (listener != null) {
            listener.onProgress("正在下载" + displayName + "...", 8, false);
        }
        downloadToFile(resolvedUrl, targetFile, listener, "正在下载" + displayName);
    }

    private String resolveDownloadUrl(String[] candidateUrls, String displayName, InstallListener listener) throws Exception {
        java.util.LinkedHashSet<String> candidates = new java.util.LinkedHashSet<String>();
        appendUrls(candidates, candidateUrls);
        if (candidates.isEmpty()) {
            throw new IllegalStateException(displayName + " 没有可用下载地址");
        }
        if (listener != null) {
            listener.onProgress("正在校验 " + displayName + " 下载链路...", 4, false);
        }
        java.util.Iterator<String> iterator = candidates.iterator();
        while (iterator.hasNext()) {
            String candidate = iterator.next();
            if (isUrlReachable(candidate, 0)) {
                return candidate;
            }
        }
        throw new IllegalStateException(displayName + " 所有下载链路都不可用");
    }

    private void appendUrls(java.util.LinkedHashSet<String> values, String[] candidates) {
        if (candidates == null) {
            return;
        }
        for (int i = 0; i < candidates.length; i++) {
            if (candidates[i] != null && candidates[i].trim().length() > 0) {
                values.add(candidates[i].trim());
            }
        }
    }

    private boolean isUrlReachable(String urlString, int redirectCount) {
        if (urlString == null || urlString.length() == 0 || redirectCount > 5) {
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
            int responseCode = connection.getResponseCode();
            if (responseCode >= 300 && responseCode < 400) {
                String redirect = connection.getHeaderField("Location");
                return redirect != null && redirect.length() > 0 && isUrlReachable(redirect, redirectCount + 1);
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

    private void downloadToFile(String urlString, File targetFile, InstallListener listener, String progressLabel) throws Exception {
        downloadToFile(urlString, targetFile, listener, progressLabel, 0);
    }

    private void downloadToFile(String urlString, File targetFile, InstallListener listener, String progressLabel, int redirectCount) throws Exception {
        if (redirectCount > 5) {
            throw new IllegalStateException("下载重定向次数过多");
        }
        HttpURLConnection connection = null;
        InputStream inputStream = null;
        BufferedOutputStream outputStream = null;
        File partialFile = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(60000);
            connection.setReadTimeout(60000);
            connection.setRequestProperty("User-Agent", "LM-APK-Builder/2.1");
            connection.connect();
            int responseCode = connection.getResponseCode();
            if (responseCode >= 300 && responseCode < 400) {
                String redirect = connection.getHeaderField("Location");
                if (redirect != null && redirect.length() > 0) {
                    connection.disconnect();
                    downloadToFile(redirect, targetFile, listener, progressLabel, redirectCount + 1);
                    return;
                }
            }
            if (responseCode >= 400) {
                throw new IllegalStateException("下载失败，HTTP " + connection.getResponseCode());
            }
            long totalBytes = connection.getContentLengthLong();
            ensureDir(targetFile == null ? null : targetFile.getParentFile());
            partialFile = new File(targetFile.getAbsolutePath() + ".part");
            deleteQuietly(partialFile);
            inputStream = new BufferedInputStream(connection.getInputStream());
            outputStream = new BufferedOutputStream(new FileOutputStream(partialFile));
            copyStream(inputStream, outputStream, totalBytes, progressLabel, listener);
            outputStream.flush();
            outputStream.close();
            outputStream = null;
            if (targetFile.exists() && !targetFile.delete()) {
                throw new IllegalStateException("无法替换旧文件：" + targetFile.getAbsolutePath());
            }
            if (!partialFile.renameTo(targetFile)) {
                throw new IllegalStateException("无法完成下载文件写入：" + targetFile.getAbsolutePath());
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

    private void extractTarGz(File archiveFile, File targetDir, InstallListener listener, String progressLabel) throws Exception {
        long totalBytes = calculateTarTotalBytes(archiveFile);
        long[] extractedBytes = {0L};
        TarArchiveInputStream tarInputStream = null;
        try {
            tarInputStream = openTarInputStream(archiveFile);
            TarArchiveEntry entry;
            while ((entry = tarInputStream.getNextTarEntry()) != null) {
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
                BufferedOutputStream outputStream = null;
                try {
                    outputStream = new BufferedOutputStream(new FileOutputStream(outputFile));
                    copyArchiveEntryStream(
                        tarInputStream,
                        outputStream,
                        totalBytes,
                        extractedBytes,
                        progressLabel + "  " + simplifyEntryName(entry.getName()),
                        listener
                    );
                } finally {
                    if (outputStream != null) {
                        outputStream.close();
                    }
                }
                outputFile.setExecutable((entry.getMode() & 0100) != 0);
            }
            if (listener != null) {
                listener.onProgress(progressLabel + " 完成", 100, false);
            }
        } finally {
            if (tarInputStream != null) {
                tarInputStream.close();
            }
        }
    }

    private void extractZip(File archiveFile, File targetDir, InstallListener listener, String progressLabel) throws Exception {
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
                        progressLabel + "  " + simplifyEntryName(entry.getName()),
                        listener
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
            if (listener != null) {
                listener.onProgress(progressLabel + " 完成", 100, false);
            }
        } finally {
            if (zipFile != null) {
                zipFile.close();
            }
        }
    }

    private TarArchiveInputStream openTarInputStream(File archiveFile) throws Exception {
        return new TarArchiveInputStream(
            new GZIPInputStream(new BufferedInputStream(new FileInputStream(archiveFile)))
        );
    }

    private long calculateTarTotalBytes(File archiveFile) throws Exception {
        long totalBytes = 0L;
        TarArchiveInputStream tarInputStream = null;
        try {
            tarInputStream = openTarInputStream(archiveFile);
            TarArchiveEntry entry;
            while ((entry = tarInputStream.getNextTarEntry()) != null) {
                if (!entry.isDirectory() && entry.getSize() > 0) {
                    totalBytes += entry.getSize();
                }
            }
        } finally {
            if (tarInputStream != null) {
                tarInputStream.close();
            }
        }
        return totalBytes;
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

    private void normalizeEmbeddedSdkLayout(File installRoot) throws Exception {
        File cmdlineToolsRoot = new File(installRoot, "cmdline-tools");
        if (!cmdlineToolsRoot.exists()) {
            return;
        }
        File latestDir = new File(cmdlineToolsRoot, "latest");
        if (latestDir.exists()) {
            return;
        }
        File[] children = cmdlineToolsRoot.listFiles();
        if (children == null || children.length == 0) {
            return;
        }
        File nestedRoot = null;
        for (int i = 0; i < children.length; i++) {
            if (children[i].isDirectory()) {
                nestedRoot = children[i];
                break;
            }
        }
        if (nestedRoot == null) {
            return;
        }
        if (new File(nestedRoot, "bin").exists() || new File(nestedRoot, "lib").exists()) {
            nestedRoot.renameTo(latestDir);
            return;
        }
        if (new File(cmdlineToolsRoot, "bin").exists() || new File(cmdlineToolsRoot, "lib").exists()) {
            File tempDir = new File(installRoot, "cmdline-tools-temp");
            if (tempDir.exists()) {
                clearDirectory(tempDir);
            }
            ensureDir(tempDir);
            File[] toolFiles = cmdlineToolsRoot.listFiles();
            if (toolFiles == null) {
                return;
            }
            for (int i = 0; i < toolFiles.length; i++) {
                toolFiles[i].renameTo(new File(tempDir, toolFiles[i].getName()));
            }
            clearDirectory(cmdlineToolsRoot);
            ensureDir(cmdlineToolsRoot);
            ensureDir(latestDir);
            File[] staged = tempDir.listFiles();
            if (staged != null) {
                for (int i = 0; i < staged.length; i++) {
                    staged[i].renameTo(new File(latestDir, staged[i].getName()));
                }
            }
            clearDirectory(tempDir);
        }
    }

    private File resolveInstalledHome(File installRoot) {
        File[] children = installRoot.listFiles();
        if (children == null || children.length != 1 || !children[0].isDirectory()) {
            return installRoot;
        }
        return children[0];
    }

    private void clearDirectory(File file) {
        if (!file.exists()) {
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

    private void ensureDir(File dir) throws Exception {
        if (dir == null || dir.exists()) {
            return;
        }
        if (!dir.mkdirs()) {
            throw new IllegalStateException("无法创建目录：" + dir.getAbsolutePath());
        }
    }

    private void copyArchiveEntryStream(
        InputStream inputStream,
        BufferedOutputStream outputStream,
        long totalBytes,
        long[] extractedBytes,
        String progressLabel,
        InstallListener listener
    ) throws Exception {
        byte[] buffer = new byte[8192];
        int len;
        int lastPercent = -1;
        while ((len = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, len);
            extractedBytes[0] += len;
            if (listener != null) {
                if (totalBytes > 0L) {
                    int percent = (int) Math.min(100L, (extractedBytes[0] * 100L) / totalBytes);
                    if (percent != lastPercent) {
                        listener.onProgress(
                            progressLabel + "  " + formatSize(extractedBytes[0]) + " / " + formatSize(totalBytes),
                            percent,
                            false
                        );
                        lastPercent = percent;
                    }
                } else {
                    listener.onProgress(progressLabel + "  已完成 " + formatSize(extractedBytes[0]), 0, true);
                }
            }
        }
        outputStream.flush();
    }

    private void copyStream(
        InputStream inputStream,
        BufferedOutputStream outputStream,
        long totalBytes,
        String progressLabel,
        InstallListener listener
    ) throws Exception {
        byte[] buffer = new byte[8192];
        int len;
        long copied = 0L;
        int lastPercent = -1;
        while ((len = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, len);
            copied += len;
            if (listener != null) {
                if (totalBytes > 0) {
                    int percent = (int) Math.min(100L, (copied * 100L) / totalBytes);
                    if (percent != lastPercent) {
                        listener.onProgress(progressLabel + "  " + formatSize(copied) + " / " + formatSize(totalBytes), percent, false);
                        lastPercent = percent;
                    }
                } else {
                    listener.onProgress(progressLabel + "  已完成 " + formatSize(copied), 0, true);
                }
            }
        }
        outputStream.flush();
    }

    private InstallListener remapListener(final InstallListener listener, final int startPercent, final int weightPercent) {
        if (listener == null) {
            return null;
        }
        return new InstallListener() {
            @Override
            public void onProgress(String message, int percent, boolean indeterminate) {
                int safePercent = clamp(percent);
                int mapped = Math.min(100, startPercent + ((safePercent * weightPercent) / 100));
                listener.onProgress(message, mapped, false);
            }

            @Override
            public void onSuccess(String installedDir) {
            }

            @Override
            public void onError(String message) {
            }
        };
    }

    private int clamp(int value) {
        if (value < 0) {
            return 0;
        }
        if (value > 100) {
            return 100;
        }
        return value;
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
