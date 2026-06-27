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
import android.util.AttributeSet;
import android.widget.EditText;
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
            applyPattern(editable, Pattern.compile("(?m)//.*$"), "#5E7F6E");
            applyPattern(editable, Pattern.compile("/\\*[\\s\\S]*?\\*/"), "#5E7F6E");
            applyPattern(editable, Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\""), "#D7BA7D");
            applyPattern(editable, Pattern.compile("'(?:\\\\.|[^'\\\\])*'"), "#D7BA7D");
            applyPattern(editable, Pattern.compile("(?<![A-Za-z0-9_])@[A-Za-z0-9_:.]+"), "#7FB0FF");
            if (isXmlFile()) {
                applyPattern(editable, Pattern.compile("</?[A-Za-z0-9_:-]+"), "#4EC9B0");
                applyPattern(editable, Pattern.compile("\\b(?:android:)?[A-Za-z0-9_:-]+(?=\\=)"), "#9CDCFE");
                applyKeywords(editable, XML_KEYWORDS, "#C586C0");
            } else if (isGradleFile()) {
                applyKeywords(editable, GRADLE_KEYWORDS, "#C586C0");
                applyPattern(editable, Pattern.compile("\\b[0-9]+(?:\\.[0-9]+)*\\b"), "#B5CEA8");
            } else {
                applyKeywords(editable, JAVA_KOTLIN_KEYWORDS, "#C586C0");
                applyPattern(editable, Pattern.compile("\\b[0-9]+(?:\\.[0-9]+)?\\b"), "#B5CEA8");
                applyPattern(editable, Pattern.compile("\\b[A-Z][A-Za-z0-9_]*\\b"), "#4EC9B0");
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

    private void applyKeywords(Editable editable, String[] keywords, String color) {
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

    private void applyPattern(Editable editable, Pattern pattern, String colorValue) {
        Matcher matcher = pattern.matcher(editable.toString());
        int color = Color.parseColor(colorValue);
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
}
