package com.taskscheduler.model;

public enum TaskCategory {
    WORK("Work"),
    PERSONAL("Personal"),
    STUDY("Study"),
    HEALTH("Health"),
    FINANCE("Finance"),
    OTHER("Other");

    private final String displayName;

    TaskCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }

    public static TaskCategory fromDisplayName(String name) {
        for (TaskCategory tc : values()) {
            if (tc.displayName.equalsIgnoreCase(name)) {
                return tc;
            }
        }
        return OTHER;
    }
}
