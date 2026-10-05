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

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.example.codenection2026_package.R;
import com.example.codenection2026_package.api.CalendarManager;
import com.example.codenection2026_package.data.CategoryRepository;
import com.example.codenection2026_package.data.TaskRepository;
import com.example.codenection2026_package.model.CoachVoice;
import com.example.codenection2026_package.model.Task;
import com.example.codenection2026_package.model.ToneType;
import com.example.codenection2026_package.ui.addtask.AddTaskSheetFragment;
import com.example.codenection2026_package.ui.edittask.EditTaskSheetFragment;
import com.example.codenection2026_package.ui.onboarding.OnboardingPrefs;
import com.example.codenection2026_package.ui.shell.AppHeader;
import com.example.codenection2026_package.ui.shell.ScreenNav;
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
 * <p><b>The feed is grouped by the task's own category.</b> Rows come from Room through
 * {@link TaskRepository}, filtered to the selected day's date, and each one carries the
 * category it was filed under - the same five the Add Activity sheet offers. The filter
 * chips are built from {@link CategoryRepository#BASELINE} at runtime rather than being
 * written into the layout, so they can never name a bucket the sheet cannot produce.
 * The row tag prints that category, and the row also repeats the High/Med/Low priority
 * the sheet collected.
 *
 * <p>{@link TaskRepository} owns the background thread Room requires and answers on the
 * main thread; until the first answer arrives the feed shows its "no tasks yet" empty
 * state, which is also what a genuinely free day looks like.
 *
 * <p>The capacity card and the weekly load chart still run on the prototype's demo
 * percentages - the biometrics model that will supply them is not wired yet.
 */
public class DashboardFragment extends Fragment {

    /** Sleep figure shown in the telemetry line. The Room layer will supply the real one. */
    private static final String DEMO_SLEEP_HOURS = "7.8h";

    /**
     * Labels for {@link CategoryRepository#BASELINE}, in that exact order.
     *
     * <p>Kept here rather than in the repository so the data layer stays free of
     * resource ids; the two lists are index-aligned and must be edited together.
     */
    @StringRes
    private static final int[] CATEGORY_LABELS = {
            R.string.cat_academic,
            R.string.cat_work,
            R.string.cat_errand,
            R.string.cat_social,
            R.string.cat_cocurricular
    };

    /**
     * The prototype's seven charted loads, in week order from Monday. Demo data:
     * nothing computes these yet.
     */
    private static final float[] WEEK_LOADS = {65f, 50f, 40f, 75f, 88f, 25f, 20f};

    private static final int CRASH_CEILING_PERCENT = 90;
    private static final long TOAST_VISIBLE_MS = 4200L;

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
    private LinearLayout filterPills;
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

    /**
     * The canonical category the user last filtered by, or null for "All". Held as a
     * name rather than a chip reference so a reload can re-apply it to fresh rows.
     */
    @Nullable
    private String activeCategory;

    private final List<TextView> filterChips = new ArrayList<>();

    /** The "All (n)" chip, which also carries the visible-row count. */
    @Nullable
    private TextView allChip;

    private final List<View> taskRows = new ArrayList<>();
    private final Set<View> doneRows = new HashSet<>();

    private final Runnable hideToast = this::dismissRebalanceToast;

    // API INJECTION
    private CalendarManager calendarManager;

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
        calendarManager = new CalendarManager(requireContext());

        // Shared shell: bottom nav selection, then the shared top bar (mascot, theme
        // toggle, profile picture). AppHeader also starts the header mascot's GIF.
        ScreenNav.bindNav(this, view, ScreenNav.Tab.HOME);
        AppHeader.bind(this, view, R.string.brand_offline_ml);

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
        filterChips.clear();
        allChip = null;
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
        filterPills = null;
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
        filterPills = view.findViewById(R.id.filterPills);
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

    /**
     * Narrow day letters, Monday first, read from strings.xml so they can be
     * translated. A fresh array every call: the load chart keeps whatever it is
     * handed, which is why the old static was cloned on the way in.
     */
    @NonNull
    private String[] dayLetters() {
        return getResources().getStringArray(R.array.day_letters);
    }

    private void renderChart() {
        if (loadChart == null) {
            return;
        }
        loadChart.setLoads(WEEK_LOADS.clone(), dayLetters(), selectedDay, heavyIndex);
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

        // Read once for the whole strip rather than once per pill.
        String[] letters = dayLetters();

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
                letter.setText(today ? getString(R.string.dash_today) : letters[i]);
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

        CoachVoice.Line stateLine;
        CoachVoice.Line headlineLine;
        int colorRes;
        int dinoRes;

        if (value <= 55) {
            stateLine = CoachVoice.Line.DASH_STATE_OPTIMAL;
            headlineLine = CoachVoice.Line.DASH_HEADLINE_BALANCED;
            colorRes = R.color.status_green;
            dinoRes = R.drawable.dino_happy;
        } else if (value <= 75) {
            stateLine = CoachVoice.Line.DASH_STATE_ELEVATED;
            headlineLine = CoachVoice.Line.DASH_HEADLINE_ELEVATED;
            colorRes = R.color.status_amber;
            dinoRes = R.drawable.dino_steady;
        } else {
            stateLine = CoachVoice.Line.DASH_STATE_OVERLOAD;
            headlineLine = CoachVoice.Line.DASH_HEADLINE_OVERLOAD;
            colorRes = R.color.status_red;
            dinoRes = R.drawable.dino_overload;
        }

        int color = ContextCompat.getColor(context, colorRes);

        // The load decides WHICH state, the coaching tone decides how it is worded.
        ToneType tone = tone();
        if (stateLabel != null) {
            stateLabel.setText(stateLine.pick(tone));
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
            headline.setText(headlineLine.pick(tone));
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

    private void renderTaskFeed(@Nullable List<TaskRepository.FeedItem> feed) {
        if (tasksList == null) {
            return;
        }
        tasksList.removeAllViews();
        taskRows.clear();
        doneRows.clear();

        if (feed == null || feed.isEmpty()) {
            // Honest empty state: this day genuinely has nothing filed under it.
            showEmptyState();
            // The chips still have to be repainted: without this the "All (n)" count
            // keeps whatever the previously viewed day had.
            applyFilter(activeCategory);
            return;
        }

        tasksList.setVisibility(View.VISIBLE);
        if (emptyState != null) {
            emptyState.setVisibility(View.GONE);
        }

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (TaskRepository.FeedItem item : feed) {
            tasksList.addView(inflateTaskRow(inflater, item));
        }

        // Keep whatever chip was active across the refresh.
        applyFilter(activeCategory);
    }

    private void showEmptyState() {
        if (tasksList != null) {
            tasksList.setVisibility(View.GONE);
        }
        if (emptyState != null) {
            // Set here rather than in the layout: the layout cannot know the coaching tone.
            emptyState.setText(CoachVoice.Line.DASH_NO_TASKS.pick(tone()));
            emptyState.setVisibility(View.VISIBLE);
        }
        if (taskCounter != null) {
            taskCounter.setText(getString(R.string.dash_task_counter, 0, 0));
        }
    }

    /**
     * Builds one row: title, time, the category it was filed under, and the priority
     * that was chosen for it.
     */
    @NonNull
    private View inflateTaskRow(@NonNull LayoutInflater inflater,
                                @NonNull TaskRepository.FeedItem item) {
        View row = inflater.inflate(R.layout.item_task_row, tasksList, false);

        Task task = item.getTask();
        String category = item.getCategoryName();
        boolean inflexible = "INFLEXIBLE".equals(task.getClassification());

        TextView title = row.findViewById(R.id.taskTitle);
        TextView time = row.findViewById(R.id.taskTime);
        TextView tag = row.findViewById(R.id.taskTag);
        TextView priority = row.findViewById(R.id.taskPriority);
        View accent = row.findViewById(R.id.taskAccent);
        View check = row.findViewById(R.id.taskCheck);

        if (title != null) {
            title.setText(task.getTaskName());
        }
        if (time != null) {
            time.setText(getString(R.string.dash_task_time_range, task.getStartTime(), task.getEndTime()));
        }

        // The tag names the user's own category. It used to print "Work"/"Classes" off
        // the classification, which meant a Social task was labelled Classes and none of
        // the five categories the sheet offers were visible here at all.
        if (tag != null) {
            if (category == null) {
                tag.setVisibility(View.GONE);
            } else {
                boolean work = CategoryRepository.WORK.equals(category);
                tag.setVisibility(View.VISIBLE);
                tag.setText(labelFor(category));
                tag.setBackgroundResource(work ? R.drawable.bg_tag_blue : R.drawable.bg_task_tag);
                tag.setTextColor(ContextCompat.getColor(requireContext(),
                        work ? R.color.secondary_blue : R.color.text_muted_dark));
            }
        }

        if (priority != null) {
            String level = task.getPriority();
            priority.setText(priorityLabelRes(level));
            priority.setBackgroundResource(priorityTagBackground(level));
            priority.setTextColor(ContextCompat.getColor(requireContext(),
                    priorityTagTextColor(level)));
        }

        if (accent != null) {
            accent.setBackgroundResource(
                    inflexible ? R.drawable.bg_accent_blue : R.drawable.bg_accent_mint);
        }
        if (check != null) {
            check.setOnClickListener(v -> toggleDone(row));
        }

        // The filter chips read the category straight off the row.
        row.setTag(R.id.taskAccent, category);

        // Store the whole Task object so toggleDone can save it later!
        row.setTag(task);

        taskRows.add(row);

        // If the task was already completed in the DB, strike it out immediately upon loading
        if (task.isCompleted()) {
            toggleDone(row); // This visually checks it off
        }

        // Long-press opens the Edit Task sheet. The standard Android gesture for "act on this
        // item" is used rather than a tap, so the row keeps its unclaimed tap gesture and the
        // check box keeps its own target: a tap on the box still only ticks the task off.
        row.setOnLongClickListener(v -> {
            Object rowTag = v.getTag();
            if (rowTag instanceof Task) {
                Task clickedTask = (Task) rowTag;

                //Prevents completed task from edits
                if (clickedTask.isCompleted()) {
                    //TO BE REPLACED WITH LINES IN STRINGS.XML
                    Toast.makeText(requireContext(), "Completed tasks cannot be edited.", Toast.LENGTH_SHORT).show();
                    return true;
                }

                openEditTask(clickedTask, category);
                return true;
            }
            return false;
        });

        return row;
    }

    /**
     * Opens the Edit Task sheet for one row.
     *
     * <p>The category name travels with the task because the row only stores its id: the
     * sheet's spinner is indexed by position, so it needs the canonical name to preselect the
     * entry the task was actually filed under.
     */
    private void openEditTask(@NonNull Task task, @Nullable String category) {
        EditTaskSheetFragment sheet = EditTaskSheetFragment.newInstance(task, category);
        sheet.setOnTaskChangedListener(this::onTaskChanged);
        sheet.show(getChildFragmentManager(), EditTaskSheetFragment.TAG);
    }

    /**
     * The edit sheet rewrote or removed a row.
     *
     * <p>Same treatment as a save: if the row belongs to a day of the week on screen, go there
     * so the change is visible straight away.
     */
    private void onTaskChanged(@NonNull String isoDate) {
        int index = indexOfWeekDate(isoDate);
        if (index >= 0 && index != selectedDay) {
            selectDay(index);
            return;
        }
        reloadTasks();
    }

    /** @return the localised label for a canonical category name */
    @NonNull
    private String labelFor(@Nullable String category) {
        int index = CategoryRepository.indexOf(category);
        if (index < 0 || index >= CATEGORY_LABELS.length) {
            return category == null ? "" : category;
        }
        return getString(CATEGORY_LABELS[index]);
    }

    @StringRes
    private static int priorityLabelRes(@Nullable String priority) {
        if (Task.PRIORITY_HIGH.equals(priority)) {
            return R.string.addtask_priority_high;
        }
        if (Task.PRIORITY_LOW.equals(priority)) {
            return R.string.addtask_priority_low;
        }
        return R.string.addtask_priority_med;
    }

    /** High is red, Med amber, Low quiet - the same three steps the sheet offers. */
    private static int priorityTagBackground(@Nullable String priority) {
        if (Task.PRIORITY_HIGH.equals(priority)) {
            return R.drawable.bg_tag_red;
        }
        if (Task.PRIORITY_LOW.equals(priority)) {
            return R.drawable.bg_task_tag;
        }
        return R.drawable.bg_tag_amber;
    }

    @ColorRes
    private static int priorityTagTextColor(@Nullable String priority) {
        if (Task.PRIORITY_HIGH.equals(priority)) {
            return R.color.status_red;
        }
        if (Task.PRIORITY_LOW.equals(priority)) {
            return R.color.text_muted_dark;
        }
        return R.color.status_amber;
    }

    /** Check-off: strike the title, tick the box, drop the row to 65% alpha, and save to Room. */
    private void toggleDone(@NonNull View row) {
        boolean nowDone = !doneRows.contains(row);

        // 1. Get the actual Task object from the view's data
        // (Assuming we store the Task object in the row's tag earlier, or we can fetch it)
        // Since the current inflateTaskRow doesn't store the Task object itself, let's just
        // update the visual state first.

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

        // 2. Save the change to the database
        Object tag = row.getTag();
        if (tag instanceof Task) {
            Task task = (Task) tag;
            task.setCompleted(nowDone);
            TaskRepository.update(requireContext(), task, success -> {
                if (!Boolean.TRUE.equals(success)) {
                    android.util.Log.e("CapCoachAPI", "Failed to save checkmark state to DB.");
                }
            });
        }
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
        if (allChip != null) {
            allChip.setText(getString(R.string.dash_filter_all, visible));
        }
    }

    // ==================================================================
    // Filters
    // ==================================================================

    /**
     * Builds the chip row: "All (n)" followed by one chip per baseline category.
     *
     * <p>The chips are inflated from {@link CategoryRepository#BASELINE} instead of
     * being written into the layout, which is what keeps them in step with the Add
     * Activity sheet. The old hardcoded Work / Classes / Study chips filtered on the
     * FLEXIBLE classification, so "Classes" and "Study" were the same bucket and a
     * Social or Errand task could not be found at all.
     */
    private void setupFilters() {
        if (filterPills == null) {
            return;
        }
        filterPills.removeAllViews();
        filterChips.clear();

        LayoutInflater inflater = LayoutInflater.from(requireContext());

        allChip = addChip(inflater, null, 0);
        for (int i = 0; i < CategoryRepository.BASELINE.length; i++) {
            addChip(inflater, CategoryRepository.BASELINE[i], i + 1);
        }

        // Paint "All" as the selected chip on entry, and give it its task count straight
        // away. Without this the chip only picked up its mint fill and "All (n)" label
        // after the first tap, so on arrival it looked missing or blank.
        applyFilter(null);
    }

    @NonNull
    private TextView addChip(@NonNull LayoutInflater inflater,
                             @Nullable String category,
                             int index) {
        TextView chip = (TextView) inflater.inflate(R.layout.item_filter_chip, filterPills, false);

        if (index > 0) {
            // Every chip but the first is nudged off its neighbour; the first lines up
            // with the cards above it.
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) chip.getLayoutParams();
            params.setMarginStart(getResources().getDimensionPixelSize(R.dimen.space_sm));
            chip.setLayoutParams(params);
        }

        chip.setText(category == null ? getString(R.string.dash_filter_all, 0) : labelFor(category));
        chip.setTag(category);
        chip.setOnClickListener(v -> applyFilter(category));

        filterPills.addView(chip);
        filterChips.add(chip);
        return chip;
    }

    /**
     * @param category canonical category name to keep, or null for "show everything"
     */
    private void applyFilter(@Nullable String category) {
        // Remembered so a reload after a save or a day change keeps the same chip lit.
        activeCategory = category;

        int activeBg = R.drawable.bg_chip_active;
        int idleBg = R.drawable.bg_chip_idle;
        int activeText = ContextCompat.getColor(requireContext(), R.color.on_brand);
        int idleText = ContextCompat.getColor(requireContext(), R.color.text_muted_dark);

        for (TextView chip : filterChips) {
            Object tag = chip.getTag();
            boolean active = category == null ? tag == null : category.equals(tag);
            chip.setBackgroundResource(active ? activeBg : idleBg);
            chip.setTextColor(active ? activeText : idleText);
        }

        for (View row : taskRows) {
            Object tag = row.getTag(R.id.taskAccent);
            String rowCategory = tag instanceof String ? (String) tag : null;
            boolean show = category == null || category.equals(rowCategory);
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

        // WIRE CALENDAR MANAGER HERE
        click(view, R.id.syncNowButton, this::syncCalendar);
    }

    /**
     * Pulls the user's native Google Calendar events and maps them directly
     * into Lieva's Room database via TaskRepository, with de-duplication.
     */
    /**
     * Pulls the user's native Google Calendar events and maps them directly
     * into Lieva's Room database via TaskRepository, with de-duplication.
     */
    private void syncCalendar() {
        Toast.makeText(requireContext(),
                getString(CoachVoice.Line.DASH_SYNC_STARTING.pick(tone())),
                Toast.LENGTH_SHORT).show();

        // THE GUARDRAIL: Check for permission explicitly.
        // This prevents the data-loss bug WITHOUT blocking empty calendars.
        if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.READ_CALENDAR) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(requireContext(), "Calendar permission denied. Cannot sync.", Toast.LENGTH_SHORT).show();
            return;
        }

        Context appContext = requireContext().getApplicationContext();

        new Thread(() -> {
            List<CalendarManager.CalendarEvent> nativeEvents = calendarManager.logUpcomingWeekEvents();

            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                if (!isAdded()) return;

                // GUARDRAIL: If it returns NULL, the read failed. Abort to prevent wiping data.
                if (nativeEvents == null) {
                    Toast.makeText(requireContext(), "Sync failed. Could not read calendar.", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (nativeEvents.isEmpty()) {
                    Toast.makeText(requireContext(), getString(CoachVoice.Line.DASH_SYNC_NONE.pick(tone())), Toast.LENGTH_SHORT).show();
                }

                // 1. GATHER ALL DATES: Generate the EXACT 22-day window queried in CalendarManager.
                // This ensures we check every day for ghosts, even if they aren't on the visible week strip.
                Set<String> datesToSync = new HashSet<>();
                Calendar cal = Calendar.getInstance();
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                cal.add(Calendar.DAY_OF_YEAR, -7);

                SimpleDateFormat iso = new SimpleDateFormat(ISO_PATTERN, Locale.US);
                for (int i = 0; i < 22; i++) { // 7 days back + today + 14 days forward = 22 days
                    datesToSync.add(iso.format(cal.getTime()));
                    cal.add(Calendar.DAY_OF_YEAR, 1);
                }

                // Track how many days have finished processing to know when to refresh the UI
                final int totalDays = datesToSync.size();
                final int[] daysProcessed = {0};

                // 2. Loop through every distinct date in our wide window
                for (String dateStr : datesToSync) {

                    // Filter the native Google events down to JUST this specific day
                    List<CalendarManager.CalendarEvent> nativeForDay = new ArrayList<>();
                    for (CalendarManager.CalendarEvent e : nativeEvents) {
                        if (dateStr.equals(e.dateStr)) {
                            nativeForDay.add(e);
                        }
                    }

                    // 3. Pull CapCoach's local tasks for this specific day
                    TaskRepository.loadByDate(appContext, dateStr, localFeed -> {
                        if (localFeed == null) {
                            checkAndReload(totalDays, daysProcessed);
                            return;
                        }

                        // --- STEP A: KILL GHOSTS ---
                        for (TaskRepository.FeedItem item : localFeed) {
                            Task localTask = item.getTask();
                            if (localTask.getCalendarEventId() != null) {
                                boolean stillExistsInGoogle = false;
                                for (CalendarManager.CalendarEvent ne : nativeForDay) {
                                    if (ne.eventId == localTask.getCalendarEventId()) {
                                        stillExistsInGoogle = true;
                                        break;
                                    }
                                }
                                if (!stillExistsInGoogle) {

                                    // LOGCAT GHOSTKILLER TEST:
                                    android.util.Log.d("CapCoachAPI", "Ghost Killer: Deleting hidden ghost event: " + localTask.getTaskName() + " on " + localTask.getDate());

                                    // Ghost detected! Physically delete it and wait for success.
                                    TaskRepository.delete(appContext, localTask, success -> {
                                        if (isAdded() && indexOfWeekDate(dateStr) == selectedDay) reloadTasks();
                                    });
                                }
                            }
                        }

                        // --- STEP B: ADD NEW / UPDATE EXISTING ---
                        for (CalendarManager.CalendarEvent ne : nativeForDay) {
                            Task matchedLocal = null;
                            for (TaskRepository.FeedItem item : localFeed) {
                                Task localTask = item.getTask();
                                if (localTask.getCalendarEventId() != null &&
                                        localTask.getCalendarEventId() == ne.eventId) {
                                    matchedLocal = localTask;
                                    break;
                                }
                            }

                            if (matchedLocal != null) {
                                matchedLocal.setStartTime(ne.startTimeStr);
                                matchedLocal.setEndTime(ne.endTimeStr);
                                matchedLocal.setTaskName(ne.title);
                                TaskRepository.update(appContext, matchedLocal, success -> {
                                    if (isAdded() && indexOfWeekDate(dateStr) == selectedDay) reloadTasks();
                                });
                            } else {
                                Task importedTask = new Task(
                                        "INFLEXIBLE", ne.title, null,
                                        ne.dateStr, ne.startTimeStr, ne.endTimeStr
                                );
                                importedTask.setPriority(Task.PRIORITY_MED);
                                importedTask.setDeferralHours(0);
                                importedTask.setCalendarEventId(ne.eventId);

                                TaskRepository.save(appContext, importedTask, CategoryRepository.ACADEMIC, rowId -> {
                                    if (isAdded() && indexOfWeekDate(ne.dateStr) == selectedDay) reloadTasks();
                                });
                            }
                        }

                        checkAndReload(totalDays, daysProcessed);
                    });
                }
            });
        }).start();
    }

    /** Helper to trigger the final UI toast when the loop completes. */
    private void checkAndReload(int totalDays, int[] daysProcessed) {
        daysProcessed[0]++;
        if (daysProcessed[0] == totalDays && isAdded()) {
            Toast.makeText(requireContext(),
                    getString(CoachVoice.Line.DASH_SYNC_DONE.pick(tone())),
                    Toast.LENGTH_SHORT).show();
        }
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
                getString(CoachVoice.Line.DASH_SAVED_OTHER_WEEK.pick(tone()), isoDate),
                Toast.LENGTH_LONG).show();
        reloadTasks();
    }

    /** The coaching tone chosen in onboarding or Settings. */
    @NonNull
    private ToneType tone() {
        return OnboardingPrefs.getTone(requireContext());
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
        // Both lines are tone-dependent, so they are written on the way in rather than
        // sitting in the layout.
        ToneType tone = tone();
        TextView title = rebalanceToast.findViewById(R.id.toastTitle);
        if (title != null) {
            title.setText(CoachVoice.Line.TOAST_REBALANCED_TITLE.pick(tone));
        }
        TextView sub = rebalanceToast.findViewById(R.id.toastSub);
        if (sub != null) {
            sub.setText(CoachVoice.Line.TOAST_REBALANCED_SUB.pick(tone));
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