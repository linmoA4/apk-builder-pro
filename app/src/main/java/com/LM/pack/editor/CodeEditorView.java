package com.LM.pack.editor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.Editable;
import android.text.Selection;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.BackgroundColorSpan;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.widget.EditText;
import com.LM.pack.theme.AppThemePalette;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CodeEditorView extends EditText {
    // TODO: 在现有高亮与历史栈稳定后补上真正的代码折叠能力，避免先引入破坏编辑体验的半成品折叠。

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

    private static final int HISTORY_LIMIT = 120;
    private static final int FULL_HIGHLIGHT_CHAR_LIMIT = 180000;
    private static final int FULL_HIGHLIGHT_LINE_LIMIT = 4000;
    private static final String INDENT = "    ";
    private static final Pattern COMMENT_LINE_PATTERN = Pattern.compile("(?m)//.*$");
    private static final Pattern COMMENT_BLOCK_PATTERN = Pattern.compile("/\\*[\\s\\S]*?\\*/");
    private static final Pattern STRING_DOUBLE_PATTERN = Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"");
    private static final Pattern STRING_SINGLE_PATTERN = Pattern.compile("'(?:\\\\.|[^'\\\\])*'");
    private static final Pattern ANNOTATION_PATTERN = Pattern.compile("(?<![A-Za-z0-9_])@[A-Za-z0-9_:.]+");
    private static final Pattern XML_TAG_PATTERN = Pattern.compile("</?[A-Za-z0-9_:-]+");
    private static final Pattern XML_ATTR_PATTERN = Pattern.compile("\\b(?:android:)?[A-Za-z0-9_:-]+(?=\\=)");
    private static final Pattern GRADLE_NUMBER_PATTERN = Pattern.compile("\\b[0-9]+(?:\\.[0-9]+)*\\b");
    private static final Pattern CODE_NUMBER_PATTERN = Pattern.compile("\\b[0-9]+(?:\\.[0-9]+)?\\b");
    private static final Pattern CLASS_PATTERN = Pattern.compile("\\b[A-Z][A-Za-z0-9_]*\\b");
    private static final Map<String, Pattern> KEYWORD_PATTERN_CACHE = new HashMap<String, Pattern>();

    private final Rect tempRect = new Rect();
    private final Paint gutterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lineNumberPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ArrayList<EditorSnapshot> undoStack = new ArrayList<EditorSnapshot>();
    private final ArrayList<EditorSnapshot> redoStack = new ArrayList<EditorSnapshot>();
    private boolean isHighlighting;
    private boolean internalTextMutation;
    private String fileName = "";
    private String activeSearchQuery = "";
    private boolean activeSearchIgnoreCase = true;
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
    private int bracketMatchColor = Color.parseColor("#33AECBFA");
    private int searchMatchColor = Color.parseColor("#335A8FFF");
    private EditorSnapshot pendingSnapshot;
    private EditorEventListener editorEventListener;
    private boolean lightweightMode;
    private int lastLineDigits = -1;

    public interface EditorEventListener {
        void onHistoryChanged(boolean canUndo, boolean canRedo);
    }

    private static class EditorSnapshot {
        final String text;
        final int selectionStart;
        final int selectionEnd;

        EditorSnapshot(String text, int selectionStart, int selectionEnd) {
            this.text = text == null ? "" : text;
            this.selectionStart = Math.max(0, selectionStart);
            this.selectionEnd = Math.max(0, selectionEnd);
        }
    }

    private static class DiagnosticLineSpan extends BackgroundColorSpan {
        DiagnosticLineSpan(int color) {
            super(color);
        }
    }

    private static class BracketMatchSpan extends BackgroundColorSpan {
        BracketMatchSpan(int color) {
            super(color);
        }
    }

    private static class SearchMatchSpan extends BackgroundColorSpan {
        SearchMatchSpan(int color) {
            super(color);
        }
    }

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
                if (internalTextMutation || isHighlighting) {
                    pendingSnapshot = null;
                    return;
                }
                if (count == 0 && after == 0) {
                    pendingSnapshot = null;
                    return;
                }
                pendingSnapshot = captureSnapshot();
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!internalTextMutation && pendingSnapshot != null && !sameText(pendingSnapshot.text, s.toString())) {
                    pushSnapshot(undoStack, pendingSnapshot);
                    redoStack.clear();
                }
                pendingSnapshot = null;
                scheduleHighlight();
                updateGutterWidth();
                scheduleBracketMatch();
                applySearchHighlights();
                notifyHistoryChanged();
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
        bracketMatchColor = withAlpha(palette.accentStrong, 68);
        searchMatchColor = withAlpha(palette.accent, 58);
        setTextColor(palette.textPrimary);
        setHintTextColor(palette.textMuted);
        invalidate();
        scheduleHighlight();
        scheduleBracketMatch();
        applySearchHighlights();
    }

    public void setFileName(String fileName) {
        this.fileName = fileName == null ? "" : fileName.toLowerCase();
        scheduleHighlight();
    }

    public void setEditorEventListener(EditorEventListener editorEventListener) {
        this.editorEventListener = editorEventListener;
        notifyHistoryChanged();
    }

    public void resetHistory() {
        undoStack.clear();
        redoStack.clear();
        pendingSnapshot = null;
        notifyHistoryChanged();
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public void undo() {
        if (!canUndo()) {
            return;
        }
        EditorSnapshot current = captureSnapshot();
        EditorSnapshot target = undoStack.remove(undoStack.size() - 1);
        pushSnapshot(redoStack, current);
        restoreSnapshot(target);
        notifyHistoryChanged();
    }

    public void redo() {
        if (!canRedo()) {
            return;
        }
        EditorSnapshot current = captureSnapshot();
        EditorSnapshot target = redoStack.remove(redoStack.size() - 1);
        pushSnapshot(undoStack, current);
        restoreSnapshot(target);
        notifyHistoryChanged();
    }

    public void setSearchQuery(String query, boolean ignoreCase) {
        activeSearchQuery = query == null ? "" : query;
        activeSearchIgnoreCase = ignoreCase;
        applySearchHighlights();
    }

    public void clearSearchHighlights() {
        activeSearchQuery = "";
        clearSearchMatchSpans();
    }

    public boolean findNext(String query, boolean ignoreCase) {
        String safeQuery = query == null ? "" : query;
        setSearchQuery(safeQuery, ignoreCase);
        if (safeQuery.length() == 0) {
            return false;
        }
        Editable editable = getText();
        if (editable == null) {
            return false;
        }
        String source = editable.toString();
        int startSearch = Math.max(0, getSelectionEnd());
        int index = indexOf(source, safeQuery, startSearch, ignoreCase);
        if (index < 0 && startSearch > 0) {
            index = indexOf(source, safeQuery, 0, ignoreCase);
        }
        if (index < 0) {
            return false;
        }
        requestFocus();
        setSelection(index, Math.min(source.length(), index + safeQuery.length()));
        scheduleBracketMatch();
        return true;
    }

    public boolean replaceCurrentMatch(String query, String replacement, boolean ignoreCase) {
        String safeQuery = query == null ? "" : query;
        if (safeQuery.length() == 0) {
            return false;
        }
        Editable editable = getText();
        if (editable == null) {
            return false;
        }
        int start = getSelectionStart();
        int end = getSelectionEnd();
        if (start < 0 || end < start || end > editable.length()) {
            return false;
        }
        String selected = editable.subSequence(start, end).toString();
        if (!textMatches(selected, safeQuery, ignoreCase)) {
            return false;
        }
        String safeReplacement = replacement == null ? "" : replacement;
        applyTextReplacement(start, end, safeReplacement, start + safeReplacement.length());
        setSearchQuery(safeQuery, ignoreCase);
        return true;
    }

    public int replaceAll(String query, String replacement, boolean ignoreCase) {
        String safeQuery = query == null ? "" : query;
        if (safeQuery.length() == 0) {
            return 0;
        }
        Editable editable = getText();
        if (editable == null) {
            return 0;
        }
        String source = editable.toString();
        String safeReplacement = replacement == null ? "" : replacement;
        StringBuilder builder = new StringBuilder();
        int count = 0;
        int index = 0;
        int matchIndex;
        while ((matchIndex = indexOf(source, safeQuery, index, ignoreCase)) >= 0) {
            builder.append(source, index, matchIndex);
            builder.append(safeReplacement);
            index = matchIndex + safeQuery.length();
            count++;
        }
        if (count == 0) {
            return 0;
        }
        builder.append(source.substring(index));
        applyWholeText(builder.toString(), Math.min(builder.length(), getSelectionStart()));
        setSearchQuery(safeQuery, ignoreCase);
        return count;
    }

    private void updateGutterWidth() {
        int lineCount = Math.max(1, getLineCount());
        int digits = String.valueOf(lineCount).length();
        if (digits == lastLineDigits && gutterWidth > 0) {
            return;
        }
        lastLineDigits = digits;
        float charWidth = lineNumberPaint.measureText("0");
        int newGutterWidth = (int) (getPaddingTop() + digits * charWidth + dp(26));
        if (newGutterWidth != gutterWidth) {
            gutterWidth = newGutterWidth;
            setPadding(gutterWidth, getPaddingTop(), getPaddingRight(), getPaddingBottom());
            invalidate();
        }
    }

    private void scheduleHighlight() {
        removeCallbacks(highlightRunnable);
        postDelayed(highlightRunnable, 70);
    }

    private void scheduleBracketMatch() {
        removeCallbacks(bracketMatchRunnable);
        post(bracketMatchRunnable);
    }

    private final Runnable highlightRunnable = new Runnable() {
        @Override
        public void run() {
            applyHighlighting();
        }
    };

    private final Runnable bracketMatchRunnable = new Runnable() {
        @Override
        public void run() {
            updateBracketMatch();
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
            lightweightMode = shouldUseLightweightMode(editable);
            if (lightweightMode) {
                return;
            }
            String source = editable.toString();
            applyPattern(editable, source, COMMENT_LINE_PATTERN, commentColor);
            applyPattern(editable, source, COMMENT_BLOCK_PATTERN, commentColor);
            applyPattern(editable, source, STRING_DOUBLE_PATTERN, stringColor);
            applyPattern(editable, source, STRING_SINGLE_PATTERN, stringColor);
            applyPattern(editable, source, ANNOTATION_PATTERN, annotationColor);
            if (isXmlFile()) {
                applyPattern(editable, source, XML_TAG_PATTERN, xmlTagColor);
                applyPattern(editable, source, XML_ATTR_PATTERN, xmlAttrColor);
                applyKeywords(editable, source, XML_KEYWORDS, keywordColor);
            } else if (isGradleFile()) {
                applyKeywords(editable, source, GRADLE_KEYWORDS, keywordColor);
                applyPattern(editable, source, GRADLE_NUMBER_PATTERN, numberColor);
            } else {
                applyKeywords(editable, source, JAVA_KOTLIN_KEYWORDS, keywordColor);
                applyPattern(editable, source, CODE_NUMBER_PATTERN, numberColor);
                applyPattern(editable, source, CLASS_PATTERN, classColor);
            }
        } finally {
            isHighlighting = false;
            applySearchHighlights();
            scheduleBracketMatch();
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
        DiagnosticLineSpan[] spans = editable.getSpans(0, editable.length(), DiagnosticLineSpan.class);
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
                editable.setSpan(new DiagnosticLineSpan(errorLineColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    private boolean shouldUseLightweightMode(Editable editable) {
        if (editable == null) {
            return false;
        }
        if (editable.length() > FULL_HIGHLIGHT_CHAR_LIMIT) {
            return true;
        }
        return getLineCount() > FULL_HIGHLIGHT_LINE_LIMIT;
    }

    private void applyKeywords(Editable editable, String source, String[] keywords, int color) {
        applyPattern(editable, source, resolveKeywordPattern(keywords), color);
    }

    private Pattern resolveKeywordPattern(String[] keywords) {
        StringBuilder cacheKey = new StringBuilder();
        for (int i = 0; i < keywords.length; i++) {
            cacheKey.append(keywords[i]).append('#');
        }
        String key = cacheKey.toString();
        Pattern cached = KEYWORD_PATTERN_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        StringBuilder builder = new StringBuilder();
        builder.append("\\b(");
        for (int i = 0; i < keywords.length; i++) {
            if (i > 0) {
                builder.append('|');
            }
            builder.append(Pattern.quote(keywords[i]));
        }
        builder.append(")\\b");
        Pattern compiled = Pattern.compile(builder.toString());
        KEYWORD_PATTERN_CACHE.put(key, compiled);
        return compiled;
    }

    private void applyPattern(Editable editable, String source, Pattern pattern, int color) {
        Matcher matcher = pattern.matcher(source);
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

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);
        scheduleBracketMatch();
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        InputConnection baseConnection = super.onCreateInputConnection(outAttrs);
        if (baseConnection == null) {
            return null;
        }
        return new InputConnectionWrapper(baseConnection, true) {
            @Override
            public boolean commitText(CharSequence text, int newCursorPosition) {
                if ("\n".contentEquals(text)) {
                    return handleNewLineWithIndent();
                }
                return super.commitText(text, newCursorPosition);
            }

            @Override
            public boolean sendKeyEvent(KeyEvent event) {
                if (event != null && event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                    return handleNewLineWithIndent();
                }
                return super.sendKeyEvent(event);
            }
        };
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

    private EditorSnapshot captureSnapshot() {
        Editable editable = getText();
        return new EditorSnapshot(
            editable == null ? "" : editable.toString(),
            Math.max(0, getSelectionStart()),
            Math.max(0, getSelectionEnd())
        );
    }

    private void restoreSnapshot(EditorSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        internalTextMutation = true;
        try {
            setText(snapshot.text);
            Editable editable = getText();
            if (editable != null) {
                int safeStart = Math.min(snapshot.selectionStart, editable.length());
                int safeEnd = Math.min(snapshot.selectionEnd, editable.length());
                Selection.setSelection(editable, safeStart, Math.max(safeStart, safeEnd));
            }
        } finally {
            internalTextMutation = false;
        }
        updateGutterWidth();
        scheduleHighlight();
        applySearchHighlights();
        scheduleBracketMatch();
    }

    private void pushSnapshot(ArrayList<EditorSnapshot> stack, EditorSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        if (!stack.isEmpty()) {
            EditorSnapshot last = stack.get(stack.size() - 1);
            if (sameText(last.text, snapshot.text) && last.selectionStart == snapshot.selectionStart && last.selectionEnd == snapshot.selectionEnd) {
                return;
            }
        }
        stack.add(snapshot);
        while (stack.size() > HISTORY_LIMIT) {
            stack.remove(0);
        }
    }

    private void notifyHistoryChanged() {
        if (editorEventListener != null) {
            editorEventListener.onHistoryChanged(canUndo(), canRedo());
        }
    }

    private void clearBracketMatchSpans() {
        Editable editable = getText();
        if (editable == null) {
            return;
        }
        BracketMatchSpan[] spans = editable.getSpans(0, editable.length(), BracketMatchSpan.class);
        for (int i = 0; i < spans.length; i++) {
            editable.removeSpan(spans[i]);
        }
    }

    private void clearSearchMatchSpans() {
        Editable editable = getText();
        if (editable == null) {
            return;
        }
        SearchMatchSpan[] spans = editable.getSpans(0, editable.length(), SearchMatchSpan.class);
        for (int i = 0; i < spans.length; i++) {
            editable.removeSpan(spans[i]);
        }
    }

    private void updateBracketMatch() {
        Editable editable = getText();
        if (editable == null) {
            return;
        }
        clearBracketMatchSpans();
        if (getSelectionStart() != getSelectionEnd()) {
            return;
        }
        int cursor = Math.max(0, getSelectionStart());
        int probe = resolveBracketProbeIndex(editable, cursor);
        if (probe < 0 || probe >= editable.length()) {
            return;
        }
        char bracket = editable.charAt(probe);
        int matchIndex = findMatchingBracket(editable, probe, bracket);
        if (matchIndex < 0) {
            return;
        }
        editable.setSpan(new BracketMatchSpan(bracketMatchColor), probe, probe + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        editable.setSpan(new BracketMatchSpan(bracketMatchColor), matchIndex, matchIndex + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private int resolveBracketProbeIndex(CharSequence text, int cursor) {
        if (cursor > 0 && isSupportedBracket(text.charAt(cursor - 1))) {
            return cursor - 1;
        }
        if (cursor < text.length() && isSupportedBracket(text.charAt(cursor))) {
            return cursor;
        }
        return -1;
    }

    private int findMatchingBracket(CharSequence text, int index, char bracket) {
        char partner = getMatchingBracket(bracket);
        if (partner == 0) {
            return -1;
        }
        int direction = isOpeningBracket(bracket) ? 1 : -1;
        int depth = 0;
        for (int i = index; i >= 0 && i < text.length(); i += direction) {
            char current = text.charAt(i);
            if (current == bracket) {
                depth++;
            } else if (current == partner) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private void applySearchHighlights() {
        Editable editable = getText();
        if (editable == null) {
            return;
        }
        clearSearchMatchSpans();
        if (activeSearchQuery.length() == 0) {
            return;
        }
        String source = editable.toString();
        int start = 0;
        while (start <= source.length()) {
            int found = indexOf(source, activeSearchQuery, start, activeSearchIgnoreCase);
            if (found < 0) {
                break;
            }
            int end = found + activeSearchQuery.length();
            editable.setSpan(new SearchMatchSpan(searchMatchColor), found, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            start = Math.max(end, found + 1);
        }
    }

    private boolean handleNewLineWithIndent() {
        Editable editable = getText();
        if (editable == null) {
            return false;
        }
        int start = Math.max(0, getSelectionStart());
        int end = Math.max(0, getSelectionEnd());
        int lineStart = findCurrentLineStart(editable, start);
        String linePrefix = editable.subSequence(lineStart, start).toString();
        String baseIndent = extractIndent(linePrefix);
        String extraIndent = shouldIncreaseIndent(linePrefix) ? INDENT : "";
        char nextChar = start < editable.length() ? editable.charAt(start) : '\0';
        String insertText;
        int targetCursor;
        if (isClosingBracket(nextChar) && extraIndent.length() > 0) {
            insertText = "\n" + baseIndent + extraIndent + "\n" + baseIndent;
            targetCursor = start + 1 + baseIndent.length() + extraIndent.length();
        } else {
            insertText = "\n" + baseIndent + extraIndent;
            targetCursor = start + insertText.length();
        }
        applyTextReplacement(start, end, insertText, targetCursor);
        return true;
    }

    private void applyTextReplacement(int start, int end, String replacement, int newCursor) {
        Editable editable = getText();
        if (editable == null) {
            return;
        }
        pushSnapshot(undoStack, captureSnapshot());
        redoStack.clear();
        internalTextMutation = true;
        try {
            editable.replace(start, end, replacement == null ? "" : replacement);
            int safeCursor = Math.max(0, Math.min(editable.length(), newCursor));
            Selection.setSelection(editable, safeCursor);
        } finally {
            internalTextMutation = false;
        }
        scheduleHighlight();
        updateGutterWidth();
        applySearchHighlights();
        scheduleBracketMatch();
        notifyHistoryChanged();
    }

    private void applyWholeText(String value, int newCursor) {
        pushSnapshot(undoStack, captureSnapshot());
        redoStack.clear();
        internalTextMutation = true;
        try {
            setText(value == null ? "" : value);
            Editable editable = getText();
            if (editable != null) {
                int safeCursor = Math.max(0, Math.min(editable.length(), newCursor));
                Selection.setSelection(editable, safeCursor);
            }
        } finally {
            internalTextMutation = false;
        }
        updateGutterWidth();
        scheduleHighlight();
        applySearchHighlights();
        scheduleBracketMatch();
        notifyHistoryChanged();
    }

    private int findCurrentLineStart(CharSequence text, int cursor) {
        int index = Math.max(0, Math.min(cursor, text.length()));
        while (index > 0 && text.charAt(index - 1) != '\n') {
            index--;
        }
        return index;
    }

    private String extractIndent(String linePrefix) {
        if (linePrefix == null || linePrefix.length() == 0) {
            return "";
        }
        int index = 0;
        while (index < linePrefix.length()) {
            char ch = linePrefix.charAt(index);
            if (ch != ' ' && ch != '\t') {
                break;
            }
            index++;
        }
        return linePrefix.substring(0, index);
    }

    private boolean shouldIncreaseIndent(String linePrefix) {
        if (linePrefix == null) {
            return false;
        }
        String trimmed = linePrefix.trim();
        if (trimmed.length() == 0) {
            return false;
        }
        if (trimmed.endsWith("{") || trimmed.endsWith("(") || trimmed.endsWith("[") || trimmed.endsWith(":")) {
            return true;
        }
        return isXmlFile() && trimmed.startsWith("<") && !trimmed.startsWith("</") && !trimmed.endsWith("/>") && !trimmed.endsWith("?>");
    }

    private int indexOf(String source, String query, int start, boolean ignoreCase) {
        if (source == null || query == null) {
            return -1;
        }
        if (!ignoreCase) {
            return source.indexOf(query, Math.max(0, start));
        }
        return source.toLowerCase().indexOf(query.toLowerCase(), Math.max(0, start));
    }

    private boolean textMatches(String selected, String query, boolean ignoreCase) {
        if (selected == null || query == null) {
            return false;
        }
        return ignoreCase ? selected.equalsIgnoreCase(query) : selected.equals(query);
    }

    private boolean sameText(String first, String second) {
        String safeFirst = first == null ? "" : first;
        String safeSecond = second == null ? "" : second;
        return safeFirst.equals(safeSecond);
    }

    private boolean isSupportedBracket(char value) {
        return value == '(' || value == ')' || value == '[' || value == ']' || value == '{' || value == '}';
    }

    private boolean isOpeningBracket(char value) {
        return value == '(' || value == '[' || value == '{';
    }

    private boolean isClosingBracket(char value) {
        return value == ')' || value == ']' || value == '}';
    }

    private char getMatchingBracket(char value) {
        if (value == '(') {
            return ')';
        }
        if (value == ')') {
            return '(';
        }
        if (value == '[') {
            return ']';
        }
        if (value == ']') {
            return '[';
        }
        if (value == '{') {
            return '}';
        }
        if (value == '}') {
            return '{';
        }
        return 0;
    }

    private int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }
}
