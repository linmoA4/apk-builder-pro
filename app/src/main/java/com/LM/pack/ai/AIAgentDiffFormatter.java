package com.LM.pack.ai;

import java.util.ArrayList;

public final class AIAgentDiffFormatter {

    private static final int MAX_RENDER_LINES = 120;
    private static final int MAX_DP_CELLS = 240000;

    private AIAgentDiffFormatter() {
    }

    public static String buildHtml(String oldContent, String newContent) {
        String before = oldContent == null ? "" : oldContent;
        String after = newContent == null ? "" : newContent;
        if (before.equals(after)) {
            return "<font color=\"#94A3B8\">没有可见文本改动</font>";
        }
        String[] oldLines = splitLines(before);
        String[] newLines = splitLines(after);
        long cellCount = (long) oldLines.length * (long) newLines.length;
        if (cellCount > MAX_DP_CELLS) {
            return buildSummaryHtml(oldLines.length, newLines.length);
        }
        int[][] dp = buildLcsTable(oldLines, newLines);
        ArrayList<DiffLine> lines = rebuildDiff(oldLines, newLines, dp);
        return renderDiff(lines, oldLines.length, newLines.length);
    }

    private static String[] splitLines(String content) {
        return content.split("\n", -1);
    }

    private static int[][] buildLcsTable(String[] oldLines, String[] newLines) {
        int[][] dp = new int[oldLines.length + 1][newLines.length + 1];
        for (int i = oldLines.length - 1; i >= 0; i--) {
            for (int j = newLines.length - 1; j >= 0; j--) {
                if (oldLines[i].equals(newLines[j])) {
                    dp[i][j] = dp[i + 1][j + 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i + 1][j], dp[i][j + 1]);
                }
            }
        }
        return dp;
    }

    private static ArrayList<DiffLine> rebuildDiff(String[] oldLines, String[] newLines, int[][] dp) {
        ArrayList<DiffLine> result = new ArrayList<DiffLine>();
        int i = 0;
        int j = 0;
        while (i < oldLines.length && j < newLines.length) {
            if (oldLines[i].equals(newLines[j])) {
                result.add(new DiffLine(' ', oldLines[i]));
                i++;
                j++;
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                result.add(new DiffLine('-', oldLines[i]));
                i++;
            } else {
                result.add(new DiffLine('+', newLines[j]));
                j++;
            }
        }
        while (i < oldLines.length) {
            result.add(new DiffLine('-', oldLines[i]));
            i++;
        }
        while (j < newLines.length) {
            result.add(new DiffLine('+', newLines[j]));
            j++;
        }
        return result;
    }

    private static String renderDiff(ArrayList<DiffLine> lines, int oldCount, int newCount) {
        StringBuilder builder = new StringBuilder();
        int rendered = 0;
        int added = 0;
        int removed = 0;
        for (int i = 0; i < lines.size(); i++) {
            DiffLine line = lines.get(i);
            if (line.kind == '+') {
                added++;
            } else if (line.kind == '-') {
                removed++;
            }
        }
        builder.append("<b>变更摘要</b>：新增 ").append(added).append(" 行，删除 ").append(removed).append(" 行");
        for (int i = 0; i < lines.size(); i++) {
            DiffLine line = lines.get(i);
            if (line.kind == ' ') {
                continue;
            }
            if (rendered >= MAX_RENDER_LINES) {
                builder.append("<br/><font color=\"#94A3B8\">其余变更已折叠，原文件 ")
                    .append(oldCount)
                    .append(" 行，新文件 ")
                    .append(newCount)
                    .append(" 行</font>");
                break;
            }
            builder.append("<br/>");
            if (line.kind == '+') {
                builder.append("<font color=\"#22C55E\">+ ")
                    .append(escapeHtml(line.text))
                    .append("</font>");
            } else {
                builder.append("<font color=\"#EF4444\">- ")
                    .append(escapeHtml(line.text))
                    .append("</font>");
            }
            rendered++;
        }
        return builder.toString();
    }

    private static String buildSummaryHtml(int oldCount, int newCount) {
        return "<b>变更摘要</b>：文本过长，已省略逐行 diff。原文件 "
            + oldCount
            + " 行，新文件 "
            + newCount
            + " 行。";
    }

    public static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }

    private static class DiffLine {
        final char kind;
        final String text;

        DiffLine(char kind, String text) {
            this.kind = kind;
            this.text = text == null ? "" : text;
        }
    }
}
