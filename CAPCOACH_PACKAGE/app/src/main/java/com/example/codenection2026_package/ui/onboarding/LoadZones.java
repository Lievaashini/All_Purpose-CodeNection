package com.example.codenection2026_package.ui.onboarding;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;

import com.example.codenection2026_package.R;
import com.example.codenection2026_package.model.CoachVoice;

/**
 * Zone grading for the "Total Committed Load" banner.
 *
 * <p>Extracted from {@code hard-limits.html}, which computed the same thing in JavaScript.
 * Ported verbatim - these thresholds are part of the product definition, not a guess:
 *
 * <pre>
 *   total &lt;= 52  ->  SAFE ZONE / BALANCED
 *   total &lt;= 60  ->  ELEVATED CAPACITY
 *   total &gt;  60  ->  OVERLOAD WARNING
 * </pre>
 *
 * <p>Kept as a standalone, pure class so it can be unit-tested without an emulator.
 */
public final class LoadZones {

    /** Above this, the student is at the safe ceiling. */
    public static final int SAFE_MAX_HOURS = 52;

    /** Above this, the load is unsustainable. */
    public static final int ELEVATED_MAX_HOURS = 60;

    /** The documented weekly work ceiling from the market research. */
    public static final int WORK_CAP_HOURS = 20;

    /** Above this, the work slider turns amber. */
    public static final int WORK_CAUTION_HOURS = 25;

    /** Study slider turns amber above this. */
    public static final int STUDY_CAUTION_HOURS = 35;

    /** Co-curricular slider turns amber above this. */
    public static final int COCURRICULAR_CAUTION_HOURS = 12;

    private LoadZones() {
    }

    /** The three graded states, in increasing severity. */
    public enum Zone {
        SAFE(CoachVoice.Line.ZONE_SAFE, R.color.status_green),
        ELEVATED(CoachVoice.Line.ZONE_ELEVATED, R.color.status_amber),
        OVERLOAD(CoachVoice.Line.ZONE_OVERLOAD, R.color.status_red);

        /**
         * The pill's wording. A slot rather than a string resource, because the wording
         * depends on the user's coaching tone as well as the zone.
         */
        @NonNull
        public final CoachVoice.Line label;

        @ColorRes
        public final int colorRes;

        Zone(@NonNull CoachVoice.Line label, @ColorRes int colorRes) {
            this.label = label;
            this.colorRes = colorRes;
        }
    }

    /** Immutable result of grading a set of commitment hours. */
    public static final class Result {
        public final int totalHours;
        @NonNull
        public final Zone zone;

        Result(int totalHours, @NonNull Zone zone) {
            this.totalHours = totalHours;
            this.zone = zone;
        }
    }

    /**
     * Grades the weekly commitment total.
     *
     * @param studyHours        from the Study &amp; Classes slider
     * @param workHours         from the Weekly Shift Ceiling slider
     * @param cocurricularHours from the Co-curricular slider
     */
    @NonNull
    public static Result grade(int studyHours, int workHours, int cocurricularHours) {
        int total = studyHours + workHours + cocurricularHours;

        Zone zone;
        if (total <= SAFE_MAX_HOURS) {
            zone = Zone.SAFE;
        } else if (total <= ELEVATED_MAX_HOURS) {
            zone = Zone.ELEVATED;
        } else {
            zone = Zone.OVERLOAD;
        }
        return new Result(total, zone);
    }

    /**
     * Maps the work slider value to a status band, mirroring the prototype's
     * three-way branch.
     */
    @NonNull
    public static WorkBand workBand(int workHours) {
        if (workHours <= WORK_CAP_HOURS) {
            return WorkBand.SAFE;
        }
        if (workHours <= WORK_CAUTION_HOURS) {
            return WorkBand.CAUTION;
        }
        return WorkBand.RISK;
    }

    /** Work slider status bands. */
    public enum WorkBand {
        SAFE(CoachVoice.Line.WORK_STATUS_SAFE,
                CoachVoice.Line.WORK_WARNING_SAFE,
                CoachVoice.Line.DINO_WORK_SAFE,
                R.color.status_green),

        CAUTION(CoachVoice.Line.WORK_STATUS_CAUTION,
                CoachVoice.Line.WORK_WARNING_CAUTION,
                CoachVoice.Line.DINO_WORK_CAUTION,
                R.color.status_amber),

        RISK(CoachVoice.Line.WORK_STATUS_RISK,
                CoachVoice.Line.WORK_WARNING_RISK,
                CoachVoice.Line.DINO_WORK_RISK,
                R.color.status_red);

        /**
         * The status word next to the dot. A slot rather than a string resource, because
         * the wording depends on the user's coaching tone as well as the band.
         */
        @NonNull
        public final CoachVoice.Line statusLabel;

        /**
         * The callout under the slider. A slot rather than a string resource, because the
         * wording depends on the user's coaching tone as well as the band.
         */
        @NonNull
        public final CoachVoice.Line warningText;

        /**
         * The guard's line for this band. A slot rather than a string resource, because the
         * wording depends on the user's coaching tone as well as the band.
         */
        @NonNull
        public final CoachVoice.Line dinoQuote;

        @ColorRes
        public final int colorRes;

        WorkBand(@NonNull CoachVoice.Line statusLabel,
                 @NonNull CoachVoice.Line warningText,
                 @NonNull CoachVoice.Line dinoQuote,
                 @ColorRes int colorRes) {
            this.statusLabel = statusLabel;
            this.warningText = warningText;
            this.dinoQuote = dinoQuote;
            this.colorRes = colorRes;
        }
    }
}
