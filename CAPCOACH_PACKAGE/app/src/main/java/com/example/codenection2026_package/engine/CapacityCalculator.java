package com.example.codenection2026_package.engine;

import com.example.codenection2026_package.model.Biometrics;
import com.example.codenection2026_package.model.Task;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

public class CapacityCalculator {

    private static final double TIME_MENTAL_WEIGHT = 1.5;

    // Maximum sleep penalty for extremely poor sleep.
    private static final double MAX_SLEEP_PENALTY = 2.0;

    // At 5 hours of sleep, the penalty becomes 1.0x.
    private static final int SLEEP_PENALTY_THRESHOLD_MINUTES = 5 * 60;

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

    /**
     * Applies a gradual sleep penalty based on sleep duration.
     *
     * Less sleep results in a higher penalty.
     * 5 hours or more results in no penalty.
     */
    public double applySleepPenalty(
            double capacity,
            Biometrics biometrics
    ) {
        if (biometrics == null) {
            return capacity;
        }

        int sleepMinutes = biometrics.getSleepDurationMinutes();

        double sleepPenalty = calculateSleepPenalty(sleepMinutes);

        return capacity * sleepPenalty;
    }

    /**
     * Calculates the sleep penalty using linear interpolation.
     *
     * 0 minutes of sleep   -> 2.0x penalty
     * 300 minutes of sleep -> 1.0x penalty
     */
    private double calculateSleepPenalty(int sleepMinutes) {

        // 5 hours or more: no penalty.
        if (sleepMinutes >= SLEEP_PENALTY_THRESHOLD_MINUTES) {
            return 1.0;
        }

        // Zero or negative sleep: maximum penalty.
        if (sleepMinutes <= 0) {
            return MAX_SLEEP_PENALTY;
        }

        /*
         * Linear interpolation between:
         *
         * 0 minutes   = 2.0x
         * 300 minutes = 1.0x
         */
        double sleepRatio =
                (double) sleepMinutes
                        / SLEEP_PENALTY_THRESHOLD_MINUTES;

        return MAX_SLEEP_PENALTY
                - ((MAX_SLEEP_PENALTY - 1.0) * sleepRatio);
    }

    /**
     * Calculates the duration of an unfinished task.
     *
     * Completed tasks are excluded because this represents
     * the user's remaining workload.
     */
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

    /**
     * Calculates the planned duration of a task.
     *
     * Completed tasks are included because this represents
     * the total workload originally planned for the day.
     */
    public double calculatePlannedTaskHours(Task task) {
        if (task == null) {
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

    /**
     * Calculates total remaining workload hours.
     */
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

    /**
     * Calculates total planned workload hours.
     *
     * Completed tasks are included because this represents
     * the workload originally planned for the day.
     */
    public double calculateTotalPlannedTaskHours(List<Task> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return 0.0;
        }

        double totalHours = 0.0;

        for (Task task : tasks) {
            totalHours += calculatePlannedTaskHours(task);
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