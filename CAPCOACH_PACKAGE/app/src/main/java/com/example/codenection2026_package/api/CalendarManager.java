package com.example.codenection2026_package.api;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.util.Log;

import java.util.Calendar;
import java.util.TimeZone;

public class CalendarManager {

    private final Context context;

    public CalendarManager(Context context) {
        this.context = context;
    }

    /**
     * Queries the local device for all calendar events happening in the next 7 days.
     * Prints results to Logcat so the backend team can verify the data pull.
     */
    public void logUpcomingWeekEvents() {
        ContentResolver contentResolver = context.getContentResolver();
        Uri uri = CalendarContract.Events.CONTENT_URI;

        String[] projection = new String[]{
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND
        };

        Calendar now = Calendar.getInstance();
        long startTime = now.getTimeInMillis();

        now.add(Calendar.DAY_OF_YEAR, 7);
        long endTime = now.getTimeInMillis();

        String selection = CalendarContract.Events.DTSTART + " >= ? AND " +
                CalendarContract.Events.DTSTART + " <= ?";
        String[] selectionArgs = new String[]{String.valueOf(startTime), String.valueOf(endTime)};
        String sortOrder = CalendarContract.Events.DTSTART + " ASC";

        try (Cursor cursor = contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)) {
            if (cursor != null && cursor.getCount() > 0) {
                Log.d("CapCoachAPI", "Found " + cursor.getCount() + " calendar events for the upcoming week.");

                while (cursor.moveToNext()) {
                    String title = cursor.getString(1);
                    long eventStart = cursor.getLong(2);
                    long eventEnd = cursor.getLong(3);

                    double durationHours = (eventEnd - eventStart) / (1000.0 * 60 * 60);
                    Log.d("CapCoachAPI", "Event: " + title + " | Duration: " + String.format("%.1f", durationHours) + " hours");
                }
            } else {
                Log.d("CapCoachAPI", "No upcoming events found in the local calendar.");
            }
        } catch (SecurityException e) {
            Log.e("CapCoachAPI", "Calendar permission not granted: " + e.getMessage());
        }
    }

    /**
     * Triage System: Writes a recovery block to the local calendar.
     * The Android OS will automatically sync this to the cloud in the background.
     */
    public void blockRecoveryTime(String title, int durationHours) {
        ContentResolver contentResolver = context.getContentResolver();

        // 1. Find the user's primary calendar account
        Uri calendarsUri = CalendarContract.Calendars.CONTENT_URI;
        String[] projection = new String[]{CalendarContract.Calendars._ID};
        String selection = CalendarContract.Calendars.IS_PRIMARY + " = 1";

        try (Cursor cursor = contentResolver.query(calendarsUri, projection, selection, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                long calendarId = cursor.getLong(0);

                // 2. Set the time block (Starting now)
                Calendar now = Calendar.getInstance();
                long startMillis = now.getTimeInMillis();
                now.add(Calendar.HOUR_OF_DAY, durationHours);
                long endMillis = now.getTimeInMillis();

                // 3. Package the event details
                ContentValues values = new ContentValues();
                values.put(CalendarContract.Events.DTSTART, startMillis);
                values.put(CalendarContract.Events.DTEND, endMillis);
                values.put(CalendarContract.Events.TITLE, "CapCoach Triage: " + title);
                values.put(CalendarContract.Events.CALENDAR_ID, calendarId);
                values.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());

                // 4. Inject it into the system database
                Uri uri = contentResolver.insert(CalendarContract.Events.CONTENT_URI, values);
                if (uri != null) {
                    Log.d("CapCoachAPI", "Triage block written successfully! " + uri.toString());
                } else {
                    Log.e("CapCoachAPI", "Failed to write triage block. Database returned null.");
                }

            } else {
                Log.e("CapCoachAPI", "No primary calendar found on device.");
            }
        } catch (SecurityException e) {
            Log.e("CapCoachAPI", "Calendar write permission denied: " + e.getMessage());
        }
    }
}