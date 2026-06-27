package com.LM.pack.env;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Environment;
import com.LM.pack.model.EnvironmentState;
import java.io.File;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
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

    private final SharedPreferences sharedPreferences;
    private final File baseDir;

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
        int index = sharedPreferences.getInt(KEY_SELECTED_JDK_INDEX, 3);
        if (index < 0 || index >= JDK_NAMES.length) {
            return 3;
        }
        return index;
    }

    public int loadSelectedNdkIndex() {
        int index = sharedPreferences.getInt(KEY_SELECTED_NDK_INDEX, 0);
        if (index < 0 || index >= NDK_NAMES.length) {
            return 0;
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

    private int normalizeJdkIndex(int index) {
        if (index < 0 || index >= JDK_NAMES.length) {
            return 3;
        }
        return index;
    }

    private int normalizeNdkIndex(int index) {
        if (index < 0 || index >= NDK_NAMES.length) {
            return 0;
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

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }
}
