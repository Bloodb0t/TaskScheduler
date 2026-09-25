package com.taskscheduler.util;

@FunctionalInterface
public interface TaskFilter {
    boolean test(com.taskscheduler.model.Task task);

    default TaskFilter and(TaskFilter other) {
        return t -> this.test(t) && other.test(t);
    }

    default TaskFilter or(TaskFilter other) {
        return t -> this.test(t) || other.test(t);
    }

    default TaskFilter negate() {
        return t -> !this.test(t);
    }

    static TaskFilter alwaysTrue() {
        return t -> true;
    }
}
