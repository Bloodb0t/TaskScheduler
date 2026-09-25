package com.taskscheduler.concurrent;

import com.taskscheduler.model.Task;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class TaskProducer implements Runnable {
    private final BoundedTaskQueue queue;
    private final List<Task> tasks;
    private final String producerName;
    private volatile long producedCount = 0;

    public TaskProducer(String producerName, BoundedTaskQueue queue, List<Task> tasks) {
        this.producerName = producerName;
        this.queue = queue;
        this.tasks = tasks;
    }

    @Override
    public void run() {
        Thread.currentThread().setName("Producer-" + producerName);
        try {
            for (Task task : tasks) {
                if (Thread.currentThread().isInterrupted()) {
                    System.out.println(Thread.currentThread().getName() + " interrupted, stopping.");
                    return;
                }
                int delay = ThreadLocalRandom.current().nextInt(50, 250);
                Thread.sleep(delay);
                TaskExecutionRequest req = new TaskExecutionRequest(task);
                try {
                    queue.enqueue(req);
                    producedCount++;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (IllegalStateException e) {
                    return;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            System.out.printf("%s finished producing %d tasks%n",
                    Thread.currentThread().getName(), producedCount);
        }
    }

    public long getProducedCount() { return producedCount; }
}
