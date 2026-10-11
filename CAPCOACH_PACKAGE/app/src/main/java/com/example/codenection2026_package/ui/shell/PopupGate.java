package com.example.codenection2026_package.ui.shell;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.codenection2026_package.data.TaskRepository;
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
 *       the feast follows it (see {@link #weeklyPopup}): the user collects the apples
 *       first and only then feeds the Dino, which is the whole point of the order.</li>
 *   <li><b>Nothing to show, no pop-up.</b> A due pop-up with an empty tray is skipped rather
 *       than opened - see {@link #resolveAppOpenPopup}.</li>
 * </ul>
 *
 * <p><b>Why prefs and not a background alarm.</b> The pop-ups are not allowed to interrupt
 * the user at midnight or mid-task - they belong to the moment the app is opened - so
 * "has the user opened the app today?" is the only question this class has to answer. A
 * stored date answers it exactly, survives a reboot, and needs no scheduler, no permission
 * and no notification channel.
 *
 * <p><b>When the day is marked as seen.</b> Not when the decision is made, but when a pop-up
 * actually reaches the screen ({@link #markShown}, called by the two screens themselves). The
 * decision takes a database read, and marking it early would spend the day's pop-up during
 * those few milliseconds: a user who moved on to another screen in that window would lose the
 * morning harvest, and on a Monday the week's only feast. Written on display, the marker
 * cannot leak a pop-up, and a drop costs nothing - the next arrival at the dashboard asks
 * again.
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
    //  What the app should open on
    // ==================================================================

    /**
     * Receives the screen to open on. Null means "nothing to show".
     *
     * <p>Invoked on the main thread, except in the common case where nothing is due: that is
     * answered from prefs on the spot, on the calling thread - main thread as well, since both
     * callers are the activity starting up or the dashboard resuming.
     */
    public interface Callback {

        /**
         * @param popup a ready-made harvest or feast screen, or null when the day is owed
         *              nothing and the user should go straight to the dashboard
         */
        void onPopup(@Nullable Fragment popup);
    }

    /**
     * Works out which pop-up the user is owed right now, handing the screen to
     * {@code callback} - or null when there is nothing worth showing.
     *
     * <p>Safe to call from any screen the app may safely interrupt; it answers from the
     * stored markers alone when nothing is due, so the everyday case costs one prefs read.
     * The caller may still drop the screen - the user has moved on, see
     * {@link ScreenNav#showDuePopup} - and nothing is lost by that, because the day is only
     * marked as seen once a pop-up reaches the screen ({@link #markShown}).
     *
     * <p><b>Why it is asynchronous.</b> A pop-up with an empty tray is worse than no pop-up:
     * a harvest screen showing a bare tree and "no apples today" on the morning after
     * onboarding reads as a broken reward rather than a greeting. Whether anything is there to
     * collect is a question only the task table can answer, and Room refuses to be queried on
     * the main thread, so the screen is handed over once the count comes back.
     *
     * <p><b>The gate: an empty morning opens neither screen.</b> The harvest is worth opening
     * when apples grew overnight, or - on a Monday - when the week's basket holds something,
     * because that morning the harvest is where the basket about to be feasted is on show.
     * With both empty there is nothing to collect and nothing to feast, so nothing opens and
     * <em>no marker is written</em>: nothing is spent, and a later open that day decides
     * again on the same facts.
     *
     * <p><b>Daily is tested first.</b> On Monday both are due, and the harvest has to win: the
     * feast is about the apples this screen is handing over.
     */
    public static void resolveAppOpenPopup(@NonNull Context context, @NonNull Callback callback) {
        final Context appContext = context.getApplicationContext();

        final boolean dailyDue = isDailyDue(appContext);
        final boolean weeklyDue = isWeeklyDue(appContext);

        if (!dailyDue && !weeklyDue) {
            callback.onPopup(null);
            return;
        }

        // Yesterday's crop settles most mornings on its own: any crop at all is something to
        // collect, and it also proves the week's basket is not empty, because yesterday sits
        // inside that week.
        TaskRepository.getDailyAppleCount(appContext, yesterdayIso(), grownOvernight -> {
            if (grownOvernight != null && grownOvernight > 0) {
                callback.onPopup(dailyDue ? new DailyHarvestFragment() : weeklyPopup(appContext));
                return;
            }

            // Nothing grew overnight, so the only thing left that could make a Monday morning
            // worth opening is the basket the feast is about to serve - the harvest is where
            // that basket's meter lives. Read it only when a Monday is what is at stake:
            // Tuesday through Sunday the basket's week contains today, so opening the harvest
            // on it would mean the first task ticked off that morning fills the basket and
            // re-opens a harvest with nothing in it - the empty screen this gate exists to
            // avoid, arriving at the worst possible moment.
            if (!weeklyDue) {
                callback.onPopup(null);
                return;
            }

            final String[] basketWeek = weekEndingYesterday();
            TaskRepository.getWeeklyAppleCount(appContext, basketWeek[0], basketWeek[1], basket -> {
                if (basket == null || basket == 0) {
                    callback.onPopup(null);
                    return;
                }
                callback.onPopup(dailyDue ? new DailyHarvestFragment() : weeklyPopup(appContext));
            });
        });
    }

    /**
     * The weekly feast while it is genuinely owed - the second half of Monday's queue, called
     * when the user leaves the daily harvest, and the direct answer when Monday's harvest has
     * already been seen.
     *
     * <p>Answered from the stored marker alone: both callers only reach it having already
     * loaded the very same week's basket, so the feast it hands over has something in it.
     *
     * @return a new {@link FeastFragment}, or null when today is not a Monday still waiting
     *         for its feast (every other day of the week, and any Monday already served, so
     *         the caller falls through to the dashboard)
     */
    @Nullable
    public static Fragment weeklyPopup(@NonNull Context context) {
        return isWeeklyDue(context) ? new FeastFragment() : null;
    }

    /**
     * Records that a pop-up has actually reached the screen.
     *
     * <p>Called by the two screens themselves as they are created, which is what makes the
     * marker mean what its name says. Writing it earlier - at the moment the day's decision was
     * made - would spend the pop-up during the few milliseconds the counts take, so a user who
     * moved on to another screen in that window would lose it. For the harvest that costs a
     * morning; for the feast it would cost the week, since Monday is the only day it fires.
     *
     * <p>A screen the user opened from the dashboard's buttons marks the same way, which is
     * the point: they have seen today's harvest, so there is no second one owed.
     */
    public static void markShown(@NonNull Context context, @NonNull Fragment popup) {
        if (popup instanceof DailyHarvestFragment) {
            markDailyShown(context);
        } else if (popup instanceof FeastFragment) {
            markWeeklyShown(context);
        }
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
     * Yesterday's date, "yyyy-MM-dd" - the day whose crop the morning harvest counts.
     */
    @NonNull
    private static String yesterdayIso() {
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        return iso(yesterday);
    }

    /**
     * Monday and Sunday of the week yesterday belongs to, as {@code {start, end}}.
     *
     * <p>This is the same anchoring the two screens use: on a Monday morning yesterday is
     * Sunday, so the week in play is the one that has just ended rather than an empty new one.
     * It is duplicated here - deliberately, and with a sigh - because this class needs the
     * answer on the main thread before either screen exists, and it is the gate that decides
     * whether a basket worth showing even exists. If the three copies ever disagree, the
     * symptom is a harvest that fails to open for a basket it would have shown.
     */
    @NonNull
    private static String[] weekEndingYesterday() {
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);

        Calendar monday = (Calendar) yesterday.clone();
        monday.add(Calendar.DAY_OF_YEAR, -((monday.get(Calendar.DAY_OF_WEEK) + 5) % 7));

        Calendar sunday = (Calendar) monday.clone();
        sunday.add(Calendar.DAY_OF_YEAR, 6);

        return new String[]{iso(monday), iso(sunday)};
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
