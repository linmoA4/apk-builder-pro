# 保留在 XML 中直接引用的自定义 View 构造方法
-keep class com.LM.pack.theme.GlassProgressBarView {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

-keep class com.LM.pack.theme.LiquidGlassBackgroundView {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

-keep class com.LM.pack.editor.CodeEditorView {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# 保留当前项目中的 Adapter 类，避免混淆后排查 UI 问题困难
-keep class com.LM.pack.ui.FileTreeListAdapter { *; }
-keep class com.LM.pack.ui.ProjectListAdapter { *; }
-keep class com.LM.pack.FileBrowserActivity$FileListAdapter { *; }
-keep class com.LM.pack.MainActivity$ViewPager2Adapter { *; }
-keep class com.LM.pack.MainActivity$ViewPager2Adapter$PageHolder { *; }
