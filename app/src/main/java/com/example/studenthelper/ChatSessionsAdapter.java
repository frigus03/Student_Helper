package com.example.studenthelper;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatSessionsAdapter extends RecyclerView.Adapter<ChatSessionsAdapter.SessionViewHolder> {

    public interface OnSessionClickListener {
        void onSessionClick(ChatSession session);
        void onSessionDelete(ChatSession session);
    }

    private final List<ChatSession> sessions = new ArrayList<>();
    private final OnSessionClickListener listener;
    private String selectedSessionId = null;

    public ChatSessionsAdapter(OnSessionClickListener listener) {
        this.listener = listener;
    }

    public void setSessions(List<ChatSession> newSessions, String currentSessionId) {
        this.sessions.clear();
        this.sessions.addAll(newSessions);
        this.selectedSessionId = currentSessionId;
        notifyDataSetChanged();
    }

    public void setSelectedSessionId(String sessionId) {
        this.selectedSessionId = sessionId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SessionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat_session, parent, false);
        return new SessionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SessionViewHolder holder, int position) {
        ChatSession session = sessions.get(position);

        holder.tvTitle.setText(session.getTitle() != null ? session.getTitle() : "Study Chat");

        if (session.getUpdatedAt() > 0) {
            String dateStr = new SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                    .format(new Date(session.getUpdatedAt()));
            holder.tvDate.setText(dateStr);
        } else {
            holder.tvDate.setText("Recently");
        }

        boolean isSelected = session.getSessionId() != null && session.getSessionId().equals(selectedSessionId);
        holder.itemView.setAlpha(isSelected ? 1.0f : 0.85f);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSessionClick(session);
            }
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSessionDelete(session);
            }
        });
    }

    @Override
    public int getItemCount() {
        return sessions.size();
    }

    static class SessionViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle;
        TextView tvDate;
        ImageButton btnDelete;

        public SessionViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvSessionTitle);
            tvDate = itemView.findViewById(R.id.tvSessionDate);
            btnDelete = itemView.findViewById(R.id.btnDeleteSession);
        }
    }
}
