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
        return dailyDue(Calendar.getInstance(), prefs(context).getString(KEY_DAILY_SHOWN, NO_DATE));
    }

    /**
     * True on a Monday whose feast has not been opened yet.
     *
     * <p>Deliberately Monday-only rather than "any day this week": a feast that could still
     * fire on Thursday would turn a missed Monday into a pop-up out of nowhere. The
     * dashboard's Weekly Pop-up button remains the way to feast late.
     */
    public static boolean isWeeklyDue(@NonNull Context context) {
        return weeklyDue(Calendar.getInstance(),
                prefs(context).getString(KEY_WEEKLY_SHOWN, NO_DATE));
    }

    /**
     * The daily rule on its own: today is owed a harvest unless today's date is the one
     * already recorded.
     *
     * <p>Separated from the stored read so the rule can be pinned by a JVM test
     * ({@code app/src/test/.../PopupGateTest.java}) instead of only on a device whose clock
     * cannot be moved.
     */
    static boolean dailyDue(@NonNull Calendar today, @Nullable String lastShownDate) {
        return !iso(today).equals(lastShownDate);
    }

    /**
     * The weekly rule on its own: Monday is owed a feast unless this week's Monday is the one
     * already recorded.
     *
     * <p>Keyed on the week's Monday, so "already feasted" expires by itself when the next
     * Monday arrives - there is no week counter to keep in step.
     */
    static boolean weeklyDue(@NonNull Calendar today, @Nullable String lastShownMonday) {
        if (today.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            return false;
        }
        return !weekStartMonday(today).equals(lastShownMonday);
    }

    // ==================================================================
    //  Remembering
    // ==================================================================

    /** Records that today's harvest has been shown. */
    public static void markDailyShown(@NonNull Context context) {
        prefs(context).edit().putString(KEY_DAILY_SHOWN, iso(Calendar.getInstance())).apply();
    }

    /**
     * Records that this week's feast has been shown.
     *
     * <p>Keyed on the week's Monday rather than on today, so the marker cannot accidentally
     * match a later day: next Monday is a different date and the feast is due again.
     */
    public static void markWeeklyShown(@NonNull Context context) {
        prefs(context).edit()
                .putString(KEY_WEEKLY_SHOWN, weekStartMonday(Calendar.getInstance()))
                .apply();
    }

    // ==================================================================
    //  Dates
    // ==================================================================

    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /**
     * Monday of the week {@code today} falls in, as "yyyy-MM-dd".
     *
     * <p>{@code Calendar.MONDAY} is 2 and Sunday is 1, so the usual
     * {@code (DAY_OF_WEEK + 5) % 7} turns Monday into 0 and Sunday into 6 - the same
     * week arithmetic the harvest and feast screens use.
     */
    @NonNull
    static String weekStartMonday(@NonNull Calendar today) {
        Calendar monday = (Calendar) today.clone();
        monday.add(Calendar.DAY_OF_YEAR, -((monday.get(Calendar.DAY_OF_WEEK) + 5) % 7));
        return iso(monday);
    }

    /** The day's date as "yyyy-MM-dd", the format every date key in the app uses. */
    @NonNull
    static String iso(@NonNull Calendar day) {
        return new SimpleDateFormat(DATE_PATTERN, Locale.US).format(day.getTime());
    }
}
