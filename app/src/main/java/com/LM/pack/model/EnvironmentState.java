package com.LM.pack.model;

public class EnvironmentState {

    private final String installedJdkName;
    private final String installedJdkDir;
    private final String androidSdkDir;
    private final String installedNdkName;
    private final String installedNdkDir;

    public EnvironmentState(
        String installedJdkName,
        String installedJdkDir,
        String androidSdkDir,
        String installedNdkName,
        String installedNdkDir
    ) {
        this.installedJdkName = installedJdkName;
        this.installedJdkDir = installedJdkDir;
        this.androidSdkDir = androidSdkDir;
        this.installedNdkName = installedNdkName;
        this.installedNdkDir = installedNdkDir;
    }

    public String getInstalledJdkName() {
        return installedJdkName;
    }

    public String getInstalledJdkDir() {
        return installedJdkDir;
    }

    public String getAndroidSdkDir() {
        return androidSdkDir;
    }

    public String getInstalledNdkName() {
        return installedNdkName;
    }

    public String getInstalledNdkDir() {
        return installedNdkDir;
    }
}
