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

    public static final int DEFAULT_JDK_INDEX = 3;
    public static final int DEFAULT_NDK_INDEX = 0;
    public static final int EMBEDDED_JDK_INDEX = DEFAULT_JDK_INDEX;
    public static final int EMBEDDED_NDK_INDEX = DEFAULT_NDK_INDEX;

    public static final String SDK_DISPLAY_NAME = "Android SDK Command-line Tools";
    public static final String DEFAULT_GRADLE_VERSION = "8.7";
    public static final String REPOSITORY_RAW_BASE = "https://raw.githubusercontent.com/linmoA4/apk-builder-pro/main";

    public static final String[] JDK_NAMES = {
        "JDK 8 (长期支持版)",
        "JDK 11 (长期支持版)",
        "JDK 17 (长期支持版)",
        "JDK 21 (当前推荐版)",
        "JDK 25 (前沿版本)",
        "JDK 26 (实验版本)"
    };

    public static final String[] JDK_URLS = {
        "https://aka.ms/download-jdk/microsoft-jdk-8-linux-x64.tar.gz",
        "https://aka.ms/download-jdk/microsoft-jdk-11-linux-x64.tar.gz",
        "https://aka.ms/download-jdk/microsoft-jdk-17-linux-x64.tar.gz",
        "https://aka.ms/download-jdk/microsoft-jdk-21-linux-x64.tar.gz",
        "https://aka.ms/download-jdk/microsoft-jdk-25-linux-x64.tar.gz",
        "https://aka.ms/download-jdk/microsoft-jdk-26-linux-x64.tar.gz"
    };

    public static final String[][] JDK_FALLBACK_URLS = {
        {"https://aka.ms/download-jdk/microsoft-jdk-8-linux-x64.tar.gz"},
        {"https://aka.ms/download-jdk/microsoft-jdk-11-linux-x64.tar.gz"},
        {"https://aka.ms/download-jdk/microsoft-jdk-17-linux-x64.tar.gz"},
        {"https://aka.ms/download-jdk/microsoft-jdk-21-linux-x64.tar.gz"},
        {"https://aka.ms/download-jdk/microsoft-jdk-25-linux-x64.tar.gz"},
        {"https://aka.ms/download-jdk/microsoft-jdk-26-linux-x64.tar.gz"}
    };

    public static final String[] NDK_NAMES = {
        "NDK r27c (稳定版，推荐)",
        "NDK r28c (较新稳定版)",
        "NDK r29 Beta 3 (测试版)"
    };

    public static final String[] NDK_URLS = {
        "https://dl.google.com/android/repository/android-ndk-r27c-linux.zip",
        "https://dl.google.com/android/repository/android-ndk-r28c-linux.zip",
        "https://dl.google.com/android/repository/android-ndk-r29-beta3-linux.zip"
    };

    public static final String[][] NDK_FALLBACK_URLS = {
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

    public static final String[] SDK_PRIMARY_URLS = {
        "https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip",
        "https://googledownloads.cn/android/repository/commandlinetools-linux-13114758_latest.zip"
    };

    public static final String[] SDK_FALLBACK_URLS = {
        "https://dl.google.com/android/repository/commandlinetools-linux-latest.zip",
        "https://googledownloads.cn/android/repository/commandlinetools-linux-latest.zip",
        "https://redirector.gvt1.com/edgedl/android/repository/commandlinetools-linux-13114758_latest.zip"
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

        if (javaLevel >= 21 || gradleMajor >= 9 || (gradleMajor == 8 && gradleMinor >= 5) || agpMajor >= 9 || (agpMajor == 8 && agpMinor >= 2)) {
            return 3;
        }
        if (javaLevel >= 17 || gradleMajor >= 7 || agpMajor >= 7) {
            return 2;
        }
        if (javaLevel >= 11 || gradleMajor >= 5 || agpMajor >= 4) {
            return 1;
        }
        return 0;
    }

    public int recommendNdkIndex(File projectDir) {
        String ndkVersion = findNdkVersion(projectDir);
        if (ndkVersion.startsWith("29") || ndkVersion.contains("r29")) {
            return 2;
        }
        if (ndkVersion.startsWith("28") || ndkVersion.contains("r28")) {
            return 1;
        }
        return 0;
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
            return compileSdk + ".0.0";
        }
        return "36.0.0";
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
        if (preferChinaMirror()) {
            values.add(SDK_PRIMARY_URLS[1]);
            values.add(SDK_PRIMARY_URLS[0]);
        } else {
            values.add(SDK_PRIMARY_URLS[0]);
            values.add(SDK_PRIMARY_URLS[1]);
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
        if (preferChinaMirror()) {
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
        if (preferChinaMirror()) {
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

    public boolean preferChinaMirror() {
        Locale locale = Locale.getDefault();
        String country = safeText(locale.getCountry()).toUpperCase(Locale.US);
        String language = safeText(locale.getLanguage()).toLowerCase(Locale.US);
        return "CN".equals(country) || "zh".equals(language);
    }

    public String getDownloadRegionLabel() {
        return preferChinaMirror() ? "国内镜像优先" : "国外官方源优先";
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

    public String buildWrapperPropertiesContent(String gradleVersion) {
        String version = safeText(gradleVersion);
        if (version.length() == 0) {
            version = DEFAULT_GRADLE_VERSION;
        }
        return "distributionBase=GRADLE_USER_HOME\n"
            + "distributionPath=wrapper/dists\n"
            + "distributionUrl=https\\://services.gradle.org/distributions/gradle-" + version + "-bin.zip\n"
            + "networkTimeout=10000\n"
            + "validateDistributionUrl=true\n"
            + "zipStoreBase=GRADLE_USER_HOME\n"
            + "zipStorePath=wrapper/dists\n";
    }

    private String sanitizeDirName(String name) {
        return name.replace(" ", "_")
            .replace("(", "")
            .replace(")", "")
            .replace("，", "_")
            .replace("/", "_");
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
