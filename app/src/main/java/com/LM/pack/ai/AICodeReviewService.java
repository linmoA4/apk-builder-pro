package com.LM.pack.ai;

import com.LM.pack.BuildConfig;
import com.LM.pack.model.BuildIssue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import org.json.JSONArray;
import org.json.JSONObject;

public class AICodeReviewService {

    public interface ReviewCallback {
        void onComplete(ArrayList<BuildIssue> issues, ArrayList<String> notices, boolean fullSuccess);
    }

    private static final String SYNTAX_REVIEW_PROMPT =
        "你是 Android 代码审查引擎。请只返回 JSON，不要返回 Markdown、解释或额外文字。"
            + " 输出格式固定为 {\"issues\":[{\"severity\":\"error|warning|info\",\"line\":1,\"message\":\"问题描述\",\"suggestion\":\"修复建议\"}]}"
            + "。只关注语法错误、资源引用错误、潜在空指针、明显的 Android API 调用错误和会导致编译失败的问题。"
            + " 如果没有问题，返回 {\"issues\":[]}。";

    private static final String LOGIC_REVIEW_PROMPT =
        "你是 Android 代码审查引擎。请只返回 JSON，不要返回 Markdown、解释或额外文字。"
            + " 输出格式固定为 {\"issues\":[{\"severity\":\"error|warning|info\",\"line\":1,\"message\":\"问题描述\",\"suggestion\":\"修复建议\"}]}"
            + "。重点检查逻辑漏洞、边界条件、线程/UI 调用风险、状态同步问题和容易引发崩溃的实现缺陷。"
            + " 如果没有问题，返回 {\"issues\":[]}。";

    private final AIChatService chatService;

    public AICodeReviewService(AIChatService chatService) {
        this.chatService = chatService == null ? new AIChatService() : chatService;
    }

    public void reviewFile(final String relativePath, final String content, final ReviewCallback callback) {
        if (callback == null) {
            return;
        }
        final ArrayList<BuildIssue> collectedIssues = new ArrayList<BuildIssue>();
        final ArrayList<String> notices = new ArrayList<String>();
        final Object lock = new Object();
        final int[] finishedCount = new int[] {0};
        final boolean[] successFlags = new boolean[] {false, false};

        requestReview(
            0,
            BuildConfig.AI_REVIEW_MODEL_SYNTAX,
            SYNTAX_REVIEW_PROMPT,
            buildReviewPayload(relativePath, content),
            relativePath,
            collectedIssues,
            notices,
            successFlags,
            finishedCount,
            lock,
            callback
        );
        requestReview(
            1,
            BuildConfig.AI_REVIEW_MODEL_LOGIC,
            LOGIC_REVIEW_PROMPT,
            buildReviewPayload(relativePath, content),
            relativePath,
            collectedIssues,
            notices,
            successFlags,
            finishedCount,
            lock,
            callback
        );
    }

    private void requestReview(
        final int index,
        final String model,
        final String prompt,
        final String userContent,
        final String relativePath,
        final ArrayList<BuildIssue> collectedIssues,
        final ArrayList<String> notices,
        final boolean[] successFlags,
        final int[] finishedCount,
        final Object lock,
        final ReviewCallback callback
    ) {
        chatService.requestChat(model, prompt, userContent, new AIChatService.ChatCallback() {
            @Override
            public void onSuccess(String content) {
                synchronized (lock) {
                    try {
                        collectedIssues.addAll(parseIssues(content, relativePath));
                        successFlags[index] = true;
                    } catch (Exception e) {
                        notices.add(model + " 返回格式无法解析：" + safeText(e.getMessage(), "不是合法 JSON"));
                    }
                    finishIfReady(collectedIssues, notices, successFlags, finishedCount, callback);
                }
            }

            @Override
            public void onError(String message) {
                synchronized (lock) {
                    notices.add(model + " 调用失败：" + safeText(message, "未知错误"));
                    finishIfReady(collectedIssues, notices, successFlags, finishedCount, callback);
                }
            }
        });
    }

    private void finishIfReady(
        ArrayList<BuildIssue> collectedIssues,
        ArrayList<String> notices,
        boolean[] successFlags,
        int[] finishedCount,
        ReviewCallback callback
    ) {
        finishedCount[0]++;
        if (finishedCount[0] < 2) {
            return;
        }
        ArrayList<BuildIssue> merged = dedupeAndSort(collectedIssues);
        boolean fullSuccess = successFlags[0] && successFlags[1];
        callback.onComplete(merged, new ArrayList<String>(notices), fullSuccess);
    }

    private String buildReviewPayload(String relativePath, String content) {
        return "请审查下面这个文件，并严格输出 JSON。\n"
            + "文件路径：" + safeText(relativePath, "unknown") + "\n"
            + "代码开始：\n"
            + safeText(content)
            + "\n代码结束";
    }

    private ArrayList<BuildIssue> parseIssues(String raw, String relativePath) throws Exception {
        String json = unwrapJson(raw);
        ArrayList<BuildIssue> results = new ArrayList<BuildIssue>();
        if (json.startsWith("[")) {
            appendIssues(results, new JSONArray(json), relativePath);
            return results;
        }
        JSONObject object = new JSONObject(json);
        JSONArray issues = object.optJSONArray("issues");
        if (issues == null && object.has("data")) {
            Object data = object.opt("data");
            if (data instanceof JSONArray) {
                issues = (JSONArray) data;
            } else if (data instanceof JSONObject) {
                issues = ((JSONObject) data).optJSONArray("issues");
            }
        }
        if (issues == null) {
            issues = new JSONArray();
        }
        appendIssues(results, issues, relativePath);
        return results;
    }

    private void appendIssues(ArrayList<BuildIssue> target, JSONArray array, String relativePath) {
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String message = safeText(item.optString("message"));
            if (message.length() == 0) {
                continue;
            }
            int line = item.optInt("line", -1);
            if (line <= 0) {
                line = item.optInt("lineNumber", -1);
            }
            String suggestion = safeText(item.optString("suggestion"), "请结合当前上下文检查这一段实现。");
            String severity = safeText(item.optString("severity"), "warning");
            target.add(new BuildIssue(severity, safeText(relativePath, "当前文件"), line, message, suggestion));
        }
    }

    private ArrayList<BuildIssue> dedupeAndSort(ArrayList<BuildIssue> issues) {
        LinkedHashMap<String, BuildIssue> map = new LinkedHashMap<String, BuildIssue>();
        for (int i = 0; i < issues.size(); i++) {
            BuildIssue issue = issues.get(i);
            String key = issue.getSeverity() + "|" + issue.getFilePath() + "|" + issue.getLineNumber() + "|" + issue.getMessage();
            if (!map.containsKey(key)) {
                map.put(key, issue);
            }
        }
        ArrayList<BuildIssue> results = new ArrayList<BuildIssue>(map.values());
        Collections.sort(results, new Comparator<BuildIssue>() {
            @Override
            public int compare(BuildIssue left, BuildIssue right) {
                int leftLine = left == null ? Integer.MAX_VALUE : (left.getLineNumber() <= 0 ? Integer.MAX_VALUE : left.getLineNumber());
                int rightLine = right == null ? Integer.MAX_VALUE : (right.getLineNumber() <= 0 ? Integer.MAX_VALUE : right.getLineNumber());
                if (leftLine != rightLine) {
                    return leftLine - rightLine;
                }
                return safeText(left == null ? "" : left.getMessage()).compareToIgnoreCase(safeText(right == null ? "" : right.getMessage()));
            }
        });
        return results;
    }

    private String unwrapJson(String raw) {
        String text = safeText(raw);
        if (text.startsWith("```")) {
            int firstLine = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstLine >= 0 && lastFence > firstLine) {
                text = text.substring(firstLine + 1, lastFence).trim();
            }
        }
        int objectStart = text.indexOf('{');
        int arrayStart = text.indexOf('[');
        if (objectStart >= 0 && (arrayStart < 0 || objectStart < arrayStart)) {
            return text.substring(objectStart).trim();
        }
        if (arrayStart >= 0) {
            return text.substring(arrayStart).trim();
        }
        return text;
    }

    private String safeText(String value) {
        return safeText(value, "");
    }

    private String safeText(String value, String fallback) {
        if (value == null) {
            return fallback == null ? "" : fallback;
        }
        String trimmed = value.trim();
        return trimmed.length() == 0 ? (fallback == null ? "" : fallback) : trimmed;
    }
}
