package com.LM.pack.theme;

public class AppThemePalette {

    public final boolean dark;
    public final boolean liquid;
    public final int backgroundStart;
    public final int backgroundMid;
    public final int backgroundEnd;
    public final int orbPrimary;
    public final int orbSecondary;
    public final int orbTertiary;
    public final int noiseColor;
    public final int surface;
    public final int surfaceRaised;
    public final int surfaceMuted;
    public final int chipSurface;
    public final int stroke;
    public final int fresnelStroke;
    public final int accent;
    public final int accentStrong;
    public final int accentWarn;
    public final int accentSuccess;
    public final int warningSurface;
    public final int warningStroke;
    public final int textPrimary;
    public final int textSecondary;
    public final int textMuted;
    public final int textChip;
    public final int gutter;
    public final int lineNumber;
    public final int editorComment;
    public final int editorString;
    public final int editorAnnotation;
    public final int editorKeyword;
    public final int editorNumber;
    public final int editorClass;
    public final int editorXmlTag;
    public final int editorXmlAttr;

    private AppThemePalette(Builder builder) {
        this.dark = builder.dark;
        this.liquid = builder.liquid;
        this.backgroundStart = builder.backgroundStart;
        this.backgroundMid = builder.backgroundMid;
        this.backgroundEnd = builder.backgroundEnd;
        this.orbPrimary = builder.orbPrimary;
        this.orbSecondary = builder.orbSecondary;
        this.orbTertiary = builder.orbTertiary;
        this.noiseColor = builder.noiseColor;
        this.surface = builder.surface;
        this.surfaceRaised = builder.surfaceRaised;
        this.surfaceMuted = builder.surfaceMuted;
        this.chipSurface = builder.chipSurface;
        this.stroke = builder.stroke;
        this.fresnelStroke = builder.fresnelStroke;
        this.accent = builder.accent;
        this.accentStrong = builder.accentStrong;
        this.accentWarn = builder.accentWarn;
        this.accentSuccess = builder.accentSuccess;
        this.warningSurface = builder.warningSurface;
        this.warningStroke = builder.warningStroke;
        this.textPrimary = builder.textPrimary;
        this.textSecondary = builder.textSecondary;
        this.textMuted = builder.textMuted;
        this.textChip = builder.textChip;
        this.gutter = builder.gutter;
        this.lineNumber = builder.lineNumber;
        this.editorComment = builder.editorComment;
        this.editorString = builder.editorString;
        this.editorAnnotation = builder.editorAnnotation;
        this.editorKeyword = builder.editorKeyword;
        this.editorNumber = builder.editorNumber;
        this.editorClass = builder.editorClass;
        this.editorXmlTag = builder.editorXmlTag;
        this.editorXmlAttr = builder.editorXmlAttr;
    }

    public static class Builder {
        private boolean dark;
        private boolean liquid;
        private int backgroundStart;
        private int backgroundMid;
        private int backgroundEnd;
        private int orbPrimary;
        private int orbSecondary;
        private int orbTertiary;
        private int noiseColor;
        private int surface;
        private int surfaceRaised;
        private int surfaceMuted;
        private int chipSurface;
        private int stroke;
        private int fresnelStroke;
        private int accent;
        private int accentStrong;
        private int accentWarn;
        private int accentSuccess;
        private int warningSurface;
        private int warningStroke;
        private int textPrimary;
        private int textSecondary;
        private int textMuted;
        private int textChip;
        private int gutter;
        private int lineNumber;
        private int editorComment;
        private int editorString;
        private int editorAnnotation;
        private int editorKeyword;
        private int editorNumber;
        private int editorClass;
        private int editorXmlTag;
        private int editorXmlAttr;

        public Builder dark(boolean dark) { this.dark = dark; return this; }
        public Builder liquid(boolean liquid) { this.liquid = liquid; return this; }
        public Builder backgroundStart(int v) { this.backgroundStart = v; return this; }
        public Builder backgroundMid(int v) { this.backgroundMid = v; return this; }
        public Builder backgroundEnd(int v) { this.backgroundEnd = v; return this; }
        public Builder orbPrimary(int v) { this.orbPrimary = v; return this; }
        public Builder orbSecondary(int v) { this.orbSecondary = v; return this; }
        public Builder orbTertiary(int v) { this.orbTertiary = v; return this; }
        public Builder noiseColor(int v) { this.noiseColor = v; return this; }
        public Builder surface(int v) { this.surface = v; return this; }
        public Builder surfaceRaised(int v) { this.surfaceRaised = v; return this; }
        public Builder surfaceMuted(int v) { this.surfaceMuted = v; return this; }
        public Builder chipSurface(int v) { this.chipSurface = v; return this; }
        public Builder stroke(int v) { this.stroke = v; return this; }
        public Builder fresnelStroke(int v) { this.fresnelStroke = v; return this; }
        public Builder accent(int v) { this.accent = v; return this; }
        public Builder accentStrong(int v) { this.accentStrong = v; return this; }
        public Builder accentWarn(int v) { this.accentWarn = v; return this; }
        public Builder accentSuccess(int v) { this.accentSuccess = v; return this; }
        public Builder warningSurface(int v) { this.warningSurface = v; return this; }
        public Builder warningStroke(int v) { this.warningStroke = v; return this; }
        public Builder textPrimary(int v) { this.textPrimary = v; return this; }
        public Builder textSecondary(int v) { this.textSecondary = v; return this; }
        public Builder textMuted(int v) { this.textMuted = v; return this; }
        public Builder textChip(int v) { this.textChip = v; return this; }
        public Builder gutter(int v) { this.gutter = v; return this; }
        public Builder lineNumber(int v) { this.lineNumber = v; return this; }
        public Builder editorComment(int v) { this.editorComment = v; return this; }
        public Builder editorString(int v) { this.editorString = v; return this; }
        public Builder editorAnnotation(int v) { this.editorAnnotation = v; return this; }
        public Builder editorKeyword(int v) { this.editorKeyword = v; return this; }
        public Builder editorNumber(int v) { this.editorNumber = v; return this; }
        public Builder editorClass(int v) { this.editorClass = v; return this; }
        public Builder editorXmlTag(int v) { this.editorXmlTag = v; return this; }
        public Builder editorXmlAttr(int v) { this.editorXmlAttr = v; return this; }

        public AppThemePalette build() {
            return new AppThemePalette(this);
        }
    }
}
