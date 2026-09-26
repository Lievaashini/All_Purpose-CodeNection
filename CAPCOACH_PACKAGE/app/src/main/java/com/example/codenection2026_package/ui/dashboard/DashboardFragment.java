package com.example.codenection2026_package.ui.dashboard;

import android.content.Context;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.example.codenection2026_package.R;
import com.example.codenection2026_package.model.Task;
import com.example.codenection2026_package.ui.addtask.AddTaskSheetFragment;
import com.example.codenection2026_package.ui.onboarding.OnboardingPrefs;
import com.example.codenection2026_package.ui.onboarding.ThemeController;
import com.example.codenection2026_package.ui.shell.ScreenNav;
import com.example.codenection2026_package.ui.widget.LoadChartView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * SCREEN 3 - DASHBOARD (port of Prototype/dashboard.html).
 *
 * <p>One scrolling feed plus four fixed pieces of chrome: the header, the rebalance
 * toast, the floating Add Task button, and the shared bottom navigation bar.
 *
 * <p><b>DATA ACCESS IS DELIBERATELY ISOLATED.</b> The Room database class is being
 * written by another agent, so this screen talks to {@link TaskSource} and nothing
 * else. The default implementation is a stub: it returns no tasks, which is exactly
 * the state a first launch is in, so the screen renders its empty state honestly
 * rather than pretending to have data it cannot load. When AppDatabase lands, swap
 * the stub for a real implementation and nothing else in this class changes.
 *
 * <p>NEW FILE - additive. Nothing existing is modified.
 */
public class DashboardFragment extends Fragment {

    /**
     * Swap-in point for AppDatabase once it lands.
     *
     * <p>Room wiring goes here: the real implementation should be
     * {@code AppDatabase.getInstance(context).taskDao().getAll()} for {@link #loadAll()}
     * and {@code taskDao().insert(task)} for {@link #save(Task)}. Both DAO calls are
     * blocking, so the real implementation must run them off the main thread.
     */
    private interface TaskSource {
        List<Task> loadAll();

        long save(Task task);
    }

    /**
     * Placeholder source. Returns an empty feed so the dashboard shows its
     * "no tasks yet" empty state on a fresh install.
     */
    private static final TaskSource EMPTY_SOURCE = new TaskSource() {
        @Override
        public List<Task> loadAll() {
            return Collections.emptyList();
        }

        @Override
        public long save(Task task) {
            // TODO Room wiring: replace with AppDatabase.getInstance(context).taskDao().insert(task).
            return -1L;
        }
    };

    /** Sleep figure shown in the telemetry line. The Room layer will supply the real one. */
    private static final String DEMO_SLEEP_HOURS = "7.8h";

    // Week strip: the prototype shows Mon 14 through Sun 20 with today on Wednesday.
    private static final String[] DAY_LETTERS = {"M", "T", "W", "T", "F", "S", "S"};
    private static final String[] DAY_NAMES = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
    private static final int[] DAY_NUMBERS = {14, 15, 16, 17, 18, 19, 20};

    /**
     * The prototype's seven charted loads, in day order. The weekly chart draws all
     * seven; the capacity card reads whichever one the selected day points at.
     */
    private static final float[] WEEK_LOADS = {65f, 50f, 40f, 75f, 88f, 25f, 20f};

    private static final int TODAY_INDEX = 2;
    private static final int CRASH_CEILING_PERCENT = 90;
    private static final long TOAST_VISIBLE_MS = 4200L;

    private TaskSource taskSource = EMPTY_SOURCE;

    private TextView stateLabel;
    private View stateDot;
    private LinearLayout statePill;
    private TextView loadPercent;
    private TextView headline;
    private TextView telemetry;
    private TextView capLabel;
    private TextView weekLabel;
    private TextView currentDayTitle;
    private TextView taskCounter;
    private TextView emptyState;
    private LinearLayout weekStrip;
    private LinearLayout tasksList;
    private LoadChartView loadChart;
    private View rebalanceToast;

    /** 0 = Mon ... 6 = Sun. The prototype starts on Wednesday. */
    private int selectedDay = TODAY_INDEX;

    /** Load shown in the capacity card, in percent. Tracks the selected day. */
    private int selectedLoad = (int) WEEK_LOADS[TODAY_INDEX];

    private final List<View> taskRows = new ArrayList<>();
    private final Set<View> doneRows = new HashSet<>();

    private final Runnable hideToast = this::dismissRebalanceToast;

    public DashboardFragment() {
        super(R.layout.fragment_dashboard);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        bindViews(view);

        // Shared shell: bottom nav selection, theme toggle, week strip, chart.
        ScreenNav.bindNav(this, view, ScreenNav.Tab.HOME);
        ThemeController.bind(view, R.id.themeToggleButton, R.id.themeToggleIcon);

        // The header brand mark is an animated GIF. The layout keeps android:src only so
        // the Studio preview renders something; that path decodes a single frame, so the
        // sprite is loaded through Glide here to actually animate.
        ImageView headerDino = view.findViewById(R.id.headerDino);
        if (headerDino != null) {
            Glide.with(this).load(R.drawable.dino_happy).into(headerDino);
        }

        setupWeekStrip();
        setupLoadChart();
        renderCapLabel();
        renderWeekLabel();
        renderCapacityCard();
        renderDays();

        setupTaskFeed();
        setupFilters();
        setupActions(view);
    }

    @Override
    public void onDestroyView() {
        if (rebalanceToast != null) {
            rebalanceToast.removeCallbacks(hideToast);
        }
        taskRows.clear();
        doneRows.clear();
        stateLabel = null;
        stateDot = null;
        statePill = null;
        loadPercent = null;
        headline = null;
        telemetry = null;
        capLabel = null;
        weekLabel = null;
        currentDayTitle = null;
        taskCounter = null;
        emptyState = null;
        weekStrip = null;
        tasksList = null;
        loadChart = null;
        rebalanceToast = null;
        super.onDestroyView();
    }

    // ==================================================================
    // Wiring
    // ==================================================================

    private void bindViews(@NonNull View view) {
        stateLabel = view.findViewById(R.id.stateLabel);
        stateDot = view.findViewById(R.id.stateDot);
        statePill = view.findViewById(R.id.statePill);
        loadPercent = view.findViewById(R.id.loadPercent);
        headline = view.findViewById(R.id.headline);
        telemetry = view.findViewById(R.id.telemetry);
        capLabel = view.findViewById(R.id.capLabel);
        weekLabel = view.findViewById(R.id.weekLabel);
        currentDayTitle = view.findViewById(R.id.currentDayTitle);
        taskCounter = view.findViewById(R.id.taskCounter);
        emptyState = view.findViewById(R.id.emptyState);
        weekStrip = view.findViewById(R.id.weekStrip);
        tasksList = view.findViewById(R.id.tasksList);
        loadChart = view.findViewById(R.id.loadChart);
        rebalanceToast = view.findViewById(R.id.rebalanceToast);
    }

    /**
     * Fills @id/weekStrip with seven copies of item_day_pill.xml and wires each one.
     * The prototype's selectDay() repaints every pill on every tap, so this does too.
     */
    private void setupWeekStrip() {
        if (weekStrip == null) {
            return;
        }
        weekStrip.removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (int i = 0; i < DAY_NUMBERS.length; i++) {
            View pill = inflater.inflate(R.layout.item_day_pill, weekStrip, false);
            final int index = i;
            pill.setOnClickListener(v -> selectDay(index));
            weekStrip.addView(pill);
        }
    }

    /**
     * Hands the seven daily loads to the chart.
     *
     * <p>LoadChartView draws straight to a Canvas because XML cannot express a
     * percentage height against a value range. Index 2 is today (drawn mint with its
     * value label) and index 4 is the heavy day (drawn amber).
     */
    private void setupLoadChart() {
        if (loadChart == null) {
            return;
        }
        loadChart.setCeilingPercent(CRASH_CEILING_PERCENT);
        loadChart.setLoads(WEEK_LOADS.clone(), DAY_LETTERS.clone(), TODAY_INDEX, 4);
    }

    /** Day taps: repaint every pill, retitle the schedule, refresh the capacity card. */
    private void selectDay(int index) {
        selectedDay = index;
        selectedLoad = (int) WEEK_LOADS[index];
        renderDays();
        renderCapacityCard();
        renderScheduleTitle();
    }

    private void renderDays() {
        if (weekStrip == null) {
            return;
        }

        int activeBg = R.drawable.bg_day_pill_active;
        int idleBg = R.drawable.bg_day_pill_idle;
        int activeText = ContextCompat.getColor(requireContext(), R.color.on_brand);
        int idleLetter = ContextCompat.getColor(requireContext(), R.color.text_dim_dark);
        int idleNumber = ContextCompat.getColor(requireContext(), R.color.text_primary_dark);

        for (int i = 0; i < weekStrip.getChildCount(); i++) {
            View pill = weekStrip.getChildAt(i);
            boolean active = i == selectedDay;

            TextView letter = pill.findViewById(R.id.dayLetter);
            TextView number = pill.findViewById(R.id.dayNumber);
            View dot = pill.findViewById(R.id.dayDot);

            pill.setBackgroundResource(active ? activeBg : idleBg);

            if (letter != null) {
                letter.setText(active
                        ? getString(R.string.dash_today)
                        : DAY_LETTERS[i]);
                letter.setTextColor(active ? activeText : idleLetter);
                letter.setTypeface(null, active
                        ? android.graphics.Typeface.BOLD
                        : android.graphics.Typeface.NORMAL);
            }
            if (number != null) {
                number.setText(String.valueOf(DAY_NUMBERS[i]));
                number.setTextColor(active ? activeText : idleNumber);
            }
            if (dot != null) {
                // The prototype flags the heaviest day of the week with an amber dot.
                boolean heavy = i == 4;
                dot.setBackgroundResource(active
                        ? (heavy ? R.drawable.dot_amber : R.drawable.dot_mint)
                        : (heavy ? R.drawable.dot_amber : R.drawable.dot_idle));
            }
        }

        renderScheduleTitle();
    }

    private void renderScheduleTitle() {
        if (currentDayTitle == null) {
            return;
        }
        String day = DAY_NAMES[selectedDay] + " " + DAY_NUMBERS[selectedDay];
        currentDayTitle.setText(getString(R.string.dash_schedule_title, day));
    }

    // ==================================================================
    // Capacity card
    // ==================================================================

    private void renderCapLabel() {
        if (capLabel != null) {
            capLabel.setText(getString(R.string.dash_cap_label, CRASH_CEILING_PERCENT));
        }
    }

    private void renderWeekLabel() {
        if (weekLabel == null) {
            return;
        }
        Calendar calendar = Calendar.getInstance();
        int week = calendar.get(Calendar.WEEK_OF_YEAR);
        weekLabel.setText(getString(R.string.dash_week_number, week));
    }

    /**
     * Repaints the capacity card for the selected day's load, following the
     * prototype's three zones: optimal up to 55%, elevated to 75%, overload above.
     */
    private void renderCapacityCard() {
        Context context = requireContext();
        int value = selectedLoad;

        int stateRes;
        int headlineRes;
        int colorRes;
        int dinoRes;

        if (value <= 55) {
            stateRes = R.string.dash_state_optimal;
            headlineRes = R.string.dash_headline_balanced;
            colorRes = R.color.status_green;
            dinoRes = R.drawable.dino_happy;
        } else if (value <= 75) {
            stateRes = R.string.dash_state_elevated;
            headlineRes = R.string.dash_headline_elevated;
            colorRes = R.color.status_amber;
            dinoRes = R.drawable.dino_steady;
        } else {
            stateRes = R.string.dash_state_overload;
            headlineRes = R.string.dash_headline_overload;
            colorRes = R.color.status_red;
            dinoRes = R.drawable.dino_overload;
        }

        int color = ContextCompat.getColor(context, colorRes);

        if (stateLabel != null) {
            stateLabel.setText(stateRes);
            stateLabel.setTextColor(color);
        }
        if (stateDot != null) {
            stateDot.setBackgroundResource(dotFor(colorRes));
        }
        if (loadPercent != null) {
            loadPercent.setText(getString(R.string.dash_load_percent, value));
            loadPercent.setTextColor(color);
        }
        if (headline != null) {
            headline.setText(headlineRes);
        }
        if (telemetry != null) {
            telemetry.setText(getString(
                    R.string.dash_telemetry,
                    DEMO_SLEEP_HOURS,
                    OnboardingPrefs.getWorkHours(context) + "/20h"));
        }

        ImageView mascot = getView() == null ? null : getView().findViewById(R.id.heroMascot);
        if (mascot != null) {
            // The mascot assets are animated GIFs, so they must go through Glide.
            // setImageResource would decode only the first frame and freeze it.
            Glide.with(this).load(dinoRes).into(mascot);
        }
    }

    private static int dotFor(int colorRes) {
        if (colorRes == R.color.status_amber) {
            return R.drawable.dot_amber;
        }
        if (colorRes == R.color.status_red) {
            return R.drawable.dot_red;
        }
        return R.drawable.dot_mint;
    }

    // ==================================================================
    // Task feed
    // ==================================================================

    private void setupTaskFeed() {
        if (tasksList == null) {
            return;
        }
        tasksList.removeAllViews();

        List<Task> tasks = taskSource.loadAll();
        if (tasks == null) {
            tasks = Collections.emptyList();
        }

        if (tasks.isEmpty()) {
            // Honest empty state: the feed is unpopulated, so say so.
            showEmptyState();
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (Task task : tasks) {
            tasksList.addView(inflateTaskRow(inflater, task));
        }

        updateCounterAndChips();
    }

    private void showEmptyState() {
        if (tasksList != null) {
            tasksList.setVisibility(View.GONE);
        }
        if (emptyState != null) {
            emptyState.setVisibility(View.VISIBLE);
        }
        if (taskCounter != null) {
            taskCounter.setText(getString(R.string.dash_task_counter, 0, 0));
        }
    }

    @NonNull
    private View inflateTaskRow(@NonNull LayoutInflater inflater, @NonNull Task task) {
        View row = inflater.inflate(R.layout.item_task_row, tasksList, false);

        TextView title = row.findViewById(R.id.taskTitle);
        TextView time = row.findViewById(R.id.taskTime);
        TextView tag = row.findViewById(R.id.taskTag);
        View accent = row.findViewById(R.id.taskAccent);
        View check = row.findViewById(R.id.taskCheck);

        String classification = task.getClassification();
        boolean inflexible = "INFLEXIBLE".equals(classification);

        if (title != null) {
            title.setText(task.getTaskName());
        }
        if (time != null) {
            time.setText(task.getStartTime() + " - " + task.getEndTime());
        }
        if (tag != null) {
            tag.setText(inflexible ? R.string.dash_filter_work : R.string.dash_filter_classes);
        }
        if (accent != null) {
            accent.setBackgroundResource(
                    inflexible ? R.drawable.bg_accent_blue : R.drawable.bg_accent_mint);
            // The filter chips read the classification straight off the row.
            row.setTag(R.id.taskAccent, classification);
        }
        if (check != null) {
            check.setOnClickListener(v -> toggleDone(row));
        }

        taskRows.add(row);
        return row;
    }

    /** Check-off: strike the title, tick the box, drop the row to 65% alpha. */
    private void toggleDone(@NonNull View row) {
        boolean nowDone = !doneRows.contains(row);
        if (nowDone) {
            doneRows.add(row);
        } else {
            doneRows.remove(row);
        }

        row.setAlpha(nowDone ? 0.65f : 1f);

        View box = row.findViewById(R.id.taskCheckBox);
        if (box != null) {
            box.setBackgroundResource(nowDone
                    ? R.drawable.bg_task_check_done
                    : R.drawable.bg_task_check);
        }

        ImageView tick = row.findViewById(R.id.taskCheckIcon);
        if (tick != null) {
            tick.setVisibility(nowDone ? View.VISIBLE : View.INVISIBLE);
        }

        TextView title = row.findViewById(R.id.taskTitle);
        if (title != null) {
            if (nowDone) {
                title.setPaintFlags(title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            } else {
                title.setPaintFlags(title.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            }
        }

        updateCounterAndChips();
    }

    /**
     * Counter and "All (n)" chip both count VISIBLE rows only, so a filter narrows
     * the numbers the same way it narrows the feed.
     */
    private void updateCounterAndChips() {
        int visible = 0;
        int done = 0;
        for (View row : taskRows) {
            if (row.getVisibility() != View.VISIBLE) {
                continue;
            }
            visible++;
            if (doneRows.contains(row)) {
                done++;
            }
        }

        if (taskCounter != null) {
            taskCounter.setText(getString(R.string.dash_task_counter, done, visible));
        }
        TextView all = getView() == null ? null : getView().findViewById(R.id.filterAll);
        if (all != null) {
            all.setText(getString(R.string.dash_filter_all, visible));
        }
    }

    // ==================================================================
    // Filters
    // ==================================================================

    private void setupFilters() {
        View root = getView();
        if (root == null) {
            return;
        }
        click(root, R.id.filterAll, () -> applyFilter(null, R.id.filterAll));
        click(root, R.id.filterWork, () -> applyFilter("INFLEXIBLE", R.id.filterWork));
        click(root, R.id.filterClasses, () -> applyFilter("FLEXIBLE", R.id.filterClasses));
        click(root, R.id.filterStudy, () -> applyFilter("FLEXIBLE", R.id.filterStudy));

        // Paint "All" as the selected chip on entry, and give it its task count straight
        // away. Without this the chip only picked up its mint fill and "All (n)" label
        // after the first tap, so on arrival it looked missing or blank.
        applyFilter(null, R.id.filterAll);
        updateCounterAndChips();
    }

    /**
     * @param wanted classification to keep, or null for "show everything"
     */
    private void applyFilter(@Nullable String wanted, int selectedChipId) {
        View root = getView();
        if (root == null) {
            return;
        }

        int activeBg = R.drawable.bg_chip_active;
        int idleBg = R.drawable.bg_chip_idle;
        int activeText = ContextCompat.getColor(requireContext(), R.color.on_brand);
        int idleText = ContextCompat.getColor(requireContext(), R.color.text_muted_dark);

        int[] chips = {R.id.filterAll, R.id.filterWork, R.id.filterClasses, R.id.filterStudy};
        for (int chipId : chips) {
            TextView chip = root.findViewById(chipId);
            if (chip == null) {
                continue;
            }
            boolean active = chipId == selectedChipId;
            chip.setBackgroundResource(active ? activeBg : idleBg);
            chip.setTextColor(active ? activeText : idleText);
        }

        for (View row : taskRows) {
            Object tag = row.getTag(R.id.taskAccent);
            String classification = tag instanceof String ? (String) tag : null;
            boolean show = wanted == null || wanted.equals(classification);
            row.setVisibility(show ? View.VISIBLE : View.GONE);
        }

        updateCounterAndChips();
    }

    // ==================================================================
    // Actions
    // ==================================================================

    private void setupActions(@NonNull View view) {
        click(view, R.id.rebalanceButton, this::openTriage);
        click(view, R.id.dailyPopupButton, () -> ScreenNav.showDailyHarvest(this));
        click(view, R.id.weeklyPopupButton, () -> ScreenNav.showFeast(this));
        click(view, R.id.addTaskFab, this::openAddTask);
        click(view, R.id.toastClose, this::dismissRebalanceToast);
        click(view, R.id.syncNowButton, () ->
                Toast.makeText(requireContext(), R.string.dash_synced_ago, Toast.LENGTH_SHORT).show());
    }

    private void openTriage() {
        TriageSheetFragment sheet = new TriageSheetFragment();
        sheet.setOnRebalanceAccepted(this::showRebalanceToast);
        sheet.show(getChildFragmentManager(), "triage");
    }

    private void openAddTask() {
        new AddTaskSheetFragment().show(getChildFragmentManager(), "add_task");
    }

    // ==================================================================
    // Rebalance toast (prototype showToast / dismissToast)
    // ==================================================================

    /** Fades the confirmation in, then hides it again after 4.2s. */
    public void showRebalanceToast() {
        if (rebalanceToast == null) {
            return;
        }
        rebalanceToast.removeCallbacks(hideToast);
        rebalanceToast.setAlpha(0f);
        rebalanceToast.setVisibility(View.VISIBLE);
        rebalanceToast.animate()
                .alpha(1f)
                .setDuration(250L)
                .start();
        rebalanceToast.postDelayed(hideToast, TOAST_VISIBLE_MS);
    }

    /** Fades the confirmation out. Also wired to the toast's close icon. */
    public void dismissRebalanceToast() {
        if (rebalanceToast == null) {
            return;
        }
        rebalanceToast.removeCallbacks(hideToast);
        rebalanceToast.animate()
                .alpha(0f)
                .setDuration(250L)
                .withEndAction(() -> {
                    if (rebalanceToast != null) {
                        rebalanceToast.setVisibility(View.GONE);
                    }
                })
                .start();
    }

    // ==================================================================
    // Small helpers
    // ==================================================================

    private static void click(@NonNull View root, int id, @NonNull Runnable action) {
        View target = root.findViewById(id);
        if (target != null) {
            target.setOnClickListener(v -> action.run());
        }
    }
}

