package com.LM.pack.theme;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

public class LiquidGlassBackgroundView extends View {

    private static final int NOISE_POINT_COUNT = 72;
    private static final long FRAME_DELAY_MS = 48L;

    private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint orbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint noisePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tempRect = new RectF();
    private final float[] noiseSeeds = new float[NOISE_POINT_COUNT * 2];

    private AppThemePalette palette;
    private Shader cachedBaseShader;
    private int lastWidth;
    private int lastHeight;
    private long lastFrameTime;

    public LiquidGlassBackgroundView(Context context) {
        super(context);
        init();
    }

    public LiquidGlassBackgroundView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LiquidGlassBackgroundView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        orbPaint.setStyle(Paint.Style.FILL);
        noisePaint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < noiseSeeds.length; i++) {
            noiseSeeds[i] = (float) Math.random();
        }
    }

    public void setPalette(AppThemePalette palette) {
        this.palette = palette;
        rebuildBaseShader(getWidth(), getHeight());
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rebuildBaseShader(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (palette == null) {
            return;
        }
        float width = getWidth();
        float height = getHeight();
        if (cachedBaseShader == null || width != lastWidth || height != lastHeight) {
            rebuildBaseShader((int) width, (int) height);
        }
        tempRect.set(0f, 0f, width, height);
        basePaint.setShader(cachedBaseShader);
        canvas.drawRect(tempRect, basePaint);

        long now = SystemClock.uptimeMillis();
        float t = now / 5000f;
        drawOrb(canvas, width * (0.18f + 0.03f * (float) Math.sin(t)), height * 0.20f, width * 0.42f, palette.orbPrimary);
        drawOrb(canvas, width * 0.82f, height * (0.30f + 0.04f * (float) Math.cos(t * 0.8f)), width * 0.38f, palette.orbSecondary);
        drawOrb(canvas, width * (0.48f + 0.05f * (float) Math.sin(t * 0.65f)), height * 0.82f, width * 0.50f, palette.orbTertiary);

        if (palette.liquid) {
            drawOrb(canvas, width * 0.52f, height * 0.42f, width * 0.26f, palette.orbSecondary);
            drawNoise(canvas, width, height, 1.4f);
            if (getWindowVisibility() == VISIBLE) {
                long delta = now - lastFrameTime;
                if (delta >= FRAME_DELAY_MS) {
                    lastFrameTime = now;
                    postInvalidateDelayed(FRAME_DELAY_MS);
                } else {
                    postInvalidateDelayed(FRAME_DELAY_MS - delta);
                }
            }
        } else {
            drawNoise(canvas, width, height, 0.8f);
        }
    }

    private void drawOrb(Canvas canvas, float cx, float cy, float radius, int color) {
        orbPaint.setShader(new RadialGradient(
            cx,
            cy,
            radius,
            new int[] {
                withAlpha(color, palette.liquid ? 126 : 82),
                withAlpha(color, palette.liquid ? 54 : 32),
                withAlpha(color, 0)
            },
            new float[] {0f, 0.55f, 1f},
            Shader.TileMode.CLAMP
        ));
        canvas.drawCircle(cx, cy, radius, orbPaint);
    }

    private void drawNoise(Canvas canvas, float width, float height, float radiusDp) {
        float density = getResources().getDisplayMetrics().density;
        noisePaint.setColor(palette.noiseColor);
        float radius = density * radiusDp;
        for (int i = 0; i < NOISE_POINT_COUNT; i++) {
            float x = noiseSeeds[i * 2] * width;
            float y = noiseSeeds[i * 2 + 1] * height;
            canvas.drawCircle(x, y, radius, noisePaint);
        }
    }

    private int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private void rebuildBaseShader(int width, int height) {
        lastWidth = width;
        lastHeight = height;
        if (palette == null || width <= 0 || height <= 0) {
            cachedBaseShader = null;
            return;
        }
        cachedBaseShader = new LinearGradient(
            0f,
            0f,
            width,
            height,
            new int[] {palette.backgroundStart, palette.backgroundMid, palette.backgroundEnd},
            new float[] {0f, 0.45f, 1f},
            Shader.TileMode.CLAMP
        );
    }
}
