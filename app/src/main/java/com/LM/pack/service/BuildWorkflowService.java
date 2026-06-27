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

public class BuildWorkflowService {
    public interface Listener {
        void onPreflightFailed(ArrayList<BuildIssue> issues);
        void onBuildStarted();
        void onBuildLog(String line);
        void onBuildFinished(BuildResult result);
    }

    private final BuildManager buildManager;
    private final ProjectPreflightChecker preflightChecker;
    private final Handler mainHandler;

    public BuildWorkflowService(BuildManager buildManager, ProjectPreflightChecker preflightChecker, Handler mainHandler) {
        this.buildManager = buildManager;
        this.preflightChecker = preflightChecker;
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
        new Thread(new Runnable() {
            @Override
            public void run() {
                final ArrayList<BuildIssue> issues = preflightChecker.collectProjectIssues(
                    new File(currentProject.getProjectDir()),
                    projectPrepared,
                    environmentState,
                    selectedJdkIndex,
                    selectedNdkIndex
                );
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!issues.isEmpty()) {
                            listener.onPreflightFailed(issues);
                            return;
                        }
                        listener.onBuildStarted();
                        startRealBuild(currentProject, environmentState, selectedJdkIndex, listener);
                    }
                });
            }
        }).start();
    }

    private void startRealBuild(
        final ProjectEntry currentProject,
        final EnvironmentState environmentState,
        final int selectedJdkIndex,
        final Listener listener
    ) {
        buildManager.runGradleBuild(
            currentProject.getProjectDir(),
            environmentState.getInstalledJdkDir(),
            environmentState.getAndroidSdkDir(),
            environmentState.getInstalledNdkDir(),
            EnvironmentManager.JDK_NAMES[selectedJdkIndex],
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
                            listener.onBuildFinished(result);
                        }
                    });
                }
            }
        );
    }
}
