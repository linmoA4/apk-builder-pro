package com.LM.pack.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.LM.pack.R;
import com.LM.pack.model.FileTreeItem;
import com.LM.pack.theme.AppThemePalette;
import com.LM.pack.theme.ThemeManager;
import java.io.File;
import java.util.List;

public class FileTreeListAdapter extends BaseAdapter {

    private final Context context;
    private final ThemeManager themeManager;
    private final List<FileTreeItem> fileTreeItems;
    private AppThemePalette palette;
    private String projectRoot;

    public FileTreeListAdapter(Context context, ThemeManager themeManager, List<FileTreeItem> fileTreeItems) {
        this.context = context;
        this.themeManager = themeManager;
        this.fileTreeItems = fileTreeItems;
    }

    public void setPalette(AppThemePalette palette) {
        this.palette = palette;
        notifyDataSetChanged();
    }

    public void setProjectRoot(String projectRoot) {
        this.projectRoot = projectRoot;
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return fileTreeItems.size();
    }

    @Override
    public Object getItem(int position) {
        return fileTreeItems.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        FileTreeRowHolder holder;
        if (convertView == null) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(16), dp(10), dp(12), dp(10));

            TextView title = new TextView(context);
            title.setTextSize(12.5f);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(title);

            TextView sub = new TextView(context);
            sub.setTextSize(10.5f);
            sub.setPadding(0, dp(3), 0, 0);
            sub.setSingleLine(true);
            sub.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            row.addView(sub);

            holder = new FileTreeRowHolder(title, sub);
            row.setTag(holder);
            convertView = row;
        } else {
            holder = (FileTreeRowHolder) convertView.getTag();
        }
        FileTreeItem item = fileTreeItems.get(position);
        String prefix = item.isDirectory() ? "›  " : "·  ";
        convertView.setBackground(palette == null ? roundedDrawable("#151B24", "", 8) : themeManager.createPanelDrawable(palette, false));
        holder.title.setText(prefix + item.getFile().getName());
        holder.title.setTextColor(item.isDirectory()
            ? (palette == null ? Color.parseColor("#F3F7FD") : palette.textPrimary)
            : (palette == null ? Color.parseColor("#B8C9E0") : palette.textSecondary));
        holder.sub.setText(buildSecondaryText(item));
        holder.sub.setTextColor(palette == null ? Color.parseColor("#7D8DA4") : palette.textMuted);
        holder.sub.setVisibility(View.VISIBLE);
        return convertView;
    }

    private String buildSecondaryText(FileTreeItem item) {
        if (item == null || item.getFile() == null) {
            return "";
        }
        File parent = item.getFile().getParentFile();
        if (projectRoot == null || projectRoot.length() == 0 || parent == null) {
            return item.isDirectory()
                ? context.getString(R.string.file_tree_folder_short)
                : context.getString(R.string.file_tree_file_short);
        }
        String parentPath = parent.getAbsolutePath();
        if (parentPath.startsWith(projectRoot)) {
            String relative = parentPath.substring(projectRoot.length());
            if (relative.length() == 0) {
                relative = "/";
            }
            return item.isDirectory()
                ? context.getString(R.string.file_tree_directory_relative_format, relative)
                : context.getString(R.string.file_tree_file_relative_format, relative);
        }
        return item.isDirectory()
            ? context.getString(R.string.file_tree_directory_short)
            : context.getString(R.string.file_tree_file_short);
    }

    private int dp(int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density);
    }

    private GradientDrawable roundedDrawable(String fillColor, String strokeColor, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.parseColor(fillColor));
        drawable.setCornerRadius(dp((int) radiusDp));
        if (strokeColor != null && strokeColor.length() > 0) {
            drawable.setStroke(1, Color.parseColor(strokeColor));
        }
        return drawable;
    }

    private static class FileTreeRowHolder {
        final TextView title;
        final TextView sub;

        FileTreeRowHolder(TextView title, TextView sub) {
            this.title = title;
            this.sub = sub;
        }
    }
}
