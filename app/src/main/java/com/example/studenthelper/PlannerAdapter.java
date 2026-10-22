package com.example.studenthelper;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.checkbox.MaterialCheckBox;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class PlannerAdapter
        extends RecyclerView.Adapter<PlannerAdapter.TaskViewHolder> {

    public interface TaskListener {
        void onCompletionChanged(StudyTask task, boolean completed);
        void onDeleteTask(StudyTask task);
    }

    private final ArrayList<StudyTask> tasks = new ArrayList<>();
    private final TaskListener listener;

    public PlannerAdapter(TaskListener listener) {
        this.listener = listener;
    }

    public void setTasks(ArrayList<StudyTask> newTasks) {
        tasks.clear();
        tasks.addAll(newTasks);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_task, parent, false);

        return new TaskViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull TaskViewHolder holder,
            int position
    ) {
        StudyTask task = tasks.get(position);

        holder.tvTitle.setText(task.getTitle());
        holder.tvSubject.setText(task.getSubject());

        String dueDate = new SimpleDateFormat(
                "dd MMM yyyy",
                Locale.getDefault()
        ).format(new Date(task.getDueDate()));

        holder.tvDueDate.setText("Due " + dueDate);

        holder.checkCompleted.setOnCheckedChangeListener(null);
        holder.checkCompleted.setChecked(task.isCompleted());

        if (task.isCompleted()) {
            holder.tvTitle.setPaintFlags(
                    holder.tvTitle.getPaintFlags()
                            | Paint.STRIKE_THRU_TEXT_FLAG
            );
            holder.tvTitle.setTextColor(
                    holder.itemView.getContext().getColor(R.color.text_secondary)
            );
            holder.tvDueDate.setText("Completed • " + dueDate);
        } else {
            holder.tvTitle.setPaintFlags(
                    holder.tvTitle.getPaintFlags()
                            & (~Paint.STRIKE_THRU_TEXT_FLAG)
            );
            holder.tvTitle.setTextColor(
                    holder.itemView.getContext().getColor(R.color.text_primary)
            );
        }

        holder.checkCompleted.setOnCheckedChangeListener(
                (buttonView, checked) -> {
                    if (checked != task.isCompleted()) {
                        listener.onCompletionChanged(task, checked);
                    }
                }
        );

        holder.btnDelete.setOnClickListener(
                v -> listener.onDeleteTask(task)
        );
    }

    @Override
    public int getItemCount() {
        return tasks.size();
    }

    static class TaskViewHolder extends RecyclerView.ViewHolder {

        TextView tvTitle;
        TextView tvSubject;
        TextView tvDueDate;
        MaterialCheckBox checkCompleted;
        ImageButton btnDelete;

        TaskViewHolder(@NonNull View itemView) {
            super(itemView);

            tvTitle = itemView.findViewById(R.id.tvTaskTitle);
            tvSubject = itemView.findViewById(R.id.tvTaskSubject);
            tvDueDate = itemView.findViewById(R.id.tvTaskDueDate);
            checkCompleted = itemView.findViewById(R.id.checkTaskCompleted);
            btnDelete = itemView.findViewById(R.id.btnDeleteTask);
        }
    }
}