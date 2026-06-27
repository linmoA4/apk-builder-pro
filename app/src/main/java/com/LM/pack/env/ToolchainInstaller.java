package com.LM.pack.env;

import android.content.Context;
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
        void onProgress(String message);
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

                    if (environmentManager.isEmbeddedJdk(index)) {
                        listener.onProgress("正在从 APK 内嵌资源复制 JDK 安装包...");
                        copyAssetToFile(EnvironmentManager.JDK_ASSET_ARCHIVES[index], archiveFile);
                    } else {
                        listener.onProgress("正在下载 JDK 安装包...");
                        downloadToFile(EnvironmentManager.JDK_URLS[index], archiveFile);
                    }

                    File installRoot = new File(environmentManager.getJdkInstallDir(EnvironmentManager.JDK_NAMES[index]));
                    clearDirectory(installRoot);
                    ensureDir(installRoot);
                    listener.onProgress("正在解压 JDK 到本地目录...");
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

                    if (environmentManager.isEmbeddedNdk(index)) {
                        listener.onProgress("正在从 APK 内嵌资源复制 NDK 安装包...");
                        copyAssetToFile(EnvironmentManager.NDK_ASSET_ARCHIVES[index], archiveFile);
                    } else {
                        listener.onProgress("正在下载 NDK 安装包...");
                        downloadToFile(EnvironmentManager.NDK_URLS[index], archiveFile);
                    }

                    File installRoot = new File(environmentManager.getNdkInstallDir(EnvironmentManager.NDK_NAMES[index]));
                    clearDirectory(installRoot);
                    ensureDir(installRoot);
                    listener.onProgress("正在解压 NDK 到本地目录...");
                    extractZip(archiveFile, installRoot);
                    File actualDir = resolveInstalledHome(installRoot);
                    listener.onSuccess(actualDir.getAbsolutePath());
                } catch (Exception e) {
                    listener.onError("NDK 安装失败：" + e.getMessage());
                }
            }
        }).start();
    }

    private void copyAssetToFile(String assetPath, File targetFile) throws Exception {
        AssetManager assetManager = context.getAssets();
        InputStream inputStream = null;
        BufferedOutputStream outputStream = null;
        try {
            inputStream = assetManager.open(assetPath);
            outputStream = new BufferedOutputStream(new FileOutputStream(targetFile));
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

    private void downloadToFile(String urlString, File targetFile) throws Exception {
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
                    downloadToFile(redirect, targetFile);
                    return;
                }
            }
            if (responseCode >= 400) {
                throw new IllegalStateException("下载失败，HTTP " + connection.getResponseCode());
            }
            inputStream = new BufferedInputStream(connection.getInputStream());
            outputStream = new BufferedOutputStream(new FileOutputStream(targetFile));
            copyStream(inputStream, outputStream);
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

    private void copyStream(InputStream inputStream, BufferedOutputStream outputStream) throws Exception {
        byte[] buffer = new byte[8192];
        int len;
        while ((len = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, len);
        }
        outputStream.flush();
    }
}
