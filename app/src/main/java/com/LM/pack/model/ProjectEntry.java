package com.LM.pack.model;

public class ProjectEntry {

    private final String projectName;
    private final String packageName;
    private final String projectDir;
    private final String iconPath;
    private final String mode;
    private final String versionName;

    public ProjectEntry(
        String projectName,
        String packageName,
        String projectDir,
        String iconPath,
        String mode,
        String versionName
    ) {
        this.projectName = projectName;
        this.packageName = packageName;
        this.projectDir = projectDir;
        this.iconPath = iconPath;
        this.mode = mode;
        this.versionName = versionName;
    }

    public String getProjectName() {
        return projectName;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getProjectDir() {
        return projectDir;
    }

    public String getIconPath() {
        return iconPath;
    }

    public String getMode() {
        return mode;
    }

    public String getVersionName() {
        return versionName;
    }
}
