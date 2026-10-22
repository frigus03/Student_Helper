package com.example.studenthelper;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NotesActivity extends AppCompatActivity {

    private static final String UPLOAD_API_URL = "http://10.0.2.2:3000/api/upload";

    private NotesAdapter notesAdapter;

    private RecyclerView rvNotes;
    private View layoutEmptyNotes;
    private TextView tvNotesCount;
    private TextView tvEmptyMessage;
    private EditText etSearchNotes;

    private final ArrayList<Note> allNotes = new ArrayList<>();
    
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private ListenerRegistration notesListener;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private ActivityResultLauncher<String> filePickerLauncher;
    private Uri pendingUploadUri = null;
    private String pendingUploadName = null;
    private TextView tvAttachedFileStatus = null;
    private EditText currentEtContent = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notes);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        setupFilePicker();
        initializeViews();
        setupRecyclerView();
        setupListeners();
        loadNotes();
    }

    private void setupFilePicker() {
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        pendingUploadUri = uri;
                        pendingUploadName = getFileNameFromUri(uri);
                        if (tvAttachedFileStatus != null) {
                            tvAttachedFileStatus.setText("Attached: " + pendingUploadName);
                            tvAttachedFileStatus.setVisibility(View.VISIBLE);
                        }

                        // Ingest text if it's a plain text or markdown file
                        try {
                            String type = getContentResolver().getType(uri);
                            if (type != null && (type.contains("text") || type.contains("markdown")) 
                                    || (pendingUploadName != null && pendingUploadName.endsWith(".txt"))) {
                                InputStream is = getContentResolver().openInputStream(uri);
                                if (is != null) {
                                    BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                                    StringBuilder sb = new StringBuilder();
                                    String line;
                                    while ((line = reader.readLine()) != null) {
                                        sb.append(line).append("\n");
                                    }
                                    reader.close();
                                    if (currentEtContent != null && currentEtContent.getText().toString().trim().isEmpty()) {
                                        currentEtContent.setText(sb.toString().trim());
                                    }
                                }
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
        );
    }

    private String getFileNameFromUri(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result != null ? result.lastIndexOf('/') : -1;
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result != null ? result : "material_file";
    }

    private void initializeViews() {
        rvNotes = findViewById(R.id.rvNotes);
        layoutEmptyNotes = findViewById(R.id.layoutEmptyNotes);
        tvNotesCount = findViewById(R.id.tvNotesCount);
        tvEmptyMessage = findViewById(R.id.tvEmptyMessage);
        etSearchNotes = findViewById(R.id.etSearchNotes);

        com.google.android.material.floatingactionbutton.FloatingActionButton btnAddNote = findViewById(R.id.btnAddNote);
        btnAddNote.setOnClickListener(v -> showAddNoteDialog(null));
    }

    private void setupRecyclerView() {
        rvNotes.setLayoutManager(new LinearLayoutManager(this));

        notesAdapter = new NotesAdapter(new NotesAdapter.NoteListener() {
            @Override
            public void onNoteClick(Note note) {
                showNoteDetailDialog(note);
            }

            @Override
            public void onDeleteClick(Note note) {
                confirmDeleteNote(note);
            }

            @Override
            public void onAiTutorClick(Note note) {
                Intent intent = new Intent(NotesActivity.this, AssistantActivity.class);
                intent.putExtra(AssistantActivity.EXTRA_CONTEXT, note.getContent());
                intent.putExtra(AssistantActivity.EXTRA_SUBJECT, note.getSubject());
                intent.putExtra(AssistantActivity.EXTRA_NOTE_TITLE, note.getTitle());
                intent.putExtra(AssistantActivity.EXTRA_NOTE_ID, note.getId());
                startActivity(intent);
            }

            @Override
            public void onQuizClick(Note note) {
                Intent intent = new Intent(NotesActivity.this, QuizActivity.class);
                intent.putExtra(QuizActivity.EXTRA_SUBJECT, note.getSubject());
                intent.putExtra(QuizActivity.EXTRA_MATERIAL, note.getContent());
                intent.putExtra(QuizActivity.EXTRA_NOTE_TITLE, note.getTitle());
                intent.putExtra(QuizActivity.EXTRA_NOTE_ID, note.getId());
                startActivity(intent);
            }
        });

        rvNotes.setAdapter(notesAdapter);
    }

    private void setupListeners() {
        etSearchNotes.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterNotes(s.toString());
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void loadNotes() {
        if (mAuth.getCurrentUser() == null) return;
        String userId = mAuth.getCurrentUser().getUid();

        notesListener = db.collection("users").document(userId).collection("notes")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e("NotesActivity", "Listen failed.", error);
                        return;
                    }
                    if (value != null) {
                        allNotes.clear();
                        for (com.google.firebase.firestore.DocumentSnapshot doc : value.getDocuments()) {
                            Note note = doc.toObject(Note.class);
                            if (note != null) {
                                note.setId(doc.getId());
                                allNotes.add(note);
                            }
                        }
                        tvNotesCount.setText("YOUR NOTES (" + allNotes.size() + ")");
                        filterNotes(etSearchNotes.getText().toString());
                    }
                });
    }

    private void filterNotes(String query) {
        ArrayList<Note> filteredNotes = new ArrayList<>();
        String search = query.trim().toLowerCase(Locale.ROOT);

        for (Note note : allNotes) {
            boolean matches =
                    note.getTitle().toLowerCase(Locale.ROOT).contains(search)
                            || note.getSubject().toLowerCase(Locale.ROOT).contains(search)
                            || note.getContent().toLowerCase(Locale.ROOT).contains(search);

            if (matches) {
                filteredNotes.add(note);
            }
        }

        notesAdapter.setNotes(filteredNotes);
        boolean isEmpty = filteredNotes.isEmpty();

        layoutEmptyNotes.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        rvNotes.setVisibility(isEmpty ? View.GONE : View.VISIBLE);

        if (allNotes.isEmpty()) {
            tvEmptyMessage.setText("Save your first note to keep important concepts in one place.");
        } else if (isEmpty) {
            tvEmptyMessage.setText("No notes match your search. Try another keyword.");
        }
    }

    private void showNoteDetailDialog(Note note) {
        if (note == null) return;

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(20);
        layout.setPadding(padding, dp(16), padding, dp(16));

        // 1. Subject pill badge
        TextView tvSubjectBadge = new TextView(this);
        tvSubjectBadge.setText(note.getSubject() != null ? note.getSubject().toUpperCase(Locale.ROOT) : "COURSE");
        tvSubjectBadge.setTextSize(11);
        tvSubjectBadge.setTextColor(getColor(R.color.primary));
        tvSubjectBadge.setTypeface(null, android.graphics.Typeface.BOLD);
        tvSubjectBadge.setPadding(dp(10), dp(4), dp(10), dp(4));
        tvSubjectBadge.setBackgroundResource(R.drawable.bg_badge_primary_light);
        
        LinearLayout.LayoutParams subjectParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        subjectParams.bottomMargin = dp(8);
        layout.addView(tvSubjectBadge, subjectParams);

        // 2. Note Title
        TextView tvTitle = new TextView(this);
        tvTitle.setText(note.getTitle());
        tvTitle.setTextSize(22);
        int titleColor = com.google.android.material.color.MaterialColors.getColor(
                this,
                com.google.android.material.R.attr.colorOnSurface,
                getColor(R.color.text_primary)
        );
        tvTitle.setTextColor(titleColor);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(tvTitle);

        // 3. Attachment section
        if (note.getFileName() != null && !note.getFileName().trim().isEmpty()) {
            LinearLayout attachCard = new LinearLayout(this);
            attachCard.setOrientation(LinearLayout.HORIZONTAL);
            attachCard.setGravity(android.view.Gravity.CENTER_VERTICAL);
            attachCard.setBackgroundResource(R.drawable.bg_card);
            attachCard.setPadding(dp(14), dp(12), dp(14), dp(12));

            TextView tvFile = new TextView(this);
            tvFile.setText("📎 " + note.getFileName());
            tvFile.setTextSize(14);
            tvFile.setTextColor(getColor(R.color.text_primary));
            tvFile.setTypeface(null, android.graphics.Typeface.BOLD);
            LinearLayout.LayoutParams fileParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            attachCard.addView(tvFile, fileParams);

            MaterialButton btnViewPdf = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            btnViewPdf.setText("Open Material");
            btnViewPdf.setTextSize(12);
            btnViewPdf.setOnClickListener(v -> openAttachedMaterial(note));
            attachCard.addView(btnViewPdf);

            LinearLayout.LayoutParams attachCardParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            attachCardParams.topMargin = dp(14);
            attachCardParams.bottomMargin = dp(12);
            layout.addView(attachCard, attachCardParams);
        }

        // 4. ✨ Gemini AI Concise Revision Summary Card
        LinearLayout aiSummaryCard = new LinearLayout(this);
        aiSummaryCard.setOrientation(LinearLayout.VERTICAL);
        aiSummaryCard.setBackgroundResource(R.drawable.bg_badge_primary_light);
        aiSummaryCard.setPadding(dp(16), dp(14), dp(16), dp(14));

        TextView tvSummaryHeader = new TextView(this);
        tvSummaryHeader.setText("✨ AI Revision Summary (Gemini)");
        tvSummaryHeader.setTextSize(14);
        tvSummaryHeader.setTextColor(getColor(R.color.primary));
        tvSummaryHeader.setTypeface(null, android.graphics.Typeface.BOLD);
        aiSummaryCard.addView(tvSummaryHeader);

        TextView tvSummaryContent = new TextView(this);
        tvSummaryContent.setTextSize(13);
        tvSummaryContent.setTextColor(getColor(R.color.text_primary));
        tvSummaryContent.setLineSpacing(dp(3), 1.15f);
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        contentParams.topMargin = dp(8);
        aiSummaryCard.addView(tvSummaryContent, contentParams);

        MaterialButton btnGenerateSummary = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnGenerateSummary.setText("✨ Generate AI Concise Summary");
        btnGenerateSummary.setTextSize(12);
        btnGenerateSummary.setVisibility(View.GONE);
        btnGenerateSummary.setOnClickListener(v -> {
            btnGenerateSummary.setEnabled(false);
            tvSummaryContent.setText("Generating concise revision summary with Gemini...");
            requestNoteSummary(note, tvSummaryContent, btnGenerateSummary);
        });
        LinearLayout.LayoutParams btnGenParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        btnGenParams.topMargin = dp(8);
        aiSummaryCard.addView(btnGenerateSummary, btnGenParams);

        if (note.getAiSummary() != null && !note.getAiSummary().trim().isEmpty()) {
            tvSummaryContent.setText(note.getAiSummary().trim());
        } else {
            tvSummaryContent.setText("Generating concise revision summary with Gemini...");
            requestNoteSummary(note, tvSummaryContent, btnGenerateSummary);
        }

        LinearLayout.LayoutParams summaryCardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        summaryCardParams.topMargin = dp(10);
        summaryCardParams.bottomMargin = dp(14);
        layout.addView(aiSummaryCard, summaryCardParams);

        // 5. Preview of note content / extracted text
        if (note.getContent() != null && !note.getContent().trim().isEmpty()) {
            TextView tvContentHeader = new TextView(this);
            tvContentHeader.setText("Notes / Material Excerpt");
            tvContentHeader.setTextSize(12);
            tvContentHeader.setTextColor(getColor(R.color.text_secondary));
            tvContentHeader.setTypeface(null, android.graphics.Typeface.BOLD);
            layout.addView(tvContentHeader);

            TextView tvContent = new TextView(this);
            tvContent.setText(note.getContent().trim());
            tvContent.setTextSize(13);
            tvContent.setTextColor(getColor(R.color.text_primary));
            tvContent.setMaxLines(7);
            tvContent.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            previewParams.topMargin = dp(4);
            previewParams.bottomMargin = dp(12);
            layout.addView(tvContent, previewParams);
        }

        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        scrollView.addView(layout);

        new MaterialAlertDialogBuilder(this)
                .setView(scrollView)
                .setPositiveButton("💬 Ask AI Tutor", (d, which) -> {
                    Intent intent = new Intent(NotesActivity.this, AssistantActivity.class);
                    intent.putExtra(AssistantActivity.EXTRA_CONTEXT, note.getContent());
                    intent.putExtra(AssistantActivity.EXTRA_SUBJECT, note.getSubject());
                    intent.putExtra(AssistantActivity.EXTRA_NOTE_TITLE, note.getTitle());
                    intent.putExtra(AssistantActivity.EXTRA_NOTE_ID, note.getId());
                    startActivity(intent);
                })
                .setNeutralButton("⚡ Adaptive Quiz", (d, which) -> {
                    Intent intent = new Intent(NotesActivity.this, QuizActivity.class);
                    intent.putExtra(QuizActivity.EXTRA_SUBJECT, note.getSubject());
                    intent.putExtra(QuizActivity.EXTRA_MATERIAL, note.getContent());
                    intent.putExtra(QuizActivity.EXTRA_NOTE_TITLE, note.getTitle());
                    intent.putExtra(QuizActivity.EXTRA_NOTE_ID, note.getId());
                    startActivity(intent);
                })
                .setNegativeButton("✏️ Edit Note", (d, which) -> showAddNoteDialog(note))
                .show();
    }

    private void openAttachedMaterial(Note note) {
        if (note == null || note.getFileUrl() == null) {
            Toast.makeText(this, "No material file attached", Toast.LENGTH_SHORT).show();
            return;
        }

        String fileUrl = note.getFileUrl();
        try {
            if (fileUrl.startsWith("http://") || fileUrl.startsWith("https://")) {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(fileUrl));
                startActivity(browserIntent);
            } else {
                File file = new File(fileUrl);
                if (file.exists()) {
                    Uri contentUri = androidx.core.content.FileProvider.getUriForFile(
                            this,
                            getPackageName() + ".provider",
                            file
                    );
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(contentUri, "application/pdf");
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(intent, "Open " + note.getFileName()));
                } else {
                    Toast.makeText(this, "File: " + note.getFileName(), Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Opening material: " + note.getFileName(), Toast.LENGTH_SHORT).show();
        }
    }

    private void requestNoteSummary(Note note, TextView tvSummary, View btnGenerate) {
        executor.execute(() -> {
            try {
                URL url = new URL("http://10.0.2.2:3000/api/summarize");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(60000);
                conn.setDoOutput(true);

                JSONObject body = new JSONObject();
                body.put("text", note.getContent());
                body.put("subject", note.getSubject());
                body.put("title", note.getTitle());
                if (note.getFileUrl() != null) {
                    body.put("fileUrl", note.getFileUrl());
                }

                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(bytes);
                    os.flush();
                }

                if (conn.getResponseCode() == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();

                    JSONObject res = new JSONObject(sb.toString());
                    String summary = res.optString("aiSummary", "").trim();
                    String extracted = res.optString("extractedText", "").trim();

                    if (!summary.isEmpty()) {
                        note.setAiSummary(summary);
                        if ((note.getContent() == null || note.getContent().trim().isEmpty()) && !extracted.isEmpty()) {
                            note.setContent(extracted);
                        }

                        if (mAuth.getCurrentUser() != null && note.getId() != null) {
                            String userId = mAuth.getCurrentUser().getUid();
                            java.util.Map<String, Object> updates = new java.util.HashMap<>();
                            updates.put("aiSummary", summary);
                            if (note.getContent() != null && !note.getContent().isEmpty()) {
                                updates.put("content", note.getContent());
                            }
                            db.collection("users").document(userId).collection("notes")
                                    .document(note.getId())
                                    .update(updates);
                        }

                        runOnUiThread(() -> {
                            tvSummary.setText(summary);
                            if (btnGenerate != null) btnGenerate.setVisibility(View.GONE);
                        });
                        return;
                    }
                }
            } catch (Exception e) {
                Log.e("NotesActivity", "Summary request error: " + e.getMessage());
            }

            runOnUiThread(() -> {
                if (btnGenerate != null) {
                    btnGenerate.setVisibility(View.VISIBLE);
                    btnGenerate.setEnabled(true);
                }
                tvSummary.setText("Tap below to generate a concise revision summary with Gemini.");
            });
        });
    }

    private void showAddNoteDialog(Note noteToEdit) {
        pendingUploadUri = null;
        pendingUploadName = null;

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);

        int padding = dp(20);
        form.setPadding(padding, dp(8), padding, 0);

        EditText etTitle = createInput("Note title");
        EditText etSubject = createInput("Subject (e.g. Computer Science 301)");
        EditText etContent = createInput("Write or paste your notes/material here...");
        currentEtContent = etContent;

        etTitle.setSingleLine(true);
        etSubject.setSingleLine(true);

        etContent.setMinLines(5);
        etContent.setMaxLines(10);
        etContent.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        etContent.setInputType(
                android.text.InputType.TYPE_CLASS_TEXT
                        | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                        | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        );

        if (noteToEdit != null) {
            etTitle.setText(noteToEdit.getTitle());
            etSubject.setText(noteToEdit.getSubject());
            etContent.setText(noteToEdit.getContent());
        }

        tvAttachedFileStatus = new TextView(this);
        tvAttachedFileStatus.setTextSize(12);
        tvAttachedFileStatus.setTextColor(getColor(R.color.primary));
        if (noteToEdit != null && noteToEdit.getFileName() != null) {
            tvAttachedFileStatus.setText("Attached: " + noteToEdit.getFileName());
            tvAttachedFileStatus.setVisibility(View.VISIBLE);
        } else {
            tvAttachedFileStatus.setVisibility(View.GONE);
        }

        MaterialButton btnAttach = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnAttach.setText("Upload Subject Material (PDF/Text/Image)");
        btnAttach.setOnClickListener(v -> filePickerLauncher.launch("*/*"));

        form.addView(etTitle, createFieldParams());
        form.addView(etSubject, createFieldParams());
        form.addView(etContent, createFieldParams());
        form.addView(btnAttach, createFieldParams());
        form.addView(tvAttachedFileStatus, createFieldParams());

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(noteToEdit == null ? "Create a new note" : "Edit note")
                .setView(form)
                .setNegativeButton("Cancel", (d, which) -> d.dismiss())
                .setPositiveButton("Save note", null);

        androidx.appcompat.app.AlertDialog dialog = builder.create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String title = etTitle.getText().toString().trim();
                String subject = etSubject.getText().toString().trim();
                String content = etContent.getText().toString().trim();

                if (title.isEmpty()) {
                    etTitle.setError("Enter a note title");
                    etTitle.requestFocus();
                    return;
                }
                if (subject.isEmpty()) {
                    etSubject.setError("Enter a subject");
                    etSubject.requestFocus();
                    return;
                }
                if (content.isEmpty() && pendingUploadUri == null) {
                    etContent.setError("Enter some note content or attach a file");
                    etContent.requestFocus();
                    return;
                }

                if (mAuth.getCurrentUser() == null) return;
                String userId = mAuth.getCurrentUser().getUid();

                // Alert box immediately closes upon saving!
                dialog.dismiss();

                Toast.makeText(this, pendingUploadUri != null ? "Saving note & ingesting material with Gemini..." : "Saving note...", Toast.LENGTH_SHORT).show();

                if (pendingUploadUri != null) {
                    uploadMaterialFile(userId, noteToEdit, title, subject, content, pendingUploadUri, pendingUploadName);
                } else {
                    String existingUrl = noteToEdit != null ? noteToEdit.getFileUrl() : null;
                    String existingName = noteToEdit != null ? noteToEdit.getFileName() : null;
                    String existingSummary = noteToEdit != null ? noteToEdit.getAiSummary() : null;
                    saveNoteToFirestore(userId, noteToEdit, title, subject, content, existingUrl, existingName, existingSummary);
                }
            });
        });

        dialog.show();
    }

    private void uploadMaterialFile(String userId, Note noteToEdit, String title, String subject,
                                    String content, Uri fileUri, String fileName) {
        executor.execute(() -> {
            try {
                String boundary = "===" + System.currentTimeMillis() + "===";
                URL url = new URL(UPLOAD_API_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setDoOutput(true);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(60000);

                OutputStream os = conn.getOutputStream();
                PrintWriter writer = new PrintWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8), true);

                writer.append("--" + boundary).append("\r\n");
                writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"").append("\r\n");
                writer.append("Content-Type: application/octet-stream").append("\r\n");
                writer.append("\r\n").flush();

                InputStream is = getContentResolver().openInputStream(fileUri);
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
                os.flush();
                is.close();

                writer.append("\r\n").flush();
                writer.append("--" + boundary + "--").append("\r\n").flush();
                writer.close();

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();

                    JSONObject resJson = new JSONObject(sb.toString());
                    String fileUrl = resJson.optString("fileUrl");
                    String extractedText = resJson.optString("extractedText", "");
                    String aiSummary = resJson.optString("aiSummary", "");

                    String finalContent = content;
                    if ((finalContent == null || finalContent.trim().isEmpty()) && !extractedText.isEmpty()) {
                        finalContent = extractedText;
                    }

                    final String noteContentToSave = finalContent;
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Material uploaded & ingested with Gemini!", Toast.LENGTH_SHORT).show();
                        saveNoteToFirestore(userId, noteToEdit, title, subject, noteContentToSave, fileUrl, fileName, aiSummary);
                    });
                    return;
                }
            } catch (Exception e) {
                Log.e("NotesActivity", "Upload to StudySphere backend failed: " + e.getMessage());
            }

            // Fallback: Copy to app's internal private storage so the file is stored locally on device for free
            try {
                File internalDir = new File(getFilesDir(), "materials");
                if (!internalDir.exists()) internalDir.mkdirs();
                File destFile = new File(internalDir, System.currentTimeMillis() + "_" + fileName);

                InputStream in = getContentResolver().openInputStream(fileUri);
                FileOutputStream out = new FileOutputStream(destFile);
                byte[] buf = new byte[4096];
                int len;
                while ((len = in.read(buf)) != -1) {
                    out.write(buf, 0, len);
                }
                in.close();
                out.close();

                String localPath = destFile.getAbsolutePath();
                runOnUiThread(() -> {
                    Toast.makeText(this, "Saved file to local storage", Toast.LENGTH_SHORT).show();
                    saveNoteToFirestore(userId, noteToEdit, title, subject, content, localPath, fileName, null);
                });
            } catch (Exception fallbackEx) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "File upload failed, saving note text only", Toast.LENGTH_SHORT).show();
                    saveNoteToFirestore(userId, noteToEdit, title, subject, content, null, null, null);
                });
            }
        });
    }

    private void saveNoteToFirestore(String userId, Note noteToEdit, String title, String subject, 
                                    String content, String fileUrl, String fileName, String aiSummary) {
        com.google.firebase.firestore.CollectionReference notesRef = 
                db.collection("users").document(userId).collection("notes");

        Note note = new Note();
        note.setTitle(title);
        note.setSubject(subject);
        note.setContent(content);
        note.setCreatedAt(noteToEdit != null ? noteToEdit.getCreatedAt() : System.currentTimeMillis());
        if (fileUrl != null) note.setFileUrl(fileUrl);
        if (fileName != null) note.setFileName(fileName);

        String summaryToSave = aiSummary;
        if ((summaryToSave == null || summaryToSave.trim().isEmpty()) && noteToEdit != null) {
            summaryToSave = noteToEdit.getAiSummary();
        }
        if (summaryToSave != null && !summaryToSave.trim().isEmpty()) {
            note.setAiSummary(summaryToSave.trim());
        }

        String noteId = noteToEdit != null ? noteToEdit.getId() : notesRef.document().getId();

        notesRef.document(noteId).set(note).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Toast.makeText(this, "Note saved successfully!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Unable to save note. Please try again.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private EditText createInput(String hint) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setTextSize(15);
        editText.setPadding(dp(12), dp(12), dp(12), dp(12));
        return editText;
    }

    private LinearLayout.LayoutParams createFieldParams() {
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        params.bottomMargin = dp(12);
        return params;
    }

    private void confirmDeleteNote(Note note) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete note?")
                .setMessage("Are you sure you want to delete \"" + note.getTitle() + "\"? This cannot be undone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    if (mAuth.getCurrentUser() == null) return;
                    String userId = mAuth.getCurrentUser().getUid();

                    db.collection("users").document(userId).collection("notes").document(note.getId())
                            .delete().addOnCompleteListener(task -> {
                                if (task.isSuccessful()) {
                                    Toast.makeText(this, "Note deleted", Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(this, "Unable to delete note", Toast.LENGTH_SHORT).show();
                                }
                            });
                })
                .show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (notesListener != null) {
            notesListener.remove();
        }
    }
}
