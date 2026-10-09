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

    // ---- Boundaries ----------------------------------------------------------------------

    /** The threshold is "at least 90", so exactly 90 must let the model run. */
    @Test
    public void exactlyNinetyPercent_runsTheModel() {
        assertEquals(LoadShedder.MOVE, trigger.predictTaskAction(90.0, 95, readingTask(), "Academic"));
    }

    @Test
    public void justBelowNinetyPercent_doesNotRunTheModel() {
        assertEquals(LoadShedder.KEEP, trigger.predictTaskAction(89.9, 95, readingTask(), "Academic"));
    }

    @Test
    public void zeroDeferralWindow_isKept_evenWhenExhaustedAndOverloaded() {
        Task task = readingTask();
        task.setDeferralHours(0);
        assertEquals(LoadShedder.KEEP, trigger.predictTaskAction(100, 100, task, "Academic"));
    }

    @Test
    public void completedTask_isKept() {
        Task task = readingTask();
        task.setCompleted(true);
        assertEquals(LoadShedder.KEEP, trigger.predictTaskAction(100, 100, task, "Academic"));
    }

    @Test
    public void missingTask_isKeptRatherThanCrashing() {
        assertEquals(LoadShedder.KEEP, trigger.predictTaskAction(100, 100, null, "Academic"));
    }

    /** No category is treated as ordinary load, not as protected social time. */
    @Test
    public void missingCategory_isTreatedAsOrdinaryLoad() {
        assertEquals(trigger.predictTaskAction(95, 95, readingTask(), "Academic"),
                trigger.predictTaskAction(95, 95, readingTask(), null));
    }

    /** Unreadable times fall back to a one-hour duration instead of throwing. */
    @Test
    public void unreadableTimes_doNotCrash() {
        Task task = readingTask();
        task.setStartTime("not a time");
        task.setEndTime("");
        assertEquals(LoadShedder.MOVE, trigger.predictTaskAction(95, 95, task, "Academic"));
    }

    @Test
    public void outOfRangeDebt_isClampedRatherThanRejected() {
        assertEquals(trigger.predictTaskAction(95, 100, readingTask(), "Academic"),
                trigger.predictTaskAction(95, 250, readingTask(), "Academic"));
    }
}
