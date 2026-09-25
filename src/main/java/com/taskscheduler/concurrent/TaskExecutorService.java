package com.taskscheduler.concurrent;

import com.taskscheduler.model.Task;
import com.taskscheduler.model.TaskStatistics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class TaskExecutorService {
    public enum ShutdownMode { GRACEFUL, IMMEDIATE }

    private final ExecutorService pool;
    private final int poolSize;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final List<Future<?>> activeFutures = Collections.synchronizedList(new ArrayList<>());

    public TaskExecutorService(int poolSize) {
        if (poolSize <= 0) poolSize = Runtime.getRuntime().availableProcessors();
        this.poolSize = poolSize;
        this.pool = Executors.newFixedThreadPool(poolSize, new CustomThreadFactory("TaskWorker"));
        this.running.set(true);
    }

    public void execute(Runnable runnable) {
        if (!running.get()) throw new RejectedExecutionException("Service not running");
        activeFutures.add(pool.submit(runnable));
    }

    public <V> Future<V> submit(Callable<V> callable) {
        if (!running.get()) throw new RejectedExecutionException("Service not running");
        Future<V> f = pool.submit(callable);
        activeFutures.add(f);
        return f;
    }

    public <V> List<Future<V>> invokeAll(Collection<? extends Callable<V>> tasks)
            throws InterruptedException {
        if (!running.get()) throw new RejectedExecutionException("Service not running");
        return pool.invokeAll(tasks);
    }

    public Future<TaskExecutionRequest> submitTaskExecution(Task task,
                                                            TaskConsumer.TaskCompletionCallback callback) {
        Callable<TaskExecutionRequest> callable = () -> {
            Thread.currentThread().setName("Exec-" + task.getId());
            TaskExecutionRequest req = new TaskExecutionRequest(task);
            req.markStarted();
            long delayMs = 150L + task.getPriority().getWeight() * 80L;
            Thread.sleep(delayMs);
            if (ThreadLocalRandom.current().nextDouble() < 0.04) {
                req.markFailed("Random failure during execution");
            } else {
                req.markCompleted(com.taskscheduler.model.TaskStatus.COMPLETED);
            }
            if (callback != null) callback.onComplete(req);
            return req;
        };
        return submit(callable);
    }

    public void startProducerConsumerPipeline(List<Task> seedTasks,
                                              int consumerCount,
                                              TaskConsumer.TaskCompletionCallback callback) {
        BoundedTaskQueue queue = new BoundedTaskQueue(Math.max(4, seedTasks.size() / 2 + 1));
        TaskProducer producer = new TaskProducer("MainProducer", queue, seedTasks);
        execute(producer);
        for (int i = 1; i <= consumerCount; i++) {
            TaskConsumer consumer = new TaskConsumer("C-" + i, queue, callback);
            execute(consumer);
        }
        new Thread(() -> {
            try {
                Thread.sleep(Math.max(500L, seedTasks.size() * 150L));
            } catch (InterruptedException ignored) {
            } finally {
                queue.close();
            }
        }, "QueueCloser").start();
    }

    public Future<TaskStatistics> submitStatisticsComputation(Callable<TaskStatistics> work) {
        return submit(work);
    }

    public Future<BigDecimal> submitProgressAverage(List<Task> tasks) {
        return submit(() -> {
            if (tasks.isEmpty()) return BigDecimal.ZERO;
            BigDecimal sum = BigDecimal.ZERO;
            for (Task t : tasks) {
                sum = sum.add(BigDecimal.valueOf(t.getProgress()));
            }
            return sum.divide(BigDecimal.valueOf(tasks.size()), 2, RoundingMode.HALF_UP);
        });
    }

    public boolean shutdown(ShutdownMode mode, long timeoutMs) {
        running.set(false);
        boolean terminated;
        try {
            switch (mode) {
                case GRACEFUL -> pool.shutdown();
                case IMMEDIATE -> {
                    List<Runnable> dropped = pool.shutdownNow();
                    System.out.printf("shutdownNow dropped %d queued tasks%n", dropped.size());
                }
            }
            terminated = pool.awaitTermination(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            terminated = false;
        }
        return terminated;
    }

    public boolean isShutdown() { return pool.isShutdown(); }
    public boolean isTerminated() { return pool.isTerminated(); }
    public int getPoolSize() { return poolSize; }

    public int countDoneFutures() {
        int done = 0;
        synchronized (activeFutures) {
            for (Future<?> f : activeFutures) if (f.isDone()) done++;
        }
        return done;
    }

    public int countTotalFutures() {
        synchronized (activeFutures) {
            return activeFutures.size();
        }
    }

    public void cleanupDoneFutures() {
        activeFutures.removeIf(Future::isDone);
    }

    public static void demonstrateThreadBasics() {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                Thread t = Thread.currentThread();
                String name = t.getName();
                Thread.State s = t.getState();
                boolean alive = t.isAlive();
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };

        Thread t1 = new WorkerThread("W-1");
        Thread t2 = new Thread(runnable, "W-2");

        t1.start();
        t2.start();

        try {
            t1.join();
            t2.join(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        t1.interrupt();
        boolean interrupted = t1.isInterrupted();
    }

    public static class WorkerThread extends Thread {
        public WorkerThread(String name) { super(name); }
        @Override
        public void run() {
            for (int i = 0; i < 3; i++) {
                if (Thread.currentThread().isInterrupted()) return;
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    public static class CustomThreadFactory implements ThreadFactory {
        private final String baseName;
        private int counter = 0;
        private final Object counterLock = new Object();

        public CustomThreadFactory(String baseName) { this.baseName = baseName; }

        @Override
        public Thread newThread(Runnable r) {
            String name;
            synchronized (counterLock) {
                counter++;
                name = baseName + "-" + counter;
            }
            Thread t = new Thread(r, name);
            t.setDaemon(false);
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }
}
