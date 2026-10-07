package com.example.codenection2026_package

import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.reflect.KClass

/**
 * Puts a known set of biometric readings into Health Connect so the Biometrics screen can be
 * checked against numbers that are known in advance.
 *
 * <p>This exists because the emulator has no wearable and no data, which would only ever
 * exercise the screen's "Not recorded" path. Seeding here means every figure on the screen
 * can be compared with a number this file chose.
 *
 * <p>The week is deliberately uneven, so the 7-day sparkline has a shape to read rather than
 * one reproducible value. It is also deliberately unlike the prototype's own figures, so a
 * screen still showing the prototype would be obvious.
 *
 * <pre>
 *   nights, oldest first, in minutes:
 *     450   7h 30m
 *     375   6h 15m
 *     485   8h 05m   <- over the 8h target, which exercises the chart's clamp at the top
 *     340   5h 40m
 *     475   7h 55m
 *     390   6h 30m
 *     225   3h 45m   <- last night: the dip the alert card reports
 *
 *   7-night mean  2740 / 7 = 391 min = 6.5 hrs
 *   HRV           38 / 45 / 41 ms -> mean 41 ms, which a single reading could not prove
 *
 *   expected on screen:
 *     Baseline Average       6.5 hrs          (391 / 480 = 81% fill)
 *     Last Night (Recorded)  3.8 hrs          (225 / 480 = 47% fill)
 *     HRV SCORE              41 ms
 *     deficit badge          -53% Deficit     (8.0 - 3.75 = 4.25h of 8h)
 *     load-shedding rate     64%              (4.25 * 15, the existing helper's scale)
 *     anomaly card           visible: "3h 45m" against a target of "8h 00m", at 07:12 AM
 *     sparkline              high, dip, top, lower, high, dip, deepest
 *                            amber marker on night 6, pulsing marker on night 7
 * </pre>
 *
 * <p>The seed clears the records it owns first, so running it twice replaces the data instead
 * of doubling it and silently inflating the seven-day mean.
 *
 * <p><b>Sleep needs nothing extra; HRV does.</b> The app already declares WRITE_SLEEP, so the
 * seven nights always seed. It only ever <i>reads</i> HRV, so its manifest declares no HRV
 * write permission and the HRV step is skipped unless that permission is temporarily
 * declared and granted. Sleep is unaffected either way.
 *
 * <p>Run it with (the APKs are left installed so the screen can be inspected afterwards):
 * <pre>
 *   gradlew.bat :app:connectedDebugAndroidTest \
 *     "-Pandroid.testInstrumentationRunnerArguments.class=com.example.codenection2026_package.BiometricsSeedTest#seedBiometricsData" \
 *     "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
 * </pre>
 *
 * <p>{@link #clearBiometricsData} puts Health Connect back the way it was found.
 */
@RunWith(AndroidJUnit4::class)
class BiometricsSeedTest {

    private val zone: ZoneOffset = ZoneOffset.of("+08:00")

    /**
     * One sleep total per night, oldest first, in minutes.
     *
     * <p>Uneven on purpose: a flat week would make the sparkline a straight line, which
     * cannot show whether the chart is plotting the readings or just drawing a shape. Night 3
     * is over the target so the clamp at the top of the plot gets exercised too.
     */
    private val nightMinutes = longArrayOf(
        450, // 7h 30m
        375, // 6h 15m
        485, // 8h 05m - over the 8h target
        340, // 5h 40m
        475, // 7h 55m
        390, // 6h 30m
        225  // 3h 45m - last night, the dip
    )

    /** Three different readings, so the mean on screen cannot be a pass-through of one. */
    private val hrvMillis = listOf(38.0, 45.0, 41.0)

    @Test
    fun seedBiometricsData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val client = HealthConnectClient.getOrCreate(context)

        // Last night ends at 07:12 this morning, which is both inside the rolling 24-hour
        // window the screen reads and late enough to be a plausible wake time.
        val lastNightEnd = ZonedDateTime.now(zone)
            .withHour(7).withMinute(12).withSecond(0).withNano(0)
            .toInstant()

        val sessions = ArrayList<SleepSessionRecord>()
        nightMinutes.forEachIndexed { index, minutes ->
            val nightsAgo = (nightMinutes.size - 1 - index).toLong()
            val end = lastNightEnd.minus(nightsAgo, ChronoUnit.DAYS)
            sessions += session(end.minus(minutes, ChronoUnit.MINUTES), end)
        }

        val totalMinutes = nightMinutes.sum()

        runBlocking {
            // Clear first so a second run replaces the data instead of doubling it.
            deleteAll(client, SleepSessionRecord::class)

            trace("seeding ${sessions.size} sleep sessions, $totalMinutes min over 7 nights")
            nightMinutes.forEachIndexed { index, minutes ->
                trace("  night %d  %s  (%d min)".format(index + 1, formatHm(minutes), minutes))
            }
            trace("  7-night mean ${totalMinutes / 7.0} min")
            client.insertRecords(sessions)
        }

        seedHrv(client, lastNightEnd)

        trace("done - open the app, go to Biometrics, and compare against the numbers above")
    }

    /**
     * Seeds the HRV readings, or says why it could not.
     *
     * <p>Best effort by design: the app declares no HRV write permission, so a plain run
     * cannot write these, and that must not take the sleep data down with it.
     */
    private fun seedHrv(client: HealthConnectClient, lastNightEnd: Instant) {
        val readings: List<Record> = hrvMillis.mapIndexed { index, millis ->
            HeartRateVariabilityRmssdRecord(
                lastNightEnd.plus(index.toLong(), ChronoUnit.HOURS), zone, millis)
        }
        try {
            runBlocking {
                deleteAll(client, HeartRateVariabilityRmssdRecord::class)
                client.insertRecords(readings)
            }
            trace("seeded ${readings.size} HRV readings $hrvMillis -> mean ${hrvMillis.average()}")
        } catch (e: Exception) {
            trace("HRV skipped (${e.javaClass.simpleName}): needs WRITE_HEART_RATE_VARIABILITY")
            trace("sleep is unaffected; the HRV figure keeps whatever it had before")
        }
    }

    /** Removes everything this class writes, so the emulator is left as it was found. */
    @Test
    fun clearBiometricsData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val client = HealthConnectClient.getOrCreate(context)
        runBlocking {
            deleteAll(client, SleepSessionRecord::class)
        }
        try {
            runBlocking {
                deleteAll(client, HeartRateVariabilityRmssdRecord::class)
            }
        } catch (e: Exception) {
            trace("HRV not clearable (${e.javaClass.simpleName}); sleep and HRV both cleared is best effort")
        }
        trace("cleared seeded sleep sessions and HRV readings")
    }

    /** Deletes every record of one type over the last 30 days. */
    private suspend fun <T : Record> deleteAll(client: HealthConnectClient, type: KClass<T>) {
        val start = Instant.now().minus(30, ChronoUnit.DAYS)
        val request = ReadRecordsRequest(
            recordType = type,
            timeRangeFilter = TimeRangeFilter.between(start, Instant.now())
        )
        val ids = client.readRecords(request).records.mapNotNull { it.metadata.id }
        if (ids.isNotEmpty()) {
            client.deleteRecords(type, ids, emptyList())
        }
        trace("deleted ${ids.size} ${type.simpleName} record(s)")
    }

    /** One night's sleep, built the way HealthConnectManager builds its mock. The metadata
     *  argument is left to its default: Metadata.EMPTY is internal to Kotlin, which the Java
     *  mock can reach and this cannot. */
    private fun session(start: Instant, end: Instant) = SleepSessionRecord(
        start,
        zone,
        end,
        zone,
        "Biometrics seed",
        null,
        emptyList()
    )

    /** Minutes as "7h 30m", the same shape the anomaly copy uses. */
    private fun formatHm(minutes: Long): String = "%dh %02dm".format(minutes / 60, minutes % 60)

    private fun trace(line: String) {
        Log.i(TAG, line)
        println("[$TAG] $line")
    }

    private companion object {
        const val TAG = "BiometricsSeed"
    }
}
