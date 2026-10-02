package com.example.codenection2026_package.ui.edittask;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.codenection2026_package.model.Task;
import com.example.codenection2026_package.ui.addtask.AddTaskSheetFragment;

/**
 * SCREEN 4 in edit mode - the Add Task sheet, opened on a row that already exists.
 *
 * <p>Reached by long-pressing a card in the dashboard feed. It is the same sheet, the same
 * layout and the same fields: an edit collects exactly what an add collects, so this class
 * only supplies the row and lets {@link AddTaskSheetFragment#beginEdit} do the work. A second
 * sheet implementation would have meant a thousand duplicated lines and two forms that drift
 * apart the first time either one changes.
 *
 * <p>What it adds over the Add flow:
 *
 * <ul>
 *   <li>every control is filled from the row instead of from the prototype's defaults</li>
 *   <li>{@code @id/deleteTaskButton} becomes visible, and a confirmed tap removes the row</li>
 *   <li>the save button overwrites the row instead of inserting a new one</li>
 * </ul>
 *
 * <p><b>Why {@link #newInstance} rather than a constructor taking the task.</b> A fragment is
 * rebuilt from its arguments after a configuration change or process death, not from the
 * constructor it was created with, so a constructor argument would be silently lost and the
 * sheet would come back blank - and a blank edit sheet that saves would overwrite a real task
 * with empty fields. The row travels in a Bundle instead, which survives.
 */
public class EditTaskSheetFragment extends AddTaskSheetFragment {

    /** Tag this sheet is shown under. */
    public static final String TAG = "edit_task";

    // The whole row travels in arguments: the fragment has to be reconstructible from them.
    private static final String ARG_ID = "arg_id";
    private static final String ARG_NAME = "arg_name";
    private static final String ARG_CLASSIFICATION = "arg_classification";
    private static final String ARG_CATEGORY = "arg_category";
    private static final String ARG_CATEGORY_ID = "arg_category_id";
    private static final String ARG_DATE = "arg_date";
    private static final String ARG_START = "arg_start";
    private static final String ARG_END = "arg_end";
    private static final String ARG_PRIORITY = "arg_priority";
    private static final String ARG_DEFERRAL = "arg_deferral";
    private static final String ARG_COMPLETED = "arg_completed";
    private static final String ARG_CALENDAR_ID = "arg_calendar_id";

    /**
     * Builds the sheet for one row.
     *
     * <p>Takes the {@link Task} rather than a row id: the dashboard already holds the object
     * it just rendered, and a second query could only return the same thing a moment later.
     *
     * @param task         a row that came back from a query, so it carries its primary key
     * @param categoryName the canonical category it was filed under, or null for a row that
     *                     predates category filing
     */
    @NonNull
    public static EditTaskSheetFragment newInstance(@NonNull Task task,
                                                   @Nullable String categoryName) {
        EditTaskSheetFragment sheet = new EditTaskSheetFragment();

        Bundle args = new Bundle();
        args.putLong(ARG_ID, task.getId());
        args.putString(ARG_NAME, task.getTaskName());
        args.putString(ARG_CLASSIFICATION, task.getClassification());
        args.putString(ARG_CATEGORY, categoryName);
        args.putString(ARG_DATE, task.getDate());
        args.putString(ARG_START, task.getStartTime());
        args.putString(ARG_END, task.getEndTime());
        args.putString(ARG_PRIORITY, task.getPriority());
        args.putInt(ARG_DEFERRAL, task.getDeferralHours());
        args.putBoolean(ARG_COMPLETED, task.isCompleted());

        // Both ids are optional on the entity, so they are only written when present.
        if (task.getCategory_id() != null) {
            args.putLong(ARG_CATEGORY_ID, task.getCategory_id());
        }
        if (task.getCalendarEventId() != null) {
            args.putLong(ARG_CALENDAR_ID, task.getCalendarEventId());
        }

        sheet.setArguments(args);
        return sheet;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        if (args == null) {
            return;
        }
        beginEdit(taskFrom(args), args.getString(ARG_CATEGORY));
    }

    /** Rebuilds the row from the arguments, ids and all. */
    @NonNull
    private static Task taskFrom(@NonNull Bundle args) {
        Task task = new Task(
                args.getString(ARG_CLASSIFICATION),
                args.getString(ARG_NAME),
                null,
                args.getString(ARG_DATE),
                args.getString(ARG_START),
                args.getString(ARG_END));

        task.setId(args.getLong(ARG_ID));
        task.setPriority(args.getString(ARG_PRIORITY, Task.PRIORITY_MED));
        task.setDeferralHours(args.getInt(ARG_DEFERRAL, Task.DEFAULT_DEFERRAL_HOURS));
        task.setCompleted(args.getBoolean(ARG_COMPLETED, false));

        if (args.containsKey(ARG_CATEGORY_ID)) {
            task.setCategory_id(args.getLong(ARG_CATEGORY_ID));
        }
        if (args.containsKey(ARG_CALENDAR_ID)) {
            task.setCalendarEventId(args.getLong(ARG_CALENDAR_ID));
        }
        return task;
    }
}
