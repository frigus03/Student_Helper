package com.example.studenthelper;

import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.IgnoreExtraProperties;

@IgnoreExtraProperties
public class StudyTask {

    @Exclude
    private String id;
    
    private String title;
    private String subject;
    private long dueDate;
    private boolean completed;
    private long createdAt;

    public StudyTask() {}

    public StudyTask(
            String id,
            String title,
            String subject,
            long dueDate,
            boolean completed,
            long createdAt
    ) {
        this.id = id;
        this.title = title;
        this.subject = subject;
        this.dueDate = dueDate;
        this.completed = completed;
        this.createdAt = createdAt;
    }

    @Exclude
    public String getId() {
        return id;
    }

    @Exclude
    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public long getDueDate() {
        return dueDate;
    }

    public void setDueDate(long dueDate) {
        this.dueDate = dueDate;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }
    
    public long getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
