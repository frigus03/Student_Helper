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
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Locale;

public class NotesActivity extends AppCompatActivity {

    private NotesAdapter notesAdapter;

    private RecyclerView rvNotes;
    private View layoutEmptyNotes;
    private TextView tvNotesCount;
    private TextView tvEmptyMessage;
    private EditText etSearchNotes;

    private final ArrayList<Note> allNotes = new ArrayList<>();
    
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private FirebaseStorage storage;
    private ListenerRegistration notesListener;

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
        storage = FirebaseStorage.getInstance();

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
                showAddNoteDialog(note);
            }

            @Override
            public void onDeleteClick(Note note) {
                confirmDeleteNote(note);
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

        if (noteToEdit != null) {
            builder.setNeutralButton("Adaptive Quiz", (d, which) -> {
                Intent intent = new Intent(NotesActivity.this, QuizActivity.class);
                intent.putExtra(QuizActivity.EXTRA_SUBJECT, noteToEdit.getSubject());
                intent.putExtra(QuizActivity.EXTRA_MATERIAL, noteToEdit.getContent());
                startActivity(intent);
            });
        }

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

                dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                Toast.makeText(this, "Saving note...", Toast.LENGTH_SHORT).show();

                if (pendingUploadUri != null) {
                    // Upload to Firebase Cloud Storage (Free Spark Tier)
                    String fileName = System.currentTimeMillis() + "_" + pendingUploadName;
                    StorageReference fileRef = storage.getReference()
                            .child("users")
                            .child(userId)
                            .child("notes")
                            .child(fileName);

                    fileRef.putFile(pendingUploadUri)
                            .continueWithTask(task -> {
                                if (!task.isSuccessful() && task.getException() != null) {
                                    throw task.getException();
                                }
                                return fileRef.getDownloadUrl();
                            })
                            .addOnSuccessListener(downloadUrl -> {
                                saveNoteToFirestore(userId, noteToEdit, title, subject, content, 
                                        downloadUrl.toString(), pendingUploadName, dialog);
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, "File upload failed, saving note text only", Toast.LENGTH_SHORT).show();
                                saveNoteToFirestore(userId, noteToEdit, title, subject, content, null, null, dialog);
                            });
                } else {
                    String existingUrl = noteToEdit != null ? noteToEdit.getFileUrl() : null;
                    String existingName = noteToEdit != null ? noteToEdit.getFileName() : null;
                    saveNoteToFirestore(userId, noteToEdit, title, subject, content, existingUrl, existingName, dialog);
                }
            });
        });

        dialog.show();
    }

    private void saveNoteToFirestore(String userId, Note noteToEdit, String title, String subject, 
                                    String content, String fileUrl, String fileName, 
                                    androidx.appcompat.app.AlertDialog dialog) {
        com.google.firebase.firestore.CollectionReference notesRef = 
                db.collection("users").document(userId).collection("notes");

        Note note = new Note();
        note.setTitle(title);
        note.setSubject(subject);
        note.setContent(content);
        note.setCreatedAt(noteToEdit != null ? noteToEdit.getCreatedAt() : System.currentTimeMillis());
        if (fileUrl != null) note.setFileUrl(fileUrl);
        if (fileName != null) note.setFileName(fileName);

        String noteId = noteToEdit != null ? noteToEdit.getId() : notesRef.document().getId();

        notesRef.document(noteId).set(note).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Toast.makeText(this, "Note saved successfully!", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            } else {
                Toast.makeText(this, "Unable to save note. Please try again.", Toast.LENGTH_LONG).show();
                dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setEnabled(true);
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
