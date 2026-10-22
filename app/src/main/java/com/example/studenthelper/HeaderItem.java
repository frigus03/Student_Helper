package com.example.studenthelper;

public class HeaderItem extends PlannerListItem {
    private String title;
    private int count;

    public HeaderItem(String title, int count) {
        this.title = title;
        this.count = count;
    }

    public String getTitle() { return title; }
    public int getCount() { return count; }

    @Override
    int getType() {
        return TYPE_HEADER;
    }
}
