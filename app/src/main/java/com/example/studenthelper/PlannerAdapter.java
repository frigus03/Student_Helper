package com.example.studenthelper;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.checkbox.MaterialCheckBox;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class PlannerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface TaskListener {
        void onCompletionChanged(StudyTask task, boolean completed);
        void onAiBreakdown(StudyTask task);
        void onCalendarSync(StudyTask task);
    }

    private final ArrayList<PlannerListItem> items = new ArrayList<>();
    private final TaskListener listener;

    public PlannerAdapter(TaskListener listener) {
        this.listener = listener;
    }

    public void setItems(ArrayList<PlannerListItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).getType();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == PlannerListItem.TYPE_HEADER) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_planner_header, parent, false);
            return new HeaderViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_task_card, parent, false);
            return new TaskViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        PlannerListItem item = items.get(position);

        if (holder.getItemViewType() == PlannerListItem.TYPE_HEADER) {
            HeaderItem headerItem = (HeaderItem) item;
            HeaderViewHolder headerHolder = (HeaderViewHolder) holder;
            headerHolder.tvHeaderTitle.setText(headerItem.getTitle());
            headerHolder.tvHeaderCount.setText("(" + headerItem.getCount() + ")");
        } else {
            TaskItem taskItem = (TaskItem) item;
            StudyTask task = taskItem.getTask();
            TaskViewHolder taskHolder = (TaskViewHolder) holder;

            taskHolder.tvTitle.setText(task.getTitle() != null ? task.getTitle() : "Untitled Task");
            taskHolder.tvSubject.setText(task.getSubject() != null ? task.getSubject() : "No Subject");
            taskHolder.tvCategory.setText(task.getCategory() != null ? task.getCategory() : "Study");

            String timeStr = new SimpleDateFormat("MMM dd, h:mm a", Locale.getDefault())
                    .format(new Date(task.getDueDate()));

            boolean isOverdue = !task.isCompleted() && task.getDueDate() < System.currentTimeMillis();
            boolean isHighPriority = "HIGH".equalsIgnoreCase(task.getPriority());

            if (isOverdue || isHighPriority) {
                taskHolder.viewPriorityAccent.setBackgroundColor(holder.itemView.getContext().getColor(R.color.error));
            } else if ("LOW".equalsIgnoreCase(task.getPriority())) {
                taskHolder.viewPriorityAccent.setBackgroundColor(holder.itemView.getContext().getColor(R.color.success));
            } else {
                taskHolder.viewPriorityAccent.setBackgroundColor(holder.itemView.getContext().getColor(R.color.warning));
            }

            if (isOverdue) {
                taskHolder.tvOverdueBadge.setVisibility(View.VISIBLE);
            } else {
                taskHolder.tvOverdueBadge.setVisibility(View.GONE);
            }

            taskHolder.checkCompleted.setOnCheckedChangeListener(null);
            taskHolder.checkCompleted.setChecked(task.isCompleted());

            if (task.isCompleted()) {
                taskHolder.tvTitle.setPaintFlags(taskHolder.tvTitle.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                taskHolder.tvTitle.setTextColor(holder.itemView.getContext().getColor(R.color.text_tertiary));
                taskHolder.tvTime.setText("Completed • " + task.getDurationMinutes() + " mins");
            } else {
                taskHolder.tvTitle.setPaintFlags(taskHolder.tvTitle.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
                taskHolder.tvTitle.setTextColor(holder.itemView.getContext().getColor(R.color.text_primary));
                String dueFormatted = "📅 " + timeStr + (task.getTime() != null && !task.getTime().isEmpty() ? " (" + task.getTime() + ")" : "") + " • ⏱️ " + task.getDurationMinutes() + " mins";
                taskHolder.tvTime.setText(dueFormatted);
            }

            // Bind Actionable Notes ("What to do")
            if (taskHolder.layoutTaskNotes != null && taskHolder.tvTaskNotes != null) {
                if (task.getNotes() != null && !task.getNotes().trim().isEmpty()) {
                    taskHolder.layoutTaskNotes.setVisibility(View.VISIBLE);
                    taskHolder.tvTaskNotes.setText(task.getNotes().trim());
                } else {
                    taskHolder.layoutTaskNotes.setVisibility(View.GONE);
                }
            }

            taskHolder.checkCompleted.setOnCheckedChangeListener((buttonView, checked) -> {
                if (checked != task.isCompleted()) {
                    listener.onCompletionChanged(task, checked);
                }
            });

            taskHolder.btnCalendarSync.setOnClickListener(v -> listener.onCalendarSync(task));
            taskHolder.btnAiBreakdown.setOnClickListener(v -> listener.onAiBreakdown(task));
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvHeaderTitle;
        TextView tvHeaderCount;

        HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvHeaderTitle = itemView.findViewById(R.id.tvHeaderTitle);
            tvHeaderCount = itemView.findViewById(R.id.tvHeaderCount);
        }
    }

    static class TaskViewHolder extends RecyclerView.ViewHolder {
        View viewPriorityAccent;
        MaterialCheckBox checkCompleted;
        TextView tvTitle;
        TextView tvTime;
        TextView tvOverdueBadge;
        TextView tvSubject;
        TextView tvCategory;
        View layoutTaskNotes;
        TextView tvTaskNotes;
        View btnCalendarSync;
        View btnAiBreakdown;

        TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            viewPriorityAccent = itemView.findViewById(R.id.viewPriorityAccent);
            checkCompleted = itemView.findViewById(R.id.checkTaskCompleted);
            tvTitle = itemView.findViewById(R.id.tvTaskTitle);
            tvTime = itemView.findViewById(R.id.tvTaskTime);
            tvOverdueBadge = itemView.findViewById(R.id.tvOverdueBadge);
            tvSubject = itemView.findViewById(R.id.tvTaskSubject);
            tvCategory = itemView.findViewById(R.id.tvTaskCategory);
            layoutTaskNotes = itemView.findViewById(R.id.layoutTaskNotes);
            tvTaskNotes = itemView.findViewById(R.id.tvTaskNotes);
            btnCalendarSync = itemView.findViewById(R.id.btnCalendarSync);
            btnAiBreakdown = itemView.findViewById(R.id.btnAiBreakdown);
        }
    }
}
