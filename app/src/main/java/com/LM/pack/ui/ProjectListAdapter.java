package com.LM.pack.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.LM.pack.R;
import com.LM.pack.model.ProjectEntry;
import com.LM.pack.theme.AppThemePalette;
import com.LM.pack.theme.ThemeManager;
import java.io.File;
import java.util.List;

public class ProjectListAdapter extends BaseAdapter {

    private final Context context;
    private final ThemeManager themeManager;
    private final List<ProjectEntry> projectEntries;
    private final LruCache<String, Bitmap> iconCache = new LruCache<String, Bitmap>(12);
    private AppThemePalette palette;

    public ProjectListAdapter(Context context, ThemeManager themeManager, List<ProjectEntry> projectEntries) {
        this.context = context;
        this.themeManager = themeManager;
        this.projectEntries = projectEntries;
    }

    public void setPalette(AppThemePalette palette) {
        this.palette = palette;
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return projectEntries.size();
    }

    @Override
    public Object getItem(int position) {
        return projectEntries.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ProjectCardHolder holder;
        if (convertView == null) {
            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(14), dp(14), dp(14), dp(14));
            card.setBackground(palette == null ? roundedDrawable("#182231", "#263246", 12) : themeManager.createPanelDrawable(palette, true));

            ImageView iconView = new ImageView(context);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(52), dp(52));
            iconParams.rightMargin = dp(14);
            iconView.setLayoutParams(iconParams);
            iconView.setBackground(palette == null ? roundedDrawable("#0F141B", "#2A3850", 10) : themeManager.createChipDrawable(palette));
            iconView.setPadding(dp(8), dp(8), dp(8), dp(8));
            iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            card.addView(iconView);

            LinearLayout textColumn = new LinearLayout(context);
            textColumn.setOrientation(LinearLayout.VERTICAL);
            textColumn.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView title = new TextView(context);
            title.setTextColor(Color.parseColor("#F3F7FD"));
            title.setTextSize(14f);
            textColumn.addView(title);

            TextView sub = new TextView(context);
            sub.setTextColor(Color.parseColor("#A2B0C3"));
            sub.setTextSize(11f);
            sub.setPadding(0, dp(2), 0, 0);
            textColumn.addView(sub);

            TextView meta = new TextView(context);
            meta.setTextColor(Color.parseColor("#8FB6FF"));
            meta.setTextSize(11f);
            meta.setPadding(0, dp(4), 0, 0);
            textColumn.addView(meta);

            card.addView(textColumn);
            holder = new ProjectCardHolder(iconView, title, sub, meta);
            card.setTag(holder);
            convertView = card;
        } else {
            holder = (ProjectCardHolder) convertView.getTag();
        }

        ProjectEntry entry = projectEntries.get(position);
        convertView.setBackground(palette == null ? roundedDrawable("#182231", "#263246", 12) : themeManager.createPanelDrawable(palette, true));
        holder.icon.setBackground(palette == null ? roundedDrawable("#0F141B", "#2A3850", 10) : themeManager.createChipDrawable(palette));
        holder.title.setTextColor(palette == null ? Color.parseColor("#F3F7FD") : palette.textPrimary);
        holder.sub.setTextColor(palette == null ? Color.parseColor("#A2B0C3") : palette.textSecondary);
        holder.meta.setTextColor(palette == null ? Color.parseColor("#8FB6FF") : palette.accentStrong);
        holder.title.setText(entry.getProjectName());
        holder.sub.setText(safeText(entry.getPackageName(), context.getString(R.string.package_unrecognized)));
        holder.meta.setText(context.getString(
            R.string.project_meta_format,
            safeText(entry.getMode(), context.getString(R.string.project_mode_generic)),
            safeText(entry.getVersionName(), "1.0"),
            context.getString(R.string.project_manage_hint)
        ));
        Bitmap bitmap = loadProjectIcon(entry.getIconPath());
        if (bitmap != null) {
            holder.icon.setImageBitmap(bitmap);
        } else {
            holder.icon.setImageResource(R.drawable.app_brand);
        }
        return convertView;
    }

    private Bitmap loadProjectIcon(String iconPath) {
        if (iconPath == null || iconPath.length() == 0 || iconPath.endsWith(".xml")) {
            return null;
        }
        Bitmap cached = iconCache.get(iconPath);
        if (cached != null && !cached.isRecycled()) {
            return cached;
        }
        File iconFile = new File(iconPath);
        if (!iconFile.exists() || !iconFile.isFile()) {
            return null;
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(iconPath, bounds);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        options.inSampleSize = computeSampleSize(bounds, dp(52), dp(52));
        Bitmap bitmap = BitmapFactory.decodeFile(iconPath, options);
        if (bitmap != null) {
            iconCache.put(iconPath, bitmap);
        }
        return bitmap;
    }

    private int computeSampleSize(BitmapFactory.Options options, int targetWidth, int targetHeight) {
        int sampleSize = 1;
        int width = Math.max(1, options.outWidth);
        int height = Math.max(1, options.outHeight);
        while (width / sampleSize > targetWidth * 2 || height / sampleSize > targetHeight * 2) {
            sampleSize *= 2;
        }
        return Math.max(1, sampleSize);
    }

    private String safeText(String value, String fallback) {
        return value == null || value.length() == 0 ? fallback : value;
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

    private int dp(int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density);
    }

    private static class ProjectCardHolder {
        final ImageView icon;
        final TextView title;
        final TextView sub;
        final TextView meta;

        ProjectCardHolder(ImageView icon, TextView title, TextView sub, TextView meta) {
            this.icon = icon;
            this.title = title;
            this.sub = sub;
            this.meta = meta;
        }
    }
}
