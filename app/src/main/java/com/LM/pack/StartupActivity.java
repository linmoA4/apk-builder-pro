package com.LM.pack;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import com.LM.pack.build.BuildManager;
import com.LM.pack.env.EnvironmentManager;
import com.LM.pack.env.ToolchainInstaller;
import com.LM.pack.model.EnvironmentState;
import com.LM.pack.theme.AppThemePalette;
import com.LM.pack.theme.LiquidGlassBackgroundView;
import com.LM.pack.theme.ThemeManager;
import java.io.File;

public class StartupActivity extends Activity {

    private static final int STAGE_SDK = 0;
    private static final int STAGE_JDK = 1;
    private static final int STAGE_NDK = 2;
    private static final int STAGE_GRADLE = 3;

    private Handler handler;
    private EnvironmentManager environmentManager;
    private ToolchainInstaller toolchainInstaller;
    private BuildManager buildManager;
    private ThemeManager themeManager;
    private EnvironmentState environmentState;
    private AppThemePalette palette;
    private int embeddedJdkIndex;
    private int embeddedNdkIndex;
    private boolean bootInProgress;
    private int currentProgress;

    private LiquidGlassBackgroundView bgSceneView;
    private ImageView ivAvatar;
    private TextView tvTitle;
    private TextView tvSubtitle;
    private TextView tvStatus;
    private TextView tvProgress;
    private ProgressBar progressBoot;
    private Button btnRetry;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_startup);

        handler = new Handler(getMainLooper());
        SharedPreferences sharedPreferences = getSharedPreferences(EnvironmentManager.PREFS_NAME, MODE_PRIVATE);
        environmentManager = new EnvironmentManager(this, sharedPreferences);
        toolchainInstaller = new ToolchainInstaller(this, environmentManager);
        buildManager = new BuildManager(this, environmentManager);
        themeManager = new ThemeManager(this);
        environmentState = environmentManager.loadState();
        embeddedJdkIndex = environmentManager.findEmbeddedJdkIndex();
        embeddedNdkIndex = environmentManager.findEmbeddedNdkIndex();

        bindViews();
        applyThemeUi();
        bindEvents();
        startAvatarAnimation();
        beginBootFlow();
    }

    private void bindViews() {
        bgSceneView = (LiquidGlassBackgroundView) findViewById(R.id.bgSceneView);
        ivAvatar = (ImageView) findViewById(R.id.ivAvatar);
        tvTitle = (TextView) findViewById(R.id.tvTitle);
        tvSubtitle = (TextView) findViewById(R.id.tvSubtitle);
        tvStatus = (TextView) findViewById(R.id.tvStatus);
        tvProgress = (TextView) findViewById(R.id.tvProgress);
        progressBoot = (ProgressBar) findViewById(R.id.progressBoot);
        btnRetry = (Button) findViewById(R.id.btnRetry);
    }

    private void bindEvents() {
        btnRetry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                beginBootFlow();
            }
        });
    }

    private void applyThemeUi() {
        palette = themeManager.getPalette(this);
        themeManager.applyActivityWindow(this, palette);
        themeManager.applyTaggedStyles(findViewById(R.id.startupRoot), palette);
        if (bgSceneView != null) {
            bgSceneView.setPalette(palette);
        }
    }

    private void startAvatarAnimation() {
        ivAvatar.setScaleX(1.32f);
        ivAvatar.setScaleY(1.32f);
        ivAvatar.setAlpha(0.92f);
        ObjectAnimator animator = ObjectAnimator.ofPropertyValuesHolder(
            ivAvatar,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1.32f, 1.0f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.32f, 1.0f),
            PropertyValuesHolder.ofFloat(View.ALPHA, 0.92f, 1.0f)
        );
        animator.setDuration(950L);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.start();
    }

    private void beginBootFlow() {
        if (bootInProgress) {
            return;
        }
        bootInProgress = true;
        btnRetry.setVisibility(View.GONE);
        currentProgress = 0;
        progressBoot.setMax(100);
        progressBoot.setProgress(0);
        updateStatus("正在检查启动环境...", "准备进入工作台", 6);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                environmentState = environmentManager.loadState();
                normalizeSelections();
                if (needsPreparation()) {
                    runFirstPrepare();
                } else {
                    runEnvironmentCheck();
                }
            }
        }, 420L);
    }

    private boolean needsPreparation() {
        boolean needEmbeddedSdk = !environmentManager.isAndroidSdkRegistered(environmentState);
        boolean needEmbeddedJdk = embeddedJdkIndex >= 0 && !environmentManager.isSelectedJdkInstalled(embeddedJdkIndex, environmentState);
        boolean needEmbeddedNdk = embeddedNdkIndex >= 0 && !environmentManager.isSelectedNdkInstalled(embeddedNdkIndex, environmentState);
        boolean needGradle = !buildManager.isOfflineGradlePrepared();
        return needEmbeddedSdk || needEmbeddedJdk || needEmbeddedNdk || needGradle;
    }

    private void runFirstPrepare() {
        updateStatus("首次启动，正在准备运行环境", "开始解压内置 SDK / JDK / NDK / Gradle", 6);
        prepareEmbeddedSdk();
    }

    private void prepareEmbeddedSdk() {
        boolean needEmbeddedSdk = !environmentManager.isAndroidSdkRegistered(environmentState);
        if (!needEmbeddedSdk) {
            prepareEmbeddedJdk();
            return;
        }
        toolchainInstaller.installEmbeddedSdk(new ToolchainInstaller.InstallListener() {
            @Override
            public void onProgress(final String message, final int percent, final boolean indeterminate) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        updateStageProgress(STAGE_SDK, percent, indeterminate, "正在解压内置 SDK", message);
                    }
                });
            }

            @Override
            public void onSuccess(final String installedDir) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        environmentState = environmentManager.saveAndroidSdkDir(installedDir);
                        updateStatus("Android SDK 已准备完成", installedDir, 28);
                        prepareEmbeddedJdk();
                    }
                });
            }

            @Override
            public void onError(final String message) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        showBootError(message);
                    }
                });
            }
        });
    }

    private void prepareEmbeddedJdk() {
        boolean needEmbeddedJdk = embeddedJdkIndex >= 0 && !environmentManager.isSelectedJdkInstalled(embeddedJdkIndex, environmentState);
        if (!needEmbeddedJdk) {
            prepareEmbeddedNdk();
            return;
        }
        toolchainInstaller.installJdk(embeddedJdkIndex, new ToolchainInstaller.InstallListener() {
            @Override
            public void onProgress(final String message, final int percent, final boolean indeterminate) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        updateStageProgress(STAGE_JDK, percent, indeterminate, "正在解压 JDK 21", message);
                    }
                });
            }

            @Override
            public void onSuccess(final String installedDir) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        environmentState = environmentManager.saveInstalledJdk(EnvironmentManager.JDK_NAMES[embeddedJdkIndex], installedDir);
                        environmentManager.saveSelectedJdkIndex(embeddedJdkIndex);
                        updateStatus("JDK 21 已准备完成", installedDir, 52);
                        prepareEmbeddedNdk();
                    }
                });
            }

            @Override
            public void onError(final String message) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        showBootError(message);
                    }
                });
            }
        });
    }

    private void prepareEmbeddedNdk() {
        boolean needEmbeddedNdk = embeddedNdkIndex >= 0 && !environmentManager.isSelectedNdkInstalled(embeddedNdkIndex, environmentState);
        if (!needEmbeddedNdk) {
            prepareOfflineGradle();
            return;
        }
        toolchainInstaller.installNdk(embeddedNdkIndex, new ToolchainInstaller.InstallListener() {
            @Override
            public void onProgress(final String message, final int percent, final boolean indeterminate) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        updateStageProgress(STAGE_NDK, percent, indeterminate, "正在解压内置 NDK", message);
                    }
                });
            }

            @Override
            public void onSuccess(final String installedDir) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        environmentState = environmentManager.saveInstalledNdk(EnvironmentManager.NDK_NAMES[embeddedNdkIndex], installedDir);
                        environmentManager.saveSelectedNdkIndex(embeddedNdkIndex);
                        updateStatus("NDK 已准备完成", installedDir, 78);
                        prepareOfflineGradle();
                    }
                });
            }

            @Override
            public void onError(final String message) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        showBootError(message);
                    }
                });
            }
        });
    }

    private void prepareOfflineGradle() {
        if (buildManager.isOfflineGradlePrepared()) {
            runEnvironmentCheck();
            return;
        }
        updateStatus("正在解压内置 Gradle 8.7", "为后续构建准备运行环境", Math.max(currentProgress, 78));
        buildManager.prepareOfflineGradleAsync(new BuildManager.OfflineGradleListener() {
            @Override
            public void onProgress(final String message, final int percent, final boolean indeterminate) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        updateStageProgress(STAGE_GRADLE, percent, indeterminate, "正在解压内置 Gradle 8.7", message);
                    }
                });
            }

            @Override
            public void onSuccess(final File gradleExecutable) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        updateStatus("Gradle 8.7 已准备完成", gradleExecutable.getAbsolutePath(), 100);
                        runEnvironmentCheck();
                    }
                });
            }

            @Override
            public void onError(final String message) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        showBootError(message);
                    }
                });
            }
        });
    }

    private void runEnvironmentCheck() {
        environmentState = environmentManager.loadState();
        normalizeSelections();
        updateStatus("正在检查环境", "核对 JDK、NDK、Gradle 状态", Math.max(currentProgress, 92));
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                boolean sdkReady = environmentManager.isAndroidSdkRegistered(environmentState);
                boolean jdkReady = environmentManager.isSelectedJdkInstalled(environmentManager.loadSelectedJdkIndex(), environmentState);
                boolean ndkReady = environmentManager.isSelectedNdkInstalled(environmentManager.loadSelectedNdkIndex(), environmentState);
                boolean gradleReady = buildManager.isOfflineGradlePrepared();
                if (sdkReady && jdkReady && ndkReady && gradleReady) {
                    updateStatus("环境检查通过", "正在进入 APK Builder Pro", 100);
                    launchMain();
                } else {
                    if (!canRepairEnvironment()) {
                        showBootError("缺少可用的内置 JDK 或 NDK，无法自动完成启动准备");
                        return;
                    }
                    updateStatus("检测到环境不完整", "缺少必要组件，正在重新准备", Math.max(currentProgress, 88));
                    prepareEmbeddedSdk();
                }
            }
        }, 520L);
    }

    private void normalizeSelections() {
        environmentState = environmentManager.loadState();
        int selectedJdkIndex = environmentManager.loadSelectedJdkIndex();
        int selectedNdkIndex = environmentManager.loadSelectedNdkIndex();
        if (!environmentManager.isSelectedJdkInstalled(selectedJdkIndex, environmentState)
            && embeddedJdkIndex >= 0
            && environmentManager.isSelectedJdkInstalled(embeddedJdkIndex, environmentState)) {
            environmentManager.saveSelectedJdkIndex(embeddedJdkIndex);
        }
        if (!environmentManager.isSelectedNdkInstalled(selectedNdkIndex, environmentState)
            && embeddedNdkIndex >= 0
            && environmentManager.isSelectedNdkInstalled(embeddedNdkIndex, environmentState)) {
            environmentManager.saveSelectedNdkIndex(embeddedNdkIndex);
        }
        environmentState = environmentManager.loadState();
    }

    private void updateStageProgress(int stage, int percent, boolean indeterminate, String title, String message) {
        int stageStart;
        int stageEnd;
        if (stage == STAGE_SDK) {
            stageStart = 6;
            stageEnd = 30;
        } else if (stage == STAGE_JDK) {
            stageStart = 30;
            stageEnd = 56;
        } else if (stage == STAGE_NDK) {
            stageStart = 56;
            stageEnd = 82;
        } else {
            stageStart = 82;
            stageEnd = 100;
        }
        int nextProgress;
        if (indeterminate) {
            nextProgress = Math.min(stageEnd - 2, Math.max(currentProgress + 1, stageStart + 4));
        } else {
            nextProgress = stageStart + ((stageEnd - stageStart) * Math.max(0, Math.min(100, percent)) / 100);
        }
        updateStatus(title, safeText(message, title), nextProgress);
    }

    private void updateStatus(String title, String message, int progress) {
        currentProgress = Math.max(0, Math.min(100, progress));
        tvTitle.setText(title);
        tvStatus.setText(message);
        progressBoot.setProgress(currentProgress);
        tvProgress.setText(currentProgress + "%");
    }

    private void showBootError(String message) {
        bootInProgress = false;
        tvTitle.setText("启动准备失败");
        tvStatus.setText(safeText(message, "内置环境准备失败，请重试"));
        tvProgress.setText(currentProgress + "%");
        btnRetry.setVisibility(View.VISIBLE);
        Toast.makeText(this, safeText(message, "启动准备失败"), Toast.LENGTH_SHORT).show();
    }

    private boolean canRepairEnvironment() {
        boolean canRepairSdk = true;
        boolean canRepairJdk = embeddedJdkIndex >= 0 || environmentManager.isSelectedJdkInstalled(environmentManager.loadSelectedJdkIndex(), environmentState);
        boolean canRepairNdk = embeddedNdkIndex >= 0 || environmentManager.isSelectedNdkInstalled(environmentManager.loadSelectedNdkIndex(), environmentState);
        return canRepairSdk && canRepairJdk && canRepairNdk;
    }

    private void launchMain() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                bootInProgress = false;
                Intent intent = new Intent(StartupActivity.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }
        }, 260L);
    }

    private String safeText(String value, String fallback) {
        if (value == null || value.trim().length() == 0) {
            return fallback;
        }
        return value.trim();
    }
}
