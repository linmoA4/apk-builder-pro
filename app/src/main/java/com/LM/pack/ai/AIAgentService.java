package com.LM.pack.ai;

import com.LM.pack.BuildConfig;
import com.LM.pack.model.AIAgentEvent;
import com.LM.pack.model.BuildIssue;
import com.LM.pack.model.ProjectEntry;
import com.LM.pack.service.ProjectFileService;
import com.LM.pack.service.ProjectWorkspaceService;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;

public class AIAgentService {

    public interface Listener {
        void onEvent(AIAgentEvent event);
        void onFinished(String summary, boolean success);
    }

    private static final int MAX_ROUNDS = 4;
    private static final int MAX_STEPS_PER_ROUND = 4;
    private static final int MAX_READ_LINES = 220;
    private static final int MAX_OBSERVATION_CHARS = 14000;
    private static final long COMMAND_TIMEOUT_SECONDS = 20L;
    private static final String AGENT_SYSTEM_PROMPT =
        "你是 Android 工程代理。你要帮用户在项目根目录内完成任务。"
            + "请只返回 JSON，不要返回 Markdown。"
            + "顶层格式固定为 {\"thought\":\"给用户看的简短思路摘要\",\"steps\":[...],\"summary\":\"阶段总结\"}。"
            + "steps 里的 type 只允许 read、write、delete、command、finish。"
            + "read 格式：{\"type\":\"read\",\"path\":\"相对路径\",\"startLine\":1,\"endLine\":160,\"reason\":\"为什么读\"}。"
            + "write 格式：{\"type\":\"write\",\"path\":\"相对路径\",\"content\":\"完整文件内容\",\"reason\":\"为什么写\"}。"
            + "delete 格式：{\"type\":\"delete\",\"path\":\"相对路径\",\"reason\":\"为什么删\"}。"
            + "command 格式：{\"type\":\"command\",\"command\":\"非交互命令\",\"reason\":\"为什么执行\"}。"
            + "finish 格式：{\"type\":\"finish\",\"summary\":\"已经完成什么，还剩什么\"}。"
            + "规则："
            + "1. 只能使用项目根目录内的相对路径。"
            + "2. 如果要改文件，优先先 read，再 write。"
            + "3. 用户可见的思路摘要必须简短，不要暴露隐藏推理。"
            + "4. 不要用 command 读取文件内容，不要输出 cat/head/tail/sed/awk/grep 等读取文件的命令。"
            + "5. command 只用于验证、构建、查看目录或运行非交互检查，不能包含重定向、管道、连写。"
            + "6. 每轮最多输出 4 步。"
            + "7. 如果信息足够且任务完成，就输出 finish。";

    private final AIChatService chatService;
    private final ProjectWorkspaceService workspaceService;
    private final ProjectFileService fileService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public AIAgentService(AIChatService chatService, ProjectWorkspaceService workspaceService, ProjectFileService fileService) {
        this.chatService = chatService == null ? new AIChatService() : chatService;
        this.workspaceService = workspaceService;
        this.fileService = fileService == null ? new ProjectFileService() : fileService;
    }

    public void startTask(
        final ProjectEntry project,
        final File activeFile,
        final String userGoal,
        final ArrayList<BuildIssue> issues,
        final Listener listener
    ) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                executeTask(project, activeFile, userGoal, issues, listener);
            }
        });
    }

    private void executeTask(ProjectEntry project, File activeFile, String userGoal, ArrayList<BuildIssue> issues, Listener listener) {
        if (project == null || listener == null) {
            return;
        }
        File projectRoot = new File(project.getProjectDir());
        String projectSummary = buildProjectSummary(project, activeFile, issues);
        String observationLog = "";
        emit(listener, new AIAgentEvent(
            AIAgentEvent.TYPE_STATUS,
            "AI 代理已启动",
            "<b>目标</b>：" + AIAgentDiffFormatter.escapeHtml(safeText(userGoal, "未提供目标")),
            "AI 代理已启动"
        ));
        boolean finished = false;
        String finalSummary = "AI 任务已结束。";
        for (int round = 0; round < MAX_ROUNDS && !finished; round++) {
            String prompt = buildRoundPrompt(projectSummary, userGoal, observationLog, round);
            String raw = requestChatSync(BuildConfig.AI_AGENT_MODEL, AGENT_SYSTEM_PROMPT, prompt);
            if (safeText(raw).length() == 0) {
                emit(listener, errorEvent("AI 返回空结果"));
                listener.onFinished("AI 返回空结果", false);
                return;
            }
            PlanResponse plan;
            try {
                plan = parsePlan(raw);
            } catch (Exception e) {
                emit(listener, errorEvent("AI 计划解析失败：" + safeText(e.getMessage(), "格式异常")));
                listener.onFinished("AI 计划解析失败", false);
                return;
            }
            if (safeText(plan.thought).length() > 0) {
                emit(listener, new AIAgentEvent(
                    AIAgentEvent.TYPE_THOUGHT,
                    "思路摘要",
                    AIAgentDiffFormatter.escapeHtml(plan.thought),
                    plan.thought
                ));
            }
            if (plan.steps.isEmpty()) {
                emit(listener, errorEvent("AI 没有给出可执行步骤"));
                listener.onFinished("AI 没有给出可执行步骤", false);
                return;
            }
            int roundSteps = Math.min(plan.steps.size(), MAX_STEPS_PER_ROUND);
            for (int i = 0; i < roundSteps; i++) {
                Step step = plan.steps.get(i);
                StepResult result = executeStep(projectRoot, step, listener);
                observationLog = appendObservation(observationLog, result.observation);
                if (!result.continueExecution) {
                    finished = "finish".equals(step.type);
                    if (finished) {
                        finalSummary = safeText(result.summary, safeText(plan.summary, "AI 任务完成"));
                    } else {
                        finalSummary = safeText(result.summary, "AI 任务终止");
                    }
                    break;
                }
                if (result.forceFinish) {
                    finished = true;
                    finalSummary = safeText(result.summary, safeText(plan.summary, "AI 任务完成"));
                    break;
                }
            }
            if (!finished && safeText(plan.summary).length() > 0) {
                observationLog = appendObservation(observationLog, "阶段总结：" + plan.summary);
            }
        }
        if (!finished) {
            emit(listener, new AIAgentEvent(
                AIAgentEvent.TYPE_FINISH,
                "AI 任务结束",
                AIAgentDiffFormatter.escapeHtml("已达到本轮最大执行步数，建议继续观察结果后再次发起。"),
                "AI 任务结束"
            ));
            listener.onFinished("已达到本轮最大执行步数", true);
            return;
        }
        emit(listener, new AIAgentEvent(
            AIAgentEvent.TYPE_FINISH,
            "AI 任务完成",
            AIAgentDiffFormatter.escapeHtml(finalSummary),
            finalSummary
        ));
        listener.onFinished(finalSummary, true);
    }

    private StepResult executeStep(File projectRoot, Step step, Listener listener) {
        if (step == null) {
            return StepResult.stop("步骤为空", "步骤为空");
        }
        String type = normalizeType(step.type);
        if ("finish".equals(type)) {
            return StepResult.finish(safeText(step.summary, "AI 认为当前任务已完成。"), "finish");
        }
        if ("read".equals(type)) {
            return executeRead(projectRoot, step, listener);
        }
        if ("write".equals(type)) {
            return executeWrite(projectRoot, step, listener);
        }
        if ("delete".equals(type)) {
            return executeDelete(projectRoot, step, listener);
        }
        if ("command".equals(type)) {
            return executeCommand(projectRoot, step, listener);
        }
        emit(listener, errorEvent("不支持的步骤类型：" + safeText(step.type, "unknown")));
        return StepResult.next("步骤被拒绝：不支持的类型");
    }

    private StepResult executeRead(File projectRoot, Step step, Listener listener) {
        try {
            File target = resolveProjectFile(projectRoot, step.path);
            if (target == null || !target.exists() || !target.isFile()) {
                emit(listener, errorEvent("读取失败，文件不存在：" + safeText(step.path, "")));
                return StepResult.next("读取失败：文件不存在 " + safeText(step.path, ""));
            }
            String content = workspaceService.readText(target);
            String[] lines = content.split("\n", -1);
            int totalLines = lines.length;
            int start = clamp(step.startLine <= 0 ? 1 : step.startLine, 1, Math.max(1, totalLines));
            int end = clamp(step.endLine <= 0 ? Math.min(totalLines, start + MAX_READ_LINES - 1) : step.endLine, start, Math.min(totalLines, start + MAX_READ_LINES - 1));
            StringBuilder selected = new StringBuilder();
            for (int i = start; i <= end && i <= totalLines; i++) {
                if (selected.length() > 0) {
                    selected.append('\n');
                }
                selected.append(i).append(": ").append(lines[i - 1]);
            }
            String relativePath = toRelativePath(projectRoot, target);
            emit(listener, new AIAgentEvent(
                AIAgentEvent.TYPE_READ,
                "读取文件",
                "<b>文件</b>：" + AIAgentDiffFormatter.escapeHtml(relativePath)
                    + "<br/><b>范围</b>：" + start + "-" + end
                    + "<br/><b>读取行数</b>：" + (end - start + 1)
                    + "<br/><font color=\"#94A3B8\">已隐藏文件正文</font>",
                "读取 " + relativePath + " " + (end - start + 1) + " 行"
            ));
            return StepResult.next(
                "READ " + relativePath + " lines " + start + "-" + end + " of " + totalLines + ":\n" + selected
            );
        } catch (Exception e) {
            emit(listener, errorEvent("读取文件失败：" + safeText(e.getMessage(), "未知错误")));
            return StepResult.next("读取文件失败：" + safeText(e.getMessage(), "未知错误"));
        }
    }

    private StepResult executeWrite(File projectRoot, Step step, Listener listener) {
        try {
            File target = resolveProjectFile(projectRoot, step.path);
            if (target == null) {
                emit(listener, errorEvent("写入失败，路径非法：" + safeText(step.path, "")));
                return StepResult.next("写入失败：路径非法");
            }
            boolean existedBefore = target.exists() && target.isFile();
            String oldContent = target.exists() && target.isFile() ? workspaceService.readText(target) : "";
            File parent = target.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IOException("无法创建目录：" + parent.getAbsolutePath());
            }
            workspaceService.writeText(target, step.content == null ? "" : step.content);
            String newContent = workspaceService.readText(target);
            String relativePath = toRelativePath(projectRoot, target);
            emit(listener, new AIAgentEvent(
                AIAgentEvent.TYPE_WRITE,
                existedBefore ? "修改文件" : "新增文件",
                "<b>文件</b>：" + AIAgentDiffFormatter.escapeHtml(relativePath)
                    + "<br/>"
                    + AIAgentDiffFormatter.buildHtml(oldContent, newContent),
                "写入 " + relativePath
            ));
            return StepResult.next("WRITE " + relativePath + " success. New size=" + newContent.length());
        } catch (Exception e) {
            emit(listener, errorEvent("写入文件失败：" + safeText(e.getMessage(), "未知错误")));
            return StepResult.next("写入文件失败：" + safeText(e.getMessage(), "未知错误"));
        }
    }

    private StepResult executeDelete(File projectRoot, Step step, Listener listener) {
        try {
            File target = resolveProjectFile(projectRoot, step.path);
            if (target == null || !target.exists()) {
                emit(listener, errorEvent("删除失败，目标不存在：" + safeText(step.path, "")));
                return StepResult.next("删除失败：目标不存在");
            }
            String relativePath = toRelativePath(projectRoot, target);
            String diffHtml = "";
            if (target.isFile() && fileService.isTextEditableFile(target)) {
                String oldContent = workspaceService.readText(target);
                diffHtml = AIAgentDiffFormatter.buildHtml(oldContent, "");
            }
            deleteRecursively(target);
            emit(listener, new AIAgentEvent(
                AIAgentEvent.TYPE_DELETE,
                "删除文件",
                "<b>目标</b>：" + AIAgentDiffFormatter.escapeHtml(relativePath)
                    + (diffHtml.length() > 0 ? "<br/>" + diffHtml : "<br/><font color=\"#EF4444\">已删除</font>"),
                "删除 " + relativePath
            ));
            return StepResult.next("DELETE " + relativePath + " success.");
        } catch (Exception e) {
            emit(listener, errorEvent("删除失败：" + safeText(e.getMessage(), "未知错误")));
            return StepResult.next("删除失败：" + safeText(e.getMessage(), "未知错误"));
        }
    }

    private StepResult executeCommand(File projectRoot, Step step, Listener listener) {
        String command = safeText(step.command);
        if (!isAllowedCommand(command)) {
            emit(listener, errorEvent("命令被拒绝：" + command));
            return StepResult.next("命令被拒绝：" + command);
        }
        Process process = null;
        try {
            process = new ProcessBuilder("sh", "-c", command)
                .directory(projectRoot)
                .redirectErrorStream(true)
                .start();
            String output = readStream(process.getInputStream(), 12000);
            boolean completed = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                emit(listener, new AIAgentEvent(
                    AIAgentEvent.TYPE_ERROR,
                    "命令超时",
                    "<b>命令</b>：<code>" + AIAgentDiffFormatter.escapeHtml(command) + "</code><br/><font color=\"#EF4444\">执行超时，已中断</font>",
                    "命令超时：" + command
                ));
                return StepResult.next("COMMAND " + command + " timeout");
            }
            int code = process.exitValue();
            String normalizedOutput = safeText(output, "(无输出)");
            String title = code == 0 ? "执行命令" : "命令报错";
            String outputColor = code == 0 ? "#E2E8F0" : "#FCA5A5";
            emit(listener, new AIAgentEvent(
                code == 0 ? AIAgentEvent.TYPE_COMMAND : AIAgentEvent.TYPE_ERROR,
                title,
                "<b>命令</b>：<code>" + AIAgentDiffFormatter.escapeHtml(command) + "</code>"
                    + "<br/><b>退出码</b>：" + code
                    + "<br/><font color=\"" + outputColor + "\"><pre>" + AIAgentDiffFormatter.escapeHtml(normalizedOutput) + "</pre></font>",
                title + "：" + command
            ));
            return StepResult.next("COMMAND " + command + " exit=" + code + "\n" + normalizedOutput);
        } catch (Exception e) {
            emit(listener, errorEvent("命令执行失败：" + safeText(e.getMessage(), "未知错误")));
            return StepResult.next("命令执行失败：" + safeText(e.getMessage(), "未知错误"));
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private boolean isAllowedCommand(String command) {
        if (command.length() == 0) {
            return false;
        }
        String lower = command.toLowerCase(Locale.ROOT);
        if (lower.contains("\n")
            || lower.contains("&&")
            || lower.contains("||")
            || lower.contains("|")
            || lower.contains(">")
            || lower.contains("<")
            || lower.contains(";")) {
            return false;
        }
        String[] denied = new String[] {"cat ", "head ", "tail ", "sed ", "awk ", "grep ", "rm ", "mv ", "cp ", "tee ", "more ", "less "};
        for (int i = 0; i < denied.length; i++) {
            if (lower.startsWith(denied[i]) || lower.contains(" " + denied[i])) {
                return false;
            }
        }
        return true;
    }

    private String buildRoundPrompt(String projectSummary, String userGoal, String observationLog, int round) {
        return "项目概况：\n"
            + safeText(projectSummary)
            + "\n\n用户目标：\n"
            + safeText(userGoal)
            + "\n\n当前轮次："
            + (round + 1)
            + "\n\n已知观察：\n"
            + safeText(observationLog, "暂无观察。请先读取必要文件，再开始修改。");
    }

    private String buildProjectSummary(ProjectEntry project, File activeFile, ArrayList<BuildIssue> issues) {
        StringBuilder builder = new StringBuilder();
        builder.append("项目名：").append(safeText(project == null ? "" : project.getProjectName(), "未知项目")).append('\n');
        builder.append("包名：").append(safeText(project == null ? "" : project.getPackageName(), "未识别")).append('\n');
        if (activeFile != null) {
            builder.append("当前打开文件：").append(activeFile.getAbsolutePath()).append('\n');
        }
        if (issues != null && !issues.isEmpty()) {
            builder.append("当前错误：\n");
            for (int i = 0; i < issues.size() && i < 8; i++) {
                builder.append("- ").append(issues.get(i).getDisplayText()).append('\n');
            }
        }
        builder.append("可编辑文件概览：\n");
        ArrayList<File> files = new ArrayList<File>();
        collectEditableFiles(new File(project.getProjectDir()), files, 40);
        Collections.sort(files, new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                return left.getAbsolutePath().compareToIgnoreCase(right.getAbsolutePath());
            }
        });
        File root = new File(project.getProjectDir());
        for (int i = 0; i < files.size(); i++) {
            builder.append("- ").append(toRelativePath(root, files.get(i))).append('\n');
        }
        return builder.toString();
    }

    private void collectEditableFiles(File directory, ArrayList<File> results, int limit) {
        if (directory == null || !directory.exists() || !directory.isDirectory() || results.size() >= limit) {
            return;
        }
        File[] children = directory.listFiles();
        if (children == null) {
            return;
        }
        ArrayList<File> files = new ArrayList<File>();
        ArrayList<File> directories = new ArrayList<File>();
        for (int i = 0; i < children.length; i++) {
            File child = children[i];
            if (shouldIgnore(child)) {
                continue;
            }
            if (child.isDirectory()) {
                directories.add(child);
            } else if (fileService.isTextEditableFile(child)) {
                files.add(child);
            }
        }
        Collections.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        Collections.sort(directories, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (int i = 0; i < files.size() && results.size() < limit; i++) {
            results.add(files.get(i));
        }
        for (int i = 0; i < directories.size() && results.size() < limit; i++) {
            collectEditableFiles(directories.get(i), results, limit);
        }
    }

    private boolean shouldIgnore(File file) {
        if (file == null) {
            return true;
        }
        String name = file.getName();
        return ".git".equals(name) || ".gradle".equals(name) || "build".equals(name);
    }

    private PlanResponse parsePlan(String raw) throws Exception {
        String json = unwrapJson(raw);
        JSONObject object = new JSONObject(json);
        PlanResponse response = new PlanResponse();
        response.thought = safeText(object.optString("thought"));
        response.summary = safeText(object.optString("summary"));
        JSONArray steps = object.optJSONArray("steps");
        if (steps == null) {
            steps = new JSONArray();
        }
        for (int i = 0; i < steps.length(); i++) {
            JSONObject item = steps.optJSONObject(i);
            if (item == null) {
                continue;
            }
            Step step = new Step();
            step.type = safeText(item.optString("type"));
            step.path = safeText(item.optString("path"));
            step.command = safeText(item.optString("command"));
            step.content = item.has("content") ? String.valueOf(item.opt("content")) : "";
            step.reason = safeText(item.optString("reason"));
            step.summary = safeText(item.optString("summary"));
            step.startLine = item.optInt("startLine", 1);
            step.endLine = item.optInt("endLine", step.startLine + MAX_READ_LINES - 1);
            response.steps.add(step);
        }
        return response;
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
        int start = text.indexOf('{');
        if (start >= 0) {
            return text.substring(start).trim();
        }
        return text;
    }

    private String requestChatSync(String model, String systemPrompt, String userContent) {
        final CountDownLatch latch = new CountDownLatch(1);
        final String[] holder = new String[] {""};
        chatService.requestChat(model, systemPrompt, userContent, new AIChatService.ChatCallback() {
            @Override
            public void onSuccess(String content) {
                holder[0] = safeText(content);
                latch.countDown();
            }

            @Override
            public void onError(String message) {
                holder[0] = "";
                latch.countDown();
            }
        });
        try {
            latch.await(90L, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        }
        return holder[0];
    }

    private String appendObservation(String source, String addition) {
        String merged = safeText(source);
        if (safeText(addition).length() > 0) {
            if (merged.length() > 0) {
                merged += "\n\n";
            }
            merged += addition;
        }
        if (merged.length() > MAX_OBSERVATION_CHARS) {
            return merged.substring(merged.length() - MAX_OBSERVATION_CHARS);
        }
        return merged;
    }

    private File resolveProjectFile(File root, String relativePath) throws IOException {
        if (root == null || safeText(relativePath).length() == 0) {
            return null;
        }
        File target = new File(root, relativePath);
        String rootPath = root.getCanonicalPath();
        String targetPath = target.getCanonicalPath();
        if (targetPath.equals(rootPath) || targetPath.startsWith(rootPath + File.separator)) {
            return target;
        }
        return null;
    }

    private String toRelativePath(File root, File file) {
        try {
            String rootPath = root.getCanonicalPath();
            String filePath = file.getCanonicalPath();
            if (filePath.startsWith(rootPath)) {
                String relative = filePath.substring(rootPath.length());
                if (relative.startsWith(File.separator)) {
                    relative = relative.substring(1);
                }
                return relative.length() == 0 ? "." : relative;
            }
        } catch (Exception ignored) {
        }
        return file == null ? "" : file.getAbsolutePath();
    }

    private void deleteRecursively(File target) throws IOException {
        if (target.isDirectory()) {
            File[] children = target.listFiles();
            if (children != null) {
                for (int i = 0; i < children.length; i++) {
                    deleteRecursively(children[i]);
                }
            }
        }
        if (!target.delete()) {
            throw new IOException("无法删除：" + target.getAbsolutePath());
        }
    }

    private String readStream(InputStream inputStream, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            if (output.size() + read > limit) {
                output.write(buffer, 0, Math.max(0, limit - output.size()));
                break;
            }
            output.write(buffer, 0, read);
        }
        return output.toString(StandardCharsets.UTF_8.name()).trim();
    }

    private AIAgentEvent errorEvent(String message) {
        return new AIAgentEvent(
            AIAgentEvent.TYPE_ERROR,
            "执行异常",
            "<font color=\"#EF4444\">" + AIAgentDiffFormatter.escapeHtml(safeText(message, "未知错误")) + "</font>",
            safeText(message, "未知错误")
        );
    }

    private void emit(Listener listener, AIAgentEvent event) {
        if (listener != null && event != null) {
            listener.onEvent(event);
        }
    }

    private int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private String normalizeType(String value) {
        String lower = safeText(value).toLowerCase(Locale.ROOT);
        if ("read_file".equals(lower)) {
            return "read";
        }
        if ("write_file".equals(lower) || "replace_file".equals(lower)) {
            return "write";
        }
        if ("run_command".equals(lower) || "exec".equals(lower)) {
            return "command";
        }
        return lower;
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

    private static class PlanResponse {
        String thought = "";
        String summary = "";
        final ArrayList<Step> steps = new ArrayList<Step>();
    }

    private static class Step {
        String type = "";
        String path = "";
        String command = "";
        String content = "";
        String reason = "";
        String summary = "";
        int startLine = 1;
        int endLine = MAX_READ_LINES;
    }

    private static class StepResult {
        final String observation;
        final boolean continueExecution;
        final boolean forceFinish;
        final String summary;

        StepResult(String observation, boolean continueExecution, boolean forceFinish, String summary) {
            this.observation = observation == null ? "" : observation;
            this.continueExecution = continueExecution;
            this.forceFinish = forceFinish;
            this.summary = summary == null ? "" : summary;
        }

        static StepResult next(String observation) {
            return new StepResult(observation, true, false, "");
        }

        static StepResult stop(String summary, String observation) {
            return new StepResult(observation, false, false, summary);
        }

        static StepResult finish(String summary, String observation) {
            return new StepResult(observation, false, true, summary);
        }
    }
}
