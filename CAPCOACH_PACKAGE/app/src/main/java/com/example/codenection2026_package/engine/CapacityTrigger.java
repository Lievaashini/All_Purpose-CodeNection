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
     * <p>The two numbers do different jobs and must not be swapped. {@code capacity} is the
     * workload, and only decides WHETHER the model runs. {@code recoveryDebtScore} is how
     * depleted the body is (sleep and HRV), and is what the model was trained on - it decides
     * how aggressively to shed. Feeding the workload in as the debt, as this used to, pinned
     * the model's input at 90-100 every time it ran, so sleep and HRV never changed a decision.
     *
     * @param capacity          the day's workload, 0-100+
     * @param recoveryDebtScore 0-100 from HealthConnectReader, 50 when there is no data
     * @return LoadShedder.KEEP or LoadShedder.MOVE
     */
    public int predictTaskAction(
            double capacity,
            int recoveryDebtScore,
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

        int debt = clampToMlRange(recoveryDebtScore);

        int daysUntilDue =
        // FIX: Use the deferral window (slack) instead of the scheduled date,
        // so the ML model knows this task isn't an immediate emergency.
           task.getDeferralHours() / 24
                ;


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
                debt,
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
    private int clampToMlRange(int score) {
        return Math.max(0, Math.min(100, score));
    }
}