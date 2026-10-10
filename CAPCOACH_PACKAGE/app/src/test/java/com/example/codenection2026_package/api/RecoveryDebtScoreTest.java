package com.example.codenection2026_package.api;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Pins HealthConnectReader.calculateRecoveryDebtScore: 15 points per hour of sleep short
 * of 8h, plus (40 - HRV) points when HRV is below 40 ms, capped at 100.
 *
 * <p>Argument order: sleep deficit in hours, HRV in ms (0 = no reading), has sleep data.
 */
public class RecoveryDebtScoreTest {

    @Test
    public void noSleepData_isTheNeutralFifty() {
        assertEquals(50, HealthConnectReader.calculateRecoveryDebtScore(0, 0, false));
    }

    /** No HRV reading (no wearable) leaves the score on sleep alone. */
    @Test
    public void noHrvReading_usesSleepOnly() {
        assertEquals(45, HealthConnectReader.calculateRecoveryDebtScore(3, 0, true));
    }

    /** The bug this replaced: a full night zeroed out the HRV penalty entirely. */
    @Test
    public void fullNightWithLowHrv_stillShowsStress() {
        assertEquals(20, HealthConnectReader.calculateRecoveryDebtScore(0, 20, true));
    }

    @Test
    public void shortSleepAndLowHrv_add() {
        assertEquals(65, HealthConnectReader.calculateRecoveryDebtScore(3, 20, true));
    }

    @Test
    public void hrvAtOrAboveForty_addsNothing() {
        assertEquals(45, HealthConnectReader.calculateRecoveryDebtScore(3, 40, true));
        assertEquals(45, HealthConnectReader.calculateRecoveryDebtScore(3, 65, true));
    }

    @Test
    public void scoreIsCappedAtOneHundred() {
        assertEquals(100, HealthConnectReader.calculateRecoveryDebtScore(8, 5, true));
    }

    /** Sleeping longer than the target is not negative debt. */
    @Test
    public void oversleeping_isNotNegative() {
        assertEquals(0, HealthConnectReader.calculateRecoveryDebtScore(-2, 0, true));
    }
}
