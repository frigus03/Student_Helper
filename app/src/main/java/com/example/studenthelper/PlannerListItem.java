package com.example.studenthelper;

public abstract class PlannerListItem {
    public static final int TYPE_HEADER = 0;
    public static final int TYPE_TASK = 1;

    abstract int getType();
}
