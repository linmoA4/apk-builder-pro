package com.LM.pack.model;

public class BuildIssue {

    private final String filePath;
    private final int lineNumber;
    private final String message;
    private final String suggestion;

    public BuildIssue(String filePath, int lineNumber, String message, String suggestion) {
        this.filePath = filePath;
        this.lineNumber = lineNumber;
        this.message = message;
        this.suggestion = suggestion;
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
        if (lineNumber > 0) {
            return filePath + ":" + lineNumber + "  " + message;
        }
        return filePath + "  " + message;
    }
}
