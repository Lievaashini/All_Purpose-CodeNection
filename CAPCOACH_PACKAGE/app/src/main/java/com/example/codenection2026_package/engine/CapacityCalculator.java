package com.example.codenection2026_package.engine;

import com.example.codenection2026_package.model.Biometrics;
import com.example.codenection2026_package.model.Task;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

public class CapacityCalculator {

    // Documented project constants
    private static final double TIME_MENTAL_WEIGHT = 1.5;
    private static final double POOR_SLEEP_PENALTY = 2.0;
    private static final int POOR_SLEEP_THRESHOLD_MINUTES = 5 * 60;

    /**
     * Calculates the load for one category.
     *
     * Formula:
     * Category Load =
     * (Self-Reported Daily Input / Baseline Commitment)
     * × Mood Modifier
     *
     * @param dailyInputHours current workload/input for the category
     * @param baselineHours user's baseline commitment for the category
     * @param moodModifier modifier supplied by the check-in/input system
     * @return category load as a percentage
     */
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

    /**
     * Calculates the weighted overall capacity.
     *
     * Time and Mental categories receive the documented 1.5x weight.
     *
     * @param categoryLoads category name -> calculated load percentage
     * @param weightedCategories categories that should receive the 1.5x weight
     * @return overall capacity percentage
     */
    public double calculateOverallCapacity(
            Map<String, Double> categoryLoads,
            List<String> weightedCategories
    ) {
        if (categoryLoads == null || categoryLoads.isEmpty()) {
            return 0.0;
        }

        double weightedTotal = 0.0;
        double totalWeight = 0.0;

        for (Map.Entry<String, Double> entry : categoryLoads.entrySet()) {

            double load = entry.getValue();
            double weight = 1.0;

            if (weightedCategories != null
                    && weightedCategories.contains(entry.getKey())) {
                weight = TIME_MENTAL_WEIGHT;
            }

            weightedTotal += load * weight;
            totalWeight += weight;
        }

        if (totalWeight == 0.0) {
            return 0.0;
        }

        return weightedTotal / totalWeight;
    }

    /**
     * Applies the documented 2.0x sleep penalty when sleep
     * is below the project's poor-sleep threshold.
     *
     * @param capacity current capacity percentage
     * @param biometrics biometric record
     * @return capacity after the sleep penalty
     */
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

    /**
     * Calculates the duration of a task in hours.
     *
     * Completed tasks are not counted as active workload.
     *
     * @param task task from the Room database
     * @return task duration in hours
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

        long minutes;

        if (end.isAfter(start)) {
            minutes = Duration.between(start, end).toMinutes();
        } else {
            // Handles tasks that cross midnight.
            minutes = Duration.between(start, end.plusHours(24))
                    .toMinutes();
        }

        return minutes / 60.0;
    }

    /**
     * Calculates the total active workload from a list of tasks.
     *
     * Completed tasks are automatically excluded.
     *
     * @param tasks tasks retrieved from the Room database
     * @return total active task hours
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
     * Determines whether the capacity has reached
     * the documented 90% trigger threshold.
     */
    public boolean hasReachedNinetyPercent(double capacity) {
        return capacity >= 90.0;
    }

    /**
     * Returns the current capacity state.
     *
     * < 50%   = Thriving
     * 50-79%  = Normal
     * 80-100% = Stacked
     * > 100%  = Overload
     */
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