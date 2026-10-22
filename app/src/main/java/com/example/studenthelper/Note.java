package com.example.studenthelper;

public class Note {

    private final long id;
    private final String title;
    private final String subject;
    private final String content;
    private final long createdAt;

    public Note(
            long id,
            String title,
            String subject,
            String content,
            long createdAt
    ) {
        this.id = id;
        this.title = title;
        this.subject = subject;
        this.content = content;
        this.createdAt = createdAt;
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

    public String getContent() {
        return content;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}