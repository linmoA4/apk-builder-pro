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
import com.LM.pack.env.EnvironmentManager;
import com.LM.pack.model.EnvironmentState;
import com.LM.pack.theme.AppThemePalette;
import com.LM.pack.theme.LiquidGlassBackgroundView;
import com.LM.pack.theme.ThemeManager;

public class StartupActivity extends Activity {

    private Handler handler;
    private EnvironmentManager environmentManager;
    private ThemeManager themeManager;
    private EnvironmentState environmentState;
    private AppThemePalette palette;
    private boolean bootInProgress;

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
        themeManager = new ThemeManager(this);
        environmentState = environmentManager.loadState();

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
        progressBoot.setMax(100);
        progressBoot.setProgress(0);
        updateStatus("正在切换外置环境模式", "不再解压 APK 内嵌工具链，启动时只做轻量检查。", 18);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                environmentState = environmentManager.loadState();
                runEnvironmentCheck();
            }
        }, 420L);
    }

    private void runEnvironmentCheck() {
        environmentState = environmentManager.loadState();
        updateStatus("正在检查已登记环境", "启动不再阻塞下载，缺失环境可在设置页或打包时按推荐补齐。", 58);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                boolean sdkReady = environmentManager.isAndroidSdkRegistered(environmentState);
                boolean jdkReady = environmentManager.isSelectedJdkInstalled(environmentManager.loadSelectedJdkIndex(), environmentState);
                boolean ndkReady = environmentManager.isSelectedNdkInstalled(environmentManager.loadSelectedNdkIndex(), environmentState);
                if (sdkReady && jdkReady && ndkReady) {
                    updateStatus("检测到可用环境", "外置环境模式已启用，正在进入工作台。", 100);
                } else {
                    updateStatus("未检测到完整环境", "稍后可在设置页查看下载链接，或在打包时按项目自动推荐。", 100);
                }
                launchMain();
            }
        }, 520L);
    }

    private void updateStatus(String title, String message, int progress) {
        tvTitle.setText(title);
        tvStatus.setText(message);
        progressBoot.setProgress(Math.max(0, Math.min(100, progress)));
        tvProgress.setText(Math.max(0, Math.min(100, progress)) + "%");
    }

    private void showBootError(String message) {
        bootInProgress = false;
        tvTitle.setText("启动准备失败");
        tvStatus.setText(safeText(message, "外置环境模式启动失败，请重试"));
        tvProgress.setText(progressBoot.getProgress() + "%");
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
