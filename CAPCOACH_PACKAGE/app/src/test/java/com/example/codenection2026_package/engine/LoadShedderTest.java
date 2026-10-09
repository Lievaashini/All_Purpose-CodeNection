package com.example.codenection2026_package.engine;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Pins the decisions the triage model was trained to make (ml/generate_synthetic_dataset.py,
 * label_row). The tree is generated code, so these guard against a retrain or a hand edit
 * quietly changing what it does.
 *
 * <p>Argument order matches {@link LoadShedder#predictTaskAction}: recovery debt, days until
 * due, priority weight, is fixed time, is recovery activity, duration in minutes.
 */
public class LoadShedderTest {

    private static final int HIGH = 3;
    private static final int MED = 2;
    private static final int LOW = 1;

    /** The core promise: the same task gets a different answer as the body gets more tired. */
    @Test
    public void sameTask_movesOnlyOnceRecoveryDebtIsHeavy() {
        // Medium priority, due in 4 days, 90 minutes, flexible.
        assertEquals("rested", LoadShedder.KEEP, LoadShedder.predictTaskAction(20, 4, MED, 0, 0, 90));
        assertEquals("a bit tired", LoadShedder.KEEP, LoadShedder.predictTaskAction(70, 4, MED, 0, 0, 90));
        assertEquals("exhausted", LoadShedder.MOVE, LoadShedder.predictTaskAction(95, 4, MED, 0, 0, 90));
    }

    @Test
    public void fixedTimeTask_isNeverMoved_evenAtMaximumDebt() {
        assertEquals(LoadShedder.KEEP, LoadShedder.predictTaskAction(100, 10, LOW, 1, 0, 240));
    }

    @Test
    public void recoveryActivity_isKeptWhenExhausted() {
        // Social time is a buffer: cutting it from a burnt-out student makes things worse.
        assertEquals(LoadShedder.KEEP, LoadShedder.predictTaskAction(95, 10, LOW, 0, 1, 120));
    }

    @Test
    public void urgentHighPriority_isKeptWhenExhausted() {
        assertEquals(LoadShedder.KEEP, LoadShedder.predictTaskAction(95, 1, HIGH, 0, 0, 120));
    }

    @Test
    public void moderateDebt_shedsOnlyLowPriorityWorkThatIsFarAway() {
        assertEquals(LoadShedder.MOVE, LoadShedder.predictTaskAction(70, 8, LOW, 0, 0, 60));
        assertEquals(LoadShedder.KEEP, LoadShedder.predictTaskAction(70, 8, HIGH, 0, 0, 60));
    }

    @Test
    public void lowDebt_keepsEverything() {
        assertEquals(LoadShedder.KEEP, LoadShedder.predictTaskAction(30, 10, LOW, 0, 0, 240));
    }
}
