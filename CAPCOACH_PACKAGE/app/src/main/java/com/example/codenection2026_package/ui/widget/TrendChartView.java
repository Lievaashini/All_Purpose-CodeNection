package com.example.codenection2026_package.ui.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.codenection2026_package.R;

/**
 * Seven-night sleep trend, drawn straight onto a Canvas.
 *
 * <p>Port of the sparkline in {@code Prototype/biometrics.html}: a dashed line through one
 * point per night, a faded wash under it, an amber dot on the night the week started going
 * short, and a pulsing dot on the newest night.
 *
 * <p>Why a custom view: the prototype draws an SVG polyline in a fixed 280x48 viewBox.
 * Android has no equivalent for a path scaled to a value range, so a stack of XML views
 * could only ever draw a fixed shape - which is exactly what the placeholder this replaces
 * did. Drawing it means the points follow the readings, and {@code setNights} can redraw
 * for any week without rebuilding a view tree.
 *
 * <p>The vertical scale is absolute, not relative to the week's own best and worst: the top
 * of the plot is the sleep target, so the same shape means the same thing from one week to
 * the next. That also makes the two markers meaningful - a point near the bottom is short
 * sleep, not merely the shortest of seven.
 *
 * <p>A night with no reading is left out of the line rather than drawn as zero. Plotting an
 * unrecorded night at the baseline would read as "slept nothing", which is a claim the data
 * does not support, so the line simply breaks there.
 *
 * <p>NEW FILE - additive. Nothing existing is modified.
 */
public class TrendChartView extends View {

    /** Any value below this means Health Connect holds no reading for that night. */
    public static final float NO_READING = -1f;

    /** Line stroke width, matching the prototype's stroke-width. */
    private static final float STROKE_DP = 2f;

    /** The prototype's stroke-dasharray "3 3". */
    private static final float DASH_DP = 3f;

    /** Radius of the pulsing dot on the newest night. */
    private static final float FEATURE_RADIUS_DP = 4f;

    /** Radius of the amber dot on the night the week first fell short. */
    private static final float WARN_RADIUS_DP = 3.5f;

    /** How far the pulse ring expands before it restarts. */
    private static final float HALO_RADIUS_DP = 9f;

    private static final long PING_DURATION_MS = 1400L;

    /** Prototype opacity on the wash under the line. */
    private static final int FILL_ALPHA = 64;

    private static final float DEFAULT_HEIGHT_DP = 48f;

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint featurePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint warnPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path linePath = new Path();
    private final Path fillPath = new Path();

    private final float density;
    private final float featureRadius;
    private final float warnRadius;
    private final float haloRadius;

    /** Minutes per night, oldest first. {@link #NO_READING} where nothing was recorded. */
    private float[] nights = new float[0];

    /** The top of the plot: a night this long touches the ceiling. */
    private float targetMinutes = 1f;

    /** Newest night with a reading, drawn with the pulsing dot, or -1. */
    private int latestIndex = -1;

    /** Most recent night below the target, drawn amber, or -1. */
    private int warnIndex = -1;

    /** 0 to 1 through the current pulse. */
    private float ping;

    @Nullable
    private ValueAnimator pingAnimator;

    /** Plot rectangle, inset so no dot or pulse ring is clipped by the view bounds. */
    private float plotLeft;
    private float plotRight;
    private float plotTop;
    private float plotBottom;

    public TrendChartView(Context context) {
        this(context, null);
    }

    public TrendChartView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TrendChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        density = getResources().getDisplayMetrics().density;
        featureRadius = FEATURE_RADIUS_DP * density;
        warnRadius = WARN_RADIUS_DP * density;
        haloRadius = HALO_RADIUS_DP * density;

        @ColorInt int line = color(R.color.outline_muted);
        @ColorInt int accent = color(R.color.status_red);
        @ColorInt int amber = color(R.color.accent_amber);

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(STROKE_DP * density);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setColor(line);
        linePaint.setPathEffect(new DashPathEffect(
                new float[]{DASH_DP * density, DASH_DP * density}, 0f));

        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(accent);

        featurePaint.setStyle(Paint.Style.FILL);
        featurePaint.setColor(accent);

        warnPaint.setStyle(Paint.Style.FILL);
        warnPaint.setColor(amber);

        haloPaint.setStyle(Paint.Style.FILL);
        haloPaint.setColor(accent);
    }

    private int color(int res) {
        return ContextCompat.getColor(getContext(), res);
    }

    /**
     * Supplies the week the chart draws.
     *
     * @param nights        one total per night in minutes, oldest first, with
     *                      {@link #NO_READING} for a night nothing was recorded
     * @param targetMinutes the sleep target, which becomes the top of the plot
     * @param latestIndex   the newest night with a reading, marked with the pulsing dot,
     *                      or -1 for none
     * @param warnIndex     the most recent night below the target, marked amber, or -1
     */
    public void setNights(@NonNull float[] nights,
                          float targetMinutes,
                          int latestIndex,
                          int warnIndex) {
        this.nights = nights;
        this.targetMinutes = targetMinutes > 0f ? targetMinutes : 1f;
        this.latestIndex = latestIndex;
        this.warnIndex = warnIndex;

        rebuildPaths();
        if (isAttachedToWindow()) {
            stopPing();
            startPing();
        }
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
        int height = resolveSize(Math.round(DEFAULT_HEIGHT_DP * density), heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        // Inset by the widest thing drawn, so the newest night's pulse ring is not sliced off
        // at the right edge and the dots keep their full radius at the top and bottom.
        float insetX = haloRadius + density;
        float insetY = featureRadius + (2f * density);

        plotLeft = getPaddingLeft() + insetX;
        plotRight = w - getPaddingRight() - insetX;
        plotTop = getPaddingTop() + insetY;
        plotBottom = h - getPaddingBottom() - insetY;

        if (plotRight <= plotLeft || plotBottom <= plotTop) {
            plotLeft = plotRight = plotTop = plotBottom = 0f;
        }

        fillPaint.setShader(plotBottom > plotTop
                ? new LinearGradient(0f, plotTop, 0f, plotBottom,
                        withAlpha(color(R.color.status_red), FILL_ALPHA),
                        withAlpha(color(R.color.status_red), 0),
                        Shader.TileMode.CLAMP)
                : null);

        rebuildPaths();
    }

    private static int withAlpha(@ColorInt int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    // ==================================================================
    // Geometry
    // ==================================================================

    /**
     * Splits the week into unbroken runs and builds both paths.
     *
     * <p>One run per stretch of consecutive nights that have readings, so a gap breaks the
     * line instead of being bridged by a segment no reading supports.
     */
    private void rebuildPaths() {
        linePath.reset();
        fillPath.reset();

        int count = nights.length;
        if (count == 0 || plotRight <= plotLeft) {
            return;
        }

        int index = 0;
        while (index < count) {
            if (nights[index] < 0f) {
                index++;
                continue;
            }
            int runStart = index;
            while (index < count && nights[index] >= 0f) {
                index++;
            }
            int runEnd = index - 1;

            for (int i = runStart; i <= runEnd; i++) {
                float x = xAt(i);
                float y = yAt(nights[i]);
                if (i == runStart) {
                    linePath.moveTo(x, y);
                    fillPath.moveTo(x, plotBottom);
                } else {
                    linePath.lineTo(x, y);
                }
                fillPath.lineTo(x, y);
            }
            // Close the wash back along the baseline.
            fillPath.lineTo(xAt(runEnd), plotBottom);
            fillPath.close();
        }
    }

    private float xAt(int index) {
        int slots = nights.length;
        if (slots <= 1) {
            return plotLeft;
        }
        return plotLeft + ((plotRight - plotLeft) * index / (slots - 1f));
    }

    /** The target sits at the top of the plot and is the ceiling for anything longer. */
    private float yAt(float minutes) {
        float fraction = (plotBottom - plotTop) <= 0f
                ? 0f
                : Math.max(0f, Math.min(1f, minutes / targetMinutes));
        return plotBottom - ((plotBottom - plotTop) * fraction);
    }

    // ==================================================================
    // Drawing
    // ==================================================================

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        if (nights.length == 0 || plotRight <= plotLeft) {
            return;
        }

        canvas.drawPath(fillPath, fillPaint);
        canvas.drawPath(linePath, linePaint);

        if (hasReading(warnIndex)) {
            canvas.drawCircle(xAt(warnIndex), yAt(nights[warnIndex]), warnRadius, warnPaint);
        }

        if (hasReading(latestIndex)) {
            float x = xAt(latestIndex);
            float y = yAt(nights[latestIndex]);
            if (ping > 0f) {
                haloPaint.setAlpha(Math.round((1f - ping) * 153f));
                canvas.drawCircle(x, y, featureRadius + ((haloRadius - featureRadius) * ping),
                        haloPaint);
            }
            canvas.drawCircle(x, y, featureRadius, featurePaint);
        }
    }

    private boolean hasReading(int index) {
        return index >= 0 && index < nights.length && nights[index] >= 0f;
    }

    // ==================================================================
    // Pulse
    // ==================================================================

    /**
     * The prototype's animate-ping: a ring that grows and fades off the newest night. Owned
     * by the view rather than by the caller, and tied to the window so a detached chart is
     * not left animating.
     */
    private void startPing() {
        if (pingAnimator != null || !hasReading(latestIndex)) {
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(PING_DURATION_MS);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.RESTART);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            ping = (Float) animation.getAnimatedValue();
            invalidate();
        });
        pingAnimator = animator;
        animator.start();
    }

    private void stopPing() {
        if (pingAnimator != null) {
            pingAnimator.cancel();
            pingAnimator = null;
        }
        ping = 0f;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startPing();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopPing();
        super.onDetachedFromWindow();
    }
}
