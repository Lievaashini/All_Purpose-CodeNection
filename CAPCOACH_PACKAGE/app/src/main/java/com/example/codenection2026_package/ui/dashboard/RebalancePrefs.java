package com.example.codenection2026_package.ui.dashboard;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Remembers the day the Rebalance sheet last offered itself on a return to the dashboard.
 *
 * <p><b>What the marker is for.</b> The dashboard may open the sheet on its own when the user
 * comes back to it and today has since crossed the intervention threshold - the case that
 * matters is sleep logged on Biometrics, which changes today's capacity without any task being
 * added or edited. That is an unprompted screen appearing over the user's own schedule, so it
 * happens at most once a day: "Not now" has to mean not again today.
 *
 * <p><b>What it deliberately does not gate.</b> The task-arrival triggers - a row added, a row
 * edited, a calendar sync finishing, the demo injector - do not read this marker. Adding a task
 * is a fresh act by the user, and offering after it is a response to that act rather than the
 * app repeating itself, so a day can both open the sheet on arrival and offer again when a new
 * task lands. This file rations the app's own initiative, not the user's own button: the
 * Rebalance button on the dashboard is untouched and still opens the sheet on demand, any number
 * of times.
 *
 * <p><b>Written when the sheet opens, never when it is decided.</b> The decision takes two
 * database reads, and a day that turns out to be under the threshold, or to have nothing the
 * model can move, must not be charged for an offer that never appeared. The date is written from
 * the offer's own callback, at the moment the sheet reaches the screen, which is the same
 * principle the reward pop-ups use: a marker means what its name says.
 *
 * <p><b>Why prefs.</b> The question is "has the sheet already appeared today?", and a stored date
 * answers it across a reboot with no schema, no scheduler and no notification channel. The store
 * is the one the reward loop already writes its "shown today" markers into, so the day's
 * self-opening screens are all recorded in one file; the key is unique to this feature, so the
 * two cannot collide.
 *
 * <p>NEW FILE - additive. Nothing else reads or writes these two keys.
 */
public final class RebalancePrefs {

    /** The same store the two pop-up screens already write their collect / eat state into. */
    private static final String PREFS = "GamificationPrefs";

    /**
     * "yyyy-MM-dd" of the last day the sheet opened by itself on a return to the dashboard.
     *
     * <p>Named for the feature rather than for a screen: this is the automatic offer's own
     * marker, and the Rebalance button never touches it.
     */
    private static final String KEY_AUTO_OFFERED = "rebalance_auto_offered";

    /** Never matches a date, so "unset" and "offered on some other day" read the same way. */
    private static final String NO_DATE = "";

    /** The date format used everywhere else for task dates, so the value stays comparable. */
    private static final String DATE_PATTERN = "yyyy-MM-dd";

    private RebalancePrefs() {
    }

    /**
     * True once the sheet has already opened by itself today, in which case the returning
     * dashboard stays out of the way and offers nothing until tomorrow.
     */
    public static boolean wasOfferedToday(@NonNull Context context) {
        return today().equals(prefs(context).getString(KEY_AUTO_OFFERED, NO_DATE));
    }

    /**
     * Records that the sheet opened by itself just now, which spends today's one offer.
     *
     * <p>Called from the offer itself, as the sheet is handed to the screen - see the class
     * comment for why it is not written any earlier.
     */
    public static void markOfferedToday(@NonNull Context context) {
        prefs(context).edit().putString(KEY_AUTO_OFFERED, today()).apply();
    }

    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Today's date on the device calendar, "yyyy-MM-dd". */
    @NonNull
    private static String today() {
        return new SimpleDateFormat(DATE_PATTERN, Locale.US).format(new Date());
    }
}
