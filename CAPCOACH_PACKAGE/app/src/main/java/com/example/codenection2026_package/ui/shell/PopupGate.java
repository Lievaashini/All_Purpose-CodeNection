package com.example.codenection2026_package.ui.shell;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.codenection2026_package.ui.harvest.DailyHarvestFragment;
import com.example.codenection2026_package.ui.harvest.FeastFragment;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * Decides which reward pop-up the app owes the user when it opens, and remembers that it
 * was shown.
 *
 * <p><b>The rule.</b>
 * <ul>
 *   <li><b>Daily harvest</b> - once per calendar day, on the first open of that day.</li>
 *   <li><b>Weekly feast</b> - once per week, on the first open of that week's Monday.</li>
 *   <li><b>Daily outranks weekly.</b> Monday owes both, so Monday opens on the harvest and
 *       the feast follows it (see {@link #claimWeeklyPopup}): the user collects the apples
 *       first and only then feeds the Dino, which is the whole point of the order.</li>
 * </ul>
 *
 * <p><b>Why prefs and not a background alarm.</b> The pop-ups are not allowed to interrupt
 * the user at midnight or mid-task - they belong to the moment the app is opened - so
 * "has the user opened the app today?" is the only question this class has to answer. A
 * stored date answers it exactly, survives a reboot, and needs no scheduler, no permission
 * and no notification channel.
 *
 * <p><b>Claiming.</b> Reading and remembering are one step ({@code claim...}) on purpose. A
 * marker written when the screen is handed back rather than when it is opened would re-open
 * the same pop-up after a crash, and the user would see the same morning twice. The
 * trade-off is deliberate: if the fragment is never actually committed, the day's pop-up is
 * spent - a missed pop-up is cheaper than a nagging one.
 *
 * <p>State lives in {@code GamificationPrefs}, the same store the two screens already use
 * for "already harvested" and "already eaten", so one file holds the whole reward loop.
 *
 * <p>NEW FILE - additive. The dashboard's Daily Pop-up / Weekly Pop-up buttons are untouched
 * and still open both screens on demand at any time; this file only adds the automatic
 * trigger on top of them.
 */
public final class PopupGate {

    /** Same store the two pop-up screens already write their collect / eat state into. */
    private static final String PREFS = "GamificationPrefs";

    /** "yyyy-MM-dd" of the last day the daily harvest was opened automatically. */
    private static final String KEY_DAILY_SHOWN = "popup_daily_last_shown";

    /** "yyyy-MM-dd" of the Monday whose weekly feast was opened automatically. */
    private static final String KEY_WEEKLY_SHOWN = "popup_weekly_last_shown";

    /** Never matches a date, so "unset" and "shown on some other day" read the same way. */
    private static final String NO_DATE = "";

    /** The date format used everywhere else for task dates, so keys stay comparable. */
    private static final String DATE_PATTERN = "yyyy-MM-dd";

    private PopupGate() {
    }

    // ==================================================================
    //  Claiming - read what is due, then remember it was shown
    // ==================================================================

    /**
     * The pop-up the app should open with, or null when nothing is due and the user goes
     * straight to the dashboard.
     *
     * <p>Call once per app open, after the onboarding check - a user who has not finished
     * setup has no apples, so there is nothing to harvest or feast yet.
     *
     * <p>Daily is tested first: on Monday both are due and the harvest has to win, because
     * the feast is about the apples this screen is holding.
     */
    @Nullable
    public static Fragment claimPopupForAppOpen(@NonNull Context context) {
        if (isDailyDue(context)) {
            markDailyShown(context);
            return new DailyHarvestFragment();
        }
        if (isWeeklyDue(context)) {
            markWeeklyShown(context);
            return new FeastFragment();
        }
        return null;
    }

    /**
     * The weekly feast, but only while it is genuinely owed - the second half of Monday's
     * queue, called when the user leaves the daily harvest.
     *
     * @return a new {@link FeastFragment}, or null when today is not a Monday still waiting
     *         for its feast (every other day of the week, and any Monday already served, so
     *         the caller falls through to the dashboard)
     */
    @Nullable
    public static Fragment claimWeeklyPopup(@NonNull Context context) {
        if (!isWeeklyDue(context)) {
            return null;
        }
        markWeeklyShown(context);
        return new FeastFragment();
    }

    // ==================================================================
    //  What is due
    // ==================================================================

    /** True until the daily harvest has been opened once on the current calendar day. */
    public static boolean isDailyDue(@NonNull Context context) {
        return !today().equals(prefs(context).getString(KEY_DAILY_SHOWN, NO_DATE));
    }

    /**
     * True on a Monday whose feast has not been opened yet.
     *
     * <p>Deliberately Monday-only rather than "any day this week": a feast that could still
     * fire on Thursday would turn a missed Monday into a pop-up out of nowhere. The
     * dashboard's Weekly Pop-up button remains the way to feast late.
     */
    public static boolean isWeeklyDue(@NonNull Context context) {
        if (!isMonday()) {
            return false;
        }
        return !weekStartMonday().equals(prefs(context).getString(KEY_WEEKLY_SHOWN, NO_DATE));
    }

    // ==================================================================
    //  Remembering
    // ==================================================================

    /** Records that today's harvest has been shown. */
    public static void markDailyShown(@NonNull Context context) {
        prefs(context).edit().putString(KEY_DAILY_SHOWN, today()).apply();
    }

    /**
     * Records that this week's feast has been shown.
     *
     * <p>Keyed on the week's Monday rather than on today, so the marker cannot accidentally
     * match a later day: next Monday is a different date and the feast is due again.
     */
    public static void markWeeklyShown(@NonNull Context context) {
        prefs(context).edit().putString(KEY_WEEKLY_SHOWN, weekStartMonday()).apply();
    }

    // ==================================================================
    //  Dates
    // ==================================================================

    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @NonNull
    private static String today() {
        return iso(Calendar.getInstance());
    }

    /**
     * Monday of the week today falls in, as "yyyy-MM-dd".
     *
     * <p>{@code Calendar.MONDAY} is 2 and Sunday is 1, so the usual
     * {@code (DAY_OF_WEEK + 5) % 7} turns Monday into 0 and Sunday into 6 - the same
     * week arithmetic the harvest and feast screens use.
     */
    @NonNull
    private static String weekStartMonday() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7));
        return iso(cal);
    }

    private static boolean isMonday() {
        return Calendar.getInstance().get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY;
    }

    @NonNull
    private static String iso(@NonNull Calendar cal) {
        return new SimpleDateFormat(DATE_PATTERN, Locale.US).format(cal.getTime());
    }
}
