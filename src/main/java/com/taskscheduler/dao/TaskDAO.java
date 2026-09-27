package com.taskscheduler.dao;

import com.taskscheduler.exception.DataAccessException;
import com.taskscheduler.exception.TaskValidationException;
import com.taskscheduler.model.*;

import java.sql.*;
import java.time.LocalDate;
import java.util.*;

public class TaskDAO {

    public TaskDAO() {
        DatabaseConnection.initializeSchema();
    }

    public Optional<Task> findById(int id) {
        String sql = "SELECT * FROM tasks WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("findById failed for id=" + id, e);
        }
        return Optional.empty();
    }

    public List<Task> findAll() {
        List<Task> tasks = new ArrayList<>();
        String sql = "SELECT * FROM tasks ORDER BY due_date ASC, priority DESC, id ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                tasks.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new DataAccessException("findAll failed", e);
        }
        return tasks;
    }

    public List<Task> findByStatus(TaskStatus status) {
        List<Task> tasks = new ArrayList<>();
        String sql = "SELECT * FROM tasks WHERE status = ? ORDER BY due_date ASC, priority DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tasks.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("findByStatus failed for status=" + status, e);
        }
        return tasks;
    }

    public List<Task> findByCategory(TaskCategory category) {
        List<Task> tasks = new ArrayList<>();
        String sql = "SELECT * FROM tasks WHERE category = ? ORDER BY due_date ASC, priority DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, category.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tasks.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("findByCategory failed", e);
        }
        return tasks;
    }

    public boolean existsByTitleAndDueDate(String title, LocalDate dueDate, Integer excludeId) {
        String sql = "SELECT COUNT(*) FROM tasks WHERE title = ? AND due_date = ? AND (? IS NULL OR id <> ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, title);
            ps.setString(2, dueDate.toString());
            if (excludeId == null) {
                ps.setNull(3, Types.INTEGER);
                ps.setNull(4, Types.INTEGER);
            } else {
                ps.setInt(3, excludeId);
                ps.setInt(4, excludeId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new DataAccessException("existsByTitleAndDueDate check failed", e);
        }
    }

    public Task insert(Task task) throws TaskValidationException {
        task.validate();
        if (existsByTitleAndDueDate(task.getTitle(), task.getDueDate(), null)) {
            throw new TaskValidationException(
                    "A task with the same title and due date already exists.");
        }
        String sql = """
            INSERT INTO tasks(title, description, priority, status, category, due_date, progress, assigned_to)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindInsertOrUpdate(ps, task);
            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new DataAccessException("Insert failed, no rows affected.");
            }
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int id = keys.getInt(1);
                    return findById(id).orElseThrow(() -> new DataAccessException(
                            "Insert succeeded but could not read back task id=" + id));
                }
            }
            throw new DataAccessException("Insert did not return a generated key.");
        } catch (SQLException e) {
            throw new DataAccessException("Insert task failed: " + task.getTitle(), e);
        }
    }

    public Task update(Task task) throws TaskValidationException {
        task.validate();
        if (task.getId() <= 0) {
            throw new TaskValidationException("Task has no ID for update.");
        }
        if (existsByTitleAndDueDate(task.getTitle(), task.getDueDate(), task.getId())) {
            throw new TaskValidationException(
                    "Another task with the same title and due date already exists.");
        }
        String sql = """
            UPDATE tasks SET
                title = ?, description = ?, priority = ?, status = ?, category = ?,
                due_date = ?, progress = ?, assigned_to = ?
            WHERE id = ?
            """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindInsertOrUpdate(ps, task);
            ps.setInt(9, task.getId());
            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new DataAccessException("Update failed, no rows affected for id=" + task.getId());
            }
            return findById(task.getId()).orElseThrow(() -> new DataAccessException(
                    "Update succeeded but task id=" + task.getId() + " not found after update"));
        } catch (SQLException e) {
            throw new DataAccessException("Update task failed for id=" + task.getId(), e);
        }
    }

    public void deleteById(int id) {
        String sql = "DELETE FROM tasks WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Delete failed for id=" + id, e);
        }
    }

    public TaskStatistics computeStatistics() {
        List<Task> all = findAll();
        long total = all.size();
        long pending = countByStatus(all, TaskStatus.PENDING);
        long inProgress = countByStatus(all, TaskStatus.IN_PROGRESS);
        long completed = countByStatus(all, TaskStatus.COMPLETED);
        long cancelled = countByStatus(all, TaskStatus.CANCELLED);
        long overdue = all.stream().filter(Task::isOverdue).count();

        double avgProgress = 0.0;
        if (!all.isEmpty()) {
            long sum = 0;
            for (Task t : all) sum += t.getProgress();
            avgProgress = (double) sum / all.size();
        }

        EnumMap<TaskPriority, Long> byPriority = new EnumMap<>(TaskPriority.class);
        for (TaskPriority p : TaskPriority.values()) {
            byPriority.put(p, 0L);
        }
        for (Task t : all) {
            byPriority.merge(t.getPriority(), 1L, Long::sum);
        }

        try {
            return new TaskStatistics(total, pending, inProgress, completed, cancelled,
                    overdue, avgProgress, byPriority);
        } catch (RuntimeException | TaskValidationException e) {
            throw new DataAccessException("Failed to compute statistics", e);
        }
    }

    private static long countByStatus(Collection<Task> tasks, TaskStatus status) {
        long count = 0;
        for (Task t : tasks) {
            if (t.getStatus() == status) count++;
        }
        return count;
    }

    private static void bindInsertOrUpdate(PreparedStatement ps, Task task) throws SQLException {
        ps.setString(1, task.getTitle());
        ps.setString(2, task.getDescription());
        ps.setString(3, task.getPriority().name());
        ps.setString(4, task.getStatus().name());
        ps.setString(5, task.getCategory().name());
        ps.setString(6, task.getDueDate().toString());
        ps.setInt(7, task.getProgress());
        ps.setString(8, task.getAssignedTo());
    }

    private static Task mapRow(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        String title = rs.getString("title");
        String desc = rs.getString("description");
        TaskPriority priority = TaskPriority.valueOf(rs.getString("priority"));
        TaskStatus status = TaskStatus.valueOf(rs.getString("status"));
        TaskCategory category = TaskCategory.valueOf(rs.getString("category"));
        LocalDate dueDate = LocalDate.parse(rs.getString("due_date"));
        int progress = rs.getInt("progress");
        String assigned = rs.getString("assigned_to");
        return new Task(id, title, desc == null ? "" : desc, priority, status, category,
                dueDate, progress, assigned == null ? "" : assigned);
    }
}