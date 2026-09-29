package com.example.codenection2026_package.engine;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public class TaskFeatureExtractor {

    /**
     * Calculates days until due, clamping negative (overdue) values to 0
     * and long-term tasks to a max of 14 days per ML bounds.
     */
    public static int calculateDaysUntilDue(String dateStr) {
        try {
            LocalDate taskDate = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            LocalDate today = LocalDate.now();
            long days = ChronoUnit.DAYS.between(today, taskDate);

            if (days < 0) return 0;
            if (days > 14) return 14;
            return (int) days;
        } catch (Exception e) {
            return 0; // Default fallback
        }
    }

    /**
     * Calculates duration in minutes. Clamps to [30, 180] and rounds
     * to the nearest 30-minute block to bypass the off-grid training defect.
     */
    public static int calculateDurationMinutes(String startTime, String endTime) {
        try {
            LocalTime start = LocalTime.parse(startTime, DateTimeFormatter.ofPattern("HH:mm"));
            LocalTime end = LocalTime.parse(endTime, DateTimeFormatter.ofPattern("HH:mm"));
            long minutes = ChronoUnit.MINUTES.between(start, end);

            // Handle midnight wrapping
            if (minutes < 0) {
                minutes += 1440;
            }

            // Round to nearest 30 to fix Defect #2
            int rounded = Math.round(minutes / 30.0f) * 30;

            if (rounded < 30) return 30;
            if (rounded > 180) return 180;
            return rounded;

        } catch (Exception e) {
            return 60; // Default fallback
        }
    }

    /**
     * Converts priority strings to ML integer weights.
     */
    public static int extractPriorityWeight(String priorityStr) {
        if (priorityStr == null) return 2;
        switch (priorityStr.toUpperCase().trim()) {
            case "HIGH": return 3;
            case "LOW":  return 1;
            case "MED":
            default:     return 2;
        }
    }
}

