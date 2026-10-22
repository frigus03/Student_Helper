package com.example.studenthelper;

import com.google.firebase.firestore.IgnoreExtraProperties;

@IgnoreExtraProperties
public class ChatSession {
    private String sessionId;
    private String title;
    private String lastMessage;
    private long updatedAt;
    private String noteId;
    private String subject;
    private String context;

    public ChatSession() {
        // Required for Firestore deserialization
    }

    public ChatSession(String sessionId, String title, String lastMessage, long updatedAt, String noteId, String subject, String context) {
        this.sessionId = sessionId;
        this.title = title;
        this.lastMessage = lastMessage;
        this.updatedAt = updatedAt;
        this.noteId = noteId;
        this.subject = subject;
        this.context = context;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getNoteId() {
        return noteId;
    }

    public void setNoteId(String noteId) {
        this.noteId = noteId;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChatSession that = (ChatSession) o;
        return sessionId != null ? sessionId.equals(that.sessionId) : that.sessionId == null;
    }

    @Override
    public int hashCode() {
        return sessionId != null ? sessionId.hashCode() : 0;
    }
}

