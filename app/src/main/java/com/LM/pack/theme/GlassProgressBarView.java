package com.LM.pack.theme;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

public class GlassProgressBarView extends View {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF trackRect = new RectF();
    private final RectF fillRect = new RectF();
    private final RectF shineRect = new RectF();

    private int progress = 0;
    private boolean indeterminate = false;
    private boolean animating = false;
    private long lastFrameTime = 0L;
    private float shineOffset = -0.45f;

    public GlassProgressBarView(Context context) {
        super(context);
        init();
    }

    public GlassProgressBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public GlassProgressBarView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        trackPaint.setColor(0x3A122033);
        shinePaint.setStyle(Paint.Style.FILL);
    }

    public void setProgress(int progress) {
        this.progress = clamp(progress);
        if (!indeterminate) {
            invalidate();
        }
    }

    public void setIndeterminate(boolean indeterminate) {
        this.indeterminate = indeterminate;
        if (indeterminate) {
            startAnimating();
        } else {
            stopAnimating();
            invalidate();
        }
    }

    private void startAnimating() {
        if (animating) {
            return;
        }
        animating = true;
        lastFrameTime = 0L;
        postInvalidateOnAnimation();
    }

    private void stopAnimating() {
        animating = false;
        lastFrameTime = 0L;
        shineOffset = -0.45f;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopAnimating();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility != VISIBLE) {
            stopAnimating();
        } else if (indeterminate) {
            startAnimating();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        if (width <= 0f || height <= 0f) {
            return;
        }

        float radius = height * 0.5f;
        trackRect.set(0f, 0f, width, height);
        canvas.drawRoundRect(trackRect, radius, radius, trackPaint);

        float fillWidth = indeterminate ? width * 0.34f : Math.max(radius, width * (progress / 100f));
        fillRect.set(0f, 0f, Math.min(fillWidth, width), height);
        fillPaint.setShader(new LinearGradient(
            0f,
            0f,
            fillRect.right,
            height,
            new int[] {0xFF67C3FF, 0xFF7C8BFF, 0xFF8E6DFF},
            new float[] {0f, 0.55f, 1f},
            Shader.TileMode.CLAMP
        ));
        canvas.drawRoundRect(fillRect, radius, radius, fillPaint);

        float shineWidth = Math.max(width * 0.20f, height * 2.4f);
        float shineLeft = indeterminate ? width * shineOffset : Math.max(0f, fillRect.right - shineWidth * 0.92f);
        shineRect.set(shineLeft, 0f, Math.min(shineLeft + shineWidth, width), height);
        if (shineRect.right > shineRect.left) {
            shinePaint.setShader(new LinearGradient(
                shineRect.left,
                0f,
                shineRect.right,
                0f,
                new int[] {0x00FFFFFF, 0x88FFFFFF, 0x00FFFFFF},
                new float[] {0f, 0.5f, 1f},
                Shader.TileMode.CLAMP
            ));
            canvas.drawRoundRect(shineRect, radius, radius, shinePaint);
        }

        if (indeterminate && animating && getVisibility() == VISIBLE) {
            long now = SystemClock.uptimeMillis();
            if (lastFrameTime != 0L) {
                float delta = Math.min(56f, now - lastFrameTime);
                shineOffset += delta / 900f;
                if (shineOffset > 1.2f) {
                    shineOffset = -0.45f;
                }
            }
            lastFrameTime = now;
            postInvalidateDelayed(33L);
        }
    }

    private int clamp(int value) {
        if (value < 0) {
            return 0;
        }
        if (value > 100) {
            return 100;
        }
        return value;
    }
}
