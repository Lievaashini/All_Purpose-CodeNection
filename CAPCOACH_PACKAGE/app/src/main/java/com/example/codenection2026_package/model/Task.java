package com.example.codenection2026_package.model;

import androidx.room.ColumnInfo;
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

    /** The user's own priority. Urgent work is protected from auto-shedding. */
    public static final String PRIORITY_HIGH = "HIGH";
    public static final String PRIORITY_MED = "MED";
    public static final String PRIORITY_LOW = "LOW";

    /**
     * The same three levels on the integer scale the ML load shedder expects as its
     * {@code taskPriorityWeight} input: 3 High, 2 Medium, 1 Low.
     */
    public static final int WEIGHT_HIGH = 3;
    public static final int WEIGHT_MED = 2;
    public static final int WEIGHT_LOW = 1;

    /** Default deferral window, in hours, of a sheddable task. */
    public static final int DEFAULT_DEFERRAL_HOURS = 48;

    @PrimaryKey(autoGenerate = true)
    private long id;

    @ColumnInfo(name = "calendar_event_id")
    private Long calendarEventId;

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

    // "HIGH", "MED" or "LOW". The scheduler refuses to shed a HIGH task while a
    // recovery window is at risk, so every task carries the user's own priority.
    @ColumnInfo(name = "priority", defaultValue = "MED")
    private String priority = PRIORITY_MED;

    // How many hours late the auto-deferral rail may push this task. 0 means the
    // task may not be deferred at all.
    @ColumnInfo(name = "deferral_hours", defaultValue = "48")
    private int deferralHours = DEFAULT_DEFERRAL_HOURS;

    // Tracks whether the user has checked off the task in the dashboard
    @ColumnInfo(name = "is_completed", defaultValue = "0")
    private boolean isCompleted = false;

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

    // Priority

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    /**
     * @return this task's priority as the ML model's integer input:
     *         {@link #WEIGHT_HIGH} (3), {@link #WEIGHT_MED} (2) or {@link #WEIGHT_LOW} (1).
     *         Feed this straight into the load shedder's {@code taskPriorityWeight}, so the
     *         High/Med/Low the user picked in the Add Activity sheet reaches the model.
     */
    public int getPriorityWeight() {
        if (PRIORITY_HIGH.equals(priority)) {
            return WEIGHT_HIGH;
        }
        if (PRIORITY_LOW.equals(priority)) {
            return WEIGHT_LOW;
        }
        return WEIGHT_MED;
    }

    /** Stores a priority given on the model's integer scale: 3 High, 2 Medium, 1 Low. */
    public void setPriorityWeight(int weight) {
        if (weight >= WEIGHT_HIGH) {
            priority = PRIORITY_HIGH;
        } else if (weight <= WEIGHT_LOW) {
            priority = PRIORITY_LOW;
        } else {
            priority = PRIORITY_MED;
        }
    }

    // Deferral window

    public int getDeferralHours() {
        return deferralHours;
    }

    public void setDeferralHours(int deferralHours) {
        this.deferralHours = deferralHours;
    }

    public Long getCalendarEventId() {
        return calendarEventId;
    }

    public void setCalendarEventId(Long calendarEventId) {
        this.calendarEventId = calendarEventId;
    }

    public boolean isCompleted(){return this.isCompleted;};
    public void setCompleted(boolean completed){this.isCompleted=completed;}
}