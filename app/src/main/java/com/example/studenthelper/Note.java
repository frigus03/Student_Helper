package com.example.studenthelper;

import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.IgnoreExtraProperties;

@IgnoreExtraProperties
public class Note {

    @Exclude
    private String id;
    
    private String title;
    private String subject;
    private String content;
    private long createdAt;
    private String fileUrl;
    private String fileName;
    private String aiSummary;

    public Note() {
        // Default constructor required for calls to DataSnapshot.getValue(Note.class)
    }

    public Note(
            String id,
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

    public Note(
            String id,
            String title,
            String subject,
            String content,
            long createdAt,
            String fileUrl,
            String fileName,
            String aiSummary
    ) {
        this.id = id;
        this.title = title;
        this.subject = subject;
        this.content = content;
        this.createdAt = createdAt;
        this.fileUrl = fileUrl;
        this.fileName = fileName;
        this.aiSummary = aiSummary;
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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getAiSummary() {
        return aiSummary;
    }

    public void setAiSummary(String aiSummary) {
        this.aiSummary = aiSummary;
    }
}
