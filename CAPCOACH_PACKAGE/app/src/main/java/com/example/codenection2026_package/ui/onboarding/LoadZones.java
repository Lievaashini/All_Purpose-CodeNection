package com.example.codenection2026_package.ui.onboarding;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.example.codenection2026_package.R;

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
        SAFE(R.string.zone_safe, R.color.status_green),
        ELEVATED(R.string.zone_elevated, R.color.status_amber),
        OVERLOAD(R.string.zone_overload, R.color.status_red);

        @StringRes
        public final int labelRes;

        @ColorRes
        public final int colorRes;

        Zone(@StringRes int labelRes, @ColorRes int colorRes) {
            this.labelRes = labelRes;
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
        SAFE(R.string.work_status_safe,
                R.string.work_warning_safe,
                R.string.dino_work_safe_speech,
                R.color.status_green),

        CAUTION(R.string.work_status_caution,
                R.string.work_warning_caution,
                R.string.dino_work_caution_speech,
                R.color.status_amber),

        RISK(R.string.work_status_risk,
                R.string.work_warning_risk,
                R.string.dino_work_risk_speech,
                R.color.status_red);

        @StringRes
        public final int statusLabelRes;

        @StringRes
        public final int warningTextRes;

        @StringRes
        public final int dinoSpeechRes;

        @ColorRes
        public final int colorRes;

        WorkBand(@StringRes int statusLabelRes,
                 @StringRes int warningTextRes,
                 @StringRes int dinoSpeechRes,
                 @ColorRes int colorRes) {
            this.statusLabelRes = statusLabelRes;
            this.warningTextRes = warningTextRes;
            this.dinoSpeechRes = dinoSpeechRes;
            this.colorRes = colorRes;
        }
    }
}
