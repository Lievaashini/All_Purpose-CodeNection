package com.example.codenection2026_package.engine;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.codenection2026_package.api.HealthConnectReader;
import com.example.codenection2026_package.data.CategoryRepository;
import com.example.codenection2026_package.data.TaskRepository;
import com.example.codenection2026_package.model.Biometrics;
import com.example.codenection2026_package.model.Task;
import com.example.codenection2026_package.ui.onboarding.OnboardingPrefs;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Turns one day's real tasks into the proposal the Rebalance sheet shows.
 *
 * <p>This is the piece that was missing: {@link CapacityTrigger} and {@link LoadShedder}
 * existed but nothing ever called them, so the triage sheet rendered a fixed demo
 * proposal. The planner is the bridge - it measures the day, asks the model about each
 * task, and hands back a list the sheet can render row for row.
 *
 * <p>Division of labour, deliberately: {@link LoadShedder} decides <em>whether</em> a task
 * moves. It never decides <em>where</em> - it has no feature describing any other day's
 * load, so it could not answer that even in principle. Picking a target day is arithmetic
 * over {@link CapacityCalculator}, and {@link Proposal#targetDate} is where it will land.
 *
 * <p>Health Connect reads block, so {@link #plan} does its work on a background thread and
 * calls back on the main one, the same shape {@link TaskRepository} uses.
 */
public final class RebalancePlanner {

    /** Mirrors the three states the sheet can paint a row in. */
    public enum Outcome {
        /** Immovable - a shift, class or exam. The model is never consulted. */
        PROTECTED,
        /** The model looked at it and said keep it where it is. */
        KEPT,
        /** The model said this one can move. */
        MOVED
    }

    /** One row of the proposal: a real task, and what the plan does with it. */
    public static final class Proposal {

        private final Task task;

        @Nullable
        private final String categoryName;

        private final Outcome outcome;

        /**
         * The day this task should move to, as "yyyy-MM-dd", or null when it stays put.
         *
         * <p>Also null for a MOVED task when no day in its deferral window has room. The sheet
         * shows those as "No lighter day in range", and Accept leaves them where they are.
         */
        @Nullable
        private final String targetDate;

        Proposal(@NonNull Task task,
                 @Nullable String categoryName,
                 @NonNull Outcome outcome,
                 @Nullable String targetDate) {
            this.task = task;
            this.categoryName = categoryName;
            this.outcome = outcome;
            this.targetDate = targetDate;
        }

        @NonNull
        public Task getTask() {
            return task;
        }

        @Nullable
        public String getCategoryName() {
            return categoryName;
        }

        @NonNull
        public Outcome getOutcome() {
            return outcome;
        }

        @Nullable
        public String getTargetDate() {
            return targetDate;
        }
    }

    /** The finished proposal for one day, plus the capacity figures that drove it. */
    public static final class Plan {

        private final double capacity;
        private final double projectedCapacity;
        private final List<Proposal> proposals;
        private final int recoveryDebtScore;
        private final boolean hasSleepReading;

        Plan(double capacity,
             double projectedCapacity,
             @NonNull List<Proposal> proposals,
             int recoveryDebtScore,
             boolean hasSleepReading) {
            this.capacity = capacity;
            this.projectedCapacity = projectedCapacity;
            this.recoveryDebtScore = recoveryDebtScore;
            this.hasSleepReading = hasSleepReading;
            this.proposals = proposals;
        }

        /** The day's measured load as it stands, 0-100+. */
        public double getCapacity() {
            return capacity;
        }

        /**
         * The recovery debt the model was given for this plan, 0-100. It is what decided how
         * many tasks were released, so the sheet shows it rather than a made-up figure.
         */
        public int getRecoveryDebtScore() {
            return recoveryDebtScore;
        }

        /**
         * False when there was no sleep reading, in which case the debt score is the neutral
         * default rather than a measurement.
         */
        public boolean hasSleepReading() {
            return hasSleepReading;
        }

        /** What the load would be once everything marked MOVED is off the day. */
        public double getProjectedCapacity() {
            return projectedCapacity;
        }

        /**
         * Percentage points this rebalance takes off the day, never negative.
         *
         * <p>This is the sheet's "Cognitive Load" headline. It is honest about the part
         * that is knowable now: how much load leaves this day. Where it lands is the
         * next step's problem.
         */
        public int getLoadReductionPercent() {
            return (int) Math.round(Math.max(0.0, capacity - projectedCapacity));
        }

        @NonNull
        public List<Proposal> getProposals() {
            return proposals;
        }

        /** True once the day is loaded enough for the model to be allowed to move anything. */
        public boolean isOverThreshold() {
            return new CapacityCalculator().hasReachedNinetyPercent(capacity);
        }

        /** How many moved tasks also have a day to go to - what Accept would change. */
        public int placedCount() {
            int placed = 0;
            for (Proposal proposal : proposals) {
                if (proposal.getOutcome() == Outcome.MOVED && proposal.getTargetDate() != null) {
                    placed++;
                }
            }
            return placed;
        }

        /** How many tasks the model actually agreed to move. */
        public int movedCount() {
            int moved = 0;
            for (Proposal proposal : proposals) {
                if (proposal.getOutcome() == Outcome.MOVED) {
                    moved++;
                }
            }
            return moved;
        }
    }

    /**
     * Errands have no slider in onboarding, unlike Study, Work and Co-curricular, so they
     * need an assumed ceiling to be measurable at all. An hour a day is a deliberately
     * mild assumption: too low and every grocery run reads as an overload.
     */
    private static final double ERRAND_HOURS_PER_WEEK = 7.0;

    /** The onboarding sliders are weekly ceilings ("h/wk"), and capacity is measured per day. */
    private static final double DAYS_PER_WEEK = 7.0;

    /** Nothing in the app asks the user how they feel yet, so mood stays neutral. */
    private static final double NEUTRAL_MOOD = 1.0;

    /** Passed where no sleep figure applies, which leaves capacity unpenalised. */
    private static final int NO_SLEEP_READING = 0;

    private static final String TAG = "RebalancePlanner";

    /** The date format every task row and DAO query in the app already uses. */
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private RebalancePlanner() {
    }

    /**
     * Measures {@code feed} and runs the model over it.
     *
     * @param isoDate the day being rebalanced, "yyyy-MM-dd" - the point the search for a
     *                target day counts forward from
     * @param feed one day's tasks, as the dashboard already loaded them
     * @param callback receives the finished plan on the main thread, or null if measuring
     *                 the day failed
     */
    public static void plan(@NonNull Context context,
                            @NonNull String isoDate,
                            @NonNull List<TaskRepository.FeedItem> feed,
                            @NonNull TaskRepository.Callback<Plan> callback) {

        final Context appContext = context.getApplicationContext();

        // Copied before leaving the main thread: the caller's list is the live feed, and
        // the dashboard is free to reload it while this runs.
        final List<TaskRepository.FeedItem> snapshot = new ArrayList<>(feed);

        IO.execute(() -> {
            Plan plan;
            try {
                plan = build(appContext, isoDate, snapshot);
            } catch (RuntimeException e) {
                // Reported as null rather than an empty plan: an empty plan reads as "nothing
                // scheduled", which would tell the user a failed measurement was a free day.
                Log.w(TAG, "Rebalance plan failed for " + isoDate, e);
                plan = null;
            }
            final Plan result = plan;
            MAIN.post(() -> callback.onResult(result));
        });
    }

    /** Seven days of measured load, Monday first, and the sleep reading behind today's. */
    public static final class WeekLoads {

        private final float[] loads;
        private final int lastNightSleepMinutes;

        WeekLoads(@NonNull float[] loads, int lastNightSleepMinutes) {
            this.loads = loads;
            this.lastNightSleepMinutes = lastNightSleepMinutes;
        }

        /** One load per requested date, in the order requested; 0 for an empty day. */
        @NonNull
        public float[] getLoads() {
            return loads.clone();
        }

        /** Last night's sleep in minutes, or 0 when Health Connect has no reading. */
        public int getLastNightSleepMinutes() {
            return lastNightSleepMinutes;
        }
    }

    /**
     * Measures every day in {@code isoDates} the same way {@link #plan} measures one.
     *
     * <p>This is what the dashboard's capacity card and weekly chart draw. They used to show a
     * fixed demo week; sharing the planner's measurement means the card and the Rebalance
     * sheet can never disagree about how heavy a day is.
     *
     * <p>Completed tasks count (planned workload), the same as in the plan, so ticking a task
     * off does not change the day's load. Only today carries the sleep penalty.
     *
     * @param isoDates days to measure, "yyyy-MM-dd"; a null entry measures as 0
     * @param callback receives the loads on the main thread, or null if measuring failed
     */
    public static void measureWeek(@NonNull Context context,
                                   @NonNull String[] isoDates,
                                   @NonNull TaskRepository.Callback<WeekLoads> callback) {

        final Context appContext = context.getApplicationContext();
        final String[] dates = isoDates.clone();

        IO.execute(() -> {
            WeekLoads measured;
            try {
                float[] loads = new float[dates.length];
                int lastNight = lastNightSleepMinutes(appContext);
                String today = LocalDate.now().format(ISO);
                for (int i = 0; i < dates.length; i++) {
                    if (dates[i] == null) {
                        continue;
                    }
                    int sleep = dates[i].equals(today) ? lastNight : NO_SLEEP_READING;
                    loads[i] = (float) measureCapacity(appContext,
                            TaskRepository.loadByDateBlocking(appContext, dates[i]), sleep);
                }
                measured = new WeekLoads(loads, lastNight);
            } catch (RuntimeException e) {
                // Null rather than a half-measured week: partial zeros would draw the week
                // lighter than it is. The dashboard keeps its last good figures instead.
                Log.w(TAG, "Measuring the week failed", e);
                measured = null;
            }
            final WeekLoads result = measured;
            MAIN.post(() -> callback.onResult(result));
        });
    }

    /**
     * The sleep figure that applies to {@code isoDate}: last night's for today, none for any
     * other day.
     *
     * <p>Last night's sleep is a fact about today. Carrying it to Thursday would let one bad
     * night inflate a day it has nothing to do with, and carrying it to last Monday would
     * rewrite history.
     */
    private static int sleepMinutesFor(@NonNull Context context, @NonNull String isoDate) {
        return isoDate.equals(LocalDate.now().format(ISO))
                ? lastNightSleepMinutes(context)
                : NO_SLEEP_READING;
    }

    @NonNull
    private static Plan build(@NonNull Context context,
                              @NonNull String isoDate,
                              @NonNull List<TaskRepository.FeedItem> feed) {

        // Read once: this is the only Health Connect call on the path, and both the
        // current and the projected figure have to be penalised identically or the
        // difference between them would be measuring sleep rather than the plan.
        int sleepMinutes = sleepMinutesFor(context, isoDate);

        double capacity = measureCapacity(context, feed, sleepMinutes);

        // How depleted the user is right now, from sleep and HRV. Unlike the sleep penalty on
        // capacity, this is not tied to the day being rebalanced: it describes the person, and
        // their current state is the best estimate of how much they can carry this week.
        int recoveryDebt = HealthConnectReader.currentRecoveryDebtScore(context);

        CapacityTrigger trigger = new CapacityTrigger();
        List<Proposal> proposals = new ArrayList<>(feed.size());
        List<TaskRepository.FeedItem> staying = new ArrayList<>(feed.size());
        List<TaskRepository.FeedItem> leaving = new ArrayList<>();

        for (TaskRepository.FeedItem item : feed) {
            Task task = item.getTask();

            // A finished task cannot be moved, but it still belongs to the day: it stays in the
            // projected load, otherwise the plan would claim credit for work already done.
            if (task.isCompleted()) {
                staying.add(item);
                continue;
            }

            if (isFixedTime(task) || isCalendarLinked(task)) {
                // Rail 1 of the model keeps fixed tasks regardless, so asking is pointless.
                // Calendar-linked tasks are kept for a harder reason: the delta sync deletes
                // any linked task it cannot find in Google Calendar on that date, so moving
                // one would not reschedule it - it would destroy it on the next sync.
                proposals.add(new Proposal(task, item.getCategoryName(), Outcome.PROTECTED, null));
                staying.add(item);
                continue;
            }

            int action = trigger.predictTaskAction(
                    capacity, recoveryDebt, task, item.getCategoryName());

            if (action == LoadShedder.MOVE) {
                leaving.add(item);
            } else {
                proposals.add(new Proposal(task, item.getCategoryName(), Outcome.KEPT, null));
                staying.add(item);
            }
        }

        // The model has said what leaves. Where each one lands is arithmetic from here.
        List<Proposal> moved = chooseTargetDays(context, isoDate, leaving);
        proposals.addAll(moved);

        // A task the search found no room for does not actually leave the day, so it still
        // counts against it. Without this the headline would promise a reduction that
        // depends on tasks going nowhere.
        for (int i = 0; i < moved.size(); i++) {
            if (moved.get(i).getTargetDate() == null) {
                staying.add(leaving.get(i));
            }
        }

        double projected = measureCapacity(context, staying, sleepMinutes);
        boolean hasSleepReading = lastNightSleepMinutes(context) > 0;
        return new Plan(capacity, projected, proposals, recoveryDebt, hasSleepReading);
    }

    // ==================================================================
    //  Choosing the day
    // ==================================================================

    /**
     * Finds each released task the emptiest day it is allowed to land on.
     *
     * <p>Deliberately not a model. The decision tree has no feature describing any other
     * day's load, so it could not answer this; measuring the candidate days directly is
     * both exact and explainable - "Thursday is at 41%, today is at 94%" is a sentence a
     * user can argue with, which a classifier's output is not.
     *
     * <p>Assignment is greedy and sequential: once a task is placed, it counts towards
     * that day's load for every task placed after it. Without that, a heavy day would
     * attract every moved task at once and simply become the new heavy day.
     */
    @NonNull
    private static List<Proposal> chooseTargetDays(@NonNull Context context,
                                                   @NonNull String isoDate,
                                                   @NonNull List<TaskRepository.FeedItem> leaving) {

        List<Proposal> placed = new ArrayList<>(leaving.size());
        if (leaving.isEmpty()) {
            return placed;
        }

        LocalDate from;
        try {
            from = LocalDate.parse(isoDate, ISO);
        } catch (DateTimeParseException e) {
            // Without a day to count from there is no window to search.
            for (TaskRepository.FeedItem item : leaving) {
                placed.add(new Proposal(item.getTask(), item.getCategoryName(), Outcome.MOVED, null));
            }
            return placed;
        }

        // Each candidate day's tasks, loaded once and then kept current as tasks are
        // assigned into it.
        Map<String, List<TaskRepository.FeedItem>> dayLoads = new HashMap<>();

        for (TaskRepository.FeedItem item : leaving) {
            String target = bestDayFor(context, from, item, dayLoads);

            if (target != null) {
                // Count it against the day it just took, so the next task sees it.
                dayLoads.get(target).add(item);
            }
            placed.add(new Proposal(item.getTask(), item.getCategoryName(), Outcome.MOVED, target));
        }
        return placed;
    }

    /**
     * The best day for one task, or null when its deferral window offers none.
     *
     * <p>"Best" is the lowest resulting capacity. A day that would itself tip over the
     * intervention threshold is rejected outright - moving an overload onto an overload
     * is not a plan - and if every candidate does that, the task keeps its MOVED verdict
     * with no destination rather than being handed somewhere harmful.
     *
     * <p>Candidate days are measured on booked hours alone, with no sleep penalty. Last
     * night's sleep is a fact about today, not about Thursday; carrying it forward would
     * inflate every future day by up to double and have the planner report no room on a
     * week that is actually wide open, purely because the user slept badly once.
     */
    @Nullable
    private static String bestDayFor(@NonNull Context context,
                                     @NonNull LocalDate from,
                                     @NonNull TaskRepository.FeedItem item,
                                     @NonNull Map<String, List<TaskRepository.FeedItem>> dayLoads) {

        CapacityCalculator calculator = new CapacityCalculator();
        String best = null;
        double bestCapacity = Double.MAX_VALUE;

        for (int offset = 1; offset <= maxDaysOut(item.getTask()); offset++) {
            String candidate = from.plusDays(offset).format(ISO);

            List<TaskRepository.FeedItem> existing = dayLoads.get(candidate);
            if (existing == null) {
                existing = new ArrayList<>(TaskRepository.loadByDateBlocking(context, candidate));
                dayLoads.put(candidate, existing);
            }

            if (clashesWithAny(item.getTask(), existing)) {
                continue;
            }

            List<TaskRepository.FeedItem> withTask = new ArrayList<>(existing);
            withTask.add(item);

            double capacity = measureCapacity(context, withTask, NO_SLEEP_READING);

            if (calculator.hasReachedNinetyPercent(capacity)) {
                continue;
            }
            if (capacity < bestCapacity) {
                bestCapacity = capacity;
                best = candidate;
            }
        }
        return best;
    }

    /**
     * How many days out the task may be pushed, from the deferral window the user set on
     * it in Add Task.
     *
     * <p>The window is in hours and the search is in whole days, so it is rounded to the
     * nearest day with a floor of one. That floor is a deliberate call: a 12-hour window
     * contains no whole day, and refusing to move a task the user explicitly marked
     * deferrable would be worse than stretching their window by half a day.
     */
    private static int maxDaysOut(@NonNull Task task) {
        return (int) Math.max(1L, Math.round(task.getDeferralHours() / 24.0));
    }

    /**
     * The day's load as a 0-100+ figure, measured the way {@link CapacityCalculator}
     * defines it: hours booked against the user's own ceilings, then weighted, then
     * adjusted for how badly they slept.
     *
     * <p>Uses planned hours, so completed tasks are included. This is the one measurement
     * behind both the dashboard and the Rebalance trigger.
     */
    private static double measureCapacity(@NonNull Context context,
                                          @NonNull List<TaskRepository.FeedItem> feed,
                                          int sleepMinutes) {

        CapacityCalculator calculator = new CapacityCalculator();

        // 1. First, total up the hours the user actually booked for today
        Map<String, Double> hoursByCategory = new HashMap<>();
        for (TaskRepository.FeedItem item : feed) {
            String category = item.getCategoryName();
            if (category == null || isRecovery(category)) {
                // Social time is the model's protective buffer, not load.
                continue;
            }
            double hours = calculator.calculatePlannedTaskHours(item.getTask());
            if (hours > 0) {
                Double running = hoursByCategory.get(category);
                hoursByCategory.put(category, running == null ? hours : running + hours);
            }
        }

        // 2. Next, calculate the load percentage across ALL tracked categories
        Map<String, Double> loads = new HashMap<>();

        // FIX: Explicitly check all 4 tracked categories so empty schedules
        // dilute the daily average properly, preventing false overloads.
        String[] trackedCategories = {
                CategoryRepository.ACADEMIC,
                CategoryRepository.WORK,
                CategoryRepository.ERRAND,
                CategoryRepository.CO_CURRICULAR
        };

        // We loop through the trackedCategories array, NOT hoursByCategory
        for (String cat : trackedCategories) {
            // If the category has no tasks today, default to 0.0 hours
            double hours = hoursByCategory.containsKey(cat) ? hoursByCategory.get(cat) : 0.0;
            double baseline = dailyBaselineHours(context, cat);
            loads.put(cat, calculator.calculateCategoryLoad(hours, baseline, NEUTRAL_MOOD));
        }

        double capacity = calculator.calculateOverallCapacity(loads);
        return applySleepPenaltyIfKnown(calculator, capacity, sleepMinutes);
    }

    /**
     * Applies the sleep penalty only when a sleep reading actually exists.
     *
     * <p>This guard matters more than it looks.
     * {@link CapacityCalculator#applySleepPenalty} reads zero minutes as "slept nothing"
     * and doubles the load, so without this every user who has no smartwatch - the exact
     * users the manual-entry dialog was built for - would sit permanently over the
     * intervention threshold and be told to cancel their day.
     */
    private static double applySleepPenaltyIfKnown(@NonNull CapacityCalculator calculator,
                                                   double capacity,
                                                   int sleepMinutes) {
        if (sleepMinutes <= 0) {
            return capacity;
        }
        // Only the sleep figure is read by applySleepPenalty; date and HRV are carried
        // by the row type but play no part in the penalty.
        Biometrics biometrics = new Biometrics(null, sleepMinutes, null);
        return calculator.applySleepPenalty(capacity, biometrics);
    }

    /** Last night's sleep in minutes, or 0 when Health Connect has nothing to say. */
    private static int lastNightSleepMinutes(@NonNull Context context) {
        try {
            double hours = HealthConnectReader.calculateTotalSleepHours(
                    HealthConnectReader.getSleepSessionsLast24Hours(context));
            return hours > 0 ? (int) Math.round(hours * 60.0) : 0;
        } catch (RuntimeException e) {
            // Permission revoked mid-session, or no provider installed at all.
            return 0;
        }
    }

    /**
     * The user's own ceiling for a category, converted from the weekly hours the
     * onboarding sliders collect into the daily figure capacity is measured in.
     */
    private static double dailyBaselineHours(@NonNull Context context, @NonNull String category) {
        final double weekly;
        if (CategoryRepository.WORK.equals(category)) {
            weekly = OnboardingPrefs.getWorkHours(context);
        } else if (CategoryRepository.CO_CURRICULAR.equals(category)) {
            weekly = OnboardingPrefs.getCocurricularHours(context);
        } else if (CategoryRepository.ERRAND.equals(category)) {
            weekly = ERRAND_HOURS_PER_WEEK;
        } else {
            // Academic, and anything filed under a category this build does not know.
            weekly = OnboardingPrefs.getStudyHours(context);
        }
        return weekly / DAYS_PER_WEEK;
    }

    private static boolean isFixedTime(@NonNull Task task) {
        return "inflexible".equalsIgnoreCase(task.getClassification());
    }

    private static boolean isCalendarLinked(@NonNull Task task) {
        return task.getCalendarEventId() != null;
    }

    /**
     * True when the task would sit on top of something already booked that day.
     *
     * <p>A task keeps its time of day when it moves, so a day is only a real option if that
     * slot is free there. Without this, "Moves to Thu" could land a reading block squarely
     * on a Thursday shift. Finished tasks do not block a slot.
     *
     * <p>A time that cannot be parsed is treated as no clash: an unreadable row should not
     * veto every day it appears on.
     */
    private static boolean clashesWithAny(@NonNull Task moving,
                                          @NonNull List<TaskRepository.FeedItem> booked) {
        int[] window = minutesOf(moving);
        if (window == null) {
            return false;
        }
        for (TaskRepository.FeedItem item : booked) {
            Task other = item.getTask();
            if (other.isCompleted()) {
                continue;
            }
            int[] otherWindow = minutesOf(other);
            if (otherWindow != null && window[0] < otherWindow[1] && otherWindow[0] < window[1]) {
                return true;
            }
        }
        return false;
    }

    /**
     * A task's start and end as minutes since midnight, with a past-midnight end pushed
     * onto the next day so the comparison still holds, or null if either time is missing.
     */
    @Nullable
    private static int[] minutesOf(@NonNull Task task) {
        try {
            int start = LocalTime.parse(task.getStartTime()).toSecondOfDay() / 60;
            int end = LocalTime.parse(task.getEndTime()).toSecondOfDay() / 60;
            if (end <= start) {
                end += 24 * 60;
            }
            return new int[] {start, end};
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean isRecovery(@NonNull String category) {
        return CategoryRepository.SOCIAL.equalsIgnoreCase(category.trim());
    }
}
