package com.example.codenection2026_package.engine;

import static org.junit.Assert.assertEquals;

import com.example.codenection2026_package.model.Task;

import org.junit.Test;

import java.time.LocalDate;

/**
 * Checks that workload and recovery debt each do their own job: workload decides whether
 * the model runs at all, recovery debt decides what it says once it does.
 */
public class CapacityTriggerTest {

    private final CapacityTrigger trigger = new CapacityTrigger();

    /** Medium priority, flexible, 90 minutes, filed four days out. */
    private static Task readingTask() {
        Task task = new Task("FLEXIBLE", "Read Chapter 4", null,
                LocalDate.now().plusDays(4).toString(), "10:00", "11:30");
        task.setPriorityWeight(Task.WEIGHT_MED);
        task.setDeferralHours(48);
        return task;
    }

    @Test
    public void belowThreshold_neverMoves_evenWhenExhausted() {
        assertEquals(LoadShedder.KEEP, trigger.predictTaskAction(60, 100, readingTask(), "Academic"));
    }

    /**
     * The bug this guards against: the model used to receive the workload as its debt
     * score, so an overloaded day always looked like an exhausted user. Over the threshold,
     * a well-rested user must keep the task and an exhausted one must shed it.
     */
    @Test
    public void overThreshold_decisionFollowsRecoveryDebt_notWorkload() {
        assertEquals("rested", LoadShedder.KEEP,
                trigger.predictTaskAction(95, 10, readingTask(), "Academic"));
        assertEquals("exhausted", LoadShedder.MOVE,
                trigger.predictTaskAction(95, 95, readingTask(), "Academic"));
    }

    @Test
    public void inflexibleTask_isKept() {
        Task shift = readingTask();
        shift.setClassification("INFLEXIBLE");
        shift.setDeferralHours(0);
        assertEquals(LoadShedder.KEEP, trigger.predictTaskAction(100, 100, shift, "Work"));
    }

    @Test
    public void socialTask_isKeptWhenExhausted() {
        assertEquals(LoadShedder.KEEP, trigger.predictTaskAction(95, 95, readingTask(), "Social"));
    }

    @Test
    public void outOfRangeDebt_isClampedRatherThanRejected() {
        assertEquals(trigger.predictTaskAction(95, 100, readingTask(), "Academic"),
                trigger.predictTaskAction(95, 250, readingTask(), "Academic"));
    }
}
