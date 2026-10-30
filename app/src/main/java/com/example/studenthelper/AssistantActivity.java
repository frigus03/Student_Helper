package com.example.studenthelper;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AssistantActivity extends AppCompatActivity {

    public static final String EXTRA_CONTEXT = "extra_context";
    public static final String EXTRA_SUBJECT = "extra_subject";
    public static final String EXTRA_NOTE_TITLE = "extra_note_title";
    public static final String EXTRA_NOTE_ID = "extra_note_id";

    private static final String CHAT_API_URL = AppConfig.BACKEND_BASE_URL + "/api/chat";

    private DrawerLayout drawerLayout;
    private RecyclerView rvChatSessions;
    private TextView tvEmptySessions;
    private ChatSessionsAdapter chatSessionsAdapter;
    private final List<ChatSession> sessionList = new ArrayList<>();

    private TextInputEditText etMessage;
    private LinearLayout messagesContainer;
    private ScrollView scrollMessages;
    private MaterialButton btnSend;
    private MaterialButton btnDrawerNewChat;
    private MaterialButton btnOpenDrawer;
    private MaterialButton btnCloseDrawer;
    private View layoutIntroBanner;
    private TextView tvInitialAiMessage;
    private TextView tvAssistantTitle;
    private TextView tvAssistantSubtitle;
    private TextView tvBannerGreeting;
    private TextView tvBannerDesc;

    // Note Selection Container views (when opened without pre-selected note)
    private View selectNoteContainer;
    private View layoutChatContent;
    private View layoutNoNotes;
    private RecyclerView rvNotesSelection;
    private NotesAdapter notesAdapter;
    private final ArrayList<Note> availableNotes = new ArrayList<>();

    private String extraContext = null;
    private String extraSubject = null;
    private String extraNoteTitle = null;
    private String extraNoteId = null;

    // null means New Chat (not yet saved to Firestore until first message is sent)
    private String currentSessionId = null;
    private String currentSessionTitle = null;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private final List<ChatMessage> chatHistoryList = new ArrayList<>();

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private boolean isWaitingForResponse = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_assistant);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        if (getIntent().hasExtra(EXTRA_CONTEXT)) {
            extraContext = getIntent().getStringExtra(EXTRA_CONTEXT);
        }
        if (getIntent().hasExtra(EXTRA_SUBJECT)) {
            extraSubject = getIntent().getStringExtra(EXTRA_SUBJECT);
        }
        if (getIntent().hasExtra(EXTRA_NOTE_TITLE)) {
            extraNoteTitle = getIntent().getStringExtra(EXTRA_NOTE_TITLE);
        }
        if (getIntent().hasExtra(EXTRA_NOTE_ID)) {
            extraNoteId = getIntent().getStringExtra(EXTRA_NOTE_ID);
        }

        drawerLayout = findViewById(R.id.drawerLayout);
        rvChatSessions = findViewById(R.id.rvChatSessions);
        tvEmptySessions = findViewById(R.id.tvEmptySessions);

        // Note selector views
        selectNoteContainer = findViewById(R.id.selectNoteContainer);
        layoutChatContent = findViewById(R.id.layoutChatContent);
        layoutNoNotes = findViewById(R.id.layoutNoNotes);
        rvNotesSelection = findViewById(R.id.rvNotesSelection);

        MaterialButton btnGoToNotes = findViewById(R.id.btnGoToNotes);
        if (btnGoToNotes != null) {
            btnGoToNotes.setOnClickListener(v -> {
                startActivity(new Intent(AssistantActivity.this, NotesActivity.class));
                finish();
            });
        }

        etMessage = findViewById(R.id.etMessage);
        messagesContainer = findViewById(R.id.messagesContainer);
        scrollMessages = findViewById(R.id.scrollMessages);
        btnSend = findViewById(R.id.btnSend);
        btnDrawerNewChat = findViewById(R.id.btnDrawerNewChat);
        btnOpenDrawer = findViewById(R.id.btnOpenDrawer);
        btnCloseDrawer = findViewById(R.id.btnCloseDrawer);
        layoutIntroBanner = findViewById(R.id.layoutIntroBanner);

        tvAssistantTitle = findViewById(R.id.tvAssistantTitle);
        tvAssistantSubtitle = findViewById(R.id.tvAssistantSubtitle);
        tvBannerGreeting = findViewById(R.id.tvBannerGreeting);
        tvBannerDesc = findViewById(R.id.tvBannerDesc);
        tvInitialAiMessage = findViewById(R.id.tvInitialAiMessage);

        MaterialButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        // Setup Drawer toggles
        if (btnOpenDrawer != null) {
            btnOpenDrawer.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        }
        if (btnCloseDrawer != null) {
            btnCloseDrawer.setOnClickListener(v -> drawerLayout.closeDrawer(GravityCompat.START));
        }

        // Setup Sessions RecyclerView
        setupSessionsRecyclerView();

        // New Chat button from side drawer: show note selector so user can pick which note/topic to chat about!
        if (btnDrawerNewChat != null) {
            btnDrawerNewChat.setOnClickListener(v -> {
                if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                }
                showNoteSelector();
            });
        }

        btnSend.setOnClickListener(v -> sendMessage());

        // Load saved chat history sessions into the side drawer
        loadChatSessions();

        // If opened without a specific note pre-selected, present the Note Selector first (just like Quiz!)
        if (extraContext == null || extraContext.trim().isEmpty()) {
            showNoteSelector();
        } else {
            startNewChat();
        }
    }

    private void setupSessionsRecyclerView() {
        rvChatSessions.setLayoutManager(new LinearLayoutManager(this));
        chatSessionsAdapter = new ChatSessionsAdapter(new ChatSessionsAdapter.OnSessionClickListener() {
            @Override
            public void onSessionClick(ChatSession session) {
                openChatSession(session);
            }

            @Override
            public void onSessionDelete(ChatSession session) {
                confirmDeleteSession(session);
            }
        });
        rvChatSessions.setAdapter(chatSessionsAdapter);
    }

    /**
     * Display the note selector so user can pick which note/chapter to study with AI.
     */
    private void showNoteSelector() {
        if (selectNoteContainer != null) selectNoteContainer.setVisibility(View.VISIBLE);
        if (layoutChatContent != null) layoutChatContent.setVisibility(View.GONE);

        tvAssistantTitle.setText("AI Study Companion");
        tvAssistantSubtitle.setText("Select a note to begin tutoring");

        if (notesAdapter == null) {
            rvNotesSelection.setLayoutManager(new LinearLayoutManager(this));
            notesAdapter = new NotesAdapter(new NotesAdapter.NoteListener() {
                @Override
                public void onNoteClick(Note note) {
                    selectNoteForAssistant(note);
                }

                @Override
                public void onDeleteClick(Note note) {
                    // Not deleted from tutor selector
                }

                @Override
                public void onAiTutorClick(Note note) {
                    selectNoteForAssistant(note);
                }

                @Override
                public void onQuizClick(Note note) {
                    Intent intent = new Intent(AssistantActivity.this, QuizActivity.class);
                    intent.putExtra(QuizActivity.EXTRA_SUBJECT, note.getSubject());
                    String material = (note.getContent() != null && !note.getContent().trim().isEmpty())
                            ? note.getContent()
                            : note.getAiSummary();
                    intent.putExtra(QuizActivity.EXTRA_MATERIAL, material != null ? material : "");
                    intent.putExtra(QuizActivity.EXTRA_FILE_URL, note.getFileUrl());
                    intent.putExtra(QuizActivity.EXTRA_NOTE_TITLE, note.getTitle());
                    intent.putExtra(QuizActivity.EXTRA_NOTE_ID, note.getId());
                    startActivity(intent);
                    finish();
                }
            });
            rvNotesSelection.setAdapter(notesAdapter);
        }
        loadNotesForSelector();
    }

    private void loadNotesForSelector() {
        if (mAuth.getCurrentUser() == null) {
            if (layoutNoNotes != null) layoutNoNotes.setVisibility(View.VISIBLE);
            return;
        }

        String userId = mAuth.getCurrentUser().getUid();
        db.collection("users")
                .document(userId)
                .collection("notes")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (isFinishing() || isDestroyed()) return;

                    availableNotes.clear();
                    if (!queryDocumentSnapshots.isEmpty()) {
                        for (DocumentSnapshot doc : queryDocumentSnapshots) {
                            Note note = doc.toObject(Note.class);
                            if (note != null) {
                                note.setId(doc.getId());
                                availableNotes.add(note);
                            }
                        }
                        if (layoutNoNotes != null) layoutNoNotes.setVisibility(View.GONE);
                        if (notesAdapter != null) notesAdapter.setNotes(availableNotes);
                    } else {
                        if (layoutNoNotes != null) layoutNoNotes.setVisibility(View.VISIBLE);
                    }
                })
                .addOnFailureListener(e -> {
                    if (layoutNoNotes != null) layoutNoNotes.setVisibility(View.VISIBLE);
                });
    }

    private void selectNoteForAssistant(Note note) {
        extraContext = note.getContent() != null ? note.getContent() : "";
        extraSubject = note.getSubject() != null ? note.getSubject() : "Course Material";
        extraNoteTitle = note.getTitle() != null ? note.getTitle() : extraSubject;
        extraNoteId = note.getId() != null ? note.getId() : "";

        startNewChat();
    }

    /**
     * Start a fresh, clean conversation.
     * Shows introductory greeting banner and placeholder message.
     */
    private void startNewChat() {
        if (selectNoteContainer != null) selectNoteContainer.setVisibility(View.GONE);
        if (layoutChatContent != null) layoutChatContent.setVisibility(View.VISIBLE);

        currentSessionId = null;
        currentSessionTitle = null;
        chatHistoryList.clear();
        messagesContainer.removeAllViews();

        if (extraContext != null && !extraContext.trim().isEmpty()) {
            String subjectDisplay = (extraSubject != null && !extraSubject.trim().isEmpty()) ? extraSubject.trim() : "Course Material";
            String titleDisplay = (extraNoteTitle != null && !extraNoteTitle.trim().isEmpty()) ? extraNoteTitle.trim() : subjectDisplay;

            tvAssistantTitle.setText("AI Course Tutor");
            tvAssistantSubtitle.setText("Topic: " + titleDisplay + " (" + subjectDisplay + ")");
            if (tvBannerGreeting != null) tvBannerGreeting.setText("Tutor: " + titleDisplay);
            if (tvBannerDesc != null) {
                tvBannerDesc.setText("Strict tutor mode active for \"" + titleDisplay + "\". Questions and answers are strictly grounded in your notes.");
            }
            if (tvInitialAiMessage != null) {
                tvInitialAiMessage.setText("Hello! I am your AI study tutor for \"" + titleDisplay + "\". I have reviewed your notes and material. Ask me any question, calculation, or concept check about this topic!");
            }
        } else {
            tvAssistantTitle.setText("AI Study Assistant");
            tvAssistantSubtitle.setText("Your learning companion");
            if (tvBannerGreeting != null) tvBannerGreeting.setText(getString(R.string.assistant_welcome));
            if (tvBannerDesc != null) {
                tvBannerDesc.setText("Ask a question, simplify a difficult topic, or prepare for your exams.");
            }
            if (tvInitialAiMessage != null) {
                tvInitialAiMessage.setText("Hi! I'm your StudySphere assistant. Send a message below to try the chat interface.");
            }
        }

        restoreInitialGreeting();

        if (chatSessionsAdapter != null) {
            chatSessionsAdapter.setSelectedSessionId(null);
        }

        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        }
    }

    /**
     * Load all saved chat sessions from Firestore for the user and display in side drawer.
     */
    private void loadChatSessions() {
        if (mAuth.getCurrentUser() == null) {
            updateEmptySessionsState(true);
            return;
        }

        String userId = mAuth.getCurrentUser().getUid();

        db.collection("users")
                .document(userId)
                .collection("chat_sessions")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (isFinishing() || isDestroyed()) return;

                    sessionList.clear();
                    if (!queryDocumentSnapshots.isEmpty()) {
                        for (DocumentSnapshot doc : queryDocumentSnapshots) {
                            ChatSession session = doc.toObject(ChatSession.class);
                            if (session != null) {
                                session.setSessionId(doc.getId());
                                sessionList.add(session);
                            }
                        }
                        updateEmptySessionsState(false);
                        chatSessionsAdapter.setSessions(sessionList, currentSessionId);
                    } else {
                        // Check if there was any legacy session under chats/general to migrate
                        migrateLegacyChatIfPresent(userId);
                    }
                })
                .addOnFailureListener(e -> {
                    if (sessionList.isEmpty()) {
                        updateEmptySessionsState(true);
                    }
                });
    }

    /**
     * If chat_sessions was empty, import any previous messages from legacy chats/general so user doesn't lose them.
     */
    private void migrateLegacyChatIfPresent(String userId) {
        db.collection("users")
                .document(userId)
                .collection("chats")
                .document("general")
                .collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (isFinishing() || isDestroyed()) return;

                    if (!queryDocumentSnapshots.isEmpty()) {
                        String firstMsgText = "General Study Chat";
                        long lastTime = System.currentTimeMillis();

                        for (DocumentSnapshot doc : queryDocumentSnapshots) {
                            ChatMessage m = doc.toObject(ChatMessage.class);
                            if (m != null && "user".equalsIgnoreCase(m.getSender()) && "General Study Chat".equals(firstMsgText)) {
                                firstMsgText = m.getText();
                                if (firstMsgText.length() > 40) {
                                    firstMsgText = firstMsgText.substring(0, 40) + "...";
                                }
                            }
                            if (m != null && m.getTimestamp() > 0) {
                                lastTime = m.getTimestamp();
                            }
                        }

                        String legacySessionId = "session_legacy_general";
                        ChatSession legacySession = new ChatSession(
                                legacySessionId,
                                firstMsgText,
                                "Past conversation",
                                lastTime,
                                null, null, null
                        );

                        // Save session doc
                        db.collection("users")
                                .document(userId)
                                .collection("chat_sessions")
                                .document(legacySessionId)
                                .set(legacySession);

                        // Copy messages
                        WriteBatch batch = db.batch();
                        CollectionReference newMessagesRef = db.collection("users")
                                .document(userId)
                                .collection("chat_sessions")
                                .document(legacySessionId)
                                .collection("messages");

                        for (DocumentSnapshot doc : queryDocumentSnapshots) {
                            ChatMessage msg = doc.toObject(ChatMessage.class);
                            if (msg != null) {
                                batch.set(newMessagesRef.document(doc.getId()), msg);
                            }
                        }
                        batch.commit();

                        sessionList.add(legacySession);
                        updateEmptySessionsState(false);
                        chatSessionsAdapter.setSessions(sessionList, currentSessionId);
                    } else {
                        updateEmptySessionsState(true);
                    }
                })
                .addOnFailureListener(e -> updateEmptySessionsState(true));
    }

    private void updateEmptySessionsState(boolean isEmpty) {
        if (tvEmptySessions != null) {
            tvEmptySessions.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        }
        if (rvChatSessions != null) {
            rvChatSessions.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        }
    }

    /**
     * Switch to a selected past conversation session from the drawer.
     */
    private void openChatSession(ChatSession session) {
        if (session == null || session.getSessionId() == null) return;

        if (selectNoteContainer != null) {
            selectNoteContainer.setVisibility(View.GONE);
        }
        if (layoutChatContent != null) {
            layoutChatContent.setVisibility(View.VISIBLE);
        }

        currentSessionId = session.getSessionId();
        currentSessionTitle = session.getTitle();

        extraContext = session.getContext();
        extraSubject = session.getSubject();
        extraNoteId = session.getNoteId();
        extraNoteTitle = session.getTitle();

        tvAssistantTitle.setText(session.getTitle() != null ? session.getTitle() : "AI Study Assistant");
        tvAssistantSubtitle.setText(session.getSubject() != null ? session.getSubject() : "Saved Chat");

        if (layoutIntroBanner != null) {
            layoutIntroBanner.setVisibility(View.GONE);
        }

        messagesContainer.removeAllViews();
        chatHistoryList.clear();

        chatSessionsAdapter.setSelectedSessionId(currentSessionId);

        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        }

        if (mAuth.getCurrentUser() == null) return;
        String userId = mAuth.getCurrentUser().getUid();

        // Load messages for this session
        db.collection("users")
                .document(userId)
                .collection("chat_sessions")
                .document(currentSessionId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (isFinishing() || isDestroyed()) return;

                    if (!queryDocumentSnapshots.isEmpty()) {
                        for (DocumentSnapshot doc : queryDocumentSnapshots) {
                            ChatMessage msg = doc.toObject(ChatMessage.class);
                            if (msg != null) {
                                msg.setId(doc.getId());
                                chatHistoryList.add(msg);
                                boolean isUser = "user".equalsIgnoreCase(msg.getSender());
                                addMessageView(msg.getText(), isUser);
                            }
                        }
                        scrollToBottom();
                    } else {
                        restoreInitialGreeting();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(AssistantActivity.this, "Could not load messages: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void confirmDeleteSession(ChatSession session) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete Chat")
                .setMessage("Delete \"" + (session.getTitle() != null ? session.getTitle() : "this chat") + "\" from your history?")
                .setPositiveButton("Delete", (dialog, which) -> deleteSession(session))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteSession(ChatSession session) {
        if (mAuth.getCurrentUser() == null || session == null || session.getSessionId() == null) return;
        String userId = mAuth.getCurrentUser().getUid();

        DocumentReference sessionDoc = db.collection("users")
                .document(userId)
                .collection("chat_sessions")
                .document(session.getSessionId());

        sessionDoc.collection("messages").get().addOnSuccessListener(msgs -> {
            WriteBatch batch = db.batch();
            for (DocumentSnapshot mDoc : msgs) {
                batch.delete(mDoc.getReference());
            }
            batch.delete(sessionDoc);
            batch.commit().addOnSuccessListener(aVoid -> {
                Toast.makeText(AssistantActivity.this, "Chat deleted", Toast.LENGTH_SHORT).show();

                sessionList.removeIf(s -> s.getSessionId() != null && s.getSessionId().equals(session.getSessionId()));
                chatSessionsAdapter.removeSession(session.getSessionId());
                updateEmptySessionsState(sessionList.isEmpty());

                if (session.getSessionId().equals(currentSessionId)) {
                    showNoteSelector();
                }
            });
        }).addOnFailureListener(e -> {
            Toast.makeText(AssistantActivity.this, "Failed to delete: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    private void sendMessage() {
        if (isWaitingForResponse) return;

        if (etMessage.getText() == null) {
            return;
        }

        String message = etMessage.getText().toString().trim();

        if (message.isEmpty()) {
            Toast.makeText(this, "Please enter a question.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Hide intro banner and default placeholder once conversation starts
        if (layoutIntroBanner != null && layoutIntroBanner.getVisibility() == View.VISIBLE) {
            layoutIntroBanner.setVisibility(View.GONE);
        }
        if (tvInitialAiMessage != null && tvInitialAiMessage.getParent() != null) {
            ((ViewGroup) tvInitialAiMessage.getParent()).removeView(tvInitialAiMessage);
        }

        // If this is a New Chat, initialize a new session ID and title
        boolean isBrandNewSession = (currentSessionId == null);
        if (isBrandNewSession) {
            currentSessionId = "session_" + System.currentTimeMillis();
            currentSessionTitle = message.length() > 36 ? message.substring(0, 36) + "..." : message;

            tvAssistantTitle.setText(currentSessionTitle);
            tvAssistantSubtitle.setText("Active Chat");

            // Create new ChatSession and add to drawer list
            ChatSession newSession = new ChatSession(
                    currentSessionId,
                    currentSessionTitle,
                    message,
                    System.currentTimeMillis(),
                    extraNoteId,
                    extraSubject,
                    extraContext
            );

            sessionList.add(0, newSession);
            updateEmptySessionsState(false);
            chatSessionsAdapter.setSessions(sessionList, currentSessionId);

            // Persist session document in Firestore
            if (mAuth.getCurrentUser() != null) {
                String userId = mAuth.getCurrentUser().getUid();
                db.collection("users")
                        .document(userId)
                        .collection("chat_sessions")
                        .document(currentSessionId)
                        .set(newSession);
            }
        }

        // 1. Add user message to UI and history list
        ChatMessage userMsg = new ChatMessage("user", message, System.currentTimeMillis());
        chatHistoryList.add(userMsg);
        addMessageView(message, true);
        etMessage.setText("");

        // 2. Persist user message to Firestore
        saveMessageToFirestore(userMsg);

        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(etMessage.getWindowToken(), 0);
        }

        btnSend.setEnabled(false);
        isWaitingForResponse = true;

        TextView replyView = addMessageView("Thinking...", false);

        // Prepare previous conversational turns to send to Gemini
        JSONArray historyArray = new JSONArray();
        int historySize = chatHistoryList.size();
        int startIndex = Math.max(0, historySize - 9);
        for (int i = startIndex; i < historySize - 1; i++) {
            ChatMessage hist = chatHistoryList.get(i);
            try {
                JSONObject obj = new JSONObject();
                obj.put("role", hist.getSender());
                obj.put("text", hist.getText());
                historyArray.put(obj);
            } catch (Exception ignored) {}
        }

        executor.execute(() -> {
            String result;
            try {
                result = requestAiAnswer(message, historyArray);
            } catch (Exception e) {
                result = "Sorry, I couldn't get an AI answer.\n\n" + e.getMessage();
            }

            final String finalResult = result;

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }

                replyView.setText(MarkdownTextFormatter.format(finalResult));
                btnSend.setEnabled(true);
                isWaitingForResponse = false;
                scrollToBottom();

                // Save AI reply to Firestore and history
                ChatMessage aiMsg = new ChatMessage("ai", finalResult, System.currentTimeMillis());
                chatHistoryList.add(aiMsg);
                saveMessageToFirestore(aiMsg);
            });
        });
    }

    private void saveMessageToFirestore(ChatMessage chatMessage) {
        if (mAuth.getCurrentUser() == null || currentSessionId == null) return;
        String userId = mAuth.getCurrentUser().getUid();

        db.collection("users")
                .document(userId)
                .collection("chat_sessions")
                .document(currentSessionId)
                .collection("messages")
                .add(chatMessage)
                .addOnSuccessListener(docRef -> {
                    chatMessage.setId(docRef.getId());
                });

        Map<String, Object> sessionMeta = new HashMap<>();
        sessionMeta.put("updatedAt", System.currentTimeMillis());
        sessionMeta.put("lastMessage", chatMessage.getText());
        if (currentSessionTitle != null) {
            sessionMeta.put("title", currentSessionTitle);
        }

        db.collection("users")
                .document(userId)
                .collection("chat_sessions")
                .document(currentSessionId)
                .set(sessionMeta, SetOptions.merge());
    }

    private void restoreInitialGreeting() {
        if (layoutIntroBanner != null) {
            layoutIntroBanner.setVisibility(View.VISIBLE);
        }
        if (tvInitialAiMessage != null) {
            if (tvInitialAiMessage.getParent() == null) {
                messagesContainer.addView(tvInitialAiMessage);
            }
        }
    }

    private String requestAiAnswer(String message, JSONArray historyArray) throws Exception {
        HttpURLConnection connection = null;

        try {
            URL url = new URL(CHAT_API_URL);
            connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("POST");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(70000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty(AppConfig.APP_TOKEN_HEADER, AppConfig.APP_TOKEN_VALUE);

            JSONObject requestBody = new JSONObject();
            requestBody.put("message", message);
            if (extraContext != null && !extraContext.trim().isEmpty()) {
                requestBody.put("context", extraContext.trim());
            }
            if (extraSubject != null && !extraSubject.trim().isEmpty()) {
                requestBody.put("subject", extraSubject.trim());
            }
            if (historyArray != null && historyArray.length() > 0) {
                requestBody.put("history", historyArray);
            }

            byte[] requestBytes = requestBody.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(requestBytes.length);

            try (OutputStream output = connection.getOutputStream()) {
                output.write(requestBytes);
                output.flush();
            }

            int statusCode = connection.getResponseCode();
            InputStream inputStream = statusCode >= 200 && statusCode < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();

            String responseText = "";
            if (inputStream != null) {
                try (InputStream stream = inputStream;
                     ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
                    byte[] data = new byte[4096];
                    int count;
                    while ((count = stream.read(data)) != -1) {
                        buffer.write(data, 0, count);
                    }
                    responseText = buffer.toString("UTF-8");
                }
            }

            JSONObject responseJson;
            try {
                responseJson = new JSONObject(responseText);
            } catch (Exception e) {
                throw new IOException("Invalid backend response. HTTP " + statusCode);
            }

            if (statusCode < 200 || statusCode >= 300) {
                throw new IOException(responseJson.optString("error", "Request failed: HTTP " + statusCode));
            }

            String answer = responseJson.optString("answer", "").trim();
            if (answer.isEmpty()) {
                throw new IOException("The AI returned an empty answer.");
            }
            return answer;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private TextView addMessageView(String message, boolean isUser) {
        TextView messageView = new TextView(this);

        messageView.setText(isUser ? message : MarkdownTextFormatter.format(message));
        messageView.setTextSize(15);
        messageView.setTextColor(isUser ? Color.WHITE : getColor(R.color.text_primary));

        int paddingH = dp(16);
        int paddingV = dp(12);
        messageView.setPadding(paddingH, paddingV, paddingH, paddingV);
        messageView.setBackgroundResource(isUser ? R.drawable.bg_bubble_user : R.drawable.bg_bubble_ai);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);

        params.gravity = isUser ? Gravity.END : Gravity.START;
        params.setMargins(0, dp(4), 0, dp(4));

        messageView.setLayoutParams(params);
        messagesContainer.addView(messageView);

        scrollToBottom();
        return messageView;
    }

    private void scrollToBottom() {
        scrollMessages.post(() -> scrollMessages.fullScroll(View.FOCUS_DOWN));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
