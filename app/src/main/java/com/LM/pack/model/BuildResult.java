package com.LM.pack.model;

public class BuildResult {

    private final boolean success;
    private final int exitCode;
    private final String message;

    public BuildResult(boolean success, int exitCode, String message) {
        this.success = success;
        this.exitCode = exitCode;
        this.message = message;
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
}
