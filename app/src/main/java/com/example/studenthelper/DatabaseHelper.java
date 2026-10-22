
package com.example.studenthelper;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;

public class DatabaseHelper extends SQLiteOpenHelper {

    // Database configuration
    private static final String DATABASE_NAME = "studysphere.db";
    private static final int DATABASE_VERSION = 2;

    // Notes table
    private static final String TABLE_NOTES = "notes";

    public static final String COL_ID = "id";
    public static final String COL_TITLE = "title";
    public static final String COL_SUBJECT = "subject";
    public static final String COL_CONTENT = "content";
    public static final String COL_CREATED_AT = "created_at";

    // Study tasks table
    private static final String TABLE_TASKS = "study_tasks";

    public static final String COL_TASK_ID = "task_id";
    public static final String COL_TASK_TITLE = "task_title";
    public static final String COL_TASK_SUBJECT = "task_subject";
    public static final String COL_TASK_DUE_DATE = "task_due_date";
    public static final String COL_TASK_COMPLETED = "task_completed";
    public static final String COL_TASK_CREATED_AT = "task_created_at";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    // Create all tables for a new installation.
    @Override
    public void onCreate(SQLiteDatabase db) {
        createNotesTable(db);
        createTasksTable(db);
    }

    private void createNotesTable(SQLiteDatabase db) {
        String sql = "CREATE TABLE IF NOT EXISTS " + TABLE_NOTES + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_TITLE + " TEXT NOT NULL, " +
                COL_SUBJECT + " TEXT NOT NULL, " +
                COL_CONTENT + " TEXT NOT NULL, " +
                COL_CREATED_AT + " INTEGER NOT NULL" +
                ")";

        db.execSQL(sql);
    }

    private void createTasksTable(SQLiteDatabase db) {
        String sql = "CREATE TABLE IF NOT EXISTS " + TABLE_TASKS + " (" +
                COL_TASK_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_TASK_TITLE + " TEXT NOT NULL, " +
                COL_TASK_SUBJECT + " TEXT NOT NULL, " +
                COL_TASK_DUE_DATE + " INTEGER NOT NULL, " +
                COL_TASK_COMPLETED + " INTEGER NOT NULL DEFAULT 0, " +
                COL_TASK_CREATED_AT + " INTEGER NOT NULL" +
                ")";

        db.execSQL(sql);
    }

    // Upgrade existing databases without deleting saved notes.
    @Override
    public void onUpgrade(
            SQLiteDatabase db,
            int oldVersion,
            int newVersion
    ) {
        if (oldVersion < 2) {
            createTasksTable(db);
        }
    }

    // =========================
    // NOTES OPERATIONS
    // =========================

    public long insertNote(
            String title,
            String subject,
            String content
    ) {
        SQLiteDatabase db = getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(COL_TITLE, title);
        values.put(COL_SUBJECT, subject);
        values.put(COL_CONTENT, content);
        values.put(COL_CREATED_AT, System.currentTimeMillis());

        return db.insert(TABLE_NOTES, null, values);
    }

    public ArrayList<Note> getAllNotes() {
        ArrayList<Note> notes = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();

        try (Cursor cursor = db.query(
                TABLE_NOTES,
                null,
                null,
                null,
                null,
                null,
                COL_CREATED_AT + " DESC"
        )) {
            while (cursor.moveToNext()) {
                Note note = new Note(
                        cursor.getLong(
                                cursor.getColumnIndexOrThrow(COL_ID)
                        ),
                        cursor.getString(
                                cursor.getColumnIndexOrThrow(COL_TITLE)
                        ),
                        cursor.getString(
                                cursor.getColumnIndexOrThrow(COL_SUBJECT)
                        ),
                        cursor.getString(
                                cursor.getColumnIndexOrThrow(COL_CONTENT)
                        ),
                        cursor.getLong(
                                cursor.getColumnIndexOrThrow(COL_CREATED_AT)
                        )
                );

                notes.add(note);
            }
        }

        return notes;
    }

    public boolean deleteNote(long noteId) {
        SQLiteDatabase db = getWritableDatabase();

        int rowsDeleted = db.delete(
                TABLE_NOTES,
                COL_ID + " = ?",
                new String[]{String.valueOf(noteId)}
        );

        return rowsDeleted > 0;
    }

    public int getNotesCount() {
        SQLiteDatabase db = getReadableDatabase();

        try (Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) FROM " + TABLE_NOTES,
                null
        )) {
            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
        }

        return 0;
    }

    // =========================
    // STUDY PLANNER OPERATIONS
    // =========================

    public long insertTask(
            String title,
            String subject,
            long dueDate
    ) {
        SQLiteDatabase db = getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(COL_TASK_TITLE, title);
        values.put(COL_TASK_SUBJECT, subject);
        values.put(COL_TASK_DUE_DATE, dueDate);
        values.put(COL_TASK_COMPLETED, 0);
        values.put(COL_TASK_CREATED_AT, System.currentTimeMillis());

        return db.insert(TABLE_TASKS, null, values);
    }

    public ArrayList<StudyTask> getAllTasks() {
        ArrayList<StudyTask> tasks = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();

        try (Cursor cursor = db.query(
                TABLE_TASKS,
                null,
                null,
                null,
                null,
                null,
                COL_TASK_COMPLETED + " ASC, " +
                        COL_TASK_DUE_DATE + " ASC, " +
                        COL_TASK_CREATED_AT + " DESC"
        )) {
            while (cursor.moveToNext()) {
                StudyTask task = new StudyTask(
                        cursor.getLong(
                                cursor.getColumnIndexOrThrow(COL_TASK_ID)
                        ),
                        cursor.getString(
                                cursor.getColumnIndexOrThrow(COL_TASK_TITLE)
                        ),
                        cursor.getString(
                                cursor.getColumnIndexOrThrow(COL_TASK_SUBJECT)
                        ),
                        cursor.getLong(
                                cursor.getColumnIndexOrThrow(COL_TASK_DUE_DATE)
                        ),
                        cursor.getInt(
                                cursor.getColumnIndexOrThrow(COL_TASK_COMPLETED)
                        ) == 1
                );

                tasks.add(task);
            }
        }

        return tasks;
    }

    public boolean updateTaskStatus(
            long taskId,
            boolean completed
    ) {
        SQLiteDatabase db = getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(COL_TASK_COMPLETED, completed ? 1 : 0);

        int rowsUpdated = db.update(
                TABLE_TASKS,
                values,
                COL_TASK_ID + " = ?",
                new String[]{String.valueOf(taskId)}
        );

        return rowsUpdated > 0;
    }

    public boolean deleteTask(long taskId) {
        SQLiteDatabase db = getWritableDatabase();

        int rowsDeleted = db.delete(
                TABLE_TASKS,
                COL_TASK_ID + " = ?",
                new String[]{String.valueOf(taskId)}
        );

        return rowsDeleted > 0;
    }

    // =========================
    // DASHBOARD STATISTICS
    // =========================

    public int getTotalTaskCount() {
        return getTaskCount(null);
    }

    public int getCompletedTaskCount() {
        return getTaskCount(COL_TASK_COMPLETED + " = 1");
    }

    public int getPendingTaskCount() {
        return getTaskCount(COL_TASK_COMPLETED + " = 0");
    }

    private int getTaskCount(String selection) {
        SQLiteDatabase db = getReadableDatabase();

        String sql = "SELECT COUNT(*) FROM " + TABLE_TASKS;

        if (selection != null) {
            sql += " WHERE " + selection;
        }

        try (Cursor cursor = db.rawQuery(sql, null)) {
            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
        }

        return 0;
    }
}
