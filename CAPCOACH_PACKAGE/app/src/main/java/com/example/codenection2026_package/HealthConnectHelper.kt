package com.example.codenection2026_package

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.runBlocking
import java.time.Instant

object HealthConnectHelper {

    // READ
    @JvmStatic
    fun readSleepDataSync(client: HealthConnectClient, start: Instant, end: Instant): List<SleepSessionRecord> {
        return runBlocking {
            val request = ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
            client.readRecords(request).records
        }
    }

    // WRITE
    @JvmStatic
    fun writeSleepDataSync(client: HealthConnectClient, records: List<SleepSessionRecord>) {
        runBlocking {
            client.insertRecords(records)
        }
    }
}