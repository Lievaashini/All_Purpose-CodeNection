package com.example.codenection2026_package.engine;

import com.example.codenection2026_package.model.Task;

public class CapacityTrigger {

    private static final String CLASSIFICATION_INFLEXIBLE = "inflexible";
    private static final String RECOVERY_CATEGORY = "Social";

    private final CapacityCalculator capacityCalculator;

    public CapacityTrigger() {
        this.capacityCalculator = new CapacityCalculator();
    }

    /**
     * Checks whether the calculated capacity has reached the
     * 90% intervention threshold.
     */
    public boolean shouldTrigger(double capacity) {
        return capacityCalculator.hasReachedNinetyPercent(capacity);
    }

    /**
     * Runs the ML triage decision for a task once the
     * 90% capacity threshold has been reached.
     *
     * @return LoadShedder.KEEP or LoadShedder.MOVE
     */
    public int predictTaskAction(
            double capacity,
            Task task,
            String categoryName
    ) {
        if (task == null) {
            return LoadShedder.KEEP;
        }

        // Do not run ML triage unless the 90% threshold is reached.
        if (!shouldTrigger(capacity)) {
            return LoadShedder.KEEP;
        }

        // Completed or non-deferrable tasks must not be moved.
        if (task.isCompleted() || task.getDeferralHours() <= 0) {
            return LoadShedder.KEEP;
        }

        int recoveryDebtScore = clampToMlRange(capacity);

        int daysUntilDue =
                TaskFeatureExtractor.calculateDaysUntilDue(
                        task.getDate()
                );

        int durationMinutes =
                TaskFeatureExtractor.calculateDurationMinutes(
                        task.getStartTime(),
                        task.getEndTime()
                );

        // Use the Task model's canonical priority conversion.
        int taskPriorityWeight = task.getPriorityWeight();

        int isFixedTime =
                CLASSIFICATION_INFLEXIBLE.equalsIgnoreCase(
                        task.getClassification()
                ) ? 1 : 0;

        // Normalize the category name before checking for Social.
        int isRecoveryActivity =
                RECOVERY_CATEGORY.equalsIgnoreCase(
                        categoryName == null ? "" : categoryName.trim()
                ) ? 1 : 0;

        return LoadShedder.predictTaskAction(
                recoveryDebtScore,
                daysUntilDue,
                taskPriorityWeight,
                isFixedTime,
                isRecoveryActivity,
                durationMinutes
        );
    }

    /**
     * Keeps the ML recovery-debt input within its documented 0-100 range.
     */
    private int clampToMlRange(double capacity) {
        if (capacity <= 0.0) {
            return 0;
        }

        if (capacity >= 100.0) {
            return 100;
        }

        return (int) Math.round(capacity);
    }
}