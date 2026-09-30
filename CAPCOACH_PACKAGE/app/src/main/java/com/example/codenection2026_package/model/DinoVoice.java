package com.example.codenection2026_package.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.PluralsRes;
import androidx.annotation.StringRes;

import com.example.codenection2026_package.R;

/**
 * Every line the Dino says, resolved for the user's coaching tone.
 *
 * <p>The Dino speaks in six places - the Screen 1 greeting, the Screen 2 work-cap guard, the
 * Daily Harvest bubble, the Weekly Feast bubbles, the Feast idle chatter and the Add Task
 * line - and before this existed five of them were tone-agnostic, so a user on the Plain
 * tone still got "WHOAAAA! ALL OF THEM AT ONCE?!". This class is the single place that maps
 * (slot, tone) to a string, so no screen has to know how the tone is stored or spell out a
 * three-way switch of its own.
 *
 * <p>The copy itself lives in the three per-tone resource files {@code dino_hype.xml},
 * {@code dino_chill.xml} and {@code dino_plain.xml}. Each declares the same slot names with
 * a different suffix, so a slot is either present in all three tones or in none, and
 * rewriting one character's voice means editing one file.
 *
 * <p><b>Why an enum rather than a suffix built at runtime:</b> {@code getIdentifier()} would
 * let a caller assemble {@code "dino_work_safe_" + tone} and look it up by name, but it is a
 * reflective lookup that defeats resource shrinking and fails at runtime instead of compile
 * time when a slot is misspelled or missing from one tone. Constants keep that a build error.
 *
 * <p>Tone is a presentation preference only - see {@link ToneType} - so nothing here touches
 * scheduling or the capacity model.
 */
public final class DinoVoice {

    /**
     * A line with a fixed set of variants: one per tone.
     *
     * <p>Slots whose text takes arguments are still ordinary constants; the caller passes the
     * arguments through {@code getString(...)} exactly as it did before. The two documented
     * argument shapes are noted on the constants that use them.
     */
    public enum Quote {

        /** Screen 1, the greeting shown as soon as a tone is chosen. */
        ONBOARDING(R.string.dino_onboarding_hype,
                R.string.dino_onboarding_chill,
                R.string.dino_onboarding_plain),

        /** Screen 2 work-cap guard, safe band. Takes {@code %1$d} weekly hours. */
        WORK_SAFE(R.string.dino_work_safe_hype,
                R.string.dino_work_safe_chill,
                R.string.dino_work_safe_plain),

        /** Screen 2 work-cap guard, caution band. */
        WORK_CAUTION(R.string.dino_work_caution_hype,
                R.string.dino_work_caution_chill,
                R.string.dino_work_caution_plain),

        /** Screen 2 work-cap guard, burnout-risk band. */
        WORK_RISK(R.string.dino_work_risk_hype,
                R.string.dino_work_risk_chill,
                R.string.dino_work_risk_plain),

        /** Weekly Feast, nothing eaten yet. */
        FEAST_HUNGRY(R.string.dino_feast_hungry_hype,
                R.string.dino_feast_hungry_chill,
                R.string.dino_feast_hungry_plain),

        /** Weekly Feast, everything fed at once. */
        FEAST_ALL(R.string.dino_feast_all_hype,
                R.string.dino_feast_all_chill,
                R.string.dino_feast_all_plain),

        /** Weekly Feast, after a one-by-one feed. */
        FEAST_HUG(R.string.dino_feast_hug_hype,
                R.string.dino_feast_hug_chill,
                R.string.dino_feast_hug_plain),

        /** Weekly Feast, complete. */
        FEAST_DONE(R.string.dino_feast_done_hype,
                R.string.dino_feast_done_chill,
                R.string.dino_feast_done_plain),

        /** Add Task sheet, the locked-shift line. */
        ADDTASK_GUARD(R.string.dino_addtask_guard_hype,
                R.string.dino_addtask_guard_chill,
                R.string.dino_addtask_guard_plain),

        /**
         * Add Task sheet, the ML-scheduler line.
         * Takes {@code %1$s} the deferral window and {@code %2$s} the priority label.
         */
        ADDTASK_SCHED(R.string.dino_addtask_sched_hype,
                R.string.dino_addtask_sched_chill,
                R.string.dino_addtask_sched_plain);

        @StringRes
        private final int hype;
        @StringRes
        private final int chill;
        @StringRes
        private final int plain;

        Quote(@StringRes int hype, @StringRes int chill, @StringRes int plain) {
            this.hype = hype;
            this.chill = chill;
            this.plain = plain;
        }

        /** The wording for {@code tone}; Hype for a null or unknown tone. */
        @StringRes
        public int pick(@Nullable ToneType tone) {
            if (tone == null) {
                return hype;
            }
            switch (tone) {
                case CHILL:
                    return chill;
                case PLAIN:
                    return plain;
                case HYPE:
                default:
                    return hype;
            }
        }
    }

    private static final int[] FEAST_QUOTES_HYPE = {
            R.string.dino_feast_quote_1_hype,
            R.string.dino_feast_quote_2_hype,
            R.string.dino_feast_quote_3_hype,
            R.string.dino_feast_quote_4_hype,
            R.string.dino_feast_quote_5_hype
    };

    private static final int[] FEAST_QUOTES_CHILL = {
            R.string.dino_feast_quote_1_chill,
            R.string.dino_feast_quote_2_chill,
            R.string.dino_feast_quote_3_chill,
            R.string.dino_feast_quote_4_chill,
            R.string.dino_feast_quote_5_chill
    };

    private static final int[] FEAST_QUOTES_PLAIN = {
            R.string.dino_feast_quote_1_plain,
            R.string.dino_feast_quote_2_plain,
            R.string.dino_feast_quote_3_plain,
            R.string.dino_feast_quote_4_plain,
            R.string.dino_feast_quote_5_plain
    };

    private DinoVoice() {
    }

    /**
     * The Daily Harvest bubble, which is a plural because a one-apple day must not read
     * "1 apples". Resolved separately from {@link Quote} because a plurals resource is a
     * different type from a string resource and cannot share the same constant.
     */
    @PluralsRes
    public static int harvestApples(@Nullable ToneType tone) {
        if (tone == null) {
            return R.plurals.dino_harvest_apples_hype;
        }
        switch (tone) {
            case CHILL:
                return R.plurals.dino_harvest_apples_chill;
            case PLAIN:
                return R.plurals.dino_harvest_apples_plain;
            case HYPE:
            default:
                return R.plurals.dino_harvest_apples_hype;
        }
    }

    /**
     * The five idle Feast lines for {@code tone}, for the caller to pick from at random.
     *
     * <p>Returns the shared array rather than a copy: callers only read it.
     */
    @NonNull
    public static int[] feastQuotes(@Nullable ToneType tone) {
        if (tone == null) {
            return FEAST_QUOTES_HYPE;
        }
        switch (tone) {
            case CHILL:
                return FEAST_QUOTES_CHILL;
            case PLAIN:
                return FEAST_QUOTES_PLAIN;
            case HYPE:
            default:
                return FEAST_QUOTES_HYPE;
        }
    }
}
