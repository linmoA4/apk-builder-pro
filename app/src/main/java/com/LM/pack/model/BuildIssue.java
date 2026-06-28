package com.LM.pack.model;

public class BuildIssue {

    private final String severity;
    private final String filePath;
    private final int lineNumber;
    private final String message;
    private final String suggestion;

    public BuildIssue(String filePath, int lineNumber, String message, String suggestion) {
        this("error", filePath, lineNumber, message, suggestion);
    }

    public BuildIssue(String severity, String filePath, int lineNumber, String message, String suggestion) {
        this.severity = normalizeSeverity(severity);
        this.filePath = filePath;
        this.lineNumber = lineNumber;
        this.message = message;
        this.suggestion = suggestion;
    }

    public String getSeverity() {
        return severity;
    }

    public String getFilePath() {
        return filePath;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public String getMessage() {
        return message;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public String getDisplayText() {
        String prefix = buildSeverityPrefix();
        if (lineNumber > 0) {
            return prefix + filePath + ":" + lineNumber + "  " + message;
        }
        return prefix + filePath + "  " + message;
    }

    private String buildSeverityPrefix() {
        if ("warning".equals(severity)) {
            return "[警告] ";
        }
        if ("info".equals(severity)) {
            return "[提示] ";
        }
        return "[错误] ";
    }

    private String normalizeSeverity(String value) {
        if (value == null) {
            return "error";
        }
        String lower = value.trim().toLowerCase();
        if ("warning".equals(lower) || "warn".equals(lower)) {
            return "warning";
        }
        if ("info".equals(lower) || "notice".equals(lower) || "hint".equals(lower)) {
            return "info";
        }
        return "error";
    }
}
