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
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.example.codenection2026_package.R;
import com.example.codenection2026_package.api.CalendarManager;
import com.example.codenection2026_package.api.VoiceManager;
import com.example.codenection2026_package.data.CategoryRepository;
import com.example.codenection2026_package.data.TaskRepository;
import com.example.codenection2026_package.model.CoachVoice;
import com.example.codenection2026_package.model.Task;
import com.example.codenection2026_package.model.ToneType;
import com.example.codenection2026_package.ui.onboarding.LoadZones;
import com.example.codenection2026_package.ui.onboarding.OnboardingPrefs;
import com.example.codenection2026_package.ui.onboarding.ThemeController;
import com.example.codenection2026_package.ui.shell.ToneCopy;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.progressindicator.LinearProgressIndicator;

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

    private CalendarManager calendarManager;

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
     * Removed 12h deferral as Ml moves tasks in 24h block.
     */
    private static final int[] DEFERRAL_HOURS = {0, 24, 48, 72};

    /** The prototype's "48h max", which is also {@link Task#DEFAULT_DEFERRAL_HOURS}. */
    private static final int DEFAULT_DEFERRAL_INDEX = 2;
    private VoiceManager voiceManager;

    /** Tells the dashboard a row landed, so it can refresh the day it belongs to. */
    public interface OnTaskSavedListener {
        void onTaskSaved(@NonNull String isoDate);
    }

    /**
     * Tells the dashboard a row was edited or removed, so it can repaint.
     *
     * <p>Separate from {@link OnTaskSavedListener}: that one is only ever wired for the Add
     * flow, and a delete has to refresh the feed too.
     */
    public interface OnTaskChangedListener {
        void onTaskChanged(@NonNull String isoDate);
    }

    @Nullable
    private OnTaskSavedListener onTaskSavedListener;

    @Nullable
    private OnTaskChangedListener onTaskChangedListener;

    /**
     * The row being edited, or null in the Add flow.
     *
     * <p>Editing is not a separate sheet implementation: the two flows collect exactly the
     * same fields into exactly the same layout, so duplicating a thousand lines of form
     * handling to change three of them would guarantee the two drift apart. Everything below
     * that reads this field is inert while it is null, which is why the Add flow behaves
     * byte for byte as it did before.
     */
    @Nullable
    private Task editingTask;

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

    /** Work minutes already booked in the sheet's week, or -1 until (or if) the read lands. */
    private int bookedWorkMinutes = -1;

    /** The week and excluded row {@link #bookedWorkMinutes} was read for. */
    @Nullable
    private String bookedWorkKey;

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

    /** Lets the dashboard repaint once a row is edited or removed. */
    public void setOnTaskChangedListener(@Nullable OnTaskChangedListener listener) {
        this.onTaskChangedListener = listener;
    }

    // ==================================================================
    // Edit mode
    // ==================================================================

    /**
     * Turns this sheet into the Edit Task sheet for one existing row.
     *
     * <p>Called by {@link com.example.codenection2026_package.ui.edittask.EditTaskSheetFragment}
     * once the views exist. It fills every control from the row, renames the sheet, reveals
     * the delete affordance and repaints, in that order.
     *
     * @param task         a row that came back from a query, so it carries its primary key
     * @param categoryName the canonical category it was filed under, or null for a row that
     *                     predates category filing
     */
    protected void beginEdit(@NonNull Task task, @Nullable String categoryName) {
        editingTask = task;
        mode = CLASSIFICATION_INFLEXIBLE.equals(task.getClassification())
                ? MODE_INFLEXIBLE
                : MODE_FLEXIBLE;

        if (task.getDate() != null) {
            dateIso = task.getDate();
        }
        startMinutes = parseMinutes(task.getStartTime(), DEFAULT_START_MINUTES);
        endMinutes = parseMinutes(task.getEndTime(), DEFAULT_END_MINUTES);

        setCategorySelection(CategoryRepository.indexOf(categoryName));
        selectPriority(task.getPriority());
        selectDeferral(task.getDeferralHours());
        setupTimes();

        // Paint the classification first and write the name last: applyMode() replaces the
        // field when it still holds one of the example names, which is right for the Add flow
        // and would quietly rename a real task here.
        applyMode();
        if (taskNameInput != null) {
            taskNameInput.setText(task.getTaskName());
        }

        TextView title = root == null ? null : root.findViewById(R.id.sheetTitle);
        if (title != null) {
            title.setText(R.string.edittask_title);
        }

        showDeleteAffordance();
    }

    /**
     * Reveals @id/deleteTaskButton, which the layout ships {@code gone} so the Add flow never
     * shows a destructive action on a task that does not exist yet.
     */
    private void showDeleteAffordance() {
        if (root == null) {
            return;
        }
        View delete = root.findViewById(R.id.deleteTaskButton);
        if (delete == null) {
            return;
        }
        delete.setVisibility(View.VISIBLE);
        delete.setOnClickListener(v -> confirmDelete());
    }

    /**
     * Asks before removing the row.
     *
     * <p>A task is cheap to delete and impossible to get back, so a single stray tap next to
     * the close button is not worth the risk.
     */
    private void confirmDelete() {
        if (editingTask == null) {
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setMessage(R.string.edittask_delete_confirm)
                .setPositiveButton(R.string.edittask_delete, (dialog, which) -> deleteTask())
                .setNegativeButton(R.string.addtask_cancel, null)
                .show();
    }

    private void deleteTask() {
        final Task task = editingTask;
        if (task == null) {
            return;
        }
        Context appContext = requireContext().getApplicationContext();
        setSaving(true);

        TaskRepository.delete(appContext, task, success -> {
            if (!isAdded()) {
                return;
            }
            setSaving(false);
            if (!Boolean.TRUE.equals(success)) {
                Toast.makeText(requireContext(), R.string.edittask_delete_failed,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(requireContext(), R.string.edittask_deleted,
                    Toast.LENGTH_SHORT).show();
            if (onTaskChangedListener != null) {
                onTaskChangedListener.onTaskChanged(task.getDate());
            }
            dismiss();
        });
    }

    /**
     * Writes an edit back over the row it came from.
     *
     * <p>The original id and calendar event id are carried across, so the update lands on the
     * same row rather than inserting a second one, and the shift keeps pointing at the
     * calendar entry it already owns. The completed flag is carried too: editing a task is
     * not the user un-ticking it.
     *
     * <p>The category is resolved first because {@link TaskRepository#update} writes the row
     * exactly as handed to it - unlike {@link TaskRepository#save}, it does not look the
     * category up. Without this the edit would quietly unfile the task.
     */
    private void updateExisting(@NonNull Context appContext,
                                @NonNull Task edited,
                                @NonNull String categoryName) {
        final Task original = editingTask;
        if (original == null) {
            return;
        }
        edited.setId(original.getId());
        edited.setCalendarEventId(original.getCalendarEventId());
        edited.setCompleted(original.isCompleted());

        TaskRepository.categoryId(appContext, categoryName, resolved -> {
            if (!isAdded()) {
                return;
            }
            // A failed lookup keeps the category the row already had rather than dropping it.
            edited.setCategory_id(resolved != null ? resolved : original.getCategory_id());

            TaskRepository.update(appContext, edited, success -> {
                if (!isAdded()) {
                    return;
                }
                setSaving(false);
                if (!Boolean.TRUE.equals(success)) {
                    Toast.makeText(requireContext(), R.string.edittask_update_failed,
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                Toast.makeText(requireContext(), R.string.edittask_updated,
                        Toast.LENGTH_SHORT).show();
                if (onTaskChangedListener != null) {
                    onTaskChangedListener.onTaskChanged(edited.getDate());
                }
                dismiss();
            });
        });
    }

    /** "17:00" as minutes from midnight, or {@code fallback} when it will not parse. */
    private static int parseMinutes(@Nullable String hhmm, int fallback) {
        if (hhmm == null) {
            return fallback;
        }
        String[] parts = hhmm.split(":");
        if (parts.length != 2) {
            return fallback;
        }
        try {
            int hours = Integer.parseInt(parts[0].trim());
            int minutes = Integer.parseInt(parts[1].trim());
            if (hours < 0 || hours > 23 || minutes < 0 || minutes > 59) {
                return fallback;
            }
            return (hours * 60) + minutes;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Restores the checked priority segment from the stored "HIGH"/"MED"/"LOW". */
    private void selectPriority(@Nullable String priority) {
        if (priorityGroup == null) {
            return;
        }
        if (Task.PRIORITY_HIGH.equals(priority)) {
            priorityGroup.check(R.id.priorityHigh);
        } else if (Task.PRIORITY_LOW.equals(priority)) {
            priorityGroup.check(R.id.priorityLow);
        } else {
            priorityGroup.check(R.id.priorityMed);
        }
    }

    /** Restores the deferral window, falling back to the default when the row holds one
     *  the spinner does not offer. */
    private void selectDeferral(int hours) {
        if (deferralSpinner == null) {
            return;
        }
        for (int i = 0; i < DEFERRAL_HOURS.length; i++) {
            if (DEFERRAL_HOURS[i] == hours) {
                deferralSpinner.setSelection(i);
                return;
            }
        }
        deferralSpinner.setSelection(DEFAULT_DEFERRAL_INDEX);
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
        calendarManager = new CalendarManager(requireContext());
        root = view;

        bindViews(view);
        bindToneCopy(view);
        setupCategorySpinner();
        setupDeferralSpinner();
        setupPriority();
        setupTimes();
        setupActions(view);
        setupVoice();

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
        if (voiceManager != null) {
            voiceManager.destroy();
            voiceManager = null;
        }

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
    // Coaching tone
    // ==================================================================

    /**
     * Rewrites the sheet's fixed prose in the user's coaching tone.
     *
     * <p>The lines that follow the classification - the header subtitle and the task name
     * hint - are deliberately not here: {@link #applyMode()} already picks between their
     * slots. As in the rest of the shell's binding, a missing id or a non-TextView costs one
     * line of copy rather than the whole sheet.
     */
    private void bindToneCopy(@NonNull View view) {
        // The work badge names the weekly cap, so it takes the real figure rather than
        // going through ToneCopy, which only handles argument-free lines.
        TextView workBadge = view.findViewById(R.id.modeWorkBadge);
        if (workBadge != null) {
            workBadge.setText(getString(CoachVoice.Line.ADDTASK_MODE_WORK_BADGE.pick(tone()),
                    LoadZones.WORK_CAP_HOURS));
        }
        ToneCopy.on(view, tone())
                .set(R.id.modeWorkDesc, CoachVoice.Line.ADDTASK_MODE_WORK_DESC)
                .set(R.id.modeAcademicDesc, CoachVoice.Line.ADDTASK_MODE_ACADEMIC_DESC)
                .set(R.id.modeAcademicBadge, CoachVoice.Line.ADDTASK_MODE_ACADEMIC_BADGE)
                .set(R.id.capFootnote, CoachVoice.Line.ADDTASK_CAP_FOOTNOTE)
                .set(R.id.priorityHint, CoachVoice.Line.ADDTASK_PRIORITY_HINT)
                .set(R.id.deferralHint, CoachVoice.Line.ADDTASK_DEFERRAL_HINT);
    }

    // ==================================================================
    // Voice Dictation
    // ==================================================================

    private void setupVoice() {
        if (!android.speech.SpeechRecognizer.isRecognitionAvailable(requireContext())) {
            return;
        }

        voiceManager = new VoiceManager(requireContext(), new android.speech.RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                if (taskNameInput != null) {
                    taskNameInput.setHint(R.string.addtask_dictation_listening);
                }
            }

            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}

            @Override
            public void onError(int error) {
                if (taskNameInput != null) {
                    taskNameInput.setHint(R.string.addtask_dictation_error);
                }
            }

            @Override
            public void onResults(Bundle results) {
                if (results != null) {
                    java.util.ArrayList<String> matches = results.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty() && taskNameInput != null) {
                        String cleanTitle = com.example.codenection2026_package.api.VoiceInputFormatter.formatTaskTitle(matches.get(0));
                        taskNameInput.setText(cleanTitle);
                        taskNameInput.setSelection(cleanTitle.length());
                    }
                }
            }

            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });
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
                if (suppressCategoryCallback) return;
                // REMOVED: mode = isWorkCategory(position) ? ...
                applyMode(); // Only repaint the tags, not forcing the mode to flip
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
                // "Never move it" turns the movable labels off, and the Dino line repeats
                // whichever window is selected.
                renderMovable();
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
        // The Dino line names what the priority means, so it follows every change.
        priorityGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                updateDinoCopy();
            }
        });
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

        click(view, R.id.dictateButton, () -> {
            if (voiceManager != null) {
                if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    voiceManager.startListening();
                } else {
                    Toast.makeText(requireContext(), R.string.addtask_mic_permission_required, Toast.LENGTH_SHORT).show();
                }
            } else {
                // Same line the Companion screens use for the same condition, so the two
                // cannot drift apart in wording.
                Toast.makeText(requireContext(),
                        CoachVoice.Line.VOICE_UNAVAILABLE.pick(tone()),
                        Toast.LENGTH_SHORT).show();
            }
        });

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

        // THE UI FIX: Update BOTH card badges to match the currently selected category,
        // rather than leaving the inactive one hardcoded to Work/Academic.
        TextView flexibleTag = root == null ? null : root.findViewById(R.id.tagFlexible);
        if (flexibleTag != null) {
            flexibleTag.setText(category);
        }

        TextView inflexibleTag = root == null ? null : root.findViewById(R.id.tagInflexible);
        if (inflexibleTag != null) {
            inflexibleTag.setText(category);
        }

        // Header badge: a school glyph for sheddable work, a work glyph for shifts.
        if (modalBadgeIcon != null) {
            modalBadgeIcon.setBackgroundResource(flexible
                    ? R.drawable.bg_mode_icon_acad
                    : R.drawable.bg_mode_icon_work);
        }
        ImageView glyph = root == null ? null : root.findViewById(R.id.modalBadgeGlyph);
        if (glyph != null) {
            glyph.setImageResource(flexible ? R.drawable.ic_swap_horiz : R.drawable.ic_lock);
            tint(glyph, R.color.on_brand);
        }

        // THE FIX: Restoring CoachVoice and injecting the selected category
        if (modalSubtitle != null) {
            CoachVoice.Line line = flexible
                    ? CoachVoice.Line.ADDTASK_SUBTITLE_FLEXIBLE_CATEGORY
                    : CoachVoice.Line.ADDTASK_SUBTITLE_WORK;
            modalSubtitle.setText(getString(line.pick(tone()), category));
            modalSubtitle.setTextColor(color(flexible
                    ? R.color.brand_mint
                    : R.color.secondary_blue));
        }

        renderMovable();
        if (taskNameInput != null) {
            String academic = getString(R.string.addtask_academic_default);
            String work = getString(R.string.addtask_work_default);
            String current = taskNameInput.getText().toString().trim();
            if (current.isEmpty() || current.equals(academic) || current.equals(work)) {
                taskNameInput.setText(defaultNameFor(category));
            }
            taskNameInput.setHint(hintFor(category));
        }

        // The weekly work panel only means something for a Work entry: a fixed lecture
        // does not count toward work hours.
        boolean work = CategoryRepository.WORK.equals(category);
        show(panelCognitiveBuffer, flexible);
        show(panelWorkCap, !flexible && work);
        // The category decides whether this entry counts toward the week's work.
        refreshWorkCap();

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
            // The Add flow names the category it is about to file under. An edit is not
            // filing anything new, so it just says what the button does.
            button.setText(editingTask != null
                    ? getString(R.string.edittask_save)
                    : (!flexible && work
                            ? getString(R.string.addtask_save_work)
                            : getString(R.string.addtask_save_category, category)));
            button.setIconResource(flexible
                    ? R.drawable.ic_event_available
                    : R.drawable.ic_shield);
        }

        updateDinoCopy();
    }

    /**
     * Whether this entry can be moved: the name hint and the "if your day gets too full"
     * pill. A flexible task set to "Never move it" is as fixed as a shift, so it says so.
     */
    private void renderMovable() {
        boolean movable = MODE_FLEXIBLE.equals(mode) && selectedDeferralHours() > 0;
        if (titleHint != null) {
            titleHint.setText(movable
                    ? CoachVoice.Line.ADDTASK_HINT_SHEDDABLE.pick(tone())
                    : CoachVoice.Line.ADDTASK_HINT_LOCKED.pick(tone()));
            titleHint.setTextColor(color(MODE_FLEXIBLE.equals(mode)
                    ? R.color.brand_mint
                    : R.color.secondary_blue));
        }
        TextView eligible = root == null ? null : root.findViewById(R.id.bufferEligible);
        if (eligible != null) {
            eligible.setText(movable
                    ? CoachVoice.Line.ADDTASK_BUFFER_ELIGIBLE.pick(tone())
                    : CoachVoice.Line.ADDTASK_BUFFER_OFF.pick(tone()));
        }
    }

    /**
     * The Dino line under the sheet. It repeats the category, deferral window and priority
     * the user just chose, so the sheet visibly reads the same values it is about to save.
     */
    private void updateDinoCopy() {
        boolean flexible = MODE_FLEXIBLE.equals(mode);
        ToneType tone = tone();

        if (dinoTitle != null) {
            dinoTitle.setText(flexible
                    ? R.string.addtask_dino_scheduler_label
                    : R.string.addtask_dino_guard_label);
        }
        if (dinoMessage == null) {
            return;
        }
        if (!flexible) {
            dinoMessage.setText(CategoryRepository.WORK.equals(currentCategory())
                    ? CoachVoice.Line.DINO_ADDTASK_GUARD.pick(tone)
                    : CoachVoice.Line.DINO_ADDTASK_FIXED.pick(tone));
            return;
        }
        int hours = selectedDeferralHours();
        if (hours == 0) {
            // Never moved, so its priority has nothing to decide.
            dinoMessage.setText(CoachVoice.Line.DINO_ADDTASK_SCHED_LOCKED.pick(tone));
            return;
        }
        String line = getString(CoachVoice.Line.DINO_ADDTASK_SCHED.pick(tone), hours);
        // Medium is the default and needs no comment; High and Low change the order.
        int weight = selectedPriorityWeight();
        if (weight == Task.WEIGHT_HIGH) {
            line += " " + getString(CoachVoice.Line.DINO_ADDTASK_PRIORITY_HIGH.pick(tone));
        } else if (weight == Task.WEIGHT_LOW) {
            line += " " + getString(CoachVoice.Line.DINO_ADDTASK_PRIORITY_LOW.pick(tone));
        }
        dinoMessage.setText(line);
    }

    /** The coaching tone chosen in onboarding or Settings. */
    @NonNull
    private ToneType tone() {
        return OnboardingPrefs.getTone(requireContext());
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

    /**
     * Maps the Priority selector to the integers the ML model takes: High 3, Medium 2,
     * Low 1. {@link Task#setPriorityWeight(int)} turns this back into the stored
     * "HIGH"/"MED"/"LOW" value, so the row still round-trips through the database.
     *
     * @return 3, 2 or 1 - written to the row and fed to the load shedder
     */
    private int selectedPriorityWeight() {
        int checked = priorityGroup == null ? View.NO_ID : priorityGroup.getCheckedButtonId();
        if (checked == R.id.priorityHigh) {
            return Task.WEIGHT_HIGH;
        }
        if (checked == R.id.priorityLow) {
            return Task.WEIGHT_LOW;
        }
        return Task.WEIGHT_MED;
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
    private String[] deferralLabels() {
        return new String[]{
                getString(R.string.addtask_deferral_none),
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
                    // A different week can hold a different amount of work.
                    refreshWorkCap();
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
        durationPill.setText(getString(
                R.string.addtask_duration_hours,
                String.format(Locale.US, "%.1f", shiftMinutes() / 60.0)));
        // The shift's length feeds the week's work total.
        refreshWorkCap();
    }

    /** This entry's length in minutes, wrapping past midnight. */
    private int shiftMinutes() {
        int span = endMinutes - startMinutes;
        return span < 0 ? span + 24 * 60 : span;
    }

    // ==================================================================
    // Weekly work panel
    // ==================================================================

    /**
     * Fills the work panel: the week's Work hours, this shift included, against the user's own
     * weekly work hours from Set Limits.
     *
     * <p>The week runs Monday to Sunday, the same week the dashboard strip shows. Only a new
     * week, or a different row being edited, costs a read; time and category changes repaint
     * from the figure already held.
     */
    private void refreshWorkCap() {
        if (root == null || getContext() == null) {
            return;
        }
        String[] week = weekOf(dateIso);
        if (week == null) {
            return;
        }
        // An edited row is left out of the total, or its old hours would sit next to its new.
        long excludeId = editingTask == null ? 0 : editingTask.getId();
        final String key = week[0] + "#" + excludeId;
        if (key.equals(bookedWorkKey)) {
            renderWorkCap();
            return;
        }
        bookedWorkKey = key;
        bookedWorkMinutes = -1;
        renderWorkCap();
        TaskRepository.sumMinutesByCategory(requireContext(), CategoryRepository.WORK,
                week[0], week[1], excludeId, minutes -> {
                    // A newer week may have been picked while this one was reading.
                    if (!isAdded() || !key.equals(bookedWorkKey)) {
                        return;
                    }
                    bookedWorkMinutes = minutes == null ? -1 : minutes;
                    renderWorkCap();
                });
    }

    /**
     * Paints the panel from {@link #bookedWorkMinutes}.
     *
     * <p>Going past the weekly cap is overload whatever the user's own limit says, so it wins
     * over going past their limit, and takes the same amber-then-red bands Set Limits uses.
     * Both are warnings only: a shift is fixed, so the sheet still lets it be saved.
     */
    private void renderWorkCap() {
        if (root == null) {
            return;
        }
        TextView value = root.findViewById(R.id.capValue);
        LinearProgressIndicator bar = root.findViewById(R.id.capProgress);
        TextView footnote = root.findViewById(R.id.capFootnote);
        ImageView icon = root.findViewById(R.id.capFootnoteIcon);

        if (bookedWorkMinutes < 0) {
            // Not read yet, or the read failed: show no figure rather than a wrong one.
            if (value != null) {
                value.setText("");
            }
            if (bar != null) {
                bar.setProgressCompat(0, false);
            }
            return;
        }

        // This entry only adds to the work total when it is filed under Work.
        int position = categorySpinner == null ? -1 : categorySpinner.getSelectedItemPosition();
        int total = bookedWorkMinutes + (isWorkCategory(position) ? shiftMinutes() : 0);
        int limitHours = OnboardingPrefs.getWorkHours(requireContext());
        int limit = limitHours * 60;
        int cap = LoadZones.WORK_CAP_HOURS * 60;

        ToneType tone = tone();
        @ColorRes int colorRes;
        String note;
        if (total > cap) {
            colorRes = LoadZones.workBand((int) Math.ceil(total / 60.0)).colorRes;
            note = getString(CoachVoice.Line.ADDTASK_CAP_OVER_CAP.pick(tone),
                    formatHours(total - cap), LoadZones.WORK_CAP_HOURS);
        } else if (total > limit) {
            colorRes = R.color.status_amber;
            note = getString(CoachVoice.Line.ADDTASK_CAP_OVER_LIMIT.pick(tone),
                    formatHours(total - limit), limitHours);
        } else {
            colorRes = R.color.secondary_blue;
            note = getString(CoachVoice.Line.ADDTASK_CAP_FOOTNOTE.pick(tone));
        }
        int color = color(colorRes);

        if (value != null) {
            value.setText(getString(R.string.addtask_cap_value, formatHours(total), limitHours));
            value.setTextColor(color);
        }
        if (bar != null) {
            int percent = limit <= 0
                    ? (total > 0 ? 100 : 0)
                    : Math.min(100, Math.round(total * 100f / limit));
            bar.setProgressCompat(percent, true);
            bar.setIndicatorColor(color);
        }
        if (footnote != null) {
            footnote.setText(note);
            footnote.setTextColor(color);
        }
        if (icon != null) {
            tint(icon, colorRes);
        }
    }

    /** Minutes as hours the way the panel prints them: "16", or "16.5" for a part hour. */
    @NonNull
    private static String formatHours(int minutes) {
        return minutes % 60 == 0
                ? String.valueOf(minutes / 60)
                : String.format(Locale.US, "%.1f", minutes / 60.0);
    }

    /**
     * Monday and Sunday of the week {@code isoDate} falls in, as ISO dates, or null for a
     * date that cannot be read.
     */
    @Nullable
    private static String[] weekOf(@NonNull String isoDate) {
        SimpleDateFormat iso = new SimpleDateFormat(ISO_PATTERN, Locale.US);
        try {
            Date parsed = iso.parse(isoDate);
            if (parsed == null) {
                return null;
            }
            Calendar cursor = Calendar.getInstance();
            cursor.setTime(parsed);
            // Calendar.SUNDAY is 1, so this makes Monday 0 ... Sunday 6, like the dashboard.
            int fromMonday = (cursor.get(Calendar.DAY_OF_WEEK) + 5) % 7;
            cursor.add(Calendar.DAY_OF_YEAR, -fromMonday);
            String start = iso.format(cursor.getTime());
            cursor.add(Calendar.DAY_OF_YEAR, 6);
            return new String[]{start, iso.format(cursor.getTime())};
        } catch (ParseException e) {
            return null;
        }
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
            name = defaultNameFor(category);
            if (name.isEmpty()) {
                Toast.makeText(requireContext(), CoachVoice.Line.ADDTASK_NAME_REQUIRED.pick(tone()),
                        Toast.LENGTH_SHORT).show();
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
        task.setPriorityWeight(selectedPriorityWeight());
        // A protected shift can never be deferred, so its window is zero whatever the
        // spinner says: the scheduler reads the row, not the screen.
        task.setDeferralHours(flexible ? selectedDeferralHours() : 0);

        // --- TWO-WAY SYNC LOGIC ---
        // If it is an INFLEXIBLE work shift, push it to Android Calendar.
        //
        // Never on an edit. CalendarManager can write an event but has no update or delete, so
        // writing again would leave the original event in place and the shift would appear
        // twice on the user's calendar. The row keeps the event id it already owns.
        if (!flexible && editingTask == null) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(ISO_PATTERN + " HH:mm", Locale.US);
                Date startDate = sdf.parse(dateIso + " " + formatMinutes(startMinutes));
                Date endDate = sdf.parse(dateIso + " " + formatMinutes(endMinutes));

                if (startDate != null && endDate != null) {
                    // Push to Android Calendar and get the ID back
                    Long newCalendarId = calendarManager.writeShiftToCalendar(name, startDate.getTime(), endDate.getTime());
                    task.setCalendarEventId(newCalendarId); // Save the external ID to Room!
                }
            } catch (ParseException e) {
                android.util.Log.e(TAG, "Failed to parse dates for Calendar sync.");
            }
        }

        Context appContext = requireContext().getApplicationContext();
        setSaving(true);

        // An edit overwrites the row it came from; the Add flow inserts a new one.
        if (editingTask != null) {
            updateExisting(appContext, task, category);
            return;
        }

        final String savedCategory = category;
        TaskRepository.save(appContext, task, category, rowId -> {
            if (!isAdded()) {
                return;
            }
            setSaving(false);

            if (rowId == null || rowId <= 0) {
                Toast.makeText(requireContext(), CoachVoice.Line.ADDTASK_SAVE_FAILED.pick(tone()),
                        Toast.LENGTH_SHORT).show();
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
            return CategoryRepository.WORK.equals(category)
                    ? getString(CoachVoice.Line.ADDTASK_SAVED_WORK.pick(tone()))
                    : getString(CoachVoice.Line.ADDTASK_SAVED_FIXED.pick(tone()), category);
        }
        if (CategoryRepository.ACADEMIC.equals(category)) {
            return getString(CoachVoice.Line.ADDTASK_SAVED_ACADEMIC.pick(tone()));
        }
        return getString(CoachVoice.Line.ADDTASK_SAVED_CATEGORY.pick(tone()), category);
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