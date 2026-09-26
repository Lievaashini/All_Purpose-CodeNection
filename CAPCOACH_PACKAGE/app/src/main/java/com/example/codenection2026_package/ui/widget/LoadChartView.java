package com.example.codenection2026_package.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.codenection2026_package.R;

/**
 * Weekly cognitive-load bar chart, drawn straight onto a Canvas.
 *
 * <p>Why a custom view instead of XML bars: the prototype lays out seven bars with
 * percentage heights inside a fixed-height flex container, then floats a dashed 90
 * percent "crash limit" rule over the top. Android cannot express a percentage height
 * against a value range - an XML percentage resolves against the parent - so the bars
 * would all render at the same height. Drawing it means the ceiling rule, the value
 * labels, the bars and the day letters all share one coordinate system, and the chart
 * can be re-rendered for any data set via setLoads without rebuilding a view tree.
 *
 * <p>NEW FILE - additive. Nothing existing is modified.
 */
public class LoadChartView extends View {

    /** Fraction of the chart height at which the red crash-limit rule is drawn. */
    private static final float CEILING_FRACTION = 0.90f;
    /** Head-room above the tallest possible bar so a 100 percent bar keeps its cap. */
    private static final float TOP_PADDING_FRACTION = 0.12f;

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ceilingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF barRect = new RectF();
    private final Path ceilingPath = new Path();

    private final float cornerRadius;
    private final float barGap;
    private final float labelSize;
    private final float dayLabelSize;
    private final float dayRowHeight;
    private final float density;

    @ColorInt private int colorIdle;
    @ColorInt private int colorToday;
    @ColorInt private int colorHeavy;
    @ColorInt private int colorCeiling;
    @ColorInt private int colorCeilingText;
    @ColorInt private int colorTodayText;
    @ColorInt private int colorHeavyText;
    @ColorInt private int colorDayLabel;
    @ColorInt private int colorDayLabelActive;
    @ColorInt private int colorCeilingBackdrop;

    private float[] loads = new float[0];
    private String[] days = new String[0];
    private int activeIndex = -1;
    private int heavyIndex = -1;
    private int ceilingPercent = 90;

    public LoadChartView(Context context) {
        this(context, null);
    }

    public LoadChartView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LoadChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        density = getResources().getDisplayMetrics().density;
        cornerRadius = 4f * density;
        barGap = 10f * density;
        labelSize = 10f * density;
        dayLabelSize = 11f * density;
        dayRowHeight = 18f * density;

        colorIdle = color(R.color.chart_bar_idle);
        colorToday = color(R.color.brand_mint);
        colorHeavy = color(R.color.accent_amber);
        colorCeiling = color(R.color.chart_ceiling);
        colorCeilingText = color(R.color.status_red);
        colorTodayText = color(R.color.brand_mint);
        colorHeavyText = color(R.color.accent_amber);
        colorDayLabel = color(R.color.text_dim_dark);
        colorDayLabelActive = color(R.color.brand_mint);
        colorCeilingBackdrop = color(R.color.bg_dark);

        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setFakeBoldText(true);
        dayPaint.setTextAlign(Paint.Align.CENTER);

        ceilingPaint.setStyle(Paint.Style.STROKE);
        ceilingPaint.setStrokeWidth(1f * density);
        ceilingPaint.setPathEffect(new DashPathEffect(new float[]{4f * density, 4f * density}, 0));
    }

    private int color(int res) {
        return ContextCompat.getColor(getContext(), res);
    }

    /**
     * Supplies the chart data.
     *
     * @param loads       seven percentages, 0-100
     * @param days        seven single-letter labels, e.g. "M","T","W"
     * @param activeIndex index drawn as today, or -1
     * @param heavyIndex  index drawn amber, or -1
     */
    public void setLoads(@NonNull float[] loads,
                         @NonNull String[] days,
                         int activeIndex,
                         int heavyIndex) {
        this.loads = loads;
        this.days = days;
        this.activeIndex = activeIndex;
        this.heavyIndex = heavyIndex;
        invalidate();
    }

    public void setCeilingPercent(int ceilingPercent) {
        this.ceilingPercent = ceilingPercent;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
        int height = resolveSize(Math.round(112 * density), heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        int count = loads.length;
        if (count == 0 || days.length != count) {
            return;
        }

        float left = getPaddingLeft();
        float right = getWidth() - getPaddingRight();
        float top = getPaddingTop();
        float bottom = getHeight() - getPaddingBottom() - dayRowHeight;

        float chartWidth = right - left;
        float chartHeight = bottom - top;
        if (chartWidth <= 0 || chartHeight <= 0) {
            return;
        }

        float barWidth = (chartWidth - (barGap * (count - 1))) / count;
        if (barWidth <= 0) {
            return;
        }

        float barAreaTop = top + (chartHeight * TOP_PADDING_FRACTION);
        float barAreaHeight = chartHeight - (chartHeight * TOP_PADDING_FRACTION);

        drawCeiling(canvas, left, right, top, barAreaTop, barAreaHeight);

        labelPaint.setTextSize(labelSize);
        dayPaint.setTextSize(dayLabelSize);

        for (int i = 0; i < count; i++) {
            float x = left + (i * (barWidth + barGap));
            float fraction = Math.max(0f, Math.min(1f, loads[i] / 100f));
            float barHeight = barAreaHeight * fraction;

            barRect.set(x, bottom - barHeight, x + barWidth, bottom);

            boolean isActive = i == activeIndex;
            boolean isHeavy = i == heavyIndex;

            barPaint.setColor(isActive ? colorToday : (isHeavy ? colorHeavy : colorIdle));
            canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, barPaint);

            if (isActive || isHeavy) {
                labelPaint.setColor(isActive ? colorTodayText : colorHeavyText);
                float y = Math.max(barAreaTop, barRect.top - (4f * density));
                canvas.drawText(Math.round(loads[i]) + "%", barRect.centerX(), y, labelPaint);
            }

            if (i < days.length) {
                dayPaint.setColor(isActive ? colorDayLabelActive : colorDayLabel);
                dayPaint.setFakeBoldText(isActive);
                canvas.drawText(days[i], barRect.centerX(), bottom + dayLabelSize + (2f * density), dayPaint);
            }
        }
    }

    /**
     * Dashed rule plus the "90% Crash Limit" tag, matching the prototype overlay. The tag
     * sits on the right and gets a solid backing plate so it stays readable where it
     * crosses the rule.
     */
    private void drawCeiling(Canvas canvas,
                             float left,
                             float right,
                             float top,
                             float barAreaTop,
                             float barAreaHeight) {
        float y = barAreaTop + (barAreaHeight * (1f - CEILING_FRACTION));
        if (y < top) {
            y = top;
        }

        ceilingPath.reset();
        ceilingPath.moveTo(left, y);
        ceilingPath.lineTo(right, y);
        ceilingPaint.setColor(colorCeiling);
        canvas.drawPath(ceilingPath, ceilingPaint);

        labelPaint.setColor(colorCeilingText);
        labelPaint.setTextSize(labelSize);
        labelPaint.setTextAlign(Paint.Align.RIGHT);

        String tag = ceilingPercent + "% Crash Limit";
        float textWidth = labelPaint.measureText(tag);

        barPaint.setColor(colorCeilingBackdrop);
        barRect.set(right - textWidth - (4f * density), y - labelSize, right + (2f * density), y + (2f * density));
        canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, barPaint);

        canvas.drawText(tag, right, y - density, labelPaint);
        labelPaint.setTextAlign(Paint.Align.CENTER);
    }
}