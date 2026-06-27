package com.LM.pack.build;

import com.LM.pack.model.BuildResult;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

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
        try {
            File projectRoot = new File(projectDir);
            File gradlew = new File(projectRoot, "gradlew");
            if (!gradlew.exists()) {
                listener.onFinished(new BuildResult(false, -1, "未找到 gradlew，无法执行真实打包。"));
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
            processBuilder.environment().put("PATH", jdkDir + "/bin:" + processBuilder.environment().get("PATH"));

            process = processBuilder.start();
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                listener.onLogLine(line);
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                listener.onFinished(new BuildResult(true, 0, "真实打包完成"));
            } else {
                listener.onFinished(new BuildResult(false, exitCode, "真实打包失败，Gradle 退出码：" + exitCode));
            }
        } catch (Exception e) {
            listener.onFinished(new BuildResult(false, -1, "真实打包异常：" + e.getMessage()));
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
}
