package com.example.codenection2026_package.ui.addtask;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
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
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.example.codenection2026_package.R;
import com.example.codenection2026_package.data.CategoryRepository;
import com.example.codenection2026_package.data.TaskRepository;
import com.example.codenection2026_package.model.Task;
import com.example.codenection2026_package.ui.onboarding.ThemeController;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * SCREEN 4 - ADD TASK (port of Prototype/add-task.html).
 *
 * <p>A bottom sheet with two mutually exclusive classifications:
 *
 * <ul>
 *   <li><b>INFLEXIBLE</b> - a protected work shift. Blue. Never deferred, so it
 *       shows the weekly 20h cap meter.</li>
 *   <li><b>FLEXIBLE</b> - a sheddable task. Mint. Eligible for auto-deferral, so it
 *       shows the cognitive buffer panel.</li>
 * </ul>
 *
 * <p>Flipping modes repaints roughly fifteen elements, exactly like the prototype's
 * selectMode(): both cards, both icon plates, both checks, both guard badges, the
 * header badge and subtitle, the name hint, the two dynamic panels, the duration
 * colour, the Dino copy and the save button.
 *
 * <p><b>The category drives the classification.</b> The five baseline categories
 * (Academic, Work, Errand, Social, Co-curricular) are not decoration: picking Work
 * flips the sheet to the protected shift card, and picking anything else leaves it
 * sheddable. The card's tag, its subtitle and the save button all name the category
 * the user actually chose, so a Social task never announces itself as academic.
 *
 * <p><b>Priority and deferral are the user's call.</b> The scheduler refuses to shed a
 * HIGH task, and it will not push a task further than the deferral window the user
 * allowed, so both are captured here and stored on the row
 * ({@link Task#getPriority()}, {@link Task#getDeferralHours()}).
 *
 * <p>Saving goes through {@link TaskRepository}, which owns the background thread
 * Room requires, and reports back on the main thread. A null row id becomes the
 * "could not save" toast rather than a silent loss.
 */
public class AddTaskSheetFragment extends BottomSheetDialogFragment {

    /** Tag used by DashboardFragment when it shows this sheet. */
    public static final String TAG = "add_task";

    private static final String MODE_INFLEXIBLE = "inflexible";
    private static final String MODE_FLEXIBLE = "flexible";

    private static final String CLASSIFICATION_INFLEXIBLE = "INFLEXIBLE";
    private static final String CLASSIFICATION_FLEXIBLE = "FLEXIBLE";

    private static final String ISO_PATTERN = "yyyy-MM-dd";

    /** Defaults from the prototype: 17:00 to 21:00, a four hour shift. */
    private static final int DEFAULT_START_MINUTES = 17 * 60;
    private static final int DEFAULT_END_MINUTES = 21 * 60;

    /**
     * The deferral windows the user can allow, in hours, paired one-to-one with the
     * labels built in {@link #deferralLabels()}. 0 means "never defer this".
     */
    private static final int[] DEFERRAL_HOURS = {0, 12, 24, 48, 72};

    /** The prototype's "48h max", which is also {@link Task#DEFAULT_DEFERRAL_HOURS}. */
    private static final int DEFAULT_DEFERRAL_INDEX = 3;

    /** Tells the dashboard a row landed, so it can refresh the day it belongs to. */
    public interface OnTaskSavedListener {
        void onTaskSaved(@NonNull String isoDate);
    }

    @Nullable
    private OnTaskSavedListener onTaskSavedListener;

    /** "inflexible" or "flexible". The prototype starts on the work card. */
    private String mode = MODE_INFLEXIBLE;

    /** Times held as minutes from midnight so the duration maths is trivial. */
    private int startMinutes = DEFAULT_START_MINUTES;
    private int endMinutes = DEFAULT_END_MINUTES;

    /** The day the task is filed under, held as the ISO string the DAO queries on. */
    @NonNull
    private String dateIso = todayAsIso();

    /**
     * Set while the fragment moves the spinner itself. Without it, choosing a
     * classification would come straight back through the item listener and undo
     * the choice.
     */
    private boolean suppressCategoryCallback;

    /** Guards the save button against a double tap while the insert is in flight. */
    private boolean saving;

    @Nullable
    private View root;

    private EditText taskNameInput;
    private Spinner categorySpinner;
    private Spinner deferralSpinner;
    private MaterialButtonToggleGroup priorityGroup;
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

    /** Lets the dashboard refresh the feed once a row is written. */
    public void setOnTaskSavedListener(@Nullable OnTaskSavedListener listener) {
        this.onTaskSavedListener = listener;
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
        setupDeferralSpinner();
        setupPriority();
        setupTimes();
        setupActions(view);

        ThemeController.bind(view, R.id.themeToggleButton, R.id.themeToggleIcon);

        ImageView coach = view.findViewById(R.id.dinoCoachAvatar);
        if (coach != null) {
            // Animated GIF asset, so it must go through Glide.
            Glide.with(this).load(R.drawable.dino_happy).into(coach);
        }

        // Paint the starting state, then let the theme toggle repaint it again.
        applyMode();
    }

    @Override
    public void onDestroyView() {
        root = null;
        taskNameInput = null;
        categorySpinner = null;
        deferralSpinner = null;
        priorityGroup = null;
        titleHint = null;
        dateButton = null;
        startTimeButton = null;
        endTimeButton = null;
        durationPill = null;
        modalSubtitle = null;
        dinoTitle = null;
        dinoMessage = null;
        modalBadgeIcon = null;
        cardInflexible = null;
        cardFlexible = null;
        iconContainerInflexible = null;
        iconContainerFlexible = null;
        iconInflexible = null;
        iconFlexible = null;
        checkInflexible = null;
        checkFlexible = null;
        checkIconInflexible = null;
        checkIconFlexible = null;
        badgeInflexibleGuard = null;
        badgeFlexibleGuard = null;
        panelWorkCap = null;
        panelCognitiveBuffer = null;
        super.onDestroyView();
    }

    // ==================================================================
    // Wiring
    // ==================================================================

    private void bindViews(@NonNull View view) {
        taskNameInput = view.findViewById(R.id.taskNameInput);
        categorySpinner = view.findViewById(R.id.categorySpinner);
        deferralSpinner = view.findViewById(R.id.deferralSpinner);
        priorityGroup = view.findViewById(R.id.priorityGroup);
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

    /**
     * Fills @id/categorySpinner with the five baseline categories.
     *
     * <p>The labels are localised, but the value that gets stored is the canonical
     * name from {@link CategoryRepository}, indexed by the same position. Those two
     * lists must stay in the same order.
     */
    private void setupCategorySpinner() {
        if (categorySpinner == null) {
            return;
        }

        String[] labels = {
                getString(R.string.cat_academic),
                getString(R.string.cat_work),
                getString(R.string.cat_errand),
                getString(R.string.cat_social),
                getString(R.string.cat_cocurricular)
        };

        categorySpinner.setAdapter(styledAdapter(labels));
        categorySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (suppressCategoryCallback) {
                    return;
                }
                // Work is the only protected category; every other one stays
                // sheddable. Applying the mode again relabels the card, the subtitle
                // and the save button with the category the user just picked.
                mode = isWorkCategory(position) ? MODE_INFLEXIBLE : MODE_FLEXIBLE;
                applyMode();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // The spinner always has a selection, so there is nothing to restore.
            }
        });
    }

    /** The deferral windows the scheduler is allowed to use for this task. */
    private void setupDeferralSpinner() {
        if (deferralSpinner == null) {
            return;
        }

        deferralSpinner.setAdapter(styledAdapter(deferralLabels()));
        deferralSpinner.setSelection(DEFAULT_DEFERRAL_INDEX);
        deferralSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // The Dino line repeats whichever window is selected.
                updateDinoCopy();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Nothing to do: a selection always exists.
            }
        });
    }

    /**
     * Restores the checked priority segment.
     *
     * <p>sheet_add_task.xml already marks "Med" checked; this is the belt-and-braces
     * case where the toggle group lost the state across a configuration change.
     */
    private void setupPriority() {
        if (priorityGroup == null) {
            return;
        }
        if (priorityGroup.getCheckedButtonId() == View.NO_ID) {
            priorityGroup.check(R.id.priorityMed);
        }
    }

    private void setupTimes() {
        renderTime(startTimeButton, startMinutes);
        renderTime(endTimeButton, endMinutes);
        updateDuration();
        renderDate(dateIso);
    }

    private void setupActions(@NonNull View view) {
        click(view, R.id.cardInflexible, () -> selectMode(MODE_INFLEXIBLE));
        click(view, R.id.cardFlexible, () -> selectMode(MODE_FLEXIBLE));

        click(view, R.id.sheetClose, this::dismiss);
        click(view, R.id.cancelButton, this::dismiss);

        click(view, R.id.dictateButton, () ->
                Toast.makeText(requireContext(), R.string.voice_unavailable, Toast.LENGTH_SHORT).show());

        click(view, R.id.dateButton, this::pickDate);
        click(view, R.id.startTimeButton, () -> pickTime(true));
        click(view, R.id.endTimeButton, () -> pickTime(false));

        click(view, R.id.saveTaskButton, this::saveTask);
    }

    /** A Spinner row styled for both the closed field and the open popup. */
    @NonNull
    private ArrayAdapter<String> styledAdapter(@NonNull String[] values) {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                requireContext(), R.layout.item_spinner_selected, values) {
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
        return adapter;
    }

    // ==================================================================
    // Category, classification and mode selection
    // ==================================================================

    /** @return the canonical baseline name for the current spinner position */
    @Nullable
    private String currentCategory() {
        int position = categorySpinner == null ? 0 : categorySpinner.getSelectedItemPosition();
        return CategoryRepository.baselineAt(position);
    }

    private boolean isWorkCategory(int spinnerPosition) {
        return CategoryRepository.WORK.equals(CategoryRepository.baselineAt(spinnerPosition));
    }

    /** Moves the spinner without letting the item listener fight the change. */
    private void setCategorySelection(int position) {
        if (categorySpinner == null || position < 0) {
            return;
        }
        suppressCategoryCallback = true;
        categorySpinner.setSelection(position);
        suppressCategoryCallback = false;
    }

    /**
     * Mode card tap.
     *
     * <p>Choosing a classification carries its natural category with it - a protected
     * shift is Work, and a sheddable task defaults back to Academic only if it was
     * sitting on Work. A task already filed under Social or Co-curricular keeps that
     * category, because those are sheddable too.
     *
     * @param next {@link #MODE_INFLEXIBLE} or {@link #MODE_FLEXIBLE}
     */
    private void selectMode(@NonNull String next) {
        boolean flexible = MODE_FLEXIBLE.equals(next);
        int position = categorySpinner == null ? -1 : categorySpinner.getSelectedItemPosition();
        boolean onWork = isWorkCategory(position);

        if (flexible && onWork) {
            setCategorySelection(CategoryRepository.indexOf(CategoryRepository.ACADEMIC));
        } else if (!flexible && !onWork) {
            setCategorySelection(CategoryRepository.indexOf(CategoryRepository.WORK));
        }

        mode = flexible ? MODE_FLEXIBLE : MODE_INFLEXIBLE;
        applyMode();
    }

    /** Repaints every element that depends on the classification and the category. */
    private void applyMode() {
        boolean flexible = MODE_FLEXIBLE.equals(mode);
        String category = currentCategory();
        if (category == null) {
            category = getString(R.string.cat_academic);
        }

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
            tint(iconFlexible, flexible ? R.color.on_brand : R.color.text_muted_dark);
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
            tint(checkIconFlexible, flexible ? R.color.on_brand : R.color.text_muted_dark);
        }

        if (iconContainerInflexible != null) {
            iconContainerInflexible.setBackgroundResource(flexible
                    ? R.drawable.bg_mode_icon_idle
                    : R.drawable.bg_mode_icon_work);
        }
        if (iconInflexible != null) {
            tint(iconInflexible, flexible ? R.color.text_muted_dark : R.color.on_brand);
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
            tint(checkIconInflexible, flexible ? R.color.text_muted_dark : R.color.on_brand);
        }

        show(badgeFlexibleGuard, flexible);
        show(badgeInflexibleGuard, !flexible);

        // The flexible card wears the category the user picked, not a hardcoded
        // "Academic": a Social task has to say so on its own card.
        TextView flexibleTag = root == null ? null : root.findViewById(R.id.tagFlexible);
        if (flexibleTag != null && flexible) {
            flexibleTag.setText(category);
        }

        // Header badge: a school glyph for sheddable work, a work glyph for shifts.
        if (modalBadgeIcon != null) {
            modalBadgeIcon.setBackgroundResource(flexible
                    ? R.drawable.bg_mode_icon_acad
                    : R.drawable.bg_mode_icon_work);
        }
        ImageView glyph = root == null ? null : root.findViewById(R.id.modalBadgeGlyph);
        if (glyph != null) {
            glyph.setImageResource(flexible ? R.drawable.ic_school : R.drawable.ic_work);
            tint(glyph, R.color.on_brand);
        }
        if (modalSubtitle != null) {
            modalSubtitle.setText(flexible
                    ? getString(R.string.addtask_subtitle_flexible_category, category)
                    : getString(R.string.addtask_subtitle_work));
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
            String current = taskNameInput.getText().toString().trim();
            if (current.isEmpty() || current.equals(academic) || current.equals(work)) {
                taskNameInput.setText(defaultNameFor(category));
            }
            taskNameInput.setHint(hintFor(category));
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

        View save = root == null ? null : root.findViewById(R.id.saveTaskButton);
        if (save instanceof MaterialButton) {
            MaterialButton button = (MaterialButton) save;
            button.setText(flexible
                    ? getString(R.string.addtask_save_category, category)
                    : getString(R.string.addtask_save_work));
            button.setIconResource(flexible
                    ? R.drawable.ic_event_available
                    : R.drawable.ic_shield);
        }

        updateDinoCopy();
    }

    /**
     * The Dino line under the sheet. It repeats the deferral window and priority the
     * user just chose, so the sheet visibly reads the same values it is about to save.
     */
    private void updateDinoCopy() {
        boolean flexible = MODE_FLEXIBLE.equals(mode);

        if (dinoTitle != null) {
            dinoTitle.setText(flexible
                    ? R.string.addtask_dino_scheduler_label
                    : R.string.addtask_dino_guard_label);
        }
        if (dinoMessage == null) {
            return;
        }
        if (!flexible) {
            dinoMessage.setText(R.string.addtask_dino_guard_speech);
            return;
        }
        dinoMessage.setText(getString(
                R.string.addtask_dino_scheduler_speech_fmt,
                deferralSummary(),
                getString(priorityLabelRes())));
    }

    /** The example name each category starts with, or "" when it has none. */
    @NonNull
    private String defaultNameFor(@Nullable String category) {
        if (CategoryRepository.WORK.equals(category)) {
            return getString(R.string.addtask_work_default);
        }
        if (CategoryRepository.ACADEMIC.equals(category)) {
            return getString(R.string.addtask_academic_default);
        }
        return "";
    }

    @StringRes
    private int hintFor(@Nullable String category) {
        if (CategoryRepository.WORK.equals(category)) {
            return R.string.addtask_name_hint_work;
        }
        if (CategoryRepository.ACADEMIC.equals(category)) {
            return R.string.addtask_name_hint_academic;
        }
        return R.string.addtask_name_hint_generic;
    }

    // ==================================================================
    // Priority and deferral
    // ==================================================================

    /** @return "HIGH", "MED" or "LOW" - the value written to the row */
    @NonNull
    private String selectedPriority() {
        int checked = priorityGroup == null ? View.NO_ID : priorityGroup.getCheckedButtonId();
        if (checked == R.id.priorityHigh) {
            return Task.PRIORITY_HIGH;
        }
        if (checked == R.id.priorityLow) {
            return Task.PRIORITY_LOW;
        }
        return Task.PRIORITY_MED;
    }

    @StringRes
    private int priorityLabelRes() {
        int checked = priorityGroup == null ? View.NO_ID : priorityGroup.getCheckedButtonId();
        if (checked == R.id.priorityHigh) {
            return R.string.addtask_priority_high;
        }
        if (checked == R.id.priorityLow) {
            return R.string.addtask_priority_low;
        }
        return R.string.addtask_priority_med;
    }

    /** @return the deferral window index, clamped to the table in this class */
    private int deferralIndex() {
        int position = deferralSpinner == null
                ? DEFAULT_DEFERRAL_INDEX
                : deferralSpinner.getSelectedItemPosition();
        if (position < 0 || position >= DEFERRAL_HOURS.length) {
            return DEFAULT_DEFERRAL_INDEX;
        }
        return position;
    }

    /** @return the deferral window in hours: 0 when the task may not be deferred */
    private int selectedDeferralHours() {
        return DEFERRAL_HOURS[deferralIndex()];
    }

    @NonNull
    private String deferralSummary() {
        int hours = selectedDeferralHours();
        return hours == 0
                ? getString(R.string.addtask_deferral_summary_none)
                : getString(R.string.addtask_deferral_summary_hours, hours);
    }

    @NonNull
    private String[] deferralLabels() {
        return new String[]{
                getString(R.string.addtask_deferral_none),
                getString(R.string.addtask_deferral_12),
                getString(R.string.addtask_deferral_24),
                getString(R.string.addtask_deferral_48),
                getString(R.string.addtask_deferral_72)
        };
    }

    // ==================================================================
    // Date, time and duration
    // ==================================================================

    private void pickDate() {
        Calendar calendar = Calendar.getInstance();
        try {
            Date parsed = new SimpleDateFormat(ISO_PATTERN, Locale.US).parse(dateIso);
            if (parsed != null) {
                calendar.setTime(parsed);
            }
        } catch (ParseException ignored) {
            // The field only ever holds an ISO date, so this cannot really happen;
            // falling back to today is still better than refusing to open the picker.
        }

        DatePickerDialog dialog = new DatePickerDialog(
                requireContext(),
                (picker, year, month, day) -> {
                    Calendar picked = Calendar.getInstance();
                    picked.set(year, month, day);
                    dateIso = new SimpleDateFormat(ISO_PATTERN, Locale.US).format(picked.getTime());
                    renderDate(dateIso);
                    // The saved date changes with it, so the Dino line stays truthful.
                    updateDinoCopy();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH));
        dialog.show();
    }

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
        SimpleDateFormat format = new SimpleDateFormat(ISO_PATTERN, Locale.US);
        return format.format(new Date());
    }

    // ==================================================================
    // Save
    // ==================================================================

    private void saveTask() {
        if (saving) {
            return;
        }

        boolean flexible = MODE_FLEXIBLE.equals(mode);
        String category = currentCategory();
        if (category == null) {
            category = CategoryRepository.ACADEMIC;
        }

        String name = taskNameInput == null ? "" : taskNameInput.getText().toString().trim();
        if (name.isEmpty()) {
            // Fall back to the category's example name rather than saving a blank row.
            name = defaultNameFor(category);
            if (name.isEmpty()) {
                Toast.makeText(requireContext(), R.string.addtask_name_required, Toast.LENGTH_SHORT).show();
                return;
            }
        }

        Task task = new Task(
                flexible ? CLASSIFICATION_FLEXIBLE : CLASSIFICATION_INFLEXIBLE,
                name,
                null,
                dateIso,
                formatMinutes(startMinutes),
                formatMinutes(endMinutes));
        task.setPriority(selectedPriority());
        // A protected shift can never be deferred, so its window is zero whatever the
        // spinner says: the scheduler reads the row, not the screen.
        task.setDeferralHours(flexible ? selectedDeferralHours() : 0);

        Context appContext = requireContext().getApplicationContext();
        setSaving(true);

        final String savedCategory = category;
        TaskRepository.save(appContext, task, category, rowId -> {
            // Room needs a background thread, so this arrives after the sheet may
            // already be gone. isAdded() guards the views and the toasts.
            if (!isAdded()) {
                return;
            }
            setSaving(false);

            if (rowId == null || rowId <= 0) {
                Toast.makeText(requireContext(), R.string.addtask_save_failed, Toast.LENGTH_SHORT).show();
                return;
            }

            Toast.makeText(requireContext(),
                    savedMessage(flexible, savedCategory),
                    Toast.LENGTH_SHORT).show();

            if (onTaskSavedListener != null) {
                onTaskSavedListener.onTaskSaved(task.getDate());
            }
            dismiss();
        });
    }

    @NonNull
    private String savedMessage(boolean flexible, @NonNull String category) {
        if (!flexible) {
            return getString(R.string.addtask_saved_work);
        }
        if (CategoryRepository.ACADEMIC.equals(category)) {
            return getString(R.string.addtask_saved_academic);
        }
        return getString(R.string.addtask_saved_category, category);
    }

    /** Locks the save button while the insert is in flight. */
    private void setSaving(boolean inFlight) {
        saving = inFlight;
        View save = root == null ? null : root.findViewById(R.id.saveTaskButton);
        if (save != null) {
            save.setEnabled(!inFlight);
            save.setAlpha(inFlight ? 0.6f : 1f);
        }
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
