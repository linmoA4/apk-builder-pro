package com.LM.pack.model;

public class ProjectConfig {

    private final String packageName;
    private final String appName;
    private final int minSdk;
    private final int targetSdk;

    public ProjectConfig(String packageName, String appName, int minSdk, int targetSdk) {
        this.packageName = packageName;
        this.appName = appName;
        this.minSdk = minSdk;
        this.targetSdk = targetSdk;
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

    public String getPackagePath() {
        return packageName.replace('.', '/');
    }
}
