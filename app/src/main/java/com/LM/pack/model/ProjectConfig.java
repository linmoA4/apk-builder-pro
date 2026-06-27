package com.LM.pack.model;

public class ProjectConfig {

    private final String packageName;
    private final String appName;
    private final int minSdk;
    private final int targetSdk;
    private final int versionCode;
    private final String versionName;
    private final String iconSourcePath;
    private final String splashSourcePath;

    public ProjectConfig(
        String packageName,
        String appName,
        int minSdk,
        int targetSdk,
        int versionCode,
        String versionName,
        String iconSourcePath,
        String splashSourcePath
    ) {
        this.packageName = packageName;
        this.appName = appName;
        this.minSdk = minSdk;
        this.targetSdk = targetSdk;
        this.versionCode = versionCode;
        this.versionName = versionName;
        this.iconSourcePath = iconSourcePath;
        this.splashSourcePath = splashSourcePath;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getAppName() {
        return appName;
    }

    public int getMinSdk() {
        return minSdk;
    }

    public int getTargetSdk() {
        return targetSdk;
    }

    public int getVersionCode() {
        return versionCode;
    }

    public String getVersionName() {
        return versionName;
    }

    public String getIconSourcePath() {
        return iconSourcePath;
    }

    public String getSplashSourcePath() {
        return splashSourcePath;
    }

    public String getPackagePath() {
        return packageName.replace('.', '/');
    }
}
