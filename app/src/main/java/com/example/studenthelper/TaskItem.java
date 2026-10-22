package com.example.studenthelper;

public class TaskItem extends PlannerListItem {
    private StudyTask task;

    public TaskItem(StudyTask task) {
        this.task = task;
    }

    public StudyTask getTask() { return task; }

    @Override
    int getType() {
        return TYPE_TASK;
    }
}
