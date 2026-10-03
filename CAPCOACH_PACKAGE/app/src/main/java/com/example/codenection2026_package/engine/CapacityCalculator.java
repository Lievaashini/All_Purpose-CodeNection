package com.example.codenection2026_package.engine;

import com.example.codenection2026_package.model.Biometrics;
import com.example.codenection2026_package.model.Task;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

public class CapacityCalculator {

    private static final double TIME_MENTAL_WEIGHT = 1.5;
    private static final double POOR_SLEEP_PENALTY = 2.0;
    private static final int POOR_SLEEP_THRESHOLD_MINUTES = 5 * 60;

    private static final double CAPACITY_THRESHOLD = 90.0;
    private static final double CAPACITY_EPSILON = 0.000001;

    public double calculateCategoryLoad(
            double dailyInputHours,
            double baselineHours,
            double moodModifier
    ) {
        if (baselineHours <= 0) {
            return 0.0;
        }

        return (dailyInputHours / baselineHours)
                * moodModifier
                * 100.0;
    }

    public double calculateOverallCapacity(
            Map<String, Double> categoryLoads
    ) {
        if (categoryLoads == null || categoryLoads.isEmpty()) {
            return 0.0;
        }

        double weightedTotal = 0.0;
        double totalWeight = 0.0;

        for (Map.Entry<String, Double> entry : categoryLoads.entrySet()) {

            double load = entry.getValue();
            double weight = getCategoryWeight(entry.getKey());

            weightedTotal += load * weight;
            totalWeight += weight;
        }

        if (totalWeight == 0.0) {
            return 0.0;
        }

        return weightedTotal / totalWeight;
    }

    private double getCategoryWeight(String category) {
        if (category == null) {
            return 1.0;
        }

        switch (category) {
            case "Academic":
            case "Work":
                return TIME_MENTAL_WEIGHT;

            case "Errand":
            case "Social":
            case "Co-curricular":
            default:
                return 1.0;
        }
    }

    public double applySleepPenalty(
            double capacity,
            Biometrics biometrics
    ) {
        if (biometrics == null) {
            return capacity;
        }

        if (biometrics.getSleepDurationMinutes()
                < POOR_SLEEP_THRESHOLD_MINUTES) {

            return capacity * POOR_SLEEP_PENALTY;
        }

        return capacity;
    }

    public double calculateTaskHours(Task task) {
        if (task == null || task.isCompleted()) {
            return 0.0;
        }

        if (task.getStartTime() == null
                || task.getEndTime() == null) {
            return 0.0;
        }

        LocalTime start = LocalTime.parse(task.getStartTime());
        LocalTime end = LocalTime.parse(task.getEndTime());

        long minutes = Duration.between(start, end).toMinutes();

        // Handle tasks that cross midnight.
        if (minutes < 0) {
            minutes += 1440;
        }

        return minutes / 60.0;
    }

    public double calculateTotalTaskHours(List<Task> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return 0.0;
        }

        double totalHours = 0.0;

        for (Task task : tasks) {
            totalHours += calculateTaskHours(task);
        }

        return totalHours;
    }

    public boolean hasReachedNinetyPercent(double capacity) {
        return capacity + CAPACITY_EPSILON >= CAPACITY_THRESHOLD;
    }

    public CapacityState getCapacityState(double capacity) {
        if (capacity < 50.0) {
            return CapacityState.THRIVING;
        }

        if (capacity >= 80.0 && capacity <= 100.0) {
            return CapacityState.STACKED;
        }

        if (capacity > 100.0) {
            return CapacityState.OVERLOAD;
        }

        return CapacityState.NORMAL;
    }

    public enum CapacityState {
        THRIVING,
        NORMAL,
        STACKED,
        OVERLOAD
    }
}