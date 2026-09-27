package com.taskscheduler.model;

import com.taskscheduler.exception.TaskValidationException;
import javafx.beans.property.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public class Task {
    private final IntegerProperty id;
    private final StringProperty title;
    private final StringProperty description;
    private final ObjectProperty<TaskPriority> priority;
    private final ObjectProperty<TaskStatus> status;
    private final ObjectProperty<TaskCategory> category;
    private final ObjectProperty<LocalDate> dueDate;
    private final IntegerProperty progress;
    private final StringProperty assignedTo;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public Task() {
        this(0, "", "", TaskPriority.MEDIUM, TaskStatus.PENDING,
                TaskCategory.OTHER, LocalDate.now(), 0, "");
    }

    public Task(int id, String title, String description, TaskPriority priority,
                TaskStatus status, TaskCategory category, LocalDate dueDate,
                int progress, String assignedTo) {
        this.id = new SimpleIntegerProperty(id);
        this.title = new SimpleStringProperty(title);
        this.description = new SimpleStringProperty(description);
        this.priority = new SimpleObjectProperty<>(priority);
        this.status = new SimpleObjectProperty<>(status);
        this.category = new SimpleObjectProperty<>(category);
        this.dueDate = new SimpleObjectProperty<>(dueDate);
        this.progress = new SimpleIntegerProperty(progress);
        this.assignedTo = new SimpleStringProperty(assignedTo);
    }

    public void validate() throws TaskValidationException {
        String trimmedTitle = title.get() == null ? "" : title.get().trim();
        if (trimmedTitle.isEmpty()) {
            throw new TaskValidationException("Task title cannot be empty.");
        }
        if (trimmedTitle.length() > 200) {
            throw new TaskValidationException("Task title is too long (max 200 characters).");
        }
        int p = progress.get();
        if (p < 0 || p > 100) {
            throw new TaskValidationException("Progress must be between 0 and 100.");
        }
        if (dueDate.get() == null) {
            throw new TaskValidationException("Due date is required.");
        }
        if (priority.get() == null) {
            throw new TaskValidationException("Priority is required.");
        }
    }

    public int getId() { return id.get(); }
    public void setId(int id) { this.id.set(id); }
    public IntegerProperty idProperty() { return id; }

    public String getTitle() { return title.get(); }
    public void setTitle(String title) { this.title.set(title); }
    public StringProperty titleProperty() { return title; }

    public String getDescription() { return description.get(); }
    public void setDescription(String description) { this.description.set(description); }
    public StringProperty descriptionProperty() { return description; }

    public TaskPriority getPriority() { return priority.get(); }
    public void setPriority(TaskPriority priority) { this.priority.set(priority); }
    public ObjectProperty<TaskPriority> priorityProperty() { return priority; }

    public TaskStatus getStatus() { return status.get(); }
    public void setStatus(TaskStatus status) { this.status.set(status); }
    public ObjectProperty<TaskStatus> statusProperty() { return status; }

    public TaskCategory getCategory() { return category.get(); }
    public void setCategory(TaskCategory category) { this.category.set(category); }
    public ObjectProperty<TaskCategory> categoryProperty() { return category; }

    public LocalDate getDueDate() { return dueDate.get(); }
    public void setDueDate(LocalDate dueDate) { this.dueDate.set(dueDate); }
    public ObjectProperty<LocalDate> dueDateProperty() { return dueDate; }

    public int getProgress() { return progress.get(); }
    public void setProgress(int progress) { this.progress.set(progress); }
    public IntegerProperty progressProperty() { return progress; }

    public String getAssignedTo() { return assignedTo.get(); }
    public void setAssignedTo(String assignedTo) { this.assignedTo.set(assignedTo); }
    public StringProperty assignedToProperty() { return assignedTo; }

    public String formattedDueDate() {
        return dueDate.get() != null ? dueDate.get().format(DATE_FORMATTER) : "";
    }

    public boolean isOverdue() {
        return dueDate.get() != null
                && !status.get().isTerminal()
                && LocalDate.now().isAfter(dueDate.get());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Task task)) return false;
        return getId() == task.getId()
                && Objects.equals(getTitle(), task.getTitle())
                && Objects.equals(getDueDate(), task.getDueDate());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getTitle(), getDueDate());
    }

    @Override
    public String toString() {
        String raw = "Task[id=%d, title='%s', priority=%s, status=%s, due=%s]";
        return raw.formatted(getId(), getTitle(), getPriority(), getStatus(), formattedDueDate());
    }

    public Task copy() {
        return new Task(getId(), getTitle(), getDescription(), getPriority(),
                getStatus(), getCategory(), getDueDate(), getProgress(),
                getAssignedTo());
    }

    @SuppressWarnings("unused")
    private static void demonstratePrimitives() {
        byte b = 127;
        short s = 32000;
        int i = 100_000;
        long l = 10_000_000_000L;
        float f = 3.14f;
        double d = 2.718281828;
        char c = 'A';
        boolean flag = true;

        int inc = 0;
        inc++;
        inc += 5;
        boolean isHigh = (b > 100) && flag;
        String label = isHigh ? "HIGH" : "LOW";
        int mask = i & 0xFF;
    }
}
