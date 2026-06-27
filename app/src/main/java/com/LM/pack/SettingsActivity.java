package com.LM.pack;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
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
import java.util.ArrayList;

public class SettingsActivity extends Activity {

    private Handler handler;
    private EnvironmentManager environmentManager;
    private ToolchainInstaller toolchainInstaller;
    private BuildManager buildManager;
    private ThemeManager themeManager;
    private EnvironmentState environmentState;
    private AppThemePalette palette;
    private int selectedJdkIndex;
    private int selectedNdkIndex;
    private boolean preparingEmbeddedTools;

    private LiquidGlassBackgroundView bgSceneView;
    private Button btnBackSettings;
    private Button btnPrepareEmbedded;
    private Button btnEnvironmentDetect;
    private Button btnDownloadRoute;
    private Button btnJdkPreference;
    private Button btnNdkPreference;
    private Button btnAppearanceMode;
    private Button btnSurfaceStyle;
    private TextView tvEnvironmentSummary;
    private TextView tvSdkHint;
    private TextView tvConfigHint;
    private TextView tvGradleHint;
    private TextView tvThemeSummary;
    private TextView tvDirectoryPlan;
    private View progressOverlay;
    private TextView tvProgressTitle;
    private TextView tvProgressMessage;
    private TextView tvProgressPercent;
    private ProgressBar progressBarInstall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        handler = new Handler(getMainLooper());
        SharedPreferences sharedPreferences = getSharedPreferences(EnvironmentManager.PREFS_NAME, MODE_PRIVATE);
        environmentManager = new EnvironmentManager(this, sharedPreferences);
        toolchainInstaller = new ToolchainInstaller(this, environmentManager);
        buildManager = new BuildManager(this, environmentManager);
        themeManager = new ThemeManager(this);
        environmentState = environmentManager.loadState();
        selectedJdkIndex = environmentManager.loadSelectedJdkIndex();
        selectedNdkIndex = environmentManager.loadSelectedNdkIndex();

        bindViews();
        bindEvents();
        applyThemeUi();
        refreshUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        environmentState = environmentManager.loadState();
        selectedJdkIndex = environmentManager.loadSelectedJdkIndex();
        selectedNdkIndex = environmentManager.loadSelectedNdkIndex();
        applyThemeUi();
        refreshUi();
    }

    private void bindViews() {
        bgSceneView = (LiquidGlassBackgroundView) findViewById(R.id.bgSceneView);
        btnBackSettings = (Button) findViewById(R.id.btnBackSettings);
        btnPrepareEmbedded = (Button) findViewById(R.id.btnPrepareEmbedded);
        btnEnvironmentDetect = (Button) findViewById(R.id.btnEnvironmentDetect);
        btnDownloadRoute = (Button) findViewById(R.id.btnDownloadRoute);
        btnJdkPreference = (Button) findViewById(R.id.btnJdkPreference);
        btnNdkPreference = (Button) findViewById(R.id.btnNdkPreference);
        btnAppearanceMode = (Button) findViewById(R.id.btnAppearanceMode);
        btnSurfaceStyle = (Button) findViewById(R.id.btnSurfaceStyle);
        tvEnvironmentSummary = (TextView) findViewById(R.id.tvEnvironmentSummary);
        tvSdkHint = (TextView) findViewById(R.id.tvSdkHint);
        tvConfigHint = (TextView) findViewById(R.id.tvConfigHint);
        tvGradleHint = (TextView) findViewById(R.id.tvGradleHint);
        tvThemeSummary = (TextView) findViewById(R.id.tvThemeSummary);
        tvDirectoryPlan = (TextView) findViewById(R.id.tvDirectoryPlan);
        progressOverlay = findViewById(R.id.progressOverlay);
        tvProgressTitle = (TextView) findViewById(R.id.tvProgressTitle);
        tvProgressMessage = (TextView) findViewById(R.id.tvProgressMessage);
        tvProgressPercent = (TextView) findViewById(R.id.tvProgressPercent);
        progressBarInstall = (ProgressBar) findViewById(R.id.progressBarInstall);
    }

    private void bindEvents() {
        btnBackSettings.setOnClickListener(v -> finish());
        btnPrepareEmbedded.setOnClickListener(v -> maybePrepareExternalTools(true));
        btnEnvironmentDetect.setOnClickListener(v -> showEnvironmentDetectDialog());
        btnDownloadRoute.setOnClickListener(v -> showDownloadRouteDialog());
        btnJdkPreference.setOnClickListener(v -> showJdkPreferenceDialog());
        btnNdkPreference.setOnClickListener(v -> showNdkPreferenceDialog());
        btnAppearanceMode.setOnClickListener(v -> showAppearanceModeDialog());
        btnSurfaceStyle.setOnClickListener(v -> showSurfaceStyleDialog());
    }

    private void applyThemeUi() {
        palette = themeManager.getPalette(this);
        themeManager.applyActivityWindow(this, palette);
        if (bgSceneView != null) {
            bgSceneView.setPalette(palette);
        }
        themeManager.applyTaggedStyles(findViewById(R.id.settingsRoot), palette);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            progressBarInstall.setProgressTintList(ColorStateList.valueOf(palette.accent));
            progressBarInstall.setIndeterminateTintList(ColorStateList.valueOf(palette.accent));
        }
    }

    private void refreshUi() {
        environmentState = environmentManager.loadState();
        selectedJdkIndex = environmentManager.loadSelectedJdkIndex();
        selectedNdkIndex = environmentManager.loadSelectedNdkIndex();
        tvEnvironmentSummary.setText(buildEnvironmentSummary());
        tvSdkHint.setText(buildEmbeddedSummary());
        tvConfigHint.setText(buildConfigSummary());
        tvGradleHint.setText(buildGradleSummary());
        tvThemeSummary.setText(buildThemeSummary());
        tvDirectoryPlan.setText(buildDirectoryPlan());
        btnPrepareEmbedded.setText(buildPrepareButtonText());
        btnDownloadRoute.setText("下载路线：" + environmentManager.getDownloadRouteDisplayName());
        btnJdkPreference.setText("JDK 偏好：" + simplifyToolLabel(environmentManager.getSelectedJdkName(selectedJdkIndex)));
        btnNdkPreference.setText("NDK 偏好：" + simplifyToolLabel(environmentManager.getSelectedNdkName(selectedNdkIndex)));
    }

    private void maybePrepareExternalTools(boolean userTriggered) {
        if (preparingEmbeddedTools) {
            return;
        }
        final int embeddedJdkIndex = EnvironmentManager.DEFAULT_JDK_INDEX;
        final int embeddedNdkIndex = EnvironmentManager.DEFAULT_NDK_INDEX;
        boolean needSdk = !environmentManager.isAndroidSdkRegistered(environmentState);
        boolean needJdk = embeddedJdkIndex >= 0 && !environmentManager.isSelectedJdkInstalled(embeddedJdkIndex, environmentState);
        boolean needNdk = embeddedNdkIndex >= 0 && !environmentManager.isSelectedNdkInstalled(embeddedNdkIndex, environmentState);
        boolean needGradle = !buildManager.isOfflineGradlePrepared();
        if (!needSdk && !needJdk && !needNdk && !needGradle) {
            if (userTriggered) {
                toast("4 个环境已经准备完成");
            }
            return;
        }
        environmentManager.saveSelectedJdkIndex(embeddedJdkIndex);
        environmentManager.saveSelectedNdkIndex(embeddedNdkIndex);
        selectedJdkIndex = embeddedJdkIndex;
        selectedNdkIndex = embeddedNdkIndex;
        preparingEmbeddedTools = true;
        showProgressOverlay(
            "自动检测环境",
            "正在按" + environmentManager.getDownloadRouteDisplayName() + "准备 SDK / JDK 21 / NDK r27 / Gradle 8.7 ...",
            0,
            true
        );
        prepareExternalSdkThenRest(embeddedJdkIndex, embeddedNdkIndex, userTriggered);
    }

    private void prepareExternalSdkThenRest(final int embeddedJdkIndex, final int embeddedNdkIndex, final boolean userTriggered) {
        boolean needSdk = !environmentManager.isAndroidSdkRegistered(environmentState);
        if (!needSdk) {
            prepareExternalJdkThenNdk(embeddedJdkIndex, embeddedNdkIndex, userTriggered);
            return;
        }
        toolchainInstaller.installEmbeddedSdk(new ToolchainInstaller.InstallListener() {
            @Override
            public void onProgress(final String message, final int percent, final boolean indeterminate) {
                handler.post(() -> showProgressOverlay("准备 Android SDK", message, blendProgress(0, 42, percent), indeterminate));
            }

            @Override
            public void onSuccess(final String installedDir) {
                handler.post(() -> {
                    environmentState = environmentManager.saveAndroidSdkDir(installedDir);
                    prepareExternalJdkThenNdk(embeddedJdkIndex, embeddedNdkIndex, userTriggered);
                });
            }

            @Override
            public void onError(final String message) {
                handler.post(() -> finishPreparationWithError(message));
            }
        });
    }

    private void prepareExternalJdkThenNdk(final int embeddedJdkIndex, final int embeddedNdkIndex, final boolean userTriggered) {
        boolean needJdk = embeddedJdkIndex >= 0 && !environmentManager.isSelectedJdkInstalled(embeddedJdkIndex, environmentState);
        if (!needJdk) {
            prepareExternalNdk(embeddedNdkIndex, userTriggered);
            return;
        }
        toolchainInstaller.installJdk(embeddedJdkIndex, new ToolchainInstaller.InstallListener() {
            @Override
            public void onProgress(final String message, final int percent, final boolean indeterminate) {
                handler.post(() -> showProgressOverlay("准备 JDK 21", message, blendProgress(42, 20, percent), indeterminate));
            }

            @Override
            public void onSuccess(final String installedDir) {
                handler.post(() -> {
                    environmentState = environmentManager.saveInstalledJdk(EnvironmentManager.JDK_NAMES[embeddedJdkIndex], installedDir);
                    if (selectedJdkIndex == embeddedJdkIndex || selectedJdkIndex < 0) {
                        environmentManager.saveSelectedJdkIndex(embeddedJdkIndex);
                        selectedJdkIndex = embeddedJdkIndex;
                    }
                    prepareExternalNdk(embeddedNdkIndex, userTriggered);
                });
            }

            @Override
            public void onError(final String message) {
                handler.post(() -> finishPreparationWithError(message));
            }
        });
    }

    private void prepareExternalNdk(final int embeddedNdkIndex, final boolean userTriggered) {
        boolean needNdk = embeddedNdkIndex >= 0 && !environmentManager.isSelectedNdkInstalled(embeddedNdkIndex, environmentState);
        if (!needNdk) {
            prepareOfflineGradle(userTriggered);
            return;
        }
        toolchainInstaller.installNdk(embeddedNdkIndex, new ToolchainInstaller.InstallListener() {
            @Override
            public void onProgress(final String message, final int percent, final boolean indeterminate) {
                handler.post(() -> showProgressOverlay("准备 NDK r27", message, blendProgress(62, 18, percent), indeterminate));
            }

            @Override
            public void onSuccess(final String installedDir) {
                handler.post(() -> {
                    environmentState = environmentManager.saveInstalledNdk(EnvironmentManager.NDK_NAMES[embeddedNdkIndex], installedDir);
                    if (selectedNdkIndex == embeddedNdkIndex || selectedNdkIndex < 0) {
                        environmentManager.saveSelectedNdkIndex(embeddedNdkIndex);
                        selectedNdkIndex = embeddedNdkIndex;
                    }
                    prepareOfflineGradle(userTriggered);
                });
            }

            @Override
            public void onError(final String message) {
                handler.post(() -> finishPreparationWithError(message));
            }
        });
    }

    private void prepareOfflineGradle(final boolean userTriggered) {
        if (buildManager.isOfflineGradlePrepared()) {
            finishPreparationSuccess(userTriggered);
            return;
        }
        buildManager.prepareOfflineGradleAsync(new BuildManager.OfflineGradleListener() {
            @Override
            public void onProgress(final String message, final int percent, final boolean indeterminate) {
                handler.post(() -> showProgressOverlay("准备 Gradle 8.7", message, blendProgress(80, 20, percent), indeterminate));
            }

            @Override
            public void onSuccess(final File gradleExecutable) {
                handler.post(() -> finishPreparationSuccess(userTriggered));
            }

            @Override
            public void onError(final String message) {
                handler.post(() -> finishPreparationWithError(message));
            }
        });
    }

    private void finishPreparationSuccess(boolean userTriggered) {
        preparingEmbeddedTools = false;
        hideProgressOverlay();
        refreshUi();
        if (userTriggered) {
            toast("外置 SDK / JDK 21 / NDK 27 / Gradle 8.7 已准备完成");
        }
    }

    private void finishPreparationWithError(String message) {
        preparingEmbeddedTools = false;
        hideProgressOverlay();
        refreshUi();
        toast(message);
    }

    private void showAppearanceModeDialog() {
        final String[] labels = {"跟随系统", "浅色", "深色"};
        final String[] values = {ThemeManager.MODE_SYSTEM, ThemeManager.MODE_LIGHT, ThemeManager.MODE_DARK};
        int checked = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(themeManager.getAppearanceMode())) {
                checked = i;
                break;
            }
        }
        showOptionListDialog("亮暗模式", "不再用系统原生选择框，直接在应用里切换。", labels, checked, which -> {
            themeManager.setAppearanceMode(values[which]);
            applyThemeUi();
            refreshUi();
        });
    }

    private void showDownloadRouteDialog() {
        final String[] labels = {"国内路线（镜像优先）", "国外路线（官方优先）"};
        final String[] values = {EnvironmentManager.DOWNLOAD_ROUTE_CHINA, EnvironmentManager.DOWNLOAD_ROUTE_GLOBAL};
        String currentRoute = environmentManager.loadDownloadRoute();
        int checked = EnvironmentManager.DOWNLOAD_ROUTE_GLOBAL.equals(currentRoute) ? 1 : 0;
        showOptionListDialog("下载路线", "安装环境时可以按国内或国外线路走直链。", labels, checked, which -> {
            environmentManager.saveDownloadRoute(values[which]);
            refreshUi();
            toast("已切换为" + labels[which]);
        });
    }

    private void showSurfaceStyleDialog() {
        final String[] labels = {"正常主题", "液态玻璃主题"};
        final String[] values = {ThemeManager.STYLE_NORMAL, ThemeManager.STYLE_LIQUID};
        int checked = ThemeManager.STYLE_LIQUID.equals(themeManager.getSurfaceStyle()) ? 1 : 0;
        showOptionListDialog("主题材质", "液态玻璃已经改成更克制的圆角和更稳定的背景层次。", labels, checked, which -> {
            themeManager.setSurfaceStyle(values[which]);
            applyThemeUi();
            refreshUi();
        });
    }

    private void showJdkPreferenceDialog() {
        String[] labels = new String[EnvironmentManager.JDK_NAMES.length];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = simplifyToolLabel(EnvironmentManager.JDK_NAMES[i]);
        }
        showOptionListDialog("JDK 偏好", "这里保存你平时优先使用的 JDK 版本。", labels, selectedJdkIndex, which -> {
            environmentManager.saveSelectedJdkIndex(which);
            selectedJdkIndex = which;
            refreshUi();
        });
    }

    private void showNdkPreferenceDialog() {
        String[] labels = new String[EnvironmentManager.NDK_NAMES.length];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = simplifyToolLabel(EnvironmentManager.NDK_NAMES[i]);
        }
        showOptionListDialog("NDK 偏好", "这里保存你平时优先使用的 NDK 版本。", labels, selectedNdkIndex, which -> {
            environmentManager.saveSelectedNdkIndex(which);
            selectedNdkIndex = which;
            refreshUi();
        });
    }

    private void showEnvironmentDetectDialog() {
        final Dialog dialog = createAppDialog("环境检测结果", "这里会把全部环境的已装版本一次性列出来。");
        ListView listView = (ListView) dialog.findViewById(R.id.lvDialogItems);
        Button btnPrimary = (Button) dialog.findViewById(R.id.btnDialogPrimary);
        Button btnSecondary = (Button) dialog.findViewById(R.id.btnDialogSecondary);
        Button btnNeutral = (Button) dialog.findViewById(R.id.btnDialogNeutral);
        btnPrimary.setVisibility(View.GONE);
        btnNeutral.setVisibility(View.GONE);
        btnSecondary.setText("关闭");
        btnSecondary.setOnClickListener(v -> dialog.dismiss());

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(2), dp(2), dp(2), dp(2));
        panel.addView(buildEnvironmentRow("Android SDK", environmentManager.isAndroidSdkRegistered(environmentState)
            ? "已准备：" + safeText(environmentState.getAndroidSdkDir(), "未登记")
            : "未准备"));
        panel.addView(buildEnvironmentRow("JDK", buildInstalledJdkSummary()));
        panel.addView(buildEnvironmentRow("NDK", buildInstalledNdkSummary()));
        panel.addView(buildEnvironmentRow("Gradle", buildManager.isOfflineGradlePrepared() ? "8.7" : "未准备"));
        panel.addView(buildEnvironmentRow("下载路线", environmentManager.getDownloadRouteDisplayName()));
        panel.addView(buildEnvironmentRow("当前偏好", simplifyToolLabel(environmentManager.getSelectedJdkName(selectedJdkIndex))
            + " / " + simplifyToolLabel(environmentManager.getSelectedNdkName(selectedNdkIndex))));

        listView.addHeaderView(panel, null, false);
        listView.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, new String[] {
            "点上面的“自动检测环境”可以直接补齐缺失环境。"
        }));
        dialog.show();
    }

    private LinearLayout buildEnvironmentRow(String title, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(8);
        row.setLayoutParams(params);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor(palette == null ? Color.parseColor("#182231") : palette.surface);
        bg.setStroke(dp(1), palette == null ? Color.parseColor("#2D3C56") : palette.stroke);
        row.setBackground(bg);

        ImageView icon = new ImageView(this);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(18), dp(18));
        iconParams.rightMargin = dp(10);
        icon.setLayoutParams(iconParams);
        icon.setImageResource(R.drawable.ic_small_check);
        row.addView(icon);

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        textColumn.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextColor(palette == null ? Color.WHITE : palette.textPrimary);
        tvTitle.setTextSize(14f);
        textColumn.addView(tvTitle);

        TextView tvValue = new TextView(this);
        tvValue.setText(value);
        tvValue.setTextColor(palette == null ? Color.parseColor("#A2B0C3") : palette.textSecondary);
        tvValue.setTextSize(12f);
        tvValue.setPadding(0, dp(4), 0, 0);
        textColumn.addView(tvValue);

        row.addView(textColumn);
        return row;
    }

    private void showOptionListDialog(String title, String subtitle, String[] labels, int checkedIndex, final OptionSelectListener listener) {
        final Dialog dialog = createAppDialog(title, subtitle);
        ListView listView = (ListView) dialog.findViewById(R.id.lvDialogItems);
        Button btnPrimary = (Button) dialog.findViewById(R.id.btnDialogPrimary);
        Button btnSecondary = (Button) dialog.findViewById(R.id.btnDialogSecondary);
        Button btnNeutral = (Button) dialog.findViewById(R.id.btnDialogNeutral);
        listView.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, labels));
        if (checkedIndex >= 0 && checkedIndex < labels.length) {
            listView.setSelection(checkedIndex);
        }
        listView.setOnItemClickListener((parent, view, which, id) -> {
            dialog.dismiss();
            if (listener != null) {
                listener.onSelected(which);
            }
        });
        btnPrimary.setVisibility(View.GONE);
        btnNeutral.setVisibility(View.GONE);
        btnSecondary.setText("关闭");
        btnSecondary.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private Dialog createAppDialog(String title, String subtitle) {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_issue_center);
        View content = dialog.findViewById(R.id.tvDialogTitle).getRootView();
        if (palette != null) {
            themeManager.applyTaggedStyles(content, palette);
        }
        ((TextView) dialog.findViewById(R.id.tvDialogTitle)).setText(title);
        ((TextView) dialog.findViewById(R.id.tvDialogSubtitle)).setText(subtitle);
        dialog.setCancelable(true);
        return dialog;
    }

    private void showDirectoryDialog(final String title, File initialDir) {
        final ArrayList<File> visibleFiles = new ArrayList<File>();
        final ArrayList<String> labels = new ArrayList<String>();
        final ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, labels);
        final File[] currentDir = {initialDir};
        final ListView listView = new ListView(this);
        listView.setAdapter(adapter);

        final AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(title)
            .setView(listView)
            .setNegativeButton("关闭", null)
            .create();

        final Runnable refresh = new Runnable() {
            @Override
            public void run() {
                populateDirectoryEntries(currentDir[0], visibleFiles, labels);
                dialog.setTitle(title + "\n" + currentDir[0].getAbsolutePath());
                adapter.notifyDataSetChanged();
            }
        };

        listView.setOnItemClickListener((parent, view, position, id) -> {
            File file = visibleFiles.get(position);
            if (file == null) {
                File parentDir = currentDir[0].getParentFile();
                if (parentDir != null) {
                    currentDir[0] = parentDir;
                    refresh.run();
                }
                return;
            }
            if (file.isDirectory()) {
                currentDir[0] = file;
                refresh.run();
                return;
            }
            toast(file.getName());
        });

        refresh.run();
        dialog.show();
    }

    private void populateDirectoryEntries(File dir, ArrayList<File> visibleFiles, ArrayList<String> labels) {
        visibleFiles.clear();
        labels.clear();
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            labels.add("目录不存在");
            visibleFiles.add(new File(""));
            return;
        }
        if (dir.getParentFile() != null) {
            visibleFiles.add(null);
            labels.add(".. 返回上一级");
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        ArrayList<File> dirs = new ArrayList<File>();
        ArrayList<File> normals = new ArrayList<File>();
        for (int i = 0; i < files.length; i++) {
            if (files[i].isDirectory()) {
                dirs.add(files[i]);
            } else {
                normals.add(files[i]);
            }
        }
        java.util.Collections.sort(dirs, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        java.util.Collections.sort(normals, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (int i = 0; i < dirs.size(); i++) {
            visibleFiles.add(dirs.get(i));
            labels.add("📁 " + dirs.get(i).getName());
        }
        for (int i = 0; i < normals.size(); i++) {
            visibleFiles.add(normals.get(i));
            labels.add("📄 " + normals.get(i).getName());
        }
    }

    private void showProgressOverlay(String title, String message, int percent, boolean indeterminate) {
        progressOverlay.setVisibility(View.VISIBLE);
        tvProgressTitle.setText(title);
        tvProgressMessage.setText(message);
        progressBarInstall.setIndeterminate(indeterminate);
        if (!indeterminate) {
            progressBarInstall.setProgress(percent);
            tvProgressPercent.setText(percent + "%");
        } else {
            tvProgressPercent.setText("处理中");
        }
    }

    private void hideProgressOverlay() {
        progressOverlay.setVisibility(View.GONE);
        progressBarInstall.setIndeterminate(false);
        progressBarInstall.setProgress(0);
        tvProgressPercent.setText("");
    }

    private String buildEnvironmentSummary() {
        StringBuilder builder = new StringBuilder();
        builder.append("Android SDK：").append(environmentManager.isAndroidSdkRegistered(environmentState) ? "已准备" : "未准备").append('\n');
        builder.append("JDK：").append(buildInstalledJdkSummary()).append('\n');
        builder.append("NDK：").append(buildInstalledNdkSummary()).append('\n');
        builder.append("Gradle：").append(buildManager.isOfflineGradlePrepared() ? "8.7 已准备" : "8.7 未准备").append('\n');
        builder.append("当前路线：").append(environmentManager.getDownloadRegionLabel()).append('\n');
        builder.append("当前偏好：").append(simplifyToolLabel(environmentManager.getSelectedJdkName(selectedJdkIndex))).append(" / ")
            .append(simplifyToolLabel(environmentManager.getSelectedNdkName(selectedNdkIndex)));
        return builder.toString();
    }

    private String buildEmbeddedSummary() {
        String sdkStatus = environmentManager.isAndroidSdkRegistered(environmentState) ? "已准备" : "未准备";
        int embeddedJdk = EnvironmentManager.DEFAULT_JDK_INDEX;
        int embeddedNdk = EnvironmentManager.DEFAULT_NDK_INDEX;
        String jdkStatus = embeddedJdk >= 0 && environmentManager.isSelectedJdkInstalled(embeddedJdk, environmentState) ? "已准备" : "未准备";
        String ndkStatus = embeddedNdk >= 0 && environmentManager.isSelectedNdkInstalled(embeddedNdk, environmentState) ? "已准备" : "未准备";
        String gradleStatus = buildManager.isOfflineGradlePrepared() ? "已准备" : "未准备";
        return "环境不是只有一个版本。这里会统一检查 Android SDK、JDK、NDK、Gradle。"
            + "\n当前路线：" + environmentManager.getDownloadRouteDisplayName()
            + "\n当前状态：SDK " + sdkStatus + "；JDK " + buildInstalledJdkSummary() + "；NDK " + buildInstalledNdkSummary() + "；Gradle " + gradleStatus + "。";
    }

    private String buildConfigSummary() {
        return "你可以分别切换 JDK、NDK 和下载路线。"
            + "\n国内路线优先镜像，国外路线优先官方源。"
            + "\n打包前应用还会根据 `compileSdk`、`ndkVersion`、Gradle Wrapper 和架构做推荐。";
    }

    private String buildGradleSummary() {
        return buildManager.isOfflineGradlePrepared()
            ? "Gradle 8.7 已就绪，打包时会优先直接使用外置 Gradle。"
            : "Gradle 8.7 还没准备好，点击上方按钮后会自动从外置直链下载并解压。";
    }

    private String buildThemeSummary() {
        String appearanceLabel = "跟随系统";
        if (ThemeManager.MODE_LIGHT.equals(themeManager.getAppearanceMode())) {
            appearanceLabel = "浅色";
        } else if (ThemeManager.MODE_DARK.equals(themeManager.getAppearanceMode())) {
            appearanceLabel = "深色";
        }
        String surfaceLabel = ThemeManager.STYLE_LIQUID.equals(themeManager.getSurfaceStyle()) ? "液态玻璃主题" : "正常主题";
        return "亮暗模式：" + appearanceLabel + "；材质主题：" + surfaceLabel
            + "。液态玻璃已经改成更克制的圆角、边框和背景漂浮感。";
    }

    private String buildDirectoryPlan() {
        StringBuilder builder = new StringBuilder();
        builder.append("工作根目录：").append(environmentManager.getBaseDir()).append('\n');
        builder.append('\n');
        builder.append("`packages/`：保存外置下载回来的原始压缩包，避免重复下载。").append('\n');
        builder.append("`sdk/`：Android SDK 的实际工作目录，`cmdline-tools` 会自动整理到 `latest/`。").append('\n');
        builder.append("`jdk/`：固定存放 JDK 21。").append('\n');
        builder.append("`ndk/`：固定存放 NDK r27。").append('\n');
        builder.append("`gradle/`：固定存放 Gradle 8.7 的离线运行目录。").append('\n');
        builder.append("下载路线：").append(environmentManager.getDownloadRouteDisplayName()).append("。切换路线后会影响后续下载、缓存命中顺序和 Wrapper 默认地址。").append('\n');
        builder.append("`projects/`：你在首页新建出来的项目。").append('\n');
        builder.append("`android/data/`：导入进来的现有 Android 项目。").append('\n');
        builder.append("`import_temp/`：导入压缩包时的临时解压区，识别完工程根目录后再正式导入。");
        return builder.toString();
    }

    private String buildPrepareButtonText() {
        boolean allReady = environmentManager.isAndroidSdkRegistered(environmentState)
            && environmentManager.isSelectedJdkInstalled(selectedJdkIndex, environmentState)
            && environmentManager.isSelectedNdkInstalled(selectedNdkIndex, environmentState)
            && buildManager.isOfflineGradlePrepared();
        return allReady ? "重新检测环境" : "自动检测环境";
    }

    private int blendProgress(int startPercent, int weightPercent, int stagePercent) {
        int safeStage = Math.max(0, Math.min(100, stagePercent));
        return Math.max(0, Math.min(100, startPercent + ((safeStage * weightPercent) / 100)));
    }

    private String buildInstalledJdkSummary() {
        ArrayList<String> versions = new ArrayList<String>();
        for (String name : environmentState.getInstalledJdks().keySet()) {
            versions.add(extractVersionTag(name).replace("JDK ", ""));
        }
        if (versions.isEmpty()) {
            return "未准备";
        }
        return android.text.TextUtils.join("、", versions);
    }

    private String buildInstalledNdkSummary() {
        ArrayList<String> versions = new ArrayList<String>();
        for (String name : environmentState.getInstalledNdks().keySet()) {
            versions.add(extractVersionTag(name).replace("NDK ", "").replace(" ", ""));
        }
        if (versions.isEmpty()) {
            return "未准备";
        }
        return android.text.TextUtils.join("、", versions);
    }

    private String simplifyToolLabel(String name) {
        String value = safeText(name, "未选择");
        return value.replace(" (长期支持版)", "")
            .replace(" (当前推荐版)", "")
            .replace(" (前沿版本)", "")
            .replace(" (实验版本)", "")
            .replace("稳定版，推荐", "推荐")
            .trim();
    }

    private String extractVersionTag(String name) {
        String clean = simplifyToolLabel(name);
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(JDK\\s*\\d+|NDK\\s*r\\d+[A-Za-z0-9]*)").matcher(clean);
        if (matcher.find()) {
            return matcher.group(1).replaceAll("\\s+", " ");
        }
        matcher = java.util.regex.Pattern.compile("(\\d+(?:\\.\\d+)?)").matcher(clean);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return clean;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private String safeText(String value, String fallback) {
        if (value == null || value.trim().length() == 0) {
            return fallback;
        }
        return value.trim();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private interface OptionSelectListener {
        void onSelected(int which);
    }
}
