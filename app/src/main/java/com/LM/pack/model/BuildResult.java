package com.LM.pack.model;

import java.util.ArrayList;

public class BuildResult {

    private final boolean success;
    private final int exitCode;
    private final String message;
    private final String apkPath;
    private final ArrayList<BuildIssue> issues;

    public BuildResult(boolean success, int exitCode, String message, String apkPath, ArrayList<BuildIssue> issues) {
        this.success = success;
        this.exitCode = exitCode;
        this.message = message;
        this.apkPath = apkPath;
        this.issues = issues == null ? new ArrayList<BuildIssue>() : issues;
    }

    public boolean isSuccess() {
        return success;
    }

    public int getExitCode() {
        return exitCode;
    }

    public String getMessage() {
        return message;
    }

    public String getApkPath() {
        return apkPath;
    }

    public ArrayList<BuildIssue> getIssues() {
        return issues;
    }
}
