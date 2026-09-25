package com.taskscheduler.service;

import com.taskscheduler.concurrent.TaskConsumer;
import com.taskscheduler.concurrent.TaskExecutorService;
import com.taskscheduler.dao.TaskDAO;
import com.taskscheduler.exception.TaskValidationException;
import com.taskscheduler.model.Task;
import com.taskscheduler.model.TaskCategory;
import com.taskscheduler.model.TaskPriority;
import com.taskscheduler.model.TaskStatistics;
import com.taskscheduler.model.TaskStatus;
import com.taskscheduler.util.TaskFilter;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

public class TaskService {
    private final TaskDAO dao;
    private final TaskExecutorService executor;

    public TaskService() {
        this.dao = new TaskDAO();
        this.executor = new TaskExecutorService(
                Math.max(2, Runtime.getRuntime().availableProcessors() - 1));
    }

    public TaskService(TaskDAO dao, TaskExecutorService executor) {
        this.dao = dao;
        this.executor = executor;
    }

    public List<Task> findAll() {
        return dao.findAll();
    }

    public Optional<Task> findById(int id) {
        return dao.findById(id);
    }

    public List<Task> findFiltered(TaskFilter filter) {
        List<Task> all = dao.findAll();
        List<Task> result = new ArrayList<>();
        for (Task t : all) {
            if (filter.test(t)) result.add(t);
        }
        return result;
    }

    public Task createTask(Task task) throws TaskValidationException {
        return dao.insert(task);
    }

    public Task updateTask(Task task) throws TaskValidationException {
        return dao.update(task);
    }

    public void deleteTask(int id) {
        dao.deleteById(id);
    }

    public TaskStatistics getStatistics() {
        return dao.computeStatistics();
    }

    public List<TaskPriority> getPriorities() {
        return Arrays.asList(TaskPriority.values());
    }

    public List<TaskStatus> getStatuses() {
        return Arrays.asList(TaskStatus.values());
    }

    public List<TaskCategory> getCategories() {
        return Arrays.asList(TaskCategory.values());
    }

    public Future<TaskStatistics> computeStatisticsAsync() {
        Callable<TaskStatistics> work = dao::computeStatistics;
        return executor.submitStatisticsComputation(work);
    }

    public Future<com.taskscheduler.concurrent.TaskExecutionRequest> executeTaskAsync(
            Task task, TaskConsumer.TaskCompletionCallback callback) {
        return executor.submitTaskExecution(task, callback);
    }

    public void startPipeline(List<Task> seed, TaskConsumer.TaskCompletionCallback callback) {
        int consumers = Math.max(2, executor.getPoolSize() / 2 + 1);
        executor.startProducerConsumerPipeline(seed, consumers, callback);
    }

    public TaskExecutorService getExecutor() { return executor; }

    public void shutdownExecutor() {
        executor.shutdown(TaskExecutorService.ShutdownMode.GRACEFUL, 3000);
    }

    public List<Task> seedSampleDataIfEmpty() {
        List<Task> existing = dao.findAll();
        if (!existing.isEmpty()) return existing;

        String[] titles = {
                "Review Q3 report",
                "Call client about proposal",
                "Study for Java certification",
                "Morning run 5K",
                "Pay electricity bill",
                "Design new dashboard UI",
                "Refactor auth module",
                "Write unit tests for DAO",
                "Team standup prep",
                "Renew SSL certificates",
                "Doctor appointment",
                "Grocery shopping",
                "Update resume",
                "Book flight tickets",
                "Plan weekend trip"
        };

        TaskPriority[] priorities = TaskPriority.values();
        TaskStatus[] statuses = TaskStatus.values();
        TaskCategory[] categories = TaskCategory.values();
        Random rnd = new Random(42);

        List<Task> created = new ArrayList<>();
        for (int i = 0; i < titles.length; i++) {
            LocalDate due = LocalDate.now().plusDays(rnd.nextInt(-2, 14));
            Task t = new Task(0,
                    titles[i],
                    "Sample description for: " + titles[i],
                    priorities[rnd.nextInt(priorities.length)],
                    statuses[rnd.nextInt(statuses.length - 1)],
                    categories[rnd.nextInt(categories.length)],
                    due,
                    rnd.nextInt(101),
                    "Team Member " + (rnd.nextInt(5) + 1),
                    new java.awt.Color(
                            rnd.nextInt(200),
                            rnd.nextInt(200),
                            rnd.nextInt(200)));
            try {
                created.add(dao.insert(t));
            } catch (TaskValidationException ignored) {
            }
        }
        return created;
    }
}
