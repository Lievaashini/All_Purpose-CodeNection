package com.example.codenection2026_package.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.example.codenection2026_package.model.Biometrics;
import com.example.codenection2026_package.model.Category;
import com.example.codenection2026_package.model.Task;

/**
 * The Room database that ties the team's three entities together.
 *
 * <p><b>Why this file was added.</b> The database-foundation commit defined Task,
 * Category and Biometrics as @Entity classes and TaskDao, CategoryDao and BiometricsDao
 * as @Dao interfaces, but shipped no @Database class and no Room.databaseBuilder call
 * anywhere in the project. Without one, Room never generates an implementation and none
 * of the DAOs are reachable at runtime - the schema existed but nothing could open it.
 * This class is the missing entry point. It is purely additive: the entities and DAOs it
 * references are used exactly as their author wrote them and are not modified.
 *
 * <p>Access is through {@link #get(Context)} so the whole app shares one instance.
 *
 * <p><b>Version 2.</b> {@code tasks} gained the {@code priority} and
 * {@code deferral_hours} columns, which the Add Activity sheet now collects from the
 * user so the scheduler can protect urgent work. Version 1 was never released, so the
 * existing {@code fallbackToDestructiveMigration()} below is still the whole migration.
 */
@Database(
        entities = {Task.class, Category.class, Biometrics.class},
        version = 2,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static final String DATABASE_NAME = "capcoach.db";

    /** Volatile so the double-checked lock below is safe across threads. */
    private static volatile AppDatabase instance;

    public abstract TaskDao taskDao();

    public abstract CategoryDao categoryDao();

    public abstract BiometricsDao biometricsDao();

    /**
     * Returns the process-wide database, creating it on first use.
     *
     * <p>Uses the application context so the instance cannot outlive an Activity.
     */
    @NonNull
    public static AppDatabase get(@NonNull Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    DATABASE_NAME)
                            // Version 1 schema was never released, so a destructive
                            // migration is the honest option until a real migration is
                            // written alongside version 2.
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return instance;
    }
}