package com.LM.pack.env;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
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
                        EnvironmentManager.JDK_ASSET_ARCHIVES[index],
                        EnvironmentManager.JDK_URLS[index],
                        EnvironmentManager.JDK_FALLBACK_URLS[index],
                        archiveFile,
                        "JDK 安装包",
                        listener
                    );

                    File installRoot = new File(environmentManager.getJdkInstallDir(EnvironmentManager.JDK_NAMES[index]));
                    clearDirectory(installRoot);
                    ensureDir(installRoot);
                    listener.onProgress("正在解压 JDK 到本地目录...", 100, true);
                    extractTarGz(archiveFile, installRoot);
                    File actualDir = resolveInstalledHome(installRoot);
                    listener.onSuccess(actualDir.getAbsolutePath());
                } catch (Exception e) {
                    listener.onError("JDK 安装失败：" + e.getMessage());
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
                        EnvironmentManager.NDK_ASSET_ARCHIVES[index],
                        EnvironmentManager.NDK_URLS[index],
                        EnvironmentManager.NDK_FALLBACK_URLS[index],
                        archiveFile,
                        "NDK 安装包",
                        listener
                    );

                    File installRoot = new File(environmentManager.getNdkInstallDir(EnvironmentManager.NDK_NAMES[index]));
                    clearDirectory(installRoot);
                    ensureDir(installRoot);
                    listener.onProgress("正在解压 NDK 到本地目录...", 100, true);
                    extractZip(archiveFile, installRoot);
                    File actualDir = resolveInstalledHome(installRoot);
                    listener.onSuccess(actualDir.getAbsolutePath());
                } catch (Exception e) {
                    listener.onError("NDK 安装失败：" + e.getMessage());
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
                        EnvironmentManager.SDK_ASSET_ARCHIVE,
                        "",
                        new String[0],
                        archiveFile,
                        "Android SDK 命令行工具",
                        listener
                    );

                    File installRoot = new File(environmentManager.getEmbeddedSdkInstallDir());
                    clearDirectory(installRoot);
                    ensureDir(installRoot);
                    listener.onProgress("正在解压 Android SDK 命令行工具...", 100, true);
                    extractZip(archiveFile, installRoot);
                    normalizeEmbeddedSdkLayout(installRoot);
                    listener.onSuccess(installRoot.getAbsolutePath());
                } catch (Exception e) {
                    listener.onError("Android SDK 解压失败：" + e.getMessage());
                }
            }
        }).start();
    }

    private void prepareArchive(
        String assetPath,
        String downloadUrl,
        String[] fallbackUrls,
        File targetFile,
        String displayName,
        InstallListener listener
    ) throws Exception {
        if (assetPath != null && assetPath.length() > 0 && assetExists(assetPath)) {
            listener.onProgress("正在从应用内置资源复制" + displayName + "...", 0, false);
            copyAssetToFile(assetPath, targetFile, listener, "正在复制" + displayName);
            return;
        }
        if (targetFile.exists() && targetFile.length() > 0) {
            listener.onProgress("已找到本地缓存，跳过下载。", 100, false);
            return;
        }
        String resolvedUrl = resolveDownloadUrl(downloadUrl, fallbackUrls, displayName, listener);
        listener.onProgress("正在下载" + displayName + "...", 0, false);
        downloadToFile(resolvedUrl, targetFile, listener, "正在下载" + displayName);
    }

    private String resolveDownloadUrl(String primaryUrl, String[] fallbackUrls, String displayName, InstallListener listener) throws Exception {
        java.util.LinkedHashSet<String> candidates = new java.util.LinkedHashSet<String>();
        if (primaryUrl != null && primaryUrl.trim().length() > 0) {
            candidates.add(primaryUrl.trim());
        }
        if (fallbackUrls != null) {
            for (int i = 0; i < fallbackUrls.length; i++) {
                if (fallbackUrls[i] != null && fallbackUrls[i].trim().length() > 0) {
                    candidates.add(fallbackUrls[i].trim());
                }
            }
        }
        if (candidates.isEmpty()) {
            throw new IllegalStateException(displayName + " 没有可用下载地址");
        }
        listener.onProgress("正在校验 " + displayName + " 下载链路...", 0, true);
        java.util.Iterator<String> iterator = candidates.iterator();
        while (iterator.hasNext()) {
            String candidate = iterator.next();
            if (isUrlReachable(candidate, 0)) {
                return candidate;
            }
        }
        throw new IllegalStateException(displayName + " 所有下载链路都不可用");
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

    private void copyAssetToFile(String assetPath, File targetFile, InstallListener listener, String progressLabel) throws Exception {
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
            copyStream(inputStream, outputStream, totalBytes, progressLabel, listener);
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
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(60000);
            connection.setReadTimeout(60000);
            connection.setRequestProperty("User-Agent", "LM-APK-Builder/2.0");
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
            inputStream = new BufferedInputStream(connection.getInputStream());
            outputStream = new BufferedOutputStream(new FileOutputStream(targetFile));
            copyStream(inputStream, outputStream, totalBytes, progressLabel, listener);
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

    private void extractTarGz(File archiveFile, File targetDir) throws Exception {
        TarArchiveInputStream tarInputStream = null;
        try {
            tarInputStream = new TarArchiveInputStream(
                new GZIPInputStream(new BufferedInputStream(new java.io.FileInputStream(archiveFile)))
            );
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
                } else {
                    ensureDir(outputFile.getParentFile());
                    BufferedOutputStream outputStream = null;
                    try {
                        outputStream = new BufferedOutputStream(new FileOutputStream(outputFile));
                        copyStream(tarInputStream, outputStream);
                    } finally {
                        if (outputStream != null) {
                            outputStream.close();
                        }
                    }
                    outputFile.setExecutable((entry.getMode() & 0100) != 0);
                }
            }
        } finally {
            if (tarInputStream != null) {
                tarInputStream.close();
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
                        copyStream(zipInputStream, outputStream);
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
