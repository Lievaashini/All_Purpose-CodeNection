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

    /**
     * Converts a raw sleep deficit (in hours) into a 0-100 Recovery Debt Score.
     * @param hasData Pass false if the Health Connect list was empty.
     */
    public static int calculateRecoveryDebtScore(double sleepDeficitHours, boolean hasData) {
        if (!hasData) {
            return 50; // Neutral baseline for emulators and missing data
        }

        if (sleepDeficitHours <= 0) {
            return 0; // Fully rested
        }

        // Adjusted to a softer scale (15 points per hour instead of 25)
        double score = sleepDeficitHours * 15.0;

        if (score > 100) return 100;
        return (int) Math.round(score);
    }
}