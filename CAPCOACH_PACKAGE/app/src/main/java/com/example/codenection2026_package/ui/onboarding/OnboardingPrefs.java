package com.example.codenection2026_package.ui.onboarding;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.example.codenection2026_package.model.ToneType;

/**
 * Temporary persistence for the onboarding inputs.
 *
 * <p><b>This is a placeholder for Role 1's Room layer.</b> It exists so Screen 1 and
 * Screen 2 can save and restore values right now, without waiting for the database.
 *
 * <p>To migrate: delete this class and point {@link HardLimitsFragment} at the
 * {@code User} entity instead. The field names below match
 * {@code TEAM_HANDSHAKE.md} section 5.1 exactly, so the swap is mechanical.
 */
public final class OnboardingPrefs {

    private static final String FILE = "capcoach_onboarding";

    private static final String KEY_TONE = "coaching_tone";
    private static final String KEY_STUDY = "study_hours_per_week";
    private static final String KEY_WORK = "work_hours_per_week";
    private static final String KEY_COCURRICULAR = "cocurricular_hours_per_week";
    private static final String KEY_CALENDAR_SYNCED = "calendar_synced";
    private static final String KEY_ONBOARDED = "onboarding_complete";

    // Defaults taken from the prototype's initial slider positions.
    public static final int DEFAULT_STUDY_HOURS = 24;
    public static final int DEFAULT_WORK_HOURS = 20;
    public static final int DEFAULT_COCURRICULAR_HOURS = 6;

    private OnboardingPrefs() {
    }

    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static int getStudyHours(@NonNull Context c) {
        return prefs(c).getInt(KEY_STUDY, DEFAULT_STUDY_HOURS);
    }

    public static int getWorkHours(@NonNull Context c) {
        return prefs(c).getInt(KEY_WORK, DEFAULT_WORK_HOURS);
    }

    public static int getCocurricularHours(@NonNull Context c) {
        return prefs(c).getInt(KEY_COCURRICULAR, DEFAULT_COCURRICULAR_HOURS);
    }

    @NonNull
    public static ToneType getTone(@NonNull Context c) {
        return ToneType.fromStorage(prefs(c).getString(KEY_TONE, ToneType.HYPE.storageValue));
    }

    public static boolean isCalendarSynced(@NonNull Context c) {
        return prefs(c).getBoolean(KEY_CALENDAR_SYNCED, false);
    }

    public static boolean isOnboardingComplete(@NonNull Context c) {
        return prefs(c).getBoolean(KEY_ONBOARDED, false);
    }

    /** Persists everything Screen 2 collects. Call from the Save &amp; Launch button. */
    public static void save(@NonNull Context c,
                            int studyHours,
                            int workHours,
                            int cocurricularHours,
                            boolean calendarSynced) {
        prefs(c).edit()
                .putInt(KEY_STUDY, studyHours)
                .putInt(KEY_WORK, workHours)
                .putInt(KEY_COCURRICULAR, cocurricularHours)
                .putBoolean(KEY_CALENDAR_SYNCED, calendarSynced)
                .putBoolean(KEY_ONBOARDED, true)
                .apply();
    }

    /** Persists the coaching tone chosen on Screen 1. */
    public static void saveTone(@NonNull Context c, @NonNull ToneType tone) {
        prefs(c).edit().putString(KEY_TONE, tone.storageValue).apply();
    }
}
