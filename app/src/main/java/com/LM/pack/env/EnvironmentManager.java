package com.LM.pack.env;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Environment;
import com.LM.pack.model.EnvironmentState;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;

public class EnvironmentManager {

    public static final String PREFS_NAME = "lm_pack_tool_state";
    private static final String KEY_JDK_REGISTRY = "installed_jdk_registry";
    private static final String KEY_NDK_REGISTRY = "installed_ndk_registry";
    private static final String KEY_JDK_NAME = "installed_jdk_name";
    private static final String KEY_JDK_DIR = "installed_jdk_dir";
    private static final String KEY_ANDROID_SDK_DIR = "android_sdk_dir";
    private static final String KEY_NDK_NAME = "installed_ndk_name";
    private static final String KEY_NDK_DIR = "installed_ndk_dir";
    private static final String KEY_SELECTED_JDK_INDEX = "selected_jdk_index";
    private static final String KEY_SELECTED_NDK_INDEX = "selected_ndk_index";
    private static final String KEY_DOWNLOAD_ROUTE = "download_route";
    private static final String KEY_SDK_LICENSE_ACCEPTED = "sdk_license_accepted";

    public static final String DOWNLOAD_ROUTE_CHINA = "china";
    public static final String DOWNLOAD_ROUTE_GLOBAL = "global";

    public static final int DEFAULT_JDK_INDEX = 4;
    public static final int DEFAULT_NDK_INDEX = 4;
    public static final int EMBEDDED_JDK_INDEX = DEFAULT_JDK_INDEX;
    public static final int EMBEDDED_NDK_INDEX = DEFAULT_NDK_INDEX;
    public static final String DEFAULT_DOWNLOAD_ROUTE = DOWNLOAD_ROUTE_CHINA;

    public static final String SDK_DISPLAY_NAME = "Android SDK Command-line Tools";
    public static final String DEFAULT_GRADLE_VERSION = "8.7";
    public static final String REPOSITORY_RAW_BASE = "https://raw.githubusercontent.com/linmoA4/apk-builder-pro/main";
    public static final String DEFAULT_GRADLE_DISTRIBUTION_SHA256 = "544c35d6bd849ae8a5ed0bcea39ba677dc40f49df7d1835561582da2009b961d";
    public static final String DEFAULT_GRADLE_WRAPPER_SHA256 = "cb0da6751c2b753a16ac168bb354870ebb1e162e9083f116729cec9c781156b8";

    private static final LinkedHashMap<String, String> GRADLE_DISTRIBUTION_SHA256 = new LinkedHashMap<String, String>();
    private static final LinkedHashMap<String, String> GRADLE_WRAPPER_SHA256 = new LinkedHashMap<String, String>();

    static {
        GRADLE_DISTRIBUTION_SHA256.put("8.7", DEFAULT_GRADLE_DISTRIBUTION_SHA256);
        GRADLE_WRAPPER_SHA256.put("8.7", DEFAULT_GRADLE_WRAPPER_SHA256);
    }

    public static final String[] JDK_NAMES = {
        "JDK 8 (长期支持版)",
        "JDK 11 (长期支持版)",
        "JDK 17 (长期支持版)",
        "JDK 20 (过渡版本)",
        "JDK 21 (当前推荐版)",
        "JDK 22 (过渡版本)",
        "JDK 23 (过渡版本)",
        "JDK 24 (过渡版本)",
        "JDK 25 (前沿版本)",
        "JDK 26 (实验版本)"
    };

    public static final String[] JDK_URLS = {
        "https://api.adoptium.net/v3/binary/latest/8/ga/linux/x64/jdk/hotspot/normal/eclipse",
        "https://api.adoptium.net/v3/binary/latest/11/ga/linux/x64/jdk/hotspot/normal/eclipse",
        "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse",
        "https://api.adoptium.net/v3/binary/latest/20/ga/linux/x64/jdk/hotspot/normal/eclipse",
        "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse",
        "https://api.adoptium.net/v3/binary/latest/22/ga/linux/x64/jdk/hotspot/normal/eclipse",
        "https://api.adoptium.net/v3/binary/latest/23/ga/linux/x64/jdk/hotspot/normal/eclipse",
        "https://api.adoptium.net/v3/binary/latest/24/ga/linux/x64/jdk/hotspot/normal/eclipse",
        "https://api.adoptium.net/v3/binary/latest/25/ga/linux/x64/jdk/hotspot/normal/eclipse",
        "https://api.adoptium.net/v3/binary/latest/26/ga/linux/x64/jdk/hotspot/normal/eclipse"
    };

    public static final String[][] JDK_FALLBACK_URLS = {
        {"https://api.adoptium.net/v3/binary/latest/8/ga/linux/x64/jdk/hotspot/normal/eclipse"},
        {"https://api.adoptium.net/v3/binary/latest/11/ga/linux/x64/jdk/hotspot/normal/eclipse"},
        {"https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse"},
        {"https://api.adoptium.net/v3/binary/latest/20/ga/linux/x64/jdk/hotspot/normal/eclipse"},
        {"https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse"},
        {"https://api.adoptium.net/v3/binary/latest/22/ga/linux/x64/jdk/hotspot/normal/eclipse"},
        {"https://api.adoptium.net/v3/binary/latest/23/ga/linux/x64/jdk/hotspot/normal/eclipse"},
        {"https://api.adoptium.net/v3/binary/latest/24/ga/linux/x64/jdk/hotspot/normal/eclipse"},
        {"https://api.adoptium.net/v3/binary/latest/25/ga/linux/x64/jdk/hotspot/normal/eclipse"},
        {"https://api.adoptium.net/v3/binary/latest/26/ga/linux/x64/jdk/hotspot/normal/eclipse"}
    };

    public static final String[] NDK_NAMES = {
        "NDK r23c (旧项目兼容版)",
        "NDK r24 (标准静态版)",
        "NDK r25c (中期版本)",
        "NDK r26d (较新过渡版)",
        "NDK r27c (稳定版，推荐)",
        "NDK r28c (较新稳定版)",
        "NDK r29 Beta 3 (测试版)"
    };

    public static final String[] NDK_URLS = {
        "https://dl.google.com/android/repository/android-ndk-r23c-linux.zip",
        "https://dl.google.com/android/repository/android-ndk-r24-linux.zip",
        "https://dl.google.com/android/repository/android-ndk-r25c-linux.zip",
        "https://dl.google.com/android/repository/android-ndk-r26d-linux.zip",
        "https://dl.google.com/android/repository/android-ndk-r27c-linux.zip",
        "https://dl.google.com/android/repository/android-ndk-r28c-linux.zip",
        "https://dl.google.com/android/repository/android-ndk-r29-beta3-linux.zip"
    };

    public static final String[][] NDK_FALLBACK_URLS = {
        {
            "https://googledownloads.cn/android/repository/android-ndk-r23c-linux.zip",
            "https://redirector.gvt1.com/edgedl/android/repository/android-ndk-r23c-linux.zip"
        },
        {},
        {
            "https://googledownloads.cn/android/repository/android-ndk-r25c-linux.zip",
            "https://redirector.gvt1.com/edgedl/android/repository/android-ndk-r25c-linux.zip"
        },
        {
            "https://googledownloads.cn/android/repository/android-ndk-r26d-linux.zip",
            "https://redirector.gvt1.com/edgedl/android/repository/android-ndk-r26d-linux.zip"
        },
        {
            "https://googledownloads.cn/android/repository/android-ndk-r27c-linux.zip",
            "https://redirector.gvt1.com/edgedl/android/repository/android-ndk-r27c-linux.zip"
        },
        {
            "https://googledownloads.cn/android/repository/android-ndk-r28c-linux.zip",
            "https://redirector.gvt1.com/edgedl/android/repository/android-ndk-r28c-linux.zip"
        },
        {
            "https://googledownloads.cn/android/repository/android-ndk-r29-beta3-linux.zip",
            "https://redirector.gvt1.com/edgedl/android/repository/android-ndk-r29-beta3-linux.zip"
        }
    };

    public static final String SDK_OFFICIAL_URL = "https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip";
    public static final String SDK_CHINA_URL = "https://googledownloads.cn/android/repository/commandlinetools-linux-13114758_latest.zip";

    public static final String[] SDK_FALLBACK_URLS = {
        "https://dl.google.com/android/repository/commandlinetools-linux-latest.zip",
        "https://googledownloads.cn/android/repository/commandlinetools-linux-latest.zip",
        "https://redirector.gvt1.com/edgedl/android/repository/commandlinetools-linux-13114758_latest.zip"
    };

    public static final String PLATFORM_TOOLS_OFFICIAL_URL = "https://dl.google.com/android/repository/platform-tools-latest-linux.zip";
    public static final String PLATFORM_TOOLS_CHINA_URL = "https://googledownloads.cn/android/repository/platform-tools-latest-linux.zip";

    public static final int[] ANDROID_API_LEVELS = {27, 28, 29, 30, 31, 32, 33, 34, 35, 36};
    public static final String[] ANDROID_VERSION_NAMES = {
        "Android 8.1", "Android 9", "Android 10", "Android 11", "Android 12",
        "Android 12L", "Android 13", "Android 14", "Android 15", "Android 16"
    };

    public static final String[][] ANDROID_PLATFORM_URLS = {
        {"platform-27_r03.zip", "https://dl.google.com/android/repository/platform-27_r03.zip"},
        {"platform-28_r06.zip", "https://dl.google.com/android/repository/platform-28_r06.zip"},
        {"platform-29_r05.zip", "https://dl.google.com/android/repository/platform-29_r05.zip"},
        {"platform-30_r03.zip", "https://dl.google.com/android/repository/platform-30_r03.zip"},
        {"platform-31_r01.zip", "https://dl.google.com/android/repository/platform-31_r01.zip"},
        {"platform-32_r01.zip", "https://dl.google.com/android/repository/platform-32_r01.zip"},
        {"platform-33-ext3_r03.zip", "https://dl.google.com/android/repository/platform-33-ext3_r03.zip"},
        {"platform-34-ext7_r03.zip", "https://dl.google.com/android/repository/platform-34-ext7_r03.zip"},
        {"platform-35_r02.zip", "https://dl.google.com/android/repository/platform-35_r02.zip"},
        {"platform-36_r02.zip", "https://dl.google.com/android/repository/platform-36_r02.zip"}
    };

    public static final String[][] ANDROID_BUILD_TOOLS = {
        {"27.0.3", "https://dl.google.com/android/repository/build-tools_r27.0.3-linux.zip"},
        {"28.0.3", "https://dl.google.com/android/repository/build-tools_r28.0.3-linux.zip"},
        {"29.0.3", "https://dl.google.com/android/repository/build-tools_r29.0.3-linux.zip"},
        {"30.0.3", "https://dl.google.com/android/repository/build-tools_r30.0.3-linux.zip"},
        {"31.0.0", "https://dl.google.com/android/repository/build-tools_r31-linux.zip"},
        {"32.0.0", "https://dl.google.com/android/repository/build-tools_r32-linux.zip"},
        {"33.0.3", "https://dl.google.com/android/repository/build-tools_r33.0.3-linux.zip"},
        {"34.0.0", "https://dl.google.com/android/repository/build-tools_r34-linux.zip"},
        {"35.0.1", "https://dl.google.com/android/repository/build-tools_r35.0.1_linux.zip"},
        {"36.0.0", "https://dl.google.com/android/repository/build-tools_r36_linux.zip"}
    };

    public static final String[] GRADLE_VERIFIED_DIRECT_URLS = {
        "https://services.gradle.org/distributions/gradle-8.7-bin.zip",
        "https://mirrors.cloud.tencent.com/gradle/gradle-8.7-bin.zip",
        "https://downloads.gradle.org/distributions/gradle-8.7-bin.zip"
    };

    private final SharedPreferences sharedPreferences;
    private final File baseDir;

    public EnvironmentManager(Context context, SharedPreferences sharedPreferences) {
        this.sharedPreferences = sharedPreferences;
        this.baseDir = resolveBaseDir(context);
    }

    private File resolveBaseDir(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                if (Environment.isExternalStorageManager()) {
                    return new File(Environment.getExternalStorageDirectory(), "LMBuildTools");
                }
            } catch (Throwable t) {
            }
        }
        File external = context.getExternalFilesDir(null);
        if (external == null) {
            external = context.getFilesDir();
        }
        return new File(external, "LMBuildTools");
    }

    public EnvironmentState loadState() {
        LinkedHashMap<String, String> jdkRegistry = readRegistry(KEY_JDK_REGISTRY);
        LinkedHashMap<String, String> ndkRegistry = readRegistry(KEY_NDK_REGISTRY);
        boolean migrated = false;

        String legacyJdkName = safeText(sharedPreferences.getString(KEY_JDK_NAME, ""));
        String legacyJdkDir = safeText(sharedPreferences.getString(KEY_JDK_DIR, ""));
        if (jdkRegistry.isEmpty() && legacyJdkName.length() > 0 && legacyJdkDir.length() > 0) {
            jdkRegistry.put(legacyJdkName, legacyJdkDir);
            migrated = true;
        }

        String legacyNdkName = safeText(sharedPreferences.getString(KEY_NDK_NAME, ""));
        String legacyNdkDir = safeText(sharedPreferences.getString(KEY_NDK_DIR, ""));
        if (ndkRegistry.isEmpty() && legacyNdkName.length() > 0 && legacyNdkDir.length() > 0) {
            ndkRegistry.put(legacyNdkName, legacyNdkDir);
            migrated = true;
        }

        if (migrated) {
            sharedPreferences.edit()
                .putString(KEY_JDK_REGISTRY, encodeRegistry(jdkRegistry))
                .putString(KEY_NDK_REGISTRY, encodeRegistry(ndkRegistry))
                .apply();
        }

        return new EnvironmentState(
            jdkRegistry,
            sharedPreferences.getString(KEY_ANDROID_SDK_DIR, ""),
            ndkRegistry
        );
    }

    public EnvironmentState saveInstalledJdk(String name, String dir) {
        LinkedHashMap<String, String> registry = readRegistry(KEY_JDK_REGISTRY);
        String cleanName = safeText(name);
        String cleanDir = safeText(dir);
        if (cleanName.length() > 0 && cleanDir.length() > 0) {
            registry.put(cleanName, cleanDir);
        }
        sharedPreferences.edit()
            .putString(KEY_JDK_REGISTRY, encodeRegistry(registry))
            .putString(KEY_JDK_NAME, cleanName)
            .putString(KEY_JDK_DIR, cleanDir)
            .apply();
        return loadState();
    }

    public EnvironmentState saveInstalledNdk(String name, String dir) {
        LinkedHashMap<String, String> registry = readRegistry(KEY_NDK_REGISTRY);
        String cleanName = safeText(name);
        String cleanDir = safeText(dir);
        if (cleanName.length() > 0 && cleanDir.length() > 0) {
            registry.put(cleanName, cleanDir);
        }
        sharedPreferences.edit()
            .putString(KEY_NDK_REGISTRY, encodeRegistry(registry))
            .putString(KEY_NDK_NAME, cleanName)
            .putString(KEY_NDK_DIR, cleanDir)
            .apply();
        return loadState();
    }

    public EnvironmentState saveAndroidSdkDir(String dir) {
        sharedPreferences.edit()
            .putString(KEY_ANDROID_SDK_DIR, dir == null ? "" : dir)
            .apply();
        return loadState();
    }

    public int loadSelectedJdkIndex() {
        int index = sharedPreferences.getInt(KEY_SELECTED_JDK_INDEX, DEFAULT_JDK_INDEX);
        if (index < 0 || index >= JDK_NAMES.length) {
            return DEFAULT_JDK_INDEX;
        }
        return index;
    }

    public int loadSelectedNdkIndex() {
        int index = sharedPreferences.getInt(KEY_SELECTED_NDK_INDEX, DEFAULT_NDK_INDEX);
        if (index < 0 || index >= NDK_NAMES.length) {
            return DEFAULT_NDK_INDEX;
        }
        return index;
    }

    public void saveSelectedJdkIndex(int index) {
        if (index < 0 || index >= JDK_NAMES.length) {
            return;
        }
        sharedPreferences.edit().putInt(KEY_SELECTED_JDK_INDEX, index).apply();
    }

    public void saveSelectedNdkIndex(int index) {
        if (index < 0 || index >= NDK_NAMES.length) {
            return;
        }
        sharedPreferences.edit().putInt(KEY_SELECTED_NDK_INDEX, index).apply();
    }

    public String loadDownloadRoute() {
        String route = safeText(sharedPreferences.getString(KEY_DOWNLOAD_ROUTE, DEFAULT_DOWNLOAD_ROUTE));
        if (DOWNLOAD_ROUTE_CHINA.equals(route) || DOWNLOAD_ROUTE_GLOBAL.equals(route)) {
            return route;
        }
        return DEFAULT_DOWNLOAD_ROUTE;
    }

    public void saveDownloadRoute(String route) {
        String normalized = normalizeDownloadRoute(route);
        sharedPreferences.edit().putString(KEY_DOWNLOAD_ROUTE, normalized).apply();
    }

    public boolean isSdkLicenseAccepted() {
        return sharedPreferences.getBoolean(KEY_SDK_LICENSE_ACCEPTED, false);
    }

    public void saveSdkLicenseAccepted(boolean accepted) {
        sharedPreferences.edit().putBoolean(KEY_SDK_LICENSE_ACCEPTED, accepted).apply();
    }

    public boolean isSelectedJdkInstalled(int selectedJdkIndex, EnvironmentState state) {
        return isExistingDirectory(getSelectedJdkDir(selectedJdkIndex, state));
    }

    public boolean isSelectedNdkInstalled(int selectedNdkIndex, EnvironmentState state) {
        return isExistingDirectory(getSelectedNdkDir(selectedNdkIndex, state));
    }

    public boolean isAndroidSdkRegistered(EnvironmentState state) {
        return state != null && isExistingDirectory(state.getAndroidSdkDir());
    }

    public boolean isExistingDirectory(String path) {
        if (path == null || path.length() == 0) {
            return false;
        }
        File file = new File(path);
        return file.exists() && file.isDirectory();
    }

    public String getSelectedJdkName(int selectedJdkIndex) {
        return JDK_NAMES[normalizeJdkIndex(selectedJdkIndex)];
    }

    public String getSelectedNdkName(int selectedNdkIndex) {
        return NDK_NAMES[normalizeNdkIndex(selectedNdkIndex)];
    }

    public String getSelectedJdkDir(int selectedJdkIndex, EnvironmentState state) {
        if (state == null) {
            return "";
        }
        return safeText(state.getInstalledJdkDir(getSelectedJdkName(selectedJdkIndex)));
    }

    public String getSelectedNdkDir(int selectedNdkIndex, EnvironmentState state) {
        if (state == null) {
            return "";
        }
        return safeText(state.getInstalledNdkDir(getSelectedNdkName(selectedNdkIndex)));
    }

    public String getJdkInstallDir(String jdkName) {
        return new File(new File(baseDir, "jdk"), sanitizeDirName(jdkName)).getAbsolutePath();
    }

    public String getNdkInstallDir(String ndkName) {
        return new File(new File(baseDir, "ndk"), sanitizeDirName(ndkName)).getAbsolutePath();
    }

    public String getProjectRootDir(String appName) {
        return new File(new File(baseDir, "projects"), sanitizeDirName(appName)).getAbsolutePath();
    }

    public String getManagedProjectRootDir() {
        return new File(baseDir, "projects").getAbsolutePath();
    }

    public String getImportedProjectRootDir() {
        return new File(new File(baseDir, "android"), "data").getAbsolutePath();
    }

    public String getImportTempDir() {
        return new File(baseDir, "import_temp").getAbsolutePath();
    }

    public String getPackageCacheDir() {
        return new File(baseDir, "packages").getAbsolutePath();
    }

    public String getGradlePackageArchivePath() {
        return getPackageCacheDir() + "/gradle/gradle-" + DEFAULT_GRADLE_VERSION + "-bin.zip";
    }

    public String getGradlePackageArchivePath(String gradleVersion) {
        String version = safeText(gradleVersion);
        if (version.length() == 0) {
            version = DEFAULT_GRADLE_VERSION;
        }
        return getPackageCacheDir() + "/gradle/gradle-" + version + "-bin.zip";
    }

    public String getSdkPackageArchivePath() {
        return getPackageCacheDir() + "/sdk/commandlinetools-linux-latest.zip";
    }

    public String getEmbeddedSdkInstallDir() {
        return new File(baseDir, "sdk").getAbsolutePath();
    }

    public String getEmbeddedSdkCmdlineToolsDir() {
        return new File(new File(getEmbeddedSdkInstallDir(), "cmdline-tools"), "latest").getAbsolutePath();
    }

    public String getSdkManagerPath() {
        return new File(new File(getEmbeddedSdkCmdlineToolsDir(), "bin"), "sdkmanager").getAbsolutePath();
    }

    public String getGradleInstallDir() {
        return new File(baseDir, "gradle").getAbsolutePath();
    }

    public String getGradleUserHomeDir() {
        return new File(baseDir, "gradle-user-home").getAbsolutePath();
    }

    public String getBaseDir() {
        return baseDir.getAbsolutePath();
    }

    public File getDefaultBrowseRootDir() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                if (Environment.isExternalStorageManager()) {
                    return Environment.getExternalStorageDirectory();
                }
            } catch (Throwable t) {
            }
        }
        return baseDir;
    }

    public String getJdkPackageArchivePath(int index) {
        return getPackageCacheDir() + "/jdk/" + sanitizeDirName(JDK_NAMES[normalizeJdkIndex(index)]) + ".tar.gz";
    }

    public String getNdkPackageArchivePath(int index) {
        return getPackageCacheDir() + "/ndk/" + sanitizeDirName(NDK_NAMES[normalizeNdkIndex(index)]) + ".zip";
    }

    public int recommendJdkIndex(File projectDir) {
        String gradleVersion = findGradleVersion(projectDir);
        String agpVersion = findAgpVersion(projectDir);
        String sourceCompatibility = findSourceCompatibility(projectDir);
        int gradleMajor = majorOfVersion(gradleVersion);
        int gradleMinor = minorOfVersion(gradleVersion);
        int agpMajor = majorOfVersion(agpVersion);
        int agpMinor = minorOfVersion(agpVersion);
        int javaLevel = parseJavaLevel(sourceCompatibility);

        if (javaLevel >= 26 || gradleMajor >= 10 || agpMajor >= 10) {
            return 9;
        }
        if (javaLevel >= 25 || gradleMajor >= 9 || agpMajor >= 9) {
            return 8;
        }
        if (javaLevel >= 24 || (gradleMajor == 8 && gradleMinor >= 12) || agpMajor >= 8) {
            return 7;
        }
        if (javaLevel >= 21 || gradleMajor >= 8 || agpMajor >= 8) {
            return 4;
        }
        if (javaLevel >= 20) {
            return 3;
        }
        if (javaLevel >= 17 || gradleMajor >= 7 || agpMajor >= 7) {
            return 2;
        }
        if (javaLevel >= 11 || gradleMajor >= 5 || agpMajor >= 4) {
            return 1;
        }
        if (gradleMajor <= 0 && agpMajor <= 0 && javaLevel <= 0) {
            return 4;
        }
        return 0;
    }

    public int recommendNdkIndex(File projectDir) {
        String ndkVersion = findNdkVersion(projectDir);
        if (ndkVersion.startsWith("29") || ndkVersion.contains("r29")) {
            return 6;
        }
        if (ndkVersion.startsWith("28") || ndkVersion.contains("r28")) {
            return 5;
        }
        if (ndkVersion.startsWith("27") || ndkVersion.contains("r27")) {
            return 4;
        }
        if (ndkVersion.startsWith("26") || ndkVersion.contains("r26")) {
            return 3;
        }
        if (ndkVersion.startsWith("25") || ndkVersion.contains("r25")) {
            return 2;
        }
        if (ndkVersion.startsWith("23") || ndkVersion.contains("r23")) {
            return 0;
        }
        return 4;
    }

    public String recommendGradleVersion(File projectDir) {
        String version = findGradleVersion(projectDir);
        return version.length() == 0 ? DEFAULT_GRADLE_VERSION : version;
    }

    public String recommendBuildToolsVersion(File projectDir) {
        String value = extractFromGradle(projectDir, "buildToolsVersion\\s*(?:=\\s*)?[\"']([^\"']+)[\"']");
        if (value.length() > 0) {
            return value;
        }
        String compileSdk = extractFromGradle(projectDir, "compileSdk(?:Version)?\\s*(?:=\\s*)?(\\d+)");
        if (compileSdk.length() > 0) {
            return getDefaultBuildToolsForApi(parseIntSafe(compileSdk));
        }
        return "36.0.0";
    }

    public String getDefaultBuildToolsForApi(int apiLevel) {
        for (int i = 0; i < ANDROID_API_LEVELS.length; i++) {
            if (ANDROID_API_LEVELS[i] == apiLevel) {
                return ANDROID_BUILD_TOOLS[i][0];
            }
        }
        return apiLevel + ".0.0";
    }

    public String getAndroidPlatformUrl(int apiLevel) {
        for (int i = 0; i < ANDROID_API_LEVELS.length; i++) {
            if (ANDROID_API_LEVELS[i] == apiLevel) {
                return ANDROID_PLATFORM_URLS[i][1];
            }
        }
        return "";
    }

    public String getAndroidBuildToolsUrl(int apiLevel) {
        for (int i = 0; i < ANDROID_API_LEVELS.length; i++) {
            if (ANDROID_API_LEVELS[i] == apiLevel) {
                return ANDROID_BUILD_TOOLS[i][1];
            }
        }
        return "";
    }

    public String getAndroidPlatformFileName(int apiLevel) {
        for (int i = 0; i < ANDROID_API_LEVELS.length; i++) {
            if (ANDROID_API_LEVELS[i] == apiLevel) {
                return ANDROID_PLATFORM_URLS[i][0];
            }
        }
        return "platform-" + apiLevel + ".zip";
    }

    public String getAndroidBuildToolsFileName(int apiLevel) {
        for (int i = 0; i < ANDROID_API_LEVELS.length; i++) {
            if (ANDROID_API_LEVELS[i] == apiLevel) {
                String[] parts = ANDROID_BUILD_TOOLS[i][1].split("/");
                return parts[parts.length - 1];
            }
        }
        return "build-tools-" + apiLevel + ".zip";
    }

    public String getPlatformToolsDownloadUrl() {
        if (isChinaDownloadRoute()) {
            return PLATFORM_TOOLS_CHINA_URL;
        }
        return PLATFORM_TOOLS_OFFICIAL_URL;
    }

    public String recommendCompileSdk(File projectDir) {
        String value = extractFromGradle(projectDir, "compileSdk(?:Version)?\\s*(?:=\\s*)?(\\d+)");
        return value.length() == 0 ? "36" : value;
    }

    public String getRecommendedJdkName(File projectDir) {
        return getSelectedJdkName(recommendJdkIndex(projectDir));
    }

    public String getRecommendedNdkName(File projectDir) {
        return getSelectedNdkName(recommendNdkIndex(projectDir));
    }

    public String[] getSdkDownloadCandidates() {
        LinkedHashSet<String> values = new LinkedHashSet<String>();
        if (isChinaDownloadRoute()) {
            values.add(SDK_CHINA_URL);
            values.add(SDK_OFFICIAL_URL);
        } else {
            values.add(SDK_OFFICIAL_URL);
            values.add(SDK_CHINA_URL);
        }
        appendUrls(values, SDK_FALLBACK_URLS);
        return values.toArray(new String[0]);
    }

    public String[] getJdkDownloadCandidates(int index) {
        LinkedHashSet<String> values = new LinkedHashSet<String>();
        int safeIndex = normalizeJdkIndex(index);
        values.add(JDK_URLS[safeIndex]);
        appendUrls(values, JDK_FALLBACK_URLS[safeIndex]);
        return values.toArray(new String[0]);
    }

    public String[] getNdkDownloadCandidates(int index) {
        LinkedHashSet<String> values = new LinkedHashSet<String>();
        int safeIndex = normalizeNdkIndex(index);
        if (isChinaDownloadRoute()) {
            appendUrls(values, NDK_FALLBACK_URLS[safeIndex]);
            values.add(NDK_URLS[safeIndex]);
        } else {
            values.add(NDK_URLS[safeIndex]);
            appendUrls(values, NDK_FALLBACK_URLS[safeIndex]);
        }
        return values.toArray(new String[0]);
    }

    public String[] getGradleDownloadCandidates(String gradleVersion) {
        String version = safeText(gradleVersion);
        if (version.length() == 0) {
            version = DEFAULT_GRADLE_VERSION;
        }
        String official = "https://services.gradle.org/distributions/gradle-" + version + "-bin.zip";
        String mirror = "https://mirrors.cloud.tencent.com/gradle/gradle-" + version + "-bin.zip";
        String backup = "https://downloads.gradle.org/distributions/gradle-" + version + "-bin.zip";
        LinkedHashSet<String> values = new LinkedHashSet<String>();
        if (isChinaDownloadRoute()) {
            values.add(mirror);
            values.add(official);
            values.add(backup);
        } else {
            values.add(official);
            values.add(mirror);
            values.add(backup);
        }
        return values.toArray(new String[0]);
    }

    public boolean isChinaDownloadRoute() {
        return DOWNLOAD_ROUTE_CHINA.equals(loadDownloadRoute());
    }

    public String getDownloadRouteDisplayName() {
        return isChinaDownloadRoute() ? "国内路线（镜像优先）" : "国外路线（官方优先）";
    }

    public String getDownloadRegionLabel() {
        return getDownloadRouteDisplayName();
    }

    public String buildToolchainRecommendationSummary(File projectDir) {
        String compileSdk = recommendCompileSdk(projectDir);
        String buildTools = recommendBuildToolsVersion(projectDir);
        String gradleVersion = recommendGradleVersion(projectDir);
        return "推荐环境："
            + getRecommendedJdkName(projectDir)
            + " / "
            + getRecommendedNdkName(projectDir)
            + " / SDK android-" + compileSdk
            + " / Build-Tools " + buildTools
            + " / Gradle " + gradleVersion
            + " / " + getDownloadRegionLabel();
    }

    public String getRepositoryWrapperJarUrl() {
        return REPOSITORY_RAW_BASE + "/gradle/wrapper/gradle-wrapper.jar";
    }

    public String getRepositoryWrapperPropertiesUrl() {
        return REPOSITORY_RAW_BASE + "/gradle/wrapper/gradle-wrapper.properties";
    }

    public String getOfficialWrapperJarUrl(String gradleVersion) {
        return "https://raw.githubusercontent.com/gradle/gradle/v" + normalizeGradleTag(gradleVersion) + "/gradle/wrapper/gradle-wrapper.jar";
    }

    public String getOfficialWrapperPropertiesUrl(String gradleVersion) {
        return "https://raw.githubusercontent.com/gradle/gradle/v" + normalizeGradleTag(gradleVersion) + "/gradle/wrapper/gradle-wrapper.properties";
    }

    public String getGradleDistributionSha256(String gradleVersion) {
        String version = safeText(gradleVersion);
        if (version.length() == 0) {
            version = DEFAULT_GRADLE_VERSION;
        }
        String value = GRADLE_DISTRIBUTION_SHA256.get(version);
        return value == null ? "" : value;
    }

    public String getGradleWrapperSha256(String gradleVersion) {
        String version = safeText(gradleVersion);
        if (version.length() == 0) {
            version = DEFAULT_GRADLE_VERSION;
        }
        String value = GRADLE_WRAPPER_SHA256.get(version);
        return value == null ? "" : value;
    }

    public String buildWrapperPropertiesContent(String gradleVersion) {
        String version = safeText(gradleVersion);
        if (version.length() == 0) {
            version = DEFAULT_GRADLE_VERSION;
        }
        String distributionSha256 = getGradleDistributionSha256(version);
        String distributionUrl = getPreferredGradleDistributionUrl(version);
        return "distributionBase=GRADLE_USER_HOME\n"
            + "distributionPath=wrapper/dists\n"
            + "distributionUrl=" + escapePropertiesUrl(distributionUrl) + "\n"
            + (distributionSha256.length() == 0 ? "" : "distributionSha256Sum=" + distributionSha256 + "\n")
            + "networkTimeout=10000\n"
            + "validateDistributionUrl=true\n"
            + "zipStoreBase=GRADLE_USER_HOME\n"
            + "zipStorePath=wrapper/dists\n";
    }

    public String getPreferredGradleDistributionUrl(String gradleVersion) {
        String version = safeText(gradleVersion);
        if (version.length() == 0) {
            version = DEFAULT_GRADLE_VERSION;
        }
        if (isChinaDownloadRoute()) {
            return "https://mirrors.cloud.tencent.com/gradle/gradle-" + version + "-bin.zip";
        }
        return "https://services.gradle.org/distributions/gradle-" + version + "-bin.zip";
    }

    private String sanitizeDirName(String name) {
        return name.replace(" ", "_")
            .replace("(", "")
            .replace(")", "")
            .replace("，", "_")
            .replace("/", "_");
    }

    private String normalizeDownloadRoute(String route) {
        String value = safeText(route).toLowerCase(Locale.US);
        if (DOWNLOAD_ROUTE_GLOBAL.equals(value)) {
            return DOWNLOAD_ROUTE_GLOBAL;
        }
        return DOWNLOAD_ROUTE_CHINA;
    }

    private int parseIntSafe(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return -1;
        }
    }

    private String escapePropertiesUrl(String url) {
        return safeText(url).replace(":", "\\:");
    }

    private int normalizeJdkIndex(int index) {
        if (index < 0 || index >= JDK_NAMES.length) {
            return DEFAULT_JDK_INDEX;
        }
        return index;
    }

    private int normalizeNdkIndex(int index) {
        if (index < 0 || index >= NDK_NAMES.length) {
            return DEFAULT_NDK_INDEX;
        }
        return index;
    }

    private LinkedHashMap<String, String> readRegistry(String key) {
        LinkedHashMap<String, String> registry = new LinkedHashMap<String, String>();
        String raw = sharedPreferences.getString(key, "");
        if (raw == null || raw.trim().length() == 0) {
            return registry;
        }
        try {
            JSONObject jsonObject = new JSONObject(raw);
            Iterator<String> iterator = jsonObject.keys();
            while (iterator.hasNext()) {
                String name = safeText(iterator.next());
                String dir = safeText(jsonObject.optString(name, ""));
                if (name.length() == 0 || dir.length() == 0) {
                    continue;
                }
                registry.put(name, dir);
            }
        } catch (Exception e) {
        }
        return registry;
    }

    private String encodeRegistry(Map<String, String> registry) {
        JSONObject jsonObject = new JSONObject();
        if (registry != null) {
            for (Map.Entry<String, String> entry : registry.entrySet()) {
                String name = safeText(entry.getKey());
                String dir = safeText(entry.getValue());
                if (name.length() == 0 || dir.length() == 0) {
                    continue;
                }
                try {
                    jsonObject.put(name, dir);
                } catch (Exception e) {
                }
            }
        }
        return jsonObject.toString();
    }

    private void appendUrls(LinkedHashSet<String> values, String[] candidates) {
        if (candidates == null) {
            return;
        }
        for (int i = 0; i < candidates.length; i++) {
            String value = safeText(candidates[i]);
            if (value.length() > 0) {
                values.add(value);
            }
        }
    }

    private String findGradleVersion(File projectDir) {
        File wrapperProperties = new File(projectDir, "gradle/wrapper/gradle-wrapper.properties");
        if (!wrapperProperties.exists()) {
            return "";
        }
        try {
            String content = readText(wrapperProperties);
            Matcher matcher = Pattern.compile("gradle-([0-9][0-9A-Za-z.\\-]*)-(?:bin|all)\\.zip").matcher(content);
            if (matcher.find()) {
                return safeText(matcher.group(1));
            }
        } catch (Exception e) {
        }
        return "";
    }

    private String findAgpVersion(File projectDir) {
        String fromRoot = extractFromFile(new File(projectDir, "build.gradle"), "com\\.android\\.tools\\.build:gradle:([0-9][0-9A-Za-z.\\-]*)");
        if (fromRoot.length() > 0) {
            return fromRoot;
        }
        fromRoot = extractFromFile(new File(projectDir, "build.gradle.kts"), "com\\.android\\.tools\\.build:gradle:([0-9][0-9A-Za-z.\\-]*)");
        if (fromRoot.length() > 0) {
            return fromRoot;
        }
        String pluginVersion = extractFromFile(new File(projectDir, "settings.gradle"), "id\\s*[\"']com\\.android\\.(?:application|library)[\"']\\s*version\\s*[\"']([0-9][0-9A-Za-z.\\-]*)[\"']");
        if (pluginVersion.length() > 0) {
            return pluginVersion;
        }
        return extractFromFile(new File(projectDir, "settings.gradle.kts"), "id\\s*\\([\"']com\\.android\\.(?:application|library)[\"']\\)\\s*version\\s*[\"']([0-9][0-9A-Za-z.\\-]*)[\"']");
    }

    private String findSourceCompatibility(File projectDir) {
        String value = extractFromGradle(projectDir, "sourceCompatibility\\s*(?:=\\s*)?JavaVersion\\.VERSION_(\\d+_\\d+|\\d+)");
        if (value.length() > 0) {
            return value;
        }
        return extractFromGradle(projectDir, "sourceCompatibility\\s*(?:=\\s*)?[\"']?([0-9][0-9A-Za-z._-]*)[\"']?");
    }

    private String findNdkVersion(File projectDir) {
        return extractFromGradle(projectDir, "ndkVersion\\s*(?:=\\s*)?[\"']([^\"']+)[\"']");
    }

    private String extractFromGradle(File projectDir, String regex) {
        String value = extractFromFile(new File(projectDir, "app/build.gradle"), regex);
        if (value.length() > 0) {
            return value;
        }
        return extractFromFile(new File(projectDir, "app/build.gradle.kts"), regex);
    }

    private String extractFromFile(File file, String regex) {
        if (file == null || !file.exists()) {
            return "";
        }
        try {
            Matcher matcher = Pattern.compile(regex).matcher(readText(file));
            if (matcher.find()) {
                return safeText(matcher.group(1));
            }
        } catch (Exception e) {
        }
        return "";
    }

    private int parseJavaLevel(String value) {
        String clean = safeText(value).replace("VERSION_", "").replace("_", ".");
        if ("1.8".equals(clean)) {
            return 8;
        }
        int major = majorOfVersion(clean);
        return major <= 0 ? 0 : major;
    }

    private int majorOfVersion(String value) {
        String clean = safeText(value);
        if (clean.length() == 0) {
            return 0;
        }
        Matcher matcher = Pattern.compile("^(\\d+)").matcher(clean);
        if (!matcher.find()) {
            return 0;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (Exception e) {
            return 0;
        }
    }

    private int minorOfVersion(String value) {
        String clean = safeText(value);
        Matcher matcher = Pattern.compile("^\\d+\\.(\\d+)").matcher(clean);
        if (!matcher.find()) {
            return 0;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (Exception e) {
            return 0;
        }
    }

    private String normalizeGradleTag(String gradleVersion) {
        String version = safeText(gradleVersion);
        if (version.length() == 0) {
            version = DEFAULT_GRADLE_VERSION;
        }
        if (Pattern.compile("^\\d+\\.\\d+$").matcher(version).find()) {
            return version + ".0";
        }
        return version;
    }

    private String readText(File file) throws Exception {
        FileInputStream inputStream = null;
        try {
            inputStream = new FileInputStream(file);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
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

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }
}
