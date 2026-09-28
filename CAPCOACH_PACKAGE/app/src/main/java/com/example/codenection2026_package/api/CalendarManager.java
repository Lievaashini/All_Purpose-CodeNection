package com.example.codenection2026_package.api;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class CalendarManager {

    private final Context context;
    private static final String TAG = "CapCoachAPI";

    public CalendarManager(Context context) {
        this.context = context;
    }

    // DTO to hold structured calendar data
    public static class CalendarEvent {
        public String title;
        public String dateStr;      // "yyyy-MM-dd"
        public String startTimeStr; // "HH:mm"
        public String endTimeStr;   // "HH:mm"
        public boolean isAllDay;

        public CalendarEvent(String title, String dateStr, String startTimeStr, String endTimeStr, boolean isAllDay) {
            this.title = title;
            this.dateStr = dateStr;
            this.startTimeStr = startTimeStr;
            this.endTimeStr = endTimeStr;
            this.isAllDay = isAllDay;
        }
    }

    /**
     * Extracts upcoming 7-day events and formats them to match the Room Task entity.
     */
    public List<CalendarEvent> logUpcomingWeekEvents() {
        List<CalendarEvent> eventsList = new ArrayList<>();
        ContentResolver contentResolver = context.getContentResolver();
        Uri uri = CalendarContract.Events.CONTENT_URI;

        String[] projection = new String[]{
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.ALL_DAY
        };

        Calendar now = Calendar.getInstance();
        long startTime = now.getTimeInMillis();
        now.add(Calendar.DAY_OF_YEAR, 7);
        long endTime = now.getTimeInMillis();

        String selection = CalendarContract.Events.DTSTART + " >= ? AND " +
                CalendarContract.Events.DTSTART + " <= ?";
        String[] selectionArgs = new String[]{String.valueOf(startTime), String.valueOf(endTime)};
        String sortOrder = CalendarContract.Events.DTSTART + " ASC";

        SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat timeFormatter = new SimpleDateFormat("HH:mm", Locale.getDefault());

        try (Cursor cursor = contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)) {
            if (cursor != null && cursor.getCount() > 0) {
                while (cursor.moveToNext()) {
                    String title = cursor.getString(1);
                    long eventStart = cursor.getLong(2);
                    long eventEnd = cursor.getLong(3);
                    boolean isAllDay = cursor.getInt(4) == 1;

                    Calendar calStart = Calendar.getInstance();
                    calStart.setTimeInMillis(eventStart);

                    Calendar calEnd = Calendar.getInstance();
                    calEnd.setTimeInMillis(eventEnd);

                    String dateStr = dateFormatter.format(calStart.getTime());
                    String startTimeStr = timeFormatter.format(calStart.getTime());
                    String endTimeStr = timeFormatter.format(calEnd.getTime());

                    eventsList.add(new CalendarEvent(title, dateStr, startTimeStr, endTimeStr, isAllDay));
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Calendar permission not granted: " + e.getMessage());
        }
        return eventsList;
    }

    public void blockRecoveryTime(String title, int durationHours) {
        ContentResolver contentResolver = context.getContentResolver();
        Uri calendarsUri = CalendarContract.Calendars.CONTENT_URI;
        String[] projection = new String[]{CalendarContract.Calendars._ID};
        String selection = CalendarContract.Calendars.IS_PRIMARY + " = 1";

        try (Cursor cursor = contentResolver.query(calendarsUri, projection, selection, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                long calendarId = cursor.getLong(0);
                Calendar now = Calendar.getInstance();
                long startMillis = now.getTimeInMillis();
                now.add(Calendar.HOUR_OF_DAY, durationHours);
                long endMillis = now.getTimeInMillis();

                ContentValues values = new ContentValues();
                values.put(CalendarContract.Events.DTSTART, startMillis);
                values.put(CalendarContract.Events.DTEND, endMillis);
                values.put(CalendarContract.Events.TITLE, "CapCoach Triage: " + title);
                values.put(CalendarContract.Events.CALENDAR_ID, calendarId);
                values.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());

                Uri uri = contentResolver.insert(CalendarContract.Events.CONTENT_URI, values);
                if (uri == null) Log.e(TAG, "Failed to write triage block.");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Calendar write permission denied: " + e.getMessage());
        }
    }
}
