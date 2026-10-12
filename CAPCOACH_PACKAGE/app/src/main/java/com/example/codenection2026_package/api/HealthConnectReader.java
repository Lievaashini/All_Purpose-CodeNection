package com.example.codenection2026_package.api;

import android.content.Context;
import android.util.Log;

import androidx.health.connect.client.HealthConnectClient;
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord;
import androidx.health.connect.client.records.SleepSessionRecord;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class HealthConnectReader {

    private static final String TAG = "CapCoachAPI";

    /**
     * Reads all sleep sessions from the last 24 hours dynamically.
     */
    public static List<SleepSessionRecord> getSleepSessionsLast24Hours(Context context) {
        try {
            HealthConnectClient client = HealthConnectClient.getOrCreate(context);
            Instant end = Instant.now();
            Instant start = end.minus(24, ChronoUnit.HOURS);

            return HealthConnectHelper.readSleepDataSync(client, start, end);
        } catch (Exception e) {
            Log.e(TAG, "Failed to read rolling 24h sleep data: " + e.getMessage());
            return java.util.Collections.emptyList();
        }
    }

    /**
     * Sums total sleep hours from a list of records.
     */
    public static double calculateTotalSleepHours(List<SleepSessionRecord> records) {
        double totalHours = 0;
        for (SleepSessionRecord record : records) {
            long minutes = Duration.between(record.getStartTime(), record.getEndTime()).toMinutes();
            totalHours += (minutes / 60.0);
        }
        return totalHours;
    }

    /**
     * Exposes a clean metric for the CapacityCalculator to use.
     * Negative values indicate the user slept MORE than the target.
     */
    public static double getSleepDebtHours(List<SleepSessionRecord> records, double targetBaselineHours) {
        double totalSlept = calculateTotalSleepHours(records);
        return targetBaselineHours - totalSlept;
    }

    /**
     * Reads HRV (Heart Rate Variability) from the last 24 hours to populate the Biometrics UI.
     */
    public static double getAverageHrvLast24Hours(Context context) {
        try {
            HealthConnectClient client = HealthConnectClient.getOrCreate(context);
            Instant end = Instant.now();
            Instant start = end.minus(24, ChronoUnit.HOURS);

            List<HeartRateVariabilityRmssdRecord> hrvRecords = HealthConnectHelper.readHrvDataSync(client, start, end);

            if (hrvRecords == null || hrvRecords.isEmpty()) {
                return 0.0;
            }

            double totalHrv = 0;
            for (HeartRateVariabilityRmssdRecord record : hrvRecords) {
                totalHrv += record.getHeartRateVariabilityMillis();
            }
            return totalHrv / hrvRecords.size();

        } catch (Exception e) {
            Log.e(TAG, "Failed to read rolling 24h HRV data: " + e.getMessage());
            return 0.0;
        }
    }

    /** The nightly sleep the debt score is measured against. */
    public static final double TARGET_SLEEP_HOURS = 8.0;

    /**
     * Below this RMSSD, in milliseconds, HRV counts as low: the debt score adds a stress
     * penalty and the Biometrics screen shows the stressed state. One constant, so the two
     * cannot disagree.
     */
    public static final double LOW_HRV_MS = 40.0;

    /**
     * Today's Recovery Debt Score straight from Health Connect: last night's sleep against
     * {@link #TARGET_SLEEP_HOURS}, worsened by low HRV.
     *
     * <p>The same calculation the Biometrics screen draws, packaged so the triage model can
     * receive the identical number. Blocking - call it off the main thread.
     *
     * @return 0-100, or the neutral 50 when there is no sleep reading or it cannot be read
     */
    public static int currentRecoveryDebtScore(Context context) {
        try {
            List<SleepSessionRecord> lastNight = getSleepSessionsLast24Hours(context);
            boolean hasSleep = calculateTotalSleepHours(lastNight) > 0;

            double acuteDebt = getSleepDebtHours(lastNight, TARGET_SLEEP_HOURS);
            double hrvMs = getAverageHrvLast24Hours(context);

            // NEW: Fetch 7-day average for the chronic penalty
            double avg7Days = getAverageSleepLast7Days(context);
            // FIX: Clamped at 0.0
            double chronicDebt = Math.max(0.0, TARGET_SLEEP_HOURS - avg7Days);

            return calculateRecoveryDebtScore(acuteDebt, chronicDebt, hrvMs, hasSleep);
        } catch (RuntimeException e) {
            return calculateRecoveryDebtScore(0, 0.0, 0, false);
        }
    }

    //==============
    /**
     * THE NEW ENGINE: Converts acute sleep, chronic sleep, and HRV into a 0-100 Score.
     * We deleted the 3-parameter version to prevent Split-Brain bugs.
     * @param hasData Pass false if the Health Connect list was empty.
     */
    public static int calculateRecoveryDebtScore(double acuteDebt, double chronicDebt, double hrvMs, boolean hasData) {
        if (!hasData) {
            return 50;
        }

        // Acute Penalty (Last 24 Hours)
        double score = acuteDebt > 0 ? (acuteDebt * 11.0) : 0;

        // Chronic Penalty (Last 7 Days) - We use a smaller multiplier (7.0) so it
        // elevates the baseline without instantly maxing out the 100-point scale.
        if (chronicDebt > 0) {
            score += (chronicDebt * 7.0);
        }

        // HRV stress penalty. An RMSSD below 40 ms is this app's heuristic for poor recovery
        // in young adults, not a clinical threshold. An HRV of 0 means no reading (no
        // wearable), so it is skipped and the score rests on sleep alone.
        //
        // Additive, not multiplicative: a multiplier scaled the sleep debt, so a user who
        // slept the full 8h (debt 0) scored 0 however stressed they were - 0 x anything is 0.
        // Adding (40 - HRV) points keeps stress visible after a full night. On its own it
        // tops out near 40, below the model's first shedding band (above 60), so stress alone
        // raises the score without moving tasks; combined with short sleep it can.
        if (hrvMs > 0 && hrvMs < LOW_HRV_MS) {
            score += LOW_HRV_MS - hrvMs;
        }

        if (score > 100) return 100;
        return (int) Math.round(score);
    }


    /** NEW: Reads the trailing 7 days to calculate chronic sleep debt. */
    public static double getAverageSleepLast7Days(Context context) {
        try {
            HealthConnectClient client = HealthConnectClient.getOrCreate(context);
            Instant end = Instant.now();
            Instant start = end.minus(7, ChronoUnit.DAYS);
            List<SleepSessionRecord> records = HealthConnectHelper.readSleepDataSync(client, start, end);

            if (records == null || records.isEmpty()) {
                return TARGET_SLEEP_HOURS; // No penalty if they have no history
            }

            // FIX: Extract unique days rather than raw session counts
            java.util.Set<java.time.LocalDate> uniqueDays = new java.util.HashSet<>();
            for (SleepSessionRecord record : records) {
                java.time.ZoneOffset offset = record.getEndZoneOffset();
                java.time.ZoneId zone = offset != null ? offset : java.time.ZoneId.systemDefault();
                uniqueDays.add(record.getEndTime().atZone(zone).toLocalDate());
            }

            // THE PRODUCTION FIX: Divide by the number of days actually recorded
            // Prevents a new user from being penalized for days before they installed the app.
            double daysRecorded = Math.min(7.0, Math.max(1.0, uniqueDays.size()));
            return calculateTotalSleepHours(records) / daysRecorded;
        } catch (Exception e) {
            return TARGET_SLEEP_HOURS;
        }
    }


}