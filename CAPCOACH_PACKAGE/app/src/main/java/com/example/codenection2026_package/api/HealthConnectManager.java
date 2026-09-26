package com.example.codenection2026_package.api;

import android.content.Context;
import androidx.health.connect.client.HealthConnectClient;
import androidx.health.connect.client.permission.HealthPermission;
import androidx.health.connect.client.records.SleepSessionRecord;
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord;
import androidx.health.connect.client.records.metadata.Metadata;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

// This import bridges the Kotlin KClass requirement in Java
import kotlin.jvm.JvmClassMappingKt;

public class HealthConnectManager {

    private HealthConnectClient client;

    public HealthConnectManager(Context context) {
        int sdkStatus = HealthConnectClient.getSdkStatus(context);
        if (sdkStatus == HealthConnectClient.SDK_AVAILABLE) {
            client = HealthConnectClient.getOrCreate(context);
        }
    }

    public boolean isClientAvailable() {
        return client != null;
    }

    public Set<String> getRequiredPermissions() {
        Set<String> permissions = new HashSet<>();
        // Converting Java Class to Kotlin KClass for the API
        permissions.add(HealthPermission.getReadPermission(JvmClassMappingKt.getKotlinClass(SleepSessionRecord.class)));
        // NOTE: Remove this line before final submission
        permissions.add(HealthPermission.getWritePermission(JvmClassMappingKt.getKotlinClass(SleepSessionRecord.class)));
        // Add HRV Read Permission
        permissions.add(HealthPermission.getReadPermission(JvmClassMappingKt.getKotlinClass(HeartRateVariabilityRmssdRecord.class)));

        return permissions;
    }

    public SleepSessionRecord createMockBurnoutSleep() {
        // Based on local Malaysia time (+08:00)
        ZoneOffset malaysiaOffset = ZoneOffset.of("+08:00");

        // Simulating 4 hours of sleep (11:00 PM to 3:00 AM)
        Instant startTime = Instant.parse("2026-09-23T23:00:00.000Z");
        Instant endTime = Instant.parse("2026-09-24T03:00:00.000Z");

        return new SleepSessionRecord(
                startTime,
                malaysiaOffset,
                endTime,
                malaysiaOffset,
                "Mocked Burnout Sleep",
                null,
                Collections.emptyList(),
                Metadata.EMPTY
        );
    }
}