package com.example.codenection2026_package.model;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "tasks",
        foreignKeys = @ForeignKey(
                entity = Category.class,
                parentColumns = "id",
                childColumns = "category_id",
                onDelete = ForeignKey.SET_NULL
        ),
        indices = {
                @Index("category_id")
        }
)
public class Task {

    @PrimaryKey(autoGenerate = true)
    private long id;

    // "FLEXIBLE" or "INFLEXIBLE"
    private String classification;

    // Name entered by the user
    private String taskName;

    // Links this task to the user's category
    private Long category_id;

    // Format: yyyy-MM-dd
    private String date;

    // Format: HH:mm
    private String startTime;

    // Format: HH:mm
    private String endTime;

    public Task(
            String classification,
            String taskName,
            Long category_id,
            String date,
            String startTime,
            String endTime
    ) {
        this.classification = classification;
        this.taskName = taskName;
        this.category_id = category_id;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    // ID

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    // Classification

    public String getClassification() {
        return classification;
    }

    public void setClassification(String classification) {
        this.classification = classification;
    }

    // Task name

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    // Category

    public Long getCategory_id() {
        return category_id;
    }

    public void setCategory_id(Long category_id) {
        this.category_id = category_id;
    }

    // Date

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    // Start time

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    // End time

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }
}