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
            double debtHours = getSleepDebtHours(lastNight, TARGET_SLEEP_HOURS);
            double hrvMs = getAverageHrvLast24Hours(context);
            return calculateRecoveryDebtScore(debtHours, hrvMs, hasSleep);
        } catch (RuntimeException e) {
            // Permission revoked or no provider installed: treat it like missing data.
            return calculateRecoveryDebtScore(0, 0, false);
        }
    }

    /**
     * Converts raw sleep deficit and HRV into a 0-100 Recovery Debt Score.
     * @param hasData Pass false if the Health Connect list was empty.
     */
    public static int calculateRecoveryDebtScore(double sleepDeficitHours, double hrvMs, boolean hasData) {
        if (!hasData) {
            return 50; // Neutral baseline for emulators and missing data
        }

        double score = sleepDeficitHours > 0 ? (sleepDeficitHours * 15.0) : 0;

        // HRV stress penalty. An RMSSD below 40 ms is this app's heuristic for poor recovery
        // in young adults, not a clinical threshold. An HRV of 0 means no reading (no
        // wearable), so it is skipped and the score rests on sleep alone.
        //
        // Additive, not multiplicative: a multiplier scaled the sleep debt, so a user who
        // slept the full 8h (debt 0) scored 0 however stressed they were - 0 x anything is 0.
        // Adding (40 - HRV) points keeps stress visible after a full night. On its own it
        // tops out near 40, below the model's first shedding band (above 60), so stress alone
        // raises the score without moving tasks; combined with short sleep it can.
        if (hrvMs > 0 && hrvMs < 40.0) {
            score += 40.0 - hrvMs;
        }

        if (score > 100) return 100;
        return (int) Math.round(score);
    }
}