package com.example.codenection2026_package.ui.addtask;

import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.example.codenection2026_package.R;
import com.example.codenection2026_package.model.Task;
import com.example.codenection2026_package.ui.onboarding.ThemeController;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * SCREEN 4 - ADD TASK (port of Prototype/add-task.html).
 *
 * <p>A bottom sheet with two mutually exclusive classifications:
 *
 * <ul>
 *   <li><b>INFLEXIBLE</b> - a protected work shift. Blue. Never deferred, so it
 *       shows the weekly 20h cap meter.</li>
 *   <li><b>FLEXIBLE</b> - a sheddable academic task. Mint. Eligible for
 *       auto-deferral, so it shows the cognitive buffer panel.</li>
 * </ul>
 *
 * <p>Flipping modes repaints roughly fifteen elements, exactly like the prototype's
 * selectMode(): both cards, both icon plates, both checks, both guard badges, the
 * header badge and subtitle, the name hint, the category default, the two dynamic
 * panels, the duration colour, the Dino copy and the save button.
 *
 * <p><b>DATA ACCESS IS ISOLATED.</b> AppDatabase is being written by another agent,
 * so saving goes through {@link TaskSource}. The default implementation is a stub
 * that reports failure, and the sheet tells the user so instead of silently losing
 * the task.
 *
 * <p>NEW FILE - additive. Nothing existing is modified.
 */
public class AddTaskSheetFragment extends BottomSheetDialogFragment {

    /**
     * Swap-in point for AppDatabase once it lands.
     *
     * <p>Room wiring goes here: {@code AppDatabase.getInstance(context).taskDao()}
     * supplies both methods. Both calls block, so the real implementation must run
     * them off the main thread.
     */
    private interface TaskSource {
        List<Task> loadAll();

        long save(Task task);
    }

    /** Placeholder source. Nothing is persisted until AppDatabase lands. */
    private static final TaskSource NO_DB_SOURCE = new TaskSource() {
        @Override
        public List<Task> loadAll() {
            return java.util.Collections.emptyList();
        }

        @Override
        public long save(Task task) {
            // TODO Room wiring: replace with AppDatabase.getInstance(context).taskDao().insert(task).
            return -1L;
        }
    };

    /** Tag used by DashboardFragment when it shows this sheet. */
    public static final String TAG = "add_task";

    private static final String MODE_INFLEXIBLE = "inflexible";
    private static final String MODE_FLEXIBLE = "flexible";

    private static final String CLASSIFICATION_INFLEXIBLE = "INFLEXIBLE";
    private static final String CLASSIFICATION_FLEXIBLE = "FLEXIBLE";

    /** Defaults from the prototype: 17:00 to 21:00, a four hour shift. */
    private static final int DEFAULT_START_MINUTES = 17 * 60;
    private static final int DEFAULT_END_MINUTES = 21 * 60;

    private static final int CATEGORY_COUNT = 6;

    private TaskSource taskSource = NO_DB_SOURCE;

    /** "inflexible" or "flexible". The prototype starts on the work card. */
    private String mode = MODE_INFLEXIBLE;

    /** Times held as minutes from midnight so the duration maths is trivial. */
    private int startMinutes = DEFAULT_START_MINUTES;
    private int endMinutes = DEFAULT_END_MINUTES;

    @Nullable
    private View root;

    private EditText taskNameInput;
    private Spinner categorySpinner;
    private TextView titleHint;
    private TextView dateButton;
    private TextView startTimeButton;
    private TextView endTimeButton;
    private TextView durationPill;
    private TextView modalSubtitle;
    private TextView dinoTitle;
    private TextView dinoMessage;
    private FrameLayout modalBadgeIcon;
    private LinearLayout cardInflexible;
    private LinearLayout cardFlexible;
    private FrameLayout iconContainerInflexible;
    private FrameLayout iconContainerFlexible;
    private ImageView iconInflexible;
    private ImageView iconFlexible;
    private FrameLayout checkInflexible;
    private FrameLayout checkFlexible;
    private ImageView checkIconInflexible;
    private ImageView checkIconFlexible;
    private LinearLayout badgeInflexibleGuard;
    private LinearLayout badgeFlexibleGuard;
    private LinearLayout panelWorkCap;
    private LinearLayout panelCognitiveBuffer;

    public AddTaskSheetFragment() {
        super(R.layout.sheet_add_task);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_add_task, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        root = view;

        bindViews(view);
        setupCategorySpinner();
        setupTimes();
        setupActions(view);

        ThemeController.bind(view, R.id.themeToggleButton, R.id.themeToggleIcon);

        ImageView coach = view.findViewById(R.id.dinoCoachAvatar);
        if (coach != null) {
            // Animated GIF asset, so it must go through Glide.
            Glide.with(this).load(R.drawable.dino_happy).into(coach);
        }

        // Paint the starting state, then let the theme toggle repaint it again.
        selectMode(mode);
    }

    @Override
    public void onDestroyView() {
        root = null;
        super.onDestroyView();
    }

    // ==================================================================
    // Wiring
    // ==================================================================

    private void bindViews(@NonNull View view) {
        taskNameInput = view.findViewById(R.id.taskNameInput);
        categorySpinner = view.findViewById(R.id.categorySpinner);
        titleHint = view.findViewById(R.id.titleHint);
        dateButton = view.findViewById(R.id.dateButton);
        startTimeButton = view.findViewById(R.id.startTimeButton);
        endTimeButton = view.findViewById(R.id.endTimeButton);
        durationPill = view.findViewById(R.id.durationPill);
        modalSubtitle = view.findViewById(R.id.modalSubtitle);
        dinoTitle = view.findViewById(R.id.dinoTitle);
        dinoMessage = view.findViewById(R.id.dinoMessage);
        modalBadgeIcon = view.findViewById(R.id.modalBadgeIcon);
        cardInflexible = view.findViewById(R.id.cardInflexible);
        cardFlexible = view.findViewById(R.id.cardFlexible);
        iconContainerInflexible = view.findViewById(R.id.iconContainerInflexible);
        iconContainerFlexible = view.findViewById(R.id.iconContainerFlexible);
        iconInflexible = view.findViewById(R.id.iconInflexible);
        iconFlexible = view.findViewById(R.id.iconFlexible);
        checkInflexible = view.findViewById(R.id.checkInflexible);
        checkFlexible = view.findViewById(R.id.checkFlexible);
        checkIconInflexible = view.findViewById(R.id.checkIconInflexible);
        checkIconFlexible = view.findViewById(R.id.checkIconFlexible);
        badgeInflexibleGuard = view.findViewById(R.id.badgeInflexibleGuard);
        badgeFlexibleGuard = view.findViewById(R.id.badgeFlexibleGuard);
        panelWorkCap = view.findViewById(R.id.panelWorkCap);
        panelCognitiveBuffer = view.findViewById(R.id.panelCognitiveBuffer);
    }

    private void setupCategorySpinner() {
        if (categorySpinner == null) {
            return;
        }

        String[] categories = new String[CATEGORY_COUNT];
        categories[0] = getString(R.string.cat_employment);
        categories[1] = getString(R.string.cat_neuro201);
        categories[2] = getString(R.string.cat_cs101);
        categories[3] = getString(R.string.cat_econ102);
        categories[4] = getString(R.string.cat_phys210);
        categories[5] = getString(R.string.cat_personal);

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                requireContext(), R.layout.item_spinner_selected, categories) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                return styleRow(super.getView(position, convertView, parent));
            }

            @NonNull
            @Override
            public View getDropDownView(int position, @Nullable View convertView,
                                        @NonNull ViewGroup parent) {
                return styleRow(super.getDropDownView(position, convertView, parent));
            }

            private View styleRow(@NonNull View row) {
                if (row instanceof TextView) {
                    TextView text = (TextView) row;
                    text.setTextColor(ContextCompat.getColor(
                            requireContext(), R.color.text_primary_dark));
                    text.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                            getResources().getDimension(R.dimen.text_body));
                }
                return row;
            }
        };
        adapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        categorySpinner.setAdapter(adapter);
    }

    private void setupTimes() {
        renderTime(startTimeButton, startMinutes);
        renderTime(endTimeButton, endMinutes);
        updateDuration();
        renderDate(todayAsIso());
    }

    private void setupActions(@NonNull View view) {
        click(view, R.id.cardInflexible, () -> selectMode(MODE_INFLEXIBLE));
        click(view, R.id.cardFlexible, () -> selectMode(MODE_FLEXIBLE));

        click(view, R.id.sheetClose, this::dismiss);
        click(view, R.id.cancelButton, this::dismiss);

        click(view, R.id.dictateButton, () ->
                Toast.makeText(requireContext(), R.string.voice_unavailable, Toast.LENGTH_SHORT).show());

        click(view, R.id.startTimeButton, () -> pickTime(true));
        click(view, R.id.endTimeButton, () -> pickTime(false));

        click(view, R.id.saveTaskButton, this::saveTask);
    }

    // ==================================================================
    // Mode selection (the prototype's selectMode)
    // ==================================================================

    /**
     * Repaints every element that depends on the classification.
     *
     * @param next {@link #MODE_INFLEXIBLE} or {@link #MODE_FLEXIBLE}
     */
    private void selectMode(@NonNull String next) {
        mode = next;
        boolean flexible = MODE_FLEXIBLE.equals(next);

        if (cardFlexible != null) {
            cardFlexible.setBackgroundResource(flexible
                    ? R.drawable.bg_mode_card_acad
                    : R.drawable.bg_mode_card_idle);
            cardFlexible.setAlpha(flexible ? 1f : 0.8f);
        }
        if (cardInflexible != null) {
            cardInflexible.setBackgroundResource(flexible
                    ? R.drawable.bg_mode_card_idle
                    : R.drawable.bg_mode_card_work);
            cardInflexible.setAlpha(flexible ? 0.6f : 1f);
        }

        // Active card: solid plate and a tick. Inactive card: idle plate, empty radio.
        if (iconContainerFlexible != null) {
            iconContainerFlexible.setBackgroundResource(flexible
                    ? R.drawable.bg_mode_icon_acad
                    : R.drawable.bg_mode_icon_idle);
        }
        if (iconFlexible != null) {
            tint(iconFlexible, flexible ? R.color.bg_dark : R.color.text_muted_dark);
        }
        if (checkFlexible != null) {
            checkFlexible.setBackgroundResource(flexible
                    ? R.drawable.bg_mode_icon_acad
                    : R.drawable.bg_mode_icon_idle);
        }
        if (checkIconFlexible != null) {
            checkIconFlexible.setImageResource(flexible
                    ? R.drawable.ic_check
                    : R.drawable.ic_radio_unchecked);
            tint(checkIconFlexible, flexible ? R.color.bg_dark : R.color.text_muted_dark);
        }

        if (iconContainerInflexible != null) {
            iconContainerInflexible.setBackgroundResource(flexible
                    ? R.drawable.bg_mode_icon_idle
                    : R.drawable.bg_mode_icon_work);
        }
        if (iconInflexible != null) {
            tint(iconInflexible, flexible ? R.color.text_muted_dark : R.color.bg_dark);
        }
        if (checkInflexible != null) {
            checkInflexible.setBackgroundResource(flexible
                    ? R.drawable.bg_mode_icon_idle
                    : R.drawable.bg_mode_icon_work);
        }
        if (checkIconInflexible != null) {
            checkIconInflexible.setImageResource(flexible
                    ? R.drawable.ic_radio_unchecked
                    : R.drawable.ic_check);
            tint(checkIconInflexible, flexible ? R.color.text_muted_dark : R.color.bg_dark);
        }

        show(badgeFlexibleGuard, flexible);
        show(badgeInflexibleGuard, !flexible);

        // Header badge: a school glyph for academic work, a work glyph for shifts.
        if (modalBadgeIcon != null) {
            modalBadgeIcon.setBackgroundResource(flexible
                    ? R.drawable.bg_mode_icon_acad
                    : R.drawable.bg_mode_icon_work);
        }
        ImageView glyph = root == null ? null : root.findViewById(R.id.modalBadgeGlyph);
        if (glyph != null) {
            glyph.setImageResource(flexible ? R.drawable.ic_school : R.drawable.ic_work);
            tint(glyph, flexible ? R.color.bg_dark : R.color.bg_dark);
        }
        if (modalSubtitle != null) {
            modalSubtitle.setText(flexible
                    ? R.string.addtask_subtitle_academic
                    : R.string.addtask_subtitle_work);
            modalSubtitle.setTextColor(color(flexible
                    ? R.color.brand_mint
                    : R.color.secondary_blue));
        }

        // Task name hint and default copy, exactly as the prototype swaps its value.
        if (titleHint != null) {
            titleHint.setText(flexible
                    ? R.string.addtask_hint_sheddable
                    : R.string.addtask_hint_locked);
            titleHint.setTextColor(color(flexible
                    ? R.color.brand_mint
                    : R.color.secondary_blue));
        }
        if (taskNameInput != null) {
            String academic = getString(R.string.addtask_academic_default);
            String work = getString(R.string.addtask_work_default);
            String current = taskNameInput.getText().toString();
            if (current.trim().isEmpty() || current.equals(academic) || current.equals(work)) {
                taskNameInput.setText(flexible ? academic : work);
            }
            taskNameInput.setHint(flexible
                    ? R.string.addtask_name_hint_academic
                    : R.string.addtask_name_hint_work);
        }
        if (categorySpinner != null) {
            // Prototype picks NEUR 201 in academic mode and Employment in work mode.
            categorySpinner.setSelection(flexible ? 1 : 0);
        }

        show(panelCognitiveBuffer, flexible);
        show(panelWorkCap, !flexible);

        if (durationPill != null) {
            durationPill.setTextColor(color(flexible
                    ? R.color.brand_mint
                    : R.color.secondary_blue));
        }
        if (endTimeButton != null) {
            endTimeButton.setTextColor(color(flexible
                    ? R.color.brand_mint
                    : R.color.secondary_blue));
        }

        if (dinoTitle != null) {
            dinoTitle.setText(flexible
                    ? R.string.addtask_dino_scheduler_label
                    : R.string.addtask_dino_guard_label);
        }
        if (dinoMessage != null) {
            dinoMessage.setText(flexible
                    ? R.string.addtask_dino_scheduler_speech
                    : R.string.addtask_dino_guard_speech);
        }

        View save = root == null ? null : root.findViewById(R.id.saveTaskButton);
        if (save instanceof MaterialButton) {
            MaterialButton button = (MaterialButton) save;
            button.setText(flexible
                    ? R.string.addtask_save_academic
                    : R.string.addtask_save_work);
            button.setIconResource(flexible
                    ? R.drawable.ic_event_available
                    : R.drawable.ic_shield);
        }
    }

    // ==================================================================
    // Date, time and duration
    // ==================================================================

    private void pickTime(boolean isStart) {
        int current = isStart ? startMinutes : endMinutes;
        TimePickerDialog dialog = new TimePickerDialog(
                requireContext(),
                (picker, hour, minute) -> {
                    if (isStart) {
                        startMinutes = hour * 60 + minute;
                        renderTime(startTimeButton, startMinutes);
                    } else {
                        endMinutes = hour * 60 + minute;
                        renderTime(endTimeButton, endMinutes);
                    }
                    updateDuration();
                },
                current / 60,
                current % 60,
                true);
        dialog.show();
    }

    private void renderTime(@Nullable TextView target, int minutes) {
        if (target != null) {
            target.setText(formatMinutes(minutes));
        }
    }

    /**
     * Duration in hours to one decimal, wrapping past midnight the way the
     * prototype's updateDuration() does (a negative difference gains 24 hours).
     */
    private void updateDuration() {
        if (durationPill == null) {
            return;
        }
        double hours = (endMinutes - startMinutes) / 60.0;
        if (hours < 0) {
            hours += 24.0;
        }
        durationPill.setText(getString(
                R.string.addtask_duration_hours,
                String.format(Locale.US, "%.1f", hours)));
    }

    private void renderDate(@NonNull String isoDate) {
        if (dateButton != null) {
            dateButton.setText(isoDate);
        }
    }

    @NonNull
    private static String formatMinutes(int minutes) {
        int wrapped = ((minutes % 1440) + 1440) % 1440;
        return String.format(Locale.US, "%02d:%02d", wrapped / 60, wrapped % 60);
    }

    @NonNull
    private static String todayAsIso() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        return format.format(new Date());
    }

    // ==================================================================
    // Save
    // ==================================================================

    private void saveTask() {
        boolean flexible = MODE_FLEXIBLE.equals(mode);

        String name = taskNameInput == null ? "" : taskNameInput.getText().toString().trim();
        if (name.isEmpty()) {
            // Fall back to the mode's example name rather than saving a blank row.
            name = getString(flexible
                    ? R.string.addtask_academic_default
                    : R.string.addtask_work_default);
        }

        Task task = new Task(
                flexible ? CLASSIFICATION_FLEXIBLE : CLASSIFICATION_INFLEXIBLE,
                name,
                null,
                todayAsIso(),
                formatMinutes(startMinutes),
                formatMinutes(endMinutes));

        long id = taskSource.save(task);

        if (id < 0) {
            // The stub source: nothing was persisted, so say that rather than lie.
            Toast.makeText(requireContext(), R.string.addtask_save_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(requireContext(), flexible
                ? R.string.addtask_saved_academic
                : R.string.addtask_saved_work, Toast.LENGTH_SHORT).show();
        dismiss();
    }

    // ==================================================================
    // Small helpers
    // ==================================================================

    private int color(@ColorRes int colorRes) {
        return ContextCompat.getColor(requireContext(), colorRes);
    }

    private void tint(@NonNull ImageView view, @ColorRes int colorRes) {
        view.setImageTintList(android.content.res.ColorStateList.valueOf(color(colorRes)));
    }

    private static void show(@Nullable View view, boolean visible) {
        if (view != null) {
            view.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private static void click(@NonNull View root, int id, @NonNull Runnable action) {
        View target = root.findViewById(id);
        if (target != null) {
            target.setOnClickListener(v -> action.run());
        }
    }
}
