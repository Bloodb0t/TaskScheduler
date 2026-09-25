package com.taskscheduler.concurrent;

import com.taskscheduler.model.Task;
import com.taskscheduler.model.TaskStatus;

import java.time.Instant;

public class TaskExecutionRequest {
    private final Task task;
    private final Instant submittedAt;
    private volatile Instant startedAt;
    private volatile Instant completedAt;
    private volatile TaskStatus finalStatus;
    private volatile String errorMessage;

    public TaskExecutionRequest(Task task) {
        this.task = task;
        this.submittedAt = Instant.now();
    }

    public Task getTask() { return task; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public TaskStatus getFinalStatus() { return finalStatus; }
    public String getErrorMessage() { return errorMessage; }

    public void markStarted() { this.startedAt = Instant.now(); }
    public void markCompleted(TaskStatus status) {
        this.completedAt = Instant.now();
        this.finalStatus = status;
    }
    public void markFailed(String message) {
        this.completedAt = Instant.now();
        this.finalStatus = TaskStatus.CANCELLED;
        this.errorMessage = message;
    }

    public boolean isFinished() { return completedAt != null; }

    public long millisTaken() {
        if (startedAt == null || completedAt == null) return -1;
        return completedAt.toEpochMilli() - startedAt.toEpochMilli();
    }
}
