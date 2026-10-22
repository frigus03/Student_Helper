package com.example.studenthelper;

import android.content.Context;
import android.content.Intent;
import android.provider.CalendarContract;
import android.widget.Toast;

public final class CalendarHelper {

    private CalendarHelper() {
    }

    /**
     * Opens Google Calendar pre-filled with the study session event.
     */
    public static void addStudyTaskToCalendar(Context context, StudyTask task) {
        if (context == null || task == null) return;

        try {
            long startTimeMillis = task.getDueDate() > 0 
                    ? task.getDueDate() 
                    : System.currentTimeMillis() + (2 * 60 * 60 * 1000);
                    
            long durationMillis = task.getDurationMinutes() > 0 
                    ? (task.getDurationMinutes() * 60 * 1000L) 
                    : (45 * 60 * 1000L);
            long endTimeMillis = startTimeMillis + durationMillis;

            String cleanTitle = task.getTitle() != null ? task.getTitle() : "Study Session";
            cleanTitle = cleanTitle.replaceAll("[^\\x00-\\x7F]", "").trim();

            StringBuilder desc = new StringBuilder();
            desc.append("Subject: ").append(task.getSubject() != null ? task.getSubject() : "Course Study").append("\n");
            if (task.getCategory() != null && !task.getCategory().isEmpty()) {
                desc.append("Category: ").append(task.getCategory()).append("\n");
            }
            if (task.getNotes() != null && !task.getNotes().isEmpty()) {
                desc.append("\nWhat to do:\n").append(task.getNotes()).append("\n");
            }
            desc.append("\nScheduled with Student Helper AI");

            Intent intent = new Intent(Intent.ACTION_INSERT)
                    .setData(CalendarContract.Events.CONTENT_URI)
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startTimeMillis)
                    .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTimeMillis)
                    .putExtra(CalendarContract.Events.TITLE, cleanTitle)
                    .putExtra(CalendarContract.Events.DESCRIPTION, desc.toString())
                    .putExtra(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY);

            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, "Could not open Google Calendar: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
