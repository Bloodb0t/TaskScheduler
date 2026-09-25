package com.taskscheduler.concurrent;

import com.taskscheduler.model.TaskStatus;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

public class TaskConsumer implements Runnable {
    private final BoundedTaskQueue queue;
    private final String consumerName;
    private final AtomicLong consumedCount = new AtomicLong(0);
    private final AtomicLong failedCount = new AtomicLong(0);
    private final TaskCompletionCallback callback;

    @FunctionalInterface
    public interface TaskCompletionCallback {
        void onComplete(TaskExecutionRequest request);
    }

    public TaskConsumer(String consumerName, BoundedTaskQueue queue, TaskCompletionCallback callback) {
        this.consumerName = consumerName;
        this.queue = queue;
        this.callback = callback;
    }

    @Override
    public void run() {
        Thread.currentThread().setName("Consumer-" + consumerName);
        while (!Thread.currentThread().isInterrupted()) {
            TaskExecutionRequest request;
            try {
                request = queue.dequeue();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            if (request == null) {
                break;
            }
            request.markStarted();
            try {
                simulateWork(request);
                request.markCompleted(TaskStatus.COMPLETED);
                consumedCount.incrementAndGet();
            } catch (Exception e) {
                request.markFailed(e.getMessage());
                failedCount.incrementAndGet();
            } finally {
                if (callback != null) {
                    try {
                        callback.onComplete(request);
                    } catch (RuntimeException ignored) {
                    }
                }
            }
        }
        System.out.printf("%s finished consuming %d tasks (%d failed)%n",
                Thread.currentThread().getName(), consumedCount.get(), failedCount.get());
    }

    private void simulateWork(TaskExecutionRequest request) throws InterruptedException {
        int weight = request.getTask().getPriority().getWeight();
        int baseWorkMs = 100 + weight * 50;
        int variability = ThreadLocalRandom.current().nextInt(0, baseWorkMs / 2);
        int total = baseWorkMs + variability;
        long deadline = System.currentTimeMillis() + total;
        while (System.currentTimeMillis() < deadline) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Worker thread interrupted mid-work");
            }
            Thread.sleep(Math.min(20, deadline - System.currentTimeMillis()));
        }
        if (ThreadLocalRandom.current().nextDouble() < 0.05) {
            throw new RuntimeException("Simulated random failure");
        }
    }

    public long getConsumedCount() { return consumedCount.get(); }
    public long getFailedCount() { return failedCount.get(); }
}
