package com.LM.pack.model;

public class AIAgentEvent {

    public static final String TYPE_STATUS = "status";
    public static final String TYPE_THOUGHT = "thought";
    public static final String TYPE_READ = "read";
    public static final String TYPE_COMMAND = "command";
    public static final String TYPE_WRITE = "write";
    public static final String TYPE_DELETE = "delete";
    public static final String TYPE_ERROR = "error";
    public static final String TYPE_FINISH = "finish";

    private final String type;
    private final String title;
    private final String htmlBody;
    private final String plainSummary;
    private final long timestampMs;

    public AIAgentEvent(String type, String title, String htmlBody, String plainSummary) {
        this(type, title, htmlBody, plainSummary, System.currentTimeMillis());
    }

    public AIAgentEvent(String type, String title, String htmlBody, String plainSummary, long timestampMs) {
        this.type = type == null ? TYPE_STATUS : type;
        this.title = title == null ? "" : title;
        this.htmlBody = htmlBody == null ? "" : htmlBody;
        this.plainSummary = plainSummary == null ? "" : plainSummary;
        this.timestampMs = timestampMs;
    }

    public String getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getHtmlBody() {
        return htmlBody;
    }

    public String getPlainSummary() {
        return plainSummary;
    }

    public long getTimestampMs() {
        return timestampMs;
    }
}
