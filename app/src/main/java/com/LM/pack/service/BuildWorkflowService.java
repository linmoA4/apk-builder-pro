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
    private final EnvironmentManager environmentManager;
    private final Handler mainHandler;

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
                        startRealBuild(currentProject, environmentState, selectedJdkIndex, selectedNdkIndex, listener);
                    }
                });
            }
        }).start();
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
                            listener.onBuildFinished(result);
                        }
                    });
                }
            }
        );
    }
}
