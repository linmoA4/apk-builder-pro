package com.LM.pack.env;

import android.content.SharedPreferences;
import com.LM.pack.model.EnvironmentState;
import java.io.File;

public class EnvironmentManager {

    public static final String PREFS_NAME = "lm_pack_tool_state";
    private static final String KEY_JDK_NAME = "installed_jdk_name";
    private static final String KEY_JDK_DIR = "installed_jdk_dir";
    private static final String KEY_NDK_NAME = "installed_ndk_name";
    private static final String KEY_NDK_DIR = "installed_ndk_dir";

    private final SharedPreferences sharedPreferences;

    public static final String[] JDK_NAMES = {
        "JDK 8 (长期支持版)",
        "JDK 11 (长期支持版)",
        "JDK 17 (长期支持版)",
        "JDK 21 (当前最新长期支持版)",
        "JDK 25 (预览/前沿版本)",
        "JDK 26 (最新前沿版本)"
    };

    public static final String[] JDK_URLS = {
        "https://aka.ms/download-jdk/microsoft-jdk-8-linux-x64.tar.gz",
        "https://aka.ms/download-jdk/microsoft-jdk-11-linux-x64.tar.gz",
        "https://aka.ms/download-jdk/microsoft-jdk-17-linux-x64.tar.gz",
        "https://aka.ms/download-jdk/microsoft-jdk-21-linux-x64.tar.gz",
        "https://aka.ms/download-jdk/microsoft-jdk-25-linux-x64.tar.gz",
        "https://aka.ms/download-jdk/microsoft-jdk-26-linux-x64.tar.gz"
    };

    public static final String[] NDK_NAMES = {
        "NDK r27c (稳定版，推荐)",
        "NDK r28c (较新稳定版)",
        "NDK r29 Beta 3 (最新测试版)"
    };

    public static final String[] NDK_URLS = {
        "https://dl.google.com/android/repository/android-ndk-r27c-linux.zip",
        "https://dl.google.com/android/repository/android-ndk-r28c-linux.zip",
        "https://dl.google.com/android/repository/android-ndk-r29-beta3-linux.zip"
    };

    public static final String[] JDK_ASSET_ARCHIVES = {
        "",
        "",
        "",
        "toolchains/jdk/microsoft-jdk-21-linux-x64.tar.gz",
        "",
        ""
    };

    public static final String[] NDK_ASSET_ARCHIVES = {
        "toolchains/ndk/android-ndk-r27c-linux.zip",
        "",
        ""
    };

    public EnvironmentManager(SharedPreferences sharedPreferences) {
        this.sharedPreferences = sharedPreferences;
    }

    public EnvironmentState loadState() {
        return new EnvironmentState(
            sharedPreferences.getString(KEY_JDK_NAME, ""),
            sharedPreferences.getString(KEY_JDK_DIR, ""),
            sharedPreferences.getString(KEY_NDK_NAME, ""),
            sharedPreferences.getString(KEY_NDK_DIR, "")
        );
    }

    public EnvironmentState saveInstalledJdk(String name, String dir) {
        sharedPreferences.edit()
            .putString(KEY_JDK_NAME, name)
            .putString(KEY_JDK_DIR, dir)
            .apply();
        return loadState();
    }

    public EnvironmentState saveInstalledNdk(String name, String dir) {
        sharedPreferences.edit()
            .putString(KEY_NDK_NAME, name)
            .putString(KEY_NDK_DIR, dir)
            .apply();
        return loadState();
    }

    public boolean isSelectedJdkInstalled(int selectedJdkIndex, EnvironmentState state) {
        return JDK_NAMES[selectedJdkIndex].equals(state.getInstalledJdkName())
            && isExistingDirectory(state.getInstalledJdkDir());
    }

    public boolean isSelectedNdkInstalled(int selectedNdkIndex, EnvironmentState state) {
        return NDK_NAMES[selectedNdkIndex].equals(state.getInstalledNdkName())
            && isExistingDirectory(state.getInstalledNdkDir());
    }

    public boolean isExistingDirectory(String path) {
        if (path == null || path.length() == 0) {
            return false;
        }
        File file = new File(path);
        return file.exists() && file.isDirectory();
    }

    public String getJdkInstallDir(String jdkName) {
        return "/storage/emulated/0/LMBuildTools/jdk/" + sanitizeDirName(jdkName);
    }

    public String getNdkInstallDir(String ndkName) {
        return "/storage/emulated/0/LMBuildTools/ndk/" + sanitizeDirName(ndkName);
    }

    public String getProjectRootDir(String appName) {
        return "/storage/emulated/0/LMBuildTools/projects/" + sanitizeDirName(appName);
    }

    public String getManagedProjectRootDir() {
        return "/storage/emulated/0/LMBuildTools/projects";
    }

    public String getImportedProjectRootDir() {
        return "/storage/emulated/0/LMBuildTools/android/data";
    }

    public String getImportTempDir() {
        return "/storage/emulated/0/LMBuildTools/import_temp";
    }

    public String getPackageCacheDir() {
        return "/storage/emulated/0/LMBuildTools/packages";
    }

    public String getJdkPackageArchivePath(int index) {
        return getPackageCacheDir() + "/jdk/" + sanitizeDirName(JDK_NAMES[index]) + ".tar.gz";
    }

    public String getNdkPackageArchivePath(int index) {
        return getPackageCacheDir() + "/ndk/" + sanitizeDirName(NDK_NAMES[index]) + ".zip";
    }

    public boolean isEmbeddedJdk(int index) {
        return JDK_ASSET_ARCHIVES[index] != null && JDK_ASSET_ARCHIVES[index].length() > 0;
    }

    public boolean isEmbeddedNdk(int index) {
        return NDK_ASSET_ARCHIVES[index] != null && NDK_ASSET_ARCHIVES[index].length() > 0;
    }

    private String sanitizeDirName(String name) {
        return name.replace(" ", "_")
            .replace("(", "")
            .replace(")", "")
            .replace("，", "_")
            .replace("/", "_");
    }
}
