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

    public static int calculateDurationMinutes(String startTime, String endTime) {
        try {
            LocalTime start = LocalTime.parse(startTime, DateTimeFormatter.ofPattern("HH:mm"));
            LocalTime end = LocalTime.parse(endTime, DateTimeFormatter.ofPattern("HH:mm"));
            long minutes = ChronoUnit.MINUTES.between(start, end);

            // Handle midnight wrapping
            if (minutes < 0) {
                minutes += 1440;
            }

            // Updated boundaries matching the ML training data (15 to 240)
            if (minutes < 15) return 15;
            if (minutes > 240) return 240;
            return (int) minutes;

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

