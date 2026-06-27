package com.LM.pack.model;

public class EnvironmentState {

    private final String installedJdkName;
    private final String installedJdkDir;
    private final String installedNdkName;
    private final String installedNdkDir;

    public EnvironmentState(String installedJdkName, String installedJdkDir, String installedNdkName, String installedNdkDir) {
        this.installedJdkName = installedJdkName;
        this.installedJdkDir = installedJdkDir;
        this.installedNdkName = installedNdkName;
        this.installedNdkDir = installedNdkDir;
    }

    public String getInstalledJdkName() {
        return installedJdkName;
    }

    public String getInstalledJdkDir() {
        return installedJdkDir;
    }

    public String getInstalledNdkName() {
        return installedNdkName;
    }

    public String getInstalledNdkDir() {
        return installedNdkDir;
    }
}
