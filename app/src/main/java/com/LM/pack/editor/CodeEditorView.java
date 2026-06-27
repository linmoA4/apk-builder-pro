package com.LM.pack.editor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.Editable;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.BackgroundColorSpan;
import android.util.AttributeSet;
import android.widget.EditText;
import com.LM.pack.theme.AppThemePalette;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CodeEditorView extends EditText {

    private static final String[] JAVA_KOTLIN_KEYWORDS = {
        "abstract", "annotation", "as", "break", "case", "catch", "class", "const", "continue",
        "data", "default", "do", "else", "enum", "extends", "false", "final", "finally", "for",
        "fun", "if", "implements", "import", "in", "instanceof", "interface", "internal", "is",
        "new", "null", "object", "open", "override", "package", "private", "protected", "public",
        "return", "sealed", "static", "super", "switch", "this", "throw", "throws", "true", "try",
        "val", "var", "void", "when", "while"
    };

    private static final String[] XML_KEYWORDS = {
        "manifest", "application", "activity", "service", "receiver", "provider", "uses-permission",
        "intent-filter", "action", "category", "LinearLayout", "FrameLayout", "TextView", "Button",
        "ScrollView", "ImageView", "EditText", "resources", "style", "color", "string", "item"
    };

    private static final String[] GRADLE_KEYWORDS = {
        "plugins", "android", "dependencies", "repositories", "classpath", "implementation",
        "namespace", "compileSdk", "compileSdkVersion", "defaultConfig", "minSdk", "targetSdk",
        "versionCode", "versionName", "buildTypes", "release", "debug", "signingConfigs",
        "buildToolsVersion", "ndkVersion", "mavenCentral", "google", "task", "apply"
    };

    private final Rect tempRect = new Rect();
    private final Paint gutterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lineNumberPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean isHighlighting;
    private String fileName = "";
    private int gutterWidth;
    private int commentColor = Color.parseColor("#5E7F6E");
    private int stringColor = Color.parseColor("#D7BA7D");
    private int annotationColor = Color.parseColor("#7FB0FF");
    private int keywordColor = Color.parseColor("#C586C0");
    private int numberColor = Color.parseColor("#B5CEA8");
    private int classColor = Color.parseColor("#4EC9B0");
    private int xmlTagColor = Color.parseColor("#4EC9B0");
    private int xmlAttrColor = Color.parseColor("#9CDCFE");
    private int errorLineColor = Color.parseColor("#33FF6B6B");

    public CodeEditorView(Context context) {
        super(context);
        init();
    }

    public CodeEditorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CodeEditorView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        gutterPaint.setColor(Color.parseColor("#121A24"));
        lineNumberPaint.setColor(Color.parseColor("#60748F"));
        lineNumberPaint.setTextSize(getTextSize() * 0.88f);
        lineNumberPaint.setTextAlign(Paint.Align.RIGHT);
        setHorizontallyScrolling(true);
        updateGutterWidth();
        addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                scheduleHighlight();
                updateGutterWidth();
            }
        });
        scheduleHighlight();
    }

    public void applyThemePalette(AppThemePalette palette) {
        if (palette == null) {
            return;
        }
        gutterPaint.setColor(palette.gutter);
        lineNumberPaint.setColor(palette.lineNumber);
        commentColor = palette.editorComment;
        stringColor = palette.editorString;
        annotationColor = palette.editorAnnotation;
        keywordColor = palette.editorKeyword;
        numberColor = palette.editorNumber;
        classColor = palette.editorClass;
        xmlTagColor = palette.editorXmlTag;
        xmlAttrColor = palette.editorXmlAttr;
        errorLineColor = adjustErrorColor(palette.accentStrong);
        setTextColor(palette.textPrimary);
        setHintTextColor(palette.textMuted);
        invalidate();
        scheduleHighlight();
    }

    public void setFileName(String fileName) {
        this.fileName = fileName == null ? "" : fileName.toLowerCase();
        scheduleHighlight();
    }

    private void updateGutterWidth() {
        int lineCount = Math.max(1, getLineCount());
        int digits = String.valueOf(lineCount).length();
        float charWidth = lineNumberPaint.measureText("0");
        gutterWidth = (int) (getPaddingTop() + digits * charWidth + dp(26));
        setPadding(gutterWidth, getPaddingTop(), getPaddingRight(), getPaddingBottom());
        invalidate();
    }

    private void scheduleHighlight() {
        removeCallbacks(highlightRunnable);
        postDelayed(highlightRunnable, 70);
    }

    private final Runnable highlightRunnable = new Runnable() {
        @Override
        public void run() {
            applyHighlighting();
        }
    };

    private void applyHighlighting() {
        if (isHighlighting) {
            return;
        }
        Editable editable = getText();
        if (editable == null) {
            return;
        }
        isHighlighting = true;
        try {
            clearColorSpans(editable);
            applyPattern(editable, Pattern.compile("(?m)//.*$"), commentColor);
            applyPattern(editable, Pattern.compile("/\\*[\\s\\S]*?\\*/"), commentColor);
            applyPattern(editable, Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\""), stringColor);
            applyPattern(editable, Pattern.compile("'(?:\\\\.|[^'\\\\])*'"), stringColor);
            applyPattern(editable, Pattern.compile("(?<![A-Za-z0-9_])@[A-Za-z0-9_:.]+"), annotationColor);
            if (isXmlFile()) {
                applyPattern(editable, Pattern.compile("</?[A-Za-z0-9_:-]+"), xmlTagColor);
                applyPattern(editable, Pattern.compile("\\b(?:android:)?[A-Za-z0-9_:-]+(?=\\=)"), xmlAttrColor);
                applyKeywords(editable, XML_KEYWORDS, keywordColor);
            } else if (isGradleFile()) {
                applyKeywords(editable, GRADLE_KEYWORDS, keywordColor);
                applyPattern(editable, Pattern.compile("\\b[0-9]+(?:\\.[0-9]+)*\\b"), numberColor);
            } else {
                applyKeywords(editable, JAVA_KOTLIN_KEYWORDS, keywordColor);
                applyPattern(editable, Pattern.compile("\\b[0-9]+(?:\\.[0-9]+)?\\b"), numberColor);
                applyPattern(editable, Pattern.compile("\\b[A-Z][A-Za-z0-9_]*\\b"), classColor);
            }
        } finally {
            isHighlighting = false;
        }
    }

    private void clearColorSpans(Editable editable) {
        ForegroundColorSpan[] spans = editable.getSpans(0, editable.length(), ForegroundColorSpan.class);
        for (int i = 0; i < spans.length; i++) {
            editable.removeSpan(spans[i]);
        }
    }

    public void clearDiagnosticLines() {
        Editable editable = getText();
        if (editable == null) {
            return;
        }
        BackgroundColorSpan[] spans = editable.getSpans(0, editable.length(), BackgroundColorSpan.class);
        for (int i = 0; i < spans.length; i++) {
            editable.removeSpan(spans[i]);
        }
    }

    public void setDiagnosticLine(int lineNumber) {
        ArrayList<Integer> lines = new ArrayList<Integer>();
        if (lineNumber > 0) {
            lines.add(lineNumber);
        }
        setDiagnosticLines(lines);
    }

    public void setDiagnosticLines(ArrayList<Integer> lineNumbers) {
        Editable editable = getText();
        if (editable == null) {
            return;
        }
        clearDiagnosticLines();
        if (lineNumbers == null || lineNumbers.isEmpty()) {
            return;
        }
        for (int i = 0; i < lineNumbers.size(); i++) {
            int lineNumber = lineNumbers.get(i);
            if (lineNumber <= 0) {
                continue;
            }
            int start = findLineStart(editable, lineNumber);
            int end = findLineEnd(editable, start);
            if (start >= 0 && end >= start && end <= editable.length()) {
                editable.setSpan(new BackgroundColorSpan(errorLineColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    private void applyKeywords(Editable editable, String[] keywords, int color) {
        StringBuilder builder = new StringBuilder();
        builder.append("\\b(");
        for (int i = 0; i < keywords.length; i++) {
            if (i > 0) {
                builder.append('|');
            }
            builder.append(Pattern.quote(keywords[i]));
        }
        builder.append(")\\b");
        applyPattern(editable, Pattern.compile(builder.toString()), color);
    }

    private void applyPattern(Editable editable, Pattern pattern, int color) {
        Matcher matcher = pattern.matcher(editable.toString());
        while (matcher.find()) {
            editable.setSpan(new ForegroundColorSpan(color), matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private boolean isXmlFile() {
        return fileName.endsWith(".xml");
    }

    private boolean isGradleFile() {
        return fileName.endsWith(".gradle") || fileName.endsWith(".kts") || fileName.endsWith(".properties");
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.save();
        canvas.getClipBounds(tempRect);
        tempRect.right = gutterWidth;
        canvas.drawRect(tempRect, gutterPaint);
        canvas.restore();

        int layoutLine = getLayout() == null ? 0 : getLayout().getLineForVertical(getScrollY());
        int firstLine = Math.max(0, layoutLine);
        int lastLine = getLayout() == null ? getLineCount() : getLayout().getLineForVertical(getScrollY() + getHeight());
        for (int i = firstLine; i <= lastLine + 1 && i < getLineCount(); i++) {
            int baseline = getLineBounds(i, tempRect);
            canvas.drawText(String.valueOf(i + 1), gutterWidth - dp(10), baseline, lineNumberPaint);
        }
        super.onDraw(canvas);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private int findLineStart(CharSequence text, int lineNumber) {
        int line = 1;
        int index = 0;
        while (index < text.length() && line < lineNumber) {
            if (text.charAt(index) == '\n') {
                line++;
            }
            index++;
        }
        return line == lineNumber ? index : -1;
    }

    private int findLineEnd(CharSequence text, int start) {
        if (start < 0) {
            return -1;
        }
        int end = start;
        while (end < text.length() && text.charAt(end) != '\n') {
            end++;
        }
        return end;
    }

    private int adjustErrorColor(int baseColor) {
        int alpha = 0x44;
        return Color.argb(alpha, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor));
    }
}
