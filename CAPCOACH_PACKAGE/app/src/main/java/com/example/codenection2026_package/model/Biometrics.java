package com.example.codenection2026_package.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "biometrics")
public class Biometrics {

    @PrimaryKey(autoGenerate = true)
    private long id;

    // Date associated with the biometric record
    @ColumnInfo(name = "date")
    private String date;

    // Total sleep duration, stored in minutes
    @ColumnInfo(name = "sleep_duration_minutes")
    private int sleepDurationMinutes;

    // Heart Rate Variability measurement, stored in milliseconds
    // Nullable because HRV may not always be available.
    @ColumnInfo(name = "hrv")
    private Double hrv;

    public Biometrics(
            String date,
            int sleepDurationMinutes,
            Double hrv
    ) {
        this.date = date;
        this.sleepDurationMinutes = sleepDurationMinutes;
        this.hrv = hrv;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public int getSleepDurationMinutes() {
        return sleepDurationMinutes;
    }

    public void setSleepDurationMinutes(int sleepDurationMinutes) {
        this.sleepDurationMinutes = sleepDurationMinutes;
    }

    public Double getHrv() {
        return hrv;
    }

    public void setHrv(Double hrv) {
        this.hrv = hrv;
    }
}