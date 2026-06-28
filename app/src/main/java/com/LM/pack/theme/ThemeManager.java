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
import com.LM.pack.util.CommonUtils;

public class ThemeManager {
    // TODO: 用显式 token 映射表替代字符串 if-else 链，并为调色板参数建立分组构造方式。

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
            return new AppThemePalette.Builder()
                .dark(true)
                .liquid(true)
                .backgroundStart(Color.parseColor("#090B14"))
                .backgroundMid(Color.parseColor("#11142A"))
                .backgroundEnd(Color.parseColor("#05060C"))
                .orbPrimary(Color.parseColor("#7A56FF"))
                .orbSecondary(Color.parseColor("#3B82F6"))
                .orbTertiary(Color.parseColor("#7C3AED"))
                .noiseColor(Color.parseColor("#12FFFFFF"))
                .surface(Color.parseColor("#12FFFFFF"))
                .surfaceRaised(Color.parseColor("#1EFFFFFF"))
                .surfaceMuted(Color.parseColor("#18F5F7FF"))
                .chipSurface(Color.parseColor("#18FFFFFF"))
                .stroke(Color.parseColor("#24FFFFFF"))
                .fresnelStroke(Color.parseColor("#4DD9EEFF"))
                .accent(Color.parseColor("#7C6CFF"))
                .accentStrong(Color.parseColor("#9D8BFF"))
                .accentWarn(Color.parseColor("#FF7A93"))
                .accentSuccess(Color.parseColor("#38C59A"))
                .warningSurface(Color.parseColor("#26FF8598"))
                .warningStroke(Color.parseColor("#40FFD0DA"))
                .textPrimary(Color.parseColor("#F4F7FF"))
                .textSecondary(Color.parseColor("#D4DAF0"))
                .textMuted(Color.parseColor("#AAB4D0"))
                .textChip(Color.parseColor("#F2F6FF"))
                .gutter(Color.parseColor("#12FFFFFF"))
                .lineNumber(Color.parseColor("#A8B6E0"))
                .editorComment(Color.parseColor("#9FD5A8"))
                .editorString(Color.parseColor("#FFD48E"))
                .editorAnnotation(Color.parseColor("#A9D4FF"))
                .editorKeyword(Color.parseColor("#E0A4FF"))
                .editorNumber(Color.parseColor("#C4F09C"))
                .editorClass(Color.parseColor("#81E6FF"))
                .editorXmlTag(Color.parseColor("#8CF0E2"))
                .editorXmlAttr(Color.parseColor("#9FD5FF"))
                .build();
        }
        if (liquid) {
            return new AppThemePalette.Builder()
                .dark(false)
                .liquid(true)
                .backgroundStart(Color.parseColor("#F6F8FF"))
                .backgroundMid(Color.parseColor("#E8EEFF"))
                .backgroundEnd(Color.parseColor("#F9FBFF"))
                .orbPrimary(Color.parseColor("#9C7CFF"))
                .orbSecondary(Color.parseColor("#66B4FF"))
                .orbTertiary(Color.parseColor("#B06BFF"))
                .noiseColor(Color.parseColor("#10FFFFFF"))
                .surface(Color.parseColor("#80FFFFFF"))
                .surfaceRaised(Color.parseColor("#B8FFFFFF"))
                .surfaceMuted(Color.parseColor("#90FFFFFF"))
                .chipSurface(Color.parseColor("#88FFFFFF"))
                .stroke(Color.parseColor("#33FFFFFF"))
                .fresnelStroke(Color.parseColor("#7AE8F4FF"))
                .accent(Color.parseColor("#5B78FF"))
                .accentStrong(Color.parseColor("#6A8FFF"))
                .accentWarn(Color.parseColor("#E6697E"))
                .accentSuccess(Color.parseColor("#1BAA82"))
                .warningSurface(Color.parseColor("#33FFB6C1"))
                .warningStroke(Color.parseColor("#55FFD4DE"))
                .textPrimary(Color.parseColor("#1A2340"))
                .textSecondary(Color.parseColor("#40506D"))
                .textMuted(Color.parseColor("#67748E"))
                .textChip(Color.parseColor("#203052"))
                .gutter(Color.parseColor("#EEFFFFFF"))
                .lineNumber(Color.parseColor("#7082A5"))
                .editorComment(Color.parseColor("#5B8A62"))
                .editorString(Color.parseColor("#B67B2F"))
                .editorAnnotation(Color.parseColor("#5784C6"))
                .editorKeyword(Color.parseColor("#8D56B7"))
                .editorNumber(Color.parseColor("#7A9E3B"))
                .editorClass(Color.parseColor("#18709B"))
                .editorXmlTag(Color.parseColor("#157F78"))
                .editorXmlAttr(Color.parseColor("#4F6EA6"))
                .build();
        }
        if (dark) {
            return new AppThemePalette.Builder()
                .dark(true)
                .liquid(false)
                .backgroundStart(Color.parseColor("#0F141B"))
                .backgroundMid(Color.parseColor("#111827"))
                .backgroundEnd(Color.parseColor("#0B1017"))
                .orbPrimary(Color.parseColor("#2E3C66"))
                .orbSecondary(Color.parseColor("#1E3A8A"))
                .orbTertiary(Color.parseColor("#3C275F"))
                .noiseColor(Color.parseColor("#09FFFFFF"))
                .surface(Color.parseColor("#151B24"))
                .surfaceRaised(Color.parseColor("#1A2230"))
                .surfaceMuted(Color.parseColor("#111821"))
                .chipSurface(Color.parseColor("#1C273A"))
                .stroke(Color.parseColor("#263246"))
                .fresnelStroke(Color.parseColor("#2E80FF"))
                .accent(Color.parseColor("#4E89FF"))
                .accentStrong(Color.parseColor("#5B9CFF"))
                .accentWarn(Color.parseColor("#D45E6E"))
                .accentSuccess(Color.parseColor("#23A26D"))
                .warningSurface(Color.parseColor("#331E24"))
                .warningStroke(Color.parseColor("#6B3B45"))
                .textPrimary(Color.parseColor("#F3F7FD"))
                .textSecondary(Color.parseColor("#A2B0C3"))
                .textMuted(Color.parseColor("#7D8DA4"))
                .textChip(Color.parseColor("#DCE7F8"))
                .gutter(Color.parseColor("#121A24"))
                .lineNumber(Color.parseColor("#60748F"))
                .editorComment(Color.parseColor("#5E7F6E"))
                .editorString(Color.parseColor("#D7BA7D"))
                .editorAnnotation(Color.parseColor("#7FB0FF"))
                .editorKeyword(Color.parseColor("#C586C0"))
                .editorNumber(Color.parseColor("#B5CEA8"))
                .editorClass(Color.parseColor("#4EC9B0"))
                .editorXmlTag(Color.parseColor("#4EC9B0"))
                .editorXmlAttr(Color.parseColor("#9CDCFE"))
                .build();
        }
        return new AppThemePalette.Builder()
            .dark(false)
            .liquid(false)
            .backgroundStart(Color.parseColor("#F7FAFF"))
            .backgroundMid(Color.parseColor("#EEF3FF"))
            .backgroundEnd(Color.parseColor("#F3F6FC"))
            .orbPrimary(Color.parseColor("#C7D8FF"))
            .orbSecondary(Color.parseColor("#B4CFFF"))
            .orbTertiary(Color.parseColor("#E0D4FF"))
            .noiseColor(Color.parseColor("#050B1A26"))
            .surface(Color.parseColor("#FFFFFF"))
            .surfaceRaised(Color.parseColor("#F7FAFF"))
            .surfaceMuted(Color.parseColor("#EEF2F8"))
            .chipSurface(Color.parseColor("#EEF4FF"))
            .stroke(Color.parseColor("#D4DEEC"))
            .fresnelStroke(Color.parseColor("#E7F0FF"))
            .accent(Color.parseColor("#3E6DFF"))
            .accentStrong(Color.parseColor("#2B59D6"))
            .accentWarn(Color.parseColor("#CF5A67"))
            .accentSuccess(Color.parseColor("#22956C"))
            .warningSurface(Color.parseColor("#FFF1F3"))
            .warningStroke(Color.parseColor("#F4C6CC"))
            .textPrimary(Color.parseColor("#162033"))
            .textSecondary(Color.parseColor("#4B5C76"))
            .textMuted(Color.parseColor("#748398"))
            .textChip(Color.parseColor("#203D73"))
            .gutter(Color.parseColor("#F0F5FF"))
            .lineNumber(Color.parseColor("#8AA0BF"))
            .editorComment(Color.parseColor("#5C7F6B"))
            .editorString(Color.parseColor("#B27B34"))
            .editorAnnotation(Color.parseColor("#4F84D4"))
            .editorKeyword(Color.parseColor("#8A55B9"))
            .editorNumber(Color.parseColor("#709841"))
            .editorClass(Color.parseColor("#287DA2"))
            .editorXmlTag(Color.parseColor("#207F78"))
            .editorXmlAttr(Color.parseColor("#4D76AE"))
            .build();
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
        base.setCornerRadius(CommonUtils.dpSystem(radius));
        base.setColor(fill);
        base.setStroke(CommonUtils.dpSystem(1f), stroke);
        if (!palette.liquid) {
            return base;
        }
        GradientDrawable gloss = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[] {withAlpha(Color.WHITE, 54), withAlpha(Color.WHITE, 6)}
        );
        gloss.setCornerRadius(CommonUtils.dpSystem(radius));
        return new LayerDrawable(new Drawable[] {base, gloss});
    }

    private Drawable createSurfaceDrawable(int fill, AppThemePalette palette, float radiusDp) {
        GradientDrawable base = new GradientDrawable();
        base.setCornerRadius(CommonUtils.dpSystem(radiusDp));
        base.setColor(fill);
        base.setStroke(CommonUtils.dpSystem(1f), palette.liquid ? palette.fresnelStroke : palette.stroke);
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
        gloss.setCornerRadius(CommonUtils.dpSystem(radiusDp));
        GradientDrawable rim = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[] {
                withAlpha(Color.WHITE, 18),
                Color.TRANSPARENT,
                withAlpha(palette.fresnelStroke, 20)
            }
        );
        rim.setCornerRadius(CommonUtils.dpSystem(radiusDp));
        return new LayerDrawable(new Drawable[] {base, gloss, rim});
    }

    public int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private String safeValue(String value, String fallback) {
        if (MODE_LIGHT.equals(value) || MODE_DARK.equals(value) || MODE_SYSTEM.equals(value)
            || STYLE_NORMAL.equals(value) || STYLE_LIQUID.equals(value)) {
            return value;
        }
        return fallback;
    }
}
