package com.example.codenection2026_package.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.codenection2026_package.model.Category;
import com.example.codenection2026_package.model.Task;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The one place the UI talks to Room for tasks.
 *
 * <p><b>Why this file exists.</b> {@link TaskDao} is blocking by design and Room
 * throws if a query runs on the main thread, so the screens cannot call the DAO
 * directly from a click handler. This class owns a single background thread and a
 * main-thread handler: callers hand in a callback and get the answer back on the
 * UI thread, never having to touch a thread themselves.
 *
 * <p>Every callback reports failures as {@code null} rather than by throwing. A
 * database that cannot be opened is not worth crashing a bottom sheet over - the
 * Add Activity sheet turns a null insert id into its "could not save" toast, and the
 * dashboard turns a null list into its empty state.
 *
 * <p>NEW FILE - additive. {@link AppDatabase}, {@link TaskDao} and
 * {@link CategoryRepository} are used exactly as written; nothing existing changes.
 */
public final class TaskRepository {

    /** Receives a result on the main thread. A null value means "no result". */
    public interface Callback<T> {
        void onResult(@Nullable T value);
    }

    /** One worker thread is enough: these are tiny, infrequent queries. */
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** Seeding is once per process; the guard saves five queries on every load. */
    private static volatile boolean categoriesSeeded;

    private TaskRepository() {
    }

    /**
     * One row of the dashboard feed: the task itself, plus the name of the category it
     * was filed under.
     *
     * <p>Only the row id lives on {@link Task}, so the name has to be resolved here.
     * Doing it in the repository means the screen never holds a second map, and the
     * row's tag and the filter chips compare against the same canonical string.
     */
    public static final class FeedItem {

        private final Task task;

        @Nullable
        private final String categoryName;

        FeedItem(@NonNull Task task, @Nullable String categoryName) {
            this.task = task;
            this.categoryName = categoryName;
        }

        @NonNull
        public Task getTask() {
            return task;
        }

        /**
         * The canonical category name - Academic, Work, Errand, Social or
         * Co-curricular - or null for a row that predates category filing. Such a row
         * shows no tag and only appears under "All".
         */
        @Nullable
        public String getCategoryName() {
            return categoryName;
        }
    }

    /**
     * Loads every task filed under {@code isoDate} ("yyyy-MM-dd"), each paired with its
     * category name.
     *
     * <p>An empty list means the day is genuinely free - the dashboard shows its
     * empty state for it.
     */
    public static void loadByDate(@NonNull Context context,
                                  @NonNull String isoDate,
                                  @NonNull Callback<List<FeedItem>> callback) {
        final Context appContext = context.getApplicationContext();
        IO.execute(() -> {
            List<FeedItem> feed;
            try {
                AppDatabase db = AppDatabase.get(appContext);
                seedCategories(db);

                List<Task> tasks = db.taskDao().findByDate(isoDate);
                Map<Long, String> names = categoryNames(db.categoryDao());

                feed = new ArrayList<>(tasks.size());
                for (Task task : tasks) {
                    feed.add(new FeedItem(task, names.get(task.getCategory_id())));
                }
            } catch (RuntimeException e) {
                feed = null;
            }
            if (feed == null) {
                feed = Collections.emptyList();
            }
            final List<FeedItem> result = feed;
            MAIN.post(() -> callback.onResult(result));
        });
    }

    /**
     * Maps category row ids to their names.
     *
     * <p>One query for the whole feed rather than one per row. A HashMap tolerates the
     * null key a task with no category produces, which is exactly the fallback wanted.
     */
    @NonNull
    private static Map<Long, String> categoryNames(@NonNull CategoryDao dao) {
        Map<Long, String> names = new HashMap<>();
        for (Category category : dao.getAll()) {
            names.put(category.getId(), category.getName());
        }
        return names;
    }

    /**
     * Inserts a task, filing it under {@code categoryName}.
     *
     * <p>The category row is resolved (and created if the baseline seeding somehow
     * missed it) on the same background thread, immediately before the insert, so
     * the foreign key is always satisfied.
     *
     * @param callback receives the new row id, or null when the save failed
     */
    public static void save(@NonNull Context context,
                            @NonNull Task task,
                            @Nullable String categoryName,
                            @NonNull Callback<Long> callback) {
        final Context appContext = context.getApplicationContext();
        IO.execute(() -> {
            Long rowId;
            try {
                AppDatabase db = AppDatabase.get(appContext);
                seedCategories(db);
                task.setCategory_id(CategoryRepository.idFor(db.categoryDao(), categoryName));
                long inserted = db.taskDao().insert(task);
                rowId = inserted > 0 ? inserted : null;
            } catch (RuntimeException e) {
                rowId = null;
            }
            final Long result = rowId;
            MAIN.post(() -> callback.onResult(result));
        });
    }

    /**
     * Resolves a category name to its row id on the background thread.
     *
     * <p>Used when a caller needs the id without inserting a task.
     */
    public static void categoryId(@NonNull Context context,
                                  @Nullable String categoryName,
                                  @NonNull Callback<Long> callback) {
        final Context appContext = context.getApplicationContext();
        IO.execute(() -> {
            Long id;
            try {
                AppDatabase db = AppDatabase.get(appContext);
                seedCategories(db);
                id = CategoryRepository.idFor(db.categoryDao(), categoryName);
            } catch (RuntimeException e) {
                id = null;
            }
            final Long result = id;
            MAIN.post(() -> callback.onResult(result));
        });
    }

    private static void seedCategories(@NonNull AppDatabase db) {
        if (categoriesSeeded) {
            return;
        }
        CategoryRepository.ensureBaseline(db.categoryDao());
        categoriesSeeded = true;
    }

    /**
     * Checks if a specific occurrence of a Google Calendar event already exists in the database.
     */
    public static void findByCalendarIdAndDateAndTime(@NonNull Context context,
                                                      @NonNull Long calendarEventId,
                                                      @NonNull String date,
                                                      @NonNull String startTime,
                                                      @NonNull Callback<Task> callback) {
        final Context appContext = context.getApplicationContext();
        IO.execute(() -> {
            Task task;
            try {
                AppDatabase db = AppDatabase.get(appContext);
                task = db.taskDao().findByCalendarEventIdAndDateAndTime(calendarEventId, date, startTime);
            } catch (RuntimeException e) {
                task = null;
            }
            final Task result = task;
            MAIN.post(() -> callback.onResult(result));
        });
    }

    /**
     * Updates an existing task in the database.
     */
    public static void update(@NonNull Context context,
                              @NonNull Task task,
                              @NonNull Callback<Boolean> callback) {
        final Context appContext = context.getApplicationContext();
        IO.execute(() -> {
            boolean success = true;
            try {
                AppDatabase db = AppDatabase.get(appContext);
                db.taskDao().update(task);
            } catch (RuntimeException e) {
                success = false;
            }
            final Boolean result = success;
            MAIN.post(() -> callback.onResult(result));
        });
    }

    /**
     * Removes a task.
     *
     * <p>Here for the same reason {@link #save} and {@link #update} are: the Edit Task sheet
     * runs on the main thread, and {@link TaskDao#delete} would throw there. The row is
     * matched on its primary key, so the task handed in has to be one that came back from a
     * query rather than a freshly built one.
     *
     * <p>The foreign key runs the other way - {@code Task} points at {@code Category} with
     * {@code onDelete = SET_NULL} - so removing a task never disturbs the category it was
     * filed under.
     *
     * @param callback receives true when the row was removed, false when the delete failed
     */
    public static void delete(@NonNull Context context,
                              @NonNull Task task,
                              @NonNull Callback<Boolean> callback) {
        final Context appContext = context.getApplicationContext();
        IO.execute(() -> {
            boolean success = true;
            try {
                AppDatabase db = AppDatabase.get(appContext);
                db.taskDao().delete(task);
            } catch (RuntimeException e) {
                success = false;
            }
            final Boolean result = success;
            MAIN.post(() -> callback.onResult(result));
        });
    }
}
