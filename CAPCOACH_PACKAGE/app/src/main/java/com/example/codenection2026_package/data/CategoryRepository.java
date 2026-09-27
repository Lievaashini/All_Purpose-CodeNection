package com.example.codenection2026_package.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.codenection2026_package.model.Category;

/**
 * The five baseline categories a CapCoach task can belong to.
 *
 * <p><b>Why this file exists.</b> {@code tasks.category_id} is a foreign key into
 * {@code categories}, and Room enforces foreign keys, so a task can only be filed
 * under a category row that actually exists. Nothing in the project ever inserted
 * one, so {@link #ensureBaseline(CategoryDao)} seeds the five the classifier and the
 * scheduler agree on, and {@link #idFor(CategoryDao, String)} resolves a name to its
 * row id at save time.
 *
 * <p>The names are the canonical, unlocalised values stored in the database. The
 * Add Activity sheet shows localised labels for them but always saves these strings,
 * so a translated build still files tasks under the same categories.
 *
 * <p>NEW FILE - additive. {@link CategoryDao} and {@link Category} are used exactly
 * as their author wrote them.
 */
public final class CategoryRepository {

    public static final String ACADEMIC = "Academic";
    public static final String WORK = "Work";
    public static final String ERRAND = "Errand";

    /**
     * The category the burnout-protection rail watches: social plans are the first
     * thing an overloaded student drops, and the scheduler treats losing them as a
     * recovery risk rather than a win.
     */
    public static final String SOCIAL = "Social";

    public static final String CO_CURRICULAR = "Co-curricular";

    /** Display order in the Add Activity spinner, and the order rows are seeded in. */
    public static final String[] BASELINE = {
            ACADEMIC, WORK, ERRAND, SOCIAL, CO_CURRICULAR
    };

    private CategoryRepository() {
    }

    /**
     * Inserts whichever baseline categories are missing.
     *
     * <p>Blocking: every call is a DAO round trip, so run it off the main thread.
     * Safe to call repeatedly - it only writes the rows that are not there yet.
     */
    public static void ensureBaseline(@NonNull CategoryDao dao) {
        for (String name : BASELINE) {
            if (dao.findByName(name) == null) {
                dao.insert(new Category(name));
            }
        }
    }

    /**
     * Resolves a category name to its row id, creating the row when it is missing.
     *
     * <p>Blocking: run it off the main thread.
     *
     * @return the row id, or null when the name is null or the insert was refused
     *         (which leaves the task without a category rather than blocking the save)
     */
    @Nullable
    public static Long idFor(@NonNull CategoryDao dao, @Nullable String name) {
        if (name == null) {
            return null;
        }
        Category existing = dao.findByName(name);
        if (existing != null) {
            return existing.getId();
        }
        long id = dao.insert(new Category(name));
        return id > 0 ? id : null;
    }

    /** @return the baseline name at {@code index}, or null when it is out of range */
    @Nullable
    public static String baselineAt(int index) {
        if (index < 0 || index >= BASELINE.length) {
            return null;
        }
        return BASELINE[index];
    }

    /** @return the index of a baseline name, or -1 when it is not one of the five */
    public static int indexOf(@Nullable String name) {
        if (name == null) {
            return -1;
        }
        for (int i = 0; i < BASELINE.length; i++) {
            if (BASELINE[i].equals(name)) {
                return i;
            }
        }
        return -1;
    }
}
