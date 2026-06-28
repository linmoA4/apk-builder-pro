package com.LM.pack.service;

import android.os.Handler;
import com.LM.pack.build.BuildManager;
import com.LM.pack.build.ProjectPreflightChecker;
import com.LM.pack.env.EnvironmentManager;
import com.LM.pack.model.BuildIssue;
import com.LM.pack.model.BuildResult;
import com.LM.pack.model.EnvironmentState;
import com.LM.pack.model.ProjectEntry;
import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.CancellationException;

public class BuildWorkflowService {
    // TODO: 为构建流程补充取消与超时控制，避免 Gradle 卡死时只能强制退出。

    public interface Listener {
        void onPreflightFailed(ArrayList<BuildIssue> issues);
        void onBuildStarted();
        void onBuildLog(String line);
        void onBuildFinished(BuildResult result);
        void onWorkflowCancelled(String message);
    }

    private final BuildManager buildManager;
    private final ProjectPreflightChecker preflightChecker;
    private final EnvironmentManager environmentManager;
    private final Handler mainHandler;
    private volatile boolean cancelRequested = false;
    private volatile Thread preflightThread;

    public BuildWorkflowService(
        BuildManager buildManager,
        ProjectPreflightChecker preflightChecker,
        EnvironmentManager environmentManager,
        Handler mainHandler
    ) {
        this.buildManager = buildManager;
        this.preflightChecker = preflightChecker;
        this.environmentManager = environmentManager;
        this.mainHandler = mainHandler;
    }

    public void checkAndBuild(
        final ProjectEntry currentProject,
        final boolean projectPrepared,
        final EnvironmentState environmentState,
        final int selectedJdkIndex,
        final int selectedNdkIndex,
        final Listener listener
    ) {
        final File projectDir = new File(currentProject.getProjectDir());
        final int resolvedJdkIndex = selectedJdkIndex;
        final int resolvedNdkIndex = selectedNdkIndex;
        environmentManager.saveSelectedJdkIndex(resolvedJdkIndex);
        environmentManager.saveSelectedNdkIndex(resolvedNdkIndex);
        cancelRequested = false;
        preflightThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final ArrayList<BuildIssue> issues = preflightChecker.collectProjectIssues(
                        projectDir,
                        projectPrepared,
                        environmentState,
                        resolvedJdkIndex,
                        resolvedNdkIndex,
                        new ProjectPreflightChecker.CancellationSignal() {
                            @Override
                            public boolean isCancelled() {
                                return cancelRequested || Thread.currentThread().isInterrupted();
                            }
                        }
                    );
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (cancelRequested) {
                                listener.onWorkflowCancelled("构建已取消");
                                return;
                            }
                            if (!issues.isEmpty()) {
                                listener.onPreflightFailed(issues);
                                return;
                            }
                            listener.onBuildStarted();
                            startRealBuild(currentProject, environmentState, resolvedJdkIndex, resolvedNdkIndex, listener);
                        }
                    });
                } catch (CancellationException cancelled) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            listener.onWorkflowCancelled("构建已取消");
                        }
                    });
                } catch (Exception e) {
                    final ArrayList<BuildIssue> issues = new ArrayList<BuildIssue>();
                    issues.add(
                        new BuildIssue(
                            projectDir.getAbsolutePath(),
                            -1,
                            "打包前检查异常：" + buildErrorMessage(e),
                            "请检查最近修改的项目结构、构建脚本和环境配置，然后重新尝试。"
                        )
                    );
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (cancelRequested) {
                                listener.onWorkflowCancelled("构建已取消");
                            } else {
                                listener.onPreflightFailed(issues);
                            }
                        }
                    });
                } finally {
                    preflightThread = null;
                }
            }
        });
        preflightThread.start();
    }

    public void cancelCurrentWork() {
        cancelRequested = true;
        Thread thread = preflightThread;
        if (thread != null) {
            thread.interrupt();
        }
        buildManager.cancelCurrentBuild();
    }

    private String buildErrorMessage(Exception e) {
        if (e == null || e.getMessage() == null || e.getMessage().trim().length() == 0) {
            return "未知错误";
        }
        return e.getMessage().trim();
    }

    private void startRealBuild(
        final ProjectEntry currentProject,
        final EnvironmentState environmentState,
        final int selectedJdkIndex,
        final int selectedNdkIndex,
        final Listener listener
    ) {
        String selectedJdkDir = environmentManager.getSelectedJdkDir(selectedJdkIndex, environmentState);
        String selectedNdkDir = environmentManager.getSelectedNdkDir(selectedNdkIndex, environmentState);
        buildManager.runGradleBuild(
            currentProject.getProjectDir(),
            selectedJdkDir,
            environmentState.getAndroidSdkDir(),
            selectedNdkDir,
            environmentManager.getSelectedJdkName(selectedJdkIndex),
            new BuildManager.BuildListener() {
                @Override
                public void onLogLine(final String line) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            listener.onBuildLog(line);
                        }
                    });
                }

                @Override
                public void onFinished(final BuildResult result) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (cancelRequested || result.getExitCode() == BuildManager.EXIT_CODE_CANCELLED) {
                                listener.onWorkflowCancelled(result.getMessage());
                            } else {
                                listener.onBuildFinished(result);
                            }
                        }
                    });
                }
            }
        );
    }
}
