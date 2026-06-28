package com.LM.pack.editor;

import android.content.Context;
import com.LM.pack.R;
import com.LM.pack.model.BuildIssue;
import java.util.ArrayList;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;
import java.io.StringReader;

public class EditorIssueAnalyzer {

    private final Context context;

    public EditorIssueAnalyzer(Context context) {
        this.context = context.getApplicationContext();
    }

    public ArrayList<BuildIssue> analyze(String fileName, String content) {
        ArrayList<BuildIssue> issues = new ArrayList<BuildIssue>();
        if (content == null) {
            return issues;
        }
        if (content.contains("<<<<<<<") || content.contains("=======") || content.contains(">>>>>>>")) {
            issues.add(new BuildIssue(
                fileName,
                -1,
                context.getString(R.string.issue_merge_conflict_message),
                context.getString(R.string.issue_merge_conflict_suggestion)
            ));
        }
        String lowerName = fileName == null ? "" : fileName.toLowerCase();
        if (lowerName.endsWith(".xml")) {
            collectXmlIssues(fileName, content, issues);
        } else if (lowerName.endsWith(".java") || lowerName.endsWith(".kt") || lowerName.endsWith(".gradle") || lowerName.endsWith(".kts")) {
            collectBraceIssues(fileName, content, issues);
        }
        return issues;
    }

    private void collectXmlIssues(String fileName, String content, ArrayList<BuildIssue> issues) {
        try {
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader(content)));
        } catch (SAXParseException e) {
            issues.add(new BuildIssue(
                fileName,
                normalizeLineNumber(e.getLineNumber()),
                e.getMessage(),
                context.getString(R.string.issue_xml_suggestion)
            ));
        } catch (Exception e) {
            issues.add(new BuildIssue(
                fileName,
                -1,
                e.getMessage(),
                context.getString(R.string.issue_xml_incomplete_suggestion)
            ));
        }
    }

    private void collectBraceIssues(String fileName, String content, ArrayList<BuildIssue> issues) {
        int roundBalance = 0;
        int curlyBalance = 0;
        int squareBalance = 0;
        int lineNumber = 1;
        boolean inString = false;
        char stringQuote = 0;
        boolean earlyCloseReported = false;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '\n') {
                lineNumber++;
            }
            if (inString) {
                if (c == stringQuote && (i == 0 || content.charAt(i - 1) != '\\')) {
                    inString = false;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                inString = true;
                stringQuote = c;
                continue;
            }
            if (c == '(') {
                roundBalance++;
            } else if (c == ')') {
                roundBalance--;
            } else if (c == '{') {
                curlyBalance++;
            } else if (c == '}') {
                curlyBalance--;
            } else if (c == '[') {
                squareBalance++;
            } else if (c == ']') {
                squareBalance--;
            }
            if (roundBalance < 0 || curlyBalance < 0 || squareBalance < 0) {
                if (!earlyCloseReported) {
                    issues.add(new BuildIssue(
                        fileName,
                        lineNumber,
                        context.getString(R.string.issue_bracket_early_close_message),
                        context.getString(R.string.issue_bracket_early_close_suggestion)
                    ));
                    earlyCloseReported = true;
                }
                roundBalance = Math.max(0, roundBalance);
                curlyBalance = Math.max(0, curlyBalance);
                squareBalance = Math.max(0, squareBalance);
            }
        }
        if (inString) {
            issues.add(new BuildIssue(
                fileName,
                lineNumber,
                context.getString(R.string.issue_string_unclosed_message),
                context.getString(R.string.issue_string_unclosed_suggestion)
            ));
            return;
        }
        if (roundBalance != 0 || curlyBalance != 0 || squareBalance != 0) {
            issues.add(new BuildIssue(
                fileName,
                lineNumber,
                context.getString(R.string.issue_bracket_unbalanced_message),
                context.getString(R.string.issue_bracket_unbalanced_suggestion)
            ));
        }
    }

    private int normalizeLineNumber(int lineNumber) {
        return lineNumber > 0 ? lineNumber : 1;
    }
}
