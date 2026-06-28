package com.LM.pack.build;

import com.LM.pack.model.BuildIssue;
import java.util.ArrayList;
import java.util.concurrent.CancellationException;

public class BracketCounter {

    public interface CancellationSignal {
        boolean isCancelled();
    }

    public void validateJavaSyntax(String filePath, String content, ArrayList<BuildIssue> issues, CancellationSignal cancellationSignal) {
        int braceDepth = 0;
        int parenDepth = 0;
        int bracketDepth = 0;
        boolean inSingleLineComment = false;
        boolean inMultiLineComment = false;
        boolean inString = false;
        boolean inChar = false;
        char prev = 0;
        String[] lines = content.split("\n", -1);
        for (int lineIdx = 0; lineIdx < lines.length; lineIdx++) {
            throwIfCancelled(cancellationSignal);
            String line = lines[lineIdx];
            int lineNum = lineIdx + 1;
            inSingleLineComment = false;
            for (int col = 0; col < line.length(); col++) {
                throwIfCancelled(cancellationSignal);
                char ch = line.charAt(col);
                if (inMultiLineComment) {
                    if (ch == '/' && prev == '*') {
                        inMultiLineComment = false;
                    }
                } else if (inSingleLineComment) {
                    continue;
                } else if (inString) {
                    if (ch == '"' && prev != '\\') {
                        inString = false;
                    }
                } else if (inChar) {
                    if (ch == '\'' && prev != '\\') {
                        inChar = false;
                    }
                } else if (ch == '/' && prev == '/') {
                    inSingleLineComment = true;
                } else if (ch == '*' && prev == '/') {
                    inMultiLineComment = true;
                } else if (ch == '"') {
                    inString = true;
                } else if (ch == '\'') {
                    inChar = true;
                } else if (ch == '{') {
                    braceDepth++;
                } else if (ch == '}') {
                    braceDepth--;
                    if (braceDepth < 0) {
                        issues.add(new BuildIssue(filePath, lineNum, "多余的右大括号 '}'", "删除或修正多余的大括号。"));
                        braceDepth = 0;
                    }
                } else if (ch == '(') {
                    parenDepth++;
                } else if (ch == ')') {
                    parenDepth--;
                    if (parenDepth < 0) {
                        issues.add(new BuildIssue(filePath, lineNum, "多余的右圆括号 ')'", "删除或修正多余的圆括号。"));
                        parenDepth = 0;
                    }
                } else if (ch == '[') {
                    bracketDepth++;
                } else if (ch == ']') {
                    bracketDepth--;
                    if (bracketDepth < 0) {
                        issues.add(new BuildIssue(filePath, lineNum, "多余的右方括号 ']'", "删除或修正多余的方括号。"));
                        bracketDepth = 0;
                    }
                }
                prev = ch;
            }
            if (!inMultiLineComment && !inSingleLineComment) {
                String trimmed = line.trim();
                if (trimmed.length() > 0
                    && !trimmed.equals("{")
                    && !trimmed.equals("}")
                    && !trimmed.startsWith("@")
                    && !trimmed.startsWith("//")
                    && !trimmed.startsWith("/*")
                    && !trimmed.equals("*/")
                    && !trimmed.endsWith("{")
                    && !trimmed.endsWith("}")
                    && !trimmed.endsWith(";")
                    && !trimmed.endsWith(",")
                    && !trimmed.endsWith(":")
                    && !trimmed.startsWith("package ")
                    && !trimmed.startsWith("import ")
                    && !trimmed.startsWith("if (")
                    && !trimmed.startsWith("for (")
                    && !trimmed.startsWith("while (")
                    && !trimmed.startsWith("try")
                    && !trimmed.startsWith("catch")
                    && !trimmed.startsWith("else")
                    && !trimmed.startsWith("do")
                    && !trimmed.matches(".*\\s*\\*.*")) {
                    issues.add(new BuildIssue(filePath, lineNum, "可能缺少分号 ';'", "在语句末尾添加分号。"));
                }
            }
        }
        if (braceDepth != 0) {
            issues.add(new BuildIssue(filePath, -1, "大括号不匹配，差 " + braceDepth + " 个（" + (braceDepth > 0 ? "多" : "少") + "）", "检查 {} 是否成对出现。"));
        }
        if (parenDepth != 0) {
            issues.add(new BuildIssue(filePath, -1, "圆括号不匹配，差 " + parenDepth + " 个（" + (parenDepth > 0 ? "多" : "少") + "）", "检查 () 是否成对出现。"));
        }
        if (bracketDepth != 0) {
            issues.add(new BuildIssue(filePath, -1, "方括号不匹配，差 " + bracketDepth + " 个（" + (bracketDepth > 0 ? "多" : "少") + "）", "检查 [] 是否成对出现。"));
        }
    }

    public void validateKotlinSyntax(String filePath, String content, ArrayList<BuildIssue> issues, CancellationSignal cancellationSignal) {
        int braceDepth = 0;
        int parenDepth = 0;
        int bracketDepth = 0;
        boolean inSingleLineComment = false;
        boolean inMultiLineComment = false;
        boolean inString = false;
        boolean inRawString = false;
        int rawStringHash = 0;
        boolean inChar = false;
        char prev = 0;
        String[] lines = content.split("\n", -1);
        for (int lineIdx = 0; lineIdx < lines.length; lineIdx++) {
            throwIfCancelled(cancellationSignal);
            String line = lines[lineIdx];
            int lineNum = lineIdx + 1;
            inSingleLineComment = false;
            for (int col = 0; col < line.length(); col++) {
                throwIfCancelled(cancellationSignal);
                char ch = line.charAt(col);
                if (inMultiLineComment) {
                    if (ch == '/' && prev == '*') {
                        inMultiLineComment = false;
                    }
                } else if (inSingleLineComment) {
                    continue;
                } else if (inRawString) {
                    if (ch == '"' && col + rawStringHash < line.length()) {
                        boolean match = true;
                        for (int i = 1; i <= rawStringHash; i++) {
                            if (line.charAt(col + i) != '"') {
                                match = false;
                                break;
                            }
                        }
                        if (match) {
                            inRawString = false;
                            rawStringHash = 0;
                        }
                    }
                } else if (inString) {
                    if (ch == '"' && prev != '\\') {
                        inString = false;
                    }
                } else if (inChar) {
                    if (ch == '\'' && prev != '\\') {
                        inChar = false;
                    }
                } else if (ch == '/' && prev == '/') {
                    inSingleLineComment = true;
                } else if (ch == '*' && prev == '/') {
                    inMultiLineComment = true;
                } else if (ch == '"') {
                    int hashCount = 0;
                    while (col + hashCount + 1 < line.length() && line.charAt(col + hashCount + 1) == '"') {
                        hashCount++;
                    }
                    if (hashCount >= 2) {
                        inRawString = true;
                        rawStringHash = hashCount;
                    } else {
                        inString = true;
                    }
                } else if (ch == '\'') {
                    inChar = true;
                } else if (ch == '{') {
                    braceDepth++;
                } else if (ch == '}') {
                    braceDepth--;
                    if (braceDepth < 0) {
                        issues.add(new BuildIssue(filePath, lineNum, "多余的右大括号 '}'", "删除或修正多余的大括号。"));
                        braceDepth = 0;
                    }
                } else if (ch == '(') {
                    parenDepth++;
                } else if (ch == ')') {
                    parenDepth--;
                    if (parenDepth < 0) {
                        issues.add(new BuildIssue(filePath, lineNum, "多余的右圆括号 ')'", "删除或修正多余的圆括号。"));
                        parenDepth = 0;
                    }
                } else if (ch == '[') {
                    bracketDepth++;
                } else if (ch == ']') {
                    bracketDepth--;
                    if (bracketDepth < 0) {
                        issues.add(new BuildIssue(filePath, lineNum, "多余的右方括号 ']'", "删除或修正多余的方括号。"));
                        bracketDepth = 0;
                    }
                }
                prev = ch;
            }
        }
        if (braceDepth != 0) {
            issues.add(new BuildIssue(filePath, -1, "大括号不匹配，差 " + braceDepth + " 个（" + (braceDepth > 0 ? "多" : "少") + "）", "检查 {} 是否成对出现。"));
        }
        if (parenDepth != 0) {
            issues.add(new BuildIssue(filePath, -1, "圆括号不匹配，差 " + parenDepth + " 个（" + (parenDepth > 0 ? "多" : "少") + "）", "检查 () 是否成对出现。"));
        }
        if (bracketDepth != 0) {
            issues.add(new BuildIssue(filePath, -1, "方括号不匹配，差 " + bracketDepth + " 个（" + (bracketDepth > 0 ? "多" : "少") + "）", "检查 [] 是否成对出现。"));
        }
    }

    private void throwIfCancelled(CancellationSignal cancellationSignal) {
        if ((cancellationSignal != null && cancellationSignal.isCancelled()) || Thread.currentThread().isInterrupted()) {
            throw new CancellationException("预检查已取消");
        }
    }
}
