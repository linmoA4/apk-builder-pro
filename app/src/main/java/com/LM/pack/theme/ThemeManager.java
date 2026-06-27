package com.LM.pack.theme;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import com.LM.pack.env.EnvironmentManager;

public class ThemeManager {

    public static final String MODE_SYSTEM = "system";
    public static final String MODE_LIGHT = "light";
    public static final String MODE_DARK = "dark";

    public static final String STYLE_NORMAL = "normal";
    public static final String STYLE_LIQUID = "liquid";

    private static final String KEY_APPEARANCE_MODE = "appearance_mode";
    private static final String KEY_SURFACE_STYLE = "surface_style";

    private final SharedPreferences sharedPreferences;

    public ThemeManager(Context context) {
        this.sharedPreferences = context.getSharedPreferences(EnvironmentManager.PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String getAppearanceMode() {
        return safeValue(sharedPreferences.getString(KEY_APPEARANCE_MODE, MODE_SYSTEM), MODE_SYSTEM);
    }

    public void setAppearanceMode(String mode) {
        sharedPreferences.edit().putString(KEY_APPEARANCE_MODE, safeValue(mode, MODE_SYSTEM)).apply();
    }

    public String getSurfaceStyle() {
        return safeValue(sharedPreferences.getString(KEY_SURFACE_STYLE, STYLE_NORMAL), STYLE_NORMAL);
    }

    public void setSurfaceStyle(String style) {
        sharedPreferences.edit().putString(KEY_SURFACE_STYLE, safeValue(style, STYLE_NORMAL)).apply();
    }

    public boolean isDarkActive(Context context) {
        String mode = getAppearanceMode();
        if (MODE_DARK.equals(mode)) {
            return true;
        }
        if (MODE_LIGHT.equals(mode)) {
            return false;
        }
        int nightModeFlags = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES;
    }

    public AppThemePalette getPalette(Context context) {
        boolean dark = isDarkActive(context);
        boolean liquid = STYLE_LIQUID.equals(getSurfaceStyle());
        if (liquid && dark) {
            return new AppThemePalette(
                true,
                true,
                Color.parseColor("#090B14"),
                Color.parseColor("#11142A"),
                Color.parseColor("#05060C"),
                Color.parseColor("#7A56FF"),
                Color.parseColor("#3B82F6"),
                Color.parseColor("#7C3AED"),
                Color.parseColor("#12FFFFFF"),
                Color.parseColor("#12FFFFFF"),
                Color.parseColor("#1EFFFFFF"),
                Color.parseColor("#18F5F7FF"),
                Color.parseColor("#18FFFFFF"),
                Color.parseColor("#24FFFFFF"),
                Color.parseColor("#4DD9EEFF"),
                Color.parseColor("#7C6CFF"),
                Color.parseColor("#9D8BFF"),
                Color.parseColor("#FF7A93"),
                Color.parseColor("#38C59A"),
                Color.parseColor("#26FF8598"),
                Color.parseColor("#40FFD0DA"),
                Color.parseColor("#F4F7FF"),
                Color.parseColor("#D4DAF0"),
                Color.parseColor("#AAB4D0"),
                Color.parseColor("#F2F6FF"),
                Color.parseColor("#12FFFFFF"),
                Color.parseColor("#A8B6E0"),
                Color.parseColor("#9FD5A8"),
                Color.parseColor("#FFD48E"),
                Color.parseColor("#A9D4FF"),
                Color.parseColor("#E0A4FF"),
                Color.parseColor("#C4F09C"),
                Color.parseColor("#81E6FF"),
                Color.parseColor("#8CF0E2"),
                Color.parseColor("#9FD5FF")
            );
        }
        if (liquid) {
            return new AppThemePalette(
                false,
                true,
                Color.parseColor("#F6F8FF"),
                Color.parseColor("#E8EEFF"),
                Color.parseColor("#F9FBFF"),
                Color.parseColor("#9C7CFF"),
                Color.parseColor("#66B4FF"),
                Color.parseColor("#B06BFF"),
                Color.parseColor("#10FFFFFF"),
                Color.parseColor("#80FFFFFF"),
                Color.parseColor("#B8FFFFFF"),
                Color.parseColor("#90FFFFFF"),
                Color.parseColor("#88FFFFFF"),
                Color.parseColor("#33FFFFFF"),
                Color.parseColor("#7AE8F4FF"),
                Color.parseColor("#5B78FF"),
                Color.parseColor("#6A8FFF"),
                Color.parseColor("#E6697E"),
                Color.parseColor("#1BAA82"),
                Color.parseColor("#33FFB6C1"),
                Color.parseColor("#55FFD4DE"),
                Color.parseColor("#1A2340"),
                Color.parseColor("#40506D"),
                Color.parseColor("#67748E"),
                Color.parseColor("#203052"),
                Color.parseColor("#EEFFFFFF"),
                Color.parseColor("#7082A5"),
                Color.parseColor("#5B8A62"),
                Color.parseColor("#B67B2F"),
                Color.parseColor("#5784C6"),
                Color.parseColor("#8D56B7"),
                Color.parseColor("#7A9E3B"),
                Color.parseColor("#18709B"),
                Color.parseColor("#157F78"),
                Color.parseColor("#4F6EA6")
            );
        }
        if (dark) {
            return new AppThemePalette(
                true,
                false,
                Color.parseColor("#0F141B"),
                Color.parseColor("#111827"),
                Color.parseColor("#0B1017"),
                Color.parseColor("#2E3C66"),
                Color.parseColor("#1E3A8A"),
                Color.parseColor("#3C275F"),
                Color.parseColor("#09FFFFFF"),
                Color.parseColor("#151B24"),
                Color.parseColor("#1A2230"),
                Color.parseColor("#111821"),
                Color.parseColor("#1C273A"),
                Color.parseColor("#263246"),
                Color.parseColor("#2E80FF"),
                Color.parseColor("#4E89FF"),
                Color.parseColor("#5B9CFF"),
                Color.parseColor("#D45E6E"),
                Color.parseColor("#23A26D"),
                Color.parseColor("#331E24"),
                Color.parseColor("#6B3B45"),
                Color.parseColor("#F3F7FD"),
                Color.parseColor("#A2B0C3"),
                Color.parseColor("#7D8DA4"),
                Color.parseColor("#DCE7F8"),
                Color.parseColor("#121A24"),
                Color.parseColor("#60748F"),
                Color.parseColor("#5E7F6E"),
                Color.parseColor("#D7BA7D"),
                Color.parseColor("#7FB0FF"),
                Color.parseColor("#C586C0"),
                Color.parseColor("#B5CEA8"),
                Color.parseColor("#4EC9B0"),
                Color.parseColor("#4EC9B0"),
                Color.parseColor("#9CDCFE")
            );
        }
        return new AppThemePalette(
            false,
            false,
            Color.parseColor("#F7FAFF"),
            Color.parseColor("#EEF3FF"),
            Color.parseColor("#F3F6FC"),
            Color.parseColor("#C7D8FF"),
            Color.parseColor("#B4CFFF"),
            Color.parseColor("#E0D4FF"),
            Color.parseColor("#050B1A26"),
            Color.parseColor("#FFFFFF"),
            Color.parseColor("#F7FAFF"),
            Color.parseColor("#EEF2F8"),
            Color.parseColor("#EEF4FF"),
            Color.parseColor("#D4DEEC"),
            Color.parseColor("#E7F0FF"),
            Color.parseColor("#3E6DFF"),
            Color.parseColor("#2B59D6"),
            Color.parseColor("#CF5A67"),
            Color.parseColor("#22956C"),
            Color.parseColor("#FFF1F3"),
            Color.parseColor("#F4C6CC"),
            Color.parseColor("#162033"),
            Color.parseColor("#4B5C76"),
            Color.parseColor("#748398"),
            Color.parseColor("#203D73"),
            Color.parseColor("#F0F5FF"),
            Color.parseColor("#8AA0BF"),
            Color.parseColor("#5C7F6B"),
            Color.parseColor("#B27B34"),
            Color.parseColor("#4F84D4"),
            Color.parseColor("#8A55B9"),
            Color.parseColor("#709841"),
            Color.parseColor("#287DA2"),
            Color.parseColor("#207F78"),
            Color.parseColor("#4D76AE")
        );
    }

    public void applyActivityWindow(Activity activity, AppThemePalette palette) {
        if (activity == null || palette == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            activity.getWindow().setStatusBarColor(Color.TRANSPARENT);
            activity.getWindow().setNavigationBarColor(withAlpha(palette.backgroundEnd, palette.dark ? 240 : 225));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int flags = activity.getWindow().getDecorView().getSystemUiVisibility();
            if (!palette.dark) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            } else {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            activity.getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }

    public void applyTaggedStyles(View root, AppThemePalette palette) {
        if (root == null || palette == null) {
            return;
        }
        Object tagObject = root.getTag();
        if (tagObject instanceof String) {
            String[] tokens = ((String) tagObject).split("\\s+");
            for (int i = 0; i < tokens.length; i++) {
                applyToken(root, palette, tokens[i]);
            }
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyTaggedStyles(group.getChildAt(i), palette);
            }
        }
    }

    private void applyToken(View view, AppThemePalette palette, String token) {
        if (token == null || token.length() == 0) {
            return;
        }
        if ("panel".equals(token)) {
            view.setBackground(createPanelDrawable(palette, false));
        } else if ("panelRaised".equals(token)) {
            view.setBackground(createPanelDrawable(palette, true));
        } else if ("editorSurface".equals(token)) {
            view.setBackground(createEditorDrawable(palette));
        } else if ("ghostButton".equals(token)) {
            view.setBackground(createGhostButtonDrawable(palette));
        } else if ("primaryButton".equals(token)) {
            view.setBackground(createPrimaryButtonDrawable(palette));
        } else if ("successButton".equals(token)) {
            view.setBackground(createSuccessButtonDrawable(palette));
        } else if ("warnButton".equals(token)) {
            view.setBackground(createWarnButtonDrawable(palette));
        } else if ("chip".equals(token)) {
            view.setBackground(createChipDrawable(palette));
        } else if ("warningCard".equals(token)) {
            view.setBackground(createWarningCardDrawable(palette));
        } else if ("primary".equals(token)) {
            applyTextColor(view, palette.textPrimary, palette.textMuted);
        } else if ("secondary".equals(token)) {
            applyTextColor(view, palette.textSecondary, palette.textMuted);
        } else if ("muted".equals(token)) {
            applyTextColor(view, palette.textMuted, palette.textMuted);
        } else if ("chipText".equals(token)) {
            applyTextColor(view, palette.textChip, palette.textMuted);
        } else if ("warningText".equals(token)) {
            applyTextColor(view, palette.accentWarn, palette.textMuted);
        }
    }

    private void applyTextColor(View view, int textColor, int hintColor) {
        if (!(view instanceof TextView)) {
            return;
        }
        TextView textView = (TextView) view;
        textView.setTextColor(textColor);
        textView.setHintTextColor(hintColor);
    }

    public Drawable createPanelDrawable(AppThemePalette palette, boolean raised) {
        int fill = raised ? palette.surfaceRaised : palette.surface;
        float radius = palette.liquid ? (raised ? 22f : 16f) : (raised ? 14f : 11f);
        return createSurfaceDrawable(fill, palette, radius);
    }

    public Drawable createEditorDrawable(AppThemePalette palette) {
        float radius = palette.liquid ? 18f : 12f;
        return createSurfaceDrawable(palette.surfaceMuted, palette, radius);
    }

    public Drawable createChipDrawable(AppThemePalette palette) {
        float radius = palette.liquid ? 14f : 10f;
        return createSurfaceDrawable(palette.chipSurface, palette, radius);
    }

    public Drawable createGhostButtonDrawable(AppThemePalette palette) {
        float radius = palette.liquid ? 14f : 10f;
        return createSurfaceDrawable(palette.chipSurface, palette, radius);
    }

    public Drawable createPrimaryButtonDrawable(AppThemePalette palette) {
        return createSolidButtonDrawable(palette, palette.accent, palette.accentStrong);
    }

    public Drawable createSuccessButtonDrawable(AppThemePalette palette) {
        return createSolidButtonDrawable(palette, palette.accentSuccess, palette.accentStrong);
    }

    public Drawable createWarnButtonDrawable(AppThemePalette palette) {
        return createSolidButtonDrawable(palette, palette.accentWarn, palette.warningStroke);
    }

    public Drawable createWarningCardDrawable(AppThemePalette palette) {
        float radius = palette.liquid ? 18f : 12f;
        return createSurfaceDrawable(palette.warningSurface, palette, radius);
    }

    private Drawable createSolidButtonDrawable(AppThemePalette palette, int fill, int stroke) {
        float radius = palette.liquid ? 16f : 10f;
        GradientDrawable base = new GradientDrawable();
        base.setCornerRadius(dp(radius));
        base.setColor(fill);
        base.setStroke(dp(1f), stroke);
        if (!palette.liquid) {
            return base;
        }
        GradientDrawable gloss = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[] {withAlpha(Color.WHITE, 54), withAlpha(Color.WHITE, 6)}
        );
        gloss.setCornerRadius(dp(radius));
        return new LayerDrawable(new Drawable[] {base, gloss});
    }

    private Drawable createSurfaceDrawable(int fill, AppThemePalette palette, float radiusDp) {
        GradientDrawable base = new GradientDrawable();
        base.setCornerRadius(dp(radiusDp));
        base.setColor(fill);
        base.setStroke(dp(1f), palette.liquid ? palette.fresnelStroke : palette.stroke);
        if (!palette.liquid) {
            return base;
        }
        GradientDrawable gloss = new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            new int[] {
                withAlpha(Color.WHITE, 28),
                withAlpha(Color.WHITE, 7),
                withAlpha(Color.WHITE, 0)
            }
        );
        gloss.setCornerRadius(dp(radiusDp));
        GradientDrawable rim = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[] {
                withAlpha(Color.WHITE, 18),
                Color.TRANSPARENT,
                withAlpha(palette.fresnelStroke, 20)
            }
        );
        rim.setCornerRadius(dp(radiusDp));
        return new LayerDrawable(new Drawable[] {base, gloss, rim});
    }

    public int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private int dp(float value) {
        return (int) (value * Resources.getSystem().getDisplayMetrics().density);
    }

    private String safeValue(String value, String fallback) {
        if (MODE_LIGHT.equals(value) || MODE_DARK.equals(value) || MODE_SYSTEM.equals(value)
            || STYLE_NORMAL.equals(value) || STYLE_LIQUID.equals(value)) {
            return value;
        }
        return fallback;
    }
}
