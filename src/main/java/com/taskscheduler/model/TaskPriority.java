package com.taskscheduler.model;

public enum TaskPriority {
    LOW("Low", 1),
    MEDIUM("Medium", 2),
    HIGH("High", 3),
    URGENT("Urgent", 4);

    private final String displayName;
    private final int weight;

    TaskPriority(String displayName, int weight) {
        this.displayName = displayName;
        this.weight = weight;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getWeight() {
        return weight;
    }

    @Override
    public String toString() {
        return displayName;
    }

    public static TaskPriority fromDisplayName(String name) {
        for (TaskPriority tp : values()) {
            if (tp.displayName.equalsIgnoreCase(name)) {
                return tp;
            }
        }
        return MEDIUM;
    }
}
