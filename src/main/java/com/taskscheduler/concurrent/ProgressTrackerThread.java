package com.taskscheduler.concurrent;

import javafx.application.Platform;

import java.util.concurrent.atomic.AtomicLong;

public class ProgressTrackerThread extends Thread {
    private final AtomicLong completedCount = new AtomicLong(0);
    private final AtomicLong totalCount = new AtomicLong(0);
    private volatile boolean keepRunning = true;
    private final Runnable onUpdate;

    public ProgressTrackerThread(String name, Runnable onUpdate) {
        super(name);
        this.onUpdate = onUpdate;
        setDaemon(true);
    }

    @Override
    public void run() {
        while (keepRunning && !isInterrupted()) {
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                interrupt();
                break;
            }
            if (onUpdate != null) {
                Platform.runLater(onUpdate);
            }
        }
    }

    public void stopTracking() {
        keepRunning = false;
        interrupt();
    }

    public void incrementCompleted() { completedCount.incrementAndGet(); }
    public void incrementTotal() { totalCount.incrementAndGet(); }
    public long getCompletedCount() { return completedCount.get(); }
    public long getTotalCount() { return totalCount.get(); }

    public double getProgress() {
        long total = totalCount.get();
        if (total == 0) return 0.0;
        return (double) completedCount.get() / total;
    }
}
