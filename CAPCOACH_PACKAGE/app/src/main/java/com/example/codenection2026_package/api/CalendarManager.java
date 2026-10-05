package com.example.codenection2026_package.api;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.util.Log;

import androidx.annotation.Nullable;

import com.example.codenection2026_package.R;

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

    public static class CalendarEvent {

        public long eventId;

        public String title;
        public String dateStr;      // "yyyy-MM-dd"
        public String startTimeStr; // "HH:mm"
        public String endTimeStr;   // "HH:mm"
        public boolean isAllDay;

        public CalendarEvent(long eventId, String title, String dateStr, String startTimeStr, String endTimeStr, boolean isAllDay) {
            this.eventId=eventId;
            this.title = title;
            this.dateStr = dateStr;
            this.startTimeStr = startTimeStr;
            this.endTimeStr = endTimeStr;
            this.isAllDay = isAllDay;
        }
    }

    /**
     * Extracts upcoming 7-day events using CalendarContract.Instances.
     * Expands recurring events and includes morning events from today.
     */
    public List<CalendarEvent> logUpcomingWeekEvents() {
        List<CalendarEvent> eventsList = new ArrayList<>();
        ContentResolver contentResolver = context.getContentResolver();

        // 1. Reset to the start of today (00:00:00)
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        // Step BACKWARD 7 days so Monday/Tuesday don't get falsely deleted
        cal.add(Calendar.DAY_OF_YEAR, -7);
        long startRange = cal.getTimeInMillis();

        // Step FORWARD 14 days (covers the rest of this week + next week)
        cal.add(Calendar.DAY_OF_YEAR, 22);
        long endRange = cal.getTimeInMillis();

        // 2. Query Instances instead of Events so recurring events are expanded
        Uri.Builder builder = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(builder, startRange);
        ContentUris.appendId(builder, endRange);
        Uri uri = builder.build();

        String[] projection = new String[]{
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY
        };

        String sortOrder = CalendarContract.Instances.BEGIN + " ASC";

        SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat timeFormatter = new SimpleDateFormat("HH:mm", Locale.getDefault());

        try (Cursor cursor = contentResolver.query(uri, projection, null, null, sortOrder)) {

            // FIX 1: If the provider crashes and returns a null cursor, abort and return null!
            if (cursor == null) return null;

            if (cursor.getCount() > 0) {
                while (cursor.moveToNext()) {
                    long eventId = cursor.getLong(0);
                    String title = cursor.getString(1);
                    long eventStart = cursor.getLong(2);
                    long eventEnd = cursor.getLong(3);
                    boolean isAllDay = cursor.getInt(4) == 1;

                    if (title == null || title.trim().isEmpty()) {
                        title = context.getString(R.string.calendar_untitled_event);
                    }

                    Calendar calStart = Calendar.getInstance();
                    calStart.setTimeInMillis(eventStart);

                    Calendar calEnd = Calendar.getInstance();
                    calEnd.setTimeInMillis(eventEnd);

                    String dateStr = dateFormatter.format(calStart.getTime());
                    String startTimeStr = timeFormatter.format(calStart.getTime());
                    String endTimeStr = timeFormatter.format(calEnd.getTime());

                    eventsList.add(new CalendarEvent(eventId, title, dateStr, startTimeStr, endTimeStr, isAllDay));
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Calendar permission not granted: " + e.getMessage());
            return null;
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
                values.put(CalendarContract.Events.TITLE,
                        context.getString(R.string.calendar_triage_prefix, title));
                values.put(CalendarContract.Events.CALENDAR_ID, calendarId);
                values.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());

                Uri uri = contentResolver.insert(CalendarContract.Events.CONTENT_URI, values);
                if (uri == null) Log.e(TAG, "Failed to write triage block.");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Calendar write permission denied: " + e.getMessage());
        }
    }

    /**
     * Writes a user-created inflexible shift to the native Android Calendar.
     * @return The newly generated Google Calendar EVENT_ID, or null if it failed.
     */
    @Nullable
    public Long writeShiftToCalendar(String title, long startMillis, long endMillis) {
        ContentResolver contentResolver = context.getContentResolver();
        Uri calendarsUri = CalendarContract.Calendars.CONTENT_URI;
        String[] projection = new String[]{CalendarContract.Calendars._ID};
        String selection = CalendarContract.Calendars.IS_PRIMARY + " = 1";

        try (Cursor cursor = contentResolver.query(calendarsUri, projection, selection, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                long calendarId = cursor.getLong(0);

                ContentValues values = new ContentValues();
                values.put(CalendarContract.Events.DTSTART, startMillis);
                values.put(CalendarContract.Events.DTEND, endMillis);
                values.put(CalendarContract.Events.TITLE, title);
                values.put(CalendarContract.Events.CALENDAR_ID, calendarId);
                values.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());

                Uri uri = contentResolver.insert(CalendarContract.Events.CONTENT_URI, values);
                if (uri != null) {
                    return Long.parseLong(uri.getLastPathSegment()); // Return the new Calendar ID!
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Calendar write permission denied: " + e.getMessage());
        } catch (NumberFormatException e) {
            Log.e(TAG, "Failed to parse new Calendar ID.");
        }
        return null;
    }
}