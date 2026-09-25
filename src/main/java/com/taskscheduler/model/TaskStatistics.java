package com.taskscheduler.model;

import com.taskscheduler.exception.TaskValidationException;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class TaskStatistics {
    private final long totalTasks;
    private final long pendingTasks;
    private final long inProgressTasks;
    private final long completedTasks;
    private final long cancelledTasks;
    private final long overdueTasks;
    private final double averageProgress;
    private final Map<TaskPriority, Long> tasksByPriority;

    public TaskStatistics(long totalTasks,
                          long pendingTasks,
                          long inProgressTasks,
                          long completedTasks,
                          long cancelledTasks,
                          long overdueTasks,
                          double averageProgress,
                          Map<TaskPriority, Long> tasksByPriority) throws TaskValidationException {
        if (totalTasks < 0) {
            throw new TaskValidationException("Total tasks count cannot be negative.");
        }
        long counted = pendingTasks + inProgressTasks + completedTasks + cancelledTasks;
        if (counted != totalTasks && totalTasks != 0) {
            throw new TaskValidationException(
                    "Status counts (%d) do not sum to total (%d).".formatted(counted, totalTasks));
        }
        if (averageProgress < 0.0 || averageProgress > 100.0) {
            throw new TaskValidationException("Average progress must be between 0 and 100.");
        }

        this.totalTasks = totalTasks;
        this.pendingTasks = pendingTasks;
        this.inProgressTasks = inProgressTasks;
        this.completedTasks = completedTasks;
        this.cancelledTasks = cancelledTasks;
        this.overdueTasks = overdueTasks;
        this.averageProgress = averageProgress;

        EnumMap<TaskPriority, Long> copy = new EnumMap<>(TaskPriority.class);
        for (TaskPriority p : TaskPriority.values()) copy.put(p, 0L);
        if (tasksByPriority != null) {
            for (Map.Entry<TaskPriority, Long> e : tasksByPriority.entrySet()) {
                if (e.getKey() == null) continue;
                long v = e.getValue() == null ? 0L : Math.max(0L, e.getValue());
                copy.merge(e.getKey(), v, Long::sum);
            }
        }
        this.tasksByPriority = Collections.unmodifiableMap(copy);
    }

    public long totalTasks() { return totalTasks; }
    public long pendingTasks() { return pendingTasks; }
    public long inProgressTasks() { return inProgressTasks; }
    public long completedTasks() { return completedTasks; }
    public long cancelledTasks() { return cancelledTasks; }
    public long overdueTasks() { return overdueTasks; }
    public double averageProgress() { return averageProgress; }
    public Map<TaskPriority, Long> tasksByPriority() { return tasksByPriority; }

    public long activeTasks() {
        return pendingTasks + inProgressTasks;
    }

    public double completionRate() {
        return totalTasks == 0 ? 0.0 : (double) completedTasks / totalTasks * 100.0;
    }

    public String summary() {
        return ("Total: %d | Active: %d | Done: %d | Overdue: %d | Avg Progress: %.1f%%")
                .formatted(totalTasks, activeTasks(), completedTasks, overdueTasks, averageProgress);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TaskStatistics that)) return false;
        return totalTasks == that.totalTasks
                && pendingTasks == that.pendingTasks
                && inProgressTasks == that.inProgressTasks
                && completedTasks == that.completedTasks
                && cancelledTasks == that.cancelledTasks
                && overdueTasks == that.overdueTasks
                && Double.compare(that.averageProgress, averageProgress) == 0
                && tasksByPriority.equals(that.tasksByPriority);
    }

    @Override
    public int hashCode() {
        return Objects.hash(totalTasks, pendingTasks, inProgressTasks, completedTasks,
                cancelledTasks, overdueTasks, averageProgress, tasksByPriority);
    }

    @Override
    public String toString() {
        return "TaskStatistics[total=%d, pending=%d, inProgress=%d, completed=%d, cancelled=%d, overdue=%d, avgProgress=%.2f, byPriority=%s]"
                .formatted(totalTasks, pendingTasks, inProgressTasks, completedTasks,
                        cancelledTasks, overdueTasks, averageProgress, tasksByPriority);
    }
}
