package com.example.codenection2026_package.ui.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A {@link ScrollView} that reports how far it has been scrolled, as a percentage.
 *
 * <p>Used by Screen 2 to drive the thin progress bar at the top of the screen, which the
 * HTML prototype implemented with {@code window.addEventListener('scroll', ...)}.
 *
 * <p><b>Why a subclass instead of a listener helper:</b> there is no
 * {@code ViewCompat.setOnScrollChangeListener} method — that API does not exist in
 * androidx.core. The platform {@code View.setOnScrollChangeListener} exists from API 23,
 * but its functional-interface signature changed in API 30 (the {@code oldScrollX/Y}
 * parameters were removed), which makes lambdas ambiguous to compile against modern SDKs.
 *
 * <p>Overriding {@link #onScrollChanged(int, int, int, int)} avoids both problems: it is
 * stable on every API level, needs no AndroidX helper, and is trivial to unit test.
 */
public class ObservableScrollView extends ScrollView {

    /** Implement this to be notified when the scroll position changes. */
    public interface OnScrollProgressListener {
        /**
         * @param percent 0 when at the top, 100 when scrolled to the very bottom.
         *                Reported as 0 when the content fits on screen and cannot scroll.
         */
        void onScrollProgressChanged(int percent);
    }

    @Nullable
    private OnScrollProgressListener progressListener;

    public ObservableScrollView(@NonNull Context context) {
        super(context);
    }

    public ObservableScrollView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public ObservableScrollView(@NonNull Context context,
                                @Nullable AttributeSet attrs,
                                int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setOnScrollProgressListener(@Nullable OnScrollProgressListener listener) {
        this.progressListener = listener;
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);

        if (progressListener == null) {
            return;
        }

        // Total distance this view can scroll. Zero (or negative) means the content fits,
        // so there is nothing to report progress against.
        int scrollableRange = getChildCount() == 0
                ? 0
                : getChildAt(0).getHeight() - (getHeight() - getPaddingTop() - getPaddingBottom());

        if (scrollableRange <= 0) {
            progressListener.onScrollProgressChanged(0);
            return;
        }

        int percent = Math.round(getScrollY() * 100f / scrollableRange);
        progressListener.onScrollProgressChanged(Math.max(0, Math.min(100, percent)));
    }
}
