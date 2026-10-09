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
import androidx.health.connect.client.HealthConnectClient;
import androidx.health.connect.client.records.SleepSessionRecord;

import com.example.codenection2026_package.R;
import com.example.codenection2026_package.api.HealthConnectHelper;
import com.example.codenection2026_package.api.HealthConnectManager;
import com.example.codenection2026_package.api.HealthConnectReader;
import com.example.codenection2026_package.model.CoachVoice;
import com.example.codenection2026_package.model.ToneType;
import com.example.codenection2026_package.ui.onboarding.OnboardingPrefs;
import com.example.codenection2026_package.ui.shell.AppHeader;
import com.example.codenection2026_package.ui.shell.ScreenNav;
import com.example.codenection2026_package.ui.shell.ToneCopy;
import com.example.codenection2026_package.ui.widget.TrendChartView;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * SCREEN 5 - BIOMETRICS (port of Prototype/biometrics.html).
 *
 * <p>Shows the overnight sleep anomaly, the sleep debt and HRV metrics, and the cognitive
 * penalty the app applies to today's capacity.
 *
 * <p><b>Every figure on this screen is a Health Connect reading.</b> The screen used to
 * paint the prototype's own numbers whenever a reading was missing - a 7.2 hr baseline, a
 * 4.3 hr night, a 28 ms HRV - and its penalty bar animated from 40% to 95% without looking
 * at any data at all. Those are gone. A figure is now either the real reading or an
 * explicit "Not recorded", and the alert card only appears when the alert is real.
 *
 * <p>The arithmetic is not reimplemented here. It all comes from {@link HealthConnectReader},
 * used exactly as written: the rolling 24-hour sleep read, the seven-day history, the sleep
 * debt, the 0-100 recovery-debt score and the HRV mean. That class's debt, debt-score and
 * HRV helpers were written but never called from anywhere until this screen called them -
 * which is precisely why this screen was showing invented numbers while the code to compute
 * the real ones already existed.
 *
 * <p>The load-shedding rate is worth calling out: it is
 * {@link HealthConnectReader#calculateRecoveryDebtScore}, the same 0-100 score
 * {@code engine.LoadShedder} takes as its {@code recoveryDebtScore} input. The number on
 * screen is therefore the number the triage model will actually use, not a lookalike
 * computed for display.
 */
public class BiometricsFragment extends Fragment {

    /**
     * The guideline the debt and the deficit are judged against. The screen's own anomaly
     * copy names it ("Target: 8h 00m"), and it is the scale both sleep bars are drawn on.
     */
    private static final double TARGET_SLEEP_HOURS = 8.0;

    /** A full sleep bar is the target: 480 minutes. */
    private static final int SCALE_MINUTES = (int) (TARGET_SLEEP_HOURS * 60);

    /** How many days of history the "Baseline Average" row averages over. */
    private static final int BASELINE_DAYS = 7;

    /**
     * Below this, last night is an anomaly worth alerting on.
     *
     * <p>Deliberately the threshold the alert's own headline states ("&lt; 5 Hours Sleep
     * Detected"), so the card and its copy cannot end up disagreeing.
     */
    private static final double ANOMALY_SLEEP_HOURS = 5.0;

    /** The prototype walks the penalty bar up; it now climbs to the real score. */
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

    /** Where the penalty bar has to settle, as a 0-1 fraction of its track. */
    private float penaltyFraction;

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

        Snapshot snapshot = read(requireContext());

        bindToneCopy(view);
        bindAnomaly(view, snapshot);
        bindSleepMetrics(view, snapshot);
        bindTrend(view, snapshot);
        bindHrv(view, snapshot);
        bindDeficitAndPenalty(view, snapshot);

        reportHealthConnectCapability();

        View overrideButton = view.findViewById(R.id.overrideSensorButton);
        // "Override Sensor Metric" opens the Hard Limits screen, where the study / work /
        // co-curricular ceilings are actually editable, rather than the dashboard.
        overrideButton.setOnClickListener(v -> ScreenNav.showHardLimits(this));

        // Wire the manual sleep button
        View manualSleepButton = view.findViewById(R.id.manualSleepButton);
        if (manualSleepButton != null) {
            // THE FIX: Route to the permission checker first
            manualSleepButton.setOnClickListener(v -> checkPermissionsAndShowSlider());
        }

        startPenaltyAnimation();
        eventDotPulse = startDotPulse(eventDot);
        penaltyDotPulse = startDotPulse(penaltyDot);

        // If Health Connect is empty, check if we've already educated the user.
        if (snapshot.lastNightMinutes == 0) {
            android.content.SharedPreferences prefs = requireContext().getSharedPreferences("BioPrefs", Context.MODE_PRIVATE);
            boolean hasSeenDialog = prefs.getBoolean("hide_clock_dialog", false);

            if (!hasSeenDialog) {
                view.postDelayed(this::showNoWearableFallbackDialog, 500);
            }
        }
    }

    // ==================================================================
    // Reading Health Connect
    // ==================================================================

    /** Every figure the screen shows, read once per bind. */
    private static final class Snapshot {

        /** Seven-day mean, in minutes. 0 when Health Connect holds no history. */
        final int baselineMinutes;

        /** Last night's total, in minutes. 0 when nothing was recorded. */
        final int lastNightMinutes;

        /** Mean HRV over the last 24 hours, in milliseconds. 0 when not recorded. */
        final double hrvMs;

        /** How far under the target last night fell, in whole percent. 0 when it did not. */
        final int deficitPercent;

        /** The 0-100 recovery-debt score, which is what the load shedder consumes. */
        final int recoveryDebtScore;

        /** True when last night is short enough to alert on. */
        final boolean anomaly;

        /** Local time the last sleep session ended. Null unless there is an anomaly. */
        @Nullable
        final String anomalyTime;

        /**
         * One sleep total per night over the last week, oldest first, with
         * {@link TrendChartView#NO_READING} for a night nothing was recorded. The trend
         * sparkline draws this, and the Baseline row is its mean.
         */
        @NonNull
        final float[] nightlyMinutes;

        Snapshot(int baselineMinutes,
                 int lastNightMinutes,
                 double hrvMs,
                 int deficitPercent,
                 int recoveryDebtScore,
                 boolean anomaly,
                 @Nullable String anomalyTime,
                 @NonNull float[] nightlyMinutes) {
            this.baselineMinutes = baselineMinutes;
            this.lastNightMinutes = lastNightMinutes;
            this.hrvMs = hrvMs;
            this.deficitPercent = deficitPercent;
            this.recoveryDebtScore = recoveryDebtScore;
            this.anomaly = anomaly;
            this.anomalyTime = anomalyTime;
            this.nightlyMinutes = nightlyMinutes;
        }
    }

    /**
     * Reads the screen's figures from Health Connect.
     *
     * <p>Only {@link HealthConnectReader} and {@link HealthConnectHelper} are used, both
     * exactly as they were written. Nothing here re-derives sleep debt or the debt score,
     * because both already exist and are the values the triage model will consume.
     */
    @NonNull
    private static Snapshot read(@NonNull Context context) {
        List<SleepSessionRecord> lastNight = HealthConnectReader.getSleepSessionsLast24Hours(context);
        double lastNightHours = HealthConnectReader.calculateTotalSleepHours(lastNight);
        boolean hasSleep = lastNightHours > 0;

        // One seven-day read serves both the Baseline row and the trend sparkline, so the two
        // can never disagree about how the week went.
        float[] nightly = nightlyMinutes(readSleepSessions(context, BASELINE_DAYS), BASELINE_DAYS);
        int baselineMinutes = meanOfRecordedNights(nightly);

        double hrvMs = HealthConnectReader.getAverageHrvLast24Hours(context);

        // The existing helpers own both of these. Debt is measured against the target, and
        // the resulting 0-100 score is exactly the recoveryDebtScore LoadShedder expects.
        double debtHours = HealthConnectReader.getSleepDebtHours(lastNight, TARGET_SLEEP_HOURS);
        int debtScore = HealthConnectReader.calculateRecoveryDebtScore(debtHours, hrvMs, hasSleep);
        int deficitPercent = hasSleep && debtHours > 0
                ? (int) Math.round(debtHours / TARGET_SLEEP_HOURS * 100.0)
                : 0;

        boolean anomaly = hasSleep && lastNightHours < ANOMALY_SLEEP_HOURS;

        return new Snapshot(
                baselineMinutes,
                hasSleep ? (int) Math.round(lastNightHours * 60.0) : 0,
                hrvMs,
                deficitPercent,
                debtScore,
                anomaly,
                anomaly ? latestEndTime(lastNight) : null,
                nightly);
    }

    /**
     * Sleep sessions over the last {@code days} days, through the same helper the rolling
     * 24-hour read uses. That helper takes an arbitrary window, so a seven-day history needs
     * no new query code - only a wider range.
     *
     * @return an empty list when Health Connect is unreachable or was never granted the
     *         read permission
     */
    @NonNull
    private static List<SleepSessionRecord> readSleepSessions(@NonNull Context context, int days) {
        try {
            HealthConnectClient client = HealthConnectClient.getOrCreate(context);
            Instant end = Instant.now();
            Instant start = end.minus(days, ChronoUnit.DAYS);
            return HealthConnectHelper.readSleepDataSync(client, start, end);
        } catch (Exception e) {
            // Every device without Health Connect, and every install where the user never
            // granted the read permission, lands here.
            return Collections.emptyList();
        }
    }

    /**
     * Totals one night at a time, oldest first, keyed by the local date a session ended on -
     * the morning the user woke, which is how sleep is normally attributed to a night.
     *
     * <p>Each night is summed through {@link HealthConnectReader#calculateTotalSleepHours},
     * so the sparkline and the metrics card divide the same way rather than each doing their
     * own arithmetic.
     *
     * @return one entry per night, oldest first, with {@link TrendChartView#NO_READING}
     *         where Health Connect holds nothing
     */
    @NonNull
    private static float[] nightlyMinutes(@NonNull List<SleepSessionRecord> sessions, int days) {
        List<List<SleepSessionRecord>> byNight = new ArrayList<>(days);
        for (int i = 0; i < days; i++) {
            byNight.add(new ArrayList<>());
        }

        ZoneId deviceZone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(deviceZone);

        for (SleepSessionRecord session : sessions) {
            // The session records the offset it happened in, so the night it belongs to is
            // the date it ended on where it was slept, not where the phone is now.
            ZoneOffset offset = session.getEndZoneOffset();
            ZoneId zone = offset != null ? offset : deviceZone;
            long nightsAgo = ChronoUnit.DAYS.between(
                    session.getEndTime().atZone(zone).toLocalDate(), today);
            if (nightsAgo < 0 || nightsAgo >= days) {
                continue;
            }
            byNight.get(days - 1 - (int) nightsAgo).add(session);
        }

        float[] totals = new float[days];
        for (int i = 0; i < days; i++) {
            List<SleepSessionRecord> night = byNight.get(i);
            totals[i] = night.isEmpty()
                    ? TrendChartView.NO_READING
                    : (float) (HealthConnectReader.calculateTotalSleepHours(night) * 60.0);
        }
        return totals;
    }

    /**
     * The Baseline row: the mean of the nights that were actually recorded.
     *
     * <p>Dividing by seven instead would treat every unrecorded night as a night of no sleep
     * and drag the baseline down, which is a claim the data does not support.
     */
    private static int meanOfRecordedNights(@NonNull float[] nightly) {
        float sum = 0f;
        int recorded = 0;
        for (float minutes : nightly) {
            if (minutes > 0f) {
                sum += minutes;
                recorded++;
            }
        }
        return recorded == 0 ? 0 : Math.round(sum / recorded);
    }

    /** The newest night carrying a reading, or -1 when the week is empty. */
    private static int latestRecordedIndex(@NonNull float[] nightly) {
        for (int i = nightly.length - 1; i >= 0; i--) {
            if (nightly[i] >= 0f) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The most recent night that fell short of the target, skipping the newest night.
     *
     * <p>The amber marker answers "where did the week start going wrong", so it must not sit
     * on the same point as the pulsing alarm, which already owns the newest night.
     */
    private static int warnIndex(@NonNull float[] nightly, int latestIndex) {
        for (int i = latestIndex - 1; i >= 0; i--) {
            if (nightly[i] >= 0f && nightly[i] < SCALE_MINUTES) {
                return i;
            }
        }
        return -1;
    }

    /** Local time the latest session in the list ended, or null when the list is empty. */
    @Nullable
    private static String latestEndTime(@NonNull List<SleepSessionRecord> sessions) {
        SleepSessionRecord latest = null;
        for (SleepSessionRecord session : sessions) {
            if (latest == null || session.getEndTime().isAfter(latest.getEndTime())) {
                latest = session;
            }
        }
        if (latest == null) {
            return null;
        }
        // The session records the offset it happened in, and that is the wall-clock time the
        // user woke at. Formatting in the device's zone instead would print a different hour
        // for anyone who slept somewhere else, so the device zone is only the fallback for a
        // record that carries no offset at all.
        ZoneOffset recordedOffset = latest.getEndZoneOffset();
        ZoneId zone = recordedOffset != null ? recordedOffset : ZoneId.systemDefault();
        return DateTimeFormatter.ofPattern("hh:mm a", Locale.US)
                .withZone(zone)
                .format(latest.getEndTime());
    }

    // ==================================================================
    // Binding
    // ==================================================================

    /**
     * The anomaly card and its status pill.
     *
     * <p>Both are hidden unless last night really is short. That is what keeps the
     * headline honest: its claim is "&lt; 5 Hours Sleep Detected", so the card may only be
     * on screen when a reading is under {@link #ANOMALY_SLEEP_HOURS}. Both numbers in the
     * body come from the reading rather than from the prototype's copy.
     */
    private void bindAnomaly(@NonNull View view, @NonNull Snapshot snapshot) {
        setVisible(view.findViewById(R.id.anomalyCard), snapshot.anomaly);
        setVisible(view.findViewById(R.id.eventPillRow), snapshot.anomaly);
        if (!snapshot.anomaly) {
            return;
        }

        ToneType tone = tone();
        TextView title = view.findViewById(R.id.bioAnomalyTitle);
        if (title != null) {
            title.setText(CoachVoice.Line.BIO_ANOMALY_TITLE.pick(tone));
        }

        TextView body = view.findViewById(R.id.bioAnomalyBody);
        if (body != null) {
            // The body names two durations, so it takes arguments rather than going
            // through ToneCopy, which only sets a whole string.
            body.setText(getString(CoachVoice.Line.BIO_ANOMALY_BODY.pick(tone),
                    formatDuration(snapshot.lastNightMinutes),
                    formatDuration(SCALE_MINUTES)));
        }

        TextView time = view.findViewById(R.id.anomalyTime);
        if (time != null && snapshot.anomalyTime != null) {
            time.setText(getString(R.string.bio_anomaly_time, snapshot.anomalyTime));
        }
    }

    /**
     * The two sleep rows.
     *
     * <p>A row with no reading says so rather than falling back to the prototype's numbers,
     * which is what used to make this card look like it held data it did not.
     */
    private void bindSleepMetrics(@NonNull View view, @NonNull Snapshot snapshot) {
        LinearLayout metricBars = view.findViewById(R.id.metricBarsContainer);
        if (metricBars == null) {
            return;
        }
        metricBars.removeAllViews();

        addMetricBar(metricBars,
                R.string.bio_baseline_label,
                sleepValue(snapshot.baselineMinutes),
                R.drawable.dot_mint,
                R.color.brand_mint,
                fillFraction(snapshot.baselineMinutes));
        addMetricBar(metricBars,
                R.string.bio_lastnight_label,
                sleepValue(snapshot.lastNightMinutes),
                R.drawable.dot_red,
                R.color.status_red,
                fillFraction(snapshot.lastNightMinutes));
    }

    /**
     * Hands the week to the trend sparkline.
     *
     * <p>Both markers are chosen here rather than inside the widget: "newest night" and "the
     * night the week started going short" are facts about the readings, not about how the
     * chart happens to be drawn.
     */
    private void bindTrend(@NonNull View view, @NonNull Snapshot snapshot) {
        TrendChartView chart = view.findViewById(R.id.sleepTrendChart);
        if (chart == null) {
            return;
        }
        int latest = latestRecordedIndex(snapshot.nightlyMinutes);
        chart.setNights(snapshot.nightlyMinutes,
                SCALE_MINUTES,
                latest,
                warnIndex(snapshot.nightlyMinutes, latest));
    }

    /** The real rolling 24-hour HRV mean, or the honest empty state. */
    private void bindHrv(@NonNull View view, @NonNull Snapshot snapshot) {
        TextView hrvValue = view.findViewById(R.id.hrvValue);
        if (hrvValue == null) {
            return;
        }
        hrvValue.setText(snapshot.hrvMs > 0
                ? getString(R.string.bio_hrv_value, formatHrv(snapshot.hrvMs))
                : getString(R.string.bio_not_recorded));
    }

    /**
     * The deficit badge, and the fraction the load-shedding bar has to settle on.
     *
     * <p>The rate is the recovery-debt score straight from
     * {@link HealthConnectReader#calculateRecoveryDebtScore} - the same number
     * {@code engine.LoadShedder} receives as {@code recoveryDebtScore}, so the screen and
     * the triage model cannot disagree about how depleted today is.
     */
    private void bindDeficitAndPenalty(@NonNull View view, @NonNull Snapshot snapshot) {
        TextView badge = view.findViewById(R.id.deficitBadge);
        if (badge != null) {
            if (snapshot.deficitPercent > 0) {
                badge.setVisibility(View.VISIBLE);
                badge.setText(getString(R.string.bio_deficit_badge, snapshot.deficitPercent));
            } else if (snapshot.lastNightMinutes > 0) {
                // Slept at or past the target, so the shortfall is a real, reportable zero.
                badge.setVisibility(View.VISIBLE);
                badge.setText(R.string.bio_deficit_none);
            } else {
                // No reading, so there is no shortfall to claim in either direction.
                badge.setVisibility(View.GONE);
            }
        }

        penaltyFraction = snapshot.recoveryDebtScore / 100f;
    }

    /**
     * Rewrites the screen's prose in the user's coaching tone.
     *
     * <p>A layout cannot know which tone the user picked, so the wording that reads as the
     * app talking is resolved here rather than left to the layout's {@code android:text}.
     * The layout keeps the Hype variant, which gives a sensible design-time preview and a
     * graceful fallback if this pass ever runs without a tone.
     *
     * <p>The anomaly title and body are not here: both live in {@link #bindAnomaly}, which
     * only runs when there is an anomaly to report and which has the reading the body's
     * two durations need.
     *
     * <p>Null-tolerant per view, like the rest of the shell's view binding: a missing id
     * costs one line of copy rather than crashing the screen.
     */
    private void bindToneCopy(@NonNull View view) {
        if (getContext() == null) {
            return;
        }
        ToneCopy.on(view, tone())
                .set(R.id.bioEventPill, CoachVoice.Line.BIO_EVENT_PILL)
                .set(R.id.bioHrvState, CoachVoice.Line.BIO_HRV_STATE)
                .set(R.id.bioHrvStateSub, CoachVoice.Line.BIO_HRV_STATE_SUB)
                .set(R.id.bioTrendState, CoachVoice.Line.BIO_TREND_STATE)
                .set(R.id.bioPenaltySub, CoachVoice.Line.BIO_PENALTY_SUB)
                .set(R.id.bioPenaltyFootnote, CoachVoice.Line.BIO_PENALTY_FOOTNOTE);
    }

    /** The coaching tone chosen in onboarding or Settings. */
    @NonNull
    private ToneType tone() {
        return OnboardingPrefs.getTone(requireContext());
    }

    // ==================================================================
    // Formatting
    // ==================================================================

    /** "7.2", or "Not recorded" when there is no reading. The unit comes from resources. */
    @NonNull
    private String sleepValue(int minutes) {
        return minutes > 0
                ? getString(R.string.bio_sleep_hours, formatHours(minutes))
                : getString(R.string.bio_not_recorded);
    }

    /** Sleep minutes as hours to one decimal: {@code bio_sleep_hours} adds the unit. */
    @NonNull
    private static String formatHours(int minutes) {
        return String.format(Locale.US, "%.1f", minutes / 60f);
    }

    /** Minutes as the alert copy writes them: 258 becomes "4h 18m", 480 becomes "8h 00m". */
    @NonNull
    private String formatDuration(int minutes) {
        return getString(R.string.bio_duration_hm, minutes / 60, minutes % 60);
    }

    /** HRV as whole milliseconds: {@code bio_hrv_value} adds the unit. */
    @NonNull
    private static String formatHrv(double millis) {
        return String.format(Locale.US, "%.0f", millis);
    }

    /**
     * How full a sleep bar is, against the target.
     *
     * <p>A row with no reading stays empty rather than showing a stub: a 10% sliver next to
     * "Not recorded" reads as a small amount of sleep, which is exactly the confusion the
     * prototype numbers caused.
     */
    private static float fillFraction(int minutes) {
        if (minutes <= 0) {
            return 0f;
        }
        return Math.min(1f, minutes / (float) SCALE_MINUTES);
    }

    // ==================================================================
    // Metric rows
    // ==================================================================

    /**
     * Inflates one {@code item_metric_bar} row into the metrics card.
     *
     * <p>The value parameter is a pre-formatted string rather than a string resource: both
     * rows show numbers derived from the reading, so they cannot be static resources.
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
        // Climbs from empty to the real score. The prototype's 40% floor was an invented
        // starting point, and keeping it would show a penalty no reading supports.
        ValueAnimator animator = ValueAnimator.ofFloat(0f, penaltyFraction);
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

        penaltyPercent.setText(getString(R.string.percent_value, Math.round(fraction * 100)));
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
                        getString(R.string.bio_health_connect_unavailable,
                                getString(R.string.bio_health_connect)),
                        Toast.LENGTH_SHORT).show();
            }
        } catch (RuntimeException e) {
            // Health Connect is not installable on every device; the screen still works
            // from the reading, so a missing provider is not fatal.
            Toast.makeText(requireContext(),
                    getString(R.string.bio_health_connect_unavailable,
                            getString(R.string.bio_health_connect)),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private static void setVisible(@Nullable View view, boolean visible) {
        if (view != null) {
            view.setVisibility(visible ? View.VISIBLE : View.GONE);
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
        penaltyFraction = 0f;

        super.onDestroyView();
    }

    private static void cancelAnimator(@Nullable ValueAnimator animator) {
        if (animator != null) {
            animator.cancel();
        }
    }

    /**
     * Educates users on missing data and routes them directly to the manual slider.
     */
    private void showNoWearableFallbackDialog() {
        if (!isAdded() || getContext() == null) {
            return;
        }

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.bio_no_data_title)
                .setMessage(R.string.bio_no_data_msg)
                .setPositiveButton(R.string.bio_log_manual_btn, (dialog, which) -> {
                    requireContext().getSharedPreferences("BioPrefs", Context.MODE_PRIVATE)
                            .edit().putBoolean("hide_clock_dialog", true).apply();
                    checkPermissionsAndShowSlider();
                })
                // THE FIX: 'null' listener ensures "Maybe later" doesn't trigger the 'seen' flag
                .setNegativeButton(R.string.bio_maybe_later_btn, null)
                .show();
    }

    /**
     * THE FIX: Intercepts the user before the slider opens to ensure they have write access.
     */
    private void checkPermissionsAndShowSlider() {
        if (ContextCompat.checkSelfPermission(requireContext(), "android.permission.health.WRITE_SLEEP") == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            showManualSleepSliderDialog();
        } else {
            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle(R.string.bio_permission_title)
                    .setMessage(R.string.bio_permission_msg)
                    .setPositiveButton(R.string.bio_got_it_btn, null)
                    .show();
        }
    }

    /**
     * Shows a Material Slider dialog for frictionless manual sleep logging.
     */
    private void showManualSleepSliderDialog() {
        Context context = requireContext();

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 40, 60, 0);

        TextView valueText = new TextView(context);
        valueText.setText(getString(R.string.bio_slider_value, 8.0f));
        valueText.setTextSize(18f);
        valueText.setTextColor(ContextCompat.getColor(context, R.color.text_primary_dark));
        valueText.setGravity(android.view.Gravity.CENTER);

        com.google.android.material.slider.Slider slider = new com.google.android.material.slider.Slider(context);
        slider.setValueFrom(0.5f);
        slider.setValueTo(24.0f);
        slider.setStepSize(0.5f);
        slider.setValue(8.0f);

        slider.addOnChangeListener((s, val, fromUser) -> valueText.setText(getString(R.string.bio_slider_value, val)));

        layout.addView(valueText);
        layout.addView(slider);

        new androidx.appcompat.app.AlertDialog.Builder(context)
                .setTitle(R.string.bio_manual_title)
                // THE FIX: Hardcoded warning moved to strings.xml
                .setMessage(R.string.bio_manual_msg_warning)
                .setView(layout)
                .setPositiveButton(R.string.bio_save_btn, (dialog, which) -> {
                    writeManualSleepToHealthConnect(slider.getValue());
                })
                .setNegativeButton(R.string.bio_cancel_btn, null)
                .show();
    }

    /**
     * Converts the slider value into a Health Connect SleepSessionRecord.
     */
    private void writeManualSleepToHealthConnect(double hours) {
        final android.app.Activity activity = getActivity();
        final Context context = getContext();

        if (activity == null || context == null) {
            return;
        }

        new Thread(() -> {
            try {
                HealthConnectClient client = HealthConnectClient.getOrCreate(context);

                // THE FIX (Bug 1): The Reviewer's exact timestamp anchor.
                // Prevents destructive overlaps by anchoring the delete window safely in the past.
                Instant now = Instant.now();
                Instant today8AM = LocalDate.now().atTime(8, 0).atZone(ZoneId.systemDefault()).toInstant();
                Instant endTime = now.isBefore(today8AM) ? today8AM.minus(24, ChronoUnit.HOURS) : today8AM;

                Instant searchStart = endTime.minus(24, ChronoUnit.HOURS);
                HealthConnectHelper.deleteSleepDataSync(client, searchStart, endTime);

                Instant startTime = endTime.minus((long) (hours * 60), ChronoUnit.MINUTES);
                ZoneOffset currentOffset = ZoneId.systemDefault().getRules().getOffset(endTime);

                SleepSessionRecord manualRecord = new SleepSessionRecord(
                        startTime,
                        currentOffset,
                        endTime,
                        currentOffset,
                        context.getString(R.string.bio_manual_record_title), // THE FIX: Metadata string extracted
                        null,
                        Collections.emptyList(),
                        androidx.health.connect.client.records.metadata.Metadata.EMPTY
                );

                HealthConnectHelper.writeSleepDataSync(client, Collections.singletonList(manualRecord));

                activity.runOnUiThread(() -> {
                    if (!isAdded() || getView() == null) {
                        return;
                    }

                    Toast.makeText(context, R.string.bio_manual_success, Toast.LENGTH_SHORT).show();

                    Snapshot newSnapshot = read(context);
                    bindAnomaly(getView(), newSnapshot);
                    bindSleepMetrics(getView(), newSnapshot);
                    bindTrend(getView(), newSnapshot);
                    bindDeficitAndPenalty(getView(), newSnapshot);

                    if (penaltyTrack != null && penaltyTrack.getWidth() > 0) {
                        cancelAnimator(penaltyAnimator);
                        penaltyAnimator = null;
                        runPenaltyAnimation(penaltyTrack.getWidth());
                    }
                });

            } catch (Exception e) {
                activity.runOnUiThread(() -> {
                    if (!isAdded()) return;
                    Toast.makeText(context, getString(R.string.bio_manual_error, e.getMessage()), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
}
