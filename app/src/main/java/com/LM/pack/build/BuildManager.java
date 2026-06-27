package com.LM.pack.build;

import com.LM.pack.model.BuildIssue;
import com.LM.pack.model.BuildResult;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BuildManager {

    public interface BuildListener {
        void onLogLine(String line);
        void onFinished(BuildResult result);
    }

    public void runGradleBuild(
        final String projectDir,
        final String jdkDir,
        final String ndkDir,
        final String selectedJdkName,
        final BuildListener listener
    ) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                executeGradleBuild(projectDir, jdkDir, ndkDir, selectedJdkName, listener);
            }
        }).start();
    }

    private void executeGradleBuild(
        String projectDir,
        String jdkDir,
        String ndkDir,
        String selectedJdkName,
        BuildListener listener
    ) {
        Process process = null;
        BufferedReader reader = null;
        ArrayList<BuildIssue> issues = new ArrayList<BuildIssue>();
        String apkPath = "";
        try {
            File projectRoot = new File(projectDir);
            File gradlew = new File(projectRoot, "gradlew");
            if (!gradlew.exists()) {
                listener.onFinished(new BuildResult(false, -1, "未找到 gradlew，无法执行真实打包。", "", issues));
                return;
            }

            gradlew.setExecutable(true);
            listener.onLogLine("开始执行真实构建命令。");
            listener.onLogLine("构建目录: " + projectDir);
            listener.onLogLine("JDK 环境: " + selectedJdkName);
            listener.onLogLine("Gradle 任务: assembleDebug --stacktrace");

            ProcessBuilder processBuilder = new ProcessBuilder("./gradlew", "assembleDebug", "--stacktrace");
            processBuilder.directory(projectRoot);
            processBuilder.redirectErrorStream(true);
            processBuilder.environment().put("JAVA_HOME", jdkDir);
            processBuilder.environment().put("ANDROID_NDK_HOME", ndkDir);
            processBuilder.environment().put("ANDROID_NDK_ROOT", ndkDir);
            processBuilder.environment().put("PATH", jdkDir + "/bin:" + processBuilder.environment().get("PATH"));

            process = processBuilder.start();
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                listener.onLogLine(line);
                collectIssue(line, issues);
                String detectedApkPath = detectApkPath(line);
                if (detectedApkPath.length() > 0) {
                    apkPath = detectedApkPath;
                }
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                if (apkPath.length() == 0) {
                    apkPath = guessApkPath(projectRoot);
                }
                listener.onFinished(new BuildResult(true, 0, "真实打包完成", apkPath, issues));
            } else {
                listener.onFinished(new BuildResult(false, exitCode, "真实打包失败，Gradle 退出码：" + exitCode, apkPath, issues));
            }
        } catch (Exception e) {
            listener.onFinished(new BuildResult(false, -1, "真实打包异常：" + e.getMessage(), apkPath, issues));
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (Exception e) {
            }
            if (process != null) {
                process.destroy();
            }
        }
    }

    private void collectIssue(String line, ArrayList<BuildIssue> issues) {
        if (line == null) {
            return;
        }
        Matcher lineErrorMatcher = Pattern.compile("^(.+?):(\\d+):\\s*(?:error:)?\\s*(.+)$").matcher(line.trim());
        if (lineErrorMatcher.find()) {
            String filePath = lineErrorMatcher.group(1).trim();
            int lineNumber = parseIntSafe(lineErrorMatcher.group(2));
            String message = lineErrorMatcher.group(3).trim();
            issues.add(new BuildIssue(filePath, lineNumber, message, suggestFix(message, "")));
            return;
        }

        Matcher javaStyleMatcher = Pattern.compile("^(.*\\.(?:java|kt|xml|gradle)):\\s*(.+)$").matcher(line.trim());
        if (javaStyleMatcher.find() && line.toLowerCase().indexOf("error") >= 0) {
            issues.add(new BuildIssue(javaStyleMatcher.group(1).trim(), -1, javaStyleMatcher.group(2).trim(), suggestFix(line, "")));
        }
    }

    private String detectApkPath(String line) {
        Matcher matcher = Pattern.compile("(\\/.*?\\.apk)").matcher(line);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private String guessApkPath(File projectRoot) {
        File apk = new File(projectRoot, "app/build/outputs/apk/debug/app-debug.apk");
        if (apk.exists()) {
            return apk.getAbsolutePath();
        }
        return "";
    }

    private int parseIntSafe(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return -1;
        }
    }

    private String suggestFix(String message, String codeLine) {
        String lower = message == null ? "" : message.toLowerCase();
        if (lower.indexOf("';' expected") >= 0 || lower.indexOf("expecting ';'") >= 0) {
            return "检查这一行结尾是否缺少分号 `;`。";
        }
        if (lower.indexOf("cannot find symbol") >= 0) {
            return "检查类名、方法名、变量名和 import 是否写错，必要时同步依赖。";
        }
        if (lower.indexOf("package") >= 0 && lower.indexOf("does not exist") >= 0) {
            return "当前包或依赖不存在，检查 `import`、包名目录和 Gradle 依赖。";
        }
        if (lower.indexOf("android resource linking failed") >= 0) {
            return "资源链接失败，优先检查 XML 是否闭合、资源名是否存在、引用格式是否正确。";
        }
        if (lower.indexOf("parseerror") >= 0 || lower.indexOf("xml") >= 0) {
            return "XML 可能有标签未闭合、属性引号不完整，或特殊字符未转义。";
        }
        if (codeLine != null && codeLine.trim().length() > 0) {
            return "先修正当前高亮行的语法，再重新打包验证。";
        }
        return "先根据错误行号检查代码，再重新执行打包。";
    }
}
