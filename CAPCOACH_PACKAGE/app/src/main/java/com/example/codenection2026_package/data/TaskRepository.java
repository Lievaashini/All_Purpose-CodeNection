package com.example.codenection2026_package.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.codenection2026_package.model.Task;

import java.util.Collections;
import java.util.List;
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
     * Loads every task filed under {@code isoDate} ("yyyy-MM-dd").
     *
     * <p>An empty list means the day is genuinely free - the dashboard shows its
     * empty state for it.
     */
    public static void loadByDate(@NonNull Context context,
                                  @NonNull String isoDate,
                                  @NonNull Callback<List<Task>> callback) {
        final Context appContext = context.getApplicationContext();
        IO.execute(() -> {
            List<Task> tasks;
            try {
                AppDatabase db = AppDatabase.get(appContext);
                seedCategories(db);
                tasks = db.taskDao().findByDate(isoDate);
            } catch (RuntimeException e) {
                tasks = null;
            }
            if (tasks == null) {
                tasks = Collections.emptyList();
            }
            final List<Task> result = tasks;
            MAIN.post(() -> callback.onResult(result));
        });
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
}
