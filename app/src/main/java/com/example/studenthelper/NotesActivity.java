package com.example.studenthelper;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Locale;

public class NotesActivity extends AppCompatActivity {

    private DatabaseHelper databaseHelper;
    private NotesAdapter notesAdapter;

    private RecyclerView rvNotes;
    private View layoutEmptyNotes;
    private TextView tvNotesCount;
    private TextView tvEmptyMessage;
    private EditText etSearchNotes;

    private ArrayList<Note> allNotes = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notes);

        databaseHelper = new DatabaseHelper(this);

        initializeViews();
        setupRecyclerView();
        setupListeners();
        loadNotes();
    }

    private void initializeViews() {
        rvNotes = findViewById(R.id.rvNotes);
        layoutEmptyNotes = findViewById(R.id.layoutEmptyNotes);
        tvNotesCount = findViewById(R.id.tvNotesCount);
        tvEmptyMessage = findViewById(R.id.tvEmptyMessage);
        etSearchNotes = findViewById(R.id.etSearchNotes);

        MaterialButton btnAddNote = findViewById(R.id.btnAddNote);
        btnAddNote.setOnClickListener(v -> showAddNoteDialog());
    }

    private void setupRecyclerView() {
        rvNotes.setLayoutManager(new LinearLayoutManager(this));

        notesAdapter = new NotesAdapter(new NotesAdapter.NoteListener() {
            @Override
            public void onNoteClick(Note note) {
                showNoteDetails(note);
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
            public void beforeTextChanged(
                    CharSequence s,
                    int start,
                    int count,
                    int after
            ) {
                // Not needed.
            }

            @Override
            public void onTextChanged(
                    CharSequence s,
                    int start,
                    int before,
                    int count
            ) {
                filterNotes(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Not needed.
            }
        });
    }

    private void loadNotes() {
        allNotes = databaseHelper.getAllNotes();

        tvNotesCount.setText(
                "YOUR NOTES (" + allNotes.size() + ")"
        );

        filterNotes(etSearchNotes.getText().toString());
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
            tvEmptyMessage.setText(
                    "Save your first note to keep important concepts in one place."
            );
        } else if (isEmpty) {
            tvEmptyMessage.setText(
                    "No notes match your search. Try another keyword."
            );
        }
    }

    private void showAddNoteDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);

        int padding = dp(20);
        form.setPadding(padding, dp(8), padding, 0);

        EditText etTitle = createInput("Note title");
        EditText etSubject = createInput("Subject (e.g. Machine Learning)");
        EditText etContent = createInput("Write your notes here...");

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

        form.addView(etTitle, createFieldParams());
        form.addView(etSubject, createFieldParams());
        form.addView(etContent, createFieldParams());

        androidx.appcompat.app.AlertDialog dialog =
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Create a new note")
                        .setView(form)
                        .setNegativeButton("Cancel", (d, which) -> d.dismiss())
                        .setPositiveButton("Save note", null)
                        .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(
                    androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener(v -> {

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

                if (content.isEmpty()) {
                    etContent.setError("Enter some note content");
                    etContent.requestFocus();
                    return;
                }

                long result = databaseHelper.insertNote(
                        title,
                        subject,
                        content
                );

                if (result != -1) {
                    Toast.makeText(
                            this,
                            "Note saved successfully!",
                            Toast.LENGTH_SHORT
                    ).show();

                    dialog.dismiss();
                    loadNotes();

                } else {
                    Toast.makeText(
                            this,
                            "Unable to save note. Please try again.",
                            Toast.LENGTH_LONG
                    ).show();
                }
            });
        });

        dialog.show();
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

    private void showNoteDetails(Note note) {
        TextView contentView = new TextView(this);

        contentView.setText(note.getContent());
        contentView.setTextSize(16);
        contentView.setTextColor(
                getResources().getColor(R.color.text_primary)
        );
        contentView.setPadding(dp(24), dp(12), dp(24), dp(16));
        contentView.setTextIsSelectable(true);
        contentView.setLineSpacing(dp(4), 1f);

        new MaterialAlertDialogBuilder(this)
                .setTitle(note.getTitle() + "\n" + note.getSubject())
                .setView(contentView)
                .setPositiveButton("Close", null)
                .show();
    }

    private void confirmDeleteNote(Note note) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete note?")
                .setMessage(
                        "Are you sure you want to delete \""
                                + note.getTitle()
                                + "\"? This cannot be undone."
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {

                    boolean deleted =
                            databaseHelper.deleteNote(note.getId());

                    if (deleted) {
                        Toast.makeText(
                                this,
                                "Note deleted",
                                Toast.LENGTH_SHORT
                        ).show();

                        loadNotes();
                    } else {
                        Toast.makeText(
                                this,
                                "Unable to delete note",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                })
                .show();
    }

    private int dp(int value) {
        return Math.round(
                value * getResources().getDisplayMetrics().density
        );
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (databaseHelper != null) {
            databaseHelper.close();
        }
    }
}