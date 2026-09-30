package com.example.codenection2026_package.ui.biometrics;

import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.health.connect.client.records.SleepSessionRecord;

import com.example.codenection2026_package.R;
import com.example.codenection2026_package.api.HealthConnectManager;
import com.example.codenection2026_package.api.HealthConnectReader;
import com.example.codenection2026_package.model.Biometrics;
import com.example.codenection2026_package.model.CoachVoice;
import com.example.codenection2026_package.model.ToneType;
import com.example.codenection2026_package.ui.onboarding.OnboardingPrefs;
import com.example.codenection2026_package.ui.shell.AppHeader;
import com.example.codenection2026_package.ui.shell.ScreenNav;
import com.example.codenection2026_package.ui.shell.ToneCopy;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * SCREEN 5 - BIOMETRICS (port of Prototype/biometrics.html).
 *
 * <p>Shows the overnight sleep anomaly, the sleep debt and HRV metrics, and the cognitive
 * penalty the app applies to today's capacity.
 *
 * <p>Data flows through {@link BiometricsSource}. There is no shared Room database class in
 * the project yet, so the default source returns an empty list; the screen then renders the
 * prototype's own numbers. That is a supported state, not a failure: nothing here assumes a
 * record exists.
 */
public class BiometricsFragment extends Fragment {

    /**
     * Swap-in point for AppDatabase once it lands.
     */
    private interface BiometricsSource {
        List<Biometrics> loadAll(Context context);
    }

    /**
     * API DIRECT SOURCE (Replaces EmptyBiometricsSource)
     *
     * <p>Bypasses the unmerged Room database and feeds data directly from the
     * Health Connect API into the UI.
     */
    private static final class ApiBiometricsSource implements BiometricsSource {
        @NonNull
        @Override
        public List<Biometrics> loadAll(Context context) {
            // 1. Fetch real rolling 24-hour sleep sessions from your API
            List<SleepSessionRecord> sessions = HealthConnectReader.getSleepSessionsLast24Hours(context);

            // 2. Calculate the total sleep time in hours, then convert to minutes for the UI model
            double totalHours = HealthConnectReader.calculateTotalSleepHours(sessions);
            int sleepMinutes = (int) (totalHours * 60);

            // 3. Fetch the real rolling 24-hour HRV
            double hrv = HealthConnectReader.getAverageHrvLast24Hours(context);

            // If the API returns 0 (meaning no permissions or no data), fall back to empty list so UI uses prototype defaults
            if (sleepMinutes <= 0) {
                return Collections.emptyList();
            }

            // Map the API results to the UI's expected format
            String todayIso = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            Biometrics liveData = new Biometrics(todayIso, sleepMinutes, hrv);

            return Collections.singletonList(liveData);
        }
    }

    /** Prototype values, used while no record has been stored. */
    private static final int FALLBACK_BASELINE_MINUTES = 432;      // 7.2 hrs
    private static final int FALLBACK_LAST_NIGHT_MINUTES = 258;    // 4.3 hrs
    private static final double FALLBACK_HRV_MS = 28d;

    /** Fill fractions from the prototype's inline widths (90% and 53.75%). */
    private static final float BASELINE_FRACTION = 0.90f;
    private static final float LAST_NIGHT_FRACTION = 0.5375f;

    /** The prototype walks the penalty bar from 40% to 95% one point every 45ms. */
    private static final float PENALTY_FROM = 0.40f;
    private static final float PENALTY_TO = 0.95f;
    private static final long PENALTY_DURATION_MS = 2500L;

    /** Prototype animate-ping / animate-pulse are both a slow opacity breathing loop. */
    private static final long DOT_PULSE_DURATION_MS = 1200L;
    private static final float DOT_PULSE_MIN_ALPHA = 0.25f;

    private View penaltyTrack;
    private View penaltyBar;
    private TextView penaltyPercent;
    private View eventDot;
    private View penaltyDot;

    private ValueAnimator penaltyAnimator;
    private ValueAnimator eventDotPulse;
    private ValueAnimator penaltyDotPulse;

    @DrawableRes
    private int penaltyFillRes;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_biometrics, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ScreenNav.bindNav(this, view, ScreenNav.Tab.BIOMETRICS);
        AppHeader.bind(this, view, R.string.nav_biometrics);

        penaltyTrack = view.findViewById(R.id.penaltyTrack);
        penaltyBar = view.findViewById(R.id.penaltyBar);
        penaltyPercent = view.findViewById(R.id.penaltyPercent);
        eventDot = view.findViewById(R.id.eventDot);
        penaltyDot = view.findViewById(R.id.penaltyDot);

        bindToneCopy(view);

        // Fetch data from Health Connect directly
        List<Biometrics> records = createSource().loadAll(requireContext());
        Biometrics newest = newestRecord(records);

        LinearLayout metricBars = view.findViewById(R.id.metricBarsContainer);
        addMetricBar(metricBars,
                R.string.bio_baseline_label,
                formatHours(FALLBACK_BASELINE_MINUTES), // Baseline remains prototype data until we pull 7-day history
                R.drawable.dot_mint,
                R.color.brand_mint,
                BASELINE_FRACTION);
        addMetricBar(metricBars,
                R.string.bio_lastnight_label,
                formatHours(newest == null
                        ? FALLBACK_LAST_NIGHT_MINUTES
                        : newest.getSleepDurationMinutes()),
                R.drawable.dot_red,
                R.color.status_red,
                newest == null ? LAST_NIGHT_FRACTION : calculateFillFraction(newest.getSleepDurationMinutes()));

        TextView hrvValue = view.findViewById(R.id.hrvValue);
        hrvValue.setText(formatHrv(newest));

        reportHealthConnectCapability();

        View overrideButton = view.findViewById(R.id.overrideSensorButton);
        // "Override Sensor Metric" opens the Hard Limits screen, where the study / work /
        // co-curricular ceilings are actually editable, rather than the dashboard.
        overrideButton.setOnClickListener(v -> ScreenNav.showHardLimits(this));

        startPenaltyAnimation();
        eventDotPulse = startDotPulse(eventDot);
        penaltyDotPulse = startDotPulse(penaltyDot);
    }

    /**
     * Rewrites the screen's prose in the user's coaching tone.
     *
     * <p>A layout cannot know which tone the user picked, so the wording that reads as the
     * app talking is resolved here rather than left to the layout's {@code android:text}.
     * The layout keeps the Hype variant, which gives a sensible design-time preview and a
     * graceful fallback if this pass ever runs without a tone.
     *
     * <p>Null-tolerant per view, like the rest of the shell's view binding: a missing id
     * costs one line of copy rather than crashing the screen.
     */
    private void bindToneCopy(@NonNull View view) {
        if (getContext() == null) {
            return;
        }
        ToneType tone = OnboardingPrefs.getTone(requireContext());

        ToneCopy.on(view, tone)
                .set(R.id.bioEventPill, CoachVoice.Line.BIO_EVENT_PILL)
                .set(R.id.bioAnomalyTitle, CoachVoice.Line.BIO_ANOMALY_TITLE)
                .set(R.id.bioAnomalyBody, CoachVoice.Line.BIO_ANOMALY_BODY)
                .set(R.id.bioHrvState, CoachVoice.Line.BIO_HRV_STATE)
                .set(R.id.bioHrvStateSub, CoachVoice.Line.BIO_HRV_STATE_SUB)
                .set(R.id.bioTrendState, CoachVoice.Line.BIO_TREND_STATE)
                .set(R.id.bioPenaltySub, CoachVoice.Line.BIO_PENALTY_SUB)
                .set(R.id.bioPenaltyFootnote, CoachVoice.Line.BIO_PENALTY_FOOTNOTE);
    }

    /**
     * Replaces the static fractions with dynamic math when real API data arrives.
     * Target is 8 hours (480 minutes).
     */
    private float calculateFillFraction(int minutes) {
        float fraction = (float) minutes / 480f;
        if (fraction > 1.0f) return 1.0f;
        if (fraction < 0.1f) return 0.1f;
        return fraction;
    }

    /**
     * The one place that knows how to read biometric records. Swap the returned
     * implementation for the Room backed one when the shared database lands.
     */
    @NonNull
    private BiometricsSource createSource() {
        return new ApiBiometricsSource();
    }

    // ==================================================================
    // Derivation from the stored records (with prototype fallbacks)
    // ==================================================================

    /** Newest record by its yyyy-MM-dd date, or null when nothing is stored. */
    @Nullable
    private static Biometrics newestRecord(@NonNull List<Biometrics> records) {
        Biometrics newest = null;
        for (Biometrics record : records) {
            if (newest == null) {
                newest = record;
                continue;
            }
            String candidate = record.getDate();
            String current = newest.getDate();
            if (candidate != null && current != null && candidate.compareTo(current) > 0) {
                newest = record;
            }
        }
        return newest;
    }

    /** sleepDurationMinutes is an int of minutes, so it is shown as hours with one decimal. */
    @NonNull
    private static String formatHours(int minutes) {
        return String.format(Locale.US, "%.1f hrs", minutes / 60f);
    }

    @NonNull
    private String formatHrv(@Nullable Biometrics newest) {
        Double hrv = newest == null ? null : newest.getHrv();
        if (hrv == null || hrv <= 0) {
            // No record at all still shows the prototype figure; a record with a null HRV
            // is reported honestly as not recorded.
            return newest == null
                    ? String.format(Locale.US, "%.0f ms", FALLBACK_HRV_MS)
                    : getString(R.string.bio_hrv_unavailable);
        }
        return String.format(Locale.US, "%.0f ms", hrv);
    }

    // ==================================================================
    // Metric rows
    // ==================================================================

    /**
     * Inflates one {@code item_metric_bar} row into the metrics card.
     *
     * <p>The value parameter is a pre-formatted string rather than a string resource: both
     * rows show numbers derived from stored records, so they cannot be static resources.
     *
     * <p>The fill width is applied after the first layout pass. During onViewCreated the
     * track is still 0px wide, so a width computed there would collapse to nothing.
     */
    private void addMetricBar(@NonNull LinearLayout parent,
                              @StringRes int labelRes,
                              @NonNull CharSequence value,
                              @DrawableRes int dotRes,
                              @ColorRes int colorRes,
                              float fraction) {
        View row = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_metric_bar, parent, false);

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (parent.getChildCount() > 0) {
            rowParams.topMargin = getResources().getDimensionPixelSize(R.dimen.space_sm);
        }
        row.setLayoutParams(rowParams);

        View dot = row.findViewById(R.id.metricDot);
        dot.setBackgroundResource(dotRes);

        TextView label = row.findViewById(R.id.metricLabel);
        label.setText(labelRes);

        TextView valueView = row.findViewById(R.id.metricValue);
        valueView.setText(value);
        valueView.setTextColor(ContextCompat.getColor(parent.getContext(), colorRes));

        // Prototype colours the fill to match the row: mint for baseline, red for last night.
        final View fill = row.findViewById(R.id.metricFill);
        fill.setBackgroundResource(colorRes == R.color.status_red
                ? R.drawable.bg_progress_red
                : R.drawable.bg_progress_mint);

        final View track = row.findViewById(R.id.metricTrack);
        final float fillFraction = fraction;
        track.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v,
                                       int left,
                                       int top,
                                       int right,
                                       int bottom,
                                       int oldLeft,
                                       int oldTop,
                                       int oldRight,
                                       int oldBottom) {
                int trackWidth = right - left;
                if (trackWidth <= 0) {
                    return;
                }
                v.removeOnLayoutChangeListener(this);
                ViewGroup.LayoutParams params = fill.getLayoutParams();
                params.width = Math.round(trackWidth * fillFraction);
                fill.setLayoutParams(params);
            }
        });

        parent.addView(row);
    }

    // ==================================================================
    // Cognitive penalty bar
    // ==================================================================

    private void startPenaltyAnimation() {
        if (penaltyTrack == null) {
            return;
        }
        penaltyTrack.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v,
                                       int left,
                                       int top,
                                       int right,
                                       int bottom,
                                       int oldLeft,
                                       int oldTop,
                                       int oldRight,
                                       int oldBottom) {
                int trackWidth = right - left;
                if (trackWidth <= 0) {
                    return;
                }
                v.removeOnLayoutChangeListener(this);
                runPenaltyAnimation(trackWidth);
            }
        });

        // Already measured (for example when the view hierarchy was reused): start now.
        if (penaltyTrack.getWidth() > 0) {
            runPenaltyAnimation(penaltyTrack.getWidth());
        }
    }

    private void runPenaltyAnimation(int trackWidth) {
        if (penaltyAnimator != null || trackWidth <= 0) {
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(PENALTY_FROM, PENALTY_TO);
        animator.setDuration(PENALTY_DURATION_MS);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation ->
                applyPenaltyFraction(trackWidth, (Float) animation.getAnimatedValue()));
        penaltyAnimator = animator;
        animator.start();
    }

    private void applyPenaltyFraction(int trackWidth, float fraction) {
        if (penaltyBar == null || penaltyPercent == null) {
            return;
        }

        ViewGroup.LayoutParams params = penaltyBar.getLayoutParams();
        params.width = Math.round(trackWidth * fraction);
        penaltyBar.setLayoutParams(params);

        // Band colours come straight from the prototype's class swap.
        @DrawableRes int fill;
        if (fraction > 0.75f) {
            fill = R.drawable.bg_progress_red;
        } else if (fraction > 0.55f) {
            fill = R.drawable.bg_progress_amber;
        } else {
            fill = R.drawable.bg_progress_mint;
        }
        if (fill != penaltyFillRes) {
            penaltyFillRes = fill;
            penaltyBar.setBackgroundResource(fill);
        }

        penaltyPercent.setText(Math.round(fraction * 100) + "%");
    }

    // ==================================================================
    // Ambient state animations
    // ==================================================================

    @Nullable
    private static ValueAnimator startDotPulse(@Nullable View dot) {
        if (dot == null) {
            return null;
        }
        ValueAnimator animator =
                ValueAnimator.ofFloat(1f, DOT_PULSE_MIN_ALPHA, 1f);
        animator.setDuration(DOT_PULSE_DURATION_MS);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.RESTART);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(animation ->
                dot.setAlpha((Float) animation.getAnimatedValue()));
        animator.start();
        return animator;
    }

    // ==================================================================
    // Health Connect capability reporting
    // ==================================================================

    /**
     * Reporting only. The permission request lives on the onboarding screen, so this screen
     * never asks for permissions and never writes mock sleep sessions.
     */
    private void reportHealthConnectCapability() {
        if (!isAdded()) {
            return;
        }
        try {
            HealthConnectManager manager = new HealthConnectManager(requireContext());
            if (!manager.isClientAvailable()) {
                Toast.makeText(requireContext(),
                        getString(R.string.bio_health_connect) + " unavailable on this device",
                        Toast.LENGTH_SHORT).show();
            }
        } catch (RuntimeException e) {
            // Health Connect is not installable on every device; the screen still works
            // from stored records, so a missing provider is not fatal.
            Toast.makeText(requireContext(),
                    getString(R.string.bio_health_connect) + " unavailable on this device",
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        cancelAnimator(penaltyAnimator);
        penaltyAnimator = null;
        cancelAnimator(eventDotPulse);
        eventDotPulse = null;
        cancelAnimator(penaltyDotPulse);
        penaltyDotPulse = null;

        penaltyTrack = null;
        penaltyBar = null;
        penaltyPercent = null;
        eventDot = null;
        penaltyDot = null;
        penaltyFillRes = 0;

        super.onDestroyView();
    }

    private static void cancelAnimator(@Nullable ValueAnimator animator) {
        if (animator != null) {
            animator.cancel();
        }
    }
}