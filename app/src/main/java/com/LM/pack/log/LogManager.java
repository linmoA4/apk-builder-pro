package com.LM.pack.log;

import android.graphics.Color;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

public class LogManager {

    private static final int COLOR_INFO = Color.parseColor("#4CAF50");
    private static final int COLOR_WARN = Color.parseColor("#FFC107");
    private static final int COLOR_ERROR = Color.parseColor("#F44336");
    private static final int COLOR_HIGHLIGHT = Color.parseColor("#00BCD4");

    private final TextView tvLogs;
    private final ScrollView logScrollView;
    private final SpannableStringBuilder logBuilder = new SpannableStringBuilder();

    public LogManager(TextView tvLogs, ScrollView logScrollView) {
        this.tvLogs = tvLogs;
        this.logScrollView = logScrollView;
    }

    public void clear() {
        logBuilder.clear();
        refreshLogView();
    }

    public void appendLogLine(String level, String message) {
        int levelColor = getColorForLevel(level);
        appendColoredText("[" + level + "] ", levelColor);
        appendColoredText(message + "\n", levelColor);
        refreshLogView();
    }

    public void appendKeyValue(String level, String key, String value) {
        int levelColor = getColorForLevel(level);
        appendColoredText("[" + level + "] ", levelColor);
        appendColoredText(key + ": ", levelColor);
        appendColoredText(value + "\n", COLOR_HIGHLIGHT);
        refreshLogView();
    }

    public void appendCatalogGroup(String tag, String[] items) {
        appendLogLine("INFO", "分类：" + tag);
        for (int i = 0; i < items.length; i++) {
            appendCatalogItem(tag, items[i]);
        }
    }

    public void appendCatalogItem(String tag, String item) {
        int splitIndex = item.indexOf("\n");
        if (splitIndex < 0) {
            appendKeyValue("INFO", tag, item);
            return;
        }
        String title = item.substring(0, splitIndex);
        String value = item.substring(splitIndex + 1);
        appendKeyValue("INFO", tag + " - " + title, value);
    }

    private int getColorForLevel(String level) {
        if ("WARN".equals(level)) {
            return COLOR_WARN;
        }
        if ("ERROR".equals(level)) {
            return COLOR_ERROR;
        }
        return COLOR_INFO;
    }

    private void appendColoredText(String text, int color) {
        int start = logBuilder.length();
        logBuilder.append(text);
        int end = logBuilder.length();
        logBuilder.setSpan(new ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void refreshLogView() {
        tvLogs.setText(logBuilder);
        logScrollView.post(new Runnable() {
            @Override
            public void run() {
                logScrollView.fullScroll(View.FOCUS_DOWN);
            }
        });
    }
}
