package com.taskscheduler.concurrent;

import java.util.Deque;
import java.util.LinkedList;

public class BoundedTaskQueue {
    private final Deque<TaskExecutionRequest> queue;
    private final int capacity;
    private final Object lock = new Object();
    private volatile boolean closed = false;

    public BoundedTaskQueue(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("Capacity must be positive");
        this.capacity = capacity;
        this.queue = new LinkedList<>();
    }

    public void enqueue(TaskExecutionRequest request) throws InterruptedException {
        synchronized (lock) {
            while (queue.size() == capacity && !closed) {
                lock.wait();
            }
            if (closed) throw new IllegalStateException("Queue is closed");
            queue.addLast(request);
            lock.notifyAll();
        }
    }

    public TaskExecutionRequest dequeue() throws InterruptedException {
        synchronized (lock) {
            while (queue.isEmpty() && !closed) {
                lock.wait();
            }
            if (queue.isEmpty() && closed) return null;
            TaskExecutionRequest r = queue.removeFirst();
            lock.notifyAll();
            return r;
        }
    }

    public int size() {
        synchronized (lock) {
            return queue.size();
        }
    }

    public boolean isEmpty() {
        synchronized (lock) {
            return queue.isEmpty();
        }
    }

    public void close() {
        synchronized (lock) {
            closed = true;
            lock.notifyAll();
        }
    }

    public boolean isClosed() { return closed; }
}
