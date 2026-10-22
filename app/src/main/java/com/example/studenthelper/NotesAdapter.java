package com.example.studenthelper;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class NotesAdapter extends RecyclerView.Adapter<NotesAdapter.NoteViewHolder> {

    public interface NoteListener {
        void onNoteClick(Note note);
        void onDeleteClick(Note note);
        void onAiTutorClick(Note note);
        void onQuizClick(Note note);
    }

    private final ArrayList<Note> notes = new ArrayList<>();
    private final NoteListener listener;

    public NotesAdapter(NoteListener listener) {
        this.listener = listener;
    }

    public void setNotes(ArrayList<Note> newNotes) {
        notes.clear();
        notes.addAll(newNotes);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public NoteViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_note, parent, false);

        return new NoteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull NoteViewHolder holder,
            int position
    ) {
        Note note = notes.get(position);

        holder.tvTitle.setText(note.getTitle());
        holder.tvSubject.setText(note.getSubject());

        String preview = note.getContent();
        if (note.getFileName() != null && !note.getFileName().isEmpty()) {
            preview = "📎 " + note.getFileName() + (preview != null && !preview.isEmpty() ? "\n" + preview : "");
        }
        holder.tvPreview.setText(preview);

        String date = new SimpleDateFormat(
                "dd MMM",
                Locale.getDefault()
        ).format(new Date(note.getCreatedAt()));

        holder.tvDate.setText(date);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onNoteClick(note);
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDeleteClick(note);
        });

        if (holder.btnAskAi != null) {
            holder.btnAskAi.setOnClickListener(v -> {
                if (listener != null) listener.onAiTutorClick(note);
            });
        }

        if (holder.btnQuiz != null) {
            holder.btnQuiz.setOnClickListener(v -> {
                if (listener != null) listener.onQuizClick(note);
            });
        }
    }

    @Override
    public int getItemCount() {
        return notes.size();
    }

    static class NoteViewHolder extends RecyclerView.ViewHolder {

        TextView tvSubject;
        TextView tvDate;
        TextView tvTitle;
        TextView tvPreview;
        ImageButton btnDelete;
        MaterialButton btnAskAi;
        MaterialButton btnQuiz;

        NoteViewHolder(@NonNull View itemView) {
            super(itemView);

            tvSubject = itemView.findViewById(R.id.tvNoteSubject);
            tvDate = itemView.findViewById(R.id.tvNoteDate);
            tvTitle = itemView.findViewById(R.id.tvNoteTitle);
            tvPreview = itemView.findViewById(R.id.tvNotePreview);
            btnDelete = itemView.findViewById(R.id.btnDeleteNote);
            btnAskAi = itemView.findViewById(R.id.btnNoteAskAi);
            btnQuiz = itemView.findViewById(R.id.btnNoteQuiz);
        }
    }
}