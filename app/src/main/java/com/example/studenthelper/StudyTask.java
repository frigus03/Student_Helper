package com.example.studenthelper;

public class StudyTask {

    private final long id;
    private final String title;
    private final String subject;
    private final long dueDate;
    private final boolean completed;

    public StudyTask(
            long id,
            String title,
            String subject,
            long dueDate,
            boolean completed
    ) {
        this.id = id;
        this.title = title;
        this.subject = subject;
        this.dueDate = dueDate;
        this.completed = completed;
    }

    public long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getSubject() {
        return subject;
    }

    public long getDueDate() {
        return dueDate;
    }

    public boolean isCompleted() {
        return completed;
    }
}