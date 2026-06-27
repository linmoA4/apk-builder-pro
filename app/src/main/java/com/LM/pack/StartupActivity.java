package com.LM.pack;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.Settings;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import com.LM.pack.env.EnvironmentManager;
import com.LM.pack.model.EnvironmentState;
import com.LM.pack.theme.AppThemePalette;
import com.LM.pack.theme.GlassProgressBarView;
import com.LM.pack.theme.LiquidGlassBackgroundView;
import com.LM.pack.theme.ThemeManager;

public class StartupActivity extends Activity {
    // TODO: 将启动流程改为事件驱动，替换 360ms / 420ms / 460ms 这类硬编码延迟。

    private static final int REQUEST_STORAGE_PERMISSION = 7001;

    private Handler handler;
    private EnvironmentManager environmentManager;
    private ThemeManager themeManager;
    private EnvironmentState environmentState;
    private AppThemePalette palette;
    private boolean bootInProgress;
    private boolean waitingForStoragePermission;
    private boolean splashFinished;

    private LiquidGlassBackgroundView bgSceneView;
    private View loadingContent;
    private View splashOverlay;
    private ImageView ivAvatar;
    private ImageView ivSplashAvatar;
    private TextView tvTitle;
    private TextView tvSubtitle;
    private TextView tvStatus;
    private TextView tvStatusHint;
    private GlassProgressBarView progressBoot;
    private Button btnRetry;
    private TextView[] stageViews;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_startup);

        handler = new Handler(getMainLooper());
        SharedPreferences sharedPreferences = getSharedPreferences(EnvironmentManager.PREFS_NAME, MODE_PRIVATE);
        environmentManager = new EnvironmentManager(this, sharedPreferences);
        themeManager = new ThemeManager(this);
        environmentState = environmentManager.loadState();

        bindViews();
        applyThemeUi();
        bindEvents();
        prepareLoadingUi();
        startSplashSequence();
    }

    private void bindViews() {
        bgSceneView = (LiquidGlassBackgroundView) findViewById(R.id.bgSceneView);
        loadingContent = findViewById(R.id.loadingContent);
        splashOverlay = findViewById(R.id.splashOverlay);
        ivAvatar = (ImageView) findViewById(R.id.ivAvatar);
        ivSplashAvatar = (ImageView) findViewById(R.id.ivSplashAvatar);
        tvTitle = (TextView) findViewById(R.id.tvTitle);
        tvSubtitle = (TextView) findViewById(R.id.tvSubtitle);
        tvStatus = (TextView) findViewById(R.id.tvStatus);
        tvStatusHint = (TextView) findViewById(R.id.tvStatusHint);
        progressBoot = (GlassProgressBarView) findViewById(R.id.progressBoot);
        btnRetry = (Button) findViewById(R.id.btnRetry);
        stageViews = new TextView[] {
            (TextView) findViewById(R.id.tvStageWelcome),
            (TextView) findViewById(R.id.tvStagePrepare),
            (TextView) findViewById(R.id.tvStageReady)
        };
    }

    private void bindEvents() {
        btnRetry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!splashFinished) {
                    startSplashSequence();
                    return;
                }
                ensureStorageAccessThenBoot();
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

    private void prepareLoadingUi() {
        loadingContent.setAlpha(0f);
        loadingContent.setTranslationY(dp(14f));
        splashOverlay.setAlpha(1f);
        splashOverlay.setVisibility(View.VISIBLE);
        btnRetry.setVisibility(View.GONE);
        tvTitle.setText("正在进入工作台");
        tvSubtitle.setText("正在为你恢复项目空间与常用能力");
        progressBoot.setIndeterminate(true);
        progressBoot.setProgress(12);
        updateStatus("正在整理启动所需内容", "整个过程通常只需片刻，请稍等一下。", 10);
    }

    private void startSplashSequence() {
        splashFinished = false;
        splashOverlay.animate().cancel();
        loadingContent.animate().cancel();
        ivSplashAvatar.animate().cancel();

        splashOverlay.setVisibility(View.VISIBLE);
        splashOverlay.setAlpha(1f);
        ivSplashAvatar.setScaleX(0.84f);
        ivSplashAvatar.setScaleY(0.84f);
        ivSplashAvatar.setAlpha(0f);
        ivSplashAvatar.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(380L)
            .setInterpolator(new DecelerateInterpolator())
            .start();

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                loadingContent.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(520L)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();

                splashOverlay.animate()
                    .alpha(0f)
                    .setDuration(520L)
                    .setInterpolator(new DecelerateInterpolator())
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            splashOverlay.setVisibility(View.GONE);
                            splashOverlay.animate().setListener(null);
                            splashFinished = true;
                            startAvatarAnimation();
                            ensureStorageAccessThenBoot();
                        }
                    })
                    .start();

                ivSplashAvatar.animate()
                    .alpha(0f)
                    .scaleX(1.14f)
                    .scaleY(1.14f)
                    .setDuration(520L)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
            }
        }, 420L);
    }

    private void startAvatarAnimation() {
        ivAvatar.setScaleX(1.2f);
        ivAvatar.setScaleY(1.2f);
        ivAvatar.setAlpha(0.85f);
        ObjectAnimator animator = ObjectAnimator.ofPropertyValuesHolder(
            ivAvatar,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1.2f, 1.0f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.2f, 1.0f),
            PropertyValuesHolder.ofFloat(View.ALPHA, 0.85f, 1.0f)
        );
        animator.setDuration(820L);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.start();
    }

    private void beginBootFlow() {
        if (bootInProgress) {
            return;
        }
        bootInProgress = true;
        btnRetry.setVisibility(View.GONE);
        progressBoot.setIndeterminate(true);
        updateStatus("正在唤醒工作台", "正在同步上次使用状态并整理必要资源。", 18);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                environmentState = environmentManager.loadState();
                runEnvironmentCheck();
            }
        }, 360L);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!waitingForStoragePermission) {
            return;
        }
        if (hasStorageAccess()) {
            waitingForStoragePermission = false;
            beginBootFlow();
            return;
        }
        updateStatus("等待你完成授权", "回到应用后会自动继续，无需重新开始。", 12);
    }

    private void runEnvironmentCheck() {
        environmentState = environmentManager.loadState();
        updateStatus("正在恢复常用能力", "正在让工作台回到可用状态。", 58);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                boolean sdkReady = environmentManager.isAndroidSdkRegistered(environmentState);
                boolean jdkReady = environmentManager.isSelectedJdkInstalled(environmentManager.loadSelectedJdkIndex(), environmentState);
                boolean ndkReady = environmentManager.isSelectedNdkInstalled(environmentManager.loadSelectedNdkIndex(), environmentState);
                if (sdkReady && jdkReady && ndkReady) {
                    updateStatus("准备完成", "马上带你进入主界面。", 100);
                } else {
                    updateStatus("基础内容已就绪", "缺少的构建能力稍后也能在设置页补齐。", 100);
                }
                launchMain();
            }
        }, 460L);
    }

    private void updateStatus(String title, String message, int progress) {
        int clamped = clamp(progress);
        if (clamped >= 100) {
            tvTitle.setText("欢迎回来");
        } else if (title != null && title.contains("授权")) {
            tvTitle.setText("需要授权继续");
        } else if (clamped >= 40) {
            tvTitle.setText("正在进入工作台");
        } else {
            tvTitle.setText("请稍等一下");
        }
        tvStatus.setText(title);
        tvStatusHint.setText(message);
        if (clamped >= 100) {
            progressBoot.setIndeterminate(false);
            progressBoot.setProgress(100);
        } else {
            progressBoot.setIndeterminate(true);
            progressBoot.setProgress(Math.max(12, clamped));
        }
        updateStageViews(clamped >= 100 ? 2 : (clamped >= 40 ? 1 : 0));
    }

    private void updateStageViews(int stage) {
        if (stageViews == null) {
            return;
        }
        for (int i = 0; i < stageViews.length; i++) {
            TextView view = stageViews[i];
            if (view == null) {
                continue;
            }
            boolean reached = i <= stage;
            boolean active = i == stage;
            view.animate()
                .alpha(reached ? 1f : 0.45f)
                .scaleX(active ? 1.06f : 1f)
                .scaleY(active ? 1.06f : 1f)
                .setDuration(220L)
                .start();
        }
    }

    private void showBootError(String message) {
        bootInProgress = false;
        splashFinished = true;
        tvTitle.setText("暂时没能进入工作台");
        tvStatus.setText("启动准备失败");
        tvStatusHint.setText(safeText(message, "这次启动没有顺利完成，可以再试一次。"));
        progressBoot.setIndeterminate(false);
        progressBoot.setProgress(22);
        updateStageViews(0);
        btnRetry.setVisibility(View.VISIBLE);
    }

    private void launchMain() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                bootInProgress = false;
                Intent intent = new Intent(StartupActivity.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                finish();
            }
        }, 220L);
    }

    private void ensureStorageAccessThenBoot() {
        if (hasStorageAccess()) {
            waitingForStoragePermission = false;
            beginBootFlow();
            return;
        }
        waitingForStoragePermission = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            updateStatus("请开启文件访问权限", "允许后即可继续浏览项目与使用本地目录。", 8);
            openAllFilesAccessPage();
            return;
        }
        updateStatus("请允许储存权限", "允许后即可继续使用本地项目与工作目录。", 8);
        requestPermissions(
            new String[] {
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            },
            REQUEST_STORAGE_PERMISSION
        );
    }

    private boolean hasStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void openAllFilesAccessPage() {
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(android.net.Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            } catch (Exception ignored) {
                showBootError("请到系统设置中开启文件访问权限后再返回应用。");
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_STORAGE_PERMISSION) {
            return;
        }
        if (hasStorageAccess()) {
            waitingForStoragePermission = false;
            beginBootFlow();
            return;
        }
        updateStatus("还需要文件访问权限", "允许后才能继续使用本地目录与项目导入。", 8);
        btnRetry.setVisibility(View.VISIBLE);
    }

    private int clamp(int value) {
        if (value < 0) {
            return 0;
        }
        if (value > 100) {
            return 100;
        }
        return value;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String safeText(String value, String fallback) {
        if (value == null || value.trim().length() == 0) {
            return fallback;
        }
        return value.trim();
    }
}
