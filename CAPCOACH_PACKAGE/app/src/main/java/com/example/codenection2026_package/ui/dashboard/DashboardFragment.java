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
import com.example.codenection2026_package.data.TaskRepository;
import com.example.codenection2026_package.model.Task;
import com.example.codenection2026_package.ui.addtask.AddTaskSheetFragment;
import com.example.codenection2026_package.ui.onboarding.OnboardingPrefs;
import com.example.codenection2026_package.ui.onboarding.ThemeController;
import com.example.codenection2026_package.ui.shell.ScreenNav;
import com.example.codenection2026_package.ui.widget.LoadChartView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * SCREEN 3 - DASHBOARD (port of Prototype/dashboard.html).
 *
 * <p>One scrolling feed plus four fixed pieces of chrome: the header, the rebalance
 * toast, the floating Add Task button, and the shared bottom navigation bar.
 *
 * <p><b>The week strip is the real week.</b> The prototype hardcodes Mon 14 to Sun 20
 * with Wednesday marked today. This screen builds the seven cells from the device
 * calendar instead, so the numbers are the actual dates and exactly one cell - the
 * real today - is labelled TODAY. Tapping any other cell selects that day and nothing
 * else: it is not relabelled as today, because it is not one.
 *
 * <p><b>The feed is the real feed.</b> Rows come from Room through
 * {@link TaskRepository}, filtered to the selected day's date, so a task saved from
 * the Add Activity sheet appears on the day it was filed under. {@link TaskRepository}
 * owns the background thread Room requires and answers on the main thread; until the
 * first answer arrives the feed shows its "no tasks yet" empty state, which is also
 * what a genuinely free day looks like.
 *
 * <p>The capacity card and the weekly load chart still run on the prototype's demo
 * percentages - the biometrics model that will supply them is not wired yet.
 */
public class DashboardFragment extends Fragment {

    /** Sleep figure shown in the telemetry line. The Room layer will supply the real one. */
    private static final String DEMO_SLEEP_HOURS = "7.8h";

    /**
     * The prototype's seven charted loads, in week order from Monday. Demo data:
     * nothing computes these yet.
     */
    private static final float[] WEEK_LOADS = {65f, 50f, 40f, 75f, 88f, 25f, 20f};

    private static final int CRASH_CEILING_PERCENT = 90;
    private static final long TOAST_VISIBLE_MS = 4200L;

    /** Narrow day letters, Monday first. Matches the prototype's M T W T F S S. */
    private static final String[] DAY_LETTERS = {"M", "T", "W", "T", "F", "S", "S"};

    private static final String ISO_PATTERN = "yyyy-MM-dd";

    private TextView stateLabel;
    private View stateDot;
    private LinearLayout statePill;
    private TextView loadPercent;
    private TextView headline;
    private TextView telemetry;
    private TextView capLabel;
    private TextView monthLabel;
    private TextView weekLabel;
    private TextView currentDayTitle;
    private TextView taskCounter;
    private TextView emptyState;
    private LinearLayout weekStrip;
    private LinearLayout tasksList;
    private LoadChartView loadChart;
    private View rebalanceToast;

    // ---- The current week, built from the device calendar in buildWeek() ----

    /** ISO dates ("yyyy-MM-dd"), Monday first. The DAO queries on exactly these. */
    private final String[] weekDates = new String[7];
    private final String[] dayNames = new String[7];
    private final int[] dayNumbers = new int[7];
    private final int[] weekOfYear = new int[7];
    private final String[] monthTitles = new String[7];

    /** 0 = Mon ... 6 = Sun. Today's own cell. */
    private int todayIndex;

    /** The cell drawn amber: the heaviest load of the week in the demo data. */
    private int heavyIndex = 4;

    /** 0 = Mon ... 6 = Sun. The cell the user is looking at; starts on today. */
    private int selectedDay;

    /** Load shown in the capacity card, in percent. Tracks the selected day. */
    private int selectedLoad;

    /** The chip the user last tapped, so a reload keeps the same filter applied. */
    @Nullable
    private String activeFilter;

    private int activeFilterChip = R.id.filterAll;

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

        buildWeek();
        setupWeekStrip();
        setupLoadChart();
        renderCapLabel();
        renderWeekLabel();
        renderCapacityCard();
        renderDays();

        // Paints "All" as the selected chip on entry, so it is never blank on arrival.
        setupFilters();
        setupActions(view);

        // Rows arrive asynchronously, so the empty state is on screen until they do.
        reloadTasks();
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
        monthLabel = null;
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
        monthLabel = view.findViewById(R.id.monthLabel);
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
     * Builds the seven days of the week the device is currently in, Monday first.
     *
     * <p>Everything the strip and the header show comes from here: the ISO date the
     * feed is queried with, the day numbers, the month title and the week number.
     * The prototype's fixed 14-20 is gone, which is what lets a task saved today be
     * found on today's cell.
     */
    private void buildWeek() {
        SimpleDateFormat iso = new SimpleDateFormat(ISO_PATTERN, Locale.US);
        SimpleDateFormat shortName = new SimpleDateFormat("EEE", Locale.US);
        SimpleDateFormat monthTitle = new SimpleDateFormat("MMMM yyyy", Locale.US);

        Calendar cursor = Calendar.getInstance();
        // Calendar.SUNDAY is 1 and Calendar.SATURDAY is 7, so this shifts the week to
        // start on Monday: Mon -> 0, Tue -> 1, ... Sun -> 6.
        todayIndex = (cursor.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        cursor.add(Calendar.DAY_OF_YEAR, -todayIndex);

        for (int i = 0; i < 7; i++) {
            Date date = cursor.getTime();
            weekDates[i] = iso.format(date);
            dayNames[i] = shortName.format(date);
            dayNumbers[i] = cursor.get(Calendar.DAY_OF_MONTH);
            weekOfYear[i] = cursor.get(Calendar.WEEK_OF_YEAR);
            monthTitles[i] = monthTitle.format(date);
            cursor.add(Calendar.DAY_OF_YEAR, 1);
        }

        heavyIndex = heaviestLoadIndex();

        // Land on today, which is also what the capacity card starts on.
        selectedDay = todayIndex;
        selectedLoad = (int) WEEK_LOADS[selectedDay];
    }

    /** @return the index of the largest demo load, so the amber bar is never a guess */
    private static int heaviestLoadIndex() {
        int heaviest = 0;
        for (int i = 1; i < WEEK_LOADS.length; i++) {
            if (WEEK_LOADS[i] > WEEK_LOADS[heaviest]) {
                heaviest = i;
            }
        }
        return heaviest;
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
        for (int i = 0; i < weekDates.length; i++) {
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
     * percentage height against a value range. The mint bar follows the selected day
     * and the amber bar is the week's heaviest, the same two signals the strip shows.
     */
    private void setupLoadChart() {
        if (loadChart == null) {
            return;
        }
        loadChart.setCeilingPercent(CRASH_CEILING_PERCENT);
        renderChart();
    }

    private void renderChart() {
        if (loadChart == null) {
            return;
        }
        loadChart.setLoads(WEEK_LOADS.clone(), DAY_LETTERS.clone(), selectedDay, heavyIndex);
    }

    /** Day taps: repaint every pill, retitle the schedule, refresh the capacity card. */
    private void selectDay(int index) {
        selectedDay = index;
        selectedLoad = (int) WEEK_LOADS[index];
        renderChart();
        renderWeekLabel();
        renderDays();
        renderCapacityCard();
        renderScheduleTitle();
        reloadTasks();
    }

    private void renderDays() {
        if (weekStrip == null) {
            return;
        }

        int activeBg = R.drawable.bg_day_pill_active;
        int idleBg = R.drawable.bg_day_pill_idle;
        int activeText = ContextCompat.getColor(requireContext(), R.color.on_brand);
        int idleLetter = ContextCompat.getColor(requireContext(), R.color.text_dim_dark);
        int todayLetter = ContextCompat.getColor(requireContext(), R.color.brand_mint);
        int idleNumber = ContextCompat.getColor(requireContext(), R.color.text_primary_dark);

        for (int i = 0; i < weekStrip.getChildCount(); i++) {
            View pill = weekStrip.getChildAt(i);
            boolean active = i == selectedDay;
            boolean today = i == todayIndex;

            TextView letter = pill.findViewById(R.id.dayLetter);
            TextView number = pill.findViewById(R.id.dayNumber);
            View dot = pill.findViewById(R.id.dayDot);

            pill.setBackgroundResource(active ? activeBg : idleBg);

            if (letter != null) {
                // Only the real today says TODAY. Any other cell keeps its own letter,
                // so selecting Thursday cannot turn Thursday into today.
                letter.setText(today ? getString(R.string.dash_today) : DAY_LETTERS[i]);
                letter.setTextColor(active ? activeText : (today ? todayLetter : idleLetter));
                letter.setTypeface(null, active || today
                        ? android.graphics.Typeface.BOLD
                        : android.graphics.Typeface.NORMAL);
            }
            if (number != null) {
                number.setText(String.valueOf(dayNumbers[i]));
                number.setTextColor(active ? activeText : idleNumber);
            }
            if (dot != null) {
                dot.setBackgroundResource(dotFor(i, active, today));
            }
        }
    }

    /**
     * The strip's dots, in priority order: the week's heaviest day keeps its amber
     * warning whatever else is going on, today and the selected cell get mint, and
     * every other day gets the idle grey.
     */
    private int dotFor(int index, boolean active, boolean today) {
        if (index == heavyIndex) {
            return R.drawable.dot_amber;
        }
        if (today || active) {
            return R.drawable.dot_mint;
        }
        return R.drawable.dot_idle;
    }

    private void renderScheduleTitle() {
        if (currentDayTitle == null) {
            return;
        }
        String day = dayNames[selectedDay] + " " + dayNumbers[selectedDay];
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

    /** Month and week number of the selected day, so the header follows the strip. */
    private void renderWeekLabel() {
        if (monthLabel != null) {
            monthLabel.setText(monthTitles[selectedDay]);
        }
        if (weekLabel != null) {
            weekLabel.setText(getString(R.string.dash_week_number, weekOfYear[selectedDay]));
        }
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

    /**
     * Loads the selected day's rows and paints them.
     *
     * <p>The query runs on {@link TaskRepository}'s worker thread and comes back on
     * the main thread, so this can be called from a click handler.
     */
    private void reloadTasks() {
        if (tasksList == null || weekDates[selectedDay] == null) {
            return;
        }
        TaskRepository.loadByDate(requireContext(), weekDates[selectedDay], tasks -> {
            if (!isAdded() || tasksList == null) {
                return;
            }
            renderTaskFeed(tasks);
        });
    }

    private void renderTaskFeed(@Nullable List<Task> tasks) {
        if (tasksList == null) {
            return;
        }
        tasksList.removeAllViews();
        taskRows.clear();
        doneRows.clear();

        if (tasks == null || tasks.isEmpty()) {
            // Honest empty state: this day genuinely has nothing filed under it.
            showEmptyState();
            return;
        }

        tasksList.setVisibility(View.VISIBLE);
        if (emptyState != null) {
            emptyState.setVisibility(View.GONE);
        }

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (Task task : tasks) {
            tasksList.addView(inflateTaskRow(inflater, task));
        }

        // Keep whatever chip was active across the refresh.
        applyFilter(activeFilter, activeFilterChip);
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

        // Remembered so a reload after a save or a day change keeps the same chip.
        activeFilter = wanted;
        activeFilterChip = selectedChipId;

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
        AddTaskSheetFragment sheet = new AddTaskSheetFragment();
        // A saved row has to show up without leaving the screen, so the sheet reports
        // back the date it filed the task under.
        sheet.setOnTaskSavedListener(this::onTaskSaved);
        sheet.show(getChildFragmentManager(), AddTaskSheetFragment.TAG);
    }

    /**
     * The sheet wrote a row. If it belongs to a day of the week on screen, jump to
     * that day so the new task is visible immediately. A date further out than this
     * week is not silently swallowed: the toast says where it landed.
     */
    private void onTaskSaved(@NonNull String isoDate) {
        int index = indexOfWeekDate(isoDate);
        if (index >= 0) {
            if (index != selectedDay) {
                selectDay(index);
            } else {
                reloadTasks();
            }
            return;
        }
        Toast.makeText(requireContext(),
                getString(R.string.dash_saved_other_week, isoDate),
                Toast.LENGTH_LONG).show();
        reloadTasks();
    }

    private int indexOfWeekDate(@NonNull String isoDate) {
        for (int i = 0; i < weekDates.length; i++) {
            if (isoDate.equals(weekDates[i])) {
                return i;
            }
        }
        return -1;
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
