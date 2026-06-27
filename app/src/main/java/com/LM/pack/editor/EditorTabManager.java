package com.LM.pack.editor;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.LM.pack.theme.AppThemePalette;
import java.io.File;
import java.util.ArrayList;

public class EditorTabManager {

    public interface TabListener {
        void onTabSelected(File file);
        void onActiveTabClosed(File fallbackFile);
    }

    private final Context context;
    private final LinearLayout container;
    private final ArrayList<File> openFiles = new ArrayList<File>();
    private File activeFile;
    private TabListener tabListener;
    private AppThemePalette palette;

    public EditorTabManager(Context context, LinearLayout container) {
        this.context = context;
        this.container = container;
    }

    public void setTabListener(TabListener tabListener) {
        this.tabListener = tabListener;
    }

    public void setPalette(AppThemePalette palette) {
        this.palette = palette;
        render();
    }

    public void clear() {
        openFiles.clear();
        activeFile = null;
        render();
    }

    public void openTab(File file) {
        if (file == null) {
            return;
        }
        if (!containsFile(file)) {
            openFiles.add(file);
        }
        activeFile = file;
        render();
    }

    public void activate(File file) {
        if (file == null || !containsFile(file)) {
            return;
        }
        activeFile = file;
        render();
    }

    public boolean hasOpenTabs() {
        return !openFiles.isEmpty();
    }

    private boolean containsFile(File file) {
        for (int i = 0; i < openFiles.size(); i++) {
            if (sameFile(openFiles.get(i), file)) {
                return true;
            }
        }
        return false;
    }

    private void closeTab(File file) {
        if (file == null) {
            return;
        }
        int closedIndex = -1;
        for (int i = 0; i < openFiles.size(); i++) {
            if (sameFile(openFiles.get(i), file)) {
                closedIndex = i;
                openFiles.remove(i);
                break;
            }
        }
        if (closedIndex < 0) {
            return;
        }
        File fallback = activeFile;
        if (sameFile(activeFile, file)) {
            if (openFiles.isEmpty()) {
                activeFile = null;
                fallback = null;
            } else if (closedIndex > 0) {
                activeFile = openFiles.get(closedIndex - 1);
                fallback = activeFile;
            } else {
                activeFile = openFiles.get(0);
                fallback = activeFile;
            }
            if (tabListener != null) {
                tabListener.onActiveTabClosed(fallback);
            }
        }
        render();
    }

    private void render() {
        container.removeAllViews();
        if (openFiles.isEmpty()) {
            TextView placeholder = new TextView(context);
            placeholder.setText("未打开文件");
            placeholder.setTextColor(palette == null ? Color.parseColor("#7D8DA4") : palette.textMuted);
            placeholder.setTextSize(12f);
            placeholder.setGravity(Gravity.CENTER_VERTICAL);
            placeholder.setPadding(dp(10), 0, dp(10), 0);
            container.addView(placeholder, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
            return;
        }

        for (int i = 0; i < openFiles.size(); i++) {
            final File file = openFiles.get(i);
            final boolean active = sameFile(file, activeFile);

            TextView tab = new TextView(context);
            tab.setText(file.getName());
            tab.setTextColor(resolveTabTextColor(active));
            tab.setTextSize(12f);
            tab.setGravity(Gravity.CENTER_VERTICAL);
            tab.setPadding(dp(12), 0, dp(12), 0);
            tab.setBackground(buildTabBackground(active));
            tab.setSingleLine(true);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(26));
            if (i > 0) {
                params.leftMargin = dp(6);
            }
            tab.setLayoutParams(params);
            tab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    activeFile = file;
                    render();
                    if (tabListener != null) {
                        tabListener.onTabSelected(file);
                    }
                }
            });
            tab.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    closeTab(file);
                    return true;
                }
            });
            container.addView(tab);
        }
    }

    private GradientDrawable buildTabBackground(boolean active) {
        GradientDrawable drawable = new GradientDrawable();
        boolean liquid = palette != null && palette.liquid;
        drawable.setCornerRadius(dp(liquid ? 20 : 10));
        drawable.setColor(resolveTabBackgroundColor(active));
        drawable.setStroke(1, active
            ? (palette == null ? Color.parseColor("#4E89FF") : palette.accent)
            : (palette == null ? Color.parseColor("#263246") : (palette.liquid ? palette.fresnelStroke : palette.stroke)));
        return drawable;
    }

    private int resolveTabTextColor(boolean active) {
        if (palette == null) {
            return Color.parseColor(active ? "#DCE7F8" : "#A2B0C3");
        }
        return active ? palette.textChip : palette.textSecondary;
    }

    private int resolveTabBackgroundColor(boolean active) {
        if (palette == null) {
            return Color.parseColor(active ? "#1C273A" : "#151B24");
        }
        return active ? palette.chipSurface : palette.surfaceMuted;
    }

    private boolean sameFile(File first, File second) {
        if (first == null || second == null) {
            return false;
        }
        return first.getAbsolutePath().equals(second.getAbsolutePath());
    }

    private int dp(int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density);
    }
}
