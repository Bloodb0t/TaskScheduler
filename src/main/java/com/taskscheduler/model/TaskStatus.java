package com.taskscheduler.model;

public enum TaskStatus {
    PENDING("Pending"),
    IN_PROGRESS("In Progress"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled");

    private final String displayName;

    TaskStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }

    public static TaskStatus fromDisplayName(String name) {
        for (TaskStatus ts : values()) {
            if (ts.displayName.equalsIgnoreCase(name)) {
                return ts;
            }
        }
        return PENDING;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }
}
